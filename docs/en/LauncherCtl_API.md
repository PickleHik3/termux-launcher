# LauncherCtl API (Local Shell Bridge)

## Overview
LauncherCtl is a localhost API bridge for exposing Android/app data to shell tools without high-frequency `adb`/`rish` polling loops.

- Server: in app process, bound to `127.0.0.1` on random port.
- Auth: bearer token from `~/.launcherctl/token`.
- Endpoint URL: `~/.launcherctl/endpoint`.
- CLI: `$PREFIX/bin/launcherctl` (installed by the launcher app when `TermuxActivity` starts).

Important behavior:
- `launcherctl tty-doctor` validates the optional local `~/.rish/rish` setup.
- `tai` uses the same authenticated bridge for the local Termux AI endpoint; native AI runtime work is isolated in `:tai_runtime`.
- Custom Shizuku shell commands should use `rish -c` directly.

## Files and Components

- Server implementation:
  - `app/src/main/java/com/termux/launcherctl/LauncherCtlApiServer.java`
- Notification/media source:
  - `app/src/main/java/com/termux/launcherctl/LauncherCtlNotificationListener.java`
- App startup wiring:
  - `app/src/main/java/com/termux/app/TermuxActivity.java`
- Manifest service entry:
  - `app/src/main/AndroidManifest.xml`

Runtime files under `$HOME/.launcherctl`:

- `token`: API bearer token.
- `endpoint`: local base URL (`http://127.0.0.1:<port>`).
- `launcher.db`: SQLite store for notification history and agent memory.
- `notifications.jsonl`: append-only notification event stream for scripts/debugging.
- `events.jsonl`: append-only system/agent event stream.
- `agent-runs.jsonl`: append-only route/execute audit stream.
- `tools.json`: generated LauncherCtl tool schema snapshot.
- `capabilities.json`: generated hardware/capability snapshot.

## Endpoints (v1)

### TAI / Termux AI
TAI endpoints are documented in [TAI / Termux AI](Termux_AI). They share this API server, bearer token, and localhost endpoint.

Common routes:
- `GET /v1/ai/status`
- `GET /v1/ai/runtime`
- `POST /v1/ai/runtime/preflight`
- `POST /v1/ai/runtime/load`
- `POST /v1/ai/runtime/unload`
- `POST /v1/ai/runtime/keep-warm`
- `POST /v1/ai/runtime/cancel`
- `GET /v1/ai/models`
- `POST /v1/ai/models/import`
- `POST /v1/ai/models/download`
- `POST /v1/ai/models/download-catalog`
- `GET /v1/ai/models/downloads`
- `POST /v1/ai/models/delete`
- `POST /v1/ai/models/load`
- `POST /v1/ai/models/unload`
- `GET /v1/models`
- `POST /v1/chat/completions`
- `POST /v1/responses`
- `POST /v1/completions`
- `POST /v1/embeddings`
- `POST /v1/audio/speech`
- `GET /api/version`
- `GET /api/tags`
- `POST /api/show`
- `POST /api/chat`
- `POST /api/generate`
- `GET /api/ps`
- `POST /api/embed`

`/v1/chat/completions` and `/v1/completions` support `stream: true` and return `text/event-stream` chunks ending with `data: [DONE]`.

`/v1/responses` is the stateless OpenAI Responses adapter used by current Codex clients. It
supports text/image inputs, function calls and function results over the same LiteRT-LM/MNN
runtime contract. The native Ollama `/api/chat` and `/api/generate` adapters stream newline-delimited
JSON by default. Ollama registry operations (`pull`, `create`, `push`, `copy`, and `delete`) are not
emulated because Ollama/GGUF packages are not LiteRT-LM or MNN packages.

Use `launcherctl client-config aichat|opencode|crush|codex|ollama` to generate a configuration from
the models and endpoint capabilities currently installed on the device. The command references the
`LAUNCHERCTL_TOKEN` environment variable rather than writing the bearer token into generated files.

