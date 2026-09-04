package com.test.automation.sdk.mobile.execution;

/**
 * Resolves the {@link MobileExecutionStrategy} matching the current {@link ExecutionTarget}.
 * This is the single place that maps target -> strategy implementation; adding a future
 * provider (Sauce Labs, LambdaTest, Perfecto, ...) means adding one {@code case} here plus
 * one new package-private strategy class -- no other code changes.
 */
public final class MobileExecutionStrategyFactory {

    private MobileExecutionStrategyFactory() {}

    /** Resolves the strategy for {@link ExecutionTarget#resolve()}. */
    public static MobileExecutionStrategy resolve() {
        return forTarget(ExecutionTarget.resolve());
    }

    /** Resolves the strategy for an explicit target (mainly useful for tests). */
    public static MobileExecutionStrategy forTarget(ExecutionTarget target) {
        switch (target) {
            case LOCAL:
                return new LocalExecutionStrategy();
            case BROWSERSTACK:
                return new BrowserStackExecutionStrategy();
            default:
                throw new IllegalStateException("No MobileExecutionStrategy registered for target: " + target);
        }
    }
}
