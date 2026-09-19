package com.test.automation.sdk.healing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a ranked list of progressively "relaxed" XPath candidates from a single
 * failing XPath expression, used by {@link HealingElementLocator} to recover from a
 * broken locator at runtime -- with no pre-crawled fingerprint or external service
 * required.
 *
 * <p>Relaxation strategies (a candidate is produced for each applicable spot in the
 * expression, most-specific-first):
 * <ol>
 *   <li><b>Drop-one-predicate</b>: for a compound {@code [A and B and C]} predicate,
 *       try every "remove exactly one operand" combination, then every "keep exactly
 *       one operand alone" combination.</li>
 *   <li><b>Exact-to-contains</b>: convert {@code @attr='value'} to
 *       {@code contains(@attr,'value')}.</li>
 *   <li><b>Exact-text-to-contains</b>: convert {@code normalize-space(.)='value'} or
 *       {@code text()='value'} to a {@code contains(...)} variant.</li>
 * </ol>
 *
 * <p>This class only <em>proposes</em> candidates; it never asserts they are correct.
 * The caller ({@link HealingElementLocator}) is responsible for validating each
 * candidate against the live DOM and only trusting one that resolves to exactly one
 * element -- the same uniqueness bar the SDK's crawler enforces at design time.
 */
public final class LocatorRelaxationEngine {

    private static final Pattern PREDICATE = Pattern.compile("\\[([^\\[\\]]*)\\]");
    private static final Pattern ATTR_EXACT = Pattern.compile("@([\\w:-]+)\\s*=\\s*'([^']*)'");
    private static final Pattern TEXT_EXACT =
            Pattern.compile("(normalize-space\\(\\.\\)|text\\(\\))\\s*=\\s*'([^']*)'");

    private LocatorRelaxationEngine() {}

    /**
     * @param xpath the original, now-failing XPath expression (without the
     *              {@code By.xpath: } prefix Selenium's {@code By#toString()} adds)
     * @return an ordered, de-duplicated list of relaxed candidates; empty if no
     *         relaxation strategy applies (e.g. the xpath has no predicates at all)
     */
    public static List<String> relax(String xpath) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (xpath == null || xpath.trim().isEmpty()) {
            return new ArrayList<>(candidates);
        }

        addDropOnePredicate(xpath, candidates);
        addContainsVariants(xpath, candidates);

        candidates.remove(xpath);
        return new ArrayList<>(candidates);
    }

    private static void addDropOnePredicate(String xpath, LinkedHashSet<String> out) {
        Matcher m = PREDICATE.matcher(xpath);
        while (m.find()) {
            List<String> operands = splitTopLevelAnd(m.group(1));
            if (operands.size() < 2) {
                continue;
            }
            // Remove exactly one operand at a time (conservative relaxation).
            for (int i = 0; i < operands.size(); i++) {
                List<String> remaining = new ArrayList<>(operands);
                remaining.remove(i);
                out.add(replacePredicate(xpath, m, String.join(" and ", remaining)));
            }
            // Most relaxed: keep exactly one operand alone.
            for (String operand : operands) {
                out.add(replacePredicate(xpath, m, operand.trim()));
            }
        }
    }

    private static String replacePredicate(String xpath, Matcher m, String replacement) {
        return xpath.substring(0, m.start(1)) + replacement + xpath.substring(m.end(1));
    }

    /** Splits an XPath predicate on top-level {@code " and "}, ignoring occurrences inside quotes. */
    private static List<String> splitTopLevelAnd(String predicate) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        int i = 0;
        while (i < predicate.length()) {
            char c = predicate.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
            }
            if (!inQuote && predicate.regionMatches(i, " and ", 0, 5)) {
                parts.add(current.toString());
                current.setLength(0);
                i += 5;
                continue;
            }
            current.append(c);
            i++;
        }
        parts.add(current.toString());
        return parts;
    }

    private static void addContainsVariants(String xpath, LinkedHashSet<String> out) {
        addContainsVariant(xpath, ATTR_EXACT, out);
        addContainsVariant(xpath, TEXT_EXACT, out);
    }

    private static void addContainsVariant(String xpath, Pattern pattern, LinkedHashSet<String> out) {
        Matcher m = pattern.matcher(xpath);
        int from = 0;
        while (m.find(from)) {
            String target = m.group(1);
            String value = m.group(2);
            from = m.end();
            if (value == null || value.isEmpty()) {
                continue;
            }
            String replacement = "contains(" + attrPrefix(pattern, target) + ",'" + value + "')";
            out.add(xpath.substring(0, m.start()) + replacement + xpath.substring(m.end()));
        }
    }

    private static String attrPrefix(Pattern pattern, String target) {
        return pattern == ATTR_EXACT ? "@" + target : target;
    }
}
