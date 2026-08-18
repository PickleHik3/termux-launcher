# Features A-1 and A-2 implementation plan

This plan covers the first two Epic A delivery slices against `app/termux-launcher` branch `dev` at
commit `dac3e486`: A-1 (the widget-host foundation, with no picker/grid UI) and A-2 (the third,
full-height status-pane state). It assumes the reported committed Epic B baseline of 1,256 unit tests,
zero failures, in both variants. This artifact contains no production code.

All proposed production and test code is Java 11. Unit coverage is JUnit4, with Robolectric only where
an Android view/lifecycle seam is useful. No Kotlin and no `androidx.dynamicanimation` are introduced.
Every new spring-driven motion uses the existing `com.termux.app.Spring`.

## 0. Verified constraints and reusable seams

| Area | Verified seam | Consequence for A-1/A-2 |
|---|---|---|
| Current top-pane hierarchy | `activity_termux.xml:72-89` puts `terminal_window_bar_host` first in `terminal_content_column`; `terminal_surface_host` is the following weighted child at `:261-294`. | FULL grows the existing host in this real layout. It is not a second root overlay and does not reuse the app drawer plane. |
| App drawer isolation | `activity_termux.xml:1634-1670` declares `app_drawer_host` as a full-screen sibling deliberately outside the accessory stack. `AppDrawerController.java:56-62` freezes accessory geometry because that overlay is transform-only. | This is the contrast case, not the A-2 model. Drawer code and status-pane code remain separate controllers, states, geometry and touch policies. |
| Accessory height contract | `AccessoryStackLayoutPolicy.java:7-24` sums the explicitly sized bottom apps/A-Z/extra-key bands. Its production call is `TermuxActivity.java:2669`. | FULL does **not** enter `computeCombinedHeight()`: it is a top child of the terminal content column, not a bottom accessory band. Its own layout height nevertheless remains real, so the weighted terminal sibling shrinks without clipping or dead space. |
| Compact/expanded sizes | `TermuxActivity.java:2494-2496` resolves 30/100dp for Rounded and 32/96dp for Default. | Preserve these exact endpoints and the existing collapsed preference. FULL is transient and does not become a third persisted preference value. |
| Existing resize path | `setTopStatusBarCollapsed()` is at `TermuxActivity.java:11365-11436`; it uses 260ms distance-scaled animation and `PathInterpolator(.16,1,.3,1)` at `:11409-11417`. | Compact/expanded behavior stays byte-for-byte. A separate FULL controller can begin from either endpoint or from the current in-flight height without rewriting the shipped two-state animator. |
| Terminal resize coalescing | `beginStatusBarTerminalResize()` / `finishStatusBarTerminalResizeAfterLayout()` call the pane controller at `TermuxActivity.java:11350-11362`; the comment explicitly promises one final row/column update instead of one per frame. | A-2 reuses this bracket for the entire FULL enter/exit. Layout still changes each frame, but terminal size/SIGWINCH is emitted once at final settle, never once per frame. |
| Current row solver | `StatusBarResizeGeometry.calculate()` at `statusbar/StatusBarResizeGeometry.java:22-40` clamps compact-to-expanded progress and keeps the status row attached to the moving lower edge. | Extend it with an explicit three-state/full input rather than passing an oversized height accidentally. The FULL rule intentionally leaves the status row on the pane's lower edge and the top slot at the top, reserving the body for A-3. |
| Status gesture | `StatusBarSwipeLayout.java:45-90` currently intercepts horizontal swipe outside the window bar; its fixed DOWN fields are at `:22-29`, and the window-bar exclusion is `:132-142`. | Replace independent timeout/swipe decisions with one pure, one-way claim policy and an immutable DOWN snapshot. Window-bar and interactive-child streams are never intercepted. |
| Window-bar ownership | `TerminalWindowBar.java:186-220` is a `HorizontalScrollView`, disallows parent interception on DOWN, and already owns scrolling/edge overswipe. | A-2 never arms long-press for a DOWN in this rectangle. Window switching, chip taps and the existing edge overswipe remain child-owned for the whole stream. |
| Top-row contention | `TopPaneWidgetSlot.java:28-49` owns its mode; `TopPaneSlotMode.derive()` and clock form priority are at `TopPaneSlotMode.java:10-45`. Current measurement/layout is `TopPaneWidgetSlot.java:212-318`. | FULL adds a layout presentation to this same owner. It does not create a competing media/notification priority policy. |
| Status glass | The existing pane already contains `terminal_window_bar_wallpaper_backdrop`, `terminal_window_bar_blur` and `terminal_window_bar_background` (`activity_termux.xml:91-111`). `refreshTerminalWindowBar()` styles those exact views at `TermuxActivity.java:11468-11489`. | Grow those views with the host. Do not create a new blur, backdrop or glass slab for FULL. |
| Shared pre-blur cache | The radius-keyed, access-ordered cache is capped at three entries at `TermuxActivity.java:663-671`; lookup/eviction is `:3909-3961`. Top-pane frost already uses the status blur radius at `:4657-4688`. | Reuse the existing status blur radius. Do not raise the LRU cap and do not introduce a FULL radius key. A-2 also avoids cutting a new bitmap on every height frame. |
| House spring | `Spring.java:19-74` provides the clamped/substepped integrator, finite-value guard and reduced-motion snap. | The FULL progress channel is one `Spring(0, 420, 41)` on one controller-owned `Choreographer` callback. Clock/top-row bounds are pure functions of that progress, not separately animated. |
| Back ordering | The single override is `TermuxActivity.java:10032-10058`: palette, app drawer, dock tuning, then navigation drawer. The drawer's internal Back policy is called at `AppDrawerController.java:464-473`. | Add one explicit FULL consumer and pin the complete order with the existing source-order test. |
| Lifecycle/result hooks | `TermuxActivity.onStart()` is `:865-929`, `onStop()` is `:4960-5010`, and legacy result routing is `:10200-10219`. Existing request codes 4711-4713 are declared at `:280-282`. | Construct the host controller during activity setup; call host start/stop at the matching lifecycle boundaries; reserve 4714/4715 and route them through the controller before unrelated result cases. |
| Manifest gap | Permissions are `AndroidManifest.xml:22-46`; `TermuxActivity` and its HOME filter are `:106-139`. A repository-wide search at `dac3e486` finds no `AppWidgetHost`, `BIND_APPWIDGET`, app-widget host receiver or host metadata. | Add the protected permission declaration, but design for it not being granted. Hosting itself needs no provider receiver or `android.appwidget.provider` metadata. |
| Package-change seam | The package receiver and `LauncherApps.Callback` are registered at `TermuxActivity.java:12738-12808`. Their common production refresh enters `refreshSuggestionBarFromPackageState()` at `:12822-12830`, above the dock-enabled guard. | Add widget-provider reconciliation at this same above-guard call site. It must run even if the dock/drawer is disabled or never built. |
| Drawer gesture precedent | `AppDrawerGestureArbiter.java:41-89` freezes eligibility at DOWN and `:151-171` latches one claim forever. `AppDrawerPlaneView.java:280-295` samples content ownership once. | Reuse the architectural pattern, not the drawer class itself: a status-specific pure policy, frozen snapshot, and one-way claims. A status policy that is tested but not called from `StatusBarSwipeLayout` is not acceptable. |
| Launcher bitmap budget | `SuggestionBarView.java:247-269` owns the shared 6-16MiB byte-budgeted rendered-icon LRU; byte accounting is `:488-510`, and drawer/category clients access it through public seams at `:2724-2729`. | Widget chrome never creates a second icon cache or widget snapshot. Provider-supplied `RemoteViews` bitmaps cannot honestly be put into this LRU; their limits and verification are treated separately below. |

