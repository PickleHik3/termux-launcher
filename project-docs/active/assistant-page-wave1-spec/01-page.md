
01 · Page anatomy and chat states · Assistant page build spec
Contents01 Page02 Composer03 Model chip04 No model05 Chats list06 Wall entry07 Look
# 01 · Page anatomy and chat states
The layout of the Assistant page and every state a chat can be in. Build the page from this sheet; the composer, model and no-model details are on sheets 02 to 04.
Wave 1 · 412 × 915 dp frames · Termux Launcher v2 design system
 Numbered callouts Size overlay on frames Fit more (60% scale)Frames are 412 × 915 dp at 1 dp = 1 CSS px when scale is off.
Anatomy
01.1 Empty
01.2 Typing
01.3 Waiting
01.4 Streaming
01.5 Finished
01.6 Markdown
01.7 Stopped
01.8 Error
01.9 Cut-off
01.10 Jump to latest
Build notes
## Page anatomy
The Assistant page is a wall page between Home 1 and Terminal. It has the launcher status pill, the page dots, and one glass page surface holding the top bar, the thread and the composer.
### 01.0Anatomy
Numbers on the frame match the table. Measurements are for the 412 × 915 dp device of record at font scale 1.0.
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Scooped cups weigh more than spooned and levelled ones, so weigh it if the recipe needs to be exact.
Ask anything
01.0 · Anatomy (finished answer)Dark · Cool wallpaper · Glass on
#### Anatomy · 01.0
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Status pill | 32 high, top 8, side 12 | existing | Glass, onSurface | 
| | Launcher chrome, unchanged. Shown with time and system icons only. | 
| 2 | Page dots | row 20; dot 6, current 8; gap 8 | — | white 50% / 100% | 
| | Same dots as every wall page; the Assistant page is one more dot. | 
| 3 | Page surface (pane) | x 12…400 (388 wide), y 72…879; corner 24; bottom gap 8 | — | Glass: surfaceContainerHigh 34% | 
| | Hosts bar, thread and composer. Corner 24 = --radius-lg. | 
| 4 | Top bar | 56 high, padding 4 | — | transparent on the pane | 
| | No divider. Never scrolls away. | 
| 5 | Chats button | 48 touch, icon 24 | — | onSurface | 
| | Opens the Chats list (sheet 05). | 
| 6 | Chat title | flex, margin 0 4 | barTitle text-lg 18/24 Medium | onSurface | 
| | One line, ellipsized at the end. "New chat" until the first answer names it. | 
| 7 | Model chip | 32 high in a 48 touch area; padding 12 / 6; gap 6 | labelSm text-md 14/20 Medium | surfaceContainerHighest, onSurface | 
| | States on sheet 03. | 
| 8 | New chat button | 48 touch, icon 24 | — | onSurface | 
| | Starts an empty chat. Disabled while the current chat is empty. | 
| 9 | Your message | max 84% of 356; padding 10 / 16; corners 20 20 6 20 | body text-base 16/24 Regular | primaryContainer, onPrimaryContainer | 
| | Right-aligned. Wraps; never truncates. | 
| 10 | Answer | full width 356; paragraph gap 12 | body text-base 16/24 Regular | onSurface on the pane | 
| | No bubble. Markdown: see 01.6. | 
| 11 | Answer actions | 3 × 48 touch, icon 20; row pulled up 8, left −14 so icons align to text | — | onSurfaceVariant | 
| | Copy, Share, Try again. Only under finished answers. | 
| 12 | Composer | min 56; corner 28; inset 8 from the pane; padding 4 | — | surfaceContainerHigh (opaque) | 
| | Pinned to the bottom of the pane. | 
| 13 | Attach | 48 touch, icon 24 | — | onSurfaceVariant | 
| | Present and enabled; its sheet is wave 2. | 
| 14 | Field | 220 wide at 412; min 48; padding 12 / 4 | body text-base 16/24 Regular | placeholder onSurfaceVariant | 
| | Placeholder "Ask anything". Growth on sheet 02. | 
| 15 | Mic | 48 touch, icon 24 | — | onSurfaceVariant | 
| | Dictation, sheet 02. | 
| 16 | Send | 48 touch, disc 40, icon 20 | — | primary, onPrimary | 
| | Becomes Stop while an answer streams. | GlassPane follows the Base Look: surfaceContainerHigh at 34% (light: surfaceContainer at 34%), blur 8, grain 18%, hairline rim. Everything inside the pane is opaque.MotionPages settle on the launcher page spring (ω 18/s), never a timed slide. Nothing on this page loops.ThreadFills between bar and composer (687 at rest). Padding 8 top, 16 sides, 16 bottom; 24 between messages. Pinned to the bottom: newest last.
Vertical budget at 412 × 915 dp, keyboard down
| Band | Height | From | Note | 
| System status inset (status pill inside) | 52 | top | Pill 32 high, 8 from the top | 
| Page dots | 20 | 52 | | 
| Pane: top bar | 56 | 72 | | 
| Pane: thread | 687 (flex) | 128 | Shrinks with the keyboard, the composer's growth and notices | 
| Pane: composer area | 64 | 815 | 56 composer + 8 inset | 
| Gap under the pane | 8 | 879 | | 
| Gesture inset | 28 | 887 | System navigation | 
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
## Chat states
Each state is shown once; frames rotate through dark and light, cool and warm wallpapers, and glass on and off. Only what changes from the anatomy is called out.
### 01.1Empty chat
A new chat with earlier chats on the phone. No starter prompts, no suggestion chips.
10:56
### New chat
 E2B
