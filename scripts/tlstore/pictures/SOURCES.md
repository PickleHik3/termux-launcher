# tlstore item pictures — sources

Each picture is the upstream project's own README hero image or a screenshot from its docs,
converted to JPEG (about 832 px wide, quality 80) and signed with the catalog like any other
`launcher:` source. Re-run the conversion the same way if a picture is ever replaced:

```sh
magick <original> -background "#1e1e1e" -flatten -resize 832x -quality 80 <item>.jpg
```

(`-background`/`-flatten` only matters for a source with transparency; it fills it so the JPEG
does not print black. `[0]` on the source file selects a GIF's first frame.)

| file | source | licence note |
| --- | --- | --- |
| `claude-code.jpg` | first frame of `demo.gif` in [anthropics/claude-code](https://github.com/anthropics/claude-code) | the repository's own terms (see its `LICENSE.md`); used here only to show the tool, not redistributed on its own |
| `dawn.jpg` | `assets/hero.png` in [andrewmd5/dawn](https://github.com/andrewmd5/dawn) | dawn is MIT licensed |
| `fastfetch.jpg` | `screenshots/example1.png` in [fastfetch-cli/fastfetch](https://github.com/fastfetch-cli/fastfetch) | fastfetch is MIT licensed |
| `kitten.jpg` | `docs/screenshots/diff.png` in [kovidgoyal/kitty](https://github.com/kovidgoyal/kitty) (the `kitten diff` screenshot, which shows an image diff — the closest upstream image to what this item does) | kitty is GPL-3.0 licensed |
| `opencode.jpg` | `packages/web/src/assets/lander/screenshot.png` in [anomalyco/opencode](https://github.com/anomalyco/opencode) | opencode is MIT licensed |
| `sigye.jpg` | first frame of `assets/demo.gif` in [am2rican5/sigye](https://github.com/am2rican5/sigye) | sigye is MIT licensed |

`fish-shell` has no picture: it is the launcher's own setup, not one upstream project, so there is
no single README to take a hero image from.
