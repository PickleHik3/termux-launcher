
03 · Model chip and Model sheet · Assistant page build spec
Contents01 Page02 Composer03 Model chip04 No model05 Chats list06 Wall entry07 Look
# 03 · Model chip and Model sheet
How the chat model is shown in the top bar, and how it is chosen, downloaded, loaded and unloaded. This is the launcher's one shared chat model.
Wave 1 · 412 × 915 dp frames · Termux Launcher v2 design system
 Numbered callouts Size overlay on frames Fit more (60% scale)Frames are 412 × 915 dp at 1 dp = 1 CSS px when scale is off.
03.1 Chip states
03.2 Ready / not downloaded
03.3 Downloading
03.4 Loading
03.5 Unload
03.6 Download failed
03.7 No space
03.8 No memory
Build notes
## Model chip
The chip in the top bar names the chat model and its state. Tapping it opens the Model sheet in every state.
### 03.1Chip states
The four states side by side, then each in context.
E2BReady
E2BLoading
E2BNot loaded
No modelNo model
Chip statesDark · Cool wallpaper · Glass on
E2BReady
E2BLoading
E2BNot loaded
No modelNo model
Chip statesLight · Warm wallpaper · Glass off (solid)
#### Model chip · 03.1
| # | Element | Size (dp) | Type | Colour role | 
| | Chip | 32 high in a 48 touch area; padding 0 6 0 12; gap 6; chevron 18 | labelSm text-md 14/20 Medium | surfaceContainerHighest, onSurface; chevron onSurfaceVariant | 
| | Label is the short model name: "E2B" or "E4B". Tap opens the Model sheet in every state. | 
| | Ready | dot 8 | — | dot: success (termux_chip_done) | 
| | Model loaded in memory; answers start at once. | 
| | Loading | ring 14, stroke ≈ 2.4, determinate | — | ring primary on primary 24% | 
| | Shows load progress. A question sent now waits and is answered when loading ends. | 
| | Not loaded | ring 8, stroke 1.5 | — | onSurfaceVariant | 
| | Downloaded but not in memory (unloaded, or the system reclaimed it). Sending a question loads it first. | 
| | No model | outlined; no indicator | labelSm text-md 14/20 Medium | 1 outline outline; label onSurfaceVariant | 
| | "No model". Nothing downloaded; sheet 04 shows the notice. | GlassThe chip is opaque in both glass modes; it sits on the pane like other bar controls.MotionState changes swap the indicator with a 170 ms cross-fade. Loading ring advances with progress, never spins on its own. Content description: "Chat model: E2B, ready" (and so on).
### 03.1b–dChip states in context
Ready is shown on sheet 01 (01.0).
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
03.1b · Chip: LoadingDark · Warm wallpaper · Glass on
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
03.1c · Chip: Not loadedLight · Cool wallpaper · Glass off (solid)
10:56
### New chat
 No model
## What can I help with?
Answers need a model on this phone.
Gemma E2B is the quicker one. Wi-Fi recommended.
Choose modelDownload
Ask anything
03.1d · Chip: No modelDark · Cool wallpaper · Glass on
## Model sheet
Two models, one selected. Each row shows where that model is: on the phone, in memory, or on its way.
### 03.2E2B ready, E4B not downloaded
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
### Chat model
Gemma E2B
Quick answers, lighter on battery
Ready
Unload
Gemma E4B
Better with long files and code
Not downloaded
Download
Models live on this phone, and so do your chats.
This is the launcher's one chat model. The local API uses the same one.
03.2 · Sheet: E2B ready, E4B not downloadedDark · Cool wallpaper · Glass on
#### Sheet: ready / not downloaded · 03.2
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Sheet | full width; top corners 28; padding 0 16 44 (gesture inset + 16) | — | surfaceContainerLow; scrim #000 45% dark / 32% light | 
| | Modal bottom sheet with drag handle. Height wraps content. Drag down, tap the scrim, or Back closes it. | 
| 2 | Title | handle 32 × 4 at 22 from the top, 14 below; title margin 0 8 8 | title text-xl 22/28 Regular | onSurface; handle onSurfaceVariant 40% | 
| | "Chat model". | 
| 3 | Radio | 48 touch, ring 20, stroke 2, dot 10 | — | selected primary; unselected onSurfaceVariant | 
| | Selects the chat model. A model that is not downloaded cannot be selected: radio at 38%. | 
| 4 | Row text and state | row min 88; padding 8 0; name 24, desc 20, state 16 + 4 above | name label text-base 16/24 Medium; desc bodySm text-md 14/20 Regular; state meta text-sm 12/16 Regular | onSurface / onSurfaceVariant | 
| | State words: Ready, Downloaded, Not downloaded, Downloading n%, Loading n%. Rows divided by 1 outlineVariant. | 
| 5 | Unload | 40 high, 48 touch; padding 0 16 | labelSm text-md 14/20 Medium | 1 outline outlineVariant; text onSurface | 
| | Only on the loaded model. | 
| 6 | Radio (disabled) | — | — | 38% | 
| 7 | Download | 40 high; icon 18; gap 8 | labelSm text-md 14/20 Medium | secondaryContainer, onSecondaryContainer | 
| | Starts the download. Over mobile data, Android's download rules apply; no extra prompt spec decision. | 
| 8 | Note: storage | margin 12 8 0 | bodySm text-md 14/20 Regular | onSurfaceVariant | 
| | "Models live on this phone, and so do your chats." | 
| 9 | Note: shared model | margin 4 8 0 | bodySm text-md 14/20 Regular | onSurfaceVariant | 
| | "This is the launcher's one chat model. The local API uses the same one." Changing it here changes it for the local API too. | GlassThe page behind keeps its glass; the sheet is opaque surfaceContainerLow and is never glass.MotionSheet in on the glass enter spring (damping 0.75, stiffness 400); out on the exit spring (0.55 / 300). Scrim alpha 170 ms in, 110 ms out.
### 03.3E4B downloading
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
### Chat model
Gemma E2B
Quick answers, lighter on battery
Ready
Unload
Gemma E4B
Better with long files and code
Downloading 42%
Cancel
Models live on this phone, and so do your chats.
This is the launcher's one chat model. The local API uses the same one.
03.3 · Sheet: E4B downloadingLight · Cool wallpaper · Glass on
#### Sheet: downloading · 03.3
| # | Element | Size (dp) | Type | Colour role | 
| 1 | State | — | meta text-sm 12/16 Regular | onSurfaceVariant | 
| | "Downloading 42%". Updates at most once per whole percent. | 
| 2 | Progress | 4 high, full text width, 8 above, corner full | — | primary on primary 24% | 
| | Determinate. Download continues with the sheet closed and the page left; the system notification shows it too. | 
| 3 | Cancel | 40 high, 48 touch; padding 0 12 | labelSm text-md 14/20 Medium | text primary | 
| | Stops and deletes the partial file; row returns to Not downloaded. | GlassThe page behind keeps its glass; the sheet is opaque surfaceContainerLow and is never glass.MotionBar width set directly on each update; no easing, no indeterminate loop.
### 03.4E4B loading
10:56
### Flour to grams
 E4B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
