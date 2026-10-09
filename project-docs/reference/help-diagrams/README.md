# Help diagrams

The drawn diagrams on the in-app help topic pages: one vector drawable per topic, 360dp wide,
plus one animated vector (Mouse mode). `generate.py` is the single source for the geometry; the
palette and glyphs come from `../layout-assets/`.

To regenerate, from the repository root:

```
python project-docs/reference/help-diagrams/generate.py        # writes ./out (svg, png, drawable, index.json, sheet)
python project-docs/reference/help-diagrams/to_theme_attrs.py  # copies out/drawable into app/src/main/res/drawable
```

`generate.py` needs `cairosvg` or `rsvg-convert` to rasterise the previews. `out/` is generated
and not committed. `to_theme_attrs.py` rewrites the generator's placeholder colours to theme
attributes (`?attr/colorSurface`, `?attr/colorPrimary`, ...) so the diagrams follow the palette.

The topic to drawable table is `app/src/main/java/com/termux/app/help/HelpDiagrams.java`; a unit
test checks that every help topic has a drawable. Topic labels used by the diagrams are in
`help-strings.txt`.
