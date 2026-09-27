"""The three LiteRT TTS pipelines, host glue ported from each repo's reference Python.

Every engine exposes the same small surface so tts_eval.py can time them the same way:

    eng.name, eng.sample_rate, eng.voices, eng.punct
    eng.chunks(text)                       -> the sentence-level synthesis units
    eng.synth(chunk, g2p_result, voice, i) -> float32 PCM for one chunk, plus a note dict
    eng.join(pieces, chunks)               -> the full utterance (pauses where the reference adds them)

Sources:
  Kitten  - litert-community/kitten-tts-nano-0.8 say.py, and litert-samples
            text_to_speech_streaming/python/kitten_tts.py (sentence chunker, tail trim)
  Inflect - litert-community/Inflect-Nano-v2 say.py (splitting, pauses, edge fade, seeds)
  Matcha  - litert-community/Matcha-TTS README reference Python and litert-samples
            MatchaSynthesizer.kt (blanks, length regulator, Euler loop, denormalise)
"""
import math
import re

import numpy as np

try:
    from ai_edge_litert.interpreter import Interpreter
except ImportError:  # pragma: no cover
    from tflite_runtime.interpreter import Interpreter


def _canon(name):
    name = name.split(":")[0]
    return name[len("serving_default_"):] if name.startswith("serving_default_") else name


class Graph:
    """Interpreter wrapper: resize dynamic inputs, allocate, reset LSTM state, invoke.

    `reset_all_variables()` after every allocate is what the Kitten card requires: the fused
    TFLite LSTM kernels keep their hidden state in variable tensors across invoke(), and a
    reused interpreter without the reset corrupts the next utterance. It is harmless for the
    graphs that have no variables.
    """

    def __init__(self, path, threads, dynamic=True):
        self.it = Interpreter(model_path=str(path), num_threads=threads)
        self.inputs = sorted(self.it.get_input_details(), key=lambda d: d["name"])
        self.outputs = self.it.get_output_details()
        self.dynamic = dynamic
        self._shapes = None
        if not dynamic:
            self.it.allocate_tensors()

    def __call__(self, feeds):
        """feeds: {canonical input name: array} or a list in sorted-name order."""
        if isinstance(feeds, dict):
            arrays = [feeds[_canon(d["name"])] for d in self.inputs]
        else:
            arrays = list(feeds)
        if self.dynamic:
            shapes = [tuple(a.shape) for a in arrays]
            if shapes != self._shapes:
                for d, a in zip(self.inputs, arrays):
                    self.it.resize_tensor_input(d["index"], list(a.shape))
                self.it.allocate_tensors()
                self._shapes = shapes
            self.it.reset_all_variables()
        for d, a in zip(self.inputs, arrays):
            self.it.set_tensor(d["index"], np.ascontiguousarray(a, dtype=d["dtype"]))
        self.it.invoke()
        return {o["name"]: self.it.get_tensor(o["index"]) for o in self.outputs}


def _by_suffix(outputs, suffix):
    for name, value in outputs.items():
        if name.endswith(suffix):
            return value
    raise KeyError(f"no output ending {suffix!r} in {list(outputs)}")


