package com.sergiofagundes.chess.auth.dto;

public record AuthResponse(String accessToken, long expiresInSeconds, UserResponse user) {
}
