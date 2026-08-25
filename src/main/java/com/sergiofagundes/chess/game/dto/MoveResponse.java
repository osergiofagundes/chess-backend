package com.sergiofagundes.chess.game.dto;

import com.sergiofagundes.chess.game.Move;

public record MoveResponse(int ply, String uci, String san, String fenAfter) {

    public static MoveResponse from(Move move) {
        return new MoveResponse(move.getPly(), move.getUci(), move.getSan(), move.getFenAfter());
    }
}
