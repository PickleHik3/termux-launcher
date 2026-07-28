# Searchable session browser

Status: implemented July 2026. This completes the searchable session-management and clone-with-CWD
part of Phase 3 in the terminal modernization study.

## Surface and hierarchy

`session.browser` is a low-risk terminal action in the Session category, so it appears in the
searchable command palette and is available through the authenticated launcher API. It opens one
Material dialog containing:

- a search field over session name, every pane's working directory, and every cached foreground
  process/open-file label;
- one row per tmux-style session, visibly marking the active session;
- the session's total window and pane counts; and
- a line for every window with its ordered pane CWD and foreground/title labels.

The browser asks the existing `WindowForegroundResolver` to cover inactive sessions and windows,
not only the window-bar panes. Results update the open browser on the main thread. When privileged
foreground inspection is unavailable or still pending, the terminal title remains a useful label
and every CWD is still present and searchable.

## Actions and safety

The top-level actions are **New**, **Clone current**, and **Save workspace**. Each session row has an
accessible overflow menu for **Activate**, **Clone with CWD**, **Rename**, and **Close**.

- New creates a fresh shell at the currently focused pane's CWD.
- Clone creates a new one-window session at the selected session's focused-pane CWD. It does not
  copy or restart the foreground process, scrollback, environment mutations, windows, or panes.
- Rename uses the existing five-code-point tmux-style session naming policy.
- Save workspace captures the complete live hierarchy using the durable workspace store, without
  foreground commands. An existing name requires a second explicit replace confirmation.
- Close works on the selected session without activating it first, shows its pane count in a
  destructive confirmation, terminates only that session's shells, and selects a neighbouring
  session. Closing the last session preserves the established Termux behavior of starting a fresh
  shell.

`session.clone_current` is also a registered medium-risk action for direct palette/API use. The
browser and registry action share the same service-owned shell creation path and maximum-session
guard.

## Verification

`SessionBrowserModelTest` pins case-insensitive name/CWD/foreground filtering, stable ordering, and
hierarchy pane counts. Registry and dispatcher tests pin both new actions, UI metadata, risk, counts,
and detached execution behavior. Focused JVM tests pass. The complete app suite now contains 578 tests
and retains the documented unrelated baseline of 48 environmental failures.

On Pong (Nothing A065, Android 16), the installed debug APK advertised 69 tools including the one
configured MCP tool. The device pass opened the browser through `session.browser`, verified the
one-window/one-pane hierarchy, cloned a disposable session at the same CWD, renamed it `stest`,
filtered to that name, saved and detected a disposable workspace, and closed the disposable session
through its confirmation. Cleanup restored one session, one window, and one visible pane and deleted
the test workspace.
