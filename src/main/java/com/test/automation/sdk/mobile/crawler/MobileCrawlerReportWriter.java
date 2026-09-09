package com.test.automation.sdk.mobile.crawler;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerReportWriter}. This static
 * facade preserves the legacy report-writer FQN.
 */
@Deprecated
public final class MobileCrawlerReportWriter {

    private MobileCrawlerReportWriter() {}

    public static String resolveReportDir() {
        return com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerReportWriter.resolveReportDir();
    }

    public static String write(String screenName, MobileScreenSnapshot snapshot) {
        return com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerReportWriter.write(screenName, snapshot.unwrap());
    }

    public static String render(String screenName, MobileScreenSnapshot snapshot) {
        return com.test.automation.sdk.tools.crawler.mobile.MobileCrawlerReportWriter.render(screenName, snapshot.unwrap());
    }
}