## What can I help with?
Recent chats
 Rename photos by the date they were takenToday
 Lease notice periodYesterday
 Bread starter ratiosMonday
Ask anything
01.1a · Empty chatDark · Cool wallpaper · Glass on
10:56
### New chat
 E2B
## What can I help with?
Recent chats
Rename photos by the date they were takenTodayLease notice periodYesterdayBread starter ratiosMonday
Ask anything
01.1b · Empty chat, glass offLight · Warm wallpaper · Glass off (solid)
#### Empty chat · 01.1a, 01.1b
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Chat title | — | barTitle text-lg 18/24 Medium | onSurface | 
| | Reads "New chat". New chat button disabled (38% onSurface). | 
| 2 | Model chip | — | labelSm text-md 14/20 Medium | — | 
| | Shows the current state; Ready here. | 
| 3 | Heading | margin 0 20, 24 below | heading text-2xl 28/34 Regular −0.02em | onSurface | 
| | "What can I help with?" Wraps to 2 lines at large font scale. | 
| 4 | Rail label | 20 high, margin 0 20 8 | labelSm text-md 14/20 Medium | onSurfaceVariant | 
| | "Recent chats". Hidden, with the rail, when there are no chats. | 
| 5 | Recent chat card | 152 × min 72; padding 12 / 14; corner 16; gap 8 | title labelSm text-md 14/20 Medium 2 lines max; when meta text-sm 12/16 Regular | surfaceContainerHigh; when onSurfaceVariant | 
| | The last 3 chats, newest first. Tap opens that chat. Rail starts at 16 and scrolls sideways; no starter prompts, no suggestion chips. | 
| 6 | Composer | — | — | — | 
| | Idle. Send disabled. | GlassPane follows the Base Look: surfaceContainerHigh at 34% (light: surfaceContainer at 34%), blur 8, grain 18%, hairline rim. Everything inside the pane is opaque. 01.1b: Glass off: Solid material, surfaceContainer at 77.5%, no blur, no grain, rim kept. Nothing else changes.MotionHeading and rail are static. When the first message is sent they cross-fade out (110 ms, --tl-ease-out) as the message enters.Block positionHeading and rail sit at the bottom of the thread area, 16 above the composer, so they stay near the thumb.
### 01.2Typing with the keyboard up
10:56
### New chat
 E2B
