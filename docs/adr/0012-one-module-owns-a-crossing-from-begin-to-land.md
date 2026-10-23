---
status: proposed
date: 2026-10-23
amends: 0003's amendment (the dock and keyboard travel as transforms of the wall's live offset), by naming who owns that travel; 0011's "a slide is an input", by making the slide's dress a morph
---

# One module owns a crossing from begin to land, and the dock's dress morphs with the wall

A slide between Home and the Workspace still did not read as one launcher. A consistency audit on
the emulator (2026-10-23) found the landing changing things: a short blurred flash at the top of
the pane as the last band settled, Strip's clock and tile leaving in the last frames with the pane
shortening after, and the 118 to 143 px icon snap measured on the phone for #133. The cause is
that acting on a **Crossing** is spread over `TermuxActivity`: 23 travel fields, a travel cover
pushed every frame to whichever window is in front and cleared at two settle sites, and chrome
predicates that re-read the current layout mid-slide (46 reads, 17 one-line policy predicates, 34
call sites of the rounded-dock check). Callers could therefore see different arrangements within
one crossing, and a settle had to fix up whatever the frames had left half-applied. ADR 0011 gave
the dock's measurements one owner, but each half still wore its own **Dress** until the landing.

We decided that a crossing (the wall's one slide between Home and the Workspace, from the first
moving frame to the landing) is owned by one module from begin to land. It builds on 0003, whose
amendment lets the dock and keyboard travel as transforms of the wall's live offset, on 0007, where
one pure model answers the chrome's shapes, on 0009, where one chrome stands across the wall, and
on 0011 (the dock's measurements), whose Dock geometry the module asks.

1. **One owner.** The activity hands the module the wall's offset per frame and nothing else acts
   on a crossing. Its adapter interface is only what moves: the stack, the bar, the cover and the
   keyboard.
2. **Held arrangements.** On begin it takes both halves' resolved arrangements (where every Element
   stands and its shape, resolved once per page and orientation, #141) and holds them for the
   crossing's life, so no caller reads a changing layout mid-slide.
3. **The dress morphs.** Every frame the dock's dress goes from the leaving half's to the arriving
   half's in step with the wall's offset, over the whole slide: icon size, row width, bands, Search
   field and Drawer tile interpolate, and bands fade out and in. The last frame already is the
   arriving dress, so the landing changes nothing. Dock geometry is asked for both halves'
   DockLayouts and the module interpolates them.
4. **The keyboard travels with the half that has it open.**
5. **Retargeting.** A reversal, or a window chip tapped mid-slide, retargets from the current offset
   with no jump. The crossing ends only when the wall lands.
6. **Land clears in one step.** The content hold, the keyboard pre-roll and the travel cover are
   cleared together, and the arriving half's arrangement goes back to the activity. The travel
   cover is hold state that the pane controller applies to the window in front, so a switch
   mid-slide cannot leave it behind. `SeamCrossing` stays the tested answerer of what each frame
   shows.

Considered options, all rejected by the developer: **each half wearing its own dress all the way**
and changing at the landing (the snap and flash above); **two docks crossfading by opacity** (two
sets of rows overlapping, and a dock that is no longer one dock, against 0009); **a snap at the
midpoint** (still a jump, and a slow drag shows it).

Consequences: this is hard to reverse, because every chrome reader moves behind the module and the
activity's travel fields, settle sites and re-reads go; going back would mean putting each one back.
It costs a per-frame interpolation of two DockLayouts, and Dock geometry is asked for both halves
instead of one. It rules out travel-specific paths in the activity and a settle that "fixes up":
whatever a frame needs is the module's answer, and the landing has nothing left to change. Sizes at
rest, the Looks and One layout are unchanged. A scene's wallpaper surface is left alone. The spec
is #139; this ticket is #140.

Related: docs/adr holds two accepted ADRs numbered 0011, `0011-one-owner-for-the-docks-measurements.md`
and `0011-the-launcher-can-wear-a-wallpaper-of-its-own.md` (the second was ported from 1.3.0-pre.2,
where it is 0009). Neither is renamed or rewritten; cite them by file name. There is no ADR index to
carry this note.
