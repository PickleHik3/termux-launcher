## v1.2.2

A fixes release: the Nix edition installs again, the dock keeps its pins, and on-device AI explains a refused or stopped load.

### Fixes

#### Nix edition

- A fresh install of the Nix edition sets up its packages again (#181).

#### Dock and app drawer

- Unpinning one app no longer removes your other pins, and a full dock no longer shows "pin your apps".
- Holding a pinned app opens its menu and never picks a menu item by itself, so a long press can't unpin or uninstall.
- In the Categories drawer, pulling down on an open folder closes just the folder.
- With **Use system keyboard** on, a hardware keyboard types into the drawer's search, not the shell behind it.
- The drawer's search over a running Linux display brings up the keyboard.
- The A–Z letters sit centred on their pill, and the floating icon strip keeps its icons while it fades.

#### Terminal

- Typing on the in-app keyboard ends a text selection and jumps back to the bottom.
- Long-pressing a word no longer selects the full stop or comma after it.
- Pictures in the terminal make room by dropping old hidden ones instead of refusing new ones.
- Text and pictures stay inside their pane, and an empty pane shows your wallpaper instead of black.
- Some full-screen programs redraw straight away after a resize.
- Back returns to the launcher when no session is open.
- The welcome box fills a narrow pane.
- With two pages of extra keys, a swipe turns exactly one page.

#### Linux apps

- Text you copied before the display opened can be pasted into Linux apps.
- Termux's Chromium opens from the drawer again, and an app that can't start now says so.
- The graphics benchmark's cleanup removes only what it installed.
- Closing the display window gives typing back to whatever had it before.

#### On-device AI

- When a load is refused because the accelerator failed before, the message says so; ask for that accelerator by name to try it once more (#191).
- When Android closes the AI to free memory during a load, the message says so and suggests closing apps or using CPU (#191).

#### Home, widgets and look

- Clock and date widgets are no longer cut short after Undo.
- A widget moved to another Home page keeps its place.
- The terminal clock no longer drops its seconds or AM/PM.
- Fullscreen uses the area beside the camera.
- Glass edges follow rounded corners at any radius.
- Undo in the Appearance editor no longer shifts the title.
- Settings choices stack only when a label can't fit, and only the selected one shows a check.
- The notice pill sits in the right row when the terminal is partly off screen.
- The sessions drawer's Saved tab always lists your saved sessions.
- The herdr theme keeps sidebar rows you wrote yourself.
- The app drawer's keyboard setting is now called **Use system keyboard**.
