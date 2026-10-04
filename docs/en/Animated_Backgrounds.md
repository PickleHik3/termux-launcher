# Living stills

A living still is your own photo, brought to life. The launcher reads the photo once, on the phone,
and finds its depth, its water, sky, plants, falling water and lights. It then draws the photo
itself, behind everything: it drifts a little with depth, plants sway, water moves, lights breathe.
Every glass surface (the status bar, the dock, the keyboard, the A–Z index, the pane slabs and the
corner tabs) shows it blurred and in register, just as it does a plain photo.

A living still is slow and low contrast on purpose, so an OLED panel stays mostly off and the
battery cost stays small. On the home screen it is not a system live wallpaper, and it is never a
video. The photo itself is the wallpaper Android holds: the still. The lock screen can play the
motion as a live wallpaper (see [Lock screen](#lock-screen)).

The launcher used to ship ten pre-made animated backgrounds (Mesh, Aurora, Tide, Rain, Contour,
Drift, Lava, Silk, Caustics and Chrome). They are gone. If one was your wallpaper, its picture
stays as a plain photo; a lock screen that used one follows Home, or keeps its picture as a photo.

## Requirements

- The extra glass is active. It has no switch: it is on wherever the device supports it, so Lazy
  mode, battery saver and reduced motion must be off.
- Android 14 or later.

Living stills also need the depth and scene models, which you install from the model centre; the
launcher asks you to open it when one is missing. The wallpaper picker page offers the motion only
when both requirements hold. Otherwise the page still opens, with your photos only: the recent
photos, **Photo…** and what each screen shows now. On Android 13 the extra glass works but the
motion is not offered. While Lazy mode, battery saver or reduced motion is on, the still stops on
its photo; when it ends, the motion carries on.

## Make one

Hold a page's corner and tap **Wallpaper**. The wallpaper picker page opens full screen.

- **Two previews, Lock screen then Home screen.** Swipe between them; the title names the one in
  the middle. The middle preview plays its still live, and the other shows its photo. The
  Lock preview carries a generic lock screen with the current time. The launcher's own background
  pauses while the page is open.
- **Bring to life.** With a photo chosen, the **bring to life** button starts the analysis. While
  it runs the button is a progress bar; once the photo is done it becomes the **Motion** switch.
  The result is kept for that photo.
- **Thumbnails.** Tap one to preview it in the middle slot, then tap **Apply** to set it. Apply is
  only available when the choice differs from what the slot holds. Each slot keeps its choice
  while you swipe, and back closes the page without applying anything.
- **Same as Home** comes first in the strip while the Lock preview is in the middle. It is the
  default: the lock screen follows every Home change.
- **Photo…** opens your photos, then the crop. When the crop is done the page comes back on the
  same slot, with the photo previewed in that card. Nothing changes until you tap **Apply**, which
  puts it on both screens; the menu next to Apply has **Home screen only** and **Lock screen
  only**. Back without Apply leaves your wallpaper as it was.
- **Recent** photos lead the strip (after Same as Home on the Lock card): the last three photos you
  applied, newest first. Tap one to preview it and Apply to set it again, with no new crop.
- A slot that holds a photo shows that photo in its card, as the screen shows it.
- **Motion** sits on both cards (Android 14 and later), on by default. On the Home card it plays the
  still in the launcher; off, the home screen shows the plain photo. On the Lock card it animates
  the lock screen through the launcher's live wallpaper; the first time, Android asks you to
  confirm it in its own live-wallpaper preview, where you pick "Lock screen". Off, the lock screen
  shows the photo.
- **Look**, **Icon pack** and **Layout** close the page and open the appearance editor, the icon
  pack setting and the layout editor.

The thumbnails are still pictures, so they cost no battery. Choosing a plain photo replaces a living
still in one step. A wallpaper set from another app stops the animation on its own.

From the terminal, `launcherctl wallpaper set FILE` sets a photo (see
[LauncherCtl API](LauncherCtl_API.md)). `launcherctl wallpaper` also reports whether a living still
is playing, and why not when it isn't: `api`, `fancier_glass_off`, `paused` or `killed`.

## Lock screen

The wallpaper picker has two slots, **Home** and **Lock**. The Lock slot starts as **Same as Home**,
which follows every home change. You can also give it a living still of its own or a photo.

- With **Motion** on (the default, Android 14 or later), a living still plays on the lock screen.
  It is the same still, calmer and dimmed by about a fifth so the clock and notifications stay
  easy to read.
- The first time, Android shows its live wallpaper preview. Choose **Lock screen** there. An app
  cannot set a live wallpaper without asking you. Later lock choices change without asking again.
- When you unlock, the lock animation settles to its rest pose, the home screen fades in on the same
  picture, and the launcher's own animation picks up from there.
- It draws nothing while the screen is off or showing the always-on display. It drops to 15 frames
  per second when the phone is warm or the battery is low, and stops on a still under battery
  saver or reduced motion.
- With Motion off, the lock screen shows the photo instead.
- A plain photo picked for the Lock slot replaces the live wallpaper. Picking a living still again
  brings back Android's one-time preview.

Below Android 14 the Motion toggle is hidden, and the Lock slot takes photos only.

From the terminal, `launcherctl wallpaper` reports `lock_slot` (`same_as_home`, a living still id or
`photo`), `lock_motion`, and `lock_live` (whether the launcher's live wallpaper holds the lock
screen). `launcherctl wallpaper set FILE --lock` puts a photo on the lock screen, and `--both`
makes the Lock slot Same as Home.

## Colours

A living still is drawn with colours taken from its own photo. Colours flow from the wallpaper to
the phone: Android derives its Material theme from the wallpaper, never the other way round.

## Rest pose

Each living still has a rest pose: the photo with the motion at zero. That is the picture the
launcher gives to the system, so your home screen in recents and other apps show exactly what the
launcher settles on, and so does a lock screen with Motion off.

## Moments

Short effects inside the still, set off by what you do. At most two play at once, and they stay
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

A paused still keeps showing its current frame, with no jump, and moments do not play while it
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
| `northern-lights`, `inside-the-matrix` | Not carried over | The launcher no longer ships generated backgrounds; the wallpaper is your photo |
| `water` | Carried over as the water motion of a living still, plus the touch ripple | Here the water is found in your photo by the scene masks |
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
