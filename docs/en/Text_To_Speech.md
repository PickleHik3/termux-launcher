# Text to speech

Termux Launcher can read text aloud with a voice model that runs on the phone. Hear a terminal
selection with **Read aloud**, have a script or an agent speak with `tai speak`, or get the audio
from the OpenAI-compatible `/v1/audio/speech` route. No text leaves the phone.

## Quick start

1. Open **Settings → On-device AI → Model centre → Get models → Voice output** and install
   **KittenTTS Nano 0.8** (about 90 MB). The **Read aloud** row on the **Functions** segment
   offers it too.
2. Long-press text in the terminal to select it, then tap **Read aloud** in the selection toolbar.
3. Or, in a shell:

   ```sh
   tai speak "The build finished."
   ```

## The voice model

The voice model is **KittenTTS Nano 0.8**. It speaks English only and is small enough for phones
with 4 GB of RAM. It runs on the CPU in the launcher's `:tai_runtime` process, loads when something
first asks it to speak, and never waits behind a chat generation.

It has four voices: **Bruno**, **Hugo** and **Jasper** (male; Jasper is the default) and **Rosie**
(female). Choose the voice and speed in any of these places; they share one setting:

- **Settings → Keyboard → Voice input → Speech model**, under **Voice output**. **Voice settings**
  in the installed voice model's menu in the Model centre opens the same place.
- **Model centre → Functions → Read aloud**, under **Voice** and **Speed**.
- The voice button on the Read aloud card, while it reads.

**Speed** runs from 0.75× to 1.75×; 1.0× is the voice's own pace. **Play sample** plays the chosen
voice and speed; tap it again to stop. These are the defaults for Read aloud, and for `tai speak`
and the API when a request names no voice or speed.

Text is read a sentence at a time, the next one prepared while the current one plays, so the voice
starts after one short sentence however long the text is. Dates, years and similar forms are read
as words. A line break inside a sentence, as in a wrapped terminal line, is read as a space; a
blank line ends a sentence.

The voice plays at the media volume. Music and videos dip under it instead of stopping. A
notification sound or a ringing call pauses the reading until it is over, and another app starting
to play stops it.

## Read aloud in the terminal

Select text in the terminal and tap **Read aloud** in the selection toolbar. It appears only when
the voice model is installed, and takes up to 20,000 characters.

The reading opens in a card like the [dictation card](Voice_Input.md#the-dictation-card), in the
same corner:

- The header says **Reading**, with "· preparing" until the first sound, then "· 3 of 12" as the
  sentences go by; **Paused** while paused; **Done** at the end, and then the card closes.
- The text shows the sentence being heard in the accent colour and what was already read dimmed,
  and scrolls along by itself.
- **Pause** and **Resume** stop and continue in place, mid-sentence.
- The voice button ("Voice: Jasper. Choose another") picks one of the four voices. The choice
  becomes the default and is heard from the next sentence.
- **Stop**, **×**, a sideways swipe, or **Stop reading** in the selection toolbar stop the reading
  and close the card.
- Drag the card's strip to move it, and double-tap the strip to put it back in the corner, as with
  the dictation card.

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
| `--voice Bruno\|Hugo\|Jasper\|Rosie` | The voice. Without it, the default voice. |
| `--speed N` | A number such as `1.2`, from 0.5 to 2.0. Without it, the default speed. |
| `--whole` | Collects all the piped text first and speaks it in one go. |
| `--stream` | Streams piped text sentence by sentence even from a shell that reports no pipe. |
| `--out file.wav` | Saves the audio as a WAV file instead of playing it, and prints `Saved file.wav`. |
| `--stop` | Stops whatever the phone is reading aloud, from any shell. |

The text comes from the command line, or from standard input when none is given, up to 20,000
characters. `tai speak` returns once the text has been heard and prints a summary:

```text
Spoke 3 sentences (4.2 s) as Jasper; first sound after 0.9 s.
```

Ctrl-C stops the voice as well as the command, and a stopped reading prints
`Stopped after 1 sentence.` Put `--json` first (`tai --json speak …`) for the raw answer.

### Speaking as an agent talks

```sh
claude -p "Summarise today's commits" | tai speak
some-agent | tai speak --voice Rosie
```

Piped text is read as it arrives: each sentence plays as soon as it is complete, so you hear the
start of a long answer while the rest is still being written. Colour codes and Markdown marks
(`**`, backticks, `#` headings, list bullets) are dropped so they are not read out.

## The API: `/v1/audio/speech`

`POST /v1/audio/speech` takes OpenAI's request body:

| Field | Meaning |
| --- | --- |
| `input` | The text, up to 4,096 characters. Required. |
| `voice` | `Bruno`, `Hugo`, `Jasper` or `Rosie`, in any case. OpenAI voice names work too: `alloy`, `sage` and `verse` are Jasper; `echo`, `onyx` and `ballad` are Bruno; `fable` and `ash` are Hugo; `nova`, `shimmer` and `coral` are Rosie. Omitted: the default voice. |
| `speed` | 0.5 to 2.0. Omitted: the default speed. |
| `response_format` | `wav` (the default) or `pcm`. `mp3`, `opus`, `aac` and `flac` are refused. |
| `model` | Optional; any OpenAI speech model name is accepted. |

`wav` returns the whole file once synthesis ends. `pcm` streams raw 24 kHz, 16-bit signed
little-endian mono audio sentence by sentence, so a client can start playing after the first.

```sh
curl -sS -H "Authorization: Bearer $(cat ~/.launcherctl/token)" -H 'Content-Type: application/json' \
  -d '{"input":"The build finished.","voice":"Rosie","speed":1.1}' \
  "$(cat ~/.launcherctl/endpoint)/v1/audio/speech" -o hello.wav
```

This route returns audio and plays nothing on the phone. To play on the phone from a script, use
`tai speak`, whose route `POST /v1/ai/speak` is in the [LauncherCtl API](LauncherCtl_API.md).

## Installing and removing the voice model

The voice model is in **Model centre → Get models → Voice output**. It downloads like any other
model and can be paused and resumed. Deleting it removes **Read aloud** from the selection toolbar,
and `tai speak` and `/v1/audio/speech` answer `tts_model_not_installed` until you download it again.

When something goes wrong, see
[Text to speech problems](Launcher_Troubleshooting.md#text-to-speech-is-silent-or-refuses). To turn
speech into text instead, see [Voice input](Voice_Input.md).
