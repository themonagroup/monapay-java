package com.themona.monapay;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Synchronous, zero-dependency MONA Pay API client for Java 11+. */
public final class MonaPay {
    public static final String DEFAULT_BASE_URL = "https://api.monapay.vn";

    public interface Transport {
        Response send(Request request) throws IOException, InterruptedException;
    }

    public static final class Request {
        private final String method;
        private final String url;
        private final Map<String, String> headers;
        private final String body;

        public Request(String method, String url, Map<String, String> headers, String body) {
            this.method = method;
            this.url = url;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            this.body = body;
        }
        public String getMethod() { return method; }
        public String getUrl() { return url; }
        public Map<String, String> getHeaders() { return headers; }
        public String getBody() { return body; }
    }

    public static final class Response {
        private final int status;
        private final String body;
        public Response(int status, String body) { this.status = status; this.body = body == null ? "" : body; }
        public int getStatus() { return status; }
        public String getBody() { return body; }
    }

    public static Builder builder(String username, String password) { return new Builder(username, password); }
    public static Builder clientCredentials(String clientId, String clientSecret) {
        return new Builder("", "").clientId(clientId).clientSecret(clientSecret);
    }
    public static MonaPay fromEnv() {
        String clientId = System.getenv("MONAPAY_CLIENT_ID");
        String clientSecret = System.getenv("MONAPAY_CLIENT_SECRET");
        Builder builder = clientId != null && !clientId.isEmpty() && clientSecret != null && !clientSecret.isEmpty()
            ? clientCredentials(clientId, clientSecret)
            : builder(System.getenv("MONAPAY_USERNAME"), System.getenv("MONAPAY_PASSWORD")).clientSecret(clientSecret);
        String baseUrl = System.getenv("MONAPAY_BASE_URL");
        return (baseUrl == null || baseUrl.isEmpty() ? builder : builder.baseUrl(baseUrl)).build();
    }

    public static final class Builder {
        private final String username;
        private final String password;
        private String clientId;
        private String clientSecret;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = Duration.ofSeconds(30);
        private Transport transport;

        private Builder(String username, String password) { this.username = username; this.password = password; }
        private Builder clientId(String value) { clientId = value; return this; }
        public Builder clientSecret(String value) { clientSecret = value; return this; }
        public Builder baseUrl(String value) { baseUrl = value; return this; }
        public Builder timeout(Duration value) { timeout = value; return this; }
        public Builder transport(Transport value) { transport = value; return this; }
        public MonaPay build() { return new MonaPay(this); }
    }

    private final String username;
    private final String password;
    private final String clientId;
    private final String baseUrl;
    private final Transport transport;
    private final Object authLock = new Object();
    private volatile String accessToken;
    private volatile String clientSecret;
    private volatile long tokenExpiresAtMillis;

    private final Keys keys = new Keys();
    private final VirtualAccounts va = new VirtualAccounts();
    private final BankAccounts bankAccounts = new BankAccounts();
    private final QR qr = new QR();
    private final Transactions transactions = new Transactions();
    private final Webhooks webhooks = new Webhooks();
    private final WebhookLogs webhookLogs = new WebhookLogs();
    private final Sandbox sandbox = new Sandbox();
    private final EmailConfigs emailConfigs = new EmailConfigs();
    private final EmailLogs emailLogs = new EmailLogs();
    private final EmailSuppressions emailSuppressions = new EmailSuppressions();

    private MonaPay(Builder builder) {
        boolean hasClientCredentials = builder.clientId != null && !builder.clientId.trim().isEmpty() && builder.clientSecret != null && !builder.clientSecret.trim().isEmpty();
        boolean hasPasswordCredentials = builder.username != null && !builder.username.trim().isEmpty() && builder.password != null && !builder.password.isEmpty();
        if (!hasClientCredentials && !hasPasswordCredentials) {
            throw new IllegalArgumentException("Cần client ID + client secret hoặc username + password; không dùng password cho AI agent vì sẽ gãy khi bật 2FA");
        }
        clientId = builder.clientId;
        username = builder.username;
        password = builder.password;
        clientSecret = builder.clientSecret;
        baseUrl = trimBaseUrl(builder.baseUrl);
        transport = builder.transport == null ? new JdkTransport(builder.timeout) : builder.transport;
    }

