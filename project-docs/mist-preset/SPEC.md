# Mist preset (Obsidian glass and motion)

Mist (preset id `frost`) is retuned to Obsidian-Music's glass look and motion. Values come from
`OBSIDIAN-VALUES.md` (decompiled from Obsidian-Music 2.5.1, Apache-2.0; attribution is in that file
and in the code comments of `SurfacePresets`, `GlassLook` and `GlassMotion`).

## What a preset carries now

Format version 2. Everything from version 1 (dock style, material point, Base blur / opacity /
grain / radius / side gap, terminal radius and gap, detached per-surface cells), plus three
enum-like string prefs. Every preset states all three, so applying one always resets the last one's
choice; an unknown value reads as the default.

| Pref key | Values (default first) | Meaning |
|---|---|---|
| `surface_glass_tint` | `scheme`, `obsidian` | Where the glass tint colour comes from |
| `surface_glass_rim` | `hairline`, `gradient` | The 1dp containing stroke |
| `surface_glass_motion` | `classic`, `mist` | How glass cards arrive, leave and dim their backdrop |

A stored Custom look from before version 2 has none of the keys; it reads back as
`scheme` / `hairline` / `classic`, so applying it restores the shipped look of those three.

Classic, Slate and Bare write the defaults, so they look and move exactly as before. Mist's Base
blur / opacity / grain are named in its extras rather than computed from its material point (its
triple is off every curve), so the material control reads as a hand-tuned look after applying it.

## Where each value is drawn

- Tint and rim: `GlassSurfaceFactory` reads the look (`GlassLook`) once, so the dock, the keyboard,
  the under-keyboard card, the status bar and the dock capsule's outline follow it together. The
  panes take the tint through `paneGlassTintColor` (`GlassLook.flatTint`). The preset tiles draw
  from the preset's own look (`GlassSurfaceFactory.withLook`).
- Motion: `GlassMotion` (pure values, `CLASSIC` and `MIST`), played by `GlassMotionPlayer`. The
  terminal sheet's centred card and its drawer scrim use it.

## Obsidian to ours

| Obsidian | Ours | Note |
|---|---|---|
| Blur 25dp | Base blur 25 | Same unit (dp) |
| Dark background #161822 @ 0.60 | tint `obsidian`, opacity 60 | Colour from `GlassLook`; opacity is the Base slider |
| Light background white @ 0.15 | tint `obsidian` on a light scheme: white at the user's opacity | The slider keeps its meaning; 0.15 is not forced |
| Tint white @ 0.05 | Flat white 13/255 wash replacing the sheen and foot | `GlassLook.WASH_ALPHA` |
| Noise 0.08 | Base grain 8 | See below |
| Rim 1dp gradient white 0.25 to 0.03, diagonal | `gradient` rim, `GradientRimDrawable` | Top-left to bottom-right |
| Dialog radius about 28dp | Base radius 28 | Dock stays rounded, side gap stays 14 |
| Press 0.92, spring 0.75/400, release 0.55/300 | `pressScale`, `pressSpring`, `releaseSpring` | Values held, no tile plays them yet |
| Dialog enter scale 0.9 to 1, spring 0.8/400 | `enterScaleFrom`, `enterScaleSpring` | Springs solved analytically |
| Dialog enter blur 20 to 0, spring 0.82/400 | `enterBlurFromDp`, `enterBlurSpring` | `RenderEffect`, Android 12+ only |
| Dialog alpha in 280ms, out 200ms | `enterAlphaMs`, `exitAlphaMs` | Fast-out-slow-in in, fast-out-linear-in out |
| Backdrop black 0.55 | `backdropDim` | Sheet drawer scrim; classic is today's 71/255 |

Grain: our layer is full-contrast random alpha drawn at `percent / 100 * 60` of 255, while Haze lays
a soft tile at 0.08 alpha. Matching the alpha literally would be about 68, which reads as sand
rather than as Obsidian's faint tooth, so 8 is used as the visually equivalent value.

## Classic constants

`GlassMotion.CLASSIC` is the terminal sheet card as it always ran: 170 ms in from 0.94, 110 ms out to
0.94, scrim 71/255. Nothing else that animates is a glass surface with numbers of its own, so those
are the only classic values that exist.

## Left out, and why

- Dialog window blur 80 (`setBackgroundBlurRadius`): the app has no dialog windows for glass.
- Content-reveal, controls-enter, nav slide and the spring tokens: they animate content, not glass
  surfaces, and nothing here shares them.
- Shadow on FABs, and the no-blur fallback colours: no equivalent surface, and the fallback is
  already the solid material.
- Press feedback on tiles: the values and springs are in `GlassMotion`, but no glass surface here
  is a tappable tile that could share one press helper without inventing an animation.
- Editor card and pill reveal (180 ms, settle curve) and `PageSink` / `PlankTilt` page physics: not
  glass arrival animations of the same kind, kept as they are.
- A pane rim: panes are drawn tint and grain only today, so Mist's gradient rim reaches the dock,
  keyboard and status surfaces but not the panes.
- Ink measurement: the status bar's readability veil still measures the light model's sheen, which
  Obsidian tint replaces with a flat wash, so the measurement is slightly conservative there.
