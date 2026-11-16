package com.biteExpress.importer;

import com.biteExpress.model.Restaurant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "biteexpress.import.enabled", havingValue = "true")
public class SampleRestaurantImport implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleRestaurantImport.class);
    private static final int BATCH_SIZE = 250;

    private final MongoTemplate mongoTemplate;
    private final Resource file;
    private final Resource countryFile;

    public SampleRestaurantImport(MongoTemplate mongoTemplate,
                                  @Value("${biteexpress.import.file}") Resource file,
                                  @Value("${biteexpress.import.country-file}") Resource countryFile) {
        this.mongoTemplate = mongoTemplate;
        this.file = file;
        this.countryFile = countryFile;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Instant started = Instant.now();
        try (var countryReader = new InputStreamReader(countryFile.getInputStream(), StandardCharsets.UTF_8);
             var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            var countries = new CountryCsvReader().read(countryReader);
            List<Restaurant> batch = new ArrayList<>(BATCH_SIZE);
            var counts = new ImportCounts();
            Set<String> insertedThisRun = new HashSet<>();
            var summary = new RestaurantCsvReader().scan(reader, countries, restaurant -> {
                batch.add(restaurant);
                if (batch.size() == BATCH_SIZE) saveBatch(batch, counts, insertedThisRun);
            });
            saveBatch(batch, counts, insertedThisRun);
            if (summary.validRows() == 0) throw new IllegalStateException("Import contained no valid restaurant rows: " + file.getDescription());
            log.info("Restaurant import from {}: rows={}, valid={}, inserted={}, skippedLegacy={}, skippedExisting={}, inBatchDuplicates={}, laterDuplicateUpdates={}, rejected={}, failed=0, unavailableLocations={}, duration={}ms, rejectionReasons={}, examples={}",
                    file.getDescription(), summary.totalRows(), summary.validRows(), counts.inserted, counts.skippedLegacy,
                    counts.skippedExisting, counts.inBatchDuplicates, counts.duplicateUpdates,
                    summary.rejectedRows(), summary.unavailableLocations(), Duration.between(started, Instant.now()).toMillis(),
                    summary.reasons(), summary.examples());
        }
    }

    static String idForSource(String sourceId) {
        return UUID.nameUUIDFromBytes(("biteexpress:restaurant:" + sourceId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void saveBatch(List<Restaurant> batch, ImportCounts counts, Set<String> insertedThisRun) {
        if (batch.isEmpty()) return;
        LinkedHashMap<String, Restaurant> latest = new LinkedHashMap<>();
        for (Restaurant restaurant : batch) latest.put(restaurant.id(), restaurant);
        counts.inBatchDuplicates += batch.size() - latest.size();
        Set<String> legacyIds = new HashSet<>(mongoTemplate.findDistinct(
                Query.query(Criteria.where("_id").in(latest.keySet())), "_id", Restaurant.class, String.class));
        try {
            for (Restaurant restaurant : latest.values()) {
                String sourceId = restaurant.id();
                if (legacyIds.contains(sourceId)) {
                    counts.skippedLegacy++;
                    continue;
                }
                String id = idForSource(sourceId);
                Restaurant imported = new Restaurant(id, restaurant.name(), restaurant.address(),
                        restaurant.country(), restaurant.location(), restaurant.cuisines(), restaurant.pricing(),
                        restaurant.rating(), restaurant.features());
                if (insertedThisRun.contains(id)) {
                    mongoTemplate.save(imported); // Preserve the last valid duplicate within this import only.
                    counts.duplicateUpdates++;
                    continue;
                }
                Update insertOnly = new Update().setOnInsert("name", imported.name())
                        .setOnInsert("address", imported.address()).setOnInsert("country", imported.country())
                        .setOnInsert("location", imported.location()).setOnInsert("cuisines", imported.cuisines())
                        .setOnInsert("pricing", imported.pricing()).setOnInsert("rating", imported.rating())
                        .setOnInsert("features", imported.features());
                var result = mongoTemplate.upsert(Query.query(Criteria.where("_id").is(id)), insertOnly, Restaurant.class);
                if (result.getUpsertedId() == null) counts.skippedExisting++;
                else {
                    counts.inserted++;
                    insertedThisRun.add(id);
                }
            }
        } catch (RuntimeException error) {
            log.error("Restaurant import batch failed; batchRows={}, insertedBeforeFailure={}, exact failed-record count unavailable",
                    batch.size(), counts.inserted, error);
            throw error;
        }
        batch.clear();
    }

    private static final class ImportCounts {
        long inserted;
        long skippedLegacy;
        long skippedExisting;
        long inBatchDuplicates;
        long duplicateUpdates;
    }
}
