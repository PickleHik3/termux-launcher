# Usage latency — touch to intent, and whether an architectural change is worth it

Research record, 2026-09-08. Written after four fixes landed on `dev` and left a clear picture of
where the remaining time goes. Nothing here is implemented. It exists so the next pass starts from
the mechanism rather than from a profiler run.

The question: how much of the delay between a finger landing and the app doing the thing is work
this codebase chooses to do synchronously, and is there a shape that makes that cheap without
giving up a feature.

The short answer is that almost all of it is chosen. The app's expensive paths are correct and
individually cheap; what costs is that they run many times per gesture, in the gesture's own frame,
because the chrome has one entry point that says *apply now* and nine call sites that use it. One
per-frame commit and three caches that stop being thrown away are worth more than any renderer
swap.

## Measured today

On the developer's phone (**pong**, Nothing A065, 90 Hz, Android 16), 2026-09-07 and 2026-09-08.
Treat as fact; the method is the gfxinfo/perfetto loop recorded in agent memory.

Already landed on `dev` and reflected in these numbers:

| Commit | What it stopped doing |
|---|---|
| `f2b957e5` | clearing the blur cache on every wall-page change; the cache now holds four radii |
| `a1b62654` | broadcasting a styling reload for every new shell instead of the first |
| `62ab4c04` | delivering a widget's size once per layout pass instead of once per settled size |
| `47eed5e9` | rebuilding the keyboard palette when nothing it reads had moved |

Result: page-change worst frames **800–890 ms → 150–200 ms**; a pane split **287+257 ms →
~170+60 ms**.

What is still open, with the numbers:

| | Hotspot | Cost |
|---|---|---|
| (a) | `ChromeRenderer.requestSync(SCOPE_APPLY_NOW)` — builds and applies a spec synchronously | fired **27–38 times per two page changes**, **1.2–1.3 s** in total |
| | `applyAccessoryGeometryIfNeeded` | 16–22 times per two page changes |
| | `TermuxActivity.applyPlaceLook` | **150–275 ms inside the tap** — keyboard `onPreferencesReloaded` ~155 ms, `refreshPaneLayout` 45–75 ms |
| (b) | `TerminalRenderer.render` on the main thread | ~50 ms per render, **max 381 ms**; a live TUI redraws ~8×/s. Hot: `symbolMapFor`, `resolveRunColors`, `dimColor`, `variationKey`/`variationTypeface`, `Typeface.nativeCreateFromTypefaceWithVariation` |
| (c) | Return from Settings | main thread blocked ~225 ms, of which `onStart` 107 ms |
| (d) | Widget host views re-applying RemoteViews | ~200 ms each; the grid is sized from the current place's chrome |
| (e) | Wallpaper blur | `WallpaperManager.getDrawable()` decode ~46 ms **plus** a RenderScript blur per miss, all on the main thread |
| (f) | Idle home screen | 33–66 ms full-screen frames |

One number in this table contradicts an existing record and the contradiction is load-bearing.
`plans/backlog.md` closes the "Explicit OpenGL ES / Vulkan renderer" item with a measured gate:
"Canvas draw is ~2.4 ms typical and 5.0 ms worst against an 8.333 ms budget with zero slow draws."
The 50 ms figure above is the *same* draw path. The difference is configuration, and the code says
which: `terminal-view/src/main/java/com/termux/view/TerminalRenderer.java:311-324` records that
variable-font axes alone moved median frame time from 12 ms to 73 ms before the instance cache went
in. So the 50 ms is a **cold-cache, symbol-map-plus-variable-axes** render, not the cost of Canvas.
That points the fix at the caches, not at the renderer — see T6/T7 and A3 below, and read the
backlog's recommendation to close the GL item as still correct.

## How a tap becomes a frame here

Worth stating once, because every candidate below is a change to one link in it.

**The wall.** A tile tap or a status-bar drag release reaches
`PaneWallLayout.goTo` (`app/src/main/java/com/termux/app/wall/PaneWallLayout.java:179-196`), which
moves the offset, sets the current page, and calls `notifyPageChanged()` (`:299-301`) **before**
`startSlide()`. So the listener runs inside the tap's own frame. `settleImmediately()` (`:292-297`)
fires `onWallPageSettled` at the end of the slide.

`onWallPageChanged` (`app/src/main/java/com/termux/app/TermuxActivity.java:12384-12400`) then does
seven things in a row, one of which is `syncPlaceLayout()` (`:8794-8816`), which may call
`applyPlaceLook()` (`:8828-8839`), which touches eight subsystems and ends in a four-scope
`requestSync`. Everything after the tap and before the first slide frame is in this call stack.

**The chrome.** `ChromeRenderer.requestSync(int)` is the single "something changed" entry
(`app/src/main/java/com/termux/app/chrome/ChromeRenderer.java:193-223`). Two of its bits behave
completely differently:

- `SCOPE_ACCESSORY_RENDER` is **coalesced** — one `Handler.post` per main-loop turn, guarded by
  `mRenderSyncPending` (`:215-218`). This is the design the class documents.
- `SCOPE_APPLY_NOW` is **not** — it calls `applyChromeSpec(buildChromeSpec())` inline (`:209-211`).

And `applyChromeSpec` is not a leaf. Its tail calls `requestSync(SCOPE_TOP_PANE_FROST)`
(`TermuxActivity.java:4926` and `:4998`), which runs `WallpaperFrostPainter.updateTopPane()`
(`app/src/main/java/com/termux/app/chrome/WallpaperFrostPainter.java:52-92`), whose first line is
`mSurfaces.updateTerminalGlassFrost()` (`:55`) → `TermuxActivity.updateTerminalGlassFrost`
(`:1968-1973`) → `TerminalPaneController.setSurfaceStyle`, which unconditionally re-glazes every
live pane (`app/src/main/java/com/termux/app/terminal/TerminalPaneController.java:2635-2638`), and
`PaneWallController.applyStyle`, which unconditionally re-styles the Widgets and Display pages
(`app/src/main/java/com/termux/app/wall/PaneWallController.java:118-122`).

So **one** `SCOPE_APPLY_NOW` is: build a spec, walk the decor tree for ~14 view ids
(`TermuxActivity.java:4858-4867`), rebuild the dock glass drawable (`:4976-4977`), recompute the
backdrop crop rect and possibly re-cut a bitmap (`:4724-4813`), re-cut two frost crops, re-glaze
every pane, and re-style two wall pages. Times 27–38.

**Nothing in the spec stops a repeat.** `ChromeSpec`
(`app/src/main/java/com/termux/app/chrome/ChromeSpec.java:12-37`) has nine public final fields and
**no `equals`/`hashCode`**, and nothing records the last applied spec. There is therefore no way for
`requestSync` to know that the 30th apply of a gesture is byte-identical to the 29th.

## Constraints any candidate has to respect

- **`targetSdkVersion` stays 28** (`gradle.properties:27`), for the SELinux exec-domain reason
  AGENTS.md:253 spells out. `minSdkVersion` is 26. Compile SDK is 36, so API-31+ classes are
  reachable behind a `Build.VERSION.SDK_INT` gate — which the app already does for `RenderEffect`
  (`TermuxActivity.java:4785`, `:3519`).
- targetSdk 28 also means `Paint.setFontVariationSettings` takes its legacy behaviour: "If the
  application that targets API 35 or before, this function mutates the underlying typeface
  instance" ([Paint.java][paint]). That is exactly the cost the renderer's instance cache exists to
  avoid, and it is not going away by moving targetSdk.
- **The wallpaper read works because this app targets API 28,** and separately because the
  manifest declares `MANAGE_EXTERNAL_STORAGE` (`app/src/main/AndroidManifest.xml:45`). `WallpaperManager.getDrawable()`'s own javadoc:
  "Up to Android 12, this method requires the `READ_EXTERNAL_STORAGE` permission. Starting in
  Android 13, directly accessing the wallpaper is not possible anymore, instead the default system
  wallpaper is returned... **From Android 14, this method should not be used and will always throw a
  `SecurityException`.** Apps with `MANAGE_EXTERNAL_STORAGE` can still access the real wallpaper on
  all versions." ([WallpaperManager.java][wallpaper], ~L1007-1034; the current signature is
  `@RequiresPermission(anyOf = {MANAGE_EXTERNAL_STORAGE, READ_WALLPAPER_INTERNAL})`.) The exemption
  is one of two reasons pong shows a real ~46 ms decode rather than a throw.
  *Checked 2026-09-08, and the javadoc is written for apps that target API 33 or later.* The
  service's actual check, in `getWallpaperWithFeature`, is: `READ_WALLPAPER_INTERNAL`, else
  `MANAGE_EXTERNAL_STORAGE` (permission or app-op), else
  `StorageManager.checkPermissionReadImages(...)` ([WallpaperManagerService.java][wms]), and that
  last call is MediaProvider's `checkPermissionReadImages`, which reads
  `String permission = targetSdkIsAtLeastT && SdkLevel.isAtLeastT() ? READ_MEDIA_IMAGES :
  READ_EXTERNAL_STORAGE;` ([MediaProvider PermissionUtils.java][mppu]). For a target-28 app the
  operative permission is therefore `READ_EXTERNAL_STORAGE`, exactly what the reactive prompt
  (`TermuxActivity.java:4576-4599`) and the first-run chain ask for. **The earlier draft of this
  study called that prompt dead code; it is not.** Pong holds both: `READ_EXTERNAL_STORAGE`
  granted and the `MANAGE_EXTERNAL_STORAGE` app-op allowed (`dumpsys package` / `appops get`,
  2026-09-08). The prompt would only become wrong if `targetSdkVersion` moved to 33 or later,
  which AGENTS.md rules out.
  *Unverified:* Google's `/about/versions/{13,14,15}/behavior-changes-all` pages do **not** mention
  `WallpaperManager`, `getDrawable` or `MANAGE_EXTERNAL_STORAGE` (grepped). The behaviour above
  comes from AOSP source, not from a behaviour-changes page.
