# Director comparison on pong: Gemma 4 E2B vs E4B for the living-still scene reading

Measured 2026-10-04 on pong (Nothing Phone 2, 12 GB, Android 16, installed build 2adbaf512) with the
developer's go. Raw answers, scores, scripts and the marked images:
`~/Projects/termux-launcher/wall-alive/pong/gemma-cmp-2026-10-04/` (outside git).

## Method

- The six prototype wallpapers in `wall-alive/src`. For each, a scratch Robolectric test (native
  graphics, deleted after use) ran the phone's own code, `ColourClusters` → `LivingBitmaps.markedCopy`
  → `GemmaSceneReader.buildRequest`, so both models saw byte-identical requests: the photo and the
  numbered-regions copy at 768 px, JPEG 85, temperature 0, 400 tokens, window 2048.
- Requests went to pong's TAI API (`/v1/chat/completions`) over an ssh forward, one model at a time,
  unload between models, a MemAvailable watchdog at 1.5 GB (never fired).
- Scoring: the picked regions' pixels against the SegFormer group maps from `harness_inputs.py`
  (precision = mean SegFormer probability inside the picks; recall = share of SegFormer-confident
  pixels the picks cover). SegFormer finds nothing on the two flat graphic pieces, so those two
  photos only show behaviour, not accuracy.

## Speed and memory

| Pass | Backend | First call (with load) | Warm, median (range) | Lowest MemAvailable |
|---|---|---|---|---|
| E2B `-vision`, automatic | GPU | 30.6 s | 13.8 s (11.2–15.1) | 2.49 GB |
| E2B `-vision`, strict prompt | GPU | 21.8 s | 15.4 s (13.8–16.3) | 3.31 GB |
| E4B `-vision`, automatic | CPU | 42.3 s | 42.6 s (38.8–44.5) | 3.19 GB |
| E4B `-vision`, `accelerator: gpu` | refused | — | — | — |
| E4B `-vision`, automatic, history cleared | CPU | 42.8 s | 50.7 s (48.5–53.9) | 3.21 GB |

- The explicit GPU request was refused with `known_failed_accelerator: "This model/backend previously
  failed on gpu: Model load cancelled."`, the stale record the fix on dev `0bcfbb7a4` stops writing.
- With the history cleared (`tai runtime --clear-history`; backup kept next to the raw data) and
  5.7 GB free, E4B **still** went to the CPU: the memory budget's ratio estimate for E4B on the GPU
  (3/4 of the file + encoders + KV + 25% margin + the 1.5 GiB reserve) is about 5.7 GB. On a
  daily-driver phone E4B rarely gets the GPU. E2B on the GPU fits with 2.5 GB to spare.
- E4B's time is the CPU vision encoder plus prefill of two images; the load itself is ~1.3 s.

## Reading quality

| Photo | SegFormer finds | E2B | E2B strict | E4B |
|---|---|---|---|---|
| BMO island (`IMG_0302`) | sky 32%, water 5%, subject | water = 8 of 10 regions incl. the sky (prec 0.09); sky = one cloud; style none | water [2,3,4] prec 0.22 rec 0.98; sky [1] 0.93/0.97; no styles | water [2,3] 0.27/0.86; sky [1] 0.93/0.97; subject 0.59/0.70; **lake + clouds** |
| Pool photo (`IMG_2026…`) | water 35% | water = all 8 (0.38/1.0); subject = all 9 | same | water same; subject [9,10] 0.89/0.52; pool + glints |
| Lake illustration (`Picsart…`) | water 45%, foliage 39% | water [1,2] 0.93/0.69; foliage 8 regions 0.51/0.95; reflection | water = all 10; foliage 8 | water [1,2] 0.93/0.69; foliage 4 (0.48/0.37); invented sky; reflection |
| Garden (`deckdhq…`) | foliage 44% | water 5 regions (0.04); foliage 8 (0.45/0.91); pool | foliage [1,3,6]; sun | water 3 (0.08); foliage 7 (0.50/0.85); pool + sun + glints |
| Neon city (`nzvrpl…`) | nothing (flat art) | lights 4; trails 180° | lights [8]; trails | lights [8,9]; subject 8 regions; trails |
| Flat art (`philipsue…`) | nothing | lights = all 10 | lights = all 10 | lights = all 10; neon |

Patterns:

- **E4B reads the scene; E2B lists.** On the one photo where SegFormer, E2B and E4B all have ground,
  E4B picks the two water regions and the sky and chooses lake + clouds; E2B calls eight regions
  water, including the sky, and chooses no styles. E2B's precision on water is 0.04–0.09 on two photos.
- **E2B wrote the marks as strings** (`"3"`) in four of six answers. The app's parser rejected those
  answers whole; fixed on dev `484ae3c2b` (numeric strings count).
- **E2B does not follow constraints.** With a strict rule block (short lists, no region in two lists,
  plain numbers) it still listed all ten regions as water on the lake illustration while listing eight
  of them as foliage, and copied the literal `photo|illustration` out of the schema in four answers.
- Both over-list "lights" on flat graphic art (every region). The shape gates in `RegionMasks` are
  what keeps that from painting the whole picture; they stay.
- E4B is deterministic at temperature 0: the cleared-history pass reproduced its first answers exactly.

## Verdict

1. **E2B can run the step** on the GPU in 12–16 s warm, with memory to spare, where E4B takes 40–50 s
   on the CPU. **E2B cannot lead the reading.** Its region picks are too loose for the fill role in
   `RegionMasks` (Gemma's picks stand where SegFormer found nothing), and it skips the style choices
   the recipe needs.
2. **E4B stays the director when installed.** Its cost is the CPU, and the CPU is the budget's choice,
   not the history's: freeing the GPU for E4B means ~5.7 GB free, which pong does not have in use.
3. **E2B as the fallback director** when E4B is absent (8 GB phones), with two guards: drop a group
   pick that covers more than 60% of the picture (over-listing), and keep the parser fix. Prompt
   wording alone does not fix E2B.
4. The real speed fix for the step is the remote director (the bring-your-own-key provider), or a
   smaller dedicated vision model. The speed-aware load rule (spec §7.1, item 4) should let a user
   choose "fast (E2B)" over "careful (E4B)" rather than the app choosing silently.

## Side effects on pong

- Runtime history cleared (61 entries, including E4B's 11 measured GPU-load samples). The budget falls
  back to ratio estimates until loads are measured again. Backup: `pong-runtime-history-backup.json`
  next to the raw data.
- No install, no taps; the launcher stayed in the foreground throughout.
