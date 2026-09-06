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

## Revision 2 (2026-09-06)

The developer tried the store and asked for a much smaller catalog and stricter handling of config
files. What follows supersedes the item list and the `script` kind above.

### Seven visible items

`list`, `search` and the picker show exactly `claude-code`, `fastfetch`, `fish-shell`, `kitten`,
`nvim-theme`, `omp-theme`, `sigye`, alphabetically. Everything else carries `hidden=1` in `options`:
it is a part another item pulls in through `requires`, never listed, never searched, never offered
in the picker, and `install <hidden name>` is refused with "that is part of another item". `info` on
a hidden name still works, because names appear in `Needs` lines.

Gone: `neovim`, `build-tools`, `dev-tools`, `shell-setup`, `showcase`, `setup-nvim`, and the
standalone `patchelf` pkg item. `config-fish` and `personal-fish` became hidden parts of
`fish-shell`. `tlstore shell` now means `install fish-shell`.

`list` drops the `installed`/`-` column for a leading `*`, and prints
`needs while installing: <tools>` indented under an item that declares `build=`.

### New kinds and options

| addition | meaning |
|---|---|
| `hidden=1` | a part, not a choice (above) |
| `build=<pkgs>` | packages needed only while installing |
| kind `fisher` | `source` is a space-separated plugin list; install runs `fish -c 'fisher install …'`, remove the matching `fisher remove`, version `-`, and `update` leaves it alone — fisher updates are the user's (`fisher update`) |

Removed with them: kind `script` and its `args=` and `post=` options, which only `setup-nvim` used.
The dependency sort now reasons about `requires` alone.

### Config files are never replaced silently

For a `file` item (not `file-once`, not a binary) whose destination exists and differs, tlstore
downloads the new file to the cache, prints the item name, shows `diff -u` (a before/after of the
first 25 lines each where `diff` is missing) and asks `Replace your <basename>? [y/N]`. **`-y` and
`TLSTORE_ASSUME_YES` do not answer this question** — only the new `--configs` flag on `install` and
`update` does. With no tty and no `--configs` the file is kept and one line says so.

Either answer records the item at the catalog version, so declining is not asked again until the
shipped file itself moves; `update --check` reports those as
`<name> has a new version; update shows the change and asks`. Replacing still leaves the timestamped
`.bak`.

### Build tools

Before installing, tlstore notes which `build=` packages are missing (`pacman -Q`, else `dpkg -s`,
else "is the command there") and installs them. When the plan finishes it asks
`Remove the tools that were only needed for installing (<pkgs>)? [y/N]`, and on yes removes exactly
those (`pacman -R --noconfirm` / `apt remove -y`) and clears `~/.cache/tlstore`. `-y` may answer
this one. `info` shows a `Builds with` line. Only `claude-code` declares one, `patchelf`.

### `nvim-theme`

`docs/en/examples/nvim/lua/launcher/material_palette.lua` (lifted out of `setup-nvim` unchanged) and
`docs/en/examples/nvim/colors/launcher-material.lua` (a real colorscheme that paints the standard
groups, the `@` treesitter captures, diagnostics and git/diff groups from the palette's base16 and
base30 tables, keeps the glass default and falls back to a fixed dark palette when the launcher has
never written wallpaper colours). Both are hidden `file` items required by the visible `nvim-theme`
bundle; Neovim itself is not installed. The colorscheme overrides the palette module's `reload` so a
wallpaper change re-applies it rather than base46's, which only NvChad has.

Enabling it in each config — plain, lazy.nvim/LazyVim, AstroNvim, NvChad — is in `docs/en/Tlstore.md`
rather than the item summary, which stays one sentence.

### `tlstore display`

Runs `$PREFIX/bin/termux-x11-gpu-setup` (installed by the app) with whatever arguments it is given,
and says the app needs updating when the file is not there.

### Pinning

A file in this repository is pinned to a tag when it has not changed since one, otherwise to the
commit that added it (`launcher:<path>@<sha>`). `build-catalog.sh` now also hashes a plain `http(s)`
source by downloading it once, so `fisher` and the fastfetch logo GIF are pinned like everything
else; such a URL must name a tag or commit, never a branch.

### As built

`catalog.tsv` is regenerated at serial 2026090603 and **unsigned** — `catalog.tsv.minisig` was
deleted rather than left describing a file it no longer matches. The maintainer signs the new one
before it ships.

The test suite reads one more documented knob, `TLSTORE_ASSUME_TTY`, because it has no pty to answer
the two questions that are only asked of a person. 512 checks green under `sh`, `dash`, `busybox sh`
and `bash --posix`; `shellcheck -s sh` clean. The colorscheme was checked with `nvim --headless`
0.12, with a wallpaper palette and without one.

`docs/en/examples/fastfetch.jsonc` still names `/data/data/com.termux/files/home/Pictures/gif/skel.gif`
outright, so on the VAJ edition the logo path does not resolve. Left alone here: changing it would
need its own commit and a new pin, and fastfetch's handling of `~` in a logo source is not something
this phase verified.
