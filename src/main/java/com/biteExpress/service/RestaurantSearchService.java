package com.biteExpress.service;

import com.biteExpress.api.RestaurantResponse;
import com.biteExpress.api.RestaurantSearchResponse;
import com.biteExpress.model.Restaurant;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class RestaurantSearchService {
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name", "city", "address.city", "rating", "rating.average", "cost", "pricing.averageCostForTwo");
    private final MongoTemplate mongoTemplate;

    public RestaurantSearchService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public RestaurantSearchResponse search(RestaurantSearchCriteria input) {
        List<Criteria> filters = new ArrayList<>();
        if (input.name() != null) filters.add(Criteria.where("name").regex(Pattern.quote(input.name()), "i"));
        if (input.city() != null) filters.add(Criteria.where("address.city").regex(cityPattern(input.city()), "i"));
        if (input.cuisine() != null) filters.add(Criteria.where("cuisines").regex(exact(input.cuisine()), "i"));
        if (input.minRating() != null) filters.add(Criteria.where("rating.average").gte(input.minRating()));
        if (input.currency() != null) filters.add(Criteria.where("pricing.currency").regex(currencyPattern(input.currency()), "i"));
        if (input.maxCostForTwo() != null)
            filters.add(Criteria.where("pricing.averageCostForTwo").lte(input.maxCostForTwo()));

        Criteria combined = filters.isEmpty() ? null : new Criteria().andOperator(filters.toArray(Criteria[]::new));
        if (input.latitude() != null) return searchNearby(input, combined);

        Query query = new Query();
        if (combined != null) query.addCriteria(combined);
        long total = mongoTemplate.count(query, Restaurant.class);
        Sort.Direction direction = input.sortDir().equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        query.with(Sort.by(new Sort.Order(direction, SORT_FIELDS.get(input.sortBy())),
                new Sort.Order(Sort.Direction.ASC, "id")));
        query.skip((long) input.page() * input.size()).limit(input.size());
        List<RestaurantSearchResponse.RestaurantSummary> content = mongoTemplate.find(query, Restaurant.class).stream()
                .map(restaurant -> toSummary(restaurant, null)).toList();
        return new RestaurantSearchResponse(content, input.page(), input.size(), total,
                totalPages(total, input.size()));
    }

    private RestaurantSearchResponse searchNearby(RestaurantSearchCriteria input, Criteria filters) {
        Document point = new Document("type", "Point")
                .append("coordinates", List.of(input.longitude(), input.latitude()));
        Document options = new Document("near", point).append("distanceField", "distanceMeters")
                .append("spherical", true).append("key", "location");
        if (input.radiusKm() != null) options.append("maxDistance", input.radiusKm() * 1000);

        List<AggregationOperation> common = new ArrayList<>();
        common.add(raw(new Document("$geoNear", options)));
        if (filters != null) common.add(raw(new Document("$match", filters.getCriteriaObject())));

        List<AggregationOperation> countStages = new ArrayList<>(common);
        countStages.add(raw(new Document("$count", "total")));
        List<Document> countResult = mongoTemplate.aggregate(
                Aggregation.newAggregation(countStages), Restaurant.class, Document.class).getMappedResults();
        long total = countResult.isEmpty() ? 0 : ((Number) countResult.getFirst().get("total")).longValue();

        int direction = input.sortDir().equals("asc") ? 1 : -1;
        String sortField = input.sortBy().equals("distance") ? "distanceMeters" : SORT_FIELDS.get(input.sortBy());
        List<AggregationOperation> pageStages = new ArrayList<>(common);
        pageStages.add(raw(new Document("$sort", new Document(sortField, direction).append("_id", 1))));
        pageStages.add(raw(new Document("$skip", (long) input.page() * input.size())));
        pageStages.add(raw(new Document("$limit", input.size())));
        List<RestaurantSearchResponse.RestaurantSummary> content = mongoTemplate.aggregate(
                        Aggregation.newAggregation(pageStages), Restaurant.class, Document.class).getMappedResults().stream()
                .map(document -> toSummary(mongoTemplate.getConverter().read(Restaurant.class, document),
                        ((Number) document.get("distanceMeters")).doubleValue() / 1000))
                .toList();
        return new RestaurantSearchResponse(content, input.page(), input.size(), total,
                totalPages(total, input.size()));
    }

    private long totalPages(long total, int size) {
        return total == 0 ? 0 : 1 + (total - 1) / size;
    }

    private AggregationOperation raw(Document stage) {
        return context -> stage;
    }

    private String exact(String value) {
        return "^" + Pattern.quote(value) + "$";
    }

    private String cityPattern(String city) {
        if (city.equalsIgnoreCase("Gurgaon") || city.equalsIgnoreCase("Gurugram"))
            return "^(?:" + Pattern.quote("Gurgaon") + "|" + Pattern.quote("Gurugram") + ")$";
        return exact(city);
    }

    private String currencyPattern(String currency) {
        if (currency.equalsIgnoreCase("INR") || currency.equalsIgnoreCase("Rs")
                || currency.equalsIgnoreCase("Indian Rupees(Rs.)"))
            return "^(?:" + Pattern.quote("INR") + "|" + Pattern.quote("Indian Rupees(Rs.)") + ")$";
        return exact(currency);
    }

    private RestaurantSearchResponse.RestaurantSummary toSummary(Restaurant restaurant, Double distanceKm) {
        var address = restaurant.address();
        var country = restaurant.country();
        var rating = restaurant.rating();
        var location = restaurant.location();
        return new RestaurantSearchResponse.RestaurantSummary(
                restaurant.id(), restaurant.name(),
                address == null ? null : new RestaurantResponse.AddressResponse(
                        address.line1(), address.city(), address.locality(), address.localityVerbose()),
                address == null ? null : address.city(),
                country == null ? null : new RestaurantResponse.CountryResponse(country.code(), country.name()),
                restaurant.cuisines(), rating == null ? null : rating.average(),
                location == null ? null : new RestaurantResponse.LocationResponse(location.getY(), location.getX()),
                distanceKm);
    }
}
