package com.test.automation.sdk.tools.crawler.mobile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.test.automation.sdk.mobile.config.MobileConfigReader;
import com.test.automation.sdk.tools.crawler.web.ElementCrawler;

/**
 * <p><b>Unified SDK Review Priority 3</b> ({@code docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md}, section 9): this implementation was moved into the formal {@code tools.*} structure as a package reorganization only; the underlying crawler/generator logic was intentionally preserved.
 * Writes a text locator report for a {@link MobileScreenSnapshot}, mirroring the
 * desktop SDK's {@code test-output/crawler/*.txt} convention (uniqueness markers,
 * one section per screen) so the two crawlers feel familiar to the same engineers.
 */
public final class MobileCrawlerReportWriter {

    private static final Logger log = LogManager.getLogger(MobileCrawlerReportWriter.class.getName());
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private MobileCrawlerReportWriter() {}

    /** Resolution order: {@code -Dpog.mobile.reportDir} &gt; {@code mobile-config.yaml crawler.pageObject.reportDir} &gt; default. */
    public static String resolveReportDir() {
        String v = System.getProperty("pog.mobile.reportDir");
        if (v != null && !v.isEmpty()) {
            return v;
        }
        return MobileConfigReader.get("crawler.pageObject.reportDir", "test-output/crawler/");
    }

    /** Writes {@code <screenName>_<timestamp>.txt} under {@link #resolveReportDir()} and returns the file path. */
    public static String write(String screenName, MobileScreenSnapshot snapshot) {
        String dir = resolveReportDir();
        File dirFile = new File(dir);
        if (!dirFile.exists() && !dirFile.mkdirs()) {
            log.warn("Could not create mobile crawler report directory: {}", dir);
        }
        String fileName = screenName + "_" + LocalDateTime.now().format(TIMESTAMP) + ".txt";
        File outFile = new File(dirFile, fileName);
        try (Writer writer = new FileWriter(outFile)) {
            writer.write(render(screenName, snapshot));
        } catch (IOException e) {
            log.error("Failed to write mobile crawler report to {}", outFile.getAbsolutePath(), e);
        }
        return outFile.getPath();
    }

    /** Renders the report body as a string, without touching the filesystem (unit-testable). */
    public static String render(String screenName, MobileScreenSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mobile Crawler Report -- ").append(screenName).append('\n');
        sb.append("Platform: ").append(snapshot.getPlatform()).append('\n');
        sb.append("Elements found: ").append(snapshot.getNativeElements().size()).append('\n');
        sb.append("=".repeat(80)).append('\n');

        int index = 1;
        for (MobileElementInfo info : snapshot.getNativeElements()) {
            sb.append('\n').append("[").append(index++).append("] <").append(info.getTagOrClassName()).append('>');
            if (info.getText() != null && !info.getText().isEmpty()) {
                sb.append(" text=\"").append(info.getText()).append('"');
            }
            sb.append(" suggestedField=").append(info.getSuggestedFieldName()).append('\n');
            for (MobileLocatorCandidate candidate : info.getCandidates()) {
                sb.append("    - ").append(candidate.toReportLine()).append('\n');
            }
            if (!info.isResolved()) {
                sb.append("    !! UNRESOLVED -- no UNIQUE candidate; review manually before adding to a page object\n");
            }
        }

        if (snapshot.hasWebViewContent()) {
            sb.append('\n').append("=".repeat(80)).append('\n');
            sb.append("WebView content detected -- delegated to desktop ElementCrawler.\n");
            sb.append("Use com.test.automation.sdk.tools.pageobject.PageObjectGenerator.generateFromElements()\n");
            sb.append("to emit @FindBy fields for this content; it is not included in the\n");
            sb.append("@AndroidFindBy/@iOSXCUITFindBy page object emitted for the native screen.\n");
            for (Map.Entry<String, List<ElementCrawler.ElementInfo>> entry : snapshot.getWebViewElements().entrySet()) {
                sb.append('\n').append("Context: ").append(entry.getKey())
                        .append(" (").append(entry.getValue().size()).append(" elements)\n");
            }
        }
        return sb.toString();
    }
}
