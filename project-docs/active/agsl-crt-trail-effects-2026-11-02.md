# CRT realism, bezel depth, trail glow and event effects (2026-11-02)

Status: spec, settled in a grilling round on 2026-11-02 (19 questions). All four waves built the
same day on branch `worktree-agsl-effects` (waves 1–2 merged 866b20085/963e8329e, waves 3–4
committed 2b7484ebd/88e25065e), emulator-shot on the page at `~/reports/crt-effects-2026-11-02`;
developer judgement and pong checks owed (`project-docs/device-checks.md`). Known gaps from the
build: decision 7's power-off is in the shader but unwired (the leaving pane is removed at once and
its ghost keeps no picture); death snow shows only for a shell killed from outside (a plain `exit`
closes the pane first); a split or focus change started from the keyboard is held by decision 3's
typing rule; the Idle animations loop restarts only on a visibility, screen or style change.

Wave 5 (2026-11-02 evening, from the developer's look at pong, no emulator page): text under
CRT is sharpened with a 1 px unsharp mask on bent screens (`PaneRetroEffect.SHARPEN`, none on the
flat chrome); the glow blooms only from pixels above `BLOOM_FLOOR`, so it haloes glyphs without
filling the gaps of dense text; the Material CRT keeps coloured text its own colour and tints
only greys and whites (`PaneRetroStyle.keepsColour`), since its phosphor is the wallpaper's hue,
the same hue as the glass behind the pane; the trail halo is halved (peak 0.28) with a feathered
two-cell skirt instead of a one-cell box; the trail decays go back to kitty's 0.10/0.40 s
(decision 9 amended), since 0.60 s lingered on newlines over a slow ssh echo.

Wave 6 (same evening; the developer: "the borders don't convey a proper CRT border feel", pointing
at sam1am/termux_crt, whose look is a barrel-shaped picture in black with a steep 45 % vignette and
a centre glow, no bezel at all). The bend is now pinned at the middle of each edge
(`c(1 + b|c|²)/(1 + b)`): the picture touches the glass there, its corners pull inward, and the
gap is a dark tube face (`TUBE_RGB`, alpha 0.92, no scanlines or mask) under the glass's recess
and sheen; the bend clearance in `PaneBezel.contentInsets` is gone, since no cell leaves the
screen (the columns it reserved come back). The vignette runs in the picture's space on
termux_crt's `uv(1-uv)` curve, full across the middle third and 30 % down at the edge (decision
5's 0.08 r² vignette replaced; 45 % was judged too dark for edge text). A 4 % additive haze in the
tint's colour lights the middle (clamped to the picture's alpha, so glass panes keep their glass).
The bezel band, line and shade are unchanged.

