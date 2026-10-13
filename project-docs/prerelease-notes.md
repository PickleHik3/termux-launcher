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

## New in this drop
<!-- Only what this drop adds over the one before it; replace the whole list at every drop. -->

- Pinned app icons are bigger and sit tighter: icon packs no longer carry extra padding around their artwork, the Icon size slider reaches further, and a dock with nothing above or below it trims the space around its icons and lets them fill the bar.
- Dictation cleanup writes what you meant from the whole passage, about twice as fast, and has it ready a moment after you stop. It keeps your language and spelling and leaves alone what needs no fixing. Tap the card's text to fix a word before you insert it, and with a multilingual Whisper model, Auto-detect listens for the language you speak.
- Swipe down on the apps row to open the app drawer while it shows filtered apps, such as an A–Z letter's, not only your pinned apps.
- The paste key's photo, shown while a screenshot or a copied picture is waiting, is now the size of the keys around it.
- Also new: tlstore 0.17, on-device models that load with less free memory and stop instead of freezing the phone, and the last three fixes under "Heading for the next release".

## Heading for the next release
<!-- Everything on dev since the latest stable; ships in the next stable as it stands. -->

- A fresh install comes with the current tlstore (0.13).
- Opening the widget picker no longer closes the launcher on Android 11 and earlier (#62).
- Picking a letter just as the last letter's apps fade from the dock no longer closes the launcher.
- A pull on the apps row that you let go past halfway opens the app drawer instead of springing back.
- A new number of apps on the dock, picked in the Appearance editor, shows at once instead of after your next swipe.

## Experimental
<!-- What is on pre and not yet on dev, one line each; name the pull request or branch it came from. -->
These may change or be dropped before they reach a stable release.

- A fresh install comes with the current tlstore (0.17): terminal-browser is in the store, and Claude Code and opencode start on Android 12 (tlstore#1).
- Search contacts from the command palette: type a name or a number, tap the result to open the contact, or tap its phone chip to dial. The first search offers a row to allow contact access. Thanks to @AbineshDev01 (#52, #53).
- Hold any action or contact in the command palette to give it a keyboard shortcut, the way you already could with apps. Binding a row again moves its shortcut instead of adding a second one.
- The chips under the command palette's search box show what you use most: actions, apps (with their icon) and contacts (with their photo, or their initial). Each chip is named after what it does, so the keyboard's split, docked and floating no longer all read "set".
- A search you repeat puts the result you pick most at the top, rather than the one you picked last.
- Settings › About & help has an Ask on Discord row, Documentation opens the website guide, and the help panel's support link goes to Discord (#58).
- Settings › Backup & restore saves your launcher settings and config files to one file, and your downloaded AI models to a folder, and restores both on this phone or a new one (#40).
- Look › Custom now has Surface and Edge groups. Edge brings back Bend, Edge width and Edge light, and adds Shine (a light along the glass rim) and Dispersion (colour fringing at the rim); both sit at zero in every shipped Look, so nothing changes until you turn them up. Corners is now called Corner radius (#63).
- Look › Custom starts from the Look on screen, so you can tune Clear or Mist without losing it; a quick look at another Look and back keeps your changes, and Undo puts back what you opened with.
- Scripts and agents in a pane can place, size, inspect, wait on and close panes and windows through launcherctl: `pane open --from --direction --size`, `pane resize`, `pane wait`, `window list|focus|close`, and `launcherctl <group> --help` (#66).
- Pictures travel through the terminal clipboard: programs that speak kitty's clipboard protocol (OSC 5522) can read a picture from Android's clipboard and copy one to it, pasting a picture into a shell or an agent pastes it as a file path under `$PREFIX/tmp/clipboard`, and pictures inserted from the keyboard land the same way (#61).
- A screenshot you just took can be pasted from the keyboard's paste key, which shows a photo while it or a copied picture is waiting; plain paste (paste with a modifier) keeps the clipboard. Screenshots need the storage permission (Settings › Permissions & services › Files and media, or `termux-setup-storage`).
- Pinned app icons go bigger: the Icon size slider reaches further on both dock styles, icon packs draw as big as stock icons, and a full row of apps keeps its icons from touching.
- Pinned apps fill the dock when it holds nothing else: the icons grow into the bar, and the bar sheds some of the space above and below them, at every Icon size.
- Swipe down on the apps row to open the app drawer while an A–Z letter's apps are showing, not only from your pinned apps.
- Dictation cleanup now writes what you meant from the whole passage, fixing misheard words and broken sentences rather than only dropping fillers; Polished keeps its paragraphs and lists.
- Dictation cleanup is about twice as fast and leaves what you said alone where nothing needs fixing: "uh" and "um" are always removed, only sentences that need it are rewritten, it no longer turns other languages into English, and it keeps your spelling.
- Cleaned-up dictation is ready a moment after you stop: on phones that run cleanup on the graphics chip (or with a remote model), it tidies your words at natural pauses while you talk.
- With a multilingual Whisper model, Voice language **Auto-detect** listens for the language you speak instead of assuming your keyboard's, and the dictation card names it when it differs.
- Tap the text on the dictation card to fix a word with the keyboard before you insert or copy it.
- On-device models load with less free memory than before, and one that would fill the phone's memory is stopped instead of freezing it.

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing is exclusive to an edition.
