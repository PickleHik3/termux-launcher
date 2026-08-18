# Kitty-to-Termux Launcher Feature Feasibility Study

**Prepared:** 2026-07-27  
**Kitty source reviewed:** `/home/amal/kitty`, Kitty 0.48.1, commit `a5b1c37ec337dc605596dbab693e413e48cef8a2` (2026-07-27)  
**Target source reviewed:** `/home/amal/termux-launcher/app/termux-launcher`, commit `1a629b12b12eeeec02f32878fc447cbc578edd61` (2026-07-27)  
**Target platform:** Android 8.0+ (`minSdkVersion=26`), Java 11, Android Canvas UI, with an existing NDK/JNI module and builds for ARM and x86 ABIs  
**Revised:** 2026-07-28 after implementation. Amendments: the action-registry recommendation now extends the existing `LauncherToolRegistry` instead of creating a peer registry; styled underlines are split into style (fits the current `TextStyle` bit budget) and decoration color (needs a side table); Phase 1 re-estimated for `TermuxActivity.java` extraction cost; a fork-specific constraints section was added; the status table now reflects the completed input, multiplexer, graphics Tier 1, multiple-cursor, measurement, and parser-hardening slices.

## Implementation status (2026-07-27)

Not part of the assessment; a pointer to what has since been built, so the roadmap below is
not read as untouched.

| Phase | State | Record |
|---|---|---|
| 0 — measurement and contracts | **done** | action registry records plus `kitty-protocol-features.md` slices 10–11 |
| 1 — registry extension and command palette | **done** | `project-docs/plans/action-registry-terminal-actions.md` slices 1–4 |
| 2 — unified bindings and key diagnostics | **done** | same file, slices 3, 5–13, plus the key inspector in `kitty-protocol-features.md` slice 8 |
| 3 — multiplexer completion and workspaces | **done**; retained automatic-layout policy is a recorded follow-up | `project-docs/plans/{durable-workspaces,automatic-pane-layouts,session-browser}.md` |
| 4 — high-return protocol/render upgrades | **done** except cluster-aware shaping/font fallback | `project-docs/plans/kitty-protocol-features.md` slices 1–5 and 12 |
| — key diagnostics (`show-key` equivalent) | **done** | same file, slice 8 |
| 5 — modern input and graphics | keyboard protocol and graphics Tier 1 **done**; Tier 2–3 deferred | same file, slices 6 and 13 |
| 6 — renderer decision | **not started**, still benchmark-gated; Phase 0 counters are available | — |

What remains of the roadmap below, with sizes and the reason each deferred item waits, is tracked in
`app/termux-launcher/project-docs/plans/backlog.md`.

One finding from building Phase 4 that corrects this document: the free bit window in the
packed style `long` is bits 11–**14**, not 11–15. Bit 15 is already `BITMAP`. Four bits, of
which three went to the underline style.

## Executive conclusion

A direct port of the Kitty application is neither practical nor desirable. Kitty assumes a desktop window system, GLFW, desktop OpenGL, a Python-driven application model, and Kitty-specific C/Go internals. The Android app uses Activity/View lifecycle, Android input and IME APIs, Java terminal state, Canvas rendering, and Termux service-owned PTYs. These are different application architectures.

Porting the *behavior and protocols* is nevertheless highly feasible. The most valuable requested features fall into three groups:

1. **High-value, low-to-medium-risk Android-native work:** a command palette, a centralized action registry, much broader custom key bindings, a key-event HUD/diagnostic screen, session/window polish, saved workspace definitions, OSC 8 hyperlinks, styled underlines, cursor movement animation, and shell integration.
2. **Already substantially present:** tmux-like session → window → pane management, recursive splits, window switching, session switching, persisted pane topology across Activity recreation, true color, bold/italic/underline/strike rendering, Sixel images, iTerm2 inline images, embedded keyboard pressed states, and animated swipe-up secondary-key popups.
3. **Feasible but architectural projects:** Kitty graphics protocol, Unicode image placeholders, Kitty keyboard protocol, reliable ligature/cluster-aware shaping, variable-sized/multicell text, multiple cursors, and a Kitty-like GPU glyph renderer. These require changes to terminal data structures and renderer contracts, not just new UI.

The recommended strategy is to converge on a single shared **action registry** first. The fork already ships one: `LauncherToolRegistry` (551 lines) with `name`, `description`, JSON schema, `ToolRisk`, `requiresConfirmation`, and `ToolExecutor`, wired to `/v1/agent/tools`, `/v1/agent/execute`, an MCP bridge, and 14 registered tool IDs. Extend that registry with UI-facing metadata rather than creating a second, parallel one; the command palette, the key binding resolver, and the agent/MCP surface must all consume the same registry. Next, finish and expose the existing multiplexer. Then add terminal protocol improvements incrementally on the current Canvas renderer. Only replace the renderer with an explicit OpenGL ES/Vulkan renderer after benchmarks demonstrate that the Canvas path is the limiting factor.

The strongest near-term product is therefore not “Kitty running on Android.” It is “a Termux-native Android terminal with Kitty-inspired discoverability, input configurability, project workspaces, and selected modern protocols.”

## Scope and interpretation

This study examined the local source, not only feature lists. It traced:

- Kitty's command palette data collection and action execution.
- Kitty mapping parsing and runtime dispatch, including chords, modes, aliases, conditions, fallbacks, timeouts, and pass-through behavior.
- Kitty's `show-key` diagnostic.
- Kitty's OS-window/tab/window/session and layout model.
- Kitty's OpenGL renderer, font shaping model, graphics protocol, Unicode placeholders, text sizing, styled underlines, and related extensions.
- The launcher's current Android UI, embedded keyboard, hardware shortcut handling, Termux terminal emulator, renderer, images, and multiplexer code.

“Port” is used in three distinct ways:

- **Direct source port:** compile/adapt Kitty source for Android. Usually not recommended.
- **Behavioral port:** recreate the user-facing behavior using Android components. Recommended for UI and session features.
- **Protocol implementation:** implement Kitty's documented escape-code protocol in the Termux emulator. Recommended for terminal interoperability.

Effort estimates assume one experienced engineer familiar with this fork and include focused unit/instrumentation tests, but not a large UI redesign or a long public beta:

- **S:** 2–5 engineering days
- **M:** 1–3 engineering weeks
- **L:** 4–8 engineering weeks
- **XL:** 2–6 engineering months

## Architecture comparison

### Kitty

Kitty is a terminal application and window manager written in C, Python, and Go. It renders its UI and terminal content with OpenGL rather than a conventional widget toolkit. Its terminology is:

