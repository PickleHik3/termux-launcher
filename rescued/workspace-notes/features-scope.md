# Scope: Home Screen Widgets + Dedicated App Drawer

Source: `features.md` (plain-language). Target branch: `dev`.
Codebase survey done 2026-08-10 against `app/termux-launcher@3c341c34`.

## Decisions (locked 2026-08-10)

1. **Order:** Epic B (app drawer) ships first; Epic A (widgets) follows.
2. **Categories:** `ApplicationInfo.category` where declared + bundled curated package→category map + `Other` catch-all. `Suggestions` from `LauncherUsageStatsStore`, `Recently Added` from `PackageInfo.firstInstallTime`. Computed locally, no network.
3. **Widgets v1 is full-fidelity:** `ACTION_APPWIDGET_BIND` consent fallback, widget `configure` activity launch, resize with `updateAppWidgetOptions`.
4. **Folders:** one shared model — reuse `PinnedFolderItem` + `LauncherConfigRepository`; raise the current 3×3 / 6-item cap. Folders are the same objects in dock and drawer.

---

## 0. What already exists (survey result)

Reuse, do not rebuild:

| Need | Existing | Location |
|---|---|---|
| App enumeration, work/clone profiles | `LauncherAppDataProvider` (singleton, async warm, letter buckets) | `app/launcher/data/LauncherAppDataProvider.java:38` |
| App model | `LauncherAppEntry` / `AppRef` (pre-normalized search fields) | `app/launcher/model/` |
| Search + ranking | `LauncherRankingEngine.filterAndRank()` — pure, stateless, tiered + Levenshtein | `app/launcher/data/LauncherRankingEngine.java:13` |
| Icon resolve + icon packs + compose | `LauncherIconResolver` | `app/launcher/data/LauncherIconResolver.java:24` |
| Launch (profile-aware, fallbacks) | `LauncherAppLauncher.launchEntry()` | `app/launcher/LauncherAppLauncher.java:21` |
| Usage ranking | `LauncherUsageStatsStore.rankForAz()` | `app/launcher/data/LauncherUsageStatsStore.java:27` |
| Pinned/folder persistence (JSON schema v4) | `LauncherConfigRepository` | `app/launcher/data/LauncherConfigRepository.java:20` |
| Folder model | `PinnedFolderItem` (id, title, rows, cols, tint, apps) | `app/launcher/model/PinnedFolderItem.java` |
| App long-press popup menu | `showAppContextPopup()` (+ `showFolderContextPopup()`) | `SuggestionBarView.java:3996`, `:4149` |
| Drag-and-drop of icons | `startDragAndDrop` + `handlePinnedBarDragEvent` | `SuggestionBarView.java:5998`, `:6018` |
| A-Z column + gesture FX | `AZ_ORDER`, `LauncherAzGestureFxView` | `SuggestionBarView.java:148`, `LauncherAzGestureFxView.java` |
| Glass/blur surface | `DockGlassRendering`, `RealtimeBlurView`, shared pre-blur LRU | `DockGlassRendering.java:17`, `TermuxActivity.java:655` |
| Accessory stack height math | `AccessoryStackLayoutPolicy.computeCombinedHeight()` | `terminal/AccessoryStackLayoutPolicy.java:7` |
| Status bar collapse/expand state machine | `setTopStatusBarCollapsed()` + `StatusBarResizeGeometry` | `TermuxActivity.java:10968`, `statusbar/StatusBarResizeGeometry.java:22` |
| Radius / inset / grain tokens | `TermuxPreferenceConstants` | `termux-shared/.../TermuxPreferenceConstants.java:184-217` |
| Settings sub-screen pattern | `<Preference app:fragment=...>` + `MaterialPreferenceFragment` | `res/xml/launcher_preferences.xml`, `fragments/settings/termux/` |
| Live restyle propagation | `requestTermuxActivityStylingOnNextResume()` (debounced 140ms DataStore) | `TermuxStylePreferencesFragment` |

Gaps that must be built from zero:

- **No widget hosting whatsoever.** No `AppWidgetHost`, no `AppWidgetHostView`, no `BIND_APPWIDGET` permission, no picker intent, no manifest entries. Manifest declares HOME/LAUNCHER/LEANBACK only.
- **No app category data.** `ApplicationInfo.category` is not read anywhere. Only intent `CATEGORY_LAUNCHER` filtering.
- **No RecyclerView direct dependency** — present only transitively via `io.noties.markwon:recycler:4.6.2`. Must add explicit `androidx.recyclerview`.
- **No `androidx.dynamicanimation`** — spring/rope animations currently hand-rolled on `ValueAnimator`. Rope-sway A-Z and drawer spring overscroll need either the dep or hand-rolled physics (`DockPlankController` is the existing hand-rolled precedent).
- **No `OnBackPressedDispatcher`** — back is a single `TermuxActivity.onBackPressed()` override at `:9651` with hand-ordered consumers. Two new full-screen surfaces must be inserted into that ordering explicitly.

