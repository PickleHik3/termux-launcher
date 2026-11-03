# Motion invariants: one owner per number, and a log that says so

Added to the 2026-10-30 round after the Terminal landing stutter. Its cause was a number owned twice:
the crossing pre-sized the terminal from one formula (`travelRoomChangePx`, 670 px on pong) and the
landing laid it out from another (the accessory geometry pass, 96 px more for the dock rows under
the keyboard), so the grid resized twice per crossing and the second reflow was masked by the
terminal's reflow frost. Finding it took a hand-written tagged log on the developer's phone. The
same shape appeared twice more the same day: three storage mechanisms for one look value (ADR
0017), and one audio-focus request for every kind of stream (the short-sound grace).

The architecture (pure policies, dumb views, holds, stills, the reflow frost) stays. This wave adds
the enforcement and the visibility it lacked.

## 1. The rule

A number that one phase predicts, pre-rolls or pre-sizes and a later phase settles comes from one
pure policy, and the two phases share a parity test. Written into AGENTS.md ("Coding style").

## 2. The audit (once)

The inventory (scouted 2026-10-30 at `0f2cf4a04`; "two" = two formulas that can diverge), ranked
by how likely a layout (floating keyboard, dock rows under the keyboard, status bar at the bottom,
split panes, Docked vs Floating) makes the two sides disagree:

| # | Number | Predicting side | Settling side | Owners | Parity test |
|---|---|---|---|---|---|
| 1 | Terminal pre-size room | `TermuxActivity` :23714 `travelRoomChangePx` :24365 via `travelRestReservationPx` / `chromeRestOf` | `runAccessoryGeometryPass` :12479 → `StackLayout.layout` (:154 `underKeyboardTopMarginPx`), `applyUnderKeyboardBandAir` :17136 | two | none (the landing fix of this round unifies it) |
| 2 | Dock lift / flush absorption under the keyboard | `PlaceChromeTravel` :385-386, `travelDockLaidOutPx` ~:23727 | `StackLayout` :154, `DockGeometry` :409-412 | two | `UnderKeyboardBandTest`, `DockRowsSeamTest` (rest only) |
| 3 | Terminal resize prediction | `TerminalView` :3290-3294 `predictResize*` :3382-3397 | `updateSize` :3177-3186 | two | `TravelDisplacementTest` (prediction math only) |
| 4 | Keyboard reveal at pre-roll vs rest | `Crossing.preRollKeyboard` :445 (`hold(reservationPx)`) | `PlaceChromeTravel` :377-378 rest `keyboardReveal()` | two | `PlaceChromeTravelTest` :177 (pre-roll asked, no rest parity) |
| 5 | Status bar reveal and position | `PlaceChromeTravel` :378 `statusReveal` | `PlaceChromeTravel` :385 rest | one class, two states | `PlaceChromeTravelTest` :430 (fade only) |
| 6 | Still size vs live view | `PageStillPolicy.pictureSide` :63 in `PaneWallLayout` :882-904 | the live view's bounds | one formula, compared to wall dims not live bounds | `PageStillsTest` :151 |
| 7 | Frame tint in a crossing vs at rest | `GlassLook` :219-220, `ChromeRenderer` :85-90 | `GlassVibrancy` :58-59 | one (`GlassFrameTint.of`), two callers | `GlassFrameTintTest` (no crossing-vs-rest case) |
| 8 | Held overlap | `PlaceChromeTravel` :438, :452 | `StackLayout` :180 | one formula, two input sources | `PlaceChromeTravelTest` :221, :238 |

Pairs 1–4 become one ticket each (one policy both phases read, a parity test over the layout
fixtures of §5); 5–8 get a parity case in their existing test.

## 3. Motion log (a product switch)

Settings › Debugging › **Motion log**, off by default. While on, the launcher writes one line per
event under tag `TermuxMotion` and into a ring buffer of the last 2,000 lines, with uptime ms:
crossing begin and land, every PTY resize (the existing `TermGeom` line joins it), a page still
shown or dropped, a hold taken or released, a reflow frost started, a keyboard pre-roll, a
pre-size. `launcherctl motion-log dump` prints the buffer; `launcherctl motion-log clear` empties
it. Zero cost at rest when off (one boolean check per event; no allocation).

Copy: title "Motion log", description "Records page changes and resizes for bug reports."

## 4. Invariants (staging and debug builds)

Checked after every crossing settles, reported under the same tag as `INVARIANT <name>` and
counted; never thrown:

- `one-resize-per-crossing`: no `TermGeom` resize after the settle of the crossing that pre-sized.
- `no-still-after-live`: a page that was live during the slide does not show its still after landing.
- `no-frost-at-landing`: the reflow frost does not start within 500 ms after a settle.

The emulator sweep greps the log for `INVARIANT` and fails on any: delivered as `MotionInvariants` (fed by `MotionLog.record`, counted in `launcherctl motion-log dump` as `invariants`) and `project-docs/verification/motion-invariants.sh <serial>`. A page "was live" when it logged `still-dropped` during the slide and no `still-kept` after it.

## 5. Layout fixtures

`app/src/test/.../place/LayoutFixtures.java`: five real layouts the geometry policy tests run
through: pong's (dock rows under the keyboard, extra keys above it, status bar top), the emulator's
(tl16, nothing under the keyboard), a floating keyboard, the status bar at the bottom, a split pane.
Every test of a two-phase number parametrises over them.

## Waves

| Step | Delivers | After |
|---|---|---|
| M0 | this spec; the AGENTS.md rule | now |
| M1 | the audit as a table in this file; one ticket per pair with two owners | the scout's report |
| M2 | the motion log switch, tag, ring buffer, launcherctl commands | the landing fix merges (same files) |
| M3 | the three invariants and the sweep grep | M2 |
| M4 | the layout fixtures, the parity tests parametrised over them: delivered 025da0020; pairs 1, 2, 4–8 agree on all five fixtures, pair 3 diverges under a text top inset → #190 | M1 |

## Owed

- Pong: Motion log on, one Home ⇄ Terminal round trip, `launcherctl motion-log dump` shows the
  events and no `INVARIANT` line.
