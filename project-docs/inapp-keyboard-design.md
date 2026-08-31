# In-app Unexpected-Keyboard design

Status: delivered. This is the design record for the shipped `:inapp-keyboard` module and its
launcher-side host — the decisions and their reasons, not a proposal. Local deviations from the
vendored upstream snapshot are tracked in
[`../inapp-keyboard/UPSTREAM.md`](../inapp-keyboard/UPSTREAM.md), which is authoritative for what
the module's code actually differs on. A study of what could still be added is
[`plans/keyboard-mis-input-correction.md`](plans/keyboard-mis-input-correction.md).

Source baseline reviewed:

- `termux-launcher` at `787cfbcf3b6b22d26fcb49c92bbb4bb8465c6383`.
- `unexpected-keyboard` at `38836e440d8ca779d572b52601c6b2ad10f3bb7f`.
- Source citations below are workspace-relative paths in the form `file:start-end`.

## 1. Decision summary

Create an Android library module named `:inapp-keyboard`. Preserve the upstream Java package `juloo.keyboard2` inside that module, but expose only a small embedded-view API. Put Termux-specific lifecycle, preferences, Material palette construction, layout-file loading, and terminal event dispatch in `:app`, under `com.termux.app.terminal.inappkeyboard`.

The library is a source snapshot, not an IME and not a dependency on the Unexpected-Keyboard APK. It retains the XML parser, nine-position key model, gesture/pointer engine, modifier/compose engine, renderer, and icon font. It excludes the `InputMethodService`, Android `InputConnection` handler, settings application, dictionaries/suggestions and their native `cdict` dependency, emoji/clipboard/voice panes, and fold tracking. Upstream's current build combines Java and generated sources with native dictionary code, and creates generated layout/font/compose artifacts; those are inappropriate runtime or build dependencies for this app (`unexpected-keyboard/build.gradle.kts:8-13`, `unexpected-keyboard/build.gradle.kts:40-44`, `unexpected-keyboard/build.gradle.kts:106-117`, `unexpected-keyboard/build.gradle.kts:125-131`, `unexpected-keyboard/build.gradle.kts:153-165`).

`TermuxInAppKeyboard` is the controller and single owner of enabled/visible state. It inserts the keyboard at the bottom of `accessory_stack_container`, below the existing toolbar. `TerminalKeyEventHandler` implements the trimmed `Config.IKeyEventHandler` and translates already-modified Unexpected-Keyboard values to `TerminalView`/`TerminalSession` operations. When the preference is enabled, every terminal path that would show or toggle the system IME delegates to this controller and keeps the system IME suppressed.

The custom layout convention is:

```text
~/.termux/keyboard/layout.xml
```

If the file is absent or invalid, the controller uses the bundled `latn_qwerty_us` layout. Termux already defines `~/.termux` as `TERMUX_DATA_HOME_DIR_PATH`, rooted under its `$HOME` (`termux-launcher/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java:945-965`, `termux-launcher/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java:978-987`).

## 2. Goals, non-goals, and invariants

### Goals

1. Render a keyboard as an ordinary child view of `TermuxActivity`, so leaving the launcher for another app cannot remove the keyboard state from the activity. `TermuxActivity` is the HOME/LAUNCHER activity and uses `singleTask`, making activity-owned visible state the right persistence unit for an app launch and return (`termux-launcher/app/src/main/AndroidManifest.xml:105-132`).
2. Preserve Unexpected-Keyboard's center plus eight directional key values. `KeyboardData` parses `c`, `nw`, `ne`, `sw`, `se`, `w`, `e`, `n`, and `s` into slots 0 through 8, while `Pointers` selects directions and recognizes gesture state (`unexpected-keyboard/srcs/juloo.keyboard2/KeyboardData.java:399-418`, `unexpected-keyboard/srcs/juloo.keyboard2/KeyboardData.java:485-500`, `unexpected-keyboard/srcs/juloo.keyboard2/Pointers.java:218-331`).
3. Preserve XML layout customization through `KeyboardData.load_string_exn(String)` and bundled layouts through `KeyboardData.load(Resources, int)` (`unexpected-keyboard/srcs/juloo.keyboard2/KeyboardData.java:178-232`).
4. Preserve modifier latching/locking, compose, Hangul, macros, repeat, sliders, haptics, and circle/round-trip gesture behavior where those concepts have a terminal meaning. The pointer engine owns latched and locked fake pointers and long-press/repeat behavior (`unexpected-keyboard/srcs/juloo.keyboard2/Pointers.java:86-134`, `unexpected-keyboard/srcs/juloo.keyboard2/Pointers.java:394-461`); `Gesture` recognizes swipe, round trip, circle, and anticircle (`unexpected-keyboard/srcs/juloo.keyboard2/Gesture.java:17-95`).
5. Resize the terminal deterministically when keyboard visibility or measured height changes.
6. Resolve colors from the app's active Material role colors and support auto/light/dark/black keyboard variants. The app already resolves Material attributes with `MaterialColors.getColor` when constructing terminal schemes (`termux-launcher/app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java:35-52`, `termux-launcher/app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java:147-150`).
7. Leave a clean, inactive integration seam for a future LLM suggestion bar without porting Unexpected-Keyboard dictionaries or suggestion UI.

### Non-goals for the first implementation

- Registering another Android IME, creating an `InputMethodService`, or talking through an `InputConnection`.
- Porting Unexpected-Keyboard settings screens, layout editor UI, autocapitalisation, dictionary/suggestion code, emoji pane, clipboard history, voice IME switching, or direct-boot preferences.
- Making the in-app keyboard available to other Android apps.
- Providing a bundled layout picker in the first UI. The static layout catalog may be packaged so a picker can be added without changing the parser.
- Making selection-oriented Android editor commands emulate a full editable text field. A terminal screen and a shell line editor do not expose Android's editable-buffer contract.
- Implementing the LLM backend or issuing any network request.

### Invariants

- The library has no dependency on `:app`, `:terminal-view`, `InputMethodService`, `InputConnection`, AndroidX Window, or native code.
- The app, not a library singleton, owns preference and lifecycle state.
- No file I/O occurs in `Keyboard2View.onDraw`, touch callbacks, or terminal write callbacks.
- A hidden/detached keyboard has no active pointers, latched modifiers, queued repeats, or queued macro tasks.
- Enabling in-app mode suppresses the system IME for terminal focus; disabling it restores the existing system-IME behavior.
- Only `TerminalKeyEventHandler` writes keyboard data to a terminal. The renderer/gesture module never imports Termux classes.

## 3. Proposed architecture

```text
TermuxActivity
  |
  +-- TermuxInAppKeyboard (enabled/visible state, lifecycle, layout loading)
  |     |
  |     +-- InAppKeyboardPaletteFactory --> Theme.Palette
  |     +-- TerminalKeyEventHandler ------> TerminalView / TerminalSession
  |     +-- optional future SuggestionSource/SuggestionBarHost
  |     |
  |     `-- juloo.keyboard2.Keyboard2View  (:inapp-keyboard)
  |             |
  |             +-- KeyboardData / LayoutModifier
  |             +-- Pointers / Gesture / KeyModifier / ComposeKey
  |             `-- Config + Theme (injected, no app dependencies)
  |
  `-- accessory_stack_container
          +-- existing apps bar and terminal toolbar
          `-- inapp_keyboard_container (bottom-most)
```

The boundary is deliberately event-oriented:

```java
public final class EmbeddedKeyboardConfig {
    public final Config.IKeyEventHandler handler;
    // Gesture, sizing, repeat, haptic and rendering knobs only.
}

public interface EmbeddedKeyboardView {
    void setKeyboard(KeyboardData data);
    void setPalette(Theme.Palette palette);
    void resetInputState();
}
```

The concrete view may remain named `Keyboard2View` for upstream diffability. The app should only construct it through a small `EmbeddedKeyboardFactory` or the explicit `(Context, Config, Theme.Palette)` constructor, so no app code reaches mutable internals.

## 4. Module strategy

### 4.1 Recommendation: a new `:inapp-keyboard` library

The current build contains five modules and no keyboard-core boundary (`termux-launcher/settings.gradle:1`). The target app compiles with SDK 36, min SDK 26, Java 11, and AGP 8.13.2 (`termux-launcher/gradle.properties:26-29`, `termux-launcher/app/build.gradle:15-31`, `termux-launcher/build.gradle:1-9`). Unexpected-Keyboard is pure Java at this layer and currently has min SDK 21/compile SDK 36 (`unexpected-keyboard/build.gradle.kts:4-24`). A library can therefore use the target's SDK and Java settings without compatibility shims.

Suggested `inapp-keyboard/build.gradle`:

