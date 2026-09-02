# Terminal action registry — inventory and rollout

Feeds the command-palette / custom-bindings work: Phase 1 of the Kitty feasibility study, whose
overview lives in [`../terminal-modernization-status.md`](../terminal-modernization-status.md).

Status: delivered. User configuration and usage are documented in
[`../../docs/en/Terminal_Modernization.md`](../../docs/en/Terminal_Modernization.md); the cross-project
status map is [`../terminal-modernization-status.md`](../terminal-modernization-status.md).

## Decision

Extend the existing `LauncherToolRegistry` rather than adding a second
registry. It already carries `name`, `description`, JSON schema, `ToolRisk`,
`requiresConfirmation`, and `ToolExecutor`, and is consumed by
`/v1/agent/tools`, `/v1/agent/execute`, the MCP bridge, and the CLI. A parallel
`TerminalAction` registry would duplicate dispatch, confirmation gating, and
action naming.

Two projections over the same entries:

- **Agent/MCP** — `ToolMetadata.toInternalJson()` / `toOpenAiTool()`. Contract
  is frozen; UI metadata must never appear here.
- **UI** — `ToolMetadata.toUiJson()` plus `getUiTools()` /
  `getUiToolsByCategory()`. Consumed by the palette, curated action sheet, and
  binding editor.

## Slices

- **Slice 1 — registry metadata (done).** Added `ToolExecutor.TERMINAL`,
  category constants, and optional `category` / `titleRes` / `descriptionRes` /
  `defaultBindings` fields with a preserved 6-argument constructor for
  agent-only tools. Added the UI projection and 5 unit tests, including a guard
  that the agent projection stays at exactly 7 keys.
- **Slice 2 — first terminal actions (done).** Registered 14 tools and wired
  them in the same change, so nothing is advertised before it is executable.
  Registry now holds 28 tools.

  New `com.termux.app.terminal.TerminalActionDispatcher` is the single seam
  between background callers and the UI: it holds the Activity weakly, attaches
  in `onResume`, detaches in `onStop`/`onDestroy`, and posts work to the main
  looper with a 5s bounded wait. Detached callers get
  `409 activity_not_running` rather than a silent no-op, so an agent can
  distinguish "did nothing" from "could not act". `detach()` ignores calls from
  an Activity instance that has already been replaced, so teardown during
  recreation cannot detach the incoming instance.

  Shipped: `terminal.state`, `pane.split_vertical`, `pane.split_horizontal`,
  `pane.focus_direction`, `pane.resize`, `pane.kill_focused`, `window.new`,
  `window.close`, `window.next`, `window.previous`, `session.new`,
  `session.next`, `session.previous`, `session.close_current`.

  `terminal.state` was added beyond the inventory: a command-only surface gives
  an agent no way to observe what it is acting on. It reads only public
  `TermuxActivity` API (`isSplitPanesEnabled()`, `mDrawerSessions`,
  `getCurrentSession()`, `getPaneController().getVisiblePaneViews()`).

  Risk convention, matching the existing launcher tools: navigation LOW and
  unconfirmed, shell-spawning MEDIUM and confirmed, shell-terminating HIGH and
  confirmed.

  Deferred out of slice 2 because each needs a new public seam on
  `TermuxActivity` rather than just registration: `window.select`
  (`showWindowFromBar` is private), `window.rename` / `session.rename` (the
  public entry points open a text-input dialog and cannot return a result to a
  remote caller — they need a direct-rename API taking the new name),
  `session.kill`, and the whole `terminal.*` / `clipboard.*` /
  `appearance.*` / `app.*` groups.

  **Not verified end-to-end.** The tests that exercise `/v1/agent/execute`
  cannot bind loopback in this environment (see Test baseline), so the HTTP
  path into the new branch is covered only by unit tests of the dispatcher and
  registry. Confirm on-device before treating it as working.
