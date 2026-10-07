package edu.ksu.redchess;
import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.*;



/**
 * Boundary between the team's controller and ChessLib's rules/state implementation. Owns the live
 * board and the display history; the UI never receives the live Board reference. Use from the
 * JavaFX application thread only. This class is not synchronized/thread-safe.
 *
 * <p>Human and AI moves both enter play, so neither can bypass legal-move validation. ChessLib
 * handles turn changes, captures, castling rights, en passant, promotion, move counters, and
 * repetition tracking when doMove succeeds.
 */
public final class GameSession {
  // A new ChessLib Board starts in the standard initial position, with White to move.
  private final Board board = new Board();
  // Team display history: one committed move per entry, independent of search's temporary moves.
  private final List<Move> history = new ArrayList<>();

  /** Reads a square for rendering; Piece.NONE means empty. */
  public Piece piece(Square square) {
    return board.getPiece(square);
  }

  /** Reads whose turn it is; ChessLib changes this after a successful move. */
  public Side turn() {
    return board.getSideToMove();
  }

  /** Returns an unmodifiable list copy of all legal moves, including special moves. */
  public List<Move> legalMoves() {
    return List.copyOf(board.legalMoves());
  }

  /** Returns an unmodifiable list copy; callers cannot add/remove display-history entries. */
  public List<Move> history() {
    return List.copyOf(history);
  }

  /**
   * Copies the position AND repetition history for private AI analysis. A FEN-only reconstruction
   * would lose repetition history; use ChessLib clone here.
   */
  public Board snapshot() {
    return board.clone();
  }

  /** Automatically ends games under ChessLib draw detection; draw-claim UI is not implemented. */
  public boolean finished() {
    return board.isMated() || board.isDraw();
  }

  /** Commits a legal move and records it; returns false without a commit if rejected. */
  public boolean play(Move move) {
    // 1. Reject completed games and proposals not in the current legal-move list.
    if (finished() || !legalMoves().contains(move)) return false;
    // 2. Ask ChessLib to apply the whole move, including its special-rule state updates.
    if (!board.doMove(move)) return false;
    // 3. Record only successfully committed moves. Search never calls this method.
    history.add(move);
    return true;
  }

  /** Produces a human-readable result; checkmate wins take precedence over draw messages. */
  public String status() {
    // A mated side is the side to move, so its opponent is the winner.
    if (board.isMated())
      return "Checkmate — " + (turn() == Side.WHITE ? "Black" : "White") + " wins";
    if (board.isStaleMate()) return "Draw — stalemate";
    if (board.isDraw()) return "Draw — repetition, move count, or insufficient material";
    return turn() + " to move" + (board.isKingAttacked() ? " — check" : "");
  }
}
