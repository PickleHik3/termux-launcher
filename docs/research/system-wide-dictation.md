# System-wide dictation: exposing the launcher's on-device speech-to-text to other apps

Research note. Researched 2026-10-22 against `v2-redesign` @ `0c90c8c23`. No code was changed.

Method: Android claims are read from the AOSP source on `android.googlesource.com` (branch `main`, fetched as raw text, so every quote is verbatim) or from `developer.android.com` / `support.google.com` pages. The `developer.android.com/reference` pages are too large for the fetch tool to read past their navigation, so the class-level facts below cite the AOSP source of the same class instead; the reference page is linked beside it for the reader. Prior-art claims were read from shallow clones of each repo, pinned to the commit in the "Prior art" table. Anything I could not confirm from a primary source is marked **unverified**. Anything I infer is marked **[inference]**. Line numbers in AOSP links are as of the fetch and drift; the symbol name is the stable handle.

## Summary

- The launcher's dictation is **not** a service or an IME. It is an app-owned pipeline: `AudioRecord` + Silero VAD in the app process, then Whisper ACFT or Parakeet in a separate `:tai_runtime` process, driven from the in-app keyboard's voice key. The in-app keyboard is "not an Android input method". It already exposes nothing to other apps, but it already *consumes* `ACTION_RECOGNIZE_SPEECH` from other recognizers.
- Android offers three routes that let other apps use a dictation engine: a **voice IME** (`InputMethodService` with a `voice` subtype), an **`ACTION_RECOGNIZE_SPEECH` activity**, and a **`RecognitionService`**. They reach different callers and the first two work today without any settings change on most devices.
- The best fit is **a voice-only IME plus an `ACTION_RECOGNIZE_SPEECH` activity**, both thin shells over the existing engine, with a `RecognitionService` as a later, optional third. Reasons are in section 3.
- A `RecognitionService` is the most generic route, but a user can only make a third-party one the default through a settings path that I could not confirm exists on current stock Android, and `createOnDeviceSpeechRecognizer` can never reach it.
- `VoiceInteractionService` is overkill. An `AccessibilityService` is feasible but a poor fit: Play policy says launchers and assistants do not qualify as accessibility tools, and the app already carries one `AccessibilityService` for screen lock.
- The launcher's transcriber is **phrase-batch, not streaming** (`SttRuntime.transcribe(File audio, ...)`), so partial results are phrase-level, not word-level. That shapes every route.

## 1. Current state in this repo

**Engine.** Confirmed: Silero VAD plus on-device ASR, no cloud.

| Piece | What it is | Where |
| --- | --- | --- |
| Mic capture | `AudioRecord` on `VOICE_RECOGNITION`, 16 kHz mono PCM16 | `app/src/main/java/com/termux/app/terminal/inappkeyboard/voice/VoiceInputSession.java:224` |
| VAD | Silero VAD v5, LiteRT (`.tflite`, about 1.2 MB, bundled), 30 ms frames re-chunked to 512-sample windows | `.../inappkeyboard/voice/SileroVad.java:20-31`, asset `app/src/main/assets/vad/silero_vad_v5.tflite`, decider `SileroVoiceDecider.java:31` |
| Phrase segmentation | VAD closes a phrase; PCM is written to `cacheDir/tai-ipc/stt-<uuid>.pcm` and sent to the runtime | `VoiceInputSession.java:403-460`, `:537-541`, `TaiManager.STT_IPC_DIR` at `app/src/main/java/com/termux/ai/TaiManager.java:55` |
| ASR interface | `SttRuntime.transcribe(spec, File audioFile, language, languageHint, biasText)` returns JSON. **File in, text out; no streaming API.** `warm()` preloads the model | `app/src/main/java/com/termux/ai/SttRuntime.java:23-50` |
| ASR engines | Whisper ACFT (LiteRT `.tflite`, base/small, multilingual and `.en`) and Parakeet TDT 0.6b v3 | `WhisperSttRuntime.java:45`, `ParakeetSttRuntime.java`, catalog `TaiModelCatalog.java:18`, `:223-270` |
| Runtime process | Models run in a separate process `:tai_runtime`, reached over a `Messenger` | `app/src/main/AndroidManifest.xml:373-377` (`android:process=":tai_runtime"`, `foregroundServiceType="specialUse"`), `TaiRuntimeService.java:17`, `:130` |
| Model storage | Downloaded (not bundled) to `getFilesDir()/tai/models/<id>/`; catalog entries pin a Hugging Face revision, size and sha256; downloads run in `TaiModelDownloadService` | `TaiModelStore.java:86`, `TaiModelCatalog.java:223-232`, `AndroidManifest.xml:357-360` |
| Post-processing | Local LLM cleanup, command formatter, fillers, "dictation card" UI | `.../inappkeyboard/voice/VoiceDictation.java:23`, `VoiceTextPolisher.java`, user doc `docs/en/Voice_Input.md` |
| Mic permission | `RECORD_AUDIO` declared, plus `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`. **No `FOREGROUND_SERVICE_MICROPHONE`** | `AndroidManifest.xml:53,54,69` |
| SDK levels | `minSdk 26`, **`targetSdk 28`**, `compileSdk 36` | `gradle.properties:29-32` |

