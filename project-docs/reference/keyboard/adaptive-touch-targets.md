# How touch keyboards adapt key resolution to the individual user

Status: research note, 2026-10-16. Read-only study; no code changed. Written at the developer's
request. Autocorrect and word suggestions are out of scope; the language model appears only where it
changes which key a touch resolves to, and each such place says so.

Verification legend. **[V]** read in a primary source (source file, paper text, patent text) during
this study. **[A]** taken from an abstract or publisher record only (full text not read). **[U]**
unverified: no primary source found or reachable. AOSP line numbers are from
`android.googlesource.com/platform/packages/inputmethods/LatinIME`, `refs/heads/main`, fetched
2026-10-16; they drift, the file and symbol names are the stable part.

## Summary

- Users land systematically off key centre, and the offset differs per key, per hand posture and per
  person. This is the root finding behind everything else (Azenkot & Zhai 2012 as cited in Yin 2013;
  Sivek & Riley 2022 Figs 1-3). **[V]**
- The standard model is one bivariate Gaussian per key, with the mean shifted from the key centre. The
  production-scale result (Gboard, 2022) found that shifting only the means, pooled into about 7 key
  clusters over the last 800 taps, is what pays; learning full covariances gave no significant gain.
  **[V]**
- Almost every published gain is measured *through a decoder that includes a language model*. The
  Gboard personalisation is applied to the spatial score inside word decoding, and its training data
  is only touches from committed words that matched the final text. So it cannot be copied as-is
  without a dictionary; the pure-geometry part (per-key offset) can. **[V]**
- Without a language model the evidence is thinner but real: Yin et al. 2013 measured a character
  error rate (no LM) of 7.85% for a shared Gaussian, 8.02% for per-key means alone, 7.06% with posture
  and 6.85% with individual plus key adaptation, as an upper bound with known intents. Per-key means
  alone did not beat the shared model. **[V]**
- AOSP LatinIME (open ancestor of Gboard) does **not** use per-user data for hit-testing. Its key
  detector is a plain nearest-key-containing-rectangle test plus slide hysteresis. Its
  `touch_position_correction_data` is a small static per-row table (Y offset and a radius) that feeds
  the native word-decoder's scoring, not the key hit test, and its X offset is marked obsolete. **[V]**
- Apple's dynamic key-target resizing is documented only in a patent (US 8,232,973, priority 2008): a
  hidden hit region added around a key, scaled by the key's next-letter probability from the typed
  prefix, never smaller than the visible key. It depends wholly on a language model. Apple's shipped
  behaviour beyond the patent is **[U]**.
- Hand posture (thumb, two thumbs, index finger) can be inferred from touch data alone (timing versus
  distance between taps, tap size) at roughly 84-86% accuracy and switches which Gaussian set is used;
  it is the main way to avoid the "my model is wrong right now" failure. Gboard's published
  personalisation deliberately ignores posture and orientation. **[V]/[A]**
- Per-user models stay on device in Gboard's published design (touch data kept in unordered coarse
  buckets so typed text cannot be reconstructed). Federated learning is documented for Gboard's
  *language* model, not for touch models. **[V]**
- This repo already ships the geometry-only half: `TapModel` / `TapCorrectionController`
  (per-key centroid bias, shrinkage, cap, per-layout store, off by default). See the feasibility
  section for what is left.

## 1. Touch offset and bias models

### 1.1 The phenomenon

- Henze, Rukzio, Boll, "100,000,000 Taps", MobileHCI 2011, pp. 133-142, DOI 10.1145/2037373.2037395.
  A game on the Android Market, 91,731 installs, 120,626,225 touch events; touch positions are
  systematically skewed; a compensation function derived from the data reduced the error rate by 7.79%
  in a follow-up with 12,201 installs. **[A]** (abstract via OFFIS and Lancaster records). Sivek &
  Riley summarise the keyboard variant as a per-device-type function shifting touches toward key
  centres, 7.7% fewer errors and 2.6% faster. **[V]** (Sivek & Riley sec 1.1).
- Holz & Baudisch, "The Generalized Perceived Input Point Model and How to Double Touch Accuracy by
  Extracting Fingerprints", CHI 2010, pp. 581-590. Argues inaccuracy is mostly not the "fat finger"
  but an offset that depends on finger posture and on the individual; the generalised model explained
  67% of the inaccuracy previously attributed to the fat finger; contact points for one target form
  small ovals that are offset per posture. **[A]** (author page abstract; full text not read). The
  prototype (RidgePad) needs fingerprint-resolution sensing and is not applicable to a phone screen.
