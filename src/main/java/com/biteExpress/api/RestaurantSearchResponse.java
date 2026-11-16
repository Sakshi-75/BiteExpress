package com.biteExpress.api;

import io.swagger.v3.oas.annotations.media.Schema;
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
            RestaurantResponse.LocationResponse location,
            @Schema(description = "Spherical distance from the search center in kilometers; null without coordinates", example = "1.2")
            Double distanceKm
    ) {}
}
