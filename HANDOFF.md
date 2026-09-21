# P1 text sizing model (feat/text-sizing-model)

## Done
- `KittyTextSizing.java`: OSC 66 parser, grapheme split, cell measuring/cutting, the packed record.
- `TerminalRow`: `mTextSizes` side table + typed accessors; cleared by `clear`/`setChar`; `widenCell`
  refuses sized cells; `setBlockAnchorChar` stores a whole block text in one cell.
- `TerminalBuffer`: `TextBlock`, `getTextBlockAt`, `getTextBlockText`, `writeTextBlock`,
  `isAreaBlank`, `dropTextBlocksIn`; drops wired into `setChar`/`blockCopy`/`blockSet`/
  `scrollDownOneLine`; D5 selection; D3 reflow replay with demote/promote.
- `TerminalEmulator`: OSC 66 case, `doTextSizing`/`writeTextSizeBlock`, REP of a block.
- `TerminalTestCase.assertInvariants` uses stored widths on rows with blocks.

## In progress
- Nothing.

## Next
- `KittyTextSizingTest` covering every protocol rule; run `:terminal-emulator:testDebugUnitTest`.

## Gotchas
- The anchor holds the whole text as one cluster at stored width 1; never use `WcWidth` on it.
- Records are stamped after the text is written, or `setChar` would drop the block being written.
