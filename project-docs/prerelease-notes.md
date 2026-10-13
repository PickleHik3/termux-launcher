<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. docs/agents/releasing.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.0.5.

## New in this drop
<!-- Only what this drop adds over the one before it; replace the whole list at every drop. -->

- With the status bar hidden on a phone with a camera cutout, the app drawer no longer shows a dark band with square corners behind it (#69).
- The welcome sheet has a Contacts in search switch, so the command palette can find people from the start.
- The command palette's contact search, shortcuts for actions and contacts, and its most-used chips have shipped in v1.0.5, so they leave the list below.

## Experimental
<!-- What is on pre and not yet on dev, one line each; name the pull request or branch it came from. -->
These may change or be dropped before they reach a stable release.

- Settings › About & help has an Ask on Discord row, Documentation opens the website guide, and the help panel's support link goes to Discord (#58).
- Settings › Backup & restore saves your launcher settings and config files to one file, and your downloaded AI models to a folder, and restores both on this phone or a new one (#40).
- Look › Custom now has Surface and Edge groups. Edge brings back Bend, Edge width and Edge light, and adds Shine (a light along the glass rim) and Dispersion (colour fringing at the rim); both sit at zero in every shipped Look, so nothing changes until you turn them up. Corners is now called Corner radius (#63).
- Scripts and agents in a pane can place, size, inspect, wait on and close panes and windows through launcherctl: `pane open --from --direction --size`, `pane resize`, `pane wait`, `window list|focus|close`, and `launcherctl <group> --help` (#66).
- Pictures travel through the terminal clipboard: programs that speak kitty's clipboard protocol (OSC 5522) can read a picture from Android's clipboard and copy one to it, pasting a picture into a shell or an agent pastes it as a file path under `$PREFIX/tmp/clipboard`, and pictures inserted from the keyboard land the same way (#61).
- A screenshot you just took can be pasted from the keyboard's paste key, which shows a photo while it or a copied picture is waiting; plain paste (paste with a modifier) keeps the clipboard. Screenshots need the storage permission (Settings › Permissions & services › Files and media, or `termux-setup-storage`).
- Pinned app icons go bigger: the Icon size slider reaches further on both dock styles, and icon packs draw as big as stock icons.
- Pinned apps fill the dock when it holds nothing else: the icons grow into the bar, and the bar sheds some of the space above and below them, at every Icon size.
- Dictation cleanup now writes what you meant from the whole passage, fixing misheard words and broken sentences rather than only dropping fillers; Polished keeps its paragraphs and lists.
- Dictation cleanup is about twice as fast and leaves what you said alone where nothing needs fixing: "uh" and "um" are always removed, only sentences that need it are rewritten, it no longer turns other languages into English, and it keeps your spelling.
- Cleaned-up dictation is ready a moment after you stop: on phones that run cleanup on the graphics chip (or with a remote model), it tidies your words at natural pauses while you talk.
- With a multilingual Whisper model, Voice language **Auto-detect** listens for the language you speak instead of assuming your keyboard's, and the dictation card names it when it differs.
- Tap the text on the dictation card to fix a word with the keyboard before you insert or copy it.
- On-device models load with less free memory than before, and one that would fill the phone's memory is stopped instead of freezing it.

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing is exclusive to an edition.
