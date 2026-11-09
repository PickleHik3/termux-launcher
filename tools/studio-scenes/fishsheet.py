"""Cut a 4x4 fish sprite sheet (fish facing right on flat magenta) into the scene's fish atlas.

Each fish is keyed off the magenta, cleaned of magenta fringe, cropped, scaled to fit a
240x116 box and centred in a 256x128 cell; the atlas is 4 columns x 4 rows (1024x512).
The shader reads cell i at ((i % 4) * 256, floor(i / 4) * 128), head toward +x.

usage: python3 -I fishsheet.py fish-sheet.png fish.png [preview.jpg]
"""
import sys
import numpy as np
from PIL import Image

src, out = sys.argv[1], sys.argv[2]
prev = sys.argv[3] if len(sys.argv) > 3 else None
sheet = np.asarray(Image.open(src).convert('RGB').resize((1024, 1024), Image.LANCZOS)).astype(float)
key = np.array([255.0, 0.0, 255.0])
atlas = Image.new('RGBA', (1024, 512), (0, 0, 0, 0))

for i in range(16):
    cy, cx = divmod(i, 4)
    cell = sheet[cy * 256:(cy + 1) * 256, cx * 256:(cx + 1) * 256]
    d = np.sqrt(((cell - key) ** 2).sum(-1))
    a = np.clip((d - 60) / 80, 0, 1)                 # magenta -> 0, real colour -> 1
    a[:4], a[-4:], a[:, :4], a[:, -4:] = 0, 0, 0, 0   # cell borders are never fish
    # un-mix the magenta from half-transparent edge pixels
    rgb = (cell - (1 - a[..., None]) * key) / np.maximum(a[..., None], 1e-3)
    rgb = np.clip(rgb, 0, 255)
    ys, xs = np.nonzero(a > 0.5)
    if len(xs) == 0:
        print('cell', i, 'is empty'); continue
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    rgba = np.dstack([rgb, a * 255])[y0:y1, x0:x1].astype(np.uint8)
    im = Image.fromarray(rgba, 'RGBA')
    k = min(240 / im.width, 116 / im.height)
    im = im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS)
    atlas.alpha_composite(im, (cx * 256 + (256 - im.width) // 2, cy * 128 + (128 - im.height) // 2))
    print(f'cell {i}: {x1 - x0}x{y1 - y0} px')

atlas.save(out)
if prev:
    bg = Image.new('RGBA', atlas.size, (40, 120, 150, 255)); bg.alpha_composite(atlas)
    bg.convert('RGB').save(prev)
