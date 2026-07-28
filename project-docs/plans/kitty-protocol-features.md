# Kitty protocol and render features — inventory and rollout

Continues `kitty-to-termux-launcher-feasibility-study.md` past the command palette and
binding work recorded in `action-registry-terminal-actions.md`. That covered the study's
Phase 1–2; this file covers Phase 4 (protocol/render upgrades) and the first Phase 5
project (the keyboard protocol).

## Decision

Protocol state lives with the `TerminalEmulator` in the service, not with the view, so it
survives Activity recreation for free. Policy — what a link may be opened with, whether the
cursor animates — lives in `app`, because `terminal-emulator` and `terminal-view` are shared
with upstream Termux and every line added there is rebase surface.

Two mechanisms carry the new per-cell state:

- **The packed style `long`.** Bits 11–13 became the underline style. Bits 0–10 are the
  existing attributes, 15 is `BITMAP`, so bit 14 is now the only free one.
- **Lazily allocated side tables on `TerminalRow`.** A 24 bit decoration colour and a
  hyperlink id do not fit in one spare bit. Each is an `int[]` allocated only for the rows
  that actually carry one, and cleared by `clear()` because an erased row's links are gone
  with its text.

Both are preserved by the same paths: `setChar`, `copyInterval`, reflow inside
`TerminalBuffer.resize`, and `setOrClearEffect` (DECCARA), which now re-encodes through
`TextStyle.withColorsAndEffect` instead of dropping everything it does not know about.

## Slices

- **Slice 1 — underline styles (done).** `TextStyle` gained
  `UNDERLINE_STYLE_{NONE,SINGLE,DOUBLE,CURLY,DOTTED,DASHED}` in bits 11–13, a 4-argument
  `encode`, `decodeUnderlineStyle`, and `withColorsAndEffect`.

  SGR `4:0`–`4:5` map to the styles, plain `SGR 4` is single, `SGR 21` is double (it was
  previously logged as unknown), `SGR 24` and `SGR 0` clear. An unknown style such as `4:9`
  draws a single underline rather than nothing, which is what the specification asks of a
  terminal that does not know it. `mUnderlineStyle` is saved and restored with the cursor.

  `CHARACTER_ATTRIBUTE_UNDERLINE` is kept in step with the style field by
  `setUnderlineStyle`, so DECCARA and any code that only knows the attribute bit still work.
  The renderer treats "bit set, style NONE" as single for exactly that reason.

  `TerminalRenderer` draws the geometry itself instead of `Paint.setUnderlineText(boolean)`,
  which can only draw one straight line: rects for single and double, a `Path` wave for
  curly, and `drawLine` with a cached `DashPathEffect` for dotted and dashed — `drawLine` is
  the one form hardware acceleration accepts a path effect on across all supported API
  levels. Every variant is clamped between the baseline and the bottom of its own cell so a
  decoration never bleeds into the row below.

- **Slice 2 — decoration colour (done).** SGR 58 was already fully parsed into
  `mUnderlineColor` and read nowhere. It is now written per cell into the row's decoration
  table and used as the underline colour, falling back to the cell's foreground.
  `TextStyle.DECORATION_COLOR_DEFAULT` is the "follow the text colour" sentinel that SGR 59
  restores; `SGR 0` now resets it too, which it did not before.

- **Slice 3 — OSC 8 hyperlinks (done).** `TerminalHyperlinks` is the per-session link pool:
  cells store a small id, the pool holds the URI once. Ids are interned by `(id, uri)` so a
  link spanning many cells and several runs costs one string, which is what the `id=`
  parameter is for.

  Bounded on purpose, since escape sequences are untrusted input: 4096 links per session and
  2083 characters per URI, and a URI containing a control character is rejected outright
  rather than percent-decoded. A full pool degrades to plain text — the cells render normally
  and are simply not clickable — instead of pushing the session toward an lmkd kill. The pool
  is dropped on `reset()`; it is **not** garbage collected when the last referencing cell
  scrolls away, because finding that out means walking every row. `CSI 3 J` deliberately does
  not clear it, as cells still on screen may reference those links.

  Activation is policy and lives in `app`: a tap on a linked cell opens a dialog showing the
  full target, because unlike the URL regex path the target is chosen by the application and
  need not resemble the text that was tapped. Only `http, https, mailto, tel, sms, geo, ftp,
  ftps` get an Open button; anything else — `file:` in particular, which either leaks an
  app-private path or throws `FileUriExposedException` — can only be copied.

  Linked cells are underlined when they have no underline of their own, since touch has no
  hover to reveal them on.

