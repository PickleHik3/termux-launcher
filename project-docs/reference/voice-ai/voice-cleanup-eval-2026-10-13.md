# Dictation cleanup: full rewrite or partial edits, measured on the phone (2026-10-13)

The developer asked: "is the model rewriting the whole thing or just the necessary parts — are we
doing the text tidying optimally?" This record answers it with the first runs of the fixed eval in
`project-docs/verification/voice-cleanup-eval/` (research proposal: section 8 of
`voice-cleanup-quality-research-2026-10-13.md`). No app code changed.

**Short answer.** The model rewrites the whole thing. Its output is about as long as the input, so
time grows with the length of the dictation, not with how much needs fixing. Asking E2B for only
the changes does not fix that. It does not write small edits: it copies whole sentences into both
sides of each edit, and a third of its edits change nothing. That costs 1.2–2.2× the output tokens
and up to 2.5× the time. What does save time is not sending text that has no sign of trouble. On
this set that halves the time and makes no case worse. Its cost is the mishearings, which leave no
surface sign. Details and the recommendation are at the end.

## Setup (measured)

- **Device.** Pong, through `ssh termux`, between 12:17 and 13:20 (+03) on 2026-10-13, on whatever
  build was installed then. The model was `gemma-4-e2b-it-litert-lm` (int4 LiteRT-LM) on the GPU,
  run by TAI's `/v1/chat/completions`. The main runs used a manual `tai load --gpu`, which gave a
  4096-token window. A check with the model loaded by the cleanup plan instead (an autoload from a
  `tidy_dictation` request: GPU, 2048 window, as the app loads it) timed the same (see Timing).
- **Request.** For A, the app's request byte for byte (`LocalTaiVoiceTextPolisher.request`): the
  `VoicePolishRules` system turn, the `<transcript>` user turn, `temperature: 0`,
  `thinking: false`, `function: tidy_dictation` and `max_tokens` from `maxTokens`. `check-java`
  confirms that the ported prompt and guard constants match the Java. The one difference is
  `stream: true`, so that time to first token (TTFT) can be read. `stream: false`, as the app sends
  it, gave the same total times (Timing).
- **Guard.** Every output went through a port of `VoicePolishRules.accept`. Where it refused, the
  raw text was scored, which is what the app would type.
- **Cases.** 60 cases (1603 raw words, 4–271 per case), written for this eval: fillers and
  stutters, self-corrections within and across sentences, technical mishearings, run-ons, spoken
  lists, short phrases, near-commands, questions and instructions that must not be answered,
  Spanish, German and French, 12 already-clean texts and the anchor dictation. Every case has a
  hand-written faithful reference. The case list is in `cases.jsonl`.
- **Runs.** Two runs of every variant, with the variant order reversed on the second run. All 60
  outputs of **every** variant were byte-identical between the runs, D included: TAI builds
  LiteRT-LM's `SamplerConfig` with seed 0 (`LiteRtTaiRuntime`), so even temperature 1.0 gives one
  fixed sample per prompt. Quality is therefore reported once. Times are reported for both runs,
  because they moved by up to 40% with the phone's state. The developer may have been using the
  phone.
- **Watchdog.** MemAvailable was polled every 0.25 s with a 1200 MB floor. It never fired; the
  lowest value seen was 2.7 GB. After the run the model was unloaded and the phone folder was
  deleted.

## Variants

