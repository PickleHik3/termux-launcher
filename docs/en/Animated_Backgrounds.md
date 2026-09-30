# Animated backgrounds

An animated background is a wallpaper the launcher draws itself, behind everything. It moves slowly,
and every glass surface (the status bar, the dock, the keyboard, the A–Z index, the pane slabs and
the corner tabs) shows it blurred and in register, just as it does a photo.

These are the launcher's own backgrounds, written for a portrait OLED screen: dark, low contrast and
slow, so the panel stays mostly off and the battery cost stays small. They are not a system live
wallpaper, and they are not a video.

## Requirements

- **Fancier Glass** is on (**Settings → Appearance**).
- Android 14 or later.

The **Animated** row in the wallpaper picker shows only when both hold. On Android 13 Fancier Glass
works but the row stays hidden. Turn Fancier Glass off and the background stops on its still; turn it
back on and it carries on.

## Pick one

1. Open the wallpaper picker.
2. Choose a tile in the **Animated** row. The tiles are still pictures, so the picker itself costs no
   battery.
3. For each tile, choose the palette (below).

The built-ins are **Aurora** (slow ribbons), **Mesh** (a four-colour gradient that drifts), **Tide**
(soft waves) and **Rain** (sparse glyph-cell rain). Choosing a photo replaces a background in one
step. A wallpaper set from another app stops the animation on its own.

From the terminal:

```
launcherctl wallpaper list-builtins
launcherctl wallpaper set --builtin aurora --palette material
```

`launcherctl wallpaper` also reports whether a background is animated and playing, and why not when
it isn't: `api`, `fancier_glass_off`, `paused` or `killed`. See [LauncherCtl API](LauncherCtl_API.md).

## Material or own palette

- **Material** takes four colours from your launcher's Material palette, so the background matches
  the status bar, dock and keyboard. If you set your own colours in `colors.properties`, they reach
  the background too. The colours are taken when you choose the background and when you change the
  launcher colour scheme, not at any other time.
- **Own** uses the colours the background ships with, for example an aurora in its natural greens even
  when your palette is blue.

## Rest pose

Each background has a rest pose: its picture with the motion at zero. That is the picture the
launcher gives to the system, so your lock screen and other apps show exactly what the launcher
settles on.

## Moments

Short effects inside the background, set off by what you do. At most two play at once, and they stay
dim enough that text and icons on the glass keep their contrast. A moment shows through the glass
too, so a wave passing under the dock is seen in the dock's blur.

| Moment | When |
|---|---|
| Unlock | The rest pose blooms into motion when you unlock the phone onto the launcher. |
| Lock | A double tap on the A–Z index settles the motion into the rest pose and dims it, in under about 400 ms, then the screen locks. A second double tap locks at once. |
| Pane open or split | A soft wave from the new pane's seam. |
| Pane close | The wave folds inward. |
| Page change | The flow is nudged in the direction of the slide. |
| Touch | A ripple where you tap bare wallpaper on the Home place. Taps on the dock, tiles or keyboard do nothing. |
| Bell | A faint glow behind the pane that rang. |

## When it pauses

A paused background keeps showing its current frame, with no jump, and moments do not play while it
is paused. It pauses for:

- battery saver;
- a warm phone (thermal moderate or worse);
- Lazy mode;
- reduced motion (the lock then happens at once, with no animation);
- the launcher not being visible, or the screen being off.

It drops from 30 to 15 frames per second when the phone is only slightly warm or the battery is low
and discharging. It picks up on the next frame when you come back. Rotation keeps it running at the
new size.

## Which kitty shaders have a counterpart

kitty 0.49 ships animated backgrounds and window effects as shaders. They are Slang post-processes
over a desktop window, so none of them run here. Where an idea carried over, it was rewritten from
scratch; nothing is copied.

| kitty shader | Here | Why |
|---|---|---|
| `northern-lights` | Rewritten as **Aurora** | A single-pass generator; kitty's ray-march and `persist` pass are dropped |
| `inside-the-matrix` | Rewritten as **Rain** | Glyph-cell rain with procedural cells and no font; the idea only |
| `water` | Rewritten as **Tide**, plus the touch ripple | kitty's version only distorts the finished frame; here it is a generator |
| `fireworks` | Not carried over | Contains CC BY-NC-SA code, and its look fights the ink colour |
| `pond-ripple` | Rewritten as the touch ripple | |
| `tab-change` | Rewritten as the page change moment | |
| bell region (`bell_window_geometry`) | Rewritten as the bell moment | |
| `spotlight` | Not carried over | A phone has no hover |
| `dim-inactive-windows`, `focus-highlight` | Not carried over | The active pane already shows through focus growth and the idle ring |
| `crt`, `crt-blue`, `tft` | Not carried over | Full-window post-process, and `crt` contains CC BY-NC-SA code |
| `cursor-trail-blaze`, `lightning`, `motion-blur` | Not here | Planned with the glass experiments |

**`custom_shaders` in `kitty.conf` is not read.** A kitty.conf synced from your desktop never changes
the phone's wallpaper, and there is no way to load your own shaders.
