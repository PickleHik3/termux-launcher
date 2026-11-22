# AI wallpaper generators: Pixel and Nothing (2026-11-07)

Question from the coordinator: how do the OS-level AI wallpaper generators on Google Pixel and Nothing
phones work (flow, option pool, under the hood, evolution), and what is relevant to a launcher building
something similar? Read-only research, no code changed.

Method. Pages fetched or searched 2026-11-07. Primary = Google blog / Pixel Help / Google Store pages.
Everything else is **secondary** (press, APKMirror, guides). The fetch tool summarises pages, so quoted
text is the tool's quote. Anything not confirmed is marked **unverified**. Several press pages
(Tom's Guide, Android Central, Android Authority, PhoneArena) returned truncated text or HTTP 403, and
Nothing's own site and community staff posts were not reached, so Nothing has no primary source here.

## Summary

- **Pixel** (Pixel 8 and later, Android 14): a structured-prompt generator. The user picks a theme, each
  theme is a prewritten sentence with underlined fill-in words, each word opens a list of options, "Inspire me"
  randomises, "Create wallpaper" generates, the user swipes through several results. Google says it is
  "Google's text-to-image diffusion model" (primary). Colours follow the wallpaper through Material You
  (primary).
- **Why structured:** Google says the prompts were chosen "to add meaning and depth, covering mood, subject,
  material, and art style" (primary). No longer rationale (safety, cost) is published; those are inference.
- **Pixel pool:** at launch 12 themes (secondary count); named themes seen in secondary sources: Soft focus,
  Dark theme, Mood, Drawing, Dreamscapes, Unexpected landscape, Vivid, Classic art. "Curiosities" was not
  found anywhere. Option words per slot are not documented in anything reachable.
- **Pixel on-device vs cloud, latency, safety filter, age limits:** not stated in any primary source I could
  read. Only a Family Link caveat is documented (primary).
- **Nothing** (Wallpaper Studio, Nothing OS 2.5+, introduced with Phone (2a) March 2024 per secondary press):
  not text at all. The user swipes two pickers (a subject/element and a style), taps Create, can Recreate or
  "Surprise me", and can download. Cloud generation is claimed by one search summary only (unverified).
- **Nothing pool:** examples only: sky, iridescence, painting, black and white; Chart, Graphic, Modernist Ink;
  Flora Graphic, Still-Life Photograph, Sky Painting as filenames. No full list. Model and provider unknown.
- **Essential Space:** no source ties it to wallpapers.
- Both vendors converge on: closed vocabulary, one-tap randomise, several or regenerable results, no free text.

## Google Pixel

### Flow (primary: [Pixel Help 16517561](https://support.google.com/pixelphone/answer/16517561), [Pixel Help 14853283](https://support.google.com/pixelphone/answer/14853283))

1. Touch and hold an empty home-screen space, Wallpaper & style, More wallpapers, under "Create wallpaper"
   tap AI wallpaper.
2. Choose a theme.
3. Tap the first underlined phrase of the prompt to customise it; tap each underlined phrase to change its
   description. "Inspire me" produces a random prompt (the newer help page describes it as a random prompt;
   the older page lists it as the step that generates an image).
4. Tap Create wallpaper and wait while the phone generates.
5. Swipe left or right to see other results (newer help page).
6. Done, then preview home and lock, Set wallpaper, choose home, lock or both.
7. Created wallpapers are saved for reuse. Secondary: a "Your AI wallpapers" section lists past creations
   ([9to5Google](https://9to5google.com/2023/10/05/pixel-8-ai-wallpapers/)).
8. System colours follow the wallpaper: "the color palette of your Android system will automatically match
   the wallpaper you've selected"
   ([Google, I/O 2023](https://blog.google/products/android/new-android-features-generative-ai/),
   [Google, Android 14](https://blog.google/products-and-platforms/platforms/android/android-14-customizations/)).
   Help page: "Changing your wallpaper also changes your system colors."

The number of results per generation is not stated; "several" is as precise as the sources get. Cinematic
3D motion is a separate photo effect (Pixel 6+), not applied to AI wallpapers by anything I found.

### Pool (partly secondary)

- Google's only published prompt examples (primary,
  [Android 14 customizations](https://blog.google/products-and-platforms/platforms/android/android-14-customizations/)):
  "A painting of a beach and robots in the 19th century realist style" and "A surreal palace made of flowers
  in shades of blue and indigo." These show the template shape: subject, material, mood, art style.
- Themes named at launch (secondary,
  [9to5Google](https://9to5google.com/2023/10/05/pixel-8-ai-wallpapers/)): Soft focus, Dark theme, Mood,
  Drawing, Dreamscapes, Unexpected landscape, Vivid. "Each theme has its own prompt, and some ask for more
  than one input."
- Other secondary mentions: Classic art (PhoneArena, The Verge via search summaries), Bloom and Imaginary
  (Digital Trends review outputs). Digital Trends and Android Headlines report 12 themes in total
  (secondary, via search summary). That does not reconcile with the 7 above: 5 themes are unnamed here.
- **Not found:** the option words inside each slot, and the theme "Curiosities". Do not rely on it.
- Criticism (secondary, Gizchina via search summary): options are limited to Google's own prompts and
  attributes.

### Under the hood

- Model: "Google's text-to-image diffusion model" (primary, I/O 2023 post); "AI-generated text-to-image
  diffusion models" (primary, [Android 14 launch post](https://blog.google/products-and-platforms/platforms/android/android-14/)).
  No Google source I read names Imagen for wallpapers. A search summary linked Imagen to Pixel Studio and
  Gemini, which is a different feature, so treat "wallpapers use Imagen" as **unverified**.
- On-device vs cloud, latency, safety filtering, region limits, daily caps: **unverified**; not in any
  readable primary source. Inference: a diffusion model of this size on a 2023 phone and the wait described
  by the help page ("wait while your Pixel generates the image") suggest server generation, but this is not
  documented here.
- Requirements (primary): Pixel 8 or later; some account types such as Family Link may not have access.
- Design rationale (primary, partial): a preset list of prompts "chosen to add meaning and depth". Why not
  free text is not published.

### Evolution

- May 2023: announced at I/O as "answer suggested prompts" (primary).
- Oct 2023: shipped on Pixel 8 and 8 Pro with Android 14, rollout began 2023-10-04 (primary, Android 14 post).
  Chromebook Plus and older Pixels were promised (secondary, 9to5Google).
- 2024: Pixel 8a gained it (secondary, Android Authority via search summary).
- Now: help page lists "Pixel 8 or later" for AI wallpaper, emoji wallpaper Pixel 4a+, shape and weather
  effects Pixel 9a+, cinematic Pixel 6+ (primary, Pixel Help 16517561). No Pixel 9 or 10 specific AI
  wallpaper change was found; a Pixel Drop that added generative icon packs was reported by Tom's Guide
  (secondary, search summary only), and is a related feature, not wallpapers.

## Nothing

### Flow (secondary only)

Name: Wallpaper Studio, inside the Nothing Wallpaper app. Release notes (APKMirror, secondary, via search
summary) describe it as an AI creative tool for combining subjects and styles, requiring Nothing OS 2.5.0+.

1. Long-press home screen, Customisation, More Wallpapers, Wallpaper Studio, Create
   ([Beebom](https://beebom.com/nothing-phone-2a-ai-wallpaper-feature/amp/),
   [GadgetsToUse](https://gadgetstouse.com/blog/2024/03/19/create-ai-wallpapers-on-android)).
2. Swipe two pickers: elements/subject and style, to combine them
   ([Nothingtec](https://nothingtec.com/en/?p=2189) via search summary: pick one from each list).
3. Tap Create, wait. Terms of Service shown on first use (GadgetsToUse).
4. Result: Set as wallpaper, Recreate (another version from the same picks), or download icon to save.
   "Surprise me" gives a random combination (GadgetsToUse; Beebom does not mention it).
5. Beebom's author calls it "a little too simplistic" and prefers the prompt editors on Pixel and Galaxy.

Number of results per Create: one, as described; not stated explicitly. Material You or colour extraction
on a Nothing wallpaper: not covered.

### Pool (secondary)

- Beebom examples: sky, iridescence, painting, black and white.
- Nothingtec list names: Chart, Graphic, Modernist Ink (sources of the "mix one from each list" claim).
- AndroidSage sample filenames: Flora Graphic, Still-Life Photograph, Sky Painting
  ([AndroidSage](https://www.androidsage.com/2024/03/07/new-nothing-wallpapers-app-ai-wallpapers-generator-nothing-2a/)).
  Inference: name = subject plus style, which matches two pickers.
- No complete list found. Nothing's visual language is monochrome and graphic, consistent with the examples.

### Under the hood

- Model, provider: **unverified**, no source names one.
- Cloud: a search summary says "Nothing devices use cloud servers to process requests", with no primary
  support. A Terms-of-Service prompt before first generation (GadgetsToUse) is consistent with a server,
  but not proof. Mark as **unverified, probable cloud**.
- Requirements: Nothing OS 2.5.0+. Devices: GadgetsToUse says Phone (1), Phone (2), and (2a); Beebom and
  AndroidSage say Phone (2a) at launch with Nothing OS 2.5 reaching Phone (1) and (2). Sources conflict.
- No safety, age or region information found.

### Evolution

- Mar 2024: introduced with Phone (2a) and Nothing OS 2.5 (secondary, Beebom 2024-03-05, AndroidSage 2024-03-07).
- Wallpaper app updates continued (v1.6.9 added Nothing OS 3.0 wallpapers; v1.8.12 still describes Wallpaper
  Studio as combining subjects and styles; secondary, APKMirror via search summary).
- Not found: any Nothing OS 3.0 change to Wallpaper Studio, any CMF-specific variant, and any link to
  Essential Space (Nothingtec calls Essential Space Nothing's best-known AI feature, unrelated to wallpapers).
- A "Wallpaper Studio Pro" on Nothing Community is a third-party app by a user, not Nothing's tool
  ([post](https://nothing.community/d/47263-free-app-wallpaper-studio-pro-ai-generated-wallpapers-that-finally-fit-the-nothing-vibe)).

## Side by side

| | Pixel AI wallpaper | Nothing Wallpaper Studio |
|---|---|---|
| Input | Theme, then sentence with underlined slots, each slot a closed list | Two pickers (subject/element, style) |
| Free text | No | No |
| Randomise | Inspire me | Surprise me (secondary) |
| Results | Swipe through several | One, then Recreate |
| Apply | Home, lock or both with preview | Set as wallpaper, or download |
| Colours | System palette follows wallpaper (primary) | Not documented |
| Model | Google text-to-image diffusion (primary), name unverified | Unknown |
| Where it runs | Not stated (unverified) | Probably cloud (unverified) |
| Gate | Pixel 8+, no Family Link (primary) | Nothing OS 2.5+, ToS prompt |
| Option pool | About 12 themes (secondary), slot words unknown | Few pairs, no full list |
| Launched | Oct 2023 | Mar 2024 |

## Ideas for a launcher (inference, brief)

- A closed vocabulary beats free text for a wallpaper: it guarantees quality and safety, and the two vendors
  independently chose it. Google's slot categories (mood, subject, material, art style) are a ready schema.
- Randomise is first-class on both. Make it one tap and let the user lock a slot while shuffling the rest.
- Ship the pool as data (themes, template sentence, option lists), not code, so it can be extended; keep
  the user's picks as the saved recipe so a wallpaper is reproducible.
- On-device diffusion is expensive in pixels, memory and heat (see the memory rules in AGENTS.md); a bounded
  store is needed either way. A cloud call needs a provider decision, an account or key story and a
  fallback offline. Neither vendor documents this, so decide it separately.
- Procedural (AGSL) scenes already exist in the repo and share the same pool idea: themes and slot words
  could drive scene parameters with no model at all.
- After generation, feed the image to the existing colour extraction so surfaces follow it, as Pixel does.

## Sources

Primary (Google):
- [Express yourself on Android, with help from AI (I/O 2023)](https://blog.google/products/android/new-android-features-generative-ai/)
- [How Android phones help us be ourselves](https://blog.google/products-and-platforms/platforms/android/android-14-customizations/)
- [Android 14: more customization, control and accessibility](https://blog.google/products-and-platforms/platforms/android/android-14/)
- [Pixel Help: Create and customize the wallpaper](https://support.google.com/pixelphone/answer/16517561)
- [Pixel Help: Change or create a wallpaper](https://support.google.com/pixelphone/answer/14853283)

Secondary:
- [9to5Google, Pixel 8 AI wallpapers](https://9to5google.com/2023/10/05/pixel-8-ai-wallpapers/)
- Search-summary only (pages not read): PhoneArena, Digital Trends, Android Headlines, Android Authority, The Verge, Gizchina, Tom's Guide, Android Central
- [Beebom, Nothing Phone (2a) AI wallpaper](https://beebom.com/nothing-phone-2a-ai-wallpaper-feature/amp/)
- [AndroidSage, Nothing Wallpapers app](https://www.androidsage.com/2024/03/07/new-nothing-wallpapers-app-ai-wallpapers-generator-nothing-2a/)
- [GadgetsToUse](https://gadgetstouse.com/blog/2024/03/19/create-ai-wallpapers-on-android)
- [Nothingtec](https://nothingtec.com/en/?p=2189) (search summary only; fetch failed on certificate)
- [APKMirror, Nothing Wallpaper 1.1.2](https://apkmirror.com/apk/nothing-technology-limited/wallpaper-style-4/wallpaper-style-4-1-1-2-release) (fetch failed; search summary only)
- [Nothing Community, Wallpaper Studio Pro (third party)](https://nothing.community/d/47263-free-app-wallpaper-studio-pro-ai-generated-wallpapers-that-finally-fit-the-nothing-vibe)
