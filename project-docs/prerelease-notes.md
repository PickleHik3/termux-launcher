<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. docs/agents/releasing.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.0.4.

## Heading for the next release
<!-- Everything on dev since the latest stable; ships in the next stable as it stands. -->

- A fresh install comes with the current tlstore (0.13).
- Opening the widget picker no longer closes the launcher on Android 11 and earlier (#62).

## Experimental
<!-- What is on pre and not yet on dev, one line each; name the pull request or branch it came from. -->
These may change or be dropped before they reach a stable release.

- Search contacts from the command palette: type a name or a number, tap the result to open the contact, or tap its phone chip to dial. The first search offers a row to allow contact access. Thanks to @AbineshDev01 (#52, #53).
- Settings › About & help has an Ask on Discord row, Documentation opens the website guide, and the help panel's support link goes to Discord (#58).
- Hold any action or contact in the command palette to give it a keyboard shortcut, the way you already could with apps. Binding a row again moves its shortcut instead of adding a second one.
- The chips under the command palette's search box show what you use most: actions, apps (with their icon) and contacts (with their photo, or their initial). Each chip is named after what it does, so the keyboard's split, docked and floating no longer all read "set".
- A search you repeat puts the result you pick most at the top, rather than the one you picked last.
- Settings › Backup & restore saves your launcher settings and config files to one file, and your downloaded AI models to a folder, and restores both on this phone or a new one (#40).
- Look › Custom now has Surface and Edge groups. Edge brings back Bend, Edge width and Edge light, and adds Shine (a light along the glass rim) and Dispersion (colour fringing at the rim); both sit at zero in every shipped Look, so nothing changes until you turn them up. Corners is now called Corner radius (#63).
- Scripts and agents in a pane can place, size, inspect, wait on and close panes and windows through launcherctl: `pane open --from --direction --size`, `pane resize`, `pane wait`, `window list|focus|close`, and `launcherctl <group> --help` (#66).
- Pictures travel through the terminal clipboard: programs that speak kitty's clipboard protocol (OSC 5522) can read a picture from Android's clipboard and copy one to it, pasting a picture into a shell or an agent pastes it as a file path under `$PREFIX/tmp/clipboard`, and pictures inserted from the keyboard land the same way (#61).
- A screenshot you just took can be pasted from the keyboard's paste key, which shows a photo while it or a copied picture is waiting; plain paste (paste with a modifier) keeps the clipboard. Screenshots need the storage permission (Settings › Permissions & services › Files and media, or `termux-setup-storage`).
- Look › Custom starts from the Look on screen, so you can tune Clear or Mist without losing it; a quick look at another Look and back keeps your changes, and Undo puts back what you opened with.

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing is exclusive to an edition.
