package com.biteExpress.api;

import com.biteExpress.service.RestaurantNotFoundException;
import com.biteExpress.service.RestaurantService;
import com.biteExpress.service.RestaurantSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantController.class)
@Import(RestaurantExceptionHandler.class)
class RestaurantControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean RestaurantService service;
    @MockitoBean RestaurantSearchService searchService;

    @Test
    void missingRestaurantHasStructured404() throws Exception {
        when(service.get("missing")).thenThrow(new RestaurantNotFoundException("missing"));
        mvc.perform(get("/v1/restaurants/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESTAURANT_NOT_FOUND"));
    }

    @Test
    void createsAndReturnsCompleteRestaurant() throws Exception {
        when(service.create(any())).thenReturn(response("123"));
        mvc.perform(post("/v1/restaurants").contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value("123"))
                .andExpect(jsonPath("$.name").value("Spice House"))
                .andExpect(jsonPath("$.address.city").value("Gurgaon"))
                .andExpect(jsonPath("$.country.name").value("India"))
                .andExpect(jsonPath("$.location.latitude").value(28.4595))
                .andExpect(jsonPath("$.location.longitude").value(77.0266))
                .andExpect(jsonPath("$.cuisines[0]").value("Indian"))
                .andExpect(jsonPath("$.pricing.averageCostForTwo").value(800))
                .andExpect(jsonPath("$.rating.average").value(4.4))
                .andExpect(jsonPath("$.features.hasOnlineDelivery").value(true));
    }

    @Test
    void getsImportedRestaurantById() throws Exception {
        when(service.get("100001")).thenReturn(response("100001"));
        mvc.perform(get("/v1/restaurants/100001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("100001"))
                .andExpect(jsonPath("$.country.name").value("India"))
                .andExpect(jsonPath("$.cuisines[0]").value("Indian"));
    }

    @Test
    void rejectsInvalidBodiesBeforeCallingService() throws Exception {
        List<String> invalid = List.of(
                validJson().replace("Spice House", "   "),
                validJson().replace("\"city\":\"Gurgaon\"", "\"city\":\"\""),
                validJson().replace("\"latitude\":28.4595", "\"latitude\":91"),
                validJson().replace("\"longitude\":77.0266", "\"longitude\":181"),
                validJson().replace("\"longitude\":77.0266", "\"longitude\":null"),
                validJson().replace("\"averageCostForTwo\":800", "\"averageCostForTwo\":-1"),
                validJson().replace("\"average\":4.4", "\"average\":5.1"),
                validJson().replace("\"votes\":10", "\"votes\":-1"),
                "{\"address\":{\"city\":\"Gurgaon\"}}",
                "{\"name\":\"Missing city\",\"address\":{}}"
        );
        for (String body : invalid) {
            mvc.perform(post("/v1/restaurants").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonHasInvalidRequestCode() throws Exception {
        mvc.perform(post("/v1/restaurants").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void invalidNumericTypeHasInvalidRequestCode() throws Exception {
        mvc.perform(post("/v1/restaurants").contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"averageCostForTwo\":800", "\"averageCostForTwo\":\"many\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void invalidSearchParametersReturnStructured400BeforeQuerying() throws Exception {
        List<String> invalid = List.of(
                "maxCostForTwo=100", "minRating=6", "minRating=NaN", "page=-1",
                "size=0", "size=101", "sortBy=unknown", "sortDir=sideways", "minRating=oops",
                "latitude=28", "longitude=77", "radiusKm=1", "sortBy=distance",
                "latitude=91&longitude=77", "latitude=28&longitude=181",
                "latitude=28&longitude=77&radiusKm=-1", "latitude=oops&longitude=77");
        for (String query : invalid) {
            mvc.perform(get("/v1/restaurants?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        for (String field : List.of("name", "currency")) {
            mvc.perform(get("/v1/restaurants").param(field, "  "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(searchService);
    }

    private String validJson() {
        return """
                {"name":"Spice House","address":{"city":"Gurgaon"},"country":{"name":"India"},
                 "location":{"latitude":28.4595,"longitude":77.0266},"cuisines":["Indian"],
                 "pricing":{"averageCostForTwo":800},"rating":{"average":4.4,"votes":10},
                 "features":{"hasOnlineDelivery":true}}
                """;
    }

    private RestaurantResponse response(String id) {
        return new RestaurantResponse(id, "Spice House",
                new RestaurantResponse.AddressResponse(null, "Gurgaon", null, null),
                new RestaurantResponse.CountryResponse(null, "India"),
                new RestaurantResponse.LocationResponse(28.4595, 77.0266),
                List.of("Indian"), new RestaurantResponse.PricingResponse(800, null, null),
                new RestaurantResponse.RatingResponse(4.4, null, null, 10),
                new RestaurantResponse.FeaturesResponse(null, true, null, null));
    }
}
