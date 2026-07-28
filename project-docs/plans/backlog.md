# Terminal modernization backlog

The single list of what is left from `kitty-to-termux-launcher-feasibility-study.md`. It exists
because the remaining work was spread across three documents, and a list kept in more than one
place drifts — which is exactly the bug that made two registered actions answer `501` while being
advertised everywhere else.

Only unfinished work is listed here. For what is built, and why each decision was made, read the
slice records instead:

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
- `../../../kitty-to-termux-launcher-feasibility-study.md` — the original assessment, with a phase
  status table near the top.

Sizes follow the study: **S** 2–5 days, **M** 1–3 weeks, **L** 4–8 weeks, **XL** 2–6 months.

## Ready to start

| Item | Size | Notes |
|---|---|---|
| Shaping controls and font metrics | M | Four real faces, ICU fixed-cell graphemes, Canvas shaped-run advances, and path/family `symbol_map` ranges are complete; no HarfBuzz port is currently needed. Next: ligature policy, OpenType features, variable axes, and metrics. No bidi, `narrow_symbols`, or arbitrary symbols consuming following cells. |

## Deferred on purpose

Not "forgotten" — each has a stated reason to wait.

| Item | Size | Why it waits |
|---|---|---|
| Kitty graphics Tier 2 and 3 | L–XL | Reusable images, multiple placements, z-index, crop, Unicode placeholders, animation. Only after Tier 1 has been proven against real clients such as `kitten icat` and yazi. |
| Explicit OpenGL ES / Vulkan renderer | XL | Benchmark-gated by the study. Do not start until the completed Phase 0 counters show that frame time, allocation, or power cannot be fixed within Canvas. It also owes accessibility, IME positioning, and selection snapshots, which are part of the work rather than follow-ups. |
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
- `appearance.set_wallpaper` is registered and advertised but was never invoked in a device pass,
  because it launches an external picker and crop flow. Exercise by hand.
- The OSC 8 link pool is not garbage collected when the last cell referencing a link scrolls away.
  Bounded, so it degrades rather than grows without limit. See `kitty-protocol-features.md` slice 3.
- The keyboard protocol reports the shifted alternate key but not the base-layout one: Android
  exposes no PC-101 physical mapping to derive it from.
- Automatic pane layouts are currently one-shot tree transforms. They still need a retained
  per-window layout policy, automatic reflow after pane creation/removal, and a `next_layout`
  action/keybind matching kitty's layout-cycling workflow. See `automatic-pane-layouts.md`.
- `:app:testDebugUnitTest` fails 48 tests across 12 classes for environmental reasons — loopback HTTP
  and an unmocked `XmlPullParserFactory`. Compare against that baseline rather than expecting green.
  Fixing the harness so those tests can run is itself unclaimed work.
