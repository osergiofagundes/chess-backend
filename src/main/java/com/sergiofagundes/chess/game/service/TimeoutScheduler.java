package com.sergiofagundes.chess.game.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class TimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimeoutScheduler.class);

    private static final Duration GRACE = Duration.ofMillis(250);

    private final TaskScheduler taskScheduler;
    private final Map<UUID, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    TimeoutScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public void schedule(UUID gameId, long remainingMs, Runnable onExpire) {
        cancel(gameId);

        var firesAt = Instant.now().plusMillis(remainingMs).plus(GRACE);
        var future = taskScheduler.schedule(() -> {
            scheduled.remove(gameId);
            try {
                onExpire.run();
            } catch (RuntimeException ex) {
                log.error("Falha ao processar o fim do tempo da partida {}", gameId, ex);
            }
        }, firesAt);

        scheduled.put(gameId, future);
    }

    public void cancel(UUID gameId) {
        var previous = scheduled.remove(gameId);
        if (previous != null) {
            previous.cancel(false);
        }
    }
}
