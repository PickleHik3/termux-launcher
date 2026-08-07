# Kitty protocols and terminal compatibility

Termux Launcher implements modern application-facing terminal protocols directly in the native
terminal. Programs negotiate them; users normally do not enable a compatibility switch.

## Terminal identity and detection

Every Termux shell receives:

```sh
TERM_PROGRAM=termux-launcher
TERM_PROGRAM_VERSION=<installed version>
```

XTVERSION reports `termux-launcher(version)`. XTSMGRAPHICS reports the Sixel color-register count and
geometry for the current screen. Capability detectors such as chafa and notcurses can therefore pick
a supported renderer instead of relying on a conservative terminal-name fallback.

## Kitty keyboard protocol

Supported negotiation includes:

- disambiguated escape codes;
- key press, repeat, and release events;
- alternate key values;
- all-keys-as-escape-codes mode;
- associated text; and
- independent flags and bounded mode stacks for main and alternate screens.

Applications opt in and out themselves. Programs that do not negotiate the protocol continue through
the normal Termux key encoder.

## Multiple cursors

The Kitty multiple-cursors protocol supports point and rectangular cursors, cursor shape, and color.
This is independent of the launcher's animated input cursor trail.

## Kitty graphics Tier 2

The terminal supports:

- direct PNG and raw RGB/RGBA transmission;
- zlib-compressed raw pixels and chunked transfers;
- stored images by image ID or number;
- placements with source cropping, cell scaling, sub-cell offsets, and z-index;
- acknowledgements, quiet modes, and delete forms; and
- text-safe negative-z placement: text remains visible while the image occupies surrounding blanks.

Examples that work without extra launcher configuration include:

```sh
timg -pk image.png
chafa -f kitty image.png
```

Yazi can also use Kitty image previews when configured to select that backend.

## Kitty graphics animation

Animation support includes frame upload, partial-frame rectangles, base-frame and background-color
composition, frame gaps, animation control, composition, and frame deletion.

Both client-driven and terminal-driven playback are supported. With terminal-driven playback, a GIF
uploaded as Kitty frames keeps animating on the terminal's own clock after the sender exits.

The repository's patched Fastfetch recipe demonstrates this path without replacing APT-owned files.
See [Building terminal showcase tools](Building_Terminal_Showcase_Tools.md).

## Sixel and iTerm paths

Existing Sixel and iTerm bitmap rendering remain available alongside Kitty graphics. Applications can
choose their preferred protocol from capability replies or explicit command-line options.

## Current boundaries

- Kitty Unicode placeholders are not implemented.
- Shared-memory and file-based Kitty transmissions are not implemented.
- Unsupported or excessive requests return bounded protocol errors rather than consuming unbounded
  memory.
- Image geometry follows terminal cells, so changing a pane's size or font metrics may cause the
  sending application to redraw or resend an image.

For the complete protocol-level feature list, see
[Rendering and application compatibility](Terminal_Modernization.md#rendering-and-application-compatibility).
