# Termux Launcher

Termux Launcher is a fork of Termux that is also an Android **home launcher**. The terminal is the
home screen: app drawer, dock, widgets, status chrome and an embedded keyboard are drawn around a
live terminal session, and the whole thing is themable down to per-surface blur, opacity, grain,
corner radius and edge gap.

The rest of this document exists so you can change it without breaking the parts people already
rely on. Treat it as good defaults, not scripture — the developer's stated preference overrides
anything here, and if a rule fights the task in front of you, say so out loud rather than quietly
working around it.

## What we can never compromise on

### 1. It is the home screen

This is not an app the user can quit. It is the process Android returns to when anything else
closes, it holds their shell sessions, and it is the first thing they see all day. That has hard
consequences you will meet repeatedly:

- **`am force-stop` is not a clean restart.** The system immediately relaunches the launcher, and
  the fresh process reads shared preferences on the way up. Edit prefs (via `run-as` + `sed`)
  **before** the force-stop, never after — otherwise the restart overwrites your edit.
- A crash is not a crash of "an app", it is the home screen falling over mid-touch. Null-tolerance
  at input boundaries is a feature, not defensive clutter.
- **Never gate window drawing without a fail-safe.** The keyboard reveal gate ships three (frame
  count, 160 ms backstop, null-gate-view). A gate that can hang leaves a black home screen.

### 2. Memory discipline

An app that never dies pays for every byte it retains forever. The 2026-08 RAM work bounded
growth: memory now comes back on `clear`, on pane close and under pressure, and a live terminal
idles at about 185–200 MB PSS on pong. That ground is not to be given back. Anything that holds
pixels — kitty graphics frames, app icons, wallpaper blurs — belongs behind a bounded store with an
explicit budget:

- Kitty frames are charged against a process-wide `KittyImageStore.FrameBudget` sized from
  `/proc/meminfo`, settled at commit rather than accept, and folded rather than refused when full.
- App icon artwork lives in the budgeted `LauncherIconStore`, not on `LauncherAppEntry`.
- **Decide cost from the pixels a drawable actually holds, never the size it declares.**
  `AdaptiveIconDrawable.getIntrinsicWidth()` reports the nominal 72dp while its layers hold the
  108dp rasterisation; walk containers all the way down, and treat "no intrinsic size" as
  *measure it*, not zero.

### 3. It has to feel right

Users drive this thing with their thumbs all day and notice a dropped frame, a mispositioned chip
and a stale label. No continuously repainting animations. No layout work per frame. Prefer
structural fixes (derive the position, own the outline) over tuning durations.

**Performance has numbers.** Before a change that keeps pixels or memory, runs per frame or at
rest, sits on the start-up or page-change path, or loads a model, read
[`docs/agents/performance.md`](docs/agents/performance.md): the budgets, the pong baselines, how to
measure them, and what your report must show.

**When the developer calls something "janky", they mean it looks bad — not that it dropped
frames.** Read UI complaints as aesthetic first and ask before optimising a frame budget nobody
complained about.

### 4. Three editions, fed only by merging

Content reaches the three release branches (`main`, `nix-edition`, `io-vaj-package`) only by
merging `dev`. A release branch carries nothing but its identity: applicationId,
`TERMUX_PACKAGE_NAME` (in `app/build.gradle` and `TermuxConstants`), `versionName`, manifest
placeholders, ABI/split rules, bootstrap handling, CI matrix. New work starts on `pre`, and fixes
for the current stable start on `dev`; see "Branches and releases".

Every edition has its own package and so its own prefix, `/data/data/<pkg>/files/usr`. Name the
package through `context.getPackageName()` or `TermuxConstants`; a literal `com.termux` in code is
correct on one edition and a bug on the other two.

## Words

**You** are the agent reading this. **The developer** is the maintainer you are talking to, who
uses the launcher as their daily driver. **Pong** is their phone and the device of record. For every
other name (a place, a surface, a gesture, an edition), look it up in
[`docs/GLOSSARY.md`](docs/GLOSSARY.md) before you name it in code, a commit, a string or a report.

## The five ways to hurt yourself

1. **`gh` talks to the wrong repo.** This checkout has an `upstream` remote pointing at
   `termux/termux-app`, and `gh` resolves to it by default. **Pass `--repo PickleHik3/termux-launcher`
   to every single `gh` command.** A release created on upstream is not a mistake you can quietly
   undo.
