package com.test.automation.sdk.utility;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

/**
 * Extracts SDK instruction files from the JAR into the consumer project's
 * .github/instructions/ directory.
 *
 * Usage (run from consumer project root):
 *   mvn exec:java -Dexec.mainClass="com.test.automation.sdk.utility.InstructionExtractor"
 *
 * Extracted files are SDK-managed by default -- do NOT edit them unless you
 * intend to permanently fork that file for your project (see below).
 *
 * <h2>Safe re-extraction (customization protection)</h2>
 * Every extracted file gets a hidden {@code <file>.sdkhash} sidecar recording
 * the SHA-256 of the content written at extraction time. On a later re-run
 * (e.g. after an SDK upgrade):
 * <ul>
 *   <li>If the local file is unchanged since the last extraction (hash still
 *       matches the sidecar), it is safely refreshed with the newer SDK content.</li>
 *   <li>If the local file was hand-edited or intentionally customized (hash no
 *       longer matches), it is <b>never silently overwritten</b>. The newer SDK
 *       version is instead written to {@code <file>.sdk-new} alongside it for
 *       manual review/merge.</li>
 *   <li>Pass {@code -Dsdk.forceExtract=true} to discard local customizations
 *       and reset a file to the pristine SDK version.</li>
 * </ul>
 * This prevents the exact failure mode seen when a consumer project
 * intentionally customizes and git-tracks a file that the SDK also manages
 * (e.g. {@code failure-investigation.instructions.md}) -- re-running the
 * extractor after an SDK upgrade no longer clobbers that customization.
 */
public class InstructionExtractor {

    /** Sidecar file suffix recording the SHA-256 of content written at last extraction. */
    private static final String HASH_SIDECAR_SUFFIX = ".sdkhash";

    /** Suffix used when a newer SDK version can't be safely applied over a customized file. */
    private static final String CUSTOMIZED_SUFFIX = ".sdk-new";

    /** System property that forces overwrite of customized files, discarding local changes. */
    private static final String FORCE_EXTRACT_PROPERTY = "sdk.forceExtract";

    private static final String RESOURCE_PREFIX = "sdk-instructions/";
    private static final String PROMPTS_PREFIX = "sdk-prompts/";
    private static final String TEMPLATES_PREFIX = "sdk-templates/";
    private static final String OUTPUT_DIR_INSTRUCTIONS = ".github/instructions";
    private static final String OUTPUT_DIR_PROMPTS = ".github/prompts";
    private static final String OUTPUT_DIR_GITHUB = ".github";
    private static final String OUTPUT_DIR_GAP_TEMPLATES = "docs/test-case-gaps";
    private static final String OUTPUT_DIR_CONFIGURATION = "configuration";

    private static final List<String> INSTRUCTION_FILES = Arrays.asList(
        "test-creation.instructions.md",
        "page-object-creation.instructions.md",
        "locator-strategy.instructions.md",
        "formal-testcase-to-script.instructions.md",
        "test-fix.instructions.md",
        "test-case-gap.instructions.md",
        "sdk-migration.instructions.md",
        "failure-investigation.instructions.md",
        "test-data-dependency.instructions.md",
        "mobile-locator-strategy.instructions.md"
        // sdk-development.instructions.md is SDK-internal only (applyTo: src/main/**) -- not extracted
        // sdk-test-suite.instructions.md governs this SDK's own src/test/java -- not extracted
    );

    private static final List<String> ROOT_GITHUB_FILES = Arrays.asList(
        "copilot-instructions.md"
    );

    private static final List<String> PROMPT_FILES = Arrays.asList(
        "start.prompt.md",
        "create-test.prompt.md",
        "modify-test.prompt.md",
        "fix-failed-test.prompt.md",
        "ado-sync-test.prompt.md",
        "fix-broken-locator.prompt.md",
        "report-test-gap.prompt.md"
    );

    private static final List<String> TEMPLATE_FILES = Arrays.asList(
        "test-case-gap-report.md.template"
    );

