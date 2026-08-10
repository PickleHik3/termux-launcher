# Nix Fork Differences

The Nix edition is built from a fork of
[nix-community/nix-on-droid](https://github.com/nix-community/nix-on-droid):
[PickleHik3/nix-on-droid](https://github.com/PickleHik3/nix-on-droid),
branch `launcher-nix`. The delta is deliberately small — a dozen commits
that rebase cleanly — and splits into four groups.

## Package identity

Upstream hardcodes `com.termux.nix` as the installation directory
(`installationDir` is a read-only option baked into every store path,
which is why this is a fork and not a wrapper flake). The fork renames
the package family to `com.termux.launcher.nix` everywhere it is baked:
bootstrap store paths, the login script, `/etc` defaults, and the
`termux-am` socket path. Channel and flake default URLs are repointed
at the fork so a fresh device never pulls upstream definitions with the
wrong paths.

> **Reusing an existing nix-on-droid flake?** A flake written for
> upstream pins `github:nix-community/nix-on-droid` as its
> `nix-on-droid` input. Used unchanged on this edition it rebuilds the
> login chain for `com.termux.nix` and breaks the app on the next
> session start. Repoint the input to
> `github:PickleHik3/nix-on-droid/launcher-nix` before the first
> `nix-on-droid switch`.

## Compatibility patches

- **termios2 ioctl translation in proot**
  (`pkgs/proot-termux/tcgets2-termios2-translate.patch`): glibc 2.41+
  implements `tcgetattr`/`tcsetattr` via `TCGETS2`/`TCSETS2`, which
  Android's SELinux devpts allowlist denies for app domains — every
  terminal-touching glibc binary (bash readline included) and every nix
  build's pty setup would die with `Permission denied`. The fork's
  proot translates the termios2 ioctls to their classic counterparts at
  the syscall boundary. Candidate for upstreaming to termux/proot.
- **Bootstrap zip store database location** (`pkgs/bootstrap.nix`): the
  upstream recipe copies the nix store database to `nix/var/var/nix/db`
  (one `var` too deep), which silently invalidates every shipped store
  path. Upstream gets away with it because all its paths are
  substitutable from caches; the fork's patched proot is in no cache,
  so a fresh install died in the first switch. Fixed by copying
  directory contents. Applies upstream as well — candidate for a PR.

## Robustness

- **First-boot recovery**: an interrupted first-time setup used to
  soft-brick the install (`~/.nix-profile` dangling, every session
  dying under `set -e`). The login script now detects the missing
  profile and re-runs setup instead.
- **Default closure additions**: `curl` and `gnused` — the launcher's
  generated `launcherctl`/`tai` clients need them.

## Launcher integration

- **`launcher` flake template** (`templates/launcher/`, used by the
  [getting-started walkthrough](Nix_Package_Management.md)): fish as
  login shell, oh-my-posh Material themes, eza/zoxide/yazi, LazyVim,
  fastfetch/timg/chafa, the launcher's stock `config.fish`, and the
  `sshd-start`/`sshd-autostart` toolset. Kept compatible with both old
  and new oh-my-posh, ships `ncurses`, and puts the proot `/bin`
  (launcherctl, tai) on fish's PATH.
- `android-integration` defaults match the launcher's package name, so
  `termux-am`, `termux-setup-storage` and friends talk to the right
  socket out of the box.

## Syncing with upstream

The fork tracks upstream `master`. To update: rebase `launcher-nix`,
rebuild the bootstrap zip (`nix build .#bootstrapZip-aarch64 --impure`
with the nix-on-droid cachix substituter), re-pin the proot store paths
in `modules/environment/login/default.nix` if the proot derivation
changed, and re-upload the zip to the `nix-bootstrap` release tag.
