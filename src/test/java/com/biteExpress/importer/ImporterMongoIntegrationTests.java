package com.biteExpress.importer;

import com.biteExpress.model.Restaurant;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ImporterMongoIntegrationTests {
    @TempDir Path tempDir;

    @Test
    @EnabledIfEnvironmentVariable(named = "BITEEXPRESS_TEST_MONGODB_URI", matches = ".+")
    void persistsValidRowsSkipsMalformedRowsAndIsIdempotent() throws Exception {
        String uri = System.getenv("BITEEXPRESS_TEST_MONGODB_URI");
        String database = new com.mongodb.ConnectionString(uri).getDatabase();
        assertNotNull(database);
        assertTrue(database.startsWith("biteexpress_import_test_"), "Use an isolated import test database");

        String id = "phase2-test-" + UUID.randomUUID();
        String source = Files.readString(Path.of("data/sample-restaurants.csv"));
        String valid = source.lines().skip(1).findFirst().orElseThrow().replaceFirst("100001", id);
        String malformed = valid.replace("Spice Garden", "");
        String laterDuplicate = valid.replace("Spice Garden", "Final Name");
        Path file = tempDir.resolve("restaurants.csv");
        Files.writeString(file, source.lines().findFirst().orElseThrow() + System.lineSeparator()
                + malformed + System.lineSeparator() + valid + System.lineSeparator()
                + laterDuplicate + System.lineSeparator());

        try (var client = MongoClients.create(uri)) {
            MongoTemplate template = new MongoTemplate(client, database);
            var importer = new SampleRestaurantImport(template, new FileSystemResource(file),
                    new FileSystemResource(Path.of("data/country-codes.csv")));
            importer.run(null);
            String persistedId = SampleRestaurantImport.idForSource(id);
            assertEquals(1, template.findAll(Restaurant.class).stream().filter(r -> persistedId.equals(r.id())).count());
            Restaurant saved = template.findById(persistedId, Restaurant.class);
            assertNotNull(saved);
            assertEquals("Final Name", saved.name());
            assertEquals("India", saved.country().name());
            assertEquals(77.2167, saved.location().getX());

            importer.run(null);
            assertEquals(1, template.findAll(Restaurant.class).stream().filter(r -> persistedId.equals(r.id())).count());
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BITEEXPRESS_TEST_MONGODB_URI", matches = ".+")
    void preservesLegacySourceIdWithoutCreatingASecondDocument() throws Exception {
        String uri = System.getenv("BITEEXPRESS_TEST_MONGODB_URI");
        String database = new com.mongodb.ConnectionString(uri).getDatabase();
        assertNotNull(database);
        assertTrue(database.startsWith("biteexpress_import_test_"), "Use an isolated import test database");

        String sourceId = "legacy-test-" + UUID.randomUUID();
        String source = Files.readString(Path.of("data/sample-restaurants.csv"));
        Path file = tempDir.resolve("legacy-restaurants.csv");
        Files.writeString(file, source.lines().findFirst().orElseThrow() + System.lineSeparator()
                + source.lines().skip(1).findFirst().orElseThrow().replaceFirst("100001", sourceId)
                + System.lineSeparator());

        try (var client = MongoClients.create(uri)) {
            MongoTemplate template = new MongoTemplate(client, database);
            template.save(new Restaurant(sourceId, "Existing legacy restaurant", null, null, null,
                    java.util.List.of(), null, null, null));
            new SampleRestaurantImport(template, new FileSystemResource(file),
                    new FileSystemResource(Path.of("data/country-codes.csv"))).run(null);

            assertEquals("Existing legacy restaurant", template.findById(sourceId, Restaurant.class).name());
            assertNull(template.findById(SampleRestaurantImport.idForSource(sourceId), Restaurant.class));
        }
    }
}
