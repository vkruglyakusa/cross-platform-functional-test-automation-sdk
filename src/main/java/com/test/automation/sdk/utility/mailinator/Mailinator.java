package com.test.automation.sdk.utility.mailinator;

import com.test.automation.sdk.utility.YamlConfigReader;
import com.test.automation.sdk.utility.mailinator.Email.EmailPart;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Mailinator API v2 client.
 *
 * Authentication: Authorization header (Bearer token) -- NOT query param.
 * All endpoints: https://api.mailinator.com/api/v2/domains/{domain}/...
 *
 * Domain config (sdk-config.yaml):
 *   api.mailinator.domain = "mailinator.com"        -- public Mailinator domain
 *   api.mailinator.domain = "yourcompany.com"       -- private/team domain
 *   api.mailinator.privateDomain = true/false       -- informational only in v2;
 *                                                     actual routing is by domain name
 *
 * @author Valeriy Kruglyak
 */
public class Mailinator {

	private static final String MAILINATOR_API_ENDPOINT = "https://api.mailinator.com/api/v2";
	private static final String MAILINATOR_INBOX_URL =
			MAILINATOR_API_ENDPOINT + "/domains/%s/inboxes/%s?limit=20&sort=descending";
	private static final String MAILINATOR_EMAIL_URL =
			MAILINATOR_API_ENDPOINT + "/domains/%s/inboxes/%s/messages/%s";
	private static final String MAILINATOR_DELETE_URL =
			MAILINATOR_API_ENDPOINT + "/domains/%s/inboxes/%s/messages/%s";
	private static final String MAILINATOR_DELETE_ALL_URL =
			MAILINATOR_API_ENDPOINT + "/domains/%s/inboxes/%s";

	private Mailinator() {
	}

	// --- Inbox ---------------------------------------------------------------

	public static List<InboxMessage> getInboxMessages(String apikey, String emailAddress) throws IOException {
		String domain = YamlConfigReader.get("api.mailinator.domain", "mailinator.com");
		return getInboxMessages(apikey, domain, extractInboxName(emailAddress));
	}

	public static List<InboxMessage> getInboxMessages(String apikey, String domain, String inbox) throws IOException {
		ArrayList<InboxMessage> messages = new ArrayList<InboxMessage>();
		Reader reader = get(String.format(MAILINATOR_INBOX_URL, domain, inbox), apikey);

		JSONObject obj = (JSONObject) JSONValue.parse(reader);
		JSONArray jsonMessages = getJsonArray(obj, "msgs", "messages");

		if (jsonMessages != null) {
			for (Object jsonMsg : jsonMessages) {
				InboxMessage message = createInboxMessageFrom((JSONObject) jsonMsg);
				messages.add(message);
			}
			Collections.reverse(messages);
		}

		return messages;
	}

	// --- Email ---------------------------------------------------------------

	public static Email getEmail(String apikey, String emailId) throws IOException {
		String domain = YamlConfigReader.get("api.mailinator.domain", "mailinator.com");
		return getEmail(apikey, domain, extractInboxFromMessageId(emailId), emailId);
	}

	public static Email getEmail(String apikey, String domain, String inbox, String emailId) throws IOException {
		Reader reader = get(String.format(MAILINATOR_EMAIL_URL, domain, inbox, emailId), apikey);
		JSONObject obj = (JSONObject) JSONValue.parse(reader);
		return createEmailFrom(obj);
	}

	public static ArrayList<String> getEmailBody(String apikey, String domain, String inbox, String emailId)
			throws IOException {
		Reader reader = get(String.format(MAILINATOR_EMAIL_URL, domain, inbox, emailId), apikey);
		JSONObject obj = (JSONObject) JSONValue.parse(reader);
		return getEmailBody(obj);
	}

	// --- Delete --------------------------------------------------------------

	public static boolean deleteEmail(String apikey, String emailId) throws IOException {
		String domain = YamlConfigReader.get("api.mailinator.domain", "mailinator.com");
		return deleteEmail(apikey, domain, extractInboxFromMessageId(emailId), emailId);
	}

