package com.biteExpress.service;

import java.util.Locale;

/** Validated, normalized inputs for the restaurant listing query. */
public record RestaurantSearchCriteria(
        String name, String city, String cuisine, Double minRating,
        Integer maxCostForTwo, String currency, int page, int size,
        String sortBy, String sortDir, Double latitude, Double longitude, Double radiusKm
) {
    public RestaurantSearchCriteria {
        name = normalize(name);
        city = normalize(city);
        cuisine = normalize(cuisine);
        currency = normalize(currency);
        if (minRating != null && (!Double.isFinite(minRating) || minRating < 0 || minRating > 5))
            throw new InvalidRestaurantSearchException("minRating must be between 0 and 5");
        if (maxCostForTwo != null && maxCostForTwo < 0)
            throw new InvalidRestaurantSearchException("maxCostForTwo must be nonnegative");
        if (maxCostForTwo != null && currency == null)
            throw new InvalidRestaurantSearchException("currency is required with maxCostForTwo");
        if (page < 0) throw new InvalidRestaurantSearchException("page must be nonnegative");
        if (size < 1 || size > 100) throw new InvalidRestaurantSearchException("size must be between 1 and 100");
        if ((long) page * size > Integer.MAX_VALUE)
            throw new InvalidRestaurantSearchException("page is too large");
        if ((latitude == null) != (longitude == null))
            throw new InvalidRestaurantSearchException("latitude and longitude must be supplied together");
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90))
            throw new InvalidRestaurantSearchException("latitude must be between -90 and 90");
        if (longitude != null && (!Double.isFinite(longitude) || longitude < -180 || longitude > 180))
            throw new InvalidRestaurantSearchException("longitude must be between -180 and 180");
        if (radiusKm != null && (!Double.isFinite(radiusKm) || radiusKm < 0 || radiusKm > Double.MAX_VALUE / 1000))
            throw new InvalidRestaurantSearchException("radiusKm must be a nonnegative finite distance");
        if (radiusKm != null && latitude == null)
            throw new InvalidRestaurantSearchException("radiusKm requires latitude and longitude");
        sortBy = sortBy == null ? (latitude == null ? "name" : "distance") : sortBy.strip().toLowerCase(Locale.ROOT);
        if (!switch (sortBy) { case "name", "city", "rating", "cost", "distance" -> true; default -> false; })
            throw new InvalidRestaurantSearchException("sortBy must be name, city, rating, cost, or distance");
        if (sortBy.equals("cost") && currency == null)
            throw new InvalidRestaurantSearchException("currency is required when sorting by cost");
        if (sortBy.equals("distance") && latitude == null)
            throw new InvalidRestaurantSearchException("distance sorting requires latitude and longitude");
        sortDir = sortDir == null ? "asc" : sortDir.strip().toLowerCase(Locale.ROOT);
        if (!sortDir.equals("asc") && !sortDir.equals("desc"))
            throw new InvalidRestaurantSearchException("sortDir must be asc or desc");
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String cleaned = value.strip();
        if (cleaned.isEmpty()) throw new InvalidRestaurantSearchException("Search text must not be blank");
        if (cleaned.length() > 200) throw new InvalidRestaurantSearchException("Search text must not exceed 200 characters");
        return cleaned;
    }
}
