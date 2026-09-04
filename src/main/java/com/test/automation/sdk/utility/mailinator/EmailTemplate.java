package com.test.automation.sdk.utility.mailinator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmailTemplate {

    private final String name;
    private final String subjectContains;
    private final List<String> urlPathPatterns;
    private final List<String> excludePatterns;
    private final List<String> masks;
    /**
     * Optional regex pattern with one capture group used to extract text
     * from the email body (e.g. OTP codes, dates, IDs, names).
     * The value of group(1) is returned by getTextFromEmail().
     * Example: "Your code is: (\\d{6})"
     */
    private final String textPattern;

    public EmailTemplate(String name, String subjectContains,
                         List<String> urlPathPatterns, List<String> excludePatterns,
                         List<String> masks) {
        this(name, subjectContains, urlPathPatterns, excludePatterns, masks, "");
    }

    public EmailTemplate(String name, String subjectContains,
                         List<String> urlPathPatterns, List<String> excludePatterns,
                         List<String> masks, String textPattern) {
        this.name = name;
        this.subjectContains = subjectContains == null ? "" : subjectContains;
        this.urlPathPatterns = Collections.unmodifiableList(copyList(urlPathPatterns));
        this.excludePatterns = Collections.unmodifiableList(copyList(excludePatterns));
        this.masks = Collections.unmodifiableList(copyList(masks));
        this.textPattern = textPattern == null ? "" : textPattern;
    }

    public String getName() {
        return name;
    }

    public String getSubjectContains() {
        return subjectContains;
    }

    public List<String> getUrlPathPatterns() {
        return urlPathPatterns;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public List<String> getMasks() {
        return masks;
    }

    public String getTextPattern() {
        return textPattern;
    }

    private List<String> copyList(List<String> source) {
        if (source == null) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(source);
    }
}
