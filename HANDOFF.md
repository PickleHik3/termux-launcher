# HANDOFF — feat/tlstore-window

Phase W: window.open (launcherctl window open / POST /v1/windows). Owns app/src/main/java/**,
app/src/test/**, docs/en/LauncherCtl_API.md.

Status: implemented, unit-tested green, not yet committed/merged (WIP).

Root cause found: TerminalSession forks its subprocess only on the first updateSize() call
(TerminalSession.initializeEmulator, terminal-emulator module); a new window/pane's view isn't
laid out (so never calls it) while the Activity is stopped or the window is never shown — "no
output at all". Fixed via TermuxActivity#seedWindowSize, borrowing the on-screen pane's
columns/rows/cell size so the shell forks immediately regardless of visibility or focus; applied
to both openCommandWindow (new) and openCommandPane (existing, same root cause).

Files: TerminalHost.java, TerminalActionDispatcher.java, TermuxActivity.java,
LauncherCtlApiServer.java, docs/en/LauncherCtl_API.md, two test files.

Next: commit, run full :app:testDebugUnitTest + :app:assembleDebug, hand back to orchestrator.
