# Lock-screen live wallpaper and the two-slot picker (2026-10-03)

The developer settled this on 2026-10-03, in chat. It amends SPEC.md §1, which had a system
WallpaperService out of scope. The glass still never blurs a system wallpaper, because the
home screen keeps the launcher's self-drawn animation.

## The flow

When a preshipped (animated) background is applied:

- **Lock screen:** our live wallpaper (`LockLiveWallpaperService`) animates it.
- **Unlock → launcher:** the lock animation settles towards its rest pose, Android cross-fades to
  the home wallpaper, and the launcher's in-app animation eases up from rest (the existing
  900 ms unlock ease).
- **Inside the launcher:** the self-drawn live animation and the glass, as today.
- **Home system wallpaper:** the static rest-pose still, as today. Recents, app transitions and
  Material colours read it, so colours still flow from wallpaper to theme, never the reverse.

## Decisions

1. **Unlock seam (Q3).**
   - The lock animation settles to its rest pose when the unlock starts, so the cross-fade lands
     on the matching still.
   - Use whatever public signal is earliest: `WallpaperService.Engine#onCommand` wake/sleep
     commands if public at API 34+, `onVisibilityChanged`, and the screen-on/keyguard state you
     can read without privileged permissions.
   - If no early signal exists, the lock look is calm enough (item 3) that the seam stays small.
   - Record which signal was used.
2. **Your own photo (Q4).** A photo picked for the lock slot is set with `FLAG_LOCK`. That
   silently replaces our live wallpaper. Picking an animated background for the lock slot again
   brings back the one-time system confirmation.
3. **Lock look (Q5).**
   - The same background as chosen, calmer (lower energy) and dimmed about 20% (`uDim`) so the
     clock and notifications stay legible.
   - Frame rate as home: 30 fps, or 15 under light thermal pressure or a low battery.
   - Draw nothing while not visible: the screen off, AOD, or hidden behind an app.
4. **Opt-in (Q6, Q10).** The Lock slot has a **Motion** toggle, on by default.
   - Off puts the background's rest-pose still on the lock screen (`FLAG_LOCK`) instead of the
     live wallpaper.
   - Turning it on, or the first animated lock choice, opens Android's live-wallpaper preview
     (`WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER` with `EXTRA_LIVE_WALLPAPER_COMPONENT`),
     where the user picks "Lock screen". An app cannot set a live wallpaper silently.
   - Once our service holds the lock screen, later lock choices only change what it draws (a
     preference it observes), with no further prompt.
5. **API (Q7).** API 34 and up, the same gate as the in-app animation. Below that the Motion
   toggle is hidden and the lock slot takes stills and photos only.
6. **Two slots (Q8, Q9).**
   - The picker has a Home slot and a Lock slot.
   - The Lock slot's first choice is **Same as Home**, which is the default: it follows every
     home change, animated if home is animated and Motion is on.
   - The unlock seam (item 1) applies only when lock and home show the same background.
     Otherwise Android's normal cross-fade runs.

## The picker page (Q11–Q15)

The model is Nothing's own wallpaper and style page
(`project-docs/reference/lock-wallpaper/reference-nothing-picker.png`). It is a full-screen page
that replaces the `WallpaperPickerSheet` bottom sheet and its home/lock/both dialog.

- **Two large previews, Lock then Home,** swipeable.
  - The centred preview is the selected slot, and the title reads "Lock screen" or "Home screen".
  - The centred preview plays its background live (a RuntimeShader on the hardware canvas,
    through `WallpaperUniforms`); the other shows its still.
  - While the page is open, the launcher's own backdrop renderer is hidden behind it, so pause it.
- **Lock preview:**
  - Carries the generic lock-screen cutout drawn by Codex: `lock_preview_overlay`, with the
    current time composed from `lock_digit_*` glyphs. Its placeholder colours map to theme
    attrs.
  - The Motion toggle sits under it.
- **Home preview:** the background only, with no miniature launcher.
- **Shortcut row:** **Look** (the appearance editor), **Icon pack** (the existing
  `app_launcher_pinned_icon_pack_package` setting) and **Layout** (the layout editor).
