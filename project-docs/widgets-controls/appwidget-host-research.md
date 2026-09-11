# AppWidget host research — picker performance, layout, theming, configure flow

Primary-source research for the widget picker and host implementation. Every claim cites the
source that owns it: `developer.android.com` for behavior/contract, `android.googlesource.com`
(AOSP `frameworks/base`, tag `refs/heads/master`, fetched 2026-09-11) for mechanism, and AOSP
`packages/apps/Launcher3` (`refs/heads/main`) for the reference host implementation.

## 1. Widget picker list loading performance

**`getInstalledProviders()` / `getInstalledProvidersForProfile()`** are a single binder round trip
to `system_server`, which returns a `ParceledListSlice<AppWidgetProviderInfo>` for every provider
matching the category filter; the client then calls `info.updateDimensions(mDisplayMetrics)` on
each entry to convert the complex min/max sizes to dp
([`AppWidgetManager.java:1094-1113`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetManager.java)).
The list itself is comparatively cheap — the expense is in what a picker does with each
`AppWidgetProviderInfo` afterward:

- **`loadLabel(PackageManager)`** just forwards to `ActivityInfo.loadLabel(packageManager)` on the
  underlying provider `ActivityInfo`
  ([`AppWidgetProviderInfo.java:438-445`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetProviderInfo.java)),
  which resolves a manifest string resource through `PackageItemInfo` — a resource lookup against
  the *other app's* resources, not a static field read.
- **`loadIcon(Context, int)`** and **`loadPreviewImage(Context, int)`** both funnel into a private
  `loadDrawable()` helper:
  ```java
  private Drawable loadDrawable(Context context, int density, int resourceId, boolean loadDefaultIcon) {
      Resources resources = context.getPackageManager().getResourcesForApplication(
              providerInfo.applicationInfo);
      if (ResourceId.isValid(resourceId)) {
          return resources.getDrawableForDensity(resourceId, density, null);
      }
      ...
  }
  ```
  ([`AppWidgetProviderInfo.java:602-615`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetProviderInfo.java)).
  `PackageManager.getResourcesForApplication()` is the expensive step: for every provider it makes
  the framework construct (or fetch from its internal `LoadedApk` cache) an `AssetManager` /
  `Resources` object for a *different app's* APK — opening `resources.arsc`, not something the
  caller controls the caching of — and then `getDrawableForDensity` decodes the actual bitmap/XML
  drawable. Doing this synchronously for every provider in a picker with hundreds of installed
  widgets, on the UI thread, is the actual bottleneck; nothing here is memoized by the platform
  across picker sessions.
