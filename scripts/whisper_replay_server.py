"""Host-side Whisper server for VoiceReplayRig (app/src/test/.../voice/VoiceReplayRig.java).

Started once per rig run over a plain process, JSON lines on stdin/stdout:
  in:  {"wav": "<path to a 16 kHz mono PCM16 WAV>"}
  out: {"text": "...", "encode_ms": 210, "decode_ms": 340, "steps": 6}
    or {"error": "..."} on a decode failure

Reuses whisper_reference_decoder's mel front end, tokenizer decode and interpreter setup; the
prompt (task tokens) and the suppression ids are *not* rebuilt here. They come in once, at
startup, as argv from VoiceReplayRig, which reads them straight out of the real
com.termux.ai.WhisperDecoder/WhisperTokenizer by reflection — so the prompt this server runs is
exactly the one the app's tokenizer.json BPE would produce, with no second implementation of it
to drift out of step.

argv: model_path tokenizer_path prompt eot timestamp_begin always
  prompt / always: comma-separated token ids, or "-" for none.
"""
import json
import sys
import time

import numpy as np

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from whisper_reference_decoder import Whisper, Tok, load_wav, log_mel, SR  # noqa: E402

FRAME_SAMPLES = SR * 30 // 1000
MIN_VOICED_SECONDS = 0.3
PAD_SECONDS = 0.3
VOICE_OVER_FLOOR = 2.8
ABSOLUTE_FLOOR = 0.002
NOISE_FLOOR_CAP = 0.02


def parse_ids(arg):
    return [] if arg == "-" else [int(x) for x in arg.split(",") if x != ""]


def frame_rms(piece):
    frames = len(piece) // FRAME_SAMPLES
    out = [0.0] * frames
    for f in range(frames):
        base = f * FRAME_SAMPLES
        chunk = piece[base:base + FRAME_SAMPLES]
        out[f] = float((np.sum(chunk.astype(np.float64) * chunk) / FRAME_SAMPLES) ** 0.5)
    return out


def voice_threshold(rms):
    if not rms:
        return ABSOLUTE_FLOOR
    sorted_rms = sorted(rms)
    noise_floor = min(NOISE_FLOOR_CAP, sorted_rms[min(len(sorted_rms) - 1, len(sorted_rms) // 5)])
    return max(noise_floor * VOICE_OVER_FLOOR, ABSOLUTE_FLOOR)


def has_voice(piece):
    """Mirrors WhisperSegmenter.hasVoice: at least MIN_VOICED_SECONDS of frames over the threshold."""
    rms = frame_rms(piece)
    threshold = voice_threshold(rms)
    voiced = sum(1 for v in rms if v > threshold)
    return voiced * FRAME_SAMPLES >= MIN_VOICED_SECONDS * SR


def pad_piece(piece):
    """Mirrors WhisperSegmenter.padded: silence added so the first/last voiced frame sits at least
    PAD_SECONDS from the edges; unchanged when the clip already has that much quiet."""
    rms = frame_rms(piece)
    threshold = voice_threshold(rms)
    first_voiced = last_voiced = -1
    for i, v in enumerate(rms):
        if v > threshold:
            if first_voiced < 0:
                first_voiced = i
            last_voiced = i
    if first_voiced < 0:
        return piece
    pad_samples = int(PAD_SECONDS * SR)
    lead_in = first_voiced * FRAME_SAMPLES
    tail = len(piece) - (last_voiced + 1) * FRAME_SAMPLES
    if last_voiced == len(rms) - 1:
        tail = 0  # speech runs to the edge, not quiet
    prepend = max(0, pad_samples - lead_in)
    append = max(0, pad_samples - tail)
    if prepend == 0 and append == 0:
        return piece
    return np.concatenate([np.zeros(prepend, np.float32), piece, np.zeros(append, np.float32)])


class ReplayServer:
    def __init__(self, model_path, tokenizer_path, prompt, eot, timestamp_begin, always):
        self.model = Whisper(model_path, tokenizer_path)
        self.tok = self.model.tok
        self.prompt = prompt
        self.eot = eot
        self.timestamp_begin = timestamp_begin
        self.always = set(always)

    def transcribe_one(self, wav_path):
        audio = load_wav(wav_path)
        if not has_voice(audio):
            return {"text": "", "encode_ms": 0, "decode_ms": 0, "steps": 0}
        audio = pad_piece(audio)

        t0 = time.perf_counter()
        feats = log_mel(audio, self.model.frames)[None]
        enc = self.model.enc(args_0=feats)["output_0"]
        t1 = time.perf_counter()

        toks = list(self.prompt)
        n0 = len(toks)
        mask = self.model.mask()
        seq = self.model.seq
        steps = 0
        while len(toks) < seq:
            arr = np.full((1, seq), self.eot, np.int32)
            arr[0, :len(toks)] = toks
            logits = self.model.dec(args_0=enc, args_1=arr, args_2=mask)["output_0"][0, len(toks) - 1].copy()
            steps += 1
            if self.timestamp_begin > 0:
                logits[self.timestamp_begin:] = -np.inf
            for special_id in self.always:
                logits[special_id] = -np.inf
            if len(toks) == n0:
                logits[self.eot] = -np.inf
            nxt = int(np.argmax(logits))
            if nxt == self.eot:
                break
            toks.append(nxt)
            # The same repetition guard as WhisperDecoder.repeats (4-gram repeated 3 times).
            if len(toks) - n0 >= 12 and toks[-4:] == toks[-8:-4] == toks[-12:-8]:
                break
        t2 = time.perf_counter()
        text = self.tok.decode(toks[n0:]).strip()
        return {
            "text": text,
            "encode_ms": int((t1 - t0) * 1000),
            "decode_ms": int((t2 - t1) * 1000),
            "steps": steps,
        }


def main():
    model_path, tokenizer_path = sys.argv[1], sys.argv[2]
    prompt = parse_ids(sys.argv[3])
    eot = int(sys.argv[4])
    timestamp_begin = int(sys.argv[5])
    always = parse_ids(sys.argv[6])

    server = ReplayServer(model_path, tokenizer_path, prompt, eot, timestamp_begin, always)
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
            result = server.transcribe_one(request["wav"])
        except Exception as e:  # noqa: BLE001 - one bad clip must not kill the server for the rest
            result = {"error": str(e)}
        sys.stdout.write(json.dumps(result) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