```text
Kitty process
└── OS window (desktop top-level window)
    └── tab
        └── kitty window (a terminal pane)
```

A Kitty session file can construct multiple OS windows, tabs, layouts, terminal windows, commands, working directories, titles, environment variables, focus state, and split geometry. The same application owns both terminal processes and the complete hierarchy.

### Current Termux launcher fork

The fork has already evolved beyond stock Termux's flat list of terminal sessions:

```text
Android TermuxActivity
└── WSession (named tmux-style session; drawer item)
    └── ordered Window list
        └── recursive binary pane tree
            └── TerminalSession leaf (PTY/shell)
```

`WSession` is defined in `TermuxActivity.java` and owns an ordered list of windows. `TerminalPaneController` owns arbitrary nested split trees. Only the active window is rendered; off-screen windows keep their shells alive. It supports:

- Horizontal and vertical recursive splits.
- Directional pane focus and resize.
- Touch divider resizing with cell-grid snapping.
- Active-pane borders.
- Pane swapping, maximize/restore, and close controls.
- New/close/previous/next window operations.
- New/close/rename/switch session operations.
- Saving the split topology and active handles into an Android `Bundle`, then reconnecting it to still-running service sessions after Activity recreation.
- A window bar and drawer-visible sessions.

This is structurally close to tmux and already covers much of Kitty's *tab and pane* value. Android has no useful equivalent to multiple freely positioned desktop OS windows inside one phone app. Android activities in multi-window mode are OS-managed, device-dependent, and unsuitable as the primary terminal hierarchy. The correct mapping is therefore:

| Kitty concept | Recommended Android mapping |
|---|---|
| OS window | Android task/activity only when explicitly launching a separate app instance; otherwise omit |
| Tab | Current fork's `Window` (one visible pane tree) |
| Kitty window | `TerminalSession` pane leaf |
| Named Kitty project/session | Current `WSession`, extended with durable workspace definitions |
| Overlay window/kitten | Dialog, bottom sheet, transient full-screen View, or terminal overlay pane |

## Overall feasibility matrix

| Feature | Current target baseline | Feasibility | Effort | Recommendation |
|---|---|---:|---:|---|
| Searchable command palette | Small hard-coded terminal action sheet; actions scattered through Activity/client; `LauncherToolRegistry` already provides an agent/MCP-facing registry | **High** | M | Build natively on top of the extended `LauncherToolRegistry` |
| Simple custom hardware mappings | Four configurable `Ctrl+key` session shortcuts plus fixed `Ctrl+Alt` actions | **High** | M | Replace special cases with a generic resolver |
| Chords, modes, timeouts, combined actions | Not generalized | **High** | L | Add after simple mappings; reuse same resolver for physical and embedded input |
| Conditional mappings | Terminal titles/CWD/foreground process are available or derivable | **Medium-high** | M–L | Start with session/window/pane/title conditions; avoid arbitrary process introspection initially |
| Embedded keyboard macros and custom layout | Already present, including macro dispatch and modifiers | **Already strong** | S–M polish | Integrate host actions and binding display |
| Pressed-key visual feedback | Embedded keyboard pressed glow and swipe popup already present | **Already present** | S | Add preference/polish only |
| Kitty-style `show-key` diagnostic | No equivalent diagnostic UI; input logging exists | **High** | S–M | Add a native key-event inspector/HUD |
| Session/window/pane management | Recursive split/window/session model already present | **Already strong** | M–L parity polish | Extend rather than replace |
| Declarative project/workspace sessions | Only Android recreation state; no Kitty-like user file format | **High** | M–L | Add JSON/TOML workspace files and save/load UI |
| Layouts: stack, grid, tall, fat, horizontal, vertical | Arbitrary splits and maximize exist; no automatic layout algorithms | **High** | M | Add layout strategies over existing tree model |
| Remote control | `launcherctl` already exposes a registry, risk gating, execute endpoint, and MCP bridge; terminal/session/pane actions are simply absent from it | **High** | S–M | Register terminal actions in the existing registry; no new control plane |
| True color and ordinary SGR styles | Present | **Already present** | — | Preserve and test |
| Curly/dotted/dashed/double underlines | Colon subparameter parsing already exists; style collapsed to one boolean bit | **High** | S–M | Best first renderer enhancement; fits the free `long` bits |
| Colored underlines (SGR 58) | Color fully parsed into `mUnderlineColor`, never stored per cell | **High** | M | Needs a side decoration table; do after underline style |
| OSC 8 semantic hyperlinks | Tap URL regex exists; no per-cell OSC 8 model was found | **High** | M | Implement before advanced graphics |
| Ligatures and robust shaping | Android `drawTextRun` is used, but no explicit glyph/cluster model or feature control | **Medium-high** | L | Improve Canvas shaping first; HarfBuzz only if tests require it |
| Per-codepoint font fallback / symbol maps / variable-font controls | Android fallback is mostly platform-driven; one regular and one italic file | **Medium** | L | Add font-chain/range configuration incrementally |
| Sixel | Parser, bitmap storage, scroll integration, and drawing present | **Already present** | M hardening | Fuzz, cap resources, fix performance rather than replace |
| iTerm2 inline images | Present through OSC 1337 image decoding | **Already present** | M hardening | Retain for compatibility |
| Kitty graphics protocol MVP | APC is currently swallowed; cell bitmap machinery exists | **High, but substantial** | L | Implement query + PNG transmit/display + delete first |
| Full Kitty graphics placements, z-index, animation, shared memory, Unicode placeholders | Data model lacks reusable images/placements/layers | **Medium-high** | XL | Stage separately after MVP |
| Multicell/variable-sized text protocol | Cell model lacks required multirow ownership/metadata | **Medium** | XL | Defer unless a target app requires it |
| Kitty keyboard protocol | Android supplies rich key events, but encoder/mode negotiation is absent | **High** | L | High-value protocol project after key resolver |
| Cursor trail | Cursor coordinates already available; Canvas supports animation | **High** | S–M | Implement as a View/render overlay first |
| Explicit GPU glyph-atlas renderer | Current renderer is Android Canvas, not Kitty's GL cell pipeline | **Technically high, product-risk high** | XL | Benchmark-gated, not a prerequisite |

## Command palette

### What Kitty actually implements

Kitty's palette is a searchable terminal overlay. Python gathers configured keyboard modes, resolved action names, categories, help text, aliases, unmapped actions, and mouse mappings into JSON. A Go TUI filters and renders the collection. When the user selects an entry, the overlay closes and Python asks Kitty's central dispatcher to execute the selected action against the original window.

Important design properties are:

