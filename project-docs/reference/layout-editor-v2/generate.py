"""Generate vector design sources; never writes application resources."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET
import zipfile

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
# Reuse the established miniature glyph grammar without executing its exporter.
source = ROOT / 'project-docs/reference/layout-assets/generate.py'
scope = {'__file__': str(source)}
exec(source.read_text().split("for mode in ['floating','docked']:")[0], scope)
C = scope['C']
C.update(canvas='#1b140b', surface='#30271b', terminal='#231c12',
         key='#3c3327', line='#756653', text='#f0e0cc', muted='#c9b69d',
         accent='#ffbc57', accent_bg='#604211', secondary='#e2c48d', secondary_bg='#51452c')
svg, rect, path, text, place = [scope[n] for n in ('svg', 'rect', 'path', 'text', 'place')]

def save(name, w, h, content):
    target = OUT / (name + '.svg')
    target.parent.mkdir(parents=True, exist_ok=True)
    content = content.replace('☾', '◔')
    target.write_text(svg(w, h, content))

specs = {}
for mode in ('docked', 'floating'):
    surfaces = {
        'statusbar': (336, 32, scope['status'](336, 32, mode)),
        'statusbar-expanded': (336, 80, scope['status'](336, 80, mode)),
        'terminal': (336, 240, scope['terminal'](336, 240, mode)),
        'keyboard': (336, 180, scope['keyboard'](336, 180, mode)),
        'keyboard-split': (336, 180, scope['keyboard'](336, 180, mode, 'split')),
    }
    for vertical in (False, True):
        suffix = 'vertical' if vertical else 'horizontal'
        for name, kind, thickness in (('app-icons', 'dock', 48), ('extra-keys', 'toolbar', 40)):
            w, h = (thickness, 336) if vertical else (336, thickness)
            surfaces[name + '-' + suffix] = (w, h, scope['symbols'](w, h, mode, kind, vertical))
        w, h = (28, 336) if vertical else (336, 28)
        surfaces['alphabets-' + suffix] = (w, h, scope['alphabet'](w, h, mode, vertical))
    for name, (w, h, content) in surfaces.items():
        save(mode + '/' + name, w, h, content)
        specs[mode + '/' + name] = [w, h]

controls = {
    'portrait': 'M7 3h10v18H7ZM10 18h4',
    'landscape': 'M3 7h18v10H3ZM18 10v4',
    'docked': 'M5 3h14v18H5ZM5 16h14M5 18h14',
    'floating': 'M5 3h14v18H5ZM8 15h8v3H8Z',
    'hide': 'M3 3l18 18M10 5c5-1 9 3 12 7-1 2-2 3-4 4M6 6c-2 1-4 3-5 6 3 5 7 8 13 6M10 10a3 3 0 0 0 4 4',
    'restore': 'M4 10V4M4 10h6M4 10a8 8 0 1 1 0 6',
    'move': 'M12 3v18M3 12h18M9 6l3-3 3 3M9 18l3 3 3-3M6 9l-3 3 3 3M18 9l3 3-3 3',
    'done': 'M5 12l4 4L19 6',
}
for name, d in controls.items():
    save('controls/' + name, 24, 24, path(d))
    # Android vectors use runtime tint; path data is independent of preview palette.
    folder = OUT / 'android'
    folder.mkdir(exist_ok=True)
    (folder / ('ic_layout_' + name + '.xml')).write_text(
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
        '    <path android:fillColor="@android:color/transparent" android:strokeColor="?attr/colorOnSurface"\n'
        '        android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round"\n'
        f'        android:pathData="{d}" />\n</vector>\n')

# No hints or labels in the screen. Individual bars remain visually separable.
board = rect(0, 0, 412, 892, C['canvas'])
board += rect(26, 24, 360, 720, C['terminal'], 24, C['line'])
y = 36
for name in ('statusbar', 'terminal', 'app-icons-horizontal', 'alphabets-horizontal', 'extra-keys-horizontal', 'keyboard'):
    w, h = specs['floating/' + name]
    content = (OUT / ('floating/' + name + '.svg')).read_text().split('>', 1)[1].rsplit('</svg>', 1)[0]
    if name == 'alphabets-horizontal':
        content += rect(1, 1, w-2, h-2, 'none', 9, C['accent'])
    board += place(content, 38, y)
    y += h + 6
board += rect(0, 744, 412, 148, C['surface'], 28)
for x, names in ((24, ('portrait', 'landscape')), (144, ('docked', 'floating'))):
    board += rect(x, 768, 104, 48, C['key'], 24)
    board += rect(x+2, 770, 48, 44, C['accent_bg'], 22)
    for i, name in enumerate(names):
        board += place(path(controls[name], C['accent'] if i == 0 else C['text']), x+14+i*52, 780)
board += rect(272, 768, 48, 48, C['key'], 24) + place(path(controls['hide']), 284, 780)
board += rect(336, 768, 52, 48, C['accent'], 24) + place(path(controls['done'], C['canvas']), 350, 780)
board += place(path(controls['restore'], C['muted']), 284, 842)
save('editor-preview', 412, 892, board)
palette_roles = dict(canvas='colorSurface', surface='colorSurfaceContainerHigh',
    terminal='colorSurfaceContainerLow', key='colorSurfaceContainerHighest',
    line='colorOutlineVariant', text='colorOnSurface', muted='colorOnSurfaceVariant',
    accent='colorPrimary', accent_bg='colorPrimaryContainer', secondary='colorTertiary',
    secondary_bg='colorTertiaryContainer')
(OUT / 'tokens.json').write_text(json.dumps(dict(preview_palette=C, roles=palette_roles,
    minimum_touch_target_dp=48, glyph_dp=24, dimensions=specs), indent=2)+'\n')
for file in list(OUT.rglob('*.svg')) + list(OUT.rglob('*.xml')):
    ET.parse(file)
with zipfile.ZipFile(OUT / 'layout-editor-v2.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for file in sorted(OUT.rglob('*')):
        if file.is_file() and file.suffix != '.zip':
            z.write(file, file.relative_to(OUT))
print(f'Validated {len(specs)} surface SVGs, {len(controls)} control SVGs and Android vectors.')
