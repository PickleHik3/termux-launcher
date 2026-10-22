---
status: accepted
date: 2026-10-21
amends: 0005 (Fancier Glass's "wallpaper set from inside the launcher" now includes a picture Android never got)
---

# The launcher can wear a wallpaper of its own

Until now every wallpaper the glass could treat as the launcher's own went through Android: the
picker set it with `WallpaperManager`, stored the system id, and the glass counted the exact copy
as on screen only while that id was still the system's. People who use Termux Launcher as a
terminal and keep another home app did not want a terminal theme to repaint their phone, but
without that set the launcher fell back to whatever Android showed: no self-drawn backdrop, and
no Fancier Glass.

We decided that, while the launcher is not the home app, the picker sets a picture for the
launcher alone unless "Also set as phone wallpaper" is ticked. The picture becomes the same
managed exact copy as before, marked app-only (`wallpaper_app_only`), and the stored system id is
cleared because the copy is no longer the phone's picture. While the mark holds and the copy is
on disk, `WallpaperSourceFacts.managedOnScreen` is true without comparing ids, the picture
resolves to `MATCHES_SCREEN` whatever Android runs, and the backdrop is self-drawn even when a
read of Android's wallpaper was refused. Any Home set to the phone (the picker ticked, `POST
/v1/wallpaper`, a restore) ends the mark. A mark whose copy is gone counts for nothing, and
Android's wallpaper shows again.

**The window stops asking for the phone's wallpaper.** In every other self-drawn case the
picture behind the window is the same one the launcher draws, so the system carrying the frame or
two before a capture lands (a cold start, a rotation) is invisible. Under an app-only picture it
is a different picture and would flash, so the activity clears `FLAG_SHOW_WALLPAPER` while it
draws one, and adds it back when it stops (`WallpaperBackdropPolicy.showsSystemWallpaper`). The
gap shows the window's ground instead.

The alternative, a second file for the app-only picture, would have given every reader of the
exact copy (the blur cache, parallax, the Look editor, the settings page's Fancier Glass check) a
second path to choose between. One file with one mark keeps them all reading the same picture.

Consequences: the launcher stays on the frame ADR 0002 and 0004 describe; nothing new is blurred
or decoded per frame, and the mark is one in-memory preference read where the stored id already
was.

**The colours follow the app-only picture** (amended 2026-10-21; this ADR first left them on
Android's wallpaper). While an app-only picture is on screen, every Material colour the launcher
derives from "the wallpaper" comes from that picture: the activity and Settings themes, the
terminal's Wallpaper colours, the exported palettes and the in-app keyboard. The picker's apply
takes the picture's seed on its worker, from a small downsampled decode through Material's own
content-based extraction (`DynamicColorsOptions.setContentBasedSource`), and stores it with the
exact copy's stamp (`wallpaper_app_only_seed`, `wallpaper_app_only_seed_stamp`) before it writes
the mark. Activity creation decodes nothing: it reads the seed, builds Material's content scheme
from it, and loads it over the same `material_personalized_color_*` resources the terminal-scheme
chrome uses (`LauncherSchemeTheme`), so every reader of a Material role follows without knowing.
`WallpaperColorSource` holds the rule: the terminal scheme, when it owns the chrome, wins; below
Android 12 nothing follows a wallpaper; a seed whose stamp is not the copy's is taken again on a
worker (a picture set before seeds were kept, a restore) while Android's colours stand in. Android
sends no configuration change for a picture it never got, so the activity checks its colours
wherever it reads the wallpaper facts again (and Settings on resume) and recreates when they moved:
a new app-only picture, the mark ended, the copy gone. The check compares what was decided, not
what loaded, so a failed load cannot recreate in a loop. The contexts built outside an activity
(the exported dark and light palettes, the background day/night refresh) are wrapped in the same
colours.
