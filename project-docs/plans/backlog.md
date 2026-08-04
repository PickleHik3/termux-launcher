# Terminal modernization backlog

The single list of what is left from `kitty-to-termux-launcher-feasibility-study.md`. It exists
because the remaining work was spread across three documents, and a list kept in more than one
place drifts — which is exactly the bug that made two registered actions answer `501` while being
advertised everywhere else.

Only unfinished work is listed here. For what is built, and why each decision was made, read the
slice records instead:

- `../terminal-modernization-status.md` — engineering overview, user-file contracts, compatibility
  decisions, verification summary, and links to every owning record.
- `../../docs/en/Terminal_Modernization.md` — public task-oriented setup and usage guide for all
  delivered features.

- `action-registry-terminal-actions.md` — the registry, dispatcher, binding resolver, command
  palette, chords, user binding file, hints, search, and modal maps (the study's Phase 1–2).
- `kitty-protocol-features.md` — underlines, decoration colour, OSC 8, cursor trail, OSC 133 and
  its bash/zsh scripts, the kitty keyboard protocol, the key inspector, and the Phase 0 performance
  counters and parser fuzz/size-limit work, multiple cursors, and graphics Tier 1 (Phase 4–5).
- `fonts-and-shaping.md` — the decided `fonts.conf` contract, four-face first delivery, Canvas
  cluster experiment, symbol maps, features/axes/metrics, safety rules, and explicit exclusions.
- `durable-workspaces.md` — versioned workspace files, atomic storage, safe shell+CWD restore, and
  opt-in foreground-command capture/execution (the first Phase 3 project).
- `automatic-pane-layouts.md` — six automatic layouts plus equalize, geometric rotation, and
  focused-pane movement to an outer edge (Phase 3 multiplexer completion).
- `session-browser.md` — searchable session/window/pane hierarchy, clone-with-CWD, and browser
  management actions (Phase 3 multiplexer completion).
- `../../../../kitty-to-termux-launcher-feasibility-study.md` — the original assessment, with a phase
  status table near the top.

Sizes follow the study: **S** 2–5 days, **M** 1–3 weeks, **L** 4–8 weeks, **XL** 2–6 months.

## Ready to start

There is no unblocked implementation slice left from the feasibility study. The remaining projects
below are gated on measurements, concrete client demand, security/UX design, or an explicit product
decision.

## Deferred on purpose

Not "forgotten" — each has a stated reason to wait.

| Item | Size | Why it waits |
|---|---|---|
| `androidTest` source set for `terminal-view` | S | `FallbackFontResolver`'s coverage probe is the only font code with no real-`Paint` verification, because the module has only `main` and `test`. The blocker is the missing source set and its build wiring, not the test: once it exists, one instrumentation case per chain-precedence rule plus one `Paint.hasGlyph` fixture covers it. Worth doing before the next font batch rather than after. |
| Geometric octants and the excluded legacy-computing sub-ranges | M | `U+1CD00-U+1CDE5` (octants, Symbols for Legacy Computing Supplement) is outside the block the geometric batch scoped, and `U+1FB3C-U+1FB6F` (wedges, diagonal fills) plus `U+1FB90-U+1FBFF` (inverse shades, pattern fills, segmented digits) were left to the font. Octants are the valuable half — they are what modern TUI plotters emit — but they need their own `Segments` budget and a genuine 2×4 sub-cell grid, so they are a slice, not a range addition. Wait for a client that emits them. |
| Iosevka in the font catalog | S | Deliberately dropped: upstream publishes no small per-face artifact, so one family would cost a multi-hundred-megabyte download for four faces. Reopen if upstream ships per-face releases or if the downloader learns partial-archive extraction. |
| Kitty graphics Unicode placeholders | M | Animation (`a=f/a/c`, `d=f/F`) was delivered 2026-07-29 as slice 16 of `kitty-protocol-features.md` with terminal-driven GIF playback verified on device. What remains of Tier 2/3: Unicode placeholders (`U=1`, needs renderer placeholder decoding — the path to images inside tmux) and file/shm transmission (`t=f/t/s`, a security decision). Each answers a bounded `ENOSYS` today; wait for a packaged client that needs one. |
| Explicit OpenGL ES / Vulkan renderer | XL | **Gate measured, condition not met — recommend closing.** Canvas draw is ~2.4 ms typical and 5.0 ms worst against an 8.333 ms budget with zero slow draws, and 36 s of sustained glyph work caused no GC. Frame time does overrun at 120 Hz but outside draw, where a renderer swap cannot help. Numbers in `kitty-protocol-features.md` slice 10a. Reopen only if draw percentiles or GC counts move materially. |
| Multicell / variable-sized text | XL | Every buffer mutation and reflow path would need multirow ownership metadata. Wait until a concrete Android workflow depends on it. |
| Desktop notification protocol | M | Needs runtime notification permission, channels, background restrictions, and intent validation to be designed together. |
| File transfer over TTY | M | Security sensitive. Android share and SAF flows may simply be better UX. |
| Colour stack, palette notifications, pointer shapes, unscroll | M | Parser and state work with no current demand. Prioritize when an application asks. |
| Kittens and plugin scripts | M | Do not embed kitty's Python app API. `launcherctl` plus the registry already lets a shell script in any language drive the app. |
| Multiple Android top-level windows as a core abstraction | — | The study rejects it outright: on a phone, tabs and panes inside one Activity are predictable and OS-managed multi-window is not. |

## Loose ends inside finished slices

Small, deliberate omissions recorded where they happened. Listed here so they are not lost.

- The terminal action sheet is still hand-curated rather than generated from the registry. That was a
  product decision, not an oversight: auto-generating all 50 UI tools into it would bury the common
  actions. See `action-registry-terminal-actions.md` slice 7.
- `appearance.set_wallpaper` was device-invoked: it returns 200 and launches the system photo picker
  (`com.google.android.photopicker`), and cancelling returns to `TermuxActivity` with no crash and the
  API still responsive. The pick-and-crop completion is still unexercised, deliberately — finishing it
  writes a real wallpaper to the device, so it needs a human who wants that outcome.
- The keyboard protocol reports the shifted alternate key but not the base-layout one: Android
  exposes no PC-101 physical mapping to derive it from.
- Workspace files do not record a window's retained layout, so a window restored by `workspace.load`
  starts manually managed. Adding the field is a `TerminalWorkspace` format/version decision owned by
  `durable-workspaces.md`, not by the layout slice.
- Direct `goto_layout`/`toggle_layout` bindings still wait on user-editable bindings being able to
  carry an enum argument. `next_layout` cycling is the shipped substitute.
- `:app:testDebugUnitTest` is green (769 tests, 109 classes, re-measured 2026-08-04). The former "48 environmental failures
  across 12 classes" baseline was a misdiagnosis — nothing in it was environmental, and 9 of the 48
  were real assertion failures the label concealed. Fixed; see the verification section of
  `../terminal-modernization-status.md` for the breakdown. Do not reintroduce an accepted-failure
  count: it launders regressions exactly the way a duplicated backlog launders unfinished scope.
- `powerline_symbols synthesize` has no effect while `box_drawing font` is set, because
  `BoxDrawingPolicy.synthesizes()` gates on the mode before consulting the range. That is the intended
  reading of `box_drawing font` — hand the whole geometric repertoire back to the font — and it is
  documented rather than fixed. Revisit only if someone genuinely wants font box drawing with
  synthesized separators.
- Mirroring an installed family to `~/.termux/font.ttf`/`font-italic.ttf` is **closed, not pending.**
  It shipped once, silently replaced the user's own Nerd Font build on a real device, and was removed;
  the picker now writes nothing outside `~/.termux/fonts/` and `~/.termux/fonts.d/10-launcher.conf`.
  Do not reopen it as "make the picker visible to Termux:Styling" — the managed config already names
  all four faces by path, so there is nothing to gain and a user's font to lose. Two picker bugs found
  in the same pass are also fixed rather than open: the reload broadcast that a stopped
  `TermuxActivity` dropped, and the window bar loading `font.ttf` directly so `symbol_map` icons in
  tab labels drew as tofu. Both are recorded under "Fixed while delivering the picker" in
  `fonts-and-shaping.md`.
- The font catalog is bundled with no refresh URL wired up (`refreshUrl` is empty in
  `assets/fonts/catalog.json`). That was the point: the picker must work with no network and no apt
  repository, and every hash in the file was verified by hand. A remote catalog needs a signing story
  before it is an improvement.
- The positive case for the speculative-decoding settings switch has no JVM coverage.
  `TaiModelSpec` only promotes that capability into its endpoint set after reading flags out of a
  real `.litertlm` package, so the shown path needs instrumentation with a model installed.
  `TaiParameterPreferencesFragmentHidingTest` covers only the hidden cases.
