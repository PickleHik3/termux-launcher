---
status: accepted
date: 2026-09-27
amends: 0002 (the crop-holding surfaces now sample the frame too; the frame is held at blur resolution)
---

# Every glass surface samples the shared frame at its live position; the frame is held at blur resolution

ADR 0002 kept the wallpaper blurred once per radius into a frame wider than the screen and had
each glass surface shift where it samples that frame as the places slide. Its implementation note
left the dock, the keyboard, the under-pill strip, the top bars and the planes holding crops cut
from that frame — cut wider by the parallax's travel and drawn shifted — because the chrome reasoned
about those crops as `BitmapDrawable`s in several places. Measured on pong (2026-09-27), the crops
were still cut at every pre-roll and settle of a place slide, each one a main-thread bitmap the size
of the surface, cut at the surface's transformed position mid-travel and swapped in with no fade;
a wallpaper change composited a third bitmap per tick to crossfade two of them; and the blurred
frames were stored scaled back up to full size, so five radii of a 1.5×-wide frame (~15.6 MB each)
overran the 72 MB budget and evicted each other mid-session.

We decided that every glass surface samples the one shared frame through a shader aimed at the
surface's live screen position on every draw (`SharedFrameDrawable`), the way the pane slabs
already did, and that the blur cache holds each frame at the resolution it was blurred at — the
capture downsampled by the blur's factor, a quarter of the screen at the usual radii — scaling it
up as it is drawn. Nothing cuts a bitmap during a slide, a pre-roll or a settle; a wallpaper
change's replacement fades in by drawing both frames; the in-use scan that guards recycling asks
each drawable what it shows.

Where a surface is anchored is chosen by what moves it (`GlassAnchor`). The pane slabs and the
accessory stack use their laid-out position plus the transforms the launcher itself applies — the
wall page's slide, and the stack's travel and IME lift — so a plank's press still carries the frost
with it, as it always has, rather than re-aiming on whichever draw lands mid-press. The top bars,
the planes and the under-pill strip, which nothing tilts and whose owners re-apply them when they
move, use the framework's transform-inclusive position.

What a surface stacks over its frame — the frost, the refraction, the tint and grain, the rim, one
alpha over the whole — is built in one place, `chrome/GlassStack.build`, from a plain `Spec`. The
keyboard capsule and the under-keyboard card are calls to it with their own values, so they cannot
drift from the dock by a hand-written layer; the keyboard's blur and grain are cells of the
inheritance model like the dock's, following Base until detached.

The alternative was to keep the crops and cut them off the main thread. That leaves the mid-travel
displacement, the swap at settle and the rect bookkeeping in place, and still costs a bitmap per
surface per geometry change.

Consequences: the frame rect (`WallpaperBlurCache.frameRectRef()`) is the one statement of which
screen rect a frame stands for, whatever its pixel size, and every reader scales by it. The radius-0
frame — the wallpaper itself, drawn by the self-drawn backdrop — and any radius under the blur's
25 px downsample cap stay full size. The keyboard's material between a place that paints it as
glass and one that paints it as the opaque panel blends with the wall's travel
(`KeyboardMaterialPolicy.travelSolidness`) instead of swapping at settle. Fancier Glass, when it
comes, adds AGSL refraction over this same frame and a live backdrop for video; the default mode
is unchanged in look.
