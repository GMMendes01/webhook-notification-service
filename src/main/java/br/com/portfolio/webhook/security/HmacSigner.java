package br.com.portfolio.webhook.security;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signs webhook payloads with HMAC-SHA256 so receivers can prove the request
 * really came from us and was not tampered with.
 *
 * <p>Signature scheme (Stripe-style): receivers compute
 * {@code HMAC(secret, timestamp + "." + body)} and compare it against the
 * {@code X-Webhook-Signature} header. Binding the timestamp into the signed
 * string also lets them reject replays outside a tolerance window.
 */
@Component
public class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";
    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    public static final String EVENT_ID_HEADER = "X-Webhook-Event-Id";
    public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";

    /** Returns {@code sha256=<lowercase-hex>} over {@code timestamp + "." + payload}. */
    public String sign(String payload, String secret, long timestamp) {
        String signedContent = timestamp + "." + payload;
        return "sha256=" + hmacHex(signedContent, secret);
    }

    /** Constant-time verification helper (used by tests and documented in the README). */
    public boolean verify(String payload, String secret, long timestamp, String signatureHeader) {
        String expected = sign(payload, secret, timestamp);
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute HMAC-SHA256 signature", e);
        }
    }
}
