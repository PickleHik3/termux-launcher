
04 · No model and waiting question · Assistant page build spec
Contents01 Page02 Composer03 Model chip04 No model05 Chats list06 Wall entry07 Look
# 04 · No model and waiting question
What the Assistant page shows before a model is on the phone, while one downloads and loads, and when that fails. Model sheet details are on sheet 03.
Wave 1 · 412 × 915 dp frames · Termux Launcher v2 design system
 Numbered callouts Size overlay on frames Fit more (60% scale)Frames are 412 × 915 dp at 1 dp = 1 CSS px when scale is off.
04.1 No model
04.2 Downloading
04.3 Waiting question
04.4 Loading
04.5 Answered
04.6 Errors
Build notes
## No model on the phone
Until a model is downloaded, every chat shows one notice above the composer. The composer stays usable: questions wait and are answered when the model is ready.
### 04.1No model: notice
10:56
### New chat
 No model
## What can I help with?
Answers need a model on this phone.
Gemma E2B is the quicker one. Wi-Fi recommended.
Choose modelDownload
Ask anything
04.1 · No model: noticeDark · Cool wallpaper · Glass on
10:56
### New chat
 No model
## What can I help with?
Answers need a model on this phone.
Gemma E2B is the quicker one. Wi-Fi recommended.
Choose modelDownload
Ask anything
04.1b · No model: notice, glass offLight · Warm wallpaper · Glass off (solid)
#### No model · 04.1, 04.1b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Model chip | 32 in 48 touch; outlined 1 | labelSm text-md 14/20 Medium | outline outline; text onSurfaceVariant | 
| | "No model", no state mark. Tap opens the Model sheet (sheet 03). | 
| 2 | Notice | inset 8 from pane sides, 8 above the composer; padding 16; corner 20 (--radius-md); gap 4 | — | surfaceContainerHigh | 
| | Shown whenever no model is downloaded, in every chat, empty or not. Not dismissible. | 
| | Notice title | 24 line | label text-base 16/24 Medium | onSurface | 
| | "Answers need a model on this phone." | 
| | Notice body | 20 line | bodySm text-md 14/20 Regular | onSurfaceVariant | 
| | "Gemma E2B is the quicker one. Wi-Fi recommended." Wraps; never truncates. | 
| 3 | Choose model | 40 high, 48 touch; padding 0 12 | labelSm text-md 14/20 Medium | primary | 
| | Opens the Model sheet with nothing selected. | 
| 4 | Download | 40 high, 48 touch; padding 0 16; icon 18 | labelSm text-md 14/20 Medium | primary, onPrimary | 
| | Starts the Gemma E2B download straight away and selects it as the chat model. | 
| 5 | Composer | — | — | — | 
| | Stays usable. A question sent now waits (04.3). | GlassPane: Base Look glass. The notice is opaque surfaceContainerHigh (error variants errorContainer) in both modes; no shadow. 04.1b: Pane: Solid material (surfaceContainer 77.5%, no blur). Notice unchanged.MotionNotice enters with the page; no animation of its own. Buttons right-aligned, gap 8.
### 04.2Downloading
10:56
### New chat
 E2B
## What can I help with?
Downloading Gemma E2B
38% · {done} of {size}
Cancel
Ask anything
04.2 · DownloadingLight · Cool wallpaper · Glass on
#### Downloading · 04.2
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Model chip | — | labelSm text-md 14/20 Medium | progress ring primary on 24% primary | 
| | Loading state with a 14 determinate ring showing the download %. Label "E2B". | 
| 2 | Notice | same box as 04.1 | label text-base 16/24 Medium / bodySm text-md 14/20 Regular | surfaceContainerHigh | 
| | Title "Downloading Gemma E2B". Line 2: "38% · {done} of {size}" (sizes come from the download; never hard-coded). | 
| 3 | Progress bar | 4 high, full width, corner full, 8 above | — | track primary 24%; fill primary | 
| | Determinate. Updates at most once a second; no indeterminate sweep. | 
| 4 | Cancel | 40 / 48 touch | labelSm text-md 14/20 Medium | primary | 
| | Stops the download, deletes the partial file, and returns to 04.1. | GlassPane: Base Look glass. The notice is opaque surfaceContainerHigh (error variants errorContainer) in both modes; no shadow.MotionBar width changes with no tween (jumps per update). The notice swaps content in place, 170 ms cross-fade.Leaving the pageThe download continues as a foreground service with the system download notification. The notice reflects it on return.
### 04.3A question sent while downloading
10:56
### Soft-boiled egg
 E2B
How long should I boil an egg for a soft yolk?
Waiting for the model
Downloading Gemma E2B
72% · {done} of {size}
Cancel
Ask anything
04.3 · Question waits for the modelDark · Warm wallpaper · Glass on
#### Question waiting · 04.3
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Your message | — | body text-base 16/24 Regular | primaryContainer | 
| | Sent normally; the field clears. | 
| 2 | Waiting status | 20 line; icon 16; gap 8; right-aligned under the message, 8 above | bodySm text-md 14/20 Regular | onSurfaceVariant | 
| | "Waiting for the model". Static: no animation. | 
| 3 | Notice | — | — | — | 
| | Download progress keeps showing. | 
| 4 | Send | — | — | — | 
| | Stays Send (not Stop): nothing is streaming. A second question also waits; they are answered in order. | GlassPane: Base Look glass. The notice is opaque surfaceContainerHigh (error variants errorContainer) in both modes; no shadow.MotionNone while waiting.CancellingIf the download is cancelled or fails, the question stays in the chat with the status unchanged, and the notice shows its error (04.6).
### 04.4Loading, question still waiting
10:56
### Soft-boiled egg
 E2B