## What can I help with?
What is the difference between RAM and
q1w2e3r4t5y6u7i8o9p0
asdfghjkl
⇧zxcvbnm⌫
?123,.
01.2 · Typing, keyboard upDark · Cool wallpaper · Glass on
#### Typing with keyboard · 01.2
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Pane | bottom edge moves to 8 above the keyboard | — | — | 
| | Thread shrinks; the bar stays put. The heading moves with the pane bottom. | 
| 2 | Composer | stays 8 inside the pane | body text-base 16/24 Regular | focus ring 2 primary | 
| | Text in onSurface; caret primary 2 wide. | 
| 3 | Send | 48 / 40 | — | primary, onPrimary | 
| | Enabled once the field has a non-space character. | 
| 4 | Keyboard | rows 48, gap 3, corner 24, bottom 4 | existing | Glass | 
| | Whichever keyboard is active; the launcher keyboard is shown. Page dots stay. | GlassPane and keyboard both glass; the thread behind the keyboard is not drawn.MotionThe pane follows the keyboard frame by frame through WindowInsetsAnimationCompat, applied as translationY on the pane bottom group; one relayout when the animation ends. No layout work per frame.
### 01.3Waiting for the first word
10:56
### RAM and storage
 E2B
What is the difference between RAM and storage?
Working on it
Ask anything
01.3 · Waiting for the first wordLight · Cool wallpaper · Glass on
#### Waiting for the first word · 01.3
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Your message | — | body text-base 16/24 Regular | primaryContainer | 
| | Appears as soon as Send is tapped. The field clears. | 
| 2 | Status line | 20 high, where the answer will start | bodySm text-md 14/20 Regular | onSurfaceVariant | 
| | "Working on it". Static text: no dots, no shimmer. Replaced by the first word. | 
| 3 | Stop | 48 / 40, icon 20 (filled square) | — | primary, onPrimary | 
| | Active from the moment of sending. Stopping now removes the empty answer and keeps your message, with Try again under it. | GlassPane follows the Base Look: surfaceContainerHigh at 34% (light: surfaceContainer at 34%), blur 8, grain 18%, hairline rim. Everything inside the pane is opaque.MotionYour message: alpha 0→1 in 170 ms (--tl-ease-in) and 8 → 0 rise on the settle curve, 220 ms. Send → Stop: icon cross-fade 170 ms.
### 01.4Streaming
10:56
### RAM and storage
 E2B
What is the difference between RAM and storage?
RAM holds what the phone is working on right now. It is fast, and it empties when the phone restarts.
Storage keeps your apps, photos and files until you delete them. It is slower than RAM but much
Ask anything
01.4 · Streaming, Stop shownDark · Warm wallpaper · Glass on
#### Streaming · 01.4
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Answer (growing) | full width | body text-base 16/24 Regular | onSurface | 
| | Text appends at the end. No caret, no fade on new words, no pulsing. Markdown is formatted as it arrives. | 
| 2 | Stop | 48 / 40 | — | primary, onPrimary | 
| | Tap stops at the last word received; see 01.7. | 
| 3 | Model chip | — | — | — | 
| | Stays Ready while answering. | GlassPane follows the Base Look: surfaceContainerHigh at 34% (light: surfaceContainer at 34%), blur 8, grain 18%, hairline rim. Everything inside the pane is opaque.MotionAppend at most once per frame (coalesce tokens). While the person is at the bottom, the thread keeps the newest line in view by scrolling, not animating. If they scroll up, it stops following and Jump to latest appears (01.10).Leaving the pageSwiping to another page, locking the phone or opening another app does not stop the answer. It keeps going in the background and is saved to the chat. On return the thread shows the finished or still-growing answer.
### 01.5Finished answer with actions
10:56
### Flour to grams
 E2B
Convert 3 cups of flour to grams.
About 375 g, using 125 g per cup of all-purpose flour.
Scooped cups weigh more than spooned and levelled ones, so weigh it if the recipe needs to be exact.
Ask anything
01.5 · Finished answer with actionsLight · Cool wallpaper · Glass off (solid)
#### Finished answer · 01.5
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Answer | — | body text-base 16/24 Regular; bold = Semibold 600 | onSurface | 
| 2 | Actions | 3 × 48, icon 20 | — | onSurfaceVariant | 
| | Copy: copies the answer as plain text, toast-free (the system clipboard shows its own confirmation on Android 13+; below 13 show a snackbar "Copied"). Share: system share sheet with the answer text. Try again: replaces this answer with a new one. | 
| 3 | Send | — | — | disabled: onSurface 12% / 38% | 
| | Back to Send; disabled while the field is empty. | GlassGlass off: Solid material, surfaceContainer at 77.5%, no blur, no grain, rim kept. Nothing else changes.MotionActions row fades in over 170 ms when the answer ends. Older answers keep their actions.
### 01.6Answer with lists and a code block
10:56
### Rename photos by the date they were taken
 E2B
