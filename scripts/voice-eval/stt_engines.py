"""The three speech models, each as .transcribe(float32 audio) -> text.

parakeet   parakeet-tdt-0.6b-v3, LiteRT int8 stateful 5 s graph: the exact runtime the replay rig
           uses (scripts/parakeet_replay_server.Parakeet, imported, not copied).
whisper    Whisper small.en ACFT, LiteRT 10 s graph: scripts/whisper_replay_server.ReplayServer,
           imported, including the runtime WhisperSegmenter checks it mirrors (hasVoice, padding)
           and the repetition guard. The prompt is the plain dictation prompt
           (<|startoftranscript|><|notimestamps|>) built as whisper_reference_decoder builds it;
           the app's terminal-bias prompt is only available through VoiceReplayRig (it reads the
           ids out of the Java tokenizer by reflection).
moonshine  UsefulSensors moonshine-tiny, from the ONNX export (onnx-community/moonshine-tiny-ONNX)
           through onnxruntime + tokenizers, greedy decoding. The app would run
           litert-community's moonshine_tiny_5s_f32.tflite (fixed 5 s windows); the ONNX graph
           takes any length, so segments are sent whole. Same weights, float32 both.
           --moonshine-backend transformers uses transformers + torch instead, if installed.
"""
import json
import os

import numpy as np

CACHE = os.path.expanduser("~/.cache/termux-launcher")


class ParakeetEngine:
    name = "parakeet"

    def __init__(self, model=None, tokenizer=None, threads=4):
        from parakeet_replay_server import Parakeet
        d = os.path.join(CACHE, "parakeet")
        self.p = Parakeet(model or os.path.join(d, "parakeet_tdt_0.6b_v3_5s_i8_stateful.tflite"),
                          tokenizer or os.path.join(d, "tokenizer.json"), threads)

    def transcribe(self, audio):
        return self.p.transcribe(audio)[0]


class WhisperEngine:
    def __init__(self, size="small", threads=4):
        import whisper_replay_server as wrs
        from whisper_reference_decoder import Whisper
        self.name = "whisper-" + size + ".en"
        d = os.path.join(CACHE, f"whisper/whisper-acft-{size}-en")
        model = os.path.join(d, f"acft_whisper_{size}.en_10s_drq.tflite")
        tok = os.path.join(d, "tokenizer.json")
        ref = Whisper(model, tok, threads=threads)
        s = ref.tok.special
        always = [s[n] for n in ("<|startoftranscript|>", "<|translate|>", "<|transcribe|>", "<|startoflm|>",
                                 "<|startofprev|>", "<|nocaptions|>", "<|nospeech|>") if n in s]
        self.server = wrs.ReplayServer.__new__(wrs.ReplayServer)
        self.server.model = ref
        self.server.tok = ref.tok
        self.server.prompt = ref.prompt("en")
        self.server.eot = s["<|endoftext|>"]
        self.server.timestamp_begin = s["<|notimestamps|>"] + 1
        self.server.always = set(always)
        # ReplayServer.transcribe_one takes a WAV path and calls the module's load_wav on it; let
        # it take the array directly so no temp WAVs are written. Everything after that is its code.
        original = wrs.load_wav
        wrs.load_wav = lambda p: p if isinstance(p, np.ndarray) else original(p)

    def transcribe(self, audio):
        return self.server.transcribe_one(audio.astype(np.float32))["text"]


