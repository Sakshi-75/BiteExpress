package com.biteExpress.importer;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountryCsvReaderTests {

    @Test
    void readsCountryNamesByCode() throws Exception {
        try (var input = Files.newBufferedReader(Path.of("data/country-codes.csv"))) {
            var countries = new CountryCsvReader().read(input);

            assertEquals(15, countries.size());
            assertEquals("India", countries.get(1).name());
            assertEquals("United Kingdom", countries.get(215).name());
        }
    }

    @Test
    void rejectsMissingCountryName() {
        String csv = "Country Code,Country\n1,  \n";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CountryCsvReader().read(new StringReader(csv)));

        assertTrue(error.getMessage().contains("Country"));
    }
}
