# One clipboard: Android, the terminal, the display, the keyboard

*2026-09-14. Branch `feat/shared-clipboard`, merged to `dev` when every phase below is in.*

## Goal

Copy anywhere, paste anywhere. Text copied in an X app on the Display page pastes into a shell and
into any Android app; text copied in the terminal or in Android pastes into an X app; the in-app
keyboard's copy / cut / paste / select-all keys and the extra-keys row's paste key act on whatever
is in front of the user. No new clipboard store: the Android clipboard is the one clipboard, and
every side reads and writes it.

## What is there today

- **Terminal ↔ Android** works: copy mode, the selection toolbar, OSC 52 writes and the keyboard's
  edit keys all go through `ShareUtils`/`ClipboardManager` (`TermuxActivity.paste/copySelection/
  prepareCut/selectAll`, `TermuxTerminalSessionActivityClient`). OSC 52 is write-only: a `?` query
  is decoded as garbage and logged (`TerminalEmulator.java:3320`).
- **Display ↔ Android** is wired in the vendored server but never armed. `LorieView` carries
  upstream's full sync (`setClipboardText` from X, `requestClipboard` to X, `checkForClipboardChange`
  → `sendClipboardAnnounce`), but the `OnPrimaryClipChangedListener` is only registered in
  `onWindowFocusChanged` (`LorieView.java:648`). The pane wall attaches the view with
  `requestFocus()` (`TermuxActivity.syncDisplayPageAttachment`, `:13596`), which fires no window
  focus change, so Android → X announces happen only by accident (screen off/on with the page
  attached and the pref already loaded). The announce also demands exactly one MIME type
  (`:637`), so anything copied from a browser (text/plain + text/html) is never offered to X.
- **Keyboard edit keys on the Display page are swallowed**: `X11KeyboardBridge.interceptKeyValue`'s
  `Editing` branch returns true for COPY/CUT/PASTE/SELECT_ALL/UNDO/REDO without sending anything
  (`X11KeyboardBridge.java:75`). The extra-keys row's PASTE bypasses the interceptor and pastes into
  the terminal even while the display is showing (`TermuxTerminalExtraKeys.java:111`,
  `TerminalToolbarViewPager.java:77`).
- **Launcher text intakes drop paste**: the command palette, app-drawer search, folder rename,
  find and inline rename each handle only SPACE_BAR and BACKSPACE (`FocuslessKeyIntake.java:61`,
  `TerminalCommandPaletteController.java:732`, `AppDrawerSearchController.java:216`,
  `FolderRenameController.java:85`).

## Decisions

1. **Android's clipboard is the bus.** No launcher-side clipboard mirror, no history pane.
2. **The display side is armed by the page, not by window focus.** Sync is active exactly while
   the display view is attached, connected and `clipboardEnable` is on; arming runs a check so a
   copy made on another page is announced to X the moment the Display page settles.
3. **Edit keys on the display become the GUI chords**: copy Ctrl+C, cut Ctrl+X, paste Ctrl+V,
   select all Ctrl+A, undo Ctrl+Z, redo Ctrl+Y; a held Shift/Alt rides along, so Shift+copy is
   Ctrl+Shift+C for X terminal emulators. **Paste as plain text (Fn+paste) types the clipboard
   text as keystrokes**, the fallback for apps where Ctrl+V means something else. Before any paste
   chord the bridge re-checks the Android clipboard so X owns the latest text.
4. **The extra-keys PASTE goes wherever the keyboard's paste goes**: through the interceptor
   slot, never straight to the terminal.
5. **OSC 52 becomes readable** (`\e]52;c;?\a` answers `\e]52;c;<base64>\a`), behind one Terminal
   setting, "Let programs read the clipboard", default on. Programs in the terminal can already
   read it through Termux:API; the switch is the way out.
6. **Launcher text fields accept paste** (PASTE and PASTE_PLAIN insert the clipboard text through
   the intake's own text channel). Copy / cut / select-all in those fields stay unhandled.
7. **The X11 keyboard bridge writes through a small sink seam** so its chord mapping is unit
   tested without a native `LorieView`.

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| 1 Display sync + display edit keys | `feat/clipboard-display` | `LorieView.setClipboardSyncActive`, relaxed MIME check, `DisplayClipboardPolicy` (pure, tested), controller arming on connect/reload/detach, bridge chords via a sink seam, extra-keys PASTE through the interceptor, `x11-server/UPSTREAM.md` deviations, `docs/en/X11_Display.md` | — |
| 2 OSC 52 read | `feat/clipboard-osc52` | Query branch in `TerminalEmulator`, `TerminalOutput`/`TerminalSessionClient` read hook (default null), activity client reads via `ShareUtils`, Terminal setting + `termux.properties`-free pref, `OperatingSystemControlTest` enabled and extended | — |
| 3 Paste into launcher fields | `feat/clipboard-intakes` | PASTE/PASTE_PLAIN in `FocuslessKeyIntake`, palette (both modes), app-drawer search, folder rename; Robolectric tests | — |

Phases run in parallel in their own worktrees off `feat/shared-clipboard`; each merges back here;
`feat/shared-clipboard` merges to `dev` last.

## Gates

- `./gradlew :app:testDebugUnitTest --tests '*x11*' --tests '*inappkeyboard*' --tests '*terminal*'`
  and `./gradlew :terminal-emulator:test` green per phase; the module suites green on the merged
  branch, failing-name lists compared against a clean `dev` worktree.
- Device check on pong (developer's call): copy in xterm → paste in a shell; copy in a shell →
  Ctrl+V in an X text field; keyboard paste key on the Display page; extra-keys PASTE on the
  Display page; `printf '\e]52;c;?\a'` answered; paste into the palette.

## Hit every surface

Keyboard up and down (edit keys exist only with the keyboard up; the extra-keys row's PASTE is the
keyboard-down path). Display page and terminal page (the interceptor decides). `clipboardEnable`
on and off, and toggled while the page is showing. Display started before and after the page is
first attached. Editions: no change.
