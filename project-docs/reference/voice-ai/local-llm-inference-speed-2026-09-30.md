# Local LLM inference speed: what makes TAI faster on a phone

Research, 2026-09-30. Scope: ways to raise prefill (prompt reading) and decode (writing) speed of
small local models on an Android phone, first for the two runtimes TAI ships (MNN-LLM, bundled 3.6.1;
LiteRT-LM 0.17.1), then llama.cpp where a technique carries over. TAI has no GGUF/llama.cpp backend
(`docs/en/On_Device_AI_Backends.md`), so llama.cpp items are reference points, not settings.

Each claim carries a source tag like **[S12]**; the list is at the end. **Unverified** marks anything
not confirmed at a primary source. Numbers from papers or vendor pages name the hardware they were
measured on; none of them were measured on pong unless the text says so.

## What TAI uses today (from a brief grep, not a code review)

- **MNN** (`MnnTaiRuntime.java`): the package `config.json` is preserved; TAI fills gaps with
  `backend_type=cpu`, `thread_num=4`, `precision=low`, `memory=low`, sets `max_all_tokens` to the
  endpoint context window and `prompt_cache=true`, keeps an mmap weight cache, and forces
  `speculative_type` off unless the user switches EAGLE-3 on. Nothing sets `attention_mode`,
  `reuse_kv` or `dynamic_option`, so those come from each package's config or MNN defaults.
- **LiteRT-LM** (`LiteRtTaiRuntime.java`): GPU or CPU backend, engine token budget sized to the
  context window, `cacheDir` null (cache next to the model, per
  `gallery-gpu-loading-comparison.md`), and speculative decoding wired as opt-in when the file
  reports `hasSpeculativeDecodingSupport()`.
- **pong** (read from `/proc/cpuinfo` and `cpufreq` on 2026-09-30): Snapdragon 8+ Gen 1 (SM8475),
  Adreno 730, 12 GB. CPUs 0–3 are Cortex-A510 (max 1.80 GHz), 4–6 Cortex-A710 (2.50 GHz), 7 Cortex-X2
  (3.00 GHz). Feature flags include `asimddp` and `i8mm`; `sve` is not listed.

## Summary: ranked by likely payoff for TAI on pong