- **Slice 3 — keybind unification (done for the multiplexer binds).**
  `handleMultiplexerKeybinds` no longer holds a stroke-to-action `switch`. New
  `TerminalKeyBindingResolver` builds a stroke table from the registry's
  `defaultBindings` and returns a `Match` (tool name + arguments derived from the
  stroke), which the same `TerminalActionDispatcher` executes. A keystroke, a
  palette entry, and `/v1/agent/execute` now reach the action by one path.
  Bindings change in the registry, not in the input handler.

  Behavior was held identical on purpose, and the parity is pinned by 14 tests:

  - Matching is on **key code**, not the produced character, exactly as the old
    switch did, so binds keep following physical US positions on non-Latin
    layouts.
  - `resolve()` returns null immediately unless Ctrl+Alt are both held, so
    ordinary typing pays nothing. On the main thread the dispatcher runs inline —
    no handler post, no latch.
  - An unbound Ctrl+Alt stroke still returns false and falls through to the
    legacy character-based binds.
  - Arrows still report whether they consumed the stroke: the dispatcher's
    `handled` flag is passed back, so an unconsumed arrow reaches the legacy
    drawer/session binds.
  - `Ctrl+Alt+R` remains a direct call — `renameCurrentWindowSession()` opens a
    text-input dialog, so it cannot be a tool that returns a result until the
    direct-rename API exists.

  The resolver also records `getConflicts()` for strokes claimed by two tools, so
  the diagnostics screen can show them. The current defaults have none.

  **Still open in this slice:** the legacy `Ctrl+Alt`+character block in
  `onKeyDown` (session next/previous, drawer, keyboard toggle, URLs, paste, font
  size, session-by-index) is untouched. It matches on `getUnicodeChar`, so
  folding it in needs the real stroke normalization — layout fallbacks, the
  shifted-`+` special case, and a conflict policy for `Ctrl+Alt+V` — which is the
  Phase 2 project in the study, not a lookup table. The action sheet is likewise
  not yet generated from the registry.
- **Slice 4 — palette UI (done).** `TerminalCommandPalette` shows a Material
  dialog over `getUiToolsByCategory()`: search field, category headers when
  unfiltered, a flat ranked list while searching, keybind chips as subtitles, and
  greyed-out rows with a reason when compatibility mode disables pane/window
  actions. Selection runs through `TerminalActionDispatcher`, so it shares the
  keystroke and agent path. `HIGH`/`CRITICAL` risk actions get a confirm dialog
  first.

  `CommandPaletteFilter` holds the search/ranking and is free of Android types so
  it is unit-testable (16 tests). Tiers: exact title, title prefix, word prefix,
  title substring, tool-id substring with `.`/`_` as spaces, category, binding
  text, then a fuzzy subsequence pass that needs 2+ characters. Ties prefer
  enabled entries, then shorter titles, then registration order. This mirrors the
  launcher's app-search behavior rather than reusing `LauncherRankingEngine`,
  which is typed to `LauncherAppEntry`.

  Two inclusion rules, both self-maintaining:

  - **A `titleRes` marks a tool user-facing.** `terminal.state` has none, so the
    introspection tool stays out of the palette while remaining available to
    agents and to the binding table.
  - **No required schema arguments.** The palette has nowhere to prompt, so
    `pane.focus_direction` and `pane.resize` are excluded automatically. They keep
    their titles and bindings and stay keyboard-only.

  Entry point is a new "Command palette" item at the top of the terminal action
  sheet. It gets **no default keybind**: `Ctrl+Alt+Shift+P` is free in the
  multiplexer table but falls through to the legacy "previous session" bind in
  compatibility mode, which is the same mode-dependent ambiguity that kept
  `session.next` unbound. Bind it once `defaultBindings` can express conditions.

  Verified on device: sheet item present, grouped view renders headers and chips,
  searching "close" yields exactly Close window and Close session as a flat list,
  and tapping Close window raises "Run Close window?" with Cancel/Run. Cancelled;
  no state change, no crash.

  Deferred: string resources for `descriptionRes` (subtitles currently show the
  keybind, falling back to the agent-facing `description`), an XML layout with
  proper Material styling instead of the programmatic view, and generating the
  rest of the action sheet from the registry.
