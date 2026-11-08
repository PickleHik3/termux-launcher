"""Build a scene's region map from its flat-poster art plus a rough AI label map.

The label map (an image edit of the art) paints sky red, water green, swaying things blue,
lights white, everything else black. It is allowed to be sloppy and slightly misaligned:
the edges come from the art itself. The art is split into its flat-colour shapes, and each
shape takes the majority label of the label-map pixels inside it.

Output: <out>.png RGB = R sky, G water, B sway (spilled past each shape so tips move);
<out>-lights.png greyscale = an id 1..255 per lit shape (0 none). Sample it with nearest filtering.
The lights are not the alpha channel: decoders premultiply, which would wipe the masks.

usage: python3 -I regions.py art.png labels.png out.png preview.jpg [sky_max_y]
"""
import sys
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage as ndi

art_p, lab_p, out_p, prev_p = sys.argv[1:5]
SKY_MAX_Y = int(sys.argv[5]) if len(sys.argv) > 5 else 0   # below this row nothing is sky
art_img = Image.open(art_p).convert('RGB')
W, H = art_img.size
art = np.asarray(art_img.filter(ImageFilter.MedianFilter(3))).astype(int)   # drop the grain
lab = np.asarray(Image.open(lab_p).convert('RGB').resize((W, H), Image.NEAREST)).astype(int)

# 1. Label map -> classes by nearest key colour.
keys = np.array([[0, 0, 0], [255, 0, 0], [0, 255, 0], [0, 0, 255], [255, 255, 255]])
NAMES = ['static', 'sky', 'water', 'sway', 'lights']
dist = ((lab[..., None, :] - keys[None, None]) ** 2).sum(-1)
cls = dist.argmin(-1)

# 2. Art -> flat-colour shapes: cut along colour edges, label the connected pieces.
diff = np.zeros((H, W), int)
diff[:, 1:] = np.maximum(diff[:, 1:], np.abs(art[:, 1:] - art[:, :-1]).sum(-1))
diff[1:, :] = np.maximum(diff[1:, :], np.abs(art[1:] - art[:-1]).sum(-1))
edge = diff > 24
seg, n = ndi.label(~edge)
# edge pixels join their nearest shape
idx = ndi.distance_transform_edt(seg == 0, return_distances=False, return_indices=True)
seg = seg[idx[0], idx[1]]

# 3. Majority class per shape. Shapes too small to trust (grain, tiny details) take the
#    label map's own class pixel by pixel.
counts = np.zeros((n + 1, len(keys)), int)
np.add.at(counts, (seg.ravel(), cls.ravel()), 1)
size = counts.sum(1)
major = counts.argmax(1)
share = counts.max(1) / np.maximum(size, 1)
# A shape follows its majority only when the vote is clear; a shape that merged across real
# boundaries (dark buildings into a dark sky) has a split vote and keeps the label map's pixels.
trust = (size >= 40) & (share >= 0.8)
out_cls = np.where(trust[seg], major[seg], cls)
if SKY_MAX_Y:
    rows = np.arange(H)[:, None]
    out_cls = np.where((out_cls == 1) & (rows > SKY_MAX_Y), 0, out_cls)

def chan(k):
    return (out_cls == k)

def soft(m, r=1.2):
    return np.asarray(Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r)))

sway = chan(3)
spill = np.asarray(Image.fromarray((sway * 255).astype(np.uint8))
                   .filter(ImageFilter.MaxFilter(13)).filter(ImageFilter.GaussianBlur(4)))
R, G = soft(chan(1)), soft(chan(2))
# lights: each connected lit shape gets its own id 1..255 (no blur, ids must not mix)
lit, nl = ndi.label(chan(4))
rng = np.random.default_rng(7)
ids = np.concatenate([[0], rng.integers(1, 256, nl)])
A = ids[lit].astype(np.uint8)
B = np.maximum(spill, soft(sway))
Image.fromarray(np.dstack([R, G, B]).astype(np.uint8), 'RGB').save(out_p)
Image.fromarray(A, 'L').save(out_p.replace('.png', '-lights.png'))

p = np.asarray(art_img).astype(float)
for m, c in ((R, (255, 60, 60)), (G, (60, 255, 255)), (soft(sway), (255, 255, 0)), ((A > 0) * 255, (255, 255, 255))):
    a = (m / 255.0)[..., None] * 0.55
    p = p * (1 - a) + np.array(c) * a
Image.fromarray(p.astype(np.uint8)).resize((W // 2, H // 2)).save(prev_p)
print(' '.join(f'{NAMES[k]}={chan(k).mean():.3f}' for k in range(1, 5)), f'shapes={n}')
