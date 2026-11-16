package com.biteExpress.service;

import java.util.Locale;

/** Validated, normalized inputs for the restaurant listing query. */
public record RestaurantSearchCriteria(
        String name, String city, String cuisine, Double minRating,
        Integer maxCostForTwo, String currency, int page, int size,
        String sortBy, String sortDir
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
        sortBy = sortBy == null ? "name" : sortBy.strip().toLowerCase(Locale.ROOT);
        if (!switch (sortBy) { case "name", "city", "rating", "cost" -> true; default -> false; })
            throw new InvalidRestaurantSearchException("sortBy must be name, city, rating, or cost");
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
