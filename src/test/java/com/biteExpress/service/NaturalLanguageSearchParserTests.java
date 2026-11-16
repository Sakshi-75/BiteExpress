package com.biteExpress.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalLanguageSearchParserTests {
    private final NaturalLanguageSearchParser parser = new NaturalLanguageSearchParser();

    @Test
    void parsesCombinedRankingCuisineCityAndRupeeBudget() {
        var intent = parser.parse("Best North Indian restaurants in Gurugram under Rs 800 for two");
        assertEquals("Gurugram", intent.city());
        assertEquals("North Indian", intent.cuisine());
        assertEquals(800, intent.maxCostForTwo());
        assertEquals("INR", intent.currency());
        assertEquals("rating", intent.sortBy());
        assertEquals("desc", intent.sortDir());
        assertEquals(0.0, intent.minRating());
    }

    @Test
    void parsesNearestAndRadiusWithoutInventingCoordinates() {
        var intent = parser.parse("nearest Chinese restaurants within 5 km");
        assertEquals("Chinese", intent.cuisine());
        assertEquals(5.0, intent.radiusKm());
        assertEquals("distance", intent.sortBy());
        assertTrue(intent.requiresCoordinates());
        assertNull(intent.city());
    }

    @Test
    void parsesCityOnlyAndCurrencySynonym() {
        assertEquals("New Delhi", parser.parse("restaurants in New Delhi").city());
        assertEquals("Chinese", parser.parse("best Chinese food in Gurgaon").cuisine());
        var intent = parser.parse("Italian restaurants below 600 INR in Gurgaon");
        assertEquals("Italian", intent.cuisine());
        assertEquals("Gurgaon", intent.city());
        assertEquals(600, intent.maxCostForTwo());
        assertEquals("INR", intent.currency());
    }

    @Test
    void rejectsAmbiguousUnsupportedAndDishSpecificQueries() {
        for (String query : new String[]{"", "restaurants", "best nearest restaurants",
                "restaurants in Gurgaon or Delhi", "restaurants with butter chicken dish",
                "romantic restaurants", "restaurants under 500 dollars", "restaurants near Gurgaon"}) {
            assertThrows(InvalidRestaurantSearchException.class, () -> parser.parse(query), query);
        }
    }
}
