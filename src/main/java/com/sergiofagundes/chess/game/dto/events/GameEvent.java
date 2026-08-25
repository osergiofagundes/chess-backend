package com.sergiofagundes.chess.game.dto.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = GameStartedEvent.class, name = "GAME_STARTED"),
        @JsonSubTypes.Type(value = MoveEvent.class, name = "MOVE"),
        @JsonSubTypes.Type(value = DrawOfferedEvent.class, name = "DRAW_OFFERED"),
        @JsonSubTypes.Type(value = GameOverEvent.class, name = "GAME_OVER"),
})
public sealed interface GameEvent
        permits GameStartedEvent, MoveEvent, DrawOfferedEvent, GameOverEvent {
}