    public Keys keys() { return keys; }
    public VirtualAccounts va() { return va; }
    public BankAccounts bankAccounts() { return bankAccounts; }
    public QR qr() { return qr; }
    public Transactions transactions() { return transactions; }
    public Webhooks webhooks() { return webhooks; }
    public WebhookLogs webhookLogs() { return webhookLogs; }
    public Sandbox sandbox() { return sandbox; }
    public EmailConfigs emailConfigs() { return emailConfigs; }
    public EmailLogs emailLogs() { return emailLogs; }
    public EmailSuppressions emailSuppressions() { return emailSuppressions; }
    public void setClientSecret(String value) { clientSecret = value; }
    public Object me() { return request("GET", "/api/v1/client/me", null, null); }

    public final class Keys {
        public Object generate() { return generate("Default Key"); }
        public Object generate(String name) {
            Object data = request("POST", "/api/v1/client-keys/generate", map("name", name == null || name.isEmpty() ? "Default Key" : name), null);
            if ((clientSecret == null || clientSecret.isEmpty()) && data instanceof Map<?, ?>) {
                Object secret = ((Map<?, ?>) data).get("client_secret");
                if (secret instanceof String) clientSecret = (String) secret;
            }
            return data;
        }
        public Object list() { return request("GET", "/api/v1/client-keys/list", null, null); }
        public Object destroy(String keyId) { return request("DELETE", "/api/v1/client-keys/destroy/" + segment(keyId), null, null); }
    }

    public final class VirtualAccounts {
        public Object register(Map<String, Object> body) { return request("POST", "/api/v1/acb/virtual-account/registration", body, null); }
        public Object verify(String requestId, String code) { return request("POST", "/api/v1/acb/" + segment(requestId) + "/virtual-account/verification", map("code", code), null); }
        public Object registerNotification(String vaId, Map<String, Object> body) { return request("POST", "/api/v1/acb/" + segment(vaId) + "/notification/registration", body, null); }
        public Object verifyNotification(String requestId, String code) { return request("POST", "/api/v1/acb/" + segment(requestId) + "/notification/verification", map("code", code), null); }
        public Object list(String bankAccountId) { return request("GET", "/api/v1/acb/" + segment(bankAccountId) + "/virtual-account/retrieve", null, null); }
    }

    public final class BankAccounts {
        public Object list() { return request("GET", "/api/v1/client/bank-accounts", null, null); }
    }

    public final class QR {
        public Object generate(Map<String, Object> body) { return request("POST", "/api/v1/acb/qr-payment/generate", body, null); }
        public Object cancel(String qrCodeId) { return cancel(qrCodeId, null); }
        public Object cancel(String qrCodeId, Map<String, Object> body) { return request("DELETE", "/api/v1/acb/qr-payment/" + segment(qrCodeId) + "/cancellation", body, null); }
    }

    public static final class TransactionOptions {
        private int page = 1;
        private int limit = 100;
        private String sinceId;
        public TransactionOptions page(int value) { page = value; return this; }
        public TransactionOptions limit(int value) { limit = value; return this; }
        public TransactionOptions sinceId(String value) { sinceId = value; return this; }
    }

    public final class Transactions {
        public Object list(String virtualAccountNumber) { return list(virtualAccountNumber, new TransactionOptions()); }
        public Object list(String virtualAccountNumber, TransactionOptions options) {
            if (virtualAccountNumber == null || virtualAccountNumber.isEmpty()) throw new IllegalArgumentException("virtualAccountNumber là bắt buộc");
            TransactionOptions actual = options == null ? new TransactionOptions() : options;
            Map<String, Object> query = map(
                "virtual_account_number", virtualAccountNumber,
                "page", positive(actual.page, 1),
                "limit", positive(actual.limit, 100)
            );
            // since_id stays client-side because the current API does not accept it.
            return request("GET", "/api/v1/acb/virtual-account/transactions", null, query);
        }
        public Iterable<Object> iterate(String virtualAccountNumber) { return iterate(virtualAccountNumber, new TransactionOptions()); }
        public Iterable<Object> iterate(String virtualAccountNumber, TransactionOptions options) {
            TransactionOptions actual = options == null ? new TransactionOptions() : options;
            return () -> new TransactionIterator(virtualAccountNumber, actual);
        }
        public Object retry(String transactionId, String targetType) { return retry(transactionId, targetType, null); }
        public Object retry(String transactionId, String targetType, String targetId) {
            Map<String, Object> body = map("target_type", targetType);
            if (targetId != null) body.put("target_id", targetId);
            return request("POST", "/api/v1/acb/virtual-account/transactions/" + segment(transactionId) + "/retry", body, null);
        }
    }

