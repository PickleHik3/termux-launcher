# Ship the release build type, not debug APKs

Status: **experiment, untested on a real device.** Nothing in CI or on any release branch has
changed. Test on a phone before the next release; decide then.

## What we ship today

- `.github/workflows/attach_debug_apks_to_release.yml` runs `:app:verifyReleaseHardening` and
  `:app:processReleaseMainManifest` (checks on the *release* build type), then builds and uploads
  `assembleDebug` output from `app/build/outputs/apk/debug`. The hardening checks never touch what
  is shipped.
- v0.2.39 ("Latest"), `termux-app_v0.2.39+apt-android-7-github-debug_arm64-v8a.apk`:
  `aapt2 dump badging` reports `application-debuggable`.
- The 1.0.0 dev build on pong: `dumpsys package com.termux` → `flags=[ DEBUGGABLE HAS_CODE
  ALLOW_CLEAR_USER_DATA ALLOW_BACKUP LARGE_HEAP ]`.

What that costs users:

- **Speed.** A debuggable app runs without its precompiled code (JIT/interpreter, JDWP on), and
  the debug type skips R8. In a pong cold-start trace (2026-10-06) the JIT threads were busy for
  2.1 s of the first 3.5 s; the main thread needed ~2 s to reach the first frame.
- **Data exposure.** The debug type sets `allowBackup=true` / full backup content, and
  debuggable apps allow `run-as`. Anyone with adb access to a user's phone can copy out `$HOME`,
  `$PREFIX`, keys and shell history.

More from the same trace: `base.apk` is dexopted with `filter=verify` (interpreter + JIT only),
`EnableDebugFeatures` suspends threads for 27 ms at process start, and CheckJNI is on. A
non-debuggable build with a baseline profile (androidx.profileinstaller) commonly cuts this kind of
startup by 30–50% — an estimate, not a measurement. A cheap way to see the AOT share on a test
phone without a release build: `adb shell cmd package compile -m speed -f <pkg>`, then a
cold-start trace (a debuggable app may still ignore it; compare).

## The experiment

Branch `exp/release-buildtype` (worktree `.claude/worktrees/exp-release`), commit `065dbc3e8`, off
`feat/icon-pack-glyph-rev2`. Only `app/proguard-rules.pro` changed:

- The release type had not built since the X11 server landed: R8 failed on hidden platform
  classes `CmdEntryPoint` links against. Seven `-dontwarn` rules fix that.
- `-dontobfuscate`, `-keep class com.termux.** { *; }`, `-keep class juloo.keyboard2.** { *; }`:
  the app's own code stays whole and unrenamed, because much of it is reached by name from
  outside the dex (app_process entry points, JNI, Shizuku user services, class names in intents
  and prefs). R8 trims only third-party libraries. The goal is the non-debuggable runtime, not a
  smaller dex; tighten the rules later if size matters.

Signing is unchanged: the release type takes its key from `TERMUX_RELEASE_STORE_FILE`,
`TERMUX_RELEASE_STORE_PASSWORD`, `TERMUX_RELEASE_KEY_ALIAS`, `TERMUX_RELEASE_KEY_PASSWORD`.
Pointing them at `app/testkey_untrusted.jks` (alias `alias`, the password in
`scripts/dev-install.sh`) gives the same certificate as every published APK and every companion
app (SHA-256 `b6da0148…`), so it installs over a debug build without an uninstall and keeps the
`sharedUserId` pairing.

Build it locally:

```sh
cd .claude/worktrees/exp-release
TERMUX_RELEASE_STORE_FILE=$PWD/app/testkey_untrusted.jks \
TERMUX_RELEASE_STORE_PASSWORD=<from scripts/dev-install.sh> \
TERMUX_RELEASE_KEY_ALIAS=alias \
TERMUX_RELEASE_KEY_PASSWORD=<from scripts/dev-install.sh> \
./gradlew :app:assembleRelease            # add -PsplitAPKsForReleaseBuilds=1 for per-ABI APKs
# → app/build/outputs/apk/release/termux-app_apt-android-7-release_universal.apk (~100 MB)
```

## Waydroid results (2026-10-06, API 33 x86_64)

- Installed over the debug build; data kept; not debuggable (`pkgFlags=[ HAS_CODE
  ALLOW_CLEAR_USER_DATA LARGE_HEAP ]`).
- Worked: launcher and terminal (commands, prefix intact), `tai --json runtime` (API server and
  the `:tai_runtime` service start), Settings, Appearance, On-device AI, Model centre. No
  ClassNotFound / NoSuchMethod / NoSuchField in logcat.
- Cold start (`am start -W` after force-stop, three runs each): **~4.0 s release vs 5.0–5.3 s
  debug.** Waydroid's software GL is most of both numbers; a real GPU should show a larger share.

## Before the next release: test on a real phone

Install the release-type APK over the current debug build (same key, so `adb install -r`), then:

1. Cold start: `adb shell am force-stop com.termux; adb shell am start -W -n
   com.termux/.app.TermuxActivity`, three runs, against the same with the debug build. For the
   breakdown, a perfetto trace with atrace categories am wm view gfx dalvik and
   `atrace_apps: "com.termux"`.
2. Terminal, sessions, split panes, kitty graphics, the in-app keyboard, voice input.
3. **X11 / `tlstore display`** — the X server is started through app_process by class name; the
   riskiest path for R8.
4. **The privileged lane** (`tl-priv`, Shizuku user service `PrivilegedLaneService`).
5. Companion apps (`…api`, `…styling`, `…boot`) still pair: same `sharedUserId`, same key, and
   `termux-api` calls work.
6. On-device AI: load a model, `tai` CLI, dictation cleanup, the category sort.
7. launcherctl API, notifications, widgets, wallpaper picker, icon packs.
8. Each edition (`com.termux`, `com.termux.launcher.nix`, `io.vaj.tl`) at least installs and
   reaches the terminal.
9. Going back: a debug build installs over the release one (same key), for bisecting.

## If it holds up: the change

- Merge `app/proguard-rules.pro` from `exp/release-buildtype` into `dev`.
- In `attach_debug_apks_to_release.yml`: export the four `TERMUX_RELEASE_*` variables pointing at
  the testkey (no CI secrets needed — it is the published test key), build
  `./gradlew :app:assembleRelease -PsplitAPKsForReleaseBuilds=1`, read APKs from
  `app/build/outputs/apk/release`, keep the `termux-app_v<versionName>_<edition>_<variant>_<abi>.apk`
  names. The workflow file lives on each edition branch, so it reaches `main`, `nix-edition` and
  `io-vaj-package` at the next cut, with the identity rules in AGENTS.md.
- Keep nightlies (`debug_build.yml`) and dev installs on the debug type: agents and the developer
  rely on `run-as` on test devices.
- Mention it in the release notes only as a user-visible effect ("starts faster"); no mechanism.
