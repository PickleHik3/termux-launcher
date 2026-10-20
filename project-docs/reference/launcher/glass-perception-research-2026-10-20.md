# Glass perception in motion: which cues make the pane read as glass (2026-10-20)

Question from the user, after tilt, lag and dent landed in `GlassRefraction.java`: "the animation is
better but I have to look for it". Does shifting or bending the edges give a better perception of
glass, or do other cues matter more? Read-only research, no code changed.

Method. Every perception claim is from the paper's own abstract or full text. On 2026-10-20 the
user supplied full-text PDFs of 15 papers; the entries marked **[full text]** were read from them,
with page or figure numbers given. The rest come from PubMed abstracts (via NCBI E-utilities) or
from PubMed Central full text where it is open. Where I only had another paper's
summary of a study, it is marked **[second-hand]**. Arithmetic I did myself (Fresnel values, slab
shift, visual angle) is marked **[computed]**; design conclusions drawn from the sources are marked
**[inference]**. Apple and library material builds on the two earlier notes, which hold the full
quotes:

- `termux-launcher/docs/research/liquid-glass-design.md` (other checkout, commit 7f75ec5e4), called
  **LGD** below.
- `project-docs/reference/launcher/glass-refraction-references-2026-10-15.md` (this checkout), called
  **GRR** below. It pins Haze@c2bdd15 and Kyant@65ab177 source lines.

Visual-angle conversions assume the phone at 30 cm: 1 degree is about 33 dp and 1 arcmin about
0.55 dp (1 dp = 0.159 mm) **[computed]**.

## Answer in short

1. **Edge bending is necessary but is not what makes motion read as glass.** The rim is the only
   place where the picture's displacement changes from point to point, and that change is the
   cue. Fleming et al. 2011 define the "distortion field" as the divergence of the displacement
   (p. 814), so uniform movement of the picture carries none. But studies of
   refraction alone find it weak when it is static, and it gets weaker over a blurred or low-detail
   background, which is all our face shows.
2. **A whole-face shift is close to useless as a glass cue.** A uniform translation leaves every
   background element the same size and shape. That is the opposite of the distortion field, and it
   moves rigidly with the pane: "as if they were painted on". This is the "snapshot being hovered"
   the user saw. A small shift still has a use: a picture moving against the pane is a glass
   signature (Tamura et al. 2018). Keep it, but no larger than the pane's own motion.
3. **Specular reflection is the strongest single cue once it is clearly visible**, and in motion
   what matters is that the highlight moves *relative to the surface*, as the reflection of a light
   fixed in the world does. Our edge light has a fixed light direction and a very wide lobe, so a
   tilt only reweights it per edge. It never travels.
4. Highlights have to be congruent with the geometry: on the rim and moving only with the pane's
   pose. Otherwise they read as a lighting change or as paint. That is what the rejected "shimmer"
   sweep was.

Ranked recommendations are in the last section.

## 1. What the perception research says

### 1.1 Refraction: the distortion field, and its limits

- **Fleming, Jäkel & Maloney 2011**, "Visual perception of thick transparent materials",
  *Psychological Science* 22(6):812-820, DOI
  [10.1177/0956797611408734](https://doi.org/10.1177/0956797611408734), PMID 21597102. Read in full
  from a copy the user supplied. Page numbers are the journal's.
  - *Definition (p. 814).* The displacement field is `D(x,y) = p_r - p_i`, the shift of every
    background feature seen through the object. The visual system cannot measure D directly, since
    it does not know the unrefracted positions. It "may be able to estimate the relative compression
    and expansion of a texture pattern compared with its local context", which is the
    **distortion field** `d = ∇·D`, the divergence of the displacement. Positive values mean local
    magnification, negative values compression.
  - *What scales it (pp. 815, 817).* As the refractive index rises, "the pattern of compressions
    and rarefactions remains similar, but the magnitude of the distortions increases". Their model
    pools the local magnitudes with an arithmetic mean over the object's image region (p. 818). A
    thicker object or a more distant background gives more distortion. Observers misjudged
    refractive index accordingly: thin objects and a near back plane were judged *less* refractive,
    thick objects and a far back plane *more* (Exps 2-3, Fig. 3b-c). That is, they did not
    discount the scene.
  - *Shape of the response (p. 815, Fig. 3a).* The perceptual scale against refractive index is
    "pronounced[ly] positively bowed": "a small change in refractive index at a low refractive
    index had a larger effect on material appearance than a large change did". The
    distortion-field prediction has the same bow. Higher refractive index makes the object look
    "more lustrous and substantial" (p. 813).
  - *Conditions (pp. 814, 818).* Refraction "can prevent X junctions from occurring at the
    boundaries", yet the impression stays vivid. The cue is offered for the case "when a textured
    background is visible through a refractive object", and "when distorted refracted patterns are
    salient—as in our stimuli". The stimuli were a pebble-shaped blob in front of a high-contrast
    Voronoi-tiled box (Fig. 1b, 2).
  - *Other cues (p. 818).* The distortion field "is clearly not the only cue". More refractive
    objects also look glossier (Fresnel), so gloss cues may be used. Average contrast and luminance
    were poor predictors.
  - *Limits.* The stimuli were static, monocular 320 × 320 px renders lit by two lights, with six
    observers who differed a lot from one another. The paper gives no motion data and no
    displacement magnitudes in degrees or pixels. Its only magnitudes are scene parameters:
    refractive index 1.1-2.3, thickness ×0.5-1.5, back plane 2.5-17.5 cm. The authors speculate
    that very extreme compression may count *less* in the pooled estimate, because it gives
    unreliable local scale estimates (p. 818). That is a hypothesis, not a tested result.
- **Schlüter & Faul 2014**, "Are optical distortions used as a cue for material properties of thick
  transparent objects?", *JoV* 14(14):2, DOI 10.1167/14.14.2 **[full text]**.
  - Matches were "a compromise between a similarity match of background distortions (D match) and
    a similarity match of specular reflections (SR match)", and the weight "seems to partly depend
    on its salience".
  - "Specular reflections tend to be the dominant factor as soon as they become clearly
    noticeable. Background distortion dominated the settings only for low values of the
    refractive index of the standard object (and thus weak specular reflections)" (General
    discussion).
  - "The detectability of the distortion strongly depends on context factors like the
    characteristics of the background itself" (Exp. 1 discussion).
  - Caveat from the authors: the weights "differed considerably between subjects", which suggests
    the compromise is "mainly of a cognitive nature".
  - No crossover value in image units is given.
