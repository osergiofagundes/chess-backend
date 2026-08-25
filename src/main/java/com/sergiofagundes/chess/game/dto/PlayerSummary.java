package com.sergiofagundes.chess.game.dto;

import java.util.UUID;

import com.sergiofagundes.chess.user.User;

public record PlayerSummary(UUID id, String username) {

    public static PlayerSummary from(User user) {
        return user == null ? null : new PlayerSummary(user.getId(), user.getUsername());
    }
}
