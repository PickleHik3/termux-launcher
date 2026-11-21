# Ghostty parity wave 3: lead report (uncommitted)

Branch feat/ghostty-parity, worktree /home/amalvajdan/Projects/termux-launcher/app/ghostty-parity.
Base 3b116815b (`git merge-base --is-ancestor` passed). Fast engine only; the Java core never
produces OSC 7501 events and its store stays empty, so Standard does not change.

## Commits

| Hash | Subject |
|---|---|
| 26cefd4a1 | fix(terminal): the clipboard consent sheet opens only while the terminal is the page on screen |
| 7ab4b8c1a | fix(terminal): a clipboard read over 16 KiB gets the library's denial instead of silence |
| c229d69dd | fix(terminal): dismissing the clipboard consent sheet closes that sheet only |
| 2bc320d71 | feat(terminal): OSC 7501 program-status record store |
| 8fc6808f7 | feat(terminal): Fast engine feeds OSC 7501 reports to the store and signals once per turn |
| 98d7e4c52 | feat(terminal): the window pill shows a program's own status in place of the title |
| 90073cb25 | feat(terminal): in-app notice when a program needs input, finishes or fails in a pane not in front |
| 05e081345 | feat(terminal): typing or pasting into a pane marks its finished program status seen |
| b6fd81814 | test(terminal): probe the BEL-terminated program-status query and a valid report |
| f3b405352 | fix(terminal): a program that asks again after working gets a second notice |
| 01a8b6e28 | fix(terminal): tapping a program-status notice opens its pane and the terminal |
| 5846dff6c | fix(terminal): a pill showing a program's status is wide enough for its word and percentage |
| 577497e5e | docs(terminal): wave 3 probe rerun on Fast: OSC 7501 query answered |

f3b405352, 01a8b6e28 and 5846dff6c are the lead's own: the first fixes a re-arm gap found in
review (blocked, working, blocked gave one notice: the engine raises only blocked/done/error, so the
working step never re-armed); the other two fix what the first emulator run found (on Home the
sender is often the current pane, and the shared notice gave a current pane no tap at all; the
pill's 104 dp cap clipped "demo working 4…"; a status pill now takes up to 176 dp).

## Part 1: consent sheet

1. **Only while the terminal is on screen.** The policy's Ui now asks `isTerminalShowing()` =
   launcher visible and `PaneWallController.isTerminalShowing()` (terminal page, wall at rest),
   through a new `TerminalHost.isTerminalShowing()`. Waiting requests are pumped from
   `runWallPageSettle()` and from the at-rest branch of `onWallPageChanged`. Tests in
   `ClipboardReadConsentTest`.
2. **Over-16 KiB read.** Established first: silent (three new 5522 tests failed with empty output).
   Cause: the library calls `on_clipboard_read` before the sniffer has seen the string's last chunk,
   so `osc_overflow` was still 0; the denial was muted and then dropped at `osc_end`. Now the muted
   denial bytes are kept (`clip_deny`, 1 KiB) and written from `osc_end` when the request was not
   kept: 5522 gets `ESC ] 5522 ; type=read:status=EPERM` with the request's own terminator (ST and
   BEL tested). OSC 52 cannot exceed the cap as a read: the library does not treat a long selection
   string as a read (no callback, no reply); that is library behaviour, pinned by a test that no
   request reaches Java. Tests in `GhosttyClipboardReadTest` (12 total).
3. **Dismiss closes that sheet only.** `TerminalSheetController.dismiss(View card)` closes one
   card wherever it sits (top: the normal dismiss; under another card: removed, the upper card stays
   and shows, its onDismiss runs once); the consent Ui remembers the card it showed. Test in
   `TerminalSheetControllerTest`.

**"Let programs read the clipboard" default:** on (`DEFAULT_TERMINAL_OSC52_CLIPBOARD_READ_ENABLED
= true`, TermuxPreferenceConstants.java:821; the settings xml also says `true`). A first-time user
on Fast therefore gets the consent sheet the first time a program asks to read the clipboard
("<program> wants to read the clipboard": Deny / Allow once / Allow for this session / Always
allow <program>). Nothing is read without it, except a 5522 listing-only read and the user's own
paste event. Not changed.

