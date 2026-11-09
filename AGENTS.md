# Termux Launcher

A fork of Termux that is also an Android **home launcher**: the terminal is the home screen, with
the app drawer, dock, widgets, status chrome and an embedded keyboard drawn around a live session,
all themable per surface (blur, opacity, grain, corner radius, edge gap).

These are defaults, not scripture. The developer's stated preference overrides them; if a rule
fights the task, say so rather than working around it quietly.

## What we never compromise on

**It is the home screen.** Android returns to it whenever anything closes, it holds the user's
shell sessions, and a crash is the home screen falling over mid-touch.
- `am force-stop` is not a clean restart: the system relaunches the launcher at once and it reads
  shared preferences on the way up. Edit prefs (`run-as` + `sed`) before the force-stop.
- Null-tolerance at input boundaries is a feature, not clutter.
- Never gate window drawing without a fail-safe (the keyboard reveal gate has three: frame count,
  160 ms backstop, null gate view). A gate that can hang leaves a black home screen.

**Memory discipline.** A process that never dies pays for every retained byte forever; a live
terminal idles at about 185–200 MB PSS on pong, and that ground is not given back. Anything holding
pixels (kitty frames, app icons, wallpaper blurs) sits behind a bounded store with an explicit
budget (`KittyImageStore.FrameBudget`, `LauncherIconStore`). Decide cost from the pixels a
drawable actually holds, never its declared size: `AdaptiveIconDrawable` reports 72dp while its
layers hold 108dp.

**It has to feel right.** No continuously repainting animations, no layout work per frame; prefer
structural fixes (derive the position, own the outline) over tuning durations. Before a change
that keeps pixels or memory, runs per frame or at rest, or sits on start-up or the page-change
path, read [`docs/agents/performance.md`](docs/agents/performance.md). When the developer says
"janky" they mean it looks bad, not that it dropped frames: read UI complaints as aesthetic first.

**Three editions, fed only by merging.** Content reaches `main`, `nix-edition` and
`io-vaj-package` only by merging `dev`; a release branch carries only its identity
(applicationId, `TERMUX_PACKAGE_NAME`, `versionName`, manifest placeholders, ABI rules, bootstrap
handling, CI matrix). Each edition has its own package and prefix, so name the package through
`context.getPackageName()` or `TermuxConstants`, never a literal `com.termux`.

## Words

The developer is the maintainer, who uses the launcher daily. **Pong** is their phone and the
device of record. Every other name (a place, surface, gesture, edition) is in
[`docs/GLOSSARY.md`](docs/GLOSSARY.md); look it up before naming it in code, a commit or a string.

## The five ways to hurt yourself

1. **`gh` talks to the wrong repo.** The `upstream` remote is `termux/termux-app` and `gh` resolves
   to it. Pass `--repo PickleHik3/termux-launcher` to every `gh` command.
2. **The developer edits this tree while you work.** A compile error in a file you did not touch
   is probably theirs: retry the build; do not fix or revert their half-typed code.
3. **Committing bootstrap zips.** `*.zip` is gitignored for a reason; no APK carries a bootstrap
   (the app downloads and verifies it on first run). One committed zip cost a history rewrite.
4. **Touching a real phone without being asked.** Ask before installing, force-stopping or
   injecting input on pong, and pass `-s <serial>` to every adb command (bare `adb` often reaches
   the phone). A stray tap once zeroed the developer's side gap on every surface.
5. **Merging `dev` into an edition branch** carelessly. Expect conflicts in `app/build.gradle`,
   `app/src/main/res/values/strings.xml` and `.github/workflows/debug_build.yml`; resolve identity,
   `versionName` and the push trigger to the edition's side, and diff `app/build.gradle` against
   the previous edition tag before tagging.

## Hit every surface

The most common defect here is a change that works on the path you tested and nowhere else.
Before calling a change done, say which of these applied:

- **Dock styles.** Docked and Floating resolve geometry differently on purpose (Docked is square at
  rest; Floating is already a 26dp card). Do not unify them into one formula.
