# Termux AI

Termux AI (TAI) lets supported Android devices run language models locally. Your prompts and model output stay on the device unless you deliberately use a network service or expose the API to your local network.

TAI is the model host. You can chat with it through an OpenAI- or Ollama-compatible app such as Codex, OpenCode, Crush, or AIChat. The local API is an inference endpoint plus model manager — it does not control Android apps or the device.

## Quick start

1. Open **Settings → TAI / Termux AI**.
2. Open the model catalog and choose a model that fits your device memory.
3. Read and accept the model provider's terms when asked, then download the model.
4. Tap the installed model and choose **Load**. You can also leave **OpenAI auto-load** enabled so the first API request loads it after safety checks pass.
5. Use `tai status` in Termux to confirm that TAI is ready.

Models are not bundled in the APK. A download can be several gigabytes, so check the size and available storage first.

## Choosing a model

The catalog can change as compatible models are added. The following table explains the current built-in choices.

| Model | Best for | Approximate download | Suggested device RAM | Notes |
| --- | --- | ---: | ---: | --- |
| Gemma 4 E2B IT | General chat, images, audio, and tools | 2.4 GB | 8 GB+ | Recommended general model |
| Gemma 4 E4B IT | Better coding and reasoning | 3.7 GB | 12 GB+ | Larger and slower |
| Qwen2.5 Coder 1.5B MNN | Coding and terminal clients | 971 MB | 4–6 GB+ | Recommended coding model; supports tools |
| Qwen2.5 Coder 7B MNN | Higher-quality coding | 4.4 GB | 10–12 GB+ | Does not currently advertise tool use |
| Qwen2.5 0.5B MNN | Very small, fast text model | 557 MB | 3 GB+ | Lower answer quality |
| Qwen2.5 1.5B MNN | Lightweight general chat | 879 MB | 4–6 GB+ | Text and multilingual prompts |
| Qwen2.5 3B MNN | Balanced general chat | 2.4 GB | 6–8 GB+ | Better quality than the smaller Qwen models |
| DeepSeek-R1 1.5B MNN | Small reasoning tasks | 1.0 GB | 4–6 GB+ | Reasoning-focused |
| DeepSeek-R1 Distill 1.5B LiteRT | Small reasoning tasks | 1.7 GB | 6 GB+ | LiteRT package |
| FunctionGemma 270M | Choosing a function or device tool | 0.3 GB | 6 GB+ | CPU-only, 1,024-token context; not a general assistant |
| Qwen2.5 1.5B LiteRT | Imported lightweight model | 1.5 GB | 6 GB+ | Import-only until a verified direct artifact is available |

Only one chat/generation model is active at a time. Loading FunctionGemma is the same as loading any other model: it replaces the currently loaded chat model. TAI does not silently load it beside another assistant.

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
| `speculative_decoding` | Supports the LiteRT speculative-decoding option |
| `mobile_actions` | Tuned to choose from compatible Android action tools |

`_capabilities` in `/v1/models` is the important field for apps. `_source_capabilities` describes upstream claims, while `_endpoint_capabilities` describes what this APK can currently provide.

### Images and audio

Multimodal LiteRT models appear as separate IDs that share one downloaded file:

- `model-id` for text
- `model-id-vision` for image input
- `model-id-audio` for audio input

Choose the ID matching the input you intend to send. Only one mode is loaded at a time, which reduces memory use.

### FunctionGemma and phone actions

FunctionGemma is an optional catalog model. It can return structured tool calls when a client sends compatible tool definitions, but it does not execute those calls and it does not run beside another model. Executing any returned tool call is the client's responsibility; TAI itself does not perform Android actions.

## Connect an AI app

TAI stores its current local address and secret token in:

```text
~/.launcherctl/endpoint
~/.launcherctl/token
```

The address normally looks like `http://127.0.0.1:54298`. OpenAI-compatible clients usually need `/v1` appended to it.

Read the token and endpoint at call time rather than copying the secret into configuration files:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Use `/v1/responses` for current Codex-compatible Responses clients. Use `/v1/chat/completions` for OpenAI-compatible chat clients. Ollama-compatible clients use the same base address without adding `/v1`.

## Available API endpoints

You do not need these routes for normal use, but they help when configuring another app.

### OpenAI-compatible

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/models` | List installed, loadable models and capabilities |
| GET | `/v1/models/{id}` | Return one model object |
| POST | `/v1/responses` | Responses API text, image/audio input, streaming, and function calls |
| POST | `/v1/chat/completions` | Chat Completions text, streaming, media, and tools |
| POST | `/v1/completions` | Legacy text completions |
| POST | `/v1/embeddings` | Embeddings for models advertising `text_embeddings` |
| POST | `/v1/audio/speech` | Returns an unsupported-operation error; speech output is not available |

OpenAI streaming uses server-sent events and ends with `data: [DONE]`.

### Ollama-compatible

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/version` | Compatibility version |
| GET | `/api/tags` | List installed models |
| POST | `/api/show` | Show one model's details and capabilities |
| POST | `/api/chat` | Chat and tool calls |
| POST | `/api/generate` | Prompt-style generation |
| GET | `/api/ps` | Show the loaded model |
| POST | `/api/embed` | Create embeddings when supported |
| POST | `/api/embeddings` | Legacy alias: `{model, prompt}` in, `{embedding: [...]}` out |
| POST | `/api/pull` `/api/create` `/api/push` `/api/copy` `/api/delete` | Return 501 (not emulated) |

