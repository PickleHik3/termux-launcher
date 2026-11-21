# AGSL capabilities and limits on Android (2026-11-05)

Question: what can AGSL shaders do on Android, and where do they stop? Read-only research, no code
changed. Complements `project-docs/active/agsl-effects-and-touch-latency-2026-10-22.md` and
`project-docs/active/agsl-crt-trail-effects-2026-11-02.md` (what this project already built) and
`project-docs/reference/terminal/agsl-performance-and-touch-latency-research.md` (cost rules).

Method. Every claim cites the page that owns it, fetched on 2026-11-05: developer.android.com
(guides and API reference), the AOSP `RuntimeShader.java` and `RenderProperties.cpp` sources, and
Skia's `SkRuntimeEffect.h` and SkSL docs. Anything I could not confirm from a primary source is
marked **unconfirmed**. Where the fetched page was summarised by the fetch tool rather than read
verbatim, the quotes are the tool's quotes of the page; re-check a load-bearing one before
building on it.

## Answer in short

1. AGSL is Skia's SkSL pinned to GLSL ES 1.0, wrapped in `RuntimeShader` (API 33). It runs as one
   function inside the fragment shader Android already assembles for a draw, so it is a stage of the
   Canvas pipeline, not a free-standing GPU program
   ([AGSL overview](https://developer.android.com/develop/ui/views/graphics/agsl),
   [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl)).
2. It has no textures, no `sampler2D`, no derivatives in the documented function list, no
   preprocessor, no recursion, constant-bound `for` loops only, 1-D arrays indexed by a constant or
   loop variable only
   ([quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)).
3. Inputs are float/int uniforms, `layout(color)` colour uniforms and child `uniform shader`s read
   with `.eval(coord)`. `RenderEffect.createRuntimeShaderEffect` binds exactly one input: the
   node's own contents
   ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader),
   [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect)).
4. API 36 added `RuntimeColorFilter`, `RuntimeXfermode` and `setInputColorFilter` /
   `setInputXfermode`; API 37 added `RuntimeShader.setWorkingColorSpace` (sources below). No
   newer `createRuntimeShaderEffect` variant is listed in the current reference.
5. Anything that needs the previous frame, a second render target, compute, or a real texture
   pipeline has to be built outside AGSL (ping-pong bitmaps, a GL/Vulkan surface). Escape hatches
   are in section 6.

## 1. API levels

| API | What | Source |
|---|---|---|
| 31 | `RenderEffect` class: blur, chain, blend-mode, colour-filter, offset, bitmap and shader effects; `View.setRenderEffect`, `RenderNode.setRenderEffect` | [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect) |
| 33 | `RuntimeShader` (constructor, `setFloatUniform`, `setIntUniform`, `setColorUniform`, `setInputShader`, `setInputBuffer`); `RenderEffect.createRuntimeShaderEffect(RuntimeShader, String)` | [RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader), [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect) |
| 33 | "AGSL is used by Android 13 and above" | [AGSL overview](https://developer.android.com/develop/ui/views/graphics/agsl) |
| 36 | `RuntimeColorFilter`, `RuntimeXfermode`; `RuntimeShader.setInputColorFilter`, `setInputXfermode` | [RuntimeColorFilter](https://developer.android.com/reference/android/graphics/RuntimeColorFilter), [RuntimeXfermode](https://developer.android.com/reference/android/graphics/RuntimeXfermode), [RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader), [Android 16 features](https://developer.android.com/about/versions/16/features) |
| 37 | `RuntimeShader.setWorkingColorSpace(ColorSpace)` (RGB model only; null restores destination space) | [RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader) |

Details.

- `Paint.setShader` takes any `Shader`, so a `RuntimeShader` goes wherever a Paint does, including
  `drawText` ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)).
  The Paint API itself is older than 33; I did not look up its level. The guide says a
  `RuntimeShader` "can be used anywhere a standard Android shader can".
- `RenderEffect.createRuntimeShaderEffect` has one overload in the current reference: shader plus
  one uniform-shader name. The reference lists no variant with several inputs or a size hint.
