# When a pre-release feature is ready for stable — research

**Date:** 2026-10-14 · **Branch:** `pre` (at `d37a3eeea`, v1.1.0-pre.4) · Numbers marked
**proposal** are this note's suggestions, not industry standards.

**Decided 2026-10-14.** The developer answered §6 and the rules are in `docs/agents/releasing.md`
("From pong to `pre`", "Promoting from `pre` to a stable"): cherry-pick with a re-test; 2 drops and
3 days (7 for AI and data work), a behaviour change restarts the clock; acceptance recorded in the
`release:` commit; a status comment per Experimental line; any stable with a feature is a minor;
pong's results are enough; an off switch for every behaviour change for one stable; the stable notes
list what stays on the channel and the next drop ships with the stable; the pong → `pre` gate is
written down. What follows is the research as it stood before those answers.

The question: at each stable cut, which lines under `## Experimental` in
`project-docs/prerelease-notes.md` go into the stable, which stay on `pre`, and which come out.

## 1. Summary: the proposed rule set

A feature on `pre` is promoted to a stable cut only when every one of these holds; a fix takes the
shorter route at the end.

1. **Soaked**: in at least 2 published drops and at least 3 days since its last behaviour change
   (7 days for AI, memory-budget or data-format work). **Proposal**, from Kubernetes' "two releases
   between beta and GA" and Mozilla's "baked" rule.
2. **Seen**: no `project-docs/device-checks.md` item for it, or the developer has accepted what is
   left, in writing, at the cut.
3. **No open bug**: no open issue or known regression against it from the channel.
4. **Release-type build**: the exact stable candidate, built as R8 `release`, installed and run
   on pong with the feature exercised. A cherry-picked candidate is a new build and must be checked
   again.
5. **Within budget**: frame time and memory measured on pong are no worse than on the stable, and
   an AI feature has a load that is admitted, with no OOM-guard stop.
6. **Swap-safe**: stable → pre → stable on one install keeps the user's data and settings
   (same `versionCode`, so nothing stops a downgrade).
7. **Can be turned off, or is low-risk**: a change to existing behaviour ships with a setting or a
   neutral default (like Shine and Dispersion at zero), or is small enough that a patch release can
   take it back.
8. **Finished**: no open design question, the notes line is written for the stable, user docs and
   help are updated.
9. **All editions**: it was in the Nix and VAJ drops too, and works there.
10. **Stays on pre** when any of 1–9 fails. **Revert from pre** when it breaks the channel with no fix
    in one drop, or has stalled on `pre` for 4 drops or 3 weeks (**proposal**).
11. **Fixes**: a fix for a stable bug lands on `dev`, is verified on a staging or release build,
    and can ship in a patch release at any time. A feature is never shipped as a fix.

## 2. The current process, as found in the repo

### What exists

- **Route.** Content moves `pre` → `dev` → release branches. Features and behaviour changes start on
  `pre`; fixes for the current stable start on `dev` (`AGENTS.md:311-319`,
  `docs/agents/releasing.md:84-87`). On `origin/dev` the same rules are still in `AGENTS.md`
  (around line 385); `docs/agents/releasing.md` exists only on `pre` so far.
- **Promotion.** "Promotion is a merge of `pre` into `dev` when the developer accepts what the
  channel has had a round with"; to hold something back, "revert it on `pre` before merging"
  (`docs/agents/releasing.md:95-100`).
- **Stable gate.** The cut starts by running the device-check list: "cut only once the list is
  empty or the developer has accepted what is left" (`docs/agents/releasing.md:75-76`).
- **Drop gate.** A drop is cut "after the full suite is green on `pre`"
  (`docs/agents/releasing.md:150-152`). The script prints the device checks but does not stop on
  them: "shown, not gated" (`scripts/cut-prerelease.sh:58`).
- **Device checks.** One line per item, deleted when seen; "an item two releases old is re-judged
  and dropped if nothing rides on it"; at most twenty items (`project-docs/device-checks.md:9-13`).
- **Channel swap.** Drops are the same R8 release build type, same key, `versionCode` 1020, so "a
  user therefore installs stable over pre-release or the reverse with no uninstall and keeps their
  data" (`docs/agents/releasing.md:124-127`).
