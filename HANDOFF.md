# feat/widget-restore-quiet — spec item 8 (D1 = B, small form)

Done: wholesale-loss predicate `WidgetProviderReconcilePolicy.isWallLost`; repository
`resetToEmptyWall()` (records + reservation + shelf + pages, one commit); controller
`clearLostWall()` wired into `reconcileProviders` before the per-record loop; `AddResult.WALL_RESET`;
`widget_wall_reset` string; pane shows it once. Tests: `WidgetLostWallResetTest` (6).
In progress: nothing.
Next: full suite, then report.
Gotchas:
- Threshold is 2 records (`LOST_WALL_MIN_RECORDS`): with one widget a lost wall and an ordinary
  uninstall are indistinguishable, and the existing single-record tombstone tests rely on it.
- The wholesale branch returns early, so the orphan-ID recovery pass is skipped that round.
- No backup agent / no HOST_RESTORED receiver / no ID remapping — D1 ruled them out.
