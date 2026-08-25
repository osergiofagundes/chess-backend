package com.sergiofagundes.chess.game.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.move.Move;
import com.sergiofagundes.chess.game.GameResult;
import com.sergiofagundes.chess.game.Termination;

/**
 * A regra dos 50 lances nao cabe numa sequencia escrita a mao: sao 100 meios-lances
 * sem captura e sem peao, e sem repetir posicao (senao a triplice repeticao dispara
 * antes). O historico e construido aqui com a propria chesslib -- codigo de teste
 * pode conhecer a biblioteca; o servico sob teste continua recebendo apenas UCI.
 */
class FiftyMoveRuleTest {

    private final ChessRulesService rules = new ChessLibRulesService();

    @Test
    @DisplayName("declara empate apos 100 meios-lances sem captura nem lance de peao")
    void declaraEmpatePorRegraDos50Lances() {
        var historico = construirHistoricoQuieto(100);

        var ultimo = historico.getLast();
        var anteriores = historico.subList(0, historico.size() - 1);

        var result = rules.applyMove(
                anteriores,
                ultimo.substring(0, 2),
                ultimo.substring(2, 4),
                null);

        assertThat(result.outcome())
                .isEqualTo(new Outcome(GameResult.DRAW, Termination.FIFTY_MOVE));
    }

    @Test
    @DisplayName("nao declara empate um meio-lance antes de completar a regra")
    void naoDeclaraEmpateAntesDaHora() {
        var historico = construirHistoricoQuieto(99);

        var info = rules.describe(historico);

        assertThat(info.outcome()).isNull();
    }

    /**
     * Encontra {@code plies} lances quietos sem repetir posicao. Sem lances de
     * peao apenas os cavalos se movem, e escolher sempre o primeiro candidato
     * inedito leva a um beco sem saida -- por isso a busca faz backtracking.
     */
    private static List<String> construirHistoricoQuieto(int plies) {
        var board = new Board();
        var historico = new ArrayList<String>();
        Set<String> vistas = new HashSet<>();
        vistas.add(posicaoDe(board));

        if (!buscar(board, historico, vistas, plies)) {
            throw new IllegalStateException("Nenhuma sequencia quieta de " + plies + " meios-lances");
        }
        return historico;
    }

    private static boolean buscar(Board board, List<String> historico, Set<String> vistas, int alvo) {
        if (historico.size() == alvo) {
            return true;
        }
        for (var candidato : board.legalMoves()) {
            if (!ehQuieto(board, candidato)) {
                continue;
            }
            board.doMove(candidato);
            var posicao = posicaoDe(board);

            if (vistas.add(posicao)) {
                historico.add(candidato.toString());
                if (buscar(board, historico, vistas, alvo)) {
                    return true;
                }
                historico.removeLast();
                vistas.remove(posicao);
            }
            board.undoMove();
        }
        return false;
    }

    /** Quieto = nao captura e nao move peao, ou seja, nao zera o contador. */
    private static boolean ehQuieto(Board board, Move move) {
        return board.getPiece(move.getTo()) == Piece.NONE
                && board.getPiece(move.getFrom()).getPieceType() != PieceType.PAWN;
    }

    /** Identidade da posicao para efeito de repeticao: FEN sem os contadores. */
    private static String posicaoDe(Board board) {
        return board.getFen(false);
    }
}
