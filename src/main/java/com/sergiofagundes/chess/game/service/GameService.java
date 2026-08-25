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
import com.sergiofagundes.chess.game.GameStatus;
import com.sergiofagundes.chess.game.dto.CreateGameRequest;
import com.sergiofagundes.chess.game.dto.GameStateResponse;
import com.sergiofagundes.chess.game.engine.ChessRulesService;
import com.sergiofagundes.chess.game.repository.GameRepository;
import com.sergiofagundes.chess.game.repository.MoveRepository;
import com.sergiofagundes.chess.user.User;
import com.sergiofagundes.chess.user.UserRepository;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private final MoveRepository moveRepository;
    private final UserRepository userRepository;
    private final JoinCodeGenerator joinCodeGenerator;
    private final ChessRulesService rules;
    private final ClockService clock;

    GameService(GameRepository gameRepository,
                MoveRepository moveRepository,
                UserRepository userRepository,
                JoinCodeGenerator joinCodeGenerator,
                ChessRulesService rules,
                ClockService clock) {
        this.gameRepository = gameRepository;
        this.moveRepository = moveRepository;
        this.userRepository = userRepository;
        this.joinCodeGenerator = joinCodeGenerator;
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional
    public GameStateResponse create(UUID userId, CreateGameRequest request) {
        var creator = loadUser(userId);
        var game = new Game(
                joinCodeGenerator.generate(),
                creator,
                request.preferredColor().resolve(),
                request.timeControl().initialSeconds(),
                request.timeControl().incrementSeconds());

        return state(gameRepository.save(game), userId);
    }

    @Transactional
    public GameStateResponse joinByCode(UUID userId, String code) {
        var game = gameRepository.findByJoinCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "INVALID_JOIN_CODE", "Nao encontramos uma partida com este codigo"));

        if (game.getStatus() != GameStatus.WAITING) {
            throw new BusinessException(HttpStatus.CONFLICT, "GAME_NOT_WAITING",
                    "Esta partida nao esta mais aceitando jogadores");
        }
        if (game.hasPlayer(userId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_JOIN_OWN_GAME",
                    "Voce ja esta nesta partida");
        }

        game.join(loadUser(userId));
        return state(game, userId);
    }

    @Transactional(readOnly = true)
    public GameStateResponse get(UUID userId, UUID gameId) {
        return state(loadPlayableGame(userId, gameId), userId);
    }

    @Transactional
    public void cancel(UUID userId, UUID gameId) {
        var game = loadPlayableGame(userId, gameId);

        if (game.getStatus() != GameStatus.WAITING) {
            throw new BusinessException(HttpStatus.CONFLICT, "GAME_ALREADY_STARTED",
                    "So da para cancelar uma partida que ainda nao comecou");
        }
        game.setStatus(GameStatus.ABORTED);
    }

    private Game loadPlayableGame(UUID userId, UUID gameId) {
        var game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "GAME_NOT_FOUND", "Partida nao encontrada"));

        if (!game.hasPlayer(userId)) {
            throw new ResourceNotFoundException("GAME_NOT_FOUND", "Partida nao encontrada");
        }
        return game;
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND", "Usuario nao encontrado"));
    }

    private GameStateResponse state(Game game, UUID viewerId) {
        List<String> history = moveRepository.findUciHistory(game.getId());
        var moves = moveRepository.findByGameIdOrderByPlyAsc(game.getId());

        var position = rules.describe(history);
        var snapshot = clock.snapshot(game, position.sideToMove(), Instant.now());

        return GameStateResponse.of(game, viewerId, moves, position, snapshot);
    }
}