### Chat model
Gemma E2B
Quick answers, lighter on battery
Downloaded
Gemma E4B
Better with long files and code
Loading 63%
Models live on this phone, and so do your chats.
This is the launcher's one chat model. The local API uses the same one.
03.4 · Sheet: E4B loadingDark · Warm wallpaper · Glass off (solid)
#### Sheet: loading · 03.4
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Radio | — | — | selected primary | 
| | Selecting a downloaded model loads it at once and unloads the other; only one is in memory. | 
| 2 | State | — | meta text-sm 12/16 Regular | onSurfaceVariant; ring 14 primary | 
| | "Loading 63%". | 
| 3 | Progress | 4 high | — | primary | 
| | Same bar as downloading. | 
| 4 | Chip | — | — | — | 
| | Loading ring with the new name, "E4B". | GlassPage behind is glass off (Solid); the sheet looks the same either way.MotionWhen loading ends, state becomes Ready with a dot (170 ms cross-fade). The sheet stays open.
### 03.5Unload
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
### Chat model
Gemma E2B
Quick answers, lighter on battery
Ready
Unload
Gemma E4B
Better with long files and code
Downloaded
Models live on this phone, and so do your chats.
This is the launcher's one chat model. The local API uses the same one.
03.5a · Sheet: unloadLight · Warm wallpaper · Glass on
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
### Chat model
Gemma E2B
Quick answers, lighter on battery
Downloaded · loads when you ask
Gemma E4B
Better with long files and code
Downloaded
Models live on this phone, and so do your chats.
This is the launcher's one chat model. The local API uses the same one.
03.5b · Sheet: after unloadDark · Warm wallpaper · Glass on
#### Unload · 03.5a, 03.5b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Unload | 40 / 48 | labelSm text-md 14/20 Medium | outlined | 
| | Frees the memory now. The model stays downloaded and selected. | 
| 2 | State after unload | — | meta text-sm 12/16 Regular | 8 ring onSurfaceVariant | 
| | "Downloaded · loads when you ask". No button: the next question loads it. | GlassThe page behind keeps its glass; the sheet is opaque surfaceContainerLow and is never glass.MotionUnload button fades out 110 ms; state swaps in 170 ms. Chip goes to Not loaded (03.5b).Local APIIf the local API is using the model, Unload still unloads it; a request on the API loads it again.
### 03.6Download failed
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
### Chat model
Gemma E2B
Quick answers, lighter on battery
Ready
Unload
Gemma E4B
Better with long files and code
Download failed. Check the connection.
Try again
Models live on this phone, and so do your chats.
This is the launcher's one chat model. The local API uses the same one.
03.6 · Sheet: download failedDark · Cool wallpaper · Glass off (solid)
#### Download failed · 03.6
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Error state | icon 16, gap 8 | meta text-sm 12/16 Regular | error | 
| | "Download failed. Check the connection." Partial file kept so Try again resumes. | 
| 2 | Try again | 40 / 48; icon 18 | labelSm text-md 14/20 Medium | secondaryContainer | 
| | Resumes the download. | GlassPage behind is glass off (Solid).MotionError state in 170 ms.
### 03.7Not enough space
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
### Chat model
Gemma E2B
Quick answers, lighter on battery
Ready
Unload
Gemma E4B
Better with long files and code
Not enough space. Needs {size} free.
Free up space
Models live on this phone, and so do your chats.
This is the launcher's one chat model. The local API uses the same one.
03.7 · Sheet: not enough spaceLight · Cool wallpaper · Glass on
#### Not enough space · 03.7
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Error state | — | meta text-sm 12/16 Regular | error | 
| | "Not enough space. Needs {size} free." {size} is the remaining bytes the download needs, formatted by Formatter.formatShortFileSize. Checked before the download starts and when it fails with no space. | 
| 2 | Free up space | 40 / 48 | labelSm text-md 14/20 Medium | secondaryContainer | 
| | Opens Android storage settings (ACTION_INTERNAL_STORAGE_SETTINGS). Coming back re-checks; if there is room the row returns to Download. | GlassThe page behind keeps its glass; the sheet is opaque surfaceContainerLow and is never glass.MotionNone beyond the state swap.SizesNo model size is printed on this sheet; real sizes come from the model manifest at build time.
### 03.8Not enough memory to load
10:56
### Flour to grams
 E4B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Ask anything