| # | Technique | Expected gain on pong | Cost | MNN | LiteRT-LM | llama.cpp |
|---|---|---|---|---|---|---|
| 1 | **Fewer bytes read per decoded token**: 4-bit weights and small or sparse-active models | Decode speed scales close to inversely with weight bytes (roughly 2.5× F16→Q4 in llama.cpp's own table) | Quality loss grows below 4 bits | yes (`--quant_bit`, HQQ/AWQ) | yes (int4 files) | yes (k-quants, IQ) |
| 2 | **Threads = the 4 big cores** (X2 + 3×A710), never all 8 | Peak decode on 8+ Gen 1 was at 4 big cores; adding little cores lowered it [S30] | None; test 3/4/5 | `thread_num` (4 already) | `num_cpu_threads` | `-t`, `-C` |
| 3 | **Reuse the KV cache across turns / shared prefixes** | Skips re-reading the system prompt and history; TTFT drops toward the cost of the new suffix | Memory held between turns | `reuse_kv`, prefix KV on disk | prefix cache in runtime | `cache_prompt`, `--prompt-cache` |
| 4 | **GPU for prefill-heavy work, CPU or GPU for decode** | Vendor table: GPU prefill 6.8× CPU but decode only 1.1× (S26 Ultra) [S7] | GPU load time, driver risk (Adreno 730 corrupt `-gpu` files) | OpenCL | GPU | OpenCL (Adreno) |
| 5 | **Speculative decoding that needs no second model** (MTP heads, n-gram / prompt lookup) | Vendor claims up to 3× (Gemma 4 MTP) [S9]; n-gram 2–4× on input-grounded tasks [S24] | Small; output distribution unchanged | `lookahead` (n-gram) | MTP (opt-in, wired) | ngram-*, draft, EAGLE-3 |
| 6 | **Right-size the context window; quantize KV only when memory binds** | Less memory and faster long-context attention; KV-int8 is close to lossless per MNN | TQ3/TQ4 hurt models under 1B | `attention_mode` 10/14 | engine token budget | `-ctk/-ctv` |
| 7 | **Account for thermal throttling** | Prime-core clock nearly halved by the 9th back-to-back run on 8 Gen 3 [S30] | Measurement discipline, not code | n/a | n/a | n/a |
| 8 | **CPU int8 decode path** (MNN `dynamic_option` 8+n, i8mm kernels) | Faster decode; i8mm mainly lifts prefill [S30] | Prefill slower for prompts under ~300 tokens | yes | internal | yes (repack) |
| 9 | **Load-time work**: mmap, weight/program caches | Faster second load, no steady-state speed change | Disk space | `use_mmap` | weight + GPU program cache | `--load-mode` |
| 10 | **NPU** (QNN/Hexagon, NNAPI) | Large prefill gains on supported SoCs [S29] | pong's SM8475 is not on any runtime's NPU list | offline QNN convert | SM8550+ only | Hexagon v73+ |

The rest of this file is one section per technique, in roughly that order, then the sources.

---

## 1. Prefill vs decode: why decode is bandwidth-bound

- **Prefill** processes the whole prompt in one batched pass: large matrix-matrix products that are
  compute-bound. **Decode** produces one token per step, and each step reads every active weight
  once for a single token (matrix-vector work), so it is limited by memory bandwidth. The Medusa and
  Lookahead papers state this directly ("each step necessitates moving the full model parameters",
  "autoregressive decoding ... is memory bandwidth bounded") [S14, S17]; Pope et al. model the same
  trade-off for large-batch vs small-batch inference on TPU v4 [S21].
- **On phones:** the 8+ Gen 1 / Snapdragon / Dimensity study measured with llama.cpp that "unlike
  the prefill stage, decoding is more heavily constrained by memory bandwidth", and measured about
  39 GB/s achievable on an Adreno 730 (63 GB/s on Adreno 750) [S30].
- **Evidence from quantization tables:** llama.cpp's README for Llama-3.1-8B shows text generation
  at 29 t/s for F16, 51 t/s for Q8_0 and 72 t/s for Q4_K_M, while prompt processing stays in the
  750–920 t/s band across all of them [S4]. Decode tracks bytes per weight; prefill does not.
  *The README does not name the hardware (unverified).*
- **Rule of thumb that follows:** decode tok/s ≈ usable bandwidth ÷ bytes read per token (active
  weights + KV cache). For pong this makes weight bytes the main lever for "writes N tok/s", and
  compute (GPU, int8 kernels, NPU) the main lever for "reads a long page in X s". *This is an
  inference from the sources above, not a measured pong figure.*

## 2. Quantization

**Weight-only vs weight+activation.**
- Weight-only quantization (int4/int8 weights, fp16 activations) shrinks the bytes decode reads.
  GPTQ quantizes to 3–4 bits with "negligible accuracy degradation" and reports end-to-end speedups
  over FP16 of about 3.25× on an NVIDIA A100 and 4.5× on an A6000 [S12]. AWQ protects about 1% of
  salient channels by activation-aware scaling; its TinyChat runtime reports more than 3× over
  Hugging Face FP16 on desktop and mobile GPUs (RTX 4090, Jetson Orin) [S11].
- Weight+activation (W8A8) also speeds compute: SmoothQuant reports up to 1.56× speed and 2× memory
  reduction (datacenter GPUs) [S13]. On phones, integer matmul instructions matter: recompiling
  llama.cpp for Armv8.7 with `smmla` (i8mm) "significantly improves throughput, with the
  acceleration primarily observed in the prefill stage", decode only slightly [S30]. pong has `i8mm`.

**MNN** [S1]:
- Export: `--quant_bit` 2/3/4/8 (default 4), `--quant_block` (default 64; 0 = per-channel),
  `--lm_quant_bit` for the output head, `--embed_bit` 16/8/4, and quality aids `--hqq` (the docs
  "generally recommend" adding it), `--awq`, `--omni`, `--smooth`, and GPTQ weights via
  `--gptq_path`.
- Runtime: `precision: "low"` prefers fp16; `memory: "low"` "enables runtime quantization" [S1].
- The MNN-LLM paper rearranges weights at load for i8mm-capable CPUs and reports up to 8.6× prefill
  over llama.cpp on CPU and up to 25.3× prefill / 7.1× decode on GPU, measured on a Xiaomi 14
  (Snapdragon 8 Gen 3) with Qwen2 1.5B/7B and Llama3 8B [S28]. These are the authors' own numbers
  against llama.cpp builds of that time.

**LiteRT-LM:** ships pre-quantized `.litertlm` files (for example `gemma-3n-E2B-it-int4`) [S6]; the
quantization is chosen at conversion, not at runtime. The benchmark page does not state which
quantization each row used (unverified) [S7].

**llama.cpp / GGUF:** k-quants (Q4_K_M ≈ 4.89 bits/weight, Q5_K_M 5.70, Q6_K 6.56, Q8_0 8.50),
IQ-quants down to about 2 bits, importance-matrix (`--imatrix`) and per-tensor overrides [S4]. On
Arm the CPU backend repacks Q4_0, Q4_K, IQ4_NL and Q8_0 at load into interleaved layouts selected by
CPU features (`q4_0_4x8` when i8mm is present, `q4_0_4x4` with dot-product only) [S5], and KleidiAI
micro-kernels can be enabled for `arm64-v8a` [S3].

**For TAI:** stay at 4-bit weights with HQQ/AWQ for MNN; keep the output head and embeddings at
the package's defaults unless a model's quality drops. Going below 4 bits trades quality for a
further decode gain in proportion to bytes; the llama.cpp table shows the gain flattening once
bits/weight fall under about 3 [S4].

## 3. Model choice: bytes per token, PLE and MoE

- **Gemma 3n / Gemma 4 E-models use Per-Layer Embeddings (PLE).** Gemma 4 E2B is 2.3B effective
  (5.1B with embeddings), E4B 4.5B effective (8B with embeddings); the PLE tables "are only used
  for quick lookups" [S25]. Google's Gemma 3n docs say PLE parameters "can be cached to fast, local
  storage" and kept out of model memory, running E2B at an effective 1.91B-parameter load, and that
  audio/vision parameters can be skipped at load [S26]. TAI's per-modality ids already do the
  latter. llama.cpp added `--lazy-mode` to read PLE rows from disk on demand (default on only for
  tensors over 4 GiB) [S2].
- **Attention layout also matters for long context.** Gemma 3 raised the ratio of local to global
  attention layers to cut KV memory [S27]; Gemma 4 E2B/E4B use a 512-token sliding window plus
  global layers with unified K/V [S25]. GQA shares KV heads for near-MQA speed at close to MHA
  quality [S22].
- **MoE** reads only the active experts per token (Mixtral: 47B total, 13B active [S23]), so decode
  bytes fall, but every expert must still be resident or streamed. On a 12 GB phone that rules out
  the large Gemma 4 MoE; PowerInfer-2 streams neuron clusters from flash to serve a 47B MoE at
  11.68 tok/s on a smartphone (the paper evaluates a OnePlus 12 with Snapdragon 8 Gen 3 and 24 GB, and a OnePlus Ace 2) [S31], which neither MNN nor LiteRT-LM
  does.
- **For TAI:** the cheapest speed-up for a given task is the smallest model that passes it: Qwen3
  0.6B and E2B read far fewer bytes per token than E4B or a 2B/7B dense model. The bench's
  Smooth/Usable verdicts already rank this.

## 4. Threads and big.LITTLE core placement

- **Measured on the same SoC as pong** (Xiaomi Pad 6 Pro, Snapdragon 8+ Gen 1, llama.cpp,
  Llama 2 7B): peak decode came with the four big cores active; efficiency cores gave "slight
  improvement during prefill, but degrade performance in decoding". The general finding: set threads
  to the number of big (prime + performance) cores [S30].
- **Contention collapses decode:** on a Xiaomi 14 Pro (8 Gen 3) running a YOLO task alongside, the
  best setting moved from 6 to 4 threads, and at 8 threads decode throughput fell by more than 90%
  [S30]. With the launcher UI, voice and embeddings sharing the CPU, 4 is the safe ceiling on pong,
  and 3 may win while something else is busy.
- **MNN:** `thread_num` (default 4) [S1]. The MNN-LLM paper describes load-balancing work between
  the prime and performance cores rather than splitting it evenly [S28]. On OpenCL the config doc
  says `thread_num` should be 68 (a GPU mode code, not a thread count), while the `llm_bench` doc
  says 4 performs better in the current buffer mode [S1]; the two MNN docs disagree, so measure.
  TAI leaves 4 in place for OpenCL.
- **LiteRT-LM:** `num_cpu_threads` for the CPU backend [S8]. Its CPU-affinity helper returns
  hard-coded performance cores only for Pixel Tensor SoCs [S8]; that it does not pin on Snapdragon is
  inferred from that header, *unverified*.
- **llama.cpp:** `-t` / `-tb` (separate prefill threads), `-C/--cpu-mask`, `--prio`, `--poll` [S2].
- **For TAI:** keep 4 on pong; benchmark 3, 4 and 5 on CPU once, and consider a lower thread count
  while voice or embeddings run. Whether MNN's threads land on CPUs 4–7 is not verified.

## 5. GPU and NPU backends

**GPU (OpenCL on Adreno; LiteRT-LM's GPU delegate).**
- LiteRT-LM's own table for Gemma-4-E2B on a Samsung S26 Ultra: CPU 557 prefill / 47 decode tok/s,
  GPU 3808 / 52; TTFT 1.8 s vs 0.3 s; peak CPU memory 1733 vs 676 MB [S7]. Gemma-3n-E2B on an S24
  Ultra: CPU 111/16, GPU 816/16 [S7]. The page does not give prompt lengths or quantization
  (unverified conditions). The pattern: GPU wins prefill 5–7× and CPU memory, decode is roughly a
  tie.
- The phone study found non-Apple mobile GPUs underused: memory-access stalls and generic kernels
  (Adreno 750, Mali-G720) [S30].
- MNN supports `opencl` (and Vulkan) backends; FlashAttention switching applies only to CPU and
  Metal, not OpenCL/Vulkan [S1].
- llama.cpp's OpenCL backend targets Adreno; verified list starts at Adreno 750, and "A6x GPUs in
  phones are likely not supported" [S10]. Adreno 730 is not on the list (unverified either way).
- pong-specific: TAI's backlog records Gemma 4 `-gpu` LiteRT files producing corrupt text on
  Adreno 730; that is a correctness block, not a speed trade.

**NPU (Hexagon via QNN; NNAPI).**
- Research systems show the NPU's value is prefill: llm.npu reports 22.4× faster prefill and 30.7×
  energy saving, measured on a Xiaomi 14 and Redmi K60 Pro [S29]; HeteroInfer reports 1.34–6.02×
  end-to-end by splitting work across GPU and NPU [S32].
- **LiteRT-LM NPU** supports Qualcomm SM8550, SM8650, SM8750, MediaTek MT6989/MT6991 and Google
  Tensor G5/G6, with per-SoC model files and 1280–8192-token contexts [S15]. pong's SM8475 is not
  listed.
- **MNN** runs LLMs on Qualcomm/MTK NPUs after an offline per-device conversion (`--generate_for_npu
  --sym --seperate_embed`, `--act_bit=16`); a separate Hexagon backend needs 4-bit symmetric
  weights [S1].
- **llama.cpp Hexagon backend** is "experimental" and builds HTP libraries for v73, v75, v79 and v81
  [S19]. Its example shows 169 tok/s prefill / 52 tok/s decode for Llama-3.2-1B Q4_0 on a device
  reporting Hexagon v79 (device not named). SM8475's Hexagon version being v69, and so outside that
  list, is *unverified*.
- **NNAPI** is deprecated as of Android 15; Google recommends other paths such as the GPU runtime
  [S34].
- **For TAI:** GPU is the only accelerator worth pursuing on pong, and mostly for long inputs. NPU is
  a future-device item.

## 6. Speculative decoding

The principle: draft several tokens cheaply, verify them in one batched target pass (which costs
about the same as one decode step, because decode is bandwidth-bound), and keep the accepted
prefix. Exact methods keep the target's output distribution.

| Method | Needs | Reported gain (hardware) | Runtime support |
|---|---|---|---|
| Draft model [S16, S33] | separate small model, same tokenizer | 2–3× on T5-XXL (TPU-v4) [S16]; 2–2.5× Chinchilla 70B (distributed) [S33] | llama.cpp `draft` [S20] |
| Medusa [S14] | extra decoding heads (fine-tune) | >2.2× (Medusa-1), 2.3–3.6× (Medusa-2) on A100/A40/A6000 | none of the three |
| EAGLE / EAGLE-2 / EAGLE-3 [S35, S36, S37] | trained 1-layer draft head per target | 2.7–3.5× LLaMA2-Chat 70B (EAGLE; the paper uses RTX 3090 and A100 GPUs); 3.05–4.26× (EAGLE-2) and up to 6.5× (EAGLE-3), NVIDIA GPUs, exact GPU for the headline figures not checked | MNN (EAGLE-3 packages), llama.cpp `draft-eagle3` |
| Multi-token prediction [S18, S9] | model trained with MTP heads | 4-token models up to 3× faster [S18]; Gemma 4 MTP drafters "up to 3x" (vendor; frameworks named, devices not) [S9] | LiteRT-LM (`enable_speculative_decoding`) [S6, S8] |
| Lookahead (Jacobi) [S17] | nothing | up to 1.8× MT-bench, 4× code on multiple A100s | no mobile runtime |
| Prompt lookup / n-gram [S24, S20] | nothing | 2–4× on input-grounded tasks, about 2.4× summarization/QA (Mistral-7B, one A100) | MNN `lookahead` [S1]; llama.cpp `ngram-*` [S20] |

- **pong evidence:** TAI's own measurement found MNN EAGLE-3 slower than plain decoding on both CPU
  and GPU, with slightly different text, so it is off by default (`On_Device_AI_Backends.md`). A
  plausible cause is that the verify pass on a phone is not free the way it is on an A100; this is
  an inference, *unverified*.
- **MNN `speculative_type: "lookahead"`** drafts from the prompt or an external `lookup_file`;
  MNN's docs say it helps mainly where output overlaps input (code edits, summaries), with
  `draft_predict_length` 2–8 (default 4) and `ngram_match_maxlen` 4 [S1].
- **For TAI:** measure LiteRT-LM MTP on pong for Gemma 4 E2B/E4B (the switch exists). For MNN,
  n-gram lookahead suits the voice-cleanup and "fix this text" paths, where the reply repeats the
  input; it needs no extra model.

## 7. KV cache: size, quantization, context length

- KV memory grows linearly with context and layers; attention cost per decoded token grows with
  context, so a long chat slows decode as well as prefill. The phone study saw performance drop as
  prompt length grew on the 8 Gen 3 device [S30].
- **Quantizing KV:** KIVI (2-bit, per-channel keys, per-token values) reports 2.6× less peak memory
  and 2.35–3.47× throughput via larger batches on an A100 [S38]; KVQuant reports <0.1 perplexity loss
  at 3 bits and up to ~1.7× kernel speedups (A100) [S39]. On a single-user phone the gain is memory
  headroom more than speed.
- **MNN** `attention_mode = flash*8 + kv_mode`: 8 = FlashAttention, fp16 KV (default); 10 = + KV
  int8 ("almost lossless"); 14 = + KV TQ4 (">30% memory saving, recommended for 4B+"); TQ3/TQ4 are
  CPU-only and lose noticeable accuracy on models under 1B. `kvcache_mmap` spills KV to disk [S1].
  Whether TQ modes exist in the bundled 3.6.1 is *unverified*; the docs read are MNN master.
- **LiteRT-LM** sizes the KV cache from `max_num_tokens` (context length) [S8]; TAI already sizes
  this from the per-device context window.
- **llama.cpp** `-ctk/-ctv` accept f16, q8_0, q4_0, q4_1, iq4_nl, q5_0, q5_1 [S2].
- **Very long sessions:** StreamingLLM keeps a few initial "sink" tokens plus a recent window, with
  up to 22.2× over sliding-window recomputation [S40]. Not exposed by MNN or LiteRT-LM as a setting.
- **For TAI:** keep the context window as small as the task allows (TaiContextWindowPolicy already
  does this per device). Try `attention_mode: 10` on MNN models of 1.5B and up if memory is tight;
  leave TQ off for Qwen3 0.6B.

## 8. Prompt and prefix caching

- **MNN:** `reuse_kv: true` reuses the previous turns' KV in multi-turn chat (default false); the
  `rollback_demo` shows saving a shared prefix's KV to disk so a later launch skips that prefill [S1].
  TAI sets `prompt_cache: true`, which is not described in the MNN doc read here (*unverified what it
  does in 3.6.1*).
- **LiteRT-LM:** the runtime has a `PrefixCache` that "finds the longest common prefix between the
  cached elements and the incoming elements", including media hashes [S41].
- **llama.cpp server:** `cache_prompt` (default on) re-processes only the differing suffix;
  `--cache-reuse` shifts KV chunks; `--slot-save-path` saves KV to disk; the CLI has
  `--prompt-cache` for long fixed prompts [S2, S42].
- **For TAI:** the OpenAI-style API resends the whole history each request, so prefix reuse turns
  each follow-up's prefill into the cost of the new message. This is the biggest TTFT lever after
  the GPU for chat and agent loops with long system prompts or tool lists.

## 9. FlashAttention

- FlashAttention tiles attention to cut memory reads/writes between GPU HBM and on-chip SRAM; the
  paper reports up to 3× on GPT-2 at 1K tokens (training, A100) [S43]. It matters for prefill and long
  contexts, less for short decode.
- **MNN:** on by default for CPU (`attention_mode` 8); Metal has a prefill kernel; OpenCL/Vulkan
  ignore the switch [S1].
- **llama.cpp:** `-fa on|off|auto` [S2]; the OpenCL doc warns "flash attention does not always
  improve performance" [S10].
- **LiteRT-LM:** no user switch found in the flags read [S8].

## 10. Prefill chunking and batching

- **Chunked prefill** caps memory: MNN `chunk` / `chunk_limits` [S1]; LiteRT-LM
  `prefill_batch_sizes` [S8]; llama.cpp `-ub` (physical batch, default 512) [S2]. Bigger chunks
  usually speed prefill at the cost of peak memory.
- **Request batching** (continuous batching, PagedAttention, 2–4× server throughput [S44]) raises
  throughput for many users. TAI serves one user; its only concurrency is embeddings during chat,
  which TAI already throttles. Low payoff here.

## 11. CPU dynamic quantization for decode (MNN)

- MNN `dynamic_option` quantizes feature maps per channel (0), per tensor (1) or per block (2);
  values 8+n are meant "to accelerate LLM decode", but prefill becomes "significantly slower" for
  prompts shorter than 300 tokens [S1]. Worth a bench pass on pong for chat-style use, where
  prompts are short and decode dominates.
- `cpu_sme2_neon_division_ratio` applies to Arm SME CPUs [S1]; pong's cores do not list SME.

## 12. Memory mapping and load time

- **MNN:** `use_mmap` (recommended true on phones) spills weights to disk under memory pressure;
  `llm_bench` says mmap has no effect on inference speed [S1]. TAI's mmap weight cache is this.
- **LiteRT-LM:** a weight cache (CPU and GPU) and a GPU program cache, with flags to disable each;
  `cacheDir` "can improve 2nd load time" [S8, S6]. TAI passes null, which puts the cache next to the
  model (see `gallery-gpu-loading-comparison.md`).
- **llama.cpp:** `--load-mode` auto/mmap/mlock; disabling mmap loads slower but "may reduce
  pageouts" [S2].
- Load time does not change tok/s, but it dominates the first reply after a cold start, and
  memory-pressure pageouts can stall decode. *The pageout-stall effect on pong is unverified.*

## 13. Thermal throttling and DVFS

- The phone study found Snapdragon DVFS "more aggressive": on 8 Gen 3 the prime and performance cores'
  frequency was "nearly halved" by the 9th consecutive run, and running a vision model alongside
  took battery temperature from 28 °C to 47 °C with severe throttling [S30]. It suggests single,
  intermittent requests (more than 30 s apart) are the common case and can run at higher clocks.
- pong's cores are clocked lower than the study's 8+ Gen 1 device (X2 at 3.00 vs 3.2 GHz, from
  pong's `cpufreq`), so absolute figures differ.
- Android's ADPF offers `getThermalHeadroom()`, a thermal status listener and Performance Hint
  sessions; Google says it targets games but "you can also use the features for other
  performance-intensive apps" [S45]. The API levels were not confirmed at the reference page
  (*unverified*).
- **For TAI:** the bench should record thermal state or at least the run order, cool down between
  models, and report sustained decode separately from the first run. Throttling is a measurement
  risk before it is an optimisation.

---

## Unverified items (collected)

1. Hardware for llama.cpp's quantization speed table [S4] is not stated.
2. LiteRT-LM benchmark conditions (prompt/decode length, quantization) are not stated [S7].
3. Gemma 4 MTP "up to 3x" is a vendor blog claim; the device for the E2B/E4B figures is not named
   [S9].
4. SM8475's Hexagon version (v69), and so its exclusion from llama.cpp's Hexagon builds.
5. Adreno 730 support in llama.cpp's OpenCL backend (not on the verified list, not excluded).
6. MNN master options (`attention_mode` TQ modes, `dynamic_option` 8+n) being present in the bundled
   MNN 3.6.1; what TAI's `prompt_cache: true` does in 3.6.1.
7. Whether MNN's 4 threads land on pong's CPUs 4–7; LiteRT-LM not pinning cores on Snapdragon
   (inferred from source).
8. The GPU behind the EAGLE-2 and EAGLE-3 headline speedups. Why EAGLE-3 is slower on pong (the verify-cost explanation is an inference).
9. ADPF API levels; pageout stalls on pong under memory pressure.
10. The decode ≈ bandwidth ÷ bytes rule applied to pong is an inference, not a pong measurement.

## Sources

Runtimes (docs and source):

- [S1] MNN LLM docs (`docs/transformers/llm.md`, master): https://github.com/alibaba/MNN/blob/master/docs/transformers/llm.md
- [S2] llama.cpp `tools/completion/README.md`: https://github.com/ggml-org/llama.cpp/blob/master/tools/completion/README.md
- [S3] llama.cpp `docs/build.md` (KleidiAI): https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
- [S4] llama.cpp `tools/quantize/README.md`: https://github.com/ggml-org/llama.cpp/blob/master/tools/quantize/README.md
- [S5] llama.cpp `ggml/src/ggml-cpu/repack.cpp`: https://github.com/ggml-org/llama.cpp/blob/master/ggml/src/ggml-cpu/repack.cpp
- [S6] LiteRT-LM README and Kotlin guide: https://github.com/google-ai-edge/LiteRT-LM , https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- [S7] LiteRT-LM overview and benchmarks: https://developers.google.com/edge/litert-lm/overview (redirected from ai.google.dev/edge/litert-lm/overview)
- [S8] LiteRT-LM engine flags and CPU affinity: https://github.com/google-ai-edge/LiteRT-LM/blob/main/runtime/engine/shared_flags.cc , https://github.com/google-ai-edge/LiteRT-LM/blob/main/runtime/engine/cpu_affinity_utils.h
- [S9] Google, "Accelerating Gemma 4: faster inference with multi-token prediction drafters" (linked from the LiteRT-LM README): https://blog.google/innovation-and-ai/technology/developers-tools/multi-token-prediction-gemma-4/
- [S10] llama.cpp OpenCL backend: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md
- [S15] LiteRT-LM NPU: https://developers.google.com/edge/litert/next/litert_lm_npu
- [S19] llama.cpp Snapdragon/Hexagon backend: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md
- [S20] llama.cpp speculative decoding: https://github.com/ggml-org/llama.cpp/blob/master/docs/speculative.md
- [S24] Prompt lookup decoding (author's repo): https://github.com/apoorvumang/prompt-lookup-decoding
- [S25] Gemma 4 model card: https://ai.google.dev/gemma/docs/core/model_card_4
- [S26] Gemma 3n overview: https://ai.google.dev/gemma/docs/gemma-3n
- [S34] Android NNAPI (deprecation): https://developer.android.com/ndk/guides/neuralnetworks
- [S41] LiteRT-LM `runtime/core/prefix_cache.h`: https://github.com/google-ai-edge/LiteRT-LM/blob/main/runtime/core/prefix_cache.h
- [S42] llama.cpp server README: https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md
- [S45] Android Dynamic Performance Framework: https://developer.android.com/games/optimize/adpf

Papers:

- [S11] Lin et al., AWQ: https://arxiv.org/abs/2306.00978
- [S12] Frantar et al., GPTQ: https://arxiv.org/abs/2210.17323
- [S13] Xiao et al., SmoothQuant: https://arxiv.org/abs/2211.10438
- [S14] Cai et al., Medusa: https://arxiv.org/abs/2401.10774
- [S16] Leviathan et al., Fast Inference from Transformers via Speculative Decoding: https://arxiv.org/abs/2211.17192
- [S17] Fu et al., Lookahead Decoding: https://arxiv.org/abs/2402.02057
- [S18] Gloeckle et al., Better & Faster LLMs via Multi-token Prediction: https://arxiv.org/abs/2404.19737
- [S21] Pope et al., Efficiently Scaling Transformer Inference: https://arxiv.org/abs/2211.05102
- [S22] Ainslie et al., GQA: https://arxiv.org/abs/2305.13245
- [S23] Jiang et al., Mixtral of Experts: https://arxiv.org/abs/2401.04088
- [S27] Gemma Team, Gemma 3 Technical Report: https://arxiv.org/abs/2503.19786
- [S28] Wang et al., MNN-LLM: https://arxiv.org/abs/2506.10443
- [S29] Xu et al., Fast On-device LLM Inference with NPUs (llm.npu): https://arxiv.org/abs/2407.05858
- [S30] Xiao et al., Understanding Large Language Models in Your Pockets: Performance Study on COTS Mobile Devices: https://arxiv.org/abs/2410.03613
- [S31] Xue et al., PowerInfer-2: https://arxiv.org/abs/2406.06282
- [S32] Characterizing Mobile SoC for Accelerating Heterogeneous LLM Inference (HeteroInfer): https://arxiv.org/abs/2501.14794
- [S33] Chen et al., Accelerating LLM Decoding with Speculative Sampling: https://arxiv.org/abs/2302.01318
- [S35] Li et al., EAGLE: https://arxiv.org/abs/2401.15077
- [S36] Li et al., EAGLE-2: https://arxiv.org/abs/2406.16858
- [S37] Li et al., EAGLE-3: https://arxiv.org/abs/2503.01840
- [S38] Liu et al., KIVI: https://arxiv.org/abs/2402.02750
- [S39] Hooper et al., KVQuant: https://arxiv.org/abs/2401.18079
- [S40] Xiao et al., StreamingLLM (Attention Sinks): https://arxiv.org/abs/2309.17453
- [S43] Dao et al., FlashAttention: https://arxiv.org/abs/2205.14135
- [S44] Kwon et al., PagedAttention / vLLM: https://arxiv.org/abs/2309.06180
