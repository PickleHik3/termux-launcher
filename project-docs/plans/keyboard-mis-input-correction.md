# Correcting mis-taps on the in-app keyboard — feasibility

Status: stage 1 implemented 2026-09-01 (`TapModel`, `TapModelStore`, `TapCorrectionController`
in `app/.../terminal/inappkeyboard/`, hook in `Keyboard2View.TapResolver`; setting "Learn where
you tap", off by default, with "Forget learned taps"). See "Implementation notes" at the end for
where the build departed from this study. Written 2026-08-31 at the developer's request: "add
something like the Gboard or iOS keyboard has, where the keyboard fixes users' slight mis-inputs",
noting that the in-app keyboard has no autocorrect and no dictionary, and asking whether a heatmap
over the user's own typing history could get there instead.

The module's own design record is
[`../inapp-keyboard-design.md`](../inapp-keyboard-design.md); its deviations from the vendored
upstream snapshot are in [`../../inapp-keyboard/UPSTREAM.md`](../../inapp-keyboard/UPSTREAM.md).

## The short answer

The Gboard/iOS feature is two mechanisms wearing one name, and only one of them can exist here.

- **Post-hoc word correction** — you finish a word, the keyboard replaces it with a dictionary
  word. **Not possible in the terminal, and not a matter of effort.** See "Why the word half is
  closed" below.
- **Pre-commit tap correction** — the keyboard decides *which key you meant* before the key is
  committed, by shifting the boundary between adjacent keys towards where you actually land.
  **Possible, and it is the half that needs no dictionary.** It is exactly the "heatmap over
  historic typing data" idea, and it fixes most of what a user perceives as autocorrect on a phone
  keyboard: the neighbour-key press.

So the recommendation is: build the tap model, do not build a dictionary. Everything below is about
the tap model.

## Why the word half is closed

The in-app keyboard is not an input method. It is an ordinary `View` embedded in the launcher
(`inapp-keyboard/`, `TermuxInAppKeyboard`), and a key press ends in
`TerminalKeyEventHandler.dispatch` writing bytes to the PTY. There is no `InputConnection`, no
composing region, and no editable buffer to revise — the deliberate removals in
`inapp-keyboard/UPSTREAM.md` (dictionaries, `cdict`, suggestions/candidates) were removals of
machinery that had nothing to attach to.

Rewriting an already-sent word would mean sending backspaces, and a terminal is not a text field:

- The byte is gone the moment it is written. `ls` may have already run; a `y` may have answered a
  prompt; readline may have completed something; a TUI may have taken it as a command.
- What a backspace *does* depends on what is reading: readline erases a char, vim leaves insert
  mode context intact but not the same way, `less` scrolls, a password prompt echoes nothing at
  all and there is no way to know one is on screen.
- A correction that guesses wrong in a chat app costs a giggle. Here it edits a command line.

This is not a thing to work around with heuristics. Word-level correction is out.

The one exception worth noting: the same keyboard also drives real text fields inside the launcher
through `TerminalKeyEventHandler.KeyValueInterceptor` — the command palette, the terminal sheet,
folder rename. Those *do* have an editable buffer and could in principle carry suggestions. They
are also short, mostly names and paths, and are the least of the typing the developer does. Not
worth a dictionary either; noted so a later reader does not think it was missed.

## What the tap model does instead

Every key press starts as a point. `Keyboard2View.getKeyAtPosition(tx, ty)` walks rows, then keys
within the row, and returns whichever rectangle contains the point — a hard, static grid. A press
2 px past the boundary is the neighbour, always, for everyone, forever.

Real thumbs do not land on key centres. They land systematically off-centre, in a direction that
depends on the key's position (reach), on which thumb is used, on grip, and on phone size. The
offsets are *consistent per user and per key*, which is precisely why they are learnable and why
learning them removes errors rather than adding randomness. Gboard's spatial model is this, plus a
language prior; the spatial half alone is worth most of the win and carries none of the risk.

Concretely: keep, per key, a running estimate of where the user's taps actually land relative to
that key's centre. At hit-test time, correct the incoming point by the local estimate before
resolving it. Nothing after the resolve changes. The key that is committed is still a key the user
physically touched near; no character is ever revised after the fact.

## The three seams this needs

The codebase is unusually well shaped for it — one funnel in, one funnel out.

1. **`Keyboard2View.getKeyAtPosition(float, float)`** — the only hit test, called once at
   `ACTION_DOWN`. Correction goes here and nowhere else.
2. **`Keyboard2View`'s per-pointer `TouchFx`** — already records `key`, `downX`, `downY`, `downAt`
   and `swiped` for the touch effects. That is the entire observation record the model needs; it is
   already being kept and thrown away.
3. **`Pointers.IPointerEventHandler.onPointerDown(KeyValue, boolean isSwipe)` /
   `onPointerUp`** — the commit point, and the flag that says whether the press was a tap or a
   swipe.

`inapp-keyboard/` is a vendored upstream snapshot and every deviation is documented in
`UPSTREAM.md`, so the model itself must **not** live there. The deviation should be one
host-supplied interface — a `TapResolver` the launcher sets on the view, consulted by
`getKeyAtPosition` and fed by the pointer callbacks — with the whole model, its storage and its
settings in `app/terminal/inappkeyboard/`. That keeps the vendored diff to roughly one field, one
setter and two call sites, and keeps the refresh procedure honest.

## Three findings that decide the design

### 1. The swipes poison the data

Unexpected-Keyboard's model is nine values per key: a centre and eight directions. Users press
**deliberately off-centre** all day, because that is how a swipe starts. A naive heatmap would
learn the swipe offsets and then shift every key towards the direction its owner swipes most.

The model must therefore learn only from presses that committed the *centre* value with
`isSwipe == false` and no directional travel. `TouchFx.swiped` already carries that flag. This is
the single most important constraint in the study; getting it wrong produces a model that is worse
than no model and feels like the keyboard drifting.

### 2. There is a free ground-truth signal, and it costs nothing to collect

A tap model learned only from where taps land is unsupervised: it learns the user's bias but never
learns which presses were *wrong*. There is a supervised signal sitting in the stream already:

> key **X** committed, then Backspace, then key **Y**, where Y is adjacent to X and both were
> taps.

That is the user telling the keyboard, in their own hand, that the point which resolved to X meant
Y. Collected over a few thousand keystrokes it is a labelled dataset for exactly the boundary the
model has to move — and unlike the aggregate bias, it is evidence about the *decision*, not about
the average. The retype must be gated tightly (a short window, no intervening keys, both keys plain
characters, adjacent in the layout) or ordinary editing looks like a correction.

Stage 1 can ship without it. It is what makes stage 2 more than a guess.

### 3. Learn the boundary, not the centre

The naive form — shift each key's rectangle by that key's mean offset — is easy and mostly right,
but it degrades at the edges of the keyboard, where the bias is largest and where shifting a
rectangle either opens a gap or overlaps a neighbour. The better shape is to keep the rectangles
and move the **decision boundary between each adjacent pair**: a single scalar per pair, along the
axis joining their centres. It is the same amount of arithmetic, it cannot open a gap, it is what
the backspace-retype pairs directly supervise, and it is trivially bounded ("a boundary may never
move more than 30% of the narrower key").

## Staging

| Stage | What it is | Risk |
|---|---|---|
| 1 | Per-key tap-centroid bias, shrunk towards zero by sample count, applied as a bounded offset to the incoming point. Learns from centre-value taps only. | Low. Bounded, reversible, no new characters possible. |
| 2 | Per-adjacent-pair boundary offsets supervised by the backspace-retype signal, replacing stage 1's whole-key shift. | Low–moderate. Needs the retype gate to be strict. |
| 3 | A character-level prior (n-gram over the user's shell history / `PATH` commands) breaking ties inside a narrow ambiguity band. | **High. Recommend against — see below.** |

Stage 1 is the shippable one and is maybe 500 lines including tests: a pure model class, a small
persisted store, the host `TapResolver`, a settings toggle and a reset. Stage 2 adds the observer
and a pair table. Stage 3 is a different feature wearing the same coat.

## Why stage 3 is a recommendation against

A language prior is what makes Gboard feel magic, and it is the part that does not belong in a
terminal:

- It changes which character is committed based on a guess about what command you are typing. In
  prose a wrong guess is a typo; on a command line it is a different command, a different filename,
  a different flag.
- The corpus would be the user's shell history — every command they have run, with arguments.
  Reading that into a persistent model is a real privacy escalation over stages 1–2, which store
  only anonymous per-key geometry and never a character sequence.
- It cannot know when a password prompt is on screen. Neither can we.

If it is ever wanted, restrict the corpus to command names on `PATH` (not arguments, not history
lines), keep it off by default, and use it only to break ties within a few percent of the boundary.
That is a much smaller feature than it sounds, which is itself the argument for not building it.

## Constraints any implementation must hold

- **Character keys only.** Never correct a press that resolves to Ctrl, Alt, Esc, Enter, Tab, an
  arrow, or a Fn/compose key. A shifted Ctrl press is an unrecoverable surprise.
- **Never during a swipe or a multi-touch modifier hold.** The correction happens at `ACTION_DOWN`
  and only decides the key; the raw point still goes to `Pointers`, so swipe directions stay
  measured from where the finger actually is.
- **Bounded.** A hard cap in key-width units, enforced in the pure model, not at the call site.
- **Keyed per layout, per orientation, per geometry.** The height scale, key margin and layout
  hot-swap all move the grid; a model learned on one does not apply to another. Key it on the
  layout id plus the resolved key geometry, and start fresh when that changes.
- **Off by default, with a reset.** It is the home screen's keyboard. AGENTS.md's "reverse states"
  rule applies: a way in, a way out, and a way to see what it has learned.
- **Pure and tested.** The model is arithmetic over points — it belongs in the same family as
  `PaneShape`, `DockLayoutPolicy` and `AzScrubGesture`: no Android types, unit-tested directly,
  with the view layer only applying the answer.
- **Storage is bounded and holds no text.** Aggregates per key and per key pair, never a sequence,
  never a character log.

## How to know whether it worked

The honest measure is not "does the model fit". It is whether the user backspaces less.

- The stage-2 retype signal doubles as the metric: count qualifying backspace-retypes per thousand
  keystrokes, before and after. It needs no new instrumentation.
- Offline: record raw touch traces once (a debug build, the developer's own typing), then replay
  them through the model to check that a candidate never *changes* a press that the user did not
  correct. That replay harness is the thing that makes this safe to tune.
- Device: pong, both orientations, both thumbs, and the layouts actually in the hot-swap ring.

## Bottom line

Feasible, worth doing, and smaller than it looks — provided it stays a *tap* model. The seams are
already there (`getKeyAtPosition`, `TouchFx`, `isSwipe`), the vendored module needs one narrow
host hook, and the supervision signal is free. The dictionary half of "autocorrect" is closed by
the terminal itself and should be recorded as closed rather than reattempted.

## Implementation notes (2026-09-01, stage 1)

Checked against the code before building; four points of the study needed correcting.

- **`isSwipe` is not the tap verdict.** `Pointers.onTouchDown` fires `onPointerDown(value, false)`
  for every press at touch-down, before any movement; the swipe arrives later as a second
  `onPointerDown(…, true)` and `onPointerUp` carries no flag. The observer therefore reads
  `TouchFx.swiped` in the view's `ACTION_UP` branch, not the pointer callbacks.
- **`getKeyAtPosition` is shared with the colour-editor paint path.** Correction wraps the
  `ACTION_DOWN` call site only; painting a key colour is never corrected.
- **Observations are recorded against the raw static-grid key**, never the corrected key, so the
  model cannot train on its own output and drift to the cap.
- **Stage 1 already learns the boundary, not the centre.** Per-key centroids are kept, but the
  decision uses the mean of the two keys' shrunk biases for each pair, so the boundary between two
  keys is one line seen from either side (no crossover band, no gap). Stage 2 becomes "add a
  supervised update to that same pair estimate", not a rewrite.
- **The multi-touch rule was relaxed** to "both the raw key and the corrected key must be plain
  character keys". Ctrl held on one thumb and a letter tapped with the other is exactly where a
  correction helps, and the character-only guard already excludes every modifier and action key
  in either direction.

Parameters: shrinkage n/(n+20), cap 0.3 of the narrower key along each axis, statistics halved
when a key reaches 400 taps, store bounded to 8 layout×geometry entries (LRU), written 5 s after
the last tap and on stop. Off means off: disabled, nothing is corrected and nothing is recorded.
