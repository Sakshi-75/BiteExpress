package com.biteExpress.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRestaurantRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Valid AddressRequest address,
        @Valid CountryRequest country,
        @Valid LocationRequest location,
        @Size(max = 50) List<@NotBlank @Size(max = 100) String> cuisines,
        @Valid PricingRequest pricing,
        @Valid RatingRequest rating,
        FeaturesRequest features
) {
    public record AddressRequest(
            @Size(max = 500) String line1,
            @NotBlank @Size(max = 100) String city,
            @Size(max = 200) String locality,
            @Size(max = 300) String localityVerbose
    ) {}

    public record CountryRequest(@Positive Integer code, @NotBlank @Size(max = 100) String name) {}

    public record LocationRequest(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
    ) {}

    public record PricingRequest(
            @Min(0) Integer averageCostForTwo,
            @Size(max = 100) String currency,
            @Min(1) @Max(4) Integer priceRange
    ) {}

    public record RatingRequest(
            @DecimalMin("0.0") @DecimalMax("5.0") Double average,
            @Size(max = 100) String color,
            @Size(max = 100) String text,
            @Min(0) Integer votes
    ) {}

    public record FeaturesRequest(
            Boolean hasTableBooking,
            Boolean hasOnlineDelivery,
            Boolean isDeliveringNow,
            Boolean switchToOrderMenu
    ) {}
}