Wave 7 (11-03; the developer, from pong: "I like solid borders more than glass borders for the
terminal"). The bezel band is a solid fill in the theme's raised panel colour
(`termuxColorSurfacePanelHigh`, the drawer's and popups' tone; `PaneBezel.fill` takes only that
colour and always returns it opaque), whatever the pane's glass is doing; the shade overlay, the
1 dp line and the glass's recess and sheen inside the screen are unchanged. The pane's glass now
shows only inside the screen. Before, the band was the pane's own glass and took an opaque fill
(the surface colour) only while the terminal's glass had no opacity.

Builds on [`agsl-effects-and-touch-latency-2026-10-22.md`](agsl-effects-and-touch-latency-2026-10-22.md)
and amends two of its decisions (see "Amendments"). Everything here is built-in AGSL picked in
settings (its decision 4); nothing reads user shader files.

Terms: **Event effect**, **Ambient effect** as in [`docs/GLOSSARY.md`](../../docs/GLOSSARY.md).
**Screen**: the rect inside a pane's bezel the CRT draws on (`RetroUniforms.screen*`). **Chrome**:
the Workspace bars the effect also covers (`chrome/ChromeRetroEffect.java`), drawn flat.

## What exists today

- `terminal/PaneRetroEffect.java`: one AGSL program per family. `CRT_AGSL` (:110–147) does barrel
  bend `CRT_BEND = 0.04` (:29), vignette `PANE_VIGNETTE = 0.08` (:32), a 4-tap glow at
  `1.5 * uDensity` mixed at 0.35 (:130–135), phosphor tint, scanlines `mix(0.70, 1.0, cos)` at
  `RetroUniforms.PERIOD_DP = 3` dp (:141). `TFT_AGSL` (:149–159) is a grid. Uniforms are declared
  once in `HEAD` (:77–100) and written by `RetroEffectBinder` from `RetroUniforms`.
- The chrome runs the same program with `uBend = 0` and no vignette (`ChromeRetroEffect` javadoc).
- `terminal/PaneBezel.java`: `SIDE_DP = 12`, `TOP_BOTTOM_DP = 16`, `SCREEN_RADIUS_DP = 10` (:23–27);
  `fill()` (:111–113) is flat: transparent while the pane's glass shows through, else the opaque
  surface colour. `terminal/PaneContentFrame.java` draws the fill and a 1 dp line
  (`PaneRim.STOCK_STROKE_DP`) around the screen (about :555–570).
- Cursor trail: `terminal-view/.../KittyCursorTrail.java` is a faithful port of kitty's
  `cursor_trail.c` (per-corner decay :278–341, opacity ramp :343–349). Drawn by
  `terminal/PaneMotionOverlayView.java` as a flat quad in the cursor colour (`drawQuad` :486–500).
  Defaults 10 ms, decay 0.10/0.40 s, threshold 2 cells (`TerminalFontConfig.java:823–892`,
  `TerminalPaneController.java:675–678, 726–745`). Six styles in `CursorTrailStyle.java`.
- Setting: `terminal_retro_effect` list (None, CRT, CRT green, CRT amber, CRT Material colours,
  TFT) in `performance_preferences.xml:55–61` and `terminal_text_preferences.xml:24`, shown only
  when `PaneRetroEffect.available()` (API 33+).

## Decisions

1. **Deliverable.** This spec, then prototype builds shot on the emulator onto a before/after page in
   `~/reports`; pong only after the developer has looked at the page.
2. **Scope.** New static parts go on panes and on the chrome (keeping the one-display rule); every
   animated part (glitch, flicker, events) is pane-only. Home chrome stays plain, as today.
3. **Headache guard.** Scanlines and the triad mask are static; nothing rolls. No flicker unless
   Idle animations is on, and then within the 10-22 caps (a few percent, under 3 flash-like events a
   second). Glitches are events, under 400 ms, and never fire while the user is typing or scrolling
   (a key commit or a scroll within the last 1.5 s suppresses them, as scenes already hold still).
   Every animated part is off while Android's "Remove animations" is on
   (`ValueAnimator.areAnimatorsEnabled()` false).
4. **Battery.** Static passes ride the pane's own invalidates; a pane at rest costs nothing. Only
   events and opted-in ambient parts drive frames, and the trail only while a trail is live.
5. **Richer static CRT**, replacing the current one in place (no "classic" preset):
   - *Chromatic aberration*: red and blue sampled at offsets growing from 0 at the screen centre to
     about 0.6 px at the edges (in the bent space, so it follows the glass). On the chrome a constant
     0.3 px split.
   - *Phosphor triad mask*: vertical RGB stripes at the 3 dp period, about 10% depth, on panes and
     chrome alike, phase-locked to `uOrigin` like the scanlines so stripes run on across surfaces.
   - *Glass recess and sheen*, pane-only: an inner shadow inside the screen's edge (about 6 dp wide,
     darkest at the lip, about 25% at the edge, zero at 6 dp) and one faint diagonal specular band
     (under 6% white, top-left to bottom-right, fixed, no motion).
   - Existing bend, vignette, glow, tint and scanline depth are unchanged.
6. **Bezel**: `SIDE_DP` 12 → 8, `TOP_BOTTOM_DP` 16 → 10. Depth from a multiplicative shade overlay
   drawn after the fill (so it works over glass and over the solid surface colour alike): the bezel's
   top edge lit (about +8% white), bottom edge darker (about −18% black), linear top to bottom; the
   inner lip's shadow onto the screen is decision 5's recess. The 1 dp line stays. Bend clearance
   (`PaneBezel.screen`, `bendClearancePx`) is recomputed from the new sizes, nothing else changes.
7. **Glitch, events only** (each bounded; all pane-only; all honour decision 3):
   - *Bell*: one horizontal tear band (a 3–6 row slice shifted sideways a few px) plus a brief red/blue
     split, under 150 ms.
   - *Pane opens*: power-on sweep, about 400 ms (a bright horizontal line widening to the screen, with
     the picture fading in).
   - *Pane closes*: collapse to a bright line, about 250 ms.
   - *Pane takes focus*: faint degauss wobble (a damped sideways shear of a few px), under 300 ms.
   - *Pane's process dies*: a burst of snow for about 300 ms before the dead-pane card.
   - *Ambient glitch*: a random tear every 2–5 minutes, under 200 ms, only under Idle animations.
8. **Unfocused panes dim**: with more than one pane, unfocused ones lose about 10% brightness and 20%
   saturation inside the CRT pass. Static (one uniform per pane), no extra frames.
9. **Cursor trail**: an AGSL glow halo under kitty's quad, about one cell wide, additive in the trail
   colour, brightest at the cursor end and fading along the body; the quad above it stays as
   drawn. Applies to `DEFAULT` and `COMET` only. App defaults for the decays become 0.15 / 0.60 s
   (`cursor_trail_decay` in kitty.conf still wins); the 2-cell threshold and 10 ms delay stay.
   Measure first: the same kitty.conf recorded on the emulator and on the developer's desktop kitty,
   side by side on the reports page, so the halo targets the real gap.
10. **Settings.** The Terminal effect list is unchanged. One new switch under it, **Screen events**
    (bell tear, power on and off, focus wobble, death static), default on. One new switch on the
    Battery and performance page, **Idle animations**, default off, described as a battery cost;
    it gates the ambient glitch, flicker and any future ambient effect. Plain copy per the UI
    copy rule: the row says what it does in one fact.
