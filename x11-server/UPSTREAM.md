# x11-server — vendored from termux/termux-x11

This module is a copy of termux-x11's `lorie` library module, in the same package
(`com.termux.x11`) so it stays diffable against upstream. The native half — `libXlorie.so`, the
Xorg "Xlorie" DDX built against sixteen freedesktop submodules — is not built here: it is a
committed prebuilt under `app/src/main/jniLibs/<abi>/`, produced by
`.github/workflows/build_x11_native.yml` (and `../x11-local-build.sh`) from the same commit.

- upstream: <https://github.com/termux/termux-x11> (GPLv3, the licence this app already ships under)
- upstream commit: `9df8b767645aa0d0a2f2576767449df55b41962f` (2026-09-04)
- pinned in three places, which must agree: this file, `x11-server/version.gradle`, and the
  workflow's `TERMUX_X11_COMMIT`. Java from one upstream and a native library from another is a
  JNI mismatch waiting to happen.

Read this before merging a newer upstream. Everything below is a deliberate deviation; anything
not listed is upstream's code unchanged, and should be updated by taking upstream's version.

## Why the module exists at all

Termux:X11 is a second app: the `termux-x11` command starts the server as `app_process`, which
class-loads that app's APK and hands its running activity a Binder. A launcher cannot embed
another app's surface on Android 14–16, so the only way to put an X display inside the home
screen is to own the server. See `project-docs/plans/pane-wall-x11-study.md`.

## What is not vendored

| Dropped | Why |
|---|---|
| `MainActivity` | The display is a page of the pane wall, not a fullscreen single-instance activity. Replaced by `LorieHost` (below). |
| `LorieBroadcastReceiver` | The launcher declares its own receiver for the server's `ACTION_START` broadcast — not exported, behind a signature permission. |
| `LoriePreferences`' activity and `LoriePreferenceFragment` | The launcher's display settings are one screen in its own settings, written as product copy. Only the store (`PrefsProto`) and the receiver `termux-x11-preference` talks to are kept. |
| `extrakeys/*`, `utils/TermuxX11ExtraKeys`, `utils/X11ToolbarViewPager` | The launcher types into X through its own in-app keyboard and extra-keys row. A second keys bar inside the page would be a second keyboard. |
| `utils/KeyInterceptor` | An accessibility service that captures the meta key. A home screen must not install one on the user's behalf. |
| `utils/ImeHeightProvider` | Insets are the launcher's own; it has a keyboard choreographer already. |

## Deviations in the files that are vendored

- **`LorieHost.primePrefs(Context)` (new)** and `release()` keeping the `Prefs`: the launcher's
  Display page is on the wall — measured, which reads the resolution preference through
  `getPrefs()` — while the display is switched off and no host exists. Upstream's view is never
  in that position, because its activity is the host.
- **`LorieHost` (new)** stands where `MainActivity` did. It carries the exact names the view and
  input classes already use — `getPrefs()`, `prefs`, `handler`, `getInstance()`, `findActivity()`,
  `getLorieView()`, `mInputHandler`, `getRealMetrics()`, `toggleKeyboardVisibility()`,
  `handleKey()`, `setCapturingEnabled()`, `setExternalKeyboardConnected()`, `finish()`,
  `ACTION_CUSTOM`, `ACTION_STOP` — over a plain `ContextWrapper` and a `Callbacks` interface the
  launcher implements. Keeping the names is what makes a nightly merge stay a merge.
- **`LorieView`**: `MainActivity` → `LorieHost`, and its `activity` field is nullable and no
  longer final. Upstream can never see a null host — its activity exists for the life of its
  process — but the launcher inflates this view as a page of the pane wall, which can happen
  before a host exists and can outlive one, and a null dereference here is the home screen
  falling over. `LorieHost.setLorieView` hands the host back to the view so construction order
  does not matter. The one behavioural cut is the composing-text path's call into upstream's
  additional-keys bar, which is not vendored.
- **`input/TouchInputHandler`, `input/InputEventSender`**: `MainActivity` → `LorieHost`. Three
  gesture/notification actions lose their target — "toggle additional key bar" and "open
  preferences" become no-ops, and the notification's "restart activity" is gone — because they
  were upstream's own chrome.
- **`utils/SamsungDexUtils.dexMetaKeyCapture`** takes a `Context` instead of an `Activity` and
  resolves the component name itself; there is no activity behind the page.
