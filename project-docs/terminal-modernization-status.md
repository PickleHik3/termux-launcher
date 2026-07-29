# Terminal modernization status

Status: delivered feature record, updated 2026-07-28.

This is the engineering entry point for the Kitty-inspired terminal work. The public, task-oriented
documentation is [`../docs/en/Terminal_Modernization.md`](../docs/en/Terminal_Modernization.md).
The public guide is authoritative for user-visible commands and configuration; the plan records
below are authoritative for design rationale, implementation details, verification, and remaining
scope.

## Delivered system

| Area | Delivered behavior | Detailed record |
|---|---|---|
| Action surface | One registry for palette, keybindings, LauncherCtl/MCP schemas, risk, requirements, and dispatch; searchable palette; conditional shortcuts; chords; modal maps; hints; scrollback search | [`plans/action-registry-terminal-actions.md`](plans/action-registry-terminal-actions.md) |
| Terminal hierarchy | Session → window → unlimited recursive horizontal/vertical pane tree; app-owned window strip; directional focus, resize, close, switching, and single-pane compatibility mode | [`plans/split-panes.md`](plans/split-panes.md) |
| Workspaces | Versioned, owner-only, atomically saved hierarchy definitions; append/replace restore; safe CWD restore; separately gated command capture/execution | [`plans/durable-workspaces.md`](plans/durable-workspaces.md) |
| Pane layouts | `stack`, `grid`, `tall`, `fat`, `horizontal`, and `vertical`; equalize; geometric rotation; move focused pane to an outer edge | [`plans/automatic-pane-layouts.md`](plans/automatic-pane-layouts.md) |
| Session browser | Searchable session/window/pane hierarchy; create, activate, clone with CWD, rename, close, and save workspace | [`plans/session-browser.md`](plans/session-browser.md) |
| Terminal protocols | Extended underlines and color, safe OSC 8, cursor trail, OSC 133, Kitty keyboard protocol, multiple cursors, Kitty graphics Tier 1 | [`plans/kitty-protocol-features.md`](plans/kitty-protocol-features.md) |
| Fonts and shaping | Native-font compatibility, four real faces, grapheme-aware Canvas shaping, symbol maps, ligature policy, OpenType features, variable axes, and bounded metrics | [`plans/fonts-and-shaping.md`](plans/fonts-and-shaping.md) |
| Robustness and observability | Parser size limits and fuzzing, renderer/window timing, allocation/GC counters, and a non-focusable key inspector | [`plans/kitty-protocol-features.md`](plans/kitty-protocol-features.md) |

The original feasibility study is
[`../../../kitty-to-termux-launcher-feasibility-study.md`](../../../kitty-to-termux-launcher-feasibility-study.md).
The one authoritative list of unfinished work is [`plans/backlog.md`](plans/backlog.md).

## User contracts

All terminal-modernization user files live under `~/.termux/` in both package editions:

| Path | Contract |
|---|---|
| `~/.termux/termux-launcher-bindings.conf` | Optional binding/chord/modal-map overlay |
| `~/.termux/fonts.conf` | Optional advanced faces, symbol maps, shaping policy, features, axes, and metrics |
| `~/.termux/font.ttf` | Existing native Termux regular-font contract, retained when `fonts.conf` is absent |
| `~/.termux/font-italic.ttf` | Existing optional italic-font contract, retained when `fonts.conf` is absent |
| `~/.termux/shell-integration/termux-launcher.bash` | App-managed, user-opt-in Bash OSC 133 integration |
| `~/.termux/shell-integration/termux-launcher.zsh` | App-managed, user-opt-in zsh OSC 133 integration |
| `~/.termux/workspaces/<name>.json` | Versioned durable workspace definitions |

`termux-reload-settings` reloads the binding and font candidates through the existing settings path.
Candidates are parsed and validated before being applied; valid binding lines survive invalid peers,
while font faces/settings fall back safely at their own error boundaries.

