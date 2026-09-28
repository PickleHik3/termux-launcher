# On-device AI Backends

On-device AI supports two local LLM runners behind the same authenticated localhost API:

- LiteRT-LM for Gemma 4 and MobileActions `.litertlm` packages.
- MNN-LLM for downloaded MNN `config.json` packages.

On-device AI does not include a GGUF/llama.cpp backend. GGUF, safetensors, PyTorch, ONNX, and
other raw weight files are not listed by `/v1/models` and are rejected by import/load paths.

Both runners are exposed through OpenAI-compatible endpoints so CLI tools can use the device as a
local model backend:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Implemented OpenAI-style endpoints:

```text
GET  /v1/models
POST /v1/chat/completions
POST /v1/responses
POST /v1/completions
POST /v1/embeddings
POST /v1/tokenize
POST /v1/audio/transcriptions
POST /v1/audio/speech
```

Native Ollama compatibility is exposed from the same authenticated base URL through
`/api/version`, `/api/tags`, `/api/show`, `/api/chat`, `/api/generate`, `/api/ps`, and `/api/embed`.
Ollama responses use NDJSON streaming while OpenAI responses use SSE. Both adapters route through
the same `TaiChatRequest`, runtime options, capability checks, model load lifecycle, and
cancellation path. See [LauncherCtl API](LauncherCtl_API.md) for the full route tables, request
and response shapes, rate limits, and error codes; this page is the runtime and backend reference.

## Capability matrix

| | LiteRT-LM | MNN-LLM |
| --- | --- | --- |
| Model format | `.litertlm` / `.task` packages | Directory with `config.json` + sidecars |
| Chat input | Yes | Yes |
| Image input | Yes (`-vision` id) | No — `capability_not_supported` |
| Audio input | Yes (`-audio` id) | No — `capability_not_supported` |
| Tool calling | Native, through `tool_use` | Prompt-based (`_tool_mode: "prompt_fallback"`) |
| Thinking / reasoning traces | Yes (`llm_thinking`) | No |
| Embeddings | Yes (`.tflite` EmbeddingGemma) | Yes, where the model advertises `text_embeddings` |
| Speculative decoding | Runtime flag, own auto/on/off | EAGLE-3 draft head, off by default |
| Runs on | CPU or GPU | CPU or GPU (OpenCL) |

Speech (`/v1/audio/transcriptions`, `/v1/audio/speech`) runs outside these two runners. It
transcribes with the speech model voice input uses (Whisper ACFT or Parakeet, both on LiteRT) and
speaks with the voice model (KittenTTS nano 0.8). Both run on the CPU in the `:tai_runtime`
process, load on demand beside the chat model, and are never queued behind a chat generation.
Their request fields, the `tai transcribe` and `tai speak` commands and Read aloud are described in
[Voice input](Voice_Input.md) and [Text to speech](Text_To_Speech.md).

## Embeddings and tokenizer internals

`/v1/embeddings` accepts a string or an array of strings (at most `_endpoint_max_batch`, currently
64) and returns float (or, with `encoding_format:"base64"`, base64) vectors in OpenAI's `embedding`
list shape, each carrying `tokens` and `truncated`. Use it only with models whose `/v1/models`
`_capabilities` include `text_embeddings`. LiteRT EmbeddingGemma `.tflite` packages need
`sentencepiece.model` beside the model file; new downloads fetch that sidecar automatically. Older
installs missing the sidecar return `embedding_tokenizer_missing`.

`input_type: "query"` or `"document"` (default) selects EmbeddingGemma's trained task prefix,
applied on the server and counted inside the model's window; an optional `title` folds into the
document prefix. While a chat generation is running, embeddings run throttled (background thread
priority) so they never slow the live reply, and a load that does not fit in memory returns `503`
with `Retry-After` and `code: "embedding_memory"` rather than the chat path's `409`. `/v1/tokenize`
(`{model, input}` → `{tokens: n}`) uses the same tokenizer with no prefix, for splitting text on
real token counts.

## Model IDs

Use the exact IDs returned by:

```sh
tai models
```

Common catalog IDs:

