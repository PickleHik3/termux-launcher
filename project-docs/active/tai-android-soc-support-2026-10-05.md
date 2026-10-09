# TAI: Android versions and phone processors (fact check for the tiers)

Status: research note, 2026-10-05, against dev `8411d209c`. Read-only. It checks the platform facts that
`tai-device-tiers-2026-10-05.md` and `tai-device-classes-and-gates-2026-10-05.md` rest on. Every answer names
its source key; the keys are listed under **Sources**. Anything not found in a primary source is listed under
**Unverified**. Issue threads on the upstream trackers count as primary only for what a maintainer
(MEMBER/COLLABORATOR/CONTRIBUTOR) said; reporter claims are marked "user report".

Pinned sources used:

- LiteRT-LM tag `v0.17.1` = `5e58e9a0aef7abf7091207a8b1d1063a1c800f08`; the published AAR
  `com.google.ai.edge.litertlm:litertlm-android:0.17.1` from the Gradle cache (the app's own dependency).
- google-ai-edge/gallery `main` = `c7e9ccd4c75a476dc4e35b41fbac0a40298dadad` (2026-10-02).
- alibaba/MNN tag `3.6.1` = `d407447ed56c4121a11ccbd266dc184ca1ead0c2`; the app's bundled
  `app/src/main/jniLibs/arm64-v8a/libMNN.so` and `libmnnllmapp.so`.
- TensorFlow `d9a8da74b4c3de28a39ab34ad007838d6bc30c67` (LiteRT-LM's `TENSORFLOW_REF`, `WORKSPACE:10`), XNNPACK
  `8388bd78690515166d59f1b28e593a455a41d580` (TF's pin).
- AOSP tags `android-9.0.0_r1`, `android-14.0.0_r1`, `android-15.0.0_r1`, `android-16.0.0_r1`.
- Hugging Face `litert-community/gemma-4-E2B-it-litert-lm` @ `b3ca0d2f`, `gemma-4-E4B-it-litert-lm` @ `2eee7ac3`.

## 1. LiteRT-LM GPU backend on Android

**What it needs.**

- The Android GPU path is **OpenCL**. Google's LiteRT GPU page lists Android's backend as "OpenCL + OpenGL"
  and lists WebGPU (Vulkan) only for Linux [D3]. The Kotlin guide tells apps to request `libvndksupport.so` and
  `libOpenCL.so` with `<uses-native-library>` to use the GPU backend [L2 `getting_started.md:101-110`, D4].
- The 0.17.1 AAR's `liblitertlm_jni.so` names `libOpenCL.so`, `libOpenCL-pixel.so`, `libOpenCL-car.so`,
  `libvndksupport.so`, `android_load_sphal_library`, `libvulkan.so`, `libGLES_mali.so` and the delegate names
  `LITERT_CL` and `LITERT_WEBGPU`. It does not contain `LITERT_OPENGL` [L1, `strings`]. The loader order matches
  TFLite's open-source `opencl_wrapper.cc`: `libOpenCL.so` through the SP-HAL namespace (`dlopen`, then
  `android_load_sphal_library` from `libvndksupport.so`), then `libOpenCL-pixel.so` / `libOpenCL-car.so`
  with `enableOpenCL()`, then the ICD loader [T1 `opencl_wrapper.cc:54-78,120-160`].
- The WebGPU (Dawn → Vulkan) path is compiled in (`libwebgpu_dawn.so` and `libLiteRtWebGpuAccelerator.so` are in
  `prebuilt/android_arm64/` [L3]). Whether the runtime ever picks it on Android is a compile-time choice
  (`#if defined(LITERT_USE_WEBGPU_ACCELERATOR)`, with "TODO b/441627719 - Select backend by runtime options")
  [L4 `runtime/executor/llm_executor_settings_utils.cc:225-228`, `runtime/components/BUILD` `android_webgpu`].
  Every Android GPU log quoted by maintainers or reporters shows `LITERT_CL` [I3 #2114, I1 #1681, I8 #2611].
- There is **no automatic CPU fallback** when `Backend.GPU()` fails. A maintainer says the app should build its
  own device denylist: "we currently don't have plan to create a denylist for devices, and hopefully application
  can build such list on its own for its own model" [I6 #2460, ztenghui, 2026-07-18]. Engine creation, not
  `Backend.GPU()`, is where it fails [I2 #1860, I3 #2114].

**GPU families.**

| Family | What the primary sources say | Verdict for TAI |
|---|---|---|
| Qualcomm Adreno | The binary knows `adreno-4xx` … `adreno-8xx` and carries Qualcomm-only OpenCL extensions (`cl_qcom_dot_product8`, `cl_qcom_recordable_queues`, …). It warns: "Adreno 8xx with compiler E031.47.12.xxx is not well supported in MLDrift OpenCL backend. Various kernel compilation errors and incorrect results. Update your driver to a newer version." [L1]. The only Android benchmark Google publishes is a Samsung S26 Ultra GPU run [H1, H2, D5]. A maintainer: "Exynos GPU is known to be slow, compared to Qualcomm GPU" [I7 #1864]. | Supported; the family Google benchmarks. |
| Arm Mali / Immortalis (incl. Tensor G1–G4) | Gallery's `HardwareUtils.isOldMaliGpu` says "Pixel 9 and later should use GPU" and flags other `exynos`/`mali` boards as old Mali [G3 `common/HardwareUtils.kt:26-51`]. It is defined but never called in the public tree, so Gallery does **not** act on it today. Open user reports: Pixel 8 Pro (G3) "Can not find OpenCL library" (a contributor answered: add the `<uses-native-library>` lines) [I2 #1860]; Mali-G715 / Tensor G4 `CL_INVALID_COMMAND_QUEUE` after 1–3 turns [I9 #2421]; Gallery Pixel 8 Pro GPU hang [G4 #280]. | Usable from Tensor G4 / Immortalis; older Mali is risky. No maintainer statement either way. |
| Samsung Xclipse (Exynos, AMD RDNA) | User reports only: Xclipse 960 (Exynos 2600) fails engine init because Samsung's ANGLE-CL/Clspv rejects an MLDrift kernel [I3 #2114]; Xclipse 530 (A55) decodes garbage [I8 #2611]. Maintainer on Exynos 2600: "Exynos GPU is known to be slow … Try to use CPU for now." [I7 #1864, 2026-09-30]. | Treat as CPU-only. |
| Imagination PowerVR | The binary carries "Weights preparation on Gpu is disabled for PowerVR, Broadcom, Mali GPUs and non-OpenCL/WebGPU/Metal backends", "Async execution is not supported on Adreno and PowerVR", "Surface-less context is not properly supported on powervr" [L1]. User report: PowerVR BXM-8-256 (Dimensity 7020) loops garbage; the maintainer reply is the "no denylist" quote above [I6 #2460]. User report: MTP slows decode on PowerVR (Tensor G6) [I10 #2227]. | Treat as CPU-only unless measured. |
| Google Tensor G5 (Pixel 10) | Gallery strips GPU on any `Build.MODEL` containing "pixel 10" [G1 `data/ModelAllowlist.kt:129-132`, `ui/modelmanager/ModelImportDialog.kt:97-103`, `common/Utils.kt:366-368`]. It was added in commit `22f6b881` (2026-02-25, "Show compatible accelerators specified in the allowlist in benchmark page"), whose message gives no reason [G2]. The only stated reason is a Gallery collaborator on the Pixel 10 GPU hang thread: "this seems to be an issue with the GPU of the Pixel 10 device itself" [G4 #280, dpknag, 2025-09-23]. Gallery does **not** strip GPU on "pixel 11" (it has `isPixel11()` only for a storage workaround) [G1 `common/Utils.kt:370-385`]. | Keep TAI's rule (`TaiDeviceCapabilities.java:160,216,303`); it matches Gallery exactly (substring "pixel 10" in `Build.MODEL`). |

**Minimum Adreno or Mali generation.** None is published. The LiteRT GPU page names no vendor floor [D3]; the
LiteRT-LM docs and source name none [L2, L4]. The only generation-specific statements are the Adreno 8xx driver
warning and Gallery's unused "old Mali" helper above. → Unverified beyond that.

**Adreno 730 `-gpu` FP16 corruption.** No upstream issue matches it. There is no LiteRT-LM issue for Adreno 730
(SM8475) or for the `-gpu.litertlm` (GPU_ARTISAN) bundles on any Adreno. Nearest matches, all on the **standard**
`.litertlm` file: Adreno 750 corrupts German text on ~2k-token prompts with E4B, CPU clean (user report, open)
[I11 #3012]; S23 Ultra (Adreno 740) "Using GPU backend results in precision loss", unconfirmed [H3 E2B
discussion #35]; Adreno OpenCL output diverges from CPU for a MiniCPM bundle [I12 #3577]. The 0.17.1 runtime has an
`ActivationDataType` override in C++ (`executor_settings_base.h:63-80,338-345`, "OpenCL backend only support fp32
on Linux"), but the Kotlin `Backend.GPU()` takes no options [L5 `kotlin/.../Config.kt:137-158`]. So TAI's finding
(`voice-cleanup-benchmark-2026-09-27.md:87-88`) stays TAI's own.

## 2. LiteRT-LM CPU (XNNPACK): API, ABIs, CPU features

- **Minimum API: 24.** The 0.17.1 AAR manifest says `<uses-sdk android:minSdkVersion="24" />` [L1]. No doc page
  states one [D4, D5].
- **ABIs: `arm64-v8a` and `x86_64` only.** The AAR ships `jni/arm64-v8a/liblitertlm_jni.so` and
  `jni/x86_64/liblitertlm_jni.so` [L1]; prebuilts exist for `android_arm64` and `android_x86_64` only [L3]; the
  build guide says "we currently only support `arm64`" for the Android device runtime
  [L2 `docs/getting-started/build-and-run.md:315`]. **`armeabi-v7a` is not shipped.** `.bazelrc` still has an
  `android_arm` config (`:139-142`), but no artifact is published for it. TAI already gates on this
  (`TaiDeviceCapabilities.java:343`).
- **CPU features.** Nothing is *required* beyond baseline AArch64 NEON. XNNPACK compiles its dotprod, i8mm, SME
  and SME2 kernels by default on aarch64 and enables KleidiAI by default when i8mm is enabled
  [X1 `BUILD.bazel:1600-1662,2071-2087`]; KleidiAI is pinned by TF as an XNNPACK dependency
  [T2 `tensorflow/workspace2.bzl:259-265`]. At run time XNNPACK picks kernels from `cpuinfo`:
  `cpuinfo_has_arm_neon_dot`, `cpuinfo_has_arm_i8mm`, `cpuinfo_has_arm_sme`, `cpuinfo_has_arm_sme2`
  [X2 `src/configs/hardware-config.c:244-256`]. The 0.17.1 binary is stripped, so which kernels were actually
  linked cannot be read from it; it does contain `getauxval` and `/proc/cpuinfo` (cpuinfo's probes) [L1].
- LiteRT-LM also pins threads on Pixels: it reads `ro.soc.manufacturer` / `ro.soc.model` and maps
  Tensor G3/G4/G5/G6 to big/mid core sets [L4 `runtime/engine/cpu_affinity_utils.cc:56-82`].
- Google's own CPU numbers use the XNNPACK delegate with 4 threads [H1, H2].

## 3. LiteRT-LM NPU

- **SoCs (docs, last updated 2026-09-02) [D1]:** Google Tensor G5 and G6 (Gemma3-1B and **Gemma4-E2B**, 8192
  context); Qualcomm SM8750, SM8650, SM8550 (**Gemma3-1B only**, 1280 context); MediaTek MT6989 and MT6991
  (**Gemma3-1B only**); Intel PantherLake/LunarLake (desktop).
- **Files published for Gemma 4 [H4]:** E2B has `_Google_Tensor_G5`, `_Google_Tensor_G6`, `_qualcomm_sm8750`,
  `_qualcomm_qcs8275` (IoT), `_intel_LNL`, `_intel_PTL`. **E4B has no NPU file.**
- **So the existing note is right about the SoC list but wrong for Gemma 4:** SM8550/SM8650 and MT6989/MT6991 are
  listed for Gemma3-1B only. For Gemma 4 E2B on a phone, only Tensor G5/G6 (docs) and SM8750 (a Hugging Face file
  not in the docs table) exist; none for E4B.
- **Per-SoC model files: yes.** "You'll have to download the model that corresponds to your SoC" [D1]. LiteRT's
  NPU page: Qualcomm, MediaTek and Samsung support AOT and on-device (JIT) compilation, but "Google Tensor SDK
  doesn't yet support on-device (JIT) compilation" [D2]. Gallery hides NPU-only models when `Build.SOC_MODEL` is
  not a key of the model's `socToModelFiles` [G5 `ui/modelmanager/ModelManagerViewModel.kt:1302-1312`,
  `data/Consts.kt:78-84`].
- **Android version: API 31+, arm64-v8a only** ("API level 31+ is required for NPU support", "NPU only supports
  arm64-v8a") [D2]. Runtime libraries ship through Play Feature Delivery (`google_tensor_runtime`,
  `qualcomm_runtime_v69`…`v81`, `mediatek_runtime`, `samsung_runtime`) and AOT models through Play AI Packs [D2];
  the Qualcomm path also needs QAIRT libraries [D1]. A sideloaded app has no Play delivery, so NPU needs the vendor
  libraries bundled or present on the device.

## 4. MNN 3.6.1 LLM on Android

- **Minimum API.** Upstream builds `libMNN.so` at `android-21` [M1 `project/android/build_64.sh:11`];
  MnnLlmChat uses `minSdk 26`, `targetSdk 35` [M2 `apps/Android/MnnLlmChat/app/build.gradle:61-71`].
  **TAI's bundled bridge is stricter:** `libmnnllmapp.so` carries Android API **30** in `.note.android.ident`
  (`libMNN.so` carries 21) and has a strong undefined import of `AMEDIAFORMAT_KEY_SLICE_HEIGHT`, which the NDK marks
  `__INTRODUCED_IN(28)` [A-local: `readelf`, NDK r29 `media/NdkMediaFormat.h:204`]. The jniLibs README says "min
  API 30" (`app/src/main/jniLibs/arm64-v8a/README.md`). So `MNN_SDK_MINIMUM = 24`
  (`TaiDeviceCapabilities.java:29`) is too low for the shipped binary: it fails to load below API 28 and is only
  guaranteed from API 30. The app's own `minSdkVersion` is 26 (`gradle.properties:26`), so 24 never matters.
- **OpenCL.** MNN `dlopen`s by name and path: `libOpenCL.so`, `libGLES_mali.so`, `libmali.so`,
  `libOpenCL-pixel.so`, then `/system/vendor/lib64/libOpenCL.so`, `/system/lib64/libOpenCL.so`, the Mali `egl/`
  paths, `libPVROCL.so` [M3 `source/backend/opencl/core/runtime/OpenCLWrapper.cpp:38-56`]. No `libvndksupport`
  route. It calls `enableOpenCL`/`loadOpenCLPointer` when present (`:183-217`). It classifies the GPU from the CL
  device name (Adreno, Mali, …) and enables FP16 only when `cl_khr_fp16` is reported
  [M4 `OpenCLRuntime.cpp:139-190,282-284`]. Its FAQ says new Android versions may block the library and to add
  `<uses-native-library android:name="libOpenCL.so">` [M5 `docs/faq.md:215-225`]. No minimum GPU generation is
  stated. For the LLM, `thread_num` must be 68 on OpenCL and the first run tunes a cache
  [M6 `docs/transformers/llm.md:486,676-677`].
- **Vulkan.** MNN loads `libvulkan.so` itself [M7 `source/backend/vulkan/runtime/vulkan_wrapper.cpp:38-56`], but
  `MNN_VULKAN` is OFF by default [M8 `CMakeLists.txt:267`] and TAI's build does not turn it on
  (`.github/workflows/build_mnn_native.yml:83`). The LLM docs name only `cpu` and `opencl` for Android [M6 `:55,79`].
- **CPU features.** MNN reads `getauxval(AT_HWCAP)` for `asimddp` (dot) and `fphp|asimdhp` (FP16), and
  `getauxval(AT_HWCAP2)` for I8MM (bit 13), SVE2 and SME2 (bit 37) [M9 `source/backend/cpu/CPURuntime.cpp:36-44,
  1358-1382`]. `MNN_ARM82` (FP16) and `MNN_SME2` default ON; `MNN_KLEIDIAI` is ON but `MNN_KLEIDIAI_DEFAULT_ON` is
  OFF [M8 `CMakeLists.txt:85,268-271`], so KleidiAI kernels are compiled in but `enableKleidiAI` is false unless a
  runtime hint sets it [M10 `source/core/Backend.hpp:70-75`, `source/core/Session.cpp:118`]. The LLM engine sets
  `cpu_sme2_neon_division_ratio` (default 41) [M11 `transformers/llm/engine/src/llm.cpp:195`]. TAI's `libMNN.so`
  contains KleidiAI `sme2` and `neon_i8mm` kernels [A-local `strings`].

## 5. Runtime detection without root

**CPU features.**

- `Build.SOC_MODEL`, `Build.SOC_MANUFACTURER`: API 31 [A1 `api-versions.xml`]. They read `ro.soc.model` /
  `ro.soc.manufacturer`, labelled `soc_prop` [A2 `private/property_contexts:1690-1691` @16], which every domain may
  read [A3 `private/domain.te:502` @16]. Gallery and LiteRT-LM both key on them [G5 `data/Consts.kt:78-84`,
  L4 `cpu_affinity_utils.cc:70-71`].
- `/proc/cpuinfo` is readable by every domain [A3 `private/domain.te:244` @16; `public/domain.te:230` @9]. Its
  `Features` line uses the kernel names `asimddp`, `asimdhp`, `i8mm`, `svei8mm`, `sme`, `sme2`
  [K2 `arch/arm64/kernel/cpuinfo.c:61-122`].
- `getauxval(AT_HWCAP/AT_HWCAP2)` from JNI (`<sys/auxv.h>`, NDK); bits `HWCAP_ASIMDDP` (1<<20),
  `HWCAP2_I8MM` (1<<13), `HWCAP2_SME` (1<<23), `HWCAP2_SME2` (1UL<<37) [K1 `arch/arm64/include/uapi/asm/hwcap.h:46,
  91,101,115`; NDK r29 `asm/hwcap.h:54,78`]. Java has no `getauxval`; use JNI (MNN does exactly this [M9]).

**GPU vendor and model without a GL context.**

- **Vulkan, no surface needed.** `vkCreateInstance` + `vkEnumeratePhysicalDevices` +
  `vkGetPhysicalDeviceProperties` gives `vendorID` and `deviceName`. `libvulkan.so` is an NDK library, so no
  manifest entry is needed [D4]. `PackageManager.FEATURE_VULKAN_HARDWARE_VERSION` exists since API 24 [A1]. ANGLE's
  table maps the IDs: Qualcomm `0x5143`, ARM `0x13B5`, Imagination `0x1010`, Samsung `0x144D`
  [N1 `src/libANGLE/renderer/driver_utils.h:24-43,105-118`].
- **System property `ro.hardware.egl`** names the GLES driver the EGL loader opens (`libGLES_<value>.so`)
  [A4 `frameworks/native/opengl/libs/EGL/Loader.cpp:66-68,278` @16]. On Android 16 it is `exported_default_prop`
  (`private/property_contexts:1395`), readable by every domain (`private/domain.te:487`) [A2, A3]. On Android 9 it
  matches `ro.hardware.` → `vendor_default_prop` (`private/property_contexts:141` @9), and on devices built with
  compatible-property enforcement, apps may not read it (`public/domain.te:97-127` @9). Read it natively with
  `__system_property_get` (LiteRT-LM does the same for `ro.soc.*`).
- **EGL/GLES `glGetString(GL_RENDERER)`** works only with a current context; without one it returns null, which
  is exactly how LiteRT-LM misidentified a Pixel 10 GPU as PowerVR (user report, closed) [I1 #1681]. A
  short-lived pbuffer or surfaceless context (`EGL14`, API 17 [A1]) is needed.
- `Build.SOC_MODEL` → GPU needs a lookup table we maintain; no API returns the GPU from the SoC.

**OpenCL availability.**

- Apps targeting **API 30 or lower** may load any vendor *public* native library without declaring it; from
  target 31 they must list it in `<uses-native-library>` [D4; A5 "Apps that target SDK version 31 … must
  explicitly specify"]. Vendor public libraries are those listed in `/vendor/etc/public.libraries.txt` (silicon
  vendors, since Android 7.0) or `/system/etc/public.libraries-COMPANYNAME.txt` (OEMs, since Android 9), and the
  feature applies to apps targeting SDK 24+ [A5].
- TAI targets **SDK 28** (`gradle.properties:27`), so the four `<uses-native-library>` entries
  (`AndroidManifest.xml:119-130`) are inert today and become load-bearing the day `targetSdkVersion` reaches 31.
- `/vendor/etc/*` is labelled `vendor_configs_file` [A6 `private/file_contexts:427` @16, `:304` @9] and every domain
  may read it [A3 `private/domain.te:177-178` @16, `public/domain.te:183-184` @9]. So an app can read
  `/vendor/etc/public.libraries.txt` and look for `libOpenCL.so` (vendors may relabel; not checked per OEM).
- The definitive probe is a JNI `dlopen("libOpenCL.so")` followed by `clGetPlatformIDs`, the same sequence
  LiteRT and MNN use [T1, M3]. Samsung One UI may block `libvndksupport.so` from app namespaces (user report)
  [I4 #2292]; TFLite's loader tries plain `dlopen` first, so that alone does not block OpenCL [T1 `:54-58`].

## 6. Android versions

- **AGSL `RuntimeShader` = API 33; `HardwareBufferRenderer` = API 34** [A1]. Why TAI gates live effects at 34:
  the renderer is `HardwareBufferRenderer` (API 34) and the spec chose one path: "**Decided (2026-09-30): API 34+
  only, one path.** The `ImageReader` path is not built" (`project-docs/active/animated-wallpaper/SPEC.md:104-116`,
  §13 Q1 at `:435`). `LivingEffectsRenderer` is `@RequiresApi(34)` and holds `HardwareBufferRenderer`s
  (`LivingEffectsRenderer.java:38,52,102`). The lock live wallpaper also reads
  `WallpaperManager.getWallpaperInfo(int)`, API 34 [A1] (`WallpaperSlots.java:245-249`), and relies on the
  keyguard-going-away command "Android 14+ sends to wallpaper engines" (`LockLiveWallpaperService.java:39-50`).
  Fancier Glass's own AGSL floor stays 33 (`generated-backgrounds-issue.md:188-195`).
- **`getFreeMemory` spot-check: confirmed.** `android_os_Process_getFreeMemory` sums `MemFree:` + `Cached:` at
  android-9.0.0_r1 (`core/jni/android_util_Process.cpp:652-656`) and `kMemFree` + `kMemCached` at
  android-15.0.0_r1 (`:657-662`); it reads `kMemAvailable` at android-16.0.0_r1 (`:659-662`) [A7].
  `ProcessList.getMemoryInfo` sets `outInfo.availMem = getFreeMemory()` at android-14.0.0_r1 (`:1622`) and
  android-16.0.0_r1 (`:1737`) [A8].
- **`ApplicationExitInfo` = API 30**, as is `ActivityManager.getHistoricalProcessExitReasons` [A1].
  `MemoryInfo.advertisedMem` = API 34 [A1].
- **`/proc/meminfo` from an untrusted app, Android 9–16: yes.** `allow appdomain proc_meminfo:file r_file_perms;`
  at android-9.0.0_r1 (`public/app.te:327`) and android-16.0.0_r1 (`private/app.te:481`); `/proc/meminfo` is
  labelled `proc_meminfo` (`private/genfs_contexts:24` @16) [A9]. No neverallow in `app_neverallows.te` or
  `untrusted_app_all.te` at 16. Tags 10–15 were not read; OEM policy was not checked.
- **Foreground services for long downloads or loads.**
  - From Android 14, apps **targeting API 34+** must declare a type and its permission for every FGS; `dataSync`
    covers upload/download/import/"local file processing"; `specialUse` needs a declared subtype that Play
    reviews; `shortService` lasts about 3 minutes [D6]. Types: `DATA_SYNC` API 29, `SHORT_SERVICE` /
    `SPECIAL_USE` API 34, `MEDIA_PROCESSING` API 35 [A1].
  - Apps **targeting API 35+** get a 6-hours-per-24 limit for `dataSync` and `mediaProcessing`, tracked per type
    and reset when the user brings the app to the foreground; on expiry `Service.onTimeout(int,int)` (API 35)
    gives a few seconds to `stopSelf()`, else a crash/ANR; new starts throw
    `ForegroundServiceStartNotAllowedException` [D7, D6]. Apps targeting 35+ may not start a `dataSync` FGS from
    `BOOT_COMPLETED` [D6].
  - User-initiated data transfer jobs (API 34, `RUN_USER_INITIATED_JOBS`, must be scheduled while visible) are
    meant for "downloading a file from a remote server" and are exempt from standby-bucket quotas [D8].
  - All of these are **target-gated**. At `targetSdkVersion=28` none applies to TAI today. A model *load* is not a
    data transfer: no FGS type fits it except `specialUse`, so loads should stay in-process while the launcher
    runs, as they do now.

## 7. Which SoC families give a usable LiteRT-LM GPU path for Gemma 4 E2B/E4B today

Published benchmarks only (model cards [H1, H2], Google's LiteRT-LM overview [D5]). No Qualcomm, Arm, Samsung or
MediaTek page with Gemma 4 LiteRT-LM numbers was found.

| Model | Device (only Android entry) | Backend | Prefill tok/s | Decode tok/s | TTFT s | Memory MB |
|---|---|---|---|---|---|---|
| E2B (2583 MB) | Samsung S26 Ultra | CPU | 557 | 46.9 | 1.8 | 1733 |
| E2B | Samsung S26 Ultra | GPU | 3,808 | 52.1 | 0.3 | 676 |
| E4B (3654 MB) | Samsung S26 Ultra | CPU | 195 | 17.7 | 5.3 | 3283 |
| E4B | Samsung S26 Ultra | GPU | 1,293 | 22.1 | 0.8 | 710 |

1024 prefill + 256 decode tokens, 2048 context, CPU on XNNPACK with 4 threads, caches warm [H1, H2]. The cards
do not name the S26 Ultra's SoC.

Summary:

- **Published and usable:** only the S26 Ultra. Its SoC is not named in the source (→ Unverified). Google benchmarks
  no other Android phone.
- **Supported by design, unpublished:** Qualcomm Adreno in general. The maintainer calls Qualcomm GPUs fast
  relative to Exynos [I7]. TAI's own pong numbers (SM8475 / Adreno 730) are the only Adreno 7xx data we have.
- **Usable with care:** Mali/Immortalis from Tensor G4 on (Gallery's heuristic [G3]). Open G4 queue-loss reports
  exist [I9].
- **Not usable today:** Pixel 10 / Tensor G5 (Gallery strips the GPU [G1]); Exynos Xclipse (maintainer: use the
  CPU [I7]); PowerVR (no maintainer support, user reports of garbage [I6]).
- **Memory rule from Gallery:** E2B `minDeviceMemoryInGb: 8`, E4B `12`; both list `gpu,cpu` with `gpu` first
  and a GPU vision encoder [G6 `model_allowlists/1_0_19.json`].

## Rules for the tier design

1. Gate the GPU per GPU family, not only per model string. Order: Adreno → GPU first; Mali/Immortalis Tensor G4+
   → GPU first, CPU after one failure; Mali before that, Xclipse, PowerVR → CPU [I7, I6, G3, L1].
2. Keep the Pixel 10 rule as Gallery has it (substring "pixel 10" in `Build.MODEL`) [G1, G2, G4].
3. Find the GPU family at startup without GL: Vulkan `vendorID`, else `ro.hardware.egl`, else `Build.SOC_MODEL`
   (API 31+) through our own table [N1, A2, A3, A4, A1].
4. Probe OpenCL before offering GPU: JNI `dlopen("libOpenCL.so")` + `clGetPlatformIDs`; GPU init can still fail
   late, so every GPU load keeps a CPU route and records the failure [T1, I2, I3].
5. Keep the `<uses-native-library>` entries; they matter once `targetSdkVersion` ≥ 31 [D4, A5].
6. Warn or fall back on Adreno 8xx with GL compiler `E031.47.12.*` (LiteRT's own warning) [L1].
7. Read CPU features (dotprod, i8mm, sme2) for bench labels only; never gate on them. Both runtimes dispatch at run
   time [X1, X2, M9].
8. LiteRT-LM: arm64-v8a and x86_64 only, API 24+; never offer it on armeabi-v7a [L1, L3].
9. Raise `MNN_SDK_MINIMUM` to 30, the bundled bridge's build level; it cannot load below 28 [A-local, NDK].
10. NPU is not a tier input. Gemma 4 E2B NPU exists only for Tensor G5/G6 (+ SM8750 file); it needs API 31, a
    per-SoC file and vendor runtime libraries; E4B has none [D1, D2, H4].
11. Below API 36 read `MemAvailable` from `/proc/meminfo`; apps may read it on 9–16 [A7, A8, A9].
12. Keep live effects at API 34 (HardwareBufferRenderer, `getWallpaperInfo(int)`) [A1, SPEC.md:104-116].
13. FGS limits are target-gated and do not bind at target 28. When the target rises: downloads as UIDT (34+) or
    `dataSync` within 6 h/24 h (35+); loads stay in-process [D6, D7, D8].
14. Only the S26 Ultra has published Gemma 4 GPU numbers; every other phone's GPU speed must come from TAI's own
    bench [H1, H2, D5].
15. The Adreno 730 `-gpu` corruption has no upstream issue; keep the ban as TAI's own finding [I11, H3].

## Sources

- [L1] litertlm-android 0.17.1 AAR (`~/.gradle/caches/modules-2/files-2.1/com.google.ai.edge.litertlm/litertlm-android/0.17.1/9c8ab948…/litertlm-android-0.17.1.aar`): `AndroidManifest.xml`, `jni/*/liblitertlm_jni.so` (`readelf`, `strings`).
- [L2] https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/docs/api/kotlin/getting_started.md (lines 101-121); `docs/getting-started/build-and-run.md:315`.
- [L3] https://github.com/google-ai-edge/LiteRT-LM/tree/v0.17.1/prebuilt (android_arm64, android_x86_64).
- [L4] https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/runtime/executor/llm_executor_settings_utils.cc (225-228); `runtime/components/BUILD` (`android_webgpu`); `runtime/engine/cpu_affinity_utils.cc` (56-82).
- [L5] https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt (137-158); `runtime/executor/executor_settings_base.h` (63-80, 338-345).
- [G1] https://github.com/google-ai-edge/gallery/blob/c7e9ccd4c75a476dc4e35b41fbac0a40298dadad/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/ModelAllowlist.kt (129-132); `ui/modelmanager/ModelImportDialog.kt` (97-103); `common/Utils.kt` (366-385).
- [G2] https://github.com/google-ai-edge/gallery/commit/22f6b881324d459d3051d8f84332dc2c20f043f9
- [G3] https://github.com/google-ai-edge/gallery/blob/c7e9ccd4c75a476dc4e35b41fbac0a40298dadad/Android/src/app/src/main/java/com/google/ai/edge/gallery/common/HardwareUtils.kt (26-51; added 9e491873, 2026-09-10).
- [G4] https://github.com/google-ai-edge/gallery/issues/280 (collaborator dpknag, 2025-09-23); related #309, #682.
- [G5] Gallery `data/Consts.kt` (78-84), `ui/modelmanager/ModelManagerViewModel.kt` (1302-1312), `huggingface/HfModelUtils.kt` (115-170) at `c7e9ccd4`.
- [G6] https://github.com/google-ai-edge/gallery/blob/c7e9ccd4c75a476dc4e35b41fbac0a40298dadad/model_allowlists/1_0_19.json
- [I1] https://github.com/google-ai-edge/LiteRT-LM/issues/1681 · [I2] …/issues/1860 (contributor whhone, 2026-04-13) · [I3] …/issues/2114 · [I4] …/issues/2292 · [I6] …/issues/2460 (collaborator ztenghui, 2026-07-18) · [I7] …/issues/1864 (collaborator ztenghui, 2026-09-30) · [I8] …/issues/2611 · [I9] …/issues/2421 · [I10] …/issues/2227 · [I11] …/issues/3012 · [I12] …/issues/3577
- [H1] https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/README.md (lines 38-67)
- [H2] https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/blob/2eee7ac325f20eb8c9ac1d0e972f7c84663062da/README.md (lines 38-67)
- [H3] https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/discussions/35
- [H4] https://huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm and …/gemma-4-E4B-it-litert-lm (file lists, read 2026-10-05).
- [D1] https://developers.google.com/edge/litert/next/litert_lm_npu (last updated 2026-09-02)
- [D2] https://developers.google.com/edge/litert/next/npu (last updated 2026-08-26)
- [D3] https://developers.google.com/edge/litert/next/gpu (last updated 2026-05-28)
- [D4] https://developer.android.com/guide/topics/manifest/uses-native-library-element (last updated 2026-10-01); https://developers.google.com/edge/litert-lm/android (2026-09-04)
- [D5] https://developers.google.com/edge/litert-lm/overview (last updated 2026-09-04)
- [D6] https://developer.android.com/develop/background-work/services/fgs/service-types (2026-10-01)
- [D7] https://developer.android.com/develop/background-work/services/fgs/timeout (2026-10-01)
- [D8] https://developer.android.com/develop/background-work/background-tasks/uidt (2026-10-01)
- [M1] https://github.com/alibaba/MNN/blob/3.6.1/project/android/build_64.sh · [M2] …/apps/Android/MnnLlmChat/app/build.gradle · [M3] …/source/backend/opencl/core/runtime/OpenCLWrapper.cpp · [M4] …/source/backend/opencl/core/runtime/OpenCLRuntime.cpp · [M5] …/docs/faq.md · [M6] …/docs/transformers/llm.md · [M7] …/source/backend/vulkan/runtime/vulkan_wrapper.cpp · [M8] …/CMakeLists.txt · [M9] …/source/backend/cpu/CPURuntime.cpp · [M10] …/source/core/Backend.hpp, …/source/core/Session.cpp · [M11] …/transformers/llm/engine/src/llm.cpp
- [T1] https://github.com/tensorflow/tensorflow/blob/d9a8da74b4c3de28a39ab34ad007838d6bc30c67/tensorflow/lite/delegates/gpu/cl/opencl_wrapper.cc
- [T2] https://github.com/tensorflow/tensorflow/blob/d9a8da74b4c3de28a39ab34ad007838d6bc30c67/tensorflow/workspace2.bzl; `third_party/xla/third_party/xnnpack/workspace.bzl`
- [X1] https://github.com/google/XNNPACK/blob/8388bd78690515166d59f1b28e593a455a41d580/BUILD.bazel · [X2] …/src/configs/hardware-config.c
- [K1] https://github.com/torvalds/linux/blob/a90ee4305c4a5df72c11b31dacfdc76e00fcf78a/arch/arm64/include/uapi/asm/hwcap.h · [K2] …/arch/arm64/kernel/cpuinfo.c
- [N1] https://chromium.googlesource.com/angle/angle/+/756d31051f12e93630ae51fd5e0d03deb6958739/src/libANGLE/renderer/driver_utils.h
- [A1] Android SDK `platforms/android-36/data/api-versions.xml` (local SDK).
- [A2] https://android.googlesource.com/platform/system/sepolicy/+/refs/tags/android-16.0.0_r1/private/property_contexts; same file at android-9.0.0_r1.
- [A3] …/sepolicy/+/refs/tags/android-16.0.0_r1/private/domain.te; …/android-9.0.0_r1/public/domain.te
- [A4] https://android.googlesource.com/platform/frameworks/native/+/refs/tags/android-16.0.0_r1/opengl/libs/EGL/Loader.cpp
- [A5] https://source.android.com/docs/core/permissions/namespaces_libraries (last updated 2026-07-13)
- [A6] …/sepolicy/+/refs/tags/android-16.0.0_r1/private/file_contexts; android-9.0.0_r1 same path.
- [A7] https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r1/core/jni/android_util_Process.cpp (also at android-9.0.0_r1, android-15.0.0_r1)
- [A8] …/frameworks/base/+/refs/tags/android-16.0.0_r1/services/core/java/com/android/server/am/ProcessList.java (also android-14.0.0_r1)
- [A9] …/sepolicy/+/refs/tags/android-16.0.0_r1/private/app.te, private/genfs_contexts; …/android-9.0.0_r1/public/app.te
- [A-local] `app/src/main/jniLibs/arm64-v8a/libMNN.so`, `libmnnllmapp.so` (`readelf -n`, `readelf --dyn-syms`, `strings`); NDK r29 sysroot headers `media/NdkMediaFormat.h`, `sys/auxv.h`, `asm/hwcap.h`.
- [V1] https://www.arm.com/products/mobile/compute-subsystems/lumex ("SME2-enabled Arm C1 CPU cluster"; C1-Ultra/Premium/Pro/Nano are Armv9.3-A).

## Unverified

- Whether the 0.17.1 AAR ever selects the WebGPU/Vulkan GPU path on Android. The flag is compile-time and the
  build config of the published AAR is not public. Observed logs show `LITERT_CL` only.
- The GPU inside Tensor G5 and G6. Reporters disagree (#1681 says Immortalis-G720; #2227 says PowerVR for G6); Google's
  sources found here do not name it.
- The real reason Gallery removes the GPU on Pixel 10, beyond the collaborator's "issue with the GPU of the Pixel 10
  device itself". Whether that still holds on current Pixel 10 builds.
- Any minimum Adreno or Mali generation for LiteRT-LM's GPU path.
- The SoC in the S26 Ultra used for the model-card numbers. The cards and the overview do not name it.
- Gemma 4 LiteRT-LM GPU numbers from Qualcomm, Arm, Samsung or MediaTek. None found; web search on vendor domains
  returned nothing.
- Which phone SoCs ship SME2 (Arm's Lumex page says the C1 cluster has it; per-SoC vendor confirmation not read),
  and from which kernel version Android exposes `HWCAP2_SME2`.
- Whether Pixel phones list `libOpenCL.so` in `/vendor/etc/public.libraries.txt`. #1860's fix suggests yes for
  Pixel 8 Pro, but no file was read.
- Samsung blocking `libvndksupport.so` from app namespaces (#2292 is a user report).
- `/proc/meminfo` and `/vendor/etc` readability on tags 10–15 and under OEM sepolicy; read only at 9 and 16.
- When `ro.hardware.egl` became `exported_default_prop` (Android 9 has `vendor_default_prop`; 16 has
  `exported_default_prop`; versions between were not read).
- Whether XNNPACK in the published LiteRT-LM AAR actually links the KleidiAI/SME2 kernels. Defaults say yes; the
  binary is stripped.
- Upstream MNN 3.6.1 has no stated minimum Android API for the LLM engine beyond the build scripts' `android-21`
  and MnnLlmChat's `minSdk 26`.
