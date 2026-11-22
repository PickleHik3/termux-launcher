# Home dock UX: ergonomics, discoverability, glanceability, glass legibility, adaptivity (2026-10-20)

Question: what do HCI and perception studies say that a redesign of termux-launcher's Home dock can
use, in numbers? Five sub-questions: (1) ergonomics, (2) gesture-only entries vs visible
affordances, (3) glanceable status, (4) glass legibility and realism, (5) expert vs novice and
customisation.

Conversions used throughout: 1 dp = 0.159 mm (160 dpi baseline), so 1 mm = 6.3 dp **[computed]**.
Tags: **[full text]** = I read the PDF (or the publisher HTML) for the passages cited;
**[abstract]** = abstract only; **[prior]** = taken from the earlier notes, which hold the quotes:
`docs/research/liquid-glass-design.md` (LGD) and `glass-perception-research-2026-10-20.md` (GPR,
15 perception papers). **[inference]** = my design conclusion, not a measured result.

## Answer in short (2026-10-20)

1. **Targets.** Thumb studies on one-handed use land at 9.2-10 mm, i.e. 58-63 dp, for a target that
   does not cost speed or accuracy; 7 mm (44 dp) is the floor where speed is still fine but
   accuracy drops. The platform floor is 48 dp (Android) and 24 CSS px (WCAG 2.5.8). Draw dock tiles
   at 56-60 dp, keep the hit area at least 48 dp, and never let adjacent hit areas overlap.
2. **Place.** The thumb is fastest and happiest in the centre of the screen and slowest at the edges;
   the lower-right (for a right thumb) and the far-left/bottom corners are the worst for errors.
   Android users in the wild put their launch icons in the lower part of the screen. A bottom dock
   is right; keep the most-used items in the middle of the dock, not at its far ends.
3. **Gestures.** A swipe from an edge is faster than a soft button (about 12 % faster seated, 12-16 %
   walking or distracted) and survives distraction, but strokes are not self-revealing. Pair every
   gesture-only entry with a visible cue, and let the cue fade once used.
4. **Glanceable status.** Wearable logs show over 80 % of sessions are glances with a median of
   7 s. Status in the dock should be abstract (a shape, a ring, a dot), steady, and change slowly;
   only real interrupts should change abruptly.
5. **Glass.** Text legibility on a see-through plate holds up to about 50 % background showing, and
   fails past 75 %; a luminance-contrasting outline moves that limit by about one step. This
   confirms the frost-the-face rule in GPR. Icon reflections: no study exists, so the numbers below
   come from Fresnel and the gloss papers in GPR.
6. **Adaptivity.** People prefer a menu they control (55 %) to one that adapts itself (30 %) or a
   static one (15 %). Do not auto-sort the dock; adapt at the level of a suggested slot or page, and
   keep dock slots stable.

## 1. Ergonomics

### 1.1 Target size

- **Parhi, Karlson & Bederson 2006**, MobileHCI [full text]. 20 right-handed participants, standing,
  right thumb, 8.9 cm iPAQ, lift-off selection. Discrete targets (a button): no significant error
  difference for sizes >= 9.6 mm (60 dp); recommendation 9.2 mm (58 dp). Serial targets (keypad):
  no difference >= 7.7 mm (48 dp); recommendation 9.6 mm (60 dp). Abstract and results, pp. 203,
  207-208. Speed kept improving up to 11.5 mm (72 dp). Their pilot found "lone targets were perceived
  easier to tap than those near other objects", so they surrounded targets with distractors to test
  realistically (p. 205).
- **Park & Han 2010**, *Int. J. Industrial Ergonomics* 40(1):68-76 [full text, publisher HTML,
  Sec. 4]. 30 subjects, 3 sizes (4, 7, 10 mm), 25 locations, one thumb. 7 mm and 10 mm gave the best
  times; 10 mm (63 dp) alone gave the best result for errors and for preference. 4 mm produced many
  errors. They recommend 7 mm when time and number of keys matter and 10 mm for all measures
  together (Sec. 4.3).
