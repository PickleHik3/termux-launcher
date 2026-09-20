# Building the launcher on the phone

The launcher can be built from a Termux session on the device it runs on. This record exists
because the route is not obvious: Google ships the NDK and the build-tools host binaries as
**linux-x86_64 ELF only**, so `sdkmanager --install ndk` gets you a toolchain that cannot execute,
and the failure it produces later is `Exec format error` a long way from its cause.

AGENTS.md owns the build commands and everything about the branch and release model; nothing here
restates them. What follows is only what is specific to an aarch64 Android host.

## Recommended minimum specs

Measured on the reference device: 8 cores, 10 GB RAM, 4 GB swap, Android 16 (API 36), aarch64.

| | Minimum | Comfortable | Why |
|---|---|---|---|
| Architecture | aarch64 | aarch64 | The prebuilt toolchains exist for nothing else. 32-bit ARM cannot host the LLVM cross toolchain. |
| Android | 9 (API 28) | 12+ | Lower bound is what the termux-ndk builds support. |
| RAM | 6 GB | 8 GB+ | See [Parallelism](#parallelism-is-the-thing-that-bites). Below 8 GB, treat `--max-workers=1` as mandatory, not advice. |
| Swap | 2 GB | 4 GB | The native link peaks hard and briefly. |
| Free storage | 12 GB | 20 GB+ | Breakdown below. |
| Termux | F-Droid or GitHub build | — | Play-store builds are too old. |

Storage, as actually measured:

| | |
|---|---|
| `~/android-sdk` | 2.5 GB — of which NDK 1.8 GB, platforms 287 MB, build-tools 190 MB |
| `~/.gradle` | 1.1 GB — wrapper distribution and the dependency cache |
| `app/build` | 570 MB — five debug APKs, 118 MB of which is the universal one |
| `app/src/main/cpp` | 113 MB — the four cached `bootstrap-*.zip` |
| Installer tarballs | 950 MB — deletable after setup |

A phone that only ever builds `arm64-v8a` needs less, but the module `ndk` blocks list all four
ABIs, so an unmodified debug build compiles all four regardless of what the host needs.

Build times on that device, `:app:assembleDebug --max-workers=1`:

| | |
|---|---|
| Cold — every `build/` wiped, all eight per-ABI native builds re-run | **1 min 20 s** |
| No-op re-run, nothing changed | ~10 s |
| First ever build | add the one-off downloads: Gradle distribution, the dependency cache, and 118 MB of bootstraps |

That is fast enough that a phone is a real development environment for this project, not a
curiosity. The Gradle daemon staying warm is most of it.

## Toolchain

The aarch64 builds come from [lzhiyong/termux-ndk][1], statically linked against musl with Zig.
Everything Google ships that is pure Java — the platform `android.jar`, `d8`, `apksigner`, `r8`,
the AGP jars — runs unmodified. The job is only to replace the native binaries.

[1]: https://github.com/lzhiyong/termux-ndk

| Piece | Version | Source |
|---|---|---|
| JDK | OpenJDK 21 | Termux package `openjdk-21`, matching CI's `java-version: 21` |
| Gradle | as pinned | the checked-in `./gradlew`, which bootstraps itself |
| NDK | 29.0.14206865 | termux-ndk `android-ndk-r29-aarch64` |
| SDK platform | android-36 | termux-ndk `android-sdk-aarch64` |
| build-tools | 35.0.0 (hybrid, below) | Google r35 + aarch64 host tools |
| platform-tools | 35.0.2 | termux-ndk `android-sdk-aarch64` |

The NDK revision is not luck: `gradle.properties` pins `ndkVersion=29.0.14206865`, and
termux-ndk's r29 release *is* that revision — so the on-device toolchain is the one CI uses.

Termux's own native ports are worth installing alongside for direct command-line use:
`aapt aapt2 aidl apksigner d8 zipalign android-tools ninja`.

## Layout

```
$HOME/android-sdk/
├── ndk/29.0.14206865/     # renamed from android-ndk-r29 so AGP resolves it by version
├── platforms/android-36/  # compileSdkVersion
├── build-tools/35.0.0/    # the revision AGP asks for
├── build-tools/37.0.0/    # native, the donor for the swap below
├── platform-tools/
├── cmdline-tools/latest/  # bin/ and lib/ moved under latest/ per SDK convention
└── licenses/              # pre-accepted, so sdkmanager never prompts
```

Inside the NDK, `toolchains/llvm/prebuilt/linux-aarch64` is a symlink to `linux-x86_64`, and that
directory holds aarch64 binaries despite its name. That is what lets `ndk-build`'s host detection
work unpatched — do not "fix" it.

## The three workarounds

### 1. build-tools 35.0.0 ships x86-64 host tools

AGP demands exactly revision `35.0.0` (`Failed to find Build Tools revision 35.0.0`), and the
aarch64 tarball only carries 37.0.0. Rather than mislabel a version, `build-tools/35.0.0` is
Google's genuine r35 — correct jars, `core-lambda-stubs.jar`, `source.properties` — with the six
host executables AGP actually runs copied in from the native 37.0.0 tree:

```bash
for t in aapt aapt2 aidl dexdump split-select zipalign; do
    cp -f $ANDROID_HOME/build-tools/37.0.0/$t $ANDROID_HOME/build-tools/35.0.0/$t
done
```

`apksigner` and `d8` are shell scripts over jars and need nothing. The RenderScript leftovers
(`llvm-rs-cc`, `bcc_compat`, `lld`, `renderscript/`) are still x86-64 and are deliberately left
alone: AGP 8 removed RenderScript compilation, and the launcher only uses the *framework*
`android.renderscript` classes in `AndroidStockBlurImpl`, which compile no `.rs` and invoke no host
tool.

### 2. AGP fetches aapt2 from Maven, not from build-tools

Swapping the binary under `build-tools/` is not enough on its own. AGP resolves aapt2 as the Maven
artifact `com.android.tools.build:aapt2:<ver>-linux`, published for x86-64 only. In
`~/.gradle/gradle.properties`:

```properties
android.aapt2FromMavenOverride=/path/to/android-sdk/build-tools/35.0.0/aapt2
android.builder.sdkDownload=false
```

`sdkDownload=false` is a guard, not a preference. Left on, AGP will helpfully download x86-64
build-tools over the working ones, and builds that worked yesterday start failing with
`Exec format error` for no visible reason.

### 3. `ndk.dir` is deprecated

`local.properties` should carry `sdk.dir` only. Setting `ndk.dir` also works, but prints the
`[CXX5106]` deprecation warning once per native module; AGP resolves the NDK from `ndkVersion`
against `$ANDROID_HOME/ndk/<version>` instead.

## Environment

```sh
JAVA_HOME=$PREFIX/lib/jvm/java-21-openjdk
ANDROID_HOME=$HOME/android-sdk        ANDROID_SDK_ROOT=$ANDROID_HOME
ANDROID_NDK_HOME=$ANDROID_HOME/ndk/29.0.14206865   # also _ROOT and bare ANDROID_NDK
PATH=$PATH:$ANDROID_NDK_HOME:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

Two notes for anyone doing this on a launcher device:

- **Put the fish copy in `~/.config/fish/conf.d/`, not `config.fish`.** `setup-launcher` replaces
  `config.fish` on every run; `conf.d/*.fish` survives it.
- **Keep the NDK's own `clang` off `PATH`.** It shadows Termux's `clang` for ordinary work, and
  `ndk-build` finds its toolchain through `$ANDROID_NDK_HOME` regardless.

## Parallelism is the thing that bites

Use `--max-workers=1` on a phone. At `--max-workers=3` the build dies in
`:termux-shared:buildNdkBuildDebug[x86_64]` with

```
clang++: error: unable to execute command: Segmentation fault
clang++: error: linker command failed due to signal
```

That is not a broken toolchain — the identical `ndk-build` command run on its own links fine. It is
memory. AGP runs the four per-ABI `ndkBuild` tasks concurrently and each runs `make` across every
core, so three workers meant roughly twenty clang processes beside a 2 GB Gradle heap on a device
with 2.8 GB free and swap at 3.4 of 4 GB. `lld` was the process that lost.

`termux-shared` is the module that falls over because it is the only C++ one and links libc++
statically (`APP_STL := c++_static`); `terminal-emulator` is plain C and builds all four ABIs
without complaining. If a native link does segfault, re-run — the build is incremental and every
ABI that already linked is cached.

AGENTS.md's advice to stop the Gradle daemon before memory-hungry work applies doubly here.

## Other phone-specific notes

- The first debug build downloads four `bootstrap-*.zip` into `app/src/main/cpp/`, one per ABI.
  They are checksum-verified and cached; only `./gradlew clean` deletes them. They are not packaged
  into the debug APK — the prefix is provisioned on first run — but `downloadBootstraps` fetches and
  verifies them as part of every build.
- Debug builds default to split APKs: four per-ABI plus a universal.
  `TERMUX_SPLIT_APKS_FOR_DEBUG_BUILDS=0` gives the universal alone, though native code is still
  compiled for all four ABIs either way.
- `-Px11Server=false` drops the embedded X server and its `libXlorie.so` prebuilts.
- `adb` is packaged and works, but on a single device the simpler route is to open the APK with the
  package installer. Remember the launcher **is** the home screen and shares a `sharedUserId`: a
  debug build signed with `testkey_untrusted.jks` will not install over a release-signed one.
  `scripts/dev-install.sh` builds the smaller upgrade-only APK for an existing install.
