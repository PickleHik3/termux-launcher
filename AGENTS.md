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

An app that never dies pays for every byte it retains forever. The 2026-08 RAM work took the
process from **PSS 601 MB to 171 MB** (native heap 437 → 82 MB, reachable bitmaps 340 → 28.8 MB),
and that ground is not to be given back. Anything that holds pixels — kitty graphics frames, app
icons, wallpaper blurs — belongs behind a bounded store with an explicit budget:

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

**When the developer calls something "janky", they mean it looks bad — not that it dropped
frames.** Read UI complaints as aesthetic first and ask before optimising a frame budget nobody
complained about.

### 4. Two editions, one branch

Features live on `dev` and reach editions only by merging. Edition branches carry nothing but their
identity differences (applicationId, manifest placeholders, ABI/split rules, bootstrap handling, CI
matrix). Never develop on an edition branch.

## A small glossary

Use this language; it is what the code and the developer use.

- **you** — the agent reading this file and changing the launcher.
- **the developer / the user** — the maintainer you are talking to, who also uses the launcher as
  their daily driver and is often testing your change on their own phone while you work.
- **edition** — one shipped applicationId: `com.termux` (main), `com.termux.launcher.nix` (Nix),
  `io.vaj.tl` (VAJ, the demo edition).
- **pong** — the developer's Nothing Phone 2 (A065, Android 16), the real device of record.
- **surface** — one themable chrome region: Dock, Keyboard, Status, Canvas. Modelled as
  `SurfaceSlot` × `SurfaceProperty` (blur, opacity, grain, corner radius, side gap).
- **Base** — the shared surface values every slot inherits until a property is *detached*.
- **Docked / Floating** — the two dock styles (formerly Default / Rounded; labels changed, stored
  values did not). Docked is flush and square at rest; Floating is a card already rounded at rest.
- **surface editor** — the one-page overlay that edits all of the above
  (`app/surfaces/SurfaceEditorController`). Also reachable by deep link.
- **pane** — one terminal view in a split; **chrome** — everything the launcher draws around it.
- **the seams / Host interfaces** — `TerminalHost`, `SurfaceEditorController.Host`,
  `ChromeRenderer` and friends: the deep modules extracted out of `TermuxActivity`.

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
4. **Touching pong without being asked.** It is the developer's daily driver, reachable over
   Tailscale (`adb connect <device-ip>:5555`, with `ANDROID_ADB_SERVER_PORT=5038` — that server
   holds the trusted key; the address is configured locally, not in the repo). Ask before
   installing, force-stopping, or injecting input. A stray
   `adb input tap` once landed on the editor's edge-drag pill and silently zeroed the developer's
   side gap on every surface.
5. **Merging `dev` into an edition branch.** A dev merge once clobbered the VAJ identity in
   `app/build.gradle` (the v0.2.31 hotfix): applicationId, manifest placeholders and ABI rules are
   the only thing an edition branch owns, and a merge that takes `dev`'s side of that file silently
   ships the wrong app. Diff `app/build.gradle` against the previous edition tag before tagging;
   the workflow's manifest guard is the backstop, not the check.

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
  - `app/surfaces/` — the surface editor, inheritance row table, presets, materials, outlines.
  - `app/launcher/` — app catalogue, icons, paging, popups, A–Z scrub, notifications.
  - `app/chrome/`, `app/dock/`, `app/statusbar/`, `app/notice/`, `app/theme/`, `app/settings/`,
    `app/onboarding/` — the named chrome subsystems.
  - `ai/`, `launcherctl/`, `privileged/`, `filepicker/` — the non-terminal side features.
- `terminal-emulator/` — escape-sequence parsing, buffers, kitty graphics, sixel. Upstream-shaped.
- `terminal-view/` — the `TerminalView` widget and input handling.
- `termux-shared/` — shared constants, settings, `TermuxAppSharedPreferences` (which is also where
  the surface inheritance resolver lives, so every existing getter returns resolved values).
- `termux-am-library/`, `inapp-keyboard/` — vendored: the am library, and a trimmed snapshot of
  Unexpected-Keyboard kept in package `juloo.keyboard2` for upstream diffability. **Local deviations
  from upstream go in `inapp-keyboard/UPSTREAM.md`, always.**
- `docs/` — contributor and workflow docs. `project-docs/` — durable plans, design docs, release
  notes, verification baselines. `recipes/`, `ci/`, `site/`, `fastlane/`, `art/` — packaging,
  build support and store metadata.
- Never commit generated `build/` or `.gradle/` content.

## Build and run

