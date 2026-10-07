package edu.ksu.redchess;
import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.concurrent.CancellationException;



/**
 * ChessLib supplies legal moves and board operations, not move selection.
 *
 * <p>This is minimax written as negamax: a score is always from the side-to-move perspective. After
 * a move the opponent becomes the side to move, so negate the child's score. Alpha-beta pruning
 * skips branches that cannot improve the decision already found.
 *
 * <p>Read choose first (root decision), search next (recursive lookahead), then evaluate. The
 * caller must provide an exclusively owned board snapshot. Temporary moves are undone in finally
 * blocks, including when cancellation or an exception unwinds the recursion. Limitations:
 * material-only evaluation, fixed depth, no move ordering or time budget.
 */
public final class MinimaxEngine {
  /**
   * Selects a legal move at the requested depth, or null if the game is already terminal. depth
   * counts plies: depth 2 considers our move followed by the opponent's reply. The UI exposes 1–3;
   * this engine accepts 1–4. Equal scores keep the first generated move.
   */
  public Move choose(Board board, int depth) {
    // 1. Validate the search limit and do not search completed positions.
    if (depth < 1 || depth > 4) throw new IllegalArgumentException("Depth must be 1–4");
    if (board.isMated() || board.isDraw()) return null;
    // 2. Start below all possible evaluation scores so the first legal move becomes best.
    Move best = null;
    int score = -1000000;
    // Explore only moves ChessLib has verified as legal for the current position.
    for (Move move : board.legalMoves()) {
      checkCancelled();
      board.doMove(move);
      int value;
      // 3. Look ahead from the opponent's perspective, then negate that score.
      // -score is the opponent's cutoff bound derived from the best root move so far.
      try {
        value = -search(board, depth - 1, -1000000, -score, 1);
      }
      // Always restore the parent's position before testing another candidate or returning.
      finally {
        board.undoMove();
      }
      if (value > score) {
        score = value;
        best = move;
      }
    }
    return best;
  }

  /**
   * Returns a score/bound for the current side under the alpha-beta search window. alpha = best
   * lower bound so far; beta = cutoff bound imposed by the parent. ply = distance from root, used
   * to prefer faster wins and postpone forced losses.
   */
  private int search(Board board, int depth, int alpha, int beta, int ply) {
    // 1. Check cancellation at each node so pause/restart can interrupt long searches.
    checkCancelled();
    // 2. Terminal results take priority over the depth cutoff. Being mated is a large loss;
    // the distance adjustment makes earlier mates better for the winning player.
    if (board.isMated()) return -100000 + ply;
    if (board.isDraw()) return 0;
    // 3. At the horizon, estimate the position rather than generate another search layer.
    if (depth == 0) return evaluate(board);
    // Explore only moves ChessLib has verified as legal for the current position.
    for (Move move : board.legalMoves()) {
      board.doMove(move);
      int score;
      // 4. Swap and negate the bounds because the opponent has the opposite perspective.
      try {
        score = -search(board, depth - 1, -beta, -alpha, ply + 1);
      }
      // Always restore the parent's position before testing another candidate or returning.
      finally {
        board.undoMove();
      }
      // 5. Improve our lower bound. If it reaches beta, the parent has no reason to
      // choose this branch over an alternative already considered, so prune the rest.
      alpha = Math.max(alpha, score);
      if (alpha >= beta) break;
    }
    // A cutoff may return a bound rather than the exact score; callers use its window.
    return alpha;
  }

  /** Material balance in centipawns (100 = one pawn), from the side-to-move perspective. */
  private int evaluate(Board board) {
    int score = 0;
    for (Square square : Square.values()) {
      // NONE is an enum sentinel, not a physical square; skip it and all empty squares.
      if (square == Square.NONE) continue;
      Piece piece = board.getPiece(square);
      if (piece == Piece.NONE) continue;
      // Conventional approximate piece values; king value is handled by mate detection.
      int value =
          switch (piece.getPieceType()) {
            case PAWN -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 330;
            case ROOK -> 500;
            case QUEEN -> 900;
            default -> 0;
          };
      // Friendly material adds to the score; opposing material subtracts from it.
      score += piece.getPieceSide() == board.getSideToMove() ? value : -value;
    }
    return score;
  }

  /** Cooperates with Task.cancel(true); throwing unwinds all pending finally/undo blocks. */
  private void checkCancelled() {
    if (Thread.currentThread().isInterrupted()) throw new CancellationException();
  }
}
