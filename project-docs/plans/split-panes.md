# Native split panes — feasibility study & implementation plan

**Status:** Phase 2b DONE + verified on-device (2026-07-22). tmux-style keybinds + compatibility mode. Session-as-tab + up-to-2 panes per tab. Greenfield feature.

## Phase 2b result (verified on device, 2026-07-22)

Replicated the user's tmux binds (from their `~/.tmux.conf`) and added a safety toggle.

**Keybinds (active only when compatibility mode is OFF), in `TermuxTerminalViewClient.handleMultiplexerKeybinds()`** — user-specified scheme, all Ctrl+Alt(+Shift):
- `Ctrl+Alt+V` → vertical split (panes side by side)  ✓ verified
- `Ctrl+Alt+H` → horizontal split (panes stacked)
- `Ctrl+Alt+C` → new window  (window layer not built — shows toast)
- `Ctrl+Alt+X` → close window (window layer not built — shows toast)
- `Ctrl+Alt+Shift+C` → new session
- `Ctrl+Alt+Shift+X` → close session (`closeCurrentSession()`)
- `Ctrl+Alt+arrow` → focus pane directionally
- `Ctrl+Alt+Shift+arrow` → resize split ratio (weight ±0.12, clamped)

NOTE: the earlier Alt+Enter / Alt+Esc / Alt+Up-Down tmux binds were removed in favour of this scheme.
CAVEAT: these fire from a HARDWARE keyboard (via `TerminalView.onKeyDown`). The in-app soft
keyboard sends letter keys as code points that bypass onKeyDown, so Ctrl+Alt+letter from the
in-app keyboard is NOT yet routed to the multiplexer — needs a hook in TerminalKeyEventHandler.

**Compatibility mode** — Settings › Termux › Terminal IO › "Split panes" › Compatibility mode. Key `compatibility_mode` (`TermuxPreferenceConstants.TERMUX_APP`, default false). When ON: `isSplitPanesEnabled()` returns false → all split keybinds ignored, `splitCurrentPane()` no-ops, and `onResume` calls `collapseAllSplits()` to restore native single-pane Termux.

**Duplicate-prompt fix (side-by-side only):** a side-by-side split changes pane 1's column count; the running shell reflows and leaves a stale prompt (stacked splits keep the same width, so no issue). After the resize settles, `splitCurrentPane` posts a Ctrl+L (``) to pane 1's session (250ms delay) so the shell redraws one clean prompt. Verified clean.

### Not built yet (next phase): the Windows layer
The middle tmux level. User's binds for it (currently pass through to the shell, NOT intercepted):
- `Alt+1..9` → select-window
- `Alt+Left / Alt+Right` → prev/next window
- `Alt+Shift+Left / Right` → swap-window
Needs a Window model (each window owns its own pane layout, many windows per session) + a switch UI (window bar or just the keybinds).

## Phase 2a result (verified on-device, 2026-07-22)

## Target model (agreed with user)

Three-level tmux-style hierarchy:
- **Session** = termux native session = tmux session. Top level, shown in the sessions drawer.
- **Window** = tmux-style window inside a session. Multiple per session. **NOT built yet — next phase.**
- **Pane** = split within a window. Each pane is its own shell (TerminalSession under the hood), hidden from the drawer.

Current build implements Session + Pane (2 panes max, h/v). The Window layer is the next phase.

## Phase 2a result (verified on device, 2026-07-22)

Fixes to the first spike, all confirmed working:
- **Default is 1 pane** (auto-spawn removed). Split is user-triggered.
- **Ctrl+Alt+H** = split horizontally (stacked top/bottom). **Ctrl+Alt+V** = split vertically (side by side). ⚠️ Ctrl+Alt+V previously = paste; paste still reachable via paste key / long-press menu.
- **Nerd font on all panes** — `checkForFontAndColors()` now applies the typeface to every populated pane (was active-pane only).
- **Touch-switch is repeatable** — switched from OnFocusChangeListener to an ACTION_DOWN OnTouchListener; taps re-target reliably (old bug: worked once then stuck).
- **Exit teardown** — when a secondary pane's shell exits, the pane collapses cleanly, focus returns to the surviving pane, no stale border, input routes correctly.
- **Drawer shows sessions, not panes** — secondary pane shells are filtered out of the drawer list (`mDrawerSessions`). Verified: 2 panes → 1 drawer entry.
- New session (Ctrl+Alt+C / button) makes a new tab (single pane) instead of replacing the bottom pane.

