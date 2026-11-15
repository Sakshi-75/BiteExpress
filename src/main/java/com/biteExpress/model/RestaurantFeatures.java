package com.biteExpress.model;

public record RestaurantFeatures(
        Boolean hasTableBooking,
        Boolean hasOnlineDelivery,
        Boolean isDeliveringNow,
        Boolean switchToOrderMenu
) {
}