```groovy
plugins {
    id 'com.android.library'
}

android {
    namespace 'juloo.keyboard2'
    compileSdk project.properties.compileSdkVersion.toInteger()

    defaultConfig {
        minSdk project.properties.minSdkVersion.toInteger()
        consumerProguardFiles 'consumer-rules.pro'
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }

    testOptions.unitTests.includeAndroidResources = true
}

dependencies {
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.robolectric:robolectric:4.13'
}
```

Add `include ':inapp-keyboard'` in `settings.gradle` and `implementation project(':inapp-keyboard')` in `app/build.gradle`. The app already consumes local modules with `implementation project(...)`, including `:terminal-view` and `:termux-shared`, so this follows the existing structure (`termux-launcher/app/build.gradle:169-170`). The test versions above match the app's current JUnit/Robolectric versions (`termux-launcher/app/build.gradle:175-178`).

Why not fold the code into `:app`:

- A module enforces the no-Termux/no-IME dependency boundary at compile time.
- Parser/gesture/render tests can run without compiling the large app.
- An upstream refresh becomes a bounded snapshot/diff operation.
- Resource names and the `juloo.keyboard2.R` namespace remain isolated from Termux resources.

### 4.2 Package choice

Keep `package juloo.keyboard2` for copied/adapted upstream classes. This minimizes mechanical changes to imports, package-private calls, generated `ComposeKeyData`, XML view names, and future upstream diffs. Use `com.termux.app.terminal.inappkeyboard` only for Termux integration classes.

Changing the library package to `com.termux.inappkeyboard` would make ownership more obvious and eliminate any theoretical class collision if an upstream AAR were later added, but it would rewrite every source declaration, generated source, `R` reference, and fully qualified XML class name. Since this design does not depend on an upstream AAR, that cost outweighs the benefit. Record the upstream commit in `inapp-keyboard/UPSTREAM.md`, and never introduce a second `juloo.keyboard2` binary dependency.

### 4.3 Static source and resource snapshot

Do not copy upstream's generator tasks into the target build. Upstream generates `special_font.ttf`, `layouts.xml`, `ComposeKeyData.java`, and copied XML layouts at build time (`unexpected-keyboard/build.gradle.kts:106-117`, `unexpected-keyboard/build.gradle.kts:125-165`, `unexpected-keyboard/build.gradle.kts:178-191`). Instead, vendor reviewed outputs as ordinary source/resources:

| Upstream input/output | Destination | Decision |
|---|---|---|
| `srcs/juloo.keyboard2/*.java` selected below | `inapp-keyboard/src/main/java/juloo/keyboard2/` | Copy and adapt. Copy the generated `ComposeKeyData.java` snapshot, not its generator. |
| `srcs/layouts/*.xml` | `inapp-keyboard/src/main/res/xml/` | Copy the complete static layout snapshot. This preserves identifiers for later layout cycling/picking with no build-time script. |
| `res/xml/bottom_row.xml` | same resource name in module | Keep, but remove or remap unsupported pane/IME actions. The current bottom row contains clipboard, IME change, emoji, config, voice and layout-switch events (`unexpected-keyboard/res/xml/bottom_row.xml:1-8`). |
| `res/xml/number_row.xml`, `number_row_no_symbols.xml` | same | Keep for trimmed `LayoutModifier`. |
| `res/xml/numeric.xml`, `numeric_landscape.xml`, `numpad.xml`, `greekmath.xml` | same | Keep as optional special layouts so existing `Event` values have useful embedded targets. Pin layouts may be deferred. |
| `res/xml/split_middle_column.xml` | none initially | Exclude with split/fold behavior. |
| `res/values/themes.xml` | trimmed module `themes.xml` | Keep the core `keyboard` styleable and static fallback styles; remove candidates/emoji/clipboard/navigation attrs. Upstream's styleable and Light/Dark/Black styles are at `unexpected-keyboard/res/values/themes.xml:3-46` and `unexpected-keyboard/res/values/themes.xml:47-126`. |
| `res/values/values.xml` | trimmed module `dimens.xml` | Keep keyboard `margin_top` and `key_padding`; drop pane/settings dimensions (`unexpected-keyboard/res/values/values.xml:1-8`). |
| `assets/special_font.ttf` | `inapp-keyboard/src/main/assets/special_font.ttf` | Keep. `Theme` loads this exact asset path (`unexpected-keyboard/srcs/juloo.keyboard2/Theme.java:92-99`). |
| `res/layout/keyboard.xml` | none | Exclude; it couples the view to `CandidatesView` (`unexpected-keyboard/res/layout/keyboard.xml:1-10`). The app creates its own host. |
| `res/values/layouts.xml` | optional generated-output snapshot | Keep only if the first implementation exposes layout metadata/cycling; otherwise derive a small hard-coded registry for main/numeric/Greek-math and add the full snapshot with the picker. |
| Remaining drawable, pane, settings, launcher, method, dictionary and localized settings resources | none | Exclude. They support stripped features. |

Treat the vendored snapshot as code: include its upstream path, upstream commit, local changes, and refresh procedure in `UPSTREAM.md`. A refresh should copy into a temporary tree, apply the documented adaptation patches, run module tests, and inspect a semantic diff; it must not silently overwrite local adaptations.

## 5. File-by-file port plan

The following table accounts for every top-level Java source currently in `srcs/juloo.keyboard2`.

