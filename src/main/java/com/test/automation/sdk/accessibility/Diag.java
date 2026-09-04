package com.test.automation.sdk.accessibility;

import com.test.automation.sdk.accessibility.config.A11yConfig;

/**
 * Last-resort diagnostic channel that writes directly to {@code System.out},
 * bypassing SLF4J entirely.
 *
 * <p>Why this exists: the library logs through SLF4J, but a consuming project may
 * have <b>no SLF4J binding</b> on its test classpath — in which case every log
 * line is silently discarded and it looks like "nothing is happening". Turning on
 * this flag guarantees visible output no matter how (or whether) logging is wired:</p>
 *
 * <pre>
 * mvn test -Daccessibility.debug=true
 * </pre>
 *
 * <p>Disabled by default; zero overhead when off.</p>
 */
final class Diag {

    private Diag() {
    }

    static boolean enabled() {
        return A11yConfig.getBoolean("accessibility.debug", false);
    }

    /** Prints {@code [A11Y DEBUG] <msg>} to stdout when {@code accessibility.debug=true}. */
    static void print(String msg) {
        if (enabled()) {
            System.out.println("[A11Y DEBUG] " + msg);
        }
    }

    /** Simple {@code {}}-placeholder formatting (same style as SLF4J) before printing. */
    static void print(String template, Object... args) {
        if (!enabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder(template.length() + 32);
        int argIdx = 0;
        for (int i = 0; i < template.length(); i++) {
            if (i + 1 < template.length() && template.charAt(i) == '{' && template.charAt(i + 1) == '}'
                    && argIdx < args.length) {
                sb.append(args[argIdx++]);
                i++; // skip the '}'
            } else {
                sb.append(template.charAt(i));
            }
        }
        System.out.println("[A11Y DEBUG] " + sb);
    }
}

