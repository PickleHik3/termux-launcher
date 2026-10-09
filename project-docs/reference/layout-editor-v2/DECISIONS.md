# Layout editor v2: decisions (2026-10-02)

The developer settled these on 2026-10-02 (grilling rounds 1–4, `.lavish/layout-editor-v2/round-4.html`). Where they disagree with `README.md` in this folder, the decisions here win. Where they disagree with `project-docs/active/appearance-layout-editor/SPEC.md`, they replace those parts of the spec.

## Layout mode

1. `README.md` and `editor-preview.png` are the spec. `design-concept.png` is for mood only.
2. The eye-off control (`ic_layout_hide`) is the **only** hide drop target. Remove the dashed "Drag here to hide" zone (`layout_editor_hide_zone.xml`) and the trash button, and give the 56dp back to the miniature. Eye-off carries a count badge, which is absent when nothing is hidden. While a bar is lifted, eye-off highlights as an accepted drop with a highlight area larger than its 48dp target.
3. Tapping eye-off toggles an **inline row of hidden-element tiles inside the sheet**, which replaces the `HiddenElementsPopup` window. Each tile shows a restore arrow (`ic_layout_restore`). Tapping a tile restores the element to its last edge. Dragging a tile onto the canvas still restores it at the chosen edge.
4. Lift-anywhere stays: a bar can be dragged from anywhere on it. Tapping a bar selects it, which draws the selection outline from the bar's own shape, shows the four-way move control (`ic_layout_move`) outside the bar, and highlights its valid edge destinations. Only accepted drops highlight. A drop commits on release. Back, or a release outside the canvas, cancels. The terminal stays the anchor.
5. Tapping the move control opens a destination menu (top / bottom / left / right / hide, whichever are valid). This is the touch alternative to dragging. It reuses the existing accessibility actions (`LayoutCanvasView.performElementAction`, `strings_layout_canvas_a11y.xml`).
6. Keep the existing keyboard-type chips (Docked / Floating / Split) and restyle them only. **Key radius** (the key-cap radius, `setInAppKeyboardKeyCornerRadiusDp`) moves here from Look mode and shows when the keyboard is selected. The resize handles and their readouts stay.
7. The canvas draws the live variant: compact or expanded status bar, full or split keyboard, normal or minimal layout. Do not add toggles for these.
8. The **app icons bar uses 7 fixed placeholder icons** in both row and rail, as in `docked/app-icons-*.svg`: tonal circles with generic glyphs (phone, chat, globe, terminal, play, display, grid). Never draw the user's real pinned apps or follow their count. This reverses the 2026-10-02 `Host.pinnedAppIcons` canvas change. **Extra keys stay live** (real glyphs).
9. Separate selection outlines distinguish the app bar, the alphabet strip and the extra keys, even where they share a material background. Docked pieces join with square internal corners under one outer clip. Floating uses the per-surface shape policy (ADR 0007).
10. The top row (`Look | Layout`, Undo, Done) stays. The Layout body has the orientation pair, the Style pair (`ic_layout_docked` / `ic_layout_floating`) and eye-off, then a row with Corner radius and Margin, both unchanged.
11. No visible hint text. Rewrite or remove the strings that mention the trash, the hide zone or the restore tray: `layout_editor_hide_zone_hint`, `layout_editor_trash_*`, help `help_topic_layout_step1..3`, and the glossary `help_term_tray_definition`. Selected state and descriptive labels are exposed to accessibility only.
12. Colour roles come from `tokens.json` resolved against the active scheme (selected = primaryContainer / onPrimaryContainer). Glyphs are 24dp inside touch targets of at least 48dp. No literal colours, radii or text sizes (M3 consistency rule).

## Look mode

13. At the **Custom** stop, with nothing tapped, row 2 shows global **Blur, Opacity and Grain** sliders, which write the base `SurfaceProperty.BLUR / OPACITY / GRAIN` values. Tapping an element swaps in that element's row. Tapping the empty canvas brings the global row back. No global row appears on the Look stops. Margin is **not** added here; it stays in Layout only.
14. Global Opacity and Grain follow the rule global Blur already follows: writing them re-attaches every surface, terminal included, to the base value. The terminal's own Opacity control stays for fine-tuning afterwards.
15. Tapping the keyboard shows **Blur** plus a **"Keyboard theme ›"** button, and Key radius is gone from this row (it moved to Layout, item 6). The button deep-links to the Keyboard theme settings page (`SettingsActivity.createFragmentIntent`). Back returns to the editor with its session intact. The editor's Undo does not cover changes made on that page.

## Keyboard theme page

16. Merge Theme (`in_app_keyboard_theme`), the colours (`KeyboardColorSchemeFragment`) and Typeface (`in_app_keyboard_font`) into one page titled "Keyboard theme", with its live keyboard preview showing the chosen typeface. Settings → Style keeps a single row that opens this page.

## Rim (Mist and Custom under the rounded Style)

17. The reference is the dock as it renders under Mist with the Floating Style: the drawn rim is hidden under its own backdrop, and the visible edge comes from the Fancier Glass edge light (the refraction shader). Under **every Look**, the keyboard, status bar, under-keyboard card and any other glass surface stop drawing a visible rim drawable, and get their edge from the Fancier Glass edge light the way the dock does. **When Fancier Glass is off**, keep the existing faint hairline rim so cards keep an edge.
18. Retire the gradient rim option (`GLASS_RIM_GRADIENT`, `GradientRimDrawable`). Mist stops setting it, and a saved Custom look that carries `surface_glass_rim = gradient` reads as the default. The dock's touch/tilt glow (`DockEdgeGlowView`) stays on the dock only.
