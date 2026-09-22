# Why the one-cell "«" at the pane's bottom-left is hard to tap

**This is the first research note in this repository.** `docs/` holds the project's specs; research
notes live in `docs/research/`, named `YYYY-MM-DD-topic.md`, and say what was measured and what was
only reasoned, with a citation per claim.

Repo at `dev` @ `b8423175`. Device: pong, 1080×2412, density 420 (scale 2.625). Captures in
`app/termux-launcher-hold/app/build/pong/` (`touch.log`, `act.txt`, `screen.png`, `screen2.png`).

## Summary

**Verified**

- The taps are clean: 49–104 ms, 0–1 px of movement. No hold fires, nothing is stolen (claim 1).
- `PaneContentFrame` insets `TerminalView` by 8 px on all four sides and nothing in that band
  handles touches — a real dead region (claim 2), **but not the one that breaks this button**.
- The touch→cell mapping is the exact inverse of the draw path on both buffers (claim 3). No
  off-by-one.
- The cell is **14.07 × 31 px = 5.36 × 11.8 dp** (measured from the screenshot), against Android's
  48 × 48 dp guideline (claim 4). The nine compact taps land on **three different columns**; the
  sixteen expanded taps land on **five**, and all of them work — the working target is ≥5 cells wide.
- `TouchDelegate` re-centres forwarded events, so it cannot extend the terminal's hit area (claim 5).

**Refuted / corrected**

- The compact "«" sits inside the pane's **40 dp bottom-left corner square**, where
  `PaneInteractionOverlay` forwards every event straight into `TerminalView` by
  `dispatchTouchEvent` — which does **no** bounds check — and `TerminalEmulator.sendMouseEvent`
  clamps to the edge cell. So in that corner the 8 px margin is *not* dead: a tap at x = 20 reaches
  column 1. The expanded button is 244 px from the pane's left edge and is **not** in a corner
  square, so it takes the ordinary dispatch path. The dead-margin theory therefore does not explain
  the failures here; **horizontal target size does.**
- No rounded-border fix shifted the touch-to-cell mapping. `44b96c08` created the dead band.

**Unverified**

- Which terminal column herdr paints "«" on in the *collapsed* state. Neither screenshot shows that
  state, so the per-tap hit/miss verdict cannot be settled from this data.
- What changed the pane's height between the two screenshots, and whether any individual tap in the
  log actually toggled the sidebar. `FLSA` (`mFontLineSpacingAndAscent`) is estimated at ~6 px, so
  row indices below the last row are approximate.

Everything in Data is measured from the captures; every code statement is read from source at
`b8423175`. Nothing here was re-checked on the device.

## Data

Parsed from `touch.log` (`ABS_MT_TRACKING_ID` down→`ffffffff`; the driver re-reports only changed
axes, so tap 10 inherits its position from the previous packet).

Column grid measured from `screen2.png`: horizontal autocorrelation of three text lines peaks at
lag 14 on all three, inter-glyph gap midpoints step by 14.07 px, vertical autocorrelation peaks at
31. With `TerminalView` 1032 px wide that is 73 columns and
`getHorizontalContentOffset() = (1032 − 73×14.07)/2 ≈ 2.4`, so 1-based column *c* spans absolute
x ∈ [26.4 + 14.07(c−1), 26.4 + 14.07c).

**Layout uncertainty.** `act.txt` gives `terminal_view` at absolute **(24,231)–(1056,1333)** inside
`PaneContentFrame` (16,223)–(1064,1341) — an 8 px inset on every side. `screen2.png` agrees (pane
border measured at x≈16, y≈1337–1340). `screen.png`, 5 minutes earlier, has the **same** top
(y≈222–225), left (x≈15–18) and right (x≈1063) borders but its bottom border at **y≈1398**, 58 px
lower. Call these layout **A** (act.txt, view bottom 1333) and **B** (screen.png, view bottom
≈1391). Under A, 24 of the 25 button taps fall outside the view — impossible, since the expanded
button demonstrably works. **Layout B is the one in force during the capture**; A is the state after
the two long presses at the end of the log (taps 26–27).

