# Features

Everything Termux Launcher does, grouped by where you meet it. Where a feature was ported from or inspired by another project, that project is named next to it. The [notices](../../THIRD_PARTY_NOTICES.md) list exactly what is included and adapted.

**Home and Workspace (v2)**

- Home is pages of Android widgets and app icons, swiped left and right; the Workspace beside it holds your terminal windows, then the Linux display. Home and Workspace share one status bar, dock and look. See [Home screen and apps](Home_Screen_And_Apps.md)
- Floating terminals: hold the terminal entry and choose **New float here** to float a terminal over the current Home page, on real glass that shows what is beneath
- Start in Home or in the terminal; add, reorder and remove pages under **Edit pages**, the Assistant page included
- The app drawer follows a swipe up from Home under your finger, with category cards; a peek dock and A–Z beads
- On Android 15 and later, the notification shade pulls down under your finger (needs Shizuku)

**Glass (v2)**

- Live glass on glass: the drawer, command palette, terminal sheet and menus blur what is really behind them, other glass included (**Settings › Battery & performance › Live blur**, off by default)
- A **Clear** glass style with a sharp face and lensing that scales with the surface
- An Appearance editor with Global, Home, Workspace and per-surface cards; the look changes under your finger as you swipe between Home and Workspace
- Live wallpaper scenes with a picker
- The status bar and panes make room for the camera cutout

**Assistant (v2)**

- An optional Assistant page: a chat with the on-device model that lives on its own page, with a chats list

**Launcher**

- Termux as the actual Android home launcher, the idea borrowed from [TEL](https://github.com/t-e-l/tel): an app dock, a full app drawer (vertical, horizontal and category layouts, folders), Android widget pages and a Linux display beside it
- An expandable status bar with sessions and window chips, live CPU, RAM and weather (animated [Meteocons](https://github.com/basmilius/meteocons))
- Notification dots, pinned notification cards and a notification history for the shell
- Looks and themes: Material You colours from your wallpaper (from [termux-monet](https://github.com/Termux-Monet/termux-monet)), glass surfaces with blur, edge light and corner radius you can tune per surface, icon packs, cursor trails, and theme templates (after [Noctalia](https://github.com/noctalia-dev/noctalia-shell)) so shell tools follow the wallpaper too
- Backup and restore of your settings, config files and downloaded AI models

**The terminal**

- Kitty graphics (`timg -pk foo.jpg`, animations included) and Sixel images in the terminal
- The [kitty](https://github.com/kovidgoyal/kitty) protocols, adapted from kitty's code: the kitty keyboard protocol, text sizing (OSC 66), hyperlinks (OSC 8), notifications and progress (OSC 99, OSC 9;4), clipboard read and write from programs (OSC 52 and the extended OSC 5522, pictures included), colour queries and multiple cursors; `kitten icat` and `kitten clipboard` work (kitty's remote control does not, there is no kitty to talk to)
- Fonts the kitty way: an in-app font picker installs Nerd Fonts and writes a kitty-style `fonts.conf`, with fallback fonts, ligatures, variable axes and clean box drawing
- Native sessions, windows, recursive split and floating panes (and floats on Home), layouts and workspace restore (**Ctrl + Alt** is the default keybind); tiling after [Hyprland](https://github.com/hyprwm/Hyprland), the focused pane after [focus.nvim](https://github.com/nvim-focus/focus.nvim), terminal padding after [Ghostty](https://github.com/ghostty-org/ghostty)
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

- The Assistant page (see above), with a Gemma 4 model choice on the On-device AI page
- The app drawer sorts itself into categories
- Voice dictation and cleanup: a [Whisper](https://github.com/futo-org/whisper-acft) or [Parakeet](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3) model turns speech into text on the phone, with [Silero VAD](https://github.com/snakers4/silero-vad) listening for pauses; a Gemma model cleans up fillers and misheard words while you pause, and the dictation card lets you fix a word before inserting it. The audio never leaves the phone
- Read aloud: text to speech from the terminal and `tai speak`, with the [KittenTTS](https://huggingface.co/litert-community/kitten-tts-nano-0.8) voice
- Local OpenAI- and Ollama-compatible endpoints on localhost, plus the `tai` command, so aichat and other clients can talk to the phone's own models
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
