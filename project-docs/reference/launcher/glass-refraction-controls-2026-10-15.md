# Glass refraction: rim light, depth, clear edge and controls (2026-10-15)

Builds on `glass-refraction-references-2026-10-15.md` (read it first; not repeated). Read-only research
against the developer's feedback on the current build. No code changed.

Sources, all read in full where cited:

- **Haze@6dcad7c** = github.com/chrisbanes/haze `main` at `6dcad7cd5406514220942f2cc5d5454eb964917a`
  (2026-10-15 13:33 +0100; the earlier note pinned c2bdd15, the numbers below still hold). Paths are
  under `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/` unless they start `docs/`.
- **Kyant@65ab177** = github.com/Kyant0/AndroidLiquidGlass, branch `kmp`, `backdrop/src/commonMain/kotlin/com/kyant/backdrop/`
  (`internal/Shaders.kt` = `Sh`); demo values from `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/`.
- **Apple**: developer.apple.com pages fetched 2026-10-15 (HIG Materials, SwiftUI `Glass`, `glassEffect(_:in:)`,
  WWDC25 219, WWDC26 112 and 8012). Apple publishes no shader and no numbers.
- **Ours**: `app/src/main/java/com/termux/app/chrome/GlassRefraction.java` (`GR`), `SurfacePresets.java`,
  `GlassEdgeControls.java`.
- Arithmetic marked (calc) is mine, from the cited lines, not stated by a source.

## Answers in brief

1. **Highlight.** Both libraries draw a hairline, not a band: Haze about 1 dp, Kyant 0.5 dp. Ours is 4 dp wide
   at the full lobe. At the references' own alphas the lit strip carries 0.24 to 0.36 alpha-dp; ours carries
   1.1 at the shipped Looks (light 50) and 0.4 at the default (light 18). Your 8-10% finding is the references' level (calc, table 1).
2. **Depth.** Our displacement numbers match Kyant's bottom bar and sit within 25% of Haze Clear. The
   references are not more continuous with the wallpaper at the rim; they all jump there. What differs is
   what the bend is applied to (a blurred, graded face in Haze; a vibrant, tinted, shadowed face in Kyant)
   and that Haze's band reads 10 dp wide where ours reads 26 dp.
3. **Clear edge.** Only Haze has an equivalent, and it is a fixed per-style constant (Clear 0.76, Regular 0).
   Kyant has none. A fixed value is the norm.
4. **Controls.** No reference gives end users an edge slider. Apple's public API has no numeric optics; Haze
   and Kyant are developer APIs. Apple's only user control is a system-wide clear-to-tinted slider.
5. **Accuracy.** Seven differences in kind, section 5.

## 1. The rim light

