# Code map for OSC 66 text sizing (surveyed 2026-09-21 on dev a6b461f4)

Line numbers are from that commit; re-grep before editing. Never read TerminalEmulator.java top
to bottom (4909 lines) — use the ranges below.

## 1. Cell model — terminal-emulator/.../TerminalRow.java (460 lines)
- `mText: char[]`, `mCharWidths: byte[]`, `mStyle: long[]` L46-60; `mSpaceUsed` L50.
- Lazily allocated side tables (mirror for the size record): `mDecorationColors` L95
  (has/get/set L115-149), `mHyperlinkIds` L101 (`hasHyperlinks` L120, `getHyperlinkId` L128,
  `setHyperlinkId` L151, `markUsedHyperlinkIds` L133).
- Flags: `mHasNonOneWidthOrSurrogateChars` L65, `mHasBitmap` L70, `mShellIntegrationMark` L88.
- Wide chars: width per code-point start in `mCharWidths`; `findStartOfColumn` L205,
  `wideDisplayCharacterStartingAt` L237, `widenCell` L282 (copies style/decoration/hyperlink to
  the new second cell L293-296).
- `copyInterval` L163 (per-column copy calling `setChar` with style/decoration/hyperlink L188).
- `clear(long style)` L253 (nulls side tables L261-263).
- `setChar(...)` private core L301-440 — the one choke point; per-column metadata written
  L305-311 when `updateCellMetadata`; wide-char neighbour overwrite L323-337 assumes ≤2 columns.

## 2. Emit path — TerminalEmulator.java
- `emitCodePoint` L4280-4475: charset substitution L4282-4406; grapheme fast path L4407-4419;
  `WcWidth.width` L4421; autowrap L4422-4433; insert mode L4452-4456; final write L4463
  `mScreen.setChar(column, mCursorRow, codePoint, getStyle(), mUnderlineColor,
  mCurrentHyperlinkId)`; cursor advance L4470-4475.
- REP (CSI b) L3062-3069 loops `emitCodePoint(mLastEmittedCodePoint)`.
- Cursor helpers L4477-4499. `mInsertMode` L521.

## 3. OSC dispatch — TerminalEmulator.java
- `doOscSetTextParameters` L3521-3822; prefix parser L3523-3536; cases: 7 L3616, 8 L3619,
  22 L3800, 99 L3805 (`mKittyNotifications.handle(textParameter, mKittyNotificationHandler)`),
  default `unknownParameter` L3818, `finishSequence` L3822. Add `case 66` beside 99.
- Field `mKittyNotifications` L418. No OSC 66 exists: the 66s at L50 (mouse wheel), L797 and
  L2274-2276 (DEC private mode 66 = keypad) are unrelated.
- Precedent to copy: `KittyNotifications.java` (443 lines): wire form L21-23, `handle` L86-114
  (split on first `;` L91-98), `parseMetadata` L214-236 (colon-separated single-letter keys),
  `Handler` interface L31-40 (keep the parser free of session state).
- Kitty graphics APC (`doApc` L1949, comma dialect) is not the model for a plain OSC.

## 4. Per-cell aux data — TextStyle.java (189 lines)
- Layout doc L9-17: bit 14 is the only free bit. `encode`/decoders L112-159, `withColorsAndEffect`
  L166, bitmap-cell precedent `encodeBitmap`/`isBitmap`/`bitmapNum/X/Y` L170-188.
- Hyperlink pool GC: `TerminalHyperlinks.reclaimUnused` L107-120.

## 5. Erase/insert/delete — TerminalEmulator.java → TerminalBuffer.java
- Primitives: `TerminalBuffer.blockCopy` L530, `blockSet` L548; emulator wrapper `blockClear`
  L3911-3916. ICH L2864-2874; ED L2909-2942; EL L2944-2965; IL L2969-2981; DL L2982-2993;
  DCH L2994-3009; SU/SD L3010-3030; ECH L3032-3036; `scrollDownOneLine` L4067-4079 →
  `TerminalBuffer.scrollDownOneLine` L474. Hook the block-drop helper at blockCopy/blockSet.

