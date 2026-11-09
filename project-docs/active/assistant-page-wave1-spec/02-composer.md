
02 · Composer and dictation · Assistant page build spec
Contents01 Page02 Composer03 Model chip04 No model05 Chats list06 Wall entry07 Look
# 02 · Composer and dictation
The input row in every state: empty, typing, growing, and voice dictation with its permission and speech-model cases.
Wave 1 · 412 × 915 dp frames · Termux Launcher v2 design system
 Numbered callouts Size overlay on frames Fit more (60% scale)Frames are 412 × 915 dp at 1 dp = 1 CSS px when scale is off.
02.1 Idle
02.2 Focused
02.3 Typed
02.4 Growing
02.5 Cap
02.6 Listening
02.7 Partial
02.8 Done
02.9 Permission
02.10 No speech model
Build notes
## Typing
The composer is one row: attach, field, mic, send. It is pinned to the bottom of the page surface and rides on top of the keyboard.
### 02.1Idle
Send is disabled while the field is empty.
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
02.1 · Idle, send disabledDark · Cool wallpaper · Glass on
#### Idle · 02.1
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Composer | min 56; corner 28 (full at one line); padding 4; inset 8 from the pane | — | surfaceContainerHigh | 
| | No outline at rest. | 
| 2 | Attach | 48 touch, icon 24 | — | onSurfaceVariant | 
| | Enabled; opens the attach sheet in wave 2. In wave 1 it does nothing visible beyond the ripple spec decision. | 
| 3 | Field | flex (220 at 412); min 48; padding 12 / 4 | body text-base 16/24 Regular | placeholder onSurfaceVariant | 
| | "Ask anything". One line, ellipsized at large font scale. | 
| 4 | Mic | 48 touch, icon 24 | — | onSurfaceVariant | 
| | Starts dictation (02.6). | 
| 5 | Send (disabled) | 48 touch, disc 40, icon 20 | — | disc onSurface 12%, icon onSurface 38% | 
| | Disabled while the field is empty or only spaces. Content description "Send"; announced as disabled. | GlassPane follows the Base Look (glass). The composer is opaque surfaceContainerHigh in both modes; nothing inside it is glass.MotionNone at rest.
### 02.2Focused
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
q1w2e3r4t5y6u7i8o9p0
asdfghjkl
⇧zxcvbnm⌫
?123,.
02.2 · Focused, still emptyLight · Cool wallpaper · Glass on
#### Focused · 02.2
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Composer (focused) | same size | — | 2 inner ring primary | 
| | Ring drawn inside the 56 so nothing shifts. spec decision | 
| 2 | Field | — | body text-base 16/24 Regular | caret primary 2 wide; placeholder stays | 
| 3 | Send | — | — | disabled | 
| | Still disabled: field is empty. | GlassPane follows the Base Look (glass). The composer is opaque surfaceContainerHigh in both modes; nothing inside it is glass. The keyboard is glass too.MotionRing fades in 170 ms (--tl-ease-in), out 110 ms. Pane bottom follows the keyboard (see 01.2).
### 02.3Typed
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
And 2 cups of sugar?
q1w2e3r4t5y6u7i8o9p0
asdfghjkl
⇧zxcvbnm⌫
?123,.
02.3 · Typed, send enabledDark · Warm wallpaper · Glass on
#### Typed · 02.3
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Field | — | body text-base 16/24 Regular | text onSurface | 
| | Sentence case on (textCapSentences). | 
| 2 | Send (enabled) | 48 / 40 | — | primary, onPrimary | 
| | Enabled on the first non-space character. Tap sends, clears the field, keeps focus and keyboard. | GlassPane follows the Base Look (glass). The composer is opaque surfaceContainerHigh in both modes; nothing inside it is glass.MotionDisabled → enabled: colour cross-fade 170 ms on the settle curve.
### 02.4Growing to more lines
10:56
### Note about the heater
 E2B
Write a short note to my landlord. The heater in the living room stopped on Monday evening.
q1w2e3r4t5y6u7i8o9p0
asdfghjkl
⇧zxcvbnm⌫
?123,.
02.4 · Growing: 3 linesDark · Warm wallpaper · Glass off (solid)
#### Growing · 02.4
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Composer | height = field + 8; at 3 lines 104 | — | — | 
| | Corner stays 28. Grows upward; the thread shrinks by the same amount and keeps its bottom in view. | 
| 2 | Field | 3 × 24 + 24 padding = 96 | body text-base 16/24 Regular | — | 
| | Wraps at word boundaries. | 
| 3 | Attach, mic | 48 each | — | — | 
| | Stay on the last line (bottom-aligned), never move up with the text. | 
| 4 | Send | — | — | — | 
| | Bottom-aligned with them. | GlassGlass off: pane is Solid (surfaceContainer 77.5%, no blur). The composer does not change.MotionHeight changes follow each line break directly; no animation spec decision. One relayout per line added, not per character.
### 02.5At the six-line cap
10:56
### Note about the heater
 E2B