| | Width | Peak alpha | Blend | Lobes | Direction | Fixed or knob |
|---|---|---|---|---|---|---|
| **Haze rim** | `edge = 1 - smootherstep(-sd / max(edgeSoftness, sampleStep))`, `edgeSoftness` 1 dp (`GlassDefaults.kt:66`), `sampleStep` 2 px (`GlassRenderParams.kt:654`): about 1 dp, integral 0.5 dp (calc) | `specularIntensity` Regular 0.48 / dark 0.38, Clear 0.55 / 0.42 (`GlassDefaults.kt:51`, `GlassStyle.kt:90-147`) | SrcOver. Premultiplied white `specularAlpha`, then black `edgeShadow` under it: `alpha = s + sh(1-s)` (`GlassShaders.kt:581-583`) | One lobe: `pow(dot(n3, L3), k)`, k 24 Regular, 16 Clear; vectors are `(-grad,1)` and `(l,1)`, so the dot is `(1+cosθ)/2` and the half-power angle is about 20 deg (k 24) or 24 deg (k 16) (calc) | `lightPosition` an `Alignment`, default `Center` (`GlassStyle.kt:493`): the light is toward the surface centre, so on a wide pill only the middle of the long edges lights (calc), not a top lamp | Style value, `0..1`; docs list it under Parameters for app developers (`docs/effects/glass/index.md:106`) |
| **Haze edge shadow** | same 1 dp | black 14% Regular light, 32% dark, 10% / 25% Clear (`GlassStyle.kt:91, 107, 126, 142`) | under the light, SrcOver | all round the rim | none | Style value |
| **Kyant Plain** (catalog default; used in 6 demos) | `Highlight.width` 0.5 dp, blur 0.25 dp (`highlight/Highlight.kt:10-15`); stroke is `ceil(px)*2` centred on the outline and clipped to it, so `ceil(0.5 dp)` px inside: 1-2 px, about 0.7 dp at 2.75x (`HighlightModifier.kt:153`, calc) | white 38% (`HighlightStyle.kt:33-36`), layer alpha 1 | `BlendMode.Plus` (additive) (`HighlightModifier.kt:118`) | none: uniform round the rim, no shader (`HighlightStyle.kt:38-41`) | none | Style, 3 presets; `GlassPlaygroundContent.kt:79, 135`, `DialogContent.kt:69` |
| **Kyant Default** | same 0.5 dp | white 50% (`:46`) | Plus | `pow(abs(dot(grad, dir)), falloff)`, falloff 1: `abs` lights both sides of the axis, 60 deg half-power (`Sh:160-181`) | `angle` 45 deg default; Control Center feeds the gravity sensor, falloff 2 (`ControlCenterContent.kt:100-104`) | Params `angle`, `falloff`, colour; only the bottom-bar thumb uses Default (`LiquidBottomTabs.kt:217, 254`) |
| **Kyant Ambient** | 0.5 dp (0.33 dp on slider/toggle thumbs, `LiquidSlider.kt:178-180`) | white 38% (`:75`) | SrcOver | one side only: `step(0, d)` (`Sh:202`) | 45 deg, falloff 1 | thumbs only, alpha follows press |
| **Ours** | `RIM_WIDTH_DP` 4 dp, full for 2, gone at 6, so integral 4 dp (`GR` `Look.RIM_WIDTH_*`, AGSL `rim`; calc) | `uRim` = light x 0.55 (`MAX_LIGHT_ALPHA`); Clear ships light 50 = 0.275; default 18 = 0.099 | SrcOver (mix to white) over a multiplicative dark edge | one lobe, `max(dot(n, up), 0)^6`, half-power 28 deg | fixed lamp straight above | Edge light slider, plus Specular reshaping the lobe |

Lit-strip energy at the lobe peak (alpha x width, calc): Haze Regular 0.48 x 0.5 = 0.24 (Clear 0.28); Kyant Plain
0.38 x 0.7 = 0.27, Default 0.5 x 0.7 = 0.36; ours at slider 9: 0.05 x 4 = 0.2, at the default 18: 0.4, at 40 to 50 (Tint, Mist,
Clear): 0.9 to 1.1. The dark edge: Haze 0.14 x 0.5 = 0.07 dp (Clear 0.05); ours at light 50: 0.16 x about 4.5 = 0.7.
So the milky read is width times alpha, about 4x too much at the default and 4-5x at the shipped Looks, not the lobe: Haze k 16-24 on its 3D dot
is the same arc as our 2D k of about 8-10 (calc: half-power 21-24 deg), against our 6. Apple states no width or alpha; the only
words are "a lot sharper and a lot crisper ... even has these dark parts, which helps define the shape" and
"specular ... is the light that draws around the edge of the object, which really gives it the definition"
(WWDC26 8012, Icon Composer lab; `developer.apple.com/videos/play/wwdc2026/8012/`). The sizes are untraced.

**Fixed or a knob?** Fixed material in every reference. Haze sets it per built-in style and light/dark; its docs
call `specularIntensity` a Style parameter for developers. Kyant fixes it per demo. Apple's icon tool has "automatic"
as the default and says the defaults "hopefully work well without you having to touch them" (8012), and the app API has
no highlight parameter. Nobody exposes it to end users.

## 2. Depth: displacement, band, fold, cohesion

Documented intent (Apple, no shader). "Liquid Glass uses these instinctive visual cues to provide separation ... while
letting content shine through underneath it ... definition against the background content while still feeling visually
grounded" (WWDC25 219). Larger glass has "more pronounced lensing and refraction" and "a softer scattering of light"
(219). Regular "blurs and adjusts the luminosity of background content"; clear is "highly translucent" and wants a 35% dimming
layer over bright content (HIG Materials). Tint "mapped to content brightness underneath" (219).

