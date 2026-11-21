# Frontend for On-device AI: what to use (research, 2026-11-03)

Status: research note, not a decision. Nothing here is committed or built.

## The question

"Frontend for the local AI engine of Termux Launcher. What should I use?" I read it as: the
launcher holds models and serves them, but has no place where the user simply talks to one. Which
chat/assistant surface should sit in front of it?

## What "the engine" is (repo facts)

- **Runtimes**: LiteRT-LM (`app/build.gradle:12`, `:220`, version 0.18.0) and MNN
  (`app/build.gradle:281`, `MnnTaiRuntime.java`). No llama.cpp; GGUF is explicitly unsupported
  (`docs/en/On_Device_AI.md:13-14`). Models: Gemma 4 E2B/E4B for chat, Whisper and Parakeet for
  STT, KittenTTS for read aloud, EmbeddingGemma for Dawn search (`docs/en/On_Device_AI.md:98-110`).
  Silero VAD is covered in `project-docs/reference/voice-ai/voice-vad-eval-2026-09-27.md`.
- **Process**: native model work runs in `:tai_runtime` so a model crash cannot take the home
  screen down (`app/src/main/AndroidManifest.xml:401`; `docs/GLOSSARY.md:869-873`;
  `TaiRuntimeService.java:51`). The runtime unloads after 10 idle minutes by default
  (`docs/en/On_Device_AI.md:28-29`).
- **Exposure**: a localhost HTTP server, `LauncherCtlApiServer`. It serves OpenAI routes
  (`/v1/chat/completions` with SSE streaming, `/v1/responses`, `/v1/completions`, `/v1/embeddings`,
  `/v1/audio/transcriptions`, `/v1/audio/speech`: `LauncherCtlApiServer.java:726-758`), Ollama
  routes (`/api/chat`, `/api/generate`, `/api/tags`, ...: `:567-601`), and model/runtime
  management under `/v1/ai/*` (`:651-725`). Tool calls are translated
  (`TaiApiCompatibility.java:38-55`, `:82`, `:128`) and the catalogue marks which models support
  `tool_use` (`TaiModelCatalog.java:212`, `:218`).
- **Auth and origin**: bearer token in `~/.launcherctl/token`, address in `~/.launcherctl/endpoint`
  (`docs/en/On_Device_AI.md:129-146`). Browser pages are accepted only from loopback origins; any
  other Origin gets 403 (`LauncherCtlApiServer.java:372-378`, `isAllowedOrigin` at `:475-485`).
  Optional LAN access, plain HTTP, token always required (`docs/en/On_Device_AI.md:189-191`).
- **CLI**: `tai` manages models and "is not a chat program" (`docs/en/On_Device_AI.md:197`).
- **Frontends today**: none in the app. Settings pages only (Model centre, Runtime status).
  The docs send chat users to `aichat` (`docs/en/On_Device_AI.md:134-142`), and
  `TaiAichatConfig.java:7-27` already generates an aichat client entry from the live discovery
  response. Glossary: Dawn is a tlstore terminal program that is a client of the embeddings
  endpoint, "not the launcher's assistant" (`docs/GLOSSARY.md:761-765`). No ADR or plan in
  `docs/adr/`, `project-docs/active/` or `project-docs/backlog.md` uses the word "frontend" for
  AI (grepped). The only "Dawn chat" mention is as a feature in
  `project-docs/active/tai-feature-load-plan-2026-10-08.md:33,47,61`.
- **Stack facts that matter**: the app has zero Kotlin files and no Compose (`find app/src/main
  -name "*.kt"` returns none; `app/build.gradle` has no compose dependency). It has no WebView
  in app code. minSdk 26, targetSdk 28.

## Constraints that bind (from AGENTS.md)

1. Home screen: a crash is the home screen falling over; engine already isolated in `:tai_runtime`,
   so a frontend must not pull model work back into the main process.
2. Memory: idle live terminal about 207 MB PSS (`docs/agents/performance.md:53`); pixels sit
   behind bounded stores. Adding a UI toolkit or a browser engine to that process is retained
   cost forever.
3. No continuously repainting animation (streaming text must append, not animate).
4. targetSdk 28 on every edition; three editions with different package names (use
   `TermuxConstants`, never `com.termux`).
