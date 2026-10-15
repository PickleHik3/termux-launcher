# How Obtainium picks our releases — research

**Date:** 2026-10-14 · **Branch:** `pre` (at `d37a3eeea`, v1.1.0-pre.4) · **Obtainium source read:**
`ImranR98/Obtainium` at `af286fa8d31d7406d6db167e2314d376d74f7696` (2026-09-13), cloned read-only.
Everything marked **proposal** is this note's suggestion; nothing in the repo or the release
process was changed.

The question: with all three editions and both channels published as releases of one GitHub repo,
does Obtainium give a pre-release user every drop and every stable, give a stable user only
stables, and never hand anyone another edition's APK? And what is the least we can change so it
never gets confused across the six combinations (Termux / Nix / VAJ × stable / pre-release)?

Citations: `GH:` = `lib/app_sources/github.dart`, `VS:` = `lib/services/version_service.dart`,
`VN:` = `lib/utils/version_normalization.dart`, `AF:` = `lib/services/apk_filter_service.dart`,
`AS:` = `lib/app_sources/app_source.dart`, `SP:` = `lib/providers/source_provider.dart`, all at the
commit above (permalink base:
`https://github.com/ImranR98/Obtainium/blob/af286fa8d31d7406d6db167e2314d376d74f7696/`).

## 1. Summary

- **Obtainium ignores GitHub's "Latest" badge.** It fetches `/releases?per_page=100`, sorts by
  **publish date** (the default sort), and walks from the newest: it skips drafts, skips
  pre-releases unless "Include prereleases" is on, skips any release without a usable APK, and
  takes the first one left. Edition, tag prefix and semver play no part in the choice. `--latest`
  / `--latest=false` only matter if a user turns on "Verify the 'latest' tag" (off by default).
