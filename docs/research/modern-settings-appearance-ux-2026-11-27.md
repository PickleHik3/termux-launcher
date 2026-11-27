# Modern settings and appearance UX: evidence and recommendations for Terl

Date: 2026-11-27. Method: first-party pages fetched on this date. Evidence is tagged:

- **[V]** verified on the cited primary page.
- **[K]** from prior knowledge of the product or guideline. The page could not be fetched or returned no body text. Treat as unverified until re-read.
- **[3P]** only third-party coverage was found (labelled as such).

Coverage gaps, stated plainly: the Apple HIG pages (Settings, Accessibility), the Material 3 pages (the `m3.material.io` pages return a title only) and the GNOME HIG preferences URLs did not return usable text. Raycast, Warp, Linear, KDE and Hyprland docs 404ed at the URLs tried. Those products are covered with [K] items or left out of the evidence. The Recommendations do not depend on any single [K] claim.

## 1. Platform guidance on settings

| Source | Finding | Tag |
|---|---|---|
| Android settings pattern https://developer.android.com/design/ui/mobile/guides/patterns/settings | Settings hold "infrequently accessed preferences". "Don't include frequently accessed actions. These should be contextual to the feature they most affect." Place frequent contextual preferences "close to the feature it affects". | V |
| same | Defaults should "represent the default most users would choose", be neutral and low risk, use less battery, "only interrupt when important". | V |
| same | Overview page of the most important settings with status as secondary text. 15 or more settings go under subscreens. "For complex or deep settings hierarchies, add search". Group labels must match subscreen titles. | V |
| same | "Show the setting status instead of describing the setting". Do not use generic label verbs (Set, Change, Edit, Manage). Avoid "Don't" and "Never". | V |
| same | Dependent settings sit under their parent with a short explanation of why they are unavailable. Slider for "a setting with a range of values... can be less precise". Radio buttons for discrete choices. | V |
| same | "Don't override settings provided by the system, as they can be personal accessibility needs." Extend theming rather than replace it. Offer a Quick Settings tile for frequent access. | V |
| same | Adaptive: list-detail layout, each screen a containment pane. Do not stretch single-pane items to full width. | V |
| Android accessibility https://developer.android.com/design/ui/mobile/guides/foundations/accessibility | Touch targets at least 48 dp even if the visual is smaller. Body text at least 12 sp, in `sp`. Contrast 4.5:1 text, 3:1 non-text. "Don't rely on gestures to complete all actions; create accessibility actions." | V |
| Material 3 Expressive launch https://blog.google/products/android/material-3-expressive-android-wearos-launch/ | Updated dynamic color themes, Quick Settings that users can fill with favourite actions, subtle blur on the notification shade, springy haptic motion. The post does not discuss research. | V |
| Windows app settings https://learn.microsoft.com/en-us/windows/apps/design/app-settings/guidelines-for-app-settings | "Define smart defaults and keep the number of settings to a minimum." "When a user changes a setting, the app should immediately reflect the change — don't require a confirmation button." Disabled controls get a description saying why. Expanders go one level deep. One-word section headers. Not commands from the common workflow. | V |
| Apple HIG Settings https://developer.apple.com/design/human-interface-guidelines/settings | Sensible defaults, minimise settings, keep frequently changed ones in context. | K |
| GNOME HIG https://developer.gnome.org/hig/ | "Avoid preferences": prefer fixing the default. Preferences apply immediately, no OK button. The URLs I tried returned 404, so unverified. | K |

Takeaway: all four platforms agree on the same five rules. Fewer settings. Smart defaults. Frequent controls live next to the thing they affect, not in Settings. Changes apply immediately. Explain why a control is disabled.

## 2. Home-screen and appearance customization