    private final class TransactionIterator implements Iterator<Object> {
        private final String virtualAccountNumber;
        private final int limit;
        private final String sinceId;
        private int page;
        private List<?> items = Collections.emptyList();
        private int index;
        private boolean done;
        private boolean ready;
        private Object next;

        private TransactionIterator(String virtualAccountNumber, TransactionOptions options) {
            this.virtualAccountNumber = virtualAccountNumber;
            page = positive(options.page, 1);
            limit = positive(options.limit, 100);
            sinceId = options.sinceId;
        }

        @Override public boolean hasNext() {
            if (ready) return true;
            while (true) {
                if (index < items.size()) {
                    Object candidate = items.get(index++);
                    if (sinceId != null && matches(candidate, sinceId)) { done = true; return false; }
                    next = candidate;
                    ready = true;
                    return true;
                }
                if (done) return false;
                Object response = transactions.list(virtualAccountNumber, new TransactionOptions().page(page).limit(limit));
                if (!(response instanceof Map<?, ?>)) throw new MonaPayException("Response giao dịch không phải object");
                Map<?, ?> result = (Map<?, ?>) response;
                Object data = result.get("data");
                items = data instanceof List<?> ? (List<?>) data : Collections.emptyList();
                index = 0;
                Object hasNext = result.get("has_next");
                if (hasNext instanceof Boolean) done = !((Boolean) hasNext);
                else done = page >= intValue(result.get("last_page"), page);
                page++;
            }
        }

        @Override public Object next() {
            if (!hasNext()) throw new NoSuchElementException();
            ready = false;
            return next;
        }
    }

    public final class Webhooks {
        public Object list() { return request("GET", "/api/v1/client-webhooks", null, null); }
        public Object create(Map<String, Object> body) { return request("POST", "/api/v1/client-webhooks", body, null); }
        public Object update(String configId, Map<String, Object> body) { return request("PUT", "/api/v1/client-webhooks/" + segment(configId), body, null); }
        public Object remove(String configId) { return request("DELETE", "/api/v1/client-webhooks/" + segment(configId), null, null); }
        public Object test(Map<String, Object> body) { return request("POST", "/api/v1/client-webhooks/test", body, null); }
    }

    public static final class WebhookLogOptions {
        private String status;
        private String fromDate;
        private String toDate;
        private Integer page;
        private Integer limit;
        public WebhookLogOptions status(String value) { status = value; return this; }
        public WebhookLogOptions fromDate(String value) { fromDate = value; return this; }
        public WebhookLogOptions toDate(String value) { toDate = value; return this; }
        public WebhookLogOptions page(int value) { page = value; return this; }
        public WebhookLogOptions limit(int value) { limit = value; return this; }
    }

    public final class WebhookLogs {
        public Object list() { return list(new WebhookLogOptions()); }
        public Object list(WebhookLogOptions options) { return request("GET", "/api/v1/webhook-logs", null, logQuery(options)); }
        public Object stats() { return stats(new WebhookLogOptions()); }
        public Object stats(WebhookLogOptions options) { return request("GET", "/api/v1/webhook-logs/stats", null, logQuery(options)); }
    }

    public final class Sandbox {
        public Object createTransaction(Map<String, Object> body) { return request("POST", "/api/v1/sandbox/transactions", body, null); }
    }

