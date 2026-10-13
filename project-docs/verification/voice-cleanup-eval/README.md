# Dictation cleanup eval

A fixed, repeatable check of dictation cleanup quality and speed. It runs on the phone against
the local TAI endpoint and scores without a model judge. The first results and the reasoning
behind the variants are in
`project-docs/reference/voice-ai/voice-cleanup-eval-2026-10-13.md`.

## Files

| File | What it is |
|---|---|
| `cases.jsonl` | 60 cases. Each has `id`, `tags`, `raw` (as speech-to-text heard it), `ref` (a hand-written faithful cleanup), and optional `must` (case-insensitive regexes the output must contain), `must_not` (regexes it must not contain) and `note`. |
| `cleanup_eval.py` | The runner and scorer. Standard library only; runs in Termux and on a host. |
| `run_on_phone.sh` | Runs `cleanup_eval.py run` under a memory watchdog. |
| `pipeline.jsonl` | What the app's pipeline (variant `E`) decides for every case at both levels before any model call: the language, the text with fillers stripped, and the gate's groups. Written by `expect`; `VoiceCleanupEvalParityTest` checks the Java against it. |

The tags are `anchor`, `filler`, `stutter`, `selfcorrect`, `cross-sentence`, `restart`,
`mishear`, `tech`, `garble`, `runon`, `unpunctuated`, `list`, `short`, `nearcommand`, `question`,
`instruction`, `guard`, `foreign`, `clean`, `numbers`, `symbols`, `negation` and `long`. `anchor`
is the developer's own dictation from `VoicePolishRulesTest.ANCHOR`, and its reference is that
test's ideal rewrite. The 12 `clean` cases must come back unchanged.

Each reference uses one layout for both levels. Word error rate (WER) ignores layout, so a
Polished list and a Light line score the same.

## Variants

| Variant | What it sends |
|---|---|
| `A-light`, `A-polished` | The app's request, byte for byte: the `VoicePolishRules` system turn, the `<transcript>` user turn, temperature 0, `thinking: false`, `function: tidy_dictation`, and `max_tokens` from `maxTokens`. |
| `B` | Edits only. The model lists `old => new` lines, and a deterministic applier patches the raw text. |
| `B-json` | The same edits as a JSON array of `{"from", "to"}` objects. This is a prompt format only: TAI does not pass `response_format` to LiteRT-LM, so nothing constrains the decoding. |
| `B-sent` | The sentences are numbered, and the model returns only the numbered sentences it changes. |
| `C` | Sentence gating. Only sentences with a surface sign of trouble go to the model, as a Light rewrite with one sentence either side as read-only context. See `sentence_flags` for the signs. |
| `D` | `A-polished` with Google's recommended sampling: temperature 1.0, top_p 0.95, top_k 64. |
| `E-light`, `E-polished` | The app's pipeline since the second 2026-10-13 round, per case as one part: the language read from the text (`VoiceTextLanguage`), fillers stripped (`VoiceFillers`), the gate (`VoiceCleanupGate`), and one call per flagged group with the sentence before it as `<earlier>` and the language named unless it is English (`VoiceGatedCleanup`, `VoicePolishRules`). Unclear language: fillers only, no call. Experiment suffixes: `-later` adds the sentence after as `<later>`, `-en` names English too, `-keep2` and `-nokeep` swap the KEEP clause. |
| `A-light-…`, `A-polished-…` | The full rewrite with prompt changes: `-keep` (the KEEP clause), `-keep2` (its alternative), `-lang` (the language line, English included), `-noen` (no line for English); `-new` is `-keep-lang`. |

Every variant's output passes the app's `accept` guard, ported. When the guard refuses an answer,
the raw text is scored, as the app would type it. `check-java` confirms that the ported prompt,
guard, language and filler constants still match the Java (`VoicePolishRules.java` and the
`VoiceTextLanguage`, `VoiceFillers` and `VoiceCleanupGate` files beside it). After changing the
pipeline on either side, run `expect` and commit `pipeline.jsonl`; `VoiceCleanupEvalParityTest`
then fails until the two agree.

## Running on the phone

```sh
scp cleanup_eval.py cases.jsonl run_on_phone.sh termux:cleanup-eval/
ssh termux 'tai load gemma-4-e2b-it-litert-lm --gpu'
ssh termux 'cd ~/cleanup-eval; nohup bash run_on_phone.sh out --variants A-light,A-polished,B,C --runs 2 --warmup > run.log 2>&1 &'
ssh termux 'tail -3 ~/cleanup-eval/run.log'      # poll until "runner exit"
scp -r termux:cleanup-eval/out results/
ssh termux 'tai unload; rm -rf ~/cleanup-eval'
```

The phone's login shell is fish, so keep remote commands simple and score on the host.

- `--only id1,id2` runs a subset, and `--no-stream` sends `stream: false` as the app does. With
  `--no-stream` there is no time to first token, only the total time.
- The watchdog polls MemAvailable every 0.25 s. Below `MIN_AVAILABLE_MB` (default 1200) it runs
  `tai cancel; tai unload`, and the runner stops before the next case. The lowest value seen is
  written to `<out>/min_available_kb`.
- Never inject input on the phone and never install anything. The developer may be using it.
- The run order of the variants is reversed on even runs, so heat builds up evenly across them.

`show` prints the exact request for one case and variant without sending it:

```sh
python3 cleanup_eval.py show --variant B-sent --case anchor
python3 cleanup_eval.py show --variant E-polished --case anchor   # language, stripped text, groups
python3 cleanup_eval.py check-java ../../../app/src/main/java/com/termux/app/terminal/inappkeyboard/voice/VoicePolishRules.java
python3 cleanup_eval.py expect                                    # rewrites pipeline.jsonl
```

## Scoring

```sh
python3 cleanup_eval.py score results/*.jsonl --cases-out per-case.jsonl
```

This prints one Markdown row per variant and run:

- **WER**: word error rate of the output against the reference. Before comparing, it lowercases,
  folds accents and apostrophes, drops list markers and punctuation, and maps number words up to
  ninety to digits. **WER raw** is the raw text's WER, as a baseline.
- **Improved / same / worse**: the number of cases whose WER went down, stayed level or went up
  against the raw text. "Worse" is the main risk to watch.
- **Filler removal**: the share of vocalised fillers (uh, um, er, hmm, äh, euh…) in the raw text
  that is gone from the output.
- **Content F1**: the bag-of-words F1 of the output's content words against the reference's.
- **Kept share**: the app guard's `keptShare` of the raw content words.
- **Clean unchanged**: the number of `clean` cases returned word for word. Punctuation and case
  are allowed to change.
- **Guard kept raw**: how many answers the ported `accept` refused.
- **Must miss** and **Must-not hit**: counts across all cases. They catch answered questions,
  followed instructions, translations, unresolved self-corrections and lost key terms.
- **Negation off**: the number of cases whose negation count differs from the reference.
- **Time**: time to first token (from the stream) and total time, as mean and p90; the mean over
  the `long` cases; and the mean output tokens from the server's usage figures. For `C`, time is
  the sum of its calls.

To compare variants, read them from the same run on the same cases. At temperature 0 a rerun
should match; check that it does before trusting a single run.

## Adding a case

Append one JSON line. Write the reference as the faithful text the speaker meant, with nothing
added. Run `score` on any results file before committing: a reference that fails its own
`must` or `must_not` checks, or that the guard refuses, is a broken case.
