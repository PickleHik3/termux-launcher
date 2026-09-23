# tlstore design sources

Exports of the design canvas (https://claude.ai/artifact/NwnkCF8w2Dzmhw3cAxLjX2), kept here so build
phases can read exact geometry. Each file is one artboard: absolutely positioned spans on an 8×20 px
cell grid (52×45 cells unless the file says otherwise); `{{accent}}`-style holes are palette slots
computed in the script block at the bottom. `Flow.dc.html` is the approved system (shared masthead,
rule, hero, transitions); the `Paper-*` files are the screens. Where they disagree, Flow wins for
structure and motion, Paper for screen content. Script words (goodies, app names) are pictures.
