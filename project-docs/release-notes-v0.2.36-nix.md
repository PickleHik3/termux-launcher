# Changelog — v0.2.36-nix

The Nix edition of the [v0.2.36 release](https://github.com/PickleHik3/termux-launcher/releases/tag/v0.2.36) — all of the same changes, plus the edition notes below.

## Edition notes

- Colours from `~/.termux/colors.properties`: a newly generated file is now
  picked up, instead of the old palette continuing to be served (#16).
- This edition ships for 64-bit devices only — `arm64-v8a` and `x86_64`.
- `setup-launcher` is not part of this edition; packages come from nixpkgs.
