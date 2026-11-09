<p align="center">
  <img src="screenshots/hero.webp" alt="Termux Launcher: an Android home screen with a real Linux terminal. Three phones show the Home page with widgets, a floating glass terminal, and the AI assistant page. Callouts: Android home with widgets; floating glass terminals; live glass on glass; AI assistant page; split panes and Kitty images; on-device voice." width="100%">
</p>

<p align="center">
  <a href="https://picklehik3.github.io/termux-launcher-site/"><b>🌐 Website &amp; docs</b></a> ·
  <a href="https://discord.gg/sWSCRh7aVR"><b>💬 Discord</b></a> ·
  <a href="https://github.com/PickleHik3/termux-launcher/releases">Releases</a>
</p>

<p align="center">An Android home launcher built on <a href="https://github.com/termux/termux-app">Termux</a>: widgets and apps on Home, real terminals beside them and floating over them.<br>
<a href="docs/en/Features.md"><b>Full feature list →</b></a></p>

> [!IMPORTANT]
> **An independent fork, not affiliated with [Termux](https://github.com/termux/termux-app)**; report issues [here](https://github.com/PickleHik3/termux-launcher/issues).
> The `com.termux` edition **replaces** official Termux, and there is no way back without a wipe ([details](#install)).

## Install

Pick an edition. All three are the same launcher; they differ in package name and package manager.

| | **Termux** (recommended) | **Nix** | **VAJ** (demo) |
|---|---|---|---|
| Package name | `com.termux` | `com.termux.launcher.nix` | `io.vaj.tl` |
| Next to official Termux? | ❌ Replaces it | ✅ Side by side | ✅ Side by side |
| Packages | `pkg`, official Termux repos | [Nix](https://nixos.org) ([guide](docs/en/Nix_Package_Management.md)) | `pkg`, a small demo repo |
| Get it | [<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="40">][obt-termux]<br>[pre-release][obt-termux-pre] · [APK](https://github.com/PickleHik3/termux-launcher/releases) | [<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="40">][obt-nix]<br>[pre-release][obt-nix-pre] · [APK](https://github.com/PickleHik3/termux-launcher/releases) | [<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="40">][obt-vaj]<br>[pre-release][obt-vaj-pre] · [APK](https://github.com/PickleHik3/termux-launcher/releases) |

> [!WARNING]
> **Installing `com.termux` over official Termux is one-way.** It keeps your files, but its versionCode (1020) is far above official Termux's, so Android refuses official Termux afterwards as a downgrade. Going back means uninstalling the launcher, which **deletes your Termux home and packages**. Copy `~` to shared storage first (`termux-setup-storage`).

1. Install the APK for your edition (Obtainium badge above, or from [Releases](https://github.com/PickleHik3/termux-launcher/releases)).
2. Open it and follow [Get started](docs/en/Get_Started.md).
3. Set Termux Launcher as your Android home app.

<details>
<summary><b>Architectures and companion add-ons</b></summary>

| | Termux | Nix | VAJ |
|---|---|---|---|
| Release tag | `vX.Y.Z` | `nix-vX.Y.Z` | `vaj-vX.Y.Z` |
| Architectures | arm64-v8a, armeabi-v7a, x86_64, x86 | arm64-v8a, x86_64; bootstrap downloaded on first run | arm64-v8a only; bootstrap downloaded on first run |
| Add-ons | [Termux:API](https://github.com/PickleHik3/termux-api/releases/tag/v0.53.2) / [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases/tag/v0.32.3) / [Termux:Boot](https://github.com/PickleHik3/termux-boot/releases/tag/v0.8.2) | [TLNix:API](https://github.com/PickleHik3/termux-api/releases/tag/nix-v0.53.2) / [TLNix:Styling](https://github.com/PickleHik3/termux-styling/releases/tag/nix-v0.32.3) / [TLNix:Boot](https://github.com/PickleHik3/termux-boot/releases/tag/nix-v0.8.2) | [Termux:API](https://github.com/PickleHik3/termux-api/releases/tag/v0.53.2-vaj) / [Termux:Styling](https://github.com/PickleHik3/termux-styling/releases/tag/v0.32.3-vaj) / [Termux:Boot](https://github.com/PickleHik3/termux-boot/releases/tag/v0.8.2-vaj) |

Add-ons must be the matching builds from this project's forks (they share the launcher's signing key); official F-Droid add-ons will not pair with any edition. Termux:Boot runs the scripts in `~/.termux/boot/` after a restart.

**The VAJ edition is a demo.** It runs off a small, hand-maintained APT repo (`https://repo.pathayam.xyz`) with a fraction of the Termux packages, and updates may not come. For the launcher next to official Termux, use Nix; see the [VAJ to Nix migration guide](docs/en/VAJ_To_Nix_Migration.md).
</details>

<details>
<summary><b>Stable or pre-release</b></summary>

**Stable** (`vX.Y.Z`) is what most people should run. **Pre-release** (`vX.Y.Z-pre.N`, marked *Pre-release* on the releases page) is a rolling build of the next stable plus experiments that may change or be dropped; each drop's notes list what it adds. Both channels share a signing key and version code, so you can switch either way and keep your data. When reporting an issue from a pre-release, include the version from **Settings › About** (it ends in `-pre.N`).
</details>

<details>
<summary><b>Obtainium by hand</b></summary>

The badges above set Obtainium to fetch only that edition's APK. To add the repository URL by hand instead, set **Filter APKs by regular expression** to `_termux_`, `_nix_` or `_vaj_`, and turn on **Include prereleases** for the pre-release channel. Leave **Verify the 'latest' tag** off, and keep Obtainium's **Hide downgrades as updates** on so a pre-release install is never offered an older stable. To change channel later, switch **Include prereleases** on the app in Obtainium.
</details>

## After install

```sh
tlstore shell     # fish with the store's prompt, eza, zoxide
tlstore install   # pick more: dawn, fastfetch, btop, terminal-browser, Claude Code, Codex, ...
```

- Pick a Nerd Font in **Settings › Terminal › Font**; the kitty features need it.
- [Shizuku](https://github.com/rikkaapps/shizuku) is optional; it unlocks screen lock, CPU stats and the privileged tools.
- For dictation, get a speech model in **Settings › Keyboard › Voice input** ([guide](docs/en/Voice_Input.md)).

`tlstore` never replaces a config file without showing you the change first, and keeps a `.bak`. See the [tlstore guide](docs/en/Tlstore.md).

## Docs and help

User docs are on the **[website](https://picklehik3.github.io/termux-launcher-site/)**. Questions go to [Discord](https://discord.gg/sWSCRh7aVR) (TildeBot answers in `#help` first, then a human); bugs you can reproduce go to [GitHub issues](https://github.com/PickleHik3/termux-launcher/issues).

> [!NOTE]
> Running scripts or an AI agent in a pane? Read [Programs and agents inside the terminal](docs/en/Programs_Inside_The_Terminal.md) before assuming stock Termux behaviour.

<details>
<summary><b>Docs in this repository</b></summary>

- [Features](docs/en/Features.md): everything the launcher does.
- [Get started](docs/en/Get_Started.md) and [Learn the launcher](docs/en/Learn_The_Launcher.md): install, the first launch, and the places on screen.
- [Voice input](docs/en/Voice_Input.md) and [Text to speech](docs/en/Text_To_Speech.md): on-device dictation, cleanup, read aloud, `tai speak`.
- [Programs and agents inside the terminal](docs/en/Programs_Inside_The_Terminal.md): extra environment variables, accepted escape sequences, `launcherctl`, and the one `am start` that must never be run.
- [Local AI API](docs/en/LauncherCtl_API.md): OpenAI/Ollama-compatible localhost endpoint, app launch, model management, auth, and route tables.
- [On-device AI](docs/en/On_Device_AI.md): local model setup, `tai`, OpenAI-compatible clients, and troubleshooting.
- [Building showcase tools](docs/en/Building_Terminal_Showcase_Tools.md): recipes for Sigye and animated-Kitty Fastfetch.
- [VAJ to Nix migration](docs/en/VAJ_To_Nix_Migration.md): moving off the VAJ edition.
- [Developer Docs](docs/en/Developer_Docs.md): advanced API routes, runtime notes, helper scripts, and security details.
- [Glossary](docs/GLOSSARY.md): the words the code, the docs and the developer use.
- [Developer specs and research](project-docs/README.md): active specs, delivered records, research and the backlog.
</details>

## License and credits

Built on [termux-app](https://github.com/termux/termux-app), [termux-monet](https://github.com/Termux-Monet/termux-monet) and the idea of [TEL](https://github.com/t-e-l/tel). A modified Termux/Termux:Monet distribution under GPLv3-only, with no affiliation to the official Termux project. See [LICENSE](LICENSE), [license exceptions](LICENSE-EXCEPTIONS.md) and the [open-source notices](THIRD_PARTY_NOTICES.md) (also in the app under **Settings › Open-source licenses**).

This project is entirely vibe-coded. I daily-drive it, but if you have Android or app-security experience, a review would be very welcome; please open an issue or discussion.

<details>
<summary><b>Bundled assets and thanks</b></summary>

Bundled assets carry their own licenses: the weather animations are
[Meteocons](https://github.com/basmilius/meteocons) (MIT, Copyright 2020-present Bas Milius), the
voice key's speech detector is [Silero VAD](https://github.com/snakers4/silero-vad) (MIT, Copyright
2020-present Silero Team), and the icon font is [Symbols Nerd Font Mono](https://github.com/ryanoasis/nerd-fonts) (MIT for the Symbols-only font, with separate licenses for its glyph sources).

What started out as me just wanting sixel image drawing in [TEL](https://github.com/t-e-l/tel) spiraled out of scope to what this project is today.
All credits go to the amazing developers and contributors of Termux, TEL, and Termux:Monet.

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
</details>

[obt-termux]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.termux%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Ev%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_termux_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-termux-pre]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.termux%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20pre-release%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Ev%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_termux_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-nix]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.termux.launcher.nix%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20%28Nix%29%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Enix-v%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_nix_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-nix-pre]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.termux.launcher.nix%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20%28Nix%29%20pre-release%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Enix-v%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_nix_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-vaj]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.vaj.tl%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20%28VAJ%29%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Evaj-v%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_vaj_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
[obt-vaj-pre]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.vaj.tl%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPickleHik3%2Ftermux-launcher%22%2C%22author%22%3A%22PickleHik3%22%2C%22name%22%3A%22Termux%20Launcher%20%28VAJ%29%20pre-release%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5Evaj-v%5B0-9%5D%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22_vaj_%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5B0-9%5D.%2A%5C%22%7D%22%7D
