package com.test.automation.sdk.execution;

/**
 * The minimal set of inputs needed to create a driver session, for either
 * platform. Generalizes
 * {@code com.test.automation.sdk.mobile.execution.MobileSessionRequest}, whose
 * own Javadoc anticipated exactly this: "eventually adapting this into a
 * broader cross-platform execution-context type shared with the web SDK".
 *
 * Kept as a small, immutable value object (rather than loose {@code String}
 * parameters) so that adding a field later does not require changing every
 * {@link SessionFactory} method signature.
 */
public final class ExecutionContext {

    private final Platform platform;
    private final RunMode runMode;
    private final String browserName;
    private final String deviceName;

    private ExecutionContext(Platform platform, RunMode runMode, String browserName, String deviceName) {
        if (platform == null) {
            throw new IllegalArgumentException("platform must not be null");
        }
        if (runMode == null) {
            throw new IllegalArgumentException("runMode must not be null");
        }
        this.platform = platform;
        this.runMode = runMode;
        this.browserName = browserName == null ? "" : browserName;
        this.deviceName = deviceName == null ? "" : deviceName;
    }

    /** Creates a context for {@link Platform#WEB}. {@code runMode} defaults to {@link RunMode#resolve()} when null. */
    public static ExecutionContext forWeb(String browserName, RunMode runMode) {
        if (browserName == null || browserName.trim().isEmpty()) {
            throw new IllegalArgumentException("browserName must not be null/empty");
        }
        return new ExecutionContext(Platform.WEB, runMode == null ? RunMode.resolve() : runMode, browserName, null);
    }

    /** Creates a context for {@link Platform#ANDROID} or {@link Platform#IOS}. {@code runMode} defaults to {@link RunMode#resolve()} when null. */
    public static ExecutionContext forMobile(Platform platform, String deviceName, RunMode runMode) {
        if (platform != Platform.ANDROID && platform != Platform.IOS) {
            throw new IllegalArgumentException("platform must be ANDROID or IOS for a mobile context, was: " + platform);
        }
        return new ExecutionContext(platform, runMode == null ? RunMode.resolve() : runMode, null, deviceName);
    }

    public Platform getPlatform() {
        return platform;
    }

    public RunMode getRunMode() {
        return runMode;
    }

    /** Browser name ("chrome"/"firefox"/"edge"); empty for mobile contexts. */
    public String getBrowserName() {
        return browserName;
    }

    /** Local device/emulator/simulator name; empty for web contexts, and ignored by remote strategies. */
    public String getDeviceName() {
        return deviceName;
    }

    @Override
    public String toString() {
        return "ExecutionContext{platform=" + platform + ", runMode=" + runMode
                + ", browserName='" + browserName + "', deviceName='" + deviceName + "'}";
    }
}
