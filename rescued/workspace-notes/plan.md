Here is Claude's plan:
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
Termux Launcher to-do batch

Context

A backlog of multiplexer, command-palette, and bug-fix items has accumulated. There is no local
Android build environment, so every change must be verifiable either by a JUnit test running on
CI or by an explicit on-device check listed at the end. Work happens on dev (which is currently
one docs commit ahead of main); pushing dev triggers .github/workflows/debug_build.yml
("Build nightly": testDebugUnitTest + assembleDebug + 5 debug APKs).

The guiding constraint throughout: extract logic into pure static helpers and unit-test them,
because CI is the only thing that can check the work. Where no honest test seam exists, that is
stated rather than papered over.

Two items are explicitly out of scope:

- Status bar themes — deferred at the user's request.
- Anything requiring a privileged (Shizuku/root) backend as a hard dependency.

Decisions taken

┌───────────────────┬───────────────────────────────────────────────────────────────────────────────────────────┐
│ Topic │ Decision │
├───────────────────┼───────────────────────────────────────────────────────────────────────────────────────────┤
│ Session name cap │ Raise MAX_CODE_POINTS 5 → 8, rename the scratchpad shell to "scratch", and stop adopting │
│ │ the hidden scratchpad as a top-level session │
├───────────────────┼───────────────────────────────────────────────────────────────────────────────────────────┤
│ App-launch │ Live key capture inside the palette, writing to ~/.termux/termux-launcher-bindings.conf │
│ keybinds │ │
├───────────────────┼───────────────────────────────────────────────────────────────────────────────────────────┤
│ Pane kill │ SIGHUP to the process group, ~150 ms grace, then SIGKILL the group │
├───────────────────┼───────────────────────────────────────────────────────────────────────────────────────────┤
│ Shell activity │ Native onTextChanged output activity, unioned with ForegroundInfo.idle when privileged. │
│ signal │ No procfs fallback needed │
├───────────────────┼───────────────────────────────────────────────────────────────────────────────────────────┤
│ Process-group │ Pure Java via Os.kill(-pid, …). No JNI change — keep the killpg recipe as a contingency │
│ kill │ only │
├───────────────────┼───────────────────────────────────────────────────────────────────────────────────────────┤
│ CI │ One commit per item on dev, single push at the end │
└───────────────────┴───────────────────────────────────────────────────────────────────────────────────────────┘

Step 0 — Branch

git checkout -b dev --track origin/dev # or: git checkout dev && git reset --hard origin/dev
origin/dev is one commit behind main in content only (5108d353 docs); main has the v0.2.30
release merges. Do not rebase dev onto main — that is a release-flow decision, not ours.

---

Group A — Multiplexer & status bar

A1. Session name cap and scratchpad naming (do first — A2/A6 depend on it)

- app/src/main/java/com/termux/app/terminal/WindowSessionName.java — MAX_CODE_POINTS 5 → 8,
  with a comment naming the two copy mirrors that must move in lockstep.
- Mirrors: R.string.title_rename_window_session ("5 characters max" → 8) and the two
  "capped at 5 characters" literals in LauncherToolRegistry (window.rename, session.rename).
- TerminalPaneController (~1030): SCRATCHPAD_SESSION_NAME → "scratch"; add
  LEGACY_SCRATCHPAD_SESSION_NAME = "scratchpad" and
  public static boolean isScratchpadShellName(@Nullable String) accepting both. Rewrite the
  private isScratchpadLeaf() to delegate — that keeps findScratchpadLeaf, closeFloat,
  rememberScratchpadFrac and actionSlotCount() correct with no other edits. In
  toggleScratchpad(), fall back to findIdleShellByName(LEGACY_…) before creating a shell, so an
  existing scratchpad survives the app update instead of being duplicated.
- Add a static shouldAdoptAsWindowSession(String shellName) on TerminalPaneController and use it
  to guard both adoption sites: TermuxActivity.ensureWindowsForServiceSessions() (~9967) and
  activateSessionInPanes() (~10740). A hidden scratchpad is a floating leaf, not a window session;
  adopting it is what minted the bogus "scrat" row.

Tests — WindowSessionNameTest: new 8-code-point boundary, a surrogate pair at that boundary,
and scratchpadConstant_fitsTheCapUnchanged (fails the build if anyone lowers the cap or lengthens
the constant). TerminalPaneControllerTest: isScratchpadShellName accepts both literals and
rejects null/""/"scrat"; shouldAdoptAsWindowSession rejects both scratchpad names.

A2. Rename button on sessions-panel rows

The plumbing already exists: SessionsPanelView.Listener.onSessionRenameRequested(int)
(SessionsPanelView.java:43) is dispatched from the row long-press (~281) and implemented at
TermuxActivity.java:10541. The only gap is discoverability — this is a view-only change.

- New res/drawable/ic_sessions_panel_rename.xml — a pencil matching
  ic_sessions_panel_close.xml's shape conventions (10dp/viewport 10, transparent fill, white
  stroke tinted at runtime, round cap/join), strokeWidth="1.1".
- New string sessions_panel_rename_session ("Rename session %1$d") beside
  sessions_panel_close_session.
- SessionsPanelView.buildRow() — clone the close button construction (~277-292), 28×28dp,
  gravity = CENTER_VERTICAL, same tint. Add it before close so the destructive action stays
  at the outer edge. Keep the long-press as a redundant shortcut.
- The +28dp of chrome must be folded into desiredWidthDp() — do it as part of A6, in one pass.

Tests — new app/src/test/java/com/termux/app/statusbar/SessionsPanelViewTest.java (Robolectric,
same header as SessionsIndicatorViewTest): rename button dispatches for its own row; close still
dispatches close and not rename (catches an add-order swap); content description is indexed.

A6. Sessions pop-down marquee refinement

The brief's premise needed correcting. desiredWidthDp() computes the panel width from the
measured text plus chrome, so overflow is impossible except where a cap bites. With chrome = 89dp
the 320dp clamp permits 231dp of text — more than the dp(190) text cap. The 320 clamp is dead
code; dp(190) is the sole cause of overflow. Widening the clamp alone changes nothing. Worse, a
third cap is invisible to the view: StatusCardHost.portraitMaxWidthPx() (~232) shrinks the popup
to screenPortraitWidth - 24dp.