Run from the repository root with the checked-in wrapper. Builds need an Android SDK and a JDK
compatible with AGP 8.13.2; the code targets Java 11.

- `./gradlew assembleDebug` — debug APKs for all configured ABIs.
- `./gradlew :app:assembleDebug` — application module only.
- `./gradlew testDebugUnitTest` — JVM unit tests across modules, matching CI.
- `./gradlew :app:connectedDebugAndroidTest` — instrumentation on a connected emulator or device.
- `./gradlew lintDebug` — Android lint.
- `./gradlew :app:verifyReleaseHardening` — release build safety settings.
- `scripts/dev-install.sh` — smaller upgrade-only APK; needs an existing Termux install and a
  configured ADB target.

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
- **Emulator first, for anything visual.** Use the `android-emulator` skill
  (`~/.claude/skills/android-emulator/SKILL.md` and its `scripts/emu` driver) rather than
  re-deriving the setup. Non-negotiables it encodes: AVD `tl_test`, `-gpu angle_indirect` (the
  default swiftshader segfaults on this app's blur), stop the Gradle daemon first (7 GB host RAM),
  install the **x86_64** split, and always pass `-s emulator-5554` — if qemu dies, adb silently
  falls back to the phone.
- **What the emulator cannot tell you.** Jank: `gfxinfo` on ANGLE/lavapipe reports everything as
  100% janky — judge motion on pong. Dialogs: `emu bounds` does not see dialog windows, so
  screenshot and tap fresh coordinates. Simulate other phones with `wm size` / `wm density` /
  `font_scale`, and **force-stop the app after a density or font change** or the layout keeps the
  old dp scale.
- **Be honest about the boundary.** Say which items you verified, on what, and which are reasoned
  from the code and still owe a device check. "Device-verified" means you saw it.
- Do not verify with browsers or computer use unless the developer asks.
- Compile-check any API claim before you rely on it. `UserHandle.getIdentifier` looks public and is
  `@SystemApi`; a worker asserted otherwise and was wrong.

## Coding style

Follow `.editorconfig`: UTF-8, LF, final newline, four spaces (two for YAML). Standard Java
conventions — `UpperCamelCase` types, `lowerCamelCase` members, `UPPER_SNAKE_CASE` constants,
lowercase packages under `com.termux`, `lower_snake_case` Android resource names. There is no
repository-wide formatter: match the surrounding code.

- **Extend the seams, not `TermuxActivity`.** New activity-facing behaviour goes through a Host
  interface or a deep module (`TerminalHost`, `ChromeRenderer`, `DockLayoutPolicy`,
  `SurfaceEditorController.Host`, …), not a new public method on the activity. That interface
  accreted 326 public methods once; the extraction is what stopped it.
- Keep geometry and policy **pure and tested** (`DockLayoutPolicy`, `SurfaceEditorCardMetrics`,
  `AzScrubGesture`, `PaneShape`), with the view layer dumb enough to just apply the answer.
- Comments describe how a thing is used and move when the code moves. Do not annotate every line.
- Inferred types over ceremony. Do not preserve complexity because it already exists, and do not
  add machinery because it looks architecturally impressive.

## Things already decided — do not re-litigate

These were settled deliberately. Raise them if you think they are wrong; do not silently undo them.

- **Negative / concave corner radius was proposed and fully dropped.** Do not reintroduce it.
- **The Sessions sidebar is legacy.** It loses its purpose with split tabs and its edge-swipe
  fights the keyboard's. No Sessions tab in the editor, no dot tab badges.
- **The surface editor commits only on Done.** Back and ✕ both route through the unsaved-changes
  dialog; dirtiness is a comparison against the snapshot taken on entry.
- **A stored `-1` corner radius is the "follow the style" sentinel**, resolved in one place
  (`resolveAutoCornerRadiusDp`). Never set `DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS` or
  `DEFAULT_STATUS_BAR_CORNER_RADIUS` to a real number — the shipped default rides on
  `DEFAULT_SURFACE_BASE_CORNER_RADIUS` instead.
- **Shipped defaults are pinned for existing installs.** `adoptShippedSurfaceDefaults()` treats a
  store holding nothing but `log_level` as a fresh install; anything else gets the pre-shipped
  values pinned into keys it never set, *before* the inheritance fold.
- **The surface editor collapses the status pane on entry.** A status-pane control therefore cannot
  rely on the live pane — it must draw its own preview in its row.
- **`SettingsLayoutUtils.applyItemLayout` overwrites every preference's layout.** A preference with
  its own layout must be added to the exemption list or it renders as a plain row.
- Terminal padding (`getHorizontalContentOffset` centering, `mFontLineSpacingAndAscent` top offset)
  is intentional. Leave it.
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

All development happens on `dev`. Editions receive features exclusively by merging `dev`:

- `main` — the Termux edition (`com.termux`). Merge `dev`, tag `vX.Y.Z`.
- `nix-edition` — the Nix edition (`com.termux.launcher.nix`), tag `vX.Y.Z-nix`, published as a
  prerelease. Backed by the PickleHik3/nix-on-droid fork, branch `launcher-nix`; bootstrap zips live
  on the `nix-bootstrap` tag. Companion apps (TLNix API/Styling) release from their `nix-pkg`
  branches via `github_release_build.yml` with `nix-v*` tags.
- `io-vaj-package` — the VAJ edition (`io.vaj.tl`), tag `vX.Y.Z-vaj`. **The demo edition, and the
  least recommended one to install.** Its packages come from the developer's own apt repository
  (`repo.pathayam.xyz`), which is updated sometimes, with no promises. The security-only freeze it
  carried from v0.2.34-vaj to v0.2.36-vaj is over — it gets every release like the others — but
  "not frozen" is not "supported", and nothing should describe it as maintained, revived or
  production-ready.

**Every release ships all three editions.** A cut is not finished when `main` is tagged — `dev`
goes to `nix-edition` and `io-vaj-package` in the same pass, each with its own tag, notes and APK
run. Do not ask which editions to release; releasing one is the thing that needs a reason.

Per edition: bump `versionName` (it **must** equal the tag minus `v` or CI aborts, so it carries the
`-nix` / `-vaj` suffix too), merge `dev`, push, tag, `gh release create --notes-file …`, then
dispatch `attach_debug_apks_to_release.yml` with the tag. `versionCode` stays **1020** for upstream
parity — never change it.

A **hotfix** that must not claim a new version carries semver build metadata instead:
`v0.2.37+hotfix1`, `v0.2.37-nix+hotfix1`, `v0.2.37-vaj+hotfix1`, with `versionName` matching as
usual. Build metadata is ignored in semver precedence, so the tag compares equal to the release it
patches — which also means **a hotfix ships fixes only**. A feature under a `+hotfix` tag is a
feature under a version that sorts equal to the one before it; cut a real version for that.

Release notes live at `project-docs/release-notes-v<version>.md` and are the **only** changelog.
There is no `CHANGELOG.md` — it was deleted after v0.2.35-a as a second hand-maintained history that
duplicated the notes and drifted from them. The technical record is `git log`: commit bodies carry
the mechanism, the measurements and the reasoning, which is where a reader who wants that should be
sent. Write the notes from the commit range (`git log v<previous>..dev`), not from another document.

The notes are for someone holding the phone. One line per item, saying what they can now do or what
stopped happening — not how it works. No mechanism, no root cause, no class or file names, no heap
figures or test counts, no bold lead-ins. Name an issue number where a reported fix closes one.
Config paths appear only because people type them.

**Leave out everything that is not visible to a user of the released build**: refactors, extracted
seams, performance internals, and any defect that was introduced and fixed inside the same release
cycle. A reader must never learn that something was briefly broken between two tags.

Since v0.2.35 they are split by edition and the split is the point:

- `release-notes-v<version>.md` is the whole changelog and ships with the `com.termux` release.
  Group under `## New` / `## Changes` / `## Fixes` (and `## Security` when a review is behind it),
  with `###` sub-headings per surface under `## New` (App Drawer, Widgets Page, Keybinds, Terminal,
  Launcher, Extra Keys).
- `release-notes-v<version>-vaj.md` and `-nix.md` carry **only** what is exclusive to that edition
  and link back to the main notes. Do not restate the shared notes.

## Commits, PRs and work artifacts

- Conventional-commit subjects, plain language, imperative, scoped where it helps:
  `fix(statusbar): move Floating's bottom row inward as its corner radius grows`. One concern per
  commit. If the subject wants an "also", split it.
- **Never open a PR or cut a release unless the developer explicitly asks.**
- A PR body explains the problem in a sentence or two, then the fix. List what you ran and what you
  tested it on. Link the issue. UI changes need before/after images; motion needs a short video.
  Call out native-library, signing, bootstrap or compatibility impact explicitly.
- **Durable** plans, designs and decisions go in `project-docs/plans/` and `project-docs/` so the
  next agent finds current facts. **Ephemeral** agent scratch — working notes, todo checklists,
  research dumps — stays out of the worktree. `work-logs/` runtime logs and `.lavish/` review
  documents are gitignored on purpose.
- The merged commit is the implementation record. Do not leave a second checklist behind in the
  repo after the work lands.