5. Surfaces follow Base/scheme theming; a native surface must join that system, a foreign UI
   will not.
6. Extend seams (a Host interface), do not grow `TermuxActivity`.
7. Product copy: short title plus one sentence.

## Candidates

| # | Candidate | Class | License | Maintenance (verified 2026-11-03) | Talks to engine via | Footprint / runs on Termux aarch64 | Fit |
|---|---|---|---|---|---|---|---|
| 1 | **aichat** | TUI/CLI (REPL, shell assistant, RAG, function calling, `--serve` playground) | Apache-2.0 | v0.30.0 released 2025-07-06; last push 2026-02-23 [1] | Generic `openai-compatible` client [2] | Single Rust binary; already a Termux package at 0.30.0 [3] (not run against Gemma on pong by me: unverified) | Best: no app memory, no app code, config generator exists |
| 2 | **gptme** | TUI agent | MIT | v0.36.0 on 2026-11-02 [4] | OpenAI-compatible | Python; Termux package not found in termux-packages [5]; heavier, shell-tool agent | Possible; agentic power the small local model may not carry |
| 3 | **oterm** | Textual TUI | MIT | 0.26.1 on 2026-10-21 [6] | Ollama API (launcher has `/api/chat`, `/api/tags`) or OpenAI via pydantic-ai | Python >=3.11 and a large pinned dependency set (textual, pydantic-ai, aiosqlite) [7]; no termux-packages entry [5] | Works in principle; heavy install |
| 4 | **simonw/llm** | CLI (plus `llm chat`) | Apache-2.0 | 0.37 on 2026-11-02 [8] | `extra-openai-models.yaml` with `api_base` [9] | Python >=3.11, `openai` dep [10]; no termux-packages entry [5] | Good for scripting, not a "frontend" |
| 5 | **llama.cpp web UI** | Web UI served by `llama-server` | MIT | v0.8.0 on 2026-10-28 [11] | Its own server only | Engine here is not llama.cpp; its UI ships inside that server [12]. Reusing it against the launcher's endpoint: unverified | Not applicable |
| 6 | **Open WebUI** | Web app | BSD-3 plus a branding clause that bans removing "Open WebUI" branding beyond 50 users/30 days [13] | v0.12.5 on 2026-10-29 [11] | OpenAI-compatible | Python 3.11 to <3.13 [14] plus a large dependency tree; Termux Python version mismatch is my inference, unverified | Poor: server in Termux plus WebView, branding clause conflicts with a distributed fork |
| 7 | **Native View chat surface** (RecyclerView/Material, which the app already has) | In-app | ours | n/a | In-process `TaiManager` or loopback HTTP | No new toolkit; text only; the token/stream path already exists | Joins theming and Host seams; costs build effort |
| 8 | **Jetpack Compose chat** | In-app | Apache-2.0 | active | same | Compose requires Kotlin [15]; app has none; adds compiler, runtime and a second UI stack to a never-dying process | Poor fit: new toolchain for one surface |
| 9 | **WebView on a local page** | In-app web | n/a | n/a | loopback HTTP from a `http://127.0.0.1` page (allowed by `isAllowedOrigin`) | WebView renderer runs in multiprocess mode from Android 8 and the system can kill it for memory [16], which helps; the app-side overhead number is unmeasured here: unverified | Possible for rich rendering; adds a page to ship and a token to inject |
| 10 | **Other OpenAI/Ollama-compatible Android or desktop clients over LAN** | Any client | varies | varies | The existing endpoint, with LAN access on | Zero cost to the launcher; HTTP only, token required [docs/en/On_Device_AI.md:189] | Already supported; mention it, do not build for it |

Sources for the table are numbered at the end.

## Recommendation (first pass, superseded by the revised one below)

**Make the terminal the frontend: recommend aichat now, and keep the endpoint the only contract.**

Reasons:
- The launcher is a terminal first; a chat in a pane inherits panes, themes, the keyboard,
  split and floating windows and Base/scheme colours for free, so no new surface has to be
  taught the Appearance system.
- Zero retained memory in the home-screen process and zero crash surface: aichat is an
  ordinary child process; the engine already lives in `:tai_runtime`.