## 1. Cross-slice architectural decisions

### 1.1 FULL is real top-pane geometry, not a drawer-style overlay

The FULL status pane remains `terminal_window_bar_host`. Its height grows inside
`terminal_content_column`, and the weighted `terminal_surface_host` below it gives up exactly the same
space. This is the only design that simultaneously preserves the visual requirement (“the pane grows”),
reuses the existing blur/backdrop, and avoids a band whose pixels and measured geometry disagree.

This has precise consequences:

- FULL is **not** added to `AccessoryStackLayoutPolicy.computeCombinedHeight()`. That solver owns the
  explicitly sized bottom accessory stack, while the status host is already a measured top sibling.
- Opening/closing FULL makes **no** call to `requestAccessoryGeometrySync()`,
  `applyAccessoryGeometryIfNeeded()`, `setTerminalToolbarHeight()` or a dock height setter.
- It **does** resize `terminal_surface_host`. The existing pane-controller resize bracket suppresses
  intermediate terminal updates and deliberately produces one final `TerminalView.updateSize()` /
  SIGWINCH when the FULL spring settles, and one when it settles back. That final update is acceptable:
  the terminal's real viewport changed. Zero per-frame SIGWINCH is the invariant.
- The FULL target is computed from the measured inner height of `terminal_content_column`, minus its
  actual bottom padding and the host's top margin. It is not computed from raw display metrics. The
  column already reflects display cutouts, the configured terminal vertical margins applied at
  `TermuxActivity.java:5277-5279`, and the current bottom accessory stack, so “screen bottom minus
  in-app padding” has one concrete source of truth.
- While FULL is engaged, a parent layout change (for example an already-authorized keyboard/accessory
  change elsewhere) recomputes this target from the new measured column. A-2 does not initiate that
  accessory change; it only follows the resulting bound so it cannot clip or leave dead space.

### 1.2 Surface exclusivity and Back order

FULL is modal relative to the launcher's other full-screen surfaces. Entering it first closes an engaged
app drawer immediately and dismisses dock/folder popups; `TerminalCommandPaletteController.show()` adds a
call to close FULL immediately beside its existing drawer close at `TerminalCommandPaletteController.java:210-215`.
The dock's drawer eligibility snapshot gains a `fullStatusPaneClosed` veto at the production capture in
`SuggestionBarView.java:1923-1938`, so a dock swipe cannot stack the drawer over FULL.

With stacking ruled out, `TermuxActivity.onBackPressed()` is exactly:

1. If FULL is opening, open or closing, consume Back and retarget/snap it to its recorded prior state.
2. Else collapse the command palette.
3. Else run the app drawer's existing internal hierarchy: clear a query; collapse category detail; otherwise
   close the drawer (which also tears down its drag/popup/rename state).
4. Else exit dock tuning.
5. Else close the left navigation drawer if open.
6. Else preserve the current fallback and open the left navigation drawer.

The FULL branch is literally first, as required. Repeated Back during its exit is consumed until the
spring settles; it cannot fall through and open/close another surface during the same transition.

### 1.3 Bitmap accounting is honest

Launcher-owned widget material uses vectors/drawables and no bitmap snapshot. If A-3 later shows provider
icons/previews outside `RemoteViews`, those go through the existing `SuggestionBarView.getRenderedIcon()`
and its 6-16MiB byte budget, just like drawer/folder art.

The pixels embedded by a third-party provider in `RemoteViews` are different: Android unmarshals and owns
that view content, and the launcher cannot redirect those bitmaps through `normalizedIconCache` without
copying, reflection or changing provider semantics. A-1 therefore does **not** claim they are charged to the
icon LRU. It contains them by:

- never taking a bitmap or full-view snapshot for transitions, drag, errors or persistence;
- constructing at most one host view per persisted app-widget ID and not duplicating it between surfaces;
- attaching host views only to the visible widget grid in A-3, with a future attachment policy based on
  visible host count/pixel area rather than a fake byte count;
- releasing launcher references when a widget is removed or its provider becomes invalid;
- relying on the platform's `RemoteViews` bitmap transaction ceiling as a platform backstop, then measuring
  real heap behavior on device because that ceiling is not the launcher's 6-16MiB cache.

## Part I — A-1: widget host foundation

### 2. Host, view and repository classes

All new classes live under `com.termux.app.launcher.widget`; widget hosting does not enter the drawer package.

