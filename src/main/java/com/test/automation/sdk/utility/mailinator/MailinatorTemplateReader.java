package com.test.automation.sdk.utility.mailinator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MailinatorTemplateReader {

    private static final Logger log = LogManager.getLogger(MailinatorTemplateReader.class.getName());
    private static final String TEMPLATE_PREFIX = "templates.";
    private static MailinatorTemplateReader instance;

    private final Map<String, String> flatMap = new HashMap<String, String>();
    private final Map<String, EmailTemplate> templates = new HashMap<String, EmailTemplate>();

    private MailinatorTemplateReader() {
        String dir = System.getProperty("sdk.config.dir");
        if (dir == null || dir.isEmpty()) dir = System.getenv("SDK_CONFIG_DIR");
        if (dir == null || dir.isEmpty()) dir = "configuration";
        dir = dir.replaceAll("[/\\\\]+$", "");
        File yamlFile = new File(dir + "/mailinator-email-templates.yaml");
        if (!yamlFile.exists()) {
            log.warn("[MailinatorTemplateReader] mailinator-email-templates.yaml not found at: {}", yamlFile.getPath());
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(yamlFile);
            try {
                parse(fis);
                buildTemplates();
                log.info("[MailinatorTemplateReader] Loaded mailinator-email-templates.yaml from: {}", yamlFile.getPath());
            } finally {
                fis.close();
            }
        } catch (IOException e) {
            log.error("[MailinatorTemplateReader] Failed to read mailinator-email-templates.yaml: {}", e.getMessage());
        }
    }

    public static EmailTemplate getTemplate(String name) {
        if (name == null) {
            return null;
        }
        return getInstance().templates.get(name);
    }

    public static Map<String, EmailTemplate> getAllTemplates() {
        return Collections.unmodifiableMap(getInstance().templates);
    }

    public static synchronized void reset() {
        instance = null;
    }

    private static synchronized MailinatorTemplateReader getInstance() {
        if (instance == null) {
            instance = new MailinatorTemplateReader();
        }
        return instance;
    }

    private void parse(InputStream is) throws IOException {
        byte[] bytes = readAllBytes(is);
        String content = new String(bytes, "UTF-8");
        String[] lines = content.split("\\r?\\n");
        String currentSection = "";
        String currentSubSection = "";

        for (String rawLine : lines) {
            String line = rawLine;
            int commentIdx = line.indexOf('#');
            if (commentIdx >= 0) {
                line = line.substring(0, commentIdx);
            }
            if (line.trim().isEmpty()) {
                continue;
            }

            int indent = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ') {
                    indent++;
                } else {
                    break;
                }
            }

            String trimmed = line.trim();
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx < 0) {
                continue;
            }

            String key = trimmed.substring(0, colonIdx).trim();
            String value = trimmed.substring(colonIdx + 1).trim();
            if (value.length() >= 2
                    && ((value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
                    || (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\''))) {
                value = value.substring(1, value.length() - 1);
            }

            if (indent == 0) {
                currentSection = key;
                currentSubSection = "";
            } else if (indent == 2) {
                currentSubSection = key;
                if (!value.isEmpty()) {
                    flatMap.put(currentSection + "." + key, value);
                }
            } else if (indent == 4) {
                // Always store even empty values -- needed so templates with all-empty
                // fields are still discoverable by buildTemplates()
                flatMap.put(currentSection + "." + currentSubSection + "." + key, value);
            }
        }
    }

    private void buildTemplates() {
        Set<String> templateNames = new HashSet<String>();
        for (String key : flatMap.keySet()) {
            if (key.startsWith(TEMPLATE_PREFIX)) {
                String remainder = key.substring(TEMPLATE_PREFIX.length());
                int nextDot = remainder.indexOf('.');
                if (nextDot > 0) {
                    templateNames.add(remainder.substring(0, nextDot));
                }
            }
        }

        for (String templateName : templateNames) {
            String subjectContains = getTemplateValue(templateName, "subjectContains");
            List<String> urlPathPatterns = splitCsv(getTemplateValue(templateName, "urlPathPatterns"));
            List<String> excludePatterns = splitCsv(getTemplateValue(templateName, "excludePatterns"));
            List<String> masks = splitCsv(getTemplateValue(templateName, "masks"));
            String textPattern = getTemplateValue(templateName, "textPattern");
            templates.put(templateName, new EmailTemplate(templateName, subjectContains, urlPathPatterns,
                    excludePatterns, masks, textPattern));
        }
    }

    private String getTemplateValue(String templateName, String propertyName) {
        String value = flatMap.get(TEMPLATE_PREFIX + templateName + "." + propertyName);
        return value == null ? "" : value;
    }

    private List<String> splitCsv(String value) {
        List<String> items = new ArrayList<String>();
        if (value == null || value.trim().isEmpty()) {
            return items;
        }

        String[] parts = value.split(",");
        for (int i = 0; i < parts.length; i++) {
            String item = parts[i].trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = is.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
