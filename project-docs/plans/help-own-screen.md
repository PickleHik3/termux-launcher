# Help as its own screen

Status: agreed with the user 2026-09-19 (review page `.lavish/help-own-screen-20260919`). Builds on
`help-guide.md` (2026-09-18): the topic catalogue, search, glossary, navigation, clips, the corner
overview and the explore/practice overlay all stay. What changes is where the reading happens.

## Why

The Help centre is a sheet drawn over the launcher. It borrows the launcher's keyboard, dodges its
insets and reasons about the place behind it, and a Display topic opened from the Terminal place
came back empty (fixed in cda03b72). The user wants an ordinary Android help screen: Up arrow, Back,
Recents, the system keyboard, reachable from Settings without bouncing through the home screen.

## Decisions

- D1 Hotfix the place-bound topic lookup now — done, cda03b72.
- D2 Corner ? keeps the overview cards first; its Guide button opens the Help screen.
- D3 No "On this screen" section. The screen is the whole guide; the place is only carried for
  the hand-back below.
- D4 Explore this screen, Show on screen and Practice close the Help screen, run on the launcher
  as today, and Back afterwards returns to the Help screen where the reader was.
- D5 Display clips are re-recorded on pong (start, stop, display_keys, display_apps, scale,
  touchpad); setup stays.

## The screen

`HelpActivity` (package `com.termux.app.help`, `exported=false`, the launcher's settings theme so
colours and type match). Content is `HelpPanelView`'s pages — home (search field, browse groups,
glossary, practice list), search, glossary, term, topic with clip — hosted as the activity's
content under a toolbar: Up arrow, the current page title, a search action. `HelpNavigation` keeps
the page stack; the activity's Back pops it and finishes at the root. Search uses a plain EditText
and the system IME; no in-app keyboard, no `onSystemImeRequested`. The topic page's "Not visible on
this screen" note is dropped along with "On this screen" (the screen has no measured targets).

Intent extras: `EXTRA_PLACE` (PaneWallPage name; absent = TERMINAL), `EXTRA_TOPIC` (topic id;
absent = home), `EXTRA_NAVIGATION` (Bundle from `HelpNavigation.saveState()`, restores a page stack).

Hand-back (D4): the activity finishes with `setResult(RESULT_OK, intent)` carrying
`EXTRA_ACTION` ∈ {explore, show_on_screen, show_gesture, practice}, `EXTRA_TOPIC`, and
`EXTRA_NAVIGATION`. `TermuxActivity` launches the screen through an `ActivityResultLauncher`;
on a result it runs the matching `HelpController` overlay call and keeps the navigation bundle.
When that overlay closes (× or Back, or practice ends) `TermuxActivity` relaunches `HelpActivity`
with the bundle, so the reader lands on the page they left.

Entry points: overview Guide button → `TermuxActivity` launches the screen for result with the
current place. Settings → Help → `startActivity(HelpActivity)` directly; Settings stays in the
back stack (the `EXTRA_SHOW_HELP` bounce goes). Command palette → Help → same as Settings.
`TermuxActivity.EXTRA_SHOW_HELP` is kept only as an alias that launches the screen.

## Build plan

| Phase | Branch | Deliverable | Depends on | Model |
|---|---|---|---|---|
| 0 hotfix | fix/help-topic-lookup | topic page finds every topic (cda03b72) | — | orchestrator |
| 1 screen | feat/help-activity | `HelpActivity` + manifest + extras + result contract; `HelpPanelView` loses "On this screen" and the not-visible note; `HelpNavigation.saveState/restore`; Settings, palette and overview Guide open the screen; `TermuxActivity` result launcher + relaunch after overlay; Robolectric tests for launch-with-topic, Back pops then finishes, explore result, restore from bundle | 0 | opus |
| 2 sheet removal | chore/help-sheet-removal | `HelpController` keeps overview/explore/practice only; sheet path, `HelpPanelView` hosting in the view host, IME borrowing and dead strings removed | 1 merged | sonnet |
| 3 display clips | — (assets) | six Display clips from pong, re-crop audit, bundle | — | orchestrator |

Gates: help package tests green per phase; module suite on the merged result; Waydroid walk-through
(Settings → Help, Guide → Help, topic → Show on screen → Back returns); install on pong.
