---
status: accepted
date: 2026-11-17
amends: 0021 §1 (a fourth kind, Compositor; kinds change only on a policy event) and §2b (the rest and layers switch goes: a Live surface always draws its backdrop layers; "See through: One layer" is removed), 0014 (the legibility correction is measured at rest and kept per page, never measured in a crossing)
accepts: 0018 §1 for glass (no twins; the foot is the slab's own bottom). 0018 itself stays proposed.
---

# Each glass surface keeps one picture for its life

Decided on the map [Map: one writer and one frame order for the launcher's window](https://github.com/PickleHik3/termux-launcher/issues/236), in [The glass service: registry, backdrop source and commit](https://github.com/PickleHik3/termux-launcher/issues/242) and [How the look blends when two pages wear different glass](https://github.com/PickleHik3/termux-launcher/issues/239). It builds on [ADR 0028](0028-one-frame-pipeline-a-view-property-has-one-writer.md), whose frame order the glass commit is the last step of.

On 2026-11-17 glass had eight mechanisms and no registry: the stored frame aimed per draw (status
bar, window bar, sheet bars), the pane frame aim, Live pictures (dock, floats, widgets), panel
pictures (drawer, menus, palette, sheet), refraction effects, the Display keyboard's frost, the
decor strip under the gesture pill, and the tint sampler. ADR 0021 §2b let a Live surface switch
between one recording at rest and backdrop layers while moving.

[A prototype on pong](https://github.com/PickleHik3/termux-launcher/issues/241) tried one shared
glass clock, with and without, over six Home 2 and Terminal crossings, with and without a float
open. Every step appeared identically either way. The pops came from surfaces changing picture
after the landing: the dock slab swapped from the picture frozen at the swipe's start to the live
one about 80 ms after a Terminal to Home landing, and a float built, dropped and rebuilt its page
layer on one crossing. Timing was never the cause.

We decided the following.

## 1. One service, one registry

Every glass surface registers one entry:

| Part | Meaning |
|---|---|
| element | which look cells it reads, through the one element-glass path |
| view | the view it draws in; its place is read each frame |
| wants | Wallpaper, Snapshot, Live or Compositor |
| priority | budget order: dock, then floats, then widgets |

The service owns the granted kind, the layers, refresh, commit order and release. A surface decides
nothing about timing; a special case becomes a rule inside the service. The commit is step 4 of the
one pre-draw hook, walking the registry back to front in the window's layer order. The "one clock"
is the frame snapshot's wall phase and offset; no surface asks the wall whether it moves. The
refraction effects stay the effect builder, not a source.

## 2. Four kinds

Wallpaper (the stored pre-blurred frame), Snapshot, Live, and **Compositor**: the keyboard's frost
over the X11 screen on the Display page, which the recorder cannot draw. The service grants
Compositor or falls back to Wallpaper; its show, release and failure follow the same policy events
and guards as the others.

## 3. One picture per surface for its life

A Live surface always draws its backdrop layers (ground, pages, local). At rest they are the same
layers with nothing displaced. A landing has nothing to swap, so the pop goes by structure, and a
float never rebuilds its layer mid-swipe. The rest and layers switch and each picture's own stale
test go; each layer keeps its own refresh trigger. Soft edges of translucent content may differ
from one blurred composite at rest, as 0021 §2b already accepted while moving.

Amendment, 2026-11-18. The pages layer behind a pane's or surface's glass is frozen at rest too:
it is held, moved by the pages' travel, and never swapped for live pages when the wall starts or
stops. Only the first capture of a surface (start-up) draws the pages live until it lands. It is
taken again when the content behind it changes:

- The wall reports a content generation: any invalidation under its pages (not while it moves,
  when the pages move by their poses and the layer follows them).
- A surface retakes its layer at most once per 250 ms (`PageLayerRefresh.REFRESH_INTERVAL_MS`),
  and a change inside the interval is taken as soon as it ends, so the last change is always
  seen. Nothing changed means nothing is captured: no work at rest.
- The new picture replaces the old one when its capture lands; the old one is drawn until then,
  so no frame has an empty layer. A surface holds one picture and has one in flight, both counted
  against its pages budget (below).
- A layer left off where it was taken (pages that moved by a shift) is retaken once the wall
  rests, so the next swipe starts from a layer that matches.

Whole-page slots, same day. Each page is held whole and untransformed, in a slot of its own (its
own bounds and the blur's reach around them, at a quarter scale), and the wall's decor in one
more. Where a page stands is its pose's matrix, drawn over the slot whenever the layer is drawn,
so a transition that turns pages (Carousel, Depth, Stack, a sink) redraws a handful of bitmaps
and neither captures again nor falls back to live pages at its first frame; a slide is still the
layer's translation alone, and a fade only changes the slots' alphas. A new capture is for a new
content or a different set of pages, and happens at rest.

One pool, 2026-11-18 (replaces a budget of 4.4 MiB an atlas and twice that a surface, 79 MiB in the
worst case). The pages layers of every surface in the window share one pool, `GlassPagePool`, sized
by the phone's RAM and read once per process (`ActivityManager.getMemoryInfo` and
`isLowRamDevice`). Pages are always frozen on the wall in a crossing, on every phone; the tier sets
only how sharply:

| RAM | Pages captured at | Pool | Arithmetic (nine slots: three surfaces, three 1080 x 2400 pages, 16 px gutter) |
|---|---|---|---|
| more than 4 GiB and not a low-RAM device | a quarter | 6 MiB = 6,291,456 B | 278 x 608 x 4 = 676,096 B a slot, 9 slots 6,084,864 B |
| 4 GiB or less, or a low-RAM device | an eighth | 2 MiB = 2,097,152 B | 139 x 304 x 4 = 169,024 B a slot, 9 slots 1,521,216 B |

The live blur node keeps its own quarter scale; only the pages layer follows the tier. The worst
case in all is eight blur layers and the pool, 8 x 1,152,000 + 6,291,456 = 15,507,456 B
(`LiveBackdropBudgetTest`).

- **Priority.** While the wall moves, surfaces whose rect overlaps the wall are served the pool
  first (`GlassPagePool.order`). A capture counts its atlas against the pool from its start; the
  held picture of the same surface stays beside it while both fit, else it goes first. A surface
  the pool has no room for that frame keeps the picture it last held, without recording again; one
  holding none draws live.
- **Trim.** At `TRIM_MEMORY_RUNNING_LOW` and above, which includes the UI hiding and the process
  moving to the background, every surface lets go of its pages pictures and the pool is empty. The
  next crossing takes one capture per surface at its start and rides it through.
- **The effect runs live.** A slot is captured without the blur, colours or frost. Those are the
  page node's render effect, set where the frozen atlas is drawn, so the blur runs every frame on
  the moving picture and a change of radius, tint or frost reaches the frozen layer at once. Only
  the pages' content is a still, at most one refresh old. The refraction shader is drawn over the
  picture at draw time and is never recorded into a capture.
- **Live blur off** holds no pages layer at all (the surfaces draw their stored frame); the
  setting stays the low-memory path.

A surface with more than three pages near it, or whose pages do not fit the pool, keeps the pages
live.

## 4. Kinds change only on a policy event

A surface asks for a kind. The service grants it from the Live blur policy and the slot budget at
registration, and re-grants only when a policy input changes: the Live blur switch, battery saver,
a memory trim or a slot freeing. The change applies at once, even mid-swipe, and is counted by the
guards. Motion and landings never change a kind.

What sits above changes a kind in one case only: a sheet at rest covers the surfaces it hides
wholly (on a lower layer, and inside the sheet's rect or not shown), and a covered surface is
granted Wallpaper until the sheet leaves rest. That frees the Live slots for what can be seen, so an
open drawer and its search capsule always get Live (amended 2026-11-22 at the developer's decision;
`GlassCover`, `GlassRegistry.setCovered`).

## 5. No twins

One entry per element, drawn by the element's own view. The registry refuses a second entry for an
element. The status bar's and window bar's wallpaper twins move into the bars with the top of the
window ([ADR 0030](0030-pages-sides-and-the-bottom-stack.md) section 6).

## 6. The bottom edge

Every glass surface whose resting edge is the screen's bottom draws its own glass to the physical
edge, under the gesture pill: the Docked stack slab (keyboard up or down), the long-press menus on
Home and the terminal, the More menu, the app drawer and the command palette. Its rim runs along the
top and sides only, so there is no line above the pill; in Docked with the keyboard up the
keyboard's borders are that rim. The foot is the slab's own bottom, and the decor nav strip is
deleted. Floating keeps its own geometry: its card floats, and only Peek under Floating reaches the
edge. Each surface has a pong screenshot check in light and dark.

## 7. See through is removed

"See through: One layer" and its row in Battery & performance go. What sits above a surface never
changes its picture, apart from a sheet at rest covering it wholly (section 4). The 8-slot budget
and layers-always bound the cost.

## 8. How shared surfaces blend

A surface both ends of a crossing share (status bar, dock slab, keyboard, the strip under the pill)
moves from the leaving look to the arriving look on one curve of the crossing's progress. A surface
only one end has (pane slabs, widgets, floats, a page's wallpaper dim) keeps its look and travels
with its page; it never blends.

- Opacity, tint, grain, veil and the solid fill blend as numbers. Frost is a crossfade of the two
  blurred frames, not a radius. Rim style and Fancier Glass crossfade.
- Ink blends with the glass between pale and dark on the same curve, so legends never flip.
  Contrast is lowest around the middle of a crossing between a light and a dark look; that is accepted.
- The status bar stops switching its look at the seam.

## 9. Legibility corrections

The service keeps one correction per page and band (dock, keyboard, strip, panes), measured at the
page's rest position. It is measured again only when the wallpaper, the palette, light or dark, or
the band's rest rectangle changes; over a live scene it is taken from the scene's frame at rest and
never chased frame by frame. Keying on the page covers parallax. A crossing blends two stored values
and measures nothing; a page never yet visited is measured once before its first crossing. The tint
sampler becomes this store. The status bar and window bar get no correction in this restructure.

## 10. Join order

1. The registry, with Live at its core, layers always.
2. Terminal pane slabs read the frame snapshot instead of the activity's push.
3. Panels (drawer, menus, palette, sheet) register as Snapshot or Live. Done 2026-11-22: the
   drawer, palette and sheet draw layered Live glass; menus, in their own window, keep a registered
   Snapshot. The one-picture branch is deleted, and `LayeredGlassSourceRulesTest` keeps it out.
4. The stored-frame bars register; their twins go with the top of the window.
5. The foot: the slab extends under the pill and the decor strip is deleted.

## Considered options

- **Keep the rest and layers switch and hold the layers longer after a landing.** Rejected: the two
  pictures share one record node, so holding both would draw everything twice unless each had its
  own record and blur node, which costs memory.
- **Blend frost as a radius.** Rejected: a crossfade of two blurred frames is what the eye reads as
  smooth; the cost is two frames drawn while the frost differs.
- **Measure the legibility correction at the start of a crossing.** Rejected: it holds the swipe
  for a measurement; rest-time values blended need no hold.

## Consequences

- A float's glass and the dock's draw the same layers at rest and moving, so the first frame of a
  swipe and the landed frame are the same picture.
- Users lose the "One layer" setting; the default behaviour (All layers) stays.
- Memory is bounded: at most 8 live surfaces by priority, each a blur layer and, beneath the
  wall, a pages layer (`LiveBackdrop.BUDGET_BYTES`, the worst case; see the 2026-11-18 amendment).
- Glass that must be on screen the frame it is asked for (the keyboard) draws what the service
  already holds; if the stored frame is not resident, it shows its frost or tint for a frame or two.
