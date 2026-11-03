# AGSL shader performance and tap-to-pixel latency: research for termux-launcher

Date: 2026-10-22. Status: research only. Nothing is built, and no decision is recorded here.
Companion to [kitty-custom-shaders-research.md](kitty-custom-shaders-research.md) (kitty shader model,
AGSL port differences, API levels, battery, licensing) and
[kitty-shaders-community-research.md](kitty-shaders-community-research.md) (community findings,
including the power measurements). Latency starts from
[../../active/input-latency-study.md](../../active/input-latency-study.md) (chrome and wall latency,
the `ChromeRenderer` work) and
[../../active/keyboard-touch-engine-2026-10-16.md](../../active/keyboard-touch-engine-2026-10-16.md)
(the tap model). This note does not repeat those.

Conventions: **(inference)** is my reading with no source. **(unverified)** was not checked or could
not be reached. Android and AOSP links are to `refs/heads/main` as fetched on 2026-10-22; they are
not pinned to a commit, so line numbers for AOSP files are "at fetch" and may drift. Local paths are
relative to the repo root. Raw downloads are kept under the session scratchpad `agsl-latency/`.

## 0. Answer in brief

**A. AGSL.**

- **A shader costs power only while frames are being produced.** Per-pixel cost matters for how
  long the GPU is busy, but the larger lever is not producing frames at all. Wire every effect as
  event-driven: arm a `Choreographer.FrameCallback` on an event, re-post it only while something
  moves, and stop when settled. The repo already does this for the cursor trail
  (`PaneMotionOverlayView.java:249-271`) and has no time uniform on the CRT/TFT effect
  (`PaneRetroEffect.java:152-174`). The "lower fps" knob is the wrong lever (community note: a live
  60 fps CRT cost about +16 W of GPU on one laptop, a still shader nothing).
- **Compile once, write uniforms only when they change.** `new RuntimeShader(src)` parses the
  SkSL on the calling thread ([Shader.cpp:250-258](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/jni/Shader.cpp)).
  Every `set*Uniform` call discards the native shader and rebuilds it on the next draw
  ([RuntimeShader.java:398-412](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RuntimeShader.java)).
  Tunables that never animate belong in `const`, which AGSL folds
  ([AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl)).
- **Prefer a small drawn shape with a `Paint` shader over a `RenderEffect` on a parent view.** The
  docs say a `RenderEffect` shader is "more expensive than drawing a custom View"
  ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)). hwui
  promotes any node with an image filter to an offscreen `RenderLayer`
  ([RenderProperties.h:552-558](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/RenderProperties.h)),
  so the filter re-runs over the whole view every time that view's content changes. On a terminal,
  that is every echoed character and every cursor blink **(inference)**.

**B. Latency.**

- **The biggest single delay is chosen by design: a character key commits on finger-up, not on
  finger-down.** `Pointers.onTouchDown` only starts the press (feedback, haptic, sound);
  `onTouchUp` sends the key (`inapp-keyboard/.../Pointers.java:298-312`, `:169-218`). The finger's
  dwell between down and up sits in front of every character. Committing plain taps on down would
  remove it, at the cost of swipe legends and the release-time tap correction. Size of the gain:
  **(unverified)**, measure the dwell first (section B.4).
- **Everything after the key is committed is already short and mostly a vsync wait.** Write is a
  queue copy plus a writer thread; echo returns through a reader thread and one main-thread
  handler message; the draw is bounded by the next vsync. The measurable wins left are keeping that
  frame cheap (no `RenderEffect` re-run per echo, no cold caches) and skipping one coalescing post.
- **`requestUnbufferedDispatch`, front-buffered rendering and predictive echo are not worth it for
  this path.** The input stack batches only `MOVE`; `DOWN` and `UP` already arrive immediately
  ([InputConsumer.cpp:316-322](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/libs/input/InputConsumer.cpp)).
  Front-buffering needs its own `SurfaceView` layer, which the latency study already rejected for the
  terminal (A6). Mosh-style prediction hides network latency; the PTY here is local.

## 1. Part A: performant, power-efficient AGSL

### 1.1 What AGSL is on the GPU, which sets the cost model

