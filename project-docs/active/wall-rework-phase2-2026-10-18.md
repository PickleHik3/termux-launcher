# Wall rework, phase two (feat/wall-rework-phase2, from 2026-10-18)

Phase one is frozen on `feat/wall-rework` at 96525b145 (spec: `wall-rework-2026-10-17.md`, ADR 0008,
glossary "The wall and its chrome"). Phase two builds on it on this branch, one slice at a time, each
slice merged only with its unit tests green and an emulator or Surface pass recorded in
`project-docs/device-checks.md`.

Merged in: PR #82 (`feat/glass-controls-unify`: one set of glass controls in Look › Custom,
Adaptive glass always on) at 03aac5a8b, merged into this branch on 2026-10-18 (the branch tip then
was 0843ad75e, 03aac5a8b plus one device-checks note, and that is what went in); its owed device
checks stay owed here too. When #82 lands on `pre`, merge `pre` into this branch again.

## Slices, in order

1. **The status bar's capsule form.** A third form beside compact and expanded: a thin material
   capsule at the top centre. A continuous status swipe sizes the bar by where the finger lets go;
   a quick swipe steps one form; the bar folds to the capsule after a short idle; the capsule is
   Minimal mode's default form. Everywhere on the wall (Home with floats, the Workspace).
   Status 2026-10-18: merged at 1458207d8 + d3f2fa8e7 (StatusBarFormPolicy, fold motion, gesture,
   view); seen on the emulator; ink fix and the Minimal-mode bar owed.
   Status 2026-10-18 (later): **removed.** The developer chose two looks instead: the window pages
   keep the bar's compact and expanded forms as before the capsule, and Home pages wear the Home
   pills (below). `StatusBarFormPolicy`, `StatusBarCapsuleDrawing`, the idle fold, the capsule's
   stop on the swipe, Minimal mode's capsule start and the float landing's unfold are gone; Minimal
   mode does what it did before.
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
   Status 2026-10-18 (emulator round 3, C): the re-tell at the session's end did not heal the
   Clock; it is a built-in widget, which reads its own size, so no size was ever stale. The zoom
   was: a built-in's zoom search measured its layout at specs it had measured before, the
   framework answered them from its measure cache without measuring the labels the losses are
   read from, so a cell back at an earlier size (Undo, Discard, a Style flip and back) came back
   at the largest zoom and the zoom memo kept it. Every trial now measures the layout afresh
   (`BuiltinWidgetView.lossesAt`). For built-ins the `resyncSizes` re-tell was never the cure;
   whether app widgets need it is still unproven. Re-check owed (device-checks, slice 2).
3. **Parallax between Home pages and windows** (the wallpaper pans with the wall).
   Status 2026-10-18: built on a worker branch. `WallParallax` spreads the spare width over the
   row (`p / (count - 1) * spare`); `PaneWallController.rowPosition` composes the wall's offset
   between views, Home's grid translation (reported per frame through `WidgetPaneView`) and the
   terminal page's nudge as a window arrives (the window bar names the window it left, so a jump
   pans over every window between). A Home page or window added or removed snaps to the new
   spread. Unit tests green; emulator pass owed (device-checks, slice 3).
   Status 2026-10-18 (emulator pass): steps even, seam and drags clean. The 1 → 3 bar jump's
   196 px first step: the departure was already passed; the nudge now starts before the re-sync.
   Likely the emulator's slow first frame on the settle curve; re-check owed with a 1 → 2 jump
   beside it.
   Status 2026-10-18 (fix round 3): the bar jump's wrong-way start was the departure card's
   glass ground drawn without the parallax offset (Home's stretch of the picture under any
   window); fixed in `wallpaperGlassGround`. Re-check owed (device-checks, slice 3).
4. **Saved sessions record the float flag and page**; a saved float loads as a float.
   Status 2026-10-18: built on a worker branch. File version 4: a window may carry `homeFloat`
   { page, minimised, portrait?, landscape? }; absent, or malformed, it is a window of the row, so
   v1–3 files load as before. Save writes the current session's Home floats after its row's
   windows, in the order they were opened. Load floats them on their page
   (`HomeFloatPolicy.restoredPage`: the last Home page when theirs is gone; tiled when there are no
   Home pages), minimised as saved, at their shape for the screen's orientation with the other
   seeded into their memory; a float with no shape is born as a new float is. Every saved float
   floats (`restoredAsFloats`): a session saved as floats only loads as floats only and adds no
   session (a session is its row's windows; the empty Workspace is no session, as after closing the
   last window), so the Workspace may come back empty, with the wall on Home. The row's current
   window skips floats (`restoredRowIndex`, -1 for an empty row) and the wall stays where it was.
   An empty Workspace with Home floats saves them as such a session. Replace also closes the live
   Home floats.
   Maximise's restore is not saved. Unit tests green; emulator pass owed (device-checks, slice 4).
   Status 2026-10-18 (emulator pass): save and load pass. Fixed from it: the last window or float
   closing finished the activity (the service stopped itself; it now stays while there is a Home,
   `WallRow.lastShellClosedShowsHome`); a Home float dragged off-screen (`HomeFloatPolicy
   .clampInRoom`); the drawer's Saved half empty from the list key (older bug). Re-checks owed.