Model implementation (`TermuxActivity`): `mTabSecondary` (primary→secondary), `mTabOrientation`, `mSecondaryPaneSessions`, `mCurrentTabPrimary`, `mDrawerSessions`. Key methods: `activateSessionInPanes()`, `splitCurrentPane()`, `closeSecondaryPane()`, `promoteSecondaryToPrimary()`, `rebuildDrawerSessions()`. `setCurrentSession()` (session client) now delegates to `activateSessionInPanes()`.

### Known gaps / next phase
- **Windows layer** not built (the middle tmux level). Needs its own model + switch UI.
- Max 2 panes; only one split axis at a time (no nested/recursive splits yet).
- No drag divider, no rotation persistence.
- No close-pane shortcut (only shell `exit`).
- Switching tabs preserves each tab's split via `mTabSecondary`, but a secondary shell of a non-active tab keeps producing output to a hidden view (harmless, but not paused).

## Original Phase 1 spike result (superseded by 2a above)

## Phase 1 spike result (verified on device)

Built debug APK, installed on connected device, confirmed working:
- Two live `fish` shells stacked, independent grids, each reflows to its own height. ✓
- Active-pane border (cyan) on the focused pane. ✓
- Tap a pane → focus + border move to it. ✓
- Typed input routes to the focused pane only (`echo BOTTOM_PANE` hit bottom pane, top untouched). ✓
- In-app keyboard retargets to the focused pane (supplier-based sink). ✓
- No crash.

Files changed in the spike:
- `app/src/main/res/layout/activity_termux.xml` — single `terminal_view` wrapped in a vertical `LinearLayout` (`terminal_panes`) with two pane frames + divider; pane 2 frame/divider GONE until a second session attaches.
- `app/src/main/res/drawable/pane_active_border.xml` — new; focused-pane border.
- `app/.../TermuxActivity.java` — added `mTerminalView2`/`mActivePane`; `getTerminalView()` returns active pane; added `getTerminalPaneViews()`, `getTerminalViewForSession()`, `setActivePane()`, `updatePaneActiveIndicators()`, `ensureSecondPaneSession()` (bootstraps pane 2 font before attach); focus routing; in-app keyboard host getter → active pane.
- `app/.../TermuxTerminalSessionActivityClient.java` — `onTextChanged` routes per-session to the owning pane view; per-session coalescing set so two panes don't drop each other's frames.
- `app/.../inappkeyboard/TerminalKeyEventHandler.java` + `TermuxInAppKeyboard.java` — `ViewTerminalSink` resolves the target view via `Supplier<TerminalView>` so keyboard follows focus.
- `terminal-view/.../TerminalView.java` — `updateSize()` now also guards `mRenderer == null` (a pane can be laid out before its font is set). Only engine-adjacent change; safe/defensive.

Build: `TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0 ./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/termux-app_apt-android-7-debug_universal.apk`.

Spike caveats (expected — not Phase 1 scope): pane 2 auto-spawns at launch (no split/close UI yet); vertical 2-pane only; no drag divider; no rotation persistence; drawer session-switch always targets the active pane.

**Goal:** show 2+ live terminals on screen at once (split panes), like modern terminals do without tmux. Both editions (`com.termux`, VAJ `io.vaj.tl`).

---

## Verdict

**Feasible, medium effort. ~1500–3000 LOC, entirely in the `app/` layer plus one layout container. Zero changes to the terminal engine.**

