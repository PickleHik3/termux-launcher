# Handoff — layout freedom follow-ups, evening 2026-09-16

Repo `~/Projects/termux-launcher/app/termux-launcher`, branch `dev` at `4fccedcf` (+ this file),
**not pushed**. Integration worktree `../termux-launcher-integ` mirrors dev (fast-forward only) and
is where suites and APKs are built (`JAVA_HOME=~/.local/opt/jdk21`, every gradlew under
`flock /tmp/claude-1000/gradle.lock`). The main checkout carries an older session's uncommitted
help/keyboard WIP (`HelpTargets`, `HelpTopics`, `TermuxInAppKeyboard`, `Keyboard2View`, `strings.xml`,
`TermuxActivity`, two tests) — never commit it; stash it around merges (`git stash push -- app
inapp-keyboard`, merge, `git stash pop`), which has worked cleanly every time today.

Device: pong (`adb -s 100.101.173.85:5555`, Nothing A065, 1080×2412, portrait, dark). Install with
push + `pm install -r /data/local/tmp/tl.apk`; the arm64 debug APK is
`termux-launcher-integ/app/build/outputs/apk/debug/termux-app_apt-android-7-debug_arm64-v8a.apk`.
adb over TCP drops when the phone leaves Wi-Fi (`adb connect` again). `input swipe/motionevent/
draganddrop` do NOT reliably lift bands in the Layout editor or open the drawer — the developer
tests gestures by finger; use adb for screenshots and `uiautomator dump` bounds only.

Full history of the day, decisions and verification: `project-docs/handoff-2026-09-16-theme-batch.md`
and `project-docs/layout-freedom/SPEC.md` (outcome sections L1–L4, P1–P10). Memory file:
`theme-keys-layout-project.md`.

## State on the phone (15:37 build, developer's verdict "everything looks good so far")

Working: every bar on every edge, ordered stacks, side rail with paging + one tick strip, rail
drawer swipe, off-dock plank with the dock radius, A–Z column, coloured extra keys, TOP rows,
bottom status bar in the dock stack, corner hold on Home, status cards toward the centre,
quick reply per edge.

## Open queue (reported 15:50, screenshot `uiautomator` bounds: status bar 1228–1307 on the
plank, extra keys 1312–1410, apps row 1410–1594 with the tick strip 1570–1594, A–Z 1594–1670)

1. **Bottom status bar cannot be collapsed** once expanded (swipe down does nothing). The fold
   gesture (`StatusBarGesturePolicy`, "across the bar") is still oriented for a TOP bar; P10 put the
   row as the lower band and grows the clock upward — the collapse direction must follow
   `StatusBarLensPolicy.growthFor(edge)` (collapse = swipe against the growth). Also verify the
   P9 note that folding a bottom bar now resizes the dock container, not the terminal.
2. **Side status bar never expands** — deliberate (`expansionAllowed` false for LEFT/RIGHT; P10
   outcome). Developer accepts it but wants a hint so users are not confused: one sentence of
   product copy where the user puts the bar on a side (Layout editor notice, like the
   narrow-canvas one) — e.g. "A status bar on the side stays compact." No mechanism talk.
3. **A–Z row does nothing when not adjacent-below the apps row.** Two orders reported: extra keys →
   A–Z → apps row (letters above the icons) and extra keys → apps row → A–Z with the status bar
   on top. The scrub preview "fills the apps row" (`AccessoryStackLayoutPolicy.rowOverAz/
   rowUnderAz`, `SuggestionBarView` az-preview mode, `AzScrubGesture` targets); the code still
   assumes the letters sit directly under the row. Design: the preview targets the apps row
   wherever it is in the stack (any distance, either side), or, when the row is hidden, the
   floating strip from P7. One policy, tested for every permutation of
   `EdgeStackPolicy.stack(BOTTOM)`.
4. **Recently-used page missing on a side rail.** P1 set `hasMostUsedDynamicPage = false` when
   vertical (`SuggestionBarView`) — a shortcut, not a rule. The rail pages since P6, so the dynamic
   page belongs in `DockPagingModel.railItemsPerPage` paging like any other page (amber tick too).
5. **Order extra keys → apps row → A–Z (all bottom), status bar on the plank above:** the second
   icon from the left draws offset upward ("ghost touch" look, visible in the 15:50 screenshot:
   Outlook sits high), a page swipe plays the animation and lands back on page 1, the tick strip
   sits between the row and the letters. Likely one cause: the row's measured height/air in this
   arrangement (`isAppsRowAlone` false, P8 paddings) vs the settle path of `finishSwipeSettle`
   (row-mute fix) — reproduce in `SuggestionBarPageCommitTest` with the stack [EXTRA_KEYS, APPS,
   AZ] + a bottom status bar and the real `activity_termux.xml`.
6. **Tick strip placement rule** — developer's instinct: above the apps row on the bottom. Proposal
   for their decision: the strip always sits on the row's centre-facing side (BOTTOM above, TOP
   below, LEFT right, RIGHT left), consistent with the drawer/lens/preview "toward the centre"
   family; today `PageTickStrip.leadsRow` says "under the row on TOP/BOTTOM". Ask before changing.
7. **Separator between the apps row and the extra keys sits too close to the extra keys**
   (`EdgeStackView` draws it at the child boundary; the apps row carries its own air so the line is
   visually off-centre). Rule: centre the hairline in the visible gap between the two bands'
   content, or give adjacent bands symmetric air.
8. Judgement call still open from P10: on TOP the bar's row is the lower band with the clock above;
   pinning the row to the screen edge on TOP would be a visible change to the shipped bar — not
   done, developer has not said.

## Parked

`project-docs/display-fullscreen/PARKED.md` — Display place full-screen mode with edge slide-outs;
groundwork rule: keep every bar inside its edge's `EdgeStackView`.

## How this developer works

Short answers; reports one screenshot's worth of symptoms at a time; wants root-cause designs
("a proper fix rather than items being tacked on"); tests by finger and says "everything looks
good so far" when it does. Print lavish URLs in chat every time a page is mentioned. Agents work
in `../wt-<name>` worktrees cut from dev, commit there, never push; the orchestrator merges into
dev with the stash dance, runs the full suite in the integ worktree, builds, installs on pong.
