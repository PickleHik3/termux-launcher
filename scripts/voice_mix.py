"""Mixes a speech WAV with a noise WAV, to build noisy variants for VoiceReplayRig
(project-docs/plans/whisper-voice-input.md "Replay rig"). Both must be 16 kHz mono PCM16; the
noise loops (or is trimmed) to the speech's length, optionally with extra seconds of noise-only
audio before and/or after the speech so the mixed clip still opens and closes on room tone the way
a real session does.

Usage:
  python3 voice_mix.py speech.wav noise.wav out.wav --snr 10
  python3 voice_mix.py speech.wav noise.wav out.wav --level 0.05 --lead 1.5 --trail 1.5
"""
import argparse
import wave

import numpy as np

SAMPLE_RATE = 16_000


def read_wav(path):
    with wave.open(path, "rb") as w:
        if w.getframerate() != SAMPLE_RATE or w.getnchannels() != 1 or w.getsampwidth() != 2:
            raise ValueError(f"{path} must be 16 kHz mono PCM16, got {w.getframerate()} Hz, "
                              f"{w.getnchannels()} channel(s), {8 * w.getsampwidth()}-bit")
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768.0


def write_wav(path, samples):
    clipped = np.clip(samples, -1.0, 1.0)
    pcm = (clipped * 32767.0).astype(np.int16)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(pcm.tobytes())


def looped_to_length(noise, length):
    if len(noise) >= length:
        return noise[:length]
    reps = length // len(noise) + 1
    return np.tile(noise, reps)[:length]


def rms(samples):
    return float(np.sqrt(np.mean(samples.astype(np.float64) ** 2))) if len(samples) else 0.0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("speech", help="16 kHz mono PCM16 speech WAV")
    parser.add_argument("noise", help="16 kHz mono PCM16 noise WAV (looped or trimmed to fit)")
    parser.add_argument("out", help="where the mixed WAV is written")
    level_group = parser.add_mutually_exclusive_group(required=True)
    level_group.add_argument("--level", type=float, help="noise RMS as a fraction of full scale (e.g. 0.05)")
    level_group.add_argument("--snr", type=float, help="target signal-to-noise ratio in dB against the speech's own RMS")
    parser.add_argument("--lead", type=float, default=0.0, help="seconds of noise-only audio prepended (default 0)")
    parser.add_argument("--trail", type=float, default=0.0, help="seconds of noise-only audio appended (default 0)")
    args = parser.parse_args()

    speech = read_wav(args.speech)
    noise = read_wav(args.noise)
    if len(noise) == 0:
        raise ValueError(f"{args.noise} is empty")

    noise_body = looped_to_length(noise, len(speech))
    noise_rms = rms(noise_body)
    if args.level is not None:
        target_rms = args.level
    else:
        target_rms = rms(speech) / (10.0 ** (args.snr / 20.0))
    gain = 0.0 if noise_rms == 0.0 else target_rms / noise_rms
    noise_body = noise_body * gain

    lead_len = int(args.lead * SAMPLE_RATE)
    trail_len = int(args.trail * SAMPLE_RATE)
    lead = looped_to_length(noise, max(1, lead_len))[:lead_len] * gain if lead_len > 0 else np.zeros(0, np.float32)
    trail = looped_to_length(noise, max(1, trail_len))[:trail_len] * gain if trail_len > 0 else np.zeros(0, np.float32)

    mixed = np.concatenate([lead, speech + noise_body, trail])
    write_wav(args.out, mixed)
    print(f"{args.out}: {len(mixed) / SAMPLE_RATE:.2f}s, noise rms {rms(noise_body):.4f} "
          f"(speech rms {rms(speech):.4f})")


if __name__ == "__main__":
    main()
