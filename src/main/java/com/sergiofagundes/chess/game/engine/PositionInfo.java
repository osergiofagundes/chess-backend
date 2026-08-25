package com.sergiofagundes.chess.game.engine;

import com.sergiofagundes.chess.game.PieceColor;

public record PositionInfo(
        String fen,
        PieceColor sideToMove,
        boolean check,
        Outcome outcome) {
}
