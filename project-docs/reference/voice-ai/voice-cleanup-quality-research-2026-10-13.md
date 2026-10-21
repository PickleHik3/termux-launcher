# Dictation cleanup quality: research (2026-10-13)

Desk research on how to raise the quality of the on-device cleanup pass (Gemma 4 E2B int4 on LiteRT-LM, GPU, one pass over the dictation, `VoicePolishRules`). No code was changed, built or run, and no device was touched. It builds on `voice-cleanup-context-rework-2026-10-13.md`, `voice-cleanup-benchmark-2026-09-27.md`, `daytoday-settings-bench-2026-10-05.md` and `freestyle-voice-comparison.md`, and does not repeat what they establish (prompt rules vs a worked example vs few-shot turns on E2B, the guard, the model bake-off, E4B and Qwen/LFM/Granite/Ministral verdicts).

**Labels.** **[source]** is stated by the document that owns it (model card, docs, source code, paper). **[3rd]** is a secondary write-up only; treat as unverified. **[inference]** is my reasoning from sources plus this repo's code; nobody has measured it. Where a result comes from a model much larger than E2B, the entry says so, because that is the main way this literature misleads.

## Executive summary

The developer's on-phone failures fall in two classes: **(a) recogniser errors the model has to guess at** ("on the pinned I control it opens the app", "the third, and probably the most work" → "What most work I'm on"), and **(b) cleanup it simply did not do** (an "uh" left in). Class (b) is a prompt or decoding issue. Class (a) is the larger one and is mostly an information problem: the LLM sees only the 1-best text. The literature's consistent answer is to give it more evidence about what the recogniser was unsure of, and to stop it from rewriting what the recogniser was sure of.

Top 5, ranked by expected quality gain per effort. A prerequisite comes first because every item below needs it.

**Prerequisite: a fixed on-phone eval (effort S–M, section 8).** Without it, every item is judged by eye on one anchor case, and the 2026-10-13 host result already moved on the phone. Do this first.

