package com.biteExpress.importer;

import com.biteExpress.model.Country;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

final class CountryCsvReader {

    Map<Integer, Country> read(Reader reader) throws IOException {
        var format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .get();

        Map<Integer, Country> countries = new HashMap<>();
        try (var records = format.parse(reader)) {
            for (CSVRecord row : records) {
                try {
                    int code = Integer.parseInt(row.get("Country Code").strip());
                    String name = row.get("Country").strip();
                    if (code <= 0) {
                        throw new IllegalArgumentException("Country Code must be positive");
                    }
                    if (name.isEmpty()) {
                        throw new IllegalArgumentException("Country is required");
                    }
                    if (countries.putIfAbsent(code, new Country(code, name)) != null) {
                        throw new IllegalArgumentException("Duplicate Country Code " + code);
                    }
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException(
                            "Invalid country CSV record " + row.getRecordNumber() + ": " + error.getMessage(), error);
                }
            }
        }
        return countries;
    }
}
