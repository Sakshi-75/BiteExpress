package com.biteExpress.integration;

import com.biteExpress.repository.RestaurantRepository;
import com.mongodb.client.MongoClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.database=biteexpress_integration_test")
@AutoConfigureMockMvc
@Import(MongoIntegrationTestConfiguration.class)
class RestaurantHttpIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired RestaurantRepository repository;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired MongoClient mongoClient;
    @Autowired MongoDBContainer mongoContainer;

    @BeforeEach
    void cleanContainerDatabase() {
        MongoIntegrationTestConfiguration.assertContainerConnection(mongoTemplate, mongoClient, mongoContainer);
        repository.deleteAll();
    }

    @Test
    void postPersistsUuidAndGetReturnsTheStoredRestaurant() throws Exception {
        String json = """
                {"name":"Spice House","address":{"line1":"1 Market Road","city":"Gurgaon"},
                 "country":{"name":"India"},"location":{"latitude":28.4595,"longitude":77.0266},
                 "cuisines":["Indian","Chinese"],"pricing":{"averageCostForTwo":800,"currency":"INR"},
                 "features":{"hasOnlineDelivery":true}}
                """;
        String body = mvc.perform(post("/v1/restaurants").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.name").value("Spice House"))
                .andExpect(jsonPath("$.location.latitude").value(28.4595))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertDoesNotThrow(() -> UUID.fromString(id));
        assertEquals(1, repository.count());
        assertEquals("Spice House", repository.findById(id).orElseThrow().name());
        assertEquals(77.0266, repository.findById(id).orElseThrow().location().getX());

        mvc.perform(get("/v1/restaurants/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.address.city").value("Gurgaon"))
                .andExpect(jsonPath("$.country.name").value("India"))
                .andExpect(jsonPath("$.cuisines[0]").value("Indian"))
                .andExpect(jsonPath("$.location.longitude").value(77.0266));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mvc.perform(get("/v1/restaurants/{id}", UUID.fromString("00000000-0000-0000-0000-000000000099")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
        assertEquals(0, repository.count());
    }

    @Test
    void invalidPostReturns400WithoutPersistence() throws Exception {
        mvc.perform(post("/v1/restaurants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"address\":{\"city\":\"Gurgaon\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0, repository.count());
    }
}
