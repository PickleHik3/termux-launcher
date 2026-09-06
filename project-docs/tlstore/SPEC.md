# tlstore — the launcher's tool store

`tlstore` (aliases `tl`, `tls`) is a small package-manager-style CLI shipped inside the launcher
APK. It installs, lists, updates and removes the tools and configs the launcher shows off but does
not ship: the showcase binaries from `PickleHik3/termux-launcher-binaries`, the opinionated shell
configs from `docs/en/examples`, apt/pacman packages, and Claude Code. It replaces the interactive
`setup-launcher` script (which stays, but points here).

Written 2026-09-06. Decisions here are settled; raise, do not silently change.

## Shape

- One POSIX `sh` program, `app/src/main/assets/tlstore/tlstore`, no bash-isms (Termux `sh` is dash).
  Runtime needs only the bootstrap: `curl`, `tar`, `sha256sum`, `sha512sum`, `base64`, `od`, `sed`,
  `awk`. Optional: `fzf` (picker), `patchelf` (npm-musl), `minisign` (catalog refresh).
- The app writes it on every start (`TlstoreInstaller`, modelled on `app/x11/X11CliInstaller`):
  `$PREFIX/bin/tlstore`, symlinks `$PREFIX/bin/tl` and `$PREFIX/bin/tls`,
  `$PREFIX/libexec/termux-launcher/tlstore/{catalog.tsv,trusted.pub,.installed}`. Marker comment
  `# written by termux-launcher` in the script's first lines; a foreign `tlstore`/`tl`/`tls` is left
  alone and reported, never overwritten.
- User state: `~/.local/share/tlstore/installed.tsv` (what tlstore installed, with files),
  `~/.local/share/tlstore/catalog.tsv` (verified refreshed catalog, optional),
  `~/.cache/tlstore/` (downloads). Payloads go to `~/.local/bin/<name>` and `~/.local/lib/<name>/`,
  never `$PREFIX/bin` (a bootstrap reinstall wipes it; apt owns names there). Replaced user files
  get a timestamped `.bak` beside them, as setup-launcher does today.
- Edition/prefix is read from `$PREFIX` (`/data/data/<app package>/files/usr`); env override
  `TLSTORE_PREFIX` for tests on a Linux host.

## Catalog

Tab-separated, UTF-8, `#` comment lines, one header comment carrying the serial:

```
# tlstore catalog	serial=2026090601
# name	kind	version	prefixes	source	digest	target	requires	options	summary
```

Columns:

| column | meaning |
|---|---|
| `name` | `[a-z0-9][a-z0-9-]*`, unique per prefix set |
| `kind` | `pkg` \| `binary` \| `file` \| `file-once` \| `script` \| `npm-musl` \| `bundle` |
| `version` | `-` for pkg/bundle; upstream version for binary/file/script; `latest` or pinned for npm-musl |
| `prefixes` | `*` or comma list of app packages (`com.termux`, `io.vaj.tl`, `com.termux.launcher.nix`) |
| `source` | `pkg`: space-separated package names. `binary`/`file`/`file-once`/`script`: a URL, or `binaries:<asset>@<tag>` (→ `https://raw.githubusercontent.com/PickleHik3/termux-launcher-binaries/<tag>/bin/<asset>`) or `launcher:<path>@<tag>` (→ `https://raw.githubusercontent.com/PickleHik3/termux-launcher/<tag>/<path>`). `npm-musl`: `npm:<package>#<executable inside package/>`. `bundle`: `-` |
| `digest` | sha256 hex of the downloaded file; `-` for pkg, bundle, npm-musl (npm's registry sha512 is the check) |
| `target` | install path with `~`; `-` = default (`~/.local/bin/<name>` for binary, `~/.local/lib/<name>` for npm-musl, none for others) |
| `requires` | comma list of catalog names installed first; bundle members live here |
| `options` | `;`-separated `key=value`: `env=K=V` (wrapper exports, repeatable with `,`), `tz=1` (wrapper exports TZ from `persist.sys.timezone`), `mode=755`, `post=<catalog script name>` |
| `summary` | one plain sentence, product copy |

Rules: one row per (name, prefix set) — per-edition builds are separate rows with their own
digest (fastfetch, musl-loader). tlstore uses the first row whose `prefixes` matches. Items whose
kind needs aarch64 (`binary`, `npm-musl`) are hidden on other CPUs.

Generated, never hand-edited: `scripts/tlstore/build-catalog.sh` reads `docs/en/examples/*`
(digests), the checked-out `termux-launcher-binaries` `SHA256SUMS` (path argument), the item
definitions in `scripts/tlstore/items.tsv` (hand-maintained: everything but digests), and writes
`app/src/main/assets/tlstore/catalog.tsv` with `serial=YYYYMMDDNN`.

Initial items: `fish`, `oh-my-posh`, `zoxide`, `eza`, `neovim`, `build-tools` (pkg); `config-fish`
(file), `personal-fish` (file-once), `omp-theme` (file), `setup-nvim` (script); `sigye`, `kitten`,
`fastfetch` ×2 rows, `musl-loader` ×2 rows (binary); `claude-code` (npm-musl, requires
`musl-loader,patchelf`; options `env=DISABLE_AUTOUPDATER=1;tz=1`); `patchelf` (pkg); bundles
`shell-setup` (fish, oh-my-posh, zoxide, eza, config-fish, omp-theme, personal-fish), `dev-tools`
(build-tools, neovim, setup-nvim), `showcase` (sigye, fastfetch, kitten).

### Trust

- Baseline catalog = the one in the APK. Payload digests come from it; `pkg` goes through apt/pacman;
  `npm-musl` verifies the registry's `dist.integrity` sha512 over TLS.
- Refresh: `https://raw.githubusercontent.com/PickleHik3/termux-launcher/main/app/src/main/assets/tlstore/catalog.tsv`
  and `catalog.tsv.minisig`, verified with `minisign -V -p $PREFIX/libexec/termux-launcher/tlstore/trusted.pub`.
  Accepted only when its serial is newer than the active one. No `minisign` → offer `pkg install
  minisign`; still none → refresh is off and the baseline is used, said in one line.
  `TLSTORE_CATALOG_URL` overrides the URL (tests use `file://`).
- The signing key lives with the maintainer (`~/.config/vaj-apt/tlstore-minisign.key`), never in
  a repo or an agent worktree. `scripts/tlstore/sign-catalog.sh` signs; the orchestrator runs it.

## Commands

```
tlstore                       help
tlstore list [-i|-a]          everything (installed marked), or installed only / available only
tlstore search <term>         name and summary match
tlstore info <name>           version, kind, source, digest, requires, files, summary
tlstore install [name...] [-y]   no names → multi-select picker (fzf --multi, else numbered toggles)
tlstore remove <name...> [-y]
tlstore update [name...] [--check] [--offline]   refresh catalog, then upgrade what is newer
tlstore refresh               catalog only
tlstore shell                 = install shell-setup
tlstore doctor                prefix, PATH order, tools present, catalog serial, drift, loader check
tlstore version
```

Exit codes 0/1/2 (ok / failed / usage). `TLSTORE_ASSUME_YES=1` = `-y` everywhere. Output is plain
text, one line per action, product copy (no mechanism talk); errors start with `tlstore: `.
`update` for `pkg` items runs the package manager's own upgrade for exactly those names
(`apt install --only-upgrade` / `pacman -S --needed`).

## Build plan

| phase | branch | deliverable | model | depends on |
|---|---|---|---|---|
| P1 | `feat/tlstore-cli` | `app/src/main/assets/tlstore/tlstore`; `scripts/tlstore/{items.tsv,build-catalog.sh,test.sh}`; generated `catalog.tsv`; tests green under `sh`/`dash`/`busybox sh` on a Linux host with `file://` fixtures | opus | — |
| P2 | `feat/tlstore-installer` | `app/src/main/java/com/termux/app/store/TlstoreInstaller.java`, hook in `TermuxActivity` beside the X11 installer call, `TlstoreInstallerTest`; assets read by name only | sonnet | — |
| P3 | `feat/tlstore-docs` | `docs/en/Tlstore.md`, README section replacing the `setup-launcher` curl instructions, `setup-launcher` header pointing to tlstore, release-notes line | sonnet | P1 |
| P4 | orchestrator | minisign key, `trusted.pub` asset, `sign-catalog.sh`, signed catalog on `dev`; queue `minisign` for the VAJ apt repo | — | P1 |
| gate | — | debug APK on the emulator: tlstore written, `tlstore list/doctor` run; then pong by the developer | — | P1, P2 |

## As built (2026-09-06)

Deviations from the text above, all deliberate: an `args=` option passes arguments to a `script`
item (`setup-nvim`); a `fastfetch-libs` pkg item carries fastfetch's runtime libraries and both
fastfetch rows require it; the `npm-musl` wrapper is named after the executable in the source
(`claude`), its directory after the item; `musl-loader` targets `~/.local/lib/musl/` and the
npm-musl install copies it from there; `claude-code` is limited to the editions with a loader;
`update --check` does not guess about `latest`-pinned items; a refused refresh exits 1;
`remove` on a pkg item only stops tracking it; a bundle member with no row on this device is
skipped with a line; the picker lists only what is not installed; the CLI reads two test knobs,
`TLSTORE_ARCH` and `TLSTORE_PATCHELF`. The app installer stamps the marker with the app's
versionName as well, so every release rewrites the files.

Verified 2026-09-06 on the x86_64 emulator (debug build): the app writes `tlstore`, `tl`, `tls`,
the catalog and key; list, info, doctor, a real apt install, update, a failed refresh, the picker
and remove behave. Not yet run on a device: binary, npm-musl and refresh against the published
catalog (the binaries tag `2026.09.06` and the catalog on `main` are not pushed yet).

Side queue: publish `minisign` in the VAJ apt repo (needs the build VM).
