# Voice input

Voice input turns speech into text on the phone. What you say collects in a small card, at the top
right until you move it; nothing is typed while you speak. When you stop, you decide what happens to
the text: type it once at the cursor, copy it, or throw it away.

There are two engines, chosen in **Settings → Keyboard → Voice input → Speech engine**.
**On-device** runs a speech model you download (Whisper or Parakeet) in the launcher's own AI
runtime, and the audio never leaves the phone. **Android system** hands the microphone to the
phone's own recognizer, which types its result straight away. Until you choose, the launcher uses
**Android system** and switches to **On-device** by itself once a speech model is installed.

## Quick start

1. Open **Settings → On-device AI → Model centre → Get models → Speech** (or the **Voice typing**
   row on the **Functions** segment) and install a speech model. See [Speech models](#speech-models).
2. Swipe up on the built-in keyboard's Enter key and speak. Allow the microphone the first time.
3. Tap **Pause** on the card, or just stop talking; after **Stop after silence** (10 s by default)
   the dictation stops by itself.
4. Tap **Insert** to type the text at the cursor, or **Copy**.

## Starting a dictation

- **The voice key** is the swipe up on Enter on the built-in [keyboard](Keyboard.md). A swipe
  starts a dictation; a second swipe pauses it. Swiping up and holding opens Android's **Choose
  speech recognizer** list instead. A dictation started here belongs to the keyboard: hiding the
  keyboard stops it.
- **The Dictate key**, `tool:voice.dictate` (**Dictate**), starts or stops a dictation on Home,
  Terminal and Display, with the keyboard up, down, or not the launcher's at all. It is not on the
  row out of the box; add it from **Settings → Keyboard → Terminal extra keys**.
- **The Android recognizer.** With **Speech engine** set to **Android system**, both keys open the
  phone's recognizer and its result is typed directly, without the card. The on-device engine also
  falls back to it, with a one-line notice, when it cannot start: no speech model, the microphone
  refused, or the runtime refusing the model.

Leaving the app stops listening. The text heard so far stays in the card.

## The dictation card

The card sits under the status bar, in the same corner on Home, Terminal and Display. It is whole
from the start, in three parts.

**The strip** on top holds:

- the state: **Listening…**, **Transcribing…**, **Cleaning up…**, then what became of the text:
  **Ready**, **Cleaned up** or **Kept as heard**. It is in the accent colour only while the
  microphone is open. A small ring turns while something loads or runs, and a few words follow:
  **· warming up** (the model is still loading; keep talking, recording has started),
  **· cleanup undone**, **· at the cursor**, **· to the clipboard**;
- a handle in the middle, where you grab the card;
- **×** ("Discard and close") on the right: it stops listening, throws the text away and closes
  the card. Swiping the card sideways does the same. × never means stop; use **Pause** for that.

**The text** shows up to seven lines, the oldest scrolling off the top (fewer where there is less
room). A soft bar stands in for a phrase while it transcribes. Text as heard, while a cleanup is
still to come or after undo, is *italic* and dimmer; cleaned text is upright.

**The control bar** is there from the start:

- **Pause**, carrying your voice's waveform, closes the microphone. Phrases still transcribing
  arrive, then the cleanup runs. The voice key and the Dictate key do the same;
- **Resume** listens again and carries on the same text;
- **Undo** takes back a cleanup or command formatting and turns into **Redo**;
- **Copy** puts the text on the clipboard;
- **Insert** ("Insert at the cursor") types the text once where the keyboard would type. It never
  presses Enter.

**Copy** and **Insert** work early too: the dictation stops and the press is carried out once the
text is final. The strip then says **Inserted** or **Copied**, and the card closes a moment later.
Nothing reaches the terminal while you dictate, and starting a new dictation while text is waiting
carries on from that text.

**Moving the card.** Drag the strip to put the card anywhere on the place; it stays below the top
bars and above the keyboard, and comes back there next time. Portrait and landscape each remember
their own spot. **Double-tap the strip** to put it back in the top right corner. Placed low, the
card grows upward.

### Where Insert types

In this order, on the place you are looking at:

1. whatever has taken the launcher keyboard's typing: the command palette, the drawer's search, a
   rename field, an open sheet, or on Display, the display itself;
2. on **Display**, the Linux display, even with no launcher keyboard up (copied instead if no X
   server is connected);
3. a focused text field of the launcher's own, such as a widget's editor;
4. on **Terminal**, the shell in the focused pane.

On **Home** with no search or field open, nothing takes typing, so the text is copied instead.

## Cleanup

Cleanup is one pass a chat model makes over the whole dictation once it stops. It fixes
punctuation, capitals, fillers and grammar, and resolves self-corrections ("at five, no, six"
becomes "at six"). Changed and added words show in the accent colour and removed words struck
through; **Insert** and **Copy** use the plain cleaned text.

The settings are in the **Dictation cleanup** group of **Settings → Keyboard → Voice input**:

| Setting | Choices | Default |
| --- | --- | --- |
| **Clean up dictation** | on or off | on |
| **Cleanup model** | Opens the **Tidy dictation** picker: **Automatic**, an installed model, a **Remote** model, or **Raw text** (no model) | Automatic |
| **Cleanup level** | **Light: punctuation, fillers and grammar, in your words. Runs on this phone.** or **Polished: also paragraphs and lists. Uses your remote model when one is set up.** | Polished |
| **Cleanup details** | What cleanup does, and shell commands | |

**Automatic** uses Gemma 4 E2B, then E4B, then raw text. On a phone with 6 GB of RAM or less it
means **Raw text**. The model loads while you speak, so the pass at the end does not wait for it.
**Polished** uses the [remote model](On_Device_AI.md#remote-model) whenever one is set up, and the
cleanup model otherwise. A Polished result's line breaks are pasted only into programs that accept
bracketed paste; elsewhere they are joined with spaces.

The text is **Kept as heard** when the dictation has fewer than 4 words, when it is a spoken
command, when the model's answer looks like a reply instead of a cleanup (or drops most of your
words), when the model takes too long or cannot load, or when the dictation ended on a failure.

## Spoken commands

A dictation that starts with a command name is written as a command by fixed rules, with no model,
whether cleanup is on or off. The strip says **Cleaned up** and **Undo** takes it back.

| You say | You get |
| --- | --- |
| "ls dash la" or "L S dash L A" | `ls -la` |
| "cd slash home slash user" | `cd /home/user` |
| "cd dot dot" | `cd ..` |
| "git add dot" | `git add .` |
| "find dot dash name star dot txt" | `find . -name *.txt` |
| "ls dash dash color equals auto" | `ls --color=auto` |
| "dot slash gradlew" | `./gradlew` |

- The first word must be a known command (`git`, `ls`, `cd`, `sudo`, `pkg`, `tai`, `launcherctl`
  and about seventy-five more) or a path (`./…`, `/…`, `~/…`). Spelled letters join up: "L S" is
  `ls`.
- "dash" is `-`, "dash dash" is `--`, "dot" is `.`, "slash" is `/`, "tilde" is `~`, "pipe" is `|`,
  "star" or "asterisk" is `*`, "equals" is `=`, "underscore" is `_`.
- Everyday words that are also commands (`cat`, `find`, `make`, `go`, `python` and a few more)
  count as a command only in a one- or two-word dictation, or with a symbol word, flag or path.
- "dash L dash A" gives `-l -a`; a long option needs "dash dash". Other symbols and number words
  are not converted.

## Speech models

| Model | Languages | Download | Suggested RAM | Window |
| --- | --- | ---: | ---: | --- |
| Whisper ACFT Base | Many languages | 97 MB | 6 GB+ | 5 s or 10 s |
| Whisper ACFT Base (English) | English only | 97 MB | 6 GB+ | 5 s or 10 s |
| Whisper ACFT Small | Many languages | 273 MB | 8 GB+ | 5 s or 10 s |
| Whisper ACFT Small (English) | English only | 273 MB | 8 GB+ | 5 s or 10 s |
| Parakeet TDT 0.6B v3 | 25 European languages, detected automatically | 586 MB | 8 GB+ | 5 s |

- **Base** is faster and fine for commands and short phrases. **Small** is more accurate in long
  sentences and noisy rooms, and about 3× slower.
- The **window** is the longest stretch of speech read at once; a longer phrase is cut at its
  quietest moment and carries on as the next.
- Whisper opens an install sheet for the size, the language and the window; Parakeet installs as it
  is. The first speech model to finish becomes the one in use. With several installed, choose with
  **Use for…** in the Model centre, or **Settings → Keyboard → Voice input → Speech model**, which
  also offers **Change window** and **Unload after idle** (2 minutes by default).
- **Voice language** (Speech group) applies to the multilingual Whisper models only. **Auto** takes
  the keyboard layout's language, then the phone's. Parakeet detects the language itself.

## Listening settings

In the **Listening** group of **Settings → Keyboard → Voice input**:

| Setting | Choices | Default |
| --- | --- | --- |
| **Phrase pause** | 400 ms, 600 ms, 800 ms, 1.2 s: how long a pause closes a phrase | 600 ms |
| **Mic sensitivity** | **Normal: ignores background talk** or **High: for soft speech; may hear others** | Normal |
| **Stop after silence** | 5 s, 10 s, 30 s, **Until tap** | 10 s |
| **Listening sounds** | A sound when listening starts or stops | on |

Speech detection uses Silero VAD, a small bundled model, so fans and keyboard clicks are not taken
for speech; other voices and a TV can be, more often on **High**. Haptics follow the keyboard's
haptic setting. The screen stays on while the card is up, until the waiting text has been left
alone for 3 minutes.

## From the terminal: `tai transcribe`

`tai transcribe` runs the speech model voice input uses on a file. It prints the text as heard, with
no cleanup and no command formatting:

```sh
tai transcribe recording.wav
tai transcribe recording.wav --language de
tai transcribe recording.wav --prompt "launcherctl tmux nvim"
tai transcribe recording.wav --model whisper-acft-small-en
```

Input is a WAV file (16 kHz mono 16-bit preferred; other rates are resampled) or raw 16-bit PCM at
16 kHz mono. `--language` forces a language on multilingual Whisper; `--prompt` gives Whisper words
to lean towards. It runs on the CPU and never waits behind a chat generation.

## The API: `/v1/audio/transcriptions`

The same route serves `tai transcribe`, curl and OpenAI clients: OpenAI's multipart form with
`file`, `model`, `language`, `prompt` and `response_format` (`json`, `text` or `verbose_json`).

```sh
curl -sS -H "Authorization: Bearer $(cat ~/.launcherctl/token)" \
  -F "file=@recording.wav" "$(cat ~/.launcherctl/endpoint)/v1/audio/transcriptions"
```

Without a speech model installed it answers `stt_model_not_configured`.

When something goes wrong, see
[Voice input problems](Launcher_Troubleshooting.md#voice-input-does-not-hear-or-type). To hear text
read aloud, see [Text to speech](Text_To_Speech.md); for the models, see
[On-device AI models](On_Device_AI_Models.md).
