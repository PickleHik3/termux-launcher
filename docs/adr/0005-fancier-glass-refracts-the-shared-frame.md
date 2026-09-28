---
status: accepted
date: 2026-09-28
amends: 0004 (the surfaces that sample the shared frame may draw it through one AGSL refraction)
---

# Fancier Glass draws the shared frame through one AGSL refraction, as the paint shader

ADR 0004 left every glass surface sampling the one pre-blurred wallpaper frame at its live
screen position, through a `BitmapShader` aimed per draw. The dock and the under-pill strip
alone carried a second effect on top: an AGSL program (`GLASS_AGSL`, API 33+) applied as a
`RenderEffect` over their backdrop views, bending the frame under the rim, lighting the rim and
carrying the key-press lens. Fancier Glass (`project-docs/fancier-glass/SPEC.md`) asks for that
refraction on every glass surface, behind one opt-in switch, with three global knobs.

We decided on one program, `chrome/GlassRefraction`, with two ways in, and one rule for when it
runs.

**The program is the dock's, moved, with the aim as uniforms.** The AGSL is the same shader
with one change: the content is sampled at `(sampleCoord - uFrameOffset) / uFrameScale`. Under a
`RenderEffect` the content is the view's own pixels and the aim is identity, so the dock and the
strip draw exactly what they did — their numbers (20 dp band, 9 dp pull, 0.16 of light) are
pinned as `Look.DEFAULT` and the default mode never reads a knob. Everywhere else the program
is the surface's paint shader: the frame's `BitmapShader` is its child input, and the four
numbers `SharedFrameDrawable.aim` and the pane slab already compute — scale and translate — are
written as uniforms instead of into a matrix. The program then bends and lights the frame in the
one pass the plain draw already took. The alternative, a `RenderEffect` per surface, costs an
offscreen layer per glass surface per frame and refracts the tint and grain along with the
picture; those are the slab's own surface, not what is behind it, so they stay put.

**Registration is the plain draw's.** The aim is re-read on every draw from the surface's
`GlassAnchor` and the live parallax, as before, and written to the GPU only when it moved. A
slide, a keyboard travel or a pan therefore costs a uniform write per moved frame and no
allocation; a surface at rest costs nothing. Programs are compiled when a frame or the look
changes, never in a draw. Where two surfaces are one sheet of glass — the docked keyboard over
the under-pill strip, the status band over the window bar — the shared edge is a seam: the rim
rect is pushed out past the band, the pull and the corner arc on that side, so no line and no
bend shows through the sheet.

**One rule says when it runs.** `FancierGlassPolicy`: Android 13 (the first `RuntimeShader`),
the switch, and a wallpaper set from inside the launcher — the picker's stored system id is the
current one and its exact copy is on disk, the same test the blur source uses. Below Android 13
the switch is hidden; with any other wallpaper it is shown disabled with the hint; the switch
itself stays where the user left it. The activity resolves the rule once at the head of every
apply and of the panes' pass (`syncFancierGlassLook`); a moved answer marks every backdrop dirty
and drops the pane style key, so the switch and the knobs land on the next pass with no path of
their own. Lazy mode, battery saver and reduced motion are not in the rule: nothing here animates,
so there is nothing for them to stop.

**The knobs are global.** Bend, Edge width and Edge light stand on the Appearance editor's shared
layer, between Material and Shape, only while the rule holds; no surface has a row of its own.
Their defaults are the dock's numbers, so switching Fancier Glass on changes nothing about the
dock and only lends the other surfaces the same rim.

Consequences: `SharedFrameDrawable.setRefraction` and `PaneGlassBackdropView.setRefraction` take
a `Look`, a corner radius and the seam edges, and every pass restates them — a compare, not a
rebuild. `PaneSurfaceStyle.paneGlassRefraction` carries the look to the slabs and
`PaneStyleKey` compares it. The corner tab in `wall/` still draws the frame plain; it is a control,
not a slab. Video wallpapers, the unlock pairs, the motions and the parallax switch of the spec
are later slices and change nothing decided here.
