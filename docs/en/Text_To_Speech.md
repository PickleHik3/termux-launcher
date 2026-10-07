# Text to speech

Termux Launcher can read text aloud with a voice model that runs on the phone. You can hear a
terminal selection with **Read aloud**, have a script speak with `tai speak`, or get the audio from
the OpenAI-compatible `/v1/audio/speech` route. No text leaves the device.

## Quick start

1. Open **Settings → On-device AI → Model centre**, go to **Speech**, and
   install **KittenTTS Nano 0.8** under **Voice output** (about 90 MB).
2. Long-press text in the terminal to select it, then tap **Read aloud** in the selection toolbar.
3. Or, in a shell:

   ```sh
   tai speak "The build finished."
   ```

## The voice model

The voice model is **KittenTTS nano 0.8**. It speaks English only and is small enough for phones with
4 GB of RAM. It runs on the CPU in the launcher's `:tai_runtime` process, loads when something first
asks it to speak, and never waits behind a chat generation.

It comes with four voices:

| Voice | |
| --- | --- |
| **Bruno** | male |
| **Hugo** | male |
| **Jasper** | male, the default |
| **Rosie** | female |

Choose the voice and speed in **Settings → Keyboard → Voice input → Speech model**, under **Voice
output**. **Voice settings**, in the installed voice model's menu in Model centre, opens the same
place.

- **Voice**: one of the four above.
- **Speed**: 0.75× to 1.75×. 1.0× is the voice's own pace, as its authors tuned it.
- **Play sample**: hear the chosen voice and speed. Tap it again to stop.

These are the defaults for Read aloud, and for `tai speak` and the API when a request names no voice
or speed.

Text is read a sentence at a time. The first sentence is spoken while the next one is computed, so
the voice starts after one short sentence however long the text is. Dates, years and similar
written forms are expanded into words first. A line break in the middle of a sentence, as in a
wrapped terminal line, is read as a space; a blank line ends a sentence.

The voice plays at the media volume. Music and videos dip under it instead of stopping. A
notification sound or a ringing call pauses the reading until it is over, and another app starting
to play stops it.

## Read aloud in the terminal

Select text in the terminal and tap **Read aloud** in the selection toolbar. While it is reading,
the same place offers **Stop reading**. Tapping it, from any selection, stops the reading.

**Read aloud** appears only when the voice model is installed. A selection can be up to 20,000
characters.

## `tai speak`

```sh
tai speak "Tests passed."
tai speak --voice Rosie --speed 1.2 "Deploy is done."
make 2>&1 | tail -n 3 | tai speak
tai speak --out summary.wav "Saved for later."
tai speak --stop
```

| Option | What it does |
| --- | --- |
| `--voice Bruno\|Hugo\|Jasper\|Rosie` | The voice. Without it, the voice from **Voice output**. |
| `--speed N` | A number such as `1.2`, from 0.5 to 2.0. Without it, the speed from **Voice output**. |
| `--whole` | Collects all the text first and speaks it in one go, instead of sentence by sentence. |
| `--out file.wav` | Saves the audio as a WAV file instead of playing it, and prints `Saved file.wav`. |
| `--stop` | Stops whatever the phone is reading aloud, from any shell. |

The text comes from the command line, or from standard input when none is given. It can be up to
20,000 characters. `tai speak` returns once the text has been heard, and prints a summary:

```text
Spoke 3 sentences (4.2 s) as Jasper; first sound after 0.9 s.
```

"First sound after" is how long it took from the command to the first audio. Ctrl-C stops the voice
as well as the command, and a stopped reading prints `Stopped after 1 sentence.` Add `--json` before
`speak` (`tai --json speak …`) for the raw answer.

### Speaking as an agent talks

```sh
claude -p "Summarise today's commits" | tai speak
some-agent | tai speak --voice Rosie
```

When text is piped in, `tai speak` reads it as it arrives: each sentence plays as soon as it is
complete, so you hear the start of a long answer while the rest is still being written. Colour
codes and Markdown marks (`**`, backticks, `#` headings, list bullets) are dropped, so they are not
read out. Ctrl-C stops the voice and the command. Add `--whole` to collect everything first and
speak it as one piece, or `--stream` to stream from a shell that reports no pipe.

## The API: `/v1/audio/speech`

`POST /v1/audio/speech` takes OpenAI's request body:

| Field | Meaning |
| --- | --- |
| `input` | The text, up to 4,096 characters. Required. |
| `voice` | `Bruno`, `Hugo`, `Jasper` or `Rosie`, in any case. OpenAI voice names are accepted too: `alloy`, `sage` and `verse` are Jasper; `echo`, `onyx` and `ballad` are Bruno; `fable` and `ash` are Hugo; `nova`, `shimmer` and `coral` are Rosie. Omitted: the **Voice output** voice. |
| `speed` | 0.5 to 2.0. Omitted: the **Voice output** speed. |
| `response_format` | `wav` (the default) or `pcm`. `mp3`, `opus`, `aac` and `flac` are refused. |
| `model` | Optional; any OpenAI speech model name is accepted. |

`wav` returns the whole file once synthesis ends. `pcm` streams raw 24 kHz, 16-bit signed
little-endian mono audio sentence by sentence, so a client can start playing after the first
sentence.

```sh
endpoint="$(cat ~/.launcherctl/endpoint)"
token="$(cat ~/.launcherctl/token)"
curl -sS -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  -d '{"input":"The build finished.","voice":"Rosie","speed":1.1,"response_format":"wav"}' \
  "$endpoint/v1/audio/speech" -o hello.wav
```

This route returns audio and plays nothing on the phone. To play on the phone from a script, use
`tai speak` (its route, `POST /v1/ai/speak`, is listed in the
[LauncherCtl API reference](LauncherCtl_API.md)).

## Installing and removing the voice model

The voice model is in **Model centre → Speech → Voice output**. It downloads like any other model and
can be paused and resumed. Deleting it from Model centre removes **Read aloud** from the selection
toolbar, and `tai speak` and `/v1/audio/speech` answer `tts_model_not_installed` until you download it
again.

## Troubleshooting

- **No Read aloud in the selection toolbar.** Install the voice model.
- **`tts_model_not_installed`.** No voice model is installed; get it from **Model centre → Speech →
  Voice output**.
- **`tts_audio_busy`.** A call or another app holds the phone's audio. Try again in a moment.
- **`tts_voice_not_found`.** Use one of the four voices or an OpenAI voice name. The model file holds
  other voices, but they are deliberately not offered.
- **`tts_input_too_long`.** `/v1/audio/speech` takes up to 4,096 characters; split the text, or use
  `tai speak`, which takes up to 20,000.
- **`tts_files_missing` or `tts_load_failed`.** The download is incomplete or damaged. Delete the
  voice model in Model centre and download it again.
- **Nothing is heard.** Check the media volume; the voice follows it, not the ringer volume.
- **Non-English text sounds wrong.** The voice model reads English only.

To turn speech into text instead, see [Voice input](Voice_Input.md). For the rest of the local API,
see [On-device AI](On_Device_AI.md).