- The frame budget on pong is **11 ms** at 90 Hz: "your app must render frames in under 16ms to
  achieve 60 frames per second... If you overrun this window by 1ms... Choreographer drops the
  frame entirely" ([Android vitals: rendering][render]). The ANR threshold is far above anything
  measured here — "not responded to an input event (such as a key press or screen touch) within 5
  seconds" ([ANRs][anr]) — so nothing in this document is a crash risk; it is all perceived
  responsiveness. And none of it is a *launch* problem either: warm start "encompasses a subset of
  the operations that take place during a cold start" and hot start "has lower overhead"
  ([App startup time][launch]), which is the (c) case — the process and the UI are alive.
- **Reading a preference does not block on a pending `apply()`.** Worth knowing before anyone
  "optimises" `apply()` to `commit()`: every getter in `SharedPreferencesImpl` waits on
  `awaitLoadedLocked()`, which blocks only until the *initial* load of the file completes, and
  `apply()` updates the in-memory map synchronously before queuing its disk write
  ([SharedPreferencesImpl.java][spi]). The preference reads scattered through the hot paths above
  are memory reads.

## Candidates

Sizes follow `backlog.md`: **S** 2–5 days, **M** 1–3 weeks, **L** 4–8 weeks, **XL** 2–6 months.

### Tactical

**T1 — Give `ChromeSpec` an identity and skip an apply that would change nothing.** `equals`/
`hashCode` on the nine fields, a `mLastAppliedSpec` in `ChromeRenderer`, and an apply that returns
early when the spec matches *and* the ledger reports no dirty backdrop. Addresses (a) directly: the
27–38 applies per two page changes are not 27–38 distinct states, they are a handful of states
requested by nine call sites that each behave as if they were the only one.
*Gain:* unquantified as a fraction, but bounded above by the 1.2–1.3 s the applies cost, and the
ledger already knows the crop-invalidation half of the answer (`ChromeRenderer.java:200-208`).
*Risk:* a caller that today relies on `applyChromeSpec` for its *side effects* rather than its spec
— the frost re-cut, the pane re-glaze — would stop getting them. That is a real risk and the reason
T3 must land with T1, not after it.
*Guard:* `ChromeRendererTest.applyNowRunsBeforeTheCallReturns` and
`manyRenderRequestsInOneMainLoopTurnCostOneApply` (`app/src/test/java/com/termux/app/chrome/ChromeRendererTest.java:44-71`)
pin the current contract and will need a companion case for "an identical second request costs
nothing"; `SurfaceDirtyLedgerTest`, `ChromePolicyTest`, `GlassSurfaceFactoryTest`,
`TermuxActivityInAppKeyboardGeometryTest` (which calls `SCOPE_APPLY_NOW` at `:317`) cover the
downstream. *Effort:* **S**.

**T2 — Stop `applyChromeSpec` re-glazing panes and re-styling wall pages on every pass.**
`TerminalPaneController.setSurfaceStyle` and `PaneWallController.applyStyle` re-apply
unconditionally; both take a `PaneSurfaceStyle` that changes only when a look changes. Guard both
on the style being different from the one applied. Addresses (a) and (f) — the idle full-screen
frames are the 1 Hz stats tick (`app/src/main/java/com/termux/app/statusbar/SystemStatsController.java:103`)
reaching the status widgets that sit inside the frosted chrome, and every chrome pass it provokes
currently re-glazes the whole pane tree.
*Gain:* unquantified. *Risk:* a style change that arrives without an object-identity change would
be dropped — needs value equality on `PaneSurfaceStyle`, not reference equality.
*Guard:* `WidgetPaneGlassIntegrationTest`, `PaneWallControllerTest`. *Effort:* **S**.

**T3 — Split the frost re-cut out of `applyChromeSpec`.** The frost crop depends on the status
bar's rect and radius, not on the accessory spec; it is re-cut on every apply only because it was
convenient to hang it there. Make `SCOPE_TOP_PANE_FROST` a scope callers request when the top pane
actually moved, and let it ride the coalesced pass. *Gain:* unquantified; it removes one whole
`getLocationOnScreen`-plus-crop pair (`WallpaperFrostPainter.java:114-130`) from each of the 27–38
applies. *Risk:* a moved status bar with no other trigger would keep a stale crop — the ledger's
frost-rect matching (`:130`) already detects that, so the guard exists.
*Guard:* `SurfaceDirtyLedgerTest`, `ChromeRendererTest`. *Effort:* **S**.

**T4 — Take the tap-correction store off the tap.** `TermuxInAppKeyboard.onPreferencesReloaded`
calls `mTapCorrection.reload()` (`app/src/main/java/com/termux/app/terminal/inappkeyboard/TermuxInAppKeyboard.java:313`),
which is `flush()` plus `TapModelStore.load(mFile)`
(`app/src/main/java/com/termux/app/terminal/inappkeyboard/TapCorrectionController.java:80-83`), and
that is a synchronous `FileInputStream` read plus a JSON parse
(`app/src/main/java/com/termux/app/terminal/inappkeyboard/TapModelStore.java:54-67`). Every wall
page change with a per-place look does main-thread disk I/O. The controller already owns an
`mIoExecutor` (used at `:92`); reload should skip when the file's size and mtime are unchanged, and
otherwise load on that executor. Addresses the remaining part of the ~155 ms in (a).
*Gain:* unquantified per-read, but this is a main-thread file read, and `StrictMode`'s
`detectDiskReads()`/`detectDiskWrites()` (API 9) plus `penaltyLog()` exist to find exactly this
class of call ([StrictMode.ThreadPolicy.Builder][strictmode]). A debug-only `ThreadPolicy` with
`detectDiskReads`, `detectDiskWrites`, `detectCustomSlowCalls` (API 11) and `detectUnbufferedIo`
(API 26) is worth adding alongside T10 — it turns "find the main-thread I/O" from a reading exercise
into a logcat grep, and would have found this one.
*Risk:* a settings change that forgets learned taps must still take effect before the next key
press. *Guard:* `TapCorrectionControllerTest`, `TapModelStoreTest`, `TapModelTest`. *Effort:* **S**.

**T5 — Guard `refreshPaneLayout` the way the palette is guarded.** `refreshPaneLayout()` is
`render()` on the whole active window (`TerminalPaneController.java:2645-2648`) — 45–75 ms — and
`applyPlaceLook` calls it unconditionally (`TermuxActivity.java:8835`). The pane layout depends on
the gap, the glass radius and the tree; a place look that changes none of them does not need a
render. This is precisely the pattern `47eed5e9` used for the keyboard palette
(`TermuxInAppKeyboard.java:1033-1038`) and the comment there already names the page-change case.
*Gain:* up to the 45–75 ms, on the page changes where the pane inputs did not move. *Risk:* a
signature that misses an input leaves a pane laid out for the old gap. *Guard:*
`app/src/test/java/com/termux/app/terminal/` pane-layout tests plus the `PaneWallControllerTest`
suite. *Effort:* **S**.

