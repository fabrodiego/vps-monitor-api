package com.fabrodiego.vpsmonitor.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.fabrodiego.vpsmonitor.auth.AuthDtos.TokenResponse;
import com.fabrodiego.vpsmonitor.config.AppProperties;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public TokenResponse issue(String username) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(props.jwt().issuer())
            .subject(username)
            .issuedAt(now)
            .expiresAt(now.plus(props.jwt().ttl()))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", props.jwt().ttl().toSeconds());
    }
}
