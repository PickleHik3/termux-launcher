<!--
Rolling notes for the pre-release channel (vX.Y.Z-pre.N, nix-vX.Y.Z-pre.N, vaj-vX.Y.Z-pre.N):
everything the channel carries beyond the latest stable release, one file for all three editions.
This whole file is the body of every drop (HTML comments do not render on GitHub), so it restates
the full list each time; when a stable ships, the lines that shipped move into that release's notes
and leave this file. docs/agents/releasing.md, "The pre-release channel", has the rules; the voice is the
release-notes voice (one line per item, what you can now do, no mechanism).
-->
# Pre-release channel

Latest stable: v1.2.2.

## New since pre.3

### Home and Workspace
- Pressing Home now always lands on the Home page you set as home, from any page, including the extra keys' Home key. No more landing on Home 2, and no more blank Home card sliding in.
- Home ⇄ Workspace now uses your Page transition (Carousel, Depth, …), same as moving between Home pages.
- While you swipe between Home and the Workspace, the status bar and dock ease from one page's look to the other's (margin, glass) instead of snapping when the swipe ends.
- Swiping home from an app shrinks it back into its icon again, for apps on Home pages and in the drawer too, not only the dock.

### App drawer
- In the pages view, the rows spread over the whole page instead of leaving a gap at the bottom.
- Every drawer view (list, pages, categories) now ends the same distance above the bottom of its glass as at the sides, keyboard up or down.
- A sideways swipe on the drawer's pages closes the keyboard (with "Open keyboard automatically" on).
- The App menu shows each shortcut's own icon, and More shortcuts has the new menu look.
- Move to category is back in the App menu (#199).

### Dock
- New float terminal tile for the dock, after the Command palette tile. Turn it on from the Dock card (#197).
- Hold the Drawer, Command palette or New float terminal tile to pick it up and move it to another column (holding them did nothing in pre.3).

### Terminal
- The pane corner panel has a More button at the end, opening the same sheet as long-pressing the Workspace.
- A Full screen pane under the camera cut-out no longer jumps at the end of the keyboard slide (#190).

### Edit pages
- Long-press a page card for Set as home, Rename, Bind app (Display pages) and Remove (#201).
- Add display opens a picker: Whole display first, then your Linux apps (#200).
- You can remove the Terminal page; it asks before closing its windows, and opening a terminal brings the page back (#202).
- With Use as set to Terminal only, Edit pages offers "Bring back N Home pages" (#203).
- Page cards show their names.

### Appearance editor
- Sliders sit on one line, with the value inside the slider.
- Rows are grouped: the Look and layout choices first, then sliders, toggles, colours and links.
- Margin on the Global, Workspace and Home cards; Home and Workspace pages can hold different margins.
- Moving a Global, Workspace or Home slider now overrides what you set beneath it. Changing a single element afterwards only changes that element.

### Keyboard
- Over a Display page, the keyboard frosts the live display behind it in overlay mode (Android 12 and up; plain see-through where Android turns window blur off, like battery saver). In overlay mode the display now runs under the keyboard to the bottom of the screen, and keeps that size when the keyboard closes, so the extra keys and a bottom status bar sit over the bottom of the display.

### Linux apps
- Start with, in Settings › Linux apps: open the display with nothing, a distro's XFCE desktop, or a command of your choice (#166).
- The Set up Linux apps guide marks distros you already have installed and skips installing them again (#176).
- The guide can install an XFCE desktop for you (#177).

### On-device AI
- Newer MNN runtime (3.6.2): MoE models like MobileMoE and LFM2.5-8B-A1B now load (#211).
- LiteRT-LM updated to 0.19.0.

### General
- Scripts using launcherctl work right after an update, without opening the launcher first (#205).

## Editions

Nothing exclusive to the Nix or VAJ build in this drop.
