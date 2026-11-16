package com.biteExpress.api;

import com.biteExpress.service.RestaurantService;
import com.biteExpress.service.RestaurantSearchCriteria;
import com.biteExpress.service.RestaurantSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/restaurants")
@Tag(name = "Restaurants", description = "Create, retrieve, and search restaurants")
public class RestaurantController {
    private final RestaurantService service;
    private final RestaurantSearchService searchService;

    public RestaurantController(RestaurantService service, RestaurantSearchService searchService) {
        this.service = service;
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(summary = "Search restaurants", description = "Combines supplied filters with AND logic. Supply latitude and longitude to search located restaurants by distance; radiusKm limits results. Nearby results default to nearest first. Results use ID as a stable tiebreaker. Missing ratings or costs do not match their respective numeric filters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching restaurants", content = @Content(schema = @Schema(implementation = RestaurantSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter (INVALID_FILTER)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":400,\"title\":\"Bad Request\",\"detail\":\"size must be between 1 and 100\",\"code\":\"INVALID_FILTER\"}")))
    })
    public RestaurantSearchResponse search(
            @Parameter(description = "Case-insensitive partial restaurant name; literal text", example = "spice") @RequestParam(required = false) String name,
            @Parameter(description = "Case-insensitive exact city", example = "Gurgaon") @RequestParam(required = false) String city,
            @Parameter(description = "Case-insensitive exact cuisine in the cuisines array", example = "Indian") @RequestParam(required = false) String cuisine,
            @Parameter(description = "Minimum average rating, from 0 to 5; unrated restaurants are excluded", example = "4.0") @RequestParam(required = false) Double minRating,
            @Parameter(description = "Maximum cost for two, nonnegative; requires currency", example = "1000") @RequestParam(required = false) Integer maxCostForTwo,
            @Parameter(description = "Case-insensitive exact currency; required with maxCostForTwo", example = "Indian Rupees(Rs.)") @RequestParam(required = false) String currency,
            @Parameter(description = "Search center latitude, -90 to 90; requires longitude", example = "28.4595") @RequestParam(required = false) Double latitude,
            @Parameter(description = "Search center longitude, -180 to 180; requires latitude", example = "77.0266") @RequestParam(required = false) Double longitude,
            @Parameter(description = "Optional maximum distance in kilometers, inclusive and nonnegative; requires coordinates", example = "5") @RequestParam(required = false) Double radiusKm,
            @Parameter(description = "Zero-based page number", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 100", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field: name, city, rating, cost, or distance (requires coordinates). Defaults to distance for nearby searches, name otherwise", example = "distance") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction: asc or desc", example = "desc") @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return searchService.search(new RestaurantSearchCriteria(name, city, cuisine, minRating,
                maxCostForTwo, currency, page, size, sortBy, sortDir, latitude, longitude, radiusKm));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a restaurant", description = "Creates a restaurant with a generated UUID. The name and address city are required; all other sections are optional.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            description = "Restaurant details. Supply latitude and longitude together when a location is provided.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateRestaurantRequest.class),
                    examples = @ExampleObject(name = "Restaurant with optional details", value = """
                            {"name":"Spice House","address":{"line1":"2 Market Road","city":"Gurgaon"},
                            "country":{"name":"India"},"location":{"latitude":28.4595,"longitude":77.0266},
                            "cuisines":["Indian","Chinese"],"pricing":{"averageCostForTwo":800,
                            "currency":"Indian Rupees(Rs.)","priceRange":2},"rating":{"average":4.4,"votes":10},
                            "features":{"hasOnlineDelivery":true}}
                            """)))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Restaurant created", content = @Content(schema = @Schema(implementation = RestaurantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed (VALIDATION_FAILED) or invalid JSON (INVALID_REQUEST)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":400,\"title\":\"Validation failed\",\"detail\":\"Restaurant request validation failed\",\"code\":\"VALIDATION_FAILED\",\"errors\":[\"name: must not be blank\"]}")))
    })
    public RestaurantResponse create(@Valid @RequestBody CreateRestaurantRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a restaurant by ID", description = "Returns the complete stored restaurant, whether created through the API or imported from CSV.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restaurant found", content = @Content(schema = @Schema(implementation = RestaurantResponse.class))),
            @ApiResponse(responseCode = "404", description = "No restaurant has this ID (RESTAURANT_NOT_FOUND)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":404,\"title\":\"Not Found\",\"detail\":\"Restaurant not found: missing\",\"code\":\"RESTAURANT_NOT_FOUND\"}")))
    })
    public RestaurantResponse get(@Parameter(description = "Stored restaurant ID (UUID for current records)", example = "c4a8b35e-8594-44d3-919f-dd48cdb191bc") @PathVariable String id) {
        return service.get(id);
    }
}
