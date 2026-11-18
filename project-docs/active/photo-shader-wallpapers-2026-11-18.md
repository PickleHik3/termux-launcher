# A moving wallpaper from a photo, written by an AI tool (2026-11-18)

Status: spec, not built. Decided with the developer 2026-11-18. **The spec of record is issue #277**;
where it and this draft differ, the issue wins. The main difference: later the same day the
developer chose to make the depth image on the phone (a *Make depth* action running one small depth
model once, on the LiteRT backend), so every line below saying no inference runs on the device, or
that depth comes only from a picked file, is superseded. Builds on
[shader-video-wallpapers-2026-11-09.md](shader-video-wallpapers-2026-11-09.md) (the GL ES 3 scene
renderer and the Shader kind) and changes none of its decisions except "channels 1-3 are black".

## Problem

The Shader kind runs any pasted fragment shader, but a user who wants *their photo* to move has no
route to one. Asked cold, a chat AI either specifies a layered scene renderer nobody can paste
(separate leaf meshes, atlases, a scene file) or writes a procedural drawing that ignores the photo.
Both happened with the developer's lotus photo on 2026-11-18. The AI needs the launcher's contract
stated up front, and the shader needs the photo and, for parallax, its depth.

No inference runs on the device. The work is done by whatever AI tool the user already has.

## Decisions (developer, 2026-11-18)

1. **A steps card on the Shader page.** A row *Make one from a photo* sits between the saved
   shaders and Examples and opens a card with three steps: copy the instructions, paste them into
   an AI tool with the photo, bring the result back.
2. **Two named images: Photo and Depth.** `iChannel0` is the photo, `iChannel1` its depth map.
   Channels 2 and 3 stay black. No free slots.
3. **The answer comes back as pasted code and picked images.** No bundle file, no import format.

## Design

### The round trip

1. *Copy* puts the instructions (below) on the clipboard.
2. The user pastes them into an AI tool together with the photo.
3. *Paste shader* on the card opens the shader editor on a new shader holding the clipboard text,
   through the same path as the editor's own Paste (same checks, same error strip).
4. In the editor the user picks the photo, and optionally a depth image, then Apply. From there the
   flow is the existing one: preview, Done keeps, Back reverts.

A compile error is shown by the existing error strip; the instructions tell the AI to expect it
pasted back.

### Renderer and store