- The palette is generated from the same action/mapping metadata used by actual dispatch.
- It includes mapped and unmapped actions.
- It groups actions by category and custom keyboard mode.
- It searches shortcut, action, and category fields with typo-tolerant ranking.
- It does not contain a second independent set of action implementations.

Relevant Kitty source: `docs/kittens/command-palette.rst`, `kittens/command_palette/main.py`, `kittens/command_palette/main.go`, and `kitty/actions.py`.

### Target baseline and gap

The fork has a Material terminal action sheet in `TermuxActivity.showTerminalActionSheet()`. It exposes URL selection, transcript sharing, wallpaper controls, tuning, settings, reset, and kill. Multiplexer and keyboard actions live elsewhere in `TermuxActivity` and `TermuxTerminalViewClient`. This is workable UI, but it is not a discoverable action system.

The primary requirement is therefore not a search dialog. It is a registry — and the fork already has one.

#### The registry that already exists

`app/src/main/java/com/termux/launcherctl/LauncherToolRegistry.java` provides:

```java
ToolMetadata  { name, description, schema(JSONObject), risk, requiresConfirmation, executor }
ToolRisk      { LOW, MEDIUM, HIGH, CRITICAL }              // confirmation gating
ToolExecutor  { LAUNCHER, NOTIFICATIONS, MEDIA, SYSTEM, INTENT, MEMORY, EVENTS, MCP, USER }
```

It is consumed by `LauncherToolExecutionHandler` (`LauncherCtlApiServer.java:2978`), the `/v1/agent/tools` and `/v1/agent/execute` endpoints, and the `launcherctl mcp` stdio bridge. It registers 14 dotted-lowercase tool IDs today: `apps.launch`, `apps.search`, `capabilities.get`, `events.tail`, `intent.open`, `media.now_playing`, `memory.search`, `memory.write`, `notifications.recent`, `notifications.search`, `notifications.since`, `notifications.stats`, `system.resources`, `user.confirm`.

This is already the metadata model a command palette needs, minus its presentation layer. Building a second `TerminalAction` registry beside it would produce exactly the duplicated action logic this section warns against, one level higher: two registries, two dispatchers, two confirmation models, and divergent action naming.

#### What to add to it

Extend `ToolMetadata` with UI-facing, optional fields:

```java
ToolMetadata {
    // existing
    name, description, schema, risk, requiresConfirmation, executor,
    // added
    category,               // palette grouping and section headers
    titleResource,          // localized short label
    descriptionResource,    // localized help text; `description` stays the agent-facing string
    defaultBindings,        // key strokes, consumed by the binding resolver
    availability            // predicate(AppState) -> enabled/disabled + reason
}
```

Then run two projections over one registry:

- **Agent/MCP projection** — the existing `toInternalJson()`/OpenAI-schema view. Unchanged behavior.
- **UI projection** — title, category, binding chip, icon, disabled reason. Consumed by the palette, the curated action sheet, and the binding editor.

Terminal actions must follow the established dotted convention (`pane.split_vertical`, `session.rename`, `window.close`), not a new scheme. New app/terminal actions should be registered with an executor value covering the terminal hierarchy; add `TERMINAL` to `ToolExecutor` rather than overloading `LAUNCHER`.

The registry should own only app/terminal actions, not shell commands from arbitrary untrusted sources. Both the command palette and key resolver query it. The existing action sheet can also be generated from a curated subset. Because `launcherctl` consumes the same registry, remote control of the terminal hierarchy comes for free instead of being separate Phase 3 work.

### Android implementation recommendation

Use a full-screen Material dialog or bottom sheet with an `EditText`/`SearchView` and `RecyclerView`. A native UI is preferable to running Kitty's Go TUI because it:

- Works naturally with touch, TalkBack, rotation, large fonts, and the embedded/system IME.
- Can display icons, categories, binding chips, disabled reasons, and destructive confirmations.
- Avoids launching a temporary PTY process and redirecting key events.
- Fits existing Material dialogs and search surfaces already in this app.

Reuse Kitty's *ranking behavior*, not its UI code. The launcher's existing fuzzy ranking engine may also be reusable after decoupling it from app-launch results.

### Feasibility verdict

**High.** The registry itself already exists, so the metadata work is an extension rather than a build. The palette UI is 1–2 weeks.

The dominant cost is not the palette or the registry: it is extracting user-facing operations out of `TermuxActivity.java`, which is 9471 lines and also owns `WSession`, the action sheet (`:7514`), and pane save/restore (`:7897`). Every extraction touches code shared with upstream Termux and therefore adds rebase surface. Budget more than the naive 2–4 weeks for full extraction, or scope the first pass to registering *new* terminal actions plus a thin adapter over the existing sheet, and defer bulk extraction to a follow-up.

The main risk is no longer building the palette before a registry — it is building a *second* registry beside `LauncherToolRegistry`.

## Custom key bindings

### Kitty capability

Kitty mappings support:

- Modifiers plus Unicode, functional, or native keys.
- Arbitrary action arguments.
- Multiple actions on one binding.
- Multi-key sequences such as `Ctrl+F > 2`.
- User-defined modal maps with mode stacks.
- Timeouts, pass-through, ignore, beep, and exit policies.
- Conditional mappings based on the focused terminal's title or variables.
- Shifted/physical-US fallbacks for non-Latin layouts.
- Aliases, unmapping, send-text, send-key, scripts, and remote control.

The core runtime is a mode stack and mapping trie/state machine in `kitty/keys.py`; it is not inseparable from OpenGL.

### Target baseline

The target currently has three separate input/configuration concepts:

1. Four configurable session shortcuts parsed strictly as `Ctrl+<something>`.
2. Fixed `Ctrl+Alt(+Shift)` multiplexer bindings in `TermuxTerminalViewClient`.
3. A very capable embedded keyboard that can emit characters, Android key events, modifiers, strings, macros, sliders, and host editing/layout events.

This means the app already receives sufficient physical and virtual key information. The missing piece is one policy layer that decides whether an event invokes an app action or is encoded to the PTY.

### Recommended design

Introduce a `KeyBindingResolver` before terminal encoding:

```text
Android physical KeyEvent ─┐
                           ├─> normalized KeyStroke -> binding resolver
Embedded keyboard value ──┘                         ├─ app ActionRegistry
                                                     └─ PTY key/text encoder
```

The normalized stroke should preserve:

- Android key code and scan code.
- Unicode code point.
- Ctrl/Alt/Shift/Meta state and lock state.
- Down/repeat/up event type.
- Physical versus embedded/IME origin.
- A US-position fallback where Android's `KeyCharacterMap` permits it.

