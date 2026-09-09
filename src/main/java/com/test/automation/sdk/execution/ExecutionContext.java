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
    private final AutomationTechnology automationTechnology;
    private final String browserName;
    private final String deviceName;

    private ExecutionContext(Platform platform, RunMode runMode, AutomationTechnology automationTechnology,
            String browserName, String deviceName) {
        if (platform == null) {
            throw new IllegalArgumentException("platform must not be null");
        }
        if (runMode == null) {
            throw new IllegalArgumentException("runMode must not be null");
        }
        this.platform = platform;
        this.runMode = runMode;
        this.automationTechnology = automationTechnology == null ? defaultTechnologyFor(platform) : automationTechnology;
        this.browserName = browserName == null ? "" : browserName;
        this.deviceName = deviceName == null ? "" : deviceName;
    }

    /**
     * The technology implied by {@code platform} when the caller does not
     * specify one explicitly: {@link AutomationTechnology#SELENIUM} for
     * {@link Platform#WEB}, {@link AutomationTechnology#APPIUM} for
     * {@link Platform#ANDROID}/{@link Platform#IOS}. Mirrors
     * {@link SessionFactory#getAutomationTechnology()}'s default so a
     * context and the factory resolved for it always agree absent an
     * explicit override.
     */
    private static AutomationTechnology defaultTechnologyFor(Platform platform) {
        return platform == Platform.WEB ? AutomationTechnology.SELENIUM : AutomationTechnology.APPIUM;
    }

    /** Creates a context for {@link Platform#WEB}. {@code runMode} defaults to {@link RunMode#resolve()} when null. Technology defaults to {@link AutomationTechnology#SELENIUM}. */
    public static ExecutionContext forWeb(String browserName, RunMode runMode) {
        return forWeb(browserName, runMode, null);
    }

    /**
     * Creates a context for {@link Platform#WEB} with an explicit
     * {@link AutomationTechnology} (Unified SDK Review Priority 5, section
     * 11) -- reserved for a future second Web technology (e.g. Playwright);
     * {@code null} defaults to {@link AutomationTechnology#SELENIUM}, the
     * only Web technology implemented today.
     */
    public static ExecutionContext forWeb(String browserName, RunMode runMode, AutomationTechnology technology) {
        if (browserName == null || browserName.trim().isEmpty()) {
            throw new IllegalArgumentException("browserName must not be null/empty");
        }
        return new ExecutionContext(Platform.WEB, runMode == null ? RunMode.resolve() : runMode,
                technology, browserName, null);
    }

    /** Creates a context for {@link Platform#ANDROID} or {@link Platform#IOS}. {@code runMode} defaults to {@link RunMode#resolve()} when null. Technology defaults to {@link AutomationTechnology#APPIUM}. */
    public static ExecutionContext forMobile(Platform platform, String deviceName, RunMode runMode) {
        return forMobile(platform, deviceName, runMode, null);
    }

    /**
     * Creates a context for {@link Platform#ANDROID} or {@link Platform#IOS}
     * with an explicit {@link AutomationTechnology} (Unified SDK Review
     * Priority 5, section 11) -- reserved for a future second Mobile
     * technology; {@code null} defaults to {@link AutomationTechnology#APPIUM},
     * the only Mobile technology implemented today.
     */
    public static ExecutionContext forMobile(Platform platform, String deviceName, RunMode runMode,
            AutomationTechnology technology) {
        if (platform != Platform.ANDROID && platform != Platform.IOS) {
            throw new IllegalArgumentException("platform must be ANDROID or IOS for a mobile context, was: " + platform);
        }
        return new ExecutionContext(platform, runMode == null ? RunMode.resolve() : runMode,
                technology, null, deviceName);
    }

    public Platform getPlatform() {
        return platform;
    }

    public RunMode getRunMode() {
        return runMode;
    }

    /** The {@link AutomationTechnology} this context resolves to (see Unified SDK Review Priority 5). */
    public AutomationTechnology getAutomationTechnology() {
        return automationTechnology;
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
                + ", automationTechnology=" + automationTechnology
                + ", browserName='" + browserName + "', deviceName='" + deviceName + "'}";
    }
}
