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
