# B-2 implementation plan — vertical app grid, search pill, launch, long-press parity

Builds on landed, device-verified B-1. Owns nothing from B-3..B-7 (A-Z rope, horizontal view,
categories, folders, settings screen).

## 0. Verified constraints

| Claim | Verdict |
|---|---|
| `androidx.recyclerview` not a direct dep | **True** in `app/build.gradle`, but already compiled against at `launcher/PinnedAppsEditor.java:31-33` and `fragments/settings/MaterialPreferenceFragment.java:11`. Resolved version **1.1.0** (pulled up from `androidx.preference:1.2.1`'s 1.0.0 by `io.noties.markwon:recycler:4.6.2`). |
| 96-entry icon LruCache | **True**, `SuggestionBarView.java:238`, count-based. Each `RenderedIconDrawable` = **two** ARGB_8888 bitmaps of `sizePx²` (`:2561`, `:2591`, `:2598`). |
| Accessory geometry frozen while engaged | **True** — `TermuxActivity:5552`, `:8851`; flushed by `flushPendingAccessoryGeometry()` (`:9819`). |
| Work/clone apps via `LauncherApps` | **True** — `LauncherAppDataProvider.addProfileAppsForUser` (`:293`); launch must take `LauncherAppLauncher.tryStartProfileMainActivity` (`:111`). |
| `SystemEventReceiver` is the package-change seam | **FALSE.** It only handles Termux *plugin* packages. Real seam: `TermuxActivity.mPackageChangeReceiver` (`:367`) + `mLauncherAppsCallback` (`:12601`) -> `scheduleSuggestionBarPackageRefresh` (`:12834`) -> **`refreshSuggestionBarFromPackageState(boolean)` (`:12653`)**. |

**Latent bug to handle:** `LauncherAppDataProvider.invalidate()` (`:78`) does `pendingRefreshCallbacks.clear()`.
A drawer rebind callback registered via `warmAsync` just before an `invalidate()` (which `clearAppCache()`
triggers, `SuggestionBarView:836`) is **silently dropped** — the drawer would sit on an empty list forever.
The rebind must be idempotent and re-driven from `refreshSuggestionBarFromPackageState`, never rely on a
one-shot callback surviving an invalidate.

## 1. Decisions

### 1.1 RecyclerView: pin `androidx.recyclerview:recyclerview:1.1.0` explicitly
Zero behaviour change (1.1.0 already resolves). Not hand-rolled: a 200-400 app grid needs recycling, and
decisively `canScrollVertically(-1)` + `NestedScrollingChild` dispatch are the two primitives the
scroll/close arbitration is built on. Pin rather than float: RV 1.2+ adds API-31 stretch overscroll that
would double up with our spring. Also set `OVER_SCROLL_NEVER`.

### 1.2 Search input: no EditText, no IME summon — reuse the palette's focusless 3-channel intake
An EditText is wrong for four independent reasons: it steals focus from `TerminalView` (which owns the
terminal `InputConnection`); the in-app keyboard is not an IME and would need `beginExternalTextInput()`
(`:630`) which calls `requestAccessoryGeometrySync()` — exactly the path B-1 froze; the system IME would
need softInputMode changes that relayout the content root under an open plane; and it duplicates a solved
problem (`activity_termux.xml:1673` documents the same reasoning for the palette).

Three channels, mirroring the palette 1:1:

| Channel | Wire point |
|---|---|
| In-app keyboard | `TermuxActivity.setAppDrawerInterceptorActive(boolean)` -> `mInAppKeyboard.setKeyValueInterceptor(...)`. Safe to share the single interceptor slot with the palette: `TerminalCommandPaletteController.show()` already calls `getAppDrawerController().closeImmediate()` (`:211`) before setting its own. |
| Hardware keyboard | `TermuxTerminalViewClient.onKeyDown` (`:342`), new `handleAppDrawerKey` **after** the palette hook, **before** `handleTerminalAppSearchKey` (`:344`). Mirror in `onKeyUp` (`:572`). |
| System IME text | `TermuxTerminalViewClient.onCodePoint` (`:652`), new `handleAppDrawerCodePoint` after the palette twin. |

Reuse `com.termux.app.terminal.CommandPaletteSoftKeyDecision.decide(open, false, codePoint, ctrlDown)`
verbatim — already pure, public, palette-free. No twin class.

Consequence and feature: open the drawer and just type. No tap-to-focus.

**Keyboard reveal while open:** reveal fraction `k` in [0,1], own `Spring`, ticked in the *same* `doFrame`
loop. Plane bottom = `lerp(openRect.bottom, capturedPinTopPx - capturedGapPx, k)`; band results blended
toward identity by `k`. Transform-only — laid-out positions never move.
**Fallback when no in-app keyboard band:** `requestAppDrawerSearchKeyboard()` = `onSystemImeRequested()` +
`KeyboardUtils.showSoftKeyboard(this, mTerminalView)`, **no focus change**. Register a host
`addOnLayoutChangeListener` while open recomputing **only** `mOpenRect`.

### 1.3 Launch through `SuggestionBarView`, not `LauncherAppLauncher` directly
`SuggestionBarView.launchEntry(...)` (`:2681`) is a superset: same fallback ladder plus the clone-profile
branch (`:2688`), `LaunchAnimationContext`/`ActivityOptions` transitions, `dispatchLaunchRipple`,
`recordLaunch` (`:2795`) + `invalidateMostUsedCache()`, popup dismissal. Add
`launchEntryFromDrawer(View, LauncherAppEntry)` delegating to `launchEntryFromTouch` (`:2804`).
Deviation from the brief — call it out in the commit message.
Drawer cells must **not** call `registerLaunchTarget` (`:7227`) — they would clobber the dock's entries.

### 1.4 Icon cache: one shared, byte-budgeted cache
At density 4.0 a 48dp icon is 192px; one `RenderedIconDrawable` = display (147KB) + cleanArtwork (147KB)
= **~294KB**. A 96-entry count cache admits **~28MB**. Therefore:
1. `normalizedIconCache` (`:238`) becomes byte-budgeted: `sizeOf` = `getAllocationByteCount() × (1 + hasCleanArtwork)`;
   budget `clamp(memoryClassMB / 12, 6MB, 16MB)`.
2. **One cache, not two** — keys already carry `"@" + sizePx` (`:2543`), so sizes coexist and identical
   `(entry,size)` pairs are shared.
3. Drawer icon size capped at `dp(48)` in `AppDrawerGridMetrics`.
4. `setItemViewCacheSize(columns × 2)`, one shared `RecycledViewPool`, `setHasFixedSize(true)`.
5. Raw `entry.icon` drawables are already retained by the provider snapshot — the drawer adds nothing there.
6. Optional later: regenerate `cleanArtwork` on demand (used at only `:1258` and `:1337`) to halve cost.
   Held back — touches the dock hot path.
7. Optional: `onTrimMemory` -> `trimToSize(budget/3)` when closed.
`SuggestionBarIconCacheTest` asserts `size() == 0` after clear; `evictAll()` still zeroes a byte cache.

### 1.5 Long-press: make it callable, do NOT extract
`showAppContextPopup` (`:4202`) is 170 lines calling ~30 private helpers and mutating ~8 fields.
Extraction is a multi-thousand-line move. Instead, pure addition:
```java
public void bindDrawerAppContextLongPress(@NonNull View pressTarget, @NonNull LauncherAppEntry entry) {
    bindContextLongPressGesture(pressTarget, -1, false, () -> {
        dismissShortcutsPopup();
        showAppContextPopup(new AppMenuContext(entry, pressTarget, -1, null, null));
    }, null, null);
}
```
`bindContextLongPressGesture` (`:4054`) is already parameterised for this; `-1/false/null/null` is the same
configuration already used at `:2138` and `:7222`. It carries the whole interaction (press-down animation,
`LongPressPickupState`, slide-to-select, drag-back-to-cancel, release bounce) — cloning any part is how the
two menus drift. `showAppContextPopup` already recomputes `findPinnedAppIndex` when `pinnedIndex < 0`
(`:4206`), so Pin/Unpin resolves correctly with no branch.

**Dock call sites that must keep working:** `:2134` (pinned icon), `:2138` (non-pinned: A-Z preview,
most-used, search results), `:2131` (pinned folder -> `showFolderContextPopup` `:4416`), `:7222` (icon in
folder popup), `:4137`/`:4151` (notification-swipe popup path).

**One additive change to an existing method** — `showPopupAtAnchor` (`:5221`) always opens upward and clamps,
so a top-row drawer icon would cover itself. Add: `if (y < visibleFrame.top) y = location[1] + anchor.getHeight() + gap;`
then clamp as today. Dock icons sit at the bottom and always have room above, so dock placement stays
byte-identical. Pin with a test.

## 2. New classes — `com.termux.app.launcher.drawer`

| Class | Kind | Responsibility |
|---|---|---|
| `AppDrawerContentView` | `FrameLayout implements NestedScrollingParent3` | Search pill + grid; owns spring overscroll, close-arming state, nested-scroll close drag. |
| `AppDrawerAppsAdapter` | `RecyclerView.Adapter` | Binds entry -> icon + label; tap-to-launch, `bindDrawerAppContextLongPress`. |
| `AppDrawerSearchPillView` | `View` | Focusless query pill: hint, query, caret, clear. Radius from the shared token. |
| `AppDrawerSearchController` | plain | Query model owner, `KeyValueInterceptor`, hardware/IME routing, drives `LauncherRankingEngine`. |
| `AppDrawerSearchModel` | **pure** | Query + caret; insert/backspace/clear/moveCursor. No Android types. |
| `AppDrawerCloseArmingPolicy` | **pure** | The scroll-vs-close decisions. |
| `AppDrawerGridMetrics` | **pure** | Columns, cell size, icon size, paddings from width/density. B-7 feeds deferred prefs here only. |

Extended with new pure functions (existing signatures kept):
`AppDrawerAccessoryChoreography.blendTowardIdentity(Result, float reveal)` — must be byte-identical to the
input at `k = 0`; `AppDrawerTransitionGeometry.resolveSearchPlaneBottom(openBottom, pinTopPx, gapPx, reveal)`.

## 3. Scroll vs close-drag arbitration

### 3.1 The trap
The plane claims at `dy >= 1.15 × slop`. RecyclerView starts scrolling at `1.0 × slop` and immediately calls
`requestDisallowInterceptTouchEvent(true)`, killing the plane's interceptor for that stream. So a slow drag
goes to the RV and a fast flick (one big MOVE) goes to the plane — **non-deterministic ownership**.

Therefore: **when ACTION_DOWN lands on the grid the plane never claims.** The grid decides via nested
scrolling. No touch stealing; the parent's first-refusal hook (`onNestedPreScroll`) is used instead.

### 3.2 States
```
        DOWN on chrome (pill / margins / empty strip) ──► plane's own arbiter (B-1 path, unchanged)
IDLE ──DOWN──►
        DOWN on grid ──► grid owns stream ──► nested scroll ──► policy.claimOnPreScroll(...)
                                                                ├─ CLOSE_DRAG → consume dy, updateDrag
                                                                ├─ SCROLL     → child scrolls
                                                                └─ OVERPULL   → damp + translate
```
Captured at DOWN, never re-read mid-stream: `overGrid`, `atTopAtDown` (`!canScrollVertically(-1)`),
`scrollable`, `armed`, `armedAtMs`.

### 3.3 Rules
1. **Chrome** (pill, side margins, strip below the grid, reserved bottom cog band) -> B-1 close drag unchanged.
   Keeps a close affordance always available; the pill must not swallow vertical drags.
2. **Grid that cannot scroll either way** (few apps / filtered to two results) -> treated as chrome. Arming
   protects a scroll; there is no scroll to protect.
3. **Grid, `!atTopAtDown`** -> `SCROLL` for the whole stream, **even if it reaches the top mid-gesture**.
   This is what distinguishes "second deliberate swipe" from "continued scroll": the drag that carried you
   to the top is never the drag that closes.
4. **Grid, `atTopAtDown`, not armed** -> `SCROLL`; unconsumed downward delta becomes spring overscroll:
   `translation = OVERPULL_MAX_PX × (1 - exp(-raw / OVERPULL_MAX_PX))`, `OVERPULL_MAX_DP = 96`, released via
   `Spring(0, 900, 60)`. `OVER_SCROLL_NEVER` so the platform glow never doubles it.
5. **Arming** at `onStopNestedScroll(TYPE_TOUCH)`: overpull >= `dp(28)` **or** a downward top fling with
   `|velocityY| >= AppDrawerCommitPolicy.FLING_VELOCITY_PX_PER_SEC` (900 px/s — reused, not a new number).
   Record `armedAtMs`.
6. **Grid, `atTopAtDown`, armed, within `ARM_WINDOW_MS = 1200`** -> first `onNestedPreScroll` with `dy < 0`
   claims: `consumed[1] = dy`, deltas drive `beginDrag/updateDrag`. RV never scrolls, so no touch stealing and
   no `ACTION_CANCEL`. `onNestedPreFling` converts (`velocityPxPerSec = -velocityY`, unit-tested) into
   `endDrag`; slow release -> `onStopNestedScroll` -> `endDrag(0)`.
7. **Disarm** on: window expiry (checked at DOWN against `armedAtMs` — no Handler, keeps the policy pure);
   a gesture ending with `canScrollVertically(-1)`; any upward drag; a tap (a tap is a launch, not a
   dismissal); query change (list identity changed, scroll reset); `beginDrag`/`close`/`closeImmediate`/`onClosed`.

### 3.4 Exact changes to `AppDrawerPlaneView`
Untouched: `PLANE_ELIGIBILITY`, the arbiter use, `VelocityTracker`, `endTracking`, outline provider, `onDraw`.
1. New `interface CloseDragGate { boolean ownsPoint(float x, float y); }` + `setCloseDragGate(...)`.
   Null gate = exact B-1 behaviour (pinned by a test).
2. `beginTracking` (`:202`): after `mTracking`, set `mDeferToContent = gate != null && gate.ownsPoint(x,y)`;
   when true do **not** call `mArbiter.begin(...)`, do not obtain the `VelocityTracker`, return false.
3. `trackMove` (`:216`): first line `if (mDeferToContent) return false;`.
4. `endTracking` (`:228`): clear `mDeferToContent`.
5. New forwarders `beginCloseDragFromContent/updateCloseDragFromContent/endCloseDragFromContent/cancelCloseDragFromContent`,
   each guarded by `!mArbiter.isDrawerDrag()`, all funnelling into the same `Callbacks` — one path from
   gesture to controller.
6. `setFrame(...)` (`:140`): also `mContent.setVisibility(alpha <= 0.01f ? INVISIBLE : VISIBLE)`. An alpha-0
   view still receives touches; a full-screen invisible grid over the terminal is the worst regression here.
7. New `setContentInsets(Frame openRect)` — one-shot padding to the **open** rect from `prepareOverlay()`,
   never per frame. Content is laid out once for its final position; the growing outline reveals it.
8. `getContentHost()` keeps its signature.

Grid raw screen Y comes from `AppDrawerContentView.dispatchTouchEvent` recording `mDownRawY`/`mLastRawY` and
calling `super` — an observer, not an interceptor.
`setInteractive(boolean)` returns true from `onInterceptTouchEvent` when false, driven by `mOpen` **only**
(not `p`), so a close drag from the grid is never yanked mid-stream; `mNestedCloseActive` makes the duplicate
`onStopNestedScroll` after a cancel idempotent.

## 4. Ordered steps

1. **Dependency.** `implementation "androidx.recyclerview:recyclerview:1.1.0"`; confirm `:app:dependencies`
   still resolves 1.1.0 and nothing else moved.
2. **Pure classes + tests first** (B-1 discipline): `AppDrawerCloseArmingPolicy`, `AppDrawerGridMetrics`,
   `AppDrawerSearchModel`, plus the two additions to `AppDrawerAccessoryChoreography` /
   `AppDrawerTransitionGeometry`.
3. **`SuggestionBarView` public seams** (all additive except the `showPopupAtAnchor` flip):
   `bindDrawerAppContextLongPress` (beside `:4025`), `launchEntryFromDrawer` (beside `:2804`),
   `getRenderedIcon(entry, sizePx)` -> `iconForDisplay` (`:2536`), `applyIconColorFilter(ImageView)`,
   `getLauncherTextColor()`, `dismissContextPopups()`, the `showPopupAtAnchor` flip (`:5221`), and the
   byte-budgeted `normalizedIconCache` (`:238`).
4. **`AppDrawerGridMetrics` + `AppDrawerAppsAdapter` + cells.** Cell = vertical `LinearLayout` -> `ImageView`
   (`getRenderedIcon`, `applyIconColorFilter`, `contentDescription = entry.label`) + `TextView` (11sp, single
   line, ellipsize END). Built programmatically like every other launcher surface here (`createEntryButton:2637`,
   `createPopupEntryButton:7209`) — no new XML. Defaults: `columns = clamp(round(planeWidthDp / 84), 4, 6)`,
   `iconPx = min(dp(48), cellWidth × 0.58)`, row height `= iconPx + dp(6) + labelHeight + dp(10)`.
   **The deferred prefs are not read, written or declared in B-2.**
5. **`AppDrawerSearchPillView` + `AppDrawerSearchController`.** Pill radius
   `min(controller.getOpenRadiusPx(), pillHeight / 2f)` from `resolveOpenRadiusPx()` (`:507`) — the same token
   the plane uses, **no literal**. Search runs `LauncherRankingEngine.filterAndRank(...)` on the main thread
   (pure, already run per keystroke by the dock at `:1936`); tolerance from the dock's existing source.
   Empty query -> full catalogue in provider order. Query change -> scroll to 0 + disarm.
6. **`AppDrawerContentView`.** Pill + `RecyclerView(GridLayoutManager)`, `OVER_SCROLL_NEVER`,
   `setHasFixedSize(true)`, `setItemViewCacheSize(columns × 2)`, shared pool. Implements
   `NestedScrollingParent3` + `CloseDragGate` + overscroll spring + arming, decided by the pure policy.
7. **`AppDrawerPlaneView`** changes per 3.4.
8. **`AppDrawerController` wiring.** `bindViews()` (`:366`) builds the content into `getContentHost()`,
   `plane.setCloseDragGate(content)`, `content.setCallbacks(this)`. `prepareOverlay()` (`:517`):
   `setContentInsets(mOpenRect)`, `setSurfaceRadiusPx`, `setMetrics(resolve(...))`, `bind(provider, search)`.
   `applyFrame` (`:560`): plane bottom via `resolveSearchPlaneBottom(...)` with reveal `k`;
   `applyAccessoryBands` uses `blendTowardIdentity(result, k)`; `k` ticked in the **same** `doFrame`.
   `settle`/`onClosed` (`:645`): `setInteractive(mOpen)`, `resetSearch()`, `disarm()`,
   `setAppDrawerInterceptorActive(mOpen)`, `setVisibility(INVISIBLE)`, `stopOverpullSpring()` — inside the
   existing `try`, `finally` flush untouched. New `onAppCatalogChanged()`, `onBackPressedInDrawer()`
   (true when it only cleared a non-empty query), and a host layout listener recomputing only `mOpenRect`.
9. **`TermuxActivity` seams.** `setAppDrawerInterceptorActive` (beside `:9847`), `handleAppDrawerKey` /
   `handleAppDrawerCodePoint` (beside `:9858`, `:9866`), `requestAppDrawerSearchKeyboard()`.
   `refreshSuggestionBarFromPackageState` (`:12653`): at the **top**, before the
   `!isSuggestionBarEnabled() || mSuggestionBarView == null` guard, call
   `mAppDrawerController.onAppCatalogChanged()` — the drawer refresh must not be gated on the dock's
   enablement, and must re-drive `warmAsync` itself. `onBackPressed()` (`:9880`): drawer branch becomes
   `isOpen() && !onBackPressedInDrawer()` -> `close(true)`. Palette-first ordering untouched.
10. **`TermuxTerminalViewClient` hooks.** `onKeyDown` (`:342`) drawer after palette, before
    `handleTerminalAppSearchKey` (`:344`); `onKeyUp` (`:572`) swallow releases; `onCodePoint` (`:652`) after
    the palette twin, before the `\r`/`\n` hook.
11. **Warm-up measurement.** Build content on first `beginDrag`. Measure first-open cost with
    `gfxinfo framestats`; if it janks, move the warm beside `scheduleLauncherCatalogWarmup()` (`:12901`,
    450ms post-resume) rather than onto the touch path.
12. **Full test pass + device verification.**

## 5. Tests (baseline 939, both variants green; read the XML)

Pure JUnit4: `AppDrawerCloseArmingPolicyTest` (first top pull does not close; arming needs overpull >= dp(28)
or >= 900px/s fling; second pull within 1200ms closes, at 1201ms does not; a pull starting mid-list never
closes even after reaching the top; upward disarms; non-scrollable closes immediately; chrome always closes;
tap disarms) · `AppDrawerGridMetricsTest` · `AppDrawerSearchModelTest` ·
`AppDrawerAccessoryChoreographyTest` extended (`blendTowardIdentity(r,0)` byte-identical at p = 0,.25,.5,.75,1;
identity at k=1; monotonic in k) · `AppDrawerTransitionGeometryTest` extended ·
`AppDrawerNestedVelocityTest` (fling sign conversion both directions — easiest thing here to get backwards,
mirrors the `settle()` sign note at `AppDrawerController:303`).

Robolectric (`@Config(sdk = P)`, matching `SuggestionBarDrawerGestureTest`): `AppDrawerPlaneCloseGateTest`
(gate owns point -> no `onPlaneDragBegin`; null gate -> B-1 unchanged) · `AppDrawerContentViewTest` ·
`SuggestionBarDrawerPopupTest` (same popup shape as the dock; flip-below for a top anchor, above for a bottom
anchor) · `SuggestionBarDrawerLaunchTest` (routes through `launchEntryFromTouch`; usage stats recorded; clone
entry takes the profile branch) · `TermuxActivityDrawerIntakeTest` · `TermuxActivityBackOrderTest` extended ·
`SuggestionBarIconCacheTest` extended.

## 6. Device verification — HTC 10 `HT66PBN06539`

`screencap` costs ~6.5s: **four captures total**. `input motionevent` holds the pointer down between calls.
Read the apps-row Y from the first capture rather than guessing.

1. **B-1 regression sweep first** — page swipe, long-press menu + slide-to-select, pickup drag, badged-icon
   swipe-up, tap launch. **[capture 1]** of the dock menu.
2. **Open + grid** — motionevent drag, **[capture 2]** mid-drag, release; scroll; tap a cell to launch; HOME back.
3. **Arbitration (core):** (a) mid-list flick down scrolls, does not close; (b) continue to the top in the
   *same* gesture and keep pulling -> overscrolls, does **not** close; (c) release then immediately flick down
   -> **closes**; (d) release, wait 2s, flick -> does not close, re-arms; (e) up-then-down in one stream ->
   no close; (f) drag down on the **pill** -> closes immediately.
4. **Short list** — filter to no/two matches; first downward grid drag closes.
5. **Search, in-app keyboard on** — type; keyboard rises above the plane bottom with the gap preserved;
   results filter live. **[capture 3]**. Backspace to empty -> keyboard recedes. Confirm logcat shows **no**
   accessory-geometry activity during the reveal.
6. **Search, in-app keyboard off** — tap pill, system IME rises, typing filters, plane recovers on dismiss.
   Roughest path; watch for a plane bottom left behind the IME.
7. **Hardware keyboard** (if pairable) — type with nothing focused; Enter launches top result; Esc closes.
8. **Long-press parity** — top-row icon opens the menu **below** itself; bottom-half icon opens above.
   **[capture 4]** of the top-row menu.
9. **Package change while open** — `install -r` / `uninstall`; grid updates within ~1s, query and scroll
   survive, no dead cell. Repeat with a popup open on the removed app.
10. **Clone / work profile** — `pm list users`; if a profile exists confirm `· Clone N` entries launch into the
    right user (`dumpsys activity activities | grep mUserId`).
11. **Frames** — `gfxinfo framestats` over ten open/scroll/close cycles. NOTE: this device's *baseline* is
    ~61% janky at 1440p, so compare against a control run, never against 16.6ms absolute.
12. **Memory** — `dumpsys meminfo com.termux` before first open and after a full catalogue scroll; Java heap
    delta within the cache budget (<= 16MB) and no further growth on a second scroll.
13. **Back / HOME / rotation / reduce-motion** — back clears a non-empty query then closes; HOME then relaunch
    leaves no ghost transforms and a touchable terminal; rotate while open closes cleanly;
    `animator_duration_scale 0` snaps.
14. **Terminal still alive** after ten cycles — tap, type, confirm output. Catches the invisible-grid-eating-
    touches regression, which is silent otherwise.

## 7. Regression risks

1. **Full-screen invisible grid eating terminal touches after close** — highest severity, silent. Mitigated by
   the `setFrame` visibility flip, `setInteractive(false)` on close, and not building content before first drag.
2. **Plane stealing the grid's scroll on a fast flick** (1.15 vs 1.0 slop race against
   `requestDisallowInterceptTouchEvent`). The `CloseDragGate` is the fix; without it the failure is intermittent.
