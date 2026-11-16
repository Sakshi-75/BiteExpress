package com.biteExpress.importer;

import com.biteExpress.model.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.function.Consumer;

final class RestaurantCsvReader {
    private static final List<String> HEADERS = List.of("Restaurant ID", "Restaurant Name", "Country Code", "City", "Address", "Locality", "Locality Verbose", "Longitude", "Latitude", "Cuisines", "Average Cost for two", "Currency", "Has Table booking", "Has Online delivery", "Is delivering now", "Switch to order menu", "Price range", "Aggregate rating", "Rating color", "Rating text", "Votes");

    record Summary(long totalRows, long validRows, long rejectedRows, long unavailableLocations,
                   Map<String, Long> reasons, List<String> examples) {}

    Summary scan(Reader reader, Map<Integer, Country> countries, Consumer<Restaurant> consumer) throws IOException {
        var format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).get();
        long total = 0, valid = 0, rejected = 0, unavailable = 0;
        Map<String, Long> reasons = new LinkedHashMap<>();
        List<String> examples = new ArrayList<>();
        try (var records = format.parse(reader)) {
            for (String header : HEADERS) if (!records.getHeaderMap().containsKey(header)) throw new IllegalArgumentException("Missing restaurant CSV header: " + header);
            for (CSVRecord row : records) {
                total++;
                Restaurant restaurant;
                try {
                    if (!row.isConsistent()) throw new IllegalArgumentException("Column count does not match header");
                    restaurant = toRestaurant(row, countries);
                } catch (IllegalArgumentException error) {
                    rejected++;
                    reasons.merge(error.getMessage().split(": '", 2)[0], 1L, Long::sum);
                    if (examples.size() < 10) examples.add("record " + row.getRecordNumber() + ": " + error.getMessage());
                    continue;
                }
                consumer.accept(restaurant); // Let database failures propagate.
                valid++;
                if (restaurant.location() == null) unavailable++;
            }
        }
        return new Summary(total, valid, rejected, unavailable, Map.copyOf(reasons), List.copyOf(examples));
    }

    private Restaurant toRestaurant(CSVRecord row, Map<Integer, Country> countries) {
        String id = required(row, "Restaurant ID");
        int code = positiveInt(row, "Country Code");
        Country country = countries.get(code);
        if (country == null) throw new IllegalArgumentException("Country Code " + code + " has no mapping");
        String ratingText = text(row, "Rating text");
        return new Restaurant(id, required(row, "Restaurant Name"),
                new Address(nullableText(row, "Address"), nullableText(row, "City"), nullableText(row, "Locality"), nullableText(row, "Locality Verbose")),
                country, location(row), cuisines(text(row, "Cuisines")),
                new Pricing(optionalNonNegativeInt(row, "Average Cost for two"), nullableText(row, "Currency"), optionalRangedInt(row, "Price range", 1, 4)),
                new Rating(rating(row, ratingText), nullableText(row, "Rating color"), ratingText.isEmpty() ? null : ratingText, optionalNonNegativeInt(row, "Votes")),
                new RestaurantFeatures(yesNo(row, "Has Table booking"), yesNo(row, "Has Online delivery"), yesNo(row, "Is delivering now"), yesNo(row, "Switch to order menu")));
    }

    private GeoJsonPoint location(CSVRecord row) {
        Double lon = coordinate(text(row, "Longitude"), -180, 180);
        Double lat = coordinate(text(row, "Latitude"), -90, 90);
        return lon == null || lat == null || (lon == 0 && lat == 0) ? null : new GeoJsonPoint(lon, lat);
    }
    private Double coordinate(String value, double min, double max) {
        if (value.isEmpty()) return null;
        try {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) && number >= min && number <= max ? number : null;
        } catch (NumberFormatException error) { return null; }
    }
    private Double rating(CSVRecord row, String ratingText) {
        String value = text(row, "Aggregate rating");
        if (value.isEmpty() || "Not rated".equalsIgnoreCase(value)) return null;
        try {
            double number = Double.parseDouble(value);
            if (Double.isFinite(number) && number >= 0 && number <= 5) {
                return number == 0 && "Not rated".equalsIgnoreCase(ratingText) ? null : number;
            }
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Aggregate rating must be between 0 and 5: '" + value + "'");
    }
    private List<String> cuisines(String value) {
        return value.isEmpty() ? List.of() : Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
    private String text(CSVRecord row, String field) { return row.get(field).strip(); }
    private String nullableText(CSVRecord row, String field) { String value = text(row, field); return value.isEmpty() ? null : value; }
    private String required(CSVRecord row, String field) { String value = text(row, field); if (value.isEmpty()) throw new IllegalArgumentException(field + " is required"); return value; }
    private int integer(CSVRecord row, String field) { String value = required(row, field); try { return Integer.parseInt(value); } catch (NumberFormatException e) { throw new IllegalArgumentException(field + " must be a whole number: '" + value + "'", e); } }
    private int positiveInt(CSVRecord row, String field) { int value = integer(row, field); if (value <= 0) throw new IllegalArgumentException(field + " must be positive"); return value; }
    private Integer optionalNonNegativeInt(CSVRecord row, String field) { if (text(row, field).isEmpty()) return null; int value = integer(row, field); if (value < 0) throw new IllegalArgumentException(field + " must not be negative"); return value; }
    private Integer optionalRangedInt(CSVRecord row, String field, int min, int max) { if (text(row, field).isEmpty()) return null; int value = integer(row, field); if (value < min || value > max) throw new IllegalArgumentException(field + " must be between " + min + " and " + max); return value; }
    private Boolean yesNo(CSVRecord row, String field) { String value = text(row, field); if (value.isEmpty()) return null; if ("Yes".equalsIgnoreCase(value)) return true; if ("No".equalsIgnoreCase(value)) return false; throw new IllegalArgumentException(field + " must be Yes or No: '" + value + "'"); }
}