- `ShaderStore.Meta` gains `depth` (a file name inside the shader's folder, or absent), written and
  read like `texture`. A `meta.json` without it reads as "no depth".
- `SceneSource`, `SceneLayerView.Spec`, `SceneStill.render` and `SceneThumbView.setShader` carry the
  depth file next to `channel0`; the scene sets channel 1 where it sets channel 0 today
  (`SceneLayerView`, `SceneStill`). Thumbs and the still written to Home and Lock therefore match
  the running scene.
- The depth image loads through `ImageChannel` with two differences, given as constructor
  arguments rather than a second class: the longer side is capped at 1024 px, and wrap is clamp to
  edge (parallax reads past the border; repeat would show the far side of the image).
- `iChannelResolution[1]` is `(w, h, 1)` with a depth image and zero without, as it already is for
  an empty channel. A shader tells "no depth" from `iChannelResolution[1].x == 0.0`.
- One new uniform, `tlTouchAge` (float, seconds since the finger lifted; 0 while it is down; 1e6
  before the first touch). `SceneTouch` owns it: `up()` stamps the time, `fill` writes the age.
  `iMouse` alone cannot ease out, because it carries no time.

### Memory

| Image | Cap | Worst case held by GL |
|---|---|---|
| Photo (`iChannel0`, unchanged) | 2048 px longer side, mipmapped | about 21 MB |
| Depth (`iChannel1`, new) | 1024 px longer side, no mipmaps | about 4 MB |

Both bitmaps are released after upload, as today. The depth texture exists only while a shader that
has one is the active wallpaper or is being previewed. It is counted wherever the channel 0 texture
is counted now; if nothing counts that today, this work adds the count for both.

### Frame cost

One more texture bind per frame, nothing else on the launcher's side. The shader's own cost is the
AI's doing, so the instructions carry a budget (samples per pixel, loop length), and the existing
heavy-frame guard (`ShaderSafety`, step down then hold) is the backstop. Default quality stays Half,
rate 30.

### The editor

- The *Texture* knob is renamed **Photo**; a **Depth** knob is added beside it. Each shows the
  picked file's name, and each has a way back to none.
- Removing or changing either marks the draft dirty and is covered by the existing
  `savedTexture` clean-up, extended to the depth file.
- Settings-page footprint stays minimal: one knob in the existing knob row, no new page.

### The card

Copy for the card (product copy; no mechanism):

- Title: **Make one from a photo**
- Row subtitle: *An AI tool writes the motion for a photo you choose.*
- Step 1: **Copy the instructions** with a *Copy* button.
- Step 2: **Paste them into an AI tool with your photo.**
- Step 3: **Bring the result back** with a *Paste shader* button.
- Footnote: *For depth, add a depth image of the same photo.* linking to the guide.

A guide page under `docs/en/` says what a depth image is, that it must line up with the photo pixel
for pixel (white is near), and that a dedicated depth tool gives one that lines up while a
picture-drawing chat tool usually does not. No tool is named in the app.

### The instructions

English only and not translated (the reader is an AI); kept as a raw resource, not in
`strings.xml`. One text covers both cases, with and without depth.

```
Write one GLSL ES 3.00 fragment shader that turns the attached photo into a calm,
continuously moving wallpaper. Return only the shader, in one code block.

Contract (Shadertoy style):
- Entry point: void mainImage(out vec4 fragColor, in vec2 fragCoord). No #version line,
  no main(), no uniform declarations.
- iChannel0 is the attached photo (v = 0 is its bottom row). iChannelResolution[0].xy is
  its size. Centre-crop it to cover iResolution.xy and never stretch it. Zoom in about 4%
  so that moving the lookup never reaches the photo's border.
- iChannel1 is a depth map of the same photo, aligned with it (white = near, black = far),
  or empty. It is empty when iChannelResolution[1].x == 0.0: the shader must look right
  both ways.
- Also available: iResolution, iTime, iMouse (pixels; z > 0 while a finger is down),
  tlTouchAge (seconds since the finger lifted, 0 while it is down),
  tlPageOffset (0..1 as the home screen pages are swiped).
- Not available: any other texture, buffers or feedback passes, audio, cubemaps.

Rules:
- Keep the photo recognisable: move where it is sampled by a few pixels; do not redraw it.
- Barely noticeable at rest: sway periods of 8-15 s, drift of 45-90 s, different phases
  across the image so nothing moves in step.
- With depth: near things move more than far ones, and the view shifts slightly with
  tlPageOffset. Without depth: derive masks (sky, foliage, highlights) from the photo's
  own colours.
- A touch pushes the image near iMouse.xy and eases out over about 2 s using tlTouchAge.
- It runs at 30 frames a second on a phone all day: at most 8 texture samples per pixel,
  no loop longer than 4 steps, no noise function with more than 3 octaves.
- If I paste back a compile error, fix it and return the whole shader again.

Say what moves in comments at the top of the shader. Write nothing outside the code block.
```

## Defaults taken without asking (overrule freely)

- The photo is always picked by hand; the current Home photo is not used automatically. The AI
  wrote the shader for one particular image, so guessing another would look wrong.
- Depth is capped at 1024 px; it is a smooth image and parallax does not need more.
- *Texture* becomes *Photo* in the editor. A pasted Shadertoy shader that wants a noise texture
  still works; the label is just less exact for it.
- One instruction text, not one per case.

## Not in scope

- Depth or segmentation made on the device (the vision models removed 2026-10-06 stay removed).
- A bundle or share format; more than two images; feedback buffers.
- A layered scene renderer (separate sprites, meshes, a scene file).
- Video as an input to a user's shader.

## Every surface

- **Reverse states:** depth can be removed, the photo can be removed, and a preview that is not
  kept deletes both files with the shader's folder.
- **Themes and scheme:** the card uses the picker's existing card and button styles; nothing reads
  system palette resources directly.
- **Density and font scale:** step lines wrap; button labels are single line with ellipsis.
- **Rotation mid-preview** reverts the preview today; unchanged.
- **Editions, keyboard, dock styles, panes:** not touched.

## Build order

1. **Renderer and store** (one worker): `Meta.depth`, channel 1 through `SceneSource`,
   `SceneLayerView`, `SceneStill`, `SceneThumbView`; `ImageChannel` cap and wrap arguments;
   `tlTouchAge`.
2. **Picker** (one worker, after 1): the card on `ShaderListPage`, the Depth knob in
   `ShaderEditorPage` and `ShaderEditorLogic`, the instructions resource, strings, the guide page.

## Tests

- `SceneTouch`: age is 0 while down, grows after `up`, and is 1e6 before any touch.
- `ShaderPreludeTest`: `tlTouchAge` is declared only when the text names it.
- `ShaderStore`: meta round trip with and without `depth`; an old `meta.json` reads as no depth;
  an unsafe depth name is dropped like an unsafe texture name.
- `ShaderEditorLogic`: changing or removing depth makes the draft dirty; Apply rules unchanged.
- **Contract parity:** every uniform the instructions text names is one `ShaderPrelude` declares,
  so the text cannot promise an input the renderer lacks.
- A fixture shader written to the instructions (photo plus depth, and photo only) compiles through
  `ShaderCheck` in the instrumentation suite.

## Owed on a device

On pong, with the developer's go: the lotus photo through the whole round trip with a real AI
tool; the same with a depth image; power against the 11-09 video numbers; a cold start with a
photo shader as the wallpaper.
