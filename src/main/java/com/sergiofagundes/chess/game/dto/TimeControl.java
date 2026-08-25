package com.sergiofagundes.chess.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TimeControl(
        @Min(10) @Max(10_800) int initialSeconds,
        @Min(0) @Max(60) int incrementSeconds) {
}
