# Termux:API and the launcher: what to port, bridge, keep delegating or remove

Assessment date: **2026-09-29**. Local checkout: branch `dev`, **ffc9282a**. This is desk research.
No code was changed, nothing was built, and no device was touched.

**Question.** Termux:API (the `com.termux.api` Android app plus the `termux-api` apt package of
CLI scripts) gives shell programs clipboard, notification, vibrate, torch, battery, TTS/STT,
sensors, location, camera, SMS and similar access. The launcher now does several of these jobs
itself, in its terminal emulator (OSC 52/9/99/777), in `launcherctl`, in TAI and in its status bar.
For each Termux:API feature: port it into the launcher, bridge the existing CLI to launcher code
that already exists, keep delegating to the companion app, or drop one of two implementations.

**Labels.**
- **[upstream]**: read in termux/termux-api at `fc26ce17` or termux/termux-api-package at `9e7f1531`
  (both the `master` heads on 2026-09-29). File links are in Sources.
- **[kitty]**: kitty's own docs at sw.kovidgoyal.net/kitty.
- **[repo]**: this checkout, `file:line`.
- **[inferred]**: my reasoning. Nobody has built or measured it.
- **[unverified]**: I could not confirm it from a primary source.

---

## 1. Summary and recommendation

**Keep Termux:API as the companion app for the long tail. Do not fold the app into the launcher.
Make the few everyday commands work without it by shipping launcher-backed versions of those
scripts.**

1. **Clipboard and notifications are already the launcher's.** They are the two things people
   use Termux:API for most. The emulator handles OSC 52 read and write, OSC 9, OSC 99 and OSC 777
   (`TerminalEmulator.java:3722-3744`, `:3882-3914`). `launcherctl notify|progress|clipboard`
   reaches the same `ShellSignals` methods from a process that has no terminal
   (`LauncherCtlApiServer.java:1150-1210`, `ShellSignals.java:82-184`). The work left is a bridge,
   not a port: install `termux-clipboard-get/set`, `termux-notification`,
   `termux-notification-remove` and `termux-toast` as thin shims over `launcherctl`. Then these
   commands work on every edition with no companion APK. **Priority: P1.**
2. **Porting the rest buys little and costs a lot.** The Termux:API manifest asks for SMS,
   call-log, contacts, fine and background location, camera, body sensors, IR, NFC and biometric
   permissions [upstream `AndroidManifest.xml:8-40`]. Folding those into the home-screen app would
   make the launcher hold every dangerous permission. That is a policy problem on Play (SMS and
   call log are limited to default handlers) and an attack-surface problem everywhere. The
   companion already inherits the launcher's uid through `sharedUserId`. So a separate APK costs
   the user one install and costs no capability.
3. **Worth porting, and cheap:** `termux-vibrate`, `termux-torch`, `termux-battery-status` and
   possibly `termux-volume`. The launcher already holds, or needs no, permission for each
   (`AndroidManifest.xml:46-47,57`). Each is a few lines of Android API. **Priority: P3, optional.**
4. **Clean up duplicates inside the launcher first.** The notification-history store is
   write-only. Its read endpoints were removed in 9eaab899 and nothing reads it now (§5.1). OSC 99
   `buttons` are parsed and then dropped (§5.3). The launcher has three notification paths that
   follow different rules (§5.2).

---

## 2. Transport and compatibility background

### 2.1 How a `termux-*` command reaches the Termux:API app

- Every script ends in `$PREFIX/libexec/termux-api <Method> [am-style extras]`. For example,
  `termux-clipboard-get.in` ends with `@TERMUX_PREFIX@/libexec/termux-api Clipboard`
  [upstream `scripts/termux-clipboard-get.in`].