| Upstream file | Disposition | Required embedded-mode changes |
|---|---|---|
| `Autocapitalisation.java` | Strip | Terminal input must not infer sentence state from an Android editor. |
| `ClipboardHistoryCheckBox.java` | Strip | Settings widget for the excluded clipboard subsystem. |
| `ClipboardHistoryService.java` | Strip | Service/persistence subsystem is out of scope. |
| `ClipboardHistoryView.java` | Strip | Pane UI is out of scope. |
| `ClipboardPinView.java` | Strip | Pane UI is out of scope. |
| `ComposeKey.java` | Keep | Preserve compose-state evaluation; remove no behavior unless a stripped logger/import requires it. |
| `ComposeKeyData.java` | Keep snapshot | Vendor the generated file verbatim and record the generating upstream revision. |
| `Config.java` | Replace with trimmed class | Remove `SharedPreferences`, migrations, dictionaries, suggestions, device locales, panes, theme IDs, direct-boot state, and static preference writes. Retain only renderer/gesture/layout/haptic/repeat values and `IKeyEventHandler`. Prefer constructor injection; see Section 6. |
| `CurrentlyTypedWord.java` | Strip | Supports upstream suggestions/autocapitalisation. |
| `CustomLayoutEditDialog.java` | Defer/strip | Custom XML is loaded from the Termux file; an in-app editor can be designed later. |
| `DeviceLocales.java` | Strip | IME subtype/locale selection is not used. |
| `DirectBootAwarePreferences.java` | Strip | The embedded view reads no preferences and Termux data is used after app startup. |
| `EditorConfig.java` | Keep as terminal stub | Replace `EditorInfo`/`InputConnection` interpretation with `EditorConfig.forTerminal()`: non-numeric, no candidates, no Android selection actions, action key = Enter. Upstream otherwise branches on Android input types and disables selection for `TYPE_NULL` (`unexpected-keyboard/srcs/juloo.keyboard2/EditorConfig.java:27-52`). |
| `Emoji.java` | Strip | Emoji pane/data is excluded; normal Unicode values in XML still work. |
| `EmojiGridView.java` | Strip | Pane UI is excluded. |
| `EmojiGroupButtonsBar.java` | Strip | Pane UI is excluded. |
| `ExtraKeys.java` | Strip initially | It injects locale/preference keys into upstream layouts and its name is easily confused with Termux Extra Keys. Encode required terminal controls in the embedded bottom row. Re-evaluate only with a future layout picker. |
| `FoldStateTracker.java` | Strip | It adds AndroidX Window and hinge state solely for upstream split decisions (`unexpected-keyboard/srcs/juloo.keyboard2/FoldStateTracker.java:3-24`, `unexpected-keyboard/srcs/juloo.keyboard2/FoldStateTracker.java:27-63`). Normal view measurement handles rotation/width in phase one. |
| `Gesture.java` | Keep/adapt | Remove `Config.globalConfig()` access; pass circle sensitivity through `Pointers` or the constructor. Keep the state machine unchanged. |
| `KeyEventHandler.java` | Strip/replace in app | It is built around `InputConnection`, editor actions, autocapitalisation, typed-word tracking, and suggestions. Its dispatch shape is useful reference, but `TerminalKeyEventHandler` is the only embedded implementation (`unexpected-keyboard/srcs/juloo.keyboard2/KeyEventHandler.java:70-143`). |
| `KeyModifier.java` | Keep | Preserve upstream modifier transforms, including Shift/Ctrl/Alt/Fn/compose/Hangul. The handler must not apply these transforms a second time. `KeyModifier` already turns modifier-plus-key combinations into chars or key events (`unexpected-keyboard/srcs/juloo.keyboard2/KeyModifier.java:61-99`, `unexpected-keyboard/srcs/juloo.keyboard2/KeyModifier.java:209-307`). |
| `KeyValue.java` | Keep/trim | Preserve all `Kind`, `Event`, `Modifier`, `Editing`, `Slider`, macro and placeholder values so existing/custom XML parses. Remove or neutralize the global `Stateful._handler`; future suggestions use an app-owned API. The complete enums are defined at `unexpected-keyboard/srcs/juloo.keyboard2/KeyValue.java:8-109` and `unexpected-keyboard/srcs/juloo.keyboard2/KeyValue.java:866-961`. |
| `KeyValueParser.java` | Keep | Required to interpret the existing XML key vocabulary and user layouts. Add parser tests for unsupported-but-recognized events. |
| `Keyboard2.java` | Strip | This is the `InputMethodService` and owns input views, `InputMethodManager`, dictionaries, candidates and pane switching (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2.java:1-56`, `unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2.java:125-161`). `TermuxInAppKeyboard` replaces only the small layout-switch/controller subset. |
| `Keyboard2View.java` | Keep/adapt | Remove all IME/window ownership, inject config and palette, measure from parent constraints, expose state-reset/layout/palette APIs, and keep drawing/touch behavior; details follow below. |
| `KeyboardData.java` | Keep/adapt | Retain bundled/custom XML loading, 9-slot keys, rows, modmap and parser errors. Remove static resource cache or scope it per `Resources` to avoid stale tests/config; add parser limits and public error information suitable for Termux logging. |
| `LauncherActivity.java` | Strip | Upstream application launcher/settings entry point. |
| `LayoutLandscapeModifier.java` | Strip initially | Split/fold layout is excluded. Landscape uses the same layout against a smaller measured height. Reintroduce independently if phone landscape usability testing requires it. |
| `LayoutModifier.java` | Keep, heavily trim | Retain bottom-row and optional number-row composition. Remove global resource/config state, `ExtraKeys`, locale injection, split column, fold, PIN/editor inference and forced upstream CONFIG behavior. Make `modify(KeyboardData, LayoutOptions, Resources)` pure. Upstream currently reads global config and composes number/numpad/bottom/split rows (`unexpected-keyboard/srcs/juloo.keyboard2/LayoutModifier.java:10-93`, `unexpected-keyboard/srcs/juloo.keyboard2/LayoutModifier.java:205-220`). |
| `Logs.java` | Keep as tiny facade or inline | Retain `Log.e` parser diagnostics only; remove `EditorInfo`, config migration and startup-input dumping (`unexpected-keyboard/srcs/juloo.keyboard2/Logs.java:1-52`). Use a distinct `TermuxInAppKeyboard` tag. |
| `Modmap.java` | Keep | Required by layout-defined modifier remapping. |
| `NonScrollListView.java` | Strip | Settings/pane helper. |
| `NumberLayout.java` | Strip initially | Android numeric-editor selection is excluded. Numeric/Greek layouts are selected explicitly by events/controller instead. |
| `Pointers.java` | Keep/adapt | Inject config/gesture values, use a main-looper `Handler`, expose `reset`, cancel callbacks on detach/hide, and call `requestDisallowInterceptTouchEvent(true)` while pointers are active. Preserve pointer, latch/lock, swipe, slider, repeat and callback behavior (`unexpected-keyboard/srcs/juloo.keyboard2/Pointers.java:10-60`, `unexpected-keyboard/srcs/juloo.keyboard2/Pointers.java:675-824`). |
| `SettingsActivity.java` | Strip | Termux settings own all user-facing preferences. |
| `Theme.java` | Keep/adapt | Add immutable programmatic `Palette`; preserve computed metrics/paints and icon font. Retain styleable loading only as a fallback/testing constructor. Upstream currently reads every color from a `keyboard` styleable in its constructor (`unexpected-keyboard/srcs/juloo.keyboard2/Theme.java:10-70`) and computes paints from config/rows (`unexpected-keyboard/srcs/juloo.keyboard2/Theme.java:116-196`). |
| `Utils.java` | Trim | Keep only pure helpers still referenced, such as label capitalization. Remove IME-picker/dialog and file/prefs helpers not used by the library. |
| `VibratorCompat.java` | Keep | Preserve version-specific haptic behavior and system-setting checks (`unexpected-keyboard/srcs/juloo.keyboard2/VibratorCompat.java:8-46`). |
| `VoiceImeSwitcher.java` | Strip | System voice IME switching is explicitly excluded. |

Strip the complete `dict/`, `prefs/`, and `suggestions/` packages and the native `vendor/cdict` integration. This is what removes the NDK dependency rather than merely hiding suggestions at runtime. Strip their supporting candidates/dictionary/clipboard/emoji/settings/launcher resources as listed in Section 4.

### 5.1 `Keyboard2View` embedded changes

`Keyboard2View` already implements `Pointers.IPointerEventHandler`, and its pointer callbacks forward key down/up/hold and modifier changes to the configured handler (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:23-25`, `unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:157-192`). Retain that relationship, but make these changes:

1. **Construct explicitly.** Add `Keyboard2View(Context, Config, Theme.Palette)` for production. XML inflation may retain a test/fallback constructor, but it must not call a global config. Remove `Config.globalConfig()` from construction (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:65-79`).
2. **Remove IME window traversal.** Delete `getParentWindow()` and `refresh_navigation_bar()`. The current code walks `ContextWrapper` instances looking for `InputMethodService` and then dereferences the resulting window; an activity-hosted view has no such service (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:81-105`).
3. **Do not own insets.** Replace the SDK 35 `onApplyWindowInsets` override with normal `super` propagation. The current view derives bottom/cutout padding and returns `WindowInsets.CONSUMED`, which would steal the activity's insets (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:311-325`). Termux owns navigation/cutout/fullscreen policy.
4. **Measure from the parent.** Replace `DisplayMetrics.widthPixels` with `MeasureSpec.getSize(widthMeasureSpec)` minus padding. Compute desired height from rows/config, then resolve it against EXACTLY/AT_MOST constraints. The current measurement assumes display width and an IME-owned screen-height budget (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:264-288`). Report height changes to the controller through layout listeners; never set a raw display-sized height.
5. **Do not reserve the full system back-gesture edge.** Remove the view-wide `systemGestureExclusionRects` behavior by default. The current on-layout code installs exclusion rectangles on Android 10+ (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:290-309`). If edge keys prove unusable, add narrow, measured exclusions only while the keyboard is visible and only after gesture-navigation testing.
6. **Expose safe mutation APIs.** `setKeyboard`, `setPalette`, `resetInputState`, and `setShiftLocked` must run on the main thread. `setKeyboard` already resets modifier/pointer state (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:107-122`). `setPalette` rebuilds `Theme`/computed paints and calls `requestLayout()` plus `invalidate()`.
7. **Lifecycle cleanup.** `onDetachedFromWindow` and controller `hide()` cancel pointer long-press/repeat and macro callbacks, synthesize no terminal output, and clear compose/modifier state. A later show begins neutral.
8. **Parent interception.** On first pointer down, request the accessory/drawer parents not intercept; release that request after the final up/cancel. Keep the existing `ACTION_CANCEL` path and ensure it resets every pointer (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:194-224`).

### 5.2 `Config`: reimplement, do not reuse upstream preferences

Do not initialize upstream `Config` with Termux `SharedPreferences`. The upstream singleton reads a wide preference schema and constructs dictionary/editor/layout state (`unexpected-keyboard/srcs/juloo.keyboard2/Config.java:17-116`, `unexpected-keyboard/srcs/juloo.keyboard2/Config.java:121-205`); initialization also runs migrations and initializes global `LayoutModifier` resources (`unexpected-keyboard/srcs/juloo.keyboard2/Config.java:309-391`). Sharing the Termux preference file would create key-collision and migration risks, while a second private preference file would split ownership and still retain irrelevant state.

Keep the familiar class name for low-diff porting, but make it a non-singleton, mostly immutable embedded config. It should contain only values actually read by retained code:

- handler;
- key/row size, horizontal/bottom/top margins and max keyboard-height fraction;
- label/sub-label size and icon scale;
- key padding, border radius/width, opacity and swipe-trail options;
- swipe distance, slider step, circle sensitivity;
- long-press timeout, repeat start/interval;
- double-tap Shift behavior;
- haptic enabled/duration/amplitude;
- optional bottom-row and number-row flags.

