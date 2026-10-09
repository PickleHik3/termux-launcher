# Learn the launcher

This page explains the three places, what is on screen, the gestures that move you around, and
where to find help. Read it once after [Get started](Get_Started.md).

## The three places

The launcher is up to three places side by side, as your **Launcher mode** allows:

- **Home**: your widgets. See [Widgets](Widgets.md).
- **Terminal**: your shells, in sessions, windows and panes.
- **Display**: a Linux desktop or X11 apps. See [The Linux display](X11_Display.md).

They sit in a ring, so from any place the other two are one step away, one to each side. Only one
place is on screen at a time. The terminal never changes size for the others, so nothing reflows
in your shells when you move, and your sessions keep running while you are away. The launcher
reopens on the place you left.

## What is on screen

In portrait, on the terminal, from top to bottom:

- **Status bar**: the place icon and clock, the session number, your windows, and CPU, memory and
  weather readings. See [Status bar](Status_Bar.md).
- **Terminal**: the focused shell, or the panes of the current window.
- **Dock**: pinned Android apps, the A–Z index and the extra keys. See
  [Home screen and apps](Home_Screen_And_Apps.md) and [Extra keys](Extra_Keys.md).
- **Keyboard**: the built-in terminal keyboard, on by default. See [Keyboard](Keyboard.md).

In landscape the pinned apps become a rail on the left by default. Every bar can be moved to another
edge or hidden; see [Layout and full screen](Layout_And_Full_Screen.md).

## Move around

- **Change place.** Hold the page's border (the frame line around the terminal, the widgets or the
  display, on any side) until it ticks and sinks under your finger, then drag sideways. A short
  drag goes back; a longer drag or a flick lands on the next place. A tap or a swipe on the border
  without the hold belongs to the page, so it never changes place by accident.
- **Tap a place icon.** The other two places peek in from the edges of the status bar. Tap one to
  go there.
- **Keyboard.** Swipe up from the bottom border to open the keyboard, and down to put it away. It
  works on every place.
- **Status bar.** Swipe down from the top border to open the status bar, and up to fold it. This
  works while the bar stands along the top. A swipe from Android's own strip at the very top of the
  screen still pulls the notification shade.

The corners are not the border. They hold the corner tab (below).

## The corner tab

Hold any corner of a page to show its tab:

- **Terminal**: **Appearance**, minimal mode, automatic tiling, **Open settings** (cog) and **?**.
- **Widgets**: **Edit widgets**, **Add page**, **Appearance**, minimal mode and **?**.
- **Display**: turn the display on or off, minimal mode, settings, **Appearance** and **?**.
- **A split pane**: **Close**, **Move** and **Maximise** instead.

Tap away from the tab to put it away.

## Sessions, windows and panes

```text
Session
└── Window
    ├── Tiled pane
    ├── Tiled pane
    └── Optional floating pane
```

- A **session** is the top-level group, shown by the number at the start of the status bar.
- A **window** is a tab inside a session, shown as a pill beside the number.
- A **pane** is one shell. A window can be split into several panes.
- A **workspace** is a saved set of sessions, windows, panes and working directories.

Closing a pane, window or session ends its shells. Switching between them does not.
[Panes, windows and sessions](Panes_Windows_And_Sessions.md) covers all of it.

## Try these first

- Tap a pinned app to launch it. Slide along the A–Z index, drag up to the app you want and
  lift.
- At an idle prompt, type `%settings` to list apps matching "settings" in the dock.
- Tap `+` in the status bar for a new terminal window.
- Swipe up on the space bar for the command palette, or press `Ctrl+Alt+Shift+P` on a hardware
  keyboard. Search for `split`, `workspace`, `font` or `settings`. See
  [Command palette and actions](Command_Palette_And_Actions.md).
- Hold on terminal text to select and copy it. In a full-screen program such as htop or nvim, the
  same hold makes your finger the mouse. See [Touch, links and clipboard](Touch_Links_And_Clipboard.md).
- Tap Ctrl, then Alt. While both are latched, a strip in the top-right corner shows what every
  other key does.

## The keyboard

Fresh installs use the built-in terminal keyboard. **Settings → Keyboard → On-screen keyboard**
chooses **Built-in**, **Android** or **Off**. Swipe up on Enter to dictate; see
[Voice input](Voice_Input.md). Everything else about typing is on [Keyboard](Keyboard.md).

## Optional components

None of these are needed for the launcher and terminal:

- **Termux:API**: install the build from the same edition family when scripts need Android APIs.
- **Termux:Styling**: its colour schemes and `~/.termux/font.ttf` still work. The launcher's own
  font picker is the palette's **Terminal fonts**; see [Terminal fonts](Terminal_Fonts.md).
- **Shizuku**: an optional privileged backend, used for example by double-tap to lock. See
  [Shizuku](Shizuku.md).
- **On-device AI**: local models under **Settings → On-device AI**. See
  [On-device AI](On_Device_AI.md).
- **`tlstore`**: the launcher's tool store, for extras such as fastfetch, the sigye clock, Claude
  Code and kitten. See [tlstore](Tlstore.md).

## The tour

The tour plays once, over the real home screen, after first launch. Each step glows the control it
wants you to try, and **Skip** is on every card. In order:

1. How you will use the launcher (the Launcher mode).
2. Hold a corner, then tap **?**.
3. Hold the page border and drag to the next place, then back.
4. Swipe up and down on the bottom border for the keyboard.
5. Swipe down and up on the top border for the status bar.
6. Hold an empty spot in the apps row to pin apps.
7. Swipe down on the apps row to open the app drawer (on a side rail, swipe right on a left rail
   or left on a right rail).
8. Swipe up on the space bar for the command palette.
9. After an update only: an offer of the new extra-keys row.
10. Use the launcher as your home screen.
11. A closing card with tips, a **Copy** button for the `tlstore` commands it mentions, and
    **Read the docs**.

Replay it from **Settings → About & help → Replay tour**.

## Get help

Hold any corner for its tab, then tap **?** to see what the controls on that place do. **Help** is
also in the command palette and in **Settings → Apps → Help**, and it opens for the place you are
on. On the terminal it opens by topic, grouped from **Find your way** to **Something missing?**:
pick what you are stuck on. **Try it** on a topic closes help and walks you through that action.

These guides are also linked from **Settings → About & help → Documentation**. To report a bug,
use **Send feedback** there, and see [Troubleshooting](Launcher_Troubleshooting.md) first.
