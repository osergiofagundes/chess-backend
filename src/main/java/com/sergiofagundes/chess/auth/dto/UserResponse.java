package com.sergiofagundes.chess.auth.dto;

import java.util.UUID;

import com.sergiofagundes.chess.user.User;

public record UserResponse(UUID id, String username, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail());
    }
}
