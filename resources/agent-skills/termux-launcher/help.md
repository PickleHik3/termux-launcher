# Helping the user: which page answers what

The user guide ships with this skill under `<skill dir>/docs/`. When the user asks how the launcher
works or how to set something up:

1. **Read the page before answering.** Open the matching page below, find the section, and answer
   from it. Prefer the page's words to your memory of Termux or of earlier versions.
2. **Name settings by their on-screen path**, the way the page writes it: Settings → Keyboard →
   Voice input → Speech engine. Quote row and button names exactly.
3. **Say when the docs do not cover it.** Then say what you would try and that it is a guess;
   never invent a setting, a menu or a command.

`docs/Launcher_Settings.md` maps every top-level Settings row to its page, and the Settings
screen's own **Search settings** finds any setting by name. `docs/index.md` is the table of
contents.

## Getting started and editions

- `docs/Get_Started.md` — Which edition do I pick, how do I install or update the APK, what happens on first launch, how do I make it my home app.
- `docs/Learn_The_Launcher.md` — What are Home, Terminal and Display, what is on screen, how do I move between them, where are the tour and help.
- `docs/Launcher_Getting_Started.md` — Old link; points to Get started and Learn the launcher.
- `docs/Launcher_Usage.md` — Old link; points to the pages that replaced it.
- `docs/Launcher_Settings.md` — Where is a setting; every top-level Settings row in order with its page; how to open Settings.
- `docs/Config_Files.md` — Which files in my home directory does the launcher read or write, and how do I reload them without a restart.
- `docs/Nix_Getting_Started.md` — I installed the Nix edition and have never used Nix: what to type in the first hour, what is normal.
- `docs/Nix_Package_Management.md` — How do I install packages on the Nix edition instead of `pkg install`.
- `docs/Nix_Fork_Differences.md` — How does the Nix edition differ from upstream nix-on-droid.
- `docs/VAJ_To_Nix_Migration.md` — The VAJ edition is going away: how do I move to the Nix edition.
- `docs/Developer_Docs.md` — Internals beyond the beginner guide: project shape, launcherctl internals and security, AI runtime notes, model import, Material colours, Shizuku and rish.

## Home screen, dock, widgets and look

- `docs/Home_Screen_And_Apps.md` — How do I pin apps to the dock, make folders, use the A–Z index and the app drawer, search apps from the prompt, set it as home.
- `docs/Widgets.md` — How do I add, move, resize and remove widgets on the Home page, add pages, change the grid.
- `docs/Status_Bar.md` — What the status bar shows (sessions, windows, clock, CPU, memory, weather), how to open it and change it.
- `docs/Notifications.md` — How do I get notification dots, pinned cards in the status bar, notification widgets and a history the shell can search.
- `docs/Pinned_Notifications.md` — Old link; points to Notifications and Status bar.
- `docs/Layout_And_Full_Screen.md` — How do I move or resize the bars, change the keyboard type, use minimal mode or full screen.
- `docs/Look_And_Themes.md` — How do I change the wallpaper, the glass look, colours, icon pack, cursor trail and screen effects.

## Terminal, keyboard and panes

- `docs/Panes_Windows_And_Sessions.md` — How do I split into panes, group windows and sessions, float or arrange panes, save a workspace.
- `docs/Command_Palette_And_Actions.md` — How do I open the command palette, and which action ids can shortcuts, extra keys and swipes run.
- `docs/Keyboard.md` — How do I show, hide or switch off the built-in keyboard, use its swipes and layers, pick docked, floating or split, change its size, font and look.
- `docs/Extra_Keys.md` — What the extra-keys row in the dock does and how do I edit it, in the app or in `termux.properties`.
- `docs/Keyboard_Shortcuts.md` — What are the default shortcuts, from a hardware keyboard or the built-in one with Ctrl and Alt latched.
- `docs/Custom_Keybindings.md` — How do I change or add a shortcut in `termux-launcher-bindings.conf`: run an action, type text, launch an app, chain strokes.
- `docs/Touch_Links_And_Clipboard.md` — How does touch work in the terminal, how do links open, how do I search the scrollback, copy, paste and use clipboard history.
- `docs/Terminal_Fonts.md` — How do I change the terminal font: install one from the app, use `font.ttf`, or write `fonts.conf` for symbols, fallbacks and features.
- `docs/Terminal_Kitty_Protocols.md` — Which graphics and protocols work beyond stock Termux: images, kitty keyboard, text sizing, clipboard, cursor trail.
- `docs/Terminal_Modernization.md` — Old link; points to the terminal pages above.
- `docs/Programs_Inside_The_Terminal.md` — What can a program or agent in this terminal rely on that stock Termux lacks, and the two things that bite.
- `docs/Building_Terminal_Showcase_Tools.md` — How are the animated fastfetch and sigye builds made from pinned recipes.

## On-device AI and voice

- `docs/On_Device_AI.md` — How do I run AI models on the phone, use the Model centre and the `tai` command, and connect an OpenAI or Ollama app.
- `docs/On_Device_AI_Models.md` — Which models are offered, how do I add or import others, what they cost in memory, how to benchmark, how to make images.
- `docs/On_Device_AI_Backends.md` — How do the LiteRT-LM and MNN runners differ, and which model packages each one loads.
- `docs/Voice_Input.md` — How do I dictate, choose the speech engine, clean up dictation, use spoken commands, or transcribe from the shell.
- `docs/Text_To_Speech.md` — How do I have text read aloud: Read aloud on a selection, `tai speak`, voices, speed, the voice model.

## Agents and launcherctl

- `docs/Agent_Skills.md` — How do I install these agent skills and ask an agent for them.
- `docs/Agent_Status.md` — How does the launcher show what a coding agent in a pane is doing without opening the pane.
- `docs/LauncherCtl.md` — What `launcherctl` can do from a shell: apps, panes, windows, notifications, progress, clipboard, keyboard, phone functions.
- `docs/LauncherCtl_API.md` — The localhost HTTP API behind `launcherctl` and `tai`: routes, auth, LAN access, rate limits.

## Packages, Shizuku and X11

- `docs/Tlstore.md` — How do I install, update or roll back the extra tools, coding agents and configs from the launcher's own store.
- `docs/Shizuku.md` — What each permission is for, and how do I set up Shizuku for system readings and the privileged lane.
- `docs/X11_Display.md` — How do I turn on the Linux display and run X11 apps or a desktop beside the terminal.
- `docs/Linux_Apps_From_A_Distro.md` — How do apps installed in a proot distro show up in the app drawer.

## Troubleshooting

- `docs/Launcher_Troubleshooting.md` — Something is wrong: an APK will not install, the first shell stalls, the keyboard, shortcuts, fonts, Shizuku, AI, voice, speech or the display misbehave; how to collect diagnostics.