- Azenkot & Zhai, "Touch behavior with different postures on soft smartphone keyboards", MobileHCI
  2012, pp. 251-260. As reported by Yin et al.: three postures (one index finger, one thumb, two
  thumbs); touch points shift by posture; shift depends on the key (for one finger, downward on the
  bottom row but not the top row); points spread wider pooled across users than within one user.
  **[V]** (Yin et al. 2013, Introduction, observations 1-4; the original not read).

### 1.2 The model form

- One bivariate Gaussian per key, with the mean given as an offset from the key's bounding-box centre,
  is the baseline (Yin et al. sec "Hierarchical Spatial Backoff Models"; Goodman et al. and Zhai et al.
  as cited there). **[V]**
- Yin, Ouyang, Partridge, Zhai, "Making touchscreen keyboards adaptive to keys, hand postures, and
  individuals: a hierarchical spatial backoff model approach", CHI 2013, pp. 2775-2784,
  DOI 10.1145/2470654.2481384 (https://research.google.com/pubs/archive/41930.pdf). **[V]**
  - Hierarchy: base model (one Gaussian N(mu, Lambda) for all keys, users, postures) at the bottom;
    above it, key-adaptive, posture-adaptive, user-adaptive and combinations, up to user x posture x
    key (the paper counts 26 keys x 3 postures = 78 submodels per user).
  - Backoff: a specific submodel is used only if its posture is identified with confidence and it has
    enough training points; otherwise it falls back to a simpler one. The authors' analysis for key "E"
    suggests a user-and-key model needs at least about 55 points before it beats the key-adaptive
    model.
  - Results (Table 1, character error rate, **no language model**, ten-fold cross-validation on the
    32-participant "Pepper" dataset, observed postures and intents, so an upper bound): distance from
    key centre 7.98%; shared Gaussian 7.85%; key-adaptive 8.02%; posture and key 7.06%;
    individual and key 6.85%. With an online posture classifier (86.4% accurate) the prototype reduced
    the "language-model-independent error rate by 13.2%" over the non-adaptive baseline (abstract).
  - Language-model dependence: the key decision is arg max p(k; theta_l) p(c | k; theta_s) (eq. 5),
    a language prior times the spatial likelihood. The "no LM" numbers set the prior uniform.
- Findlater & Wobbrock, "Personalized input: improving ten-finger touchscreen typing through automatic
  adaptation", CHI 2012. Two keyboards that adapt key-press classification per person, one also
  moving keys; the stable-layout one significantly improved speed in a three-session study; the moving
  one did not. **[A]** (abstract via search; PDF fetch failed). Relevant here as evidence that adapting
  the *classifier* with the layout fixed works and that moving visible keys does not help.
- Weir, Pohl, Rogers, Vertanen, Kristensson, "Uncertain Text Entry on Mobile Devices", CHI 2014,
  pp. 2307-2316, DOI 10.1145/2556288.2557412. A decoder pairing a Gaussian Process touch model with a
  language model; character error rate down by up to 7% over a baseline and up to 1.3% over a leading
  commercial keyboard; a pressure-controlled variant lets the user signal certainty. **[A]**
  (Glasgow Enlighten record). Language-model dependent in evaluation. The Gaussian Process learns a
  touch-to-intended-point mapping from sensor data; Sivek & Riley report it was run with 8
  participants x 1000 keystrokes in a fixed orientation and posture. **[V]** (Sivek & Riley sec 1.1).
- Gunawardana, Paek, Meek, "Usability Guided Key-Target Resizing for Soft Keyboards", IUI 2010,
  pp. 111-118 (https://www.microsoft.com/en-us/research/publication/usability-guided-key-target-resizing-for-soft-keyboards).
  Source-channel resizing: a key's target is the region where p_L(k|h) p_T(l|k) exceeds the rivals';
  because that can make a key untypeable, each key gets a central **anchor** that is always its own.
  The best anchor area was 360 against 4700 for the full static key (paper's units; at 4700 no
  resizing happens). **[V]** (text of the MSR PDF). Language-model dependent, but the anchor idea is
  the part that is geometry only and is the usability guard worth copying.

### 1.3 How it is learned online (production)

Sivek & Riley, "Spatial model personalization in Gboard", Proc. ACM HCI 6(MHCI), Art. 202, Sept 2022,
DOI 10.1145/3546737, arXiv 2209.11311. The only published account of personalisation on a shipping
keyboard. **[V]**

- Model: the Gaussian spatial model's key centres are replaced by personalised centres (centre plus
  learned offset mean); sigma is a per-language constant. Optional learned covariance was tried and
  dropped: "Using full covariance did not yield any significant improvements" (sec 5).
- Statistics kept per key: count and sums of dx, dy (and squares and cross term for covariance), in
  key-width/height units (sec 3.1, eq. 3 normalises dx by key width, dy by key height).
- Training data: "all touch points from committed words such that the word (a) was committed via tap
  typing, and (b) matches the final committed text for that text span". This requires knowing the
  intended text, which is the language-model side: touches the user later edited are excluded. This
  is the label source and it does not exist without a text buffer.
- Sparsity: keys are grouped into rectangular clusters by a greedy decision tree; offsets are per
  cluster. 7 clusters were used; 4-9 gave different spatial cost but no significant difference in
  user-visible metrics (sec 4.2).
- Data window: the most recent N taps, 250 <= N <= 800 in experiments; settled on 800 taps in 4
  buckets. Uniform weights performed the same as arithmetic decay. More data gave a better fit with
  rapidly diminishing returns (sec 4.1).
- Not used: orientation, posture, app. "we did not attempt to predict posture at any point."
- Result: after re-tuning sigma, personalisation beat the best non-personalised model in every
  language except English in India. Table in Fig. 12, per language, e.g. de-DE: WMR -1.09%, WPM +0.57%;
  en-GB: WMR -1.88%, WPM +0.51%. The authors call the gains "small-but-significant" and attribute the
  small size to a strong LM and auto-correction already absorbing errors.
- Where it acts: in the decoder's spatial score for word candidates (eq. 1-3), not described as moving
  hit boundaries. A separate "key correction model to decide when a key press near a boundary should
  be forcibly corrected to a key on the other side" is mentioned but its details are not published
  (sec 1). **[U]** beyond that sentence.

## 2. Invisible and dynamic hit-area resizing

### 2.1 Apple

- US 8,232,973 B2 (Apple Inc., priority 2008-01-09, provisional 61/010,619, filed 2008-06-30,
  published 2012-07-31), "Method, device, and graphical user interface providing word recommendations
  for text input" (https://patents.google.com/patent/US8232973B2/en). **[V]** from the patent text:
  each key icon has "an adjustable hit region of dynamically adjustable size"; the part larger than
  the key is a "hidden hit region" not displayed; sizes are "dynamically adjusted (e.g., updated after
  each character in a character string is entered)"; "the total hit area ... is never less than the
  visible key area"; the size is set by "determining a probability associated with the respective key
  icon" from the displayed character sequence and usage-based probabilities; a constant K "can be
  determined by trial and error by observing users" and "approaches zero as the size of the visible
  keys increases". Only the first ~100,000 of ~325,000 characters of the page were read; the formula
  itself came through garbled.
