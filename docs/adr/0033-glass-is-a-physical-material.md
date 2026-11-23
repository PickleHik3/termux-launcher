---
status: accepted
date: 2026-11-22
amends: 0031 (the one look: its glass values become physical parameters; Grain, Rim, Edge and Vibrancy leave the editor and are set by the presets)
---

# Glass is a physical material; its controls scale physical parameters

Research: [`docs/research/glass-realism-2026-11-22.md`](../research/glass-realism-2026-11-22.md).
Spec: [`project-docs/active/glass-physical-material-2026-11-22.md`](../../project-docs/active/glass-physical-material-2026-11-22.md).

Our glass reads as a graphics effect, not as glass. On pong the developer pointed at three
things: widget rims that look stitched together (corners that do not match the straight edges),
thick, soft, saturated "neon" edge bands, and slow swipes that look artificial. The research
traced each to a stylisation and the compensations built around it:

- Built-in widgets draw two rims: the shader's light, and a stroke drawn over it
  (`BuiltinWidgetView` sets the slab's rim). The shader turns its light round a corner 1.5× the
  drawn radius, and puts the colour split only at the corners (`cornerWeight`).
- The rim multiplies the backdrop's own colour by up to `1 + 1.6 × 2.25`, in gamma space, over
  the blurred frost, and still shows about 20% at 4 dp, about 10–15 px on pong (iOS fades within
  2–6 px).
- Slide moves a pane only sideways, which leaves real glass unchanged; ours thickens its
  leading band and adds a glint with speed (`GlassMotionLight`).

About 35 pieces of code (listed in the research, §5) exist to make that multiplied, blurred rim
behave: the gain, its tint boost, clipping holds, the pale-backdrop cut, the grey lift, the bold
fringe, parity filters that re-add baked colours. Each new complaint so far has added another one.

## Decision

1. **Glass is modelled as a physical slab.** One rounded-box signed distance gives the slab's
   outline, its bevel or lens profile, its normal and its rim, the same everywhere: no per-edge
   or per-corner logic. Refraction is Snell's law through the slab's thickness, light is a fixed
   room light computed in linear light, output is premultiplied, and the edge samples the sharp
   frame. The rim is drawn once, by the shader, matched to the view's clip outline.
2. **The realistic look is the default on every glass surface** (panes, widgets, dock, keyboard,
   sheets, bars, chips). Small pieces get a lens proportional to their size.
3. **The controls are physical parameters, scaled past realism for a dramatic look.** Frost
   (surface roughness) and Opacity stay per surface; Depth (thickness and lens) and Shine
   (light intensity, with a little dispersion at the high end) are app-wide. Presets (Clear,
   Frosted, Tinted, Solid, Liquid, plus Custom) set everything else. No new sliders: a normal
   user gets three glass controls.
4. **Motion comes from the page's pose, not its speed.** A translation changes nothing in the
   glass; a turn, tilt or scale changes it the way it would change real glass. Reduced motion
   and Animation speed Off keep the glass flat. No sensors: the room light is fixed.

## Consequences

- The compensations for the stylised rim go with it (research §5); the capture pipeline, its
  guards and the SurfaceView recording rule stay (research §5.3, ADR 0029).
- Existing users' settings are mapped to the nearest physical value; nothing is reset.
- Living glass (the speed spring) is removed; the Settings toggle goes.
- A new look complaint is answered by a physical parameter or a preset, not by a new gain or an
  overlay. A request for a stylised look is a slider end or the Liquid preset.
- Default numbers are tuned by eye on pong and frozen in one pure parameter class with tests.

## Amendment 2026-11-23: lit by its surroundings

Decisions 1 and 4 said the glass is lit by a fixed room light. Tuning on pong, the developer
turned that light off everywhere (the lens zone's specular drew "a thick milky line under the top
border of every glass surface"; the rim's lit side and the turn's glint went with it) and asked
for the light to come from the wallpaper instead: "I don't like the global light shimmering from
a set place. Is it possible to have the light dependent on the wallpaper? Like the bright areas
with colours just come into the glass instead of an invisible white light."

- **The glass has no fixed light.** It is lit by its surroundings: the lens zone reflects the
  blurred wallpaper just outside its outline (one tap at the pixel mirrored out across the
  outline), weighted by Schlick Fresnel from the slab's normal, in linear light, added and never
  multiplied. A bright, colourful patch next to a surface brings its colour into that edge; a
  dark one brings nothing. A faint constant sky tone stays as a floor.
- **The rim is an even line**: its baseline white and dark line, the same on every side. A
  finger still lights it.
- **Under a turn** the reflected patch follows the slab's normal, so the edge going away sees
  further out; there is no glint.
- **Shine** scales how much of the wallpaper's light shows in the glass.
- The fixed light's terms stay as debug tuning keys that ship at 0.
