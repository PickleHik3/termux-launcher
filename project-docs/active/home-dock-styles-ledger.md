# Home dock styles: ledger

Rolling status for [`home-dock-styles-2026-10-20.md`](home-dock-styles-2026-10-20.md). Newest
first within each section. Delete this file with the spec when the work is delivered.

## Status

| Step | State | Commit | Notes |
|---|---|---|---|
| 1 Dock style pref + picker | merged, unchecked on device | f8ed2e66b | Picker in the Layout editor's Row B while Home's dock is selected; `DockStyle.BUILT` gates unbuilt styles to Glass tray. |
| 2 Glass tray + shared glass | in progress (worker, parallel) | | |
| 3 Lens beads | not started | | |
| Emulator pass 1 | not started | | |
| 4a Taskbar + run strip | not started | | |
| 4b Prompt line | not started | | |
| 4c Wall strip | not started | | |
| Emulator pass 2 | not started | | |
| 5 Pong install | not started | | |

## Device checks

Owed checks also go into [`../device-checks.md`](../device-checks.md) in its usual form; this list
records what each pass found.

## Decisions taken without the developer

The developer delegated decisions for this run (2026-10-20, overnight). Each is recorded here with
its reason so it can be reversed.

- 2026-10-20 · The picker lives in the Layout editor's Row B, shown while Home's dock is selected
  (as the keyboard's tools are), rather than as a new settings row: it keeps the fragile settings
  pages untouched and puts the choice next to the dock it changes. Its name, "Dock style", sits
  near the older Docked/Floating "Style"; revisit if the two read as one.