- Language-model dependence: complete. The probability is of the next letter given the typed prefix
  and a word list. With no dictionary the mechanism has no input.
- Per-user adaptation: the patent text read describes frequency and probability, not a per-user touch
  offset. Whether iOS also learns a per-user touch offset is **[U]**: no Apple documentation found.
- Apple developer documentation was not found to describe the behaviour. Treat any claim about iOS
  internals beyond the patent as **[U]**.

### 2.2 AOSP LatinIME (ancestor of Gboard) **[V]**

Source read: `java/src/com/android/inputmethod/keyboard/{KeyDetector,PointerTracker,ProximityInfo}.java`,
`.../keyboard/internal/{TouchPositionCorrection,BogusMoveEventDetector,TypingTimeRecorder}.java`,
`native/jni/src/suggest/core/layout/{touch_position_correction_utils.h,proximity_info_state*.cpp}`,
`java/res/values/{touch-position-correction.xml,config-common.xml}`. Gboard itself is closed source, so
none of this is proof of what Gboard ships today.

- **Hit test.** `KeyDetector.detectHitKey` (KeyDetector.java:88-115) takes the touch point, adds a
  constant `mCorrectionX/Y` (`getTouchX/getTouchY`, lines ~64-72; there is a "TODO: Remove vertical
  correction"), asks the keyboard for the nearest keys, keeps those whose hit box contains the point
  (`key.isOnKey`), and picks the one with the smallest squared distance to its edge. No per-user data
  and no language model. Edge keys have an enlarged hit box (comment at line ~99). Where the constant
  correction values come from was not traced: **[U]**.
- **Touch position correction data.** `TouchPositionCorrection.java` loads an array of triples per
  row: "correctionX = (touch_center_x - hitbox_center_x) / hitbox_width, correctionY = ... /
  hitbox_height, correctionR = sweet_spot_radius / sqrt(hitbox_width^2 + hitbox_height^2)"
  (`touch-position-correction.xml` header comment). `getX` is hard-wired to return 0 with the comment
  "Touch position correction data for X coordinate is obsolete" (TouchPositionCorrection.java:86). The
  shipped table `touch_position_correction_data_holo` has three rows, for example the second row is
  -0.0236678, 0.0381731, 0.1529972 (so Y is pushed down 3.8% of key height on row 2 and 8.8% on row
  3). It is static, per row, not per user, not per key.
- **Where that data is used.** `ProximityInfo.java:173-203` builds per-key `sweetSpotCenterXs/Ys` and
  `sweetSpotRadii` from the hit box plus those row corrections and passes them to the native layer.
  There, `ProximityInfoStateUtils::calculateNormalizedSquaredDistance` (proximity_info_state_utils.cpp
  ~157-190) divides squared distance from the sweet-spot centre by squared radius, and
  `TouchPositionCorrectionUtils::getSweetSpotFactor` (touch_position_correction_utils.h) maps it with
  a piecewise-linear function (0 at the centre, 0.24 at 25% of the radius, 1.20 outside the radius) to
  promote or demote key candidates. That is the dictionary decoder scoring candidate words:
  **language-model dependent, and it does not change which key the on-screen tap commits.**
- **Slide-off hysteresis.** `PointerTracker.isMajorEnoughMoveToBeOnNewKey` (PointerTracker.java:1080-1112)
  changes the current key only when the distance from the old key's edge reaches
  `getKeyHysteresisDistanceSquared` (resource `keyHysteresisDistance`, with a separate 8 dp
  `config_key_hysteresis_distance_for_sliding_modifier`, config-common.xml:66); the default
  `KeyDetector()` constructor uses 0, and the real value is read from the theme attribute in
  MainKeyboardView.java:193-198. On touch-up the key is the one the finger is on, so sliding is how a
  user fixes a wrong landing before lifting.
- **Fast-typing bogus-move hack.** Same function: during fast typing (`TypingTimeRecorder.isInFastTyping`,
  a time since the last letter) and after a long accumulated travel, a move counts as a new key even
  inside hysteresis. Gated to tablets / low-density screens by `BogusMoveEventDetector.init`
  (thresholds 0.53 and 1.14 of key diagonal).
- **Touch noise filter.** `PointerTracker.onDownEvent` (lines 622-632): a down within
  `touchNoiseThresholdTime` (40 ms) of the previous up and within `touchNoiseThresholdDistance`
  (12.6 dp) of the last position is ignored as noise (config-common.xml:96-97).
- **Multi-touch slide-off.** When more than one pointer is down and a finger slides off its key, the
  key is committed rather than switched (PointerTracker.java ~862-868), "defends against noise from
  some touch panels".
- Pressure and contact size: grep of the files above found no use of pressure or touch size.

### 2.3 SwiftKey / Microsoft

- No first-party engineering post or patent on a per-user touch model was found. Wikipedia says
  SwiftKey adapts the touch-sensitive area of each key from the user's typing precision and itself
  tags the sentence "citation needed". **[U]**. Microsoft Research's published relevant work is
  Gunawardana et al. above (anchored key-target resizing, language-model based). Where the
  `Microsoft Technology Licensing` / `TouchType` patents would be searched is Google Patents, not done.

## 3. Hand posture and grip

- Postures studied: one index finger, one thumb, two thumbs (Azenkot & Zhai via Yin). The same person
  changes posture with context (sitting, standing, walking). **[V]** (Yin, Introduction).
- From touch data alone (Yin et al.): for one-finger input, time between taps grows with the distance
  moved; for two-thumb input there is no such trend. A per-tap SVM plus a sliding window of 10 touches
  (about two words) gives 86.4% accuracy over 26,769 touch points; first few touches of a session are
  "unknown", and the model then backs off. Posture is only used when classifier confidence p_y exceeds
  a threshold. **[V]**
- From sensors: GripSense (Goel, Wobbrock, Patel, UIST 2012, pp. 545-554) used touch plus inertial
  sensors and the vibration motor; 99.67% table versus hand, 84.26% hand posture, 95.1% three pressure
  levels in a 10-person study. **[A]** (UW Ubicomp Lab page).
- ContextType (Goel et al., CHI 2013, DOI 10.1145/2470654.2481386): infers posture from tap size and
  time between taps and keeps a spatial model per posture, with a language model; 20.6% lower total
  error rate than personalised models without posture, 16 participants, no speed change because more
  errors were corrected. **[A]** for the abstract; **[V]** for the description via Sivek & Riley sec 1.1.
- WalkType (Goel et al.): adding accelerometer data lifted speed from 28.3 to 31.3 WPM and cut
  uncorrected errors from 10.5% to 5.8% against plain key-rectangle hit testing. **[V]** as quoted by
  Sivek & Riley; the paper itself not read. Music & Murray-Smith (via the same text): while walking,
  posture-dependent offset models cut errors 14-30% against 6-17% for static offsets.
- Effect on the model: posture picks which Gaussian set to use (Yin) and, in the stronger systems,
  which offsets (ContextType). The cost is a misclassification, which Yin handles by backing off.
- Production position: Gboard personalisation uses no posture signal (Sivek & Riley sec 3.1). **[V]**

## 4. Other mechanisms that decide which key a touch means

All are independent of suggestions.

| Mechanism | What it does | Source |
|---|---|---|
| Resolve on down | Key chosen at touch-down, finger movement later may change it | LatinIME `PointerTracker.onDownEvent` / `onMoveEvent` [V]; this repo `Keyboard2View.onTouch` ACTION_DOWN (line 1622-1636) [V] |
| Resolve on up | Key is whichever the finger is over at lift, with slide allowed | LatinIME `onUpEventInternal` (PointerTracker.java:956) [V] |
| Slide-off hysteresis | New key only after the finger is a set distance beyond the old key's edge | `isMajorEnoughMoveToBeOnNewKey`, PointerTracker.java:1080-1112 [V] |
| Fast-typing bypass | During fast typing, treat a long drift as a new key | same function; `TypingTimeRecorder.isInFastTyping` [V] |
| Up-to-down noise filter | Drop a down within 40 ms and 12.6 dp of the last up | PointerTracker.java:622-632, config-common.xml:96-97 [V] |
| Phantom move hacks | Split firmware-merged down/move/up into separate events | PointerTracker.java:836-860 [V] |
| Multi-touch slide-off | Commit instead of switching when several fingers are down | PointerTracker.java:862-868 [V] |
| Contact area | `MotionEvent.AXIS_SIZE` is normalised 0-1, non-linear, "can be used to determine fat touch events"; `AXIS_TOUCH_MAJOR` is calibrated, in display pixels | AOSP MotionEvent.java:695-716 [V] |
| Pressure | `AXIS_PRESSURE` nominally 0-1, "values higher than 1 may be generated depending on the calibration of the input device" | MotionEvent.java ~660-675 [V] |
| Tap size as posture cue | ContextType uses tap sizes and inter-tap time | Sivek & Riley sec 1.1 [V] |
| Pressure as explicit certainty | Weir et al. let pressure express how certain a touch is | Glasgow record [A] |

Debounce for accidental touches in LatinIME is therefore the time-and-distance filter above; no
pressure or contact-area filter exists in the files read.

## 5. Privacy and storage

- Gboard spatial personalisation: "We use an on-device adaptation framework within Gboard which keeps
  data on the phone's persistent storage; none of the touch data described here or typed words ever
  leave the device." Touches are stored in coarse unordered buckets so "the typed content cannot be
  reconstructed even with physical access to the device"; at most N recent points; the oldest bucket
  expires whole. Live experiments trained per-user clusters "to avoid having to send touch data off
  the phone". Server-side metrics log spatial score, event type and timing but "we do not store
  anything about the typed content itself or the touch points". **[V]** (Sivek & Riley sec 3.1, 3.3,
  4.2).
