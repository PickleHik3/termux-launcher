# Kitty protocol and render features — inventory and rollout

Continues `kitty-to-termux-launcher-feasibility-study.md` past the command palette and
binding work recorded in `action-registry-terminal-actions.md`. That covered the study's
Phase 1–2; this file covers Phase 4 (protocol/render upgrades) and the first Phase 5
project (the keyboard protocol).

Status: delivered through graphics Tier 2 core — stored images, placements, crop, z-index, and the
full delete forms (slice 14); animation, Unicode placeholders, and file/shm media remain excluded. User setup and compatibility guidance are in
[`../../docs/en/Terminal_Modernization.md`](../../docs/en/Terminal_Modernization.md); the cross-project
status map is [`../terminal-modernization-status.md`](../terminal-modernization-status.md).

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

- **Slice 10a — the counters were read, and they close the renderer question.** Pong (A065,
  Android 16, 120 Hz so an 8.333 ms budget). Counters reset, then a sustained glyph-heavy workload
  (900 iterations of wide mixed-width lines) held drawing active for 36.3 s and produced 143 window
  frames / 129 pane draws:

  | Metric | Value |
  |---|---|
  | Frame budget | 8.333 ms |
  | Window frame: avg / median / p95 / max | 11.77 / 10.57 / 22.00 / 26.51 ms |
  | Window **draw**: avg / median / p95 / max | 2.38 / 2.45 / 3.23 / 5.18 ms |
  | Pane **render**: avg / median / p95 / max | 2.44 / 2.36 / 3.10 / 5.00 ms |
  | `slowDraws` | 0 |
  | `jankyFrames` / `estimatedDroppedFrames` | 21 / 27 of 143 |
  | Allocation / GC | 12.19 MB over 36.3 s (~336 KB/s), 0 collections |

  A shorter 40 000-line scroll run agreed: draw avg 2.66 ms, max 5.80 ms, `slowDraws` 0.

  **Reading:** Canvas glyph drawing costs about 29% of the frame budget typically and 62% at its
  worst, with zero slow draws, and allocation produced no GC at all in 36 s. Total frame time does
  exceed the 120 Hz budget at the median, but the excess is outside draw — measure/layout/input and
  other UI work — and swapping the glyph renderer cannot recover time that is not spent drawing
  glyphs. At a 60 Hz budget the median frame would sit inside the deadline entirely.

  **Gate outcome:** the study's condition for starting the explicit OpenGL ES / Vulkan renderer was
  that "frame time, allocation, or power cannot be fixed within Canvas". These numbers do not meet
  it, and the GPU renderer additionally owes accessibility, IME positioning, and selection snapshots.
  Recommend closing that XL item as not justified by measurement rather than leaving it open, and
  attributing any future frame-time work to the non-draw portion of the frame. Re-measure if the
  draw percentiles or GC counts move materially.

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

