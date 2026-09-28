# On-device AI

On-device AI is a local AI service built into Termux Launcher. It lets apps and command-line tools
talk to on-device AI models through a localhost API. Your prompts and model output stay on the
device unless you deliberately use a network service or expose the API to your local network.

The short version:

- The AI runs on your device. Native model runtime work is isolated in the Android `:tai_runtime`
  process so launcher UI survives native runtime crashes.
- It exposes an OpenAI-compatible API for tools such as `aichat`, Codex, OpenCode, or Crush.
- It supports LiteRT-LM and MNN model backends.
- It does not run GGUF/raw weight files.
- It does not bundle model files inside the APK. You download or import models yourself.
- The API is protected by a bearer token stored on your device.
- The command-line tool is `tai`.

## What It Is For

On-device AI is mainly a model host. It gives other tools a local AI backend.

Good uses:

- chatting with a local model from a CLI tool
- connecting OpenAI-compatible tools to an on-device endpoint
- keeping a selected model warm in the background
- switching between supported local models by changing the request model name
- letting Termux Launcher manage local model downloads, imports, loading, and unloading

It is not meant to replace every AI shell tool. Tools like `aichat`, coding assistants, or tmux
helpers can provide the user interface. On-device AI provides the local model runtime they can
call. On-device AI does not control Android apps or the device: the same local server has one
separate app-launch route used by `launcherctl launch`; agent, MCP, and other device-control
routes are not present.

## Where It Lives

Open:

```text
Settings → On-device AI
```

It is a row in the **Launcher** category, right after **Display**. From there you reach:

- **Model centre** — installed models, the catalog, downloads, and per-model settings
- **Hugging Face token** — a token for gated downloads
- **Context window** — the global or per-model window override
- **Endpoint & access** — the local base URL, bearer token, and port

## Quick start

1. Open **Settings → On-device AI → Model centre**.
2. Open the model catalog and choose a model that fits your device memory.
3. Read and accept the model provider's terms when asked, then download the model.
4. Tap the installed model and choose **Load**. You can also leave OpenAI auto-load enabled so the
   first API request loads it after safety checks pass.
5. Use `tai status` in Termux to confirm On-device AI is ready.

Models are not bundled in the APK. A download can be several gigabytes, so check the size and
available storage first.

## Model centre

Model centre lists every installed model plus a **Worth a download** catalog. Each row shows a
quiet backend pill (LiteRT-LM or MNN). Models download from Hugging Face; gated ones ask for a
Hugging Face token first, and every download shows the provider's license to accept.

The built-in catalog is deliberately short: the two Gemma 4 chat models, the speech-to-text models
[voice input](Voice_Input.md) uses, and the voice model for [text to speech](Text_To_Speech.md).
Any other LiteRT-LM or MNN model (Qwen, DeepSeek, FunctionGemma, an embedding model) still runs
when you add it by Hugging Face link or import the file; it is just not listed.

| Model | Best for | Approximate download | Suggested device RAM | Notes |
| --- | --- | ---: | ---: | --- |
| Gemma 4 E2B IT | General chat, images, audio, and tools | 2.4 GB | 8 GB+ | Recommended general model, Apache-2.0, not gated |
| Gemma 4 E4B IT | Better coding and reasoning | 3.7 GB | 12 GB+ | Larger and slower, Apache-2.0, not gated |
| Whisper ACFT Base / Base (English) | Voice input, multilingual or English only | 97 MB | 6 GB+ | 5 s or 10 s window |
| Whisper ACFT Small / Small (English) | Voice input, better accuracy | 273 MB | 8 GB+ | 5 s or 10 s window |
| Parakeet TDT 0.6B v3 | Voice input in 25 European languages | 586 MB | 8 GB+ | Language detected automatically |
| KittenTTS Nano 0.8 | Reading text aloud, in English | 90 MB | 4 GB+ | Four voices; under Speech > Voice output |

Only one chat/generation model is active at a time: loading a model replaces the currently loaded
chat model. Speech models are separate: they load on demand beside the chat model and never
replace it.

