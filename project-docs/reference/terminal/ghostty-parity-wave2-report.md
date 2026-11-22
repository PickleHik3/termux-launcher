# Ghostty parity wave 2: lead report (uncommitted)

Branch feat/ghostty-parity, worktree /home/amalvajdan/Projects/termux-launcher/app/ghostty-parity.
Base 74acf6a86 (`git merge-base --is-ancestor` passed).

## Premise checks (from the pinned vt-1.0.0 source)

- Clipboard read (`GHOSTTY_TERMINAL_OPT_CLIPBOARD_READ`, terminal.h:2499-2507, struct at ~1030):
  the reply must be given before the callback returns; returning without one answers empty (OSC 52)
  or EPERM (5522). No deferred reply exists. Installing the callback also makes mode 5522
  settable (stream_terminal.zig:1939) and turns on kitty paste events in the library.
- Parsing runs on the main thread (TerminalSession.MainThreadHandler -> emulator.append ->
  nativeWrite), so a blocking callback would freeze the UI: the reply has to be asynchronous.
- DA3: the C wrapper always installs `deviceAttributesTrampoline` (c/terminal.zig:872) and the
  host callback is not told which DA level was asked (GhosttyTerminalDeviceAttributesFn has no
  request argument). `reportDeviceAttributes` (stream_terminal.zig:1712) always encodes the
  requested level. So the host cannot leave DA3 unanswered: library limit.
- Default cursor blink: `GHOSTTY_TERMINAL_OPT_DEFAULT_CURSOR_BLINK` (terminal.h:2275) applies at
  once while the program has not chosen a style (Terminal.zig:502-509).
- TERM: configurable per install (`terminal-term` property, TerminalTerm.resolve, default
  xterm-256color; TermuxShellEnvironment.java:105-106), so the shim takes it from the session's env.

## Clipboard reply design (F)

In the callback the shim replies DENIED while its write_pty sink is muted, so the library's
synchronous refusal never reaches the pty; it marks the request pending. The OSC sniffer keeps
the raw `52;`/`5522;` string, and at its end the shim hands Java the raw request plus its
terminator as an event (stream order kept). After consent Java calls a native encoder that feeds
the same raw request into a throwaway 1-row libghostty-vt terminal whose clipboard-read callback
answers with the text Java supplies, and returns what that terminal wrote; Java writes it to the
pty. The reply bytes are therefore exactly what the library would have written, for both OSC 52
and 5522 (status lines, ids, terminator), without re-implementing either protocol.

Paste events: once the read callback is installed, mode 5522 can be set on Fast, the mode is
mirrored to Java (`GHOSTTY_MODE_BITS`, TerminalEmulator.java:1604) and a paste goes out as Java's
`KittyClipboard.pasteEvent` with Java's one-time password (TerminalEmulator.java:1374). The
follow-up read reaches the core, which does not know that password, so Java redeems it from the
raw request (same rule as `KittyClipboard.handle`, :168-172) and answers without a sheet.

(continued below as the work proceeds)

## Decisions recorded before building

- B (G4): the app's cursor default is a steady block (`terminal-cursor-blink-rate` default 0,
  TermuxPropertyConstants.java:235; `terminal-cursor-style` default block). Ghostty's own default
  blinks; Ghostty with a steady cursor reports `?12;2` and DECSCUSR `2`, which is what Fast
  reports. Matching Ghostty's literal reply would claim a blink the user does not see, so no code
  change: intended difference. Not done: following a user who sets blink-rate > 0 — the view
  toggles `setCursorBlinkingEnabled` on every blinker start/stop (TerminalView.java:4751-4773),
  so feeding it to the core would make the report flip with focus.
- C (G5): TN comes from the session's own `TERM=` env entry (configurable, default xterm-256color).
- D (G6): DA2 firmware 0 kept, intended difference (a version field; we are not Ghostty 1.x).
- E (G7): library limit (see premise checks).
- A: the scheme fed to the core is the dark/light class of the app's terminal colour scheme's
  default background (`isDefaultBackgroundDark()`, the same rule the Standard engine answers
  996 with), pushed at core creation and on `resetColorsToScheme()`.

## Fixer 1 (colour scheme + TN) — pass

Commits acf2f84f0 (core owns 996/2031; Java 2031 path returns early on Fast), 5600f74a5 (TN from
session TERM; TerminalOutput.getTerminfoName; TerminalSession.termValue). Reported XML:
GhosttyCoreTest tests=25 skipped=0 failures=0; ColorSchemeReportTest (Standard) tests=6 failures=0;
TerminalSessionTermTest tests=2 failures=0. Terminfo name survives RIS (lives in the stream handler).
My review of the diff: matches the brief; initial Core.dark is false (calloc) and is set at creation
without a report.

## Fixer 2 (clipboard read seam) — pass

