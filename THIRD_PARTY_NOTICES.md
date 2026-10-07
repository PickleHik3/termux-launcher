# Open-source licenses and attributions

Termux Launcher is a modified distribution of
[Termux](https://github.com/termux/termux-app) and
[Termux:Monet](https://github.com/Termux-Monet/termux-monet). The launcher-specific source is
available at [PickleHik3/termux-launcher](https://github.com/PickleHik3/termux-launcher).

The project as a whole is distributed under GPLv3-only. Full license texts are included with the
source distribution and in the app's **Settings > Open-source licenses** screen.

## Vendored and adapted code

- **Termux** — GPLv3-only — Copyright Termux contributors.
- **Termux:Monet** — GPLv3-only — Copyright Termux:Monet contributors.
- **[Unexpected-Keyboard](https://github.com/Julow/Unexpected-Keyboard)** — GPL-3.0 — Copyright Jules Aguillon (Julow) and Unexpected-Keyboard contributors. Vendored and modified as the `inapp-keyboard/` module (upstream commit `38836e440d8ca779d572b52601c6b2ad10f3bb7f` recorded in `inapp-keyboard/UPSTREAM.md`); modifications include removal of the IME service and adaptation as an embedded view. See `inapp-keyboard/UPSTREAM.md`.
- **[kitty](https://github.com/kovidgoyal/kitty)** — GPL-3.0-only — Copyright Kovid Goyal
  and kitty contributors. `terminal-view/.../KittyCursorTrail.java` adapts
  `kitty/cursor_trail.c` to Java and Android pane geometry, and
  `app/.../terminal/CursorTrailParticles.java` ports `cursor-trail-particles.slang`
  (its pixiedust mode) to a CPU particle model; the graphics animation handling in
  `terminal-emulator/.../KittyImageStore.java` also follows kitty's `graphics.c`. These are
  adaptations shipped inside the APK, separate from the external `kitten` tool below. The cursor
  trail files were ported from, and last checked against, kitty `master` at commit
  `ce459fb1a44b72a2e40a223b74c7aa078d7df5f1` (`kitty/cursor_trail.c`, `kitty/shaders/trail.slang`
  and `kitty/shaders/custom/cursor-trail-*`); the Java files document local changes.
- **[herdr](https://github.com/herdrdev/herdr)** — Apache-2.0 — herdr contributors.
  `AgentTitleRules.java` and `AgentScreenRules.java` adapt the agent-detection manifests recorded
  on 2026-09-11 into ordered Java rules. The original work is credited here separately from the
  independently written herdr colour template. The exact manifest revision was not recorded;
  the upstream v0.9.0 distribution carries Apache-2.0.
- **[Google AI Edge LiteRT samples](https://github.com/google-ai-edge/litert-samples)** —
  Apache-2.0 — Copyright 2026 Google LLC; Copyright 2026 The Google AI Edge Authors.
  `TaiG2p.java`, `TaiTtsChunker.java`, and `ParakeetTdtDecoder.java` adapt the samples'
  `KittenG2P.kt`, `SentenceChunker.kt`, and `TdtDecoder.kt`. The host evaluation scripts under
  `scripts/tts-eval/` and `scripts/parakeet_replay_server.py` use the same upstream work.
  The adaptations change language, runtime integration, chunking and decoding; their comments
  describe the changes. This code ships in the APK; the model weights described below do not.
- **Terminal Emulator for Android** — Apache-2.0 — Copyright Jack Palevich and contributors.
- **Android Open Source Project / Launcher3** — Apache-2.0 — portions of terminal compatibility,
  `termux-am-library`, and launcher gesture navigation.
- **Lawnchair** — Apache-2.0 — launcher gesture-navigation compatibility adapted from Lawnchair.
- **RealtimeBlurView** — Apache-2.0 — Copyright 2016 Tu Yimin. The vendored implementation is
  modified by Termux Launcher.
- **libsuperuser** — Apache-2.0 — Copyright 2012–2019 Jorrit "Chainfire" Jongma.
- **libcore/ojluni** — GPLv2-only with the Classpath exception — filesystem compatibility classes.
- **[DrJava](https://drjava.org/) `ArgumentTokenizer`** — BSD-3-Clause — Copyright (c) 2001-2010,
  JavaPLT group at Rice University. Inherited from Termux as
  `termux-shared/.../shell/ArgumentTokenizer.java`; the notice is reproduced in
  THIRD_PARTY_LICENSES.txt.
- **[Chromium](https://www.chromium.org/)** — BSD-3-Clause — Copyright The Chromium Authors. The
  X11 touch-input classes that termux-x11 derived from Chromium Remoting
  (`x11-server/.../x11/input/`: `InputEventSender`, `TouchInputHandler`, `TapGestureDetector`,
  `SwipeDetector`, `InputStub`, `InputStrategyInterface`, `RenderData`) keep that license; the
  notice is reproduced in THIRD_PARTY_LICENSES.txt.
- **MNN 3.6.1** — Apache-2.0 — Copyright 2018 Alibaba Group. Termux Launcher distributes modified
  arm64 native builds and a patched UTF-8 stream processor.
- **nlohmann/json 3.11.2** — MIT — Copyright 2013–2022 Niels Lohmann. It is statically included in
  the MNN Android JNI library.
- **Tinted Theming schemes** — MIT — imported on demand from
  [tinted-theming/schemes](https://github.com/tinted-theming/schemes); palette authors remain
  credited in the downloaded scheme metadata.
- **[noctalia shell](https://github.com/noctalia-dev/noctalia-shell)** — MIT — Copyright (c) 2026 noctalia-dev.
  The full upstream notice is reproduced in THIRD_PARTY_LICENSES.txt. The `starship` and `helix`
  theme templates under
  `app/src/main/assets/theme-templates/` are ported from noctalia's
  `assets/templates/{starship,helix}/`, renamed from `noctalia` to `launcher-material` and
  adapted to the launcher's own hook environment and Material palette (no filters or wallpaper
  ANSI-derivation logic carried over).
- **[termux-x11](https://github.com/termux/termux-x11)** — GPL-3.0-only — Copyright Twaik Yont
  and termux-x11 contributors. The `lorie` library module is vendored and modified as
  `x11-server/` and its `shell-loader` as `x11-server/loader/` (upstream commit
  `9df8b767645aa0d0a2f2576767449df55b41962f`, recorded in `x11-server/UPSTREAM.md` with every
  deviation); the `libXlorie.so` prebuilts under `app/src/main/jniLibs/` are built from the same
  commit with the patch set in `ci/x11-patch/`. `libXlorie.so` statically links the freedesktop
  components termux-x11 builds from source: the **X.Org Server**, **libX11**, **xorgproto**,
  **libXfont2**, **libxkbfile**, **xkbcomp**, **libxcvt**, **libxshmfence**, **xtrans**,
  **libXau**, **libXdmcp** and **libfontenc** (individual notices reproduced in THIRD_PARTY_LICENSES.txt),
  **pixman** (MIT), **libepoxy** (MIT), **libtirpc** (BSD-style notices — Copyright Sun Microsystems,
  Inc., Bull S.A., and other authors named in the bundled notices) and **bzip2** (bzip2 license — Copyright 1996–2019 Julian Seward).

## Bundled assets

- **[Symbols Nerd Font Mono](https://github.com/ryanoasis/nerd-fonts)** — MIT for the Symbols-only font,
  with additional licenses for its constituent glyphs — Copyright 2014 Ryan L McIntyre and
  Nerd Fonts contributors. Version 3.5.0. Shipped as
  `app/src/main/assets/fonts/SymbolsNerdFontMono.ttf`, drawn on app chrome and extracted for the
  terminal font config. The icon sets Nerd Fonts aggregates — Material Design Icons, Font Awesome,
  Octicons, Weather Icons, Devicons, Codicons, Powerline and others — remain under their own
  licenses; the upstream license audit and glyph notices are reproduced in THIRD_PARTY_LICENSES.txt.
  The glyph names in
  `app/src/main/res/raw/nerd_font_glyphs.csv` are generated from that font's own name table.
- **[Meteocons](https://github.com/basmilius/meteocons)** — MIT — Copyright 2020-present Bas
  Milius. The weather animations in `app/src/main/assets/weather/` are the fill style of
  `@meteocons/lottie`, unmodified apart from compact re-serialization; the license text ships beside
  them as `app/src/main/assets/weather/LICENSE.txt`.
- **[Silero VAD](https://github.com/snakers4/silero-vad) v5.1.2** — MIT — Copyright 2020-present
  Silero Team. The voice key's speech detector, `app/src/main/assets/vad/silero_vad_v5.tflite`, is
  the 16 kHz graph of the upstream `silero_vad.onnx` rebuilt for LiteRT with its weights unchanged
  (`scripts/voice-eval/convert_silero.py`); the license text ships beside it as
  `app/src/main/assets/vad/LICENSE-silero.txt`.
- **[OpenAI CLIP](https://github.com/openai/CLIP) tokenizer** — MIT — Copyright (c) 2021 OpenAI.
  `app/src/main/assets/tai-diffusion/clip-vit-l14.tokenizer.mtok` is the CLIP ViT-L/14 BPE
  vocabulary and merges, converted to MNN's tokenizer format for Stable Diffusion imports. The MIT
  notice is reproduced in THIRD_PARTY_LICENSES.txt.
- **[Material Symbols](https://github.com/google/material-design-icons)** — Apache-2.0 — Copyright
  Google LLC. The vector paths of the `ic_symbol_*` drawables. The Android robot in
  `ic_symbol_icon_pack.xml` is reproduced or modified from work created and shared by Google and
  used according to terms described in the
  [Creative Commons 3.0 Attribution License](https://creativecommons.org/licenses/by/3.0/).
- **Terminal bell** (`termux-shared/src/main/res/raw/bell.ogg`) — inherited unchanged from Termux.

## Runtime libraries

The Android application also uses these independently maintained libraries:

- AndroidX and Material Components for Android — Apache-2.0
- AndroidSVG 1.4 — Apache-2.0
- Android Image Cropper — Apache-2.0
- Apache Commons IO — Apache-2.0
- Google Guava — Apache-2.0
- Google Gson — Apache-2.0 — through LiteRT-LM
- Google LiteRT 1.4.2 and LiteRT-LM 0.17.1 — Apache-2.0
- Kotlin standard library, kotlin-reflect and kotlinx.coroutines — Apache-2.0 — through LiteRT-LM
  and AndroidX
- Lottie for Android — Apache-2.0 — renders the bundled Meteocons weather animations
- HiddenApiBypass — Apache-2.0
- Markwon — Apache-2.0
- Process Phoenix — Apache-2.0
- Shizuku API — Apache-2.0
- CommonMark Java — BSD-2-Clause
- Android core library desugaring (`desugar_jdk_libs` 2.1.2) — GPLv2 with the Classpath exception
  — OpenJDK-derived `java.*` classes compiled into the APK for older Android versions

Dependencies used only by tests and build tooling are not part of the distributed APK. Their
licenses remain available in their respective distributions.

## Build recipes for external terminal tools

The source, build recipes, patches, corresponding-source pointers and tool-specific licenses
live in [PickleHik3/tlstore](https://github.com/PickleHik3/tlstore). The launcher bundles the
`tlstore` engine and store UI; it downloads the selected tools when the user installs them.

- **tlstore engine and `tlstore-ui`** — GPL-3.0-only — Copyright tlstore contributors.
  Bundled in the APK at the tag pinned by `app/tlstore.lock`. The `tlstore-ui-<abi>` binaries
  statically link the Rust standard library and these crates, each under MIT, Apache-2.0, Zlib or a
  choice among them: adler2, bitflags, cfg-if, core_maths, crc32fast, fdeflate, flate2, fontdue,
  libc, libm, memchr, miniz_oxide, png, pulldown-cmark, simd-adler32, ttf-parser, unicase,
  unicode-width, zlib-rs, zune-core and zune-jpeg. They also embed the
  **[Pinyon Script](https://github.com/SorkinType/Pinyon)** font — SIL OFL 1.1 — Copyright 2024
  The Pinyon Project Authors. All of these notices are reproduced in THIRD_PARTY_LICENSES.txt.

The catalog includes kitten, Fastfetch, Sigye, dawn, btop, and the runtime used for Claude Code
and OpenCode, as well as shell and theme configs. Their binaries are not bundled in the APK.
The following entries describe some of those external tools; tlstore maintains the complete
notices for its own releases.

- **[Fastfetch](https://github.com/fastfetch-cli/fastfetch)** — MIT — Copyright 2021–2023 Linus
  Dierheimer, 2022– Carter Li. Built from pinned commit `9c7cfb8` (v2.67.0) with the repository's
  animated Kitty graphics patch.
- **[Sigye](https://github.com/am2rican5/sigye)** — MIT — built from pinned commit `0f0b8ca`
  (v0.6.0) with the repository's Termux clipboard patch.
- **[kitty](https://github.com/kovidgoyal/kitty) `kitten`** — GPL-3.0-only — Copyright Kovid Goyal.
  Built from tag `v0.48.2`. The binary statically links kitty's Go dependencies (MIT and
  BSD-licensed), whose notices travel with it. Distributing a built `kitten` obliges the
  distributor to offer the corresponding source under GPLv3.
- **[Chafa](https://github.com/hpjansson/chafa)** — LGPL-3.0-or-later — Copyright Hans Petter
  Jansson. Fastfetch loads `libchafa.so` with `dlopen` at run time and does not link it statically,
  which is also what keeps LGPLv3's relinking requirement out of scope. Chafa itself bundles
  lodepng (Zlib) and libnsgif (MIT).
- **[ImageMagick 7](https://imagemagick.org/)** — SPDX `ImageMagick` (the ImageMagick License, an
  Apache-2.0 derivative, not Apache-2.0 itself) — Copyright ImageMagick Studio LLC. Also loaded
  with `dlopen` at run time.

Chafa and ImageMagick reach a device through the Termux package repositories, not through this
project.

## Data sources

- **[Open-Meteo](https://open-meteo.com/)** — forecast data licensed
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The status bar's weather reading and
  its detail card are drawn from Open-Meteo's keyless forecast API, and carry the credit
  **Weather data by Open-Meteo.com** beside the data. No account, API key or personal identifier is
  sent — a request carries the device's last known coordinates to four decimal places and nothing
  else — and a request is only made when the weather reading is enabled and location permission has
  been granted. Open-Meteo's own server software is AGPLv3; Termux Launcher calls the public API and
  distributes none of it.
- **System speech recognizer** — the system voice-typing path hands off through Android's
  `RecognizerIntent` to whichever recognizer app the device provides. No recognizer SDK is linked
  into the APK.
- **Remote model provider** — optional and off by default. The user supplies an OpenAI-compatible
  endpoint and their own API key; no key or provider SDK ships with the app.

## Models downloaded on request

Nothing in this section ships inside the APK. Each model is downloaded from Hugging Face, pinned to
the revision recorded in `app/src/main/java/com/termux/ai/TaiModelCatalog.java`, only when the user
installs it in **Settings > TAI > Model centre**, and runs on the phone in the app's own runtime
process. The Model centre shows each model's license before the download. Models the user imports
by URL or from a file remain under the terms of their own model cards.

### Language models

- **[Gemma 4 E2B and E4B instruct](https://huggingface.co/google/gemma-4-E2B-it)** — Apache-2.0 —
  Copyright Google LLC. The LiteRT-LM conversions
  ([E2B](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm),
  [E4B](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)) are published by
  litert-community.
- **[EmbeddingGemma 300M](https://huggingface.co/google/embeddinggemma-300m)** — Gemma Terms of Use
  — provided by Google under the [Gemma Terms of Use](https://ai.google.dev/gemma/terms), which
  include the [Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy).
  The model is gated: the user accepts those terms on Hugging Face and downloads the
  [litert-community conversion](https://huggingface.co/litert-community/embeddinggemma-300m) with
  their own access token. Termux Launcher does not redistribute it.

### Speech input models

- **[Whisper ACFT](https://huggingface.co/litert-community/whisper-acft)** (base, base.en, small,
  small.en) — Apache-2.0 — the audio-context fine-tuned checkpoints by
  [FUTO](https://github.com/futo-org/whisper-acft) of OpenAI's Whisper (Copyright (c) 2022 OpenAI),
  converted for LiteRT by litert-community. Their `tokenizer.json` files come from the matching
  `openai/whisper-*` repositories, Apache-2.0.
- **[Parakeet TDT 0.6B v3](https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3)** — CC-BY-4.0 —
  by NVIDIA, licensed under
  [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/).
  The app downloads the int8 LiteRT conversion published by
  [litert-community](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3), which changes
  the format and quantization of NVIDIA's weights, and the tokenizer from NVIDIA's own repository.
  That conversion's model card says Apache-2.0, but the CC-BY-4.0 terms of NVIDIA's weights still
  apply.

### Speech output model

- **[KittenTTS nano 0.8](https://huggingface.co/litert-community/kitten-tts-nano-0.8)** —
  Apache-2.0 — Copyright KittenML (KittenTTS). The LiteRT conversion (`kitten_predictor.tflite`,
  `kitten_prosody.tflite`, `kitten_vocoder.tflite`, `voices.npz`) is published by
  litert-community. The separately bundled pipeline code is credited under Vendored and adapted code above.
- **OpenPhonemizer pronunciation dictionary** (`g2p_dict.txt.gz`, as published in
  [litert-community/Matcha-TTS](https://huggingface.co/litert-community/Matcha-TTS)) — The Clear
  BSD License — Copyright (c) 2024 mrfakename, NeuralVox, OpenPhonemizer Contributors. The Clear BSD
  License grants **no patent rights**; see its full text below.
- **[DeepPhonemizer](https://github.com/as-ideas/DeepPhonemizer)** (`dp_g2p_matcha_fp16.tflite`
  and `g2p_meta.json`, OpenPhonemizer's checkpoint converted for LiteRT) — MIT — Copyright (c) 2021
  Axel Springer News Media & Tech GmbH & Co. KG - Ideas Engineering. The MIT terms are those of the
  nlohmann/json notice below with this copyright line.

No GPL phonemizer (espeak-ng) is used or downloaded.

## The Clear BSD License, for the OpenPhonemizer dictionary

Copyright (c) 2024 mrfakename, NeuralVox, OpenPhonemizer Contributors
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted
(subject to the limitations in the disclaimer below) provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright notice, this list of conditions
  and the following disclaimer.
- Redistributions in binary form must reproduce the above copyright notice, this list of
  conditions and the following disclaimer in the documentation and/or other materials provided
  with the distribution.
- Neither the name of the copyright holder nor the names of its contributors may be used to
  endorse or promote products derived from this software without specific prior written
  permission.

NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY THIS LICENSE. THIS
SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## MIT notice for nlohmann/json

Copyright © 2013-2022 Niels Lohmann

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT
OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Additional full license texts

[THIRD_PARTY_LICENSES.txt](THIRD_PARTY_LICENSES.txt) reproduces the individual license notices
from the pinned X11 dependencies, Noctalia, Nerd Fonts and its glyph sources, Chromium, DrJava,
OpenAI CLIP, and the `tlstore-ui` crates and font. It is bundled
with this document in the app's Open-source licenses screen. Source URLs identify each notice's
origin; the X11 revisions are those pinned by termux-x11 at
`9df8b767645aa0d0a2f2576767449df55b41962f`.
