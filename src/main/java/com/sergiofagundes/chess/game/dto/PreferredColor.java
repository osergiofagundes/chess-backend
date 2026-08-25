package com.sergiofagundes.chess.game.dto;

import java.security.SecureRandom;

import com.sergiofagundes.chess.game.PieceColor;

public enum PreferredColor {
    WHITE,
    BLACK,
    RANDOM;

    private static final SecureRandom RANDOM_SOURCE = new SecureRandom();

    public PieceColor resolve() {
        return switch (this) {
            case WHITE -> PieceColor.WHITE;
            case BLACK -> PieceColor.BLACK;
            case RANDOM -> RANDOM_SOURCE.nextBoolean() ? PieceColor.WHITE : PieceColor.BLACK;
        };
    }
}
