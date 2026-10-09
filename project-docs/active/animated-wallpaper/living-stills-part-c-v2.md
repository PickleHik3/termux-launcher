# Living stills, Part C revision: elements and boxes instead of numbered colour regions

Status: approved for build 2026-10-04 (evening). Supersedes Part C of `living-stills.md` for the
director and the masks; Parts A, B and D stand. Evidence: `director-comparison-2026-10-04.md` and
`~/Projects/termux-launcher/wall-alive/pong/gemma-cmp-2026-10-04/` (Gallery answers, overlays).

## Why

The set-of-mark prompt asked Gemma to match meaning to numbered colour blobs, and that is where it
failed: on the samurai-in-the-meadow picture E4B called the pink grass "water", the recipe put lake
ripples on the field and glints across the frame, and the sky scroll's two-phase crossfade showed
every cloud twice. Asked in words for the scene's *elements*, each with a kind, a box, a depth and
a motion, the same model named nine real elements, invented no water, chose petals, and placed its
boxes where the things are. Speculative decoding made that answer 2.4× faster (47 s on Gallery's
GPU); thinking corrupted the JSON on both models and stays off.

So the director describes, the hands paint, and the recipe is read off the plan.

## The Scene Plan (v2), the one contract

Director output, validated and persisted verbatim in `recipe.json` as `plan`:

```
{"elements": [{"name": str, "kind": K, "box": [top, left, bottom, right] in 0..1000,
               "depth": near|middle|far, "motion": M}],
 "scene": {"time": dawn|day|dusk|night, "weather": clear|cloudy|misty|rain|snow,
           "light_direction": str, "particles": P, "mood": str, "do_not_animate": [names]}}
K = sky|clouds|water|falling_water|grass|trees|flowers|mountain|ground|building|lights|figure|animal|vehicle|other
M = none|still|drift|sway|wind_wave|ripple|flow|glow|flicker|twinkle
P = none|petals|leaves|dust|fireflies|snow|rain|embers|glints|sparks
```

The `note` field of the Gallery prompt is dropped (about 40% of the output tokens); the prompt text
is in `SceneReader.PROMPT`. Validation (`SceneReader.parse`): take the text between the first `{`
and the last `}`; `elements` required and non-empty; an element is **dropped** (not the answer) when
its kind or motion is outside the lists, its box has not four numbers, any value is outside
0..1000, or top ≥ bottom or left ≥ right; numbers written as strings count; an element whose box is
larger than the frame or whose values exceed 1000 is dropped (the thinking runs produced 4500 and
10000). The answer is **rejected** (null plan) when fewer than two elements survive or `scene` is
missing. `particles` outside P becomes `none`; `time`/`weather` outside the lists become null.
`do_not_animate` names are matched to elements case-insensitively.

## Round 1 (now): four parallel pieces, disjoint files

### A. `living/SceneReader` + `living/ScenePlan` (new; `GemmaSceneReader` stays until integration)

- `ScenePlan` (pure Java): `List<Element> elements`, `Scene scene`, `JSONObject raw`; `Element
  {name, kind, box(int[4]), depth, motion, still(boolean, from do_not_animate)}`;
  `toJson()`/`fromJson()` round trip; helpers `elementsOfKind(kind)`, `has(kind)`.
- `SceneReader`: `PROMPT` (the Gallery prompt without `note`), `buildRequest(photoDataUrl)`:
  OpenAI chat body, one user message `[text, image_url]` (one image, no marked copy),
  `temperature 0`, `max_tokens 700`, `stream false`, `context_window 2048`,
  `load_class "momentary"`, `speculative_decoding true`, `thinking false`. `modelId(installed)`:
  prefer `gemma-4-e4b-it-litert-lm` + `-vision`, else `gemma-4-e2b-it-litert-lm` + `-vision`, else
  null. `read(Chat chat, String photoUrl) → ScenePlan` (60 s timeout; null on any failure), reusing
  `GemmaSceneReader.Chat`. `parse(text)` as above.
- Tests: the four real Gallery answers in the evidence file (E4B round 1 and round 2 parse to 9 and
  8 elements; the two thinking runs are rejected or reduced correctly: E2B-thinking's axis-swapped
  boxes are kept only where valid, its figure box `[450,650,555,75]` is dropped; E4B-thinking is
  rejected for broken JSON); strings as numbers; `do_not_animate` matching; round trip.

### B. `living/ElementMasks` (new; `RegionMasks` stays as the no-director fallback)

Boxes become pixels without a new model (SAM 2.1 Tiny is round 3):

- Inputs: `ScenePlan`, `AnalysisMaps` (depth near=1, U-2-Net saliency, optional SegFormer groups),
  `ColourClusters.Result`, the small photo (`int[] argb`, w, h) and the mask size (w, h).
