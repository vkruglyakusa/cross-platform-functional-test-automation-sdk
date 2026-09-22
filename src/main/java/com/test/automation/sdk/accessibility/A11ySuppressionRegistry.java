package com.test.automation.sdk.accessibility;

import com.test.automation.sdk.accessibility.config.A11yConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry of <b>verified false-positive</b> suppressions — axe-core rule IDs
 * that a human has reviewed, determined are not real accessibility barriers,
 * and recorded a written justification for.
 *
 * <p>This is intentionally separate from {@link A11ySessionManager}'s
 * {@code accessibility.session.allowed.rules} allowlist, which silently hides
 * a rule from the inline reporter/noise banner with no audit trail and does
 * <b>not</b> affect PASS/FAIL or violation counts. A registered suppression
 * here instead:</p>
 * <ul>
 *   <li>Is <b>excluded</b> from a scan's counted violations, PASS/FAIL
 *       outcome, and totals in the generated HTML/Excel reports.</li>
 *   <li>Is still fully <b>visible</b> — listed in its own "Verified False
 *       Positives" report section together with the written reason, so the
 *       judgement itself is auditable, not just the resulting number.</li>
 *   <li><b>Expires.</b> A suppression without a periodic re-check silently
 *       rots — a page redesign could turn a real false positive into a real
 *       bug. Once {@code expires} has passed, the rule stops being
 *       suppressed (it counts again as a normal violation) and the report
 *       surfaces a banner naming which suppressions need re-verification.</li>
 * </ul>
 *
 * <h3>Supported {@code accessibility.properties} keys</h3>
 * <pre>
 * # Comma-separated axe-core rule IDs with a verified, audited justification
 * accessibility.suppression.rules=color-contrast,duplicate-id
 *
 * # Per-rule metadata (all three required for the suppression to take effect;
 * # an entry missing reason/verified/expires is logged and ignored)
 * accessibility.suppression.color-contrast.reason=Decorative swatch flagged by axe; verified by design system team as non-content
 * accessibility.suppression.color-contrast.verified=2026-08-01
 * accessibility.suppression.color-contrast.expires=2026-12-01
 *
 * accessibility.suppression.duplicate-id.reason=Third-party widget markup outside our control; vendor ticket #1234 filed
 * accessibility.suppression.duplicate-id.verified=2026-07-15
 * accessibility.suppression.duplicate-id.expires=2026-10-15
 * </pre>
 *
 * <p><b>Live enforcement + report generation.</b> A verified suppression is excluded
 * from the configurable {@code accessibility.mode=fail-test} enforcement decision
 * (see {@link AccessibilityChecker#getEnforcementMode()}) — a verified false positive
 * must never fail a test, live or in aggregate — as well as from the aggregate
 * HTML/Excel summary reports produced at the end of a run. {@link
 * AccessibilityChecker#assertNoViolations} is the one deliberate exception: it is a
 * hard, always-fail assertion API that intentionally ignores both this registry and
 * {@code accessibility.mode}, so a test that calls it always sees the real,
 * unsuppressed count.</p>
 */
public final class A11ySuppressionRegistry {

    private static final Logger log = LoggerFactory.getLogger(A11ySuppressionRegistry.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    static {
        load();
    }

    private A11ySuppressionRegistry() {
    }

    /** Re-reads all {@code accessibility.suppression.*} keys from {@link A11yConfig}. */
    public static synchronized void load() {
        ENTRIES.clear();
        String rulesRaw = A11yConfig.get("accessibility.suppression.rules");
        if (rulesRaw == null || rulesRaw.trim().isEmpty()) {
            return;
        }
        for (String ruleId : rulesRaw.split(",")) {
            String id = ruleId.trim();
            if (id.isEmpty()) continue;

            String reason   = A11yConfig.get("accessibility.suppression." + id + ".reason");
            String verified = A11yConfig.get("accessibility.suppression." + id + ".verified");
            String expires  = A11yConfig.get("accessibility.suppression." + id + ".expires");

            if (isBlank(reason) || isBlank(verified) || isBlank(expires)) {
                log.warn("[A11Y SUPPRESSION] Rule '{}' is listed in accessibility.suppression.rules but is "
                        + "missing reason/verified/expires — ignoring (all three are required).", id);
                continue;
            }

            try {
                LocalDate verifiedOn = LocalDate.parse(verified.trim(), DATE_FMT);
                LocalDate expiresOn  = LocalDate.parse(expires.trim(), DATE_FMT);
                ENTRIES.put(id, new Entry(id, reason.trim(), verifiedOn, expiresOn));
            } catch (Exception ex) {
                log.warn("[A11Y SUPPRESSION] Rule '{}' has an unparseable verified/expires date "
                        + "(expected yyyy-MM-dd) — ignoring: {}", id, ex.getMessage());
            }
        }
    }

    /** Test-only: clears all loaded entries without needing config-file changes. */
    static void reset() {
        ENTRIES.clear();
    }

    /**
     * Returns {@code true} when {@code ruleId} has a valid, not-yet-expired
     * suppression entry — i.e. its violations should be excluded from counted
     * totals/outcome and shown only in the "Verified False Positives" section.
     */
    public static boolean isActivelySuppressed(String ruleId) {
        if (ruleId == null) return false;
        Entry e = ENTRIES.get(ruleId.trim());
        return e != null && !e.isExpired();
    }

    /** Returns the suppression entry for {@code ruleId}, or {@code null} if none is registered. */
    public static Entry get(String ruleId) {
        return ruleId == null ? null : ENTRIES.get(ruleId.trim());
    }

    /** All registered entries whose {@code expires} date has not yet passed. */
    public static Collection<Entry> getActiveEntries() {
        return ENTRIES.values().stream().filter(e -> !e.isExpired())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * All registered entries whose {@code expires} date has passed — these no
     * longer suppress anything (their violations count normally again) but are
     * still surfaced so the report can flag "needs re-verification".
     */
    public static Collection<Entry> getExpiredEntries() {
        return ENTRIES.values().stream().filter(Entry::isExpired)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** A single verified false-positive suppression with its audit trail. */
    public static final class Entry {
        public final String ruleId;
        public final String reason;
        public final LocalDate verifiedOn;
        public final LocalDate expiresOn;

        private Entry(String ruleId, String reason, LocalDate verifiedOn, LocalDate expiresOn) {
            this.ruleId = ruleId;
            this.reason = reason;
            this.verifiedOn = verifiedOn;
            this.expiresOn = expiresOn;
        }

        public boolean isExpired() {
            return expiresOn.isBefore(LocalDate.now());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
