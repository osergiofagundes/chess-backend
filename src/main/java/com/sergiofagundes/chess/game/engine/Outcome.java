package com.sergiofagundes.chess.game.engine;

import com.sergiofagundes.chess.game.GameResult;
import com.sergiofagundes.chess.game.Termination;

public record Outcome(GameResult result, Termination termination) {
}
