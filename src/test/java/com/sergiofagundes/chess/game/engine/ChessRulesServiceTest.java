package com.sergiofagundes.chess.game.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sergiofagundes.chess.game.GameResult;
import com.sergiofagundes.chess.game.PieceColor;
import com.sergiofagundes.chess.game.Termination;

class ChessRulesServiceTest {

    private final ChessRulesService rules = new ChessLibRulesService();

    @Test
    @DisplayName("aplica 1.e4 devolvendo UCI, SAN, FEN e a vez das pretas")
    void aplicaPrimeiroLance() {
        var result = rules.applyMove(List.of(), "e2", "e4", null);

        assertThat(result.uci()).isEqualTo("e2e4");
        assertThat(result.san()).isEqualTo("e4");
        assertThat(result.sideToMove()).isEqualTo(PieceColor.BLACK);
        assertThat(result.check()).isFalse();
        assertThat(result.outcome()).isNull();
        assertThat(result.fenAfter())
                .startsWith("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b");
    }

    // --- lances ilegais ------------------------------------------------------

    @Test
    @DisplayName("recusa lance geometricamente impossivel")
    void recusaLanceImpossivel() {
        assertThatExceptionOfType(IllegalMoveException.class)
                .isThrownBy(() -> rules.applyMove(List.of(), "e2", "e5", null));
    }

    @Test
    @DisplayName("recusa mover peca do adversario")
    void recusaMoverPecaDoAdversario() {
        // Vez das brancas, mas o lance move um peao preto.
        assertThatExceptionOfType(IllegalMoveException.class)
                .isThrownBy(() -> rules.applyMove(List.of(), "e7", "e5", null));
    }

    @Test
    @DisplayName("recusa lance que ignora um xeque")
    void recusaLanceQueIgnoraXeque() {
        // 1.e4 d5 2.Bb5+ -- o bispo mira e8 pela diagonal b5-c6-d7, que ficou
        // aberta quando o peao saiu de d7. As pretas precisam cobrir ou fugir.
        var historico = List.of("e2e4", "d7d5", "f1b5");

        // Na6 nao cobre a diagonal nem move o rei.
        assertThatExceptionOfType(IllegalMoveException.class)
                .isThrownBy(() -> rules.applyMove(historico, "b8", "a6", null));
    }