Downloads run two at a time by default (the `tai_download_parallel` setting allows 1 to 3); the
rest wait in line. A download can be paused and continues from the bytes it already has; one
interrupted by the app closing or the network dropping is shown as paused and continues by itself
on an unmetered (Wi-Fi) network, or waits for a tap on mobile data. Cancelling deletes the partial
file. Before a download starts, the free space on the model directory must cover the remaining
bytes plus a reserve of 500 MB or 5% of the volume.

## Understanding model capabilities

Every installed model reports what the local endpoint can actually accept. Run:

```sh
tai models
```

For the complete machine-readable list, run:

```sh
endpoint="$(cat ~/.launcherctl/endpoint)"
token="$(cat ~/.launcherctl/token)"
curl -sS -H "Authorization: Bearer $token" "$endpoint/v1/models" | jq .
```

Common capability names are:

| Capability | Meaning |
| --- | --- |
| `text_chat` | Normal text prompts and replies |
| `image_input` | Images can be included in a prompt |
| `audio_input` | Audio can be included in a prompt |
| `tool_use` | The model can return structured function/tool calls |
| `text_embeddings` | Converts text into embedding vectors instead of chat text |
| `code` | Tuned or intended for programming tasks |
| `reasoning` | Intended for multi-step reasoning |
| `multilingual` | Intended for more than one language |
| `llm_thinking` | Supports the backend's thinking mode |
| `speculative_decoding` | A LiteRT model with the runtime flag, or an MNN package built around an EAGLE-3 draft head (a `config.json` with `speculative_type` and its `eagle*.mnn` files) |
| `mobile_actions` | Tuned to choose from compatible Android action tools |

`_capabilities` in `/v1/models` is the important field for apps. `_source_capabilities` describes
upstream claims, while `_endpoint_capabilities` describes what this APK can currently provide.

A model with `speculative_decoding` shows a "Speculative decoding" switch in its parameters
screen. For MNN, EAGLE-3 is off by default (auto and explicit off both fall back to plain
decoding): measured on a phone it was slower than plain decoding on both CPU and GPU, and its text
differed slightly, so it is not worth turning on by default. The switch lets a developer turn it
on anyway to try it on an EAGLE-3 package; turning it on has no effect on a package that never
shipped a draft head. LiteRT is unaffected by this default: a LiteRT model with the runtime flag
keeps its own auto/on/off behaviour.

### Images and audio

Multimodal LiteRT models are advertised on the API as up to three ids sharing one downloaded file.
By default (the split exposure) the bare id is text-only chat, and only the modalities beyond that
get a suffix:

- `model-id` for text (the default, canonical id)
- `model-id-vision` for image input
- `model-id-audio` for audio input

Choose the id matching the input you intend to send. Only one mode is loaded at a time, which
reduces memory use. The advanced **Endpoint exposure** setting changes this: **Combined** serves
every modality from the bare id alone, with no suffixed ids, at a higher memory cost; **Both**
keeps that combined bare id and adds the splits, with the text-only variant as `model-id-text`.

### FunctionGemma and phone actions

FunctionGemma is an optional catalog model. It can return structured tool calls when a client
sends compatible tool definitions, but it does not execute those calls and it does not run beside
another model. Executing any returned tool call is the client's responsibility; On-device AI
itself does not perform Android actions.

## Connect an AI app

On-device AI stores its current local address and secret token in:

```text
~/.launcherctl/endpoint
~/.launcherctl/token
```

The address normally looks like `http://127.0.0.1:54298`. OpenAI-compatible clients usually need
`/v1` appended to it.

Read the token and endpoint at call time rather than copying the secret into configuration files:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Use `/v1/responses` for current Codex-compatible Responses clients. Use `/v1/chat/completions` for
OpenAI-compatible chat clients. Ollama-compatible clients use the same base address without adding
`/v1`.

The full HTTP route tables, request/response shapes, rate limits, and error codes are in
[LauncherCtl API](LauncherCtl_API.md); this page covers the settings screens and the `tai` CLI.