11. **Verification.** Emulator shots and short clips per wave on a `~/reports` page (CRT pane,
    bezel, chrome; bell tear; power on/off; trail next to desktop kitty). The 10-22 missed-echo-frame
    measurement with Terminal effect on and off runs once on pong during wave 1, as a check, not a
    gate.

## Amendments to the 10-22 spec

- *Decision 10 (flicker)*: flicker is ambient-only, under Idle animations, never tied to keystrokes
  or redraws. The amplitude and event caps stand.
- *Decision 11 (surfaces)*: the cursor and chrome take static shader parts; animated parts are
  pane-only. Keyboard and bars get aberration (constant) and the triad mask, nothing that moves.

## Waves

Each wave is one worker, merged and shot before the next starts.

1. **Static CRT + bezel** (pane and chrome): decisions 5 and 6. Files: `PaneRetroEffect.java`
   (`HEAD`, `CRT_AGSL`), `RetroUniforms.java` (new uniforms: recess/sheen on, aberration scale,
   dim), `RetroEffectBinder`, `PaneBezel.java`, `PaneContentFrame.java` (shade overlay). JVM tests
   for the new geometry (`PaneBezel.screen` sizes and clearance, shade stops), instrumentation
   compile of the shader as `GlassRefractionAgslInstrumentationTest` does.
2. **Trail**: decision 9 (halo + decays), plus the side-by-side measurement.
3. **Events**: decision 7's five events and the Screen events switch.
4. **Idle animations**: the switch, ambient glitch, flicker, and decision 8's dim.
5. **Solid bezel + rim drop** (wave 7, 7b): done, 54c5ce312 and c4e60f78f.
6. **Deck look** (wave 8): three workers on disjoint seams: `DeckLook` + strip (dock/statusbar),
   keyboard (inapp-keyboard module + TermuxInAppKeyboard), extra keys (termux-shared
   ExtraKeysView); then the TermuxActivity wiring.
7. **Wave 9: signal** (`PaneRetroEffect` constants): (a) chroma bleed: luma stays sharp, chroma is
   the mean of three horizontal taps, the glow taps at 2.0 dp sideways and 1.0 dp up and down;
   (b) beam-width scanlines: the row floor goes 0.70 to 0.92 with luma (smoothstep 0.15 to 0.85);
   (c) highlights: bloom weight 0.35 plus 0.10 above 0.7 to 0.95 luma; (d) opt-in Composite signal
   (`terminal_retro_composite`, panes only): luma `[0.25 0.5 0.25]` over the row, chroma over five
   taps, sharpen off. No new samples anywhere.

## Wave 8: the deck look (decided 2026-11-03)

While a terminal effect is on, the Workspace's strip (window chips and metric cards) and the
in-app keyboard with its extra keys row stop being glass and become the CRT's accessories: a
deck above the set and a unit below it, in black plastic. Decided from the OpenDesign mockups
(project "CRT solid frame: status bar and keyboard", page `deck.html`, cut A); the developer:
nothing is drawn for looks alone, every light means something; no mockups beyond this.

- **Material**: an opaque fill in the frame's own tone (`termuxColorSurfacePanelHigh`, the
  bezel's colour) with the bezel's shade (lit 8 % at the top, 18 % black at the bottom), so the
  strip, the CRT card and the keyboard read as one stack. Off the effect everything returns to
  the Look's glass. No new setting.
- **Chips as deck buttons**: a rounded-4 dp raised button (1 dp lighter top edge, 2 dp darker
  foot) per window and for "+". The window in front: label in the lit colour. A 5 dp LED dot at
  the chip's left, lit on the window in front; on the others it carries what the chip's ring and
  dot carry today (attention / bell) and the working state (`WallStripModel.isWorking`): lit for
  attention, half-lit for working, dark otherwise.
- **Metrics in a display window**: CPU, RAM and weather in one dark inset window (near-black,
  1 dp inner shadow) in the display colour with a soft glow, same text sizes as today.
- **Keys as push-buttons**: opaque key fill one step lighter than the bed, 1 dp lighter top
  edge, 2 dp darker foot, corner radius 6 dp; legends as today. A latched modifier: label in
  the lit colour plus a 2 dp bar near the key's foot. Return: filled in the lit colour with dark
  ink. Extra keys row: the same buttons; the active mode button lit with the bar.
- **Palette per effect** (the display colour / the lit colour): CRT and TFT cyan `#7CF5D8` /
  amber `#FFB020`; CRT green: green `#8CF0A4` / `#6EF08E`; CRT amber: amber `#FFBE5C` /
  `#FFB020`; Material CRT: the theme's primary for both. One pure class `DeckLook` (style ->
  colours) beside `PaneRetroStyle`, JVM-tested.
- **Scope**: Workspace only, like `ChromeRetroEffect` (the Home page keeps the Look); the bezel
  thickness (8/10 dp) is unchanged.

## Owed device checks

Wave 1: a look at a CRT pane and the chrome on pong; the missed-frame check typing with Terminal
effect on and off. Each later wave: a look at its effect on pong. Record in
`project-docs/device-checks.md` when built.
