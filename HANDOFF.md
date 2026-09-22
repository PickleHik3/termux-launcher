# HANDOFF: feat/widget-small-fixes

Done:
- (4) beginDeferringUpdates/endDeferringUpdates on SafeLauncherAppWidgetHostView, 1000ms
  auto-release, wired into WidgetPaneController.beginMoveDrag/endMoveDrag only.
- (6) WidgetCornerPolicy (new, pure) + WidgetCellView wired to it via onLayout/onSizeChanged;
  respects android:id/background clipToOutline opt-out.
- (7) LauncherWidgetHostController.onHostSizeCommitted now reads Platform.getOptions(id) (new
  Platform method, added to AndroidPlatform + WidgetTestFixtures.Platform) and skips the
  provider write only when the LIVE bundle matches, not the stored one.
- messageFor's six literal strings moved to strings.xml (widget_unsupported reused as-is,
  widget_add_busy/widget_configuration_unavailable/widget_storage_failure/widget_add_failed/
  widget_grid_full added).

In progress: nothing left in this phase's scope.

Next: none - contract handed back to the orchestrator for the resize-displace and colour
phases to build on.

Gotchas:
- SafeLauncherAppWidgetHostView's timeout uses its own Handler(Looper.getMainLooper()), not
  View.postDelayed - a mid-drag lift can detach the view from any window, and postDelayed on a
  detached view only queues until next attach (learned the hard way via a Robolectric idleFor
  that never fired).
- WidgetCornerPolicy is a plain pure class but its test needs
  @RunWith(RobolectricTestRunner.class) anyway: android.graphics.Rect throws "Stub!" on a bare
  JVM outside Robolectric's shadow.
- Did not touch resizeDrag/endResizeDrag/WidgetEditPolicy.java (resize-displace phase's) or
  LauncherWidgetRepository/LauncherWidgetRecord (orientation phase's), per scope.
