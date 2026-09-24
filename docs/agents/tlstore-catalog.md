# Keeping the tlstore catalog current

How an agent adds, updates or removes an item in tlstore, and what has to happen before the
change can reach a phone. The user-facing side is `docs/en/Tlstore.md`; the design of the store's
screens is `project-docs/tlstore/REVISION-6.md` (Revision 5 still describes the engine, the TSV
contract and the progress stream).

## The pieces

| Piece | Where | Owner |
|---|---|---|
| Item list (hand-maintained) | `scripts/tlstore/items.tsv` | you edit this |
| Catalog (generated, signed) | `app/src/main/assets/tlstore/catalog.tsv` + `.minisig` | `build-catalog.sh`, `sign.sh` |
| Engine (POSIX sh) | `app/src/main/assets/tlstore/tlstore` + `.minisig` | edit, `test.sh`, `sign.sh` |
| Store UI (Rust) | `tools/tlstore-ui/`; bundled binaries `app/src/main/assets/tlstore/tlstore-ui-<abi>` | `build-ui.sh --install` after any `src/` change |
| Pictures | `scripts/tlstore/pictures/<name>.jpg` + `SOURCES.md` | you, with the conversion in `SOURCES.md` |
| Binaries the catalog installs | GitHub `PickleHik3/tlstore` (renamed from termux-launcher-binaries 2026-09-24; the local checkout keeps the old directory name), per tag, `SHA256SUMS` | its `recipes/cross` and `recipes/termux` build them |
| Launcher-side install | `app/src/main/java/com/termux/app/store/TlstoreInstaller.java` | writes script, catalog, key and UI binary into `$PREFIX` on every start |

The phone reads `catalog.tsv` by fixed column position and requires at least ten columns. New
columns land only at the end, and a hidden part carries `-` in every descriptive column and `0`
in `setup`/`featured`.

## Adding or changing an item

1. **Pin the payload.** A `binary`/`file` source is `launcher:<path>@<tag or commit>`,
   `binaries:<asset>@<tag>` or an immutable URL; `npm-musl` is `npm:<package>#<exe>`; `pkg` names
   Termux packages. Never a branch. For a new binary, build it with the recipe, publish it under a
   tag in the binaries repository, and take its digest from that tag's `SHA256SUMS`.
2. **Edit `items.tsv`.** The header comment defines every column and its rule (length limits,
   allowed values). Revision 6 reads: `category`, `upstream`, `setup`, `standfirst`, `author`,
   `licence`, `size`, `picture`, `featured`, `readme-skip`, `readme` (the pinned-content addendum).
   `does1..3`, `try` and `notes` are no longer shown; fill them with `-` for a new item.
   - `standfirst`: one line, at most 42 characters, lower case, no brand name, no full stop. It is
     the only sentence we write about the item.
   - `readme-skip`: `|`-separated headings of the upstream README to leave out (developer-facing
     sections). Installation, Building, Contributing, Licence, Changelog, Sponsors and similar are
     dropped by default already.
   - `picture`: a `launcher:` source pointing at `scripts/tlstore/pictures/<name>.jpg`. Convert the
     upstream hero image as `pictures/SOURCES.md` says and add a row there with the source and
     its licence.
   - `readme`: a `launcher:`/`binaries:` source for a pinned copy of the item's README, read
     instead of the upstream one; `-` to keep fetching upstream (most items). Pin one when the
     upstream README does not render well as-is (heavy badges, a build matrix table, prose that
     assumes a desktop) — write a trimmed copy instead of relying on `readme-skip` alone.
   - `demo`: unchanged (a `launcher:`/`binaries:` source for a short clip of `try` working), but
     now digest-checked like `picture` — `build-catalog.sh` computes its `demo-digest` the same
     way it already computes `picture-digest`.
3. **Build the catalog.**
   ```sh
   scripts/tlstore/build-catalog.sh /path/to/tlstore/SHA256SUMS
   ```
   It computes digests (network for plain URLs), bumps the serial (`YYYYMMDDNN`, only forward) and
   writes `catalog.tsv`. A source whose digest cannot be computed stops the build.