2. **The developer is editing this tree while you work in it.** Builds have failed mid-save on
   their in-flight edits. A compile error in a file you did not touch is probably theirs — retry
   the build, do not "fix" their half-typed code, and do not revert it.
3. **Committing bootstrap zips.** `*.zip` is gitignored for a reason: bootstraps are downloaded and
   checksum-verified at build time (`downloadBootstraps`) or on first run. One got committed once
   and cost a force-push and a history rewrite.
4. **Touching a real phone without being asked.** The device of record is someone's daily
   driver, often reached over the network (`adb connect <device-ip>:5555`); how to reach it is
   configured on the host, not in the repo. Pass `-s <serial>` to every adb command: bare `adb`
   goes to whichever device is attached, and that is often the phone. Ask before installing, force-stopping, or injecting input. A stray
   `adb input tap` once landed on the editor's edge-drag pill and silently zeroed the developer's
   side gap on every surface.
5. **Merging `dev` into an edition branch.** A dev merge once clobbered the VAJ identity in
   `app/build.gradle` (the v0.2.31 hotfix): applicationId, manifest placeholders and ABI rules are
   the only thing an edition branch owns, and a merge that takes `dev`'s side of that file silently
   ships the wrong app. Expect conflicts in `app/build.gradle` and `app/src/main/res/values/strings.xml`
   on every cut, and in `.github/workflows/debug_build.yml` whenever `dev` touched its push
   trigger (each edition names its own branch there); resolve identity, `versionName` and the
   trigger to the edition's side. Diff `app/build.gradle`
   against the previous edition tag before tagging; the workflow's manifest guard is the backstop,
   not the check.

## Hit every surface

The most common defect in this repo is a change that works on the path you tested and is missing
everywhere else. Before calling a change done, walk this list and say which entries applied:

- **Dock styles.** Docked and Floating resolve geometry differently and *should* — Docked is square
  at rest so every dp of radius is new encroachment; Floating is already a 26dp card, so only radius
  beyond that encroaches. Do not "unify" them into one formula.
- **Keyboard up and down.** The in-app keyboard changes terminal bounds, dock lift, flush-dock
  absorption, and which surface the decor nav strip should mirror. Check both.
- **One pane and many.** Split panes change what "the terminal" means; small panes clamp their own
  radius (`PaneShape.radiusForBounds`).
- **Inherited and detached.** Every surface property can be following Base or overridden. A control
  that only works in one state is half-built. Setters write *through* the link deliberately, so
  Settings sliders and wallpaper mode do not silently detach surfaces.
- **Reverse states.** If you added a way in, add the way out and the way to see it. A one-way door
  is a bug.
- **Themes and scheme.** Light, dark, black, the Material You scheme, and the launcher's own scheme
  chrome (`LauncherSchemeTheme.isSchemeChromeActive()`) — chrome colours that read system palette
  resources directly have bypassed the scheme before.
- **Density and font scale.** Overlap reports from other phones are usually *text*, not geometry:
  fixed-height rows plus a 1.3× font scale is what clips. Single-line + ellipsize labels, and give
  toggle segments `minWidth=0` and autosize.
- **Editions.** Anything touching applicationId, bootstrap, manifest or signing needs a decision per
  edition, even if the decision is "no change".
- **The system IME.** Any code path that shows the system keyboard **must** call
  `TermuxActivity.onSystemImeRequested()` first, or its insets are ignored by the
  `mAcceptSystemImeInsets` gate.

## Where code lives

- `app/` — the launcher. Java only, under `com.termux`.
  - `app/` — `TermuxActivity` (the shell everything hangs off), `SuggestionBarView`, service,
    installer, dock/glass rendering views.
  - `app/terminal/` — terminal hosting, clients, panes, find/copy modes, `inappkeyboard/`.
  - `app/surfaces/` — the Appearance editor, inheritance row table, presets, materials, outlines.
  - `app/layouteditor/` — the Layout editor: the miniature, the orientation toggle, the size rows.
  - `app/launcher/` — app catalogue, icons, paging, popups, A–Z scrub, notifications.
  - `app/chrome/`, `app/dock/`, `app/statusbar/`, `app/notice/`, `app/theme/`, `app/settings/`,
    `app/tour/` — the named chrome subsystems.
  - `ai/`, `launcherctl/`, `privileged/`, `filepicker/` — the non-terminal side features.
