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

**No emulation is involved** — this is not QEMU. Every binary is native
`aarch64` code executing directly on the CPU. proot is a
ptrace-based supervisor that intercepts syscalls only to rewrite
*paths*: nixpkgs binaries hardcode `/nix/store/...` locations that an
Android app cannot own, so proot translates `/nix`, `/bin`, `/etc` and
`/usr` to the app's private directory on the fly (the real Android
system stays visible at `/android`). The cost is a small
syscall-interception overhead — noticeable in `fork`/`exec`-heavy
workloads, irrelevant for compute — not the instruction-translation
cost of an emulator.

## Getting started (first launch)

1. Install the nix-edition APK and open it. The app downloads and
   unpacks the bootstrap (~38 MB) on its own — no interaction needed.
2. The first terminal session asks:

   ```
   Do you want to set it up with flakes? (y/N)
   ```

   Answer **`y`**. Flakes are what the shell template below uses, and
   the flake setup is the tested path.
3. The first generation now builds on the device. Expect minutes on a
   recent phone and up to ~35 minutes on an old one. The
   `evaluating derivation ...` phase prints nothing for a long time —
   that is normal, it is not stuck. Keep the app in the foreground
   (Android cuts the app's network in the background).
4. You have a `bash-5.3$` prompt when it finishes. That is the stock
   environment; continue below to get the full shell setup.

## Shell environment (fish, oh-my-posh, eza/zoxide/yazi, LazyVim, fastfetch)

The fork ships a ready-made flake template that recreates the launcher's
reference shell — the same fish config, oh-my-posh Material themes, and
CLI stack the examples in this wiki use. Replace the minimal first-boot
files with it:

```sh
cd ~/.config/nix-on-droid
rm flake.nix nix-on-droid.nix        # the minimal files from first boot
nix flake init -t github:PickleHik3/nix-on-droid/launcher-nix#launcher
nix-on-droid switch --flake ~/.config/nix-on-droid
```

Open a new session afterwards: fish is the login shell, prompt themed,
`eza`/`zoxide` wired in.

The template's layout — **which file owns what matters**:

| File | Module type | What goes here |
|---|---|---|
| `flake.nix` | flake wiring | inputs (nixpkgs, home-manager, the fork), overlays |
| `nix-on-droid.nix` | system module | `environment.packages`, `user.shell`, `android-integration` |
| `home.nix` | home-manager module | `home.packages`, dotfiles (`xdg.configFile`), activation hooks |
| `config/` | plain files | the actual `config.fish`, oh-my-posh themes, `fastfetch/config.jsonc` |

The split exists because two different configuration systems are wired
together. `nix-on-droid.nix` is evaluated by nix-on-droid's own module
set and manages the *environment* — everything outside your home
directory: the login shell, `/etc`, base packages every script assumes,
Android glue like `termux-setup-storage`. `home.nix` is evaluated by
[home-manager](https://github.com/nix-community/home-manager) (embedded
via the `home-manager.config` bridge in `flake.nix`) and manages *your
user*: dotfiles, per-user packages, activation hooks, session
variables.

Rule of thumb: a file under `~/.config` or a tool only you invoke →
`home.nix`; the login shell, a base CLI everything expects (git, curl,
sed), or Android integration → `nix-on-droid.nix`. `flake.nix` is where
the two meet — it owns the inputs, the overlays, and the wiring.

Do **not** paste `home.nix` contents into `nix-on-droid.nix` or vice
versa: `home.*` options and `lib.hm` only exist inside home-manager, so
the switch fails with `error: attribute 'hm' missing` /
"option `home' does not exist". System options and home options live in
different files by design.

### Animated fastfetch logo

`home.nix` already installs fastfetch and the config expects a GIF at
`~/Pictures/gif/skel.gif` — drop any GIF there (fastfetch falls back to
text output while it is missing). Stock nixpkgs fastfetch shows only the
first frame; for full animation over the kitty graphics protocol, add
the overlay from
[`recipes/nix/fastfetch`](https://github.com/PickleHik3/termux-launcher/tree/dev/recipes/nix/fastfetch)
(copy `overlay.nix` and the patch next to `flake.nix`, register it in
the flake's `pkgs = import nixpkgs { ... overlays = [ ... ]; }`). It
compiles on the device — serial build, roughly 20–50 minutes depending
on the phone, app foregrounded.

### SSH into the phone

The template ships a small sshd toolset (`sshd-tools.nix`), declarative
where it matters and manual where you want control:

```sh
sshd-start            # start (idempotent); generates the host key on first use
sshd-stop             # stop
sshd-status           # running state, port, autostart arming
sshd-autostart on|off # arm/disarm starting it with new interactive sessions
```

Nothing starts unless you run `sshd-start` yourself or explicitly arm
`sshd-autostart on`. Details:

- Port **8023** by default (the Termux edition's sshd conventionally
  owns 8022 on the same device); override by writing a number to
  `~/.config/sshd/port`.
- Key-only auth. Append your client's public key to
  `~/.ssh/authorized_keys`, then `ssh -p 8023 nix-on-droid@<phone-ip>`.
- The ed25519 host key lives in `~/.ssh/hostkeys` — generated on
  device, never part of the nix store.
- Server log: `~/.config/sshd/log`. The server does not survive a
  reboot or an app force-stop; reopen the app and it comes back with
  the next session if autostart is armed.
- If you run sshd by hand instead: it must be invoked by absolute path
  (it re-execs itself), needs `-o StrictModes=no` on Android, and a
  port above 1024.

### After a switch

- Open a **new session** to pick up the new login shell and PATH.
- `launcherctl` and `tai` live in the proot's `/bin`; the template's
  `config.fish` puts that on the PATH for fish sessions.
- Something broke? `nix-on-droid rollback` restores the previous
  generation.

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