- It is already the documented path (`docs/en/On_Device_AI.md:134-142`), already has a config
  generator (`TaiAichatConfig.java`), already exists as a Termux package [3], and speaks the
  same OpenAI routes the server implements, including streaming and function calling [2].
- It matches the three-edition rule: it names nothing about the package.

Work this implies (small): ship aichat as a tlstore item with an on-first-run config written
from `TaiAichatConfig`, and optionally a `tai chat` alias that execs it. Verify once on pong
that a Gemma 4 E2B reply streams and that function calling round-trips (unverified now).

Caveat to weigh: aichat's last release is July 2025 and activity slowed in 2026 [1]. Since the
contract is a plain OpenAI endpoint, replacing it later costs nothing.

**Runner-up: a native chat surface in plain Android Views** (RecyclerView plus the Material
components already in `app/build.gradle`), behind a Host interface, reading Base/scheme
colours. Choose it if you want a thing a non-terminal user can open from the drawer or an
overlay and voice in/out (STT and TTS are already in-process). Do not use Compose (needs
Kotlin, no existing Kotlin, second toolkit) and do not use a WebView page unless you need
rich Markdown or code rendering badly.

Not recommended: Open WebUI (heavy Python server plus a branding clause incompatible with a
redistributed fork [13]), llama.cpp's web UI (belongs to a different engine [12]).

## Revision (same day): the five use cases

The developer named what the AI is for. The first recommendation (aichat) was written without
them; this section tests it against each, and the recommendation below it replaces the earlier one.

### Facts the engine gives (repo)

- **Audio into the chat model**: the Gemma 4 catalogue entries carry `audio_input` and `image_input`
  (`TaiModelCatalog.java:212`, `:218`). The server accepts OpenAI `image_url` and `input_audio`
  content parts (`TaiManager.java:4823-4858`, `:4888-4903`; Responses mapping in
  `TaiApiCompatibility.java:104-111`). A model id with the `-audio` suffix loads the audio
  encoder on CPU and there is no combined image+audio id, so one model load handles one modality
  (`docs/en/On_Device_AI_Backends.md:234-244`). Unsupported parts are refused, not dropped
  (`:194-199`).
- **No Opus decoding**: the speech path reads RIFF/WAVE or raw PCM16 only
  (`WhisperAudio.java:12-38`); `/v1/audio/transcriptions` is documented as "WAV or raw PCM16
  16 kHz mono" (`LauncherCtlApiServer.java:2260`). No `MediaExtractor` or `MediaCodec` anywhere in
  `app/src/main/java` (grepped). The Gemma path forwards the raw bytes (`TaiManager.java:4896`);
  whether LiteRT-LM decodes Ogg/Opus itself is unverified. Google's model card recommends "a short
  16 kHz mono WAV" [17].
- **Speech models**: Whisper ACFT Base/Small (multilingual; `TaiModelCatalog.java:230`, `:256`) and
  Parakeet TDT 0.6B v3, which covers 25 European languages, not Arabic
  (`TaiModelCatalog.java:281-288`; `docs/en/Voice_Input.md:164`). Whisper's decoder is hard-wired
  to the transcribe task: the prompt is `<sot><lang><transcribe><notimestamps>`
  (`WhisperDecoder.java:70`) and the `<|translate|>` token is suppressed (`:113`). So the on-device
  ASR route cannot translate; it can transcribe Arabic if `language=ar` is passed (the route takes a
  `language` field, `LauncherCtlApiServer.java:1034`). Arabic quality of the ACFT files is not
  documented in the repo (`grep -i arabic` over `docs/` and `project-docs/` finds nothing for STT).
- **Gemma 4 on audio**: Google's model card lists a 30 second audio limit and says ASR and
  speech translation are supported on E2B and E4B, with CoVoST scores 33.47 (E2B) and 35.54 (E4B);
  140+ languages pre-trained, Arabic not named [18]. LiveCodeBench v6: E2B 44.0%, E4B 52.0% [18].