- Per element: `inside` = box at mask size, feathered 2 px. `depthBand` = pixels whose depth sits
  in the element's band, bands from the depth percentiles **inside the box** (near = top 40% of
  nearness, far = bottom 40%, middle = the rest, each widened by 0.1). `colour` = union of colour
  clusters whose pixels are ≥ 60% inside the box. Element mask = inside ∧ depthBand ∧ colour, then
  the guided filter (`GuidedFilter`, radius 8, eps 1e-3) against the photo.
- Overlaps: a pixel goes to the nearer element (near > middle > far); ties to the smaller box.
- Kinds → output planes (all float[] at mask size, 0..1): `sky` (sky ∪ clouds, then minus every
  non-sky element, then ∪ pixels above the horizon row with depth < 0.15), `water`, `fall`
  (falling_water), `sway` (trees ∪ flowers with motion sway), `wind` (grass ∪ ground with motion
  sway or wind_wave; the tip weighting from `RegionMasks` is reused by calling its package-private
  helper if it is static, else copied), `glow` (lights with glow/flicker/twinkle), `subject`
  (U-2-Net saliency ∩ (figure ∪ animal ∪ vehicle boxes if any, else the whole frame), capped as in
  `RegionMasks.MAX_SUBJECT`), `still` (every element in `do_not_animate` ∪ subject), `mist`
  (far depth as `RegionMasks` does), `particles` (as `RegionMasks` does, but zero inside `still`).
- SegFormer, when groups are present and a group's mean inside the box is > 0.6: intersect the
  element mask with that group (the cross-check); otherwise ignore SegFormer.
- A water element whose mean colour is warm (Lab b* > 15 and a* > 5, i.e. pink/orange/yellow) is
  dropped with a reason string in `Result.warnings` (the pink-meadow guard).
- Output `Result {w, h, sky, water, fall, sway, wind, glow, subject, still, mist, particles,
  Stats stats (reuse RegionMasks.Stats), List<String> warnings}`.
- Tests on synthetic maps: a box with a depth band picks the right pixels; overlap goes to the
  nearer; a warm "water" element is dropped; `still` covers do_not_animate; SegFormer cross-check
  intersects only when confident.

### C. Recipe v2 + rules from the plan (`LivingRecipe`, `RecipeRules`, `Manifest`)

- `LivingRecipe.version = 2`; readers accept 1 and 2 (v1 → new fields at defaults). New fields:
  `plan` (ScenePlan JSON or null), `wind {dirDeg, speed, amp}` (0 amp = off), `cloudMode
  scroll|warp|none`, `cloudWarp {amount, period}`, `particles.kind` extended with
  `petals|leaves|embers|sparks`, `stillProtected` (boolean, whether `still` is honoured),
  `director {model, accelerator, fallbackReason, ms}` (moves `gemmaModel`, `gemmaAccelerator`,
  `gemmaFallbackReason` under it; keep reading the old keys from v1).
- `RecipeRules.fromPlan(ScenePlan plan, ElementMasks.Stats stats) → LivingRecipe`:
  clouds with drift → `cloudMode warp`, amount 0.6, period 90 s; sky with no clouds element →
  `cloudMode none`; `time night` or sky luminance < 0.25 → stars; water element → `waterMode` by
  motion (ripple → lake, flow → noise, still water → reflection), params as today; falling_water →
  pour; trees/flowers sway → sway as today; grass/ground wind_wave or sway → `wind` with the
  direction from `light_direction`'s side (text contains "left" → 0°, "right" → 180°, else 20°),
  speed 1, amp 0.006; lights → glow by motion (glow → breathe, flicker → flicker, twinkle →
  twinkle = flicker 0.5); `scene.particles` → `particles.kind` (dust → dust, glints only when a
  water element exists, else none); mist only when `weather misty` or depth spread > 0.4, amount
  as today but 0.12 when the sky holds clouds; drift 0.35 when a figure is present, else 0.6;
  intensity 1.0; `stillProtected true`.
- `RecipeRules.make` (v1, no director) unchanged and still used by the fallback.
- `Manifest`: `maskD.png` (R wind, G still, B spare) written and required for v2; v1 manifests
  without it still load; `recipe.json` v2.
- Tests: v1 read, v2 round trip with plan, `fromPlan` on the two Gallery plans (samurai picture →
  cloud warp, wind on grass, petals, no water, figure still), the direction rule.

### D. Shader and binding (`chrome/wallpaper/LivingStill.java`, `LivingStillTextures`,
`WallpaperUniforms`, the AGSL device test)

