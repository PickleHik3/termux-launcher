# B-1 implementation plan — app drawer open/close choreography + gesture arbitration

Scope context: `/home/amal/termux-launcher/features-scope.md`. Repo: `app/termux-launcher`, branch `dev`.
Placeholder plane only — no app grid, no search pill, no A-Z rope. Those are B-2/B-3.

## Verified corrections to earlier assumptions

- `SuggestionBarView.PICKUP_DECISION_WINDOW_MS = 650L` (`SuggestionBarView.java:153`), not 200ms.
- `TermuxActivity.onBackPressed()` (`:9651`) has exactly three consumers: command palette, dock-tuning mode, nav `DrawerLayout`. Folder/app context popups are `PopupWindow`s that eat back themselves.
- Pre-blur LRU (`MAX_CACHED_WALLPAPER_BLUR_RADII = 3`, `TermuxActivity.java:663`) caches a **whole-screen** frame that every surface crops (`createCachedAccessoryWallpaperBlurCrop`, `:3914`). A full-screen drawer needs **no new radius** — reuse `getEffectiveExtraKeysBlurRadius()`.
- `com.termux.app.Spring` (critically damped, substepped, reduce-motion aware) already exists and backs `DockPlankController` + `TerminalCommandPaletteController`. No `androidx.dynamicanimation` needed.
- `getDrawer()` already means the left nav `DrawerLayout`. All new names use `AppDrawer*` / `app_drawer_*`.

## Architecture

The drawer is a **full-screen overlay plane**, NOT a taller accessory stack. It deliberately does not join
`AccessoryStackLayoutPolicy.computeCombinedHeight()` — that path runs the flush-padding solver and
`mTerminalView.updateSize()` (SIGWINCH); driving it per frame is unacceptable.

`@+id/app_drawer_host` is a child of `terminal_root_container`, inserted immediately before
`command_palette_host` (`activity_termux.xml:1641`). Sibling of `activity_termux_root_relative_layout`,
so it is full-bleed (applies its own inset) and paints over the whole accessory stack, but loses to the palette in z.

Seed rect = the dock glass rect (`accessory_surface_host` on-screen bounds, dock radius, dock inset), lerped to
full-screen. Since the seed rect is exactly the dock rect, the handoff is a 100ms cross-fade of two identical
rectangles — invisible.

While open or animating, accessory-stack **layout is frozen**: `applyAccessoryGeometryIfNeeded()` and
`setTerminalToolbarHeight()` early-return + set a pending flag, flushed on close. All dock/keyboard motion is
`translationY` / `setClipBounds` / `alpha` on existing views. Zero relayout, zero SIGWINCH.

Add `createCachedAccessoryWallpaperBlurCrop` (`:3914`) identity fast path: when
`targetRect.equals(mCachedAccessoryWallpaperBlurFrameRect)` return the cached frame instead of allocating a
second full-screen ARGB_8888 bitmap (~10MB/open on a 1080x2400 panel — the real jank risk).

**B-1 adds no gradle dependency.**

## New classes — package `com.termux.app.launcher.drawer`

| Class | Responsibility |
|---|---|
| `AppDrawerController` | Owns open/closed/dragging state, binds `@id/app_drawer_host`, runs the `Choreographer` + `Spring` loop, applies per-frame transforms, single back/lifecycle consumer. Mirrors `TerminalCommandPaletteController`. |
| `AppDrawerPlaneView` | The plane: glass slab via `DockGlassRendering.createGlassSurface(...)` clipped to an animated rounded rect through a `ViewOutlineProvider`; hosts the (empty) content frame; owns close-drag touch. |
| `AppDrawerGestureArbiter` | **Pure.** No View/Context. `(downPoint, currentPoint, slop, eligibility) -> PENDING / PAGE_SWIPE / DRAWER_DRAG / CHILD_OWNED`. |
| `AppDrawerTransitionGeometry` | **Pure.** Open-travel distance, drag→progress mapping, seed↔open rect/radius/inset lerps, `ramp(p, start, end)`. |
| `AppDrawerAccessoryChoreography` | **Pure.** `(dockStyle, progress, band rects, captured dock↔keyboard gap) -> translationY / clip-top / alpha` per band. Encodes both rounded and default recipes. |
| `AppDrawerCommitPolicy` | **Pure.** `(progress, velocityPxPerSec, direction) -> COMMIT_OPEN / COMMIT_CLOSE / CANCEL`. |

