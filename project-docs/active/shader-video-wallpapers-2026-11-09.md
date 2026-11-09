# Shader and video wallpapers (2026-11-09)

Status: built 2026-11-09 (waves A-C merged to local v2-redesign at 0fc3ff95d, emulator-checked; wave D on pong
owed, see project-docs/device-checks.md). Decided 2026-11-09. Replaces the AGSL Live scenes of
[live-wallpapers-2026-11-05.md](live-wallpapers-2026-11-05.md) (built 11-06, on pong). The parked
Studio maker (`origin/feat/studio`) is unaffected and stays parked. Decision record: ADR 0024.

## Problem

The Live kind is five first-party AGSL scenes. AGSL is pinned at GLSL ES 1.0: no derivatives, no
integer bit operations, no dynamic loops, no preprocessor, no cubemaps, no feedback passes. The
developer could not get the scenes where they wanted them, and nothing a user finds on Shadertoy or
writes in ShaderEditor pastes in. Battery is the other worry: a moving wallpaper must not cost more
than it is worth.

## Decisions (developer, 2026-11-09)

1. **Two user-fed kinds replace Live.** The Wallpaper picker offers **Photo | Video | Shader**.
   *Video* plays a file the user picks (video or GIF). *Shader* runs a fragment shader the user
   pastes, imports or writes. The five AGSL scenes and their knobs (speed, density, palette) are
   deleted, not hidden. A stored Live choice migrates back to the remembered photo through the
   existing restore files.
2. **One renderer: OpenGL ES 3 on the scene surface.** The back surface (`SceneLayerView`, its
   thread, gates and frame policy) stays; its drawing moves from `HardwareRenderer` +
   `RuntimeShader` to an EGL context on the same `Surface`. Both kinds draw through it: a shader
   as the program, a video as a passthrough program sampling an external texture.
3. **Shadertoy contract, ShaderEditor aliases.** A pasted shader gets a prelude and, when it
   defines `mainImage`, a generated `main`. Uniforms: `iResolution`, `iTime`, `iTimeDelta`,
   `iFrame`, `iFrameRate`, `iMouse` (pixels, Shadertoy semantics), `iDate`, `iChannel0..3`,
   `iChannelResolution[4]`. Launcher extras: `tlPageOffset` (0..1), `tlBattery` (0..1),
   `tlKeyboard` (fraction of height), `tlDark` (0/1), `tlColor0..2` (the look's accent, surface and
   on-surface, sRGB 0..1). ShaderEditor's names (`resolution`, `time`, `touch`, `battery`, `offset`,
   `date`, `pointerCount`, `pointers[]`) are aliases in the same prelude, verified against the
   ShaderEditor source (MIT).
   - No `#version` and `mainImage` present: compile as `#version 300 es`, `out vec4` output, wrapper
     `main` calls `mainImage`.
   - No `#version` and `void main` present: compile as GLSL ES 1.00 (`gl_FragColor`), the way
     ShaderEditor's default shaders are written.
   - A `#version` line present: keep it as the first line; the prelude follows it.
   - Coordinates are GL native (origin bottom-left), which is what Shadertoy and ShaderEditor expect.
   - Channels: `iChannel0` is an optional image the user picks (any further channels are black
     until feedback buffers come, see Not in scope). For Video, the frame is `iChannel0`.
4. **Quality and pacing.** Per shader: quality Full / Half / Quarter (render scale 1, 1/2, 1/4;
   default Half) and rate 15 / 30 / 60 (default 30). Video renders at Full, rate 30, capped by the
   file's own frame rate. The scene skips frames itself as today.
5. **Pause rules carry over** from the 11-05 spec (decisions 5-7): pause onto the still when
   covered, screen off, battery saver, thermal throttling, animators off; resume continues the
   shader's clock from where it paused (a shader may be non-periodic, so no restart-at-still).
   Video pauses at its position and resumes there.
6. **Glass** reads the same 1/8-size pass at half the rate (`LiveSceneFrame` contract unchanged).
   The producer becomes an EGL window surface over an `ImageReader`; the ledger, hold depth and
   the software-copy path in `SceneLowRes` stay as they are. Available from API 29
   (`Bitmap.wrapHardwareBuffer`); below that the kinds are hidden, as Live was below 33.
7. **The system wallpaper** stays "one still for Home and Lock" (11-05 decisions 11-14): the
   still is a frame rendered offscreen (FBO + `glReadPixels`) at `LiveSceneLogic.stillSize`. For
   Video it is the frame at apply time. Photo restore and terminal-app mode are unchanged.
8. **Safety, because it is the home screen.**
   - A shader is applied only after it compiles, links and draws one frame offscreen; errors show
     in the editor with line numbers mapped back to the user's text (prelude offset removed).
   - Context loss (`EGL_CONTEXT_LOST`, `GL_OUT_OF_MEMORY`): recreate once; a second loss within
     10 s reverts to Photo with a one-line notice.
   - Heavy shaders: when the wall frame time exceeds twice the period for 30 consecutive frames,
     drop one quality step; at Quarter and still over, pause onto the still and show "Too heavy
     at this quality" on the detail page.
   - Crash guard: applying writes a pending marker; it is cleared after 3 s of running. A marker
     found at start reverts to Photo before the surface is created.
