# Voice input

Voice input turns speech into text on the phone. What you say collects in a small panel, at the top
right until you move it; nothing is typed while you speak. When you stop, you decide what happens to the text: type it
once at the cursor, copy it, or throw it away.

There are two engines. **On-device** runs a speech model you download (Whisper or Parakeet) in the
launcher's own AI runtime, and the audio never leaves the phone. **Android system** hands the
microphone to the phone's own speech recognizer, which types its result straight away. Until you
pick one in **Settings → Keyboard → Voice input → Speech engine**, the launcher uses **Android
system**, then switches to **On-device** by itself as soon as a speech model is installed. Once you
pick an engine, your choice sticks.

## Quick start

1. Open **Settings → On-device AI → Model centre**, go to **Speech**,
   and install a speech model (see [Speech models](#speech-models)).
2. That's it: with a speech model installed, the voice key uses it. (If you picked **Android system**
   earlier, set **Settings → Keyboard → Voice input → Speech engine** back to **On-device**.)
3. Swipe up on the built-in keyboard's Enter key and speak. Allow the microphone the first time.
4. Tap the pill's pause, or just stop talking; after the silence auto-stop (10 s by default) the
   dictation stops by itself.
5. Tap **✓** to type the text at the cursor, or **Copy**.

## Starting a dictation

- **The voice key.** On the built-in keyboard it is the swipe up on Enter. A swipe starts a
  dictation with the engine chosen in Settings; a second swipe pauses it. Swiping up and holding
  opens Android's **Choose speech recognizer** list instead, whichever engine is chosen. A dictation
  started from the voice key belongs to the keyboard: hiding the keyboard stops it.
- **The Dictate key.** `tool:voice.dictate` (**Dictate**) starts or stops a dictation on Home,
  Terminal and Display, with the keyboard up, down, or not the launcher's at all. It is not on the
  row out of the box; add it from **Settings → Keyboard → Edit extra keys**. Use it where the
  keyboard is usually down (Home, Display) or when you type with Android's keyboard.
- **The Android recognizer.** With **Speech engine** set to **Android system**, both keys open the
  phone's recognizer and its result is typed directly, without the panel. The on-device engine
  also falls back to it, with a one-line notice, whenever it cannot start: no speech model
  installed, the microphone permission refused, or the runtime refusing the model.

Leaving the app stops listening. The text heard so far stays in the panel.

## The pill and the panel

While a dictation is up, a pill sits at the top right of the place, under the status bar, in the
same corner on Home, Terminal and Display (you can move it; see
[Moving the pill](#moving-the-pill)). The pill is as long as what it holds, and no longer:

- a scrolling **waveform** of your voice, resting as a dim line whenever the microphone is closed;
- the state: **Listening…**, **Transcribing…**, **Cleaning up…**, then what became of the text
  (**Ready**, **Cleaned up**, **Formatted as a command** or **Kept as heard**);
- a **Warming up** chip while the speech model or the cleanup model is still loading. Recording has
  already started, so keep talking;
- **pause / resume**. Pause closes the microphone: the phrases still transcribing arrive, then the
  cleanup runs by itself. Resume listens again and carries on the same text. It waits, disabled,
  until the last phrases are in. The voice key and the Dictate key do the same thing;
- **×**, which discards: it stops listening if need be, throws the text away and closes the pill.
  Swiping the card sideways does the same. × never means stop; use pause for that.

The pill grows downward into the **panel**, which shows the dictation's text: seven lines, the
oldest scrolling off the top (fewer lines where there is less room, such as landscape with the
keyboard up). A shimmer line stands in for a phrase while it transcribes, then its words type out.

The text shows which version it is at a glance. **As heard**, while a cleanup is still to come and
again after undo, it is *italic* in the dimmer secondary text colour. **Cleaned**, it is upright in
the normal text colour. Text that is final as heard (cleanup off, or **Kept as heard**) has nothing
to be told apart from and shows upright too.

At the bottom of the panel, one long rounded bar holds three controls, spread evenly across it:

- **undo** (**Undo the cleanup**), there only when a cleanup or command formatting changed
  something. It puts back the text as it went in, and turns into redo;
- **Copy**, which puts the text on the clipboard;
- **✓** (**Insert at the cursor**), which types the text once where the keyboard would type. It
  never presses Enter, and the text never contains a line break.

You can press **Copy** or **✓** early, while still listening or cleaning up. The dictation stops and
the press is carried out as soon as the text is final. Afterwards the pill says **Inserted** or
**Copied** and closes a moment later.

Nothing reaches the terminal while you dictate. The panel is the only place the text lives until
you use it. Starting a new dictation while text is still waiting carries on from that text, so it
is not lost.

### Moving the pill

Under the panel sits a short grab handle, the same one the floating keyboard has. Drag it to put
the pill and its panel anywhere on the place: it stays under the status bar and the top bars and
above the keyboard. The pill comes back there on the next dictation. Portrait and landscape each
remember their own spot. **Double-tap the handle** to put the pill back in the top right corner.

Placed low, the panel grows upward instead of down, and shows fewer lines if the room is short, so
it never runs off the screen. The handle shows only while the panel does; a pill with no text yet
stays where it was left.

### Where ✓ types

✓ types where the keyboard would type on the place you are looking at, in this order:

1. whatever has taken the launcher keyboard's typing: the command palette, the app drawer's
   search, a rename field, an open sheet, or on Display, the display itself;
2. on **Display**, the Linux display, even with no launcher keyboard up. If no X server is
   connected, the text is copied instead. It never goes to the terminal behind the display;
3. a focused text field of the launcher's own, such as a widget's editor. A selection there is
   replaced;
4. on **Terminal**, the shell in the focused pane.

On **Home** with no search or field open, nothing takes typing, so ✓ copies the text and says
**Nothing here takes typing, so the dictation was copied instead**.

## Cleanup

Cleanup is one pass a chat model makes over the whole dictation once it stops, when you pause,
tap the voice key or the Dictate key, press ✓ or Copy early, or the silence auto-stop fires. It
fixes punctuation, capitals, fillers and grammar, and resolves self-corrections ("at five, no, six"
becomes "at six"). The panel marks what changed: changed and added words in the accent colour,
removed words struck through. The marks stay on screen for as long as the cleaned text does, so a
long dictation's corrections can still be read; ✓ and Copy use the plain cleaned text. Undo takes
the cleanup back and redo puts it, marks and all, on screen again.

Cleanup is **on by default** and needs a Gemma chat model installed, or a remote model for
Polished. Turn it off with **Clean up dictation** in **Settings → Keyboard → Voice input**. While
it is on, two more rows apply:

| Setting | Choices | Default |
| --- | --- | --- |
| **Cleanup level** | **Light**: punctuation, fillers and grammar, in your words, on one line, on this phone. **Polished**: also paragraphs, and a numbered or bulleted list when you count through or list items | Polished |
| **Cleanup model** | **Automatic** or any installed chat model | Automatic |

**Light** and the local half of **Polished** use the **Cleanup model**. **Automatic** uses Gemma 4
E2B when it is installed, else Gemma 4 E4B. E2B is the recommended model: it keeps your words and is
about three times faster than E4B. The model loads while you speak, so the pass at the end does not
wait for it, and it stays loaded afterwards until On-device AI's normal idle unload.

**Polished** uses the remote model from **Settings → On-device AI → Remote model** whenever one is
set up, whatever the Cleanup model says. With no remote model it runs on the Cleanup model, so it
works offline too. Choosing **Off** as the Cleanup model turns cleanup off for both levels. With no
usable model the text stays as heard.

A Polished result can have line breaks. They are inserted only into a program that accepts pasted
text (bracketed paste), which receives it as one paste. In any other program, such as a plain shell
prompt, the line breaks are joined with spaces, because each line would run on its own.

The text is **Kept as heard** when:

- the dictation has fewer than 4 words. It is left alone, since there is nothing to fix;
- it is a command (see [Spoken commands](#spoken-commands)). Commands never go to the model;
- the model's answer looks like a refusal or an answer instead of a cleanup: it opens with "I
  can't…", "As an AI…" or "Sure, here is…" when you did not say that, drops more than half of your
  words, or is mostly new words. The dictation is sent as quoted speech, never as instructions, and
  this guard catches what gets past that;
- the model takes too long (from 4 seconds for a short dictation up to 90 seconds for a long one),
  is busy loading or answering for another model, or cannot load;
- the dictation ended on a failure, which is often the runtime short of memory.

## Spoken commands

A dictation that starts with a command name is written as a command, with fixed rules and no model.
This happens whether cleanup is on or off and however long the dictation is. The pill says
**Formatted as a command**, and undo takes it back.

| You say | You get |
| --- | --- |
| "ls dash la" or "L S dash L A" | `ls -la` |
| "cd slash home slash user" | `cd /home/user` |
| "cd dot dot" | `cd ..` |
| "git add dot" | `git add .` |
| "find dot dash name star dot txt" | `find . -name *.txt` |
| "ls dash dash color equals auto" | `ls --color=auto` |
| "git push dash dash dry dash run" | `git push --dry-run` |
| "dot slash gradlew" | `./gradlew` |

The rules:

- The first word must be a known command name (`git`, `ls`, `cd`, `sudo`, `pkg`, `tai`,
  `launcherctl` and about seventy-five more), in any case, or a path (`./…`, `/…`, `~/…`). Spelled letters
  at the start join up: "L S" is `ls`, "C D" is `cd`.
- **Symbol words:** "dash" is `-`, "dash dash" is `--`, "dot" is `.`, "slash" is `/`, "tilde" is `~`,
  "pipe" is `|`, "star" or "asterisk" is `*`, "equals" is `=`, "underscore" is `_`. Dot, slash, equals
  and underscore join the words next to them; pipe stands alone.
- The command name and flags are lower-cased, the recognizer's trailing full stop and commas are
  removed, and "um" and "uh" are dropped. Everything else stays as heard.

Limits:

- **Everyday command words.** `cat`, `clear`, `echo`, `find`, `go`, `head`, `kill`, `make`, `man`,
  `more`, `python`, `source`, `tail`, `top`, `touch`, `which` and a few others also start ordinary
  sentences ("Make sure…", "Find the file…"). They count as a command only in a dictation of one or
  two words, or when you say a symbol word, a flag or a path.
- **Joined flags.** After "dash", the next word is the flag's name as heard: "dash la" is `-la`, and
  spelled letters join ("dash L A" is `-la`). "dash L dash A" gives `-l -a`. A long option needs
  "dash dash": "dash all" is `-all`, "dash dash all" is `--all`.
- Other symbols ("colon", "comma", "quote", "hash") and number words are not converted.
- Prose that happens to start with a command name, such as "Claude, can you…", is left as prose.

## Speech models

| Model | Languages | Download | Suggested RAM | Window |
| --- | --- | ---: | ---: | --- |
| Whisper ACFT Base | Many languages | 97 MB | 6 GB+ | 5 s or 10 s |
| Whisper ACFT Base (English) | English only | 97 MB | 6 GB+ | 5 s or 10 s |
| Whisper ACFT Small | Many languages | 273 MB | 8 GB+ | 5 s or 10 s |
| Whisper ACFT Small (English) | English only | 273 MB | 8 GB+ | 5 s or 10 s |
| Parakeet TDT 0.6B v3 | 25 European languages, detected automatically | 586 MB | 8 GB+ | 5 s |
| Android recognizer | Whatever the phone's recognizer supports | none | none | none |

- **Base** is faster and fine for commands and short phrases. **Small** is more accurate for long
  sentences and noisy rooms, but about 3× slower.
- The **window** is the longest stretch of speech the model reads at once. 10 seconds covers commands
  and short phrases, and 5 seconds is fastest for single commands. A phrase longer than the window
  is cut at its quietest moment and carries on as the next phrase.
- **Voice language** (**Settings → Keyboard → Voice input**) applies only to the multilingual
  Whisper models. **Auto** takes the language of the keyboard layout you are typing with, then the
  phone's language. A language Whisper does not know falls back to English with a one-time notice.
  The English-only models always decode English and say so once if another language is chosen.
  Parakeet ignores the setting and detects the language itself.

**Installing.** Open **Model centre → Speech**. Whisper opens an install sheet where you choose the
size (**Base** or **Small**), the language (**English** or **Many languages**) and the window
(**10 seconds** or **5 seconds**); Parakeet installs as it is. A speech model that finishes
downloading becomes the one voice input uses, and the launcher says **… is ready and in use**. With
several installed, choose one with **Use for voice** in Model centre, or in **Settings → Keyboard →
Voice input → Speech model**.

The **Speech model** screen lists the installed models with **In use** on the current one, offers
**Change window** (it downloads the other window first and removes the old one once it arrives), and
**Unload after idle**, which frees the model's memory when voice input has not been used for a while
(2 minutes by default). Deleting the model in use switches to another installed one, or to the
Android recognizer when none is left.

## Listening settings

All in **Settings → Keyboard → Voice input**:

| Setting | Choices | Default | What it does |
| --- | --- | --- | --- |
| **Pause that ends a phrase** | 400 ms, 600 ms, 800 ms, 1.2 s | 600 ms | How long a pause closes a phrase and sends it to be transcribed. Longer suits slow speakers; shorter shows text sooner. |
| **Silence auto-stop** | 5 s, 10 s, 30 s, **Until tap** | 10 s | How long without speech stops the dictation. **Until tap** only stops on a tap, the keyboard going down, or a failure. |
| **Mic sensitivity** | **Normal: keeps background talk out** or **High: for speaking softly; may hear a TV or people nearby** | Normal | How readily quiet speech counts as speech. Choose **High** if soft speech in a quiet office is missed; stay on **Normal** where other people talk nearby. |
| **Voice sounds** | on or off | on | A short blip when the microphone opens and when listening ends. |

Speech detection uses **Silero VAD**, a small voice-activity model bundled with the app, to tell
speech from room noise. Silero hears quiet speech less well, so the launcher raises the level of
what it hears to match the room: a little on **Normal**, up to four times as much on **High**. Fans,
air conditioning and keyboard clicks are not taken for speech at either setting, but other people's
voices and a TV are, more often on **High**. If Silero cannot load, a simpler loudness detector takes
over, and **Mic sensitivity** does not change it. A phrase with less than about 0.3 s of speech
(0.42 s on **High**) is dropped, and captions a model writes for non-speech (`[Music]`,
`[BLANK_AUDIO]`) are removed.

The blips play at the volume of the phone's touch sounds, and the first 180 ms after the microphone
opens are dropped so the blip is never transcribed. Haptics (a tick on open, a confirm on stop, a
double buzz on a failure) follow the keyboard's **Haptic feedback** setting.

The screen stays on for as long as the pill is up: while the dictation listens, transcribes or
cleans up, and while the text waits for you. If the waiting text is left untouched for 3 minutes,
the screen may sleep as usual; the text stays in the panel, and touching the pill keeps the screen on
again. Closing the pill, or leaving the app, lets the screen sleep at once.

## From the terminal: `tai transcribe`

`tai transcribe` runs the same speech model voice input uses on an audio file:

```sh
tai transcribe recording.wav
tai transcribe recording.wav --language de
tai transcribe recording.wav --prompt "launcherctl tmux nvim"
tai transcribe recording.wav --model whisper-acft-small-en
tai --json transcribe recording.wav
```

- Input is a WAV file (16 kHz mono 16-bit PCM preferred; other rates are resampled) or raw 16-bit
  PCM at 16 kHz mono.
- `--language` forces an ISO 639-1 code on a multilingual Whisper model. The English-only models
  always decode English, and Parakeet detects the language itself.
- `--prompt` gives Whisper a line of vocabulary to lean towards. Parakeet has no prompt.
- `--model` picks another installed speech model by ID.
- It prints the bare transcript; `--json` prints the API's JSON.

Transcription runs on the CPU in the `:tai_runtime` process and never waits behind a chat
generation. The command prints the text as heard: no cleanup and no command formatting.

## The API: `/v1/audio/transcriptions`

The same route serves `tai transcribe`, curl and OpenAI clients. It takes OpenAI's multipart form:
`file` (required), `model`, `language`, `prompt` and `response_format` (`json`, the default, `text`,
or `verbose_json`).

```sh
curl -sS -H "Authorization: Bearer $(cat ~/.launcherctl/token)" \
  -F "file=@recording.wav" -F "response_format=json" \
  "$(cat ~/.launcherctl/endpoint)/v1/audio/transcriptions"
```

`json` answers `{"text": "…", "tai": {…}}`, with the model, language, duration and timings under
`tai`. `text` answers the transcript as plain text, and `verbose_json` adds `task`, `language`,
`duration` and `segments`. Scripts on the phone can also send a JSON body with a `file` path instead
of an upload. Without a speech model installed, the route answers `stt_model_not_configured`.

## Troubleshooting

- **The voice key opens Google's (or another) recognizer.** **Speech engine** is set to **Android
  system**, either because you picked it or because no speech model is installed yet. Install one,
  or choose **On-device**. If no speech model is installed, choosing it opens the **Speech model**
  screen.
- **"No speech model installed — using the Android recognizer".** Install one from **Model centre →
  Speech**.
- **Microphone permission.** The first on-device dictation asks for the microphone with **Allow** or
  **Not now**. **Not now**, or refusing, uses the Android recognizer instead ("Microphone access
  denied — using the Android recognizer"). To allow it later, open Android's app settings for Termux
  Launcher → **Permissions** → **Microphone**. The microphone is used only while a dictation is
  listening.
- **Nothing heard.** When no phrase came through, the pill closes by itself. Start speaking just after
  the blip, hold the phone nearer, and avoid single very short words. If you speak softly, set **Mic
  sensitivity** to **High**. A room with a fan or TV can hide quiet speech; try the **Small** model,
  which recovers far-field speech that **Base** loses.
- **Phrases appear that you did not say.** On **Mic sensitivity: High**, a TV or people talking near
  the phone can be heard as speech. Set it back to **Normal**.
- **Media playing.** Voice input does not pause music or videos, and the microphone hears them. Music
  can keep a phrase open or be transcribed as words, and the silence auto-stop may never fire. Pause
  the media while you dictate.
- **The dictation stops when the keyboard hides.** A dictation from the voice key belongs to the
  keyboard. Use the Dictate key to dictate with the keyboard down.
- **Memory.** A speech model and a cleanup model can be loaded at the same time. If the runtime is
  short of memory, the dictation stops with **Voice input stopped: …**, or falls back to the Android
  recognizer when nothing was heard yet. Close other apps, use a **Base** Whisper model, keep
  **Automatic** (E2B) as the cleanup model, or turn cleanup off. **Unload after idle** frees the speech
  model between dictations.
- **The text is "Kept as heard" every time.** Check that a Gemma chat model is installed and that
  **Polish dictation with local model** is on. Dictations under four words are always
  kept as heard, and commands are formatted by fixed rules rather than the model. See [Cleanup](#cleanup).

For the models themselves and the rest of the local API, see [On-device AI](On_Device_AI.md). To
hear text read aloud, see [Text to speech](Text_To_Speech.md).