The terminal engine and the `TerminalView` widget already support N independent, differently-sized, simultaneously-live terminals. The only thing standing in the way is the single-view assumption baked into `TermuxActivity` and the two client classes. This is a UI/glue refactor, not an engine problem.

---

## Why the core is already ready

Everything below the app layer is per-instance, statics-free, and size-agnostic.

| Layer | File | Fact |
|-------|------|------|
| Session | `TerminalSession.java:28` | Doc: "session may outlive the EmulatorView." Sessions decoupled from views by design. |
| Session | `TerminalSession.java:121` | `updateSize(cols, rows, cellW, cellH)` → `JNI.setPtyWindowSize` + `emulator.resize`. Grid set *after* construction, per session. |
| Session store | `TermuxService` `mShellManager.mTermuxSessions` | Canonical session list lives in the **service**, independent of any view. Natural backing store for panes. |
| Emulator | `TerminalEmulator.java:554` | `resize()` is first-class, tested (`ResizeTest`). Reflows preserving cursor. |
| View | `TerminalView.java:1348` | `updateSize()` derives grid from the view's **own** `getWidth()/getHeight()`. A half-width pane auto-computes half the columns. |
| View | `TerminalView.java:345` | `attachSession()` — clean per-instance rebind. No statics. |
| View | `TerminalView.setTerminalViewClient` / `TerminalSession.updateTerminalSessionClient` | **Per-instance clients already supported.** Each pane can carry its own client — no shared singleton required. |

A shrunk pane reflows for free. Two `TerminalView` instances side by side each measure their own bounds and drive their own session's grid correctly today.

---

## The one real blocker: single-view singletons

The single-pane assumption lives almost entirely in three places:

1. **`TermuxActivity`** — `mTerminalView` (field `:193`), `getTerminalView()` (`:7184`), `getCurrentSession()` (`:7197`, defined *as* "what the one view shows"). Bound once in `setTermuxTerminalViewAndClients()` (`:5346`).
2. **Two shared client objects** — one `TermuxTerminalViewClient`, one `TermuxTerminalSessionActivityClient`. `getTerminalView()` is called **34×** in the view client, **8×** in the session client, **2×** in the activity. Every call assumes one active view.
3. **Layout host** — `terminal_surface_host` FrameLayout (`activity_termux.xml:48`) holding exactly one `terminal_view`. The `DrawerLayout` (session list) and the toolbar `ViewPager` (extra-keys only — *not* a session pager) are orthogonal and reusable as-is.

Switching today rebinds the one view (`setCurrentSession`/`switchToSession`/`attachSession`). Panes keep multiple views attached at once instead of rebinding.

---

## Hard parts (ranked by risk)

1. **Focus + IME retargeting** — highest risk. Android IME binds one `View.onCreateInputConnection`. Must retarget to the active pane on focus change. Three keyboard paths to test: soft keyboard, hardware keyboard, and the in-app keyboard (`TermuxInAppKeyboard`, currently single-view). **Mitigant:** per-instance clients mean each pane's view carries its own client — IME binds the active pane's view directly, cleaner than routing through a shared singleton.
2. **Screen real estate** — phone portrait: 2 panes ≈ 40 cols each, marginal. This is a *launcher* → likely used on large screens / DeX / landscape. Design for landscape/tablet first. No `layout-land` exists yet.
3. **`getTerminalView()` refactor mass** — 44 call sites to route to a "focused pane" concept. Mechanical but broad.
4. **Config-change persistence** — save/restore pane tree + session→pane bindings across rotation.
5. **Perf** — each visible view redraws on its session's screen update. 2–4 panes fine. Never render collapsed/hidden panes.

---

## Design: pane model