- **Slice 5 — availability predicates and terminal view actions (done).**
  Registry now holds 35 tools, 21 of them `TERMINAL`.

  `ActionContext` is a narrow interface on the registry side
  (`isSplitPanesEnabled()`, `hasCurrentSession()`) — conditions, not Android
  objects, so the registry stays platform-neutral.
  `TerminalActionDispatcher.actionContext()` implements it against the live
  Activity and returns an all-false context when detached, so asking about
  availability while backgrounded answers "unavailable" instead of throwing.
  `AvailabilityPredicate` is evaluated per call via `ToolMetadata.availabilityIn()`,
  never at registration time; a null predicate means unconditionally available.
  Two shared predicates cover every case so far: `REQUIRES_SPLITS` for
  pane/window actions, `REQUIRES_SESSION` for anything acting on the focused
  shell. The palette no longer hard-codes the compatibility-mode check.

  Seven new actions, all reachable through already-public methods so no new
  seams were needed: `terminal.toggle_soft_keyboard`, `terminal.toggle_toolbar`,
  `terminal.font_size_increase`, `terminal.font_size_decrease`,
  `terminal.select_url`, `terminal.share_transcript`, `clipboard.paste`.

  `terminal.share_transcript` and `clipboard.paste` are MEDIUM and confirmed:
  sharing moves scrollback into another app, and pasting writes clipboard text
  into a live shell where a stray newline runs it.

  **Confirmation policy, stated deliberately:** `requiresConfirmation` gates the
  *remote* surface and is enforced by `LauncherCtlAgentHandler`. The palette
  instead confirms on `HIGH`/`CRITICAL` risk only, because deliberately tapping
  "Paste" in a search list *is* the confirmation, and prompting on every split or
  new window would be noise. If a future surface needs a third policy, add a flag
  rather than overloading `requiresConfirmation`.

  Verified on device: `/v1/agent/tools` reports 36 (35 static + 1 MCP) with 21
  terminal executors; `terminal.font_size_increase` then `_decrease` both
  returned ok (net zero change); the palette shows `TERMINAL`, `PANE`, `WINDOW`,
  `SESSION`, `CLIPBOARD` groups in registration order; searching "toggle" and
  "font" returns exactly the right pairs. No crashes, state unchanged.

- **Slice 6 — argument-taking actions and the direct-rename seams (done).**
  Registry now holds 39 tools, 25 of them with UI metadata.

  New public seams, all narrow and documented as such: `renameCurrentWindowSessionTo`,
  `selectWindow`, `resetCurrentSession`, `getCurrentWindowCount`,
  `getCurrentWindowIndex`, `getCurrentWindowSessionName` on `TermuxActivity`, and
  `renameCurrentSessionTo` on `TermuxTerminalSessionActivityClient`. Each exists
  because the public entry point it replaces opens a dialog and cannot return a
  result to a remote caller.

  New actions: `window.select` (required `index`), `window.rename` (required
  `name`), `session.rename` (required `name`), `terminal.reset`. The first three
  declare required arguments, so the palette skips them automatically; they are
  reachable remotely and, later, from keybinds. `terminal.reset` takes none and
  appears in the palette.

  `terminal.state` now also reports `windows`, `currentWindow`, and
  `windowSessionName`, which is what makes `window.select` usable — an agent can
  see how many windows exist before choosing an index.

  `session.kill` was deliberately **not** registered: `pane.kill_focused` already
  calls `finishIfRunning()` on the focused shell, so it would be the same
  operation under a second name. A test pins this.

  Two gaps that only device testing exposed, both fixed:

  - `window.rename` with `"slice6"` stored `"slice"`. Not a bug —
    `WindowSessionName.MAX_CODE_POINTS` is 5 and the seam correctly reuses the
    app's naming policy — but the schema never said so. The description now states
    the cap, and the result reports the **stored** name, so a caller can see that
    what it asked for and what was kept differ.
  - Neither rename tool could clear a name, while the rename dialog can. Both now
    treat an explicitly empty `name` as "restore the default" and reject only a
    missing key. `window.rename` returns JSON null when cleared.

  Verified on device: enriched `terminal.state`; rename to a 5-char name, then
  cleared back to unnamed; `window.select` index 0 ok and index 99 rejected with
  the window count in the message; both renames reject a missing key;
  `terminal.reset` ok. No crashes, and the device was left in its original state.

