"""Synthesise listening samples from three LiteRT TTS models and report speed and G2P coverage.

    ~/.cache/termux-launcher/venv/bin/python scripts/tts-eval/tts_eval.py

Writes, under scripts/tts-eval/out/ (git-ignored):
    <model>-<voice>-<n>.wav   one per model x voice x sentence (Kitten: all 8 voices)
    report.md                 G2P coverage, synthesis time, audio length, RTF, time to first audio
    samples.html              one <audio> per WAV next to its sentence, for quick comparison

Models download on first run into ~/.cache/termux-launcher/tts/ (see fetch.py, ~177 MB).
The phonemizer is the GPL-free dictionary + DeepPhonemizer path in g2p.py; espeak is never used.
"""
import argparse
import datetime
import html
import os
import platform
import sys
import time
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import engines  # noqa: E402
import fetch  # noqa: E402
from g2p import G2P, check_symbol_tables  # noqa: E402

SENTENCES = [
    "The build finished in forty two seconds, with three warnings.",
    "Open the settings, then choose Model centre, and download Gemma four.",
    "Did you mean git status, or git stash?",
    "The model is ready, but it needs two gigabytes of space. "
    "Do you want to download it now, or wait until tonight?",
    "NASA and the BBC reported it on 27 September 2026.",
]

MATCHA = "litert-community/Matcha-TTS"


def write_wav(path, pcm, sr):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(np.clip(np.round(pcm * 32767), -32768, 32767).astype(np.int16).tobytes())


def synth_utterance(eng, g2p, text, voice, frontend):
    """Sentence-by-sentence synthesis, as a streaming player would do it.

    Returns (pcm, row) where row has the timings and the merged G2P coverage. The clock covers
    G2P + every graph + host glue; time to first audio is when the first chunk's PCM exists.
    """
    t0 = time.perf_counter()
    chunks = eng.chunks(text)
    pieces, g2ps, notes = [], [], []
    ttfa = None
    for i, chunk in enumerate(chunks):
        r = g2p.phonemize(chunk, eng.punct, frontend)
        pcm, note = eng.synth(chunk, r, voice, i)
        if ttfa is None:
            ttfa = time.perf_counter() - t0
        pieces.append(pcm)
        g2ps.append(r)
        notes.append(note)
    pcm = eng.join(pieces, chunks)
    total = time.perf_counter() - t0
    audio = pcm.size / eng.sample_rate
    row = {
        "chunks": chunks,
        "g2p": g2ps,
        "g2p_s": sum(r.seconds for r in g2ps),
        "synth_s": total,
        "audio_s": audio,
        "rtf": total / audio if audio else float("nan"),
        "ttfa_s": ttfa or 0.0,
        "first_chunk_audio_s": pieces[0].size / eng.sample_rate if pieces else 0.0,
        "splits": sum(n.get("splits", 0) for n in notes),
        "truncated": any(n.get("truncated") for n in notes),
        "frames": sum(n.get("frames", 0) for n in notes),
    }
    return pcm, row


def coverage(g2ps):
    d = sum(r.dict_words for r in g2ps)
    neural = [w for r in g2ps for w in r.neural_words]
    total = d + len(neural)
    acr = [a for r in g2ps for a in r.acronyms]
    share = f"{d}/{total} dict" if total else "-"
    if total:
        share += f" ({100 * d / total:.0f}%), {len(neural)} neural"
    if acr:
        share += f", spelled {' '.join(acr)}"
    return share


def oov_notes(g2ps):
    parts = []
    neural = sorted({w for r in g2ps for w, _ in r.neural_words})
    if neural:
        parts.append("neural: " + ", ".join(neural))
    dropped = "".join(sorted({c for r in g2ps for c in r.dropped_text}))
    if dropped:
        parts.append(f"ignored input chars `{dropped}`")
    unm = "".join(sorted({c for r in g2ps for c in r.unmapped}))
    if unm:
        parts.append(f"IPA not in symbol table `{unm}`")
    nd = "".join(sorted({c for r in g2ps for c in r.neural_chars_dropped}))
    if nd:
        parts.append(f"chars the neural G2P cannot see `{nd}`")
    return "; ".join(parts) or "none"


def build_engine(name, args):
    if name == "kitten":
        return engines.Kitten(fetch, args.threads)
    if name == "inflect":
        return engines.Inflect(fetch, args.threads, seed=args.seed)
    if name == "matcha":
        return engines.Matcha(fetch, args.threads, steps=args.steps, seed=args.seed)
    raise SystemExit(f"unknown model {name}")


def md_table(header, rows):
    out = ["| " + " | ".join(header) + " |", "|" + "|".join("---" for _ in header) + "|"]
    for r in rows:
        out.append("| " + " | ".join(str(c).replace("|", "\\|") for c in r) + " |")
    return "\n".join(out)


