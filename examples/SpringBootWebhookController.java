package example;

import com.themona.monapay.WebhookVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class SpringBootWebhookController {
    @Value("${monapay.webhook-secret}")
    private String secret;

    @PostMapping("/webhooks/monapay")
    public ResponseEntity<?> receive(
        @RequestBody byte[] rawBody,
        @RequestHeader("X-Mona-Timestamp") String timestamp,
        @RequestHeader("X-Mona-Signature") String signature
    ) {
        WebhookVerifier.Result result = WebhookVerifier.verifyWebhook(rawBody, timestamp, signature, secret);
        if (!result.isOk()) return ResponseEntity.status(401).body(result.getReason());

        // Queue result.getPayload(); transaction_code is the idempotency key.
        return ResponseEntity.accepted().build();
    }
}