- **Slice 4 — cursor trail (done).** `CursorTrail` draws a streak that shrinks and fades from
  the cursor's old cell to its new one. It reads the cursor position and draws over the
  finished frame; it knows nothing about cells or protocols, and requests a frame only while
  a streak is in flight, so a still cursor costs nothing.

  It remembers the cursor in **emulator** row coordinates, not view coordinates, so scrolling
  the view does not read as cursor movement. Jumps of more than 8 rows are treated as a screen
  change and not animated. Reset on session switch and on reflow, where the remembered cell no
  longer means anything.

  **Fixed after the first device pass, from a report that it worked inside tmux but not at a raw
  prompt.** Four defects, in order of how much they mattered:

  - *It gated on whether the cursor was being drawn this frame.* With a blinking cursor the remembered
    cell was cleared on every blink-off frame, so a streak could never start. That is the tmux
    difference: `DECSCUSR` with an odd parameter turns blinking on, fish sets exactly that on startup,
    and tmux normalizes it to a steady cursor. It now tracks `isCursorEnabled()` — has the program
    hidden the cursor — and is indifferent to the blink phase.
  - *The streak was the rectangle bounding the two cells.* On a diagonal move that is a block covering
    every cell between them: the reported screenshot showed a nine-by-five block tinting 45 cells of
    an editor. It is now the convex hull of the two cursor cells, a band one cell wide along the
    direction of travel, built by a monotone chain over the eight corners — cheaper than case analysis
    for eight directions and unable to get one of them wrong. `CursorTrailHullTest` pins the shape.
  - *Too heavy and too slow.* Peak alpha 0.55 to 0.3, duration 70-160ms to 60-120ms, and both the
    tail's catch-up and the fade now ease out, so the streak is mostly gone early instead of lingering
    long enough to be caught in a screenshot.
  - *No minimum distance.* Typing advances one column per keystroke, which drew a two-cell blob on
    every letter. Moves shorter than two cells are no longer animated.

  On or off is decided in `app` by `TermuxTerminalViewClient.applyCursorTrailPolicy`: the new
  `terminal_cursor_trail` preference (default on) and `PowerManager.isPowerSaveMode()`,
  re-read on resume because the user can change either while the activity is stopped.
  `appearance.toggle_cursor_trail` flips the preference and reports the value it moved to;
  `terminal.state` reports it.

- **Slice 5 — OSC 133 shell integration (done).** A row carries one mark
  (`TerminalRow.MARK_{PROMPT,COMMAND,OUTPUT}_START`), stored on the row so it follows it
  through the circular buffer and into history for free. Reflow moves a wrapped row's mark to
  the first of the rows it becomes. Blanking a full row clears its mark, which is what makes
  `CSI 2 J` drop them.

  `OSC 133;D` records the exit status, tolerating the trailing `key=value` parameters shells
  add and leaving the status unknown rather than failing the sequence on garbage.
  `hasShellIntegration()` reports whether any mark was ever seen, which is how the app tells
  "no more prompts" apart from "this shell emits no marks".

  `terminal.jump_previous_prompt` / `terminal.jump_next_prompt` scroll to the closest marked
  row. They carry `REQUIRES_SESSION` but not a "needs marks" predicate — no such condition
  exists in `ActionContext` — so they report `409 no_prompt_mark` at execution time with a
  message that distinguishes the two causes.

  **Shipping the shell scripts is deliberately not part of this slice**, only the path they will
  live at (see below). fish 4 already emits marks with no setup, which is what the device pass was
  run against. For bash and zsh, the minimum is a `PS1` that brackets the
  prompt: `\033]133;A\033\\` before it and `\033]133;B\033\\` after.

