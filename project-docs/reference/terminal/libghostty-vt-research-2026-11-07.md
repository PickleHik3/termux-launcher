# libghostty-vt 1.0 against our terminal core (2026-11-07)

Question: what would adopting libghostty-vt 1.0, Ghostty's standalone terminal-emulation library,
mean for the launcher? Read-only research. The developer asked for no verdict, so none is given:
this note lays out what the library is, what it would change and cost, and what is still unknown.
Nothing here was built or run on a device, and no code was changed. Claims about the library come
from Ghostty's own repo, docs and the author's posts; claims about our emulator come from reading
`terminal-emulator/`. Items marked (reasoned) are inferences, not measurements. Anything the
sources did not settle is in "Open questions". A second pass on 2026-11-07 read the source at tag
`vt-1.0.0` directly (shallow clone, nothing built or run); "Source" paths below are in that tree.

## What libghostty-vt is

- A C library, written in Zig, that is Ghostty's terminal core without the app: VT parser, terminal
  state, primary and alternate screens, scrollback, selection, search, and encoders for key, mouse,
  focus and paste input. It draws nothing and owns no pty
  (`include/ghostty/vt/terminal.h`: the terminal "doesn't draw or manage a pty, so the caller
  handles both").
- Versioning: the first tag is `vt-1.0.0`, dated 2026-11-06, and `0.1.0` was skipped and never
  released (PR #15126, merged 2026-11-06). Correction: the GitHub releases API for the repo lists
  only the rolling `tip` release, so `vt-1.0.0` is a tag, not a GitHub Release; no changelog text
  exists there. Releases are published only at `release.files.ghostty.org/libghostty-vt/VERSION/`
  (`PACKAGING-LIBGHOSTTY.md`).
- The library has its own version, `lib_version` in `build.zig` (`"1.0.0-dev"` on the branch,
  overridden by a `LIB_VERSION` file in release tarballs or by the `vt-X.Y.Z` tag;
  `src/build/Config.zig` `libVersion`), independent of the app version (`PACKAGING-LIBGHOSTTY.md`).
- Stated promise at 1.0: semantic versioning and a "real, enforced ABI stability policy"
  (Hashimoto, "Libghostty 1.0").
- License MIT, "no copyleft code anywhere in the dependency tree" (same post).
- It is a separate release cycle from the Ghostty app. The app's 1.4.0 notes (2026-10-27) still
  called the API "a work in progress with ongoing breaking changes", so the stability promise is
  nine days old at the time of writing.

## Comparison with the current Java emulator

| Area | Our `terminal-emulator/` | libghostty-vt 1.0 |
|---|---|---|
| Language, size | Java, `TerminalEmulator.java` 5416 lines, `TerminalBuffer.java` 1463, `TerminalRow.java` 626, plus kitty files (~6 k lines) | Zig core behind a C ABI; 35 public headers under `include/ghostty/vt/` (37 under `include/`) |
| Cell storage | `TerminalRow[] mLines` ring (`TerminalBuffer.java:24`); per row a `char[] mText` (`TerminalRow.java:46`) and `long[] mStyle` (`:60`); side tables only for rows that need them | Page-based storage with a byte budget; idle scrollback pages can be LZ4-compressed and their physical memory released (Linux 64-bit, macOS) |
| Scrollback | Row count, 100 to 50000, default 2000 (`TerminalEmulator.java:404-408`) | Byte budget (`SCROLLBACK_MAX_BYTES`, default 10,000 bytes rounded up to pages in the header) and/or line budget (`SCROLLBACK_MAX_LINES`, none by default); pruned at page granularity |
| Reflow on resize | Yes (`TerminalBuffer.java:405` `resize`, called from `TerminalEmulator.java:1239`) | Primary screen only, only with wraparound on; alt screen does not reflow (`terminal.h`) |
| Synchronized output 2026 | Yes (`TerminalEmulator.java:321, 907, 2649`) | Yes, mode 2026, surfaced as a `RENDER_HOLD` callback (`modes.zig`, `terminal.h`) |
| Grapheme clustering | Via Android ICU `BreakIterator`, no DECSET 2027 case found (`GraphemeClusterer.java:1-12`) | DECSET 2027 `grapheme_cluster` mode; compact Unicode tables (`modes.zig`; post) |
| OSC 8 hyperlinks | Yes (`TerminalEmulator.java:2562, 3756`, `TerminalHyperlinks.java`) | Yes (docs `/docs/vt/external`) |
| Kitty keyboard | Yes, push/pop/set (`TerminalEmulator.java:173-175, 702-770`, `KittyKeyEncoder.java`) | Yes. Parser handles CSI `> u` push, `< u` pop, `= u` set/or/not, `? u` query; per-screen stack of 8 entries (`src/terminal/stream.zig:2745-2815`, `src/terminal/kitty/key.zig:6-60`, `Screen.zig:75`); see Compatibility |
| Kitty graphics | `KittyGraphicsProtocol.java` (2103 lines), `KittyImageStore.java`; animation, Unicode placeholders, delete forms; file/shm media excluded (`kitty-protocol-features.md`) | Parsed and stored inside the library; "100% complete against the published spec" including animation and relative placements (1.4.0 notes); default storage cap 10 MB (`terminal.h`); pixels handed to the host as RGBA. Unicode placeholders: yes (`kitty/graphics_unicode.zig`, surfaced as `KITTY_PLACEMENT_KIND_VIRTUAL` in `render.h`). Media: direct always; file, temp-file and shared-memory off by default and enabled by the host (`terminal.h` `KITTY_IMAGE_MEDIUM_*`); shared memory returns unsupported on Android (`kitty/graphics_image.zig:206`) |
| Sixel | `TerminalSixel.java` | Not handled: `dcs.zig` routes only XTGETTCAP, DECRQSS and tmux control mode; other DCS is ignored |
| iTerm2 inline images (OSC 1337) | `ITermImage.java` | No. OSC 1337 is parsed but `File`, `MultipartFile` and the other keys are logged "unimplemented OSC 1337" and dropped; only `CurrentDir` becomes a pwd report (`src/terminal/osc/parsers/iterm2.zig:150-190`) |
| Kitty text sizing (OSC 66), kitty notifications (OSC 99), kitty clipboard 5522 | `KittyTextSizing.java`, `KittyNotifications.java`, `KittyClipboard.java` | 5522 paste events: yes (mode 5522; also `kitty/clipboard*.zig`). OSC 66 and OSC 99: parsed (`osc/parsers/kitty_text_sizing.zig`, `kitty_desktop_notification.zig`) but the stream drops them as "unimplemented OSC callback" (`src/terminal/stream.zig:3085-3101`), so no text sizing and no kitty notifications reach the host. The `DESKTOP_NOTIFICATION` callback is fed by OSC 9 and OSC 777 only (`osc/parsers/osc9.zig`, `rxvt_extension.zig`, `stream_terminal.zig:785`) |
| In-band size 2048, colour scheme 2031 | Yes (`TerminalEmulator.java:307, 329`) | Yes (`modes.zig`, `terminal.h`) |
| Threading | Emulation on the main thread; pty reader, writer and waiter are separate threads (`TerminalSession.java:27-28, 202-238`) | Creates no threads; terminal is not reentrant; render-state `begin_update` needs the terminal serialised, `end_update` does not |
| Pty, process spawn | `JNI.java` + `jni/termux.c` (218 lines) | None; stays ours |

## Advantages

- Memory shape (reasoned for our case): a byte-budgeted page store replaces a ring of Java
  `char[]`/`long[]` rows. Upstream measured an empty terminal at about 16 KiB of page memory on
  macOS, down from 128 KiB (1.4.0 notes). Our 185-200 MB idle PSS is dominated by other things
  (the doc's native heap is about 91-108 MB), so how much of it the terminal core owns is not
  measured here.
- Scrollback compression: LZ4 pages, resident memory released with `MADV_DONTNEED`; the PR
  corpus encoded to 6.11% of raw size, the author expects 10-30% for ordinary text, and the title
  claims 70-90% savings (PR #13264). In libghostty-vt it is opt-in: the host calls
  `ghostty_terminal_compress` (incremental mode for idle callbacks) and can read
  `ghostty_terminal_compression_activity` to schedule it. `compression_supported` in the memory
  usage struct says whether it can work on the build (see Compatibility for Android).
- Memory accounting exists in the API: `GhosttyTerminalMemoryUsage` (virtual, resident, compressed
  bytes, kitty image bytes) via `ghostty_terminal_get`.
- Kitty graphics decode (PNG, zlib) happens inside the library with SIMD payload parsing, "about
  16x faster" on 64 MiB of image data on x86_64 Linux (1.4.0 notes).
- Zero Java allocation per byte parsed: the parse path would be native. Our parser allocates per
  escape string today (`mOSCOrDeviceControlArgs.toString()`, `TerminalEmulator.java:2297`);
  (reasoned) a native core removes that class of garbage, though a JNI boundary adds its own.
- Fuzzing and breadth of use: JetBrains terminals, Neovim (approved PR), KDE and others embed it
  (post). That is a larger test population than a fork-local Java parser has.
- Render-state dirty tracking (`DIRTY_PARTIAL`, `row_iterator_next_dirty`, stable row ids) is a
  ready-made source for per-row redraw decisions.
- Snapshot API: encode and restore a whole terminal (scrollback and kitty pixels) as bytes
  (`snapshot.h`). The byte format is "versioned separately from the library and isn't covered by
  the ABI promise".

## Disadvantages

- Feature gaps against what we ship: sixel, iTerm2 images, OSC 66 text sizing and OSC 99
  notifications have no equivalent in the library at `vt-1.0.0` (see table; sixel and iTerm2 are
  dropped, OSC 66 and 99 are parsed then ignored). Each would stay in Java, which means a second
  parser in front of or beside the library, or dropping the feature.
- Shared-memory kitty image transmission is unsupported on Android in the library; file and
  temp-file media exist but are off until the host enables them (we exclude file and shm media
  today, so this is parity, not a loss).
- No link detection in the C API. The regex-link facility (`StringMap`, with a default URL and
  path regex in `src/config/url.zig`) is Zig-only (`src/lib_vt.zig:94`), needs a regex build
  (`StringMap.zig:33`, oniguruma is not in the libghostty-vt dependency manifest) and has no C
  header. Hosts get OSC 8 URIs per cell (`ghostty_grid_ref_hyperlink_uri`, `grid_ref.h:190`) and
  the formatter and selection for text, so `UrlDetector` stays ours.
- Our fork-local work does not move over. The kitty layer (`KittyGraphicsProtocol`,
  `KittyImageStore`, `KittyNotifications`, `KittyClipboard`, `KittyTextSizing`, placeholders) is
  built on `TerminalEmulator` internals and the `mStyle` bit layout (bit 15 `BITMAP`; the note
  says bit 14 is the last free one). Rendering in `terminal-view/` reads `TerminalRow` directly
  (not read for this note).
- `KittyImageStore`'s budgets (32 MB / 256 images stored; frames RAM/48, 64-256 MB, per
  `docs/agents/performance.md`) would be replaced by the library's own cap
  (`KITTY_IMAGE_STORAGE_LIMIT`, default 10 MB) plus whatever the host keeps as textures.
- Pointer lifetimes: render-state rows, kitty pixel pointers and image handles are invalid after
  the next update or any mutation, including `animation_tick` (`render.h`, `kitty_graphics.h`).
  Every host read must be copied or consumed inside the lock window.
- JNI crossing cost per frame is unknown; the per-cell getters are per-call (the API offers
  `get_multi` and `CELLS_RAW` "which helps when calls are expensive, such as from WebAssembly").
- A native core in a process that is the home screen: a native crash takes the launcher down, not
  one pane, and Java exceptions no longer catch parser faults.
- Bold-as-bright and similar presentation choices are left to the host (`render.h`).
- Fork surface: `terminal-emulator/` and `terminal-view/` are "shared with upstream Termux" per
  `kitty-protocol-features.md`; replacing the core moves them further from upstream.

## Efficiency: CPU, memory, allocations

- Published numbers are Ghostty-app numbers, not libghostty-vt on Android:
  plain ASCII parsing 128 MB/s to 725 MB/s and `cat` of a 150 MB file 1.5 s to 566 ms
  (macOS-centred, 1.4.0 notes); resize with large scrollback about 6x faster; OSC 52 parse about
  30x faster. No Android or arm64 phone figure was found.
- Hashimoto posted a libghostty versus `alacritty_terminal` memory comparison (empty, full screen,
  10K-row scrollback of plain, unicode, styled and mixed text) from one Rust binary embedding both.
  The tweet gives no tables and says the long-form write-up is pending; compression-on figures
  include a feature Alacritty lacks, and uncompressed figures were also posted. I could not read
  the numbers.
- SIMD: simdutf and Google Highway, enabled by default except WebAssembly and freestanding
  (`PACKAGING-LIBGHOSTTY.md`). `-Dsimd=false` is "much slower". Whether the Android arm64 build
  uses NEON through these is not stated; the Ghostty discussion says using the NDK "enables SIMD,
  which may improve performance" (discussion #10902).
- Allocation: "allocates only through a customizable allocator" and creates no threads (post).
  `ghostty_terminal_new` has a custom allocator parameter; whether a bionic build defaults to
  `malloc` is stated only as "default `malloc` allocator" needing libc.
- Compression CPU, from the PR benchmark: about 96-101 microseconds per page to compress, about
  26 microseconds per page to restore, run only after 250 ms of idle.
- Our side, for reference: `docs/agents/performance.md` lists typing frame p50 15 ms, heavy output
  median frame 5 ms, glyph-heavy pane render median 2.4 ms and about 336 KB/s allocated. These
  include rendering; the emulation share is not broken out, so a before/after would need to
  isolate parse+state from draw.

## Scalability

- Scrollback size: a byte budget with page-granular pruning, and optional compression, versus our
  fixed row-count ring with Java arrays per row. Upstream raised its own default from 10 MB to
  50 MB because compressed history costs less (1.4.0 notes); virtual memory does not fall, only
  resident (PR #13264).
- Many sessions: the author reports 10,000 live terminals on a 4-core 8 GB Linux server (post,
  with `dash`). Not a phone and not our workload.
- Floods: the 1.4.0 notes put parser throughput far above the Java baseline we cannot compare
  directly; on macOS reading and parsing run on separate threads for 25-55% throughput. That is an
  app-level change, not a library one, and the library "creates no threads", so a host doing the
  same split must serialise `vt_write` against render-state `begin_update` itself.
- Freed scrollback memory returns to the OS on macOS and Linux (1.4.0 notes); Android is not named.

## Stability

- API/ABI (post): opaque handles, enum-keyed getters and setters so new values need no new
  symbols, newer headers against older libraries return `GHOSTTY_UNSUPPORTED`, sized or padded
  structs, enums pinned to `int`; ABI checked in CI with libabigail on Linux and golden type
  manifests on macOS and WebAssembly; versioned ELF symbols (`src/terminal/c/ghostty-vt.map`).
  No ABI check on Android is mentioned.
- Not covered by the promise: the snapshot byte format (`snapshot.h`). Anything beyond the C API,
  and the Zig-language API, is not offered.
- Maturity: the tag is two days old. The same repo had a 1.4.0 app release nine days before it
  with "ongoing breaking changes" language for the C API (1.4.0 notes), and 1.0 changed no C API
  and left the ABI goldens untouched (PR #15126), so the stable surface is what 1.4 already had.
  `build.zig.zon` at the `vt-1.0.0` tag reports version `1.4.1-dev` and minimum Zig 0.17.0:
  that is the Ghostty app's version, not the library's (the library's is `lib_version` in
  `build.zig`, see "What libghostty-vt is").
- What the stability promise says and carves out (`dist/doxygen/pages/stability.md`, referenced
  from `include/ghostty/vt.h:10`; the section is a doxygen page, not text in `vt.h`): a 1.x
  minor release may only add functions, types, enum values and fields at the end of sized
  structs; a behaviour change ships as a new function and the old one is marked
  `GHOSTTY_DEPRECATED` until the next major. The one carve-out is the snapshot byte format
  (versioned separately). The page also says the library is "fuzzed via an AFL++ harness on
  multiple dedicated machines 24/7" (project's own claim).
- Adopters named by the author: JetBrains IDE terminals (reported 12.5 million active users),
  Neovim (approved PR, merge awaiting the tag), KDE, others. Self-reported.
- Fuzzing in the repo: `test/fuzz-libghostty/` holds three AFL++ harnesses (`fuzz-osc`,
  `fuzz-parser`, `fuzz-stream`, the last through the read-only stream handler); CI only builds the
  harness (`.github/workflows/test.yml` job `test-fuzz-libghostty`), it does not run it. The
  24/7 runs are the project's statement and are not visible in the repo. Issue #14998 (open)
  says the harnesses leave lazily parsed OSC paths and effect callbacks unexercised; the PR
  behind it widens coverage. No OSS-Fuzz config exists in the tree (grep for "oss-fuzz" found
  nothing). Other checks in CI: a `valgrind` job running `zig build test-valgrind`; ubsan is
  disabled for some C++ deps (PRs #9696, #12381). No ASan or MSan job was found, and no
  Android job runs tests (the Android job only builds, see Compatibility). Antithesis also uses
  the library to fuzz TUIs (post).
- Known Android issues: a GitHub issue search for "android" in the repo returns no open issue
  about libghostty-vt. Android defects have been fixed through PRs: #10925 added the Android
  build and CI; #12341 stopped referencing `shm_open` (the `dlopen` failed with "cannot locate
  symbol shm_open" on bionic); #13174 fixed a build break on `arm-linux-androideabi` because the
  kitty image generation counter used a 64-bit atomic, now a mutex-guarded u64 on 32-bit
  targets. All merged. Zig has no bionic libc support (ziglang/zig#23906, cited in #10902).

## Compatibility and Android

- Official: Android is listed in Tier 1 ("Built and tested in CI") in `PACKAGING-LIBGHOSTTY.md`
  and the post says unit tests and examples for aarch64, x86_64 and arm. Correction to that
  reading: the only Android CI job (`build-libghostty-vt-android`, `.github/workflows/test.yml`,
  NDK r29, targets `aarch64-linux-android`, `x86_64-linux-android`, `arm-linux-androideabi`) runs
  `zig build -Demit-lib-vt` and nothing else, so it proves the build, not the tests. The 16 KB page size is supported: `lib.link_z_max_page_size = 16384` in
  `src/build/GhosttyLibVt.zig`. The NDK is wired in only for Android ABIs
  (`@import("android_ndk").addPaths`, same file; `android_ndk` is a `build.zig.zon` dependency).
- Why the NDK: Zig has no bionic libc support (ziglang/zig#23906); a Zig-only `.so` lacks
  `DT_NEEDED libc.so` and `dlopen` fails on `__tls_get_addr` (discussion #10902).
- Our ABIs are `x86_64`, `armeabi-v7a`, `arm64-v8a` (`terminal-emulator/build.gradle:31`), the same
  three. `minSdkVersion` is 26 (`gradle.properties:29`), `targetSdkVersion` 28. The only Android
  API-level figure found is 24, from a third-party repo (`ancientcatz/libghostty-android`: Zig
  0.16.0, NDK r30, `aarch64-linux-android.24`, `-Dcpu=baseline`), not from Ghostty. Upstream gives
  no minimum API.
- Other prior art, all third party: `JotaVeUltra/ghostty-android` (Kotlin, Compose and a JNI
  bridge), `pohunekd/pohunek-companion-android` issue #11 (reproducible build; two clean builds
  were not byte-identical; JNI left to issue #12). None is an upstream-maintained Android binding.
  Java and Kotlin: the post says "community bindings for ... Java" and points to the
  `awesome-libghostty` list (`Uzaaft/awesome-libghostty`), which names `vlaaad/ghosttyfx` (a
  JavaFX control, MIT, Java 25, Maven Central; platforms Linux, macOS and Windows only, no
  Android) and the Kotlin apps `jossephus/chuchu` (Android SSH client, MIT, pushed 2026-11-06)
  and `charliesbot/umbra` (last push 2026-02-22). Chuchu carries its own Zig JNI bridge and
  Kotlin `GhosttyBridge.kt` and builds with Zig 0.15.2; it is an app, not a reusable binding.
  No maintained Java or Kotlin binding for Android exists upstream or in that list.
- Prebuilt artifacts: the release directory holds source and deps tarballs, an XCFramework, two
  wasm files and the docs zip (`SHA256SUMS` fetched from
  `release.files.ghostty.org/libghostty-vt/1.0.0/`). No Android `.so` or AAR is shipped: an
  Android build is built from source with the NDK.
- Runtime dependency: "only libc" by default, because SIMD paths and the default allocator need it;
  freestanding only with SIMD off (post, corrected from an earlier "not even libc" claim).
- Compression gating (`src/terminal/mem.zig`, `canReclaim`): off unless the pointer width is 64
  bits, then on for `os.tag == .linux` via a raw `madvise(MADV_DONTNEED)` (no libc needed), Darwin,
  Windows and FreeBSD. Zig's `*-linux-android` targets have `os.tag == .linux` (reasoned from
  the Zig target model; not built), so arm64 (`aarch64`) and `x86_64` Android are on, and
  `armeabi-v7a` is off (32-bit) and stays uncompressed. No Android-specific branch exists in
  `mem.zig`. Where off, `ghostty_terminal_compress` returns
  `GHOSTTY_TERMINAL_COMPRESSION_RESULT_UNSUPPORTED` and the memory-usage struct reports
  `compression_supported = false` (`terminal.h:472-479`, `src/terminal/c/terminal.zig:1910`).
  Whether bionic or SELinux policy lets `madvise` through in the app domain was not tested.
  This replaces the earlier "unconfirmed" reading; the PR text naming only "macOS and 64-bit
  Linux" predates the Android build.
- Kitty keyboard flag stack (source, `src/terminal/stream.zig:2745-2815`,
  `src/terminal/kitty/key.zig`): CSI `> flags u` pushes (a missing parameter pushes 0), `< n u`
  pops n (default 1; popping 8 or more resets the stack), `= flags ; mode u` sets with mode 1 set,
  2 or, 3 not-and, `? u` replies `CSI ? flags u` through the WRITE_PTY callback
  (`stream_terminal.zig:1966-1980`). Flags are 5 bits (0-31); a larger value is logged and
  ignored. The stack is 8 deep and kept per screen (`Screen.zig:75`), and a full reset clears
  it (test `fullReset clears alt screen kitty keyboard state`, `Terminal.zig:18443`). The
  encoder takes the current flags as input (`key/encoder.h:39-57`).
- Mouse and links: `ghostty_terminal_get(..., MOUSE_TRACKING)` returns whether any tracking mode
  (X10, normal, button, any-event) is on (`terminal.h:2762`); individual modes and formats (SGR,
  SGR-pixels, urxvt, UTF-8, alternate scroll) are readable as mode bits (`modes.h:63-80`), and
  `ghostty_mouse_encoder_set_from_terminal` copies tracking and format into the encoder
  (`mouse/encoder.h:209`); mouse shape is exposed (`GHOSTTY_TERMINAL_DATA_MOUSE_SHAPE`). No URL
  detection in the C API (see Disadvantages).
- Reflow (`src/terminal/PageList.zig`, `resizeCols` and `reflowRow`): wide cells move with their
  spacer tail; a wide cell that lands in the last column gets a spacer head and wraps; at one
  column, wide characters are replaced by empty narrow cells (`:2180-2215`). Hyperlinks and
  grapheme data are copied per cell into the new page, growing page capacity when needed
  (`:2260-2370`). Kitty unicode placeholder rows keep their placeholder flag (`:1899-1912, 2250`),
  and tracked pins, which kitty placements use, are adjusted during reflow (`:1696`; the
  placement-to-pin link is in `kitty/graphics_storage.zig`, reasoned that placements follow
  their text). Reflow runs only on the primary screen and only with wraparound on
  (`Terminal.zig` resize, reflow = wraparound; alt screen `reflow = false`).
- Our own constraints that touch this: the app keeps `targetSdkVersion` 28 and `sharedUserId`
  (AGENTS.md); a bundled `.so` loaded from the APK is unaffected by the exec-in-`$PREFIX` rule
  (reasoned; not tested).
- Three editions with their own package names (AGENTS.md): a shared native library would need the
  JNI class and method names to follow each edition's package, or be loaded through a thin
  edition-neutral shim (reasoned).

## Performance and SIMD (published benchmarks)

- Source for numbers: Ghostty 1.4.0 release notes. ASCII parse 725 MB/s on the author's macOS
  hardware, kitty payload parse about 16x on x86_64 Linux. No arm64 Android, no phone, no
  libghostty-vt-only benchmark with a harness we can rerun was found.
- Benchmark harness (`src/benchmark/`, `src/build/GhosttyBench.zig`, `src/benchmark/AGENTS.md`):
  built with `zig build -Demit-bench -Doptimize=ReleaseFast` into two executables, `ghostty-gen`
  (synthetic input) and `ghostty-bench --data <file>` (about 15 actions). The ones that matter
  here run the same Zig terminal module libghostty-vt wraps, not its C API: `terminal-stream`
  (full read-only stream handler, parse to state), `terminal-parser`, `osc-parser`,
  `terminal-resize` (column reflow ping-pong), `scrollback-compression`, `page-compression`,
  `terminal-snapshot`, `terminal-formatter`, `screen-clone`. Not libghostty-vt alone: the
  executables link the app's shared dependency set (`SharedDeps`; includes freetype and font
  code), and `CApi.zig` only exposes a benchmark entry for the macOS app. It measures wall time
  per run on a data file; the project advises `hyperfine` and fixed corpora. Nothing in
  `GhosttyBench.zig` or the build mentions Android; the NDK wiring exists only in
  `GhosttyLibVt.zig`, so cross-building the harness for arm64 Android is untested (reasoned:
  it would need the same libc setup). A phone run is therefore possible only by building a
  separate small program against the C API or by getting the Zig harness to cross-build.
- SIMD: simdutf and Highway are vendored and built without libc++ (post). Android uses the NDK
  toolchain for these (discussion #10902; reasoned: that means NEON on arm64).

## Integration cost and shape

What the C API gives the host (headers under `include/ghostty/vt/`):

- Terminal: `ghostty_terminal_new(size)`, `ghostty_terminal_vt_write`,
  `ghostty_terminal_vt_write_until` (stop at ground state or string boundaries),
  `ghostty_terminal_resize`, `ghostty_terminal_reset`, `ghostty_terminal_scroll_viewport`,
  `ghostty_terminal_free`.
- Effects, as callbacks set with `ghostty_terminal_set`: `WRITE_PTY` (replies for the program),
  `BELL`, `TITLE_CHANGED`, `PWD_CHANGED`, `DESKTOP_NOTIFICATION`, `PROGRESS_REPORT`,
  `CLIPBOARD_WRITE` and synchronous `CLIPBOARD_READ`, `SEMANTIC_PROMPT`, `COLOR_SCHEME`,
  `SIZE`, `RESIZE_WINDOW`, `UNKNOWN_SEQUENCE`, `RENDER_HOLD`. They fire synchronously inside
  `vt_write` and must not call write on the same terminal.
- Render state: `ghostty_render_state_update` (or `begin_update` / `end_update`), rows via
  `row_iterator_next` and `next_dirty`, cells via `row_cells_*`, cursor, palette, colours, overscan
  rows, image placements via `image_iterator_*`. Placements are not in draw order; the host sorts
  by layer and z-index.
- Input encoders: key (including kitty), mouse (SGR), focus, paste, colour-scheme and in-band size
  reports.
- Also: selection and search, a formatter (`ghostty_formatter_format` to plain text and other
  forms), grid references that survive scrolling, snapshot encode/decode, OSC and SGR parsers.

What it leaves to the host:

- The pty (our `JNI.createSubprocess` / `termux.c` stays), all drawing (fonts, glyph shaping,
  cursor, selection highlight, underline geometry), texture upload and caching for kitty images,
  clipboard, hyperlink opening policy, bell and notification UI, and any protocol it does not
  implement (sixel and iTerm2 images at least).

Sketch of how it would sit behind `terminal-view` (one possible shape, not a plan):

1. A new native library (`libghostty-vt.so` plus a JNI shim, built per ABI) is loaded beside
   `libtermux.so`. A `GhosttyEmulator` Java class owns a native terminal pointer and implements the
   same surface `TerminalSession` uses today (`append(bytes)`, `resize`, key and mouse encode,
   `paste`).
2. Threading: `TerminalSession` already feeds the emulator on the main thread from a byte queue
   (`TerminalSession.java:27-28, 59`). That stays valid: `vt_write` and render-state
   `begin_update` on one thread. `end_update` could run elsewhere. Callbacks arrive during
   `vt_write` and would be marshalled to the existing `TerminalSessionClient`.
3. Rendering stays in `terminal-view/`. `TerminalRenderer` reads `TerminalRow` today; it would read
   either a Java-side mirror of the render state (copied per dirty row into reusable arrays) or
   call `get_multi` per row. Which is cheaper over JNI is not known (see unknowns).
4. Kitty: the library keeps the image store; `KittyImageStore`'s role shrinks to texture caching
   keyed on image generation (`kitty_graphics.h`), under its existing budgets.
5. Our extensions (sixel, iTerm2, text sizing, anything the library omits) would need a pre-parser
   on the byte stream, since `vt_write_until` stops at string boundaries but the library ignores
   unknown DCS.
6. Scrollback and `TerminalTranscript` consumers (find/copy, selection, `UrlDetector`, session
   browser, durable workspaces) read Java rows now; they would move to the library's selection,
   search and formatter, or to copies.

## Licensing

- libghostty-vt: MIT (`LICENSE`, post). `deps/libghostty-vt-third-party.json` (generated, lists
  version, SPDX id and whether the code ends up in the library) gives:
  - Highway 1.2.0: `Apache-2.0 OR BSD-3-Clause`, linked.
  - simdutf 9.0.0 (vendored under `pkg/simdutf/vendor`): `Apache-2.0 OR MIT`, linked.
  - Wuffs 0.4.0-alpha.10: `Apache-2.0 OR MIT`, linked.
  - uucode 0.2.0 (the Unicode tables): `MIT AND Unicode-3.0`, linked.
  - translate_c 2.0.0: `MIT`, linked.
  - aro 0.0.0: `MIT AND Unicode-3.0`, build-time only.
  The manifest shows no copyleft entry. Pinned versions are those at the tag.
- The Ghostty app and `build.zig.zon` also list many non-library dependencies (GTK, freetype,
  harfbuzz, fonts, sentry); the packaging doc says libghostty-vt tarballs "contain only the files
  and dependencies it needs".
- Our notice obligations (reasoned from the manifest): attribution for MIT, Apache-2.0 (where the
  Apache arm is chosen), BSD-3-Clause and the Unicode licence for the uucode tables; for the
  dual-licensed entries the MIT or BSD arm is available. Whether the NDK's own runtime adds
  notices was not checked.

## Build-system impact

- Zig: `minimum_zig_version` at `vt-1.0.0` is 0.17.0; the packaging doc says major and minor must
  match exactly and 1.0.0 needs Zig 0.17.x. The third-party Android recipes use 0.15.2 and 0.16.0,
  so they predate or differ from the 1.0 requirement.
- NDK: wired through the `android_ndk` dependency; third parties pin r27 to r30. Our Gradle uses
  `ndkBuild` from `jni/Android.mk` with `ndkVersion` from `gradle.properties`
  (`terminal-emulator/build.gradle:18-43`); a Zig step would be an additional task producing
  `.so` per ABI into `jniLibs`, or a prebuilt checked in or fetched.
- Offline and reproducible: signed source and dependency tarballs, `--system` offline builds, a
  fresh `ZIG_GLOBAL_CACHE_DIR`; do not reuse a filled cache (can crash under Zig 0.17)
  (`PACKAGING-LIBGHOSTTY.md`). One third-party report found clean builds not byte-identical.
- Flags worth knowing: `-Doptimize=ReleaseFast`, `-Dcpu=baseline`, `-Dstrip`,
  `-Dvt-features=` to compile subsystems out (functions then return `GHOSTTY_UNSUPPORTED`).
- CI: the project's workflows would need Zig and an NDK on every runner for all three editions
  (AGENTS.md: debug build workflow, edition matrix); not examined.
- APK size per ABI: not published. The only size datum is WebAssembly, "under a megabyte with
  every feature enabled" in the small build (post). Today's universal release APK is about 100 MB
  (`performance.md`), so a native library would be a small share either way (reasoned); a
  measured size needs a build.

## Open questions

Need a build or a device:

- What does JNI cost per frame for reading a dirty row or a full viewport, with `get_multi` or
  `CELLS_RAW`, on pong? Nothing published measures it.
- Real idle memory and parse throughput on arm64 Android (pong), against our Java core, for the
  same transcript. No such number exists in the sources. The harness question is answered under
  "Performance and SIMD" (it can likely be built for arm64, but that is untested).
- Android library size per ABI with and without `vt-features` trimming.
- Compatibility of the Android `.so` with the app's `sharedUserId` and `targetSdk` 28 loading
  rules, and with the three edition package names (reasoned only).

Source-only, not fully settled:

- Whether `madvise(MADV_DONTNEED)` works in the app's seccomp and SELinux domain on a real
  Android 8-15 device, and that Zig labels `*-linux-android` as `os.tag == .linux`. Checked:
  `src/terminal/mem.zig` has no Android case and CI only builds. Needs a build.
- Kitty placements across reflow: tracked pins are adjusted by reflow, but I did not read a test
  that resizes with a placement present. Checked: `PageList.zig` reflow and
  `kitty/graphics_storage.zig`.
- Whether the "24/7 AFL++ on dedicated machines" fuzzing exists outside the repo: only the
  project's statement and the harness source were found.

## Sources

Library and 1.0 release:
- Mitchell Hashimoto, "Libghostty 1.0": https://mitchellh.com/writing/libghostty-1-0
- Release tag list (shows `vt-1.0.0`, 2026-11-06, commit 0fa3d57): https://github.com/ghostty-org/ghostty/tags
- Release page: https://github.com/ghostty-org/ghostty/releases/tag/vt-1.0.0
- PR #15126 "libghostty-vt: prepare for 1.0.0": https://github.com/ghostty-org/ghostty/pull/15126
- Ghostty 1.4.0 release notes: https://ghostty.org/docs/install/release-notes/1-4-0
- Packaging guide: https://raw.githubusercontent.com/ghostty-org/ghostty/main/PACKAGING-LIBGHOSTTY.md
- `build.zig.zon` at `vt-1.0.0`: https://raw.githubusercontent.com/ghostty-org/ghostty/vt-1.0.0/build.zig.zon
- Release directory checksums (asset list): https://release.files.ghostty.org/libghostty-vt/1.0.0/SHA256SUMS
- Stability page: `dist/doxygen/pages/stability.md` at `vt-1.0.0` (rendered at https://lib.ghostty.org/)
- Optional features page: `dist/doxygen/pages/features.md` at `vt-1.0.0`
- Third-party manifest: `deps/libghostty-vt-third-party.json` at `vt-1.0.0`

C API headers (main, read 2026-11-07), under
https://github.com/ghostty-org/ghostty/tree/main/include/ghostty/vt :
- `terminal.h`: https://raw.githubusercontent.com/ghostty-org/ghostty/main/include/ghostty/vt/terminal.h
- `render.h`: https://raw.githubusercontent.com/ghostty-org/ghostty/main/include/ghostty/vt/render.h
- `kitty_graphics.h`: https://raw.githubusercontent.com/ghostty-org/ghostty/main/include/ghostty/vt/kitty_graphics.h
- `snapshot.h`: https://raw.githubusercontent.com/ghostty-org/ghostty/main/include/ghostty/vt/snapshot.h
- `io.h`: https://github.com/ghostty-org/ghostty/blob/main/include/ghostty/vt/io.h
- `vt.h`: https://raw.githubusercontent.com/ghostty-org/ghostty/main/include/ghostty/vt.h

Source (tag `vt-1.0.0` unless noted):
- Modes: https://raw.githubusercontent.com/ghostty-org/ghostty/vt-1.0.0/src/terminal/modes.zig
- DCS handling (no sixel): https://raw.githubusercontent.com/ghostty-org/ghostty/vt-1.0.0/src/terminal/dcs.zig
- Android and linking in the build (main): https://github.com/ghostty-org/ghostty/blob/main/src/build/GhosttyLibVt.zig
- Also read at `vt-1.0.0` (clone, repo-relative paths): `src/terminal/mem.zig`, `src/terminal/stream.zig`,
  `src/terminal/stream_terminal.zig`, `src/terminal/PageList.zig`, `src/terminal/Terminal.zig`,
  `src/terminal/kitty/key.zig`, `src/terminal/kitty/graphics_image.zig`,
  `src/terminal/kitty/graphics_unicode.zig`, `src/terminal/osc/parsers/iterm2.zig`,
  `src/terminal/osc/parsers/kitty_text_sizing.zig`, `src/lib_vt.zig`, `src/build/Config.zig`,
  `build.zig`, `.github/workflows/test.yml`, `test/fuzz-libghostty/README.md`,
  `src/benchmark/` and `src/build/GhosttyBench.zig`, `include/ghostty/vt/*.h`

Compression and memory:
- PR #13264 scrollback page compression: https://github.com/ghostty-org/ghostty/pull/13264
- Hashimoto, libghostty vs alacritty_terminal memory: https://x.com/mitchellh/status/2081788657078505732
- Hashimoto, scrollback compression announcement: https://x.com/mitchellh/status/2075284760583418284

Android:
- Discussion #10902 "ci: Add lib-vt Android support": https://github.com/ghostty-org/ghostty/discussions/10902
- Third-party `ancientcatz/libghostty-android`: https://github.com/ancientcatz/libghostty-android
- Third-party `JotaVeUltra/ghostty-android`: https://github.com/JotaVeUltra/ghostty-android
- Third-party reproducible-build issue: https://github.com/pohunekd/pohunek-companion-android/issues/11
- Zig bionic issue cited in the discussion: ziglang/zig#23906 (not fetched)
- PR #10925 `ci: Add lib-vt Android support`, PR #12341 `android: Avoid referencing POSIX shared memory functions`, PR #13174 `kitty/gfx: fix build on targets without 64-bit atomics`: https://github.com/ghostty-org/ghostty/pull/10925 (and /12341, /13174)
- Issue #14998 `fuzz: expand coverage across libghostty-vt fuzz harnesses`: https://github.com/ghostty-org/ghostty/issues/14998
- awesome-libghostty (bindings list the post points to): https://github.com/Uzaaft/awesome-libghostty
- `vlaaad/ghosttyfx`: https://github.com/vlaaad/ghosttyfx ; `jossephus/chuchu`: https://github.com/jossephus/chuchu

Protocol coverage:
- Ghostty VT external protocols: https://ghostty.org/docs/vt/external
- Ghostty VT reference: https://ghostty.org/docs/vt/reference
- ucs-detect result for Ghostty (sixel, iTerm2 "no"): https://ucs-detect.readthedocs.io/sw_results/ghostty.html

This repo:
- `terminal-emulator/src/main/java/com/termux/terminal/`: `TerminalEmulator.java`,
  `TerminalBuffer.java`, `TerminalRow.java`, `GraphemeClusterer.java`, `KittyGraphicsProtocol.java`,
  `TerminalSession.java`, `JNI.java`; `terminal-emulator/build.gradle`; `gradle.properties`
- `docs/agents/performance.md`
- `project-docs/reference/terminal/kitty-protocol-features.md`
- `AGENTS.md`
