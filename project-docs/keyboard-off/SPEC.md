# Keyboard off — spec v1 (2026-09-27)

Hiding the keyboard lasts only until the next tap on the terminal, which raises it again. Some
users want it to stay shut: they type on a hardware keyboard, read long output, or drive a
full-screen program by touch. **Keyboard on/off** switches the keyboard off until they ask for it
back.

Naming: "hide" and "show" mean one trip down or up. "Off" and "on" mean the standing state. In
code the state is `keyboard_turned_off`, and the action is `keyboard.toggle_enabled`.

Branch: `feat/keyboard-toggle`, cut from dev f6d82b5a.

## Behaviour

| | Hidden (today) | Off (new) |
|---|---|---|
| Tap on the terminal | raises the keyboard | nothing; the tap is only a tap |
| Tap in a mouse-tracking TUI | raises it; the program still gets the click | the program gets the click, the keyboard stays down |
| A text field takes focus (`keyboard.show --source focus`, X11 text focus) | raises it | ignored; the call answers 409 `unavailable` |
| Keyboard key (extra key, `Ctrl+Alt+K`, palette **Toggle keyboard**, Display place key) | raises it | **turns it on** and raises it |
| `keyboard.show` (manual) | raises it | **turns it on** and raises it |
| **Keyboard on/off** | turns it off, puts it down | turns it on, raises it |
| App restart, activity recreate | keyboard restores as it was | stays off; the keyboard starts down |
| Settings height editor | shows the keyboard | still shows it, since it has to show the rows being resized |

Each flip posts a notice: "Keyboard off. The keyboard key turns it back on." / "Keyboard on".

The keyboard key turns "off" back on rather than doing nothing. A user who switched the keyboard
off and then presses its key clearly wants it. Without this, the palette would be the only way
back.

Off holds whichever input method is chosen in **Settings → Keyboard** and leaves that choice alone.
It is not the Settings "none" input method, which is a permanent configuration rather than a
toggle you reach from the palette.

## Where it lives

- **Action.** `keyboard.toggle_enabled` in `LauncherToolRegistry` (category keyboard, low risk,
  unbound, no session or in-app-keyboard requirement), titled **Keyboard on/off**. It appears in the
  command palette and, through `ExtraKeyEligibility` (band `LAUNCHER`), in the extra-keys picker as
  `tool:keyboard.toggle_enabled`. `TerminalActionDispatcher` routes it to
  `TermuxTerminalViewClient.toggleKeyboardTurnedOff()` and answers `{"enabled": <bool>}`.
- **State.** `TermuxAppSharedPreferences.is/setKeyboardTurnedOff`, key `keyboard_turned_off`,
  default false.
- **In-app keyboard.** `TermuxInAppKeyboard.show()` refuses every `ShowReason` except
  `HEIGHT_ADJUSTMENT` while off. That single gate covers terminal taps, focus signals, the
  typing-surface and display-frame raises, and state restore. `onCreate` and the Settings
  re-enable path start the keyboard down while it is off. `setTurnedOff(off)` writes the
  preference, then hides the keyboard (`KEYBOARD_ACTION`) or shows it.
- **Android keyboard.** `TermuxTerminalViewClient` sets the disable-soft-keyboard flags when the
  keyboard is turned off, the same flags Settings' disabled mode uses. `setSoftKeyboardState` keeps
  them set on startup and resume. Turning it on clears the flags and shows the IME, unless the IME
  is disabled in Settings.
- **Tap paths.** `onSingleTapUp` and `onMouseTrackingTap` return before any keyboard call while
  the keyboard is off.
- **Turning it back on.** `onToggleSoftKeyboardRequest` (keyboard key, `Ctrl+Alt+K`, palette
  toggle), the Display place's `toggleKeyboardVisibility`, and the host's `showInAppKeyboard` with
  a manual source all call `setTurnedOff(false)` first.

## Known gaps

- On the Display place, when it takes the phone's own keyboard (`displayTakesSystemKeyboard`), a
  focus signal can still raise that keyboard. Off is scoped to the launcher's keyboard and the
  terminal's IME.
- The palette row has a fixed title. It does not say whether the keyboard is currently on or off.

## Device checks owed

1. Built-in keyboard: turn it off from the palette. Tap the terminal, tap in `htop` and `vim`, and
   restart the app. The keyboard stays down each time. The keyboard key brings it back.
2. The same with the Android keyboard selected in Settings.
3. Add `tool:keyboard.toggle_enabled` to the extra-keys row and flip it both ways.
4. While off, open the height editor in Settings. The keyboard shows for editing and goes down
   again after.
