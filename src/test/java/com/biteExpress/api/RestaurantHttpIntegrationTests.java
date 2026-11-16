package com.biteExpress.api;

import com.biteExpress.repository.RestaurantRepository;
import com.mongodb.ConnectionString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "BITEEXPRESS_API_TEST_MONGODB_URI", matches = ".+")
class RestaurantHttpIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired RestaurantRepository repository;

    @Test
    void createsAndGetsPersistedRestaurantWithoutTouchingDevelopmentData() throws Exception {
        String expectedUri = System.getenv("BITEEXPRESS_API_TEST_MONGODB_URI");
        assertEquals(expectedUri, System.getenv("SPRING_MONGODB_URI"));
        String database = new ConnectionString(expectedUri).getDatabase();
        assertNotNull(database);
        assertTrue(database.startsWith("biteexpress_api_test_"), "Use an isolated API test database");

        String name = "HTTP Test " + UUID.randomUUID();
        String json = """
                {"name":"%s","address":{"city":"Gurgaon"},"country":{"name":"India"},
                 "location":{"latitude":28.4595,"longitude":77.0266},"cuisines":["Indian","Chinese"],
                 "pricing":{"averageCostForTwo":800,"currency":"Indian Rupees(Rs.)"},
                 "features":{"hasOnlineDelivery":true}}
                """.formatted(name);
        String base = "http://localhost:" + port;
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> created = client.send(HttpRequest.newBuilder(URI.create(base + "/v1/restaurants"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(201, created.statusCode(), created.body());
            String id = findId(created.body());
            assertDoesNotThrow(() -> UUID.fromString(id));
            assertTrue(created.headers().firstValue("Location").isEmpty());

            HttpResponse<String> fetched = client.send(HttpRequest.newBuilder(URI.create(base + "/v1/restaurants/" + id))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, fetched.statusCode(), fetched.body());
            assertEquals(created.body(), fetched.body());
            assertTrue(fetched.body().contains("\"latitude\":28.4595"));
            assertEquals(name, repository.findById(id).orElseThrow().name());
            assertEquals(77.0266, repository.findById(id).orElseThrow().location().getX());

            long beforeInvalid = repository.count();
            HttpResponse<String> invalid = client.send(HttpRequest.newBuilder(URI.create(base + "/v1/restaurants"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"  \",\"address\":{\"city\":\"Gurgaon\"}}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalid.statusCode(), invalid.body());
            assertTrue(invalid.body().contains("VALIDATION_FAILED"));
            assertEquals(beforeInvalid, repository.count());
        }
    }

    private String findId(String json) {
        var match = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(json);
        assertTrue(match.find(), json);
        return match.group(1);
    }
}
