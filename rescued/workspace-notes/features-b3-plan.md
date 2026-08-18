# B-3 implementation plan — A-Z rope column + scrub highlight

Builds on landed, device-verified B-1 + B-2. Owns nothing from B-4..B-7.

## 0. Verified constraints

- Accessory geometry freeze confirmed (`TermuxActivity:8851`, `:5552`, flush `:9829`). B-3 adds **zero** calls
  on those paths — the column is a draw-only sibling in `AppDrawerContentView`, laid out once, animated in
  canvas space.
- Highlight uses **only** `View.setAlpha` / `setScaleX/Y`. Never `metrics.iconPx`, never a second
  `getRenderedIcon` call, so the shared byte-budgeted icon cache is untouched by construction.
- B-1's dock eligibility veto on `activeAzLetter` does **not** interact: that field is written only by the
  dock's own scrub. The drawer column must never write it, never call `previewAzLetter` /
  `refreshActiveAzCandidates` / `getAppsForLetter`. Pinned by an isolation test.
- **`LauncherAppDataProvider.getAllApps()` is NOT fully alphabetical.** `loadSnapshot()` sorts the primary
  user with `ResolveInfo.DisplayNameComparator` (`:216`), then `addProfileApps` **appends** work/clone
  entries (`:253`, `:389`). An A-Z index over that tail is a lie — B-3 sorts by label first (§4.1).
- **No new preference.** The deferred keys stay unclaimed; a "hide the column" toggle belongs to B-7.
- Baseline 1009 tests, both variants.

## 1. The third touch category

B-2's gate is one boolean: `ownsPoint` true = grid, false = chrome (plane's close drag claims any downward
drag). The column is neither — and a scrub IS a sustained downward drag, so as-is it would close the drawer
on the second letter.

**`AppDrawerPlaneView` is not modified at all.** The plane only needs defer-or-claim. The three-way split
resolves one level down:
- `ownsPoint` returns **TRUE for column points**; its contract widens from "the grid owns it" to "the content
  owns it and the plane must defer". Body becomes `AppDrawerTouchRegions.resolve(...) != CHROME`.
- New pure `AppDrawerTouchRegions.Region { GRID, COLUMN, CHROME }`, resolved once per stream at ACTION_DOWN in
  `AppDrawerContentView.dispatchTouchEvent` (`:486`) from the grid rect, the column rect, `mInteractive` and
  `columnActive`.

| Region at DOWN | Plane | Content | Stream owner |
|---|---|---|---|
| `GRID` | defers | `mDownOverGrid = true`, `mPolicy.begin(...)` | RecyclerView + arming policy (**B-2 unchanged**) |
| `COLUMN` | defers | `mDownOverGrid = false`, `mGestureActive = false`, `mPolicy.disarm()` | `AppDrawerRopeColumnView` |
| `CHROME` | arbiter armed | ignores | plane's B-1 close drag (**unchanged**) |

`onNestedPreScroll` (`:528`) is already gated on `if (!mDownOverGrid) return;`, so a column stream can never
report a close **with no change to that method**. The column also calls
`requestDisallowInterceptTouchEvent(true)` on DOWN as insurance.

### Distinguishing the cases
- **Close drag started on the column: does not exist, by design.** No test can separate it from a scrub —
  same motion, same speed, same place. A touch landing on the column is a scrub, full stop (the dock's
  `AzScrubRowView.onTouchEvent:427` already works this way). Close stays reachable on the pill, both side
  margins, the strip below the grid, the 64dp bottom band, the grid's armed second pull, and Back.
- **Grid scroll:** down point is `GRID`; the column never sees the stream. A scrub can never be mistaken for
  a scroll because the recycler never receives the stream — no cancel, no half-scroll.
- **Tap on a letter: same code path as a scrub.** The scrub is live from ACTION_DOWN, so a tap is a scrub
  that lasted 80ms. No timeout, no tap slop. **A tap jumps the grid to that section**; the highlight is on for
  the touch and fades on release, and the scroll position stays where the letter put it.
- **Horizontal drift:** X is read only at DOWN; afterwards only Y maps to a letter, clamped at both ends. A
  finger sliding onto the grid mid-scrub keeps scrubbing.

## 2. New classes — `com.termux.app.launcher.drawer`