def edge_fade(wav, sr, ms=5.0):
    n = min(round(sr * ms / 1000.0), wav.size // 2)
    if n <= 0:
        return wav
    ramp = np.linspace(0.0, 1.0, n, dtype=np.float32)
    wav = wav.copy()
    wav[:n] *= ramp
    wav[-n:] *= ramp[::-1]
    return wav


def pause_seconds(chunk):
    """Inflect's inter-sentence pause by the chunk's final punctuation (its say.py)."""
    return {"?": 0.28, "!": 0.24, ".": 0.22, ";": 0.16, ":": 0.13, ",": 0.09}.get(
        chunk.rstrip()[-1:], 0.08)


# ============================================================================== KittenTTS nano

# StyleTTS2 / Kokoro 178-symbol table, verbatim from the repo's say.py.
KITTEN_PAD = "$"
KITTEN_PUNCT = ';:,.!?¡¿—…"«»“” '
KITTEN_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
KITTEN_IPA = ("ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ")
KITTEN_SYMBOLS = [KITTEN_PAD] + list(KITTEN_PUNCT) + list(KITTEN_LETTERS) + list(KITTEN_IPA)

KITTEN_VOICES = {"Bella": "expr-voice-2-f", "Jasper": "expr-voice-2-m",
                 "Luna": "expr-voice-3-f", "Bruno": "expr-voice-3-m",
                 "Rosie": "expr-voice-4-f", "Hugo": "expr-voice-4-m",
                 "Kiki": "expr-voice-5-f", "Leo": "expr-voice-5-m"}
KITTEN_SPEED_PRIORS = {"expr-voice-2-f": 0.8, "expr-voice-2-m": 0.8, "expr-voice-3-m": 0.8,
                       "expr-voice-3-f": 0.8, "expr-voice-4-m": 0.9, "expr-voice-4-f": 0.8,
                       "expr-voice-5-m": 0.8, "expr-voice-5-f": 0.8}


class Kitten:
    name = "kitten"
    sample_rate = 24000
    punct = "spaced"          # KittenG2P: punctuation is its own space-separated token
    TAIL_TRIM = 5000          # the pip package trims this many samples off every chunk
    MIN_SAMPLES = 1200

    def __init__(self, fetch, threads=4, speed=1.0):
        repo = "litert-community/kitten-tts-nano-0.8"
        self.predictor = Graph(fetch.path(repo, "kitten_predictor.tflite"), threads)
        self.prosody = Graph(fetch.path(repo, "kitten_prosody.tflite"), threads)
        self.vocoder = Graph(fetch.path(repo, "kitten_vocoder.tflite"), threads)
        self.table = np.load(fetch.path(repo, "voices.npz"))
        self.voices = list(KITTEN_VOICES)
        self.speed = speed

    @staticmethod
    def chunks(text):
        """litert-samples chunk_text / the pip package: split on [.!?]+ (consumed), and end
        every chunk with punctuation, adding "," when it has none. Kept on purpose: the
        model's prosody was tuned against this frontend."""
        out = []
        for sentence in re.split(r"[.!?]+", text):
            sentence = sentence.strip()
            if not sentence:
                continue
            while len(sentence) > 400:
                cut = sentence.rfind(" ", 0, 400)
                cut = cut if cut > 200 else 400
                piece = sentence[:cut].strip()
                out.append(piece if piece[-1] in ".!?,;:" else piece + ",")
                sentence = sentence[cut:].strip()
            out.append(sentence if sentence[-1] in ".!?,;:" else sentence + ",")
        return out

    def synth(self, chunk, g2p, voice, index):
        key = KITTEN_VOICES.get(voice, voice)
        rows = self.table[key]
        row = min(len(chunk), rows.shape[0] - 1)   # one style row per text length (pip package)
        style = rows[row:row + 1].astype(np.float32)
        speed = np.array([self.speed * KITTEN_SPEED_PRIORS.get(key, 1.0)], np.float32)
        ids = np.array([[0] + list(g2p.ids) + [0]], np.int32)

        p = self.predictor({"input_ids": ids, "style": style, "speed": speed})
        t_en = _by_suffix(p, ":0")        # [1,N,128]
        dur = _by_suffix(p, ":1")         # [N] int32
        d = _by_suffix(p, ":2")           # [1,N,256]
        dur = np.maximum(dur.astype(np.int64), 0)
        en = np.repeat(d[0], dur, axis=0)[None].astype(np.float32)
        asr = np.repeat(t_en[0], dur, axis=0)[None].astype(np.float32)
        pr = self.prosody({"en": en, "style": style})
        f0, n, har = _by_suffix(pr, ":0"), _by_suffix(pr, ":1"), _by_suffix(pr, ":2")
        wav = next(iter(self.vocoder({"asr": asr, "f0": f0, "n": n, "har": har,
                                      "style": style}).values()))[0]
        keep = max(wav.size - self.TAIL_TRIM, min(self.MIN_SAMPLES, wav.size))
        return np.clip(wav[:keep], -1, 1).astype(np.float32), {
            "tokens": int(ids.shape[1]), "frames": int(dur.sum())}

    def join(self, pieces, chunks):
        return np.concatenate(pieces) if pieces else np.zeros(0, np.float32)


# ============================================================================ Inflect-Nano-v2

class Inflect:
    name = "inflect"
    sample_rate = 24000
    punct = "attached"        # espeak preserve_punctuation style: "wˈɜːld,"
    voices = ["male"]         # the checkpoint has one fixed male voice

    def __init__(self, fetch, threads=4, speed=1.0, variation=0.667, seed=0):
        repo = "litert-community/Inflect-Nano-v2"
        self.encoder = Graph(fetch.path(repo, "inflect_text_encoder.tflite"), threads)
        self.decoder = Graph(fetch.path(repo, "inflect_decoder.tflite"), threads)
        self.speed, self.variation, self.seed = speed, variation, seed

    @staticmethod
    def chunks(text, limit=280):
        normalized = " ".join(text.split())
        sentences = [p.strip() for p in re.split(r"(?<=[.!?;:])\s+", normalized) if p.strip()]
        out = []
        for s in sentences or [normalized]:
            while len(s) > limit:
                cut = s.rfind(" ", 0, limit + 1)
                cut = cut if cut >= limit // 2 else limit
                out.append(s[:cut].strip())
                s = s[cut:].strip()
            if s:
                out.append(s)
        return out

    def synth(self, chunk, g2p, voice, index):
        seq = list(g2p.ids)
        tokens = [0] * (2 * len(seq) + 1)     # blank between phonemes (and at both ends)
        tokens[1::2] = seq
        e = self.encoder([np.array([tokens], np.int32)])
        outs = [e[k] for k in sorted(e)]      # Identity, Identity_1, Identity_2
        m_p, logs_p = [v for v in outs if v.shape[-1] == 128]
        logw = [v for v in outs if v.shape[-1] == 1][0]
        dur = np.maximum(np.ceil(np.exp(logw[0, :, 0]) / self.speed).astype(np.int64), 0)
        m = np.repeat(m_p[0], dur, axis=0)[None]
        logs = np.repeat(logs_p[0], dur, axis=0)[None]
        noise = np.random.RandomState(self.seed + index).randn(*m.shape).astype(np.float32)
        z_p = (m + noise * np.exp(logs) * self.variation).astype(np.float32)
        wav = next(iter(self.decoder([z_p]).values()))[0]
        return edge_fade(wav.astype(np.float32), self.sample_rate), {
            "tokens": len(tokens), "frames": int(dur.sum())}

    def join(self, pieces, chunks):
        out = []
        for i, pcm in enumerate(pieces):
            if i:
                out.append(np.zeros(round(self.sample_rate * pause_seconds(chunks[i - 1])),
                                    np.float32))
            out.append(pcm)
        return np.clip(np.concatenate(out), -1, 1) if out else np.zeros(0, np.float32)


# ================================================================================ Matcha-TTS

class Matcha:
    name = "matcha"
    sample_rate = 22050
    punct = "attached"        # MatchaG2P: punctuation glued to the preceding word
    voices = ["ljspeech"]
    MAXT, MAXM, NF, NC, HOP, TDIM = 256, 512, 80, 192, 256, 160

    def __init__(self, fetch, threads=4, steps=10, seed=0, temperature=1.0):
        import json
        repo = "litert-community/Matcha-TTS"
        cfg = json.load(open(fetch.path(repo, "config.json"), encoding="utf-8"))
        self.symbols = cfg["symbols"]
        self.mel_mean, self.mel_std = cfg["mel_mean"], cfg["mel_std"]
        self.length_scale = cfg["length_scale"]
        self.emb = np.fromfile(fetch.path(repo, "emb.bin"), "<f4").reshape(cfg["n_vocab"],
                                                                            cfg["n_channels"])
        self.textenc = Graph(fetch.path(repo, "matcha_textenc_fp16.tflite"), threads, False)
        self.decoder = Graph(fetch.path(repo, "matcha_decoder_fp16.tflite"), threads, False)
        self.vocoder = Graph(fetch.path(repo, "matcha_vocoder_fp16.tflite"), threads, False)
        self.steps, self.seed, self.temperature = steps, seed, temperature
        self.g2p = None           # set by the caller; needed to re-phonemize split halves
        self.frontend = "google"

    @staticmethod
    def chunks(text):
        # The Android sample synthesises the whole text in one 512-frame window (~5.9 s).
        # Sentences are the natural unit for time-to-first-audio; synth() splits further at
        # commas if a sentence still does not fit.
        return [s.strip() for s in re.split(r"(?<=[.!?])\s+", " ".join(text.split()))
                if s.strip()]

    def _t_sin(self, t, scale=1000.0):
        half = self.TDIM // 2
        e = scale * t * np.exp(np.arange(half) * -math.log(10000.0) / (half - 1))
        return np.concatenate([np.sin(e), np.cos(e)]).astype(np.float32)[None]

    def _encode(self, pids):
        n_fit = min(len(pids), (self.MAXT - 1) // 2)    # 127 phonemes + 128 blanks
        text_len = 2 * n_fit + 1
        ids = np.zeros(self.MAXT, np.int64)
        ids[1:2 * n_fit:2] = pids[:n_fit]                 # intersperse blanks (id 0)
        tmask = (np.arange(self.MAXT) < text_len).astype(np.float32)[None, None]
        out = self.textenc([self.emb[ids][None].astype(np.float32), tmask])
        mu = _by_suffix(out, "output_0_output")      # [1,80,256]
        logw = _by_suffix(out, "output_1_output")    # [1,1,256]
        w = np.ceil(np.exp(logw[0, 0]) * tmask[0, 0]) * self.length_scale
        cum = np.cumsum(w)
        return mu, cum, float(cum[-1]), len(pids) > n_fit

    def _decode(self, mu, cum, index):
        ylen = int(min(max(int(cum[-1]), 1), self.MAXM))
        mu_y = np.zeros((1, self.NF, self.MAXM), np.float32)
        mu_y[0, :, :ylen] = mu[0][:, np.searchsorted(cum, np.arange(ylen), "right")
                                  .clip(max=self.MAXT - 1)]
        ymask = (np.arange(self.MAXM) < ylen).astype(np.float32)[None, None]
        rng = np.random.RandomState(self.seed + index)
        x = np.zeros((1, self.NF, self.MAXM), np.float32)
        x[0, :, :ylen] = rng.randn(self.NF, ylen) * self.temperature
        for k in range(self.steps):
            v = next(iter(self.decoder([x, mu_y, self._t_sin(k / self.steps), ymask]).values()))
            x = x + v / self.steps
        mel = np.zeros_like(x)
        mel[0, :, :ylen] = x[0, :, :ylen] * self.mel_std + self.mel_mean
        wav = next(iter(self.vocoder([mel]).values())).reshape(-1)[: ylen * self.HOP]
        return np.clip(wav, -1, 1).astype(np.float32), ylen

    @staticmethod
    def _split(chunk):
        """Split at the comma nearest the middle, else at the middle word boundary."""
        commas = [m.end() for m in re.finditer(r"[,;:—]\s", chunk)]
        mid = len(chunk) / 2
        if commas:
            cut = min(commas, key=lambda c: abs(c - mid))
        else:
            cut = chunk.rfind(" ", 0, int(mid) + 1)
            if cut <= 0:
                return None
        a, b = chunk[:cut].strip(), chunk[cut:].strip()
        return (a, b) if a and b else None

    def synth(self, chunk, g2p, voice, index, _depth=0):
        mu, cum, frames, text_over = self._encode(g2p.ids)
        if (frames > self.MAXM or text_over) and _depth < 4 and self.g2p is not None:
            halves = self._split(chunk)
            if halves:
                pieces, notes = [], {"tokens": 0, "frames": 0, "splits": 1, "truncated": False}
                for j, part in enumerate(halves):
                    r = self.g2p.phonemize(part, self.punct, self.frontend)
                    pcm, n = self.synth(part, r, voice, index * 10 + j, _depth + 1)
                    if pieces:
                        pieces.append(np.zeros(round(self.sample_rate * 0.09), np.float32))
                    pieces.append(pcm)
                    notes["tokens"] += n["tokens"]
                    notes["frames"] += n["frames"]
                    notes["splits"] += n.get("splits", 0)
                    notes["truncated"] |= n.get("truncated", False)
                return np.concatenate(pieces), notes
        wav, ylen = self._decode(mu, cum, index)
        return wav, {"tokens": min(2 * len(g2p.ids) + 1, self.MAXT), "frames": ylen,
                     "truncated": frames > self.MAXM or text_over}

    def join(self, pieces, chunks):
        out = []
        for i, pcm in enumerate(pieces):
            if i:
                out.append(np.zeros(round(self.sample_rate * pause_seconds(chunks[i - 1])),
                                    np.float32))
            out.append(pcm)
        return np.concatenate(out) if out else np.zeros(0, np.float32)