| # | x | y | dur ms | move px | col | in view (A) | in view (B) | target |
|---|---|---|---|---|---|---|---|---|
| 1 | 295 | 1383 | 74 | 0 | 20 | no | yes | expanded |
| 2 | 283 | 1368 | 82 | 0 | 19 | no | yes | expanded |
| 3 | 39 | 1408 | 78 | 0 | **1** | no | **NO** | compact |
| 4 | 46 | 1396 | 49 | 0 | 2 | no | **NO** | compact |
| 5 | 277 | 1368 | 95 | 0 | 18 | no | yes | expanded |
| 6 | 45 | 1369 | 78 | 0 | 2 | no | yes | compact |
| 7 | 266 | 1361 | 104 | 0 | 18 | no | yes | expanded |
| 8 | 260 | 1383 | 79 | 0 | 17 | no | yes | expanded |
| 9 | 295 | 1372 | 78 | 0 | 20 | no | yes | expanded |
| 10 | 295 | 1386 | 91 | 0 | 20 | no | yes | expanded |
| 11 | 318 | 1393 | 87 | 0 | 21 | no | **NO** | expanded |
| 12 | 296 | 1378 | 58 | 0 | 20 | no | yes | expanded |
| 13 | 287 | 1372 | 91 | 0 | 19 | no | yes | expanded |
| 14 | 44 | 1370 | 87 | 0 | 2 | no | yes | compact |
| 15 | 282 | 1366 | 104 | 0 | 19 | no | yes | expanded |
| 16 | 42 | 1325 | 100 | 1 | 2 | yes | yes | compact |
| 17 | 56 | 1398 | 61 | 0 | **3** | no | **NO** | compact |
| 18 | 50 | 1392 | 74 | 0 | 2 | no | **NO** | compact |
| 19 | 267 | 1379 | 91 | 0 | 18 | no | yes | expanded |
| 20 | 300 | 1380 | 83 | 0 | 20 | no | yes | expanded |
| 21 | 282 | 1371 | 83 | 0 | 19 | no | yes | expanded |
| 22 | 41 | 1379 | 103 | 0 | 2 | no | yes | compact |
| 23 | 304 | 1389 | 91 | 0 | 20 | no | yes | expanded |
| 24 | 284 | 1372 | 91 | 0 | 19 | no | yes | expanded |
| 25 | 44 | 1373 | 96 | 0 | 2 | no | yes | compact |
| 26–29 | 794–1015 | 1331–1394 | 70–1177 | 55–71 | — | — | — | elsewhere; 26 and 27 are long presses |

Compact: columns **1, 2, 2, 2, 2, 2, 2, 2, 3** — a 17 px horizontal spread over a 14 px cell.
Expanded: columns **17–21**, every one of which works. The "«" glyph in `screen2.png` measures
absolute x 255–264, y 1304–1314 — exactly one cell, column 17 — so the expanded target is that
glyph plus ≥4 more cells of clickable label.

Vertically all 25 taps map to the terminal's **bottom row** under layout B: those below the view
(3, 4, 11, 17, 18) are inside the bottom-left corner square or, for 11, three px below the view
where the ordinary dispatch drops them (see claim 2).

## Claim-by-claim

### 1. The taps are clean — VERIFIED

Durations 49–104 ms; movement 0 px on 24 of 25 taps, 1 px on one. The corner hold needs
`HoldTiming.holdTimeoutMs()` = `max(250, 3/4 × ViewConfiguration.getLongPressTimeout())` = 300 ms on
a default phone (`terminal-view/src/main/java/com/termux/view/HoldTiming.java:19-23,34-42`), so no
tap comes close; on `ACTION_UP` the overlay reads `CornerHold.Lift.NOTHING` and forwards the up
(`app/src/main/java/com/termux/app/terminal/TerminalPaneController.java:3474-3481`). No scroll
recogniser fires either: `TapPrecision.ScrollDelivery` counts a scroll only once a row, column or
pixel actually moved (`terminal-view/src/main/java/com/termux/view/TapPrecision.java:36-63`).

### 2. The inset band is dead — VERIFIED as a mechanism, REFUTED as this bug's cause

