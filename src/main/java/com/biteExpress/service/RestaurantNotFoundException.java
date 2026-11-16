package com.biteExpress.service;

public class RestaurantNotFoundException extends RuntimeException {
    public RestaurantNotFoundException(String id) {
        super("Restaurant not found: " + id);
    }
}
