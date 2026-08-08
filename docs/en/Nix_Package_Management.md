# Nix Package Management

The **Nix edition** (`com.termux.launcher.nix`) replaces the APT package
manager with [Nix](https://nixos.org) in a
[Nix-on-Droid](https://github.com/nix-community/nix-on-droid)-style
environment. Instead of `pkg install`, packages come from the official
`nixpkgs` collection — tens of thousands of prebuilt packages served from
`cache.nixos.org`, independent of any Termux repository.

The launcher itself is unchanged: same terminal, palette, panes,
keyboard, and `launcherctl`/`tai` tooling as the Termux edition.

## How it works

On first launch the app downloads a bootstrap that contains the Nix
package manager and a minimal shell. Every session then runs inside a
[proot](https://proot-me.github.io/) that maps the app's private
directory to `/nix`, so unmodified upstream binaries (glibc, not Android
libc) run as-is and the official binary cache applies.

## Everyday commands

Quick, imperative package management:

```sh
# search the package set
nix search nixpkgs ripgrep

# install / remove for your user profile
nix profile install nixpkgs#ripgrep
nix profile remove ripgrep

# list what's installed, upgrade everything
nix profile list
nix profile upgrade --all

# try a tool without installing it
nix run nixpkgs#cowsay -- moo
nix shell nixpkgs#nodejs   # temporary shell with node in PATH
```

## Declarative setup (recommended)

The system environment lives in `~/.config/nix-on-droid/nix-on-droid.nix`.
Add packages there and rebuild — the config *is* your installed system,
reproducible and rollback-able:

```nix
{ pkgs, ... }:
{
  environment.packages = with pkgs; [
    fish
    neovim
    ripgrep
    eza
  ];

  user.shell = "${pkgs.fish}/bin/fish";
}
```

Apply with:

```sh
nix-on-droid switch --flake ~/.config/nix-on-droid
```

Roll back to the previous generation any time:

```sh
nix-on-droid rollback
```

## Housekeeping

```sh
# reclaim disk from old generations and unused store paths
nix-collect-garbage --delete-old
```

## Differences from the Termux edition

| | Termux edition (`com.termux`) | Nix edition (`com.termux.launcher.nix`) |
|---|---|---|
| Package manager | `pkg` / APT | `nix` / `nix-on-droid` |
| Package source | Termux repos (bionic builds) | official `nixpkgs` (glibc builds, via proot) |
| Install command | `pkg install foo` | `nix profile install nixpkgs#foo` |
| Config-as-code | — | `nix-on-droid.nix` + flakes |
| Rollbacks | — | `nix-on-droid rollback` |
| Termux:API tools | native | available; some tools need the `/android` prefix for system binaries |

## Tips and caveats

- Everything runs under proot: a small syscall-translation overhead
  applies, mostly noticeable in process-heavy workloads.
- Compiling large packages on-device works but is slow — prefer cached
  binaries, and keep the app in the foreground during long downloads or
  builds (Android cuts background network for the app).
- The Android system is visible at `/android` inside the environment.
- First-run bootstrap and the environment defaults come from this
  project's [nix-on-droid fork](https://github.com/PickleHik3/nix-on-droid).

Nix is a deep tool — the [official Nix manual](https://nix.dev) and the
[nix-on-droid wiki](https://github.com/nix-community/nix-on-droid/wiki)
cover the rest.
