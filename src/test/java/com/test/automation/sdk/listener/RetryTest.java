package com.test.automation.sdk.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testng.ITestResult;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Retry - retry logic and status name mapping")
class RetryTest {

    @Test
    @DisplayName("retry() returns false immediately on SUCCESS -- no retry on pass")
    void retryReturnsFalseOnSuccess() {
        Retry retry = new Retry();
        ITestResult result = Mockito.mock(ITestResult.class);
        Mockito.when(result.getStatus()).thenReturn(ITestResult.SUCCESS);
        assertFalse(retry.retry(result), "Should never retry a passing test");
    }

    @Test
    @DisplayName("retry() returns true on FAILURE while count is below maxRetry")
    void retryReturnsTrue() {
        Retry retry = new Retry();
        ITestResult result = Mockito.mock(ITestResult.class);
        Mockito.when(result.getStatus()).thenReturn(ITestResult.FAILURE);
        assertTrue(retry.retry(result), "First retry on failure should return true");
        assertTrue(retry.retry(result), "Second retry on failure should return true");
    }

    @Test
    @DisplayName("retry() returns false once maxRetry is exhausted on FAILURE")
    void retryReturnsFalseAtMax() {
        Retry retry = new Retry();
        ITestResult result = Mockito.mock(ITestResult.class);
        Mockito.when(result.getStatus()).thenReturn(ITestResult.FAILURE);
        // exhaust 2 true calls
        retry.retry(result);
        retry.retry(result);
        assertFalse(retry.retry(result), "Should return false after maxRetry");
    }

    @Test
    @DisplayName("getResultStatusName returns correct label for SUCCESS")
    void statusNameSuccess() {
        assertEquals("SUCCESS", Retry.getResultStatusName(ITestResult.SUCCESS));
    }

    @Test
    @DisplayName("getResultStatusName returns correct label for FAILURE")
    void statusNameFailure() {
        assertEquals("FAILURE", Retry.getResultStatusName(ITestResult.FAILURE));
    }

    @Test
    @DisplayName("getResultStatusName returns correct label for SKIP")
    void statusNameSkip() {
        assertEquals("SKIP", Retry.getResultStatusName(ITestResult.SKIP));
    }

    @Test
    @DisplayName("getResultStatusName returns UNKNOWN for unrecognized code")
    void statusNameUnknown() {
        assertEquals("UNKNOWN", Retry.getResultStatusName(99));
    }
}