| Class | Kind | Responsibility |
|---|---|---|
| `AppDrawerTouchRegions` | **pure** | `Region{GRID,COLUMN,CHROME}` + `resolve(x, y, Frame grid, Frame column, interactive, columnActive)`. Uses the house `Frame`, not `Rect`. |
| `AppDrawerRopeModel` | **pure** | Coupled-chain rope: per-letter offset + velocity, `advance(anchorPx, dt, reduced)`, `offsetPx(i)`, `tiltDeg(i)`, `isMoving()`. No time source of its own. |
| `AppDrawerRopeMetrics` | **pure** | Column width, track geometry, slot height, glyph size, `centerYForIndex`, `indexForY(y, prev)` with hysteresis, anchor/alpha ramps as functions of `p`. |
| `AppDrawerSectionIndex` | **pure** | `sortByLabel`, visible letter set in `AZ_ORDER` with `#` last, `firstPositionForLetter`, `letterOf`. |
| `AppDrawerScrubHighlight` | **pure** | `(entryLetter, activeLetter, strength) -> alpha, scale`. |
| `AppDrawerRopeColumnView` | `View` | Draws letters via the model + metrics, owns the scrub touch, reports letter changes. Owns **no** Choreographer. |

Modified: `AppDrawerContentView`, `AppDrawerController`, `AppDrawerAppsAdapter`, one additive getter on
`SuggestionBarView`. **Not modified:** `AppDrawerPlaneView`, `AppDrawerSearchController`,
`AppDrawerSearchModel`, `AppDrawerCloseArmingPolicy`, `AppDrawerGestureArbiter`, `AppDrawerGridMetrics`,
`AppDrawerTransitionGeometry`, `AppDrawerCommitPolicy`, `AppDrawerAccessoryChoreography`, `TermuxActivity`.

## 3. The rope

**State per letter `i`** (0 = top, anchored): lateral offset `x[i]` (positive = outward) and velocity `v[i]`.
**Rest is `x=0, v=0` for all i** — a straight vertical column on the rest line at the plane's right edge. That
is the unique fixed point, so "comes to rest on the right" is a property of the physics, not a hard-coded end.

**Propagation — a discrete damped wave on a chain**, not per-letter phase lag and not 27 independent springs:
```
a[i] = K*(x[i-1] - x[i]) + K*(x[i+1] - x[i]) - Kr*x[i] - C*v[i]
```
`x[-1] = anchorPx` (kinematic driver), free lower end (`x[N] == x[N-1]`). Semi-implicit Euler, substepped like
`Spring`: `steps = ceil(dt / (1/120))`, `dt` pre-clamped by `Spring.clampDelta`. At `K = 900` the stability
bound `K*h^2 + 2*C*h < 4` is `0.22` — an order of magnitude inside the margin. A disturbance at the anchor
propagates down the chain, reflects off the free end and decays: the visible per-letter lag is emergent.

**Driver — anchor is a pure function of progress:**
```
anchorPx(p) = ENTRY_OFFSET_PX * (1 - ramp(p, COLUMN_IN_START, COLUMN_IN_END))
```
The chain lags the anchor, so the tail is still travelling when the anchor stops, swings through, and damps
back. **Release velocity needs no injection anywhere** — the anchor's velocity IS the finger's, because
`anchorPx` is a function of `p`. Slow pull = gentle lean; hard flick = real whip; the settle spring's
overshoot gives one more swing free.

**Tilt** (what sells "rope" over "wobbling letters") is the local slope:
`tiltDeg(i) = clamp(TILT_DEG_PER_PX * (x[i] - x[i-1]), -TILT_MAX_DEG, TILT_MAX_DEG)`, applied as
`canvas.rotate` about each glyph centre. Part of the model's output, so it is unit-testable.

| Constant | Value | Why |
|---|---|---|
| `COUPLING_STIFFNESS K` | 900f | Propagation speed; head-to-tail delay ~120ms at 27 letters |
| `REST_STIFFNESS Kr` | 60f | Weak pull to the rest line; guarantees rest is the unique fixed point even for the free end |
| `DAMPING_RATIO` | 0.16f | Deliberately **under**damped — the one motion here that should oscillate |
| `DAMPING C` | `2*sqrt(K)*ratio ≈ 9.6f` | Derived, not typed, so changing K cannot silently change the character |
| `ENTRY_OFFSET_DP` | 26f | Bigger reads as a slap; smaller is invisible at the head |
| `COLUMN_IN_START` | 0.34f | Arrives after the content fade begins (`CONTENT_FADE_START = 0.22`) — last thing to appear |
| `COLUMN_IN_END` | 0.86f | Anchor home before p=1, so the tail is **still settling** when the plane finishes |
| `TILT_DEG_PER_PX` | 0.55f | |
| `TILT_MAX_DEG` | 14f | Beyond this glyphs read as broken |
| `MAX_LETTERS` | 27 | A-Z + `#`; fixed arrays, zero per-frame allocation |
| `SETTLE_EPSILON` | `Spring.SETTLE_EPSILON` | Reused — "settled" means one thing in this app |

