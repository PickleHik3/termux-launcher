<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. docs/agents/releasing.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.2.2.

## New in this drop
<!-- Only what this drop adds over the one before it; replace the whole list at every drop. -->

This update starts your look again from the default, once: glass, colours, the status bar, the dock, the keyboard's look, the terminal font and text size, and every page's layout. Your Home pages, widgets, apps, wallpaper, extra keys and sessions stay. Set it up again in the Appearance editor.

### Appearance
- The status bar, dock, keyboard and menus look the same on every page; the Appearance editor has six cards, and the Workspace card is now part of Terminal.
- A setting you change on one card shows a reset arrow; changing Global no longer overwrites it.
- Each setting lives in one place: rows moved to the card where you see their effect, and Settings no longer repeats what the editor holds.
- Menus and dialogs wear Global's glass, and sheets that stand over other glass are frosted more so they stay readable.
- The Terminal card has its own Background: Opacity, Blur and Wallpaper dim (#287).
- Adaptive legibility, next to Contrast on the Global and Terminal cards: off, text is tuned once for the whole wallpaper; on, each item adjusts to what is behind it (#297).
- `launcherctl look` sets what the Appearance cards can; `--kind` and `--page` are gone.

### Home
- A glass page bar above the dock replaces the page dots: slide a finger along it to switch pages, with the page's name above your finger.
- A page switcher key for the extra keys opens the page bar from any page, and a Setup card offers to add it.
- Floating terminals on Home open above the keyboard and step down so no title bar is hidden; only the focused one makes room for the keyboard (#310).
- On Home the keyboard comes up over the dock instead of pushing it up (#309).
- Home comes back with its keyboard up or down as you left it.
- Home's status bar shows a chip for each floating terminal on that page (#295).
- Long-press Home › Appearance opens the Global card.
- A vertical swipe that starts on a widget opens the drawer or shade as it would anywhere on Home.

### Dock and drawer
- New installs get the Default drawer: Apps and Categories side by side, switched by a swipe or the pill at the top, reopening on the one you left. Upgrading keeps your current drawer.
- The drawer's search bar is a centred capsule, at the top or the bottom as you choose; while you scroll it folds into a round button in the corner, and a tap brings it back.
- Long-press a Linux app in the drawer and choose Hide to take it out of the drawer; bring it back from the app's settings page.
- Drawer layout settings have Icon size, Columns, and Rows for the Pages layout.
- Hold a corner of the drawer for a shortcut to its settings, or tap Edit there to rearrange it: touch an app to lift it into a folder, drag the Categories tiles into your own order, and change the grid size in place. Reset category order in Drawer layout settings puts the tiles back.
- Large icons stay sharp on dense screens and tablets.
- A folder on the dock no longer appears inside the drawer, and its apps stay listed there.
- The highlighted search result is a small glass tile around the app's icon and name.
- Uninstall in an app's long-press menu works again, from the drawer, the dock and the widget pane.
- Reorder pinned apps live: the row opens a gap where you carry an icon and the icon drops into it; hold at the row's end, or touch with a second finger, to turn the dock's page (#155).
- Drag an app from the drawer onto the dock to pin it; the drawer closes as soon as you pick it up.
- Lift a held icon by moving in any direction.
- A folder opens out of its icon.
- The drawer's app menu has Add to folder, with New folder.
- On tablets and wide screens the drawer's Categories view adds columns instead of stretching the cards.
- Touching the A–Z strip puts the drawer's keyboard away.
- The drawer keeps its icons while the app list reloads instead of going blank.
- New apps appear in the drawer and A–Z even when installed while another app was in front.

### Extra keys and keyboard
- The extra-keys bar is new: swipe between pages of keys, hold to repeat, swipe up for a key's second action, and set the key size in the editor.
- Each page can have its own extra keys, or follow Global's.
- Extra keys on Home: turn them on in the editor and they sit in a pill you can hold and move, and dock above the keyboard when it opens.
- One set of outline icons across the extra keys, the keyboard, the status bar, notices, Home's widgets, weather, the page bar and the editor's buttons; keys you saved before pick them up on their own, and Esc, Ctrl and Alt stay as words.
- Notices show an icon for what happened, and messages that used to pop up as Android toasts appear as notices too.
- The extra keys' key picker shows each action's icon instead of its name.
- Weather icons are still and two-toned, with their own icons for mainly clear, heavy rain, heavy snow and rime fog.
- A press on the keyboard's outer frame types the key beside it.
- Key bindings work from the in-app keyboard on every page.
- In every text field in the launcher, the keyboard's settings, clipboard and layout keys work, and copy, cut, paste, select all, undo and Ctrl+A, C, X, V and Z act on the field.
- Settings › Keyboard › Layouts previews the layout you are using.
- Settings groups the keyboard and extra keys under Keyboard & extra keys.
- With a hardware keyboard and the on-screen one hidden, renaming a window, the palette and find no longer bring up the on-screen keyboard.
- After switching windows, by shortcut or by tapping a window chip, typing goes to the window now on screen.

### Terminal
- A program's status (needs input, done, failed) shows on its window's pill, with a notice when it happens in a pane you are not looking at.
- When you are away from the Terminal page, bells, messages and program states arrive as Android notifications, one per window; tap one to open that pane.
- On the Fast engine, a program that wants to read the clipboard asks first, per program; Settings can reset the programs you always allowed.
- On the Fast engine, programs get the same keyboard, mouse and colour-scheme answers they get in Ghostty.
- The split divider has its own touch strip, and Inner gap sets the gap between split panes.
- In Terminal only mode, opening the app after Ctrl+D on the last shell starts a new shell instead of closing again (#276).

### Linux apps
- Settings › Linux apps has a page per app: touch mode, Scale, Graphics and the paste key's shortcut, applied when the app starts.
- Paste a picture from Android's clipboard into Linux apps.
- New shells connect to the running display without setup, and apps on the display get a proper cursor.
- XFCE is offered on the Termux route of Set up Linux apps, with typing through your keyboard and the store's apps in its menu.
- A desktop that ends is noticed, so it can start again.
- In overlay mode the extra keys and keyboard sit below the display instead of over it.
- GL apps start their graphics server on the display.
- Text typed into Linux apps arrives as normal key presses, so it works in apps that ignored it before.
- The desktop no longer disappears behind a Shader wallpaper.
- Resize display shrinks the display window.
- If Android stops the launcher's programs, a notice explains why, with a fix through Shizuku and a Restore in Diagnostics.

### Wallpaper
- Make a moving wallpaper from a photo: the Shader page's From a photo turns it into a depth scene, with the depth made on the phone.

### Status bar
- A battery chip can sit beside CPU and memory; switch it on in Settings or the Appearance editor.
- When the readings are wider than the row, the status bar shows whole chips instead of cutting one off.
- Window chips work from any page, and tapping the selected chip on Home goes to its window.

### Assistant
- Answers show their thinking in a row you can open, and a line with their speed and time.
- Messages are bubbles in your colours, and the composer says what the answer is doing, including Waiting in queue.

### Voice and on-device AI
- A graphics chip that has not been checked starts on the processor; Check the GPU, among the Functions, tests it, and the result survives updates.
- Re-sort apps offers the on-device route on lower-end phones and stays usable on low memory.
- A voice's Voice settings opens the Read aloud page.

### Setup
- Setup cards come back only for cards you have never seen.
- Skip keeps the choices made so far; the wizard waits until the first install has finished, and asks for wallpaper access only when your look needs it.

### Fixes
- Menus, key bubbles and the A–Z strip appear in the right place in split screen.
- Two-option toggles stay on one line at large font sizes.
- Weather keeps the last good forecast when a refresh fails.
- Linux sound starts again after its server was killed.
- Export logs saves its report to a file.
- The Settings search field stays when you type the first letter.

## Experimental
<!-- What is on pre and not yet in a stable, one line each; name the pull request or branch it came from,
and end the line with its status in an HTML comment: ready, stays: P2, P6 (the checks still failing)
or drop: reason (docs/agents/releasing.md, "Promoting from pre to a stable"). -->
These may change or be dropped before they reach a stable release.

- **Terminal** (v2.0.0-pre.1):
  - dialog and whiptail screens, such as termux-change-repo, are readable: the red and blue text on their white panels now stands out in every Look and at every Contrast level. <!-- stays: P1, P2 -->
  - Grey and normal text stays readable on a glass terminal over a bright wallpaper: the glass evens out the wallpaper under the text. It is on by default; Settings › Debugging › Even out glass turns it off so you can compare. <!-- stays: P1, P2 -->
  - Switching terminal windows with Fancier Glass on no longer flashes the bare wallpaper behind the window that is leaving. <!-- stays: P1, P2 -->
  - The terminal's Opacity stays yours: at 0% the glass is exactly as clear as you set it, with no evening-out, and below the default it evens out less. <!-- stays: P1, P2 -->
  - A terminal window whose shell Android kills (its child-process limit, often hit with a Linux desktop on the display) stays open with "Process completed (signal 9)" instead of vanishing; Enter closes it, and a notice says how to lift the limit. <!-- stays: P1, P2 -->
- **Home** (v2.0.0-pre.1):
  - Bare widgets draw nothing behind themselves: the soft dim behind a Bare clock is gone, and only its ink changes tone to stay readable. <!-- stays: P1, P2 -->
  - Swiping between Home pages moves both pages together under your finger, in the transition you picked, instead of the next page appearing only after you let go. <!-- stays: P1, P2 -->
  - The Home card has a Transition row of five glyph tiles (Slide, Depth, Fade, Carousel and Stack), and Settings › General › Page transition now opens it. <!-- stays: P1, P2 -->
- **Appearance editor** (v2.0.0-pre.1):
  - Scrolling a card's content moves the card sheet like a standard bottom sheet: up grows it to half and full height, down at the top of the content shrinks it a stop. Sliders and strips still drag sideways. <!-- stays: P1, P2 -->
  - A swipe on a glass slider in the Appearance sheet moves it straight away, without tapping it first. <!-- stays: P1, P2 -->
  - Change wallpaper opens its picker above the card sheet, so Choose photo can be tapped again. <!-- stays: P1, P2 -->
- **Launcher** (v2.0.0-pre.1):
  - Opening or closing a window behind the one you are looking at no longer makes the wallpaper jump; it glides to its new place. <!-- stays: P1, P2 -->
- **Linux apps** (v2.0.0-pre.1):
  - A display you start from a shell with termux-x11 gets a window manager, so apps open with window frames instead of unmanaged at their own size. <!-- stays: P1, P2 -->
  - The display starts on its own again after it was killed, instead of staying down until you cleared it by hand. <!-- stays: P1, P2 -->
  - On the apt edition, installing termux-x11-nightly no longer replaces the launcher's termux-x11 command. <!-- stays: P1, P2 -->
  - Linux apps play sound in a distro you installed while the launcher was running, without an "Access denied" from the sound server. <!-- stays: P1, P2 -->
  - Tapping an app that is already open, in the drawer or Home, raises its window instead of starting a second copy, and the Display chip's menu lists the other open windows under Switch to. <!-- stays: P1, P2 -->
  - The Linux display now works on 32-bit phones and with the 32-bit APK; before, it could not start there at all. <!-- stays: P1, P2 -->
  - When the display can't start, its window now says why, with a link to Startup and support, instead of showing Start display again as if nothing happened. <!-- stays: P1, P2 -->
  - Settings › Linux apps says Linux apps are set up only once the setup command has finished; after copying it, the row tells you to run it. <!-- stays: P1, P2 -->
- Settings › About & help has an Ask on Discord row, Documentation opens the website guide, and the help panel's support link goes to Discord (#58). <!-- ready -->
- Settings › Backup & restore saves your launcher settings and config files to one file, and your downloaded AI models to a folder, and restores both on this phone or a new one (#40). <!-- stays: P1, P2 -->
- Pressing a pinned app on a glass dock, or a glass card in a sheet, lights the glass under your finger for a moment (#75). <!-- stays: P1, P2 -->
- Glass edges catch the wallpaper's own light instead of a white line, brightest at the top left, on every glass surface on Android 13 and up (glass plan 2026-10-16, Round 1, always on). <!-- stays: P1, P2 -->
- Scripts and agents in a pane can place, size, inspect, wait on and close panes and windows through launcherctl: `pane open --from --direction --size`, `pane resize`, `pane wait`, `window list|focus|close`, and `launcherctl <group> --help` (#66). <!-- ready -->
- Pictures travel through the terminal clipboard: programs that speak kitty's clipboard protocol (OSC 5522) can read a picture from Android's clipboard and copy one to it, pasting a picture into a shell or an agent pastes it as a file path under `$PREFIX/tmp/clipboard`, and pictures inserted from the keyboard land the same way (#61). <!-- ready -->
- A screenshot you just took can be pasted from the keyboard's paste key, which shows a photo while it or a copied picture is waiting; plain paste (paste with a modifier) keeps the clipboard. Screenshots need the storage permission (Settings › Permissions & services › Files and media, or `termux-setup-storage`). <!-- stays: P2 -->
- Terminal programs that ask for touch events receive your taps, long presses and swipes. <!-- stays: P1 -->
- Settings › Keyboard › Layouts lets you edit, reset and create keyboard layouts, each row sliding its buttons open beneath it. <!-- stays: P1, P2 -->
- Pinned app icons go bigger: the Icon size slider reaches further on both dock styles, and icon packs draw as big as stock icons. <!-- stays: P3 (#71) -->
- Pinned apps fill the dock when it holds nothing else: the icons grow into the bar, and the bar sheds some of the space above and below them, at every Icon size. <!-- stays: P2, P3 (#71) -->
- The Appearance editor's Cursor trail menu has an Off entry, and the Mouse mode key reads out the state it switched to. <!-- stays: P1, P2 -->
- The window bar names terminal-browser in full, with a globe. <!-- stays: P1 -->
- Dictation cleanup now writes what you meant from the whole passage, fixing misheard words and broken sentences rather than only dropping fillers; Polished keeps its paragraphs and lists. <!-- stays: P1, P2 -->
- Dictation cleanup is about twice as fast and leaves what you said alone where nothing needs fixing: "uh" and "um" are always removed, only sentences that need it are rewritten, it no longer turns other languages into English, and it keeps your spelling. <!-- stays: P1, P2 -->
- Cleaned-up dictation is ready a moment after you stop: on phones that run cleanup on the graphics chip (or with a remote model), it tidies your words at natural pauses while you talk. <!-- stays: P1, P2 -->
- With a multilingual Whisper model, Voice language **Auto-detect** listens for the language you speak instead of assuming your keyboard's, and the dictation card names it when it differs. <!-- stays: P1, P2 -->
- Tap the text on the dictation card to fix a word with the keyboard before you insert or copy it, and the words the cleanup fixed stay highlighted while you edit. <!-- stays: P1, P2 -->
- On-device models load with less free memory than before, and one that would fill the phone's memory is stopped instead of freezing it. <!-- stays: P1, P2 -->
- Settings › Keyboard › Adaptive touch accuracy learns where your taps land, the space bar included, and pauses itself if typing gets less accurate. <!-- stays: P1, P2 (pass bar on pong, three emulator checks) -->
- Look › Custom's glass controls work from any Look: Frost (clear to frosted), Tint with Hue, Vibrancy, Edge and the keyboard's Keys, with Custom remembering your setup; the wallpaper can change under the editor and the X11 display (#82). <!-- stays: P1, P2 -->
- The welcome message in a new terminal is a rounded card in your wallpaper's colours, with Apps, Packages and Setup sections that fit the pane's width. <!-- stays: P1, P2 -->
- The Appearance editor's one card sheet, per-screen glass and Elements list replace the old Layout and Look pages (editor redesign, 2026-10-26). <!-- stays: P1, P2 (device checks owed) -->
- Peek dock style, page overview and live layout drag (peek-dock and overview, 2026-10-25). <!-- stays: P1, P2 (keyboard-up and overview checks owed) -->
- The extra keys editor (#164) and Auto keyboard (2026-10-25). <!-- stays: P1, P2 -->
- Launcher sound with Linux media controls (2026-10-25). <!-- stays: P1, P2 (visual and Sound-off checks owed) -->
- Niagara-style A–Z with Shown, Faint and Pull tab, and the long-press popup (editor redesign). <!-- stays: P1, P2 -->
- Use as (Home screen or Terminal only) and the welcome choice (editor redesign). <!-- stays: P1, P2 -->
- Home's widget family, dock styles and the Home ⇄ Workspace crossing (widget redesign, chrome unification, wall rework). <!-- stays: P1, P2 -->
- The graphics benchmark and Graphics acceleration row on Linux apps (GPU bench, 2026-10-24). <!-- stays: P1, P2 -->
- Terminal text on glass evens out the wallpaper under it and keeps dialog's red and blue readable, with Even out glass in Settings › Debugging to compare (legibility waves 1 and 2, 2026-10-27). <!-- stays: P1, P2 (pong frame time and split-window resize checks owed) -->
- The Home card's Transition row and the card sheet that follows a scroll of its content (2026-10-27). <!-- stays: P1, P2 (five-tile row sizing and sideways drags owed) -->
- The Linux display's upstream-parity work: window manager for a shell-started display, auto-start after a killed display, termux-x11-nightly diversion, raising an open app's window and Switch to on the Display chip (2026-10-27). <!-- stays: P1, P2 (drawer raise, Switch to, apt divert and new-distro sound owed) -->
- Live blur: the Battery & performance switch that makes the dock's, floating windows' and widgets' glass blur what moves behind them (live glass waves 1 to 4, v2.0.0-pre.3). <!-- stays: P1 -->
- Panels that blur through one shared picture: the drawer, command palette, terminal sheet and menus, with a software picture on Android 8 to 11 (backdrop panels 2b, v2.0.0-pre.3). <!-- stays: P1 -->
- Pages as cells: Display pages for Linux apps, the Terminal page anywhere in the order, Set as home on any page, a Look per page kind, and live windows while swiping (page model, wall cells and Display pages, #168 to #193, v2.0.0-pre.3). <!-- stays: P1 -->
- One layout per page kind, one Icon size, and Home in landscape the Launcher3 way (ADR 0020, v2.0.0-pre.3). <!-- stays: P1 -->
- The Appearance cards overhaul: Global, Home and Workspace cards, the same glass rows everywhere, Frost's eight steps and the Material 3 Expressive controls (appearance overhaul and look grid, v2.0.0-pre.3). <!-- stays: P1 -->
- The CRT effect's screen events and the deck look for the keyboard, window bar, extra keys and sessions chip (agsl effects waves 1 to 8e, v2.0.0-pre.3). <!-- stays: P1 -->
- Beaded A–Z and dock tiles you drag to any column (A–Z letter beads and dock tiles by column, v2.0.0-pre.3). <!-- stays: P1 -->
- First-run setup cards and the Setup row (setup wizard waves 1 to 5, v2.0.0-pre.3). <!-- stays: P1 -->
- Pane borders, and panes that resize when your finger pauses on a divider (#206, ADR 0019, v2.0.0-pre.3). <!-- stays: P1 -->
- Media widget redraw and the status bar's long-press fold (widget cards and status bar menu, v2.0.0-pre.3). <!-- stays: P1 -->
- Motion log and `launcherctl` look and layout (motion invariants, ADR 0017 and ADR 0020, v2.0.0-pre.3). <!-- stays: P1 -->
- The Fast terminal engine, with kitty graphics, sixel and the crash fallback (libghostty-vt, ADR 0026, v2.0.0-pre.5). <!-- stays: P1 -->
- The Assistant page and one Workspace for Terminal, Display and Assistant (ADR 0025, v2.0.0-pre.5). <!-- stays: P1 -->
- Video and Shader wallpapers (v2.0.0-pre.5). <!-- stays: P1 -->
- The camera-aware Floating status bar and See through (v2.0.0-pre.5). <!-- stays: P1 -->
- The one look and the six Appearance cards, with Adaptive legibility (ADR 0031, #281–#297, v2.0.0-pre.6). <!-- stays: P1 -->
- The page bar and the page switcher key (#298, v2.0.0-pre.6). <!-- stays: P1 -->
- Floating terminals that make room for the keyboard on Home, and the keyboard over Home's dock (#309, #310, v2.0.0-pre.6). <!-- stays: P1 -->
- The new extra-keys bar, extra keys per page and on Home (v2.0.0-pre.6). <!-- stays: P1 -->
- The outline icon family across keys, keyboard, status bar, notices and widgets (v2.0.0-pre.6). <!-- stays: P1 -->
- The Default drawer, its layout settings and Edit mode (v2.0.0-pre.6). <!-- stays: P1 -->
- Live dock reorder and pinning from the drawer (#155, v2.0.0-pre.6). <!-- stays: P1 -->
- Program status, terminal notifications and Fast-engine clipboard consent (v2.0.0-pre.6). <!-- stays: P1 -->
- Per-app settings for Linux apps, picture paste and XFCE on the Termux route (v2.0.0-pre.6). <!-- stays: P1 -->
- Moving wallpapers from a photo (#277, v2.0.0-pre.6). <!-- stays: P1 -->

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing exclusive to the Nix or VAJ build in this drop.
