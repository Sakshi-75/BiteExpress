package com.biteExpress.service;

import com.biteExpress.api.CreateRestaurantRequest;
import com.biteExpress.model.*;
import com.biteExpress.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RestaurantServiceTests {
    private final RestaurantRepository repository = mock(RestaurantRepository.class);
    private final RestaurantService service = new RestaurantService(repository);

    @Test
    void createsDocumentWithUuidIdAndLongitudeFirstLocation() {
        when(repository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateRestaurantRequest("  Spice House  ",
                new CreateRestaurantRequest.AddressRequest(" 2 Market Road ", " Gurgaon ", null, null),
                new CreateRestaurantRequest.CountryRequest(null, " India "),
                new CreateRestaurantRequest.LocationRequest(28.4595, 77.0266),
                List.of(" Indian ", "Chinese"),
                new CreateRestaurantRequest.PricingRequest(800, " Rupees ", 2),
                new CreateRestaurantRequest.RatingRequest(4.4, "Green", "Very Good", 10),
                new CreateRestaurantRequest.FeaturesRequest(true, false, false, null));

        var response = service.create(request);
        var saved = org.mockito.ArgumentCaptor.forClass(Restaurant.class);
        verify(repository).save(saved.capture());
        Restaurant document = saved.getValue();
        assertDoesNotThrow(() -> java.util.UUID.fromString(document.id()));
        assertEquals("Spice House", document.name());
        assertEquals("Gurgaon", document.address().city());
        assertNull(document.country().code());
        assertEquals("India", document.country().name());
        assertEquals(77.0266, document.location().getX());
        assertEquals(28.4595, document.location().getY());
        assertEquals(List.of("Indian", "Chinese"), document.cuisines());
        assertEquals(800, document.pricing().averageCostForTwo());
        assertEquals(4.4, document.rating().average());
        assertEquals(document.id(), response.id());
        assertEquals(28.4595, response.location().latitude());
        assertEquals(77.0266, response.location().longitude());
    }

    @Test
    void retrievesCompleteImportedRecordWithoutChangingItsIdOrValues() {
        Restaurant imported = new Restaurant("100001", "Spice Garden",
                new Address("12 Market Road", "New Delhi", "Connaught Place", "Connaught Place, New Delhi"),
                new Country(1, "India"), new GeoJsonPoint(77.2167, 28.6333),
                List.of("North Indian", "Chinese"), new Pricing(1200, "Indian Rupees(Rs.)", 3),
                new Rating(4.2, "Green", "Very Good", 180),
                new RestaurantFeatures(true, true, false, false));
        when(repository.findById("100001")).thenReturn(Optional.of(imported));

        var response = service.get("100001");

        assertEquals("100001", response.id());
        assertEquals("Connaught Place", response.address().locality());
        assertEquals(1, response.country().code());
        assertEquals("India", response.country().name());
        assertEquals(List.of("North Indian", "Chinese"), response.cuisines());
        assertEquals(28.6333, response.location().latitude());
        assertEquals(77.2167, response.location().longitude());
        assertEquals("Indian Rupees(Rs.)", response.pricing().currency());
        assertEquals(180, response.rating().votes());
        assertTrue(response.features().hasTableBooking());
    }

    @Test
    void missingIdThrowsNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(RestaurantNotFoundException.class, () -> service.get("missing"));
    }

    @Test
    void createsRestaurantWithoutOptionalSectionsAndNormalizesBlankAddress() {
        when(repository.save(any(Restaurant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CreateRestaurantRequest("Cafe",
                new CreateRestaurantRequest.AddressRequest("  ", "Delhi", " ", null),
                null, null, null, null, null, null);

        var response = service.create(request);

        assertNull(response.address().line1());
        assertNull(response.address().locality());
        assertNull(response.country());
        assertNull(response.location());
        assertEquals(List.of(), response.cuisines());
        assertNull(response.pricing());
        assertNull(response.rating());
        assertNull(response.features());
    }
}