## Touch arbitration

One arbitration point: `SuggestionBarView.dispatchTouchEvent` (`:1515`). Nothing added to the per-icon
`OnTouchListener` (`:3809`) or `TermuxActivity.dispatchTouchEvent` (`:908`) except gating.

States:
```
IDLE --DOWN--> PENDING --+-- horizontal test ----> PAGE_SWIPE --UP/CANCEL--> SETTLING -> IDLE
                         +-- vertical-down test -> DRAWER_DRAG -UP/CANCEL--> SETTLING -> IDLE
                         +-- child claimed ------> CHILD_OWNED -UP/CANCEL-------------> IDLE
                         +-- no claim, UP ------------------------------------------> IDLE
```
Latching is one-way. Today `horizontalIntent` is recomputed every MOVE (`:1547-1552`); that becomes an
explicit `int gestureClaim` field.

Eligibility snapshot captured at `ACTION_DOWN` (`:1517-1538`) — `drawerEligible` requires all of:
`isAppLauncherDrawerEnabled()`; `TextUtils.isEmpty(lastInput.trim())`; `activeAzLetter == null`; not landscape
(apps row is GONE there — `buildAccessoryRenderState()` `:2646`); `!isDockTuningMode()` and palette closed;
`activeLongPressPickupState == null` and no active pinned drag; drawer not already open/animating.

Transitions on `ACTION_MOVE` (`:1540`), `slop = ViewConfiguration.getScaledTouchSlop()`:

| Test | Condition | Result |
|---|---|---|
| Drawer (evaluated first) | `drawerEligible && dy >= slop*1.15f && dy > abs(dx)*1.2f` | `DRAWER_DRAG` |
| Page (existing `:1550`) | `abs(dx) >= slop && abs(dx) > abs(dy)*1.1f` | `PAGE_SWIPE` |
| Child | `activeLongPressPickupState != null && state.menuShown`, or `notificationSwipeStarted`, or `dragStarted` | `CHILD_OWNED` |
| neither | — | stays `PENDING` |

1.2 > 1.1 leaves a deliberate neutral cone (~40-50° off vertical) where neither claims — that is what stops a
diagonal flick firing both. Upward drags never claim, so the notification swipe-up (`:3834`) is untouched by construction.

On entering `DRAWER_DRAG`:
1. `suppressContextLongPressForSwipe = true`; `cancelPendingContextLongPresses()`.
2. `dismissAppContextPopup(); dismissFolderPopup(); dismissShortcutsPopup();`
3. **Dispatch one synthetic `ACTION_CANCEL` through `super.dispatchTouchEvent(...)`.** After the claim we return
   `true` without calling super, so the pressed child would never see UP — `animateLaunchReleaseBounce` would
   never run and `activeLongPressPickupState` would leak a stuck pressed icon. Most bug-prone line in the slice.
4. `onDrawerDragBegin(downRawY)`.
5. Gate `TermuxActivity.feedDockPlank` (`:912`) on `!mAppDrawerController.isEngaged()`.

Gated existing branches: `:1547-1552` horizontal feedback → only when claim is `PAGE_SWIPE` or `PENDING`;
`:1571-1601` UP page-commit → only `PAGE_SWIPE`; `:1616-1626` CANCEL also forwards to the controller when
`DRAWER_DRAG`. `resetTransientVisualState()` (`:7124`) must early-return (or re-apply drawer progress) while a
transition is active — it is called from `onAttachedToWindow` (`:401`), `onWindowVisibilityChanged(VISIBLE)`
(`:421`) and `TermuxActivity.onNewIntent` home handling (`:840`), and unconditionally resets every child's
alpha/scale/translation. **Live regression, not hypothetical.**

