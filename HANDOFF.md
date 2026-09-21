# P1 text sizing model (feat/text-sizing-model) - done

## Done
- `KittyTextSizing`: OSC 66 parser, grapheme split, cell measure/cut, the packed size record.
- `TerminalRow`: `mTextSizes` side table + typed accessors; cleared by `clear`/`setChar`;
  `widenCell` refuses sized cells; `setBlockAnchorChar` keeps a whole block text in one cell.
- `TerminalBuffer`: `TextBlock`, `getTextBlockAt`, `getTextBlockText`, `writeTextBlock`,
  `isAreaBlank`, `dropTextBlocksIn`, wired into `setChar`/`blockCopy`/`blockSet`/
  `scrollDownOneLine`; D5 selection; D3 reflow with demote/promote.
- `TerminalEmulator`: OSC 66 case, `doTextSizing`/`writeTextSizeBlock`, REP of a block.
- `KittyTextSizingTest` (39 tests); `assertInvariants` uses stored widths on rows with blocks.
- Renderer follow-up: `getWordAtLocation` counts what each row really contributed when any row
  in the wrapped group carries a block, and a tap on a block gives the block's whole text;
  `TerminalRow.getTextSizeRecord` is public so a cache can compare one int.
- Suite: 476 tests green (437 before), terminal-view green.

## Next
- Nothing here. P2 (renderer) builds on the contract in the final report.

## Gotchas
- The anchor holds the whole text as one cluster at stored width 1; never use `WcWidth` on it.
- Records are stamped after the text is written, or `setChar` would drop the block being written.
- Stepping past an anchor cell means `findStartOfColumn(column + 1)`: its cluster's continuation
  characters are stored at width 0, so a char-index walk would run into the next cell's text.
