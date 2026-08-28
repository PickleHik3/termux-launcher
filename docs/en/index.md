---
page_ref: /docs/apps/termux-launcher/index.html
---

# Termux Launcher user guide

Termux Launcher combines a real Termux shell, an Android home screen, native terminal windows and
panes, a built-in keyboard, live status widgets, and optional on-device AI.

These pages describe **v0.2.31**. The paths and behavior were checked against the standard arm64
release on a physical Android 16 device. The VAJ edition uses the same interface but a different
Android package name.

## New here?

1. [See what changed in v0.2.31](Whats_New_0.2.31.md).
2. [Install and complete first-run setup](Launcher_Getting_Started.md).
3. [Learn the launcher, terminal, dock, panes, and workspaces](Launcher_Usage.md).
4. [Use the settings map](Launcher_Settings.md) when you want to change something.
5. [Troubleshoot common problems](Launcher_Troubleshooting.md).

The app's first-launch tour gives the same short introduction. Its two recommended commands are:

```sh
pkg update && pkg upgrade
termux-setup-storage
```

The first command updates the Termux package environment. The second asks Android for shared-storage
access; skip it if command-line tools do not need your shared files.

## Everyday guides

- [What’s new in v0.2.31](Whats_New_0.2.31.md)
- [Getting started](Launcher_Getting_Started.md)
- [Using Termux Launcher](Launcher_Usage.md)
- [Settings map](Launcher_Settings.md)
- [Troubleshooting](Launcher_Troubleshooting.md)
- [Modern terminal power-user guide](Terminal_Modernization.md)
- [Terminal fonts](Terminal_Fonts.md)
- [Kitty protocols and terminal compatibility](Terminal_Kitty_Protocols.md)
- [Install showcase tools](Building_Terminal_Showcase_Tools.md)

## Nix edition

- [Nix beginner's guide](Nix_Getting_Started.md) — start here if `nix` is new to you
- [Nix package management](Nix_Package_Management.md)
- [Nix fork differences](Nix_Fork_Differences.md)
- [Migrating from the VAJ edition](VAJ_To_Nix_Migration.md) — moving `io.vaj.tl` off the deprecated APT repo

## Optional local AI

- [TAI / Termux AI user guide](Termux_AI.md)
- [Supported AI backends and model formats](Termux_AI_Backends.md)
- [LauncherCtl local API reference](LauncherCtl_API.md)

TAI is optional. Normal terminal and launcher features do not require a model, an API token, or
Shizuku.

## Project and developer reference

- [Developer docs](Developer_Docs.md)
- [Releases & changelog](https://github.com/PickleHik3/termux-launcher/releases)
- [Source repository](https://github.com/PickleHik3/termux-launcher)

This wiki covers Termux Launcher-specific behavior. General shell commands, Linux packages, and
programming-language setup are better covered by the upstream
[Termux wiki](https://github.com/termux/termux-app/wiki).