	public static boolean deleteEmail(String apikey, String domain, String inbox, String emailId) throws IOException {
		String url = String.format(MAILINATOR_DELETE_URL, domain, inbox, emailId);
		Reader reader = delete(url, apikey);
		JSONObject obj = (JSONObject) JSONValue.parse(reader);
		return obj != null && "ok".equalsIgnoreCase(String.valueOf(obj.get("status")));
	}

	public static boolean deleteAllEmails(String apikey, String domain, String inbox) throws IOException {
		if (inbox == null || inbox.trim().isEmpty()) return false;
		String url = String.format(MAILINATOR_DELETE_ALL_URL, domain, inbox);
		Reader reader = delete(url, apikey);
		JSONObject obj = (JSONObject) JSONValue.parse(reader);
		return obj != null && "ok".equalsIgnoreCase(String.valueOf(obj.get("status")));
	}

	// --- HTTP helpers ---------------------------------------------------------

	/**
	 * HTTP GET with Authorization: Bearer {apikey} header.
	 */
	private static Reader get(String url, String apikey) throws IOException {
		HttpURLConnection connection = openConnection(url);
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Authorization", apikey);
		connection.setRequestProperty("Accept", "application/json");
		return readResponse(connection, url);
	}

	/**
	 * HTTP DELETE with Authorization: Bearer {apikey} header.
	 */
	private static Reader delete(String url, String apikey) throws IOException {
		HttpURLConnection connection = openConnection(url);
		connection.setRequestMethod("DELETE");
		connection.setRequestProperty("Authorization", apikey);
		connection.setRequestProperty("Accept", "application/json");
		return readResponse(connection, url);
	}

	private static HttpURLConnection openConnection(String urlStr) throws IOException {
		boolean proxyEnabled = YamlConfigReader.getBoolean("proxy.enabled", false);
		URL url = new URL(urlStr);
		if (proxyEnabled) {
			String proxyHost = YamlConfigReader.get("proxy.host", "");
			int proxyPort    = YamlConfigReader.getInt("proxy.port", 8080);
			Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
			return (HttpURLConnection) url.openConnection(proxy);
		}
		return (HttpURLConnection) url.openConnection();
	}

	private static Reader readResponse(HttpURLConnection connection, String url) throws IOException {
		int responseCode = connection.getResponseCode();
		if (responseCode >= 200 && responseCode < 300) {
			InputStream in = connection.getInputStream();
			return new InputStreamReader(in, "UTF-8");
		}
		// Return empty JSON object on non-2xx so callers get a safe parse result
		InputStream err = connection.getErrorStream();
		if (err != null) {
			// consume error stream
			byte[] buf = new byte[4096]; while (err.read(buf) != -1) {}
			err.close();
		}
		return new StringReader("{}");
	}

	// --- JSON builders --------------------------------------------------------

	private static InboxMessage createInboxMessageFrom(JSONObject jsonInboxMsg) {
		InboxMessage message = new InboxMessage();
		message.setTo(stringValue(jsonInboxMsg.get("to")));
		message.setId(stringValue(jsonInboxMsg.get("id")));
		message.setSeconds_ago(longValue(jsonInboxMsg.get("seconds_ago")));
		message.setTime(longValue(jsonInboxMsg.get("time")));
		message.setSubject(stringValue(jsonInboxMsg.get("subject")));
		message.setFrom(stringValue(jsonInboxMsg.get("from")));
		message.setFromfull(stringValue(firstNonNull(jsonInboxMsg.get("fromfull"), jsonInboxMsg.get("origfrom"))));
		if (jsonInboxMsg.get("ip") != null) {
			message.setIp(String.valueOf(jsonInboxMsg.get("ip")));
		}
		if (jsonInboxMsg.get("been_read") != null) {
			message.setBeen_read(Boolean.parseBoolean(String.valueOf(jsonInboxMsg.get("been_read"))));
		}
		return message;
	}