## Part 2: OSC 7501

How it travels: shim `on_program_status` (registered with `GHOSTTY_TERMINAL_OPT_PROGRAM_STATUS`;
`WRITE_PTY` was already set) -> `EVENT_PROGRAM_STATUS` (strings copied by `send_event`) ->
`TerminalEmulator` applies to its `ProgramStatusStore` -> once per output turn
`TerminalSessionClient.onProgramStatusChanged(session, raised)` -> the app refreshes the window bar
(coalesced: `scheduleWindowBarRefresh` posts one refresh 250 ms out and ignores calls while one is
pending) and decides notices. `ProgramStatus` / `ProgramStatusStore` are generic types in
`com.termux.terminal`; no Fast type in app code.

Pill: the chip's text becomes `<name> <word>` (+ ` NN%` for working/blocked with a percentage);
name = the record's app (inherited from the nearest ancestor), else its title, else the window's
own title, cut to 12 code points + "…"; words "working", "needs input", "done", "error". Marks:
needs input = attention tint, error = failure edge, done = done tint, working = no mark. Idle, clear
or no record = the pill exactly as before. Several records across a window's panes: blocked, error,
working, done, idle; ties to the latest update. The chip's existing ring is not driven by 7501.

Notice: through the OSC 9 channel (`ShellSignals` -> in-app `AppNotice`, tap brings the pane to
front), new `statusNotice` that does not set the bell mark. Raised for blocked, done and error when
the pane is not in front (launcher visible and terminal showing and pane current). Headline
"<name> needs input" / "<name> is done" / "<name> stopped with an error", detail = msg (one line,
200 code points) plus the session tag "[n] name". Rate limit: one notice per session per 2 s (rest
dropped), not again for the same id and state until that id's state moves.

### Ambiguity picks (checklist section 5)