| Class | Kind | Responsibility |
|---|---|---|
| `LauncherAppWidgetHost` | `AppWidgetHost` subclass | Own stable host ID `APPWIDGET_HOST_ID = 0x544C`; override `onCreateView()` to return the safe host view; forward `onProviderChanged`, `onProvidersChanged` and API-30 `onAppWidgetRemoved` to the controller after the framework implementation. |
| `SafeLauncherAppWidgetHostView` | `AppWidgetHostView` subclass | Supply the launcher error tile via `getErrorView()` and guard the synchronous host-owned RemoteViews/layout/draw/touch boundaries described in section 5. Report failure/recovery by app-widget ID without knowing persistence. |
| `LauncherWidgetHostController` | lifecycle/orchestration | Own `AppWidgetManager`, `LauncherAppWidgetHost`, repository and one pending add flow. Start/stop listening, allocate/delete IDs, bind or request consent, launch configuration, route results, create host views, update size options and reconcile providers. |
| `LauncherWidgetRepository` | durable synchronized store | Persist schema, active records, provider tombstones and the one pending external transaction with synchronous commit-before-launch semantics. Expose immutable snapshots and injectable storage for tests. |
| `LauncherWidgetRecord` | pure immutable | App-widget ID, provider component, profile serial, state (`ACTIVE`, `PROVIDER_MISSING`, `DELETING`), last committed size options and last render failure. A-3 extends it with cell geometry; A-1 does not invent placement UI. |
| `WidgetAddTransaction` | pure immutable | Token, app-widget ID, provider/profile, stage, requested options and start time. Stages are `ALLOCATED`, `WAITING_FOR_BIND_CONSENT`, `BOUND`, `WAITING_FOR_CONFIGURATION`, `COMMITTING`. |
| `WidgetBindFlowPolicy` | pure | Legal stage/result transitions and cleanup outcome (`CONTINUE`, `READY`, `DECLINED`, `FAILED_DELETE_ID`, `IGNORE_FOREIGN_RESULT`). |
| `WidgetConfigurePolicy` | pure | Decide whether the provider's configure activity is mandatory from `configure` and widget feature flags; produce `NONE`, `REQUIRED`, or `UNAVAILABLE`. |
| `WidgetSizeOptionsPolicy` | pure | Convert committed full host bounds to integer dp; retain the last known portrait/landscape sizes; emit min/max width/height plus API-31 size list; equality/dedup. |
| `WidgetProviderReconcilePolicy` | pure | Compare persisted records, host-owned IDs and current `AppWidgetProviderInfo`; choose keep, refresh-after-update, tombstone/delete-ID, resume deletion or ignore foreign host ID. |

Production consultation is explicit:

- `LauncherWidgetHostController.beginAdd()` and `handleActivityResult()` call
  `WidgetBindFlowPolicy`; no Activity branch reimplements its transitions.
- After either direct binding or consent success, the controller calls `WidgetConfigurePolicy` before
  any record can become active.
- `LauncherWidgetHostController.onHostSizeCommitted()` calls `WidgetSizeOptionsPolicy` before
  `AppWidgetManager.updateAppWidgetOptions()`; A-3's resize-end callback will call this same production seam.
- `LauncherWidgetHostController.reconcileProviders()` calls `WidgetProviderReconcilePolicy`; that method is
  invoked from `onStart`, all three host provider callbacks, and the package-refresh call site at
  `TermuxActivity.java:12822-12830`.

### 3. Manifest and platform capability

Add:

```xml
<uses-permission
    android:name="android.permission.BIND_APPWIDGET"
    tools:ignore="ProtectedPermissions" />
```

The declaration follows the host contract but is **not** treated as a runtime grant. On ordinary installs it
is signature/privileged-gated. Every add therefore first calls `bindAppWidgetIdIfAllowed()` and treats `false`
as the normal path to `ACTION_APPWIDGET_BIND`, not as an exceptional or unsupported state.

Do **not** add an `AppWidgetProvider` receiver, `android.appwidget.provider` metadata, an invented
`APPWIDGET_HOST` action, or `appwidget-host` metadata. Those describe a provider or do not exist in the host
contract; `AppWidgetHost` communicates with the system service directly. Device-backup ID remapping via
`ACTION_APPWIDGET_HOST_RESTORED` is an A-4 restore concern, not required for activity recreation/process death.

At controller construction, check `PackageManager.FEATURE_APP_WIDGETS`. If absent, expose `UNSUPPORTED` and
do not allocate an ID or start an intent. A-3 can render the user-facing unsupported state.

### 4. Consent, configuration and exact ID lifecycle

#### 4.1 Begin add

A-1 has no picker or placement UI. Its production API accepts a selected `AppWidgetProviderInfo` plus initial
size options; A-3's picker will call it. Device verification can invoke it through a test harness/provider,
but no hidden production button or hard-coded provider ships.

The sequence is transactional:

1. Reject a second add while one transaction is pending. A later UI can offer explicit cancel/retry; two
   request-code results must never race one mutable field.
2. Allocate with `LauncherAppWidgetHost.allocateAppWidgetId()`.
3. Synchronously persist `ALLOCATED` with ID, provider `ComponentName`, profile serial, a random transaction
   token and options. If persistence fails, immediately `deleteAppWidgetId()` and return storage failure.
4. Call the profile-aware `bindAppWidgetIdIfAllowed(id, profile, provider, options)` on API 21+; use the
   component/options overload on older supported APIs.
5. If it returns true, persist `BOUND` and continue to configuration.
6. If it returns false, synchronously persist `WAITING_FOR_BIND_CONSENT`, then launch
   `ACTION_APPWIDGET_BIND` with `EXTRA_APPWIDGET_ID`, `EXTRA_APPWIDGET_PROVIDER`,
   `EXTRA_APPWIDGET_PROVIDER_PROFILE` where supported, and `EXTRA_APPWIDGET_OPTIONS`, request code **4714**.
   `ActivityNotFoundException`/`SecurityException` is a failed add: mark deleting, delete the ID, clear pending.

#### 4.2 Bind result

`TermuxActivity.onActivityResult()` first offers request codes 4714/4715 to the widget controller. The
controller reloads the durable transaction; it never relies on a field surviving recreation.

- `RESULT_CANCELED` is a normal user decline. Persist `DELETING`, call `deleteAppWidgetId()` exactly once,
  clear the pending record, and return `DECLINED`. Do not create an error tile, active record or retry loop.
  A-3 may show “Widget wasn't added”; A-1 only reports/logs the outcome.
- `RESULT_OK` does not trust arbitrary returned extras. Accept only the persisted pending ID (the returned ID
  may confirm it); load `AppWidgetManager.getAppWidgetInfo(id)` and require the expected provider/profile.
  Missing or mismatched info is failure plus deletion.
- A result for the right request code but no matching pending stage is ignored and logged. It must never delete
  an ID belonging to an active record.
- A valid bound result persists `BOUND` and enters configuration.

#### 4.3 Configure result

After direct or consent binding, inspect the freshly loaded `AppWidgetProviderInfo`:

- No `configure` component: commit active immediately.
- A mandatory configure component: persist `WAITING_FOR_CONFIGURATION`, then call
  `LauncherAppWidgetHost.startAppWidgetConfigureActivityForResult(activity, id, 0, 4715, options)`. Use the
  host helper, not a raw cross-profile `startActivityForResult`, so work-profile configuration is supported.
- A configuration explicitly optional and reconfigurable under the provider feature flags may skip initial
  configuration; the exact feature decision is isolated and SDK-gated in `WidgetConfigurePolicy`.
- Missing/blocked configure activity is not a usable widget. Clean up the ID and report
  `CONFIGURATION_UNAVAILABLE` rather than persisting a half-configured tile.
- `RESULT_OK`: verify the same persisted ID/provider again, commit one `ACTIVE` record, clear pending, then
  create/attach a host view only when a caller requests it.