- **`LoriePreferences`** is a plain container for `PrefsProto` and `Receiver`. Its
  `ACTION_PREFERENCES_CHANGED` constant is kept and made public, and the string the command-line
  `main` broadcasts is the new public `ACTION_CHANGE_PREFERENCE`: the launcher's manifest
  declares the vendored `Receiver` for it (not exported, signature permission — the same guard as
  the server's announcement), and `X11DisplayHostController` listens for the changed broadcast to
  re-read the store into the live view, which is what upstream's `MainActivity` did. The
  `enableAccessibilityServiceAutomatically` and `extra_keys_config` branches of the receiver's
  setter are gone with the features they set. `PrefsProto.IntPreference` gains the `put(int)` its
  boolean and string siblings already have, for the launcher's settings page.
- **`res/xml/preferences.xml`** loses upstream's "main" screen — the rows that navigated into the
  four sections and the version row — because those titles only existed for the dropped activity,
  and AAPT links a resource file whether or not anything shows it. The store's schema, which is
  what `:x11-server:generatePrefs` reads, is untouched.
- **`build.gradle`** is ours: our compileSdk/minSdk/Java 11, `BuildConfig.APPLICATION_ID` from
  the edition's applicationId (the server broadcasts to and class-loads *our* APK), and the
  `generatePrefs` task carried over so the store and its schema can never disagree.
- **`ci/x11-patch/0001-look-up-the-host-class-as-LorieHost.patch`** is the one native change.
  The JNI surface names the host class and the `LorieView` field that holds it — the server does
  `FindClassOrDie("com/termux/x11/MainActivity")` and reads `activity` as that type — and nothing
  type-checks either, so the abort is at runtime, on the first `LorieView`. Two string literals
  become `LorieHost`; `clientConnectedStateChanged()` and the view's `resetIme()` keep their names
  and signatures. The rest of the JNI surface (`LorieView`, `CmdEntryPoint`) is untouched.
- **`stub/`** is upstream's `shell-loader/stub` — compile-only declarations of the hidden
  framework classes `CmdEntryPoint` reaches for while it runs outside an app process.
- Only `res/values/arrays.xml` and `res/xml/preferences.xml` are vendored from upstream's
  resources; its strings, styles, layouts, icons and the accessibility-service config belong to
  the app it was.

## `loader/`

Upstream's `shell-loader`, vendored the same way: the few kilobytes of dex that
`$PREFIX/bin/termux-x11` puts on `app_process`'s CLASSPATH, which finds the launcher's APK,
checks its signature against a hash baked in at build time, class-loads its dex and calls
`CmdEntryPoint.main`. It is never installed. Deviations: our compileSdk/minSdk/targetSdk (the
shared-user SELinux rule in AGENTS.md applies to it too), the package and signature it trusts are
the edition's own, and its error strings say what a launcher user would need to hear. The
`termux-x11-nightly` apt package cannot serve here at all — it bakes `com.termux.x11` into both
the script and the signature check.

Upstream's "is it installed" and "does the signature match" checks are Java `assert` statements.
ART runs `app_process` without `-ea`, so they never executed and any APK with the right
applicationId was class-loaded. Ours are explicit checks that print upstream's error text and exit
non-zero (`Loader.java`); the `AssertionError` catch went with them.

`app/build.gradle` packs the built APK into the launcher's assets as `assets/x11/loader.apk`, and
`X11CliInstaller` writes it and the two scripts into the prefix.

## Merging a newer upstream

1. `git -C <termux-x11 checkout> diff <pinned>..<new> -- lorie/src/main/java lorie/src/main/res/xml lorie/src/main/res/values/arrays.xml lorie/src/main/aidl`
2. Apply what touches the vendored files, re-doing the renames above (`MainActivity` →
   `LorieHost` is the only mechanical one).
3. Bump the commit in this file *and* `version.gradle` *and* the workflow, dispatch
   `build_x11_native.yml`, and commit the new `libXlorie.so` prebuilts from the same run.
4. `./gradlew :x11-server:assembleDebug :app:assembleDebug` and check the Display page on a
   device — the JNI surface between `CmdEntryPoint`/`LorieView` and the native library is not
   type-checked by anything.

## Two things that only fail at runtime

Both were found by running it, not by reading it, and both are cheap to break again:

- **The loader must be read-only in the prefix.** ART refuses a writable dex file on `CLASSPATH`
  ("Writable dex file ... is not allowed") and aborts `app_process` before `Loader.main`, so
  `X11CliInstaller` clears the write bit after copying it out.
- **The server needs the XKB data or it exits before opening a port.** It comes from the
  `xkeyboard-config` package, and upstream only knows how to find it under `com.termux`'s own
  prefix, so the generated `termux-x11` points `XKB_CONFIG_ROOT` at this edition's. The Display
  page says what to install when it is missing.
