# Developer Docs

This page keeps the intermediate and high-level details out of the beginner guide. Start with [Getting Started](Launcher_Getting_Started.md) if you are installing or using the launcher for the first time.

## Project Shape

Termux Launcher is based on [termux-app](https://github.com/termux/termux-app), with launcher UI, sixel-capable terminal rendering, Material color integration, and the local Termux AI runtime added on top.

Important local areas:

- `app/src/main/java/com/termux/launcherctl/LauncherCtlApiServer.java`: local OpenAI/Ollama-compatible inference API server and app-launch route; installs the `tai` and launch-only `launcherctl` shell clients.
- `app/src/main/java/com/termux/launcherctl/LauncherCtlNotificationListener.java`: notification and media cache source for the in-app status bar UI.
- `app/src/main/java/com/termux/ai/`: TAI settings, model registry, model downloads/imports, and runtime adapters.
- `resources/bin/tai`: installed TAI shell helper.
- `docs/en/examples/`: optional shell and Neovim setup scripts, plus the terminal, font, and keyboard config examples.

## LauncherCtl Internals

LauncherCtl runs in the app process and binds to:

```text
127.0.0.1
```

Runtime files:

```sh
~/.launcherctl/endpoint
~/.launcherctl/token
```

The endpoint file contains the base URL without `/v1`, for example:

```text
http://127.0.0.1:41237
```

The token file contains the bearer token used by `tai` and direct API clients.

### App Launch Route

```text
POST /v1/apps/launch
```

The body is `{"query":"..."}`. The query matches the launcher's app catalog by label, package,
activity, or stable id. A unique best match launches and returns its app record. No match returns
404 `not_found`; tied best matches return 409 `ambiguous` with up to eight candidates. The route is
limited to 30 requests per minute.

The installed `launcherctl launch <app name, package, or activity>` client is the only shell command
for this route. Agent, MCP, notification, media, resource, event, and restart commands are not
installed. Local AI commands belong to `tai`.

### TAI Routes

```text
GET  /v1/ai/status
GET  /v1/ai/runtime
POST /v1/ai/runtime/load
POST /v1/ai/runtime/unload
POST /v1/ai/runtime/keep-warm
POST /v1/ai/runtime/cancel
GET  /v1/ai/models
POST /v1/ai/models/import
POST /v1/ai/models/download
POST /v1/ai/models/download-catalog
GET  /v1/ai/models/downloads
POST /v1/ai/models/downloads/cancel
POST /v1/ai/models/delete
POST /v1/ai/models/load
POST /v1/ai/models/unload
GET  /v1/models
GET  /v1/models/{id}
POST /v1/chat/completions
POST /v1/responses
POST /v1/completions
POST /v1/embeddings
POST /v1/auth/rotate
```

`/v1/chat/completions` and `/v1/completions` support `stream: true` and return `text/event-stream` chunks ending with:

```text
data: [DONE]
```

### Security Model

Implemented mitigations:

- bearer token authentication with an optional-off toggle for localhost
- constant-time token comparison
- bounded worker pool
- HTTP request size limits
- endpoint rate limiting
- token rotation (`POST /v1/auth/rotate`)
- owner-only sensitive files

Remaining considerations:

- Localhost token auth still depends on local process trust.
- Apps or processes that can read the same Termux home files can read the token.
- A future Unix-domain socket could tighten local access further.

## Termux AI Runtime Notes

TAI stores user overrides separately from model metadata. Most runtime tunables default to `Auto / Gallery default`:

- max tokens
- TopK
- TopP
- temperature
- accelerator
- thinking
- speculative decoding
- idle unload or keep-warm policy

Known catalog profiles are synchronized with Google AI Edge Gallery 1.0.15:

- `Gemma-4-E2B-it`: GPU, CPU; 8 GiB minimum; 4000 max tokens; TopK 64; TopP 0.95; temperature 1.0.
- `Gemma-4-E4B-it`: GPU, CPU; 12 GiB minimum; 4000 max tokens; TopK 64; TopP 0.95; temperature 1.0.
- `MobileActions-270M`: CPU only; 6 GiB minimum; 1024 max tokens; TopK 64; TopP 0.95; temperature 0.0.

Auto accelerator follows the ordered compatible accelerator list from the Gallery model allowlist. Explicit `--gpu` or `--cpu` is accepted only when both the model profile and device support it.

Device memory detection follows Gallery behavior:

- Android 14 and newer use `ActivityManager.MemoryInfo.advertisedMem`.
- Older Android versions use `totalMem`.
- Low memory is reported as a warning so the user can still decide whether to proceed.

The LiteRT-LM `Engine` remains loaded after `tai load`. TAI reuses a `Conversation` while the model, prompt mode, system prompt, and sampling options remain compatible. One generation runs at a time.

Future runtime work:

- isolate GPU probing/loading so native GPU initialization failures cannot crash the main launcher process
- add benchmark counters
- expand multimodal and tool-calling support when there is a clear API boundary
- add more download controls such as pause, cancel, and retry in the UI

Reference material:

- [Google AI Edge Gallery model allowlist 1.0.15](https://github.com/google-ai-edge/gallery/blob/main/model_allowlists/1_0_15.json)
- [Gallery model allowlist/device policy](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/ModelAllowlist.kt)
- [Gallery memory detection](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/MemoryWarning.kt)
- [Gallery LiteRT-LM runtime](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt)

## Model Import Details

Settings import and CLI import intentionally behave differently.

Settings import:

- uses Android's document picker
- accepts `.litertlm` and `.task`
- copies the selected file into app-private model storage
- registers the copied model path

CLI import:

```sh
tai import /absolute/path/to/model.litertlm MyModel
```

- requires a path the app process can read
- registers the path
- does not copy the file into app-private storage
- accepts optional runtime profile metadata through the JSON API

The import API accepts fields such as:

- `runtimeProfile.compatibleAccelerators`
- `defaultMaxTokens`
- `defaultTopK`
- `defaultTopP`
- `defaultTemperature`
- `minDeviceMemoryInGb`

Unknown imported models default to CPU, matching Gallery's import behavior.

## Material Colors

When Terminal Material colors are enabled, the launcher writes:

```sh
~/.termux/material-colors.sh
~/.termux/material-colors.properties
```

The shell file exports variables such as:

```sh
TERMUX_MATERIAL_PRIMARY
TERMUX_MATERIAL_ON_SURFACE
TERMUX_MATERIAL_SURFACE
TERMUX_MATERIAL_SURFACE_CONTAINER
TERMUX_MATERIAL_TERMINAL_BACKGROUND
TERMUX_MATERIAL_TERMINAL_FOREGROUND
TERMUX_MATERIAL_TERMINAL_COLOR4
```

Shell startup pattern:

```sh
if [ -r "$HOME/.termux/material-colors.sh" ]; then
    . "$HOME/.termux/material-colors.sh"
fi
```

tmux can read exported environment values with `#{E:VARIABLE_NAME}`:

```tmux
set -g status-style "fg=#{E:TERMUX_MATERIAL_ON_SURFACE},bg=#{E:TERMUX_MATERIAL_SURFACE_CONTAINER}"
set -g window-status-current-style "fg=#{E:TERMUX_MATERIAL_SURFACE},bg=#{E:TERMUX_MATERIAL_PRIMARY}"
```

## Optional Helper Scripts

The helper scripts in `docs/en/examples/` are not installed by the APK. The beginner setup downloads them when requested.

Scripts:

- `setup-launcher`: interactive installer for the current shell setup, offering all of it, the shell essentials, or single items — fish, Oh My Posh, zoxide, eza, Neovim through `setup-nvim`, and the showcase binaries (sigye, fastfetch, kitten) as digest-pinned release assets. It deliberately installs nothing under `~/.termux`; the app seeds those files and would lose in-app edits if a script rewrote them.
- `setup-nvim`: interactive Neovim setup. Installs AstroNvim (default), NvChad, LazyVim, kickstart, or a stock config, plus launcher integrations: OSC 52 clipboard, always-on line wrap, and — on AstroNvim and NvChad — a colorscheme generated from `~/.termux/material-colors.sh` that retints live on wallpaper changes.
- `config.fish`, `conf.d-personal.fish`, and `aliens-material.omp.json`: optional Fish and Oh My Posh defaults. `config.fish` is launcher-owned and replaced on re-install; `conf.d-personal.fish` is copied once to `~/.config/fish/conf.d/personal.fish` and never overwritten, so personal edits survive re-runs. It is the Termux-edition copy — the Nix edition ships its own, with nix-on-droid shortcuts in place of the `pkg`/`pacman` ones.

Refresh installed helper scripts after an APK or docs update by re-running `setup-launcher`, or by re-downloading the single script from `docs/en/examples/`. Every template it replaces gets a timestamped `.bak` first.

## Shizuku and rish

Normal launcher usage does not require Shizuku. Its optional uses are privileged ones the app sandbox cannot reach on its own, such as the Shizuku lock method.

For direct Shizuku shell commands, call `rish` directly:

```sh
rish -c "id"
```

Expected local setup:

- `rish` is executable and in `$PATH`, or `RISH_BIN` points to it.
- `rish_shizuku.dex` is beside it as Shizuku generated.
- the bottom of `rish` uses the current value of `$TERMUX_APP__PACKAGE_NAME`
  for `RISH_APPLICATION_ID` (`com.termux` for the standard edition or
  `io.vaj.tl` for the VAJ edition).
- Shizuku permission has been granted once.

If `rish` fails to start, the most common cause is a stale or missing `rish_shizuku.dex`, or a mismatched `RISH_APPLICATION_ID` at the bottom of the `rish` script. To fix it:

1. In Shizuku, regenerate the terminal helper files (`rish` and `rish_shizuku.dex`).
2. Copy both files back into a Termux PATH directory.
3. Make `rish` executable: `chmod +x "$(command -v rish)"`.
4. Confirm the bottom of `rish` sets `RISH_APPLICATION_ID` to this app's package name (`com.termux` for the standard edition or `io.vaj.tl` for the VAJ edition).
5. Run `rish -c "id"` once and grant the Shizuku permission prompt.

