package com.sergiofagundes.chess.game.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class GameClockCoordinator {

    private final GamePlayService playService;
    private final GameEventPublisher publisher;
    private final TimeoutScheduler scheduler;

    GameClockCoordinator(GamePlayService playService,
                         GameEventPublisher publisher,
                         TimeoutScheduler scheduler) {
        this.playService = playService;
        this.publisher = publisher;
        this.scheduler = scheduler;
    }

    public void arm(UUID gameId) {
        var remaining = playService.remainingMsForSideToMove(gameId);
        if (remaining < 0) {
            scheduler.cancel(gameId);
            return;
        }
        scheduler.schedule(gameId, remaining, () -> onTimeout(gameId));
    }

    public void disarm(UUID gameId) {
        scheduler.cancel(gameId);
    }

    private void onTimeout(UUID gameId) {
        var events = playService.flagIfExpired(gameId);

        if (events.isEmpty()) {
            arm(gameId);
            return;
        }
        publisher.broadcast(gameId, events);
    }
}
