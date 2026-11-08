"""Lift the clouds out of a scene's art into their own RGBA layer, so the shader can drift them
across the screen over a clear sky. Alpha = how far a pixel is from its row's clear-sky colour
(the right edge). Clouds cut by the art's left/right edge are feathered so they enter softly."""
import sys; from PIL import Image, ImageFilter; import numpy as np
src, out, top = sys.argv[1], sys.argv[2], int(sys.argv[3])
im = np.asarray(Image.open(src).convert('RGB')).astype(float); H, W, _ = im.shape
clear = im[:, W - 6:W - 5, :]
d = np.abs(im - clear).sum(-1)
a = np.clip((d - 20) / 40, 0, 1)                     # ignore the faint grain of the sky
a[top:] = 0
a = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))) / 255
xs = np.arange(W)[None, :]
a *= np.clip(xs / 90, 0, 1) * np.clip((W - 1 - xs) / 90, 0, 1)   # feather edge-cut clouds
rgba = np.dstack([im, a * 255]).astype(np.uint8)
Image.fromarray(rgba, 'RGBA').save(out)
print('cloud cover', (a > 0.2).mean().round(4))
