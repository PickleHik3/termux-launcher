# Keyboard glass over the display window (2026-10-29)

Grilled 2026-10-29. The in-app keyboard, when it sits over an X11 display window, wears glass:
the Look's opacity and frost, with an editor-only override scoped to the display, a snapshot blur
of the display behind it, and an opt-in live blur.

## Today

- `x11_keyboard_over_display` (res/xml/x11_display_input_preferences.xml:24-28) is `overlay` or
  `resize`, stored per orientation through `PlaceLayoutStore.setKeyboardMode` (key
  `keyboard_mode`, place/PlaceLayoutStore.java:152; fragment
  X11DisplayInputPreferencesFragment.java:107-113). Overlay floats the keyboard over the content
  (`KeyboardOverlayPolicy.contentOverlapPx`, place/KeyboardOverlayPolicy.java:187-205;
  `StackLayout` :66, :131-132).
- `chrome/KeyboardMaterialPolicy` (:13-22 javadoc, `hostMaterial` :52, `opacityApplies` :66) makes
  the overlay keyboard ignore the Keyboard Look's opacity: overlay = solid, docked = the Look's
  glass. That stays true over terminal windows.
- The keyboard Look lives in `surfaces/SurfacePresets` (key opacity captured at :644) and the
  editor's `surfaces/cards/KeyboardCard` rows FORM, SECTION_GLASS, FROST, TINT, OPACITY,
  OPACITY_LINK, KEY_OPACITY (`EditorRows.java:123`, `OpacityLink.java:24-25`; bounds
  `AppearanceLooks.java:94-95`). Frost blurs the shared pre-blurred wallpaper frame
  (`terminal/PaneGlassBackdropView.java:30-56`), never live content.
- The display is a `SurfaceView` (`x11-server/.../LorieView.java:29,56`) held by
  `x11/X11PaneFrame` (`mDisplay` :156, `display()` :687). No `RenderEffect` or AGSL shader on a
  sibling view can sample it; the only read path is `PixelCopy`, which `X11PaneFrame` already
  uses for a one-shot still (`:1172`, cache expiry :1203-1265). `chrome/GlassRefraction` (:83,
  `RuntimeShader` at :2110) is the AGSL program applied over a view's finished pixels;
  `FancierGlassPolicy` gates it on API level.

## Decisions

1. **Scope: display windows only.** Over a display window, in overlay and in resize mode, the
   keyboard wears opacity and frost. Over terminal windows the overlay keyboard stays solid as the
   policy intends. Nothing else about the keyboard changes.
2. **Default: follow the Look.** With no override the keyboard uses the global Keyboard Look's
   opacity and frost. The display-scoped values appear only once the user switches to Custom.
3. **Override, editor only.** The Appearance editor's Keyboard card, opened with a display window
   focused, gains an **Over the display** switch: *Follows the keyboard Look* / *Custom*. Custom
   shows **Opacity** and **Frost** rows writing display-scoped keys; a reset returns to following.
   Key opacity, tint, form and ink stay global. No Settings row.
4. **One setting for both orientations**, unlike keyboard mode.
5. **Blur source: a still, refreshed on change.** Frost over the display blurs a `PixelCopy` still
   of the display region under the keyboard, taken when the keyboard shows and refreshed at most
   every 500 ms, only when the display reports new frames. In resize mode the keyboard is beside
   the display, so frost keeps the normal wallpaper backdrop with the override's values.
6. **Live blur, opt-in.** A **Live blur** knob under Custom refreshes the still at a capped rate
   (about 12 fps) at 1/8 scale while the keyboard is up, pausing when the display emits no frames.
   Hidden, and the still stands in silently, when fancier glass is off, lazy mode is on, the
   low-memory preset is on, or the setup wizard's device tier is low.
7. **Legibility.** Key caps keep the global Key opacity and the legibility contract's contrast
   floor (ADR 0014). The display-scoped Opacity row drives only the keyboard background, which may
   go fully clear.
8. **Touches unchanged.** The keyboard swallows every touch in its bounds; translucency is visual.

## Keys

Display-scoped, written by the editor only:

| Key | Type | Default | Meaning |
|---|---|---|---|
| `x11_keyboard_look_custom` | bool | false | Custom when true, else follow the Look |
| `x11_keyboard_opacity` | int | from the Look at switch time | background opacity, same bounds as the Look's |
| `x11_keyboard_frost` | int | from the Look at switch time | frost strength, same bounds as the Look's |
| `x11_keyboard_live_blur` | bool | false | live refresh of the still |

## Implementation notes

- `KeyboardMaterialPolicy.opacityApplies` gains the display-window case: true when the focused
  window is an `X11PaneFrame`, in either mode, with the values resolved by a small
  `DisplayKeyboardLook` (follow vs custom).
- The still: reuse `X11PaneFrame`'s `PixelCopy` path with a region and a handler cadence, scaled
  down before blurring with `RenderEffect.createBlurEffect` (`RealtimeBlurView` :250 shows the
  call). The keyboard backdrop gets a second source besides the wallpaper frame in
  `PaneGlassBackdropView`.
- Live mode is the same path on a timer; both stop when the keyboard hides or the window loses
  focus.
- Editor: `KeyboardCard` adds the switch and the two rows only when the editor's focused window is
  a display; `OpacityLink` must not link the display-scoped opacity to key opacity.

## Acceptance

- Over a display in overlay mode with the Look at 60% opacity: the X app shows through the
  keyboard background; caps stay readable on a white app window and on a black terminal in X.
- Custom at 0% background: only caps and the frost tint remain; typing still lands in X.
- Frost on: the blur updates within a second of the app repainting; no readback while idle
  (watch `adb shell dumpsys gfxinfo` and the PixelCopy handler log).
- Live blur on: the blur follows a moving window at about 12 fps; the knob disappears with
  fancier glass off or lazy mode on, and the still takes over without a visual jump.
- Terminal windows: overlay keyboard unchanged (solid).
- Rotation keeps the same values; resize mode shows the same override values over the wallpaper.
- Pong is the truth for jank: no dropped frames while typing with live blur on a mid-tier phone.

## Out of scope

- Touch pass-through between keys.
- Per-orientation values.
- A Settings page row (editor only).
- Changing the SurfaceView to a TextureView.
