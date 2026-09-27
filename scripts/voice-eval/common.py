"""Shared pieces of the voice-eval harness: clip sets, noise augmentation, WAV I/O, VoiceGain.

No audio is ever written by the harness. Every clip variant (a clip, optionally mixed with a
noise at an SNR) is rebuilt deterministically from its id, so vad_compare.py and
stt_compare.py see bit-identical audio without a cache of WAVs.

Variant ids:  "<clip>|<noise>|<snr>"   e.g. "near-lessac-05.wav|babble|5", "far-alan-03.wav|none|-"
Noise-only ids: "noise:<source>|<noise>|<snr>"  (see noise_only_variants)
"""
import hashlib
import json
import os
import re
import sys
import wave

import numpy as np

SR = 16000
FRAME_MS = 30
FRAME_SAMPLES = SR * FRAME_MS // 1000
HOME = os.path.expanduser("~")
CACHE = os.path.join(HOME, ".cache/termux-launcher")
DEFAULT_SET = os.path.join(CACHE, "voice-eval")
HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPTS = os.path.dirname(HERE)
OUT = os.path.join(HERE, "out")

# The existing scripts (parakeet_replay_server, whisper_replay_server, voice_eval_score) sit one
# directory up; the harness imports them rather than re-implementing them.
if SCRIPTS not in sys.path:
    sys.path.insert(0, SCRIPTS)

# voice_eval_make.py lays every synthetic clip out as 1.0 s of room, the phrase, 1.5 s of room.
LEAD_S = 1.0
TAIL_S = 1.5

NOISES = ("tv", "babble", "fan", "white", "pink")
DEFAULT_NOISES = ("tv", "babble", "fan", "white")
DEFAULT_SNRS = (10, 5, 0)


# ---------------------------------------------------------------------------------------------
# WAV I/O
# ---------------------------------------------------------------------------------------------

def load_wav(path):
    """16 kHz mono PCM16 WAV -> float32 in [-1, 1]. Anything else is refused loudly."""
    with wave.open(path) as w:
        if w.getframerate() != SR or w.getnchannels() != 1 or w.getsampwidth() != 2:
            raise ValueError(f"{path}: need 16 kHz mono PCM16, got {w.getframerate()} Hz "
                             f"x{w.getnchannels()} {8 * w.getsampwidth()}-bit")
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768.0


def to_pcm16(x):
    """float [-1, 1] -> int16 as the app's capture delivers it (rounded, clipped)."""
    return np.clip(np.round(np.asarray(x, np.float64) * 32767), -32768, 32767).astype(np.int16)


def write_wav(path, x):
    """Only used for ad-hoc debugging (--dump); never called by a normal run."""
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(to_pcm16(x).tobytes())


def voice_gain(pcm16):
    """Port of VoiceGain.java: peak-normalise a segment to -6 dBFS, at most +40 dB, never down.

    Every STT gets the segment exactly as the app hands it over after the VAD.
    """
    peak = int(np.max(np.abs(pcm16.astype(np.int32)))) if len(pcm16) else 0
    if peak == 0:
        return pcm16.astype(np.float32) / 32768.0
    gain = max(1.0, min(100.0, 0.5 * 32768.0 / peak))  # VoiceGain.TARGET_PEAK, MAX_GAIN
    scaled = np.clip(np.round(pcm16.astype(np.float64) * gain), -32768, 32767)
    return (scaled / 32768.0).astype(np.float32)


# ---------------------------------------------------------------------------------------------
# Clip sets and truth
# ---------------------------------------------------------------------------------------------

def words(text):
    """The scorer's own normalisation (voice_eval_score.words), so WER here matches its WER."""
    return re.sub(r"[^a-z0-9' ]+", " ", (text or "").lower().replace("-", " ")).split()


class Clip:
    """One source clip and what is known about it.

    text      the spoken words (the reference for WER), or None for an unlabelled real clip
    kind      "key" / "text" (a typed command, exact-match scored too) / "dictation" / "real"
    align_key the clip whose word timings describe this one: all four conditions of one
              phrase+voice share the same speech placed at the same offset (voice_eval_make.build),
              so the near (clean, no reverb) version is aligned once and reused
    """

    def __init__(self, name, path, text, kind, condition, expect=None, align_key=None):
        self.name = name
        self.path = path
        self.text = text
        self.kind = kind
        self.condition = condition
        self.expect = expect
        self.align_key = align_key or name


