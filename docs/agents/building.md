# Building, installing and CI

Run everything from the repository root with the checked-in wrapper. Builds need an Android SDK
and a JDK compatible with AGP 8.13.2; the code targets Java 11.

## Gradle tasks

- `./gradlew :app:assembleStaging`: what goes on a phone and into nightlies. It has the release
  build's R8 shrinking and keep rules, but it is debuggable (`run-as` works) and signed with the
  published test key. Debug builds skip R8, so a hole in the keep rules only shows in a shrunk
  build; 1.0.0 shipped one.
- `./gradlew assembleDebug` / `:app:assembleDebug`: unshrunk debug APKs, for fast local iteration.
- `./gradlew testDebugUnitTest`: JVM unit tests across modules, matching CI.
- `./gradlew :app:connectedDebugAndroidTest`: instrumentation on a connected emulator or device.
- `./gradlew lintDebug`: Android lint.
- `./gradlew :app:verifyReleaseHardening`: release build safety settings.

## Installing

`scripts/dev-install.sh` builds and installs the arm64 staging APK, fresh or as an upgrade.
Target the device with `DEVICE=<serial>`; `BUILD_TYPE=debug` installs the debug APK instead. It
keeps only arm64-v8a, signs with the test key every build shares, installs with
`--no-incremental` and checks the launcher came back up. For pong, where the network link is slow,
the host's `ad -s pong install <apk>` sends the APK through the device-drops route instead of adb.

`adb -s <serial> shell run-as <pkg> …` reads and edits the app's files on a debug or staging build.

## What runs on pong

Install on pong only when the developer asks.

1. **Day to day**, pong runs a **staging** or **diagnostic** build (staging plus a branch's
   temporary logging commits, which are never merged), named `<versionName>+dev.<sha>` so
   tlstore's `min-launcher` gating sees the same version string a release dev build shows.
   A diagnostic build stays on pong: after a merge, build the next one on top rather than swapping
   a clean build in.
2. The **release** build goes on only just before an update ships to users, as the final check of
   the candidate commit: `BUILD_TYPE=release DEVICE=<pong> scripts/dev-install.sh`. Never
   `adb install` a release APK by hand: a local release build has no ABI splits (one ~100 MB
   universal APK) and is unsigned unless the CI's `TERMUX_RELEASE_*` variables are set, which
   fails as `INSTALL_PARSE_FAILED_NO_CERTIFICATES`.
3. A **shipped** build is the CI artifact from the GitHub release, installed through Obtainium.
   Only that exercises the real signing key, the ABI splits and the bootstrap download.

Staging is enough for development because it differs from release only in `debuggable` and the
signing key. The user bugs that never showed on pong came from fresh installs, upgrades across
migrations, other densities and font scales and other editions; those are checked on the emulator
per drop (`releasing.md`).

## CI

When the machine is busy, let CI build. A push to `dev` runs "Build nightly"
(`.github/workflows/debug_build.yml`: `testDebugUnitTest`, then `assembleStaging`):

```
gh run list --repo PickleHik3/termux-launcher --branch dev
gh run view <id> --repo PickleHik3/termux-launcher --log-failed
```
