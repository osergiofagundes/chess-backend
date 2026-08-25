package com.sergiofagundes.chess.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sergiofagundes.chess.auth.entity.RefreshToken;
import com.sergiofagundes.chess.auth.repository.RefreshTokenRepository;
import com.sergiofagundes.chess.auth.security.JwtProperties;
import com.sergiofagundes.chess.common.exception.BusinessException;
import com.sergiofagundes.chess.user.User;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.ttl = properties.refreshTokenTtl();
    }

    public record IssuedToken(String rawValue, Instant expiresAt) {}

    @Transactional
    public IssuedToken issue(User user) {
        var bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        var rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        var expiresAt = Instant.now().plus(ttl);
        repository.save(new RefreshToken(user, sha256Hex(rawValue), expiresAt));
        return new IssuedToken(rawValue, expiresAt);
    }

    @Transactional
    public User consume(String rawValue) {
        var token = repository.findByTokenHash(sha256Hex(rawValue))
                .orElseThrow(RefreshTokenService::invalid);

        if (!token.isUsable(Instant.now())) {
            throw invalid();
        }
        token.setRevokedAt(Instant.now());

        var user = token.getUser();
        user.getUsername();
        return user;
    }

    @Transactional
    public void revoke(String rawValue) {
        repository.findByTokenHash(sha256Hex(rawValue))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional
    public void revokeAllForUser(java.util.UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    private static BusinessException invalid() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Refresh token inválido ou expirado");
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", ex);
        }
    }
}