| # | Recommendation | Evidence | Effort |
|---|---|---|---|
| 1 | **Expose per-word confidence from our own Parakeet/Whisper decoders and put it in the prompt** (mark low-confidence words, e.g. `pinned[?] I[?] control[?]`), and gate: send fully confident short phrases through untouched. | Confidence-in-prompt beat naive LLM correction on Parakeet output; naive correction nearly doubled WER on one set through over-correction, and confidence gating avoided it (arXiv 2509.25048 [source], 8B model, LoRA-tuned). Gating by lowest-word confidence kept most of the gain while sending 53–87 % of utterances (arXiv 2407.21414 [source], GPT-3.5/4). Our greedy loops already hold the logits (`ParakeetTdtDecoder.java:99-103`). | M |
| 2 | **Give the model the vocabulary it should expect**: the user dictionary, plus names and terms visible in the terminal or app on screen, as a short `Terms:` block in the user turn. | Wispr's Context Awareness uses on-screen names to fix proper nouns [source: docs.wisprflow.ai]; Apple's entity-retrieval correction cut rare-entity WER by 33–39 % [source, 7B, fine-tuned], but in-context prompting alone "resulted in large WER regressions" there. Freestyle does the same for the recogniser (`freestyle-voice-comparison.md`). | S–M |
| 3 | **Decide the chunk plan for pause-time cleaning on evidence**: clean ~4-sentence units, give prior text as read-only context, keep it short, and keep a cheap final pass or accept the loss of look-ahead. | Segments of about four sentences improved disfluency removal across models (DRES, arXiv 2509.20321 [source]). Feeding earlier *ASR* utterances as context raised WER in every condition (arXiv 2505.16212 [source], 1B and 8B). Batching several sentences cut over-correction (arXiv 2609.10810 [source], Gemini 3.1 Pro). | M |
| 4 | **Sampling A/B on the phone** (temperature 0 vs Google's T=1.0 / top_p 0.95 / top_k 64, and a light repetition penalty). Google recommends the latter for all uses; we ship T=0 for cleanup. | Gemma 4 card: "standardized sampling configuration across all use cases" is T=1.0, top-p 0.95, top-k 64 [source]. Likelihood-maximising decoding gives repetitive text (arXiv 1904.09751 [source]). No source tests cleanup faithfulness at T=0 vs 1.0, so this is a measurement, not a known win. | S |
| 5 | **LoRA-tune E2B on cleanup pairs** and ship it as a runtime sidecar or a merged `.litertlm`. | Fine-tuning lifted disfluency-removal F1 from the high 60s to above 90 for a 3B Llama (DRES [source]); task adapters are how Apple specialises its 3B model [source]. But on LiteRT-LM a Gemma 4 E2B sidecar "loads but has no effect" today (issue #3173 [source]), and the public export path is text-only and larger than the official build (issue #2497 comment [3rd]). | L |

Not in the top five, with reasons (sections 3, 6): an edit-list or JSON "edits" output (no source compares it with a rewrite for a small LLM; one real use is making the guard structural), a two-pass draft-and-verify scheme (self-correction without external feedback is unreliable and doubles latency), N-best/beam from the recogniser (Whisper's N-best has "very limited diversity"; NeMo exposes n-best only through beam decoders we do not run), and a different model (no primary source ranks a 2–4B model above Gemma 4 E2B on this task).

**Questions I could not answer from primary sources** are listed in section 9.

---

## 1. ASR error correction with LLMs

### Established by a source

| Finding | Source | Model size | Note |
|---|---|---|---|
| Generative correction from N-best lists (HyPoradise): 334k hypothesis/transcript pairs; the LLM predicts the transcript from the N-best list and "can recover tokens missing from the N-best list", beyond the reranking upper bound. | arXiv 2309.15701 | LLaMA-13B + LoRA, T5-large, GPT-3.5 | |
| LoRA-tuned LLaMA-13B cut WER 10–80 % relative on eight sets (WSJ 4.5 → 2.2, ATIS 8.3 → 1.7, CHiME-4 11.1 → 6.6, Switchboard 15.7 → 14.1, CORAAL 21.4 → 19.2). Gains were smallest on conversational and accented speech (Switchboard 10 %, CORAAL 10 %). | same, Table 2 | 13B | Conversational speech, our case, is where the gain shrank. |
| In-context only: GPT-3.5 zero-shot gave 3.5–6.4 % relative; 10 in-domain demonstrations gave 15–21 %. Zero-shot LLaMA-13B did not improve CHiME-4 (11.5 ± 0.5 vs 11.1) or CV-accent. | same, Table 3 and text | 175B / 13B | In-context gain depends on a large model and in-domain demonstrations. |
| Whisper's N-best lists show "very limited diversity"; HyPoradise kept the top 5 of a 60-beam search. | same | | Whisper-style decoders add little from N-best. |
| Prompted correction on Whisper output (LibriSpeech): GPT-3.5 cut WER 21.9 % (Tiny), 15.0 % (Medium) but *raised* it 7.4 % on Large V3; on Tiny it improved 26–30 % of utterances and worsened 5–6 %. Gating on lowest-word confidence 0.7 sent 53–94 % of utterances to the LLM; GPT-4 beat GPT-3.5 only on Tiny. | arXiv 2407.21414 | GPT-3.5 / GPT-4 | Cleaner ASR output gets hurt. |
| Confidence in the prompt (`HOW[1.00] MANY[0.85] RAFELLES[0.61]`) on **Parakeet** output, LLaMA 3.1 8B LoRA-tuned: TORGO 20.01 (naive LLM) → 10.58 (confidence prompt) vs 10.83 uncorrected; SAP-unshared 10.56 → 9.48 vs 9.94 uncorrected; on SAP-shared it was slightly worse than naive (4.95 vs 4.69). The authors attribute naive correction doubling TORGO's WER to over-correction. | arXiv 2509.25048 | 8B + LoRA | Disordered speech; thresholds and entropy settings needed per-dataset tuning. |
| Zero-shot LLM correction by size (child speech, Whisper): LLaMA 3.2-1B gave small or negative changes (WSP-S 46.67 → 47.19), LLaMA 3.1-8B helped (→ 43.96). Previous 1 or 3 *recognised* utterances as context raised WER in every condition (e.g. 32.92 → 33.02 → 37.87 fine-tuned); authors blame error propagation. They also revert any correction more than three words longer than the input. | arXiv 2505.16212 | 1B, 8B | Direct evidence on small size and on prior-context risk. |
| Whole-text correction over-rephrases; correcting segment by segment (1–5 sentences) in a chat, earlier corrected segments as context, plus a correction-rate threshold, gave 10–44 % relative error-rate reductions. A threshold that was too permissive caused over-rephrasing and lower scores. | arXiv 2504.01519 | 7B, full fine-tune, Mandarin | |
| Entity-retrieval correction: retrieve rare entities by acoustic-neighbour embedding, pass to a tuned LLM; WER −32.6 to −39.0 % on rare-entity sets. LoRA ranks 4–32 and full fine-tuning gave similar WER; "in-context learning resulted in large WER regressions"; more than one retrieved candidate per query regressed. | arXiv 2409.06062 | 7B | Apple authors. |
| Phonetic-edit-distance retrieval plus LLM reasoning cut named-entity CER 18–34 %. | arXiv 2602.12287 | not read in full | Abstract-level only. |
| Entropy-based word confidence is "up to 2 and 4 times better" than max-probability at flagging wrong words, for CTC and RNN-T. | arXiv 2212.08703 | | The paper does not cover TDT. |
| Whisper (the model's own paper) already treats average log-probability below −1 and gzip ratio above 2.4 as decode failures, and combines the no-speech probability (0.6) with the log-probability. | arXiv 2212.04356 §4.5 | | Confidence signals ship in Whisper's reference decoder. |

### Can Parakeet TDT or Whisper give N-best or confidences?

- **Parakeet TDT, N-best.** NeMo's beam decoders return a hypothesis list only when `return_best_hypothesis=False` (NeMo's `eval_beamsearch_ngram*.py` scripts) [3rd, via search snippet; I did not read the NeMo decoding docs for the TDT strategy]. The Parakeet v3 card mentions neither N-best nor confidence [source]. **Our runtime is not NeMo**: it runs Google's LiteRT conversion of the graph with a home-grown greedy TDT loop (`ParakeetTdtDecoder.java`), so N-best would mean writing a beam search over the joint network (our decision, not a flag).
- **Parakeet TDT, confidences.** Not in the card or the pages I could open. The loop already reads the full logits at each step and takes `argmax` over the vocabulary and over the durations (`ParakeetTdtDecoder.java:99-123`). [inference] A softmax over those logits gives per-token probability and entropy at near-zero cost, and the entropy measures in arXiv 2212.08703 apply to transducers (the paper tests CTC and RNN-T, not TDT; the TDT duration head is an extra softmax). arXiv 2509.25048 used exactly this idea on Parakeet through NeMo, with Tsallis entropy and min/mean/product aggregation, and says the settings need per-dataset tuning.
- **Whisper ACFT.** Our `WhisperDecoder` also holds the logits per step (`WhisperDecoder.java:138-140`), so token probabilities and the average log-probability are available [inference]. arXiv 2407.21414 used `whisper-timestamped` confidences the same way. Whisper's N-best has limited diversity (HyPoradise).

### Does feeding them help? (our inference)

For the small model: the strongest evidence is for confidence as a **gate and a pointer** (do not touch confident words; look here), not as extra N-best candidates. It matches both failure classes: "the third, and probably the most work" is a place where a transducer's per-word confidence is likely to dip [inference, not measured]; and an "uh" is not a confidence problem at all (see section 2). The 8B result used LoRA tuning on the marked-up format, and no source tests the marked-up format zero-shot on a 2B model. A prompt-only trial is cheap, so run it before committing to tuning (section 8). Marking only the lowest few words, not every word, keeps tokens down (E2B's window is 2048).

---

## 2. Prompting small instruction models for cleanup

### Established

- **System role.** Gemma 4 has "native support for the `system` role", and thinking is enabled by putting `<|think|>` in the system turn; remove it to disable [source: Gemma 4 model card; ai.google.dev prompt-formatting-gemma4]. The Gemma 1–3 guide says the opposite ("only two roles: user and model… provide system-level instructions directly within the initial user prompt") [source: ai.google.dev prompt-structure, legacy]. Our system turn is therefore correct for Gemma 4, and a different model family may need the instructions moved into the user turn. The 2026-09-27 benchmark's thinking-on bug (the string "false" reaching the template) is the same switch.
- **Recommended sampling.** Gemma 4: "standardized sampling configuration across all use cases": temperature 1.0, top-p 0.95, top-k 64 [source: model card]. The Gemma 3n card gives no sampling advice and its example decodes greedily (`do_sample=False`) [source]. The app's default is already T=1.0, top-k 64 (`LiteRtTaiRuntime.java:48-50`); cleanup overrides it to 0.
- **Few-shot is fragile.** The order of few-shot examples swings accuracy from near state of the art to near chance, at every model size, and a good order does not transfer between models (arXiv 2104.08786). Apple's rare-entity paper: in-context prompting "resulted in large WER regressions" on a 7B model while a LoRA at rank 4 worked (arXiv 2409.06062). The HyPoradise in-context gains need a 175B model and in-domain demonstrations. This is consistent with our own 2026-10-13 result (a worked example and few-shot turns changed nothing on E2B).
- **Length.** Reasoning accuracy degrades at input lengths far below the technical maximum even with irrelevant padding (arXiv 2402.14848); information in the middle of a long input is used less than at the start or end (arXiv 2307.03172). Both used much larger models than E2B and neither tests rewriting; they say nothing about the exact break-even for a 360-token system prompt.
- **Whisper's own prompt follows style, not instructions**, uses only the last 224 tokens and is "unreliable": a list of names fixed spellings ("Amy and Sean" → "Aimee, Shawn") [source: OpenAI cookbook]. That matters for item 2: vocabulary can bias the recogniser (our `biasPromptFor`, capped at 24 tokens) as well as the cleanup model.
- **Prompted LLMs over-correct.** Zero- and few-shot LLMs "systematically overcorrect" in minimal-edit correction, which is why the field weights precision (arXiv 2506.13148 abstract, quoted by the 2026 batching paper; arXiv 2609.10810). Instruction-tuned fluency pressure is the cause given in the literature; our "never add" rules and the guard are the same remedy.
- **Disfluency removal by prompting.** In the DRES benchmark (Switchboard text, Qwen3 0.6B–8B, Phi-4-mini, Llama 3.x, GPT-4o) small models sit in regimes of over- or under-deletion, reasoning variants over-delete, and the paper recommends under-deleting small models and Qwen3-1.7B for edge use (arXiv 2509.20321). **I could not extract the F1 values behind those statements.** Models handled repairs ("Monday, no, Tuesday") better than interjections and parentheticals.

### Our inference for the prompt

- Class (b) misses such as the left-in "uh" look like a prompt priority problem: the prompt asks for ten things and the filler rule sits mid-paragraph ("Remove fillers, stutters and repetitions"). A deterministic filler pass before or after the model (`uh`, `um`, `er`, `hmm` as standalone tokens; the guard already lists them in `FILLERS`) is cheaper than prompting and cannot be forgotten. Unlike words like "like" or "so", the true vocalised fillers are safe to strip with a regex [inference].
- Spoken-language examples inside the system prompt (`'Monday, no, Tuesday'`) are the one place few-shot style is already working (selfcorrect scores in the 2026-10-13 table). Keep them; do not add more without an eval, given the ordering fragility above.
- Put the highest-priority constraint (keep every negation, never add) last in the system turn, where recency helps [inference from arXiv 2307.03172's start/end effect; not tested on rewriting].

---

## 3. Decoding

### Established

- Likelihood-based decoding (greedy, beam) yields "bland and strangely repetitive" text, and "the decoding strategy alone can greatly affect output quality" (arXiv 1904.09751). This concerns open-ended generation; cleanup is a copy-with-edits task where the opposite risk (drift from the input) dominates, and no source tests T=0 vs T=1.0 for it.
- Gemma 4's own recommendation is T=1.0/top-p 0.95/top-k 64 "across all use cases" (model card). Papers that report their setting for ASR correction use low values (arXiv 2505.16212: temperature 0.2).
- Whisper's reference decoder: beam 5, temperature fallback 0 → 1.0 in 0.2 steps when the average log-probability < −1 or the gzip ratio > 2.4 (arXiv 2212.04356 §4.5). Freestyle's audit says beam-5 buys at most ~1.5 pp WER at 2×+ cost and "greedy hallucinates less" (`freestyle-voice-comparison.md`, source: Freestyle repo).
- **LiteRT-LM 0.18.0 exposes** `SamplerConfig(topK, topP, temperature, seed)`, a `RepetitionPenaltyConfig` (multiplicative, presence, frequency, window) and `ResponseFormat` constrained decoding by JSON schema or regex (`enableResponseFormat` "initializes the constraint provider LLGuidance") [source: LiteRT-LM Kotlin sources `Config.kt`, `ResponseFormat.kt` on `main`; the app pins 0.18.0 (`app/build.gradle:12`) and I did not check that these exact classes are in that tag]. No beam search in the public API [inference: none found].
- **Speculative decoding.** Google recommends MTP "universally" on GPU backends (up to 2.2× decode) and for rewrite or summarise tasks, but warns E2B on CPU may slow slightly [source: developers.google.com/edge/litert-lm/models/gemma-4]. Latency headroom is what pays for any two-pass scheme.
- **Self-check schemes.** Chain-of-Verification reduces hallucination in a draft → plan checks → answer separately → revise loop, on large models and QA-style tasks (arXiv 2309.11495). Without external feedback, LLMs "struggle to self-correct their responses", sometimes worsening them (arXiv 2310.01798). In correction work, the check that helped had a rule (error-rate threshold, arXiv 2504.01519) or extra data (confidence, retrieval), not the model re-reading itself.
- **Edits versus rewrite.** Seq2Edits: inference cost scales with the number of edits and speeds up grammar correction "up to 5.2×" at competitive accuracy (arXiv 2009.11136, trained tagging model, not a chat LLM). For prompted LLMs the literature describes over-correction and offers batching, edit-level voting and a small post-corrector (arXiv 2609.10810, 2509.20811 via search snippet). **I found no paper comparing an "edits list" with a full rewrite for a 2–4B instruction model.**

### Our inference

- Test T=1.0/top-p 0.95/top-k 64 and a low value (0.2) against 0 on the eval before changing anything. The expected direction is unknown. The int4 LiteRT build can have a different sensitivity from the host's Q4_0 GGUF (noted in the 2026-10-13 record), so the host's T=0 results do not transfer automatically.
- A repetition penalty is risky for a copy task (every correctly kept word is a repeat); at most use `windowSize` small and a value just above 1.0, and only if the eval shows loops.
- A JSON "edits" format (`[{"from": "...", "to": "..."}]`) has one clear benefit independent of the unknown quality question: every `from` must be a verbatim substring of the transcript, so a regex or schema constraint can reject invention structurally, and the guard shrinks to "apply only edits whose `from` is present". Its costs: the model must locate spans exactly (E2B copying quality is unmeasured), it handles "rebuild this garbled clause" less naturally than a rewrite, and the grammar-constrained path is untested on the GPU backend here. Treat as an experiment after items 1–3.
- A draft-and-verify second pass roughly doubles decode time (E2B GPU ~17 tok/s in this repo's measurement, 52 tok/s on a S26 Ultra per Google), and the sources do not support a model judging itself. A **rule-based verify** (the existing kept/new-share guard, applied per sentence instead of per passage, and a negation count) is free and has support from the correction-rate threshold result.

---

## 4. Chunking and context (the planned pause-time cleaning)

### Established

- DRES: splitting transcripts into segments of about four sentences "improves structural fidelity across models, including long-context ones" (arXiv 2509.20321; the paper's ΔF values are in a figure I could not read).
- Chain of Correction: segment-wise chat with earlier corrected segments and the whole pre-recognised text as context; the first user turn gives the full text, and a correction-rate threshold filters rewrites (arXiv 2504.01519; 7B model; Mandarin).
- Minimal-edit GEC: batching several sentences in one prompt lowers the edit rate across model families and acts "as a targeted regularizer against overcorrection" (arXiv 2609.10810; the authors' explanation, attention dilution, is a hypothesis).
- Prior ASR output as context hurt (arXiv 2505.16212): with 1 utterance 32.92 → 33.02 WER; with 3, → 37.87 (fine-tuned 8B); the authors blame error propagation from earlier mis-recognitions.
- Structured, non-transcript context helped: dialogue-state context improved correction recall/F1 by 34 %/16 % (arXiv 2501.06129 via search snippet, not read in full).
- Wispr's Android client "reads the surrounding text in your input field before transcribing" to continue sentences and match tone; on desktop, text immediately around the cursor is read on device; context is not described as a prior-cleanup window [3rd, docs.wisprflow.ai via search; first-party help pages but not read in full].
- Vendor window sizes: no first-party source states a dictation cleanup window. ML Kit's Proofreading accepts "less than 256 tokens" per call [source: developers.google.com ML Kit GenAI Proofreading].

### Our inference for the planned change

1. **What the new design loses.** The 2026-10-13 prompt says "Read the whole transcript first … using the rest of the transcript as context". Repairing "pinned I control it opens the app" needed the later sentences about the app drawer. Cleaning each pause-segment with only earlier text removes that look-ahead, so garbles resolvable only by what comes *after* them will survive. Expect the pause-time pass to do best on fillers, run-ons and local mishearings, and worst on exactly the anchor's garbles.
2. **Earlier context should be the already cleaned text** (as planned), not raw ASR. This avoids the error-propagation mechanism in arXiv 2505.16212, but does not remove it: a wrong rewrite in segment 1 now steers segment 2. Keep the context to one to three sentences, and mark it read-only and not to be repeated.
3. **Cheap compromise.** Pause-time passes give an early, mostly-clean text; one final pass on the whole passage runs only on sentences the guard or the confidence marks flagged (item 1), so the extra latency is bounded and look-ahead returns where it matters. This is a design choice to test, not a sourced result.
4. **Unit size.** Four sentences (DRES) is about 60–100 words, which also fits comfortably in the 2048 window with prompt and budget. A single short phrase gets no repair benefit from context and gets the over-correction risk of arXiv 2609.10810; the MIN_WORDS gate already skips short ones.
5. **Over-rephrasing guard per chunk.** The existing share guard works per chunk; arXiv 2504.01519's best threshold (0.3–0.4 error rate between original and corrected) is of the same kind as `MIN_KEPT_SHARE = 0.4`.

---

## 5. Fine-tuning

### Is it feasible within LiteRT-LM? (established, with gaps)

- **Runtime LoRA exists in the engine.** LiteRT-LM has `LoraManager`/`LoRA` components (`runtime/components/lora_manager.h`), a Kotlin/Python `LoraConfig`, and sidecar adapters as `.tflite` files [source: LiteRT-LM repo]. The public docs do not mention LoRA [source: developers.google.com/edge/litert-lm/overview, its Kotlin getting-started guide].
- **Gemma 4 E2B sidecar: unresolved.** Issue #3173 (open, 2026-09-02): a rank-16 PEFT adapter converted with `litert-torch` (`LoRA.from_safetensors().to_tflite()`, KV-shared layers zero-padded) loads on `gemma-4-E2B-it-litert-lm` "although the sidecar loads without any errors, it appears to have no effect on inference". A commenter (v0.16.0) saw the adapter take effect only on a bundle built with LoRA signatures (`test_lm_lora.litertlm`) and guesses the official E2B bundle has none [source for the issue; the diagnosis is a third party's]. Issue #1188 (open) requests a public API for adapter weights in sessions; a community PR (#2508) wires LoRA into the session path, not merged as of the data I read.
- **Merge-and-convert path.** Google's tutorial converts a fine-tuned Hugging Face checkpoint with `litert-torch export_hf … --externalize_embedder`, runs it with `litert-lm run`, and checks parity with `ai_edge_eval`; its example is Gemma 3 270M, and it says nothing on LoRA merging, supported architectures or quantisation [source: developers.google.com/edge/litert-lm/tutorials/convert-and-run]. A commenter on issue #2497 reports that `export_hf` of the Gemma 4 E4B QAT checkpoint yields a working text-only artifact of 4.0 GB against the official 3.66 GB multimodal build, and that fine-tune + re-quantise garbled output on the Pixel 10 NPU because the static-activation quantiser is Google-internal [3rd, GitHub comments]. For the Adreno GPU path, nothing published says a custom-converted Gemma 4 E2B matches the official build's speed or quality.
- **Other precedent.** Apple trains rank-16/32 task adapters for its ~3B model and swaps them at run time ("10s of megabytes") [source: machinelearning.apple.com 2024 post; developer.apple.com adapter training]. ML Kit's Proofreading applies LoRA adapters to Gemini Nano "to reduce differences across devices" and accepts keyboard or voice input types [source: developers.google.com ML Kit]. So both platform vendors specialise their on-device model for proofreading with adapters rather than prompts.

### Data and expected gains

| Source | What it is | Use for us |
|---|---|---|
| Switchboard Treebank-3 disfluency annotations (LDC; EDITED/INTJ/PRN nodes; Charniak–Johnson split sw[23] train, sw4[5-9] dev, sw4[0-1] test) | Human-annotated conversational transcripts, 1,126 conversations [3rd, via arXiv 2104.10769 and 2004.05323 summaries]. Licensed (LDC). | Disfluency targets for removal; text is conversational, not dictation. |
| DRES (arXiv 2509.20321) | A text-level disfluency-removal benchmark built from Switchboard; code/benchmark released with the paper. | Eval and a recipe. I did not check its licence. |
| LARD (arXiv 2201.05041, LREC 2022) | Synthetic disfluency injection (repetition, replacement, restart) into fluent text, with a released dataset. | Cheap training/eval pairs: inject into dictation-like text. |
| Disfl-QA (arXiv 2106.04016) | SQuAD questions with human-added disfluencies (QA task). | Weak fit; questions only. |
| Hypothesis → transcript pairs (HyPoradise, arXiv 2309.15701; 334k) | N-best lists and references over eight corpora. | Mishearing repair, but the hypotheses come from other ASRs. |
| **Our own recogniser's mistakes** | Parakeet/Whisper output on the developer's audio, with a hand-corrected reference. | [inference] The only data matching our error distribution; small but exact. |

- **Reported gains.** DRES fine-tuning: E_F from the mid-70s to the mid-90s (GPT-4o-mini) and from the high 60s to above 90 (Llama 3B), with a +22 to +23 ΔDRES; but the authors say fine-tuning "hurt generalization" (small drops on MMLU/GSM8K/CoQA). On ASR-hypothesis correction, LoRA-tuned LLaMA-13B beat the N-best oracle on two sets (HyPoradise), a 7B LLM with LoRA rank 4 matched full fine-tuning (arXiv 2409.06062), and LoRA 8B needed confidence prompts to avoid over-correction on atypical speech (arXiv 2509.25048). An older IBM result traces over-correction to noisy training pairs and trains the model to make no change when the target is not clearly better (IBM, "Robust ASR Error Correction with Conservative Data Filtering" [3rd, via search; not read]).
- **Synthetic data.** Apple's summarisation adapters were trained on "synthetic summaries generated from bigger server models", filtered for quality [source]. [inference] The same recipe fits us: take a larger model (E4B on the host, a remote model) to produce reference cleanups of real or synthetic dictations, filter with the guard plus the eval's metrics, then tune E2B. The reference quality caps the student; the 2026-10-13 record says even E4B misses the anchor's garbles.
- **Expected gain on E2B: unknown.** No source fine-tunes a Gemma E2B-class model for this task. The closest sizes (Llama 3B on disfluency; LLaMA 1B/8B on ASR text) show large in-domain gains and a generalisation cost.

### Verdict (inference)

Feasible only through a merged-weights `.litertlm` (the documented conversion path, with unknown Gemma 4 E2B GPU quality and size) or through a sidecar, which is blocked until the E2B bundle is confirmed LoRA-capable. Effort is high and the gain is unmeasured for our model. It is a sensible later step once the eval exists and items 1–3 have been tried, because the same data (items in section 8) trains and tests.

---

## 6. Model choice

Within ~2–3 GB and GPU, **no primary source I found shows a model clearly better than Gemma 4 E2B for dictation cleanup.**

- Google's numbers for E2B: 2.3B effective parameters (5.1B with embeddings), MMLU Pro 60.0 %; supports audio input (30 s limit) [source: Gemma 4 card]. On a S26 Ultra GPU: 52 tok/s decode, 0.3 s TTFT; E4B 22 tok/s, 0.8 s [source: LiteRT-LM Gemma 4 page]. LiteRT-LM supports E2B and E4B "today", larger models "coming soon" (same page).
- Published disfluency-removal evidence: DRES recommends small under-deleting models for edge use and Qwen3-1.7B specifically; reasoning-oriented models over-delete (arXiv 2509.20321). Gemma was not in the model list I read (GPT-4o family, Llama 3.x, MobileLLM, Qwen3, Phi-4-mini). This repo found the Qwen3.5 LiteRT builds do not load on the GPU and summarise or drop text (`voice-cleanup-benchmark-2026-09-27.md`), so a Qwen3-1.7B option is a different model from the one measured there and not available in a LiteRT GPU build that I verified.
- Size effects in ASR correction: LLaMA 3.2-1B was weak, 8B helped (arXiv 2505.16212). Gains beyond 8B are not seen on already-clean recognisers (GPT-4 over GPT-3.5 only helped the weakest recogniser, arXiv 2407.21414).
- Gemma-native audio ASR on E2B exists ("ASR … listed as capabilities", CoVoST 33.47, FLEURS 0.09 [source: card]) but is a different pipeline (an audio model replacing Parakeet), outside this question.

**Inference.** A bigger win than a different 2B model is lowering the entropy of the task (items 1–3), or specialising E2B (item 5). E4B is a quality step with a measured 3× latency (our benchmark) that Google's own numbers confirm (22 vs 52 tok/s).

---

## 7. What production apps say they do

| Product | What the first-party material says | What it does not say |
|---|---|---|
| **Wispr Flow** | Backtrack "removes filler words, false starts, and self-corrections" and "uses your full dictation to decide what to change"; Smart Formatting builds lists and punctuation; Context Awareness reads the active app and adapts "accuracy, style, and formatting", uses on-screen names such as email recipients for proper nouns; Dictionary words are prioritised in transcription; Android reads the surrounding text before transcribing; desktop sends the app name and website domain per dictation, and reads text near the cursor only on device [source: docs.wisprflow.ai help articles]. Model: "Llama … as the base for a real-time cleanup step … fine-tuned … on user context and preferences", p90/p99 latency, "<700 ms end-to-end" [3rd: Baseten customer page, vendor marketing]. | The actual models, prompts, whether cleanup is on device (Baseten page says dedicated cloud inference). Wispr is cloud. |
| **Freestyle** | Local whisper.cpp or cloud ASR, optional LLM cleanup with tones and per-app formats, a vocabulary turned into a `Terms:` prompt (900-char budget), a post-ASR dictionary with exact replacements; filler-only output skips the LLM; transcript wrapped against prompt injection [source: Freestyle repo, as read in `freestyle-voice-comparison.md`]. | Its audit marks vocabulary bias for local whisper as fixed while the code at the read commit has no such case (same file). |
| **Apple** | On-device ~3B model with task LoRA adapters swapped at run time (rank 16, 10s of MB), adapters trained on synthetic data from larger server models; adapter training toolkit (rank 32) for developers [source: machinelearning.apple.com 2024 post, developer.apple.com adapter page, 2025 tech report snippet]. | Which adapter serves Writing Tools or dictation cleanup, its data and its quality numbers; "no adapter focused on composition". I could not open the SpeechAnalyzer/SpeechTranscriber documentation body, so Apple's dictation custom-vocabulary mechanism is unverified. |
| **Google, Gemini Nano** | ML Kit GenAI Proofreading: grammar and spelling on short text (< 256 tokens), keyboard or voice input type ("voice input is more prone to words that sound alike being misspelled"), Gemini Nano via AICore with LoRA adapters; beta [source: developers.google.com ML Kit]. Pixel Recorder summarises on device with Gemini Nano [3rd: 9to5Google, DeepMind page]. Gboard Writing Tools run on Gemini Nano [3rd: Android Authority / Headlines; I found no first-party page]. | Whether Gboard voice typing runs an LLM cleanup after the recogniser. Gboard's 2019 on-device recogniser is an end-to-end RNN-T (secondary reports). "Help me write": nothing primary found. |

[inference] The common production pattern is **not** a bigger generic prompt. It is (i) per-task adapters or fine-tuned models, (ii) context from the app or screen, (iii) a user dictionary, and (iv) a narrow task (proofreading short spans, backtracking) rather than "rewrite the passage".

---

## 8. Evaluation

### Metrics and their traps

- **WER against a clean reference** (after lowercasing and stripping punctuation, as Parakeet's card does when it reports WER [source: card]) measures repairs but not formatting; it can disagree with CER and with meaning (WER 57.14 → 28.57 while CER rose in one example, arXiv 2407.21414). Report **word-level delta against the 1-best raw text** too: the share of utterances improved / worsened / unchanged (the metric arXiv 2407.21414 used: 26–30 % improved, 5–6 % worsened on Tiny).
- **Disfluency removal.** Word-level precision, recall and F1 against a gold deletion mask, counting over-deletions and under-deletions separately (DRES). For Switchboard the standard target is EDITED-node F1 [3rd]. For dictation we need a small gold mask for fillers, repetitions and abandoned starts.
- **Meaning preservation.** SemDist: the distance between reference and hypothesis in a sentence-embedding space (RoBERTa in the paper), argued to track downstream behaviour better than WER (arXiv 2104.02138; the abstract gives no correlations). On the host only; the phone does not need an embedding model.
- **Task-specific deterministic checks** (cheap and the most useful for a small-model rewriter): negation count preserved; protected terms and numbers exact; no new content words (`newShare`); content-word retention (`keptShare`); standalone fillers remaining; wrapped in a refusal or answer shape; copies a prompt instruction.
- **LLM-as-judge pitfalls.** Position bias, verbosity bias, self-enhancement bias and limited reasoning; strong judges reach about 80 % agreement with human preference on chat tasks (arXiv 2306.05685, MT-bench). [inference] A rewriter that "improves" fluency will be favoured by a verbosity-biased judge even when it invents content, which is the failure we care about. Use a judge only for pairwise ties with both orders, never as the headline number, and never with E2B or E4B judging themselves.

### A small eval set we can run (proposal, inference)

About 60 cases, all with a hand-written reference and tagged by what they test.

| Group | N | Source | Tests |
|---|---|---|---|
| Real dictation | 12 | The developer's own sessions as Parakeet heard them (the five 2026-09-26 sessions, the anchor), cut to 40–120 words, hand-corrected by the developer | The real failure class |
| Pause-sized chunks | 12 | The same sessions cut at VAD pauses, with and without 1–3 preceding cleaned sentences | The planned pause-time pass (section 4) |
| Synthetic disfluency | 12 | Fluent dictation-style text with LARD-style repetitions, restarts and replacements injected, then spoken by TTS and run through Parakeet | Filler and repair recall with realistic mishearing |
| Self-correction and numbers | 8 | The 11-case set's `selfcorrect`, `numbers`, plus "no wait" variants | Rule-checked outputs |
| Terms and entities | 8 | Dictations with names, paths, flags and app terms, some in the user dictionary | Item 2 |
| Guard cases | 8 | `question`, `inject`, `short`, `shellish`, a double negative | No answer, no translation, no inversion |

All cases are text in, text out: the audio and recogniser are fixed offline, so recogniser noise cannot move the score. For item 1 each case also carries the per-word confidences from the real decoder.

**Scoring (deterministic, runs on the phone or host with no model):**
1. For each case: WER(raw, ref), WER(cleaned, ref), and Δ.
2. Counts across the set: improved / unchanged / worsened utterances (worsened is the headline risk).
3. Fillers remaining (regex list `uh`, `um`, `er`, `hmm`) and filler-removal recall on the gold mask.
4. `keptShare`, `newShare` from `VoicePolishRules` and a negation-count equality.
5. Protected-term exact-match rate and number exact-match rate.
6. Guard rejection rate (how often the raw text would be kept).
7. Latency: TTFT and total per case.
Rubric scoring by hand (0–3, as in the 2026-10-13 record) only for the 12 real-dictation cases, by the developer, since that is where the metrics are weakest.

**Running it on the phone.** The runtime already takes the request `LocalTaiVoiceTextPolisher.request` builds (system turn, `<transcript>` user turn, temperature 0, thinking off, `max_tokens`), and earlier benches drove it over the local API with a script run in Termux (`cleanup_bench.py`, retired to git history; `tai chat` and `tai benchmark` exist). The exact CLI flag for a system turn and sampler options is unverified here. A runner that reads the case file, posts each case, writes JSONL (case, variant, text, TTFT, total ms) and scores it on the host over `ssh termux` fits the existing practice. Variants are config rows (prompt id, T/top-k/top-p, confidence marks on or off, context on or off) so one run compares A/B/C. Run each variant twice (the 2026-10-13 record notes run-to-run drift even at T=0) and report the mean and the spread.

**Rules for reading results** (inference): compare variants on the same cases in the same run; require "worsened" not to rise; a gain on synthetic cases that the real-dictation cases do not show is not a gain.

---

## 9. Not answerable from primary sources

1. **Does Parakeet TDT (our LiteRT build) give usable confidences?** The mechanism is available (logits in hand); no source validates TDT confidence for error detection, and the entropy paper covers only CTC and RNN-T.
2. **Does confidence marking work zero-shot on a ~2B model?** The only Parakeet result used an 8B LoRA-tuned model.
3. **Edit list vs rewrite for a small instruction model.** No paper compares them; Seq2Edits is a tagging model.
4. **Gemma 4 E2B LoRA on LiteRT-LM.** Whether the official E2B bundle can take an adapter, and the quality and speed of a merged custom `.litertlm` on the Adreno GPU, are unresolved in the sources (issues #3173 and #2497).
5. **DRES's F1 per model** (small-model numbers); the text I could read gives only qualitative regimes.
6. **How Apple's dictation and Writing Tools clean up speech** and **how Gboard voice typing or "Help me write" do**: no first-party description found. Wispr's model claims rest on a vendor's customer page.
7. **Any published benchmark of 2–4B models at dictation cleanup** that includes Gemma 4 E2B.
8. **Window sizes used by dictation vendors for cleanup**: none stated.

---

## Sources

Models and runtime
- Gemma 4 model card (sampling, system role, benchmarks, audio): https://huggingface.co/google/gemma-4-E2B-it
- Gemma 4 prompt formatting (roles, `<|think|>`): https://ai.google.dev/gemma/docs/core/prompt-formatting-gemma4
- Gemma 1–3 prompt structure (no system role): https://ai.google.dev/gemma/docs/core/prompt-structure
- Gemma 3n E2B card: https://huggingface.co/google/gemma-3n-E2B-it
- LiteRT-LM overview: https://developers.google.com/edge/litert-lm/overview
- LiteRT-LM Gemma 4 page (speed, MTP): https://developers.google.com/edge/litert-lm/models/gemma-4
- LiteRT-LM convert-and-run tutorial: https://developers.google.com/edge/litert-lm/tutorials/convert-and-run
- LiteRT-LM file builder: https://developers.google.com/edge/litert-lm/file_builder
- LiteRT-LM source (`Config.kt`, `ResponseFormat.kt`, `runtime/components/lora_manager.h`): https://github.com/google-ai-edge/LiteRT-LM
- LiteRT-LM issues #3173, #1188, #2497: https://github.com/google-ai-edge/LiteRT-LM/issues/3173, https://github.com/google-ai-edge/LiteRT-LM/issues/1188, https://github.com/google-ai-edge/LiteRT-LM/issues/2497
- litert-torch (LoRA, `export_hf`): https://github.com/google-ai-edge/litert-torch
- Parakeet TDT 0.6B v3 card: https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3
- Whisper paper (§4.5 decoding heuristics): https://arxiv.org/abs/2212.04356 (HTML: https://arxiv.org/html/2212.04356)
- OpenAI Whisper prompting guide: https://developers.openai.com/cookbook/examples/whisper_prompting_guide

ASR error correction
- HyPoradise: https://arxiv.org/abs/2309.15701 (tables: https://arxiv.org/html/2309.15701v1)
- Confidence measures and prompting (Whisper + GPT): https://arxiv.org/abs/2407.21414
- Confidence-guided correction (Parakeet, LLaMA 3.1 8B LoRA): https://arxiv.org/abs/2509.25048
- Word-level confidence, fast entropy methods: https://arxiv.org/abs/2212.08703
- LLM correction for child conversations (small models, prior context): https://arxiv.org/abs/2505.16212
- Chain of Correction: https://arxiv.org/abs/2504.01519
- Entity retrieval + LLM correction (Apple): https://arxiv.org/abs/2409.06062
- Phonetic retrieval + reasoning: https://arxiv.org/abs/2602.12287
- Contextual handling with dialogue state: https://arxiv.org/abs/2501.06129
- Three-stage verification framework: https://arxiv.org/abs/2505.24347

Prompting, decoding, editing
- Few-shot ordering: https://arxiv.org/abs/2104.08786
- Same Task, More Tokens: https://arxiv.org/abs/2402.14848
- Lost in the Middle: https://arxiv.org/abs/2307.03172
- Neural text degeneration: https://arxiv.org/abs/1904.09751
- Seq2Edits: https://arxiv.org/abs/2009.11136
- Chain-of-Verification: https://arxiv.org/abs/2309.11495
- LLMs cannot self-correct reasoning yet: https://arxiv.org/abs/2310.01798
- Prompting LLMs for GEC: https://arxiv.org/abs/2401.07702
- Minimal-edit GEC with LLMs: https://arxiv.org/abs/2506.13148
- Larger context window, fewer overcorrections: https://arxiv.org/abs/2609.10810

Disfluency and datasets
- DRES: https://arxiv.org/abs/2509.20321
- LARD: https://arxiv.org/abs/2201.05041
- Disfl-QA: https://arxiv.org/abs/2106.04016
- Disfluency detection papers on Switchboard Treebank-3: https://arxiv.org/abs/2004.05323, https://arxiv.org/abs/2104.10769

Evaluation
- SemDist: https://arxiv.org/abs/2104.02138
- LLM-as-a-judge: https://arxiv.org/abs/2306.05685

Production
- Wispr Flow help center: https://docs.wisprflow.ai/articles/4678293671-feature-context-awareness, https://docs.wisprflow.ai/articles/5373093536-how-do-i-use-smart-formatting-and-backtrack (secondary, vendor marketing: https://www.baseten.co/resources/customers/wispr-flow/)
- Freestyle source: https://github.com/freestyle-voice/freestyle (as read in `freestyle-voice-comparison.md`)
- Apple Foundation Models 2024: https://machinelearning.apple.com/research/introducing-apple-foundation-models
- Apple 2025 tech report: https://arxiv.org/abs/2507.13575
- Apple adapter training: https://developer.apple.com/apple-intelligence/foundation-models-adapter
- ML Kit GenAI Proofreading: https://developers.google.com/ml-kit/genai/proofreading/android
- Secondary (unverified): 9to5Google on Recorder and Gemini Nano https://9to5google.com/2023/12/06/pixel-8-pro-gemini-nano/, Android Authority on Gboard Writing Tools https://www.androidauthority.com/gboard-writing-tools-othre-android-phones-3593589

Repo evidence (cited as `file:line`): `app/src/main/java/com/termux/app/terminal/inappkeyboard/voice/VoicePolishRules.java`, `app/src/main/java/com/termux/ai/ParakeetTdtDecoder.java`, `app/src/main/java/com/termux/ai/WhisperDecoder.java`, `app/src/main/java/com/termux/ai/LiteRtTaiRuntime.java`, `app/build.gradle`.