- **`previewLayout` (API 31)** lets a provider give a `@LayoutRes` instead of a flat
  `previewImage` drawable: "Unlike previewImage, previewLayout can better showcase AppWidget in
  different locales, system themes, display sizes & density etc. If supplied, this will take
  precedence over the previewImage on supported widget hosts"
  ([`AppWidgetProviderInfo.java:316-327`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetProviderInfo.java);
  [developer guide](https://developer.android.com/develop/ui/views/appwidgets/previews)). A host
  that supports it renders the provider's real layout (via `RemoteViews(pkg, previewLayout)`)
  instead of decoding a static drawable — still a resource-context inflate, but reuses the same
  inflate path as a real widget instead of a bespoke bitmap decode.
- **Generated previews (`generatedPreviewCategories`, API 35 / `VANILLA_ICE_CREAM`)**: a provider
  proactively calls
  `AppWidgetManager.setWidgetPreview(ComponentName, categoryFlags, RemoteViews)`, which is
  rate-limited ("~2 calls per hour" per the guide) and stores a ready-made `RemoteViews` server-side
  ([`AppWidgetManager.java:1508-1536`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetManager.java);
  [developer guide](https://developer.android.com/develop/ui/views/appwidgets/previews)). A host
  checks `AppWidgetProviderInfo.generatedPreviewCategories` (a `@FlaggedApi(FLAG_GENERATED_PREVIEWS)`
  bit field, "corresponds to the previews set by this provider with `setWidgetPreview`"
  — [`AppWidgetProviderInfo.java:378-392`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetProviderInfo.java))
  and if the category bit is set, calls
  `AppWidgetManager.getWidgetPreview(provider, profile, category)` to fetch the stored `RemoteViews`
  instead of loading/decoding anything itself
  ([`AppWidgetManager.java:1552-1568`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetManager.java)).
  This is the cheapest tier: one more binder call, no local decode, no APK resource load. Precedence
  is: generated preview > `previewLayout` > `previewImage` > bare icon.

**What AOSP Launcher3 does about it** (`packages/apps/Launcher3`,
`src/com/android/launcher3/widget/DatabaseWidgetPreviewLoader.java` — despite the historical name,
the class doc now reads *"Utility class to generate widget previews. Note that it no longer uses
database, all previews are freshly generated"*
([source](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/DatabaseWidgetPreviewLoader.java))):

- `loadPreview(WidgetItem, Size, Consumer<WidgetPreviewInfo>)` is documented as "Generates the
  widget preview on `Executors#UI_HELPER_EXECUTOR`" and posts the callback back onto
  `MAIN_EXECUTOR` — i.e. every preview is produced off the UI thread and only the result hop is
  posted to main
  ([`DatabaseWidgetPreviewLoader.java:75-90`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/DatabaseWidgetPreviewLoader.java)).
- `generatePreviewInfoBg()` is annotated "This method must be called on a background thread" and
  implements exactly the precedence above: generated preview (`WIDGET_CATEGORY_HOME_SCREEN`, gated
  behind `BuildCompat.isAtLeastV() && Flags.enableGeneratedPreviews()`) → `previewLayout` (wrapped
  through `LauncherAppWidgetProviderInfo.fromProviderInfo`, forcing `initialLayout` to the preview
  layout as "a hack to force the initial layout to be the preview layout since there is no API for
  rendering a preview layout for work profile apps yet") → bitmap fallback via
  `generateWidgetPreview()`/`generateShortcutPreview()`
  ([`DatabaseWidgetPreviewLoader.java:99-131`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/DatabaseWidgetPreviewLoader.java)).
- The bitmap fallback path (`generateWidgetPreview`) explicitly wraps `info.loadPreviewImage()` in a
  `try/catch (OutOfMemoryError)` and logs+no-ops rather than crashing, because decoding an
  arbitrary provider's preview drawable is untrusted, unbounded work
  ([`DatabaseWidgetPreviewLoader.java:145-158`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/DatabaseWidgetPreviewLoader.java)).
- The picker list itself (`WidgetCell`, `WidgetsListAdapter`) issues a `loadPreview()` request per
  visible cell and cancels it via `CancellableTask` when the cell is recycled/scrolled off —
  i.e. work is scoped to visible RecyclerView rows, not the whole provider list.

## 2. Widget picker layout patterns (Launcher3)

Package: `packages/apps/Launcher3/src/com/android/launcher3/widget/picker/`
([directory listing](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/picker/)).

- **`WidgetsFullSheet`** (`extends BaseWidgetSheet`) is the picker's bottom-sheet/dialog shell —
  class doc: *"Popup for showing the full list of available widgets"*
  ([`WidgetsFullSheet.java:94`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/picker/WidgetsFullSheet.java)).
  It owns up to three parallel adapter/RecyclerView pairs — `PRIMARY`, `WORK`, and `SEARCH` — via
  an inner `AdapterHolder` class, each with its own `WidgetsListAdapter` +
  `WidgetsRecyclerView`
  ([`WidgetsFullSheet.java:177-179, 1082-1104`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/picker/WidgetsFullSheet.java)).
- **One-pane vs. two-pane** is a static, device-profile-driven decision:
  ```java
  private static int getWidgetSheetId(BaseActivity activity) {
      boolean isTwoPane = (activity.getDeviceProfile().isTablet
              && (activity.getDeviceProfile().isLandscape || enableCategorizedWidgetSuggestions())
              && !activity.getDeviceProfile().isTwoPanels)
              || (activity.getDeviceProfile().isTwoPanels && enableUnfoldedTwoPanePicker());
      return isTwoPane ? R.layout.widgets_two_pane_sheet : R.layout.widgets_full_sheet;
  }
  ```
  ([`WidgetsFullSheet.java:787-796`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/picker/WidgetsFullSheet.java)).
  The two-pane variant is `WidgetsTwoPaneSheet`, a separate subclass in the same package
  (`WidgetsTwoPaneSheet.java`) that adds a left-hand app list next to the right-hand widget grid;
  `onDeviceProfileChanged()` tears down and recreates the whole sheet (switching layout resource)
  if the pane-count decision would change, e.g. on fold/unfold or rotation
  ([`WidgetsFullSheet.java:930-960`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/picker/WidgetsFullSheet.java)).
- **Grouped-by-app headers + per-app rows**: `WidgetsListAdapter extends RecyclerView.Adapter` and
  "supports view binding of subclasses of `WidgetsListBaseEntry`. There are 2 subclasses:
  `WidgetsListHeader` & `WidgetsListContentEntry`"
  ([`WidgetsListAdapter.java`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/picker/WidgetsListAdapter.java)).
  Entries are sorted alphabetically by package/app title (current-user packages first) via
  `WidgetListBaseRowEntryComparator`; tapping a header (`onHeaderClicked`) expands/collapses that
  app's content row, which is where the horizontal table of widget-preview cells
  (`WidgetsListTableView` / `WidgetsRecommendationTableLayout`) lives — i.e. the "carousel per app"
  is the expanded content entry under its header, not a separate always-visible row.
- **Search**: `packages/.../widget/picker/search/` holds `WidgetsSearchBar` (an interface/base view
  contract), `LauncherWidgetsSearchBar` (Launcher3's concrete implementation),
  `SearchModeListener`, `WidgetsSearchBarController`, and `SimpleWidgetsSearchAlgorithm`
  ([directory listing](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/picker/search/)).
  `SimpleWidgetsSearchAlgorithm implements SearchAlgorithm<WidgetsListBaseEntry>` and is documented
  as "posts a task to query on the main thread" — it is a synchronous, main-thread string match
  (`StringMatcherUtility.matches`) over the already-loaded in-memory widget list, run per header
  against the package title and per-item against each widget's label, then re-materialized into
  fresh `WidgetsListHeaderEntry`/`WidgetsListContentEntry` pairs for the `SEARCH` adapter
  ([`SimpleWidgetsSearchAlgorithm.java`](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/widget/picker/search/SimpleWidgetsSearchAlgorithm.java)).
  `WidgetsFullSheet.setViewVisibilityBasedOnSearch()` toggles between the recommendations/primary
  RecyclerView and the dedicated `SEARCH` adapter's RecyclerView rather than filtering the primary
  list in place.

## 3. Widget light/dark theme

**Provider-side tools** (used inside the widget's own app, no host cooperation needed beyond
inflating current RemoteViews):

- Night-qualified resources (`res/values-night/`, `res/drawable-night/`) work automatically because
  the RemoteViews are inflated through a resources context whose `Configuration.uiMode` is checked
  like any other resource qualifier (mechanism below).
- **`RemoteViews.setColorAttr(int viewId, String methodName, @AttrRes int colorAttribute)`** (API
  31) resolves a *theme attribute* (e.g. `android:textColorPrimary`) "at the time the RemoteViews
  is (re-)applied," so it tracks whatever theme the host applies at inflate time
  ([`RemoteViews.java:7391-7404`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/widget/RemoteViews.java)).
- **`RemoteViews.setColorInt(int viewId, String methodName, @ColorInt int notNight, @ColorInt int night)`**
  (API 31) lets the provider hard-code both variants explicitly and let the framework pick per
  `Configuration#UI_MODE_NIGHT_NO` / `UI_MODE_NIGHT_YES`
  ([`RemoteViews.java:7407-7419`, doc comment above](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/widget/RemoteViews.java)),
  implemented as a `NightModeReflectionAction`.
- **`RemoteViews.setFloatDimen(...)`** (API 31, resource- and attr-based overloads) resolves a
  dimension "from the resources at the time the RemoteViews is (re-)applied," same re-apply-time
  binding as `setColorAttr`
  ([`RemoteViews.java:7532-7580`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/widget/RemoteViews.java)).
- **`RemoteViews.setLightBackgroundLayoutId(@LayoutRes int layoutId)`** (API 31) gives the host an
  *alternate whole layout* "used by the host when the widgets displayed on a light-background where
  foreground elements and text can safely draw using a dark color without any additional background
  protection"
  ([`RemoteViews.java:7826-7832`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/widget/RemoteViews.java)).
  This is opt-in per host: `AppWidgetHostView.setOnLightBackground(boolean)` flips
  `mOnLightBackground`, and `applyRemoteViews()` calls `rvToApply.getDarkTextViews()` before
  inflating when that flag is set
  ([`AppWidgetHostView.java:515, 576-579`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHostView.java)) —
  i.e. this one *is* a host responsibility (the host decides whether its chrome counts as a "light
  background" and must call `setOnLightBackground`).

**The host mechanism — this is the part that actually decides day/night, and it is host
`Context`-driven, not provider-driven.** When `AppWidgetHostView` re-applies a `RemoteViews` it
calls:

```java
content = rvToApply.apply(mContext, this, mInteractionHandler, mCurrentSize, mColorResources);
```

passing **its own `mContext`** (the `Context` the host passed to the `AppWidgetHostView`
constructor / `AppWidgetHost.onCreateView`) as the resources context
([`AppWidgetHostView.java:601-604`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHostView.java)).
Inside `RemoteViews.inflateViewInternal()`, that context is transformed for resource resolution by:

```java
private Context getContextForResourcesEnsuringCorrectCachedApkPaths(Context context) {
    if (mApplication != null) {
        if (context.getUserId() == UserHandle.getUserId(mApplication.uid)
                && context.getPackageName().equals(mApplication.packageName)) {
            return context;
        }
        LoadedApk.checkAndUpdateApkPaths(mApplication);
        Context applicationContext = context.createApplicationContext(mApplication,
                Context.CONTEXT_RESTRICTED);
        // Get the correct apk paths while maintaining the current context's configuration.
        return applicationContext.createConfigurationContext(
                context.getResources().getConfiguration());
    }
    return context;
}
```
([`RemoteViews.java:8475-8492`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/widget/RemoteViews.java)).

This **confirms the hinted mechanism exactly**: the resulting resources context loads the
*provider's* package resources (`createApplicationContext(mApplication, ...)`) but is then wrapped
with `createConfigurationContext(context.getResources().getConfiguration())` using the
**caller-supplied `context`'s Configuration** — i.e. the host's `Configuration`, including
`uiMode`/night-mode bit, not whatever the provider process's own current Configuration is. The
comment even says so: *"Get the correct apk paths while maintaining the current context's
configuration."* `AppWidgetHostView`'s own equivalent helper,
`getRemoteContextEnsuringCorrectCachedApkPath()`, does the same thing one layer up (calls
`mContext.createApplicationContext(...)`, which inherits `mContext`'s Configuration by construction
inside `ContextImpl`) before that context is ever handed to `RemoteViews.apply`
([`AppWidgetHostView.java:754-772`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHostView.java)).

**What this means for a host, concretely:**

- **The host's `Context`'s `Configuration.uiMode` is authoritative for the widget's night-mode
  resource resolution.** If the `Context` passed to `AppWidgetHost.createView()` /
  `new AppWidgetHostView(context)` has a *stale* or *wrong* `uiMode` (e.g. a long-lived
  `Application`/service context captured before a runtime dark-mode toggle, or a
  `createConfigurationContext()` snapshot taken once at process start and never refreshed), every
  widget hosted through it will keep resolving `values-night/` resources against that stale mode
  until a `Context` with the corrected Configuration is used again for `apply()`/`reapply()`.
- `AppWidgetHostView` has **no `onConfigurationChanged` override of its own** in AOSP — it does not
  automatically re-inflate on a system dark-mode change. Re-resolution only happens the next time
  `applyRemoteViews()`/`reapply()` runs (normal widget update, `updateAppWidgetOptions`-triggered
  update, or the containing `Activity` being recreated with a fresh `Context` on the configuration
  change). A host that overrides `configChanges` and never recreates/reapplies will show a stale
  theme after a runtime day/night toggle.
- `AppWidgetHostView.updateAppWidgetOptions(Bundle)` only forwards to
  `AppWidgetManager.getInstance(mContext).updateAppWidgetOptions(mAppWidgetId, options)`
  ([`AppWidgetHostView.java:479-482`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHostView.java))
  — it communicates *sizing/host-category* options to the provider, it is not a theme/Configuration
  channel by itself; the theme channel is purely "what `Context` was passed to `apply()`."
  `setExecutor(Executor)` only controls whether inflation runs via `inflateAsync()` vs.
  synchronously — it does not change which context's Configuration is used
  ([`AppWidgetHostView.java:500`, `555-621`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHostView.java)).
- Practical rule for this launcher: always construct/refresh `AppWidgetHostView`'s `Context` (or
  call `reapply`) from a `Context` whose `resources.getConfiguration().uiMode` matches the
  launcher's *current, live* dark-mode state — not a cached one — any time that state can change
  without the hosting `Activity`/window being torn down and recreated.

## 4. Widget configuration activity

**Flow.** `AppWidgetProviderInfo.configure` is a `ComponentName` — "The activity to launch that
will configure the AppWidget... The package name always corresponds to the package containing the
AppWidget provider"
([`AppWidgetProviderInfo.java:268-274`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetProviderInfo.java)).
When non-null, the host must launch it *after* the widget id is bound and *before* treating the
widget as placed, unless `WIDGET_FEATURE_CONFIGURATION_OPTIONAL` is set (below).

- **`AppWidgetHost.startAppWidgetConfigureActivityForResult(Activity, int appWidgetId,
  int intentFlags, int requestCode, Bundle options)`**: *"Starts an app widget provider configure
  activity for result on behalf of the caller. Use this method if the provider is in another
  profile as you are not allowed to start an activity in another profile... Note that the provided
  app widget has to be bound for this method to work"*
  ([`AppWidgetHost.java:334-363`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHost.java)).
  Internally it calls `getIntentSenderForConfigureActivity()` (a binder call into
  `system_server`'s `createAppWidgetConfigIntentSender`) and then
  `activity.startIntentSenderForResult(intentSender, ...)`
  ([`AppWidgetHost.java:307-365`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHost.java)).
  This is why it is preferred over building a raw `Intent(ACTION_APPWIDGET_CONFIGURE)` yourself:
  the system constructs and signs the `IntentSender`, so it works across the profile boundary
  (managed/work profile providers, where the host cannot directly `startActivity` into another
  user) and the system validates that the target really is the bound widget's configure activity —
  a manually built `Intent` has no such guarantee and will fail cross-profile.
- **Result handling**: `ACTION_APPWIDGET_CONFIGURE`'s own doc says it plainly — *"If you return
  `RESULT_OK`... the AppWidget will be added, and you will receive an `ACTION_APPWIDGET_UPDATE`
  broadcast... If you return `RESULT_CANCELED`, the host will cancel the add and not display this
  AppWidget, and you will receive a `ACTION_APPWIDGET_DELETED` broadcast"*
  ([`AppWidgetManager.java:181-204`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetManager.java)).
  On the host's `onActivityResult`, `RESULT_CANCELED` (or any non-`RESULT_OK`) for a *newly-bound,
  not-yet-placed* widget id means the host itself must call
  `AppWidgetHost.deleteAppWidgetId(appWidgetId)`
  ([`AppWidgetHost.java:412-421`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHost.java))
  to release the id rather than leaving an orphaned binding.

- **`widgetFeatures` flags** (`AppWidgetProviderInfo`, API 31):
  - `WIDGET_FEATURE_RECONFIGURABLE = 1` — *"The widget can be reconfigured anytime after it is
    bound by starting the `configure` activity"*
    ([`AppWidgetProviderInfo.java:117-123`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetProviderInfo.java)).
    This is the flag that tells the host it is safe to offer a "reconfigure"/"edit" action on an
    *already-placed* widget: to reconfigure, the host calls
    `startAppWidgetConfigureActivityForResult` again with the **existing** `appWidgetId` (not a
    freshly-allocated one) — the same call used for initial setup, just invoked later from a menu
    action instead of from the add-widget flow. If the flag is absent, the host should not surface
    a reconfigure entry point, since the provider hasn't declared its configure activity supports
    being re-entered against a live id.
  - `WIDGET_FEATURE_HIDE_FROM_PICKER = 2` — *"The widget is added directly by the app, and the host
    may hide this widget when providing the user with the list of available widgets to choose
    from"*
    ([`AppWidgetProviderInfo.java:129-136`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetProviderInfo.java)),
    cross-referenced to `AppWidgetManager.requestPinAppWidget` — these providers expect to be
    placed via the pin-widget flow initiated by their own app, not chosen from the picker list, so
    a picker implementation should filter them out of `getInstalledProviders()` results by default.
  - `WIDGET_FEATURE_CONFIGURATION_OPTIONAL = 4` — *"The widget provides a default configuration.
    The host may choose not to launch the provided configuration activity"*
    ([`AppWidgetProviderInfo.java:138-144`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetProviderInfo.java)).
    Even when `configure` is non-null, a host may skip launching it immediately on add (placing the
    widget with the provider's default state) and instead offer "configure" later as an optional,
    user-initiated action — again via `startAppWidgetConfigureActivityForResult` with the existing
    id.

- **`bindAppWidgetIdIfAllowed` / `ACTION_APPWIDGET_BIND` ordering relative to configure.** Order is:
  allocate id → attempt bind → (if bind denied) request bind permission → configure:
  1. `AppWidgetHost.allocateAppWidgetId()` gets an id from `system_server`
     ([`AppWidgetHost.java:290-305`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHost.java)).
  2. `AppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)` (or the options-bundle
     overload) attempts to bind directly: *"You need the BIND_APPWIDGET permission or the user must
     have enabled binding widgets always for your component. Should be used by apps that host
     widgets; if this method returns false, call `ACTION_APPWIDGET_BIND` to request permission to
     bind"*
     ([`AppWidgetManager.java:1202-1230`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetManager.java)).
     Returns `true`/`false` synchronously — no activity involved when it succeeds.
  3. If it returns `false`, the host builds `Intent(ACTION_APPWIDGET_BIND)` (extras
     `EXTRA_APPWIDGET_ID`, `EXTRA_APPWIDGET_PROVIDER`) and calls it with
     `startActivityForResult`; the system shows its own bind-permission grant UI. Per the constant's
     doc: *"When you receive the result from the AppWidget bind activity, if the resultCode is
     `RESULT_OK`, the AppWidget has been bound. You should then check the `AppWidgetProviderInfo`
     for the returned AppWidget, and if it has one, launch its configuration activity. If
     `RESULT_CANCELED` is returned, you should delete the appWidgetId"*
     ([`AppWidgetManager.java:160-180`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetManager.java)).
  4. Only **after** the id is bound (by either path) does the host check
     `AppWidgetProviderInfo.configure != null` and call
     `startAppWidgetConfigureActivityForResult` — the doc for that method is explicit that *"the
     provided app widget has to be bound for this method to work"*
     ([`AppWidgetHost.java:340-341`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/appwidget/AppWidgetHost.java)).
     Configure always comes last, and a `RESULT_CANCELED` at *either* the bind step or the configure
     step means the host deletes the same `appWidgetId` via `AppWidgetHost.deleteAppWidgetId()`.