- `termux-api.c` builds two abstract-namespace sockets for stdin and stdout. It then tries one of
  two routes:
  - **Listen socket (Android < 14 only).** It connects to the abstract socket
    `com.termux.api://listen` and checks `SO_PEERCRED` `cred.uid == getuid()`. If that works, it
    sends the argument list over the socket [upstream `termux-api.c:31,67-90,92-160`].
    - The API-level gate exists because on Android 14 and later, a frozen app process still
      accepts connections but never answers. `am broadcast` unfreezes it [upstream
      `termux-api.c:67-75`, citing termux-api#638].
  - **Fallback (every version).** It execs `$PREFIX/bin/am broadcast --user 0 -n
    com.termux.api/.TermuxApiReceiver --es socket_input … --es socket_output … --ei api_server_pid
    … --es api_method <Method> …` [upstream `termux-api.c:311,355-401`].
- The component name `com.termux.api/.TermuxApiReceiver` and the listen address are
  **hard-coded** [upstream `termux-api.c:31,364`]. The termux-packages recipe applies no
  substitution to them [termux-packages `packages/termux-api/build.sh`].
- On the app side, `SocketListener` binds a `LocalServerSocket` on the same address and rejects
  any peer whose uid differs from its own [upstream `SocketListener.java:40-46`].
  `TermuxApiReceiver` switches on `api_method` [upstream `TermuxApiReceiver.java:89-277`].
- `am` is Termux's `termux-am`. With `run-termux-am-socket-server` on, it is served by the app's
  own am socket server [repo `TermuxPropertyConstants.java:150-151`, `TermuxAmSocketServer.java:57`].
  termux-api depends on `termux-am (>= 0.8.0)` [termux-packages `build.sh`].

### 2.2 Why the companion must share the launcher's uid and key

- `TermuxApiReceiver` is `exported="false"` [upstream `AndroidManifest.xml:162-163`]. Only the
  same uid can deliver the broadcast. The launcher and the companion join
  `sharedUserId="${TERMUX_PACKAGE_NAME}"` [repo `AndroidManifest.xml:9`; upstream
  `AndroidManifest.xml:4`], and a shared uid requires the same signing key.
- The project's forks exist for that reason. Each edition's companion is debug-signed with the
  shared `testkey_untrusted.jks`, and an F-Droid build will never pair [repo `AGENTS.md:328-336`,
  `README.md:79-81`].
- `targetSdkVersion` must stay 28 across the whole shared-uid group, or `$PREFIX` stops being
  executable [repo `AGENTS.md:300-307`, `AndroidManifest.xml:1-5`]. Upstream Termux:API also
  targets 28 [upstream `gradle.properties`].
- The Nix fork's companion is `applicationId "com.termux.launcher.nix.api"` with
  `TERMUX_PACKAGE_NAME = "com.termux.launcher.nix"` [PickleHik3/termux-api `nix-pkg`,
  `app/build.gradle` diff].
  - The stock CLI would still broadcast to `com.termux.api/...`. **[unverified]** I did not find how
    the Nix and VAJ editions' `termux-api` CLI is patched to reach `…nix.api` / the VAJ package. The
    Nix docs only say "available; some tools need the `/android` prefix" [repo
    `docs/en/Nix_Package_Management.md:506`].
- Actions on Termux:API notifications run through the host app. Button and tap actions become
  `ACTION_SERVICE_EXECUTE` intents to `TERMUX_PACKAGE_NAME`'s `TermuxService`, running `sh -c
  <action>` [upstream `NotificationAPI.java:417-439`]. The launcher still serves that action
  [repo `TermuxService.java:179-180`].

### 2.3 What that means for a drop-in replacement

**[inferred]** Three routes keep `termux-clipboard-get` and the other commands working:

| Route | Works on | Cost | Verdict |
|---|---|---|---|
| **A. Shim scripts** of the same names that call `launcherctl` (HTTP to the local API with the token in `~/.launcherctl`) [repo `LauncherCtlApiServer.java:68-69,2484-2490`] | every edition, no APK, any Android version | conflicts with the real `termux-api` apt package on file paths; install them under another name or `$PREFIX/libexec`, or as an apt/`tlstore` package that `Conflicts:`/`Provides:` it | **Recommended** |
| **B. Launcher hosts `com.termux.api://listen`** (same uid, so it passes the `SO_PEERCRED` check) | Android < 14 only; Android 14+ always falls back to `am broadcast` to a component the launcher cannot own | bind collides with a real Termux:API app; has to reimplement the argument framing | Reject |
| **C. Launcher claims component `com.termux.api/.TermuxApiReceiver`** | impossible: a component belongs to its package | — | Reject |

Two caveats for route A:
- **Clipboard read.** Android 10+ lets a uid read the clipboard only while that uid has window
  focus, or while it is the default IME [AOSP `ClipboardService.java` (android10-release)
  `:741-790`, `isUidFocused(uid)`].
  - The check is **per uid**, so Termux:API's background read works only because it shares the
    launcher's uid while the launcher is in front [inferred from the same lines].
  - The launcher's rule, "on screen or refuse" (`ShellSignals.java:166-171`), is therefore not a
    regression against Termux:API. It is the same limit, stated honestly.
- **Clipboard write.** Termux:API writes with no visibility gate [upstream
  `ClipboardAPI.java:23-61`]. The launcher's `clipboardWrite` refuses when the launcher is not on
  screen (`ShellSignals.java:152-157`).
  - A shim must either accept that difference or the launcher must relax the gate for the API path.
    See open question Q2.

---

## 3. Feature tables

Columns: **Termux:API impl** (upstream) · **Launcher impl(s)** (repo) · **Kitty protocol** ·
**Duplicate?** · **Recommendation, priority**.

### 3.1 Clipboard

| | |
|---|---|
| Termux:API | `termux-clipboard-get/-set` → `ClipboardAPI`; plain `ClipboardManager.get/setPrimaryClip`, api_version 2 reads stdin for set [upstream `ClipboardAPI.java:20-61`] |
| Launcher | OSC 52 write + `?` query answered as `52;c;<base64>` (`TerminalEmulator.java:3722-3744`); `ShellSignals.clipboardWrite/Read` with on-screen + "Let programs read the clipboard" rules (`ShellSignals.java:139-184`, pref `TermuxAppSharedPreferences.java:638-641`); `launcherctl clipboard copy|paste` → `GET/POST /v1/clipboard` (`LauncherCtlApiServer.java:1203-1206,2445-2446,2693-2703`), rate-limited 60/min (`:1775-1776`); keyboard clipboard panel fed by launcher copies (`ClipboardHistory.java:51,154`; plan `project-docs/plans/shared-clipboard.md` decision 1 revision); X display sync (`x11-server/.../LorieView.java:610-616`, `DisplayClipboardPolicy.java`); copy/paste cleanup (`terminal-emulator/.../ClipboardCleanup.java`) |
| Kitty | OSC 52 (plain text) and **OSC 5522** (multi-MIME, chunked, permission-asking read) [kitty `clipboard/`]; `kitten clipboard` uses 5522, not 52 [kitty `kittens/clipboard/`] |
| Duplicate? | Yes, functionally: Termux:API and `launcherctl clipboard` both put shell text on the Android clipboard. In the launcher itself there is one path (`ShellSignals`) — good |
| Recommendation | **Bridge, P1.** Shim `termux-clipboard-get` → `launcherctl clipboard paste`, `termux-clipboard-set` → `launcherctl clipboard copy` (stdin or args). Decide the write-while-hidden gate (Q2). **Port OSC 5522, P3**: the launcher has no 5522 handler (grep of `terminal-emulator/src/main` finds none), so `kitten clipboard` will not work inside the launcher [inferred; not tested] |

### 3.2 Notifications, toast

| | |
|---|---|
| Termux:API | `termux-notification` → `NotificationAPI`: id, title, content, priority, channel, group, led, vibrate pattern, sound, ongoing, alert-once, icon, image-path, media buttons, up to 3 buttons with shell actions, `--action`, `--on-delete`, reply input [upstream `NotificationAPI.java:161-320,344-360`]; `termux-notification-remove`, `-channel`, reply [upstream `TermuxApiReceiver.java:190-200`]; `termux-toast` with short/gravity/colours [upstream `ToastAPI.java:25-68`] |
| Launcher | OSC 99 parsed by `KittyNotifications` (advertises `p=title,body,close,?,alive`, `a=focus,report`, `o=`, `u=0,1,2`, `c=1`, `w=1`; `KittyNotifications.java:44-52`) → `ShellSignals.notify` → `ShellNotifications.post` (two channels, urgency → priority, timeout, tap returns to pane, dismiss reported; `ShellNotifications.java:60-136,239-262`); OSC 9 and OSC 777 → `ShellSignals.notice` = in-app notice only, never the shade (`TerminalEmulator.java:3882-3914`, `ShellSignals.java:98-121`); `launcherctl notify [--title] [--id] [--urgency] [--pane]` (`LauncherCtlApiServer.java:2441,2635-2650`); toasts are centralised in `AppNotice` (`AppNotice.java:291`) |
| Kitty | OSC 99 with buttons, icons (`p=icon`), sound `s=`, close, alive, `?` [kitty `desktop-notifications/`]; `kitten notify` emits OSC 99, supports `--button`, `--icon`, `--wait-for-completion`, `--only-print-escape-code` [kitty `kittens/notify/`] |
| Duplicate? | Yes. Two shade posters (Termux:API's `termux-notification` channel vs `ShellNotifications`) with different feature sets; the launcher has no `close` on `launcherctl notify` though the OSC 99 close path exists (`ShellSignals.notifyClose`, `:94-96`) |
| Recommendation | **Bridge, P1** for the common subset: `termux-notification --id/-t/-c/--priority` → `launcherctl notify`; add `launcherctl notify --close ID` (it already has `notifyClose`) so `termux-notification-remove` can shim. **Delegate** the rich flags (shell-action buttons, media style, image, LED, reply) to Termux:API. **Port OSC 99 buttons, P2**: they are parsed (`KittyNotifications.java:148-150`, `KittyNotification.getButtons` at `:187`) but no app code reads `getButtons()` (repo grep), so `kitten notify --button` shows nothing. Icon payloads are dropped on purpose (`KittyNotifications.java:151-154`). `termux-toast` → shim to a new `launcherctl notice`/`AppNotice` route, **P3** |

### 3.3 Notification list / remove others' notifications / media

| | |
|---|---|
| Termux:API | `termux-notification-list` → its own `NotificationListAPI$NotificationService` (needs its **own** notification-access grant; prompts if missing) emitting id/tag/key/group/packageName/title/content/when/lines [upstream `TermuxApiReceiver.java:179-188`, `NotificationListAPI.java:76-85`, manifest `:193-197`] |
| Launcher | `LauncherCtlNotificationListener` (manifest `AndroidManifest.xml:366-372`) feeds pinned notifications, dots and the media widget, can cancel (`LauncherCtlNotificationListener.java:161-175,688-712`) and drive media sessions (`:663-686`); JSON snapshot helpers `getNotificationsSnapshot`/`getNowPlayingSnapshot`/`…ArtSnapshot` (`:185-240`) have **no callers**; the `/v1/notifications*`, `/v1/media/art` routes were removed in 9eaab899 |
| Kitty | none |
| Duplicate? | Yes: two notification listeners in the same uid, two user grants |
| Recommendation | **Bridge, P2**, if the developer wants shell access to notifications: re-expose a read-only `GET /v1/notifications` over the existing snapshot and shim `termux-notification-list` to it, so one grant serves both. Otherwise **delete the dead snapshot helpers** (§5.1). Privacy call: Q3 |

### 3.4 Everything else

| Feature | Termux:API impl | Launcher impl(s) | Kitty | Dup? | Recommendation, priority |
|---|---|---|---|---|---|
| Vibrate | `VibrateAPI`: duration, `--force` past silent mode [upstream `VibrateAPI.java:26-49`] | bell vibrate via `BellHandler` (`TermuxTerminalSessionActivityClient.java:432-450`, `termux-shared/.../BellHandler.java:26`); ~18 files with haptic calls (§5.4); `VIBRATE` held (`AndroidManifest.xml:47`) | BEL | partial | **Port, P3** (`launcherctl vibrate`, tiny) or delegate |
| Torch | `TorchAPI` → `CameraManager.setTorchMode`, no permission check in the receiver [upstream `TorchAPI.java:35`, `TermuxApiReceiver.java:250`] | none (grep) | — | no | **Port, P3** — no new permission [inferred from upstream not gating it] |
| Battery | `BatteryStatusAPI` | reads `ACTION_BATTERY_CHANGED` for TAI bench guards (`TaiDeviceConditions.java:58`) | — | partial | **Port, P3** as `launcherctl battery` reusing that reader |
| Wake lock | not Termux:API: `termux-wake-lock` is termux-tools → `am startservice` to TermuxService [termux-tools `termux-wake-lock.in`] | `TermuxService.actionAcquireWakeLock` + Wi-Fi lock (`TermuxService.java:171-177,391-420`) | — | no | **Nothing to do**; already core |
| Share / open / open-url | `termux-share` → `ShareAPI` (+ content provider) [upstream manifest `:154-158`] | `termux-open [--send] [--chooser]` → `TermuxOpenReceiver` (ACTION_SEND/VIEW, chooser; `TermuxOpenReceiver.java:40-105`); `termux-open-url` = termux-tools `am start -a VIEW` [termux-tools `termux-open-url.in`]; inbound shares `FileShareReceiverActivity` (`AndroidManifest.xml:228`) | OSC 8 links | yes (`termux-share` ≈ `termux-open --send`) | **Delegate/keep both**; document `termux-open --send` as the no-companion path. P3 docs only |
| Dialog | `DialogAPI$DialogActivity` (confirm, checkbox, date, spinner, text…) [upstream manifest `:101-105`] | none as a shell API (command palette is internal) | — | no | **Delegate** |
| TTS | `TextToSpeechAPI` = Android system `TextToSpeech`, engines, language, stream [upstream `TextToSpeechAPI.java:8-37`] | `tai speak` KittenTTS, 4 voices (`LauncherCtlApiServer.java:1993-1994`, `/v1/audio/speech` `:669-672`; `TaiTtsPlayer.java:76-84`) | — | overlapping, not equal | **Keep both**; optional shim flag later. P3 |
| STT | `SpeechToTextAPI` = Android `SpeechRecognizer`, live mic [upstream `SpeechToTextAPI.java:13-52`] | `tai transcribe <file.wav>` Whisper/Parakeet (`LauncherCtlApiServer.java:1992,2330-2346`); keyboard voice input (`inappkeyboard/voice/VoiceInputSession.java`) | — | overlapping | **Delegate** for live mic; consider `tai listen` later. P3 |
| Mic record | `MicRecorderAPI` (RECORD_AUDIO) | launcher records for voice input only | — | no | **Delegate** |
| Volume | `VolumeAPI` streams alarm/music/notification/ring/system/call [upstream `VolumeAPI.java:16-41`] | none; `MODIFY_AUDIO_SETTINGS` is declared (`AndroidManifest.xml:57`) but only audio focus is used (`TaiTtsPlayer.java:76-84`) | — | no | **Port, P3** or delegate |
| Brightness | `BrightnessAPI` writes `Settings.System` (needs WRITE_SETTINGS special access) [upstream `BrightnessAPI.java:22-32`, `TermuxApiReceiver.java:102-104`] | none | — | no | **Delegate** |
| Wallpaper | `WallpaperAPI` sets the system wallpaper | managed wallpaper: own copy, parallax, reads system wallpaper (`TermuxActivity.java:3469-3490`, `chrome/WallpaperPicture*.java`); `SET_WALLPAPER` held (`AndroidManifest.xml:43`); no `setBitmap` call (grep) | — | different jobs | **Bridge, P3**: a `launcherctl wallpaper set FILE` that feeds the managed wallpaper would matter more than the system setter; keep `termux-wallpaper` for the system one |
| Media player | `MediaPlayerAPI` plays a file | media-session control of other apps via listener (`LauncherCtlNotificationListener.java:663-686`) | — | no | **Delegate**; optional `launcherctl media next/pause` P3 |
| Location | `LocationAPI` fine + background [upstream manifest `:8-10`] | weather uses coarse last-known only (`WeatherController.java:334-345`; `ACCESS_COARSE_LOCATION` `AndroidManifest.xml:40`) | — | no | **Delegate** — do not add fine/background location to the home app |
| Camera photo/info | `CameraPhotoAPI` (CAMERA) | none | — | no | **Delegate** |
| Sensors | `SensorAPI` (BODY_SENSORS declared) | none | — | no | **Delegate** |
| SMS / call log / telephony / contacts | `SmsInbox/SmsSend/CallLog/Telephony*/ContactList` [upstream `TermuxApiReceiver.java:122-135,211-243`] | none | — | no | **Delegate** — Play limits these to default handlers (Sources) |
| IR, NFC, USB, fingerprint, keystore, SAF, storage-get, Wi-Fi, job scheduler, download, media scan, audio info, notification channel | respective `*API.java` [upstream `apis/`] | none relevant | — | no | **Delegate** |

---

## 4. Kitty-feature ports: status

| Kitty feature | Launcher status | Evidence | Next |
|---|---|---|---|
| OSC 52 write/read | Done; read behind a setting | `TerminalEmulator.java:3722-3744`, `shared-clipboard.md` decision 5 | — |
| OSC 5522 extended clipboard | Missing | no handler in `terminal-emulator/src/main` | P3 port; unblocks `kitten clipboard` |
| OSC 99 notifications | Done except buttons (parsed, not shown), icon (dropped), sound (not advertised) | `KittyNotifications.java:44-52,148-154`; `ShellNotifications.java:115-131` | P2 buttons as notification actions reported back `i=<id>;<n>` |
| OSC 9 / OSC 777 | Done as in-app notice + attention mark, not shade | `TerminalEmulator.java:3882-3914`, `ShellSignals.java:98-121` | Q4 whether they should reach the shade when hidden |
| OSC 9;4 progress | Done (window chip ring) | `ShellSignals.java:131-137` | — |
| Remote control (`kitty @` / DCS `kitty-cmd`) | Not supported; `launcherctl pane open/list/focus/write/read/close` covers `@ launch/ls/focus-window/send-text/get-text/close-window` over HTTP | `LauncherCtlApiServer.java:2431-2437`; kitty `remote-control/`, `rc_protocol/`; `docs/en/Terminal_Kitty_Protocols.md:77-81` | Keep; no DCS port needed unless kitten tools demand it |
| `kitten notify` | Works for title/body/urgency/close via OSC 99 [inferred]; `--button` silent | as above | P2 via buttons |
| `kitten clipboard` | Will not work (needs 5522) [inferred, untested] | as above | P3 via 5522 |

---

## 5. Duplicate or dead implementations inside the launcher

1. **Notification history store is write-only.** `LauncherCtlNotificationStore` keeps up to 10,000
   rows of posted and removed notifications in SQLite (`LauncherCtlNotificationStore.java:29-99`).
   - The rows are written from the listener when the "notification history" preference is on
     (`LauncherCtlNotificationListener.java:280-296`). The preference is off by default
     (`TermuxPreferenceConstants.java:336-339`).
   - Its readers `queryRecent/Since/Search/Stats` (`:166-188`) have no callers. The only other
     caller is `clearAll` from Settings (`TermuxStylePreferencesFragment.java:593-596`).
   - The endpoints that read it, `/v1/notifications/recent|since|search|stats`, went away in
     9eaab899 ("feat(api): strip agent and MCP endpoints").
   - It captures message bodies and nothing shows them. **Recommend: remove the store and the
     preference, or re-expose it on purpose (Q3).**
2. **Three shade and notice paths with different rules.**
   - (a) `ShellNotifications` for OSC 99 and `launcherctl notify`: shade, even when the launcher
     is hidden.
   - (b) `ShellSignals.notice` for OSC 9 and 777: in-app only, dropped when the launcher is hidden
     (`ShellSignals.java:104-107`).
   - (c) Termux:API's own channel, when installed.
   - Also relevant: `LauncherCategoryPasteNotification` (`LauncherCategoryPasteNotification.java:36,86`)
     is a fourth, unrelated poster.
   - **Recommend:** route any `termux-notification` shim into (a), and document the OSC 9 vs OSC 99
     difference in Help.
3. **Dead JSON snapshot helpers.** `LauncherCtlNotificationListener.getNotificationsSnapshot`,
   `getNowPlayingSnapshot` and `getNowPlayingArtSnapshot` (`:185-240`) have no callers since
   9eaab899. **Recommend:** remove them, or reuse them for §3.3.
4. **Haptics are spread out.** About 18 source files call `performHapticFeedback` or `Vibrator`
   directly. The largest are `VoiceFeedback.java` (12 hits), `DisplayTouchpadView`,
   `ClipboardPanelView` and `PaneWallLayout`. The in-app keyboard has its own `VibratorCompat`, and
   the bell has `BellHandler`. Only `RowHapticTickHelper` is shared (repo grep).
   - Not a Termux:API duplicate. It matters if `launcherctl vibrate` is added: that should go
     through one helper that respects the same "haptics off" setting. **[unverified]** I did not
     check whether one global haptics preference exists.
5. **Clipboard: one bus, several producers. That is fine.** OSC 52, the API, the keyboard edit
   keys, the selection toolbar and the X display all end at `ShareUtils`/`ClipboardManager`, and
   launcher copies are recorded once in `ClipboardHistory` (`ShellSignals.java:139-157`).
   - The only divergence is the X display's own sync, which is by design
     (`shared-clipboard.md` decision 2).
   - No removal needed. The duplicate is Termux:API versus `launcherctl`, and that is handled
     by §3.1.
6. **Two notification-listener grants.** When Termux:API is installed, the user grants
   notification access twice: to `LauncherCtlNotificationListener` and to
   `NotificationListAPI$NotificationService`. The §3.3 bridge removes the need for the second one.

---

## 6. Permission and policy notes

- Today the launcher holds, among others, `RECORD_AUDIO`, `ACCESS_COARSE_LOCATION`,
  `POST_NOTIFICATIONS`, `VIBRATE`, `WAKE_LOCK`, `SET_WALLPAPER`, `MODIFY_AUDIO_SETTINGS`,
  `SYSTEM_ALERT_WINDOW`, `READ_LOGS`, `DUMP` and `WRITE_SECURE_SETTINGS`
  (`AndroidManifest.xml:38-65`). It also binds a notification listener and an accessibility
  service (`:366-379`).
- Porting the P3 items adds none of the high-risk permissions. `setTorchMode` needs no manifest
  permission [inferred from upstream, which does not gate it]. Battery needs none. Volume is
  already declared.
- Porting SMS, call log, contacts, camera, fine or background location or body sensors would
  add dangerous permissions to the home app.
  - Google Play allows SMS and Call Log permissions only to apps "actively registered as the
    default SMS, Phone, or Assistant handler" [Play policy 10208820].
  - The launcher ships from GitHub releases, not Play or F-Droid (`README.md:85-88`). The policy
    is therefore not binding today. It is still the sensible bar.
- Because the companion shares the uid, a permission granted to Termux:API is effectively
  granted to the launcher's processes too, and the reverse holds as well. Android grants runtime
  permissions per uid for shared-uid packages. **[unverified]** I did not re-read the AOSP source
  for this. Splitting the APK is therefore about the install prompt and the manifest's reach, not
  process isolation.

---

## 7. Open questions for the developer

1. **Q1. Where do the shims live?** Options:
   - (a) The launcher writes `termux-clipboard-*` and similar into `$PREFIX/bin`, the way it writes
     `launcherctl` and `tai` today (`LauncherCtlApiServer.java:70-71`). These would clash with the
     apt `termux-api` package.
   - (b) A small `tlstore`/apt package that `Provides/Conflicts: termux-api`.
   - (c) Only when Termux:API is absent.
2. **Q2. Should the API clipboard write work while the launcher is hidden?** Termux:API's does.
   `launcherctl`'s refuses (`ShellSignals.java:152-157`). A reading of the clipboard from the
   background is blocked by Android anyway (§2.3).
3. **Q3. Should shell programs read other apps' notifications at all?** If yes, bridge §3.3. If no,
   delete the history store and the snapshot helpers (§5.1, §5.3).
4. **Q4. Should OSC 9 and OSC 777 reach the shade when the launcher is hidden,** the way OSC 99
   does? Agents ring OSC 9 when a turn ends (`TerminalEmulator.java:3883-3885`), and that is the
   case where the user is away.
5. **Q5. How does the Nix/VAJ `termux-api` CLI reach `com.termux.launcher.nix.api`?** (§2.2,
   unverified.) If it does not, the shims are the only working route on those editions, and P1
   becomes more urgent.
6. **Q6. Is the companion fork worth keeping current?** `master` is 2 ahead and 9 behind upstream;
   `nix-pkg` is 8 ahead and 9 behind (GitHub compare, 2026-09-29). If the shims land, the
   companion becomes optional, and releases can lag further.

---

## 8. Could not verify

- How the Nix/VAJ editions' `termux-api` CLI addresses their renamed companion packages (§2.2, Q5).
- That `kitten clipboard` fails and `kitten notify` works inside the launcher. This is inferred
  from the code, not run.
- That runtime permissions are shared across the whole `sharedUserId` group. I did not re-read
  AOSP for this.
- That `CameraManager.setTorchMode` needs no manifest permission. I inferred it from upstream not
  gating it. I did not read the Android reference.
- Whether a single global haptics preference exists in the launcher.
- The exact byte framing of the listen-socket argument message. I only skimmed
  `termux-api.c:92-160`.

---

## Sources

**Termux:API app** (termux/termux-api @ `fc26ce17e3badf85d4df191af364fcf7798047f1`),
`https://github.com/termux/termux-api/blob/fc26ce17e3badf85d4df191af364fcf7798047f1/<path>`:
- `app/src/main/AndroidManifest.xml`, `app/build.gradle`, `gradle.properties`
- `app/src/main/java/com/termux/api/TermuxApiReceiver.java`, `SocketListener.java`
- `app/src/main/java/com/termux/api/apis/{Clipboard,Notification,NotificationList,Toast,Vibrate,Torch,Volume,Brightness,TextToSpeech,SpeechToText,…}API.java`

**Termux:API CLI** (termux/termux-api-package @ `9e7f1531e1aa4a9c1e261a2dbde4de93653c366e`),
`https://github.com/termux/termux-api-package/blob/9e7f1531e1aa4a9c1e261a2dbde4de93653c366e/<path>`:
- `termux-api.c`, `termux-api-broadcast.c`
- `scripts/termux-clipboard-get.in`, `scripts/termux-api-start.in`

**Other sources:**
- termux-packages recipe: https://github.com/termux/termux-packages/blob/master/packages/termux-api/build.sh
- termux-tools: https://github.com/termux/termux-tools/tree/master/scripts (`termux-open.in`, `termux-open-url.in`, `termux-wake-lock.in`)
- Project fork: https://github.com/PickleHik3/termux-api. Branches `master`, `nix-pkg`,
  `io-vaj-package`. Compared via `gh api repos/termux/termux-api/compare/master...PickleHik3:termux-api:<branch>`.
- AOSP clipboard focus rule: https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/services/core/java/com/android/server/clipboard/ClipboardService.java (`clipboardAccessAllowed`)
- termux-api issue on frozen processes, as cited in `termux-api.c`: https://github.com/termux/termux-api/issues/638#issuecomment-1813233924
- kitty docs:
  - https://sw.kovidgoyal.net/kitty/clipboard/
  - https://sw.kovidgoyal.net/kitty/desktop-notifications/
  - https://sw.kovidgoyal.net/kitty/kittens/clipboard/
  - https://sw.kovidgoyal.net/kitty/kittens/notify/
  - https://sw.kovidgoyal.net/kitty/remote-control/
  - https://sw.kovidgoyal.net/kitty/rc_protocol/
- Google Play SMS/Call Log policy: https://support.google.com/googleplay/android-developer/answer/10208820

**Repo files** (all paths relative to the checkout root):
- Top level: `AGENTS.md`, `README.md`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/termux/`:
  - `app/terminal/`: `ShellSignals.java`, `ShellNotifications.java`, `ClipboardHistory.java`,
    `TerminalActionDispatcher.java`, `TermuxTerminalSessionActivityClient.java`,
    `inappkeyboard/voice/VoiceInputSession.java`
  - `app/`: `TermuxService.java`, `TermuxOpenReceiver.java`, `TermuxActivity.java`
  - `app/notice/AppNotice.java`, `app/statusbar/WeatherController.java`,
    `app/launcher/data/LauncherCategoryPasteNotification.java`,
    `app/fragments/settings/termux/TermuxStylePreferencesFragment.java`
  - `launcherctl/`: `LauncherCtlApiServer.java`, `LauncherCtlNotificationListener.java`,
    `LauncherCtlNotificationStore.java`
  - `ai/`: `TaiDeviceConditions.java`, `TaiTtsPlayer.java`
- `terminal-emulator/src/main/java/com/termux/terminal/`: `TerminalEmulator.java`,
  `KittyNotifications.java`, `KittyNotification.java`, `ClipboardCleanup.java`
- `termux-shared/src/main/java/com/termux/shared/`:
  - `termux/TermuxConstants.java`
  - `termux/settings/preferences/{TermuxPreferenceConstants,TermuxAppSharedPreferences}.java`
  - `termux/settings/properties/TermuxPropertyConstants.java`
  - `termux/shell/am/TermuxAmSocketServer.java`
  - `termux/terminal/io/BellHandler.java`
- `x11-server/src/main/java/com/termux/x11/LorieView.java`
- `project-docs/plans/shared-clipboard.md`, `project-docs/kitty-features/SPEC.md`
- `docs/en/Terminal_Kitty_Protocols.md`, `docs/en/Nix_Package_Management.md`
- Commit `9eaab899`: removal of the `/v1/notifications*` and `/v1/media/art` routes
