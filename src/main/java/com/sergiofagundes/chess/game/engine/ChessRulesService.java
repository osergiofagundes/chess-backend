package com.sergiofagundes.chess.game.engine;

import java.util.List;

public interface ChessRulesService {

    PositionInfo describe(List<String> uciHistory);

    MoveResult applyMove(List<String> uciHistory, String from, String to, String promotion);
}
