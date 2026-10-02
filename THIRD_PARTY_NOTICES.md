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
  `kitty/cursor_trail.c` to Java and Android pane geometry; `app/.../terminal/CursorTrailMotionBlur.java`
  translates `cursor-trail-motion-blur.slang` to AGSL (originally contributed to kitty by Jonathan
  Lippincott) and `app/.../terminal/CursorTrailParticles.java` ports `cursor-trail-particles.slang`
  (railgun, torpedo, pixiedust) to a CPU particle model; the graphics animation handling in
  `terminal-emulator/.../KittyImageStore.java` also follows kitty's `graphics.c`. These are
  adaptations shipped inside the APK, separate from the external `kitten` tool below. The original
  port commits did not record an exact upstream revision; the Java files document local changes.
- **[Android-AGSL-Shader-Playground](https://github.com/mejdi14/Android-AGSL-Shader-Playground)** —
  MIT — Copyright (c) 2025 Mejdi Hafiene. The `Chrome` generated background
  (`app/.../chrome/wallpaper/Chrome.java`) follows the sine warp and sheen of its
  `LiquidChromeEffect`, rewritten to bend a palette instead of an input picture and to loop.
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

## Runtime libraries

The Android application also uses these independently maintained libraries:

- AndroidX and Material Components for Android — Apache-2.0
- Android Image Cropper — Apache-2.0
- Apache Commons IO — Apache-2.0
- Google Guava — Apache-2.0
- Google LiteRT and LiteRT-LM — Apache-2.0
- Lottie for Android — Apache-2.0 — renders the bundled Meteocons weather animations
- HiddenApiBypass — Apache-2.0
- Markwon — Apache-2.0
- Process Phoenix — Apache-2.0
- SentencePiece4J — Apache-2.0
- Shizuku API — Apache-2.0
- CommonMark Java — BSD-2-Clause

Dependencies used only by tests and build tooling are not part of the distributed APK. Their
licenses remain available in their respective distributions.

## Build recipes for external terminal tools

The source, build recipes, patches, corresponding-source pointers and tool-specific licenses
live in [PickleHik3/tlstore](https://github.com/PickleHik3/tlstore). The launcher bundles the
`tlstore` engine and store UI; it downloads the selected tools when the user installs them.
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

## Speech output model (downloaded on request)

Nothing below ships inside the APK. The files are downloaded from Hugging Face only when the user
installs the voice model in **Settings > TAI > Model centre > Speech > Voice output**, and they run
on the phone in the app's own runtime process.

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
from the pinned X11 dependencies, Noctalia, and Nerd Fonts and its glyph sources. It is bundled
with this document in the app's Open-source licenses screen. Source URLs identify each notice's
origin; the X11 revisions are those pinned by termux-x11 at
`9df8b767645aa0d0a2f2576767449df55b41962f`.
