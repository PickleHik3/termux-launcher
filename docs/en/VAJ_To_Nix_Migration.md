# Migrating from the VAJ edition to the Nix edition

The VAJ edition (`io.vaj.tl`) is being deprecated. It has received security-critical fixes only
since `v0.2.34-vaj`, and it is the one edition whose packages come from a small APT repository
(`https://repo.pathayam.xyz`) maintained by hand. Keeping that repository current — and current on
security updates in particular — is not sustainable for one person, so the edition is winding down
rather than pretending to be maintained.

This page moves you to the **Nix edition** (`com.termux.launcher.nix`), which is the direct
replacement: same launcher, same terminal, but packages come from the official `nixpkgs` binary
cache instead of a private repository, so updates no longer depend on this project.

If you would rather go the other way — full upstream Termux package compatibility — install the
**Termux edition** (`com.termux`) instead. The backup and restore steps below apply just the same;
only the package step differs (there you keep using `pkg install`).

## Before you start

- **Nothing is uninstalled by this guide.** `io.vaj.tl` and `com.termux.launcher.nix` are separate
  Android packages with separate private storage, so both can sit on the phone until you are
  satisfied with the new one.
- **Nothing carries over automatically.** Android gives each package its own home directory. Files
  move through shared storage; APT packages do not move at all and get reinstalled from `nixpkgs`.
- **Set aside time for the first launch.** The Nix edition's first build takes minutes on a recent
  phone and up to ~35 minutes on an old one.

## 1. Back up your VAJ home directory

In the **VAJ** app, give it access to shared storage (once) and write an archive there:

```sh
termux-setup-storage
cd ~
tar -czf ~/storage/downloads/vaj-home-backup.tar.gz --exclude=.cache \
    .termux .ssh .config .local .gitconfig bin
```

`tar` complains about any of those that you do not have; drop those names from the line. Add
whatever else you keep in `~` — scripts, notes, project checkouts. To take everything:

```sh
tar -czf ~/storage/downloads/vaj-home-backup.tar.gz --exclude=.cache .
```

Worth listing your installed packages too, so you know what to ask for later:

```sh
apt list --installed 2>/dev/null | cut -d/ -f1 > ~/storage/downloads/vaj-packages.txt
```

**Launcher settings do not live in the home directory.** Dock layout, folders, widgets, theme,
keybinds and extra-keys are Android app preferences, and Android keeps those private to
`io.vaj.tl`. They cannot be copied across; you set them again in the new app. The terminal-side
configuration *does* travel: `~/.termux/termux.properties`, `~/.termux/launcher.properties`,
`~/.termux/colors.properties`, `~/.termux/fonts.d`, and the shell configs under `~/.config`.

Copy the archive off the phone as well if it matters to you — it is your only copy once VAJ is
uninstalled.

## 2. Install the Nix edition

