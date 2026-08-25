package com.sergiofagundes.chess.game;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sergiofagundes.chess.auth.security.AuthenticatedUser;
import com.sergiofagundes.chess.game.dto.CreateGameRequest;
import com.sergiofagundes.chess.game.dto.GameStateResponse;
import com.sergiofagundes.chess.game.dto.JoinGameRequest;
import com.sergiofagundes.chess.game.dto.events.GameStartedEvent;
import com.sergiofagundes.chess.game.service.GameClockCoordinator;
import com.sergiofagundes.chess.game.service.GameEventPublisher;
import com.sergiofagundes.chess.game.service.GameService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/games")
public class GameController {

    private final GameService gameService;
    private final GameEventPublisher publisher;
    private final GameClockCoordinator clock;

    GameController(GameService gameService,
                   GameEventPublisher publisher,
                   GameClockCoordinator clock) {
        this.gameService = gameService;
        this.publisher = publisher;
        this.clock = clock;
    }

    @PostMapping
    ResponseEntity<GameStateResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateGameRequest request) {

        var game = gameService.create(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(game);
    }

    @PostMapping("/join")
    GameStateResponse join(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody JoinGameRequest request) {

        var game = gameService.joinByCode(principal.id(), request.code());

        publisher.broadcast(game.id(), List.of(
                new GameStartedEvent(game.whitePlayer(), game.blackPlayer())));

        clock.arm(game.id());

        return game;
    }

    @GetMapping("/{gameId}")
    GameStateResponse get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID gameId) {

        return gameService.get(principal.id(), gameId);
    }

    @DeleteMapping("/{gameId}")
    ResponseEntity<Void> cancel(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID gameId) {

        gameService.cancel(principal.id(), gameId);
        return ResponseEntity.noContent().build();
    }
}
