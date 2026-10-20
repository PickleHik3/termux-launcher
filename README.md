<p align="center">
  <img src="screenshots/banner.webp" alt="Termux Launcher showing seven authentic Android screens: terminal home, split panes, widgets, voice dictation, Dawn notes, tlstore, and the app drawer" width="100%">
</p>

<p align="center">
  <a href="https://picklehik3.github.io/termux-launcher-site/"><b>🌐 Website &amp; docs</b></a> ·
  <a href="https://discord.gg/sWSCRh7aVR"><b>💬 Discord</b></a> ·
  <a href="https://github.com/PickleHik3/termux-launcher/releases">Releases</a>
</p>

> [!NOTE]
> This project is entirely vibe-coded. I daily-drive it, but if you have Android or
> app-security experience, a review would be very welcome. Please open an issue or discussion.

> [!IMPORTANT]
> **An independent fork, not affiliated with [Termux](https://github.com/termux/termux-app).**
> Report issues [here](https://github.com/PickleHik3/termux-launcher/issues), not to Termux.
> The `com.termux` edition **replaces** official Termux on your device; the other editions install side by side.

## About

Termux Launcher is a terminal-first Android home launcher inspired by [TEL](https://github.com/t-e-l/tel), built on [termux-app](https://github.com/termux/termux-app) and [termux-monet](https://github.com/Termux-Monet/termux-monet).
What started out as me just wanting sixel image drawing in [TEL](https://github.com/t-e-l/tel) spiraled out of scope to what this project is today.
All credits go to the amazing developers and contributors of Termux, TEL, and Termux:Monet.

## Features

Where a feature was ported from or inspired by another project, that project is named next to it. The [notices](THIRD_PARTY_NOTICES.md) list exactly what is included and adapted.

**The home screen**

- Termux as the actual Android home launcher, the idea borrowed from [TEL](https://github.com/t-e-l/tel): the terminal is the home screen, with an app dock, a full app drawer (vertical, horizontal and category layouts, folders), Android widget pages and a Linux display beside it
- An expandable status bar with sessions and window chips, live CPU, RAM and weather (animated [Meteocons](https://github.com/basmilius/meteocons))
- Notification dots, pinned notification cards and a notification history for the shell
- Looks and themes: Material You colours from your wallpaper (from [termux-monet](https://github.com/Termux-Monet/termux-monet)), glass surfaces with blur, edge light and corner radius you can tune per surface, icon packs, cursor trails, and theme templates (after [Noctalia](https://github.com/noctalia-dev/noctalia-shell)) so shell tools follow the wallpaper too
- Backup and restore of your settings, config files and downloaded AI models

**The terminal**

- Kitty graphics (`timg -pk foo.jpg`, animations included) and Sixel images in the terminal
- The [kitty](https://github.com/kovidgoyal/kitty) protocols, adapted from kitty's code: the kitty keyboard protocol, text sizing (OSC 66), hyperlinks (OSC 8), notifications and progress (OSC 99, OSC 9;4), clipboard read and write from programs (OSC 52 and the extended OSC 5522, pictures included), colour queries and multiple cursors; `kitten icat` and `kitten clipboard` work (kitty's remote control does not, there is no kitty to talk to)
- Fonts the kitty way: an in-app font picker installs Nerd Fonts and writes a kitty-style `fonts.conf`, with fallback fonts, ligatures, variable axes and clean box drawing
- Native sessions, windows, recursive split and floating panes, layouts and workspace restore (**Ctrl + Alt** is the default keybind); tiling after [Hyprland](https://github.com/hyprwm/Hyprland), the focused pane after [focus.nvim](https://github.com/nvim-focus/focus.nvim), terminal padding after [Ghostty](https://github.com/ghostty-org/ghostty)
- Touch that reaches desktop TUIs. Drags scroll mouse-aware programs and taps click, as in Termux; the difference is a short press-and-hold that hands the finger to the program as a held mouse button, so programs written for a desktop mouse work by touch: select in vim, click around lazygit, drag a pane edge. Mouse mode turns the whole screen into a mouse for programs that take one
- A searchable command palette for actions, apps and contacts, a tmux-style leader key, modal keymaps and on-screen key hints
- Quick select of URLs, paths and hashes on screen; scrollback search and vim-style copy mode (work in progress)
- `launcherctl`: launch apps, open and arrange panes and windows, notify, set the wallpaper and more from the shell, for you or for an agent running in a pane
- Touch reports for programs that draw their own page (private mode 5523), which is how terminal-browser gets fling, pinch and long press
- An in-app X11 display, built on [Termux:X11](https://github.com/termux/termux-x11): install graphical apps from Termux's `x11-repo` (`pkg install firefox`) and they appear in the app drawer with their own icons; a tap starts the display and opens the app full size. Apps installed in a proot distro are listed the same way, but that path has had less testing

**Typing**

- A built-in terminal keyboard on fresh installs: a trimmed [Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) by Jules Aguillon embedded in the app, docked, floating or split, with an extra-keys row and a Fn layer; the Android keyboard and no keyboard are a switch away
- Edit, reset and create keyboard layouts in Settings and cycle through them from the space bar
- The keyboard's paste key can paste the screenshot you just took on Android: only the latest fresh screenshot is offered, it lands as a file path under the terminal's tmp folder, and a plain paste still gives you the clipboard text

**On-device AI**

The models run in the launcher's own runtime, on Google's [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) and Alibaba's [MNN](https://github.com/alibaba/MNN), and a Model centre picks a size for your phone. Nothing in the launcher needs a model; these are the features that use one:

- Voice dictation and cleanup: a [Whisper](https://github.com/futo-org/whisper-acft) or [Parakeet](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3) model turns speech into text on the phone, with [Silero VAD](https://github.com/snakers4/silero-vad) listening for pauses; a Gemma model cleans up fillers and misheard words while you pause, and the dictation card lets you fix a word before inserting it. The audio never leaves the phone
- Read aloud: text to speech from the terminal and `tai speak`, with the [KittenTTS](https://huggingface.co/litert-community/kitten-tts-nano-0.8) voice
- Local OpenAI- and Ollama-compatible endpoints on localhost, plus the `tai` command, so aichat and other clients can talk to the phone's own models
- The app drawer sorts itself into categories
- Dawn notes integration: automatic titles, an assistant that can edit the note, read aloud and dictation, and note search by meaning indexed with EmbeddingGemma 2 (dawn is work in progress)

**Extras**

- `tlstore`, a small package store made for Termux Launcher. It ships companion apps that only work here, and a few things that are just easier from it:
  - **dawn** (a fork of [andrewmd5/dawn](https://github.com/andrewmd5/dawn)): a writing pad for the terminal, wired to the launcher's AI, voice and wallpaper colours
  - **fastfetch** with **animated GIF logos** drawn through kitty graphics
  - **btop** that sees the whole phone, run as the shell user through Shizuku
  - **terminal-browser** (a fork of [zenbu-labs/terminal-browser](https://github.com/zenbu-labs/terminal-browser)): a real browser in a pane beside your shell, with touch, and an agent CLI (`terminal-browser action`) that lets a coding agent open pages, read them and click through them
  - The **termux-launcher agent skill**: Claude Code, Codex, OpenCode or pi learn to run their work in a pane or window beside you, split and arrange panes, notify you and show progress, show images, open terminal-browser, answer questions about the launcher, and read their answers aloud with the on-device voice
  - **Claude Code**, **opencode v2** and **Codex** made to run on Android (the first two through a patched musl loader, Codex as the [codex-termux](https://github.com/DioNanos/codex-termux) port by DioNanos), and [herdr](https://github.com/herdrdev/herdr), a workspace that keeps agents running after you close the terminal
  - A fish shell setup with the store's own prompt (Pure's layout, Nerd Font icons, your wallpaper's colours), eza and zoxide; [sigye](https://github.com/am2rican5/sigye), kitten, and the Termux:API commands without the Termux:API app
- Optional Shizuku integration: screen lock, CPU usage in the status bar, and a privileged lane for tools such as btop

## Editions

> **Three editions are available.** The **`com.termux`** build is the **recommended** version: it stays fully compatible with the upstream Termux package ecosystem. If you want to install it alongside your existing Termux install, choose either **`com.termux.launcher.nix`** or **`io.vaj.tl`**.

Every release ships the same launcher built from the same source; the editions differ only in Android package identity and the package ecosystem they use.

| | Termux edition | Nix edition | VAJ demo edition |
|---|---|---|---|
| Package name | `com.termux` | `com.termux.launcher.nix` | `io.vaj.tl` |
| Release tag | `vX.Y.Z` | `nix-vX.Y.Z` | `vaj-vX.Y.Z` |
| Obtainium | [<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="44">][obt-termux]<br>[pre-release][obt-termux-pre] | [<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="44">][obt-nix]<br>[pre-release][obt-nix-pre] | [<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="44">][obt-vaj]<br>[pre-release][obt-vaj-pre] |
| Alongside official Termux? | ❌ No: same package name, replaces it | ✅ Yes, installs side by side | ✅ Yes, installs side by side |
| Package manager | `pkg` / APT | [Nix](https://nixos.org) ([guide](docs/en/Nix_Package_Management.md)) | `pkg` / APT |
| Package repository | official Termux repos | official `nixpkgs` binary cache | small VAJ APT repo (`https://repo.pathayam.xyz`) |
| Architectures | arm64-v8a, armeabi-v7a, x86_64, x86 | arm64-v8a, x86_64; bootstrap downloaded on first run | arm64-v8a (aarch64) only; bootstrap downloaded on first run |
| Companion add-ons | [Termux:API](https://github.com/PickleHik3/termux-api/releases/tag/v0.53.2) / [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases/tag/v0.32.3) / [Termux:Boot](https://github.com/PickleHik3/termux-boot/releases/tag/v0.8.2) (plain tags) | [TLNix:API](https://github.com/PickleHik3/termux-api/releases/tag/nix-v0.53.2) / [TLNix:Styling](https://github.com/PickleHik3/termux-styling/releases/tag/nix-v0.32.3) / [TLNix:Boot](https://github.com/PickleHik3/termux-boot/releases/tag/nix-v0.8.2) (`nix-v*` tags) | [Termux:API](https://github.com/PickleHik3/termux-api/releases/tag/v0.53.2-vaj) / [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases/tag/v0.32.3-vaj) / [Termux:Boot](https://github.com/PickleHik3/termux-boot/releases/tag/v0.8.2-vaj) (`-vaj` tags) |

> ⚠️ **The VAJ edition is a demo.** It runs off my manually maintained APT repo (`https://repo.pathayam.xyz`), which carries only a small fraction of the Termux package set, and keeping its crucial packages updated is hard for me, so `pkg` updates may or may not come. If you want the launcher next to official Termux, use the Nix edition; the **[VAJ to Nix migration guide](docs/en/VAJ_To_Nix_Migration.md)** may help. For the curious, this is a [pathayam](https://commons.wikimedia.org/wiki/File:Exhibits_at_Koyikkal_Palace_Museum_Nedumangad_162.jpg); the odd URL is my homelab domain, which was never supposed to see the light of day :(

Companion add-ons must be the matching builds from this project's forks (they share the launcher's signing key and package family); official F-Droid add-ons will not pair with any edition.

## Installation

Download the latest APK of your chosen [edition](#editions) from [Releases](https://github.com/PickleHik3/termux-launcher/releases), install it, then select Termux Launcher as your Android home app. [Get started](docs/en/Get_Started.md) walks through the first launch; the **com.termux** edition installs over official Termux and keeps your files, but it is a one-way move.

### Stable or pre-release

There are two channels. **Stable** releases (`vX.Y.Z`, one build per edition) are what most people should run. The **pre-release** channel (`vX.Y.Z-pre.N`, with `nix-` and `vaj-` builds alongside, marked *Pre-release* on the releases page) is a rolling build of what is heading for the next stable plus experiments that may still change or be dropped; each drop's notes list everything it has beyond the latest stable. Both channels are signed with the same key and share a version code, so you can install one over the other in either direction and keep your data. When reporting an issue from a pre-release, include the version from Settings › About; it ends in `-pre.N`.

### Updates with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) can install the launcher and keep it updated from this repository. Every edition and both channels are published here, so add it with the link for your edition and channel: the link sets Obtainium to fetch only that edition's APK.

| Edition | Stable | Pre-release |
|---|---|---|
| Termux (`com.termux`) | [Add to Obtainium][obt-termux] | [Add to Obtainium][obt-termux-pre] |
| Nix (`com.termux.launcher.nix`) | [Add to Obtainium][obt-nix] | [Add to Obtainium][obt-nix-pre] |
| VAJ (`io.vaj.tl`) | [Add to Obtainium][obt-vaj] | [Add to Obtainium][obt-vaj-pre] |

To change channel later, open the app in Obtainium and switch **Include prereleases**. Leave **Verify the 'latest' tag** off, and keep **Hide downgrades as updates** (Obtainium's own settings) on, so a pre-release install is never offered an older stable.

Adding the repository URL by hand works too: set **Filter APKs by regular expression** to `_termux_`, `_nix_` or `_vaj_` for your edition, and turn on **Include prereleases** for the pre-release channel.

### Recommended setup

- [Shizuku](https://github.com/rikkaapps/shizuku), only if you want the optional privileged features
- Matching companion forks when using Termux add-ons (pick the release matching your [edition](#editions): plain tag for `com.termux`, `nix-v*` tag for `com.termux.launcher.nix`, `-vaj` tag for `io.vaj.tl`):
  - [Termux:API](https://github.com/PickleHik3/termux-api/releases)
  - [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases)
  - [Termux:Boot](https://github.com/PickleHik3/termux-boot/releases), which runs the scripts in `~/.termux/boot/` after a restart
- A Nerd Font from the in-app font picker (**Settings › Terminal › Font**); it configures the font properly for the kitty protocol features
- A speech model from **Settings › Keyboard › Voice input** if you want on-device dictation; see [Voice input](docs/en/Voice_Input.md)

The built-in terminal keyboard is enabled on fresh installs; an external keyboard app is optional.

### Quick start script

`tlstore`, the launcher's own tool store, is already on your phone. The first line installs the fish shell with the store's own prompt (Pure's two-line layout with Nerd Font icons, in your wallpaper's colours), eza, zoxide and two fish plugins; the second opens a picker for everything else in the store (dawn, fastfetch, btop, terminal-browser, herdr, the agent skills, Claude Code, Codex, opencode, sigye, kitten). A config file of yours is never replaced without showing you the change and asking first, and whatever it does replace gets a timestamped `.bak`. The wallpaper-matching Neovim colour scheme is not a store item: it lives under **Settings › Appearance › Tools that follow the terminal colours**.

```sh
tlstore shell
tlstore install
```

See the [tlstore guide](docs/en/Tlstore.md) for the full command list.

## Documentation

**User documentation lives on the [website](https://picklehik3.github.io/termux-launcher-site/)**

In-repo references:

- [Get started](docs/en/Get_Started.md) and [Learn the launcher](docs/en/Learn_The_Launcher.md): install, the first launch, and the three places on screen.
- [Voice input](docs/en/Voice_Input.md) and [Text to speech](docs/en/Text_To_Speech.md): on-device dictation, cleanup, read aloud, `tai speak`.
- [Programs and agents inside the terminal](docs/en/Programs_Inside_The_Terminal.md): **read this before assuming stock Termux behaviour** from a script or an AI agent running in a pane: the extra environment variables, the escape sequences the terminal accepts, `launcherctl`, and the one `am start` that must never be run.
- [Local AI API](docs/en/LauncherCtl_API.md): OpenAI/Ollama-compatible localhost endpoint, app launch, model management, auth, and route tables.
- [On-device AI](docs/en/On_Device_AI.md): local model setup, `tai`, OpenAI-compatible clients, and troubleshooting.
- [Building showcase tools](docs/en/Building_Terminal_Showcase_Tools.md): reproducible recipes for Sigye and animated-Kitty Fastfetch, on device and cross-built.
- [VAJ to Nix migration](docs/en/VAJ_To_Nix_Migration.md): moving off the deprecated VAJ edition.
- [Developer Docs](docs/en/Developer_Docs.md): advanced API routes, runtime notes, helper scripts, and security details.
- [Glossary](docs/GLOSSARY.md): the words the code, the docs and the developer use.
- [Developer specs and research](project-docs/README.md): active specs, delivered records, research and the backlog.

## Getting help

Questions, setups and ideas all go to the [Discord server](https://discord.gg/sWSCRh7aVR). TildeBot, the server's help bot, answers in `#help` first and points you to the right part of the docs, and a human steps in when it can't sort you out. Bugs you can reproduce can also go straight to [GitHub issues](https://github.com/PickleHik3/termux-launcher/issues).

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
[Meteocons](https://github.com/basmilius/meteocons) (MIT, Copyright 2020-present Bas Milius), the
voice key's speech detector is [Silero VAD](https://github.com/snakers4/silero-vad) (MIT, Copyright
2020-present Silero Team), and the icon font is [Symbols Nerd Font Mono](https://github.com/ryanoasis/nerd-fonts) (MIT for the Symbols-only font, with separate licenses for its glyph sources).

### Thanks to the projects behind the launcher

Alongside Termux, Termux:Monet and TEL, a huge thank you to
[Termux:X11](https://github.com/termux/termux-x11) for the Linux display,
[Unexpected Keyboard](https://github.com/Julow/Unexpected-Keyboard) for the embedded keyboard,
[kitty](https://github.com/kovidgoyal/kitty) for its terminal protocols and the code adapted here,
[Noctalia](https://github.com/noctalia-dev/noctalia-shell) for the theme templates, and
[herdr](https://github.com/herdrdev/herdr) for the agent-detection rules.

[Ghostty](https://github.com/ghostty-org/ghostty), [Hyprland](https://github.com/hyprwm/Hyprland)
and [focus.nvim](https://github.com/nvim-focus/focus.nvim) inspired the terminal padding, pane
tiling and focused-pane behaviour. Thanks also to the speech-model authors, Google AI Edge and
the LiteRT conversion contributors, and the authors of dawn, fastfetch, sigye and the other
[tlstore tools](https://github.com/PickleHik3/tlstore). This launcher would not exist without
all that work. The [notices](THIRD_PARTY_NOTICES.md) describe what is included and adapted.

[obt-termux]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.termux%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Ev%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_termux_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-termux-pre]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.termux%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20pre-release%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Ev%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_termux_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-nix]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.termux.launcher.nix%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20%28Nix%29%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Enix-v%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_nix_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-nix-pre]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.termux.launcher.nix%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20%28Nix%29%20pre-release%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Enix-v%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_nix_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-vaj]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.vaj.tl%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20%28VAJ%29%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Evaj-v%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_vaj_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-vaj-pre]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.vaj.tl%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20%28VAJ%29%20pre-release%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Evaj-v%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_vaj_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
