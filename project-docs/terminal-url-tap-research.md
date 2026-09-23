# Why tapping a link in the terminal often does nothing — research

**Date:** 2026-09-22 · **Branch:** `dev` · Nothing implemented; nothing here is a decision.

**Symptom (user's words).** "the url detection in terminal, i find that often times when i tap a
link it does not work even though it gets properly detected as the url (complete url), worse at
times when there is a TUI with a sidebar"

## Answer in one paragraph

Two different pieces of code answer two different questions, and they disagree. The **underline**
the user sees is computed over the whole visible screen at once; the **tap** re-runs the detector
over a window of only ±3 rows around the tapped row. So a URL that a program wrapped by hand over
more than about four rows is underlined in full but, when tapped, either opens a *truncated*
address or resolves to nothing at all — verified by running the detector (see "Measured" below). On
top of that the tap is a **confirmed** tap: it fires 300 ms after the finger lifts, is cancelled
outright if the finger rested 300 ms before lifting (the launcher's own hold), and re-reads the
*live* screen buffer at that moment — so any output arriving in those 300 ms moves the text out
from under the tap. A TUI makes all three worse at once: it wraps by hand (deep manual wrap), it
repaints constantly, and in mouse-tracking mode the lift is *also* sent to the program as a click
before the URL lookup runs, so the program repaints the cell the user aimed at. A sidebar adds its
own bug: the "continue this URL on the next row" rule starts at the next row's first non-blank
character, which for a left-hand sidebar is the **sidebar's own text**, so the URL is joined with
the wrong pane's words and the real continuation row becomes untappable.

## How a link tap works today

1. Touch reaches `TerminalView.onTouchEvent`
   (`terminal-view/src/main/java/com/termux/view/TerminalView.java:1913`), which feeds the hold
   state machine (`:1742`) and then `mGestureRecognizer.onTouchEvent`
   (`:1953`).
2. `GestureAndScaleRecognizer` maps the tap to **`onSingleTapConfirmed`**, not `onSingleTapUp`
   (`terminal-view/src/main/java/com/termux/view/GestureAndScaleRecognizer.java:68`). That fires
   `ViewConfiguration.getDoubleTapTimeout()` = **300 ms** after `ACTION_UP` (AOSP
   `DOUBLE_TAP_TIMEOUT = 300`). Same as upstream Termux.
3. `TerminalView`'s listener (`TerminalView.java:374`) refuses the tap when
   `mEmulator == null` (`:375`), when the launcher's hold already claimed the gesture
   (`mHoldConsumedGesture`, `:378`), or when text selection is up — in which case the tap only ends
   the selection (`:380`).
4. `mClient.onSingleTapUp(event)` (`:385`) →
   `app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:298`.
   - OSC 8 hyperlink first (`:301`, `TerminalEmulator.getHyperlinkUriAt`,
     `terminal-emulator/.../TerminalEmulator.java:4117`): opens a **confirm strip**, so an OSC 8
     link needs a second tap on "Open" (`:748`).
   - Then, only if `terminal-onclick-url-open` is true (`:308`), `urlAtTap` (`:1277`) →
     `UrlDetector.at(screen, column, row)`.
   - Then `ShareUtils.openUrl` (`:311`,
     `termux-shared/src/main/java/com/termux/shared/interact/ShareUtils.java:155`).
5. Cell under the finger: `TerminalView.getColumnAndRow(event, true)` (`:1210`) →
   `getColumnForX` (`:1224`) and `getRowForY` (`:1228`), plus `mTopRow` (scrollback position).
6. The **underline** the user reads as "detected" is a different call: `UrlUnderlines.prepare`
   (`terminal-view/src/main/java/com/termux/view/UrlUnderlines.java:59`) calls
   `UrlDetector.find(screen, topRow, endRow - 1)` — **the whole visible screen** — once per frame
   from `TerminalRenderer.render` (`terminal-view/.../TerminalRenderer.java:973`). The tap calls
   `UrlDetector.at` (`terminal-emulator/src/main/java/com/termux/terminal/UrlDetector.java:103`),
   which is `find(screen, row, row)` — **one row plus `CONTEXT_ROWS = 3`** (`UrlDetector.java:39`,
   `:123`).

### What must be true at the moment of the tap

| Condition | Where |
| --- | --- |
| `terminal-onclick-url-open = true` (fork ships it on) | `TermuxTerminalViewClient.java:308`; `TermuxLauncherConfigInstaller.java:29` |
| Finger lifted **before** the launcher hold fired (300 ms default) | `TerminalView.java:378`, `HoldTiming.java:34` |
| Finger lifted before AOSP's own long press (400 ms) | AOSP `GestureDetector`, `LONG_PRESS_TIMEOUT = 400` |
| No second tap within 300 ms of the lift | `GestureAndScaleRecognizer.java:68` |
| Finger travelled less than touch slop (no `onScroll`) | AOSP `GestureDetector`; `TerminalView.java:390` |
| No second finger (pinch/scale, `ABANDONED`) | `HoldGesture.java:154`, `TerminalView.java:1846` |
| Not selecting text | `TerminalView.java:380` |
| No `ACTION_CANCEL` from a parent (pane corner, status bar, sheet) | `TerminalPaneController.java:3641`; `StatusBarSwipeLayout.java:273` |
| A span from `UrlDetector.at` **still** covers that cell 300 ms later | `UrlDetector.java:103` |

Note: mouse-tracking mode does **not** gate the URL path. The client only consults
`isMouseTrackingActive()` for the *keyboard* branch (`TermuxTerminalViewClient.java:315`), after the
URL branch has already returned.

## Measured

Run against this tree's own detector (temporary JUnit probe on a 48-column screen, deleted after
the run; `:terminal-emulator:testDebugUnitTest`). `find()` = what is underlined, `at()` = what a tap
gets.

**(a) URL wrapped by hand over six full-width rows** (a TUI, `git log`, a wrapped log line):

```
find(whole screen): https://…aaaa…bbbb…cccc…dddd…eeee…fffffffffff.html  [0:0-48][1:0-48]…[5:0-16]
at(row 0) = https://…aaaa…bbbb…cccc…dddd          <- truncated, 4 rows
at(row 1) = https://…aaaa…bbbb…cccc…dddd…eeee     <- truncated, 5 rows
at(row 2) = full URL
at(row 3) = full URL
at(row 4) = null                                   <- nothing happens
at(row 5) = null                                   <- nothing happens
```

All six rows are underlined. Tapping rows 4–5 does nothing; tapping rows 0–1 opens a **different,
truncated** address.

**(b) Left-hand sidebar, URL wrapped in the right pane:**

```
row 0: "sidebar a   │ https://example.com/aaaaaaaaaaaaa"
row 1: "sidebar b   │ bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
row 2: "sidebar c   │ ccccccccccccc.html"

find(whole screen): https://example.com/aaaaaaaaaaaaasidebar  [0:14-47][1:0-7]
at(col 20, row 0) = https://example.com/aaaaaaaaaaaaasidebar   <- wrong address
at(col 20, row 1) = null
at(col 20, row 2) = null
```

The continuation was taken from the **left sidebar's own text** on row 1, and the sidebar word is
underlined as part of the link.

**(c) Right-hand sidebar, URL in the left pane:** the join is never attempted (the URL is not at the
end of the row's text, `UrlDetector.java:154`), so only the first row's fragment is detected and the
continuation row is dead.

**(d) Emulator-wrapped URLs** (no TUI, real wrap flags): correct on every row — wrap chains are
followed up to `MAX_WRAP_CHAIN = 64` (`UrlDetector.java:42`, `:124`). This is the case that works,
which is why the bug reads as intermittent.

## Why a tap fails — ranked

### 1. Underline and tap use different scan windows (highest)

`UrlUnderlines.prepare` scans the whole screen (`UrlUnderlines.java:64`); `UrlDetector.at` scans
`row ± CONTEXT_ROWS(3)` (`UrlDetector.java:39`, `:105`, `:123`). For a **manually** wrapped URL
(TUI, multiplexer pane, `fold`-style output) the two answers differ as soon as the address spans
more than ~4 rows: the tap gets a truncated address or `null`, while the screen shows a complete
underlined link. Matches the user's words exactly ("properly detected as the url (complete url)"
and yet nothing happens). Emulator-wrapped rows are immune, which is why it feels random.

- *Confirm on device:* print `UrlDetector.at` and `UrlDetector.find` for the same cell behind a
  debug log line, then tap the 5th row of a long wrapped URL in a narrow pane.
- *Fix:* small. Give `at()` the same window as the renderer — take the span list the renderer
  already computed for this frame (it is cached and keyed on a row hash, `UrlUnderlines.java:60`)
  and hit-test against that, so what is underlined is by construction what opens. Removes the
  duplicate regex pass too.

### 2. The tap is cancelled by the launcher's own hold at 300 ms

`postDelayed(mHoldRunnable, HoldTiming.holdTimeoutMs())` (`TerminalView.java:1759`); default
`holdTimeoutMs()` = ¾ × 400 ms = **300 ms**, floor 250 ms (`HoldTiming.java:19`, `:34`). At that
moment a plain shell starts text selection (`HoldGesture.java:103`) and a mouse-tracking terminal
hands the finger the mouse (`:108`); either way `mHoldConsumedGesture` is set
(`TerminalView.java:1854`) and `onSingleTapUp` returns without ever reaching the client
(`TerminalView.java:378`). HoldTiming's own comment says "a slow deliberate tap lands around
200 ms" — that is only 100 ms of headroom, and aiming at a one-character-tall link makes people
slower. Upstream Termux has no such hold; its ceiling is AOSP's 400 ms long press.

- *Confirm on device:* log the down→up duration next to whether the URL opened; or raise
  `MIN_HOLD_MS` temporarily and see the failure rate drop.
- *Fix:* small but a judgement call — e.g. do not start the selection hold when the landing cell is
  inside a detected URL span, or open the URL from the hold's `CLICK`/`HOLD_SELECTED` path.

### 3. The screen moves between the lift and the 300 ms confirmation

`onSingleTapConfirmed` re-reads the **live** buffer 300 ms later. Output arriving meanwhile shifts
rows while `mTopRow` stays 0 (`TerminalView.java:713`), so the cell computed from the finger
position no longer holds the URL. A TUI repaints many times a second; a shell at an idle prompt
does not. Also in this window: a running scroll settle (120 ms, `TerminalView.java:187`, `:1657`)
moves content up to half a row under the finger after the lift.

- *Confirm on device:* tap a link in `watch -n0.2 date`-style output vs. at a still prompt and
  compare hit rates.
- *Fix:* medium — capture the span at `ACTION_UP` (or at `onDown`) and act on the captured span at
  confirmation, instead of re-reading the buffer.

### 4. Sidebar continuation takes the wrong pane's text

`Line.continuationStart()` (`UrlDetector.java:266`) returns the first non-space character of the
next row, on the documented assumption that everything before it is "a pane border, blanked in
`appendRow`, its padding and any indentation". That is true only when the border is at the left
margin (the existing test `testBorderedPaneWithIndentedContinuationIsJoined`). With a **left-hand
sidebar that has text**, the first non-space character is the sidebar's own label — measured in (b)
above. Result: a corrupted URL on the first row and a dead continuation row.

- *Confirm on device:* lazygit / yazi / a tmux left pane with a long URL in the right pane.
- *Fix:* medium — the continuation must start in the same *column band* as the address it continues
  (record where the address's row had its border/padding, and require the next row's text to begin
  at or after that column), not merely at the row's first non-blank.

### 5. Mouse tracking delivers the tap to the program first

With a TUI reading the mouse, `onUp` sends a full press+release at the tapped cell immediately
(`TerminalView.java:362`, `sendClickAt` `:1704`) and the URL lookup still runs 300 ms later. Both
happen — upstream acknowledges this in PR #2146. So mouse tracking alone does **not** swallow the
URL tap; what it does is give the program 300 ms to repaint the cell before the lookup, which
converts into cause 3. (If the finger rests past 300 ms, cause 2 takes over and only the program's
click is delivered.)

- *Confirm on device:* `printf '\e[?1006h\e[?1000h'` then tap a URL; the click reaches the program
  and the browser still opens if nothing repaints.
- *Fix:* not a fix on its own; ordering falls out of cause 3's fix.

### 6. A parent cancels the gesture

The pane-corner overlay forwards the touch to the terminal with `setHoldExempt(true)`
(`TerminalPaneController.java:3579`) and, if the corner hold fires, sends the terminal an
`ACTION_CANCEL` (`:3641`), which makes AOSP's `GestureDetector` drop the pending confirmed tap. So a
link inside a pane's corner square is unreliable when split panes are up. `StatusBarSwipeLayout`
(`:273`) and `PaneContentFrame` (`:141`) also re-route touches, but only for edge/clearance
gestures.

- *Confirm on device:* tap a link in a pane's corner vs. its middle, with a split.
- *Fix:* small; probably not worth it on its own.

### 7. Silent intent failure (lowest)

`ShareUtils.openUrl` catches `ActivityNotFoundException` and falls back to the chooser, but the
generic `catch (Exception)` only logs (`ShareUtils.java:161-167`) — nothing on screen. The context
is the activity (`TermuxActivity.java:19196`), so there is no missing-`NEW_TASK` problem, and the
launcher is foreground, so no background-activity-start restriction. Exotic schemes the regex
accepts (`ws://`, `vnc://`, `dns://`, `tcp://` — `UrlDetector.java:368-395`) with no handler end at
the chooser or at a silent log line.

- *Confirm on device:* `logcat -s ShareUtils` while tapping a failing link.
- *Fix:* trivial — a toast on the swallowed branch would at least separate "nothing matched" from
  "nothing could open it".

## The sidebar / TUI case, separately

The user is right that it is worse, and it is worse for four independent reasons at once:

1. **Narrow pane ⇒ deep manual wrap.** A sidebar halves the text column, so an ordinary 80–120
   character URL now occupies 3–6 rows of *manual* wrapping. Cause 1 bites at ~4 rows, so ordinary
   links start failing.
2. **The sidebar's own text is eaten as the continuation** (cause 4, measured). Left-hand sidebar:
   wrong address on the first row, dead continuation row. Right-hand sidebar: the join is never
   attempted at all and the address is truncated at the pane edge.
3. **Constant repaint** (cause 3): the 300 ms confirmation window is the TUI's redraw window.
4. **Mouse tracking** (cause 5) delivers the tap to the program first, which is what causes that
   redraw.

Extraction itself handles the border glyph correctly — `isBorderGlyph` blanks `|` and
`U+2500..U+259F` to a space while keeping the cell mapping one char per cell
(`UrlDetector.java:186`, `:216`), so the border is never part of the address and the columns stay
aligned. The breakage is in *which rows get joined*, not in the glyphs.

Distinguishing the user's two cases:

- **"Detected but the wrong region is clickable"** — causes 1 and 4. The underline is right (whole
  screen scan); the tap's own scan sees a different, smaller world.
- **"Not detected"** — only really happens for the rows past the ±3 window, which is the same bug
  seen from the other end.

## Hit-test geometry (checked, not a suspect)

- Column: `(x - getHorizontalContentOffset()) / mFontWidth` (`TerminalView.java:1224`), and cells
  are drawn at the same `horizontalOffset` (`TerminalRenderer.java:1024`). Consistent. Wide glyphs
  map both their columns (`UrlDetector.java:217`).
- Row: `(y - getVerticalContentOffset() + mScrollOffsetPixels - mFontLineSpacingAndAscent) /
  mFontLineSpacing` (`TerminalView.java:1228`). The drawn cell rect for visible index *i* is
  `[heightOffset - mFontLineSpacing, heightOffset)` with `heightOffset = mFontLineSpacingAndAscent +
  (i+1) * mFontLineSpacing` (`TerminalRenderer.java:1159`, `:1883`, `:2388`) — **exactly** the band
  the hit test computes. No off-by-one, no descender offset.
- `getVerticalContentOffset()` returns 0 on the alternate buffer (`TerminalView.java:2620`), i.e.
  for every TUI, and both draw and hit test read it. Consistent.
- Scrollback: `getColumnAndRow(e, true)` adds the *current* `mTopRow` (`:1214`). The only staleness
  is temporal (cause 3), not geometric.
- Split panes: the pane's `OnTouchListener` focuses the tapped session synchronously on
  `ACTION_DOWN` (`TerminalPaneController.java:2928`), and `currentSession()` derives from
  `getTerminalView()` (`TermuxActivity.java:19214`), so `term` and the coordinates belong to the
  same pane. Checked and cleared.

## What this fork changed vs upstream

- Upstream takes the whitespace-delimited **word** under the finger and regexes it
  (`getWordAtLocation` + `TermuxUrlUtils.extractUrls`, upstream
  `TermuxTerminalViewClient.onSingleTapUp`, lines 186–206). Single row only: a wrapped URL never
  opens whole there. This fork's `UrlDetector` is strictly better — the failures above are its own
  new edges, not regressions.
- Upstream has no underline, so upstream users never see the "it is clearly detected" mismatch.
- Upstream has no launcher hold; cause 2 is this fork's.
- `onSingleTapConfirmed` and the mouse-tracking double delivery are upstream behaviour, unchanged.

## Open questions / needs a device

- **Actual frequency of each cause.** Everything above is static + a JUnit probe; nothing has been
  measured on pong or in Waydroid. A single debug log line (down→up ms, hold fired?, span at up vs
  span at confirm, span from the renderer's cache) would rank 1/2/3 in one session.
- **How often the user's real links wrap past 4 rows.** Depends on their font size and pane width;
  measure on their actual screenshot.
- **OSC 8 links.** Tapping one opens a confirm strip rather than the browser
  (`TermuxTerminalViewClient.java:301`, `:748`). Whether the user reads that as "did not work" is
  unknown — worth asking.
- **Whether their failing taps are in a pane corner** (cause 6) — only observable on device.
- **Whether any failing tap reaches `openUrl` at all** — needs logcat.
- Not investigated: accessibility/TalkBack touch exploration, and whether an in-app keyboard
  overlay ever covers the tapped row.

## Sources

Repo (all under `/home/amalvajdan/Projects/termux-launcher/app/termux-launcher`):

- `app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java:183`, `:298`, `:301`,
  `:308`, `:311`, `:315`, `:748`, `:780`, `:1277`
- `terminal-view/src/main/java/com/termux/view/TerminalView.java:187`, `:269`, `:353`, `:362`,
  `:374`, `:378`, `:380`, `:385`, `:390`, `:430`, `:441`, `:508`, `:713`, `:1210`, `:1224`,
  `:1228`, `:1657`, `:1704`, `:1735`, `:1742`, `:1759`, `:1826`, `:1846`, `:1854`, `:1913`,
  `:1953`, `:2599`, `:2620`
- `terminal-view/src/main/java/com/termux/view/GestureAndScaleRecognizer.java:60`, `:68`, `:97`
- `terminal-view/src/main/java/com/termux/view/HoldTiming.java:19`, `:34`, `:56`
- `terminal-view/src/main/java/com/termux/view/HoldGesture.java:100`, `:114`, `:154`, `:169`
- `terminal-view/src/main/java/com/termux/view/TapPrecision.java:36`
- `terminal-view/src/main/java/com/termux/view/UrlUnderlines.java:23`, `:59`, `:64`
- `terminal-view/src/main/java/com/termux/view/TerminalRenderer.java:973`, `:1024`, `:1159`,
  `:1883`, `:2388`
- `terminal-emulator/src/main/java/com/termux/terminal/UrlDetector.java:39`, `:42`, `:103`, `:115`,
  `:123`, `:154`, `:186`, `:201`, `:247`, `:266`, `:362`
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java:4117`
- `terminal-emulator/src/test/java/com/termux/terminal/UrlDetectorTest.java` (existing coverage;
  every case there is 1–2 rows deep, which is why this never showed up)
- `termux-shared/src/main/java/com/termux/shared/interact/ShareUtils.java:155`
- `termux-shared/src/main/java/com/termux/shared/termux/settings/properties/TermuxSharedProperties.java:584`
- `app/src/main/java/com/termux/app/terminal/TerminalPaneController.java:2928`, `:3579`, `:3627`,
  `:3641`
- `app/src/main/java/com/termux/app/terminal/PaneContentFrame.java:141`
- `app/src/main/java/com/termux/app/statusbar/StatusBarSwipeLayout.java:273`
- `app/src/main/java/com/termux/app/TermuxActivity.java:1884`, `:13737`, `:18468`, `:19196`,
  `:19214`

External:

- Upstream client: <https://raw.githubusercontent.com/termux/termux-app/master/app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java>
- Upstream recogniser: <https://raw.githubusercontent.com/termux/termux-app/master/terminal-view/src/main/java/com/termux/view/GestureAndScaleRecognizer.java>
- Upstream PR that added tap-to-open, incl. the mouse-tracking double delivery:
  <https://github.com/termux/termux-app/pull/2146>
- AOSP `ViewConfiguration` (`LONG_PRESS_TIMEOUT = 400`, `DOUBLE_TAP_TIMEOUT = 300`,
  `TAP_TIMEOUT = 100`, `TOUCH_SLOP = 8`):
  <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewConfiguration.java>
- AOSP `GestureDetector` (confirmed-tap and `ACTION_CANCEL` handling):
  <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/GestureDetector.java>

## Device verification — pong, 2026-09-22

Reproduced on pong (com.termux, the user's own install) against this chat running in herdr
with its left sidebar. Method: one script shot the screen, tapped, and shot again in a single
command, so the transcript could not scroll between the screenshot and the tap; every tap point
was checked against the pre-shot before the result was believed. `input tap` is a ~10 ms
down/up, so the 300 ms hold (cause 2) and the confirmed-tap window (cause 3) were not exercised
— this tests the detector only.

Results:

| target | outcome |
| --- | --- |
| one-row URL (`https://example.com/a`) | opens correctly |
| first row of a 5-row hand-wrapped URL | opens **truncated** |
| any continuation row of the same URL | nothing happens |

The truncation is exact and silent. `ActivityTaskManager: START … act=VIEW` for the row-0 tap
carried `capturedLink=https://example.com/e/aaaaaaaaaa/bbbbbbbbbb/cccccccc` — precisely the
first visual row, cut mid-segment at the pane's right edge. The browser opened a page the user
never asked for, which is worse than nothing happening.

Only the first row is underlined (zoomed crop of the tap-time screenshot): with the sidebar
open the join stops after row 0, so the detector and the renderer agree here and both are
wrong. That refines cause 1 — in this layout the underline does not run ahead of the tap;
detection itself stops at one row.

Collapsing herdr's sidebar and repeating: the E-URL then showed two underlined rows instead of
one, i.e. the join reached further with the sidebar gone. Continuation rows were still not
tappable (the collapsed rail still carries glyphs). This is the on-device counterpart of the
`continuationStart()` finding at `terminal-emulator/src/main/java/com/termux/terminal/UrlDetector.java:266`:
the next row's first non-blank character belongs to the sidebar, not to the URL, so the address
is cut at the pane edge.

Not yet tested on device: a full-width pane with no sidebar at all (would separate "hand-wrap
alone truncates at ~4 rows", which the JUnit probe showed, from "a sidebar truncates at 1 row");
and slow finger taps, which are what exercise causes 2 and 3.

## Resolution — branch `fix/url-wrap-detection`, commit f50b5df2

Diagnosed test-first. Loop (4s, deterministic):
`JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :terminal-emulator:testDebugUnitTest --tests '*UrlDetectorTest*'`

Minimising separated what the device could not: the sidebar defect needs only **two** rows, and
the context-window defect needs no sidebar at all. They are independent.

1. **A sidebar's text was read as the address continuing.** `continuationStart()` took the next
   row's first non-blank character; with a sidebar that is the sidebar's own word, so the address
   grew into `…/aaaaaaaaaa/bmac` and the real continuation became untappable. Fixed by
   `Line.blockColumn()`: an address with ≥2 blank cells before it on its row opens a block of its
   own, and a continuation may not start left of that block.
2. **The tap searched a window, the underline searched the screen.** `at()` used
   `find(screen, row, row)` (±3 rows, extended only along emulator wrap flags); the renderer
   underlines `find(screen, topRow, endRow-1)`. Hand-wrapped rows carry no wrap flag, so past ~4
   rows the two disagreed — the underline showed a whole address, the tap handed the browser a
   truncated one. `at()` now searches what the renderer underlines.

Three regression tests, each verified red with the fix reverted and green with it:
`testASidebarsTextIsNotGluedOntoAnAddress`, `testWrappedAddressBesideASidebarIsJoined`,
`testAnAddressWrappedPastTheContextWindowIsWholeFromEveryRow`. Full `testDebugUnitTest` green
(2m38s).

Still unverified on device: the fix has not been built or installed on pong, so the original
finger repro has not been re-run. Causes 2 and 3 of the earlier analysis (the 300 ms hold and the
confirmed-tap window) are untouched — they were never exercised, since `input tap` is ~10 ms.

## Device verification of the fix — pong, 2026-09-22 20:21

Debug APK from f50b5df2 installed on pong (`lastUpdateTime=2026-09-22 20:21:12`), herdr restarted
with its sidebar open, same shoot-tap-shoot method, every tap point checked against the pre-shot.

| tap | before | after |
| --- | --- | --- |
| first row of a 4-row wrapped URL | opened the first row only | opens the whole address |
| **last** row of the same URL | nothing | opens the whole address |
| one-row URL | opened | opens |
| words next to a link | — | nothing (correct) |

Both wrapped-row taps produced `capturedLink=https://example.com/…/rrrrrrrrrr/end` in the launch
intent — the address entire. All four rows are underlined now; before the fix only the first was.

## Follow-up — the block rule was too exact (commit cc547c5a)

Device report on a five-link sheet: links 1, 4 and 5 worked; the two wrapped ones were underlined
on their first row only. Not the original bug — a regression from the fix. `blockColumn` required
a continuation to start at or right of the column the address started at, and a pane's text column
moves by a column or two between a first row and its continuations. Measured in the harness:

    offset  0: row0=whole  row1=whole
    offset +1: row0=short  row1=NULL

A run is now rejected only when it ends ≥2 columns clear of the address's block. All offsets come
back whole; full `testDebugUnitTest` green.

Known limit: a sidebar whose text stops one column short of the pane's own column leaves no gap
for `blockColumn` to see, so its text can still be glued on. A separate mechanism also remains
unreproduced on the device — rows joined by the emulator's wrap flag interleave a sidebar's text
between an address's rows, which the `padded` probe shows but which cannot be what the device did,
since it would have broken link 5 too.