**T6 — Stop throwing away the terminal renderer's caches.** `TerminalView.setTextSize`
(`terminal-view/src/main/java/com/termux/view/TerminalView.java:854-863`) and `setTypeface`
(`:956-970`) both **construct a new `TerminalRenderer`**, and a new renderer starts with an empty
`mVariationTypefaces` map (`TerminalRenderer.java:324`) and a fresh `FallbackFontResolver` memo
(`:291`). That is why `Typeface.nativeCreateFromTypefaceWithVariation` and `Paint.hasGlyph` show up
in (b) at all: the map is never cleared in normal operation, so a hot profile can only mean the
renderer itself was replaced. `applyFontToView` runs per new pane view
(`app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java:855-868`), so a
pane split hands the new pane a cold renderer. Two fixes: carry the instance map and the fallback
memo across a rebuild when the face set is unchanged, and make `setTextSize` mutate rather than
replace.
The cost being avoided is real: `Typeface_createFromTypefaceWithVariation` builds a fresh
`minikin::VariationSettings` and calls the native Typeface factory per invocation
([Typeface.cpp][typefacecpp]) — it is not a cache hit. The framework's own `LruCache` of typefaces
is `sDynamicTypefaceCache`, documented as a cache "for Typeface objects dynamically loaded from
assets" ([Typeface.java][typefacejava]), and nothing shows the variation path consulting it.
*Gain:* against the renderer's own recorded 73 ms → 12 ms for the same axes, this is the largest
single lever in (b). *Risk:* carrying a cache across a face change would draw the old font.
*Guard:* `FallbackFontResolverTest`, `TerminalRendererPolicyTest`, `TerminalRenderMetricsTest`
(`terminal-view/src/test/java/com/termux/view/`), plus `GraphemeBufferInstrumentationTest` which
constructs renderers directly. *Effort:* **S**.

**T7 — Make the per-run and per-cell lookups allocation-free.** Three named hot spots in (b) are
each a small function called per cell or per run:
- `variationKey` builds a `String` per call by concatenating an identity hash
  (`TerminalRenderer.java:1798-1804`), and `configureFont` calls it for every run (`:1894`). A
  packed `long` key or a two-level `IdentityHashMap` removes the allocation.
- `symbolMapFor` is a reverse linear scan of every configured map, **per cell**
  (`TerminalRenderer.java:1913-1921`). A sorted range index or a small direct-mapped cache on the
  last matched range turns it into a comparison.
- `resolveRunColors` (`:1324-1345`) and `dimColor` (`:1351-1356`) are pure functions of
  `(textStyle, palette, boldWithBright, reverseVideo)`, called from the run walk, the background
  pass (`:1408`) and the synthesized-glyph path (`:1720`, `:1730`). They are memoizable on a
  palette generation counter.

*Gain:* unquantified individually; they are hot because the render loop is two full passes over
every visible cell (`TerminalRenderer.java:619-632` and `:634` onward) with no dirty-row tracking.
*Risk:* low — all three are pure. *Guard:* `TerminalRendererPolicyTest` (which already covers
`variationKey` via the package-private static at `:1802`), `BoxGeometryTest`,
`FallbackFontResolverTest`. *Effort:* **S**.

**T8 — Hand the widget host an executor.** Nothing in the widget package calls
`AppWidgetHostView.setExecutor`; `createView` goes straight through
(`app/src/main/java/com/termux/app/launcher/widget/LauncherWidgetHostController.java:595-597`), so
every RemoteViews apply inflates on the main thread — the ~200 ms per widget in (d). The framework
already has the other half: "Sets an executor which can be used for asynchronously inflating. CPU
intensive tasks like view inflation or loading images will be performed on the executor. The updates
will still be applied on the UI thread," and with one set the host routes through
`reapplyAsync`/`applyAsync` instead of the inline `reapply`/`apply`
([AppWidgetHostView.java][ahv], `setExecutor` at ~L500, the async branch at ~L582 and ~L660).
`setExecutor` is documented as added in API 26 ([AppWidgetHostView reference][ahvref]) — this app's
`minSdkVersion`, so no gate is needed. Note that `RemoteViews.applyAsync`/`reapplyAsync` themselves
are `@hide` ([RemoteViews.java][rv]): the executor is the *only* public way in, which is why this
is one call and not a rewrite.
*Gain:* moves the inflation off the main thread; the wall clock is unchanged but it stops being
inside the tap. *Risk:* an async apply lands a frame or more later, so a widget can appear blank
briefly, and the crash-isolation wrapper `SafeLauncherAppWidgetHostView` must keep catching on the
new thread. *Guard:* `LauncherAppWidgetRemoteViewsInflationTest`,
`SafeLauncherAppWidgetHostViewTest`, `WidgetGridHostViewIntegrationTest`,
`LauncherWidgetProviderRefreshIntegrationTest`. *Effort:* **S**.
**Landed, 2026-09-09.** `LauncherAppWidgetHost.onCreateView` hands every host view one shared
low-priority `widget-inflate` executor. `SafeLauncherAppWidgetHostView` detects failure through
`getErrorView()` (the framework's only error hook, on both paths) and recovery through
`prepareView()`; the error tile is still applied inline so the measure/layout guards keep working,
and after a framework error view the tile is re-applied as RemoteViews before the next update so the
pre-API-33 layout-id recycling does not reapply a provider update onto the tile. Measured on pong,
four Terminal↔Widgets page changes: `inflate` slices on main went from 64 (214 ms) to 0, all 64 now
on `widget-inflate` (224 ms); main doFrame median 3.7 → 2.0 ms. The worst frames (38–42 ms) are
unchanged because they are the page-arrival traversal: two `Chrome.commit` runs (13–17 ms together)
plus layout/measure (18–20 ms) in one frame — that is the next lever, not widgets.

**T9 — Stop the widget grid being re-sized by the terminal's insets.** `PaneWallLayout` measures
and lays out **every** page inside the *terminal page's* margins
(`PaneWallLayout.java:330-372`) — by design, so the wall reads as one frame. But those margins are
the terminal place's frame insets, so arriving on Widgets with a per-place look changes the grid's
bounds, re-measures it (`WidgetGridView.java:181-194`), and schedules a size delivery, which makes
each provider re-render. `62ab4c04`'s 160 ms debounce (`WidgetGridView.java:226`) already stops the
per-frame case; what remains is the one delivery per page arrival. Either give the Widgets page its
own insets, or record the last delivered size per orientation *and* place so an arrival that
returns to a size already delivered delivers nothing.
The provider round trip is not incidental: `updateAppWidgetOptions(Bundle)` is documented as
"Specify some extra information for the widget provider. **Causes a callback to the
AppWidgetProvider**" ([AppWidgetHostView.java][ahv]), and `onAppWidgetOptionsChanged` fires "when
this widget has been layed out at a new size or its options changed via
`AppWidgetManager#updateAppWidgetOptions`" ([AppWidgetProvider.java][awp]). Every delivery wakes
another app's process. Note also the framework's own cap while designing the fix: "There is a limit
of `MAX_INIT_VIEW_COUNT` (16) on the number of different RemoteViews that an AppWidgetProvider can
provide," with the guidance being "two sizes for phones—portrait and landscape"
([App widget layouts][awl]) — so a provider is *expected* to hear a small, fixed set of sizes, not
one per place.
*Gain:* removes one ~200 ms provider round trip per widget per page arrival. *Risk:* a provider
that never hears a genuinely new size lays out cut off — the exact failure
`WidgetGridView.java:234-241` documents. *Guard:* `WidgetSizeOptionsPolicyTest`,
`LauncherWidgetOptionsIntegrationTest`, `WidgetGridMetricsTest`, `WidgetPaneGeometryIsolationTest`.
*Effort:* **M**.

**T10 — Add trace sections and a repeatable harness.** The app has **no**
`android.os.Trace` calls at all (verified by grep across `app/src/main` and
`terminal-view/src/main`) and no Macrobenchmark module. It does already own the frame side:
`TerminalFrameMetricsMonitor` (`app/src/main/java/com/termux/app/terminal/TerminalFrameMetricsMonitor.java:48-66`)
registers a `Window.OnFrameMetricsAvailableListener` on its own `HandlerThread` and is surfaced
through a registry action (`app/src/main/java/com/termux/app/terminal/TerminalActionDispatcher.java:1173`),
and `TerminalRenderMetrics` records per-draw times from `onDraw`
(`terminal-view/src/main/java/com/termux/view/TerminalView.java:2187`, `:2220`). What is missing is
attribution: which *phase* of a tap the time went to.

Where to put the sections — one `beginSection`/`endSection` pair each, names under the 127-code-unit
cap ([Trace.java][trace]):
`ChromeRenderer.requestSync` (with the scope bits in the name), `TermuxActivity.applyChromeSpec`,
`applyPlaceLook`, `applyAccessoryGeometryIfNeeded`, `syncPlaceLayout`,
`WallpaperBlurCache.obtain` (miss only), `WallpaperBlurRenderer.preBlur`,
`TerminalRenderer.render`, `TermuxInAppKeyboard.onPreferencesReloaded`,
`TerminalPaneController.render`, and `LauncherWidgetHostController.createHostView`. Then
`TraceSectionMetric` reports "the number of times a specific trace section occurs and the absolute
amount of time it takes to execute" and `FrameTimingMetric` gives `frameOverrunMs` per frame on API
31+ ([Macrobenchmark metrics][macro]) — which together answer "how many applies did that tap cost"
without a hand-read profile.

For the on-device loop, Perfetto's `android.frame_timeline` source gives an expected and an actual
timeline slice per frame, with the expected slice starting at "the time the Choreographer callback
was scheduled to run" and a per-slice jank classification ([frametimeline][ft]). *Note:* there is
**no** general-purpose Perfetto input-latency data source for an arbitrary app — the
`chrome.android_input` stdlib tables are Chrome-browser-process specific ([stdlib][stdlib]) — so
touch-to-present has to be derived from frame-timeline timestamps plus a trace section opened in
`dispatchTouchEvent`. Adding that one section makes the whole thing measurable.
*Risk:* none to features; `Trace` calls are no-ops when tracing is off. *Effort:* **S**, and it is
the prerequisite for claiming any of the gains above.

**T11 — Get the package-manager query out of `onStart`.** (c) is ~225 ms of blocked main thread on
the way back from Settings, 107 ms of it `onStart`. The largest identifiable piece is
`refreshSuggestionBarIfLauncherCatalogChanged()` (`TermuxActivity.java:1256`), whose only job is to
compare a signature — and it computes that signature with a synchronous
`PackageManager.queryIntentActivities` over every launchable activity on the device, then builds and
sorts a `String` per result (`:16388-16408`). On a phone with a normal app count that is the whole
budget for the frame, and it runs on *every* return to the launcher, to answer a question that is
almost always "nothing changed". Two options, and they compose: the app already registers a
`LauncherApps.Callback` and a package-change receiver in the same method (`:1252-1253`), so package
churn is *already* observed — the signature sweep is a belt-and-braces check for events missed while
stopped, and it can move to the existing catalog-warmup post
(`scheduleLauncherCatalogWarmup`, `:1205`, `:16410-16413`) or onto a worker. `MessageQueue`'s idle
handler is the obvious alternative but its javadoc could not be verified (see *What could not be
verified*), so prefer the existing delayed post.
Also in `onStart` and worth timing before touching: `applyTerminalSurfaceAppearance()` (`:1235`),
`updateStatusWidgets()` (`:1240`, ~14 `findViewById` calls and a full re-wire, `:13980` onward) and
`flushPendingAccessoryGeometry()` (`:1231`, which is another `applyAccessoryGeometryIfNeeded(true)`
and therefore another synchronous chrome apply — A1 removes this one for free).
One part of (c) is **not** the app's to fix, and should be excluded before anyone chases it: the
framework "ensures in-flight disk writes from `apply()` complete before switching states (such as
when Activities or Services start or stop)" ([SharedPreferences.Editor][sp]), which
`QueuedWork.waitToFinish()` implements — "Trigger queued work to be processed immediately... Is
called from the Activity base class's onPause(), after BroadcastReceiver's onReceive, after Service
command handling, etc. (so async work is never lost)" ([QueuedWork.java][qw]). For a modern
`targetSdk` that wait sits in the **stop** path, not `onPause` (`ActivityThread` gates the pause-side
call on `r.isPreHoneycomb()` and the stop-side call on `!r.isPreHoneycomb()`
([ActivityThread.java][at])). A Settings screen that wrote a batch of preferences therefore charges
part of its flush to the transition. Measuring it is the point of T10; blaming `onStart` for all
225 ms without that split would send the fix to the wrong place.
*Gain:* unquantified until T10 lands, but this is a `PackageManager` IPC plus a sort on the arrival
frame. *Risk:* a package installed while the launcher was stopped, whose broadcast was also missed,
would show stale for one warmup delay. *Guard:* the suggestion-bar and catalog tests under
`app/src/test/java/com/termux/app/launcher/`. *Effort:* **S**.

