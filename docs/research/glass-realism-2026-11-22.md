# Glass realism: what to fix in our glass and what to build on it

Research note for the launcher's glass. Researched 2026-11-22.

Method: every claim about another project was read from that project's own source at a pinned commit (links below go to that commit, with line numbers). Android API facts come from developer.android.com reference and guide pages, fetched on 2026-11-22. Our own code is cited as `path:line` on `v2-redesign` at `4a38c03a8`, and the experiment branch `exp/glass-motion` at `8bafff2d0`. Nothing was run on a device. Anything I infer is marked **[inference]**. Paths below are relative to `app/src/main/java/com/termux/app/` unless they start with a top-level folder.

Pinned sources:

| Short name | Repository | Commit | Licence |
|---|---|---|---|
| Playground | [mejdi14/Android-AGSL-Shader-Playground](https://github.com/mejdi14/Android-AGSL-Shader-Playground/tree/6bed9959cad266600dc569ac9c857574ba225685) | `6bed995` | MIT ([LICENSE](https://github.com/mejdi14/Android-AGSL-Shader-Playground/blob/6bed9959cad266600dc569ac9c857574ba225685/LICENSE)); one file is Kyant's Apache-2.0 code |
| Mirage | [AndroidPoet/mirage](https://github.com/AndroidPoet/mirage/tree/9da9b00f678d57ce9989838ac513ffa88a11b038) | `9da9b00` | Apache-2.0, ports of Paper Design's shaders ([NOTICE](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/NOTICE)) |
| LiquidGlass | [shayann07/liquidglass](https://github.com/shayann07/liquidglass/tree/9effb52bcb9d36e8048666b935f9d4ac46b99b47) | `9effb52` | Apache-2.0, with Haze, Kyant and MIT credits ([NOTICE](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/NOTICE)) |
| PrismGlass | [woojaeHEO/PrismGlass](https://github.com/woojaeHEO/PrismGlass/tree/266c405d36ef116c2ab4aa86298d71b4ec2b6f32) | `266c405` | Apache-2.0 |
| Vitro | [itshedimisawi/vitro](https://github.com/itshedimisawi/vitro/tree/3384d0432dc2a7fe69804873f607af941c2d1088) | `3384d04` | Apache-2.0 |
| BorderBeam | [ismnoiet/border-beam-android](https://github.com/ismnoiet/border-beam-android/tree/0855cf96819203fa803c4f3b5be1657e8b4620d7) | `0855cf9` | MIT |

## 1. Summary

Ranked by visible gain per unit of effort.

1. **One rim, drawn once, from the true outline.** Widget slabs draw two rims: the shader's light plus a 1 dp gradient stroke whose light runs along the bounds' diagonal, not along the normal (§2.3). The shader also turns its normals on an arc 1.5 times the drawn corner, and puts its colour split only at the corners. Together these explain "stitched corners". Fix: drop the stroke where the program draws an edge, take the light's normal from the drawn radius, and spread any split evenly round the outline. Small.
2. **A thin rim lit with white light in linear space.** Today the rim multiplies the backdrop's own colour by up to `1 + 1.6 × 2.25` in gamma space, over a 1.4 dp e-fold that still shows 5 to 6 dp in (§2.2). iOS, as LiquidGlass measured it, is additive white over about 2 to 6 px and nothing by 16 px. Our `thin` edge experiment already moves this way. Finishing it removes the neon. Small to medium.
3. **Motion from pose, not from speed.** In Slide a pane only translates, so real glass shows no optical change, but our lag spring thickens the leading band by up to 1.8 times and turns on a glint in proportion to speed (§2.4). That is why slow swipes look artificial. Drive optics only from the page's 3D transform (rotation, scale, alpha). Slide then changes nothing, and Carousel, Depth and Stack each get their own physically derived change. The `real` experiment already holds most of the maths. Medium.
4. **A physically based slab profile.** Use the SDF bevel slope, exact Snell deviation, Schlick Fresnel and premultiplied output, with dispersion tied to the deviation rather than to corner position. LiquidGlass's `bevelSlope`/`snellShift` pair is short, Apache-2.0 and self-bounding. Medium.
5. **Keep the pipeline. Do not adopt Mirage for wallpapers.** None of the sources has a cheaper backdrop architecture than our referenced record node plus one quarter-scale blur layer per surface. They share a recording, not a blur. Mirage is in-window AGSL driven by a per-frame Compose loop, which would repaint the home window and re-record every glass surface. The Apache-2.0 GLSL ES 3.00 originals it ports (Paper Design) can run in our SurfaceView scene system as bundled examples instead.

The developer's direction (added 2026-11-22) shapes the rest:
- Physically realistic glass on every glass surface by default.
- The existing Appearance sliders push from that base toward stylised, each mapped to a physical parameter scaled past realism (§4(f)).
- On pong, the only change clearly visible in the `real` experiment was the thinner, sharper edge. So (1) and (2) carry most of the visible gain. The per-transition optics in (3) must be strong enough at their defaults to be seen, with a slider to exaggerate them; otherwise they are cost without a visible return.

§5 lists the workarounds the physical model makes unnecessary, and §6 gives the target architecture and the order in which to delete things.

## 2. What our glass does today

### 2.1 The pipeline

- **Backdrop.** For each live surface, a record node references what the window draws beneath it, plus a margin of one blur radius. A blur node draws that at a quarter scale with `createBlurEffect` and is drawn back at four times (`chrome/LivePicture.java:25-35`, blur effect at `chrome/LivePicture.java:390`). Each surface costs one GPU layer. The budget is at most 8 live surfaces of about 1.1 MiB each, plus one shared pages pool of 6 MiB (`docs/agents/performance.md:45-47`). During a swipe the pages come from a frozen quarter-scale capture (`chrome/LivePicture.java:105-118`).
- **Shader.** One AGSL program, `GlassRefraction.AGSL` (`chrome/GlassRefraction.java:710-1407`). It takes `content` (the frost) and `sharp` (a clearer frame, or `content` itself). Panes run it as a `RenderEffect` (`chrome/LiveGlassEffect.java:49-76`). Widgets and the dock run it as a paint shader over a shared frame (`chrome/SharedFrameDrawable.java:377-470`).
- **Geometry.** `sdRoundRect` is the exact rounded-box SDF (`chrome/GlassRefraction.java:899-902`). The normal comes from a separate `rectNormal`, evaluated on a corner radius that differs from the drawn one (§2.3).
- **Rim stroke.** `GradientRimDrawable` strokes a 1 px-or-more round rect with a `LinearGradient` from the top-left to the bottom-right of the bounds (`chrome/GradientRimDrawable.java:31-64`), white 0.10 to 0.01 over glass (`chrome/GlassSurfaceFactory.java:560-570`).
- **Motion.** A lag spring follows the slab's centre (`chrome/GlassMotionLight.java:9-35`). Tilt is the summed `rotationX/Y` of every ancestor (`terminal/PaneGlassBackdropView.java:908-940`), so Carousel's `rotationY` reaches the shader as `uTilt`.
- **Transitions.** Slide only translates. Fade drifts 0.2 of a width and fades. Depth and Stack scale by 0.75 and 0.92 and fade. Carousel turns ±60° about the shared edge (`launcher/home/HomePageTransition.java:44-51, 95-134`).

### 2.2 The rim diagnosis, checked line by line

| Claim | Verdict | Where |
|---|---|---|
| The band is 20 dp (`bandPx`) | **True.** The default Edge width is 20 dp and `bandPx = edgeWidthDp × density`, never under a pixel. On pong (420 dpi, 2.625) that is about 52 px. | `termux-shared/.../TermuxPreferenceConstants.java:1554`; `chrome/GlassRefraction.java:536-538` |
| The pull goes up to 1.5 times the band | **True as a cap, not as the default.** `strengthPx = min(bend, 1.5 × band)`, and the default bend is 9 dp, so the shipped pull is 9 dp at the rim. The cap only binds for odd stored pairs. | `chrome/GlassRefraction.java:144, 531-533`; `TermuxPreferenceConstants.java:1548` |
| The rim samples the blurred frost (`content`), not the sharp frame | **True by default, false for Clear.** Every tap reads `content`. `sharp` is mixed in only by `clarityK`, which is 0 unless `uClarity` or `uFaceClarity` is above 0, and the stored Clarity defaults to 0. While the slab moves, `uLiveClarity` raises it. | `chrome/GlassRefraction.java:1242-1307`; `TermuxPreferenceConstants.java:1590` |
| A colour gain of `1 + 1.6k` × `uEdgeGain` up to 2.25 raises chroma | **True.** `col.rgb × (1 + kGain·k)` with `kGain = 1.6`, and `k` already carries `uEdgeGain` (1 to 2.25). One gain on all three channels in gamma space widens the gaps between them, so a coloured backdrop goes more saturated. `heldK` keeps it under the alpha but not under neon. The live glint multiplies the lobe by `kKeyPeak = 2.546` on top. | `chrome/GlassRefraction.java:330, 349, 1338-1341, 1364, 1374-1376, 316` |
| Unpremultiplied grey is added | **True, mostly harmless.** `+ kLift·k` (0.07) is added without multiplying by alpha, then clamped to `col.a`. The pressed-key glow and touch scatter (line 1401) are added with no alpha and no clamp, so at anti-aliased pixels they can give rgb above alpha, which is invalid premultiplied output. | `chrome/GlassRefraction.java:1374-1376, 1401`; [RuntimeShader, "AGSL and Premultiplied Alpha"](https://developer.android.com/reference/android/graphics/RuntimeShader) |
| Lighting is in gamma space | **True.** There is no `toLinearSrgb` anywhere in the app. The shader works in the destination's colour space, which is sRGB-encoded on our window. | `grep toLinearSrgb` finds nothing; [RuntimeShader, "AGSL and Color Spaces"](https://developer.android.com/reference/android/graphics/RuntimeShader) |
| RGB fringes at the corners | **True, and worse past Glint.** Up to Edge 40 (Glint) the split is weighted by `cornerWeight = |xy| / (bx·by)`: nothing mid-edge, all of it at a corner. Past 40 a "bold" azure and orange pair goes all round, mixed toward full colour by up to 0.9. | `chrome/GlassRefraction.java:977-979, 1268, 1285-1288, 1390-1399, 145-150` |
| Tilt × 12 widens the band 1.8 times during swipes | **True for turning transitions.** `kTiltLean = 12` makes a whole lean at about 5°, and a live band reaches `1 + kLagBand(0.8) × lean`. Carousel's 60° saturates it. In Slide there is no tilt, but the **lag** lean (`4 × lag / 160 dp`) does the same thing in proportion to speed. | `chrome/GlassRefraction.java:2175, 2013, 2018, 1128, 1183-1197` |
| Two rims per pane | **Partly wrong.** Terminal panes draw one: `PaneGlass.apply` sets `NO_RIM` on the slab, and `PaneRim` skips its stroke when the slab draws an edge. **Built-in widgets draw two:** `BuiltinWidgetView` restores the rim right after `PaneGlass.apply`, and `drawDressing` draws `mRim` with no `drawsEdge()` check. Stacked glass (dock, bars) draws one (`wearsRim = specRim && !programEdge`). | `terminal/PaneGlass.java:160-162`; `terminal/PaneRim.java:407`; `launcher/widget/builtin/BuiltinWidgetView.java:849-850`; `terminal/PaneGlassBackdropView.java:1163-1167`; `chrome/GlassStack.java:339-341` |

What the diagnosis left out:

- **The thick band is mostly the light, not the bend.** The light falls off as `exp(-d / 1.4 dp)`, reaches 10 dp, and is scaled by up to `1.6 × 2.25 ≈ 3.6`. That is still about 20% at 4 dp (10 px on pong) and 5% at 6 dp (`chrome/GlassRefraction.java:1335-1338`). LiquidGlass's measured iOS rim is +33 at 2 px, +13 at 4 px and nothing by 16 px ([how-it-works.md:129-131](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L129-L131)). Ours is about three times wider in pixels **[inference from the two formulas]**.
- **The light is the backdrop's own colour, multiplied.** Over a saturated wallpaper the rim becomes that hue at full chroma, which reads as neon. Glass reflects the light source (white), and what it transmits is not brighter than what is behind it **[inference]**.

### 2.3 Why corners look stitched

1. **The normal turns on the wrong arc.** The light's normal is `rectNormal(p, b, min(cr × 1.5, …))` and the bend's is on `max(cr × 1.5, reach)` (`chrome/GlassRefraction.java:1086, 1129`). The outline turns on `cr`. So the light and the bend start turning before the outline does, and in straight runs next to a corner the light reads as already rounding. The 1.5× arc comes from Kyant's AndroidLiquidGlass, which Playground vendors ([GuideGlassShaders.kt:70-71](https://github.com/mejdi14/Android-AGSL-Shader-Playground/blob/6bed9959cad266600dc569ac9c857574ba225685/shaderRippleEffect/src/main/java/com/example/shaderrippleeffect/internal/GuideGlassShaders.kt#L70-L71)).
2. **The colour split lives only at the corners.** `cornerWeight` is zero along the middle of each edge and one at a corner (`chrome/GlassRefraction.java:977-979`). It is Kyant's `x·y / (hx·hy)` weight ([GuideGlassShaders.kt:105](https://github.com/mejdi14/Android-AGSL-Shader-Playground/blob/6bed9959cad266600dc569ac9c857574ba225685/shaderRippleEffect/src/main/java/com/example/shaderrippleeffect/internal/GuideGlassShaders.kt#L105)). Corners therefore wear a fringe that straights do not.
3. **On widgets, a second rim with a different light model.** The stroke's brightness follows a linear gradient across the bounds' diagonal. The shader's follows `pow(dot(n, key), 2)` round the outline (`chrome/GlassRefraction.java:983-986`). Across a corner arc the shader's light changes by up to a factor of four while the stroke's barely changes, so the two rims disagree exactly at the corners.
4. **A band wider than the corner.** At the default 20 dp band and a widget corner of about 16 to 24 dp, the bend's corner normal arc is set by the band, not by the corner (`chrome/GlassRefraction.java:1128-1129`). The bent field fans diagonally across a wider area at the corners than along the edges **[inference]**. LiquidGlass ties the band to the corner instead: 0.6 of the radius ([how-it-works.md:22-33](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L22-L33)).

### 2.4 Why slow swipes look artificial

- In Slide the slab only translates. A rigid plate moving in its own plane does not change its optics; only the picture behind it moves. Ours does change. The lag spring leans the band and bend toward the motion, mixes in a narrow travelling glint (`glintW = smoothstep(liveK)`), darkens the live edge, raises the live colour split (`kLiveDisp`) and clears the rim toward `sharp` (`chrome/GlassRefraction.java:1179-1205, 1254-1257, 1270-1277, 1357-1361`).
- The amount follows speed, so a slow drag changes the look a little and continuously. The spring then rocks back after release (`chrome/GlassMotionLight.java:9-35`). The class's own note says a press dent read as "a ripple, and glass does not ripple" (`chrome/GlassMotionLight.java:17-20`). The lag is the same deformation, driven by motion instead of a press **[inference]**.
- The experiment on `exp/glass-motion` already does the right thing in part: the `real` mode gives "Slid (Slide, Fade): nothing of its own" (`GlassRealOptics.java:16` on `8bafff2d0`).

## 3. The sources

### 3.1 Android-AGSL-Shader-Playground (mejdi14)

- **What it is.** A demo app of AGSL effects. Its glass is two shaders: a bevel refraction pass and a lighting pass over a recorded layer ([LiquidGlass.kt:82-151](https://github.com/mejdi14/Android-AGSL-Shader-Playground/blob/6bed9959cad266600dc569ac9c857574ba225685/shaderRippleEffect/src/main/java/com/example/shaderrippleeffect/LiquidGlass.kt#L82-L151)).
- **Backdrop.** Records the content into a layer and draws it with a chained colour filter and blur ([LiquidGlass.kt:163-166, 201-221](https://github.com/mejdi14/Android-AGSL-Shader-Playground/blob/6bed9959cad266600dc569ac9c857574ba225685/shaderRippleEffect/src/main/java/com/example/shaderrippleeffect/LiquidGlass.kt#L163-L221)).
- **Rim and refraction.** Exact rounded-box SDF, normal on a 1.5× corner radius. Displacement follows `(1-t)^1.3` multiplied by an artistic `1 + 0.45 sin(13t)` ripple and a tangential swirl, so it is not physical. Dispersion uses three taps ([LiquidGlass.kt:90-118](https://github.com/mejdi14/Android-AGSL-Shader-Playground/blob/6bed9959cad266600dc569ac9c857574ba225685/shaderRippleEffect/src/main/java/com/example/shaderrippleeffect/LiquidGlass.kt#L90-L118)).
- **Lighting.** Gamma space. A rim line weighted `0.3 + 0.7·facing + 0.45·back`, plus a cubic "fresnel" that is just `(1 - d/bevel)^3`. Output stays premultiplied, because `rgb = light ≤ a = light + shade` ([LiquidGlass.kt:127-150](https://github.com/mejdi14/Android-AGSL-Shader-Playground/blob/6bed9959cad266600dc569ac9c857574ba225685/shaderRippleEffect/src/main/java/com/example/shaderrippleeffect/LiquidGlass.kt#L127-L150)).
- **Kyant's reference shaders** are vendored under Apache-2.0: `circleMap` refraction, the 7-tap spectral dispersion weighted at the corners, and a highlight that is `pow(|dot(n, angle)|, falloff)` with no depth term ([GuideGlassShaders.kt:1-178](https://github.com/mejdi14/Android-AGSL-Shader-Playground/blob/6bed9959cad266600dc569ac9c857574ba225685/shaderRippleEffect/src/main/java/com/example/shaderrippleeffect/internal/GuideGlassShaders.kt)).
- **Motion.** A `DeviceTilt` helper reads `TYPE_GAME_ROTATION_VECTOR` ([DeviceTilt.kt:45-47](https://github.com/mejdi14/Android-AGSL-Shader-Playground/blob/6bed9959cad266600dc569ac9c857574ba225685/shaderRippleEffect/src/main/java/com/example/shaderrippleeffect/internal/DeviceTilt.kt#L45-L47)). A sensor loop would be continuous repainting, which AGENTS.md rules out at rest.
- **Take:** nothing new. It confirms where our 1.5× normal and corner-weighted split came from.
- **Leave:** the ripple, the swirl, the sensor tilt.

### 3.2 Mirage (AndroidPoet)

- **What it is.** 29 AGSL shaders for Compose (gradients, noise, image filters). They are ports of Paper Design's GLSL ES 3.00 shaders ([README](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/README.md); [NOTICE](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/NOTICE); [PORTING_GUIDE.md:1-3](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/PORTING_GUIDE.md#L1-L3)).
- **API level.** Library `minSdk` 24 ([Configuration.kt:8](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/buildSrc/src/main/kotlin/io/androidpoet/mirage/Configuration.kt#L8)). Rendering needs API 33 and draws nothing below it ([Program.android.kt:48-51](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/mirage/src/androidMain/kotlin/io/androidpoet/mirage/core/Program.android.kt#L48-L51)).
- **How it renders.** In-window. Procedural shaders are a `ShaderBrush` in `drawWithCache` ([MirageHost.kt:39-64](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/mirage/src/commonMain/kotlin/io/androidpoet/mirage/core/MirageHost.kt#L39-L64)), and image filters a `graphicsLayer` `renderEffect` ([MirageHost.kt:78-110](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/mirage/src/commonMain/kotlin/io/androidpoet/mirage/core/MirageHost.kt#L78-L110)). Time advances in a `withFrameNanos` loop every frame unless `speed = 0` ([ShaderTime.kt:31-47](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/mirage/src/commonMain/kotlin/io/androidpoet/mirage/core/ShaderTime.kt#L31-L47)).
- **Cost.** One full-screen fragment pass per frame, inside the app's window. It is a Compose library (Kotlin Multiplatform), and our launcher is Java Views.
- **Licence.** Apache-2.0. The upstream [paper-design/shaders](https://github.com/paper-design/shaders) is also Apache-2.0 (GitHub licence API: `apache-2.0`). Its originals are GLSL ES 3.00 strings, for example `mesh-gradient.ts` starts `#version 300 es` with `u_time`, `u_colors[10]` and `v_objectUV`.
- **Fit with our scene system.** Our wallpaper scenes are GLSL on a `SurfaceView` behind the window, on their own GL context, so "a scene frame never repaints the window" (`chrome/scene/SceneLayerView.java:1-15`). They run at an ambient 30 Hz by default, and glass follows the scene at half rate (`chrome/scene/SceneFramePolicy.java:29-33`). The prelude takes Shadertoy-style ES 3.00 code (`chrome/scene/ShaderPrelude.java:1-40, 81-103`). Mirage in the window would invalidate the home window every frame. Every live glass surface would then re-record what lies beneath (`chrome/LivePicture.java:25-35`), which defeats the scene layer's whole purpose and AGENTS.md's rule against continuous repainting. **Verdict:** do not replace or supplement the scene system with Mirage. Paper's GLSL originals can be adapted into our ES 3.00 contract (map `u_time` to `iTime`, `u_resolution` to `iResolution`, and compute `v_objectUV` in the fragment shader, since we have no custom vertex stage) and shipped as more examples in `app/src/main/assets/shaders/examples/` (today: aurora, caustics, drift, plasma, starfield). Credit Paper Design in a NOTICE. Mirage's image filters (`fluted-glass`, `water`) evaluate a `u_image` child ([FlutedGlass.kt:33, 87](https://github.com/AndroidPoet/mirage/blob/9da9b00f678d57ce9989838ac513ffa88a11b038/mirage/src/commonMain/kotlin/io/androidpoet/mirage/FlutedGlass.kt#L33-L87)), which maps to our `iChannel0` photo or video input.

### 3.3 LiquidGlass (shayann07)

The deepest source. One AGSL/SkSL shader with two optical models (a "Measured" one fitted to iOS captures, and a "Legacy" one), plus a written method ([how-it-works.md](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md)).

- **Backdrop.** The host records what the app draws beneath once per frame. Each glass draws afterwards in an overlay at its position ([how-it-works.md:10-15](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L10-L15)). The interior blur is deliberately not chained ahead of the shader: "pre-blurring destroys exactly the detail the rim is supposed to bend" ([LiquidGlass.android.kt:190-203](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/androidMain/kotlin/com/wexpa/liquidglass/LiquidGlass.android.kt#L190-L203)). Tone comes from a quarter-scale blurred "wide strip" read with one tap. Detail comes from the sharp recording ([how-it-works.md:65-96](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L65-L96); [GlassShaderSource.kt:336-349](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L336-L349)). The recorded layer must be opaque, and samples are clamped to the recorded region and un-premultiplied ([GlassShaderSource.kt:201-205](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L201-L205)).
- **Geometry.** A rounded-box SDF with a superellipse corner power ([GlassShaderSource.kt:141-147](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L141-L147)). The normal is the field's gradient, or a closed form when the band stops short of the medial axis. Central differences at an epsilon of 0.3 band remove the medial-axis seam ([GlassShaderSource.kt:398-431](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L398-L431)). Long pills blend into Haze's "dome" field for refraction only. Lighting keeps the true distance ([GlassShaderSource.kt:433-462](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L433-L462)). The band defaults to 0.6 of the corner radius ([how-it-works.md:26-29](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L26-L29)).
- **Refraction.** The Legacy model uses a superellipse height profile `h(e) = T(1-(1-e)^p)^(1/p)`. Its slope feeds an exact Snell deviation through the tangent-difference identity, which is self-bounding at `sqrt(n²-1)`, so no clamp is needed ([GlassShaderSource.kt:278-305](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L278-L305)). The Measured model is a lookup "fold" fitted to captures, and at rest it is only "a slight inward offset" ([how-it-works.md:35-57](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L35-L57)).
- **Dispersion.** Measured: red is 0.07 W deeper than blue, only in the mirrored zone ([how-it-works.md:59-63](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L59-L63)).
- **Lighting.** "The bevel is lit, not outlined": additive white, directional, no floor round the shape ([how-it-works.md:127-136](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L127-L136)). A smoothstep bevel band, a key lobe plus a counter-lobe, a 2 px edge line, and Schlick Fresnel from the bevel slope with `f0 = ((n-1)/(n+1))²` ([GlassShaderSource.kt:745-841](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L745-L841)). Highlights are applied as lightness and chroma in Oklab, so a lit rim over colour stays that colour without going milky. The sRGB transfer is approximated as gamma 2 ([GlassOklabSource.kt:3-30](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassOklabSource.kt#L3-L30)).
- **Coverage.** A one-pixel box ramp `clamp(0.5 - d, 0, 1)` ([GlassShaderSource.kt:373-376](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L373-L376)).
- **Motion.** Pose and pull sources for a dragged lens (`GlassPoseDynamics`, `GlassPull`). The fold forms only while a finger drags, "never on a committed animation or on chrome at rest" ([how-it-works.md:50-55](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L50-L55)).
- **Performance.** Their own numbers are desktop JVM. "Android was not timed and no frame-rate claim is made" ([docs/performance.md](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/performance.md)).
- **Take:**
  - `bevelSlope` and `snellShift` (self-bounding refraction).
  - The rule that lighting is additive white and directional with no floor.
  - Schlick Fresnel from the slope.
  - A band tied to the corner radius.
  - Measured rim widths as targets.
  - A sharp source for the rim with frost only for tone.
  - The opaque-recording and bounded-sample contract.
  - Apache-2.0 permits copying with attribution in NOTICE.
- **Leave:** the measured fold maps and capture-fitted constants (iOS tab-bar specific); the Compose host; the distance-field rasteriser for arbitrary shapes (ours are all rounded rects or circles).

### 3.4 PrismGlass (woojaeHEO)

- **Backdrop.** One shared `GraphicsLayer` recorded by a source modifier. Each surface draws it translated by `source.localPositionOf(target)` under its own `RenderEffect` ([PrismGlassBackdrop.kt:53-83, 126-142](https://github.com/woojaeHEO/PrismGlass/blob/266c405d36ef116c2ab4aa86298d71b4ec2b6f32/src/main/kotlin/io/github/woojaeheo/prismglass/PrismGlassBackdrop.kt#L53-L142)).
- **Shader.** Blur first, then lens: `createChainEffect(lens, blur)` ([PrismGlassBackdrop.kt:193-205](https://github.com/woojaeHEO/PrismGlass/blob/266c405d36ef116c2ab4aa86298d71b4ec2b6f32/src/main/kotlin/io/github/woojaeheo/prismglass/PrismGlassBackdrop.kt#L193-L205); chain order per [RenderEffect.createChainEffect](https://developer.android.com/reference/android/graphics/RenderEffect#createChainEffect(android.graphics.RenderEffect,%20android.graphics.RenderEffect))). The lens is a box metric `max(|x|,|y|)`, not an SDF, so corners are square in the field. There is a velocity term that shifts samples sideways, and an additive grey highlight `pow(edge, 7) × 0.08` with alpha untouched ([PrismGlassBackdrop.kt:208-229](https://github.com/woojaeHEO/PrismGlass/blob/266c405d36ef116c2ab4aa86298d71b4ec2b6f32/src/main/kotlin/io/github/woojaeheo/prismglass/PrismGlassBackdrop.kt#L208-L229)).
- **Rim.** A `sweepGradient` border over gradient fills, which is a drawn stroke, not lighting ([PrismGlass.kt:59-97](https://github.com/woojaeHEO/PrismGlass/blob/266c405d36ef116c2ab4aa86298d71b4ec2b6f32/src/main/kotlin/io/github/woojaeheo/prismglass/PrismGlass.kt#L59-L97)).
- **Take:** nothing optical.
- **Leave:** the velocity-driven sample shift. It is the same speed-driven deformation §2.4 argues against.

### 3.5 Vitro (itshedimisawi)

- **Backdrop.** One `GraphicsLayer` recorded from the marked content ([BackdropNode.kt:53-54](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/node/BackdropNode.kt#L53-L54)). Each glass group re-records a view of it sized to the group, then applies `material ∘ blurAcross` as a chain. Blur is separable: the horizontal pass is its own RuntimeShader, and the vertical pass happens inside the material shader while it reads ([ShaderRenderer.kt:136-187](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/render/ShaderRenderer.kt#L136-L187)). Up to 8 shapes are drawn in one pass and joined by a smooth union, so "the whole group draws at the cost of one piece" ([GlassShaders.kt:65-117](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/render/GlassShaders.kt#L65-L117); [README.md:111](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/README.md#L111)). The backdrop's tone is read off the render thread from a miniature, through a private `HardwareRenderer` into an `ImageReader` ([ToneCapture.kt:30-90](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/tone/ToneCapture.kt#L30-L90)).
- **Refraction.** A pull along the SDF normal of `depth × (1 - inset/bevel)³`. The defaults (Regular) are a 12 dp bevel and 26 dp depth, which is strong ([GlassShaders.kt:209-213](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/render/GlassShaders.kt#L209-L213); [Glass.kt:160-175](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/Glass.kt#L160-L175)). Dispersion is three taps along the same pull, all round the outline, not at the corners only ([GlassShaders.kt:216-227](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/render/GlassShaders.kt#L216-L227)).
- **Rim.** `rim = 1 - smoothstep(0.75 dp, 1.25 dp, inset)`, about one dp. Its strength is `RimFloor(0.11) + 0.89·|dot(dir, light)|`: both the lit and the far side, nothing between ([GlassShaders.kt:246-248](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/render/GlassShaders.kt#L246-L248); [Optics.kt:26](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/material/Optics.kt#L26)). The rim pushes the backdrop's own colour more vivid, much as ours does, in gamma space ([GlassShaders.kt:264-274](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/render/GlassShaders.kt#L264-L274)).
- **Output.** Premultiplied (`half4(color × cover, cover)`, [GlassShaders.kt:293](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/render/GlassShaders.kt#L293)).
- **Motion.** A `MotionStretch` spring stretches moving glass with speed ([MotionStretch.kt:16-57](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/node/MotionStretch.kt#L16-L57)). That is Apple's jelly, not rigid glass.
- **Take:**
  - The one-dp rim width and one-pixel coverage ramp.
  - The smooth union of up to N rounded boxes in one pass. It would let the dock's beads, the search capsule and A–Z letters share one field. We do this by nearest-shape selection today (`chrome/GlassRefraction.java:1043-1076`), which creases where two shapes meet.
  - Off-thread tone sampling, if a "scheme from what is behind" ever comes up.
- **Leave:** the in-shader blur taps (we blur at a quarter scale, which is cheaper) and the stretch spring.

### 3.6 BorderBeam (ismnoiet)

- **What it is.** An animated beam that runs round a border, not glass. The rim technique is the useful part ([BeamShader.kt:19-233](https://github.com/ismnoiet/border-beam-android/blob/0855cf96819203fa803c4f3b5be1657e8b4620d7/borderbeam/src/main/java/com/ismnoiet/borderbeam/BeamShader.kt#L19-L233)).
- **SDF and arc length.** `sdRoundedBox` ([L36-39](https://github.com/ismnoiet/border-beam-android/blob/0855cf96819203fa803c4f3b5be1657e8b4620d7/borderbeam/src/main/java/com/ismnoiet/borderbeam/BeamShader.kt#L36-L39)). `perimeterParam` gives the true arc length round straights and quarter arcs, so values along the border are continuous through the corners ([L41-83](https://github.com/ismnoiet/border-beam-android/blob/0855cf96819203fa803c4f3b5be1657e8b4620d7/borderbeam/src/main/java/com/ismnoiet/borderbeam/BeamShader.kt#L41-L83)). Its inverse `boundaryPoint` ([L101-133](https://github.com/ismnoiet/border-beam-android/blob/0855cf96819203fa803c4f3b5be1657e8b4620d7/borderbeam/src/main/java/com/ismnoiet/borderbeam/BeamShader.kt#L101-L133)) lays a soft glow as a chain of Gaussian blobs that "never asks which edge is nearest, so it cannot make seams" ([L135-165](https://github.com/ismnoiet/border-beam-android/blob/0855cf96819203fa803c4f3b5be1657e8b4620d7/borderbeam/src/main/java/com/ismnoiet/borderbeam/BeamShader.kt#L135-L165)).
- **Crisp line.** `1 - smoothstep(0.5, 2.25, dist)` in dp, plus a halo `0.6·exp(-dist / max(glow·0.1, 0.6))`. Output is premultiplied: `half4(rgb·a, a)` ([L184-232](https://github.com/ismnoiet/border-beam-android/blob/0855cf96819203fa803c4f3b5be1657e8b4620d7/borderbeam/src/main/java/com/ismnoiet/borderbeam/BeamShader.kt#L184-L232)).
- **Motion.** A `withFrameNanos` loop drives `phase` ([BorderBeamModifier.kt:94](https://github.com/ismnoiet/border-beam-android/blob/0855cf96819203fa803c4f3b5be1657e8b4620d7/borderbeam/src/main/java/com/ismnoiet/borderbeam/BorderBeamModifier.kt#L94)). That is continuous repainting, so not for glass at rest.
- **Take:** the crisp profile, a 0.5 to 2.25 dp edge with a short exp halo, as the rim's width model. Use arc length wherever a value must vary along the border (a light, a press glow) so corners stay continuous. MIT allows copying with the notice.

### 3.7 Official Android documentation

- **API levels.**
  - `RenderEffect` is API 31, and `createRuntimeShaderEffect` and `RuntimeShader` are API 33 ([RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect); [RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)).
  - `RuntimeColorFilter`, `RuntimeXfermode`, `RuntimeShader.setInputColorFilter` and `setInputXfermode` are API 36. Both new classes take sRGB in and give sRGB out: `vec4 main(half4 in_color)` and `vec4 main(half4 src, half4 dst)` ([RuntimeColorFilter](https://developer.android.com/reference/android/graphics/RuntimeColorFilter); [RuntimeXfermode](https://developer.android.com/reference/android/graphics/RuntimeXfermode)).
  - `RuntimeShader.setWorkingColorSpace` is API 37, so not usable on Android 13 to 16 ([RuntimeShader#setWorkingColorSpace](https://developer.android.com/reference/android/graphics/RuntimeShader#setWorkingColorSpace(android.graphics.ColorSpace))).
- **Colour.** The working colour space is the destination's. `toLinearSrgb` and `fromLinearSrgb` convert to and from `LINEAR_EXTENDED_SRGB`, and "for physically accurate lighting, math should be done in a linear color space". `layout(color)` uniforms are converted to the working space ([AGSL vs GLSL, Color spaces](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl)).
- **Alpha.** "The color returned by the main function is expected to be premultiplied" ([RuntimeShader](https://developer.android.com/reference/android/graphics/RuntimeShader)).
- **Precision.** `half` is `mediump`, with a guaranteed relative precision of 2⁻¹⁰ and range ±2¹⁴. Loops must unroll with constant bounds, and arrays may be indexed only by a constant or loop variable ([AGSL quick reference](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)). Coordinates and SDF maths therefore stay `float`, as ours already do.
- **Built-ins.** `refract(I, N, eta)`, `reflect`, `faceforward`, `normalize`, `smoothstep`, `inversesqrt` and `unpremul()` are all built in ([quick reference, Geometric and Color functions](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference)).
- **Raw buffers.** `setInputBuffer` reads a bitmap with no colour conversion or premultiplication, which suits a stored normal or height field ([RuntimeShader#setInputBuffer](https://developer.android.com/reference/android/graphics/RuntimeShader#setInputBuffer(java.lang.String,%20android.graphics.BitmapShader))).
- **Cost.** A `RenderEffect` with a RuntimeShader on a view "is more expensive than drawing a custom View" ([Using AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl)).

## 4. Proposals

Every proposal keeps the floor at API 33 (`GlassRefraction.available()`, `chrome/GlassRefraction.java:108-111`), with the stored frame as the fallback below it. None adds a frame at rest. Each lists the surfaces it must be checked on.

### (a) One seamless SDF rim, drawn in one place

**What changes.**
1. **Widgets lose their second rim.** `BuiltinWidgetView` stops calling `slab.setRim(...)` when the slab's program draws an edge. Better, `PaneGlassBackdropView.drawDressing` skips `mRim` when `drawsEdge()`, the same rule as `PaneRim` and `GlassStack.wearsRim`.
2. **The light's normal comes from the drawn radius.** It turns exactly where the outline turns (`chrome/GlassRefraction.java:1086`: `cr`, not `cr × 1.5`). The bend may keep a wider arc (a pull mitred at a small corner creases, as the comment at lines 1091-1097 records), but the light must follow the outline.
3. **The rim's profile is BorderBeam's and Vitro's.** A crisp `1 - smoothstep(0.5 dp, ~1.5 dp, inside)` line plus a short exp halo of about 1 to 1.5 dp. It replaces the 1.4 dp e-fold with its 10 dp reach (`chrome/GlassRefraction.java:821-822, 1335-1336`).
4. **No colour split weighted by corner position.** Delete `cornerWeight`. If a split stays, it follows the local deviation (proposal b), which is the same along a straight and round a corner.
5. **One owner.** `GlassRefraction` keeps the rim. `GradientRimDrawable` stays only as the fallback below API 33 and when Fancier Glass is off.

**Files:** `chrome/GlassRefraction.java`, `terminal/PaneGlassBackdropView.java`, `launcher/widget/builtin/BuiltinWidgetView.java`, and possibly `chrome/GlassSurfaceFactory.java`. Tests: the Java mirrors (`gatheredLit`, `cornerWeight` callers) and their tests.

**Visible gain:** corners match straights; the soft neon band becomes a hairline. This answers complaints 1 and 2 directly.

**Risk:**
- No memory and no added frame cost (fewer ops).
- A look change for every surface, so the Appearance reset question in `docs/agents/releasing.md` applies only if stored values change meaning. The Edge slider past Glint loses its bold fringe meaning, which needs a decision.
- Check Docked against Floating dock, keyboard up and down, one pane against many, light and dark wallpapers, and the 420 dpi density against a low-density emulator (the `kMinRimPx`-style floors).

**Effort:** small, about a day with tests. The `thin` experiment (`GlassEdgeModel.java` on `8bafff2d0`: 3 dp bevel, 0.6 dp lines, white specular, no colour gain) is a ready base for the profile.

### (b) A physically based slab

**What changes.** Replace the rim's light and bend inside `GlassRefraction.AGSL` with one model:

1. **Height field.** `h(e)` on the true SDF depth `e = inside / W`, with `W = clamp(0.6 × cornerRadius, 3 dp, 24 dp)` (LiquidGlass's band rule), or a fixed bevel of about 3 to 6 dp for tiny radii. The profile is a superellipse ([GlassShaderSource.kt:278-288](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L278-L288)). The surface normal is `N = normalize(float3(-slope·n2, 1))`, with `n2` the exact SDF gradient on the drawn radius.
2. **Refraction.** `refract(V, N, 1/1.5)` with `V = (0,0,-1)` at rest, or the exact `snellShift` ([GlassShaderSource.kt:290-305](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L290-L305)). The sample offset is the refracted ray's lateral travel over the slab's thickness `T`. It is self-bounding, so `MAX_PULL_SHARE`-style caps go.
3. **Source.** The rim samples `sharp`, the clearer frame, because a bevel is thin glass. The face keeps the frost. We already bind `sharp` where a surface has one (`setClarityInput`, `chrome/GlassRefraction.java:2673`). This makes Clear's behaviour the default for the rim only.
4. **Lighting in linear space.**
   - `lin = toLinearSrgb(unpremul(c).rgb)`.
   - Transmission is `lin × (1 - F)`.
   - The reflection is `F × env`, with `env` a fixed sky tone or a very blurred, mirrored wallpaper tap.
   - The specular is Blinn-Phong from one fixed light in the room (`KEY_LIGHT_*`, `KEY_LIGHT_RISE`, `chrome/GlassRefraction.java:1432-1442`) on the 3D normal, in **white**.
   - Schlick `F = F0 + (1-F0)(1-cosθ)^5` with `F0 = 0.04` (as `GlassRealOptics.F0`, and LiquidGlass at [L778-782](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/GlassShaderSource.kt#L778-L782)).
   - Convert back with `fromLinearSrgb`, multiply by alpha, and clamp under alpha.
5. **No colour gain.** Drop `kGain` and `uEdgeGain`'s multiplication of the backdrop. The tint's transmission is handled by drawing the specular **over** the tint (a separate light pass, or a `RuntimeXfermode` on API 36), not by pre-boosting under it.
6. **Dispersion only if subtle.** A per-channel IOR (e.g. 1.49, 1.50, 1.52), giving three taps only where `|offset| > 0.5 px`. It is proportional to the deviation, so it is equal along straights and corners and falls to nothing on the face.

**Files:** `chrome/GlassRefraction.java` (shader and `Program`), its Java mirrors and tests, `chrome/GlassMotionLight.java` (see c). The `Look` keeps Depth (thickness `T`) and Edge (dispersion strength). Width becomes derived.

**Visible gain:** the edge reads as a polished bevel. It is bright only where it faces the light, keeps the backdrop's hue, and shows a sharp, compressed picture of what is just inside. It no longer glows.

**Risk:**
- Frame cost is about the same: `toLinearSrgb` and `fromLinearSrgb` per rim pixel, one `refract`, and one `pow(., 5)`. Face pixels can skip all of it with the existing `inside < reach` early-out (`chrome/GlassRefraction.java:1335`).
- On a wide-colour window the working space is not sRGB, and the linear conversion handles that correctly; that is the point of the intrinsics.
- `half` precision is fine for colour; keep `float` for geometry.
- Memory: none.
- Seam handling (`rimRect`, `seamReachPx`) must use the new `W` (`chrome/GlassRefraction.java:647-695`).
- Check the page bar's concave lens (it keeps its own path, `chrome/GlassRefraction.java:1106-1112`), the dock's beads and shelf, the Clear face terms, and Material You and scheme tints.

**Effort:** medium, about 3 to 5 days, most of it in tests and the Java mirrors. Code from LiquidGlass (Apache-2.0) may be adapted with a NOTICE entry.

### (c) Per-transition responses from the page's 3D transform

**What changes.**
- **Drop the speed-driven lag** (`GlassMotionLight` lag spring, `uLag`) for surfaces on a page. Drive optics only from the page's pose. A pose already reaches the shader for rotation (`readTilt` sums ancestor rotations, `terminal/PaneGlassBackdropView.java:908-940`). Add the page's **scale** and **alpha**, which `HomePageTransition.Transform` already computes (`launcher/home/HomePageTransition.java:72-87`).
- **Per transition**, with every term zero at rest so the rest look is unchanged:

| Transition | Pose it applies | What real glass does | Shader terms |
|---|---|---|---|
| Slide | translationX only | Nothing changes in the glass; the wallpaper behind is fixed, so the refracted picture slides through the bevel. | None. The existing aim and parallax already move the picture. |
| Fade | small drift, alpha 1 to 0 | Nothing optical. The surface fades as a whole. | None. Make sure the specular is drawn under the page's alpha, never added after it (§2.2, line 1401). |
| Depth (outgoing) | scale 1 to 0.75, alpha to 0 | The slab recedes: further from the viewer, it shows the same wallpaper slightly magnified relative to itself, and its bevel shrinks in pixels. | `GlassRealOptics.magnify` and `lineScale` (on `8bafff2d0`). The bevel width in px scales with the page. |
| Stack (incoming) | scale 0.92 to 1, alpha | Rises toward the viewer. | As Depth, reversed. |
| Carousel | rotationY ±60° about the shared edge | The plate turns. The picture shifts by `T(tan θi − tan θt)`, the far bevel brightens by Fresnel, the face's reflectance rises from 4% toward 9% at 60°, and one glint sweeps across the face as its normal passes the half-vector. | `GlassRealOptics.shiftPx`, `rimFresnel`, `glint` (on `8bafff2d0`). Add a face Fresnel term `F(θ) × env`. |

- **Light and viewer are fixed in the room.** The pose turns the slab's frame (`keyLight`, `chrome/GlassRefraction.java:1435-1497`). Everything is a pure function of the pose, so a page at rest asks for no frames. A transition asks only for the frames the page animation already draws. This meets "no continuously repainting animations" and the "one owner per two-phase number" rule: the transition's `Transform` is the one owner. A pure `GlassPose.of(Transform, slabRect)` with a parity test is the seam.
- **Press tilt** on panes and held borders keeps its current rig. It is a pose too.

**Files:**
- `launcher/home/HomePageTransition.java` (expose the pose).
- A new pure `chrome/GlassPose.java`.
- `terminal/PaneGlassBackdropView.java` and `chrome/SharedFrameDrawable.java` (feed it, instead of reading ancestor rotations).
- `chrome/GlassRefraction.java` (uniforms `uPose`: sine of turn, scale, plus the glint).
- Retire the page path of `chrome/GlassMotionLight.java`.
- Fold `GlassRealOptics` from `exp/glass-motion` in.

**Visible gain:** slow swipes look like moving glass because nothing about the glass changes. Each turning transition then shows a different, physically consistent change: a sweep of light on Carousel, a receding bevel on Depth.

**Risk:**
- Removing the lag removes the "living glass" the developer may like on flings, so offer it as an opt-in at most. Glass may cost battery but never jank.
- Carousel at 60° with a perspective local matrix already exercises `framePx` (`chrome/GlassRefraction.java:916-922`).
- Frame cost: a few uniforms per frame during motion; no memory.
- Check every transition at slow-drag speed on pong's motion log, and keyboard up and down (the dock lift is not a page pose).

**Effort:** medium, about 3 to 4 days. The optics functions exist and are tested on the experiment branch (`GlassRealOpticsTest`, `GlassEdgeModelTest`).

### (d) The glass pipeline: what the sources suggest, and what not to change

**What the sources do.**
- PrismGlass and Vitro record one shared layer of the backdrop and let each glass draw it translated under its own effect ([PrismGlassBackdrop.kt:126-142](https://github.com/woojaeHEO/PrismGlass/blob/266c405d36ef116c2ab4aa86298d71b4ec2b6f32/src/main/kotlin/io/github/woojaeheo/prismglass/PrismGlassBackdrop.kt#L126-L142); [ShaderRenderer.kt:136-186](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/render/ShaderRenderer.kt#L136-L186)).
- LiquidGlass records once per frame and draws all glass in an overlay ([how-it-works.md:10-15](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L10-L15)), and still keeps one wide strip per glass element (`wideLayer.record`, [LiquidGlass.kt:807](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/liquidglass/src/commonMain/kotlin/com/wexpa/liquidglass/LiquidGlass.kt#L807)).
- None of them blurs once for all surfaces. They share the **recording**, as our record nodes already do by reference (`chrome/LivePicture.java:25-35`), and blur per surface, as we do at a quarter scale. Ours is already cheaper per surface than Vitro's full-resolution in-shader taps **[inference from the code paths; not measured]**.

**What is worth building:**
1. **A sharp and a frost source for every live surface.** The rim samples the sharp recording (the record node, unblurred, which already exists), and the face samples the blur node. That is LiquidGlass's "two kernels" split ([how-it-works.md:65-96](https://github.com/shayann07/liquidglass/blob/9effb52bcb9d36e8048666b935f9d4ac46b99b47/docs/how-it-works.md#L65-L96)). Today the live route binds no clearer picture under an effect: `setClarityInput(null, …)` in `chrome/LiveGlassEffect.java:67`. Under a `RenderEffect` only one child (the node's own content) is available. So the sharp source means drawing the record node and the blur node into the surface as two layers, and running the program as a paint shader over a `BitmapShader`-free path is not possible for a RenderNode. The practical route is a `RenderEffect` chain `createRuntimeShaderEffect(program, "content")` over a `createBlendModeEffect`, or two programs: rim from sharp, face from frost, composited by the rim mask. **[inference: needs a spike]** Cost: no new pixels (the record node holds none of its own); a second pass over the rim's bounds only.
2. **One field for groups.** Dock beads, the search capsule and A–Z letters move from nearest-shape selection (`chrome/GlassRefraction.java:1043-1076`) to a smooth union of up to N rounded boxes, as Vitro does ([GlassShaders.kt:105-117, 186-203](https://github.com/itshedimisawi/vitro/blob/3384d0432dc2a7fe69804873f607af941c2d1088/vitro/src/main/kotlin/io/github/itshedimisawi/vitro/internal/render/GlassShaders.kt#L105-L203)). It removes creases where shapes meet and gives a true normal through the join.
3. **Not worth building:** a single full-screen shared blur for every surface. Surfaces sit in different window layers (`WindowLayer`: ground, pages, chrome, sheets) and see different things beneath them. Pages also move under chrome during a swipe, which is why the frozen page layer exists (`chrome/LivePicture.java:105-118`). One shared blur would have to be per layer and re-blurred whenever any page moves, which is the per-frame capture `docs/agents/performance.md:41-44` forbids.

**Risk:** proposal 1 adds a pass over rim pixels during motion. Memory is unchanged. It needs a perfetto check against the 30 to 77 ms pre-roll frames already on record (`docs/agents/performance.md:73`).

**Effort:** 1 is medium with a spike first (about 2 days to prove). 2 is small to medium.

### (e) Mirage for wallpapers

**No to replacing or supplementing the scene system with Mirage** (§3.2). It renders in the window with a per-frame `withFrameNanos` loop, which repaints the home screen and every glass surface behind it. It is a Compose library in a Views app, and it needs API 33 where our GL scenes do not.

**Yes to borrowing the shaders.** Adapt Paper Design's Apache-2.0 GLSL ES 3.00 originals (mesh gradient, grain gradient, warp, swirl, god rays, voronoi, waves, and others) into `app/src/main/assets/shaders/examples/` under our ES 3.00 contract. The scene layer runs them at its ambient rate on its own surface, and glass follows at half rate (`chrome/scene/SceneFramePolicy.java:29-33`). Mind the per-example quality and rate knobs (`chrome/scene/ShaderExamples.java:30-51`) and `ShaderSafety`'s guard.

**Effort:** small per shader (uniform renames and an inline `v_objectUV`). Credit both Paper Design and Apache-2.0 in a NOTICE.

### (f) The sliders as physical parameters

Today's glass controls are the `LookGrid` properties (`termux-shared/.../LookGrid.java:60-89`), shown as rows in `surfaces/GlassRows.java:41-80`. The Depth slider writes two stored keys (`surfaces/GlassEdgeControls.java:43-55`; `surfaces/SurfaceEditorController.java:4420-4431`). Seven more stored keys have no slider and are written only by presets and snapshots (`surfaces/SurfacePresets.java:1361-1382`; `surfaces/AppearanceSnapshot.java:284-291`).

The rule for the new model: each slider's shipped value is the physical value, and its range runs past realism in one direction or both. No slider multiplies a gain.

| Slider today (stored unit) | What it does today | Physical parameter | Default, then the stylised end | Keep? |
|---|---|---|---|---|
| **Frost** (stop 0-7: blur `4 + 32t^1.5` dp, grain `2 + 6t` %) | Blur radius of the shared frame and the grain | Surface roughness of the face: the scatter radius of the transmitted image | Clear's stop (7 dp) is a lightly frosted pane; the top stop is ground glass | Keep. Roughness applies to the face only; the bevel stays polished and samples the sharp source (§4(b) step 3) |
| **Grain** (%) | A tiled grain layer | The texture of that roughness | Follows Frost by default (`LookGrid.java:63-64`) | Keep as stylistic |
| **Opacity** (%) | The tint layer's alpha, and `uEdgeGain` through `withCover` (`chrome/GlassStack.java:300-303`) | Optical density of a tinted body: transmission `exp(-α·T)` | The shipped value; up to opaque | Keep. It stops feeding a light gain (see §5 row 2) |
| **Tint** (amount) and **Tint hue** | The tint colour, plus a hue shift baked into the shared frame (`chrome/GlassFrameTint.java:3-20`) | Per-channel absorption, a coloured glass body | The shipped amount; up to deep colour | Keep. It moves from a bake into the frame to an absorption uniform in the shader |
| **Depth** (bend dp, 0-30; writes width = ⅔ bend) | The pull at the rim and the band's width | Slab thickness `T` (with IOR fixed at 1.5) | Thin pane; at the top, a thick lens with IOR up to about 1.9 | Keep. It drives `T` and the IOR past glass. The band width becomes derived (`W = 0.6 × corner`, clamped) |
| **Edge** (0-100: corner split up to 40 "Glint", bold fringe past it) | The corner colour split and the bold azure and orange fringe | Dispersion: Δn between red and blue (Abbe number) | Crown glass (Abbe about 60, a hair of colour only where the bend is steep); up to about 10× that | Keep, renamed in meaning. The bold fringe mode goes |
| **Rim** (Off, Hairline, Gradient) | Picks the 1 dp `GradientRimDrawable` stroke's style (`chrome/GlassLook.java:127-131, 169`) | Specular light intensity (the key light's power and lobe width) | Hairline is the physical light; Gradient is a brighter, broader light; Off is refraction only | Keep three stops, remapped. The stroke drawable goes |
| **Vibrancy** (70-150 %) | Saturation baked into the shared frame (`chrome/GlassVibrancy.java:3-14`) | None. Glass does not saturate what is behind it | 100 (no change) | Keep as a plain stylistic colour knob, applied in the shader, not baked |
| **Corners** (dp or auto) | Outline radius | Geometry, and the bevel width through `W` | — | Keep |
| **Margin** | Layout | — | — | Keep (not glass) |
| *Edge width* (stored, no slider; written by Depth; the shipped 20 dp is off Depth's own ⅔ curve) | The band | Derived from Corners and Depth | — | **Remove** the key |
| *Edge light*, *Specular* (stored, ignored by the program, `chrome/GlassRefraction.java:380-389`) | Nothing | The Rim stop covers them | — | **Remove** |
| *Clarity*, *Face clarity* (preset-only) | Mix of the sharp frame into the rim and the face | Rim: always sharp. Face: Frost | — | **Remove** both; Clear's look comes from a low Frost stop |
| *Dim* (preset-only) | Clear's face tone over bright backdrops (`chrome/GlassRefraction.java:180-188, 1308-1320`) | Not optics: a legibility layer (Apple's dimming) | — | Keep as the legibility rule; consider folding it into Opacity |
| *Size lens* (preset-only) | Band and bend scale with the surface's size (`chrome/GlassRefraction.java:1113-1127`) | Thickness proportional to size | — | Keep only as the default relation between `T` and the short side; no key |

Which sliders no longer make sense: none of the visible ones need to go. Edge past 40 changes meaning (no bold fringe). Rim changes meaning (light, not stroke). Vibrancy stays but is admitted as stylistic. The seven hidden keys become derived or go. Changing what stored values mean is an appearance change, so this lands with the next Appearance reset only on the developer's say (AGENTS.md, "Things already decided"; `docs/agents/releasing.md`).

## 5. What to remove

Each row says what the piece compensates for, whether the physical model of §4(a)-(c) makes it unnecessary, and the risk of removing it. "Added in" is the commit that introduced it, from `git log -S` or `--diff-filter=A` on the file.

### 5.1 Inside the shader (`chrome/GlassRefraction.java`)

| Piece | Where | Compensates for | Unnecessary after | Risk of removing | Added in |
|---|---|---|---|---|---|
| Colour gain `1 + kGain·k` on the backdrop | 330, 826, 1374-1376 | An edge light made by brightening the **blurred** frost, which has no detail to show, so the edge was made by colour | (a), (b): white specular over a sharp bevel | Low; the main look change | `a5556188a` 2026-10-16 "Adaptive glass, an edge of gathered light" |
| `uEdgeGain` / `REFERENCE_COVER` / `MAX_EDGE_GAIN` / `withCover` | 331-349, 461-505, 733; `chrome/GlassStack.java:300-303` | The light drawn **under** the tint being dimmed by it, so it was pre-boosted by `1/(1-cover)` | (b) step 5: light drawn over the tint | Low. The specular must then be composited after the tint layer, which is a pipeline change for stacked glass | `b7933f7df` 2026-10-17 |
| `heldK` headroom hold | 365-371, 1006-1013, 1362-1372 | The gain clipping channels and shifting hue | (b): additive white in linear space under alpha does not shift hue | None once the gain is gone | `216f49b17` 2026-10-16 |
| `kPaleCut` | 327-328, 1337-1338 | The multiplied light whitening pale backdrops | (b): Fresnel and specular are physical over any backdrop | Low | `a5556188a` |
| `kLift` grey | 350-351, 1374-1376 | Multiplied light being zero over black | (b): white specular shows over black | Low | `a5556188a` |
| `kEdgeShade` / `kLiveEdgeShade` dark edge | 352-363, 1357-1361 | No thickness cue, so a dark line was drawn | (b): Fresnel darkening of transmission at the steep bevel gives the cue | Low; LiquidGlass also keeps a weak inner line, so a physical one may be wanted | `22d926282` 2026-10-20 |
| Bold fringe (`kBold*`, Edge > 40) | 213-268, 853-865, 1379-1399 | The corner-only split being invisible on frosted rims; mixing toward a full colour so the tint does not recolour it | (b) step 6: per-channel IOR dispersion along the whole rim | Changes what Edge > 40 looks like | `b732f8da4` 2026-10-17 |
| Split on the gathered light (`rimSplit`, `kRimSplitDp`, `kSharpClarity`) | 204-211, 270-276, 1278-1288, 1365-1373 | A frosted rim blurring the sampled split away | (b) step 3: the rim samples the sharp source | None | `b2f1e191b` 2026-10-17 |
| `cornerWeight` | 974-979, 1268, 1285 | Kyant's copied weight to keep straights clean | (a) step 4 | Stitched corners go away | `ce6a35ccd` 2026-10-15 |
| Light normal on `1.5 × cr` | 1084-1086 | The light snapping where two edges meet, from a mitred normal | (a) step 2: exact SDF gradient on the drawn radius is already smooth | Must keep the bend's wider arc for small panes (1091-1097), or the chevron crease returns | `e0dd38903` 2026-09-28 |
| Clarity, face clarity, `uLiveClarity`, `kLiveReach` | 391-393, 406-408, 1240-1262, 1249-1257 | The rim and face sampling frost, so detail was mixed in by amount, and raised during motion "so the bend has detail to move" | (b) step 3 plus Frost on the face | Clear's look must be rebuilt from Frost | `22d926282` 2026-10-20 (live) |
| Lag lean (`uLag`, `kLag*`), `liveK`, `glintW`, live lobe, counter-lobe | 739-741, 866-874, 1165-1205, 990-1005, 1339-1341 | A slab with no 3D pose, so motion was faked from speed | (c) | Removes "living glass" on flings; offer it as an opt-in if wanted | `3d618532e` 2026-10-19 |
| Tilt lean on the band (`kTiltLean` × `kLagBand`) and `kTiltLight` | 1170-1197, 1343-1348, 2135-2190 | A 2D rim standing in for a tipped bevel | (c) with the 3D normal of (b) | Low | `0b3685f27` 2026-10-20 |
| `tiltOffset` (soft face shift, quadratic magnification) | 1014-1035, 1207-1213 | Thick-plate displacement without Snell | (c): `shiftPx` uses Snell (`GlassRealOptics.java` on `8bafff2d0`) | Low | `0b3685f27` |
| Seam push-out (`SEAM_*`, `rimRect`, `seamReachPx`) | 630-695 | Two glass surfaces sharing an edge each drawing a rim there | Partly: still needed while the keyboard host and the strip are separate surfaces. A group field (§4(d) item 2) removes it where the two can share a program | Medium; a missed seam shows a bright line through one sheet | `e0dd38903` 2026-09-28 |
| Unclamped additive glows (`lensGlow`, touch scatter) | 1400-1401 | — | Fix rather than remove: multiply by alpha | Invalid premultiplied output at the edge pixels | — |

### 5.2 Around the shader

| Piece | Where | Compensates for | Unnecessary after | Risk of removing | Added in |
|---|---|---|---|---|---|
| Second rim on widget slabs | `launcher/widget/builtin/BuiltinWidgetView.java:850`; `terminal/PaneGlassBackdropView.java:1163-1167` | Widgets had a rim before the program drew an edge | (a) | None where the program draws an edge; keep it for the no-program fallback | `16a37744f` 2026-10-19 |
| `GradientRimDrawable` and `OpenEdgeDrawable` as the rim over glass | `chrome/GradientRimDrawable.java`; `chrome/GlassSurfaceFactory.java:560-606` | A drawn stroke as the edge | (a): the shader is the one rim. Keep only below API 33 or with Fancier Glass off | Low; the Rim row is remapped (§4(f)) | `f1213d95d` 2026-09-29, `d18d6b3f5` 2026-09-30 |
| `LiveStoredParity` (take the wall's dim out of the live recording, then re-bake Vibrancy and the frame tint) | `chrome/LiveStoredParity.java:7-60`; `chrome/LivePicture.java:60-66, 262-290`; `chrome/LiveBackdrop.java:352, 363-378` | Two routes (stored frame and live picture) carrying different colour bakes, so the hand-off would change colour | Yes, once Vibrancy and the Tint hue shift are shader uniforms (§4(f)) and not baked into the stored frames: both routes then hand the shader the same raw blur. The dim removal stays if the live recording keeps drawing the dimmed wallpaper | Medium: a colour jump at the stored-to-live hand-off if any bake remains. A parity test should guard it | `b65d36779` 2026-11-04; `803804621` 2026-11-22 |
| Vibrancy and frame tint baked into the shared frames | `chrome/GlassVibrancy.java:3-14`; `chrome/GlassFrameTint.java:3-20` | Doing colour work once per bake, not per frame | Yes, as above. Costs a 4x5 matrix per pixel in the shader instead, which is cheap | Low; the ink check must then sample the shader's result, or compute the same matrix | `b7933f7df`, `4bc1cecdf` 2026-10-17 |
| Stored frame (shared pre-blurred wallpaper per radius) as a separate route from the live picture | `chrome/WallpaperBlurCache.java` (header); `chrome/SharedFrameDrawable.java:377-440`; `terminal/PaneGlassBackdropView.java:208-300` | Live blur being too expensive everywhere, all the time | No. It is already the "one shared backdrop texture plus per-surface parameters" design, and it is the cheap route at rest. Keep both, with **one** program and **one** colour path | — | `e0dd38903` 2026-09-28 |
| Still frame against live scene frame forks | `chrome/SharedFrameDrawable.java:382-392, 446-470`; `terminal/PaneGlassBackdropView.java:67-70, 330`; `chrome/LiveGround.java`; `chrome/scene/LiveSceneFrame.java` | A GL scene on a SurfaceView that no RenderNode recording can see (`chrome/BackdropRecording.java:3-10`) | No. The scene frame is a third source of the same kind. It can share the shader's input slot: today it is a separate draw path per surface | Low if unified behind one "source" interface; high if removed | `d3f0c9b63` 2026-10-23; `36e687ee1` 2026-11-05 |
| Quarter-scale blur layer per live surface, page-layer captures, `GlassPagePool`, `BackdropLayers` LIVE and FROZEN | `chrome/LivePicture.java:42-43, 105-140`; `chrome/BackdropLayers.java:91`; `chrome/PageLayerCapture.java`; `chrome/GlassPagePool.java` | Re-blurring moving pages per frame during a swipe | No. These are the measured fixes for swipe cost (`docs/agents/performance.md:45-47, 71`). The physical model adds no need for them, and none of the sources offers a cheaper replacement (§4(d)) | High | `0f5a67566` 2026-11-03; `c9bfaa288`, `88a93d23b` 2026-11-07; `ca9e1e596` 2026-11-18 |
| Software-canvas skips | `chrome/SharedFrameDrawable.java:394-401, 453-454`; `terminal/PaneGlassBackdropView.java:1130-1136, 321` | A `RuntimeShader` and a `HARDWARE` bitmap refused by a software canvas (the departing card's, a `RealtimeBlurView`) | No: needed while any software canvas draws glass | High: a crash on the home screen | `29ddea0e9` 2026-09-29 |
| `HardwareFrame` (GPU-resident frames with a small software twin for pixel reads) | `chrome/HardwareFrame.java:16-31` | Uploading frames on the RenderThread mid-swipe | No | High (jank) | `03d1b1ea5` 2026-11-20 |
| Android version branches | `chrome/GlassRefraction.java:108-111` (33); `terminal/PaneGlassBackdropView.java:269-271` (33), `291` (29); `chrome/LiveBackdrop.java:509` (31), `997` (29); `chrome/GlassMotionPlayer.java:73, 91` (31); `chrome/DisplayKeyboardBlur.java:144-178, 318` | `RenderEffect` from 31, `RuntimeShader` from 33, `enableZ` from 29 | Only if `minSdk` moves to 33. It is held at 28 on every edition for other reasons (AGENTS.md, `targetSdkVersion`) and the min is separate, so these stay | — | — |
| Corner mask (paint only the four arcs with what is behind the page) | `terminal/PaneGlassBackdropView.java:666-690, 805, 1141-1147, 1183-1186` | The X display is a `SurfaceView` that no parent clip can round | No: the display page still needs rounded corners | High (square display corners) | `10552ba8d` 2026-09-04 |
| X11 stand-ins: `DisplayKeyboardBlur` (a compositor blur layer under the keyboard bed) and `KeyboardFrost` | `chrome/DisplayKeyboardBlur.java:1-30`; `chrome/KeyboardFrost.java:41` | No RenderNode recording can see the X display's surface | No. A physical bevel over it is not possible without the pixels, so these surfaces keep frost with a physical-looking specular rim only | — | `f84285fd7` 2026-11-04 |
| `BackdropRecording` SurfaceView rule | `chrome/BackdropRecording.java:3-10` | A recorded SurfaceView moving the real surface | No | High | `e20ae88b0` 2026-11-22 |
| `FrameCrossfade` (220 ms between two stored frames) | `chrome/FrameCrossfade.java` | A wallpaper or radius change snapping | No; it is a transition, not optics | — | `504b32511` 2026-09-26 |
| `GlassMotionLight` lag spring, its gate and the tilt still-draw counter | `chrome/GlassMotionLight.java`; `chrome/GlassMotionGate.java`; `terminal/PaneGlassBackdropView.java:897-940, 1246-1265` | No pose model (see §5.1) | (c): the pose comes from the page `Transform`, which draws only while the page moves | Low | `d40a84a93` 2026-10-19 |
| `GlassTouchLight` light and lens | `chrome/GlassTouchLight.java`; uniforms `chrome/GlassRefraction.java:734-738, 1214-1222` | Press feedback | Keep as a feature, but draw its light as a specular point light in (b)'s model (white, linear), not an added grey | Low | `067e74d2d` 2026-10-14 |
| Experiment switches `debug.tl.glass_edge`, `debug.tl.glass_motion` | `GlassEdgeModel.java`, `GlassRealOptics.java` on `exp/glass-motion` (`8bafff2d0`) | Comparing models on the phone | Yes, once one model ships: fold the code in, delete the switches | None | `86d023b6d`, `8bafff2d0` |
| Diagnostic hooks: `FrameProbe` sources, `Trace` sections, motion-log ticks | `terminal/PaneGlassBackdropView.java:74-77, 343-347, 621`; `chrome/GlassTouchLight.java:276-285`; `chrome/GlassMotionPlayer.java:105, 130`; `chrome/LiveBackdrop.java:626-627, 759-768` | Measuring motion and capture cost | Not yet: the pong rounds rule keeps the motion log and diagnostic lines in every build until the next release (AGENTS.md, "Pong rounds"). The probes for removed pieces (`paneGlass.motion`, `paneGlass.tilt`) go with them. `Glass.layers.degenerate` is an empty trace section | Low | — |
| `GroundUnderGlass` / `GlassGround` prototype | `chrome/GroundUnderGlass.java`, `chrome/GlassGround.java` (both "proto", #283) | The bars' glass not seeing the page's ground | Unrelated to optics; decide with #283 | — | `9036574b1` 2026-11-19 |

### 5.3 Not workarounds: features to carry over

The concave page-bar lens (`chrome/GlassRefraction.java:1099-1112`), the dock's soft rim and shelf, lens beads, A–Z letter beads and the key lens (`chrome/GlassRefraction.java:755-793, 1040-1076, 1143-1164, 1223-1239`) are features. Under (b) each becomes a different height field over the same lighting. The beads move to a smooth union (§4(d) item 2). `EvenOut`, `LowOpacityGlass`, `GlassInk` and the face dim are legibility, not optics, and stay.

## 6. Target architecture and deletion order

**Target.**

```
sources (unchanged in kind)                     one program, physical at defaults
---------------------------                     ---------------------------------
stored frame  (shared, per radius, raw blur) --\
live picture  (record node + 1/4 blur layer) ---+--> GlassMaterial(source.frost, source.sharp,
scene frame   (GL scene's published frame)   --/        shape SDF or union, pose,
                                                         params: T, IOR, Δn, roughness,
                                                         absorption, light, vibrancy)
                                                       -> frost face (transmission x absorption)
                                                       -> polished bevel: Snell from the sharp
                                                          source, Fresnel, white specular, all
                                                          in linear light, premultiplied out
fallback (< API 33, software canvas): stored frame + tint + hairline stroke, as today
```

- **One material, one rim, one colour path.** Every glass surface (panes, widgets, dock, keyboard, sheets, bars) hands the same program a source, a shape and a pose. The program owns the rim. No surface draws its own stroke. Colour knobs (Vibrancy, the Tint hue) are uniforms, not bakes, so stored, live and scene sources look identical by construction, and parity matrices go.
- **Pose from the page.** A pure `GlassPose.of(HomePageTransition.Transform, rect)` (plus the press rig) is the only motion input. Slide and Fade draw no optical change. Depth, Stack and Carousel change thickness projection, Fresnel and the glint. Nothing runs at rest.
- **Sliders are physical parameters** (§4(f)), with defaults at realism.
- **Kept as they are:** the stored-frame cache, the live picture's quarter-scale layer, page captures and pool, `HardwareFrame`, the software-canvas and SDK guards, the corner mask, the X11 frost and `BackdropRecording`. They solve cost and platform limits that the optical model does not touch.

**Order of deletion.** Each step ships alone, keeps a parity or snapshot test, and is checked on pong before the next.

1. **Second rim on widgets** (§5.2 row 1). One line plus a test that a slab whose program draws an edge has no `mRim`. No look change elsewhere.
2. **Light normal on the drawn radius, and `cornerWeight` out** (§5.1). The stitched-corner fix; keep the bend's wide arc.
3. **Thin rim** (fold `GlassEdgeModel`'s thin edge in as the default): delete the colour gain, `uEdgeGain`/`withCover`, `heldK`, `kPaleCut`, `kLift`, the edge shades, the bold fringe and the gathered-light split together, because each exists only for the multiplied light. Remove the `debug.tl.glass_edge` switch.
4. **Sharp rim source**: delete clarity, face clarity and live clarity. Clear is rebuilt on a low Frost stop. This needs the §4(d) item 1 spike for the live route first.
5. **Physical slab (§4(b))**: linear light, Snell, Fresnel, per-channel IOR. Remap the Edge and Rim rows. Remove the hidden keys (edge width, edge light, specular) at the Appearance reset the developer chooses.
6. **Pose-driven motion (§4(c))**: delete `GlassMotionLight`'s page path, `uLag` and the live lobes, the tilt lean, `tiltOffset`, and their `FrameProbe` sources. Fold `GlassRealOptics` in and remove `debug.tl.glass_motion`.
7. **Colour bakes to uniforms**: move Vibrancy and the frame tint into the shader, then delete `LiveStoredParity`'s bake half (keep the dim removal while the live recording draws the dimmed wallpaper), and re-point the ink check.
8. **Unify the three sources behind one input interface** in `SharedFrameDrawable` and `PaneGlassBackdropView`, so still, live and scene frames stop being separate draw paths. Last, because it touches the most code and gains no pixels.

## 7. Open questions to check on pong

1. **Widget corners after (a) steps 1 and 2 alone.** With the stroke removed and the light normal on the drawn radius, do widget corners still read as stitched? Use the Edge slider at 0 and at 40 to tell the corner-only split apart from the normal arc.
2. **Rim width in pixels.** Take a screenshot of a widget edge over a dark and a light wallpaper and read the light's profile in px. Is it near the 10 to 15 px estimated in §2.2 (against iOS's 2 to 6)?
3. **Slow swipe in Slide with the lag off.** With the lag spring disabled (a debug switch), does slow-swipe glass still look artificial? If it does, the cause is elsewhere: the frozen page capture's quarter scale, or the page and wallpaper parallax under the frost.
4. **Carousel with the `real` edge.** On `debug.tl.glass_edge=real`, does the glint's travel and the far-bevel Fresnel read as glass at 60°, or does it need the face Fresnel term?
5. **Linear-light cost.** Frame time of the rim with `toLinearSrgb`/`fromLinearSrgb`, measured with perfetto against today on the drawer and palette scenarios (`scripts/perf-scenarios/gfxbench.sh`).
6. **Window colour mode.** Is the launcher window ever in a wide-gamut mode? If so, the current gamma-space gain behaves differently there, and the linear path is the fix.
7. **Sharp rim through the live route.** Can the live route bind the unblurred record node as `sharp` with no extra layer (the §4(d) spike)? Measure layer count and memory with `dumpsys gfxinfo` before and after.
8. **Lag as an option.** Does the developer want the speed lag kept as an opt-in for flings once Slide is optically still?
9. **The second rim on other surfaces.** Does any other surface besides built-in widgets restore a rim after `PaneGlass.apply`? Grep found the page bar sets none (`wall/pageswitch/PageBarGlass.java:158`). Third-party widget frames were not checked.

## Not read

- Haze's source (chrisbanes/haze) was not read. It appears here only as LiquidGlass's credit for the dome field.
- LiquidGlass's measured-model research files under `docs/research/`, and its Compose host beyond the lines cited.
- Mirage's 29 shader bodies, beyond MeshGradient, Water and FlutedGlass headers.
- Nothing was measured on a device. Every frame-cost statement above is an inference from the code.
