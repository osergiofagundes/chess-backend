package com.sergiofagundes.chess.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinGameRequest(@NotBlank @Size(max = 8) String code) {

    public JoinGameRequest {
        if (code != null) {
            code = code.trim().toUpperCase();
        }
    }
}
