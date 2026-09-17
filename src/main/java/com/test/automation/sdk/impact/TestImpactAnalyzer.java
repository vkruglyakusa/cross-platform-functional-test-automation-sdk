package com.test.automation.sdk.impact;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maps a set of changed source files to the set of test classes that could
 * be affected by that change, using {@link JavaSourceIndexer}'s heuristic
 * source-reference graph.
 *
 * <p>A test class is considered "affected" when it is reachable by
 * following reference edges (transitively, in reverse) from any changed
 * class, or when the changed file is itself a test class. If any changed
 * file cannot be resolved to a class known to the index (e.g. a non-Java
 * file such as {@code pom.xml}, a YAML config, or test data), this analyzer
 * conservatively reports {@link ImpactResult#isFullSuiteRecommended()} so
 * callers fail safe (run everything) instead of silently under-testing.</p>
 */
public class TestImpactAnalyzer {

    private final JavaSourceIndexer index;
    private final Pattern testClassNamePattern;
    private final Set<Path> testSourceRoots;

    public TestImpactAnalyzer(JavaSourceIndexer index, Pattern testClassNamePattern, Collection<Path> testSourceRoots) {
        this.index = index;
        this.testClassNamePattern = testClassNamePattern;
        this.testSourceRoots = new HashSet<>();
        for (Path root : testSourceRoots) {
            this.testSourceRoots.add(root.toAbsolutePath().normalize());
        }
    }

    /**
     * @param changedFiles paths (absolute or relative to the current working directory) of files
     *                      reported changed, typically from {@code git diff --name-only}
     */
    public ImpactResult findAffectedTests(Collection<Path> changedFiles) {
        Set<String> changedClasses = new HashSet<>();
        List<Path> unresolvedFiles = new ArrayList<>();

        for (Path file : changedFiles) {
            if (!file.toString().endsWith(".java")) {
                unresolvedFiles.add(file);
                continue;
            }
            String fqcn = index.classNameForFile(file);
            if (fqcn == null) {
                unresolvedFiles.add(file);
                continue;
            }
            changedClasses.add(fqcn);
        }

        Map<String, Set<String>> reverseGraph = buildReverseGraph();
        Set<String> reached = new HashSet<>(changedClasses);
        Deque<String> queue = new ArrayDeque<>(changedClasses);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String dependent : reverseGraph.getOrDefault(current, Collections.emptySet())) {
                if (reached.add(dependent)) {
                    queue.add(dependent);
                }
            }
        }

        Set<String> affectedTests = new HashSet<>();
        for (String fqcn : reached) {
            if (isTestClass(fqcn)) {
                affectedTests.add(fqcn);
            }
        }

        boolean fullSuiteRecommended = !unresolvedFiles.isEmpty();
        return new ImpactResult(affectedTests, unresolvedFiles, fullSuiteRecommended);
    }

    private boolean isTestClass(String fqcn) {
        if (!testClassNamePattern.matcher(simpleName(fqcn)).matches()) {
            return false;
        }
        Path file = index.fileFor(fqcn);
        if (file == null) {
            return false;
        }
        Path absolute = file.toAbsolutePath().normalize();
        for (Path root : testSourceRoots) {
            if (absolute.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Set<String>> buildReverseGraph() {
        Map<String, Set<String>> reverse = new HashMap<>();
        for (String fqcn : index.allIndexedClasses()) {
            for (String referenced : index.referencesOf(fqcn)) {
                reverse.computeIfAbsent(referenced, k -> new HashSet<>()).add(fqcn);
            }
        }
        return reverse;
    }

    private static String simpleName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }
}