**Driven from the existing loop.** `AppDrawerController.doFrame` (`:457`) is the only time source; add
`mContent.advanceDrawerFx(p, dt, reducedMotion)` beside the two spring ticks and OR its result into the
re-kick. The content forwards to the column (rope) and the highlight spring. Rides the controller's loop
rather than the content's overpull loop for the same reason `mReveal` does — the growing rectangle and the
letters inside it are one surface, and two Choreographer callbacks render them a frame apart.

**Two traps this creates, both handled in the same commit:**
1. **`doFrame` teardown must be gated on `!mDragging`.** Today the loop never runs during a drag, so
   `if (!mOpen && mProgress.value < CLOSED_EPSILON) { ... onClosed(); }` (`:471`) is safe. The rope needs
   frames *during* the opening drag, where `mOpen` is false and `p` is near zero — which would tear the
   drawer down under the user's finger. **Single most dangerous line in the slice.**
2. **`beginDrag`/`updateDrag` must `kick()`**, and the content needs `setFrameRequestListener(this::requestFrames)`
   (mirroring the existing `setRevealListener` seam) so a scrub on a settled drawer can restart the loop.

**Reduce motion:** `advance(anchor, dt, reduced=true)` collapses to rest in one call — all `x=0`, `v=0`,
return false — rather than snapping to the anchor. Mirrors `Spring.tick(reduced, ...)`.

**On close: yes, by construction.** `p` runs 1 -> 0, the anchor travels back outward, the chain trails. No
close-specific spec. Column alpha is 0 below `COLUMN_IN_START`, so the last third is a rope nobody sees;
`onClosed()` resets the model so a re-open never starts from a stale chain.

## 4. Grid side

**4.1 Sort first.** `pushCatalogue()` (`:283`) becomes
`search.setCatalogue(AppDrawerSectionIndex.sortByLabel(provider.getAllApps()))`. Stable,
`compareToIgnoreCase`. This makes every letter a **contiguous run**, so `firstPositionForLetter` is a single
number and highlighted cells are always adjacent. `AppDrawerSearchController` is untouched — the empty query
returns its input verbatim and the ranked path already tiebreaks by label.

**4.2 Section index.** `build(...)` does one pass using the already-public
`LauncherAppDataProvider.normalizeLetter` (`:426`); yields the visible letter set (letters with >= 1 app),
mirroring `AzScrubRowView.setVisibleLetters` (`:252`). Rebuilt in `applyResults` (`:352`), once per submitted
list. **`LauncherUsageStatsStore.rankForAz` is deliberately NOT used** — it reorders a bucket by launch count,
which would break the contiguity the grid index depends on. Note as a deviation in the commit message.

## 5. Scrub highlight

200-400 cells, ~24-36 attached. A `notifyDataSetChanged()` per frame would rebind everything 60x/sec.
**Two write paths that must agree:**
1. **Per frame:** walk `mGrid.getChildCount()` (attached only), write `setAlpha` / `setScaleX/Y`. Letter comes
   from `getChildAdapterPosition` + the adapter's cached `char[]`. O(visible), no notification, no allocation.
2. **At bind time:** `onBindViewHolder` ends by applying the current `(activeLetter, strength)` set by
   `setScrubState(...)`, which explicitly **does not notify**. Not optional — the auto-scroll binds fresh cells
   continuously, and without it every new cell flashes at full opacity for one frame.

Plus `onViewRecycled` (`:177`) resets alpha/scale to 1 — a holder returned to the pool at 0.28 and reused
after the scrub is a permanently dim cell.

| | alpha | scale |
|---|---|---|
| matching | 1f | `lerp(1, 1.06, strength)` |
| non-matching | `lerp(1, 0.28, strength)` | 1f |
| no scrub | 1f | 1f — byte-identical to B-2 |

**Auto-scroll: yes**, on every letter change (not per frame):
`scrollToPositionWithOffset(firstPositionForLetter(letter), 0)` — not `smoothScrollToPosition`, which would
fight a moving finger. Fires `onScrolled` -> the existing `dismissContextPopups()`. The column DOWN already
disarmed the close policy, so the post-scrub grid cannot close the drawer on the next pull.