- `RESULT_CANCELED`: mark deleting, delete the bound ID, clear pending. The provider/system receives the normal
  deletion lifecycle; the launcher shows no active widget.

Every path that abandons an allocation uses `deleteAppWidgetId()`. Routine removal never calls `deleteHost()`
or `deleteAllHosts()`: those would erase unrelated active widgets. A user-requested full widget-data reset may
use `deleteHost()` only after the repository has durably entered a reset state.

#### 4.4 Crash-safe removal

Deletion is two-phase to avoid leaking a system allocation if the process dies:

1. Commit record state `DELETING`.
2. Call `deleteAppWidgetId(id)`; “already gone” is success.
3. Commit removal from the repository.

On startup, reconciliation resumes every `DELETING` record. If step 3 fails after system deletion, the next
pass observes missing widget info and finishes the repository cleanup. Add-finalization is symmetrical: if the
ACTIVE commit fails after bind/configure, the controller transitions to deletion rather than leaving an
untracked system ID.

### 5. Crash isolation and its limits

`SafeLauncherAppWidgetHostView` uses two layers:

1. Override `getErrorView()` so the framework's own `AppWidgetHostView` RemoteViews inflate/apply fallback is
   launcher-styled and allocation-light.
2. Guard host-owned synchronous boundaries: `updateAppWidget(RemoteViews)`, `onMeasure`, `onLayout`,
   `dispatchDraw` and `dispatchTouchEvent`. Catch **`RuntimeException` only** (including inflate/action,
   resource, class-cast and security failures), record the failing phase, and replace/post-replace the content
   with the error view. Draw/touch failures post replacement after dispatch rather than mutating the child list
   during traversal. A later provider update or APK update recreates a clean host view and may recover.

The error tile is a local vector warning mark plus localized “Widget couldn't load” and provider app label when
that label resolves safely. It has an accessibility description, no provider bitmap, no retry animation and no
click in A-1. It never renders the exception text or package internals to the user.

The catch boundary is deliberately not `Throwable`:

- Do not catch `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath`, linkage/VM errors, native renderer
  faults or process kills. Continuing after those can corrupt the process or loop on every frame.
- A provider normally runs in another process. Its process crash does **not** execute provider code in the
  launcher and is not the same as malformed `RemoteViews` inflation. The host usually keeps the last delivered
  view or waits for another update; it must not mark the provider uninstalled merely because updates stop.
- This wrapper cannot prevent an ANR caused by pathological framework work on the launcher main thread, a
  system-server failure, a Binder unmarshalling failure before the callback reaches the host view, GPU/driver
  failure, or platform-enforced process death from memory pressure.
- Remote collection adapters/services remain remote. Their provider process failure should yield stale/empty
  collection content, not a launcher exception; any runtime exception that actually bubbles through this host
  view's measure/layout/draw boundary is caught as above.

This is real containment of recoverable view exceptions, not a promise that arbitrary third-party code can
never affect the host process.

### 6. Size options and resize behavior

`LauncherWidgetHostController.onHostSizeCommitted(id, widthPx, heightPx, orientation)` is the only size-write
entry point. A-3 calls it on initial placement, resize-handle release and configuration/display-size reflow—not
on each drag frame.

`WidgetSizeOptionsPolicy`:

- treats the full `AppWidgetHostView` area as the supplied size; framework widget padding is not manually
  subtracted;
- rounds px to positive integer dp using current density;
- stores the last valid portrait and landscape size for that widget;
- emits `OPTION_APPWIDGET_MIN_WIDTH`, `MIN_HEIGHT`, `MAX_WIDTH`, `MAX_HEIGHT` as the minima/maxima of known
  orientations; until both are known, min=max=current;
- adds `OPTION_APPWIDGET_HOST_CATEGORY = WIDGET_CATEGORY_HOME_SCREEN`;
- on API 31+, also adds a deduplicated `OPTION_APPWIDGET_SIZES` list of the known `SizeF` values, capped well
  below the platform's 16-view limit;
- compares against the persisted last bundle and makes no platform call if all effective values are unchanged.

For a changed result, persist the new options then call
`AppWidgetManager.updateAppWidgetOptions(appWidgetId, bundle)` once. If the provider rejects/crashes remotely,
the record remains active; if a synchronous `RuntimeException` occurs, record it and keep the prior committed
options so a later resize may retry. Do not issue one provider callback per pixel of A-3's resize gesture.

### 7. Recreation, process death, uninstall and update

- Host ID `0x544C` is compile-time stable and package-scoped. Never derive it from a resource, process, build
  variant or edition application ID.
- App-widget IDs are allocated by the system and stored durably with provider/profile identity. Activity
  recreation rebuilds host view objects from those IDs; it never allocates replacements for active records.
- The pending transaction is committed before either external activity is launched. After process death,
  `onActivityResult` reloads it. On a cold start without a delivered result, reconciliation does not silently
  relaunch consent: if the ID is now bound, resume mandatory configuration once; if still unbound, retain the
  pending transaction for explicit user resume/cancel and age it out to deletion after 24 hours. This avoids a
  surprise system dialog on launch and avoids an immortal allocated ID.
- `LauncherAppWidgetHost.startListening()` runs once from `TermuxActivity.onStart()` after invalid-state checks;
  `stopListening()` runs once in `onStop()` before activity-owned views/listeners are torn down. Both are
  idempotent and guarded against partial initialization.
- Provider APK **update**, same component: keep the ID and record. `AppWidgetHost.onProviderChanged()` first lets
  the framework reset/reinflate, then clears any render-failure marker, refreshes provider metadata and recreates
  a safe host view if necessary. Recompute options only if size constraints actually change.
- Provider **uninstall**, disabled/removed component, or inaccessible profile: `getAppWidgetInfo(id)` becomes
  absent/mismatched. Commit a `PROVIDER_MISSING` tombstone containing component/profile, delete the unusable
  system ID, release its host view and let A-3 show a provider-removed tile. Reinstall does not silently rebind:
  a new ID and user consent may be required. A-4 can offer explicit re-add/restore using the tombstone.
- `onProvidersChanged`, API-30 `onAppWidgetRemoved`, `onStart`, and the activity's package-refresh seam all run
  the same idempotent reconciliation. There is no widget-specific package receiver competing with the shipped
  receiver.

## Part II — A-2: full-screen status-bar expansion

### 8. State, geometry and motion classes

