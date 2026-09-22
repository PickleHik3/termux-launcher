# feat/widget-orientation-layouts — spec item 10 (D4)

Done: bug reproduced in a failing test (portrait 5x4 -> landscape 3x6 -> portrait 5x4 lost a
  row off the tall widget); schema v4 adds a per-orientation shelf in LauncherWidgetRepository
  (`applyOrientation(key, grid)`), v3 reads as v4 with nothing shelved; TermuxActivity's
  applyWidgetGridPreference names the orientation before applyGrid.
In progress: nothing.
Next: P5 (restore-quiet) builds on this record schema — see the contract in the report.
Gotchas:
- The records are always the orientation on screen; the shelf holds only the other sides, so
  every existing caller and every mutation is unchanged.
- applyOrientation returns true only when widgets moved; the grid is usually unchanged on a turn
  so LauncherWidgetHostController.applyGrid early-returns and never notifies — the activity calls
  WidgetPaneController.onStart() (which is just render()) instead. A dedicated pane method would
  be cleaner once the other agents' branches land; that file was out of scope here.
- Version assertions in LauncherWidgetRepository{Migration,V2,V3}Test moved 3 -> 4, and the
  "unknown newer payload" case in V3Test moved 4 -> 5.