- **So, with default settings, a user gets whichever edition was published last.** Replaying the
  real release history through that rule (§3.6): a Termux stable user was offered **vaj-v1.0.2**
  and **vaj-v1.0.3** for the whole life of those versions, and v1.0.4 tied with vaj-v1.0.4 to the
  second (Dart's sort is not stable, so either could win). A pre-release user was offered
  **nix-v1.1.0-pre.4** for about 24 seconds on 2026-10-13, and **v1.0.5** for the 14 minutes before
  that. v1.0.5 and v1.1.0-pre.4 are right today only because the Termux release happened to be
  published last both times.
- **What a wrong pick does:** the editions have different package names (`com.termux`,
  `com.termux.launcher.nix`, `io.vaj.tl`). An app Obtainium already tracks shows an "update" that
  then fails with *"Downloaded package ID does not match existing app ID"*. That is stuck but safe.
  **A first-time add takes its package name from the downloaded APK, so it installs the wrong
  edition outright.**
- **The pre-release flag is respected exactly.** Users who leave "Include prereleases" off never
  get a `-pre` tag, because every pre tag carries the flag (CI enforces it). Users who turn it on
  get drops and stables, ordered by publish date. A stable published *after* a drop with a lower
  version (1.0.6 after 1.1.0-pre.4) becomes their "latest". The global "Hide downgrades as updates"
  setting (on by default) is all that stops it being offered. With that setting off, the user is
  moved to the stable, the issue #71 shape. Publishing the drop after the stable, as we do now, is
  the right order.
- **APK choice inside a release is fine:** the default "filter APKs by architecture" picks
  `…_arm64-v8a.apk` over `universal`, and no debug APKs are published. The only gap is the minutes
  while APKs are still uploading.
- **`versionCode` 1020 is never compared** unless a user turns on "Use app versionCode as
  OS-detected version". Obtainium compares the release **tag** against the installed
  **versionName**. `v1.0.5` / `1.0.5` and `v1.1.0-pre.4` / `1.1.0-pre.4` reconcile. `nix-v…` and
  `vaj-v…` tags do not reconcile cleanly with a bare versionName. That causes one spurious
  "update" on first add, and on pre builds Obtainium quietly switches version detection off. There
  is no endless update loop.
- **Recommendation (proposal):** publish **six one-tap Obtainium import links** (§5.2) on the
  README/site, one per edition × channel. They preset the edition filter, the channel and a version
  trim, so nobody types a regex. Also make the publish step itself safe for people who paste the
  bare repo URL: **create every release as a draft, attach the APKs, then publish Nix, VAJ and
  Termux last** (§5.1). With that, a Termux stable or pre user who adds the plain URL also gets the
  right build with zero config. Nix and VAJ users still need the link or a filter, because one repo
  cannot make them zero-config. Splitting repos would be the only way, and it is not worth it
  (§6).

## 2. Our release layout, as found

- **One repo, three editions.** All releases live in `PickleHik3/termux-launcher`
  (`gh release list`; `docs/agents/releasing.md` "Editions and their branches"). The package names
  are `com.termux` / `com.termux.launcher.nix` / `io.vaj.tl` (`scripts/cut-prerelease.sh:72`).
  Tags are `vX.Y.Z`, `nix-vX.Y.Z` and `vaj-vX.Y.Z`. Drops are `…-pre.N`. Release titles equal the
  tags (`--title "$t"` in `cut-prerelease.sh:222`, and the live data).
- **Flags:** stables get no pre-release flag. Only `vX.Y.Z` gets `--latest`, and the edition
  stables get `--latest=false`. Every drop gets `--prerelease --latest=false`. CI refuses a
  mismatch (`.github/workflows/attach_debug_apks_to_release.yml:91-105`). History: `nix-v1.0.0` to
  `nix-v1.0.3` (and `v0.2.x-nix`) were flagged pre-release. Since `nix-v1.0.4` they are not.
- **Order and timing of a drop:** `cut-prerelease.sh` loops `EDITIONS=(nix vaj termux)` (line 67).
  For each edition it pushes the tag, runs `gh release create` (the release is public immediately),
  dispatches `attach_debug_apks_to_release.yml`, then retires the previous drop to a draft (lines
  218-226). APKs arrive 3-5 minutes later. The stable cut follows the same pattern by hand, with the
  plain tag created last (`releasing.md` "Cutting a stable release").
- **Asset names:** `termux-app_v<versionName>_<edition>_<variant>_<abi>.apk`, built by
  `app/build.gradle:183-184`. Termux releases carry arm64-v8a, armeabi-v7a, universal, x86 and
  x86_64. Nix carries arm64-v8a, universal and x86_64. VAJ carries only arm64-v8a. Each release
  also has a `_sha256sums` file. The API returns the assets in alphabetical order, the same in
  every release checked.
- **Versions:** `versionCode 1020` everywhere (`app/build.gradle:56`). `versionName` is the bare
  `X.Y.Z` or `X.Y.Z-pre.N` (line 58), so the edition prefix exists only in the tag.
- **Real publish times (API `published_at`, the field Obtainium sorts on):**

  | cut | nix | vaj | termux | published last |
  |---|---|---|---|---|
  | 1.0.0 | 18:34:25 (pre flag) | 18:34:23 | 18:34:26 | termux |
  | 1.0.1 | 22:37:38 (pre flag) | 22:37:41 | 22:37:52 | termux |
  | 1.0.2 | 13:12:38 (pre flag) | **13:12:39** | 13:12:36 | **vaj** |
  | 1.0.3 | 19:59:07 (pre flag) | **19:59:27** | 19:58:49 | **vaj** |
  | 1.0.4 | 16:23:51 | **16:23:52** | **16:23:52** | **tie** |
  | 1.0.5 | 21:31:21 | 21:31:29 | 21:31:36 | termux |
  | 1.1.0-pre.4 | 21:41:54 | 21:42:04 | 21:42:14 | termux |

## 3. Evidence from Obtainium's source

### 3.1 What it fetches

- The GitHub source requests `<api>/repos/<owner>/<repo>/releases?per_page=100`, falling back to
  `/tags` (GH:864). There is no pagination, so only the newest 100 releases are seen. We have 43.
- The `/releases/latest` endpoint is fetched **only** when "Verify the 'latest' tag"
  (`verifyLatestTag`) is on (GH:722, GH:731-745). That setting has no default value, so it is off
  (GH:102; switches default to `false` in `getDefaultValuesFromFormItems`,
  `lib/components/generated_form_model.dart:199-214`). When it is on, the "latest" release is
  moved to the top of the list regardless of sort (GH:542-559, GH:777).

### 3.2 How it sorts (Q1, Q2)

- **Sort method** dropdown: Release date / Name (smart) / None / Name (smart) × Release date / Name.
  The form default is `'date'` (GH:103-118). The in-code fallback `'smartname-datefallback'`
  (GH:726) is dead in practice: every app's settings are merged over the form defaults on load
  (`lib/providers/app_json_migration.dart:281-290`) and on add (SP:350).
- **`date`** compares `published_at` only (GH:435-464, GH:487-500), or the newest asset's
  `updated_at` if "Use latest asset upload as release date" is on (GH:119-125, GH:443-458). It sorts
  ascending and then reverses, so the newest comes first (GH:778). Equal timestamps compare as 0.
  Dart's `List.sort` "is not guaranteed to be stable" (api.dart.dev, `List.sort`), so a tie to the
  second has no defined winner.
- **`smartname`** picks the longest version pattern both tags share and compares only that
  substring (GH:503-535, VS:109-153). `nix-v1.0.5` and `v1.0.5` compare equal. So do
  `v1.1.0-pre.4` and `v1.1.0` (a 3-number match), and `-pre.4` and `-pre.5`: the `pre` patterns
  only allow a digit right after `pre` or a `+N` (VS:125-148), so the `.4` is never read. Smart
  sort therefore cannot order our drops or tell editions apart.
- **`name`** is a plain alphanumeric compare of the whole tag (GH:538; `lib/utils/string_compare.dart`).
  `vaj-v…` > `v…` > `nix-v…`, so VAJ would always win.
- **Conclusion:** the only usable sort is the default, Release date. It makes **publish order the
  whole contract**.

### 3.3 How it picks the release from the sorted list

`_selectGitHubTargetRelease` (GH:561-670) walks newest-first:

1. If "Fallback to older releases" is off, it stops after the first non-skipped candidate
   (GH:580). That switch defaults to **on** (AS:200-206).
2. Pre-releases are skipped unless "Include prereleases" is on (GH:581-584). That defaults to
   **off** (GH:70-76).
3. Drafts are always skipped (GH:585-588). The unauthenticated API does not list them anyway: 0
   drafts in our anonymous fetch versus 6 via `gh`.
4. "Filter release titles by regular expression" tests `name`, falling back to `tag_name`
   (GH:589-595). A mismatch `continue`s **without** counting as a skip, so with fallback off a
   title filter ends the search at the first mismatch (GH:580 + GH:593-595). **Title filters need
   fallback on.**
5. "Filter release notes" works the same way on `body` (GH:596-601).
6. "Minimum update age" skips too-young releases (GH:602-611).
7. Assets: only `.apk/.xapk/.apkm/.apks` count (AF:10-36). They then pass the "Filter APKs by
   regular expression" setting (GH:630-634, AF:60-73). **A release with no APK left is skipped**
   (GH:649-651). This happens both while APKs are still uploading and when an APK filter rules out
   the release.
8. The version string is the `tag_name`, or the title if "Use release title as version string" is
   on (GH:652-657).

### 3.4 How it picks the APK inside a release (Q3)

- After selection, the APK regex is applied again (SP:270-279). Then, if "Attempt to filter APKs by
  CPU architecture" is on, `filterApksByArch` narrows the list (SP:281-286). That switch defaults
  to **on** for new apps (AS:260-265). Apps migrated from very old exports get `false`
  (`app_json_migration.dart:309-311`).
- `filterApksByArch` walks the device's supported ABIs in order (`arm64-v8a` first on 64-bit ARM).
  It keeps the files whose name contains the ABI or an alias (`aarch64`, `arm64`), but only if that
  is a strict subset (AF:75-104). `…_arm64-v8a.apk` is kept and `…_universal.apk` is dropped. With
  a single APK there is nothing to filter (AF:87).
- If several APKs are left, the user is asked once (`lib/pages/add_app.dart:280-293`). The choice
  is stored as an **index** (`preferredApkIndex`) and reused on later updates
  (`lib/providers/apps_provider_updates.dart:40-44`). Our asset order is stable and alphabetical,
  so the index keeps meaning the same ABI.
- **Our names are unambiguous** for this: one ABI token per file, no `debug` assets on releases,
  `sha256sums` ignored (not `.apk`). The one gap is timing. Assets upload one by one, and for a few
  seconds a release can hold only `armeabi-v7a`. A lone APK is never arch-filtered, so it would be
  offered. The replay in §3.6 caught this for 1-2 s on v1.0.1, v1.0.4 and v1.0.5.

### 3.5 Version comparison, versionCode, package name (Q4)

- **Installed version** is the OS `versionName`. It is `versionCode` only if "Use app versionCode as
  OS-detected version" is on (`lib/providers/apps_provider.dart:717-723`), which defaults to off
  (AS:234-240). With that setting off, the pinned 1020 never enters any comparison. Leave it off: a
  constant `1020` could never be reconciled with a tag. One more place uses `versionCode`: install
  completion is detected by `lastUpdateTime`, and by `versionCode` only when the update time is
  unavailable (`lib/installers/install_utils.dart:33-44`). With a constant code, that fallback
  path could not see an update land. This is a minor edge case.
- **Same-release check** strips a leading `v` only when a digit follows, then compares tokens
  (VN:54, VN:98-122, VN:168-186). Results:
  - `1.0.5` vs `v1.0.5` matches. `1.1.0-pre.4` vs `v1.1.0-pre.4` matches. The Termux edition is
    clean.
  - `1.0.5` vs `nix-v1.0.5` does not match: the tokens `nix` and `v` remain. On the first add of an
    already-installed Nix/VAJ app, the tracked version is the OS `1.0.5`
    (`lib/providers/apps_provider_lifecycle.dart:150-153`). It never collapses to `nix-v1.0.5`, and
    `isAppUpdateable` (VS:388-399) sees different strings that compare numerically equal, so it
    shows an **"update" to the version already installed**. Installing it sets the tracked version
    to the tag, and the badge clears. That is one extra download, not a loop.
  - `1.1.0-pre.4` vs `nix-v1.1.0-pre.4`: no strict standard format and no token match
    (VS:246-274), so `versionDetectionPossible` returns false. Obtainium then **turns "Reconcile
    version string with version detected from OS" off** for that app
    (`apps_provider_lifecycle.dart:171-182`). After that it trusts its own record, and an APK
    side-loaded outside Obtainium goes unnoticed.
- **Downgrade hiding:** `isAppUpdateable` hides a "latest" that is numerically older than the
  installed version, but only while the global "Hide downgrades as updates" is on (VS:388-399). It
  defaults to true (`lib/providers/settings_provider.dart:534-536`). `v1.1.0-pre.4` vs `v1.0.5`:
  1.1.0 > 1.0.5, so the downgrade is hidden. `-pre.4` vs `-pre.5` compares *equal* numerically
  (§3.2), and `0 <= 0` counts as updateable, so a new drop is still shown. `1.1.0-pre.N` vs `1.1.0`
  compares equal, so the stable that retires the series is shown to pre users, which is intended.
- **Package-name guard:** at install, if the APK's package differs from the tracked app ID and the
  app is not new, Obtainium throws `IDChangedError` (*"Downloaded package ID does not match existing
  app ID"*, `lib/providers/apps_provider_install.dart:95-110`). **When a user adds an app through
  the Add App screen, the ID comes from the APK just downloaded** (`add_app.dart:310`). A wrong
  edition at add time therefore becomes the tracked app, and that edition is installed. For
  imports, `allowIdChange` defaults to false (`lib/models/app.dart:125, 257`), so an imported
  config with the right `id` rejects a wrong-edition APK.

### 3.6 Replay of our real history

`scratchpad/sim/simulate.py` (not in the repo) replays the anonymous `/releases?per_page=100`
response. At every timestamp where a release or asset appeared, it applies the default rules:
date sort, skip drafts and (optionally) pre-releases, skip releases with no APK yet, prefer arm64.
Transitions since 2026-10-09:

| period (UTC) | prereleases off | prereleases on |
|---|---|---|
| 10-09 18:34 | vaj-v1.0.0 (seconds) then v1.0.0 | vaj → nix-v1.0.0 → v1.0.0 |
| 10-09 22:43 | vaj-v1.0.1 (seconds) then v1.0.1 | nix → vaj → v1.0.1 |
| 10-10 13:16 to 10-10 20:04 | **vaj-v1.0.2** | **vaj-v1.0.2** |
| 10-10 20:04 to 10-11 16:28 | **vaj-v1.0.3** | **vaj-v1.0.3** |
| 10-11 16:28 | vaj-v1.0.4, then v1.0.4 *only by tie order* | same |
| 10-13 21:36 | v1.0.5 | v1.0.5 |
| 10-13 21:42-21:45:25 | v1.0.5 | **v1.0.5** (new drop has no APKs yet) |
| 10-13 21:45:25-21:45:49 | v1.0.5 | **nix-v1.1.0-pre.4** |
| 10-13 21:45:49 on | v1.0.5 | v1.1.0-pre.4 |

The replay uses a stable sort, which Dart does not guarantee, so the v1.0.4 row could have gone
either way on a device.

## 4. Answers

**Q1, prereleases off: "latest" or its own sort?** Its own sort, by publish date. GitHub's Latest
is used only with "Verify the 'latest' tag" (GH:722-745, GH:777). It **can pick a Nix or VAJ
release**: any non-pre-release published after the Termux one. It did so for VAJ during 1.0.2 and
1.0.3. It **cannot pick a `-pre` tag**: the flag is honoured (GH:581-584) and CI enforces the flag
on every pre tag.

**Q2, prereleases on: order of v1.0.5 vs v1.1.0-pre.4.** By `published_at` only, not semver or tag
string (GH:487-500, GH:778). A stable published after a drop becomes the pre user's "latest". If
its version is lower, "Hide downgrades as updates" (default on) hides it. With that setting off,
the user is offered and installs the stable over the drop: same key and `versionCode`, so Android
allows it. That is the issue #71 shape. Issue #71 itself gives no version string, so this note
cannot confirm that was its cause. The same thing happens during a drop's first ~4 minutes, before
its APKs exist. Order that avoids it: **stable first, drop after** (what we do now), with each
release's APKs attached before it is published.

**Q3, which APK.** Default arch filtering picks `…_arm64-v8a.apk` and drops `universal`
(AF:83-104). Our names are unambiguous, with no debug assets. Risk exists only mid-upload, when a
lone `armeabi-v7a` APK can be offered (§3.4).

**Q4, versionCode 1020.** Not compared unless the user opts into "Use app versionCode as
OS-detected version", which they should not. Obtainium compares the tag (version string) with the
OS versionName. No update loop. Nix/VAJ tags cause one spurious same-version "update" on first add.
On pre builds of those editions, version detection is switched off (§3.5).

**Q5, settings per channel.** See §5.3. The minimum by hand is one edition filter, plus the
pre-release switch for drops. It can be zero-config only for the Termux edition, and only after
§5.1.

**Q6, the six combinations today.** The source URL for all six is
`https://github.com/PickleHik3/termux-launcher`. Keep the defaults (sort = Release date, fallback
on, arch filter on, "Verify the 'latest' tag" off).

| edition × channel | Include prereleases | Filter release titles by regex | (alternative) Filter APKs by regex | Zero-config today? |
|---|---|---|---|---|
| Termux stable | off | `^v[0-9]` | `_termux_` | **No**: picked VAJ through 1.0.2-1.0.3 |
| Termux pre-release | on | `^v[0-9]` | `_termux_` | **No**: nix-pre / stable windows at each drop |
| Nix stable | off | `^nix-v[0-9]` | `_nix_` | **No**: gets Termux whenever Termux is published last, which is the normal case |
| Nix pre-release | on | `^nix-v[0-9]` | `_nix_` | No |
| VAJ stable | off | `^vaj-v[0-9]` | `_vaj_` | No |
| VAJ pre-release | on | `^vaj-v[0-9]` | `_vaj_` | No |

For Nix and VAJ, also set "Trim version string with RegEx" to `[0-9].*`. That turns
`nix-v1.0.5` into `1.0.5` before comparison (SP:252-260, VS:50-71), which removes the spurious
first update and keeps version detection on for pre builds. Either filter alone is enough. The
APK filter is the sturdier one if release titles ever stop matching tags, because APK names are
fixed by `app/build.gradle:184` and CI. The title filter is the more obvious one to a user.

**Q8, publish order of a stable and the drop after it.** It matters only to pre users, and only
through `published_at`. Drop after stable (current practice) is right. The reverse makes the stable
the pre user's latest, hidden only by the global downgrade setting. Within each cut, the **Termux
edition must be published last and at least one second after the others**. Each release should be
published only once its APKs are attached: Obtainium skips an APK-less release and falls through to
whichever edition is next (GH:649-651). For a stable that retires a series (1.1.0 after
1.1.0-pre.N), publishing the stable first is also right: pre users see it as an update (equal
numeric core, VS:397-398) and move to it until `1.2.0-pre.1` arrives.

## 5. Proposed fixes

### 5.1 Proposal: publish drafts, attach APKs, then publish in order (Termux last)

Fixes: wrong edition for default-settings Termux users, the mid-upload windows, the lone-armeabi
window, and the tie.

- `cut-prerelease.sh` and the stable cut create each release with `--draft`. The release then
  dispatches `attach_debug_apks_to_release.yml` against the draft, waits for all three runs to
  finish (`gh run watch`), and only then runs `gh release edit <tag> --draft=false` for nix, vaj
  and termux, in that order, with a short sleep between, then retires the previous drop.
- The Discord announcement fires on publish (`releasing.md`). It still fires once, when the plain
  tag is published last.
- **To verify on a throwaway tag before adopting** (this note did not test it): that the workflow's
  `gh release view/upload <tag>` steps find a *draft* by tag; that `published_at` is set when the
  draft is published, not when it was created; and that `--latest` can be applied in the same
  `gh release edit`. The retired pre.3 drafts still carry their original `published_at`, so a
  re-drafted release keeps its old date.
- After this, the Termux stable and Termux pre combinations are zero-config with the plain URL.
  Nix and VAJ are not.
- Smaller fallback if the draft route fails: keep today's order, but create the Termux release only
  after the Nix and VAJ APK runs have finished. This shortens the windows but does not close the
  gap before the Termux APKs land.

### 5.2 Proposal: six one-tap import links on the README/site (recommended)

Obtainium accepts `obtainium://app/<url-encoded app JSON>`. That JSON can carry `additionalSettings`
(a JSON *string*), exactly as Obtainium's own share action builds it
(`lib/pages/apps.dart:777`; handler `lib/pages/home.dart:191-267`; wiki "Deep Links"). Settings left
out take their defaults (`app_json_migration.dart:281-290`). The redirect wrapper
`https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/…` falls back to a "get Obtainium"
page when the app is missing (wiki "Deep Links"). The import also fixes the package `id` up front,
so a wrong-edition APK is refused instead of adopted (§3.5).

Configs (the `name` strings are placeholders):

```json
{"id":"com.termux","url":"https://github.com/PickleHik3/termux-launcher","author":"PickleHik3","name":"Termux Launcher","additionalSettings":"{\"includePrereleases\":false,\"fallbackToOlderReleases\":true,\"filterReleaseTitlesByRegEx\":\"^v[0-9]\",\"verifyLatestTag\":false,\"apkFilterRegEx\":\"_termux_\",\"invertAPKFilter\":false,\"autoApkFilterByArch\":true,\"versionExtractionRegEx\":\"[0-9].*\"}"}
{"id":"com.termux","url":"https://github.com/PickleHik3/termux-launcher","author":"PickleHik3","name":"Termux Launcher pre-release","additionalSettings":"{\"includePrereleases\":true,\"fallbackToOlderReleases\":true,\"filterReleaseTitlesByRegEx\":\"^v[0-9]\",\"verifyLatestTag\":false,\"apkFilterRegEx\":\"_termux_\",\"invertAPKFilter\":false,\"autoApkFilterByArch\":true,\"versionExtractionRegEx\":\"[0-9].*\"}"}
{"id":"com.termux.launcher.nix","url":"https://github.com/PickleHik3/termux-launcher","author":"PickleHik3","name":"Termux Launcher (Nix)","additionalSettings":"{\"includePrereleases\":false,\"fallbackToOlderReleases\":true,\"filterReleaseTitlesByRegEx\":\"^nix-v[0-9]\",\"verifyLatestTag\":false,\"apkFilterRegEx\":\"_nix_\",\"invertAPKFilter\":false,\"autoApkFilterByArch\":true,\"versionExtractionRegEx\":\"[0-9].*\"}"}
{"id":"com.termux.launcher.nix","url":"https://github.com/PickleHik3/termux-launcher","author":"PickleHik3","name":"Termux Launcher (Nix) pre-release","additionalSettings":"{\"includePrereleases\":true,\"fallbackToOlderReleases\":true,\"filterReleaseTitlesByRegEx\":\"^nix-v[0-9]\",\"verifyLatestTag\":false,\"apkFilterRegEx\":\"_nix_\",\"invertAPKFilter\":false,\"autoApkFilterByArch\":true,\"versionExtractionRegEx\":\"[0-9].*\"}"}
{"id":"io.vaj.tl","url":"https://github.com/PickleHik3/termux-launcher","author":"PickleHik3","name":"Termux Launcher (VAJ)","additionalSettings":"{\"includePrereleases\":false,\"fallbackToOlderReleases\":true,\"filterReleaseTitlesByRegEx\":\"^vaj-v[0-9]\",\"verifyLatestTag\":false,\"apkFilterRegEx\":\"_vaj_\",\"invertAPKFilter\":false,\"autoApkFilterByArch\":true,\"versionExtractionRegEx\":\"[0-9].*\"}"}
{"id":"io.vaj.tl","url":"https://github.com/PickleHik3/termux-launcher","author":"PickleHik3","name":"Termux Launcher (VAJ) pre-release","additionalSettings":"{\"includePrereleases\":true,\"fallbackToOlderReleases\":true,\"filterReleaseTitlesByRegEx\":\"^vaj-v[0-9]\",\"verifyLatestTag\":false,\"apkFilterRegEx\":\"_vaj_\",\"invertAPKFilter\":false,\"autoApkFilterByArch\":true,\"versionExtractionRegEx\":\"[0-9].*\"}"}
```

Example encoded link (Termux stable):
`https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.termux%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Ev%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_termux_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D`.
Encode the other five the same way: `encodeURIComponent` of the JSON line.

Caveats:

- One `id` per edition means a user tracks one channel per edition. Switching channel is the
  "Include prereleases" toggle in the app's settings, which the README says.
- Opening a second link for the same `id` does **not** refuse: the import replaces the tracked
  entry and its settings (seen on the emulator, below). The `add_app.dart` "App already added"
  path is the manual Add App screen, not the deep-link import.

**Tested 2026-10-14** on the agents' emulator (AVD tl16, Android 16, x86_64 with arm64
translation) with Obtainium 1.6.17. All six raw links and the https redirect wrapper imported with
every setting intact and resolved as intended: v1.0.5 / v1.1.0-pre.4 for Termux, nix-v1.0.5 /
nix-v1.1.0-pre.4, vaj-v1.0.5 / vaj-v1.1.0-pre.4, each with its own edition's APK (the x86_64 build
on the emulator, arm64-v8a for VAJ). No Termux Launcher APK was installed. The links are in the
README's "Updates with Obtainium" section and on the site.

