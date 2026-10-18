# Keyboard touch engine (2026-10-16)

Status: decisions 1–13 built (27a6ceebb on `pre`), on pong since 2026-10-16. Round 2 below is
built too; it owes the pass bar on pong again.

The in-app keyboard learns where this user's thumbs actually land and moves key borders to match.
The first acceptance case is the developer's own: aiming at the space bar and landing on `b` or `v`
in the row above.

Read first: [`reference/keyboard/adaptive-touch-targets.md`](../reference/keyboard/adaptive-touch-targets.md)
(the research) and [`reference/launcher/keyboard-mis-input-correction.md`](../reference/launcher/keyboard-mis-input-correction.md)
(the study behind today's tap model). The terminal-aware letter prior in
[`reference/keyboard/terminal-aware-key-prior.md`](../reference/keyboard/terminal-aware-key-prior.md)
is parked and out of scope.

## Where it starts from

- `app/…/terminal/inappkeyboard/TapModel.java`: the mean landing offset per key, a border between
  two keys that both sides agree on (the mean of both keys' offsets), shrinkage at 20 taps, a 0.3
  cap, halving at 400 taps. It is off by default.
- `TapCorrectionController` (the `Keyboard2View.TapResolver`) and `TapModelStore` (the JSON file,
  keyed by layout id and geometry signature).
- `inapp-keyboard/…/TapGeometry.java:106` counts only `KeyValue.Kind.Char` keys. The space bar is
  `Kind.Editing`, so it never learns and its border never moves. **This is the developer's bug.**
- `Keyboard2View.onTouch` resolves the corrected key on finger-down (`Keyboard2View.java:1630`),
  so a swipe that starts in a moved border strip gets the neighbour's swipe legends.

## Words used here

| Word | Meaning |
|---|---|
| Raw key | The key whose rectangle contains the finger-down point. |
| Committed key | The key that is sent. It differs from the raw key only on a corrected tap. |
| Take-back | Backspace pressed k times (1–8) in a row, then k keys retyped, where the retyped keys match the erased ones except at exactly one place, and there the erased key X and the retyped key Y are grid neighbours. It means "meant Y, hit X". Round 2; before it, k was always 1. |
| Group | A set of keys that pool their offsets until each key has taps of its own. |
| Warm-up | The first 1,000 taps after the feature is switched on: it learns and counts but never corrects. |
| Take-back rate | Take-backs per 100 taps. It is both the metric and the drift alarm's input. |

## Decisions

1. **Where the code lives.** A pure package, `com.termux.app.terminal.inappkeyboard.touch`, with no
   Android types: the model, the groups, the take-back detector, warm-up and drift, the per-key
   log. A thin adapter (today's controller) owns the file, threads and Settings. The vendored
   keyboard keeps only its hook; every change there is recorded in `inapp-keyboard/UPSTREAM.md`.
2. **Which keys are corrected.** Character keys, space, Esc and Tab. Enter, Backspace, arrows and
   modifiers are never corrected into or out of. A press that the plain grid gives to an excluded
   key stays on it.
3. **When the key is chosen.** The raw key is chosen on finger-down and is what the pointer holds.
   Only a press that ends as a plain tap is corrected, on release. A swipe, long press or key
   repeat always uses the raw key, so user-configured swipe legends never change meaning.
4. **Highlight.** The pressed highlight shows the raw key. There is no extra flash when the
   committed key differs; the character typed is the feedback.
5. **The model.** The mean landing offset per key, as today, with the existing border rule, cap and
   shrinkage. On top of that:
   - Keys borrow their group's offset until they have their own taps (shrink towards the group,
     not towards zero).
   - Groups come from geometry, so they work on any layout: each row splits into left, middle and
     right thirds of the keyboard width, and any key two or more key widths wide is its own group.
   - The space bar, and any other key two or more widths wide, learns its vertical offset only;
     its horizontal offset is always zero.
   - The key centre always stays its own key (the 0.3 cap guarantees this; keep a test on it).
6. **What it learns from.** Landed taps count at weight 1, take-backs at weight 3, filed as a tap
   meant for Y at X's landing point. Each tap is held for one keystroke before it is filed, so a
   take-back never has to undo anything. Esc and Tab learn from landed taps only: Backspace does
   not undo a Tab completion or an Esc, so a take-back cannot be read after them.
7. **Recorded, not acted on.** Each tap's duration, contact size (`getTouchMajor`) and the gap from
   the previous tap are logged. Nothing acts on them until pong data shows they separate misses.
8. **Privacy.** The log is stored per key with no order: each key keeps a bounded bucket of recent
   positions, durations and sizes. The order of typing is never on disk, so text cannot be rebuilt.
   A take-back keeps only its X→Y pair and position. Everything stays on the device.
9. **Safeguards against pollution.** All of these:
   - Swipes, long presses and key repeats are ignored.
   - Presses with Ctrl or Alt held are ignored.
   - Paste and hardware-keyboard input are ignored.
   - A tap more than 0.5 key from the centre of its raw key is dropped as an outlier.
   - The model trains only on the raw key (and take-back labels), never on its own corrections.
   - Borders never move more than 0.3 of a key.
   - Old data fades out: halving at 400 taps, as today.
   - Each key accepts a capped number of observations per minute.
   - The drift alarm, below.
   - Editing a layout restarts that layout's learning.
10. **Warm-up and drift alarm.** After the switch is turned on, the first 1,000 taps learn and set
    the baseline take-back rate without correcting. After that, if the take-back rate over the last
    500 taps is 20% above the baseline, correction pauses itself and Settings says so. Resume
    restarts the warm-up; reset clears everything.
11. **Settings.** The existing switch and reset stay. One status line is added: learning, active or
    paused, and the number of taps learned. Product copy, short; no mechanism in it.
12. **Default.** Off until it passes on pong, then on by default in a pre-release drop.
13. **Migration.** The existing learned file is discarded once: changing which keys count changes
    every stored geometry signature. Bump the store version so the old file is dropped cleanly.

## Round 2: late fixes (settled 2026-10-16)

The developer often notices a miss only a few keys later and presses Backspace 3–5 times, back
into the previous word, before retyping. Round 1 missed this in two ways, and the file pulled
from pong after about two hours confirmed it:

- The warm-up saw 0 take-backs in 1,000 taps, and the corrected phase saw 15 in about 6,900.
  None were into space, so the pass bar had nothing to measure.
- Only one tap was held, so a miss noticed late had already been filed as a correct tap on the key
  that was hit. That pulls the border the wrong way.

The same file also showed the following:

- **Space taps land high.** The median space tap is 0.21 key widths above the centre. The learned
  border had risen only about 0.1 key width.
- **Two take-backs went the other way.** They were meant for `n` and hit space: watch for
  over-correction.
- **Contact size is real on pong.** `getTouchMajor` gives 24 distinct values, 0.22–0.36 key
  widths.
- **A resize started learning from zero.** The layout's key width changed from 106 px to 100 px,
  which gave it a new grid. The old grid kept the warm-up counts and the new one kept everything
  since, so the pass bar's two phases ended up in different grids.

Decisions:

14. **Never learn from erased taps (Gboard's rule).** Sivek & Riley learn only from text that
    survived. Hold the last 8 taps before filing them. A Backspace erases the newest held tap,
    and an erased tap is never filed as landed. Held taps that were not erased are filed when the
    text is final, or when they drop out of the 8:
    - Enter or another key that is never corrected, apart from Backspace.
    - A Ctrl or Alt chord.
    - Paste or a hardware key.
    - A gap of more than 2 s.
    - The keyboard going away.
15. **Retype match.** This is our own extension; no keyboard has published one. Take k
    consecutive Backspaces, 1 to 8, that erase held taps, followed by k plain taps:
    - **Match:** the retyped keys equal the erased ones except at exactly one place, where the
      erased key X and the retyped key Y are grid neighbours, both characters or space. That place
      is a take-back: X's landing point is filed as meant for Y, at weight 3.
    - **Erased correct taps:** the other erased taps are dropped, not filed.
    - **No match:** anything else (more than one difference, keys that are not neighbours, a
      longer or shorter retype, a gap over 2 s between keystrokes, other input in between) means
      editing, and all the erased taps are dropped.
    - **Not caught:** a held Backspace that auto-repeats, and doubled or skipped letters.
16. **The pass bar counts by both keys.** Take-backs are counted by the key meant and also by the
    key hit, so "missed into `b`, `v` or `n`, meant space" can be read straight from the file.
17. **A resize keeps what was learned.** Vertical offsets are stored in key heights and horizontal
    ones in key widths (Gboard's normalisation). A grid is identified by the layout and its key
    structure (rows, relative widths and roles), not by pixel size, so a resize or edge-gap change
    keeps both the model and the pass-bar counts. Editing the layout's keys still restarts it.
18. **Fresh start.** Bump the store version: everything learned so far is dropped, with the
    developer's agreement, so the warm-up and baseline are taken under the new take-back rule.

## Pass bar

On pong, over a few days of the developer's normal use: space take-backs (hit `b`, `v` or `n`, meant
space, under the round 2 rule) drop by at least 30% against the warm-up baseline, and the take-back rate
for other keys does not rise. Read it with `project-docs/verification/touch-engine-passbar.py --pong`;
its thresholds for "enough data" and "risen" are the working definition.

## Device checks owed

Recorded in `project-docs/device-checks.md` in the commit that lands the work:

- Developer: the pass bar on pong.
- Agent: a swipe starting in a moved border strip keeps its own key's swipe legends.
- Agent: a forced drift pause shows in Settings and resume restarts the warm-up.
- Agent: the status line reads correctly at 1.3× font scale.

## Out of scope

Autocorrect, suggestions, any language or letter prior, acting on duration, size or posture,
and syncing the model off the device.
