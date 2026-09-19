package com.test.automation.sdk.impact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lightweight, compiler-free static source index used by {@link TestImpactAnalyzer}.
 *
 * <p>Rather than requiring a full Java compiler/AST or bytecode coverage
 * instrumentation (JaCoCo et al.), this indexer uses a deliberately simple
 * heuristic: for every {@code .java} file it records the file's fully
 * qualified class name (from its {@code package} declaration + file name),
 * then tokenizes the entire file body and records every token that matches
 * the simple name of some other class known to the index. That token set
 * becomes the "references" edge list for that class.
 *
 * <p>This intentionally over-approximates real dependencies (a comment or
 * string literal mentioning a class name would count as a "reference", and
 * two unrelated classes that happen to share a simple name are both treated
 * as referenced) -- for impact analysis, over-approximating (running a few
 * extra tests) is the safe failure mode; under-approximating (silently
 * skipping an affected test) is not. No external tooling or build-time
 * instrumentation is required, so this works out of the box for any Maven
 * project.
 */
public class JavaSourceIndexer {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /** Fully qualified class name -> absolute source file path. */
    private final Map<String, Path> classToFile = new HashMap<>();

    /** Absolute source file path -> fully qualified class name (reverse of {@link #classToFile}). */
    private final Map<Path, String> fileToClass = new HashMap<>();

    /** Simple (unqualified) class name -> every FQCN in the index sharing that simple name. */
    private final Map<String, Set<String>> simpleNameToFqcns = new HashMap<>();

    /** Fully qualified class name -> set of other FQCNs referenced by its source body. */
    private final Map<String, Set<String>> forwardReferences = new HashMap<>();

    /**
     * Scans every {@code .java} file under {@code sourceRoots} and adds it to this index.
     * Safe to call multiple times with different roots (e.g. once for {@code src/main/java}
     * and once for {@code src/test/java}) to build a single combined index.
     */
    public void indexSourceRoot(Path sourceRoot) {
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            return;
        }
        List<Path> javaFiles = listJavaFiles(sourceRoot);
        for (Path file : javaFiles) {
            registerClass(file);
        }
    }

    /** Must be called only after every source root of interest has been indexed via {@link #indexSourceRoot(Path)}. */
    public void computeReferences() {
        for (Map.Entry<String, Path> entry : classToFile.entrySet()) {
            String fqcn = entry.getKey();
            Path file = entry.getValue();
            forwardReferences.put(fqcn, referencedClasses(file, fqcn));
        }
    }

    /** Returns the fully qualified class name backed by {@code file}, or {@code null} if not indexed. */
    public String classNameForFile(Path file) {
        return fileToClass.get(file.toAbsolutePath().normalize());
    }

    public boolean isIndexed(String fqcn) {
        return classToFile.containsKey(fqcn);
    }

    public Set<String> allIndexedClasses() {
        return classToFile.keySet();
    }

    /** Absolute source file path backing {@code fqcn}, or {@code null} if not indexed. */
    public Path fileFor(String fqcn) {
        return classToFile.get(fqcn);
    }

    /** FQCNs directly referenced (heuristically) by {@code fqcn}'s source body. Empty if unknown or no references. */
    public Set<String> referencesOf(String fqcn) {
        return forwardReferences.getOrDefault(fqcn, new HashSet<>());
    }

    private void registerClass(Path file) {
        String content;
        try {
            content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read source file: " + file, e);
        }
        String packageName = extractPackage(content);
        String simpleName = fileNameWithoutExtension(file);
        String fqcn = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;

        Path absoluteFile = file.toAbsolutePath().normalize();
        classToFile.put(fqcn, absoluteFile);
        fileToClass.put(absoluteFile, fqcn);
        simpleNameToFqcns.computeIfAbsent(simpleName, k -> new HashSet<>()).add(fqcn);
    }

    private Set<String> referencedClasses(Path file, String selfFqcn) {
        String content;
        try {
            content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read source file: " + file, e);
        }

        Set<String> referenced = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        while (matcher.find()) {
            String token = matcher.group();
            Set<String> candidates = simpleNameToFqcns.get(token);
            if (candidates == null) {
                continue;
            }
            for (String candidateFqcn : candidates) {
                if (!candidateFqcn.equals(selfFqcn)) {
                    referenced.add(candidateFqcn);
                }
            }
        }
        return referenced;
    }

    private static String extractPackage(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String fileNameWithoutExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static List<Path> listJavaFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> result = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(result::add);
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk source root: " + root, e);
        }
    }
}
