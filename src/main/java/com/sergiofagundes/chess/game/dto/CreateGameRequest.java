package com.sergiofagundes.chess.game.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateGameRequest(
        @NotNull PreferredColor preferredColor,
        @NotNull @Valid TimeControl timeControl) {
}
