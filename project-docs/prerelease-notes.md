<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. docs/agents/releasing.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.2.1.

## New in this drop
<!-- Only what this drop adds over the one before it; replace the whole list at every drop. -->

### Launcher
- Home and Workspace are one wall of Home pages and windows: swipe from Home into the Workspace and back, and the glass edge, the dock and the keyboard cross with the page in one motion.
- The Workspace can be empty, and Terminal and Display open on demand as windows of the row; reorder windows by dragging their chips along the window bar, and move panes between windows.
- A shell can float over a Home page with a glass titlebar, minimise to a chip, and wait in the sessions drawer under Home and Saved sessions.
- Settings › General › Launcher › Use as picks Home screen or Terminal only, and the welcome screen asks which you want.
- Pinch a Home page, or use Edit pages, to see Home pages and windows as cards; delete with Undo, and add a page, terminal or display.
- Long-pressing Home opens an action sheet that can lock the layout and reaches past the navigation pill.
- Long-press an app for the App menu: grouped rows with an arrow, shortcuts you can pin to Home, and Remove or Uninstall by dragging its icon.
- Drag icons between Home and the dock, and from the drawer onto Home; apps can pin their shortcuts and widgets to Home.
- The drawer keeps its own keyboard, hides it as you scroll, and walks its search results with the arrow keys; physical keys type into an open search wherever focus is.
- Settings reopen on the page you left for three minutes, then at the top, and open full screen.
- Screen readers can reach the command palette and the App menu, and a dock tile opens the command palette.
- Text on glass and over the wallpaper stays readable: terminal and dock text hold a contrast floor that follows your level, and faint text fades toward its ground.
- A one-time hint points new users to the theme; the welcome tour stays off on pre-release builds.
- The status row flows around the camera and clears the screen's rounded corners (#163).
- Caps, chips and the Home-locked notice fit at font scale 1.3.
- The calendar's today disc is centred on its number, one size every day, and keeps its contrast on dark glass.
- A pull down on Suggested's empty pane closes the folder, not the drawer.
- Home keeps its grid and the terminal widget its prompt under the system keyboard.

### Appearance editor
- One editor for everything you see: tap the thing you want to change, on a card sheet that rests at peek, half or full height and stays open.
- A Global card holds the wallpaper tile, the Look and its colours; each screen card (Home, Workspace, Status bar, Dock, Keyboard, Terminal) reads its Look from its own half and follows the page you swipe to.
- Each screen can have its own glass, with Frost, Tint, Hue, Vibrancy, Edge and Keys controls on any Look, and Custom remembers your setup (#82).
- Cards use glyphs and Match rows, and the Dock card has a Bars list; the screen cards have an Elements list for hidden items, with Undo and Reset.
- Layout drags happen live in the preview, with slim drop markers and a lifted pose for the piece you carry.
- The Alignment control is gone, and Edge colours follow Wallpaper colors.
- Sliders and rows fit on one line where they can.

### Home
- Widgets are one redesigned family: a Clock with eight faces and time zones, one Calendar, one Device widget, Glance, Notes, Weather with a place you set, Media, Tasks and a narrower Notifications widget.
- Clock widgets have Show date and Show next alarm toggles, and the flip clock stacks AM and PM; Clock and Weather can go Bare.
- Widgets wear the Look's glass and corners, with one switch and per-widget overrides, and the widget picker names each design and stays clear of the dock.
- Widgets resize to per-kind spans, and edit mode reaches every widget and explains refused drops.

### Dock
- Home's dock has styles you pick: Glass tray, Lens beads, Taskbar, Prompt line, Wall strip and the new-install default, Peek, which has a live reflection under the keyboard and a Floating variant.
- Prompt line and Taskbar are toggles (Search field, Drawer tile) that work with any dock design.
- A side dock on a landscape phone holds every pin and scrolls the rest, keeping the most-used apps in view (#146).
- Pinned apps never run Unpin from a still long press.

### Keyboard
- An extra keys editor (#164) lets you edit pages, rows and keys with a picker and Undo.
- Auto keyboard waits until Linux apps have what it needs.
- A long hold on the keyboard's frame, its key gaps or a key's edge opens the keyboard popup (Edit, Hide, Docked or Floating); the keyboard keeps its height.
- Keyboard over display: overlay or resize, from the display's Input page.

### Terminal
- A sideways swipe on a terminal's text pages the row.
- A growing pane keeps the prompt at the bottom only while history fills the new rows.

### Linux apps
- Settings › Linux apps › Graphics acceleration has a benchmark that runs on its own page with one confirmation, and skips the Vulkan wrapper where the phone's driver is too old for it.
- GPU setup follows Termux's own package manager and never asks.
- The display is a window of the row and holds the keyboard and focus only while it is on screen.
- Settings › Linux apps › Arch Linux installs on arm64 phones again, from the Arch Linux ARM image; Debian and Ubuntu are unchanged.
- A Set up Linux apps guide walks through the first setup.
- Linux apps play sound: a sound server runs with the launcher, and Media controls show what is playing.

### Settings
- Settings follow Material 3 and are regrouped by the wall's halves, with search everywhere and a Motion page for the page transition and animation speed.
- Gestures: the gesture list is the one control, and quick reply has its own switch.
- Home settings gather page transitions, icons and labels, the page indicator, lock, Edit pages and folders; Start in names Terminal, Display or Home.
- Drawer settings cover search, position, reach, A–Z side and badge counts; Niagara-style A–Z has Shown, Faint and Pull tab states, and the pull tab rests beside the text.
- What's new and Restart launcher are in Settings.

## Experimental
<!-- What is on pre and not yet in a stable, one line each; name the pull request or branch it came from,
and end the line with its status in an HTML comment: ready, stays: P2, P6 (the checks still failing)
or drop: reason (docs/agents/releasing.md, "Promoting from pre to a stable"). -->
These may change or be dropped before they reach a stable release.

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

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing exclusive to the Nix or VAJ build in this drop.