| Source | Finding | Tag |
|---|---|---|
| iOS 18 Home Screen (3P: TechCrunch https://techcrunch.com/2024/09/18/three-new-ways-to-personalize-your-iphones-home-screen-in-ios-18/, MacRumors https://www.macrumors.com/2024/06/10/ios-18-home-screen/) | Edit mode (long-press empty area, Edit, Customize) opens a panel over the live Home Screen with Default, Dark, Auto and Tinted, plus a Small/Large size switch. Tinted has two sliders. | 3P |
| iOS 26 (3P: MacRumors https://www.macrumors.com/guide/ios-26-home-screen/, 9to5Mac https://9to5mac.com/2025/08/27/how-to-make-iphone-app-icons-clear-with-ios-26/) | Adds a Clear icon style with Light, Dark and Auto. Widgets follow it. A sun icon in the Customize panel dims the wallpaper when labels are unreadable. A shortcut jumps to wallpaper editing. | 3P |
| Apple support Home Screen pages https://support.apple.com/guide/iphone/customize-the-iphone-home-screen-iph5d8ed0e5f/ios | Fetched page was a table of contents only. Edit-mode details above are not first-party verified. | V (limited) |
| Pixel / Material You, M3 Expressive https://blog.google/products/android/material-3-expressive-android-wearos-launch/ | A palette choice drives the whole system (the blog states this for Wear OS watch faces). Tonal accessible palettes, see the accessibility page above. | V |
| Lawnchair https://docs.lawnchair.app/ | Built on Launcher3, aims to feel like the Pixel Launcher with more customization. No settings detail on the page fetched. | V (limited) |
| Kvaesitso https://github.com/MM2-0/Kvaesitso | "A search focused" launcher. The README defers all customization detail to its user guide. | V (limited) |
| Nothing Launcher, Niagara, Smart Launcher, One UI Good Lock/Theme Park | No first-party docs fetched. Not used as evidence. | not covered |

Takeaway: the best-evidenced pattern is edit mode over the live home screen with one compact panel of few big choices (style, size, tint), plus a "dim the wallpaper" escape when the look hurts legibility. Palette choice propagates everywhere instead of being set per surface.

## 3. Tiling and desktop configuration

| Source | Finding | Tag |
|---|---|---|
| Omarchy manual https://learn.omacom.io/2/the-omarchy-manual/52/themes | One choice (Style > Theme in the Omarchy Menu, or a direct hotkey) restyles the desktop, terminal, Neovim, btop, notifications, status bar, launcher and lock screen. Backgrounds are a separate setting, cycled per theme with one key. | V |
| niri https://niri-wm.github.io/niri/Configuration%3A-Introduction.html | "The configuration is live-reloaded. Simply edit and save the config file." `niri validate` checks a file. Omitted sections take defaults, except `binds {}`. | V |
| Hyprland wiki https://wiki.hypr.land/Configuring/ | A versioned wiki of config pages (Monitors, Config options, Binds, Rules, Animations). The deeper pages were not fetched. | V (limited) |
| COSMIC https://system76.com/cosmic/theming | "Transform the look of your desktop in seconds", community theme library, themes shared as files. A "Theme Constellations" bundle (wallpaper, icons, fonts, colours in one go) was previewed (3P: https://www.linuxtoday.com/blog/system76-teases-cosmic-theme-constellations-for-its-cosmic-desktop/). The official page does not describe live preview. | V / 3P |
| Windows 11 Snap https://support.microsoft.com/en-us/windows/snap-your-windows-885a9b1e-a983-a3b1-16cd-c531795e6241 | Snap layout behaviour is five small toggles under Settings > System > Multitasking (hover on maximize, drag to top, Snap Assist, snap groups, edge-free snapping). Layouts themselves are not user-authored. | V |
| KDE System Settings, GNOME Tweaks vs Settings, macOS Appearance, Stage Manager | Pages not fetched. Not used as evidence. | not covered |

Takeaway: the tiling world splits cleanly. Power configuration is a text file with live reload, validation and defaults for anything omitted. The "feel" of the desktop is delivered as named bundles (Omarchy themes, COSMIC constellations), not as 40 sliders.

## 4. Developer and power-user tools

| Source | Finding | Tag |
|---|---|---|
| Ghostty config https://ghostty.org/docs/config | Single plain-text `key = value` file. Configuration is optional and the project aims to remove the need for it. Reload with a shortcut. Every key doubles as a CLI flag. Docs available offline and via `ghostty +show-config --default --docs`. The page says text is "the only way to configure Ghostty" for now, GUI planned. | V |
| Ghostty themes https://ghostty.org/docs/features/theme | One `theme` key. `theme = dark:X,light:Y` pairs follow system appearance. `ghostty +list-themes` lists them. | V |
| VS Code https://code.visualstudio.com/docs/configure/settings | Settings editor is a GUI front end for `settings.json`. Search filters as you type, with `@` filters (`@modified`, `@tag:advanced`, `@id`). A coloured bar marks changed settings. Per-setting "Reset Setting". "Copy Setting as JSON". User vs Workspace scope, some settings user-only. Applies immediately. A few settings are JSON-only. | V |
| Zed https://zed.dev/docs/configuring-zed | Two routes: a Settings Editor with search and `settings.json`. Defaults are documented per section. Project settings override user ones. Per-language overrides under `languages`. | V |
| Obsidian https://obsidian.md/help/settings | Settings split into Options, Core plugins, Community plugins. Appearance is small: base scheme, one accent colour, three font roles, font size (stored per device and not synced). Themes by installing. CSS snippets for everything else. On mobile, Settings opens as a sheet. | V |
| Raycast, Warp, Arc, Linear | Docs 404ed or were off-topic at the URLs tried. Not used as evidence. | not covered |

Takeaway: the best power-user tools keep one source of truth (a text file), make the GUI a front end over it, show what differs from the default, and give a per-setting reset. Obsidian shows the "small appearance panel plus escape hatch" split.

## 5. Cross-cutting patterns

| Pattern | Evidence | Cost or caveat |
|---|---|---|
| Edit in place beats forms for look | iOS Customize panel over the live screen (3P). Android: put frequent contextual preferences "close to the feature it affects" (V). | Needs a way to reach things that are off-screen or too small to tap. |
| Immediate apply, no confirm | Windows guidelines (V), GNOME [K], VS Code "changes apply immediately" (V). | Terl's current "commit on Done" is the outlier. Needs cheap undo and reset instead. |
| Live preview | Ghostty reload, niri live reload, iOS panel over the real screen. | Preview of expensive effects (blur) costs GPU; apply on release, not per drag frame. |
| Presets plus fine-tune | Omarchy themes, COSMIC constellations, iOS Default/Dark/Tinted/Clear. | A preset has to be a real bundle (palette, glass, corners, gap), or it is just a shortcut. |
| Progressive disclosure | Android subscreens and overview, WinUI SettingsExpander one level, VS Code `@tag:advanced` hides advanced settings. | Hidden things need search to be findable. |
| Search | Android: "for complex or deep settings hierarchies, add search" (V). VS Code and Zed settings search (V). | Search must index labels and synonyms and land on the control. |
| Modified indicator and reset | VS Code coloured bar, `@modified`, per-setting reset (V). | Per-context overrides multiply what must be shown (see below). |
| Per-context overrides | VS Code and Zed scopes (V). | Real cost: users cannot tell which layer won. VS Code needs filters and scope tabs to cope. |
| Fewer settings, better defaults | Android, Windows, Ghostty (all V). | Removing a knob needs a better default and sometimes a preset. |
| Text config plus UI parity | Ghostty (text only), VS Code and Zed (both), niri validate (V). | Two surfaces to keep consistent. |
| Accessibility | 48 dp targets, 4.5:1 and 3:1 contrast, no gesture-only paths (V). Reduce Motion and Reduce Transparency are Apple platform settings [K]. | Glass surfaces need a contrast floor and a respect-system-reduce-transparency path. |

## Recommendations for Terl

1. **Apply immediately; replace "Done saves" with Undo and Revert.**
   - Evidence: Windows ("immediately reflect the change — don't require a confirmation button", V), VS Code (immediate, V), GNOME [K].
   - Trade-off: loses the all-or-nothing draft. In return a snackbar "Undo" plus a "Revert session" action covers mistakes. Per-surface blur or opacity is cheap to apply live; throttle blur re-render to release.

2. **Edit mode over the live desktop with one small panel, not a scaled-down copy.**
   - Evidence: iOS Customize panel over the real Home Screen (3P).
   - Trade-off: no shrunken frame means you edit at true size, so small surfaces are easy to hit. Costs a way to reach surfaces covered by the panel (see 8).

3. **Ship named looks as the primary appearance control.**
   - A Look sets palette, glass (opacity, blur, grain), corner radius and gap together, plus a light/dark/auto pair like Ghostty's `theme = dark:X,light:Y`.
   - Evidence: Omarchy one-switch themes (V), COSMIC constellations (3P), Ghostty paired themes (V), iOS style set (3P).
   - Trade-off: presets hide knobs. Needs a "customised" state for when someone tweaks one (see 6).

4. **Make the fine controls few, large and in one place: five or six per surface.**
   - Evidence: Windows "four or five", Android "manageable number" and "slider for less precise" (V).
   - Trade-off: power users lose rarely used per-surface knobs. They move to the text file (11).

5. **Keep one Base look and let surfaces inherit; show inherit state with one glyph and one-tap "follow base".**
   - Evidence: VS Code scopes and modified bar (V). The per-context override cost is real in every product that has it.
   - Trade-off: inheritance is already in Terl. The fix is visibility and reset, not new overrides. Do not add more override layers than Base plus surface.

6. **Modified marker plus per-control reset plus filter "changed".**
   - Evidence: VS Code coloured bar, per-setting Reset, `@modified` (V).
   - Trade-off: a small mark per row. Needs the default value to be queryable from one place.

7. **Settings search with deep links, and a command palette entry on the control bar.**
   - Evidence: Android recommends search for deep hierarchies (V), VS Code and Zed settings search (V), Obsidian opens settings from the command palette (V).
   - Trade-off: needs a settings index (label, synonyms, route). Each setting must be addressable by id, which also enables 12.

8. **Put the frequent controls on the surface, not in Settings.**
   - Long-press a surface to get its quick card (opacity, blur, radius). Settings keeps only infrequent ones.
   - Evidence: Android "Don't include frequently accessed actions", "close to the feature it affects" (V). Quick Settings tile for frequent access (V).
   - Trade-off: two entry points to one setting. They must read and write the same store.

9. **Respect system accessibility settings; never override them.**
   - Evidence: Android "Don't override settings provided by the system" (V). 48 dp targets, 4.5:1 and 3:1 contrast, accessibility action for every gesture (V).
   - Trade-off: Terl needs a contrast floor on glass that can win over an opacity slider, and a Reduce Transparency path that falls back to solid. Reduce Motion and Reduce Transparency semantics are [K].

10. **Treat font scale as a layout input in every card.**
    - Evidence: sp text, 12 sp minimum (V). Android adaptive guidance to cap width (V).
    - Trade-off: cards must reflow at large scale, so rows are two-line capable and a card may go to half or full sheet by itself.

11. **A single text config with live reload and validation as the power-user door, generated from the same schema as the UI.**
    - Evidence: Ghostty "only way" plus docs via CLI (V), niri live reload and `niri validate` (V), VS Code "Copy Setting as JSON" and GUI-over-JSON (V), Zed (V).
    - Trade-off: two surfaces to keep in parity. One schema generates both, and unknown keys are reported, not silently dropped (niri validate shows the value of an explicit validator).

12. **Every setting is a stable id with a one-line "Copy as config" and a shareable string.**
    - Evidence: VS Code "Copy Setting as JSON" (V), Omarchy themes shared as files (V), COSMIC community themes (V).
    - Trade-off: stable ids become API. Renames need aliases.

13. **Explain disabled controls in place.**
    - Evidence: Windows (V), Android dependency pattern (V).
    - Trade-off: a line of text per disabled row. Use status text, not prose.

14. **Write labels as nouns with status as secondary text, no "Set/Change/Manage", no negatives.**
    - Evidence: Android writing rules (V).
    - Trade-off: none beyond the rewrite. Aligns with the existing plain-copy rule.

15. **Delete a setting before adding one: every release, retire the least-touched knob into a default or the text file.**
    - Evidence: Ghostty "aims to remove the need for configuration" (V), Windows "minimum number" (V), GNOME [K].
    - Trade-off: someone loses a knob. Mitigated by 11.

### Innovative ideas for a one-handed phone tiling desktop

16. **Thumb-zone edit: drag the panel, not the target.**
    - In edit mode the control bar morphs into the editing handle and every surface can be selected by a bottom-edge swipe cycling through surfaces (previous/next), so the thumb never reaches to the top of the screen. The selected surface gets a highlight ring and its card sits in the bottom 40 percent.
    - Rests on: Android 48 dp targets and "don't rely on gestures alone" (V), Material 3 Expressive keeping frequent actions in reach in Quick Settings (V). The cycle is not in any product I found.
    - Trade-off: an invented gesture, so it must also work as two visible chevrons.

17. **Look transplant between surfaces: "pull the look" from any window.**
    - Long-press a surface, then tap another surface or a running window to copy its glass settings, like a format painter. Terminal and Linux GUI windows can be sources.
    - Rests on: iOS wallpaper-first look propagation (3P) and Omarchy's one change reaching every app (V). Neither lets you point at a thing and copy its look.
    - Trade-off: copy should write through the same inherit link (5) or it will create silent overrides. Offer "copy as follow" and "copy as value".

18. **Contrast guardian: a live legibility meter during edits.**
    - While sliding opacity or blur, the panel shows a one-glyph indicator of the worst text-on-glass contrast over the real wallpaper and refuses to go below 4.5:1 without an explicit override. It also offers one tap "dim wallpaper" (iOS shows this sun-dim idea (3P)).
    - Rests on: 4.5:1 and 3:1 thresholds (V).
    - Trade-off: cost of sampling the wallpaper. Compute once per edit release, not per frame.

19. **Appearance by example: "Looks from this wallpaper".**
    - Generate three candidate looks from the current wallpaper (palette, glass, corner, gap), shown as live full-size previews you swipe between, then keep one.
    - Rests on: Material You palette drives the system (V) and presets as the primary control (rec 3). The "swipe between full-size live candidates" is an extension, not found in the sources.
    - Trade-off: three live previews cost GPU. Show one at a time and swipe.

20. **Time-travel for settings.**
    - A bottom-bar "history" strip keeps the last N looks and edits with thumbnails. One tap returns to any.
    - Rests on: immediate apply with undo (1) and VS Code per-setting reset (V).
    - Trade-off: thumbnails hold pixels, so it needs a bounded store, which the project already requires for pixel-holding things.

## Open items

- Re-read the Apple HIG, GNOME HIG and Material 3 pages in a browser to upgrade the [K] items.
- Not researched at all: KDE System Settings, GNOME Tweaks, macOS System Settings, Stage Manager, Raycast, Arc, Warp, Linear, One UI Good Lock, Nothing Launcher, Niagara, Smart Launcher.
