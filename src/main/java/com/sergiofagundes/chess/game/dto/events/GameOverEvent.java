package com.sergiofagundes.chess.game.dto.events;

import com.sergiofagundes.chess.game.GameResult;
import com.sergiofagundes.chess.game.Termination;

public record GameOverEvent(
        GameResult result,
        Termination termination) implements GameEvent {
}
