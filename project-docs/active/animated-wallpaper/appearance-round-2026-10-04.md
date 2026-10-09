# Appearance round, 2026-10-04

The developer's requests after trying living stills on pong, with the decisions taken. Surveys of
the code are summarised where they decide the design.

## Requests

1. Delete the ten pre-made AGSL backgrounds (Drift, Mesh, Aurora, Tide, Rain, Contour, Lava, Silk,
   Caustics, Chrome) entirely. Living stills stay.
2. Moving between the wallpaper page and the Look / Layout editors stutters. Make it one unified
   menu with smooth transitions, in the code and in the app.
3. Remove the "Recent" heading; the bottom row shows recently applied wallpapers, evenly spaced.
4. Replace "Read again" with an AI star glyph (`ic_symbol_ai_star`).
5. The page heading becomes **Appearance**. The Home and Lock previews shrink a little so a small
   heading ("Home screen", "Lock screen") sits above each.
6. The icon pack button opens its own page: a preview of the home screen, under it a horizontally
   scrolling row of installed icon packs, under it a toggle "Pinned app icons only". Reference:
   Nothing OS icon pack page (`wall-alive/ref/Screenshot_20261004-092029.png`, outside the repo):
   title "Icon Pack", phone preview, round pack tiles with labels (Add, Default, packs), one toggle.
7. The corner tab's wallpaper glyph becomes the palette glyph, and the entry is **Appearance**
   everywhere (Settings row, corner tab, terminal menu), since it is no longer a wallpaper changer.
8. The previews on the Appearance page play the living still's real animation (today they draw the
   composite with a neutral effects map, so no water refraction or mist).
9. Rework the **Clear** Look: the absolute minimum dark tint on every surface; more aggressive blur,
   refraction and glass edges ("the most fancy glass"); and a **grain** setting on every surface.

## Why it stutters (survey)

The page is a separate full-screen `AppCompatDialog` window; the editors transform the activity's
own `terminal_root_container` and add their sheet to `android.R.id.content`. Each hop dismisses one
window and builds the other from scratch, with expensive work on the animation frames:

- picker dismiss releases every thumbnail and executor, restarts the backdrop renderer, and the
  activity's `onWindowFocusChanged` resyncs blur/backdrops and pane sizes;
- the editor decodes the wallpaper on a raw thread and swaps the window background mid-animation,
  raises the keyboard (terminal relayout, PTY resize), animates panel height with `setLayoutParams`
  per frame, and scales the whole live glass tree;
- on exit `onEditorClosed` fires synchronously, so the page is rebuilt one frame into the exit
  animation: dialog inflate, `WallpaperSlots.read` (binder calls), SHA-256 of the photo on the main
  thread (`LivingStills.find`), ten GPU thumbnail renders, a shader compile; then a deferred full
  re-glaze (`redressChrome`, `repaintAll`) lands on the page's first frames.

## Design

### One Appearance surface in the activity window

`AppearanceSurfaceController` owns one host view in `android.R.id.content`, registered once with
`OverlayRegistry` (Back goes to the previous page, then closes). Pages: **Overview** (the wallpaper
page), **Look**, **Layout**, **Icons**. The Overview view tree is built once per session and hidden,
not destroyed, while another page shows; thumbnails, the living-still job listener and the preview
shaders live for the session. Slow reads (`WallpaperSlots.read`, manifest lookup with its SHA-256,
recents) move off the main thread. The surface toggles `setCovered` once per session.

Transitions use the app's own motion (`Motion.settle`, `GlassMotion` springs, `ReducedMotion`):
Overview → Look/Layout: the strip, Motion row and shortcut row slide down and fade while the editor
sheet slides up; the Home card is the shared element: the launcher frame starts at the card's rect
and settles at the editor's 0.76 frame while the Overview background fades. The reverse plays on
the way back, and the page swap happens from the end action of the frame's hide, never one frame in.
The editor wallpaper decode starts when the surface opens; the keyboard rises after the transition
settles; panel height animates by translation/clip, not per-frame layout. Done in an editor returns
to Overview; the unsaved-changes question is asked when leaving the editor page.