The app creates the config with density-scaled defaults and preference overrides if such UI is added later. Pass it to `Keyboard2View`, `Pointers`, `Gesture`, `Theme.Computed`, and pure `LayoutModifier` calls. `Config.IKeyEventHandler` remains the library-to-host event interface (`unexpected-keyboard/srcs/juloo.keyboard2/Config.java:329-335`). If preserving the static singleton is temporarily necessary for the first compile, scope it to the view instance behind a deprecated adapter and remove it before parallel work packages merge; two activity/view instances must not share a handler.

## 6. `TerminalKeyEventHandler`

### 6.1 Placement and dependencies

Create:

```text
app/src/main/java/com/termux/app/terminal/inappkeyboard/TerminalKeyEventHandler.java
```

It implements `Config.IKeyEventHandler` and receives narrow collaborators:

```java
TerminalKeyEventHandler(
    TerminalView terminalView,
    Supplier<TerminalSession> currentSession,
    HostActions hostActions,
    Handler mainHandler)
```

`HostActions` covers paste/copy, layout switching, settings, hide, compose/shift view state, logging, and the future suggestion hook. Avoid retaining `TermuxActivity` directly if these operations can be represented by the interface; the controller clears references in `onDestroy`.

The dispatch happens on the main thread. `key_down` handles slider immediacy/repeat bookkeeping only; normal output occurs on `key_up`, matching upstream's event ordering (`unexpected-keyboard/srcs/juloo.keyboard2/KeyEventHandler.java:70-126`). The `mods` passed to `key_up` are the modifier snapshot captured for that pointer. `mods_changed` updates visual/debug state only; it must not press Termux Extra Keys or set `TermuxTerminalViewClient.mVirtualFnKeyDown`.

### 6.2 Complete `KeyValue.Kind` mapping

| `KeyValue.Kind` | Embedded action |
|---|---|
| `Char` | On release, call `TerminalView.inputCodePoint(KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD, codePoint, ctrl, alt)`. Shift/diacritic/Fn transformations have already been applied by `KeyModifier`; pass only the remaining CTRL/ALT state. `TerminalView.inputCodePoint` combines client modifier state, handles control-code conversion, and writes through the session (`termux-launcher/terminal-view/src/main/java/com/termux/view/TerminalView.java:1171-1236`). |
| `String` | Treat as a literal XML macro string. With no CTRL/ALT, call `currentSession.write(string)` once. With CTRL or ALT active, iterate Unicode code points through `TerminalView.inputCodePoint` so terminal modifier semantics are retained. Never iterate UTF-16 `char`s. |
| `Keyevent` | Build a DOWN/UP `KeyEvent` pair with `SOURCE_KEYBOARD | SOURCE_VIRTUAL` and the captured CTRL/ALT/SHIFT/META state, then call `TerminalView.onKeyDown` and `onKeyUp`. This preserves the client callback, finished-session Enter behavior, shortcuts, suggestion-bar observation, and terminal-mode-aware `handleKeyCode` path (`termux-launcher/terminal-view/src/main/java/com/termux/view/TerminalView.java:1093-1168`, `termux-launcher/terminal-view/src/main/java/com/termux/view/TerminalView.java:1242-1253`; `termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:235-288`). If synthetic device metadata makes a client shortcut unsafe in tests, use a dedicated `TerminalView.dispatchVirtualKey` wrapper that still invokes the client and terminal handler in that order. |
| `Modifier` | Emit no bytes. `Pointers.Modifier` owns held/latched/locked state; `mods_changed` stores a defensive snapshot and invalidates affected keys. Clear all modifier state on hide, layout replace, detach, session replace, or cancel. |
| `Editing` | Dispatch according to Section 6.3. No Android `InputConnection` or context-menu action IDs are used. |
| `Event` | Dispatch according to Section 6.4. Unsupported pane/IME events are explicit no-ops, never fall through to the system IME. |
| `Compose_pending` | Emit no bytes and call `hostActions.setComposePending(true)`. The next key is resolved by the retained modifier/compose pipeline. Upstream likewise turns this kind into compose-pending state rather than text (`unexpected-keyboard/srcs/juloo.keyboard2/KeyEventHandler.java:113-123`). |
| `Hangul_initial`, `Hangul_medial` | Emit no bytes. Preserve them as transient composition/latch states; only the final transformed `Char`/`String` is written. Add sequence tests from upstream compose data. |
| `Placeholder` | Emit no bytes. `COMPOSE_CANCEL` additionally clears compose pending; removed/font placeholders remain parseable so old custom layouts fail soft. |
| `Slider` | On the first `key_down`, immediately send one mapped arrow; on holds/release send the requested repeat count without sleeping. Horizontal/vertical cursor sliders map to DPAD LEFT/RIGHT/UP/DOWN through the same virtual-key path. Selection variants initially map to normal LEFT/RIGHT with a one-time debug log because a shell cursor is not an Android selection cursor. The slider enum contains these six values (`unexpected-keyboard/srcs/juloo.keyboard2/KeyValue.java:866-893`), and upstream sends the first movement at `key_down` (`unexpected-keyboard/srcs/juloo.keyboard2/KeyEventHandler.java:90-97`). |
| `Macro` | Evaluate recursively on the main handler using a copied key array and local macro modifier state. Preserve the upstream 33 ms delay after `Keyevent`, `Editing`, and `Event` values to maintain ordering, but cap nesting depth and total expanded keys. Cancel the task token on hide/destroy/session replacement. Upstream explains and implements the asynchronous ordering delay at `unexpected-keyboard/srcs/juloo.keyboard2/KeyEventHandler.java:450-518`. |
| `Stateful` | No output in phase one; render an empty/disabled label. Do not retain the static global provider used by upstream suggestions (`unexpected-keyboard/srcs/juloo.keyboard2/KeyValue.java:934-961`). A later suggestion adapter may map these values to app-owned slots without introducing dictionaries into the library. |

Use a small `TerminalModifiers` converter with unit tests. META should be preserved on synthetic `KeyEvent`s when a key value remains a `Keyevent`; there is no generic terminal `meta` boolean in `inputCodePoint`, so a remaining META+Char combination should be handled by `KeyModifier` or converted to an Android key event. Do not silently treat META as ALT.

### 6.3 `Editing` mapping

| `KeyValue.Editing` | Terminal behavior |
|---|---|
| `BACKSPACE` | Dispatch `KEYCODE_DEL`. |
| `SPACE_BAR` | Send U+0020 through `inputCodePoint`; retain its distinct key role only for drawing. |
| `PASTE`, `PASTE_PLAIN` | Invoke the existing session paste path. `TermuxTerminalSessionActivityClient.onPasteTextFromClipboard` already owns clipboard retrieval/paste (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java:225-231`). Both variants are equivalent for a terminal because there is no styled text target. |
| `COPY` | If TerminalView selection mode has non-empty selected text, use the existing activity clipboard action and then leave selection mode according to current Termux behavior. `TerminalView` exposes selected text/selection shutdown APIs (`termux-launcher/terminal-view/src/main/java/com/termux/view/TerminalView.java:1707-1729`, `termux-launcher/terminal-view/src/main/java/com/termux/view/TerminalView.java:1750-1757`). |
| `CUT` | Copy the terminal selection but do not send Delete. A terminal screen is not an editable buffer. Log this semantic reduction only in debug builds. |
| `SELECTION_CANCEL` | Call `TerminalView.stopTextSelectionMode()` if selection is active. |
| `DELETE_WORD` | Dispatch CTRL+`KEYCODE_DEL`, matching the upstream key definition. Document that the active shell/readline decides the resulting control sequence; do not hard-code Ctrl-W unless product testing chooses that terminal-specific contract. |
| `FORWARD_DELETE_WORD` | Dispatch CTRL+`KEYCODE_FORWARD_DEL`. |
| `SELECT_ALL` | No-op in phase one; TerminalView has no public terminal-screen `selectAll` contract. |
| `UNDO`, `REDO`, `REPLACE`, `SHARE`, `ASSIST`, `AUTOFILL` | No-op with structured debug logging. These are Android editable-widget operations, not terminal input. A future SHARE action may be a host action for selected terminal text, but must not be inferred from Android context-menu IDs. |

Keep the mapping exhaustive with a `switch` that has no `default`; adding an upstream enum must fail compilation or a completeness test. The enum source is `unexpected-keyboard/srcs/juloo.keyboard2/KeyValue.java:68-87`.

### 6.4 `Event` mapping

| `KeyValue.Event` | Embedded action |
|---|---|
| `CONFIG` | Open `TerminalIOPreferencesFragment` (or a future in-app-keyboard sub-screen), not Unexpected-Keyboard settings. Close the drawer first if necessary. |
| `SWITCH_TEXT` | Select the last main layout: valid custom layout if loaded, otherwise bundled QWERTY. |
| `SWITCH_NUMERIC` | Toggle the bundled numeric layout; return to the previous main layout on the next text/back event. |
| `SWITCH_FORWARD`, `SWITCH_BACKWARD` | Cycle the controller's configured layout registry. In phase one this is custom/main plus explicitly packaged alternates; persist the selected main layout only after a picker preference exists. |
| `SWITCH_GREEKMATH` | Toggle bundled `greekmath.xml` when packaged; otherwise no-op with debug log. |
| `ACTION` | Dispatch Enter. The terminal stub has no Android editor action. |
| `CAPS_LOCK` | Ask the view/pointer state API to lock Shift and redraw. It must be cleared on reset/layout change. |
| `HIDE_SELF` | `TermuxInAppKeyboard.hide(USER_EVENT)`. |
| `CHANGE_METHOD_PICKER`, `CHANGE_METHOD_PREV`, `CHANGE_METHOD_NEXT` | Hide the in-app keyboard. Do not launch the system IME picker or enable the system IME. This gives old/custom bottom rows a safe escape action. |
| `SWITCH_EMOJI`, `SWITCH_BACK_EMOJI`, `SWITCH_CLIPBOARD`, `SWITCH_BACK_CLIPBOARD`, `SWITCH_VOICE_TYPING`, `SWITCH_VOICE_TYPING_CHOOSER` | No-op with structured debug log; panes/voice are not ported. A curated embedded bottom row should omit these labels so users do not see dead first-party keys. |

The upstream event enum is exhaustive at `unexpected-keyboard/srcs/juloo.keyboard2/KeyValue.java:8-28`. As with `Editing`, use a no-default switch and a mapping test.

### 6.5 Modifiers and Termux Fn interaction

Unexpected-Keyboard applies its own `FN` mapping before the handler (`unexpected-keyboard/srcs/juloo.keyboard2/KeyModifier.java:234-307`). Termux separately implements its virtual-volume Fn layer: W/A/S/D become arrows, P/N pages, T/I special keys, 1-0 F1-F10, E Escape, punctuation/control characters, Alt-B/F/X, volume, and toolbar actions (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:362-460`). These systems must remain separate:

- In-app `FN` is handled only by Unexpected-Keyboard's `Pointers`/`KeyModifier`. Never set `mVirtualFnKeyDown` and never call the Termux Fn mapping a second time.
- Volume-up Fn and existing Termux Extra Keys Fn retain the current `TermuxTerminalViewClient` behavior.
- In-app CTRL/ALT are supplied with the emitted value and do not mutate Termux Extra Keys latch state. Termux's `TerminalView.inputCodePoint` may additionally observe a physical/extra-key CTRL/ALT through its client; tests must cover combined sources (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:321-349`, `termux-launcher/terminal-view/src/main/java/com/termux/view/TerminalView.java:1171-1192`).
- Shift, accents, small caps, superscript, compose and Hangul transformations stay in `KeyModifier`; the handler must not uppercase or normalize Unicode.

## 7. Visibility, lifecycle, IME suppression, and resize

### 7.1 `TermuxInAppKeyboard` API and state

Create:

```text
app/src/main/java/com/termux/app/terminal/inappkeyboard/TermuxInAppKeyboard.java
```

Public surface:

```java
boolean isEnabled();
boolean isVisible();
void onCreate(Bundle state);
void onStart();
void onResume();
void onStop();
void onDestroy();
void onSaveInstanceState(Bundle out);
void onConfigurationChanged(Configuration configuration);
void onPreferencesReloaded();
void show(ShowReason reason);
void hide(HideReason reason);
void toggle(ToggleReason reason);
void attachSession(TerminalSession session);
```

State model:

- `enabled` is read from `TermuxAppSharedPreferences` and defaults false.
- `visible` is activity state, not the legacy soft-keyboard-enabled preference. Initial default after first enable is **visible** unless a restored instance-state value exists.
- `onStop` cancels active touches/macros but does not set `visible=false`; the view remains in the hierarchy. Returning from an app launch restores the same visibility. The current activity stop path tears down listeners but does not destroy the activity (`termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:2750-2784`).
- Store `visible` and selected special/main-layout identity in `onSaveInstanceState`; the activity already has a save-state hook for toolbar state (`termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:2809-2815`). Process death restores the view after preferences/config/layout are rebuilt, never by serializing `KeyboardData`.
- `onDestroy` clears handlers/listeners and controller references. `onConfigurationChanged` recomputes palette, reloads measurement config, resets pointers, and requests geometry; the activity already forwards configuration changes to terminal material color refresh (`termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:2817-2824`).
- If the preference changes from enabled to disabled, hide/reset first, cancel callbacks, clear system-IME suppression flags, then call the existing `setSoftKeyboardState(false, true)` exactly once to restore legacy policy.

### 7.2 Toggle/show call sites

Add the enabled-mode branch at the top of `TermuxTerminalViewClient.onToggleSoftKeyboardRequest()`:

```java
if (mActivity.getInAppKeyboard().isEnabled()) {
    mActivity.getInAppKeyboard().toggle(ToggleReason.KEYBOARD_ACTION);
    mActivity.getInAppKeyboard().suppressSystemIme();
    return;
}
// existing system IME implementation unchanged
```

This one branch covers:

- the existing Extra Keys `KEYBOARD` action, which already calls `onToggleSoftKeyboardRequest()` (`termux-launcher/app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java:75-80`);
- the drawer's `toggle_keyboard_button`, whose listener calls the same client method (`termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:4523-4527`);
- Ctrl+Alt+K, which also calls that method (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:245-260`).

In `onSingleTapUp`, after URL/mouse checks, call `controller.show(TERMINAL_TAP)` when enabled and return. The current code otherwise calls `KeyboardUtils.showSoftKeyboard` on a terminal tap (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:187-205`). A tap should show, not toggle, so typing never hides the keyboard accidentally.

At the top of `setSoftKeyboardState`, branch to `controller.onSystemImeStateRequested(...)`, request TerminalView focus, install the embedded-mode focus listener, and return. This is essential because `onResume` and activity styling reload both call `setSoftKeyboardState` (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:115-129`, `termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:149-156`). Also guard/cancel the delayed `getShowSoftKeyboardRunnable`; it directly shows the IME (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:652-658`).

### 7.3 System IME suppression

For terminal focus, `suppressSystemIme()` should:

1. cancel all pending show runnables;
2. call `KeyboardUtils.hideSoftKeyboard(activity, terminalView)`;
3. set Termux's disable-soft-keyboard window flag (`FLAG_ALT_FOCUSABLE_IM` through the existing helper);
4. set `SOFT_INPUT_STATE_ALWAYS_HIDDEN` while preserving the existing adjust/fullscreen bits;
5. request TerminalView focus without installing a listener that calls `showSoftKeyboard`.

The existing helper hides first and sets/clears `FLAG_ALT_FOCUSABLE_IM`, and exposes the always-hidden/adjust-resize helpers (`termux-launcher/termux-shared/src/main/java/com/termux/shared/view/KeyboardUtils.java:86-119`). Reuse these helpers rather than duplicating flags.

The existing focus listener explicitly shows the system IME for the terminal or toolbar `EditText` (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:601-630`). In embedded mode replace it with a listener that never shows the system IME for TerminalView. **Phase-one policy:** strict activity-wide suppression while in-app mode is enabled. The toolbar text-input page remains focusable for hardware input but does not summon a system IME; selecting the terminal page is the supported touch-input workflow. Supporting an `EditText` target adapter is an open follow-up, not permission to leak a system IME into terminal focus.

Do not mutate the legacy `KEY_SOFT_KEYBOARD_ENABLED` when showing/hiding the embedded view. That preference must still describe the legacy IME when embedded mode is later disabled. The current toggle path writes it and changes disable flags (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:524-563`), which is why the embedded branch must return before the old code.

### 7.4 View hierarchy and terminal resizing

The current `DrawerLayout` is constrained above `accessory_stack_container`, so increasing the accessory's measured height already reduces the terminal/drawer area (`termux-launcher/app/src/main/res/layout/activity_termux.xml:39-46`, `termux-launcher/app/src/main/res/layout/activity_termux.xml:164-171`). Add this bottom-most child to that `RelativeLayout`:

```xml
<LinearLayout
    android:id="@+id/inapp_keyboard_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_alignParentBottom="true"
    android:orientation="vertical"
    android:visibility="gone">

    <FrameLayout
        android:id="@+id/inapp_keyboard_suggestion_host"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:visibility="gone" />

    <FrameLayout
        android:id="@+id/inapp_keyboard_view_host"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
</LinearLayout>
```

Then change `terminal_toolbar_view_pager` from `layout_alignParentBottom=true` to `layout_above=@id/inapp_keyboard_container`. Existing apps-bar rows are already chained above the toolbar (`termux-launcher/app/src/main/res/layout/activity_termux.xml:233-263`). Keep the suggestion host `GONE`/zero-height until the future feature is enabled.

