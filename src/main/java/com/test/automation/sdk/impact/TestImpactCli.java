package com.test.automation.sdk.impact;

import com.test.automation.sdk.config.ConfigurationManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Command-line entry point for test-impact analysis.
 *
 * <p>Usage (run from the consumer project root, after making changes but
 * before committing/pushing, or as a CI stage):</p>
 * <pre>
 * mvn exec:java -Dexec.mainClass="com.test.automation.sdk.impact.TestImpactCli"
 * </pre>
 *
 * <p>Diffs the working tree against {@code impact.baseRef} (default
 * {@code HEAD~1}) via {@code git diff --name-only}, builds a heuristic
 * source-reference graph over {@code impact.mainSourceDir} and
 * {@code impact.testSourceDir}, and writes a filtered TestNG suite XML
 * (containing only the classes transitively affected by the change) to
 * {@code impact.outputSuiteFile}. Run just the impacted tests with:</p>
 * <pre>
 * mvn test -Dsurefire.suiteXmlFiles=test-output/impact/impact_suite.xml
 * </pre>
 *
 * <p>If any changed file cannot be resolved to a known Java class (a
 * non-Java file, or a file outside the indexed source roots), the CLI
 * prints a clear warning and recommends running the full suite instead --
 * this heuristic never silently narrows test coverage without saying so.</p>
 *
 * <p>All git interaction and console output live only in {@link #main(String[])};
 * {@link #analyze(List, Path, Path, Path, Pattern)} is the pure, unit-testable core.</p>
 */
public final class TestImpactCli {

    private TestImpactCli() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        ConfigurationManager.TestImpactConfig config = ConfigurationManager.getTestImpactConfig();

        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        Path mainSourceDir = projectRoot.resolve(config.mainSourceDir());
        Path testSourceDir = projectRoot.resolve(config.testSourceDir());
        Path outputFile = projectRoot.resolve(config.outputSuiteFile());
        Pattern testClassNamePattern = Pattern.compile(config.testClassNamePattern());

        System.out.println("[TestImpactCli] Base ref: " + config.baseRef());
        List<Path> changedFiles = gitDiffChangedFiles(config.baseRef(), projectRoot);
        System.out.println("[TestImpactCli] Changed files (" + changedFiles.size() + "):");
        for (Path file : changedFiles) {
            System.out.println("[TestImpactCli]   " + file);
        }

        ImpactResult result = analyze(changedFiles, mainSourceDir, testSourceDir, outputFile, testClassNamePattern);

        System.out.println("[TestImpactCli] Affected test classes (" + result.getAffectedTestClasses().size() + "):");
        for (String className : result.getAffectedTestClasses()) {
            System.out.println("[TestImpactCli]   " + className);
        }

        if (!result.getUnresolvedFiles().isEmpty()) {
            System.out.println("[TestImpactCli] WARNING: " + result.getUnresolvedFiles().size()
                + " changed file(s) could not be mapped to a Java class (non-Java file, or outside indexed source roots):");
            for (Path file : result.getUnresolvedFiles()) {
                System.out.println("[TestImpactCli]   " + file);
            }
            System.out.println("[TestImpactCli] Full suite run is recommended in addition to (or instead of) the impact suite below.");
        }

        if (result.getAffectedTestClasses().isEmpty()) {
            System.out.println("[TestImpactCli] No affected test classes found -- impact suite XML was not written.");
            return;
        }

        ImpactSuiteWriter.write(result.getAffectedTestClasses(), outputFile);
        System.out.println("[TestImpactCli] Impact suite written to: " + outputFile);
        System.out.println("[TestImpactCli] Run it with: mvn test -Dsurefire.suiteXmlFiles=" + config.outputSuiteFile());
    }

    /** Pure core logic (no process/console I/O) -- kept separate from {@link #main(String[])} for unit testing. */
    public static ImpactResult analyze(List<Path> changedFiles, Path mainSourceDir, Path testSourceDir,
                                        Path outputFile, Pattern testClassNamePattern) {
        JavaSourceIndexer index = new JavaSourceIndexer();
        index.indexSourceRoot(mainSourceDir);
        index.indexSourceRoot(testSourceDir);
        index.computeReferences();

        TestImpactAnalyzer analyzer = new TestImpactAnalyzer(index, testClassNamePattern, List.of(testSourceDir));
        return analyzer.findAffectedTests(changedFiles);
    }

    private static List<Path> gitDiffChangedFiles(String baseRef, Path projectRoot) throws IOException, InterruptedException {
        List<Path> changedFiles = new ArrayList<>();
        ProcessBuilder builder = new ProcessBuilder("git", "diff", "--name-only", baseRef);
        builder.directory(projectRoot.toFile());
        builder.redirectErrorStream(false);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    changedFiles.add(projectRoot.resolve(trimmed));
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("[TestImpactCli] WARNING: 'git diff --name-only " + baseRef
                + "' exited with code " + exitCode + " -- changed-file list may be incomplete.");
        }
        return changedFiles;
    }
}
