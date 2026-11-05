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