- **Thumbnail strip:**
  - The 10 preshipped backgrounds scroll sideways.
  - A **Photo…** button replaces "More wallpapers" and opens the existing photo flow for the
    selected slot.
  - When the Lock preview is centred, a **Same as Home** tile comes first.
- **Apply:**
  - Tapping a thumbnail previews it in the centred slot. An **Apply** button commits it.
  - For the lock screen's first animated choice, Apply is where Android's one-time preview
    appears.
- **Rules:** M3 theme attrs only, glyphs 24dp in touch targets of at least 48dp, no hint text.

## Module seams

- `com.termux.app.chrome.wallpaper.WallpaperSlots` is the one API between the picker and the
  slots. It covers reading the state, applying a choice to a slot, the Motion toggle and
  whether lock live is supported.
- The picker never touches `WallpaperManager` or the slot preferences directly.

## Implementation notes (2026-10-03)

Built in `com.termux.app.chrome.wallpaper`: `LockLiveWallpaperService` (the engine),
`LockWallpaperDirector` (its pure look and pacing), `WallpaperSlotPlan` (the pure decision behind
every `WallpaperSlots` call) and the `WallpaperSlots` bodies. Not device-checked yet.

### Storage

| Slot | Preference | Values |
|---|---|---|
| Home | `managed_wallpaper_animated` (unchanged) | a background id, or unset for a photo |
| Lock | `wallpaper_lock_choice` | `same_as_home` (default), `animated:<id>`, `photo`; anything else reads as `same_as_home` |
| Lock | `wallpaper_lock_motion` | boolean, default `true` |

`WallpaperSlots.read` takes `lockLiveActive` from `WallpaperManager.getWallpaperInfo(FLAG_LOCK)`
(API 34) matching our component; when the lock screen has no wallpaper of its own, from the home
one.

### The unlock signal (decision 1)

The engine settles on the **first** of these:

1. `Engine#onCommand` with `android.wallpaper.keyguardgoingaway`
   (`WallpaperManager.COMMAND_KEYGUARD_GOING_AWAY`). The constant is not in the public SDK, but
   the callback is public and Android 14+ sends it to wallpaper engines as the keyguard starts to
   go away. It is the earliest signal, before the cross-fade.
2. `KeyguardManager#isKeyguardLocked()` reading false, polled once per drawn frame. Public and
   needs no permission. It covers ROMs that never send the command. The keyguard-state listener
   (`addKeyguardLockedStateListener`) needs a privileged permission, so it is not used.
3. `Intent.ACTION_USER_PRESENT`, which is late (after the keyguard is gone) and is a backstop.

