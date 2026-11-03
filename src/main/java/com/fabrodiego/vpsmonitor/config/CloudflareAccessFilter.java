package com.fabrodiego.vpsmonitor.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exige o JWT que o Cloudflare Access coloca em toda requisicao que passou por ele
 * (header Cf-Access-Jwt-Assertion). Quem chegar no pod sem passar pelo Access leva 403.
 * O health fica de fora porque o kubelet chama o pod direto.
 */
@Component
public class CloudflareAccessFilter extends OncePerRequestFilter {

    static final String HEADER = "Cf-Access-Jwt-Assertion";
    private static final Logger log = LoggerFactory.getLogger(CloudflareAccessFilter.class);

    private final JwtDecoder decoder;

    public CloudflareAccessFilter(AppProperties props) {
        AppProperties.Access access = props.access();
        if (access == null || !access.enabled()) {
            this.decoder = null;
            return;
        }
        String issuer = "https://" + access.teamDomain() + ".cloudflareaccess.com";
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(issuer + "/cdn-cgi/access/certs").build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(access.audience())
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "aud do Access invalido", null));
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience));
        this.decoder = nimbus;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return decoder == null
            || HttpMethod.OPTIONS.matches(request.getMethod())
            || request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            log.warn("Recusado sem header do Access: {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpStatus.FORBIDDEN.value(), "Acesso só pelo Cloudflare Access");
            return;
        }
        try {
            decoder.decode(token);
        } catch (JwtException e) {
            // so o motivo, nunca o token
            log.warn("Token do Access recusado em {}: {}", request.getRequestURI(), e.getMessage());
            response.sendError(HttpStatus.FORBIDDEN.value(), "Token do Cloudflare Access inválido");
            return;
        }
        chain.doFilter(request, response);
    }
}
