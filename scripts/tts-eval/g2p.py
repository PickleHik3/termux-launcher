# Copyright 2026 The Google AI Edge Authors. All Rights Reserved.
# Licensed under the Apache License, Version 2.0. See LICENSE-TERMINAL-EMULATOR.
# https://www.apache.org/licenses/LICENSE-2.0
# Distributed on an AS IS basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
# Modified for Termux Launcher: Python adaptation of Google AI Edge LiteRT samples
# for host-side speech evaluation. See THIRD_PARTY_NOTICES.md and the module docstring.

"""GPL-free English G2P shared by the three TTS models (no espeak-ng, no phonemizer package).

A Python port of Google's litert-samples `KittenG2P.kt` / `MatchaG2P.kt`
(samples/litert/text_to_speech{,_streaming}/, Apache-2.0; the same logic as the streaming
sample's `python/kitten_tts.py`):

  1. a 275k-entry espeak-style IPA dictionary (`g2p_dict.txt.gz`, OpenPhonemizer, Clear BSD),
  2. DeepPhonemizer (`dp_g2p_matcha_fp16.tflite`, MIT) for words missing from the dictionary:
     char ids, each repeated `char_repeats` times, wrapped in <en_us>...<end>, zero-padded to
     96 -> logits [96, 64] -> argmax -> collapse repeats -> IPA,
  3. host normalisation: ALL-CAPS runs of 2+ letters are spelled letter by letter, numbers are
     read as words ("4090" -> "four thousand ninety"),
  4. the IPA string is mapped onto the 178-symbol StyleTTS2/keithito table, which is the same
     table in all three model repos (checked by `check_symbol_tables`).

The one difference between the two Kotlin files is punctuation: KittenG2P emits it as its own
space-separated token ("wˈɜːld ,", the pip package's `basic_english_tokenize`); MatchaG2P
attaches it to the preceding word ("wˈɜːld,"), which is also what espeak's
`preserve_punctuation` gives Inflect. `punct="spaced"` / `punct="attached"` selects the style.

`frontend="extended"` adds an opt-in normalisation pass that is NOT in Google's samples (dates,
years, ordinals, dictionary-pronounceable acronyms such as NASA); the default, "google",
mirrors the samples exactly.
"""
import gzip
import json
import re
from dataclasses import dataclass, field

import numpy as np

try:
    from ai_edge_litert.interpreter import Interpreter
except ImportError:  # pragma: no cover
    from tflite_runtime.interpreter import Interpreter

# Case-aware tokens: ACRONYM (2+ caps) | NUMBER | word | punctuation. Anything else in the
# input (hyphens, %, &, brackets...) matches nothing and is silently dropped, as in Kotlin.
TOKEN = re.compile(r"[A-Z]{2,}|\d[\d,]*(?:\.\d+)?|[A-Za-z']+|[.,!?;:—…\"]")
ACRONYM = re.compile(r"[A-Z]{2,}\Z")
WORD = re.compile(r"[A-Za-z']+\Z")

# espeak letter-name IPA ("GPU" -> dʒˈiːpˈiːjˈuː), copied from the Kotlin samples.
LETTER_IPA = {
    "a": "ˈeɪ", "b": "bˈiː", "c": "sˈiː", "d": "dˈiː", "e": "ˈiː", "f": "ˈɛf",
    "g": "dʒˈiː", "h": "ˈeɪtʃ", "i": "ˈaɪ", "j": "dʒˈeɪ", "k": "kˈeɪ", "l": "ˈɛl",
    "m": "ˈɛm", "n": "ˈɛn", "o": "ˈoʊ", "p": "pˈiː", "q": "kjˈuː", "r": "ˈɑːɹ",
    "s": "ˈɛs", "t": "tˈiː", "u": "jˈuː", "v": "vˈiː", "w": "dˈʌbəljˌuː", "x": "ˈɛks",
    "y": "wˈaɪ", "z": "zˈiː",
}
ONES = ("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
        "eighteen", "nineteen")