- **Slice 13a — real-client pass, two protocol bugs fixed.** The hand-written checkerboard above was
  self-consistent, which is exactly why it proved less than it looked. Running the real clients
  packaged for Termux found two defects that a first-party payload could not:

  - **`q=2` did not suppress success replies.** The check compared `quiet` to `1` and `2` exactly, so
    a `q=2` command still wrote `OK`. Per the protocol `q=1` suppresses success and `q=2` suppresses
    everything. The reply goes to the tty, and with no application reading it, it lands in the
    shell's input line: on device this left `Gi=888985473;OK` in the prompt and broke the next fish
    command outright. The existing test only covered `q=2` with a *failure*, which was suppressed
    correctly, so the bug sat in the gap.
  - **A control-only command was rejected.** The parser required a `;`, answering
    `EINVAL:missing payload separator` when it was absent. The payload is optional, so this rejected
    well-formed input — including the canonical delete form `ESC_G a=d ESC\`, which meant the
    `action == 'd'` branch was unreachable in its most common spelling. Every existing test wrote the
    separator, so again nothing caught it.

  Both are `KittyGraphicsProtocol` fixes with new tests (11 in `KittyGraphicsProtocolTest`).

  **Client outcome.** `kitten` is not packaged for Termux, so `kitten icat` is unavailable; `chafa`
  and `timg` were used instead, and both emit genuine `ESC_Ga=T` sequences. `timg` (`-p k`) sends
  inline PNG (`f=100`, single chunk, image id) and now renders correctly and silently. `chafa`
  (`-f kitty`) sends raw RGBA (`f=32`) as a control-only header plus 296 continuation chunks; after
  the fixes it is cleanly and silently declined, because **Tier 1 is PNG-only by design**. That is
  the honest state: chafa cannot be displayed until Tier 1 accepts raw pixel formats, and chafa has
  no PNG mode to fall back to.

  **This matters for the Tier 2/3 gate**, which was worded as "only after Tier 1 has been proven
  against real clients". Tier 1 is now proven against one real client and shown to structurally
  exclude another — not because of a bug, but because of the PNG-only scope decision. Accepting
  `f=24`/`f=32` raw pixel data is a smaller and better-motivated next step than any of Tier 2's
  image-management features, and should be sequenced first.

- **Slice 13b — raw pixel formats (done).** Tier 1 display now accepts `f=24` (RGB) and `f=32`
  (RGBA, straight alpha) in addition to PNG, including `o=z` zlib-compressed payloads, which closes
  the gap slice 13a identified: `chafa -f kitty` now renders. The support query already validated
  both raw formats, so a client that probed honestly was told yes and then refused at display time.

  The PNG and raw paths share one display tail (`submitDecode`): bound checks, cursor advance, and
  the off-thread decode-and-scale, with a per-path bitmap producer. Raw decode goes through two pure
  static helpers, `rawPixelsToArgb` and `inflate`, kept free of Android types so JVM tests cover
  them directly (`Bitmap` construction itself is only exercised on device, since the module tests
  run with `returnDefaultValues`). Deliberate details:

  - A raw header must carry `s` and `v`, and `s*v*4` is checked against the session decode limit at
    header time, so a doomed transfer is refused before its chunks are accepted rather than after.
  - An uncompressed payload whose size disagrees with `s*v` is answered synchronously; a compressed
    payload can only be measured after inflation, so its mismatch answer comes from the worker.
  - `inflate` requires the stream to produce exactly the expected byte count — a stream that is
    short, over-long, or trailing-garbage is one `EINVAL`, bounded by the existing decode ceiling.
  - Unknown `o=` values are `ENOSYS:unsupported compression` at accept time, not a garbled image.
  - The format rejection message became `ENOSYS:unsupported image format` (there is no PNG-only
    scope left to cite), and the query path now inflates `o=z` probes before validating size.

  Device pass (2026-07-29, Pong, `com.termux` debug upgraded in place, fish): `chafa -f kitty`
  (raw RGBA, control-only header + continuation chunks, `q=2`) renders correctly and silently next
  to `timg -pk` (PNG) in one script run; a hand-rolled `o=z` RGBA transfer with `i=` answered
  `Gi=77;OK`, and a half-transparent zlib image scaled by `c`/`r` composited correctly over the
  wallpaper, confirming straight-alpha conversion. App-scoped logcat had no fatal exception,
  OOM, or emulator error. One earlier probe's malformed ST (a `printf` octal mistake) left reply
  bytes in the shell's input line — probe defect, useful reminder that unconsumed `i=` replies land
  in the prompt exactly as the q=2 bug's replies did.

- **Slice 14 — graphics Tier 2 core: stored images and placements (done).** `a=t` and `a=p` are
  implemented; the PNG-only-then-raw display pipeline above now feeds a bounded image store
  (`KittyImageStore`: 32 MiB / 256 images, ENOSPC over eviction, cleared by RIS but not by screen
  clears, which only drop placements). Image numbers (`I=`) resolve latest-wins, and an `I=`-only
  transmission is assigned a free id that the reply reports (`i=<id>,I=<n>;OK`), which is what
  `kitten icat` relies on.

  **The ordering design is the part worth reading.** Transmission completion *reserves* the image —
  id, number, dimensions, byte budget — synchronously on the update thread while pixels decode on
  the worker, so an `a=p` arriving next in the stream resolves the image and validates its source
  rectangle synchronously (a JVM test pins this with the decode still in flight). The placement's
  own pixel work is bounced decoder→update→decoder so it runs strictly after the transmission's
  completion lands. Store and placement never share a bitmap instance — the placement layer
  recycles; the store only drops references — so a recycle can never corrupt the store.

  Placements support source rectangles (`x,y,w,h`, clamped by `computeCrop`), cell scaling (`c,r`),
  sub-cell pixel offsets (`X,Y`, composited into transparent padding), placement ids (`p=` replaces
  its own (i,p) pair; unidentified placements are additive), and z-index. **z is stamping priority,
  not compositing**: the cell model shows one owner per cell, so a higher z takes the cell, and a
  negative z never overwrites visible text — kitty's draw-under-text becomes keep-the-text. A z<0
  placement whose every cell is withheld fails placement rather than storing an invisible bitmap.

  All delete forms except animation frames are implemented: `a/A`, `i/I` (with optional `p=`),
  `n/N`, `c/C`, `p/P`, `q/Q`, `x/X`, `y/Y`, `z/Z`; uppercase also frees the targeted stored data.
  Intersection forms delete the whole intersecting placement via a scan-then-delete-by-membership
  double pass — a single filter pass would miss the placement's cells left of and above the match.
  `d=a` with an explicit `i`/`I` keeps the legacy id-scoped meaning. Still out of scope, each with
  a bounded `ENOSYS`: animation (`a=a/f/c` — needs a render loop the terminal does not have),
  Unicode placeholders (`U=1`), and file/shared-memory media (`t=f/t/s` — filesystem paths in
  escape sequences are a security decision, not a parser feature).

  **One renderer defect found and fixed on device**: a row mixing text and image cells dropped the
  text run accumulated to the left of the first image cell — `TerminalRenderer` reset the run
  trackers without flushing them (plus two off-by-ones in the reset). Invisible for Tier 1, routine
  under z<0. The fix flushes the pending run before drawing the image cell.

  Device pass (2026-07-29, Pong, `com.termux` debug, fish): stored a 64x32 two-colour RGBA via
  `a=t o=z`, placed it twice (`p=1` full, `p=2` cropped to the right half — rendered pure red),
  deleted `p=1` by id+placement leaving `p=2` intact; `I=9`-only store answered `i=1,I=9;OK`
  (captured via `cat -v`); z=-1 placements kept text glyphs and filled blank cells around and below
  them, before and after the renderer fix. End-to-end: **yazi detected KGP and previewed images**
  through store/place/delete cycles — hovering a PNG rendered it, moving to a JSON file replaced it
  with a text preview, no ghost images, and app-scoped logcat stayed clean throughout.

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
`KittyGraphicsProtocolTest` (27, including raw-format, compression, placement, delete-form, and
pure-helper coverage), and `KittyImageStoreTest` (6).
The complete `terminal-emulator` (273 tests), `terminal-view` (15 tests), and `:app` (598 tests)
suites pass with 0 failing in both debug and release variants. (The former "48 environmental
failures" baseline was a misdiagnosis and is fixed — see `../terminal-modernization-status.md`.)

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
