# Home conventions: what a thumb expects (2026-10-30)

Decisions from the grilling round of 2026-10-30 over
`project-docs/reference/launcher/home-screen-interaction-expectations-research-2026-10-30.md`
(the note cites the sources; this file holds only what was decided). Vocabulary as in
`docs/GLOSSARY.md`: **open layer**, **Start in**, **App menu**, **Home page**, **Appearance editor**,
**home press**. It extends `launcher-idiom-2026-10-23.md`: the Launcher3 way for conventions every
launcher shares, a sheet where this launcher has more to show.

## The baseline

Where Pixel (Launcher3), Samsung One UI Home and the iOS Home Screen agree, the launcher does the
same. Where they disagree, the majority of the three wins, unless a departure below says otherwise.

"Editor" below means every place that edits and then commits: the Appearance editor, Edit pages,
the widget editor and the wallpaper page.

## Rules

✔ = the code already does this; ✗ = a change owed; *audit* = not yet checked against the code.

1. ✔ **Home press inside the launcher.** It closes every open layer, and the Appearance editor
   through its unsaved-changes rule, then lands on Start in. A home press that brings the launcher
   back from another app keeps the page you left (`wall/HomePress`).
2. ✗ **Home press resets the drawer.** A home press that closes the app drawer also clears its
   search text and scrolls it back to the top.
3. ✔ **Back never leaves the launcher.** It closes the topmost open layer first; on a quiet page it
   does nothing.
4. ✗ **App menu: four shortcuts.** The App menu shows at most four of an app's shortcuts, then
   More shortcuts (`AppMenuPlan.MAX_SHORTCUT_ROWS` is 6 today, five shortcuts plus More). This
   replaces "collapse past 6" in `launcher-idiom-2026-10-23.md` §1.
5. *audit* **Every editor exits by Done or Back**, and leaving returns you to the page and card you
   came from. The wallpaper page's Done follows it since 418a2d7ef; the others are unchecked.
6. *audit* **An outside tap closes a layer.** The App menu, Home's long-press sheet, folders, a
   widget in resize and the app drawer each close on a tap outside them. The Appearance editor is
   exempt: a tap there picks a surface ("tap the thing").
7. *audit* **Widgets, one rule for both kinds** (app widgets and the launcher's own): a tap opens
   the app or the declared button's action, a long press lifts the widget into resize, and a tap
   outside ends the resize.
8. *audit* **Folders.** Dropping an app on an app makes a folder; a folder left with one app
   dissolves into that app's icon.
9. ✔ **Remove and Uninstall are two words.** Remove takes an item off Home and offers Undo
   (`HomeItemUndo`); Uninstall removes the app.
10. *audit* **Haptics only at moments.** A tick on long-press recognition, pick-up, and a snap or
    detent crossing; never per frame. Each goes through `Haptics.tick` (the app's Haptics switch)
    and `performHapticFeedback` (Android's touch-feedback setting). The drag ticks comply
    (`IconCarry.move` ticks only when the drop target changes); the other call sites are unchecked.
11. *audit* **Every custom gesture has a tap path and a visible cue.** A gesture without one is a
    gap to fix. The status bar's long-press menu item Expand/Compact status bar (baf9a1a3b) is the
    tap path for the status swipe's fold.
12. ✔ **The bottom edge is the system's.** No launcher gesture starts in the system's bottom-edge
    zone; the keyboard swipe starts on the page's bottom border, above the dock.

## Departures, with reasons

- **New apps go to the app drawer only**, not onto a Home page (Pixel adds them to Home). Home
  pages are widget-first, and the A–Z index already finds a new app.
- **Home's long press opens a sheet**, not Launcher3's three-item menu: it has more to show
  (`launcher-idiom-2026-10-23.md`, "sheets where we have more").
- **Start in, not a default page.** Launcher3 jumps to its default screen; this launcher lands on
  whatever Start in names, which may be the Workspace or the display window.
- **The Appearance editor ignores outside taps** (rule 6): everything on screen is a surface to pick.

## Work owed

One change at a time, each with its own test and pong check:

| Rule | Change |
|---|---|
| 2 | Drawer search text and scroll reset on a home press |
| 4 | App menu cap from five shortcuts to four, then More shortcuts |
| 5 | Audit Edit pages, the widget editor and the Appearance editor for Done/Back and the return place |
| 6, 7, 8 | Audit outside tap, widget tap/long press/resize exit, folder dissolve at one app |
| 10 | Audit the haptic call sites against the three moments |
| 11 | List every custom gesture with its tap path and cue; the gaps become tickets |

Delete each row as it lands; move this file to `project-docs/reference/` when the table is empty.
