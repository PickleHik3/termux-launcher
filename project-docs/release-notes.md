# Changelog

Every shipped release, newest first. This is the whole changelog — the notes for the release
currently being written live in their own `release-notes-v<version>.md` until it is published, then
they move in here and that file goes. AGENTS.md has the convention and the voice.

Entries are kept as they shipped and are not revised afterwards. Where an old entry describes the
state of an edition, read it as of that release, not as of today.

Each version's **Editions** list carries only what was exclusive to the Nix (`com.termux.launcher.nix`)
or VAJ (`io.vaj.tl`) build; everything above it applies to all three.

**Versioning.** From v1.0.0 every edition ships the same plain `X.Y.Z`; the edition is in the tag
(`vX.Y.Z`, `nix-vX.Y.Z`, `vaj-vX.Y.Z`) and the APK name, and hotfixes bump the patch number. Older
entries keep the tags they shipped under (`-nix`, `-vaj`, `+hotfixN`, `-a`).

---

## v1.0.0

37 days since the last update?! And I've been aiming to release this version for the last 5 weeks, smh. I've got ADHD and just-one-more-feature disease.

This was supposed to be 0.2.40. Somewhere along the way it became 1.0. The launcher I'd always pictured in my head is finally here. I should probably stop adding features now.

There's a lot in this one. Here's the short version:

- Twelve built-in Termux Launcher widgets: clocks, calendar, agenda, weather, battery, system stats, media, notifications, tasks, scratchpad, and scheduled commands.
- [A dedicated Home page](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Learn_The_Launcher.md#move-around) for widgets, beside the terminal. The old pull-down widget pane is gone; hold a page's border until it sinks, then drag sideways to switch pages.
- [An in-app X11 display](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/X11_Display.md) for Linux GUI apps. Installed apps appear in the drawer and open with a tap. Proot apps work too, but that part is still very untested.
- [tlstore](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Tlstore.md), my collection of terminal goodies: fastfetch with GIFs, dawn, Claude Code, Codex, OpenCode, sigye, btop, and my shell config. Run `tlstore` to browse.
- [Local speech-to-text](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Voice_Input.md), optional cleanup with a local LLM, and [text-to-speech](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Text_To_Speech.md). Once the models are downloaded, these run on your phone.
- [Layout customisation](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Layout_And_Full_Screen.md), [Appearance controls](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Look_And_Themes.md), custom app icons, floating/split keyboards, retro CRT effects, and a much-needed pass over the light theme.
- [Automatic pane tiling](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Panes_Windows_And_Sessions.md#automatic-layouts), [big and small terminal text](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Terminal_Kitty_Protocols.md#text-sizing-osc-66), [clipboard history](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Touch_Links_And_Clipboard.md#clipboard-history), and plenty of fixes.
- This is the first release built as a proper release build rather than a debug build. It starts faster, and it installs over your existing launcher without an uninstall.

If you're updating, I'd recommend taking the new tour and trying its extra-keys layout. Your old row is saved as “Before the update” under Settings → Keyboard → Terminal extra keys → Presets. There's also [in-app help and a user guide](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Learn_The_Launcher.md#get-help) now.

Maybe get a cup of tea for [the full write-up](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/project-docs/release-notes-v1.0.0.md#the-long-read), including the smaller changes, known rough edges, and edition notes.

### The long read

This was supposed to be a much smaller update. Anyways, here's where those 37 days went.

#### Home, terminal, and Linux apps

The launcher now has three pages: widgets on the left, the terminal in the middle, and an X11 display on the right. To move between them, hold the page's border until it sinks and drag sideways, tap the page icons that peek in at the edges of the status bar, or use the new extra keys. Swipe up from the bottom border for the keyboard, and down from the top border to unfold the status bar. [The tour](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Learn_The_Launcher.md#the-tour) teaches all of this.

[Home](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Widgets.md) is a traditional home screen where you can place your widgets. The old pull-down widget pane is gone. Move and resize widgets, change the grid, and drag widgets onto another page. In the widget picker, hold any card to pick a widget up and carry it across page edges.

Termux Launcher also brings twelve widgets of its own: analog and digital clocks, an agenda, a month calendar, weather, battery, system stats, media controls, notifications, tasks, a scratchpad, and a command that runs on a timer. They resize to fit your grid and follow your theme, with a choice of Tonal cards or cards styled like terminal panes. Tasks and scratchpad notes are Markdown files in `~/notes`, so you can edit them from the terminal too.

Weather now takes a place you pick: search for a city in Settings or on the first-run card, and no location permission is needed. “Use my location” is still there if you prefer it.

The [Display page](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/X11_Display.md) runs Linux GUI apps right inside the launcher:

- Apps installed from the Termux X11 repo automatically appear in the app drawer. Tap one to start the display and open it.
- [Apps inside proot](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Linux_Apps_From_A_Distro.md) get indexed too, but this is very untested. If you run into bugs, please [file an issue](https://github.com/PickleHik3/termux-launcher/issues).
- There's a “Get GUI apps” setup screen to help you get started.
- Copy/paste works across Android, the terminal, and the display.
- [Mouse mode](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/X11_Display.md#every-day) gives you a touchpad in place of the keyboard, with gestures for scrolling, right-clicking, and switching windows.

I'd recommend running individual GUI apps instead of a full desktop environment, though you can do either.

#### Finding your way around

There's a [new onboarding tour](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Learn_The_Launcher.md#the-tour). Even if you've been using the launcher for a while, I'd recommend going through it. You can replay it any time from Settings → About & help.

Try the new extra-keys layout when it asks, too. Your previous layout is saved as “Before the update” under Settings → Keyboard → Terminal extra keys → Presets, so you can always go back. There's also an App drawer extra key now.

There's also an [in-app help overlay and searchable user guide](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Learn_The_Launcher.md#get-help). A bit WIP, but about 90% there. If I keep refining it, this update will take another 2 weeks. The help overlay uses drawn diagrams now, in place of the video clips.

#### Moving things around, and making them look nicer

Each page's corner tab and the terminal's long-press menu now open one Appearance editor for Wallpaper, Look, Layout, and Icon pack.

The [Layout editor](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Layout_And_Full_Screen.md) lets you arrange the status bar, apps row, and extra keys, and adjust sizes. Portrait and landscape have separate arrangements; Home, Terminal, and Display share them. You can edit the other orientation without physically turning your phone. On a tablet in landscape, the controls sit beside the preview.

A new [Style setting](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Layout_And_Full_Screen.md#style-corners-and-margin) makes the whole chrome either one Docked glass frame or Floating cards with space around them. The status bar can now be hidden like the other bars, the A–Z index can shrink to a small pull tab, and [minimal mode](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Layout_And_Full_Screen.md#minimal-mode) has its own layout.

“Surface editor” is now called [Appearance](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Look_And_Themes.md), and has been reworked around a live preview. Choose a Look or use the vertical sliders to customise blur, tint, grain, opacity, margins, and corners across the launcher or for an individual surface. One Done button applies your changes across all four tabs; reopening within 30 minutes takes you back to where you left off.

Preview icon packs against your own dock, choose custom app icons, or apply the pack only to pinned apps. The Wallpaper tab previews Home and Lock separately and keeps your five most recent wallpapers handy.

While I was there:

- Improved the in-app keyboard's default colours. Tbh, forgot about this one for a long time.
- Simplified the keyboard colour customisation menu. Hopefully simpler now, lmk.
- Reworked the app-wide light theme. Another thing I forgot about because, obviously, I'm living on the dark side.
- Added fancier glass effects on supported devices. Glassier glass.
- Polished the flip clock theme to fit the Material look better, and made the expanded clock fit the space available.
- Terminal contrast presets now change the terminal colours independently of the surrounding glass, while respecting your custom `colors.properties`.
- Added [retro terminal effects](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Look_And_Themes.md#cursor-trail-and-terminal-effect): CRT, green or amber CRT, and TFT. They cover the whole launcher, including its bars, keyboard, and popups.
- The [cursor trail](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Terminal_Kitty_Protocols.md#cursor-trail) has styles now: Comet, Motion blur, Railgun, Torpedo, and Pixie dust.
- Reorganised Settings into focused pages with clearer labels and more room for large text. On-device AI has moved to the top level of Settings.
- Right-to-left languages are supported, touch targets are larger, the Layout editor works with a screen reader or a keyboard, and there's one master switch for haptic feedback.

There are [floating and split keyboards](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Keyboard.md#docked-floating-or-split) now, plus [pressed-key popups](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Keyboard.md#typing-and-feedback) with hints for the characters you can swipe towards. The keyboard's settings cog opens the launcher's settings, and the font picker can browse internal storage.

#### tlstore, and my new favourite TUI

I'm introducing [tlstore](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Tlstore.md), which ships with Termux Launcher and installs some of my favourite terminal apps and configs, patched to work here. Run `tlstore` to browse:

- fastfetch with animated GIF support
- Claude Code, Codex, and OpenCode
- sigye
- dawn
- btop, which requires the launcher's [Shizuku integration](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Shizuku.md#setting-it-up)
- my shell config

tlstore asks before updating itself, and you can [go back a version](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Tlstore.md#going-back-a-version) or hold one with `tlstore rollback`, `hold`, and `unhold`.

If there's something you'd like that isn't available through Termux's `pkg`, put in a request on the [tlstore issue tracker](https://github.com/PickleHik3/tlstore/issues). The [build recipes and patches](https://github.com/PickleHik3/tlstore) are there too, if you want to vet the binaries.

A special mention for [dawn](https://github.com/andrewmd5/dawn), my new most beloved TUI app. It's a writing/note-taking app with live Markdown rendering, including headings that actually change size as you type.

Termux Launcher now supports [kitty's OSC 66 text sizing](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Terminal_Kitty_Protocols.md#text-sizing-osc-66), so terminal apps can draw big and small text. Dawn is a very nice excuse to try it.

In the tlstore build of dawn, if your phone supports local LLMs and you've [downloaded a model and picked it in the Model centre](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/On_Device_AI.md#model-centre), press `Ctrl+/` to open a completely local assistant for the note you're working on. It can also generate a title from the contents, and with an EmbeddingGemma model installed, dawn finds notes by meaning rather than by the words you typed.

#### More things your phone can do locally

- Added [speech-to-text](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Voice_Input.md) with Whisper and Parakeet, with Silero detecting when you're speaking. Set it up under Settings → Keyboard → Voice input.
- Added optional [local LLM cleanup](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Voice_Input.md#cleanup) for dictated text. You can undo the cleanup if it gets creative. Cleanup offers Light for careful corrections and Polished for paragraphs and lists.
- The [dictation card](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Voice_Input.md#the-dictation-card) has been redesigned, and listening stops after 5 seconds of silence by default.
- Added [text-to-speech with KittenTTS](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Text_To_Speech.md#quick-start). Install the voice model, then select terminal text and tap Read aloud, or use `tai speak` from the shell. Read aloud opens a reading card that marks the sentence being spoken and can pause and resume. `tai speak` can read piped text sentence by sentence as it arrives. English only for now.
- [App categorisation](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Launcher_Settings.md#apps) can use your local LLM too. If your phone can't run one, copy the prompt into something like ChatGPT, then bring the answer back through the persistent notification. You'll need to run it again after installing more apps.
- TAI is now called [On-device AI](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/On_Device_AI.md) in Settings, grouped into Runtime, Models & voice, and Developer access. The `tai` command is unchanged.
- Reworked the [Model centre](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/On_Device_AI.md#model-centre) so you can choose a model for each function, see which functions use an installed model, and get recommendations for your phone. Licences are shown before a download, and installed models have a Delete button.
- Added a [“Models for this phone”](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/On_Device_AI.md#models-for-this-phone) setup card, [remote models](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/On_Device_AI.md#remote-model) using your own provider key, and a Hugging Face token prompt when a gated model is refused.
- Added [benchmarks](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/On_Device_AI_Models.md#benchmark): one dial per test and a ranked list, with guards for battery and heat. A “Your features” check measures each feature's real workload on your phone, and the Model centre uses the results. `tai benchmark` runs it from the shell.
- Added [image generation](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/On_Device_AI_Models.md#image-generation) from the Model centre or `tai image`, with Hugging Face and folder import.
- Added a Memory limits setting with Relaxed and Unrestricted options, and fixed issues with the runtime's limits and context window.

#### The terminal itself

You can now enable [automatic tiling](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Panes_Windows_And_Sessions.md#automatic-layouts) under Settings → Terminal → Sessions and panes, then use `Ctrl+Alt+Return` to open another pane. There's also an option to grow the focused pane while the others shrink aside, similar to focus.nvim.

Panes got some polish of their own: [resize a split by dragging its divider](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Panes_Windows_And_Sessions.md#resize-panes-and-text), [swap a pane with its neighbour with a two-finger flick](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Panes_Windows_And_Sessions.md#swap-panes-with-a-two-finger-flick), and a split pane's corner tab holds only Close, Move, and Maximise.

[Touch interactions](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Touch_Links_And_Clipboard.md#touch-works-like-a-mouse) have changed a little: a short long-press (ikik) acts as a mouse press in apps that support it. Hold and drag to resize things in herdr or nvim, for example. Keep holding through the second buzz to get the usual Copy/Paste menu. Mouse clicks now carry Ctrl, Alt, and Shift.

A few more everyday improvements:

- Added [clipboard history](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Touch_Links_And_Clipboard.md#clipboard-history). Swipe down-left on Ctrl to bring it up, tap an entry to paste, or pin something to keep it across restarts.
- Terminal apps fill out towards the inner rim of rounded panes now, so you don't get that square-terminal-inside-a-rounded-border ugliness.
- The [cursor trail](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Terminal_Kitty_Protocols.md#cursor-trail) stays inside its own pane.
- Redesigned the [sessions browser](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Panes_Windows_And_Sessions.md#sessions) to match the rest of the app.
- Improved [URL detection](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Touch_Links_And_Clipboard.md#links). Tapping a plain URL or a hyperlink now shows the same Copy/Open strip, including inside apps that handle mouse clicks.
- Fixed more [kitty graphics](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Terminal_Kitty_Protocols.md#kitty-graphics-tier-2) behaviour, including images sent by file, as used by some Neovim plugins.
- Terminal programs can use [kitty's extended clipboard](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Terminal_Kitty_Protocols.md#extended-clipboard-osc-5522), and buttons in [OSC 99 notifications](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Terminal_Kitty_Protocols.md#notifications-osc-99-progress-osc-94-and-the-clipboard-osc-52) become Android notification actions.
- [`launcherctl`](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/LauncherCtl.md) gained vibrate, torch, battery, volume, toast, wallpaper, and `notify --close`, and its clipboard copy works while the launcher is off screen.
- [Notifications](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Notifications.md): pinned cards are tinted and can be swiped away with undo, and an opt-in [per-app history](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Notifications.md#notification-history) lets shell agents read what arrived.
- Closing a pane now stops the programs it was running instead of leaving them behind in the background.

#### A few more fixes

- The in-app keyboard now stays hidden when a hardware keyboard is connected and you have enabled that setting ([#42](https://github.com/PickleHik3/termux-launcher/issues/42)).
- Added an option to let Android handle Ctrl+Space for hardware keyboard language switching ([#43](https://github.com/PickleHik3/termux-launcher/issues/43)).
- Voice input settings remain available when you use the Android keyboard.
- Reduced repeated work when opening or returning to the launcher, including with custom icon packs and glass effects.
- Improved drawing performance for launcher chrome and effects.
- On the Display, a single finger no longer turns into a pinch.
- The version is a plain `1.0.0` on every edition now; the edition is shown on its own line in About.

- Fixed wallpaper blur controls on affected phones ([#37](https://github.com/PickleHik3/termux-launcher/issues/37)).
- Fixed fullscreen mode losing the terminal tint ([#27](https://github.com/PickleHik3/termux-launcher/issues/27)).
- Large wallpapers no longer freeze the launcher while their blur is being prepared.
- Fixed the clock shifting nearby items as the seconds changed.
- Improved widget sizing and behaviour when switching between light and dark themes.

### Editions

- Nix: GUI apps installed in your Nix profile appear in the drawer too. [Setup guidance](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/docs/en/Nix_Package_Management.md) points you towards `home.nix` and nixpkgs. Nix releases are tagged `nix-v1.0.0` from now on.
- VAJ: GUI setup offers both the edition's own packages and proot. The native package option leaves out the browser because the repo doesn't carry one. VAJ releases are tagged `vaj-v1.0.0` from now on.

### Thank you

I also want to give the projects behind all this a proper thank you: Termux, Termux:Monet and
TEL for the foundations; Termux:X11 and Unexpected Keyboard for two enormous parts of the app;
kitty for the terminal work; Noctalia and herdr for the templates and detection rules. Ghostty,
Hyprland and focus.nvim gave me plenty of ideas too. And thanks to the speech-model and LiteRT
contributors, and everyone making the tools I've been enjoying through tlstore.

I've linked the projects and described the adaptations in the
[credits and notices](https://github.com/PickleHik3/termux-launcher/blob/v1.0.0/THIRD_PARTY_NOTICES.md).

---

## v0.2.39

### New

#### Terminal

- Automatic tiling: open a new pane with `Ctrl+Alt+Enter`. Each new pane splits the pane you are in along its longer side, so on a phone the first split is horizontal and the next goes side by side. Drag a pane onto another to put it in the half you drop it on. `Ctrl+Alt+L` cycles tiling layouts. Turn it on or off from Settings ▸ Terminal & Status ▸ Sessions and panes ▸ Automatic tiling.
- Spotlight the active pane: focus.nvim-like behaviour, the focused pane takes up 70% of the screen. Same section, off by default.
- Programs and agents can open panes of their own: `launcherctl pane open -- htop` opens a pane running `htop`, and a script or AI agent can write to, read, focus and close the panes it opened, and only those. `launcherctl pane list` shows every pane. Let programs and agents control panes, in the same section, switches it off.
- Process indicator on the window chips in the status bar: a turning ring, a tick mark or a bell icon shows what each window's process is doing.
- Programs can report progress and send notifications with the usual terminal escapes (the ConEmu progress report, the iTerm2 and rxvt notifications), which is what the ring, the bell and the tick read.

#### Keyboard shortcuts

- `Ctrl+Alt+Enter` opens a new pane.
- `Alt+Arrow` moves between panes. It was `Ctrl+Arrow`, which took word jumps away from the shell and editors.
- `Ctrl+Alt+W` closes the focused pane.

#### Keyboard

- Learn where you tap (Settings ▸ Keyboard & input): the keyboard learns where your taps land on each key and nudges near-misses onto the key you meant. Off by default, and Forget learned taps clears what it has learned. (Not tested thoroughly yet, post an issue if you have problems.)
- Keyboard bottom padding adjustment. Change it from the surface editor by grabbing the bottom handle of the keyboard, or from Settings ▸ Keyboard & input ▸ Appearance.

#### Extra keys

- Redesigned to be a little more intuitive (hopefully).
- One-tap CTRL, ALT, TAB and other common keys (#22).

#### App drawer

- Open the keyboard with the drawer (Settings ▸ Launcher & apps ▸ Drawer layout) brings the search keyboard up as the drawer opens (#24).
- Choose between your default Android keyboard or the in-app keyboard for drawer search.
- In the Categories layout the drawer says when more than five apps are waiting to be categorized, and Re-run categorization shows the count.

#### Appearance editor

- Redesigned surface editor: touch the icon on the floating pill to change the global values, tap an item (status bar, keyboard, dock, terminal) to override them for that surface.
- Custom preset slot: once you change anything in the surface editor a save icon shows up. Tap it to save the current settings as a custom preset.

#### Companion apps

- Termux:Boot now has a matching build for every edition, so the scripts in `~/.termux/boot/` run after a restart (#25).

### Changes

- The dock's A–Z row gets better padding and alignment while the extra-keys row is hidden.
- Simplified app drawer close animation.

### Fixes

- On some phones the terminal collapsed to a blank area, and the dock jumped to the top, when the command input opened the Android keyboard (#21, #23).
- The Android keyboard no longer stays stuck on screen after the screen turns off and on while the command input has it.
- A URL that a tmux or other multiplexer pane wrapped at its border is found and opened whole, from either half.
- With the extra-keys row off, closing the in-app keyboard no longer stacks the dock rows at the dock's top edge.
- Images sent over the kitty graphics protocol render whatever their size. `kitten icat` used to print a wall of text once the image passed a few kilobytes.
- Terminal text at the corners of a pane no longer clips under its rounded corners.
- Scrubbing the A–Z row and sliding up onto the app icons used to tick twice. Fixed.
- `setup-launcher`: fastfetch logos made from 1-bit or 16-bit PNGs draw again. They came out blank.
- `setup-launcher`: with a one-line prompt, fish's clear no longer leaves a blank row above the keyboard. Run `setup-launcher` again to pick up the config.

### Editions

Shipped as `v0.2.39-nix` and `v0.2.39-vaj`.

- VAJ: `setup-launcher` installs a fastfetch built for this edition. The one it fetched before could not start.
- Nix: the flake template's animated fastfetch overlay carries the same logo fixes.

---

## v0.2.38+hotfix1

### Fixes

- The flip clock now follows the alignment setting instead of always sitting in the centre.

### Editions

Shipped as `v0.2.38-nix+hotfix1` and `v0.2.38-vaj+hotfix1`. Nothing was exclusive to an edition.

---

## v0.2.38

### New

#### Keyboard

- Every layout the app ships — ninety-one of them, QWERTY and Dvorak through
  Arabic PC and Dubeolsik — can now be used, not just the launcher's own.
  Settings ▸ Keyboard ▸ Layouts picks which ones the keyboard cycles through
  and in what order.
- Swiping the space bar down steps that cycle. You can also bind a key to it
  (`keyboard.cycle_layout`), jump straight to a layout by name
  (`keyboard.select_layout latn_dvorak`), or pick one from the command palette,
  where the Keyboard section lists the cycle once it holds more than one layout.
- Your own `~/.termux/keyboard/layout.xml` is the first entry in the cycle, so a
  custom layout sits alongside the shipped ones rather than replacing them.

### Fixes

- When Android has put the app in a state where it is not allowed to run
  anything it installs, it now says so, tells you a full uninstall and reinstall
  is what clears it, and names any app sharing its user id that caused it.
  Before, this looked like a failed download, and every launch fetched the
  bootstrap again to fail the same way.
- The bootstrap directory is kept when that happens instead of being deleted, so
  a working install is not thrown away.
- The appearance editor no longer opens fully collapsed in some cases.

### Editions

Shipped as `v0.2.38-nix` and `v0.2.38-vaj`. Nothing was exclusive to an edition.

---

## v0.2.37

A hotfix for the appearance editor.

### Fixes

- The appearance editor keeps its presets and controls when there is little
  room above the dock. With the dock rows switched off, or the Android keyboard
  in use, it could open as nothing but a title with **Reset** and **Done** (#20).

### Editions

Shipped as `v0.2.37-nix` and `v0.2.37-vaj`. No edition-exclusive notes were written.

---

## v0.2.36

A big one: a rebuilt appearance editor, tappable links in the terminal,
redesigned shortcut hints, and fixes for the memory leaks that made long
sessions grow.

### New

#### Appearance

- **The appearance editor is now a single page.** *Global* sets what every
  surface shares, *Fine tune* adjusts one surface at a time. Each row shows
  whether it follows Global (✓) or has its own value (↺). Nothing is applied
  until you tap **Done**.
- **Blur, opacity and grain are now one choice.** Pick a material — Solid,
  Glass or Frost — and how strong it should be. The individual numbers are
  still there if you want them.
- **Four ready-made looks:** Classic, Mist, Slate and Bare. Each preview is a
  small phone mock drawn over your own wallpaper, so you can see a look before
  you apply it. One tap applies it, one tap undoes it. A fifth **Custom** slot
  saves your own. Mist now follows Obsidian-Music's glass: an ink-blue tint,
  a diagonal gradient rim, and springier sheet arrival with a deeper backdrop.
- **Pick a clock face by looking at it** — all six are drawn as themselves
  instead of listed by name.
- Rounded corners for the sessions indicator and the window pills, so the two
  chips finally match (#16).
- Rounded corners for the docked terminal frame. The spacing control is now
  named for what it does: *Margin* when docked, *Inner padding* when floating.

#### Terminal

- **Tap a link to open it.** New installs get this switched on; existing
  installs keep their settings untouched. To enable it yourself, set
  `terminal-onclick-url-open = true` in `~/.termux/termux.properties`.
- Tapping a link now shows a small Copy / Open bubble above it, instead of a
  sheet that covers your output.
- Copy mode and scrollback search show a small card listing the keys you can
  use, matching the mode you are actually in.

#### Keyboard shortcuts

- **Redesigned the shortcut hints.** Hold a prefix key and a card appears at
  the edge of the terminal, one shortcut per line. They used to take over the
  A-Z row and move around depending on your settings.
- Holding a prefix now highlights only the keys the card lists, and `?` is
  marked separately so the full keymap is easy to find.
- The full `?` table now uses the terminal's width and scrolls inside it,
  instead of being capped at 45% of the screen and overlapping the dock.

### Improvements

- The app drawer closes with one pull instead of two, and follows your finger.
- Notices can now offer an undo.
- Dragging a slider no longer resizes the terminal on every pixel — the preview
  updates live and the layout settles once, when you let go.
- A large internal cleanup. Nothing about it shows on screen, but it is what
  made the memory and speed work below possible.

### Fixes

#### Memory and speed

- **Fixed memory leaks.** A long session with images loaded could climb past
  600 MB and stay there. Memory now comes back on `clear`, when you close a
  pane, or when the system needs it.
- Animated images no longer hold onto their frames forever. Playback stops when
  they are off screen, and memory is released once nothing is showing them or
  when the system is running short. Running `clear` after a fastfetch banner
  now hands back tens of megabytes.
- Fixed a Shizuku leak that grew with every privileged command — one live
  session had built up 5,497 leftover handles.
- App icons are now kept once, at the size they are actually drawn, instead of
  every installed app holding full-size artwork for the life of the app.
- Stopped keeping a decoded copy of your wallpaper in memory for the whole
  session.
- The CPU widget shows a reading within a second of starting, instead of
  sitting blank for up to 36 seconds.

#### Terminal

- Opening something full-screen — tmux, an editor, a pager — no longer wipes
  the images from the screen you came from.
- Images now land on the row they were placed on, instead of drifting down the
  screen with a gap above them.
- Long animations play through instead of snapping back partway.
- Fastfetch now places its logo in a way that also works inside tmux and Neovim
  image plugins.

#### Interface

- Colours from `~/.termux/colors.properties` now reach the whole interface. The
  dock, status bar, drawer, keyboard and command palette were falling back to
  wallpaper colours in dark mode (#16).
- A pressed terminal pane keeps its edges, and a small pane no longer rounds
  into a lozenge.
- The floating status bar's chips are no longer clipped when you increase its
  corner radius.
- On the in-app keyboard, a quick tap on Ctrl now latches reliably, so Ctrl+V
  works as pressed.
- A session with a single window no longer shows two focus outlines.
- Switching or creating a window no longer flashes black when pane borders are
  turned off.
- The resize glow now follows the pane's real corners.
- `app-categories.conf` has moved into `~/.termux` alongside the other config
  files. An existing file is moved for you.

#### Setup

- `setup-launcher` now installs a build toolchain, so Neovim's treesitter
  parsers and Mason packages build properly on first use.

### Editions

- **Nix** — Colours from `~/.termux/colors.properties`: a newly generated file is now
  picked up, instead of the old palette continuing to be served (#16). This edition ships
  for 64-bit devices only — `arm64-v8a` and `x86_64`. `setup-launcher` is not part of this
  edition; packages come from nixpkgs.
- **VAJ** — Ships for 64-bit ARM devices (`arm64-v8a`) only.

---

## v0.2.35-a

A small fix release on top of v0.2.35.

### Fixes

- The Style entry is back in the terminal long-press menu on the Nix edition
  when TLNix:Styling is installed (#13).
- Removed the tinted square edges behind the rounded corners of the CPU and
  weather cards (#13).
- The Lazy Mode toggle now actually saves, and applies without restarting the
  app.

### Editions

- **Nix** — The Style entry fix above is this edition's: it was the one missing it (#13).
- **VAJ** — At the time this edition received security fixes only; this small fix release
  rode along to keep the editions in step.

---

## v0.2.35

### New

#### App Drawer

- Added an app drawer accessible by swiping down on the app icons row.
- Added 3 app drawer layouts:
  - Vertical
  - Horizontal
  - Categories
- Categories can be corrected per app, and a Games category joins the set.
- The drawer can be sorted by an on-device AI model, or through any AI chat app
  you already use via a copy-paste prompt. Results land in a hand-editable
  `~/.termux/app-categories.conf`, and your own edits always win.
- A robot glyph in the status bar shows while an AI model is loaded, with a
  countdown to its idle unload.
- Folders are shared between the dock and the drawer: drag an app onto another
  to merge, drag one back out to remove it, rename in place. Folders hold up to
  36 apps and can be placed anywhere in the drawer.

#### Widgets Page

- Added swipe-down gestures on the status bar:
  - Half swipe expands the status bar.
  - Full swipe opens the widgets page.
- Widget pages can be added, reordered and edited in place; long-press a widget
  to move, resize or remove it.

#### Keybinds

- Added a display name field for custom keybinds (`map --label "…"` in
  `~/.termux/termux-launcher-bindings.conf`).
- Display names now appear in the keyboard hints popup.
- Added a tmux-style prefix key: declare `leader ctrl+space` in the bindings
  file and every Ctrl+Alt shortcut also answers to the prefix followed by the
  same key.
- Reworked pane, window and session navigation so each level has its own chord:
  - `Ctrl+Arrow` moves between panes.
  - Prefix + Left/Right walks windows, prefix + Up/Down walks sessions.
  - Prefix + a number picks a window, prefix + Shift + a number picks a session.
- Keybind hints now light up in the extra-keys A-Z row while a prefix is held,
  and `?` opens the full keybind table.
- Holding Ctrl+Alt on a hardware keyboard shows the same hints popup the in-app
  keyboard shows.

#### Terminal

- Added scrollback search on the dock, with vim-style copy mode over the
  transcript (`hjkl`, `v`/`V`/`Ctrl-V` selection, `y` to copy).
- Moved every terminal prompt onto one in-app sheet, instead of system dialogs.
- Added session, window and pane renaming from an anchored chip.
- Renamed Hints to Quick select and reshaped it to match the search bar.
- Added kitty graphics Unicode placeholders, enabling images inside tmux and
  Neovim image plugins.
- The terminal name is configurable: set `terminal-term = xterm-kitty` in
  `~/.termux/termux.properties`.
- A commented `~/.termux/termux.properties` is now seeded on install, so the
  launcher's properties are discoverable without a download.

#### Nerd Fonts

- Nerd Fonts can now be used in the in-app keyboard and Extra Keys.
- The status bar now uses Nerd Fonts bundled with the app.

#### Lazy Mode

- Added an experimental Lazy Mode designed to reduce idle resource usage.
- Enable it from Settings → Terminal & Status.
- If testing goes well, Lazy Mode is intended to become the default in a future
  release.

#### Launcher

- Simplified the launcher setup script.
- Added a new repository for hosting Termux Launcher-specific binaries; every
  install is verified against a pinned checksum.
- Added Fastfetch with GIF support.
- Added `setup-nvim`, a Neovim distro chooser (AstroNvim by default, or NvChad,
  LazyVim, kickstart or stock) themed from your wallpaper palette.
- Added one switch for the launcher versus terminal-only use case, in
  Settings → Launcher & apps.
- Landscape is now usable: the dock becomes a side rail on the edge you pick,
  and the drawer is denser.
- The whole interface can follow the terminal colour scheme
  (`~/.termux/colors.properties`) instead of the wallpaper palette, with
  per-token overrides in `~/.termux/launcher-theme.properties`.
- Rebuilt the weather card around the forecast, with a greeting on arrival and
  an optional Fahrenheit unit.
- The flip clock animates seconds on their own pair, with a metadata cell
  beside the digits.

#### Extra Keys

- Added a visual-style Extra Keys editor: tap a key to edit it, hold and drag
  to move it, with macros and swipe-up actions per key.
- Added a glyph picker behind the label fields, searchable across all 10,512
  bundled Nerd Font icons.
- The refined key row now ships as the built-in default — no setup script
  needed, and your own `extra-keys` property still wins.

### Changes

- Reworked in-app notifications: every stock toast is replaced by one quiet
  chip in the top-right that you can act on, and mirrored notification cards
  can be swiped away.
- New motion system: panes, windows and the drawer move with consistent
  physics, and every terminal pane sits on its own glass slab.
- Refined dock animations so app icons now animate together with the dock.
- Resizing a pane lights the edge being resized instead of drawing a border,
  and a keybind that runs briefly names itself.
- On the in-app keyboard, the pressed key now dominates visually and hint
  lighting fades out.
- Improved window and session rename surfaces to look more like the Processes
  interface.
- Improved terminal font handling so that when the space next to a Nerd Font
  icon is empty, the glyph can draw across two cells.

### Fixes

- Fixed dragging app icons into and out of folders.
- Fixed the touch surface around the clock widget: the system clock now opens
  only when the clock itself is touched.
- Changed volume key behaviour so volume keys control volume by default,
  preventing Home from hijacking them. Existing users can change this in
  `~/.termux/termux.properties`.
- A hand-written `~/.termux/colors.properties` is respected again (#11), and
  the Style entry is back in the terminal long-press menu when Termux:Styling
  is installed.
- Fixed page swipes on the pinned apps row landing back on the page they came
  from.
- The launcher no longer grows its memory use while the expanded status bar is
  open.
- Reopening Settings from a retained task no longer crashes after an upgrade.

### Security

Findings from an external review of the launcher's local API surface, all
fixed:

- The local API only answers pages served from this device; a web page from
  anywhere else can no longer reach it.
- Media paths in inference requests are validated, so a request can no longer
  read arbitrary files or pivot into the LAN.
- Notification history is opt-in and off by default; turning it off deletes
  what was captured.
- LAN mode now ends itself after 12 hours: the API goes back to this device
  only, and the old token stops working.
- The setup script verifies everything it installs against pinned checksums
  and fails closed on a mismatch.

### Editions

- **Nix** — The bootstrap now arrives with the environment already set up, so the first
  launch lands in a configured shell instead of asking you to run setup.
- **VAJ** — Added a one-time notice on first launch explaining that the edition was at the
  time deprecated and receiving security fixes only, with a link to the migration guide for
  moving to the Nix edition.

---

Releases before v0.2.35 are on their
[GitHub release pages](https://github.com/PickleHik3/termux-launcher/releases) only.
