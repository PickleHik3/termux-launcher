"""Silero VAD v5.1.2 (ONNX) -> LiteRT flatbuffer for the in-app keyboard's voice VAD.

The app bundles the result as app/src/main/assets/vad/silero_vad_v5.tflite and runs it with the
classic org.tensorflow.lite.Interpreter from com.google.ai.edge.litert:litert:1.4.2 (see
SileroVad.java in app/src/main/java/com/termux/app/terminal/inappkeyboard/voice/).

Method: a hand rebuild. The 16 kHz branch of Silero's graph is small and fixed (reflect pad, an
STFT done as a strided Conv1d, four Conv1d+ReLU blocks, one LSTM cell, a 1x1 Conv1d and a
sigmoid), so it is rewritten here as a plain TensorFlow function with the weights copied out of
the ONNX file, and converted with TFLiteConverter restricted to builtin ops. The generic routes
were not needed and each would have brought its own trouble: onnx2tf has to unpick the sr == 16000
/ 8000 If nodes and the dynamic-shape LSTM wrapper Silero exports, and ai-edge-torch needs the JIT
model plus torch in the environment. A rebuild also gives exactly the interface the app wants:

    inputs   input  float32 [1, 576]     64 samples of context (the previous chunk's tail, zeros
                                         at the start) followed by one 512-sample chunk at 16 kHz,
                                         samples in [-1, 1] as the official OnnxWrapper feeds it
             state  float32 [2, 1, 128]  LSTM h and c, zeros at the start of a stream
    outputs  prob   float32 [1, 1]       speech probability of the chunk
             state  float32 [2, 1, 128]  the state to feed with the next chunk

The LSTM state is an ordinary input/output pair, never a variable inside the model, so the Java
side owns it (and resets it by zeroing a buffer). The sr input is gone: the graph is 16 kHz only.

Pinned environment (Python 3.12; the repo's usual venv is 3.14, which has no TensorFlow wheels):

    uv venv --python 3.12 ~/.cache/termux-launcher/silero-conv
    uv pip install --python ~/.cache/termux-launcher/silero-conv/bin/python \\
        tensorflow-cpu==2.19.0 onnx==1.17.0 onnxruntime==1.20.1 ai-edge-litert==1.4.0 "numpy<2.2"

Run from the repo root:

    ~/.cache/termux-launcher/silero-conv/bin/python scripts/voice-eval/convert_silero.py

It writes the tflite, prints the op list (and fails on anything that is not a builtin), then
streams a few clips from ~/.cache/termux-launcher/voice-eval/ chunk by chunk through both the ONNX
model (onnxruntime) and the tflite (ai-edge-litert), carrying state and context exactly as the
app does, and fails unless the largest probability difference is under 1e-3.
"""
import argparse
import glob
import hashlib
import os
import sys
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
CACHE = os.path.join(os.path.expanduser("~"), ".cache/termux-launcher")
DEFAULT_ONNX = os.path.join(CACHE, "silero/silero_vad.onnx")
DEFAULT_OUT = os.path.join(REPO, "app/src/main/assets/vad/silero_vad_v5.tflite")
DEFAULT_CLIPS = os.path.join(CACHE, "voice-eval")
# v5.1.2's src/silero_vad/data/silero_vad.onnx; anything else and the weight names may differ.
ONNX_SHA256 = "2623a2953f6ff3d2c1e61740c6cdb7168133479b267dfef114a4a3cc5bdd788f"

SR = 16000
CHUNK = 512
CONTEXT = 64
WINDOW = CONTEXT + CHUNK
HIDDEN = 128
TOLERANCE = 1e-3


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def load_weights(onnx_path):
    """The 16 kHz branch's constants (the top-level If's then_branch), keyed by their short name."""
    import onnx
    from onnx import numpy_helper

    model = onnx.load(onnx_path)
    top_if = next(n for n in model.graph.node if n.op_type == "If")
    branch = next(a.g for a in top_if.attribute if a.name == "then_branch")
    wanted = {
        "stft.forward_basis_buffer": (258, 1, 256),
        "encoder.0.reparam_conv.weight": (128, 129, 3), "encoder.0.reparam_conv.bias": (128,),
        "encoder.1.reparam_conv.weight": (64, 128, 3), "encoder.1.reparam_conv.bias": (64,),
        "encoder.2.reparam_conv.weight": (64, 64, 3), "encoder.2.reparam_conv.bias": (64,),
        "encoder.3.reparam_conv.weight": (128, 64, 3), "encoder.3.reparam_conv.bias": (128,),
        "decoder.rnn.weight_ih": (512, 128), "decoder.rnn.weight_hh": (512, 128),
        "decoder.rnn.bias_ih": (512,), "decoder.rnn.bias_hh": (512,),
        "decoder.decoder.2.weight": (1, 128, 1), "decoder.decoder.2.bias": (1,),
    }
    found = {}
    for node in branch.node:
        if node.op_type != "Constant":
            continue
        for key in wanted:
            if node.output[0].endswith("__" + key):
                value = numpy_helper.to_array(node.attribute[0].t).astype(np.float32)
                if value.shape != wanted[key]:
                    raise SystemExit(f"{key}: expected {wanted[key]}, got {value.shape}")
                found[key] = value
    missing = sorted(set(wanted) - set(found))
    if missing:
        raise SystemExit("weights not found in the 16 kHz branch: " + ", ".join(missing))
    return found


