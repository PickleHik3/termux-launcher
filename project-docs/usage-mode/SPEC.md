# Usage mode — spec v1 (2026-09-28)

Termux Launcher is three things at once: a terminal, a home screen and a Linux display. Many
installs want one or two of them. The old two-way switch (launcher / terminal only) hid surfaces
but left their machinery running — the widget host listening for a page that was GONE, the app
catalogue warming under a hidden dock, the X view's native context allocated for a display that
was off. **Usage mode** replaces it with three nested presets that a user can find, and makes a
surface that is off release what it held.

Naming: the modes are `terminal`, `home` and `display`, stored under the old key
`app_launcher_use_case_mode`. In the UI the row is **Use as** and the choices are **Terminal**,
**Terminal + Home screen** and **Terminal + Home screen + Linux display**. The summary reads
**Custom** once a switch below has moved the surfaces off the stored preset.

Branch: `usage-mode`, cut from dev 86d80ca8.

## The modes

A mode is a **preset, not a lock**. Picking one writes the real surface switches, and every one of
them stays settable afterwards. What the user picked is stored; what the switches spell is derived
(`LauncherUseCaseMode.modeMatchingSwitches`), and the row names the stored mode only while the two
agree.

| | Terminal | Terminal + Home screen | + Linux display |
|---|---|---|---|
| Wall places | Terminal | Widgets (if the pane is on), Terminal | Widgets, Terminal, Display |
| Apps row | hidden, both orientations | shipped default (bottom / left rail) | same |
| A-Z row | off (global flag and both layout keys) | restored from the snapshot, else on | same |
| App drawer, widget pane | off | restored from the snapshot, else on | same |
| Show in Recents when not home | on | restored from the snapshot | same |
| `x11_display_enabled` | off | off | on |
| Shipped extra-keys row | no place switch at all | Widgets and Terminal switches | all three |
| Status bar place marks | none | Widgets and Terminal | all three, display glyph |
| Along-bar swipe | chip strip scroll; the fold takes one slop | pages the wall | pages the wall |
| Package receiver, LauncherApps callback | not registered | registered | registered |
| App catalogue warm-up, package scans, icon caches | never run; caches cleared on entry | run | run |
| Widget host | not built | built | built |
| X view, server connection | not built | not built | built |
| `refreshLinuxApps` on resume | skipped | skipped | runs |

Going into terminal mode captures the A-Z, drawer, widget-pane and Recents switches in a snapshot
preference; leaving it restores them, or the shipped defaults when there is nothing to restore.
Re-picking the mode the install is already in does nothing.

**Custom.** A home surface on under terminal mode, every home surface off under a home mode, or
the display on with no home screen under it, all read as Custom; the stored mode is left alone.
The Display switch moved on its own (Settings → Display, the first-run card, the page's power
button, a Linux app tapped in the drawer) moves the mode between `home` and `display`; under
`terminal` it is left as it is and the row reads Custom.

**Migration.** The old value `launcher` maps to `display` when `x11_display_enabled` was true,
otherwise to `home`; anything unknown maps to `home`. The mapped value is written once at
activity start (`migrateIfNeeded`). A build with `BuildConfig.X11_SERVER = false` never offers
`display` and reads a stored `display` as `home`.

## Where it lives

- **Logic.** `LauncherUseCaseMode` (app, pure over `TermuxAppSharedPreferences`): the modes,
  `resolve`/`migrateIfNeeded`, `applyMode`, `modeMatchingSwitches`/`summaryMode`,
  `onDisplaySwitched`, the label resources. `LauncherUseCaseModeTest` covers all of it with the
  display offered and not.
- **Display switch follow-ups.** `X11DisplaySwitch.write/onWritten` — the prefix commands in or
  out, the drawer's Linux apps re-listed, the mode moved — used by the style data store, the
  Display page's data store (which routes the key through the style store; its own branch was
  unreachable before) and the activity.