    public static void main(String[] args) throws IOException {
        System.out.println("[InstructionExtractor] Extracting SDK instructions...");

        File instructionsDir = new File(OUTPUT_DIR_INSTRUCTIONS);
        if (!instructionsDir.exists()) {
            instructionsDir.mkdirs();
            System.out.println("[InstructionExtractor] Created: " + instructionsDir.getPath());
        }

        File promptsDir = new File(OUTPUT_DIR_PROMPTS);
        if (!promptsDir.exists()) {
            promptsDir.mkdirs();
            System.out.println("[InstructionExtractor] Created: " + promptsDir.getPath());
        }

        for (String fileName : INSTRUCTION_FILES) {
            extractResource(RESOURCE_PREFIX + fileName, OUTPUT_DIR_INSTRUCTIONS + "/" + fileName);
        }

        for (String fileName : ROOT_GITHUB_FILES) {
            extractResource(RESOURCE_PREFIX + fileName, OUTPUT_DIR_GITHUB + "/" + fileName);
        }

        for (String fileName : PROMPT_FILES) {
            extractResource(PROMPTS_PREFIX + fileName, OUTPUT_DIR_PROMPTS + "/" + fileName);
        }

        File gapTemplatesDir = new File(OUTPUT_DIR_GAP_TEMPLATES);
        if (!gapTemplatesDir.exists()) {
            gapTemplatesDir.mkdirs();
            System.out.println("[InstructionExtractor] Created: " + gapTemplatesDir.getPath());
        }

        for (String fileName : TEMPLATE_FILES) {
            extractResource(TEMPLATES_PREFIX + fileName, OUTPUT_DIR_GAP_TEMPLATES + "/" + fileName);
        }

        extractResource("sdk-defaults/mailinator-email-templates.yaml.template",
            OUTPUT_DIR_CONFIGURATION + "/mailinator-email-templates.yaml.template");

        extractResource("sdk-defaults/sdk-config.yaml.template",
            OUTPUT_DIR_CONFIGURATION + "/sdk-config.yaml.template");

        extractResource("sdk-defaults/config.properties.template",
            OUTPUT_DIR_CONFIGURATION + "/config.properties.template");

        extractResource("sdk-defaults/log4j2.xml.template",
            OUTPUT_DIR_CONFIGURATION + "/log4j2.xml.template");

        extractResource("sdk-defaults/log4j2.properties.template",
            OUTPUT_DIR_CONFIGURATION + "/log4j2.properties.template");

        extractResource("sdk-defaults/azure-pipelines.yml.template",
            "azure-pipelines.yml.template");

        extractResource("CHANGELOG.md", "docs/sdk/CHANGELOG.md");
        extractResource("SDK-USER-GUIDE.md", "docs/sdk/SDK-USER-GUIDE.md");
        extractResource("TESTBASE-API.md", "docs/sdk/TESTBASE-API.md");
        extractResource("MOBILE-USER-GUIDE.md", "docs/sdk/MOBILE-USER-GUIDE.md");
        extractResource("MOBILE-TESTBASE-API.md", "docs/sdk/MOBILE-TESTBASE-API.md");

        System.out.println("[InstructionExtractor] Done. Files written to .github/, docs/sdk/, configuration/, and project root.");
        System.out.println("[InstructionExtractor] IMPORTANT: Add .github/instructions/ and .github/prompts/ to .gitignore");
        System.out.println("[InstructionExtractor]            Do NOT gitignore docs/test-case-gaps/, docs/sdk/, or configuration/ -- these must be committed");
        System.out.println("[InstructionExtractor]            Re-run after every SDK version upgrade to get the latest instructions, prompts, and templates.");
        System.out.println("[InstructionExtractor]            Locally customized files are never overwritten -- see any *.sdk-new files above for manual merges.");
        System.out.println("[InstructionExtractor]            Use -Dsdk.forceExtract=true to discard local customizations and reset specific files to SDK defaults.");
    }

    /**
     * Extracts a single JAR resource to {@code outputPath}, protecting any local
     * customization from being silently overwritten. See the class-level Javadoc
     * for the full safe re-extraction algorithm.
     */
    private static void extractResource(String resourcePath, String outputPath) throws IOException {
        InputStream in = InstructionExtractor.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            System.err.println("[InstructionExtractor] WARNING: resource not found: " + resourcePath);
            return;
        }
        byte[] newContent;
        try {
            newContent = readAllBytes(in);
        } finally {
            in.close();
        }

        File outFile = new File(outputPath);
        String newHash = sha256Hex(newContent);
        File hashFile = new File(outputPath + HASH_SIDECAR_SUFFIX);

        if (!outFile.exists()) {
            writeFile(outFile, newContent);
            writeFile(hashFile, newHash.getBytes(StandardCharsets.UTF_8));
            System.out.println("[InstructionExtractor] Written: " + outputPath);
            return;
        }

        boolean forceExtract = Boolean.getBoolean(FORCE_EXTRACT_PROPERTY);
        byte[] existingContent = Files.readAllBytes(outFile.toPath());
        String existingHash = sha256Hex(existingContent);

        if (forceExtract) {
            writeFile(outFile, newContent);
            writeFile(hashFile, newHash.getBytes(StandardCharsets.UTF_8));
            deleteIfExists(new File(outputPath + CUSTOMIZED_SUFFIX));
            System.out.println("[InstructionExtractor] Written (forced, local changes discarded): " + outputPath);
            return;
        }

        if (existingHash.equals(newHash)) {
            // Already up to date -- just (re)sync the sidecar in case it was ever missing.
            writeFile(hashFile, newHash.getBytes(StandardCharsets.UTF_8));
            System.out.println("[InstructionExtractor] Unchanged: " + outputPath);
            return;
        }

        String lastKnownHash = hashFile.exists()
            ? new String(Files.readAllBytes(hashFile.toPath()), StandardCharsets.UTF_8).trim()
            : null;

        if (lastKnownHash != null && lastKnownHash.equals(existingHash)) {
            // Untouched locally since the last extraction -- safe to sync to the newer SDK content.
            writeFile(outFile, newContent);
            writeFile(hashFile, newHash.getBytes(StandardCharsets.UTF_8));
            System.out.println("[InstructionExtractor] Updated: " + outputPath);
            return;
        }

        // Locally customized (hand-edited, or intentionally forked/tracked with team-specific
        // content) -- never silently overwrite. Write the newer SDK version alongside for
        // manual review/merge instead.
        File sdkNewFile = new File(outputPath + CUSTOMIZED_SUFFIX);
        writeFile(sdkNewFile, newContent);
        System.out.println("[InstructionExtractor] WARNING: " + outputPath
            + " has local customizations -- NOT overwritten.");
        System.out.println("[InstructionExtractor]          Newer SDK version written to: "
            + sdkNewFile.getPath() + " -- review and merge manually.");
        System.out.println("[InstructionExtractor]          (Use -Dsdk.forceExtract=true to discard local changes and reset this file to the SDK default.)");
    }

    private static void writeFile(File file, byte[] content) throws IOException {
        File parentDir = file.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }

    private static void deleteIfExists(File file) {
        if (file.exists()) {
            file.delete();
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** SHA-256 is available on every JVM (JDK-mandated algorithm) -- this never throws in practice. */
    private static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available on this JVM", e);
        }
    }
}