### Overview page

- Fixed heading **Appearance**. Each preview card gets a small label above it ("Home screen",
  "Lock screen"); cards shrink enough to fit the label with the current fixed chrome at 360 dp.
- The bottom row: Same as Home (Lock only) then the recently applied photos (`RecentWallpapers`),
  evenly spaced across the row; no "Recent" badge or heading; no pre-made backgrounds.
- "Read again" becomes an icon button with `ic_symbol_ai_star` (content description "Read again").
- Preview cards play the living still with its effects map (a small per-card effects renderer at
  ÷4 of the card, or a shared one for the centred card only).

### Icons page

Title "Icon pack". A home-screen preview (the Home card with the user's pinned app icons drawn from
the selected pack in a row/grid), a horizontally scrolling row of round pack tiles (Default, then the
installed packs from `IconPackChoices.listing`, enumerated off the main thread; selected tile ringed),
and a toggle row **Pinned app icons only**: on writes the choice to `KEY_PINNED`
(`app_launcher_pinned_icon_pack_package`), off writes `KEY_GLOBAL` (`app_launcher_icon_pack_package`)
and clears the pinned override. Tapping a tile previews it at once and applies it.

### Entry

"Appearance" with the palette glyph (`CornerTabGlyphs.APPEARANCE`, nf-md-palette) on the corner tab,
the Settings row and the terminal menu. Help/tour copy updated.

### Removal of the pre-made backgrounds

Per the survey's checklist: drop `AnimatedWallpapers.ALL`, make ids living-only; delete the ten
classes, `AnimatedWallpaperStill`, `MomentAgsl.TAIL/assemble`; strip `GeneratedWallpaperApplier` and
`WallpaperThumbs` of the built-in paths; launcherctl drops `/v1/wallpaper/builtins` and rejects
`builtin`. Migration: a one-shot `WallpaperSlots.dropRetiredBackgrounds` clears a retired Home id
(the system still is already that background's PNG, kept as a photo) and heals a retired lock choice
(copy the Home picture to the lock when our live wallpaper holds it, otherwise record `photo`), so a
live lock screen never goes black. Docs rewritten around living stills.

### Clear and grain

Clear today: blur 3, opacity 16, grain 4, bend 4, edge width 10, edge light 18, scheme tint,
hairline rim, classic motion. Blur is RenderScript / `RenderEffect` (capped at 30 dp everywhere);
the AGSL program (`GlassRefraction`) only bends at the rim and draws a 2 dp hairline. Darkening
layers Clear does not reach: opacity floors (terminal sheet 0.92, chips 0.88), the Docked insert
floor (20% darker), the frost filter's -6 brightness, the light model's dark foot, Obsidian's wash.

New Clear: blur raised to the new maximum (cap 30 → 48 dp in every clamp; Clear 44), opacity 2 on
all surfaces (terminal canvas detached at 8 so text stays on something), grain 14, bend 28, edge
width 40, edge light 85, scheme tint, gradient rim, classic motion; under Clear the floors scale
with the surface's own opacity instead of fixed minimums, and the frost filter's brightness offset
is 0. The AGSL program gains a bevel/specular term along the rim normal (lit top-left, faint shade
bottom-right) and a small chromatic dispersion at the rim, as new `Look` fields with preference keys,
set by Looks (Clear high, others low or off), `SurfacePresets.FORMAT_VERSION` 3.

Grain: already stored and inherited for the four slots (status, canvas, keyboard, dock). Add a
grain slider to every surface's row in the Look editor; make every glass stack draw it (the
`tintColor` path skips it today; corner tabs and popup menus get it too). Static grain only, so the
"nothing redraws at rest" rule holds.
