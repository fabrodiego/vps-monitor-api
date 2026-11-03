package com.fabrodiego.vpsmonitor.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.fabrodiego.vpsmonitor.auth.AuthDtos.LoginRequest;
import com.fabrodiego.vpsmonitor.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class AuthServiceTest {

    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private final Instant now = Instant.parse("2026-11-03T12:00:00Z");
    private MutableClock clock;
    private AuthService auth;
    private Totp totp;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(now);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        byte[] key = new byte[32];
        AppProperties props = new AppProperties(
            new AppProperties.Jwt(Base64.getEncoder().encodeToString(key), Duration.ofHours(8), "teste"),
            new AppProperties.Cors(List.of()),
            new AppProperties.Admin("diego", encoder.encode("senha-forte"), SECRET),
            new AppProperties.Prometheus("http://localhost"),
            new AppProperties.Access("", ""));
        TokenService tokens = new TokenService(
            new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256"))), props, clock);
        auth = new AuthService(props, encoder, tokens, clock);
        totp = new Totp(SECRET);
    }

    private String code() {
        return totp.generate(clock.instant().getEpochSecond() / Totp.STEP_SECONDS);
    }

    @Test
    void loginCertoPassaEOMesmoCodigoNaoValeDuasVezes() {
        String code = code();
        assertDoesNotThrow(() -> auth.login(new LoginRequest("diego", "senha-forte", code)));
        assertThrows(BadCredentialsException.class, () -> auth.login(new LoginRequest("diego", "senha-forte", code)));
    }

    @Test
    void codigoErradoOuUsuarioErradoFalha() {
        assertThrows(BadCredentialsException.class, () -> auth.login(new LoginRequest("diego", "senha-forte", "000000")));
        assertThrows(BadCredentialsException.class, () -> auth.login(new LoginRequest("outro", "senha-forte", code())));
    }

    @Test
    void cincoErrosTravamPorQuinzeMinutos() {
        for (int i = 0; i < AuthService.MAX_FAILURES; i++) {
            assertThrows(BadCredentialsException.class, () -> auth.login(new LoginRequest("diego", "errada", code())));
        }
        assertThrows(LockedException.class, () -> auth.login(new LoginRequest("diego", "senha-forte", code())));
        clock.advance(AuthService.LOCK_TIME.plusSeconds(1));
        assertDoesNotThrow(() -> auth.login(new LoginRequest("diego", "senha-forte", code())));
    }

    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration d) {
            instant = instant.plus(d);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
