package com.sergiofagundes.chess.game.dto.events;

import com.sergiofagundes.chess.game.dto.PlayerSummary;

public record GameStartedEvent(
        PlayerSummary whitePlayer,
        PlayerSummary blackPlayer) implements GameEvent {
}
