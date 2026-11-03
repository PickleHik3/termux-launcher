# Code health review: band-aids, races and the mechanisms to replace them

Scouted 2026-11-01 at `4ebbf4449` (v2-redesign). Seven read-only scouts covered the app by area;
every site listed under "Verified" was then read by the reviewing session. Sites under
"Scout-reported" were not re-read and need a look before anyone acts on them.

Scale for reference: 529k lines of main Java; `TermuxActivity` is 35.3k of them and took 121 of
the fix commits of the last sixty days (the next file took 31). About 1,000 lines carry a timing
or compensation signal (`postDelayed`, backstop, retry, workaround, cross-thread flag).

The architecture the project already has (pure policies, one owner per two-phase number, holds,
stills, the motion log and its invariants, ADR 0012's one-owner crossing) is the right shape and
is not questioned here. What this review found is the layer *around* it: the code that was added
when a signal did not arrive, a state went stale, or an order came out wrong, and that was kept
once the symptom went away. Each section names the pattern, the sites, and the one mechanism
that would replace the copies.

## 1. Thirteen hand-rolled "signal or give up" holds, and none of them say when they gave up

The same shape is implemented thirteen times: wait for a signal (a draw, a layout, a callback),
or N frames, or a backstop in ms, then release; each with its own `Runnable` field, its own
`removeCallbacks`, its own "is this still my hold" identity check, and its own frame counter.

| Site | Signal | Backstop |
|---|---|---|
| `terminal/GeometryScheduler.java:144,378-405` | the pass's layout | 500 ms |
| `terminal/ReturnResizeHold.java:41-47` | 2 stable frames | 600 / 1500 ms |
| `inappkeyboard/KeyboardGeometryChoreographer.java:45-51,488-530` | first draw | 3 frames, 160 ms |
| `x11/X11PaneFrame.java:975-1011` | height still for 48 ms | 160 ms |
| `x11/X11PaneFrame.java:1195-1205` + `wall/SurfaceStandIn.java:46` | surface drew | 2 frames, 300 ms |
| `wall/FirstDrawGate.java:26,58` | `onDraw` | 100 ms |
| `SuggestionBarView.java:1924-1935` | dock morph settled | 500 ms |
| `SuggestionBarView.java:609,640,3966,10008` | stable bounds | 8 attempts, 180 ms |
| `surfaces/AppearanceSurfaceController.java:282-284` | commit | 20 s |
| `surfaces/WallpaperApplyHold.java:35-45` | colours callback | 500 + 1600 + 400 ms |
| `statusbar/WindowForegroundResolver.java:146-164` | resolver reply | 8 s |
| `TermuxActivity.java:29526-29566` (`DisplayTouchpadWait`) | a layout | 3 × 160 ms |
| `TermuxActivity.java:27528-27541` + `x11/DisplayViewFocus.retryAfterLayout` | keys landed | 3 layouts |

Three of them carry a comment saying their 160 ms "is the keyboard reveal gate's backstop, for the
same reason", and `PICTURE_HANDOVER_MS` (`TermuxActivity.java:24958`) is 200 "past the 160". The
number appears four times by cross-reference, which is exactly the thing AGENTS.md's coding style
forbids.

Only `ReturnResizeHold` names its reason when the timeout wins (`REASON_BACKSTOP`). No site logs
to `MotionLog`, and the motion invariants cover three outcomes, none of which is "a backstop
fired". So when a frame on pong is produced by a fail-safe rather than the design, nothing says so.

**Mechanism.** One `Hold` class in `app/terminal` (or `chrome`): `open(name, signal | frames |
backstopMs)`, `release(reason)`, generation-checked so a superseded hold is a no-op. When the
backstop wins it records `MotionLog` `backstop <name> <ms>` and counts as an invariant breach in
staging builds. The thirteen sites become thirteen named holds, the 160 ms becomes one constant,
and a week of pong use tells you which "signal that never comes" is real and which fail-safe can
go. This is the highest-information change in the review and the smallest.

## 2. Watchdogs standing in for a cause nobody found

- **The blur heartbeat.** `chrome/ChromeRenderer.java:302-303,360-392,909-925`: every five minutes,
  if the activity is visible and blur is on, it asks `isAccessoryBlurHealthy`
  (`TermuxActivity.java:9002`), which checks that the dock's blur backdrop `ImageView` is still
  VISIBLE with a non-null drawable; if not, it marks every backdrop dirty and re-syncs. A 120 ms
  "recovery retry" runs after every `SCOPE_BLUR_HEALTH` request as well. The ledger
  (`SurfaceDirtyLedger`) exists so that the backdrop can only change through a recorded apply; a
  backdrop that loses its drawable outside the ledger is the bug. Find the writer (grep
  `setImageDrawable(null)` and `setVisibility` on `accessory_blur_backdrop` and
  `mDecorNavBarBlurBackdrop`), route it through the ledger, delete the heartbeat, and keep the
  health predicate as a staging invariant.
- **The system keyboard show retry chain.** `terminal/TermuxTerminalViewClient.java:103-109,
  1120-1206`: four constants (500, 140, 300, 320 ms), each with a device story ("devices whose
  resumed window becomes IME-ready later"), posting the same show runnable up to three times per
  resume, on top of upstream's own 500 ms retry in `termux-shared/.../KeyboardUtils.java:26-28`
  ("may not automatically open on some devices, but still may fail"). The constants date from
  April 2026. The IME has signals: `onWindowFocusChanged(true)` and the IME inset in
  `onApplyWindowInsets`. Show once on window focus, and once more only if the IME inset is still
  zero at the next insets dispatch. Three of the four constants go.
- **The keys safety net.** `TermuxActivity.java:27515-27600`: `returnKeysToTerminal` retries a
  focus request after each of the next three layouts, and `routeKeyNobodyHolds` runs on *every*
  `dispatchKeyEvent` to deliver a key to whatever is in front when no view holds focus, then asks
  for focus again. The comment says the window exists because "a focus request made while the
  display goes can come to nothing". `DisplayStopSequence` already owns the display's departure as
  a sequence; hand the keys back as its last step, after the X view is detached, and the window
  closes. Keep `routeKeyNobodyHolds` as a logged invariant ("a key arrived with no holder"), not as
  a feature that types.
- **The dock's deferred render.** `SuggestionBarView.java:600-650,3955-3990,10000-10110`: three
  mechanisms for "render once my bounds are stable": a retry budget of 8, a 180 ms anti-flicker
  window, and `renderLostWhileUnstable` which "outlives" the budget, plus `rowHeightHintWaived`
  which gives up on the dock's height hint after the window. The comment at :3963 cites
  Robolectric's `idle()` livelocking as the reason for the bound. A view has one true signal for
  stable bounds: `onLayout` with a changed size. Render from there; the budget, the window and the
  waiver are what you add when you render from somewhere else.
- **`DisplayTouchpadWait`** polls for a layout three times at 160 ms. Same answer: the pad's host
  knows when it lays out.

## 3. Dead and vestigial timing code (delete)

- `terminal/TermuxTerminalSessionActivityClient.java:403-405`: `shouldDeferForegroundScreenRefresh`
  returns `false`. Everything behind it is dead: `FOREGROUND_REFRESH_DEFER_MS` (:66),
  `mForegroundRefreshPending` (:86, :143, :237, :269), `scheduleDeferredForegroundRefresh` (:407)
  and `mForegroundTerminalRefreshRunnable`. From `fbb4a74c6` (2026-04-13).
- `terminal/TermuxActivityRootView.java:72-86,149,318-339,462-465`: two clocks for one settle
  (`System.currentTimeMillis` at :318/:336, `SystemClock.uptimeMillis` at :149/:465), two public
  mutable timestamps, an 18 ms "roughly one display frame" debounce and a 40 ms settle, under a
  comment that admits "some changes may be missed". The `onMeasure` comment says margin writes are
  now deferred to the geometry pass; `GeometryScheduler` is that pass. Move the margin decision in
  and delete the clocks.
- `TermuxActivity.java:1569,11413`: `mArrangementAutosaveArmed` is set once and never cleared. It
  is a latch ("shells have been ready once"); name it so, or derive it.
- `TermuxActivity.java:34483`: `consumePendingStyleReloadRecreateActivity` resets the recreate
  flag to `true` after consuming, so the next bare reload request recreates unless something
  clears it first. Check whether that is intended; it reads like a leftover.

## 4. Two mechanisms for one request

- **Style reload.** `TermuxActivity.java:1534-1540,2588-2594,2631-2642,34448-34490`: a request
  both sends a broadcast (for a live activity) and sets process-wide static flags (for the next
  resume), with `pendingRecreateAfterRequest` added to keep the two from cancelling each other
  ("a recreate already owed stays owed"). One owner: a `PendingReload` held by `TermuxApplication`
  with `request(recreate)` and `consume()`, read in `onResume`; the receiver goes, or the statics
  go. Not both.
- **Handlers.** Ten `new Handler(Looper.getMainLooper())` in `TermuxActivity` (:595, :597, :1219,
  :1451, :1875, :6045, :9155, :9840, :23057, :26528), 128 across the app.
  `scheduleWindowLabelPoll` (:31173) calls `removeCallbacksAndMessages(null)` on one of them,
  which is why `noteShellActivity` (:31071) documents that it must use a *different* handler or
  its coalescing flag sticks. Rule: one main handler per class, cancel by runnable or token, never
  `removeCallbacksAndMessages(null)`.
- **The foreground label.** Three timers answer one question: the 2 s poll at :31173, the
  resolver's own 1500 / 5000 ms throttle (`WindowForegroundResolver.java:146-152`) and its 8 s
  in-flight timeout (:164). The resolver should own the cadence; the activity asks once per
  change.
- **A timer and a clock for one window.** `x11/X11PaneFrame.java:1340-1345` (still expiry: a
  timer and a polled uptime check), `launcher/widget/WidgetEditOverlayView.java:275-349` (a posted
  fade and an elapsed-time check), `surfaces/SurfaceLongPress.java:215-230`,
  `x11/DisplayTextFocusPolicy.java:67-70` (two 150 ms windows with separate names). Pick one per
  site.
- **Settle by quiet period.** `x11/X11PaneFrame.java:975-1011` releases a host-size hold when the
  height has not moved for 48 ms, with a 160 ms ceiling and a layout-change follower that re-arms
  the 48. The thing that moves the height (the keyboard choreographer, the accessory stack) knows
  when its last pass lands; ask it, and the quiet period goes. Same for
  `launcher/widget/WidgetGridView.java:446-456` (160 ms size-delivery settle) and
  `dock/PeekGlassReflection.java:523-524` (160 ms still settle).

## 5. Global mutable state

- `TermuxActivity`: `sPendingStyleReloadOnNextResume`, `sPendingStyleReloadRecreateActivity`,
  `sPendingAppDrawerReloadOnNextResume` (§4), `sFirstShellStarted` (:1550, written from three
  lifecycle methods), `sProcessPaneLayout` (:1558, a `Bundle` cached process-wide because the
  activity "comes back without instance state"), `sLiveActivity` (:23036, a `WeakReference` read
  from any thread), `sHomeWallpaperInPlace` (:23040, a public static `BiConsumer`).
- `termux-shared/.../TermuxAppSharedPreferences.java:66,3403-3421`: `sTerminalPageId` is a static
  volatile read at about 30 sites inside the inheritance resolver. Every preference getter's
  answer silently depends on a static set elsewhere; no test references it. Make the page an
  explicit parameter of the resolver, or a resolver instance the activity holds.
- `chrome/scene/LiveSceneFrame.java:58-80,119-124`: a block of statics whose thread contract is
  the comment "Main thread only from here"; `sWindowMaxDp` and `sWindowStartMs` are plain while
  their neighbours are volatile. Make it an instance the scene controller owns.
- `terminal/TermuxActivityRootView.java:80`: `static int mStatusBarHeight`, named as an instance
  field.
- `theme/LauncherSchemeTheme.java:59-62`: `sCachedTokens` is an unsynchronised static
  `LinkedHashMap`.
- Singletons without `volatile` or a lock on read: `PrivilegedBackendManager.java:27`,
  `PrivilegedLaneServer.java:49`, `LauncherCtlApiServer.java:83`,
  `LauncherCtlNotificationStore.java:49`.

## 6. Cross-thread hazards (scout-reported; reproduce or test before fixing)

The launcher side is mostly clean: background work hands back through `runOnUiThread`, a posted
runnable or a generation check, and the terminal emulator's reader and writer threads touch only
the `ByteQueue`. The candidates below were reported by scouts and not re-read here.

- `ai/TaiRuntimeService.java:128-160`: `watchEpoch` and `benchWakeLock` appear to be written on
  the binder thread and read on the watch scheduler without `volatile`.
- `launcherctl/LauncherCtlApiServer.java:112-121,355`: the accept-loop `Future` is not volatile;
  the LAN session deadline compares `System.currentTimeMillis` (wall clock, jumps).
- `ai/TaiDownloadHub.java:229-235`: `emitScheduled` is checked inside `synchronized` and the post
  happens outside it.
- `ai/TaiPlatformCaps.java:393-395`: a verdict spread over three separate volatiles can tear.
- `terminal/RetroEffectPrewarm.java:32-42`: static `sRequested` written from a background thread.
- `terminal-view/.../AccessibilityTextUpdates.java:56-66`: `mPending` is a plain boolean.
- `terminal-emulator/.../TerminalEmulator.java:4911`: the upstream TODO about `mCursorCol` /
  `mCursorRow` synchronisation is still there.
- `terminal-emulator/.../ShellTerminator.java:86-96`: check-then-act between the process-table
  lookup and `SIGKILL`, mitigated by the `livePid` compare.
- `launcher/data/LauncherCategorySortService.java:585`: its own comment calls the notification
  path a race with the foreground notification.

**Mechanism for the launcher side.** Generation tokens are already the house answer, in five
spellings: `SurfaceStandIn.mEpoch/mGeneration`, `NotificationsWidgetView` tickets,
`AsyncIconBinder.generation`, `LauncherIconStore` / `DockIconCache` epochs,
`WeatherPlaceSearchView` generation. One `Generation` helper (`next()`, `isCurrent(token)`) and
the rule "an async result carries the generation it was asked under" replaces the five and the
`isAttachedToWindow()` / `isFinishing()` re-checks inside posted runnables.

## 7. Numbers owned twice (beyond the motion-invariants audit)

The audit in `motion-invariants-2026-10-30.md` §2 already tracks the eight two-phase numbers;
pair 1/2 is still live: `TermuxActivity.java:17436-17440` (`resolveAccessoryStackBottomMarginPx`)
builds `(lifted ? imeLiftPx : 0) + edgeGapPx` with the same Peek-card logic as
`place/StackLayout.java:220-228`. The activity should ask `StackLayout`.

New ones:

- `surfaces/SurfaceEditorController.java:4433-4436`: corner radius clamped to a literal 0–40
  outside `DockLayoutPolicy`.
- `SuggestionBarView.java:9820,9842`: `dp(4.2f)` twice as a lift amount.
- `TermuxActivity.java:1433,10481`: `mDockTravelTranslationPx - mDockImeLiftPx` twice.
- `SuggestionBarView.java:1578,9092`: `swipeVisualOffsetPx + swipePreviewDirection *
  pageAxisLengthPx()` twice.
- Animation durations as literals: 160 (`SuggestionBarView.java:3404,4436`,
  `WidgetPaneController.java:1810,1874`), 120 (`WidgetPaneController.java:1944,1954`,
  `SuggestionBarView.java:3374`), 96 (`SuggestionBarView.java:3310`,
  `LauncherAzGestureFxView.java:713,1075`). `DEBOUNCE_MS = 300` declared in three widget sources;
  `3500` as a literal at `WidgetPaneView.java:748` beside `HINT_MS = 3500` at
  `WidgetPickerSheetView.java:62`.

## 8. Swallowed exceptions

`launcher/widget/LauncherWidgetHostController.java` (32 catch sites, several resolved by "the
reconciliation pass retries"), `launcherctl/LauncherCtlNotificationListener.java` (about 20, five
with `ignored`), `launcher/LauncherAppLauncher.java:348-396` (`catch (Throwable)` → fall back to
`am` with no log), `chrome/ChromeInk.java:2414,2461`, `chrome/GlassVibrancy.java:64`,
`chrome/IconColor.java:80`, `statusbar/PinnedAvatarCache.java:117`, `TermuxActivity.java:6149,
34633` (empty bodies). Most are defensible as "draw plain this frame"; none say so in logcat.
**Mechanism:** a once-per-site, rate-limited `Quiet.log(tag, t)` so a swallowed error still
appears once in a bug report's log.

## 9. The activity

322 public methods, 28 runnable fields, ten handlers; 116 of the cross-file calls into it are
`postDelayed`, which means other classes use the activity as their handler. The AGENTS.md seam
rule stops the growth; the extractions with the most cohesion and least coupling, each with its
policy class already beside it:

1. Display page glue (`:26860-29750`): `ensureDisplayPageFrame`, `refreshDisplayCards`,
   `DisplayTouchpadWait`, `returnKeysToTerminal`, `routeKeyNobodyHolds`, `showDisplayWindow`,
   `resolveLinuxAppForLaunch`; policies `DisplayViewFocus`, `DisplayPagePolicy`,
   `DisplayStopSequence` exist.
2. Shell activity and window label (`:30890-31190`): `scheduleShellPhaseJudgement`,
   `scheduleShellActivityDecay`, `scheduleWindowBarRefresh`, `scheduleWindowLabelPoll`; policy
   `WindowForegroundResolver` exists.
3. Package refresh and warm-up (`:34880-35000`).
4. Style reload statics (`:34440-34490`, §4).

## Order of work

1. `Hold` with backstop telemetry (§1), then a week on pong with the motion log on. Everything in
   §2 is cheaper to decide once the log says which backstops fire.
2. Deletions (§3), the handler rule and `PendingReload` (§4). No behaviour change intended; each
   is one commit with its test.
3. Keyboard show on a signal; blur heartbeat's writer found and the heartbeat removed; keys handed
   back inside the stop sequence (§2).
4. `sTerminalPageId` to a parameter; `LiveSceneFrame` to an instance (§5).
5. `Generation` helper and the §6 list, each with a reproduction first.
6. Activity extractions (§9), one per wave, Display glue first.
