# LauncherCtl API (Local AI Endpoint)

## Overview
LauncherCtl is a localhost HTTP server that exposes an OpenAI- and Ollama-compatible inference endpoint, model management for the on-device Termux AI (TAI) runtime, and one app-launch route. It is not a general device-control or agent bridge.

- Server: in app process, isolated from native model work which runs in `:tai_runtime`.
- Bind mode: `localhost` (default, `127.0.0.1`) or opt-in `lan` (`0.0.0.0`).
- Auth: bearer token from `~/.launcherctl/token`, or `X-Api-Key: <token>` header. The token can be made optional for localhost (see [Auth](#auth)).
- Endpoint URL: `~/.launcherctl/endpoint`.
- CLIs: `$PREFIX/bin/tai` for local AI and `$PREFIX/bin/launcherctl` for `launcherctl launch <app name, package, or activity>`. The launcher app installs both when `TermuxActivity` starts.
- Removed helpers: `launcherctl-mcp` and `launcher-restart` are no longer installed and are deleted on upgrade.

`tai` uses this authenticated server for the local Termux AI endpoint; native AI runtime work is isolated in `:tai_runtime`.

## Files and Components

- Server implementation:
  - `app/src/main/java/com/termux/launcherctl/LauncherCtlApiServer.java`
- App startup wiring:
  - `app/src/main/java/com/termux/app/TermuxActivity.java`
- Manifest service entry:
  - `app/src/main/AndroidManifest.xml`

Runtime files under `$HOME/.launcherctl`:

- `token`: API bearer token.
- `endpoint`: local base URL (`http://127.0.0.1:<port>`).

TAI model packages live under app-private model storage, not under `~/.launcherctl`.

## Discovery

Read the active endpoint and token from the two files the app writes on startup:

```sh
BASE=$(sed -n '1p' ~/.launcherctl/endpoint)   # e.g. http://127.0.0.1:41237
TOKEN=$(cat ~/.launcherctl/token)
```

The first line of `~/.launcherctl/endpoint` is the active base URL. OpenAI-compatible clients expect `/v1` appended; Ollama-compatible clients use the base address as-is.

If the files are missing, open Termux Launcher once so the server starts and writes them.

## Auth

Authentication uses a bearer token. Send it with either header:

- `Authorization: Bearer <token>`
- `X-Api-Key: <token>`

The token is a startup-generated random secret stored owner-only at `~/.launcherctl/token`. Comparison is constant-time.

### Token-optional toggle (localhost only)

A new setting **Require API token** (default **on**) lives under **Settings → Services & permissions → Termux AI**. When turned **off**, requests from localhost need no token — any placeholder API key (or none) works. This is convenient for local CLI clients that cannot easily read the token file.

- `GET /` and `OPTIONS` never require auth, regardless of the toggle.
- **LAN bind mode always requires the token**, no matter the toggle state. Anyone who can reach a LAN-exposed endpoint and does not present the token gets `401`.

Rotate the token with `POST /v1/auth/rotate`, which rewrites `~/.launcherctl/token` and `~/.launcherctl/endpoint`.

## CORS and Health

All responses include `Access-Control-Allow-Origin: *` so browser-based clients can call the API (the token is still required for protected routes).

- `GET /` — returns `Ollama is running` with HTTP 200. No auth. Used by clients to detect a live Ollama-compatible server.
- `OPTIONS` — CORS preflight. No auth. Returns the standard `Access-Control-Allow-*` headers.

## Endpoint Reference

The complete route surface is below. App launch is the only non-TAI action route. There are no notification, media, resource, event, agent, MCP, restart, or general device-control routes.

### Health and discovery

| Method | Path | Auth | Purpose |
| --- | --- | :---: | --- |
| GET | `/` | no | Health check, body `Ollama is running` |
| OPTIONS | any | no | CORS preflight |

### App launch

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/apps/launch` | Match and launch one app from the launcher's app catalog |

The request body contains one non-empty query:

```json
{"query":"maps"}
```

The query may be an app label, package name, activity name, or partial match. Exact package,
activity, and stable-id matches rank first. Exact labels rank next, followed by prefix and
substring matches. The best unique match launches and returns HTTP 200:

```json
{
  "ok": true,
  "query": "maps",
  "label": "Maps",
  "packageName": "com.example.maps",
  "activityName": "com.example.maps.MainActivity",
  "stableId": "com.example.maps/com.example.maps.MainActivity#user=0",
  "userId": 0,
  "clonedProfile": false
}
```

No match returns HTTP 404 with error code `not_found`. Several matches tied at the best rank return
HTTP 409 with error code `ambiguous` and a `candidates` array containing up to eight app records.
An empty query returns HTTP 400 with `bad_request`. A matched app that Android cannot start returns
HTTP 500 with `launch_failed`.

The route allows 30 requests per minute. The installed shell client reads the endpoint and bearer
token from `~/.launcherctl`, then sends this request:

```sh
launcherctl launch maps
launcherctl launch com.example.maps
```

`launcherctl` has no other command. Use `tai` for model and inference commands.

### OpenAI-compatible

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/models` | List installed, loadable models with TAI metadata |
| GET | `/v1/models/{id}` | Return one model object (filtered from `/v1/models`) |
| POST | `/v1/chat/completions` | Chat completions: text, image/audio input, tools, SSE streaming |
| POST | `/v1/responses` | Stateless OpenAI Responses adapter (text/image input, function calls/results) |
| POST | `/v1/completions` | Legacy text completions, SSE streaming |
| POST | `/v1/embeddings` | Embeddings for models advertising `text_embeddings` |
| POST | `/v1/audio/speech` | Always returns `unsupported_audio_output` (HTTP 501) |

OpenAI `/v1/*` streaming uses Server-Sent Events (`text/event-stream`) and ends with `data: [DONE]`.

#### `GET /v1/models` metadata

Each entry in the standard OpenAI-shaped `data` array includes TAI-specific metadata prefixed with an underscore so existing OpenAI clients ignore it:

- `_backend`: backend routing for the model, currently `litert-lm` (default LiteRT-LM runtime) or `mnn-llm` (bundled MNN backend).
- `_capabilities`: ordered list of endpoint capability strings, for example `text_chat`, `image_input`, `audio_input`, `tool_use`, or `code`. This is what the installed APK can currently serve and is identical to `_endpoint_capabilities`.
- `_source_capabilities`: informational upstream/package capabilities. Clients should not treat these as enabled endpoint features.
- `_default_max_output_tokens`, `_endpoint_context_window`, and `_source_context_window`: runtime default, TAI endpoint cap, and upstream/package context metadata.
- `_tool_mode`: present for tool-capable models. MNN tool support is `prompt_fallback`; LiteRT tool support is native when advertised.

`GET /v1/models/{id}` returns the single matching object (HTTP 404 if unknown).

#### `POST /v1/embeddings`

OpenAI-compatible embeddings endpoint. Only models that advertise `text_embeddings` in their `/v1/models` `_capabilities` array are accepted; others return `capability_not_supported`. `input` may be a string or an array of strings, and the response returns one OpenAI `embedding` item per input in the same order. Local output is float vectors only; `encoding_format:"base64"` is rejected with `unsupported_encoding_format`.

LiteRT EmbeddingGemma `.tflite` installs require `sentencepiece.model` in the same model directory. New downloads fetch that sidecar automatically. Older installs that only contain the `.tflite` return `embedding_tokenizer_missing` until the model is re-downloaded or the sidecar is added.

### Ollama-compatible

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/version` | Compatibility version |
| GET | `/api/tags` | List installed models |
| POST | `/api/show` | Show one model's details and capabilities |
| GET | `/api/ps` | Show the loaded model |
| POST | `/api/chat` | Chat and tool calls, NDJSON streaming |
| POST | `/api/generate` | Prompt-style generation, NDJSON streaming |
| POST | `/api/embed` | Create embeddings when supported |
| POST | `/api/embeddings` | Legacy alias: `{model, prompt}` in, `{embedding: [...]}` out |
| POST | `/api/pull` `/api/create` `/api/push` `/api/copy` `/api/delete` | Return HTTP 501 (not emulated) |

Ollama `/api/chat` and `/api/generate` stream newline-delimited JSON (NDJSON) by default. Ollama registry operations (`pull`, `create`, `push`, `copy`, `delete`) are not emulated because Ollama/GGUF packages are not LiteRT-LM or MNN packages. Install models from the TAI catalog or import flow instead.

### Model management

These routes are used by the `tai` CLI and the Settings UI. They share the same auth as the inference routes.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/ai/status` | Overall status, settings, and limitations |
| GET | `/v1/ai/runtime` | Loaded model and runtime state |
| GET | `/v1/ai/models` | Detailed TAI model registry |
| GET | `/v1/ai/models/downloads` | Show download progress/history |
| POST | `/v1/ai/models/import` | Register a supported local package |
| POST | `/v1/ai/models/download` | Download a model from a URL |
| POST | `/v1/ai/models/download-catalog` | Download a catalog model |
| POST | `/v1/ai/models/downloads/cancel` | Cancel an active download |
| POST | `/v1/ai/models/delete` | Delete an installed user model |
| POST | `/v1/ai/models/load` | Load a model into the registry slot |
| POST | `/v1/ai/models/unload` | Unload a model from the registry slot |
| POST | `/v1/ai/runtime/preflight` | Check whether a model can load safely |
| POST | `/v1/ai/runtime/load` | Load a model into the active runtime |
| POST | `/v1/ai/runtime/unload` | Unload the active model |
| POST | `/v1/ai/runtime/keep-warm` | Keep a model loaded temporarily |
| POST | `/v1/ai/runtime/cancel` | Cancel active generation |
| POST | `/v1/auth/rotate` | Rotate the API token and rewrite discovery files |

`POST /v1/ai/runtime/preflight` checks ABI, API level, bundled native libraries, model package readability/format, memory, accelerator policy, and known backend history without touching native LiteRT-LM/MNN runtime code.

## Streaming Notes

- OpenAI endpoints (`/v1/chat/completions`, `/v1/completions`, `/v1/responses`) use SSE (`text/event-stream`) and terminate with `data: [DONE]`.
- Ollama endpoints (`/api/chat`, `/api/generate`) use NDJSON: one JSON object per line, final object flagged `done: true`.
- Non-streaming requests return a single JSON body.

## Rate Limiting

Each protected route has its own token-bucket rate limiter. When a bucket is exhausted the server returns HTTP `429 Too Many Requests` with a `Retry-After: <seconds>` header indicating when the bucket refills. The error body uses the standard error envelope (see below).

Limits are per-route, not global, so heavy generation traffic does not starve unrelated management calls.

## Error Envelope

OpenAI-style routes (`/v1/*`) return a nested OpenAI error object:

```json
{
  "error": {
    "message": "model not loaded",
    "type": "model_not_loaded",
    "code": null
  }
}
```

Ollama-style routes (`/api/*`) return a flat error string in the Ollama convention:

```json
{
  "error": "model not loaded"
}
```

Rate-limit errors use `type: "rate_limit_error"` on `/v1/*` and the same flat `error` string on `/api/*`. HTTP status codes follow OpenAI/Ollama conventions (`400`, `401`, `404`, `409`, `429`, `500`, `501`).

## Terminal LLM Client Configuration

TAI exposes OpenAI-compatible HTTP endpoints so terminal clients such as `aichat`, `aider`, `tmuxai`, or any tool that reads `OPENAI_BASE_URL` / `OPENAI_API_KEY` can drive the local model runtime.

Default bind mode is `localhost` (server bound to `127.0.0.1`). With the **Require API token** setting on (default), the bearer token is required for every protected request. Token and endpoint URL are written to:

```sh
~/.launcherctl/token
~/.launcherctl/endpoint
```

Pass the endpoint as `OPENAI_BASE_URL` with `/v1` appended:

```sh
BASE=$(sed -n '1p' ~/.launcherctl/endpoint)
TOKEN=$(cat ~/.launcherctl/token)
export OPENAI_BASE_URL="$BASE/v1"
export OPENAI_API_KEY="$TOKEN"
```

Do not echo `$TOKEN` into shell history. Prefer reading it from the file at call time or storing it in a credentials manager.

If you turned **Require API token** off for localhost use, any placeholder key works:

```sh
export OPENAI_API_KEY="local"
```

### Example: list and call a LiteRT model

```sh
MODEL=$(curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$OPENAI_BASE_URL/models" | jq -r '.data[] | select(._backend=="litert-lm") | .id' | head -n1)
curl -fsS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}" \
  "$OPENAI_BASE_URL/chat/completions"
```

### Example: call an MNN model

MNN models route through the bundled MNN backend. Only models whose `_backend` field equals `mnn-llm` should be requested via MNN. Current MNN catalog models are chat/code models; image, audio, and embeddings requests are rejected unless the model advertises that endpoint capability.

```sh
MODEL=$(curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$OPENAI_BASE_URL/models" | jq -r '.data[] | select(._backend=="mnn-llm" and (._capabilities | index("text_chat"))) | .id' | head -n1)
[ -n "$MODEL" ] && curl -fsS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply exactly OK\"}]}" \
  "$OPENAI_BASE_URL/chat/completions"
```

Inspect `/v1/models` first to confirm both `_backend == "mnn-llm"` and the endpoint capability you intend to use are present.

## Security Model

### Attack Surface
- Localhost API reachable from local device processes.
- Token theft enables API calls.
- Inference output may reflect model/package content.

### Bind Mode
- Default: `localhost` — server bound to `127.0.0.1`. Only processes on the device can reach the API.
- Opt-in: `lan` — server bound to `0.0.0.0`. Any device on the local network can reach the API. **LAN mode always requires the token** even when the localhost token-optional toggle is off.

### Mitigations Implemented
- Bearer token auth (or `X-Api-Key`), startup-generated random token.
- Constant-time token comparison.
- Token-optional toggle only loosens localhost auth; LAN always enforces.
- Bounded worker pool (prevents unbounded thread growth).
- HTTP parser limits:
  - request line size,
  - header line size/count,
  - max body size.
- Per-route token-bucket rate limiting (`429` with `Retry-After`).
- Token rotation endpoint.
- Sensitive files written owner-only.
- CORS `Access-Control-Allow-Origin: *` on all responses so browser clients work; protected routes still require the token.

### LAN Opt-In Considerations
- LAN mode (`bindMode: lan`) is opt-in and surfaces a `lanWarning` field in the endpoint settings JSON plus the `tai` CLI help text.
- Treat the bearer token as a network secret whenever LAN mode is active. Do not paste it into shell history, screenshots, or shared notes.
- Rotate the token (`POST /v1/auth/rotate`) after temporarily enabling LAN mode if the token may have been observed.
- A firewall on the LAN, a per-call `Authorization: Bearer <token>` header, and short-lived sessions are recommended for any non-trivial LAN use.

### Remaining Security Considerations
- Localhost token auth still depends on local process trust.
- If same app UID ecosystem is compromised, token can be read.
- LAN mode trusts every device on the local network; it does not implement per-device authentication.
- Consider Unix domain sockets for tighter local access boundaries in future.

## Troubleshooting

### Token errors (`401`)
- Read the current token: `cat ~/.launcherctl/token`.
- Rotate it with `POST /v1/auth/rotate` or from **Settings → Services & permissions → Termux AI → Recreate API token**, then re-run your command.
- If you turned **Require API token** off, confirm you are still on `localhost` bind mode — LAN mode always requires the token.

### `Connection refused`
- Reopen Termux Launcher so the server starts.
- Check `~/.launcherctl/endpoint` for the current port.
- Run `tai status`.

### Rate limited (`429`)
- Wait for the `Retry-After` interval and retry.
- Space out polling loops; limits are per-route.

## Performance Notes

- The server is request-driven and avoids polling loops.
- Inference is offloaded to the isolated `:tai_runtime` process; the launcher UI/API process stays alive if the native runtime crashes.
- Only one chat/generation model is resident at a time; switching models unloads the previous one.
