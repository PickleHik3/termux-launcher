
07 · Look · Assistant page build spec
Contents01 Page02 Composer03 Model chip04 No model05 Chats list06 Wall entry07 Look
# 07 · Look
One chat across dark and light, cool and warm wallpapers, and glass on and off, with the colour roles that change and the ones that do not.
Wave 1 · 412 × 915 dp frames · Termux Launcher v2 design system
 Numbered callouts Size overlay on frames Fit more (60% scale)Frames are 412 × 915 dp at 1 dp = 1 CSS px when scale is off.
Glass on
Glass off
Colour roles
## One chat, eight looks
The same chat in dark and light, over a cool and a warm wallpaper, with the page surface as glass and as Solid. Colours are Material You roles taken from the wallpaper; geometry, type and spacing never change.
### 07.AGlass on (Base Look)
10:56
### Feeding a starter
 E2B
How do I keep a sourdough starter going?
Feed it once a day while it lives on the counter:
50 g flour
50 g water
Keep about a spoonful of the old starter and discard the rest.
In the fridge it needs feeding about once a week.
Ask anything
07.1 · Dark, cool, glass onDark · Cool wallpaper · Glass on
10:56
### Feeding a starter
 E2B
How do I keep a sourdough starter going?
Feed it once a day while it lives on the counter:
50 g flour
50 g water
Keep about a spoonful of the old starter and discard the rest.
In the fridge it needs feeding about once a week.
Ask anything
07.2 · Light, cool, glass onLight · Cool wallpaper · Glass on
10:56
### Feeding a starter
 E2B
How do I keep a sourdough starter going?
Feed it once a day while it lives on the counter:
50 g flour
50 g water
Keep about a spoonful of the old starter and discard the rest.
In the fridge it needs feeding about once a week.
Ask anything
07.3 · Dark, warm, glass onDark · Warm wallpaper · Glass on
10:56
### Feeding a starter
 E2B