```text
gemma-4-e2b-it-litert-lm
gemma-4-e4b-it-litert-lm
functiongemma-270m-mobile-actions-litert-lm
qwen2.5-coder-1.5b-instruct-mnn
```

The Gemma 4 E2B and E4B catalog entries are not gated; both are Apache-2.0 and download without a
Hugging Face token.

`GET /v1/models` includes On-device AI metadata on each OpenAI model item:

```json
{
  "id": "gemma-4-e2b-it-litert-lm",
  "object": "model",
  "_backend": "litert-lm",
  "_capabilities": ["text_chat", "image_input", "audio_input", "tool_use"],
  "_endpoint_capabilities": ["text_chat", "image_input", "audio_input", "tool_use"],
  "_source_capabilities": ["text_chat", "image_input", "audio_input", "tool_use", "llm_thinking"],
  "_default_max_output_tokens": 4000,
  "_endpoint_context_window": 4096,
  "_source_context_window": 32768
}
```

Endpoint truth wins: `_capabilities` always equals `_endpoint_capabilities`, meaning what this APK
currently serves for that installed model. `_source_capabilities` and `_source_context_window` are
informational upstream/package metadata and must not be used to decide whether to send media,
embeddings, tools, or other requests.

### Context Window Sizing

`_endpoint_context_window` is sized per device, not fixed per model. The catalog carries a
conservative floor for each model (4096 for LiteRT-LM chat models, 8192 or 16384 for MNN packages)
and the model's own limit in `_source_context_window`. On a device whose RAM is known, On-device AI
raises the endpoint window to the RAM tier's cap — 4096 below 5.5 GiB, 8192 below 7.5 GiB, 16384
below 11.5 GiB, 32768 above — never above the model's own limit and never below the catalog floor.
The **Context window** setting (global or per model, under **Settings → On-device AI**) overrides
the tier. The same value sizes the LiteRT-LM engine budget and MNN's `max_all_tokens`, gates the
automatic-tool compatibility rule, and is what `/v1/models` advertises, so a client can trust it.

### Conversation Reuse

OpenAI chat requests are stateless, but the LiteRT-LM runtime keeps the last conversation alive.
When the next request's transcript is exactly the previous one plus the runtime's own reply plus
one new message, that message is sent into the existing conversation and only it is prefilled. Any
other transcript (edited, trimmed, a different system prompt, different tools or sampling options)
starts a fresh conversation from the full history. Matching ignores tool-call ids, JSON key order
and surrounding whitespace, so clients that echo the assistant message with extra fields still
continue the conversation. MNN gets the same effect natively: `prompt_cache` is on, and MNN
prefills only the suffix after the longest common rendered-prompt prefix.

## Load Preflight And Isolation

`tai preflight <model>` and `POST /v1/ai/runtime/preflight` check compatibility without touching
native runtime code. The checks cover ABI, Android API level, bundled native libraries, model-file
readability/format, sidecar files for MNN packages, recommended and available memory, accelerator
support, known GPU exclusions, and prior backend failures recorded for this model/device.

Actual native loading happens only in `:tai_runtime`. If LiteRT-LM GPU initialization or MNN native
load crashes the runtime process, the launcher UI/API process remains alive and reports the last
attempted model, backend, accelerator, and suggested fallback.

## LiteRT-LM Backend

LiteRT-LM runs inside the isolated Android `:tai_runtime` process and is used for Gemma 4 and
MobileActions models.

Supported surfaces:

- text chat through `/v1/chat/completions`
- legacy completions through `/v1/completions`
- streaming chat/completion responses
- OpenAI function tools for LiteRT models that advertise `tool_use`
- image input for Gemma models that advertise `image_input`
- audio input for Gemma models that advertise `audio_input`

Not supported:

- generated audio output
- silently dropping unsupported image/audio content parts

Unsupported content parts return explicit OpenAI-shaped errors, such as
`unsupported_content_part` or `capability_not_supported`.

### Gemma 4 Defaults

Gemma 4 LiteRT defaults follow Google AI Edge Gallery defaults:

| Model | Accelerator | Max output tokens | TopK | TopP | Temperature |
| --- | --- | ---: | ---: | ---: | ---: |
| `gemma-4-e2b-it-litert-lm` | GPU, CPU fallback | 4000 | 64 | 0.95 | 1.0 |
| `gemma-4-e4b-it-litert-lm` | GPU, CPU fallback | 4000 | 64 | 0.95 | 1.0 |

Gemma 4 uses a multimodal LiteRT engine configuration:

- main text backend: selected accelerator, usually GPU
- vision backend: selected accelerator, usually GPU (only when an image-capable model id is
  loaded)
- audio backend: CPU (only when an audio-capable model id is loaded)

This matches the LiteRT-LM Android pattern used by Google AI Edge Gallery: GPU can run text and
vision while audio decoding uses CPU. If `accelerator` is omitted or set to `auto`, On-device AI
defaults to CPU until the same model/device has a successful GPU load history.

#### Per-modality model ids

To keep each load's GPU/OpenCL footprint small enough to fit (mirroring Edge Gallery's per-task
loading, which enables exactly one modality per screen), a multimodal model is advertised on
`/v1/models` as **up to three ids that share one downloaded file**. By default (split exposure)
the bare id is text-only, and only the modalities beyond that get a suffix:

| Model id | Modality loaded | Encoders initialized |
| --- | --- | --- |
| `gemma-4-e4b-it-litert-lm` | text chat only | none |
| `gemma-4-e4b-it-litert-lm-vision` | text + image | vision (GPU) |
| `gemma-4-e4b-it-litert-lm-audio` | text + audio | audio (CPU) |

An advanced **Combined exposure** setting serves every modality from the bare id at once, at a
higher memory cost; in that case the text-only variant is advertised as `model-id-text` instead of
the bare id.

Select the id from the shell exactly like any other model (`-m`/`"model"`). Switching ids reloads
the runtime scoped to that modality, the same way switching Gallery sections does. There is no
combined image+audio id, matching Gallery (every task enables a single modality). The same file is
downloaded once; the variants only narrow the advertised capabilities.

The isolated `:tai_runtime` process is bound with `BIND_IMPORTANT`, so while a model is loaded it
inherits the launcher's foreground priority and Android's low-memory killer reaps other background
apps before the runtime — preventing the GPU load from being SIGKILLed mid initialization.

You can force GPU for validation:

```sh
tai load gemma-4-e2b-it-litert-lm --gpu
```

A successful GPU load reports:

```text
Backend: GPU
```

### Image Input

