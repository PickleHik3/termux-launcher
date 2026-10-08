# On-device AI models

This page is about the models themselves: which ones the catalogue offers, how to add others, how a
model gets loaded and what it costs in memory, how to measure one with the benchmark, and how to
make pictures with an image model. The settings and the Model centre are on
[On-device AI](On_Device_AI.md).

## The catalogue

**Settings → On-device AI → Model centre → Get models** lists the catalogue in four groups. The
catalogue is kept short on purpose; any other LiteRT-LM or MNN model (Qwen, DeepSeek, FunctionGemma,
other embedders) still runs when you add it by link or file.

| Model | Group | Download | Suggested RAM | Licence | Notes |
| --- | --- | ---: | ---: | --- | --- |
| Gemma 4 E2B IT | Assistants | 2.4 GB | 8 GB+ | Apache-2.0 | Recommended chat model. Text, images, audio and tools. Not gated |
| Gemma 4 E4B IT | Assistants | 3.7 GB | 12 GB+ | Apache-2.0 | Better at code and reasoning, slower. Not gated |
| Whisper ACFT Base / Base (English) | Speech | 97 MB | 6 GB+ | Apache-2.0 | Voice typing, many languages or English only |
| Whisper ACFT Small / Small (English) | Speech | 273 MB | 8 GB+ | Apache-2.0 | Voice typing, more accurate |
| Parakeet TDT 0.6B v3 | Speech | 586 MB | 8 GB+ | CC-BY-4.0 | Voice typing in 25 European languages, detected automatically |
| KittenTTS Nano 0.8 | Voice output | 90 MB | 4 GB+ | Apache-2.0 | Read aloud, English, four voices |
| EmbeddingGemma 2 Text+Vision 440M | Search | 388 MB | 4 GB+ | Apache-2.0 | Recommended embedder. Not gated |
| EmbeddingGemma 2 Text 270M | Search | 165 MB | 4 GB+ | Apache-2.0 | Smaller embedder, picked on Tier 1 phones |
| EmbeddingGemma 300M | Search | about 520 MB | 4 GB+ | Gemma | The older embedder. Gated: needs a Hugging Face token |

Every catalogue row shows **License: <id>** before you download. **Gemma** links to Google's
Gemma terms and **CC-BY-4.0** to the Creative Commons page. There is no separate accept dialog in
the Model centre; only `tai download` asks for `--accept-terms`.

The embedders serve search, not chat. The **Dawn notes integration** function uses EmbeddingGemma 2
so Dawn notes can find notes by meaning. The EmbeddingGemma 2 files are single `.litertlm` files
with the tokenizer inside, read up to 2048 tokens, take text only for now, and only one LiteRT
embedder is loaded at a time.

## Model capabilities

Every installed model says what the local endpoint can accept. `tai models` lists them, and
`/v1/models` gives the full machine-readable list:

```sh
curl -sS -H "Authorization: Bearer $(cat ~/.launcherctl/token)" \
  "$(cat ~/.launcherctl/endpoint)/v1/models" | jq .
```

| Capability | Meaning |
| --- | --- |
| `text_chat` | Normal text prompts and replies |
| `image_input` | Images can be part of a prompt |
| `audio_input` | Audio can be part of a prompt |
| `tool_use` | The model can return structured tool calls |
| `text_embeddings` | Turns text into vectors instead of chat text |
| `code` | Tuned for programming |
| `reasoning` | Meant for multi-step reasoning |
| `multilingual` | Meant for more than one language |
| `llm_thinking` | Supports the backend's thinking mode |
| `speculative_decoding` | A LiteRT model with the runtime flag, or an MNN package with an EAGLE-3 draft head |
| `mobile_actions` | Tuned to choose from Android action tools |

`_capabilities` in `/v1/models` is the field apps should read. A model with `speculative_decoding`
has a **Speculative decoding** switch in its **Parameters**. For MNN it is off by default: on a
phone it measured slower than plain decoding and its text differed slightly.

### Images and audio

A multimodal LiteRT model, such as Gemma 4, is offered on the API as up to three ids that share one
file:

- `model-id` for text (the default id)
- `model-id-vision` for image input
- `model-id-audio` for audio input

Use the id that matches what you send. Only one mode is loaded at a time, which saves memory. The
model's **Parameters → Endpoint exposure** changes this: **Combined** serves every modality from
the bare id alone at a higher memory cost; **Both** keeps that combined id and adds the split ids,
with the text-only one as `model-id-text`.

### FunctionGemma and tool calls

FunctionGemma 270M Mobile Actions is not in the catalogue but runs when imported, as
`functiongemma-270m-mobile-actions-litert-lm`, on the CPU only. It returns tool calls for the client
to carry out; On-device AI never runs Android actions or shell commands itself.

