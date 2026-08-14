# Nix edition: a beginner's guide

This page is for people who installed the **Nix edition** (`com.termux.launcher.nix`) and have
never used Nix before. It walks the first hour end to end: what you are looking at, what to type,
what is normal, and what to do when something looks wrong.

If you already know Nix, skip to [Nix package management](Nix_Package_Management.md) — that page is
the reference; this one is the tutorial.

## What you actually installed

Three ideas, in plain terms:

**Nix is a package manager**, like `apt` or `pkg`, but it installs every package into its own
folder under `/nix/store` and then links the pieces you asked for into your environment. Nothing
ever overwrites anything else, so two versions of the same program can sit side by side, and
removing one cannot break the other.

**`nixpkgs` is the package collection** Nix installs from — around 100,000 packages, prebuilt and
downloaded from an official cache. This is the reason the edition exists: it is a far larger and
faster-moving set than the Termux repositories.

**Your setup is a file, not a history.** In the Termux edition, your environment is whatever you
happened to install over the months. Here, a config file lists what you want, and one command makes
the system match the file. Copy that file to a new phone and you get the same environment. Change
your mind and one command puts the previous version back.

The trade-off is that this environment is not Termux. Programs come from `nixpkgs`, built against
glibc like on a desktop Linux, and they run inside a [proot](https://proot-me.github.io/) that maps
`/nix`, `/bin`, `/etc` and `/usr` into the app's private directory. It is not emulation — the code
is native `aarch64` and runs on the CPU directly — but process-heavy work (long shell loops, big
compiles) pays a small overhead. Your Android system stays visible at `/android`.

## First launch, step by step

**1. Open the app and wait.** It downloads a ~38 MB bootstrap and unpacks it. No input needed.

**2. Answer the flakes question with `y`.**

```
Do you want to set it up with flakes? (y/N)
```

"Flakes" is Nix's newer way of pinning exactly which version of `nixpkgs` your config uses. Say
yes: the template below needs it, and it is the path this fork tests.

**3. Wait through the first build. This one is slow.** Minutes on a recent phone, up to ~35 on an
old one. The line `evaluating derivation ...` sits there printing nothing for a long stretch — that
is normal, it is not frozen. **Keep the app in the foreground**: Android cuts an app's network when
it goes to the background, which stalls the download.

**4. You get a `bash-5.3$` prompt.** That is a working but bare environment — plain bash, no
prompt theming, few tools. Continue below.

## Get the real shell environment

The fork ships a template that recreates the shell used throughout this wiki: fish as the login
shell, an oh-my-posh prompt, `eza`/`zoxide`/`yazi`, Neovim, fastfetch with an animated logo, and a
small sshd toolset.

```sh
cd ~/.config/nix-on-droid
rm flake.nix nix-on-droid.nix
nix flake init -t github:PickleHik3/nix-on-droid/launcher-nix#launcher
nix-on-droid switch --flake ~/.config/nix-on-droid
```

The last command is the one to remember: **`nix-on-droid switch` is how any config change takes
effect.** It reads your files, builds what changed, and swaps the environment over.

Then **open a new session** — the login shell only changes for sessions started after the switch.

If it fails, you have lost nothing: the previous environment is still active, and the error text
names the file and line it tripped on.

## Pick what you want installed

The template installs the shell, the eye candy, Neovim and a build toolchain, and leaves the
language toolchains off. `setup-toolkits` is a checklist for changing that — it edits
`~/.config/nix-on-droid/toolkits.nix` and switches for you:

```sh
setup-toolkits
```

```
      1) everything            every toolkit above except the animated logo
      2) shell essentials      shell + eye candy, nothing else
      3) pick one by one
      4) quit, change nothing
```

The toolkits are `shell`, `eye-candy`, `editor`, `build` (cc/make/cmake/autotools — build things
from source), `node` (nodejs/npm/npx), `go`, `python` (python3 + uv/uvx), and `animated-logo` (a
patched fastfetch, ~20 minutes of on-device compiling — everything else comes prebuilt from the
cache).

`npm install -g`, `go install` and `uv tool install` are wired to write into your home directory
(`~/.npm-global`, `~/go/bin`, `~/.local/bin`), so what you install with them persists across
switches and rollbacks. See
[Global installs](Nix_Package_Management.md#global-installs-npm--g-go-install-uv-tool) for why that
needs saying at all.

Nothing about this is magic: the script writes booleans into a file you can edit yourself, and
`nix-on-droid rollback` undoes the result either way.

## Installing things

Two ways, and it is worth learning both.

**Quick and imperative**, for trying something out:

```sh
nix search nixpkgs ripgrep      # find it
nix profile install nixpkgs#ripgrep
nix profile list                # what you installed this way
nix profile remove ripgrep
```

Also useful: `nix run nixpkgs#cowsay -- moo` runs a program once without installing it, and
`nix shell nixpkgs#nodejs` drops you in a shell where `node` exists until you type `exit`.

**Declarative**, for things you actually want to keep. Edit `~/.config/nix-on-droid/home.nix`:

```nix
{ pkgs, ... }:
{
  home.packages = with pkgs; [
    ripgrep
    fd
    jq
  ];
}
```

then:

```sh
nix-on-droid switch --flake ~/.config/nix-on-droid
```

Prefer the declarative route for anything you would be annoyed to lose. It is the whole point of
the edition: the file *is* your system, and you can copy it, diff it, or put it in git.

## Which file do I edit?

The template splits the config across files, and putting a line in the wrong one produces a
confusing error. Three files, three jobs:

| File | What belongs here |
|---|---|
| `flake.nix` | inputs (which `nixpkgs`, home-manager, the fork), overlays |
| `nix-on-droid.nix` | the *environment*: login shell, `/etc`, base packages, Android integration |
| `home.nix` | *your user*: dotfiles, per-user packages, session variables |
| `toolkits.nix` | which package groups `home.nix` installs — booleans, what `setup-toolkits` writes |

Rule of thumb: a file under `~/.config`, or a tool only you invoke → `home.nix`. The login shell, a
base command every script assumes (git, curl, sed), or Android glue → `nix-on-droid.nix`.

They are evaluated by two different systems, so options are not interchangeable. Pasting `home.*`
options into `nix-on-droid.nix` fails with `error: attribute 'hm' missing`; going the other way
fails with `option 'home' does not exist`. If you see either message, you edited the wrong file.

## Undo

```sh
nix-on-droid rollback
```

That returns the previous generation — the previous complete state of your environment. This is the
safety net that makes experimenting cheap: there is no "I broke my install and have to start over"
in this edition.

## Everyday housekeeping

```sh
nix profile upgrade --all       # update imperatively-installed packages
nix flake update ~/.config/nix-on-droid   # move the pinned nixpkgs forward, then switch
nix-collect-garbage --delete-old          # reclaim disk from old generations
```

Old generations are what `rollback` uses, so run the garbage collection when you are happy with the
current state, not while debugging.

## Storage and Android

`termux-setup-storage` works here and gives the environment access to your shared storage. The
Android filesystem is visible at `/android` regardless.

Some system binaries need the `/android` prefix to run, because the proot's `/bin` and `/usr` are
Nix's, not Android's.

## SSH into the phone

The template ships a small toolset:

```sh
sshd-start              # start it (also generates the host key the first time)
sshd-status             # running? which port? autostart armed?
sshd-stop
sshd-autostart on|off   # start it with new interactive sessions
```

Port **8023** by default (the Termux edition conventionally owns 8022 on the same device). It is
key-only: put your client's public key in `~/.ssh/authorized_keys`, then
`ssh -p 8023 nix-on-droid@<phone-ip>`. The server does not survive a reboot or a force-stop —
reopen the app and it comes back with the next session if autostart is armed. Log:
`~/.config/sshd/log`.

## When something looks wrong

**The first build looks stuck.** If it is on `evaluating derivation ...`, it is not. Give it time,
and keep the app foregrounded.

**A download is crawling or stalling.** Android throttled the app in the background. Foreground it.

**A package compiles instead of downloading.** You asked for something the binary cache does not
have prebuilt — usually because an overlay or an override changed it. On a phone that can mean tens
of minutes. Let it run foregrounded, or drop the override.

**`command not found` right after a switch.** Open a new session; PATH is set at session start.

**A switch failed with an option error.** You almost certainly edited the wrong file — see the
table above.

**`nvim` complains in `:checkhealth`.** If your config was created before the template shipped the
full neovim toolchain, a few packages are missing — see
[Neovim / LazyVim](Nix_Package_Management.md#neovim--lazyvim) for the exact lines to add.

**The glass bars are not blurred.** Unrelated to Nix: the launcher needs permission to read your
wallpaper. **Settings › Services & permissions › Wallpaper access**, or set a wallpaper from inside
the launcher, which needs no permission at all.

## Words you will keep seeing

- **derivation** — a build recipe. "Evaluating derivations" = working out what to build or fetch.
- **generation** — one complete saved state of your environment. `rollback` steps back one.
- **flake** — a config that pins its inputs to exact versions, so the same files give the same result.
- **profile** — where imperative `nix profile install` packages land.
- **overlay** — a patch to the package set, e.g. "use my modified fastfetch instead of the stock one".
- **home-manager** — the system that manages your user's dotfiles and packages (`home.nix`).

## Where to go next

- [Nix package management](Nix_Package_Management.md) — the reference for this edition, including
  the animated fastfetch overlay and the full sshd details.
- [Nix fork differences](Nix_Fork_Differences.md) — what this fork changes versus upstream nix-on-droid.
- [Using the launcher](Launcher_Usage.md) — the launcher itself is identical across editions.
- [Nix manual](https://nix.dev) and the
  [nix-on-droid wiki](https://github.com/nix-community/nix-on-droid/wiki) for everything past this page.