TENS = ("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
SCALES = ("", "thousand", "million", "billion", "trillion")


# ------------------------------------------------------------------------ numbers (Kotlin port)

def number_to_words(raw):
    """'1,234.5' -> ['one','thousand','two','hundred','thirty','four','point','five']."""
    token = raw.replace(",", "")
    if "." in token:
        whole, _, frac = token.partition(".")
        words = integer_to_words(int(whole)) if whole else ["zero"]
        words.append("point")
        words.extend(ONES[int(d)] for d in frac if d.isdigit())
        return words
    try:
        return integer_to_words(int(token))
    except ValueError:
        return []


def _under_thousand(n):
    words = []
    if n >= 100:
        words += [ONES[n // 100], "hundred"]
        n %= 100
    if n >= 20:
        words.append(TENS[n // 10])
        n %= 10
    if n > 0:
        words.append(ONES[n])
    return words


def integer_to_words(value):
    if value == 0:
        return ["zero"]
    if value < 0:
        return ["minus"] + integer_to_words(-value)
    groups = []
    while value > 0:
        groups.append(value % 1000)
        value //= 1000
    if len(groups) > len(SCALES):
        digits = "".join(str(g).zfill(3) for g in reversed(groups)).lstrip("0")
        return [ONES[int(d)] for d in digits]
    words = []
    for i in reversed(range(len(groups))):
        if groups[i] == 0:
            continue
        words.extend(_under_thousand(groups[i]))
        if SCALES[i]:
            words.append(SCALES[i])
    return words


# ------------------------------------------------------- extended normalisation (opt-in, ours)

MONTHS = ("January|February|March|April|May|June|July|August|September|October|November|"
          "December")
_ORDINAL_IRREGULAR = {"one": "first", "two": "second", "three": "third", "five": "fifth",
                      "eight": "eighth", "nine": "ninth", "twelve": "twelfth"}


def _ordinal_words(n):
    words = integer_to_words(n)
    last = words[-1]
    if last in _ORDINAL_IRREGULAR:
        words[-1] = _ORDINAL_IRREGULAR[last]
    elif last.endswith("y"):
        words[-1] = last[:-1] + "ieth"
    else:
        words[-1] = last + "th"
    return " ".join(words)


def _year_words(y):
    if 2000 <= y <= 2009:
        return " ".join(integer_to_words(y))
    hi, lo = divmod(y, 100)
    if lo == 0:
        return " ".join(integer_to_words(hi) + ["hundred"])
    lo_words = (["oh"] + integer_to_words(lo)) if lo < 10 else integer_to_words(lo)
    return " ".join(integer_to_words(hi) + lo_words)


def extended_normalize(text, dictionary):
    """Dates, years, ordinals and pronounceable acronyms. Not part of Google's samples."""
    def day_month(m):
        return f"the {_ordinal_words(int(m.group(1)))} of {m.group(3)}"

    def month_day(m):
        return f"{m.group(1)} {_ordinal_words(int(m.group(2)))}"

    def month_year(m):
        return f"{m.group(1)} {_year_words(int(m.group(2)))}"

    text = re.sub(rf"\b(\d{{1,2}})(st|nd|rd|th)?\s+({MONTHS})\b", day_month, text)
    text = re.sub(rf"\b({MONTHS})\s+(\d{{1,2}})(st|nd|rd|th)?\b(?!\d)", month_day, text)
    text = re.sub(rf"\b({MONTHS}),?\s+(1[1-9]\d\d|20\d\d)\b", month_year, text)
    text = re.sub(r"\b(in|since|year|by|until)\s+(1[1-9]\d\d|20\d\d)\b",
                  lambda m: f"{m.group(1)} {_year_words(int(m.group(2)))}", text)
    text = re.sub(r"\b(\d+)(st|nd|rd|th)\b", lambda m: _ordinal_words(int(m.group(1))), text)
    # 4+ letter acronyms that the dictionary knows as a word (NASA, JSON) are read as words;
    # shorter ones (US, IT, BBC) stay spelled, since the lower-case form is often another word.
    text = re.sub(r"\b[A-Z]{4,}\b",
                  lambda m: m.group(0).lower() if m.group(0).lower() in dictionary else m.group(0),
                  text)
    return text


# ------------------------------------------------------------------------------------ the G2P

@dataclass
class G2PResult:
    text: str                      # text after normalisation (what was tokenised)
    ipa: str                       # IPA string before the symbol mapping
    ids: list                      # symbol ids (no BOS/EOS, no blanks)
    dict_words: int = 0            # words (incl. number words) found in the dictionary
    neural_words: list = field(default_factory=list)   # [(word, ipa)] from DeepPhonemizer
    acronyms: list = field(default_factory=list)       # ALL-CAPS tokens spelled out
    numbers: list = field(default_factory=list)        # [(digits, "words ...")]
    dropped_text: str = ""         # input characters the tokenizer ignored (not whitespace)
    unmapped: str = ""             # IPA characters absent from the symbol table
    neural_chars_dropped: str = "" # word characters the neural G2P has no id for (e.g. ')
    seconds: float = 0.0


class G2P:
    def __init__(self, dict_path, meta_path, model_path, config_path, threads=4):
        meta = json.load(open(meta_path, encoding="utf-8"))
        self.char_to_index = {c: i for c, i in meta["char2idx"].items() if len(c) == 1}
        self.index_to_phoneme = {int(i): p for i, p in meta["idx2ph"].items()}
        self.char_repeats = meta["char_repeats"]
        self.start_id = meta["start"]
        self.end_id = meta["end"]
        self.max_tokens = meta["MAXT"]
        self.num_phonemes = meta["n_phonemes"]
        self.special = set(meta["special"])

        self.symbols = json.load(open(config_path, encoding="utf-8"))["symbols"]
        # Later duplicates win ("'" appears twice), the same as Kotlin's HashMap and the
        # models' own `{s: i for i, s in enumerate(symbols)}`.
        self.symbol_to_id = {s: i for i, s in enumerate(self.symbols) if len(s) == 1}

        self.dictionary = {}
        with gzip.open(dict_path, "rt", encoding="utf-8") as f:
            for line in f:
                word, tab, ipa = line.rstrip("\n").partition("\t")
                if tab:
                    self.dictionary[word] = ipa

        self.it = Interpreter(model_path=str(model_path), num_threads=threads)
        self.it.allocate_tensors()
        self.inp = self.it.get_input_details()[0]
        self.out = self.it.get_output_details()[0]
        self._neural_cache = {}

    # -- neural fallback -------------------------------------------------------------------
    def phonemize_word(self, word):
        """One lower-case word -> espeak-style IPA via DeepPhonemizer. Returns (ipa, dropped)."""
        if word in self._neural_cache:
            return self._neural_cache[word]
        ids = [self.start_id]
        dropped = ""
        for ch in word:
            idx = self.char_to_index.get(ch)
            if idx is None:
                dropped += ch
                continue
            ids.extend([idx] * self.char_repeats)
        ids.append(self.end_id)
        length = min(len(ids), self.max_tokens)
        x = np.zeros((1, self.max_tokens), np.float32)
        x[0, :length] = ids[:length]
        self.it.set_tensor(self.inp["index"], x)
        self.it.invoke()
        logits = self.it.get_tensor(self.out["index"]).reshape(self.max_tokens, self.num_phonemes)
        result, previous = [], -1
        for best in np.argmax(logits[:length], axis=1):
            best = int(best)
            if best == previous:
                continue
            previous = best
            ph = self.index_to_phoneme.get(best)
            if not ph or ph in self.special or best == 0:
                continue
            result.append(ph.replace("-", ""))
        out = ("".join(result), dropped)
        self._neural_cache[word] = out
        return out

    def _word(self, word, res):
        ipa = self.dictionary.get(word)
        if ipa:
            res.dict_words += 1
            return ipa
        ipa, dropped = self.phonemize_word(word)
        res.neural_words.append((word, ipa))
        res.neural_chars_dropped += dropped
        return ipa

    # -- sentence --------------------------------------------------------------------------
    def phonemize(self, text, punct="spaced", frontend="google"):
        import time
        t0 = time.perf_counter()
        if frontend == "extended":
            text = extended_normalize(text, self.dictionary)
        res = G2PResult(text=text, ipa="", ids=[])
        pieces = []  # list of (kind, str)

        last_end = 0
        dropped = []
        for m in TOKEN.finditer(text):
            gap = text[last_end:m.start()]
            dropped.extend(c for c in gap if not c.isspace())
            last_end = m.end()
            tok = m.group()
            if ACRONYM.match(tok):
                res.acronyms.append(tok)
                pieces.append(("w", "".join(LETTER_IPA[c] for c in tok.lower() if c in LETTER_IPA)))
            elif tok[0].isdigit():
                words = number_to_words(tok)
                res.numbers.append((tok, " ".join(words)))
                for w in words:
                    pieces.append(("w", self._word(w, res)))
            elif WORD.match(tok):
                pieces.append(("w", self._word(tok.lower(), res)))
            else:
                pieces.append(("p", tok))
        dropped.extend(c for c in text[last_end:] if not c.isspace())
        res.dropped_text = "".join(dropped)

        out = []
        for kind, s in pieces:
            if not s:
                continue
            if kind == "p" and punct == "attached":
                out.append(s)          # glued to the preceding word
                continue
            if out:
                out.append(" ")
            out.append(s)
        res.ipa = "".join(out)
        ids, unmapped = [], []
        for ch in res.ipa:
            i = self.symbol_to_id.get(ch)
            if i is None:
                unmapped.append(ch)
            else:
                ids.append(i)
        res.ids = ids
        res.unmapped = "".join(sorted(set(unmapped)))
        res.seconds = time.perf_counter() - t0
        return res


def check_symbol_tables(config_symbols, **tables):
    """Assert every model's symbol list equals config.json's (index 0, the pad, may differ)."""
    report = {}
    for name, table in tables.items():
        same = len(table) == len(config_symbols) and list(table[1:]) == list(config_symbols[1:])
        report[name] = same
        if not same:
            diff = [(i, a, b) for i, (a, b) in enumerate(zip(table, config_symbols)) if a != b]
            raise ValueError(f"{name} symbol table differs from Matcha config.json: "
                             f"len {len(table)} vs {len(config_symbols)}, first diffs {diff[:5]}")
    return report
