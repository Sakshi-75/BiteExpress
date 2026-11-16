package com.biteExpress.api;

import com.biteExpress.service.UnsupportedRestaurantSearchException;
import com.biteExpress.service.NaturalLanguageSearchParser;
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
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Restaurants", description = "Create and search restaurants")
public class NaturalLanguageRestaurantController {
    private final NaturalLanguageSearchParser parser;
    private final RestaurantSearchService searchService;

    public NaturalLanguageRestaurantController(NaturalLanguageSearchParser parser, RestaurantSearchService searchService) {
        this.parser = parser;
        this.searchService = searchService;
    }

    @GetMapping("/v1/restaurants/search")
    @Operation(summary = "Search restaurants with optional natural language",
            description = "When q is supplied, parses supported city, cuisine, best-rated, nearest, and rupee-budget phrases into the same MongoDB search used by GET /v1/restaurants. Without q, searches using only explicit filters. Explicit parameters override inferred values. Dish-level and ambiguous requests return 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching restaurants", content = @Content(schema = @Schema(implementation = RestaurantSearchResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unsupported natural-language request (UNSUPPORTED_SEARCH) or invalid explicit filter (INVALID_FILTER)", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(value = "{\"status\":400,\"title\":\"Bad Request\",\"detail\":\"Dish and menu-item search is unavailable; search by cuisine\",\"code\":\"UNSUPPORTED_SEARCH\"}")))
    })
    public RestaurantSearchResponse search(
            @Parameter(description = "Optional natural-language query. Examples: best North Indian restaurants in Gurugram under Rs 800 for two; nearest Chinese restaurants within 5 km",
                    examples = {@ExampleObject(name = "Top rated", value = "best North Indian restaurants in Gurugram under Rs 800 for two"),
                            @ExampleObject(name = "Nearby", value = "nearest Chinese restaurants within 5 km")})
            @RequestParam(required = false) String q,
            @Parameter(description = "Explicit case-insensitive partial name filter") @RequestParam(required = false) String name,
            @Parameter(description = "Explicit city; overrides a city inferred from q") @RequestParam(required = false) String city,
            @Parameter(description = "Explicit cuisine; overrides a cuisine inferred from q") @RequestParam(required = false) String cuisine,
            @Parameter(description = "Minimum average rating, 0 to 5") @RequestParam(required = false) Double minRating,
            @Parameter(description = "Maximum cost for two; overrides a budget inferred from q and requires currency") @RequestParam(required = false) Integer maxCostForTwo,
            @Parameter(description = "Currency; overrides a currency inferred from q. INR and Rs match stored Indian-rupee labels") @RequestParam(required = false) String currency,
            @Parameter(description = "Search center latitude, -90 to 90; required with longitude for nearest searches") @RequestParam(required = false) Double latitude,
            @Parameter(description = "Search center longitude, -180 to 180; required with latitude for nearest searches") @RequestParam(required = false) Double longitude,
            @Parameter(description = "Maximum distance in kilometers; overrides a radius inferred from q") @RequestParam(required = false) Double radiusKm,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 100") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by name, city, rating, cost (requires currency), or distance (requires coordinates); overrides q") @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction asc or desc; overrides q") @RequestParam(required = false) String sortDir
    ) {
        if (q == null) {
            return searchService.search(new RestaurantSearchCriteria(name, city, cuisine, minRating,
                    maxCostForTwo, currency, page, size, sortBy, sortDir, latitude, longitude, radiusKm));
        }
        var intent = parser.parse(q);
        if (intent.requiresCoordinates() && (latitude == null || longitude == null))
            throw new UnsupportedRestaurantSearchException("Nearest or radius search requires latitude and longitude");
        if (intent.maxCostForTwo() != null && intent.currency() == null && currency == null)
            throw new UnsupportedRestaurantSearchException("A budget request requires an explicit currency such as INR");
        return searchService.search(new RestaurantSearchCriteria(name,
                city != null ? city : intent.city(), cuisine != null ? cuisine : intent.cuisine(),
                minRating != null ? minRating : intent.minRating(),
                maxCostForTwo != null ? maxCostForTwo : intent.maxCostForTwo(),
                currency != null ? currency : intent.currency(), page, size,
                sortBy != null ? sortBy : intent.sortBy(), sortDir != null ? sortDir : intent.sortDir(),
                latitude, longitude, radiusKm != null ? radiusKm : intent.radiusKm()));
    }
}