- Federated learning for Gboard is documented for the next-word language model (Hard et al.,
  "Federated Learning for Mobile Keyboard Prediction", arXiv 1811.03604, a recurrent LM trained with
  Federated Averaging). **[A]** (abstract). No Google paper found that applies federated learning to
  the touch/spatial model; the spatial work above says the opposite (per-device only). **[U]** for
  any such claim.
- Android side: nothing in the platform stores a touch model for an IME; it is app-private storage.
  (Inference; no source needed beyond the above.)

## 6. Comparison

| Keyboard / system | Technique | Per-user | Needs LM | Affects hit area / resolved key | Evidence |
|---|---|---|---|---|---|
| AOSP LatinIME | Nearest key whose hit box contains the point, edge keys enlarged | No | No | Yes (static) | KeyDetector.java:88-115 [V] |
| AOSP LatinIME | Slide hysteresis, noise filter, bogus-move hacks | No | No | Yes (when the key changes) | PointerTracker.java [V] |
| AOSP LatinIME | Sweet-spot table (row Y offset, radius) | No (static data) | Yes (decoder scoring) | No | ProximityInfo.java:173-203, touch_position_correction_utils.h [V] |
| Gboard | Personalised key-centre offsets, 7 key clusters, last 800 taps | Yes, on device | Yes (spatial score in decoder; training labels need committed text) | Not stated; a separate key-correction model exists, details unpublished | Sivek & Riley 2022 [V] |
| Gboard | Posture-aware models | n/a | n/a | n/a | Explicitly not used, same paper [V] |
| Apple iOS | Hidden hit region scaled by next-letter probability, floor = visible key | Not in the patent text read | Yes | Yes | US 8,232,973 [V]; shipped behaviour [U] |
| SwiftKey | Adaptive touch area per key | Claimed | Unknown | Claimed | Wikipedia only, "citation needed" [U] |
| Research: Yin 2013 | Hierarchical Gaussian backoff (key x posture x user) | Yes | Optional; no-LM numbers reported | Resolves key by max likelihood | CHI 2013 [V] |
| Research: ContextType | Per-posture model, posture from tap size and timing | Yes | Yes | Resolves key | CHI 2013 [A] |
| Research: Findlater & Wobbrock | Per-person key-press classifier, fixed layout | Yes | Not established | Yes | CHI 2012 [A] |
| Research: Henze et al. | Global per-device offset function | No | No | Shifts the touch point | MobileHCI 2011 [A] |
| Research: Gunawardana et al. | Anchored target resizing | No | Yes | Yes (with an always-own anchor) | IUI 2010 [V] |
| Research: Weir et al. | Gaussian Process touch model | Yes (trained per participant) | Yes in evaluation | Resolves key | CHI 2014 [A] |
| This repo | Per-key centroid bias, shrinkage, 0.3 cap, per-layout store | Yes, on device | No | Yes | `TapModel.java`, `TapCorrectionController.java` [V] |

