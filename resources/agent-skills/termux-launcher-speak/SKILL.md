---
name: termux-launcher-speak
description: Use inside Termux Launcher when the user asks you to read your answers aloud, speak them, answer by voice or work hands-free, or says they cannot read the screen right now. It speaks a short spoken version of each answer through the phone's on-device voice with tai speak. Not for ordinary tasks where nobody asked for speech.
---

# Speak your answers in Termux Launcher

The user is listening, not reading. Keep writing your answer as usual; then speak a short spoken
version of it on the phone. Stay in this mode until the user ends it.

## Check once, at the start

```sh
command -v tai
tai speak --out "${TMPDIR:-$HOME}/tl-speak-check.wav" "Ready." 2>&1; rm -f "${TMPDIR:-$HOME}/tl-speak-check.wav"
```

The second line makes the audio without playing it. `Saved …` means speech works.

- `tts_model_not_installed`: tell the user, in writing, to install **KittenTTS Nano** from
  Settings → On-device AI → Model centre → Get models → Voice output, and carry on in writing only.
  Check again when they say it is installed.
- No `tai`, or any other error: tell the user in writing that speech is not available here, quote
  the error in one line, and carry on in writing only.

## Speak

Make this the last command of the turn, after the work is done and the answer is written:

```sh
tai speak --stop >/dev/null 2>&1
tai speak --whole <<'SPOKEN'
<spoken text>
SPOKEN
```

The first line silences anything still playing from an earlier turn. The quoted heredoc means
the shell touches nothing in the text, so quotes, dollar signs and backticks are safe in it.

The spoken text is one to three plain sentences: what you did or found, and what the user should
do next.

- No Markdown, no code, no file paths, commands or flags unless the user asked for them.
- Say numbers and names the way a person would read them out: "version one point two", "about
  three minutes", "the Keyboard settings page".
- Never speak tool output, logs or lists. Summarise them: "Four tests failed, all in the parser."
- Keep it to a few hundred characters; `tai speak` takes 20,000, and you should never come near it.

`tai speak` returns only once the text has been heard, so a long spoken version holds up the turn.
That is why it stays short.

## Stop and interrupt

- The user says "stop", "quiet" or "enough": run `tai speak --stop`, and do not repeat what was cut.
- The user asks something new while you were speaking, or interrupted your last command: run
  `tai speak --stop` before anything else in the new turn.

## Voice and speed

Honour a voice or speed the user names, and keep it for the rest of the mode:

```sh
tai speak --whole --voice Rosie --speed 1.2 <<'SPOKEN'
<spoken text>
SPOKEN
```

Voices: `Bruno`, `Hugo`, `Jasper` (the default) and `Rosie`. Speed: 0.5 to 2.0. Without either,
the user's own defaults from Settings apply.

## End the mode

Speech stays on until the user says to stop speaking ("text only", "stop reading aloud", "I can
see the screen again"). Then run `tai speak --stop`, confirm in one written line that answers are
written only from now on, and speak nothing more.
