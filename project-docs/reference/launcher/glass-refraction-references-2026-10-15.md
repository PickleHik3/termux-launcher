# Glass refraction: what the references do (2026-10-15)

Question: are there better tuning knobs and references for the AGSL glass in
`app/src/main/java/com/termux/app/chrome/GlassRefraction.java` (six knobs: bend dp, edge width dp,
edge light %, specular %, dispersion %, clarity %)? The developer finds the result either too
subtle or a neon outline. Read-only research, 2026-10-15; no code proposed.

Pinned sources (read in full, not summarised by a third party):

- **Haze@c2bdd15** = github.com/chrisbanes/haze at commit `c2bdd15a32a6b182d16a351e0d3da4276be366fc`
  (2026-10-14), module `haze-glass`. Paths below are relative to
  `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/`. Apache-2.0.
- **Kyant@65ab177** = github.com/Kyant0/AndroidLiquidGlass, branch `kmp`, commit
  `65ab177e90e5c1d8c62e70cf7755841982da65f6`, module `backdrop`. Apache-2.0.
- Apple pages and WWDC transcripts as fetched on 2026-10-15; Android pages as fetched on the same day.

## Summary

1. **Make the bend much bigger than the band allows today, and let it fold.** Both open-source
   references displace by 1.5-2 times the band width (Haze Clear: 56 dp x 0.85 strength over a 28 dp
   edge, `GlassStyle.kt:78-82`; Kyant: `lens(16dp, 32dp)` and `lens(24dp, 48dp)`,
   `GlassPlaygroundContent.kt:133`, `DialogContent.kt:67`). Ours is held to 0.45 of the band
   (`MAX_PULL_SHARE`, `GlassRefraction.java:136`), and Clear ships at bend 11 / edge 28 (0.39).
   The refs accept compression at the rim, and Haze has a separate fold knob
   (`GlassOptics.refractionFoldStrength`, `GlassOptics.kt`). That is the likely cause of "too subtle".
2. **Replace the omnidirectional additive hairline with a directional lobe plus a dark edge.** Haze
   lights the rim with `pow(dot(normal, lightDir), 16..24) * 0.38..0.55` composited as alpha, under a
   black edge shadow of 10-32% (`GlassShaders.kt:575-585`, `GlassStyle.kt:123-143`). Apple says the
   2027 renderer "introduced a darkened edge along with brighter specular highlights"
   (developer.apple.com/videos/play/wwdc2026/112/). Ours adds up to +0.8 white everywhere, never below
   0.4 of it (`rimK`, `GlassRefraction.java` AGSL), which is the neon read.
3. **Make dispersion scale with the displacement and live at the corners.** Haze:
   `chromaOffset = displacement * strength * 0.5 * cornerWeight` with Clear strength 0.04
   (`GlassShaders.kt:120-122`, `GlassStyle.kt:129`); Kyant:
   `intensity = chromaticAberration * (x*y)/(halfW*halfH)` (`internal/Shaders.kt:120-121`). Both vanish
   at the middle of a straight edge. Ours is a fixed dp split (up to 16 dp) across the whole band on
   every edge.
4. Also worth a knob each: face saturation (Kyant `saturation = 1.5`, Haze `chromaMultiplier` 1.5/1.1)
   and an adaptive white point (Haze 0.55 light / -0.22 dark). Both tune legibility of the face, not
   the edge.

## Reference implementations

### Apple Liquid Glass (design intent only; no shader is published)

| Claim | Source |
|---|---|
| Two variants: regular "blurs and adjusts the luminosity of background content to maintain legibility"; clear is "highly translucent". | HIG Materials, `developer.apple.com/design/human-interface-guidelines/materials` (JSON: `.../tutorials/data/design/human-interface-guidelines/materials.json`) |
| Clear "only for components that appear over visually rich backgrounds"; over bright content add a dark dimming layer of 35% opacity. | same page |
| Regular is "versatile and adaptive", clear "permanently transparent and needs a dimming layer"; never mix the two. | WWDC25 session 219, `developer.apple.com/videos/play/wwdc2025/219/` (Principles chapter) |
| "The primary way Liquid Glass visually defines itself is through something called Lensing"; "dynamically bends, shapes, and concentrates light". | WWDC25 219 (Dynamics chapter) |
| Larger glass has "more pronounced lensing and refraction effects" and deeper shadows: the effect scales with size. | WWDC25 219 (Adaptivity chapter) |
| Highlights are lights "inside of this environment" that "respond to geometry" and move with interaction. | WWDC25 219 (Principles chapter) |
| Each layer "continuously adapts based on what's behind it"; "the amount of tint and the dynamic range shift" for legibility. | WWDC25 219 (Adaptivity chapter) |
| 2027 releases: "more effectively diffuses complex content", "a darkened edge along with brighter specular highlights", and a user slider from ultra clear to fully tinted. | WWDC26 session 112, `developer.apple.com/videos/play/wwdc2026/112/` |
| By default glass has "no inherent color"; apply colour "sparingly", to a primary action's background. | HIG Color, "Liquid Glass color", `.../tutorials/data/design/human-interface-guidelines/color.json` |
| Public API is only `regular`, `clear`, `identity`, `tint(_:)`, `interactive(_:)`: no numeric optics. | `developer.apple.com/documentation/swiftui/glass` (JSON endpoint) |