**T12 — Decode the wallpaper at the size the blur actually uses, off the main thread.** The blur
already downsamples before blurring and upsamples after
(`app/src/main/java/com/termux/app/chrome/WallpaperBlurRenderer.java:40-52`), so the full-resolution
decode that precedes it is thrown away. `createWallpaperBackdropBitmapForRect` calls
`getDrawable()` (`TermuxActivity.java:4623`), draws it into a target-sized bitmap
(`:4650-4690`) and then calls `forgetLoadedWallpaper()` because "getDrawable() leaves the framework
holding the decoded wallpaper in `WallpaperManager$Globals` for the life of the process — 16.6 MB of
this one" (`:4636-4641`). Two things follow:

- `getWallpaperFile(int which)` returns a `ParcelFileDescriptor` under the same permission gate
  ([WallpaperManager.java][wallpaper]), which can be decoded with
  `BitmapFactory.Options.inSampleSize` — "If set to a value > 1, requests the decoder to subsample
  the original image, returning a smaller image to save memory"
  ([BitmapFactory.Options][bfo]) — or with `BitmapRegionDecoder` for the crop
  ([BitmapRegionDecoder][brd]). At the blur's own downsample factor that is a fraction of the
  pixels and no framework-side 16.6 MB cache to forget.
- It can happen on a worker. `ImageDecoder.Source` construction is annotated `@AnyThread` and
  documented as usable "simultaneously in multiple threads," while `decodeDrawable`/`decodeBitmap`
  are annotated `@WorkerThread` ([ImageDecoder.java][id]) — i.e. the platform labels background
  decoding as the intended use. *Unverified:* there is **no**
  `ImageDecoder.createSource(ParcelFileDescriptor)` overload in current AOSP (checked), so the PFD
  path is `BitmapFactory`/`BitmapRegionDecoder`, not `ImageDecoder`.

The ledger already models "this crop is not ready" (`ChromeRenderer.java:200-208`,
`TermuxActivity.java:4771-4782`), so an asynchronous frame arriving a beat later is a state the
chrome already handles.
*Gain:* removes the ~46 ms decode from (e) from the main thread and shrinks it. *Risk:* the frost
must not flash flat on first paint — the recovery pass (`ChromeRenderer.java:162-174`) exists for
exactly that and would need to cover the async case. *Guard:* `WallpaperBlurCacheTest`,
`ManagedWallpaperSourceTest`, `FakeWallpaperBlurSource`. *Effort:* **M**, and it is A2's stepping
stone — if A2 lands, the decode is still needed, just not the CPU blur.

### Architectural