Store bindings in a human-editable file under Termux home or app preferences. A compact Kitty-inspired syntax is reasonable, but do not promise complete `kitty.conf` compatibility because action names and platform keys differ. For example:

```text
map ctrl+shift+p command_palette
map ctrl+alt+v split vertical
map ctrl+k>ctrl+s workspace save
map --mode panes h focus left
```

Implement in layers:

1. Simple one-stroke app actions and unmapping.
2. Arbitrary send-text/send-key and combined actions.
3. Chords with timeout and a visible pending-chord indicator.
4. Modes and conditional mappings.
5. Config editor, diagnostics, and palette integration.

### Android constraints

- Android reserves or intercepts some keys (Home, global shortcuts, system volume behavior). An app cannot guarantee receipt of every physical chord.
- IMEs primarily commit text and do not expose all physical press/release semantics. Embedded keys and hardware keyboards can be fully controlled; third-party soft keyboards cannot.
- Alt/Meta behavior varies by keyboard layout and manufacturer.
- Accessibility key events and game-controller sources need explicit policy.
- App bindings must take precedence only when matched. Unmatched events should reach the terminal with no noticeable latency.

### Feasibility verdict

**High.** Simple generic mappings are M effort. Kitty-like chords/modes/conditions are L effort, but no renderer or PTY redesign is required. This is one of the best ports to pursue.

## Pressed-key popups and `show-key`

The local Kitty checkout does **not** show evidence of a general GUI popup above every pressed key. Its `show-key` feature is a diagnostic terminal program. In Kitty keyboard mode it enters the full keyboard protocol and prints the key, modifiers, press/repeat/release type, generated text, CSI encoding, shifted key, and alternate key. In legacy mode it prints received bytes.

Two interpretations should therefore be handled separately.

### Touch-key visual preview

The fork already has this behavior in stronger Android-native form:

- Pressed and latched key visual states.
- Per-key glow/halo feedback.
- Swipe-up popup keys.
- A floating `PopupWindow` whose secondary glyph follows the finger and commits past a threshold.

This exists in `ExtraKeysView`, particularly the pressed-state handling and `armBubbleTravel()` path. The embedded Unexpected Keyboard view also tracks pointers and swipes.

If the desired behavior is an enlarged keycap above every ordinary embedded-key press, it is S effort: reuse the current popup/glow plumbing, make it preference-controlled, clamp it around cutouts/edges, and suppress it for fast slides or privacy-sensitive keys.

### Key-event HUD / diagnostic inspector

A native Android equivalent of `kitten show-key` is highly feasible and more useful during development than compiling the Go kitten for Android. It should display:

- Android key code, scan code, source, device ID, repeat count, and action.
- Unicode code point and modifiers.
- Which app binding matched and its resolution path.
- The exact legacy or Kitty-protocol bytes sent to the PTY.
- Embedded keyboard `KeyValue`/macro expansion.

It can be a palette action opening an overlay, optionally with a transient “Ctrl+Alt+V → split vertical” HUD. This is S–M effort and should be developed alongside custom bindings.

## Sessions, tabs, windows, and panes

### What can be retained

The existing `WSession` and `TerminalPaneController` should remain the source of truth. Replacing them with Kitty's Python `TabManager`/layout code would discard Android lifecycle integration and recent fork-specific work.

Already implemented behavior includes the hardest foundation:

- Arbitrary nested binary splits.
- Live independent PTYs in each pane.
- Multiple off-screen windows within a session.
- Multiple named sessions.
- Focus, resize, maximize, swap, close, and switching.
- Reconnection of view topology to service-owned terminals after Activity recreation.

### Parity gaps worth implementing

1. **Durable declarative workspaces.** Current `Bundle` serialization restores UI state only while corresponding `TerminalSession` handles still exist. Kitty session files can recreate commands, CWDs, environment, titles, layouts, and focus from scratch. Add a user-owned workspace format with versioned schema.
2. **Save current workspace as definition.** Capture the session/window/pane topology, CWD, titles, ratios, and optionally foreground command. Restoring arbitrary foreground programs is unsafe and often impossible; default to shell+CWD and make command capture opt-in.
3. **Automatic layouts.** Build stack, grid, tall, fat, horizontal, and vertical strategies that transform or temporarily arrange the existing pane tree. The current arbitrary split layout is already the most flexible case.
4. **Window reordering and richer movement.** Drag/reorder window chips; rotate splits; move a pane to an edge; equalize ratios.
5. **Session browser.** Search by name/CWD/foreground label, show windows/panes, and expose create/clone/rename/save/close through the command palette.
6. **Clone with CWD.** This is already close to current split/new-window behavior; expose consistent actions for new pane/window/session with current CWD.
7. **Crash/process-death semantics.** Distinguish UI recreation from TermuxService/process restart. Durable definitions should recreate workspaces; they cannot resurrect an arbitrary dead Unix process.

### Android-specific decision

Do not model Kitty OS windows as freely floating app windows. On phones, tabs/windows/panes inside one Activity are predictable. Optional separate Android tasks can be added later for tablets/DeX, but must not be required for session correctness.

### Feasibility verdict

Core management is **already present**. Kitty-level project session definitions and layout polish are M–L effort. This area can reach excellent practical parity without changing the terminal emulator.

## Modern terminal drawing and protocol features

### Current renderer reality

Kitty caches glyph alpha masks and submits cell data through an OpenGL pipeline. Its renderer explicitly handles a glyph atlas, HarfBuzz shaping, GPU cell data, decorations, graphics layers, background images, and cursor effects.

The target `TerminalRenderer` walks visible rows, groups text by style, uses Android `Canvas.drawTextRun()`, and draws Sixel/iTerm bitmap slices into cell rectangles. Android may hardware-accelerate the Canvas, but this is not the same architecture as Kitty's explicit glyph-atlas renderer. The current terminal cell style is a packed `long` with foreground, background, and a limited set of effect bits.

The correct approach is to separate protocol/data-model work from renderer-backend work. Most visible modern features can be added to Canvas first.

### Existing rendering capabilities

The target already supports or visibly renders:

- Indexed and 24-bit foreground/background colors.
- Bold, dim, italic, reverse, invisible, underline, strike-through, and three cursor shapes.
- Combining characters and double-width characters using the terminal's `WcWidth` model.
- Android text shaping through `drawTextRun` at a basic run level.
- Sixel parsing into Android bitmaps, cell-linked scrolling, bitmap GC, and Canvas drawing.
- iTerm2 OSC 1337 inline images.
- Transparent/background surfaces and app wallpaper/blur effects outside terminal cell rendering.

### Styled and colored underlines

This is a particularly favorable port, and the parsing groundwork is further along than a feature-level comparison suggests.

