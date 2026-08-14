# Termux Launcher

> [!WARNING]
> **This project is entirely vibe-coded.**
> I’ve been daily-driving it as a launcher on a Nothing Phone (2), and it has been rock-solid so far. It also does not appear to have any noticeable impact on battery life.

> [!CAUTION]
> The native AI backends—**Google LiteRT** and **Alibaba MNN**—are highly experimental. Be mindful of your device’s available RAM and processor capabilities when selecting models.

> [!NOTE]
> ~~If the terminal slows down, run `termux-reload-settings`.~~
> All hail Fable for exorcising this daemon-basically yeeted it in seconds, on God.


Termux Launcher is a terminal-first Android home launcher inspired by [TEL](https://github.com/t-e-l/tel), built on [termux-app](https://github.com/termux/termux-app) and [termux-monet](https://github.com/Termux-Monet/termux-monet).

**[🌐 Website & docs](https://picklehik3.github.io/termux-launcher-site/)** | [Download builds](https://github.com/PickleHik3/termux-launcher/releases) | [Getting Started](docs/en/Launcher_Getting_Started.md) | [Modern terminal](docs/en/Terminal_Modernization.md) | [Local AI API](docs/en/LauncherCtl_API.md) | [Termux AI](docs/en/Termux_AI.md) | [Changelog](CHANGELOG.md)

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
- Searchable terminal command palette with customizable chords and modal keymaps
- App dock with terminal app search
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
| Architectures | arm64-v8a, armeabi-v7a, x86_64, x86 | arm64-v8a (aarch64), bootstrap downloaded on first run | arm64-v8a (aarch64) only, bootstrap downloaded on first run |
| Companion add-ons | [Termux:API](https://github.com/PickleHik3/termux-api/releases) / [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases) (plain tags) | [TLNix:API](https://github.com/PickleHik3/termux-api/releases/tag/nix-v0.53.1) / [TLNix:Styling](https://github.com/PickleHik3/termux-styling/releases/tag/nix-v0.32.2) (`nix-v*` tags) | same forks, `-vaj` tagged releases |

Pick the **Termux edition** for the classic Termux experience — the launcher as your Termux, fully compatible with the upstream Termux package ecosystem. Pick the **Nix edition** if you want the entire `nixpkgs` collection, declarative configs, and rollbacks next to an existing Termux install — it is built on a [Nix-on-Droid](https://github.com/nix-community/nix-on-droid)-style environment; new to Nix? start with the [beginner's guide](docs/en/Nix_Getting_Started.md), then the [package management reference](docs/en/Nix_Package_Management.md). The **VAJ edition is a legacy demo**: it predates the Nix edition as the side-by-side option and is kept only for preview installs.

> ⚠️ The VAJ demo edition runs off my manually maintained custom APT repo (`https://repo.pathayam.xyz`), which carries only a small fraction of the Termux package set and is updated **less frequently** — many packages you rely on will simply not be installable there. If you want a side-by-side install, prefer the Nix edition.

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

### Quick start script

[`setup-launcher`](docs/en/examples/setup-launcher) turns a fresh install into the full showcase shell in one run — fish + Oh My Posh with wallpaper Material colors, zoxide/eza/yazi/neovim, and the launcher terminal configs in `~/.termux`. It is interactive, and every config it replaces gets a timestamped `.bak` first:

```sh
curl -fsSLO https://raw.githubusercontent.com/PickleHik3/termux-launcher/main/docs/en/examples/setup-launcher
sh setup-launcher
```

Fonts are not part of the script — the in-app font picker (**Settings › Terminal › Font**) downloads and wires up curated families, Nerd Font builds included.

## Documentation

- [What’s new in v0.2.31](docs/en/Whats_New_0.2.31.md): onboarding, landscape, workspace command restart, per-pane zoom, fonts, key binding, attention states, and compatibility.
- [Getting Started](docs/en/Launcher_Getting_Started.md): choose an edition, install, complete first-run setup, and make the app your Home screen.
- [Using Termux Launcher](docs/en/Launcher_Usage.md): dock search, status widgets, sessions, windows, panes, workspaces, and the built-in keyboard.
- [Settings map](docs/en/Launcher_Settings.md): exact v0.2.31 settings destinations and what each controls.
- [Troubleshooting](docs/en/Launcher_Troubleshooting.md): installation, storage, input, panes, workspaces, appearance, permissions, Shizuku, and TAI.
- [Modern terminal guide](docs/en/Terminal_Modernization.md): panes, windows, sessions, layouts, workspaces, bindings, fonts, protocols, and diagnostics.
- [Terminal fonts](docs/en/Terminal_Fonts.md): picker, config priority, drop-ins, fallback, symbols, ligatures, geometry, and troubleshooting.
- [Kitty protocols](docs/en/Terminal_Kitty_Protocols.md): keyboard negotiation, multiple cursors, graphics, animation, Sixel, and terminal detection.
- [Nix beginner's guide](docs/en/Nix_Getting_Started.md): the Nix edition's first hour — first launch, the shell template, installing packages, undoing changes, and the vocabulary.
- [Nix package management](docs/en/Nix_Package_Management.md): the Nix edition's package manager — everyday commands, declarative setup, rollbacks, and caveats.
- [Building showcase tools](docs/en/Building_Terminal_Showcase_Tools.md): reproducible Termux recipes for Sigye and animated-Kitty Fastfetch.
- [Local AI API](docs/en/LauncherCtl_API.md): OpenAI/Ollama-compatible localhost endpoint, app launch, model management, auth, and route tables.
- [Termux AI](docs/en/Termux_AI.md): local model setup, `tai`, OpenAI-compatible clients, and troubleshooting.
- [Developer Docs](docs/en/Developer_Docs.md): advanced API routes, runtime notes, helper scripts, and security details.

## Upstream Base

- [termux-app](https://github.com/termux/termux-app)
- [termux-monet](https://github.com/Termux-Monet/termux-monet)
- [TEL](https://github.com/t-e-l/tel)

## License and Attributions

Termux Launcher is a modified Termux/Termux:Monet distribution, developed from 2026 onward and
released under GPLv3-only. See [LICENSE](LICENSE), [license exceptions](LICENSE-EXCEPTIONS.md), and
[open-source notices](THIRD_PARTY_NOTICES.md). The Android app exposes the same notices under
**Settings > Open-source licenses**.
