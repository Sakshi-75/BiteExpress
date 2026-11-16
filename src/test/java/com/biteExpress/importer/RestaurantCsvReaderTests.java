package com.biteExpress.importer;

import com.biteExpress.model.Country;
import com.biteExpress.model.Restaurant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantCsvReaderTests {
    private final Map<Integer, Country> countries = Map.of(1, new Country(1, "India"));

    @Test
    void mapsEverySampleField() throws Exception {
        List<Restaurant> rows = new ArrayList<>();
        var summary = new RestaurantCsvReader().scan(new StringReader(sample()), countries, rows::add);
        assertEquals(3, summary.validRows());
        Restaurant first = rows.getFirst();
        assertEquals("100001", first.id()); // The reader retains the source ID until persistence.
        assertEquals(1, first.country().code());
        assertEquals("India", first.country().name());
        assertEquals("12 Market Road, Connaught Place", first.address().line1());
        assertEquals(List.of("North Indian", "Chinese"), first.cuisines());
        assertEquals(77.2167, first.location().getX());
        assertEquals(28.6333, first.location().getY());
        assertEquals(1200, first.pricing().averageCostForTwo());
        assertEquals(4.2, first.rating().average());
        assertTrue(first.features().hasTableBooking());
        assertFalse(first.features().isDeliveringNow());
    }

    @Test
    void trimsAndHandlesOptionalFields() throws Exception {
        String csv = sample().replace("100001,Spice Garden,", "100001,\"  Spice Garden  \",")
                .replace("\"North Indian, Chinese\"", "")
                .replace(",Yes,Yes,No,No,3,", ",,Yes,No,No,3,");
        List<Restaurant> rows = new ArrayList<>();
        new RestaurantCsvReader().scan(new StringReader(csv), countries, rows::add);
        assertEquals("Spice Garden", rows.getFirst().name());
        assertEquals(List.of(), rows.getFirst().cuisines());
        assertNull(rows.getFirst().features().hasTableBooking());
    }

    @Test
    void rejectsInvalidRequiredFieldsButContinues() throws Exception {
        String[][] invalid = {
                {"100001,Spice Garden,", ",Spice Garden,", "Restaurant ID"},
                {"100001,Spice Garden,", "100001,,", "Restaurant Name"},
                {"\"North Indian, Chinese\",1200,", "\"North Indian, Chinese\",-1,", "Average Cost for two"},
                {",No,No,3,4.2,", ",No,No,5,4.2,", "Price range"},
                {",3,4.2,Green,", ",3,5.1,Green,", "Aggregate rating"},
                {",Very Good,180", ",Very Good,-1", "Votes"},
                {",Yes,Yes,No,No,3,", ",Maybe,Yes,No,No,3,", "Has Table booking"}
        };
        for (String[] entry : invalid) {
            List<Restaurant> rows = new ArrayList<>();
            var summary = new RestaurantCsvReader().scan(new StringReader(sample().replace(entry[0], entry[1])), countries, rows::add);
            assertEquals(1, summary.rejectedRows(), entry[2]);
            assertEquals(2, rows.size(), entry[2]);
            assertTrue(summary.examples().getFirst().contains(entry[2]));
        }
    }

    @Test
    void validatesCountryCodesAndBothSidesOfPriceRange() throws Exception {
        String[][] invalid = {
                {"Spice Garden,1,", "Spice Garden,0,", "Country Code"},
                {"Spice Garden,1,", "Spice Garden,bad,", "Country Code"},
                {",No,No,3,4.2,", ",No,No,0,4.2,", "Price range"}
        };
        for (String[] entry : invalid) {
            var summary = new RestaurantCsvReader().scan(new StringReader(sample().replace(entry[0], entry[1])),
                    countries, restaurant -> {});
            assertEquals(1, summary.rejectedRows(), entry[2]);
            assertTrue(summary.examples().getFirst().contains(entry[2]));
        }
    }

    @Test
    void keepsBlankOptionalFieldsAndDropsEmptyCuisineEntries() throws Exception {
        String csv = sample().replace("\"12 Market Road, Connaught Place\"", "")
                .replace("\"North Indian, Chinese\"", "\"North Indian, , Chinese\"")
                .replace("\"North Indian, , Chinese\",1200,", "\"North Indian, , Chinese\",,")
                .replace(",No,No,3,4.2,Green,Very Good,180", ",No,No,,4.2,Green,,");
        List<Restaurant> rows = new ArrayList<>();
        new RestaurantCsvReader().scan(new StringReader(csv), countries, rows::add);
        Restaurant first = rows.getFirst();
        assertNull(first.address().line1());
        assertEquals(List.of("North Indian", "Chinese"), first.cuisines());
        assertNull(first.pricing().averageCostForTwo());
        assertNull(first.pricing().priceRange());
        assertNull(first.rating().text());
        assertNull(first.rating().votes());
    }

    @Test
    void bundledFullDatasetIsStrictUtf8AndParsesWithoutReplacementCharacters() throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of("data/zomato.csv")),
                "The Docker image build intentionally excludes the full dataset");
        String csv = Files.readString(Path.of("data/zomato.csv"), StandardCharsets.UTF_8);
        assertFalse(csv.contains("\uFFFD"));
        var lookup = new CountryCsvReader().read(
                Files.newBufferedReader(Path.of("data/country-codes.csv"), StandardCharsets.UTF_8));
        var summary = new RestaurantCsvReader().scan(new StringReader(csv), lookup, restaurant -> {});
        assertEquals(9551, summary.totalRows());
        assertEquals(9551, summary.validRows());
        assertEquals(0, summary.rejectedRows());
    }

    private String sample() throws Exception { return Files.readString(Path.of("data/sample-restaurants.csv")); }
}
