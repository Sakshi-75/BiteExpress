package com.biteExpress.model;

public record Pricing(
        Integer averageCostForTwo,
        String currency,
        Integer priceRange
) {
}