`TermuxActivity` has a custom accessory renderer that currently models only toolbar visibility and explicitly sizes the accessory from its component heights. Extend, do not bypass, that geometry model:

- Add `keyboardShown` and `keyboardHeight` to `AccessoryRenderState`/height calculation. The current state tracks `toolbarShown`, and current combined height is derived from toolbar/apps components (`termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:1708-1763`, `termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:4198-4243`).
- `accessory_stack_container` is visible when toolbar **or** keyboard is visible. Apps/toolbar views remain governed only by `toolbarShown`.
- Add `inapp_keyboard_container` to accessory layout-change listeners so a new measured keyboard height schedules the existing render sync. The listener watch list is centralized at `termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:5629-5642`.
- After final accessory height application, retain the existing posted `TerminalView.updateSize()` call (`termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:4237-4243`). `TerminalView.onSizeChanged` also calls `updateSize`, so the emulator rows/columns converge after normal layout (`termux-launcher/terminal-view/src/main/java/com/termux/view/TerminalView.java:1300-1305`).
- Restrict blur/surface bounds and dock-plank hit regions to their existing toolbar/apps content bounds. Do not let the transparent `accessory_surface_host` or plank gesture layer intercept keyboard touches. The keyboard paints its own opaque/palette background.
- If toolbar is hidden while keyboard is visible, the toolbar height contribution is zero and the keyboard remains bottom-aligned. If keyboard is hidden, the hierarchy must reproduce today's toolbar geometry exactly.

The zero-height `activity_termux_bottom_space_view` is an actual system-IME resize/probe surface, not the embedded keyboard host (`termux-launcher/app/src/main/res/layout/activity_termux.xml:295-314`). Leave it unchanged. `TermuxActivityRootView` should continue reacting only to real IME visibility; an in-app view changes ordinary child measurement and must not synthesize IME insets. This avoids applying both accessory height and an IME lift.

### 7.5 Fullscreen, landscape, and navigation insets

- The view never consumes `WindowInsets`; the activity/root remains the sole system-bar owner.
- Its maximum height is computed from the actual parent `AT_MOST` height after system/fullscreen policy, not `DisplayMetrics.heightPixels`.
- Suggested defaults: portrait row height 48-56 dp with a cap of 42% of available terminal-root height; landscape 40-48 dp with a cap of 55%. Shrink row height uniformly before clipping or hiding keys.
- On orientation/density/font-scale change, reset active pointers, rebuild metrics/palette, remeasure, and schedule one accessory geometry update.
- Phase one does not split around a fold/hinge. Treat a foldable as a normal available-width view; add `LayoutLandscapeModifier`/WindowManager only from measured device evidence.

## 8. Preference and custom-layout plumbing

### 8.1 Preference keys and accessors

Add to `TermuxPreferenceConstants.TERMUX_APP`:

```java
public static final String KEY_IN_APP_KEYBOARD_ENABLED = "in_app_keyboard_enabled";
public static final boolean DEFAULT_IN_APP_KEYBOARD_ENABLED = false;

public static final String KEY_IN_APP_KEYBOARD_THEME = "in_app_keyboard_theme";
public static final String DEFAULT_IN_APP_KEYBOARD_THEME = "system";
```

Use `system`, `light`, `dark`, and `black` as stable stored values. The existing soft-keyboard preference is in the same constants group (`termux-launcher/termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxPreferenceConstants.java:283-297`).

Add to `TermuxAppSharedPreferences`:

```java
boolean isInAppKeyboardEnabled();
void setInAppKeyboardEnabled(boolean enabled); // useful for migrations/tests
String getInAppKeyboardTheme();                // validate/fallback to "system"
```

Follow the existing typed accessor pattern used for terminal-toolbar and soft-keyboard state (`termux-launcher/termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxAppSharedPreferences.java:76-81`, `termux-launcher/termux-shared/src/main/java/com/termux/shared/termux/settings/preferences/TermuxAppSharedPreferences.java:426-438`). Do not store visible/hidden state here; it is activity instance state.

### 8.2 Settings UI

In `termux_terminal_io_preferences.xml`, add:

1. `SwitchPreferenceCompat` titled **In-app keyboard**, summary explaining that the keyboard stays inside the launcher and replaces the system keyboard for terminal input.
2. `ListPreference` titled **In-app keyboard theme**, entries System, Light, Dark, Black, dependent on the enable switch.
3. A non-clickable informational preference showing `~/.termux/keyboard/layout.xml`, or a click action that opens documentation. Do not add an editor before one is designed.

The file currently contains only fullscreen and soft-keyboard switches (`termux-launcher/app/src/main/res/xml/termux_terminal_io_preferences.xml:1-19`). Add DataStore get/put branches in `TerminalIOPreferencesFragment` using the new constants; the fragment currently explicitly routes each supported key (`termux-launcher/app/src/main/java/com/termux/app/fragments/settings/termux/TerminalIOPreferencesFragment.java:28-78`). Add strings in the app's normal string resources.

Preference changes take effect when returning from settings or running the existing settings reload path. `TermuxTerminalViewClient.onStart` already re-reads preferences after returning from settings (`termux-launcher/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:102-113`); have `TermuxActivity` call `controller.onPreferencesReloaded()` in the same lifecycle/reload flow.

### 8.3 Layout-file load contract

`TermuxInAppKeyboardLayoutLoader` (app package) owns file access; the library owns XML parsing.

Algorithm:

1. Compute `new File(TERMUX_DATA_HOME_DIR, "keyboard/layout.xml")`.
2. If absent, load `KeyboardData.load(resources, R.xml.latn_qwerty_us)`.
3. If present, require a regular readable file and a maximum UTF-8 size of 512 KiB. Read off the main thread.
4. Parse with `KeyboardData.load_string_exn(text)`, also enforcing implementation limits such as at most 16 rows, 32 keys per row, 512 total keys, and bounded macro expansion. The upstream loader accepts an arbitrary XML string and reports parser exceptions (`unexpected-keyboard/srcs/juloo.keyboard2/KeyboardData.java:212-270`, `unexpected-keyboard/srcs/juloo.keyboard2/KeyboardData.java:698-746`); the host must add resource-exhaustion limits for an app-visible file.
5. Apply pure `LayoutModifier` with bottom-row/number-row options unless the XML declares its own bottom row. The example QWERTY layout documents `bottom_row="false"` for a custom bottom row (`unexpected-keyboard/srcs/layouts/latn_qwerty_us.xml:21-23`).
6. Post the fully parsed `KeyboardData` to the main thread and call `setKeyboard`. On an error, retain the last-known-good in-memory layout; if none exists, load bundled QWERTY. Show one concise toast/snackbar and log path plus line/column/error class, never the full user layout.
7. Cache `(canonical path, size, lastModified)` and recheck on `onStart`, `onResume`, and settings reload. Do not use a permanent `FileObserver` in phase one. Document atomic user updates (`write temp; mv temp layout.xml`) to avoid parsing a partially written file.

The parser and layout modification run only when the signature changes. Configuration/theme changes reuse the parsed `KeyboardData`.

## 9. Material You theming

### 9.1 Mechanism

Use a programmatic immutable `Theme.Palette`, constructed in the app and consumed by the library. Do **not** make runtime theme overlays the primary mechanism.

Rationale: upstream's `Theme` resolves a fixed `R.styleable.keyboard` set once through `obtainStyledAttributes` (`unexpected-keyboard/srcs/juloo.keyboard2/Theme.java:41-70`). `ContextThemeWrapper` works for static styles, but runtime Material colors, forced light/dark transforms, preference changes, and activity color refresh would require creating/reinflating a themed view. A palette constructor lets Termux resolve current colors, unit-test contrast, and update an existing view. Keep trimmed style resources only as fallbacks and for visual parity tests.

Create:

```text
app/src/main/java/com/termux/app/terminal/inappkeyboard/InAppKeyboardPaletteFactory.java
```

It calls `MaterialColors.getColor(viewOrContext, attr, fallback)` for active Material roles, then returns `Theme.Palette`. Recompute on controller creation, activity configuration change, styling reload, and whenever a signature of the source role colors changes. This mirrors `MaterialTerminalColorScheme`, which resolves Material roles and maintains a material-color signature (`termux-launcher/app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java:35-52`, `termux-launcher/app/src/main/java/com/termux/app/terminal/MaterialTerminalColorScheme.java:130-150`).

### 9.2 Palette roles