- New child shader `uMaskD` (R wind, G still, B spare), decoded and bound like A–C; absent file →
  a 1×1 black bitmap so v1 manifests play.
- `uCloudMode` (0 none, 1 scroll = today's two-phase crossfade, 2 warp) and `uCloudWarp` (amount,
  period). Warp: inside the sky mask, sample the image at `uv + amount * 0.01 * curlNoise(uv * 2.5,
  t / period)`, one sample, no second phase, so a cloud is never drawn twice; the warp vector fades
  to zero at the sky mask edge (multiply by `smoothstep(0, 0.15, A.b)`) so the ridge never tears.
- `uWind` (dirDeg, speed, amp): inside `D.r`, displacement `amp * sin(dot(uv, dir) * 18 - t *
  speed * 1.7 + vnoise(uv * 6) * 2) * D.r * I`, along `dir`, weighted toward the top of the mask as
  sway is (reuse the tip weighting if it lives in the mask; if it lives in the shader, apply it).
- `still` (`D.g`): every displacement (drift, sway, wind, water, pour, bob) is multiplied by
  `(1 - D.g)` so protected pixels never move; glow and particles are also zeroed there.
- Particle kinds 7 petals (small pink-tinted discs that fall slowly and drift along the wind
  direction), 8 leaves (same, warm tint, more sideways), 9 embers (warm, rise), 10 sparks (bright,
  short-lived). Map in `recipeUniforms`.
- Rest pose rule stands: `uEnergy == 0` returns the photo exactly.
- `recipeUniforms` reads the new fields from `LivingRecipe` by the names in C (`wind.dirDeg`,
  `wind.speed`, `wind.amp`, `cloudMode`, `cloudWarp.amount`, `cloudWarp.period`,
  `particles.kind`, `stillProtected`); D does not edit `LivingRecipe`.
- `LivingStillAgslInstrumentationTest`: the program compiles with the new uniforms; a frame with
  `cloudMode warp` differs from rest only inside the sky mask; a `still` pixel equals rest under
  wind and drift.

## Round 2 (integration, after the four merge)

`LivingStillBuilder`: decode the photo once at 768 long side → `SceneReader.read` (when a Gemma is
installed) → on a plan: `ElementMasks.compute` + `RecipeRules.fromPlan`; on null: today's
`RegionMasks` + `RecipeRules.make` (rules-only fallback, SegFormer-led). `TaiGemmaChat` keeps the
unload-after and records `director.*`. Progress label "Reading the scene…". Delete
`GemmaSceneReader`, `LivingBitmaps.markedCopy`, and the colour-cluster marks (clusters themselves
stay, ElementMasks uses them). Picker: Motion stays one switch this round; per-element control
reads `plan.elements` in a later round.

## Round 3 (later)

SAM 2.1 Tiny (litert-community, Apache-2.0) as the box-to-mask step replacing B's heuristic where
installed; EfficientViT-Seg or none in place of SegFormer; the remote director through the
bring-your-own-key provider, which fills the same Scene Plan.

## Verification

JVM: the four packages' tests after each merge, then the full `testDebugUnitTest`. Waydroid: the
AGSL device test, the Bring to life flow with rules only (API 33, no Gemma). pong, on the
developer's cue: the samurai picture and the dark lake through the new director; read `recipe.json`
(`plan`, `director`, timings) and compare the masks with the boxes overlay in the evidence folder.

## As built, 2026-10-04 evening (rounds 1 and 2, dev 4bc4da486)

- A `SceneReader`/`ScenePlan`, B `ElementMasks`, C recipe v2 + `RecipeRules.fromPlan` + `Manifest.MASK_D`,
  D shader (`uMaskD`, cloud warp with a three-tap curl, wind, `still`, particle kinds 7–10) merged, then
  the builder wired: one 768 px decode, `SceneReader.read` with the installed model (E4B, else E2B),
  `ElementMasks` + `fromPlan` on a plan, `RegionMasks` + `make(stats)` on none; maskD written on both
  paths (black on the fallback); recipes are version 2. `GemmaSceneReader`, `markedCopy` and the marks
  prompt are gone; `Chat` lives in `SceneReader`.
- Seams fixed at integration: the two-element floor applies to model answers only (a persisted plan may
  hold one); recipes serialise to the text they were read from (no back-fill of the v1 keys from the
  director record); protected pixels get no particles; the guided-filter radius is capped at a quarter
  of the element box so small elements are not blurred below one half.
- Known gaps: no `bob` plane on the plan path (maskC red is zero there); `ElementMasks.warnings` are not
  persisted; the progress weights still name the stage `gemma`; the AGSL device test for the new
  uniforms has not run yet (Waydroid pass owed).
