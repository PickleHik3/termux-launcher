# kitty custom shaders: research for termux-launcher

Date: 2026-09-30. Status: research only. Nothing is built, and no decision is recorded here.
Companion to `SPEC.md` in this folder.

The kitty source links point to commit
[`3d06c27a`](https://github.com/kovidgoyal/kitty/tree/3d06c27a76526306ec003c1fbdce38718c844238)
(master, 2026-09-30), so the line anchors stay stable. Local paths
are relative to the repo root. Anything marked **(inference)** is my reading and has no source.
Anything marked **(unverified)** was not checked.

## 0. Answer in brief

- **The feature exists and is named what you'd expect.** kitty 0.49.0 (2026-09-21) shipped
  "Custom shaders", configured with `custom_shaders <pipeline…>` in kitty.conf
  ([changelog](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/changelog.rst#L12-L19), [L304-L307](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/changelog.rst#L304-L307),
  [option](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/options/definition.py#L2993-L3005),
  [docs](https://sw.kovidgoyal.net/kitty/custom-shaders/), RFC
  [#10344](https://github.com/kovidgoyal/kitty/issues/10344), opened and closed by the maintainer).
- **It is a post-process, not a background layer.** The shaders run "at the very end of the kitty
  rendering pipeline, when everything else has already been rendered"
  ([docs](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L128-L131)). The only slot is `end`
  ([L182-L185](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L182-L185)). kitty's "animated backgrounds" re-read the
  finished frame and add the effect where the terminal pixels are dark.
- **The language is Slang, not GLSL.** kitty runs an external `slangc` at runtime to compile
  Slang to GLSL 150 for OpenGL 3.1/3.3. Shadertoy and Ghostty GLSL is not accepted, and the
  maintainer declined to add it
  ([comment](https://github.com/kovidgoyal/kitty/issues/10344#issuecomment-5656761745)).
- **AGSL can run ports, not the files themselves.** The procedural core of a background translates
  to AGSL with some rewriting. The multi-pass and feedback parts (`persist`, the `a` and `b`
  textures), the integer hashing and the "sample the finished frame" blend do not translate
  mechanically.
- **Where it fits here:** as a built-in `AnimatedWallpaper` source under the existing spec (§3.6),
  drawn *behind* the glass, not as a kitty-style post-process over the window.

## 1. What the kitty feature is

### 1.1 Configuration

| Item | Fact | Source |
|---|---|---|
| Option | `custom_shaders` takes a space-separated list of pipeline names, loaded in order and concatenated. Its default is empty. | [definition.py#L2993-L3005](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/options/definition.py#L2993-L3005) |
| Lookup order | An absolute path, then `<config>/shaders/`, then the shaders shipped with kitty. If only a `.slang` is found, a default pipeline is built for it. | [custom-shaders.rst#L138-L151](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L138-L151) |
| Two files | A `.slang` shader that defines `fragment_main()`, and a `.pipeline` text file with groups, events, textures and `var` overrides. | [L119-L126](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L119-L126), [L176-L211](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L176-L211) |
| Tuning | `var <type> <name> <value>` replaces a matching `static const` before compilation. Values are baked in, not passed as uniforms. | [L193-L202](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L193-L202) |
| Groups | Up to 16 groups (`MAX_CUSTOM_SHADER_GROUPS`). Each one chains shaders, and each can write to the backbuffer or to a named texture. | [L209-L211](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L209-L211), [state.h#L492](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/state.h#L492) |
| Shipped backgrounds | `inside-the-matrix`, `northern-lights`, `fireworks`, `water` | [demo.py#L148-L151](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/demo.py#L148-L151) |
| Other categories | Cursor trail (`blaze`, `lightning`, `motion-blur`), mouse (`pond-ripple`, `spotlight`), navigation (`dim-inactive-windows`, `tab-change`, `focus-highlight`), retro (`crt`, `crt-blue`, `tft`) | [demo.py#L153-L187](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/demo.py#L153-L187) |
| Version | Added in 0.49.0 (2026-09-21). 0.49.1 (2026-09-24) fixed missing pipeline files in some builds. The unreleased 0.49.2 adds `cursor-trail-motion-blur` and fixes a dimming bug. | [changelog#L273-L307](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/changelog.rst#L273-L307), [L202-L228](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/changelog.rst#L202-L228) |

Other kitty features that are not custom shaders: `background_image`, in 0.47.0 (2026-05-19)
multiple GPU-resident images ([definition.py#L2604](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/options/definition.py#L2604),
[changelog](https://sw.kovidgoyal.net/kitty/changelog/)), and the built-in `cursor_trail`
([definition.py#L446](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/options/definition.py#L446), 0.37.0).

### 1.2 Shader language and inputs

- **Language.** Slang ([custom-shaders.rst#L119-L121](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L119-L121)). The
  entry point is `float4 fragment_main(float4 color, KittyTextures t, KittyCustomShaderData d)`
  ([sample.slang#L9-L21](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/sample.slang#L9-L21)).
- **Compilation.** It happens at runtime. kitty shells out to `slangc`
  ([constants.py#L41-L50](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/constants.py#L41-L50)) and targets GLSL `max(150, GLSL_VERSION)`
  ([slang.py#L688-L694](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/slang.py#L688-L694)). The GLSL is then rewritten "to
  something that will work with OpenGL 3.1" ([slang.py#L916](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/slang.py#L916)).
  kitty needs GL 3.1, or 3.3 on macOS ([data-types.h#L24-L32](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/data-types.h#L24-L32)).
  Without `slangc`, the shaders are disabled with an error
  ([slang.py#L306-L313](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/slang.py#L306-L313)). `shader-slang` is needed at build
  time "only if you use custom shaders" ([build.rst#L92](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/build.rst#L92)). The maintainer
  chose a subprocess on purpose: "I dont want bugs in a compier library to be able to crash kitty"
  ([comment](https://github.com/kovidgoyal/kitty/issues/10344#issuecomment-5418655848)).
- **Colour and coordinates.** Colours are linear RGB. UV coordinates have their origin at the
  bottom left, with Y pointing up ([custom-shaders.rst#L128-L131](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L128-L131)).
- **Inputs.** `KittyCustomShaderData` ([types.slang#L6-L80](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/types.slang#L6-L80)):

| Group | Fields |
|---|---|
| Time | `timestamp` (seconds since start), `last_rendered_at`, `frame_counter` |
| Size | `viewport_size_pixels` |
| Colours | `background`, `foreground`, `active_window_background`, `cursor_color`, `cursor_trail_color` |
| Pointer | `mouse_pos` (the current position and the last press), `mouse_button_pressed`, `mouse_pointer_hidden` |
| Layout | `active_window_geometry`, `active_window_padding`, `bell_window_geometry`, `central_area` |
| Cursor trail | corners (current and previous), edges (current and previous), `cursor_trail_state`, `cursor_trail_change_time` |

  `KittyTextures` ([types.slang#L108-L133](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/types.slang#L108-L133)) holds
  `backbuffer` (kitty's rendered frame), the scratch textures `a` and `b`, the frame-to-frame
  texture `persist`, and `pos`, `viewport`, `animation_progress` and `group`. There are no cell
  or text data and no per-cell glyph information (inference: none appear in either struct).

### 1.3 Where it sits in the render pipeline

- When shaders are active, kitty renders "in layers" into an offscreen texture
  (`needs_layers`, [shaders.c#L2433-L2452](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders.c#L2433-L2452)). At the end of the frame
  it either runs the custom end shader or blits that texture
  ([shaders.c#L2878-L2903](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders.c#L2878-L2903)). The maintainer calls this "a more
  resource intensive rendering path" ([#10344 body](https://github.com/kovidgoyal/kitty/issues/10344)).
- **A background is a blend over the finished frame.** `northern-lights` samples
  `t.backbuffer`, measures the terminal pixel's luminance, and adds the aurora where the pixel is
  near black (`BLACK_BLEND_THRESHOLD`). It returns the terminal's own alpha
  ([northern-lights.slang#L561-L574](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/northern-lights.slang#L561-L574)).
  `inside-the-matrix` and `fireworks` sample the backbuffer the same way
  ([L368](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/inside-the-matrix.slang#L368),
  [L103](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/fireworks.slang#L103)). Keeping the alpha means transparency is
  preserved ([custom-shaders.rst#L111-L112](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L111-L112)). An effect is
  therefore also added to dark text-free areas of a `background_image` (inference).
- If a group subscribes to `cursor-trail-move`, kitty's built-in trail is suppressed and the shader
  draws it instead ([custom-shaders.rst#L449-L455](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L449-L455),
  [shaders.c#L2880-L2881](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders.c#L2880-L2881)).

### 1.4 Frame pacing, idle and power

- `animation_step` defaults to 50 ms (20 fps), and it is never below `repaint_delay` (default
  10 ms). A value of 0 means the shader redraws only on events
  ([custom-shaders.rst#L278-L284](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L278-L284),
  [definition.py#L1460-L1463](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/options/definition.py#L1460-L1463)).
- Redraws are driven by the event loop's wait timeout: `set_maximum_wait(min_step)` and the next
  end time of an animation ([child-monitor.c#L960-L975](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/child-monitor.c#L960-L975)).
  There is no vsync-driven clock. `custom_shader_needs_render` decides per tick whether to draw
  ([shaders.c#L747-L763](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders.c#L747-L763)).
- Groups start and stop on events: focus in or out, `user-activity`, `user-idle`, `tab-change`,
  bell, mouse press, trail move or stop ([custom-shaders.rst#L399-L463](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L399-L463)).
  `user-idle` fires when `cursor_stop_blinking_after` elapses
  ([child-monitor.c#L948-L958](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/child-monitor.c#L948-L958)). All four shipped backgrounds
  use `start os-window-focus-in | user-activity` and `stop os-window-focus-out | user-idle`
  ([northern-lights.pipeline](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/northern-lights.pipeline#L3-L5),
  [inside-the-matrix.pipeline](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/inside-the-matrix.pipeline),
  [fireworks.pipeline](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/fireworks.pipeline)). So **a kitty background stops
  when the user is idle or focus leaves**, and inactive groups are skipped
  ([custom-shaders.rst#L343-L345](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/custom-shaders.rst#L343-L345)).
- **Cost, as measured by the maintainer:** `northern-lights` uses "~3-5% of CPU" and "automatically
  turns off when the window is idle" ([#10344 body](https://github.com/kovidgoyal/kitty/issues/10344)).
  ASCII throughput drops from 190 to 130 MB/s on his Linux box
  ([comment](https://github.com/kovidgoyal/kitty/issues/10344#issuecomment-5511337856)). On an
  M1 Air it manages 66 MB/s in a normal window and 22 MB/s at full screen
  ([comment](https://github.com/kovidgoyal/kitty/issues/10344#issuecomment-5518941166)). He calls
  it heavy because it "runs multiple draw calls writing into extra buffers"
  ([comment](https://github.com/kovidgoyal/kitty/issues/10344#issuecomment-5512930436)).
- **Platform limits:** it works wherever kitty's OpenGL path runs, which is Linux and macOS (no
  per-platform exclusion appears in the docs; inference). It needs `slangc`. Old macOS AMD GPUs
  had a 0.49 shader regression ([changelog#L250-L251](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/changelog.rst#L250-L251)).

## 2. Can the shaders run on Android?

### 2.1 Compatibility summary

| Source | AGSL `RuntimeShader` (API 33+) | OpenGL ES 3.x (GLSurfaceView or own EGL) |
|---|---|---|
| kitty `.slang` as shipped | **No.** It is Slang and needs `slangc` plus kitty's pipeline runtime. | **No, as is.** `slangc` output is desktop GLSL 150 (see §1.2). Whether `slangc` can emit GLSL ES is **(unverified)**. |
| kitty background, procedural core | **A port, by hand or with a script.** It is HLSL-flavoured, which AGSL partly shares (`float4`, `half`). | A port to GLSL ES 3.00. It is closer, because ES 3.00 has `uint` and bitwise operators (the ES 3.00 spec was not read here: **unverified**). |
| kitty multi-pass (`persist`, `a`/`b`) | Not in one shader. It needs our own ping-pong through offscreen renders (`HardwareBufferRenderer`, API 34). | Straightforward with FBOs. |
| Shadertoy or Ghostty GLSL | Port: rewrite `mainImage` as `main`, `texture()` as `.eval()`, `#define` as `const`, and fix the loops. | Near-mechanical (add `iTime`, `iResolution` uniforms and an ES header) **(inference)**. |

The maintainer on GLSL: "adding GLSL would not make shaders from X other product compatible
since the uniforms and pipeline architecture will remain different". Porting to Slang is
"trivial" ([comment](https://github.com/kovidgoyal/kitty/issues/10344#issuecomment-5656761745)).
Several kitty backgrounds are themselves Shadertoy ports
([inside-the-matrix#L5-L6](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/inside-the-matrix.slang#L5-L6),
[fireworks#L5-L7](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/fireworks.slang#L5-L7)).

### 2.2 The GLSL/Slang → AGSL differences that matter

| Topic | AGSL rule | Source | What it hits in kitty's backgrounds |
|---|---|---|---|
| Feature base | "fixes its GLSL feature set at GLSL ES 1.0" | [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl) | Everything below |
| Entry point | `half4 main(float2 fragCoord)`, in local coordinates | [quick ref](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference), [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl) | `fragment_main(color, t, d)` becomes `main` plus uniforms |
| Origin | Top left, Y down (flip it with `setLocalMatrix`) | [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl) | kitty is bottom-left UV in [0,1], so it needs a flip and a divide by resolution |
| Textures | "Sampler types aren't supported". Use `uniform shader x; x.eval(coord)` with pixel coordinates, fed from a `BitmapShader` or another shader | [quick ref](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference) | `t.backbuffer.Sample(uv)` becomes `content.eval(px)`; there is no `tex.Sample` or LOD |
| Loops | `for` only when unrollable: constant init, test and step, with step `++ -- += -=` | [quick ref](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference) | The loops use `static const` bounds (`ITERATIONS`, `NUM_PARTICLES`, `MAX_ITER`), so they are likely fine. An early `return` inside a loop ([inside-the-matrix#L178](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/inside-the-matrix.slang#L178)) is **(unverified)** in AGSL |
| Integers and bitwise | The types listed are `int`, `short`, `float`, `half` and `bool`, with no `uint`. The operator table has no shift, `&`, `|` or `^` | [quick ref](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference) | `northern-lights`' `pcgHash` uses `uint`, `>>` and `^` ([#L158-L175](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/northern-lights.slang#L158-L175)), so it needs a float hash instead (visibly different noise, inference) |
| Arrays | 1-D only, a constant size, indexed by a constant or a loop variable, no copy or return | [quick ref](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference) | Check per shader |
| Structs | Global scope only | [quick ref](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference) | The `KittyCustomShaderData` struct parameter is flattened to uniforms |
| Preprocessor | Not supported. Use `const` | [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl) | kitty's `var` baking becomes `const` edits or uniforms |
| Precision | `half` and `short` are mediump, and `lowp` becomes mediump. Only the GLES 2.0 minimums are guaranteed | [quick ref](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference) | kitty wraps time at 86400 s; keep time in `float` |
| Colour | Colour uniforms are not converted unless marked `layout(color)`. Use `toLinearSrgb`/`fromLinearSrgb` | [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl) | kitty works in linear RGB |
| Uniforms | `setFloatUniform`, `setIntUniform`, `setColorUniform`, `setInputShader`, `setInputBuffer` | [quick ref](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference), [RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader) | Time comes from our clock, like the doc's `iTime` sample ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)) |
| HLSL intrinsics | Not documented as AGSL built-ins (inference): `lerp`, `saturate`, `fmod` | [quick ref](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference) (its function list) | Rewrite them as `mix` and `clamp`. `fmod` → `mod` changes the result for negative inputs (standard HLSL vs GLSL semantics, not cited) |
| Derivatives (`dFdx`, `fwidth`) | Not listed **(unverified)** | — | None are used in the four backgrounds (grep, inference) |

### 2.3 API levels

| API | Level | Source |
|---|---|---|
| `RuntimeShader` (AGSL) | 33 | [reference](https://developer.android.com/reference/android/graphics/RuntimeShader), [AGSL overview](https://developer.android.com/develop/ui/views/graphics/agsl) ("Android 13 and above") |
| `RenderEffect` (blur, `createRuntimeShaderEffect`) | 31 (the runtime shader effect also needs 33 for the shader) | [reference](https://developer.android.com/reference/android/graphics/RenderEffect) |
| `HardwareBufferRenderer` | 34 | [reference](https://developer.android.com/reference/android/graphics/HardwareBufferRenderer) |
| OpenGL ES 3.0 / 3.1 / 3.2 | 18 / 21 / 24, and only if the vendor ships it | [OpenGL guide](https://developer.android.com/guide/topics/graphics/opengl) |
| `WallpaperService` | 7 | [reference](https://developer.android.com/reference/android/service/wallpaper/WallpaperService) |

The app's `minSdkVersion` is 26 and its `targetSdkVersion` is 28 (`gradle.properties:26-27`).
Fancier Glass gates AGSL at `TIRAMISU` (33) (`app/src/main/java/com/termux/app/chrome/FancierGlassPolicy.java:19`).
The animated-wallpaper spec settled on API 34+ (`SPEC.md` §13 Q1).

## 3. Fit with termux-launcher

### 3.1 What exists

| Piece | Where | Relevance |
|---|---|---|
| AGSL refraction program | `chrome/GlassRefraction.java` (`RuntimeShader` at :250, `uniform shader content` at :170, `half4 main` at :194, gated at :46-48) | The pattern for compiling once and setting uniforms per draw. `content` is the shared-frame `BitmapShader` (ADR 0005). |
| `RenderEffect` over own pixels | `TermuxActivity.java:3727` (`createRuntimeShaderEffect(…, "content")`) | The only kitty-style "post-process" in the app: route (c), dock and strip. |
| Other `RuntimeShader` users | `SharedFrameDrawable`, `PaneGlassBackdropView`, `PaneControlsView`, `DockEdgeGlowView`, `PaneSnapshot` | Readers of the shared frame (the spec §2 table). |
| Cursor trail | `terminal-view/.../KittyCursorTrail.java` (a port of `cursor_trail.c`, GPL-3.0-only header :1-5), drawn with `Canvas.drawPath` in `app/.../terminal/PaneMotionOverlayView.java:325` | Not a shader. kitty's `cursor-trail-blaze`/`motion-blur` shaders would be a new AGSL path (backlog row, `project-docs/backlog.md:55`). |
| kitty.conf reading | `TerminalFontConfig.java` reads `~/.config/kitty/kitty.conf` (:28, :404-413), including the `cursor_trail*` directives (:801-813) | There is precedent for honouring a kitty option (`docs/en/Terminal_Kitty_Protocols.md:118-137`). |
| kitty.conf never created | `TermuxLauncherConfigInstaller.java:51-63`: `kitty.conf` is only among `EXAMPLE_NAMES` and not among `SEEDED_FILES` | This matches the repo decision that the app reads kitty.conf but never creates it. |
| Animated wallpaper plan | `SPEC.md` §3–§6 (not built) | `AnimatedWallpaper` (an AGSL string plus uniforms), `LiveWallpaperRenderer`, and a live blurred frame per radius |

### 3.2 Where a kitty-style background would plug in

- **As a built-in source (fits the spec).** Port the procedural part of a kitty background (for
  example the aurora in `northern-lights`) into one `AnimatedWallpaper` implementation. It then
  feeds (1) the radius-0 backdrop, drawn directly as the shader, and (2) the ÷4 source node that the
  blur nodes read for the glass (`SPEC.md` §3.5, §3.6). The glass keeps sampling one shared frame
  (ADR 0004), so the aurora stays in register under the refraction (ADR 0005). The spec's
  Aurora already has this role (`SPEC.md` §6.1). A kitty port is a candidate body for it,
  subject to §4.3 **(inference)**.
- **Drop kitty's luminance blend.** kitty adds the effect over dark terminal pixels only because it
  has one slot, `end` (§1.3). Here the wallpaper really is behind the panes, so the blend step
  (`textPreserve`, the `t.backbuffer` read) has no counterpart. The shader becomes a pure
  generator, with no `uniform shader` input **(inference)**.
- **Multi-pass shaders don't fit phase 1.** `northern-lights` needs `a` and `persist` (a feedback
  loop, [pipeline](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/northern-lights.pipeline#L1-L22)). The spec's render graph
  is a single source node (`SPEC.md` §3.6). Feedback would need a second ring and a shader that
  reads the previous slot. `water` and `inside-the-matrix` are single-group
  ([inside-the-matrix.pipeline](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/inside-the-matrix.pipeline)), so they are
  cheaper to port. `water` is only a distortion of the backbuffer, though
  ([water.slang#L39](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/water.slang#L39)), so as a wallpaper it would need a
  source picture **(inference)**.
- **A kitty-style post-process is the wrong seam.** Running a shader over the whole window
  (`RenderEffect` on the root) would re-render the full window into an offscreen layer every
  frame. It would bend the text, the glass and the chrome, and it would feed nothing into the
  shared frame, so the glass would not show the effect. ADR 0005 already rejected "a `RenderEffect`
  per surface" as "an offscreen layer per glass surface per frame" (inference for the whole-window
  case; the cost claim is `using-agsl`'s "more expensive than drawing a custom View",
  [Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)).
  Post-process effects that *are* kitty's point, such as `crt`, `dim-inactive-windows` or the
  mouse ripples, are a separate feature and are not part of the wallpaper.
- **Pacing maps directly.** kitty's defaults (20 fps, stop on idle and focus-out) are gentler than
  the spec's 30 fps "whenever visible" (`SPEC.md` §5, §13 Q3–4). kitty's `user-idle` has no
  counterpart in the spec's pause table (inference; a candidate for open question 11 or a new
  one).

### 3.3 Could `custom_shaders` in kitty.conf be honoured?

- **Mechanically, yes.** `TerminalFontConfig` already parses kitty.conf directives, so it could map
  `custom_shaders northern-lights` to a built-in id **(inference)**.
- **Only for names we ship.** The spec rules out user shaders: "A shader is code, and packs carry
  no code" (`SPEC.md` §1; Fancier Glass §5). So paths and `~/.config/kitty/shaders/*.slang` would
  be ignored, and there is no Slang compiler on the device anyway (§1.2).
- **Conflicts with the spec.** The spec says the choice is made in the picker and there is no new
  settings page (`SPEC.md` intro, §7.1). A kitty.conf synced from a desktop would silently change
  the phone's wallpaper. kitty's shaders are also post-processes that include trails, CRT and
  focus effects, which have no wallpaper meaning. Recommendation: **don't honour it**, or at most
  report it in a diagnostics line **(inference)**. The app keeps never creating kitty.conf either
  way.

## 4. Risks and costs

### 4.1 Battery and thermal

- kitty's heaviest background costs its maintainer 3–5% CPU on a desktop, and it cuts throughput by
  about 30% at full screen, down to about 70% on an M1 (§1.4). There is no mobile number
  **(unverified)**. `northern-lights` ray-marches at `MARCH_STEPS = 50`
  ([L36](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/northern-lights.slang#L36)), which is far above the spec's
  target of "under about 60 ALU ops per pixel, with no texture reads" (`SPEC.md` §6.1)
  **(inference)**.
- The spec's own top risk still applies: every wallpaper frame damages the whole window
  (`SPEC.md` §3.2 item 4, §12 risk 1). Phase 0 measures that before any port (`SPEC.md` §10).
- The spec's pause rules (battery saver, thermal, reduced motion, Lazy mode, not visible) cover
  more than kitty's idle and focus stop (`SPEC.md` §5).

### 4.2 API floor

- The app's minSdk is 26 (`gradle.properties:26`). AGSL is 33 and the spec's renderer is 34
  (§2.3). Below 34 the Animated section is hidden and the still is shown (`SPEC.md` §6.3). An
  OpenGL ES path would reach API 26 devices, but it would need its own EGL stage, like the spec's
  phase 2 video (`SPEC.md` §6.2) **(inference)**.

### 4.3 Licensing

| Fact | Source |
|---|---|
| kitty is GPLv3 | [LICENSE](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/LICENSE#L1-L2) |
| The shipped shaders say "Distributed under terms of the GPLv3 license." | e.g. [types.slang#L2-L3](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/types.slang#L2-L3), [northern-lights#L2-L3](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/northern-lights.slang#L2-L3) (© Sami Farin) |
| termux-launcher is GPLv3-only | `LICENSE`, `LICENSE-EXCEPTIONS.md:1` |
| The cursor-trail port already carries kitty's copyright and GPL-3.0-only | `KittyCursorTrail.java:1-5`, `THIRD_PARTY_NOTICES.md:16-21` |
| `fireworks` embeds a CC BY-NC-SA 3.0 upstream | [fireworks.slang#L5-L7](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/fireworks.slang#L5-L7) |
| `crt` embeds a CC BY-NC-SA 3.0 upstream | [crt.slang#L5](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/crt.slang#L5) |
| `inside-the-matrix`, `underwater` and `fireworks-rockets` are Shadertoy ports with no upstream licence stated | [inside-the-matrix#L5-L6](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/inside-the-matrix.slang#L5-L6), [underwater#L6](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/underwater.slang#L6), [fireworks-rockets#L5](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/fireworks-rockets.slang#L5) |
| `northern-lights` takes ideas from an MIT shader | [northern-lights#L7-L8](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/kitty/shaders/custom/northern-lights.slang#L7-L8) |

What this implies (inference, not legal advice):

- Copying or translating a GPLv3 kitty shader into this GPLv3-only app is compatible. It needs the
  copyright line kept and a `THIRD_PARTY_NOTICES.md` entry, as the trail port has. A translation to
  AGSL is still a derivative work.
- CC BY-NC-SA material (`fireworks`, `crt`) must not be copied. Its non-commercial and share-alike
  terms are generally regarded as GPL-incompatible (the CC licence text was not read here:
  **unverified**).
- Shadertoy ports with no stated upstream licence (`inside-the-matrix`, `underwater`,
  `fireworks-rockets`) have unclear provenance. Shadertoy's default licence was not checked
  **(unverified)**. Avoid them.
- The cleanest option is to write original AGSL built-ins from the idea (aurora, waves). Nothing is
  copied then, and the notices stay unchanged.

### 4.4 User-supplied shaders

- kitty compiles them in a separate process so that compiler bugs can't crash the terminal (§1.2).
  kitty documents no limit on a shader's GPU run time **(unverified)**.
- On Android, `new RuntimeShader(String)` compiles in-process
  ([reference](https://developer.android.com/reference/android/graphics/RuntimeShader)). The
  AGSL docs describe no sandboxing, time limit or watchdog for a runaway shader **(unverified)**.
  A heavy shader could stall the RenderThread, which also draws the terminal and the keyboard
  **(inference)**.
- The spec already excludes user shaders (`SPEC.md` §1, §12 risk 6). This research gives no reason
  to reopen that.

## 5. Options and recommendation

The table below is all **inference**. The facts it relies on are cited in §1–§4.

| Option | What it is | Cost | Verdict |
|---|---|---|---|
| A. Original AGSL built-ins, kitty-inspired | Write Aurora and Tide from scratch as single-pass generators, with kitty's look as a visual reference only | Low. It fits `SPEC.md` phase 1 unchanged, needs no licence work, and meets the ALU target | **Recommended** |
| B. Port one GPLv3 kitty background (for example the `northern-lights` aurora core) | Translate it by hand to AGSL, drop the blend and the `persist` pass, replace `pcgHash`, and add a notice | Medium. It needs a notices entry, and the ray-march probably breaks the ALU target, so it needs a cheaper march or half-resolution (open question 5) | Only if A doesn't satisfy the look |
| C. Honour `custom_shaders` from kitty.conf for ids we ship | Map shipped names to built-ins | Low code, but it conflicts with picker-only choice and surprises synced configs | Not recommended |
| D. A kitty-style post-process slot (a `RenderEffect` over the window) | Run CRT, dimming and ripple effects over the whole window | High: a full-window offscreen layer per frame, and the glass doesn't see it | Out of scope; a separate feature if ever |
| E. A GLES stage running Slang→GLSL output, or user shaders | Ship or compile shaders in GLSL ES | Needs its own EGL, has no on-device `slangc`, and is excluded by the spec | Rejected |

If B is chosen, do it after phase 0 (`SPEC.md` §10), and measure it against the spec's §9.2
budget.

## 6. Sources not reached or not read

- The rendered `sw.kovidgoyal.net/kitty/custom-shaders/` page was fetched (HTTP 200). Its content
  was read from the repo `.rst` at the pinned commit instead, and the shader galleries on it are
  generated at doc build time ([conf.py#L319-L357](https://github.com/kovidgoyal/kitty/blob/3d06c27a76526306ec003c1fbdce38718c844238/docs/conf.py#L319-L357)).
- The `gh` CLI was unauthenticated. Issue #10344 and its comments were read through the public REST
  API instead.
- Not read: the Slang language docs (whether `slangc` targets GLSL ES), the GLSL ES 3.00 spec, the
  Creative Commons licence text, Shadertoy's terms, and a full Android `RuntimeShader` reference for
  the error behaviour on bad AGSL.