**Release:** clear the letter, spring `strength` 1 -> 0 with the house `Spring(1f, 420f, 41f)` (~260ms) in the
same `advanceDrawerFx`. **Scroll position is kept.** At strength 0 every cell is byte-identical to B-2.

**Non-empty query: the column deactivates.** A ranked list is ordered by match quality, so its letters are not
contiguous and its index would be meaningless. `columnActive = !hasQuery() && letterCount >= 2`; when inactive
the column fades out, stops drawing, and its strip resolves to `CHROME` so B-1's close drag works there.
`applyResults` clears any in-flight scrub as it already clears popups and long-presses.

## 6. Ordered steps (strictly linear 1 -> 6)

1. **Pure classes + tests first**: `AppDrawerTouchRegions`, `AppDrawerRopeModel`, `AppDrawerRopeMetrics`,
   `AppDrawerSectionIndex`, `AppDrawerScrubHighlight`.
2. **`SuggestionBarView`**: one additive `public boolean isRowHapticsEnabled()` beside `setRowHapticsEnabled`
   (`:2330`), so the column's letter tick honours the same pref as the dock. Nothing else changes.
3. **`AppDrawerRopeColumnView`**: built programmatically; fill + dark outline paints copying
   `AzScrubRowView`'s legibility recipe (`:222-238`) since it sits over glass; base colour
   `dock.getLauncherTextColor()`, focus colour from `MaterialColors` resolved as `prepareOverlay` (`:708`)
   already does; `hasOverlappingRendering()` false. `onDraw` rotates each glyph by `tiltDeg(i)` about its
   centre. `onTouchEvent` claims DOWN, resolves `indexForY` with `LETTER_SLOT_HYSTERESIS_RATIO = 0.22f` (the
   dock's own value, `AzScrubRowView:100`), fires `RowHapticTickHelper.isBoundaryCrossing` ->
   `CLOCK_TICK` gated on the new getter.
4. **`AppDrawerContentView`**: column as a `Gravity.END` child, grid gets `rightMargin = columnWidthPx`; widen
   `ownsPoint`/`ownsLocalPoint` (`:426`, `:432`); resolve the region at DOWN (`:486`) and take the COLUMN
   branch; implement the column callbacks; add `advanceDrawerFx`, `setFrameRequestListener`, `isScrubbing()`;
   rebuild the index and `columnActive` in `applyResults` (`:352`); clear the scrub in `setInteractive(false)`
   (`:223`).
5. **`AppDrawerAppsAdapter`**: cache a `char[]` parallel to `mEntries` in `submit` (`:64`); `setScrubState`
   that does not notify; apply the highlight at the end of `onBindViewHolder` (`:131`); reset in
   `onViewRecycled` (`:177`).
6. **`AppDrawerController`**: `advanceDrawerFx` in `doFrame` + the `fxMoving` re-kick term + **the
   `!mDragging` teardown guard**; `kick()` in `beginDrag` (`:227`) / `updateDrag` (`:254`);
   `setFrameRequestListener` in `buildContent` (`:547`); resolve rope metrics in `prepareContent` (`:727`) and
   pass `openRect.width() - columnWidthPx` to `AppDrawerGridMetrics.resolve`; reset rope + scrub in
   `onClosed` (`:936`) inside the existing `try`; new `public void requestFrames()`.

## 7. Tests (baseline 1009 both variants, target ~1053)

Pure: `AppDrawerTouchRegionsTest` (~7) · `AppDrawerRopeModelTest` (~9: rest is the fixed point; letter 0 moves
before letter 26 — **the lag property**; monotonic decay once the anchor stops; settles inside 1.2s at 60fps;
reduced snaps to rest in one call; tilt bounded; a 1/30s frame does not diverge; NaN/Inf absorbed; no
per-advance allocation) · `AppDrawerRopeMetricsTest` (~6) · `AppDrawerSectionIndexTest` (~7, incl. a
profile-tail catalogue coming out fully alphabetical) · `AppDrawerScrubHighlightTest` (~5, incl. the
strength-0 B-2 identity).

Robolectric: `AppDrawerRopeColumnViewTest` (~6) · `AppDrawerContentColumnTest` (~8, headline: a DOWN on the
column produces **no** `onContentCloseDragBegin` for the whole stream; a cell bound during a scrub arrives
already dimmed; release restores every attached child) · `AppDrawerControllerRopeTest` (~4, incl. the
`!mDragging` guard) · `SuggestionBarDrawerAzIsolationTest` (~2, `activeAzLetter` still null after a full
in-drawer scrub) · `AppDrawerContentViewTest` extended (~2, B-2 arbitration byte-identical with the column
present; column count computed from width minus the column).

## 8. Device verification

> **Drawer state is confirmed by screenshot, never by focus.** `mCurrentFocus` reads `com.termux` either way —
> during the B-2 pass a blind tap after a focus check launched a random app. Every tap must follow a capture
> showing the drawer open and where the target actually is. Where captures are expensive (HTC), hold the
> pointer with `input motionevent DOWN` and inspect before releasing.

**Nothing Phone `127.0.0.1:5555`** (1080x2412, rounded, full catalogue, fast capture) — the primary rig;
capture freely. 1) B-1/B-2 regression sweep. 2) Rope on a slow open: captures at p≈0.4/0.7/0.95 — column absent
below ~1/3 travel, enters from the right, **bottom letters visibly lag the top**, still settling in the last.
3) Rope on a fast flick: captures right after release and ~150ms later — the tail must be on opposite sides of
the rest line. 4) Rope on close: trails outward, gone before the plane. 5) Scrub A -> #: letter under the finger
enlarged and accented, grid jumped so its run starts at the top, matches bright and the rest visibly
transparent, **no flash of undimmed cells**, drawer never closes. 6) Tap a letter read off a capture.
7) Scrub with a query: column gone, its strip closes instead; clear query, column back. 8) Chrome close on
pill / left margin / bottom band. 9) B-2 arbitration (a)-(f) unchanged. 10) Scrub then pull the grid -> scrolls,
never closes. 11) Long-press during and after a scrub. 12) `gfxinfo framestats` over ten open/scrub/close
cycles vs a B-2 control. 13) `meminfo` before/after a full A -> # scrub — the strongest test the shared icon
budget has had. 14) `animator_duration_scale 0` -> straight column, instant dim. 15) Rotation / HOME / Back,
terminal touchable after.