- New pure class app/src/main/java/com/termux/app/statusbar/SessionsPanelMetrics.java (no Android
  imports): MIN_WIDTH_DP = 200, MAX_WIDTH_DP = 320, MARQUEE_MIN_OVERFLOW_DP = 12f, a
  Layout {widthDp, availableTitlePx} result, plus
  calculate(widestTextPx, chromePx, screenPortraitWidthPx, density) and
  shouldMarquee(textWidthPx, availableTitlePx, minOverflowPx).
  calculate derives the text cap from the clamp (maxTextPx = MAX_WIDTH_DP*density - chromePx)
  so the two constants can never drift, and mirrors portraitMaxWidthPx with a comment naming it as
  the authority.
- SessionsPanelView: add chromePx() as the single place row chrome is described — now including
  A2's 28dp button; extract measureWidestTextPx() / measureTitlePx(Session) from the existing
  TextPaint loop; compute mLayout in bind() (the call site already orders bind() before
  desiredWidthDp() at TermuxActivity:10504-10507); desiredWidthDp() becomes a getter. Gate the
  marquee block on shouldMarquee(...), with a comment on why: a 1-2 character tail scrolls out
  and restarts inside the stock MARQUEE_DELAY, reading as a twitch. Non-qualifying rows keep
  TruncateAt.END.

Net effect: the effective text cap rises from 190dp to ~203dp and the chrome is finally accounted
for, removing most overflows outright; the threshold catches the residue on narrow screens.

Tests — new SessionsPanelMetricsTest (plain JUnit): min/max width; the
availableTitlePx == measured text invariant (this is the test that would have caught A2's chrome
bug); narrow-screen shrink; shouldMarquee false for a 1-2 char tail, true for a long one, false
when text fits, and at the exact boundary; text cap derives from the clamp. Plus two
SessionsPanelViewTest cases asserting title.getEllipsize().

A3. Settings cog in the expanded status bar

Always visible whenever the status row is visible; no new preference.
applyTopStatusBarInteractiveHeight() only alpha-fades terminal_top_widget_area —
terminal_status_row stays opaque in both swipe states, and collapsed is the default, so gating on
expansion would hide the cog from most users most of the time. Splits-off needs no handling:
refreshTerminalWindowBar() sets the whole terminal_window_bar_host to GONE at
TermuxActivity.java:10254 before its early return, so there is nowhere to put a cog.

- New res/drawable/ic_status_bar_settings.xml — copy ic_settings.xml, set 10dp/10dp (keep
  viewport 24), and delete its android:tint line (it would compose with the runtime tint).
- activity_termux.xml — AppCompatImageButton @+id/terminal_status_settings as the last child of
  terminal_status_widgets: 18dp × match_parent, scaleType="center", background="@null",
  layout_marginStart="4dp". match_parent + center keeps the glyph optically centred as the row
  breathes 24⇄22dp without participating in applyInteractiveStatusRowGeometry(). No
  MaterialDotSeparatorView — dot separators punctuate readings, and the cog is an action.
- TermuxActivity.updateStatusWidgets() — tint it every pass (so theme changes take) at
  termuxColorOnSurfaceVariant alpha 152, deliberately quieter than the accent-tinted readings
  beside it; wire the click once via the established getTag() == null idiom.
- New openTerminalStatusSettings() beside openLookAndFeelSettings() (~8966), using
  TerminalStatusPreferencesFragment and R.string.settings_destination_terminal_status —
  verified: R.string.termux_status_preferences_title does not exist.
- New string termux_status_settings_content_description.

Tests — none. This is a layout child, a runtime tint and an Intent factory inside an 11k-line
activity; inflating activity_termux.xml under Robolectric drags in RealtimeBlurView and the whole
custom-view graph for no assertion worth having. Verified by assembleDebug and on-device check 7.

A4. Reposition and unify the transient notice chip

- SessionSwitchIndicatorView — add
  public static FrameLayout.LayoutParams buildHostLayoutParams(Context) returning
  TOP | END, topMargin dp(8), marginEnd dp(10). Position and entry animation must agree, so
  they belong in one place; TermuxActivity.obtainSessionSwitchIndicator() (~9159) just calls it.
- De-emphasise: text 10.5f → 9.5f, maxWidth 260 → 200dp, gravity CENTER_VERTICAL | END, padding
  10/5dp, chip fill alpha 168 → 150 and stroke 46 → 38, HOLD_MS 1200 → 1000.
- Replace the Y slide with a trailing-edge X slide (ENTER_ALPHA = 0.92f). Both show()
  branches must change — the re-entrant branch currently sets alpha(1f)/translationY(0f), so
  left alone an updated chip snaps to full opacity and keeps a stale X offset. cancel() resets
  translationX too. Drop translationY entirely: one axis of truth.
- New strings msg_no_session_to_split, msg_window_position ("Window %1$d/%2$d"), and a
  <plurals name="msg_pane_count">.
- Migrate the stock-Toast sites onto the chip: TermuxActivity:10783 (no session to split),
  :10851 and its duplicate at TermuxTerminalSessionActivityClient:491 (max terminals — same
  event, so it must not get two presentations), :10887 and :10918 (window position). Keep
  showToast for the wallpaper failures and msg_terminal_reset.
- Make successful pane creation speak: widen TerminalPaneController.split(int) to return boolean
  (3 call sites, all known) and have splitCurrentPane() report the new pane count via the plural.
- noteSessionSwitchIndicated() is untouched, so same-session pane/window churn still produces
  exactly one notice and no spurious "session switched" chip.

Tests — new SessionSwitchIndicatorViewTest (Robolectric): host params pin to top-trailing;
show() then re-show() keeps ENTER_ALPHA and clears the slide (guards the re-entrant bug class);
cancel() resets both transforms. TerminalPaneControllerTest: assert the new split() return,
including false with no active window.

A5. Shell activity ("working") animation

Signal — this is the key correction to the original design. The plan does not depend on a
privileged backend. TerminalSessionClient.onTextChanged(session) already fires on every screen
update (TerminalSession.notifyScreenUpdate(), ~270) and is already handled at
TermuxTerminalSessionActivityClient.java:154. That is precisely tmux's monitor-activity.
Tap it above the existing early return at line 157 (getTerminalViewForSession(...) == null) —
windows other than the active one have no TerminalView, so below that check their activity would
never register.

