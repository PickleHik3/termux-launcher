"""The two VADs under test, behind one streaming segmenter.

EnergyVad   a line-by-line port of VoiceActivityDetector.java (app/src/main/java/com/termux/app/
            terminal/inappkeyboard/voice/VoiceActivityDetector.java, as of dev cecc2d31). Line
            numbers "J123" below point into that file.
SileroVad   Silero VAD v5 (ONNX, onnxruntime) deciding "voiced" per frame; everything else
            (pre-roll, pause close, minimum voiced time, window cut) is the *same* segmenter, so
            the comparison isolates the voiced/unvoiced decision and nothing else.

Both return segments as absolute 30 ms frame ranges [start, end) of the clip, padding included,
exactly the PCM VoiceActivityDetector.Listener.onSegment would receive, plus per-frame flags.
"""
import os

import numpy as np

from common import FRAME_MS, FRAME_SAMPLES, SR, to_pcm16

# --- Constants, J35-J69 ------------------------------------------------------------------------
PAD_MS = 300                      # J38
MIN_VOICED_MS = 300               # J39
VOICE_OVER_FLOOR = 2.8            # J41  (9 dB) opens a segment
HOLD_OVER_FLOOR = 2.0             # J43  (6 dB) keeps it voiced
ABSOLUTE_FLOOR = 0.0003           # J49  (about -70 dBFS)
NOISE_FLOOR_CAP = 0.02            # J51  (about -34 dBFS)
FLOOR_WINDOW_MS = 5_000           # J53
FLOOR_PERCENTILE = 10             # J59
SMOOTH_FRAMES = 3                 # J65  (90 ms)
FLOOR_MIN_FRAMES = 8              # J67
CUT_SEARCH_FRAMES = 1000 // FRAME_MS  # J69

# VoiceReplayRig / VoiceInputSession defaults: 600 ms pause, 10 s window (TaiSettings default).
DEFAULT_PAUSE_MS = 600
DEFAULT_WINDOW_S = 10

F32 = np.float32


def frame_rms(frame_pcm16):
    """J297-J304: RMS of one frame of PCM16 in [0, 1], accumulated in double, returned as float."""
    v = frame_pcm16.astype(np.float64) / 32768.0
    return F32(np.sqrt(np.sum(v * v) / len(v)))