- **Schlüter & Faul 2016**, "Matching the material of transparent objects: the role of background
  distortions", *i-Perception*
  ([PMC5030754](https://pmc.ncbi.nlm.nih.gov/articles/PMC5030754/)). Subjects "matched the average
  element size of the distorted background across the scenes", and the matches moved with
  background texture density ("Even moderately different background densities in otherwise similar
  stimuli lead to significantly different refractive index settings"). Specular reflections again
  "influence the settings".
- **Schlüter & Faul 2019**, "Visual shape perception in the case of transparent objects", *JoV*
  19(4):24, DOI 10.1167/19.4.24 **[full text]**.
  - Theory ("Summary of the theoretical analyses"): "In simple situations there is often a direct
    correlation between optical magnifications and the local principal curvatures of a surface".
    But "if the refraction effects are so strong that light rays cross on their way to the
    observer, this correlation becomes increasingly ambiguous".
  - Experiment, gauge-figure task: transparent shapes were judged with errors "approximately twice
    as large" as opaque ones. "For massive transparent objects, inclusion of both specular
    reflections and absorption enhanced shape perception ... The effect of background distortions
    was very small for hollow transparent objects and negative for massive ones" (Discussion).
  - Some transparent objects "were misinterpreted as opaque objects".
- **Ohara, Kim & Koida 2022**, "The role of specular reflections and illumination in the perception
  of thickness in solid transparent objects", *Frontiers in Psychology*
  ([PMC8891632](https://pmc.ncbi.nlm.nih.gov/articles/PMC8891632/)). Abstract: "specular reflections
  increasing perceived thickness, and refractive distortions decreasing perceived thickness"; "the
  specular component can dominate in the perception of 3D shape". The thickness of an object without
  specular reflection matched at a specular ratio of about 0.049, against crown glass's ~4.2%
  normal reflectance.

**What this means for edges vs face.** Fleming's definition settles part of this outright. The
distortion field is the *divergence* of the displacement (p. 814), and a uniform translation has
zero divergence. So the whole-face tilt shift (`tiltOffset`'s `shift` term, about 4 dp at 3
degrees, 6.5 dp at 6, 10 dp at 18) contributes nothing to the cue the paper studies. It shows only
as a mismatch with the wallpaper outside the outline, and that mismatch sits at the rim again.
**[computed from the definition]**

On our pane, divergence exists only where the displacement varies: the rim band, the tilt's
magnification gradient and the dent. **[inference]** Two consequences follow from the paper's
pooling (the mean over the object's region, p. 818):

- A thin rim band of strong compression covers little of the pane's area, so it contributes
  little to the pooled magnitude. The authors also suspect extreme compression is down-weighted.
- A gentle gradient across the whole face raises the pooled estimate more.

The face's only distortion today is the magnification term. It is ±0.8-4% by `TILT_MAG`, but
`TILT_MAG_MAX_DP` = 4 dp caps it, so on a large pane it moves the picture at most about 4 dp at
the face's ends. The bowed scale (Fig. 3a) suggests the first small amount of distortion buys the
most perceived "glassiness", with diminishing returns after that. **[inference: the paper
measures this against refractive index, not against distortion in the image]**

Two limits follow from Schlüter & Faul.

- *Distortion is the weaker partner.* It wins only while specular reflection is weak (2014), and
  for massive objects it did not help shape perception, while specular reflection and absorption
  did (2019). So the distortion field is worth having, but it should not be the main motion
  cue.
- *There is a ceiling: do not fold.* Magnification reads as curvature only while rays do not
  cross (2019). Past that point the picture folds back on itself and the reading becomes
  ambiguous. This argues against the "fold" knob GRR suggested (Haze's
  `refractionFoldStrength`), and for keeping the displacement monotone, which our
  `tiltOffset` ease already does. **[inference]**

### 1.2 Refraction in motion: dynamic deformation

- **Kawabe, Maruya & Nishida 2015**, "Perceptual transparency from image deformation", *PNAS*
  112(33):E4620-7, DOI 10.1073/pnas.1500913112, PMID 26240313
  ([PMC4547276](https://pmc.ncbi.nlm.nih.gov/articles/PMC4547276)) **[full text]**. Abstract: "previous studies have
  indicated that these image deformations, at least static ones, play little role in perceptual
  transparency"; "dynamic image deformations of the background pattern ... can produce a vivid
  impression of a transparent liquid layer without the aid of any other visual cues". The full text
  says a static deformation image "cannot provide a clear impression of transparent liquid". The
  deformation that worked was "spatially low-frequency dominant while temporally broadband", with
  the strongest temporal band at "3-10 Hz", and the magnitudes were normalised to about ±8 px
  (≈ ±0.24 degrees, about 8 dp at 30 cm **[computed]**). Natural-image backgrounds rated higher than
  1/f noise, which the authors credit to "edge continuity and element shape". Blurred backgrounds
  were not tested. Caveat: the stimuli are liquids (index of refraction 1.33) deforming
  continuously, not a rigid slab. For a static index-1.5 layer over a stained-glass pattern the
  paper says observers found it "difficult" to see the layer without specular components
  (qualitative only).
- **Kawabe & Kogovšek 2017**, "Image deformation as a cue to material category judgment",
  *Sci Rep* 7:44274 ([PMC5343573](https://pmc.ncbi.nlm.nih.gov/articles/PMC5343573/)). "The larger
  and smaller image deformation magnitudes respectively contributed to the water and hot air
  impressions." Weak deformation reads as a *shallower* (thinner) layer, and "dynamic image
  deformation is necessary for human observers to have the impression of transparent materials".
- **Kawabe 2017**, "What property of the contour of a deforming region biases percepts toward
  liquid?", *Frontiers in Psychology*
  ([PMC5471326](https://pmc.ncbi.nlm.nih.gov/articles/PMC5471326/)). The study deformed the
  *outline* of the refracting region. Above 0.54 degrees (about 18 dp) of non-rigid outline
  deformation, the percept was "biased toward liquid"; sensitivity was zero at 0.18 degrees and
  significant from 0.36 degrees. Blurring the outline (Gaussian SD from 0.18 degrees, about 6 dp)
  also pushed it toward liquid. The author notes, informally, that "the intact cross and star
  produced a highly rigid and solid-like material impression". A rigid outline is what a solid
  keeps ("a transparent glass marble sliding on a tabletop preserves its shape"). DOI
  10.3389/fpsyg.2017.01014.
- **Kawabe & Nishida 2018**, "Deformation-induced transparency resolves color scission", *JoV*
  18(8):3, DOI 10.1167/18.8.3 **[full text]**.
  - Deformation-defined transparency is a real surface representation. It biased which layer took
    the colour "if and only if the deformation-induced transparency was spatially coincident with
    the interpretation of color transparency" (Abstract). The "coincidence of edges between
    dynamic image deformations and chromaticity is an important factor" (General discussion).
  - Citing their own 2017 study **[second-hand]**: deformation-induced transparency "was enhanced
    when the transparency layer was additionally supported by luminance-defined x-junctions at the
    surface's outer boundary, whereas it was suppressed when the luminance changes across the
    surface's outer boundary were inconsistent with luminance transparency" (Introduction).
  - Stimulus scale: deformation maps low-passed at 4.16 cycles/degree, drifting at 3.6 degrees/s.
- **Falkenberg & Faul 2019**, "Transparent layer constancy is improved by motion, stereo
  disparity, highly regular background pattern, and successive presentation", *JoV* 19(12):16,
  DOI 10.1167/19.12.16 **[full text]**.
  - Thin colour filters, not refractors. "Motion, stereo disparity, and a highly regular
    background pattern each contribute to the vividness of the transparency impression", and
    "combining these cues led to a cumulative increase", which the authors call "strong fusion"
    (Abstract).
  - Moving filters travelled "with a constant speed (4°/s) over a range of 112 mm (12.78°)"
    (Methods).
  - Why motion helps: "By continuously covering and uncovering background regions, new
    X-junctions are created while good continuation of the background pattern and figural unity of
    the transparent region are preserved".
  - Match quality fell with lower texture density.
  - Invalid figural conditions (T-junctions) pushed toward "opaque", the more so "the more
    conspicuous the violation".

**[inference]** For glass, as opposed to liquid, keep the outline rigid and crisp, and let the
picture *inside* it deform. A single tilt eases in over a few hundred ms and then stops, so it gives
one burst of deformation, not the sustained 3-10 Hz flow that made Kawabe's liquids vivid. So
refraction-in-motion has to be *visible* to count at all. Magnitude matters, and so does the
detail it acts on (1.5). Two more points follow:

- *Slides are the strong case.* Falkenberg & Faul show that motion of a layer across a structured
  background is a cue in its own right, and that it adds to the others. A slide (a pane moving over
  the wallpaper at a few degrees per second) is exactly that case. The tilt-in-place case is the
  weak one.
- *Tint and bend must share a boundary.* Kawabe & Nishida say the tint and the bend must coincide
  in space, and the luminance at the outline must be consistent with transparency. Ours share the
  pane's outline. But our edge treatments must preserve the background's contrast polarity across
  the outline. The multiplicative "gathered light" does. The bold fringe, which mixes toward
  pure warm and cool colours, may not.

### 1.3 Glass vs mirror, and highlights in motion

- **Tamura, Higashi & Nakauchi 2018**, "Dynamic visual cues for differentiating mirror and glass",
  *Sci Rep* 8:8403 ([PMC5976772](https://pmc.ncbi.nlm.nih.gov/articles/PMC5976772/)). "Glass objects
  have more opposite motion components relative to the direction of object rotation", which the
  authors attribute to the rear surface, a form of motion transparency. On mirrors, bright regions
  "predominantly moved in the object's rotational direction". Dynamic display beat static (F(1,9) =
  18.3). Their model predicts "glass objects may be misperceived as mirror surfaces if the glass
  generates little or no motion transparency". DOI 10.1038/s41598-018-26720-x.
- **Tamura & Nakauchi 2018**, "The rotating glass illusion: material appearance is bound to
  perceived shape and motion", *i-Perception* 9(6), DOI 10.1177/2041669518816716 **[full text]**.
  - A bumpy glass prism (refractive index 1.5, reflectance 0.04) rotating 30 degrees at 30
    degrees/s was judged to be a mirror, and its rotation direction reversed, at specific starting
    angles (30, 60, 150, 180, 270, 300 degrees).
  - That happened where "a convex edge of the triangular prism made of glass was facing toward the
    rear", and "the opposite motion components ... could be dominant".
  - The authors conclude that "the visual system more easily recognizes the object when it has a
    specular reflective surface and tends to ignore refractive media".
  - No angle ceiling for a flat slab is given. The rotations are 3D, of a faceted solid.
- **Doerschner, Fleming, Yilmaz, Schrater, Hartung & Kersten 2011**, "Visual motion and the
  perception of surface material", *Current Biology* 21(23):2010-6, DOI 10.1016/j.cub.2011.10.036
  ([PMC3246380](https://pmc.ncbi.nlm.nih.gov/articles/PMC3246380)) **[full text]**. Stimuli rotated
  "back and forth 15 degrees" about six axes (Experimental Procedures), which is the same scale as
  our 1-18 degree tilts. Specular features "move toward convexities (contractions)" and slow as they
  approach them. Contractions "are usually not generated by rotating matte, textured objects".
  "Motion measurements can override
  static cues, leading to dramatic changes in perceived material." Reflections "'rush' across low
  curvature regions and 'stick' to points of high curvature". When the reflected pattern was
  rigidly attached to the surface, "the patterns move with the surface, as if they were painted on
  instead of being reflections", and the object looked matte and patterned.

**[inference]** These two explain both rejected attempts and point at what is missing:

- The rim-only lag made the bend move *with* the pane while the face picture stayed put or moved
  rigidly. Everything visible moved as one sheet: Doerschner's "painted on" case, which the user
  called "a snapshot being hovered".
- Glass needs two motions at once: the surface's own features, meaning the outline and its
  highlights, and a seen-through picture that moves differently from it (Tamura's opposite
  component). Our screen-aimed sampling already keeps the wallpaper fixed while a pane slides,
  which is right. Under a tilt the face shift moves the picture toward the edge going away, which
  matches slab physics (1.6).
- Highlights must not stay at one place on the surface while the pose changes. They should
  "rush" across flat parts and "stick" at the high-curvature corners.
- That gives the face shift one job after all. Under a tilt, the seen-through picture moving
  *against* the surface's own motion is Tamura's glass signature. So the shift should shrink, not
  vanish, and it should never exceed the motion of the outline itself. Otherwise the picture, not
  the pane, reads as the thing moving. Tamura & Nakauchi's illusion also warns that once opposite
  motion dominates, glass can read as a mirror. That is a limit on how far the picture may outrun
  the pane. It is not an angle ceiling for our 18 degrees.

### 1.4 Highlights must be congruent with shape

- **Kim, Marlow & Anderson 2011**, "The perception of gloss depends on highlight congruence with
  surface shading", *JoV* 11(9):4, DOI 10.1167/11.9.4 **[full text]**: "displacing specular
  highlights from their natural locations in images reduces perceived surface gloss ... the visual
  system assesses both position and orientation congruence between specular highlights and diffuse
  shading".
  - Magnitudes (Exp. 1, Fig. 6). The diffuse lighting was rotated against the speculars. Perceived
    gloss "decreased continuously over the entire 0- to 180- rotational range" in two outdoor light
    fields, and "only decreased over a limited rotational range ... (0- to 15-, approximately)" in a
    third. There is no safe window: any misalignment costs some gloss.
  - Motion did not rescue it: "Surface motion had little influence on the changes in perceived
    gloss produced by displacing the specular highlights' positions relative to diffuse shading".
    Small offsets were "harder to identify in moving surfaces".
  - The paper also cites Hartung & Kersten 2002 **[second-hand]**: an object was glossy when the
    reflection moved over it, and "completely matte when the reflection rotated with the 3D
    surface-like pigment".
- **Marlow, Kim & Anderson 2011**, *JoV* 11(9):16, PMID 21873616: rotating or translating
  highlights away from the shading reduced gloss, "Rotations reduced perceived gloss more than
  translations".
- **Anderson & Kim 2009**, *JoV* 9(11):10, PMID 20053073: gloss "requires an analysis of the
  consistency between the estimate of a surface's 3D shape and the positions and orientations of
  highlights".
- **Adams & Elder 2014**, *PLoS Comput Biol*
  ([PMC4014396](https://pmc.ncbi.nlm.nih.gov/articles/PMC4014396/)): when misaligned, "the
  highlight is no longer perceived as a specularity" and "tends to appear more as a local
  illumination effect than a variation in albedo".
- **Motoyoshi 2010**, "Highlight-shading relationship as a cue for the perception of translucent
  and transparent materials", *JoV* 10(9):6, DOI 10.1167/10.9.6 **[full text]**.
  - A glossy object "tends to be perceived as translucent (or transparent) as the luminance
    pattern of the non-specular part in the image becomes blurred, faint, and reversed"
    (Discussion).
  - "Sharp highlights can specify that it is a translucent surface under directional
    illumination."
  - Highlights "keep their strong positive contrast at high spatial frequencies" while the body is
    blurred.
  - A demonstration (Fig. 7e): squaring the positive high-frequency contrast and multiplying the
    negative contrast by 0.1 made an opaque object look translucent.
- **Fleming & Bülthoff 2005**, "Low-level image cues in the perception of translucent materials",
  *ACM TAP* 2(3):346-382, DOI 10.1145/1077399.1077409 **[full text]**. On translucency
  (subsurface scattering), not clear glass:
  - X-junctions, "visibility of the background through the transparent region" and contrast
    comparisons with the surround are "not required" for translucency in solid objects
    (Conclusions).
  - "Translucent objects look most realistic when they are glossy ... the visual system 'expects'
    translucent objects to have specular highlights."
  - Observers judged an inconsistently illuminated image with highlights "more realistic than its
    consistent, but highlight-less, counterpart" (§ on specular highlights, Fig. 8).

**[inference]** A light band that sweeps over the pane on a timer, or that is not aligned with
the rim's curvature, fits Adams & Elder's "local illumination effect", which is the "shimmer". A
highlight reads as reflection only when it sits where the geometry would put it (the rim bevel,
the corners) and moves only when the pose moves.

Motoyoshi's result fits our case directly. A frosted face is the "blurred, faint" body, so what
turns frosted into *glass* is a highlight that stays sharp on top of it. That is a narrow,
high-contrast lobe, not a soft glow. In our pane nothing plays the part of "diffuse shading"
except the rim's own light, so Kim et al.'s congruence translates into one rule: every light term
moves together under one light direction (lobe, counter-lobe, edge shade). A highlight must never
move on its own. **[inference]**

### 1.5 Transparency, contrast and blur

- **Singh & Anderson 2002**, "Toward a perceptual theory of transparency", *Psychological Review*
  109:492-519 (author PDF, Rutgers): "the visual system uses Michelson contrast as a critical image
  variable to initiate percepts of transparency and to assign transmittance to transparent
  surfaces". Figural conditions: discontinuities in the underlying contour "destroy the percept of a
  transparent disk", and shifted halves weaken it. That is the thin-filter case. A thick refractor
  breaks contour continuity by its nature. Fleming 2011 notes that refraction "can prevent X
  junctions from occurring at the boundaries", yet the object still looks vividly transparent
  (p. 814), so thick glass is a different class from the filters these theories model.
- **Singh & Anderson 2002b**, "Perceptual assignment of opacity to translucent surfaces: the role
  of image blur", *Perception* 31:531-551 (abstract on the authors' Rutgers page, read as a
  summary): more blur in the transparent region lowered perceived transmittance even at constant
  Michelson contrast, so blur is a cue in its own right.
- **Levi & Klein 1990**, *Vision Research* 30(12):1995-2022, PMID 2288102: once stimulus blur
  exceeds the eye's intrinsic blur (≈0.5 arcmin in the fovea), resolution thresholds become "approx.
  equal to sigma", and spatial interval thresholds "rise when the stimulus blur exceeds about
  one-third of the separation".

**[inference]** Our face samples a pre-blurred wallpaper (Base blur 7 for Clear, 12 for Tint, 25
for Mist, `SurfacePresets.java:98-134`). Two consequences:

- (a) The face reads as frosted, which is a lower-transmittance material, not clear glass. Apple's
  regular glass does blur, so this is a style choice, not an error.
- (b) Moving a blurred picture changes each pixel by about displacement × local gradient, and
  blur flattens the gradient. A 4 dp shift (≈0.12 degrees) of features already blurred over many
  dp is near invisible. The code's own comment says as much about the 1.1-degree rig ("nobody sees
  through a blurred wallpaper", `GlassRefraction.java:1234-1235`).

Edge bending and its motion can only be seen where the picture under the rim keeps sharp detail.
That is the `sharp`/clarity path, which today is mixed only into the band.

### 1.6 Fresnel: where it applies on a tilting pane **[computed]**

Unpolarised Fresnel reflectance for n = 1.5 (Fresnel equations; Schlick gives nearly the same, see
the Filament note in GRR):

| Incidence | 0-18° | 30° | 45° | 60° | 70° | 75° | 80° | 85° |
|---|---|---|---|---|---|---|---|---|
| Reflectance | 0.040 | 0.042 | 0.050 | 0.089 | 0.171 | 0.253 | 0.388 | 0.613 |

On the flat face, a 1-18 degree tilt changes reflectance by essentially nothing (0.0400 to
0.0402). Fresnel only matters on the rim bevel, where the notional surface normal is already
steep. There a 10-degree tilt moves a 70-degree bevel to 80 degrees on one side (×2.3) and to 60
on the other (×0.5). So the per-edge brightness asymmetry under tilt (`TILT_LIGHT` ±40%) is
physically well founded, and could be larger on the steep part of the bevel. Brightening the face
under tilt would not be.

Physics of the face shift **[computed]**: a flat slab of thickness T tilted by θ displaces the view
by `T sin θ (1 - cos θ / sqrt(n² - sin² θ))`. That is 0.017 T at 3°, 0.059 T at 10° and 0.11 T at
18°. Our 10 dp at 18° corresponds to a slab of about 14 mm, a heavy plate. The direction (the
picture carried toward the edge going away) agrees with Snell's law. So the face shift is
physically right in direction, but by 1.1 it is a weak cue whatever its size.

The law of reflection: tilting a mirror by θ turns the reflected ray by 2θ. A highlight that
reflects a light fixed in the world therefore moves twice as fast as the tilt. That is the
cheapest motion cue available and we do not use it (section 3).

### 1.7 Other cues

- **Caustics.** Gigilashvili et al. found materials "appear less translucent under the conditions
  where caustics are absent" (2020), but in eye tracking "caustics might be a cue of only secondary
  importance" (2022). Both were seen only through NTNU repository and search summaries
  **[second-hand]** ([2685124](https://ntnuopen.ntnu.no/ntnu-xmlui/handle/11250/2685124),
  [3054268](https://ntnuopen.ntnu.no/ntnu-xmlui/handle/11250/3054268)). A floating UI pane casts no
  caustic onto anything we draw, so this does not apply.
- **Chromatic dispersion.** I found no psychophysics study of dispersion as a glass cue. Both
  reference libraries keep it proportional to the displacement and weighted to the corners (GRR,
  Haze `GlassShaders.kt:120-122`, Kyant `internal/Shaders.kt:120-121`), and Apple never mentions it
  (LGD). **[inference]** It is a lens-artefact signature, not a material one. It is fine as a quiet
  corner fringe, but as a motion cue it is unsupported. Since it is coloured light near the edge,
  it is also the cue most likely to read as an effect.
- **Thickness and internal reflection.** Specular reflection raises perceived thickness and
  refraction lowers it (Ohara 2022). Larger Apple glass is "thicker" with "more pronounced lensing",
  "deeper, richer shadows" and "softer scattering" (WWDC25 219, re-read 2026-10-20). Apple's 2027
  material added "a darkened edge along with brighter specular highlights" (WWDC26 112, quoted in
  GRR, not re-read). Mirror images from inside surfaces are one of Schlüter & Faul's three shape
  cues (2019).

## 2. Practitioner sources

- **Apple, WWDC25 "Meet Liquid Glass" (219)**, transcript re-read 2026-10-20: "The primary way
  Liquid Glass visually defines itself is through something called Lensing"; "the warping and
  bending of light of a transparent object communicates to us its presence, its motion, and form".
  On highlights: "Light sources inside of this environment shine on the material producing
  highlights that respond to geometry ... these lights move in space, causing light to travel around
  the material, defining its silhouette. And in some cases, the lighting responds to device motion".
  Interaction: "the material illuminates from within ... Starting right under your fingertips".
  Shadows rise over text and fall over light backgrounds.
  ([developer.apple.com/videos/play/wwdc2025/219](https://developer.apple.com/videos/play/wwdc2025/219/)).
  Apple publishes no formula, index of refraction, light direction or motion gain (LGD, "Gaps").
  Note Apple's own verb, "travel around the material": the highlight *moves along the silhouette*.
  It does not brighten in place.
- **Apple Newsroom**: "dynamically reacts to movement with specular highlights"; MacStories reports
  gyroscope and accelerometer use on the Home Screen **[second-hand, press]** (both in LGD §5).
- **kube.io, "Liquid Glass in the Browser: Refraction with CSS and SVG"** (2025-09-04,
  [kube.io/blog/liquid-glass-css-svg](https://kube.io/blog/liquid-glass-css-svg/)): true
  Snell-Descartes refraction (n = 1.5) through a convex bezel profile (circle or squircle) with a
  flat top. Only the bezel bends light, so the flat face is undisplaced. The highlight is "a simple
  rim light" whose intensity "varies based on the angle of the surface normal relative to a fixed
  light direction" (demo default -60 degrees, opacity 0.2-0.5). Its summary: "real-time refraction
  plus a simple highlight". That agrees with our rim-only refraction.
- **Haze glass and Kyant AndroidLiquidGlass** (GRR, pinned commits): the rim highlight is a
  directional lobe `pow(dot(normal, lightDir), 16..24)` (Haze) or `pow(abs(dot(n, dir)), falloff)`
  (Kyant), under a dark edge of 10-32% (Haze). Our lobe exponent is `kKeyExp = 2.0` with
  `kAmbient = 0.25` on every side (`GlassRefraction.java` AGSL, `gatherShare`), which is far wider.
  Neither library's source, as GRR read it, drives the light from device motion. I did not find an
  open-source implementation that does, so that is untraced.
- **Android.** A uniform costs nothing per pixel. A child `eval` is the cost unit ("complex shaders
  can be expensive to evaluate", AGSL quick reference), and no numeric sample budget is published
  (GRR "Android constraints"). AGSL is fixed at GLSL ES 1.0 with const folding
  ([agsl-vs-glsl](https://developer.android.com/develop/ui/views/graphics/agsl/agsl-vs-glsl), re-read
  2026-10-20).

### 2.1 Technique (graphics papers, full text)

- **Wyman 2005**, "An approximate image-space approach for interactive refraction", *ACM TOG*
  24(3) (SIGGRAPH), DOI 10.1145/1073204.1073310 **[full text]**.
  - Two-interface refraction in two GPU passes; earlier interactive methods "only refract through
    one surface" (Fig. 1).
  - Total internal reflection is handled by clamping to the critical angle, so rays "'refract'
    tangent to the exitant surface". That is "not physically accurate" but gives "plausible,
    relatively smooth results with one sample per pixel".
- **Oliveira & Brauwers 2007**, "Real-time refraction through deformable objects", *I3D 2007*, DOI
  10.1145/1230100.1230116 **[full text]**.
  - Image-space two-interface refraction with no preprocessing, so it works on deforming geometry.
  - Uses the same total-internal-reflection clamp, and "a Fresnel approximation to combine
    reflection and refraction" (Algorithm 2).
- **de Rousiers, Bousseau, Subr, Holzschuch & Ramamoorthi 2011**, "Real-time rough refraction",
  *I3D 2011*, DOI 10.1145/1944745.1944764 **[full text]**. Frosted and misted glass: "transparent
  material with rough surface shouldn't be confused with translucent material".
  - The rough-surface scattering (BTDF) is approximated by spherical Gaussians. Lighting is read
    from "the pre-convolved environment map as a MIP-map", one level per lobe width, *along the
    refracted direction*.
  - Their simplest baseline picks a "fixed mipmap level look up into the environment map based on
    depth traveled by the ray inside the object" (Fig. 11, left).

**[inference] for us.** Frosted glass in the physical model is still refraction. The picture is
bent first and then blurred, and the blur grows with the path length. Our shader already does the
first part: it samples the pre-blurred frame at the bent coordinate. The second part says the thin
bevel at the rim should be *less* blurred than the face, because light travels a shorter way
through glass there. So mixing the `sharp` frame into the band is physically motivated, not just an
effect. That is the cheapest way to keep the bend visible under frost. Two-interface and
total-internal-reflection methods matter for curved solids, not for our flat slab with a bevel.
Their shared practice of a Fresnel-weighted mix of reflection and refraction per pixel supports
letting reflection take over toward the rim's grazing edge (1.6).

### 2.2 Legibility guardrail

- **Baudisch & Gutwin 2004**, "Multiblending: displaying overlapping windows simultaneously
  without the drawbacks of alpha blending", *CHI 2004*, DOI 10.1145/985692.985739 **[full text]**.
  - Alpha blending "compromises the readability of content". "Noisy backgrounds interfere with
    multiblended palettes as much as they do with alpha-blended palettes ... making both palettes
    virtually disappear."
  - Their fix blurs the background under the palette so that "all high contrast content is clearly
    on the palette, while all low contrast content is in the photograph behind it". The result
    "seems to be made of a piece of frosted glass".
  - In the study (Gaussian blur of 1 px radius under the palette), the glass palette had
    significantly fewer background-recognition errors than any alpha palette. Palette recognition
    at alpha 10% failed badly: more than 1 error in 3 trials on light backgrounds, against about
    1 in 25 for the others.

**Guardrail [inference].** Every sharpening move above (recommendations 2 and 3) must stay out
from under the pane's content.

- Sharp detail and large distortion belong in the rim band and the dent, and only while the pane
  is moving.
- Under text and icons the face keeps its frost. That is what Baudisch shows keeps both layers
  legible.
- A gradient across the face moves frosted, low-contrast content, which costs legibility little.
  Sharp, high-contrast detail behind text is what costs it.

## 3. Recommendations, ranked

Ranked by expected gain in "I see it's glass without looking for it" for 1-18 degree tilts, press
dents and slides. The full-text pass on 2026-10-20 kept the order and sharpened the magnitudes
(see "What the full texts changed" below). A magnitude a paper supports is cited. Everything else
is marked **[inference]**.

1. **Make the edge light a sharp reflection that travels with the pose.**
   - *Light direction.* Rotate `kKeyLight` with the pose: the light stays fixed in the world, so in
     the pane's frame it turns by **2× the tilt** about the tilt axis (law of reflection, 1.6).
     That is 6 degrees at 3 degrees of tilt and 36 at 18. Doerschner's stimuli rocked ±15 degrees,
     the same scale, and their specular motion alone was enough to read as shiny.
   - *Lobe.* Narrow it from `kKeyExp` 2 to 16-24, Haze's range. That is an implementation
     reference, not a perception number. An exponent-16 lobe has a half-power half-width of about
     17 degrees **[computed]**, so 3 degrees of tilt moves it about a third of its width. Keep it
     thin in depth: `kGlowDp` 1.4 dp is already sharp.
   - *Why sharp.* Motoyoshi 2010 says sharp highlights over a blurred body are what reads as
     transparent or translucent. A soft glow over a frosted face reads as more frost.
   - *Strength.* Make it "clearly noticeable", the point at which specular dominates distortion
     (Schlüter & Faul 2014). Ohara 2022 found about a 5% specular ratio is where specular starts
     adding perceived thickness. I found no stronger number. At the steep bevel the Fresnel values
     (0.09-0.39, 1.6) justify a peak several times the face's 4%.
   - *No independent movement.* Kim et al. 2011 found gloss falls continuously with misalignment,
     with no safe window (0-180 degrees, or about 0-15 in one light field), and motion does not
     hide it. So the highlight must never be displaced on its own. Its position comes only from
     the rim normal and the rotated light. Rotate the lobe, the counter-lobe (item 4) and the edge
     shade together.
   - *Keep, drop.* Keep the ±40% per-edge Fresnel asymmetry (1.6). Nothing moves on a timer.
   - *Cost.* One uniform, no extra samples.
   - *Support.* Doerschner 2011; Schlüter & Faul 2014 and 2019 (specular helped shape for massive
     objects, distortion did not); Tamura & Nakauchi 2018 (vision "more easily recognizes" the
     specular reading); Fleming & Bülthoff 2005 (the visual system "expects" highlights); Apple 219
     ("light to travel around the material").
2. **Keep the bend visible under frost: a sharper rim while the pane moves.**
   - *What.* While `live` is true (lag, dent or tilt), raise the clarity of the rim band toward the
     `sharp` frame, and give the dent's zone some of it too. The face keeps its frost.
   - *Physical basis.* De Rousiers et al. model frosted glass as refraction looked up in a
     pre-blurred map, with blur growing with path length inside the glass. The thin bevel should
     therefore be clearer than the face (2.1).
   - *Perceptual basis.* Distortion detectability "strongly depends on ... the background itself"
     (Schlüter & Faul 2014). Fleming 2011 offers the cue "when a textured background is visible"
     (p. 818). Falkenberg & Faul 2019 found denser, more regular backgrounds and motion add up
     cumulatively.
   - *Magnitude.* The change in bend while moving should exceed the blur σ of whatever frame the
     band samples. Above the eye's intrinsic blur, spatial thresholds are about σ (Levi & Klein
     1990). **[inference in applying it to displacement]**
   - *Cost.* The band already reads `sharp` when clarity > 0. The dent adds one `eval` within its
     44 dp radius.
   - *Guardrail (2.2).* Never under the pane's content.
3. **Turn most of the uniform face shift into distortion, and never fold.**
   - *The gradient.* Raise `TILT_MAG_MAX_DP` so large panes get a real magnification gradient.
     That is the only thing on the face that has divergence, which is Fleming's cue (p. 814). It is
     pooled over the whole face, so it beats a thin rim (p. 818). Magnification reads as
     curvature (Schlüter & Faul 2019).
   - *Size.* No paper gives a magnitude in image units. **[inference]** Start where the gradient
     moves the face's ends by about 8 dp at 18 degrees (≈0.24 degrees, the scale of Kawabe 2015's
     deformations).
   - *Ceiling.* Keep the displacement monotone. Once rays cross the picture folds, and "this
     correlation becomes increasingly ambiguous" (Schlüter & Faul 2019). So do not add the fold
     knob GRR floated.
   - *The shift.* Shrink `TILT_SHIFT_DP` but keep some of it. A picture moving against the pane is
     Tamura's glass signature (1.3). But too much of it lets the picture, not the pane, read as
     what moves, and Tamura & Nakauchi show that dominant opposite motion can flip glass to mirror.
     **[inference]** Hold the shift at or below the outline's own on-screen motion under the tilt.
     If the pane is drawn with a perspective rotation, that is about h(1 − cos θ), roughly 7 dp for
     a 150 dp half-height at 18 degrees **[computed]**. Otherwise use the slab value, about 2.7 dp
     for a 4 mm plate at 18 degrees (1.6). Today's 10 dp at 18 exceeds both.
   - *Distortion stays second to specular.* Distortion dominates only while specular is weak
     (Schlüter & Faul 2014), so item 1 comes first.
4. **Give the slab an inside: a counter-highlight, a darker edge, a touch of absorption.**
   - A weaker mirrored lobe on the rim opposite the light, rotating with it under item 1.
   - Raise `kEdgeShade` from 0.05 toward Haze's 10-32%, matching Apple's 2027 "darkened edge".
   - A slight darkening that grows with path length, i.e. at the thick part of the bevel.
     Absorption helped shape perception for massive transparent objects (Schlüter & Faul 2019), and
     specular raises perceived thickness (Ohara 2022).
   - This is mostly static depth, so it ranks below 1-3 for motion.

**Avoid:**

- Anything driven by time rather than pose: sweeps, pulses, idle shimmer. These are the incongruent
  highlights of 1.4. Kim et al. found no misalignment that is free.
- Moving the face picture rigidly with the pane (1.3, "painted on"), or moving it further than the
  pane itself moves (item 3).
- Folding the picture (Schlüter & Faul 2019).
- Edge colour that breaks the background's contrast polarity across the outline. Luminance at the
  outline that is inconsistent with transparency suppressed deformation-induced transparency
  (Kawabe & Nishida 2017, via 2018). The bold fringe, which mixes toward pure warm and cool
  colours, is the candidate. Its multiplicative neighbour, the gathered light, is safe.
  **[inference]**
- Deforming or blurring the *outline* itself. Past about 0.54 degrees (≈18 dp) of non-rigid
  outline deformation, or a blurred outline (from ≈6 dp SD), reads as liquid (Kawabe 2017).
- Leaning on chromatic dispersion as the motion cue (1.7).
- Brightening the whole face with tilt. Fresnel on the flat face is flat below 30 degrees (1.6).
- Sharp, high-contrast backdrop detail behind text or icons (Baudisch & Gutwin 2004).

**What the full texts changed (2026-10-20).**

- The ranking is unchanged.
- Item 1 gained: lobe sharpness as a reason (Motoyoshi), a strength criterion (Schlüter & Faul
  2014, Ohara), the rule that all light terms move together (Kim et al.: no safe misalignment), and
  a stimulus-scale anchor (Doerschner ±15 degrees).
- Item 2 gained a physical basis (de Rousiers) and the legibility guardrail (Baudisch & Gutwin).
- Item 3 changed most. "Shrink the shift to under 3 dp" became "keep a shift, at most the outline's
  own motion" (Tamura), and a no-fold ceiling was added (Schlüter & Faul 2019).
- Item 4 gained absorption.
- New avoid: polarity-breaking edge colour (Kawabe & Nishida).
- Tamura & Nakauchi give no angle ceiling at 18 degrees. Their failure case is dominant opposite
  motion in a faceted solid.

## Sources not reached or only second-hand

- Read in full from user-supplied PDFs (2026-10-20): Fleming, Jäkel & Maloney 2011; Fleming &
  Bülthoff 2005; Doerschner et al. 2011; Kawabe, Maruya & Nishida 2015; Tamura & Nakauchi 2018;
  Motoyoshi 2010; Kim, Marlow & Anderson 2011; Schlüter & Faul 2014 and 2019; Kawabe & Nishida
  2018; Falkenberg & Faul 2019; Wyman 2005; de Rousiers et al. 2011; Oliveira & Brauwers 2007;
  Baudisch & Gutwin 2004.
- Read from PubMed Central full text: Tamura, Higashi & Nakauchi 2018 (*Sci Rep*); Schlüter & Faul
  2016; Kawabe & Kogovšek 2017; Kawabe 2017; Ohara et al. 2022; Adams & Elder 2014. Abstracts only:
  Marlow, Kim & Anderson 2011; Anderson & Kim 2009; Levi & Klein 1990.
- Second-hand: Kawabe & Nishida 2017 (via Kawabe & Nishida 2018); Hartung & Kersten 2002 (via Kim
  et al. 2011); Singh & Anderson 2002b (abstract page only); Gigilashvili et al. on caustics
  (repository and search summaries).
- WWDC26 session 112 ("darkened edge"): quoted from GRR, not re-read today.
- No open-source glass shader driving its highlight from device motion was found. Apple says only
  "in some cases" (219).
- Still missing, and each would turn an inference above into a number:
  - psychophysics on chromatic dispersion as a glass cue;
  - a detection threshold for magnification gradients, or for displacement of blurred texture,
    seen through a transparent object;
  - the specular strength at which specular overtakes distortion, in image units;
  - any study of a rigid slab tilting in place, as opposed to rotating solids or drifting liquid.
