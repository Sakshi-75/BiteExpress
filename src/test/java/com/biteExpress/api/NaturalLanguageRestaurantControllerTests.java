package com.biteExpress.api;

import com.biteExpress.service.NaturalLanguageSearchParser;
import com.biteExpress.service.RestaurantSearchCriteria;
import com.biteExpress.service.RestaurantSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NaturalLanguageRestaurantController.class)
@Import({NaturalLanguageSearchParser.class, RestaurantExceptionHandler.class})
class NaturalLanguageRestaurantControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean RestaurantSearchService searchService;

    @Test
    void explicitParametersOverrideEveryInferredFieldAndKeepPagination() throws Exception {
        when(searchService.search(any())).thenReturn(new RestaurantSearchResponse(List.of(), 2, 3, 0, 0));
        mvc.perform(get("/v1/restaurants/search")
                        .param("q", "best North Indian restaurants in Gurugram under Rs 800 for two")
                        .param("city", "New Delhi").param("cuisine", "Chinese")
                        .param("minRating", "4.5")
                        .param("maxCostForTwo", "600").param("currency", "USD")
                        .param("sortBy", "name").param("sortDir", "asc")
                        .param("page", "2").param("size", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2));
        ArgumentCaptor<RestaurantSearchCriteria> captured = ArgumentCaptor.forClass(RestaurantSearchCriteria.class);
        verify(searchService).search(captured.capture());
        var criteria = captured.getValue();
        assertEquals("New Delhi", criteria.city());
        assertEquals("Chinese", criteria.cuisine());
        assertEquals(4.5, criteria.minRating());
        assertEquals(600, criteria.maxCostForTwo());
        assertEquals("USD", criteria.currency());
        assertEquals("name", criteria.sortBy());
        assertEquals("asc", criteria.sortDir());
        assertEquals(2, criteria.page());
        assertEquals(3, criteria.size());
    }

    @Test
    void nearestRequiresCoordinatesEvenWhenSortIsOverridden() throws Exception {
        for (String query : new String[]{"nearest restaurants", "nearest Chinese restaurants within 5 km"}) {
            mvc.perform(get("/v1/restaurants/search").param("q", query).param("sortBy", "rating"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_SEARCH"));
        }
        verifyNoInteractions(searchService);
    }

    @Test
    void explicitRadiusAndSortOverrideProximityInference() throws Exception {
        when(searchService.search(any())).thenReturn(new RestaurantSearchResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/v1/restaurants/search").param("q", "nearest Chinese restaurants within 5 km")
                        .param("latitude", "28.4595").param("longitude", "77.0266")
                        .param("radiusKm", "0.5").param("sortBy", "name"))
                .andExpect(status().isOk());
        ArgumentCaptor<RestaurantSearchCriteria> captured = ArgumentCaptor.forClass(RestaurantSearchCriteria.class);
        verify(searchService).search(captured.capture());
        assertEquals(0.5, captured.getValue().radiusKm());
        assertEquals("name", captured.getValue().sortBy());
    }

    @Test
    void explicitCurrencyMakesAnOtherwiseAmbiguousBudgetUsable() throws Exception {
        when(searchService.search(any())).thenReturn(new RestaurantSearchResponse(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/v1/restaurants/search").param("q", "restaurants under 500")
                        .param("currency", "INR"))
                .andExpect(status().isOk());
        ArgumentCaptor<RestaurantSearchCriteria> captured = ArgumentCaptor.forClass(RestaurantSearchCriteria.class);
        verify(searchService).search(captured.capture());
        assertEquals(500, captured.getValue().maxCostForTwo());
        assertEquals("INR", captured.getValue().currency());
    }

    @Test
    void rejectsMissingUnsupportedAndInvalidQueries() throws Exception {
        mvc.perform(get("/v1/restaurants/search"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_SEARCH"));
        mvc.perform(get("/v1/restaurants/search").param("q", "restaurants serving butter chicken dish"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_SEARCH"));
        mvc.perform(get("/v1/restaurants/search").param("q", "restaurants under 500"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSUPPORTED_SEARCH"));
        mvc.perform(get("/v1/restaurants/search").param("q", "nearest restaurants")
                        .param("latitude", "91").param("longitude", "77"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        verifyNoInteractions(searchService);
    }
}