Use OpenAI chat content parts with `image_url`. Data URLs, `file://` URLs, absolute paths, and
HTTP(S) URLs are accepted. Image input requires the `-vision` model id (see
[Per-modality model ids](#per-modality-model-ids)); the canonical id is text-only and rejects
images with `capability_not_supported`.

Example:

```json
{
  "model": "gemma-4-e2b-it-litert-lm-vision",
  "messages": [
    {
      "role": "user",
      "content": [
        {"type": "text", "text": "What color is this image?"},
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/png;base64,..."
          }
        }
      ]
    }
  ]
}
```

For large images, prefer `file://` or absolute local paths from the Android app's readable
storage. The localhost server accepts larger JSON bodies for common base64 image payloads, but
file paths avoid copying media through JSON.

### Audio Input

Use OpenAI-style `input_audio` content parts. Audio input requires the `-audio` model id (see
[Per-modality model ids](#per-modality-model-ids)); the canonical id is text-only and rejects audio
with `capability_not_supported`.

```json
{
  "model": "gemma-4-e2b-it-litert-lm-audio",
  "messages": [
    {
      "role": "user",
      "content": [
        {"type": "text", "text": "Transcribe or describe this audio."},
        {
          "type": "input_audio",
          "input_audio": {
            "data": "...base64 wav...",
            "format": "wav"
          }
        }
      ]
    }
  ]
}
```

The runner passes audio bytes to LiteRT-LM, and the model answers whatever the prompt asks about
the audio. For plain speech to text, `/v1/audio/transcriptions` is faster and needs no chat model;
see [Voice input](Voice_Input.md#the-api-v1audiotranscriptions).

### MobileActions

MobileActions uses the LiteRT-LM runner but has its own profile:

| Model | Accelerator | Max output tokens | TopK | TopP | Temperature |
| --- | --- | ---: | ---: | ---: | ---: |
| `functiongemma-270m-mobile-actions-litert-lm` | CPU only | 1024 | 64 | 0.95 | 0.0 |

FunctionGemma is loaded like any other generation model. Loading it unloads the previous
LiteRT-LM or MNN chat model; it is not kept in a companion slot.

On-device AI does not execute Android actions or shell commands by itself. It returns tool calls
for the client to handle.

## MNN-LLM Backend

MNN models are installed as a directory containing `config.json` and referenced sidecar files
(current bundled MNN version 3.6.1). The load path must point to `config.json`. MNN has no
image or audio input path.

On-device AI validates these sidecars before loading:

- `llm_model`
- `llm_weight`
- `tokenizer_file`

If a referenced file is missing or unreadable, load fails with `model_file_not_readable` and the
missing filename.

### MNN Defaults

For installed MNN configs, Auto means "use the model config." On-device AI preserves config
fields unless a request explicitly overrides a supported value.

The tested `qwen2.5-coder-1.5b-instruct-mnn` defaults are:

| Field | Default |
| --- | --- |
| `backend_type` | `cpu` |
| `thread_num` | `4` |
| `precision` | `low` |
| `memory` | `low` |
| `max_context_len` | `8192` |
| `max_new_tokens` | `1024` |
| `temperature` | `0.8` |
| `top_k` | `40` |
| `top_p` | `0.9` |

On-device AI also preserves upstream config fields such as tokenizer, sampler type, `min_p`,
`typical`, penalties, `n_gram`, and Jinja chat templates.

### MNN Request Overrides

OpenAI request JSON can override supported runtime fields:

```json
{
  "model": "qwen2.5-coder-1.5b-instruct-mnn",
  "temperature": 0.2,
  "top_p": 0.9,
  "top_k": 40,
  "max_tokens": 512,
  "context_window": 4096,
  "thread_count": 4,
  "precision": "low",
  "memory_mode": "low",
  "messages": [{"role": "user", "content": "Reply exactly OK"}]
}
```

If `accelerator` is `auto` or omitted, On-device AI uses CPU for safety before native load.
Explicit `accelerator` can override `backend_type` for supported values such as CPU or
OpenCL/GPU.

### MNN speculative decoding (EAGLE-3)

An MNN package built around an EAGLE-3 draft head (a `config.json` with `speculative_type` and its
`eagle*.mnn` files) advertises `speculative_decoding` and shows a switch in its parameters screen.
It is off by default: auto and explicit off both fall back to plain decoding, because on-device
measurement showed EAGLE-3 slower than plain decoding on both CPU and GPU, with slightly different
text. Turning the switch on tries it anyway on a package that ships a draft head; it has no effect
on one that does not. `tai benchmark --eagle` benches the draft model on for MNN builds that ship
one, as a separate leaderboard entry.

### MNN Tools

MNN models that advertise `tool_use` support OpenAI function tool requests through
`/v1/chat/completions`.

The bundled MNN native library has no structured-tool bridge, so On-device AI marks MNN tool mode
as `_tool_mode: "prompt_fallback"`: the model sees its tools as prompt text and the server parses
the text it emits back into OpenAI `tool_calls`. How the prompt is built depends on the package:

- **Model template (preferred).** When the package's exported chat template (`llm_config.json`,
  `jinja.chat_template`) has a `tools` branch — every Qwen2.5 and Qwen3 export does — the server
  passes the OpenAI function definitions to the template as its `tools` variable, passes assistant
  tool calls as structured messages and tool results as `role: "tool"` messages, and lets the
  model's own template render its official tool prompt, `<tool_call>` format and
  `<tool_response>` wrapping. A new model with a tools-aware template therefore works without any
  code change.
- **Prompt fallback.** Packages whose template has no `tools` branch get a Hermes-style `# Tools`
  system prompt with the same `<tool_call>` format, and tool results wrapped in `<tool_response>`
  by the server.

In both cases the server parses valid `<tool_call>...</tool_call>` blocks and bare JSON
function-call responses (including a single ```json fence). If a request with
`tool_choice: "required"` or a named tool yields no parsable call, it fails closed with
`mnn_required_tool_call_missing` (HTTP 422). It never synthesizes a call from the user's text.

Example request:

```json
{
  "model": "qwen2.5-coder-1.5b-instruct-mnn",
  "tool_choice": "required",
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "Get weather for a city",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {"type": "string"}
          },
          "required": ["city"]
        }
      }
    }
  ],
  "messages": [
    {"role": "user", "content": "Return only a tool call for get_weather with city Kuwait City."}
  ]
}
```

Expected response shape:

```json
{
  "choices": [
    {
      "finish_reason": "tool_calls",
      "message": {
        "role": "assistant",
        "content": null,
        "tool_calls": [
          {
            "type": "function",
            "function": {
              "name": "get_weather",
              "arguments": "{\"city\":\"Kuwait City\"}"
            }
          }
        ]
      }
    }
  ]
}
```

The client is still responsible for executing the tool and sending the result back as a
`role:"tool"` message.

### MNN mmap weight cache

MNN keeps a converted-weights cache in the app's cache directory, which can grow as large as the
model itself. It is rebuilt automatically after an app or runtime update, or after a load with
different settings; `tai load MODEL_ID --fresh` throws it away and rebuilds it on demand, if a
loaded MNN model ever starts giving degenerate replies.

## Runtime and safety behavior

Before loading a model, On-device AI checks:

- Android and CPU compatibility
- required native libraries
- model package readability and format
- available memory
- requested CPU/GPU mode
- previous failures for that model and device

Unknown imported models default to CPU. Automatic GPU selection is conservative; you can
explicitly test `tai load MODEL_ID --gpu` when the model profile supports it. A native runtime
crash is isolated from the launcher, and On-device AI records fallback guidance for the next
attempt.

The active model normally unloads after 10 minutes without use. Change the idle timeout in
On-device AI settings or use `tai keep-warm` when a client needs it available longer.

## Endpoint Behavior For Unsupported Modalities

On-device AI rejects unsupported media instead of dropping it.

Examples:

- MNN image input: `capability_not_supported`
- MNN audio input: `capability_not_supported`
- embeddings for models without `text_embeddings`: `capability_not_supported`
- text-only LiteRT model image input: `capability_not_supported`
- unknown content part type: `unsupported_content_part`
- chat audio output through `modalities:["audio"]`: `unsupported_audio_output`, HTTP 501
- `/v1/audio/speech` without a voice model installed: `tts_model_not_installed`, HTTP 400
- `/v1/audio/transcriptions` without a speech model installed: `stt_model_not_configured`, HTTP 400

This behavior is intentional for OpenAI-compatible CLI tools. Silent media dropping makes prompts
misleading.

## Validation Commands

Check model metadata:

```sh
endpoint="$(cat ~/.launcherctl/endpoint)"
token="$(cat ~/.launcherctl/token)"
curl -sS -H "Authorization: Bearer $token" "$endpoint/v1/models" | jq .
```

Load Gemma 4 on GPU:

```sh
curl -sS -H "Authorization: Bearer $token" \
  -H "Content-Type: application/json" \
  -d '{"model":"gemma-4-e2b-it-litert-lm","accelerator":"gpu"}' \
  "$endpoint/v1/ai/runtime/load" | jq .
```

Test text chat:

```sh
curl -sS -H "Authorization: Bearer $token" \
  -H "Content-Type: application/json" \
  -d '{"model":"gemma-4-e2b-it-litert-lm","messages":[{"role":"user","content":"Reply exactly OK"}]}' \
  "$endpoint/v1/chat/completions" | jq .
```

Load MNN and inspect effective config:

```sh
curl -sS -H "Authorization: Bearer $token" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen2.5-coder-1.5b-instruct-mnn"}' \
  "$endpoint/v1/ai/runtime/load" | jq '.effectiveConfig'
```

## References

- [On-device AI](On_Device_AI.md) — settings, model centre, `tai` CLI
- [LauncherCtl API](LauncherCtl_API.md) — full HTTP route tables
- [LiteRT-LM Android documentation](https://developers.google.com/edge/litert-lm/android)
- [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)
- [MNN upstream project](https://github.com/alibaba/MNN)
