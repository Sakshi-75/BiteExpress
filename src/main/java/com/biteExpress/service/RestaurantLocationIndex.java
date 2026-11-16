package com.biteExpress.service;

import com.biteExpress.model.Restaurant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.stereotype.Component;

@Component
public class RestaurantLocationIndex {
    private final MongoTemplate mongoTemplate;
    private final boolean createOnStartup;

    public RestaurantLocationIndex(MongoTemplate mongoTemplate,
                                   @Value("${biteexpress.search.create-location-index-on-startup:true}") boolean createOnStartup) {
        this.mongoTemplate = mongoTemplate;
        this.createOnStartup = createOnStartup;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void initialize() {
        if (createOnStartup) ensure();
    }

    public void ensure() {
        mongoTemplate.indexOps(Restaurant.class).createIndex(
                new GeospatialIndex("location").typed(GeoSpatialIndexType.GEO_2DSPHERE));
    }
}
