package com.biteExpress.importer;

import com.biteExpress.model.Restaurant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        when(mongoTemplate.findDistinct(any(Query.class), eq("_id"), eq(Restaurant.class), eq(String.class)))
                .thenReturn(List.of());
        when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(Restaurant.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, new BsonString("inserted")));

        new SampleRestaurantImport(mongoTemplate,
                new FileSystemResource(csvFile),
                new FileSystemResource(countryFile)).run(null);

        verify(mongoTemplate, times(3)).upsert(any(Query.class), any(Update.class), eq(Restaurant.class));
        verify(mongoTemplate, never()).save(any(Restaurant.class));
        assertDoesNotThrow(() -> java.util.UUID.fromString(SampleRestaurantImport.idForSource("100001")));
        assertNotEquals("100001", SampleRestaurantImport.idForSource("100001"));
    }

    @Test
    void leavesExistingLegacyIdsUntouched() throws Exception {
        Path csvFile = tempDir.resolve("restaurants.csv");
        Path countryFile = tempDir.resolve("countries.csv");
        Files.copy(Path.of("data/sample-restaurants.csv"), csvFile);
        Files.copy(Path.of("data/country-codes.csv"), countryFile);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        when(mongoTemplate.findDistinct(any(Query.class), eq("_id"), eq(Restaurant.class), eq(String.class)))
                .thenReturn(List.of("100001", "100002", "100003"));

        new SampleRestaurantImport(mongoTemplate, new FileSystemResource(csvFile),
                new FileSystemResource(countryFile)).run(null);

        verify(mongoTemplate, never()).save(any(Restaurant.class));
        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(Restaurant.class));
    }
}
