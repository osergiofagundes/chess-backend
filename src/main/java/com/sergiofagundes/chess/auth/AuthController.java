package com.sergiofagundes.chess.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sergiofagundes.chess.auth.dto.AuthResponse;
import com.sergiofagundes.chess.auth.dto.LoginRequest;
import com.sergiofagundes.chess.auth.dto.RegisterRequest;
import com.sergiofagundes.chess.auth.security.RefreshTokenCookies;
import com.sergiofagundes.chess.auth.service.AuthService;
import com.sergiofagundes.chess.common.exception.BusinessException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies cookies;

    AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return respond(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        return respond(authService.refresh(readRefreshToken(request)), HttpStatus.OK);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        cookies.read(request).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expired().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> respond(AuthService.AuthResult result, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(result.body());
    }

    private String readRefreshToken(HttpServletRequest request) {
        return cookies.read(request).orElseThrow(() -> new BusinessException(
                HttpStatus.UNAUTHORIZED, "MISSING_REFRESH_TOKEN", "Refresh token ausente"));
    }
}