- **R8.** Debug builds skip R8 and 1.0.0 shipped a keep-rule hole; phone installs and nightlies use
  `staging`, which tree-shakes like release but skips R8's optimisations, "the release build is
  still the one that ships" (`AGENTS.md:188-191`,
  `project-docs/active/release-build-type-2026-10-06.md:10-21`).
- **Editions.** "Every release ships all three editions" (`docs/agents/releasing.md:9-11`); drops
  too (`docs/agents/releasing.md:87`).
- **Expectation set for users.** The Experimental section says "These may change or be dropped
  before they reach a stable release" (`project-docs/prerelease-notes.md:22`).

### What practice shows (v1.0.5, 2026-10-14)

- **v1.0.5 was promoted by cherry-pick, not by merging `pre`.** The palette commits on `dev` carry
  "(cherry picked from commit 6d9d906e7…)" (e.g. `5332a159f`), and `dev`'s first-parent history from
  v1.0.4 to v1.0.5 has no merge of `pre`. The written rule (merge, then revert what stays) and the
  practice (pick what goes) differ.
- **Soak was short.** The contact search (`6d9d906e7`) was in pre.1 (2026-10-11 21:06) and shipped
  in v1.0.5 (2026-10-14 00:31): three drops, about 2 days. "Custom starts from the Look on screen"
  (`829719305`) was in pre.2 and pre.3, then shipped: about 1.2 days.
- **Owed checks were still listed when the features shipped.** `device-checks.md` still has PR #64
  items (lines 20-24, 137-138) and the Custom item (lines 120-122) after v1.0.5 shipped them.
  Whether the developer accepted them at the cut is not recorded anywhere I could find.
- **A channel swap lost a feature for a user.** Issue #71 ("pinned app icons are small again"):
  the reporter had the bigger icons on pre.3 and lost them after installing v1.0.5. The owner
  replied that the change should reach a stable "today or tomorrow". The icon-size commits
  (`1cbcc06df` and others) are on pre.3 and pre.4 only, with device checks owed
  (`device-checks.md:51-58`, `144-150`).
- **Features ship in patch releases.** v1.0.5 has a `### New` section; the pre series is
  `1.1.0-pre.N` and "the stable `1.1.0` retires the series" (`docs/agents/releasing.md:107-109`).
- **Audience is small.** Download counts on 2026-10-14 morning: v1.0.5 arm64 APK 15, v1.1.0-pre.4
  arm64 APK 5. There is no telemetry; crashes go to a local crash log (`termux-shared/…/CrashHandler.java`)
  and reach the developer only as GitHub issues.

### Gaps: where the repo has no rule

1. No per-feature promotion criteria. "When the developer accepts what the channel has had a round
   with" is the whole test; "a round" is undefined.
2. No minimum soak time or drop count.
3. The promotion mechanism in the docs (merge plus revert) is not the one used (cherry-pick), and
   the cherry-pick route has no re-verification step for the new combination.
4. Acceptance of owed device checks at a cut is not recorded.
5. No rule for data written by a `pre` feature and read by a stable without it (the swap is
   promised in `releasing.md:124-127` but never tested per feature).
6. No rule for when an Experimental item is dropped, other than the device-check aging rule.
7. The step from pong to `pre` ("after approval and perf/stability checks") is not written down in
   the repo; it exists only in the developer's working notes.
8. No perf or memory gate for promotion, though `scripts/perf-baseline.sh` and the TAI memory
   manager exist.
9. Drift: `releasing.md:131-133` still describes a `## Heading for the next release` section that
   `prerelease-notes.md` no longer has (removed in `3d3358691`).

## 3. What primary sources do

### Kubernetes: feature gates and KEP graduation

