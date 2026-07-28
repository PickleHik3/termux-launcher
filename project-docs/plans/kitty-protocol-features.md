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
  and are simply not clickable — instead of pushing the session toward an lmkd kill. The pool is
  dropped on `reset()`. When all 4096 ids are occupied, one saturation-only sweep walks the live
  main and alternate buffers, preserves every referenced id without renumbering cells, and reuses
  unreachable holes. There is no scan on normal scroll, erase, or reflow. `CSI 3 J` deliberately
  does not clear the pool, as cells still on screen may reference those links.

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

- **Slice 9 — bash and zsh shell integration scripts (done).** App-managed scripts are installed
  idempotently at `~/.termux/shell-integration/termux-launcher.{bash,zsh}` on every process start and
  again after first-install bootstrap completion. Matching files are not rewritten, unrelated files
  in the directory are left alone, and the installer does not edit `.bashrc` or `.zshrc`.

  Users opt in with `source ~/.termux/shell-integration/termux-launcher.bash` from `.bashrc` or the
  corresponding `.zsh` file from `.zshrc`. Both scripts emit `OSC 133;D;<status>` before the next
  prompt, `OSC 133;A` at its start, and `OSC 133;C` before command execution. They deliberately do
  not emit `B` at the end of a one-line prompt: a `TerminalRow` holds one mark, so `B` on the same row
  would replace `A` and make prompt jumping lose that prompt.

  Bash prepends a status-preserving entry to either the string or array form of `PROMPT_COMMAND` and
  uses `PS0` for the command mark. Zsh appends named `precmd` and `preexec` hooks without replacing
  existing hook arrays. Both are guarded against duplicate sourcing.

- **Slice 10 — frame time, allocation, and dropped-frame counters (done).** The Activity window now
  records Android `FrameMetrics` totals, draw durations, deadline misses, and listener-overrun
  reports on a dedicated callback thread. Every visible `TerminalView` also records its own render
  duration and active-frame interval into fixed 240-sample rings, so the draw hot path allocates no
  benchmark objects. Gaps over 250 ms are treated as idle and excluded from pane frame-time
  percentiles.

  `terminal.state` exposes the results under `performance`: measurement duration, process-wide ART
  allocated-byte and GC deltas, whole-window frame metrics, and per-pane renderer metrics. Supplying
  `{"resetPerformance": true}` establishes a common zero point for the window, allocation counters,
  and all currently visible panes before returning state. Allocation/GC runtime stats and percentile
  sorting happen only when state is queried, outside the renderer hot path.

  `jankyFrames` means a measured window frame exceeded its Android deadline (or the display refresh
  budget on Android 7–11). `estimatedDroppedFrames` is a duration/budget estimate, while
  `metricsReportsDropped` separately reports samples Android could not deliver to the listener; none
  of these fields claims SurfaceFlinger presentation telemetry. Pane active-frame intervals are only
  comparable while drawing is active, and process allocation covers the whole app rather than just
  terminal rendering.

- **Slice 11 — escape parser fuzzing and per-sequence limits (done).** Every variable-length parser
  now has a finite contract. CSI payloads stop after 256 code points in addition to the existing
  32-argument/9999-value caps. OSC and ordinary DCS accumulators stop at 16,384 UTF-16 units, and
  ignored APC stops after 16,384 code points; OSC 52 is allowed roughly 100 KiB for base64 clipboard
  data, while iTerm image OSC and sixel DCS use the existing bitmap-memory ceiling as their larger
  limit. Checks account for supplementary Unicode code points without crossing a buffer boundary.

  CAN and SUB now cancel APC as well as the other parser states and run the same cleanup path.
  Aborted and completed strings clear their accumulator, oversized backing arrays are trimmed, and
  an interrupted sixel decode releases its partial bitmap. After a limit is exceeded the offending
  code point is discarded and subsequent plain text is parsed normally, matching the old overlong
  DCS recovery behaviour.

  `EscapeSequenceFuzzTest` mutates raw bytes around CSI, OSC, DCS, and APC introducers, varies BEL,
  ST, CAN, SUB, and missing terminators, splits every case at random byte boundaries, then checks all
  screen invariants and parser recovery. Its seed, case count, and maximum input length are system
  properties; `project-docs/verification/fuzz-terminal-escape-parser.sh [seed] [cases] [max-bytes]`
  is the repeatable entry point. The normal suite runs 750 fixed-seed cases, while the completion
  pass ran 5,000 cases with seed `1592639710` and 4096-byte inputs.

- **Slice 12 — multiple cursors protocol (done).** `CSI > ... SP q` now implements kitty's
  support query, coordinate forms, rectangle add/remove, main-cursor point form, cursor shapes, and
  cursor/text colour controls. Extra cursors are emulator-owned state, survive Activity recreation,
  remain fixed when scrollback moves, and are cleared by reset, full-screen erase, or screen switch.
  The Canvas renderer draws them after the base line pass so they do not disturb normal batching;
  wide cells, block/bar/underline shapes, reverse video, special colours, and cursor blink are
  handled. Seven protocol tests cover queries, coordinates, rectangles, colours, scrolling, and
  cleanup.

