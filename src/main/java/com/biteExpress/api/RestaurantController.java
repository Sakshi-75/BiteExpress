package com.biteExpress.api;

import com.biteExpress.service.RestaurantService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/restaurants")
@Tag(name = "Restaurants", description = "Create restaurants and retrieve them by ID")
public class RestaurantController {
    private final RestaurantService service;

    public RestaurantController(RestaurantService service) {
        this.service = service;
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
            @ApiResponse(responseCode = "400", description = "Validation failed or request JSON is invalid", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public RestaurantResponse create(@Valid @RequestBody CreateRestaurantRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a restaurant by ID", description = "Returns the complete stored restaurant, whether created through the API or imported from CSV.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restaurant found", content = @Content(schema = @Schema(implementation = RestaurantResponse.class))),
            @ApiResponse(responseCode = "404", description = "No restaurant has this ID", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public RestaurantResponse get(@Parameter(description = "Stored restaurant ID (UUID for current records)", example = "c4a8b35e-8594-44d3-919f-dd48cdb191bc") @PathVariable String id) {
        return service.get(id);
    }
}
