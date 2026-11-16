package com.biteExpress.service;

public class InvalidRestaurantSearchException extends RuntimeException {
    public InvalidRestaurantSearchException(String message) {
        super(message);
    }
}