- **Keyboard up and down.** The in-app keyboard changes terminal bounds, dock lift, flush-dock
  absorption and which surface the nav strip mirrors.
- **One pane and many.** Small panes clamp their own radius (`PaneShape.radiusForBounds`).
- **Inherited and detached.** Every surface property can follow Base or override it; setters
  write through the link on purpose.
- **Reverse states.** A way in needs a way out and a way to see it.
- **Themes and scheme.** Light, dark, black, Material You, and the launcher's scheme chrome
  (`LauncherSchemeTheme.isSchemeChromeActive()`); chrome that reads system palette resources
  directly bypasses the scheme.
- **Density and font scale.** Overlap reports are usually text: single-line + ellipsize labels,
  `minWidth=0` and autosize on toggle segments.
- **Editions.** Anything touching applicationId, bootstrap, manifest or signing needs a decision
  per edition.
- **The system IME.** Every path that shows it calls `TermuxActivity.onSystemImeRequested()`
  first, or the `mAcceptSystemImeInsets` gate drops its insets.

## Where code lives

- `app/` (Java, `com.termux`): `app/` holds `TermuxActivity` and the dock and glass views;
  `app/terminal/` panes, find/copy, `inappkeyboard/`; `app/surfaces/` the Appearance editor;
  `app/layouteditor/` the layout session (`LayoutEditorController`, `LayoutEditorPlan`);
  `app/launcher/` catalogue, icons, paging, A–Z; `app/chrome|dock|statusbar|notice|theme|settings|tour/`;
  `ai/`, `launcherctl/`, `privileged/` ([lane rules](docs/agents/privileged-lane.md)), `filepicker/`.
- `terminal-emulator/` (upstream-shaped), `terminal-view/`, `termux-shared/` (constants,
  `TermuxAppSharedPreferences`, which also resolves surface inheritance).
- Vendored: `termux-am-library/`, `inapp-keyboard/` (Unexpected-Keyboard, package
  `juloo.keyboard2`; every local deviation goes in `inapp-keyboard/UPSTREAM.md`).
- `docs/` user guides, ADRs, agent docs and glossary; `project-docs/` developer specs
  (`active/`, `reference/`, `verification/`, `backlog.md`; index in `project-docs/README.md`).
- Agent docs, read when the task needs them: [building and installing](docs/agents/building.md),
  [testing and device checks](docs/agents/testing.md), [releasing](docs/agents/releasing.md),
  [performance](docs/agents/performance.md), [device map](docs/agents/device-map.md),
  [tlstore](docs/agents/tlstore.md), [issue tracker](docs/agents/issue-tracker.md).

## Building and verifying

- Fast loop: `./gradlew :app:assembleDebug`; on a phone: `:app:assembleStaging` (it runs R8 like
  release); tests: `./gradlew testDebugUnitTest`. Details and pong's install tiers are in
  [building.md](docs/agents/building.md).
- Prove the change with the smallest check that shows it works, then stop: the tests you touched,
  then the module suite before handing work over. CI owns the rest.
- If the cause of a bug is plain from the code, fix it. If not, or one fix has already missed,
  reproduce the failing state first (a failing test, a log line, a measured value). Fixes ship
  with a focused regression test.
- Pong is the truth for layout and motion; the emulator is for throwaway shots.
- Say what you verified and on what; anything owed goes on `project-docs/device-checks.md` or
  `developer-checks.md` in the same commit ([testing.md](docs/agents/testing.md)).
- Compile-check an API claim before relying on it (`UserHandle.getIdentifier` looks public and is
  `@SystemApi`).

## Coding style

Follow `.editorconfig` (UTF-8, LF, four spaces, two for YAML) and the surrounding code.

- **Extend the seams, not `TermuxActivity`.** New activity-facing behaviour goes through a Host
  interface or a deep module (`TerminalHost`, `ChromeRenderer`, `DockLayoutPolicy`,
  `SurfaceEditorController.Host`).
