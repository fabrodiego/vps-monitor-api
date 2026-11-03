package com.fabrodiego.vpsmonitor.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 200) String password,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "codigo de 6 digitos") String code) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record MeResponse(String username) {
    }
}