- `terminal-emulator/` — escape-sequence parsing, buffers, kitty graphics, sixel. Upstream-shaped.
- `terminal-view/` — the `TerminalView` widget and input handling.
- `termux-shared/` — shared constants, settings, `TermuxAppSharedPreferences` (which is also where
  the surface inheritance resolver lives, so every existing getter returns resolved values).
- `termux-am-library/`, `inapp-keyboard/` — vendored: the am library, and a trimmed snapshot of
  Unexpected-Keyboard kept in package `juloo.keyboard2` for upstream diffability. **Local deviations
  from upstream go in `inapp-keyboard/UPSTREAM.md`, always.**
- `docs/` — user guides (`docs/en/`), ADRs (`docs/adr/`), agent docs (`docs/agents/`) and the
  glossary (`docs/GLOSSARY.md`). `project-docs/` — developer specs: `active/` (work in flight),
  `reference/` (research, comparisons, delivered records), `verification/` (runnable probes),
  `backlog.md`, and the release notes; `project-docs/README.md` is the index. `ci/`, `fastlane/`,
  `art/` — packaging, build support and store metadata.
- Never commit generated `build/` or `.gradle/` content.

## The privileged lane

`app/src/main/java/com/termux/privileged/lane/` lets a terminal program run as the shell uid (2000).
`PrivilegedLaneService` is a Shizuku UserService running as shell; `PrivilegedLaneServer` listens
on the abstract socket `@<packageName>.priv` and accepts only the app's own uid; the client is
`tl-priv` in the tlstore repo (PickleHik3/tlstore, `recipes/cross/tl-priv/tl-priv.c`).

- **The wire protocol `tlpriv1` (`LaneRequest`) is a contract with `tl-priv`.** Change both sides in
  step, or neither.
- `LaneAllowlist` runs only catalog `binary` rows marked `priv=shizuku` whose sha256 matches the
  active tlstore catalog. It guards against accidents, not against code already running as this
  uid; do not describe it as a security boundary.
- Binaries are staged under `/data/local/tmp/tl`. The service runs with umask 0, so every directory
  there must be chmodded 0700 explicitly (`PrivilegedLaneService`), or the tree is world-writable.
- `LocalSocket` re-attaches queued fds to every later write. After handing over the pty master,
  call `setFileDescriptorsForSend(null)`, or the next write fails with EBADF and the client hangs.
- `getFilesDir()` answers `/data/user/0/<pkg>/files` while Termux paths say `/data/data/<pkg>/files`.
  They are one directory, not a symlink, so canonical paths never agree: compare by inode.
- The window chip shows `tl-priv run <path>` as the tool's name
  (`WindowForegroundResolver.unwrapLane`); a new way of launching through the lane needs it too.

## Build and run

Run from the repository root with the checked-in wrapper. Builds need an Android SDK and a JDK
compatible with AGP 8.13.2; the code targets Java 11.

- `./gradlew :app:assembleStaging` — what goes on a phone and into nightlies: the release build's
  R8 shrinking and keep rules, but debuggable (`run-as` works) and signed with the published test
  key. Debug builds skip R8, so a hole in the keep rules only ever shows up in a shrunk build;
  1.0.0 shipped one.
- `./gradlew assembleDebug` / `:app:assembleDebug` — unshrunk debug APKs, for fast local iteration.
- `./gradlew testDebugUnitTest` — JVM unit tests across modules, matching CI.
- `./gradlew :app:connectedDebugAndroidTest` — instrumentation on a connected emulator or device.
- `./gradlew lintDebug` — Android lint.
- `./gradlew :app:verifyReleaseHardening` — release build safety settings.
- `scripts/dev-install.sh` — strips the bootstraps from the arm64 staging APK and installs it as
  an upgrade over an existing install (a fresh install from it has no bootstrap). Target the device
  with `DEVICE=<serial>`; `BUILD_TYPE=debug` installs the debug APK instead.