### 5.3 Proposal: manual instructions for people who paste the URL

One line per combination, as in the Q6 table, in the README's install section. The minimum is the
edition's "Filter APKs by regular expression" (`_termux_` / `_nix_` / `_vaj_`), plus "Include
prereleases" for drops. Also say: leave "Fallback to older releases" on, leave "Verify the 'latest'
tag" off (it would pin a pre user to the stable, GH:542-559), and leave "Hide downgrades as updates"
on.

### 5.4 Not needed: naming changes

Tag, title and asset names are already distinct enough for one regex per edition. Under the default
date sort, no renaming changes which release Obtainium picks. Renaming `-pre.N` to `-preN` would
let smart sort order drops (§3.2), but nobody should use smart sort here. If release titles are
ever made friendlier ("Termux Launcher 1.0.5 (Nix)"), the published title regexes must change with
them. The APK regexes would not.

## 6. Options compared (Q7)

| option | effort | what it fixes | robustness | verdict |
|---|---|---|---|---|
| (a) naming only | none to small | nothing by itself: the default sort is by date, not name (GH:103-118, 487-500) | n/a | not a fix; names already good |
| (b) one-tap import links (§5.2) + draft-then-publish order (§5.1) | small: six links on README/site; one script change plus a stable-cut step | all six combinations, one tap each; the plain URL is correct for Termux users | high: the filter and package `id` are pinned per user; the publish order covers people who skip the link | **recommended** |
| (c) Obtainium community configs (`ImranR98/apps.obtainium.imranr.dev`) | a PR, and a PR again for every change | discoverability inside Obtainium's catalogue | as (b), but config changes need upstream review | **Termux edition ineligible**: "Forks of apps will not be accepted, unless both the package name and display name … has been changed" (`APP_CRITERIA.md`), and ours is `com.termux`; repo is a fork. Nix/VAJ may qualify (313 stars ≥ 35; created 2026-01-30, so over 4 months). The guide also discourages configs with prereleases on "unless necessary". Optional later, Nix/VAJ stable only |
| (d) separate repos per edition | large: releases, issues, CI, notes, site, tlstore, existing users' URLs | Nix/VAJ zero-config | high | cost far beyond the problem; (b) gets the same result |

