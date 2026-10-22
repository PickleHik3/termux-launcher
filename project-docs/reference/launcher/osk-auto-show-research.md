# On-screen keyboard auto-show: desktops against the Display place — research

**Date:** 2026-10-21 · **Branch:** worktree of `dev` · Nothing implemented; nothing here is a decision.

**Question.** How do modern Linux desktops make the on-screen keyboard (OSK) appear when the
user taps a text field and disappear when they tap elsewhere, and how does termux-launcher do the
same for X11 apps on the Display place? What could the launcher learn or adopt on an X11-only
server (no Wayland compositor)?

**Method.** Primary sources only: source files fetched at pinned tags, protocol XML, official docs.
Every claim carries a link; line numbers are from the pinned file as fetched. Nothing was run on a
device. Anything that is reasoning rather than reading is marked **(inferred)** and listed again
under **Unverified**. Tags used: gnome-shell/mutter 47.0, GTK 3.24.43, wayland-protocols 1.38,
Xwayland (xserver `xwayland-24.1.0`), KWin v6.2.0, squeekboard v1.43.1, qtbase/qtwayland v6.8.0,
IBus 1.5.31, fcitx5 5.1.11, Chromium 130.0.6723.58.

## Executive summary

1. **Every desktop keys on an exact focus signal from the toolkit, not on where the finger landed.**
   Wayland: `zwp_text_input_v3.enable`/`disable`. X11 session: IBus focus-in/out. The launcher
   infers focus from a cursor *name* seen 150 ms after a tap, because X tells a host nothing about
   focus.