**HTC 10 `HT66PBN06539`** (1440x2560, default dock, ~6.5s capture) — **four captures**, default-style path and
1440p letter metrics only. 1) Regression sweep, no captures. 2) Held-pointer open, **[1]** at p≈0.7 — rope
enters and lags; 27 letters legible over a ~2100px track (the only real test of the metrics clamps).
3) Held-pointer scrub A -> M -> #, **[2]** at M. 4) Release, **[3]** — grid at `#`, full opacity, drawer open.
5) Chrome close, reopen, grid arbitration (a)/(c). 6) **[4]** after tapping a letter read off capture 3 —
never a blind tap. 7) `gfxinfo` vs a B-2 control on the same device (baseline ~61% janky at 1440p; never
compare to 16.6ms absolute). 8) `animator_duration_scale 0`. 9) Terminal alive after ten cycles.

## 9. Risks

1. **`doFrame` tearing the drawer down mid-drag** — kicking the loop during a drag makes the
   `!mOpen && p < CLOSED_EPSILON` teardown reachable at the start of every open. Presents as "the drawer
   sometimes refuses to open". Guard `!mDragging`; pin with a test.
2. **A scrub closing the drawer** — the failure the third category exists to prevent.
3. **`mGestureActive` stuck true after a column stream** — `onStopNestedScroll` never arrives for a stream the
   recycler never saw; a later spurious stop spends an arming. Presents as "the second pull sometimes doesn't close".
4. **Cells left dim after a scrub** — three write paths (per-frame walk, bind rule, recycle reset); miss one
   and a 0.28 alpha leaks into the pool. Silent until close+reopen.
5. **Flash of undimmed cells during auto-scroll** if the bind-time rule is omitted.
6. **Icon cache pressure from auto-scroll** — an A -> # scrub touches the whole catalogue in ~1s, making the
   existing B-2 dock-rerender-hitch risk much easier to hit. Measured on device.
7. **Column width and B-4/B-5** — the horizontal and category views must subtract it too or cells sit under
   the letters.
8. **Profile apps move position** — the sort relocates work/clone entries from the tail into the body. Correct,
   but visible; belongs in the commit message.
9. **Rope divergence on a dropped frame** — a diverged chain never reports settled and holds the Choreographer
   loop open forever, draining battery on an idle open drawer. Substepping + `clampDelta` + NaN/Inf absorb.
10. **Haptics per frame instead of per boundary** — `RowHapticTickHelper.isBoundaryCrossing` is the guard.
11. **Two A-Z surfaces now exist** (dock row + drawer column) and must never be confused; the isolation test
    keeps them apart.
12. **Per-frame allocation during a long scrub** — `applyFrame` allocates a `Frame` per call and the controller
    loop now runs for the length of a scrub, not just a transition.