Not found in Apple's pages: any statement of a refraction profile, IOR, or highlight formula.

### Haze glass module (Chris Banes, first-party source)

Haze 2.x ships a `haze-glass` module (`README.md` of the repo calls it a "refraction-driven Glass
effect"). It is the closest first-party AGSL implementation of our problem.

| Aspect | What it does | Source |
|---|---|---|
| Shape | Rounded-rect SDF, per-corner radii; normal from the SDF gradient with the optical corner radius at 1.5x the drawn one, capped at half the short side. Our `rectNormal` uses the same 1.5x rule. | `GlassShaders.kt:883-915`, `RefractionProfile.kt` doc |
| Edge refraction profile (the Clear/default path) | `RefractionProfile.Edge(width)`: `t = dist/width`, `profile = exp(-t/0.225) * (1-t*t)^2`, magnitude `strength * displacementDp`, direction `-normal`. Peaks at the rim, ~0.29 at t = 0.25, ~0.06 at t = 0.5. Comment: "Empirical Clear fit". | `GlassShaders.kt:917-940` |
| Surface profile path (alternative) | `SurfaceProfile` enum Circle / Squircle / Concave / Lip gives a height `h(t)`; displacement = `h * strength * scale` along the SDF normal blended with a dome gradient on elongated shapes. Circle is `1 - sqrt(1 - x^2)`, squircle `1 - smootherstep(t^1.5)`. | `SurfaceProfile.kt`, `GlassShaders.kt:728-736, 751-760` |
| Shipped numbers | Regular: displacement 48 dp, `Edge(20dp)`, strength 0.7. Clear: displacement 56 dp, `Edge(28dp)`, strength 0.85, blur 1.25 dp. | `GlassDefaults.kt`, `GlassStyle.kt:78-82` |
| Fold | `refractionFoldStrength` 0..1 reverses the sampling derivative near the edge ("inverted edge-refraction fold"); default 0. | `GlassOptics.kt`, `GlassShaders.kt:863-881` |
| Dispersion | `ChromaticAberrationMode.Simple` = 3 taps (R forward, G centre, B backward), `Full` = 7 taps ("significantly more expensive"). Offset = `displacement * strength * 0.5 * cornerWeight`, `cornerWeight = abs(x*y)/(hx*hy)`. Default strength 0; Clear 0.04. | `ChromaticAberrationMode.kt`, `GlassShaders.kt:120-122, 1011-1047` |
| Rim light | Separate rim pass. A virtual point light (`lightPosition`, default Center); normal `(-grad, 1)`; `specular = pow(max(dot(n, L),0), exponent)`; alpha = `specular * intensity * edge`; edge is about 1 dp wide (`edgeSoftness`). Composited with a black `edgeShadow` (Regular 14% light / 32% dark). Exponent 24 Regular, 16 Clear. Intensity 0.48 / 0.55 (light), 0.38 / 0.42 (dark). | `GlassShaders.kt:565-585`, `GlassDefaults.kt`, `GlassStyle.kt:90-143` |
| Fresnel | `ambient = mix(1, 1 + (1 - n.z)^fresnelExponent, ambientResponse)`, applied multiplicatively to the refracted colour and clamped to 1. Exponent 3 (Regular) / 2.5 (Clear); response 0.15 / 0.42 light, 0.08 / 0.22 dark. A Schlick-style rim brightness without an additive white. Optionally blends in a luma-gradient normal of the content (`contentNormalBlend` 0.15 / 0.1). | `GlassShaders.kt:136-146, 1078-1086`, `GlassStyle.kt` |
| Grading | Per-style `whitePoint` (Regular light 0.55, dark -0.22; Clear 0.17 / -0.18), `chromaMultiplier` 1.5 light / 1.1 dark, `contrast`. Mixes toward white or black: the "adaptive luminosity" Apple describes. | `GlassDefaults.kt`, `GlassStyle.kt:90-143`, `GlassShaders.kt:1071-1100` |
| Blur / depth coupling | `blurRadius` and `depth` are size-responsive: 20 dp at 64 dp, 25 dp at 220 dp; depth 0.65 at 64 dp, 1 at 176 dp. A second, blurred sample is mixed by `depth`. Clear instead keeps a fixed 1.25 dp. | `GlassDefaults.kt`, `GlassOptics.kt` |
| Detail pass | `refractionDetailIntensity` composites a sharper layer in an edge band (our "clarity" analogue); 0.76 in Clear, 0 in Regular. | `GlassShaders.kt:160-185`, `GlassStyle.kt` |
| Dark/light | Every style has a light and a dark variant chosen from the system appearance. | `GlassStyle.kt:90-143` |
| Blur noise (non-glass module) | `HazeBlurDefaults.noiseFactor = 0.15`, blur 20 dp. | `haze-blur/.../HazeBlurDefaults.kt:19-24` |
| Performance guidance | Reduce area; prefer fewer surfaces; "reduce Glass features such as Full chromatic aberration"; measure with Macrobenchmark; CPU frame time "does not directly measure GPU shader time". | `docs/performance.md` |
| Repo's own research note | Argues Haze should add a dark edge following iOS 27, citing Apple WWDC26 112. | `docs/research/2026-08-31-ios-27-liquid-glass.md` |

### Kyant AndroidLiquidGlass (library author's source)

A Compose library with AGSL shaders (Android 13+). Three shaders, all tiny.

| Aspect | What it does | Source |
|---|---|---|
| Lens | `d = circleMap(1 - depth/refractionHeight) * refractionAmount` (circle profile `1 - sqrt(1 - x^2)`), sample at `coord + d * grad`, with `grad` the SDF normal (optionally plus a radial term, `depthEffect`). `refractionAmount` is passed negative, so it pulls inward like ours. Two knobs only: `refractionHeight` (band) and `refractionAmount` (displacement). | `effects/Lens.kt:49`, `internal/Shaders.kt:63-84` |
| Shipped numbers | Catalog: `lens(16dp, 32dp)` and `lens(24dp, 48dp, depthEffect=true)`; playground derives both from the control's short side (`height = frac * min * 0.5`, `amount = frac * min`). | `app/.../GlassPlaygroundContent.kt:72-78, 133`, `DialogContent.kt:67` |
| Dispersion | Optional 7-tap split, identical tap weights to ours, but `dispersionIntensity = chromaticAberration * (x*y)/(hx*hy)` (signed, corner-weighted) and the offset is `d * grad * intensity`, so it scales with the displacement. | `internal/Shaders.kt:104-158` |
| Highlight | A separate draw pass, blended `BlendMode.Plus`: `Plain` white 38% (no shader), `Default` white 50% with `angle` 45 deg and `falloff`: `pow(abs(dot(normal, dir)), falloff)`, lit on both sides of the axis; `Ambient` lights one side. Knobs: colour/alpha, angle, falloff. | `highlight/HighlightStyle.kt:33-90`, `internal/Shaders.kt:160-204` |
| Colour | `vibrancy()` = saturation 1.5; `colorControls(brightness, contrast, saturation)`; dialog uses brightness +0.2 on light theme, saturation 1.5. | `effects/ColorFilter.kt:29-45`, `DialogContent.kt:62-66` |
| Blur | Plain blur before the lens, 4-16 dp in demos, smaller in light/dark dialog (16 / 8 dp). | `DialogContent.kt:66` |

Not read: the library's GitBook docs (kyant.gitbook.io/backdrop).

### Technique author: kube.io

"Liquid Glass in CSS/SVG" (kube.io/blog/liquid-glass-css-svg/) is the primary write-up of the
physical method the two libraries approximate: a convex height profile `y(x)`, the normal as the
profile's derivative rotated -90 deg, Snell-Descartes with n1 = 1 and n2 = 1.5, one refraction event,
rays orthogonal to the background plane, displacement precomputed along one radius (127 rays) and
rotated, then normalised to a maximum magnitude. Profiles: convex circle `sqrt(1-(1-x)^2)`, convex
squircle `(1-(1-x)^4)^(1/4)`, concave `1 - convex`, lip `mix(convex, concave, smootherstep)`. Highlight
is "a simple rim light" whose intensity depends on the normal's angle to a fixed direction, with a
specular-angle control (e.g. -60 deg). The blog is a single author's article (no repo cited in the
page), so treat the numbers as one author's choices.

