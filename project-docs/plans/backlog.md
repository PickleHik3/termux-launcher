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
| Raw pixel formats for Tier 1 display (`f=24`/`f=32`) | S–M | **Newly identified and now the recommended next graphics step.** The real-client pass showed Tier 1's PNG-only scope structurally excludes `chafa`, which emits raw RGBA and has no PNG mode. Smaller and better motivated than any Tier 2 feature. See `kitty-protocol-features.md` slice 13a. |
| Kitty graphics Tier 2 and 3 | L–XL | Reusable images, multiple placements, z-index, crop, Unicode placeholders, animation. The "prove Tier 1 against real clients" gate has now been run: `timg` renders correctly, `chafa` is excluded by the PNG-only scope, and `kitten` is not packaged for Termux at all. Do the raw-pixel-format row above first. |
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
- `:app:testDebugUnitTest` is green (598 tests, 92 classes). The former "48 environmental failures
  across 12 classes" baseline was a misdiagnosis — nothing in it was environmental, and 9 of the 48
  were real assertion failures the label concealed. Fixed; see the verification section of
  `../terminal-modernization-status.md` for the breakdown. Do not reintroduce an accepted-failure
  count: it launders regressions exactly the way a duplicated backlog launders unfinished scope.
- The positive case for the speculative-decoding settings switch has no JVM coverage.
  `TaiModelSpec` only promotes that capability into its endpoint set after reading flags out of a
  real `.litertlm` package, so the shown path needs instrumentation with a model installed.
  `TaiParameterPreferencesFragmentHidingTest` covers only the hidden cases.
