package com.test.automation.sdk.mobile.crawler;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.pageobject.MobilePageObjectGenerator}. This static facade
 * preserves the legacy page-object-generator FQN.
 */
@Deprecated
public final class MobilePageObjectGenerator {

    private MobilePageObjectGenerator() {}

    public static String resolvePackage() {
        return com.test.automation.sdk.tools.pageobject.MobilePageObjectGenerator.resolvePackage();
    }

    public static String resolveOutputDir() {
        return com.test.automation.sdk.tools.pageobject.MobilePageObjectGenerator.resolveOutputDir();
    }

    public static String generate(String className, MobileScreenSnapshot snapshot) {
        return com.test.automation.sdk.tools.pageobject.MobilePageObjectGenerator.generate(className, snapshot.unwrap());
    }

    public static String writeToFile(String className, MobileScreenSnapshot snapshot) {
        return com.test.automation.sdk.tools.pageobject.MobilePageObjectGenerator.writeToFile(
                className, snapshot.unwrap());
    }
}