### Android platform

| Item | Finding | Source |
|---|---|---|
| Window blur | `Window.setBackgroundBlurRadius` (API 31) "approximates a Gaussian blur", needs a translucent floating window, "Some devices might not support cross-window blur"; can be disabled at runtime in battery saver. | developer.android.com/reference/android/view/Window#setBackgroundBlurRadius(int) |
| Window blur guidance | "Avoid blur radii higher than 150 px"; "choose a blur radius value complemented with a translucent layer of color"; listen via `WindowManager#addCrossWindowBlurEnabledListener`. | source.android.com/docs/core/display/window-blurs |
| `RenderEffect` | `createBlurEffect` (API 31), `createRuntimeShaderEffect(shader, uniformName)` (API 33) passes the node's contents as the child shader. | developer.android.com/reference/android/graphics/RenderEffect |
| AGSL docs | `RuntimeShader` per-pixel; `RenderEffect` is "more expensive than drawing a custom View". | developer.android.com/develop/ui/views/graphics/agsl/using-agsl |
| Material 3 Expressive | No glass or refraction specification found in the pages searched. | untraced, see Open questions |
| Android 16 blur | Only press coverage found (system-wide blur in the app drawer and Quick Settings, off in battery saver, a QPR2 setting). No new public glass API found. | untraced beyond the Android 12 APIs above |