`PaneContentFrame.onMeasure` sets the terminal's four margins to
`PaneShape.contentInsetForBounds(radius, w, h)` before `super.onMeasure`
(`app/src/main/java/com/termux/app/terminal/PaneContentFrame.java:101-116`), where the inset is
`ceil(r × (1 − 1/√2))` ≈ 0.293 r (`PaneShape.java:29,54-62`). Pane glass is active with the 10 dp
default (`PaneGlass.java:19,29-31`), giving `ceil(26.25 × 0.2929) = 8` — exactly the 8 px seen in
`act.txt`. `PaneContentFrame` overrides no touch method and a `FrameLayout` is not clickable, so a
touch there hits no view that acts on it: `ViewGroup.dispatchTouchEvent` skips a child whose
`isTransformedTouchPointInView` is false, which is `View.pointInView` —
`localX >= -slop && localY >= -slop && localX < (mRight-mLeft)+slop && ...` with slop 0
([ViewGroup.java:3075-3086](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewGroup.java),
[View.java:20487-20490](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/View.java));
and a non-clickable `View.onTouchEvent` returns false (View.java:18059-18085, final `return false`
at :18265).

**But** the compact button is inside the pane's bottom-left corner square. That square is
`CornerZones.PANE_SIZE_DP = 40` (`app/src/main/java/com/termux/app/chrome/CornerZones.java:37`) =
105 px, plus `dp(6)` slop (`TerminalPaneController.java:3783-3784`), measured from the *pane* rect
(16, 223)–(1064, ≈1399). Every compact tap is ≤40 px from the pane's left edge and ≤105 px from its
bottom, so all nine land in `CornerZones.BOTTOM_LEFT`. There the overlay arms the hold and
**forwards**: `forwardToTerminal` offsets a copy by the screen-location delta and calls
`view.dispatchTouchEvent(copy)` directly on the `TerminalView`
(`TerminalPaneController.java:3389-3392, 3621-3630`) — a leaf `View.dispatchTouchEvent` performs no
bounds check. The resulting out-of-range cell is then clamped:
`sendMouseEventAt` adds 1 (`TerminalView.java:1594-1596`) and
`TerminalEmulator.sendMouseEvent` pins column to [1, mColumns] and row to [1, mRows]
(`terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java:771-779`).

So inside a corner square the margin resolves onto the edge cell, and outside one (the expanded
button at 244 px in, tap 11 three px below the view) it is genuinely dead. Counterintuitively, a
tap at x = 20 — on the pane border — works, while a tap at x = 45 does not.

### 3. The mapping is the exact inverse of the draw — VERIFIED, both buffers

Draw: `onDraw` translates the canvas by `getVerticalContentOffset() − mScrollOffsetPixels`
(`TerminalView.java:2390-2396`); the renderer puts screen row *i*'s cell background at
`top = mFontLineSpacingAndAscent + i×mFontLineSpacing` (`TerminalRenderer.java:872,879,888`, with
`top = heightOffset − mFontLineSpacing` at `:1559`) and column *c* at
`left = horizontalOffset + c×mFontWidth` (`:1424-1425, 2228`).

Touch: `getRowForY(y) = (int)((y − getVerticalContentOffset() + mScrollOffsetPixels −
mFontLineSpacingAndAscent) / mFontLineSpacing)`,
`getColumnForX(x) = (int)((x − getHorizontalContentOffset()) / mFontWidth)`
(`TerminalView.java:1218-1228`) — algebraically the inverse, same `mScrollOffsetPixels` sign.
`getVerticalContentOffset()` returns 0 on the alternate buffer (`:2515-2522`) and is the *same call*
in both paths, so the top anchor cannot desynchronise them; `getCursorX/Y` and `getPointX/Y`
(`:2474-2492`) use the same pair.

`TapPrecision.clickPointFor` takes the **press** point when press→lift travel is under one row
height, and the lift point otherwise (`TapPrecision.java:23-28`); it is called with
`mRenderer.mFontLineSpacing` as that radius (`TerminalView.java:359-361, 1725`). With 0–1 px of
travel here, the cell always comes from the press point.

### 4. A one-cell target is ≈5 dp — VERIFIED, and this is the cause

14.07 px / 2.625 = **5.36 dp** wide, 31 px = **11.8 dp** tall: 11 % of the width and 25 % of the
height Android asks for — "at least 48dp x 48dp. Larger is even better"
([Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps)).
Observed aim scatter on the compact target is 17 px horizontally (columns 1–3) and 83 px vertically;
on the expanded target, 58 px horizontally (columns 17–21) and 33 px vertically — and the expanded
target absorbs all of it. A single-cell target cannot: whichever column carries the collapsed "«",
at least two of the nine taps land on a neighbour.

### 5. `TouchDelegate` cannot help — VERIFIED

