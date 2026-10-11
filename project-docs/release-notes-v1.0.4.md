## v1.0.4

Border gestures that leave the terminal alone, a status bar that shows what is playing, smoother pictures and mouse support for more programs, and pinned apps that show at every icon size.

### New

#### Launcher

- Slide sideways off the top or bottom border to change place. The border lights up under your finger, and glows while a hold can change page.
- The Places help card outlines both page borders and teaches the sideways swipe.

#### Status Bar

- What is playing shows in the status bar as soon as it starts. Settings › Status bar › Indicators has a Media switch to hide it.
- Window chips that run out of room fade out at the ends and scroll, and the readings move closer to make room.
- The AI glyph drains like a gauge as its idle unload approaches, instead of showing a countdown.

#### Terminal

- Programs that ask for mouse positions in pixels now get them, from touch, mouse mode and a real mouse alike.
- Moving a real mouse over the terminal reaches programs that follow the pointer even with no button held.

#### Display

- The Display page offers to install the keyboard layout package the display needs.

### Changes

- Changing page, and the swipes that open the keyboard and the status bar, now start only on the border around the terminal and the space just outside it; the keyboard and status pills still work. Taps, long presses and mouse clicks reach the terminal all the way to its edges.
- Dragging sideways on a side border changes page instead of being taken as the phone's back swipe.
- The blurred wallpaper behind a page stays fixed to the screen while the page sinks or tips.

### Fixes

- Pinned apps and the app drawer no longer disappear with the Docked style at the smallest icon size (#49).
- Animated pictures and page loads in terminal programs no longer flicker, even over a slow connection.
- Built-in widgets fill their cards on taller grids instead of sitting in a band of empty space.
- The centred clock keeps its place beside the media row and pinned cards.
- Linux apps opened before the display is running now come up maximised, and the GUI apps setup installs the window manager the display starts.

### Editions

Shipped as `nix-v1.0.4` and `vaj-v1.0.4`. Nothing was exclusive to an edition.