| Variant | What the model is asked for | How its answer becomes text |
|---|---|---|
| **A-light / A-polished** | Today's full rewrite, with the exact shipped prompts | As the app does it |
| **B** | Only the changes, one `words from the transcript => replacement` per line, with one worked example | A deterministic applier finds each left side in the raw text (exactly, then ignoring case, then by its words) and replaces it |
| **B-json** | The same changes as a JSON array of `{"from","to"}` | The same applier |
| **B-sent** | The sentences are numbered `[1] [2] …`; the model returns only the numbered sentences it changes | Each returned sentence replaces its number |
| **C** | Sentence gating: only sentences with a surface sign of trouble are sent, as a Light rewrite with the sentence before (`<earlier>`, the app's wording) and the one after (`<later>`) as read-only context | Clean sentences pass through untouched |
| **D** | A-polished with Google's recommended sampling (temperature 1.0, top_p 0.95, top_k 64) | As A |

**Constrained decoding was not available.** LiteRT-LM has JSON-schema and regex response formats,
but TAI never passes `response_format` to the LiteRT runtime (only the image and speech routes
read it). B-json is therefore a prompt format, not a grammar. Testing a real constraint needs an app
change, which was out of scope.

**C's gate**, per sentence: a vocalised filler (uh, um, er, hmm, äh, euh…), a discourse filler
("you know", "I mean", "basically", a leading "like"), a word said twice in a row, a correction cue
("no wait", "actually no", "scratch that", "I mean", ", no,"), a spoken symbol ("slash", "dash
dash", "dot com"), more than 30 words, a lowercase start, or a function word capitalised
mid-sentence ("the apps The swiping"). Parakeet emits that last sign where it stitches chunks
together, which is where its garbles sit. A sentence with a correction cue also pulls in the
sentence before it, and a flagged sentence of under four words is sent together with the next one.
All of these are cheap to compute and none needs the recogniser. The gate sent 51 of the 112
sentences, and 30 of the 60 cases needed no call at all.

**Fixes to the harness during the run.** These are fixes to how B's and C's answers are applied,
and none changes what the model is asked:

- **B's overlap.** E2B often wrote a short left side and then carried the right side on through
  the rest of the sentence ("The command needs dash dash verbose => The command needs --verbose to
  print the full log."), which doubled the tail of the sentence. The applier now grows the span to
  cover words the replacement repeats. Before this change B scored WER 0.173 with 7 cases worse.
  `score` re-applies the edits from the stored outputs, so no rerun was needed.
- **C's echoes.** C's outputs repeated the `<later>` sentence, sometimes reworded, and repeated a
  short `<earlier>` sentence that the app's `withoutEcho` does not trim because it is under eight
  words. C now trims both. The C rows below are from a rerun with these trims. The first run
  scored WER 0.127 with 2 cases worse, both duplicated sentences.

## Results (measured)

Raw-text WER, the baseline to beat, is 0.196. The quality columns are identical for both runs.
Times are given as run 1 / run 2.

| Variant | WER | Improved / same / worse | Filler removal | Content F1 | Clean unchanged | Guard kept raw | Must miss / must-not hit | TTFT mean | Total mean | Total p90 | Long-case mean | Anchor total | Output tokens (sum) | Calls |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A-light | 0.132 | 27 / 26 / 7 | 79% | 0.950 | 9/12 | 3 | 9 / 1 | 0.79 / 0.75 s | 2.30 / 2.16 s | 3.45 / 3.46 s | 7.9 / 7.2 s | 17.0 / 15.4 s | 1850 | 60 |
| A-polished (shipped default) | 0.144 | 26 / 26 / 8 | 75% | 0.940 | 9/12 | 3 | 10 / 1 | 0.94 / 0.85 s | 2.45 / 2.18 s | 3.58 / 3.13 s | 8.0 / 7.0 s | 17.5 / 15.3 s | 1859 | 60 |
| B (`=>` edits) | 0.113 | 26 / 31 / 3 | 46% | 0.947 | 11/12 | 1 | 10 / 0 | 0.79 / 0.75 s | 3.30 / 3.05 s | 4.49 / 4.10 s | 12.5 / 11.0 s | 34.4 / 30.7 s | 3011 | 60 |
| B-json | 0.111 | 31 / 23 / 6 | 89% | 0.945 | 9/12 | 1 | 8 / 1 | 0.89 / 0.88 s | 4.25 / 4.25 s | 7.31 / 7.29 s | 15.3 / 15.6 s | 39.3 / 39.1 s | 4018 | 60 |
| B-sent | 0.117 | 25 / 32 / 3 | 39% | 0.948 | 11/12 | 0 | 8 / 1 | 0.94 / 0.95 s | 2.79 / 2.94 s | 3.78 / 3.73 s | 9.3 / 11.3 s | 21.1 / 30.3 s | 2246 | 60 |
| C (gated) | 0.120 | 20 / 40 / **0** | 79% | 0.939 | **12/12** | 3 | **17** / 1 | 0.73 / 0.70 s* | **1.20 / 1.14 s** | 2.43 / 2.22 s | **5.5 / 5.1 s** | **13.8 / 11.9 s** | 1041 | 33 |
| D (T=1.0, seed 0) | 0.146 | 26 / 25 / 9 | 71% | 0.933 | 10/12 | 3 | 10 / 2 | 1.17 / 1.35 s | 3.14 / 3.59 s | 4.74 / 5.19 s | 10.4 / 11.7 s | 17.9 / 26.2 s | 1874 | 60 |

\* C's TTFT is over the 30 cases that made a call. Its total mean counts the other 30 as 0 s.

How to read the columns:

- **WER** is against the reference, after lowercasing, folding accents and dropping punctuation and
  list markers.
- **Improved / same / worse** compares each case's WER with its raw text's.
- **Filler removal** is the share of vocalised fillers that are gone.
- **Content F1** is the bag-of-words F1 of content words against the reference.
- **Clean unchanged** counts the clean cases returned word for word.
- **Must miss / must-not hit** count case-specific regexes: key terms that must survive, and
  answers, translations or corrected-away words that must not appear.
- **Long-case mean** covers the five 72–271-word cases.

WER by tag (run 1):

| Tag (n) | raw | A-light | A-polished | B | B-json | B-sent | C | D |
|---|---|---|---|---|---|---|---|---|
| filler (17) | 0.213 | 0.127 | 0.130 | 0.151 | 0.087 | 0.156 | 0.128 | 0.147 |
| stutter (6) | 0.306 | 0.103 | 0.101 | 0.113 | 0.076 | 0.133 | 0.099 | 0.116 |
| selfcorrect (9) | 0.533 | 0.328 | 0.456 | 0.295 | 0.339 | 0.323 | 0.328 | 0.490 |
| mishear (14) | 0.175 | 0.116 | 0.120 | 0.117 | 0.123 | 0.104 | 0.157 | 0.123 |
| list (6) | 0.170 | 0.148 | 0.147 | 0.207 | 0.229 | 0.164 | 0.134 | 0.141 |
| short (4) | 0.062 | 0.277 | 0.146 | 0.000 | 0.000 | 0.062 | 0.062 | 0.062 |
| instruction (3) | 0.026 | 0.083 | 0.083 | 0.042 | 0.000 | 0.042 | 0.000 | 0.062 |
| foreign (3) | 0.144 | 0.144 | 0.144 | 0.123 | 0.000 | 0.106 | 0.144 | 0.144 |
| clean (12) | 0.000 | 0.097 | 0.054 | 0.010 | 0.027 | 0.010 | 0.000 | 0.021 |
| long (5) | 0.175 | 0.149 | 0.148 | 0.166 | 0.153 | 0.152 | 0.149 | 0.168 |

### How the edit formats actually came back (measured, run 1)

| | B | B-json | B-sent |
|---|---|---|---|
| Answers not in the format at all | 16 of 60 (full rewrites; nothing to apply, so the raw text stays) | 1 of 60 | 0 |
| Edits | 84 | 127 | 110 of 112 sentences returned |
| Median left-side length | 10.5 words (55% at 10 or more) | 8 words (43% at 10 or more) | whole sentences |
| Edits that change nothing | 31 (37%) | 40 (31%) | 52 sentences returned verbatim |
| Left sides not found in the raw text | 0 | 2 | — |
| Anchor output tokens (A: 322–324) | 649 | 746 | 390 |

Other failures:

- **B-sent** renumbered the anchor's sentences. It split sentence 13 in two, so its `[14]`
  overwrote "In addition…" and its `[15]` matched nothing. The last sentence of the dictation was
  silently lost.
- **B** lost content when an edit's left side ran over the words it was meant to keep:
  "Shopping list, um, eggs, milk, the oat one => the oat milk, …" deleted "eggs".

### Timing (measured)

- **Decode is the cost.** E2B decoded at about 20–23 tokens/s for these requests, and TTFT was
  0.7–1.0 s at 323–746 prompt tokens. Every variant's total time is close to TTFT plus output
  tokens divided by 21.
- **Streaming is not the cause.** The anchor (271 words, 322–324 output tokens) took 14.8–17.5 s
  in every A run: streamed, with `stream: false`, and with the model loaded by the cleanup plan
  (2048 window) rather than by hand (4096). The six long cases averaged 6.4–6.7 s streamed and
  6.4 s with `stream: false`.
- **The 7 s figure was not reproduced.** The brief's "a warm 250-word dictation takes about 7 s"
  did not happen on pong today: a 250-word dictation would take about 14 s at this rate. That
  figure may come from a different configuration, such as speculative decoding, or a different
  build. That is not known.
- **A spends a third of its time on words it does not change.** In 24 of A-light's 60 outputs
  (3 of them guard fallbacks) the words are the raw text's, with only punctuation and case
  changed. Those cases took 47 s of the run's 138 s. Output tokens came to 1.15 per raw word on
  both levels.
- **Sampling at temperature 1.0 is slower.** D's TTFT was 0.3–0.5 s longer than A-polished's on
  the same prompts.

## Side by side (run 1)

**Anchor, the second point.** The raw text is:

> The second thing is I whenever I swipe down on the app drawer in the on the pinned I control it
> opens the app. But if I am somewhere else, if I am in a filtered list the apps The swiping down
> does not trigger opening of afterwire. This was a deliberate decision I wanna go back on you know,
> I want users to be able to access the apps drawer no matter where they just the Pin Appa the and
> the like

| Variant (whole anchor) | Output |
|---|---|
| A-polished (17.5 s, 322 tokens) | The second thing is, whenever I swipe down on the app drawer in the pinned area, it controls it to open the app. But if I am somewhere else, if I am in a filtered list, the apps, the swiping down does not trigger opening of the drawer. This was a deliberate decision. I want to go back on that. I want users to be able to access the app drawer no matter where they are, just by pinning the app and the like. |
| B (34.4 s, 649 tokens) | The second thing is whenever I swipe down on the app drawer in the pinned area, it controls it opens the app. But if I am somewhere else, if I am in a filtered list the apps, swiping down does not trigger opening of the app drawer. This was a deliberate decision, I want users to be able to access the app drawer no matter where they just pin the app and the like. |
| B-json (39.3 s, 746 tokens) | The second thing is whenever I swipe down on the app drawer in the pinned I control it opens the app. But if I am somewhere else, if I am in a filtered list the apps, the swiping down does not trigger opening of afterwire. This was a deliberate decision, I want users to be able to access the app drawer no matter where they just pin the app and the like |
| B-sent (21.1 s, 390 tokens) | The second thing is, whenever I swipe down on the app drawer in the pinned, I control it opens the app. … This was a deliberate decision. I want to go back on you know, I want users … no matter where they just pin the app or the like. *(The dictation's last sentence is lost; see above.)* |
| C (13.8 s, 2 calls, 234 tokens) | The second thing is I whenever I swipe down on the app drawer in the on the pinned I control it opens the app. *(not sent: no surface sign)* But if I am somewhere else, if I am in a filtered list, the swiping down does not trigger the opening of the app drawer. This was a deliberate decision, I want to go back on you know, I want users to be able to access the app drawer no matter where they are, just pin the app and the like. |

Only A turned "In app kbot" into "in-app keyboard". B, B-json, C and D wrote "in-app bot". Every
variant left "uh" in "I feel like, uh, it's well, basically".

**Spanish** (`foreign-es`). Raw: "Eh, mañana no puedo ir a la reunión, eh, porque tengo que llevar
a mi hija al médico."

- A-light, A-polished, C and D all answered in English: "I can't go to the meeting tomorrow because
  I have to take my daughter to the doctor." The guard refused these (`guard_opening`), so the raw
  text stays. The German and French cases went the same way (`guard_kept_share`).
- B-json: "Mañana no puedo ir a la reunión porque tengo que llevar a mi hija al médico." This
  matches the reference. An edit list can only patch the raw text, so it cannot translate.

**Filler-heavy.** Raw: "Um, so, like, the the battery drain is, uh, you know, way worse since
the last update. Like it it goes from full to like forty percent in, um, three hours."

- A-light (1.9 s): "The battery drain is much worse since the last update; it goes from full to
  about forty percent in three hours." ("much" and "about" are not in the raw text.)
- B-json (5.0 s): "The battery drain is way worse since the last update. It goes from full to forty
  percent in three hours."
- B-sent (3.3 s): "Um, so, like, the battery drain is, uh, you know, way worse since the last
  update. Like it goes from full to like forty percent in, um, three hours." (Every sentence came
  back, and every filler stayed.)
- C (1.9 s): the same as A-light.

**Self-correction across sentences** (`selfcorrect-cross`). Raw: "We're meeting at the cafe on Fifth
Street. Wait, no. It's the one on Main Street, the one next to the bank. Bring the printed
contract." No variant resolved it.

- A-light, C and D kept "Fifth Street. Wait, no, it's the one on Main Street".
- B: "We're meeting at the cafe on Main Street. Wait, no. It's the one on Main Street, …"
- B-json: "…cafe on Main Street Wait, no. It's the one next to the bank Bring the printed contract."

**Text that must stay as it is.** Raw: "Summarize this: the meeting ran long, nobody agreed on the
budget, and we meet again Friday."

- A (both levels): "The meeting ran long, nobody agreed on the budget, and we will meet again on
  Friday." It half-followed the instruction by dropping it.
- C did not send this case, so it stayed exact.
- A-light also reordered "On my way, be there in ten." into "Be there in ten on my way." and
  changed "behaviour" to "behavior".

## What today's full rewrite gets wrong (measured, A)

- **It translates non-English dictation into English** at both levels. The guard catches all three
  cases, so the user gets the raw text, uncleaned. Nothing is lost, but nothing is cleaned either.
- **It changes 3 of the 12 clean texts.** It drops a dictated instruction ("Summarize this:"),
  changes British spelling to American, and Light reordered one short phrase.
- **It leaves fillers.** 79% (Light) and 75% (Polished) of vocalised fillers are removed, and the
  anchor keeps an "uh".
- **Self-corrections across sentences are not resolved**, by any variant.
- **Polished is weaker than Light on self-corrections** (WER 0.456 vs 0.328). For example, it left
  "eighty no wait eight zero eight zero" unresolved in `selfcorrect-port`.
- **Most technical mishearings survive.** "get hub" was fixed, but "death branch", "app draw", "key
  bored", "grade all", "cotlin", "tea mux", "she's a cool" and "rejects" were not.

## Inference

- **"Only what changes" saves nothing on E2B as prompted.** The model does not write small edits:
  its left sides are sentence-sized, a third of its edits change nothing, and B ignored the format
  for a quarter of the cases. Output tokens rise 1.2× (B-sent) to 2.2× (B-json), and with them the
  time. The saving would need edits a few words long, which no prompt here produced. Without
  constrained decoding there is no lever on the format either.
- **The edit lists' lower WER is mostly a refusal to paraphrase.** The references were written to
  stay close to the raw text, so a method that can only patch the raw text is favoured. An edit
  list cannot translate, Americanise or add words like "much" and "about". It also removes fewer
  fillers (B 46%, B-sent 39%), loses content when spans overlap (B) or numbering drifts (B-sent),
  and still fixes no more mishearings. On the anchor, B and B-json are no better than A, at twice
  the time.
- **The structural benefit is real but narrow.** An edit list can never translate and keeps the
  speaker's words. That fits non-English text and dictations that are already close to clean.
  B-json was well-formed in 59 of 60 answers, so a JSON format is reliable enough to build on if
  that benefit is wanted.
- **Gating is where the time goes, and it is safe.** C did not send text without a sign of
  trouble: 61 of 112 sentences and 30 of 60 cases. It cut total time by half (1.2 s against 2.3 s
  mean, 5.3 s against 7.5 s on long cases), made no case worse and left every clean text
  untouched. Its loss is mishearings. A misheard word without a filler or broken grammar around it
  is never sent ("death branch", "a rejects", "Jason file"), so the mishear WER stays near raw
  (0.157 against 0.116). Of everything the research document lists, word confidence from the
  recogniser (research item 1) is the signal that would point the gate at those words.
- **Context is a source of echoes.** C's read-only neighbours came back inside the answer, both
  verbatim and reworded, in 4 of C's 33 calls; the trims caught all 4. The same will happen to the
  pause-time cleanup's `<earlier>` context, where `withoutEcho` trims only runs of at least eight
  words. That is worth a check in this eval once those parts can be replayed.
- **Sampling at temperature 1.0 is no better and is slower.** Every quality metric was slightly
  worse (9 cases worse against 8; it left "Mark" in a self-correction), and TTFT was 0.3–0.5 s
  longer. With seed 0 it is also no less deterministic than temperature 0.

## Recommendation

Do not replace the full rewrite with an edit-list output on E2B. As prompted, it emits more
tokens than the rewrite, takes 1.2–2.5× as long and removes fewer fillers. Its one structural
benefit, that it cannot translate, is better got another way.

Cut the time by sending less instead. Gate each part on cheap signs (C's rules), and pass clean
text through untouched. This fits the pause-time cleanup, which already splits a dictation into
parts. Keep the full rewrite as the unit for what is sent. Next, in order:

1. Add the recogniser's word confidence to the gate, so mishearings are sent too.
2. Strip vocalised fillers deterministically, before or after the model.
3. Stop the translation of non-English dictation. The guard already prevents the harm, but the
   cleanup is lost. An in-language rule, or an edit-list fallback for non-English text, would keep
   the cleanup.

Rerun this eval after each change; on this runtime one run per variant is enough.

## Still owed

- A gated variant built from the app's real pause-time parts (`VoiceCleanupPlan`) rather than this
  eval's sentence splitter, and its echo rate with `<earlier>` context.
- The developer's own dictations as cases. Twelve real Parakeet sessions with hand-corrected
  references would replace the synthetic `raw` texts here, which are the main limit on how far
  these numbers transfer.
- Where the "~7 s for 250 words" figure came from: the configuration or build that decodes about
  twice as fast as what was measured here.

## Second round: the gated pipeline in the app (2026-10-13, afternoon)

The recommendation above is now app code: each cleanup part has its vocalised fillers stripped
without the model (`VoiceFillers`), its language read from the text unless the speech model
gives one (`VoiceTextLanguage`), and only the sentences with a surface sign of trouble sent, with
the sentence before them as `<earlier>` (`VoiceCleanupGate`, `VoiceGatedCleanup`). The prompt
gains two clauses: keep the speaker's spelling and any dictated instruction or label, and, for a
language other than English, "The transcript is in Spanish. Write in Spanish." Variant **E** in
`cleanup_eval.py` mirrors this pipeline, and `VoiceCleanupEvalParityTest` checks that the Java
decides the same language, stripped text and groups for every case at both levels.

### Setup (measured)

- **Device and model.** Pong over `ssh termux`, between 14:04 and 14:40 (+03), on whatever build
  was installed; E2B on the GPU from a manual `tai load --gpu`, streamed, temperature 0. One run
  per variant. A, C and the 2026-10-13 morning rows gave identical outputs, so quality is
  comparable across the day; times are comparable only within a run.
- **Two runs.** Run 1 (14:04–14:15): A-light, A-polished, C, E-light-later-en, E-polished-later-en,
  E-light-en, A-light-new. Run 2 (14:17–14:27): A-light-keep, A-light-keep2, A-light-lang, E-light,
  E-light-keep2, E-polished. Within run 1, A-light-new (run last) averaged 2.27 s against
  A-light's 1.85 s (run first) on about the same output tokens; one run cannot tell the longer
  system turn from the phone warming up, so compare times within a run only.
- **Watchdog.** Never fired; the lowest MemAvailable was 3.5 GB. The model was unloaded and the
  phone folder deleted afterwards.
- **The suffixes.** `-later` adds the sentence after a group as `<later>` (C's context), `-en`
  names English in the prompt too, `-keep2` swaps the KEEP clause for "Keep British or American
  spelling as it was heard…", and the A suffixes put the clauses on the full rewrite (`-new` is
  KEEP plus the language line, English included). **E-light** and **E-polished** are what the
  app ships: no `<later>`, no line for English, the first KEEP clause.

### Results (measured)

Raw-text WER is 0.196. E's mean time counts its 35 cases without a call as 0 s.

| Variant | Run | WER | Improved / same / worse | Filler removal | Content F1 | Clean unchanged | Guard kept raw | Must miss / must-not hit | Total mean | Total p90 | Long-case mean | Output tokens (sum) | Calls |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| A-light | 1 | 0.132 | 27 / 26 / 7 | 79% | 0.950 | 9/12 | 3 | 9 / 1 | 1.85 s | 2.74 s | 6.29 s | 1850 | 60 |
| A-polished | 1 | 0.144 | 26 / 26 / 8 | 75% | 0.940 | 9/12 | 3 | 10 / 1 | 2.08 s | 3.03 s | 6.57 s | 1859 | 60 |
| C | 1 | 0.120 | 20 / 40 / 0 | 79% | 0.939 | 12/12 | 3 | 17 / 1 | 1.09 s | 2.11 s | 4.89 s | 1041 | 33 |
| E-light-later-en | 1 | 0.102 | 24 / 36 / 0 | 100% | 0.939 | 12/12 | 0 | 18 / 1 | 1.04 s | 2.36 s | 5.37 s | 987 | 29 |
| E-light-en | 1 | 0.100 | 25 / 35 / 0 | 100% | 0.940 | 12/12 | 0 | 18 / 1 | 1.15 s | 2.46 s | 5.60 s | 921 | 29 |
| E-polished-later-en | 1 | 0.105 | 25 / 34 / 1 | 100% | 0.939 | 12/12 | 0 | 18 / 1 | 1.16 s | 2.51 s | 6.02 s | 1063 | 27 |
| A-light-new | 1 | 0.103 | 32 / 23 / 5 | 96% | 0.949 | 10/12 | 0 | 9 / 1 | 2.27 s | 3.57 s | 7.57 s | 1826 | 60 |
| A-light-keep | 2 | 0.115 | 28 / 25 / 7 | 75% | 0.954 | 9/12 | 3 | 8 / 1 | 2.28 s | 3.56 s | 7.77 s | 1845 | 60 |
| A-light-keep2 | 2 | 0.116 | 28 / 27 / 5 | 79% | 0.949 | 11/12 | 3 | 9 / 1 | 2.27 s | 3.49 s | 7.60 s | 1838 | 60 |
| A-light-lang | 2 | 0.130 | 30 / 22 / 8 | 86% | 0.947 | 9/12 | 0 | 10 / 1 | 2.25 s | 3.28 s | 7.68 s | 1850 | 60 |
| **E-light** (app) | 2 | **0.090** | 24 / 36 / **0** | **100%** | 0.947 | **12/12** | **0** | 17 / 1 | **1.15 s** | 2.55 s | **5.63 s** | 920 | 29 |
| E-light-keep2 | 2 | 0.106 | 25 / 35 / 0 | 100% | 0.940 | 12/12 | 0 | 18 / 1 | 1.14 s | 2.52 s | 5.52 s | 927 | 29 |
| **E-polished** (app) | 2 | 0.109 | 25 / 34 / 1 | **100%** | 0.943 | **12/12** | **0** | 17 / 1 | 1.25 s | 3.12 s | 5.94 s | 1019 | 27 |

Against today's full rewrite, the app's pipeline at Light has a third less WER (0.090 against
0.132), makes no case worse (7 before), removes every vocalised filler (79% before), leaves all 12
clean texts alone (9 before) and has no guard refusals (3 before). It is about half the time on
average (1.15 s against 2.28 s on the same run's full rewrites) and a quarter less on the long
cases (5.6 s against 7.7 s). E-polished's one worse case is "We're" written as "We are" in
`selfcorrect-cross`, which counts as two word errors.

What the gate sent: 50 of 112 sentences at Light and 58 at Polished (C: 51), and 35 of 60 cases
made no call (C: 30). After the strip, a sentence whose only trouble was a filler is not sent:
`filler-basic`, `question-dock`, `list-shopping` and the three non-English cases came back cleaned
with no call.

**The three non-English cases.** In E all three are cleaned by the strip alone and stay in their
language: "Mañana no puedo ir a la reunión, porque tengo que llevar a mi hija al médico.", "Ich
komme heute, etwas später, so gegen halb sieben.", "Je pense que le bouton est trop petit sur la
page d'accueil." (WER 0 each; the full rewrite and C left them raw after the guard refused an
English answer). No sentence of them was flagged, so E did not exercise the language line on
these cases. On the full rewrite it did: with the line, the Spanish and French answers matched
their references and the German one kept "Ähm" but stayed German, and the guard refused nothing.

### What each change did (measured)

- **`<later>` context does not help.** E-light-en and E-light-later-en differ only in it: WER
  0.100 against 0.102 and 921 against 987 output tokens, as the model writes the later sentence
  out. The app sends only `<earlier>`, one sentence.
- **Naming English hurts English.** On the full rewrite, A-light-lang made 8 cases worse against
  A-light's 7: it left "eighty, no, wait, eight zero eight zero" unresolved in
  `selfcorrect-port` (A-light: "8080") and kept "Um, so," in `filler-heavy`. A-light-new, with the
  line and KEEP, once wrote the Hindi "क्योंकि" for "because" in `filler-basic`. The app names only
  languages other than English.
- **The KEEP clause helps, and the first wording is better gated.** On the full rewrite it cut
  WER from 0.132 to 0.115 and kept "Summarize this:", but still wrote "behavior". The alternative
  wording kept 11 of 12 clean texts on the full rewrite, but in E, where clean texts are not sent,
  it scored 0.106 against 0.090.
- **Gating keeps its blind spot.** 17 must-have terms are missed, the same mishearings as C's
  ("app draw", "Jason", "grade all", "cotlin", "rejects", "she's a cool", "note book"), because
  nothing around them looks wrong.
- **A sent sentence can still be rewritten too freely.** E-light wrote "Can you make sure the
  widget doesn't refresh after midnight?" for the restart "Can you okay so the widget doesn't
  refresh after midnight." (the guard let "make sure" through), and in `long-weekend` it moved
  "the cooler and the drinks" to Sarah's sentence while resolving "Actually no, Tom is bringing
  the food".
- **At Light, list sentences cost a call for little.** `list-counted` and `list-one-two` were
  sent for their items and came back word for word; `list-enumerate` got its colon and commas.
  At Polished the whole part is sent and the items come back one per line.

### The anchor's time, back to back (measured)

Run 3, 14:37–14:40, warm model, one request at a time, the phone's load average 3.7–5.5 (it was
not idle):

| Variant | Totals | TTFT | Output tokens | Calls |
|---|---|---|---|---|
| A-light | 16.0 / 16.0 / 17.7 s | 1.0–1.2 s | 324 | 1 |
| A-polished | 15.8 / 15.5 / 17.7 s | 1.1 s | 322 | 1 |
| E-light | 15.5 / 14.4 / 16.5 s | 0.9–1.0 s | 261 | 3 |
| E-polished | 14.8 / 15.4 / 16.7 s | 1.1–1.2 s | 301 | 1 |

- **Decode is steady at about 20–22 tokens/s.** Every anchor total is TTFT plus its output tokens
  at that rate. At that rate a pass that rewrites all 271 words cannot finish in 7 s: it needs
  about 320 output tokens, about 15 s.
- **The anchor gains little from the gate.** Most of it is flagged (run-ons, stray capitals, a
  spoken list), so E-light still sends 3 groups and writes 261 tokens; Polished sends it whole.
  In the app, the early passes split a dictation this long into parts while the user speaks, so
  only the last part is waited for.
- **A queue doubles the time.** A first attempt at run 3 started two runners by mistake, so two
  requests shared TAI's serial chat lane: TTFT rose to 13–19 s and the anchor took 29.7–35.5 s,
  while the decode rate stayed at about 20 tokens/s. A cleanup that waits behind another
  generation (a chat answer, another client) takes twice as long, which fits readings of 15 s and
  more. Those runs were discarded.
- **Inference.** The "~7 s" figure is most likely the wait after the user stops in the app, where
  the early passes have already cleaned all but the last part, not a whole-anchor pass. That is
  not measured here.

### Still owed

- A real dictation with fillers and one non-English dictation on pong, through the app (listed
  in `project-docs/device-checks.md`).
- A non-English case with a flagged sentence, so the language line is measured through E.
- E built from the app's real pause-time parts, and the wait after the user stops measured in
  the app's own log (`cleanup session: cleanupMs=`).
- Recogniser word confidence as a gate sign, for the mishearings.