The remote surface remains the authenticated LauncherCtl `/v1/agent/execute` endpoint and MCP tool
registry. The palette intentionally omits actions that need a required argument because it does not
yet have an argument-entry UI.

## Compatibility decisions

- No user configuration preserves existing Termux font and legacy keyboard behavior.
- Single-pane compatibility mode disables the pane/window layer and restores conditional legacy
  shortcuts such as `Ctrl+Alt+V` for paste.
- Protocol state belongs to the emulator/session so Activity recreation does not erase it; Android
  opening policy and appearance preferences remain in the app layer.
- Canvas remains the renderer. Android ICU grapheme boundaries plus `Paint.getTextRunAdvances()` met
  the fixed-cell shaping acceptance suite, so a HarfBuzz port is not justified.
- Arabic and other complex scripts are shaped in logical terminal order. Bidi paragraph layout is
  explicitly not promised.
- `narrow_symbols`, symbols occupying following cells, and arbitrary multicell text remain one
  deferred cell-ownership project rather than renderer-only shortcuts.
- Kitty layouts are currently one-shot topology transforms. Retained automatic layout management
  and `next_layout` remain backlog work.

## Verification record

The feature plans record each test class, device run, cleanup, and discovered regression. The
repeatable scripts are under [`verification/`](verification/), including terminal actions,
keybindings, protocol rendition, shell integration, escape-parser fuzzing, and device/API smoke
checks.

The completion state includes:

- passing `terminal-emulator` and `terminal-view` JVM suites for the delivered protocol/render work;
- Android 16 instrumentation for shaping, grapheme/buffer integrity, font maps/features/axes/metrics,
  and renderer geometry;
- physical-device passes for panes/windows, workspaces, layouts, session browser, Kitty graphics,
  key inspection, shell integration, fonts, and performance counters; and
- bounded error handling and cleanup checks for malformed fonts, hostile workspace definitions,
  overlong escape sequences, graphics payloads, and link-pool saturation.

The app-wide `:app:testDebugUnitTest` task passes: 592 tests across 92 classes, zero failures.
Expect green and treat any failure as a regression.

This replaces a long-standing "48 environmental failures across 12 classes" baseline, which was a
misdiagnosis worth recording so it is not reintroduced. Nothing in it was environmental, and none of
it was WSL-specific — all 48 failed identically on any host JVM:

- **36 failures, 6 classes** were attributed to the host refusing loopback HTTP. Loopback was never
  the problem. `LauncherCtlApiServer.start()` bound its socket successfully and then threw from
  `writeClientConfig()`, which could not create `/data/data/com.termux/files/home/.launcherctl` off
  device; the `catch` closed the socket it had just opened, so clients saw `ConnectException`. Config
  and shell-helper installation is now non-fatal — the listener survives an unwritable Termux home,
  which was also a real on-device robustness bug.
- **3 failures** were `IconPackXmlParserTest` calling `XmlPullParserFactory.newInstance()` on the
  bare JVM classpath, where it is an android.jar stub. Robolectric was already a test dependency; the
  class simply was not running under it.
- **9 failures, 5 classes** were genuine assertion failures that the "environmental" label hid for
  as long as it stood: a default that had changed (`%`, not `/`), a hardcoded model-catalog count, a
  layout that moved its views into a `FrameLayout`, an unlaid-out `SuggestionBarView` that renders
  nothing, and embedding-only models becoming non-loadable by design.

The lesson is the one the backlog already records for duplicated lists: an accepted-failure baseline
launders real regressions. Prefer a green suite plus explicit skips over a failure count.

## Documentation maintenance

When a terminal feature changes:

1. Update the public guide if commands, configuration, defaults, safety, or limitations changed.
2. Update the owning plan record with implementation rationale and verification evidence.
3. Update `plans/backlog.md` only for unfinished scope; do not duplicate a second backlog here.
4. Keep registry tool names and arguments aligned with the installed `launcherctl tools` schema.
5. Do not describe a planned feature as available until it has implementation and verification
   evidence in its owning record.
