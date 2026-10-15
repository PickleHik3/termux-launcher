## v1.0.6

A Low-memory preset for weaker phones, smoother scrolling, and icon packs that stay applied.

### New

- Settings → App behavior has a Low-memory preset switch that puts a weak phone on the Solid look, an opaque terminal and less idle activity; turning it off brings your look back.

### Changes

- Dragging, flinging and settling a long transcript is smoother, and does far less work per frame, which shows most on phones with a weak graphics chip.
- The sessions drawer widens with your font size, its rows and Save label wrap before they are cut short, and its shade follows the terminal's corners.

### Fixes

- Videos and animations that programs draw in the terminal with kitty graphics play smoothly, without flicker.
- Icons from a symbol font inside text that a program draws at a larger size show instead of an empty box, and a wide icon drawn that way fills its space like the plain one does.
- Choosing System for icon packs brings back stock icons everywhere, and large packs such as Arcticons now reach every app and stay applied after a restart; Pinned app icons only stays as you set it.
- Removing a pinned app in Edit pinned apps unticks it in the list too, so it cannot slip back in.
- Applying a new wallpaper keeps your Look's blur and opacity instead of dropping the glass back to a clear pane.
- On Android 10 with the fullscreen property on, the extra keys no longer stand a keyboard's height above the keyboard, and the terminal keeps its rows.
- The Voice input pause and silence timeout you choose take effect; some phones were using the defaults.
- The Model centre shows the full size of the KittenTTS voice.

### Editions

Shipped as `nix-v1.0.6` and `vaj-v1.0.6`. Nothing is exclusive to an edition.
