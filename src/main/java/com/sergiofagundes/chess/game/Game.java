package com.sergiofagundes.chess.game;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.sergiofagundes.chess.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Game {

    public static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "join_code", nullable = false, length = 8, updatable = false)
    private String joinCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_player_id")
    private User whitePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_player_id")
    private User blackPlayer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private GameResult result;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Termination termination;

    @Column(name = "current_fen", nullable = false)
    private String currentFen;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "initial_time_seconds", nullable = false, updatable = false)
    private int initialTimeSeconds;

    @Column(name = "increment_seconds", nullable = false, updatable = false)
    private int incrementSeconds;

    @Column(name = "white_time_left_ms", nullable = false)
    private long whiteTimeLeftMs;

    @Column(name = "black_time_left_ms", nullable = false)
    private long blackTimeLeftMs;

    @Column(name = "last_move_at")
    private Instant lastMoveAt;

    public Game(String joinCode, User creator, PieceColor creatorColor,
                int initialTimeSeconds, int incrementSeconds) {
        this.joinCode = joinCode;
        this.status = GameStatus.WAITING;
        this.currentFen = INITIAL_FEN;
        this.initialTimeSeconds = initialTimeSeconds;
        this.incrementSeconds = incrementSeconds;
        this.whiteTimeLeftMs = initialTimeSeconds * 1000L;
        this.blackTimeLeftMs = initialTimeSeconds * 1000L;
        assignPlayer(creator, creatorColor);
    }

    public long timeLeftMs(PieceColor color) {
        return color == PieceColor.WHITE ? whiteTimeLeftMs : blackTimeLeftMs;
    }

    public void setTimeLeftMs(PieceColor color, long millis) {
        if (color == PieceColor.WHITE) {
            this.whiteTimeLeftMs = millis;
        } else {
            this.blackTimeLeftMs = millis;
        }
    }

    private void assignPlayer(User user, PieceColor color) {
        if (color == PieceColor.WHITE) {
            this.whitePlayer = user;
        } else {
            this.blackPlayer = user;
        }
    }

    public PieceColor openSeat() {
        if (whitePlayer == null) {
            return PieceColor.WHITE;
        }
        if (blackPlayer == null) {
            return PieceColor.BLACK;
        }
        return null;
    }

    public void join(User opponent) {
        var seat = openSeat();
        if (seat == null) {
            throw new IllegalStateException("Partida ja tem dois jogadores");
        }
        assignPlayer(opponent, seat);
        this.status = GameStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
        this.lastMoveAt = this.startedAt;
    }

    public void finish(GameResult result, Termination termination) {
        this.status = GameStatus.FINISHED;
        this.result = result;
        this.termination = termination;
        this.endedAt = Instant.now();
    }

    public boolean hasPlayer(UUID userId) {
        return matches(whitePlayer, userId) || matches(blackPlayer, userId);
    }

    public PieceColor colorOf(UUID userId) {
        if (matches(whitePlayer, userId)) {
            return PieceColor.WHITE;
        }
        if (matches(blackPlayer, userId)) {
            return PieceColor.BLACK;
        }
        return null;
    }

    private static boolean matches(User player, UUID userId) {
        return player != null && player.getId().equals(userId);
    }
}
