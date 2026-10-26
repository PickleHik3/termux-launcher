<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. docs/agents/releasing.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.2.0.

## New in this drop
<!-- Only what this drop adds over the one before it; replace the whole list at every drop. -->

- Look › Custom's Tint adds your accent colour from any Look, Clear and Mist included, and Hue picks primary, secondary or tertiary.
- Look › Custom's Frost slider runs the glass from clear to frosted in one move, in place of Blur and Grain; a new Vibrancy slider makes what shows through the glass more or less colourful, and Edge runs the glass edge from no fringe to a bold coloured edge. Margin and Corner radius are on the Layout page only.
- Look › Custom's keyboard card has a Keys slider for how solid the keys are, from clear to opaque; the key labels stay readable at every setting.
- Glass edges show the same outline on frosted and solid glass as on clear glass.
- Clear, Mist and Tint sit on the Frost slider's curve now, so Clear is a touch blurrier with finer grain and Tint has much less grain; an install on a Look keeps that Look.
- Glass edges catch the wallpaper's own light instead of a white line, brightest at the top left, and keep the wallpaper's colour over a pale sky; each surface wears one edge, not a line on top of it.
- Changing the wallpaper keeps the Appearance editor open on the page you were on, and the X11 display keeps showing its apps; new terminals still find the display (#83).
- Changing the wallpaper or switching dark mode no longer crashes the launcher when app widgets are on Home (#83).
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
- The welcome message in a new session is a rounded card in your wallpaper's colours, with a tap-to-open link to the guide; it lays itself out to fit narrow panes.
- Fixes that also shipped in v1.0.6: the Low-memory preset switch, smoother transcript scrolling, symbol icons in larger program text, kitty playback without flicker, icon packs that stay applied, the sessions drawer at large font sizes, and a wallpaper change that keeps your Look's blur.
- The welcome message in a new terminal is a rounded card in your wallpaper's colours, with Apps, Packages and Setup sections that fit the pane's width and a tap-to-open link to the guide.
- Settings › Linux apps › Arch Linux installs on arm64 phones again, from the Arch Linux ARM image; Debian and Ubuntu are unchanged.

## Experimental
<!-- What is on pre and not yet in a stable, one line each; name the pull request or branch it came from,
and end the line with its status in an HTML comment: ready, stays: P2, P6 (the checks still failing)
or drop: reason (docs/agents/releasing.md, "Promoting from pre to a stable"). -->
These may change or be dropped before they reach a stable release.

- Linux apps whose installer left a placeholder in place of their command, such as Termux's Chromium, now open from the drawer; an app whose command is missing says which one instead of a generic error. (merge 7940a21ed on `pre`) <!-- stays: P1, P2 -->
- Benchmark graphics, under Settings → Display → Linux apps → Set up Linux apps → Graphics, tries every graphics option this phone has and keeps the fastest. (merge d55271a54 on `pre`) <!-- stays: P1, P2 -->
- Full screen now uses the whole screen: the empty band left where the status bar was is gone. (280ced3f7 on `pre`) <!-- stays: P1, P2 -->
- Dragging one of the launcher's own widgets to the edge of the Widgets page to move it to another page no longer crashes the launcher. (77e72e455 on `pre`) <!-- stays: P1, P2 -->
- With another app as your home screen, Appearance lets you pick a picture for Termux Launcher alone: it sits behind the terminal and under the glass, and the phone's own wallpaper stays as it was. Tick "Also set as phone wallpaper" to set both. The launcher's Material colours follow that picture. (merges 19b0f4c29 and 0f256d039 on `pre`) <!-- stays: P1, P2 -->
- Settings › App › Battery & performance gathers Reduce idle activity, the Low-memory preset and the motion they trim (Glass refraction, Living glass, Wallpaper parallax, Cursor trail, Terminal effect), and shows which ones a preset has turned off. Wallpaper parallax moved there from Appearance. (merge 8f1301960 on `pre`) <!-- stays: P1, P2 -->
- The keyboard floats over the Linux display in portrait as it does in landscape, so apps no longer resize and flicker each time it opens. (branch `feat/display-keyboard`, spec #125) <!-- stays: P1, P2 -->
- "Keyboard follows text fields" works in every touch mode: Touchscreen, Trackpad and Direct touch. (branch `feat/display-keyboard`, #126) <!-- stays: P1, P2 -->
- The touchpad has a button that switches between the keyboard floating over the display and the display making room for it, so you can reach its bottom corners. (branch `feat/display-keyboard`, #128) <!-- stays: P1, P2 -->
- With fcitx5, which Set up Linux apps now installs, the keyboard comes up when a text field in a Linux app takes focus and goes when it loses it, with no tap; it no longer opens over text you can only read, and stays put as you move between fields. (branch `feat/display-keyboard`, #129–#131) <!-- stays: P1, P2 -->
- The app drawer, the command palette and sheets opened over the Linux display take Back and your typing, as they do over the terminal; the drawer's search there brings up the keyboard. (branch `feat/display-keyboard`) <!-- stays: P1, P2 -->
- The drawer's "Search keyboard" setting is now "Use system keyboard". (branch `feat/display-keyboard`) <!-- stays: P1, P2 -->
- Fresh installs start from a newer Termux base (bootstrap 2026.10.18). (branch `feat/display-keyboard`) <!-- stays: P1, P2 -->
- On installs where an older Layout editor visit had saved "make room" for portrait without the user choosing it, the keyboard floats over the Linux display again; leaving the Layout editor without saving no longer saves the keyboard mode. (PR #138) <!-- stays: P1, P2 -->
- Settings → Display → Input → Keyboard over display: Overlay or Resize display, for both orientations at once; the touchpad's button still switches one orientation. (PR #138) <!-- stays: P1, P2 -->

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing is exclusive to an edition.
