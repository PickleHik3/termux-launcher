# Shader effects and touch latency (2026-10-22)

Status: spec, settled in a grilling round on 2026-10-22. Nothing built.

Two linked pieces of work. The first makes a typed character reach the terminal sooner and measures
where the time goes. The second adds built-in shader effects that cost nothing at rest. The effects
come second so their cost can be judged against a measured baseline.

Read first:
- [`reference/terminal/agsl-performance-and-touch-latency-research.md`](../reference/terminal/agsl-performance-and-touch-latency-research.md):
  AGSL cost rules and the tap-to-pixel pipeline mapped onto the code. Its §2.2 ranks are cited below.
- [`reference/terminal/kitty-custom-shaders-research.md`](../reference/terminal/kitty-custom-shaders-research.md)
  and [`reference/terminal/kitty-shaders-community-research.md`](../reference/terminal/kitty-shaders-community-research.md):
  what kitty and its users do, and why continuous redraw costs a GPU power state at any frame rate.
- [`input-latency-study.md`](input-latency-study.md) and
  [`keyboard-touch-engine-2026-10-16.md`](keyboard-touch-engine-2026-10-16.md): earlier latency work
  and the tap model.

Terms: **Commit**, **Echo**, **Event effect** and **Ambient effect** are in
[`docs/GLOSSARY.md`](../../docs/GLOSSARY.md).

## Decisions

1. **Measure before changing touch behaviour.** All latency numbers in the research are unverified.
2. **Keys still commit on release.** Every letter key on the default layouts has swipe legends, so
   committing on finger-down would conflict with swipes and tap correction on nearly every key.
   Ghost glyphs (a password-leak risk) and send-then-retract (unsafe in a terminal) are rejected.
   The latency work goes into the path after commit.
3. **Gated echo redraw (rank 2).** When terminal output arrives within a few tens of ms of a
   keystroke write (`TerminalSession.mLastWriteUptimeMs`), call `onScreenUpdated` inline instead of
   going through `mUiHandler.post`. Floods of output still coalesce.
4. **Effects are built in only.** They are original AGSL picked in settings, with no user shader
   files and no kitty.conf `custom_shaders` (option A of the kitty note).
5. **Event effects by default; ambient effects allowed as an opt-in.** An ambient effect is labelled
   as a battery cost and stops under Lazy mode, when its page or pane is hidden, and after about
   10 s without input. It resumes on the next touch.
6. **If Terminal effect makes echo frames miss vsync, redraw it as a Paint overlay.** The scanlines
   and tint become a bounded Paint-shader layer instead of a `RenderEffect` over the live pane.
   Warp and curvature looks would be dropped or remade. Do this only if round 1 shows missed frames.
7. **Typing sparks follow the same rule.** They are a bounded Paint-shader layer around the cursor,
   never a `RenderEffect`. If they cost echo frames, the sparks get cheaper, not the echo.
8. **Baseline first, target later.** Round 1 records numbers with no pass bar, and a budget is set
   from them.

## Round 1: measure and the cheap fixes

- **Trace sections (rank 6):** `Keyboard.down`, `Keyboard.commit` and `Terminal.write`, next to the
  existing `Touch.dispatch`, `Terminal.append` and `Terminal.render`, so a Perfetto query can join
  each key to its frame.
- **Gated echo redraw (rank 2):** decision 3.
- **Pre-warm (rank 5):** one tiny offscreen draw when an effect is enabled, so the first-use compile
  does not land on a typed character.
- **Idle frame audit (rank 4):** list every source that keeps frames running at rest (status clock,
  glass motion, trails), with Perfetto evidence. Stopping them is a separate decision.
- **Baseline on pong:** before and after the gated redraw, record finger-rest time (down to up),
  key-down to commit, commit to echo drawn, and missed frames with Terminal effect off and on.
  Results go in a table in `input-latency-study.md`. Method: research note §2.4.

## Round 2: event effects

Bell flash, focus pulse, find-cursor and typing sparks. All are event effects. Each follows research
note §1.2–1.4: compile once, set uniforms on change, run a frame callback only while moving, and draw
with a Paint shader on bounded shapes. Each is measured against the round-1 baseline for missed echo
frames. Ambient effects are allowed by decision 5, but none is planned yet.

## Owed device checks

Round 1's pong baseline is the first. Each effect in round 2 owes a look on pong and a missed-frame
check while typing. Record them in `project-docs/device-checks.md` when they are built.