1. Reply terminator: the query's own (the library's choice; kept).
2. Spaces in the reply: none (library).
3. C1 forms / huge bodies: not examined; library behaviour as is.
4. Body cap 4087 vs 4096: library's lower limit kept.
5. Malformed value over its limit: discard (library).
6. Duplicate keys and limits: library behaviour.
7. Empty values: absent (library); `id=` discards.
8. Root title as window title: not wired; the window title is untouched, the pill shows the status text instead while a record exists.
9. Several records: blocked, error, working, done, idle (developer's order); ties to the latest update.
10. "Seen" for done/error: the user types or pastes into that pane (the spec's own example). Six call sites in TerminalView plus TerminalClipboard.paste text path; image, file-path and bracketed-paste-event paths do not mark seen.
11. Process exit: the pty's child (the shell) exiting; a foreground program exiting under a live shell is covered only by OSC 133;A.
12. LRU "updated": every stored report counts (even a no-op replacement); clears do not.
13. Revision: 0.4 per the checklist.
14. Not applicable (runtime now covered by Fast JVM tests through the real library).
Also: idle is kept at prompt and exit (the MAY in row 30 not taken); error raises a notice like done (same lifetime as done in the spec; "failed and stopped" is news to someone not watching).

### Checklist (41 rows)

| # | Requirement | Status | Covered by |
|---|---|---|---|
| 1 | Install program_status callback | met | GhosttyProgramStatusTest.testRows1To4QueryReplyWithSt |
| 2 | Install write_pty | met | same |
| 3 | Reply `?` with `?` + lists | met | testRows1To4QueryReplyWithSt, testRow3QueryReplyWithBelUsesBel |
| 4 | Lists are rev-0.4 states and kinds | met | testRows1To4QueryReplyWithSt |
| 5 | Never echo record contents | met | testRow5ReportsProduceNoOutput |
| 6 | Parse key=value pairs on `:` | met | testRows6And7PairsAndWhitespace |
| 7 | Trim whitespace | met | testRows6And7PairsAndWhitespace |
| 8 | Skip malformed pair | met | testRows8And9MalformedPairSkippedUnknownKeyIgnored |
| 9 | Ignore unknown keys | met | same |
| 10 | Last value wins | met | testRow10LastValueWins |
| 11 | No/unknown state ignored | met | testRow11MissingOrUnknownStateIsDiscarded |
| 12 | Discard whole report on failure | met | testRows12And14BadBase64IsDiscarded, testRow13ControlCharacterInTitleIsDiscarded |
| 13 | Refuse control chars in msg/title | met | testRow13ControlCharacterInTitleIsDiscarded |
| 14 | Base64 standard, padding optional | met | testRow14PaddingIsOptional, testRows12And14BadBase64IsDiscarded |
| 15 | Size limits | met (library) | no test of ours; library's own tests; shim buffers hold every library cap |
| 16 | Invalid id ignored, no root fallback | met | testRow16BadIdIsDiscardedNotRoot, testRow16EmptyIdDoesNotClearEverything |
| 17 | One record per id, replaced whole | met | ProgramStatusStoreTest.testRow17ReportReplacesTheRecordWhole, testRow17UnknownStateIsIgnored, testRows17To19And21ThroughTheLibrary |
| 18 | Root record; root and children coexist | met | testRow18RootAndChildrenCoexist |
| 19 | Clear removes subtree | met | testRow19ClearRemovesSubtreeBySegment, testRow19ClearOfMissingIdChangesNothing |
| 20 | Clear without id removes all | met | testRow20ClearWithoutIdRemovesEverything, testRow20ClearWithoutIdThroughTheLibrary |
| 21 | App from nearest ancestor | met | testRow21* (5 tests) |
| 22 | kind only for blocked | met | testRow22KindOnlyForBlocked |
| 23 | progress only for working/blocked | met | testRow23ProgressOnlyForWorkingAndBlocked; app: ProgramStatusLabelTest.percentOnlyOnWorkingAndBlocked |
| 24 | Do not interpret msg; may shorten | met | ProgramStatusLabelTest.oneLineCutsAndFlattens, longNamesAreCutAtTwelveCodePoints |
| 25 | Alt screen no effect | met | testRow25AlternateScreenKeepsRecords |
| 26 | RIS removes all | met | testRow26FullResetClearsAll |
| 27 | DECSTR keeps | met | testRow27SoftResetKeepsRecords |
| 28 | Drop working/blocked on child exit | met (session wiring untested) | testRow28ProcessExitDropsWorkingAndBlocked, testRow28And29ExitDropsWorkingAndBlocked; the TerminalSession MSG_PROCESS_EXITED call has no JVM harness |
| 29 | Drop working/blocked on OSC 133 A | met | testRow29PromptStartThroughTheLibraryDropsWorkingAndBlocked, testRow29PromptDropsWorkingAndBlocked |
| 30 | MAY drop idle | not applicable (option not taken: idle kept) | testRow30IdleIsKeptAtPromptAndExit |
| 31 | Keep done/error across exit and prompt | met | testRow31DoneAndErrorSurviveExitAndPromptUntilSeen, testMarkSeen* |
| 32 | No heartbeat / timeout | met | testRow32NothingInTheApiTakesTime |
| 33 | >= 64, cap 256, LRU by update | met | testRow33* (4 tests) |
| 34 | No markup | met (by code) | pill and notice set plain Strings on TextViews; no Html/Linkify; no test |
| 35 | Disarm bidi / invisible chars | met | testRow35InvisibleFormattingIsStripped, testRow35SurrogatePairsAndPlainTextSurvive, testInvisibleFormattingIsStrippedFromDecodedText |
| 36 | Say which terminal | met | pill is per window; notice detail carries the session tag (client code, no unit test) |
| 37 | Rate-limit external effects | met | ProgramStatusAlertsTest.oneAlertPerSessionPerTwoSeconds, sameStateDoesNotRepeatUntilItChanges; testOneSignalPerWriteTurnWithRaisedRecords |
| 38 | OSC 9;4 mapping stops after first 7501 | not applicable | OSC 9;4 is not mapped to records |
| 39 | Copy strings before callback returns | met | testRow39StringsSurviveAfterTheWriteReturns |
| 40 | Callback inside write; marshal | met (by design) | the shim only records an event; the app acts after the write (GhosttyCore.deliver) |
| 41 | Optional notification | met | ProgramStatusAlertsTest.*; device check |

Totals: met 39, not met 0, not applicable 2 (30, 38).

### What the pill cannot show

- `kind` (permission / question / auth) is not shown; "needs input" covers all three.
- `msg` is not on the pill (only in the notice).
- Records other than the most urgent one, and how many there are.
- Indeterminate progress has no text (the word alone).
- The column look of the status bar (StatusBarWindowColumn) shows no status text or mark.
- Notices only while the launcher is visible (the OSC 9 channel); with the launcher in the
  background nothing is raised and nothing reaches the shade.

## Tests

Rerun at 577497e5e (after the post-check fixes): `BUILD SUCCESSFUL in 6m 47s`, terminal-emulator 933/0/0/0, terminal-view 336/0/0/0, app 13906/0/0/14 skipped. First run at f3b405352:
`./gradlew --no-daemon -Pghostty=true --continue :terminal-emulator:testDebugUnitTest :terminal-view:testDebugUnitTest :app:testDebugUnitTest`
-> `BUILD SUCCESSFUL in 6m 58s`. XML totals (whole modules): terminal-emulator 933 tests, 0
failures, 0 errors, 0 skipped; terminal-view 336 / 0 / 0 / 0; app 13906 / 0 / 0 / 14 skipped.
(terminal-emulator and terminal-view were up to date from the fixers' runs on the same inputs.)
`:app:assembleDebug -Pghostty=true` built the x86_64 APK used on the emulator.

## Device check (device-checker, emulator-5554, AVD tl16; pong untouched)

Run 1, build f3b405352, verdict in its words: "PASS 7/9, with 5 failing (the notice tap did not
open the pane) and 3 passing with defects (pill text clipped, "needs input" and "error" look
alike)." DA1 `ESC[?62;4;22;52c`; query reply exact, no spaces, ST and BEL. Pill: "demo working 4…",
"demo needs inp…", "demo done", "demo error", title back after clear, "two needs input" for two
ids. Typing a key after done returns the title. Notices for needs input, done, error appeared on
Home; tap did nothing (fixed in 01a8b6e28). Sheet: none on Home, appeared once the requesting window
was selected on the terminal page; Deny worked. Stacked the window-bar menu over the consent sheet,
killed the requesting shell: the sheet closed and the menu stayed. Crash buffer empty. Probe pulled.
Defects it noted: needs-input and error pills both pink-edged (existing marks: attention filled vs
failure edge only); the notice overlaps the window-bar pill (existing notice placement); sheet rows
low-contrast on glass (existing sheet look); after Back the stacked menu stayed (needed a tap
outside); the chip read "home" instead of "sh" after the sheet interactions once.

Run 2, build at 5846dff6c, verdict: "PASS on all three checks." "demo working 40%" and "claude
needs input 40%" whole; tapping the needs-input and the done notice slid the terminal page in with
the pane (frames caught mid-slide; settling on that pane not confirmed); crash buffer empty.

Screenshots (scratchpad wave3-shots/): 01-da1.png, 03a-working.png, 03b-needs-input.png,
03c-done.png, 03d-error.png, 03e-clear.png, 03f-two-ids.png (and -pill crops), pills.png,
04-pair.png, 05-notice.png, 05b-t1.png, 05c-after-tap.png, 05d-notice-before-tap.png,
05e-after-tap.png, 06a-home-no-sheet.png, 06b-sheet.png, 06c-denied.png, 07-stacked.png,
07c-montage.png, 07d-final.png, 09-end.png, r1-working.png, r1-working-pill.png, r1-blocked.png,
r1-blocked-pill.png, r2-notice.png, r2-after-tap.png (missed), r2-after-tap-2.png,
r2d-notice.png, r2d-after-tap.png.

## Not verified

- Session-level process-exit drop (MSG_PROCESS_EXITED) has no JVM test; the store call is tested.
- Row 34 (no markup) and row 36 (notice names the terminal) are met by code, not by a test.
- statusNotice not marking the bell: no ShellSignals harness, untested.
- Image, file-path and bracketed paste-event paths do not mark done/error seen.
- On pong: nothing (not touched). Look, feel and the 176 dp pill on a real bar are owed there.
- The notice tap's settle on the right pane (frames mid-slide only).
