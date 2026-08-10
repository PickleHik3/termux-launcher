# fastfetch (nix edition)

Nix counterpart of `recipes/termux/fastfetch`: fastfetch 2.67.0 with the
animated Kitty graphics patch, built from nixpkgs with ImageMagick, zlib,
and Chafa support.

Usage: add `overlay.nix` (and the patch next to it) to the device's
`~/.config/nix-on-droid/` and register it in the flake:

```nix
pkgs = import nixpkgs {
  system = "aarch64-linux";
  overlays = [ (import ./overlays.nix) ];
};
```

Then `nix-on-droid switch --flake ~/.config/nix-on-droid`.

Notes:
- `enableParallelBuilding = false` — gcc's `as` spawn is flaky under
  proot at high `-j`.
- On-device builds need the app foregrounded: the ROM blocks the app
  uid's network in the background (`blocked=APP_BACKGROUND`).
- The deployed overlay embeds a whitespace-identical copy of the patch;
  keep the bytes stable or the store hash (and a ~20 min phone rebuild)
  churns.
- Config knob that pairs with this: `logo.printRemaining = true` in
  `config.jsonc` when the module list is shorter than the logo.