	private static Email createEmailFrom(JSONObject jsonEmail) {
		Email emailMsg = new Email();
		JSONObject root = getEmailRoot(jsonEmail);

		emailMsg.setId(stringValue(firstNonNull(root.get("requestId"), root.get("id"))));
		emailMsg.setSecondsAgo(longValue(root.get("seconds_ago")));
		emailMsg.setTo(stringValue(root.get("to")));
		emailMsg.setTime(longValue(root.get("time")));
		emailMsg.setSubject(stringValue(root.get("subject")));
		emailMsg.setFromFull(stringValue(firstNonNull(root.get("fromfull"), root.get("origfull"))));

		JSONObject jsonHeaders = (JSONObject) root.get("headers");
		emailMsg.setHeaders(jsonHeaders != null
				? builderHeaders(jsonHeaders.entrySet())
				: new HashMap<String, String>());

		JSONArray jsonParts = (JSONArray) root.get("parts");
		if (jsonParts != null) {
			for (Object jsonPart1 : jsonParts) {
				EmailPart emailPart = emailMsg.new EmailPart();
				JSONObject jsonPart = (JSONObject) jsonPart1;
				JSONObject jsonPartHeaders = (JSONObject) jsonPart.get("headers");
				emailPart.setHeaders(jsonPartHeaders != null
						? builderHeaders(jsonPartHeaders.entrySet())
						: new HashMap<String, String>());
				emailPart.setBody(stringValue(jsonPart.get("body")));
				emailMsg.getEmailParts().add(emailPart);
			}
		}
		return emailMsg;
	}

	private static ArrayList<String> getEmailBody(JSONObject jsonEmail) {
		ArrayList<String> emailBody = new ArrayList<String>();
		JSONObject root = getEmailRoot(jsonEmail);
		JSONArray jsonParts = (JSONArray) root.get("parts");
		if (jsonParts == null) return emailBody;
		for (Object jsonPart1 : jsonParts) {
			JSONObject jsonPart = (JSONObject) jsonPart1;
			emailBody.add(stringValue(jsonPart.get("body")));
		}
		return emailBody;
	}

	private static JSONObject getEmailRoot(JSONObject jsonEmail) {
		if (jsonEmail == null) return new JSONObject();
		Object dataSection = jsonEmail.get("data");
		if (dataSection instanceof JSONObject) return (JSONObject) dataSection;
		return jsonEmail;
	}

	private static JSONArray getJsonArray(JSONObject obj, String primaryKey, String fallbackKey) {
		if (obj == null) return null;
		Object primary = obj.get(primaryKey);
		if (primary instanceof JSONArray) return (JSONArray) primary;
		Object fallback = obj.get(fallbackKey);
		if (fallback instanceof JSONArray) return (JSONArray) fallback;
		return null;
	}

	private static HashMap<String, String> builderHeaders(Set<Entry> entries) {
		HashMap<String, String> headers = new HashMap<String, String>();
		for (Entry entry : entries) {
			headers.put(entry.getKey().toString().trim(), entry.getValue().toString().trim());
		}
		return headers;
	}

	// --- Utility -------------------------------------------------------------

	private static String extractInboxName(String emailAddress) {
		if (emailAddress == null) return "";
		int atIndex = emailAddress.indexOf('@');
		return atIndex > -1 ? emailAddress.substring(0, atIndex) : emailAddress;
	}

	private static String extractInboxFromMessageId(String emailId) {
		if (emailId == null) return "";
		int idx = emailId.indexOf('-');
		return idx > 0 ? emailId.substring(0, idx) : emailId;
	}

	private static Object firstNonNull(Object first, Object second) {
		return first != null ? first : second;
	}

	private static String stringValue(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static long longValue(Object value) {
		if (value == null) return 0L;
		try { return Long.parseLong(String.valueOf(value)); }
		catch (NumberFormatException e) { return 0L; }
	}
}