Busy = output activity within the decay window ∪ ForegroundInfo.idle == false when the
privileged resolver happens to have data. The union covers the one case output activity misses: a
foreground process that runs silently (sleep 300, an idle vim). Honest limitation to note in the
changelog: unprivileged, a silent foreground process shows as not-working.

- New pure app/src/main/java/com/termux/app/statusbar/ShellActivityTracker.java (no Android
  imports): noteActivity(int pid, long nowMs), isActive(int pid, long nowMs),
  pruneBefore(long), DECAY_MS = 1200L, and nextExpiryMs(long nowMs) so the host can schedule
  one decay refresh instead of polling.
- New pure app/src/main/java/com/termux/app/statusbar/ShellActivityPulse.java: CYCLE_MS = 1400L,
  DOT_COUNT = 3, SWEEP_WIDTH_FRACTION = 0.40f, phase(elapsedMs), dotWeight(index, phase),
  sweepStartFraction(phase) — one clock both surfaces read, so they animate as one system.
- TermuxActivity — noteShellActivity(TerminalSession) records into the tracker and posts a
  coalesced window-bar refresh (throttled ~150 ms) plus a single decay-expiry refresh. Reuse the
  existing mWindowLabelHandler.

Surface (a) — status bar. New ShellActivityIndicatorView (14dp, three pulsing dots) as the
first child of terminal_status_widgets, visibility="gone". One ValueAnimator that runs only
when busy AND attached AND window-visible — override onAttachedToWindow,
onDetachedFromWindow, onVisibilityChanged, onWindowVisibilityChanged, so it never burns frames
in the background. Reuse StatusBarWidgetView.ColorRole and re-tint from updateStatusWidgets().
Scope it to the current session's windows and say so in a comment.

Surface (b) — window pill. Draw in TerminalWindowBar's SelectionStrip, not per-pill:
one animator for the whole bar with busy state as a boolean[] on the strip, so
setWindows's mTabs.removeAllViews() has nothing to clean up. Visual: a dp(1.5) sweeping
underline at bounds.bottom - dp(2.5), which lives entirely in the pill's ~3dp bottom clearance and
can never collide with the Nerd Font glyph — a corner dot would sit on top of it at this size.

- WindowItem gains busy plus a withBusy(boolean) copy method, so the existing itemFor /
  itemForResolved / truncateFile factories and their tests stay untouched.
- The early-return trap: setWindows bails at line ~126 when label and selection are unchanged.
  A busy-only flip changes neither, so as written the state would be silently dropped. Add
  sameBusy(...) to the guard while leaving sameItems() comparing labels only — deliberately, so
  canReuseTabs stays true and starting a command does not re-inflate the pill row (which would
  also kill the selection slide). Push busy state and call updateBusyAnimator() in both
  branches.
- No interaction with mSelectionAnimator: both only mutate strip fields and invalidate(). Do
  not fold them together, or a window switch would stall the activity indication.
- Accessibility: new termux_window_tab_busy_content_description; extract
  applyTabContentDescriptions() and call it from both branches (missing the reuse branch leaves
  a stale description). spokenLabel is never modified.

Tests — ShellActivityTrackerTest (pure): activity decays after DECAY_MS; a re-note extends
it; nextExpiryMs returns the soonest; prune drops stale pids. ShellActivityPulseTest (pure):
phase wraps; weights stay in [0,1]; dot weights peak at distinct phases; sweep is continuous across
the wrap; the sweep window never leaves the pill. TerminalWindowBarTest (Robolectric, harness
already proven): a busy-only change reuses the same tab views (assertSame); busy is not swallowed
by the early return (via a @VisibleForTesting isBusyAnimationRunning()); detach stops the
animation; the content description updates on the reuse path too.

---

Group B — Command palette

B4. Rendering artefacts (do first — isolated and independently revertible)

Diagnosis: both symptoms are one bug. Every body/list fill is a square-cornered drawRect
bounded by a rectangular clip (clipToFrame → canvas.clipRect(mFrame), ~477), while the surface
is painted as a rounded rect. Anything flush to mFrame.bottom paints into the corner arcs
drawRoundRect left empty.

- (a) the highlight — drawRow (~623-630) draws the focus wash to top + height, and listBottom()
  returns mFrame.bottom outside argument mode (~564) while the focused row is the tall one (~412).
  Radius reaches 26dp, so the overspill is large.
- (b) the band — candidate 1, the bottom fade (~583-590): a gradient ending at mGlassBase
  alpha 230, drawRect-ed full-width to mFrame.bottom, so its last pixels are a near-opaque
  square-ended band overhanging both arcs. It only appears when the list overflows, matching
  "sometimes there's a band". Same bug in argument mode via drawArgumentRow (~707-710).
  Candidates 3-5 are exonerated: the faked shadow is clipOutPath'd out (~489-491), mView's
  elevation casts nothing (empty outline), and clipChildren="false" only lets the keycap strip
  draw below the frame, which is intended.
- (c) same root — drawSurface strokes the accent rim (~469-474) before drawBody, so the fade
  and last-row wash paint over the rim's bottom segment. That is why the edge reads as a band rather
  than a rimmed edge.

Fix. Add a cached mFramePath + mFramePathDirty (set dirty in setFrame()), and replace
clipToFrame's body with canvas.clipPath(framePath()). One change fixes (a), (b), the argument
row, the hairlines and the specular corner bleed at once, because drawBody and drawSurface both
route through it and drawList's clipRect intersects rather than replaces. Then extract the rim
into drawRim(Canvas) and call it after drawBody — the rim belongs to the surface, so it must
survive the body's fills. Also set mView.setOutlineProvider(null) explicitly in bindViews():
today the empty outline is accidental (default BACKGROUND provider, no background), and a future
setBackground(...) would silently start casting the exact square band applyFrame() already
documents fighting.

minSdk is 26 and Canvas has no clipRoundRect, so clipPath is the only option — and
clipOutPath is already in the frame budget. A uniform-radius addRoundRect is recognised by Skia
as an rrect and clipped analytically, so the cost is negligible; hardware rrect clips are not always
antialiased, so the wash's corner edge may be marginally jaggy — invisible at alpha 28.

