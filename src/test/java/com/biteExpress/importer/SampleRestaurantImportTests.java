package com.biteExpress.importer;

import com.biteExpress.model.Restaurant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    }
}
