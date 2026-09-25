"""Builds a synthetic voice-input test set for VoiceReplayRig: terminal commands and dictation,
spoken by several Piper voices, under the room conditions measured on pong (2026-09-25).

Each clip is a session-shaped 16 kHz mono WAV (1 s of room, the phrase, 1.5 s of room) with the
truth in manifest.json. Score a rig run with scripts/voice_eval_score.py.

Conditions (levels from the pong logs: bottom mic, VOICE_RECOGNITION, no AGC). Speech level is
set by its loud 30 ms frames (the 95th-percentile frame RMS), the figure the app's per-second
"level: peak=" log reports — not by the sample peak, which sits ~15 dB higher:
  near  speech frames -26 dBFS, room -70 dBFS           (phone held close)
  far   small-room reverb, speech frames -46 dBFS, room -62 dBFS   (phone on a desk)
  fan   far + a fan: broadband noise around -59 dBFS mean whose level jumps +-5.5 dB every 30 ms
  tv    far + another voice talking continuously at about -58 dBFS (a TV or video in the room)

Synthetic speech is cleaner than a real voice; treat results as a comparison between models on
the same audio, not as absolute accuracy. Needs the rig venv plus piper-tts and scipy, and the
voices in ~/.cache/termux-launcher/piper/.

usage: voice_eval_make.py [out_dir]   (default ~/.cache/termux-launcher/voice-eval)
"""
import json
import os
import sys
import wave

import numpy as np
from piper import PiperVoice
from scipy.signal import fftconvolve, resample_poly

SR = 16000
HOME = os.path.expanduser("~")
VOICE_DIR = os.path.join(HOME, ".cache/termux-launcher/piper")
VOICES = ["en_US-lessac-medium", "en_GB-alan-medium", "en_US-ryan-medium", "en_GB-northern_english_male-medium"]
TV_VOICE = "en_US-lessac-medium"

# kind: "text" expects exactly that text (a typed command), "dictation" is scored by word error rate.
PHRASES = [
    ("text", "ls", "ls"),
    ("text", "git status", "git status"),
    ("text", "sudo apt update", "sudo apt update"),
    ("text", "clear", "clear"),
    ("text", "pkg install python", "pkg install python"),
    ("dictation", "Can you summarise the readme file for me?", None),
    ("dictation", "Please open the settings and turn off dark mode.", None),
    ("dictation", "The build failed because the Gradle daemon ran out of memory.", None),
    ("dictation", "What is the weather like in Kuwait City today?", None),
    ("dictation", "Remind me to call the dentist tomorrow at ten in the morning.", None),
    ("dictation", "I noticed that when I speak for more than a few seconds, the transcript breaks into "
                  "pieces and some of the words go missing.", None),
]
TV_LINES = [
    "And now, the rest of the evening news. Markets closed higher today after a quiet week of trading.",
    "Coming up after the break, we look at the new season of football and what it means for the league.",
    "Scientists say the results could change how we think about sleep, diet and exercise.",
]


def synth(voice, text):
    chunks = list(voice.synthesize(text))
    rate = chunks[0].sample_rate
    audio = np.concatenate([np.frombuffer(c.audio_int16_bytes, np.int16) for c in chunks]).astype(np.float64) / 32768
    return resample_poly(audio, SR, rate) if rate != SR else audio


def db(x):
    return 10 ** (x / 20)


def room_noise(n, mean_dbfs, rng):
    """Pinkish room tone at a given RMS."""
    white = rng.standard_normal(n + 64)
    pink = np.convolve(white, np.ones(8) / 8, mode="same")[:n]
    return pink / (np.sqrt(np.mean(pink ** 2)) + 1e-12) * db(mean_dbfs)


def fan_noise(n, mean_dbfs, swing_db, rng):
    noise = rng.standard_normal(n)
    frame = SR * 30 // 1000
    gains = db(rng.uniform(-swing_db, swing_db, n // frame + 1))
    noise *= np.repeat(gains, frame)[:n]
    return noise / (np.sqrt(np.mean(noise ** 2)) + 1e-12) * db(mean_dbfs)


def reverb(x, rng, rt60=0.4):
    n = int(SR * rt60)
    t = np.arange(n) / SR
    rir = rng.standard_normal(n) * np.exp(-6.9 * t / rt60)
    rir[0] = 1.0
    wet = fftconvolve(x, rir)[:len(x) + n]
    return wet / (np.max(np.abs(wet)) + 1e-12)


def frames_to(x, dbfs):
    """Scales x so its 95th-percentile 30 ms frame RMS is dbfs."""
    frame = SR * 30 // 1000
    n = len(x) // frame
    rms = np.sqrt(np.mean(x[:n * frame].reshape(n, frame) ** 2, axis=1))
    return x / (np.percentile(rms, 95) + 1e-12) * db(dbfs)


def build(speech, condition, tv, rng):
    lead, tail = int(1.0 * SR), int(1.5 * SR)
    if condition == "near":
        body = frames_to(speech, -26)
    else:
        body = frames_to(reverb(speech, rng), -46)
    clip = np.concatenate([np.zeros(lead), body, np.zeros(tail)])
    n = len(clip)
    clip += room_noise(n, -70 if condition == "near" else -62, rng)
    if condition == "fan":
        clip += fan_noise(n, -59, 5.5, rng)
    if condition == "tv":
        start = rng.integers(0, max(1, len(tv) - n))
        bed = np.resize(tv[start:], n)
        clip += bed / (np.sqrt(np.mean(bed ** 2)) + 1e-12) * db(-58)
    return clip


def write_wav(path, x):
    pcm = np.clip(np.round(x * 32767), -32768, 32767).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HOME, ".cache/termux-launcher/voice-eval")
    os.makedirs(out, exist_ok=True)
    rng = np.random.default_rng(20260925)
    tv_voice = PiperVoice.load(os.path.join(VOICE_DIR, TV_VOICE + ".onnx"))
    tv = reverb(np.concatenate([synth(tv_voice, line) for line in TV_LINES] * 4), rng, rt60=0.6)
    manifest = {}
    for voice_name in VOICES:
        voice = PiperVoice.load(os.path.join(VOICE_DIR, voice_name + ".onnx"))
        short = voice_name.split("-")[1]
        for index, (kind, text, expect) in enumerate(PHRASES):
            speech = synth(voice, text)
            for condition in ("near", "far", "fan", "tv"):
                name = f"{condition}-{short}-{index:02d}.wav"
                write_wav(os.path.join(out, name), build(speech, condition, tv, rng))
                manifest[name] = {"kind": kind, "text": text, "expect": expect, "voice": voice_name,
                                  "condition": condition, "seconds": round(len(speech) / SR, 2)}
        print(voice_name, "done", flush=True)
    with open(os.path.join(out, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=1)
    print(len(manifest), "clips in", out)


if __name__ == "__main__":
    main()
