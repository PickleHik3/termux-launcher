# Termux Launcher

> [!WARNING]
> **This project is entirely vibe-coded.**
> I’ve been daily-driving it as a launcher on a Nothing Phone (2), and it has been rock-solid so far. It also does not appear to have any noticeable impact on battery life.

> [!CAUTION]
> The native AI backends—**Google LiteRT** and **Alibaba MNN**—are highly experimental. Be mindful of your device’s available RAM and processor capabilities when selecting models.

> [!IMPORTANT]
> **This is an independent fork.** Termux Launcher is not affiliated with, endorsed by, or
> supported by the official [Termux](https://github.com/termux/termux-app) project or its
> maintainers. It is a separately maintained modification that reuses the Termux name only to
> describe what it is built on. Do not report issues with this fork to Termux — file them
> [here](https://github.com/PickleHik3/termux-launcher/issues). The `com.termux` edition shares a
> package name with official Termux and **replaces it** on the device; the other editions install
> side by side. Termux is a trademark of its respective owners.

Termux Launcher is a terminal-first Android home launcher inspired by [TEL](https://github.com/t-e-l/tel), built on [termux-app](https://github.com/termux/termux-app) and [termux-monet](https://github.com/Termux-Monet/termux-monet).

**[🌐 Website & docs](https://picklehik3.github.io/termux-launcher-site/)** | [Releases & changelog](https://github.com/PickleHik3/termux-launcher/releases) | [Local AI API](docs/en/LauncherCtl_API.md) | [Termux AI](docs/en/Termux_AI.md)

> **Three editions are available.** The **`com.termux`** build is the **recommended** version — it stays fully compatible with the upstream Termux package ecosystem. The new **`com.termux.launcher.nix`** build swaps APT for the **[Nix](https://nixos.org) package manager**: it installs side-by-side with a stock Termux and pulls prebuilt packages straight from the official `nixpkgs` binary cache — see [Nix package management](docs/en/Nix_Package_Management.md). The **`io.vaj.tl`** build is a legacy **demo edition** on a small hand-maintained APT repository, kept only for preview installs. See [Editions](#editions).

<p align="center">
  <img src="screenshots/banner.png" alt="Termux Launcher hero showing terminal-first Android features and five device screenshots" width="100%">
</p>

## About

Designed to be a Terminal/TUI Android home launcher.
What started out as me just wanting sixel image drawing in [TEL](https://github.com/t-e-l/tel) spiralled out of scope to what this project is today.
All credits go to the amazing developers and contributors of Termux, TEL, and Termux:Monet.

<p align="center">
  <img src="screenshots/demo.gif" alt="Termux Launcher walkthrough showing the command palette, Kitty graphics, split panes, media, and keyboard shortcuts" width="360">
</p>

## Features

- Termux as the actual Android home launcher
- Sixel image drawing in terminal
- Native sessions, windows, recursive split and floating panes, layouts, workspace restore, and session browser
- Kitty keyboard/graphics protocols, safe hyperlinks, prompt navigation, and advanced font shaping
- TUI-tuned touch, unlike stock Termux: drags scroll mouse-aware apps, taps click, and a brief press-and-hold turns the finger into a held mouse button — drag vim selections or resize tmux/TUI panes by touch
- Searchable terminal command palette, tmux-style leader key, modal keymaps, and on-screen key hints
- Scrollback search with vim-style copy mode over the transcript
- App dock, full app drawer (vertical, horizontal, and category layouts) with folders and AI-assisted app categorization
- Android widget pages and an expandable status bar with live CPU, RAM, and weather
- Android Material theme integration for launcher surfaces and Termux shell theming
- Built-in terminal keyboard by default on fresh installs, with Android keyboard and no-keyboard options
- Launch Android apps from the shell with `launcherctl launch`
- `tai` shell command and OpenAI/Ollama-compatible localhost API for on-device LLM inference and model management
- Cloned/work-profile app discovery where Android exposes launcher profiles
- On-device LLM backends using Google's LiteRT and Alibaba's MNN
- Optional Shizuku integration for screen lock and privileged status helpers

## Editions

Every release ships the same launcher built from the same source; the editions differ only in Android package identity and the package ecosystem they use.

| | Termux edition | Nix edition | VAJ demo edition |
|---|---|---|---|
| Package name | `com.termux` | `com.termux.launcher.nix` | `io.vaj.tl` |
| Release tag | `vX.Y.Z` | `vX.Y.Z-nix` | `vX.Y.Z-vaj` |
| Alongside official Termux? | ❌ No — same package name, replaces it | ✅ Yes — installs side by side | ✅ Yes — installs side by side |
| Package manager | `pkg` / APT | [Nix](https://nixos.org) ([guide](docs/en/Nix_Package_Management.md)) | `pkg` / APT |
| Package repository | official Termux repos | official `nixpkgs` binary cache | small VAJ APT repo (`https://repo.pathayam.xyz`) |
| Architectures | arm64-v8a, armeabi-v7a, x86_64, x86 | arm64-v8a, x86_64 — bootstrap downloaded on first run | arm64-v8a (aarch64) only, bootstrap downloaded on first run |
| Companion add-ons | [Termux:API](https://github.com/PickleHik3/termux-api/releases) / [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases) (plain tags) | [TLNix:API](https://github.com/PickleHik3/termux-api/releases/tag/nix-v0.53.1) / [TLNix:Styling](https://github.com/PickleHik3/termux-styling/releases/tag/nix-v0.32.2) (`nix-v*` tags) | same forks, `-vaj` tagged releases |

Pick the **Termux edition** for the classic Termux experience — the launcher as your Termux, fully compatible with the upstream Termux package ecosystem. Pick the **Nix edition** if you want the entire `nixpkgs` collection, declarative configs, and rollbacks next to an existing Termux install — it is built on a [Nix-on-Droid](https://github.com/nix-community/nix-on-droid)-style environment; new to Nix? start with the [beginner's guide](docs/en/Nix_Getting_Started.md), then the [package management reference](docs/en/Nix_Package_Management.md). The **VAJ edition is a legacy demo**: it predates the Nix edition as the side-by-side option and is kept only for preview installs.

> ⚠️ **The VAJ demo edition is being deprecated.** It has been security-fixes-only since `v0.2.34-vaj`. It runs off my manually maintained custom APT repo (`https://repo.pathayam.xyz`), which carries only a small fraction of the Termux package set; keeping its crucial packages updated is no longer sustainable, and packages that stop receiving security updates are a liability rather than a feature. Move to the Nix edition instead — the **[VAJ to Nix migration guide](docs/en/VAJ_To_Nix_Migration.md)** covers backing up the home directory, installing the Nix build side by side, restoring your files, and replacing APT packages from `nixpkgs`. If the Nix edition does not cover a specific need, [open an issue](https://github.com/PickleHik3/termux-launcher/issues).

Companion add-ons must be the matching builds from this project's forks (they share the launcher's signing key and package family); official F-Droid add-ons will not pair with any edition. Nix-edition companions ship as `nix-v*` tagged releases (TLNix:API, TLNix:Styling).

## Installation

Download the latest APK of your chosen [edition](#editions) from [Releases](https://github.com/PickleHik3/termux-launcher/releases), install it, then select Termux Launcher as your Android home app.

Recommended setup:

- [Shizuku](https://github.com/rikkaapps/shizuku) only if you want optional privileged features
- Matching companion forks when using Termux add-ons (pick the release matching your [edition](#editions): plain tag for `com.termux`, `nix-v*` tag for `com.termux.launcher.nix`, `-vaj` tag for `io.vaj.tl`):
  - [Termux:API](https://github.com/PickleHik3/termux-api/releases)
  - [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases)

The built-in terminal keyboard is enabled on fresh installs; an external keyboard app is optional.
See [Getting Started](docs/en/Launcher_Getting_Started.md) for the setup flow.

### Quick start

This edition is configured declaratively, so there is no installer script to download. The fork
ships a flake template that recreates the whole showcase shell — fish as the login shell, the
oh-my-posh Material prompt, `eza`/`zoxide`/`yazi`, Neovim, and fastfetch with an animated logo:

```sh
setup-launcher
```

It ships in the base environment, so it is there at the first prompt: it installs the template
and runs the switch, restoring your files if anything fails.

`setup-toolkits` then picks what is installed — shell, eye candy, editor, build tools, node, go,
python — by flipping booleans in `~/.config/nix-on-droid/toolkits.nix` and switching for you.
Editing that file by hand does the same thing. See
[Nix package management](docs/en/Nix_Package_Management.md).

Fonts are separate either way — the in-app font picker (**Settings › Terminal › Font**) downloads
and wires up curated families, Nerd Font builds included.

## Documentation

**User documentation lives on the [website](https://picklehik3.github.io/termux-launcher-site/)** — getting started, the launcher tour, terminal and multiplexer guides, fonts, keybindings, extra keys, notifications, backups, and the Nix edition pages, kept current with each release.

In-repo references:

- [Local AI API](docs/en/LauncherCtl_API.md): OpenAI/Ollama-compatible localhost endpoint, app launch, model management, auth, and route tables.
- [Termux AI](docs/en/Termux_AI.md): local model setup, `tai`, OpenAI-compatible clients, and troubleshooting.
- [Building showcase tools](docs/en/Building_Terminal_Showcase_Tools.md): reproducible recipes for Sigye and animated-Kitty Fastfetch, on device and cross-built.
- [VAJ to Nix migration](docs/en/VAJ_To_Nix_Migration.md): moving off the deprecated VAJ edition.
- [Developer Docs](docs/en/Developer_Docs.md): advanced API routes, runtime notes, helper scripts, and security details.

## Upstream Base

- [termux-app](https://github.com/termux/termux-app)
- [termux-monet](https://github.com/Termux-Monet/termux-monet)
- [TEL](https://github.com/t-e-l/tel)

## License and Attributions

Termux Launcher is a modified Termux/Termux:Monet distribution, developed from 2026 onward and
released under GPLv3-only. It is an independent fork with no affiliation to the official Termux
project. See [LICENSE](LICENSE), [license exceptions](LICENSE-EXCEPTIONS.md), and
[open-source notices](THIRD_PARTY_NOTICES.md). The Android app exposes the same notices under
**Settings > Open-source licenses**.

Bundled assets carry their own licenses: the weather animations are
[Meteocons](https://github.com/basmilius/meteocons) (MIT, Copyright 2020-present Bas Milius), and
the icon font is [Symbols Nerd Font Mono](https://github.com/ryanoasis/nerd-fonts) (SIL OFL 1.1).