`TouchDelegate.onTouchEvent` rewrites an in-bounds event to
`event.setLocation(mDelegateView.getWidth() / 2, mDelegateView.getHeight() / 2)` — the delegate's
centre — and an out-of-bounds one to `(-slop*2, -slop*2)`
([TouchDelegate.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/TouchDelegate.java)).
Every forwarded touch would therefore report the middle cell of the terminal. It is consulted before
the clickable branch of `View.onTouchEvent` (View.java:18079-18083), so there is no way around it.
The launcher's own `forwardToTerminal` is the right shape and already exists.

## The rounded-border history, and what it did to touch

| Date | Commit | What it changed | Touch effect |
|---|---|---|---|
| 2026-04-11 | `1b11dd16` | introduced `getHorizontalContentOffset` (centres the grid) | none — used by draw *and* `getColumnForX` |
| 2026-08-17 | `50f7cd82` | clipped floating panes to their corners | none (this is the overflow the developer remembers) |
| 2026-08-20 | `884dd66d` | per-pane glass slab, `setClipToOutline(true)` | none — clipping predates the inset by 11 days |
| 2026-08-26 | `58d9e112` | `PaneShape.radiusForBounds`, cap at ⅓ of the shorter side | none |
| **2026-08-31** | **`44b96c08`** | **`contentInsetPx` + `PaneContentFrame`: the terminal gets an 8 px margin** | **(a) created the dead band** |
| 2026-08-31 | `ebaa24e0` | `getVerticalContentOffset`: bottom-anchor the grid; margin restored to all four edges | (b) moves the grid, but folded into draw *and* both mappings — coherent |
| 2026-08-31 | `836717d3` | alternate screen keeps the top anchor | same — offset 0 in both paths |
| 2026-09-05 | `330ef74a` | mouse mode speaks only to tracking programs | (c) unrelated |

The content really did overflow the arc: `PaneContentFrame`'s own header explains that "the first
and last column of the top and bottom rows sit under the arc, which is how a prompt that paints its
own background to the very edge came out clipped" (`PaneContentFrame.java:18-26`). The fix spends
the clearance as the child's margin, "because the frame's other child is the glass backdrop and it
must still reach the corners" (`:24-26`) — which is precisely why the band belongs to no view.
`project-docs/terminal-chrome/spec.md:41-45` audited the tap mapping ("Floor mapping stays
(correct)") and says nothing about the band; the inset is undocumented.

**The radius per mode** (`TerminalPaneController.java:3012-3015`):

```java
boolean glassShape = paneGlassActive();
float shapeRadiusPx = glassShape ? paneGlassRadiusPx()
    : (floating || split || mMaximizedLeaf != null) ? dp(FLOAT_CORNER_RADIUS_DP) : 0f;
```

`FLOAT_CORNER_RADIUS_DP = 6` is hardcoded (`:152`), so a non-glass float or split pane wears a fixed
6 dp and the Appearance editor's radius never reaches it. And the docked "0 radius" path is
unreachable while pane glass is on: `PaneGlass.radiusPx` falls back to `DEFAULT_RADIUS_DP = 10`
whenever the stored radius is 0 (`PaneGlass.java:19,29-31`), so a "0 radius" docked pane with glass
still pays 8 px of inset on every edge.

## Recommendations, ranked

1. **herdr side: widen the collapsed toggle to at least 3 cells (≈16 dp) and give it a whole row.**
   This is the only change that matches the evidence — the expanded button works because it is ≥5
   cells wide, and the taps scatter across 3 columns. Cheapest, and outside this repo. *Evidence:
   claim 4; the column distribution in Data.*
2. **Launcher side: let `PaneContentFrame` forward margin touches to its content.** It already knows
   the child and the inset; forwarding (not `TouchDelegate` — claim 5) would make the 8 px band
   behave everywhere the way it already behaves inside a corner square, where
   `forwardToTerminal` + `sendMouseEvent`'s clamp deliver the edge cell. Fixes tap 11 and the
   general defect; will *not* fix this button. *Evidence: claim 2.*
3. **Do not chase a mapping bug.** Claim 3 is verified on both buffers; the bottom-anchor and
   alternate-screen commits are self-consistent. *Evidence: claim 3 and the history table.*
4. **Worth a second look, not urgent:** `TextSelectionCursorController.java:254` consumes only the
   horizontal offset; and `PaneGlass.radiusPx` treating a stored 0 as "use 10 dp" means a user who
   sets the radius to zero still pays the clearance.