- **Slice 7 — appearance and app actions (done).**
  Registry now holds 45 tools, 31 with UI metadata, across 7 categories.

  Six seams on `TermuxActivity` wrapping handlers the action sheet already used:
  `openWallpaperPicker`, `toggleWallpaperMode`, `isWallpaperModeEnabled`,
  `openGlassLab`, `openSettings`, `openLookAndFeel`, `openAppsBar`. Six new
  actions: `appearance.set_wallpaper`, `appearance.toggle_wallpaper`,
  `appearance.glass_lab`, `app.open_settings`, `app.open_look_and_feel`,
  `app.open_apps_bar`. These act on the app rather than a shell, so they carry no
  session requirement — a test pins that they stay available with no session.
  `appearance.toggle_wallpaper` is MEDIUM and confirmed because it persists an
  appearance change and returns the value it moved to; opening the picker changes
  nothing by itself and is not confirmed. `terminal.state` also reports
  `wallpaperEnabled` now.

  **The action sheet was deliberately left hand-curated.** Generating it from the
  registry was on the slice-4 list, but the sheet carries an explicit comment that
  appearance and apps entries are omitted to "keep this sheet lean", and it shows
  a live PID in the kill label. Auto-generating all 31 UI tools into it would
  overturn that product decision and bury the common actions. The palette is now
  the complete surface; the sheet stays a curated shortcut list with the palette
  at its top.

  Verified on device: `/v1/agent/tools` reports 46 (45 static + 1 MCP) with all
  six present; `terminal.state` carries `wallpaperEnabled`;
  `app.open_settings` opened `SettingsActivity` and BACK returned to the launcher;
  `appearance.toggle_wallpaper` flipped true→false→true with the state field
  tracking each step and the original value restored; `appearance.glass_lab`
  opened the surface editor (Dock/Keyboard/Status/Terminal sections) and exited
  via Done; palette search finds all three settings entries and both wallpaper
  entries. No crashes.

  `appearance.set_wallpaper` was registered and confirmed advertised but
  deliberately **not invoked** during testing: it launches an external media
  picker and crop flow, which would leave the device inside another app's UI.
  Exercise it by hand.

## Action inventory

Line numbers below were verified against commit `1a629b12` on `dev`. Rows
marked *(unverified)* have a plausible entry point that was not individually
confirmed — check before wiring.

Naming note: user-facing "vertical split" passes `LinearLayout.HORIZONTAL`
(panes side by side). IDs follow the user-facing sense, matching the existing
keybind comments.

### Pane — `TermuxActivity.java`

| proposed_id | entry point | current trigger | args | destructive |
|---|---|---|---|---|
| `pane.split_vertical` | `:8562` `splitCurrentPane(int)` | Ctrl+Alt+V | orientation=HORIZONTAL | no |
| `pane.split_horizontal` | `:8562` `splitCurrentPane(int)` | Ctrl+Alt+H | orientation=VERTICAL | no |
| `pane.focus_direction` | `:8572` `focusPaneDirection(int)` | Ctrl+Alt+arrow | keyCode | no |
| `pane.resize` | `:8582`→`resizeActivePane(int)` | Ctrl+Alt+Shift+arrow | keyCode | no |
| `pane.kill_focused` | `:8582` `killFocusedPane()` | keybind path only | none | **yes** |
| `pane.collapse_all` | `:8699` `collapseAllSplits()` | none — compatibility mode on resume | none | **yes** |

### Window — `TermuxActivity.java`