    public final class EmailConfigs {
        public Object list() { return request("GET", "/api/v1/email-configs", null, null); }
        public Object create(Map<String, Object> body) { return request("POST", "/api/v1/email-configs", body, null); }
        public Object get(String configId) { return request("GET", "/api/v1/email-configs/" + segment(configId), null, null); }
        public Object update(String configId, Map<String, Object> body) { return request("PUT", "/api/v1/email-configs/" + segment(configId), body, null); }
        public Object remove(String configId) { return request("DELETE", "/api/v1/email-configs/" + segment(configId), null, null); }
        public Object verify(String configId, String email, String code) { return request("POST", "/api/v1/email-configs/" + segment(configId) + "/verify", map("email", email, "code", code), null); }
        public Object resendVerification(String configId, String email) { return request("POST", "/api/v1/email-configs/" + segment(configId) + "/resend-verification", map("email", email), null); }
        public Object test(String configId) { return request("POST", "/api/v1/email-configs/" + segment(configId) + "/test", map(), null); }
    }

    public static final class EmailLogOptions {
        private String configId;
        private String status;
        private String eventType;
        private String fromDate;
        private String toDate;
        private Integer page;
        private Integer limit;
        public EmailLogOptions configId(String value) { configId = value; return this; }
        public EmailLogOptions status(String value) { status = value; return this; }
        public EmailLogOptions eventType(String value) { eventType = value; return this; }
        public EmailLogOptions fromDate(String value) { fromDate = value; return this; }
        public EmailLogOptions toDate(String value) { toDate = value; return this; }
        public EmailLogOptions page(int value) { page = value; return this; }
        public EmailLogOptions limit(int value) { limit = value; return this; }
    }

    public final class EmailLogs {
        public Object list() { return list(new EmailLogOptions()); }
        public Object list(EmailLogOptions options) { return request("GET", "/api/v1/email-logs", null, emailLogQuery(options, true)); }
        public Object stats() { return stats(new EmailLogOptions()); }
        public Object stats(EmailLogOptions options) { return request("GET", "/api/v1/email-logs/stats", null, emailLogQuery(options, false)); }
    }

    public final class EmailSuppressions {
        public Object list() { return request("GET", "/api/v1/email-suppressions", null, null); }
        public Object remove(String email) { return request("DELETE", "/api/v1/email-suppressions/" + segment(email), null, null); }
    }

    private Object request(String method, String path, Object body, Map<String, Object> query) {
        login();
        String usedToken = accessToken;
        try {
            return send(method, path, body, query, usedToken, clientSecret);
        } catch (MonaPayException error) {
            if (error.getStatus() != 401) throw error;
            synchronized (authLock) {
                if (Objects.equals(accessToken, usedToken)) { accessToken = null; tokenExpiresAtMillis = 0; }
            }
            login();
            return send(method, path, body, query, accessToken, clientSecret);
        }
    }

    private void login() {
        synchronized (authLock) {
            if (accessToken != null && !accessToken.isEmpty() && System.currentTimeMillis() < tokenExpiresAtMillis) return;
            boolean usingClientCredentials = clientId != null && !clientId.isEmpty() && clientSecret != null && !clientSecret.isEmpty();
            Object body = usingClientCredentials
                ? map("grant_type", "client_credentials", "client_id", clientId, "client_secret", clientSecret)
                : map("username", username, "password", password);
            Object data = send("POST", usingClientCredentials ? "/api/v1/oauth/token" : "/api/v1/client/login", body, null, null, null);
            if (!(data instanceof Map<?, ?>) || !(((Map<?, ?>) data).get("access_token") instanceof String)) {
                throw new MonaPayException("Response đăng nhập không có access_token");
            }
            accessToken = (String) ((Map<?, ?>) data).get("access_token");
            Object rawExpires = ((Map<?, ?>) data).get("expires_in");
            long expiresIn = rawExpires instanceof Number ? ((Number) rawExpires).longValue() : (usingClientCredentials ? 3600 : 86400);
            tokenExpiresAtMillis = System.currentTimeMillis() + Math.max(0, expiresIn - 60) * 1000;
        }
    }

