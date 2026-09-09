# Keyboard types + Display auto-keyboard — spec v1 (decided 2026-09-09)

Three things: the in-app keyboard gains **Floating** and **Split** types beside today's docked
one; a tool cycles between them from every surface; on the Display place in **Touchscreen** touch
mode the keyboard opens when a text field is tapped and closes when the tap lands elsewhere.
Trackpad and Direct touch stay stock. Mouse mode's touchpad follows the keyboard into each type.

Naming: "layout" already means the key arrangement (qwerty, dvorak). The docked / floating /
split choice is **keyboard type** in copy and `KeyboardForm` in code.

Integration branch: `feat/keyboard-forms` (worktree `app/wt-keyboard-forms`), cut from dev
9ce48afe. Phases merge here; the branch merges to dev once the peer's blur/chrome GPU work lands.

## Model

| Setting | Scope | Values | Notes |
|---|---|---|---|
| Keyboard type | place × orientation | docked · floating · split | Key `place.<place>.<orientation>.keyboard_form`, resolved in `PlaceLayout`. Default docked everywhere (D2). The cycle key edits the value for the current place × orientation and it sticks. |
| Floating width | global | scale, default 60 % landscape / 90 % portrait | Slider only in v1; pinch later (D5). |
| Floating position | place × orientation (memory) | x, y | Clamped inside the content bounds. |
| Split gap | global | fraction, default 25 % landscape / 12 % portrait | One slider. |
| Keyboard follows text fields | Display place | on · off | Default on (D3). Inert unless touch mode is Touchscreen. |

- **Docked** — today. Resize or overlay per the existing Display keyboard mode.
- **Floating** — always overlays, on every place including the terminal. `FloatingKeyboardFrame`
  wraps the host: top grab handle, drag with clamping, remembered position. `KeyboardOverlayPolicy
  .overlays()` is true whenever the type is Floating. Height cap takes the frame as its reference
  through `KeyboardGeometryChoreographer`; the height-scale pref still applies.
- **Split** — docked at the bottom, every row parted at its midpoint by a pure `KeyboardData →
  KeyboardData` step in `LayoutModifier` (add the gap to the `shift` of the key whose cumulative
  width crosses half the row; the space key splits in two). `Keyboard2View` paints its background
  only under key spans and returns `false` for a touch that starts in the gap so it falls through.
  Follows the place's docked behaviour: overlay on Display/Home, resize on the terminal (dead gap
  there, documented).

## Tools (one registry → extra keys row, in-app key, chords, palette, launcherctl)

| Tool | Args | Palette | Copy |
|---|---|---|---|
| `keyboard.cycle_form` | direction forward\|backward | "Next keyboard type" | Switch the keyboard between docked, floating and split. |
| `keyboard.set_form` | form docked\|floating\|split | one row per type, current marked | Use a floating keyboard. |
| `keyboard.show` / `keyboard.hide` | source manual\|focus | "Show keyboard" / "Hide keyboard" | Open the on-screen keyboard. |

Cycle order Docked → Floating → Split → Docked (D4). The key is offered in the merged extra-keys
catalogue (`InAppKeyboardExtraKeys`) and on the classic row, off by default. Switching type with
the keyboard open re-hosts it without closing. Settings: Keyboard & input → Layout gets a
"Keyboard type" row per place beside the keyboard mode row, plus the two sliders.

## Mouse mode per type

The touchpad takes the *keyboard frame*, whatever its type. Docked: unchanged. Floating: the
touchpad fills the floating frame and drags with it. Split (D1): the gap becomes the touchpad and
both halves keep typing; if the gap is under 160 dp the touchpad takes the whole frame as docked.

## Display: keyboard follows text fields

`DisplayTextFocusPolicy`, owned by the X11 pane host, hears "text focus in/out" from two signals
and drives `TermuxInAppKeyboard` with its own reason. States: Closed → (text focus in) →
Auto-open → (tap elsewhere / focus out) → Closed. A user toggle from any state goes to Pinned,
where focus events are ignored; a user toggle or leaving the Display place leaves Pinned.
Auto-hide only closes what auto-show opened. "Keyboard on enter" decides the state on arrival.