def write_report(path, results, loads, g2p_load, sym_check, args, g2p_detail):
    lines = [
        "# LiteRT TTS evaluation",
        "",
        f"Generated {datetime.datetime.now():%Y-%m-%d %H:%M} on {platform.node()} "
        f"({platform.processor() or platform.machine()}, {os.cpu_count()} logical CPUs), "
        f"Python {platform.python_version()}, {args.threads} LiteRT threads, "
        f"frontend `{args.frontend}`, seed {args.seed}, Matcha {args.steps} Euler steps.",
        "",
        "Timings are wall clock on this PC, after one warm-up utterance per model: G2P + every "
        "graph + host glue. **Synth** is the whole item synthesised sentence by sentence; "
        "**TTFA** (time to first audio) is when the first sentence's PCM is ready, i.e. when a "
        "streaming player could start. **RTF** = synth / audio (below 1 is faster than real time).",
        "",
        "Coverage counts words, number words included: `dict` = found in the 275k espeak-IPA "
        "dictionary, `neural` = DeepPhonemizer fallback; `spelled` = ALL-CAPS tokens read letter "
        "by letter.",
        "",
        "## Load times",
        "",
        md_table(["component", "load s"],
                 [["G2P (dictionary + DeepPhonemizer)", f"{g2p_load:.2f}"]]
                 + [[k, f"{v:.2f}"] for k, v in loads.items()]),
        "",
        "Symbol tables: " + ", ".join(f"{k} {'matches' if v else 'DIFFERS'}"
                                      for k, v in sym_check.items())
        + " Matcha `config.json` (178 symbols; only the pad glyph at index 0 differs).",
        "",
    ]
    for model, rows in results.items():
        lines += [f"## {model}", ""]
        rtfs = [r["rtf"] for r in rows]
        ttfas = [r["ttfa_s"] for r in rows]
        lines += [f"Mean RTF {np.mean(rtfs):.3f} (min {min(rtfs):.3f}, max {max(rtfs):.3f}); "
                  f"mean TTFA {np.mean(ttfas) * 1000:.0f} ms.", ""]
        table = []
        for r in rows:
            flags = []
            if r["splits"]:
                flags.append(f"split x{r['splits']}")
            if r["truncated"]:
                flags.append("TRUNCATED")
            table.append([
                r["n"], r["voice"], coverage(r["g2p"]), oov_notes(r["g2p"]),
                len(r["chunks"]), f"{r['g2p_s'] * 1000:.0f}", f"{r['synth_s']:.3f}",
                f"{r['audio_s']:.2f}", f"{r['rtf']:.3f}", f"{r['ttfa_s'] * 1000:.0f}",
                " ".join(flags) or "", f"[wav]({r['wav']})",
            ])
        lines += [md_table(["#", "voice", "G2P coverage", "OOV / fallbacks", "chunks",
                            "G2P ms", "synth s", "audio s", "RTF", "TTFA ms", "notes", "file"],
                           table), ""]
    lines += ["## Phonemizer output per sentence", "",
              "Kitten style (punctuation as its own token). Words from the neural fallback are "
              "listed with their IPA so they can be checked by ear.", ""]
    for n, text, g in g2p_detail:
        lines += [f"**{n}.** {text}", ""]
        if g.text != text:
            lines += [f"- normalised: {g.text}"]
        lines += [f"- IPA: `{g.ipa}`"]
        if g.numbers:
            lines += ["- numbers: " + "; ".join(f"{a} -> {b}" for a, b in g.numbers)]
        if g.neural_words:
            lines += ["- neural: " + "; ".join(f"{w} -> `{i}`" for w, i in g.neural_words)]
        lines += [""]
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def write_html(path, results, sentences):
    by_sentence = {n: [] for n in range(1, len(sentences) + 1)}
    for model, rows in results.items():
        for r in rows:
            by_sentence[r["n"]].append((model, r))
    parts = ["""<!doctype html><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TTS samples</title>
<style>
:root{--bg:#fafaf8;--fg:#1d1d1b;--muted:#6b6b66;--card:#fff;--line:#e3e3de}
@media (prefers-color-scheme:dark){:root{--bg:#161615;--fg:#ecece8;--muted:#9a9a94;--card:#20201f;--line:#33332f}}
body{background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,sans-serif;margin:0;padding:24px 16px}
main{max-width:980px;margin:auto}
h1{font-size:1.4rem;margin:0 0 4px}
p.sub{color:var(--muted);margin:0 0 24px}
section{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:16px;margin-bottom:18px}
h2{font-size:1rem;margin:0 0 12px}
.row{display:grid;grid-template-columns:170px 1fr 170px;gap:10px;align-items:center;padding:6px 0;border-top:1px solid var(--line)}
.row:first-of-type{border-top:0}
.label{font-weight:600}.label small{display:block;font-weight:400;color:var(--muted)}
.stats{color:var(--muted);font-size:.85rem;text-align:right}
audio{width:100%}
@media (max-width:640px){.row{grid-template-columns:1fr}.stats{text-align:left}}
</style><main>
<h1>LiteRT TTS samples</h1>
<p class="sub">KittenTTS nano 0.8 (8 voices), Inflect-Nano-v2, Matcha-TTS &middot; GPL-free G2P
&middot; RTF = synthesis time / audio length on the PC that generated this page</p>
"""]
    for n, text in enumerate(sentences, 1):
        if not by_sentence[n]:
            continue
        parts.append(f"<section><h2>{n}. {html.escape(text)}</h2>")
        for model, r in by_sentence[n]:
            parts.append(
                f'<div class="row"><div class="label">{html.escape(model)}'
                f'<small>{html.escape(r["voice"])}</small></div>'
                f'<audio controls preload="none" src="{html.escape(r["wav"])}"></audio>'
                f'<div class="stats">{r["audio_s"]:.1f} s audio &middot; RTF {r["rtf"]:.3f}'
                f'<br>TTFA {r["ttfa_s"] * 1000:.0f} ms</div></div>')
        parts.append("</section>")
    parts.append("</main>")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(parts))