**A1 — One chrome commit per frame, driven from the pre-draw pass.** Retire `SCOPE_APPLY_NOW` as an
inline call. `requestSync` records *intent* (which it already does for four of its eight bits) and a
`ViewTreeObserver.OnPreDrawListener` on the content root builds one spec and applies it once, after
measure and layout and before draw. The interface documents exactly that position: "Callback method
to be invoked when the view tree is about to be drawn. At this point, all views in the tree have
been measured and given a frame," returning true to proceed ([ViewTreeObserver.java][vto]). It is
also the phase order the platform guarantees — `CALLBACK_INPUT` → `CALLBACK_ANIMATION` →
`CALLBACK_INSETS_ANIMATION` → `CALLBACK_TRAVERSAL` ("Handles layout and draw. Runs after all other
asynchronous messages have been handled") → `CALLBACK_COMMIT` ([Choreographer.java][choreo]) — so a
pre-draw commit is guaranteed to see the layout the same frame's input caused, which a
`Handler.post` is not.

This is the change the module was designed for and stopped one bit short of. The doc comment on
`requestSync` already says "the accessory render is coalesced to one pass per main-loop turn no
matter how many callers ask for it" (`ChromeRenderer.java:190-191`); `SCOPE_APPLY_NOW` is the
exception that makes the claim false for all nine of its call sites.

Why each of the nine callers passes `APPLY_NOW`, and whether it needs to:

| Call site | Why it is there | Needs synchronous application? |
|---|---|---|
| `TermuxActivity.java:999` — inside `setOnApplyWindowInsetsListener` | insets just changed; the accessory bounds are read from them | **No.** The listener returns the insets and a traversal follows; a pre-draw commit runs inside that same traversal. |
| `:5690` — `runOrientationGeometryPass` | rotation; the render state that hides landscape rows is derived | **No.** The pass already asks for a fresh inset dispatch (`:5681`), so a layout is coming. |
| `:6520` — `applyAccessoryGeometryIfNeeded` | after `updateAppLauncherBarHeight` + `setTerminalToolbarHeight(true)` | **No.** Both of those write layout params; the apply wants the *result*, which is what pre-draw is. This is also the caller that fires 16–22 times per two page changes and already self-throttles at 120 ms (`:6513`). |
| `:7772` — `applyGeometryPreview` (surface-editor drag) | the dock must track the finger | **No, and this is the one that most wants the change** — one commit per frame is exactly a drag's requirement. |
| `:7783` — `applyGlassPreview` (slider drag) | same, and it *already* pairs `APPLY_NOW` with `ACCESSORY_RENDER` | **No.** Passing both is a statement that the author wanted one pass and did not trust the coalesced one to be soon enough. |
| `:8416` — `setTerminalToolbarView` during `onCreate` | first layout of the toolbar | **No.** Nothing is on screen yet. |
| `:8813` — `syncPlaceLayout`, when the arrangement or a column changed | rows collapse and come back | **No.** |
| `:8838` — `applyPlaceLook` | the place's whole look | **No**, and again it is already paired with `ACCESSORY_RENDER`. |
| `:9332` — `toggleTerminalToolbar` | user toggled the dock | **No**, and it is followed on the next line by an `ACCESSORY_RENDER` and then a `setTerminalToolbarHeight` (`:9333-9337`) — i.e. this site is already three requests for one intent. |

The honest answer is that **no caller needs it**. What they need is "applied before the user sees
the next frame", which is what a pre-draw commit means and what an inline call only approximates.
*Gain:* bounded by the 1.2–1.3 s in (a); with T1 folded in, two page changes should cost a small
number of applies rather than 27–38. *Risk:* the real one. Any of these paths that reads a chrome
value back *after* the `requestSync` call, in the same method, would now read the old value. That
has to be found call site by call site, not assumed away. A second, subtler risk: the in-app
keyboard's flash-free reveal already runs two of its own pre-draw gates
(`app/src/main/java/com/termux/app/terminal/inappkeyboard/KeyboardGeometryChoreographer.java:131-134`)
and its `Surface` calls `applyChromeSpec` directly (`TermuxActivity.java:5029-5031`); the ordering
between the reveal gate and the chrome commit is a genuine design question, not a detail.
*Guard:* `ChromeRendererTest` (its `applyNowRunsBeforeTheCallReturns` case is the contract being
deliberately changed, so it becomes the new contract's test),
`KeyboardGeometryChoreographerTest`, `TermuxActivityInAppKeyboardGeometryTest`, `ChromePolicyTest`,
`PlaceChromePolicyTest`, `ExtraKeysColumnGeometryTest`, `KeyboardOverlayPolicyTest`.
*Effort:* **M**.

**A2 — Blur on the RenderThread instead of RenderScript on the main thread.** Today
`WallpaperBlurRenderer.preBlur` downsamples, runs `AndroidStockBlurImpl` — the RenderScript
`ScriptIntrinsicBlur` path — and upsamples, all inline
(`app/src/main/java/com/termux/app/chrome/WallpaperBlurRenderer.java:21-65`), and the downsample
factor exists only to keep the script radius under RenderScript's 25 px cap (`:31-38`). RenderScript
is deprecated: "Starting with Android 12, the RenderScript APIs are deprecated. They will continue
to function, but we expect that device and component manufacturers will stop providing hardware
acceleration support over time" ([RenderScript compute][rs]).

`RenderEffect.createBlurEffect(radiusX, radiusY, tileMode)` "was added into Android 12, API level
31, allowing you to blur a RenderNode" ([migration guide][migrate]), taking a radius per axis
([RenderEffect.java][re]), and a `View`'s effect is applied on its own RenderNode — i.e. on the
RenderThread, off the UI thread. The app already imports the class and uses it, but only for the
*glass refraction* shader over an already-CPU-blurred bitmap (`TermuxActivity.java:4785-4807`).
Moving the blur itself there would retire: the RenderScript dependency, the per-radius bitmap cache
(48 MB budget, `WallpaperBlurCache.java:86`), the crop-per-surface bitmap allocation
(`:246-266`), and the ~46 ms `getDrawable()` decode from every cache miss on the main thread. It
also removes the "which of four radii is resident" problem that `f2b957e5` had to fix by widening
the cache to four.

Two caveats, both documented: "Different Android devices may or may not support the feature due to
limited processing power" ([Android 12 features][a12]), and there is **no** documented maximum
radius for `createBlurEffect` (checked; not stated on any primary page), so the CPU path stays as
the `SDK_INT < 31` fallback and the radius clamp becomes empirical rather than the 25 px script cap.
*Gain:* removes a main-thread blur entirely; unquantified in ms because the RenderThread cost is
not documented, but it is not on the tap's frame. *Risk:* the highest-visual-risk item here — the
glass material is a tuned composite of blur, frost colour filter and a refraction shader
(`:4798-4812`), and re-ordering blur and refraction changes how it looks. Needs the developer's eye
on a device, not a test.
*Guard:* `WallpaperBlurCacheTest`, `FakeWallpaperBlurSource`, `GlassSurfaceFactoryTest`,
`ManagedWallpaperSourceTest`, `WallpaperModePreferencesTest` — none of which can judge appearance.
*Effort:* **M**.

**A3 — A per-row RenderNode cache for the terminal.** `render` walks every visible row twice per
draw — once for backgrounds and the cursor block, once for glyphs
(`TerminalRenderer.java:619-632`, `:634` onward) — with no notion of which rows changed. A live TUI
redrawing 8×/s therefore reshapes the whole screen 8×/s.

Two shapes, in increasing cost:

1. **Row-level `RenderNode`s.** One `RenderNode` per terminal row, re-recorded only when that row's
   content or style changed, and `canvas.drawRenderNode` for the rest. `drawRenderNode` is "only
   supported in hardware rendering, which can be verified by asserting that isHardwareAccelerated()
   is true" ([Canvas.java][canvas]) — which holds, hardware acceleration is on by default from API
   14 ([hardware acceleration][hwaccel]). And crucially the recording need not happen on the UI
   thread: "RenderNode may be created and used on any thread but they are not thread-safe. Only a
   single thread may interact with a RenderNode at any given time. It is critical that the
   RenderNode is only used on the same thread it is drawn with" ([RenderNode.java][rn]). Read
   strictly, that permits *off-thread recording* only if the same thread also draws it — so a
   worker-thread record plus a UI-thread draw is **not** sanctioned by the doc. Row caching is
   therefore a same-thread win (skip unchanged rows) rather than a threading win. That is still the
   right answer for a TUI, where most rows are unchanged.
2. **`MeasuredText` per row.** `Canvas.drawTextRun(MeasuredText text, int start, int end, ...)`
   exists and takes a `MeasuredText` rather than a `CharSequence` ([Canvas.java][canvas]), and
   `MeasuredText` is documented as the "Result of text shaping of the single paragraph string"
   ([MeasuredText.java][mt]). This is the primitive for reusing shaping across frames, and unlike
   `PrecomputedText` — whose doc ties it to `TextView`/`StaticLayout`, "This PrecomputedText
   instance can be set on android.widget.TextView or StaticLayout" ([PrecomputedText.java][pt]) —
   it is directly consumable from a raw Canvas. *Unverified:* nothing in `MeasuredText`'s javadoc
   states whether a builder may run on a background thread, so treat off-thread shaping as unproven
   until checked on device.

Precedent worth knowing: **ConnectBot** is the only open-source Android terminal of the three
examined that does better than a full redraw. It keeps a persistent `Bitmap` and `Canvas` on the
bridge (`TerminalBridge.java` at tag `v1.9.13`), redraws only dirty regions into it with
`clipRect` + `drawText`, and `TerminalView.onDraw` blits the bitmap. Its background `Relay` thread
does PTY I/O and sets dirty flags, then `postInvalidate()`s — **the glyph drawing is still on the
UI thread.** Termux upstream (`TerminalView.onDraw` → `TerminalRenderer.render`, plain `drawRect`
and `drawTextRun`) and jackpal's Android Terminal Emulator (`EmulatorView.onDraw` → per-line
`drawText`) both do a full main-thread Canvas redraw, exactly as this fork does. So dirty-region
caching is the field-proven idea; off-main-thread rendering is not proven by anyone.
*Gain:* unquantified, and the honest framing is that T6+T7 should be measured *first* — if the
50 ms was cold caches, row caching is optimising a path that is already 2.4 ms. *Risk:* the render
loop is the most intricate code in the repo (kitty placeholders, symbol expansion, synthesized
glyphs, selection, hyperlinks, decoration colours), and a dirty-row model has to be right about
every one of them or a cell goes stale. *Guard:* `TerminalRendererPolicyTest`, `BoxGeometryTest`,
`CursorTrailHullTest`, `TerminalRenderMetricsTest`, `FallbackFontResolverTest`, plus the
`androidTest` grapheme suite. *Effort:* **L**.

**A4 — The per-place look as one snapshot applied as one transaction.** `applyPlaceLook`
(`TermuxActivity.java:8828-8839`) is eight imperative calls into eight subsystems plus a four-scope
`requestSync`, run inside the tap. The per-place layout spec already established the shape for the
*arrangement* half: `PlaceLayoutStore` "resolves one immutable `PlaceLayout` for (place,
orientation)" and the readers read that, "no per-place branches scattered through `TermuxActivity`"
(`project-docs/per-place-layout/SPEC.md`). The look half never got the same treatment — it is still
"tell everyone to re-read the preferences". A `PlaceLookSpec` built once, diffed against the applied
one, and applied only where it differs would make T2 and T5 fall out for free and give A1 a single
thing to commit.
*Gain:* addresses the 150–275 ms of (a) structurally rather than one guard at a time. *Risk:* it is
a refactor across the widest seam in the activity. *Guard:* `PlaceLookPreferencesTest`,
`PlaceLayoutStoreTest`, `PlaceChromePolicyTest`, `PaneWallControllerTest`. *Effort:* **M**, and
only worth starting once A1 has landed and shown which reads are order-sensitive.

