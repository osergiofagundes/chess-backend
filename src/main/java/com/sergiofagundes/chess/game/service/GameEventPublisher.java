package com.sergiofagundes.chess.game.service;

import java.util.List;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.sergiofagundes.chess.game.dto.ErrorMessage;
import com.sergiofagundes.chess.game.dto.events.GameEvent;

@Component
public class GameEventPublisher {

    private final SimpMessagingTemplate messaging;

    GameEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void broadcast(UUID gameId, List<GameEvent> events) {
        var destination = "/topic/game/" + gameId;
        for (var event : events) {
            messaging.convertAndSend(destination, event);
        }
    }

    public void sendError(String principalName, ErrorMessage error) {
        messaging.convertAndSendToUser(principalName, "/queue/errors", error);
    }
}
