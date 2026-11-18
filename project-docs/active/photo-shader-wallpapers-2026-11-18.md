# A moving wallpaper from a photo, written by an AI tool (2026-11-18)

Status: built on `v2-redesign` (renderer, depth maker, picker); device checks owed. The spec of
record is issue #277; this page is the short version with the picker as the developer decided it on
2026-11-18. Builds on [shader-video-wallpapers-2026-11-09.md](shader-video-wallpapers-2026-11-09.md)
(the GL ES 3 scene renderer and the Shader kind) and changes none of its decisions except "channels
1-3 are black".

## What it is

The Shader kind runs any pasted fragment shader, but a user who wants their photo to move has no
route to one. An AI tool the user already has writes the shader from the launcher's instructions;
the launcher runs it. The launcher does no AI work for the shader itself.

## Decisions

1. **Two named images.** `iChannel0` is the Photo, `iChannel1` its Depth image; channels 2 and 3
   stay black. The shader's settings gain an optional depth file name in the shader's own folder.
   Depth is capped at 1024 px, clamped to edge, no mipmaps. "No depth" is
   `iChannelResolution[1].x == 0.0`.
2. **Depth is made on the phone.** *Generate depth* under the Photo row runs one depth model once over the photo
   (`TaiDepth`, LiteRT), writes `made-depth.png` into the shader's folder and closes the model. The
   model downloads once, after a dialog that names the size, and is listed (and deletable) in the
   Model Centre as Wallpaper depth. A depth image can instead be picked by hand.
3. **Inputs.** `tlTouchAge` (seconds since the finger lifted; 0 while down; 1e6 before the first
   touch) is new. `tlPageOffset` (0..1 as pages slide) is now fed to the shader by
   `WallParallax`.
4. **The entry.** On the Shader page "New shader" and "From a photo" are two equal buttons in one
   row. No row or subtitle on the page.
5. **One page, no card.** "From a photo" opens the editor on a new shader in the photo flow (no bottom
   sheet; reworked 2026-11-18). Top to bottom for every way in: title row with Apply, Name, Quality,
   Frame rate; photo flow only: "Copy the instructions" [Copy, then Copied] with the line "Paste them
   into an AI tool with your photo."; the Photo row; the Depth area; Also set as phone wallpaper and
   the lock note; and last the shader (heading "Bring the result back" in the photo flow only, Code
   box, description, Paste | Import file, error strip, too-heavy note). "New shader" and a tile's
   pencil open the same page without the photo-flow extras.
6. **The Depth area.** Nothing about depth shows until a photo is chosen. Then "Generate depth" with
   "Choose depth image"; while it runs a status line with a thin bar and Cancel; when ready the
   thumbnail, "Depth: generated from the photo" and Remove; picked by hand shows the file name (and
   "May not match the new photo." after a photo change); failed shows one error line with Try again
   and Choose depth image. The states come from `ImageRowLogic.depth`, which returns null while the
   area is hidden. The picker returns without scrolling the page: the scroll content takes focus
   when a picker opens, so no text field takes it back.
7. **Photo changes.** Changing or removing the photo clears a depth made from it and keeps a picked
   one with "May not match the new photo." (a draft flag, not stored). A run in progress is
   cancelled when the editor closes, the draft is discarded, the photo changes or the host is
   released; Apply waits while one runs. A preview that is not kept leaves no files.
8. **The instructions** are a raw resource, `res/raw/photo_shader_instructions.txt`, English only; a
   test keeps every input they name declared by the renderer.

## Memory and frame cost

Photo texture worst case about 21 MB (unchanged), depth about 4 MB; bitmaps are released after
upload and the depth texture exists only while a shader that has one runs or previews. One more
texture bind per frame; the shader's own cost is held by the budget in the instructions and the
heavy-frame guard. Defaults stay half quality and 30 frames a second.

## Out of scope

Scene segmentation, the wallpaper reader, director and living stills (stay removed); a bundle or
share format; more than two images; feedback buffers; video as a shader input; the current Home
photo used automatically; translating the instructions.

## Every surface

Settings-like pages are fragile: only the button row, the card and the two editor rows changed.
Strings wrap and labels are single line with ellipsis at font scale 1.3; colours are theme
attributes, so light, dark, black, Material You and scheme chrome follow. Editions, keyboard, dock
styles and panes are not touched.

## Owed on a device

See `project-docs/device-checks.md` and `project-docs/developer-checks.md` (photo shader wallpapers,
2026-11-18).