---

## Epic A — Home Screen Widgets

Entry: long-press on the in-app status bar (compact **or** expanded) expands it full-screen down to the bottom edge, respecting in-app padding.

### A1. Widget host foundation (blocking, no UI)
- `AppWidgetHost` + `AppWidgetHostView` subclass, host id constant, `startListening`/`stopListening` tied to `TermuxActivity` start/stop.
- Manifest: `android.permission.BIND_APPWIDGET` (system-signature-gated — must fall back to `ACTION_APPWIDGET_BIND` user-consent flow), `APPWIDGET_HOST` receiver + `appwidget-host` meta if needed.
- Widget id allocation/dealloc, `bindAppWidgetIdIfAllowed()` → consent dialog fallback, launching a widget's `configure` activity, `onActivityResult` plumbing.
- Widget option updates (`updateAppWidgetOptions` with min/max width/height) on resize.
- Crash isolation: a bad widget must not take the launcher down (host view wrapper with try/catch + error tile).

### A2. Full-screen status-bar expansion (third state)
Current status bar is a two-state machine: collapsed ~30/32dp ↔ expanded ~100/96dp, animated 260ms with `PathInterpolator(.16f,1f,.3f,1f)` (`TermuxActivity.java:10968`, geometry solver `StatusBarResizeGeometry.java:22`).
- Add a third **FULL** state: height → screen bottom minus in-app padding.
- Long-press gesture on `StatusBarSwipeLayout` (currently horizontal swipe only, `:46`/`:66`) — must not eat window-bar taps or the existing swipe collapse/expand.
- Glass carries over: reuse `terminal_window_bar_blur` / wallpaper backdrop, growing the pane rather than layering a new surface, so the transition is seamless.
- **Pre-blur cache risk:** shared pre-blur LRU is keyed per radius (`TermuxActivity.java:655`); a new full-screen surface at a new radius can thrash the cache (known 1–3s jank class). Reuse the existing radius or size the LRU up.
- Clock retargets to top-centre with animated rightward move; when media or pinned notifications are present, top row is a shared aligned layout instead. This is a re-layout of `TopPaneWidgetSlot` (`:28`), which today owns slot contention via `TopPaneSlotMode` (`:10`).
- Back press closes FULL, animating back to the **prior** state (expanded→expanded, compact→compact). Insert ahead of existing consumers in `onBackPressed` ordering.

### A3. Widget grid + editing
- Material `(+)` centre affordance → widget picker sheet (grouped by app, previews, spans).
- Cog top-right → widget settings: rows × columns grid definition.
- Placement model: cell occupancy grid, collision handling, auto-place on add.
- Long-press → edit mode (standard launcher heuristic): drag to move, resize handles, remove target.
- Persistence: grid dims + per-widget {appWidgetId, provider, cell rect} — schema + migration.

### A4. Polish
- Enter/exit animation continuity with the compact/expanded forms.
- Rotation / display-size change re-layout, widget id survival across recreate.
- Empty state, widget-provider-removed state, restore-after-reinstall behaviour.

---

## Epic B — Dedicated App Drawer

Entry: swipe straight **down** starting on the dock's app-icons row.

### B1. Open/close choreography
- Gesture: vertical-down intent from the app-icons row, distinguished from the existing horizontal page swipe and the notification swipe-up on icons (`SuggestionBarView.java:1515`/`:1550`/`:3834`) and from the pickup-drag long-press window (`:3860`, 200ms).
- Dock lifts slightly, then expands up and down to fill the screen, retaining dock styling + padding prefs.
- **In-app keyboard + extra keys**: in rounded dock style they minimise smoothly keeping current dock↔keyboard padding; in default dock style extra keys + keyboard scroll down as one entity and disappear. Both paths run through `AccessoryStackLayoutPolicy.computeCombinedHeight()` — new bands must join `combinedHeight` or they clip invisible.
- Pinned dock icons fade/animate out on open, back in on close.
- A-Z column sways like a loose rope, settling on the right edge. No physics dep today — either add `androidx.dynamicanimation` or hand-roll like `DockPlankController`.