def main():
    ap = argparse.ArgumentParser(description="LiteRT TTS listening samples + report")
    ap.add_argument("--models", default="kitten,inflect,matcha")
    ap.add_argument("--voices", default=",".join(engines.KITTEN_VOICES),
                    help="Kitten voices (comma-separated)")
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--seed", type=int, default=0, help="noise seed for Inflect and Matcha")
    ap.add_argument("--steps", type=int, default=10, help="Matcha Euler steps")
    ap.add_argument("--frontend", choices=["google", "extended"], default="google",
                    help="google = mirror the litert-samples G2P exactly; extended = also read "
                         "dates, years, ordinals and dictionary acronyms (NASA) as words")
    ap.add_argument("--out", default=os.path.join(HERE, "out"))
    ap.add_argument("--sentences", default="", help="subset, e.g. 1,3")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    print("fetching models (cached after the first run)")
    fetch.ensure_all()

    sentences = SENTENCES
    picked = list(range(1, len(sentences) + 1))
    if args.sentences:
        picked = [int(s) for s in args.sentences.split(",")]

    t = time.perf_counter()
    g2p = G2P(fetch.path(MATCHA, "g2p_dict.txt.gz"), fetch.path(MATCHA, "g2p_meta.json"),
              fetch.path(MATCHA, "dp_g2p_matcha_fp16.tflite"), fetch.path(MATCHA, "config.json"),
              args.threads)
    g2p_load = time.perf_counter() - t
    inflect_symbols = (["_"] + list(engines.KITTEN_PUNCT) + list(engines.KITTEN_LETTERS)
                       + list(engines.KITTEN_IPA))  # Inflect's symbols.py is the same list
    sym_check = check_symbol_tables(g2p.symbols, kitten=engines.KITTEN_SYMBOLS,
                                    inflect=inflect_symbols)
    print(f"G2P loaded in {g2p_load:.2f}s ({len(g2p.dictionary)} dictionary entries)")

    results, loads = {}, {}
    for name in [m.strip() for m in args.models.split(",") if m.strip()]:
        t = time.perf_counter()
        eng = build_engine(name, args)
        loads[name] = time.perf_counter() - t
        if name == "matcha":
            eng.g2p, eng.frontend = g2p, args.frontend
        voices = [v.strip() for v in args.voices.split(",")] if name == "kitten" else eng.voices
        print(f"{name}: loaded in {loads[name]:.2f}s; warming up")
        synth_utterance(eng, g2p, "Warm up.", voices[0], args.frontend)
        rows = []
        for voice in voices:
            for n in picked:
                pcm, row = synth_utterance(eng, g2p, sentences[n - 1], voice, args.frontend)
                wav = f"{name}-{voice.lower()}-{n}.wav"
                write_wav(os.path.join(args.out, wav), pcm, eng.sample_rate)
                row.update(n=n, voice=voice, wav=wav)
                rows.append(row)
                print(f"  {wav:24s} audio {row['audio_s']:5.2f}s  synth {row['synth_s']:6.3f}s  "
                      f"RTF {row['rtf']:.3f}  TTFA {row['ttfa_s'] * 1000:5.0f} ms"
                      + ("  TRUNCATED" if row["truncated"] else ""))
        results[name] = rows

    g2p_detail = [(n, sentences[n - 1], g2p.phonemize(sentences[n - 1], "spaced", args.frontend))
                  for n in picked]
    write_report(os.path.join(args.out, "report.md"), results, loads, g2p_load, sym_check, args,
                 g2p_detail)
    write_html(os.path.join(args.out, "samples.html"), results, sentences)
    print(f"wrote {args.out}/report.md and samples.html")


if __name__ == "__main__":
    main()