## Physics that costs little

| Piece | What the physics says | What the refs actually use | Why it does or does not read neon |
|---|---|---|---|
| Normal field | Normal from the SDF gradient of a rounded rect (or the derivative of a height profile, kube.io). | Both libraries: SDF gradient with the 1.5x corner radius (`internal/Shaders.kt:40-47, 79`; `GlassShaders.kt:883-915`). Ours already matches (`rectNormal`). | Nothing to gain; keep. |
| Thin vs thick slab | glTF separates "thin-walled" (thickness 0) from a "volume boundary" with refraction by IOR (default 1.5). A thin pane barely displaces; a thick slab bends a lot at the rim. | Refs implement only the thick edge: displacement is a function of distance to the rim, zero in the face. | Thick-edge-only is why the face stays calm and the rim carries the look. |
| Snell's law | `refract(I, N, eta)`: `k = 1 - eta^2 (1 - dot(N,I)^2)`, `eta*I - (eta*dot(N,I) + sqrt(k))*N`, zero if `k < 0` (total internal reflection). GLSL ES 1.0 spec, section 8.5, available in AGSL's GLSL ES 1.0 baseline. | Neither library calls `refract`. Both use displacement = `profile(depth) * amount` along the normal (a heuristic with the same shape as a Snell result for a convex edge). Kube precomputes true Snell displacement. | The heuristic is monotone and cheap. A real Snell table would add a per-channel IOR for free but needs an LUT or a closed form. |
| Fresnel (Schlick) | Reflectance blends from `f0` toward 1 with `(1 - cos)^5`; `f0 = ((n-1)/(n+1))^2`, about 4% for n = 1.5. Filament documents Schlick and the IOR to f0 mapping. | Haze: `(1 - n.z)^3` (Regular) or `^2.5` (Clear), scaled by `ambientResponse` 0.08-0.42, applied multiplicatively to the colour. | A multiplicative lift cannot exceed the face colour times (1 + response), so it never outlines. Ours adds a constant white. |
| Dispersion | Per-channel IOR via Cauchy `n(lambda) = A + B/lambda^2`; glTF `KHR_materials_dispersion` stores `20/Vd` (Abbe number), range 0..1 realistic, above 1 "artistic". Colour offset is proportional to the displacement, hence to the bend. | Offsets proportional to displacement and weighted to corners (both libraries). | Proportional-and-cornered fringe stays under about 1 dp at Clear's numbers; ours is a fixed 16 dp at 100%. |
| Specular | Phong-style lobe `pow(dot(n, L), k)` from a light direction. | Haze k 16-24 from a virtual light; Kyant `pow(abs(dot(n, dir)), falloff)` with angle 45 deg. | A lobe lights an arc of the rim, not the whole outline. A fixed "everywhere" highlight is an outline. |
| Edge shade | Not Fresnel; a depth cue. | Haze `edgeShadow` black 10-32%; Apple 2027 "darkened edge". Ours has a 15% contour. | Dark under bright keeps the rim readable on light wallpapers without raising the white. |

