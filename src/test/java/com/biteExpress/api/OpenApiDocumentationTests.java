package com.biteExpress.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.mongodb.uri=mongodb://127.0.0.1:1/biteexpress_test_guard?serverSelectionTimeoutMS=1000")
class OpenApiDocumentationTests {
    @Value("${local.server.port}") int port;

    @Test
    void servesSwaggerUiAndDocumentsTheImplementedEndpoints() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> ui = get(client, "/swagger-ui/index.html");
            assertEquals(200, ui.statusCode());
            assertTrue(ui.body().contains("Swagger UI"));

            HttpResponse<String> spec = get(client, "/v3/api-docs");
            assertEquals(200, spec.statusCode(), spec.body());
            String json = spec.body();
            assertTrue(json.contains("BiteExpress Restaurant Discovery API"));
            assertTrue(json.contains("\"version\":\"v1\""));
            assertTrue(json.contains("/v1/restaurants"));
            assertTrue(json.contains("/v1/restaurants/{id}"));
            assertTrue(json.contains("\"get\""));
            assertTrue(json.contains("maxCostForTwo"));
            assertTrue(json.contains("radiusKm"));
            assertTrue(json.contains("latitude"));
            assertTrue(json.contains("longitude"));
            assertTrue(json.contains("distanceKm"));
            assertTrue(json.contains("\"201\""));
            assertTrue(json.contains("\"200\""));
            assertTrue(json.contains("\"400\""));
            assertTrue(json.contains("\"404\""));
            assertTrue(json.contains("Spice House"));
            assertTrue(json.contains("/v1/restaurants/search"));
            assertTrue(json.contains("nearest Chinese restaurants within 5 km"));
            java.util.List<String> naturalParameters = JsonPath.read(json,
                    "$.paths['/v1/restaurants/search'].get.parameters[*].name");
            assertTrue(naturalParameters.containsAll(java.util.List.of("q", "city", "cuisine",
                    "latitude", "longitude", "radiusKm", "page", "size", "sortBy", "sortDir")));
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