- Introduce **`activePane`** (focused pane) in `TermuxActivity`. `getTerminalView()` → `getActiveTerminalView()`; `getCurrentSession()` → active pane's session.
- Give **each pane its own** `TermuxTerminalViewClient` + `TermuxTerminalSessionActivityClient` instance (per-instance client support already exists). Callbacks land on the owning pane, not a global getter.
- Session store unchanged — `TermuxService` list stays canonical. A pane is a (view + client + attached session) binding. Layout state = a tree of splits over that list.
- Container: replace `terminal_surface_host` FrameLayout with a splittable `ViewGroup` (weighted `LinearLayout` for MVP; recursive H/V tree later). DrawerLayout wraps it unchanged.

---

## Phased plan

### Phase 1 — spike (validate the unknowns) — ~few days
Hardcode a 2-pane vertical split. No drag divider, no persistence, no tree. Goal: prove IME/focus/resize work with two live views.

**Exact files to touch:**

1. **`app/src/main/res/layout/activity_termux.xml`** (`:48`) — replace the single `TerminalView` inside `terminal_surface_host` with a vertical `LinearLayout` holding two `com.termux.view.TerminalView` (`terminal_view` + `terminal_view_2`), each `layout_weight="1"`, plus a 1dp divider view between.

2. **`TermuxActivity`** (`~:193`, `~:5342-5346`, `~:7184`, `~:7197`):
   - Add `mTerminalView2`, `findViewById` in `setTermuxTerminalViewAndClients()`.
   - Add `mActivePane` + `getActiveTerminalView()`; make `getTerminalView()`/`getCurrentSession()` delegate to it.
   - Construct a **second** `TermuxTerminalViewClient` + `TermuxTerminalSessionActivityClient` for pane 2; wire each view to its own via `setTerminalViewClient`.
   - On pane touch/focus → set `mActivePane`, draw active-pane border.

3. **Session bootstrap** — attach an existing (or new) session to pane 2 so both show live shells at launch. Reuse `addNewSession`/`createTermuxSession` path.

4. **In-app keyboard** (`TermuxInAppKeyboard` / `TerminalKeyEventHandler:344`) — retarget its `mTerminalView` to the active pane on focus change.

**Success criteria:** two shells visible + live; tapping a pane moves keyboard focus; each reflows to its own width; all three keyboard paths type into the focused pane only. If this works, the rest is mechanical.