- **Signal A — cursor name (zero setup).** Native patch `ci/x11-patch/0002-…`: `lorie.h` gains
  `EVENT_CURSOR_NAME_CHANGED` on the server → activity socket (variable payload like the clipboard
  event); `InitOutput.c:lorieSetCursor` reads `pCurs->name` via `NameForAtom` and sends on change;
  `activity.cpp` calls new `@Keep LorieView.onCursorNameChanged(String)`, resolved beside
  `resetIme`. Rebuilt by `build_x11_native.yml`, prebuilts recommitted, UPSTREAM.md updated.
  Text names: `xterm`, `text`, `ibeam`, `vertical-text`; anything else, or no name, is not text.
- **Signal B — input-method focus (opt-in).** `launcherctl keyboard show|hide --source focus`,
  background-safe. Docs recipe: fcitx5 focus-in/out on D-Bus → the two routes, plus the
  `GTK_IM_MODULE`/`QT_IM_MODULE` lines. Verified only if fcitx5 installs on the test device.
- **Tap window.** A tap is an ACTION_UP in Touchscreen mode with no drag; the last cursor name
  within 150 ms after it decides. Cursor changes with no tap never toggle. Taps on the keyboard or
  launcher chrome are not display taps.
- **Copy.** Display → Touch → *Keyboard follows text fields*: "Open the keyboard when you tap a
  text field and close it when you tap elsewhere." Disabled in other modes with "Available in
  Touchscreen touch mode." Keyboard & input → Layout → *Keyboard type*: "Docked, floating or
  split, remembered for this place."

## Build plan

| # | Branch | Delivers | Depends on | Gate |
|---|---|---|---|---|
| 1 | `feat/keyboard-form-model` | `KeyboardForm` in `PlaceLayout`/store; the two form tools, palette rows, extra-keys catalogue key, settings rows and sliders (prefs only); `keyboard.show/hide` with source; overlay policy honours Floating; unit tests | — | unit tests; palette rows visible in waydroid |
| 2 | `feat/keyboard-floating` | `FloatingKeyboardFrame`: handle, drag, clamp, remembered position, width slider wired, height-cap reference; touchpad fills the frame in mouse mode | 1 | waydroid landscape terminal: type, drag, rotate, mouse mode on/off |
| 3 | `feat/keyboard-split` | `LayoutModifier` split step, gap-aware background and passthrough, gap slider wired; D1 touchpad heuristic | 1 | waydroid: gap taps reach content; both halves type; touchpad in the gap |
| 4 | `feat/x11-cursor-name` | `ci/x11-patch/0002`, `LorieView.onCursorNameChanged` stub, UPSTREAM.md; orchestrator dispatches the native rebuild and commits prebuilts | — | logcat shows names from xterm and a GTK app in waydroid |
| 5 | `feat/display-text-focus` | `DisplayTextFocusPolicy`, tap window, pinned logic, setting + copy, Touchscreen gating, fcitx5 recipe | 1, 4 | waydroid Touchscreen: tap field opens, tap elsewhere closes, toggle pins; Trackpad unchanged |

Phases 1 and 4 run in parallel; 2 and 3 after 1 merges; 5 after 4. Agents commit, never push,
never touch devices. Device gates run in waydroid via `wd`, never the emulator; waydroid is
closed after each gate. The peer's dev work touches `TermuxActivity.java`; expect a merge there.

## Risks

| Risk | Limit |
|---|---|
| Cursor has no name (no theme, raw Xlib app) | Manual toggle and Signal B still work; docs say install a cursor theme; phase 4 gate checks xterm + a GTK app |
| Native/Java commit drift | Same commit for both halves; the workflow refuses a mismatch |
| Flicker on busy cursors | Only the last name in the post-tap window counts |
| Floating hides the terminal cursor line | Drag it; Floating is never a default |
| Split gap dead in resize mode | Documented; terminal overlay is out of scope |
| Height cap assumes one root | Phase 2 plumbs the frame reference and adds a geometry test |
