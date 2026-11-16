package com.biteExpress.service;

public class UnsupportedRestaurantSearchException extends RuntimeException {
    public UnsupportedRestaurantSearchException(String message) {
        super(message);
    }
}