### Phase 2 — usable feature
- Split / close-pane commands (extra-keys button + gesture). Session→pane binding UI in the drawer.
- Draggable divider (resize on drag; each view's `onSizeChanged` fires `updateSize` for free).
- Active-pane border/highlight. Max-pane gate alongside `MAX_SESSIONS`.

### Phase 3 — polish
- Recursive split tree (H+V nesting), not just 2-pane.
- Rotation / config-change persistence of pane tree + bindings.
- Landscape/DeX-tuned defaults. Don't render collapsed panes.

---

## Effort summary

| | |
|---|---|
| Engine changes | **none** |
| App-layer LOC | ~1500–3000 |
| Highest risk | IME/focus retargeting across 3 keyboard paths |
| De-risked by | per-instance client support already present |
| Recommendation | run Phase 1 spike; the only genuine unknown is IME/focus. If the spike types cleanly into the focused pane, ship it. |

---

## Status — 2026-07-22: N-pane controller WIRED (Phase A of "both together")

`TerminalPaneController` is now the source of truth. The fixed two-view layout is gone;
`activity_termux.xml` holds a single `terminal_pane_host` FrameLayout into which the controller
inflates one `view_terminal_pane.xml` per tree leaf. Unlimited recursive splits (tmux-style).

**Architecture**
- Each drawer tab = one primary session owning a binary pane tree (leaf=shell, split=orientation+2 children+weights).
- `mTerminalView`/`mActivePane` are live aliases to the focused pane, repointed by `PaneHost.onActivePaneChanged` — keeps the ~30 single-view call sites working, now acting on the active pane.
- `PaneHost` (inner class) bridges controller → activity: createShell, configurePaneView (client/font/size/keep-on/transparent bg), removeShell, onActivePaneChanged, onTreesChanged, defaultCwd.
- Bootstrap sessionless pane (`createBootstrapView`) gives a non-null active view during onCreate (in-app keyboard + font setup need it before the first tab).
- Accessory layout listener now watches `terminal_pane_host` (was `terminal_view`).
- Base `TerminalExtraKeys.mTerminalView` made settable; toolbar repointed to the active pane.
- `onSessionFinished` routes through `controller.onSessionFinished`: pane-with-siblings → drop pane + re-key tab if primary exited; only-pane → clean tree + fall through to normal session close.
- Compat mode: `collapseAll()` kills secondaries, restores single panes.

**Verified on device (100.101.173.85):** startup (no crash, nerd font), Ctrl+Alt+V side-by-side,
Ctrl+Alt+H stacked → 3 recursive panes, Ctrl+Alt+arrow directional focus, `exit` clean collapse +
reflow + primary re-key, thin Material active-pane border. Build: `:app:assembleDebug` green.

**Not yet driven by automation (needs hands-on check):** drawer session list filtering (logic
unchanged, delegates to `controller.isSecondaryPane`); in-app soft-keyboard Ctrl+Alt+letter binds
(hardware keyboard confirmed; soft-keyboard letter combos still bypass onKeyDown).

### Phase B — windows layer (NEXT, not built)
Session › **Window** › Pane-tree. A session owns N windows; each window owns a pane tree. Bind
`Ctrl+Alt+C` = new window, `Ctrl+Alt+X` = close window (currently toast "not implemented"). Needs a
window-select affordance (bind unspecified) + a window strip UI. `Ctrl+Alt+Shift+C/X` already do
new/close session.

---

## Status — 2026-07-22 (round 3): WINDOWS layer built + visual fixes

### Visual fixes
- **Double border**: the white line was the pane *divider* drawn next to the active border.
  `pane_divider.xml` now transparent (structural gap only).
- **Inactive pane borders**: added `pane_inactive_border.xml` (Material **secondary** tone). Active =
  primary tone, inactive = secondary tone — every split pane delineated, both grounded in accents.
- **Scrollbar**: `pane_scroll_thumb.xml` (2dp, `termuxColorScrollbar`), pane view now `scrollbars=vertical`
  + `fadeScrollbars` + 3dp size → thin, fades in on scroll, out when idle.
- **Constraint bug**: `refreshPaneSizes()` (posts updateSize on all visible panes) on
  onWindowFocusChanged(true) + onResume, so returning to the app re-measures against full size.

### Windows model (Session › Window › Pane-tree)
Controller is now **window-centric**: `Window` = one pane tree (stable object identity) + focused
leaf. `mWindows` holds all live windows; `mActiveWindow` renders. Sessions live in the activity as
`WSession { List<Window> windows; int current }`. Drawer = one row per session (its current
window's focused shell is the representative). `onSessionFinished` returns FINISHED_PANE /
FINISHED_WINDOW / UNKNOWN; a emptied window is dropped from its session via `onWindowEmptied`,
closing the session (and switching away / spawning a fresh one) when it was the last window.
`ensureWindowsForServiceSessions()` on service-connect wraps persisted shells (post-recreate) each
into their own session.

**Keybinds (final):** Ctrl+Alt+V/H split · Ctrl+Alt+arrows focus · Ctrl+Alt+Shift+arrows resize ·
Ctrl+Alt+C new window · Ctrl+Alt+X close window · **Ctrl+Alt+[ / ]** prev/next window (new default —
window-select bind was unspecified) · Ctrl+Alt+Shift+C new session · Ctrl+Alt+Shift+X close session.

**Verified on device:** split → new window (Window 2/2, fresh pane) → switch back (Window 1/2, split
intact, panes preserved) → close window (drops to sibling). No crashes. Build green.

**Deferred / needs hands-on:** window strip UI (windows currently keybind-driven + a "Window i/n"
toast, no persistent visual list); drawer multi-session verification via touch; closing the very last
session auto-spawns a fresh one (launcher can't sit with zero shells) — confirm that's desired.