Colon subparameter parsing is already implemented, not partial: `mArgsSubParamsBitSet` (`TerminalEmulator.java:304`) records which `mArgs[N]` came after a colon, is consumed at `:2316` and `:2340`, and is set during CSI parsing at `:2839`. SGR 58 is fully decoded in both truecolor and indexed forms (`:2408`, `:2421`, `:2440`). What is missing is storage and drawing: every nonzero underline style collapses into the single `CHARACTER_ATTRIBUTE_UNDERLINE` bit (`TextStyle.java:21`), `mUnderlineColor` is written in four places and read in none outside the emulator, and the renderer calls `Paint.setUnderlineText(boolean)` (`TerminalRenderer.java:292`), which cannot draw curly/dotted/dashed/double variants.

These are two separately sized features, because the bit budget differs sharply. The packed `long` layout is: attribute bits 0–10, background truecolor from bit 16, foreground truecolor from bit 40. The free window is bits 11–15 — exactly five bits.

**Underline style (S–M):** five bits comfortably holds the five Kitty variants plus room to spare, so style fits the existing `TextStyle` `long` with no new data structure. Work is: allocate the style field, map subparameters to it, preserve it through erase/copy/resize/reflow/transcript/selection, and draw decoration geometry with `Paint`/`Path` instead of `setUnderlineText`. Do this first — it is a self-contained proof that the style model can evolve.

**Decoration color (M):** a 24-bit color does not fit in five bits, so this is where a parallel decoration table or a redesigned style interning model becomes necessary. Preservation through reflow and export must then cover the side table as well as the `long`, which is the bulk of the effort.

**Verdict: High feasibility. Underline style S–M, decoration color M.** Splitting them lets the cheap half ship and prove the preservation test-suite before the storage redesign starts.

### Semantic hyperlinks (OSC 8)

The app currently detects URL-like text at tap time and presents URL selection based on extracted strings. That does not preserve OSC 8 link targets, IDs, or file/line URIs emitted by applications.

Add a hyperlink ID/URI pool and per-cell hyperlink reference, parse OSC 8 open/close, carry references through scrollback/reflow, hit-test by cell, and apply configurable underline/activation policy. Android Intent launching must validate schemes and require confirmation for risky targets.

**Verdict: High feasibility, M effort.** This yields immediate value for editors, compilers, `ls --hyperlink`, and remote shells.

### Ligatures, shaping, emoji, and fonts

Android's text stack already performs script shaping and `drawTextRun` can form glyph runs, so useful improvement does not require an immediate HarfBuzz port. However, Kitty-grade behavior needs more:

- Explicit mapping from glyph clusters back to cells for cursor, selection, and ligature breaking.
- Stable grapheme segmentation and width policy independent of OEM font surprises.
- Feature toggles such as `liga`/`calt` and per-font OpenType axes.
- Font fallback by codepoint range and separate bold/italic/bold-italic faces.
- Color emoji and bitmap/vector glyph handling without breaking cell metrics.

The current renderer sometimes scales an entire run to fit expected terminal columns. That preserves grid alignment but can distort shaped text and makes cluster-aware interaction harder.

Recommended sequence:

1. Add grapheme/cluster tests for Arabic, Indic scripts, combining marks, ZWJ emoji, Nerd symbols, and common programming ligatures.
2. Improve run boundaries and cursor-time ligature disabling using Android shaping APIs.
3. Add configurable font families/fallback ranges.
4. Introduce native HarfBuzz only if Android APIs cannot give deterministic cluster/feature control across supported API levels.

**Verdict: Medium-high feasibility, L effort** for a strong Canvas implementation; complete Kitty parity is larger.

### Kitty graphics protocol

