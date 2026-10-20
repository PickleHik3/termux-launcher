<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. docs/agents/releasing.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.1.0.

## New in this drop
<!-- Only what this drop adds over the one before it; replace the whole list at every drop. -->

- Glass panes react as they move: when a pane or a page tips, the picture through it shifts and the light along its edge follows, and a press no longer ripples the glass; at rest the glass looks as before (Android 13 and up).

## Experimental
<!-- What is on pre and not yet in a stable, one line each; name the pull request or branch it came from,
and end the line with its status in an HTML comment: ready, stays: P2, P6 (the checks still failing)
or drop: reason (docs/agents/releasing.md, "Promoting from pre to a stable"). -->
These may change or be dropped before they reach a stable release.

- Settings › Backup & restore saves your launcher settings and config files to one file, and your downloaded AI models to a folder, and restores both on this phone or a new one (#40). <!-- stays: P1, P2 -->
- Pressing a pinned app on a glass dock, or a glass card in a sheet, lights the glass under your finger for a moment (#75). <!-- stays: P1, P2 -->
- Glass edges catch the wallpaper's own light instead of a white line, brightest at the top left, on every glass surface on Android 13 and up (glass plan 2026-10-16, Round 1, always on). <!-- stays: P1, P2 -->
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
- Glass panes react as they move: when a pane or a page tips, the picture through it shifts and the light along its edge follows, and a press no longer ripples the glass; at rest the glass looks as before (Android 13 and up). <!-- stays: P1, P2, P7 (no off switch: Living glass is always on, its setting hidden) -->

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing is exclusive to an edition.