1. Open the [releases page](https://github.com/PickleHik3/termux-launcher/releases) and pick the
   newest **`vX.Y.Z-nix`** tag (published as a prerelease).
2. Install the `arm64-v8a` APK, or `universal` if you are unsure of the device architecture.
3. If you use the add-ons, install the matching **`nix-v*`** builds — TLNix:API and TLNix:Styling
   from the [termux-api](https://github.com/PickleHik3/termux-api/releases) and
   [termux-styling](https://github.com/PickleHik3/termux-styling/releases) forks. The `-vaj`
   companions pair with `io.vaj.tl` only; they will not talk to the Nix edition.

Do not try to install one edition over another — Android treats them as different apps with
different signing expectations.

## 3. First launch

Follow the [Nix beginner's guide](Nix_Getting_Started.md) — the short version:

1. Open the app and let it download and unpack the bootstrap (~38 MB).
2. Answer the flakes question with **`y`**.
3. Wait out the first build with the app in the **foreground**; `evaluating derivation ...` printing
   nothing for a long stretch is normal.
4. At the `bash-5.3$` prompt, install the reference shell environment:

   ```sh
   cd ~/.config/nix-on-droid
   rm flake.nix nix-on-droid.nix
   nix flake init -t github:PickleHik3/nix-on-droid/launcher-nix#launcher
   nix-on-droid switch --flake ~/.config/nix-on-droid
   ```

5. Pick the tool groups you want with `setup-toolkits` (shell, editor, node, go, python, …).

## 4. Restore your files

In the **Nix** app:

```sh
termux-setup-storage
cd ~
tar -xzf ~/storage/downloads/vaj-home-backup.tar.gz
```

Then check the pieces that are environment-specific rather than personal:

- **`~/.termux/*.properties`** restore as-is; reload them from the app (Settings, or
  `termux-reload-settings`).
- **`~/.ssh`** needs its permissions back: `chmod 700 ~/.ssh && chmod 600 ~/.ssh/id_*`.
- **Shell config.** The template makes fish the login shell and owns `~/.config/fish/config.fish`
  through home-manager. Merge your own additions into the template's config rather than dropping
  your old file on top of it, or the next `nix-on-droid switch` will overwrite them.
- **Scripts with `#!/data/data/io.vaj.tl/files/usr/bin/...` shebangs** must be repointed. Inside the
  Nix environment the portable form is `#!/usr/bin/env bash`.

## 5. Replace your packages

APT packages do not migrate. Take the list from step 1 and get the same tools from `nixpkgs`:

| VAJ (APT) | Nix edition |
|---|---|
| `pkg install foo` | add `foo` to `environment.packages` in `nix-on-droid.nix` (or `home.packages` in `home.nix`), then `nix-on-droid switch --flake ~/.config/nix-on-droid` |
| one-off install, no config edit | `nix profile install nixpkgs#foo` |
| `pkg install` a whole toolchain | `setup-toolkits --enable node,go,python` |
| `pkg uninstall foo` | remove it from the same file and switch again |
| `pkg upgrade` | `nix flake update --flake ~/.config/nix-on-droid`, then switch again |
| a bad upgrade | `nix-on-droid rollback` — there is no APT equivalent |
| searching the repo | `nix search nixpkgs foo`, or [search.nixos.org](https://search.nixos.org/packages) |

Names are usually identical; where they are not, the search above finds them. The full reference is
[Nix package management](Nix_Package_Management.md), including where `npm -g`, `go install` and
`uv tool install` put their files here.

## 6. Switch the home app over, then remove VAJ

1. Make the Nix edition your launcher: in-app **Settings → Apps → Set as default
   launcher**, then pick it on Android's Home app screen.
2. Use it for a few days. Nothing is lost while `io.vaj.tl` is still installed.
3. When you are sure, uninstall the VAJ app and its `-vaj` companions. **Uninstalling deletes its
   private home directory permanently** — keep the archive from step 1 until you no longer care
   about anything in it.

## Differences to expect

- **Not a Termux userland.** Programs come from `nixpkgs`, built against glibc, running inside a
  [proot](https://proot-me.github.io/). Code is native `aarch64` — no emulation — but `fork`/`exec`
  heavy work (long shell loops, big builds) pays a small overhead. Android stays visible at
  `/android`.
- **Your setup is a file.** Installing means editing a config and switching, which is more typing
  for one package and far less work when you move to a new phone or want to roll a change back.
- **Some Termux-only packages have no `nixpkgs` counterpart.** Most have an equivalent under a
  different name; occasionally there is none.
- **The launcher itself is unchanged** — same terminal, panes, palette, keyboard, `launcherctl` and
  `tai` tooling.

## If something is missing

If you depend on something the Nix edition does not cover, open an issue at
[github.com/PickleHik3/termux-launcher/issues](https://github.com/PickleHik3/termux-launcher/issues)
and describe the use case. Security-critical fixes to the VAJ edition continue in the meantime.

## See also

- [Nix edition: a beginner's guide](Nix_Getting_Started.md)
- [Nix package management](Nix_Package_Management.md)
- [Nix fork differences](Nix_Fork_Differences.md)
- [Getting started](Launcher_Getting_Started.md)