| Keyboard field | Material source for `system` | Light transform | Dark transform | Black variant |
|---|---|---|---|---|
| keyboard background | `colorSurface` | blend toward white until light-surface luminance target | blend toward black until dark-surface target | black |
| normal key | `colorSurfaceContainerHigh` | light neutral surface | dark neutral surface | black |
| action key | `colorSecondaryContainer` | retain hue, light container tone | retain hue, dark container tone | black with subtle secondary outline |
| space bar | `colorSurfaceContainerHighest` | slightly stronger neutral separation | slightly stronger neutral separation | black |
| activated key | `colorPrimaryContainer` | light primary container | dark primary container | primary at low opacity over black |
| primary label | `colorOnSurface` | enforce >= 4.5:1 against key | enforce >= 4.5:1 against key | white/current on-surface, contrast checked |
| sub-label/secondary label | `colorOnSurfaceVariant` | contrast-adjusted dark neutral | contrast-adjusted light neutral | on-surface-variant, contrast checked |
| activated/pressed label or trail | `colorPrimary` | preserve active Material hue | preserve active Material hue | primary |
| locked modifier | `colorSecondary` (fallback `colorPrimary`) | preserve hue | preserve hue | secondary/primary |
| border | `colorOutlineVariant` | alpha-adjusted | alpha-adjusted | outline at low alpha |

Use `SYSTEM` to consume active roles unchanged. `LIGHT` and `DARK` still derive accent hues from active Material primary/secondary colors, but normalize neutral surface/label luminance toward a light or dark keyboard respectively. `BLACK` pins all neutral surfaces to black while retaining active Material accents. Use Material/Android color utilities and contrast tests; do not hand-edit HSV values inside `Theme`.

`Theme.Palette` should contain resolved ARGB ints only, plus border enabled/width/radius and optional opacity. `Theme.Computed` continues to build paints and text metrics. Apply the keyboard background on `Keyboard2View` itself, not on Termux's blur host, so the result is deterministic in fullscreen and black mode.

### 9.3 Static fallback resources

Retain trimmed `BaseTheme`, `Light`, `Dark`, and `Black` styles and the core `keyboard` styleable. These support preview/tests and give a safe fallback if palette construction fails. Remove `colorNavBar`, candidates, emoji and clipboard attributes because the embedded view neither owns system navigation nor those panes. Upstream already contains Light/Dark/Black definitions and separate Monet variants, but the runtime palette supersedes those static Monet styles (`unexpected-keyboard/res/values/themes.xml:65-126`, `unexpected-keyboard/res/values/themes.xml:213-239`).

## 10. Future LLM suggestion-bar hook (design only)

Do not reuse Unexpected-Keyboard's `CandidatesView`, `Suggestions`, `CurrentlyTypedWord`, `Stateful` global provider, dictionaries, or `cdict`. The stock IME creates candidates/dictionaries alongside its `InputConnection` handler (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2.java:35-56`, `unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2.java:125-146`), which would reintroduce the stripped coupling and native dependency.

Also do not repurpose the existing `com.termux.app.SuggestionBarView` as the keyboard suggestion strip. Despite its name, that view is the launcher apps/search row: it stores `LauncherAppEntry` data, pinned/most-used apps, icons, and a TerminalView-assisted launcher query (`termux-launcher/app/src/main/java/com/termux/app/SuggestionBarView.java:139-205`), and `TermuxActivity` installs it into `apps_bar_viewpager` (`termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:3007-3066`). Keep that component and its terminal-driven app-search callbacks unchanged (`termux-launcher/app/src/main/java/com/termux/app/TermuxActivity.java:5342-5393`). Name the future keyboard component `KeyboardSuggestionBarView` to avoid ambiguity.

Reserve these app-owned interfaces:

```java
interface SuggestionSource {
    Cancellable request(SuggestionRequest request, Callback callback);
}

final class SuggestionRequest {
    String sessionId;
    long generation;
    String safeContext;
    String workingDirectory; // nullable, opt-in
}

interface SuggestionSink {
    void acceptSuggestion(String text);
}
```

The zero-height `inapp_keyboard_suggestion_host` from Section 7 becomes visible only when a source is enabled. `SuggestionSink.acceptSuggestion` feeds the accepted Unicode text through `TerminalKeyEventHandler` as an explicit `String`, preserving one terminal output path. Never let a backend mutate `KeyboardData` or call `TerminalSession` directly.

The concrete future source should be `TaiKeyboardSuggestionSource`, an adapter over the app's existing Termux AI (TAI) service boundary—not a new model runtime inside `TermuxActivity`. `TaiManager` already places `MultiBackendTaiRuntime` in the dedicated runtime process and gives the app process a `TaiRuntimeServiceClient` (`termux-launcher/app/src/main/java/com/termux/ai/TaiManager.java:39-72`); the runtime interface already supports callback-based chat/completion (`termux-launcher/app/src/main/java/com/termux/ai/TaiRuntime.java:8-20`). The service client is blocking/stream-draining on its caller thread, so the adapter must own a background executor and marshal UI updates to the main thread (`termux-launcher/app/src/main/java/com/termux/ai/TaiRuntimeServiceClient.java:52-101`). Add a request-scoped cancellation API to the TAI boundary before relying on high-frequency suggestions; a global runtime cancel could interfere with another active AI operation.

Required future properties:

- explicit opt-in and disclosure of terminal context sent to a model;
- bounded/redacted context, with passwords/alternate-screen/private sessions excluded;
- generation/session IDs so late results cannot enter a different session;
- cancellation on keystroke, session switch, hide, stop, or destroy;
- all backend work off the main thread;
- UI updates on the main thread;
- no automatic command execution—acceptance inserts text only, with Enter a separate user action;
- accessibility semantics and hardware-key navigation for suggestions.

Keep `Config.IKeyEventHandler.suggestion_entered(String)` as a dormant compatibility seam that forwards to `SuggestionSink` only when the app installs one; otherwise it is a no-op. Do not expose `KeyValue.Stateful._handler` globally.

## 11. Phased implementation and parallel file ownership

The work packages below have non-overlapping production-file ownership. Agree on the constructor/interfaces in Sections 3 and 6 before parallel implementation. Integrate in dependency order even if packages are developed concurrently.

| Package | Scope and exclusive files | Verification |
|---|---|---|
| WP0 — module/vendor core | `settings.gradle`, `app/build.gradle`, all new `inapp-keyboard/**`. Port/adapt core, resources, parser/gesture/render unit tests. No app Java beyond the dependency declaration. | `./gradlew :inapp-keyboard:testDebugUnitTest :inapp-keyboard:assembleDebug`; parser golden tests for bundled and custom XML; gesture/modifier/macro tests. |
| WP1 — terminal dispatch | New `app/src/main/java/com/termux/app/terminal/inappkeyboard/TerminalKeyEventHandler.java`, `HostActions.java`, and corresponding app test files only. No `TermuxActivity` or client edits. Use fakes for view/session/host. | Unit tests exhaust every `Kind`, `Editing`, `Event`, slider direction/repeat, Unicode string, modifier and macro cancellation; then `./gradlew :app:testDebugUnitTest`. |
| WP2 — preferences | `TermuxPreferenceConstants.java`, `TermuxAppSharedPreferences.java`, `TerminalIOPreferencesFragment.java`, `termux_terminal_io_preferences.xml`, and required app string resource file(s). No controller/activity edits. | Preference DataStore read/write/default/theme-value tests; `./gradlew :termux-shared:testDebugUnitTest :app:assembleDebug`. |
| WP3 — controller and IME policy | New `TermuxInAppKeyboard.java`, `TermuxInAppKeyboardLayoutLoader.java`, controller tests, and `TermuxTerminalViewClient.java`. Do not edit activity XML or `TermuxActivity.java`; use a pre-agreed `Host` interface implemented later. `TermuxTerminalExtraKeys.java` needs no production edit because it already funnels to the client. | Controller state-machine tests for enable/disable/toggle/tap/stop-resume/recreation; malformed/missing/changed layout tests; static search/test proving embedded branches do not call an IME show path; `./gradlew :app:testDebugUnitTest`. |
| WP4 — activity layout/geometry | `activity_termux.xml`, `TermuxActivity.java`, and geometry-specific tests only. Instantiate/host the controller, extend accessory render state/height/listeners, save state, expose narrow host methods. | Existing toolbar-only geometry snapshots unchanged; combinations `{toolbar, keyboard} x {shown, hidden}`; portrait/landscape/fullscreen/system-IME probe; `./gradlew :app:assembleDebug`. |
| WP5 — Material palette | New `InAppKeyboardPaletteFactory.java` plus its test file only. Theme's palette data class is owned/frozen by WP0; preference values are owned/frozen by WP2. | Contrast/role mapping tests for auto/light/dark/black and palette-signature changes; `./gradlew :app:testDebugUnitTest`. |
| WP6 — attribution | New `inapp-keyboard/UPSTREAM.md`, new Unexpected-Keyboard license/provenance file if counsel/project convention requires it, `THIRD_PARTY_NOTICES.md`, and the existing license-resource copy task only. No source edits. | Generated license resource includes Unexpected-Keyboard; Settings license screen smoke test. The app currently packages `THIRD_PARTY_NOTICES.md` through `generateLicenseResources` (`termux-launcher/app/build.gradle:276-289`). |
| WP7 — integration/device test | Test files/scripts and fixes assigned back to the owning package; one integration owner coordinates rather than editing every partition concurrently. | `./gradlew :app:assembleDebug`; install/smoke on API 26 and API 35/36; acceptance matrix below. |

