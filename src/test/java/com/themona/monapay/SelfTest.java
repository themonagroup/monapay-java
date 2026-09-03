package com.themona.monapay;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** JUnit-free offline self-test. */
public final class SelfTest {
    public static void main(String[] args) throws Exception {
        testWebhook();
        testClientAndRefresh();
        testIteratorSinceId();
        System.out.println("MONA Pay Java self-test: PASS");
    }

    private static void testWebhook() throws Exception {
        byte[] raw = "{\"amount\":2500000,\"transaction_code\":\"FT1\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(raw, timestamp, "test-secret");
        check(WebhookVerifier.verifyWebhook(raw, timestamp, signature, "test-secret").isOk(), "valid webhook");
        check("invalid_signature".equals(WebhookVerifier.verifyWebhook(raw, timestamp, "sha256=" + repeat("0", 64), "test-secret").getReason()), "bad signature");
        check("timestamp_out_of_tolerance".equals(WebhookVerifier.verifyWebhook(raw, String.valueOf(Instant.now().getEpochSecond() - 301), signature, "test-secret").getReason()), "old timestamp");
    }

    private static void testClientAndRefresh() {
        final int[] logins = {0};
        final int[] meCalls = {0};
        List<MonaPay.Request> calls = new ArrayList<>();
        MonaPay.Transport fake = request -> {
            calls.add(request);
            if (request.getUrl().endsWith("/api/v1/oauth/token")) {
                logins[0]++;
                check(request.getBody().contains("\"grant_type\":\"client_credentials\"") && request.getBody().contains("\"client_id\":\"client-id\"") && request.getBody().contains("\"client_secret\":\"secret\""), "client credentials body");
                return ok("{\"access_token\":\"token-" + logins[0] + "\",\"expires_in\":3600}");
            }
            if (request.getUrl().endsWith("/api/v1/client-webhooks")) {
                check("secret".equals(request.getHeaders().get("X-Client-Secret")), "client secret header");
                return ok("{\"id\":\"hook-1\"}");
            }
            if (request.getUrl().endsWith("/api/v1/checkouts")) {
                check("checkout-key".equals(request.getHeaders().get("Idempotency-Key")), "checkout idempotency header");
                check("secret".equals(request.getHeaders().get("X-Client-Secret")), "checkout client secret header");
                return ok("{\"checkout_url\":\"https://pay.monapay.vn/c/token\"}");
            }
            meCalls[0]++;
            if (meCalls[0] == 1) return new MonaPay.Response(401, "{\"detail\":\"expired\"}");
            check("Bearer token-2".equals(request.getHeaders().get("Authorization")), "refreshed bearer");
            check(!request.getHeaders().containsKey("X-Client-Secret"), "GET has no secret");
            return ok("{\"username\":\"user\"}");
        };
        MonaPay client = MonaPay.clientCredentials("client-id", "secret").baseUrl("https://example.test/").transport(fake).build();
        client.webhooks().create(MonaPay.object("name", "Shop"));
        client.me();
        client.checkouts().create(MonaPay.object("amount", 250000), "checkout-key");
        check(logins[0] == 2, "one refresh after 401");
        check(calls.size() == 6, "expected request count");
    }

    private static void testIteratorSinceId() {
        final int[] pageCalls = {0};
        MonaPay.Transport fake = request -> {
            if (request.getUrl().endsWith("/api/v1/client/login")) return ok("{\"access_token\":\"token\"}");
            check(!request.getUrl().contains("since_id"), "since_id must stay client-side");
            pageCalls[0]++;
            if (request.getUrl().contains("page=1")) return ok("{\"data\":[{\"id\":\"tx-3\"},{\"id\":\"tx-2\"}],\"last_page\":2}");
            return ok("{\"data\":[{\"id\":\"tx-1\"}],\"last_page\":2}");
        };
        MonaPay client = MonaPay.builder("user", "pass").baseUrl("https://example.test").transport(fake).build();
        List<String> ids = new ArrayList<>();
        for (Object item : client.transactions().iterate("MONA 01", new MonaPay.TransactionOptions().limit(2).sinceId("tx-1"))) {
            ids.add(String.valueOf(((java.util.Map<?, ?>) item).get("id")));
        }
        check(ids.toString().equals("[tx-3, tx-2]"), "iterator values");
        check(pageCalls[0] == 2, "iterator pages");
    }

    private static MonaPay.Response ok(String data) {
        return new MonaPay.Response(200, "{\"success\":true,\"data\":" + data + "}");
    }

    private static String sign(byte[] raw, String timestamp, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
        byte[] bytes = mac.doFinal(raw);
        StringBuilder hex = new StringBuilder();
        for (byte value : bytes) hex.append(String.format("%02x", value & 0xff));
        return "sha256=" + hex;
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