- `RuntimeColorFilter` signature is `vec4 main(half4 in_color)`, input and output are sRGB.
  `RuntimeXfermode` is `vec4 main(half4 src, half4 dst)`, also sRGB
  (API pages above). The Android 16 notes pitch them for Threshold, Sepia and Hue/Saturation
  ([Android 16 features](https://developer.android.com/about/versions/16/features)).
- Android 17's feature overview mentions no AGSL change
  ([Android 17 features](https://developer.android.com/about/versions/17/features)); the only API-37
  AGSL item I found is `setWorkingColorSpace`, from the reference page, not release notes. The
  complete API diff was not read: **unconfirmed** that nothing else landed in 37.
- Compose: `ShaderBrush(RuntimeShader)` is the documented route, `@RequiresApi(TIRAMISU)`, with
  `drawWithCache` to hold the shader and `onDrawBehind` for per-frame uniforms
  ([Brush: gradients and shaders](https://developer.android.com/develop/ui/compose/graphics/draw/brush)).
  `Modifier.graphicsLayer` has a `renderEffect: RenderEffect` parameter
  ([graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
  That page does not show the AGSL conversion (`asComposeRenderEffect`), so that wrapper is
  **unconfirmed** from a fetched page.

## 2. The language

Entry point and coordinates.

- Shader: `half4 main(float2 fragCoord)` ([quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference));
  `fragCoord` is in the canvas / RenderNode's local coordinates with origin top-left, shifted by any
  local matrix; a parent shader that calls yours through `setInputShader` may remap it
  ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader),
  [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl)).
- Colour filter: `main(half4 in_color)`; blender: `main(half4 src, half4 dst)` (API 36 pages).
- Skia's own entry points match: `vec4 main(vec2)`, `vec4 main(vec4)`, `vec4 main(vec4, vec4)`
  ([SkRuntimeEffect.h](https://raw.githubusercontent.com/google/skia/main/include/effects/SkRuntimeEffect.h)).

Types and precision ([quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)).

- GLSL ES 1.0 types plus `half`/`short` (mediump) and `floatN`/`intN`/`boolN`/`floatNxM` aliases.
  `highp` guaranteed range 2^62, relative precision 2^-16; `mediump` range 2^14, 2^-10. `lowp` is
  converted to `mediump`. Only these minimums are guaranteed; the page notes most devices exceed them
  because they support ES 3.0.
- `.LTRB` swizzle and constant `0`/`1` swizzle components exist (not in GLSL).
- Structs only at global scope. Arrays: 1-D, explicit size, cannot be returned, copied, assigned or
  compared, and can be indexed "only using a constant or a loop variable".
- Constructors that shrink a vector (`vec3(v4)`) are not supported; use swizzles.
- Declarations must be in a braced scope; `attribute`, `varying`, `invariant` unsupported;
  functions with non-void return must return a value; `discard` is not allowed.
- No preprocessor: convert `#define` to `const`. The compiler folds consts and eliminates branches on
  them ([AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl)).

Control flow.

- `for` with `break`/`continue`; `if/else`; `switch` (default last); no recursion of any kind
  ([AGSL overview](https://developer.android.com/develop/ui/views/graphics/agsl),
  [quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)).
- "the compiler must be able to unroll the loop": initialiser, test and `next` must be constants,
  and `next` is limited to `++`, `--`, `+=`, `-=` (quick reference). The quick reference's control
  table lists no `while` or `do`; it does not say they are rejected in so many words, so that is
  **unconfirmed**. In practice assume bounded `for` only.
- Skia can lift the ES2 restrictions internally (`maxVersionAllowed`), but the header says that is
  for tests and certain internal effects and that the raster and pipeline-stage paths "remain
  largely ES3-unaware" ([SkRuntimeEffect.h](https://raw.githubusercontent.com/google/skia/main/include/effects/SkRuntimeEffect.h)).
  App code gets the ES2 behaviour.

Built-ins ([quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)).
Trig, exponential, common (`mod`, `clamp`, `saturate`, `mix`, `step`, `smoothstep`, ...),
geometric (`refract`, `reflect`, ...), `inverse` for square matrices, vector relationals, and the
colour functions `unpremul`, `toLinearSrgb`, `fromLinearSrgb`. The list contains no derivative
functions (`dFdx`, `dFdy`, `fwidth`), no `texture2D`, no noise function. That the list is
exhaustive is my reading of the page: **unconfirmed** that derivatives are rejected rather than
merely undocumented.

Uniforms and children ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)).

- Two primitive uniform kinds: float and int, with scalar, vec2/3/4 and `float[]`/`int[]` setters
  (matrices go through the float array). Undeclared or unset primitives default to 0; unset colour
  and shader uniforms are "undefined".
- A setter with the wrong arity throws `IllegalArgumentException`; the Java class enforces no size
  limit itself ([AOSP RuntimeShader.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RuntimeShader.java)).
  Any total-uniform-size or array-length cap lives in native Skia
  (`uniformSize()` exists, [SkRuntimeEffect.h](https://raw.githubusercontent.com/google/skia/main/include/effects/SkRuntimeEffect.h));
  no number is documented: **unconfirmed**.
- Child types: shader, colour filter, blender (Skia `ChildType`); on Android `setInputShader`,
  `setInputColorFilter` (36), `setInputXfermode` (36), `setInputBuffer`.
- `uniform shader x; x.eval(coord)` replaces `texture2D`. There are no sampler types. Evaluate any
  Android `Shader`: bitmap, gradient, another `RuntimeShader`. Complex shaders "can be expensive to
  evaluate, particularly in a loop" ([quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)).
- Bitmap shader coordinates are not normalised: (0,0) top-left to (width,height) bottom-right, and
  are in the canvas's local space ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)).
- `setInputBuffer(name, BitmapShader)` returns raw samples: no colour-space conversion, no
  premultiplication; for normals, heightmaps and other non-colour data (RuntimeShader reference).
  The quick reference adds that this "will avoid ... filtering"; the RuntimeShader page says
  filtering and tiling do work. These two pages disagree; **unconfirmed** which is current.

Colour.

- The working colour space is the destination's (usually from `Window.setColorMode`), so a shader
  does not know it. `layout(color)` on a `vec3`/`vec4`/`half4` uniform makes Android convert the
  supplied sRGB colour into the working space; without it, no conversion
  ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader),
  [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl),
  Skia: `layout(color)` only on vec3/vec4 and colours are unpremultiplied sRGB,
  [SkSL docs](https://skia.org/docs/user/sksl/)).
- `toLinearSrgb` / `fromLinearSrgb` convert between the working space and
  `LINEAR_EXTENDED_SRGB` (sRGB primaries, linear transfer, values may leave 0..1).
- Return value must be premultiplied: `[R*A, G*A, B*A, A]`
  ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)).
- Colour filter and blender inputs are sRGB and outputs are read as sRGB (API 36 pages), unlike the
  shader's working space.

## 3. Where it can run

| Surface | Status | Source |
|---|---|---|
| `Paint.setShader` on `Canvas` draws (shapes, text, `drawPaint`) | Documented | [Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl) |
| `View.setRenderEffect` / `RenderNode.setRenderEffect` with `createRuntimeShaderEffect` | Documented; applies to the node and all children | Using AGSL; [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect) |
| Compose `ShaderBrush`, `drawWithCache` | Documented | [Brush page](https://developer.android.com/develop/ui/compose/graphics/draw/brush) |
| Compose `graphicsLayer(renderEffect = ...)` | Parameter exists; always forces an offscreen buffer | [graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) |
| Colour filter / blend-mode slots of a Paint (36+) | Documented | API 36 pages |
| Hardware vs software canvas | **unconfirmed** (no page states it; the docs describe the GPU pipeline only) | |
| `SurfaceView` / `TextureView` content, system wallpaper service, `RemoteViews` | **unconfirmed**: no primary page addresses AGSL there. `SurfaceView` content is composed by SurfaceFlinger outside the app's RenderNode pipeline ([AOSP SurfaceView](https://source.android.com/docs/core/graphics/arch-sv-glsv)), so a `RenderEffect` on the hosting view cannot be assumed to see it. This is my inference. | |

Notes.

- The `RenderEffect` input is the RenderNode's drawn contents. The guide's example shows the effect
  covering a button and its siblings in the same hierarchy but not a floating action button in a
  different View hierarchy ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)).
- Multiple inputs: a runtime-shader effect has one named input (the node). Anything else comes in as
  extra `uniform shader`s set with `setInputShader` (bitmap, gradient, another RuntimeShader). Effects
  compose with `createChainEffect(outer, inner)` ("result = outer(inner(source))"; the docs advise
  explicit chaining at creation time over chain effects), `createBlendModeEffect(dst, src, mode)`,
  `createBlurEffect(..., inputEffect, ...)` and `createColorFilterEffect(filter, effect)`
  ([RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect)).
  Whether `createRuntimeShaderEffect` itself can take another effect as its input is not stated;
  the chain/blend wrappers are the documented way.
- `Window` colour mode sets the destination space, so wide-gamut windows change shader maths unless
  `setWorkingColorSpace` (37) pins it.

## 4. Performance and limits

What the primary sources actually say.

- One draw, one fragment shader. A Paint's shader, colour filter, blend, clip, coverage and colour
  conversion all become functions in a single GPU fragment shader; "Your AGSL effect contributes a
  function (or functions)" to it ([AGSL overview](https://developer.android.com/develop/ui/views/graphics/agsl)).
  So a Paint-shader effect costs its ALU and `eval` taps per covered pixel and adds no pass.
- A `RenderEffect` on a view is "more expensive than drawing a custom View"
  ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)).
  In Compose, setting a `RenderEffect` "always renders content into an offscreen buffer regardless of
  the `CompositingStrategy`"; the offscreen texture is sized to the layout bounds, so overdraw outside
  them is clipped unless `LayerOutsets` (Compose 1.12.0-alpha01+) are given
  ([graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
  For the View path I did not find the equivalent HWUI statement: the part of
  [RenderProperties.cpp](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/RenderProperties.cpp)
  I read stores image filters without forcing a layer there, so where HWUI promotes an effect node to
  a layer is **unconfirmed**. The project's own measurements already treat it as a layer.
- Shader evaluation in loops is expensive (quick reference). Loops unroll at compile time, so loop
  bounds multiply code size as well as per-pixel work.
- Create the shader once and reuse it; the Compose docs scope `RuntimeShader` in `drawWithCache` and
  update size-dependent uniforms there and per-frame uniforms in `onDrawBehind`
  ([Brush page](https://developer.android.com/develop/ui/compose/graphics/draw/brush),
  [Compose performance skill](https://developer.android.com/agents/skills/jetpack-compose/jetpack-compose-performance/skill)).
  The guide says constructing `RuntimeShader` "also compiles the shader"
  ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)); no compile
  time figure is documented, and whether compiled programs are cached across shaders with equal source
  is **unconfirmed** (the launcher's own pre-warm already assumes a first-use cost).
- Animation is app-driven: the guide animates by updating an `iTime` uniform from a `ValueAnimator`
  ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)). There is no
  built-in clock and nothing redraws unless the View invalidates.
- Backend: the docs say "Android issues a GPU accelerated drawing operation" and assembles one
  fragment shader; they do not name Ganesh, Graphite, GL or Vulkan. Which backend and API a device
  uses is **unconfirmed** from primary docs. The Skia header notes the software renderer's separate
  limits for runtime effects ([SkRuntimeEffect.h](https://raw.githubusercontent.com/google/skia/main/include/effects/SkRuntimeEffect.h)).
- Skia marks `SkRuntimeEffect` "experimental and subject to change" (same header), though the
  Android API is stable.
- Low-end GPUs: no primary statement. `mediump`/`half` use is the generic GPU advice
  ([AGI shader performance](https://developer.android.com/agi/frame-trace/shader-performance)).

## 5. What AGSL cannot do versus GLSL / OpenGL ES / Vulkan

All from the pages cited in section 2, unless noted.

- No texture objects or samplers. Only `Shader.eval`, so no texture arrays, cube maps, mip-level
  control, `textureLod`, or shadow samplers. Bicubic filtering is also not available for raw
  shaders in Skia ([SkSL docs](https://skia.org/docs/user/sksl/)).
- No framebuffer read or feedback. A shader returns a colour for one pixel in a draw; it cannot read
  the destination except through `RuntimeXfermode(src, dst)` (36+) or a RenderEffect input. There is
  no way to read the previous frame; that needs two bitmaps you swap yourself (my inference; the
  docs are silent).
- One colour output per draw: no multiple render targets, no `gl_FragDepth`, no stencil, no
  `discard` (explicitly disallowed), no `gl_FragCoord` in framebuffer terms (`fragCoord` is local).
- No vertex stage: Canvas supplies geometry. (Skia has an experimental `Mesh` with vertex and
  fragment SkSL, [SkSL docs](https://skia.org/docs/user/sksl/); an Android-public mesh API was not
  checked: **unconfirmed**.)
- No compute, no atomics or barriers in app AGSL. Skia's compute programs are internal
  ([SkSL README](https://raw.githubusercontent.com/google/skia/main/src/sksl/README.md) mentions them
  separately).
- No derivatives, no preprocessor, no recursion, unbounded loops, dynamic array indexing, or
  non-constant `for` bounds.
- No `lowp` (promoted to `mediump`), only ES 1.0 type set: no integer bit ops beyond what ES1 offers,
  no `uint`, no 3-D textures, no arrays of arrays.
- Output is premultiplied alpha.

Escape hatches (all outside AGSL; sources are the platform docs for each):

- Pre-render once to a `Bitmap`/`HardwareBuffer` and feed it back as a `BitmapShader` /
  `setInputBuffer` (lookup tables, noise, history for a manual ping-pong).
- Bake data into uniforms (`float[]` arrays) for small tables.
- Draw on a separate `SurfaceView` / `TextureView` with OpenGL ES or Vulkan, then let the compositor
  place it ([SurfaceView and GLSurfaceView](https://source.android.com/docs/core/graphics/arch-sv-glsv),
  [TextureView](https://developer.android.com/reference/android/view/TextureView): hardware-accelerated
  window only). The launcher already uses "its own surface" for scenes.
- Use several chained `RenderEffect`s or draws for multi-pass work; each pass is a separate layer.
- Use `RuntimeColorFilter` / `RuntimeXfermode` (36+) when the work is per-pixel colour mapping so the
  effect rides the draw's own fragment shader.

## 6. Relevance to this project

What the repo already does matches the documented model, and the docs back several of its decisions.

- **Paint shader over RenderEffect where bounded.** The 10-22 spec (decisions 6 and 7) keeps sparks
  and bounded overlays as Paint shaders and reserves `RenderEffect` for the live pane. That follows
  the guide's "more expensive than drawing a custom View" and Compose's "always offscreen" statement.
- **Reuse and pre-warm.** "Compile once, set uniforms on change" is the documented usage
  (construction compiles). Pre-warm in round 1 stays right; the compile time is not published, so
  keep measuring it.
- **No sampler, no feedback.** Trails and phosphor persistence cannot read the last frame in AGSL.
  Cursor trail is already done on the Java side (`KittyCursorTrail` into a quad); any future
  persistence glow needs the manual two-bitmap route above or a second surface.
- **CRT shader limits that already bite.** The glow's 4-tap sample, 1 px unsharp and chromatic
  aberration are all constant-count `eval` taps. Loop-based blur is allowed only with constant bounds
  and is expensive per tap; keep tap counts small, or chain a `createBlurEffect` as input.
- **Colour.** Tints and CRT phosphor colours go through `layout(color)` to follow the window's
  working space; maths for glow or bloom is more faithful after `toLinearSrgb`. The `half4` return
  must be premultiplied, which matters for glass panes where alpha is below 1.
- **Wallpaper and surfaces.** The project's "scene on its own surface" finding fits the SurfaceView
  composition model. The system wallpaper path and `RemoteViews` widgets are **unconfirmed** for AGSL;
  treat as unsupported until tested on pong.
- **API gate.** `PaneRetroEffect.available()` (API 33+) matches `RuntimeShader`'s level. A colour-filter
  pass for the flat fallback (API 36) is possible on newer devices only.
- **Worth a look.** (a) `RuntimeColorFilter` / `RuntimeXfermode` for tint and scanline overlays at
  API 36+: they join the draw's own fragment shader and avoid a layer. (b) `setWorkingColorSpace` at
  API 37 to pin effects to one space on wide-gamut windows. (c) `LayerOutsets` (Compose 1.12) if any
  Compose surface gets a `RenderEffect` with glow beyond its bounds; the launcher is mostly Views, so
  this is a note.

## Open questions

1. Does HWUI turn a View `RenderEffect` into an offscreen layer every frame, and at what size? Needs a
   read of HWUI's `RenderNode`/`SkiaRecordingCanvas` source (not fetched) or a Perfetto trace.
2. Are `while` loops, derivatives and dynamic array indexing rejected by AGSL or merely absent from
   the docs? A one-line shader per case on pong would settle it.
3. Does AGSL run on a software `Canvas` (Bitmap-backed) and in `RemoteViews`? Unverified.
4. Maximum uniform bytes / array length and per-shader compile cost; none is documented.
5. Which backend (Ganesh GL, Ganesh Vulkan, Graphite) each target device uses; not stated by the docs.
6. Whether `setInputBuffer` applies filtering (the two doc pages disagree).
7. Compose `RenderEffect.asComposeRenderEffect()` for AGSL: not shown on any page fetched.
