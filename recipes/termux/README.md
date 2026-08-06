# Termux build recipes

These recipes build terminal applications that exercise Termux Launcher's graphics features but
need patches or build options beyond the packages currently available from the Termux repositories.

- [`sigye`](sigye/build.sh) pins upstream v0.6.0 and replaces its unsupported Android `arboard`
  clipboard path with `termux-clipboard-set`.
- [`fastfetch`](fastfetch/build.sh) pins upstream v2.67.0, enables ImageMagick/zlib support, and adds
  animated GIF upload and playback through the Kitty graphics protocol.

See [Building terminal showcase tools](../../docs/en/Building_Terminal_Showcase_Tools.md) for package
dependencies, installation, configuration, and troubleshooting.
