package com.biteExpress.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "restaurants")
public record Restaurant(

        @Id
        String id,
        String name,
        Address address,
        Country country,
        GeoJsonPoint location,
        List<String> cuisines,
        Pricing pricing,
        Rating rating,
        RestaurantFeatures features

) {
    public Restaurant {
        cuisines = cuisines == null
                ? List.of()
                : List.copyOf(cuisines);
    }
}