def load_clip_set(set_dir=DEFAULT_SET, conditions=None, kinds=None, voices=None, limit=None):
    """The synthetic set (manifest.json from voice_eval_make.py) as Clip objects."""
    manifest = json.load(open(os.path.join(set_dir, "manifest.json")))
    clips = []
    for name in sorted(manifest):
        truth = manifest[name]
        cond = truth["condition"]
        if conditions and cond not in conditions:
            continue
        if kinds and truth["kind"] not in kinds:
            continue
        short = name.split("-")[1]
        if voices and short not in voices:
            continue
        path = os.path.join(set_dir, name)
        if not os.path.isfile(path):
            continue
        align_key = "near-" + name.split("-", 1)[1]
        clips.append(Clip(name, path, truth["text"], truth["kind"], cond, truth.get("expect"), align_key))
    if limit:
        clips = clips[:limit]
    return clips


def load_real_clips(real_dir):
    """Real recordings (never committed). Truth, when present, is <stem>.txt next to the WAV."""
    clips = []
    if not real_dir or not os.path.isdir(real_dir):
        return clips
    for name in sorted(os.listdir(real_dir)):
        if not name.lower().endswith(".wav"):
            continue
        txt = os.path.join(real_dir, name[:-4] + ".txt")
        text = open(txt, encoding="utf-8").read().strip() if os.path.isfile(txt) else None
        clips.append(Clip("real:" + name, os.path.join(real_dir, name), text, "real", "real"))
    return clips


# ---------------------------------------------------------------------------------------------
# Noise synthesis (no noise files needed: everything is generated or built from the clips)
# ---------------------------------------------------------------------------------------------

def _seed(*parts):
    return int.from_bytes(hashlib.sha256("|".join(map(str, parts)).encode()).digest()[:8], "little")


def _norm(x):
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12)


def white_noise(n, rng):
    return _norm(rng.standard_normal(n))


def pink_noise(n, rng):
    """1/f noise by spectral shaping of white noise."""
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.arange(len(spec), dtype=np.float64)
    f[0] = 1.0
    return _norm(np.fft.irfft(spec / np.sqrt(f), n))