`POST /v1/ai/runtime/preflight` checks ABI, API level, bundled native libraries, model package readability/format, memory, accelerator policy, and known backend history without touching native LiteRT-LM/MNN runtime code.

#### `GET /v1/models` metadata

Each entry in the standard OpenAI-shaped `data` array includes TAI-specific
metadata prefixed with an underscore so existing OpenAI clients ignore it:

- `_backend`: backend routing for the model, currently `litert-lm` (default
  LiteRT-LM runtime) or `mnn-llm` (bundled MNN backend).
- `_capabilities`: ordered list of endpoint capability strings, for example
  `text_chat`, `image_input`, `audio_input`, `tool_use`, or `code`. This is
  what the installed APK can currently serve and is identical to
  `_endpoint_capabilities`.
- `_source_capabilities`: informational upstream/package capabilities. Clients
  should not treat these as enabled endpoint features.
- `_default_max_output_tokens`, `_endpoint_context_window`, and
  `_source_context_window`: runtime default, TAI endpoint cap, and
  upstream/package context metadata.
- `_tool_mode`: present for tool-capable models. MNN tool support is
  `prompt_fallback`; LiteRT tool support is native when advertised.

#### `POST /v1/embeddings`

OpenAI-compatible embeddings endpoint. Embeddings support is model-capability
dependent: only models that advertise `text_embeddings` in their `/v1/models`
`_capabilities` array are accepted. `input` may be a string or an array of
strings, and the response returns one OpenAI `embedding` item per input in the
same order. Local output is float vectors only; `encoding_format:"base64"` is
rejected with `unsupported_encoding_format`.

LiteRT EmbeddingGemma `.tflite` installs require `sentencepiece.model` in the
same model directory. New downloads fetch that sidecar automatically. Older
installs that only contain the `.tflite` return `embedding_tokenizer_missing`
until the model is re-downloaded or the sidecar is added.

### `GET /v1/status`
Returns backend + LauncherCtl runtime status.

### `GET /v1/apps`
Returns the launcher's launchable activity catalog.

Each entry includes:
- `label`
- `packageName`
- `activityName`
- `stableId`
- `userId`
- `userSerialNumber`
- `clonedProfile`
- `profileLabel` when available
- `systemApp`
- `launchable`

Normal primary-user apps keep their existing stable id format:
`package/activity`. Cloned or work/profile variants are returned as separate
launchable entries when Android exposes them to the launcher, with stable ids
like `package/activity#user=10` and labels such as `WhatsApp · Clone 10`.

The top-level payload also includes:
- `count`: number of launchable activities
- `packageCount`: number of unique packages represented by those activities

### `GET /v1/system/resources`
Returns a system resource snapshot:
- CPU metrics:
  - `cpuPercent` (from `/proc/stat` delta, with load-average fallback)
  - `cpuCores`
  - `loadAvg1m`, `loadAvg5m`, `loadAvg15m`
- Memory metrics:
  - top-level compatibility fields:
    - `memTotalBytes`, `memAvailableBytes`, `memFreeBytes`, `memUsedBytes`
  - nested `memory` object with additional meminfo-derived fields:
    - `buffersBytes`, `cachedBytes`, `swapCachedBytes`, `activeBytes`, `inactiveBytes`,
      `shmemBytes`, `slabBytes`, `swapTotalBytes`, `swapFreeBytes`
- Runtime/heap metrics:
  - `javaHeapUsedBytes`, `javaHeapMaxBytes`, `javaHeapFreeBytes`, `javaHeapTotalBytes`
  - nested `runtime` object
- Uptime metrics:
  - nested `uptime` object (`systemUptimeSec`, `systemUptimeMs`, `processUptimeMs`, `processUptimeSec`)
- Storage metrics:
  - nested `storage` array with per-path totals/used/free/available bytes
- Battery metrics:
  - nested `battery` object (`levelPercent`, charging state, plug type, temperature, voltage, health)
