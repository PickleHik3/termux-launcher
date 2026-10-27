# Legibility contract (spec, 2026-10-27)

ADR: [docs/adr/0014-legibility-is-one-contract.md](../../docs/adr/0014-legibility-is-one-contract.md).
Research and measurements:
[material-terminal-palette-research-2026-10-27.md](../reference/terminal/material-terminal-palette-research-2026-10-27.md),
including the "Verified afterwards" section.

## Reproduced failures (the before state)

| Case | Measured | Where |
|---|---|---|
| dialog red / blue on ANSI white, dark recipe, Default | 1.00:1 (`#FFB1C1` on `#BDC8CE`) | `TerminalPanelInkTest` (seed of the grid) |
| the same on pong, 1.3.0-pre.4 | 1.00 / 1.01:1 on `#c8c4d3` | pong screenshot, 2026-10-27 |
| light recipe, Softer | 2.65:1 | `TerminalPanelInkTest` |
| light theme on glass over a dark wallpaper (flips to the pale-ink recipe) | 1.00–1.12:1 | `TerminalPanelInkTest` |
| grey (slot 8) over the bright patch of pong's cloud wallpaper | 1.18:1; body text 2.29:1 | pong screenshot |

Bold-as-bright is off by default (`TermuxPropertyConstants.java:513`), so dialog's bold tags draw
in slot 4, not slot 12.

## Wave 1: the pair contract

### The table

One declarative table (a small `LegibilityPairs` type beside `MaterialTerminalColorScheme`) that
the solver and the tests both read:

| Ink slots | Ground | Floor |
|---|---|---|
| foreground | background / band ends | `level.bodyTarget` |
| 1–6, 9–14 | background / band ends | `level.ansiRatio` |
| 8 | background / band ends | `level.dimRatio` (tone band as today) |
| 0, 1, 4 | slot 7 | 3.0 at every level |
| 7, 15 | slot 0 | 4.5 at every level |

Green, yellow, magenta and cyan on slot 7 are exempt (kitty parity: kitty's green is 1.61:1 and
its yellow 1.27:1 on its own `#ddd`). The cursor keeps its own rule.

### The solver

- Neutrals first: slot 0 stays at tone 25. Slot 7 rises from tone 80 (dark) and 75 (light) to the
  tone the pair "7 on 0 ≥ 4.5" and the accents' "on 7 ≥ 3" leave room for, about 88 dark. Slot 15
  stays above slot 7.
- Accents are then searched in tone (hue and chroma kept, as `contrastTone` does) for the tone
  nearest their recipe tone that satisfies **every** pair they appear in. In the dark recipe this
  lands slots 1 and 4 around tone 46–52. The other normal accents move to the same tone band, so
  the six still read as one palette. Bright slots (9–14) stay light and need only the background.
- When no tone satisfies every pair (the light-recipe Softer case, for one), the solver prefers
  the ground the text is drawn on most (the background) and reports the miss. The grid then
  fails, so a miss can't ship silently.
- `ansiSlots` keeps producing the recipe tones. `applyAnsiContrastFloor` becomes the pair-table
  pass. The per-level recipe numbers live in `TerminalContrastLevel` as today; `normalToneDark`
  comes down to match what the solver lands on, so the recipe is not fighting the floor.

Anchors, verified at base `c6e1bd3a7`:
- `MaterialTerminalColorScheme.java`:
  - `build` at 216–262 (the floor is applied at 255);
  - `ansiFloor` at 427 and `applyAnsiContrastFloor` at 436;
  - `ansiSlots` at 473–510 (neutrals at 496–499);
  - `contrastTone` at 1031;
  - the tone constants at 118–128.
- `TerminalContrastLevel.java`: rows at 16–18, fields at 25–60.

### Exports

The `colors.properties`, kitty and noctalia writers already take the built `Properties`. A test
asserts that the exported values equal the solved palette for the grid's seeds
(`MaterialColorExportWriteTest` is the neighbour).

### Test grid

`LegibilityContractTest` (it grows from `TerminalPanelInkTest`):
- **Seeds:** pong (`0xFF0C141B, 0xFFDCE3ED, 0xFF93CCFF, 0xFFF2B8B5`), daylight
  (`0xFFF8F9FF, 0xFF191C20, 0xFF3A608F, 0xFFBA1A1A`), a saturated theme and a grey one.
