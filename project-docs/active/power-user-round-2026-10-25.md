# Power-user round (2026-10-25)

Decisions from a grilling on two Codex sessions: one used the launcher on the emulator as a
long-time Nova/Niagara user (debug build 969d4f25b), and one studied the Octopi launcher on pong. The
Launcher3 comparison behind items 10–14 is
`project-docs/reference/launcher/launcher-basics-research-2026-10-25.md`. The developer's standing
rule from #149 applies: the Launcher3 way for shared conventions, sheets where we have more.

All work lands on `v2-redesign`. Waves: 1 = items 1–7 (parallel where files are disjoint), 2 = items
8–9, 3 = items 11–14 in one sequence (they share the drag code), with item 10 alongside wave 3.

## Wave 1: bugs and polish

1. **Drawer overlap.** While the app drawer is open, Home content showed through: a pinned Chrome's
   label printed over the drawer's Calendar label. Fix: the home grid (shortcuts, folders, widgets) and
   the page dots fade with the drawer's open progress and come back on close. Wallpaper, status bar and
   dock are untouched. The hook belongs on `AppDrawerController.Host` (`onDrawerOpenSettled` exists),
   not in `TermuxActivity`.
2. **Contrast floor.** Dim terminal text (welcome sheet descriptions, the prompt's `~`) vanished on a
   dark wallpaper behind a transparent terminal; the dock's A–Z letters and its terminal glyph vanished
   over a pale wallpaper behind a clear dock. Fix, always on, no setting: normal text keeps at least
   4.5:1 and dim (SGR 2) text at least 3:1 against the surface it actually sits on, nudging only
   colours below the floor so dim still reads dimmer. The backdrop is estimated per surface from the
   average luminance of the already-blurred wallpaper region behind it, blended with the surface's
   glass tint and opacity, and recomputed only when the wallpaper, the Look or the surface bounds
   change, never per frame. Today `TerminalRenderer.dimColor` multiplies RGB by 2/3 and the renderer
   knows only the terminal's own background.
3. **Blank A–Z icons.** Tapping a letter in the dock's A–Z row showed grey stand-in tiles instead of
   icons. Cause unknown: diagnose first (`LauncherIconStore` stand-ins vs a tile that never repaints
   when the load lands). The filter stays brief: it lasts until the next touch.
4. **Hardware keys in search.** Injected key events reached the terminal but not the palette or the
   drawer's search field. Reproduce with real key events first. If confirmed: while a search field
   has focus it owns printable keys, Backspace, ↑/↓ and Enter; Esc closes it; Ctrl/Alt shortcuts still
   reach the launcher. Suspect `TermuxActivity.dispatchKeyEvent` → `routeKeyNobodyHolds` offering keys
   to overlays before the focused field.
5. **Widget picker under the dock.** The dock draws over the bottom rows of the Add widget sheet.
6. **Theme hint.** In the Appearance editor the global theme is behind the palette pill. The first
   time the editor opens, a bubble over the pill says "Theme, colours and glass"; it goes for good on
   the first tap. The pill keeps its size; the resting state is otherwise unchanged.
7. **Accessibility.** Palette results and the dock's long-press menus get accessibility nodes
   (virtual views where they are drawn). The in-app keyboard is out of scope.

### Follow-ups decided after wave 1 (2026-10-25)

- **The user's contrast level wins.** The floor follows the existing Terminal contrast setting
  (Softer = 3:1, default 4.5:1); dim keeps a proportionally lower floor.
- **Dim blends toward the backdrop.** Faint text is the colour mixed a third of the way toward what it
  stands on, instead of xterm's darken-by-a-third; identical on black, quieter on light backgrounds.
- **A–Z icons warm on touch-down.** A letter's first page of matches starts rendering when the finger
  lands on it, through the existing budgeted dock icon cache. Item 3 was not a bug: Codex's screenshot
  caught the normal 180 ms arrival.
- **Arrow keys walk the drawer's search results** with a visible highlight; Enter opens it.
- Item 4's cause, found on the emulator: the active pane's `TerminalView` held focus with no session,
  and `TerminalView.onKeyDown` swallows every key while `mEmulator` is null (fixed in 5ce4ce853).

## Wave 2: settings from the home screen

8. **Settings as a bottom sheet.** Launcher settings opens as a bottom sheet over the live home,
   resting at about 60% height, with the status bar, widgets and terminal live above it. Every page
   lives in it; long pages drag it to full height, and it keeps that height until closed. Back steps
   up one level and closes from the root; dragging the handle down or tapping the home above closes
   from anywhere. The settings pages themselves stay as they are (they are fragile); only the window
   around them changes. No bottom category tab bar: search and Back are the navigation. Mockups
   chosen: variant D.
9. **Lock layout on the long-press card.** The home long-press card gains a Lock layout toggle
   (the existing `HomeSettings.KEY_LAYOUT_LOCKED`). When on, the add/edit entries step aside and the
   card says the layout is locked.

## Wave 3: the Launcher3 gaps

10. **Pin requests.** Handle `LauncherApps.PinItemRequest` (`ACTION_CONFIRM_PIN_SHORTCUT`,
    `ACTION_CONFIRM_PIN_APPWIDGET`) and the legacy `INSTALL_SHORTCUT` broadcast. A confirm card shows
    the item with "Add to Home"; it lands in the first free cell on the current page, else the next
    page with room, else a new page at the end ("Added to a new page"). Long-pressing the preview drags
    it into place. If the layout is locked, the card says so and offers Unlock.
11. **Lift from the App menu.** Long-press on a Home icon shows the App menu; moving the finger past a
    small distance closes the menu and lifts the icon. Edit mode stays for widgets and bulk moves.
12. **Home ↔ dock.** The dock becomes a drop target. A gap opens where the finger is; on a dock page
    already at its maximum, the last icon moves to the start of the next dock page (the dock pages
    through `DockPagingModel`, it has no limit). Holding at the dock's edge flips the dock page.
    Dropping on a dock icon makes a folder. Dock icons drag out to Home the same way.
13. **Remove and Uninstall targets.** While dragging, Remove and Uninstall targets appear at the top;
    removing shows an Undo notice for about five seconds.
14. **Shortcuts out of the App menu.** An app shortcut in the App menu can be dragged out and dropped
    on Home as a pinned shortcut.

## Verification

Workers compile and run the test classes they write. After each wave: install on `emu`, re-run the
Codex power-user session, and add what still needs a phone to `project-docs/device-checks.md`. Pong
only when the developer asks.
