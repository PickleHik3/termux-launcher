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

| # | Card | Then | Cleared by |
|---|------|------|------------|
| 1 | Swipe the status bar left or right. | Swipe back. | place changed; place returned |
| 2 | Drag the status bar down, then up. | | status bar expanded; collapsed |
| 3 | Tap + to open a window. | Tap the window chip, then × to close it. | window count +1; chip selected; window closed |
| 4 | Swipe up on the split key. | | pane.split action |
| 5 | Tap a pane corner. | | pane corner menu opened |
| 6 | Pull down on the dock. | Swipe down to close the drawer. | drawer opened; drawer closed |
| 7 | Slide along the A–Z row, drag up to an app and let go. | | app launched from scrub |
| 8 | Swipe up on the space bar. | | palette opened |
| 9 | Closing card, no gesture. Buttons Copy commands / Done. | | Done |

Closing card body, three lines, edition aware (Nix edition has its own package line; VAJ keeps
`pkg`):

- Hold Ctrl+Alt, or Ctrl+Alt+Shift, to see key hints.
- Install extras from tlstore: fastfetch, sigye, Claude Code. For images in the terminal,
  `pkg install timg`.
- For graphical apps, `pkg install x11-repo`, then launch them from the drawer.

## Rules

- Overlay never consumes a touch. Glow and finger only, drawn over the real control.
- Cards use the app's own surfaces (notice chip / keybind hint card treatment, theme tokens).
  No stock Android dialogs or toasts in the run.
- Targets are measured on every layout pass: keyboard up and down, both dock styles, landscape,
  1.3× font scale.
- Copy: one sentence, product voice, no mechanism.
- Step 7 leaves the launcher; the run must not draw over the launched app and resumes on return.
- No continuous animation except the finger trace while a card is live.

## Build plan

| Phase | Branch | Deliverable | Depends on |
|-------|--------|-------------|------------|
| 1 Engine | feat/tour-engine | Step state machine over prefs, target host interface, glow + finger overlay, themed card, skip, resume, steps 1–2 wired end to end. Unit tests on the policy. | — |
| 2 Steps | feat/tour-steps | Signal adapters and target rects for steps 3–9, closing card with edition-aware copy, Replay in Settings. Tests per signal. | 1 |
| 3 First-launch chain | feat/first-launch-chain | Wallpaper copy, display dialog, chain order, hold the run until the chain closes. | — |
| 4 Cutover | feat/tour-cutover | Remove footage tour and clips, docs and screenshots, edition check. | 2, 3 |

Side queue: standard Back behaviour in the app drawer (close expanded category, then close the
drawer). Branch `fix/drawer-back`, independent of the tour.
