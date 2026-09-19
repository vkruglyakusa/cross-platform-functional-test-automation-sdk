package com.test.automation.sdk.impact;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Result of {@link TestImpactAnalyzer#findAffectedTests(java.util.Collection)}.
 */
public final class ImpactResult {

    private final Set<String> affectedTestClasses;
    private final List<Path> unresolvedFiles;
    private final boolean fullSuiteRecommended;

    public ImpactResult(Set<String> affectedTestClasses, List<Path> unresolvedFiles, boolean fullSuiteRecommended) {
        this.affectedTestClasses = Collections.unmodifiableSet(new TreeSet<>(affectedTestClasses));
        this.unresolvedFiles = Collections.unmodifiableList(unresolvedFiles);
        this.fullSuiteRecommended = fullSuiteRecommended;
    }

    /** Fully qualified test class names, sorted, that are (heuristically) affected by the changed files. */
    public Set<String> getAffectedTestClasses() {
        return affectedTestClasses;
    }

    /**
     * Changed files that could not be resolved to a known Java class (non-Java files,
     * or files outside the indexed source roots). A non-empty list here is why
     * {@link #isFullSuiteRecommended()} returns {@code true} -- an unresolved change could
     * affect behavior in a way this heuristic can't trace.
     */
    public List<Path> getUnresolvedFiles() {
        return unresolvedFiles;
    }

    /**
     * When {@code true}, the caller should run the full test suite instead of (or in
     * addition to) just {@link #getAffectedTestClasses()} -- this heuristic could not
     * fully account for every changed file.
     */
    public boolean isFullSuiteRecommended() {
        return fullSuiteRecommended;
    }
}