class EnergyDecider:
    """The voiced decision of VoiceActivityDetector.processFrame (J169-J174) with its state:
    smooth() J258-J265, updateNoiseFloor() J267-J274. Arithmetic is float32 like the Java."""

    name = "energy"

    def __init__(self):
        self.recent = np.zeros(SMOOTH_FRAMES, F32)       # J116
        self.recent_count = 0                            # J117
        self.recent_next = 0                             # J118
        n = FLOOR_WINDOW_MS // FRAME_MS
        self.floor_window = np.zeros(n, F32)             # J111
        self.floor_count = 0                             # J113
        self.floor_next = 0                              # J114
        self.noise_floor = F32(NOISE_FLOOR_CAP)          # J109

    def smooth(self, rms):  # J258-J265
        self.recent[self.recent_next] = F32(rms * rms)
        self.recent_next = (self.recent_next + 1) % SMOOTH_FRAMES
        if self.recent_count < SMOOTH_FRAMES:
            self.recent_count += 1
        s = F32(0)
        for i in range(self.recent_count):              # float sum, in ring order, as Java does
            s = F32(s + self.recent[i])
        return F32(np.sqrt(np.float64(s / F32(self.recent_count))))

    def update_floor(self, rms):  # J267-J274
        self.floor_window[self.floor_next] = rms
        self.floor_next = (self.floor_next + 1) % len(self.floor_window)
        if self.floor_count < len(self.floor_window):
            self.floor_count += 1
        ordered = np.sort(self.floor_window[:self.floor_count])
        self.noise_floor = F32(min(F32(NOISE_FLOOR_CAP),
                                   ordered[(self.floor_count - 1) * FLOOR_PERCENTILE // 100]))

    def decide(self, frame_index, pcm_frame, in_speech):
        rms = frame_rms(pcm_frame)                                   # J169
        smoothed = self.smooth(rms)                                  # J170
        self.update_floor(smoothed)                                  # J171
        over = F32(HOLD_OVER_FLOOR if in_speech else VOICE_OVER_FLOOR)  # J172
        threshold = max(F32(self.noise_floor * over), F32(ABSOLUTE_FLOOR))
        voiced = self.floor_count >= FLOOR_MIN_FRAMES and smoothed > threshold  # J173-J174
        return bool(voiced), rms


class SileroDecider:
    """Silero VAD v5 speech probability per 30 ms frame, with onset/hold hysteresis mirroring the
    energy VAD's 9 dB / 6 dB pair (Silero's own get_speech_timestamps uses threshold and
    threshold - 0.15 the same way). Probabilities are computed for the whole clip up front in
    Silero's native 512-sample (32 ms) chunks, streaming state carried chunk to chunk exactly as
    the official OnnxWrapper does (64 samples of context prepended). Frame i reads the chunk that
    has *finished* by the end of frame i, so no frame sees the future, as on the phone."""

    name = "silero"

    def __init__(self, model, onset=0.5, hold=0.35):
        self.model = model
        self.onset = onset
        self.hold = hold
        self.probs = None

    def prepare(self, audio):
        self.probs = self.model.chunk_probs(audio)

    def decide(self, frame_index, pcm_frame, in_speech):
        end_sample = (frame_index + 1) * FRAME_SAMPLES
        chunk = end_sample // SileroModel.CHUNK - 1
        p = float(self.probs[chunk]) if 0 <= chunk < len(self.probs) else 0.0
        voiced = p > (self.hold if in_speech else self.onset)
        return voiced, frame_rms(pcm_frame)


class SileroModel:
    """onnxruntime wrapper for silero_vad.onnx v5 (snakers4 repo) or onnx-community/silero-vad.

    Inputs: input [1, 64 + 512] float32, state [2, 1, 128] float32, sr int64. Outputs: prob, state.
    """

    CHUNK = 512
    CONTEXT = 64

    def __init__(self, path, threads=1):
        import onnxruntime as ort
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = threads
        opts.inter_op_num_threads = 1
        self.session = ort.InferenceSession(path, sess_options=opts, providers=["CPUExecutionProvider"])
        names = [i.name for i in self.session.get_inputs()]
        if "state" not in names:
            raise RuntimeError(f"{path} is not a Silero v5 model (inputs {names}); get v5, see README")
        self.input_names = names
        self.output_names = [o.name for o in self.session.get_outputs()]

    def chunk_probs(self, audio):
        audio = audio.astype(np.float32)
        n = len(audio) // self.CHUNK
        state = np.zeros((2, 1, 128), np.float32)
        context = np.zeros((1, self.CONTEXT), np.float32)
        sr = np.array(SR, dtype=np.int64)
        probs = np.zeros(n, np.float32)
        for i in range(n):
            chunk = audio[i * self.CHUNK:(i + 1) * self.CHUNK][None]
            x = np.concatenate([context, chunk], axis=1)
            feeds = {"input": x, "state": state, "sr": sr}
            out = self.session.run(None, {k: feeds[k] for k in self.input_names})
            probs[i] = float(np.asarray(out[0]).reshape(-1)[0])
            state = out[1]
            context = chunk[:, -self.CONTEXT:]
        return probs


def default_silero_path():
    return os.path.join(os.path.expanduser("~"), ".cache/termux-launcher/silero/silero_vad.onnx")


class Segmenter:
    """VoiceActivityDetector's segment state machine (J139-J285) with the voiced decision
    delegated to a decider. Tracks absolute frame indices instead of copying PCM.

    Returns (segments, voiced_flags, frame_levels); a segment is a dict
    {start, end, voiced_frames, first_voiced, last_voiced} in absolute frames.
    """

    def __init__(self, decider, pause_ms=DEFAULT_PAUSE_MS, window_s=DEFAULT_WINDOW_S):
        self.decider = decider
        self.pause_frames = max(1, pause_ms // FRAME_MS)                   # J131
        self.pad_frames = PAD_MS // FRAME_MS                               # J90
        self.min_voiced_frames = MIN_VOICED_MS // FRAME_MS                 # J91
        window_frames = max(2, window_s) * 1000 // FRAME_MS                # J132
        self.max_segment_frames = max(CUT_SEARCH_FRAMES + 1, window_frames - 2 * self.pad_frames)  # J133

    def run(self, audio):
        pcm = to_pcm16(audio)
        if hasattr(self.decider, "prepare"):
            self.decider.prepare(pcm.astype(np.float32) / 32768.0)
        n = len(pcm) // FRAME_SAMPLES   # feed() only processes whole frames (J151); the tail is dropped
        segs = []
        # "frames" (J97) is represented by base = absolute index of its first element and its length.
        base, size = 0, 0
        voiced_flags, levels = [], []
        in_speech = False
        voiced_frames = 0
        last_voiced = -1      # relative to base, as lastVoicedIndex (J105)
        silent = 0

        def emit(start_rel, end_rel, count):
            abs_flags = voiced_flags[base + start_rel:base + end_rel]
            idx = [base + start_rel + i for i, f in enumerate(abs_flags) if f]
            segs.append({"start": base + start_rel, "end": base + end_rel, "voiced_frames": count,
                         "first_voiced": idx[0] if idx else None, "last_voiced": idx[-1] if idx else None})

        for f in range(n):
            frame = pcm[f * FRAME_SAMPLES:(f + 1) * FRAME_SAMPLES]
            voiced, rms = self.decider.decide(f, frame, in_speech)      # J169-J174
            voiced_flags.append(voiced)                                 # J176-J178
            levels.append(float(rms))
            size += 1
            if not in_speech:                                           # J179
                if voiced:
                    in_speech = True                                    # J181-J186
                    voiced_frames = 1
                    last_voiced = size - 1
                    silent = 0
                    continue
                if size > self.pad_frames:                              # trimToPreRoll J276-J279
                    base += size - self.pad_frames
                    size = self.pad_frames
                silent += 1                                             # J189
                continue
            if voiced:                                                  # J196-J202
                voiced_frames += 1
                last_voiced = size - 1
                silent = 0
            else:
                silent += 1
            if silent >= self.pause_frames:                             # J203-J204 closeSegment
                end = min(size, last_voiced + 1 + self.pad_frames)      # J212
                if voiced_frames >= self.min_voiced_frames:             # J213
                    emit(0, end, voiced_frames)
                base += end                                             # J214
                size -= end
                in_speech = False                                       # J215-J217
                voiced_frames = 0
                last_voiced = -1
                if size > self.pad_frames:                              # J218
                    base += size - self.pad_frames
                    size = self.pad_frames
            elif size >= self.max_segment_frames:                       # J205-J206
                # cutAtQuietestRecentFrame J225-J255: cut at the quietest raw-RMS frame of the last second.
                frm = max(1, size - CUT_SEARCH_FRAMES)
                cut, quietest = frm, float("inf")
                for i in range(frm, size):
                    if levels[base + i] < quietest:
                        quietest = levels[base + i]
                        cut = i
                before = sum(voiced_flags[base:base + cut])
                if before >= self.min_voiced_frames:
                    emit(0, cut, before)
                base += cut
                size -= cut
                rest = voiced_flags[base:base + size]
                voiced_frames = sum(rest)
                last_voiced = max((i for i, v in enumerate(rest) if v), default=-1)
                if last_voiced < 0:
                    in_speech = False
                    if size > self.pad_frames:
                        base += size - self.pad_frames
                        size = self.pad_frames
        if in_speech:                                                   # finish() J159-J161
            end = min(size, last_voiced + 1 + self.pad_frames)
            if voiced_frames >= self.min_voiced_frames:
                emit(0, end, voiced_frames)
        return segs, voiced_flags, levels


def make_vads(names, silero_path=None, silero_onset=0.5, silero_hold=0.35, threads=1):
    """name -> zero-arg factory of a fresh decider (deciders carry per-clip state)."""
    out = {}
    for name in names:
        if name == "energy":
            out[name] = EnergyDecider
        elif name == "silero":
            model = SileroModel(silero_path or default_silero_path(), threads)
            out[name] = lambda m=model: SileroDecider(m, silero_onset, silero_hold)
        else:
            raise ValueError("unknown VAD " + name)
    return out
