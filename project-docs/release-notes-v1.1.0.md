# v1.1.0

Scripts and agents can arrange your panes and windows, pictures travel through the terminal clipboard, and help is a tap away on Discord.

## New

### Terminal

- Pictures travel through the terminal clipboard: programs that speak kitty's clipboard protocol (OSC 5522) can read a picture from Android's clipboard and copy one to it, pasting a picture into a shell or an agent pastes it as a file path under `$PREFIX/tmp/clipboard`, and pictures inserted from the keyboard land the same way (#61).
- A screenshot you just took can be pasted from the keyboard's paste key, which shows a photo while it or a copied picture is waiting; plain paste (paste with a modifier) keeps the clipboard. Screenshots need the storage permission (Settings › Permissions & services › Files and media, or `termux-setup-storage`).

### Launcher

- Scripts and agents in a pane can place, size, inspect, wait on and close panes and windows through launcherctl: `pane open --from --direction --size`, `pane resize`, `pane wait`, `window list|focus|close`, and `launcherctl <group> --help` (#66).
- Settings › About & help has an Ask on Discord row, Documentation opens the website guide, and the help panel's support link goes to Discord (#58).

## Changes

- A new install starts with the current tool store: tlstore's own fish prompt in place of Oh My Posh, the newer herdr and kitten, and the agent skills installed whole.

## Fixes

- A quick flick in a full-screen program that follows the mouse, such as opencode, scrolls it without also opening the keyboard, in landscape and portrait.
- Thin lines no longer flash between rows of coloured backgrounds while you scroll, in full-screen programs such as opencode and in the transcript.

## Still in the pre-release channel

These are in the pre-release channel and not in this release; installing v1.1.0 over a pre-release build leaves them out until the next drop.

- Settings › Backup & restore saves your launcher settings and config files to one file, and your downloaded AI models to a folder, and restores both on this phone or a new one (#40).
- Pressing a pinned app on a glass dock, or a glass card in a sheet, lights the glass under your finger for a moment (#75).
- Glass edges catch the wallpaper's own light instead of a white line, brightest at the top left, on every glass surface on Android 13 and up (glass plan 2026-10-16, Round 1, always on).
- Terminal programs that ask for touch events receive your taps, long presses and swipes.
- Settings › Keyboard › Layouts lets you edit, reset and create keyboard layouts, each row sliding its buttons open beneath it.
- Pinned app icons go bigger: the Icon size slider reaches further on both dock styles, and icon packs draw as big as stock icons.
- Pinned apps fill the dock when it holds nothing else: the icons grow into the bar, and the bar sheds some of the space above and below them, at every Icon size.
- The Appearance editor's Cursor trail menu has an Off entry, and the Mouse mode key reads out the state it switched to.
- The window bar names terminal-browser in full, with a globe.
- Dictation cleanup now writes what you meant from the whole passage, fixing misheard words and broken sentences rather than only dropping fillers; Polished keeps its paragraphs and lists.
- Dictation cleanup is about twice as fast and leaves what you said alone where nothing needs fixing: "uh" and "um" are always removed, only sentences that need it are rewritten, it no longer turns other languages into English, and it keeps your spelling.
- Cleaned-up dictation is ready a moment after you stop: on phones that run cleanup on the graphics chip (or with a remote model), it tidies your words at natural pauses while you talk.
- With a multilingual Whisper model, Voice language **Auto-detect** listens for the language you speak instead of assuming your keyboard's, and the dictation card names it when it differs.
- Tap the text on the dictation card to fix a word with the keyboard before you insert or copy it, and the words the cleanup fixed stay highlighted while you edit.
- On-device models load with less free memory than before, and one that would fill the phone's memory is stopped instead of freezing it.
- Settings › Keyboard › Adaptive touch accuracy learns where your taps land, the space bar included, and pauses itself if typing gets less accurate.
- Look › Custom's glass controls work from any Look: Frost (clear to frosted), Tint with Hue, Vibrancy, Edge and the keyboard's Keys, with Custom remembering your setup; the wallpaper can change under the editor and the X11 display (#82).
- The welcome message in a new terminal is a rounded card in your wallpaper's colours, with Apps, Packages and Setup sections that fit the pane's width.
- Glass panes react as they move: when a pane or a page tips, the picture through it shifts and the light along its edge follows; at rest the glass looks as before (Android 13 and up).

## Editions

Shipped as `nix-v1.1.0` and `vaj-v1.1.0`. Nothing is exclusive to an edition.
