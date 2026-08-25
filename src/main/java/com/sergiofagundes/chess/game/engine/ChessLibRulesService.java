package com.sergiofagundes.chess.game.engine;

import java.util.List;

import org.springframework.stereotype.Service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import com.sergiofagundes.chess.game.GameResult;
import com.sergiofagundes.chess.game.PieceColor;
import com.sergiofagundes.chess.game.Termination;

@Service
public class ChessLibRulesService implements ChessRulesService {

    @Override
    public PositionInfo describe(List<String> uciHistory) {
        var board = new Board();
        replay(board, new MoveList(), uciHistory);

        return new PositionInfo(
                board.getFen(),
                colorOf(board.getSideToMove()),
                board.isKingAttacked(),
                outcomeOf(board));
    }

    @Override
    public MoveResult applyMove(List<String> uciHistory, String from, String to, String promotion) {
        var board = new Board();
        var moveList = new MoveList();
        replay(board, moveList, uciHistory);

        var uci = toUci(from, to, promotion);
        var move = parse(uci, board.getSideToMove());

        if (!board.legalMoves().contains(move)) {
            throw new IllegalMoveException("Lance ilegal na posicao atual: " + uci);
        }

        board.doMove(move);
        moveList.add(move);

        var sanMoves = moveList.toSanArray();

        return new MoveResult(
                uci,
                sanMoves[sanMoves.length - 1],
                board.getFen(),
                colorOf(board.getSideToMove()),
                board.isKingAttacked(),
                outcomeOf(board));
    }

    private static Outcome outcomeOf(Board board) {
        if (board.isMated()) {
            var winner = board.getSideToMove() == Side.WHITE
                    ? GameResult.BLACK_WIN
                    : GameResult.WHITE_WIN;
            return new Outcome(winner, Termination.CHECKMATE);
        }
        if (board.isStaleMate()) {
            return new Outcome(GameResult.DRAW, Termination.STALEMATE);
        }
        if (board.isInsufficientMaterial()) {
            return new Outcome(GameResult.DRAW, Termination.INSUFFICIENT_MATERIAL);
        }
        if (board.isRepetition()) {
            return new Outcome(GameResult.DRAW, Termination.THREEFOLD_REPETITION);
        }
        if (board.getHalfMoveCounter() >= 100) {
            return new Outcome(GameResult.DRAW, Termination.FIFTY_MOVE);
        }
        return null;
    }

    private static void replay(Board board, MoveList moveList, List<String> uciHistory) {
        for (var uci : uciHistory) {
            var move = new Move(uci, board.getSideToMove());
            board.doMove(move);
            moveList.add(move);
        }
    }

    private static Move parse(String uci, Side side) {
        try {
            return new Move(uci, side);
        } catch (RuntimeException ex) {
            throw new IllegalMoveException("Lance malformado: " + uci);
        }
    }

    private static String toUci(String from, String to, String promotion) {
        var normalized = from.toLowerCase() + to.toLowerCase();
        return promotion == null || promotion.isBlank()
                ? normalized
                : normalized + promotion.toLowerCase();
    }

    private static PieceColor colorOf(Side side) {
        return side == Side.WHITE ? PieceColor.WHITE : PieceColor.BLACK;
    }
}