## 7. This repo today

How a touch becomes a key (read in `inapp-keyboard/src/main/java/juloo/keyboard2/`):

- `Keyboard2View.onTouch` (line 1605): at `ACTION_DOWN`/`ACTION_POINTER_DOWN` it takes the pointer's
  x, y, calls `getKeyAtPosition(tx, ty)` (line 1816: row by y via `getRowAtPosition`, then keys of
  the row left to right, first rectangle containing x), then `resolveTap(rawKey, tx, ty)` (line ~1685),
  and passes the result to `Pointers.onTouchDown` (Pointers.java:254). The key is decided at
  touch-down and fixed; later movement only selects a directional legend (swipe) if travel exceeds
  `swipeDistanceFor` (Pointers.java:285-289, modifiers get 1.75x slack), never a different key.
  There is no slide-to-neighbour hysteresis, no up-time re-resolution, no pressure or contact-size
  read (a grep for `getPressure`, `getSize`, `getTouchMajor` in `inapp-keyboard/src/main` finds none).
- Debounce: `Pointers.onTouchDown` ignores new presses while a slider is active ("ghost touch events",
  Pointers.java:256-259). There is no up-to-down time-and-distance filter.
- `TapResolver` (Keyboard2View.java:120-136) is the local hook, documented in `UPSTREAM.md`
  deviations. The host implementation is `app/.../terminal/inappkeyboard/TapCorrectionController.java`
  over `TapModel.java` and `TapModelStore`.