How do I rename my photos by the date they were taken?
Use exiftool. It reads the date the photo was taken and renames each file to match.
Install it: pkg install exiftool
Go to the folder with the photos.
Run the command below.
ShellCopy
exiftool '-FileName<DateTimeOriginal' -d '%Y-%m-%d_%H%M%S%%-c.%%e' .
Files get names like 2026-10-29_184512.jpg.
Two photos from the same second get -1, -2 added.
Ask anything
01.6 · Answer with lists and a code blockDark · Cool wallpaper · Glass on
#### Markdown answer · 01.6
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Long title | flex | barTitle text-lg 18/24 Medium | — | 
| | Ellipsized at one line: "Rename photos by the date th…". | 
| 2 | Lists | indent 24; item gap 4; block gap 12 | body text-base 16/24 Regular | markers onSurfaceVariant | 
| | Numbered and bulleted. Nested lists indent a further 24. Inline code: code mono 14, padding 1 / 5, corner 6, on surfaceContainerHighest. | 
| 3 | Code block | full width; corner 12; header 40; padding 0 16 14 | label meta text-sm 12/16 Regular; code code mono 13/20 | surfaceContainerHighest; label onSurfaceVariant | 
| | Lines never wrap: the block scrolls sideways on its own (HorizontalScrollView), with a 28 fade at the right edge when clipped. Language label from the fence; "Code" when none. | 
| 4 | Copy (code) | 40 high in a 48 touch area; icon 18; gap 6 | labelSm text-md 14/20 Medium | onSurface | 
| | Copies only the code. Label changes to "Copied" for 2 s, then back. No run, no paste-to-terminal. | 
| 5 | Actions | — | — | — | 
| | Copy here copies the whole answer, code included. | GlassPane follows the Base Look: surfaceContainerHigh at 34% (light: surfaceContainer at 34%), blur 8, grain 18%, hairline rim. Everything inside the pane is opaque. Code blocks stay opaque.MotionNone beyond the Copy label swap (instant).Supported markdownParagraphs, bold, italic, bulleted and numbered lists, inline code, fenced code blocks. Headings render as label text-base 16/24 Medium. Links are plain primary text with underline; tap opens the browser. Tables and images: shown as plain text (wave 2).
### 01.7Stopped
10:56
### Note about the heater
 E2B
Write a short note to my landlord about the broken heater.
Hi Sam,
The heater in the living room stopped working on Monday evening. It does not turn on at any setting, and the
Stopped
Ask anything
01.7 · StoppedLight · Warm wallpaper · Glass on
#### Stopped · 01.7
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Answer (partial) | — | body text-base 16/24 Regular | onSurface | 
| | Keeps every word received. Ends mid-sentence as-is; no ellipsis added. | 
| 2 | "Stopped" mark | margin-left 14 (aligns with text), 6 before icons | meta text-sm 12/16 Regular | onSurfaceVariant | 
| | Sits at the start of the actions row. | 
| 3 | Actions | — | — | — | 
| | Copy, Share, Try again. Try again replaces the partial answer. | GlassPane follows the Base Look: surfaceContainerHigh at 34% (light: surfaceContainer at 34%), blur 8, grain 18%, hairline rim. Everything inside the pane is opaque.MotionStop → Send cross-fade 170 ms. Mark and actions appear together, 170 ms fade.
### 01.8Error: the model stopped mid-answer
10:56
### Lease notice period
 E2B
