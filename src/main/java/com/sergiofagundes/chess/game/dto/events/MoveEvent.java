package com.sergiofagundes.chess.game.dto.events;

import java.time.Instant;

import com.sergiofagundes.chess.game.PieceColor;

public record MoveEvent(
        int ply,
        String uci,
        String san,
        String fenAfter,
        PieceColor turn,
        boolean check,
        long whiteTimeLeftMs,
        long blackTimeLeftMs,
        Instant serverTimestamp) implements GameEvent {
}