## Using with aichat

Install and configure `aichat` as an OpenAI-compatible client.

Use:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Then choose one of the supported model IDs, for example:

```text
gemma-4-e2b-it-litert-lm
```

When the first request is sent, On-device AI will load the model if it is installed and not
already loaded.

## Useful `tai` commands

```sh
tai status
tai runtime
tai models
tai downloads
tai download-pause MODEL_ID
tai download-resume MODEL_ID
tai download-now MODEL_ID
tai download-cancel MODEL_ID
tai preflight MODEL_ID
tai load MODEL_ID
tai load MODEL_ID --cpu
tai load MODEL_ID --gpu
tai keep-warm MODEL_ID --minutes 30
tai cancel
tai unload
tai benchmark MODEL_ID --preset quick
tai benchmark --results
tai transcribe recording.wav
tai speak "text to read aloud"
tai speak --stop
tai doctor
```

`tai` manages models; it is not an interactive chat program. Add `--json` when you need raw output
for a script. `tai transcribe` is described in
[Voice input](Voice_Input.md#from-the-terminal-tai-transcribe) and `tai speak` in
[Text to speech](Text_To_Speech.md#tai-speak).

## Importing or downloading models

The APK does not include model files. This keeps the APK smaller and avoids bundling third-party
model licenses.

You can download supported catalog models from the settings page, or use the CLI:

```sh
tai download gemma-4-e2b-it-litert-lm <model-url> --accept-terms
```

You can import an existing local package:

- LiteRT-LM `.litertlm` or `.task` files
- MNN model directories containing `config.json` and all required sidecar files
- LiteRT EmbeddingGemma `.tflite` packages with their required tokenizer files

```sh
tai import /absolute/path/to/model.litertlm MyModelName
```

You can also import from Settings → On-device AI → Model centre → **Add a model**, with Android's
file picker; the selected file is copied into app-private model storage. Paste a Hugging Face repo
URL there instead to download and register a model straight from Hugging Face. Capabilities are
guessed from known model names; tick or untick them, then import and verify.

For catalog models, use the official model ID so OpenAI-compatible tools can request it directly.

MNN models should be installed from the catalog/download flow so On-device AI can fetch
`config.json` and required sidecar files. GGUF, safetensors, PyTorch, ONNX, and other raw weight
files are rejected by import/load paths because this APK does not include a GGUF/llama.cpp
backend.

For gated Hugging Face models, first accept the agreement on the model's Hugging Face page. Then
create a read token and save it under **Settings → On-device AI → Hugging Face token**. A classic
**Read** token, or a fine-grained token with `Contents: Read`, is enough. The token is used only
for Hugging Face downloads.

## Supported Model Names

Use these model IDs exactly:

```text
gemma-4-e2b-it-litert-lm
gemma-4-e4b-it-litert-lm
functiongemma-270m-mobile-actions-litert-lm
qwen2.5-coder-1.5b-instruct-mnn
```

`gemma-4-e2b-it-litert-lm` is the fast default assistant model.

`gemma-4-e4b-it-litert-lm` is the larger assistant model.

`functiongemma-270m-mobile-actions-litert-lm` is a smaller model intended for tool/function call
output. It is CPU-only. On-device AI returns tool calls for the client to handle; it does not
execute Android actions or shell commands itself.

`qwen2.5-coder-1.5b-instruct-mnn` is the default installed MNN code model.

## How Model Loading Works

You do not always need to manually load a model first, but auto-load is guarded.

When an OpenAI-compatible client sends a generation request, On-device AI checks the requested
model:

- If the model is already loaded, it uses it.
- If the model is installed but not loaded, it loads it automatically only when compatibility
  preflight is clean.
- If the request uses another assistant model, it switches the assistant slot to that model.
- If the model is unknown, not installed, low on memory, missing native libraries, or risky for
  automatic GPU load, the request fails with a clear error such as `model_not_loaded`,
  `device_not_supported`, or a preflight failure code.

The loaded model is kept warm for the configured timeout, then unloaded when idle.

You can also load manually:

```sh
tai load gemma-4-e2b-it-litert-lm
```

and unload manually:

```sh
tai unload
```

## One Active Model

On-device AI keeps one chat/generation model active at a time. Loading a LiteRT-LM or MNN model
unloads the previous chat model. FunctionGemma is a normal CPU-only catalog model and also
replaces the active model when loaded.

Automatic loads try the GPU first and use the CPU after a GPU load has failed on your phone. You
can pick one explicitly with `tai load <model> --gpu` or `--cpu`.

## Memory

A model is loaded to fit the memory your phone has free at that moment, so it never crowds out the
home screen and the apps you are using.

- The context window is the part that grows with free memory. It shrinks by halves until the load
  fits, down to 4096 tokens. A setting or request for a larger window is an upper limit, not a
  promise. Override it globally or per model under **Settings → On-device AI → Context window**.
- If the GPU load does not fit even at 4096 tokens, the CPU load is used instead: slower, but it
  needs about half the memory.
- If neither fits, the load is refused with a short message. Close some apps and try again.
- About 1.5 GB, or 15% of your RAM if that is more, is always left free.
- If your phone runs low on memory while a model is loaded, On-device AI unloads it. The next
  request loads it again.
- `/v1/models` reports the context window a load would actually get right now.
- `tai --json runtime` shows the window the loaded model was given.

## Benchmark

**Settings → On-device AI → Model centre → Benchmark** measures how a model actually runs on this
phone: Home → Choose models → Check (battery, heat, screen) → Run, with a cool-down between models
and a Result screen per entry. An installed chat model also has **Benchmark** in its own overflow
menu, which preselects it. Once you have run one, installed chat rows show a speed pill with their
best ranked tok/s. The same thing runs from the shell as `tai benchmark`.

`tai benchmark` measures how well a model runs on this phone. LiteRT-LM and MNN models are timed
the same way: every token goes through the runtime's generation callback and is stamped as it
arrives, so the numbers are comparable across backends.

### What it measures

For each model and processor (its own leaderboard entry):

| Phase | What happens | Figure |
| --- | --- | --- |
| Load | Whatever is loaded is unloaded first; the model loads cold through the normal preflight and memory budget | Load time; memory used (MemAvailable before minus its lowest point) |
| Warm-up | One short reply, discarded | — |
| Reading | A fixed passage of about 512 tokens, then "summarise in one line" | Prompt tokens per second (prompt tokens over the wait for the first token) |
| First word | A short chat message | Time to first token |
| Writing | A fixed long-output prompt, 128 tokens, greedy sampling | Decode tokens per second (from the first token to the last) |
| Sustained | 90 s of writing (thorough only) | Speed at the end against the start |
| Check | Three questions with known answers (`17 + 25`, a fixed JSON object, repeat a word) | Pass/fail; a model that fails is **broken**, whatever its speed |

Reading, first word and writing keep the median, minimum and maximum over their runs. A phase that
takes three times longer than expected is stopped and the record is marked `timeout`.

### Presets

| Preset | Reading | First word | Writing | Sustained | Processors | Time per model |
| --- | --- | --- | --- | --- | --- | --- |
| `quick` | 1 | 1 | 1 | – | The one an automatic load would pick | about 1 min |
| `standard` (default) | 3 | 3 | 3 | – | CPU, and GPU where the model supports it | about 2 min per processor |
| `thorough` | 3 | 3 | 5 | 90 s | CPU, and GPU where supported | about 4 min per processor |

`--cpu` or `--gpu` benches exactly that processor; if the model cannot load on it, the entry is
skipped with the reason. `--eagle` switches the draft model on for MNN builds that ship one (a
separate entry).

```sh
tai benchmark                                   # the default assistant model, standard preset
tai benchmark gemma-4-e2b qwen3-vl-2b-instruct-mnn --preset quick
tai benchmark qwen3-vl-2b-instruct-mnn --thorough --cpu
tai benchmark --results                         # the leaderboard
tai benchmark --clear gemma-4-e2b               # forget one model's results (no model: all)
tai benchmark --native gemma-4-e2b --gpu        # LiteRT-LM's own benchmark(), for comparison with Google AI Edge Gallery
```

The terminal shows one line per phase as it finishes. Verdicts: **smooth** at 15 tok/s or more,
**usable** between 7 and 15, **slow** below 7, **broken** when the check fails. While a benchmark
runs, chat and load requests are refused with `benchmark_running`; `tai cancel` stops it and keeps
the phases that finished. Speech input and output are not blocked, but using them mid-run will
disturb the numbers.

Every entry's record is appended to `files/tai/benchmarks.json` in the app's private storage (the
last 20 per model, backend, processor and draft-model combination). The leaderboard ranks the
latest complete record of each entry by median writing speed, ties to the faster first word.
Broken entries are listed but not ranked, and results from a different bench version are kept but
never ranked against the current one. The HTTP routes and their payloads are documented in
[LauncherCtl API](LauncherCtl_API.md).

## Status Bar Indicator

Any status bar that can run a shell command on a timer can show whether an AI model is loaded —
the launcher's own bar included. These are the glyphs it reports with.

Loaded:

```text
󱜙
```

Unloaded:

```text
󱚡
```

When a model is loaded, the widget can also show the remaining keep-warm or idle timer. After the
model is unloaded, the unloaded icon disappears after a short timeout.

## Security Notes

The AI endpoint is bound to localhost:

```text
127.0.0.1
```

It is meant for local apps and tools on the same device.

Requests must include the bearer token (`Authorization: Bearer <token>` or `X-Api-Key: <token>`)
from:

```sh
~/.launcherctl/token
```

A **Require API token** setting (default on) under **Settings → On-device AI → Endpoint & access**
lets you turn token checks off for localhost so local CLI clients need no real key. `GET /` and
`OPTIONS` never require auth, and **LAN bind mode always requires the token** regardless of the
toggle; LAN mode also rebinds to localhost and rotates the token 12 hours after it is enabled.

Treat this token like an API key. If it is exposed, recreate it from:

```text
Settings → On-device AI → Endpoint & access → Recreate token
```

After recreating the token, update any CLI tools that stored the old key.

## Troubleshooting

### The CLI tool cannot connect

Check that Termux Launcher has been opened at least once after install:

```sh
cat ~/.launcherctl/endpoint
cat ~/.launcherctl/token
```

If the files are missing, open the app again.

### The model is not found

Check installed models:

```sh
tai models
```

Make sure the request uses the exact model ID.

### The model does not load

Check runtime state:

```sh
tai runtime
```

Try CPU mode if GPU loading fails:

```sh
tai load gemma-4-e2b-it-litert-lm --cpu
```

### The API key does not work

Read the current token:

```sh
cat ~/.launcherctl/token
```

If needed, recreate it in Settings and update your CLI tool.

Common problems, start with `tai doctor`, `tai status`, `tai runtime`:

- **Model not listed:** finish downloading or importing it, then check `tai models`.
- **Model not loaded:** enable OpenAI auto-load or run `tai load MODEL_ID`.
- **Not enough memory:** close other apps, choose CPU, or use a smaller model.
- **GPU load crashes:** retry with `tai load MODEL_ID --cpu`.
- **401 Unauthorized:** refresh your client with the current value from `~/.launcherctl/token`, or
  turn off **Require API token** for localhost use.
- **Connection refused:** reopen Termux Launcher, check `~/.launcherctl/endpoint`, and run
  `tai status`.
- **Tools are ignored:** confirm the selected model advertises `tool_use`.
- **Image or audio rejected:** use the model's `-vision` or `-audio` ID from `/v1/models`.

## More Details

For the technical reference, see:

- [On-device AI backends](On_Device_AI_Backends.md) — runtime internals, capability matrix, per-modality behaviour
- [LauncherCtl API](LauncherCtl_API.md) — full HTTP route tables