- **Installing on pong** (only when the developer asks): pong runs release builds.
  `BUILD_TYPE=release DEVICE=<pong> scripts/dev-install.sh` builds one versioned
  `<versionName>+dev.<sha>` from HEAD and installs it. Do not `adb install` the release
  APK by hand: a local release build has no ABI splits (one ~100 MB universal APK with every
  bootstrap) and is **unsigned** unless the CI's `TERMUX_RELEASE_*` variables are set, which
  fails as `INSTALL_PARSE_FAILED_NO_CERTIFICATES`. The script keeps only arm64-v8a, drops the
  bootstraps (an update never needs them), signs with the test key every build shares, installs
  with `--no-incremental` and checks the launcher came back up.
- `adb -s <serial> shell run-as <pkg> …` reads and edits the app's files on a debug or staging
  build.

**When the machine is busy, let CI build.** A push to `dev` runs "Build nightly"
(`.github/workflows/debug_build.yml`: `testDebugUnitTest`, then `assembleStaging`). Read it with
`gh run list --repo PickleHik3/termux-launcher --branch dev` and
`gh run view <id> --repo PickleHik3/termux-launcher --log-failed`.

## Verifying

Smallest proof that the change works, then stop. Run the tests you touched; run the module suite
before you hand work over. CI owns the rest.

- **Unit tests.** JUnit 4, Robolectric where Android behaviour is needed, AndroidX Test for
  instrumentation. Tests sit beside their module in `src/test/java` / `src/androidTest/java`,
  mirror the production package, and end in `Test`. Bug fixes ship with a focused regression test.
- **Baselines, not counts.** The full suite is green (thousands of tests) and the historical
  ~50-failure environmental baseline no longer reproduces. If failures appear, **compare failing
  test *name lists* against a clean worktree**, never counts.
  `TerminalIOPreferencesDataStoreLazyModeTest` is a known order-dependent flake (static singleton
  in `TerminalIOPreferencesFragment`) — it passes alone.
