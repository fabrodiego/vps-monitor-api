package com.fabrodiego.vpsmonitor.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TotpTest {

    // vetor da RFC 6238 (chave ASCII "12345678901234567890" em base32)
    private final Totp totp = new Totp("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");

    @Test
    void geraOCodigoDaRfc() {
        assertEquals("287082", totp.generate(59 / Totp.STEP_SECONDS));
        assertEquals("081804", totp.generate(1111111109L / Totp.STEP_SECONDS));
    }

    @Test
    void aceitaUmPassoDeDiferencaENaoMais() {
        Instant t = Instant.ofEpochSecond(1111111109L);
        long step = t.getEpochSecond() / Totp.STEP_SECONDS;
        assertTrue(totp.verify(totp.generate(step - 1), t).isPresent());
        assertTrue(totp.verify(totp.generate(step + 1), t).isPresent());
        assertTrue(totp.verify(totp.generate(step + 2), t).isEmpty());
        assertTrue(totp.verify("abc123", t).isEmpty());
    }
}
