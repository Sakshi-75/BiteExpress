package com.biteExpress.service;

import com.biteExpress.api.RestaurantResponse;
import com.biteExpress.api.RestaurantSearchResponse;
import com.biteExpress.model.Restaurant;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
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
        if (input.city() != null) filters.add(Criteria.where("address.city").regex(exact(input.city()), "i"));
        if (input.cuisine() != null) filters.add(Criteria.where("cuisines").regex(exact(input.cuisine()), "i"));
        if (input.minRating() != null) filters.add(Criteria.where("rating.average").gte(input.minRating()));
        if (input.currency() != null) filters.add(Criteria.where("pricing.currency").regex(exact(input.currency()), "i"));
        if (input.maxCostForTwo() != null)
            filters.add(Criteria.where("pricing.averageCostForTwo").lte(input.maxCostForTwo()));

        Query query = new Query();
        if (!filters.isEmpty()) query.addCriteria(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        long total = mongoTemplate.count(query, Restaurant.class);
        Sort.Direction direction = input.sortDir().equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        query.with(Sort.by(new Sort.Order(direction, SORT_FIELDS.get(input.sortBy())),
                new Sort.Order(Sort.Direction.ASC, "id")));
        query.skip((long) input.page() * input.size()).limit(input.size());
        List<RestaurantSearchResponse.RestaurantSummary> content = mongoTemplate.find(query, Restaurant.class).stream()
                .map(this::toSummary).toList();
        return new RestaurantSearchResponse(content, input.page(), input.size(), total,
                (total + input.size() - 1) / input.size());
    }

    private String exact(String value) {
        return "^" + Pattern.quote(value) + "$";
    }

    private RestaurantSearchResponse.RestaurantSummary toSummary(Restaurant restaurant) {
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
                location == null ? null : new RestaurantResponse.LocationResponse(location.getY(), location.getX()));
    }
}
