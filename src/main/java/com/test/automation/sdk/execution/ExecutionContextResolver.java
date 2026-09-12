package com.test.automation.sdk.execution;

import com.test.automation.sdk.config.ConfigurationManager;

/** Builds canonical execution contexts from the SDK configuration precedence chain. */
public final class ExecutionContextResolver {

    private ExecutionContextResolver() {
    }

    public static ExecutionContext forWeb(String browserName) {
        RunMode runMode = RunMode.resolve();
        ProviderId providerId = resolveProvider(runMode);
        return providerId == null
                ? ExecutionContext.forWeb(browserName, runMode)
                : ExecutionContext.forWebWithProvider(browserName, runMode, providerId);
    }

    public static ExecutionContext forMobile(Platform platform, String deviceName) {
        RunMode runMode = RunMode.resolve();
        ProviderId providerId = resolveProvider(runMode);
        return providerId == null
                ? ExecutionContext.forMobile(platform, deviceName, runMode)
                : ExecutionContext.forMobileWithProvider(platform, deviceName, runMode, providerId);
    }

    /** Returns the configured provider for canonical REMOTE mode; legacy modes need none. */
    public static ProviderId resolveProvider(RunMode runMode) {
        if (runMode != RunMode.REMOTE) {
            return null;
        }
        String configured = ConfigurationManager.resolve("execution.provider", "");
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException(
                    "execution.provider is required when run.mode=REMOTE (for example: browserstack or custom)");
        }
        return new ProviderId(configured);
    }

    /** True for both canonical and legacy BrowserStack selection. */
    public static boolean isBrowserStackConfigured() {
        RunMode runMode = RunMode.resolve();
        return runMode == RunMode.BROWSERSTACK
                || (runMode == RunMode.REMOTE
                    && new ProviderId("browserstack").equals(resolveProvider(runMode)));
    }
}