- `TapModel` (read in full): per key count and summed offset from centre in key-width units; observes
  only taps on plain character keys that did not swipe; resolves a press by moving the decision
  boundary between a pair of keys by the mean of the two keys' shrunk biases (shrinkage n/(n+20),
  cap 0.3 of the narrower key per axis, statistics halved at 400 taps); records against the raw grid
  key so it never trains on its own output. Store keyed by layout id plus geometry signature, 8
  entries LRU, saved 5 s after the last tap; off by default; reset available.
- The earlier feasibility study is `project-docs/reference/launcher/keyboard-mis-input-correction.md`
  (stage 1 delivered 2026-09-01).

Comparison to the literature: this is structurally the Henze/Sivek idea (per-key offsets, per user,
on device) done as boundary movement instead of Gaussian likelihood. Differences worth knowing:
Sivek & Riley pool keys into ~7 clusters to fight sparsity, we shrink each key towards zero;
Gboard weights recent taps (a window of 800), we halve at 400 per key; Gboard's labels come from
words that survived editing, ours come from every non-swiped tap (the unsupervised variant the
earlier study flagged; Yin's Table 1 shows per-key means alone did not beat a shared Gaussian
unless posture or user were also separated, 8.02% versus 7.85%, so expect the gain to be small and to
be mostly the *user* part).