### B2. Chrome
- Search pill matching the drawer plane's corner radius — tie to the shared radius token (`DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP`, `KEY_APP_LAUNCHER_DOCK_CORNER_RADIUS`), not a new literal.
- Small settings cog bottom-left → drawer settings.
- Search backed by `LauncherRankingEngine` (already stateless and reusable).

### B3. View type — Vertical grid
- Vertical scroll grid over all apps.
- Overscroll at top = spring animation; a **second** deliberate down-swipe closes the drawer (reverse of the open animation).

### B4. View type — Horizontal grid
- Paginated left/right with dot page indicator at bottom.
- A deliberate down-swipe closes.

### B5. View type — Categories
- Vertical scroll over category tiles. Tile = rounded square at drawer radius, showing 7 icons in 2 rows: row 1 = 2 large icons; row 2 = 1 large icon + a 2×2 of 4 small icons occupying one large slot.
- Category heading centred **below/outside** the tile.
- Tapping the 2×2 sub-grid or the header row expands the tile: full app list bottom-aligned, large category header above it with empty space over that, list grows upward, scrollable if it overflows.
- **Category source is an open question** (see Questions) — `ApplicationInfo.category` (API 26+) covers only a fraction of installed apps and has no "Suggestions"/"Recently Added" notion. Named buckets in the brief: Suggestions, Recently Added, Social, Productivity, Utilities, Entertainment, Shopping & Food, Finance, Health, Photo & Video, Travel, Information & Reading, Other.

### B6. A-Z scrub
- Finger up/down the right A-Z column highlights matching app icons, de-emphasises (fades) the rest. Extends `LauncherAzGestureFxView` behaviour.

### B7. Icons, long-press, folders
- Long-press on a drawer icon shows the same popup as the dock's (`showAppContextPopup()` at `:3996`) — reuse, do not clone.
- Long-press + drag one icon onto another creates a folder with its own popup and a tap-to-rename title. Folder model exists (`PinnedFolderItem`), currently capped 3×3/6 items — cap likely needs raising for drawer folders.

### B8. Drawer settings
- Icon size, grid size (separate values for vertical and horizontal views), view type, plus whatever the cog needs.
- Registered via the existing preference-fragment pattern; live-apply via `requestTermuxActivityStylingOnNextResume`.

---

## Cross-cutting risks

1. **Gesture collision on the dock.** The app-icons row already handles: horizontal page swipe (velocity/slop), notification swipe-up, long-press pickup drag (200ms window), tap-to-launch. Adding swipe-down needs one arbitration point, not a fourth independent listener in `dispatchTouchEvent`.
2. **Back-press ordering.** Single `onBackPressed()` override; both new surfaces are back consumers and must be ordered against palette/dock/folder popups deliberately.
3. **Blur cache thrash.** Two new large glass surfaces at new radii will evict the dock/keyboard pre-blur entries. Budget the LRU or reuse radii.
4. **`combinedHeight` invariant.** Any new band that does not join the accessory-stack height calc renders clipped/invisible.
5. **Memory.** Widget host views + a full-screen icon grid + existing 96-slot icon LRU on a device already RAM-tight. Drawer icon cache should be sized, not unbounded.
6. **Test baseline.** App module currently at 895 unit tests, both variants green. New logic (grid placement, category mapping, gesture arbitration, ranking reuse) should land with unit tests; run both variants and trust the XML, not the exit code.
7. **Edition merge.** Everything lands on `dev` and reaches `main` / `nix-edition` by merge only — no edition-specific code in these features.

---

## Suggested delivery slices

Each slice = buildable, device-verifiable, own commit(s).

**Epic B (drawer)** — more reuse, lower risk, ship first:
- B-1 Gesture arbitration + open/close choreography with a placeholder empty plane (proves keyboard/extra-keys/dock/pinned-icon animation, back handling, glass).
- B-2 Vertical grid + search pill + launch + long-press popup reuse.
- B-3 A-Z rope + scrub highlight.
- B-4 Horizontal paginated view + dots.
- B-5 Category view + tile expansion.
- B-6 Drag-to-folder + folder popup/rename.
- B-7 Drawer settings screen + live apply.

**Epic A (widgets)** — larger unknowns, all-new subsystem:
- A-1 Host foundation + bind-consent flow + a hardcoded test widget (no UI).
- A-2 Full-screen status-bar state + clock/media/pinned re-layout + back.
- A-3 Grid model + picker + add/remove.
- A-4 Drag-move + resize edit mode.
- A-5 Rows×cols settings + persistence + polish.