2. **"Touch only" is a compositor decision, made in one place.** GNOME builds its OSK only when the
   seat is in touch mode and the last device was a touchscreen
   ([keyboard.js:1102-1104](https://gitlab.gnome.org/GNOME/gnome-shell/-/blob/47.0/js/ui/keyboard.js#L1102-L1104));
   KWin shows only when touch or tablet was the last input handler
   ([inputmethod.cpp:188-193](https://invent.kde.org/plasma/kwin/-/blob/v6.2.0/src/inputmethod.cpp#L188-L193)).
   The launcher's equivalent is the touch-mode gate in `isActive()`.
3. **Hide is focus-out plus a delay, not "tap elsewhere".** GNOME hides ~50 ms after the input
   method loses focus, then waits 300 ms more. A tap on a non-focusable area changes nothing
   (inferred). The launcher's "tap on a non-text cursor hides it" rule is *more* eager than GNOME's.
4. **Xwayland clients get no auto-show on GNOME/Wayland.** Xwayland binds no text-input or
   input-method protocol, and the shell wires IBus focus signals to the OSK only on an X11 session.
5. **The launcher already has the right second signal.** `launcherctl keyboard show --source focus`
   fed by an input method's focus events is exact and per-toolkit. fcitx5 even ships an auto-show
   mechanism and a D-Bus "virtual keyboard backend" interface the launcher could implement
   directly, replacing the documented `dbus-monitor` shell loop.
6. **AT-SPI is the only toolkit-neutral focus feed, and it is opt-in per app.** Chromium and Qt
   switch their bridge on only when the session says accessibility is enabled. It is a bigger
   dependency than an input method for similar coverage.

---

## Part 1 — How the desktops do it

### 1.1 GNOME Shell + Mutter on Wayland

**How the shell learns a field is focused.** The client speaks `zwp_text_input_v3`. The protocol
says the focused surface must send `enable`/`disable` "as the keyboard focus moves across
editable and non-editable elements of the UI", and that `enable` "must be issued every time the
active text input changes to a new one, including within the current surface"
([text-input-unstable-v3.xml:62-73, 87-95](https://gitlab.freedesktop.org/wayland/wayland-protocols/-/blob/1.38/unstable/text-input/text-input-unstable-v3.xml#L62-L95)).
v3 has no `show_input_panel` or `hide_input_panel` request; its request list is `destroy`, `enable`,
`disable`, `set_surrounding_text`, `set_text_change_cause`, `set_content_type`,
`set_cursor_rectangle`, `commit` (same file, lines 80-284; v1/v2 do have panel requests, see 1.3).
Mutter implements it in `MetaWaylandTextInput`:

- `commit` applies the pending `enabled` bit. First enable while not yet focused calls
  `clutter_input_method_focus_in`; enable **while already focused** sets `enable_panel`
  ([meta-wayland-text-input.c:659-671](https://gitlab.gnome.org/GNOME/mutter/-/blob/47.0/src/wayland/meta-wayland-text-input.c#L659-L671)).
  At the end of commit that flag becomes `clutter_input_focus_set_input_panel_state (..., ON)`
  ([:739-740](https://gitlab.gnome.org/GNOME/mutter/-/blob/47.0/src/wayland/meta-wayland-text-input.c#L739-L740)).
- `disable` while focused resets and calls `clutter_input_method_focus_out` (same function, ~:676-678).
- Content hints and purpose are translated and pushed to the `ClutterInputFocus` at commit
  (`clutter_input_focus_set_content_hints` / `_purpose`, same file).

**What the toolkit does to trigger it (GTK3 Wayland IM module).** `focus_in` enables text input
only when the surface holds text-input focus
([imwayland.c:820-835](https://gitlab.gnome.org/GNOME/gtk/-/blob/3.24.43/modules/input/imwayland.c#L820-L835)).
GTK3's `GtkEntry` calls `gtk_im_context_focus_in` only if the entry is editable
([gtkentry.c:4956-4971](https://gitlab.gnome.org/GNOME/gtk/-/blob/3.24.43/gtk/gtkentry.c#L4956-L4971)).
On top of that the module installs a multi-press gesture; on a release with no drag past the drag
threshold, and unless the field sets `GTK_INPUT_HINT_INHIBIT_OSK`, it calls `enable` again
([imwayland.c:668-690, 717-724](https://gitlab.gnome.org/GNOME/gtk/-/blob/3.24.43/modules/input/imwayland.c#L668-L690)).
**(inferred)** That second `enable` is what Mutter's "enable while already focused" branch turns
into panel-ON: a *tap* on a focused field re-raises a keyboard the user dismissed, while mere
keyboard focus does not. I did not find any other path that sends panel-ON in 47.0
(`inputMethod.js` only ever sends `OFF`; `grep` of `keyboard.js`/`inputMethod.js`).

**The touch-only heuristic.** It is not in the protocol or the toolkit; it is whether the shell
has a keyboard at all. `_syncEnabled` creates the `Keyboard` only if the a11y setting
`org.gnome.desktop.a11y.applications screen-keyboard-enabled` is on, **or** the seat is in touch
mode and the last device was a touchscreen
([keyboard.js:26, 1094-1107](https://gitlab.gnome.org/GNOME/gnome-shell/-/blob/47.0/js/ui/keyboard.js#L1094-L1107);
`last-device-changed` and `notify::touch-mode` re-run it, :1063-1071).

**How it opens.** On panel-ON, `open()` waits `KEYBOARD_REST_TIME` (= 2 × 150 ms animation = 300 ms)
unless `immediate`
([keyboard.js:22, 1667-1681, 1717-1740](https://gitlab.gnome.org/GNOME/gnome-shell/-/blob/47.0/js/ui/keyboard.js#L1717-L1740)).
`FocusTracker` supplies the cursor rectangle so the focused window can be slid clear of the OSK
(`cursor-location-changed`, :511-516).

**How it hides.** On `focus_out` the shell's `InputMethod` starts a 50 ms timer and then sends panel
state OFF ([inputMethod.js:15, 198-215](https://gitlab.gnome.org/GNOME/gnome-shell/-/blob/47.0/js/misc/inputMethod.js#L198-L215));
a following `focus_in` cancels it (:192-195). `OFF` reaches `close()` through
`_onKeyboardStateChanged` (keyboard.js:1667-1681), which again waits 300 ms unless immediate, and
`close` is a no-op if something re-requested the keyboard meanwhile (:1754-1783). Moving the focused
window closes immediately (`_onFocusWindowMoving`, :1928). **(inferred)** There is no "tap outside"
handler in these files: tapping a non-focusable area changes no focus, so the OSK stays. Explicit
dismiss is the keyboard's own hide key.

**Content purpose.** The layout follows the purpose: digits, number, phone, email, URL get
dedicated models, terminal gets `-extended` groups
([keyboard.js:1415-1440](https://gitlab.gnome.org/GNOME/gnome-shell/-/blob/47.0/js/ui/keyboard.js#L1415-L1440)).
Hints drive auto-capitalisation levels (`_updateLevelFromHints`).

**X11 clients under a Wayland GNOME session (Xwayland).** The Xwayland server's `meson.build`
lists the Wayland protocols it binds (pointer constraints, relative pointer, gestures, tablet,
keyboard grab, xdg-output, dmabuf, viewporter, xdg-shell, drm-lease, shortcuts-inhibit,
xwayland-shell, tearing, fractional-scale, syncobj) and **neither `text-input` nor `input-method`**
appears
([xwayland meson.build:36-53](https://gitlab.freedesktop.org/xorg/xserver/-/blob/xwayland-24.1.0/hw/xwayland/meson.build#L36-L53);
`grep -ci "text_input|input_method"` over `xwayland*.c` returns 0 for `xwayland.c`,
`xwayland-input.c`, `xwayland-screen.c`, `xwayland-window.c`). X clients therefore reach an input
method through IBus (XIM or the GTK/Qt IBus module), not through Mutter's text-input. In
keyboard.js the IBus `focus-in`/`focus-out` → `open()`/`close()` wiring is registered only
`if (!Meta.is_wayland_compositor())`
([:1211-1219](https://gitlab.gnome.org/GNOME/gnome-shell/-/blob/47.0/js/ui/keyboard.js#L1211-L1219)),
and in the Wayland branch IBus `set-cursor-location` is only used for positioning and is ignored
while a Wayland client has focus (:511-525). **(inferred)** Net: under GNOME/Wayland an X11 app does
not auto-raise the OSK in 47.0; the a11y setting or the edge gesture is the route.

### 1.2 GNOME on X11 / legacy: IBus and AT-SPI

- **Current X11 session: IBus, not AT-SPI.** `IBusManager` listens to the IBus *panel service* and
  re-emits `focus-in`, `focus-out` and `set-cursor-location`; it drops the shell's own context
  (`path` ending `/InputContext_1`)
  ([ibusManager.js:200-209](https://gitlab.gnome.org/GNOME/gnome-shell/-/blob/47.0/js/misc/ibusManager.js#L200-L209)).
  `FocusTracker` forwards them (keyboard.js:511-525) and, on X11 only, they open and close the OSK
  (:1211-1219). The same `open`/`close` rest-time and animation rules apply.
- **Who tells IBus.** GTK's IBus IM module sends purpose and hints through
  `ibus_input_context_set_content_type` (and, when configured to discard passwords, *skips* the
  call for PASSWORD and PIN)
  ([ibusimcontext.c:665-690](https://github.com/ibus/ibus/blob/1.5.31/client/gtk2/ibusimcontext.c#L665-L690)).
  Qt's IBus plugin calls `FocusIn()` from `setFocusObject` only if `inputMethodAccepted()`
  ([qibusplatforminputcontext.cpp:268-286](https://github.com/qt/qtbase/blob/v6.8.0/src/plugins/platforminputcontexts/ibus/qibusplatforminputcontext.cpp#L268-L286)).
  I found no `ImHints`/`SetContentType` reference in that Qt plugin (`grep`), so Qt-on-IBus
  appears not to forward hints **(inferred from absence)**.
- **Caribou (AT-SPI era).** The archived project describes itself as "an input assistive technology
  intended for switch and pointer users" with a "configurable on screen keyboard with scanning
  mode" ([README](https://gitlab.gnome.org/Archive/caribou/-/blob/master/README)). **Its focus
  tracking code was not retrievable; the AT-SPI-focus-driven design is from memory and is
  unverified.**
- **AT-SPI facts I could verify.** `ATSPI_STATE_EDITABLE` ("the user can change the contents of
  this object") and `ATSPI_STATE_FOCUSED`/`FOCUSABLE` exist
  ([StateType](https://gnome.pages.gitlab.gnome.org/at-spi2-core/libatspi/enum.StateType.html#editable)).
  Apps expose them only when the session says accessibility is on: Chromium reads
  `org.a11y.Bus` property `org.a11y.Status IsEnabled` over the session bus (after env-variable
  overrides) before turning its bridge on
  ([atk_util_auralinux.cc:122-160](https://github.com/chromium/chromium/blob/130.0.6723.58/ui/accessibility/platform/atk_util_auralinux.cc#L122-L160));
  Qt's `QSpiAccessibleBridge` is activated by `enabledChanged` from its D-Bus connection
  ([qspiaccessiblebridge.cpp:30-50](https://github.com/qt/qtbase/blob/v6.8.0/src/gui/accessible/linux/qspiaccessiblebridge.cpp#L30-L50)).
  The event name an external program subscribes to (`object:state-changed:focused`) is from my
  memory of the at-spi2 docs; `Event.xml` and the registry sources could not be fetched, so it is
  **unverified here**.

### 1.3 KDE Plasma and Phosh, as contrast

- **KWin** supports text-input v1, v2 and v3 and its own input-method-v1/v2 frontends
  ([inputmethod.cpp:128-153](https://invent.kde.org/plasma/kwin/-/blob/v6.2.0/src/inputmethod.cpp#L128-L153)).
  For v1/v2 the client can *ask* (`requestShowInputPanel`/`requestHideInputPanel` → `show`/`hide`);
  for v3 there is no such request, so KWin shows when the v3 text input becomes enabled
  (`textInputInterfaceV3EnabledChanged`: `refreshActive()`, then `show()` if enabled; :455-470).
  `refreshActive` makes the input method active only when the focused client supports text-input
  and has it enabled (:195-210). **Touch gate:** `shouldShowOnActive()` is true only if the last
  input handler was touch or tablet, or `KWIN_IM_SHOW_ALWAYS` is set (:188-193). Hide: `hide()`
  on a v1/v2 hide request, on screen lock (:116), or when the panel closes.
- **Phosh / squeekboard.** Squeekboard is an `zwp_input_method_v2` client; it sets `active` on the
  `activate`/`deactivate` events
  ([imservice.rs:62-80](https://gitlab.gnome.org/World/Phosh/squeekboard/-/blob/v1.43.1/src/imservice.rs#L62-L80))
  and maps `content_purpose` from text-input v3 (:117, 223). It also has a D-Bus API
  (`sm.puri.OSK0 SetVisible b true`) and `SQUEEKBOARD_DEBUG=force_show`
  ([README:84-87](https://gitlab.gnome.org/World/Phosh/squeekboard/-/blob/v1.43.1/README.md#L84-L87)),
  and needs layer-shell + virtual-keyboard-v1 from the compositor, input-method-v2 recommended
  (:93-100). The OSK is a separate client; visibility follows input-method activation, with an
  explicit D-Bus override. I did not read squeekboard's visibility state machine
  (`src/panel.rs`), so debounce rules there are **unverified**.

### 1.4 Toolkit side: what signals "a text field wants input"

| Toolkit / app | Signal it emits | Purpose / hints | Source |
|---|---|---|---|
| GTK3 | `gtk_im_context_focus_in/out` on editable widgets; Wayland module turns them into `enable`/`disable`, plus re-`enable` on tap | `input-purpose`, `input-hints` (`INHIBIT_OSK` respected) | gtkentry.c:4956-4971; imwayland.c:470-490, 668-690, 820-846 |
| GTK3 on X11 | XIM module: `XSetICFocus`/`XUnsetICFocus` on focus; IBus module: `FocusIn`/`FocusOut` | XIM module: none (style/preedit only); IBus module: purpose + hints | [gtkimcontextxim.c:740-766](https://gitlab.gnome.org/GNOME/gtk/-/blob/3.24.43/modules/input/gtkimcontextxim.c#L740-L766); ibusimcontext.c:665-690 |
| GTK4 | Same `GtkIMContext` model | Same properties | **not read** (could not locate the GTK4 IM module path); inferred from the GTK3 design |
| Qt 5/6 Wayland | `QPlatformInputContext::setFocusObject` → text-input v1/v2/v3 enable; hints via `Qt::ImHints` mapped to `set_content_type` | `ImHints` → hint+purpose | [qwaylandtextinputv3.cpp:323-333](https://github.com/qt/qtwayland/blob/v6.8.0/src/client/qwaylandtextinputv3.cpp#L323-L333); `QInputMethod::show()/hide()` for explicit requests ([qinputmethod.cpp:170-190](https://github.com/qt/qtbase/blob/v6.8.0/src/gui/kernel/qinputmethod.cpp#L170-L190)); [qwaylandinputcontext.cpp:111-130](https://github.com/qt/qtwayland/blob/v6.8.0/src/client/qwaylandinputcontext.cpp#L111-L130) |
| Qt 6 on X11 | In-tree input-context plugins are only `compose` and `ibus`; no XIM file under `platforms/xcb` at v6.8.0 | ibus: see 1.2 | [directory listing, v6.8.0](https://github.com/qt/qtbase/tree/v6.8.0/src/plugins/platforminputcontexts) |
| Chromium / Electron on X11 | Delegates to the toolkit's context: `LinuxUi::CreateInputMethodContext` first, then the Ozone platform's | `UpdateFocus(has_client, text_input_type, attributes, reason)` | [linux_input_method_context_factory.cc:30-50](https://github.com/chromium/chromium/blob/130.0.6723.58/ui/base/ime/linux/linux_input_method_context_factory.cc#L30-L50); [input_method_auralinux.cc:364-390](https://github.com/chromium/chromium/blob/130.0.6723.58/ui/base/ime/linux/input_method_auralinux.cc#L364-L390); [input_method_context_impl_gtk.cc:211-236](https://github.com/chromium/chromium/blob/130.0.6723.58/ui/gtk/input_method_context_impl_gtk.cc#L211-L236) |
| Firefox | GTK IM context wrapper | n/a | **Not verified**: the `IMContextWrapper.cpp` path I tried returned nothing; secondary knowledge only |
| Plain Xlib / XIM apps | `XSetICFocus`/`XUnsetICFocus` | The X Input Method protocol text has no input-purpose or hint attribute (`grep -i hint` of the spec returns nothing) | [XIM spec](https://www.x.org/releases/X11R7.7/doc/libX11/XIM/xim.html) |

### 1.5 On a pure X11 server, what can an external program observe?

Answered from the sources above; "reliability" is my assessment **(inferred)** unless a link is given.

| Signal | What it tells you | Reliability per toolkit |
|---|---|---|
| **XIM server** (be the `@im=` server) | Exact `SetICFocus`/`UnsetICFocus` per client, with the client window; no purpose/hints | Only toolkits using XIM: GTK3 with `GTK_IM_MODULE=xim`, Xlib apps, Chromium via GTK context. **Qt 6 has no in-tree XIM** (see table), so Qt6 needs an IBus/fcitx plugin instead. Server and client must agree on `XMODIFIERS` |
| **IBus panel signals** (what GNOME's X11 session uses) | Exact focus-in/out and cursor location | GTK3 IBus module and Qt ibus plugin when `GTK_IM_MODULE=ibus`/`QT_IM_MODULE=ibus`; hints carried by GTK only |
| **fcitx5 D-Bus `InputContext1` FocusIn/FocusOut** (what the launcher's doc recipe watches) | Exact, same as above | Needs `GTK_IM_MODULE=fcitx`, `QT_IM_MODULE=fcitx`, `XMODIFIERS=@im=fcitx` (docs/en/X11_Display.md:180-186) |
| **AT-SPI2 over the a11y D-Bus** | Focus and `EDITABLE` state, for any toolkit that bridges | Only when `org.a11y.Status IsEnabled` is true (Chromium: atk_util_auralinux.cc:144-160); Qt bridge activates on the bus-enabled signal; GTK3 via ATK bridge **(inferred)**; Electron/Chromium need the same flag; heavy and per-app opt-in |
| **X focus events** (`FocusIn`/`FocusOut`, `_NET_ACTIVE_WINDOW`) | Which *window* has focus, not which *widget* | Toolkits move focus between widgets inside one X window and send nothing to X **(inferred)**, so a text field is invisible |
| **XFixes** | Cursor-change notices and cursor names, delivered to X clients (the launcher's patch notes: "XFixes publishes the name only to X clients, which the host is not", [UPSTREAM.md:93-95](../../../x11-server/UPSTREAM.md)). Not a focus signal | A helper inside the session could use it; the launcher takes the same information inside the server instead |
| **Pointer cursor name** (launcher's way) | The cursor shape of the window under the pointer | Depends on the app choosing an `xterm`/`text` cursor; blind to keyboard focus changes with no pointer motion |

---

## Part 2 — termux-launcher today

### 2.1 What it is

"Keyboard follows text fields" ("OSK auto-show" in Settings → Display → Input) is a pure policy
class with no views: `DisplayTextFocusPolicy`
([DisplayTextFocusPolicy.java:6-35](../../../app/src/main/java/com/termux/app/x11/DisplayTextFocusPolicy.java#L6-L35)),
owned by `X11DisplayHostController`, which logs every decision under the tag `X11TextFocus`
([X11DisplayHostController.java:37, 88-103](../../../app/src/main/java/com/termux/app/x11/X11DisplayHostController.java#L37)).
User-facing behaviour is in [docs/en/X11_Display.md:140-203](../../../docs/en/X11_Display.md).

### 2.2 How it detects a text field: two signals

**Signal A, the cursor's name.** The X server forwards the name of the cursor the pointer shows
(`EVENT_CURSOR_NAME_CHANGED`, patch 0002, resolved from `CursorRec.name` in `lorieSetCursor`,
re-sent when a host attaches)
([UPSTREAM.md:89-96](../../../x11-server/UPSTREAM.md)). Patch 0003 names core-font glyph cursors
(`xterm`, `left_ptr`, …) in a `ProcVector[X_CreateGlyphCursor]` wrapper, because GTK/Qt/Firefox
without a cursor theme use unnamed core cursors
([UPSTREAM.md:209-232](../../../x11-server/UPSTREAM.md)). Java side:
`LorieView.onCursorNameChanged` stores it and calls the listener
([LorieView.java:732-758](../../../x11-server/src/main/java/com/termux/x11/LorieView.java#L732-L758));
the host controller posts it to the policy
([X11DisplayHostController.java:167](../../../app/src/main/java/com/termux/app/x11/X11DisplayHostController.java#L167)).
The "text" set is `xterm`, `text`, `ibeam`, `vertical-text`, case-insensitive
([DisplayTextFocusPolicy.java:44, 320-328](../../../app/src/main/java/com/termux/app/x11/DisplayTextFocusPolicy.java#L44)).

A name means something only inside the **150 ms tap window** (`TAP_WINDOW_MS`, :38) after a tap:
`onDisplayTap` opens the window and schedules `decideOnTap` (:193-199); `onCursorName` records the
last name seen inside it (:227-230); `decideOnTap` takes that name, or the standing name if X sent
none (a second tap in the same field sends no new name) (:275-301).

**What counts as a tap.** `X11PaneFrame` watches touches: an `ACTION_UP` on the display's own
picture (not on page chrome, not in a corner, `touchIsTheDisplays` :674-676) that never moved past
touch slop or gained a second finger
([X11PaneFrame.java:640-662](../../../app/src/main/java/com/termux/app/x11/X11PaneFrame.java#L640-L662)).
A move past slop or a second finger calls `onDisplayDrag` instead.

**Signal B, an input method's focus.** `launcherctl keyboard show|hide --source focus` goes through
`showInAppKeyboard(fromFocus)` / `hideInAppKeyboard(fromFocus)`, which hand the signal to
`onTextFocusSignal` first
([TermuxActivity.java:24735-24763](../../../app/src/main/java/com/termux/app/TermuxActivity.java#L24735-L24763)).
It is exact, needs no tap window, and is inert (returns false so the caller shows the keyboard
plainly) when the policy is inactive ([DisplayTextFocusPolicy.java:237-256](../../../app/src/main/java/com/termux/app/x11/DisplayTextFocusPolicy.java#L237-L256)).
The documented source is an fcitx5 watcher: `dbus-monitor` on `org.fcitx.Fcitx.InputContext1`,
`*FocusIn*` → show, `*FocusOut*` → hide ([X11_Display.md:188-203](../../../docs/en/X11_Display.md)),
which the doc itself says is "documented, not device-verified".

### 2.3 States and the log lines

`State` is `CLOSED`, `AUTO_OPEN`, `PINNED` ([DisplayTextFocusPolicy.java:73-80](../../../app/src/main/java/com/termux/app/x11/DisplayTextFocusPolicy.java#L73-L80)).

| Log message (tag `X11TextFocus`) | Where | Meaning |
|---|---|---|
| `entered the place[, keyboard on enter]` | :167-173 | Wall settled on Display; keyboard already up on arrival → `PINNED`, else `CLOSED` |
| `left the place` | :179-184 | Back to `CLOSED`; the keyboard is not touched, the wall's own rules own it |
| `user showed the keyboard -> PINNED` / `user hid the keyboard -> CLOSED` | :262-266 | `onUserKeyboardIntent`: toggle key, mouse key, tools with a manual source. Up pins; down hands the keyboard back to the policy |
| `tap over xterm -> show` / `already open` | :286-293 | Text cursor in the window, state `CLOSED` (or keyboard not actually up) → `AUTO_OPEN` + show |
| `tap over left_ptr -> hide` / `nothing to do` | :294-300 | Non-text cursor: hides only if `AUTO_OPEN` |
| `tap over … ignored: pinned` | :282-285 | A pinned keyboard is never taken away by a tap |
| `drag over … -> hide` / `selecting, keyboard stays` / `nothing to do` | :209-224 | Scroll or swipe that began over non-text hides an `AUTO_OPEN` keyboard; over text it is selection |
| `focus in -> show` / `focus out -> hide` / `focus … ignored: pinned` | :237-256 | Signal B |
| `inert: setting off` / `touch mode N` / `the touchpad went down` | :310-314 | `isActive()` false: setting off, not Touchscreen mode (Trackpad, Direct touch), place not on screen |

`isActive()` is `enabled && onPlace && (padUp || touchMode == TOUCHSCREEN)` (:115-117): the
launcher's touch-only gate. A pad tap counts as a tap in every touch mode while the touchpad
holds the keyboard frame.

Where the user's intent comes from: the in-app keyboard's visibility listener calls
`onUserKeyboardIntent(shown)`, skipping changes the pad frame or the wall's slide caused
([TermuxActivity.java:11748-11755](../../../app/src/main/java/com/termux/app/TermuxActivity.java#L11748-L11755)).
The policy's `Keyboard` adapter raises or lowers the in-app keyboard with reason `FOCUS`, or the
mouse-mode frame, or the phone's system keyboard where the place takes one
([TermuxActivity.java:21109-21129](../../../app/src/main/java/com/termux/app/TermuxActivity.java#L21109-L21129)).
"Keyboard off" suppresses focus-sourced raises entirely
([keyboard-off.md:19](keyboard-off.md)).

### 2.4 How it hides

Three routes, all gated on `state == AUTO_OPEN`: a tap whose answer is not a text cursor
(:294-298), a drag that began over non-text (:221-223), and a signal-B focus-out (:250-254).
`isKeyboardUp()` is asked before every "already open" decision, so a keyboard the user put down
with its own hide key is raised again by the next text tap (:287). There is no delay on hide
(contrast GNOME's 50 ms + 300 ms).

### 2.5 What it cannot see

All **(inferred from the code above)** unless cited.

- **Focus without a tap**: Tab, a script, a dialog that focuses its entry, a hardware-keyboard
  user. No tap means no window; the cursor name moves only with the pointer. Only signal B sees it.
- **Text fields whose cursor is not a text cursor**: a canvas-drawn editor, a web view with CSS
  `cursor:` overrides, a session with no cursor theme and a non-glyph cursor. The docs list this
  ([X11_Display.md:158-163](../../../docs/en/X11_Display.md)).
- **False positives**: a text cursor over *non-editable* text (terminal emulators, read-only text
  widgets, browser page text) raises the keyboard though nothing is editable.
- **A slow client**: if the app sets its cursor more than 150 ms after the tap, the window has
  closed and the decision used the old name.
- **Focus loss without a tap**: a field that loses focus because another window raises. Only
  signal B sees it.
- **Input purpose**: nothing about number/email/password fields reaches the in-app keyboard; there
  is no hint channel from the display side. (The in-app keyboard has its own layouts; no purpose
  input exists on this path in the files read.)
- **Which app / which window**: the policy has no notion of the focused X window.

---

## Part 3 — Comparison and recommendations

### 3.1 Table

| | Signal source | Show trigger | Hide trigger | Touch-only heuristic | Purpose/hints | Per-toolkit coverage |
|---|---|---|---|---|---|---|
| **GNOME / Wayland** | `zwp_text_input_v3` enable/disable → Mutter `ClutterInputFocus` | Enable while already focused (a tap on a focused field, via GTK's gesture) → panel ON; 300 ms rest, then animate | `focus_out` → 50 ms → panel OFF → 300 ms → hide; no tap-outside handler (inferred) | Keyboard object exists only if a11y setting, or touch mode + last device is a touchscreen (keyboard.js:1102-1104) | Yes: purpose picks layout, hints drive caps | GTK, Qt (text-input), Chromium/Electron with Wayland IM; **not Xwayland** clients |
| **GNOME / X11 (IBus)** | IBus panel `focus-in`/`focus-out` | focus-in → `open()` (300 ms rest) | focus-out → `close()` | Same `_syncEnabled` gate | GTK sends via IBus content type; Qt-ibus does not appear to | Toolkits using the IBus module; XIM clients through ibus-xim |
| **GNOME / X11 (AT-SPI, Caribou)** | AT-SPI focus + `EDITABLE` | Not verified | Not verified | n/a | none | Legacy; not read |
| **KDE Plasma (KWin)** | text-input v1/v2/v3 enabled; v1/v2 also explicit show/hide requests | `textInputInterfaceV3EnabledChanged` → `show()`; v1/v2 request | `hide()` on request, lock, or panel close | Only if last input handler is touch/tablet, or `KWIN_IM_SHOW_ALWAYS` | Yes (`contentTypeChanged`) | Wayland clients; X11 via Xwayland not read |
| **Phosh / squeekboard** | `zwp_input_method_v2` activate/deactivate | `active` flips on activate | `deactivate`, D-Bus `SetVisible` | By device (phone shell, always touch) | Yes (`content_purpose`) | Wayland text-input clients |
| **termux-launcher (Display)** | Cursor *name* after a tap (A); input-method focus via `launcherctl` (B) | Tap whose cursor is `xterm`/`text`/`ibeam`; or focus-in | Tap/drag over non-text while `AUTO_OPEN`; or focus-out | `isActive()`: Touchscreen mode (or touchpad frame up) | None | A: any X client that picks a text cursor; B: toolkits with the chosen IM module |

### 3.2 What the launcher could learn

1. **Make focus the primary signal, the tap secondary.** Every desktop above uses an exact focus
   feed; the launcher's A signal is a heuristic and B is opt-in shell scripting.
2. **Separate "show on focus" from "show on tap".** GNOME effectively raises on a *tap on* a focused
   field (GTK re-enable); focus alone does not. The launcher already has the tap; focus-in could be
   used only to confirm that the tapped thing is editable (removing the read-only-text false
   positive).
3. **Hide on focus-out with a short grace.** GNOME's 50 ms hold, cancelled by a following focus-in,
   avoids flicker when focus moves field to field; the launcher hides immediately on focus-out.
4. **Hint channel.** Wayland and IBus carry purpose; the launcher's pipeline carries nothing. Even
   a two-value hint (numeric/password) would need option 1 or 2 below.

### 3.3 Ranked options on an X11-only server

| # | Option | Cost | Coverage | Risks |
|---|---|---|---|---|
| 1 | **Ship the fcitx5 route as a supported feature**: a small helper in the prefix that owns the D-Bus name `org.fcitx.Fcitx5.VirtualKeyboard` and implements `ShowVirtualKeyboard`/`HideVirtualKeyboard` by calling `launcherctl keyboard … --source focus`. fcitx5 calls those on focus-in and focus-out when `virtualKeyboardAutoShow()` / `AutoHide()` are on and the client has not taken control ([instance.cpp:1043-1048, 1093-1097](https://github.com/fcitx/fcitx5/blob/5.1.11/src/lib/fcitx/instance.cpp#L1043-L1048); backend names and methods in [virtualkeyboard.cpp:28-60, 288-312](https://github.com/fcitx/fcitx5/blob/5.1.11/src/ui/virtualkeyboard/virtualkeyboard.cpp#L288-L312)) | Low: one tiny D-Bus service (a script with `dbus-send`/`gdbus` or a Python service) plus env defaults and a doc. Replaces the brittle `dbus-monitor | grep` loop | Exact focus for GTK/Qt/XIM clients that load the fcitx5 module | Needs fcitx5 installed and `*_IM_MODULE`/`XMODIFIERS` exported in every session and proot; Qt6 needs the out-of-tree fcitx5 Qt plugin (in-tree has only compose/ibus); Electron needs the same env; fcitx5 package presence in Termux **unverified**; session D-Bus must be shared. Unverified that the auto-show option is on by default or exposed in Termux's config |
| 2 | **Keep A, add B as a confirmer**: when B is configured, use focus-out as the hide authority and focus-in to suppress A's false positives; document `--source focus` hold semantics. Optionally lengthen or adapt `TAP_WINDOW_MS` for slow clients | Low (policy + tests; no new process) | Same as now, fewer false positives/negatives | Two-signal races; needs on-device logging in `X11TextFocus` to tune; does not help pure-A users |
| 3 | **IBus engine/panel bridge**: run `ibus-daemon`, a panel-service stub that receives `focus-in`/`focus-out`/`set-cursor-location` as GNOME's X11 session does (ibusManager.js:200-209), and forwards to `launcherctl` | Medium: write and package a panel service; IBus is heavier than fcitx5's D-Bus hook | Exact focus; GTK sends purpose/hints through IBus (ibusimcontext.c:665-690); Qt via ibus plugin (focus only) | Another daemon on a phone; IBus-vs-fcitx choice for users; I did not verify the panel D-Bus interface beyond what ibusManager.js consumes |
| 4 | **Own XIM server** (answer `@im=` with a launcher-owned server) | High: implement the XIM protocol (spec is 2,285 lines of HTML) incl. IC attributes, byte-order, transports | Exact focus for XIM clients only; **no Qt6, little modern Chromium/Firefox use** (they use IM modules, not XIM) | Largest effort for the narrowest coverage; no purpose/hints in XIM ([spec](https://www.x.org/releases/X11R7.7/doc/libX11/XIM/xim.html)) |
| 5 | **AT-SPI2 listener** subscribing to focus events and filtering `EDITABLE` | Medium-high: needs the a11y bus, `at-spi2-core` + `at-spi-bus-launcher`, `org.a11y.Status IsEnabled` set true, a D-Bus client in the prefix | Toolkit-neutral in principle: GTK, Qt, and Chromium/Electron when enabled; gives an *editable* flag, which the cursor name cannot | Per-app opt-in and env overrides (Chromium atk_util_auralinux.cc:122-160); performance cost of the bridge; event names and rules **unverified** here; Firefox status unknown |
| 6 | **Extend signal A inside the X server**: besides the cursor name, report which window/XIM client has focus from within lorie (the host already patches `lorieSetCursor`) | High: native C change in a pinned submodule, patch carried by `ci/x11-patch/` | Server sees X `FocusIn` events but widget-level focus does not reach X **(inferred)**; so no gain for text fields inside one window | Poor return; not recommended |

**Recommendation order:** 1, then 2 (cheap, composable), then measure with the `X11TextFocus` log
before spending on 3 or 5. 4 and 6 are not worth it.

---

## Unverified

- **GNOME/Wayland panel-ON on a plain tap** (§1.1): the chain GTK re-`enable` → Mutter
  `enable_panel` → shell `open` is assembled from three files; no document states it, and I did not
  run it. I saw no other panel-ON sender in 47.0.
- **No tap-outside hide on GNOME**, and **no OSK for Xwayland clients on GNOME/Wayland**: inferred
  from absence of code paths in the files read.
- **GTK4's IM module**, **Firefox's IM wrapper**, **Caribou's focus tracking**, **AT-SPI
  `Event.xml` / registryd**, **squeekboard's visibility state machine** and **IBus panel D-Bus
  interface** were not retrieved. Statements about them are absent or marked.
- **Qt6 on IBus not forwarding hints** is from a `grep` for `ImHints`/`SetContentType` returning
  nothing in one file.
- **Qt6 has no XIM**: from the absence of an XIM file in the two listed directories at v6.8.0.
- **fcitx5, checked from source since (2026-10-22, #129), still not on a device.** Read at tag
  5.1.23, the version Termux packages:
  - Auto-show and auto-hide default to on ([instance_p.h:229-231](https://github.com/fcitx/fcitx5/blob/5.1.23/src/lib/fcitx/instance_p.h#L229-L231)),
    and focus-in and focus-out call the virtual keyboard unless the client asked to control it
    itself ([instance.cpp:1097-1100, 1152-1155](https://github.com/fcitx/fcitx5/blob/5.1.23/src/lib/fcitx/instance.cpp#L1097-L1100));
    that flag is false unless a D-Bus client sends it ([inputcontext_p.h:161-162](https://github.com/fcitx/fcitx5/blob/5.1.23/src/lib/fcitx/inputcontext_p.h#L161-L162),
    [dbusfrontend.cpp:148-153](https://github.com/fcitx/fcitx5/blob/5.1.23/src/frontend/dbusfrontend/dbusfrontend.cpp#L148-L153)).
  - The calls go only to the UI addon of the current mode: the virtual keyboard
    (`UIType=OnScreenKeyboard`) is chosen only in on-screen-keyboard mode, and only while
    somebody owns `org.fcitx.Fcitx5.VirtualKeyboard`, which fcitx5 watches for whenever it comes
    ([userinterfacemanager.cpp:67-78, 312-345](https://github.com/fcitx/fcitx5/blob/5.1.23/src/lib/fcitx/userinterfacemanager.cpp#L67-L78);
    [virtualkeyboard.cpp:224-237](https://github.com/fcitx/fcitx5/blob/5.1.23/src/ui/virtualkeyboard/virtualkeyboard.cpp#L224-L237)).
    No backend registration is needed; the owner only has to answer the method calls. The calls
    are `ShowVirtualKeyboard` and `HideVirtualKeyboard` on `/org/fcitx/virtualkeyboard/impanel`,
    interface `org.fcitx.Fcitx5.VirtualKeyboard1` (virtualkeyboard.cpp:302-328).
  - The mode starts on-screen only where `isAndroid()` is true, i.e. built with `__ANDROID__`
    ([instance_p.h:163-165](https://github.com/fcitx/fcitx5/blob/5.1.23/src/lib/fcitx/instance_p.h#L163-L165),
    [misc.h:185-191](https://github.com/fcitx/fcitx5/blob/5.1.23/src/lib/fcitx-utils/misc.h#L185-L191)),
    which Termux's NDK build is (inferred from the toolchain). Calling fcitx5's own
    `org.fcitx.Fcitx.VirtualKeyboard1.ShowVirtualKeyboard` on `/virtualkeyboard` sets it
    anywhere (virtualkeyboard.cpp:330-338).
  - **The catch:** any key event not marked virtual switches fcitx5 to physical-keyboard mode,
    and the switch hides the virtual keyboard on its way out (virtualkeyboard.cpp:241-251,
    283-292). The in-app keyboard types into the display as X keys, so to fcitx5 it is hardware:
    a helper that passed every hide on would drop the keyboard at the first letter typed, and hear
    no focus at all after it. The helper tells the two hides apart by asking
    `org.fcitx.Fcitx.Controller1.CurrentUI` (still `virtualkeyboard` on a focus-out), and puts
    fcitx5 back in on-screen mode after a switch. Not tried on a phone.
  - Termux's x11 repository packages fcitx5 5.1.23 with D-Bus on, so the virtual-keyboard addon
    is built ([ui/CMakeLists.txt](https://github.com/fcitx/fcitx5/blob/5.1.23/src/ui/CMakeLists.txt)),
    the GTK modules (`fcitx5-gtk2/3/4`) and the Qt 6 module (`fcitx5-qt`, Qt 5 off). The `dbus`
    package builds `dbus-test-tool` unconditionally (dbus 1.16.2 `tools/meson.build`), which is
    what owns the name; I did not see the shipped file list.
- **Session D-Bus in the launcher's X11 display, from source.** There is none to share: the
  display server is started as a plain task, a drawer app's script sets `DISPLAY`, touch and GPU
  variables only, and only XFCE is handed a bus, a private one from
  `dbus-launch --exit-with-session` (`X11LinuxAppRunner.sessionBody`). Termux's dbus is built
  with X11 autolaunch on, which could give clients on one display a common bus by itself when
  `dbus-launch` is installed; not relied on, not checked. So the helper starts a bus per display
  and the runner hands its address to prefix apps.
- **Everything in Part 2** is read from source; no `X11TextFocus` log was captured for this note.
