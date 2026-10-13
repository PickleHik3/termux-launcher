# Releasing

Editions, versions, tags, the stable cut, the pre-release channel and the release notes. AGENTS.md
("Branches and releases") has the table of which branch each kind of work starts on; this file has
everything a release or a version string needs beyond it.

## Editions and their branches

**Every release ships all three editions.** A cut is not finished when `main` is tagged — `dev`
goes to `nix-edition` and `io-vaj-package` in the same pass, each with its own tag, notes and APK
run. Do not ask which editions to release; releasing one is the thing that needs a reason.

Each edition has its own release branch, which receives `dev` by merging:

- `main` — the Termux edition (`com.termux`). Merge `dev`, tag `vX.Y.Z`.
- `nix-edition` — the Nix edition (`com.termux.launcher.nix`), tag `nix-vX.Y.Z`, published with
  `--latest=false` (not `--prerelease`: that flag belongs to the pre-release channel below, and
  CI refuses a stable tag that carries it). Backed by the PickleHik3/nix-on-droid fork, branch `launcher-nix`; bootstrap zips live
  on the `nix-bootstrap` tag. Companion apps (TLNix API/Styling/Boot) release from their `nix-pkg`
  branches via `github_release_build.yml` with `nix-v*` tags.
- `io-vaj-package` — the VAJ edition (`io.vaj.tl`), tag `vaj-vX.Y.Z`. **The demo edition, and the
  least recommended one to install.** Its packages come from the developer's own apt repository
  (`repo.pathayam.xyz`), which is updated sometimes, with no promises. The security-only freeze it
  carried from v0.2.34-vaj to v0.2.36-vaj is over — it gets every release like the others — but
  "not frozen" is not "supported", and nothing should describe it as maintained, revived or
  production-ready.

## Companion apps

The **companion apps** are forks of the Termux plugins, one branch per edition, released on their
own cadence and not part of a launcher cut: PickleHik3/termux-api, termux-styling and termux-boot,
branches `master` / `nix-pkg` / `io-vaj-package`, tags `vX.Y.Z` / `nix-vX.Y.Z` / `vX.Y.Z-vaj`. They
exist because a plugin is only granted the launcher's permissions when it joins that edition's
`sharedUserId` and carries the same signature, so each edition needs its own build. Every one of
them is **signed with the shared `testkey_untrusted.jks`**, the same key the launcher's own
published APKs use — that is what makes them pair, and why an F-Droid plugin never will. Since
1.0.0 the launcher and the companions publish the **release build type** (not debuggable, so no
`run-as` into the shared uid's files) under that key; the per-commit CI builds stay debug. In
termux-boot the edition is three values at the top of `app/build.gradle`; nothing else in that tree
names a package. Their `targetSdk` must stay at the launcher's 28, per the shared-user rule in
AGENTS.md ("Things already decided").

## Versioning and tags

**Versioning.** One plain `X.Y.Z` `versionName` for every edition — no `-nix` / `-vaj` suffix, no
`+hotfixN`, no `-a`. The edition is a separate fact: it is derived from the package name and shown
as an `Edition` line in the About screen and every report, and it is part of the tag and the APK
name. `versionCode` stays **1020** for upstream parity — never change it. The current release is
**1.0.4**.

- **Tags** carry the edition as a prefix: `vX.Y.Z` (Termux edition, `main`), `nix-vX.Y.Z`
  (`nix-edition`), `vaj-vX.Y.Z` (`io-vaj-package`). CI (`attach_debug_apks_to_release.yml`) strips
  the prefix and the `v` and aborts unless the rest equals `versionName`.
- **Hotfixes bump the patch number** (`1.0.1`) and are a
  real version with real notes, on every edition.
- **Nightlies** (`debug_build.yml`) build as `X.Y.Z+dev.<shortsha>`: semver build metadata, so a
  nightly sorts equal to the release it follows. That string is the in-app `versionName` and goes
  verbatim into the APK name. The TAI bench groups results by the `X.Y.Z` core only.
  Pushes to `dev` prune artifacts to the newest two runs per live branch (`prune_artifacts.yml`), so
  a nightly APK must be fetched from one of the last two runs or built from the tag.
- **APK names** follow one pattern, release and nightly alike:
  `termux-app_v<versionName>_<edition>_<variant>_<abi>.apk`, with `<edition>` one of `termux`,
  `nix`, `vaj`; e.g. `termux-app_v1.0.0_termux_apt-android-7_arm64-v8a.apk` and
  `termux-app_v1.0.0+dev.a1b2c3d_termux_apt-android-7_arm64-v8a.apk`. Local builds keep the plain
  `termux-app_apt-android-7-debug_<abi>.apk` name.
- **Legacy tags** (`v0.2.x-nix`, `v0.2.x-vaj`, `v0.2.37+hotfix1`, `v0.2.35-a`) stay as history and
  are not renamed.

**Each release branch owns its `versionName`; `dev`'s is not authoritative** (it can sit a release
or more behind). The last shipped version is the one on `main`, `nix-edition` and
`io-vaj-package`, or the latest tag — read it there, never from `dev`'s `app/build.gradle`.

