# Wall rework, phase two (feat/wall-rework-phase2, from 2026-10-18)

Phase one is frozen on `feat/wall-rework` at 96525b145 (spec: `wall-rework-2026-10-17.md`, ADR 0008,
glossary "The wall and its chrome"). Phase two builds on it on this branch, one slice at a time, each
slice merged only with its unit tests green and an emulator or Surface pass recorded in
`project-docs/device-checks.md`.

## Slices, in order

1. **The status bar's capsule form.** A third form beside compact and expanded: a thin material
   capsule at the top centre. A continuous status swipe sizes the bar by where the finger lets go;
   a quick swipe steps one form; the bar folds to the capsule after a short idle; the capsule is
   Minimal mode's default form. Everywhere on the wall (Home with floats, the Workspace).
   Status 2026-10-18: merged at 1458207d8 + d3f2fa8e7 (StatusBarFormPolicy, fold motion, gesture,
   view); seen on the emulator; ink fix and the Minimal-mode bar owed.
2. **Style, Corners and Margin on Home** as Home's own settings (today global).
   Status 2026-10-18: built on a worker branch (keys `home_layout_style`,
   `home_surface_corner_radius`, `home_surface_side_gap`, seeded from the Workspace's while unset;
   `HomeChromePolicy.wearsHomeShape`; the chrome's reads follow the committed page and a settle
   across halves that differ redresses; the Appearance editor edits the half it stands over). Unit
   tests green; emulator pass owed.
   Status 2026-10-18 (slice 2b): Home's Margin is the one air on Home. The widget grid's rim (was a
   fixed 6dp) and the Home floats' room inset both read it, with the dock's cards; the grid's caps
   take it as the rim, so a wide Margin lowers them (the rim follows a drag, the caps settle on
   release). Upgrade moves the rim to the seeded 12dp, accepted; on a 386dp phone that lowers the
   column cap from six to five. Unit tests green; emulator pass owed.
   Status 2026-10-18 (emulator pass on slices 2 and 2b): found the rim at about twice the Margin
   (the Home page stood in the chrome's opening, a Margin in, and the rim added another), the
   cold start's first frame in the Workspace's shape until the first settle, a Clock widget cut
   off after a Margin change and Undo, "· Home" in the editor over what looked like a window
   after a widget edit session, and Appearance not opening from a corner toolbar with a Home float
   open. Fixed: the Home page reaches the wall's edges and the rim is the whole Margin
   (PaneWallPolicy.pageInsetPx); the first frame wears the opening page's chrome
   (WallRow.openingKind seeds the committed page). Not fixed, causes not found from the code: the
   Clock clipping (a rim change and its Undo do reach the provider in a unit test; the stale size
   is larger than the cell), and the "· Home" and Appearance reports, where the toolbar described
   (pencil, plus, palette, ?) is Home's own, so the wall was resting on a Home page, probably one
   covered by a float. Re-check owed (device-checks, slice 2).
   Status 2026-10-18 (emulator round 2): A, B and D pass. C and E fixed. C, the Clock drawn for
   an earlier size after Undo or a Style change: the cells passed through several sizes in one
   editor session and nothing re-told the provider at the end. Now the session's end tells every
   widget whose cell moved its size again, once the wall rests on Home
   (`WidgetGridView.resyncSizes`). A Style change moves the cells through the chrome, not the rim:
   the status bar's height and the dock's bottom gap differ under Floating. E, the editor from a
   window opening on Layout · Home: the surface brought back the Layout page last left over Home,
   with its place, and the layout session held the wall on Home. The Home float played no part.
   A remembered place now comes back only on the half the wall rests on
   (`HomeChromePolicy.restoredEditorPlace`). Re-check owed (device-checks, slice 2).
3. **Parallax between Home pages and windows** (the wallpaper pans with the wall).
4. **Saved sessions record the float flag and page**; a saved float loads as a float.
5. **Window reorder** by drag in the window bar; consume/expel of panes into and out of columns.
6. **The first-boot tour**, rewritten for the wall (switched off since 651ea9d7f).
7. **The icon grid on Home pages**: app icons, folders, drag between pages. Born on slice 2b's
   rule: its rim is Home's Margin, as the widget grid's is.
8. **Per-toplevel X11 windows** (needs a rootless server).
9. **Rebuilding the row and the floats after a background kill** beyond Saved sessions.

## Rules carried over

- Glossary words only; policy with arithmetic first, views thin; settings pages are fragile.
- Workers branch from pre, which has moved past this branch's base: a worker merges
  `feat/wall-rework-phase2` with `--no-ff` and the orchestrator cherry-picks its commits here.
- Device checks: emulator first (no built-in widgets there: add a Battery widget to its Home before
  a status bar or widget pass), then the Surface (floating keyboard, Launcher3 as default home,
  x86_64), pong last and only when asked.
