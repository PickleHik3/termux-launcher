# Living stills and the one "Wallpaper & style" entry

Status: approved for build 2026-10-04 (developer answers below). Device checks owed on pong (API 36).

## Why

The developer is unhappy with the preset AGSL backgrounds. They want the user's own photo, picked
on the wallpaper page, to come alive: drift with depth, plants sway, water moves, lights breathe.
The browser prototype (`~/Projects/termux-launcher/wall-alive/`, outside the repo) showed the look
on six real wallpapers and the developer liked it ("everything else looks good", water reworked
once). That prototype used a real depth model but hand-drawn masks and hand-picked recipes. This
spec replaces the hand work with models that run once per photo, on the phone.

At the same time the three ways into styling (Settings "Look", "Appearance", "Layout", and the
corner tab's Appearance / Layout / Wallpaper buttons) fold into one entry, "Wallpaper & style",
which opens the wallpaper page. The page already links Look and Layout.

## Decisions (developer, 2026-10-04)

- One entry, named **Wallpaper & style**. It opens the wallpaper picker page.
- The old "Look" settings page stays as it is, reached by a **More settings** link at the bottom of
  the wallpaper page. Settings search keeps finding its rows.
- Scope this round: **everything**: depth + masks + rule recipe + an optional Gemma 4 E4B recipe step.
- Scene segmentation: **SegFormer-B0 (ADE20K)** catalogued for testing. Its weights are NVIDIA
  non-commercial; decide before release whether to keep it or convert EfficientViT-Seg (Apache-2.0).
- Subject cut-out: **U-2-Net** is in this round.
- Depth: catalogue **both** Depth Anything 3 Small and Depth Anything V2 Small; pick the default after
  pong tests.
- The page's Motion control exists on **both** cards (Home and Lock), default on.
- A new glyph ("bring to life", drawn by Codex, `ic_symbol_bring_to_life`) starts the analysis; while
  it runs the button becomes a progress bar; once done for that photo it becomes the Motion switch.
  If a needed model is missing, the user is asked to open the model centre on that model.

## What each piece gives (the developer asked)

| Piece | Gives | Source on the phone |
|---|---|---|
| Depth map | drift and parallax, focus blur, depth mist, particles behind the subject | DA3 or DA2 |
| Region masks: which pixels are water, sky, plants, falling water, lights, subject | water, cloud drift, sway, pouring water, bobbing, glow | SegFormer labels + U-2-Net subject + colour clusters (+ Gemma's picks) |
| Recipe: which effects, which style | lake vs pool vs reflection water, trail direction, particle kind, strength | rules from the masks; Gemma 4 E4B when installed |

Gemma cannot draw pixel masks; it reads the scene and picks numbered colour regions
("set-of-mark" prompting), and chooses styles. SegFormer gives pixels but no style.

## Measured 2026-10-04 (desktop VM CPU, LiteRT Python Interpreter, 4 threads, XNNPACK)

| Model | File | Input (NCHW f32, /255 then ImageNet mean/std) | Output | Time |
|---|---|---|---|---|
| DA3 Small | `da3_small_gpu_fp16.tflite` 55,035,456 B | `[1,3,896,504]` portrait | `[1,1,896,504]` **distance: far = high, invert** | 1.9 s |
| DA2 Small int8 | `tflite/depth_anything_v2_small_wi8_afp32.tflite` 27,733,680 B | `[1,3,518,686]` landscape, stretched, bicubic | `[1,518,686]` disparity: near = high | 1.3 s |
| SegFormer-B0 ADE20K | `segformer_b0_ade20k.tflite` 15,533,492 B | `[1,3,512,512]` stretched, bilinear | `[1,128,128,150]` **NHWC logits** | 0.24 s |
| U-2-Net | `u2net_fp16.tflite` 88,230,272 B | `[1,3,320,320]`; divide by the image's max value, then ImageNet | `[1,1,320,320]` saliency 0..1 | 1.05 s |

Normalise depth by the 1st/99th percentile to 0..1 and store near = 1.

Catalogue facts (all Apache-2.0 except SegFormer; none gated):

| id | repo @ revision | file | sha256 |
|---|---|---|---|
| `depth-anything-3-small` | `litert-community/Depth-Anything-3-Small` @ `5cd25d936e3fde2edef68f53b4123401454ac9c8` | `da3_small_gpu_fp16.tflite` | `e170369a72ba1bba7486a4d2de555639fccd0595a9bb5b5349f7733ed4aebd1f` |
| `depth-anything-v2-small` | `litert-community/depth-anything-v2-small` @ `178427e448dbf4da93b1e7b1b2abc103ad329bd6` | `tflite/depth_anything_v2_small_wi8_afp32.tflite` | `f74509422e4a9270a354b249a9193abdd4903354be63701262238a7f4b869611` |
| `segformer-b0-ade20k` | `sollaholla/segformer_b0_ade20k` @ `1ba929c2bc51ea2bdcc3a7374a504b0e3e5196af` (licence `nvidia-source-code`, non-commercial) | `segformer_b0_ade20k.tflite` | `59849627a23803db4471eeb61996c77bcfce92dd7347aa8fb9308050941c8cc9` |
| `u2net` | `litert-community/U-2-Net` @ `defc203955a46f3cc760deb8c76d96f10331a46c` | `u2net_fp16.tflite` | `dd338f190a538ca3de9792b20b7617038cd56f8e440f38ff25da5554f32b9df2` |

The app's LiteRT is 1.4.2: classic `Interpreter` only, no `CompiledModel`/GPU. All four run on CPU
through `TaiXnnpackDelegate`. DA3's ~0.9 s GPU figure needs LiteRT 2.x; out of scope.

What the outputs looked like on the six test wallpapers: DA3 is sharper than DA2 (buildings, willow,
bike). U-2-Net finds every subject cleanly (BMO, floats, willow, fountain stream + Vaporeon, bike +
trails, city figure). SegFormer finds sky/water/trees on photos and soft illustrations, and nothing
useful on flat graphic art (Akira, neon city): that gap is what colour clusters and Gemma cover.

ADE20K class ids used (0-based, `nvidia/segformer-b0-finetuned-ade-512-512` `id2label`):
- water 21, sea 26, river 60, swimming pool 109, lake 128; falling water: waterfall 113, fountain 104
- sky 2
- foliage: tree 4, grass 9, plant 17, flower 66, palm 72
- lights: lamp 36, light 82, chandelier 85, streetlight 87, sconce 134; signs: signboard 43, poster 100, screen 130
- subject-like: person 12, animal 126, car 20, boat 76, bicycle 127, minibike 116

## Part A: one entry point

1. Settings root (`res/xml/root_preferences.xml`, `SettingsActivity`): replace rows `appearance`,
   `appearance_editor`, `layout_editor` with one row `wallpaper_style`, title "Wallpaper & style",
   summary "Wallpaper, look, layout and icon pack". It starts `TermuxActivity` with a new extra
   `EXTRA_WALLPAPER_STYLE`, handled next to `handleSurfaceEditorIntent` (`TermuxActivity`),
   calling `openWallpaperPicker()`. Settings search: index the Look page rows
   (`termux_style_preferences.xml`) under the new row so a hit still opens the Look page.
2. Corner tab (`PaneControlsView` users): `TerminalPaneController` lone-pane actions,
   `WidgetPaneFrame` resting actions, `X11PaneFrame` actions: replace Appearance + Layout + Wallpaper
   with one action, label "Wallpaper & style", glyph `CornerTabGlyphs.WALLPAPER` (add
   `CornerTabGlyphs.WALLPAPER_STYLE` as an alias with a doc comment; drop `APPEARANCE`/`LAYOUT` only if
   nothing else uses them). It calls `openWallpaperPicker()`.
3. Terminal long-press menu (`showTerminalActionSheet`): replace "Set Wallpaper", "Appearance" and
   "Layout" with one "Wallpaper & style" item.
4. Help: update strings that say "hold a corner and tap Appearance/Layout/Wallpaper" and the
   `appearance_editor` / `layout_editor` topics to "open Wallpaper & style, then Look / Layout".
5. Keep the launcherctl tools (`appearance.set_wallpaper`, `appearance.surface_editor`,
   `app.open_look_and_feel`) as they are: agents use them.
6. Tests: `RootPreferencesSearchIndexTest`, `TerminalPaneCornerTabTapTest`, `WidgetPaneFrameTapTest`,
   `X11PaneFrameTapTest`, `X11PaneFrameControlsTest`, `CornerTabGlyphsTest`, help tests.

The "More settings" link lives on the picker page and belongs to Part D.

## Part B: vision models and the analysis job (TAI)

1. Capabilities in `TaiModelSpec`: `depth_estimation`, `scene_segmentation`, `subject_segmentation`;
   early returns in `endpointCapabilitiesFor` like STT/TTS; helpers `isVisionTool()`.
2. Catalogue (`TaiModelCatalog`): the four entries above (backend/format like Kitten: LiteRT `.tflite`,
   architectures `depth-anything-3`, `depth-anything-v2`, `segformer-ade20k`, `u2net`), a
   `visionEntries()` list, excluded from `chatEntries()`, display tags. SegFormer's licence string must
   say non-commercial. Update `TaiModelCatalogTest` counts.
3. Model centre: a new **Vision** segment listing the four with one line on what each does for
   wallpapers; installed rows get a `vision` flag (no load/default actions). Add
   `TaiModelCentreFragment.openForModel(activity, modelId)` that opens the Vision segment, scrolls to
   the row and highlights it (honour `EXTRA_SCROLL_TO_KEY`).
4. `TaiResidency.Kind.VISION`; the job loads one graph at a time and closes it before the next, so peak
   memory is one graph. Admission via `TaiLoadBudget.planFixed` with factor ~2x file size until measured;
   `TaiLoadMeter` + `TaiRuntimeHistory.recordMeasuredLoad` like the other LiteRT runtimes.
5. `WallpaperVisionRuntime` in the `:tai_runtime` process: `Interpreter` + `TaiXnnpackDelegate`,
   threads `min(4, cores)`, `setCancellable(true)`. Consider lifting `KittenTtsRuntime.Graph` into a
   shared helper rather than a fourth copy. Pre/post-processing exactly as the table above.
6. API: `TaiManager.analyzeWallpaper(request, progress)` where request = `{imagePath, outDir,
   depthModelId}`; streamed like image generation (`OP_VISION_ANALYZE` / `OP_VISION_CANCEL` in
   `TaiRuntimeIpc`, its own lane in `TaiRuntimeService`). Progress events: `stage` (`depth`, `scene`,
   `subject`) + percent. Cancel stops between graphs and through `Interpreter.setCancelled`.
7. Outputs written to `outDir` (all RGB/gray PNG, never alpha; Android, Pillow and browsers premultiply
   alpha and wipe colour where alpha is 0):
   - `depth.png`: 8-bit gray, near = 255, at the model's resolution;
   - `scene.png` + `scene.json`: for each class group (water, falling water, sky, foliage, lights,
     signs, subject-like) a probability map (softmax over the 150 logits, summed per group), 128x128,
     packed 3 groups per RGB PNG (`scene0.png`, `scene1.png`, `scene2.png`); `scene.json` names the order;
   - `subject.png`: 8-bit saliency, 320x320;
   - `analysis.json`: models used, timings, sizes.
8. `TaiVisionModels` (app side, like `TaiTtsModels`): `missing(ctx)` lists which of {a depth model,
   SegFormer, U-2-Net} are not installed; `depthModel(ctx)` returns the chosen depth id (pref
   `wallpaper_depth_model`, default DA3 if installed, else DA2).
9. Tests: pre/post-processing (resize, NCHW, normalisation, DA3 inversion, percentile normalisation,
   NHWC softmax grouping), catalogue, residency, IPC dispatch.

## Part C: from raw maps to masks and a recipe (pure Java, plus one Gemma call)

New package `com.termux.app.chrome.wallpaper.living`. Pure Java where possible, so JVM tests cover it.

1. **Colour clusters**: k-means (k = 10) in Lab on the photo at ~270 px wide, with a small position
   term; label each cluster's largest connected component with a number (the "marks").
2. **Masks** at the stored mask size (photo width / 4): for each group, upsample the SegFormer group
   probability and refine it with the photo as a guide (guided filter, radius ~8, eps ~1e-3) so edges
   follow the art; subject = U-2-Net saliency (guided-filtered) ∩ (near depth or subject-like labels);
   lights = bright, saturated small blobs (top luminance percentile) inside lights/signs labels, or
   anywhere in a dark scene.
3. **Gemma step** (optional; only when `gemma-4-e4b-it-litert-lm` is installed and admission allows):
   send the photo (768 px) and the same photo with cluster outlines and numbers, through
   `TaiManager.openAiChatCompletions` with the `-vision` model id, asking for strict JSON:
   `{style: photo|illustration|flat_graphic, regions: {water:[n], falling_water:[n], sky:[n], foliage:[n],
   lights:[n], subject:[n]}, water_style: lake|pool|reflection|stream|none, sky_motion: clouds|stars|none,
   light_style: neon|lamps|trails|sun|none, trail_angle_deg: number|null, particles:
   none|glints|fireflies|rain|snow|dust|debris, intensity: 0.5..1.5}`. Validate strictly; any error
   or timeout (60 s) falls back to rules. Where Gemma names regions, its picks (cluster pixels,
   guided-filtered) replace that group's mask; groups Gemma leaves empty keep the SegFormer mask only
   if its mean probability is > 0.6 and coverage > 3%.
4. **Rules recipe** (no Gemma, or to fill what Gemma left out): water > 3% → water on; pool if water
   covers > 45% with flat depth over it, reflection if the scene is dark and the water sits under a
   strong horizon, else lake; falling water → pour; sky > 5% → clouds (day) or stars (sky luminance
   < 0.25); foliage > 3% → sway (tips weighted by height within each component); lights → glow
   (flicker if they sit in signs); subject inside water → bob; depth spread > 0.4 → depth mist; drift
   always on.
5. **Manifest** in `filesDir/wallpaper/living/<sha256 of the photo, 16 hex>/`: `image.png` (copy of the
   photo as applied), `depth.png`, `maskA.png` (water, sway, sky), `maskB.png` (falling water, subject,
   glow), `maskC.png` (bob, mist, particles), `recipe.json` (versioned; every effect with its params, water
   mode, particle kind, trail direction, fog colour, `gemma: true|false`, model ids, timings).
6. API: `LivingStillBuilder.build(ctx, File photo, File analysisDir, Progress) → Manifest` (runs the
   Gemma step itself when available; reports a `recipe` stage), and `LivingStills.find(ctx, File photo)`.
7. Tests: k-means determinism (seeded), guided filter on a synthetic edge, rules on synthetic stats,
   Gemma JSON validation (good, bad, partial), manifest round trip.

## Part D: playback and the page

1. `LivingStill implements AnimatedWallpaper`, id `living:<hash>`, resolved by `AnimatedWallpapers.byId`
   from the manifest and kept out of `ALL`. The shader is `MomentAgsl.HEAD` + `uniform shader uImage,
   uDepth, uMaskA, uMaskB, uMaskC` + recipe uniforms + a `scene()` ported from the prototype
   (`wall-alive/page.html`, shader `FS`): depth drift, sway, the three water modes (noise, cartoon lake,
   pool surface), sky flow loop, pour, glow (breathe/flicker/trails), mist, particles. AGSL has no
   `textureLod`: focus blur is a 6–8 tap disc on `uImage` whose radius grows with `uFocus` and
   distance. Rest pose (energy 0, phase 0) must return the image exactly.
2. Binding in `WallpaperUniforms`: decode once per process (image at frame cover size, maps at their
   size), `BitmapShader` with linear filtering, `setInputShader` (pattern: `GlassRefraction`).
   Release with the renderer.
3. Apply: a living still applies the photo itself as the system still (rest = image) via
   `ManagedWallpaper.apply`, then records `managed_wallpaper_animated = living:<hash>` (lock:
   `animated:living:<hash>`); `keepHomeStill` gets the same branch.
4. Focus: `GeneratedWallpaperHost.Environment.focus()` = terminal sheet open or keyboard up; into
   `WallpaperDirector.Frame` and the `uFocus` uniform, eased.
5. Motion prefs: Home gets `wallpaper_home_motion` (default true); Lock keeps `wallpaper_lock_motion`.
6. Page (`WallpaperPickerPage`, `wallpaper_picker_page.xml`), on both cards, only for a photo choice,
   API 34+:
   - no manifest for this photo → the **bring to life** button (`ic_symbol_bring_to_life`, label
     "Bring to life");
   - tap → if `TaiVisionModels.missing` is not empty: a dialog naming what is missing and its size,
     with "Open model centre" (→ `openForModel` on the first missing one) and "Not now";
     else the button morphs into a determinate `LinearProgressIndicator` with the stage
     ("Reading depth…", "Finding sky and water…", "Finding the subject…", "Asking Gemma…",
     "Building…") and a cancel; weights: depth 40, scene 10, subject 20, Gemma 20 (if used), build 10;
   - done → the button becomes the Motion switch (on); the card preview plays the living still;
     a small "Read again" action re-runs the analysis;
   - the job survives the page closing (runs on a background executor owned by the activity; the
     page re-attaches to its progress on reopen).
   - **More settings** link at the bottom opens the old Look page (`TermuxStylePreferencesFragment`).
7. Tests: `AnimatedWallpapersTest` contract for a living still, rest-pose test, `WallpaperSlotPlanTest`
   for `living:` ids, picker page tests for the button/progress/switch states (360/411 dp).

## Order of work

Round 1, in parallel (disjoint files): A (menus), B (TAI vision), C (living package).
Round 2: D (renderer + page), after A/B/C merge, since it calls B and C and edits the page.

## Verification

- Build + full unit tests on this machine after each round.
- Waydroid (API 33): menus, Settings row, corner tab, model centre Vision segment and download,
  analysis job end to end (CPU), the page's button/progress/missing-model dialog. Playback needs API 34.
- pong (API 36), with the developer's go-ahead: playback on Home and Lock, Motion on both, focus blur,
  render budget (`RenderBudget` p90, gfxinfo), analysis timings DA3 vs DA2, Gemma step on vs off.
- Prototype reference outputs for the six test wallpapers: `wall-alive/build/<id>/lrt_*.png`.
