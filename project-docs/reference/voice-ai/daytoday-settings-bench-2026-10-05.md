# Day-to-day TAI settings on pong: context window and speculative decoding (2026-10-05)

Phone: pong (Nothing Phone 2, SM8475 / Adreno 730, 12 GB, Android 16), running the dev build installed
2026-10-04 (8411d209c). The phone was dozing and idle; 4.2–6.0 GB was free before each load. Script:
`/tmp/tiers-logs/daytoday_bench.py`, run from the host through `adb forward tcp:41237`, with a memory
watchdog (unload below 1.5 GB). It never tripped. Raw results are in `daytoday_e2b.jsonl` and
`daytoday_e4b.jsonl` in the same folder.

Each configuration is a fresh GPU load with `context_window` ∈ {1024, 2048, 4096} and
`speculative_decoding` off or on, then the two day-to-day tasks with the app's exact prompts at
`8411d209c`:

- **Tidy dictation**: the Polished system prompt from `VoicePolishRules`, three synthetic dictations of 20,
  93 and 146 words, `max_tokens` from `VoicePolishRules.maxTokens`, temperature 0, thinking off, streamed.
- **App categories**: `LauncherCategorySortPrompt.singleAppPrompt`, 12 apps with one clear category each,
  `max_tokens` 24, temperature 0.

## What the app sends today

From a code survey of the same commit:

| Task | Model | Window | Speculative decoding | Thinking |
|---|---|---|---|---|
| Tidy dictation | Cleanup setting, else E2B | not sent → Automatic = 4096 | not sent → global switch, off | off, per call |
| App categories | E4B if the phone meets 12 GB, else E2B | not sent → 4096 | not sent → off | **not sent** → follows the global switch |
| Wallpaper reader | E4B `-vision`, else E2B | **2048**, explicit | **on**, explicit | off |
| `/v1` clients | assistant pick | 4096 | off | off |

The window and speculative decoding are load-time settings. A per-call value counts only when that call
loads the model; with the model already resident, it is ignored.

## Results

### Load and memory

The drop is the free-memory figure before the load minus the lowest figure during the whole run.

| Model | Window | MTP off: load / drop | MTP on: load / drop |
|---|---|---|---|
| E2B | 1024 | 4.1 s / 0.91 GB | 6.1 s / 1.17 GB |
| E2B | 2048 | 3.9 s / 1.03 GB | 5.6 s / 1.26 GB |
| E2B | 4096 | 4.0 s / 0.89 GB | 5.0 s / 1.41 GB |
| E4B | 1024 | 18.4 s* / 2.38 GB | 12.6 s / 2.46 GB |
| E4B | 2048 | 10.6 s / 2.58 GB | 11.9 s / 2.76 GB |
| E4B | 4096 | 11.8 s / 2.84 GB | 13.4 s / 3.14 GB |

\* The first E4B load of the run included a cold file read.

### Tidy dictation (total time, streamed)

| Model | MTP | 20 words | 93 words | 146 words | TTFT |
|---|---|---|---|---|---|
| E2B | off | 1.8 s | 4.4 s | 6.9 s | 0.8–1.3 s |
| E2B | on | 1.6 s | 2.5 s | 3.7 s | 0.8–1.3 s |
| E4B | off | 3.6 s | 9.4 s | 14.7 s | 1.8–2.6 s |
| E4B | on | 3.0 s | 5.4 s | 7.6 s | 1.8–2.7 s |

Values are at a 4096 window. 1024 and 2048 were within ±0.4 s of these.

### App categories

| Model | Time per app (median) | Correct (12-app set) |
|---|---|---|
| E2B | 0.69–0.76 s | 11/12 at every window and MTP setting (Strava → games) |
| E4B | 1.32–1.42 s | 11/12 at every window and MTP setting (Strava → social) |

**Prompt variant.** The health line was changed to "health, fitness, sport, workouts and medical". On E2B
with an 18-app set (the 12 above, plus Nike Run Club, Calm, Steam, Discord, Duolingo and Termux:X11), the
score went from 15/18 to 17/18. Strava and Calm were fixed, nothing regressed, and Duolingo → social is
still wrong.

## Findings

