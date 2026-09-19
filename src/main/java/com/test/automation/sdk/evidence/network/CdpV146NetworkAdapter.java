package com.test.automation.sdk.evidence.network;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v146.network.Network;
import org.openqa.selenium.devtools.v146.network.model.Headers;
import org.openqa.selenium.devtools.v146.network.model.Request;
import org.openqa.selenium.devtools.v146.network.model.RequestWillBeSent;
import org.openqa.selenium.devtools.v146.network.model.Response;
import org.openqa.selenium.devtools.v146.network.model.ResponseReceived;

import com.test.automation.sdk.reporting.SecretRedactor;

/**
 * SDK v1.5.1 -- the ONLY class in the SDK that imports the
 * {@code org.openqa.selenium.devtools.v146.*} CDP domain bindings.
 *
 * <p><b>Known limitation (documented, not hidden):</b> Selenium does not ship
 * a version-agnostic Network-domain facade, so this adapter is pinned to the
 * {@code v146} CDP command/event bindings bundled with the SDK's pinned
 * {@code selenium-java} version. The CDP Network domain's wire schema has
 * been stable enough across nearby CDP revisions that these bindings work in
 * practice against currently supported Chrome/Edge builds, but this is a
 * pragmatic compromise -- not true per-browser-version CDP negotiation. If a
 * future Chrome/Edge release ships a CDP revision materially incompatible
 * with v146's Network domain shape, {@link #supports(WebDriver)} /
 * {@link #attach(WebDriver, int, boolean)} are expected to fail fast and
 * safely (see below), and replacing this single class with a newer-version
 * adapter is the intended, isolated upgrade path.
 *
 * <p>Isolating all v146-specific imports and logic in this one class (rather
 * than spread through {@code NetworkTraceRecorder}) is exactly what makes
 * that future replacement straightforward.
 */
final class CdpV146NetworkAdapter implements CdpNetworkAdapter {

    private static final Logger log = LogManager.getLogger(CdpV146NetworkAdapter.class);

    @Override
    public String name() {
        return "cdp-v146";
    }