## 6. Renderer — terminal-view/.../TerminalRenderer.java (2733), RowRenderCache.java (419), TerminalRowNodes.java (89)
- `render` L854/864 → `renderRows` L874-936; node path `drawRowsThroughNodes` L945-969,
  `recordAndReplayRows` L971+; direct path backgrounds L907-918 (`drawRowBackgroundAndCursor`
  L1984), glyphs L921-935 (`drawRowGlyphs` L1100-~1644, run flushes e.g. L1193-1204,
  placeholder cells L1206-1230).
- `drawTextRun` L1644-1679 → `drawTextRunConfigured` L1756-~1900; symbol scaling L1810-1819
  (scale = min(runWidth/measured, lineSpacing/lineBox), centred); one-axis squeeze L1824-1832.
- Cursor: `drawCursorShape` L2580, `drawExtraCursors` L2262.
- `RowRenderCache.Row` L58-89; `rowChanged` L194-221 (calls `capture*` L329-420);
  image-generation tracking L232-291 (the "state outside the row" pattern); `resize` L314-321.
  `TerminalRowNodes`: three `RenderNode[]` per row, `resize` L40-62, accessors L64-74 — 1:1
  row↔node today.

## 7. Selection and copy
- `TerminalView.getColumnAndRow` L1210-1216, `getColumnForX` L1223-1225, `getRowForY` L1227+.
- `TerminalBuffer.getSelectedText` L106-168 (uses `findStartOfColumn`, wide-char fallback
  L129-131, trailing-space trim L138-160); `getWordAtLocation` L169+.
- `textselection/TextSelectionCursorController.java` works in column units.

## 8. Reflow — TerminalBuffer.java
- `TerminalEmulator.resize` L890-930 → `TerminalBuffer.resize` L265-279 / body L274-460.
  Same-column fast path L280-311 (rows reused). Column change L312-460: fresh rows L321, cell-by-
  cell rebuild L341-432 reading style/decoration/hyperlink L379-381 and writing `setChar`
  L406-407 / `appendCodePointToCell` L409-410. A new side table is dropped here unless wired in.
  `mLineWrap` L363/L397; `mShellIntegrationMark` first-of-wrap-group L375-377.

## 9. Tests
- `terminal-emulator/src/test/java/com/termux/terminal/TerminalTestCase.java` (385):
  `withTerminalSized` L178, `enterString` L159, `assertLinesAre` L261, `assertLineIs` L242,
  `assertLineWraps` L275, `resize` L271, `assertInvariants` L207-241, `MockTerminalOutput` L18-149.
- Precedents: `KittyNotificationsTest`, `HyperlinkTest`, `ResizeTest`, `ScrollRegionTest`,
  `TerminalRowTest`; view: `RowRenderCacheTest` (437), `TerminalRendererPolicyTest` (225).
- Run:
  `export JAVA_HOME=$HOME/.local/opt/jdk21 PATH=$HOME/.local/opt/jdk21/bin:$PATH`
  `./gradlew :terminal-emulator:testDebugUnitTest` · `./gradlew :terminal-view:testDebugUnitTest`

## Lockstep list (every place a new per-column side table must be handled)
TerminalRow: constructor L106-112 · `clear` L253 · `setChar` L305-311 · `widenCell` L295-296 ·
`copyInterval` L188 · new has/get/set. TerminalBuffer: `blockCopy` L530 / `blockSet` L548 (inherit
from the row) · `resize` reflow L379-381 + L406 · `scrollDownOneLine` L474 (rows move by
reference). `TerminalTestCase.assertInvariants`. `RowRenderCache.rowChanged` L216-217 (new
`capture*`).

## Risks
Reflow is a from-scratch rebuild (§8). `setChar`'s neighbour overwrite assumes ≤2 columns (§1).
Scroll-region moves can slice a block's row span — drop the whole block, never copy part of it.
Both cache classes are strictly one row per node (§6).
