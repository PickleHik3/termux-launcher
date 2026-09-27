# Voice cleanup benchmark on pong (2026-09-27)

Phone: pong (Nothing A065, SM8475, Android 16, 12 GB). Input: five real dictation sessions from
2026-09-26 (12–111 s, 12–178 words), transcribed by Parakeet TDT v3. Each model got the same 5
transcripts with 3 system prompts, streamed, temperature 0, thinking left unset.

Script: `cleanup_bench.py` (run in Termux on the phone; memory watchdog unloads below 1.5 GB).

## Results

| Model | Backend | Load | TTFT (median) | 67 s / 106 w | 111 s / 178 w | Words kept (worst) | Verdict |
|---|---|---|---|---|---|---|---|
| Gemma 4 E2B | GPU | 5.5 s | 0.53–0.61 s | 5.9–6.5 s | 10.1–10.4 s | 0.91–0.97 | Use it |
| Gemma 4 E4B | GPU | 15.8 s | 2.0–2.3 s | 17.5–19.1 s | 27.7–30.9 s | 0.95–0.97 | Slightly better punctuation, 3x slower |
| Qwen3.5 2B VL int8 | CPU (GPU load fails) | 16.9 s | 2.2–6.4 s | 7.4–10.2 s | 21–44 s | 0.00 | Summarises, then loops ("The The The…") |
| Qwen3.5 4B int8 | CPU (GPU load fails) | 31.6 s | 5.3–9.4 s | 18.5–34 s | 39–82 s | 0.35 | Drops text, keeps fillers |

E2B fixed recogniser errors from context ("DMs" → "dims", "dogs" → "docks", "home school screen"
→ "home screen"), removed fillers and kept the speaker's wording. The three prompts differed little;
P2 ("light": punctuation, fillers and abandoned restarts only; keep every other word in order) kept
the most words and the speaker's sentence starts.

## Findings that change the product

1. `thinking: false` turned thinking ON for LiteRT-LM models: `enable_thinking` reached the chat
   template as the string "false", which Jinja reads as true. The shipped voice cleanup therefore
   thought before every phrase (E2B: 3.7 s and no answer for a three-word reply, against a 0.37 s
   first token with the key left out). Fixed in d93e583a.
2. Parakeet's mel front end ran at 0.6x real time (41 s of mel for 67 s of audio), ten times the
   encoder, because of a dense filterbank multiply. Fixed in a100fcc4 (sparse bands, per-frame helper).
3. Both Qwen3.5 LiteRT builds fail to load on the GPU and run on the CPU.
4. A stale "failed on GPU" record from before a model finished downloading blocks automatic loads
   ("Download or import this model before loading it"); an explicit `--gpu` load works.

## Recommendation

Gemma 4 E2B with the P2 prompt, one pass at the end of the dictation session (spec D5). A 111 s
dictation cleans up in about 10 s at ~17 tokens/s; stream the cleaned text into the panel so the
first words show within ~0.6 s. Keep E4B as an opt-in "careful" choice. Do not offer the Qwen3.5
LiteRT builds for cleanup.
