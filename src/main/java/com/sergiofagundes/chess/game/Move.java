package com.sergiofagundes.chess.game;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "moves")
@Getter
@Setter
@NoArgsConstructor
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false)
    private int ply;

    @Column(nullable = false, length = 5)
    private String uci;

    @Column(nullable = false, length = 10)
    private String san;

    @Column(name = "fen_after", nullable = false)
    private String fenAfter;

    @Column(name = "time_spent_ms")
    private Integer timeSpentMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Move(Game game, int ply, String uci, String san, String fenAfter) {
        this.game = game;
        this.ply = ply;
        this.uci = uci;
        this.san = san;
        this.fenAfter = fenAfter;
    }
}
