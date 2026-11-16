package com.biteExpress.service;

import com.biteExpress.api.CreateRestaurantRequest;
import com.biteExpress.api.RestaurantResponse;
import com.biteExpress.model.*;
import com.biteExpress.repository.RestaurantRepository;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RestaurantService {
    private final RestaurantRepository repository;

    public RestaurantService(RestaurantRepository repository) {
        this.repository = repository;
    }

    public RestaurantResponse create(CreateRestaurantRequest request) {
        var address = request.address();
        var country = request.country();
        var location = request.location();
        var pricing = request.pricing();
        var rating = request.rating();
        var features = request.features();
        Restaurant restaurant = new Restaurant(
                UUID.randomUUID().toString(), request.name().strip(),
                new Address(clean(address.line1()), address.city().strip(),
                        clean(address.locality()), clean(address.localityVerbose())),
                country == null ? null : new Country(country.code(), country.name().strip()),
                location == null ? null : new GeoJsonPoint(location.longitude(), location.latitude()),
                request.cuisines() == null ? List.of() : request.cuisines().stream().map(String::strip).toList(),
                pricing == null ? null : new Pricing(pricing.averageCostForTwo(), clean(pricing.currency()), pricing.priceRange()),
                rating == null ? null : new Rating(rating.average(), clean(rating.color()), clean(rating.text()), rating.votes()),
                features == null ? null : new RestaurantFeatures(features.hasTableBooking(), features.hasOnlineDelivery(),
                        features.isDeliveringNow(), features.switchToOrderMenu()));
        return toResponse(repository.save(restaurant));
    }

    public RestaurantResponse get(String id) {
        return repository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new RestaurantNotFoundException(id));
    }

    private RestaurantResponse toResponse(Restaurant restaurant) {
        var address = restaurant.address();
        var country = restaurant.country();
        var location = restaurant.location();
        var pricing = restaurant.pricing();
        var rating = restaurant.rating();
        var features = restaurant.features();
        return new RestaurantResponse(
                restaurant.id(), restaurant.name(),
                address == null ? null : new RestaurantResponse.AddressResponse(address.line1(), address.city(),
                        address.locality(), address.localityVerbose()),
                country == null ? null : new RestaurantResponse.CountryResponse(country.code(), country.name()),
                location == null ? null : new RestaurantResponse.LocationResponse(location.getY(), location.getX()),
                restaurant.cuisines(),
                pricing == null ? null : new RestaurantResponse.PricingResponse(pricing.averageCostForTwo(), pricing.currency(), pricing.priceRange()),
                rating == null ? null : new RestaurantResponse.RatingResponse(rating.average(), rating.color(), rating.text(), rating.votes()),
                features == null ? null : new RestaurantResponse.FeaturesResponse(features.hasTableBooking(),
                        features.hasOnlineDelivery(), features.isDeliveringNow(), features.switchToOrderMenu()));
    }

    private String clean(String value) {
        if (value == null) return null;
        String result = value.strip();
        return result.isEmpty() ? null : result;
    }
}
