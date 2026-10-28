# Edit pages overview round (2026-10-27)

Decisions from a grilling over the developer's phone recording of v2.0.0-pre.2 (Moto G15,
`screen-20261027-161651.mp4`, 37 s). The recording and labelled frames A–F are at
`app/evidence/edit-pages-2026-10-27/` beside the repos. The user opened the Home page sheet, tapped
its Edit pages chip, landed in Settings, tapped Edit pages again, then swiped the page carousel for
twenty seconds toggling between the Done bar and the sheet.

All work lands on local `v2-redesign` (worktree base = head). Waves: A and C in parallel (disjoint
files), then B from their merged result. Each wave is built on pong after its merge; the recording's
steps are the check. No pre release from this round yet.

## What the recording shows

1. Sheet's Edit pages chip → black splash with the app icon (0:12) → Settings Home screen scrolled to
   "Pages" with the deep-link ring on the bare header (0:13) → Edit pages row → overview (0:14).
2. In the overview the sheet under the carousel always says Home with Home's elements, whatever card
   is centred (0:16, 0:23, 0:25–0:29).
3. The centred card sometimes fills its frame and sometimes draws as a small top-aligned thumbnail
   inside it (0:29 vs 0:31, same mode); at 0:31 the Home neighbour peeks in at full scale.
4. In the Done-bar editor the Home card contains a clipped full-size copy of the elements sheet
   (0:18).
5. Neighbour cards are empty outlines (0:18, 0:20).
6. Tapping a card zooms into its editor with the Portrait/Done bar; the user tapped cards expecting
   to select them.
7. "Apps row" hidden in the Home elements list while six app icons sit under the clock: not a bug,
   Apps row is a dock element (`ElementRows.java:53`) and the six icons are a widget. Left alone.

## Causes (from code, not yet proven at runtime)

- Items 3 and 4 share one cause: `PageOverviewView.captureLiveCard` (`:870-895`) is a PixelCopy of
  the window rect, so it captures whatever is drawn there at the time, including the overview's own
  card and the sheet. The live path (`:1139-1146`) scales the root by card width; the bitmap path
  (`:1149-1151`) stretches the FULL bitmap to the card rect.
- Item 5: a neighbour with no FULL bitmap, no TEXT snapshot and no backdrop draws nothing.
- Item 2: `EditorCardSheet` is created by `SurfaceEditorController` (`:586-600`); the overview's
  `onSettled` (`:800-818`) only calls `host.preview()`; nothing rebinds the sheet.
- Item 1: the chip is `LINK_PAGES` in `SurfaceEditorController.java:2516` and opens Settings at the
  Pages section; the launcher menu's Edit pages (`TermuxActivity.java:27486`) goes straight to
  `openPageOverview`. The splash is Android's system splash for a new task:
  `SettingsActivity` is singleTask with its own affinity (`AndroidManifest.xml:214-219`).

## Phone repro (pong, 2026-10-29, build 2.0.0-pre.1+dev.788a926b0)

Frames and shots in `app/evidence/edit-pages-2026-10-27/pong-repro/`. Two routes into the overview
behave differently:

- **Home actions sheet → Edit pages** (`04-overview.png`, `f1-swipe-from-menu-route.png`): every
  card fills its frame. A tap on the centred card leaves the overview and opens that page. During a
  swipe the incoming card draws empty for most of the motion, then fills.
- **Home surface editor → sheet chip Edit pages → Settings → Edit pages** (`11-overview2.png`,
  `f5-swipe-editor-route.png`, `f6-tap-editor-route.png`): the overview is layered over the still
  open surface editor, so the centred card's live source is the editor's root, in which the page is
  already drawn at 60 %; the card scales that root again and shows a small top-aligned thumbnail.
  The neighbouring terminal card peeks in at full scale from its text snapshot. The sheet stays Home.
  A tap on the centred terminal card zooms it back into the surface editor, which then shows the
  terminal page at 60 % under the label "Home" with Home's sheet. Back from there asks Discard /
  Save for an edit that was never made.

So the two-scale flip is not timing: it is the editor route. The captured copy of the sheet inside
the Home card (recording 0:18) is the PixelCopy path on the same route. Wave A therefore also has to
make the card source the page at its natural size whenever the overview opens over the surface
editor (undo the editor's preview scale, or hide the editor preview while the carousel is up), and
the neighbour's text snapshot must take the card scale.

Also seen on pong at 420 dpi: the "+ Add terminal" chip clips to "+ Add termina".

## Wave A: the overview view (`PageOverviewView`)

- **Capture gate.** A card's bitmap is taken only while its page is uncovered: before the overview
  and the sheet are shown, never recaptured while covered. A bitmap captured under cover is dropped.
  One phone repro of the scale flip is done first to pin the trigger (recording stands for the rest).
- **Neighbours render.** Every card shows its page at card scale; the centred card is only larger or
  brighter. No empty outlines.
- **Tap centres.** A tap on a non-centred card centres it (the sheet follows on the settle); a tap on
  the centred card zooms into its editor as today.

## Wave C: the Settings seam

- **Chip goes direct.** The sheet's Edit pages chip calls the same `openPageOverview` path as the
  launcher menu. Inside the overview the chip is hidden: the More row shows Global and Gestures only.
- **No splash.** `SettingsActivity` moves to standard launch mode; its task affinity and
  excludeFromRecents stay, so the two notification launches from the model services
  (`TaiModelDownloadService.java:331`, `TaiRuntimeService.java:961`) keep their own excluded task.
  Launches from `TermuxActivity` carry no new-task flag and so join the launcher's task. Terminal-only
  users keep today's Back and Home; the recents card may show Settings on top. The in-Settings sites
  that still relaunch through `createFragmentIntent` (`TaiBenchHomeFragment.java:164,177`,
  `GraphicsAccelerationFragment.java:919`, `GuiAppsSetupGuideFragment.java:81`,
  `StatusWidgetPrivilegedGate.java:94`) move to `openScreen` so they do not stack a second instance.
  The task-root Back workaround (`SettingsActivity.java:850-858`) stays for notification launches.
- **Ring on the rows card.** When a deep link targets a section rather than a row, `SettingsRowRing`
  wraps the section's rows card, not the bare header.

## Wave B: the sheet follows the card

- On the overview's settle the sheet rebinds to the page under the centred card: Home keeps its sheet,
  a terminal card gets the Workspace surface's sheet, a display card folds the sheet to its handle.
  The rebinding goes through the `onSettled` → `host.preview()` seam in `SurfaceEditorController`
  and `TermuxActivity`'s preview implementation (around `:27275`).