class MoonshineOnnxEngine:
    """Greedy moonshine decoding over the optimum-style ONNX export:
    encoder_model.onnx: input_values [1, samples] -> last_hidden_state [1, T, 288]
    decoder_model_merged.onnx: input_ids, encoder_hidden_states, past_key_values.{i}.{decoder,encoder}.{key,value},
                               use_cache_branch -> logits, present.{i}.* (the encoder cache is taken
                               from the first step and kept, as transformers does)."""

    name = "moonshine-tiny"

    def __init__(self, model_dir=None, threads=4):
        import onnxruntime as ort
        d = model_dir or os.path.join(CACHE, "moonshine/tiny")
        onnx_dir = os.path.join(d, "onnx") if os.path.isdir(os.path.join(d, "onnx")) else d
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = threads
        prov = ["CPUExecutionProvider"]
        self.enc = ort.InferenceSession(os.path.join(onnx_dir, "encoder_model.onnx"), opts, providers=prov)
        self.dec = ort.InferenceSession(os.path.join(onnx_dir, "decoder_model_merged.onnx"), opts, providers=prov)
        cfg = json.load(open(os.path.join(d, "config.json")))
        gen_path = os.path.join(d, "generation_config.json")
        gen = json.load(open(gen_path)) if os.path.isfile(gen_path) else {}
        self.bos = gen.get("decoder_start_token_id", cfg.get("decoder_start_token_id", 1))
        eos = gen.get("eos_token_id", cfg.get("eos_token_id", 2))
        self.eos = set(eos if isinstance(eos, list) else [eos])
        self.enc_inputs = [i.name for i in self.enc.get_inputs()]
        self.dec_inputs = {i.name: i for i in self.dec.get_inputs()}
        self.dec_outputs = [o.name for o in self.dec.get_outputs()]
        heads = cfg.get("decoder_num_key_value_heads") or cfg.get("decoder_num_attention_heads")
        self.heads = heads
        self.head_dim = cfg["hidden_size"] // cfg["decoder_num_attention_heads"]
        self.past_names = [n for n in self.dec_inputs if n.startswith("past_key_values.")]
        try:
            from tokenizers import Tokenizer
            self.tok = Tokenizer.from_file(os.path.join(d, "tokenizer.json"))
            self.decode = lambda ids: self.tok.decode(ids, skip_special_tokens=True)
        except ImportError:
            self.decode = self._fallback_decoder(os.path.join(d, "tokenizer.json"))

    @staticmethod
    def _fallback_decoder(path):
        """SentencePiece-style decode without the tokenizers package: ▁ -> space, <0xNN> bytes."""
        t = json.load(open(path, encoding="utf-8"))
        pieces = {i: p for p, i in t["model"]["vocab"].items()}
        special = {a["id"] for a in t.get("added_tokens", []) if a.get("special")}

        def decode(ids):
            out = bytearray()
            for i in ids:
                if i in special:
                    continue
                p = pieces.get(i, "")
                if len(p) == 6 and p.startswith("<0x") and p.endswith(">"):
                    out.append(int(p[3:5], 16))
                else:
                    out += p.replace("▁", " ").encode("utf-8")
            return out.decode("utf-8", "replace").strip()
        return decode

    def _shape(self, name, seq):
        shape = self.dec_inputs[name].shape
        dims = [d if isinstance(d, int) else None for d in shape]
        return (1, dims[1] or self.heads, seq, dims[3] or self.head_dim)

    def transcribe(self, audio):
        x = audio.astype(np.float32)[None]
        feeds = {"input_values": x}
        if "attention_mask" in self.enc_inputs:
            feeds["attention_mask"] = np.ones_like(x, dtype=np.int64)
        hidden = self.enc.run(None, {k: feeds[k] for k in self.enc_inputs})[0]
        past = {n: np.zeros(self._shape(n, 0), np.float32) for n in self.past_names}
        ids = [self.bos]
        # moonshine emits ~6.5 tokens per second of speech; a hard cap stops runaway repetition.
        max_new = int(np.ceil(len(audio) / 16000 * 6.5)) + 10
        out_ids = []
        for step in range(max_new):
            feed = {"input_ids": np.array([[ids[-1]]], np.int64), "encoder_hidden_states": hidden}
            if "use_cache_branch" in self.dec_inputs:
                feed["use_cache_branch"] = np.array([step > 0])
            if "encoder_attention_mask" in self.dec_inputs:
                feed["encoder_attention_mask"] = np.ones(hidden.shape[:2], np.int64)
            feed.update(past)
            outs = dict(zip(self.dec_outputs, self.dec.run(None, {k: feed[k] for k in self.dec_inputs})))
            nxt = int(np.argmax(outs["logits"][0, -1]))
            for n in self.past_names:
                present = outs.get(n.replace("past_key_values", "present"))
                if present is None:
                    continue
                if ".encoder." in n and step > 0:
                    continue  # keep the encoder cross-attention cache from step 0
                past[n] = present
            if nxt in self.eos:
                break
            ids.append(nxt)
            out_ids.append(nxt)
            if len(out_ids) >= 12 and out_ids[-4:] == out_ids[-8:-4] == out_ids[-12:-8]:
                break  # the same 4-gram-x3 guard the app's Whisper decoder uses
        return self.decode(out_ids).strip()


class MoonshineTransformersEngine:
    name = "moonshine-tiny"

    def __init__(self, model_id="UsefulSensors/moonshine-tiny", threads=4):
        import torch
        from transformers import AutoProcessor, MoonshineForConditionalGeneration
        torch.set_num_threads(threads)
        self.torch = torch
        self.proc = AutoProcessor.from_pretrained(model_id)
        self.model = MoonshineForConditionalGeneration.from_pretrained(model_id).eval()

    def transcribe(self, audio):
        inputs = self.proc(audio.astype(np.float32), sampling_rate=16000, return_tensors="pt")
        max_new = int(np.ceil(len(audio) / 16000 * 6.5)) + 10
        with self.torch.inference_mode():
            ids = self.model.generate(**inputs, max_new_tokens=max_new)
        return self.proc.decode(ids[0], skip_special_tokens=True).strip()


def make_engine(name, args):
    threads = args.threads
    if name == "parakeet":
        return ParakeetEngine(threads=threads)
    if name in ("whisper", "whisper-small", "whisper-small.en"):
        return WhisperEngine("small", threads)
    if name in ("whisper-base", "whisper-base.en"):
        return WhisperEngine("base", threads)
    if name in ("moonshine", "moonshine-tiny"):
        if args.moonshine_backend == "transformers":
            return MoonshineTransformersEngine(threads=threads)
        return MoonshineOnnxEngine(args.moonshine_dir, threads)
    raise ValueError("unknown STT " + name)