Sources: Filament `google.github.io/filament/Filament.md.html` (Schlick94, reflectance remapping);
glTF `KHR_materials_volume` and `KHR_materials_dispersion` READMEs in
`github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos`; GLSL ES 1.00 spec
`registry.khronos.org/OpenGL/specs/es/2.0/GLSL_ES_Specification_1.00.pdf`.

## Mapping to our knobs

Our model today (`GlassRefraction.java`, AGSL): `e = x^2` with `x` = 0 at the band's inner edge and 1
at the rim; pull = `e * min(bendDp, 0.45 * band)` along the normal; tangential slab lens capped at the
pull; additive hairline `rim * uRim * rimK` (`rimK` between 0.4 and 1); dark contour 15% max; 7-tap
split `x * uDisp` (max 16 dp) along the normal; clarity mixes `sharp` by `smoothstep(x) * uClarity`.

| Our knob | What the references do | Recommendation (for the developer to decide) |
|---|---|---|
| Bend dp (0-30, default 9; Clear 11) | One "amount" knob, large next to the band: Haze Clear 47.6 dp over 28 dp; Kyant 32 dp over 16 dp, 48 over 24. Strength is also a 0..1 knob (Haze 0.7-0.85) times a max displacement. Playground ties both to the control's short side. | Mis-scaled: max effective pull is 0.45 x band (12.6 dp at band 28). Express it as a multiple of the band (about 1.0-2.0) or as strength x max displacement, and let a separate fold or "soft clamp" decide whether to allow compression. Consider scaling with the short side (Apple: larger glass lenses more). |
| Edge width dp (1-60, default 20) | Haze `Edge(width)` 20-28 dp; Kyant `refractionHeight` 16-24 dp. Same meaning, same range as ours. | Keep. Revisit the profile inside it: the refs concentrate displacement near the rim (circle or exp-decay), ours is broader per unit depth but small in amplitude. |
| Edge light % | Not one knob: light intensity (alpha 0.38-0.55) plus lobe exponent or falloff, plus light angle, plus a dark edge alpha (10-32%), plus Fresnel response (0.08-0.42). Highlights composite as alpha or `Plus`. | Mis-scaled and overloaded: one slider drives the additive hairline, the contour and the bevel gate. Split into edge light (lobe, with angle and falloff) and edge shade; make the Fresnel lift multiplicative. |
| Specular % (bevel along the diagonal, 4th power) | Haze: one `specularIntensity` with `specularExponent`; Kyant: one highlight with `angle` and `falloff`. | Redundant with edge light once that is directional. Fold it into the lobe and expose angle instead. |
| Dispersion % (max 16 dp, linear in the band) | Haze strength 0..1, default 0, Clear 0.04, scales with displacement, corner-weighted, Simple (3) or Full (7) taps. Kyant boolean + intensity, corner-weighted, 7 taps. | Mis-scaled: the cap is too high and independent of the bend. Make it a fraction of the displacement and weight it toward corners; a 3-tap path is the references' default. |
| Clarity % (sharp frame mixed across the band) | Haze: size-responsive `depth` (second blurred sample) and `refractionDetailIntensity`; Clear uses 1.25 dp blur instead. Kyant: plain `blur()` before the lens. Apple: clear variant needs a dimming layer (35% over bright). | Keep as the blur-coupling knob, but couple it to the surface's blur and size. Apple's dimming layer is a knob we lack for the clearest glass. |
| (missing) Profile shape | Haze `SurfaceProfile` Circle/Squircle/Lip/Concave; kube.io the same four. | Optional preset-level enum; Squircle is the gentler option for wide pills. |
| (missing) Light angle | Haze `lightPosition`; Kyant `angle`; kube.io specular angle. Ours is two hard-coded lights. | Expose one angle. |
| (missing) Saturation / white point / contrast | Haze `chromaMultiplier`, `whitePoint`, `contrast` with separate light and dark values; Kyant `vibrancy()` 1.5. | A face-grading knob would let low-opacity glass stay legible without raising edge light. |
| (missing) Tint strength | Haze `tintColor`; Apple `tint(_:)`, used sparingly. | We already have tint presets; strength is the gap. |
| (missing) Fold | Haze `refractionFoldStrength`. | See bend. |