| proposed_id | entry point | current trigger | args | destructive |
|---|---|---|---|---|
| `window.new` | `:8601` `createNewWindow()` | Ctrl+Alt+C; window-bar "+" | none | no |
| `window.close` | `:8621` `closeCurrentWindow()` | Ctrl+Alt+X | none | **yes** |
| `window.next` | `:8640` `switchWindow(boolean)` | Ctrl+Alt+] | forward=true | no |
| `window.previous` | `:8640` `switchWindow(boolean)` | Ctrl+Alt+[ | forward=false | no |
| `window.select` | `:8478` `showWindowFromBar(int)` | window-bar chip tap | index | no |
| `window.rename` | `:7852` `renameCurrentWindowSession()` | Ctrl+Alt+R; session shortcut when splits on | none | no |

### Session — `TermuxTerminalSessionActivityClient.java` unless noted

| proposed_id | entry point | current trigger | args | destructive |
|---|---|---|---|---|
| `session.new` | `:442` `addNewSession(boolean, String)` | new-session button; long-press for named/failsafe; Ctrl+Alt+C; session shortcut | isFailSafe, sessionName | no |
| `session.activate` | `:358` `setCurrentSession(TerminalSession)` | drawer item click | session | no |
| `session.activate_by_index` | `:406` `switchToSession(int)` | Ctrl+Alt+digit | index | no |
| `session.next` | `:382` `switchToSession(boolean)` | Ctrl+Alt+Down; session shortcut | forward=true | no |
| `session.previous` | `:382` `switchToSession(boolean)` | Ctrl+Alt+Up; session shortcut | forward=false | no |
| `session.rename` | `:417` `renameSession(TerminalSession)` | drawer long-press when splits off; session shortcut | session | no |
| `session.remove_finished` | `:506` `removeFinishedSession(TerminalSession)` | Enter / Ctrl+J on an exited session | session | **yes** |
| `session.kill` | `TermuxActivity.java:7609` `showKillSessionDialog(TerminalSession)` | action sheet: kill process | session | **yes** |
| `session.close_current` | `TermuxActivity.java:8650` `closeCurrentSession()` | Ctrl+Alt+Shift+X | none | **yes** |

### Terminal / clipboard — `TermuxTerminalViewClient.java` unless noted

| proposed_id | entry point | current trigger | args | destructive |
|---|---|---|---|---|
| `terminal.reset` | `TermuxActivity.java:7623` `onResetTerminalSession(TerminalSession)` | action sheet: reset | session | **yes** |
| `terminal.show_action_sheet` | `TermuxActivity.java:7514` `showTerminalActionSheet()` | long-press context menu; options menu; Ctrl+Alt+M | none | no |
| `terminal.toggle_soft_keyboard` | `:646` `onToggleSoftKeyboardRequest()` | toggle-keyboard button; Ctrl+Alt+K | none | no |
| `terminal.toggle_toolbar` | `TermuxActivity.java:7062` `toggleTerminalToolbar()` | toggle-keyboard long-click | none | no |
| `terminal.font_size_increase` | `:636` `changeFontSize(boolean)` | Ctrl+Alt++; pinch | increase=true | no |
| `terminal.font_size_decrease` | `:636` `changeFontSize(boolean)` | Ctrl+Alt+-; pinch | increase=false | no |
| `terminal.select_url` | `:852` `showUrlSelection()` | action sheet: select URL; Ctrl+Alt+U | none | no |
| `terminal.share_transcript` | `:833` `shareSessionTranscript()` | action sheet: share transcript | none | no |
| `terminal.share_selected` | `shareSelectedText()` *(unverified)* | none currently wired | none | no |
| `terminal.report_issue` | `reportIssueFromTranscript()` *(unverified)* | none currently wired | none | no |
| `clipboard.paste` | `:931` `doPaste()` | Ctrl+Alt+V (legacy) — **collides with `pane.split_vertical`; resolve during slice 3** | none | no |
| `clipboard.copy_selected` | `TermuxTerminalSessionActivityClient.onCopyTextToClipboard(...)` *(unverified)* | terminal copy mode | session, text | no |

### Appearance / app — `TermuxActivity.java`

| proposed_id | entry point | current trigger | args | destructive |
|---|---|---|---|---|
| `appearance.set_wallpaper` | `:7134` `launchManagedWallpaperPicker()` | action sheet: set wallpaper | none | no |
| `appearance.toggle_wallpaper` | `setWallpaperModeEnabled(Context, boolean)` *(unverified)* | action sheet: remove wallpaper | enabled | no |
| `appearance.enter_glass_lab` | `enterDockTuningMode()` *(unverified)* | action sheet: glass lab | none | no |
| `appearance.exit_glass_lab` | `exitDockTuningMode()` *(unverified)* | dock tuning Done / Dismiss | none | no |
| `app.open_settings` | `:7472` `openSettingsHome()` | action sheet: settings; settings button | none | no |
| `app.open_look_and_feel` | `openLookAndFeelSettings()` *(unverified)* | menu id exists, not in current sheet | none | no |
| `app.open_apps_bar` | `openAppsBarSettings()` *(unverified)* | menu id exists, not in current sheet | none | no |
| `app.toggle_drawer` | sessions-indicator click listener *(unverified)* | sessions indicator tap | none | no |
| `app.lock_screen` | `lockScreenFromAzDoubleTap()` *(unverified)* | A–Z row double-tap | none | no |
| `app.request_storage_permission` | `requestStoragePermission(boolean)` *(unverified)* | `termux-setup-storage` broadcast | isPermissionCallback | no |

## Notes for slice 2

- **Slice 8 — conditional bindings, legacy chain folded in, palette bound (done).**
  Registry now holds 53 tools, 38 with UI metadata. `onKeyDown` no longer contains
  any stroke table.

  `BindingCondition` (`ALWAYS` / `SPLITS_ON` / `SPLITS_OFF`) is an **enum, not a
  lambda**, because conditions must be printable for the diagnostics screen and
  expressible in a future `map --when` config syntax. `Binding` pairs a stroke with
  a condition; `defaultBindings` is now `List<Binding>`. `overlaps()` decides what
  counts as a real conflict, so one stroke may be claimed by several tools under
  conditions that cannot both hold.

  That is what finally resolved the conflicts recorded below. `Ctrl+Alt+V` is
  `pane.split_vertical` under `SPLITS_ON` and `clipboard.paste` under `SPLITS_OFF`;
  `Ctrl+Alt+C` is `window.new` / `session.new`; `Ctrl+Alt+R` is
  `window.rename_prompt` / `session.rename_prompt`; the arrows are pane
  focus/resize with splits on and session/drawer with them off. Every one of those
  pairs is now truthful in both modes instead of unbound.

  The legacy `Ctrl+Alt`+character chain is gone from `onKeyDown`; both it and the
  multiplexer switch are replaced by one `handleRegistryKeybinds` call. Seven new
  tools absorbed the parts that had no registry entry: `app.command_palette`
  (bound to `Ctrl+Alt+Shift+P` — unambiguous now that shift is part of the stroke),
  `terminal.action_sheet`, `app.open_drawer`, `app.close_drawer`,
  `session.activate_by_index` (the resolver derives `index` from the digit key, the
  same trick the arrows use for `direction`), plus the two rename prompts.

  Two behaviors preserved deliberately: every `Ctrl+Alt` combination is still
  swallowed while hardware shortcuts are enabled, matched or not, and pane
  focus/resize still report whether they consumed the stroke. **One intentional
  change:** matching is on key code rather than `getUnicodeChar`, so these binds
  now also work on non-Latin layouts where the legacy chain silently did nothing.

  All 31 palette-visible tools gained `descriptionRes`, and subtitles read
  "description · stroke", showing only bindings whose condition currently holds —
  advertising `ctrl+alt+v` next to Paste while splits are on would be a lie.

  21 resolver parity tests encode the pre-change behavior of both chains, in both
  modes. Verified on device in one pass: `Ctrl+Alt+Shift+P` opens the palette,
  `Ctrl+Alt+M` the action sheet, `Ctrl+Alt+R` the rename dialog (which confirms the
  5-character cap in its own title), `Ctrl+Alt+U` reports no URLs, `Ctrl+Alt+V`
  splits, typing `vcxhrnpkmu` dispatches nothing, unbound `Ctrl+Alt+F1` is
  swallowed without a crash. No resolver conflicts logged.

- **Slice 9 — selection actions and the palette layout (done).**
  Registry now holds 54 tools, 40 with UI metadata.

  `ActionContext` gained `hasSelectedText()`, which is what made the last two
  inventory rows implementable: `terminal.share_selected` and
  `clipboard.copy_selected` were previously listed as "not wired to anything"
  because there was no way to see selection state. A `REQUIRES_SELECTION`
  predicate greys them out with "Unavailable: no text selected" instead of letting
  them no-op. Sharing is MEDIUM and confirmed because it leaves the device; copying
  stays on it and is LOW.

  The palette's programmatic view was replaced by
  `res/layout/command_palette{,_row,_header}.xml`, so the search field is a
  `TextInputLayout` and rows use `?attr/textAppearance*` instead of hard-coded
  sizes. The adapter now recycles views properly via `getItemViewType`, and the
  empty state is a real `setEmptyView` rather than a toast.

  Verified on device in one pass: 55 tools over HTTP (54 static + 1 MCP), both
  selection actions return `409 no_selection` with nothing selected, the palette
  opens from `Ctrl+Alt+Shift+P` with the new layout showing
  "description · stroke" subtitles, and searching "selected" shows both rows
  greyed with their reason. No crashes; device left unchanged.

- **Slice 10 — chords, timeout, and editable bindings (done).** The resolver is now a sequence
  state machine. Prefixes such as `Ctrl+Alt+Space` remain pending for two seconds, show a small
  non-focusable overlay, and cancel cleanly on timeout, mismatch, lifecycle stop, or property
  reload. The command palette keeps its direct `Ctrl+Alt+Shift+P` bind and also demonstrates the
  chord path at `Ctrl+Alt+Space > P`.

  `~/.termux/termux-launcher-bindings.conf` overlays the registry defaults without changing the
  remote action surface. `map`, `unmap`, `--when splits-on|splits-off|always`, quoted `send-text`,
  `send-key`, and repeated maps for ordered multi-action bindings are supported. Parsing is bounded
  to 256 KiB, 4096 lines, and 4096 characters per line. Invalid lines are skipped, logged, and
  summarized in a toast while every valid mapping remains active.

- **Slice 11 — hints overlay (done).** `terminal.hints`, bound to `Ctrl+Alt+U`, scans the formatted
  transcript for URLs, absolute and relative paths, hashes, and `path:line[:column]` locations.
  It renders stable letter labels in a keyboard-first picker. Pressing a label opens a URL or copies
  any other hint; holding Shift while pressing a URL label copies it instead. Extraction, overlap
  precedence, deduplication, and labels live in the Android-free
  `TerminalHintsModel` and are unit tested.

- **Slice 12 — native scrollback search (done).** `terminal.search_scrollback`, bound to
  `Ctrl+Alt+S`, opens a native search overlay over the active emulator transcript. Matches are
  case-insensitive, bounded, and retain their emulator row so choosing a result scrolls the view to
  it instead of copying text into a temporary pager. The model tests history/screen row mapping,
  multiple hits, empty queries, and result caps.

- **Slice 13 — modal keymaps (done).** A root binding can push a named mode with `map --new-mode`;
  mode-local mappings use `map --mode`. Modes form a stack and may set a timeout, choose
  `beep`, `ignore`, `end`, or `passthrough` for unknown keys, and either remain active or end after
  a matched action. `pop-mode` (and kitty's `pop_keyboard_mode` spelling) exits explicitly. The
  chord overlay doubles as a persistent mode indicator, without acquiring input focus. Reloading
  properties replaces the complete mode table and cancels both pending chord and mode timers.

  Pong verification upgraded the APK in place, loaded a disposable `test` mode, observed its mode
  indicator, dispatched `x` to `send-text MODAL_OK`, verified the mode remained active, and popped it
  with Escape. The built-in `Ctrl+Alt+Space > P` chord opened the command palette. The temporary
  binding file and typed test line were removed, the original no-config state was restored, and the
  bounded app log contained no binding error or fatal exception.

## Resolved binding conflicts

These were the ambiguities that blocked earlier slices. All three are now expressed
as conditions rather than left unbound; kept here as the rationale for why
`BindingCondition` exists.

- **`Ctrl+Alt+V`** — vertical split with splits on, legacy paste with them off.
- **`Ctrl+Alt+Up/Down`** — pane focus with splits on, session switching with them
  off. `session.next`/`previous` also keep the unconditional `Ctrl+Alt+N`/`P`.
- **`Ctrl+Alt+C`** — `window.new` with splits on, `session.new` with them off;
  `Ctrl+Alt+Shift+C` is always `session.new`.
- **`Ctrl+Alt+R`** — `window.rename_prompt` with splits on,
  `session.rename_prompt` with them off.

## Notes for later slices

- In-app destructive actions should confirm through the existing dialog path,
  not through `user.confirm`.
- Several inventory rows are only reachable from a keybind or an internal call
  site, never from a menu. Those are the clearest early wins for the palette.
- `pane.resize` and `pane.focus_direction` take a `direction` enum
  (left/right/up/down) rather than a raw Android key code; the dispatcher maps
  it to `KEYCODE_DPAD_*`. Keep that translation at the dispatcher boundary so
  the registry schema stays platform-neutral.

## Remaining work

Generating the curated action sheet from the registry remains deliberately out of scope; the
complete searchable surface is the command palette. All other binding/input items from these
slices are complete. The project-wide unfinished list is `backlog.md`.

## Test baseline

`./gradlew :app:testDebugUnitTest` fails 48 tests on clean `dev` at
`1a629b12`, across 12 classes, for environmental reasons: tests that bind
loopback HTTP get `ConnectException: Connection refused`, and
`IconPackXmlParserTest` needs an unmocked `XmlPullParserFactory`. Compare
against that baseline rather than expecting a green suite.
