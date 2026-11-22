# Ghostty parity wave 4: library key and mouse encoders on Fast

Branch feat/ghostty-parity, worktree app/ghostty-parity, base 577497e5e. Lead: Opus.

## Plan (fixed by the lead after reading the code at 577497e5e)

Where the bytes are made today (all verified at 577497e5e):

- Keys: TerminalView (terminal-view) is the one funnel. onKeyDown (2912) -> client bindings ->
  handleKittyKeyEvent (3159, KittyKeyEncoder when flags != 0) -> handleKeyCode (3087, KeyHandler
  special keys) -> getUnicodeChar -> inputCodePoint (3005, Ctrl mapping, Alt ESC prefix,
  writeCodePoint). onKeyUp (3130) -> handleKittyKeyEvent release. IME commitText /
  finishComposingText -> sendTextToTerminal (996) -> inputCodePoint. ACTION_MULTIPLE (2934) writes
  characters raw. Extra keys (termux-shared TerminalExtraKeys:74-85): special keys ->
  TerminalView.onKeyDown with a synthetic event, chars -> inputCodePoint, literal macro strings ->
  session.write. In-app keyboard (app inappkeyboard/TerminalKeyEventHandler) -> inputCodePoint and
  dispatchKeyEvent (down+up). Alternate scroll (TerminalView 2010/2136/2153) -> handleKeyCode
  arrows. App-level: TermuxTerminalViewClient:986 (volume-key virtual combos) writes
  KeyHandler.getCode / writeCodePoint directly; TerminalBindingKeyEncoder (send-key bindings,
  TermuxTerminalViewClient:664) encodes with KeyHandler.
- Mouse: every route ends in TerminalEmulator.sendMouseEvent(7 args) (1012).
- Focus: TerminalEmulator.onFocusChanged (1369).

Change list:

1. Fixer A (terminal-emulator): shim encoder handles per core + JNI encode calls (key, mouse,
   focus); GhosttyCore wrappers; GhosttyKeys mapping class (Android keycode -> GhosttyKey,
   codepoint -> US-layout key); TerminalEmulator.usesLibraryInputEncoders() /
   sendLibraryKey(...); Fast branch inside sendMouseEvent and onFocusChanged; debug property
   `debug.tl.terminal_input=java`; emulator-level tests; Standard mouse golden captured before the
   change.
2. Fixer B (terminal-view, termux-shared?, app): Fast branch at onKeyDown/onKeyUp/inputCodePoint/
   handleKeyCode/ACTION_MULTIPLE and the two app routes; Robolectric table test with the host
   library; Standard key golden captured before the change; the differences note.
3. Lead: probe rerun, device check (device-checker), sheet rider.

## Fixer reports

(appended below)

### Fixer A (Sonnet; 162k tokens, 42 tool uses): shim + emulator — pass with review

Commits 996b78d40 (Standard mouse golden, 2160 rows), d7c7ec5ce (shim), 98039e06c (GhosttyKeys),
b5e29b74d (emulator routing + property), ee579e37f (size set only on change), 05579a8dd (tests).
Its report: property GhosttyCore.INPUT_PROPERTY = `debug.tl.terminal_input`, INPUT_JAVA = `java`,
read in useGhosttyCore through TerminalEmulator.setInputPropertyReaderForTests. sendLibraryKey,
mouse and focus flush batched output first. Library de-dups motion within a cell except
SGR-pixels; last cell updated by every action. OPT_SIZE also clears the last cell, so it is set
only on change (brief was wrong there). Wheel pressed=false writes nothing on the library path;
the view never sends one. Tracking none: library writes nothing (view only calls while tracking).
`./gradlew --no-daemon --offline -Pghostty=true :terminal-emulator:testDebugUnitTest` ->
BUILD SUCCESSFUL in 2m 49s, 942 tests, 0 failures, 0 errors, 0 skipped (GhosttyInputEncodersTest 8).
Not verified by it: Android ABIs build; mouse reference is the same JNI call (pins wiring, not bytes).
Lead check: read the TerminalEmulator diff: Java mouse body untouched (one early-return line added),
focus keeps the literal when the library returns null, button mapping 0->L, 1->middle(3), 2->right(2).

### Fixer B (Sonnet; 237k tokens, 110 tool uses): view + app routing — pass with review

