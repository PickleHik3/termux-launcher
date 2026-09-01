# Making the Nix edition vanilla — study, and why it is not being done

**Decision (2026-09-01): the fork stays as it is.** What follows is the inventory that decision was
made from, so the next person to ask "how hard would it be to just track upstream nix-on-droid?"
does not have to rebuild it. Nothing here is implemented.

The question was whether the Nix edition could ship a stock
[nix-on-droid](https://github.com/nix-community/nix-on-droid) environment — users following the
official manual for everything — with only the launcher's own extras on top.

## Where the divergence actually lives

Not in the app. `app/src`, `app/src/main/res` and `app/build.gradle` on `nix-edition` contain no
reference to `setup-launcher`, `setup-toolkits` or the flake template; the only nix-aware line is
the ABI comment in `app/build.gradle`. Everything is in `PickleHik3/nix-on-droid@launcher-nix`,
which is **48 files / ~3,225 added lines / 38 commits** ahead of `nix-community/nix-on-droid@master`
(and 2 behind).

That divergence is two unrelated things sharing a branch:

| | Files | Added lines |
|---|---|---|
| Identity, proot and robustness — unavoidable | ~26 | ~480 |
| The opinionated shell stack — a product decision | 21 | ~2,750 |

The first group: the `com.termux.launcher.nix` rebase (`modules/build/config.nix`,
`initial-build.nix`, `environment/android-integration.nix`, `networking.nix`, `modules/user.nix`,
`login/*`, `pkgs/android-integration/*`, `pkgs/bootstrap.nix`, and the renames in
`templates/{minimal,advanced,home-manager}`); `curl` and `gnused` in the base environment, which the
APK-installed `launcherctl` and `tai` clients shell out to; the proot patches
(`tcgets2-termios2-translate` 239 lines, `at-phdr-covering-segment` 66) plus the store paths pinned
in `login/default.nix`; stateVersion 26.05 acceptance; profile recovery; the bootstrap `var/var`
fix. **305 of those ~480 lines are the two proot patches.**

The second group is what "going vanilla" means deleting: `modules/environment/setup-launcher.nix`
(108 lines), its entry in `modules/environment/path.nix`, the `launcher = { … }` registration at
`flake.nix:153`, and `templates/launcher/**` — `flake.nix`, `home.nix`, `nix-on-droid.nix`,
`nvim-tools.nix`, `toolkits.nix`, `toolkit-tools.nix`, `sshd-tools.nix`, `overlays.nix`, and
`config/{config.fish, personal.fish, fastfetch/, nvim/, ohmyposh/}`.

## What it would cost

**New users pay for it.** `setup-launcher` exists because the first hour was otherwise four
commands typed correctly at a phone keyboard, one of which deletes two files. Vanilla means the
upstream manual applies verbatim — the real prize — but the one-command on-ramp goes with it. That,
not any breakage, is the reason the trim was declined.

**Existing users mostly do not.** Their config is a *copy*: `nix flake init` put `home.nix`,
`toolkits.nix`, `toolkit-tools.nix`, `nvim-tools.nix`, `sshd-tools.nix`, `overlays.nix`, the fish
config, both oh-my-posh themes, the AstroNvim theme and the fastfetch patch in
`~/.config/nix-on-droid`. The only references back to the fork are `flake.nix:13` (the module-set
input, which would stay) and a hint string at `toolkit-tools.nix:77`; `setup-nvim` clones AstroNvim,
NvChad, LazyVim and kickstart from their own upstreams. Their `flake.lock` pins the fork, so nothing
reaches them until `nix flake update`. What would change:

- `setup-launcher` disappears at their next switch — it lives in the base environment, not the
  template. It is the first-run installer they already ran. `setup-toolkits` and `setup-nvim` are in
  their own directory and keep working.
- A stale line in their `config.fish` greeting and the hint at `toolkit-tools.nix:77` would point at
  a template that no longer exists.

Two things would genuinely break, both avoidable:

1. **Force-pushing `launcher-nix`.** Every existing `flake.lock` pins a rev. Rebase + force-push
   orphans it, GitHub eventually stops serving it, and a rebuild from that lock fails at fetch with
   no path forward but hand-editing the lock. Land such a trim as new commits and *merge* upstream.
2. **Re-running `nix flake init -t …#launcher`** after a repair, on a second device, or from a
   restored backup. A frozen tag (`launcher-nix-v0.2.38`) keeps it working and keeps the hint true.

Also: deleting `docs/en/Nix_Package_Management.md` (616 lines) would 404 a page on the site people
have bookmarked — leave a stub. Republishing the bootstrap would be tidy but is not required: the
zip ships a store closure (`pkgs/bootstrap.nix`) and removing a package only leaves one unused path
in it; existing installs never re-download it.

## The three showcase binaries on nix

If the extras were ever unbundled from the template, they would not go into the bootstrap:

- **fastfetch cannot be a Termux build on nix at all.** It links `libandroid-glob` — which
  nix-on-droid has no equivalent of — and `dlopen`s ImageMagick and Chafa, which on nix are store
  paths no RUNPATH can predict. It has to be the nixpkgs build with our patch, which already exists
  as `recipes/nix/fastfetch/overlay.nix` (~20 minutes of on-device compiling; nothing prebuilt).
- **kitten may already be free**: nixpkgs' kitty produces a separate `kitten` output
  (`pkgs/by-name/ki/kitty/package.nix:271-287`). Unverified risk: that is a `linux/arm64` Go build,
  and `recipes/cross/README.md` documents why ours is `android/arm64` — a linux build dies with
  `SIGSYS` on `faccessat2` under Android's seccomp filter, which proot does not lift. One device
  check decides whether ours is needed at all.
- **sigye is not in nixpkgs** (code search: 0 hits). It links only Bionic, so the published
  `sigye-aarch64` should run under proot — untested — and would wrap into ~15 lines of
  `fetchurl` + `install -Dm755`, with `dontStrip` and `dontPatchELF`.

Bundling all three into the bootstrap would add ~34 MB to zips that are currently 35 MB (aarch64)
and 37 MB (x86_64) — roughly doubling every first-run download for optional eye candy, 26 MB of it
a kitten nixpkgs may already provide. The builds are aarch64-only, so the x86_64 edition would get
nothing, and binaries in the bootstrap are outside nix's management: no upgrades, erased by a
bootstrap reinstall, and every change needs the human-gated republish that
`check_nix_bootstrap_pin.yml` polices.

## If it is ever done

- **No in-app popup.** The app has no what's-new mechanism to hang it on (no changelog dialog, no
  version-seen tracking in `app/src`), the APK is not what changes, and the notice would arrive at
  app-update time while the actual change lands whenever the user next runs `nix flake update` —
  possibly never.
- **A notice shim instead.** Keep `setup-launcher` as ~15 lines that print what happened, that the
  user's config is untouched, the frozen-tag command, and where the extras live. It reaches exactly
  the people who look for it, at the moment they look, with no app change. Delete it a release or
  two later.
- **Upstream the four fixes first.** Profile recovery, the bootstrap `var/var` nesting, the termios2
  ioctl translation and the AT_PHDR derivation are not launcher-specific. Upstreamed — the proot
  ones to termux/proot — the fork drops toward the rename alone, and the pinned proot store path
  that `check_nix_bootstrap_pin.yml` exists to guard goes with them. That pin is the edition's only
  unrecoverable first-boot failure, so this is worth doing whether or not the rest ever happens.