**Recommendation:** (b). Ship §5.1 so that the bare URL is right for the Termux edition, and put the
six links from §5.2, plus the one-line manual table from §5.3, in the README and on the site.

## 7. Concrete problems found

1. **Wrong edition offered to default-settings users whenever an edition release is published
   after the Termux one** (VAJ during 1.0.2-1.0.3; a tie on 1.0.4). Fix: §5.1 (Termux last, drafts
   first) plus §5.2/§5.3 filters.
2. **First-time add can install the wrong edition** (`add_app.dart:310`). Fix: §5.2 links pin the
   `id`; §5.1 for bare-URL adds.
3. **APK-less window after each `gh release create`** sends Obtainium to an older or other-edition
   release (GH:649-651): v1.0.5 and then nix-v1.1.0-pre.4 for pre users on 10-13. Fix: §5.1.
4. **Lone-armeabi window mid-upload** (AF:87). Fix: §5.1.
5. **Pre users depend on the global "Hide downgrades as updates"** when a lower stable is published
   after a drop (VS:394-398). Fix: keep publishing the drop after the stable (current order);
   mention the setting in §5.3.
6. **Nix/VAJ tag prefix does not reconcile with versionName**: one spurious update on first add;
   version detection switched off on pre builds (§3.5). Fix: `versionExtractionRegEx` `[0-9].*` in
   the §5.2 links and §5.3 instructions. The release-side alternative would be bare tags, which we
   cannot have in a shared repo.