## Cutting a stable release

First `project-docs/device-checks.md`: run its Agent items, hand its Developer items over, and
cut only once the list is empty or the developer has accepted what is left. Then, per edition, as in `Release v1.0.1 (merge dev into io-vaj-package)` followed by `release: vaj-v1.0.1`:
merge `dev`, then set `versionName` to the plain `X.Y.Z` in a `release:` commit (it **must** equal
the tag minus its edition prefix and `v`, or CI aborts), push, tag, `gh release create --notes-file …`
(`--latest` on `vX.Y.Z`, `--latest=false` on the edition tags, never `--prerelease` on a stable),
then dispatch `attach_debug_apks_to_release.yml` with the tag. Publishing the plain `vX.Y.Z`
(or `vX.Y.Z-pre.N`) release fires `announce_release_discord.yml`, which posts once to Discord
linking all three edition releases, so create the three in one pass and the plain one last.

## The pre-release channel

A second, rolling channel beside the stable releases, and **the first stop for new work**: the
order is `pre` → `dev` → the release branches. Like a stable, a drop ships all three editions.
`scripts/cut-prerelease.sh` performs a drop end to end, printing `project-docs/device-checks.md`
first so a drop never ships an unseen item unknowingly; these are the rules it encodes, and the
ones to keep if it changes:

- **`pre` is the channel's own, persistent branch.** Feature work and pull requests that are
  bound for the channel target `pre` and merge there; nothing is force-pushed. A drop first brings
  `pre` level with `dev` (merged `--no-ff`), then merges any extra branch named on the command
  line, then adds one `release: vX.Y.Z-pre.N` commit setting `versionName`. **Promotion is a
  merge of `pre` into `dev`** when the developer accepts what the channel has had a round with.
  Keep `dev`'s `versionName`: `dev` has usually not touched that line since the last drop, so git
  takes `pre`'s `-pre.N` value without a conflict. Check `app/build.gradle` before committing the
  merge, and put `dev`'s value back if needed. To leave something out of a promotion, revert it on
  `pre` before merging. A fix for the current stable lands on `dev` (see the table above), and the
  next drop merges it into `pre`.
- **`pre-nix` and `pre-vaj` are disposable build branches**: rebuilt per drop in worktrees from
  `origin/nix-edition` and `origin/io-vaj-package` with `pre` merged in, keeping their edition's
  identity exactly as a release cut does (the script checks `applicationId` after the merge and
  stops on a conflict for you to resolve to the edition's side). They are never pushed; their tags
  carry the commits. Never merge them anywhere.
- **Version `X.Y.Z-pre.N`.** `X.Y.Z` is the next minor above the current stable (`1.1.0-pre.1`
  while 1.0.x is current). A stable patch (`1.0.5`) does not change it; the stable `1.1.0` retires
  the series and the next drop is `1.2.0-pre.1`. `N` counts drops from 1 and is never reused. It is
  a semver prerelease, so it sorts above every 1.0.x and below 1.1.0; `validateVersionName`, CI and
  the nightly regex all accept it. `dev`'s own `versionName` is untouched. Between drops `pre`
  carries the last drop's version, so nightlies from `pre` build as `X.Y.Z-pre.N+dev.<sha>`; the
  script aligns that line with `dev`'s before each merge so it never conflicts.
- **Tags `vX.Y.Z-pre.N`, `nix-vX.Y.Z-pre.N`, `vaj-vX.Y.Z-pre.N`**, annotated like the stable
  tags. Every GitHub release is created `--prerelease --latest=false`, so none becomes "Latest" and
  the website's download links (which skip prereleases) never point at one. CI aborts when a
  `-pre` tag's release lacks the flag. The plain tag is created last: its publish fires the Discord
  post that links the other two.
- **One visible pre-release per edition.** Publishing a drop retires the one before it: its three
  releases become drafts (invisible to users, APKs and tags kept, so a bug report that names
  `1.1.0-pre.1` can still be reproduced from the draft), and drafts beyond the newest two drops
  per edition are deleted, tag kept. The script does this after each edition's release is created;
  nothing else touches old releases, and stables are never retired.
- **Same key, same `versionCode`.** A drop is the same R8-shrunk, non-debuggable release build
  type as a stable, signed with `testkey_untrusted.jks`, with `versionCode` 1020. A user therefore
  installs stable over pre-release or the reverse with no uninstall and keeps their data; the
  companion apps pair with both.
- **Notes are rolling**: `project-docs/prerelease-notes.md`, kept on `pre` (and so on `dev` after
  a promotion), in the release-notes voice, listing everything the channel carries beyond the
  **latest stable** — a `## New in this drop` part first (only what this drop adds over the
  one before it, checked against `git log v<previous drop>..pre` and replaced whole at every
  drop), a `## Heading for the next release` part (what `dev` has since the last
  stable tag), a `## Experimental` part (what is on `pre` and not yet on `dev`) and a closing
  `## Editions` list with only what is exclusive to Nix or VAJ, as in the stable notes. The whole
  file is every drop's release body (its guidance sits in HTML comments, which GitHub does not
  render), so each drop restates the list. When the stable ships, the lines that shipped move into
  `release-notes-v<version>.md` and out of this file; what has not shipped stays.
