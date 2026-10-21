# Dictation cleanup: when the model loads, and what it costs (2026-10-14)

Measured on pong (Nothing Phone 2, SM8475, Adreno 730, 12 GB, Android 16), release builds of
`pre`. Why the cleanup model now loads after a cold dictation instead of when the mic opens, and
what E4B and E2B cost in memory for one cleanup request. One open decision at the end.

## The freeze at mic open

A cold dictation (no model loaded) froze the whole screen for about 5 s, a few seconds after the
mic opened; the voice panel's waveform stopped. Build `+dev.5b1bef95e`, 20:49:

- Mic opened 20:49:19.6; `polish warm … loadMs=15688` logged at 20:49:35: the E4B GPU load ran
  from the mic opening for 15.7 s.
- `Choreographer: Skipped 611 frames!` at 20:49:32 on the launcher's main thread.
- gfxinfo over the window: **99th GPU percentile 4950 ms**. The launcher's own main and render
  threads never blocked longer than 0.5 s and 1.9 s (perfetto, the part it kept), so the frames
  waited on the GPU, which E4B's load in `:tai_runtime` held.
- MemAvailable fell 4.6 → 2.8 GB within 3 s of the mic opening.
- An earlier run had the warm-up refused (`insufficient_memory` at ~4.2 GB free) while Whisper
  Small loaded in the same process; that dictation ended "kept as heard".

## The fix, and how it measured

Merge `28d6116ee`: a cleanup model that is already resident is used from the mic opening as
before; a cold one loads at the session's end (`cleanup warm deferred` at the mic, then
`cleanup warm start: at the session's end`). Early passes are off for that one cold session.
Build `+dev.28d6116ee`, a 33 s KittenTTS recording played through pong's speaker:

| | Before | Cold run | Warm run |
|---|---|---|---|
| Waveform | froze ~5 s | moved throughout | moved throughout |
| 99th GPU percentile | 4950 ms | 9 ms | 8 ms |
| E4B load | at mic open | after the session (12 s) | none |
| Cleanup | at pauses | once, at the end | at pauses and the end |

A side effect seen in the cold run: as E4B loaded after the session with ~4.4 GB free,
lowmemorykiller killed about 20 cached apps within a second, Shizuku among them (restarted over
adb with its `libshizuku.so`).

## Memory for one cleanup request

One request shaped like a Polished cleanup pass (the real system turn, 80 words of `<earlier>`
context, ~250 words of transcript; 807 prompt tokens, `max_tokens` 766, `function: cleanup`, so
the GPU and 2048-token window of the plan), sent cold through `/v1/chat/completions`. MemAvailable
and the runtime's RSS sampled every 0.25 s over adb; per-process kgsl counters are not readable
without root, so the GPU share shows only in the MemAvailable drop.

| | E4B (`gemma-4-e4b-it-litert-lm`) | E2B (`gemma-4-e2b-it-litert-lm`) |
|---|---|---|
| MemAvailable, worst moment | 5.73 → 2.05 GB (−3.67 GB) | 5.88 → 4.43 GB (−1.44 GB) |
| Loaded, idle | about −2.9 GB | about −1.1 GB |
| `:tai_runtime` RSS peak | 1.78 GB | 0.92 GB |
| `dumpsys meminfo` Graphics after the request | 238 MB | 151 MB |
| Cold load + request | 26 s | 12 s |
| Output (139 / 136 tokens) | fillers gone, sentences rebuilt | the same, nearly word for word |

The status bar's ~75% memory with E4B loaded is right: about 2.8 GB available of 11.8 GB.
Steady use is fine at that level; the cost is the load itself, which needs ~3.7 GB free and makes
Android reclaim cached apps when the phone has less. Neither model turned "first … second …
third" into a numbered list on this sample, though Polished asks for one.

## What this means for the model choice

E2B is already the shipped cleanup default (settled 2026-09-27 from the pong benchmarks in
`voice-cleanup-benchmark-2026-09-27.md`); pong ran E4B because it was picked in Settings. These
numbers are the cost of that pick: E4B needs ~3.7 GB free to load and, at pong's usual 4–5 GB,
makes Android reclaim cached apps, Shizuku included; E2B loads in half the time at less than half
the memory with the same output on this sample.
