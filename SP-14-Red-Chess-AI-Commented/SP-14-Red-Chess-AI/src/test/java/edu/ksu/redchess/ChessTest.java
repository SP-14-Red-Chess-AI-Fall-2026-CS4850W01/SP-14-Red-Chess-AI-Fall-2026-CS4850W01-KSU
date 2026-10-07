package edu.ksu.redchess;
import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;




/**
 * Regression tests for the team/ChessLib boundary and AI search, not GUI automation. Each test uses
 * a fresh board/session so earlier tests cannot affect later ones. FEN strings encode ranks 8..1,
 * side to move, castling, en passant, and move counters.
 */
class ChessTest {
  /**
   * Try an impossible three-square pawn advance; assert both position and history remain unchanged.
   */
  @Test
  void rejectsIllegalMoveWithoutChangingState() {
    GameSession game = new GameSession();
    String fen = game.snapshot().getFen();
    assertFalse(game.play(new Move(Square.E2, Square.E5)));
    assertEquals(fen, game.snapshot().getFen());
    assertTrue(game.history().isEmpty());
  }

  /**
   * Move on an AI-style clone; verify the live pawn stays put and the history list rejects
   * additions.
   */
  @Test
  void snapshotIsIndependentAndHistoryIsReadOnly() {
    GameSession game = new GameSession();
    Board copy = game.snapshot();
    copy.doMove(new Move(Square.E2, Square.E4));
    assertEquals(Piece.WHITE_PAWN, game.piece(Square.E2));
    assertThrows(
        UnsupportedOperationException.class,
        () -> game.history().add(new Move(Square.E2, Square.E4)));
  }

  /** Replay Fool's Mate; verify Black wins and the completed session rejects further moves. */
  @Test
  void foolsMateEndsSession() {
    GameSession game = new GameSession();
    for (String move : new String[] {"f2f3", "e7e5", "g2g4", "d8h4"})
      assertTrue(game.play(new Move(move, game.turn())));
    assertTrue(game.finished());
    assertTrue(game.status().contains("Black wins"));
    assertFalse(game.play(new Move(Square.E2, Square.E4)));
  }

  /**
   * Use a mate-in-one position; verify search preserves its input, then verify the selected move
   * mates.
   */
  @Test
  void aiFindsMateAndRestoresSearchBoard() {
    Board board = new Board();
    board.loadFromFen("7k/5Q2/6K1/8/8/8/8/8 w - - 0 1");
    String before = board.getFen();
    Move move = new MinimaxEngine().choose(board, 2);
    assertEquals(before, board.getFen());
    assertTrue(board.legalMoves().contains(move));
    board.doMove(move);
    assertTrue(board.isMated());
  }

  /**
   * Exercise ChessLib contracts for castling rook movement, en-passant removal, and four
   * promotions.
   */
  @Test
  void specialMoveContracts() {
    Board board = new Board();
    board.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
    assertTrue(board.legalMoves().contains(new Move(Square.E1, Square.G1)));
    board.doMove(new Move(Square.E1, Square.G1));
    assertEquals(Piece.WHITE_ROOK, board.getPiece(Square.F1));
    // En passant: White captures the just-advanced d5 pawn by moving e5 to d6.
    board.loadFromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
    assertTrue(board.legalMoves().contains(new Move(Square.E5, Square.D6)));
    board.doMove(new Move(Square.E5, Square.D6));
    assertEquals(Piece.NONE, board.getPiece(Square.D5));
    // Promotion: a7-a8 must offer queen, rook, bishop, and knight variants.
    board.loadFromFen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");
    assertEquals(4, board.legalMoves().stream().filter(m -> m.getFrom() == Square.A7).count());
  }

  /**
   * Search the initial position; verify a legal result and no temporary search moves left on the
   * board.
   */
  @Test
  void aiReturnsLegalOpeningMove() {
    Board board = new Board();
    String fen = board.getFen();
    assertTrue(board.legalMoves().contains(new MinimaxEngine().choose(board, 2)));
    assertEquals(fen, board.getFen());
  }
}
