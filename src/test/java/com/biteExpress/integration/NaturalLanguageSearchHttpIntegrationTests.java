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

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.mongodb.database=biteexpress_integration_test",
        "biteexpress.search.create-location-index-on-startup=true"})
@AutoConfigureMockMvc
@Import(MongoIntegrationTestConfiguration.class)
class NaturalLanguageSearchHttpIntegrationTests {
    private static final String A = "00000000-0000-0000-0000-000000000011";
    private static final String B = "00000000-0000-0000-0000-000000000012";
    private static final String C = "00000000-0000-0000-0000-000000000013";
    private static final String D = "00000000-0000-0000-0000-000000000014";

    @Autowired MockMvc mvc;
    @Autowired RestaurantRepository repository;
    @Autowired MongoTemplate template;
    @Autowired MongoClient client;
    @Autowired MongoDBContainer container;

    @BeforeEach
    void seedOnlyTheContainer() {
        MongoIntegrationTestConfiguration.assertContainerConnection(template, client, container);
        org.junit.jupiter.api.Assertions.assertTrue(template.getCollection("restaurants")
                .listIndexes().into(new java.util.ArrayList<>()).stream()
                .anyMatch(index -> "2dsphere".equals(index.get("key", org.bson.Document.class).getString("location"))),
                "The location index must exist before nearby requests");
        repository.deleteAll();
        repository.saveAll(List.of(
                restaurant(A, "Spice House", "Gurgaon", "Indian", 4.6, 500, "INR", 77.0266),
                restaurant(B, "Dragon Wok", "Gurugram", "Chinese", 4.2, 800, "INR", 77.0366),
                restaurant(C, "Delhi Kitchen", "New Delhi", "North Indian", 4.9, 700, "Indian Rupees(Rs.)", 77.2167),
                restaurant(D, "Olive Table", "Gurgaon", "Italian", null, 600, "USD", null)));
    }

    @Test
    void omittedQueryUsesStructuredAndNearbyMongoSearch() throws Exception {
        mvc.perform(get("/v1/restaurants/search").param("city", "Gurgaon")
                        .param("cuisine", "Italian"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(D));
        mvc.perform(get("/v1/restaurants/search").param("latitude", "28.4595")
                        .param("longitude", "77.0266").param("radiusKm", "2")
                        .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(B));
    }

    @Test
    void inferredCityCuisineBudgetAndRatingUseExistingMongoSearch() throws Exception {
        mvc.perform(get("/v1/restaurants/search")
                        .param("q", "best Indian restaurants in Gurugram under Rs 800 for two"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(A));
        mvc.perform(get("/v1/restaurants/search")
                        .param("q", "best North Indian restaurants in New Delhi under INR 800"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(C));
    }

    @Test
    void explicitFiltersAndSortingOverrideInferences() throws Exception {
        mvc.perform(get("/v1/restaurants/search")
                        .param("q", "best Indian restaurants in Gurugram under Rs 800")
                        .param("city", "New Delhi").param("cuisine", "North Indian")
                        .param("maxCostForTwo", "700").param("sortBy", "name").param("sortDir", "asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(C));
        mvc.perform(get("/v1/restaurants/search").param("q", "restaurants in Gurugram")
                        .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/v1/restaurants/search").param("q", "top rated restaurants in Gurugram"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(A));
    }

    @Test
    void nearestSearchUsesGeoNearAndCountsOnlyLocatedRestaurants() throws Exception {
        mvc.perform(get("/v1/restaurants/search").param("q", "nearest Chinese restaurants within 5 km")
                        .param("latitude", "28.4595").param("longitude", "77.0266"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(B))
                .andExpect(jsonPath("$.content[0].distanceKm").value(closeTo(0.98, 0.1)));
        mvc.perform(get("/v1/restaurants/search").param("q", "nearest Chinese restaurants within 5 km")
                        .param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("radiusKm", "0.5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/v1/restaurants/search").param("q", "nearest restaurants")
                        .param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("size", "1").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(A));
        mvc.perform(get("/v1/restaurants/search").param("q", "Italian restaurants in Gurgaon"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(D));
    }

    @Test
    void invalidLanguageAndMissingCoordinatesReturn400WithoutWrites() throws Exception {
        for (String query : new String[]{"best nearest restaurants", "restaurants with butter chicken dish",
                "restaurants in Gurgaon or Delhi", "romantic restaurants"}) {
            mvc.perform(get("/v1/restaurants/search").param("q", query))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_SEARCH"));
        }
        mvc.perform(get("/v1/restaurants/search").param("q", "nearest restaurants"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_SEARCH"));
        org.junit.jupiter.api.Assertions.assertEquals(4, repository.count());
    }

    private Restaurant restaurant(String id, String name, String city, String cuisine, Double rating,
                                  int cost, String currency, Double longitude) {
        return new Restaurant(id, name, new Address("1 Test Street", city, null, null),
                new Country(null, "India"), longitude == null ? null : new GeoJsonPoint(longitude, 28.4595),
                List.of(cuisine), new Pricing(cost, currency, null),
                rating == null ? null : new Rating(rating, null, null, 1), null);
    }
}
