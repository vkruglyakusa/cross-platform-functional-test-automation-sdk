package com.test.automation.sdk.mobile.crawler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.test.automation.sdk.utility.ElementCrawler;

/**
 * @deprecated Unified SDK Review Priority 3
 * ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md},
 * section 9) moved the real implementation to
 * {@link com.test.automation.sdk.tools.crawler.mobile.MobileScreenSnapshot}. This wrapper keeps
 * the legacy FQN usable while delegating to the new package.
 */
@Deprecated
public class MobileScreenSnapshot {

    private final com.test.automation.sdk.tools.crawler.mobile.MobileScreenSnapshot delegate;

    public MobileScreenSnapshot(String platform, String pageSourceXml, byte[] screenshot,
            List<MobileElementInfo> nativeElements,
            Map<String, List<ElementCrawler.ElementInfo>> webViewElements) {
        this(new com.test.automation.sdk.tools.crawler.mobile.MobileScreenSnapshot(
                platform,
                pageSourceXml,
                screenshot,
                unwrapElements(nativeElements),
                unwrapWebViewElements(webViewElements)));
    }

    MobileScreenSnapshot(com.test.automation.sdk.tools.crawler.mobile.MobileScreenSnapshot delegate) {
        this.delegate = delegate;
    }

    public String getPlatform() {
        return delegate.getPlatform();
    }

    public String getPageSourceXml() {
        return delegate.getPageSourceXml();
    }

    public byte[] getScreenshot() {
        return delegate.getScreenshot();
    }

    public List<MobileElementInfo> getNativeElements() {
        List<MobileElementInfo> converted = new ArrayList<MobileElementInfo>();
        for (com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo info : delegate.getNativeElements()) {
            converted.add(new MobileElementInfo(info));
        }
        return converted;
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<ElementCrawler.ElementInfo>> getWebViewElements() {
        return (Map<String, List<ElementCrawler.ElementInfo>>) (Map<?, ?>) delegate.getWebViewElements();
    }

    public boolean hasWebViewContent() {
        return delegate.hasWebViewContent();
    }

    com.test.automation.sdk.tools.crawler.mobile.MobileScreenSnapshot unwrap() {
        return delegate;
    }

    private static List<com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo> unwrapElements(
            List<MobileElementInfo> nativeElements) {
        List<com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo> converted =
                new ArrayList<com.test.automation.sdk.tools.crawler.mobile.MobileElementInfo>();
        if (nativeElements != null) {
            for (MobileElementInfo info : nativeElements) {
                converted.add(info.unwrap());
            }
        }
        return converted;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo>> unwrapWebViewElements(
            Map<String, List<ElementCrawler.ElementInfo>> webViewElements) {
        if (webViewElements == null) {
            return null;
        }
        Map<String, List<com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo>> converted =
                new LinkedHashMap<String, List<com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo>>();
        for (Map.Entry<String, List<ElementCrawler.ElementInfo>> entry : webViewElements.entrySet()) {
            converted.put(entry.getKey(),
                    (List<com.test.automation.sdk.tools.crawler.web.ElementCrawler.ElementInfo>) (List<?>) entry.getValue());
        }
        return converted;
    }
}
