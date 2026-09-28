<!-- Use the opening section as the GitHub release body. Keep the full write-up below in this same file. Before publishing, replace /blob/dev/ links with /blob/<release-tag>/ links so they stay tied to the shipped version. -->

25 days since the last update?! And I've been aiming to release this version for the last 3 weeks, smh. I've got ADHD and just-one-more-feature disease.

This was supposed to be 0.2.40. Somewhere along the way it became 1.0. The launcher I'd always pictured in my head is finally here. I should probably stop adding features now.

There's a lot in this one. Here's the short version:

- [A dedicated Home page](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Usage.md#move-between-the-terminal-and-the-widget-grid) for widgets, beside the terminal. The old pull-down widget pane is gone; swipe along the status bar to switch pages.
- [An in-app X11 display](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/X11_Display.md) for Linux GUI apps. Installed apps appear in the drawer and open with a tap. Proot apps work too, but that part is still very untested.
- [tlstore](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Tlstore.md), my collection of terminal goodies: fastfetch with GIFs, dawn, Claude Code, OpenCode, sigye, btop, and my shell config. Run `tlstore` to browse.
- [Local speech-to-text](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Voice_Input.md), optional cleanup with a local LLM, and [text-to-speech](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Text_To_Speech.md). Once the models are downloaded, these run on your phone.
- [Layout customisation](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Settings.md#layout-editor), [Appearance controls](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Settings.md#look), floating/split keyboards, and a much-needed pass over the light theme.
- [Automatic pane tiling](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Modernization.md#panes-windows-and-layouts), [big and small terminal text](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#text-sizing-osc-66), [clipboard history](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Usage.md#clipboard-history), and plenty of fixes.

If you're updating, I'd recommend taking the new tour and trying its extra-keys layout. Your old row is saved as “Before the update” under Settings → Keyboard → Extra keys → Presets. There's also [in-app help and a user guide](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Usage.md#get-help) now.

Maybe get a cup of tea for [the full write-up](https://github.com/PickleHik3/termux-launcher/blob/dev/project-docs/release-notes-v1.0.0.md#the-long-read), including the smaller changes, known rough edges, and edition notes.

<!-- End GitHub release body. -->

## The long read

This was supposed to be a much smaller update. Anyways, here's where those 25 days went.

### Home, terminal, and Linux apps

The launcher now has three pages: widgets on the left, the terminal in the middle, and an X11 display on the right. Swipe along the status bar or use the new extra keys to move between them.

[Home](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Usage.md#move-between-the-terminal-and-the-widget-grid) is a traditional home screen where you can place your widgets. The old pull-down widget pane is gone. Move and resize widgets, change the grid, and drag widgets onto another page.

The [Display page](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/X11_Display.md) runs Linux GUI apps right inside the launcher:

- Apps installed from the Termux X11 repo automatically appear in the app drawer. Tap one to start the display and open it.
- [Apps inside proot](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Linux_Apps_From_A_Distro.md) get indexed too, but this is very untested. If you run into bugs, please [file an issue](https://github.com/PickleHik3/termux-launcher/issues).
- There's a “Get GUI apps” setup screen to help you get started.
- Copy/paste works across Android, the terminal, and the display.
- [Mouse mode](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/X11_Display.md#every-day) gives you a touchpad in place of the keyboard, with gestures for scrolling, right-clicking, and switching windows.

I'd recommend running individual GUI apps instead of a full desktop environment, though you can do either.

### Finding your way around

There's a [new onboarding tour](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Getting_Started.md#3-complete-the-first-launch). Even if you've been using the launcher for a while, I'd recommend going through it.

Try the new extra-keys layout when it asks, too. Your previous layout is saved as “Before the update” under Settings → Keyboard → Extra keys → Presets, so you can always go back.

There's also an [in-app help overlay and searchable user guide](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Usage.md#get-help). A bit WIP, but about 90% there. If I keep refining it, this update will take another 2 weeks.

### Moving things around, and making them look nicer

Each page has a small corner tab with Appearance, Layout, and its own controls. The terminal's long-press menu has Appearance and Layout too.

The [Layout editor](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Settings.md#layout-editor) lets you arrange the status bar, apps row, and extra keys, and adjust sizes. Portrait and landscape have separate arrangements; Home, Terminal, and Display share them. You can edit the other orientation without physically turning your phone.

“Surface editor” is now called [Appearance](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Settings.md#look). Use the palette button for shared settings, or tap an individual surface to customise it. I'm trying to make the main settings page a bit more streamlined.

While I was there:

- Improved the in-app keyboard's default colours. Tbh, forgot about this one for a long time.
- Simplified the keyboard colour customisation menu. Hopefully simpler now, lmk.
- Reworked the app-wide light theme. Another thing I forgot about because, obviously, I'm living on the dark side.
- Added fancier glass effects on supported devices. Glassier glass.
- Polished the flip clock theme to fit the Material look better.

There are [floating and split keyboards](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Modernization.md#keyboard-types) now, plus [pressed-key popups](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Settings.md#keyboard) with hints for the characters you can swipe towards. The keyboard's settings cog opens the launcher's settings.

### tlstore, and my new favourite TUI

I'm introducing [tlstore](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Tlstore.md), which ships with Termux Launcher and installs some of my favourite terminal apps and configs, patched to work here. Run `tlstore` to browse:

- fastfetch with animated GIF support
- Claude Code and OpenCode
- sigye
- dawn
- btop, which requires the launcher's [Shizuku integration](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Shizuku.md#setting-it-up)
- my shell config

If there's something you'd like that isn't available through Termux's `pkg`, put in a request on the [tlstore issue tracker](https://github.com/PickleHik3/tlstore/issues). The [build recipes and patches](https://github.com/PickleHik3/tlstore) are there too, if you want to vet the binaries.

A special mention for [dawn](https://github.com/andrewmd5/dawn), my new most beloved TUI app. It's a writing/note-taking app with live Markdown rendering, including headings that actually change size as you type.

Termux Launcher now supports [kitty's OSC 66 text sizing](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#text-sizing-osc-66), so terminal apps can draw big and small text. Dawn is a very nice excuse to try it.

In the tlstore build of dawn, if your phone supports local LLMs and you've [downloaded and selected a default model in TAI](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Termux_AI.md#quick-start), press `Ctrl+/` to open a completely local assistant for the note you're working on. It can also generate a title from the contents.

### More things your phone can do locally

- Added [speech-to-text](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Voice_Input.md) with Whisper and Parakeet, with Silero detecting when you're speaking. Set it up under Settings → Keyboard → Voice input.
- Added optional [local LLM cleanup](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Voice_Input.md#cleanup) for dictated text. You can undo the cleanup if it gets creative.
- Added [text-to-speech with KittenTTS](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Text_To_Speech.md#quick-start). Install the voice model, then select terminal text and tap Read aloud, or use `tai speak` from the shell. English only for now.
- [App categorisation](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Settings.md#apps) can use your local LLM too. If your phone can't run one, copy the prompt into something like ChatGPT, then bring the answer back through the persistent notification. You'll need to run it again after installing more apps.
- Refined TAI and fixed issues with its limits and context window.

### The terminal itself

You can now enable [automatic tiling](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Modernization.md#panes-windows-and-layouts) under Settings → Terminal → Sessions and panes, then use `Ctrl+Alt+Return` to open another pane. There's also an option to grow the focused pane while the others shrink aside, similar to focus.nvim.

[Touch interactions](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Usage.md#touch-works-like-a-mouse) have changed a little: a short long-press (ikik) acts as a mouse press in apps that support it. Hold and drag to resize things in herdr or nvim, for example. Keep holding through the second buzz to get the usual Copy/Paste menu.

A few more everyday improvements:

- Added [clipboard history](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Launcher_Usage.md#clipboard-history). Swipe down-left on Ctrl to bring it up, tap an entry to paste, or pin something to keep it across restarts.
- Terminal apps fill out towards the inner rim of rounded panes now, so you don't get that square-terminal-inside-a-rounded-border ugliness.
- The [cursor trail](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#cursor-trail) stays inside its own pane.
- Redesigned the [sessions browser](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Modernization.md#search-and-manage-sessions) to match the rest of the app.
- Improved [URL detection](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Modernization.md#safe-hyperlinks). URLs inside TUIs are still a bit hit-or-miss, especially with something like herdr's sidebar open. If the underline doesn't look right, try scrolling a little.
- Fixed more [kitty graphics](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Terminal_Kitty_Protocols.md#kitty-graphics-tier-2) behaviour, including images sent by file, as used by some Neovim plugins.
- Closing a pane now stops the programs it was running instead of leaving them behind in the background.

### A few more fixes

- Fixed wallpaper blur controls on affected phones ([#37](https://github.com/PickleHik3/termux-launcher/issues/37)).
- Fixed fullscreen mode losing the terminal tint ([#27](https://github.com/PickleHik3/termux-launcher/issues/27)).
- Large wallpapers no longer freeze the launcher while their blur is being prepared.
- Fixed the clock shifting nearby items as the seconds changed.
- Improved widget sizing and behaviour when switching between light and dark themes.

### Editions

- Nix: GUI apps installed in your Nix profile appear in the drawer too. [Setup guidance](https://github.com/PickleHik3/termux-launcher/blob/dev/docs/en/Nix_Package_Management.md) points you towards `home.nix` and nixpkgs.
- VAJ: GUI setup offers both the edition's own packages and proot. The native package option leaves out the browser because the repo doesn't carry one.

### Thank you

I also want to give the projects behind all this a proper thank you: Termux, Termux:Monet and
TEL for the foundations; Termux:X11 and Unexpected Keyboard for two enormous parts of the app;
kitty for the terminal work; Noctalia and herdr for the templates and detection rules. Ghostty,
Hyprland and focus.nvim gave me plenty of ideas too. And thanks to the speech-model and LiteRT
contributors, and everyone making the tools I've been enjoying through tlstore.

I've linked the projects and described the adaptations in the
[credits and notices](https://github.com/PickleHik3/termux-launcher/blob/dev/THIRD_PARTY_NOTICES.md).