Write a short note to my landlord. The heater in the living room stopped on Monday evening and does not turn on at any setting. I checked the breaker and the thermostat batteries. Ask when someone can come to look at it, and say I am home after 5 most days this week, and all of Saturday morning.
q1w2e3r4t5y6u7i8o9p0
asdfghjkl
⇧zxcvbnm⌫
?123,.
02.5 · Six-line cap, scrolling insideLight · Warm wallpaper · Glass on
#### Six-line cap · 02.5
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Composer | max 176 (168 field + 8) | — | — | 
| | Stops growing at 6 lines. | 
| 2 | Field | max 6 × 24 + 24 = 168 | body text-base 16/24 Regular | — | 
| | Beyond 6 lines the text scrolls inside the field. The caret line stays in view; the top fades over 16. | 
| 3 | Scroll indicator | 3 wide, 4 from the right edge | — | onSurfaceVariant 40% | 
| | Standard EditText vertical scrollbar, shown while scrolling, fades after 1 s. | GlassPane follows the Base Look (glass). The composer is opaque surfaceContainerHigh in both modes; nothing inside it is glass.MotionNo animation when the cap is reached.LandscapeCap is 3 lines, so the thread keeps at least one message visible spec decision.
Composer height by line count (font scale 1.0)
| Lines | Field | Composer | Thread at 412 × 915, keyboard down | 
| 1 | 48 | 56 | 687 | 
| 2 | 72 | 80 | 663 | 
| 3 | 96 | 104 | 639 | 
| 4 | 120 | 128 | 615 | 
| 5 | 144 | 152 | 591 | 
| 6 (cap) | 168 | 176 | 567 | 
## Dictation
Tapping the mic turns the composer row into a Listening row. Done puts the words in the field to edit and send; nothing is sent on its own.
### 02.6Listening
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Listening
 Done
02.6 · Listening, nothing heard yetDark · Cool wallpaper · Glass on
#### Listening · 02.6
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Cancel | 48 touch, icon 24 | — | onSurface | 
| | Stops listening and throws away what was heard. The field returns as it was before. | 
| 2 | Level bars | 5 bars, 3 wide, gap 3; height 4–24 | — | primary | 
| | Height follows the microphone level (onRmsChanged), smoothed. In silence the bars rest at 4–6. They only move when sound does: no idle animation, no loop. | 
| 3 | Text | flex; one line | body text-base 16/24 Regular | onSurfaceVariant | 
| | "Listening" until the first words arrive. | 
| 4 | Done | 40 high in 48 touch; padding 0 16; 4 from the right | labelSm text-md 14/20 Medium | secondaryContainer, onSecondaryContainer | 
| | Ends dictation and puts the words in the field (02.8). | GlassPane follows the Base Look (glass). The composer is opaque surfaceContainerHigh in both modes; nothing inside it is glass.MotionRow swap: idle row out 110 ms (--tl-ease-out), Listening row in 170 ms (--tl-ease-in), same 56 height so nothing moves. The keyboard hides while listening spec decision.SilenceAfter 8 s with nothing heard, listening ends on its own and the row returns to idle with a snackbar "Didn't catch that" spec decision.
### 02.7Partial text
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
and how many grams is a cup of brown sugar if I pack it
 Done
