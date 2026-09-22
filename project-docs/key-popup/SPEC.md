# Pressed-key popup — spec

## v2 — minimal (2026-09-21)

Shipped design, after the developer used v1 ("Halo Float") for a day: *"modern minimalism instead
of too fancy items which would not be ideal for daily use by a variety of user base."* Everything
under "v1" below is superseded and kept only for the record.

Phase `feat/key-popup-minimal`, from `dev` at d7c3d7d3.

1. **One glyph, nothing else.** Per finger the popup is a single filled character: the value that
   would be committed. No ring of alternates, no halo, no HELD / LATCH mark, and no veil over the
   keyboard — the veil, `KeyPopupPalette.dim` and the whole `setVeilBounds` path are gone. On a tap
   it is the key's centre value; when the pointer engine reports a swipe target the glyph is
   **replaced in place** by the target's value, never joined by it.
2. **Up at once, and it stays.** The glyph appears the moment the finger lands (no grace) and stays at least 180 ms before it fades, however short the tap, so fast typing reads evenly instead of flickering (measured 2026-09-21: taps of 20–100 ms). A swipe target crossfades into the same glyph.
3. **Anchored just above the cap.** Anchor X is the cap's centre, clamped so the glyph's measured
   half-width plus 4 dp stays inside the keyboard's own width. Anchor Y puts the glyph's line box
   6 dp above the cap's top edge — a 30 dp glyph is centred 21 dp above it, against v1's ~66 dp
   rise — and never above the top of the overlay. The overlay still lives in the activity's content
   view, so a top-row glyph floats over the terminal.
