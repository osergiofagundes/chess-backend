package com.sergiofagundes.chess.game.dto.events;

import com.sergiofagundes.chess.game.PieceColor;

public record DrawOfferedEvent(PieceColor offeredBy) implements GameEvent {
}