What does the lease say about the notice period?
The lease asks for two months' written notice before you move out. Notice has to
The model stopped.Try again
Ask anything
01.8 · Error: the model stoppedDark · Cool wallpaper · Glass off (solid)
#### Error · 01.8
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Answer (partial) | — | body text-base 16/24 Regular | — | 
| | Whatever arrived before the model stopped is kept. | 
| 2 | Error row | padding 12 / 16; corner 16; icon 20; gap 10 | bodySm text-md 14/20 Regular | errorContainer, onErrorContainer | 
| | "The model stopped." with an outlined "Try again" button (40 high, 48 touch). Try again reloads the model if needed, then retries this answer. | GlassGlass off: Solid material, surfaceContainer at 77.5%, no blur, no grain, rim kept. Nothing else changes.MotionError row fades in 170 ms. Screen readers hear "The model stopped." once (accessibilityLiveRegion=polite).ChipThe chip goes to Not loaded when the model process has exited. The other errors (not enough memory, download failed, no space) are on sheet 03 and 04.
### 01.9Long chat: where the model stops seeing
10:56
### Bread starter ratios
 E2B
How much flour and water do I feed it?
Equal weights of flour and water, about 50 g each, once a day while it lives on the counter.
Older messages are past what the model can see
It smells sour and has a grey liquid on top. Is it ruined?
No. The grey liquid is alcohol from a hungry starter. Pour it off or stir it in, then feed it. A sour smell is normal.
Ask anything
01.9 · Long chat: context cut-off lineDark · Warm wallpaper · Glass off (solid)
#### Context cut-off · 01.9
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Cut-off line | hairline 1 each side, min 12; gap 12; text max 290; 24 above and below like a message | meta text-sm 12/16 Regular, centred | lines outlineVariant; text onSurfaceVariant | 
| | "Older messages are past what the model can see". Placed above the oldest message that still fits the model's context. Recomputed after each answer; at most one line per chat. Wraps to 2 lines at large font scale; the lines centre on the first line. | GlassGlass off: Solid material, surfaceContainer at 77.5%, no blur, no grain, rim kept. Nothing else changes.MotionWhen the line moves down after an answer, it jumps; no animation.Not interactiveThe line has no tap action and no help link. Messages above it look the same as messages below it.
### 01.10Scrolled up: Jump to latest
10:56
### Rename photos by the date they were taken
 E2B
Install it: pkg install exiftool
Go to the folder with the photos.
Run the command below.
ShellCopy
exiftool '-FileName<DateTimeOriginal' -d '%Y-%m-%d_%H%M%S%%-c.%%e' .
Ask anything
01.10 · Scrolled up, Jump to latestLight · Cool wallpaper · Glass on
#### Scrolled up · 01.10
| # | Element | Size (dp) | Type | Colour role | 
| 1 | Jump to latest | disc 40 in a 48 touch area; centred; 8 above the composer | — | secondaryContainer, onSecondaryContainer; 1 ring outlineVariant | 
| | Shown once the newest message is more than one screen of thread below. Tap scrolls to the end (smooth scroll) and hides it. Content description "Jump to latest". | GlassPane follows the Base Look: surfaceContainerHigh at 34% (light: surfaceContainer at 34%), blur 8, grain 18%, hairline rim. Everything inside the pane is opaque. The button is opaque; no shadow (glass rule).MotionIn: alpha 170 ms + scale 0.9 → 1 on the settle curve. Out: 110 ms. While an answer streams, the button shows and the thread stops following.
## Build notes
Page: a new wall page kind, AssistantPage, hosted by the same page container as Home and Workspace pages. The pane is the launcher's existing glass surface (the one widgets and the terminal pane use, driven by SurfaceMaterials / GlobalGlass), so the Base Look, the style override and Solid all come for free.
Thread: RecyclerView with LinearLayoutManager(stackFromEnd = true). Item types: your message, answer, status line, cut-off line, error row, stopped mark. A finished answer is one item; its markdown is rendered to a Spannable, with each code block as its own child view so it can scroll sideways.
Streaming: append to the last answer item's Editable and call notifyItemChanged(last, PAYLOAD_APPEND) at most once per frame. Do not rebuild earlier items.
Composer: EditText with maxLines=6, inputType=textMultiLine|textCapSentences; the Enter key adds a line, Send sends. Buttons are MaterialButton icon style with 48 touch targets.
Keyboard: the page reads IME insets (system keyboard) and the launcher keyboard's height callback; both move the pane bottom.
Accessibility: each message is one focusable node (your message reads "You: …"; an answer reads its text, then its actions). The answer being streamed is a polite live region that announces only when it finishes.

