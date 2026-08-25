package com.sergiofagundes.chess.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MoveMessage(
        @NotBlank @Pattern(regexp = "^[a-h][1-8]$") String from,
        @NotBlank @Pattern(regexp = "^[a-h][1-8]$") String to,
        @Pattern(regexp = "^[qrbn]$") String promotion) {
}
