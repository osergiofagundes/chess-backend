package com.sergiofagundes.chess.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sergiofagundes.chess.game.Game;
import com.sergiofagundes.chess.game.GameStatus;
import com.sergiofagundes.chess.game.PieceColor;

class ClockServiceTest {

    private static final Instant INICIO = Instant.parse("2026-08-24T12:00:00Z");

    private final ClockService clock = new ClockService();
    private Game game;

    @BeforeEach
    void setUp() {
        // 3+2, sem passar pelo banco: a entidade basta para o relogio.
        game = new Game("CODIGO", null, PieceColor.WHITE, 180, 2);
        game.setStatus(GameStatus.IN_PROGRESS);
        game.setLastMoveAt(INICIO);
    }

    @Test
    @DisplayName("consome o tempo gasto e soma o incremento")
    void consomeEIncrementa() {
        var gasto = clock.consume(game, PieceColor.WHITE, INICIO.plusSeconds(10));

        assertThat(gasto).isEqualTo(10_000);
        // 180s - 10s gastos + 2s de incremento
        assertThat(game.getWhiteTimeLeftMs()).isEqualTo(172_000);
        assertThat(game.getBlackTimeLeftMs()).isEqualTo(180_000);
    }

    @Test
    @DisplayName("marca o instante do lance para o proximo desconto")
    void atualizaOInstanteDoLance() {
        var agora = INICIO.plusSeconds(5);

        clock.consume(game, PieceColor.WHITE, agora);

        assertThat(game.getLastMoveAt()).isEqualTo(agora);
    }

    @Test
    @DisplayName("nunca deixa o tempo restante negativo")
    void naoDeixaTempoNegativo() {
        clock.consume(game, PieceColor.WHITE, INICIO.plusSeconds(600));

        assertThat(game.getWhiteTimeLeftMs()).isZero();
    }

    @Test
    @DisplayName("so o relogio de quem esta na vez corre")
    void somenteOLadoDaVezCorre() {
        var snapshot = clock.snapshot(game, PieceColor.WHITE, INICIO.plusSeconds(30));

        assertThat(snapshot.whiteMs()).isEqualTo(150_000);
        assertThat(snapshot.blackMs()).isEqualTo(180_000);
    }

    @Test
    @DisplayName("partida encerrada congela os relogios nos valores gravados")
    void partidaEncerradaCongelaORelogio() {
        game.setStatus(GameStatus.FINISHED);

        var snapshot = clock.snapshot(game, PieceColor.WHITE, INICIO.plusSeconds(999));

        assertThat(snapshot.whiteMs()).isEqualTo(180_000);
        assertThat(snapshot.blackMs()).isEqualTo(180_000);
    }

    @Test
    @DisplayName("partida em espera nao consome tempo de ninguem")
    void partidaEmEsperaNaoConsome() {
        var naoIniciada = new Game("CODIGO", null, PieceColor.WHITE, 180, 2);

        var restante = clock.remainingForSideToMove(
                naoIniciada, PieceColor.WHITE, INICIO.plusSeconds(3600));

        assertThat(restante).isEqualTo(180_000);
    }

    @Test
    @DisplayName("tempo restante chega a zero e nao passa disso")
    void tempoRestanteNaoFicaNegativo() {
        var restante = clock.remainingForSideToMove(
                game, PieceColor.WHITE, INICIO.plusSeconds(500));

        assertThat(restante).isZero();
    }
}