### Chat model
Gemma E2B
Quick answers, lighter on battery
Downloaded
Use this one
Gemma E4B
Better with long files and code
Not enough memory to load. Close some apps, or use Gemma E2B.
Try again
Models live on this phone, and so do your chats.
This is the launcher's one chat model. The local API uses the same one.
03.8a · Sheet: not enough memory to loadDark · Warm wallpaper · Glass on
10:56
### Lease notice period
 E4B
What does the lease say about the notice period?
Not enough memory to load Gemma E4B.
Close some apps, or switch to Gemma E2B.
Use Gemma E2BTry again
Ask anything
03.8b · In the chat: not enough memoryLight · Warm wallpaper · Glass off (solid)
#### Not enough memory · 03.8a, 03.8b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Error state (sheet) | wraps to 2 lines | meta text-sm 12/16 Regular | error | 
| | "Not enough memory to load. Close some apps, or use Gemma E2B." Selected stays on E4B. | 
| 2 | Try again | 40 / 48 | labelSm text-md 14/20 Medium | secondaryContainer | 
| | Tries to load again. | 
| 3 | Use this one | 40 / 48 | labelSm text-md 14/20 Medium | text primary | 
| | On the other downloaded row: selects and loads it. | 
| | Notice in the chat (03.8b) | inset 8; padding 16; corner 20 | title label text-base 16/24 Medium; text bodySm text-md 14/20 Regular | errorContainer, onErrorContainer | 
| | Shown above the composer when a question was sent and the load failed. The question stays in the thread and is answered once a model loads. Buttons: "Use Gemma E2B" (text), "Try again" (outlined in onErrorContainer). | Glass03.8a glass on; 03.8b glass off. Notice and sheet are opaque in both.MotionNotice in 170 ms fade + 8 rise; out 110 ms.
Row state → controls
| State | Radio | State line | Progress | End button | Chip (if selected) | 
| Not downloaded | disabled 38% | Not downloaded | — | Download (tonal) | No model | 
| Downloading n% | disabled 38% | Downloading n% | determinate | Cancel (text) | No model | 
| Downloaded | enabled | Downloaded | — | — (other row: "Use this one" only after a memory error) | Not loaded | 
| Loading n% | selected | Loading n% with ring | determinate | — | Loading | 
| Ready | selected | Ready with dot | — | Unload (outlined) | Ready | 
| Download failed | disabled 38% | error ink | — | Try again (tonal) | unchanged | 
| Not enough space | disabled 38% | error ink | — | Free up space (tonal) | unchanged | 
| Not enough memory | selected | error ink, 2 lines | — | Try again (tonal) | Not loaded | 
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
Chip: a MaterialButton (tonal style, 32 min height, insetTop/Bottom 8 for the 48 touch) with a start icon that swaps between a dot drawable, a CircularProgressIndicator (14, determinate, track 2.4) and a ring drawable; end chevron as iconGravity=textEnd or a compound drawable.
Sheet: BottomSheetDialog with Widget.Material3.BottomSheet.Modal, drag handle on. Rows: MaterialRadioButton (single selection held by the sheet), two TextViews, a state line, a LinearProgressIndicator (track 4, determinate), and an end MaterialButton (tonal, outlined or text).
State source: one shared model service (the same one behind the local API) exposes per-model state: NOT_DOWNLOADED, DOWNLOADING(p), DOWNLOADED, LOADING(p), READY, and errors DOWNLOAD_FAILED, NO_SPACE, NO_MEMORY. The chip and the sheet both observe it; nothing here keeps its own copy.
Downloads: run in a foreground service or WorkManager job with a progress notification, so they survive leaving the page.
Accessibility: each row is one radio node: "Gemma E4B, Better with long files and code, Downloading 42 percent". Errors are announced once as polite live regions.

