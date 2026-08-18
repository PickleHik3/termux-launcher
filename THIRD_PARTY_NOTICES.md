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
- **Terminal Emulator for Android** — Apache-2.0 — Copyright Jack Palevich and contributors.
- **Android Open Source Project / Launcher3** — Apache-2.0 — portions of terminal compatibility,
  `termux-am-library`, and launcher gesture navigation.
- **Lawnchair** — Apache-2.0 — launcher gesture-navigation compatibility adapted from Lawnchair.
- **RealtimeBlurView** — Apache-2.0 — Copyright 2016 Tu Yimin. The vendored implementation is
  modified by Termux Launcher.
- **libsuperuser** — Apache-2.0 — Copyright 2012–2019 Jorrit "Chainfire" Jongma.
- **libcore/ojluni** — GPLv2-only with the Classpath exception — filesystem compatibility classes.
- **MNN 3.6.0** — Apache-2.0 — Copyright 2018 Alibaba Group. Termux Launcher distributes modified
  arm64 native builds and a patched UTF-8 stream processor.
- **nlohmann/json 3.11.2** — MIT — Copyright 2013–2022 Niels Lohmann. It is statically included in
  the MNN Android JNI library.
- **Tinted Theming schemes** — MIT — imported on demand from
  [tinted-theming/schemes](https://github.com/tinted-theming/schemes); palette authors remain
  credited in the downloaded scheme metadata.

## Runtime libraries

The Android application also uses these independently maintained libraries:

- AndroidX and Material Components for Android — Apache-2.0
- Android Image Cropper — Apache-2.0
- Apache Commons IO — Apache-2.0
- Google Guava — Apache-2.0
- Google LiteRT and LiteRT-LM — Apache-2.0
- HiddenApiBypass — Apache-2.0
- Markwon — Apache-2.0
- Process Phoenix — Apache-2.0
- SentencePiece4J — Apache-2.0
- Shizuku API — Apache-2.0
- CommonMark Java — BSD-2-Clause

Dependencies used only by tests and build tooling are not part of the distributed APK. Their
licenses remain available in their respective distributions.

## Build recipes for external terminal tools

The `recipes/` directory builds third-party command-line tools that exercise the launcher's
graphics protocols. **No binary of any of these is distributed inside the Termux Launcher APK.**
This repository contains only build scripts and patches; the tools are compiled from upstream
sources on the machine that runs a recipe.

Prebuilt `aarch64` binaries of three of them — `kitten`, the patched Fastfetch, and Sigye — are
published separately at
[PickleHik3/termux-launcher-binaries](https://github.com/PickleHik3/termux-launcher-binaries),
which `setup-launcher` can install. That repository carries the upstream licence texts, the
patches, the build recipes, and the corresponding-source pointers for the GPL-3.0-only `kitten`.
The notices below apply to those builds.

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
