<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. AGENTS.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.0.4.

## Heading for the next release
<!-- Everything on dev since the latest stable; ships in the next stable as it stands. -->

- A fresh install comes with the current tlstore (0.13).

## Experimental
<!-- One entry per experiment branch merged into this drop and not on dev; name the branch so the next drop can leave it out. -->
These may change or be dropped before they reach a stable release.

- Search contacts from the command palette: type a name or a number, tap the result to open the contact, or tap its phone chip to dial. The first search offers a row to allow contact access. Thanks to @AbineshDev01 (#52, #53). Branch `exp/palette-contacts`.

## Editions
<!-- Only what is exclusive to the Nix or VAJ build of this drop; say so when nothing is. -->

Nothing is exclusive to an edition.
