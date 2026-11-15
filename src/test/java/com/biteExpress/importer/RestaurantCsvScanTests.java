package com.biteExpress.importer;

import com.biteExpress.model.Country;
import com.biteExpress.model.Restaurant;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestaurantCsvScanTests {

    private final Map<Integer, Country> countries = Map.of(1, new Country(1, "India"));

    @Test
    void keepsUnratedRestaurantsWithoutInventingAZeroRating() throws Exception {
        String csv = sample().replace(",3,4.2,Green,Very Good,180", ",3,0,White,Not rated,0");
        List<Restaurant> restaurants = new ArrayList<>();

        var summary = new RestaurantCsvReader().scan(new StringReader(csv), countries, restaurants::add);

        assertEquals(3, summary.validRows());
        assertEquals(0, summary.rejectedRows());
        assertNull(restaurants.getFirst().rating().average());
        assertEquals("Not rated", restaurants.getFirst().rating().text());
    }

    @Test
    void treatsMissingInvalidAndZeroCoordinatesAsUnavailable() throws Exception {
        for (String coordinates : List.of("0,0", "181,28.6333", ",28.6333")) {
            String csv = sample().replace("77.2167,28.6333", coordinates);
            List<Restaurant> restaurants = new ArrayList<>();

            var summary = new RestaurantCsvReader().scan(new StringReader(csv), countries, restaurants::add);

            assertEquals(3, summary.validRows());
            assertEquals(1, summary.unavailableLocations());
            assertNull(restaurants.getFirst().location());
        }
    }

    @Test
    void parsesEmbeddedQuotesAndCommas() throws Exception {
        String csv = sample().replace("Spice Garden", "\"Cafe \"\"Quoted\"\", Place\"");
        List<Restaurant> restaurants = new ArrayList<>();

        new RestaurantCsvReader().scan(new StringReader(csv), countries, restaurants::add);

        assertEquals("Cafe \"Quoted\", Place", restaurants.getFirst().name());
    }

    @Test
    void rejectsMissingNameAndUnknownCountryWithRecordReasons() throws Exception {
        String csv = sample()
                .replace("100001,Spice Garden,", "100001,,")
                .replace("100002,Cafe Willow,1,", "100002,Cafe Willow,999,");
        List<Restaurant> restaurants = new ArrayList<>();

        var summary = new RestaurantCsvReader().scan(new StringReader(csv), countries, restaurants::add);

        assertEquals(3, summary.totalRows());
        assertEquals(1, summary.validRows());
        assertEquals(2, summary.rejectedRows());
        assertEquals(2, summary.examples().size());
        assertTrue(summary.examples().get(0).contains("Restaurant Name"));
        assertTrue(summary.examples().get(1).contains("Country Code"));
    }

    private String sample() throws Exception {
        return Files.readString(Path.of("data/sample-restaurants.csv"));
    }
}