## Android constraints

| Constraint | Source |
|---|---|
| AGSL is "largely compatible with GLSL ES 1.0"; for-loops must be unrollable, "initializer, the test condition, and the next statement must use constants", next limited to `++ -- += -=`; `discard` is not allowed; arrays index only by constant or loop variable. | developer.android.com/develop/ui/views/graphics/agsl/agsl-quick-reference |
| `shader.eval(coord)` is how a child (the wallpaper frame) is sampled; "complex shaders can be expensive to evaluate, particularly in a loop". No numeric per-pixel sample budget is published. | same page; AGSL `using-agsl` page |
| AGSL "fixes its GLSL feature set at GLSL ES 1.0"; preprocessor directives are not supported, use `const`. | developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl |
| A shader tree compiles to one fragment shader per draw, so cost is per pixel covered. | developer.android.com/develop/ui/views/graphics/agsl (Theory of operation) |
| Derivatives: GLSL ES 1.0 makes `dFdx/dFdy/fwidth` an optional extension (`OES_standard_derivatives`). Neither reference uses them: Kyant takes an analytic SDF gradient, Haze takes finite differences of the height function or an analytic gradient. | GLSL ES 1.00 spec; `internal/Shaders.kt:40-47`; `GlassShaders.kt:958-975` |
| Window blur may be disabled at runtime (battery saver, unsupported GPUs); apps should adapt. | source.android.com/docs/core/display/window-blurs |
| Samples in the references: Haze Simple 1 + 2 taps (+1 optional blurred depth), Full 1 + 6 + depth; Kyant 1 or 7. Ours: 1 face, 7 with dispersion in the band, +1 for clarity. | `GlassShaders.kt:1011-1047`; `internal/Shaders.kt`; `GlassRefraction.java` AGSL |

Implication for our cost: pixels outside the band take one sample, so band area (edge width x
perimeter) bounds the extra cost; a 3-tap split would cut the dispersion cost by more than half. This
is arithmetic on the sample counts above, not a measurement; `docs/agents/performance.md` governs
any change that ships.

## Open questions

1. Is the "too subtle" report mostly amplitude (Summary item 1)? The 0.45 hold was added to stop
   folds; the references allow them. A baseline render of Clear at ratio 0.39 vs 1.0 and 2.0 on pong
   would settle it. Fold may cost legibility of text under the rim.
2. Should edge light be directional (angle and falloff) or stay omnidirectional on a dock, which is
   lit from the top in our model (`kLightUp`)? Haze defaults the light to the surface centre.
3. Does Clear need Apple's dimming layer on bright wallpapers? Apple's 35% figure is for iOS clear
   glass over media; we have not measured our wallpapers.
4. Corner-weighted dispersion kills the fringe along a dock's straight edges. Is a corners-only
   fringe wanted, or should a faint full-edge fringe survive for Clear?
5. Per-channel IOR: Haze and Kyant offset taps proportionally to displacement; true Cauchy IOR would
   give the red/blue ratio. No reference does the latter.
6. Light/dark variants: Haze flips specular, edge shadow, white point and chroma by system
   appearance. We have scheme chrome but not a dark glass variant for edge values.

### Untraced (not asserted)

- Apple's actual lensing algorithm, IOR or highlight formula: not published; only design intent.
- Android 16 platform glass or blur APIs: only press coverage found; no developer.android.com page.
- Material 3 Expressive guidance on glass or refraction: not found; m3.material.io was not read.
- Whether AGSL exposes `dFdx/dFdy`: the AGSL quick reference does not mention them either way.
- Any numeric per-pixel sample budget for `RuntimeShader`: none in Android docs.
- Schlick's 1994 paper itself (Computer Graphics Forum 13(3)) was not fetched; its formula is cited
  through Filament, which cites it as [Schlick94].
- Kube's claim that Apple's highlight is "a simple rim light" is the author's reading, not Apple's.
- The slope arithmetic (Haze Clear folds at the rim: displacement 47.6 dp over 28 dp, profile slope
  4.4 per width at t = 0, so 7.5 > 1) is my calculation from `GlassShaders.kt:931` and
  `GlassStyle.kt:78-82`, not stated by Haze.