| Class | Kind | Responsibility |
|---|---|---|
| `TopStatusBarState` | pure enum | `COMPACT`, `EXPANDED`, `FULL`; helpers for persisted two-state preference and whether normal swipe is allowed. |
| `FullStatusBarGeometry` | pure | Resolve prior endpoint, FULL height from measured parent inner bounds, normalized progress/current height, status-row geometry and safe response to short/zero dimensions. |
| `StatusBarGesturePolicy` | pure | Immutable DOWN eligibility plus one-way `PENDING`, `HORIZONTAL_SWIPE`, `LONG_PRESS`, `CHILD_OWNED`, `CANCELLED` claims. It owns no clock/handler. |
| `FullStatusBarController` | state/controller | Record prior state, own one `Spring`/frame callback, cancel the two-state animator safely, bracket terminal resize, apply frames, retarget Back, follow parent relayout and perform immediate lifecycle teardown. |
| `TopPaneFullRowPolicy` | pure | Given `TopPaneSlotMode`, pinned count, measured child desires and width, compute centred FULL clock/group bounds with gaps and minimum widths. |

Existing classes change as follows:

- `StatusBarResizeGeometry` gains an explicit full-state overload/result. Compact-to-expanded output from the
  old signature remains identical. For expanded-to-FULL, top-slot alpha remains 1, the top slot stays at top,
  and the status row follows the moving lower edge with the existing expanded row height/bottom margin.
- `StatusBarSwipeLayout` becomes the production caller of `StatusBarGesturePolicy` from
  `dispatchTouchEvent`, `onTouchEvent` and nested-scroll callbacks. Its listener separately reports normal
  compact/expanded requests and FULL requests.
- `TopPaneWidgetSlot` gains `setFullExpansionProgress(float)` and calls `TopPaneFullRowPolicy` from its real
  `onMeasure/onLayout`. `TopPaneSlotMode` remains unchanged and is still derived once in `applyFeed()` at
  `TopPaneWidgetSlot.java:108-136`.
- `TerminalClockWidget` gains a host-controlled content-width/centred presentation used only by the FULL row.
  Existing forms and drawing remain unchanged at progress 0.
- `TermuxActivity` constructs/binds the controller, supplies measured views/preferences/pane-resize callbacks,
  maps the existing boolean collapsed preference to COMPACT/EXPANDED, inserts the Back branch, and exposes
  `isFullStatusBarEngaged()` to drawer/palette eligibility.

Again, each policy has a production call site: `StatusBarGesturePolicy` in `StatusBarSwipeLayout`,
`FullStatusBarGeometry` on every `FullStatusBarController.applyFrame`, and `TopPaneFullRowPolicy` in
`TopPaneWidgetSlot.onMeasure`. Tests alone are not considered integration.

### 9. Exact three-state behavior

Normal state remains driven by the existing preference:

```text
COMPACT  --right swipe--> EXPANDED
EXPANDED --left swipe---> COMPACT

COMPACT/EXPANDED --eligible long press--> FULL(prior captured once)
FULL/opening/closing --Back-------------> prior
```

- At long-press commit, capture the current two-state **target**, not merely the current pixel height. If the
  260ms compact/expanded animator is running, cancel it, keep its current height as the FULL spring start, and
  remember its requested endpoint as `prior`. There is no snap.
- Enter uses normalized spring progress `0 -> 1`; exit retargets the same spring `1/current -> 0`.
  `Spring(0,420,41)` gives the established roughly 260ms arrival, clamps dropped frames and snaps under reduced
  motion. No `ViewPropertyAnimator`, second `ValueAnimator` or second Choreographer loop is added for the clock.
- `applyFrame` is the sole writer of host height, status-row geometry, top-slot progress, outline invalidation
  and frost alignment while engaged.
- The FULL target is re-resolved from the stable measured content-column bounds. At settle, clear transient
  clip/translation properties, finish the terminal resize bracket once, and leave `TopStatusBarState.FULL`.
- On Back, the recorded prior endpoint is authoritative even if the collapsed preference changed elsewhere
  while FULL was open. At exit settle, write/apply that normal state once and clear `prior`.
- On `onStop`, configuration teardown or a palette takeover, `closeImmediateToPrior()` cancels the frame callback,
  applies the prior endpoint, clears interaction, and finishes any open resize bracket. FULL is not restored on
  a later launcher entry. Active widget IDs are unaffected because their lifetime belongs to A-1's repository,
  not this visual state.
- A recreation while the activity remains logically visible saves `FULL + prior` in instance state and restores
  after the first measured layout without animation; a cold HOME launch defaults to the persisted COMPACT or
  EXPANDED preference. This preserves rotation/recreation without making FULL a long-lived preference.

### 10. Exact gesture arbitration

`StatusBarSwipeLayout` stops using interception as a late contest. `onInterceptTouchEvent()` never claims a
child stream. The parent observes dispatch, consumes only background/chrome streams for which it was already the
touch target, and participates as a non-consuming `NestedScrollingParent3` observer for scrollable descendants.

At ACTION_DOWN it records one immutable `StatusBarGesturePolicy.Down`:

- pointer ID, raw/local position and uptime;
- `TopStatusBarState` and current normal target;
- whether the point is inside `terminal_window_bar`;
- whether it is inside an interactive child (sessions/settings, clock, notification/media action, or an
  `AppWidgetHostView` when widgets are later present);
- whether a nested-scrolling descendant owns the point;
- whether FULL/drawer/palette/dock-tuning/another status transition is engaged;
- touch slop and long-press timeout token.

Claims are one-way:

1. A DOWN in the window bar or any interactive/widget child latches `CHILD_OWNED` immediately. No long-press
   runnable is posted and the parent consumes zero nested-scroll distance. Window-bar taps/scroll/overswipe and
   widget `PendingIntent`, button and collection gestures are untouched.
2. Eligible chrome starts `PENDING`. Before timeout, horizontal movement beyond slop and dominant over vertical
   latches `HORIZONTAL_SWIPE`, cancels the timer and sends the existing compact/expanded request once on UP.
3. Vertical movement beyond slop, a second pointer, nested-scroll start, detach, window focus loss, UP or CANCEL
   before timeout latches `CHILD_OWNED`/`CANCELLED`; it can never become a long-press later.
4. The timeout checks the original token and may change only `PENDING -> LONG_PRESS`. It performs one long-press
   haptic and calls the FULL listener once. It does not redispatch, synthesize CANCEL or steal a child's stream.
5. After any claim, later diagonal drift cannot switch it. A neutral diagonal remains pending until it resolves
   or cancels; it never fires both swipe and long-press.

As in B-6, the terminal-dispatch claim/token is separate from resettable layout state. The FULL callback causes
reentrant state/layout changes; those may clear structural tracking but cannot clear the current dispatch latch
until the enclosing `dispatchTouchEvent()` `finally`. This directly prevents the prior “reset cleared the latch,
then terminal dispatch fired a second action” defect class.

