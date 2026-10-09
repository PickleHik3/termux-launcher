"""Generates the ParakeetFeaturesTest fixtures from scripts/parakeet_replay_server.py's front end.

    ~/.cache/termux-launcher/venv/bin/python app/src/test/resources/parakeet/gen_fixture.py

The signal is synthetic and deterministic (ParakeetFeaturesTest#fixtureSignal builds the same one in
Java): a 220 Hz tone, a chirp, an LCG noise burst and a near-silent tail, quantised to int16. It is
exactly 5 s so no padding noise (whose generator the Java port does not share) enters the fixture.

Outputs, next to this script:
  features_128x125_stride4.f32  — the [128, 500] features at frames 0, 4, 8, ... 496, little-endian
  features_stats.json           — min / max / mean of the whole [128, 500] array
"""
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "..", "..", "scripts"))
from parakeet_replay_server import features, SR, WINDOW_SAMPLES  # noqa: E402


def lcg_noise(count, seed=12345):
    out = np.zeros(count)
    s = seed
    for i in range(count):
        s = (s * 1103515245 + 12345) % (1 << 31)
        out[i] = s / float(1 << 31) - 0.5
    return out


def signal():
    n = np.arange(WINDOW_SAMPLES)
    t = n / SR
    x = 0.3 * np.sin(2 * np.pi * 220.0 * t) * (t < 2.5)
    x = x + 0.2 * np.sin(2 * np.pi * (500.0 * t + 200.0 * t * t)) * ((t >= 1.0) & (t < 3.5))
    x = x + 0.05 * lcg_noise(WINDOW_SAMPLES) * ((t >= 3.0) & (t < 4.0))
    x = x + 0.001 * np.sin(2 * np.pi * 1000.0 * t) * (t >= 4.0)
    pcm = np.clip(np.floor(x * 32767.0 + 0.5), -32768, 32767)
    return (pcm / 32768.0).astype(np.float32)


def main():
    audio = signal()
    feats = features(audio)[0]  # [128, 500]
    assert feats.shape == (128, 500), feats.shape
    strided = np.ascontiguousarray(feats[:, ::4]).astype("<f4")
    assert strided.shape == (128, 125), strided.shape
    strided.tofile(os.path.join(HERE, "features_128x125_stride4.f32"))
    stats = {"min": float(feats.min()), "max": float(feats.max()), "mean": float(feats.mean()),
             "frame0_bin0": float(feats[0, 0]), "frame250_bin64": float(feats[64, 250]),
             "audio_samples": int(len(audio)), "audio_sum": float(audio.astype(np.float64).sum())}
    with open(os.path.join(HERE, "features_stats.json"), "w") as f:
        json.dump(stats, f, indent=2)
    print(stats)


if __name__ == "__main__":
    main()