def fan_noise(n, rng, swing_db=5.5):
    """As voice_eval_make.fan_noise: broadband noise whose level jumps +-5.5 dB every 30 ms
    (the pong fan), low-passed a little so it is not plain hiss."""
    base = pink_noise(n, rng) * 0.6 + white_noise(n, rng) * 0.4
    gains = 10 ** (rng.uniform(-swing_db, swing_db, n // FRAME_SAMPLES + 1) / 20)
    return _norm(base * np.repeat(gains, FRAME_SAMPLES)[:n])


class SpeechPool:
    """Speech taken from the clip set itself, to build TV and babble noise without noise files.

    Only the phrase body (between the 1.0 s lead and the 1.5 s tail) of *near* clips is used, so
    the pool is clean speech; a TV/babble bed for a clip never uses that clip's own voice+phrase.
    """

    def __init__(self, clips):
        self.items = []
        for clip in clips:
            if clip.condition != "near" or clip.kind == "real":
                continue
            audio = load_wav(clip.path).astype(np.float64)
            body = audio[int(LEAD_S * SR):len(audio) - int(TAIL_S * SR)]
            if len(body) > SR // 4:
                self.items.append((clip.align_key, _norm(body)))

    def stream(self, n, rng, exclude=None, gap_s=(0.05, 0.3)):
        """Continuous speech of n samples: random pool phrases back to back with short gaps."""
        pool = [b for k, b in self.items if k != exclude] or [b for _, b in self.items]
        if not pool:
            return white_noise(n, rng)
        out = []
        total = 0
        while total < n:
            body = pool[rng.integers(len(pool))]
            gap = np.zeros(int(rng.uniform(*gap_s) * SR))
            out += [body, gap]
            total += len(body) + len(gap)
        return np.concatenate(out)[:n]


def tv_noise(n, rng, pool, exclude=None):
    """One other voice talking continuously, band-limited and reverberant like a TV speaker."""
    from scipy.signal import butter, fftconvolve, sosfilt
    speech = pool.stream(n, rng, exclude)
    sos = butter(4, [150, 5000], btype="band", fs=SR, output="sos")
    speech = sosfilt(sos, speech)
    rt60 = 0.5
    k = int(SR * rt60)
    rir = rng.standard_normal(k) * np.exp(-6.9 * np.arange(k) / SR / rt60)
    rir[0] = 1.0
    return _norm(fftconvolve(speech, rir)[:n])


def babble_noise(n, rng, pool, exclude=None, talkers=6):
    """Six overlapping talkers: the classic cafeteria babble, built from the clip set's speech."""
    mix = np.zeros(n)
    for _ in range(talkers):
        mix += pool.stream(n, rng, exclude, gap_s=(0.0, 0.1))
    return _norm(mix)


def make_noise(kind, n, rng, pool, exclude=None):
    if kind == "white":
        return white_noise(n, rng)
    if kind == "pink":
        return pink_noise(n, rng)
    if kind == "fan":
        return fan_noise(n, rng)
    if kind == "tv":
        return tv_noise(n, rng, pool, exclude)
    if kind == "babble":
        return babble_noise(n, rng, pool, exclude)
    raise ValueError("unknown noise " + kind)


def speech_rms(audio, clip):
    """The speech level an SNR is measured against: RMS over the phrase body for a synthetic
    clip (between the 1.0 s lead and 1.5 s tail); for a real clip, RMS of its loudest 50 % of
    30 ms frames (a rough active-speech level, ITU P.56 in spirit)."""
    if clip.kind != "real":
        body = audio[int(LEAD_S * SR):len(audio) - int(TAIL_S * SR)]
        if len(body) > FRAME_SAMPLES:
            return float(np.sqrt(np.mean(body.astype(np.float64) ** 2)))
    n = len(audio) // FRAME_SAMPLES
    frames = audio[:n * FRAME_SAMPLES].astype(np.float64).reshape(n, FRAME_SAMPLES)
    energy = np.sort(np.mean(frames ** 2, axis=1))
    return float(np.sqrt(np.mean(energy[n // 2:]))) if n else 1e-4


# ---------------------------------------------------------------------------------------------
# Variants
# ---------------------------------------------------------------------------------------------

def variant_id(clip_name, noise="none", snr=None):
    return f"{clip_name}|{noise}|{'-' if snr is None else snr}"


def parse_variant(vid):
    clip_name, noise, snr = vid.rsplit("|", 2)
    return clip_name, noise, (None if snr == "-" else int(snr))


class VariantFactory:
    """Rebuilds any variant's audio on demand from the clip files and its id."""

    def __init__(self, clips):
        self.clips = {c.name: c for c in clips}
        self._pool = None
        self._all = clips

    @property
    def pool(self):
        if self._pool is None:
            self._pool = SpeechPool(self._all)
        return self._pool

    def audio(self, vid):
        """float32 [-1, 1], quantised to PCM16 levels as the microphone would deliver it."""
        name, noise, snr = parse_variant(vid)
        if name.startswith("noise:"):
            return self._noise_only(name[6:], noise, snr)
        clip = self.clips[name]
        audio = load_wav(clip.path).astype(np.float64)
        if noise != "none":
            rng = np.random.default_rng(_seed(vid))
            level = speech_rms(audio, clip) / 10 ** (snr / 20)
            audio = audio + make_noise(noise, len(audio), rng, self.pool, exclude=clip.align_key) * level
        return (to_pcm16(audio).astype(np.float32) / 32768.0)

    def _noise_only(self, source, noise, snr, seconds=20.0):
        """Speech-free audio for the false-segment and hallucination counts.

        source = a condition (near/far/fan/tv): the room between phrases of that condition's own
        clips (0-0.9 s lead and the last 0.9 s of tail of each clip, 10 ms cross-fades), i.e. the
        generator's exact room tone, fan and TV bed with no target speech. With a noise/snr on top,
        the added noise sits at the level it would have against that condition's median speech.
        """
        rng = np.random.default_rng(_seed("noise", source, noise, snr))
        members = [c for c in self._all if c.condition == source and c.kind != "real"]
        if not members:
            raise ValueError("no clips of condition " + source)
        fade = int(0.01 * SR)
        ramp = np.linspace(0, 1, fade)
        target = int(seconds * SR)
        out = np.zeros(0)
        levels = []
        order = rng.permutation(len(members))
        i = 0
        while len(out) < target:
            clip = members[order[i % len(order)]]
            i += 1
            audio = load_wav(clip.path).astype(np.float64)
            levels.append(speech_rms(audio, clip))
            for piece in (audio[:int(0.9 * SR)], audio[-int(0.9 * SR):]):
                piece = piece.copy()
                if len(out) >= fade:
                    out[-fade:] = out[-fade:] * ramp[::-1] + piece[:fade] * ramp
                    out = np.concatenate([out, piece[fade:]])
                else:
                    out = piece
        out = out[:target]
        if noise != "none":
            level = float(np.median(levels)) / 10 ** (snr / 20)
            out = out + make_noise(noise, len(out), rng, self.pool) * level
        return to_pcm16(out).astype(np.float32) / 32768.0


def speech_variants(clips, noises, snrs, augment_conditions):
    """Every clip clean, plus every clip of augment_conditions under each noise x SNR."""
    out = []
    for clip in clips:
        out.append(variant_id(clip.name))
        if clip.condition in augment_conditions or (clip.kind == "real" and "real" in augment_conditions):
            for noise in noises:
                for snr in snrs:
                    out.append(variant_id(clip.name, noise, snr))
    return out


def noise_only_variants(clips, noises, snrs, augment_conditions):
    conds = sorted({c.condition for c in clips if c.kind != "real"})
    out = [variant_id("noise:" + c) for c in conds]
    for c in conds:
        if c in augment_conditions:
            for noise in noises:
                for snr in snrs:
                    out.append(variant_id("noise:" + c, noise, snr))
    return out


def noise_label(vid):
    """The report's noise column: "clean" or "<noise>@<snr>dB"."""
    _, noise, snr = parse_variant(vid)
    return "clean" if noise == "none" else f"{noise}@{snr}dB"


def read_jsonl(path):
    if not os.path.isfile(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def add_set_args(parser):
    """The clip-selection flags vad_compare.py and stt_compare.py share."""
    parser.add_argument("--set", default=DEFAULT_SET, help="synthetic set dir (voice_eval_make.py output)")
    parser.add_argument("--real", default=None, help="optional dir of real WAVs (+ <stem>.txt truth)")
    parser.add_argument("--conditions", default="near,far,fan,tv", help="set conditions to use")
    parser.add_argument("--kinds", default=None, help="e.g. dictation or key,text,dictation (default all)")
    parser.add_argument("--voices", default=None, help="e.g. lessac,alan (default all)")
    parser.add_argument("--limit", type=int, default=None, help="first N clips only")
    parser.add_argument("--noises", default=",".join(DEFAULT_NOISES), help="noise types; '' for none")
    parser.add_argument("--snrs", default=",".join(map(str, DEFAULT_SNRS)), help="SNRs in dB")
    parser.add_argument("--augment", default="near",
                        help="conditions that also get the noise x SNR variants (default near: "
                             "the clean close-mic speech, so the added noise is the only change)")
    parser.add_argument("--out", default=OUT)


def select(args):
    split = lambda s: [x for x in (s or "").split(",") if x]  # noqa: E731
    clips = load_clip_set(args.set, split(args.conditions), split(args.kinds), split(args.voices), args.limit)
    # The speech pool needs near clips even when --conditions leaves them out.
    pool_clips = load_clip_set(args.set, ["near"]) if "near" not in split(args.conditions) else []
    clips += load_real_clips(args.real)
    noises = split(args.noises)
    snrs = [int(s) for s in split(args.snrs)]
    augment = split(args.augment)
    factory = VariantFactory(clips + [c for c in pool_clips if c.name not in {x.name for x in clips}])
    return clips, factory, noises, snrs, augment