When FULL is active, status swipe/entry-long-press eligibility is false. Widget content gets ordinary Android
touch dispatch. A-3 edit mode must put move/resize handles in launcher-owned siblings around the
`AppWidgetHostView`; it must not intercept inside arbitrary RemoteViews. Scrollable widget descendants use
nested scrolling normally, and the status parent observes/consumes zero. Thus a widget's tap/list scroll and a
launcher gesture cannot both own one stream.

### 11. Glass, blur cache and top-row relayout

#### 11.1 Blur/cache decision

Reuse `getEffectiveStatusBarBlurRadius()` for compact, expanded and FULL. The same
`terminal_window_bar_blur` grows; in wallpaper passthrough the same cached full wallpaper frame for that radius
feeds `terminal_window_bar_wallpaper_backdrop`. The three-entry LRU remains three because A-2 adds no key.

Do not call `applyWallpaperFrostCrop()` on every animation frame: its non-full-frame path allocates a target-size
ARGB bitmap at `TermuxActivity.java:3974-3998`. Instead, add a screen-aligned, non-owning drawable/image-matrix
path that displays the already cached full blur frame through the existing backdrop and lets the growing host
clip progressively more of it. Extend `isSharedWallpaperBlurFrameInUse()` (`:4001-4020`) to include the top-pane
backdrop before any cache eviction/recycle. Bind once before motion, update only the alignment matrix when root
bounds change, and release/revert after exit. This removes both radius thrash and per-frame full-surface crops.

Corner radius is not the cache key. Rounded style keeps its existing configured status corner radius and outline;
Default remains edge-to-edge/square. No “FULL blur radius” or “FULL corner token” is introduced.

#### 11.2 Clock/media/pinned layout

At progress 0, `TopPaneWidgetSlot` output is byte-identical to the current bounds. At progress 1:

- `CLOCK_ONLY`: measure the clock to its real painted content width and centre that child in the available top
  row. Interpolate its left/right bounds from the current left-aligned/full-width presentation, producing the
  requested visible rightward movement.
- `MEDIA`, `NOTIFICATIONS`, `NOTIFICATIONS_AND_MEDIA`: retain the form chosen by `TopPaneSlotMode.clockForm()`
  and compute one centred shared horizontal group in logical start-to-end order: clock, notifications, media.
  The one-pinned media contention case uses the existing compact card/strip forms; the three-pin case retains
  the mono clock and notification stack. Gaps shrink before child minimum widths, and content never overlaps.
- RTL/logical alignment comes from start/end coordinates even though the manifest currently disables RTL; tests
  keep the policy deterministic if that changes.
- One progress value interpolates current and FULL child bounds. Feed changes during motion recompute both sets
  atomically, cancel stale child animators and continue from the current progress; there is no second owner of
  clock/media/notification position.

The top slot stays at the pane's top. The status row continues riding the pane's lower edge. The space between
them is intentionally empty in A-2 and becomes A-3's widget grid/add affordance; A-2 does not ship a fake plus,
grid or settings screen.

## 12. Numbered implementation order

1. **Characterization first.** Pin current compact/expanded geometry, 260ms endpoint behavior, window-bar tap/
   scroll/edge overswipe, all four `TopPaneSlotMode` layouts, current Back source order, app drawer eligibility,
   accessory combined height and zero per-frame terminal resize behavior.
2. **A-1 pure contracts.** Add widget records/repository schema, add transaction, bind/configure policy,
   size-options policy and provider-reconcile policy with JUnit4 tests. No Android host yet.
3. **A-1 host/view.** Implement `LauncherAppWidgetHost`, safe host view and error tile. Add injected seams around
   platform calls so exception/cleanup behavior is deterministic in tests.
4. **A-1 controller and lifecycle.** Add capability check, stable host ID, start/stop listening, create-view,
   allocation/deletion, direct bind, consent/configuration launch, result routing and package/host callbacks.
   Add manifest permission only; add no provider receiver/metadata.
5. **A-1 persistence/recovery.** Wire commit-before-launch, process-death result recovery, stale pending expiry,
   two-phase deletion and provider uninstall/update reconciliation at `onStart` and the production package seam.
6. **A-1 size and isolation pass.** Wire committed size options/dedup and all safe-host exception boundaries;
   verify no widget snapshot/cache was added. Run focused tests, both full unit variants, then the A-1 device
   matrix before A-2 changes status geometry.
7. **A-2 pure contracts.** Add `TopStatusBarState`, `FullStatusBarGeometry`, `StatusBarGesturePolicy` and
   `TopPaneFullRowPolicy`; extend existing geometry tests with strict compact/expanded golden results.
8. **A-2 gesture integration.** Refactor `StatusBarSwipeLayout` to frozen DOWN/one-way claims, no child
   interception and non-consuming nested-scroll observation. Wire FULL listener and add the app-drawer FULL veto.
9. **A-2 layout integration.** Add FULL progress to `TopPaneWidgetSlot`/clock presentation and make the pure row
   policy the actual `onMeasure/onLayout` source. Do not change `TopPaneSlotMode` priority.
10. **A-2 controller.** Add one house-spring frame loop, current-height takeover, measured FULL target,
    resize bracket, reduced-motion/immediate lifecycle paths and Back/prior-state restoration.
11. **A-2 glass.** Reuse the existing glass views/status blur key and add the shared-frame aligned frost path,
    including top-pane ownership in cache recycle checks. Verify zero per-frame crop allocations.
12. **Integration/regression.** Pin full Back order and palette/drawer exclusivity, run the 1,256-test baseline
    plus new tests in both variants, read JUnit XML, build both variants, then run the device matrix and compare
    terminal resize, frame and memory traces to the compact/expanded control.

## 13. Unit and Robolectric tests

### 13.1 Pure JUnit4 — A-1

1. `LauncherWidgetRepositoryTest`: empty/v1 load, stable serialization, active/pending/tombstone round-trip,
   synchronous commit failure, duplicate ID/provider rejection, one pending transaction, and immutable snapshots.
2. `WidgetBindFlowPolicyTest`: allocate/direct-bind/consent/configure/ready paths; decline; foreign/stale result;
   missing/mismatched result ID; unavailable configure; cancel at every stage; cleanup exactly once.
3. `WidgetConfigurePolicyTest`: no component, mandatory component, optional+reconfigurable SDK-gated case,
   unknown flags and missing activity.
4. `WidgetSizeOptionsPolicyTest`: px-to-dp rounding, portrait-only min=max, portrait+landscape min/max, API-31
   list dedup/cap, host category, density change, degenerate sizes ignored, equality suppresses duplicate update.
5. `WidgetProviderReconcilePolicyTest`: keep matching active ID; update same component; absent component becomes
   tombstone/delete; inaccessible profile; resume deleting; pending bound/unbound/expired; ignore an ID not owned
   by the repository.

