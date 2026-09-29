package br.com.portfolio.webhook.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacSignerTest {

    private final HmacSigner signer = new HmacSigner();

    @Test
    @DisplayName("signature is deterministic for the same payload, secret and timestamp")
    void deterministic() {
        String first = signer.sign("{\"a\":1}", "secret", 1_700_000_000L);
        String second = signer.sign("{\"a\":1}", "secret", 1_700_000_000L);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("sha256=");
        assertThat(first.substring("sha256=".length())).hasSize(64); // 32 bytes hex
    }

    @Test
    @DisplayName("known-answer test: HMAC-SHA256 of '1700000000.{\"a\":1}' with key 'secret'")
    void knownAnswer() {
        // Independently computed:
        //   printf '1700000000.{"a":1}' | openssl dgst -sha256 -hmac 'secret'
        String expected = "sha256=49f24e537407743fa4a0242bb63b94b9a47ee99cbbe071ccd8a22550ae411686";

        assertThat(signer.sign("{\"a\":1}", "secret", 1_700_000_000L)).isEqualTo(expected);
    }

    @Test
    @DisplayName("verify accepts a signature produced with the same inputs")
    void verifyAcceptsValidSignature() {
        long ts = 1_700_000_000L;
        String payload = "{\"orderId\":42}";
        String signature = signer.sign(payload, "top-secret", ts);

        assertThat(signer.verify(payload, "top-secret", ts, signature)).isTrue();
    }

    @Test
    @DisplayName("verify rejects a tampered payload")
    void verifyRejectsTamperedPayload() {
        long ts = 1_700_000_000L;
        String signature = signer.sign("{\"amount\":10}", "top-secret", ts);

        assertThat(signer.verify("{\"amount\":9999}", "top-secret", ts, signature)).isFalse();
    }

    @Test
    @DisplayName("verify rejects a wrong secret")
    void verifyRejectsWrongSecret() {
        long ts = 1_700_000_000L;
        String payload = "{\"a\":1}";
        String signature = signer.sign(payload, "right-secret", ts);

        assertThat(signer.verify(payload, "wrong-secret", ts, signature)).isFalse();
    }

    @Test
    @DisplayName("verify rejects a signature replayed with a different timestamp")
    void verifyRejectsReplayWithDifferentTimestamp() {
        String payload = "{\"a\":1}";
        String signature = signer.sign(payload, "secret", 1_700_000_000L);

        assertThat(signer.verify(payload, "secret", 1_700_000_999L, signature)).isFalse();
    }
}
