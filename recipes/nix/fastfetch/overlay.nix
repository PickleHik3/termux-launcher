# Overlays for the nix edition's package set.
final: prev: {
  # fastfetch with the kitty animation-frames patch (animated gif logos,
  # matching the com.termux daily setup). Patch decodes gif frames via
  # ImageMagick's CoalesceImages, transmits them with the kitty animation
  # protocol, and places the logo through Unicode placeholders (U=1), both
  # of which the launcher terminal implements.
  fastfetch = prev.fastfetch.overrideAttrs (old: {
    version = "2.67.0";
    src = final.fetchFromGitHub {
      owner = "fastfetch-cli";
      repo = "fastfetch";
      tag = "2.67.0";
      hash = "sha256-IwptETUR3mDVxF7IkBwRMHVqbh8Wl39uiVl6yxXiJmw=";
    };
    patches = (old.patches or [ ]) ++ [ ./fastfetch-kitty-animation.patch ];
    # gcc spawning `as` is flaky under proot at high -j; build serially.
    enableParallelBuilding = false;
  });
}
