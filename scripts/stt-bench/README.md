# STT bench

A repeatable, shell-driven speech-to-text bench (issue #213, umbrella #212). It runs the Whisper and
Parakeet engines over a fixed clip set and prints one grep-able line per run, clip and runtime.
Debug builds only: the code is `app/src/debug/java/com/termux/ai/SttBench*.java`; no audio is in the APK.

## Clips (fixed)

`fetch-clips.sh` downloads the originals, checks them against `sources.sha256`, converts to 16 kHz mono
PCM16 WAV with bit-exact ffmpeg flags, and checks the result against `clips.sha256`.

| Clip | Length | What | Source | Licence |
|---|---|---|---|---|
| `stt-3s-jfk.wav` | 3.0 s | first 3 s of the JFK clip (dictation length; cut mid-sentence on purpose) | whisper.cpp `samples/jfk.wav` at commit `4da27c41cebadb3cbbcd2563e9c7cbc8d84bdcad` | JFK inaugural address, US government work, public domain |
| `stt-11s-jfk.wav` | 11.0 s | the whole JFK clip | same file | same |
| `stt-45s-bush.wav` | 45.0 s | first 45 s of the weekly radio address of 1 Nov 2008 (over Whisper's 30 s window; Parakeet's `pieces()` chunking) | Wikimedia Commons `George_W._Bush's_weekly_radio_address_(November_1,_2008).oga` (`upload.wikimedia.org/wikipedia/commons/2/22/...`) | US government work, public domain |

The Wikimedia file is pinned by name and sha256 (`sources.sha256`), not by revision: if Commons ever
replaces it the script fails at the source check rather than benching different audio. The converted
hashes assume ffmpeg's resampler as of ffmpeg n9.0.2; on another build the script exits 2 with a warning
(sources matched, converted bytes differ) and the bench still runs.

No LibriVox clip: an archive.org URL could not be pinned reliably.

## Get the clips and models onto the phone

```
scripts/stt-bench/fetch-clips.sh clips
adb push clips/stt-*.wav /sdcard/Download/stt-bench/        # or scp/rsync into Termux home
```

The app user must be able to read them (Termux home, or shared storage once Termux has storage access).
Models are the two already imported ones; each `.tflite` has its `tokenizer.json` next to it. Find them:
`find /data/data/com.termux/files -name '*.tflite' -path '*tai*'` from Termux.

## Run (from a Termux shell, charger unplugged, screen on, phone cool)

The bench does not need the APK installed: copy a debug APK of the build under test to the phone and
run it from the file. Three things the first run on pong (Android 16) showed:

- the APK file must be read-only (`chmod 444`), or ART refuses it as a "writable dex file";
- `System.loadLibrary` under `app_process` ignores `LD_LIBRARY_PATH`: unzip `lib/arm64-v8a/` from the
  APK and pass it as `-Djava.library.path`;
- Termux's default shell may be fish; run this from `bash`.

```
chmod 444 bench.apk
unzip -oq bench.apk 'lib/arm64-v8a/*' -d lib
LIB=$PWD/lib/lib/arm64-v8a
CLASSPATH=$PWD/bench.apk app_process -Djava.library.path=$LIB /system/bin com.termux.ai.SttBench \
  --clips ~/stt-bench/clips --whisper /path/to/whisper.tflite --parakeet /path/to/parakeet.tflite \
  --path 1.4.2-shim | tee stt-bench.txt
```

Flags: `--runs 5` timed runs per clip, `--warmup 1` excluded runs per clip, `--path LABEL` the
accelerator/path name printed on every line (a label you choose: it is not detected; later tickets pass
`cpu-compiledmodel`, `gpu`, ...), `--language en` for Whisper (default: the app's auto-detect),
`--interval-ms 200` battery sampling period. Either model flag may be left out.

`--accel interp|cpu|gpu|auto` picks where Whisper and Parakeet run: `interp` (the default) is the stock
Interpreter with XNNPACK, the path before CompiledModel; `cpu` and `gpu` are LiteRT's CompiledModel
on that accelerator; `auto` tries the GPU and falls back to the CPU as the app does (the bench
remembers nothing between runs). `STTBENCH_LOAD`
prints the accelerator actually used (`accel=`), why it is not the GPU when the GPU was asked for
(`fallback=`), whether logits are read as one row per token (`sliceReads=true`) or as the whole
tensor, and how many inputs needed a copy of their own (`copiedBindings=`). Keep `--path` matching:

```
COMMON="--clips $HOME/stt-bench/clips --whisper /path/to/whisper.tflite --runs 5 --warmup 1 --language en"
CLASSPATH=$PWD/bench.apk app_process -Djava.library.path=$LIB /system/bin com.termux.ai.SttBench $COMMON --accel interp --path interp | tee bench-interp.txt
CLASSPATH=$PWD/bench.apk app_process -Djava.library.path=$LIB /system/bin com.termux.ai.SttBench $COMMON --accel cpu --path cm-cpu | tee bench-cm-cpu.txt
LD_LIBRARY_PATH=$LIB CLASSPATH=$PWD/bench.apk app_process -Djava.library.path=$LIB /system/bin com.termux.ai.SttBench $COMMON --accel gpu --path cm-gpu | tee bench-cm-gpu.txt
```

The GPU run also sets `LD_LIBRARY_PATH`: LiteRT opens its GPU accelerator
(`libLiteRtClGlAccelerator.so`) with `dlopen` by name, which `-Djava.library.path` does not reach.
If `STTBENCH_LOAD` still says `accel=cpu fallback=...` with the GPU asked for, the fallback text says
why.

## Output

All lines start `STTBENCH`; `grep '^STTBENCH_SUMMARY'` gives one line per runtime and path.

```
STTBENCH_ENV litert=1.4.2 litertlm=0.19.0 path=1.4.2-shim device="Google Pixel 9" abi=arm64-v8a cores=8 runs=5 warmup=1 battery=BatteryManager charging=false clips=3
STTBENCH_LOAD runtime=whisper path=1.4.2-shim model=acft_whisper_base.en_10s_drq.tflite ok=true loadMs=412 cpuS=0.38 error=""
STTBENCH_RUN runtime=whisper path=1.4.2-shim clip=stt-11s-jfk.wav audioS=11.0 run=warmup wallMs=... cpuS=... mA=-640.2 charging=false text="..."
STTBENCH_RUN ... run=1 ...
STTBENCH_CLIP runtime=whisper path=1.4.2-shim clip=stt-11s-jfk.wav ok=true audioS=11.0 wallMedMs=... wallMinMs=... cpuMedS=... cpuMinS=... mA=-640.2 charging=false sameText=true text="..."
STTBENCH_SUMMARY runtime=whisper path=1.4.2-shim model=... clips=3 failed=0 audioS=59.0 runs=5 wallMedMs=... wallMinMs=... cpuMedS=... cpuMinS=... rtfMed=0.123 mA=-640.2 charging=false
STTBENCH_DONE
```

(Values above are placeholders, not measurements.)

- Wall: `System.nanoTime` around one `transcribe` call (model already loaded). CPU: `/proc/self/stat`
  utime+stime delta over the same call. The bench runs the engines in its own `app_process`, so
  `/proc/self` is the process doing the inference (the app's `:tai_runtime` runs the identical engine
  classes). Summary wall/CPU are sums over clips of the per-clip median and min; `rtfMed` is summed
  median wall over summed audio.
- Current: mean of `BATTERY_PROPERTY_CURRENT_NOW` samples across the run, in mA, in the phone's own sign
  (usually negative on discharge). `battery=` says whether it came from `BatteryManager` or the
  fuel-gauge sysfs file (`none`: `mA=NA`). `charging=true` anywhere means the current is not usable.
- `sameText=false` means the transcript changed between runs of one clip.
- A model that fails to load prints `ok=false` with the error and the bench goes on to the next one.

## Aux bench (TTS, embeddings, VAD on the Interpreter)

`AuxBench` times the features that still run on LiteRT's `Interpreter`: KittenTTS with its phonemizer,
the Interpreter embedder (`LiteRtEmbeddingRuntime`) and Silero VAD, and writes their outputs so two builds
(for example LiteRT 1.4.2 against 2.3.2) can be compared. Code: `app/src/debug/java/com/termux/ai/AuxBench*.java`.
It runs like `SttBench` (same APK copy, same `chmod 444`, same unzipped `lib/`), and its lines start `AUXBENCH`.

```
chmod 444 bench.apk
unzip -oq bench.apk 'lib/arm64-v8a/*' -d lib
LIB=$PWD/lib/lib/arm64-v8a
mkdir -p out-1.4.2
CLASSPATH=$PWD/bench.apk app_process -Djava.library.path=$LIB /system/bin com.termux.ai.AuxBench \
  --tts /data/data/com.termux/files/tai/models/kittentts-nano-0.8 \
  --vad asset --clips ~/stt-bench/clips \
  --out out-1.4.2 --path 1.4.2 | tee aux-bench-1.4.2.txt
```

Each section runs only when its flag is given, so a section can be run alone:

| Section | Flags | Notes |
|---|---|---|
| TTS | `--tts DIR` | `DIR` holds `kitten_predictor.tflite` and its sidecars. Three fixed sentences (6, 25, about 85 words), default voice and speed. |
| Embeddings | `--embed FILE.tflite` | Eight short sentences and one of about 400 tokens. See the note below. |
| VAD | `--vad FILE\|asset --clips DIR` | `asset` reads `assets/vad/silero_vad_v5.tflite` from the first class-path entry (the APK); or pass the extracted tflite. Runs the whole `stt-45s-bush.wav` from `--clips`. |

Common flags: `--out DIR` (writes the outputs below), `--runs 5`, `--warmup 1` (excluded runs per item),
`--path LABEL`, `--interval-ms 200`. There is no `--threads`: the runtimes pick their own, and the `ENV`
line prints what they ask for (`threadsTts`/`threadsEmbed` = min(4, cores), `threadsVad` = 1). Which
engine ran those threads (the 1.4.2 JNI delegate or the stock 2.x Interpreter) is the build under test.

**Embeddings.** `LiteRtEmbeddingRuntime` is the path for raw `.tflite` EmbeddingGemma 300M (windows
`embeddinggemma-300M_seq*_mixed-precision.tflite` with `sentencepiece.model` next to them). The
`embeddinggemma-2-*.litertlm` models do not use the Interpreter: `LiteRtLmEmbeddingRuntime` serves them
through LiteRT-LM's `EmbeddingEngine`, so they are not part of this bench.

Output files in `--out`:

- `tts-1.f32`, `tts-2.f32`, `tts-3.f32`: raw little-endian float32 PCM at 24 kHz (short, medium, long). The
  `RUN` lines carry the sample count and a `sha256` of those bytes.
- `emb.f32`: float32 vector count, float32 dimension, then the vectors back to back.
- `vad.f32`: one speech probability per 512-sample chunk (about 31 per second).

Lines: `AUXBENCH_ENV`, then per section `AUXBENCH_LOAD` (model load; for embeddings the first call),
`AUXBENCH_RUN` (per run; TTS has `g2pMs` and `synthMs` from the runtime's own timers), `AUXBENCH_ITEM`
(median and min over runs), `AUXBENCH_SUMMARY`, and `AUXBENCH_DONE`. CPU and battery are measured as for `SttBench`.

Compare two output directories (host JVM with the compiled classes, or on the phone under `app_process`):

```
CLASSPATH=$PWD/bench.apk app_process /system/bin com.termux.ai.AuxBenchCompare out-1.4.2 out-2.3.2
```

It prints `AUXCOMPARE_TTS` (max abs diff, SNR in dB, A as the reference), `AUXCOMPARE_EMB` (cosine per vector)
with `AUXCOMPARE_EMB_SUMMARY minCosine`, and `AUXCOMPARE_VAD` (max abs diff, `flipsAt0.5`).