- Network metrics:
  - nested `network` array with per-interface `rx/tx` bytes, packets, errors, drops
- Thermal metrics:
  - nested `thermal` array from `/sys/class/thermal/thermal_zone*`
- Backend diagnostics:
  - `backendType`, `backendState`, `statusReason`, `statusMessage`, `isPrivilegedAvailable`

### `GET /v1/media/now-playing`
Returns cached now-playing media session data.
Requires notification listener access.

### `GET /v1/media/art`
Returns cached now-playing album art snapshot as base64-encoded JPEG payload.
Requires notification listener access.

### `GET /v1/notifications`
Returns cached notification list.
Requires notification listener access.

### Notification History
Notification listener events are persisted under `~/.launcherctl` and can be queried without scraping the active notification snapshot.

- `POST /v1/notifications/recent` with `{ "limit": 50 }`
- `POST /v1/notifications/since` with `{ "since": 1710000000000, "limit": 200 }`
- `POST /v1/notifications/search` with `{ "query": "invoice", "limit": 50 }`
- `POST /v1/notifications/stats` with optional `{ "since": 1710000000000 }`

The `launcherctl notifications recent|since|search|stats` commands wrap these endpoints.

### Status-bar pin rules
Rules decide which notifications are pinned into the expanded status bar's widget slot (max 3, oldest evicted). An empty rule list leaves the feature idle. A rule matches on package name, on a case-insensitive substring of the title or body, or on both; `clear` decides whether dismissing the pin also cancels the notification.

- `GET /v1/notifications/pin-rules`
- `POST /v1/notifications/pin-rules/add` with `{ "package": "com.whatsapp", "match": "Amma", "clear": false }`
- `POST /v1/notifications/pin-rules/remove` with `{ "id": "1f2e3d" }`

The `launcherctl notifications pin-rules|pin-add|pin-remove` commands wrap these endpoints. Pinned notifications and the media widget both require notification listener access.

### `GET /v1/launcher/capabilities`
Returns device and integration capability metadata for scripts and agents:

- ABI, SDK, RAM, device model, accelerator support, and backend warnings.
- notification listener/access state.
- TAI runtime state and loaded model id.
- available LauncherCtl tool names.
- integration hints including `openAiCompatible`, `mcpStdio`, and `mcpCommand`.

Unsupported hardware is reported as structured warnings/blocking reasons rather than hidden failure.

### Agent Tools
LauncherCtl exposes a shared tool registry for local agents, MCP clients, and scripts.

- `GET /v1/agent/tools`: internal tool metadata plus OpenAI-compatible function schemas.
- `POST /v1/agent/route`: route a natural-language request to a tool and arguments.
- `POST /v1/agent/execute`: execute a named tool with JSON arguments.

Execution is confirmation-gated for medium/high/critical risk tools. `launcherctl agent --dry-run "open maps"` routes only. `launcherctl agent "open maps"` routes and executes with `confirm: true`, so use dry-run first when reviewing an action.

Agent routing is deterministic and does not load or invoke a hidden secondary model. FunctionGemma is available separately through the normal TAI model catalog and generation APIs.

### Events
Agent route/execute calls are audited to `~/.launcherctl/events.jsonl` and `~/.launcherctl/agent-runs.jsonl`.

- `GET /v1/events`: recent event snapshot.
- `POST /v1/events/tail`: recent events with optional limit/since body.
- `GET /v1/events/stream`: snapshot SSE stream ending with `data: [DONE]`.

### `POST /v1/auth/rotate`
Rotates API token and rewrites `~/.launcherctl/token` and `~/.launcherctl/endpoint`.

## CLI Usage

```sh
launcherctl --help
launcherctl status
launcherctl apps
launcherctl launch whatsapp
launcherctl resources
launcherctl media
launcherctl art
launcherctl notifications
launcherctl notifications recent 50
launcherctl capabilities
launcherctl tools
launcherctl agent --dry-run "open maps"
launcherctl events tail 20
launcherctl mcp
launcherctl update-scripts
launcherctl tty-doctor
launcherctl token rotate
```