Close: `AppDrawerPlaneView` runs the same machine in `CLOSING` mode — a downward drag anywhere on the plane
drives progress 1→0. Back press = instantaneous `close(fromBack=true)`, zero injected velocity.

## Animation

`p ∈ [0,1]`, 0 = dock, 1 = full drawer. During a drag `p` is the raw finger fraction (no easing — "finger follows").
Only the settle is animated.

Travel: `clamp(0.30f * rootHeightPx, dp(120), dp(260))`; `p = clamp01((rawY - downY - slop*1.15f) / travel)`.

Settle: `Spring(p, stiffness=420f, damping=41f)` (critically damped, `2*sqrt(420) ≈ 41`) ticked on `Choreographer`
exactly like `TerminalCommandPaletteController.doFrame` (`:344`), with `spring.vel = velocityPxPerSec / travelPx`
injected from the fling. Settles in ~260ms, matching `setTopStatusBarCollapsed` (`TermuxActivity.java:11013`).
`Spring.tick(reduced, dt)` already snaps at `ANIMATOR_DURATION_SCALE == 0`. No `ValueAnimator`/`PathInterpolator`
in the drag path; `PathInterpolator(.16f,1f,.3f,1f)` is reused only for the two discrete fades, via a shared
`INTERPOLATOR` field mirroring `TopPaneWidgetSlot.java:36`.

Sub-phases, `ramp(p,a,b) = clamp01((p-a)/(b-a))`:

| Channel | Ramp | Value |
|---|---|---|
| Dock lift | `ramp(p,0,.16) * (1 - ramp(p,.28,1))` | `translationY = -dp(8) * r` on the plane seed rect **and** `apps_bar_viewpager` + `apps_bar_az_row`. **Never** on `accessory_stack_container` — owned by `applyDockImeOffset` (`:4692`). |
| Glass handoff | `ramp(p,0,.10)` | plane glass alpha = r; `accessory_surface_host` alpha = 1-r |
| Pinned icons out | `ramp(p, .02+.012*i, .30+.012*i)` per child `i`, stagger capped at 8 | alpha 1→0, scale 1→0.92, `translationY += dp(6)*r` |
| A-Z row + indicator band | `ramp(p,.02,.26)` | alpha 1→0 |
| Extra keys + keyboard, **default** style | `ramp(p,.05,.55)` | one entity: `translationY = (extraKeysH + keyboardH) * r` on `terminal_toolbar_view_pager` and `inapp_keyboard_container` together; alpha = `1 - ramp(p,.35,.60)` |
| Extra keys + keyboard, **rounded** style | continuous in `p` | keyboard capsule top pinned to `planeFrame.bottom + capturedGapPx` (gap measured once at gesture start = `inapp_keyboard_view_host` top minus `accessory_surface_host` bottom, i.e. the 4dp `topMargin` at `:3146` plus stack spacing). Height shrinks as the plane bottom descends → padding preserved by construction. `setClipBounds` + `translationY`; alpha = `1 - ramp(p,.60,.92)`. Extra keys ride the same clip. |
| Plane rect | linear | `left/right = lerp(dockInsetPx, drawerInsetPx, p)`, `top = lerp(dockTop+lift, contentTop, p)`, `bottom = lerp(dockBottom+lift, contentBottom, p)` |
| Plane radius | linear | `lerp(seedRadius, openRadius, p)`; `seedRadius = isRoundedDockStyle() ? resolveDockCapsuleCornerRadiusPx(dockH) : 0`; `openRadius` from the new pref (-1 → `DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP = 20`). Bottom corners 0 in default style, `openRadius` in rounded. |

