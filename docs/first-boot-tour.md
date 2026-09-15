# First boot tour — spec (approved 2026-09-13)

Replaces the footage onboarding (`app/onboarding/FirstLaunchOnboarding`, `OnboardingClipView`,
the three bundled clips) with a passive overlay run on the real home screen. Each card glows one
control, shows a finger tracing the gesture, and clears when the launcher observes the gesture.
No Next button. Skip on every card. Replay from Settings. Progress is a pref per step; the run
resumes on the same card after process death or after leaving the launcher.

## First-launch order

1. Bootstrap.
2. Wallpaper access dialog (copy below).
3. Linux display dialog (new).
4. Weather location dialog, only if the weather widget is on.
5. Overlay run.

Existing users on upgrade: nothing. The versioned first-launch pref stays the gate;
`EXTRA_SHOW_ONBOARDING` drives Replay.

## Dialog copy

Wallpaper: title "Allow wallpaper access". Body "The status bar, dock and keyboard blur your
wallpaper. Allow storage access to turn the blur on." Buttons Allow / Not now.

Display: title "Turn on the Linux display". Body "Run graphical Linux apps on the Display page.
You can change this later in Settings." Buttons Turn on / Not now.

## The run

Version 2 (below, "Fifth pass") supersedes this thirteen-card run; kept here as history.

| # | Card | Then | Cleared by |
|---|------|------|------------|
| 1 | Swipe the status bar left or right. | Swipe back. | place changed; place returned |
| 2 | Drag the status bar down, then up. | | status bar expanded; collapsed |
| 3 | Tap + to open a window. | Tap the window chip. → Tap × to close it. | window count +1; chip tapped; window closed |
| 4 | Press Ctrl, Alt, then Enter to split the pane. | | pane.split action |
| 5 | Press Ctrl, Alt, then C to open a window. | | window count +1 |
| 6 | Press Ctrl, Alt, Shift, then C to open a session. | | session count +1 |
| 7 | Swipe the space bar toward the bottom-left to return to your first session. | | the current session is the chapter's again |
| 8 | Swipe the space bar toward the top-left to return to your first window. | | the active window is the chapter's again |
| 9 | Tap a pane corner. | Tap anywhere else to close it. | pane corner menu opened; pane controls dismissed |
| 10 | Pull down on the dock. | Swipe down to close the drawer. | drawer opened; drawer closed |
| 11 | Slide along the A–Z row, drag up to an app and let go. | | app launched from scrub |
| 12 | Swipe up on the space bar to open the command palette. | Tap outside the palette to close it. | palette opened; palette closed |
| 13 | Closing card, no gesture. Three sections, Copy all, Read the docs, Done. | | Done |

Glow per stage: card 3 walks the + → the chip → the × the chip reveals; cards 9, 10 and 12 point
at nothing for their second half, because the thing they are asking about is "anywhere else", "the
plane covering the dock" and "outside the palette".

Copy per stage, not one line and a second one: card 3 says something different on each of its three
taps, because each of them is a different control. A card that names one sentence keeps it for
every stage, which is what "drag the status bar down, then up" is.

## The keyboard chapter (cards 4–8)

Five cards taught on the in-app keyboard, in one session holding one window, and ending on that
same window. The three chord cards are the launcher's own defaults — `pane.split` is
Ctrl+Alt+Enter, `window.new` is Ctrl+Alt+C and `session.new` is Ctrl+Alt+Shift+C, all with splits
on — and the two after them are the space bar's south-west and north-west swipes
(`tool:session.previous`, `tool:window.previous` in the shipped layout).