**Is the in-app keyboard an `InputMethodService`? No.** `inapp-keyboard/UPSTREAM.md` states: "Snapshot purpose: render the keyboard as an ordinary app-owned Android `View`; this module is not an Android input method." The only `InputMethodService` references in `app/` and `termux-shared/` are Javadoc links (`TermuxActivityRootView.java:5`, `KeyboardUtils.java:6`). The manifest declares no IME service.

**What the app already does with the system recognizer.** With **Speech engine = Android system** (and as a fallback), the voice key fires `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with `LANGUAGE_MODEL_FREE_FORM` (`TermuxActivity.java:19257-19275`, `createVoiceTypingIntent` at `:20034`), and swipe-and-hold shows the system chooser (`docs/en/Voice_Input.md`, "The voice key"). So this app is already a *client* of the same activity route this note proposes to *serve*. A user who installs FUTO or Transcribro could today point the launcher at it.

**Other relevant facts.**
- The app already declares one `AccessibilityService`, `LockAccessibilityService` (`AndroidManifest.xml:388-397`), and ships Shizuku (`:400-402`).
- Glossary/ADRs: `docs/GLOSSARY.md:615-671` (Voice input, Dictation, Dictate key, dictation card). No ADR in `docs/adr/` covers voice. The product stance that the on-device engine is the default once a speech model is installed is in `docs/en/Voice_Input.md` (intro).

## 2. What Android offers

### 2a. `RecognitionService` (the `SpeechRecognizer` back end)

**Declaration.**
- The service "must declare as handled" the action `android.speech.RecognitionService`; its metadata key is `android.speech` and must point at an XML resource whose root is `<recognition-service>` or `<on-device-recognition-service>` ([`RecognitionService.java`: `SERVICE_INTERFACE`, `SERVICE_META_DATA`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/speech/RecognitionService.java); [reference](https://developer.android.com/reference/android/speech/RecognitionService)).
- The XML's attributes are `settingsActivity` and `selectableAsDefault` ("Flag indicating whether a recognition service can be selected as default. The default value of this flag is true") ([`core/res/res/values/attrs.xml`, `RecognitionService` styleable](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/attrs.xml)).
- There is **no `BIND_RECOGNITION_SERVICE` permission**: the platform manifest defines `BIND_INPUT_METHOD`, `BIND_VOICE_INTERACTION` and `MANAGE_SPEECH_RECOGNITION`, and no recognition-bind permission ([`core/res/AndroidManifest.xml`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)). All three prior-art services guard theirs with `android:permission="android.permission.RECORD_AUDIO"` (FUTO, Sayboard, Transcribro, Dicio manifests, see section 2f). The reason for that convention is **unverified** [inference: only callers holding the mic permission can bind].
- Methods to implement: `onStartListening(Intent, Callback)`, `onStopListening`, `onCancel`. Optional: `onCheckRecognitionSupport` (default answers `ERROR_CANNOT_CHECK_SUPPORT`) and `onTriggerModelDownload` (default does nothing; a listener overload exists whose default answers `ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS`) (`RecognitionService.java`, same link).
- Results go through `Callback.partialResults(Bundle)` and `Callback.results(Bundle)`; the service is documented as "stateless" and the platform caps concurrent sessions (`getMaxConcurrentSessionsCount`, `ERROR_RECOGNIZER_BUSY`) (same file).

**Permission enforcement is done by the platform, per caller.** Before delivering data, `RecognitionService` preflights and then calls `PermissionChecker.checkPermissionAndStartDataDelivery(..., RECORD_AUDIO, <caller's AttributionSource>)`; if the *client* lacks the mic permission the client receives `ERROR_INSUFFICIENT_PERMISSIONS` (same file, `dispatchStartListening`, `checkPermissionAndStartDataDelivery`). A service that records audio itself should open `AudioRecord` in a context built from the caller's attribution: `createContext(ContextParams.Builder().setNextAttributionSource(callback.getCallingAttributionSource()).build())`, which is what Transcribro does (`MainRecognitionService.kt:117-124`, pinned in section 2f). Whether skipping that is harmful is **unverified**.

**How clients reach it.**
- `SpeechRecognizer.createSpeechRecognizer(Context)` resolves the component from `Settings.Secure.VOICE_RECOGNITION_SERVICE`; if it is empty the client gets `ERROR_CLIENT` ("no selected voice recognition service") ([`SpeechRecognizerImpl.java`, `getSpeechRecognizerComponentName`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/speech/SpeechRecognizerImpl.java)). `createSpeechRecognizer(Context, ComponentName)` lets a client name a specific service (`SpeechRecognizer.java`, [reference](https://developer.android.com/reference/android/speech/SpeechRecognizer)).
- The setting itself: `VOICE_RECOGNITION_SERVICE = "voice_recognition_service"`, "The ComponentName string of the service to be used as the voice recognition service", marked `@hide` with `@UnsupportedAppUsage(maxTargetSdk = R)` ([`Settings.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/Settings.java)).
- `createOnDeviceSpeechRecognizer` can **never** reach a third-party app. `isOnDeviceRecognitionAvailable` is true only if the OEM resource `config_defaultOnDeviceSpeechRecognitionService` parses to a component, and `SpeechRecognizerImpl` documents that choice as "determined by the manufacturer and not changeable by the user" (`SpeechRecognizer.java:311`, `SpeechRecognizerImpl.java` comment above `getSpeechRecognizerComponentName`).
- `isRecognitionAvailable` is just "does any service match `android.speech.RecognitionService`" (`SpeechRecognizer.java:295`).
- **Package visibility.** For client apps targeting API 30+, the `SpeechRecognizer` Javadoc says binding "requires `<queries>`" with `<intent><action android:name="android.speech.RecognitionService"/></intent>` (`SpeechRecognizer.java:322-340`, [package-visibility docs](https://developer.android.com/training/package-visibility/declaring)). **Pitfall seen in the wild:** Sayboard's manifest says "The app calling the SpeechRecognitionService gets ERROR_INSUFFICIENT_PERMISSIONS if we can't query for them (even if they have microphone permission)" and requests `QUERY_ALL_PACKAGES` as a workaround, citing [Kõnele-service issue 9](https://github.com/Kaljurand/K6nele-service/issues/9) (Sayboard `AndroidManifest.xml:13-17`, README "QUERY_ALL_PACKAGES"). Whether that is still true on current Android is **unverified**. This is a trap for a Play-distributed app because `QUERY_ALL_PACKAGES` is restricted there (**unverified**, not read on a Play page).
- `checkRecognitionSupport` and `triggerModelDownload` exist on the client class and are forwarded to the service's `onCheckRecognitionSupport` / `onTriggerModelDownload` (`SpeechRecognizer.java:504,519,552`). Their API levels (33 for support, 34 for the listener variant, 31 for `createOnDeviceSpeechRecognizer`) are from memory; the reference pages' "Added in API level" lines were not readable: **unverified**.

**How the user picks the default.** This is the weak link.
- The platform resolves a default when none is set: `findAvailRecognizer` takes the `config_systemSpeechRecognizer` package if present, prefers recognizers that are `selectableAsDefault`, else logs "more than one voice recognition service found, picking first" ([`VoiceInteractionManagerService.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerService.java)).
- AOSP Settings had a picker, `DefaultVoiceInputPicker` / `VoiceInputHelper`, at `src/com/android/settings/applications/assist/`. Both files exist at tags `android-11.0.0_r1` and `android-12.0.0_r1` and return 404 at `android-13.0.0_r1`, `-14.0.0_r1`, `-15.0.0_r1`, `-16.0.0_r1` and `main` ([Settings at android-12.0.0_r1](https://android.googlesource.com/platform/packages/apps/Settings/+/android-12.0.0_r1/src/com/android/settings/applications/assist/DefaultVoiceInputPicker.java); the same path at `android-13.0.0_r1` is a 404). They may have moved; I did not find where. So **I could not confirm any AOSP Settings UI that lets a user choose a third-party recognizer on Android 13+: unverified.**
- For `VoiceInteractionService`, `recognitionService` in its XML was "required... On Android 11 and earlier, this must be a valid RecognitionService. From Android 12 onward, this attribute does nothing" (`attrs.xml`, `VoiceInteractionService` styleable). So choosing a digital assistant no longer picks a recognizer on 12+.
- Pixel and Samsung Settings: I could not read either vendor's Settings source. FUTO's README lists keyboards that cannot use its voice input but says nothing about a Settings picker ([FUTO README](https://github.com/futo-org/voice-input/blob/d6e1eb2d139dc1a6342a4681c283686cca4bfceb/README.md)); Transcribro's README says it works "when set as the user-selected speech to text app which some apps may use" without telling how ([Transcribro README](https://github.com/soupslurpr/Transcribro/blob/c5f159e2fc3218fbee88ac22840ba986f741046d/README.md)). **Whether Pixel or Samsung expose a picker: unverified.**
- A privileged path exists in principle: the setting is a plain `Settings.Secure` string, and the launcher already ships Shizuku. Whether `Settings.Secure.putString` on that key is permitted with `WRITE_SECURE_SETTINGS` (grantable over adb or Shizuku), and whether the system accepts it, I did not test: **unverified**. [inference: a "Set as system recognizer" button via Shizuku/adb is plausible.]

### 2b. `ACTION_RECOGNIZE_SPEECH` activity

- The action `android.speech.action.RECOGNIZE_SPEECH` "starts an activity that will prompt the user for speech and send it through a speech recognizer"; required extra `EXTRA_LANGUAGE_MODEL`; optional `EXTRA_PROMPT`, `EXTRA_LANGUAGE`, `EXTRA_MAX_RESULTS`, `EXTRA_RESULTS_PENDINGINTENT(_BUNDLE)`; the result extra is `EXTRA_RESULTS` (ordered `ArrayList<String>`); the Javadoc warns "There may not be any applications installed to handle this action, so you should make sure to catch `ActivityNotFoundException`" ([`RecognizerIntent.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/speech/RecognizerIntent.java), [reference](https://developer.android.com/reference/android/speech/RecognizerIntent)).
- Declaring it is an ordinary exported activity with an intent filter for that action and `CATEGORY_DEFAULT`; FUTO, Transcribro and Dicio all do so (manifests, section 2f). Client apps on API 30+ that call `resolveActivity` need a matching `<queries>` entry (`<intent>` element, [package-visibility docs](https://developer.android.com/training/package-visibility/declaring)); a plain `startActivityForResult` needs none.
- The launcher itself is a client of this route (section 1), which is a ready-made end-to-end test.
- Which handler wins when several are installed is the system chooser. FUTO documents that Microsoft SwiftKey and some other keyboards use this intent and "opens the floating window in the center of the screen" (FUTO README "API support").
- Limits: result delivery is a single final `ArrayList<String>`; partials are not part of the contract for this route.

### 2c. `InputMethodService` (system IME) and the voice-IME pattern

- **Declaration.** Service with `android:permission="android.permission.BIND_INPUT_METHOD"`, an intent filter on `android.view.InputMethod`, and `<meta-data android:name="android.view.im" android:resource="@xml/method"/>`; the XML's `<input-method>` root can name a `settingsActivity` and contain `<subtype>` elements ([Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)). The framework reads that metadata under the key `InputMethod.SERVICE_META_DATA` (`InputMethodService.java:1867`).
- **Subtypes.** A subtype can represent "An input mode, such as voice, keyboard, or handwriting", and `imeSubtypeMode` takes free text such as `keyboard` or `voice` (same page). The subtype attributes `isAuxiliary` and `overridesImplicitlyEnabledSubtype` are declared on `InputMethodSubtype` ([`InputMethodSubtype.java:65-68,130-154`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/inputmethod/InputMethodSubtype.java)). The Javadoc I could read does not spell out their semantics, so the usual reading "auxiliary subtypes are hidden from the normal switcher cycle" is **unverified**.
- **Switching.** An IME that declares `android:supportsSwitchingToNextInputMethod="true"` and sees `shouldOfferSwitchingToNextInputMethod()` return true "has to offer ways to invoke `switchToNextInputMethod`"; `switchToNextInputMethod(onlyCurrentIme)` with `false` treats all subtypes of all IMEs equally ([Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method); `InputMethodService.java:2157,2170`). `switchToPreviousInputMethod()` returns to the last IME (`InputMethodService.java:2145`). `switchInputMethod(id, subtype)` forces a specific IME and subtype, and throws if the IME is filtered by package visibility (`InputMethodService.java:2534`). A *keyboard* that wants to hand off to a voice IME therefore calls one of these; the exact gesture Gboard uses is **unverified** (Gboard source is closed).
- **Committing text.** `getCurrentInputConnection()` (may be null) then `commitText(text, 1)`; `setComposingText` for in-progress text ([Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)). FUTO commits finals with `commitText` and shows partials with `setComposingText` (`VoiceInputMethodService.kt:261,268`).
- **Users enable it** in system keyboard settings; it appears in the keyboard picker. Third-party keyboards that expose a voice button decide for themselves whether to call a voice IME. FUTO's list: HeliBoard, FlorisBoard, AnySoftKeyboard, Unexpected Keyboard (v1.23+) and AOSP Keyboard work; Gboard and Samsung Keyboard are hardcoded to their own engines; a Microsoft SwiftKey uses the activity intent (FUTO README "Keyboard support"). Because the in-app keyboard is derived from Unexpected Keyboard, note that the *upstream* keyboard supports calling a voice IME; the launcher's fork does not (**unverified** beyond the FUTO README line).

### 2d. `VoiceInteractionService` / assistant role

A `VoiceInteractionService` is how an app becomes the system's digital assistant: it needs `BIND_VOICE_INTERACTION` (defined in the platform manifest) and a metadata XML requiring a `sessionService` ("The service that hosts active voice interaction sessions. This is required."), and the `recognitionService` attribute "does nothing" from Android 12 (`attrs.xml`, `VoiceInteractionService` styleable). The user must make the app the default assistant, which also hands it the home-button/hotword role. It does not give other apps a dictation API.

**Verdict: overkill, wrong tool.** One perk is real: the FGS docs list "The service starts by an app which provides the `VoiceInteractionService`" among the exemptions from the while-in-use background restriction ([Restrictions on starting FGS](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)). That is not a reason to take the assistant role.

### 2e. `AccessibilityService` dictation into the focused field

- **Feasible technically** [inference]: an accessibility service can read the focused node and set text on it. I did not verify this from a primary source here; `AccessibilityNodeInfo.ACTION_SET_TEXT` is the usual mechanism: **unverified**.
- **Play policy.** Only apps whose primary purpose is supporting people with disabilities may declare `isAccessibilityTool`; "voice-based input systems for motor impairments" qualify as examples, but a general voice assistant that happens to help some users does not, and the policy lists "antivirus software, automation tools, assistants, monitoring apps, cleaners, password managers, and launchers" as non-qualifying. Non-tools must complete the Permission Declaration Form with a video, and show an in-app prominent disclosure with affirmative consent ([Google Play AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491)). A launcher is explicitly on the non-qualifying list.
- The app already has `LockAccessibilityService` (`AndroidManifest.xml:388-397`), so a Play declaration for it may already exist or the app may not be on Play; see open questions. Whether the app ships on Play at all is not stated in the repo files I read.
- Verdict: poor fit. It works only through brittle node targeting, needs the user to grant a scary permission, and carries the highest policy risk of the routes.

### 2f. Prior art (read from source)

| Project | Commit read | Engine | a. RecognitionService | b. `RECOGNIZE_SPEECH` activity | c. IME |
| --- | --- | --- | --- | --- | --- |
| [FUTO Voice Input](https://github.com/futo-org/voice-input/tree/d6e1eb2d139dc1a6342a4681c283686cca4bfceb) | `d6e1eb2` (2026-05-01) | Whisper | **Declared but empty.** `WhisperRecognizerService.kt` has empty `onStartListening/onCancel/onStopListening`; README: "Currently this does not support the SpeechRecognizer API, which few apps seem to use" | yes, `RecognizeActivity` (floating centre window) | yes, `VoiceInputMethodService`, `foregroundServiceType="microphone"`, returns with `switchToPreviousInputMethod()` (`:220`) |
| [Sayboard](https://github.com/ElishaAz/Sayboard/tree/0f1ee1845a9f26f3bad82493e8cfdf849b4039f3) | `0f1ee18` (2025-07-01) | Vosk | yes, `SayboardRecognitionService`, permission `RECORD_AUDIO`, `foregroundServiceType="microphone"` | no | yes, `IME`, `method.xml` has a single subtype `imeSubtypeMode="voice"` with `overridesImplicitlyEnabledSubtype="true"` |
| [Transcribro](https://github.com/soupslurpr/Transcribro/tree/c5f159e2fc3218fbee88ac22840ba986f741046d) | `c5f159e` (2026-09-29) | whisper.cpp + Silero VAD | yes, `MainRecognitionService`: partial and final results, busy error, caller attribution context | yes, `ActionRecognizeSpeechActivity` | yes, `VoiceInput` IME, `switchToPreviousInputMethod()` (`VoiceInput.kt:301,504`), `commitText` (`:420`) |
| [Dicio](https://github.com/stypox/dicio-android/tree/fe675ede2bc06fbe9d226c775e7d020760215a90) | `fe675ed` (2026-10-11) | Vosk or system | yes, `SttService`, `directBootAware`, `foregroundServiceType="microphone"` | yes, `SttPopupActivity` (`taskAffinity=""`, `excludeFromRecents`) | no |
| [WhisperInput](https://github.com/alex-vt/WhisperInput/tree/6081d6579f7dec195f8792dec288b945f7e7a7c0) | `6081d65` (2023-03-24, unmaintained since) | whisper.cpp (tiny.en) | a Kõnele fork: queries `android.speech.RecognitionService`, README sets itself as assistant "web search" | yes (`SpeechActionActivity`) | yes, "voice keyboard" |
| [Vosk Android demo](https://github.com/alphacep/vosk-android-demo/tree/a5e58ec399135f78325bd3e289849fc3fb57ce96) | `a5e58ec` (2025-12-08) | Vosk | none (manifest has only `RECORD_AUDIO`) | none | none; this is the engine demo that Sayboard says it is based on (Sayboard README) |

Pitfalls the sources document:
- **`QUERY_ALL_PACKAGES` for the recognition service** (Sayboard README and manifest, cites [Kõnele-service #9](https://github.com/Kaljurand/K6nele-service/issues/9)).
- **FUTO's `DummyService`**, commented "Workaround for https://github.com/futo-org/voice-input/issues/17" (`AndroidManifest.xml:131`). The issue text was not read: purpose **unverified**.
- **Every service in all four apps sets `foregroundServiceType="microphone"`**, including the IMEs; Sayboard declares `FOREGROUND_SERVICE_MICROPHONE` and FUTO does not declare it (FUTO manifest `:5-10`: only `FOREGROUND_SERVICE`). Whether either works end to end on 14+ is **unverified** and relevant to section 3.
- **FUTO does not offer a voice keyboard for Gboard or Samsung Keyboard** at all (README), so IME and activity routes cannot reach users of those keyboards unless the user also switches keyboards.
- FUTO's own status note: "Development has largely shifted focus to the FUTO Keyboard app, which has voice input built-in" (README "Status"), a sign the standalone-provider model is costly to sustain.

## 3. Recommendation

### Which routes

| Route | Reaches | Needs user action | Fit |
| --- | --- | --- | --- |
| **Voice IME** (`voice` subtype) | any text field, via a keyboard's voice button or the system IME picker | enable IME, then pick it | **Yes, first.** Commits text straight into the field, the only route that works for apps that expose no voice API at all |
| **`ACTION_RECOGNIZE_SPEECH` activity** | apps and keyboards that fire that intent | none beyond installing (system chooser) | **Yes, second.** Small, and the launcher can test it against itself |
| `RecognitionService` | apps calling `SpeechRecognizer.createSpeechRecognizer` | must become the default recognizer; no confirmed settings UI on 13+ | **Later, optional.** Largest reach in principle, hardest onboarding |
| `VoiceInteractionService` | n/a | become the assistant | No |
| `AccessibilityService` | any focused field | grant a restricted permission | No (policy) |

Rationale tied to this app:
- The engine is **phrase-batch** (section 1): a phrase is segmented by VAD, written to a cache file, and transcribed whole. That maps cleanly to an IME that commits each finished phrase and to the activity's single `EXTRA_RESULTS` list. It maps less well to `RecognitionService.partialResults`, which clients expect to stream.
- The launcher's value-add (local LLM cleanup, command formatter, the dictation card) is a text post-processor. In an IME the cleanup result can be committed once per pause, the same flow the in-app dictation already has (`VoiceDictation.Phase` `LISTENING → FINISHING → WAITING`).
- The mic and the models are already managed by the app; no new model download story is needed.

### Minimal skeleton (sketch, not tested)

Voice-only IME:

```xml
<service
    android:name=".dictation.LauncherVoiceIme"
    android:exported="true"
    android:label="@string/voice_ime_label"
    android:permission="android.permission.BIND_INPUT_METHOD">
    <intent-filter><action android:name="android.view.InputMethod"/></intent-filter>
    <meta-data android:name="android.view.im" android:resource="@xml/voice_ime"/>
</service>
```

```xml
<!-- res/xml/voice_ime.xml -->
<input-method xmlns:android="http://schemas.android.com/apk/res/android"
    android:settingsActivity="com.termux.app.fragments.settings.TermuxPreferencesActivity"
    android:supportsSwitchingToNextInputMethod="true">
    <subtype android:imeSubtypeMode="voice" android:label="@string/voice_ime_label"
        android:overridesImplicitlyEnabledSubtype="true"/>
</input-method>
```

(Manifest and XML shape from [Create an input method](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method) and Sayboard's `method.xml`; the settings-activity class name above is a placeholder.)

```java
public final class LauncherVoiceIme extends InputMethodService {
    @Override public View onCreateInputView() { /* small card: mic, pause, switch-back key */ }

    // when a phrase finishes (engine callback):
    void onPhrase(String text, boolean isFinal) {
        InputConnection ic = getCurrentInputConnection();   // may be null
        if (ic == null) return;
        if (isFinal) ic.commitText(text, 1); else ic.setComposingText(text, 1);
    }
    void onDone() { switchToPreviousInputMethod(); }
}
```

Activity route:

```xml
<activity android:name=".dictation.RecognizeSpeechActivity" android:exported="true"
    android:excludeFromRecents="true" android:taskAffinity="" android:theme="@style/...Translucent">
    <intent-filter>
        <action android:name="android.speech.action.RECOGNIZE_SPEECH"/>
        <category android:name="android.intent.category.DEFAULT"/>
    </intent-filter>
</activity>
```
On finish: `setResult(RESULT_OK, new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, list))` (extras per `RecognizerIntent.java`).

If a `RecognitionService` is added later, copy the shape in section 2a and guard it with `android:permission="android.permission.RECORD_AUDIO"` as all four prior-art apps do.

### Interactions with the launcher

- **Process and model sharing.** The model graphs already live in the separate `:tai_runtime` process reached over a `Messenger` (`TaiRuntimeService.java:17,130`), which any new IME/activity can call through `TaiManager`. A new service in the main process would reuse the same runtime and the same `tai/models/` directory, so no second copy of the weights. Cost: the runtime serves one request at a time and has memory-pressure/idle-exit logic (`TaiRuntimeService.java:141-162`), so a simultaneous in-app dictation and an IME dictation would contend. Decide policy: refuse the second, or queue [inference].
- **Cleanup model contention.** Cleanup uses a local chat model that is loaded while you speak (`docs/en/Voice_Input.md` "Cleanup"). Loading Gemma on top of a foreground app is heavy on 6 GB phones; the IME should default to **Raw text** for latency unless the user opts in [inference].
- **Mic ownership.** Only one `AudioRecord` is guaranteed the mic when two apps ask; the platform may silence one ([Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input) via search result: silencing, not blocking; I read only the search snippet, **unverified** beyond that). `VoiceInputSession` must not run while an IME session is active, or vice versa.
- **Where the IME UI draws.** An IME draws in its own window above the host app, so the launcher's dictation card (`ReadAloudCard`/`VoiceTranscriptPanel`, app-window views) cannot be reused as is; a small IME view is needed. The `VoiceInputSession.Host` interface (`VoiceInputSession.java:115`) is the seam to implement for it [inference].
- **Mic permission.** `RECORD_AUDIO` is requested by the launcher's `TermuxActivity`. An IME cannot show a runtime permission dialog from its own window; it must launch an activity that does, as the permission model requires a visible activity [inference; the docs I read state only that the permission is while-in-use]. Plan a first-run "Allow microphone" hop.

### Mic, foreground service, and Android 14+

- **While-in-use rule.** On Android 14+, an app that "targets Android 14 or higher" is checked when it *creates* a foreground service: for type `microphone` the OS verifies `RECORD_AUDIO` is currently held; because that permission is while-in-use, a background app that tries to start such a service gets a `SecurityException`; "you must call `Context.startForegroundService()` or `Context.bindService()` while your app has a visible activity, unless the service falls into one of the defined exemptions" ([Restrictions on starting FGS](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)).
- **Exemptions listed:** a system component starts the service; app-widget interaction; notification interaction; a `PendingIntent` sent from a different visible app; device-owner DPC; the app providing the `VoiceInteractionService`; apps with `START_ACTIVITIES_FROM_BACKGROUND` (same page).
- **Microphone type requirements:** declare `android:foregroundServiceType="microphone"`, request `FOREGROUND_SERVICE_MICROPHONE` plus `FOREGROUND_SERVICE`, hold `RECORD_AUDIO`; you cannot create a `microphone` FGS from the background or from a `BOOT_COMPLETED` receiver "with a few exceptions" ([FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types)).
- **What that means here.** An IME that is on screen has the user in the host app, so starting a mic FGS from the IME is the normal case; the prior-art IMEs all declare `foregroundServiceType="microphone"` on the IME service itself (Sayboard, FUTO manifests). Whether an IME that records **without** an FGS is allowed to keep the mic when the user locks the screen or switches app is **unverified**; I found no primary source. [inference: record only while the IME window is shown, and treat losing the window as end-of-dictation, which matches the launcher's existing rule "Leaving the app stops listening".]
- **`targetSdk 28` caveat.** The documented FGS permission check applies to apps targeting 34+. This app targets 28 (`gradle.properties:30`), so the 14+ type rules and `FOREGROUND_SERVICE_MICROPHONE` are not enforced on it today per the quoted sentence; but any move to a higher target changes that, and background-mic behaviour that is not target-gated (Android 11+ background capture silencing) still applies. The exact target-SDK gating of the declaration requirement is **unverified**: the page says "Beginning with Android 14 ... you must declare", without restating the target condition.
- The existing `TaiRuntimeService` already runs as `specialUse` in another process (`AndroidManifest.xml:373-377`); a mic FGS would be a different service with the `microphone` type, not a change to that one.

### Risks

- **Reach is limited by hardcoded keyboards.** Gboard and Samsung Keyboard will not call a third-party voice IME (FUTO README), so a user of those keyboards gets nothing from the IME route unless they switch keyboards or use the IME picker.
- **Cold start.** The first phrase waits for the model to load; the in-app flow hides this with `warm()` while the user speaks (`SttRuntime.warm`). In an IME the user sees the card longer if the runtime was evicted by pressure watch (`TaiRuntimeService.java:141-162`).
- **Language.** Whisper detects language per phrase and the app has a `VoiceLanguageLock`; an IME needs to surface that choice somewhere other than the in-app card.
- **Privacy.** Another app's text field is the target; the cleanup model and any "remote model" cleanup (`docs/en/Voice_Input.md`) must stay local-only for system-wide use [inference/product].

## 4. Open questions for the developer

1. **Distribution.** Is the app on Google Play? That decides whether `QUERY_ALL_PACKAGES` (for a `RecognitionService`) and a second accessibility declaration are even realistic, and which store policies apply. (The repo has a "tlstore" and GitHub releases; I did not confirm Play.)
2. **Which route first?** IME plus activity as recommended, or is there a specific app (a browser, a messenger, a keyboard like HeliBoard) you want to dictate into? FUTO's README shows keyboard-by-keyboard support differs.
3. **Is a `RecognitionService` worth the onboarding?** It needs the user to set the system default; I could not confirm a Settings UI on Android 13+. Would you accept a Shizuku/adb "make default" button (**unverified** to work) as the route?
4. **Cleanup in the IME.** Default to raw text (fast) or run the local cleanup model per pause (slower, heavier on RAM)?
5. **One engine at a time?** Should an in-app dictation and an IME dictation exclude each other, or queue?
6. **Streaming.** The engine is phrase-batch. Is phrase-level partials acceptable for the IME (composing text per phrase), or do you want a word-level streaming path added to `SttRuntime` first?
7. **Mic hop.** Accept a one-time "Allow microphone" activity launched from the IME, or require the permission to be granted in the launcher first?
8. **`targetSdk 28`.** Is a raise planned? It changes the foreground-service rules above, so the answer affects whether `FOREGROUND_SERVICE_MICROPHONE` has to be added.
9. **Scope in the product.** Does a system-wide provider belong beside "Display as a window" and "Widgets follow the Base Look" decisions in the memory notes, i.e. should this get an ADR in `docs/adr/` before work starts?

## Unverified claims (collected)

1. Why prior-art services use `android:permission="android.permission.RECORD_AUDIO"` instead of a bind permission (no `BIND_RECOGNITION_SERVICE` exists in the platform manifest).
2. Whether not using the caller's attribution context in a `RecognitionService` breaks anything.
3. Whether the `QUERY_ALL_PACKAGES` workaround is still needed on current Android, and whether `QUERY_ALL_PACKAGES` is permitted on Play.
4. API levels for `createOnDeviceSpeechRecognizer`, `checkRecognitionSupport`, `triggerModelDownload` (from memory; "Added in" lines not readable).
5. Any AOSP, Pixel or Samsung Settings UI that lets a user choose a third-party recognizer on Android 13+ (AOSP picker classes exist at tags 11 and 12, not at 13 onward under that path).
6. Whether `WRITE_SECURE_SETTINGS` (via adb or Shizuku) can set `voice_recognition_service` and whether the system then honours it.
7. Exact semantics of `isAuxiliary` and `overridesImplicitlyEnabledSubtype` (declared in source, Javadoc not read); how Gboard switches to a voice IME.
8. That the upstream Unexpected Keyboard can launch a voice IME, beyond FUTO's README listing it.
9. `AccessibilityNodeInfo.ACTION_SET_TEXT` as the text-insertion mechanism.
10. Purpose of FUTO's `DummyService` (linked issue not read).
11. Whether FUTO (no `FOREGROUND_SERVICE_MICROPHONE` declared) and the other prior-art services work end to end on Android 14+.
12. Whether an IME recording without a foreground service keeps the mic when the app is backgrounded or the screen is locked; behaviour of concurrent capture beyond the search snippet from [Sharing audio input](https://developer.android.com/media/platform/sharing-audio-input).
13. How the `targetSdk 28` gating interacts with the Android 14 microphone-FGS declaration requirement.
14. Whether the app ships on Google Play.