What the shaders do. Displacement in dp for a 64 dp tall dock (calc from `Sh:63-84`, `GlassShaders.kt:917-940`, `GR` `profile`/`softClamp`; ours with the Clear Look's bend 26 over 26, pull not above 1.5 bands):

| depth in from the rim, dp | 0.5 | 2 | 4 | 6.5 | 10 | 13 | 20 | rim slope reverses (fold) out to |
|---|---|---|---|---|---|---|---|---|
| Ours Clear 26/26 | 21 | 16 | 12 | 8.8 | 5.5 | 3.5 | 0.7 | 7.6 dp |
| Ours Mist 24/24 | 19 | 14 | 11 | 7.6 | 4.5 | 2.7 | 0.3 | 7.0 |
| Ours default 9/20 | 7 | 5 | 3.6 | 2.4 | 1.2 | 0.6 | 0 | 1.8 |
| Kyant bottom bar `lens(24, 24)` (`LiquidBottomTabs.kt:172`) | 19 | 14 | 11 | 7.6 | 4.5 | 2.7 | 0.3 | 7.0 |
| Kyant button `lens(12, 24)` (`LiquidButton.kt:61`) | 17 | 11 | 6.1 | 2.7 | 0.3 | 0 | 0 | 6.6 |
| Kyant scroll/playground `lens(16, 32)` (`GlassPlaygroundContent.kt:133`) | 24 | 17 | 11 | 6.3 | 2.3 | 0.6 | 0 | 8.8 |
| Kyant dialog `lens(24, 48)` (`DialogContent.kt:67`) | 38 | 29 | 22 | 15 | 9.0 | 5.3 | 0.7 | about 10 |
| Haze Regular Edge(20), 0.7 x 48 dp | 29 | 20 | 12 | 6.0 | 2.0 | 0.6 | 0 | 8.7 |
| Haze Clear Edge(28), 0.85 x 56 dp | 30 | 23 | 16 | 10 | 5.0 | 2.5 | 0.3 | 10.4 |

- **Amount and band.** Kyant: amount 1x to 2x the band (24/24, 12/24, 16/32, 24/48; slider thumb 10/14 `LiquidSlider.kt:171`). Haze:
  `min(strength x displacement, shortSide/2)` (`GlassShaders.kt:932-935`), so the 33.6 / 47.6 dp "headline" is capped to 32 dp on a
  64 dp dock, and `RefractionProfile.Edge` documents "displacement is also capped at half that side" (`RefractionProfile.kt:21`).
  The previous note's "1.5-2x the band" holds for Kyant only; for Haze on a dock it is 1.1-1.6x, and ours (1x) is not shallower than either.
- **Fold or clamp.** Nobody clamps the slope. All three fold near the rim: the sampled point moves outward over the outer 7-10 dp (table, calc).
  Haze adds `refractionFoldStrength`, default 0 and used by no built-in style, which flattens the rim displacement toward 2% of its height
  (`GlassShaders.kt:863-881`). Kyant has no cap other than the layer edge. Ours adds a room-based soft clamp (section 5).
- **Continuity with the outside picture.** None of the three is continuous at the outline: at depth 0 the rim pixel shows the picture
  20-30 dp further in (or 38 for Kyant's dialog). Only Haze softens it, by mixing the displaced colour toward the undisplaced pixel across
  the outermost 1 dp (`shapeMask`, `opticalColor = mix(sampleDepth(coord), opticalColor, shapeMask)`, `GlassShaders.kt:85-88, 148-150`). Ours has no such mix.
- **Band width as seen.** The visible ring is far narrower than the band: Haze Regular loses 90% by 10 dp (of a 20 dp band), Kyant 16/32 by 10 dp. Ours
  still moves 5.5 dp at 10 dp and 3.5 at 13, because the circle profile has a fat tail (calc). On a smooth wallpaper that is invisible; on edges and text it
  is the "different from the background" read. That displacement is only visible on high-frequency content is arithmetic, not a claim any reference makes.

What keeps their glass reading as part of the picture (face unchanged, graded, blurred or shadowed):

| | Face | Source |
|---|---|---|
| Haze Regular | beyond the band no displacement; blur 20 dp at 64 dp tall, 24-25 dp at 176-220; `depth` 0.65-1 crossfades sharp to blurred (`Canvas.kt:48-52`); light `whitePoint` +0.55 mix toward white, `chromaMultiplier` 1.5, so the face is lifted and saturated | `GlassDefaults.kt:33-48, 87-90` |
| Haze Clear | blur only 1.25 dp at every size, `whitePoint` 0.17, chroma 1.05, contrast 0.08, ambient lift 0.42; docs: Clear over bright content needs a dimming layer, "Haze does not add dimming automatically" | `GlassStyle.kt:77-86, 123-138`, `docs/effects/glass/index.md:81-82` |
| Kyant | `vibrancy()` saturation 1.5 on nearly every demo; container colour over it (light 0xFAFAFA at 40-60%, dark 0x121212 at 40%; Control Center black 5%); blur 2-8 dp (button 2, bar 8, dialog 16 light / 8 dark, playground default 0); outer `Shadow` radius 24 dp, y offset 4, black 10% (`Shadow.kt`) | `ColorFilter.kt:29-45`, `DialogContent.kt:42-44, 62-67`, `LiquidBottomTabs.kt:72-74` |
| Apple | tint and dynamic range follow the content; shadows deepen under text (219); Clear gets a 35% dimming layer (HIG) | cited above |

Ours at rest (Clear Look): opacity 10, blur 4, face "nearly the wallpaper itself" (`SurfacePresets.java:81-90`), no face grading in the
program, and the rim shows sharp wallpaper from 20 dp in (clarity 100). The references that look like Clear (Haze Clear, Kyant button) both grade the
face (whitePoint, vibrancy plus container colour). Whether the missing grade or the 26 dp tail explains the developer's read is untraced; a pong
side-by-side at band 16-20 and bend 1.5-2x that (Kyant 16/32, Haze Regular), with the same face, would separate them.

## 3. The clear edge

| | Equivalent | Value | Fixed? | Source |
|---|---|---|---|---|
| Haze | `refractionDetailIntensity`: a sharp layer composited where the refracted source point lies between w/2 and w of the edge (`innerEnvelope` x `outerEnvelope`), `opticalColor *= 1 - detailAlpha`, width `min(refractionHeight, max(2 x edgeSoftness, 20 x sampleStep))` = 40 px (calc) | Clear 0.76, Regular 0, raw `GlassOptics` default 0.76 | per style; docs call it a Parameter, `0..1` | `GlassShaders.kt:160-185, 384-480`, `GlassRenderParams.kt:89-96`, `GlassStyle.kt:85`, `GlassDefaults.kt:47` |
| Haze `depth` | sharp-to-blurred crossfade over the whole face, not an edge band | 0.65 at 64 dp to 1 at 176 dp | per size | `GlassDefaults.kt:33-37` |
| Kyant | none: `blur()` runs before `lens()` in the effect list, so the lens bends the already blurred picture | blur 0-16 dp as above | per demo | `GlassPlaygroundContent.kt:70-72`, `DialogContent.kt:62-67` |
| Apple | none documented; Clear is a variant, not an edge parameter | | | HIG Materials |

Two differences from ours: Haze keys the sharp window to where the sample comes from (source depth), ours to where the pixel is (`smoothstep(x) x uClarity`
on output depth); and ours is one slider where Haze uses a per-style constant. Why ours shows nothing on the shipped Looks is untraced. Two candidates
from the code, unmeasured: the clear frame is only handed in when `blurRadiusDp > CLARITY_RADIUS_DP` (2 dp, `WallpaperFrostPainter.java:346-349`), so Clear's blur 4 vs a 2 dp
copy differs by 2 dp of blur, nothing to see; and where it applies, the weight is highest in the rim's first few dp, which the bend compresses (table 2). A fixed per-Look value is the norm.

## 4. Controls, by audience

| API | Knobs and defaults | Audience |
|---|---|---|
| **Apple `glassEffect(_:in:)`** | `Glass`: `.regular` (default), `.clear`, `.identity`, `.tint(_:)`, `.interactive(_:)`; shape default a capsule; `GlassEffectContainer`, `glassEffectID`, `glassEffectUnion`, `glassEffectTransition`; button styles `.glass`, `.glassProminent` (SwiftUI `Glass`, `glassEffect(_:in:)` pages). No numeric blur, refraction, edge or highlight. | app developers |
| Apple, system | iOS 27 slider "anywhere from ultra clear to fully tinted"; Reduce Transparency ("frostier"), Increase Contrast ("predominantly black or white ... contrasting border"), Reduce Motion (WWDC26 112, WWDC25 219) | **end users** |
| Apple Icon Composer | specular automatic / inside / outside, blur, refraction height and strength, "inverse refraction", translucency, shadows (WWDC26 8012) | icon designers, design time |
| **Haze `GlassStyle`** | `specularIntensity` 0..1 (0.48), `edgeShadow` (black 14%), `ambientResponse` 0..1 (0.15), `edgeSoftness` (1 dp), `lightPosition` (Center), `chromaticAberrationStrength` 0..1 (0), `Mode` Simple/Full, `contrast` -1..1 (0), `whitePoint` -1..1 (0.55), `chromaMultiplier` 0..2 (1.5), `contentNormalBlend` 0..1 (0.15), `specularExponent` >=0 (24), `fresnelExponent` >=0 (3), `tint`, `backgroundColor`, `alpha`, `shape`, `surfaceProfile` | app developers |
| Haze `GlassOptics` | `refractionStrength` 0..1 (0.7), `refractionDisplacement` dp (15; Regular 48), `refractionHeightFraction` 0..1 (0.25), `refractionProfile` Surface/`Edge(width)`, `refractionFoldStrength` 0..1 (0), `refractionDetailIntensity` 0..1 (0.76), `depth` 0..1, `blurRadius` dp (14), size-responsive variants | app developers |
| Haze accessibility | `GlassAccessibilitySettings(reduceTransparency, increaseContrast, showBorders)` mapped from the platform; "Haze does not read platform accessibility settings" | end user, via platform |
| **Kyant `drawBackdrop`** | `lens(height, amount, depthEffect=false, chromaticAberration=false)` (chromatic strength hard-set to 1, `Lens.kt:52`; the playground slider only toggles the boolean, `GlassPlaygroundContent.kt:77`), `blur(r)`, `vibrancy()` (sat 1.5), `colorControls(brightness 0, contrast 1, saturation 1)`, `opacity`, `Highlight(width 0.5 dp, blur, alpha 1, style)`, `Shadow(24 dp, y r/6, black 10%)`, `InnerShadow(24 dp, y r, black 15%)`, surface colour | app developers |

Haze states the stance: its styles "do not follow the iOS appearance slider or adapt to the content beneath them" (`docs/effects/glass/index.md:85-87`), and its research note
rejects "inventing a public iOS slider" (`docs/research/2026-08-31-ios-27-liquid-glass.md`). No reference gives end users an edge, highlight, bend or dispersion control; the
user-level levers are transparency/tint and accessibility.

**Recommendation for a home launcher (evidence above; developer decides).**

| Layer | What | Why |
|---|---|---|
| Fixed, no UI | highlight: about 1 dp wide, peak alpha 0.4-0.5 light and 0.4 dark, k about 8-10 in our 2D terms, one lobe; dark edge 10-14% over the same 1 dp; no per-Look value | Haze and Kyant both fix it at hairline width; the developer's 8-10% sits at their alpha-dp (section 1) |
| Fixed, per Look | band 16-20 dp (Haze Regular 20, Kyant 16); amount 1.5-2x the band; dispersion 0 on Mist/Tint, at most 0.02 x pull on Clear; clarity a per-Look constant (Haze 0.76 / 0); face grade (lift, saturation) per Look | per-style constants are the norm; today's Clear tail of 26 dp is the widest of the set |
| Zero or one slider | one "Glass" slider that moves the whole Look clear to frosted (blur plus opacity plus the Look's bend, Haze `refractionStrength` style) | the only user-level control Apple ships is clear to tinted; Haze's `refractionStrength` 0..1 is its headline optic |
| Accessibility, not sliders | Show-borders / Increase-contrast raise the fixed edge (Haze `showBorders`, `increaseContrast`); Reduce-transparency raises blur | the platform settings Apple and Haze both map |
| Drop or hide | Edge light, Specular, Clarity, Dispersion sliders in `GlassEdgeControls` (keep the stored keys so saved Custom looks load) | no reference exposes them to users |

## 5. Our shader against the two open ones

Differences in kind only. `GR` AGSL lines are by name; the file is `GlassRefraction.java`.

| # | Piece | Reference | Ours | Kind |
|---|---|---|---|---|
| 1 | Profile | Kyant `circleMap`, `Sh:63-65, 78`: same as `profile()`. Haze Edge: `exp(-t/0.225)(1-t^2)^2`, finite support, decays much faster (`GlassShaders.kt:928-931`) | circle, the Kyant curve | Same as Kyant; unlike Haze (fat tail, table 2) |
| 2 | Cap on displacement | Haze `min(strength x scale, shortSide/2)`, a plain cap (`:932-935`); Kyant none | `softClamp(e x uStrength, room)`, knee 0.75 of the room left to the surface's middle, never reaching it | Ours is a soft room-based clamp; neither reference has one |
| 3 | Slab lens | Neither. Nearest: Kyant `depthEffect` adds `normalize(centred)` to the normal (`Sh:80`), off by default (`Lens.kt:50`); Haze blends a dome gradient on elongated surface-profile shapes (`:751-760`) | `slabOffset`: tangential pull toward the centre, capped at `e x uStrength` | Ours is an extra pass |
| 4 | Edge-of-shape continuity | Haze mixes displaced to undisplaced over the first 1 dp (`edgeSoftness`, `:85-88, 148-150`); Kyant hard cut | none | Missing |
| 5 | Light | Haze: separate pass, 1 dp, 3D dot, light at `lightPosition` (default centre), alpha-over over a black shadow (`:565-585`); Kyant: hairline, Plus, none/abs-cos/one-sided (`Sh:160-204`) | 4 dp, 2D `cos^6` toward a fixed top lamp, mix-to-white over a multiplicative dark edge | Width and direction model differ |
| 6 | Thickness shading | Haze Fresnel `ambient = mix(1, 1 + (1-n.z)^e, response)`, applied multiplicatively and clamped, symmetric all round, response 0.15 / 0.42 (`:127-146`); Kyant none | `col x (1 + e x thick x (0.12 lit, -0.06 shadow))`: directional, brightens the lit side up to 12% and dims the other | Haze is non-directional; Kyant has none |
| 7 | Dispersion | Haze Simple: `chromaOffset = displacement x strength x 0.5 x cornerWeight`, R forward, B back, 3 taps (`:120-122`, `ChromaticAberrationMode`); Clear 0.04, so about 2% of displacement. Kyant: `d x grad x intensity`, `intensity = x*y/(hx*hy)` signed, 7 taps (`Sh:104-158`), strength fixed 1 | `split = pull x uDisp x cornerWeight`, 3 taps along n, `uDisp` up to 0.25; Clear ships 35%, so 0.0875 of the pull = about 1.8 dp at a corner (calc) | Same structure; ours reaches about 3x Haze Clear at a corner (1.8 vs 0.6 dp, calc) and Kyant's signed weight flips the fringe per quadrant |
| 8 | Face grade | Haze `whitePoint`, `chromaMultiplier`, `contrast` per style, light/dark variants (`:1071-1100`); Kyant `vibrancy`, container colour, shadow | none in this program | Missing here (tint is elsewhere) |
| 9 | Sharp copy | Haze keys it to source depth; Kyant none | keyed to output depth, one slider | Differs |

Matches that need no change: the corner-normal radius rule (`min(r x 1.5, min half side)`, `Sh:79, 116`, `GlassShaders.kt:884-890`; `GR` `rectNormal`), the SDF
gradient on straight edges, inward direction, corner weight `|x y|/(hx hy)`, and the 3-tap split (Haze `sampleChromaSimple`).

## Corrections to the earlier note

- "Both libraries displace by 1.5-2 times the band" is true of Kyant's catalog (with the 24/24 bottom bar at 1x) but not of Haze on a dock, where the 32 dp cap applies (1.1-1.6x). Our Clear at 1x is what Kyant's bar ships.
- Haze's default light is `Center`, not a top lamp, and the lobe is on a 3D dot; k 16-24 equals about 8-10 in our 2D terms.
- Kyant's highlight width (0.5 dp, 1-2 px) was not given; it is the main number.
- The WWDC26 session 112 is the ASL variant of Platforms State of the Union (also 102); the quote holds.

## Untraced

- Apple's highlight width, alpha, IOR, displacement and the 2027 edge darkness: not published.
- Kyant's GitBook docs (kyant.gitbook.io/backdrop): not read.
- Haze `edgeRefractionGradient`'s full body and the final layer order in `RuntimeShaderGlassDelegate.kt` beyond the shader: partly read.
- The Fresnel lift magnitude (about 2.5% Regular, 9.5% Clear) assumes a lighting-gradient length near 2 (`GlassShaders.kt:979-986`); not traced to a number.
- Why clarity shows nothing on our Looks, and whether the rest-state mismatch is the 26 dp tail or the missing face grade: needs a pong render.
- Pixel density of pong for the Kyant stroke width (assumed 2.75).
