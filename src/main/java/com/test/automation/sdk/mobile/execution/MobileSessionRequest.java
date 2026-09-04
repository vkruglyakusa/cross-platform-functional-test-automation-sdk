package com.test.automation.sdk.mobile.execution;

/**
 * The minimal set of inputs needed to create a mobile driver session. Kept as a small,
 * dedicated value object (rather than loose {@code String} parameters) so that adding a
 * field later (e.g. an explicit app identifier or extra capabilities) -- or eventually
 * adapting this into a broader cross-platform execution-context type shared with the web
 * SDK -- does not require changing every {@link MobileExecutionStrategy} method signature.
 */
public final class MobileSessionRequest {

    private final String mobileOS;
    private final String deviceName;

    public MobileSessionRequest(String mobileOS, String deviceName) {
        if (mobileOS == null || mobileOS.trim().isEmpty()) {
            throw new IllegalArgumentException("mobileOS must not be null/empty");
        }
        this.mobileOS = mobileOS;
        this.deviceName = deviceName == null ? "" : deviceName;
    }

    /** "android" or "ios" (case-insensitive). */
    public String getMobileOS() {
        return mobileOS;
    }

    /** Local device/emulator name; ignored by remote strategies (the cloud device matrix comes from provider config instead). */
    public String getDeviceName() {
        return deviceName;
    }
}
