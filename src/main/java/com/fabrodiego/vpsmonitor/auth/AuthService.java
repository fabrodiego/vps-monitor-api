package com.fabrodiego.vpsmonitor.auth;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fabrodiego.vpsmonitor.auth.AuthDtos.LoginRequest;
import com.fabrodiego.vpsmonitor.auth.AuthDtos.TokenResponse;
import com.fabrodiego.vpsmonitor.config.AppProperties;

/**
 * Login do unico usuario: usuario + senha (BCrypt) + codigo do app autenticador.
 * 5 erros seguidos travam o login por 15 min. O mesmo codigo nao vale duas vezes.
 */
@Service
public class AuthService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);
    private static final String INVALID = "Usuario, senha ou codigo invalidos";

    private final AppProperties.Admin admin;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Totp totp;
    private final Clock clock;

    private int failures;
    private Instant lockedUntil = Instant.EPOCH;
    private long lastUsedStep = -1;

    public AuthService(AppProperties props, PasswordEncoder passwordEncoder, TokenService tokens, Clock clock) {
        this.admin = props.admin();
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.totp = new Totp(admin.totpSecret());
        this.clock = clock;
        if (admin.username() == null || admin.username().isBlank()
            || admin.passwordHash() == null || !admin.passwordHash().startsWith("$2")) {
            throw new IllegalStateException("ADMIN_USER e ADMIN_PASSWORD_HASH (BCrypt) sao obrigatorios");
        }
    }

    public synchronized TokenResponse login(LoginRequest req) {
        Instant now = clock.instant();
        if (now.isBefore(lockedUntil)) {
            throw new LockedException("Muitas tentativas. Tente de novo depois de " + lockedUntil);
        }
        // a senha e conferida sempre (mesmo com usuario errado) para nao vazar pelo tempo de resposta
        boolean passwordOk = passwordEncoder.matches(req.password(), admin.passwordHash());
        boolean userOk = MessageDigest.isEqual(req.username().getBytes(), admin.username().getBytes());
        OptionalLong step = totp.verify(req.code(), now);
        boolean codeOk = step.isPresent() && step.getAsLong() > lastUsedStep;

        if (!(passwordOk && userOk && codeOk)) {
            failures++;
            if (failures >= MAX_FAILURES) {
                lockedUntil = now.plus(LOCK_TIME);
                failures = 0;
            }
            throw new BadCredentialsException(INVALID);
        }
        failures = 0;
        lastUsedStep = step.getAsLong();
        return tokens.issue(admin.username());
    }
}
