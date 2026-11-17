# Motion invariants: one owner per number, and a log that says so

> Note (2026-11-17): section 2 is rewritten against [ADR 0030](../../docs/adr/0030-pages-sides-and-the-bottom-stack.md) in step 3 of [the window restructure](window-restructure-2026-11-17.md).

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

## 2. The pairs, against ADR 0030 (rewritten 2026-11-17, window restructure step 3)

[ADR 0030](../../docs/adr/0030-pages-sides-and-the-bottom-stack.md) section 5 gives every keyboard
and room number one owner: the stack's scene (`place/StackScene`, written to views only by
`StackRenderer` as the frame table's STACK step). The 2026-10-30 audit's line references are gone;
names are used instead.

| # | Number | Owner under ADR 0030 | Where it stands (2026-11-17) | Parity test |
|---|---|---|---|---|
| 1 | The page's room (was: the terminal's pre-size) | the stack's scene: each page sized for its own rest; a crossing resizes nothing | done (D1-A step 3): `terminal_content_column` fills the window; the wall's cells end `PageRoom.bottomInsetPx` of their own page's rest above its bottom (`PaneWallLayout.setPageBottomInsetPx`, written by the geometry pass at rest, never in a crossing); the crossing's pre-size (`Crossing.preSizeTerminal`), its pause of size updates and its resume resize are gone, and a return (resume, focus) re-measures only if `PageRoom.resizesOnReturn` says the rest changed. The pause itself stays as the host surface's hold (divider drags, overview preview, return hold, the keyboard swipe's travel) | `PageRoomTest`, `PaneWallLayoutTest`, `MotionInvariantsTest` |
| 2 | Dock lift and flush absorption under the keyboard | the stack's scene | at rest `StackLayout` (flush padding, IME lift into its pass); in a crossing `PlaceChromeTravel` reveals on the seam's curve feeding `StackScene.travel` | `StackSceneParityTest`, `UnderKeyboardBandTest`, `DockRowsSeamTest` |
| 3 | The terminal's resize prediction | goes with pair 1: nothing to predict once a crossing never resizes | unchanged (`TerminalView.predictResize*` vs `updateSize`) | `TravelDisplacementTest` |
| 4 | The keyboard in a crossing | the stack's scene: its reveal is one function of the seam's progress on the look's curve (`StackScene.materialShare`); held halfway it holds halfway, reversed it reverses, progress 1 is the arriving rest | done for the reveal (`PlaceChromeTravel`, no landing margin, no early hide, no `ChromeSettle` clock). The pre-roll (the keyboard laid out below the screen for a slide) stays until the room decision | `KeyboardFollowsSeamTest`, `PlaceChromeTravelTest`, `KeyboardPreRollParityTest` |
| 5 | The status bar's reveal | the crossing's frame, on the same curve as pair 4 | done | `PlaceChromeTravelTest`, `KeyboardFollowsSeamTest` |
| 6 | A still's size vs the live view | `PageStillPolicy` (not a keyboard number) | unchanged | `PageStillsTest` |
| 7 | Frame tint in a crossing vs at rest | `GlassFrameTint.of` (not a keyboard number) | unchanged | `GlassFrameTintTest` |

D1-A step 1 (wave 2): the docked in-app keyboard is never GONE while it is on; down is the stack's translation, `StackScene.keyboardTranslationPx` = `(1 - reveal) * laidOutHeight`, read by the rest (`StackRenderer` adds it for a laid-out, down keyboard) and by the crossing (`StackScene.travel`), so the crossing's end is the landing's rest and a landing writes nothing for the keyboard. The container is the keyboard taller while it is down (`StackScene.containerHeightPx`); no page's room reads that height, each page's room is its own rest's (step 3). Pinned by `KeyboardLaidOutParityTest`, `StackRendererTest`, `KeyboardGeometryChoreographerTest`.
| 8 | Held overlap | goes with pair 1 | unchanged (`PlaceChromeTravel.heldOverlapPx` vs `StackLayout`) | `PlaceChromeTravelTest` |

The guards of [ADR 0028](../../docs/adr/0028-one-frame-pipeline-a-view-property-has-one-writer.md)
(foreign writer, landing wrote, crossing laid out) replace hand-checked pairs for anything the
stack's writer owns; "one resize per crossing" is now `zero-resize-per-crossing` (pair 1 landed
in D1-A step 3).

## 3. Motion log (a product switch)

Settings › Debugging › **Motion log**, off by default. While on, the launcher writes one line per
event under tag `TermuxMotion` and into a ring buffer of the last 2,000 lines, with uptime ms:
crossing begin and land, every PTY resize (the existing `TermGeom` line joins it), a page still
shown or dropped, a hold taken or released, a reflow frost started, a keyboard pre-roll. `launcherctl motion-log dump` prints the buffer; `launcherctl motion-log clear` empties
it. Zero cost at rest when off (one boolean check per event; no allocation).
While the wall is off its rest, each position pass adds one `page-pose` line per page on screen: `kind page effect slide progress tx scale alpha rotY`, plus `still` for a surface page.

Copy: title "Motion log", description "Records page changes and resizes for bug reports."

## 4. Invariants (staging and debug builds)

Checked after every crossing settles, reported under the same tag as `INVARIANT <name>` and
counted; never thrown:

- `zero-resize-per-crossing`: no `TermGeom` resize from a crossing's begin to 1 s after its settle. Each page's room is its own rest's (`PageRoom`), written at rest and never in a crossing, so nothing a crossing does resizes a pane; a keyboard that is shown or hidden at rest still resizes the Terminal, because its rest changed.
- `no-still-after-live`: a page that was live during the slide does not show its still after landing.
- `no-frost-at-landing`: the reflow frost does not start within 500 ms after a settle.

Two counters in the window's counter table (`window/CrossingWindow`, shown after the ring by `launcherctl motion-log dump`) make "a crossing never resizes the terminal and never redraws it stutteringly" checkable. A crossing is open from `crossing-begin` until the landing's settle has run (a window nobody closed shuts itself after 5 s):

- `crossing-resized`: one per terminal size change (every `pty-resize`) while a crossing is open; detail `pane from to cause`. The sweep expects zero.
- `crossing-redraw-slow`: one per terminal draw over 8 ms while a crossing is open; detail `pane ms`. Each landing also writes one ring line `crossing-draws draws=<n> maxMs=<ms>` (ring only, no counter).

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
