package com.sergiofagundes.chess.game;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.sergiofagundes.chess.auth.security.AuthenticatedUser;
import com.sergiofagundes.chess.common.exception.BusinessException;
import com.sergiofagundes.chess.game.dto.DrawResponseMessage;
import com.sergiofagundes.chess.game.dto.ErrorMessage;
import com.sergiofagundes.chess.game.dto.MoveMessage;
import com.sergiofagundes.chess.game.dto.events.GameEvent;
import com.sergiofagundes.chess.game.dto.events.GameOverEvent;
import com.sergiofagundes.chess.game.engine.IllegalMoveException;
import com.sergiofagundes.chess.game.service.GameClockCoordinator;
import com.sergiofagundes.chess.game.service.GameEventPublisher;
import com.sergiofagundes.chess.game.service.GamePlayService;

import jakarta.validation.Valid;

@Controller
public class GameWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketController.class);

    private final GamePlayService playService;
    private final GameEventPublisher publisher;
    private final GameClockCoordinator clock;

    GameWebSocketController(GamePlayService playService,
                            GameEventPublisher publisher,
                            GameClockCoordinator clock) {
        this.playService = playService;
        this.publisher = publisher;
        this.clock = clock;
    }

    @MessageMapping("/game/{gameId}/move")
    public void move(@DestinationVariable UUID gameId,
                     @Valid @Payload MoveMessage message,
                     Principal principal) {

        var events = playService.applyMove(userId(principal), gameId, message);
        publisher.broadcast(gameId, events);

        rearmClock(gameId, events);
    }

    @MessageMapping("/game/{gameId}/resign")
    public void resign(@DestinationVariable UUID gameId, Principal principal) {
        publisher.broadcast(gameId, playService.resign(userId(principal), gameId));
        clock.disarm(gameId);
    }

    @MessageMapping("/game/{gameId}/draw-offer")
    public void offerDraw(@DestinationVariable UUID gameId, Principal principal) {
        publisher.broadcast(gameId, playService.offerDraw(userId(principal), gameId));
    }

    @MessageMapping("/game/{gameId}/draw-response")
    public void respondToDraw(@DestinationVariable UUID gameId,
                              @Payload DrawResponseMessage message,
                              Principal principal) {

        var events = playService.respondToDraw(userId(principal), gameId, message.accepted());
        publisher.broadcast(gameId, events);
        rearmClock(gameId, events);
    }

    private void rearmClock(UUID gameId, List<GameEvent> events) {
        var ended = events.stream().anyMatch(GameOverEvent.class::isInstance);
        if (ended) {
            clock.disarm(gameId);
        } else {
            clock.arm(gameId);
        }
    }

    @MessageExceptionHandler(IllegalMoveException.class)
    @SendToUser("/queue/errors")
    public ErrorMessage handleIllegalMove(IllegalMoveException ex) {
        return new ErrorMessage("ILLEGAL_MOVE", "Lance ilegal na posicao atual");
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ErrorMessage handleBusiness(BusinessException ex) {
        return new ErrorMessage(ex.getCode(), ex.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorMessage handleUnexpected(Exception ex) {
        log.error("Erro nao tratado em mensagem STOMP", ex);
        return new ErrorMessage("INTERNAL_ERROR", "Nao foi possivel processar a mensagem");
    }

    private static UUID userId(Principal principal) {
        if (principal instanceof AuthenticatedUser user) {
            return user.id();
        }
        throw new BusinessException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Sessao nao autenticada");
    }
}