How long should I boil an egg for a soft yolk?
Waiting for the model
Loading Gemma E2B
60%
Ask anything
04.4 · Loading, question still waitingLight · Warm wallpaper · Glass on
#### Loading · 04.4
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Notice | same box; no button | label text-base 16/24 Medium / bodySm text-md 14/20 Regular | surfaceContainerHigh | 
| | Title "Loading Gemma E2B", line 2 "60%". Loading cannot be cancelled here; the Model sheet has Unload. | 
| 2 | Model chip | — | — | — | 
| | Loading ring follows load %. | GlassPane: Base Look glass. The notice is opaque surfaceContainerHigh (error variants errorContainer) in both modes; no shadow.MotionNotice content cross-fades 170 ms from Downloading to Loading.
### 04.5Model ready: the waiting question is answered
10:56
### Soft-boiled egg
 E2B
How long should I boil an egg for a soft yolk?
About 6 minutes in water that is already boiling, then straight into cold water for a minute. The white sets and the yolk stays
Ask anything
04.5 · Model ready: answer startsDark · Cool wallpaper · Glass off (solid)
#### Answer starts · 04.5
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Model chip | — | — | dot success | 
| | Ready. | 
| 2 | Answer | — | body text-base 16/24 Regular | onSurface | 
| | The waiting status is replaced by the answer as it streams (sheet 01, 01.4). | 
| 3 | Stop | — | — | — | 
| | Shown while the answer streams. | GlassPane: Solid material (surfaceContainer 77.5%, no blur). Notice unchanged.MotionNotice exits 110 ms (--tl-ease-out); the thread grows into its space in one relayout. The waiting status is swapped for the first words, no fade.
## Errors and recovery
Each error replaces the notice and offers the action that fixes it. A waiting question stays in the thread through all of them.
### 04.6Download failed, no space, not enough memory
10:56
### Soft-boiled egg
 No model
How long should I boil an egg for a soft yolk?
Waiting for the model
Download failed.
The connection dropped. The download picks up where it stopped.
Choose modelTry again
Ask anything
04.6a · Download failedDark · Cool wallpaper · Glass on
10:56
### New chat
 No model
## What can I help with?
Not enough space for Gemma E2B.
It needs {size} free. {free} is free now.
Free up spaceTry again
Ask anything
04.6b · No spaceLight · Cool wallpaper · Glass off (solid)
10:56
### Soft-boiled egg
 E4B
How long should I boil an egg for a soft yolk?
Waiting for the model
Not enough memory to load Gemma E4B.
Close some apps, or use Gemma E2B.
Use Gemma E2BTry again
Ask anything
04.6c · Not enough memory to loadDark · Warm wallpaper · Glass on
#### Errors · 04.6a, 04.6b, 04.6c
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Error notice | same box as 04.1 | label text-base 16/24 Medium / bodySm text-md 14/20 Regular | errorContainer, onErrorContainer | 
| | Replaces the normal notice. Buttons are outlined in onErrorContainer, 40 / 48 touch. | 
| | Download failed | — | — | — | 
| | "Download failed." / "The connection dropped. The download picks up where it stopped." Actions: Choose model · Try again (resumes). | 
| | No space | — | — | — | 
| | "Not enough space for Gemma E2B." / "It needs {size} free. {free} is free now." Actions: Free up space (opens system Storage settings) · Try again. | 
| | Not enough memory | — | — | — | 
| | "Not enough memory to load Gemma E4B." / "Close some apps, or use Gemma E2B." Actions: Use Gemma E2B (shown only when E2B is downloaded; switches the chat model and loads it) · Try again. | 
| 2 | Model chip (04.6c) | — | — | ring onSurfaceVariant | 
| | Not loaded. | GlassPane: Base Look glass. The notice is opaque surfaceContainerHigh (error variants errorContainer) in both modes; no shadow. 04.6b: Pane: Solid material (surfaceContainer 77.5%, no blur). Notice unchanged.MotionError notice cross-fades in 170 ms. Announced once (accessibilityLiveRegion=polite).
## Build notes
Notice: one MaterialCardView (stroke 0, corner 20, cardBackgroundColor = colorSurfaceContainerHigh or colorErrorContainer) between the thread and the composer, inside the pane. One view with four content states: no model, downloading, loading, error. LinearProgressIndicator (track 4, determinate) for progress.
Source of truth: the shared chat model service (the one the local API uses) exposes model state; the page, the chip and the notice observe it. The page never starts a second download.
Waiting questions: stored in the chat as normal messages with status = WAITING; when the model becomes Ready they are answered oldest first. They survive app restarts.
Wi-Fi: Download does not block on mobile data; the body line recommends Wi-Fi. The download itself uses the system download path so it survives the page closing.
Placeholders: {size}, {done} and {free} are filled at runtime from the download and StatFs; the sheet does not fix model sizes.