Ollama streaming uses newline-delimited JSON. Ollama registry operations (`pull`, `push`, `create`, `copy`, `delete`) are not emulated. Install models from the TAI catalog or import flow instead.

### Model management

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/ai/status` | Overall status, settings, and limitations |
| GET | `/v1/ai/runtime` | Loaded model and runtime state |
| GET | `/v1/ai/models` | Detailed TAI model registry |
| POST | `/v1/ai/runtime/preflight` | Check whether a model can load safely |
| POST | `/v1/ai/runtime/load` | Load a model |
| POST | `/v1/ai/runtime/unload` | Unload the active model |
| POST | `/v1/ai/runtime/keep-warm` | Keep a model loaded temporarily |
| POST | `/v1/ai/runtime/cancel` | Cancel active generation |
| POST | `/v1/ai/models/import` | Register a supported local package |
| POST | `/v1/ai/models/download-catalog` | Download a catalog model |
| GET | `/v1/ai/models/downloads` | Show download progress/history |
| POST | `/v1/ai/models/download` | Download a model from a URL |
| POST | `/v1/ai/models/downloads/cancel` | Cancel an active download |
| POST | `/v1/ai/models/delete` | Delete an installed user model |
| POST | `/v1/ai/models/load` | Load a model into the registry slot |
| POST | `/v1/ai/models/unload` | Unload a model from the registry slot |
| POST | `/v1/auth/rotate` | Rotate the API token |

### Auth

Routes require the bearer token (sent as `Authorization: Bearer <token>` or `X-Api-Key: <token>`). A new **Require API token** setting (default on) under **Settings → Services & permissions → Termux AI** lets you turn token checks off for localhost so local CLI clients need no real key — any placeholder works. `GET /` and `OPTIONS` never require auth. **LAN bind mode always requires the token** regardless of the toggle.

Keep localhost mode enabled unless you deliberately need LAN access. Anyone who can reach a LAN-exposed endpoint and knows its token can submit model requests.

## Useful `tai` commands

```sh
tai status
tai runtime
tai models
tai downloads
tai preflight MODEL_ID
tai load MODEL_ID
tai load MODEL_ID --cpu
tai load MODEL_ID --gpu
tai keep-warm MODEL_ID --minutes 30
tai cancel
tai unload
tai doctor
```

`tai` manages models; it is not an interactive chat program. Add `--json` when you need raw output for a script.

## Importing your own model

TAI supports complete packages for its included runtimes:

- LiteRT-LM `.litertlm` or `.task` files
- MNN model directories containing `config.json` and all required sidecar files
- LiteRT EmbeddingGemma `.tflite` packages with their required tokenizer files

Example LiteRT import:

```sh
tai import /absolute/path/to/model.litertlm my-local-model
```

TAI does not run GGUF, safetensors, PyTorch, or ONNX weights. Use a separate compatible runtime, such as llama.cpp in Termux, for GGUF models.

For gated Hugging Face models, first accept the agreement on the model's Hugging Face page. Then create a read token and save it under **Settings → TAI / Termux AI → Hugging Face token**. The token is used only for Hugging Face downloads.

## Runtime and safety behavior

Before loading a model, TAI checks:

- Android and CPU compatibility
- required native libraries
- model package readability and format
- available memory
- requested CPU/GPU mode
- previous failures for that model and device

Unknown imported models default to CPU. Automatic GPU selection is conservative; you can explicitly test `tai load MODEL_ID --gpu` when the model profile supports it. A native runtime crash is isolated from the launcher, and TAI records fallback guidance for the next attempt.

The active model normally unloads after 10 minutes without use. Change the idle timeout in TAI settings or use `tai keep-warm` when a client needs it available longer.

## Troubleshooting

Start with:

```sh
tai doctor
tai status
tai runtime
```

Common problems:

- **Model not listed:** finish downloading or importing it, then check `tai models`.
- **Model not loaded:** enable OpenAI auto-load or run `tai load MODEL_ID`.
- **Not enough memory:** close other apps, choose CPU, or use a smaller model.
- **GPU load crashes:** retry with `tai load MODEL_ID --cpu`.
- **401 Unauthorized:** refresh your client with the current value from `~/.launcherctl/token`, or turn off **Require API token** for localhost use.
- **Connection refused:** reopen Termux Launcher, check `~/.launcherctl/endpoint`, and run `tai status`.
- **Tools are ignored:** confirm the selected model advertises `tool_use`.
- **Image or audio rejected:** use the model's `-vision` or `-audio` ID from `/v1/models`.

For advanced backend and model-package details, see [Termux AI backends](Termux_AI_Backends.md).
