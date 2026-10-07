---
page_ref: /docs/apps/termux-launcher/index.html
---

# Termux Launcher user guide

Termux Launcher is an Android home screen with a real Termux shell at its centre. Beside the terminal sit a widget page and a Linux display; inside it are native windows and panes, a built-in keyboard, a live status bar, and optional on-device AI.

These pages describe **v1.0.0** and were checked against the standard arm64 build on a physical Android 16 phone. The Nix and VAJ editions use the same interface under a different package name.

Each page covers one job and reads in a few minutes. The short version of this guide lives on the website at [picklehik3.github.io/termux-launcher-site/docs](https://picklehik3.github.io/termux-launcher-site/docs/); these pages hold the detail.

## New here?

1. [Get started](Get_Started.md): pick an edition, install, finish the first launch and make it your home app.
2. [Learn the launcher](Learn_The_Launcher.md): the three places, the chrome around them, the tour and where help lives.
3. Then set up what you use every day, below.

Two commands are worth running in your first terminal regardless:

```sh
pkg update && pkg upgrade
termux-setup-storage
```

The first updates the Termux package environment. The second asks Android for shared-storage access; skip it if your command-line tools do not need your shared files.

## Everyday

- [Home screen and apps](Home_Screen_And_Apps.md): the dock, the drawer, folders, quick reply, double-tap to lock.
- [Widgets](Widgets.md): the widget page, the eleven built-in widgets, placing and sizing.
- [Status bar](Status_Bar.md): sessions and window chips, CPU, memory and weather, the clock.
- [Notifications](Notifications.md): dots, pinned notification cards, history for the shell.
- [Layout and full screen](Layout_And_Full_Screen.md): Launcher mode, the Layout tab, minimal mode, full screen.
- [Look and themes](Look_And_Themes.md): wallpaper, the Look slider, wallpaper colours, icon packs, cursor trails and effects.

## Typing

- [Keyboard](Keyboard.md): the built-in keyboard, docked, floating and split, its theme and font.
- [Extra keys](Extra_Keys.md): the key row above the keyboard and its editor.
- [Keyboard shortcuts](Keyboard_Shortcuts.md): every default Ctrl+Alt shortcut.
- [Custom keybindings](Custom_Keybindings.md): `~/.termux/termux-launcher-bindings.conf`.

## Terminal

- [Panes, windows and sessions](Panes_Windows_And_Sessions.md): splits, layouts, floating panes, workspaces.
- [Command palette and actions](Command_Palette_And_Actions.md): the palette and every action it can run.
- [Touch, links and clipboard](Touch_Links_And_Clipboard.md): touch as a mouse, links, scrollback, clipboard history.
- [Terminal fonts](Terminal_Fonts.md): the font store, Nerd Fonts, ligatures, `fonts.conf`.
- [Graphics, protocols and compatibility](Terminal_Kitty_Protocols.md): kitty and Sixel graphics, text sizing, OSC escapes, cursor trail styles.
- [Programs and agents inside the terminal](Programs_Inside_The_Terminal.md) and [Agent status](Agent_Status.md): what scripts and AI agents can rely on.
- [Config files](Config_Files.md): every file the launcher reads under `~/.termux` and `~/.config`.

## Extras

- [The Linux display](X11_Display.md) and [Linux apps from a distro](Linux_Apps_From_A_Distro.md): graphical apps beside the terminal.
- [On-device AI](On_Device_AI.md) and [On-device AI models](On_Device_AI_Models.md): local models, voice, the OpenAI-compatible endpoint.
- [Voice input](Voice_Input.md) and [Text to speech](Text_To_Speech.md): dictation, cleanup, read aloud, `tai speak`.
- [tlstore](Tlstore.md): the tool store (fastfetch, sigye, kitten, coding agents, btop).
- [Permissions and Shizuku](Shizuku.md): what each permission is for, and the privileged backend.

On-device AI is optional. Nothing in the terminal or the launcher needs a model, an API token or Shizuku.

## Reference

- [launcherctl](LauncherCtl.md): the shell companion (panes, notifications, clipboard, wallpaper, keyboard, agent status).
- [LauncherCtl HTTP API](LauncherCtl_API.md): the local OpenAI- and Ollama-compatible endpoint.
- [On-device AI backends](On_Device_AI_Backends.md): LiteRT-LM and MNN internals, model formats, validation.
- [Settings map](Launcher_Settings.md): every Settings destination and the page that explains it.
- [Troubleshooting](Launcher_Troubleshooting.md).

## Nix edition

- [Nix: beginner's guide](Nix_Getting_Started.md), [Nix package management](Nix_Package_Management.md), [Nix fork differences](Nix_Fork_Differences.md), [Migrating from the VAJ edition](VAJ_To_Nix_Migration.md).

## Project

- [Showcase tools](Building_Terminal_Showcase_Tools.md), [Developer docs](Developer_Docs.md), [Releases and changelog](https://github.com/PickleHik3/termux-launcher/releases), [Source repository](https://github.com/PickleHik3/termux-launcher).

This guide covers Termux Launcher-specific behaviour. General shell commands, Linux packages and programming-language setup are covered by the upstream [Termux wiki](https://github.com/termux/termux-app/wiki).