- **Platform floors (primary docs).** Android: "at least 48dpx48dp. Larger is even better"
  ([developer.android.com/guide/topics/ui/accessibility/apps](https://developer.android.com/guide/topics/ui/accessibility/apps),
  fetched 2026-10-20). WCAG 2.2 SC 2.5.8: "at least 24 by 24 CSS pixels", with an exception when
  undersized targets are spaced so their 24 px circles do not intersect another target
  ([w3.org/WAI/WCAG22/Understanding/target-size-minimum.html](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html)).
- **Reading across.** The studies measure the target's visible key; a 48 dp floor is below what the
  thumb studies recommend (58-63 dp). **[inference]** Dock tile 56-60 dp with a 48 dp minimum hit
  area on every item, including the hidden-gesture strip.
- **Spacing.** No study I read isolates spacing for thumb use. Parhi used 0 mm edge-to-edge for the
  keypad and notes Colle & Hiszem (cited there, not read) found spacing mattered little; Park & Han
  list spacing and number of keys as untested (Sec. 5). The gap is real: spacing in the mockups
  is a taste decision. The only anchor is the soft-button baseline in Bragdon et al. 2011: Android
  2.1 keypad keys 12.7 x 7.9 mm with a 2 mm gap (about 80 x 50 dp with a 12.6 dp gap) **[full text]**. **[inference]** 8-12 dp between tile edges.

### 1.2 Where the thumb reaches

- **Bergstrom-Lehtovirta & Oulasvirta 2014**, CHI [full text for abstract, Discussion]. A quadratic
  kinematic model predicts the thumb's reachable "functional area" from screen size, hand size and
  index-finger position on the back, fitted to the thumb trajectories of 20 participants. Their
  data show the thumb "could not have reached the opposite upper corner" in a case where a heuristic
  zone chart said it could, and prediction error relates to fingertip size, so the model suits
  "medium-sized and large" elements, not small ones near the limit.
- **Le, Mayer, Bader & Henze 2018**, CHI [full text for abstract and design implications]. Four
  phone sizes: the comfortable area is the part reachable "without losing grip stability", and "the
  grip shifts towards the top edge with increasing phone sizes".
- **Parhi 2006** (location, Fig. 5-6, p. 207-208 [full text]). Location did not change speed or
  error rates significantly in their constrained design, but hits scattered more and participants
  wanted larger targets in the NW, SW and SE corners (mean 7.5-7.7 mm) than in the centre (6.0 mm);
  "most difficulty ... along the left side and bottom right corner".
- **Park & Han 2010** (Sec. 4.2-4.3 [full text]). Location significantly affected all measures.
  First transition time was fastest in the centre, slow at the edges. Errors were lowest on the left
  part of the screen (the hand hides less) and worst in the lower-right (locations 20, 24, 25). Their
  recommendations: frequent shortcuts in the centre; accurate controls such as a camera shutter in the
  leftmost area. Right-handed subjects.
- **Reading across.** Parhi and Park & Han disagree on whether location matters for speed; Park & Han
  explain it as Parhi fixing thumb distance and direction. For a dock the practical result is the
  same in both: centre good, corners worse, lower-right worst for a right thumb. **[inference]** Put
  the three most-used dock items in the middle of the row, put precise or risky controls toward the
  side away from the thumb, and mirror for left-handed use.

### 1.3 Bottom placement and how many items

- **Böhmer & Krüger 2013**, CHI "A study on icon arrangement by smartphone users" [full text]. 130+
  participants, 1,400+ screenshots. iPhone sample: mean 95.5 apps, 3.75 pages, 7.8 folders; one page
  held 20 icons (4 columns x 5 rows), with the fifth row a quick-start bar shown on every page.
  The Android screenshots showed "people place app icons on the lower instead of the upper part of
  their mobile screens for launching apps, and put other content above", so the authors reverse the
  usual top-left-first order: "from most important at bottom to least important at top", and propose
  static icons in the lower part with adaptive content above (Discussion, p. 2144). Users who
  arrange by usage keep more apps on the first page (t = 2.475, p < .05).
- **Number of dock items.** No study in this pass measured how many pinned items a dock should hold.
  The nearest evidence is geometric: 4 columns in the iPhone grid above, and a 360 dp-wide screen
  fits 4 tiles of 60 dp with 3 gaps of 16 dp and 2 side margins of 24 dp (240 + 48 + 48 = 336 dp).
  Five tiles with 12 dp gaps and 16 dp margins need 5 x 60 + 4 x 12 + 2 x 16 = 380 dp. **[computed]**
  **[inference]** Four pinned items by default; five only on screens at least 392 dp wide (380 dp plus
  slack).
- **Edge vs bottom.** I found no study comparing a screen-edge dock with a bottom dock. The edge
  evidence is Bragdon 2011 (1.4 below) and Le 2018, whose comfortable area favours the thumb's arc
  near the bottom corner of the holding hand. Gap.

### 1.4 Edge-initiated input

- **Bragdon, Nelson, Li & Hinckley 2011**, CHI [full text]. 15 participants, sitting and walking,
  three distraction levels. Bezel-initiated marks 1,093 ms mean vs soft buttons 1,244 ms, a 12 %
  gain; seated and undistracted, the two were not significantly different (1,022 vs 1,097 ms). Under
  walking, marks 1,134 ms vs soft buttons 1,286 ms, and under attention-saturating distraction 1,112
  vs 1,321 ms (both p < .005). Accuracy: marks highest, soft buttons close, hard-button paths lowest
  (83.5 %). "The speed and accuracy of bezel gestures did not appear to be significantly affected by
  environment." The tactile bezel confirms the gesture without looking.

## 2. Gesture-only entries, palettes, search

- **Appert & Zhai 2009**, CHI [full text]. Stroke shortcuts vs keyboard shortcuts: with the same
  practice, participants recalled about three times as many stroke shortcuts a day later, used strokes
  in 96 % of trials (88.5 % for keyboard), and chose strokes 77.7 % of the time on day two (keyboard
  20.3 %, menu 2 %). But "these strokes are not self-revealing ... Often novel features of an interface
  are unused not because they are difficult to use, but because the users are not aware of them.
  Therefore interfaces should offer visual clues" (guidelines section). Their toolkit offers three clues: tooltip,
  menu preview, strokes sheet.
- **Bragdon 2011** adds the cost side: gestures cost no screen space and survive distraction (above).
- **Reading across.** Strong evidence that edge gestures are fast and memorable, and equally strong
  that they are invisible until shown. **[inference]** Rule: the swipe-up palette must have a
  permanent but quiet cue (a 4 dp x 28-32 dp pill or a grip dash on the dock's top edge) until the
  user has used the gesture a few times, and then the cue may shrink. The hint gets the same 48 dp
  hit area as an icon so tapping it also opens the palette.
- **Palette and universal search on mobile.** I found no controlled study of a command palette or
  swipe-up search on a phone launcher. Scopus returned an in-situ study of mobile search and app use
  (Carrascal & Church 2015, CHI) that I did not open. Gap; the palette stays a design decision.

## 3. Glanceable status and attention cost

- **Gouveia et al. 2016**, UbiComp [full text]. Citing earlier logs: over 70 % of tracker usage (and in
  their data over 80 % of sessions) are glances, "brief, 5-second sessions", median 7 s (SD 10). Their
  six qualities of glanceable feedback: abstract, integrated with existing activity, comparison to
  targets, actionable, able to lead to checking habits, a proxy to further engagement. "Abstracting
  data, as opposed to displaying raw data, allows users to process and perceive information with
  minimal consciousness"; all 21 concepts use circles or stylised forms rather than raw numbers.
  Caveat: smartwatch step feedback, not a phone dock.
- **Matthews, Dey, Mankoff, Carter & Rattenbury 2004**, UIST [full text]. Five notification levels:
  ignore, change blind, make aware, interrupt, demand action. Change-blind information "consumes no
  conscious awareness"; it should use "repetitive and very gradual animations" (fading, rolling,
  tickering were not distracting to a low-attention task in the work they cite). Abrupt transitions
  are for the interrupt level, small abrupt updates for "make aware". They say "much work remains to
  evaluate exactly how subtle or abrupt" changes must be. No numeric thresholds.
- **Pielot, Church & de Oliveira 2014**, MobileHCI [abstract and results]. 15 users, one week, 63.5
  notifications a day, viewed within minutes in all modes; a rising count correlated with more
  negative emotion, while more messages also felt more connected. Attention cost scales with
  count and with how the badge demands a reaction.
- **Reading across.** **[inference]** At most 2-3 status readings in the dock at once; each is a
  shape or a number of 1-3 characters, steady, with a slow cross-fade of about 300-600 ms for
  routine change (inside the "gradual" band; no paper gives milliseconds) and a one-off abrupt change
  only for an event the user must act on. Treat a count above a few as a cost, not a feature.
  Reading 5-7 s per glance means a status must be understood in about 1-2 s: no animated tickers.

## 4. Glass: legibility, depth, motion

Perception of glass in motion (refraction, highlight congruence, Fresnel, frost) is in GPR with
15 papers; I do not repeat it. This section adds the legibility side and turns GPR into dock numbers.

### 4.1 Legibility over a see-through plate

- **Harrison, Ishii, Vicente & Buxton 1995**, CHI [full text]. Stroop test with a word through a
  coloured layer (0 % = opaque layer, 100 % = clear). At 5 % the word was barely visible and about
  15 % of trials were unreadable; above 10 % "subjects made virtually no errors". For divided
  attention "substantial performance gains occur within the first 20-25 % transparency"; "levels of
  5 % or less do not seem usable"; for focused attention there is "rapid performance degradation
  between 5 % and 50 % transparency", worst at 50 %. Colour saturation and luminance interacted with
  legibility.
- **Harrison & Vicente 1996**, CHI [full text]. Text menus over text pages, wire frames and solid
  images, regular vs "Anti-Interference" font (a luminance-contrasting outline). "No significant
  performance degradation between 0 % (opaque) and 50 % transparent. Levels over 75 % are error
  prone." 18 % of trials were illegible overall, mostly at 90 %, where 55 % were. The outline font
  made the curve flatter: "AI fonts at 75 % and 90 % produce results similar to those of using
  regular fonts at 50 %." Pictures behind wire frames were the worst backgrounds; text pages the best.
  Icon and text items performed alike.
- **Baudisch & Gutwin 2004** (CHI) via GPR: blur under the palette (1 px Gaussian in their study)
  beat alpha blending because "all high contrast content is clearly on the palette, while all low
  contrast content is in the photograph behind it". **[prior]**
- **Gabbard, Swan & Mix 2006**, *Presence* 15(1):16-32 [abstract]. Text drawing style, background
  texture and illuminance all had significant effects on text identification in outdoor AR. Full text
  not read; it would give the size of the style effect.
- **Contrast floors (primary docs).** Android: 4.5:1 for text under 18 sp (or bold under 14 sp), 3:1
  otherwise (Android accessibility guide, fetched 2026-10-20). WCAG 2.2: text 4.5:1 (SC 1.4.3),
  non-text UI components 3:1 (SC 1.4.11).
- **Reading across.** The studies measure "how much of the background shows" and do not use blur,
  so mapping them to our frost is **[inference]**. The consistent direction: keep the background's
  share below about half, and give labels a contrasting edge or shadow when it goes above that.
  - Dock label and badge text: 4.5:1 measured against the *worst* wallpaper sample under the label
    (the brightest or darkest 10 % of the pixels after blur), not the mean.
  - Icons and glyph-only controls: 3:1.
  - The frosted face keeps its blur under content (GPR 2.2, Baudisch). Sharp refraction detail stays
    in the rim band.
  - Add a 1-1.5 dp luminance-contrasting edge or a soft shadow to text; Harrison & Vicente show this
    is worth about one 25 % step of transparency.

### 4.2 Reflection of icons, speculars, rim, motion (numbers for the AGSL dock)

No study tests a reflection of UI content in a glass UI surface. Everything here is derived
from GPR **[prior]** and marked **[inference]** where it extends it.

- Face reflection of the icons: Fresnel reflectance of n = 1.5 glass is 0.04 from 0 to about 30
  degrees of incidence (GPR 1.6, **[computed]**); Ohara et al. 2022 found thickness matched at a
  specular ratio of about 0.049 (via GPR). So the faint mirror image of the icons on the dock's face
  should sit at 4-5 % opacity, and only gently blurred (a reflection of a flat object is not
  frosted). **[inference]**
- The same reflection grows toward the rim, where the normal is steep: 0.09 at 60 degrees,
  0.17 at 70, 0.39 at 80 (GPR 1.6). Rim peak 10-40 % is physically justified. **[inference]**
- The reflected icon image must move only with the pose: highlights displaced from where the shading
  puts them lose gloss continuously, with no safe window (Kim, Marlow & Anderson 2011 via GPR 1.4).
  A reflection that rises with the tilt at 2x the angle (the law of reflection, GPR 1.6) is congruent;
  one animated on a timer is "painted on" (Doerschner et al. 2011 via GPR). Reflections of the
  dock's own icons must use the icons' *current* positions, including during a drag or press.
- A sharp, narrow highlight over a blurred face is what makes the face read as glass rather than
  frost (Motoyoshi 2010 via GPR 1.4). Specular lobe exponent 16-24 and rim width near 1.4 dp are
  the values GPR proposes. **[prior]**
- Keep the outline rigid and crisp: non-rigid outline deformation above about 0.54 degrees (18 dp)
  or blur from about 6 dp SD pushes the percept toward liquid (Kawabe 2017 via GPR). So the dock's
  silhouette stays a clean rounded rectangle even when the interior refracts.
- Refraction magnitude seen to matter in motion: deformations of about +-8 px (about 8 dp) over a
  structured background (Kawabe, Maruya & Nishida 2015 via GPR). On a dock over a blurred
  wallpaper, do not expect a static bend of 2-4 dp to be seen. **[prior]**
- Fleming 2011's distortion field is the *divergence* of the displacement, so a uniform shift of
  the face adds nothing (GPR 1.1). A 24-28 dp rim band with a monotone bend and no fold is the
  budget. **[prior]**
- Motion: lag, tilt and press depth rather than idle shimmer; nothing driven by time alone (GPR
  "Avoid"). Reflection of icons plus rim highlight both rotate by 2 x tilt; at 3-18 degrees of
  tilt that is 6-36 degrees of light rotation. **[prior]**
- Reduced transparency: Apple ships a "Reduce Transparency" setting, and LGD records the
  accessibility fall-backs. **[prior]** Provide an opaque-tinted dock option for the worst
  wallpapers.

## 5. Adaptive vs expert/novice interfaces

- **Findlater & McGrenere 2004**, CHI [full text]. 27 subjects, static vs adaptive vs adaptable split
  menus: the static menu was faster than the adaptive one (about 20 % faster in the discussion); the
  adaptable menu was faster than the adaptive in some orders; 55 % preferred adaptable, 30 % adaptive,
  15 % static (the related prior study: 65 / 15 / 20). Adaptable was slower when it was the first
  condition tried, because customising costs time.
- **Findlater & McGrenere 2008**, CHI [abstract and introduction]. 36 subjects, small vs desktop
  screens, 50 % and 78 % accurate adaptive menus: high-accuracy adaptivity helps *more* on small
  screens, but it reduces awareness of the full set of items, which hurts learning; a low-accuracy
  menu performs poorly on any screen.
- **Böhmer & Krüger 2013** [full text, Discussion p. 2144]: "we do not suggest auto-sorting icons in
  adaptive launchers ... this would break the mental models users build of their menus"; adapt "at
  the higher granularity level of pages and folders". People already assign contexts to folders.
- **Appert & Zhai 2009** and **Bragdon 2011** (sections 1.4, 2) cover the expert end: an edge gesture
  or stroke is the fast route, the visible control the learnable one.
- **Cockburn, Gutwin, Scarr & Malacria 2016**, *ACM Computing Surveys* 47(2) (novice to expert
  transitions) is the survey I wanted; the PDF download timed out and I did not read it. Listed in the
  manual fetch list.
- **Reading across.** **[inference]** Dock slots stay where the user put them. Defaults: a familiar
  4-5 icon row that works with zero setup (novice), and optional expert layers that add speed without
  removing the visible path: the edge gesture, a long-press to reorder or resize, and a "suggested"
  slot. If one slot adapts, make it one clearly marked slot (adaptive accuracy under about 60 % is a
  net loss per Findlater 2008), never a reshuffle of the others.

## Rules for the mockups

1. **Tile 56-60 dp, hit area at least 48 dp, no overlapping hit areas.** 58-60 dp is the thumb
   result of Parhi 2006 (9.2-9.6 mm) and 63 dp the best on all measures of Park & Han 2010; 48 dp is
   Android's floor and WCAG 2.5.8 sets 24 CSS px.
2. **Dock at the bottom; the three most-used items in the middle of the row, precise controls away
   from the holding-hand side.** Centre fastest and most preferred, lower-right worst (Park & Han
   2010; Parhi 2006), and Android users already launch from the lower screen (Böhmer & Krüger 2013).
3. **Four items by default, five at 392 dp or wider; 8-12 dp between tiles.** Counts and spacing are
   **[inference]** from the 4-column iPhone grid in Böhmer & Krüger 2013 and the 12.6 dp gap in the
   Android keypad of Bragdon 2011; no study measured dock size.
4. **Edge or swipe-up entries get a visible cue, always on at first.** Strokes are not self-revealing
   (Appert & Zhai 2009: recall 3x with strokes, but "interfaces should offer visual clues"). Cue: a
   dash about 4 dp x 28 dp with a 48 dp hit area; it may shrink after the gesture has been used.
5. **Edge gestures earn their place: about 12 % faster and unaffected by walking or distraction**
   (1,093 vs 1,244 ms; Bragdon et al. 2011). Start the palette gesture at the screen edge or the
   dock's top edge, not mid-screen.
6. **Status: at most 2-3 readings, shapes or 1-3 characters, understood in 1-2 s.** Glances last about
   5-7 s (median 7 s, Gouveia et al. 2016); abstract forms beat raw data (same).
7. **Routine status changes cross-fade slowly (about 300-600 ms, no flashing); abrupt change only
   for items that need action.** Direction from Matthews et al. 2004 (gradual for change-blind,
   abrupt for interrupt); the millisecond figures are **[inference]**.
8. **Glass face: keep the wallpaper's share under the labels at or below about 50 %; add a
   1-1.5 dp contrasting edge or soft shadow if it goes up to 75 %; never above 75 %.** Harrison &
   Vicente 1996: no loss up to 50 %, error-prone past 75 %, 55 % illegible at 90 %, outline worth about
   one step.
9. **Contrast floors: 4.5:1 for labels (measured against the worst 10 % of the blurred pixels), 3:1
   for icon glyphs and the dock's edge against the wallpaper.** Android accessibility guide; WCAG 1.4.3
   and 1.4.11.
10. **Icon reflection on the face at 4-5 % opacity, rising to 10-40 % at the rim's bevel, drawn from the
    icons' live positions and shifted by 2 x tilt.** Fresnel n = 1.5: 0.04 flat, 0.09-0.39 at 60-80
    degrees; Ohara 2022 about 0.049 (via GPR 1.6). Any highlight displaced against the shading loses
    gloss (Kim et al. 2011 via GPR). Opacity values are **[inference]** from those anchors.
11. **Silhouette rigid and crisp (no outline deformation above about 18 dp, no outline blur above about
    6 dp SD); interior refraction only in a 24-28 dp rim band, monotone, no fold; no idle shimmer.**
    Kawabe 2017, Schlüter & Faul 2019, Kim et al. 2011 (all via GPR). The band width is **[inference]**.
12. **Never auto-sort the dock; one clearly marked adaptive slot at most, only if it is right more than
    about 60 % of the time.** Adaptable 55 %, adaptive 30 %, static 15 % preferred (Findlater &
    McGrenere 2004); low accuracy hurts and high accuracy lowers awareness (Findlater & McGrenere
    2008); do not auto-sort icons (Böhmer & Krüger 2013). The 60 % cut is **[inference]** between their
    50 % and 78 % conditions.

## References

Local PDFs in `/home/amalvajdan/.cache/dock-research/sources/` (outside the repo, never committed).

1. Parhi, Karlson & Bederson 2006. Target size study for one-handed thumb use on small touchscreen
   devices. MobileHCI '06. DOI 10.1145/1152215.1152260. `parhi2006-targetsize.pdf`. Full text.
2. Park & Han 2010. Touch key design for one-handed thumb interaction with a mobile phone. *Int. J.
   Ind. Ergon.* 40(1):68-76. DOI 10.1016/j.ergon.2009.08.002. No PDF saved; full text read as
   publisher HTML through the proxy.
3. Bergstrom-Lehtovirta & Oulasvirta 2014. Modeling the functional area of the thumb on mobile
   touchscreen surfaces. CHI '14. DOI 10.1145/2556288.2557354. `bergstrom2014-thumbarea.pdf`. Abstract,
   Discussion and the model section read; the derivation not checked.
4. Le, Mayer, Bader & Henze 2018. Fingers' range and comfortable area for one-handed smartphone
   interaction beyond the touchscreen. CHI '18. DOI 10.1145/3173574.3173605. `le2018-fingers.pdf`.
   Abstract and design implications read; per-phone figures not extracted.
5. Böhmer & Krüger 2013. A study on icon arrangement by smartphone users. CHI '13. DOI
   10.1145/2470654.2481294. `bohmer2013-icons.pdf`. Full text, key sections.
6. Bragdon, Nelson, Li & Hinckley 2011. Experimental analysis of touch-screen gesture designs in
   mobile environments. CHI '11. DOI 10.1145/1978942.1979000. `bragdon2011-gestures.pdf`. Full text, key
   sections.
7. Appert & Zhai 2009. Using strokes as command shortcuts: cognitive benefits and toolkit support. CHI
   '09. DOI 10.1145/1518701.1519052. `appert2009-strokes.pdf`. Full text, key sections.
8. Matthews, Dey, Mankoff, Carter & Rattenbury 2004. A toolkit for managing user attention in
   peripheral displays. UIST '04. DOI 10.1145/1029632.1029676. `matthews2004-peripheral.pdf`. Full text,
   key sections.
9. Gouveia, Pereira, Karapanos, Munson & Hassenzahl 2016. Exploring the design space of glanceable
   feedback for physical activity trackers. UbiComp '16. DOI 10.1145/2971648.2971754.
   `gouveia2016-glanceable.pdf`. Full text, key sections.
10. Pielot, Church & de Oliveira 2014. An in-situ study of mobile phone notifications. MobileHCI '14.
    DOI 10.1145/2628363.2628364. `pielot2014-notifications.pdf`. Abstract and results.
11. Harrison, Ishii, Vicente & Buxton 1995. Transparent layered user interfaces. CHI '95. DOI
    10.1145/223904.223945. `harrison1995-layered.pdf`. Full text.
12. Harrison & Vicente 1996. An experimental evaluation of transparent menu usage. CHI '96. DOI
    10.1145/238386.238583. `harrison1996-menus.pdf`. Full text, key sections.
13. Findlater & McGrenere 2004. A comparison of static, adaptive, and adaptable menus. CHI '04. DOI
    10.1145/985692.985704. `findlater2004-menus.pdf`. Full text, key sections.
14. Findlater & McGrenere 2008. Impact of screen size on performance, awareness, and user
    satisfaction with adaptive graphical user interfaces. CHI '08. DOI 10.1145/1357054.1357249.
    `findlater2008-screensize.pdf`. Abstract and introduction only.
15. Gabbard, Swan & Mix 2006. The effects of text drawing styles, background textures, and natural
    lighting on text legibility in outdoor augmented reality. *Presence* 15(1):16-32. DOI
    10.1162/pres.2006.15.1.16. Abstract only (Crossref); publisher not proxied.
16. Perception papers cited through GPR (Kim et al. 2011, Kawabe 2015 and 2017, Ohara 2022, Motoyoshi
    2010, Doerschner 2011, Schlüter & Faul 2019, Baudisch & Gutwin 2004): see that note for DOIs and
    reading status. Not re-read here.

Primary docs: Android accessibility guide (above); WCAG 2.2 Understanding SC 2.5.8 (above), SC 1.4.3 and
1.4.11 via the WCAG 2.2 recommendation, w3.org/TR/WCAG22 (fetched 2026-10-20). I did not verify Material
3 navigation-bar guidance (the page returned no content), so no number from it is used.

## Search log

Databases: Scopus (library proxy), Crossref for DOIs. Web of Science was not searched in this pass;
the coverage gap this leaves is mostly non-HCI ergonomics journals. Fields covered: HCI (CHI, UIST,
MobileHCI, UbiComp), ergonomics and biomechanics of thumb reach, attention and peripheral displays,
text legibility and transparency (HCI and AR), adaptive interfaces. Not covered beyond GPR: vision
science of glass (see GPR), graphics.

All searched 2026-10-20, sorted by citations:

| Topic | Query (abridged) | Hits |
|---|---|---|
| Thumb and target size | TITLE(thumb OR "one-handed" OR "target size" OR "touch target") AND TITLE(smartphone OR mobile OR touchscreen OR phone) AND PUBYEAR > 2008 AND SRCTITLE(CHI, MobileHCI, UIST, Ergonomics ...) | 49 |
| Same, no venue filter | the first two clauses only | 138 |
| Target acquisition | TITLE("target spacing" OR "icon spacing" OR "target size" OR "touch target*" OR "finger touch" OR "key size" ...) AND TITLE(touch* OR mobile OR smartphone OR finger* OR phone) AND PUBYEAR > 2004 | 126 |
| Gestures and discoverability | TITLE(discoverability OR "novice to expert" OR "command shortcut*" OR "marking menu*" OR "gesture*") AND TITLE(touch OR mobile OR smartphone OR "command*") AND PUBYEAR > 2005 AND SRCTITLE(HCI venues) | 142 |
| Launcher and app use | TITLE("mobile search" OR "app launcher" OR "app usage" OR "home screen" OR "launcher" ...) AND TITLE(mobile ...) AND SRCTITLE(HCI venues) | 23 |
| Launcher in abstract | TITLE-ABS("home screen" OR launcher) AND TITLE-ABS(smartphone OR android OR iphone) AND SRCTITLE(HCI venues) | 9 |
| Glanceable and ambient | TITLE(glanceab* OR "peripheral display*" OR "ambient display*" OR "micro-interaction*" OR "notification*") AND TITLE(mobile OR smartphone OR wearable ...) AND SRCTITLE(HCI venues) | 100 |
| Glance in title | TITLE(glanceab* OR "at a glance" OR "glance" OR "glances") AND PUBYEAR > 2008 AND SRCTITLE(HCI venues) | 17 |
| Transparency in UI | TITLE(transparen* OR translucen* OR blur* OR "semi-transparent" OR "frosted") AND TITLE(legib* OR readab* OR "text" OR interface* OR "widget*" OR "menu*") AND PUBYEAR > 1993 AND SRCTITLE(CHI, UIST, TOCHI, Human Factors, Displays ...) | 24 |
| Text on textured background | TITLE("text legibility" OR "legibility of text" OR "contrast" OR "background texture" ...) AND ... SUBJAREA(COMP OR PSYC) | 29 (mostly Uyghur text detection noise) |
| Adaptive and expert | TITLE("adaptive" OR "adaptable" OR "novice" OR "expert" OR "customiz*" OR "personaliz*") AND TITLE(menu* OR interface* OR "home screen*" OR launcher* OR shortcut* OR "icon*") AND PUBYEAR > 2002 AND SRCTITLE(HCI venues) | 117 |

Screening notes: dropped Xiong & Muraki 2014 and 2016 (thumb coverage by age and screen size;
overlapping with Le and Bergstrom-Lehtovirta, would add per-screen-size numbers), Trudeau et al.
(biomechanics of thumb and wrist posture; motor detail beyond dock design), Rico & Brewster 2010
(social acceptability of gestures; relevant to public use, not read), Gajos et al. 2008 (predictability
and accuracy in adaptive UIs; would sharpen rule 12), Lavie & Meyer 2010 (benefits and costs of adaptive
UIs, IJHCS; behind ScienceDirect, not fetched), Kushlev 2016 (notifications and inattention; about
interruption cost, not dock status).

## Gaps

- No study of dock size (count, spacing between tiles) on a phone; the counts above are geometry.
- No study of edge vs bottom dock placement.
- No controlled study of command palettes or swipe-up search on phones; no read paper on universal
  search in launchers.
- No study of reflections of UI content in a glass UI surface, or of how reflection opacity changes
  perceived depth of an interface. Only the general gloss and Fresnel papers in GPR.
- Legibility studies (Harrison) use colour layers and 1990s displays, not blur, and measure the
  background's share of transparency, so my mapping to frost is inference.
- Millisecond figures for ambient transitions: none published (Matthews says so).
- Thumb studies are on small 2006-2010 screens (Parhi 8.9 cm) or small samples (20-30); Le 2018 and
  Bergstrom-Lehtovirta 2014 cover larger screens but I extracted no per-phone numbers.

## Manual fetch list

1. Cockburn, Gutwin, Scarr & Malacria 2016, Supporting novice to expert transitions in user
   interfaces, ACM Computing Surveys 47(2) · DOI 10.1145/2659796 · would give the design rules for
   expert layers that the rule 4-5 and 12 guess at.
2. Gabbard, Swan & Mix 2006 (above) · DOI 10.1162/pres.2006.15.1.16 · would give the size of the
   text-style effect over textured backgrounds, which could change rule 8.
3. Lavie & Meyer 2010, Benefits and costs of adaptive user interfaces, IJHCS 68(8):508-524 · DOI
   not verified (look up by title) · could change the 60 % accuracy line in rule 12.