def build_function(w):
    """The 16 kHz forward pass as a tf.function over NWC tensors; mirrors the ONNX graph node for node."""
    import tensorflow as tf

    def conv_filter(oik):
        # ONNX/PyTorch Conv1d weights are [out, in, kernel]; tf.nn.conv1d wants [kernel, in, out].
        return tf.constant(np.transpose(oik, (2, 1, 0)))

    basis = conv_filter(w["stft.forward_basis_buffer"])
    enc = [(conv_filter(w[f"encoder.{i}.reparam_conv.weight"]), tf.constant(w[f"encoder.{i}.reparam_conv.bias"]))
           for i in range(4)]
    strides = [1, 2, 2, 1]
    # PyTorch LSTMCell gate order is input, forget, cell (g), output.
    w_ih = tf.constant(w["decoder.rnn.weight_ih"].T)
    w_hh = tf.constant(w["decoder.rnn.weight_hh"].T)
    b = tf.constant(w["decoder.rnn.bias_ih"] + w["decoder.rnn.bias_hh"])
    head_w = tf.constant(w["decoder.decoder.2.weight"].reshape(HIDDEN, 1))
    head_b = tf.constant(w["decoder.decoder.2.bias"])

    @tf.function(input_signature=[
        tf.TensorSpec([1, WINDOW], tf.float32, name="input"),
        tf.TensorSpec([2, 1, HIDDEN], tf.float32, name="state"),
    ])
    def silero(input, state):  # noqa: A002 - the tensor name the app looks for
        # STFT: reflect-pad 64 samples on the right, then a 256-tap conv at hop 128 whose 258
        # filters are 129 cosine and 129 sine bases; the magnitude feeds the encoder.
        x = tf.pad(input, [[0, 0], [0, CONTEXT]], mode="REFLECT")           # [1, 640]
        x = tf.reshape(x, [1, WINDOW + CONTEXT, 1])
        spec = tf.nn.conv1d(x, basis, stride=128, padding="VALID")          # [1, 4, 258]
        real = spec[:, :, :129]
        imag = spec[:, :, 129:]
        x = tf.sqrt(real * real + imag * imag)                              # [1, 4, 129]
        for (kernel, bias), stride in zip(enc, strides):
            # Symmetric padding of 1 as in PyTorch; TF "SAME" pads asymmetrically at stride 2.
            x = tf.pad(x, [[0, 0], [1, 1], [0, 0]])
            x = tf.nn.relu(tf.nn.conv1d(x, kernel, stride=stride, padding="VALID") + bias)
        x = tf.reshape(x, [1, HIDDEN])                                      # time is 1 here
        h = tf.reshape(state[0], [1, HIDDEN])
        c = tf.reshape(state[1], [1, HIDDEN])
        gates = tf.matmul(x, w_ih) + tf.matmul(h, w_hh) + b
        i, f, g, o = tf.split(gates, 4, axis=1)
        c2 = tf.sigmoid(f) * c + tf.sigmoid(i) * tf.tanh(g)
        h2 = tf.sigmoid(o) * tf.tanh(c2)
        prob = tf.sigmoid(tf.matmul(tf.nn.relu(h2), head_w) + head_b)      # [1, 1]
        new_state = tf.reshape(tf.concat([h2, c2], axis=0), [2, 1, HIDDEN])
        return {"prob": prob, "state": new_state}

    return silero


def convert(onnx_path, out_path):
    import tensorflow as tf

    fn = build_function(load_weights(onnx_path))
    converter = tf.lite.TFLiteConverter.from_concrete_functions([fn.get_concrete_function()], fn)
    # Builtins only: a Flex op would need the select-ops AAR, which the app does not ship.
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    converter.optimizations = []  # float32 throughout; the model is small and parity matters more
    flat = converter.convert()
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "wb") as f:
        f.write(flat)
    return flat


def op_names(tflite_path):
    """Builtin op codes used by the model; exits on a custom/Flex op."""
    from tensorflow.lite.python import schema_py_generated as schema

    with open(tflite_path, "rb") as f:
        model = schema.Model.GetRootAsModel(f.read(), 0)
    names = {v: k for k, v in schema.BuiltinOperator.__dict__.items() if not k.startswith("_")}
    ops = []
    for i in range(model.OperatorCodesLength()):
        code = model.OperatorCodes(i)
        builtin = max(code.BuiltinCode(), code.DeprecatedBuiltinCode())
        if names.get(builtin) == "CUSTOM":
            raise SystemExit("custom op in model: " + code.CustomCode().decode())
        ops.append(f"{names.get(builtin, builtin)} v{code.Version()}")
    return sorted(ops)


