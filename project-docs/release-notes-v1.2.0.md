# v1.2.0

Glass that catches the wallpaper's light and moves with your hand, backup and restore for everything you set up, keyboard layouts you can edit, and dictation that writes what you meant.

## New

### Launcher

- Settings › Backup & restore saves your launcher settings and config files to one file, and your downloaded AI models to a folder, and restores both on this phone or a new one (#40).
- Glass edges catch the wallpaper's own light instead of a white line, brightest at the top left, on every glass surface on Android 13 and up.
- Glass panes react as they move: when a pane or a page tips, the picture through it shifts and the light along its edge follows; at rest the glass looks as before (Android 13 and up).
- Pressing a pinned app on a glass dock, or a glass card in a sheet, lights the glass under your finger for a moment (#75).
- Look › Custom's glass controls work from any Look: Frost (clear to frosted), Tint with Hue, Vibrancy, Edge and the keyboard's Keys, with Custom remembering your setup; the wallpaper can change under the editor and the X11 display (#82).
- Pinned app icons go bigger: the Icon size slider reaches further on both dock styles, and icon packs draw as big as stock icons.
- Pinned apps fill the dock when it holds nothing else: the icons grow into the bar, and the bar sheds some of the space above and below them, at every Icon size.
- The welcome message in a new terminal is a rounded card in your wallpaper's colours, with Apps, Packages and Setup sections that fit the pane's width.
- The window bar names terminal-browser in full, with a globe.

### Terminal

- Terminal programs that ask for touch events receive your taps, long presses and swipes.
- The Appearance editor's Cursor trail menu has an Off entry, and the Mouse mode key reads out the state it switched to.

### Keyboard

- Settings › Keyboard › Layouts lets you edit, reset and create keyboard layouts, each row sliding its buttons open beneath it.
- Settings › Keyboard › Adaptive touch accuracy learns where your taps land, the space bar included, and pauses itself if typing gets less accurate.

### Voice and AI

- Dictation cleanup now writes what you meant from the whole passage, fixing misheard words and broken sentences rather than only dropping fillers; Polished keeps its paragraphs and lists.
- Dictation cleanup is about twice as fast and leaves what you said alone where nothing needs fixing: "uh" and "um" are always removed, only sentences that need it are rewritten, it no longer turns other languages into English, and it keeps your spelling.
- Cleaned-up dictation is ready a moment after you stop: on phones that run cleanup on the graphics chip (or with a remote model), it tidies your words at natural pauses while you talk.
- With a multilingual Whisper model, Voice language Auto-detect listens for the language you speak instead of assuming your keyboard's, and the dictation card names it when it differs.
- Tap the text on the dictation card to fix a word with the keyboard before you insert or copy it, and the words the cleanup fixed stay highlighted while you edit.
- On-device models load with less free memory than before, and one that would fill the phone's memory is stopped instead of freezing it.

## Changes

- The window bar's working ring breathes instead of turning, so a busy window no longer keeps the phone drawing and warm; marks tint the whole chip.

## Fixes

- A widget no longer crashes the launcher after a wallpaper, theme or colour change (#83).
- A Linux app on the X11 display survives a wallpaper, theme or font change: the display page comes back showing it, and new terminal sessions keep their DISPLAY while the server runs.
- The status bar's CPU reading hides when the phone has not granted privileged access and comes back when it does.

## Security

- Another app holding the RUN_COMMAND permission can no longer write files into your home or prefix while allow-external-apps is off (upstream termux-app fix).

## Still in the pre-release channel

Nothing: everything the channel carried is in this release.

## Editions

Nothing is exclusive to an edition.
