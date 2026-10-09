"""Download and cache the model files the TTS evaluation needs.

Everything lands under ~/.cache/termux-launcher/tts/<repo-name>/ and is fetched once. The
Hugging Face API (`/api/models/<repo>?blobs=true`) gives each file's size, which is used to
skip files that are already complete and to catch truncated downloads.

    python fetch.py            # download everything, print the sizes
    python fetch.py --list     # print what would be downloaded, with sizes, and exit
"""
import argparse
import json
import os
import sys
import urllib.request

CACHE = os.path.join(os.path.expanduser("~"), ".cache/termux-launcher/tts")
HF = "https://huggingface.co"

# repo -> files. fp32 for Kitten and Inflect (both cards say deploy fp32; Inflect's fp16 flow
# layers break on some sentences), fp16 for Matcha (the only precision published).
MANIFEST = {
    "litert-community/kitten-tts-nano-0.8": [
        "kitten_predictor.tflite",
        "kitten_prosody.tflite",
        "kitten_vocoder.tflite",
        "voices.npz",
    ],
    "litert-community/Inflect-Nano-v2": [
        "inflect_text_encoder.tflite",
        "inflect_decoder.tflite",
    ],
    "litert-community/Matcha-TTS": [
        "matcha_textenc_fp16.tflite",
        "matcha_decoder_fp16.tflite",
        "matcha_vocoder_fp16.tflite",
        "emb.bin",
        "config.json",
        # The shared GPL-free phonemizer (Clear BSD dictionary + MIT DeepPhonemizer).
        "g2p_dict.txt.gz",
        "g2p_meta.json",
        "dp_g2p_matcha_fp16.tflite",
    ],
}


def repo_dir(repo):
    return os.path.join(CACHE, repo.split("/", 1)[1])


def path(repo, name):
    """Local path of one manifest file (does not download)."""
    return os.path.join(repo_dir(repo), name)


def _sizes(repo):
    with urllib.request.urlopen(f"{HF}/api/models/{repo}?blobs=true", timeout=60) as r:
        info = json.load(r)
    return {s["rfilename"]: s.get("size") for s in info.get("siblings", [])}


def _download(url, dest, size):
    tmp = dest + ".part"
    with urllib.request.urlopen(url, timeout=120) as r, open(tmp, "wb") as f:
        done = 0
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
            done += len(chunk)
            if size:
                print(f"\r    {done / 1e6:7.1f} / {size / 1e6:.1f} MB", end="", flush=True)
    print()
    if size and os.path.getsize(tmp) != size:
        raise IOError(f"{dest}: got {os.path.getsize(tmp)} bytes, expected {size}")
    os.replace(tmp, dest)


def ensure_all(verbose=True, list_only=False):
    """Download whatever is missing. Returns [(repo, name, size_bytes)]."""
    rows = []
    for repo, names in MANIFEST.items():
        sizes = None
        for name in names:
            dest = path(repo, name)
            have = os.path.getsize(dest) if os.path.exists(dest) else None
            if sizes is None and (have is None or list_only):
                try:
                    sizes = _sizes(repo)
                except OSError as e:
                    if have is None:
                        raise
                    print(f"  (could not reach the HF API for {repo}: {e})", file=sys.stderr)
                    sizes = {}
            size = (sizes or {}).get(name) or have
            rows.append((repo, name, size))
            if list_only:
                continue
            if have is not None and (not size or have == size):
                continue
            if sizes is None:
                sizes = _sizes(repo)
                size = sizes.get(name)
            if name not in sizes:
                raise FileNotFoundError(f"{repo} has no file {name}")
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            if verbose:
                print(f"  downloading {repo}/{name} ({(size or 0) / 1e6:.1f} MB)")
            _download(f"{HF}/{repo}/resolve/main/{name}", dest, size)
    return rows


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--list", action="store_true", help="print the manifest with sizes and exit")
    args = ap.parse_args()
    rows = ensure_all(list_only=args.list)
    total = 0
    for repo, name, size in rows:
        total += size or 0
        print(f"  {(size or 0) / 1e6:7.1f} MB  {repo}/{name}")
    print(f"  {total / 1e6:7.1f} MB  total, cached in {CACHE}")


if __name__ == "__main__":
    main()