- **tlstore** compares `min-launcher` numerically, so `min-launcher=1.1.0` is satisfied on
  `1.1.0-pre.N` builds: a store item can name the stable it will ship in and still show in the
  channel. On `1.0.x` it stays hidden.
- Bug reports from the channel: ask for the version string from About, which ends in `-pre.N`;
  the fix goes to `pre`, or to `dev` when the current stable has the bug too.

The cut, as the script does it: `scripts/cut-prerelease.sh X.Y.Z-pre.N [branch …]` opens `pre`
in a worktree, merges `dev` and the named branches, sets `versionName`, commits and tags, builds
the two edition branches the same way, and prints what it would publish; `--publish` additionally
pushes `pre` and the three tags, runs `gh release create --prerelease --latest=false --notes-file
project-docs/prerelease-notes.md` for each and dispatches `attach_debug_apks_to_release.yml` on
each tag. Merges already done are skipped, so after resolving a conflict in a worktree you re-run
the same command; `--only nix` (or `termux`, `vaj`) redoes one edition. A dry run leaves local
tags: discard them (the script prints how) before publishing. Run it with the notes already
describing the drop, after the full suite is green on `pre`.

## Release notes

Release notes are the **only** changelog, and they live in exactly one place at a time:

- While a release is being written, its notes are `project-docs/release-notes-v<version>.md` — one
  file, so `gh release create --notes-file` can point at it.
- While something is in the pre-release channel and not yet in a stable, its line is in
  `project-docs/prerelease-notes.md` (above). A stable cut starts from that file: what ships moves
  out of it into `release-notes-v<version>.md`.
- **Once every edition of that version is published, move the notes into
  `project-docs/release-notes.md`** (newest first, `## v<version>`, the per-edition notes folded
  into a closing `### Editions` list) and delete the per-version file. Moving, never copying: the
  earlier split into eleven files, and the `CHANGELOG.md` deleted after v0.2.35-a, both failed the
  same way — a second hand-maintained history that duplicated the notes and drifted from them.

Entries in `release-notes.md` are kept as they shipped and are not revised afterwards; an old entry
describes its own release, not today. The technical record is `git log`: commit bodies carry the
mechanism, the measurements and the reasoning, which is where a reader who wants that should be
sent. Write the notes from the commit range (`git log v<previous>..dev`), not from another document.

The notes are for someone holding the phone. One line per item, saying what they can now do or what
stopped happening — not how it works. No mechanism, no root cause, no class or file names, no heap
figures or test counts, no bold lead-ins. Name an issue number where a reported fix closes one.
Config paths appear only because people type them.

**Leave out everything that is not visible to a user of the released build**: refactors, extracted
seams, performance internals, and any defect that was introduced and fixed inside the same release
cycle. A reader must never learn that something was briefly broken between two tags.

Editions are a section, not a file. The notes for a version are one document:

- The body is the whole changelog and ships with the `com.termux` release. Group under `## New` /
  `## Changes` / `## Fixes` (and `## Security` when a review is behind it), with `###` sub-headings
  per surface under `## New` (App Drawer, Widgets Page, Keybinds, Terminal, Launcher, Extra Keys).
- A closing `## Editions` list carries **only** what is exclusive to the Nix or VAJ build. Do not
  restate the shared notes there. When an edition has nothing exclusive, say so — a reader must be
  able to tell "nothing was exclusive" from "nobody wrote it down".
- Cutting the `-nix` and `-vaj` releases: pass the same notes file, or an extract of it, to
  `gh release create`. There is no separate per-edition file to write.
