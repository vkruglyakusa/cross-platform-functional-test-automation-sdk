package com.test.automation.sdk.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebElement;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MapWidgetHelper public API and provider-detection logic.
 * These tests use Mockito for WebElement so no live browser/WebDriver is required.
 */
@DisplayName("MapWidgetHelper - provider detection & API contract")
class MapWidgetHelperTest {

    @Test
    @DisplayName("MapWidgetHelper class is public and final")
    void classIsPublicFinal() {
        int mods = MapWidgetHelper.class.getModifiers();
        assertTrue(Modifier.isPublic(mods));
        assertTrue(Modifier.isFinal(mods));
    }

    @Test
    @DisplayName("PROVIDER_CSS_SIGNATURES contains all supported providers")
    void providerSignaturesContainKnownProviders() {
        assertEquals("gm-style", MapWidgetHelper.PROVIDER_CSS_SIGNATURES.get("Google Maps"));
        assertEquals("leaflet-container", MapWidgetHelper.PROVIDER_CSS_SIGNATURES.get("Leaflet"));
        assertEquals("mapboxgl-map", MapWidgetHelper.PROVIDER_CSS_SIGNATURES.get("Mapbox GL JS"));
        assertEquals("maplibregl-map", MapWidgetHelper.PROVIDER_CSS_SIGNATURES.get("MapLibre GL JS"));
        assertEquals("ol-viewport", MapWidgetHelper.PROVIDER_CSS_SIGNATURES.get("OpenLayers"));
        assertEquals("MicrosoftMap", MapWidgetHelper.PROVIDER_CSS_SIGNATURES.get("Bing Maps"));
    }

    @Test
    @DisplayName("detectProvider recognizes Google Maps container class")
    void detectsGoogleMaps() {
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("class")).thenReturn("gm-style some-other-class");
        assertEquals("Google Maps", MapWidgetHelper.detectProvider(el));
    }

    @Test
    @DisplayName("detectProvider recognizes Leaflet container class")
    void detectsLeaflet() {
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("class")).thenReturn("leaflet-container leaflet-touch");
        assertEquals("Leaflet", MapWidgetHelper.detectProvider(el));
    }

    @Test
    @DisplayName("detectProvider recognizes Mapbox GL JS container class")
    void detectsMapbox() {
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("class")).thenReturn("mapboxgl-map");
        assertEquals("Mapbox GL JS", MapWidgetHelper.detectProvider(el));
    }

    @Test
    @DisplayName("detectProvider returns UNKNOWN_PROVIDER_LABEL for unrecognized class")
    void detectsUnknownProvider() {
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("class")).thenReturn("some-custom-widget");
        assertEquals(MapWidgetHelper.UNKNOWN_PROVIDER_LABEL, MapWidgetHelper.detectProvider(el));
    }

    @Test
    @DisplayName("detectProvider handles null class attribute gracefully")
    void detectsNullClassAttribute() {
        WebElement el = mock(WebElement.class);
        when(el.getAttribute("class")).thenReturn(null);
        assertEquals(MapWidgetHelper.UNKNOWN_PROVIDER_LABEL, MapWidgetHelper.detectProvider(el));
    }

    @Test
    @DisplayName("findMarkerByLabel returns null when no matching DOM element exists")
    void findMarkerByLabelReturnsNullWhenNotFound() {
        WebElement container = mock(WebElement.class);
        when(container.findElements(org.mockito.ArgumentMatchers.any(org.openqa.selenium.By.class)))
                .thenReturn(java.util.Collections.emptyList());
        WebElement result = MapWidgetHelper.findMarkerByLabel(null, container, "Nonexistent Marker");
        assertNull(result);
    }
}