## 8. Feasibility for the in-app keyboard (no language model)

Constraint reminders from AGENTS.md: terminal bytes cannot be revised after sending; character keys
only; modifiers must never be mis-corrected; bounded, resettable, off by default for anything that
moves a decision.

| Technique | Needs LM | Fit | Effort (rough) | Risk |
|---|---|---|---|---|
| A. Anchor guard on the existing boundary shift (a central region of each key, say its inner 50% per axis, that is always that key, per Gunawardana) | No | Small addition to `TapModel.resolve` | 0.5 day incl. tests | Low; bounds the one failure mode (an intended press on a key centre being stolen). The current 0.3 cap on boundary movement already leaves 70% of the narrower key, so first check whether it is redundant |
| B. Backspace-retype supervision (stage 2 of the earlier study): adjacent char key X, Backspace, adjacent char key Y within a short window, no other keys | No (uses only key sequence) | New observer in `TapCorrectionController` plus a per-pair term in `TapModel` | 2-3 days incl. persistence and tests | Medium; gate must be strict. It is the only label source available without a dictionary, and is the analogue of Gboard's "final committed text" label. In a terminal a backspace may be a deliberate edit or a no-op, so tune on pong traces first |
| C. Slide-to-correct hysteresis for plain character taps: let a tap re-resolve on touch-up to the key under the finger if the finger moved less than the swipe threshold into a neighbour | No | Touches `Pointers` swipe logic, which is vendored upstream | 2-3 days; larger `UPSTREAM.md` deviation | Medium-high; conflicts with swipe-to-legend gestures, which are the point of this keyboard |
| D. Up-to-down noise filter (drop a down within about 40 ms and about 12 dp of the previous up) | No | One field in `Keyboard2View.onTouch` or the host | 0.5 day | Low-medium; fast same-key double taps (`ll`) would be dropped if the distance is under the filter, so tune on pong; AOSP gates its own bogus-move hacks to tablets |
| E. Contact-size as a posture cue (`MotionEvent.getTouchMajor`, `getSize`), switch between two bias tables | No | Needs a posture classifier and a second table per layout | 4-6 days | Medium; AXIS_SIZE is not calibrated across devices (AOSP doc), so it must be learned relative to the user's own distribution. Yin's 86.4% used timing and distance, which this keyboard could also compute (sampled in `observeTap`) |
| F. Time-versus-distance posture classifier (one finger versus two thumbs) from tap timing, selecting between two bias tables | No | Add timestamps to `observeTap` | 4-5 days | Medium; Gboard chose not to, and Yin's gain needed a backoff when confidence is low |
| G. Clustered offsets (Sivek: about 7 rectangular key clusters) in place of per-key shrinkage, for sparsely hit keys | No | Replace the pair-bias source in `TapModel` | 1-2 days | Low; mainly helps rarely used keys, which are the majority on a terminal keyboard |
| H. Language prior (dictionary, history) | Yes | Not available; already ruled out in the earlier study | n/a | Out of scope |

