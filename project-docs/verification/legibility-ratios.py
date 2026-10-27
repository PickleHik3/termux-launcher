#!/usr/bin/env python3
"""Read ink-on-ground contrast ratios off a screenshot, for the legibility card.

    python3 -I legibility-ratios.py shot.png NAME X0 Y0 X1 Y1 [NAME X0 Y0 X1 Y1 ...]

Each box (device pixels) should hold one run of text on its ground. The ground is the median
pixel by luminance and the ink the brightest pixel (or the darkest, for a NAME starting with
"dark:"), so a box must be mostly ground. Prints the WCAG 2 ratio per box.
"""
import sys
from PIL import Image


def lum(c):
    c = [x / 255 for x in c]
    c = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def ratio(a, b):
    a, b = lum(a), lum(b)
    return (max(a, b) + .05) / (min(a, b) + .05)


def main(argv):
    im = Image.open(argv[1]).convert('RGB')
    args = argv[2:]
    for i in range(0, len(args), 5):
        name, box = args[i], tuple(int(v) for v in args[i + 1:i + 5])
        px = sorted(im.crop(box).getdata(), key=lum)
        n = len(px)
        ground = px[n // 2]
        ink = px[max(3, n // 60)] if name.startswith('dark:') else px[-max(3, n // 60)]
        print('%-30s ink #%02x%02x%02x ground #%02x%02x%02x = %.2f'
              % ((name,) + ink + ground + (ratio(ink, ground),)))


if __name__ == '__main__':
    main(sys.argv)
