package edu.ksu.redchess;
import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.*;
import java.util.concurrent.*;



/**
 * Application entry point and prototype controller.
 *
 * <p>Read this class in this order: start -> click/newGame -> schedule -> beginSearch -> refresh.
 * JavaFX calls start once to build the window. Human moves arrive through button callbacks; AI
 * moves arrive through Task completion callbacks. Both paths commit through GameSession.
 *
 * <p>Thread rule: fields and controls belong to the JavaFX application thread. Only Task.call runs
 * on the worker thread, and it receives a private board snapshot, never the live game. This
 * prototype keeps UI and orchestration together; MinimaxEngine owns search and GameSession owns
 * chess state. Extract a separate controller here if the UI grows.
 */
public class ChessApp extends Application {
  // Live session and the controls that display it. Array indices match ChessLib A1..H8.
  private GameSession game = new GameSession();
  private final Button[] squares = new Button[64];
  private final Label status = new Label();
  private final TextArea history = new TextArea();
  private final ComboBox<String> mode = new ComboBox<>();
  private final ComboBox<Side> humanSide = new ComboBox<>();
  private final ComboBox<Integer> depth = new ComboBox<>();
  private final Button pause = new Button("Pause");
  // One worker serializes AI searches and keeps expensive recursion off the UI thread.
  // A daemon worker does not keep the JVM alive after the application closes.
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "chess-search");
            t.setDaemon(true);
            return t;
          });
  // JavaFX schedules this 400 ms display interval without sleeping or blocking the UI.
  private final PauseTransition delay = new PauseTransition(Duration.millis(400));
  // Non-null means a search owns the current AI turn. selected is the human's source square.
  private Task<Move> search;
  private Square selected;
  // Incrementing this token invalidates every callback created before cancellation/restart.
  private long generation;
  private boolean paused;
  // These are the committed settings. Combo-box edits only apply when New Game is clicked.
  private String activeMode = "Player vs Player";
  private Side activeHumanSide = Side.WHITE;
  private int activeDepth = 2;

  /** Builds controls, connects event handlers, and renders the initial local PvP game. */
  public void start(Stage stage) {
    // 1. Populate game settings; depth counts individual moves (plies), not full turns.
    mode.getItems().addAll("Player vs Player", "Player vs AI", "AI vs AI");
    mode.setValue(activeMode);
    humanSide.getItems().addAll(Side.WHITE, Side.BLACK);
    humanSide.setValue(Side.WHITE);
    depth.getItems().addAll(1, 2, 3);
    depth.setValue(2);
    Button fresh = new Button("New Game");
    fresh.setOnAction(e -> newGame());
    // Pausing invalidates a running search; resuming schedules a fresh search of this position.
    pause.setOnAction(
        e -> {
          paused = !paused;
          cancelSearch();
          refresh();
          schedule();
        });
    // 2. Display rank 8 at the top and rank 1 at the bottom (White's perspective).
    // ChessLib uses A1=0, B1=1, ..., H8=63; grid row 0 corresponds to rank 8.
    GridPane board = new GridPane();
    for (int rank = 7; rank >= 0; rank--)
      for (int file = 0; file < 8; file++) {
        int index = rank * 8 + file;
        Button button = new Button();
        squares[index] = button;
        button.setPrefSize(68, 68);
        button.setMinSize(68, 68);
        button.setOnAction(e -> click(Square.values()[index]));
        board.add(button, file, 7 - rank);
      }
    // 3. Place the board beside configuration, status, and read-only move history.
    history.setEditable(false);
    history.setPrefWidth(220);
    VBox side =
        new VBox(
            10,
            new Label("Settings apply with New Game"),
            mode,
            new Label("Human side"),
            humanSide,
            new Label("AI search depth"),
            depth,
            fresh,
            pause,
            status,
            new Label("Move history (coordinate notation)"),
            history);
    HBox root = new HBox(16, board, side);
    root.setStyle("-fx-padding: 20; -fx-background-color: #eceff3;");
    // 4. Show the window and draw the starting position. AI starts after New Game.
    stage.setTitle("SP-14 Red — Chess with AI");
    stage.setScene(new Scene(root));
    stage.setResizable(false);
    stage.setOnCloseRequest(e -> stop());
    stage.show();
    refresh();
  }

  /** Decides who owns the current turn from the active mode and the human's chosen side. */
  private boolean aiTurn() {
    return activeMode.equals("AI vs AI")
        || (activeMode.equals("Player vs AI") && game.turn() != activeHumanSide);
  }

  /** Handles both stages of a human move: source selection, then destination selection. */
  private void click(Square square) {
    // 1. Reject clicks when a human is not allowed to move.
    if (game.finished() || aiTurn() || paused || search != null) return;
    // 2. Match source/destination against complete ChessLib legal moves.
    // This includes king safety, castling, en passant, and promotion choices.
    if (selected != null) {
      List<Move> candidates =
          game.legalMoves().stream()
              .filter(m -> m.getFrom() == selected && m.getTo() == square)
              .toList();
      if (!candidates.isEmpty()) {
        Move move = candidates.get(0);
        // Same source/destination can have four promotion moves, one per allowed piece.
        // Cancelling the modal choice leaves the pawn and current selection untouched.
        if (candidates.size() > 1) {
          ChoiceDialog<Move> dialog = new ChoiceDialog<>(move, candidates);
          dialog.setTitle("Pawn promotion");
          dialog.setHeaderText("Choose promotion piece (Q, R, B, N)");
          Optional<Move> choice = dialog.showAndWait();
          if (choice.isEmpty()) return;
          move = choice.get();
        }
        // 3. Commit through the shared validation boundary, render, then request an AI turn.
        game.play(move);
        selected = null;
        refresh();
        schedule();
        return;
      }
    }
    // 4. If no destination matched, select another friendly piece or clear the selection.
    // Illegal destinations never alter the board or advance the turn.
    Piece piece = game.piece(square);
    selected = piece != Piece.NONE && piece.getPieceSide() == game.turn() ? square : null;
    refresh();
  }

  /** Replaces the session only after confirmation, then applies the visible settings. */
  private void newGame() {
    // Keep the current game if the user declines or closes the confirmation dialog.
    if (!game.history().isEmpty() && !game.finished()) {
      Alert confirm =
          new Alert(
              Alert.AlertType.CONFIRMATION,
              "Discard the current game?",
              ButtonType.YES,
              ButtonType.NO);
      if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
    }
    // Invalidate old work before installing a new board, so old moves cannot enter it.
    cancelSearch();
    game = new GameSession();
    selected = null;
    paused = false;
    activeMode = mode.getValue();
    activeHumanSide = humanSide.getValue();
    activeDepth = depth.getValue();
    refresh();
    schedule();
  }

  /** Cancels the scheduled delay and requests interruption of any running AI search. */
  private void cancelSearch() {
    // Task cancellation is cooperative: the engine checks interruption during recursion.
    // The generation check ALSO protects against a completion already queued on the UI thread.
    generation++;
    delay.stop();
    if (search != null) {
      search.cancel(true);
      search = null;
    }
  }

  /** Schedules the next AI turn after a short visual delay, if the game permits it. */
  private void schedule() {
    if (game.finished() || paused || !aiTurn() || search != null) return;
    // Capture the current generation; a restart/pause invalidates this delayed callback.
    long token = generation;
    delay.setOnFinished(
        e -> {
          if (token == generation) beginSearch();
        });
    delay.playFromStart();
  }

  /** Copies the position, runs search on the worker, and accepts its result on the UI thread. */
  private void beginSearch() {
    // 1. Recheck eligibility: state could have changed during the display delay.
    if (paused || game.finished() || !aiTurn() || search != null) return;
    // 2. Capture all search input BEFORE leaving the UI thread.
    // clone preserves the position and repetition history needed for draw evaluation.
    long token = generation;
    Board snapshot = game.snapshot();
    int limit = activeDepth;
    // 3. Only this call method executes on the background worker; it touches no controls.
    Task<Move> task =
        new Task<>() {
          protected Move call() {
            return new MinimaxEngine().choose(snapshot, limit);
          }
        };
    search = task;
    // 4. JavaFX delivers success/failure handlers on its application thread.
    task.setOnSucceeded(
        e -> {
          // Ignore callbacks belonging to a cancelled generation or replaced task.
          if (token != generation || search != task) return;
          search = null;
          // Validate again against the live board before committing the proposed AI move.
          // null is the engine's no-move result for a terminal position.
          if (task.getValue() != null && !game.play(task.getValue())) {
            paused = true;
            refresh();
            status.setText("AI returned an invalid move. Start a new game.");
            return;
          }
          // In PvAI this returns control to the human; in self-play it schedules the other AI.
          refresh();
          schedule();
        });
    task.setOnFailed(
        e -> {
          // Ignore callbacks belonging to a cancelled generation or replaced task.
          if (token != generation || search != task) return;
          search = null;
          paused = true;
          refresh();
          // Stop the loop and report the error instead of retrying indefinitely.
          status.setText("AI search failed. Start a new game.");
          task.getException().printStackTrace();
        });
    // 5. Display the thinking state before handing the task to the executor.
    refresh();
    worker.submit(task);
  }

  /** Rebuilds the visible board, status, controls, and history from the committed state. */
  private void refresh() {
    // 1. Highlight only destinations allowed for the selected piece.
    Set<Square> destinations = new HashSet<>();
    if (selected != null)
      game.legalMoves().stream()
          .filter(m -> m.getFrom() == selected)
          .forEach(m -> destinations.add(m.getTo()));
    // 2. Draw each piece and coordinate. Gold=selected, green=legal destination.
    // Disable board input during AI turns, pauses, searches, and terminal positions.
    for (int i = 0; i < 64; i++) {
      Square square = Square.values()[i];
      Button button = squares[i];
      String color =
          square == selected
              ? "#e5bb48"
              : destinations.contains(square)
                  ? "#8fbf75"
                  : ((i / 8 + i % 8) % 2 == 0 ? "#8c6260" : "#f1ded0");
      button.setText(symbol(game.piece(square)) + "\n" + square.name().toLowerCase());
      button.setStyle(
          "-fx-font-size: 22; -fx-text-fill: #101010; -fx-background-color: "
              + color
              + "; -fx-background-radius: 0;");
      button.setDisable(game.finished() || aiTurn() || paused || search != null);
    }
    // 3. Combine chess status with controller activity; only self-play can be paused.
    status.setText(game.status() + (paused ? " — paused" : search != null ? " — AI thinking" : ""));
    pause.setDisable(!activeMode.equals("AI vs AI") || game.finished());
    pause.setText(paused ? "Resume" : "Pause");
    // 4. One history entry is one ply. Pair White/Black moves under a full-move number.
    // Move.toString supplies coordinate notation (e2e4), not SAN (e4).
    StringBuilder text = new StringBuilder();
    List<Move> moves = game.history();
    for (int i = 0; i < moves.size(); i++) {
      if (i % 2 == 0) text.append(i / 2 + 1).append(". ");
      text.append(moves.get(i)).append(i % 2 == 0 ? " " : "\n");
    }
    history.setText(text.toString());
    history.positionCaret(history.getLength());
  }

  /** Maps ChessLib piece values to Unicode chess glyphs; an empty square has no glyph. */
  private String symbol(Piece p) {
    return switch (p) {
      case WHITE_KING -> "♔";
      case WHITE_QUEEN -> "♕";
      case WHITE_ROOK -> "♖";
      case WHITE_BISHOP -> "♗";
      case WHITE_KNIGHT -> "♘";
      case WHITE_PAWN -> "♙";
      case BLACK_KING -> "♚";
      case BLACK_QUEEN -> "♛";
      case BLACK_ROOK -> "♜";
      case BLACK_BISHOP -> "♝";
      case BLACK_KNIGHT -> "♞";
      case BLACK_PAWN -> "♟";
      default -> "";
    };
  }

  /** Lifecycle cleanup; cancel work before shutting down the executor. Safe to call twice. */
  @Override
  public void stop() {
    cancelSearch();
    worker.shutdownNow();
  }

  /** Hands startup to JavaFX, which creates the application and calls start on the UI thread. */
  public static void main(String[] args) {
    launch(args);
  }
}