Commits f0644194a (host lib in terminal-view tests, capturing session, rig), 098938b85 (Standard
key golden, 6048 rows), ca923bfd5 (view routes 1-6), 9eef1978d (app routes: Fn combos, send-key
bindings), f52d69f73 (Shift+PgUp scroll kept on Fast when kitty flags 0), 1230c9bc6
(KeyRoutesFastTest incl. java property = Standard golden), 673e14423 (differences note +
generator test that asserts the committed note).
Routes (TerminalView): onKeyDownOnLibrary / typeCharactersOnLibrary (onKeyDown, ACTION_MULTIPLE),
onKeyUp RELEASE from event meta, inputCodePointOnLibrary, sendTextToTerminal (IME), handleKeyCode
Fast branch after handleKeyCodeAction. App: TermuxTerminalViewClient Fn combos and send-key
bindings via TerminalBindingKeyEncoder.sendOnLibrary/sendCodePointOnLibrary.
Left on Java by design: in-app keyboard string keys (view.inputText), TerminalToolbarViewPager,
TermuxActivity:864 paste/dictation, command palette command+CR, extra-keys macros, paste.
Deviation from brief (accepted): IME Enter/Tab/Backspace go through inputCodePoint as 13/9/8 which
then maps to Enter/Tab/Backspace keys, so onCodePoint hook and sticky Ctrl/Alt still apply.
Outside brief: TerminalSession no longer final (test subclass); hideTextSelectionCursors returns
false without a selection controller; TerminalView.setSessionForTest.
Android facts: hardware ACTION_UP + repeatCount; extra keys one ACTION_UP-typed event through
onKeyDown, no release; in-app keyboard DOWN+UP pair; IME commit no release, no repeat.
Tests: terminal-emulator 942/0/0, terminal-view 342/0/0, app compile OK, app touched tests green.
Note: 3108 of 6291 rows differ.
Lead checks: golden files unchanged since their commits (git diff 098938b85..HEAD on resources
shows only their creation). Spot-checked note: plain keys, Enter, Backspace, arrows with no mods
at kitty 0 match (absent from note). numpad 5 DECKPAM on -> library `5`: library mode 1035
(ignore keypad with numlock) is on by default, so numeric keypad wins: first-party behaviour.
SGR-pixels one pixel lower than Java: Java adds 1 (xterm 1-based); library reports the 0-based
pixel; first-party behaviour, in the note.

## Module suites (lead, at 673e14423)

`./gradlew --no-daemon --offline -Pghostty=true --continue :terminal-emulator:testDebugUnitTest :terminal-view:testDebugUnitTest :app:testDebugUnitTest`
First run was killed by another session's `gradle --stop` ("Gradle build daemon has been stopped:
stop command received"); rerun: `BUILD SUCCESSFUL in 5m 2s`. XML totals: terminal-emulator 942 /
0 failures / 0 errors / 0 skipped; terminal-view 342 / 0 / 0 / 0; app 13906 / 0 / 0 / 14 skipped.
APK: `:app:assembleDebug -Pghostty=true` at 673e14423 (x86_64 used on the emulator).

## Device check (device-checker, Sonnet; 115k tokens, 53 tool uses; emulator-5554, AVD tl16; pong untouched)

Verdict, its words: "the libghostty input encoders on Fast behave. Typing, Enter, Backspace, Tab,
Esc, arrows, Ctrl-A, Alt-b and the shell edit all match the expected bytes. The only caveats are
the single-finger mouse gestures and a few kitty=31 details." No crash; home restored; props cleared;
lease released.
- DA1 `^[[?62;4;22;52c` with terminal_input unset and with `java`.
- kitty=0: `aB1 [CR][DEL]`, `[ESC][TAB]`, `[ESC][A/B/D/C`, Ctrl-A `[^01]`, Alt-b `[ESC]b`.
- kitty=31: a `ESC[97;;97u`, B `ESC[98:66;2;66u`, 1 `ESC[49;;49u` + `ESC[49;1:3u`, Enter `13u`/`13;1:3u`,
  Backspace `127u`/`127;1:3u`, Esc `27u`/`27;1:3u`, Tab `9u`/`9;1:3u`, arrows `ESC[1;1:1A`/`1;1:3A`,
  Ctrl-A `97;5u`/`97;5:3u`, Alt-b `98;3u`/`98;3:3u`. Touch-typed (IME) characters give no release
  (Android gives none); adb key events do.
- Mouse 1000/1002/1003 + 1006: tap `ESC[<0;14;10M` / `m`. A one-finger touch swipe becomes wheel
  reports (64/65/66), never press+motion: that is the view's existing touch handling (touch scroll
  in tracking mode -> wheel; motion only from SOURCE_MOUSE), not the encoder. Long press: press +
  release only. Real drag motion (mouse source) and two-finger scroll not exercised.
- Shell sanity (bash): `hello Xworld`, Up recalls, Enter reruns — with and without `java`.
- Extra-keys row: this build's emulator showed a swipe keyboard; Esc/Tab/1/arrows were sent as adb
  key events, not extra-keys taps (the on-screen arrow cluster did nothing on tap). Extra-keys taps
  are covered by the view tests only.
- Probe: fast-673e14423.txt; vs fast-f3b405352 only cursor-row and colour-scheme rows changed (lead
  diff); committed f5d65c469 with a section in the probe diff note. No new gaps.

## Rider: consent sheet legibility

Code: the consent sheet (TermuxTerminalSessionActivityClient showSheet) and Select URL
(TermuxTerminalViewClient.showUrlSelection) both build TerminalSheetViews.body + addMenuRow and
show through TerminalSheetController.show. Screens (scratchpad wave4-shots/sheet-compare.png,
sheet-consent.png, sheet-selecturl.png): same translucent glass over the blue wallpaper, same
dark-grey ink, bold heading, wallpaper lettering shows through both. Lead looked at the compare:
same. Outcome: no change; the app-wide sheet look is the developer's call. Side note from the
checker: the Terminal menu (More…) is an opaque white sheet, unlike these two (07c-more.png).