- **Context**: the model supports a 128K window per the card [18] (the LiteRT file's card says "up
  to 32k" [17]), but the launcher serves 4096 by default (2048 on 6 GB phones)
  (`TaiContextWindowPolicy.java:25-26`; `docs/en/On_Device_AI_Models.md:183`), and a user may raise
  it to 4096 up to 8 GB, 8192 at 10-12 GB, 16384 from 16 GB (`TaiContextWindowPolicy.java:47-55`).
  The window is a deliberate memory cap ("8k cost ~0.8 GB more than 4k for E4B on pong's GPU",
  `TaiContextWindowPolicy.java:21-24`).
- **Share target**: the launcher already receives Android `SEND` intents for `audio/*`,
  `image/*`, `application/*`, `text/*` (`AndroidManifest.xml:243-259`).
  `FileReceiverActivity` saves the shared file to `~/downloads` or hands it to
  `~/bin/termux-file-editor` as its only argument (`FileReceiverActivity.java:55-57`, `:120-124`,
  `:433`). So a WhatsApp "Share" to the launcher already lands the .ogg in the terminal's
  filesystem, and a user-written `termux-file-editor` script could start a pipeline. Nothing in
  the app transcribes it.
- **Notifications**: the launcher records history in SQLite, opt-in per app (`app_notification_history_packages`),
  default retention 30 days (1 to 365), at most 50,000 rows, codes masked by default
  (`LauncherCtlNotificationStore.java:27`, `:41`, `:200-204`; `docs/en/LauncherCtl_API.md:506-570`).
  Reachable with `launcherctl notifications --since 7d --app ... [--json]` and
  `GET /v1/notifications` (filters `app`, `since`, `until`, `query`, `limit`, `format=text`). The
  doc's own example is the developer's use case: "make a task list from my work email
  notifications over the last week" (`docs/en/LauncherCtl_API.md:508-509`). Missing: it is not in
  `LauncherToolRegistry` (no notification tool; grepped), so no model can call it as a function;
  there is no summarising or windowing step, and the serving window (4096) is far smaller than a
  week of rows. The media-fetch policy deliberately denies `~/.launcherctl` to model-referenced
  paths (`TaiMediaAccess.java:55-58`), so a client has to read the history itself and paste it
  in. That is a feature (a model request cannot exfiltrate it), and it means the frontend, not
  the engine, does the fetching.

### What aichat can and cannot do (its own source and docs)

- **Chat, coding, files as text**: `-f file`, directories, URLs and `` `cmd` `` inputs in both CLI and
  REPL [2]. Document loaders map an extension to a command: built-in defaults `pdf: pdftotext $1 -`
  and `docx: pandoc --to plain $1` (`src/config/mod.rs`, `setup_document_loaders`) [19]; custom loaders
  are one line of config [20]. There is no xlsx or csv loader by default; a custom loader (for
  example Miller, which is packaged for Termux [21]) would be needed. Termux packages `poppler`
  and `pandoc` exist [21].
- **Images**: `png, jpeg, jpg, webp, gif` are sent as `image_url` parts (`src/config/input.rs`,
  `IMAGE_EXTS`) [22]; the generated config marks `supports_vision` when the model has `image_input`
  (`TaiAichatConfig.java:22-23`).
- **Audio**: no audio extension handling in `input.rs` [22]; aichat cannot send `input_audio`.
- **Tools**: function calling via external tools and MCP (README) [2]. Whether Gemma 4 E2B calls them
  reliably over the launcher's translation layer is not measured.
- **Share intents**: a terminal program cannot receive one. That is the launcher's existing
  `FileReceiverActivity` job, not aichat's.

### Per-use-case table

| # | Use case | Engine today | Data | Frontend that fits | Verdict | Smallest work to close |
|---|---|---|---|---|---|---|
| 1 | Arabic voice note (Opus .ogg) to English text | Gemma 4 audio input works through `input_audio` on an `-audio` id, but 30 s max [18] and no verified Ogg decode; Whisper ACFT transcribes Arabic with `language=ar` but cannot translate (`WhisperDecoder.java:70`, `:113`); Parakeet has no Arabic. No Opus decode (`WhisperAudio.java:12-38`). Arabic quality undocumented | WhatsApp share already lands the file via `FileReceiverActivity` | Not aichat (no audio). Share target plus a script | **Gap** | In the terminal: `ffmpeg` (packaged, built with libopus [21]) to 16 kHz mono WAV, split into under-30 s chunks, then either (a) Whisper `language=ar` then Gemma text translation, or (b) Gemma `-audio` with a "translate to English" prompt. Measure both on a few real notes first. Better: a share-sheet action that does this in-app and shows the English text |
| 2 | Chat about an image or a file (text, spreadsheet, PDF) | Images: `image_url` on a `-vision` id works. Text: fine within 4096 tokens, which is short for a PDF or sheet | File is on the phone | aichat: image yes, PDF and docx yes through poppler/pandoc, spreadsheets need a custom loader; context cap is the real limit | **Partly works** | Install poppler, pandoc, miller for tlstore users; add an xlsx/csv loader line to the aichat config the launcher generates; raise the context window if RAM allows (8192 at 10-12 GB). Sharing a file from another app: the existing receiver, then `aichat -f` |
| 3 | Light coding | Gemma 4 E4B is marketed for "Coding and reasoning" (`TaiModelCatalog.java:215`); LiveCodeBench v6 E4B 52.0%, E2B 44.0% [18]. E4B needs 12 GB+ (`TaiModelCatalog.java:216`) | Files in the terminal | aichat with `-f`, shell-assistant mode and function tools; terminal is where the code is | **Works**, limited by 4096-token window and model strength | None beyond a larger context on big phones; keep it a terminal job |
| 4 | Chat with notification history; organise into tasks | Notification store and route exist; model is not given a tool for them; 4096-token window | `launcherctl notifications --since 7d --format text` | aichat or any shell pipeline: `launcherctl notifications ... \| aichat "make a task list"` | **Works for small ranges, gap for a week** | Add a windowing step (per app and per day, map then merge) in a small script; later register a notification tool in `LauncherToolRegistry` so function calling can pull it. Privacy: masking is on by default; opt-in per app is already enforced |
| 5 | Normal chat | `/v1/chat/completions` streaming | n/a | aichat REPL | **Works** | None |

Evidence tags: engine facts above; aichat facts [2], [19]-[22]; Gemma figures [17], [18]. Unverified: Arabic
quality of Whisper ACFT and Gemma 4 for dialect voice notes; LiteRT-LM Ogg/Opus decoding; how aichat
behaves against the translation layer for tool calls; a 5-10 second Whisper window against 30-60 s notes.

## Recommendation (revised): a split, one contract

The endpoint stays the only contract. Two clients sit on it.

1. **Terminal frontend for use cases 2 (text and PDF), 3, 4 and 5: aichat**, shipped through tlstore
   with poppler, pandoc and Miller as its companions. All four are text in, text out through
   `-f`, a pipe or the REPL, which is what aichat is built for, and none of them needs an Android
   intent. Cost to the home-screen process: none.
2. **A share-sheet path for use case 1, and for sending an image or file from another app, that
   does the audio plumbing the terminal cannot**: the receiver exists and already accepts
   `audio/*`; what is missing is a step after it. The smallest version is not new UI: a
   launcher-shipped `termux-file-editor` handler (a script) that runs ffmpeg, calls the engine and
   shows the English text as a launcher notice. It stays inside the terminal world and costs no
   Views code. The larger version is a small native result card; only build that if the script
   version proves the quality is worth it.

The earlier runner-up (a native Views chat surface) drops further down: none of the five use cases
needs a chat UI that a terminal does not already give; only use case 1 needs something an intent can
reach, and the receiver covers that.

The one decision that gates use case 1 is quality, not frontend: run a handful of real Arabic notes
through (a) Whisper `language=ar` then Gemma text translation and (b) Gemma `-audio` directly, on pong,
before any code. If neither is usable, the feature is not buildable with today's models and no
frontend changes that.

## Open questions only the developer can answer

New after the use cases:

A. How good must a translated Arabic voice note be to count? WhatsApp notes are often longer than
   30 s and often dialect; is a rough gist enough, or does it have to be quotable?
B. Is a script on the existing receiver (`termux-file-editor`) acceptable as the first version of the
   voice-note path, or should it be a native in-app result card from day one?
C. May the context window rise on pong for these tasks (8192 costs about 0.8 GB more for E4B on
   its GPU per `TaiContextWindowPolicy.java:21-24`), or is 4096 with chunking the rule?
D. Should the notification history become a model-callable tool (`LauncherToolRegistry`), given the
   privacy stance that model requests cannot read `~/.launcherctl`?
E. Which spreadsheets matter (xlsx, csv, Google Sheets exports)? That decides the loader.

Earlier questions:

1. Is the audience a terminal user (aichat is enough) or someone who should talk to the
   assistant from the home screen without opening a shell?
2. Should voice conversations (STT, model, TTS) have a UI of their own? That pushes toward the
   native Views surface; a TUI would need to drive `/v1/audio/*` itself.
3. Is a tlstore item the acceptable way to ship aichat, or should the launcher bundle a client
   (bundling adds a per-ABI binary and licence notices to every edition)?
4. Should the frontend be allowed to act (function calling reaching `launcherctl` tools,
   `LauncherToolRegistry`), and with what confirmation? That decision shapes any candidate.
5. Does Gemma 4 E2B, on pong's tier, hold tool-calling reliably enough that the agent features
   of gptme or aichat are worth turning on? Not measured here.

## What I did not verify

Running aichat, gptme or oterm against the launcher's endpoint on pong; the PSS cost of a
WebView or Compose in this process; Termux's current Python minor version against
Open WebUI's `<3.13` pin; whether llama.cpp's UI can target a foreign server.

## Sources

1. https://github.com/sigoden/aichat (releases API: v0.30.0, 2025-07-06; pushed 2026-02-23, via `gh api repos/sigoden/aichat`)
2. https://github.com/sigoden/aichat/blob/main/README.md (OpenAI-compatible providers, REPL, function calling, `--serve` and playground)
3. https://github.com/termux/termux-packages/blob/master/packages/aichat/build.sh (version 0.30.0, built with cargo)
4. https://github.com/gptme/gptme (v0.36.0, MIT)
5. https://github.com/termux/termux-packages/tree/master/packages (no `llm`, `oterm`, `gptme`, `open-webui` entries; `aichat` and `goose` present, checked via the contents API)
6. https://github.com/ggozad/oterm (0.26.1, MIT)
7. https://github.com/ggozad/oterm/blob/main/pyproject.toml (requires-python >=3.11, pinned dependencies)
8. https://github.com/simonw/llm (0.37, Apache-2.0)
9. https://github.com/simonw/llm/blob/main/docs/other-models.md (`extra-openai-models.yaml`, `api_base`)
10. https://github.com/simonw/llm/blob/main/pyproject.toml
11. https://github.com/ggml-org/llama.cpp and https://github.com/open-webui/open-webui (releases API)
12. https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md ("a web UI to interact with llama.cpp")
13. https://github.com/open-webui/open-webui/blob/main/LICENSE (clause 4, branding)
14. https://github.com/open-webui/open-webui/blob/main/pyproject.toml (`requires-python >= 3.11, < 3.13.0a1`)
15. https://developer.android.com/develop/ui/compose/setup (Kotlin is the only language for Compose; API 21+)
16. https://developer.android.com/develop/ui/views/layout/webapps/managing-webview (multiprocess renderer, system may kill it for memory)
17. https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm (model card: 32k context, 16 kHz mono WAV example, audio and image on the NPU variant)
18. https://ai.google.dev/gemma/docs/core/model_card_4 (128K context, 30 s audio, ASR and speech translation, CoVoST, FLEURS, LiveCodeBench v6)
19. https://github.com/sigoden/aichat/blob/main/src/config/mod.rs (`setup_document_loaders`: pdf via pdftotext, docx via pandoc)
20. https://github.com/sigoden/aichat/blob/main/config.example.yaml (`document_loaders` syntax)
21. https://github.com/termux/termux-packages/tree/master/packages (ffmpeg built with libopus; poppler, pandoc, miller, opus-tools, sox present; checked per path via the contents API. Correction to source 5: that listing call is capped at 1000 entries, so only the direct per-package checks are reliable. `llm`, `oterm`, `gptme` returned 404 on their direct paths)
22. https://github.com/sigoden/aichat/blob/main/src/config/input.rs (`IMAGE_EXTS`, loaders; no audio handling)
