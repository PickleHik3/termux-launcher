## New

### Home screen

- The home screen is three places side by side: your widgets on the left, the terminal in the middle and a Linux display on the right. Swipe left or right on the status bar to move between them; past the last place you come back round to the first.
- The place you are on shows its icon beside the clock — a house for Widgets, a prompt for the terminal, a screen for the display — and the other two peek in from the bar's edges. Tap one to go there.
- The row under the clock belongs to the place on screen: your session and windows on the terminal, the apps open on the display, and on Widgets the stats and the weather line up under the clock, with the weather written out in full.
- The launcher comes back to the place you left it on, and opens there after a restart.
- Go to Widgets, Go to Terminal and Go to Display are actions you can put on the extra-keys row, on the in-app keyboard or on a key chord.
- The terminal never changes size when you move between places, and the other two places sit inside the same frame as the terminal.
- Widgets are laid out for the room they really have, so wide widgets no longer lose their edges.

### Linux display

- Run a Linux desktop or X11 apps beside the terminal. Turn it on from the Display place or from Settings ▸ Launcher & apps ▸ Linux display, install the keyboard layouts it needs, and start it with the button on the page or with `termux-x11 :0` in a shell. Everything you know from Termux:X11 keeps working, including `termux-x11-preference`. A display only ever starts when you ask for it.
- Linux apps you install (`pkg install firefox`, say) appear in the app drawer with their icons and a small prompt badge. Tapping one starts the display if needed, opens the app full size and takes you there; they can be pinned and searched like Android apps. Switch the listing off in Display options.
- While you are on the Display place the status bar lists the apps open on the display; tap one to bring it to the front.
- Hold the display for a short menu at the bottom edge with Start or Stop and Display settings.
- On the Display place the keyboard is the display's: every key and every chord, from the in-app keyboard or a hardware one, reaches the Linux program. Leave the display by swiping the status bar, tapping a place icon or pressing Home.
- The Display place remembers whether the keyboard was up when you left it, and comes back that way.
- Alt+Tab switches between windows on the display with the default window manager.
- Display options (Settings ▸ Launcher & apps): touch as trackpad, touchscreen or direct touch; resolution the same as the screen, scaled, fixed or custom; text and icon size; clipboard sharing; the window manager started with the display; the mark the Display place wears in the status bar; and how the launcher starts a display — with the launcher, with a command of your choosing, pointing new shells at it, and two compatibility switches for GPUs that draw black or with wrong colours.
- `launcherctl x11 gpu` says what your phone's GPU can do for Linux apps and, with `--env`, prints the exact settings to use; the same answer sits at the bottom of Display options.
- `termux-x11-gpu-setup` tries every graphics profile that fits your phone with a short 3D test, keeps the one that works best, removes the rest and explains each step as it goes. It works on apt and pacman installs and, with a Debian proot installed, tests inside the container too.

### Mouse mode

- Mouse mode is an action for the extra-keys row, the in-app keyboard or a chord; the shipped row has it on the keyboard key's swipe-up. In the terminal every touch becomes the mouse for programs that take one: a finger is the left button, two fingers turn the wheel, and a fast lift keeps it turning. A program that does not track the mouse gets nothing typed at it.
- On the Display place, mouse mode swaps the keyboard for a touchpad of the same size with a laptop's gestures: one finger points and taps, two fingers scroll, pinch to zoom and tap for the right button, three fingers tap for the middle button, swipe sideways to switch windows and swipe down to bring the keyboard back.
- A small mouse at the end of the status bar shows the mode is on.

### Terminal

- The session browser, the workspace panels, scrollback search and quick select rise out of the terminal's bottom edge instead of floating over it.
- A window pill shows one short item — the open file, the process or the directory — and its ring, bell, tick and cross all sit in the icon's spot.

### Local AI

- TAI sizes its context window to the phone's memory, and a conversation that continues the previous one is not read again from the start.

## Changes

- The pull-down status pane is gone; the widget grid it held is the Widgets place of the home screen.
- The status bar's own edge lines are gone; it wears the same thin rim as the dock.
- The shipped extra-keys row is keyboard, new session, Widgets, Terminal, Display, split and workspaces.

## Fixes

- A window pill's working ring no longer keeps turning after the command has finished, and a command that failed shows a cross.
- Swiping across the window pills no longer slides the next place in once the pills run out.
- Editing pinned apps: the search field and the app list stay above the keyboard.
- A large custom wallpaper no longer freezes the launcher while its blur is prepared.
- TAI's Add a model dialog keeps its Import and Cancel buttons on screen.

## Editions

- Nix and VAJ: nothing exclusive in this release. The Linux display's packages come from each edition's own package source; the VAJ repository does not carry them yet.
