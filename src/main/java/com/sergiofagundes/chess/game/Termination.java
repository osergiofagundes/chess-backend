package com.sergiofagundes.chess.game;

public enum Termination {
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    THREEFOLD_REPETITION,
    FIFTY_MOVE,
    RESIGNATION,
    DRAW_AGREEMENT,
    TIMEOUT
}
