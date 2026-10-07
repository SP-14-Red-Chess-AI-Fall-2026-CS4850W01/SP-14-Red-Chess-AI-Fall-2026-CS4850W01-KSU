# Team guide to the commented prototype

This edition adds explanations and formatting to the original working prototype. The executable Java tokens are unchanged. It is a learning and handoff edition, not a feature redesign.

## Start here

| Reading order | File | Responsibility |
|---|---|---|
| 1 | `src/main/java/edu/ksu/redchess/GameSession.java` | Owns the live game; validates and commits moves through ChessLib |
| 2 | `src/main/java/edu/ksu/redchess/ChessApp.java` | Builds the JavaFX window and coordinates human input and AI turns |
| 3 | `src/main/java/edu/ksu/redchess/MinimaxEngine.java` | Searches future legal positions and chooses a move |
| 4 | `src/test/java/edu/ksu/redchess/ChessTest.java` | Checks game-state integrity, special rules, and basic AI behavior |
| 5 | `pom.xml` | Declares Java version, dependencies, and build/run plugins |

JavaFX calls `ChessApp.start` to build the window. The initial session is local PvP. Changing a combo box does not change an active game: click New Game to apply settings.

## Follow one human move

1. A square button calls `ChessApp.click(square)` on the JavaFX application thread.
2. The first click selects a friendly piece; `refresh` highlights that piece's legal destinations.
3. The second click is matched against ChessLib's complete legal moves. An invalid destination does not change the position; clicking another friendly piece changes selection.
4. Multiple matching moves indicate promotion. The dialog chooses a queen, rook, bishop, or knight; cancelling leaves the position alone.
5. `GameSession.play` validates again, asks ChessLib to apply the move, and records it only after success. ChessLib advances the turn and updates special-rule state.
6. `refresh` redraws the position and history. `schedule` requests an AI response only when the active mode requires one.

## Follow one AI turn

1. `aiTurn` checks the committed game mode and active side.
2. `schedule` starts a 400 ms JavaFX display delay; this does not block the UI.
3. `beginSearch` rechecks eligibility and captures a private board clone, search depth, and generation token.
4. `Task.call` runs `MinimaxEngine.choose` on the single background worker. It never accesses JavaFX controls or the live board.
5. `choose` tries every legal root move. `search` recursively examines replies until a terminal result or depth limit.
6. JavaFX delivers the Task success callback on its application thread. The callback checks that its generation and task are still current.
7. `GameSession.play` checks and commits the result against the live position. `refresh` redraws. PvAI returns control to the human; self-play schedules the next AI.

## Understand the search

A ply is one side's move. Depth 2 examines our move and the opponent's reply. The engine uses **negamax**, a compact form of minimax: each score is from the side-to-move perspective, so the parent negates the child's score.

At the depth limit, evaluation counts friendly material minus opposing material: pawn 100, knight 320, bishop 330, rook 500, queen 900. Checkmate uses a much larger score than material. Draws score zero. Mate-distance adjustment prefers faster wins and delays forced losses.

Alpha is the best lower bound found at this node. Beta is the cutoff supplied by its parent. When alpha reaches beta, the remaining candidates cannot improve the parent's decision and are skipped. Because the perspective reverses, recursion uses the negated, swapped window `(-beta, -alpha)`. A pruned result can be a bound rather than an exact score.

Search temporarily applies a move to its private board, then undoes it in a `finally` block. The undo runs even if cancellation or an exception interrupts deeper recursion. Equal scores retain the first generated move. This material-only AI has no positional evaluation, move ordering, quiescence search, or time budget yet.

## Restart, pause, and close

`cancelSearch` increments `generation`, stops the display delay, and interrupts the current Task. The engine checks interruption during recursion. Generation checks independently reject old callbacks already queued on the UI thread.

New Game confirms before discarding an active game with recorded moves, cancels old work, installs a fresh session, and copies the visible settings into the active settings. Pause preserves the position but cancels AI work; Resume searches that position again. Close cancels work and shuts down the executor.

## Where to change things

| Desired change | Location |
|---|---|
| Board colors, glyphs, destination highlights | `ChessApp.refresh` and `symbol` |
| Layout, labels, settings choices | `ChessApp.start` |
| Human click/promotion behavior | `ChessApp.click` |
| AI ownership by game mode | `ChessApp.aiTurn` |
| AI display interval | `ChessApp.delay` |
| AI background work, stale results, failure handling | `ChessApp.beginSearch`, `schedule`, `cancelSearch` |
| Live validation, move history, result messages | `GameSession.play`, `history`, `status` |
| AI search and pruning | `MinimaxEngine.choose`, `search` |
| Piece values and future positional scoring | `MinimaxEngine.evaluate` |
| Regression tests | `ChessTest` |
| Libraries, Java version, run command | `pom.xml` |

## Rules to preserve when editing

- Use `GameSession.play` for every live human or AI move.
- Keep controls and live state on the JavaFX thread; search only on a private snapshot.
- Preserve repetition history in snapshots. Rebuilding a board from FEN alone loses that history.
- Undo every temporary search move, including cancellation paths.
- Reject old-generation callbacks after restart or pause.
- List copies prevent adding/removing entries, but are not promises of deep immutability for individual Move objects. Treat returned moves as read-only.

## Testing and running

In IntelliJ's Maven panel, run Lifecycle → test, then Plugins → javafx → javafx:run. Alternatively, with Maven installed: `mvn clean test` and `mvn javafx:run`.

The six tests cover illegal-move rejection, snapshot isolation and read-only history lists, Fool's Mate, AI mate selection and board restoration, special moves, and legal opening selection. They do not automate the GUI or prove every chess rule. Manually check all modes, promotion cancellation, pause/resume, restart during search, and closing during search.

The prototype automatically ends games when ChessLib reports a draw; tournament draw-claim handling is not implemented. History is coordinate notation (e2e4), not SAN (e4). The UI/controller are currently together in ChessApp; extract a separate controller when that helps the team manage a larger interface.