4. **Sizes and typeface.** Label-length tiers: 1 char → 26 dp, 2 → 17 dp, 3–4 → 13 dp, 5+ → 11 dp, all in the keyboard's own label face (`Config.labelFont`, i.e. the user's custom font from Settings when set) with no synthesized weights and no monospace substitute, so the popup can never mismatch the caps. `FLAG_KEY_FONT` glyphs use the keyboard's special font, as on the caps.
5. **Colour.** The glyph is filled with `onSurface` (the caps' own label family) and wrapped in a glow that follows the letterform: the same text drawn underneath with zero-offset soft shadows in `primary` — a wide faint pass (7 dp @ 28 %) for separation from the caps and a tight one (2.5 dp @ 70 %) for the edge. No disc, no box, no drop shadow.
6. **Motion.** Enter 130 ms: the glyph rises out of the cap (from the cap's top edge to its anchor), scaling 0.82→1 and solid by half-way, on Material's emphasized-decelerate curve `(0.05, 0.7, 0.1, 1)`. Exit 140 ms after the stay: drifts 10 dp further up, scales to 0.9 and fades on emphasized-accelerate `(0.3, 0, 0.8, 0.15)`. Swipe crossfade 60 ms. Reduced motion → 1 ms.
7. **Unchanged.** Per-pointer state, the exit running after the key is committed and never gating
   input, the `in_app_keyboard_key_popup` toggle and its copy, the palette refresh on a theme
   change, floating and docked keyboards alike, and `onTouchEvent` returning false.

---

# Pressed-key popup ("Halo Float") — spec v1 (2026-09-21)

*(v1, superseded by v2 above.)*

Design handoff received from the developer on 2026-09-21 (bundle `Terminal keyboard popup variants.zip`, README reproduced below verbatim). It supersedes the review-page variants (balloon, compass, stem callouts) and settles D5.

## Decisions taken with it

| # | Decision |
|---|---|
| D5 | Halo Float, as specified below. No box, no stem; floating outlined glyph, `primary` halo, alternates orbiting, surface-wide dim. |
| D6 | Every key with a label shows it, modifiers included (the spec gives Ctrl/Alt/Shift a HELD / LATCH sub-label). Replaces the earlier "character keys only" default. |
| D7 | One toggle under Keyboard → Feedback, on by default. Copy is product copy, one plain sentence. |
| D8 | No popup material of its own: the halo plus the dim carry legibility, colours from the Material roles in the token table. Replaces the "opaque activated-key colour" default. |
| Repeat | Open in the spec; taken here: the popup stays static while a key auto-repeats (no pulse, no counter). |
| px | The spec's px are read as dp; its 46px cap matches the app's ~48dp key. |

## Build plan

| Phase | Branch | Deliverable | Depends on |
|---|---|---|---|
| K | `feat/key-popup` (from `feat/widget-drag-key-popup`) | Pointer hooks in `inapp-keyboard`, `KeyPopupOverlayView` + wiring in `terminal/inappkeyboard`, setting, UPSTREAM.md entry, unit tests | — |

---

# Handoff: Halo Float — pressed-key popup for the in-app terminal keyboard

## Overview

The terminal app ships a custom in-app soft keyboard. Every keycap carries a base character plus up to eight alternate characters in its corners and edges, committed by short directional swipes originating on the cap (N, NE, E, SE, S, SW, W, NW — only the directions that are configured for that key). There is no long-press; long-press is reserved for holding Shift / Ctrl / Alt.

Today a pressed key simply illuminates. **Halo Float** replaces that with a floating, containerless popup: the character lifts clear of the cap, hairline-stroked with an amber halo, with the key's configured alternates orbiting it. Swiping targets one of the alternates; it strokes amber, scales up and flies outward while the centre glyph is replaced by it. Releasing commits.

The reference is the BlackBerry soft keyboard's floating outlined character, rebuilt for an 8-way swipe keyboard on a dark terminal ground.

## About the design files

`Halo Float Popup.dc.html` in this bundle is a **design reference created in HTML** — a working prototype of the intended look and behaviour, not production code to port line for line. The task is to **recreate this interaction inside the terminal app's existing environment** (Android/Kotlin + Compose or Views, React Native, or whatever the keyboard is currently built in), using its established input pipeline, theming and animation primitives.

Open the file in a browser and press-and-drag the keys; the behaviour is fully interactive, including two-finger simultaneous presses.

## Fidelity

**High-fidelity.** Sizes, typography, timings and easings below are final and should be matched. Three deliberate substitutions to be aware of:

- **Colour is themed, not fixed.** Every colour in the prototype stands in for a Material 3 role resolved from the Android system theme — see Design tokens. Match the role assignments and the alphas, not the hex values.
- The prototype uses **IBM Plex Sans** and **IBM Plex Mono** as stand-ins for whatever the app's own sans / monospace faces are. Substitute the real ones; keep the sans/mono split (base characters in the sans face, alternates always monospace).
- The prototype's terminal strip is dummy `ls -la` output, present only so popup contrast and overlap can be judged. It is not part of the deliverable.

---

## The popup

### Anchor and position

Geometry is derived from the ring radius `R` (default **34px**) and from metrics of the currently displayed label, so nothing is hard-coded to one key size.

```
below   = max(R + 6, fontSize * 0.55 + 6) + (modifier ? 12 : 0)
above   = max(R + 8, fontSize * 0.62)
sideExt = max(labelHalfWidth, R + (ringHasMultiCharLabel ? 22 : 12))

anchorX = clamp(keyCentreX, sideExt + 8, keyboardWidth - sideExt - 8)
anchorY = max(keyTop - below - 20, above + 10)
```

The anchor is the **centre of the popup**; every element inside is positioned `translate(-50%, -50%)` from it. With default `R` and a single-character label this puts the anchor ~66px above the top edge of the cap.

- **Horizontal clamp** keeps edge-column popups (`q`, `p`, `Enter`) inside the keyboard's bounds — the popup slides inward rather than being cut off.
- **Vertical clamp** keeps top-row popups inside the app; they are allowed and expected to float over the terminal output above the keyboard.

### Layers, back to front

1. **Halo** — circle, diameter `round((labelHalfWidth + R) * 1.55)`, `radial-gradient(circle, rgba(233,179,8,0.20) 0%, rgba(233,179,8,0.09) 34%, rgba(233,179,8,0) 70%)`. The two alpha stops multiply by the `glow` factor (default 1).
2. **Centre glyph** — see typography table. `color: transparent` with a **stroke** (`-webkit-text-stroke`, i.e. outline-only text), plus `text-shadow: 0 0 15px rgba(233,179,8,0.50), 0 0 44px rgba(233,179,8,0.22)` (alphas × `glow`).
3. **Modifier sub-label** — 8px IBM Plex Mono 500, `letter-spacing: 0.14em`, 3px below the glyph. Only rendered for Ctrl / Alt / Shift. Text is `HELD` while the finger is down, `LATCH` once latched. Colour `rgba(240,236,226,0.45)`, or accent `#e9b308` when latched.
4. **Ring glyphs** — one per *configured* direction; unconfigured directions render nothing at all.

There is **no background container, card, border or scrim** anywhere in this popup. That is the point of the treatment. Legibility comes from the halo plus dimming the keyboard beneath.

### Ring geometry

Unit vectors, clockwise from north:

| dir | dx | dy |
|-----|-----|-----|
| N | 0 | −1 |
| NE | 0.707 | −0.707 |
| E | 1 | 0 |
| SE | 0.707 | 0.707 |
| S | 0 | 1 |
| SW | −0.707 | 0.707 |
| W | −1 | 0 |
| NW | −0.707 | −0.707 |

```
r = (isTarget ? R + 13 : R)
  + (label.length > 1 ? 9 : 0)
  + (labelHalfWidth - 34)          // widens the ring for wide centre labels

x = dx * r        y = dy * r
```

| state | font-size | scale | colour | glow | opacity |
|---|---|---|---|---|---|
| idle, no swipe active | 14px (11px if label is multi-char) | 1 | `rgba(240,236,226,0.6)` | none | 1 |
| non-target, swipe active | same | 1 | `rgba(240,236,226,0.6)` | none | 0.2 |
| target | same | 1.45 | `#e9b308` | `0 0 14px rgba(233,179,8,0.8)` | 1 |

Ring glyphs are always monospace 500.

### Centre glyph typography

Size steps off label length so `Ctrl`, `Home`, `space`, `123` sit inside the halo instead of bursting it.

| label length | font | weight | size | stroke width | half-width used in layout |
|---|---|---|---|---|---|
| 1 | sans | 300 | 46px | 1.4px | 34 |
| 2 | mono | 400 | 30px | 1.1px | 34 |
| 3–4 | mono | 500 | 22px | 0.9px | 38 |
| 5+ | mono | 500 | 17px | 0.8px | 42 |

Stroke colour: `rgba(246,242,232,0.95)` at rest; **accent `#e9b308` once a direction is targeted** (the centre glyph is replaced by the target character and restrokes amber — it is never filled).

---

## Gesture model

| constant | value |
|---|---|
| dead zone | 13px of travel from touch-down before any direction can register |
| angular tolerance | dot product ≥ 0.55 (≈ 57°) |

On every move past the dead zone: normalise the travel vector, take the dot product against each **configured** direction's unit vector, and pick the highest above 0.55. This is deliberately *not* fixed 45° octants — a key with only NE and SW configured should accept a sloppy diagonal, and a key with no configured direction near the swipe should register nothing and fall back to the base character.

Re-render only when the resolved direction actually changes. On a physical device this runs on every touch-move sample; recomputing the whole popup per sample is wasteful and produces visible jitter.

State is keyed by **pointer id**, not by a single "active key" — two thumbs down at once produce two independent popups. This matters for fast terminal typing, where the next key goes down before the previous one is released.

### Release

- Non-modifier, no direction → commit the base character.
- Non-modifier, direction resolved → commit that alternate.
- **Modifier (Ctrl / Alt / Shift), no direction** → toggle its latch; commit nothing. A latched modifier keeps a 2px accent underline across the bottom of its cap until it is used or toggled off.
- **Modifier with a direction** → commit that alternate (e.g. Ctrl SW = `123` layer), do not change latch state.

---

## Keycap treatment

Unpressed caps are unchanged from the current keyboard:

- 46px tall, `border-radius: 9px`, `background: rgba(255,255,255,0.055)`, `border: 1px solid rgba(255,255,255,0.07)`, `box-shadow: inset 0 1px 0 rgba(255,255,255,0.04)`, 4px gap between caps, 6px between rows.
- Base glyph 17px sans 400 `#f2efe8`, centred.
- Alternates sit in a 3×3 grid inset 3px from the cap edge, 8.5px mono 500 `rgba(240,236,226,0.4)`, centred in their cell. The centre cell is empty.
- Enter is the accent cap: `#e0b420` fill, `#1a160c` glyph.

**While pressed:**

| element | change | transition |
|---|---|---|
| **whole surface** — keyboard *and* terminal output | a flat `surfaceContainerLowest` scrim at 72% opacity is laid over everything, beneath the popup | opacity 110ms linear |
| pressed cap fill | `rgba(240,236,226,0.1)` overlay added | opacity 70ms linear |
| pressed cap base glyph | opacity → 0.25 | 90ms linear |
| pressed cap corner alternates | opacity → 0.15 | 90ms linear |

The surface-wide dim is load-bearing, not decoration. The popup has no background of its own, and its ring reaches into the row above — a southern ring glyph on a bottom-row key lands squarely inside a neighbouring keycap. Without the dim, an 11px monospace ring glyph sitting on a live cap is indistinguishable from that cap's own corner labels. Dimming the whole surface is what separates the two planes. Do not substitute per-key dimming.

The dim goes **above** the keyboard and terminal and **below** the popup. It stays up as long as any pointer is down, and fades out with the last popup.

## Motion

| moment | animation | duration | easing |
|---|---|---|---|
| surface dim in / out | opacity 0↔0.72 | 110ms | linear |
| popup enter | opacity 0→1, scale 0.72→1 (about the anchor) | 170ms | `cubic-bezier(.2, 1.6, .45, 1)` |
| popup exit (on release) | opacity 0.9→0, scale 1→1.28 | 145ms | `ease-out` |
| ring glyph reposition / scale | transform | 160ms | `cubic-bezier(.2, 1.5, .45, 1)` |
| ring glyph colour, opacity | colour / opacity | 90ms | linear |
| cap dim, undim | opacity | 70–90ms | linear |
| latch underline | opacity | 120ms | linear |

The exit animation must run **after** the character has already been committed — it is confirmation, never a gate on input. In the prototype the exiting popup is cloned into a short-lived list and torn down after 145ms so a new press on the same key can start immediately.

`prefers-reduced-motion: reduce` collapses every duration above to 1ms. The popup still appears and still tracks the swipe; it just does not animate.

## Design tokens

**Colour comes from the Android system Material theme — do not hard-code the hex values below.** Resolve every colour from the active `MaterialTheme.colorScheme` (Material 3 / dynamic colour), so the keyboard tracks the user's wallpaper and light/dark setting like the rest of the app. The hex column records only what the prototype rendered, under the dark amber theme visible in the source screenshot; it is there to show the intended *relationship* between roles, not as a palette to copy.

| role | prototype value | used for |
|---|---|---|
| `primary` | `#e9b308` | halo, target ring glyph, centre glyph stroke once targeted, latch underline |
| `primary` (container fill) | `#e0b420` | Enter key fill |
| `onPrimary` | `#1a160c` | Enter key glyph |
| `onSurface` | `#f2efe8` | cap base glyph |
| `onSurface` @ 95% | `rgba(246,242,232,0.95)` | centre glyph outline at rest |
| `onSurfaceVariant` @ 60% | `rgba(240,236,226,0.6)` | ring glyph idle |
| `onSurfaceVariant` @ 45% | `rgba(240,236,226,0.45)` | modifier sub-label |
| `onSurfaceVariant` @ 40% | `rgba(240,236,226,0.4)` | cap corner alternates |
| `surfaceContainerHigh` | `rgba(255,255,255,0.055)` over the ground | keycap fill |
| `outlineVariant` | `rgba(255,255,255,0.07)` | keycap border |
| `onSurface` @ 10% | `rgba(240,236,226,0.1)` | pressed-cap overlay (state layer) |
| `surfaceContainerLowest` @ 72% | `#0b0a08` at 0.72 | surface-wide dim while a popup is open |
| `surfaceContainerLowest` → `surface` | `linear-gradient(180deg, #171512 0%, #100f0c 46%, #0b0a08 100%)` | keyboard ground |

The alphas above are part of the design and should survive the swap — apply them on top of the resolved role colour (Compose: `color.copy(alpha = …)`), the same way Material state layers work. The pressed-cap overlay is exactly a Material state layer and can use the platform's pressed-state layer opacity instead of a literal 10% if that is already wired up.

Two constraints to hold on to when the theme changes:

- **The halo and the target glyph must both read as `primary`.** If dynamic colour lands on a low-chroma primary, raise the `glow` multiplier rather than substituting a different hue — the amber in the prototype is not special, the *single accent* is.
- **The floating glyph has no background of its own.** Its legibility rests entirely on the halo plus the pressed-state dimming of the keyboard beneath it. On a light Material theme, invert the relationship (dark stroke, `primary` halo at lower alpha) and re-check contrast against the terminal output behind the top row, which is the worst case.

| scale | values |
|---|---|
| radii | 9px (cap), 50% (halo) |
| spacing | 3, 4, 6, 7, 9, 13, 20 px |
| type | 8, 8.5, 11, 13, 14, 17, 22, 30, 46 px |

## Tunable parameters

Three values are exposed as props in the prototype and are worth keeping configurable during device tuning:

| name | default | range | effect |
|---|---|---|---|
| `accent` | `#e9b308` | — | stands in for `colorScheme.primary`; drives halo, target glyph, latch underline |
| `ringRadius` | `34` | 28–60 | orbit radius; also drives the anchor rise and clamps |
| `glow` | `1` | 0–1.6 | multiplier on every halo and glow alpha |

## Keyboard layout used in the prototype

Row 1 — `q(NE 1, SW Esc) w(NW ~, NE 2, SW @) e(NW !, NE 3, SW #) r(NE 4, SW $) t(NE 5, SW %) y(NE 6, SW ^) u(NE 7, SW &) i(NE 8, SW *) o(NE 9, SW `(`) p(NE 0, SW `)`)`

Row 2 — `Tab(1.4×) a(NW backtick) s d f g(N -, SW _) h(N =, SW +) j(SW {, SE }) k(SW [, SE ]) l(N |, SW \)`

Row 3 — `Shift(1.5×, modifier, N Caps) z x c(N <, SW .) v(N >, SW ,) b(N ?, SW /) n(N :, SW ;) m(N ", SW ') Backspace(1.6×, NE ⌦)`

Row 4 — `Ctrl(1.7×, modifier, SW 123) Alt(1.7×, modifier, NW Fn) ←(N Home) Space(3×, N ⌕) ↑ ↓ →(N End) Enter(1.8×, accent)`

Multipliers are flex-grow weights within the row; unmarked keys are 1×. `s`, `d`, `f`, `z`, `x`, `↑`, `↓`, `Enter` intentionally carry no alternates — they exercise the "no ring at all" case.

This layout mirrors the current keyboard as shipped, apart from two cut/paste icon keys on `x` and `c` that were dropped rather than approximated.

## Not yet decided

**Repeat-on-hold** for Backspace and the arrow cluster is not modelled. The open question is whether the popup pulses once per repeat, shows a repeat count, or stays completely static while the key auto-repeats. Worth resolving before implementation, since it affects whether the popup needs a timer of its own.

## Files

| file | what it is |
|---|---|
| `Halo Float Popup.dc.html` | the interactive prototype — open in a browser, press and drag any key |
| `support.js` | runtime the prototype loads; keep it beside the HTML or the page will not render |
| `current-keyboard-reference.jpeg` | screenshot of the keyboard as it ships today, for the before/after comparison |
| `README.md` | this document |
