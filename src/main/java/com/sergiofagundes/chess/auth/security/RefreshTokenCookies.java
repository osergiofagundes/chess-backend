package com.sergiofagundes.chess.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.sergiofagundes.chess.auth.service.RefreshTokenService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class RefreshTokenCookies {

    public static final String NAME = "refresh_token";

    private static final String PATH = "/api/v1/auth";

    private final boolean secure;

    RefreshTokenCookies(@Value("${app.auth.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(RefreshTokenService.IssuedToken token) {
        return base(token.rawValue())
                .maxAge(Duration.between(Instant.now(), token.expiresAt()))
                .build();
    }

    public ResponseCookie expired() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path(PATH)
                .sameSite("Lax");
    }

    public Optional<String> read(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
