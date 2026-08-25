package com.sergiofagundes.chess.game.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.sergiofagundes.chess.game.Game;
import com.sergiofagundes.chess.game.GameStatus;
import com.sergiofagundes.chess.game.PieceColor;

@Service
public class ClockService {

    public record ClockSnapshot(long whiteMs, long blackMs) {}

    public long consume(Game game, PieceColor mover, Instant now) {
        var spent = elapsedSince(game.getLastMoveAt(), now);

        var remaining = game.timeLeftMs(mover) - spent + game.getIncrementSeconds() * 1000L;
        game.setTimeLeftMs(mover, Math.max(0, remaining));
        game.setLastMoveAt(now);

        return spent;
    }

    public long remainingForSideToMove(Game game, PieceColor sideToMove, Instant now) {
        return Math.max(0, game.timeLeftMs(sideToMove)
                - elapsedSince(game.getLastMoveAt(), now));
    }

    public ClockSnapshot snapshot(Game game, PieceColor sideToMove, Instant now) {
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            return new ClockSnapshot(game.getWhiteTimeLeftMs(), game.getBlackTimeLeftMs());
        }

        var elapsed = elapsedSince(game.getLastMoveAt(), now);
        var white = game.getWhiteTimeLeftMs() - (sideToMove == PieceColor.WHITE ? elapsed : 0);
        var black = game.getBlackTimeLeftMs() - (sideToMove == PieceColor.BLACK ? elapsed : 0);

        return new ClockSnapshot(Math.max(0, white), Math.max(0, black));
    }

    private static long elapsedSince(Instant reference, Instant now) {
        return reference == null ? 0 : Math.max(0, Duration.between(reference, now).toMillis());
    }
}
