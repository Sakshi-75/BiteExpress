package com.biteExpress.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantSearchCriteriaTests {
    @Test
    void trimsInputsAndDefaultsSort() {
        var criteria = new RestaurantSearchCriteria("  Spice  ", " Gurgaon ", " Indian ", null,
                null, null, 0, 20, null, null, null, null, null);
        assertEquals("Spice", criteria.name());
        assertEquals("Gurgaon", criteria.city());
        assertEquals("Indian", criteria.cuisine());
        assertEquals("name", criteria.sortBy());
        assertEquals("asc", criteria.sortDir());
    }

    @Test
    void rejectsCostWithoutCurrencyAndOversizedOffset() {
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, 100, null, 0, 20, null, null, null, null, null));
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, null, null, 0, 20, "cost", null, null, null, null));
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, null, null, Integer.MAX_VALUE, 100, null, null, null, null, null));
    }

    @Test
    void validatesNumericAndTextBoundaries() {
        for (double rating : new double[]{-0.1, 5.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(InvalidRestaurantSearchException.class,
                    () -> new RestaurantSearchCriteria(null, null, null, rating, null, null, 0, 20, null, null, null, null, null));
        }
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, -1, "INR", 0, 20, null, null, null, null, null));
        for (int size : new int[]{0, 101}) {
            assertThrows(InvalidRestaurantSearchException.class,
                    () -> new RestaurantSearchCriteria(null, null, null, null, null, null, 0, size, null, null, null, null, null));
        }
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, null, null, -1, 20, null, null, null, null, null));
        for (String text : new String[]{"  ", "x".repeat(201)}) {
            assertThrows(InvalidRestaurantSearchException.class,
                    () -> new RestaurantSearchCriteria(text, null, null, null, null, null, 0, 20, null, null, null, null, null));
        }
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, null, null, 0, 20, "votes", null, null, null, null));
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, null, null, 0, 20, null, "sideways", null, null, null));
    }

    @Test
    void acceptsAllSortFieldsAndInclusiveLimits() {
        for (String sortBy : new String[]{"name", "city", "rating", "cost"}) {
            var criteria = new RestaurantSearchCriteria(null, null, null, 5.0, 0,
                    " INR ", 0, 100, sortBy.toUpperCase(), "DESC", null, null, null);
            assertEquals(sortBy, criteria.sortBy());
            assertEquals("desc", criteria.sortDir());
            assertEquals("INR", criteria.currency());
        }
    }

    @Test
    void validatesNearbyInputsAndDefaultsToDistance() {
        var nearby = new RestaurantSearchCriteria(null, null, null, null, null, null,
                0, 20, null, null, 28.4595, 77.0266, 0.0);
        assertEquals("distance", nearby.sortBy());
        assertEquals(0.0, nearby.radiusKm());
        for (double latitude : new double[]{-90.1, 90.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(InvalidRestaurantSearchException.class, () -> new RestaurantSearchCriteria(
                    null, null, null, null, null, null, 0, 20, null, null, latitude, 77.0, null));
        }
        for (double longitude : new double[]{-180.1, 180.1, Double.NaN, Double.NEGATIVE_INFINITY}) {
            assertThrows(InvalidRestaurantSearchException.class, () -> new RestaurantSearchCriteria(
                    null, null, null, null, null, null, 0, 20, null, null, 28.0, longitude, null));
        }
        for (double radius : new double[]{-0.1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
            assertThrows(InvalidRestaurantSearchException.class, () -> new RestaurantSearchCriteria(
                    null, null, null, null, null, null, 0, 20, null, null, 28.0, 77.0, radius));
        }
        assertThrows(InvalidRestaurantSearchException.class, () -> new RestaurantSearchCriteria(
                null, null, null, null, null, null, 0, 20, null, null, 28.0, null, null));
        assertThrows(InvalidRestaurantSearchException.class, () -> new RestaurantSearchCriteria(
                null, null, null, null, null, null, 0, 20, null, null, null, 77.0, null));
        assertThrows(InvalidRestaurantSearchException.class, () -> new RestaurantSearchCriteria(
                null, null, null, null, null, null, 0, 20, null, null, null, null, 1.0));
        assertThrows(InvalidRestaurantSearchException.class, () -> new RestaurantSearchCriteria(
                null, null, null, null, null, null, 0, 20, "distance", null, null, null, null));
    }
}