5. **Window reorder** by drag in the window bar; consume/expel of panes into and out of columns.
   Status 2026-10-18 (slice 5a, window reorder): built on a worker branch. A long press on a
   window's chip in the window bar lifts it (scale, elevation, haptic); the finger carries it, the
   other chips part, and the release drops it (`WindowReorderPolicy`: the lifted centre passes a
   neighbour's resting centre by 6dp, with hysteresis; the landing place is read from the finger,
   so a chip as wide as the last one can still pass it; auto-scroll near the edges of an
   overflowing bar). Only the row's windows' chips lift: not the display's, not the Home floats'
   after them, nothing on Home or Display. The row is `WSession.windows` in `TermuxActivity`
   (`moveWindow`; the current window stays current), so the bar, the drawer, the wall's inner
   index, the Ctrl+Alt window keys and Saved sessions follow from one rebuild. `window.move`
   (`to=left|right|<place from 1>`) and the palette's Move window left / right (Workspace only),
   no default stroke. The status bar's vertical window column (a left or right bar) has no drag.
   Unit tests green; emulator pass owed (device-checks, slice 5a). Next: 5b, consume/expel.
   Status 2026-10-18 (slice 5b, consume/expel): built on a worker branch. The row's windows are
   Niri's columns (`ColumnPolicy`). `pane.consume` (`from=right|left`, default right,
   `Ctrl+Alt+,`, palette "Consume next window into this pane") pulls the neighbour window's whole
   tree and floats into the current window beside its focused pane, on `pane.split`'s automatic
   axis, and focuses the consumed window's focused pane; that window leaves the row and the
   current window stays on screen. `pane.expel` (`to=right|left`, default right, `Ctrl+Alt+.`,
   palette "Expel pane to its own window") detaches the focused pane into a new window right after
   the current one (before it with `to=left`); focus goes with it, as in Niri, so the new window is
   current and the Workspace slides to it like a new window. A one-pane window, a focused
   scratchpad and a focused Home float refuse; Home floats are never consumed; no session closes.
   `window.move` got its title ("Move window to…"). Unit tests green; emulator pass owed
   (device-checks, slice 5b). Slice 5 done.
6. **The first-boot tour**, rewritten for the wall (switched off since 651ea9d7f).
   Status 2026-10-18 (slice 6): built on a worker branch and switched back on. Six lessons in
   three chapters, the engine kept (`TourRun`, `TourController`, the overlay): Home
   (`page_swipe`: a plain swipe across the seam, cleared by the new `wall.seam_to_workspace`, then
   a border drag back, cleared by `wall.seam_to_home`; `find_apps`: the drawer off the dock over
   Home), the Workspace (`keyboard`, `status_swipe`: a slow pull open, a quick swipe up to fold)
   and anywhere (`find_action`: the palette from Home's free area or a corner tab, heard now from
   the palette's own funnel; `find_help`: a window's corner, then ?). Each stage names its half
   of the wall (`TourStep.Side`): a card read on the other half asks the way there, and a signal
   heard there does not clear it. Each lesson starts on its first stage's half (`TourRun.startSide`,
   `HomeHost.resetFor`): Home page 1 as the home press leaves it, or the first window with its
   keyboard up and the status bar folded; the closing card shows where the last lesson ended. With
   no window beside Home the seam lesson is dropped, without Home both Home lessons. `border_drag` became `page_swipe` (help's practice
   too); `RUN_VERSION` 8. Unit tests green; emulator pass owed (device-checks, slice 6).