Close runs the same functions backwards — no separate close spec.

## Ordered steps

**Step 1 — preferences.** In `TermuxPreferenceConstants.java` after `MAX_APP_LAUNCHER_DOCK_CORNER_RADIUS` (`:184`):
```
KEY_APP_LAUNCHER_DRAWER_ENABLED       = "app_launcher_drawer_enabled"          default true
KEY_APP_LAUNCHER_DRAWER_CORNER_RADIUS = "app_launcher_drawer_corner_radius"    default -1
MAX_APP_LAUNCHER_DRAWER_CORNER_RADIUS = 40
```
`-1` = follow the dock/rounded token, like `DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS`. Accessors in
`TermuxAppSharedPreferences.java` follow `getAppLauncherDockCornerRadius()` (`:175-188`) including clamp-on-write.
Horizontal inset is **not** a new key — the plane reads `getDockHorizontalInset()`.
`SwitchPreferenceCompat app:key="app_launcher_drawer_enabled"` in a new `PreferenceCategory` in
`res/xml/launcher_preferences.xml` after the `settings_app_browsing_header` block (`:26-51`), plus two
`strings.xml` entries.
Reserved for later slices, do NOT add now: `app_launcher_drawer_view_type`, `app_launcher_drawer_icon_size`,
`app_launcher_drawer_grid_columns_vertical`, `app_launcher_drawer_grid_columns_horizontal`,
`app_launcher_drawer_grid_rows_horizontal`.

**Step 2 — pure classes first, test-driven.** `AppDrawerGestureArbiter`, `AppDrawerTransitionGeometry`,
`AppDrawerCommitPolicy`, `AppDrawerAccessoryChoreography` + their tests. Compile and pass before any view exists.

**Step 3 — layout.** `activity_termux.xml`: `@+id/app_drawer_host` (FrameLayout, `match_parent`,
`visibility="invisible"`, `clipChildren="false"`) immediately before `command_palette_host` at `:1641`, containing
`@+id/app_drawer_glass` (FrameLayout) → `@+id/app_drawer_wallpaper_backdrop` (ImageView, gone) +
`@+id/app_drawer_blur` (`RealtimeBlurView`, `realtimeBlurRadius="10dp"`). Structure copied from the palette block
(`:1647-1670`).

**Step 4 — activity seams** (`TermuxActivity.java`):
- `applyAppDrawerWallpaperFrost(ImageView)` beside `applyCommandPaletteWallpaperFrost` (`:4657`), using
  `getEffectiveExtraKeysBlurRadius()` directly (not `resolveTopGlassFrostRadiusDp()`, `:4551`), with its own
  `mLastAppDrawerFrostRect` / `mLastAppDrawerFrostRadiusDp` dirty guard.
- Identity fast path in `createCachedAccessoryWallpaperBlurCrop` (`:3914`).
- Expose `getDockHorizontalInsetPx()` (wrapper over `resolveDockHorizontalInsetPx`, `:2420`) and make
  `isRoundedDockStyle()` (`:2258`) public. `resolveDockCapsuleCornerRadiusPx` is already public (`:2502`).
- `getAppDrawerController()` lazy accessor mirroring `getCommandPaletteController()`.
- Freeze hook: early-return + pending flag atop `setTerminalToolbarHeight(boolean)` (`:8688`) and
  `applyAccessoryGeometryIfNeeded(...)` when the controller `isEngaged()`; flush on close (in a `finally`, and
  unconditionally in `onStart()`).
- Gate `feedDockPlank` (`:912`) on `!isEngaged()`.
- `onStop()` (`:4820`) and the `onNewIntent` home branch (`:838-844`): `closeImmediate()`.

**Step 5 — `AppDrawerPlaneView` + `AppDrawerController`.** Seed rect from
`accessory_surface_host.getLocationOnScreen()`. Cross-fade, spring loop, per-frame `applyFrame()`.

