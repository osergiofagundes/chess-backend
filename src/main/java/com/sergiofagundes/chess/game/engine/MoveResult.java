package com.sergiofagundes.chess.game.engine;

import com.sergiofagundes.chess.game.PieceColor;

public record MoveResult(
        String uci,
        String san,
        String fenAfter,
        PieceColor sideToMove,
        boolean check,
        Outcome outcome) {
}
