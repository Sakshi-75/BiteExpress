package com.biteExpress.api;

import java.util.List;

public record RestaurantSearchResponse(
        List<RestaurantSummary> content,
        int page,
        int size,
        long totalElements,
        long totalPages
) {
    public record RestaurantSummary(
            String id,
            String name,
            RestaurantResponse.AddressResponse address,
            String city,
            RestaurantResponse.CountryResponse country,
            List<String> cuisines,
            Double averageRating,
            RestaurantResponse.LocationResponse location
    ) {}
}