Top three, in the order recommended:

1. **B, backspace-retype supervision (2-3 days).** It is the only way to turn the current
   unsupervised centroid into evidence about *decisions*, and the only analogue of Gboard's training
   label we can build without text.
2. **G, clustered offsets (1-2 days)** (with **A** as a half-day guard if any theft of a clean
   centre press is ever seen). Directly supported by the largest published study, cheap, no new
   state beyond the existing store.
3. **D, up-to-down noise filter (0.5 day).** Pure AOSP-proven mechanism, independent of the tap
   model, easy to switch off; needs a pong check against fast double taps.

Not recommended without a device study: C (conflicts with swipe legends) and E/F (posture) until the
simple offsets have been measured on pong; Yin's own Table 1 suggests per-key alone is weak and
posture is where the gain is, so E/F are the highest ceiling but the highest cost.

Measurement before and after: count backspace-retype pairs per thousand keystrokes (the study already
proposes this), replay recorded raw touch traces through the model (a debug build is needed to record
them), and check pong in both orientations.

## Sources

Primary, read in full or in relevant part:

- Sivek & Riley, "Spatial model personalization in Gboard", Proc. ACM HCI 6(MHCI) 202, 2022. https://arxiv.org/abs/2209.11311 (DOI 10.1145/3546737)
- Yin, Ouyang, Partridge, Zhai, CHI 2013. https://research.google.com/pubs/archive/41930.pdf (DOI 10.1145/2470654.2481384)
- Gunawardana, Paek, Meek, IUI 2010. https://microsoft.com/en-us/research/wp-content/uploads/2016/02/paper-final.pdf (DOI 10.1145/1719970.1719986)
- Apple US 8,232,973 B2. https://patents.google.com/patent/US8232973B2/en
- AOSP LatinIME, https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/main/ : `java/src/com/android/inputmethod/keyboard/KeyDetector.java`, `PointerTracker.java`, `ProximityInfo.java`, `internal/TouchPositionCorrection.java`, `internal/BogusMoveEventDetector.java`, `internal/TypingTimeRecorder.java`, `MainKeyboardView.java`, `java/res/values/touch-position-correction.xml`, `java/res/values/config-common.xml`, `native/jni/src/suggest/core/layout/touch_position_correction_utils.h`, `proximity_info_state_utils.cpp`
- AOSP `MotionEvent.java`, https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/MotionEvent.java
- This repo: `inapp-keyboard/src/main/java/juloo/keyboard2/{Keyboard2View,Pointers,TapGeometry}.java`; `app/src/main/java/com/termux/app/terminal/inappkeyboard/{TapModel,TapCorrectionController}.java`; `project-docs/reference/launcher/keyboard-mis-input-correction.md`

Abstract or bibliographic record only (full text not read):

- Henze, Rukzio, Boll, MobileHCI 2011, DOI 10.1145/2037373.2037395. https://eprints.lancs.ac.uk/id/eprint/57576
- Holz & Baudisch, CHI 2010, pp. 581-590. https://christianholz.net/generalized_perceived_input_point_model
- Azenkot & Zhai, MobileHCI 2012, DOI 10.1145/2371574.2371612. https://research.google/pubs/touch-behavior-with-different-postures-on-soft-smartphone-keyboards/
- Findlater & Wobbrock, CHI 2012. https://faculty.washington.edu/leahkf/pubs/CHI2012-findlater-PersonalizedTyping.pdf (fetch returned 404 at study time; abstract via search)
- Weir et al., CHI 2014, DOI 10.1145/2556288.2557412. https://eprints.gla.ac.uk/89637
- Goel, Wobbrock, Patel, GripSense, UIST 2012. https://ubicomplab.cs.washington.edu/publications/gripsense/
- Goel et al., ContextType, CHI 2013, DOI 10.1145/2470654.2481386. https://ubicomplab.cs.washington.edu/publications/contexttype
- Hard et al., Federated Learning for Mobile Keyboard Prediction. https://arxiv.org/abs/1811.03604
- WalkType and Music & Murray-Smith: cited only through Sivek & Riley sec 1.1.

Secondary, flagged unverified: Wikipedia "Microsoft SwiftKey" (adaptive touch area claim, "citation needed").
