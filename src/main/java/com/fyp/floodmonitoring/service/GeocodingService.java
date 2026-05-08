package com.fyp.floodmonitoring.service;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.AddressComponent;
import com.google.maps.model.AddressComponentType;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

/**
 * Wraps Google Maps Geocoding API for reverse-geocoding sensor coordinates.
 *
 * Configured by env var GOOGLE_MAPS_API_KEY (or
 * google.maps.geocoding-api-key in application.yml). When the key is
 * absent the service still constructs but every call returns an empty
 * Optional — callers should treat geocoding as best-effort enrichment
 * and never fail an ingest because of it.
 *
 * Free tier: $200/month credit ≈ 40 000 reverse-geocode calls free.
 */
@Slf4j
@Service
public class GeocodingService {

    private final String apiKey;
    private GeoApiContext context;

    public GeocodingService(@Value("${google.maps.geocoding-api-key:}") String apiKey) {
        this.apiKey = (apiKey == null || apiKey.isBlank()) ? null : apiKey.trim();
    }

    @PostConstruct
    void start() {
        if (apiKey == null) {
            log.warn("[Geocoding] GOOGLE_MAPS_API_KEY is unset — reverse-geocode calls will be skipped.");
            return;
        }
        context = new GeoApiContext.Builder().apiKey(apiKey).build();
        log.info("[Geocoding] GeoApiContext initialised (key present, length={})", apiKey.length());
    }

    @PreDestroy
    void stop() {
        if (context != null) context.shutdown();
    }

    /** True iff a key is configured AND the context is alive. */
    public boolean isAvailable() {
        return context != null;
    }

    /**
     * Reverse-geocode a coordinate. Returns the best-match address line +
     * a normalised area (sublocality/locality) + state (administrative
     * area level 1) parsed out of Google's address components.
     */
    public Optional<ReverseGeocode> reverseGeocode(double lat, double lng) {
        if (!isAvailable()) return Optional.empty();
        try {
            GeocodingResult[] results = GeocodingApi
                    .reverseGeocode(context, new LatLng(lat, lng))
                    .await();
            if (results == null || results.length == 0) return Optional.empty();

            GeocodingResult best = results[0];
            String formatted = best.formattedAddress;
            String area  = pickComponent(best.addressComponents,
                    AddressComponentType.SUBLOCALITY,
                    AddressComponentType.SUBLOCALITY_LEVEL_1,
                    AddressComponentType.NEIGHBORHOOD,
                    AddressComponentType.LOCALITY);
            String state = pickComponent(best.addressComponents,
                    AddressComponentType.ADMINISTRATIVE_AREA_LEVEL_1);

            return Optional.of(new ReverseGeocode(formatted, area, state));
        } catch (ApiException | InterruptedException | IOException e) {
            log.warn("[Geocoding] reverseGeocode({}, {}) failed: {}", lat, lng, e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Returns the long_name of the first matching component, or null. */
    private static String pickComponent(AddressComponent[] components, AddressComponentType... wanted) {
        if (components == null) return null;
        for (AddressComponentType type : wanted) {
            for (AddressComponent c : components) {
                if (c.types == null) continue;
                for (AddressComponentType t : c.types) {
                    if (t == type) return c.longName;
                }
            }
        }
        return null;
    }

    /** Triple of (full address, parsed area, parsed state) returned by reverseGeocode. */
    public record ReverseGeocode(String address, String area, String state) {}
}
