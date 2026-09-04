package com.test.automation.sdk.mobile.execution;

import io.appium.java_client.AppiumDriver;

/**
 * A pluggable execution strategy for creating mobile {@link AppiumDriver} sessions.
 * Exactly one implementation exists per {@link ExecutionTarget} value.
 *
 * Tests and page objects never see this interface directly -- they only call
 * {@code MobileTestBase}/{@code MobileDriverFactory}, which resolve and delegate to the
 * strategy matching {@link ExecutionTarget#resolve()}. Adding a new remote provider means
 * adding a new {@link ExecutionTarget} value plus a new implementation of this interface,
 * with no changes to test code.
 *
 * {@link #getTarget()} (not a plain "isRemote" boolean) is the defining contract method,
 * so that {@link ExecutionTarget#LOCAL} is represented as a first-class target rather than
 * treated as merely "the absence of a cloud provider".
 */
public interface MobileExecutionStrategy {

    /** Which {@link ExecutionTarget} this strategy implements. */
    ExecutionTarget getTarget();

    /** Creates and returns a ready-to-use Appium driver session for the given request. */
    AppiumDriver createDriver(MobileSessionRequest request);

    /** Convenience derived from {@link #getTarget()}; true for any non-local (cloud) target. */
    default boolean isRemote() {
        return getTarget() != ExecutionTarget.LOCAL;
    }
}
