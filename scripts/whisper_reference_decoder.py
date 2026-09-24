"""Reference Whisper-ACFT decoder for the LiteRT graphs (numpy + ai-edge-litert). Generates app/src/test/resources/whisper fixtures; see project-docs/plans/whisper-voice-input.md."""
import json, sys, time, wave, glob, os
import numpy as np
from ai_edge_litert.interpreter import Interpreter

SR, NFFT, HOP, NMELS = 16000, 400, 160, 80


def hz_to_mel(f):
    f = np.asarray(f, dtype=np.float64)
    fsp = 200.0 / 3
    mels = f / fsp
    min_log_hz, min_log_mel, logstep = 1000.0, 1000.0 / fsp, np.log(6.4) / 27.0
    return np.where(f >= min_log_hz, min_log_mel + np.log(np.maximum(f, 1e-10) / min_log_hz) / logstep, mels)


def mel_to_hz(m):
    m = np.asarray(m, dtype=np.float64)
    fsp = 200.0 / 3
    freqs = fsp * m
    min_log_hz, min_log_mel, logstep = 1000.0, 1000.0 / fsp, np.log(6.4) / 27.0
    return np.where(m >= min_log_mel, min_log_hz * np.exp(logstep * (m - min_log_mel)), freqs)


def mel_filters():
    fftfreqs = np.linspace(0, SR / 2, NFFT // 2 + 1)
    mel_f = mel_to_hz(np.linspace(hz_to_mel(0), hz_to_mel(SR / 2), NMELS + 2))
    fdiff = np.diff(mel_f)
    ramps = mel_f[:, None] - fftfreqs[None, :]
    w = np.zeros((NMELS, NFFT // 2 + 1))
    for i in range(NMELS):
        lower = -ramps[i] / fdiff[i]
        upper = ramps[i + 2] / fdiff[i + 1]
        w[i] = np.maximum(0, np.minimum(lower, upper))
    enorm = 2.0 / (mel_f[2:NMELS + 2] - mel_f[:NMELS])
    return (w * enorm[:, None]).astype(np.float32)


FILTERS = mel_filters()
WINDOW = (0.5 - 0.5 * np.cos(2 * np.pi * np.arange(NFFT) / NFFT)).astype(np.float32)  # periodic Hann


def log_mel(audio, frames):
    audio = np.concatenate([audio, np.zeros(max(0, frames * HOP - len(audio)), np.float32)])[: frames * HOP]
    padded = np.pad(audio, NFFT // 2, mode="reflect")
    n = 1 + (len(padded) - NFFT) // HOP
    idx = np.arange(NFFT)[None, :] + HOP * np.arange(n)[:, None]
    spec = np.fft.rfft(padded[idx] * WINDOW, axis=1)
    power = (np.abs(spec) ** 2)[:-1].T  # drop last frame like whisper
    mel = FILTERS @ power
    logm = np.log10(np.maximum(mel, 1e-10))
    logm = np.maximum(logm, logm.max() - 8.0)
    return ((logm + 4.0) / 4.0)[:, :frames].astype(np.float32)


def bytes_to_unicode():
    bs = list(range(ord("!"), ord("~") + 1)) + list(range(ord("¡"), ord("¬") + 1)) + list(range(ord("®"), ord("ÿ") + 1))
    cs = bs[:]
    n = 0
    for b in range(256):
        if b not in bs:
            bs.append(b); cs.append(256 + n); n += 1
    return {chr(c): b for b, c in zip(bs, cs)}


class Tok:
    def __init__(self, path):
        j = json.load(open(path))
        self.id2tok = {v: k for k, v in j["model"]["vocab"].items()}
        self.special = {}
        for t in j["added_tokens"]:
            self.id2tok[t["id"]] = t["content"]; self.special[t["content"]] = t["id"]
        self.b = bytes_to_unicode()

    def decode(self, ids):
        s = "".join(self.id2tok[i] for i in ids if not self.id2tok[i].startswith("<|"))
        return bytearray(self.b[c] for c in s).decode("utf-8", "replace")


def load_wav(p):
    with wave.open(p) as w:
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768.0


class Whisper:
    def __init__(self, model, tok, threads=4, mask_mode="neg"):
        self.it = Interpreter(model_path=model, num_threads=threads)
        self.enc = self.it.get_signature_runner("encode")
        self.dec = self.it.get_signature_runner("decode")
        self.frames = int(self.enc.get_input_details()["args_0"]["shape"][2])
        d = self.dec.get_input_details()
        self.seq = int(d["args_1"]["shape"][1])
        self.tok = Tok(tok)
        self.english_only = "<|en|>" not in self.tok.special
        self.mask_mode = mask_mode

    def prompt(self, lang="en"):
        s = self.tok.special
        if self.english_only:
            return [s["<|startoftranscript|>"], s["<|notimestamps|>"]]
        return [s["<|startoftranscript|>"], s[f"<|{lang}|>"], s["<|transcribe|>"], s["<|notimestamps|>"]]

    def mask(self):
        m = np.triu(np.ones((self.seq, self.seq), np.float32), 1)
        m = m * (-1e9 if self.mask_mode == "neg" else 1.0)
        return m[None, None]

    def transcribe(self, audio, lang="en"):
        t0 = time.perf_counter()
        feats = log_mel(audio, self.frames)[None]
        enc = self.enc(args_0=feats)["output_0"]
        t1 = time.perf_counter()
        s = self.tok.special
        eot = s["<|endoftext|>"]
        ts_begin = s["<|notimestamps|>"] + 1
        toks = self.prompt(lang)
        n0 = len(toks)
        mask = self.mask()
        steps = 0
        while len(toks) < self.seq:
            arr = np.full((1, self.seq), eot, np.int32)
            arr[0, : len(toks)] = toks
            logits = self.dec(args_0=enc, args_1=arr, args_2=mask)["output_0"][0, len(toks) - 1].copy()
            steps += 1
            logits[ts_begin:] = -np.inf
            for name in ("<|startoftranscript|>", "<|translate|>", "<|transcribe|>", "<|startoflm|>", "<|startofprev|>", "<|nocaptions|>", "<|nospeech|>"):
                if name in s: logits[s[name]] = -np.inf
            if len(toks) == n0: logits[eot] = -np.inf
            nxt = int(np.argmax(logits))
            if nxt == eot: break
            toks.append(nxt)
            if len(toks) - n0 >= 12 and toks[-4:] == toks[-8:-4] == toks[-12:-8]: break
        t2 = time.perf_counter()
        return self.tok.decode(toks[n0:]).strip(), t1 - t0, t2 - t1, steps


if __name__ == "__main__":
    model, tok = sys.argv[1], sys.argv[2]
    mode = sys.argv[3] if len(sys.argv) > 3 else "neg"
    w = Whisper(model, tok, mask_mode=mode)
    for p in sorted(glob.glob("clips/*.wav")):
        a = load_wav(p)
        text, te, td, st = w.transcribe(a)
        print(f"{os.path.basename(p)[:-4][:60]:60s} {len(a)/SR:4.1f}s enc {te:.2f} dec {td:.2f} ({st:2d}) | {text}")