7. **"Verify the 'latest' tag" would freeze a pre user on the stable** (GH:542-559, GH:777). Fix:
   say "leave it off" in §5.3; the links set it false.

## 8. Sources

- Obtainium source at `af286fa8d31d7406d6db167e2314d376d74f7696`: `lib/app_sources/github.dart`,
  `lib/app_sources/app_source.dart`, `lib/services/version_service.dart`,
  `lib/utils/version_normalization.dart`, `lib/utils/string_compare.dart`,
  `lib/services/apk_filter_service.dart`, `lib/providers/source_provider.dart`,
  `lib/providers/app_json_migration.dart`, `lib/providers/apps_provider.dart`,
  `lib/providers/apps_provider_lifecycle.dart`, `lib/providers/apps_provider_install.dart`,
  `lib/providers/apps_provider_updates.dart`, `lib/providers/settings_provider.dart`,
  `lib/installers/install_utils.dart`, `lib/models/app.dart`, `lib/pages/add_app.dart`,
  `lib/pages/home.dart`, `lib/pages/apps.dart`, `lib/components/generated_form_model.dart`,
  `assets/translations/en.json` (UI labels).
- Obtainium wiki, Deep Links: https://wiki.obtainium.imranr.dev/deep_links/
- `ImranR98/apps.obtainium.imranr.dev`: `APP_CRITERIA.md`, `CONTRIBUTING.md` (read via the GitHub
  API, 2026-10-14).
- Dart `List.sort`: https://api.dart.dev/dart-core/List/sort.html ("The sort function is not
  guaranteed to be stable.")
- Our side: `gh release list`, `gh api repos/PickleHik3/termux-launcher/releases?per_page=100`
  (authenticated and anonymous), `docs/agents/releasing.md`, `scripts/cut-prerelease.sh`,
  `.github/workflows/attach_debug_apks_to_release.yml`, `app/build.gradle`, issue #71.
- Not verified: Obtainium's behaviour on a device. Everything above is from reading its source; no
  link or setting was tried in the app. GitHub's `published_at` behaviour for a release published
  from a draft, and `gh release upload` against a draft, are also unverified.