    private Object send(String method, String path, Object body, Map<String, Object> query, String token, String secret) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (token != null) headers.put("Authorization", "Bearer " + token);
        if (token != null && !"GET".equals(method) && secret != null && !secret.isEmpty()) headers.put("X-Client-Secret", secret);
        String encodedBody = null;
        if (body != null) {
            headers.put("Content-Type", "application/json");
            encodedBody = Json.stringify(body);
        }
        final Response response;
        try {
            response = transport.send(new Request(method, buildUrl(path, query), headers, encodedBody));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new MonaPayException("Request MONA Pay bị gián đoạn", error);
        } catch (IOException error) {
            throw new MonaPayException("Không kết nối được MONA Pay", error);
        }
        Object parsed;
        try { parsed = Json.parse(response.getBody().isEmpty() ? "{}" : response.getBody()); }
        catch (IllegalArgumentException error) {
            throw new MonaPayException("MONA Pay trả response không phải JSON (HTTP " + response.getStatus() + ")", response.getStatus(), response.getBody());
        }
        if (!(parsed instanceof Map<?, ?>)) throw new MonaPayException("MONA Pay trả response không phải object JSON", response.getStatus(), parsed);
        Map<?, ?> envelope = (Map<?, ?>) parsed;
        boolean failed = response.getStatus() < 200 || response.getStatus() >= 300 || Boolean.FALSE.equals(envelope.get("success"));
        if (failed) {
            Object message = envelope.get("message");
            if (!(message instanceof String) || ((String) message).isEmpty()) message = envelope.get("detail");
            if (!(message instanceof String) || ((String) message).isEmpty()) message = "MONA Pay API lỗi HTTP " + response.getStatus();
            throw new MonaPayException((String) message, response.getStatus(), envelope);
        }
        return envelope.get("data");
    }

    private String buildUrl(String path, Map<String, Object> query) {
        StringBuilder result = new StringBuilder(baseUrl).append(path);
        if (query != null) {
            boolean first = true;
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                if (entry.getValue() == null) continue;
                result.append(first ? '?' : '&');
                first = false;
                result.append(query(entry.getKey())).append('=').append(query(String.valueOf(entry.getValue())));
            }
        }
        return result.toString();
    }

    private static String trimBaseUrl(String value) {
        String result = value == null || value.isEmpty() ? DEFAULT_BASE_URL : value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        URI.create(result);
        return result;
    }

    private static String segment(String value) { return query(value).replace("+", "%20"); }
    private static String query(String value) { return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8); }
    private static int positive(int value, int fallback) { return value > 0 ? value : fallback; }
    private static int intValue(Object value, int fallback) { return value instanceof Number ? ((Number) value).intValue() : fallback; }

    private static boolean matches(Object item, String sinceId) {
        if (!(item instanceof Map<?, ?>)) return false;
        Map<?, ?> object = (Map<?, ?>) item;
        return sinceId.equals(String.valueOf(object.get("id"))) || sinceId.equals(String.valueOf(object.get("transaction_code")));
    }

    private static Map<String, Object> logQuery(WebhookLogOptions options) {
        WebhookLogOptions actual = options == null ? new WebhookLogOptions() : options;
        return map("status", actual.status, "from_date", actual.fromDate, "to_date", actual.toDate, "page", actual.page, "limit", actual.limit);
    }

    private static Map<String, Object> emailLogQuery(EmailLogOptions options, boolean includeFilters) {
        EmailLogOptions actual = options == null ? new EmailLogOptions() : options;
        return map(
            "config_id", includeFilters ? actual.configId : null,
            "status", includeFilters ? actual.status : null,
            "event_type", includeFilters ? actual.eventType : null,
            "from_date", actual.fromDate, "to_date", actual.toDate,
            "page", includeFilters ? actual.page : null, "limit", includeFilters ? actual.limit : null
        );
    }

    public static Map<String, Object> object(Object... keyValues) { return map(keyValues); }

    private static Map<String, Object> map(Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("key/value phải đi theo cặp");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        return result;
    }

    private static final class JdkTransport implements Transport {
        private final HttpClient client;
        private JdkTransport(Duration timeout) {
            client = HttpClient.newBuilder().connectTimeout(timeout).build();
        }
        @Override public Response send(Request request) throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.getUrl()));
            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) builder.header(header.getKey(), header.getValue());
            HttpRequest.BodyPublisher publisher = request.getBody() == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(request.getBody(), StandardCharsets.UTF_8);
            builder.method(request.getMethod(), publisher);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        }
    }
}