- **Grounds:** opaque, plus glass at a dark ground (`0xFF1B1A17`), a mid ground and a light ground.
- **Levels:** all three.
- **Assertion:** every pair in the table. A failure names the seed, ground, level, pair, both
  colours and the ratio.

### Device card

`project-docs/verification/legibility-card.sh` prints the pairs: the dialog panel row (`47` with
`30`, `31`, `34` and bold `34`), the accents on the background, the grey and faint lines across
the pane. The owed check in `project-docs/device-checks.md` is: run it on pong over the cloud
wallpaper, screenshot it, and read the ratios with the pixel script. Each failing pair is listed.

### Wave 1 pass state

- The grid is green.
- On pong, the dialog row reads ≥ 3:1 for black, red and blue.
- The existing terminal palette tests still pass, or each changed expectation is listed in the
  merge message with its reason.

## Wave 2: the even-out stage

### Where it sits

Glass stack per text-bearing band: blur/frost → **even-out** → tint → Opacity colour → grain →
rim. The terminal pane goes first (`PaneGlassBackdropView.onDraw`, 694–855: the frost bitmap at
~812–824 is where it applies), then the keyboard, dock, status bar and widgets through the same
stage.

### What it computes

Inputs: the band's lightness percentiles from the pre-blurred frame (p2, mean, p98, in HCT tone /
L\*), the ink polarity, and the Contrast level.

- **Polarity:** chosen as today (`inkPolarity`, from the ground), but measured on the band's mean.
- **The ink's limit:** each level carries two new constants, the pale-ink ground ceiling and the
  dark-ink ground floor. They are derived so that the recipe's nominal inks (foreground, grey and
  accents) meet their floors at that ground, and a test asserts the derivation. For pong's
  palette at Default, the ceiling is about tone 40 (grey `#b3b2b9` at 3:1 needs ground ≤ tone
  40.6; the foreground at 4.5:1, ≤ 42.8).
- **Width:** each level carries a kept lightness range `w`: Softer widest, Default about 14 tones,
  Harder narrowest.
- **Mapping:** lightness is mapped linearly from [p2, p98] onto [c − w/2, c + w/2], where
  `c = min(mean, ceiling − w/2)` for pale ink, mirrored for dark ink. When the range already fits,
  the mapping is the identity, so a dark wallpaper is untouched. Hue is kept, and chroma is
  reduced only to stay in gamut.
- **What the palette sees:** the band ends after tint and Opacity (both composited as today with
  `OnGlass.backdrop`). These replace the single mean ground in `MaterialTerminalColorScheme.build`
  (`ground` parameter; `ChromeInk.terminalPane` 466, `meanPaneVote`). The palette rebuilds only
  when a band end moves by more than the signature step.

Simulated on pong's cloud pane (mean tone 39.5, p2–p98 13.5–67.8): the band lands at 27–41, the
mean moves 39.5 → 33.6, and the worst grey is 3.00:1 and the worst body text 4.88:1.

### Rendering

- Android 13+: an AGSL pass on the frost bitmap, one program per surface (the pattern of
  `PaneGlassBackdropView`'s existing RuntimeShader cache, ~170).
- Android 8–12: the same curve applied in software once to the cached pre-blurred frame,
  recomputed when the wallpaper, rect, level or polarity changes. Nothing runs per frame.
- Sampling: `GlassBackdropCache.sampleBitmapRegion` (434) returns a mean today. Add a sibling that
  returns the percentiles from the same grid.

### Switch

Developer → "Even out glass", on by default. Off restores today's drawing and today's mean
ground, for before/after shots. It is removed one release after wave 2 ships.

### Wave 2 pass state

- The grid gains band-end cases and is green.
- On pong over the cloud wallpaper, the card's grey reads ≥ the level's dim floor and its body
  text ≥ the body floor everywhere in the pane.
- The keyboard and the terminal glass match on the same wallpaper.
- No jank: frame time on pong within today's budget, with the switch on and off compared.

## Out of scope

Per-glyph halos; a new user-facing slider; changing what Blur, Grain, Tint or Opacity mean.
