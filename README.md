# MONA Pay SDK for Java

SDK Java 11+ zero-dependency cho MONA Pay, cổng thanh toán và API ngân hàng của The MONA Group. SDK dùng `java.net.http.HttpClient`, tự login/cache token và login lại đúng một lần khi HTTP 401.

## Xác thực cho AI agent

```bash
export MONAPAY_CLIENT_ID="client-id"
export MONAPAY_CLIENT_SECRET="client-secret"
export MONAPAY_BASE_URL="https://api.monapay.vn"
```

```java
MonaPay client = MonaPay.fromEnv();
Object profile = client.me();
Object qr = client.qr().generate(qrBody);
Object sandbox = client.sandbox().createTransaction(MonaPay.object("virtual_account_number", "MONA123", "amount", 10000, "description", "AI test"));
System.out.println(profile);
```

`MonaPay.fromEnv()` ưu tiên client credentials, cache token tới gần hạn và tự lấy lại khi gặp HTTP 401. Username/password chỉ là fallback tương thích cũ, không dùng cho AI agent vì sẽ gãy khi bật 2FA.

## Maven

Sau khi package được publish:

```xml
<dependency>
  <groupId>com.themona</groupId>
  <artifactId>monapay</artifactId>
  <version>0.3.0</version>
</dependency>
```

## Dùng nhanh

```java
MonaPay client = MonaPay.builder(
    System.getenv("MONA_USERNAME"),
    System.getenv("MONA_PASSWORD")
).clientSecret(System.getenv("MONA_CLIENT_SECRET")).build();

Object profile = client.me();
Object hooks = client.webhooks().list();
```

Body JSON dùng `Map<String,Object>`; helper `MonaPay.object(...)` giúp viết ngắn. Các resource: `keys`, `bankAccounts`, `va` (đăng ký + hai bước OTP), `qr`, `transactions`, `webhooks`, `webhookLogs`, `sandbox`, `emailConfigs`, `emailLogs`, `emailSuppressions`. POST/PUT/DELETE tự có `X-Client-Secret` khi đã cấu hình; secret vừa generate sẽ được giữ trong client nếu trước đó chưa có.

```java
for (Object transaction : client.transactions().iterate(
    "MONA000001",
    new MonaPay.TransactionOptions().limit(100).sinceId("FT26240001234")
)) {
    // Xử lý transaction; lưu transaction_code để chống trùng.
}
```

`sinceId` là checkpoint phía SDK: iterator dừng trước item có `id` hoặc `transaction_code` trùng mốc. API hiện chưa nhận query `since_id`, nên SDK không gửi tham số này.

Webhook phải được xác thực trên đúng raw bytes:

```java
WebhookVerifier.Result result = WebhookVerifier.verifyWebhook(rawBody, timestamp, signature, secret);
if (!result.isOk()) { /* trả HTTP 401 */ }
```

Ví dụ Spring Boot: `examples/SpringBootWebhookController.java`.

Self-test không dùng JUnit hay Maven dependency:

```bash
rm -rf build && mkdir build
javac --release 11 -d build $(find src/main/java src/test/java -name '*.java')
java -cp build com.themona.monapay.SelfTest
```

Docs: https://monapay.vn/docs · Hotline 1900 636 648 · info@themona.global. MONA Pay miễn phí hoàn toàn.

## English

Zero-dependency Java 11+ SDK for MONA Pay. It covers token caching and one 401 refresh, virtual accounts and both OTP steps, VietQR, client-side `sinceId` transaction iteration, webhook configuration/logs/retry, and constant-time HMAC verification. The Spring Boot source is an integration example and is not part of the dependency-free SDK build.

MIT © The MONA Group.