- **Slice 13 — kitty graphics Tier 1 (done).** APC `ESC_G...ESC\\` now supports canonical
  capability queries, direct PNG transmit, chunking, transmit-and-display, cursor placement,
  cell scaling, cursor-movement suppression, image IDs/acks, quiet modes, and deletion. Unsupported
  Tier 2 media/actions return a bounded protocol error rather than falling through or allocating.

  Base64 input, in-flight transfers, decoded session memory, controls, and dimensions are all
  capped. PNG header validation happens before decode; decoding/scaling runs on one daemon worker,
  and the final placement is posted back through the session's serialized terminal-update path.
  Existing Sixel/iTerm bitmap storage was reused, with explicit bitmap recycling and a corrected
  pixel-vs-byte resize allocation. Eight JVM tests pin parsing, queries, chunking, limits, quiet
  modes, deletion, and non-graphics APC behavior.

  Pong device verification transmitted a 12-by-6-cell pink/green checkerboard to the active PTY,
  observed `Gi=4242;OK`, visually confirmed display, deleted it, and found no app-process fatal,
  bitmap, or protocol error. The temporary local/remote payloads and test process were removed.

## User config path

Decided 2026-07-27: everything this roadmap needs to write for the user goes under
**`~/.termux/`**, which already holds this fork's own files and is the same path in both shipped
editions. That answers the study's fork-specific constraint — the shell integration scripts and the
workspace definitions no longer need a path decision before they can be built, and neither ends up
in a `com.termux`-specific location that a later `io.vaj.tl` user would have to be migrated out of.

The shell scripts use `~/.termux/shell-integration/termux-launcher.{bash,zsh}`; fish 4 emits the
marks itself and needs no file. Workspace definitions use `~/.termux/workspaces/<name>.json`.

## Verification

`project-docs/verification/test-terminal-protocols.sh` prints every rendition and protocol
covered here into a terminal for eyeballing.

Unit tests: `UnderlineStyleTest` (16), `HyperlinkTest` (19), `ShellIntegrationTest` (11),
`KittyKeyboardProtocolTest` (29), `TerminalKeyInspectorTest` (6), `CursorTrailHullTest` (6), and
`TermuxShellIntegrationInstallerTest` (2), `TerminalFrameMetricsMonitorTest` (3), and
`TerminalRenderMetricsTest` (3), `EscapeSequenceLimitTest` (7),
`EscapeSequenceFuzzTest` (1 harness test, 750 default cases), `MultipleCursorsProtocolTest` (7),
and `KittyGraphicsProtocolTest` (8).
The complete `terminal-emulator` (243 tests) and `terminal-view` (9 tests) suites pass with 0 failing.
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
- At this slice's device pass, `/v1/agent/tools` reported 58 (57 static + 1 MCP) with all three
  new tools present. Later slices add more registry actions; this is a historical verification count.
- With `CSI = 1 ; 1 u` set, `cat -v` received `^[[?1u` for the query, `^[[112;5u` for
  `ctrl+p`, `^[[97;5u` for `ctrl+a`, and `^[[15~` for F5.

One bug the device pass found and fixed: `jumpToPrompt` returned true after clamping to a
`topRow` it was already on, reporting a scroll that had not happened. It now returns false
when the target row is already the top row.

Device pass (2026-07-28, `com.termux` debug upgraded in place): the installer created both files
with owner-only permissions without touching the existing `~/.termux` contents. Bash 5.3.9 and zsh
5.9.2 passed syntax checks and emitted `D;1`, `A`, and `C` in order; pre-existing `PROMPT_COMMAND`,
`precmd_functions`, and `preexec_functions` entries survived, and sourcing each file twice installed
only one copy of its hooks. The repeatable check is `project-docs/verification/test-shell-integration.sh`.

Device performance pass (2026-07-28, same package upgraded in place): after resetting through
`terminal.state`, toggling the toolbar twice generated 25 whole-window frames and 21 pane draws.
The response contained non-zero frame/render percentiles, five deadline misses, six estimated
window drops, an 840,608-byte process allocation delta, and no dropped listener reports or GC; the
toggle ended in its original state. A preceding font-size stress pass made Pong change refresh modes
(90 Hz to 120 Hz), which found and fixed a stale display-budget snapshot. The final pass reported
the current 11.111 ms budget consistently at the window and pane levels, and app-scoped logcat had
no fatal exception or metrics-monitor error.

Parser-hardening device smoke (2026-07-28, final debug APK upgraded in place): `com.termux` launched
and its app-scoped log had no fatal exception, emulator error, out-of-memory, or bounds failure.
Pong was behind its secure keyguard, so the Activity was resumed underneath the lock screen but no
interactive terminal session was created; the oversized-stream oracle is therefore the 228-test JVM
suite and 5,000-case fuzz pass rather than a claimed on-device parser exercise.

## Not started

Everything remaining from the study, including the items this file deferred, is listed in
`backlog.md`. It is kept there rather than here so there is one list to read and one list to
maintain.
