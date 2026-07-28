# Terminal modernization backlog

The single list of what is left from `kitty-to-termux-launcher-feasibility-study.md`. It exists
because the remaining work was spread across three documents, and a list kept in more than one
place drifts — which is exactly the bug that made two registered actions answer `501` while being
advertised everywhere else.

**Nothing on this page is started.** For what *is* built, and why each decision was made, read the
slice records instead:

- `action-registry-terminal-actions.md` — the registry, dispatcher, binding resolver, and command
  palette (the study's Phase 1 and most of Phase 2).
- `kitty-protocol-features.md` — underlines, decoration colour, OSC 8, cursor trail, OSC 133, the
  kitty keyboard protocol, and the key inspector (Phase 4 and the first Phase 5 project).
- `../../../kitty-to-termux-launcher-feasibility-study.md` — the original assessment, with a phase
  status table near the top.

Sizes follow the study: **S** 2–5 days, **M** 1–3 weeks, **L** 4–8 weeks, **XL** 2–6 months.

## Ready to start

Nothing below is blocked on a decision.

| Item | Size | Notes |
|---|---|---|
| Shell integration scripts for bash and zsh | S | The emulator side is done; this is only the scripts, at `~/.termux/shell-integration/termux-launcher.{bash,zsh}`. fish 4 needs none. Without them, prompt jumping does nothing for non-fish users. |
| Frame time, allocation, and dropped frame counters | S | The study's Phase 0, never built. Prerequisite for the renderer decision below, and the only way to know whether Canvas is actually the limit. |
| Escape sequence fuzz harness and per-sequence size limits | S | Limits exist for the hyperlink pool only. Escape sequences are untrusted input and every parser added since should be fuzzed. |
| Durable workspace files | M | `~/.termux/workspaces/<name>.json`, versioned. Recreate a session/window/pane tree after a full app restart. Default to shell + CWD; capturing the foreground command is opt-in and cannot be guaranteed. |
| Automatic layouts | M | Stack, grid, tall, fat, horizontal, vertical over the existing pane tree, plus equalize, rotate, and move-pane-to-edge. |
| Session browser | M | Search by name, CWD, or foreground label; create, clone, rename, close from one surface. Clone-with-CWD is close to existing split behaviour. |
| Keybind chords and timeouts | M | `Ctrl+K > Ctrl+S`, with a visible pending-chord indicator. The resolver matches single strokes only. |
| User-editable binding file | M | Bindings currently live as `defaultBindings` in the registry, so a user cannot change one without a rebuild. Also needs `send-text`, `send-key`, multiple actions per stroke, and unmap. |
| Hints overlay | M | Numbered or lettered labels over URLs, paths, hashes, and line numbers, to open or copy without touching the screen. The existing URL regex is the base. |
| Scrollback search | M | Native search overlay, or hand a formatted transcript to `less` in a new window. |
| Multiple cursors protocol | M | Extra cursor objects and their rendering. Less invasive than multicell text. |
| Kitty graphics protocol, Tier 1 | L | The biggest remaining protocol. APC is still swallowed and `ApcTest` pins that as the current contract. Query, chunked PNG transmit, display at cursor, delete, per-session memory caps, async decode. |
| Ligature and cluster-aware shaping, font fallback ranges | L | Needs a grapheme, cursor, and selection test suite first: Arabic, Indic, combining marks, ZWJ emoji, Nerd symbols, programming ligatures. |
| Modal keybind maps | L | vim-like mode stacks. The study asks for latency and pass-through to be proven solid first, which the chords item above is the natural place to prove. |

## Deferred on purpose

Not "forgotten" — each has a stated reason to wait.

| Item | Size | Why it waits |
|---|---|---|
| Kitty graphics Tier 2 and 3 | L–XL | Reusable images, multiple placements, z-index, crop, Unicode placeholders, animation. Only after Tier 1 has been proven against real clients such as `kitten icat` and yazi. |
| Explicit OpenGL ES / Vulkan renderer | XL | Benchmark-gated by the study. Do not start until the counters above show that frame time, allocation, or power cannot be fixed within Canvas. It also owes accessibility, IME positioning, and selection snapshots, which are part of the work rather than follow-ups. |
| Multicell / variable-sized text | XL | Every buffer mutation and reflow path would need multirow ownership metadata. Wait until a concrete Android workflow depends on it. |
| Desktop notification protocol | M | Needs runtime notification permission, channels, background restrictions, and intent validation to be designed together. |
| File transfer over TTY | M | Security sensitive. Android share and SAF flows may simply be better UX. |
| Colour stack, palette notifications, pointer shapes, unscroll | M | Parser and state work with no current demand. Prioritize when an application asks. |
| Kittens and plugin scripts | M | Do not embed kitty's Python app API. `launcherctl` plus the registry already lets a shell script in any language drive the app. |
| Multiple Android top-level windows as a core abstraction | — | The study rejects it outright: on a phone, tabs and panes inside one Activity are predictable and OS-managed multi-window is not. |

## Loose ends inside finished slices

Small, deliberate omissions recorded where they happened. Listed here so they are not lost.

- The terminal action sheet is still hand-curated rather than generated from the registry. That was a
  product decision, not an oversight: auto-generating all 44 UI tools into it would bury the common
  actions. See `action-registry-terminal-actions.md` slice 7.
- `appearance.set_wallpaper` is registered and advertised but was never invoked in a device pass,
  because it launches an external picker and crop flow. Exercise by hand.
- The OSC 8 link pool is not garbage collected when the last cell referencing a link scrolls away.
  Bounded, so it degrades rather than grows without limit. See `kitty-protocol-features.md` slice 3.
- The keyboard protocol reports the shifted alternate key but not the base-layout one: Android
  exposes no PC-101 physical mapping to derive it from.
- `:app:testDebugUnitTest` fails 48 tests across 12 classes for environmental reasons — loopback HTTP
  and an unmocked `XmlPullParserFactory`. Compare against that baseline rather than expecting green.
  Fixing the harness so those tests can run is itself unclaimed work.