    @Override
    public boolean supports(WebDriver driver) {
        try {
            if (!(driver instanceof HasDevTools)) {
                return false;
            }
            // Runtime browser detection: CDP (and this adapter) is only meaningful for
            // Chromium-based browsers. Detecting this here -- rather than assuming any
            // HasDevTools driver is compatible -- lets a future non-Chromium HasDevTools
            // implementation (if one ever exists) be safely rejected instead of attempted.
            String browserName = detectBrowserName(driver);
            boolean chromium = browserName != null
                    && (browserName.contains("chrome") || browserName.contains("edge"));
            if (!chromium) {
                log.debug("[CdpV146NetworkAdapter] detected browser '{}' is not a known Chromium-based browser -- "
                        + "declining network capture (only Chrome/Edge are supported)", browserName);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.debug("[CdpV146NetworkAdapter] browser/CDP detection failed, declining network capture: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public CdpNetworkSession attach(WebDriver driver, int maxEntries, boolean redactSensitiveData) throws Exception {
        String browserDescription = describeBrowser(driver);
        DevTools devTools = ((HasDevTools) driver).getDevTools();
        try {
            devTools.createSession();
            Session session = new Session(devTools, maxEntries, redactSensitiveData);
            session.start();
            log.debug("[CdpV146NetworkAdapter] CDP network capture started for {} (maxEntries={})",
                    browserDescription, maxEntries);
            return session;
        } catch (Exception e) {
            // A CDP-version incompatibility (or any other session-negotiation error)
            // surfaces here. Fail safely: close whatever was opened and rethrow so the
            // orchestrator logs a clear WARN and continues without network evidence --
            // this must never fail the test itself.
            try {
                devTools.close();
            } catch (Exception ignored) {
                // best effort cleanup only
            }
            throw new IllegalStateException("CDP v146 network capture is not compatible with the detected browser ("
                    + browserDescription + "): " + e.getMessage(), e);
        }
    }

    private static String detectBrowserName(WebDriver driver) {
        Capabilities caps = capabilitiesOf(driver);
        if (caps == null) {
            return null;
        }
        String name = caps.getBrowserName();
        return name == null ? null : name.toLowerCase(java.util.Locale.ROOT);
    }

    private static String describeBrowser(WebDriver driver) {
        Capabilities caps = capabilitiesOf(driver);
        if (caps == null) {
            return "unknown browser";
        }
        String name = caps.getBrowserName();
        String version = caps.getBrowserVersion();
        return (name == null ? "unknown" : name) + "/" + (version == null ? "unknown" : version);
    }

    private static Capabilities capabilitiesOf(WebDriver driver) {
        try {
            if (driver instanceof HasCapabilities) {
                return ((HasCapabilities) driver).getCapabilities();
            }
        } catch (Exception ignored) {
            // fall through to null -- detection is best-effort only
        }
        return null;
    }

    /**
     * The active CDP session: buffers a bounded, in-memory list of
     * request/response pairs. All v146 domain-model types
     * ({@link Request}, {@link Response}, {@link Headers}, etc.) are consumed
     * and converted to plain {@link JSONObject}s entirely within this class,
     * so nothing v146-specific ever leaks out through {@link CdpNetworkSession}.
     */
    private static final class Session implements CdpNetworkSession {

        private final DevTools devTools;
        private final int maxEntries;
        private final boolean redact;
        private final Deque<JSONObject> entries = new ArrayDeque<>();
        private final Map<String, JSONObject> pendingByRequestId = Collections.synchronizedMap(new HashMap<String, JSONObject>());

        private Session(DevTools devTools, int maxEntries, boolean redact) {
            this.devTools = devTools;
            this.maxEntries = maxEntries;
            this.redact = redact;
        }

        private void start() {
            devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            devTools.addListener(Network.requestWillBeSent(), this::onRequest);
            devTools.addListener(Network.responseReceived(), this::onResponse);
        }

        private void onRequest(RequestWillBeSent event) {
            try {
                Request request = event.getRequest();
                JSONObject entry = new JSONObject();
                entry.put("startedDateTime", Instant.now().toString());
                entry.put("request", requestToJson(request));
                addBounded(event.getRequestId().toString(), entry);
            } catch (Exception e) {
                log.debug("[CdpV146NetworkAdapter] requestWillBeSent handling failed: {}", e.getMessage());
            }
        }

        private void onResponse(ResponseReceived event) {
            try {
                JSONObject entry = pendingByRequestId.get(event.getRequestId().toString());
                if (entry == null) {
                    return;
                }
                entry.put("response", responseToJson(event.getResponse()));
            } catch (Exception e) {
                log.debug("[CdpV146NetworkAdapter] responseReceived handling failed: {}", e.getMessage());
            }
        }

        private void addBounded(String requestId, JSONObject entry) {
            synchronized (entries) {
                entries.addLast(entry);
                pendingByRequestId.put(requestId, entry);
                while (entries.size() > maxEntries) {
                    entries.pollFirst();
                }
            }
        }

        private JSONObject requestToJson(Request request) {
            JSONObject json = new JSONObject();
            json.put("method", request.getMethod());
            json.put("url", request.getUrl());
            json.put("headers", headersToJson(request.getHeaders()));
            return json;
        }

        private JSONObject responseToJson(Response response) {
            JSONObject json = new JSONObject();
            json.put("status", response.getStatus());
            json.put("statusText", response.getStatusText());
            json.put("mimeType", response.getMimeType());
            json.put("headers", headersToJson(response.getHeaders()));
            return json;
        }

        private JSONArray headersToJson(Headers headers) {
            JSONArray array = new JSONArray();
            if (headers == null) {
                return array;
            }
            // Redact before the entry is ever buffered/persisted -- not only at
            // report-attachment time -- so the in-memory trace (and therefore any
            // artifact eventually written from it) never holds the raw sensitive value.
            for (Map.Entry<String, Object> header : headers.entrySet()) {
                String name = header.getKey();
                String value = header.getValue() == null ? "" : String.valueOf(header.getValue());
                JSONObject node = new JSONObject();
                node.put("name", name);
                node.put("value", redact ? SecretRedactor.redactFieldValue(name, value) : value);
                array.put(node);
            }
            return array;
        }

        @Override
        public List<JSONObject> snapshotEntries() {
            synchronized (entries) {
                return new java.util.ArrayList<>(entries);
            }
        }

        @Override
        public void close() {
            try {
                devTools.close();
            } catch (Exception ignored) {
                // best effort cleanup only
            }
            entries.clear();
            pendingByRequestId.clear();
        }
    }
}