Commits 035cd58c5 (shim mute+deny, sniffer keeps 52;/552, EVENT_CLIPBOARD_READ=5, throwaway-terminal
encoder nativeEncodeClipboardReply, DA1 52, ClipboardReadRequest, KittyClipboard.redeemsPasteEvent,
onClipboardReadRequest on TerminalOutput/Session/SessionClient), fb5db20b4 (tests). Reported XML:
GhosttyClipboardReadTest 7/0, GhosttyCoreTest 25/0, GhosttySixelTest 18/0, KittyClipboardTest 33/0,
TerminalTest 19/0, all skipped=0. Two existing tests pinned the old DA1 and were updated to
`62;4;22;52`. Reply bytes asserted: 52 allow ST `\e]52;c;aGk=\e\\`, BEL `...\a`; 52 deny
`\e]52;c;\e\\`; 5522 allow OK / DATA:mime=dGV4dC9wbGFpbg==;aGk= / DONE; 5522 deny
`\e]5522;type=read:status=EPERM\e\\`; DECRQM 5522 `\e[?5522;2$y`.
Known limits: a read request longer than 16 KiB (OSC_CAP) is denied silently and never reaches the
app; the sniffer now copies up to 16 KiB of every OSC 52 / 5522 string (writes included).

## Fixer 3 (app consent) — pass with review

Commits 1b9c052a4 (JNI getForegroundProcessGroup in termux.c, TerminalSession.getForegroundProgramName
via /proc/<pgid>/comm), 0f42e7de0 (ClipboardReadGrants, ClipboardReadConsent with Request/Ui
interfaces, sheet on TerminalSheetController with four menu rows, wiring in
TermuxTerminalSessionActivityClient + TermuxActivity.onActivePaneChanged/Moved), 3635c0432 (settings
row). Reported XML: ClipboardReadConsentTest 12/0, ClipboardReadGrantsTest 5/0,
TerminalPreferencesFragmentTest 5/0, SettingsTreeTest 6/0, TerminalClipboardTest 8/0,
TerminalActionDispatcherSignalsTest 12/0; :app:assembleDebug passed.
Outside the brief: SettingsSearchIndex.hiddenOnThisPhone hides the reset row when the engine is not in
the build (search must not point at a hidden row).
Strings: "%1$s wants to read the clipboard", "A program wants to read the clipboard", "Deny",
"Allow once", "Allow for this session", "Always allow %1$s", "Reset clipboard permissions",
"Programs you always allowed will ask again.", toast "Clipboard permissions reset".
My review: grants read prefs on every check (so a reset from Settings applies at once); session
grants keyed by session identity + program name. Risk flagged by the fixer and confirmed in the
diff: closeSheet() calls sheetController().dismiss(), which pops the top card; if another card is
stacked above the consent sheet when a session ends, that card is closed instead. Rare; left as is.

## Lead's test run (touched modules)

`./gradlew --no-daemon -Pghostty=true :terminal-emulator:testDebugUnitTest :termux-shared:testDebugUnitTest :app:testDebugUnitTest --tests 'com.termux.app.terminal.*' --tests 'com.termux.app.fragments.settings.*'`
-> BUILD SUCCESSFUL in 3m 59s. XML totals: terminal-emulator tests 874 failures 0 errors 0 skipped 0;
termux-shared tests 101 failures 0 errors 0 skipped 0; app (terminal + settings packages) tests 2868
failures 0 errors 0 skipped 1. Full app suite not run (brief: touched modules only, no full suite).

## Device check, first attempt

device-checker found both emulator slots leased by other sessions (emu: "verify late auto-fix",
emu2: "page transition review (codex)", both under 30 min); it waited ~3 min, touched nothing, took
no lease. Pong untouched. I then waited for a slot to free.

## Device check (device-checker, emu / emulator-5554, second attempt) — all 9 pass

DA1 `ESC [ ? 62 ; 4 ; 22 ; 52 c`. 1 sheet heading "sh wants to read the clipboard", rows Deny / Allow
once / Allow for this session / Always allow sh; Deny -> `ESC ] 52 ; c ; ESC \`. 2 Allow once -> text,
asks again. 3 session grant -> no sheet next time. 4 per-pane session grant; Always survives
force-stop. 5 two concurrent reads -> one sheet, both answered. 6 settings page rows unchanged plus
the reset row; toast "Clipboard permissions reset"; asks again after. 7 --no-focus pane: no sheet,
no focus steal; sheet after `pane focus`. 8 5522 OK/DATA/DONE. 9 probe ran with no sheet, 111 lines,
crash buffer empty. Screenshots in the scratchpad `shots/` folder. Cleanup: property cleared, Nexus
home restored, lease released, emu stopped. Pong untouched.

Seen by me in 01-sheet.png: the sheet appeared over the Home page, not over a visible terminal (the
checker's panes were not on screen); the glass card lets the wallpaper's large text run through the
heading. The first is a policy gap (see hand-back, decisions); the second is the existing sheet
component's look.

## Probe rerun

Committed as 0983eafba with the diff note's new "Wave 2 rerun" section. Only former-gap rows and
size/cursor-dependent rows changed against fast-c81774202 (mechanical row diff: 13 rows changed;
da1, decrqm-dec-5522, xtgettcap-TN, dsr-996, mode2031-dsr996, osc52-read, osc5522-read, and six
size/cursor rows because the pane had 4 rows).

No temporary logging commits were made in this wave.
