# HANDOFF — feat/svg-icons (phase 4, D6)

Done. `LinuxAppIcons.find()` accepts SVG (absolute path, hicolor `scalable`, pixmaps); a real
raster PNG at any hicolor size still wins since `scalable` stays last in the loop. `load()`
renders SVG via AndroidSVG (`com.caverock:androidsvg-aar:1.4`, added to `app/build.gradle`) to a
bitmap capped at `MAX_EDGE_PX`, returns null on any parse/render failure, never throws.

Threading: both callers of `load()` already run off the main thread in steady state —
`X11WindowIconResolver.resolve()` on its own worker executor, and
`LauncherAppDataProvider.addLinuxApps()` (which primes the icon cache) inside `loadSnapshot()`,
always invoked via the background `executor` from `warmAsync`/`refreshAsync`. No new dispatch
added inside `LinuxAppIcons`; see report for the `getAllAppsBlocking()` caveat (pre-existing,
shared with PNG, out of scope).

Tests: `LinuxAppIconsTest.java` (new, 9 tests) + one assertion fixed in `LinuxAppCatalogTest`
(`iconFilesAreFoundInHicolorThenPixmaps`, its "svg is not rendered" case is now the opposite by
design). Full suite green: 5213 tests, 0 failures. `lintDebug` fails on 1968 pre-existing errors
unrelated to this change (confirmed: grep for `caverock`/`androidsvg`/`LinuxAppIcons` in the lint
report is empty).

Commit: see `git log` on this branch. Nothing device-side attempted (out of scope for phase 4).