### 13.2 Robolectric — A-1

1. `LauncherWidgetHostLifecycleTest`: activity controller starts listening once after `onStart`, stops once on
   `onStop`, partial initialization is safe, recreation uses stable host ID and active IDs are not reallocated.
2. `LauncherWidgetConsentRoutingTest`: request codes 4714/4715 are distinct from 4711-4713; intent extras contain
   ID/provider/profile/options; RESULT_CANCELED deletes and produces no active record; unrelated results fall
   through to existing activity behavior.
3. `LauncherWidgetConfigureRoutingTest`: direct/consent bind reaches configure helper once; OK commits active;
   cancel/not-found/security failure deletes; process-style controller recreation reads the pending transaction.
4. `SafeLauncherAppWidgetHostViewTest`: injected RemoteViews-update, measure, layout, draw and touch
   `RuntimeException`s produce one local error tile and do not escape; provider refresh can replace it; error tile
   is accessible and bitmap-free; `OutOfMemoryError` is deliberately not swallowed.
5. `LauncherWidgetProviderRefreshIntegrationTest`: the production package-refresh method invokes widget
   reconciliation above the launcher/dock guard; host `onProviderChanged` and `onProvidersChanged` reach the same
   policy; update keeps ID while uninstall tombstones/deletes.
6. `LauncherWidgetOptionsIntegrationTest`: committed resize calls `updateAppWidgetOptions` once with four bounds;
   identical resize no-ops; no update is issued during synthetic intermediate resize frames.

### 13.3 Pure JUnit4 — A-2

1. `FullStatusBarGeometryTest`: Default/Rounded compact/expanded constants; FULL equals measured parent inner
   height; padding/top margin; short/zero parent; monotonic enter/exit; parent relayout; no overshoot outside bounds.
2. `StatusBarResizeGeometryTest` extended: every old expected result is identical; FULL keeps top slot visible and
   status row on lower edge; values remain finite at equal/invalid endpoints.
3. `StatusBarGesturePolicyTest`: window bar and every interactive child immediately child-owned; horizontal wins
   before timeout; long-press only from pending chrome; vertical/slop/multitouch/nested start cancel; neutral
   diagonal never double-claims; claim survives reentrant reset; FULL/overlay/tuning states are ineligible.
4. `TopPaneFullRowPolicyTest`: all four slot modes, 0/1/2/3 pins, narrow/wide widths, centred clock-only endpoint,
   shared-row ordering/gaps/no overlap, stable logical direction and interpolation endpoints.
5. `TopStatusBarStateTest`: boolean preference mapping, prior captured once, Back retarget, compact/expanded target
   chosen during in-flight takeover, repeated Back consumed, lifecycle immediate close.

### 13.4 Robolectric — A-2

1. `StatusBarSwipeLayoutTest` extended: shipped right/left requests on chrome remain; long-press enters from compact
   and expanded; tap does nothing; window chip click/scroll/overswipe is not intercepted; nested child scroll has
   zero parent consumption; callback-induced reentrant state reset cannot produce swipe+FULL.
2. `TopPaneWidgetSlotFullTest`: progress 0 matches current measured bounds; progress 1 centres clock; media/pinned
   share one aligned row under the existing mode; feed change mid-progress has one layout owner and no overlap.
3. `FullStatusBarControllerTest`: one frame callback and one house Spring; current-height takeover; measured target;
   reduce-motion snap; one begin/one finish resize bracket; no per-frame finish/update; parent relayout retarget;
   stop/palette takeover cleans callbacks and restores prior.
4. `FullStatusBarGlassTest`: same three glass child IDs before/after; status blur radius used in every state; cache
   gains no radius; aligned shared frost is not recycled while displayed; animation allocates no crop per frame.
5. `TermuxActivityBackOrderTest` extended: FULL source branch precedes palette, drawer, dock tuning and navigation;
   drawer's query/category/close hierarchy remains intact when FULL is absent; repeated exit Back cannot fall through.
6. `FullStatusBarGeometryIsolationTest`: FULL never calls `computeCombinedHeight`, accessory sync, toolbar-height
   or dock setters; it changes the existing host height and produces exactly one final pane/terminal resize.
7. `AppDrawerProductionArbitrationTest` extended: FULL is read by the actual dock DOWN snapshot and vetoes opening;
   after FULL exit the same gesture is eligible again. This prevents a pure but unwired policy regression.

Run focused tests first, then `./gradlew testDebugUnitTest` for the configured matrix and inspect every generated
JUnit XML file for failures/errors/skips rather than trusting only Gradle's exit code. The expected pre-change
baseline is 1,256 passing tests per the task brief.

## 14. What Robolectric cannot verify — mandatory device verification

Robolectric cannot faithfully verify these AppWidget contracts:

- whether an ordinary signed install actually lacks privileged `BIND_APPWIDGET` and receives the real system
  `ACTION_APPWIDGET_BIND` consent UI;
- user “Allow once”, “Always allow” and decline behavior across OEM system dialogs;
- actual AppWidget service ID allocation/retention across process kill, app update and reboot;
- cross-profile binding and `startAppWidgetConfigureActivityForResult()` behavior;
- real provider configure activities, including ones that return malformed/missing extras or never return;
- Binder-delivered RemoteViews updates, PendingIntent clicks, collection `RemoteViewsService` scrolling, partial
  updates, platform/OEM inflation differences and the RemoteViews bitmap transaction ceiling;
- provider process crash, package update/uninstall/reinstall callbacks and framework host recovery;
- whether `updateAppWidgetOptions()` reaches a provider's `onAppWidgetOptionsChanged()` with correct dp bounds;
- real blur/GPU behavior, wallpaper pre-blur cost, terminal PTY resize/SIGWINCH count, nested widget touch dispatch,
  system insets and performance on physical hardware.

Run this matrix on the dedicated physical Android device in both application variants:

1. Install as an ordinary non-system app. Add a simple no-config widget: verify direct bind returns false on a
   fresh install, system consent appears, Allow succeeds, decline leaves no ID/record/tile, and retry uses a new ID.
2. Add a mandatory-config widget; complete, cancel and force-stop during configuration. Relaunch and verify one
   active widget or complete ID cleanup—never an orphan/duplicate. Repeat with “always allow” to exercise direct bind.
3. Kill the launcher process, recreate the activity, reboot and upgrade-install the launcher. Existing IDs/views
   return without new consent or ID allocation; start/stop listening does not duplicate updates.
4. Add widgets with buttons/PendingIntents and a scrolling collection. Tap and scroll in FULL; no status swipe,
   long-press, drawer open or terminal touch fires. Exit and verify the terminal receives touch immediately.
