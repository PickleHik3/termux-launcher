"""Quiet speech vs office noise on the Silero path: the probe behind VoiceMicSensitivity.

Speech: the developer's real pong phrases (debug captures, before VoiceGain) at their own level
and 6 / 10 dB quieter over a -64 dBFS pink room that does not get quieter with them, and the
synthetic near sentences and key words at a phrase RMS of -52 / -58 / -62 dBFS over the same
room. Noise only, 120 s each: the room alone, room + babble, room + TV, room + fan, room + keyboard
clicks. Every variant runs through the harness's own Segmenter (the app's state machine) with a
decider that shows Silero a copy of each frame scaled the way VoiceActivityDetector does
(VoiceMicSensitivity.sileroGain: lift the 10th-percentile noise floor to a target, never down,
at most a maximum), so only the gain, the thresholds and the minimum voiced time differ.

    V=~/.cache/termux-launcher/venv/bin
    cd scripts/voice-eval && $V/python sensitivity_probe.py     # a few minutes

Needs the Silero ONNX model and the synthetic set (README, Setup); the real phrases are read from
~/.cache/termux-launcher/voice-clips if present. Writes nothing.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np

from common import SR, FRAME_SAMPLES, load_clip_set, load_wav, SpeechPool, make_noise, to_pcm16
from vads import SileroModel, default_silero_path, Segmenter, EnergyDecider, frame_rms

model = SileroModel(default_silero_path())
rng = np.random.default_rng(11)
clips = load_clip_set()
pool = SpeechPool(clips)


def db(x):
    return 10 ** (x / 20)


def room(n, level_db):
    return make_noise('pink', n, rng, pool) * db(level_db)


def clicks(n, level_db):
    x = np.zeros(n)
    t = 0
    while True:
        t += int(rng.uniform(0.08, 0.4) * SR)
        if t + 200 > n:
            break
        k = np.arange(160)
        x[t:t + 160] += rng.standard_normal(160) * np.exp(-k / 25.0)
    return x / (np.abs(x).max() + 1e-12) * db(level_db)


class GainedSilero:
    """Silero on the audio times a per-frame gain; the gain is decided causally per frame."""

    def __init__(self, onset, hold, gain_fn):
        self.onset, self.hold, self.gain_fn = onset, hold, gain_fn
        self.probs = None

    def prepare(self, audio):
        n = len(audio) // FRAME_SAMPLES
        energy = EnergyDecider()
        gains = np.ones(len(audio), np.float32)
        for f in range(n):
            fr = to_pcm16(audio[f * FRAME_SAMPLES:(f + 1) * FRAME_SAMPLES])
            sm = energy.smooth(frame_rms(fr))
            energy.update_floor(sm)
            gains[f * FRAME_SAMPLES:(f + 1) * FRAME_SAMPLES] = self.gain_fn(float(energy.noise_floor), float(sm))
        self.probs = model.chunk_probs(np.clip(audio * gains, -1, 1))

    def decide(self, frame_index, pcm_frame, in_speech):
        end_sample = (frame_index + 1) * FRAME_SAMPLES
        chunk = end_sample // 512 - 1
        p = float(self.probs[chunk]) if 0 <= chunk < len(self.probs) else 0.0
        return p > (self.hold if in_speech else self.onset), frame_rms(pcm_frame)


def fixed(g_db):
    g = db(g_db)
    return lambda floor, sm: g


def floor_ref(target_db, max_db):
    t, mx = db(target_db), db(max_db)
    return lambda floor, sm: max(1.0, min(mx, t / max(floor, 1e-6)))


METHODS = {
    # what shipped before round 3
    'before: no lift, 0.50/0.35, 300 ms': (0.5, 0.35, fixed(0), 300),
    # lower thresholds alone barely move quiet speech: its probabilities sit far under them
    'thresholds 0.40/0.25, 300 ms': (0.4, 0.25, fixed(0), 300),
    # a fixed lift: works for speech, lets babble and TV in
    'fixed +12 dB': (0.5, 0.35, fixed(12), 300),
    # Normal (VoiceMicSensitivity.NORMAL)
    'Normal: floor->-63, max +6, 300 ms': (0.5, 0.35, floor_ref(-63, 6), 300),
    # stronger floor lifts that were considered for the default
    'floor->-60, max +12, 300 ms': (0.5, 0.35, floor_ref(-60, 12), 300),
    'floor->-58, max +12, 360 ms': (0.5, 0.35, floor_ref(-58, 12), 360),
    # High (VoiceMicSensitivity.HIGH)
    'High: floor->-56, max +12, 420 ms': (0.5, 0.35, floor_ref(-56, 12), 420),
    'floor->-52, max +18, 450 ms': (0.5, 0.35, floor_ref(-52, 18), 450),
}


def run(method, audio):
    onset, hold, fn, min_ms = METHODS[method]
    seg = Segmenter(GainedSilero(onset, hold, fn))
    seg.min_voiced_frames = min_ms // 30
    segs, flags, _ = seg.run(audio)
    return segs, flags


# --- speech set ----------------------------------------------------------------------------
speech = []  # (name, audio, speech frame range)
real_dir = os.path.expanduser('~/.cache/termux-launcher/voice-clips')
for name in sorted(os.listdir(real_dir)) if os.path.isdir(real_dir) else []:
    if name.endswith('n.wav') or name == 'seg0.wav':
        # The n files are normalised copies; seg0 is not speech to Silero at any level.
        continue
    a = load_wav(os.path.join(real_dir, name)).astype(np.float64)
    for att in (0, -6, -10):
        body = a * db(att)
        lead, tail = int(1.0 * SR), int(1.5 * SR)
        n = lead + len(body) + tail
        x = room(n, -64)  # the room does not get quieter when the user does
        x[lead:lead + len(body)] += body
        speech.append(('real:%s@%+d' % (name, att), x, (lead // FRAME_SAMPLES, (lead + len(body)) // FRAME_SAMPLES)))

syn = [c for c in clips if c.condition == 'near' and c.kind == 'dictation'][:16] + [c for c in clips if c.condition == 'near' and c.kind == 'key'][:12]
for c in syn:
    a = load_wav(c.path).astype(np.float64)
    body = a[int(1.0 * SR):len(a) - int(1.5 * SR)]
    rms = np.sqrt(np.mean(body ** 2)) + 1e-12
    for lvl in (-52, -58, -62):
        b = body / rms * db(lvl)
        lead, tail = int(1.0 * SR), int(1.5 * SR)
        x = room(lead + len(b) + tail, -64)
        x[lead:lead + len(b)] += b
        speech.append(('%s:%s@%d' % ('syn' if c.kind == 'dictation' else 'key', c.name, lvl), x, (lead // FRAME_SAMPLES, (lead + len(b)) // FRAME_SAMPLES)))

# --- noise set -----------------------------------------------------------------------------
N = 120 * SR
noise = {
    'room-64': room(N, -64),
    'bab-70': room(N, -64) + make_noise('babble', N, rng, pool) * db(-70),
    'bab-66': room(N, -64) + make_noise('babble', N, rng, pool) * db(-66),
    'bab-62': room(N, -64) + make_noise('babble', N, rng, pool) * db(-62),
    'tv-70': room(N, -64) + make_noise('tv', N, rng, pool) * db(-70),
    'tv-66': room(N, -64) + make_noise('tv', N, rng, pool) * db(-66),
    'fan-58': room(N, -64) + make_noise('fan', N, rng, pool) * db(-58),
    'clicks': room(N, -64) + clicks(N, -30),
}

print('speech: missed clips / voiced share of the phrase, by level')
for method in METHODS:
    stats = {}
    for name, x, (a0, a1) in speech:
        key = name.split(':')[0] + name.split('@')[1]
        segs, flags = run(method, x)
        hit = any(s['start'] < a1 and s['end'] > a0 for s in segs)
        covered = np.zeros(len(flags), bool)
        for s in segs:
            covered[s['start']:s['end']] = True
        share = covered[a0:a1].mean() if a1 > a0 else 0
        st = stats.setdefault(key, [0, 0, 0.0])
        st[0] += 1
        st[1] += (not hit)
        st[2] += share
    print('%-38s' % method, '  '.join('%s: %d/%d %3.0f%%' % (k, v[1], v[0], 100 * v[2] / v[0]) for k, v in sorted(stats.items())))
print()
print('noise only: false segments per minute')
for method in METHODS:
    row = []
    for k, x in noise.items():
        segs, _ = run(method, x)
        row.append('%s: %.1f' % (k, len(segs) * 60.0 / (len(x) / SR)))
    print('%-38s' % method, '  '.join(row))
