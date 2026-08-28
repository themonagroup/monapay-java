package com.themona.monapay;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookVerifier {
    private WebhookVerifier() {}

    public static final class Result {
        private final boolean ok;
        private final String reason;
        private final Object payload;

        private Result(boolean ok, String reason, Object payload) {
            this.ok = ok;
            this.reason = reason;
            this.payload = payload;
        }

        public boolean isOk() { return ok; }
        public String getReason() { return reason; }
        public Object getPayload() { return payload; }
    }

    public static Result verifyWebhook(byte[] rawBody, String timestamp, String signature, String secret) {
        return verifyWebhook(rawBody, timestamp, signature, secret, 300);
    }

    public static Result verifyWebhook(byte[] rawBody, String timestamp, String signature, String secret, int toleranceSeconds) {
        if (rawBody == null) throw new IllegalArgumentException("rawBody là bắt buộc");
        if (toleranceSeconds < 0) throw new IllegalArgumentException("tolerance phải là số không âm");
        if (timestamp == null || timestamp.isEmpty()) return fail("missing_timestamp");
        if (!timestamp.matches("[0-9]+")) return fail("invalid_timestamp");
        final long unix;
        try { unix = Long.parseLong(timestamp); }
        catch (NumberFormatException error) { return fail("invalid_timestamp"); }
        long now = Instant.now().getEpochSecond();
        if (unix > now + toleranceSeconds || unix < now - toleranceSeconds) return fail("timestamp_out_of_tolerance");
        if (signature == null || signature.isEmpty()) return fail("missing_signature");

        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            expected = mac.doFinal(rawBody);
        } catch (Exception error) {
            throw new IllegalStateException("Không khởi tạo được HMAC-SHA256", error);
        }

        byte[] supplied = new byte[expected.length];
        boolean formatOK = signature.matches("sha256=[0-9a-fA-F]{64}");
        if (formatOK) {
            try { supplied = decodeHex(signature.substring(7)); }
            catch (IllegalArgumentException error) { formatOK = false; }
        }
        boolean signatureOK = MessageDigest.isEqual(expected, supplied) & formatOK;
        Arrays.fill(supplied, (byte) 0);
        if (!signatureOK) return fail("invalid_signature");
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(rawBody))
                .toString();
            Object payload = Json.parse(json);
            return new Result(true, null, payload);
        } catch (IllegalArgumentException | CharacterCodingException error) {
            return fail("invalid_json");
        }
    }

    private static Result fail(String reason) { return new Result(false, reason, null); }

    private static byte[] decodeHex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("invalid hex");
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }
}