## Importing and downloading

### From the Model centre

- **A catalogue model**: tap it under **Get models**. Whisper opens a sheet where you choose the
  size, the language and the window; the others start at once.
- **A Hugging Face link**: paste a repository link into **Paste a Hugging Face link…** at the top of
  **Get models** and tap **Add**. When the repository has several files, **Which version?** lists
  them, selecting the one the model card recommends or the largest portable build that fits the
  phone. You then name the model, tick **What it can do** and choose where it **Runs on**, and tap
  **Add model**.
- **A file on the phone**: tap **File** and pick a `.litertlm`, `.task` or `.tflite` file. It is
  copied into the app's private model storage.
- **An MNN folder**: **More ways to add a model → Add a model folder (MNN)** takes a folder with
  `config.json` and its other files.

GGUF, safetensors, PyTorch, ONNX and other raw weight files are refused: there is no llama.cpp
runtime in the app.

### Gated models and the Hugging Face token

Some models need you to accept their terms on Hugging Face and sign in. Open the model's page on
huggingface.co, accept its terms, then create a token: a classic **Read** token, or a fine-grained
token with `Contents: Read`, is enough. Save it in **Settings → On-device AI → Hugging Face token**.
The token is sent only to Hugging Face download addresses.

You rarely have to go looking for that row. A gated catalogue row, and a download Hugging Face
refused, show **Add token**. A link import that needs signing in shows **Sign in needed** with
**Add my token** and **Open the model page**.

### Downloads

- **Simultaneous downloads** in the Model centre sets how many run at once, 1 to 3, two by default
  ("Two usually fill a home connection; more only split it"). The rest wait in line; **Start now**
  moves one to the front.
- **Pause** keeps the partial file and **Resume** carries on from it. A download interrupted by the
  app closing or the network dropping shows as paused, continues by itself on Wi-Fi, and waits for
  a tap on mobile data.
- **Cancel** deletes the partial file.
- Before a download starts, the free space must cover what is left plus a reserve of 500 MB or 5%
  of the storage, whichever is more.

From a shell: `tai downloads`, `tai download-pause`, `tai download-resume`, `tai download-now` and
`tai download-cancel` with a model id, and `tai import <path> [model-id]` for a file or folder
already on the phone.

## Supported model names

Use the exact id `tai models` prints. The catalogue ids are:

```text
gemma-4-e2b-it-litert-lm
gemma-4-e4b-it-litert-lm
whisper-acft-base       whisper-acft-base-en
whisper-acft-small      whisper-acft-small-en
parakeet-tdt-0.6b-v3
kittentts-nano-0.8
embeddinggemma-2-text-vision-440m
embeddinggemma-2-text-270m
embeddinggemma-300m
```

An imported model keeps the name you gave it. `gemma-4-e2b-it-litert-lm` is the default assistant
on Tier 2 phones and `gemma-4-e4b-it-litert-lm` on Tier 3.

## How loading works

You rarely need to load a model by hand. With **Model autoload** on, a request for a model:

- uses it if it is already loaded;
- loads it if it is installed and its safety check (the preflight) is clean;
- switches the chat slot to it if another chat model is loaded;
- fails with a clear error (`model_not_loaded`, `device_not_supported` or a preflight code) if the
  model is unknown, not installed, short of memory, or risky to load on the GPU automatically.

A loaded model stays warm until **Idle unload** (10 minutes by default) and is then unloaded. To
load or unload by hand:

```sh
tai load gemma-4-e2b-it-litert-lm
tai load gemma-4-e2b-it-litert-lm --cpu
tai unload
```

`tai load --fresh` throws away an MNN model's weight cache first; use it if an MNN model starts
replying with repeated characters.

## One active model

One chat model is loaded at a time: loading another replaces it. Speech, voice output and
embedding models are separate; they load on demand beside the chat model and never replace it.

Automatic loads try the GPU first and use the CPU after a GPU load has failed on this phone. Pick
one yourself with `tai load <model> --gpu` or `--cpu`, or with **Runs on** in a function's picker.

## Memory

A model is loaded to fit the memory free at that moment, so it does not crowd out the home screen
and the apps you are using.