**A5 — Move `onWallPageChanged`'s work off the tap's frame.** `goTo` fires the listener before
`startSlide` (`PaneWallLayout.java:187-193`), so all seven of `onWallPageChanged`'s calls sit in the
tap. Everything that must be right on the *first slide frame* (the destination page's look, since
it is already partly on screen) has to stay; everything else — `mPreferences.setWallLastPage`,
widget attachment, window-bar chips — belongs at settle, where `onWallPageSettled` already is
(`TermuxActivity.java:12358-12368`). The right split is decided by asking, per call, whether the
user can see its effect during the slide.
*Gain:* unquantified, and smaller than it looks: the look *is* visible during the slide, so the
expensive half cannot simply be deferred. This candidate is worth doing only as a tidy-up after A1
makes the look cheap. *Risk:* deferring something visible produces a one-frame flash of the wrong
chrome, which is the class of bug the keyboard reveal gates exist to prevent. *Guard:*
`PaneWallLayoutTest`, `PaneWallControllerTest`, `PaneWallPolicyTest`. *Effort:* **S**.

**A6 — Terminal on its own `SurfaceView`. Not worth it.** A `SurfaceView` "provides a dedicated
drawing surface embedded inside of a view hierarchy," "punches a hole in its window," and exists
partly "to provide a surface in which a secondary thread can render into the screen"
([SurfaceView.java][sv]). That sounds like the answer to (b), and it is not, for this app:
- The terminal is not the top layer here. It sits under the frosted chrome, the pane glass, the
  wall's `translationX`, the dim composite and a per-pane rounded clip. A `SurfaceView` is composited
  as its own layer with its own z-order (`setZOrderOnTop`), so every one of those effects would have
  to be re-expressed. `TextureView` avoids that — it "does not create a separate window but behaves
  as a regular View" and "can have translucency, arbitrary rotations, and complex clipping" — but
  its own doc says "TextureView contents must be copied, internally, from the underlying surface
  into the view displaying those contents. For that reason, SurfaceView is recommended as a more
  general solution" ([TextureView.java][tv]). Trading a 50 ms draw for a per-frame full-screen copy
  is not obviously a win.
- The measured 50 ms is very likely cold caches (see the contradiction under *Measured today*), and
  the backlog's own GL gate found 2.4 ms typical draw. Moving surfaces to fix a cache problem is the
  expensive way round.
- The pane wall's whole premise is that pages move by `translationX` and "a page change and a whole
  drag cost no layout work" (`PaneWallLayout.java:34-36`). Punched-hole surfaces do not translate
  for free.

Recommend closing this direction, the way `backlog.md` recommends closing the GL renderer, and for
the same reason: the gate was measured and the condition is not met. `AttachedSurfaceControl`
(`View.getRootSurfaceControl()`, API 31+) is the one piece worth remembering — it exists "to enable
attaching app created SurfaceControl to the SurfaceControl hierarchy used by the app" and
`applyTransactionOnDraw` "consumes a passed transaction and requests the View hierarchy to apply it
atomically with the next draw" ([AttachedSurfaceControl.java][asc]) — but it is the answer to a
question this app is not asking yet.

**A7 — A Baseline Profile.** There is no `androidx.profileinstaller` dependency and no
`baseline-prof.txt` in the tree (checked). Google's claim: "Baseline Profiles improve code execution
speed by about 30% from the first launch by avoiding interpretation and just-in-time (JIT)
compilation steps for included code paths," installed "by `androidx.profileinstaller` on the first
run when the app module defines this dependency" ([Baseline Profiles overview][bp]). Read the
number honestly: it is a **first-launch / startup** claim, and the page frames the whole mechanism
around cold start. Nothing on it claims a warm or hot benefit, so this does not address (a), (b) or
(c) — it addresses the first launch after an install or update. Worth having; not a latency fix for
the measured hotspots. *Effort:* **S**.

**Measured 2026-09-09: the emulator's byte path on the UI thread.** A `Terminal.append` trace
section now wraps `TerminalEmulator.append` in `TerminalSession`'s main-thread handler. On pong,
`seq 1 400000` parsed 725 ms in one second, frames fell from ~90 to 34 that second, and one 64 KB
chunk held the thread for 89 ms; `top -d 0.2` parsed 10–27 ms per second; fastfetch's kitty gif
parsed 230 ms in its first two seconds and nothing afterwards. Parsing competes with drawing only
under a flood, and the largest single stall is ~90 ms. Verdict: do not move the emulator to its own
thread; the render would still need the screen lock and the gain is bounded to the flood case.
A per-turn parse cap was considered and rejected: it would trade stream throughput for input
latency during floods and helps no real workload measured.

**Landed 2026-09-09: an image node per row.** The fastfetch gif redrew 65 times a second with
`Terminal.render` median 5.4 ms, and an ART sample put the busy main thread in text shaping and
drawing (nDrawTextRun 338 ms, nGetTextAdvances 213, font features 110, of 6 s) because a row with a
kitty placeholder was re-recorded whole every frame. `TerminalRowNodes` now holds a third node,
"TerminalRowImages"; `RowRenderCache.rowChanged` answers only for text and `rowCarriesAnImage`
separately; `TerminalRenderer.drawRowImages` re-records image cells alone, and the glyph node keeps
its recording — kitty's cell buffer / graphics layer split at row granularity. After: render median
1.77 ms (from 5.37), total render 1673 ms of 24 s (from 4485), text methods gone from the sample,
main thread busy ~17 % (from ~35 %). Not verified on device: sixel rows and the cursor over a
placeholder. Follow-up: drive redraws from the gif's frame gap instead of per screen update, and a
generation counter on the placeholder so an unchanged frame skips the image walk.

**Landed 2026-09-09: one chrome apply per frame, and a free repeat frost pass.** With the scope
bits in the trace (`Chrome.requestSync <bits>`), four wall page changes showed 70 `applyChromeSpec`
runs (344 ms): the frame commit and the post-layout accessory pass were feeding each other — the
geometry pass requested an apply unconditionally, its 120 ms throttle still requested the full
post-layout apply, and the apply re-requested the accessory pass — and every apply re-dressed every
pane's frost synchronously (`Frost.updateTopPane` 78×, 174 ms). Now a geometry pass that moved
nothing requests nothing, the render pass coalesces a request made from inside itself and re-posts
only on a ledger dirty-generation change, and `PaneGlassBackdropView.setGlass` / the pane and page
appliers return early on identical inputs. Same four page changes after: 14 applies (89 ms), 8
commits (64 ms), 22 frost passes (57 ms). The worst arrival frame is still 40–53 ms: one apply
(8–11 ms on arrival), measure + insets (10 ms), layout with the widget grid's sized-RemoteViews
re-apply (`RemoteViews#applyActions` 9–12 ms, that is T9), and a second commit at pre-draw because
the insets dispatch requests an apply mid-traversal. Those three are what is left of the arrival
frame.

**Landed 2026-09-09: the launcher draws its wallpaper (223e13a1).** The glass could never be exactly
aligned while the system drew the wallpaper behind a translucent window: the ROM's composite zoom is
per wallpaper (Nothing OS: 1.03 for a 1328×2654 store, 1.084 for 1400×3100, 1.0 for a display-sized
image; `setWallpaperZoomOut` ignored) and the AOSP request the app made was the maximum zoom, not
true size (WallpaperController maps zoomOut 0 → `config_wallpaperMaxScale`; fixed to 1 in 6692e132).
Now `WallpaperBackdropView`, first child of the root container, paints the shared radius-0 frame at
the decor rect, opaque, so every glass crop is cut from the picture actually on screen. Mode policy
(`WallpaperBackdropPolicy`): self-drawn when the feature is on, the wallpaper is static and readable;
otherwise passthrough with the "Wallpaper alignment" slider (hidden in self-drawn mode). Measured on
pong with a coordinate-grid wallpaper: glass vs real wallpaper fit at zoom 1.01, shift (−2, 1), from
1.08 before; no black first frame (screens at 1.2 s and 2.7 s after launch fully painted). Page-slide
cost with the backdrop not yet re-traced.

## Recommended order

