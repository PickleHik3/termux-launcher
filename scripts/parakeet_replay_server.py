"""Host-side Parakeet TDT server for VoiceReplayRig, the counterpart of whisper_replay_server.py.

Runs NVIDIA parakeet-tdt-0.6b-v3 from Google's LiteRT conversion
(litert-community/parakeet-tdt-0.6b-v3 -> parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite, with
nvidia/parakeet-tdt-0.6b-v3's tokenizer.json) so the rig can compare it with Whisper on the same
segments. See project-docs/parakeet-stt-research.md.

Same protocol as whisper_replay_server.py, JSON lines on stdin/stdout:
  in:  {"wav": "<16 kHz mono PCM16 WAV>", "terminal": true|false}   ("terminal" is ignored:
       Parakeet has no prompt to bias)
  out: {"text": "...", "encode_ms": 210, "decode_ms": 340, "steps": 6}  or {"error": "..."}

argv: model_path tokenizer_path

Front end: NeMo's AudioToMelSpectrogramPreprocessor as the model was trained with it (the LiteRT
converter's verify_tflite.py feeds the encoder NeMo features): pre-emphasis 0.97, 25 ms Hann window
(400 samples) in a 512-point FFT, 10 ms hop, centred with zero padding, power spectrum, 128 slaney
mels, ln(x + 2^-24), per-bin mean/std over the valid frames (std with N-1, + 1e-5), zeros after.
No dither, so replays are deterministic.

Decoder: the greedy TDT loop of the sample's TdtDecoder.kt (litert-samples a1de5c7c): the 4-slot
stateless `decode` until four tokens are out, then the stateful `decode_1`, adopting the new LSTM
state only on a non-blank emission; the duration head moves the time index (at least one frame on
a blank).

Windows: the graph takes exactly 5 s. Longer audio is cut into 5 s pieces at the quietest 30 ms
frame of each piece's last second (as WhisperSegmenter cuts at its window) and the texts joined.
"""
import json
import sys
import time

import numpy as np
from ai_edge_litert.interpreter import Interpreter

