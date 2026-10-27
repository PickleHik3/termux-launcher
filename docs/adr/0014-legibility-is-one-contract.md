---
status: accepted
date: 2026-10-27
supersedes: the 2026-10-10 rule in ChromeInk.veilCeiling255 ("every surface is exactly the glass its Look and sliders say; legibility is the ink's job")
---

# Legibility is one contract: a pair table, solved against a bounded ground

On 2026-10-25 a tester (Lamgerr) reported that termux-change-repo's mirror list was pale red on
light grey and barely visible. Reproduced on 2026-10-27: dialog fills its box with ANSI white
(slot 7) and draws red and blue on it. In our dark recipe, slots 1–6 and slot 7 are both at HCT
tone 80, so red on white is 1.00:1 in a unit test and 1.00–1.01:1 in pong's pixels. The
developer's own long-standing complaint, grey text lost on glass, has a second cause. The palette
is solved against one colour, the *mean* of the wallpaper under the pane, while the ground under
one pane on pong spans tone 24 to 67. Over the bright patch the grey reads 1.18:1 and body text
2.29:1.

Each fix so far moved one pair against one ground: grey per contrast level (410458fea), accents
against the background, the foreground against glass. Every pair nobody listed stayed unguarded,
and every ground the mean did not describe stayed unmeasured. So the same class of bug keeps
coming back.

We decided that legibility is a single contract with three parts.

1. **A pair table.** Every (ink, ground, floor) the terminal promises is listed in one place, and
   the palette solver holds every slot to every pair it appears in, in both recipes and at every
   Contrast level. The table is at kitty parity: the foreground, accents (1–6, 9–14) and grey (8)
   on the background; black, red and blue (0, 1, 4) on ANSI white at 3:1, which is what
   dialog and whiptail draw; white and bright white (7, 15) on ANSI black. Green and yellow on white are
   exempt, as they are in kitty, xterm and upstream Termux. The exported palettes
   (colors.properties, kitty, noctalia) are the same solved palette.
2. **A bounded ground.** Glass that carries text gets an *even-out* stage after blur and frost,
   before tint, opacity and grain. It narrows the wallpaper's lightness range around its own
   average, and moves the average only as far as the ink needs. Its strength comes from the
   Contrast level. The sampler reports the band's two ends, not a mean, and the palette solves
   against both. The ground can no longer fall outside what was solved for.
3. **One test grid.** Every pair, over a set of seed themes × Softer, Default and Harder × opaque
   and both glass band ends, fails with the pair and its ratio. A device card (a script that
   prints the pairs, measured from a screenshot) is the device check.

Consequences:

- **The sliders keep their meaning.** Blur, Grain, Tint and Opacity remain exactly what the user
  set. Even-out acts on the wallpaper beneath them and has less to do as Opacity rises, and
  nothing at 100%. This keeps what the 10-10 rule protected (no hidden veil, and the terminal no
  darker than the keyboard, since every text-bearing band takes the same stage) while removing
  "legibility is only the ink's job", which a mean-solved palette cannot honour on a wallpaper
  with both dark and bright patches.
- **Contrast is the one legibility knob.** It sets the floors and the even-out width. No slider is
  added.
- **The polarity flip stays.** Over a light wallpaper the band moves toward the light side and the
  palette takes the light recipe. Because both recipes hold the whole pair table, a flip cannot
  reintroduce the dialog bug.
- **Dark-mode accents get darker and more saturated** (around tone 46–50, close to kitty's red and
  blue) and ANSI white gets lighter (toward tone 88). Bright slots stay light.
- **Rejected:** a fixed dark band under every pane (darker than the sliders say), and a veil sized
  by the brightest patch (the hidden veil back, darkening the whole pane for one cloud). Both
  break the sliders' contract.
- **Fallback:** even-out runs as AGSL on Android 13+ and as a CPU pass over the cached pre-blurred
  frame below that, recomputed only when an input changes, so every supported Android gets the
  same guarantee. A Developer switch turns it off for comparison, and is removed after one
  release.

Spec: project-docs/active/legibility-contract-2026-10-27.md. Research:
project-docs/reference/terminal/material-terminal-palette-research-2026-10-27.md.

Amendment (2026-10-27): **Bare widgets are the one exception.** A widget the user set to Bare
draws nothing behind itself, so the contract cannot bound its ground. Its ink is still toned and
flipped against the wallpaper it stands on, but on a busy wallpaper it may fall short of the floor.
The user chose fully clear over the soft dim that used to keep it legible.
