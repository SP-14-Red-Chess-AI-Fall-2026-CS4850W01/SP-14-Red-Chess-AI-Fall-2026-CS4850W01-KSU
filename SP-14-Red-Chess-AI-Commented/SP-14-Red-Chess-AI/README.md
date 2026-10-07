# SP-14 Red — Chess Game with AI

Java 17 desktop prototype for CS4850, using JavaFX and ChessLib 1.3.7.

## Run locally

Install **JDK 17** and **Apache Maven 3.9+**. Clone this repository, open its folder in IntelliJ IDEA, and import `pom.xml` as a Maven project. Set the project SDK to Java 17.

```sh
mvn clean test
mvn javafx:run
```

Use a desktop session to run JavaFX. Maven downloads the platform-specific JavaFX libraries; no separate JavaFX SDK is needed. ChessLib is downloaded from JitPack, which must be reachable.

## Play

Select a mode, human side, and AI search depth, then click **New Game**. Click a piece to highlight its legal destinations, then click a destination. Promotion offers all four pieces. Coordinate labels are printed on each square. Settings apply when a new game starts. AI self-play supports Pause/Resume. New Game cancels previous AI work and confirms before discarding an active game.

The prototype supports legal moves, castling, en passant, promotion, checkmate, stalemate, and ChessLib draw detection. It automatically ends the game when ChessLib reports a draw; tournament claim-based draw handling is not implemented. Move history uses coordinate notation, not SAN. The AI uses negamax/minimax with alpha-beta pruning and material evaluation at depths 1–3. It is intentionally a basic student engine.

## Code ownership and next tasks

| Component | File | Suggested owner | Next task |
|---|---|---|---|
| ChessLib boundary and live state | `GameSession.java` | Nicholas Lionetti | Expand rule/draw contract tests and status details |
| AI search and evaluation | `MinimaxEngine.java` | Nathanael Tappin | Piece-square evaluation, move ordering, time limits |
| JavaFX interface | `ChessApp.java` | Nicholas Spaulding | Extract controller, improve promotion dialog and board accessibility |
| Testing and release | `ChessTest.java`, build workflow | Jose Cortes | Manual mode/cancellation tests and packaging |

These assignments follow the planning roles and can be changed by the team.

Only `GameSession.play` commits live moves. Search receives an independent board clone and runs on a background executor. A generation token prevents old results from entering restarted or paused sessions. No network service or account is required at runtime.

## Validation

`mvn verify` runs tests for illegal-move integrity, snapshot isolation, checkmate, special moves, and AI legality/mate selection. GitHub Actions runs the same build. Before calling this a release, manually verify all modes, promotion choice, pause during search, restart during search, and close during search on Windows and macOS. GUI automation is not yet included.

## Dependencies

- [ChessLib](https://github.com/bhlangonijr/chesslib), Apache-2.0
- [OpenJFX](https://openjfx.io/), GPLv2 with Classpath Exception
- [JUnit](https://junit.org/), EPL-2.0

The team's own source license has not yet been chosen. This is a development prototype, not the final semester submission.

## Team code walkthrough

Read [TEAM-CODE-GUIDE.md](TEAM-CODE-GUIDE.md) for the reading order, event flow, threading rules, and a map of where to make changes. Java source and tests include explanatory comments.