- **Slice 6 — kitty keyboard protocol (done).** `KittyKeyEncoder` holds the encoding and the
  Android key code table; `TerminalEmulator` holds the negotiation.

  All five progressive enhancement flags are implemented, because the specification asks
  terminals not to implement a subset: disambiguate, report events, report alternate keys,
  report all keys, report associated text. `CSI = flags ; mode u` sets them (mode 1 replaces,
  2 sets bits, 3 clears bits), `CSI > flags u` pushes, `CSI < number u` pops, `CSI ? u`
  answers `CSI ? flags u`. Undefined flag bits are dropped rather than stored.

  The main and alternate screens keep **separate flags and separate stacks**, as required, so
  an editor can change the mode on the alternate screen without knowing what the shell set on
  the main one. Each stack holds 16 entries and evicts its oldest on overflow; popping an
  empty stack resets the flags.

  `encode` is three-valued and that is the whole design: `null` means "not the protocol's
  business, use `KeyHandler`", `""` means "the protocol says this event produces no bytes, so
  swallow it rather than falling through", and anything else is the bytes. That is what keeps
  legacy behaviour exactly intact for the enhancements a program did not ask for.

  Deliberate details, each from the specification rather than convenience:
  - Enter, Tab and Backspace keep their legacy bytes unless all keys are reported, so a user
    can still type `reset` after a program exits without clearing the mode. Escape does not —
    disambiguating it is the point.
  - The key number is always the **unshifted** code point: `ctrl+shift+a` is `CSI 97;6u`,
    never `CSI 65;…`.
  - `CSI A` for an unmodified arrow, `CSI 1;5A` when modified: the number is 1 by default and
    must be omitted when the modifier field is absent.
  - F3 is `CSI 13~`, never `CSI R`, which would collide with the Cursor Position Report.
  - Lock modifiers are stripped from text-producing keys unless all keys are reported.
  - Modifier keys report themselves only under all-keys reporting.
  - Only the base-layout alternate is missing: Android exposes no PC-101 physical mapping to
    derive it from. The shifted alternate is reported, and only when shift is held.

  `TerminalView.handleKittyKeyEvent` runs after the client's own bindings, so app shortcuts
  keep winning, and before the legacy encoders. Dead keys fall through to the legacy path,
  which owns `mCombiningAccent`. `onKeyUp` consults the encoder as well, since release events
  exist only in this protocol.