4. **Test the engine.** `bash scripts/tlstore/test.sh` must end `failed 0`. It runs the script
   under `/bin/sh` and `bash --posix`; dash, busybox and shellcheck run too when installed.
5. **Sign.** `bash scripts/tlstore/sign.sh` signs the catalog and the script. The key is
   `~/.config/vaj-apt/tlstore-minisign.key`, encrypted, outside the tree: the developer runs this
   step (`! bash scripts/tlstore/sign.sh` in a Claude session). Phones verify the signature only
   when they refresh from origin, so sign before every push, not before a local test install.
6. **Check the UI against it.** In `tools/tlstore-ui`: `cargo test`, and for a look at the result
   without a phone, the preview renderer:
   ```sh
   cargo run --features shot -- --shot 53x26 --screen item:<name> --out /tmp/item.png
   cargo run --features shot -- --shot-all --out /tmp/frames/
   ```
   It renders from `tests/fixtures/store`, so a new item needs a row in the fixture `list.tsv`,
   an `info/<name>` file and a `readme/<name>.md` to appear there.
7. **Commit** `items.tsv`, `catalog.tsv`, both `.minisig` files and any picture in one commit.

## Pinning a readme or a hero picture

Most items just fetch the upstream README (`readme` stays `-`). Pin one when the upstream page
does not render well as-is — heavy badges, a build matrix, prose written for a browser — and a
`readme-skip` heading list alone is not enough.

1. **Write the trimmed readme.** A plain markdown file, following the item page's rendering rules
   (`project-docs/tlstore/REVISION-6.md`, "Item").
2. **Make the hero, if the item wants an animated one.** From a short screen recording or gif of
   the item running:
   ```sh
   scripts/tlstore/make-hero.sh clip.mp4 hero.png
   ```
   4 seconds, 12 fps, 600 px wide, looping APNG, full frames (needs `ffmpeg`).
3. **Commit both into the binaries repository** (`PickleHik3/tlstore`, née
   termux-launcher-binaries — see the pieces table above), under `readme/<name>.md` and
   `hero/<name>.png`, add their lines to that tag's `SHA256SUMS`, and push a tag the way any other
   binary asset does.
4. **Point `items.tsv` at them.** `readme` (and, for a hero picture pinned the same way, `picture`
   or `demo`) takes `binaries:<path>@<tag>` — a path with a slash resolves to that exact file in
   the tag, not the per-processor `bin/<asset>-aarch64` a bare asset name resolves to. Leave
   `readme-digest`/`demo-digest` alone: `build-catalog.sh` computes them from the same
   `SHA256SUMS`, by that repo-relative path, the way it already computes `picture-digest`.
5. **Build, test, sign** as above. A pinned readme that fails its digest check on a phone is a
   hard error (the `tlstore picture` convention: no silent fall back to the upstream copy).

## Changing the engine or the UI

- Script changes: keep it POSIX (`dash`-clean), add a `test.sh` case, re-sign. `TLSTORE_VERSION`
  in the script is what phones compare when they self-update from origin.
- UI changes: any file under `tools/tlstore-ui/src`, `Cargo.toml` or `Cargo.lock` changes the
  source hash baked into the binaries. Run
  `ANDROID_HOME=~/Android/Sdk bash scripts/tlstore/build-ui.sh --install` and commit the two
  rebuilt `tlstore-ui-<abi>` assets, or `./gradlew :app:testDebugUnitTest` fails in
  `checkTlstoreUiFresh`. Never enable the `shot` feature in the shipped build.
- The installer rewrites everything on the phone when the launcher's package update time
  changes, and rewrites the UI binary whenever the bundled bytes differ; a same-version debug
  reinstall therefore still picks the new binary up on the next launcher start.

## Removing an item

Delete its row (and its parts, if nothing else needs them), rebuild, test, sign. Phones that have
it installed keep it; `tlstore remove <name>` still works from the old catalog they cached.

## Release cut

The catalog ships in the APK, so every edition's release carries it. Nothing else is needed per
edition: the catalog is edition-neutral except for `prefixes`, where a row can name one app
package when a binary is built per prefix (see `recipes/cross/README.md` in the tlstore repository).
