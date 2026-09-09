/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.test.automation.sdk.utility.mailinator;

import com.test.automation.sdk.config.SdkConfig;
import com.test.automation.sdk.config.YamlConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Valeriy Kruglyak
 */
public class MailinatorEmailReader {
	public static final Logger log = LogManager.getLogger(MailinatorEmailReader.class.getName());
	private static String msgId = "";
	private static String msgSbjc = "";
	private static String lastInbox = "";

	public MailinatorEmailReader() {
		String log4jConfPath = SdkConfig.LOG4J_PROPERTIES;
		File file = new File(log4jConfPath);
		LoggerContext context = (LoggerContext) LogManager.getContext(false);
		context.setConfigLocation(file.toURI());
	}

	private static List<InboxMessage> getInboxMessages(String emailAddress) throws Exception {
		msgId = "";
		msgSbjc = "";
		lastInbox = getInbox(emailAddress);
		List<InboxMessage> inboxMessages = Mailinator.getInboxMessages(getApiKey(), getDomain(), lastInbox);
		for (InboxMessage imsg : inboxMessages) {
			msgSbjc = imsg.getSubject();
			log.info("Subject: " + msgSbjc);
			msgId = imsg.getId();
			log.info("Mail ID: " + msgId);
		}
		return inboxMessages;
	}

	private static String extractUrlFromBody(String emailId, EmailTemplate template, String baseUrl) throws Exception {
		ArrayList<String> bodyResults = Mailinator.getEmailBody(getApiKey(), getDomain(),
				extractInboxFromMessageId(emailId), emailId);
		String lowerBaseUrl = baseUrl.toLowerCase();
		for (String body : bodyResults) {
			List<String> extractedUrls = extractUrls(body);
			for (String url : extractedUrls) {
				if (url != null && url.toLowerCase().startsWith(lowerBaseUrl)) {
					String matchedUrl = matchUrlToTemplate(url, template);
					if (matchedUrl != null) {
						String resolvedUrl = normalizeUrl(matchedUrl);
						if (!template.getMasks().isEmpty()) {
							resolvedUrl = EmailValueMask.apply(resolvedUrl, template.getMasks());
						}
						log.info(template.getName() + " URL is : " + resolvedUrl);
						return resolvedUrl;
					}
				}
			}
		}
		return null;
	}

