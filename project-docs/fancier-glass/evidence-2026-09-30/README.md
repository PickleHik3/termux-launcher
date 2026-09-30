# Window-switch evidence, 2026-09-30

Source: pong recording `/sdcard/Movies/screen-20260930-075722.mp4`. The build is integ 8f3d7ed0, which is dev 3fa80d17 plus the dictation marks. It includes the 40df0d1a slabOutline fix and not the 6d89879a PaneSnapshot fix. The capture is 1080x2412 at about 49 fps, with Mist and Fancier Glass on. The clip shows window switches: one pane → three panes (t 1.48–2.26 s), → herdr (2.50–3.08 s), and → three panes again (3.51–4.06 s).

Per-frame measurements at full resolution. Edges are the rim brightness peaks on row y=400.
- The incoming panes settle monotonically, with no overshoot in x or y. The left pane's right rim reaches x=324, the right column's left rim reaches 338, and the pane top stays at y=223 throughout.
- The outgoing single pane's left edge appears twice, about 28 px (≈10.7 dp at 2.625x) apart, with a constant offset while the pan decelerates.
  - Copy A sits exactly where the page pitch predicts: incoming gap − 324 + 1080 + 14.
  - Copy B sits 28 px to its left.
  - Frames 46–57, as A/B pairs: 989/–, –/971, 1011/983, 1020/–, –/1000, 1037/–, –/1015, 1050/–, 1056/1028, –/1033, –/1038, –/1042.
  - A constant offset at varying speed means a geometry or origin mismatch, not a timing lag.
- departure-card-right-edge-frames45-50.jpg (x 780–1080, frames 45–50): the outgoing pane sits on a dark rectangle with square top corners, and its rounded rim is inset inside it. This matches the developer's "sharp corners, tinted black".
