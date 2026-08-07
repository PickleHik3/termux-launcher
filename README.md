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

> **Two editions are available.** The **`com.termux`** build is the **recommended** version — it stays fully compatible with the upstream Termux package ecosystem. The **`io.vaj.tl`** build installs side-by-side with a stock Termux, but it runs off my own custom APT repository, which I maintain by hand — so it is updated manually and less often. See [Editions](#editions).

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

Every release ships two APK sets. They are the same launcher built from the same source; the only difference is the Android package identity and the package ecosystem they use.

| | Termux edition | VAJ edition |
|---|---|---|
| Package name | `com.termux` | `io.vaj.tl` |
| Release tag | `vX.Y.Z` | `vX.Y.Z-vaj` |
| Alongside official Termux? | ❌ No — same package name, replaces it | ✅ Yes — installs side by side |
| Package repository | official Termux repos | VAJ APT repo (`https://repo.pathayam.xyz`) |
| Architectures | arm64-v8a, armeabi-v7a, x86_64, x86 | arm64-v8a (aarch64) only, bootstrap embedded |
| Companion add-ons | [Termux:API](https://github.com/PickleHik3/termux-api/releases) / [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases) (plain tags) | same forks, `-vaj` tagged releases |

Pick the **Termux edition** if you want the launcher as your only Termux, fully compatible with the upstream Termux package ecosystem. This is the **recommended** edition for most users. Pick the **VAJ edition** if you want to keep your existing official Termux app untouched and run the launcher next to it with its own isolated prefix, data, and APT repository.

> ⚠️ The VAJ edition depends on my manually maintained custom APT repo (`https://repo.pathayam.xyz`), so its packages and `-vaj` releases are updated **less frequently** than the `com.termux` edition. If you want the most up-to-date builds and the broadest package compatibility, use the Termux (`com.termux`) edition.

In both cases, companion add-ons must be the matching builds from this project's forks (they share the launcher's signing key and package family); official F-Droid add-ons will not pair with either edition.

## Installation

Download the latest APK of your chosen [edition](#editions) from [Releases](https://github.com/PickleHik3/termux-launcher/releases), install it, then select Termux Launcher as your Android home app.

Recommended setup:

- [Shizuku](https://github.com/rikkaapps/shizuku) only if you want optional privileged features
- Matching companion forks when using Termux add-ons (pick the release matching your [edition](#editions): plain tag for `com.termux`, `-vaj` tag for `io.vaj.tl`):
  - [Termux:API](https://github.com/PickleHik3/termux-api/releases)
  - [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases)

The built-in terminal keyboard is enabled on fresh installs; an external keyboard app is optional.
See [Getting Started](docs/en/Launcher_Getting_Started.md) for the setup flow.

## Documentation

- [What’s new in v0.2.31](docs/en/Whats_New_0.2.31.md): onboarding, landscape, workspace command restart, per-pane zoom, fonts, key binding, attention states, and compatibility.
- [Getting Started](docs/en/Launcher_Getting_Started.md): choose an edition, install, complete first-run setup, and make the app your Home screen.
- [Using Termux Launcher](docs/en/Launcher_Usage.md): dock search, status widgets, sessions, windows, panes, workspaces, and the built-in keyboard.
- [Settings map](docs/en/Launcher_Settings.md): exact v0.2.31 settings destinations and what each controls.
- [Troubleshooting](docs/en/Launcher_Troubleshooting.md): installation, storage, input, panes, workspaces, appearance, permissions, Shizuku, and TAI.
- [Modern terminal guide](docs/en/Terminal_Modernization.md): panes, windows, sessions, layouts, workspaces, bindings, fonts, protocols, and diagnostics.
- [Terminal fonts](docs/en/Terminal_Fonts.md): picker, config priority, drop-ins, fallback, symbols, ligatures, geometry, and troubleshooting.
- [Kitty protocols](docs/en/Terminal_Kitty_Protocols.md): keyboard negotiation, multiple cursors, graphics, animation, Sixel, and terminal detection.
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
