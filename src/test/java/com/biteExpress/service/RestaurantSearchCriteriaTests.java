package com.biteExpress.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantSearchCriteriaTests {
    @Test
    void trimsInputsAndDefaultsSort() {
        var criteria = new RestaurantSearchCriteria("  Spice  ", " Gurgaon ", " Indian ", null,
                null, null, 0, 20, null, null);
        assertEquals("Spice", criteria.name());
        assertEquals("Gurgaon", criteria.city());
        assertEquals("Indian", criteria.cuisine());
        assertEquals("name", criteria.sortBy());
        assertEquals("asc", criteria.sortDir());
    }

    @Test
    void rejectsCostWithoutCurrencyAndOversizedOffset() {
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, 100, null, 0, 20, null, null));
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, null, null, Integer.MAX_VALUE, 100, null, null));
    }

    @Test
    void validatesNumericAndTextBoundaries() {
        for (double rating : new double[]{-0.1, 5.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(InvalidRestaurantSearchException.class,
                    () -> new RestaurantSearchCriteria(null, null, null, rating, null, null, 0, 20, null, null));
        }
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, -1, "INR", 0, 20, null, null));
        for (int size : new int[]{0, 101}) {
            assertThrows(InvalidRestaurantSearchException.class,
                    () -> new RestaurantSearchCriteria(null, null, null, null, null, null, 0, size, null, null));
        }
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, null, null, -1, 20, null, null));
        for (String text : new String[]{"  ", "x".repeat(201)}) {
            assertThrows(InvalidRestaurantSearchException.class,
                    () -> new RestaurantSearchCriteria(text, null, null, null, null, null, 0, 20, null, null));
        }
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, null, null, 0, 20, "votes", null));
        assertThrows(InvalidRestaurantSearchException.class,
                () -> new RestaurantSearchCriteria(null, null, null, null, null, null, 0, 20, null, "sideways"));
    }

    @Test
    void acceptsAllSortFieldsAndInclusiveLimits() {
        for (String sortBy : new String[]{"name", "city", "rating", "cost"}) {
            var criteria = new RestaurantSearchCriteria(null, null, null, 5.0, 0,
                    " INR ", 0, 100, sortBy.toUpperCase(), "DESC");
            assertEquals(sortBy, criteria.sortBy());
            assertEquals("desc", criteria.sortDir());
            assertEquals("INR", criteria.currency());
        }
    }
}