	private static List<String> extractUrls(String text) {
		List<String> containedUrls = new ArrayList<String>();
		String urlRegex = "((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
		Pattern pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE);
		Matcher urlMatcher = pattern.matcher(text);
		while (urlMatcher.find()) {
			containedUrls.add(text.substring(urlMatcher.start(0), urlMatcher.end(0)));
		}
		return containedUrls;
	}

	private static String matchUrlToTemplate(String url, EmailTemplate template) {
		String lowerUrl = url.toLowerCase();
		for (String excludePattern : template.getExcludePatterns()) {
			if (lowerUrl.contains(excludePattern.toLowerCase())) {
				return null;
			}
		}

		for (String urlPathPattern : template.getUrlPathPatterns()) {
			if (lowerUrl.contains(urlPathPattern.toLowerCase())) {
				return url;
			}
		}
		return null;
	}

	private static EmailTemplate resolveTemplate(String name) {
		EmailTemplate configuredTemplate = MailinatorTemplateReader.getTemplate(name);
		if (configuredTemplate != null) {
			return configuredTemplate;
		}

		if ("confirmation".equalsIgnoreCase(name)) {
			return new EmailTemplate("confirmation", "", Arrays.asList("validateToken", "validateReset",
					"validateChangeEmail", "feedback"), Arrays.asList("deactivate", "amp;"),
					Collections.<String>emptyList());
		}
		if ("deactivation".equalsIgnoreCase(name)) {
			return new EmailTemplate("deactivation", "", Arrays.asList("deactivate"), Arrays.asList("amp;"),
					Collections.<String>emptyList());
		}
		throw new IllegalArgumentException("Unknown Mailinator email template: " + name);
	}

	private static InboxMessage findMatchingMessage(List<InboxMessage> inboxMessages, EmailTemplate template) {
		String subjectContains = template.getSubjectContains();
		for (int i = inboxMessages.size() - 1; i >= 0; i--) {
			InboxMessage message = inboxMessages.get(i);
			if (subjectContains == null || subjectContains.trim().isEmpty()) {
				return message;
			}
			String subject = message.getSubject();
			if (subject != null && subject.toLowerCase().contains(subjectContains.toLowerCase())) {
				return message;
			}
		}
		return null;
	}

	private static String normalizeUrl(String url) {
		if (url == null) {
			return null;
		}
		int tagIndex = url.indexOf('<');
		if (tagIndex >= 0) {
			return url.substring(0, tagIndex);
		}
		return url;
	}

	/**
	 * Polls the inbox until an email matching the template's subjectContains is found,
	 * or the configured timeout elapses.
	 *
	 * Waits {@code inboxInitialWaitSeconds} before the first poll to allow for email
	 * delivery delay, then retries every {@code inboxPollIntervalSeconds} until
	 * {@code inboxPollTimeoutSeconds} is reached.
	 *
	 * @param template     template to match against (uses subjectContains filter)
	 * @param emailAddress inbox address to poll
	 * @return matched InboxMessage or null if not found within timeout
	 */
	private static InboxMessage pollForMatchingMessage(EmailTemplate template, String emailAddress) throws Exception {
		int intervalMs = getPollIntervalMs();
		int timeoutMs  = getPollTimeoutMs();
		int initialMs  = getInitialWaitMs();
		int maxAttempts = (timeoutMs / intervalMs) + 1;

		log.info("[Mailinator] Initial wait " + (initialMs / 1000) + "s before polling [template="
				+ template.getName() + "] inbox=" + emailAddress);
		Thread.sleep(initialMs);

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			log.info("[Mailinator] Poll attempt " + attempt + "/" + maxAttempts
					+ " [template=" + template.getName() + "] inbox=" + emailAddress);
			List<InboxMessage> inboxMessages = getInboxMessages(emailAddress);
			InboxMessage matched = findMatchingMessage(inboxMessages, template);
			if (matched != null) {
				log.info("[Mailinator] Email found on attempt " + attempt + " subject='" + matched.getSubject() + "'");
				msgId   = matched.getId();
				msgSbjc = matched.getSubject();
				return matched;
			}
			if (attempt < maxAttempts) {
				log.info("[Mailinator] No matching email yet -- retrying in " + (intervalMs / 1000) + "s");
				Thread.sleep(intervalMs);
			}
		}
		log.warn("[Mailinator] No matching email found after " + maxAttempts + " attempts [template="
				+ template.getName() + "] inbox=" + emailAddress);
		return null;
	}

	/**
	 * Extracts an application URL from an email using a named template.
	 *
	 * @param templateName configured template name or supported built-in default
	 * @param baseURL application base URL that matched links must start with
	 * @param emailAddress inbox address to poll
	 * @return extracted URL, or baseURL when no matching email/link is found
	 * @throws Exception when Mailinator API access fails
	 */
	public static String getUrlFromEmail(String templateName, String baseURL, String emailAddress) throws Exception {
		EmailTemplate template = resolveTemplate(templateName);
		InboxMessage matchedMessage = pollForMatchingMessage(template, emailAddress);

		String url = baseURL;
		if (matchedMessage != null) {
			String extractedUrl = extractUrlFromBody(matchedMessage.getId(), template, baseURL);
			if (extractedUrl != null) {
				url = extractedUrl;
			} else {
				log.warn("[Mailinator] Could not extract " + template.getName() + " URL from email body");
			}
			deleteEmailById(matchedMessage.getId());
		} else {
			log.warn("[Mailinator] No email received for template=" + template.getName()
					+ " -- returning baseURL as fallback");
		}
		return url;
	}

	public static String getConfirmationUrl(String baseURL, String emailAddress) throws Exception {
		return getUrlFromEmail("confirmation", baseURL, emailAddress);
	}

	public static String getDeactivationUrl(String baseURL, String emailAddress) throws Exception {
		return getUrlFromEmail("deactivation", baseURL, emailAddress);
	}

	// -------------------------------------------------------------------------
	// Delete / Cleanup
	// -------------------------------------------------------------------------

	/**
	 * Deletes a single email message by its Mailinator message ID.
	 *
	 * <p>The inbox name is derived automatically from the message ID prefix
	 * (Mailinator IDs are formatted as {@code inbox-suffix}).
	 *
	 * @param emailId Mailinator message ID to delete
	 * @return {@code true} if the API confirmed deletion; {@code false} on failure
	 */
	public static boolean deleteEmailById(String emailId) {
		if (emailId == null || emailId.trim().isEmpty()) {
			log.warn("[Mailinator] deleteEmailById called with null/empty ID -- skipping");
			return false;
		}
		String inbox = extractInboxFromMessageId(emailId);
		log.info("[Mailinator] Deleting message id=" + emailId + " inbox=" + inbox);
		try {
			boolean result = Mailinator.deleteEmail(getApiKey(), getDomain(), inbox, emailId);
			log.info("[Mailinator] Delete result: " + result + " for id=" + emailId);
			return result;
		} catch (IOException e) {
			log.warn("[Mailinator] Failed to delete id=" + emailId + ": " + e.getMessage());
			return false;
		}
	}

	/**
	 * Extracts a text value from an email body using a named template's {@code textPattern}.
	 *
	 * <p>The template must define a {@code textPattern} -- a Java regex with exactly one
	 * capture group. The value of group(1) is returned after applying any configured masks.
	 *
	 * <p>Common use cases: OTP codes, confirmation numbers, dates, account IDs, names.
	 *
	 * <p>Example template definition:
	 * <pre>
	 * otpCode:
	 *   subjectContains: "Your Verification Code"
	 *   textPattern: "Your code is: (\\d{6})"
	 *   masks: "trim"
	 * </pre>
	 *
	 * @param templateName name of the template in mailinator-email-templates.yaml
	 * @param emailAddress inbox address to poll
	 * @return extracted text value after masks applied, or null if not found
	 * @throws Exception when Mailinator API access fails or template has no textPattern
	 */
	public static String getTextFromEmail(String templateName, String emailAddress) throws Exception {
		EmailTemplate template = resolveTemplate(templateName);
		if (template.getTextPattern() == null || template.getTextPattern().trim().isEmpty()) {
			throw new IllegalArgumentException(
					"Template '" + templateName + "' does not define a textPattern. "
					+ "Add textPattern to the template in mailinator-email-templates.yaml");
		}

		InboxMessage matchedMessage = pollForMatchingMessage(template, emailAddress);

		if (matchedMessage == null) {
			log.warn("No matching email found for template [" + template.getName() + "] at address=" + emailAddress);
			return null;
		}

		String extracted = extractTextFromBody(matchedMessage.getId(), template);
		if (extracted != null && !template.getMasks().isEmpty()) {
			extracted = EmailValueMask.apply(extracted, template.getMasks());
		}
		log.info("Extracted text [template=" + template.getName() + "]: " + extracted);
		deleteEmailById(matchedMessage.getId());
		return extracted;
	}

	private static String extractTextFromBody(String emailId, EmailTemplate template) throws Exception {
		ArrayList<String> bodyResults = Mailinator.getEmailBody(getApiKey(), getDomain(),
				extractInboxFromMessageId(emailId), emailId);
		Pattern pattern = Pattern.compile(template.getTextPattern(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		for (String body : bodyResults) {
			Matcher matcher = pattern.matcher(body);
			if (matcher.find()) {
				String value = matcher.group(1);
				log.info("Text match found [template=" + template.getName() + "]: " + value);
				return value;
			}
		}
		log.warn("textPattern [" + template.getTextPattern() + "] found no match in email body");
		return null;
	}

	private static String getApiKey() {
		return YamlConfigReader.get("api.mailinator.apiKey", "");
	}

	private static String getDomain() {
		return YamlConfigReader.get("api.mailinator.domain", "mailinator.com");
	}

	private static int getPollIntervalMs() {
		return YamlConfigReader.getInt("api.mailinator.inboxPollIntervalSeconds", 3) * 1000;
	}

	private static int getPollTimeoutMs() {
		return YamlConfigReader.getInt("api.mailinator.inboxPollTimeoutSeconds", 60) * 1000;
	}

	private static int getInitialWaitMs() {
		return YamlConfigReader.getInt("api.mailinator.inboxInitialWaitSeconds", 5) * 1000;
	}

	private static String getInbox(String emailAddress) {
		if (emailAddress == null) {
			return "";
		}
		int atIndex = emailAddress.indexOf('@');
		if (atIndex > -1) {
			return emailAddress.substring(0, atIndex);
		}
		return emailAddress;
	}

	private static String extractInboxFromMessageId(String emailId) {
		if (emailId == null) {
			return "";
		}
		int separatorIndex = emailId.indexOf('-');
		if (separatorIndex > 0) {
			return emailId.substring(0, separatorIndex);
		}
		return emailId;
	}
}