- **Settings.** `root_preferences.xml` has a `ListPreference` above the Launcher header;
  `SettingsActivity.RootPreferencesFragment` fills its entries from `offeredModes`, shows the rich
  radio dialog ("How will you use it?"), sets the summary on resume and offers the home-app
  chooser when Terminal is picked while this launcher answers Home. `TermuxStylePreferencesDataStore`
  applies the preset and schedules a recreate; under terminal mode it also clears the
  `LauncherAppDataProvider` caches.
- **Wall.** `PaneWallController.Host.isDisplayEnabled()` is the display switch; the Display page
  is attached only while it is on and `detachDisplayPage()` removes it. `TermuxActivity`:
  `isWidgetsPageWanted()` gates the widget host, page and controller; `availableWallPages()` is
  the one list the key row and the eligibility policy read.
- **Display lifecycle.** `reconcileEmbeddedDisplay(then)` runs on every resume and before a
  pending styling reload: attaches (`attachEmbeddedDisplay`) or tears down
  (`tearDownEmbeddedDisplay`: stop sequence abandoned, window list stopped, controller destroyed
  and nulled, page removed, chrome refreshed). With a server running it asks first — Stop runs the
  existing `DisplayStopSequence` and the teardown follows the server going; Leave it running only
  takes the page away. Nothing stops a server silently.
- **Catalogue gate.** `isSuggestionBarEnabled()` reads `PlaceChromePolicy.appsShown` of the
  current orientation's layout, not the legacy `app_launcher_apps_row_enabled` master.
  `consumesPackageChanges()` gates the two package listeners.
- **Extra keys.** `ExtraKeysDefaultRow.forWall` cuts the shipped row (only the shipped row) in
  `TermuxTerminalExtraKeys.setExtraKeys`; `ExtraKeyEligibility.isUsable(value, place, pages)`
  greys a place switch whose place the wall lacks in a row the user wrote.
- **Status bar.** `StatusBarLensMetrics.marks` returns nothing for a wall of one place;
  `syncPlaceBar` shows the display's running state only while Display is on the wall.
- **First run.** The permissions card's Linux display row appears only under the display mode,
  decided as the card goes up. The tour has a `usage_mode` choice card before `home_choice`
  (`TourRun`, `TourAction.USE_*`, `TourController.Choice.USE_*`,
  `FirstBootTour.UsageModeHost`); Terminal drops the home-screen card, either other answer keeps
  it; the answer goes through `TermuxActivity.applyUsageMode`, the same path as Settings.
  `RUN_VERSION` is 5 and a v4 run in progress is mapped by card name.

## Known gaps

- The tour's usage card shows no current selection; a replayed tour asks again.
- A server left running by "Leave it running" is invisible to the launcher until the display is
  switched on again; the next Turn on reconnects to it.
- The first-run card's display row and the tour's usage card can both turn the display on during
  one first launch; the second is a no-op.

## Device checks owed

1. Fresh install: the permissions card has no Linux display row; the tour asks the usage question
   after the last lesson; Terminal skips the home-screen card and the launcher comes back with a
   bare wall (no marks, no place keys); Display comes back with three places.
2. Update from a build with the two-way switch: `launcher` + display on reads as Terminal + Home
   screen + Linux display; `launcher` + display off reads as Terminal + Home screen; `terminal`
   stays Terminal.
3. Settings → Display switch off with a display running: the question appears on the way back to
   the launcher; Stop closes the apps and the page goes; Leave it running keeps the server (a
   later Turn on reconnects). Switch on: the page appears without a recreate.
4. Settings → Use as → Terminal while this launcher is Home: the prompt appears; Keep leaves it.
5. Terminal mode: no package receiver (`dumpsys activity broadcasts`), no catalogue warm-up in
   the log, no LorieView in the hierarchy, no widget host.
6. Home mode with the display off: the shipped row has no Display key; a custom row's Display key
   is greyed; the status bar shows two marks.