def load_wav(path):
    with wave.open(path) as w:
        if w.getframerate() != SR or w.getnchannels() != 1 or w.getsampwidth() != 2:
            raise SystemExit(f"{path}: need 16 kHz mono PCM16")
        return np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768.0


class OnnxSilero:
    def __init__(self, path):
        import onnxruntime as ort
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 1
        self.session = ort.InferenceSession(path, sess_options=opts, providers=["CPUExecutionProvider"])

    def reset(self):
        self.state = np.zeros((2, 1, HIDDEN), np.float32)

    def __call__(self, window):
        prob, self.state = self.session.run(
            None, {"input": window[None], "state": self.state, "sr": np.array(SR, np.int64)})
        return float(np.asarray(prob).reshape(-1)[0])


class LiteRtSilero:
    def __init__(self, path):
        from ai_edge_litert.interpreter import Interpreter
        self.interp = Interpreter(model_path=path, num_threads=1)
        self.interp.allocate_tensors()
        ins = {tuple(d["shape"]): d["index"] for d in self.interp.get_input_details()}
        outs = {tuple(d["shape"]): d["index"] for d in self.interp.get_output_details()}
        self.in_x, self.in_state = ins[(1, WINDOW)], ins[(2, 1, HIDDEN)]
        self.out_p, self.out_state = outs[(1, 1)], outs[(2, 1, HIDDEN)]

    def reset(self):
        self.state = np.zeros((2, 1, HIDDEN), np.float32)

    def __call__(self, window):
        self.interp.set_tensor(self.in_x, window[None])
        self.interp.set_tensor(self.in_state, self.state)
        self.interp.invoke()
        self.state = self.interp.get_tensor(self.out_state).copy()
        return float(self.interp.get_tensor(self.out_p).reshape(-1)[0])


def stream(model, audio):
    """Chunk by chunk with 64 samples of context, as SileroVad.java and the OnnxWrapper do."""
    model.reset()
    context = np.zeros(CONTEXT, np.float32)
    probs = []
    for k in range(len(audio) // CHUNK):
        chunk = audio[k * CHUNK:(k + 1) * CHUNK]
        probs.append(model(np.concatenate([context, chunk]).astype(np.float32)))
        context = chunk[-CONTEXT:]
    return np.array(probs, np.float32)


def default_clips(clip_dir):
    picks = []
    for condition in ("near", "far", "fan", "tv"):
        # 16 clips per voice, sorted by voice: every 17th gives four voices at four different lines.
        picks += sorted(glob.glob(os.path.join(clip_dir, f"{condition}-*.wav")))[::17][:4]
    return picks


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--onnx", default=DEFAULT_ONNX)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--clips", nargs="*", help="WAVs to compare on (default: 4 each of near/far/fan/tv)")
    ap.add_argument("--skip-convert", action="store_true", help="only run the parity check")
    args = ap.parse_args()

    digest = sha256(args.onnx)
    if digest != ONNX_SHA256:
        print(f"warning: {args.onnx} is not v5.1.2 (sha256 {digest})", file=sys.stderr)
    if not args.skip_convert:
        convert(args.onnx, args.out)
    print(f"{args.out}: {os.path.getsize(args.out)} bytes, sha256 {sha256(args.out)}")
    print("ops:", ", ".join(op_names(args.out)))

    clips = args.clips or default_clips(DEFAULT_CLIPS)
    if not clips:
        raise SystemExit("no clips to compare on; see scripts/voice-eval/README.md")
    ref, lite = OnnxSilero(args.onnx), LiteRtSilero(args.out)
    rng = np.random.default_rng(7)
    worst, chunks, flips = 0.0, 0, 0
    inputs = [(os.path.basename(p), load_wav(p)) for p in clips]
    # Plus pure noise, where the probability hovers near the thresholds rather than sitting at 0 or 1.
    inputs.append(("white-noise", (rng.standard_normal(SR * 5) * 0.05).astype(np.float32)))
    for name, audio in inputs:
        a, b = stream(ref, audio), stream(lite, audio)
        diff = float(np.max(np.abs(a - b))) if len(a) else 0.0
        # A decision flip at the app's onset/hold thresholds is what would actually matter.
        flips += int(np.sum((a > 0.5) != (b > 0.5)) + np.sum((a > 0.35) != (b > 0.35)))
        worst = max(worst, diff)
        chunks += len(a)
        print(f"  {name:32s} chunks={len(a):4d} max|dp|={diff:.2e} max p={a.max() if len(a) else 0:.3f}")
    print(f"max |p_onnx - p_tflite| over {chunks} chunks of {len(inputs)} inputs: {worst:.3e}; "
          f"threshold flips: {flips}")
    if worst >= TOLERANCE:
        raise SystemExit(f"parity failed: {worst:.3e} >= {TOLERANCE}")


if __name__ == "__main__":
    main()