| Fact | Source |
|---|---|
| An AGSL shader is one function inside a single GPU fragment shader that also holds clipping, geometry, blend and colour-space code. It does not own the whole fragment stage. | [Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl), [RuntimeShader reference](https://developer.android.com/reference/android/graphics/RuntimeShader) |
| It is GLSL ES 1.0 syntax "to provide for maximum device reach"; `for` loops exist "with limitations", and some control structures need compile-time constants. | [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl), [AGSL](https://developer.android.com/develop/ui/views/graphics/agsl) |
| Skia's runtime effects default to `maxVersionAllowed = k100`, the ES2 restriction level, and only tests and internal effects lift it. | [SkRuntimeEffect.h:143-148](https://skia.googlesource.com/skia/+/refs/heads/main/include/effects/SkRuntimeEffect.h) |
| `half`, `short` are medium precision; `float` is high. `#define` is not supported, use `const` (the compiler does constant folding and branch elimination on `const`). | [AGSL vs GLSL](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl) |
| Sampling is `shader.eval(coord)`, not a texture fetch; BitmapShader coordinates are in pixels, not normalised. A `RenderEffect` hands the view's own pixels in as one `uniform shader`. | [RuntimeShader reference](https://developer.android.com/reference/android/graphics/RuntimeShader), [Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl) |
| The returned colour must be premultiplied. | [RuntimeShader reference](https://developer.android.com/reference/android/graphics/RuntimeShader) |
| API 33 for `RuntimeShader` and `createRuntimeShaderEffect`. | [reference](https://developer.android.com/reference/android/graphics/RuntimeShader) ("Added in API level 33") |

### 1.2 Rules of thumb

Each rule has its source and the way this app already stands.

1. **Create the `RuntimeShader` once, off the hot path.**
   - The constructor "also compiles the shader"
     ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)); in the
     framework that is `SkRuntimeEffect::MakeForShader` on the caller's thread, and a failure
     throws `IllegalArgumentException` ([Shader.cpp:250-258](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/jni/Shader.cpp)).
   - The SkSL-to-GPU program is built when Skia first needs it at draw time; hwui keeps a persistent
     disk cache that Skia calls "when it needs to compile a new SKSL shader"
     ([ShaderCache.h](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/pipeline/skia/ShaderCache.h)).
     So first use on a cold device can stall the RenderThread once **(inference)**; a second launch
     should hit the disk cache **(unverified, no measurement)**.
   - Here: `PaneRetroEffect` builds each family lazily once and keeps it
     (`PaneRetroEffect.java:152-162`); `PaneGlassBackdropView.java:156` says the same for glass.
   - Cheap hardening: when the user turns an effect on, build the shader and issue one tiny draw
     with it before the first visible frame, so the first-use compile lands behind the settings
     tap, not on a typed character. **(inference)**, measure with a `Trace` section.
2. **Write uniforms when a value changes, not each draw.**
   `setFloatUniform` calls `nativeUpdateUniforms` and then `discardNativeInstance()`; the next draw
   calls `makeShader` again ([RuntimeShader.java:396-412](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RuntimeShader.java),
   [Shader.cpp:269-275](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/jni/Shader.cpp)).
   That rebuild is a small object, not a recompile **(inference: the program text is unchanged)**,
   but it is per call. Batch with the array overloads, and skip writes whose value is unchanged
   (`PaneRetroEffect` re-wraps only "when what it draws moved", header comment).
   Changing a `const` means a new `RuntimeShader` and a new compile, so animate through uniforms.
3. **Use `half` for colour and most arithmetic; keep `float` for positions.**
   - Arm: reduced precision uses smaller arithmetic units and less energy, can pair two fp16
     operations per fp32 slot, and narrows memory traffic
     ([Precisions on Arm Mali GPUs, 3.1.2](https://documentation-service.arm.com/static/5eff5d5edbdee951c1cd5f20));
     16-bit "is normally sufficient ... especially for fragment shading when computing an output
     colour", and Mali does not distinguish `lowp` and `mediump`
     ([Arm GPU Best Practices 3.4, 10.1](https://documentation-service.arm.com/static/67a62b17091bfc3e0a947695)).
   - The same Arm note shows a pulsing-light shader whose time uniform in `mediump` loses
     resolution as the clock grows. So wrap time in Java (`t % period`) before writing it, and keep
     any sampling coordinate in `float` (fp16 addresses only about 2048 sub-texel steps,
     [3.2.4](https://documentation-service.arm.com/static/5eff5d5edbdee951c1cd5f20)).
   - Adreno and PowerVR guidance **(unverified, not reached)**; one Adreno forum report of
     mediump differences between chips is anecdote only.
4. **Keep loops short and constant-bound, and make branches uniform.**
   - ES2-level loops need constant bounds (rows above). Arm: "branching can be expensive on a GPU",
     minimise control-flow divergence in neighbouring threads, use `min`, `max`, `clamp`, `mix` to
     avoid small branches ([Arm GPU Best Practices 3.4, 10.11](https://documentation-service.arm.com/static/67a62b17091bfc3e0a947695)).
   - kitty's `northern-lights` ray-marches 50 steps; that is a desktop budget (existing note, 4.1).
     A terminal effect should stay in the tens of ALU ops per pixel **(inference)**; the old spec's
     target was "under about 60 ALU ops per pixel, with no texture reads".
5. **Count `eval()` calls: each is a sample of the whole shader tree under it.**
   `PaneRetroEffect`'s CRT glow takes five `content.eval` per pixel (`PaneRetroEffect.java`, CRT
   source, `content.eval(sp)` and the four neighbours). Arm advises the lowest resolution and
   narrowest precision for texture reads, and notes that `highp` samplers can be half the speed
   ([Arm GPU Best Practices 3.4, texture sampling](https://documentation-service.arm.com/static/67a62b17091bfc3e0a947695)).
   For a blurry term, sample fewer, further apart, or on a downscaled input **(inference)**.
6. **Bound the area and the overdraw.**
   A `Paint` shader runs for the pixels of the shape drawn. Draw the cursor trail's bounding quad,
   not the pane (the repo's trail already paints "the quad ... only" while moving,
   `PaneMotionOverlayView.java:304`). Arm: overdraw "wastes compute and memory bandwidth"
   ([3.4, Avoid overdraw](https://documentation-service.arm.com/static/67a62b17091bfc3e0a947695)).
   Full-screen passes on a tile-based GPU also force the tile buffer to be written out and read
   back; Arm's tile-based rendering note is the place to read the mechanism
   ([Tile-Based Rendering](https://documentation-service.arm.com/static/636dfb934e6cf12278ad8c27?token=), **unverified**, search hit only).
7. **Pick `RenderEffect` only when the effect must read what a view drew.**
   - Cost: the node becomes a `RenderLayer` (offscreen), and the filtered result is a snapshot
     (`SkImages::MakeWithFilter`) ([RenderNodeDrawable.cpp:241-290](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/pipeline/skia/RenderNodeDrawable.cpp)).
   - Benefit: with `enableRenderEffectCache` (default `true`,
     [Properties.cpp:71](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/Properties.cpp))
     the snapshot is rebuilt only when the layer or the filter changes
     (`RenderNode::updateSnapshotIfRequired`, [RenderNode.cpp:345-390](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/RenderNode.cpp)).
     A static view with an effect costs nothing per frame; a view that changes, costs the filter
     per change.
   - A comment in the same file records a vendor driver hang that made the cache unsafe on some
     devices (`b/193145089`, `b/197263715`): the cache may be off on some GPUs, so a static view
     with an effect may pay per frame there **(unverified which devices)**.
   - So: a CRT or glass `RenderEffect` over a **terminal** pane re-runs on every echo and blink. A
     `RenderEffect` over a **static** surface (dock, status bar) is cheap. If the effect cannot use
     `eval(content)`, draw a shader into a small shape instead.
8. **Prefer pre-multiplied, opaque output where possible; mark colour uniforms `layout(color)`.**
   Colour uniforms need `layout(color)` and `setColorUniform` so they follow the working colour
   space ([RuntimeShader reference](https://developer.android.com/reference/android/graphics/RuntimeShader)).
   Opaque output avoids blending cost **(inference)**.
9. **Software canvases refuse a `RuntimeShader`.** The repo already guards this
   (`PaneGlassBackdropView.java:681`).

### 1.3 Idle and zero-cost wiring

Principle: no frame is requested unless some value is changing. The community finding applies
directly: a continuously redrawing shader holds a GPU power state, so a lower frame rate saves
little, and an event-driven shader costs about nothing
([community note, section 0](kitty-shaders-community-research.md)).

| Rule | Mechanism and source |
|---|---|
| One driver per effect, owned by the view, armed by events | `Choreographer.postFrameCallback` on an event; the callback re-posts itself **only** while `needsMoreFrames`. Repo model: `PaneMotionOverlayView.java:81, 249-271` ("idempotent and cheap to call every frame: it only arms the Choreographer loop, which decides for itself whether anything needs to keep animating"). |
| No `postInvalidateOnAnimation` or `ValueAnimator` while settled | A settled effect draws nothing and requests nothing (`PaneMotionOverlayView.java:304`, `:354`). |
| Do not invalidate when only an invisible thing changed | `TerminalView` skips the blink invalidate while the page is off screen (`TerminalView.java:4454`, `mWallOffScreen`). |
| Cancel on lifecycle | `onDetachedFromWindow`, `onVisibilityChanged`, `onWindowFocusChanged`, `onPause`: remove the frame callback. `Choreographer.removeFrameCallback` at `PaneMotionOverlayView.java:271`. |
| Honour system motion switches | `ValueAnimator.areAnimatorsEnabled()` (API 26) is false when the animator scale is 0 or Battery Saver is on ([reference](https://developer.android.com/reference/android/animation/ValueAnimator)); `PowerManager.isPowerSaveMode()` and `getCurrentThermalStatus()` are the other two gates ([reference](https://developer.android.com/reference/android/os/PowerManager)). The existing animated-wallpaper pause rules (spec, since deleted) listed the same cases. |
| Keep the display fast while you animate, do not hint it lower | The framework boosts the frame rate on a handled touch for 3 s (`FRAME_RATE_TOUCH_BOOST_TIME = 3000`, [ViewRootImpl.java:1131, 8128-8150](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewRootImpl.java)). `View.setFrameContentVelocity` (API 35) is a hint that lets the system choose a *lower* rate ([reference](https://developer.android.com/reference/android/view/View)); it is a power tool for scrolling content, not a latency tool. **(inference on intent)** |
| Prefer a time-free effect | A static scanline or grid (the repo's retro shaders: phase from screen position, no clock) costs frames only when the view repaints. |
| Event-driven animations take their time from the frame callback | Use `frameTimeNanos` from the callback as the clock, so a dropped frame does not stretch the effect, and compute `t` per-effect rather than per-wall-clock. |

### 1.4 Skeleton

Java, API 33+. A time-driven effect that costs nothing when idle: a short comet behind the cursor.
The shader is a `Paint` shader on a bounded rectangle, not a `RenderEffect`.

```java
final class IdleTrail {
    // const folds at compile time; animate through uniforms only.
    private static final String SRC =
        "uniform float2 uHead;\n" +          // cursor centre, px
        "uniform float2 uTail;\n" +          // where it came from, px
        "uniform float  uFade;\n" +          // 1 -> 0 over the trail's life
        "layout(color) uniform half4 uColor;\n" +
        "const float R = 6.0;\n" +
        "half4 main(float2 p) {\n" +
        "  float2 ab = uHead - uTail; float2 ap = p - uTail;\n" +
        "  float t = clamp(dot(ap, ab) / max(dot(ab, ab), 1.0), 0.0, 1.0);\n" +
        "  half d = half(length(ap - ab * t)) / half(R);\n" +
        "  half a = (1.0 - clamp(d, 0.0, 1.0)) * half(uFade) * half(t);\n" +
        "  return uColor * a;\n" +            // premultiplied
        "}\n";

    private final RuntimeShader shader = new RuntimeShader(SRC);   // once
    private final Paint paint = new Paint();
    private final Choreographer ch = Choreographer.getInstance();
    private final Choreographer.FrameCallback tick = this::onFrame;
    private final View host;
    private final RectF quad = new RectF();
    private long startNs; private boolean running;

    IdleTrail(View host) { this.host = host; paint.setShader(shader); }

    /** Call from the cursor-moved event. Idempotent. */
    void onCursorMoved(float fromX, float fromY, float toX, float toY, int argb) {
        shader.setFloatUniform("uTail", fromX, fromY);
        shader.setFloatUniform("uHead", toX, toY);
        shader.setColorUniform("uColor", argb);
        quad.set(Math.min(fromX, toX) - 8, Math.min(fromY, toY) - 8,
                 Math.max(fromX, toX) + 8, Math.max(fromY, toY) + 8);   // bounded area
        startNs = 0;
        if (!running) { running = true; ch.postFrameCallback(tick); }
    }

    private void onFrame(long frameNs) {
        if (startNs == 0) startNs = frameNs;
        float f = 1f - (frameNs - startNs) / 180_000_000f;             // 180 ms life
        if (f <= 0f) { running = false; host.invalidate(); return; }   // settle: no re-post
        shader.setFloatUniform("uFade", f);
        host.invalidate((int) quad.left, (int) quad.top, (int) quad.right, (int) quad.bottom);
        ch.postFrameCallback(tick);                                    // only while moving
    }

    void onDetached() { ch.removeFrameCallback(tick); running = false; }

    void draw(Canvas c) { if (running) c.drawRect(quad, paint); }      // idle: draws nothing
}
```

The repo already has a fuller version (`PaneMotionOverlayView`, `CursorTrail*`); this skeleton is the
shape to copy for any new effect, not a replacement.

### 1.5 How to measure shader cost

| Question | Method | Source |
|---|---|---|
| Does it cost GPU time per frame? | `FrameMetrics.GPU_DURATION` (API 31, "total time ... this frame took to complete on the GPU") and `DRAW_DURATION`, `COMMAND_ISSUE_DURATION`, `SWAP_BUFFERS_DURATION`, via `Window.addOnFrameMetricsAvailableListener`; compare effect on and off over the same scripted input. | [FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics) |
| Does it jank on first use? | Perfetto FrameTimeline: red slice means the app was the cause; a blue slice is a dropped frame because UI state missed the RenderThread; capture a cold start of the effect. | [frametimeline.md](https://raw.githubusercontent.com/google/perfetto/main/docs/data-sources/frametimeline.md) |
| Does idle stay idle? | Take a 60 s Perfetto trace with the effect enabled and the screen idle; the app should show no `Choreographer#doFrame` slices after the last event. The GPU counters and clocks are in the GPU data source. | [gpu.md](https://raw.githubusercontent.com/google/perfetto/main/docs/data-sources/gpu.md) |
| What does it cost in energy? | Power-rail counters (ODPM) where the device exposes them, otherwise battery counters; resolution depends on the device. | [battery-counters.md](https://raw.githubusercontent.com/google/perfetto/main/docs/data-sources/battery-counters.md) |
| Is it the shader or the layer? | Add `Trace.beginSection` around effect setup and uniform writes; look at the RenderThread slices for the layer re-record next to a terminal echo. | repo already traces `Terminal.render`, `Terminal.append` |

Pong's `gfxinfo` and Perfetto loop from the latency study is the right harness
([input-latency-study.md](../../active/input-latency-study.md), "Measured today").
Whether pong's GPU exposes power rails and the `gpu.md` counters is **(unverified)**.

## 2. Part B: tap-to-pixel latency

### 2.1 The pipeline, with this app's code on it

Code paths below were read in this repo on `v2-redesign` (2026-10-22). Android stages are AOSP
`main` as fetched.

| # | Stage | What happens | Where | Verdict |
|---|---|---|---|---|
| 0 | Finger and panel | Touch scan, touch IC, driver | hardware | **(unverified)**, outside the app |
| 1 | InputReader, InputDispatcher, socket | Event read, dispatched to the app's channel | [input.sql](https://raw.githubusercontent.com/google/perfetto/main/src/trace_processor/perfetto_sql/stdlib/android/input.sql) names the stages: read, dispatch, receive, consume, frame | outside the app; measurable |
| 2 | App receive, batching | `MOVE` and `HOVER_MOVE` start a batch; `DOWN` and `UP` do not. A batch is delivered on the Choreographer input callback, resampled 5 ms behind the frame time | [InputConsumer.cpp:150, 316-322, 400-412](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/libs/input/InputConsumer.cpp), [ViewRootImpl.java:10552-10562](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewRootImpl.java) | `DOWN` and `UP` are immediate: good. `MOVE` waits for the frame, so swipe legends, sliders and the swipe trail lag by up to a frame **(inference)** |
| 3 | View dispatch | `Keyboard2View.onTouch` handles `ACTION_DOWN`: finds the key, `startTouchFx`, `_pointers.onTouchDown`; calls `postInvalidateOnAnimation()` | `inapp-keyboard/.../Keyboard2View.java:1670-1735` | Fine. The key view repaints on the next frame, so the pressed look shows within one vsync |
| 4 | Press feedback | `onPointerDown`: `updateFlags`, `key_down`, `invalidate`, `vibrate`, `playKeySound` | `Keyboard2View.java:1596-1603`, `VibratorCompat.java:12-33` | Good: feedback is on **down**. Haptic is a binder call from the UI thread (`ViewRootImpl.java:9755-9761`, `getSystemVibrator().performHapticFeedback`); with a custom duration it fetches `Vibrator` and builds a `VibrationEffect` per tap |
| 5 | Commit (key sent) | A plain tap sends on **release**: `Pointers.onTouchUp` removes the pointer and calls `_handler.onPointerUp`; `Keyboard2View.settleTap` resolves the corrected key first | `Pointers.java:169-218`, `Keyboard2View.java:1757`, `keyboard-touch-engine` decision 3 ("Only a press that ends as a plain tap is corrected, on release") | **The biggest delay**: finger dwell plus the correction step sit in front of every character |
| 6 | Dispatch to the terminal | `TerminalKeyEventHandler.dispatch` runs the interceptor chain, `Char` case, `inputCodePoint` | `app/.../inappkeyboard/TerminalKeyEventHandler.java:164-245` | A switch and one interceptor call; negligible **(inference)** |
| 7 | Terminal input | `TerminalView.inputCodePoint` sets the cursor visible, applies Ctrl mapping, calls `mTermSession.writeCodePoint` | `TerminalView.java:2843-2882`; `TerminalSession.java:269-300` | Good: cursor is forced visible on every key (`:2850`) |
| 8 | PTY write | `TerminalSession.write` takes `mWriteLock`, copies into a 4 KiB `ByteQueue`; a writer thread blocks on `read` and writes to the fd | `TerminalSession.java:249-256`, `:65`, `:210-225`; `ByteQueue.java:66-110` | Same shape as upstream Termux. One thread hop (condition-variable wake), no main-thread I/O. The writer can only stall on a full queue (the main thread waits then, `ByteQueue.java:75-80`) |
| 9 | Shell echo | The shell writes the echo; reader thread blocks in `read`, then `sendEmptyMessage(MSG_NEW_INPUT)` to the main handler | `TerminalSession.java:191-208` | Identical to [upstream](https://raw.githubusercontent.com/termux/termux-app/master/terminal-emulator/src/main/java/com/termux/terminal/TerminalSession.java) (`:140-142`). Latency is the shell's: a fast shell echoes in well under a frame **(inference)** |
| 10 | Parse on the main thread | `MainThreadHandler.handleMessage` calls `drainProcessOutputSlice`: reads the queue, `mEmulator.append`, `notifyScreenUpdate` | `TerminalSession.java:609-625, 661` | Parse is on the UI thread by design and is traced (`Terminal.append`). A main thread busy with chrome work delays this message; the latency study's chrome fixes target that |
| 11 | Redraw request | `onTextChanged` coalesces per session and then `mUiHandler.post(redraw)` (or `postOnAnimation` in Lazy mode), and the runnable calls `onScreenUpdated` | `TermuxTerminalSessionActivityClient.java:258-345` | One extra looper message compared with upstream, which calls `onScreenUpdated()` inline (`TermuxTerminalSessionActivityClient.java:118-122` upstream). The comment records why (flood interleaving). Cost for a lone keystroke is small, a message behind whatever is queued **(inference)** |
| 12 | Invalidate | `TerminalView.onScreenUpdated` fixes scroll, `invalidate()` | `TerminalView.java:1003-1033` | Fine |
| 13 | Vsync wait | `invalidate` marks the view; the traversal runs in the next Choreographer frame, after the input and animation callbacks | [Choreographer.java:280-322](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/Choreographer.java) | Up to one frame (8.3 ms at 120 Hz, 11.1 ms at 90 Hz) plus RenderThread and SurfaceFlinger. This is the floor for any non-front-buffered path |
| 14 | Draw | `TerminalView.onDraw` to `TerminalRenderer.renderRows` with a row cache replay | `TerminalView.java:3475`, `TerminalRenderer.java:1012` | Measured as 2.4 ms typical, 5.0 ms worst on the warm path; 50 ms with cold caches (`input-latency-study.md`, "Measured today", row (b)). The cache fix has landed (T6 carry-over) |
| 15 | Layers and effects | A pane card with a retro look applies a `RenderEffect` (so the pane is an offscreen layer and the CRT filter re-runs per change); glass elsewhere | `PaneRetroEffect.java:152-174`; hwui: [RenderProperties.h:552-558](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/RenderProperties.h) | Only when the look is on. This is the one stage where a feature can add GPU time to an echo frame **(inference)**, see B.2 item 3 |
| 16 | Cursor | The cursor is drawn in the same `onDraw` as the glyph, so it moves in the same frame. The blink runnable toggles state and invalidates (`TerminalView.java:4423-4460`). The trail overlay is a separate Choreographer-driven view | `PaneMotionOverlayView.java:81, 249-271` | The cursor itself has no extra latency. The trail's quad starts on the frame after the overlay learns the target **(inference)**; that is fine, it trails by design |

Not checked: where `Keyboard2View.onTouch` is registered as the view's touch handler; the default
cursor blink rate; and whether any idle animation (status clock, glass motion) keeps the frame
clock ticking while the keyboard is idle. The last one matters for B.2 item 2 and for power.

What the code does well for latency:

- Feedback (pressed key look, haptic, sound) is on finger-down (stage 4).
- `DOWN` and `UP` are never batched by the platform (stage 2), so the keyboard sees them at once.
- The PTY write path has no main-thread I/O and no per-key allocation beyond the small `KeyEvent`
  objects (stages 6-8).
- The cursor is forced visible at each keypress (stage 7).
- The screen update skips panes nobody can see (`TermuxTerminalSessionActivityClient.java:270-280`).

What is less good:

- Commit on release, plus a correction step on release (stage 5).
- One extra post between parse and invalidate (stage 11), and the parse itself on the UI thread.
- A `RenderEffect` over a live terminal pane (stage 15) when a retro look is chosen.

### 2.2 Ranked improvements

Gains are estimates from the stage table; none is measured on pong. Measure before building
(section 2.4). Cost is engineering plus risk to behaviour the developer has decided.

| Rank | Change | Expected gain | Cost and risk | Source |
|---|---|---|---|---|
| 1 | **Commit a plain character tap on `ACTION_DOWN`** for keys that have no swipe legend (keep release-commit for keys with swipes, modifiers and sliders). | The whole down-to-up dwell of a tap, before every such character. Size **(unverified)**: no primary source reached; measure it from `android_motion_events` (B.4). It is the only change that removes a human-timescale delay rather than a frame. | **High behaviour risk.** The tap model corrects the key on release (touch-engine decision 3), the take-back detector expects a release, and swipe legends need the gesture to finish. `Pointers.plainTapModifiers` (`Pointers.java:227`) already identifies a plain tap, but at release. Per-key opt-in by "no swipe legend" is a **(inference)**; the developer's call, so grill before building. | `Pointers.java:169-218, 298-312`; `keyboard-touch-engine`, decision 3 |
| 2 | **Skip the coalescing post for echo of the user's own keystroke.** `TerminalSession` already records `mLastWriteUptimeMs` (`TerminalSession.java:249-256`); a `onTextChanged` within a few tens of ms of a write can call `onScreenUpdated` inline instead of `mUiHandler.post`. Keep the post for floods. | One looper message, usually under a millisecond, more when the main queue is busy. Small. | Low code cost; risk is the flood interleaving the existing comment measured (`TermuxTerminalSessionActivityClient.java:310-330`). Gate strictly on a recent write. | upstream does it inline: [Termux client](https://raw.githubusercontent.com/termux/termux-app/master/app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java) |
| 3 | **Keep the echo frame cheap when a retro or glass `RenderEffect` is on a live terminal pane.** Options: skip the effect for the one frame after a keystroke (shows unfiltered text for 1 frame, **(inference)** probably visible), or draw the scanlines and tint with a `Paint` shader over a bounded area instead of `eval(content)`, or lower the glow taps. | Up to the filter's GPU time on every echo frame; matters only if it pushes the frame past a vsync. **(unverified)** until `GPU_DURATION` is compared with the effect on and off. | Medium; changes how the look is drawn. Do only if the measurement in B.4 shows missed frames with the effect on. | [RenderNodeDrawable.cpp:241-290](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/pipeline/skia/RenderNodeDrawable.cpp), [RenderNode.cpp:345-390](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/RenderNode.cpp) |
| 4 | **Stop idle frame sources that keep the frame clock alive** (status clock, glass motion, trails) so a lone keystroke does not land mid-frame behind other work, and so the 3 s touch boost is not fighting a lower rate. | Indirect: fewer janky frames around typing; saves power. Size **(unverified)**. | Needs the audit that was not done (above). The latency study already traced idle frames of 33-66 ms (row f). | [ViewRootImpl.java:8128-8150](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewRootImpl.java), `input-latency-study.md` row (f) |
| 5 | **Pre-warm effect shaders when enabled**, with one tiny offscreen draw, so a first-use compile does not land on a typed character. | Removes a one-time stall; a hitch on first keystroke after enabling a look **(inference)**. | Low. | [ShaderCache.h](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/pipeline/skia/ShaderCache.h) |
| 6 | **Trace the keyboard half.** Add `Trace` sections for `Keyboard.down`, `Keyboard.commit`, `Terminal.write`, so the Perfetto query can join key to frame. | None directly; makes ranks 1-5 measurable. | Very low. The app has `Touch.dispatch`, `Terminal.append`, `Terminal.render` already. | [input.sql](https://raw.githubusercontent.com/google/perfetto/main/src/trace_processor/perfetto_sql/stdlib/android/input.sql) |
| - | `View.requestUnbufferedDispatch` | **Nothing for taps**: `DOWN` and `UP` are not batched. For the swipe trail or sliders, it removes the wait for the next frame on `MOVE`, but the docs warn it can increase latency and jitter and lose system resampling: "just requesting unbuffered dispatch on most MotionEvent streams will not improve your input latency". | Not recommended without a measured swipe-lag problem. | [View#requestUnbufferedDispatch](https://developer.android.com/reference/android/view/View), [InputConsumer.cpp:316-322](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/main/libs/input/InputConsumer.cpp), [ViewRootImpl.java:10552-10562](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewRootImpl.java) |
| - | Front-buffered rendering (`CanvasFrontBufferedRenderer`, `GLFrontBufferedRenderer`, API 29+) | Could show a glyph or cursor cell about one frame earlier by drawing into a front-buffer layer "simultaneously being presented to the display". | A separate `SurfaceView` layer: the chrome composites over the terminal and the wall translates it (the study's reason for rejecting A6); the glyph would have to be drawn twice and reconciled by the real draw. Not recommended. | [CanvasFrontBufferedRenderer](https://developer.android.com/reference/androidx/graphics/lowlatency/CanvasFrontBufferedRenderer), `input-latency-study.md`, "Not worth it: A6" |
| - | Predictive local echo (Mosh) | None here. Mosh predicts echoes because the real echo is a network round trip away; its median keystroke latency under 5 ms came from that. The PTY here is local. | Wrong echoes for passwords, vim and TUIs; Mosh needed an epoch scheme and a 50 ms echo-ack timeout to avoid them. | [Mosh paper](https://mosh.org/mosh-paper.pdf), sections 3.2 and 4 |
| - | Haptic timing | Already on down. Fetching `Vibrator` per tap is cheap; `performHapticFeedback` goes through `SystemVibrator` to a system service. A longer custom pulse delays nothing in the text path. | Leave. A system haptic (`KEYBOARD_TAP`, `KEYBOARD_PRESS`, `KEYBOARD_RELEASE`) is available if the custom path ever shows up in a trace. | [HapticFeedbackConstants](https://developer.android.com/reference/android/view/HapticFeedbackConstants), `ViewRootImpl.java:9755-9761` |
| - | Variable refresh rate hints (`setFrameContentVelocity`, ARR) | None for latency; they let the system pick a lower rate. The touch boost already raises it on a handled touch. | Do not set a low-rate hint on the keyboard or terminal. | [View](https://developer.android.com/reference/android/view/View), `ViewRootImpl.java:8128-8150` |

### 2.3 What to expect, in one frame budget

At 120 Hz a frame is 8.3 ms; at 90 Hz, 11.1 ms (pong is 90 Hz, per the latency study). After the
key is committed, the best case for the platform is: write, shell echo, parse, then wait for the
next vsync, then one draw, then SurfaceFlinger and the panel. That is plausibly two to three frames
**(inference, not measured)**. A finger's dwell on a key is typically longer than all of it, which
is why rank 1 outranks the rest, and why rank 1 needs a measured dwell first.

### 2.4 How to measure tap-to-pixel on a device

**Perfetto.** The platform's own input timeline is the best instrument and needs no change to the app.

1. Record a trace with the `android.input.inputevent` data source, `sched`, `gfx`, `view`, `input`
   and `power` atrace categories and the frame timeline. The source name appears in the stdlib
   comments ([input.sql:441, 476, 500](https://raw.githubusercontent.com/google/perfetto/main/src/trace_processor/perfetto_sql/stdlib/android/input.sql));
   the exact config block is **(unverified)**, the perfetto.dev docs site could not be reached.
2. In trace processor: `INCLUDE PERFETTO MODULE android.input;` then
   `SELECT event_action, dispatch_latency_dur, handling_latency_dur, end_to_end_latency_dur FROM android_input_events WHERE process_name = '<package>'`.
   `end_to_end_latency_dur` is "input read to frame present"; the table also splits dispatch,
   handling and ACK ([input.sql:297-345](https://raw.githubusercontent.com/google/perfetto/main/src/trace_processor/perfetto_sql/stdlib/android/input.sql)).
   This covers stages 1 to 13 for the key's **press frame**, not for the echo.
3. For the echo, join by time: for each `ACTION_UP` (the commit), take the next slice named
   `Terminal.append` and the next `Choreographer#doFrame` that follows, then that frame's present
   time from the FrameTimeline. The FrameTimeline marks late presents as "high latency state"
   (light green) and janky frames red
   ([frametimeline.md](https://raw.githubusercontent.com/google/perfetto/main/docs/data-sources/frametimeline.md)).
   Tap dwell is `ACTION_UP.ts - ACTION_DOWN.ts` from `android_motion_events`; collect it from
   normal typing on pong to size rank 1.
4. Add rank 6's trace sections so the join does not rely on time order.

**Frame counters in the app.** `Window.addOnFrameMetricsAvailableListener` gives
`INPUT_HANDLING_DURATION`, `DRAW_DURATION`, `GPU_DURATION` and `TOTAL_DURATION` per frame
([FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics)); log those for
the frame after each echo in a debug build.

**High-speed camera, the ground truth.** A phone slow-motion mode at 240 fps resolves 4.2 ms. Film
the fingertip and the screen in one shot, step frame by frame: finger contact frame, key-up frame,
first frame in which the glyph shows, first frame in which the cursor has moved. Run it 30 times
per variant and compare medians, since a single tap varies by a frame either way. A bright
`adb shell input tap` injection enters at the dispatcher and skips stage 0 and part of stage 1 **(inference)**, so it
underestimates real latency; use real finger taps for the camera, injected taps for repeatable A/B
runs of the app's own stages.

**What to compare.** Release-commit versus down-commit (rank 1); echo post versus inline (rank 2);
retro look on versus off (rank 3). Each as median and 95th percentile.

## 3. Fit with termux-launcher, in one paragraph

Nothing new needs to be built for part A: the cursor trail, retro effect and glass already follow
the compile-once, event-driven pattern. What is worth doing is an audit, not a feature: list every
frame source that can run while the keyboard is idle (the status clock, glass motion, trails) and
confirm each stops, because that decides power and also how often a typed character lands behind
other work. For part B, the one change that buys a human-timescale gain is commit-on-down for plain
taps, and it collides with the tap model and swipe legends, so it is a design decision, not a patch.
The rest is small and should wait for a measured dwell and a measured echo frame.

## 4. Sources not reached

- **perfetto.dev** (the docs site) was unreachable from this machine, so Perfetto facts come from
  the `google/perfetto` repository on GitHub (`docs/data-sources/*.md`, the `android.input` stdlib
  SQL). The exact trace-config block for the `android.input.inputevent` data source and any
  input-latency case study page were **not read**.
- **Qualcomm Adreno** guidance: the developer pages redirect to a landing page; only a search
  snippet was seen. Adreno-specific rules on `mediump`, dynamic branching and texture fetch are
  **(unverified)**. Imagination PowerVR guidance **not searched**.
- **Arm's HTML docs** (developer.arm.com `101897`) are script-rendered and returned no text; the
  PDF editions were used instead (Arm GPU Best Practices 3.4; Precisions on Arm Mali GPUs). The
  tile-based rendering note was found by search and **not read**.
- **Android Developers guides** for front-buffered rendering, haptics and `agsl-best-practices`
  returned 404 or only the API reference; front-buffering is cited from the API reference alone.
  There was no `HapticFeedbackConstants` note on latency; none found.
- **No latency numbers from a primary source.** I found no AOSP or Android Developers figure for
  typical touch-down-to-up dwell, panel scan latency, or tap-to-photon latency on 90 or 120 Hz
  devices. Every duration above is an estimate marked **(inference)** or **(unverified)**.
- **Skia and hwui internals not read:** how Skia compiles an SkSL runtime effect into a GL or
  Vulkan program and the key it uses for the hwui persistent cache; the claim that a uniform change
  does not recompile the GPU program rests on the program text being unchanged **(inference)**.
- **Device behaviour:** whether pong keeps `enableRenderEffectCache` on, whether it exposes GPU
  counters or power rails, and which devices the vendor-driver workaround in
  `RenderNodeDrawable.cpp` concerns.
- **Repo code not read:** `Keyboard2View.onTouch` registration, default cursor blink rate, idle
  animation loops (status clock, glass motion), and the body of `TapCorrectionController`.
- **Mosh source** was not read; only the paper (sections 3.2 and 4).