- The context window grows with free memory. It halves until the load fits, down to 4096 tokens. A
  larger **Context window** setting (in **Advanced → Parameters**, or a model's own **Parameters**)
  is an upper limit, not a promise.
- If a GPU load does not fit even at 4096 tokens, the CPU is used instead: slower, but about half
  the memory.
- If neither fits, the load is refused with a short message. Close some apps and try again.
- **Memory limits** (in **Advanced**) sets how much is left free. **Relaxed**, the default, keeps
  enough free for the phone to stay responsive while a model loads. **Unrestricted** loads a model
  that does not fit anyway and lets Android close apps in the background to make room; the phone
  may slow down while it loads.
- If the phone runs low on memory while a model is loaded, it is unloaded; the next request loads
  it again.
- `/v1/models` reports the window a load would get right now; `tai --json runtime` shows the
  window the loaded model was given.

## Benchmark

**Settings → On-device AI → Benchmark** ("Which models this phone runs well") measures how chat
models actually run on this phone: choose models, check the battery, heat and screen, run, with a
cool-down between models and a result for each. An installed chat model also has **Benchmark** in
its overflow menu, which picks it for you. Once you have run one, installed chat rows show a speed
pill with their best tok/s.

Each model is loaded cold, answers one warm-up, and then runs three tests:

| Test | What happens | You read it as |
| --- | --- | --- |
| Chat | An everyday question, up to 320 tokens | **Starts replying in X s** and **writes N tok/s** |
| Long input | A build log of about 2000 tokens, then "What went wrong, in two sentences?" | **Reads a long page in X s**, and the peak **Memory** |
| Sanity | Three questions with known answers | Shown only when one fails: the model is **Broken** |

The verdict is one word per model and processor:

- **Smooth**: 12 tok/s or more, first token within 1.5 s, long page within 8 s.
- **Usable**: 6 tok/s or more, first token within 3 s, long page within 20 s.
- **Slow**: anything else. **Broken**: a sanity question failed.

A run will not start below 30% battery (unless charging) or when the phone is already warm. While
it runs, it pauses when the phone gets warm and stops if the phone gets hot or the battery drops
below 15% while not charging. While a benchmark runs, chat and load
requests are refused with `benchmark_running`; speech is not blocked but will disturb the numbers.

From a shell:

```sh
tai benchmark                                    # the default assistant, standard preset
tai benchmark gemma-4-e2b-it-litert-lm --preset quick
tai benchmark MODEL --compare                    # CPU and GPU, each as its own entry
tai benchmark --results                          # the leaderboard
tai benchmark --clear MODEL                      # forget one model's results (no model: all)
tai benchmark --skip-wait                        # end the cool-down now
```

`quick` runs each test once (about 1.5 minutes a model); `standard`, the default, runs them twice
and keeps the median (about 3 minutes). `--cpu` or `--gpu` benches that processor only. `--force`
skips the battery and heat check at the start. `--native` runs LiteRT-LM's own benchmark instead,
for comparing with Google AI Edge Gallery. `tai cancel` stops a run and keeps the finished phases.
Results are kept in the app's private storage, the last 20 per model and processor.

## Image generation

`tai image` makes a picture from a text prompt with an MNN diffusion model, on the GPU unless you
say `--cpu`. Three families are supported:

| Family | Sizes |
| --- | --- |
| Stable Diffusion 1.5 | 512x512 only |
| Taiyi (Chinese Stable Diffusion 1.5) | 512x512 only |
| Sana | multiples of 32, from 256 to 2048; can also edit a picture with `--image` |

There is no catalogue entry. Import one by pasting a Hugging Face repository link into the Model
centre (for example `https://huggingface.co/taobao-mnn/stable-diffusion-v1-5-mnn-opencl`), with
**Add a model folder (MNN)**, or with `tai import <folder>`. The app brings the tokenizer Stable
Diffusion packages need. Installed image models show under **Installed → Image generation**
("Draws pictures from a description"). They never appear in `/v1/models` and cannot be loaded with
`tai load`.

```sh
tai image "a lighthouse at dusk, oil painting" --out lighthouse.png
tai image "a red fox" --model MODEL --steps 25 --seed 7
tai image "make it winter" --model SANA_MODEL --image lighthouse.png --size 768x512
tai image --stop
```

- `--model` names an installed image model; `--model-dir` points at a model folder directly, and
  then a Taiyi folder must be named with `--type taiyi`.
- The picture is saved to `--out`, or `./tai-image-<time>.png`.
- `--steps`, `--seed`, `--size WxH` and, for Sana, `--cfg` tune the result.
- `--memory-mode 1` is fastest and keeps the model loaded, `2` balances, `0` saves memory; left out,
  the fastest mode that fits is used.
- The first load on a phone tunes the GPU and is slow; later loads reuse that work.
- A run cannot be stopped part-way: Ctrl-C or `tai image --stop` throws the result away when it
  finishes.

The HTTP route behind it, `POST /v1/ai/images/generations`, is in
[LauncherCtl API](LauncherCtl_API.md#post-v1aiimagesgenerations).