1. **Speculative decoding roughly halves cleanup time** on both models (E2B 6.9 → 3.7 s, E4B 14.7 → 7.6 s
   for 146 words). It costs about 1–1.5 s more load and 0.1–0.5 GB more memory. Outputs were the same apart
   from small punctuation differences, about the size of the run-to-run differences that window changes
   alone produced on the GPU. It does nothing for categories, where the answer is one word.
2. **The window does not change speed** for these tasks. Memory rises with it on E4B, by about 0.45 GB from
   1024 to 4096; that matches the about 195 KB per token slope in the device-classes research. On E2B the
   difference is lost in noise. These tasks need at most about 1.8k tokens (cleanup's worst case) and about
   300 tokens (categories).
3. **E4B buys nothing for categories.** It has the same accuracy, takes twice the time and uses about 3 GB
   against about 1 GB. E2B is the better category model on 8–12 GB phones.
4. **Thinking is unguarded in categories.** It follows the global switch. With thinking on, the 24-token cap
   would cut the answer off.

## Applied on feat/tai-device-tiers

- Tidy dictation and app categories send `speculative_decoding: true`, as the wallpaper reader already does.
- Windows stay on Automatic.
- Categories send `thinking: false`, skip the user's TAI system prompt, and no longer reload an
  already-resident model.
- Tier 2's Automatic category model is E2B at every RAM size. Tier 3 keeps E4B, its resident assistant.
- The health line in the category prompt now includes fitness, sport and workouts.

## Not measured

- Real dictations: the 09-27 transcripts were not available, so these are synthetic, with fillers,
  self-corrections and a dictated path.
- The CPU path.
- Memory per window on E2B beyond noise.
- The wallpaper reader. Its settings (2048 window, MTP on) are from the 2026-10-04 Gallery round.

## Wallpaper reader (E4B / E2B vision), measured later the same day

The same set-up, with the reader's own request from `SceneReader.buildRequest`: one 768×432 JPEG, temperature
0, `max_tokens` 700, `load_class: momentary`, thinking off. The photo is a flat-art wallpaper from pong's
Pictures folder: a crescent moon, three hill bands and a few birds, with no water.

The first attempt was discarded because the developer was using the phone and the timings were inconsistent;
it is kept in `reader_results_polluted.jsonl`. The rerun waited for more than 4.5 GB free before each
configuration. One configuration (4096, MTP off) was unloaded mid-answer and was rerun on its own
(`reader_redo.jsonl`).

| Model | Window | MTP | Cold (load + answer) | Warm (answer only) | Memory drop | Answer |
|---|---|---|---|---|---|---|
| E4B vision | 2048 | on | **33.4 s** | **18.2 s** | 3.33 GB | 4 elements: moon, mountain, hills, birds |
| E4B vision | 2048 | off | 52.3 s | 40.4 s | 3.36 GB | 5 elements: moon, three hill bands, birds |
| E4B vision | 4096 | on | 40.9 s | 24.0 s | 3.12 GB | 5 elements, as above |
| E4B vision | 4096 | off | 64.7 s | 50.8 s | 3.84 GB | 5 elements, as above |
| E2B vision | 2048 | on | 51.2 s | 21.5 s | 1.99 GB | 7 elements: moon as sky, birds as other, hill boxes out of order |

### Findings

1. **The app's reader settings are the fastest measured.** A 2048 window with speculative decoding was 2.2×
   faster than without it (18 s against 40 s warm), and 2048 was about 25 % faster than 4096. Keep them.
2. **E4B reads better, and with speculative decoding it is also faster than E2B when warm** (18 s against
   21.5 s), because E2B writes more. Its only edge is memory (2.0 GB against 3.3 GB).
3. **Speculative decoding changed the answer**: the three hill bands were merged into one mountain plus one
   ground element. Both answers have no invented water and keep the moon and birds.
4. **Two prompt issues seen in every answer, worth a follow-up:**
   - The models put **every element** in `do_not_animate`, so check how the builder uses it.
   - The JSON comes back fenced and pretty-printed, with one number per line, which roughly doubles the
     output tokens. LiteRT-LM 0.17.1's JSON-schema `responseFormat` or a "one line, no fences" rule should
     cut the answer time (review, 2026-10-04).
   - No E4B answer listed the sky itself.