**Step 6 — `SuggestionBarView` arbitration.** `gestureClaim` field, eligibility snapshot, two new branches in
`dispatchTouchEvent` (`:1515`), synthetic cancel, `AppDrawerGestureListener` interface,
`getAppsRowScreenRect(Rect)`, `setDrawerTransitionProgress(float)` (pinned-icon fade with stagger),
`resetTransientVisualState()` guard (`:7124`). Wire the listener in `TermuxActivity.setSuggestionBarView()`
(`:5141`), beside `setLaunchRippleListener` (`:5217`).

**Step 7 — accessory choreography application.** Controller applies `AppDrawerAccessoryChoreography` output to
`terminal_toolbar_view_pager`, `inapp_keyboard_container`, `apps_bar_az_row`, `apps_bar_indicator_band`,
`apps_bar_az_fx_underlay/overlay`. Capture band rects and the dock↔keyboard gap **once** at drag begin —
re-measuring per frame reintroduces the feedback loop documented at `:2680-2690`.

**Step 8 — back ordering.** `TermuxActivity.onBackPressed()` (`:9651`) becomes:
```
isCommandPaletteOpen()          -> collapse                  (unchanged, first)
mAppDrawerController.isOpen()   -> close(fromBack = true)     <- NEW, second
mDockTuningMode                 -> exitDockTuningMode()
nav DrawerLayout open           -> closeDrawers()
else                            -> openDrawer(LEFT)
```
Palette stays first (transient, summonable over anything); the drawer is full-screen and must beat dock tuning
and the nav drawer, both conceptually behind it.

**Step 9 — full test pass + device verification.**

## Tests — `app/src/test/java/com/termux/app/launcher/drawer/`

- `AppDrawerGestureArbiterTest` (plain JUnit): straight-down claims DRAWER; straight-across claims PAGE; 45°
  neutral cone claims neither; upward never claims; latch is one-way; every eligibility veto (search text,
  A-Z active, menu shown, pinned drag, pref off, landscape) individually blocks the drawer claim while leaving
  the page claim intact.
- `AppDrawerTransitionGeometryTest`: travel clamps both ends; progress clamps [0,1]; `ramp` endpoints; seed rect
  at p=0 equals the dock rect exactly (invisible-handoff invariant); radius/inset lerp endpoints, both dock styles.
- `AppDrawerCommitPolicyTest`: progress >= 0.5 commits; slow release below 0.5 cancels; downward fling >= 900px/s
  with progress >= 0.12 commits; upward fling cancels regardless; closing direction mirrors.
- `AppDrawerAccessoryChoreographyTest`: rounded — dock↔keyboard gap byte-identical at p = 0, .25, .5, .75, 1;
  default — extra keys and keyboard share one translationY at every p; keyboard height never negative; both
  styles return identity at p=0.
- `AppDrawerSettleTest` (extends the existing `SpringTest.java` pattern): 420/41 spring 0→1 at 60fps settles in
  240-300ms; reduce-motion snaps in one tick; injected velocity never overshoots past 1.08.
- Robolectric (`@Config(sdk = P)`, matching `SuggestionBarPagingTest`): `SuggestionBarDrawerGestureTest` — a
  synthesized down/move/up stream fires `onDrawerDragBegin/Drag/End` exactly once and dispatches exactly one
  `ACTION_CANCEL` to children; `TermuxActivityBackOrderTest` — the drawer consumer sits between palette and
  dock tuning.

Run `./gradlew testDebugUnitTest`; read the XML in `app/build/test-results/`, not the exit code.

## Device verification — Nothing Phone, adb

```bash
./gradlew :app:assembleDebug && scripts/dev-install.sh
adb shell settings put global animator_duration_scale 1.0
```
1. **Baseline regressions first**, before touching the drawer: horizontal swipe pages the dock; long-press opens
   the app menu; long-press + horizontal drag picks an icon up; swipe-up on a badged icon opens the notification
   popup; tap launches.