If an integration defect requires touching another package's owned file, hand it back or serialize the edit after that owner merges. In particular, only WP4 edits `TermuxActivity.java`, only WP3 edits `TermuxTerminalViewClient.java`, and only WP0 edits `Keyboard2View`/`Config`/`Theme`; this avoids the highest-conflict files.

### Acceptance matrix

1. Preference off: every system-IME behavior and toolbar/accessory dimension matches baseline.
2. Preference on, visible: terminal receives ASCII, multi-code-point Unicode, arrows, F-keys, Enter, Tab, Escape, Backspace, CTRL/ALT combinations, compose and macros.
3. Preference on, hidden: terminal taps and KEYBOARD actions behave as designed; no system IME appears.
4. Extra Keys KEYBOARD, drawer KEYBOARD, Ctrl+Alt+K, layout `HIDE_SELF`, and change-method events all reach the controller.
5. Launch another app from the HOME launcher, return Home/Back: visibility, active layout and terminal session remain; active touch/modifier state does not.
6. Rotate and toggle fullscreen with keyboard shown/hidden and toolbar shown/hidden: no overlap, clipped row, stale terminal columns, or double bottom inset.
7. Missing custom XML uses QWERTY; valid edit reloads; malformed/oversized/partial XML retains last good layout and never crashes.
8. Swipe all eight directions, round trip, circle/anticircle, long press, repeat, sliders, modifier latch, double-tap lock and cancel/multi-touch.
9. Auto/light/dark/black palettes update on theme/configuration changes and pass contrast checks.
10. TalkBack identifies the feature as not yet production-ready unless virtual key accessibility from Section 12 is implemented; do not ship silently inaccessible canvas keys.

## 12. Risks and open questions

### High-priority risks and mitigations

| Risk | Consequence | Mitigation/decision gate |
|---|---|---|
| Parent touch interception by drawer/accessory animations | Lost swipe or stuck modifier; launching app gesture may fire instead of a key | Disallow interception for active pointers; exclude keyboard rect from dock-plank/A-Z gesture hit testing; reset on CANCEL/hide. Multi-pointer instrumentation tests are release-blocking. |
| Canvas-only key accessibility | TalkBack cannot discover individual keys | Add an `ExploreByTouchHelper` virtual-view tree with center/corner labels, bounds, click/long-click actions, modifier checked/locked state, and traversal order. This is a ship criterion, even if delivered after the initial core port. Upstream's renderer draws all labels directly rather than child views (`unexpected-keyboard/srcs/juloo.keyboard2/Keyboard2View.java:340-378`). |
| IME suppression flag affects dialogs or toolbar `EditText` | Text fields elsewhere in the activity cannot summon an IME | Phase-one strict policy is explicit. Audit all activity text fields. If required, add a scoped target adapter that temporarily clears suppression only for a non-terminal EditText; never let terminal focus show the IME. |
| Insets/fullscreen/API 35 | Double bottom padding, hidden bottom row, terminal overlap | Remove the view's IME inset consumption; activity owns all insets. Test gesture and 3-button navigation on API 35/36 and fullscreen. |
| Accessory renderer exact-height/blur system | Keyboard clipped or overlay consumes touch | Extend its authoritative state/height model and listeners; do not independently mutate container height. Add baseline toolbar-only regression tests. |
| Modifier double application | Wrong control bytes, duplicated Alt escape, Termux shortcuts triggered | Treat `KeyModifier` output as canonical; use captured mods once; keep in-app Fn separate from Termux virtual Fn. Exhaustive dispatch tests and terminal escape golden tests. |
| Layout parser resource exhaustion or malformed user file | UI freeze/OOM/crash on Home activity | 512 KiB/read limits, row/key/macro caps, off-main parsing, last-known-good fallback, sanitized diagnostics. |
| Macro/repeat callbacks outlive session/activity | Text enters a new session/app state | Controller-scoped cancellation token and generation check on every delayed step; reset on hide/stop/session change/destroy. |
| Landscape height | Keyboard consumes most terminal or labels become illegible | Parent-constrained uniform row scaling with lower touch-target threshold; device tests before enabling split layout. |
| Rendering/file-load performance | Home jank and dropped swipes | Cache parsed layouts and computed paints; no I/O or allocations in draw/touch hot paths; profile show/first draw and multi-touch. |
| Custom layout uses stripped events | Visible dead keys | Parser retains all event values; curated bundled bottom row removes dead actions; event no-ops are logged; document the embedded compatibility subset. |
| Upstream divergence | Security/behavior fixes become hard to merge | Preserve package and class names, isolate adaptations, record commit/path/patch notes, and maintain golden parser/gesture tests. |

### Product/open questions

1. Should the first enable default to visible (recommended) or preserve the last hidden state across process death? The design stores only instance state, not a permanent visibility preference.
2. Is strict system-IME suppression acceptable for the terminal toolbar text-input page, or must phase one include an `EditText` input target?
3. Should the complete upstream layout catalog ship immediately, or should phase one package only QWERTY/numeric/Greek-math plus custom XML? The module design supports either; the recommendation is the static full snapshot because XML size is expected to be modest, but measure APK impact.
4. Should selection sliders be ordinary shell cursor arrows (recommended initially) or manipulate TerminalView's screen selection handles?
5. Should `DELETE_WORD` preserve upstream Ctrl+Delete, use conventional terminal Ctrl-W, or be layout-configurable? Decide through shell/readline compatibility tests.
6. Are keyboard height, haptic strength, bottom row, number row, and swipe sensitivity preferences needed at launch, or are fixed reviewed defaults sufficient?
7. Should change-method keys hide the embedded keyboard (this design) or present an explicit “Use system IME” action that temporarily disables the preference?
8. Which custom-layout error surface is acceptable for a HOME app: toast, settings warning row, or persistent notification? Avoid repeated messages on every resume.
9. Is TalkBack virtual-key support required in the first merge or before release? This design treats it as a release gate.
10. What terminal context, if any, may a future LLM suggestion source read? This must be a separate privacy/product design before implementation.

## 13. Licensing and provenance

Both source projects are GPLv3-compatible at the project level, but vendored modified code still needs clear provenance and notices. Unexpected-Keyboard's repository declares GPLv3 terms (`unexpected-keyboard/LICENSE:1-12`). Termux Launcher already has a “Vendored and adapted code” section and packages that notice into the app's license resources (`termux-launcher/THIRD_PARTY_NOTICES.md:1-24`, `termux-launcher/app/build.gradle:276-289`).

The implementation package should therefore:

1. add Unexpected-Keyboard, upstream URL, copyright holders, GPLv3, reviewed commit, and “modified for embedded Termux use” to `THIRD_PARTY_NOTICES.md`;
2. add `inapp-keyboard/UPSTREAM.md` with copied paths, generated snapshot provenance, deliberate removals, local adaptations, and refresh instructions;
3. preserve upstream source headers and notices;
4. include the applicable GPL text through the existing license screen/resource path, adding a separately named copy only if the project's attribution convention or legal review calls for it;
5. document modifications and dates in provenance/release notes rather than implying an unmodified upstream library.

No dictionary assets/native `cdict` code are copied, so their separate provenance and build dependencies must not appear in the module. Obtain project/legal review before release; this section is an engineering provenance plan, not legal advice.

## 14. Recommended implementation order

1. Freeze the module API, handler event contract, palette fields, controller host interface, and accessory geometry state additions.
2. Complete WP0 and its parser/gesture tests; this establishes a usable embedded view without Termux writes.
3. Complete WP1, WP2, and WP5 against those frozen interfaces in parallel.
4. Complete WP3 against a fake host, including layout fallback and strict IME-suppression state tests.
5. Complete WP4, integrating the host and layout geometry without changing dispatcher behavior.
6. Integrate, run the full acceptance matrix, add accessibility virtual keys, and resolve the open terminal semantics from device evidence.
7. Complete attribution before any distributable build.

The critical path is not XML parsing; it is accessory geometry, system-IME suppression, exhaustive terminal event semantics, and accessible multi-touch rendering. Keep those concerns independently testable throughout the implementation.