The keyboard latches a modifier on a plain tap, so a chord is three or four separate presses and
the card asks for them one at a time: the glow rests on the first key of the chord the keyboard
has not latched yet and lands on the key the chord ends on once every modifier is down
(`TourChordGlow`, driven by the keyboard's own `mods_changed`). A layout without one of those keys
— no Shift row, no Enter — answers no rect for it, and the card simply shows without a glow.

Cards 7 and 8 are cleared by arriving, never by swiping: the corner swipes walk a ring, so a user
two sessions along has swiped without getting back. "Your first session" and "your first window"
are the ones card 4 found the user in, recorded by identity when that card is shown, because the
cards before it open and close a window of their own.

Closing card body, three sections, each a heading, one sentence and — where there is something to
run — the command on a monospace line with its own Copy button. Under them: Copy all, a Read the
docs link to `https://picklehik3.github.io/termux-launcher-site/#wiki`, and Done. The card scrolls
rather than growing off a short screen at 1.3× text with the keyboard up.

| Heading | Copy | Command |
|---------|------|---------|
| Key hints | Ctrl+Alt and Ctrl+Alt+Shift are your prefix keys. Hold either to see what every key does. | — |
| Launcher extras | Install the launcher's own extras. | `tlstore install fastfetch sigye claude-code` |
| Graphical apps | Add the X11 repository, then install graphical apps to launch them from the app drawer. | `pkg install x11-repo` |

Edition aware in one row only: the Nix edition's graphical section says the apps come straight from
nixpkgs with no repository to add, and carries no command. VAJ keeps `pkg`, like Termux.

## Rules

- Overlay never consumes a touch. Glow and finger only, drawn over the real control.
- Cards use the app's own surfaces (notice chip / keybind hint card treatment, theme tokens).
  No stock Android dialogs or toasts in the run.
- Targets are measured on every layout pass: keyboard up and down, both dock styles, landscape,
  1.3× font scale.
- Copy: one sentence, product voice, no mechanism.
- Step 7 leaves the launcher; the run must not draw over the launched app and resumes on return.
- No continuous animation except the finger trace while a card is live.
- The card is anchored to its target: centred on it, below it when it is in the top half of the
  overlay and above it otherwise, with a pointer on the edge facing it. A card with no target
  keeps the middle of the overlay.
- The overlay hides while the drawer, the command palette, a terminal sheet, or the Appearance or
  Layout editor is up. The exception is the card whose ask is to close that very surface — the drawer's second
  half and the palette's: it shows compact at the top of the screen, under the launcher's own top
  bar, with no glow. A card that falls due behind chrome is shown when the chrome goes.
  `TourCardVisibility` is the whole rule, and is pure.
- A target that is not measurable this pass is not the same thing as a card that points at
  nothing. A stage that names a control keeps asking for it for 1.5 s after it arrives, and stands
  where the stage before it stood until the control can be measured; only a stage that names
  `TourTargets.NONE` takes the middle of the overlay. The glow is never moved onto the stale
  control — it appears when the real one does. `TourCardPlacement.anchorRect` is the rule.
- "The top of the screen" is the launcher's own bar, not the top of the window. The overlay fills
  the window, and above the launcher's bar sit the system status bar and the camera cutout, so a
  compact card resting on the window's own margin draws behind both — which is what the phone
  showed. It rests below `terminal_window_bar_host` whenever that is up, and below the window's
  top system inset plus the card margin when it is not.
- The run is taught on the terminal place, and knows it. Home is pinned to the wall's own home
  page (`FirstBootTour.HOME_PLACE`), not read from wherever the wall rested when the run was
  built; a run that begins — first launch, Replay, a resume after a process death — brings the
  wall back there first (`FirstBootTour.WallHost`), except when the card it resumes on is the one
  asking the user to swipe back themselves. Mid-run the run never moves the wall: a card whose
  control lives on the terminal (`TourStep.taughtOnTheTerminal` — anything but the status bar and
  "nothing"), shown while the wall rests on the display or the widgets, is presented `AWAY`:
  compact at the top, glowing nothing, saying "Swipe the status bar back to the terminal to
  continue." The two status-bar cards and the closing card read the same on any place. Chrome
  still wins over being away. A place that re-settles where it already was — a rotation — is not a
  swipe.
- The A–Z card stays at the top of the screen for the whole scrub. It used to go off the screen
  while the finger was down; resting at the top already keeps it clear of the icons and of the
  scrub's own previews, and a card that vanishes the moment the user obeys it reads as a bug.

## Corrections found on the first device pass (2026-09-14)

Card numbers below are the nine-card run these passes were made against; the keyboard chapter
renumbered everything from card 4 on.

- Card 4 asked for a swipe up on the split key. A swipe up on an extra key commits that key's
  *secondary*, which for the split key is "new window"; the split is the plain tap. Copy and
  gesture now say tap. (That card is gone: the keyboard chapter teaches the split as its chord.)
- Cards 3 and 6 glowed one control for every half of the step. Targets are per stage now: the
  window card moves from the + to the chip the + made, and the drawer card stops pointing at the
  dock once the drawer covers it.
- A chip tap is reported from the status bar's tap listener alone, so it is its own edge — the
  relay no longer swallows the first one, nor a re-tap on the current chip, which is how the × is
  revealed.
- The window count is read only while the row is standing for the terminal's own windows. The
  Display place fills the same row with its apps and the Widgets place empties it, so a place
  swipe used to read as a window opening or closing.
- A drawer the launcher put away itself (HOME, a rotation, a preference reload) is no longer the
  user's swipe down.
- Debug builds log every card, every signal and every unmeasurable target under `TermuxTour`.

## Second device pass (2026-09-14)

- The × the window card asks for is revealed by a tap the selection listener never hears: a tap on
  the already-selected chip is spent on the reveal. The bar reports every chip tap of its own now
  (`OnChipTappedListener`), and stage 2 of the card glows the × itself (`TourTargets.WINDOW_CLOSE`,
  `TerminalWindowBar.closeButtonView()`).
- The pane corner card jumped to the drawer card with the corner menu still up. It has a second
  half now, cleared by `TerminalPaneController.Host.onPaneControlsDismissed()` — the pane view's
  one way out of those controls.
- The drawer card never heard its open. The relay swallows the first call as the one that tells it
  where the plane rests, and nothing had ever primed it, so the user's own first pull was eaten;
  the launcher primes it with the resting state when it builds the run, like the status bar and
  the place.
- Cards drew over the open drawer and over the open palette. See the chrome rule under Rules.
- The A–Z card covered the icons and the scrub popups it was talking about. It rests at the top of
  the screen now, and goes off it entirely while the finger is down.
- The closing card was three paragraphs with commands buried in the prose, which is not something
  anyone can act on from a phone. It is three sections with their own Copy buttons now.

Still only reasoned, not seen on a device: every one of the above.

## Third device pass (2026-09-14)

- **The window card's × stage parked the card in the middle of the screen and never glowed.** Two
  faults, one symptom. The × opens as a 180 ms *width* animation on a zero-width child of the chip
  strip (`TerminalWindowBar.startCloseReveal` → `SelectionStrip.placeCloseSegment`), laid out by
  hand and deliberately walking no layout pass — "only the segment's own pixels move". The
  overlay's only "something moved" hook is `OnGlobalLayoutListener`, so the one pass it did hear
  measured the × at zero width and nothing ever asked again. A stage that names a control now keeps
  asking for it every 32 ms for 1.5 s (`TourOverlayView.armTargetRetry`), which covers the reveal
  with room to spare and stops on its own. And a stage whose control cannot be measured keeps the
  position the stage before it had (`TourCardPlacement.anchorRect`) instead of falling back to the
  middle of the overlay — the glow still waits for the real control.
  `isShown()` was never the problem: the × is VISIBLE with visible ancestors and is not faded in,
  so only its width was ever zero.
- **The × stage now has its own sentence.** "Tap the window chip, then × to close it." was shown
  for both of the card's last two taps, so it asked for a gesture the user had already made. Copy
  is per stage now (`TourStep.copyResAt`): "Tap the window chip." then "Tap × to close it."
- **The A–Z card no longer disappears while the user scrubs.** See Rules.
- **The palette card says what the gesture is for**, and has a second half asking for the way out
  of the palette it opened, so the closing card no longer arrives behind it. The close is heard
  from `TermuxActivity.setCommandPaletteInterceptorActive` — the one call every open and close path
  makes — as `palette.closed`, edge-triggered against an open the run actually saw so that a pause
  or a configuration change is not read as the user's tap.
- The copy is "Tap outside the palette to close it.", not "swipe up on Esc": the shipped layout
  (`inapp-keyboard/res/xml/termux_launcher_qwerty.xml`, mirrored in the examples file) has **no Esc
  key at all**. Esc is the hidden south-east swipe on `q` (`se="loc esc"`) and `Fn`+`a`, so there is
  no Esc key with an `n=` slot to swipe up on.

Still only reasoned, not seen on a device: every one of the above.

The corner-menu question the pass raised, answered from the code: a plain tap in the middle of a
pane does **not** raise the corner controls — the pane view
returns the touch to the terminal unless it lands in one of `CornerZones`' 32 dp corner squares
(plus 6 dp of slop for the divider). With two panes there are eight of those squares and they
cluster along the shared divider, so a tap anywhere near the split reads as a corner; that is what
the pass saw, not a tap anywhere on the pane.

## Fourth pass (2026-09-14): the run lives on the terminal place

Reported from the device: the tour "falls out of place" when it is started on the Display page, or
when the user leaves the terminal with the extra keys' place buttons instead of the status bar
swipe the card asked for. Three faults behind one symptom:

- The home place was whatever the wall rested on when the run was built. Replay from Settings, and
  a resume after a process death, both restore the wall to its last page, so a run could call the
  Display page home and then ask for the +, the keyboard and the panes from a place that has none.
- A card whose control is on another place kept the position of the last control that could be
  measured (the third pass's rule for a control that is still arriving), so it stood over nothing
  with no glow and no way on.
- The place signal fired on every settle, including a rotation's re-settle of the same place.

All three are in the rule above. Still only reasoned, not seen on a device.

## Fifth pass (2026-09-15): four lessons

From issue #36 (the revised onboarding review). Replaces the nine/thirteen-card run above with
four short lessons, a home-screen question and a closing card — `TourRun.java`'s full rewrite,
`TourController.RUN_VERSION` bumped to 2. Nothing here creates a shell, a window or a session.

### The run

| Step (kind) | Stage | Copy | Target | Gesture | Cleared by |
|---|---|---|---|---|---|
| 1 `find_help` (lesson) | 0 | Tap a pane corner. | Pane corner | tap | pane corner menu opened |
| | 1 | Tap ? to see what the controls do. | Help button | tap | help opened |
| | 2 | Close help to continue. | — | tap | help closed |
| 2 `find_apps` (lesson) | 0 | Pull down on the dock. | Dock | drag down | drawer opened |
| | 1 | Tap an app to open it. | — | tap | app launched |
| | 2 | Press Home to come back. *(launcher is home)* / Switch back to Termux to continue. *(it is not)* | — | tap | launcher resumed |
| 3 `keyboard` (lesson) | 0 | Tap the keyboard button to hide the keyboard. *(keyboard shown when the run was built)* / …to show the keyboard. *(hidden)* | Keyboard toggle key | tap | keyboard hidden / keyboard shown |
| | 1 | Tap it again to bring the keyboard back. / …to hide it. | Keyboard toggle key | tap | keyboard shown / keyboard hidden |
| 4 `find_action` (lesson) | 0 | Swipe up on the space bar to open the command palette. | Space bar | swipe up | palette opened |
| | 1 | Find Settings or Help, then tap outside the palette to close it. | — | tap | palette closed |
| 5 `home_choice` (choice) | — | Use Termux as your home screen? *(not home)* / Termux is your home screen. *(already home)* | — | none | answered by its own button, not a gesture |
| 6 `closing` (closing) | — | That is the tour. Enjoy the launcher. | — | none | Start using Termux |

`RunContext` (`launcherIsHome`, `keyboardShown`) is read once, when the run is built, and decides
the copy above marked *(…)*: lesson 3's two stages swap order so the first ask always matches the
keyboard's actual state, and lesson 2's return line and the home-choice card's question both read
the phone's real home-app setting rather than assuming one.

### Actions per card kind

- **Lesson** (`TourStep.LESSON_ACTIONS`): Back, Skip step, End tour — every one of the four
  lessons above. In practice (below) these are replaced by Done and End practice instead.
- **Choice**: no default; a choice names its own. `home_choice` offers Continue alone when the
  launcher already is the home app, or Use as home screen / Keep trying when it is not.
- **Closing** (`TourStep.CLOSING_ACTIONS`): Start using Termux, alone.

### Practice mode

Help's "Try it" runs one lesson on its own: `TourController.startPractice(lessonId)`. It clears on
that lesson's own signals exactly like a normal run and offers Done / End practice in place of the
lesson's three buttons, but writes nothing to `Prefs` — not the completed version, not the step
index or stage, not the skip flag — so practising a lesson can never finish, restart or skip the
stored run. Only the four lesson ids (`TourRun.lessons()`: `find_help`, `find_apps`, `keyboard`,
`find_action`) are legal practice targets; `home_choice` and `closing` are cards, not lessons, and
cannot be practised.

### Run version 2 and the version-1 mapping

`TourController.RUN_VERSION` is 2. An unfinished run stored under an older version is not resumed
on its own stored card — that card means something else now — it is mapped by
`migratedLessonFor(index)` onto the lesson that covers the same control: the old run's keyboard
chapter (cards 4–8, indices 3–7) → `keyboard`; the pane-corner card (index 8) → `find_help`; the
drawer and A–Z-scrub cards (indices 9–10) → `find_apps`; the palette card (index 11) →
`find_action`. The two status-bar cards and the old closing card (indices 0, 1, 2, 12) have no
equivalent — the new run does not teach page swipes at all — so resuming there asks the user to
pick up or start over (`Listener.onTourResumeOrRestart`, `TourAction.RESUME` / `RESTART`) instead
of dropping them at an arbitrary lesson. A run already finished under any version still counts as
finished; only an in-progress one is mapped.

### The Help chrome rule

`TourChrome` gained a `HELP` member. The drawer and the palette hide every card except the one
asking the user to close them (`TourCardVisibility.chromeClosedBy`); help has no such exception —
`TourCardVisibility.decide` returns `HIDDEN` whenever the chrome set contains `HELP`, checked
before anything else, so even `find_help`'s own third stage (the card asking the user to close
help) waits behind help like any other card. Help is where the first lesson sends people, and a
card drawn over the answer is worse than one that waits. The activity's one show path and one
dismiss path for help feed `TourSignalRelay.onHelpShownSettled(boolean)`, edge-triggered like the
drawer and the status bar: the first call only primes the resting state, and a close of a help the
run never saw open emits nothing.

## Build plan

| Phase | Branch | Deliverable | Depends on |
|-------|--------|-------------|------------|
| 1 Engine | feat/tour-engine | Step state machine over prefs, target host interface, glow + finger overlay, themed card, skip, resume, steps 1–2 wired end to end. Unit tests on the policy. | — |
| 2 Steps | feat/tour-steps | Signal adapters and target rects for steps 3–9, closing card with edition-aware copy, Replay in Settings. Tests per signal. | 1 |
| 3 First-launch chain | feat/first-launch-chain | Wallpaper copy, display dialog, chain order, hold the run until the chain closes. | — |
| 4 Cutover | feat/tour-cutover | Remove footage tour and clips, docs and screenshots, edition check. | 2, 3 |

Side queue: standard Back behaviour in the app drawer (close expanded category, then close the
drawer). Branch `fix/drawer-back`, independent of the tour.

### #36: help by topic and four lessons

| Phase | Branch | Deliverable | Depends on |
|-------|--------|-------------|------------|
| A Model | feat/help-topics-model | `HelpTopics` catalogue and `HelpPresentationModel`, pure and tested. | — |
| B Tour | feat/help-topics-tour | `TourStep` kinds and actions, the help/keyboard/app-launch tour signals, `TourRun`'s four lessons, `TourController` practice mode and the version-2 migration. | A |
| C Wiring | feat/help-topics | `HelpOverlayView` rewired to `HelpPresentationModel` (chooser, overview paging, show gesture, try it), the activity's help show/dismiss paths feeding `TourSignalRelay`, the command palette and Settings entries, accessibility. Not yet branched. | A, B |
| D Docs | feat/help-topics-docs | This spec and `docs/help-overlay.md` brought up to date. | A, B |

A and B are merged into the integration branch `feat/help-topics`.
