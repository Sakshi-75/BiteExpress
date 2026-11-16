package com.biteExpress.integration;

import com.biteExpress.model.Address;
import com.biteExpress.model.Country;
import com.biteExpress.model.Pricing;
import com.biteExpress.model.Rating;
import com.biteExpress.model.Restaurant;
import com.biteExpress.repository.RestaurantRepository;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.mongodb.database=biteexpress_integration_test",
        "biteexpress.search.create-location-index-on-startup=true"})
@AutoConfigureMockMvc
@Import(MongoIntegrationTestConfiguration.class)
class RestaurantSearchHttpIntegrationTests {
    private static final String A = "00000000-0000-0000-0000-000000000001";
    private static final String B = "00000000-0000-0000-0000-000000000002";
    private static final String C = "00000000-0000-0000-0000-000000000003";
    private static final String D = "00000000-0000-0000-0000-000000000004";

    @Autowired MockMvc mvc;
    @Autowired RestaurantRepository repository;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired MongoClient mongoClient;
    @Autowired MongoDBContainer mongoContainer;

    @BeforeEach
    void seedOnlyTheContainerDatabase() {
        MongoIntegrationTestConfiguration.assertContainerConnection(mongoTemplate, mongoClient, mongoContainer);
        assertTrue(mongoTemplate.getCollection("restaurants").listIndexes().into(new java.util.ArrayList<>())
                .stream().anyMatch(index -> "2dsphere".equals(index.get("key", org.bson.Document.class).getString("location"))),
                "The location index must exist before the first nearby request");
        repository.deleteAll();
        repository.saveAll(List.of(
                restaurant(A, "Spice House", "Gurgaon", "Indian", 4.6, 500, "INR"),
                restaurant(B, "Spice House", "Gurgaon", "INDIAN", 4.1, 800, "INR"),
                restaurant(C, "Cafe", "Gurgaon", "Chinese", null, 300, "USD"),
                restaurant(D, "Spice Diner", "Delhi", "Indian", 4.9, 700, "INR")));
    }

    @Test
    void individualFiltersAreCaseInsensitiveAndLiteral() throws Exception {
        mvc.perform(get("/v1/restaurants").param("name", "sPiCe"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/v1/restaurants").param("city", "gUrGaOn"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/v1/restaurants").param("cuisine", "indian"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/v1/restaurants").param("minRating", "4.5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/v1/restaurants").param("maxCostForTwo", "600").param("currency", "inr"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/v1/restaurants").param("name", "Spice.*"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void combinedFiltersMissingRatingsAndEmptyResults() throws Exception {
        mvc.perform(get("/v1/restaurants").param("city", "Gurgaon").param("cuisine", "INDIAN")
                        .param("minRating", "4.5").param("maxCostForTwo", "600").param("currency", "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(A));
        mvc.perform(get("/v1/restaurants").param("cuisine", "Chinese"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].averageRating").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].location").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/v1/restaurants").param("city", "Nowhere"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void sortingPaginationCountsAndCoordinates() throws Exception {
        mvc.perform(get("/v1/restaurants").param("name", "Spice House").param("sortBy", "name")
                        .param("size", "1").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(A))
                .andExpect(jsonPath("$.content[0].location.latitude").value(28.4595))
                .andExpect(jsonPath("$.content[0].location.longitude").value(77.0266));
        mvc.perform(get("/v1/restaurants").param("name", "Spice House").param("sortBy", "name")
                        .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(B));
        mvc.perform(get("/v1/restaurants").param("sortBy", "cost").param("sortDir", "desc").param("currency", "INR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(B));
        mvc.perform(get("/v1/restaurants").param("sortBy", "rating").param("sortDir", "desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(D));
        mvc.perform(get("/v1/restaurants").param("sortBy", "city"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(D));
        mvc.perform(get("/v1/restaurants").param("size", "2").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void missingEmbeddedFieldsRemainNullInListAndDetailResponses() throws Exception {
        String id = "00000000-0000-0000-0000-000000000005";
        repository.save(new Restaurant(id, "No Metadata", null, null, null,
                null, null, null, null));
        mvc.perform(get("/v1/restaurants").param("name", "No Metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].address").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].city").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].country").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].cuisines.length()").value(0))
                .andExpect(jsonPath("$.content[0].averageRating").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].location").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/v1/restaurants/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.country").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.pricing").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rating").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.features").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void nearbySearchOrdersByDistanceUsingStartup2dsphereIndex() throws Exception {
        move(B, 77.0366);
        move(D, 77.0466);
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(A))
                .andExpect(jsonPath("$.content[0].distanceKm").value(0.0))
                .andExpect(jsonPath("$.content[1].id").value(B))
                .andExpect(jsonPath("$.content[1].distanceKm").value(closeTo(0.98, 0.1)))
                .andExpect(jsonPath("$.content[2].id").value(D))
                .andExpect(jsonPath("$.content[2].distanceKm").value(closeTo(1.95, 0.1)));
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("sortBy", "distance").param("sortDir", "desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(D));
        assertTrue(mongoTemplate.getCollection("restaurants").listIndexes().into(new java.util.ArrayList<>())
                .stream().anyMatch(index -> "2dsphere".equals(index.get("key", org.bson.Document.class).getString("location"))));
    }

    @Test
    void radiusCombinesWithExistingFiltersAndExcludesMissingLocations() throws Exception {
        move(B, 77.0366);
        move(D, 77.0466);
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("radiusKm", "1.2").param("name", "SPICE").param("city", "gUrGaOn")
                        .param("cuisine", "indian").param("minRating", "4")
                        .param("maxCostForTwo", "800").param("currency", "inr"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(A))
                .andExpect(jsonPath("$.content[1].id").value(B));
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("radiusKm", "0.5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("radiusKm", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].distanceKm").value(0.0));
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("radiusKm", "0.1").param("name", "Cafe"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/v1/restaurants").param("name", "Cafe"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].distanceKm").value(nullValue()));
    }

    @Test
    void nearbyPaginationAndExplicitNonDistanceSortRemainDeterministic() throws Exception {
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("name", "Spice House").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(B));
        move(B, 77.0366);
        move(D, 77.0466);
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].id").value(B));
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("sortBy", "rating").param("sortDir", "desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(D));
        mvc.perform(get("/v1/restaurants").param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("name", "Spice House").param("size", "1").param("page", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private void move(String id, double longitude) {
        Restaurant old = repository.findById(id).orElseThrow();
        repository.save(new Restaurant(old.id(), old.name(), old.address(), old.country(),
                new GeoJsonPoint(longitude, 28.4595), old.cuisines(), old.pricing(), old.rating(), old.features()));
    }

    private Restaurant restaurant(String id, String name, String city, String cuisine,
                                  Double rating, int cost, String currency) {
        return new Restaurant(id, name, new Address("1 Test Street", city, null, null),
                new Country(null, "India"), rating == null ? null : new GeoJsonPoint(77.0266, 28.4595),
                List.of(cuisine), new Pricing(cost, currency, null),
                rating == null ? null : new Rating(rating, null, null, 1), null);
    }
}