Note: `launcherctl-status` is not a command. Use `launcherctl status`.

## Terminal LLM Client Configuration

TAI exposes OpenAI-compatible HTTP endpoints so terminal clients such as
`aichat`, `tmuxai`, or any tool that reads `OPENAI_BASE_URL` /
`OPENAI_API_KEY` can drive the local model runtime.

Default bind mode is `localhost` (server bound to `127.0.0.1`). The bearer
token is required for every request. Token and endpoint URL are written to:

```sh
~/.launcherctl/token
~/.launcherctl/endpoint
```

The first line of `~/.launcherctl/endpoint` is the active base URL, e.g.
`http://127.0.0.1:41237`. Pass it as `OPENAI_BASE_URL` with `/v1` appended:

```sh
BASE=$(sed -n '1p' ~/.launcherctl/endpoint)
TOKEN=$(cat ~/.launcherctl/token)
export OPENAI_BASE_URL="$BASE/v1"
export OPENAI_API_KEY="$TOKEN"
```

Do not echo `$TOKEN` into shell history. Prefer reading it from the file at
call time or storing it in a credentials manager.

### Supported Endpoints

- `GET /v1/models` — list models with TAI-specific `_backend` and
  `_capabilities` metadata.
- `POST /v1/chat/completions` — OpenAI chat completion.
- `POST /v1/completions` — OpenAI legacy completion.
- `POST /v1/embeddings` — OpenAI embeddings for local models whose
  `_capabilities` include `text_embeddings`.
- `POST /v1/audio/speech` — returns `unsupported_audio_output` until a local
  runner exposes generated audio.

`/v1/embeddings` is model-capability dependent. Not all models support
embeddings. Check each model's `_capabilities` field returned by
`GET /v1/models` before calling this endpoint; unsupported models return a
`capability_not_supported` error. EmbeddingGemma `.tflite` packages also need
the `sentencepiece.model` sidecar in the same model directory.

## MCP Stdio Bridge

`launcherctl mcp` runs an MCP stdio server backed by the LauncherCtl tool registry. It is intended for MCP-capable local clients that want to list and call Android launcher tools while keeping all state and credentials in `~/.launcherctl`.

The bridge requires Python in Termux:

```sh
pkg install python
launcherctl mcp
```

It supports newline-delimited JSON-RPC and `Content-Length` framed JSON-RPC. Tool calls use the same confirmation gates as `/v1/agent/execute`; pass `_confirm: true` in MCP tool arguments only when the client has obtained explicit user confirmation.

For user-facing MCP client setup, tool names, and examples, see
[LauncherCtl MCP](LauncherCtl_MCP).

### LiteRT Backend Example

LiteRT (default) models expose the LiteRT-LM runtime and return standard
OpenAI-compatible responses. Use the same `OPENAI_BASE_URL` /
`OPENAI_API_KEY` setup as above and pick any LiteRT model id from
`/v1/models`:

```sh
MODEL=$(curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$OPENAI_BASE_URL/models" | jq -r '.data[] | select(._backend=="litert-lm") | .id' | head -n1)
curl -fsS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}" \
  "$OPENAI_BASE_URL/chat/completions"
```

### MNN Backend Example

MNN models route through the bundled MNN backend. Only models whose
`_backend` field equals `mnn-llm` should be requested via MNN. Current MNN
catalog models are chat/code models; image, audio, and embeddings requests are
rejected unless the model advertises that endpoint capability.

```sh
MODEL=$(curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$OPENAI_BASE_URL/models" | jq -r '.data[] | select(._backend=="mnn-llm" and (._capabilities | index("text_chat"))) | .id' | head -n1)
[ -n "$MODEL" ] && curl -fsS -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"Reply exactly OK\"}]}" \
  "$OPENAI_BASE_URL/chat/completions"
```

