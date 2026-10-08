<!-- Use the opening section as the GitHub release body. Keep the full write-up below in this same file. Before publishing, replace /blob/dev/ links with /blob/<release-tag>/ links so they stay tied to the shipped version. -->

37 days since the last update?! And I've been aiming to release this version for the last 5 weeks, smh. I've got ADHD and just-one-more-feature disease.

This was supposed to be 0.2.40. Somewhere along the way it became 1.0. The launcher I'd always pictured in my head is finally here. I should probably stop adding features now.

There's a lot in this one. Here's the short version:

- Twelve built-in Termux Launcher widgets: clocks, calendar, agenda, weather, battery, system stats, media, notifications, tasks, scratchpad, and scheduled commands.
- [A dedicated Home page](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Learn_The_Launcher.md#move-around) for widgets, beside the terminal. The old pull-down widget pane is gone; hold a page's border until it sinks, then drag sideways to switch pages.
- [An in-app X11 display](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/X11_Display.md) for Linux GUI apps. Installed apps appear in the drawer and open with a tap. Proot apps work too, but that part is still very untested.
- [tlstore](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Tlstore.md), my collection of terminal goodies: fastfetch with GIFs, dawn, Claude Code, Codex, OpenCode, sigye, btop, and my shell config. Run `tlstore` to browse.
- [Local speech-to-text](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Voice_Input.md), optional cleanup with a local LLM, and [text-to-speech](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Text_To_Speech.md). Once the models are downloaded, these run on your phone.
- [Layout customisation](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Layout_And_Full_Screen.md), [Appearance controls](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Look_And_Themes.md), custom app icons, floating/split keyboards, retro CRT effects, and a much-needed pass over the light theme.
- [Automatic pane tiling](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Panes_Windows_And_Sessions.md#automatic-layouts), [big and small terminal text](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#text-sizing-osc-66), [clipboard history](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Touch_Links_And_Clipboard.md#clipboard-history), and plenty of fixes.
- This is the first release built as a proper release build rather than a debug build. It starts faster, and it installs over your existing launcher without an uninstall.

If you're updating, I'd recommend taking the new tour and trying its extra-keys layout. Your old row is saved as “Before the update” under Settings → Keyboard → Terminal extra keys → Presets. There's also [in-app help and a user guide](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Learn_The_Launcher.md#get-help) now.

Maybe get a cup of tea for [the full write-up](https://github.com/PickleHik3/termux-launcher/blob/dev/project-docs/release-notes-v1.0.0.md#the-long-read), including the smaller changes, known rough edges, and edition notes.

<!-- End GitHub release body. -->

## The long read

This was supposed to be a much smaller update. Anyways, here's where those 37 days went.

### Home, terminal, and Linux apps

The launcher now has three pages: widgets on the left, the terminal in the middle, and an X11 display on the right. To move between them, hold the page's border until it sinks and drag sideways, tap the page icons that peek in at the edges of the status bar, or use the new extra keys. Swipe up from the bottom border for the keyboard, and down from the top border to unfold the status bar. [The tour](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Learn_The_Launcher.md#the-tour) teaches all of this.

[Home](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Widgets.md) is a traditional home screen where you can place your widgets. The old pull-down widget pane is gone. Move and resize widgets, change the grid, and drag widgets onto another page. In the widget picker, hold any card to pick a widget up and carry it across page edges.

Termux Launcher also brings twelve widgets of its own: analog and digital clocks, an agenda, a month calendar, weather, battery, system stats, media controls, notifications, tasks, a scratchpad, and a command that runs on a timer. They resize to fit your grid and follow your theme, with a choice of Tonal cards or cards styled like terminal panes. Tasks and scratchpad notes are Markdown files in `~/notes`, so you can edit them from the terminal too.

Weather now takes a place you pick: search for a city in Settings or on the first-run card, and no location permission is needed. “Use my location” is still there if you prefer it.

The [Display page](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/X11_Display.md) runs Linux GUI apps right inside the launcher:

- Apps installed from the Termux X11 repo automatically appear in the app drawer. Tap one to start the display and open it.
- [Apps inside proot](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Linux_Apps_From_A_Distro.md) get indexed too, but this is very untested. If you run into bugs, please [file an issue](https://github.com/PickleHik3/termux-launcher/issues).
- There's a “Get GUI apps” setup screen to help you get started.
- Copy/paste works across Android, the terminal, and the display.
- [Mouse mode](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/X11_Display.md#every-day) gives you a touchpad in place of the keyboard, with gestures for scrolling, right-clicking, and switching windows.

I'd recommend running individual GUI apps instead of a full desktop environment, though you can do either.

### Finding your way around

There's a [new onboarding tour](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Learn_The_Launcher.md#the-tour). Even if you've been using the launcher for a while, I'd recommend going through it. You can replay it any time from Settings → About & help.

Try the new extra-keys layout when it asks, too. Your previous layout is saved as “Before the update” under Settings → Keyboard → Terminal extra keys → Presets, so you can always go back. There's also an App drawer extra key now.

There's also an [in-app help overlay and searchable user guide](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Learn_The_Launcher.md#get-help). A bit WIP, but about 90% there. If I keep refining it, this update will take another 2 weeks. The help overlay uses drawn diagrams now, in place of the video clips.

### Moving things around, and making them look nicer

Each page's corner tab and the terminal's long-press menu now open one Appearance editor for Wallpaper, Look, Layout, and Icon pack.

The [Layout editor](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Layout_And_Full_Screen.md) lets you arrange the status bar, apps row, and extra keys, and adjust sizes. Portrait and landscape have separate arrangements; Home, Terminal, and Display share them. You can edit the other orientation without physically turning your phone. On a tablet in landscape, the controls sit beside the preview.

A new [Style setting](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Layout_And_Full_Screen.md#style-corners-and-margin) makes the whole chrome either one Docked glass frame or Floating cards with space around them. The status bar can now be hidden like the other bars, the A–Z index can shrink to a small pull tab, and [minimal mode](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Layout_And_Full_Screen.md#minimal-mode) has its own layout.

“Surface editor” is now called [Appearance](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Look_And_Themes.md), and has been reworked around a live preview. Choose a Look or use the vertical sliders to customise blur, tint, grain, opacity, margins, and corners across the launcher or for an individual surface. One Done button applies your changes across all four tabs; reopening within 30 minutes takes you back to where you left off.

Preview icon packs against your own dock, choose custom app icons, or apply the pack only to pinned apps. The Wallpaper tab previews Home and Lock separately and keeps your five most recent wallpapers handy.

While I was there:

- Improved the in-app keyboard's default colours. Tbh, forgot about this one for a long time.
- Simplified the keyboard colour customisation menu. Hopefully simpler now, lmk.
- Reworked the app-wide light theme. Another thing I forgot about because, obviously, I'm living on the dark side.
- Added fancier glass effects on supported devices. Glassier glass.
- Polished the flip clock theme to fit the Material look better, and made the expanded clock fit the space available.
- Terminal contrast presets now change the terminal colours independently of the surrounding glass, while respecting your custom `colors.properties`.
- Added [retro terminal effects](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Look_And_Themes.md#cursor-trail-and-terminal-effect): CRT, green or amber CRT, and TFT. They cover the whole launcher, including its bars, keyboard, and popups.
- The [cursor trail](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#cursor-trail) has styles now: Comet, Motion blur, Railgun, Torpedo, and Pixie dust.
- Reorganised Settings into focused pages with clearer labels and more room for large text. On-device AI has moved to the top level of Settings.
- Right-to-left languages are supported, touch targets are larger, the Layout editor works with a screen reader or a keyboard, and there's one master switch for haptic feedback.

There are [floating and split keyboards](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Keyboard.md#docked-floating-or-split) now, plus [pressed-key popups](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Keyboard.md#typing-and-feedback) with hints for the characters you can swipe towards. The keyboard's settings cog opens the launcher's settings, and the font picker can browse internal storage.

### tlstore, and my new favourite TUI

I'm introducing [tlstore](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Tlstore.md), which ships with Termux Launcher and installs some of my favourite terminal apps and configs, patched to work here. Run `tlstore` to browse:

- fastfetch with animated GIF support
- Claude Code, Codex, and OpenCode
- sigye
- dawn
- btop, which requires the launcher's [Shizuku integration](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Shizuku.md#setting-it-up)
- my shell config

tlstore asks before updating itself, and you can [go back a version](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Tlstore.md#going-back-a-version) or hold one with `tlstore rollback`, `hold`, and `unhold`.

If there's something you'd like that isn't available through Termux's `pkg`, put in a request on the [tlstore issue tracker](https://github.com/PickleHik3/tlstore/issues). The [build recipes and patches](https://github.com/PickleHik3/tlstore) are there too, if you want to vet the binaries.

A special mention for [dawn](https://github.com/andrewmd5/dawn), my new most beloved TUI app. It's a writing/note-taking app with live Markdown rendering, including headings that actually change size as you type.

Termux Launcher now supports [kitty's OSC 66 text sizing](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#text-sizing-osc-66), so terminal apps can draw big and small text. Dawn is a very nice excuse to try it.

In the tlstore build of dawn, if your phone supports local LLMs and you've [downloaded a model and picked it in the Model centre](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/On_Device_AI.md#model-centre), press `Ctrl+/` to open a completely local assistant for the note you're working on. It can also generate a title from the contents, and with an EmbeddingGemma model installed, dawn finds notes by meaning rather than by the words you typed.

### More things your phone can do locally

- Added [speech-to-text](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Voice_Input.md) with Whisper and Parakeet, with Silero detecting when you're speaking. Set it up under Settings → Keyboard → Voice input.
- Added optional [local LLM cleanup](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Voice_Input.md#cleanup) for dictated text. You can undo the cleanup if it gets creative. Cleanup offers Light for careful corrections and Polished for paragraphs and lists.
- The [dictation card](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Voice_Input.md#the-dictation-card) has been redesigned, and listening stops after 5 seconds of silence by default.
- Added [text-to-speech with KittenTTS](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Text_To_Speech.md#quick-start). Install the voice model, then select terminal text and tap Read aloud, or use `tai speak` from the shell. Read aloud opens a reading card that marks the sentence being spoken and can pause and resume. `tai speak` can read piped text sentence by sentence as it arrives. English only for now.
- [App categorisation](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Settings.md#apps) can use your local LLM too. If your phone can't run one, copy the prompt into something like ChatGPT, then bring the answer back through the persistent notification. You'll need to run it again after installing more apps.
- TAI is now called [On-device AI](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/On_Device_AI.md) in Settings, grouped into Runtime, Models & voice, and Developer access. The `tai` command is unchanged.
- Reworked the [Model centre](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/On_Device_AI.md#model-centre) so you can choose a model for each function, see which functions use an installed model, and get recommendations for your phone. Licences are shown before a download, and installed models have a Delete button.
- Added a [“Models for this phone”](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/On_Device_AI.md#models-for-this-phone) setup card, [remote models](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/On_Device_AI.md#remote-model) using your own provider key, and a Hugging Face token prompt when a gated model is refused.
- Added [benchmarks](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/On_Device_AI_Models.md#benchmark): one dial per test and a ranked list, with guards for battery and heat. A “Your features” check measures each feature's real workload on your phone, and the Model centre uses the results. `tai benchmark` runs it from the shell.
- Added [image generation](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/On_Device_AI_Models.md#image-generation) from the Model centre or `tai image`, with Hugging Face and folder import.
- Added a Memory limits setting with Relaxed and Unrestricted options, and fixed issues with the runtime's limits and context window.

### The terminal itself

You can now enable [automatic tiling](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Panes_Windows_And_Sessions.md#automatic-layouts) under Settings → Terminal → Sessions and panes, then use `Ctrl+Alt+Return` to open another pane. There's also an option to grow the focused pane while the others shrink aside, similar to focus.nvim.

Panes got some polish of their own: [resize a split by dragging its divider](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Panes_Windows_And_Sessions.md#resize-panes-and-text), [swap a pane with its neighbour with a two-finger flick](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Panes_Windows_And_Sessions.md#swap-panes-with-a-two-finger-flick), and a split pane's corner tab holds only Close, Move, and Maximise.

[Touch interactions](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Touch_Links_And_Clipboard.md#touch-works-like-a-mouse) have changed a little: a short long-press (ikik) acts as a mouse press in apps that support it. Hold and drag to resize things in herdr or nvim, for example. Keep holding through the second buzz to get the usual Copy/Paste menu. Mouse clicks now carry Ctrl, Alt, and Shift.

A few more everyday improvements:

- Added [clipboard history](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Touch_Links_And_Clipboard.md#clipboard-history). Swipe down-left on Ctrl to bring it up, tap an entry to paste, or pin something to keep it across restarts.
- Terminal apps fill out towards the inner rim of rounded panes now, so you don't get that square-terminal-inside-a-rounded-border ugliness.
- The [cursor trail](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#cursor-trail) stays inside its own pane.
- Redesigned the [sessions browser](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Panes_Windows_And_Sessions.md#sessions) to match the rest of the app.
- Improved [URL detection](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Touch_Links_And_Clipboard.md#links). Tapping a plain URL or a hyperlink now shows the same Copy/Open strip, including inside apps that handle mouse clicks.
- Fixed more [kitty graphics](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#kitty-graphics-tier-2) behaviour, including images sent by file, as used by some Neovim plugins.
- Terminal programs can use [kitty's extended clipboard](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#extended-clipboard-osc-5522), and buttons in [OSC 99 notifications](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#notifications-osc-99-progress-osc-94-and-the-clipboard-osc-52) become Android notification actions.
- [`launcherctl`](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/LauncherCtl.md) gained vibrate, torch, battery, volume, toast, wallpaper, and `notify --close`, and its clipboard copy works while the launcher is off screen.
- [Notifications](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Notifications.md): pinned cards are tinted and can be swiped away with undo, and an opt-in [per-app history](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Notifications.md#notification-history) lets shell agents read what arrived.
- Closing a pane now stops the programs it was running instead of leaving them behind in the background.

### A few more fixes

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

- Nix: GUI apps installed in your Nix profile appear in the drawer too. [Setup guidance](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Nix_Package_Management.md) points you towards `home.nix` and nixpkgs. Nix releases are tagged `nix-v1.0.0` from now on.
- VAJ: GUI setup offers both the edition's own packages and proot. The native package option leaves out the browser because the repo doesn't carry one. VAJ releases are tagged `vaj-v1.0.0` from now on.

### Thank you

I also want to give the projects behind all this a proper thank you: Termux, Termux:Monet and
TEL for the foundations; Termux:X11 and Unexpected Keyboard for two enormous parts of the app;
kitty for the terminal work; Noctalia and herdr for the templates and detection rules. Ghostty,
Hyprland and focus.nvim gave me plenty of ideas too. And thanks to the speech-model and LiteRT
contributors, and everyone making the tools I've been enjoying through tlstore.

I've linked the projects and described the adaptations in the
[credits and notices](https://github.com/PickleHik3/termux-launcher/blob/dev/THIRD_PARTY_NOTICES.md).