**Landed on dev, 2026-09-08 (uncommitted at the time of writing): T10 and A1.** Twelve trace
sections (`Touch.dispatch`, `Chrome.requestSync`, `Chrome.commit`, `Chrome.applyChromeSpec`,
`Place.syncLayout`, `Place.applyLook`, `Accessory.geometry`, `Blur.miss`, `Blur.preBlur`,
`Terminal.render`, `Panes.render`, `Keyboard.onPreferencesReloaded`, `Widgets.createHostView`), and
`SCOPE_APPLY_NOW` became `SCOPE_APPLY_THIS_FRAME`: one commit per frame in the root view's
animation phase (after input, before layout), with a pre-draw gate for requests made inside a
traversal. `SCOPE_ACCESSORY_RENDER` keeps its post-traversal pass on purpose — the two phases read
different layouts, so folding them would re-cut crops against a stale height.
**Also landed, 2026-09-08: T6's carry-over.** `TerminalRenderer` takes the renderer it replaces
and inherits the variable-font instance map and the fallback memo when the four primary faces and
the fallback chain are identical by reference; `TerminalView.setTextSize` and `setTypeface` pass
the old renderer. Not landed from T6: making `setTextSize` mutate in place (the ASCII measure
table and every metric are size-derived, so a rebuild is the honest shape). Whether the rebuild
was actually what made `nativeCreateFromTypefaceWithVariation` hot is still unmeasured — the
`Terminal.render` trace section now makes that a Perfetto query on pong.
**T1's equality skip was dropped, not deferred.** `applyChromeSpec` reads layout the spec does not
encode (the accessory stack's height, the inset-driven bounds), and every caller that asks for an
apply knows something moved that the spec may not show — the inset listener's spec is identical
before and after an IME animation frame. An identical spec is therefore not a no-op, and a skip on
it would leave stale bounds. `ChromeSpec` stays without `equals` until something needs one.

**Do these three first.**

1. **T10, the trace sections.** Before anything else, because every gain claimed above is currently
   unquantified and the app has no attribution at all. Eleven `beginSection` pairs and one section
   in `dispatchTouchEvent` turn "27–38 applies" into a number that moves when a fix lands. It is
   also the cheapest item in the document and the only one with no feature risk.
2. **T1 + T3 together, then A1.** T1 gives `ChromeSpec` an identity so a repeat apply is free; T3
   removes the side effect that would otherwise make T1 unsafe; A1 then turns the remaining applies
   into one per frame at the phase the platform guarantees sees the right layout. This is the single
   largest measured cost in the app (1.2–1.3 s per two page changes) and it is not a hard problem —
   it is the design `ChromeRenderer` already documents, applied to the one bit that opted out. Do
   T2 alongside T1, since it is the same class of guard.
3. **T6, the renderer caches.** Because it is small, because it plausibly explains the entire
   50 ms/381 ms figure in (b), and because until it is done nobody can tell whether the terminal
   render path needs architectural work at all. Pair it with T7, which is the same file and the same
   afternoon.

Then, in order: **T4** (main-thread disk read inside a tap — small, and the kind of thing that gets
worse silently, plus the `StrictMode` policy that would have caught it), **T5** (a 45–75 ms guard,
the palette pattern again), **T8** (one API call, API 26+, moves ~200 ms per widget off the main
thread), **T11** (a `PackageManager` sweep on the arrival frame), **T9**, **T12**, and only then
**A4**. **A5** and **A7** are tidy-ups: do them when convenient, and do not expect either to move
a measured number here.

**Worth a spike: A2, blur on the RenderThread.** It retires a deprecated API, a main-thread blur and
a 48 MB bitmap-cache budget, in one change (the wallpaper *decode* is T12's, not this one's). It is
the one architectural
item where the platform gives a clearly better primitive than what is in the tree. Spike it as a
throwaway: one surface (the dock backdrop), `RenderEffect.createBlurEffect` behind the existing
`SDK_INT >= S` gate, side by side with the current material on pong, and let the developer's eye
decide. If the material holds, the rest follows; if it does not, the study cost a day.

**Worth a spike, but after T6/T7: A3, row-level caching for the terminal.** Only if T6 and T7 leave
draw times above a few ms. ConnectBot proves dirty-region caching works for a terminal on Android;
nothing proves off-main-thread glyph rendering does, and `RenderNode`'s own threading rule does not
sanction the record-off-thread/draw-on-thread split that would be needed.

**Not worth it: A6.** The terminal on its own Surface or TextureView. Closing this alongside the
backlog's GL item, for the recorded reason: the chrome composites over the terminal, the wall
translates it, and the draw cost that motivated it is a cache problem.

## What could not be verified from a primary source

Listed rather than guessed, because a study whose numbers cannot be traced is worse than none.

- **`ScriptIntrinsicBlur`'s 25 px maximum radius.** `WallpaperBlurRenderer.java:30-38` is built
  around it and the number is universally cited, but the live reference page could not be read
  (Google's reference pages are client-rendered and return only navigation chrome to a fetch), and
  the AOSP javadoc for it was not located. Confirmed only against archived mirrors of the official
  page. Treat the code's cap as the code's assumption, not as a cited fact.
- **Whether `createFromTypefaceWithVariation` rebuilds a whole `FontCollection`.** The JNI shim is
  confirmed to build a fresh `minikin::VariationSettings` and call the native Typeface factory per
  call ([Typeface.cpp][typefacecpp]) — so it is demonstrably not a cache hit — but the factory body
  in `libs/hwui/hwui/Typeface.cpp` was not retrieved, so "it rebuilds the font collection" is
  unproven. T6 stands on the observed cost and on the renderer's own 73→12 ms note, not on this.
- **Any documented maximum blur radius for `RenderEffect.createBlurEffect`.** Not stated on any
  primary page. A2's clamp has to be found empirically.
- **The `dumpsys gfxinfo framestats` column names** (`INTENDED_VSYNC`, `HANDLE_INPUT_START`, …).
  The developer.android.com page that documented them is retired; the live rendering pages describe
  the phases qualitatively only. The columns still exist in the output — the documentation for them
  does not. Cite `FrameMetrics` constants instead, which are documented and which the app already
  reads.
- **A general-purpose Perfetto input-latency data source.** There is none for an arbitrary app; the
  `chrome.android_input` tables are Chrome-process specific. Touch-to-present must be derived from
  `android.frame_timeline` plus an app trace section.
- **That input events are batched or resampled once per vsync.** `MotionEvent.getHistorySize()`
  documents historical-sample batching for `ACTION_MOVE`, and `Choreographer` documents a
  `CALLBACK_INPUT` phase that "runs first", but nothing in `source.android.com/docs/core/interaction/input`
  ties resampling to a Choreographer phase. The per-frame-input claim is therefore treated here as
  the phase ordering only.
- **That `OnPreDrawListener.onPreDraw()` is called exactly once per draw.** The javadoc gives the
  timing ("all views in the tree have been measured and given a frame") but makes no
  exactly-once guarantee. A1 should be written to be idempotent rather than to rely on one call.
- **`androidx.tracing.Trace`'s equivalence to `android.os.Trace`.** The androidx source was not
  reachable and the current in-process-tracing guide documents a newer `Tracer` API instead. T10
  should use `android.os.Trace` (whose 127-code-unit section-name cap *is* documented) unless
  someone checks the androidx wrapper.
- **`Handler.hasCallbacks(Runnable)`'s API level** (29 per an index snippet, unconfirmed) and
  **`MessageQueue.IdleHandler`'s javadoc** (snippet only; the source file was not locatable in the
  current tree). Neither is load-bearing for any candidate — A1 uses pre-draw, not idle handlers —
  but do not cite them without checking.
- **Whether Baseline Profiles help warm or hot start.** The 30% figure is documented, and it is a
  first-launch figure; no primary page claims a warm or hot benefit, so A7 is not credited with one.
- **The Android 13/14 wallpaper-access change is not on Google's behavior-changes pages.**
  `/about/versions/13/behavior-changes-all`, `/14/behavior-changes-all` and
  `/15/behavior-changes-all` were fetched and grepped: none mentions `WallpaperManager`,
  `getDrawable` or `MANAGE_EXTERNAL_STORAGE`. The only primary source is the javadoc in
  `WallpaperManager.java`. Cite it as such.
- **`WallpaperManager.getWallpaperFile`'s API level** (24 per an index snippet) and **the
  `WallpaperColors` class's API level** (27) — neither confirmed by a direct read of a primary page.
  Not load-bearing: the app already gates the colours listener on `O_MR1`
  (`TermuxActivity.java:2093`).
- **That view inflation is UI-thread-only.** Not stated on the layout-optimization page that was
  read. `AsyncLayoutInflater`'s existence implies it; the doc does not say it. T8 does not rest on
  this — it rests on `setExecutor`'s own javadoc.
- **`AppWidgetProviderInfo.setTargetCellWidth`.** No such method: `targetCellWidth`,
  `targetCellHeight`, `maxResizeWidth` and `maxResizeHeight` are public int fields backed by XML
  attributes. Any plan written around a setter is written around nothing.
- **`Typeface.Builder.setFontVariationSettings` and `Paint.hasGlyph` API levels and cost notes.**
  Reference pages unreadable; no caching or performance note exists in the AOSP javadoc for
  `Typeface.createFromFile`, so "Typeface creation is expensive, cache it" is *not* a supportable
  citation — the supportable claim is the one in `setFontVariationSettings`'s own doc about
  mutating the underlying typeface below targetSdk 36.
- **Whether any open-source Android terminal renders off the main thread or through GL.** Termux
  upstream, jackpal's Android Terminal Emulator and ConnectBot were all read; none does. Nothing was
  found either way outside those three.

## Sources

Primary only. Google's `developer.android.com/reference/**` pages are client-rendered and could not
be fetched, so reference-class facts are cited to the AOSP javadoc they are generated from on
`android.googlesource.com`.

- [Choreographer.java][choreo] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/Choreographer.java>
- [ViewTreeObserver.java][vto] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewTreeObserver.java>
- [FrameMetrics.java][fm] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/FrameMetrics.java>
- [Handler.java][handler] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/Handler.java>
- [Trace.java][trace] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/Trace.java>
- [Window.java][window] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/Window.java>
- [WindowManager.java][wm] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/WindowManager.java>
- [RenderEffect.java][re] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RenderEffect.java>
- [RenderNode.java][rn] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RenderNode.java>
- [Canvas.java][canvas] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/Canvas.java>
- [RecordingCanvas.java][rc] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RecordingCanvas.java>
- [HardwareRenderer.java][hr] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/HardwareRenderer.java>
- [Bitmap.java][bitmap] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/Bitmap.java>
- [Paint.java][paint] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/Paint.java>
- [Typeface.java][typefacejava] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/Typeface.java>
- [Typeface.cpp (JNI)][typefacecpp] — <https://android.googlesource.com/platform/frameworks/base/+/master/libs/hwui/jni/Typeface.cpp>
- [MeasuredText.java][mt] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/text/MeasuredText.java>
- [LineBreaker.java][lb] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/text/LineBreaker.java>
- [PrecomputedText.java][pt] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/text/PrecomputedText.java>
- [SurfaceView.java][sv] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/SurfaceView.java>
- [TextureView.java][tv] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/TextureView.java>
- [AttachedSurfaceControl.java][asc] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/AttachedSurfaceControl.java>
- [Android vitals: rendering][render] — <https://developer.android.com/topic/performance/vitals/render>
- [Hardware acceleration][hwaccel] — <https://developer.android.com/topic/performance/hardware-accel>
- [Profile GPU Rendering][gpu] — <https://developer.android.com/topic/performance/rendering/profile-gpu>
- [JankStats][jank] — <https://developer.android.com/topic/performance/jankstats>
- [Macrobenchmark metrics][macro] — <https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics>
- [RenderScript compute (deprecation)][rs] — <https://developer.android.com/guide/topics/renderscript/compute>
- [Migrate from RenderScript][migrate] — <https://developer.android.com/guide/topics/renderscript/migrate>
- [Android 12 features (RenderEffect)][a12] — <https://developer.android.com/about/versions/12/features>
- [StrictMode.ThreadPolicy][strictmode] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/StrictMode.java>
- [MotionEvent.getHistorySize()][me] — <https://developer.android.com/reference/android/view/MotionEvent#getHistorySize()>
- [Touch and input in a ViewGroup][vg] — <https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup>
- [AppWidgetHostView.java][ahv] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetHostView.java>
- [AppWidgetHostView reference (API levels)][ahvref] — <https://developer.android.com/reference/android/appwidget/AppWidgetHostView>
- [AppWidgetProvider.java][awp] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetProvider.java>
- [RemoteViews.java][rv] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/RemoteViews.java>
- [WallpaperManager.java][wallpaper] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/WallpaperManager.java>
- [ImageDecoder.java][id] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/ImageDecoder.java>
- [BitmapFactory.Options][bfo] — <https://developer.android.com/reference/android/graphics/BitmapFactory.Options>
- [BitmapRegionDecoder][brd] — <https://developer.android.com/reference/android/graphics/BitmapRegionDecoder>
- [SharedPreferences.Editor][sp] — <https://developer.android.com/reference/android/content/SharedPreferences.Editor>
- [SharedPreferencesImpl.java][spi] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/SharedPreferencesImpl.java>
- [QueuedWork.java][qw] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/QueuedWork.java>
- [ActivityThread.java][at] — <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ActivityThread.java>
- [StrictMode.ThreadPolicy.Builder][strictmode] — <https://developer.android.com/reference/android/os/StrictMode.ThreadPolicy.Builder>
- [Baseline Profiles overview][bp] — <https://developer.android.com/topic/performance/baselineprofiles/overview>
- [App startup time][launch] — <https://developer.android.com/topic/performance/vitals/launch-time>
- [ANRs][anr] — <https://developer.android.com/topic/performance/vitals/anr>
- [App widget layouts (responsive sizes)][awl] — <https://developer.android.com/develop/ui/views/appwidgets/layouts>
- [Perfetto: android.frame_timeline][ft] — <https://perfetto.dev/docs/data-sources/frametimeline>
- [Perfetto: standard library][stdlib] — <https://perfetto.dev/docs/analysis/stdlib-docs>
- Termux upstream terminal render path — <https://github.com/termux/termux-app/blob/master/terminal-view/src/main/java/com/termux/view/TerminalView.java> and <https://github.com/termux/termux-app/blob/master/terminal-view/src/main/java/com/termux/view/TerminalRenderer.java>
- Android Terminal Emulator (jackpal) `EmulatorView` — <https://github.com/jackpal/Android-Terminal-Emulator/blob/master/emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java>
- ConnectBot dirty-region bitmap cache, tag `v1.9.13` — <https://github.com/connectbot/connectbot/blob/v1.9.13/app/src/main/java/org/connectbot/service/TerminalBridge.java> and <https://github.com/connectbot/connectbot/blob/v1.9.13/app/src/main/java/org/connectbot/TerminalView.java>

[choreo]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/Choreographer.java
[vto]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewTreeObserver.java
[fm]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/FrameMetrics.java
[handler]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/Handler.java
[trace]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/Trace.java
[window]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/Window.java
[wm]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/WindowManager.java
[re]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RenderEffect.java
[rn]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RenderNode.java
[canvas]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/Canvas.java
[rc]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RecordingCanvas.java
[hr]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/HardwareRenderer.java
[bitmap]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/Bitmap.java
[paint]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/Paint.java
[typefacejava]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/Typeface.java
[typefacecpp]: https://android.googlesource.com/platform/frameworks/base/+/master/libs/hwui/jni/Typeface.cpp
[mt]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/text/MeasuredText.java
[lb]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/text/LineBreaker.java
[pt]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/text/PrecomputedText.java
[sv]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/SurfaceView.java
[tv]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/TextureView.java
[asc]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/AttachedSurfaceControl.java
[render]: https://developer.android.com/topic/performance/vitals/render
[hwaccel]: https://developer.android.com/topic/performance/hardware-accel
[gpu]: https://developer.android.com/topic/performance/rendering/profile-gpu
[jank]: https://developer.android.com/topic/performance/jankstats
[macro]: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics
[rs]: https://developer.android.com/guide/topics/renderscript/compute
[migrate]: https://developer.android.com/guide/topics/renderscript/migrate
[a12]: https://developer.android.com/about/versions/12/features
[strictmode]: https://developer.android.com/reference/android/os/StrictMode.ThreadPolicy.Builder
[me]: https://developer.android.com/reference/android/view/MotionEvent#getHistorySize()
[vg]: https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup
[ahv]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetHostView.java
[ahvref]: https://developer.android.com/reference/android/appwidget/AppWidgetHostView
[awp]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetProvider.java
[rv]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/RemoteViews.java
[wallpaper]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/WallpaperManager.java
[id]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/ImageDecoder.java
[bfo]: https://developer.android.com/reference/android/graphics/BitmapFactory.Options
[brd]: https://developer.android.com/reference/android/graphics/BitmapRegionDecoder
[sp]: https://developer.android.com/reference/android/content/SharedPreferences.Editor
[spi]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/SharedPreferencesImpl.java
[qw]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/QueuedWork.java
[at]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ActivityThread.java
[bp]: https://developer.android.com/topic/performance/baselineprofiles/overview
[launch]: https://developer.android.com/topic/performance/vitals/launch-time
[anr]: https://developer.android.com/topic/performance/vitals/anr
[awl]: https://developer.android.com/develop/ui/views/appwidgets/layouts
[ft]: https://perfetto.dev/docs/data-sources/frametimeline
[stdlib]: https://perfetto.dev/docs/analysis/stdlib-docs
[wms]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/wallpaper/WallpaperManagerService.java
[mppu]: https://android.googlesource.com/platform/packages/providers/MediaProvider/+/refs/heads/main/src/com/android/providers/media/util/PermissionUtils.java