Inspect `/v1/models` first to confirm both `_backend == "mnn-llm"` and the
endpoint capability you intend to use are present.

### `launcherctl update-scripts`

Refreshes repo-owned shell helpers such as `launcher-system-monitor`, `launcher-weather-widget`, `setup-btop-rish`, and the tmux material theme script. It backs up existing files before replacing them and does not overwrite `~/.tmux.conf`.

Use this after an APK update when you want the latest helper scripts without repeating the Getting Started flow.

## rish Compatibility Commands

`launcherctl` does not wrap arbitrary `rish` commands. For custom Shizuku shell commands, call `rish` directly:

```sh
rish -c "id"
```

Prerequisites in user home:
- `~/.rish/rish` (executable)
- `~/.rish/rish_shizuku.dex` (present; on Android 14+, keep this non-writable)

Diagnostics:
```sh
launcherctl tty-doctor
```

For btop, use the documented `setup-btop-rish` helper. For tmux CPU/RAM widgets, prefer `launcherctl resources`; the `rish` fallback is kept for plain Termux + Shizuku compatibility and is less efficient because it starts a Shizuku shell for sampling.

## Security Model

### Attack Surface
- Localhost API reachable from local device processes.
- Token theft enables API calls.
- Notification/media endpoints may expose sensitive user content.

### Bind Mode
- Default: `localhost` — server bound to `127.0.0.1`. Only processes on the
  device can reach the API.
- Opt-in: `lan` — server bound to `0.0.0.0`. Any device on the local network
  that knows the bearer token can reach the API. LAN mode is disabled by
  default and only becomes active after the user changes the setting.

### Mitigations Implemented
- Bearer token auth, startup-generated random token.
- Constant-time token comparison.
- Bounded worker pool (prevents unbounded thread growth).
- HTTP parser limits:
  - request line size,
  - header line size/count,
  - max body size.
- Endpoint rate limiting (`429` on abuse).
- Token rotation endpoint.
- Sensitive files written owner-only.
- No CORS headers emitted (browser-based clients cannot use the API even in
  LAN mode without an explicit proxy).

### LAN Opt-In Considerations
- LAN mode (`bindMode: lan`) is opt-in and surfaces a `lanWarning` field in
  the endpoint settings JSON plus the `tai` CLI help text.
- Treat the bearer token as a network secret whenever LAN mode is active.
  Do not paste it into shell history, screenshots, or shared notes.
- Rotate the token (`launcherctl token rotate`) after temporarily enabling
  LAN mode if the token may have been observed.
- A firewall on the LAN, a per-call `Authorization: Bearer <token>` header,
  and short-lived sessions are recommended for any non-trivial LAN use.

### Remaining Security Considerations
- Localhost token auth still depends on local process trust.
- If same app UID ecosystem is compromised, token can be read.
- LAN mode trusts every device on the local network; it does not implement
  per-device authentication.
- Consider Unix domain sockets for tighter local access boundaries in future.
- Consider endpoint toggles for media/notifications if privacy requirements increase.

## Notification and Media Data

`launcherctl media` and `launcherctl notifications` require notification listener permission for the app.
`launcherctl art` also requires notification listener permission.

If not granted, responses include:
- `listenerConnected: false`
- a `hint` message.

## Troubleshooting

### Interactive command fails with "No tty detected"
- Run `launcherctl tty-doctor` and apply its suggested fixes for `~/.rish`.

### `launcherctl media`/`notifications` empty
- Grant notification access for the app in Android settings.

### Token errors (`401`)
- Run `launcherctl token rotate`.
- Re-run command after the token file is rewritten.

## Performance Notes

- LauncherCtl is event-driven for notifications/media and avoids constant polling loops.
- `/v1/apps` can be heavier than status queries; avoid frequent tight loops.
- `/v1/system/resources` is designed for periodic dashboard polling.
