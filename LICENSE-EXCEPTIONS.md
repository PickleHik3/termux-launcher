The `termux/termux-app` repository and its fork, `Termux-Monet/termux-monet`, are released under [GPLv3 only](https://www.gnu.org/licenses/gpl-3.0.html) license.

### Exceptions

- [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator) code is used which is released under [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) license. Check [`terminal-view`](terminal-view) and [`terminal-emulator`](terminal-emulator) libraries.
- Check [`termux-shared/LICENSE.md`](termux-shared/LICENSE.md) for `termux-shared` library related exceptions.
- The `termux-am-library` library is released under [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) license.
- Launcher gesture-navigation compatibility is adapted from AOSP Launcher3 and Lawnchair under Apache 2.0. See [`docs/launcher-animation-attribution.md`](docs/launcher-animation-attribution.md).
- The vendored RealtimeBlurView implementation is based on [mmin18/RealtimeBlurView](https://github.com/mmin18/RealtimeBlurView), Copyright 2016 Tu Yimin, under Apache 2.0.
- The bundled arm64 MNN native runtime is built from [alibaba/MNN 3.6.1](https://github.com/alibaba/MNN/tree/3.6.1), Copyright 2018 Alibaba Group, under Apache 2.0. It includes a Termux Launcher modification to the UTF-8 stream processor.
- The speech pipeline adapts code from [google-ai-edge/litert-samples](https://github.com/google-ai-edge/litert-samples) (`TaiG2p.java`, `TaiTtsChunker.java`, `ParakeetTdtDecoder.java`), Copyright 2026 Google LLC, under Apache 2.0.
- The X11 touch-input classes under `x11-server/src/main/java/com/termux/x11/input/` derive from Chromium, Copyright The Chromium Authors, under a BSD 3-Clause license.
- `termux-shared/src/main/java/com/termux/shared/shell/ArgumentTokenizer.java` comes from DrJava, Copyright 2001-2010 JavaPLT group at Rice University, under a BSD 3-Clause license.

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for runtime dependency notices.
