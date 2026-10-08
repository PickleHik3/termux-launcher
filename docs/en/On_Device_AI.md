# On-device AI

On-device AI runs AI models on the phone itself and serves them to the launcher's own features
(voice typing, tidy dictation, read aloud, app categories) and to any app or command-line tool that
speaks the OpenAI or Ollama API. This page covers the settings, the Model centre and the `tai`
command. The models themselves are on [On-device AI models](On_Device_AI_Models.md).

The short version:

- Models run on this phone, in a separate `:tai_runtime` process, so a crash in a model never takes
  the launcher down with it. Prompts and replies stay on the phone unless you set up a
  [remote model](#remote-model) or turn on LAN access.
- No model is inside the APK. You download or import the ones you want.
- Two runtimes are built in: LiteRT-LM and MNN. GGUF and other raw weight files are not supported.
- Apps reach it through a local, token-protected endpoint. The command-line tool is `tai`.

## Where it lives

Open **Settings → On-device AI**. It sits in the **Workspace** group, after **Display**, with the
summary "Models, voice and local API". The page has five groups:

- **Runtime**: **Runtime status**, with the loaded model and quick actions.
- **Models**: **Model centre**, **Models for this phone**, **Benchmark** ("Which models this
  phone runs well") and **Hugging Face token**.
- **Remote model**: a model on OpenAI, OpenRouter or your own server. See [Remote model](#remote-model).
- **Server**: **Model autoload**, **OpenAI endpoint**, **Require API token** and **LAN access**.
- **Advanced**: **Parameters**, the global overrides (such as **Idle unload**, 10 minutes by
  default), **Memory limits** and **Share diagnostics log**. See
  [Memory](On_Device_AI_Models.md#memory).

## Models for this phone

The **Models for this phone** card shows what fits this phone and downloads it in one go. It
appears once on the home screen, after the tour or on a later quiet moment; reopen it any time from
the **Models for this phone** row.

- The header names the phone: "Tier 2 · 12 GB · chip · Android 15" (see
  [Device tiers](#device-tiers-and-what-automatic-picks)).
- Each row is a feature with a tick: **Voice typing**, **Read aloud**, **Assistant and tidy
  dictation** and **Dawn notes integration**, as far as they fit.
- **Download selected** starts the ticked downloads. **Wi-Fi only** holds them until you are on
  Wi-Fi. **Later** closes the card.

## Model centre

**Settings → On-device AI → Model centre** is where every model is got, installed and assigned. The
header repeats the phone's tier line. Three segments sit under it:

- **Functions**: what each launcher feature uses. See [Functions](#functions).
- **Installed**: every model on the phone. Each row shows a backend pill (**LiteRT** or **MNN**),
  a "Used by:" line naming the functions it serves, and an overflow menu.
- **Get models**: the catalogue, grouped under **Assistants**, **Speech**, **Voice output** and
  **Search**. The import bar sits at the top.

The import bar takes a Hugging Face link: paste it into **Paste a Hugging Face link…** and tap
**Add**, or tap **File** to pick a model file on the phone. **More ways to add a model → Add a
model folder (MNN)** imports an MNN model folder. Details are in
[Importing and downloading](On_Device_AI_Models.md#importing-and-downloading).

On the Installed segment a chat model's overflow menu has **Load model**, **Use for…**,
**Parameters**, **Benchmark** and **Delete**. Speech, voice output, embedding and image models have
a shorter menu. Deleting a model that a function uses warns you first ("In use by …") and says what
each function falls back to.

## Functions

The **Functions** segment has one row per launcher feature:

| Function | What it does |
| --- | --- |
| **Assistant and endpoint** | The default chat model, for the endpoint and the apps that use it |
| **Voice typing** | Turns speech into text for [voice input](Voice_Input.md) |
| **Tidy dictation** | Cleans up a dictation once you stop |
| **Read aloud** | Speaks text for [text to speech](Text_To_Speech.md) |
| **App categories** | Sorts the app drawer into categories |
| **Dawn notes integration** | Lets Dawn notes find notes by meaning |

Tap a row to open its picker. It has up to six sections:

- **Automatic**: the pick for this phone's tier, with "If it can't load:" naming the fallbacks.
- **On this phone**: each installed model that can serve the function.
- **Remote**: the remote model, shown as "Remote · model name", or **Set up a remote model**.
- **Without a model**: **Raw text** for Tidy dictation, **Off** for App categories.
- **Get a model**: catalogue models that would serve it, with a **Get** button.
- **Settings**: extras for that function. **Runs on** chooses **GPU** or **CPU**, with **Answers
  look wrong? Use the CPU** and **Try the GPU again**. Tidy dictation has **Cleanup level**. Read
  aloud has **Voice** and **Speed**. Voice typing has **Voice typing window**.

From the Installed segment, **Use for…** on a model does the same from the other side: it lists the
functions that model can serve.

## Device tiers and what Automatic picks

The launcher puts the phone in a tier by its RAM. Swap and "virtual RAM" do not count.

| | Tier 1 (6 GB or less) | Tier 2 (8 to 12 GB) | Tier 3 (16 GB or more) |
| --- | --- | --- | --- |
| Assistant and endpoint | none | Gemma 4 E2B | Gemma 4 E4B (E2B when there is no GPU path) |
| Voice typing | Whisper Base | Whisper Small | Whisper Small |
| Tidy dictation | Raw text | Gemma 4 E2B | Gemma 4 E2B |
| Read aloud | KittenTTS Nano 0.8 | KittenTTS Nano 0.8 | KittenTTS Nano 0.8 |
| App categories | Off | Gemma 4 E2B | Gemma 4 E4B |
| Dawn notes integration | EmbeddingGemma 2 Text 270M | EmbeddingGemma 2 Text+Vision 440M | EmbeddingGemma 2 Text+Vision 440M |

Whisper picks the English-only file when the phone is set to English. When the automatic pick is
not installed, the function falls back down its chain: for example, Tidy dictation tries E4B next
and then raw text, and Dawn notes integration takes the other EmbeddingGemma 2 file and then
EmbeddingGemma 300M.

## Connect an AI app

The **OpenAI endpoint** row (in the **Server** group) opens the **Endpoint & access** dialog: the
**OpenAI base URL**, the **Bearer token** with **Copy** and **Reveal**, **Recreate token** and
**Randomize port**. The same two values are written to files every time the launcher starts:

```text
~/.launcherctl/endpoint
~/.launcherctl/token
```

The address looks like `http://127.0.0.1:54298`. OpenAI-compatible clients need `/v1` added;
Ollama-compatible clients use the address as it is. Read both at call time rather than copying the
token into a config file:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Use `/v1/responses` for Codex-style clients and `/v1/chat/completions` for other OpenAI clients.
With **Model autoload** on (the default), the first request for an installed model loads it after a
clean safety check. The full route list is in [LauncherCtl API](LauncherCtl_API.md).

## Using with aichat

Install `aichat`, set it up as an OpenAI-compatible client with the two exports above, and pick a
model id from `tai models`, for example:

```text
gemma-4-e2b-it-litert-lm
```

The first request loads the model if it is installed and not yet loaded.

## Remote model

**Settings → On-device AI → Remote model** connects a model on another server, with your own key.
Functions can then pick it ("Remote · model name"), and **Polished** dictation cleanup uses it
whenever it is set up.

- **Server address**: presets for **OpenAI**, **OpenRouter** and **Server on this phone or LAN**.
  Plain `http://` is allowed only for this phone and your own network.
- **API key**: stored encrypted on the phone. A server on this phone or your network usually needs
  none.
- **Model**: picked from the server's list, or typed when the server does not list its models.
- **Understands images**: checked automatically where possible, or set by you.
- **When to use it**: **Prefer remote**, or **Only when no local model fits**.
- **Test connection** sends one short message and times the answer. **Remove** forgets the address,
  key and model.

Text you send to a function that uses the remote model leaves the phone for that server.

## Status bar indicator

Any status bar that can run a shell command on a timer can show whether a model is loaded, the
launcher's own [status bar](Status_Bar.md) included. These are the glyphs it reports with.

Loaded:

```text
󱜙
```

Unloaded:

```text
󱚡
```

While a model is loaded the widget can also show the remaining keep-warm or idle time. After the
model unloads, the unloaded icon disappears after a short timeout.

## Security notes

- The endpoint listens on `127.0.0.1` only, for apps and tools on this phone.
- Requests carry the token as `Authorization: Bearer <token>` or `X-Api-Key: <token>`.
- **Require API token** (on by default) can be turned off so local tools need no real key. `GET /`
  and `OPTIONS` never need the token.
- **LAN access** opens the endpoint to your local network over unencrypted HTTP. It always requires
  the token, whatever **Require API token** says, and turns itself off after 12 hours, rotating the
  token as it does.
- Treat the token like a password. If it leaks, open **OpenAI endpoint** and tap **Recreate
  token**, then update any tool that stored the old one.

## Useful `tai` commands

`tai` manages models; it is not a chat program. Put `--json` (or `-j`) first for raw JSON, as in
`tai --json models`.

```text
tai status                         is the runtime up, and what is loaded
tai runtime [--clear-history]      the runtime's state and its recent history
tai logs [--lines N] [--clear]     loads, evictions and failures
tai models                         installed models and what each can do
tai import <path> [model-id]       add a model file or folder from the phone
tai download <model-id> <https-url> --accept-terms
tai downloads                      download progress
tai download-pause|-resume|-now|-cancel <model-id>
tai delete <model-id>
tai preflight [model] [--auto|--cpu|--gpu]
tai load [model] [--auto|--cpu|--gpu] [--fresh]
tai unload
tai keep-warm [model] [--minutes N] [--auto|--cpu|--gpu]
tai cancel                         stop the running generation or benchmark
tai benchmark [model...] [--preset quick|standard] [--cpu|--gpu] [--compare]
tai benchmark --results | --clear [model] | --skip-wait
tai transcribe <file.wav>          see Voice input
tai speak [text] | tai speak --stop   see Text to speech
tai image "prompt" | tai image --stop see On-device AI models
tai doctor                         check the whole setup
```

`tai --help` prints every option. `tai transcribe` is described in
[Voice input](Voice_Input.md#from-the-terminal-tai-transcribe), `tai speak` in
[Text to speech](Text_To_Speech.md#tai-speak), and `tai benchmark` and `tai image` in
[On-device AI models](On_Device_AI_Models.md#benchmark).

If something does not work, see
[On-device AI problems](Launcher_Troubleshooting.md#on-device-ai-does-not-start-or-a-client-cannot-connect).

## More details

- [On-device AI models](On_Device_AI_Models.md): the catalogue, importing, loading, memory,
  benchmark and image generation.
- [On-device AI backends](On_Device_AI_Backends.md): runtime internals and the capability matrix.
- [LauncherCtl API](LauncherCtl_API.md): every HTTP route, rate limit and error code.