2. **Open, both dock styles** (`rounded`, then `default`): slow drag from the icons row tracks the finger 1:1,
   dock lifts ~8dp then grows, pinned icons stagger out, no seam at the glass handoff.
3. **Keyboard choreography** with the in-app keyboard up: rounded → keyboard shrinks with a visibly constant gap
   under the plane bottom; default → extra keys + keyboard slide down as one block and fade. Reverse on close.
4. **Fling**: quick flick commits from ~15% progress. Slow 30% drag + release springs back, no icon left faded.
5. **Cancel**: drag to 40%, slide back up past the start, release → full restore. Second finger mid-drag → no
   double-claim.
6. **Back**: `adb shell input keyevent 4` and gesture-nav back both close; a second back opens the nav drawer.
7. **HOME**: `adb shell input keyevent 3` then relaunch → drawer closed, dock intact, no ghost transforms.
8. **Frames**: `adb shell dumpsys gfxinfo com.termux framestats` over ten open/close cycles — no frame > 16.6ms
   after the first open, and the **first** open must not spike (that would mean the identity fast path is missed).
9. **Blur cache**: `adb logcat -s TermuxActivity` while alternating dock/status-bar blur sliders with the drawer
   open — no `Failed to create cached accessory wallpaper blur`, no repeated re-blur (would mean a fourth radius
   is evicting the dock entry).
10. **Live wallpaper** (`getEffectiveExtraKeysBlurRadius()` returns 0) → tinted glass fallback, no frost, no crash.
11. **Gesture-nav bottom band, default style**: `mDecorNavBarSurfaceOverlay` is attached to `decorRoot` (`:2882`)
    and therefore paints **above** the plane — confirm it continues the drawer glass instead of cutting a line.
12. **Rotation**: open, rotate to landscape → closes cleanly (apps row does not exist there); rotate back → dock intact.
13. `animator_duration_scale 0` → open/close snap instantly, no stuck plane.

## Risks

1. **Stuck pressed icon / leaked `activeLongPressPickupState`** if the synthetic `ACTION_CANCEL` is omitted or
   double-dispatched. Highest-probability defect in the slice.
2. **`resetTransientVisualState()` stomping the transition** — called from `:401`, `:421`, `TermuxActivity:840`.
   Without the guard, HOME→relaunch mid-animation leaves pinned icons at alpha 0 permanently.
3. **Page swipe stolen by the drawer** — the 1.2 vs 1.1 dominance ratios must stay in that order, drawer test
   first, one-way latch.
4. **`accessory_stack_container.setTranslationY` collision** with `applyDockImeOffset` (`:4692`) — the dock jumps
   by the IME lift if the lift is applied there instead of to the row views.
5. **Frozen accessory geometry not flushed** — if `close()` throws, `setTerminalToolbarHeight` stays suppressed
   and the dock stops responding to style/height changes until recreate. Flush in `finally` + `onStart()`.
6. **Full-screen crop allocation** (~10MB/open) without the identity fast path — GC pause plus possible eviction
   of the 96-slot icon LRU.
7. **Dock plank tilt fighting the plane** — `feedDockPlank` must be gated.
8. **`suppressContextLongPressForSwipe` never cleared** on the DRAWER path (cleared only at `:1608`/`:1622`,
   which the drawer path returns before) → every later long-press on the dock silently no-ops.
9. **Palette + drawer both open** (hardware keybind) stacks two full-screen glass surfaces — drawer closes on
   `getCommandPaletteController().toggle()`.
10. **A-Z FX overlays** — `apps_bar_az_label_overlay` lives in `activity_termux_root_relative_layout` (`:1602`) at
    `match_parent` and can draw *over* the plane if not faded with the dock.
11. **Landscape / dock-rail mode** — apps row is GONE; eligibility must veto or the arbiter runs on a zero-height view.
