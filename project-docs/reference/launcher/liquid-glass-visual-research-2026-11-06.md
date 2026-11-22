# Apple Liquid Glass: how it looks and how it treats what is behind it (2026-11-06)

Question: what does Apple's Liquid Glass material visibly do, and how does it interact with the
content behind it? Read-only research, nothing built or run, no code changed. It sits beside
`vitro-glass-research-2026-10-25.md` (a third-party Compose imitation) and
`agsl-capabilities-research-2026-11-05.md` (what AGSL can and cannot do); neither is repeated here.

Method. Every claim cites the Apple page that owns it, fetched 2026-11-06: the WWDC25 session
transcripts on developer.apple.com, the Human Interface Guidelines "Materials" page, the developer
documentation (read from developer.apple.com's own documentation data), and Apple Support pages.
Quotes are short and copied from those pages. Anything Apple did not state is in the "Not
documented / inference" section; reverse-engineering projects appear only there and are labelled
secondary. I did not read any patent. Session numbers were checked against the page titles:
219 Meet Liquid Glass, 356 Get to know the new design system, 323 Build a SwiftUI app with the new
design, 284 Build a UIKit app with the new design, 310 Build an AppKit app with the new design.

## Summary: the traits that define the material

1. **Lensing is the primary visual.** Apple says the material "bends, shapes, and concentrates
   light in real time", where earlier materials "scattered light". It is not described as a blur
   ([219](https://developer.apple.com/videos/play/wwdc2025/219/)).
2. **Small and clear at rest, thicker when large.** Bigger glass (a menu from a toolbar button)
   simulates "a thicker, more substantial material": deeper shadows, "more pronounced lensing and
   refraction", "a softer scattering of light" (219).
3. **Light is a layer.** Specular highlights follow the shape's geometry, travel around the edge
   during some interactions, and "in some cases" respond to device motion (219).
4. **Every layer adapts to what is behind it.** Tint, dynamic range and shadow strength change
   continuously with the content beneath; no fixed light or dark look (219).
5. **Small glass flips light/dark, big glass does not.** Nav bars and tab bars flip; menus and
   sidebars adapt but never flip. Symbols and text on Regular flip with them (219).
6. **Touch makes it flex and light up.** Interactive glass scales, bounces and shimmers; a glow
   starts under the finger and spreads across the element and onto nearby glass (219,
   [323](https://developer.apple.com/videos/play/wwdc2025/323/)).
7. **Shapes merge like liquid.** Glass elements in one container blend their outlines as they come
   within a spacing distance, and morph in and out of each other
   ([GlassEffectContainer](https://developer.apple.com/documentation/swiftui/glasseffectcontainer)).
8. **Two variants.** Regular adapts and is legible anywhere; Clear is "permanently more
   transparent", has no adaptive behaviour and needs a dimming layer (219,
   [HIG Materials](https://developer.apple.com/design/human-interface-guidelines/materials)).
9. **It lives on one layer only.** Controls and navigation float above content; no glass in the
   content layer, no glass on glass (219, HIG).
10. **The user and accessibility settings can drain it.** Reduce Transparency frosts it, Increase
    Contrast turns it black or white with a border, Reduce Motion removes elasticity, and from iOS
    26.1 a Clear/Tinted switch changes opacity (219,
    [iOS 26 release notes](https://support.apple.com/en-us/123075)).

## 1. Optics: refraction and lensing

- **Lensing is the defining cue.** "The primary way Liquid Glass visually defines itself is
  through something called Lensing." Apple ties it to everyday glass: "the warping and bending of
  light of a transparent object communicates to us its presence, its motion, and form" (219).
- **Bend and concentrate, not scatter.** "Whereas previous materials scattered light, this new set
  of materials dynamically bends, shapes, and concentrates light in real time." The stated purpose
  is separation: "definition against the background content" so "controls can feel ultra
  lightweight and transparent while still being visually distinguishable" (219).
- **Both blur and bending are present.** The HIG says the Regular variant "blurs and adjusts the
  luminosity of background content to maintain legibility" ([HIG Materials](https://developer.apple.com/design/human-interface-guidelines/materials)).
  The SwiftUI guide says the material "blurs content behind it, reflects color and light of
  surrounding content, and reacts to touch and pointer interactions in real time"
  ([Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)).
  Newsroom: it "reflects and refracts its surroundings"
  ([Newsroom, 2025-06-09](https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/)).
  So Apple names blur, refraction and reflection as separate properties.
- **Colour pickup from beyond the shape.** "The Liquid Glass material reflects and refracts light,
  picking color from nearby content. To create this effect, the glass material samples content
  from an area larger than itself" ([310](https://developer.apple.com/videos/play/wwdc2025/310/)).
  A SwiftUI demo shows "the pink blossoms refracting against the sidebar" (323).
- **Thickness is conveyed by edge behaviour and shadow, and it changes with size.** Large glass
  "casts deeper, richer shadows, has more pronounced lensing and refraction effects, and a softer
  scattering of light", to "simulate a thicker, more substantial material" (219). Apple gives no
  profile, index or curvature numbers.
- **Shape language.** Forms are "rounded, floating" and "nest neatly in the rounded curves of
  modern devices"; capsules suit concentric layouts (219,
  [356](https://developer.apple.com/videos/play/wwdc2025/356/)).
- **Appearing and disappearing is optical.** "Instead of fading, Liquid Glass objects materialize
  in and out by gradually modulating the light bending and lensing" (219). The SwiftUI
  `materialize` transition is the API side of this
  ([custom views article](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)).
- **Chromatic effects: not stated.** No primary Apple source I read mentions dispersion or colour
  fringing. See "Not documented".

## 2. Light: specular highlights and rim lighting

- **Highlights layer.** "Light sources inside of this environment shine on the material producing
  highlights that respond to geometry just as you'd expect" (219).
- **Motion in the light, not only in the glass.** "On interactions, such as locking and unlocking
  your phone, these lights move in space, causing light to travel around the material, defining
  its silhouette" (219). That is the closest Apple comes to describing a rim.
- **Device motion.** "In some cases, the lighting responds to device motion, making it feel like
  Liquid Glass is aware of its position in the real world" (219). Newsroom: it "dynamically
  reacts to movement with specular highlights" (Newsroom). Apple says "in some cases", so it is
  not universal.
- **Touch.** "When you interact with Liquid Glass, the material illuminates from within as a form
  of feedback. Starting right under your fingertips, the glow spreads throughout the element and
  onto any Liquid Glass elements nearby" (219). This is light emitted from inside, not a rim
  highlight.
- **Surrounding content as a light source.** On large surfaces such as sidebars, "light from
  colorful content nearby can subtly spill onto its surface", and "the light reflects, scatters,
  and bleeds into the shadow as well" (219).
- **Not stated:** that highlights respond to the content directly behind a small control, the
  highlight's width, angle, falloff or colour.

## 3. Adaptivity to the background

- **Everything adapts, continuously.** "Unlike previous materials that had a fixed light or dark
  appearance, each layer continuously adapts based on what's behind it." "The amount of tint and
  the dynamic range shift to always ensure buttons remain legible, while letting as much of the
  content through as possible" (219).
- **Light/dark switching is independent of the app.** "When needed, it can also independently
  switch between light and dark" (219). In AppKit: the toolbar "will even switch between a light
  and dark appearance if the scrolled content is especially bright or dark" (310). In SwiftUI:
  "As you scroll through content, the glass automatically adapts to the content underneath,
  changing from light to dark" (323).
- **Size decides whether it flips.** "Small elements like navbars and tabbars ... flip from light
  to dark based on the background." "Bigger elements, like menus or sidebars also adapt based on
  context, but they don't flip from light to dark. Their surface area is too big and transitions
  like these would be distracting" (219).
- **Foreground follows.** "Symbols and glyphs on top of Liquid Glass, do the same. They flip from
  light to dark and vice versa, mirroring the glass's behavior to maximize contrast. All content
  placed on the Regular variant will automatically receive this treatment" (219). Tint colour
  "also uses a vibrant color that adapts to the content behind it" (323).
- **Shadows are content-aware.** "The element is aware of what's behind it and increases the
  opacity of its shadow when it is over text. Conversely, it lowers the opacity of its shadow when
  it is over a solid light background." "As text scrolls underneath, shadows become more
  prominent to create additional separation" (219).
- **Overlap and focus also drive appearance.** System components "dynamically adapt ... in
  response to factors like element overlap and focus state"
  ([Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)).
  "When a window loses focus on the Mac or iPad, Liquid Glass shifts its appearance and visually
  recedes" (219).
- **Luminosity handling.** The Regular variant "blurs and adjusts the luminosity of background
  content" (HIG). Apple does not say how much blur, or the luminosity thresholds that flip the
  appearance.
- **Busy versus plain backgrounds.** Apple's guidance is indirect: use Regular "when background
  content might create legibility issues" and for text-heavy parts such as "alerts, sidebars, or
  popovers" (HIG). The scroll-edge effect and dimming exist because text beneath glass is the hard
  case (section 6).
- **Sheets.** A half sheet is inset with a glass background; "when a half sheet expands to full
  height, it transitions to a more opaque appearance to help maintain focus on the task"
  ([Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass),
  323). When dragged up, glass "subtly recedes, becoming more opaque and gently growing in size"
  (356).

## 4. Variants

- **Regular.** "Most versatile ... This variant gives you all the visual and adaptive effects ...
  It works in any size, over any content and anything can be placed on top of it" (219).
- **Clear.** "Does not have adaptive behaviors. It is permanently more transparent, which allows
  the richness of the content underneath to come through and interact with the glass." "It needs
  a dimming layer to darken the underlying content. Without it, legibility gets noticeably worse"
  (219). HIG: "highly translucent ... for components that float above media backgrounds — such as
  photos and videos".
- **When Clear is allowed.** Three conditions, all required: "over media-rich content", the
  "content layer won't be negatively affected by introducing a dimming layer", and "the content
  sitting above it is bold and bright" (219).
- **Dimming guidance.** "If the underlying content is bright, consider adding a dark dimming layer
  of 35% opacity." No dimming needed if content is dark or the control is standard AVKit playback
  UI that brings its own (HIG). For small glass, "localized dimming" keeps the content vibrant
  (219).
- **Never mix** Regular and Clear (219). The HIG adds: "Only use clear Liquid Glass for components
  that appear over visually rich backgrounds."
- **Identity.** `Glass.identity` is "your content remains unaffected as if no glass effect was
  applied" ([Glass](https://developer.apple.com/documentation/swiftui/glass)); it is an off
  switch, not a look.
- **Tint.** "Selecting a color generates a range of tones that are mapped to content brightness
  underneath the tinted element. It draws inspiration from how colored glass works in reality:
  changing its hue, brightness and saturation depending on whats behind without deviating too much
  from the intended color." A solid fill "is completely opaque and breaks the visual character";
  tint "feels more transparent and more grounded". Use it only for primary actions (219). Tint
  works on both variants.
- **Size-dependent behaviour:** flip rule and thickness effects are in sections 1 and 3. Apple
  also adds an extra-large control size that "takes advantage of Liquid Glass to provide emphasis
  in more spacious areas" (356).
- **API surface** (names only; no visual parameters are exposed): SwiftUI `Glass` has `regular`,
  `clear`, `identity`, `tint(_:)` and `interactive(_:)`
  ([Glass](https://developer.apple.com/documentation/swiftui/glass)); UIKit
  [`UIGlassEffect`](https://developer.apple.com/documentation/uikit/uiglasseffect) has `style`,
  `isInteractive`, `tintColor`; AppKit
  [`NSGlassEffectView`](https://developer.apple.com/documentation/appkit/nsglasseffectview) has
  `style`, `tintColor`, `cornerRadius`, `effectIsInteractive`.

## 5. Motion and interaction

- **Press response.** "Liquid Glass responds to interaction by instantly flexing and energizing
  with light ... an inherent gel-like flexibility ... as it moves in tandem with your interaction"
  (219). `interactive()`: "Glass reacts to user interaction by scaling, bouncing, and shimmering,
  matching the effect provided by toolbar buttons and sliders" (323). Apple gives no scale or
  timing numbers.
- **Glow.** Illumination "from within", starting under the finger and spreading "onto any Liquid
  Glass elements nearby" (219).
- **Lift into glass on touch.** "Elements can even lift up into Liquid Glass temporarily ... This
  lets the resting state stay visually quiet." Slider and toggle knobs become glass during
  interaction, "where the transparent liquid lens can be seen through to precisely observe the
  value underneath it" (219,
  [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)).
  UIKit sliders "preserve momentum and stretch when they are moved" (284).
- **Merging.** `GlassEffectContainer` "combines multiple Liquid Glass shapes into a single shape
  that can morph individual shapes into one another"; "as shapes near one another, their paths
  start to blend into one another. The higher the spacing, the sooner blending begins". A spacing
  larger than the layout's own spacing makes shapes "blend together at rest" ([GlassEffectContainer](https://developer.apple.com/documentation/swiftui/glasseffectcontainer),
  [custom views article](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)).
  AppKit's equivalent is `NSGlassEffectContainerView` with a `spacing` property: "meld together
  based on their proximity" (310).
- **Same-ID union.** `glassEffectUnion` / matching IDs let several views "contribute to a single
  Liquid Glass effect capsule, even when your content is at rest" (custom views article).
- **Morph transitions.** `glassEffectID` ties shapes across a hierarchy change so SwiftUI
  "animates the same shapes"; the default transition between nearby shapes is a matched-geometry
  morph, and `materialize` is for shapes farther apart than the spacing. IDs act only "during view
  hierarchy transitions or animations" (custom views article). Demo: badges expand and are
  "re-absorbed gracefully" into a toolbar button (323).
- **Menus, sheets, alerts from their source.** "When showing a menu, the bubble simply pops open
  to reveal the content contained within. This lightweight, in-line transition keeps everything
  right where you just tapped" (219). Sheets, menus, alerts and popovers "flow smoothly out of
  liquid glass controls" (323); the UIKit button "morphs into the overlay" (284); action sheets
  "spring from the action itself" and on iPad are anchored to their source (356, 284).
- **Controls on one plane.** Moving between app states, glass "dynamically morphs between the
  controls in each context", keeping "a singular floating plane" (219).

## 6. Layering rules

- **One functional layer.** "Liquid Glass forms a distinct functional layer for controls and
  navigation elements ... that floats above the content layer" (HIG). "Don't use Liquid Glass in
  the content layer"; the exception is transient controls (sliders, toggles) that take a glass
  look while active (HIG). A table view as glass "would make it compete with other elements and
  muddy the hierarchy" (219).
- **No glass on glass.** "Stacking Liquid Glass elements on top of each other can quickly make
  the interface feel cluttered." For content on glass: "use fills, transparency, and vibrancy
  for the top elements to make them feel like a thin overlay that is part of the material" (219).
- **Technical reason for grouping.** "Glass can not sample other glass, so having nearby glass
  elements in different containers will result in inconsistent behavior" (323); a container gives
  a shared sampling region, a shared adaptive appearance, and "only needs one sampling pass for the
  entire group" (310).
- **Use sparingly.** "Limit these effects to the most important functional elements" (HIG,
  [284](https://developer.apple.com/videos/play/wwdc2025/284/)); UIKit: Liquid Glass "is distinct
  from other visual effects, like UIBlurEffect" and is "designed to be an interactive layer"
  (284). Custom backgrounds on bars and sheets "might overlay or interfere" with the material and
  the scroll edge effect ([Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)).
- **Scroll edge effect.** "As content begins to scroll underneath a glass element, the effect
  gently dissolves the content into the background, lifting the glass visually above the moving
  content." When dark content makes the glass go dark, "the effect intelligently switches to apply
  a subtle dimming instead". A "hard style" is "applied uniformly across the height of the toolbar
  and the pinned accessory view" (219). It "replac[es] hard dividers with subtle blur", is "not
  decorative", and applies one per view (356). The HIG says it works by "blurring and reducing the
  opacity of background content".
- **Content peeking through.** Content scrolls and peeks beneath glass "to give the interface a
  sense of dynamism and depth" (HIG). In steady states "avoid intersections between content and
  Liquid Glass" by repositioning or scaling content (219). A background extension effect "mirrors
  the adjacent content ... and applies a blur" to fake content under a sidebar
  ([Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)).
- **Standard materials under glass.** Use the standard blur materials "to convey a sense of
  structure in the content beneath Liquid Glass" (HIG).

## 7. Accessibility and user settings

- **Reduce Transparency**: "makes Liquid Glass frostier and obscures more of the content behind
  it" (219).
- **Increase Contrast**: "makes elements predominantly black or white and highlights them with a
  contrasting border" (219).
- **Reduce Motion**: "decreases the intensity of some effects and disables any elastic
  properties for the material" (219). Apple's release notes later say Reduce Motion "more reliably
  reduces the animations of Liquid Glass" (listed under iOS 26.4 and 26.7 on the
  [iOS 26 updates page](https://support.apple.com/en-us/123075)), and a "Reduce bright effects"
  setting "minimizes bright flashes when tapping on elements like buttons" (same page).
- **Automatic.** "Whenever these settings are turned on at a system-level, Liquid Glass elements
  will get them across the board" (219).
- **Clear / Tinted (iOS 26.1).** Settings, Display & Brightness, Liquid Glass: "Choose Clear for a
  more transparent look or Tinted for more contrast and increased opacity." Release notes: it
  changes "the material in apps and notifications on the Lock Screen"
  ([iPhone guide, iOS 26](https://support.apple.com/guide/iphone/adjust-iphone-display-and-text-settings-iphd6804774e/26/ios/26),
  [iOS 26 updates page](https://support.apple.com/en-us/123075)). It is unavailable while Reduce
  Transparency or Increase Contrast is on (same guide page). Apple does not describe which layers
  change.
- **Later change.** The current Apple guide (iOS 27 wording) replaces the two choices with a
  slider under Settings, Appearance, Liquid Glass: "Drag the slider to the right to increase the
  tint of Liquid Glass, and to the left to make Liquid Glass more clear"
  ([iPhone guide](https://support.apple.com/guide/iphone/adjust-iphone-display-and-text-settings-iphd6804774e/ios)).
- **Lock Screen clock.** 26.2 adds an option giving the Liquid Glass clock "more or less opacity"
  (iOS 26 updates page).
- The HIG warns the variants "can differ in response to certain system settings, like if people
  choose a preferred look for Liquid Glass ... or turn on accessibility settings" (HIG).
  Apple does not publish what changes in each case beyond the quotes above.

## Not documented / inference

Apple states none of the following in material I read. Treat each as unknown.

- **How it is rendered.** No source describes the pipeline, filter chain, shader maths, SDF use,
  refraction profile, index, or blur radius. The only implementation facts are: the glass samples a
  region larger than itself, cannot sample other glass, one sampling pass serves a container
  (310, 323), and the AppKit/SwiftUI container renders the shapes "together" (container docs).
- **Chromatic dispersion / colour fringing.** Not mentioned by Apple. Third-party recreations add
  it (below); whether the real material does is unverified by me.
- **Where exactly the rim highlight sits, its angle, falloff, and whether it is a Fresnel term.**
  Apple says only "respond to geometry" and "defining its silhouette".
- **How tilt maps to the light** and which surfaces use it ("in some cases").
- **Thresholds**: the luminance at which small glass flips, hysteresis, how fast tint and shadow
  respond, how much blur Regular applies, scale and timing of the press response.
- **Whether Clear lenses differently** from Regular. Apple says only that it is "permanently more
  transparent" with "no adaptive behaviors".
- **What Reduce Transparency / Tinted change layer by layer.** Apple says "frostier" and "more
  opacity and contrast".
- **Secondary, reverse-engineering and imitations (not Apple, not verified by me; found by one web
  search and not opened).** A search result describes the open-source ShatteredGlass project
  (AlexStrNik) as rebuilding the look from Core Animation `CABackdropLayer` plus a private
  `glassBackground` filter and SDF layers for edge highlights; a Chinese write-up on sspai that
  dumps the private `CABackdropLayer` header on iOS 26; and the Metal library LiquidGlassKit
  (DnV1eX), whose README lists refraction, chromatic dispersion, Fresnel reflection and shape
  merging. These describe private API and third-party guesses, not Apple's documented behaviour.
  Links: [ShatteredGlass](https://github.com/AlexStrNik/ShatteredGlass),
  [LiquidGlassKit](https://github.com/DnV1eX/LiquidGlassKit),
  [sspai write-up](https://sspai.com/post/100983). Open them before relying on them.

## Implications for our AGSL glass (our reading, not Apple's)

Based on what the earlier notes say we do (`vitro-glass-research-2026-10-25.md`: rim refraction,
dispersion, tilt, lag, glint; one shared blurred frame per radius; nothing draws at rest; milky
bodies dropped; AGSL needs API 33). Not checked against current code.

- **Already matches in spirit:** edge-weighted refraction plus blur, rim light that responds to
  tilt (Apple: "in some cases"), glass that samples a wider region than itself, and one shared
  sampling frame for all glass (Apple's container rule: glass cannot sample glass, one pass per
  group).
- **Apple does that we may not:** glass that is quiet at rest and "lifts into" glass only on
  touch; size-dependent thickness (larger panels get deeper shadow, stronger lensing, softer
  light); a shadow whose strength follows the content under it; per-surface light/dark flip with a
  small-flips, large-does-not rule, and glyphs that flip with it; the glow that starts at the
  finger and spreads to neighbouring glass; metaball merging of nearby shapes with a spacing
  threshold (Vitro already has smooth-union); the scroll-edge fade/dim for content under bars;
  optical materialise (modulating lensing) instead of an alpha fade.
- **Apple deliberately does not do:** a milky body (Regular is described as blur plus luminosity
  adjustment, Clear as transparent plus a dimming layer), glass on glass, or glass in the content
  layer. This supports the earlier decision to drop the milky body.
- **Rule worth copying whole:** Clear is only for bold content over media and needs about 35% dark
  dimming when the content is bright (HIG). If we have a clear-like preset, that is the stated
  condition for its legibility.
- **Settings model:** Apple's user control is one opacity/tint axis (Clear to Tinted, a slider in
  iOS 27) that is overridden by Reduce Transparency and Increase Contrast, and Reduce Motion drops
  elastic behaviour. Our knobs could map onto that single axis.
- Cost caveat: Apple's per-pixel adaptivity (live luminance, content-aware shadow) assumes a live
  backdrop; our rule that nothing draws at rest and the frame is cached means we can only sample
  the cached frame (reasoned, not measured).

## Sources

Apple primary, fetched 2026-11-06 unless noted:

- [Newsroom, "Apple introduces a delightful and elegant new software design", 2025-06-09](https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/)
- [WWDC25 219, Meet Liquid Glass](https://developer.apple.com/videos/play/wwdc2025/219/) (transcript read in full)
- [WWDC25 356, Get to know the new design system](https://developer.apple.com/videos/play/wwdc2025/356/)
- [WWDC25 323, Build a SwiftUI app with the new design](https://developer.apple.com/videos/play/wwdc2025/323/)
- [WWDC25 284, Build a UIKit app with the new design](https://developer.apple.com/videos/play/wwdc2025/284/)
- [WWDC25 310, Build an AppKit app with the new design](https://developer.apple.com/videos/play/wwdc2025/310/)
- [HIG, Materials](https://developer.apple.com/design/human-interface-guidelines/materials)
- [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass)
- [Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)
- [SwiftUI Glass](https://developer.apple.com/documentation/swiftui/glass),
  [GlassEffectContainer](https://developer.apple.com/documentation/swiftui/glasseffectcontainer)
  (the `glassEffect(_:in:)`, `glassEffectID` and `glassEffectUnion` pages were fetched, but
  their text adds nothing beyond the custom-views article, which I cite instead)
- [UIGlassEffect](https://developer.apple.com/documentation/uikit/uiglasseffect),
  [UIGlassContainerEffect](https://developer.apple.com/documentation/uikit/uiglasscontainereffect),
  [NSGlassEffectView](https://developer.apple.com/documentation/appkit/nsglasseffectview)
- [iOS 26 updates (release notes index)](https://support.apple.com/en-us/123075)
- [iPhone User Guide, display and text settings (iOS 26 version)](https://support.apple.com/guide/iphone/adjust-iphone-display-and-text-settings-iphd6804774e/26/ios/26)
  and the [current version](https://support.apple.com/guide/iphone/adjust-iphone-display-and-text-settings-iphd6804774e/ios)

Secondary, used only to locate Apple's wording or for the labelled paragraph above: MacRumors,
Engadget and similar coverage of the iOS 26.1 beta 4 toggle (not cited for any claim), and the
three third-party projects listed under "Not documented".

Caveats. The developer-documentation pages are scripts-rendered; I read their text from the JSON
that backs them, so wording is Apple's but I could not see page layout. I did not watch any video.
The WWDC25 transcripts are the on-page text and may differ slightly from the spoken audio.