`onVisibilityChanged(false)` ends the session. If the engine becomes visible while the keyguard is
already unlocked, it draws the rest pose at once. `android.wallpaper.goingtosleep` stops frames and
`android.wallpaper.wakingup` redraws. Which signal fired is logged at debug level ("Unlock seen:
keyguard_going_away | keyguard_unlocked | user_present").

The settle eases energy and dim to 0 over `UNLOCK_SETTLE_MS`, then snaps the phase to 0. That frame
is the rest pose, the same picture as the home still, so the cross-fade lands on a match. The
engine then stops drawing until it is visible again. When the next lock screen shows, the picture
blooms from rest.

### Lock look (decision 3): `LockWallpaperDirector`

| Constant | Value | Meaning |
|---|---|---|
| `LOCK_ENERGY` | 0.45 | how far from rest (the launcher plays at 1); phase integrates it, so motion is slower too |
| `LOCK_DIM` | 0.2 | `uDim`; on from the first frame |
| `WAKE_EASE_MS` | 600 | rest → lock look when the lock screen shows |
| `UNLOCK_SETTLE_MS` | 250 | lock look → rest when the unlock starts |

The rates reuse `WallpaperDirector.MAX_FPS` (30) and `PRESSURE_FPS` (15): 15 under light thermal
pressure or a low battery while discharging. Battery saver, thermal moderate or worse, reduced motion
(animator scale 0) or the kill switch draw one frame and stop. Moments stay at rest. The engine
draws nothing while hidden or while the screen is not interactive (AOD). Preview engines register
no receivers.

Drawing: `SurfaceHolder.lockHardwareCanvas` with a `RuntimeShader` through `WallpaperUniforms`,
paced by `Choreographer`. `uResolution` is the shared-frame width (`WallParallax.pickerWidthPx`)
and the canvas shows its centre, the same crop the system takes from the home still.
`onComputeColors` returns the background's own palette (colours 0–2).

### Stills, photos and Motion off

The simplest correct route is chosen: when the lock screen should show a still (Motion off, a lock
photo, Same as Home over a home photo), the still or photo is set with `FLAG_LOCK`. That replaces
our live wallpaper, so the engine never has to draw a photo. If the engine still holds the lock
screen with Motion off or the kill switch on (a failed set), it draws the rest pose once. If it has
nothing to draw, it draws one plain frame.

### What each call does (`WallpaperSlotPlan`)

| Call | Does |
|---|---|
| `apply(HOME, animated)` | Still to `FLAG_SYSTEM`; adds `FLAG_LOCK` when Lock is Same as Home and the lock screen is not our live wallpaper with Motion on. Records the Home slot; the in-app animation follows as before. |
| `apply(HOME, photo)` | Records nothing; the host's photo flow runs, then `notePhotoApplied(HOME)`. |
| `apply(LOCK, animated or Same as Home)`, Motion on | Records the choice first. Opens the preview (`ACTION_CHANGE_LIVE_WALLPAPER`, falling back to `ACTION_LIVE_WALLPAPER_CHOOSER`) unless our service holds the lock screen already. The callback gets `ok` with error `null`, and the log has a `preview_shown` line. |
| `apply(LOCK, …)`, Motion off | Still to `FLAG_LOCK`, then records the choice. |
| `apply(LOCK, Same as Home)` with a home photo | Copies the launcher's exact home picture to `FLAG_LOCK` (`ManagedWallpaper.copyHomePictureToLock`). |
| `apply(LOCK, photo)` | Records `photo`; the host's photo flow sets it with `FLAG_LOCK` (decision 2). |
| `notePhotoApplied(HOME)` | `GeneratedWallpaperApplier.clear`; when Lock is Same as Home on our live wallpaper, copies the photo to the lock screen. |
| `notePhotoApplied(LOCK)` | Records `photo`. |
| `setLockMotion(on)` | Stores it. On: opens the preview if the lock screen is not ours. Off: still to `FLAG_LOCK`. A lock photo changes nothing. |

Below API 34 Motion never plays, and animated choices answer `api`.

### `launcherctl` targets and the slots

`POST /v1/wallpaper` keeps `home`, `lock` and `both`:

| Request | Home slot | Lock slot |
|---|---|---|
| `builtin` + `home` | the id | unchanged |
| `builtin` + `lock` | **unchanged** (before this, a lock-only still also stored the id as Home's) | `animated:<id>` |
| `builtin` + `both` | the id | `same_as_home` |
| `path` + `home` | photo | unchanged |
| `path` + `lock` | unchanged | `photo` |
| `path` + `both` | photo | `same_as_home` |

A still set on the lock screen replaces our live wallpaper. The Motion toggle keeps its value, and
the next animated lock choice offers the preview again. `ManagedWallpaper.apply` now exports the
background copy only for sets that include the home screen.

`GET /v1/wallpaper` adds `lock_slot` (`same_as_home`, a background id, or `photo`),
`lock_motion` and `lock_live` (our service holds the lock screen).

### Device checks owed (pong, Android 16)

- The preview flow: the first animated lock choice opens Android's preview, and "Lock screen" there
  sets it. `lock_live` turns true.
- The lock screen animates calmly and dimmed. It drops to 15 fps when warm or low on battery, and
  draws nothing in AOD.
- The unlock seam: which signal fires first (debug log), and whether the cross-fade lands on the
  home still.
- Motion off puts the still on the lock screen, and `lock_live` turns false.
- A photo on the lock screen replaces the live wallpaper.
- If the user picks "Home and lock screens" in the preview, the home screen gets our engine, which
  draws the rest pose. The glass loses its blur there. This is a known risk, and the preview's
  choice cannot be preset.
