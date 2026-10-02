# Animated backgrounds

An animated background is a wallpaper the launcher draws itself, behind everything. It moves slowly,
and every glass surface (the status bar, the dock, the keyboard, the A–Z index, the pane slabs and
the corner tabs) shows it blurred and in register, just as it does a photo.

These are the launcher's own backgrounds, written for a portrait OLED screen: dark, low contrast and
slow, so the panel stays mostly off and the battery cost stays small. On the home screen they are
not a system live wallpaper, and they are never a video. The lock screen can play one as a live
wallpaper (see [Lock screen](#lock-screen)).

## Requirements

- The extra glass is active. It has no switch: it is on wherever the device supports it, so Lazy
  mode, battery saver and reduced motion must be off.
- Android 14 or later.

The **Animated** row in the wallpaper picker shows only when both hold. On Android 13 the extra
glass works but the row stays hidden. While Lazy mode, battery saver or reduced motion is on, the
background stops on its still; when it ends, the background carries on.

## Pick one

1. Open the wallpaper picker.
2. Choose a tile in the **Animated** row. The tiles are still pictures, so the picker itself costs no
   battery.

The built-ins are **Aurora** (slow ribbons), **Mesh** (a four-colour gradient that drifts), **Tide**
(soft waves) and **Rain** (sparse glyph-cell rain). Choosing a photo replaces a background in one
step. A wallpaper set from another app stops the animation on its own.

From the terminal:

```
launcherctl wallpaper list-builtins
launcherctl wallpaper set --builtin aurora
```

`launcherctl wallpaper` also reports whether a background is animated and playing, and why not when
it isn't: `api`, `fancier_glass_off`, `paused` or `killed`. See [LauncherCtl API](LauncherCtl_API.md).

## Lock screen

The wallpaper picker has two slots, **Home** and **Lock**. The Lock slot starts as **Same as Home**,
which follows every home change. You can also give it a background of its own or a photo.

- With **Motion** on (the default, Android 14 or later), an animated background plays on the lock
  screen. It is the same background, calmer and dimmed by about a fifth so the clock and
  notifications stay easy to read.
- The first time, Android shows its live wallpaper preview. Choose **Lock screen** there. An app
  cannot set a live wallpaper without asking you. Later lock choices change without asking again.
- When you unlock, the lock animation settles to its rest pose, the home screen fades in on the same
  picture, and the launcher's own animation picks up from there.
- It draws nothing while the screen is off or showing the always-on display. It drops to 15 frames
  per second when the phone is warm or the battery is low, and stops on a still under battery
  saver or reduced motion.
- With Motion off, the lock screen shows the background's rest-pose still instead.
- A photo picked for the Lock slot replaces the live wallpaper. Picking an animated background again
  brings back Android's one-time preview.

Below Android 14 the Motion toggle is hidden, and the Lock slot takes stills and photos only.

From the terminal, `launcherctl wallpaper` reports `lock_slot` (`same_as_home`, a background id or
`photo`), `lock_motion`, and `lock_live` (whether the launcher's live wallpaper holds the lock
screen). `launcherctl wallpaper set --builtin aurora` with the `lock` target puts that background's
still on the lock screen, and `both` makes the Lock slot Same as Home.

## Colours

A background always uses the colours it ships with, for example an aurora in its natural greens.
Colours flow from the wallpaper to the phone: Android derives its Material theme from the
wallpaper, never the other way round. The `--palette` option and the API's `palette` field are
still accepted and ignored.

## Rest pose

Each background has a rest pose: its picture with the motion at zero. That is the picture the
launcher gives to the system, so your home screen in recents and other apps show exactly what the
launcher settles on, and so does a lock screen with Motion off.

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
