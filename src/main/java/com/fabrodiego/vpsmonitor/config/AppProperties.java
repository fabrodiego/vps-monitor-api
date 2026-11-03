package com.fabrodiego.vpsmonitor.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Admin admin, Prometheus prometheus, Access access) {

    /** secret: base64, minimo 32 bytes (HS256). */
    public record Jwt(String secret, Duration ttl, String issuer) {
    }

    public record Cors(List<String> origins) {
    }

    /** Unico usuario do site. passwordHash: BCrypt. totpSecret: base32 (app autenticador). */
    public record Admin(String username, String passwordHash, String totpSecret) {
    }

    public record Prometheus(String url) {
    }

    /**
     * Cloudflare Access na frente da API. teamDomain ex.: "minhaequipe" (de minhaequipe.cloudflareaccess.com).
     * Vazio = checagem desligada (uso local).
     */
    public record Access(String teamDomain, String audience) {

        public boolean enabled() {
            return teamDomain != null && !teamDomain.isBlank() && audience != null && !audience.isBlank();
        }
    }
}
