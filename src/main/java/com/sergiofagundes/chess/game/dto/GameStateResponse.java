package com.sergiofagundes.chess.game.dto;

import java.util.List;
import java.util.UUID;

import com.sergiofagundes.chess.game.Game;
import com.sergiofagundes.chess.game.GameResult;
import com.sergiofagundes.chess.game.GameStatus;
import com.sergiofagundes.chess.game.Move;
import com.sergiofagundes.chess.game.PieceColor;
import com.sergiofagundes.chess.game.Termination;
import com.sergiofagundes.chess.game.engine.PositionInfo;
import com.sergiofagundes.chess.game.service.ClockService.ClockSnapshot;

public record GameStateResponse(
        UUID id,
        String joinCode,
        GameStatus status,
        PlayerSummary whitePlayer,
        PlayerSummary blackPlayer,
        PieceColor yourColor,
        String currentFen,
        PieceColor turn,
        boolean check,
        GameResult result,
        Termination termination,
        int initialTimeSeconds,
        int incrementSeconds,
        long whiteTimeLeftMs,
        long blackTimeLeftMs,
        List<MoveResponse> moves) {

    public static GameStateResponse of(Game game, UUID viewerId, List<Move> moves,
                                       PositionInfo position, ClockSnapshot clock) {
        return new GameStateResponse(
                game.getId(),
                game.getJoinCode(),
                game.getStatus(),
                PlayerSummary.from(game.getWhitePlayer()),
                PlayerSummary.from(game.getBlackPlayer()),
                game.colorOf(viewerId),
                game.getCurrentFen(),
                position.sideToMove(),
                position.check(),
                game.getResult(),
                game.getTermination(),
                game.getInitialTimeSeconds(),
                game.getIncrementSeconds(),
                clock.whiteMs(),
                clock.blackMs(),
                moves.stream().map(MoveResponse::from).toList());
    }
}