SR = 16000
N_FFT = 512
WIN = 400
HOP = 160
N_MELS = 128
FRAMES = 500
WINDOW_SAMPLES = SR * 5
PREEMPH = 0.97
LOG_GUARD = 2.0 ** -24
BLANK = 8192
NUM_DURATIONS = 5
MAX_TOKENS = 80
FRAME_SAMPLES = SR * 30 // 1000
PAD_NOISE = 1e-4  # about -80 dBFS: silence, but not a constant log-guard floor


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
    """librosa.filters.mel(sr=16000, n_fft=512, n_mels=128, norm='slaney'), as NeMo builds it."""
    fftfreqs = np.linspace(0, SR / 2, N_FFT // 2 + 1)
    mel_f = mel_to_hz(np.linspace(hz_to_mel(0), hz_to_mel(SR / 2), N_MELS + 2))
    fdiff = np.diff(mel_f)
    ramps = mel_f[:, None] - fftfreqs[None, :]
    w = np.zeros((N_MELS, N_FFT // 2 + 1))
    for i in range(N_MELS):
        lower = -ramps[i] / fdiff[i]
        upper = ramps[i + 2] / fdiff[i + 1]
        w[i] = np.maximum(0, np.minimum(lower, upper))
    enorm = 2.0 / (mel_f[2:N_MELS + 2] - mel_f[:N_MELS])
    return (w * enorm[:, None]).astype(np.float64)


FILTERS = mel_filters()
# torch.hann_window(400, periodic=False), zero-padded to the 512-point FFT, centred.
_HANN = 0.5 - 0.5 * np.cos(2 * np.pi * np.arange(WIN) / (WIN - 1))
WINDOW = np.zeros(N_FFT)
WINDOW[(N_FFT - WIN) // 2:(N_FFT - WIN) // 2 + WIN] = _HANN


def features(audio):
    """[1, 128, 500] float32 NeMo log-mel features for up to 5 s of audio in [-1, 1].

    A short segment is padded with near-silent *audio* to the full 5 s before the features are
    taken, and normalised over the whole window. Padding the *features* with zeros instead (the
    Android sample's way) reads as "average sound" after per-bin normalisation; the graph has no
    length input to mask it, and on a 1-1.5 s phrase that filled 70-80 % of the window and made
    the model repeat itself ("Enter key. Enter key", "Clear clear clear ...").
    """
    audio = audio[:WINDOW_SAMPLES].astype(np.float64)
    if len(audio) < WINDOW_SAMPLES:
        tail = np.random.default_rng(len(audio)).standard_normal(WINDOW_SAMPLES - len(audio)) * PAD_NOISE
        audio = np.concatenate([audio, tail])
    emphasised = np.concatenate([audio[:1], audio[1:] - PREEMPH * audio[:-1]])
    padded = np.pad(emphasised, (N_FFT // 2, N_FFT // 2), mode="constant")
    n_frames = 1 + (len(padded) - N_FFT) // HOP
    idx = np.arange(N_FFT)[None, :] + HOP * np.arange(n_frames)[:, None]
    spec = np.abs(np.fft.rfft(padded[idx] * WINDOW[None, :], n=N_FFT)) ** 2
    mel = np.log(spec @ FILTERS.T + LOG_GUARD).T  # [128, n_frames]
    valid = min(len(audio) // HOP + 1, FRAMES, mel.shape[1])
    out = np.zeros((N_MELS, FRAMES), np.float64)
    head = mel[:, :valid]
    mean = head.mean(axis=1, keepdims=True)
    std = head.std(axis=1, ddof=1, keepdims=True) if valid > 1 else np.zeros_like(mean)
    out[:, :valid] = (head - mean) / (std + 1e-5)
    return out[None].astype(np.float32)


class Tokens:
    def __init__(self, path):
        t = json.load(open(path, encoding="utf-8"))
        vocab = t["model"]["vocab"]
        self.pieces = {i: p for p, i in vocab.items()}
        self.special = {a["id"] for a in t.get("added_tokens", [])}

    def decode(self, ids):
        text = "".join(self.pieces.get(i, "") for i in ids if i not in self.special and i != BLANK)
        return text.replace("▁", " ").strip()


class Parakeet:
    def __init__(self, model_path, tokenizer_path, threads=4):
        self.it = Interpreter(model_path=model_path, num_threads=threads)
        self.enc = self.it.get_signature_runner("encode")
        self.dec = self.it.get_signature_runner("decode")
        self.dec1 = self.it.get_signature_runner("decode_1")
        self.slots = int(self.dec.get_input_details()["args_1"]["shape"][1])
        self.tok = Tokens(tokenizer_path)

    def window(self, audio):
        t0 = time.perf_counter()
        enc = self.enc(args_0=features(audio))["output_0"]
        t1 = time.perf_counter()
        max_t = enc.shape[-1]
        zeros = np.zeros((2, 1, 640), np.float32)
        h, c = zeros, zeros
        emitted = []
        tokens = np.zeros((1, self.slots), np.int32)
        tokens[0, 0] = BLANK
        slot = 0
        stateful = False
        t = 0
        steps = 0
        while t < max_t and len(emitted) < MAX_TOKENS:
            if stateful:
                out = self.dec1(args_0=enc, args_1=tokens, args_2=h, args_3=c)
                logits = out["output_0"][0, t, 0]
            else:
                out = self.dec(args_0=enc, args_1=tokens, args_2=zeros, args_3=zeros)
                logits = out["output_0"][0, t, slot]
            steps += 1
            token = int(np.argmax(logits[:-NUM_DURATIONS]))
            if token != BLANK:
                emitted.append(token)
                if stateful:
                    h, c = out["output_1"], out["output_2"]
                    tokens[0, 0] = token
                else:
                    slot += 1
                    if slot < self.slots:
                        tokens[0, slot] = token
                    else:
                        # The token array is full: carry the state it built into decode_1.
                        stateful = True
                        h, c = out["output_1"], out["output_2"]
                        tokens = np.array([[token]], np.int32)
            duration = int(np.argmax(logits[-NUM_DURATIONS:]))
            t += 1 if (duration == 0 and token == BLANK) else duration
        t2 = time.perf_counter()
        return self.tok.decode(emitted), (t1 - t0) * 1000, (t2 - t1) * 1000, steps

    def transcribe(self, audio):
        texts, enc_ms, dec_ms, steps = [], 0.0, 0.0, 0
        for piece in split(audio):
            text, e, d, s = self.window(piece)
            if text:
                texts.append(text)
            enc_ms += e
            dec_ms += d
            steps += s
        return " ".join(texts), enc_ms, dec_ms, steps


def split(audio):
    """5 s pieces, each cut at the quietest 30 ms frame of its last second."""
    pieces = []
    start = 0
    while len(audio) - start > WINDOW_SAMPLES:
        search_from = start + WINDOW_SAMPLES - SR
        best, best_energy = start + WINDOW_SAMPLES, None
        for f in range(search_from, start + WINDOW_SAMPLES - FRAME_SAMPLES + 1, FRAME_SAMPLES):
            energy = float(np.sum(audio[f:f + FRAME_SAMPLES] ** 2))
            if best_energy is None or energy < best_energy:
                best, best_energy = f, energy
        pieces.append(audio[start:best])
        start = best
    pieces.append(audio[start:])
    return pieces


def load_wav(path):
    import wave
    with wave.open(path) as w:
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768.0


def main():
    model = Parakeet(sys.argv[1], sys.argv[2])
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
            text, enc_ms, dec_ms, steps = model.transcribe(load_wav(request["wav"]))
            reply = {"text": text, "encode_ms": int(enc_ms), "decode_ms": int(dec_ms), "steps": steps}
        except Exception as e:  # noqa: BLE001 - one bad clip must not end the run
            reply = {"error": f"{type(e).__name__}: {e}"}
        sys.stdout.write(json.dumps(reply) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