How do I keep a sourdough starter going?
Feed it once a day while it lives on the counter:
50 g flour
50 g water
Keep about a spoonful of the old starter and discard the rest.
In the fridge it needs feeding about once a week.
Ask anything
07.4 · Light, warm, glass onLight · Warm wallpaper · Glass on
#### Glass on · 07.1 to 07.4
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Page surface | corner 24; side gap 12; bottom gap 8 | — | dark: surfaceContainerHigh at 34%; light: surfaceContainer at 34% | 
| | Base Look: blur 8, opacity 34%, grain 18%, tint 0, hairline rim (white 18% dark / 45% light). Even out on. | 
| 2 | Your message | corners 20 20 6 20 | body text-base 16/24 Regular | primaryContainer / onPrimaryContainer | 
| | Takes the wallpaper hue: blue on the cool wallpaper, amber on the warm one. | 
| 3 | Answer | — | body text-base 16/24 Regular | onSurface; list markers onSurfaceVariant | 
| | Ink sits directly on the glass; no bubble. | 
| 4 | Composer | min 56; corner 28 | body text-base 16/24 Regular | surfaceContainerHigh, opaque | 
| | Opaque in every look so the field stays readable. | 
| 5 | Model chip | 32 / 48 | labelSm text-md 14/20 Medium | surfaceContainerHighest; Ready dot success | GlassOnly the page surface and the status pill are glass. Everything inside the surface is opaque and keeps the same roles in both modes.MotionSwitching the look (system theme, wallpaper change, or the launcher's appearance editor) cross-fades the page over 220 ms on the settle curve. No other motion.
### 07.BGlass off (Solid)
10:56
### Feeding a starter
 E2B
How do I keep a sourdough starter going?
Feed it once a day while it lives on the counter:
50 g flour
50 g water
Keep about a spoonful of the old starter and discard the rest.
In the fridge it needs feeding about once a week.
Ask anything
07.5 · Dark, cool, glass offDark · Cool wallpaper · Glass off (solid)
10:56
### Feeding a starter
 E2B
How do I keep a sourdough starter going?
Feed it once a day while it lives on the counter:
50 g flour
50 g water
Keep about a spoonful of the old starter and discard the rest.
In the fridge it needs feeding about once a week.
Ask anything
07.6 · Light, cool, glass offLight · Cool wallpaper · Glass off (solid)
10:56
### Feeding a starter
 E2B
How do I keep a sourdough starter going?
Feed it once a day while it lives on the counter:
50 g flour
50 g water
Keep about a spoonful of the old starter and discard the rest.
In the fridge it needs feeding about once a week.
Ask anything
07.7 · Dark, warm, glass offDark · Warm wallpaper · Glass off (solid)
10:56
### Feeding a starter
 E2B
How do I keep a sourdough starter going?
Feed it once a day while it lives on the counter:
50 g flour
50 g water
Keep about a spoonful of the old starter and discard the rest.
In the fridge it needs feeding about once a week.
Ask anything
07.8 · Light, warm, glass offLight · Warm wallpaper · Glass off (solid)
#### Glass off (Solid) · 07.5 to 07.8
| # | Element | Size (dp) | Type | Colour role | 
| | Page surface | same geometry | — | surfaceContainer at 77.5% | 
| | Solid material at intensity 50: opacity 55 + 0.45 × 50 = 77.5%, blur 0, grain 0. The rim stays. | 
| | Everything else | unchanged | unchanged | unchanged | 
| | Bubble, answer, composer, chip and actions are identical to glass on. | GlassGlass off is the launcher's Solid material, picked per surface or globally in the existing appearance editor (Glass, Frost or Solid, plus the intensity slider). The Assistant page reads the same setting as the other pages and widgets; it adds no setting of its own.MotionNone.
## Colour roles and legibility
Cool dark is the scheme measured on the device of record (pong-blue). Cool light comes from the same measurements. Both warm schemes are the Material tones for an amber seed and are inferred. On the phone every role comes from the wallpaper through dynamic colour, so build by role, never by hex.
Roles that change with the wallpaper (sample values these frames use)
| Role | Cool dark | Cool light | Warm dark | Warm light | Used for | 
| surfaceContainer | #171c24 | #f0f3ff | #271e18 | #f9ebe3 | Solid page surface; glass fill (light) | 
| surfaceContainerHigh | #252a33 | #e4e8f4 | #322822 | #f3e5dd | Glass fill (dark); composer; recent chat cards | 
| surfaceContainerHighest | #30353e | #dde2ee | #3d332c | #ede0d8 | Model chip; code blocks; inline code | 
| onSurface | #e8edf7 | #171c24 | #f0dfd6 | #231a14 | Answers, titles, field text | 
| onSurfaceVariant | #c0c7d5 | #434750 | #d7c2b6 | #53443b | Placeholder, icons, meta text | 
| primary | #a6c8ff | #005eb1 | #ffb68b | #8f4c1d | Send and Stop disc, caret, focus ring | 
| primaryContainer | #004787 | #d5e3ff | #743500 | #ffdbc8 | Your messages; themed icon discs | 
| secondaryContainer | #3e4565 | #dce1ff | #5c4030 | #ffdbc8 | Jump to latest; tonal buttons | 
Legibility rule: onSurface on the page surface must reach 4.5:1 and onSurfaceVariant 3:1 over any wallpaper. Glass meets this through the launcher's Even out setting, on by default, which evens the brightness of the blurred wallpaper behind the surface. These frames approximate it with a backdrop brightness filter (dark × 0.62, warm dark × 0.5, light × 1.12 to 1.18); on the phone use the existing Even out path, not a new filter.
If Even out is off and the wallpaper is busy, the page does not compensate on its own: Solid is the person's fix, as on every other glass surface.
Fixed in every look: surface corner 24, rounded surfaces and cards 20, composer 28 (full capsule at one line), chips full; side gap 12; 4 / 8 spacing grid; the type tokens on sheet 01.
No drop shadows on glass. Only the bottom sheets and dialogs are raised.
Two accents at most per screen: here, your message (primaryContainer) and the Send disc (primary).

