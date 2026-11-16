package com.biteExpress.integration;

import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestConfiguration(proxyBeanMethods = false)
public class MongoIntegrationTestConfiguration {
    static final String TEST_DATABASE = "biteexpress_integration_test";

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer("mongo:8.0");
    }

    static void assertContainerConnection(MongoTemplate template, MongoClient client, MongoDBContainer container) {
        assertEquals(TEST_DATABASE, template.getDb().getName());
        assertTrue(container.isRunning(), "The isolated MongoDB container must be running");
        template.getDb().runCommand(new Document("ping", 1));
        ServerAddress expected = new ServerAddress(container.getHost(), container.getMappedPort(27017));
        assertTrue(client.getClusterDescription().getServerDescriptions().stream()
                        .anyMatch(server -> expected.equals(server.getAddress())),
                "Refusing to modify a MongoDB server outside the test container; expected " + expected);
    }
}