9. **Video and GIF.** Framework `MediaPlayer` (no new dependency), looping, muted, decoded to a
   `SurfaceTexture` sampled as `GL_TEXTURE_EXTERNAL_OES`, centre-cropped to the screen. GIF goes
   through `ImageDecoder` → `AnimatedImageDrawable` drawn on a `Surface` canvas over the same
   `SurfaceTexture`, advanced per tick at the rate cap. The file is copied under
   `files/wallpaper/video/` (SAF grants do not survive reliably). On import a one-line hint from
   `MediaMetadataRetriever` + `MediaCodecList`: codec, size, fps, length, and whether this phone
   decodes it in hardware.
   - **Recommended format (shown in the picker):** H.265 (HEVC) MP4, the screen's size or smaller,
     24-30 fps, a 5-15 s seamless loop, about 5-8 Mbps (under 20 MB). H.264 is the universal
     fallback. AV1 only where the phone has a hardware decoder (pong's Snapdragon 8+ Gen 1 does
     not). GIF decodes on the CPU: fine when small, a short HEVC clip uses less battery.
10. **The editor** is the Shader detail page: name, a monospace code box (horizontal scroll, no
    wrapping), Paste, Import file (`.glsl`, `.frag`, `.txt`), Apply, an error strip, knobs
    Quality, Rate, Texture (pick an image for `iChannel0`), Delete. The Shader page lists saved
    shaders with a thumbnail (the still at thumb size) and "New shader". Copy is plain
    ([plain-ui-copy]): "Shadertoy and ShaderEditor shaders paste as-is."
11. **Storage.** Shaders under `files/wallpaper/shaders/<id>/source.glsl` + `meta.json` (name,
    quality, rate, texture file). The active choice in prefs: kind (`photo` | `video` | `shader`)
    and the active id or file. The old `live_wallpaper_*` keys are read once by the migration and
    then cleared.
12. **No AI in v1.** The editor has no "ask a model" path. The model bench (below) is a separate
    experiment; if it is good enough, a later wave hands the editor to the Assistant page.
13. **Debug route.** `debug.tl.scene` and the scene list go. `debug.tl.shader_file=<path>` loads a
    shader file as the active shader for testing; `debug.tl.scene_rate`, `scene_glass`,
    `scene_glass_rate`, `scene_glass_div`, `scene_glass_copy` stay.

## Not in scope
- Feedback passes (Shadertoy Buffer A-D, ShaderEditor `backbuffer`), cubemaps, audio input.
- A live lock screen or `WallpaperService`.
- Sharing shaders between users, a gallery, or downloading from Shadertoy.
- AI generation in the app (decision 12).
- The old reaction contract (window rects, keyboard rects beyond `tlKeyboard`).

## Model bench (separate from the build)
GLSL is rare in training data: ShaderMatch (ICSE 2025) found frontier models fail to produce
compiling GLSL about a third of the time, and AGSL is rarer still, which is one more reason the
renderer speaks GLSL. Candidates that fit a 12 GB phone, all Apache 2.0:

| Model | Engine | Download | Notes |
|---|---|---|---|
| Gemma 4 E4B | LiteRT-LM | already in the catalogue (3.7 GB) | baseline, no work |
| Qwen3.5-4B mixed int4 | LiteRT-LM | `litert-community/Qwen3.5-4B`, `Qwen3.5-4B_mixed_int4.litertlm`, 2.75 GB | LiveCodeBench v6 55.8 (card); Adreno GPU verified on S26 |
| Qwen3.5-9B mixed int4 | LiteRT-LM | `litert-community/Qwen3.5-9B`, `Qwen3.5-9B_mixed_int4.litertlm`, 6.06 GB | LCB v6 65.6 (card); the S26 GPU engine did not build, so CPU only, slow |
| Qwen2.5-Coder-7B 4-bit | MNN (`tai import`) | `taobao-mnn/Qwen2.5-Coder-7B-Instruct-MNN`, 5.5 GB | the only code-tuned model under 12 GB on either engine |

Bench: 12 prompts (gradient, plasma, raymarched blob, starfield, water caustics, ...), each model
once per prompt, score compiles-first-try, compiles-after-one-error-retry, tokens/s, peak RAM;
the developer judges the look from a contact sheet. Not before v1 lands.

## Waves
- **A. Renderer** (`chrome/scene`): EGL on the scene surface, prelude and version detection,
  uniforms, quality and rate, pause rules, safety (decision 8), still, glass producer, delete the
  scenes and their tests, the debug file route, unit tests for the pure parts (prelude builder,
  error-line mapping, quality step-down, migration plan). Emulator: a debug shader file draws.
- **B. Video source** (`chrome/scene`, after A's source seam): MediaPlayer + GIF paths, import
  copy, metadata hint, still at apply time.
- **C. Picker and editor** (`chrome/wallpaper`, `surfaces`, strings, prefs; parallel to B):
  Photo | Video | Shader, the two pages, storage, migration wiring. Settings pages are fragile:
  smallest footprint.
- **D. Pong:** power and heat for a light, a medium and a heavy shader at Half/30, one HEVC
  1080p30 clip and one GIF, against the 11-05 baseline (no scene 1.27 W, Aurora 1.2 W, Particles
  2.0 W); the crash guard and the context-loss revert exercised once on purpose.
