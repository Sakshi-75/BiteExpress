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
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
            var summary = new RestaurantCsvReader().scan(reader, countries, restaurant -> {
                batch.add(restaurant);
                if (batch.size() == BATCH_SIZE) saveBatch(batch);
            });
            saveBatch(batch);
            if (summary.validRows() == 0) throw new IllegalStateException("Import contained no valid restaurant rows: " + file.getDescription());
            log.info("Restaurant import from {}: rows={}, valid={}, saved/upserted={}, rejected={}, unavailableLocations={}, duration={}ms, rejectionReasons={}, examples={}",
                    file.getDescription(), summary.totalRows(), summary.validRows(), summary.validRows(),
                    summary.rejectedRows(), summary.unavailableLocations(), Duration.between(started, Instant.now()).toMillis(),
                    summary.reasons(), summary.examples());
        }
    }

    private void saveBatch(List<Restaurant> batch) {
        for (Restaurant restaurant : batch) mongoTemplate.save(restaurant);
        batch.clear();
    }
}
