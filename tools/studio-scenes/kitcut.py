"""Cut a kit's painted parts (four greys on flat magenta) into the files kit.agsl samples.

Every output is RGBA with the tone (the grey, 0..1) in RGB and the coverage in A.
  strips   far-N / mid-N / fore-N.png (1536x1024)  -> <kind>-N.webp 1024x683, plus skyline rows
  sprites  clouds.png, fauna.png (any grid of six)  -> clouds.webp / fauna.webp, 2 x 3 cells of 512x256
           (clouds centred in the cell, animals standing on row 236, centred)
  trees    trees.png (two trees side by side)       -> trees.webp, 2 cells of 512x1024, trunk on the bottom
Writes pieces.json with each strip's skyline (median and highest painted row, in 1024x683 px).

usage: python3 -I kitcut.py <src dir with far/ mid/ fore/ extra/> <out dir>
"""
import glob, json, os, sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
KEY = np.array([255.0, 0.0, 255.0])


def key(im):
    """RGB image -> (tone 0..1, alpha 0..1) with the magenta un-mixed from the edges."""
    a_rgb = np.asarray(im.convert('RGB')).astype(float)
    d = np.sqrt(((a_rgb - KEY) ** 2).sum(-1))
    a = np.clip((d - 60) / 80, 0, 1)
    rgb = (a_rgb - (1 - a[..., None]) * KEY) / np.maximum(a[..., None], 1e-3)
    tone = np.clip(rgb.mean(-1) / 255, 0, 1)
    return tone, a


def rgba(tone, a):
    t = (tone * 255).astype(np.uint8)
    return Image.fromarray(np.dstack([t, t, t, (a * 255).astype(np.uint8)]), 'RGBA')


pieces = {}
for kind in ('far', 'mid', 'fore'):
    for f in sorted(glob.glob(f'{src}/{kind}/{kind}-*.png')):
        name = os.path.basename(f)[:-4]
        tone, a = key(Image.open(f).convert('RGB').resize((1024, 683), Image.LANCZOS))
        # the strip is solid below its skyline: fill any holes under the first painted row
        top = np.argmax(a > 0.5, axis=0)
        rows = np.arange(683)[:, None]
        below = rows > top[None, :] + 2
        a = np.where(below, 1.0, a)
        tone = np.where(below & (a < 0.99), 0.43, tone)
        rgba(tone, a).save(f'{out}/{name}.webp', quality=92, method=6)
        pieces[name] = {'top': int(np.median(top)), 'peak': int(top.min())}
        print(name, pieces[name])


def sprites(path, outname, align):
    im = Image.open(path).convert('RGB')
    tone, a = key(im)
    a[:6], a[-6:], a[:, :6], a[:, -6:] = 0, 0, 0, 0
    lab, n = ndi.label(ndi.binary_dilation(a > 0.3, iterations=6))
    sizes = ndi.sum(np.ones_like(a), lab, range(1, n + 1))
    keep = np.argsort(sizes)[::-1][:6] + 1
    boxes = [ndi.find_objects((lab == k).astype(int))[0] for k in keep]
    H = im.height
    boxes.sort(key=lambda b: (round((b[0].start + b[0].stop) / 2 / (H / 2) - 0.5), b[1].start))   # rows, then left to right
    atlas = Image.new('RGBA', (1024, 768), (0, 0, 0, 0))
    for i, (ys, xs) in enumerate(boxes):
        piece = rgba(tone[ys, xs], a[ys, xs] * (lab[ys, xs] > 0))
        k = min(480 / piece.width, 220 / piece.height)
        piece = piece.resize((max(1, round(piece.width * k)), max(1, round(piece.height * k))), Image.LANCZOS)
        cx, cy = (i % 2) * 512, (i // 2) * 256
        y = cy + (128 - piece.height // 2 if align == 'centre' else 236 - piece.height)
        atlas.alpha_composite(piece, (cx + 256 - piece.width // 2, y))
        print(outname, i, piece.size)
    atlas.save(f'{out}/{outname}.webp', quality=92, method=6)


sprites(f'{src}/extra/clouds.png', 'clouds', 'centre')
sprites(f'{src}/extra/fauna.png', 'fauna', 'feet')

tp = f'{src}/extra/trees.png'
if os.path.exists(tp):
    im = Image.open(tp).convert('RGB').resize((1024, 1024), Image.LANCZOS)
    tone, a = key(im)
    atlas = Image.new('RGBA', (1024, 1024), (0, 0, 0, 0))
    for i in range(2):
        t, al = tone[:, i * 512:(i + 1) * 512], a[:, i * 512:(i + 1) * 512].copy()
        # keep only the tree itself: bits of its neighbour can reach across the halfway line
        lab, n = ndi.label(ndi.binary_dilation(al > 0.3, iterations=3))
        if n > 1:
            big = np.argmax(ndi.sum(np.ones_like(al), lab, range(1, n + 1))) + 1
            al *= (lab == big)
        ys, xs = np.nonzero(al > 0.5)
        piece = rgba(t, al).crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
        k = min(500 / piece.width, 1024 / piece.height)
        piece = piece.resize((round(piece.width * k), round(piece.height * k)), Image.LANCZOS)
        atlas.alpha_composite(piece, (i * 512 + 256 - piece.width // 2, 1024 - piece.height))
    atlas.save(f'{out}/trees.webp', quality=92, method=6)
    print('trees ok')

json.dump(pieces, open(f'{out}/pieces.json', 'w'), indent=1)
