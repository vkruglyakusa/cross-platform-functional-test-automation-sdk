package com.test.automation.sdk.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderIdTest {

    @Test
    void normalizesWhitespaceAndCase() {
        ProviderId provider = new ProviderId("  BrowserStack  ");

        assertEquals("browserstack", provider.value());
        assertEquals("browserstack", provider.toString());
        assertEquals(new ProviderId("BROWSERSTACK"), provider);
    }

    @Test
    void acceptsAlphanumericAndHyphenIdentifiers() {
        assertEquals("custom-grid-2", new ProviderId("custom-grid-2").value());
    }

    @Test
    void rejectsBlankMalformedAndUnsupportedCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderId(null));
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("  "));
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("-custom"));
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("custom-"));
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("custom_grid"));
        assertThrows(IllegalArgumentException.class, () -> new ProviderId("custom/grid"));
    }
}