7. **The icon grid on Home pages**: app icons, folders, drag between pages. Born on slice 2b's
   rule: its rim is Home's Margin, as the widget grid's is.
   **7a done** (app icons): an icon is a Home item of kind APP in the widget store (schema v6
   writes a `kind` on every record and an `app` on icons; v5 and older load with the kind
   inferred), keyed negative from the built-ins' allocator, so it shares the widget grid's cells,
   pages, moves and rim. 1×1, icon pack applied, label under it; tap launches, hold is the page's
   edit mode (move, carry across pages, remove; no resize). In from the app menu's **Add to Home**
   and the Home sheet's **Add an app** (`HomeAppPicker`): the page in front, else the first with
   room, else a new page (`HomeItemPlacementPolicy`). A gone app's icon stays, faint, until
   removed, and revives if the app returns. Unit tests green; emulator pass owed (device-checks,
   slice 7a).
   **7b done** (folders): a Home item of kind FOLDER (schema v7 while one exists; v6 and older
   unchanged): a name and an ordered list of apps with their labels, 1×1, same negative keys,
   under a synthetic provider. Rules in `HomeFolderPolicy`: an icon rested 300 ms on another icon
   and released makes a folder named after the dropped app (dropped app first); on a folder it
   joins at the end; cap 16; Remove from folder puts the app on the page in front (else the first
   with room, else a new page); one app left turns the folder back into its icon, none removes
   it; names trimmed, capped at 40 code points. A merge keeps the target's key, a collapse the
   folder's. A malformed stored folder is dropped, the rest of the wall stands; a folder of one
   loads as that app's icon. Cell: the drawer folder's 2×2 miniature on a plate, name under it;
   popup: the dock's (menu module, `LauncherFolderPopupController`, `FolderRenameTitleView` and
   the activity's `FolderRenameController` for rename). No drag out of the popup: Remove from
   folder places the app instead. Unit tests green; emulator pass owed (device-checks, slice 7b).
   Status 2026-10-18 (emulator pass fixes): the Home folder popup is now a view over the Home
   pane (`HomeFolderOverlay`), not a window, so the in-app keyboard types into its rename and is
   not dimmed; its title, labels and rename take the capsule's ink rule. The dock's folder popup
   is unchanged. Re-check owed (device-checks, slice 7b).
   **Slice 7 complete.**
8. **Per-toplevel X11 windows** (needs a rootless server).
   Status 2026-10-18: not started; a developer decision. The display is the vendored termux-x11
   Xlorie server (`x11-server/`, prebuilt `libXlorie.so` built by `build_x11_native.yml`, source
   outside this repo): rootful, one root framebuffer composited with GLES
   (`project-docs/reference/launcher/pane-wall-x11-study.md:37-44`). The only toplevel data is the
   EWMH client list read over the X socket (`X11WindowList`, with openbox as the window manager).
   Per-toplevel windows need the server to export each toplevel as its own surface (XComposite
   or a rootless DDX): native server work on the upstream fork, with the launcher side (one wall
   window per toplevel, map/unmap/geometry from the X connection) only after that. Options: fund the
   server work, or keep one display window per X display and drop this slice.
9. **Rebuilding the row and the floats after a background kill** beyond Saved sessions.
9. **Rebuilding the row and the floats after a background kill** beyond Saved sessions. Done in
   code: `ArrangementRecovery` decides each start (recreate, adopt, rebuild, empty, fresh). An
   activity recreate with the service alive brings the floats back with the row's trees around
   the same shells (keyed by `TerminalSession.mHandle`; the process keeps the last layout for an
   activity that returns without instance state). The arrangement is autosaved in the version 4
   model to the hidden `~/.termux/workspaces/.autosave.json` (1.5 s debounce, at once when empty,
   on stop; Exit writes the empty one), and a cold start with no live shell rebuilds it with fresh
   shells in the saved folders, never replaying a command, then a notice. Docs:
   `durable-workspaces.md` › Autosave and recovery, the user guide's After a restart, spec §9.
   Unit tests green; emulator pass owed (device-checks, slice 9).
   Status 2026-10-18 (emulator pass fix): a rebuilt window stayed on `exit`. Rebuild and Load now
   take their current window through `activateSessionInPanes` like any shell; the cause is not
   confirmed off the device. Re-check owed (device-checks, slice 9).

## Rules carried over

- Glossary words only; policy with arithmetic first, views thin; settings pages are fragile.
- Workers branch from pre, which has moved past this branch's base: a worker merges
  `feat/wall-rework-phase2` with `--no-ff` and the orchestrator cherry-picks its commits here.
- Device checks: emulator first (no built-in widgets there: add a Battery widget to its Home before
  a status bar or widget pass), then the Surface (floating keyboard, Launcher3 as default home,
  x86_64), pong last and only when asked.
