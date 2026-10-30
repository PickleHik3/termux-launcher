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

An inventory of today's two-phase numbers, each with its owner(s) and whether a parity test
exists. Known pairs: the terminal's pre-size room vs the settle's resting room (fixed in this
round); the keyboard reveal room at pre-roll vs at rest; the dock lift under the keyboard; the
still's size vs the live view; the frame tint at the crossing vs at rest. Each pair with two owners
becomes one ticket: move the number into one policy, add the parity test.

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

The emulator sweep (`project-docs/verification/`) greps the log for `INVARIANT` and fails on any.

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
| M4 | the layout fixtures, the parity tests parametrised over them | M1 |

## Owed

- Pong: Motion log on, one Home ⇄ Terminal round trip, `launcherctl motion-log dump` shows the
  events and no `INVARIANT` line.