- Keep geometry and policy pure and tested (`DockLayoutPolicy`, `PaneShape`, `AzScrubGesture`);
  views just apply the answer.
- **One owner per two-phase number.** A number one phase predicts and a later phase settles
  (keyboard reveal, dock lift, terminal room) comes from one pure policy both read, with a parity
  test. See `project-docs/active/motion-invariants-2026-10-30.md`.
- No magic padding, no negative margins, no per-size special cases. A number that appears twice
  is a constant that appears once.
- A layout never requests another layout from inside measure, layout or draw: compare before
  setting text, visibility, minimum sizes or layout params.
- Comments say how a thing is used. Don't keep complexity because it exists, or add machinery
  because it looks impressive.

## User-facing text

Every string a user sees is product copy: a short title and one sentence saying what the user
gets. No mechanism, protocol names, error codes or design history. "Arrange panes automatically
as you open and move them", not "tiles like Hyprland". Deeper detail goes in `docs/en/`.

## Things already decided

Raise these if you think they are wrong; do not silently undo them.

- No negative or concave corner radius.
- The Sessions sidebar is legacy: no Sessions tab in the editor, no dot tab badges.
- The Appearance editor commits only on Done. Its card sheet is always present at peek, half or
  full (no ✕); Back above peek collapses to peek, at peek it goes through the unsaved-changes
  dialog; dirtiness compares against the entry snapshot. Views raised over the editor need a Z
  above the sheet's elevation; `bringToFront` alone draws under.
- The editor collapses the status pane on entry, so a status-pane control draws its own preview.
- A stored `-1` corner radius means "follow the style", resolved only in
  `resolveAutoCornerRadiusDp`. Never give `DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS` or
  `DEFAULT_STATUS_BAR_CORNER_RADIUS` a real number.
- Shipped defaults are pinned for existing installs (`adoptShippedSurfaceDefaults()`; a store
  holding only `log_level` is a fresh install).
- `SettingsLayoutUtils.applyItemLayout` overwrites every preference's layout; a preference with
  its own layout goes on the exemption list.
- Pane inner padding favours rows; change the rule only in `PaneShape` (`verticalInsetPx`,
  `sideInsetPx`) with its tests.
- `targetSdkVersion` stays 28 on every edition and every companion app: from sdk 29 the
  `sharedUserId` group lands in `untrusted_app` and the kernel denies exec in `$PREFIX`, and one
  newer companion bricks the launcher until every member is uninstalled.
- No exec fallback through the system linker (tried in 8ea87965, removed): exec failures surface
  where they happen.

## Branches, commits and PRs

| The work | Branch from and merge into | Reaches users as |
|---|---|---|
| A feature, or any change in behaviour | `v2-redesign` | the next pre-release drop (cut by merging `v2-redesign` into `pre`), then the next stable |
| A fix for something the current stable has | `dev` | the next patch release |
| A fix for something only the channel has | `v2-redesign` | the next drop |

The main checkout sits on `v2-redesign` and is shared with the developer and other sessions:
branch work happens in a worktree. The local `pre` is the developer's testing branch; never
merge into it, push it or reset it.

- Read [releasing.md](docs/agents/releasing.md) before cutting a release or drop, writing release
  notes, reading a version string from a bug report, or touching an edition branch.
- Conventional-commit subjects, plain and imperative, one concern per commit:
  `fix(statusbar): move Floating's bottom row inward as its corner radius grows`.
- Never open a PR or cut a release unless the developer asks. A PR body states the problem, the
  fix, what you ran, and any native-library, signing, bootstrap or compatibility impact.
- Durable plans and decisions go in `project-docs/active/`, then `project-docs/reference/` once
  delivered. Agent scratch stays out of the tree; the merged commit is the record.
- Never commit `build/` or `.gradle/` content.

Issues: GitHub on `PickleHik3/termux-launcher` ([issue-tracker.md](docs/agents/issue-tracker.md),
[triage-labels.md](docs/agents/triage-labels.md)). Domain terms and ADRs:
[domain.md](docs/agents/domain.md).