3. **`invalidate()` clearing `pendingRefreshCallbacks`** -> empty grid after a package install.
4. **`suppressContextLongPressForSwipe` shared with the dock** — a future leak now kills drawer long-presses too.
5. **`cancelPendingContextLongPresses()` only walks `SuggestionBarView`'s children** (`:4192`) — drawer cells
   are not children, so the drawer must call `View.cancelLongPress()` on its own cells when a close drag claims.
6. **Frozen accessory geometry** — the reveal must be transforms only; any stray
   `setTerminalToolbarHeight`/`applyAccessoryGeometryIfNeeded`/`requestAccessoryGeometrySync` produces a visible
   jump on close. This is exactly why `beginExternalTextInput()` is not used.
7. **Shared icon budget** — a long drawer scroll can evict dock-sized entries, costing a re-render hitch on the
   next dock rebuild. Acceptable but a real behaviour change.
8. **`showPopupAtAnchor` flip** touches a method every dock long-press goes through; guard is unreachable for
   bottom-anchored dock icons but must be test-pinned.
9. **Single-occupancy interceptor slot** — palette and drawer must stay mutually exclusive.
10. **System-IME fallback relayouts the content root**, invalidating `mOpenRect`; the host layout listener covers it.
11. **Rotation while open** — column count must be re-resolved in `prepareOverlay`, not cached across configs.
12. **RecyclerView version drift** to 1.2+ stacks stretch overscroll on our spring. Pinned + `OVER_SCROLL_NEVER`.
13. **Robolectric + RecyclerView** — drive `AppDrawerContentView`'s nested-scroll methods directly, never RV internals.
14. **Popup anchored to a recycled cell** — disarm and dismiss popups on any scroll or query change.
