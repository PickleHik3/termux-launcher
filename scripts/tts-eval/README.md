# tts-eval: listen to three LiteRT TTS models before picking one

A PC-side prototype that synthesises the same five sentences with three on-device text-to-speech
models, so we can hear them side by side and choose a voice before integrating one into the app.
It also proves that a **GPL-free** phonemizer (no espeak-ng, no `phonemizer` package) drives all
three.

| model | repo | graphs used | voices | rate |
|---|---|---|---|---|
| KittenTTS nano 0.8 | `litert-community/kitten-tts-nano-0.8` | predictor, prosody, vocoder (fp32) | Bella, Jasper, Luna, Bruno, Rosie, Hugo, Kiki, Leo | 24 kHz |
| Inflect-Nano-v2 | `litert-community/Inflect-Nano-v2` | text encoder, decoder (fp32) | one male voice | 24 kHz |
| Matcha-TTS | `litert-community/Matcha-TTS` | text encoder, decoder (x10 Euler steps), vocoder (fp16, the only precision published) | LJSpeech (female) | 22.05 kHz |

Nothing here touches the app or Gradle. Models and WAVs never go into the repo: downloads are
cached in `~/.cache/termux-launcher/tts/`, and everything the script writes goes to
`scripts/tts-eval/out/` (git-ignored).

## Run

```sh
~/.cache/termux-launcher/venv/bin/python scripts/tts-eval/tts_eval.py
xdg-open scripts/tts-eval/out/samples.html
```

The venv already has what it needs (numpy, ai-edge-litert). The first run downloads the models
(see below); later runs start at once.

Options: `--models kitten,inflect,matcha`, `--voices Bella,Leo` (Kitten), `--sentences 1,5`,
`--threads 4`, `--seed 0` (Inflect/Matcha noise), `--steps 10` (Matcha Euler steps),
`--frontend google|extended` (see Phonemizer).

Output in `out/`:

- `<model>-<voice>-<n>.wav`: 40 Kitten files (8 voices x 5 sentences), 5 `inflect-male-*`,
  5 `matcha-ljspeech-*`.
- `report.md`: one table per model: G2P coverage (dictionary vs neural share, acronyms spelled,
  out-of-vocabulary fallbacks), G2P time, synthesis time, audio length, real-time factor, and time
  to first audio when synthesising sentence by sentence; then the IPA for every sentence with the
  neural-fallback words listed.
- `samples.html`: one `<audio>` per WAV next to its sentence text.

## Downloads (automatic, `fetch.py`)

| file | size |
|---|---:|
| kitten-tts-nano-0.8/kitten_predictor.tflite | 33.8 MB |
| kitten-tts-nano-0.8/kitten_prosody.tflite | 3.3 MB |
| kitten-tts-nano-0.8/kitten_vocoder.tflite | 26.4 MB |
| kitten-tts-nano-0.8/voices.npz | 3.3 MB |
| Inflect-Nano-v2/inflect_text_encoder.tflite | 3.5 MB |
| Inflect-Nano-v2/inflect_decoder.tflite | 12.6 MB |
| Matcha-TTS/matcha_textenc_fp16.tflite | 14.8 MB |
| Matcha-TTS/matcha_decoder_fp16.tflite | 22.6 MB |
| Matcha-TTS/matcha_vocoder_fp16.tflite | 29.0 MB |
| Matcha-TTS/emb.bin, config.json | 0.1 MB |
| Matcha-TTS/g2p_dict.txt.gz (phonemizer dictionary) | 1.8 MB |
| Matcha-TTS/dp_g2p_matcha_fp16.tflite (neural phonemizer) | 25.8 MB |
| Matcha-TTS/g2p_meta.json | 2 KB |
| **total** | **~177 MB** |

`python scripts/tts-eval/fetch.py --list` prints the live sizes from the Hugging Face API;
`fetch.py` alone downloads without synthesising. Sizes are checked against the API, so a
truncated download is fetched again.

## Phonemizer (`g2p.py`)

A Python port of Google's litert-samples `KittenG2P.kt` / `MatchaG2P.kt`
(`samples/litert/text_to_speech{,_streaming}/`, Apache-2.0), which is the same logic as that repo's
`text_to_speech_streaming/python/kitten_tts.py`:

1. **Dictionary**: 275k words to espeak-style IPA (`g2p_dict.txt.gz`, from OpenPhonemizer,
   Clear BSD).
2. **Neural fallback** for words not in the dictionary: DeepPhonemizer (`dp_g2p_matcha_fp16.tflite`,
   MIT). Letters are repeated 3x, wrapped in `<en_us>`...`<end>`, padded to 96, argmax, repeats
   collapsed.
3. **Normalisation**: ALL-CAPS runs of 2+ letters are spelled (`BBC` -> bee bee see), numbers are
   read as words (`2026` -> two thousand twenty six).
4. The IPA maps onto the 178-symbol StyleTTS2/keithito table. All three repos use the same
   table (only the pad glyph at index 0 differs); `check_symbol_tables` asserts this at start-up.

Punctuation follows each sample: Kitten gets it as its own token (`wˈɜːld ,`), Matcha and Inflect
get it attached to the word (`wˈɜːld,`, which is also what Inflect's espeak frontend produced in
training).

`--frontend extended` adds a normalisation pass that Google's samples do **not** have: dates
(`27 September 2026` -> the twenty seventh of September twenty twenty six), years after
in/since/by/until, ordinals (`3rd`), and 4+ letter acronyms that the dictionary knows as a word
(`NASA`, `JSON`) read as words. The default `google` mirrors the samples exactly, so the report
shows what the app would get from a straight port.

## Host glue per model (`engines.py`)

- **Kitten** (repo `say.py`, litert-samples `kitten_tts.py`): text split on `[.!?]+` with every
  chunk ending in `,` (the pip package's `chunk_text`); ids `[0] + ids + [0]`; style row
  `voices[v][min(len(chunk), 399)]`; speed x the voice's prior (0.8, Hugo 0.9); predictor ->
  repeat rows by durations -> prosody -> vocoder (inverse STFT is inside the graph); the pip
  package's 5000-sample tail trim. `reset_all_variables()` runs before every invoke because the
  fused LSTMs keep state.
- **Inflect** (repo `say.py`): split after `.!?;:`; blank between phonemes; durations
  `ceil(exp(logw) / speed)`; `z = m + randn * exp(logs) * 0.667` with seed `seed + sentence
  index`; decoder; 5 ms edge fades; punctuation-dependent pauses between sentences.
- **Matcha** (repo README Python, litert-samples `MatchaSynthesizer.kt`): host embedding lookup
  from `emb.bin`; blanks interspersed; fixed 256 tokens / 512 frames with masks; durations
  `ceil(exp(logw)) * 0.95`; length regulator; 10 Euler steps of the decoder from seeded noise;
  `mel * 2.116 - 5.537`; vocoder. The sample runs one 512-frame window (about 5.9 s), so the
  script synthesises sentence by sentence and splits a sentence at a comma if it still does not
  fit; the report flags any split or truncation.

## Caveats

- Timings are for this PC's CPU (XNNPACK); phone numbers will be several times slower. Matcha's
  vocoder always runs the full 512-frame window, so its cost does not shrink for short sentences.
- The Google frontend drops characters it has no rule for (hyphens, `%`, `&`, brackets); the
  report lists them per sentence under "ignored input chars".
- Kitten's chunker consumes `?`/`!` and ends every chunk with `,` (an upstream quirk the model was
  tuned against), so questions may not rise in pitch.