- Three stages. **Alpha**: "Disabled by default", "Might be buggy", "Support for feature may be
  dropped at any time without notice". **Beta**: "Usually enabled by default", "The feature is well
  tested", "Support for the overall feature will not be dropped, though details may change".
  **GA**: "always enabled; you cannot disable it"
  ([feature gates, Feature stages](https://kubernetes.io/docs/reference/command-line-tools-reference/feature-gates/#feature-stages)).
- A gate exists to be a kill switch: "A feature gate is intended to be a fast way to neutralize a
  new feature and mitigate damage caused by bugs", and "Disabling a feature gate should prevent any
  further damage caused by bugs in the feature"
  ([community feature-gates.md](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/feature-gates.md)).
- Which changes may skip stages: "Changes that carry a risk of making previously working
  functionality no longer work in certain edge cases should always start as off by default"; small,
  low-risk changes "might skip `Alpha`" (same file, "Lifecycle").
- A newly default-on GA feature "must be disable-able in the first release they are on by default"
  (same file, "GA features").
- KEP graduation examples. Beta: "All known pre-release issues and gaps resolved", "More rigorous
  forms of testing—e.g., downgrade tests". GA: "N examples of real-world usage", "N installs",
  "Allowing time for feedback", "All issues and gaps identified as feedback during beta are
  resolved". And: "Generally we also wait at least two releases between beta and GA/stable,
  because there's no opportunity for user feedback, or even bug reports, in back-to-back releases"
  ([KEP template, Graduation Criteria](https://github.com/kubernetes/enhancements/blob/master/keps/NNNN-kep-template/README.md#graduation-criteria)).
- Production readiness asks: "Can the feature be disabled once it has been enabled (i.e. can we
  roll back the enablement)?", "What happens if we reenable the feature if it was previously rolled
  back?", "Were upgrade and rollback tested? Was the upgrade->downgrade->upgrade path tested?"
  ([KEP template, Feature Enablement and Rollback](https://github.com/kubernetes/enhancements/blob/master/keps/NNNN-kep-template/README.md#feature-enablement-and-rollback)).

### Rust: nightly, beta, stable and stabilisation

- Train: work lands on main and nightly behind a feature flag; beta branches every six weeks; a
  regression found in beta is fixed on main and backported to beta; "If you're using a beta or
  stable release of Rust, you can't use any feature flags"; a feature that misses a train "rides
  the trains" into the next
  ([The Rust Book, Appendix G](https://doc.rust-lang.org/book/appendix-07-nightly-rust.html)).
- The stabilisation report asks, among others: "What outstanding bugs involve this feature? List
  them. Should any block the stabilization?", "Has a 'call for testing' been done? If so, what
  feedback was received?", "Do any known nightly users use this feature?", "What questions were
  left unresolved by the RFC? How have they been answered?", and what docs and tools need updating
  ([stabilization report template](https://rustc-dev-guide.rust-lang.org/stabilization-report-template.html)).
- Documentation is a gate: a complete Reference PR before the lang team considers it
  ([stabilization guide](https://rustc-dev-guide.rust-lang.org/stabilization-guide.html)).
- Library features: stabilise "Once an unstable feature's API design space … has been fully
  explored with no outstanding concerns", after a team FCP
  ([std-dev-guide, stabilization](https://std-dev-guide.rust-lang.org/development/stabilization.html)).
  An FCP "lasts ten calendar days"
  ([rust-lang/rfcs README](https://github.com/rust-lang/rfcs/blob/master/README.md)).
- Neither guide sets a minimum time on nightly (checked in both pages).

### Firefox: Nightly, Beta, Release and uplifts

- Nightly is the channel for experimental features; the beta branch "should now only get patches
  aimed at stabilizing the release". The page as read on 2026-10-14 gives a two-week merge interval;
  older copies and a 2019 announcement give four weeks, so the cadence is not something to copy
  ([Release Process](https://wiki.mozilla.org/Release_Management/Release_Process),
  [hacks.mozilla.org, 2019](https://hacks.mozilla.org/2019/09/moving-firefox-to-a-faster-4-week-release-cycle/)).
- Channel gating in code: `NIGHTLY_BUILD` is set only for Nightly, `RELEASE_OR_BETA` for Beta and
  Release ([Channel-specific build defines](https://wiki.mozilla.org/Platform/Channel-specific_build_defines)).
- Uplift rules: "uplift low-risk fixes for high-severity issues"; "We do not uplift low-severity
  issues, high-risk changes, or new features"; "New feature / feature enablement -> We would not
  uplift, regardless of severity or risk"; a Beta uplift "Must be landed on firefox-main", "Has
  'baked' on firefox-main", and "Cannot reasonably wait for the next train, AND cannot be mitigated
  by disabling a feature via pref/flag"; for Release, "if it can wait, then it should wait"
  ([Uplift rules](https://wiki.mozilla.org/Release_Management/Uplift_rules)).
- Common rejections: "a large change that has not had enough time to bake in nightly or beta",
  "flagged as risky without a clear plan to mitigate risk"
  ([Uplift Request Process Guide (Android)](https://firefox-source-docs.mozilla.org/mobile/android/shared/uplift_guide.html)).

### Chromium: release branches, merges and Blink launches

- Main-first: "generally, all code should land on main then roll out to stable users". A merge
  needs the change verified, "preferably by testing on and monitoring the canary channel for 24
  hours", "unlikely to introduce new regressions", "automated test coverage present"; consider a
  kill-switch. The bar narrows by phase: on stable only "urgent new regressions (especially user
  reports)", urgent release blockers and important security fixes. Flag definitions cannot be
  merged to release branches
  ([merge_request.md](https://chromium.googlesource.com/chromium/src/+/main/docs/process/merge_request.md)).
- Before branch point "new features must be code complete, all strings must be landed"
  ([release_cycle.md](https://chromium.googlesource.com/chromium/src/+/main/docs/process/release_cycle.md)).
- Blink: a feature is built behind a runtime flag, offered as a dev trial, optionally an origin
  trial, then an Intent to Ship with tests, documentation sign-off and compatibility-risk review;
  shipping means "enable the feature by default"; the flag is removed once the feature has "stuck"
  by default, "typically two milestones"
  ([Launching features](https://www.chromium.org/blink/launching-features/)).

### Google SRE: release engineering and canarying

- Release branches with cherry-picks: "we branch from the mainline at a specific revision and never
  merge changes from the branch back into the mainline. Bug fixes are submitted to the mainline and
  then cherry picked into the branch"; "Using this branch and cherry pick method, we know the exact
  contents of each release"; tests are re-run on the release branch "because if a release involves
  cherry picks, the release branch may contain a version of the code that doesn't exist anywhere on
  the mainline"
  ([SRE book, ch. 8 Release Engineering](https://sre.google/sre-book/release-engineering/)).
- Canarying: "Small deployments: Build artifacts should contain small, self-contained changes";
  canary duration must fit release velocity; the canary population "should be sizeable and last
  long enough to be representative"; "We strongly advise running only one canary deployment at a
  time"; useful metrics include memory and latency, not only errors; "Before/After Evaluation Is
  Risky" because time itself changes metrics
  ([SRE workbook, ch. 16 Canarying Releases](https://sre.google/workbook/canarying-releases/)).

### Android and Google Play

- `versionCode` "protect[s] against downgrades by preventing users from installing an APK with a
  lower `versionCode`"; "The `versionName` is the only value displayed to users"
  ([Version your app](https://developer.android.com/studio/publish/versioning)). With both channels
  at 1020, this protection never applies between them: data written by a newer `pre` build will be
  read by an older stable.
- Play test tracks (internal, closed, open) give opt-in pre-release builds; a device gets the
  highest `versionCode` among the tracks it is eligible for
  ([Set up an open, closed, or internal test](https://support.google.com/googleplay/android-developer/answer/9845334)).
- Staged rollouts send an update to a percentage of users; during one "it's a good idea to closely
  monitor crash reports and user feedback"; halting stops new installs but users who have it keep it
  ([Release app updates with staged rollouts](https://support.google.com/googleplay/android-developer/answer/6346149)).
- Android vitals bad-behaviour thresholds: user-perceived crash rate 1.09% overall / 8% per phone
  model, ANR rate 0.47% / 8%, measured per daily active user over 28 days
  ([Android vitals](https://developer.android.com/topic/performance/vitals)). These are Play's
  numbers for apps with Play telemetry; this app has none, so they are context, not a gate.

### Semantic Versioning 2.0.0 and GitHub releases

- "A pre-release version indicates that the version is unstable and might not satisfy the intended
  compatibility requirements" (item 9). Patch: "MUST be incremented if only backward compatible bug
  fixes are introduced" (item 6). Minor: "MUST be incremented if new, backward compatible
  functionality is introduced to the public API"; "MAY be incremented if substantial new
  functionality or improvements are introduced within the private code" (item 7). "Software using
  Semantic Versioning MUST declare a public API" (item 1)
  ([SemVer 2.0.0](https://semver.org/spec/v2.0.0.html)).
- GitHub's pre-release flag notifies "users that the release is not ready for production and may be
  unstable"; without an explicit choice, "the latest release label will automatically be assigned
  based on semantic versioning"
  ([Managing releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)).

## 4. Proposed promotion rules for this project

Apply the checklist to each line under `## Experimental` at a stable cut. A line is **ready**
when every row holds, **stays** when any fails, and is **reverted** under the drop rules below.

### Per-feature checklist

| # | Criterion | Pass when | Source |
|---|---|---|---|
| P1 | Soak | In ≥2 published drops **and** ≥3 days since the last commit that changed its behaviour; ≥7 days for AI, memory-budget, backup/data-format or settings-schema work. A behaviour change restarts the clock; a cosmetic fix does not. **Proposal.** | KEP "wait at least two releases between beta and GA"; Mozilla "baked"; Chromium "24 hours" canary for fixes; SRE: duration fits release velocity. The numbers are project judgement. |
| P2 | Device checks | No item for it in `device-checks.md`, or the developer accepts the remainder by name and the acceptance is written in the stable's `release:` commit body. | `releasing.md:75-76`; KEP beta "All known pre-release issues and gaps resolved". |
| P3 | Open bugs | No open GitHub issue or known regression against it from the channel (issue #71-type reports count). | Rust template "outstanding bugs … Should any block"; KEP GA "All issues and gaps identified as feedback during beta are resolved". |
| P4 | Release build | The stable candidate commit, built as `assembleRelease` (R8, not staging), installed on pong with the feature used once. If promoted by cherry-pick, this is a build that never existed on `pre`: run the full suite on `dev` and repeat the install. | SRE ch. 8 re-run tests on the release branch; v1.0.1 R8 crash (`release-build-type-2026-10-06.md:10-14`). |
| P5 | Perf and memory | Surfaces on the frame path (glass, dock, keyboard, page slides): `scripts/perf-baseline.sh` on pong no worse than the stable at the same settings. AI features: the load is admitted at the intended context, no `oom_guard` stop in `tai logs` in normal use. Compare side by side, not before/after on different days where possible. | SRE workbook: memory and latency as canary metrics, "Before/After Evaluation Is Risky"; KEP scalability questions. Thresholds are project judgement (e.g. the 11 ms frame target in `device-checks.md:128-129`). |
| P6 | Swap-safe | For anything that writes settings, config files, backups or caches: on the emulator, install stable → use → install the drop → use the feature → reinstall the stable → reinstall the drop. No crash, no lost settings, the feature comes back. | KEP "upgrade->downgrade->upgrade path tested"; Android `versionCode` downgrade protection does not apply at 1020; `releasing.md:124-127`. |
| P7 | Off switch or low risk | A change to existing behaviour comes with a setting, or a default that leaves today's look and behaviour as it is (Shine and Dispersion "sit at zero in every shipped Look", `prerelease-notes.md:26`). Otherwise it must be small enough that the next patch release can take it back. | Kubernetes: behaviour-risking changes "should always start as off by default", first default-on release "must be disable-able"; Mozilla "cannot be mitigated by disabling a feature"; Chromium kill-switch. |
| P8 | Finished | No open design question or "may change" caveat; the notes line is rewritten for the stable notes; help and `docs/en/` updated where the feature is user-facing. | Rust "Answers to unresolved questions", Reference PR before stabilisation; Blink documentation sign-off; Chromium "code complete, all strings landed". |
| P9 | Editions | It was in the matching `nix-` and `vaj-` drops; if it touches packages, bootstrap, tlstore or companions, it was run on the Nix edition at least once. | `releasing.md:9-11, 87`. Project-specific judgement. |
| P10 | Developer call | The developer says yes for this line. | `releasing.md:96`. |

### Stays on pre

- Any of P1–P10 fails.
- It depends on another Experimental item that is not being promoted (promote both or neither).
- Its evidence comes from one device only and the feature depends on device class (memory budget,
  GPU, cutout, screen width). TAI features measured only on pong are an example. Project judgement;
  Kubernetes asks for proof "in representative production environment" before default-on.
- The developer is still changing it. Rust: stabilise once the design space "has been fully
  explored with no outstanding concerns".

### Revert from pre

- It crashes, freezes the phone, loses data or breaks an existing feature on the channel, and no fix
  is in the next drop: revert it on `pre` (the mechanism already exists, `releasing.md:99-100`).
  Kubernetes alpha: support "may be dropped at any time"; the Experimental header already tells
  users the same (`prerelease-notes.md:22`).
- It has stayed on `pre` for 4 drops or 3 weeks with its blocker unchanged: re-judge, and revert if
  nothing rides on it. **Proposal**, mirroring the device-check aging rule (`device-checks.md:12`).
- It blocks the promotion of unrelated work (shared files, entangled commits).

### Fixes versus features

Every source treats fixes and features differently:

- **Features ride the train; they are not uplifted.** Mozilla will not uplift "New feature /
  feature enablement … regardless of severity or risk"; Rust stabilises a feature on nightly and
  lets it ride; Chromium accepts no new flag definitions on a release branch.
- **Fixes may move faster, with conditions.** Mozilla: low risk, high severity, landed and baked
  first, cannot wait for the next train. Chromium: landed on main, verified (24 h on canary
  preferred), test coverage, narrower bar the closer to stable. Rust: fix on main, then backport to
  beta.

For this project the fix route is already right in direction: a stable bug is fixed on `dev` and
the next drop merges it into `pre` (`AGENTS.md:318`). Proposed additions:

- A fix shipped in a patch must be verified on a staging or release build before the tag (the
  equivalent of "verified in Nightly"), and should be narrow; a large fix waits for the next drop
  and is treated as a feature.
- A change that alters working-as-designed behaviour is a feature even when a user calls it a fix
  (issue #71's "bigger icons" is a feature request about a `pre` feature). `AGENTS.md:317` already
  routes "any change in behaviour" to `pre`.
- A fix to a promoted feature that only `pre` has seen goes to `pre`; if the feature is already in
  a stable, the fix goes to `dev`.

### Promotion mechanism

The sources split into two models:

- **Whole train** (Firefox, Rust, Chromium): everything on main moves to beta and stable; unready
  work is hidden behind a flag or channel define. Needs a flag system, which this app does not have.
- **Branch plus cherry-pick** (SRE ch. 8): pick what ships, know the exact contents, re-test the new
  combination.

This project documents a third (merge `pre` into `dev` after reverting what stays,
`releasing.md:95-100`) and used the second for v1.0.5. Either works if it is written down; the
cherry-pick route needs P4's re-verification, because the stable becomes a combination nobody
ran on the channel.

## 5. Small-project calibration

| Big-org practice | Here | Why |
|---|---|---|
| Feature-flag framework, Finch, Nimbus, channel defines | Skip | No server and no telemetry. A user-visible setting or a neutral default does the same job for the few features that need one (P7). |
| Percentage rollouts (Play staged rollout) | Skip | GitHub releases have no percentage. `pre` is the canary population and pong is most of it. |
| Statistical canary analysis, vitals thresholds | Skip as gates | About 5–15 downloads per APK (2026-10-14). One crash is a large percentage, and no report is weak evidence: the SRE workbook wants a canary "sizeable" and "representative". The developer's device checks carry most of the signal, so P2 matters more than P3. |
| Fixed train cadence (Rust six weeks, browsers two or four) | Skip the calendar, keep a minimum soak | Cuts are on demand. A minimum (P1) keeps "two releases between beta and GA" without a calendar. |
| Multiple approvers, Intent to Ship, FCP | Skip | One developer. Keep the written record instead: the acceptance line in the `release:` commit (P2). |
| "N installs", "N examples of real-world usage" | Replace | Use "the developer used it daily on pong for the soak period" as the real-use signal. |
| Downgrade testing | **Keep, strengthened** | The pinned `versionCode` makes every channel swap a possible downgrade with no Android guard (P6). |
| Re-test the exact release combination | **Keep** | Cheap, and the R8 crash in 1.0.0/1.0.1 is the precedent (P4). |
| One canary at a time; small changes | Keep loosely | Avoid changing a feature in the drop right before the cut; that restarts P1. |
| Docs and notes as a gate | Keep | Already the house style; cheap to check. |

SemVer note: SemVer reserves the patch number for "only backward compatible bug fixes" and asks
for a declared public API. v1.0.5 shipped new features as a patch. Whether this app's public API
is `launcherctl`, the config files and keybinds, or nothing, is a project decision; the
calibration question is whether promoted features wait for `1.1.0` or go into `1.0.x`.

## 6. Open questions for the developer

1. Promotion mechanism: keep the documented merge-and-revert, or write down the cherry-pick route
   used for v1.0.5 (with P4's re-test)?
2. Soak numbers: are 2 drops / 3 days (7 for AI and data work) acceptable, and does a behaviour
   change restart the clock?
3. Where should acceptance of owed device checks be recorded at a cut: the `release:` commit body,
   or by deleting the line with a note?
4. Should each Experimental line carry a hidden status (`<!-- ready | stays: reason | drop -->`) so
   the cut is a review of that list?
5. Versioning: do promoted features ship in `1.0.x` patches, or wait for `1.1.0` (which retires the
   pre series)? Today both happen.
6. Is pong-only evidence enough to promote AI and memory-budget features, or should a second device
   class (Surface tablet, emulator) run them first?
7. Should a promoted feature that changes existing behaviour keep an off switch for one stable
   (the Kubernetes "disable-able in the first release" rule)?
8. Should a stable and a drop be published together (v1.0.5 and pre.4 were ten minutes apart), and
   should the stable's notes tell pre users which Experimental items they will lose by switching
   (issue #71)?
9. Should the pong → `pre` gate ("approval plus perf/stability checks") be written into
   `docs/agents/releasing.md` next to these rules?

## 7. Sources

All read on 2026-10-14.

- Kubernetes, Feature Gates, "Feature stages":
  https://kubernetes.io/docs/reference/command-line-tools-reference/feature-gates/#feature-stages
- Kubernetes community, Feature Gates in Kubernetes:
  https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/feature-gates.md
- Kubernetes enhancements, KEP template (Graduation Criteria, Production Readiness Review):
  https://github.com/kubernetes/enhancements/blob/master/keps/NNNN-kep-template/README.md
- The Rust Programming Language, Appendix G (release channels):
  https://doc.rust-lang.org/book/appendix-07-nightly-rust.html
- rustc-dev-guide, Stabilization guide: https://rustc-dev-guide.rust-lang.org/stabilization-guide.html
- rustc-dev-guide, Stabilization report template:
  https://rustc-dev-guide.rust-lang.org/stabilization-report-template.html
- std-dev-guide, Stabilizing a feature: https://std-dev-guide.rust-lang.org/development/stabilization.html
- rust-lang/rfcs README (FCP length): https://github.com/rust-lang/rfcs/blob/master/README.md
- Mozilla, Release Process: https://wiki.mozilla.org/Release_Management/Release_Process
- Mozilla, Patch uplifting rules: https://wiki.mozilla.org/Release_Management/Uplift_rules
- Firefox source docs, Uplift Request Process Guide (Android):
  https://firefox-source-docs.mozilla.org/mobile/android/shared/uplift_guide.html
- Mozilla, Channel-specific build defines: https://wiki.mozilla.org/Platform/Channel-specific_build_defines
- Mozilla Hacks, Moving Firefox to a faster 4-week release cycle (2019):
  https://hacks.mozilla.org/2019/09/moving-firefox-to-a-faster-4-week-release-cycle/
- Chromium, Merge request process:
  https://chromium.googlesource.com/chromium/src/+/main/docs/process/merge_request.md
- Chromium, Release cycle:
  https://chromium.googlesource.com/chromium/src/+/main/docs/process/release_cycle.md
- Chromium, Launching features (Blink): https://www.chromium.org/blink/launching-features/
- Google SRE book, ch. 8 Release Engineering: https://sre.google/sre-book/release-engineering/
- Google SRE workbook, ch. 16 Canarying Releases: https://sre.google/workbook/canarying-releases/
- Android developers, Version your app: https://developer.android.com/studio/publish/versioning
- Android developers, Android vitals: https://developer.android.com/topic/performance/vitals
- Play Console Help, Set up an open, closed, or internal test:
  https://support.google.com/googleplay/android-developer/answer/9845334
- Play Console Help, Release app updates with staged rollouts:
  https://support.google.com/googleplay/android-developer/answer/6346149
- Semantic Versioning 2.0.0: https://semver.org/spec/v2.0.0.html
- GitHub Docs, Managing releases in a repository:
  https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository

Not reached or not usable: the Firefox source-docs desktop uplift page
(`firefox-source-docs.mozilla.org/contributing/uplift.html` returned 404; the wiki uplift rules
were used instead); Chromium's Finch documentation is internal to Google and was not used; F-Droid
and Obtainium handling of pre-releases was not researched, because `releasing.md` names neither as
a distribution channel.