- **Emulator first, for anything visual.** The Android emulator is the test platform. If your
  host has an emulator skill or driver, use it rather than re-deriving the setup; drive the app
  through Mobilerun Portal where the host provides it. The non-negotiables: an AVD such as
  `tl16`, `-gpu angle_indirect` (the default swiftshader segfaults on this app's blur), stop the
  Gradle daemon first on a small host, install the **x86_64** split, and always pass
  `-s emulator-5554` — if qemu dies, adb silently falls back to the phone.
- **Device checks run through the `device-checker` agent** where the host has one: it gets the
  user-facing summary and the `device-checks.md` items, never the diff, and reports pass or fail
  with crops. Take the device's lease for the whole session, and batch pong items into one ask.
  This launcher becomes the emulator's home screen when installed: set the stock launcher back
  as home before you hand the emulator over. Swap checks (stable → drop → stable) need release
  builds of both, which install over each other because every build shares one signing key.
- **Tear the test setup down when you are done.** The emulator holds this host's RAM and GPU for
  as long as it runs. When the testing is finished, force-stop the app, release the lease (which
  stops an emulator it started), and stop any background monitor or logcat loop you left behind.
- **What the emulator cannot tell you.** Jank: `gfxinfo` on ANGLE/lavapipe reports everything as
  100% janky — judge motion on a real phone. Dialogs: a UI-bounds dump does not see dialog windows, so
  screenshot and tap fresh coordinates. Simulate other phones with `wm size` / `wm density` /
  `font_scale`, and **force-stop the app after a density or font change** or the layout keeps the
  old dp scale.
- **Be honest about the boundary.** Say which items you verified, on what, and which are reasoned
  from the code and still owe a device check. "Device-verified" means you saw it. Whatever still
  owes one goes on `project-docs/device-checks.md` in the same commit, under Agent (drivable over
  adb) or Developer (a hand on the phone); it is deleted there once seen.
- Do not verify with browsers or computer use unless the developer asks.
- Compile-check any API claim before you rely on it. `UserHandle.getIdentifier` looks public and is
  `@SystemApi`; a worker asserted otherwise and was wrong.

## User-facing text

Settings entries, dialogs, hints, notices — every string a user can see is product copy, not an
engineering note. Write it the way a polished 2026 app would: a short title and one sentence that
says what the user gets. No implementation mechanism, no window-manager or protocol names, no HTTP
codes, no keybind lore, and never a recap of the design discussion that produced the feature.
"Arrange panes automatically as you open and move them", not "tiles like Hyprland: each new pane
halves the focused one along its longer side". If a string seems to need a paragraph, the setting
is doing too much. The deeper detail belongs in `docs/en/`, where curious users are already sent —
and in the commit body, where the next agent will look.

## Coding style

Follow `.editorconfig`: UTF-8, LF, final newline, four spaces (two for YAML). Standard Java
conventions — `UpperCamelCase` types, `lowerCamelCase` members, `UPPER_SNAKE_CASE` constants,
lowercase packages under `com.termux`, `lower_snake_case` Android resource names. There is no
repository-wide formatter: match the surrounding code.

- **Extend the seams, not `TermuxActivity`.** New activity-facing behaviour goes through a Host
  interface or a deep module (`TerminalHost`, `ChromeRenderer`, `DockLayoutPolicy`,
  `SurfaceEditorController.Host`, …), not a new public method on the activity. That interface
  accreted 326 public methods once; the extraction is what stopped it.
- Keep geometry and policy **pure and tested** (`DockLayoutPolicy`, `SurfaceEditorPillMetrics`,
  `AzScrubGesture`, `PaneShape`), with the view layer dumb enough to just apply the answer.
- Comments describe how a thing is used and move when the code moves. Do not annotate every line.
- Inferred types over ceremony. Do not preserve complexity because it already exists, and do not
  add machinery because it looks architecturally impressive.
- No magic padding, no negative margins, no per-size special cases where a measured rule will do.
  A number that appears twice is a constant that appears once, next to the code it governs.
- A layout never requests another layout from inside measure, layout or draw: compare before
  setting text, visibility, minimum sizes or layout params.
- Robolectric layout tests use `@GraphicsMode(GraphicsMode.Mode.NATIVE)` so text has real
  metrics; a layout assertion in legacy mode proves nothing.

## Things already decided — do not re-litigate

These were settled deliberately. Raise them if you think they are wrong; do not silently undo them.

- **Negative / concave corner radius was proposed and fully dropped.** Do not reintroduce it.
- **The Sessions sidebar is legacy.** It loses its purpose with split tabs and its edge-swipe
  fights the keyboard's. No Sessions tab in the editor, no dot tab badges.
- **The Appearance editor commits only on Done.** It rests as the outlines plus a floating
  palette/✓ pill; the card is raised by the palette (shared layer) or by touching a surface, and its
  ✕ only puts the card down. Back puts an open card down first; from the resting state it routes
  through the unsaved-changes dialog. Dirtiness is a comparison against the snapshot taken on entry.
- **A stored `-1` corner radius is the "follow the style" sentinel**, resolved in one place
  (`resolveAutoCornerRadiusDp`). Never set `DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS` or
  `DEFAULT_STATUS_BAR_CORNER_RADIUS` to a real number — the shipped default rides on
  `DEFAULT_SURFACE_BASE_CORNER_RADIUS` instead.
- **Shipped defaults are pinned for existing installs.** `adoptShippedSurfaceDefaults()` treats a
  store holding nothing but `log_level` as a fresh install; anything else gets the pre-shipped
  values pinned into keys it never set, *before* the inheritance fold.
- **The Appearance editor collapses the status pane on entry.** A status-pane control therefore cannot
  rely on the live pane — it must draw its own preview in its row.
- **`SettingsLayoutUtils.applyItemLayout` overwrites every preference's layout.** A preference with
  its own layout must be added to the exemption list or it renders as a plain row.
- Terminal padding is intentional. Leave it: the grid is centred in its view on both axes
  (`getHorizontalContentOffset`, `getVerticalContentOffset`), so the sub-cell leftover splits
  evenly and the pane's inner gap is the same on all four sides; the `mFontLineSpacingAndAscent`
  top offset is the renderer's and the pane frame counts it as clearance the top already has.
- **`targetSdkVersion` stays 28, on every edition and every companion app.** Android only leaves
  the `untrusted_app_25`/`untrusted_app_27` SELinux domains allowed to execute files under an app's
  own data directory; from sdk 29 the app lands in `untrusted_app` and the kernel denies
  `execute_no_trans` on everything in `$PREFIX`. The domain is computed from the **highest**
  target sdk in the whole `sharedUserId` group and is remembered until every member is uninstalled
  — so a single companion app (`…api`, `…styling`, any plugin) built against a newer sdk bricks
  the launcher it ships beside, and a plain reinstall does not undo it. `TermuxInstaller` detects
  the restricted domain and names the offending packages; it does not work around it.
- **No exec fallback via the system linker.** Running an unexecutable binary through
  `/apex/com.android.runtime/bin/linker64` was tried (8ea87965) and removed: it turns a clean
  "cannot exec my own files" into an opaque exit 126 deep in the bootstrap second stage, it cannot
  work for `#!` scripts at all, and the prefix was deleted before anyone could read the evidence.
  Exec failures must surface where they happen.

## Branches and releases

Pick the route before you branch. Content only ever moves `pre` → `dev` → the release branches:

| The work | Branch from and merge into | Reaches users as |
|---|---|---|
| A feature, or any change in behaviour | `pre` | the next pre-release drop; then the next stable, once `pre` is promoted into `dev` |
| A fix for something the current stable has | `dev` | the next patch release (`dev` merged into each release branch); the next drop merges it into `pre` |
| A fix for something only the channel has | `pre` | the next drop |

Releasing has its own document, [`docs/agents/releasing.md`](docs/agents/releasing.md). Read it
before you cut a release or a pre-release drop, write release notes (a change merged into `pre`
adds its line to `project-docs/prerelease-notes.md`), read a version string from a bug report, or
touch an edition branch or a companion app.

## Commits, PRs and work artifacts

- Conventional-commit subjects, plain language, imperative, scoped where it helps:
  `fix(statusbar): move Floating's bottom row inward as its corner radius grows`. One concern per
  commit. If the subject wants an "also", split it.
- **Never open a PR or cut a release unless the developer explicitly asks.**
- A PR body explains the problem in a sentence or two, then the fix. List what you ran and what you
  tested it on. Link the issue. UI changes need before/after images; motion needs a short video.
  Call out native-library, signing, bootstrap or compatibility impact explicitly.
- **Durable** plans, designs and decisions go in `project-docs/active/` while the work is open and
  `project-docs/reference/` once it is delivered, so the next agent finds current facts. **Ephemeral** agent scratch — working notes, todo checklists,
  research dumps — stays out of the worktree. `work-logs/` runtime logs and `.lavish/` review
  documents are gitignored on purpose.
- The merged commit is the implementation record. Do not leave a second checklist behind in the
  repo after the work lands.

## Agent skills

### Issue tracker

GitHub Issues on `PickleHik3/termux-launcher`; every `gh` call passes `-R`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels, unchanged. See `docs/agents/triage-labels.md`.

### tlstore

The store's own sources (its CLI, `tlstore-ui`, the catalog and its build pipeline) live in
[PickleHik3/tlstore](https://github.com/PickleHik3/tlstore), not here — send catalog and store-code
changes there; see that repo's `docs/maintainer/catalog.md` and `AGENTS.md`.

This launcher only pins the release it ships: `app/tlstore.lock` names a tlstore release tag and
the sha256 of each `dist/` file, and Gradle's `fetchTlstore` task downloads them into the APK's
assets, verifying every hash. Bump the lock with the block tlstore's `scripts/release.sh` prints
after cutting a release there, and bump it whole: the engine and `tlstore-ui` are a pair and must
come from the same release.

On a new APK, `TlstoreInstaller` writes the bundled engine and `tlstore-ui`, but never over a
tlstore the user has self-updated to something newer (it compares `TLSTORE_VERSION`) — it keeps
that pair. Testing a bundled tlstore on a device that has self-updated therefore shows the newer one.

A store item that needs a launcher feature gates on `min-launcher=X.Y.Z` in the tlstore catalog,
naming the **release** the feature first ships in (btop needs the lane, which first ships after
v0.2.39). The engine compares it with the running app's `versionName`, so a `dev` build reports
`dev`'s stale version and can hide an item the next release will show. To build against a local tlstore checkout instead of a published
release — while developing tlstore itself — pass `-Ptlstore.local=<dir>` (or set `TLSTORE_LOCAL`)
pointing at that checkout's `dist/` folder; this skips the lock check entirely and prints a warning.

### Domain docs

Single-context: `docs/GLOSSARY.md` (`CONTEXT.md` at the repo root points to it) and `docs/adr/`.
See `docs/agents/domain.md`.
