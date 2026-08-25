package com.sergiofagundes.chess.game.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.sergiofagundes.chess.game.PieceColor;

@Component
public class DrawOfferRegistry {

    private final Map<UUID, PieceColor> offersByGame = new ConcurrentHashMap<>();

    public void offer(UUID gameId, PieceColor offeredBy) {
        offersByGame.put(gameId, offeredBy);
    }

    public Optional<PieceColor> pending(UUID gameId) {
        return Optional.ofNullable(offersByGame.get(gameId));
    }

    public void clear(UUID gameId) {
        offersByGame.remove(gameId);
    }
}
