---
status: accepted
date: 2026-10-24
amends: 0003 (one look for every place) and 0009 (one chrome across the wall), by letting the two halves hold different look values while staying one chrome
---

# Each half of the wall wears its own Look

ADR 0003 gave the launcher one Look for every place, and ADR 0009 one chrome across the wall. The
chrome unification (#84) then linked the dock's and status bar's look values across both halves.
On 2026-10-24 the developer darkened the Workspace to read the terminal more easily and found Home
darkened with it. A home screen and a working screen have different jobs: Home wants to show the
wallpaper, the Workspace wants contrast behind text. One value cannot serve both.

We decided that look values are held per half of the wall. The Look slider, and the opacity,
tint, blur and grain of the dock, status bar and keyboard, may each differ between Home and the
Workspace. Corner radius and margin are shapes (0007, 0011) and stay shared. The terminal is only on the Workspace and Home's own look only on
Home, so they need no half.

1. **One chrome, two dresses.** The dock, status bar and keyboard stay one element each (0009).
   Their look is part of the Dress, and the Dress already morphs with the wall's offset over a
   crossing (0012 §3), so a differing opacity or tint crosses the same way as a differing icon size:
   glass is rebuilt as drawables, so the stack changes look while it dips rather than easing per
   frame, and the status bar changes at the landing.
2. **Equal until touched.** Every install, new or upgraded, starts with both halves equal: the old
   single value becomes the value of both. A user who never edits on one half never sees a
   difference.
3. **Edit what you see.** The editor writes to the half its frame shows. There is no half picker.
   A card menu offers a one-tap copy to the other half.
4. **Shapes stay shared.** Where an element stands and how big it is remain per orientation and
   per layout variant (0001, 0011); only look values gain a half.

Consequences: the inheritance resolver takes the half; callers that draw chrome ask for the half
they draw; the crossing interpolates look values alongside the dress. Presets save both halves.