5. Use a deliberately malformed test provider to throw during RemoteViews apply/layout/draw where feasible. The
   local error tile replaces only that widget and the launcher/terminal stays alive. Then update the provider to
   a valid layout and verify recovery.
6. Kill the provider process: launcher stays alive and retains last/empty content. Update the provider APK: ID is
   retained and host reinflates. Uninstall: record becomes provider-missing and ID is deallocated. Reinstall does
   not bypass consent; explicit re-add creates a new ID.
7. Resize a test widget and log `onAppWidgetOptionsChanged`: one callback after commit, exact min/max dp values,
   portrait/landscape size list on API 31+, no callback storm during the gesture.
8. Enter FULL from compact and expanded at rest and midway through the 260ms normal transition. Confirm no snap,
   correct prior return, top-centred/right-moving clock, aligned media/pinned row and status row at the moving bottom.
9. Gesture matrix on status chrome: tap, hold, horizontal then vertical drift, vertical then horizontal drift,
   neutral diagonal, second pointer, window-chip tap/scroll/edge overswipe, settings/media/notification action.
   Exactly one outcome per stream; window taps never disappear.
10. Back through FULL, palette, drawer query/category/drawer, dock tuning and navigation states. Confirm FULL is
    first when engaged and surfaces never stack; HOME/onStop restores prior immediately with no invisible touch layer.
11. Run Default and Rounded styles, wallpaper passthrough/live blur/no blur, min/max status radius, display scale,
    large font, rotation, gesture navigation and `animator_duration_scale 0`. The same glass view grows seamlessly;
    no stretched/late wallpaper crop or one-frame hard edge appears.
12. Record `gfxinfo framestats` for ten compact->FULL->prior cycles against compact<->expanded control. Trace/cache
    logs must show no new blur-radius entry and no per-frame crop allocation; `dumpsys meminfo` must plateau after
    the first cycle and after repeated widget updates.
13. Log terminal pane begin/finish and PTY resize events. Each FULL settle produces at most one final update/
    SIGWINCH, no frame produces one, the bottom of the terminal is retained after exit, and typing still works after
    ten cycles. Change the bottom accessory height while FULL and verify the pane follows the new measured bottom
    without clipping/dead space or issuing its own accessory sync.

## 15. Regression risks and containment

| Surface | Risk | Containment |
|---|---|---|
| Terminal | Growing the real host can drive the terminal briefly to zero/near-zero height, emit a resize every frame, lose the bottom row on return, or leave resize suppression open after cancel/stop. | Reuse the pane-controller bracket, finish once in `finally`/immediate teardown, clamp geometry, test zero-height tolerance, count PTY events, and verify bottom retention on device. |
| Accessory stack | Treating FULL as an overlay while still changing the band height would clip/deaden space; adding it to `computeCombinedHeight()` would double-count unrelated top geometry; a stale target after keyboard/dock layout would leave a gap. | Keep FULL as the existing measured top child, never enter bottom combined-height math, derive target from the live measured column, and observe parent relayout without initiating accessory sync. |
| Dock | A visible dock gesture could open the drawer over FULL; folder/context popup state could survive entry; a broad geometry reload could move or disable the three independent dock rows. | Dismiss popups at entry, add FULL to the real drawer DOWN eligibility, close FULL before palette takeover, and make no dock/accessory style or height calls. Repeat all three row toggles and dock launch/folder regressions. |
| App drawer | A dead FULL veto policy, wrong Back insertion or shared gesture enum could regress the two B-4 P1 fixes, query/category Back hierarchy, close arming or reentrant click latch. | Add one explicit listener eligibility field at `captureDrawerEligibility`, do not reuse/modify drawer claim transitions, pin production consultation and full source order, and run all drawer arbitration/back suites unchanged. |
| Status/window bar | A parent timeout can consume chip taps, long-press after a horizontal claim, or fire FULL plus swipe after reentrant reset. | Frozen hit region, child-owned window-bar snapshot, one-way policy, nested-scroll observation with zero consumption, and a dispatch-finally latch independent of layout reset. |
| Top-pane content | A parallel FULL layout policy could fight `TopPaneSlotMode`, snap the clock width, overlap three pins/media, or keep a child animator writing after the controller. | Keep contention derivation in `TopPaneWidgetSlot`, make one pure bounds policy, interpolate all children from one FULL progress, cancel stale child animators and test every mode/narrow width. |
| Blur/memory | A new FULL blur radius evicts the dock from the three-entry pre-blur LRU; changing host height could allocate a multi-megabyte crop every frame or recycle a shared frame still displayed. | Reuse status radius, keep LRU at three, display the cached full frame with screen alignment/clip, include top frost in shared ownership checks, and trace allocations/cache keys on device. |
| Widget IDs | Decline/config cancel/persistence failure can leak allocated IDs; process death can duplicate a pending add; `deleteHost()` can erase unrelated widgets. | One durable transaction, commit before launch, one pending add, two-phase per-ID deletion, provider/ID verification on every result, never call host-wide deletion in routine flow. |
| Provider lifecycle | Treating a quiet/crashed provider as uninstalled can destroy a valid ID; retaining an actually removed component can leak a dead host view; update can leave an error tile permanent. | Require current provider-info absence/mismatch for tombstone, use shared host/package reconciliation, keep IDs across same-component update, recreate safe view and clear failure only on real refresh. |
| Crash guard | Catching `Throwable` can hide fatal VM/memory failure; mutating children during draw can crash again; retrying each frame can ANR-loop. | Catch `RuntimeException` only, post draw/touch replacement, local static error tile, retry only on a new provider update/recreation, and document uncatchable classes explicitly. |
| Widget memory/touch | Provider bitmaps bypass the icon LRU; a duplicated/snapshotted host can exceed heap; parent edit/entry gestures can steal collection scroll or fire a widget action plus launcher action. | No snapshots/second cache, one view per ID, visible attachment policy in A-3, child-owned widget DOWN, non-consuming nested scroll, and launcher handles outside the RemoteViews rectangle. |
| Lifecycle/result plumbing | `onStart`/`onStop` imbalance can duplicate callbacks; request-code collision or lost in-memory pending state can commit/delete the wrong ID. | Stable 4714/4715 codes, durable transaction lookup, idempotent listening, explicit result ownership, recreation/process tests and real system-flow device tests. |

## Open questions for the project lead

None. The two slices have enough locked constraints to choose the host ID, consent/configuration lifecycle,
failure boundaries, provider-removal behavior, real-layout FULL geometry, terminal resize policy, gesture ownership,
Back order and blur-cache strategy without silently guessing a product-facing option.
