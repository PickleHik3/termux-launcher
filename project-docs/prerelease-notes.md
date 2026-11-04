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

### Glass
- Settings › Battery & performance has a Live blur switch, off by default: with it on, the glass on the dock, floating windows and widgets blurs whatever moves behind it. It needs Android 13 or newer, uses more battery, and stands down under Battery saver.
- A floating terminal window's glass shows what is really beneath it, and widgets' glass shows what is drawn under them, with Live blur on.
- The drawer, command palette, terminal sheet and menus blur more smoothly, and glass on glass now reads as glass.
- On Android 8 to 11, the drawer, palette, sheets and menus blur what is behind them again.
- Battery & performance moved to the Settings root, under System, and every row on it has a new icon.

### App drawer
- The drawer's frost no longer flickers while it opens.
- The dock comes down with the drawer on a slow close, instead of hanging over the keyboard and dropping at the end.
- The open drawer stands a small gap off the search keyboard, and with a Floating dock its dim runs around the keyboard's card.
- A Home press clears the drawer's search and scroll.
- An app's long-press menu shows four shortcuts, then More shortcuts.
- Search over a running Linux display raises the launcher's keyboard, and the drawer's keyboard choice reads Use system keyboard.
- Pulling on an open folder's empty space collapses the folder.

### Command palette
- The command palette follows the Menus and dialogs corner radius, has a taller filter field, and wears Material 3 colours and type.
- The palette's sheet matches the keyboard's tint.

### Dock
- Under Beads, each A–Z letter has its own bead that lifts under your finger, on Floating and Docked.
- Drag the Drawer and command palette tiles to any column; the apps make way as you drag, and dropping on the other tile swaps them.
- One Icon size, S to XL, sets the icons on Home and the dock together: it is on the Home card and first on the Dock card, and steps the pinned apps cannot fit are greyed out.
- Every Home page shows Home's dock, so swiping between Home pages never swaps or resizes it.
- A long press held still no longer unpins an app.
- On a phone in landscape, Home's dock stands as a side bar on the long edge.

### Home
- Home in landscape keeps the portrait grid and widget arrangement instead of reflowing, and turning the phone no longer rewrites your widget layout.
- Media widgets are redrawn: the upright 2x2 is a polaroid, and the two-row cards put art, words, progress and controls in order.
- Home widgets no longer snap when the notification panel's keyboard comes and goes.
- Home and the Workspace turn side by side as you swipe across the seam, with the status bar, window bar and keyboard strip changing dress under your finger.
- The Home key goes to the first Home page while Start in is the Terminal or a Display page.
- Home keeps its widgets and icons through trips to Terminal only, the Workspace or the display.

### Workspace pages
- Linux apps open on their own Display page; each keeps its own picture or card, and its bar shows one chip per open app (#192).
- Edit pages lists every Display page in wall order and removes it, stopping its display (#193); the Terminal page can stand anywhere in the order, and any page can be Set as home.
- The windows beside the one in front slide in live while you swipe (Settings › Live windows while swiping, on by default).
- Fast swipes between terminal windows carry through smoothly, at the speed you let go.

### Terminal
- A window flooded with output, a kitty image stream included, no longer freezes the launcher (#208).
- Contrast changes the terminal live, also with your own colour scheme, and appears on the Workspace card beside Global's.
- Dragging a split divider or a floating window's corner resizes its panes when your finger pauses, with a size read-out; a divider settles on the character grid and lifts without a stall.
- Panes can have a border colour of your own, a focus colour and a steady glow: Appearance › Terminal › Pane borders (#206).
- Lazy mode no longer leaves a pane showing stale output until you resize it.
- Programs that ask for focus events, such as tmux and vim, hear when a pane gains or loses focus, and neovim finds the terminal's colour and clipboard support.
- A full-screen program keeps its top when its pane resizes, and the cursor trail snaps to a pane's new size instead of sliding.
- A full image store drops older unplaced pictures before it refuses a new one.
- The More sheet opens as Home's bottom sheet, and holds Cursor trail, Effect, Split gap, the font and Wallpaper.
- A short sound ducks the music that is playing instead of pausing it (#179).
- The Workspace terminal stands a little off its bars, and its padding favours rows.

### Terminal effects
- The CRT effect is richer and sharper: colour split, a dark tube face with a vignette, a thinner bezel with depth, a glow on bright text only, and a solid bezel in your theme's colour.
- The CRT reacts to the terminal: a bell tears the picture, a window powers on, focus wobbles it, and a closing window dies in snow; the Screen events and Idle animations switches turn these off, and unfocused panes can dim.
- The cursor trail has a glow halo and longer fades.
- Under a terminal effect the keyboard, extra keys row, window bar and sessions count wear a deck: push-button keys with lights, chips with LEDs, and the CPU, RAM and weather readout in a dark display window.

### Keyboard
- The strip under the pill is the docked keyboard's own glass carried below it, and wears the terminal effect.
- A physical keyboard types into the X11 display.
- Swipe the extra keys row one page either way when it has two key pages.

### Appearance editor
- Every card is rebuilt with the same sections in the same order, Material 3 Expressive controls without check marks, and sliders as horizontal rows.
- The base card is called Global; the Customization, Home and Workspace cards say what they apply to, and Workspace writes both Terminal and Display pages.
- Every glass card shows the same rows: Frost, Opacity, Tint, Colour, Corners, Depth and Edge, with Grain and Rim under More. Frost has eight steps and moves blur and grain together.
- Widgets and Sheets (the drawer, folders, menus, the command palette and notices) have cards of their own.
- Where it applies offers Everywhere, or Home pages, Terminal pages or Display pages; each kind ships its own keyboard look, lifted on Home pages and nearly clear on Display pages.
- A coloured dot after a row's name says where its value is set, and a long press on the name resets that row.
- A Look picked on a card resets the looks beneath it, so the change shows on every page it covers.
- Every Wallpaper entry opens the wallpaper picker, and Workspace › More has Wallpaper.
- Customization has a Layout list for the whole app with Cancel or Override, and Reset everything; Apps in the dock, the Dock icon pack and a Links box close the cards.
- The Home and Workspace buttons on the editor's pill slide the mini screen to that page, and page cards glow round it.
- Opening Appearance while the setup cards are showing no longer stacks the two sheets, and a launcher closed before Done no longer keeps your changes.

### Setup
- First-run setup is a set of cards for permissions, the default launcher, Linux apps, the Display, on-device AI and Learn the basics; Settings has a Setup row, a Run setup again row and a Dismissed list.
- A fresh install opens on the Clear look.

### Status bar
- The status bar's long-press menu expands or compacts it.

### Settings
- Settings › Home › Icon size reads and writes the one icon size.
- Settings › Debugging › Motion log notes page changes and resizes; `launcherctl look` reads and sets any look value, and `launcherctl layout` reads and writes a layout.

### On-device AI
- The AI runtime keeps a share of your phone's RAM free before a model load, sized to the phone, so a model that would starve the rest of the phone is refused up front.
- A model Android closed to free memory is tried again on its own once memory has recovered, and a refusal says what it skipped.

### Linux apps
- Pasting into a Linux app works for something you copied before the display opened.
- The GPU setup's cleanup removes only what it installed, not the distro's other orphans.
- A missing X11 command shows a notice instead of retrying.

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

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing exclusive to the Nix or VAJ build in this drop.
