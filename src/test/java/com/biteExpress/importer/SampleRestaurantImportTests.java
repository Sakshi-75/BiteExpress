package com.biteExpress.importer;

import com.biteExpress.model.Restaurant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SampleRestaurantImportTests {

    @TempDir
    Path tempDir;

    @Test
    void importsFromConfiguredFile() throws Exception {
        Path csvFile = tempDir.resolve("restaurants.csv");
        Path countryFile = tempDir.resolve("countries.csv");
        Files.copy(Path.of("data/sample-restaurants.csv"), csvFile);
        Files.copy(Path.of("data/country-codes.csv"), countryFile);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);

        new SampleRestaurantImport(mongoTemplate,
                new FileSystemResource(csvFile),
                new FileSystemResource(countryFile)).run(null);

        ArgumentCaptor<Restaurant> saved = ArgumentCaptor.forClass(Restaurant.class);
        verify(mongoTemplate, times(3)).save(saved.capture());
        assertEquals("India", saved.getAllValues().getFirst().country().name());
        assertDoesNotThrow(() -> java.util.UUID.fromString(saved.getAllValues().getFirst().id()));
        assertEquals(SampleRestaurantImport.idForSource("100001"), saved.getAllValues().getFirst().id());
        assertNotEquals("100001", saved.getAllValues().getFirst().id());
    }

    @Test
    void leavesExistingLegacyIdsUntouched() throws Exception {
        Path csvFile = tempDir.resolve("restaurants.csv");
        Path countryFile = tempDir.resolve("countries.csv");
        Files.copy(Path.of("data/sample-restaurants.csv"), csvFile);
        Files.copy(Path.of("data/country-codes.csv"), countryFile);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.exists(any(Query.class), eq(Restaurant.class))).thenReturn(true);

        new SampleRestaurantImport(mongoTemplate, new FileSystemResource(csvFile),
                new FileSystemResource(countryFile)).run(null);

        verify(mongoTemplate, never()).save(any(Restaurant.class));
    }
}
