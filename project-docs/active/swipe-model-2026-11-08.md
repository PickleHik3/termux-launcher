# Swipe model: the chrome stays put, pages carry their own (2026-11-08)

Decision: [ADR 0023](../../docs/adr/0023-the-chrome-stays-put-pages-carry-their-own.md).
Base: `perf/swipe-model` (rounds 17–22: glass slots, keyboard palette, frost, landing dock dress,
global Margin, toolbar warm-up, pane-tap bar refresh). Nothing here reaches `v2-redesign` until
the developer says so.

## Where we start (pong, round 22, Home2 ↔ Workspace, 10 swipes)

| Stall | When | Cost | Made of |
|---|---|---|---|
| Drag start | 40–70 ms into a swipe | one 37–51 ms frame | Wall.dragTo 9–18 ms, Chrome.applyChromeSpec 12–20 ms, traversal + render sync 10–29 ms |
| Landing | ~170 ms in | one 30–46 ms frame | Settle.placeState 17.6 ms (Place.syncLayout ~12, Bar.refresh ~6, Place.hosts ~5) |
| Drawing | every frame | flush layers 2.4–3.2 ms | ~10 screen-sized glass textures re-uploaded each swipe, ~0.7 ms each |

## Waves

Each wave is one branch off `perf/swipe-model` (or off the wave it needs), one worker, one round
on pong. Workers run only the tests they touch; the main session runs the full suite per round.

| Wave | What | Needs | Runs alongside |
|---|---|---|---|
| 1 | Kept glass with the RAM-tier budget and farthest-first eviction (ADR 0023 §5) | base | 2a |
| 2a | Fold the shared values into Base: status bar edge and state, dock design / search field / drawer tile, dock height; editor shows them on Global only (§1) | base | 1 |
| 2b | Page-owned elements: Home's apps row, A–Z bar, widget grid, search field and the Workspace's extra keys, minimal tab move into their page and slide with it (§2) | 2a | 4 |
| 3 | Swipe moves prepared layers: chrome look prepared on finger-down, drag frames translate and crossfade only; per-page look overrides crossfade (§3, §4) | 2b | — |
| 4 | Opt-in lighter glass while moving, Settings → Performance (§6) | 1 | 2b |

Parallel builds: waves on disjoint code (1 is the glass module: `LiveBackdrop`,
`BackdropLayers`, `GroundStripView`; 2a is preferences and editor cards) run as two workers and
two Gradle builds at once. Pong benches one APK at a time, so their rounds are measured back to
back, then combined into one round build.

## Measuring

Three runs of `~/tl-bench/set.sh` per build, from Home1, traced pairs Home2 ↔ Workspace and
Workspace ↔ AI; compare the averages of: missed-vsync share in swipe windows, pauses > 24 ms,
worst pause, Settle.placeState, the drag-start frame, flush layers and Texture upload count.
Queries live with the main session (`gaps`, `gapcause`, `kids`, `rt`).

Expected after each wave:
- 1: Texture upload count per swipe near zero after the first swipe; flush layers down.
- 2a: Settle.dockDesign / Bar.refresh at landing gone when only shared values differed.
- 2b: Settle.arrangement near zero; no extra-keys host rebuild or A–Z install at landing.
- 3: no Chrome.applyChromeSpec on drag frames after the first; drag-start frame < 16 ms.
- 4: with the switch on, flush layers mid-swipe down; with it off, identical to wave 1.

## Rules every wave keeps

- The terminal draws live through every swipe; no snapshot, no frozen pane.
- Widgets on pages off screen keep updating; nothing waits for landing.
- Memory: kept glass inside its tier; page-owned views hold no bitmaps.
- Hit every surface: Docked and Floating, keyboard up and down, one pane and many, themes and
  scheme chrome, density and font scale, the Assistant and Display pages (Display still leaves
  only by its top and bottom edges).
- Migrations run once, take Home's value, and are covered by a test.

## Pong checks owed (added to device-checks.md by each wave)

- Wave 1: swipe back and forth 20 times; glass matches at rest; open a heavy app and come back
  (eviction), the first swipe rebuilds without a blank frame.
- Wave 2a: status bar edge, state and dock look the same on every page; the editor shows them on
  Global only.
- Wave 2b: A–Z bar and apps row slide with Home; extra keys slide with the Workspace; nothing
  jumps at landing; keyboard up and down.
- Wave 3: a darker Workspace wallpaper dim crossfades during the swipe; the clock ticks
  mid-swipe; the terminal keeps drawing.
- Wave 4: switch on, glass softer only while moving and sharp at rest; switch off, unchanged.
