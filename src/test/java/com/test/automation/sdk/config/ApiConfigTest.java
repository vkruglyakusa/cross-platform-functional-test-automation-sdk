package com.test.automation.sdk.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("ConfigurationManager.ApiConfig")
class ApiConfigTest {

    @AfterEach
    void clearOverrides() {
        System.clearProperty("api.baseUrl");
        System.clearProperty("api.baseUrl.stg");
        System.clearProperty("api.authHeaderName");
        System.clearProperty("api.authTokenEnvVar");
        System.clearProperty("api.connectionTimeoutMillis");
        System.clearProperty("api.readTimeoutMillis");
    }

    @Test
    @DisplayName("baseUrl() prefers the per-environment key over the environment-neutral fallback")
    void prefersPerEnvironmentKey() {
        System.setProperty("api.baseUrl", "https://neutral.example.com");
        System.setProperty("api.baseUrl.stg", "https://stg.example.com");

        assertEquals("https://stg.example.com", ConfigurationManager.getApiConfig().baseUrl("stg"));
    }

    @Test
    @DisplayName("baseUrl() falls back to the environment-neutral key when no per-environment override exists")
    void fallsBackToNeutralKey() {
        System.setProperty("api.baseUrl", "https://neutral.example.com");

        assertEquals("https://neutral.example.com", ConfigurationManager.getApiConfig().baseUrl("prd"));
    }

    @Test
    @DisplayName("baseUrl() returns null when neither key is configured")
    void returnsNullWhenUnconfigured() {
        assertNull(ConfigurationManager.getApiConfig().baseUrl("stg"));
    }

    @Test
    @DisplayName("defaults are sensible when nothing is configured")
    void defaults() {
        ConfigurationManager.ApiConfig config = ConfigurationManager.getApiConfig();
        assertEquals("", config.authHeaderName());
        assertEquals("", config.authTokenEnvVar());
        assertEquals(10000, config.connectionTimeoutMillis());
        assertEquals(30000, config.readTimeoutMillis());
    }
}