Tests — Robolectric 4.13 runs LEGACY graphics, so Canvas/Path are shadow no-ops and no
test can prove the corners are clipped. A new CommandPaletteViewTest smoke test drives
setFrame/setRows/setArgumentMode and calls draw(new Canvas()) in list, overflowing and
argument modes without throwing, guarding the new framePath() against rewind/NPE bugs, plus
asserts the pure chromeHeight()/measuredContentHeight() arithmetic. Keep this in its own commit;
it is eyeball-verified only (on-device check 8).

B5. Rename a specific session from the palette

The two-required-argument problem does not exist. promptableArgument() is only consulted for
tool projections in buildEntries() (~94). Hand-built entries set argumentName directly, and
withArgument() (~897) merges into the entry's existing arguments. So a per-session row
carrying {"index": n} plus argumentName = "name" gets ARGUMENT mode and reaches the dispatcher
with both keys — zero changes to the argument machinery.

ctrl+alt+r needs no adjustment: session.rename_prompt is SPLITS_OFF and
window.rename_prompt is SPLITS_ON — non-overlapping, and LauncherToolRegistryTest
already pins exactly that.

- LauncherToolRegistry: new TOOL_SESSION_RENAME_AT_INDEX = "session.rename_at_index", registered
  after TOOL_SESSION_RENAME with required = ["index", "name"] in that order (so
  map … session.rename_at_index 1 work fills positionally — comment the reason), CATEGORY_SESSION,
  REQUIRES_SESSION, no default binding.
- TerminalActionDispatcher: add the case to both the allow-list switch (~203) and the dispatch
  switch (~663). Route to the existing TermuxActivity.renameBrowserSession(int, String); return
  400 for a missing arg or a bad index; on success echo the stored name, since the cap means
  asked-for ≠ kept. Comment why this is a new tool rather than an optional index on
  session.rename: it renames the WSession (what the palette's session rows display), whereas
  session.rename routes to renameCurrentSessionTo — a different object with different naming
  rules. Also route through renameBrowserSession rather than the drawer index, because
  rebuildDrawerSessions skips window-less sessions (TermuxActivity:10051) and the indices can
  diverge.
- TerminalCommandPalette.buildSessionEntries() — emit a second row per session, built by a pure
  static Entry renameSessionEntry(int index, String title, String subtitle) so it is testable
  without an activity. This doubles the Sessions section (fine at 1-4 sessions) and keeps
  "rename session 2" searchable, which is the palette's whole point.
- New strings palette_session_rename, palette_session_rename_hint,
  tool_session_rename_at_index.

Tests — TerminalActionDispatcherTest.handles_coversEveryRegisteredTerminalTool gains the new
id. LauncherToolRegistryTest: bump assertEquals(57, …) → 58 (line 60, verified) and assert the
required order and absence of default bindings. New TerminalCommandPaletteRowsTest:
promptableArgument is null for the new tool but "name" for session.rename; the rename entry
reports isArgumentPrompt() and carries index; and the withArgument merge produces both
keys — extract withArgument to package-private static to assert it, as it is the single riskiest
untested line here.

B1. Show the configured chord on app rows

CommandPaletteFilter needs no change — shortcutLabel() already checks !bindings.isEmpty()
before the CATEGORY_APPS → "app" fallback (verified at line 178-184). App rows currently pass
Collections.emptyList() (TerminalCommandPalette:231); putting the resolved stroke in that list is
the whole fix, and it makes app rows searchable by chord for free, since score() already matches
entry.bindings.

- TerminalKeyBindingResolver: new
  getArgumentStrokesForTool(toolName, argumentName, ActionContext) returning
  argument-value → first stroke. getStrokesForTool cannot answer this because one tool backs many
  rows and only the argument tells them apart.
- TerminalActionDispatcher.resolveApp → (LauncherAppDataProvider, String query, boolean allowBlocking),
  package-private static, so the palette resolves identically. Otherwise a row could advertise a
  chord that launches a different app.
- New pure CommandPaletteAppShortcuts: index(argumentToStroke, Lookup) → stableId → stroke,
  and bindingArgumentFor(stableId, defaultStableIdForPackage) returning the bare package name when
  the row is its package's default (readable config) and the full stableId otherwise — necessary
  because AppRef.stableId() can contain #userSerial= and TerminalBindingConfig.words() treats

# as a comment start.

- TerminalCommandPalette.buildAppShortcuts(...) resolves against the warm cache only, never a
  PackageManager sweep. buildAppEntries takes the map and a Context, and its subtitle becomes
  palette_app_row_subtitle ("%1$s · hold to bind a key") — which is also B3's discoverability.
  Drop the unused 3-arg overload (~186).
- TerminalCommandPaletteController: build the map once per show() and again in the
  warmAsync callback (~217) — never per keystroke. Rebuild after a successful capture write.

Tests — new CommandPaletteAppShortcutsTest (pure): package/label/stableId arguments resolve;
unresolvable dropped; first stroke wins; bindingArgumentFor for default vs work-profile activities.
TerminalKeyBindingResolverTest (Robolectric, installConfigForTesting pattern at ~184): two
app.launch maps produce the expected argument→stroke map, and a non-holding condition is excluded.
CommandPaletteFilterTest: an apps entry with a non-empty bindings returns "C-A-w", not
"app" — the regression guard on shortcutLabel()'s ordering.

B2. Comment-preserving bindings-file writer

New app/src/main/java/com/termux/app/terminal/TerminalBindingConfigWriter.java.

Prerequisite visibility bumps in TerminalBindingConfig (no contract change, existing tests
unaffected): words(String) and MAX_FILE_BYTES/MAX_LINES/MAX_LINE_CHARS become
package-private. The writer must reuse the real tokenizer — a regex would drift on quotes,
escapes and # — and must refuse to produce a file the parser would reject wholesale (load()
returns Result.empty on a limit breach, so one oversized write kills every binding).

Pure core — this is what CI can actually verify:
Edit {lines, replaced, index, error}, plus putMapping, removeMapping, formatMapLine,
quoteWord, mapLineSequence, isUnmapOf.

Rules:

- mapLineSequence tokenizes, returns null for blank/comment lines (the tokenizer strips #, so
  commented directives are structurally untouchable rather than regex-avoided), null unless
  words[0] is map, skips --opt value pairs as the parser does, and returns null when
  --mode/--new-mode is present (a modal mapping lives in another keymap and must not be
  clobbered). Broken quoting throws from words — catch it and leave the line opaque.
- Replace in place only when a matching root map exists and no unmap of the same sequence
  follows it — the parser processes directives in order, so an in-place edit before a later
  unmap would parse but never fire. Otherwise append at EOF.
- Managed-section header is cosmetic only; matching never depends on it, because users reorder files.
- quoteWord quotes on #, whitespace, quotes, backslash, or a leading --. This is what makes
  #userSerial= stableIds survive.

IO layer (thin, untested): read with load()'s guards, putMapping, write to a temp file with
getFD().sync() and owner-only perms, Files.move(ATOMIC_MOVE, REPLACE_EXISTING) with the
AtomicMoveNotSupportedException fallback — copy TerminalWorkspaceStore.save (~74-97) in shape.
mkdirs() the parent as writeTermuxPropertyToProperties does. Then
TerminalKeyBindingResolver.reloadUserBindings() and log any getConfigErrors(). Main thread,
synchronously: the file is <256 KiB, both precedents write on the main thread, and the palette needs
the reload done before it redraws. Flag as a possible StrictMode complaint.

Tests — new TerminalBindingConfigWriterTest (Robolectric, matching TerminalBindingConfigTest
since normalizeSequenceSpec reaches KeyEvent): appends with a header to an empty file; preserves
comments and blank lines byte-for-byte; replaces the same stroke instead of duplicating; treats
Ctrl+Alt+W / ctrl+alt+w / extra whitespace as one sequence; ignores a commented # map …;
ignores map --mode foo …; appends instead of replacing when a later unmap exists; errors and
leaves lines unchanged at MAX_LINES; and — the highest-value test in the plan — feeds a
written line containing a #userSerial= stableId back through TerminalBindingConfig.parse and
asserts the parsed arguments, proving writer/parser symmetry.

B3. Key-capture overlay (highest risk in this group — do last)

Capture must accept the in-app keyboard, not just hardware. interceptKeyValue(value, ctrl, alt, shift) already
carries the modifier flags, and on a phone with no physical keyboard a hardware-only
overlay would be dead UI.

- New pure CommandPaletteCaptureModel: strokeFor(keyCode, ctrl, alt, shift),
  strokeForChar(c, ctrl, alt, shift), isBindable(stroke). Modifier-only presses need no special
  logic — TerminalKeyBindingResolver.keyToken() already returns null for
  KEYCODE_CTRL_LEFT/ALT/SHIFT, so "wait for a non-modifier" falls out of the existing table.
  isBindable additionally rejects an unmodified key: binding plain w would swallow typing.
- CommandPaletteView: add Callbacks.onRowLongPressed(int); implement long-press in
  onTouchEvent (post-delayed at getLongPressTimeout(), cancelled on UP/CANCEL/drag), extracting
  rowIndexAt(float y) from handleTap's loop. Generalise the argument row with a prompt parameter
  (keep the 3-arg overload defaulting to "arg ❯"). That is the only drawing change B3 needs.
- TerminalCommandPaletteController: add Mode.CAPTURE and mCaptureStroke (do not reuse
  mQuery — different backspace/filter semantics). beginCapture(entry) guards on CATEGORY_APPS.
  rebuildRows() gains a CASE CAPTURE collapsing to one notice row. Route CAPTURE keys through
  handleCaptureKey(...) first, before the existing handleKeyCode ESC→collapse() path — in
  CAPTURE, ESC must popMode() back to the list, and routing first is also what guarantees Esc is
  never captured as the bound key. ⏎ saves, ⌫ clears, Esc cancels; nothing may reach appendText.
  Documented consequence: enter, escape and backspace cannot be bound from the overlay — the
  conf file stays the escape hatch.
- Entry points: long-press an app row, plus Ctrl/Alt+⏎ on the focused app row (Enter alone stays
  "launch"). Discoverability comes from B1's subtitle change — drawRow already renders the
  description on the focused row only, so no new rows and no layout change.
- Conflict: look up the existing binding and show palette_capture_conflict naming the claimant.
  Warn, do not block — "mentioning a sequence replaces the defaults for it" is already the
  documented file semantics, so blocking would contradict the config model.
- Save → TerminalBindingConfigWriter.bindAppLaunch(...) → rebuild mAppShortcuts → confirmation
  via the existing showConfirmation path. Reset mMode/mCaptureStroke in collapse() and
  dismissImmediately().
- New strings: palette_capture_notice, palette_capture_placeholder, palette_capture_conflict,
  palette_capture_needs_modifier, palette_capture_saved, palette_capture_failed,
  palette_meta_awaiting_key.

