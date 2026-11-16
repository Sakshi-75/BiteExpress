package com.biteExpress.integration;

import com.biteExpress.importer.SampleRestaurantImport;
import com.biteExpress.model.Restaurant;
import com.biteExpress.repository.RestaurantRepository;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.mongodb.database=biteexpress_integration_test")
@AutoConfigureMockMvc
@Import(MongoIntegrationTestConfiguration.class)
class ImporterMongoIntegrationTests {
    @TempDir Path tempDir;
    @Autowired MongoTemplate template;
    @Autowired MongoClient mongoClient;
    @Autowired MongoDBContainer mongoContainer;
    @Autowired RestaurantRepository repository;

    @BeforeEach
    void cleanContainerDatabase() {
        MongoIntegrationTestConfiguration.assertContainerConnection(template, mongoClient, mongoContainer);
        repository.deleteAll();
    }

    @Test
    void persistsValidRowsSkipsMalformedRowsAndIsIdempotent() throws Exception {
        String id = "phase2-test-001";
        String source = Files.readString(Path.of("data/sample-restaurants.csv"));
        String valid = source.lines().skip(1).findFirst().orElseThrow().replaceFirst("100001", id);
        String malformed = valid.replace("Spice Garden", "");
        String laterDuplicate = valid.replace("Spice Garden", "Final Name");
        Path file = tempDir.resolve("restaurants.csv");
        Files.writeString(file, source.lines().findFirst().orElseThrow() + System.lineSeparator()
                + malformed + System.lineSeparator() + valid + System.lineSeparator()
                + laterDuplicate + System.lineSeparator());

        var importer = new SampleRestaurantImport(template, new FileSystemResource(file),
                new FileSystemResource(Path.of("data/country-codes.csv")));
        importer.run(null);
        String persistedId = importedId(id);
        assertEquals(1, repository.count());
        Restaurant saved = template.findById(persistedId, Restaurant.class);
        assertNotNull(saved);
        assertEquals("Final Name", saved.name());
        assertEquals("India", saved.country().name());
        assertEquals(77.2167, saved.location().getX());

        importer.run(null);
        assertEquals(1, repository.count());
        assertEquals("Final Name", repository.findById(persistedId).orElseThrow().name());

        template.save(new Restaurant(persistedId, "API-edited name", saved.address(), saved.country(),
                saved.location(), saved.cuisines(), saved.pricing(), saved.rating(), saved.features()));
        importer.run(null);
        assertEquals(1, repository.count());
        assertEquals("API-edited name", repository.findById(persistedId).orElseThrow().name());
    }

    @Test
    void preservesLegacySourceIdWithoutCreatingASecondDocument() throws Exception {
        String sourceId = "legacy-test-001";
        String source = Files.readString(Path.of("data/sample-restaurants.csv"));
        Path file = tempDir.resolve("legacy-restaurants.csv");
        Files.writeString(file, source.lines().findFirst().orElseThrow() + System.lineSeparator()
                + source.lines().skip(1).findFirst().orElseThrow().replaceFirst("100001", sourceId)
                + System.lineSeparator());

        template.save(new Restaurant(sourceId, "Existing legacy restaurant", null, null, null,
                java.util.List.of(), null, null, null));
        new SampleRestaurantImport(template, new FileSystemResource(file),
                new FileSystemResource(Path.of("data/country-codes.csv"))).run(null);

        assertEquals(1, repository.count());
        assertEquals("Existing legacy restaurant", template.findById(sourceId, Restaurant.class).name());
        assertNull(template.findById(importedId(sourceId), Restaurant.class));
    }

    @Test
    void flushesMoreThanOneBatchWithoutDroppingRows() throws Exception {
        String[] lines = Files.readString(Path.of("data/sample-restaurants.csv")).split("\\R");
        StringBuilder csv = new StringBuilder(lines[0]).append('\n');
        for (int index = 0; index < 251; index++) {
            csv.append(lines[1].replaceFirst("100001", Integer.toString(200000 + index))).append('\n');
        }
        csv.append(lines[1].replaceFirst("100001", "200000")
                .replace("Spice Garden", "Last duplicate across batches")).append('\n');
        Path file = tempDir.resolve("more-than-one-batch.csv");
        Files.writeString(file, csv);

        new SampleRestaurantImport(template, new FileSystemResource(file),
                new FileSystemResource(Path.of("data/country-codes.csv"))).run(null);

        assertEquals(251, repository.count());
        assertTrue(repository.existsById(importedId("200000")));
        assertTrue(repository.existsById(importedId("200250")));
        assertEquals("Last duplicate across batches", repository.findById(importedId("200000")).orElseThrow().name());
    }

    @Test
    void importsUtf8AccentsWithoutReplacementCharacters() throws Exception {
        String[] lines = Files.readString(Path.of("data/sample-restaurants.csv")).split("\\R");
        Path file = tempDir.resolve("unicode-restaurants.csv");
        Files.writeString(file, lines[0] + "\n" + lines[1]
                .replace("Spice Garden", "Café da Sé")
                .replace("New Delhi", "São Paulo")
                .replace(",1,São Paulo,", ",30,São Paulo,") + "\n", StandardCharsets.UTF_8);

        new SampleRestaurantImport(template, new FileSystemResource(file),
                new FileSystemResource(Path.of("data/country-codes.csv"))).run(null);

        Restaurant saved = repository.findById(importedId("100001")).orElseThrow();
        assertEquals("Café da Sé", saved.name());
        assertEquals("São Paulo", saved.address().city());
        assertEquals("Brazil", saved.country().name());
    }

    @Test
    void rejectsAnImportWithNoValidRows() throws Exception {
        String[] lines = Files.readString(Path.of("data/sample-restaurants.csv")).split("\\R");
        Path file = tempDir.resolve("no-valid-rows.csv");
        Files.writeString(file, lines[0] + "\n" + lines[1].replace("Spice Garden", "") + "\n");

        assertThrows(IllegalStateException.class, () -> new SampleRestaurantImport(template,
                new FileSystemResource(file), new FileSystemResource(Path.of("data/country-codes.csv"))).run(null));
        assertEquals(0, repository.count());
    }

    private String importedId(String sourceId) {
        return UUID.nameUUIDFromBytes(("biteexpress:restaurant:" + sourceId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
