package com.test.automation.sdk.tools.crawler.mobile;

import java.util.List;
import java.util.Map;

import com.test.automation.sdk.tools.crawler.web.ElementCrawler;

/**
 * <p><b>Unified SDK Review Priority 3</b> ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md}, section 9): this implementation was moved into the formal {@code tools.*} structure as a package reorganization only; the underlying crawler/generator logic was intentionally preserved.
 * Result of one {@link MobileElementCrawler#crawlCurrentScreen()} call: the raw
 * artifacts (page source, screenshot) plus the analyzed native elements and any
 * delegated WebView elements, keyed by context name.
 */
public class MobileScreenSnapshot {

    private final String platform;
    private final String pageSourceXml;
    private final byte[] screenshot;
    private final List<MobileElementInfo> nativeElements;
    private final Map<String, List<ElementCrawler.ElementInfo>> webViewElements;

    public MobileScreenSnapshot(String platform, String pageSourceXml, byte[] screenshot,
            List<MobileElementInfo> nativeElements,
            Map<String, List<ElementCrawler.ElementInfo>> webViewElements) {
        this.platform = platform;
        this.pageSourceXml = pageSourceXml;
        this.screenshot = screenshot;
        this.nativeElements = nativeElements;
        this.webViewElements = webViewElements;
    }

    public String getPlatform() {
        return platform;
    }

    public String getPageSourceXml() {
        return pageSourceXml;
    }

    public byte[] getScreenshot() {
        return screenshot;
    }

    public List<MobileElementInfo> getNativeElements() {
        return nativeElements;
    }

    public Map<String, List<ElementCrawler.ElementInfo>> getWebViewElements() {
        return webViewElements;
    }

    public boolean hasWebViewContent() {
        return webViewElements != null && !webViewElements.isEmpty();
    }
}