- **Slice 8 — key inspector (done).** `TerminalKeyInspector` is the in-app equivalent of kitty's
  `kitten show-key`, and closes the last open item from the study's Phase 2. For each key event it
  reports three things that no existing tool shows together: what Android delivered (key code, scan
  code, device, modifiers with the protocol's own modifier value, active keyboard flags), which
  registry binding claimed it, and the exact bytes that reached the shell in `cat -v` notation.
  Android's key logging shows the event but not the encoding; `cat -v` shows the bytes but neither the
  event nor a stroke the app swallowed before the shell could see it.

  **The panel is not focusable, and that is the whole design constraint.** A dialog would take input
  focus, the terminal would stop receiving key events, and the inspector would have nothing left to
  inspect. It is a `FrameLayout` child of `terminal_root_container` with
  `descendantFocusability="blocksDescendants"`, and the close button is clickable but explicitly not
  focusable for the same reason.

  The bytes are reported from `TerminalView` rather than recomputed: a new `KeyInputProbe` — one
  nullable field and four call sites, null in normal use — fires at the three places key input is
  written, tagged `kitty`, `keyhandler` or `text`. Recomputing would mean a second implementation of
  the encoders that could disagree with the real one, which is exactly the failure a diagnostic must
  not have.

  `app.key_inspector` toggles it and is deliberately **bound to nothing**: an inspector that needs a
  chord to open cannot report that chord's own events. `TermuxActivity.onDestroy` closes it, since the
  overlay holds the Activity strongly.

  Verified on device in one pass: `KEYCODE_F5` reported `kitty: ^[[15~`, `ctrl+p` reported
  `kitty: ^[[112;5u`, `q` reported `text: q`, `KEYCODE_DPAD_UP` reported `kitty: ^[[A`,
  `ctrl+alt+v` reported `binding: ctrl+alt+v -> pane.split_vertical` followed by "nothing written to
  the shell", and every release and modifier-key event reported the same. The terminal kept focus
  throughout, which is what makes the reporting possible at all. The `kitty=5` field also showed fish
  4 pushing its own flags (disambiguate plus alternate keys) with no configuration.

- **Slice 7 — one duplicated tool list removed (done).** `LauncherCtlApiServer`'s execute
  route held a **third** copy of the terminal tool list, and a tool absent from it answered
  `501 not_implemented` while being advertised and executable everywhere else — which is
  exactly how the two new jump actions failed on their first device run. That case list is
  now `if (TerminalActionDispatcher.handles(name))`, so the route cannot drift from the
  registry again.

## User config path

Decided 2026-07-27: everything this roadmap needs to write for the user goes under
**`~/.termux/`**, which already holds this fork's own files and is the same path in both shipped
editions. That answers the study's fork-specific constraint — the shell integration scripts and the
workspace definitions no longer need a path decision before they can be built, and neither ends up
in a `com.termux`-specific location that a later `io.vaj.tl` user would have to be migrated out of.

Names to use when those slices land: `~/.termux/shell-integration/termux-launcher.{bash,zsh,fish}`
and `~/.termux/workspaces/<name>.json`.

## Verification

`project-docs/verification/test-terminal-protocols.sh` prints every rendition and protocol
covered here into a terminal for eyeballing.

Unit tests: `UnderlineStyleTest` (16), `HyperlinkTest` (15), `ShellIntegrationTest` (10),
`KittyKeyboardProtocolTest` (29), `TerminalKeyInspectorTest` (6), `CursorTrailHullTest` (6).
`terminal-emulator` is at 220 passing tests and `terminal-view` at 6, both with 0 failing.
`:app:testDebugUnitTest` still fails the documented 48 environmental tests across the same
12 classes and no others — compare against that baseline, not against green.

Device pass (2026-07-27, one build, `com.termux` debug, fish 4 as the shell):

- All six underline variants render distinctly, and the decoration colour is honoured in both
  indexed (`58;5;9`) and truecolor (`58;2;0;180;255`) form.
- An OSC 8 run is underlined and the text after it is not.
- `terminal.jump_previous_prompt` scrolled to `topRow -1`, then reported
  `409 no_prompt_mark` with nothing further back; `jump_next_prompt` returned to `topRow 0`.
- `appearance.toggle_cursor_trail` flipped false then back to true, with `terminal.state`
  tracking it. It needs `"confirm": true` on the remote surface, like the wallpaper toggle.
- `/v1/agent/tools` reports 58 (57 static + 1 MCP) with all three new tools present.
- With `CSI = 1 ; 1 u` set, `cat -v` received `^[[?1u` for the query, `^[[112;5u` for
  `ctrl+p`, `^[[97;5u` for `ctrl+a`, and `^[[15~` for F5.

One bug the device pass found and fixed: `jumpToPrompt` returned true after clamping to a
`topRow` it was already on, reporting a scroll that had not happened. It now returns false
when the target row is already the top row.

## Not started

From the study's remaining backlog, in its recommended order:

- **Kitty graphics protocol MVP** (L). APC is still swallowed; `ApcTest` pins that as the
  current contract. Tier 1 is query + chunked PNG transmit + display at cursor + delete, with
  an image store, memory caps, and async decode.
- **Durable workspace files, automatic layouts, session browser** (M–L). The study's Phase 3. The
  config path it was waiting on is decided above.
- **Shell integration scripts** for bash and zsh, at the path decided above. fish needs none.
- **Ligature and cluster-aware shaping, font fallback ranges** (L).
- **Explicit GPU renderer** (XL). Benchmark-gated by the study and still deferred: Phase 0's
  frame-time counters are the prerequisite, not the renderer.
