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

- The keyboard floats over the Linux display in portrait again on installs where an older Layout editor visit had switched it to making room.
- Keyboard over display, in Settings → Display → Input, switches the keyboard between floating over the Linux display and making the display resize.

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
