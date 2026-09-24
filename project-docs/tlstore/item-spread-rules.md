# tlstore item spread — rules

Every app's page in tlstore is written by us, from these rules, so a new item looks like the
others the day it is added. The upstream README is never shown as it is. Design reference: the
"Item spread" and "Item spread · rules" boards on the tlstore design canvas
(https://claude.ai/artifact/NwnkCF8w2Dzmhw3cAxLjX2, page Broadsheet).

## Slots, top to bottom

| # | Slot | Rule | Catalog field |
|---|---|---|---|
| 1 | Cover picture | Chosen by us, about 16:9, edge to edge, at most 7 rows, below the hero. May move: a loop of 6 s or less, no sound. Signed with the catalog. | `picture` |
| 2 | Hero word | The item name in lower case, drawn in the script face, centred — the page's hero word, same as every other screen's, sitting above the cover, never crossing it. Its lead line, small caps, dim, carries `No. NN · CATEGORY` (catalog order, category in caps). | `name`, `category` |
| 3 | Line | Installed state left (`installed <version>` once it is), upstream `owner/repo` right, wavy underline in the accent. A setup with no upstream shows `our setup` instead. | `upstream` |
| 4 | Standfirst | One line, 42 characters at most, lower case, italic, dim: what it is for, in plain words. No brand names, no "powerful", no full stop. | `standfirst` |
| 5 | Rule | Ten dashes each side of the star. ★ in the accent when starred, ☆ when not. Hidden until gh works; never shown for a setup. | — |
| 6 | Facts | A box-drawn table with these rows in this order: version (`old → new` when an update exists), made by, licence, size. An unknown value drops its row. | `version`, `author`, `licence`, `size` |
| 7 | What it does | Exactly three lines of 40 characters or fewer, each starting with a third-person verb (Shows, Sends, Keeps). An arrow → in the accent leads each line. | `does[3]` |
| 8 | Try it | One command a newcomer can safely run right after installing. No sudo, no flags they would have to look up. | `try` |
| 9 | Demo | One picture or short clip of the command from slot 8 working. Optional; the slot disappears when there is none. | `demo` |
| 10 | Good to know | Zero to two dim italic lines of 40 characters or fewer: size warnings over 100 MB, what it needs, where it works best. Never marketing. | `notes[0..2]` |
| 11 | Keys | `u update` (only when there is one) · `i install` / `r remove` · `s star` · `o repo` · `esc back`. | — |

## Tone

Sentence case, plain words, no exclamation marks, no emoji. Say what the thing does for the
person, not how it works inside. Categories: All, Note taking, Tools, AI; each item has exactly one.

## Adding an item

Fill every catalog field above, run the item through the rules, and check it at the three layout
sizes (full screen, keyboard open, zoomed in). A field that cannot follow its rule is a reason to
fix the copy, not to bend the rule.
