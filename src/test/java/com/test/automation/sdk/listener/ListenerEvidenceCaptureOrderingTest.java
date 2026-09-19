package com.test.automation.sdk.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.IInvokedMethod;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import com.test.automation.sdk.reporting.ExecutionReporting;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SDK v1.5.1 hardening: {@link Listener} evidence capture / Allure reporting
 * used to run entirely from {@code ITestListener#onTestFailure}. In real
 * consumer environments this ran AFTER {@code AllureTestNg}'s own
 * {@code onTestFailure} callback had already closed its current Allure test
 * case, so {@code Allure.addAttachment(...)} silently dropped the evidence
 * ("no test is running").
 *
 * The fix moves the actual capture+report dispatch into
 * {@code IInvokedMethodListener#afterInvocation}, which TestNG's TestInvoker
 * guarantees runs -- for every registered listener -- immediately after the
 * test method returns/throws and BEFORE any {@code ITestListener#onTestFailure}
 * callback fires for any listener. This ordering guarantee (not listener
 * registration order) is what keeps the Allure test case open long enough to
 * attach evidence to it.
 *
 * These tests verify the dispatch/idempotency contract using a bare-bones
 * fake {@link ITestResult}, without needing a live TestNG run or a real
 * Allure lifecycle (that end-to-end behavior was validated separately against
 * a real consumer project and a real browser).
 */
@DisplayName("Listener - failure evidence capture ordering (SDK v1.5.1 Allure-attachment fix)")
class ListenerEvidenceCaptureOrderingTest {

    /** Minimal ITestResult stub with a real attribute map (no framework needed). */
    private static ITestResult fakeFailedResult() {
        Map<String, Object> attrs = new HashMap<>();
        return (ITestResult) java.lang.reflect.Proxy.newProxyInstance(
                ITestResult.class.getClassLoader(),
                new Class<?>[]{ITestResult.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getStatus":
                            return ITestResult.FAILURE;
                        case "getAttribute":
                            return attrs.get((String) args[0]);
                        case "setAttribute":
                            attrs.put((String) args[0], args[1]);
                            return null;
                        case "getInstance":
                            return null; // no TestBase/driver available in this unit test
                        case "getThrowable":
                            return new RuntimeException("boom");
                        case "getMethod":
                            return fakeTestNGMethod();
                        case "getParameters":
                            return new Object[0];
                        case "getTestClass":
                            return Mockito.mock(org.testng.ITestClass.class);
                        default:
                            return method.getReturnType().isPrimitive() ? false : null;
                    }
                });
    }

    private static ITestNGMethod fakeTestNGMethod() {
        java.lang.reflect.Method probe;
        try {
            probe = ListenerEvidenceCaptureOrderingTest.class.getDeclaredMethod("fakeTestNGMethod");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return (ITestNGMethod) java.lang.reflect.Proxy.newProxyInstance(
                ITestNGMethod.class.getClassLoader(),
                new Class<?>[]{ITestNGMethod.class},
                (proxy, method, args) -> {
                    if ("getConstructorOrMethod".equals(method.getName())) {
                        return new org.testng.internal.ConstructorOrMethod(probe);
                    }
                    if ("getMethodName".equals(method.getName())) {
                        return "fakeFailingTestMethod";
                    }
                    return method.getReturnType().isPrimitive() ? false : null;
                });
    }

    private static IInvokedMethod fakeTestMethod() {
        return (IInvokedMethod) java.lang.reflect.Proxy.newProxyInstance(
                IInvokedMethod.class.getClassLoader(),
                new Class<?>[]{IInvokedMethod.class},
                (proxy, method, args) -> {
                    if ("isTestMethod".equals(method.getName())) {
                        return true;
                    }
                    return null;
                });
    }

    @Test
    @DisplayName("afterInvocation dispatches ExecutionReporting.onTestFailed for a failed test method")
    void afterInvocation_dispatchesOnTestFailed_forFailedTestMethod() {
        Listener listener = new Listener();
        ITestResult result = fakeFailedResult();

        try (MockedStatic<ExecutionReporting> reporting = Mockito.mockStatic(ExecutionReporting.class)) {
            listener.afterInvocation(fakeTestMethod(), result);
            reporting.verify(() -> ExecutionReporting.onTestFailed(
                    Mockito.eq(result), Mockito.any(), Mockito.any()), Mockito.times(1));
        }
    }

    @Test
    @DisplayName("onTestFailure does not double-dispatch when afterInvocation already captured evidence")
    void onTestFailure_doesNotDoubleReport_afterAfterInvocationAlreadyRan() {
        Listener listener = new Listener();
        ITestResult result = fakeFailedResult();

        try (MockedStatic<ExecutionReporting> reporting = Mockito.mockStatic(ExecutionReporting.class)) {
            // Simulates real TestNG order: afterInvocation always fires first.
            listener.afterInvocation(fakeTestMethod(), result);
            listener.onTestFailure(result);

            reporting.verify(() -> ExecutionReporting.onTestFailed(
                    Mockito.eq(result), Mockito.any(), Mockito.any()), Mockito.times(1));
        }
    }

    @Test
    @DisplayName("onTestFailure still captures evidence as a fallback if afterInvocation never ran")
    void onTestFailure_stillCapturesEvidence_asFallback() {
        Listener listener = new Listener();
        ITestResult result = fakeFailedResult();

        try (MockedStatic<ExecutionReporting> reporting = Mockito.mockStatic(ExecutionReporting.class)) {
            listener.onTestFailure(result);
            reporting.verify(() -> ExecutionReporting.onTestFailed(
                    Mockito.eq(result), Mockito.any(), Mockito.any()), Mockito.times(1));
        }
    }
}