Kitty graphics uses APC sequences (`ESC _ G ... ESC \`) and separates image data from placements. It supports chunked transmission, PNG/raw RGB/RGBA, file/shared-memory/direct media, image and placement IDs, cropping, scaling, pixel offsets, z-index, alpha blending above/below text, scrolling, deletion, queries, animations, virtual placements, relative placements, and Unicode placeholder cells.

The target currently consumes APC strings silently. `ApcTest` explicitly documents that a Yazi Kitty graphics query is swallowed and should receive an error only if support is later implemented. This is a clean compatibility baseline: implementing the protocol should start by changing APC handling only for the `G` namespace.

The existing bitmap infrastructure helps with decoding and cell anchoring, but it is not a Kitty placement model. `TerminalBitmap` images are represented by bitmap-marked cells and the renderer draws one source slice into each cell. Full Kitty graphics needs:

- An image store keyed by image ID with memory budgets and lifecycle.
- A placement store independent of text cells.
- Placement anchors that survive scrolling/reflow.
- Source/destination rectangles, offsets, z-order, alpha, and clipping.
- Responses written to the correct `TerminalSession` stdin.
- Incremental base64/chunk parsing with strict size/time limits.
- Async decode/upload without blocking terminal parsing or the main thread.
- Deletion and GC semantics.

Recommended protocol tiers:

**Tier 1 — capability and useful images (L, roughly 5–9 weeks):**

- Query action with correct immediate response ordering.
- Direct chunked PNG transmission.
- Transmit-and-display at the cursor.
- Cell-size `c`/`r`, aspect preservation, cursor movement policy.
- Image ID, simple replacement, delete, and memory caps.
- Foreground placement only.

**Tier 2 — placement correctness (L–XL):**

- Reusable image IDs and multiple placement IDs.
- Source crop, pixel offsets, z-index above/below text, alpha blending.
- Scrollback/reflow behavior and robust deletion.
- Raw RGB/RGBA.

**Tier 3 — ecosystem parity (XL):**

- Unicode placeholders and virtual placements.
- Relative placements.
- Animation frames/composition.
- File transmission with Android/Termux path policy.
- Shared memory only if a safe Android-compatible design is justified.

Shared memory and arbitrary file reads are security boundaries: a terminal program must not gain access to app-private or external files merely by naming a path. Restrict to the Termux sandbox, validate ownership/size, and prefer direct data initially.

**Verdict:** MVP is highly feasible. Full protocol parity is a major emulator/renderer project, not a small enhancement.

### Unicode placeholders

Unicode placeholders use private-use character U+10EEEE, diacritics for row/column, and foreground/underline colors to identify virtual image placements. Their advantage is movement through tmux/editors as ordinary text.

Implementing them requires the full image/placement store plus reliable preservation of the placeholder's combining sequence and colors. The current row/cell and `WcWidth` logic would need special handling, and selection/export must avoid leaking confusing placeholder text unintentionally.

**Verdict: Feasible only after Kitty graphics Tier 2; XL as part of full parity.**

### Variable-sized / multicell text

Kitty's text sizing protocol allows a grapheme or string to occupy a block spanning multiple columns and rows. Its screen model tracks ownership of every participating cell and defines detailed behavior for overwrite, erase, insert/delete, scrollback, cursor rendering, selection, and resize.

The target's `TerminalRow` and `TextStyle` do not contain corresponding multirow ownership metadata. Adding only a scaled draw call would be incorrect: editing and scroll operations could split a multicell object and corrupt the screen.

This needs:

- A multicell object/ID and per-cell origin/offset metadata.
- Updates to virtually every buffer mutation and reflow operation.
- Multiline cursor and selection behavior.
- Renderer scaling/alignment and transcript export.
- A large protocol conformance suite.

**Verdict: Technically feasible, XL effort, lower priority** unless a concrete Android workflow depends on it.

### Kitty keyboard protocol

This protocol fixes ambiguity in legacy terminal key encoding and can report shifted/alternate keys, text, modifiers, repeat, and release events through CSI-u sequences. Android `KeyEvent` contains much of the necessary information for hardware and embedded keyboards.

Work required:

- Parse push/pop/query keyboard-protocol mode escape sequences per terminal session.
- Track progressive enhancement flags in `TerminalEmulator`.
- Add a Kitty encoder alongside `KeyHandler`'s legacy encoder.
- Preserve press/repeat/release for physical/embedded input.
- Fall back carefully for IME committed text, which has no physical release event.
- Add terminfo/feature advertisement and conformance tests using a native `show-key` inspector.

**Verdict: High feasibility, L effort.** This is more valuable than a full renderer rewrite because modern editors/TUIs can immediately use it.

### Cursor trail and other visual effects

A Kitty-like cursor trail is a presentation feature, not a terminal protocol. Keep previous and current cursor rectangles and animate a rounded interpolating shape with a short decay. A Canvas overlay can implement it without changing terminal cells. Disable or reduce it under battery saver, when the Activity is not visible, and for large jumps if configured.

**Verdict: High feasibility, S–M effort.** An OpenGL renderer is unnecessary for the first implementation.

### Explicit OpenGL ES/Vulkan renderer

Android supports OpenGL ES and Vulkan, and this repository already builds native code for all major ABIs. A Kitty-like renderer is therefore technically possible, but Kitty's desktop GL/GLFW backend cannot simply be compiled into `TerminalView`.

A native renderer would need to own:

- A `SurfaceView`/`TextureView` or Compose-compatible surface lifecycle.
- EGL context loss, pause/resume, resize, split panes, and multi-surface synchronization.
- Glyph shaping/rasterization and atlas eviction.
- Cell/decorations/image buffers and partial damage.
- Text selection/accessibility snapshots back to Java.
- IME cursor positioning and screenshots.
- GPU-driver workarounds across vendors.

It could substantially improve throughput and effects, especially for many live panes and graphics, but introduces the largest maintenance burden. Android Canvas is often hardware accelerated already, so “GPU based” by itself is not proof of a user-visible win.

**Decision gate:** first add frame-time counters and benchmark rapid scroll, `cat` throughput, large Unicode output, Sixel/Kitty images, and 2/4 live panes on representative low/mid/high devices. Start a renderer replacement only if measured frame time, allocation, or power usage cannot be fixed within Canvas.

**Verdict: Technically feasible, XL effort and high regression risk. Defer.**

## Other Kitty feature families worth considering

These were not the primary request, but they fit the same modernization goal.

| Feature family | Android/Termux assessment |
|---|---|
| Shell integration (prompt marks, CWD, command start/end/output) | **High value/high feasibility.** Parse OSC 133-style marks and ship optional bash/zsh/fish scripts. Enables jump-to-prompt, command-output selection, better titles, and notifications. |
| Hints for URLs/paths/hashes/lines | **High.** Existing URL extraction is a base. Add numbered/letter overlays and actions to open/copy/insert. |
| Scrollback search/pager | **High.** Native search overlay or launch `less`/editor in a new window with formatted transcript. |
| Desktop notifications protocol | **Medium-high.** Android notifications are a natural backend, but runtime notification permission, channels, background restrictions, and intent security must be handled. |
| Multiple cursors protocol | **Medium.** Requires extra cursor objects and rendering, but less invasive than multicell text. |
| Color stack, palette notifications, pointer shapes, unscroll | **Medium-high.** Mostly parser/state work; prioritize based on application demand. |
| File transfer over TTY | **Medium and security-sensitive.** Android SAF/share flows and existing Termux file access may be better UX. Require confirmation and strict path policy. |
| Kittens/plugin scripts | **Medium.** Do not embed Kitty's Python app API. Expose stable `launcherctl`/local socket actions so shell scripts in any language can automate the app. |
| Remote control | **High.** Unify the current launcher control surface with the action registry and scope it to app UID/authenticated local clients. |
| Background images/opacity/blur | **Already strong at app-surface level.** Per-pane Kitty semantics may be added later; keep wallpaper rendering separate from terminal protocol images. |

## Android and Termux constraints that affect parity

### Lifecycle and process ownership

Android can destroy/recreate Activities independently of the Termux service. Views, EGL contexts, dialogs, and transient modes must be reconstructible. A saved layout can reconnect to a live PTY, but no terminal can resurrect an arbitrary Unix process after it has died. Workspace restore must clearly distinguish “reconnect” from “relaunch.”

### Input and IME

Physical `KeyEvent`s, embedded keyboard events, and IME text commits have different information. Only the first two can reliably expose press/repeat/release. Some global keys never reach the app. A binding editor must detect conflicts and state when a shortcut is unavailable on the current device.

### GPU and memory

Phones have tighter thermal and memory budgets than desktops. Image protocols need per-session and global byte/pixel caps, bounded chunk buffers, asynchronous decode, and eviction. Multiple visible panes multiply glyph/image work. Release GPU resources when surfaces stop, while keeping terminal state independent.

### Security

Terminal escape sequences are untrusted input. Graphics, hyperlinks, notifications, clipboard, file transfer, and remote control can cross Android security boundaries. All parsers need length limits and fuzz tests. External Intents, file paths, notification actions, and clipboard reads require validation or confirmation.

### Touch and screen size

Kitty defaults are keyboard/desktop oriented. On a phone, palette, pane controls, session switching, and hints should have first-class touch UI. More than two or three visible panes may be technically possible but not useful; automatic layouts should use width/height breakpoints.

### Accessibility

A native palette and Android View hierarchy can support TalkBack. A custom GPU renderer still needs an accessibility text/cursor/selection model. That work must be considered part of renderer replacement, not postponed indefinitely.

## Fork-specific constraints

These are properties of this repository rather than of Android, and they change how several features above should be designed.

### Two shipped editions per release

Every release ships twice: `com.termux` (tag `vX.Y.Z`) and the VAJ edition `io.vaj.tl` (tag `vX.Y.Z-vaj`), the latter drawing packages from `repo.pathayam.xyz`. Any user-facing configuration file introduced by this roadmap — key binding syntax, workspace definitions, shell integration scripts — needs a package-neutral path policy from the start, and shell integration scripts additionally need a VAJ packaging path. Deciding this during design costs nothing; retrofitting it after users have config files in a `com.termux`-specific location is a migration.

### Upstream rebase surface

`terminal-emulator` and `terminal-view` are shared with upstream Termux. OSC 8 illustrates the cost: it needs a hyperlink pool and per-cell reference in `terminal-emulator`, hit-testing and underline policy in `terminal-view`, and Intent validation in `app` — three modules, two of them upstream-tracked. The M estimate is accurate for the code but understates the ongoing merge burden. Prefer additive changes with narrow contact surfaces in the shared modules, and keep policy in `app` wherever the split is a choice.

### New per-cell state versus existing save/restore

Hyperlink pools, graphics image/placement stores, and shell integration marks are all per-session state that the current `Bundle` save/restore path (`TermuxActivity.java:7897` and `:7919`) does not carry. That path reconnects view topology to service-owned terminals; it does not serialize emulator-side auxiliary state. Each protocol feature must therefore state explicitly whether its state lives with the `TerminalSession` in the service (survives Activity recreation) or with the view (does not), and the verification matrix must include an Activity-recreation case per protocol feature, not only per layout feature.

## Source reuse and licensing

Both checked-out projects use GPLv3, so their license families are compatible for code reuse in this fork, subject to preserving notices and source obligations. This is a technical observation, not legal advice.

Even with compatible licensing, direct copying should be selective:

- **Good reuse candidates:** protocol documentation and tests translated into Java tests; fuzzy ranking concepts; mapping semantics; pure algorithms; image/keyboard protocol test vectors.
- **Possible but evaluate carefully:** small C shaping/parser algorithms compiled through the NDK.
- **Poor reuse candidates:** GLFW windowing, desktop GL setup, Python `Boss`/`TabManager`, Go terminal overlay UI, OS-window management, and desktop notification backends.

Behavioral reimplementation gives a cleaner Android UX and lower long-term merge burden. Protocol implementations should follow the specifications and use independent conformance tests so they interoperate with applications beyond Kitty.

## Recommended implementation roadmap

### Phase 0 — measurement and contracts (1–2 weeks)

- Add terminal frame-time, render duration, allocation, dropped-frame, and image-memory counters.
- Define `ActionContext`, stable action IDs, categories, availability, and destructive-action confirmation.
- Document the current session/window/pane vocabulary and persistence semantics.
- Establish parser fuzz-test infrastructure and per-sequence size limits.

**Exit gate:** actions can be invoked by stable ID; representative rendering baselines are recorded.

### Phase 1 — action registry extension and command palette (3–6 weeks)

- Extend `LauncherToolRegistry.ToolMetadata` with `category`, `titleResource`, `descriptionResource`, `defaultBindings`, and `availability`; add a `TERMINAL` value to `ToolExecutor`. Do **not** create a second registry.
- Keep the existing agent/MCP projection byte-compatible; add a UI projection beside it.
- Register terminal sheet and multiplexer commands in that registry under dotted IDs (`session.*`, `window.*`, `pane.*`, `terminal.*`, `clipboard.*`, `appearance.*`, `app.*`).
- Add a native searchable palette with binding display and disabled reasons.
- Include session/window/pane, clipboard, search/URL, appearance, font, reset, and settings actions.
- Retain a curated quick action sheet generated from the same metadata.

Extracting operations out of `TermuxActivity.java` (9471 lines) dominates the schedule and adds upstream-rebase surface. If the timeline is tight, register new terminal actions and adapt the existing sheet first; move the bulk extraction to a follow-up phase.

**Exit gate:** every palette entry executes the same code path as other UI/shortcut entry points, and the same registry entry is reachable through `/v1/agent/execute`.

### Phase 2 — unified bindings and key diagnostics (4–7 weeks)

- Normalize physical and embedded key strokes.
- Implement configurable one-stroke mappings, pass-through, unmap, send-text/send-key, and combined actions.
- Add chords/timeouts and a pending-chord HUD.
- Add a `show-key`-style inspector.
- Add modes and conditions only after latency/pass-through tests are solid.

**Exit gate:** no regression in ordinary shell/editor input; non-Latin and external keyboard test matrix passes.

### Phase 3 — multiplexer completion and workspaces (4–8 weeks)

- Add versioned declarative workspace files.
- Save current layouts as workspace definitions.
- Add automatic layouts, equalize/rotate/move, searchable session browser, and clone-with-CWD actions.
- `launcherctl` exposure is already satisfied by Phase 1 if hierarchy actions were registered in `LauncherToolRegistry`; this phase only sets risk levels and confirmation requirements for the destructive ones.

**Exit gate:** workspace can be recreated after full app/service restart, with documented limitations for foreground processes.

### Phase 4 — high-return protocol/render upgrades (4–8 weeks)

- OSC 8 semantic hyperlinks.
- Styled/colored underlines.
- Cursor trail.
- Shell integration and prompt/command marks.
- Grapheme/ligature/font fallback test suite and targeted Canvas improvements.

**Exit gate:** protocol state survives scrollback, selection, resize/reflow, multiple panes, and Activity recreation.

### Phase 5 — modern input and graphics (separate projects)

1. Kitty keyboard protocol (4–8 weeks).
2. Kitty graphics Tier 1 (5–9 weeks).
3. Placement/z-order Tier 2 after real application testing.
4. Unicode placeholders/animation only when Tier 2 is stable.

Do not run the keyboard protocol, graphics protocol, and renderer rewrite concurrently unless different owners have explicit interface contracts; all three touch terminal/session boundaries.

### Phase 6 — renderer decision

Compare improved Canvas benchmarks with product targets. If Canvas meets frame, power, and memory goals, keep it. If not, prototype one terminal pane with a native glyph atlas and measure it before committing to a full replacement.

## Verification strategy

Each feature class needs different evidence:

- **Actions/palette:** registry uniqueness, availability predicates, rotation/recreation, accessibility, and destructive confirmation tests.
- **Bindings:** parser tests, trie/mode state tests, timeout tests, non-Latin layouts, embedded keyboard, multiple hardware keyboards, pass-through latency, repeat/release, and app/system shortcut conflicts.
- **Sessions:** tree serialization, missing/dead handles, process/service restart, nested split mutations, resize storms, close races, and multi-pane output.
- **Protocols:** golden byte-stream tests, fragmented input, malformed/oversized sequences, immediate reply ordering, tmux pass-through where relevant, and fuzzing.
- **Rendering:** screenshot/golden tests for every style; grapheme cursor/selection cases; repeated resize/reflow; frame-time and thermal runs; GPU context loss if a native surface is introduced.
- **Graphics:** strict global/per-session memory budgets, decode cancellation, alpha/z-order, scrollback GC, huge dimensions, decompression bombs, animation throttling, and background/foreground transitions.

Representative compatibility applications should include Neovim, Yazi, tmux, fish/zsh/bash, `ls --hyperlink`, a Sixel test suite, `kitten icat` or another Kitty graphics client, and the Kitty `show-key` behavior as a keyboard-protocol oracle.

## Final prioritization

### Implement first

1. `LauncherToolRegistry` extension with UI metadata (not a new registry).
2. Native command palette over that registry.
3. Unified custom key binding resolver and key inspector, resolving to the same action IDs.
4. Durable project workspaces on top of the existing multiplexer.
5. Underline style, then OSC 8 hyperlinks, then underline decoration color.
6. Shell integration and cursor trail.

These deliver most of Kitty's day-to-day power with limited architectural risk.

### Implement as deliberate protocol projects

1. Kitty keyboard protocol.
2. Kitty graphics MVP, then placements/z-index.
3. Better cluster-aware shaping and font fallback.
4. Multiple cursors and selected smaller protocol extensions.

### Defer

1. Full Kitty graphics animation/Unicode-placeholder parity until the base protocol is proven.
2. Multicell text until a concrete application justifies the invasive buffer changes.
3. A complete OpenGL ES/Vulkan renderer until Canvas measurements show a real blocker.
4. Multiple Android top-level windows as a core abstraction.

## Evidence map from the reviewed source

### Kitty

- Version: `/home/amal/kitty/kitty/constants.py:25`
- OpenGL-only application rendering design: `/home/amal/kitty/docs/overview.rst:16`
- GPU glyph-mask cache rationale: `/home/amal/kitty/docs/faq.rst:208`
- Command palette behavior: `/home/amal/kitty/docs/kittens/command-palette.rst:10`
- Palette collection/execution boundary: `/home/amal/kitty/kittens/command_palette/main.py:289` and `:321`
- Mapping runtime/mode stack: `/home/amal/kitty/kitty/keys.py:90`
- Multi-key, conditional, and modal mapping documentation: `/home/amal/kitty/docs/mapping.rst:130`, `:197`, and `:359`
- `show-key` full-protocol loop: `/home/amal/kitty/kittens/show_key/kitty.go:19`
- Session model/parser: `/home/amal/kitty/kitty/session.py:83` and `:223`
- Split layouts: `/home/amal/kitty/docs/layouts.rst:140`
- Graphics protocol and Unicode placeholders: `/home/amal/kitty/docs/graphics-protocol.rst:1` and `:557`
- Cursor trail configuration: `/home/amal/kitty/kitty/options/definition.py:445`

### Termux launcher fork

- Current session hierarchy: `/home/amal/termux-launcher/app/termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:215`
- Pane controller and recursive layout: `/home/amal/termux-launcher/app/termux-launcher/app/src/main/java/com/termux/app/terminal/TerminalPaneController.java:46`
- Save/restore pane hierarchy: `/home/amal/termux-launcher/app/termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:7897` and `:7919`
- Fixed multiplexer shortcuts: `/home/amal/termux-launcher/app/termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:358`
- Current terminal action sheet: `/home/amal/termux-launcher/app/termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:7514`
- Limited configurable session shortcut map/parser: `/home/amal/termux-launcher/app/termux-launcher/termux-shared/src/main/java/com/termux/shared/termux/settings/properties/TermuxPropertyConstants.java:362` and `TermuxSharedProperties.java:429`
- Embedded keyboard terminal dispatch: `/home/amal/termux-launcher/app/termux-launcher/app/src/main/java/com/termux/app/terminal/inappkeyboard/TerminalKeyEventHandler.java:24`
- Touch key pressed/swipe popup implementation: `/home/amal/termux-launcher/app/termux-launcher/termux-shared/src/main/java/com/termux/shared/termux/extrakeys/ExtraKeysView.java:94` and `:1246`
- Canvas terminal renderer and `drawTextRun`: `/home/amal/termux-launcher/app/termux-launcher/terminal-view/src/main/java/com/termux/view/TerminalRenderer.java:22` and `:218`
- Sixel drawing and buffer entry point: `/home/amal/termux-launcher/app/termux-launcher/terminal-view/src/main/java/com/termux/view/TerminalRenderer.java:156` and `terminal-emulator/src/main/java/com/termux/terminal/TerminalBuffer.java:597`
- Existing shared action registry: `/home/amal/termux-launcher/app/termux-launcher/app/src/main/java/com/termux/launcherctl/LauncherToolRegistry.java:27` (`ToolMetadata:63`, `ToolRisk:31`, `ToolExecutor:45`)
- Registry execution handler and agent endpoints: `/home/amal/termux-launcher/app/termux-launcher/app/src/main/java/com/termux/launcherctl/LauncherCtlApiServer.java:2978`
- Existing agent/MCP platform plan and implementation status: `/home/amal/termux-launcher/app/termux-launcher/project-docs/plans/launcherctl-agent-platform.md`
- APC currently swallowed: `/home/amal/termux-launcher/app/termux-launcher/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java:1402` (`// Eat APC sequences silently for now.`)
- Colon subparameter parsing already implemented: `TerminalEmulator.java:304` (`mArgsSubParamsBitSet`), consumed at `:2316` and `:2340`, set at `:2839`
- `TextStyle` packed-`long` bit budget (attributes 0–10, background from 16, foreground from 40; free window 11–15): `terminal-emulator/src/main/java/com/termux/terminal/TextStyle.java:17`–`:53` and `:80`–`:87`
- Kitty graphics probe regression test: `/home/amal/termux-launcher/app/termux-launcher/terminal-emulator/src/test/java/com/termux/terminal/ApcTest.java:5`
- Underline color parsed but not stored/rendered: `/home/amal/termux-launcher/app/termux-launcher/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java:389` and `:2408`; compare `TextStyle.java:21` and `TerminalRenderer.java:218`
- Android levels and Java/ABI build configuration: `/home/amal/termux-launcher/app/termux-launcher/gradle.properties:26` and module `build.gradle` files
