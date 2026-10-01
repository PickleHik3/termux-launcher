#!/usr/bin/env python3
"""Copy the generated VectorDrawables into app/src/main/res/drawable, turning the generator's
placeholder colours into theme attributes (appearance-layout-editor SPEC section 5), so the
help diagrams follow the user's palette in light and dark.

Run after generate.py: python project-docs/reference/help-diagrams/to_theme_attrs.py
"""
from pathlib import Path
import re

HERE = Path(__file__).resolve().parent
SRC = HERE / 'out' / 'drawable'
DST = HERE.parents[2] / 'app' / 'src' / 'main' / 'res' / 'drawable'

THEME = {
    '#17150f': '?attr/colorSurface',
    '#302c20': '?attr/colorSurfaceContainerHigh',
    '#211e15': '?attr/colorSurfaceContainerLow',
    '#403b2a': '?attr/colorSurfaceContainerHighest',
    '#625c47': '?attr/colorOutlineVariant',
    '#e2dcc5': '?attr/colorOnSurface',
    '#aaa48b': '?attr/colorOnSurfaceVariant',
    '#ebc900': '?attr/colorPrimary',
    '#645400': '?attr/colorPrimaryContainer',
    '#c5ce8c': '?attr/colorTertiary',
    '#485016': '?attr/colorTertiaryContainer',
}

count = 0
for source in sorted(SRC.glob('*.xml')):
    text = source.read_text()
    for placeholder, attribute in THEME.items():
        text = text.replace('"' + placeholder + '"', '"' + attribute + '"')
    leftover = re.search(r'"#[0-9a-fA-F]{6,8}"', text)
    if leftover:
        raise SystemExit(source.name + ': unmapped colour ' + leftover.group(0))
    (DST / source.name).write_text(text)
    count += 1
print('copied', count, 'drawables to', DST)
