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

### Terminal
- Sound keeps playing after you choose Exit in the notification or close your last shell: the launcher's sound server stays up, and one that stopped answering or lost its socket is replaced the next time a terminal or Linux app opens, instead of leaving you without audio until a reboot.
- A long press on a word no longer takes the full stop, comma or other punctuation after it: pressing on a path at the end of a sentence selects just the path.
- Tapping a terminal widget on Home brings up the keyboard (#178).

### Linux apps
- Upgrading from 1.3.0-pre.3 with the Display switch off no longer starts a display server, and its window manager, every time the launcher starts. If you upgraded already and did not want a display, turn off Settings › Linux apps › Start the display with the launcher.

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

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing exclusive to the Nix or VAJ build in this drop.