    @Test
    @DisplayName("recusa lance de peca sob cravada absoluta")
    void recusaLanceDePecaCravada() {
        // Defesa Steinitz: 1.e4 e5 2.Nf3 Nc6 3.Bb5 d6 4.O-O.
        // Com d7 vazio, o cavalo de c6 e a unica peca entre o bispo de b5 e o
        // rei em e8 -- ele esta cravado e nao pode se mexer.
        var historico = List.of("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "d7d6", "e1g1");

        assertThatExceptionOfType(IllegalMoveException.class)
                .isThrownBy(() -> rules.applyMove(historico, "c6", "d4", null));
    }

    // --- fim de partida ------------------------------------------------------

    @Test
    @DisplayName("detecta xeque-mate do pastor e aponta o vencedor")
    void detectaXequeMate() {
        // Mate do bobo: 1.f3 e5 2.g4 Qh4#
        var historico = List.of("f2f3", "e7e5", "g2g4");

        var result = rules.applyMove(historico, "d8", "h4", null);

        assertThat(result.san()).isEqualTo("Qh4#");
        assertThat(result.check()).isTrue();
        assertThat(result.outcome())
                .isEqualTo(new Outcome(GameResult.BLACK_WIN, Termination.CHECKMATE));
    }

    @Test
    @DisplayName("xeque simples nao encerra a partida")
    void xequeSimplesNaoEncerra() {
        var result = rules.applyMove(List.of("e2e4", "d7d5"), "f1", "b5", null);

        assertThat(result.san()).isEqualTo("Bb5+");
        assertThat(result.check()).isTrue();
        assertThat(result.outcome()).isNull();
    }

    @Test
    @DisplayName("detecta afogamento como empate")
    void detectaAfogamento() {
        // Afogamento mais rapido conhecido, em 10 lances.
        var historico = List.of(
                "c2c4", "h7h5",
                "h2h4", "a7a5",
                "d1a4", "a8a6",
                "a4a5", "a6h6",
                "a5c7", "f7f6",
                "c7d7", "e8f7",
                "d7b7", "d8d3",
                "b7b8", "d3h7",
                "b8c8", "f7g6");

        var result = rules.applyMove(historico, "c8", "e6", null);

        assertThat(result.outcome())
                .isEqualTo(new Outcome(GameResult.DRAW, Termination.STALEMATE));
    }

    @Test
    @DisplayName("detecta triplice repeticao apos os cavalos voltarem tres vezes")
    void detectaTriplaRepeticao() {
        // Cada ciclo de 4 meios-lances devolve a posicao inicial. A terceira
        // ocorrencia acontece ao fim do segundo ciclo.
        var historico = List.of(
                "g1f3", "g8f6", "f3g1", "f6g8",
                "g1f3", "g8f6", "f3g1");

        var result = rules.applyMove(historico, "f6", "g8", null);

        assertThat(result.outcome())
                .isEqualTo(new Outcome(GameResult.DRAW, Termination.THREEFOLD_REPETITION));
    }

    // --- lances especiais ----------------------------------------------------

    @Test
    @DisplayName("roque curto vira O-O e move a torre junto")
    void roqueCurto() {
        var historico = List.of("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5");

        var result = rules.applyMove(historico, "e1", "g1", null);

        assertThat(result.san()).isEqualTo("O-O");
        // Rei em g1 e torre em f1: a torre acompanhou o rei.
        assertThat(result.fenAfter()).startsWith("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQ1RK1");
    }

    @Test
    @DisplayName("roque longo vira O-O-O")
    void roqueLongo() {
        var historico = List.of(
                "d2d4", "d7d5",
                "b1c3", "b8c6",
                "c1f4", "c8f5",
                "d1d2", "d8d7");

        var result = rules.applyMove(historico, "e1", "c1", null);

        assertThat(result.san()).isEqualTo("O-O-O");
    }

    @Test
    @DisplayName("captura en passant remove o peao que passou")
    void capturaEnPassant() {
        // 1.e4 d5 2.e5 f5 -- o peao preto passou por f6, entao 3.exf6 e.p.
        var historico = List.of("e2e4", "d7d5", "e4e5", "f7f5");

        var result = rules.applyMove(historico, "e5", "f6", null);

        assertThat(result.san()).isEqualTo("exf6");
        // O peao capturado estava em f5, e nao em f6: a casa f5 tem de ficar vazia.
        assertThat(result.fenAfter()).startsWith("rnbqkbnr/ppp1p1pp/5P2/3p4/8/8/PPPP1PPP/RNBQKBNR");
    }

    @Test
    @DisplayName("promocao a dama vira gxh8=Q")
    void promocaoADama() {
        var result = rules.applyMove(HISTORICO_ATE_PROMOCAO, "g7", "h8", "q");

        assertThat(result.san()).isEqualTo("gxh8=Q");
        assertThat(result.fenAfter()).startsWith("rnbqkb1Q/pppp3p");
    }

    @Test
    @DisplayName("subpromocao a cavalo vira gxh8=N")
    void subpromocaoACavalo() {
        var result = rules.applyMove(HISTORICO_ATE_PROMOCAO, "g7", "h8", "n");

        assertThat(result.san()).isEqualTo("gxh8=N");
    }

    @Test
    @DisplayName("recusa promocao sem informar a peca")
    void recusaPromocaoSemPeca() {
        assertThatExceptionOfType(IllegalMoveException.class)
                .isThrownBy(() -> rules.applyMove(HISTORICO_ATE_PROMOCAO, "g7", "h8", null));
    }

    @Test
    @DisplayName("recusa lance malformado sem estourar tipo da biblioteca")
    void recusaLanceMalformado() {
        assertThatExceptionOfType(IllegalMoveException.class)
                .isThrownBy(() -> rules.applyMove(List.of(), "z9", "e4", null));
    }

    // --- descrever a posicao sem aplicar lance -------------------------------

    @Test
    @DisplayName("descreve a posicao inicial a partir de historico vazio")
    void descrevePosicaoInicial() {
        var info = rules.describe(List.of());

        assertThat(info.fen())
                .startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq");
        assertThat(info.sideToMove()).isEqualTo(PieceColor.WHITE);
        assertThat(info.check()).isFalse();
        assertThat(info.outcome()).isNull();
    }

    @Test
    @DisplayName("descreve de quem e a vez no meio da partida")
    void descreveVezNoMeioDaPartida() {
        var info = rules.describe(List.of("e2e4", "e7e5", "g1f3"));

        assertThat(info.sideToMove()).isEqualTo(PieceColor.BLACK);
        assertThat(info.outcome()).isNull();
    }

    @Test
    @DisplayName("descreve historico que ja terminou em mate")
    void descreveHistoricoTerminado() {
        var info = rules.describe(List.of("f2f3", "e7e5", "g2g4", "d8h4"));

        assertThat(info.check()).isTrue();
        assertThat(info.outcome())
                .isEqualTo(new Outcome(GameResult.BLACK_WIN, Termination.CHECKMATE));
    }

    /** 1.d4 e5 2.dxe5 f6 3.exf6 Ne7 4.fxg7 Nf5 -- peao branco pronto em g7. */
    private static final List<String> HISTORICO_ATE_PROMOCAO = List.of(
            "d2d4", "e7e5",
            "d4e5", "f7f6",
            "e5f6", "g8e7",
            "f6g7", "e7f5");
}
