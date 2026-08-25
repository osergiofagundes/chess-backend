package com.sergiofagundes.chess.game.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sergiofagundes.chess.common.exception.BusinessException;
import com.sergiofagundes.chess.common.exception.ResourceNotFoundException;
import com.sergiofagundes.chess.game.Game;
import com.sergiofagundes.chess.game.GameResult;
import com.sergiofagundes.chess.game.GameStatus;
import com.sergiofagundes.chess.game.Move;
import com.sergiofagundes.chess.game.PieceColor;
import com.sergiofagundes.chess.game.Termination;
import com.sergiofagundes.chess.game.dto.MoveMessage;
import com.sergiofagundes.chess.game.dto.events.DrawOfferedEvent;
import com.sergiofagundes.chess.game.dto.events.GameEvent;
import com.sergiofagundes.chess.game.dto.events.GameOverEvent;
import com.sergiofagundes.chess.game.dto.events.MoveEvent;
import com.sergiofagundes.chess.game.engine.ChessRulesService;
import com.sergiofagundes.chess.game.repository.GameRepository;
import com.sergiofagundes.chess.game.repository.MoveRepository;

@Service
public class GamePlayService {

    private final GameRepository gameRepository;
    private final MoveRepository moveRepository;
    private final ChessRulesService rules;
    private final DrawOfferRegistry drawOffers;
    private final ClockService clock;

    GamePlayService(GameRepository gameRepository,
                    MoveRepository moveRepository,
                    ChessRulesService rules,
                    DrawOfferRegistry drawOffers,
                    ClockService clock) {
        this.gameRepository = gameRepository;
        this.moveRepository = moveRepository;
        this.rules = rules;
        this.drawOffers = drawOffers;
        this.clock = clock;
    }

    @Transactional
    public List<GameEvent> applyMove(UUID userId, UUID gameId, MoveMessage message) {
        var game = lockGame(gameId);
        var color = requirePlayer(game, userId);

        var history = moveRepository.findUciHistory(gameId);

        if (rules.describe(history).sideToMove() != color) {
            throw new BusinessException(HttpStatus.CONFLICT, "NOT_YOUR_TURN", "Nao e a sua vez");
        }

        var now = Instant.now();

        if (clock.remainingForSideToMove(game, color, now) <= 0) {
            return List.of(flag(game, color));
        }

        var result = rules.applyMove(history, message.from(), message.to(), message.promotion());

        var spentMs = clock.consume(game, color, now);

        var ply = history.size() + 1;
        var move = new Move(game, ply, result.uci(), result.san(), result.fenAfter());
        move.setTimeSpentMs((int) spentMs);
        moveRepository.save(move);
        game.setCurrentFen(result.fenAfter());

        drawOffers.clear(gameId);

        var moveEvent = new MoveEvent(
                ply,
                result.uci(),
                result.san(),
                result.fenAfter(),
                result.sideToMove(),
                result.check(),
                game.getWhiteTimeLeftMs(),
                game.getBlackTimeLeftMs(),
                now);

        if (result.outcome() == null) {
            return List.of(moveEvent);
        }

        game.finish(result.outcome().result(), result.outcome().termination());
        return List.of(moveEvent, new GameOverEvent(game.getResult(), game.getTermination()));
    }

    @Transactional(readOnly = true)
    public long remainingMsForSideToMove(UUID gameId) {
        var game = gameRepository.findById(gameId).orElse(null);
        if (game == null || game.getStatus() != GameStatus.IN_PROGRESS) {
            return -1;
        }
        var sideToMove = rules.describe(moveRepository.findUciHistory(gameId)).sideToMove();
        return clock.remainingForSideToMove(game, sideToMove, Instant.now());
    }

    @Transactional
    public List<GameEvent> flagIfExpired(UUID gameId) {
        var game = gameRepository.findByIdForUpdate(gameId).orElse(null);
        if (game == null || game.getStatus() != GameStatus.IN_PROGRESS) {
            return List.of();
        }

        var sideToMove = rules.describe(moveRepository.findUciHistory(gameId)).sideToMove();
        if (clock.remainingForSideToMove(game, sideToMove, Instant.now()) > 0) {
            return List.of();
        }

        drawOffers.clear(gameId);
        return List.of(flag(game, sideToMove));
    }

    private static GameOverEvent flag(Game game, PieceColor flagged) {
        game.setTimeLeftMs(flagged, 0);
        var winner = flagged == PieceColor.WHITE ? GameResult.BLACK_WIN : GameResult.WHITE_WIN;
        game.finish(winner, Termination.TIMEOUT);

        return new GameOverEvent(game.getResult(), game.getTermination());
    }

    @Transactional
    public List<GameEvent> resign(UUID userId, UUID gameId) {
        var game = lockGame(gameId);
        var color = requirePlayer(game, userId);

        var winner = color == PieceColor.WHITE ? GameResult.BLACK_WIN : GameResult.WHITE_WIN;
        game.finish(winner, Termination.RESIGNATION);
        drawOffers.clear(gameId);

        return List.of(new GameOverEvent(game.getResult(), game.getTermination()));
    }

    @Transactional(readOnly = true)
    public List<GameEvent> offerDraw(UUID userId, UUID gameId) {
        var game = requireInProgress(loadGame(gameId));
        var color = requirePlayer(game, userId);

        drawOffers.offer(gameId, color);
        return List.of(new DrawOfferedEvent(color));
    }

    @Transactional
    public List<GameEvent> respondToDraw(UUID userId, UUID gameId, boolean accepted) {
        var game = lockGame(gameId);
        var color = requirePlayer(game, userId);

        var offeredBy = drawOffers.pending(gameId)
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "NO_DRAW_OFFER",
                        "Nao ha proposta de empate em aberto"));

        if (offeredBy == color) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_ANSWER_OWN_OFFER",
                    "A proposta de empate e sua");
        }

        drawOffers.clear(gameId);
        if (!accepted) {
            return List.of();
        }

        game.finish(GameResult.DRAW, Termination.DRAW_AGREEMENT);
        return List.of(new GameOverEvent(game.getResult(), game.getTermination()));
    }

    private Game lockGame(UUID gameId) {
        return requireInProgress(gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(GamePlayService::gameNotFound));
    }

    private Game loadGame(UUID gameId) {
        return gameRepository.findById(gameId).orElseThrow(GamePlayService::gameNotFound);
    }

    private static Game requireInProgress(Game game) {
        if (game.getStatus() != GameStatus.IN_PROGRESS) {
            throw new BusinessException(HttpStatus.CONFLICT, "GAME_NOT_IN_PROGRESS",
                    "Esta partida nao esta em andamento");
        }
        return game;
    }

    private static PieceColor requirePlayer(Game game, UUID userId) {
        var color = game.colorOf(userId);
        if (color == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "NOT_A_PLAYER",
                    "Voce nao joga esta partida");
        }
        return color;
    }

    private static ResourceNotFoundException gameNotFound() {
        return new ResourceNotFoundException("GAME_NOT_FOUND", "Partida nao encontrada");
    }
}