02.7 · Listening, partial textLight · Cool wallpaper · Glass off (solid)
#### Partial text · 02.7
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Level bars | — | — | primary | 
| | Following speech. | 
| 2 | Partial text | one line; shows the end | body text-base 16/24 Regular | onSurfaceVariant | 
| | Live partial results. When longer than the row, the start is clipped so the newest words stay visible. | 
| 3 | Done | — | — | — | 
| | Always enabled while listening. | GlassGlass off: pane is Solid (surfaceContainer 77.5%, no blur). The composer does not change.MotionText replaces in place as results update; no fade per word.
### 02.8Done: text in the field
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
And how many grams is a cup of brown sugar if I pack it?
q1w2e3r4t5y6u7i8o9p0
asdfghjkl
⇧zxcvbnm⌫
?123,.
02.8 · Done: text in the fieldDark · Cool wallpaper · Glass on
#### Done · 02.8
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Field | grows like 02.4 | body text-base 16/24 Regular | onSurface | 
| | Final text is inserted at the caret (or appended after existing text with one space). Not sent. Caret at the end, keyboard up, so it can be edited. | 
| 2 | Send | — | — | primary | 
| | Enabled. | 
| 3 | Keyboard | — | — | Glass | 
| | Returns after Done. | GlassPane follows the Base Look (glass). The composer is opaque surfaceContainerHigh in both modes; nothing inside it is glass.MotionListening row out 110 ms, field in 170 ms.
### 02.9Microphone permission needed
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Dictation needs the microphone.
Android asks once. Audio stays on this phone.
Not nowAllow microphone
Ask anything
02.9a · Microphone permission neededLight · Warm wallpaper · Glass on
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Microphone access is off.
Turn it on for Termux Launcher in app settings.
Not nowOpen settings
Ask anything
02.9b · Microphone turned off in settingsDark · Warm wallpaper · Glass on
#### Microphone permission · 02.9a, 02.9b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Notice | inset 8; padding 16; corner 20; buttons row 8 above | title label text-base 16/24 Medium; text bodySm text-md 14/20 Regular | surfaceContainerHigh; text onSurfaceVariant | 
| | Shown above the composer when the mic is tapped without permission. The composer stays idle. | 
| 2 | Action | 40 high, 48 touch | labelSm text-md 14/20 Medium | Not now: primary text; action: secondaryContainer | 
| | 02.9a "Allow microphone" opens the Android permission dialog. 02.9b appears once Android will no longer ask (denied twice): "Open settings" opens the app's settings page. | 
| 3 | Mic | — | — | — | 
| | Stays visible. Tapping it again shows the notice again. | GlassNotice is opaque on the pane in both glass modes.MotionNotice in: 170 ms fade + 8 rise on the settle curve; out 110 ms. Granted → notice leaves and listening starts at once.
### 02.10No speech model
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Dictation needs a speech model on this phone.
Android downloads it once. It works offline after that.
Not nowGet speech model
Ask anything
02.10a · No speech modelDark · Warm wallpaper · Glass off (solid)
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Getting the speech model
You can keep typing. Dictation turns on when it is ready.
Ask anything
02.10b · Speech model downloadingLight · Cool wallpaper · Glass on
#### No speech model · 02.10a, 02.10b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Notice | as 02.9 | title label text-base 16/24 Medium; text bodySm text-md 14/20 Regular | surfaceContainerHigh | 
| | Shown when the mic is tapped and the phone has no on-device speech recognizer for the current language. | 
| 2 | Get speech model / progress | button 40; progress bar 4 high, full width, 12 above | labelSm text-md 14/20 Medium | bar primary on primary 24% | 
| | "Get speech model" asks Android to download it (triggerModelDownload, Android 13+); below 13 it opens the system speech settings. While downloading, the notice shows determinate progress; it leaves on its own when ready. | GlassGlass off: pane is Solid (surfaceContainer 77.5%, no blur). The composer does not change. 02.10b: Pane follows the Base Look (glass). The composer is opaque surfaceContainerHigh in both modes; nothing inside it is glass.MotionProgress bar width changes without easing; no indeterminate loop.
Type tokens used on these sheets
| Token | Size / line | Weight | Source | Android | 
| heading | 28 / 34, tracking −0.02em | Regular 400 | tokens.css --text-2xl, --tracking-display | TextAppearance.Termux.Heading (new) · sans-serif | 
| title | 22 / 28 | Regular 400 | --text-xl (card title 22sp) | M3 titleLarge size override | 
| barTitle | 18 / 24 | Medium 500 | --text-lg | sans-serif-medium | 
| body | 16 / 24 | Regular 400 | --text-base, --leading-body 1.5 | M3 bodyLarge | 
| label | 16 / 24 | Medium 500 | --text-base | M3 titleMedium | 
| bodySm | 14 / 20 | Regular 400 | added M3 bodyMedium; not in tokens.css | M3 bodyMedium | 
| labelSm | 14 / 20 | Medium 500 | added M3 labelLarge | M3 labelLarge (buttons, chip) | 
| meta | 12 / 16 | Regular 400 | --text-sm | M3 bodySmall | 
| code | 13 / 20 | Regular 400 | added code blocks only; --font-mono | Maple Mono if installed, else Typeface.MONOSPACE | 
## Build notes
Field: EditText, maxLines=6 (landscape 3), minLines=1, inputType=textMultiLine|textCapSentences, scrollbars=vertical. Enter inserts a line; only Send sends.
Buttons: MaterialButton icon style (Widget.Material3.Button.IconButton) at 48 × 48; Send is a filled icon button with a 40 disc. Disabled state uses the M3 disabled alphas (12% container, 38% content).
Dictation: SpeechRecognizer.createOnDeviceSpeechRecognizer (Android 12+), EXTRA_PARTIAL_RESULTS=true, EXTRA_PREFER_OFFLINE=true. Level bars from onRmsChanged, mapped −2…10 dB → 4…24 dp, updated only when the value changes.
Permission: RECORD_AUDIO through ActivityResultContracts.RequestPermission. Use shouldShowRequestPermissionRationale to tell 02.9a from 02.9b.
Speech model check: SpeechRecognizer.isOnDeviceRecognitionAvailable and checkRecognitionSupport (Android 13+) decide 02.10a.
Accessibility: Listening row announces "Listening" once; partial text is not announced word by word. Cancel reads "Cancel dictation"; Done reads "Done, put text in field".

