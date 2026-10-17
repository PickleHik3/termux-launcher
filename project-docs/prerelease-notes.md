<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. docs/agents/releasing.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.0.6.

## New in this drop
<!-- Only what this drop adds over the one before it; replace the whole list at every drop. -->

- Look › Custom's Tint adds your accent colour from any Look, Clear and Mist included, and Hue picks primary, secondary or tertiary.
- Look › Custom's Glass slider runs the glass from clear to frosted in one move, in place of Blur and Grain; a new Vibrancy slider makes what shows through the glass more or less colourful, and Edge runs the glass edge from no fringe to a bold coloured edge. Margin and Corner radius are on the Layout page only.
- Look › Custom's keyboard card has a Keys slider for how solid the keys are, from clear to opaque; the key labels stay readable at every setting.
- Glass edges show the same outline on frosted and solid glass as on clear glass.
- Clear, Mist and Tint sit on the Glass slider's curve now, so Clear is a touch blurrier with finer grain and Tint has much less grain; an install on a Look keeps that Look.
- Glass edges bend the wallpaper in a narrow ring right at the edge, meet the wallpaper outside without a seam, and carry a faint line of light along the top instead of a milky band; the Looks were re-tuned to match.
- Look › Custom's Depth slider is the last of the shared sliders, and each Look sits exactly on it; the rest of the edge comes with the Look.
- Pressing a pinned app on a glass dock, or a glass card in a sheet, lights the glass under your finger for a moment.
- Glass edges catch the wallpaper's own light instead of a white line, brightest at the top left, and keep the wallpaper's colour over a pale sky; each surface wears one edge, not a line on top of it.
- The herdr theme template goes see-through over the launcher glass, with its sidebar, text and accents in your wallpaper's colours.
- Settings › Keyboard › Adaptive touch accuracy now learns the space bar too: aim for space and land at the bottom of the row above, and it types a space once it has learned your taps. Swipes and Ctrl or Alt shortcuts always keep the key under your finger, and Settings shows whether it is learning, active or paused.
- Settings › Keyboard › Layouts lets you edit, reset and create keyboard layouts; a layout's row slides its buttons open beneath it, and a new layout shows at once on its first swap.
- Terminal programs that ask for touch events receive your taps, long presses and swipes.
- The Appearance editor's Cursor trail menu has an Off entry, so you can turn the trail off without going to Settings.
- The Mouse mode key reads out the state it switched to: Mouse mode on, Mouse mode off.
- Editing a cleaned-up dictation keeps the words the cleanup fixed or added highlighted while you type.
- Starting a dictation no longer freezes the screen for a few seconds: when the cleanup model is not loaded yet, it loads after you stop speaking.
- Round icons in icon packs stay whole circles now that pack icons draw bigger.
- The window bar names terminal-browser in full, with a globe.
- The command palette's key strip under the search field has solid keys, and its last row stays readable above the rounded bottom.
- Fixes that also shipped in v1.0.6: the Low-memory preset switch, smoother transcript scrolling, symbol icons in larger program text, kitty playback without flicker, icon packs that stay applied, the sessions drawer at large font sizes, and a wallpaper change that keeps your Look's blur.

## Experimental
<!-- What is on pre and not yet in a stable, one line each; name the pull request or branch it came from,
and end the line with its status in an HTML comment: ready, stays: P2, P6 (the checks still failing)
or drop: reason (docs/agents/releasing.md, "Promoting from pre to a stable"). -->
These may change or be dropped before they reach a stable release.

- Settings › About & help has an Ask on Discord row, Documentation opens the website guide, and the help panel's support link goes to Discord (#58). <!-- ready -->
- Settings › Backup & restore saves your launcher settings and config files to one file, and your downloaded AI models to a folder, and restores both on this phone or a new one (#40). <!-- stays: P1, P2 -->
- Glass edges draw as a hairline of light over a narrow bend that meets the wallpaper without a seam, and Look › Custom has a Depth slider after the shared sliders, shown where glass edges are drawn, that tunes how thick the glass edge looks. Corners is now called Corner radius (#63, #75). <!-- stays: P1 -->
- Pressing a pinned app on a glass dock, or a glass card in a sheet, lights the glass under your finger for a moment (#75). <!-- stays: P1, P2 -->
- Glass edges catch the wallpaper's own light instead of a white line, brightest at the top left, on every glass surface on Android 13 and up (glass plan 2026-10-16, Round 1, always on). <!-- stays: P1, P2 -->
- Scripts and agents in a pane can place, size, inspect, wait on and close panes and windows through launcherctl: `pane open --from --direction --size`, `pane resize`, `pane wait`, `window list|focus|close`, and `launcherctl <group> --help` (#66). <!-- stays: P1 -->
- Pictures travel through the terminal clipboard: programs that speak kitty's clipboard protocol (OSC 5522) can read a picture from Android's clipboard and copy one to it, pasting a picture into a shell or an agent pastes it as a file path under `$PREFIX/tmp/clipboard`, and pictures inserted from the keyboard land the same way (#61). <!-- stays: P1 -->
- A screenshot you just took can be pasted from the keyboard's paste key, which shows a photo while it or a copied picture is waiting; plain paste (paste with a modifier) keeps the clipboard. Screenshots need the storage permission (Settings › Permissions & services › Files and media, or `termux-setup-storage`). <!-- stays: P2 -->
- Terminal programs that ask for touch events receive your taps, long presses and swipes. <!-- stays: P1 -->
- Settings › Keyboard › Layouts lets you edit, reset and create keyboard layouts, each row sliding its buttons open beneath it. <!-- stays: P1, P2 -->
- Pinned app icons go bigger: the Icon size slider reaches further on both dock styles, and icon packs draw as big as stock icons. <!-- stays: P3 (#71) -->
- Pinned apps fill the dock when it holds nothing else: the icons grow into the bar, and the bar sheds some of the space above and below them, at every Icon size. <!-- stays: P2, P3 (#71) -->
- The Appearance editor's Cursor trail menu has an Off entry, and the Mouse mode key reads out the state it switched to. <!-- stays: P1 -->
- The window bar names terminal-browser in full, with a globe. <!-- stays: P1 -->
- Dictation cleanup now writes what you meant from the whole passage, fixing misheard words and broken sentences rather than only dropping fillers; Polished keeps its paragraphs and lists. <!-- stays: P1 -->
- Dictation cleanup is about twice as fast and leaves what you said alone where nothing needs fixing: "uh" and "um" are always removed, only sentences that need it are rewritten, it no longer turns other languages into English, and it keeps your spelling. <!-- stays: P1 -->
- Cleaned-up dictation is ready a moment after you stop: on phones that run cleanup on the graphics chip (or with a remote model), it tidies your words at natural pauses while you talk. <!-- stays: P1 -->
- With a multilingual Whisper model, Voice language **Auto-detect** listens for the language you speak instead of assuming your keyboard's, and the dictation card names it when it differs. <!-- stays: P1 -->
- Tap the text on the dictation card to fix a word with the keyboard before you insert or copy it, and the words the cleanup fixed stay highlighted while you edit. <!-- stays: P1 -->
- On-device models load with less free memory than before, and one that would fill the phone's memory is stopped instead of freezing it. <!-- stays: P1, P2 -->
- Settings › Keyboard › Adaptive touch accuracy learns where your taps land, the space bar included, and pauses itself if typing gets less accurate. <!-- stays: pass bar on pong, three emulator checks -->

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing is exclusive to an edition.
