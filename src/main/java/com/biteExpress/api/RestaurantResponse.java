package com.biteExpress.api;

import java.util.List;

public record RestaurantResponse(
        String id,
        String name,
        AddressResponse address,
        CountryResponse country,
        LocationResponse location,
        List<String> cuisines,
        PricingResponse pricing,
        RatingResponse rating,
        FeaturesResponse features
) {
    public record AddressResponse(String line1, String city, String locality, String localityVerbose) {}
    public record CountryResponse(Integer code, String name) {}
    public record LocationResponse(Double latitude, Double longitude) {}
    public record PricingResponse(Integer averageCostForTwo, String currency, Integer priceRange) {}
    public record RatingResponse(Double average, String color, String text, Integer votes) {}
    public record FeaturesResponse(Boolean hasTableBooking, Boolean hasOnlineDelivery,
                                   Boolean isDeliveringNow, Boolean switchToOrderMenu) {}
}
