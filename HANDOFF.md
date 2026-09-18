# P1 — wallpaper picture (fix/wallpaper-picture)

Done: WallpaperPictureReader (single getWallpaperInfo reader); activity caches the picture in
mWallpaperPicture with refreshWallpaperPicture(); bars + pane frame gated on picture.blurs();
WallpaperBackdropPolicy.mode takes the picture; settings fragment uses the reader; tests updated.
In progress: build + full unit suite.
Next: verify `grep -rn "getWallpaperInfo()" app/src/main/java` lists only the reader; commit.
Gotchas: PaneGlassBackdropView draws tint+grain for a null frame (verified, lines 191-200), so the
plain slab needs no new code. P2 owns SurfaceEditorHost (8667-8811) — not touched.
Remove this file in the final commit.