Tests — CommandPaletteCaptureModelTest (Robolectric): KEYCODE_W+ctrl+alt → "ctrl+alt+w";
canonical modifier order regardless of flag order; modifier-only → null; unmappable → null;
strokeForChar('W', true, true, false) → "ctrl+alt+w"; isBindable rejects "w" and null,
accepts "ctrl+alt+w". The controller stays untested (it needs a live TermuxActivity, as
TerminalActionDispatcherTest's own header explains) — so every raw-key decision must live in
CommandPaletteCaptureModel, and the controller must contain only routing.

---

Group C — Bug fixes and investigations

C3. Floating pane grab pill black border (free win — do first)

Confirmed: FloatingPaneContainer.dispatchDraw (~2446-2454) unconditionally fills pillRect()
(48×18dp) with termuxColorSurfacePanel (#171D26) before drawing the 28×3.6dp primary grip. That
near-black slab is the "border".

- New pure static pillBackdropAlpha(boolean expanded, boolean activeFloat) → 0 collapsed,
  0xF0 expanded+active, 0xD0 expanded+inactive. The collapsed grip is the whole affordance and
  needs no slab; expanded, the close/dock glyphs need a surface to read against.
- Resolve the backdrop from termuxColorSurfacePanelHigh instead — one step lighter than the float's
  own slab, so the strip reads as chrome, not a hole — and add a 1dp termuxColorOutlineVariant
  hairline at alpha 0x66 when it is drawn.
- Raise the collapsed grip's inactive alpha floor 90 → ~120 so it still reads on a busy terminal;
  leave chromeAlpha alone so the resize chevrons don't shift.
- pillRect() and pillActionAt() untouched — hit targets unchanged.

Test — pillBackdrop_isDrawnOnlyForTheExpandedActionStrip(): zero for both collapsed cases,

▎ 0 for both expanded, active ≥ inactive.

C1. Scratchpad geometry ratchet

Root cause confirmed as briefed, plus a second independent defect. applyFloatBounds's write-back
at line 1266 is the only place a clamp result becomes persistent state (drags clamp and assign
independently at ~2322), so it exists purely to "normalise after a host resize" — which is the
ratchet. Separately, clampFloatFractions constrains top <= 1f - minVisibleY but never
top + height <= 1f, so bottom may exceed 1.0 — and applyTerminalBorderAppearance sets
paneHost.setClipToOutline(false) the moment a second pane appears (which is what showing the
scratchpad does), so the overflow paints into the dock band.

Both fixes are needed. Removing the write-back fixes "the shape is permanently wrong after the
keyboard closes". The bottom <= 1f constraint fixes "it paints under the dock", which still happens
without it: while the IME is up, minHeightPx/hostHeight inflates the applied height and the
1f - minVisibleY ceiling lets the applied rect run past the host bottom. It also stops the user
dragging a float under the dock by hand.

Rejected third option — suppressing the reclamp while the IME is visible: it needs a new Host hook
into TermuxActivity.isImeVisibleForLayout(), is not unit-testable, and leaves the float mis-sized
while the keyboard is up, which is exactly when the scratchpad is used.

- clampFloatFractions: replace the 1f - minVisibleY vertical ceiling with 1f - height; delete
  the now-unused minVisibleY (keep minVisibleX — horizontal overhang stays intentional, and the
  handle rule still holds since the handle spans the float's full width). Comment why the vertical
  rule differs from the horizontal one.
- applyFloatBounds: delete leaf.floatFrac = frac; and set a new
  @Nullable transient RectF appliedFloatFrac instead, with a comment that the clamp is a
  projection onto the current host, not new user intent. Not added to
  saveWindow/restoreWindow/snapshotWorkspaceWindow.
- FloatingPaneContainer.startDrag: seed mDownFrac from appliedFloatFrac ?: floatFrac so a drag
  starting while clamped does not teleport. The MOVE branch keeps writing floatFrac — a deliberate
  gesture is new intent.

Tests — TerminalPaneControllerTest:
clampFloatFractions_neverLetsAFloatHangBelowTheHost() (including a host shorter than
FLOAT_MIN_HEIGHT_DP); clampFloatFractions_isIdempotent() (guards against any future ratchet);
and the regression test scratchpadBounds_survivesAKeyboardShrinkAndRegrow() — refactor the existing
newScratchpadController() (~660) to accept a caller-owned FrameLayout, then
layout(1080×2000) → set the frac → layout(1080×700) (Robolectric dispatches the real
OnLayoutChangeListener, the hook at 219-224) → layout(1080×2000) and assert the frac is unchanged
to 0.001 and bottom <= 1f; then hide/re-show and assert the shape persists. The existing
scratchpad_remembersUserShapedBounds… test (~529) must stay green.

C2. Scratchpad → background terminal flicker

Two independent changes. C2b alone stops the PTY reflow — the visible jump — so do it first.

C2b — floats must not flip the frame-line owner. Confirmed chain: onPanesRendered (11043) →
applyTerminalBorderAppearance (1034) → singlePane = visiblePaneCount() <= 1 → paneInsetPx
flips → paneHost.setLayoutParams(...) → TerminalView.onSizeChanged → updateSize →
mTermSession.updateSize (TIOCSWINSZ + emulator reflow) + mTopRow = 0 + clearScrollOffset().

- New TerminalPaneController.tiledPaneCount() — leaves of the tree, floats excluded, maximized
  counts as one, max(1, …) to cover the float-only window (root == null), which the existing
  finishedLastTiledShell_promotesAFloatIntoTheTree test can produce.
- TermuxActivity.visiblePaneCount() delegates to it; update the javadoc to say floats are
  deliberately excluded and why.
- Leave getVisiblePaneViews() alone. It fans out font/size/keyboard changes where floats must
  be included, and four existing assertions (TerminalPaneControllerTest:212,215,409,501) pin its
  float-inclusive counts. A separate method keeps them all green.
- updateActiveBorders(): a float always gets its own focus-keyed border; a lone tiled pane keeps
  the terminal border as its frame. So showing the scratchpad stops painting/unpainting a border on
  the background pane.

C2a — incremental float add/remove. Extract render()'s float-container construction
(~1428-1436) into attachFloatContainer(leaf) and call it from render() so there is one path.
Add addFloatOnly(leaf) / removeFloatOnly(leaf), each guarded (mActiveWindow != null,
root != null, not maximized, host has children) and each falling back to render(). They skip
mHost.onPanesRendered(), which is safe because that call does only
applyTerminalBorderAppearance() — and C2b makes it inert for float add/remove anyway. Wire
toggleScratchpad (capture wasMaximized before mMaximizedLeaf = null) and hideScratchpad's
remove runnable (which already calls removeFloatContainer — restructure so it isn't done twice).
Leave dockFloat, toggleFloatActivePane and maximize on render() — those genuinely
restructure the tiled tree and their inset flip is correct.

Out of scope, and worth stating: the RealtimeBlurView decor pre-draw still redraws the window each
frame of the 220/160 ms animation. That is a whole-window cost, not a PTY reflow, and will not read
as a jump once the reflow is gone.

Tests — tiledPaneCount_ignoresFloatsAndTheScratchpad();
scratchpadShow_keepsTheTiledPaneViewsAttached() (identity-compare the tiled view and its parent
across a toggle, assert host child count ±1); and extend the existing float-docking test to assert
the render() fallback still rebuilds the tree.

C6. CPU widget

One correction to the root-cause ranking, and it matters. The definitive cause of "the process
list disappears" is parsePrivileged (~203) calling parseProcessRows unconditionally when
wantTop, and parseProcessRows ending with mLatest.top = new ArrayList<>(selected.values()).
Backends return "Error: …" strings, which parse to zero sections — so one failed read wipes
top, and SystemStatsCardView.bind (~166-174) then sets the process header and scroller to GONE.
That single missing guard is the reported symptom.

The ShellBackend drain-order bug is real but latent: it bites only when combined stdout exceeds
the pipe buffer (~64 KiB), and this command is typically 25-35 KiB — so it hangs on process-heavy
devices and not others. Fix it, but don't expect it to be the whole story.

1.  Never wipe good data. Early-return from parseProcessRows when both input sections are
    empty; have parsePrivileged return boolean and log a warning with the first ~120 chars of
    output when it produced nothing.
2.  Watchdog + stale-completion guard. New pure
    shouldStartSample(inFlight, nowMs, startedAtMs, timeoutMs); timeout max(6000, interval*3).
    Add mSampleGeneration so an abandoned future cannot corrupt the CPU delta later (written and
    read on the main thread only). Set mLatest.stale on error/timeout.
3.  Drain concurrently. New app/src/main/java/com/termux/privileged/ProcessOutputPump.java, no
    Android imports so it is plain-JUnit testable: drain(InputStream) (keeping the existing
    readLine-joined-with-\n semantics exactly, because parsePrivileged splits on \n),
    start(name, in), await(timeoutMs). Rewrite ShellBackend.runProcess to start both pumps
    before waitFor, destroyForcibly() on timeout, then await. Delete the old readStream.
4.  Off the common pool. New PrivilegedExecutors — a 2-thread daemon pool — passed to every
    supplyAsync in ShellBackend and ShizukuBackend. Two threads, not the manager's own
    single-threaded executorService, which cleanup() shuts down and which would serialise a hung
    stats command behind everything else.
5.  Reliable restart. Confirmed: start(...) is reachable only from updateStatusWidgets() ←
    only refreshTerminalWindowBar()'s tail, which onStart/onResume never call, while onStop
    stops it. Add updateStatusWidgets() to onStart(). Also relax start()'s
    if (mRunning) return; to re-post mTick, so retuning is immediate — today
    toggleStatsCard's start(1500, true) waits out the old 4 s delay.
    (Note: the !isSplitPanesEnabled() early return is not the live path here — the CPU pill lives
    inside terminal_window_bar_host, which is GONE in that mode. Still call
    updateStatusWidgets() before the early return for idempotence.)
6.  Surface failure. Add stale to Stats; when set, append " · stale" to the CPU header and
    drop its alpha to 0.7 — never hide anything. Replace the catch (Exception ignored) at
    readActivityManagerMemory and readFileLines with a one-line warn.
7.  Keep top. ps -A -o PID,NAME,RSS carries no CPU column, and parseProcessRows needs
    top's %CPU for the EMA while ps enables the memory-sorted half of the union. After fix 3 the
    payload size is no longer a correctness issue.

Tests — extract the top-list merge into a static mergeProcessRows(previous, ps, top) so both
new cases stay Android-free: shouldStartSample_overridesAWedgedInFlightRequest() and
mergeProcessRows_keepsThePreviousListWhenTheBackendReturnedNothing(). New
app/src/test/java/com/termux/privileged/ProcessOutputPumpTest.java (plain JUnit): 256 KiB of
numbered lines through a PipedInputStream round-trips intact — the regression test for the
deadlock class; CRLF and a missing trailing newline behave as before; await on a never-closed
stream returns within the timeout instead of hanging. A full ShellBackendTest is not worth it (real
ProcessBuilder + android.util.Log); the pump test covers the actual bug.

C5. Notification swipe reply with multiple notifications

Verified: getNotificationsForPackage sorts newest-first
(LauncherNotificationBadgeStore.java:69), showNotificationPopup iterates in that order appending
only non-null targets, and each card keeps only its first free-form action. So
replyTargets.get(0) is already exactly the newest reply-capable notification — the core change is
one line.

1.  shouldAutoOpenNotificationReply(int) → return replyTargetCount >= 1;, and rewrite the comment
    to state the new rule: notifications arrive newest-first, so target 0 is the latest conversation,
    which is what the swipe means.
2.  Make the auto-opened card obvious (it is now one of several): give NotificationReplyTarget a
    card field (already in scope at construction), apply a rounded translucent panel fill with a
    colorPrimary stroke plus padding so the stroke does not clip text, and
    requestRectangleOnScreen(...) to scroll it into view — the enclosing ScrollView is created
    inside buildPopupWindow, so that is the only handle that does not need to know about it.
3.  Fix the mid-compose auto-dismiss (~614-630) — this is the difference between a usable and an
    unusable reply flow. Track the live composer in a @Nullable EditText notificationReplyEditor
    (set at the end of showInlineReply, cleared in dismissNotificationPopup and the dismiss
    callback). New pure
    shouldDismissNotificationPopupOnKeyChange(boolean keysChanged, boolean composing) →
    keysChanged && !composing, where composing means focused or non-empty. When it says don't
    dismiss, still re-snapshot notificationPopupKeys, or the next change fires immediately.
4.  Leave enableNotificationReplyInput / requestNotificationReplyIme alone — the IME machinery
    already works; the only reason it never ran was the count guard. Do fix the stale comment at
    ~5146 ("Keep this window non-focusable for its first layout"), which contradicts
    setFocusable(true) twelve lines above and will mislead the next reader of exactly this code.

Tests — app/src/test/java/com/termux/app/SuggestionBarNotificationPopupTest.java (exists):
retarget the auto-reply test to assert false for 0 and true for 1, 2 and 5; new case for the four
combinations of shouldDismissNotificationPopupOnKeyChange.

C4. Kill the process group on pane close (highest risk — do last)

Scope narrowed: no JNI change. android.system.Os.kill is a direct kill(2) wrapper and a
negative pid is plain POSIX that bionic passes through. Adding killpg to termux.c would put the
entire pty startup path into a build that cannot be run locally, for a case that almost certainly
does not exist. The recipe stays on the shelf as a contingency (below).

Also: do not reorder the pty-master close. The group SIGHUP already reaches strictly more
processes than the kernel's pty hangup would, and moving JNI.close(mTerminalFileDescriptor) ahead
of the reap races the reader/writer threads and JNI.waitFor. Zero upside, real risk.

Verified: the native child calls setsid() (termux.c:103) before opening the slave pty, so
pgid == mShellPid and descendants inherit it. All six kill call sites funnel through
finishIfRunning(), and every one of them wants group semantics — including
TermuxSession.killIfExecuting, which serves background RunCommand shells that are setsid'd the same
way. So changing finishIfRunning fixes all of them and no call site changes.

- New terminal-emulator/src/main/java/com/termux/terminal/ShellTerminator.java, no Android
  imports so it runs under the module's existing plain-JUnit suite:
  SignalSender {boolean send(int pid, int signal);}, Scheduler {void postDelayed(Runnable, long);},
  ESCALATION_DELAY_MS = 150L, and
  terminate(shellPid, sighup, sigkill, sender, scheduler, livePid).
  Behaviour: bail on shellPid <= 0; send(-shellPid, SIGHUP); if that fails, immediately
  send(shellPid, SIGHUP) and remember the group form is unavailable; schedule the escalation and
  send SIGKILL only if livePid still equals shellPid.
  Guarding on "the leader is still alive" is what makes this pid-reuse-safe — while the leader lives,
  pgid shellPid is unambiguously this group — and it implements the chosen contract: if the shell
  already exited on SIGHUP, skip the kill.
- TerminalSession.finishIfRunning() delegates to it with mMainThreadHandler::postDelayed (a main
  looper Handler, so this is safe from MSG_PROCESS_EXITED, the UI, and TermuxService) and
  () -> mShellPid (set to -1 by cleanupResources on the main thread, so the read is coherent with
  the escalation task). Add a sendSignal helper that logs and returns false on ErrnoException.
  cleanupResources and the reap/close order are untouched.

Contingency, only if Os.kill(-pid, …) is rejected on device: add
Java_com_termux_terminal_JNI_killProcessGroup (killpg) to termux.c — signal.h/errno.h are
already included and the module builds with -Werror, so both JNI params need TERMUX_UNUSED.
Android.mk needs no edit: it compiles termux.c wholesale, so there is no per-symbol build
surface. Declare the native in JNI.java and swap it in behind SignalSender.

Tests — new terminal-emulator/src/test/java/com/termux/terminal/ShellTerminatorTest.java (plain
JUnit, fakes for both interfaces): hangs up the whole group first; escalates to a group kill when the
shell ignores the hangup (asserting the delay); skips the kill once reaped; falls back to the single
pid when the group signal fails; ignores a non-running shell.

---

Commit sequence

┌─────┬────────────────────────────────────────────────────┬───────┬──────────────────────────────┐
│ # │ Commit │ Group │ Risk │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 1 │ Scratchpad naming + session name cap 5→8 │ A1 │ low │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 2 │ Sessions panel rename button │ A2 │ low │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 3 │ Sessions panel metrics + marquee gate │ A6 │ low — must land with/after 2 │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 4 │ Status row settings cog │ A3 │ low, untested │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 5 │ Grab pill backdrop │ C3 │ very low │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 6 │ Command palette rounded clip + rim order │ B4 │ low, eyeball-only │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 7 │ Rename session by index from the palette │ B5 │ low │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 8 │ App-row chords in the palette │ B1 │ medium │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 9 │ Bindings-file writer │ B2 │ medium │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 10 │ Palette key-capture mode │ B3 │ high │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 11 │ Transient notice chip: top-right + unified │ A4 │ medium │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 12 │ Shell activity tracker + indicators │ A5 │ medium │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 13 │ Scratchpad geometry ratchet │ C1 │ low │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 14 │ Floats no longer reflow the background shell │ C2 │ medium-high │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 15 │ CPU widget: pump, watchdog, restart, staleness │ C6 │ medium │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 16 │ Notification reply: newest target + draft survival │ C5 │ medium │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 17 │ Process-group teardown on pane close │ C4 │ high │
├─────┼────────────────────────────────────────────────────┼───────┼──────────────────────────────┤
│ 18 │ Docs + changelog │ — │ — │
└─────┴────────────────────────────────────────────────────┴───────┴──────────────────────────────┘

Docs to update in commit 18: docs/en/Terminal_Modernization.md (palette session/apps sections),
app/src/main/assets/launcher-examples/termux-launcher-bindings.conf (a commented
session.rename_at_index example and a note that the palette writes under a managed header — note
LauncherExampleConfigsTest.everyCommentedBindingDirectiveParses uncomments and parses every

# map … line, so a bad example is a red build), and CHANGELOG.md.

On-device checks CI cannot do (in rough priority order):

1.  Cold start reaches a shell prompt; echo $$ works; rotate and resize reflow correctly.
2.  sleep 300 &, close the pane → from another pane, ps -A | grep sleep shows nothing.
3.  nohup sleep 300 & and setsid sleep 300 & → both survive a pane close (the chosen contract).
4.  Foreground vim, close the pane → no zombie; the pane disappears cleanly.
5.  Kill-session dialog and the service's killTermuxSession still free the MAX_SESSIONS slot.
6.  Scratchpad hide must not kill anything — the shell survives and re-adopts on the next toggle.
7.  Open the scratchpad, raise then dismiss the keyboard → its size is unchanged and it never crosses
    the dock. Repeat three times (this is the ratchet).
8.  Show/hide the scratchpad → the background terminal does not jump, reflow, or lose scroll position.
9.  Command palette: scroll to the last row → the highlight stays inside the rounded corners, and
    there is no band at the bottom edge. Check with the list both overflowing and not.
10. Long-press an app row → capture overlay; press Ctrl+Alt+W → save; reopen the palette and confirm
    the row shows C-A-W; check ~/.termux/termux-launcher-bindings.conf kept its comments; press
    the chord and confirm the app launches.
11. Run a build in one pane, switch to another window → the pill underline and status dots animate,
    and stop within ~1.2 s of the output stopping.
12. Two chat notifications for one app → swipe its pinned icon → the newest reply field is focused
    with the keyboard up; type, let another notification arrive, confirm the draft survives.
13. Open the CPU card, leave it up for ten minutes → the process list stays populated and CPU keeps
    moving; if a privileged read fails, the header reads "stale" rather than blanking.
14. Status row cog opens Terminal & status settings.
