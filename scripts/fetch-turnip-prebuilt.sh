#!/usr/bin/env bash
# Experiment only (exp/wayland-turnip): copies the Turnip Vulkan driver and libadrenotools, built
# outside this tree, into a git-ignored build directory for -PwaylandTurnip=true. Nothing it
# copies is ever committed.
#
#   scripts/fetch-turnip-prebuilt.sh <outdir>
#
# Source: $TURNIP_PREBUILT_DIR, default ~/.cache/wayland-research/turnip-build/out (made by that
# directory's build.sh; its NOTES.md has versions and options). Output, all arm64-v8a:
#   <outdir>/jniLibs/arm64-v8a/lib{hook_impl,main_hook,file_redirect_hook,gsl_alloc_hook}.so
#       the adrenotools hooks, which must sit in the app's nativeLibraryDir;
#   <outdir>/assets/turnip/libvulkan_freedreno.so (+ .sha256)
#       the driver, copied by the app into its files dir at first use, never into lib/;
#   <outdir>/adrenotools/lib{adrenotools,linkernsbypass}.a
#       linked into libtlwayland.so (arm64 only) by wayland-host/build.rs.
set -euo pipefail

OUT="${1:?usage: fetch-turnip-prebuilt.sh <outdir>}"
SRC="${TURNIP_PREBUILT_DIR:-$HOME/.cache/wayland-research/turnip-build/out}"

HOOKS=(libhook_impl.so libmain_hook.so libfile_redirect_hook.so libgsl_alloc_hook.so)
need=("$SRC/libvulkan_freedreno.so" "$SRC/adrenotools-static/libadrenotools.a"
    "$SRC/adrenotools-static/liblinkernsbypass.a")
for h in "${HOOKS[@]}"; do
    need+=("$SRC/lib/arm64-v8a/$h")
done
missing=0
for f in "${need[@]}"; do
    if [[ ! -s "$f" ]]; then
        echo "fetch-turnip-prebuilt: missing $f" >&2
        missing=1
    fi
done
if [[ $missing -ne 0 ]]; then
    echo "fetch-turnip-prebuilt: build them first (see $SRC/../NOTES.md and build.sh)," \
        "or point TURNIP_PREBUILT_DIR at a directory with that layout" >&2
    exit 1
fi

mkdir -p "$OUT/jniLibs/arm64-v8a" "$OUT/assets/turnip" "$OUT/adrenotools"
for h in "${HOOKS[@]}"; do
    install -m 0644 "$SRC/lib/arm64-v8a/$h" "$OUT/jniLibs/arm64-v8a/$h"
done
install -m 0644 "$SRC/adrenotools-static/libadrenotools.a" "$SRC/adrenotools-static/liblinkernsbypass.a" \
    "$OUT/adrenotools/"
driver="$OUT/assets/turnip/libvulkan_freedreno.so"
install -m 0644 "$SRC/libvulkan_freedreno.so" "$driver"
# The app copies the driver again only when this changes.
sha256sum "$driver" | cut -d' ' -f1 > "$driver.sha256"

echo "fetch-turnip-prebuilt: $(stat -c %s "$driver") bytes driver, sha256 $(cat "$driver.sha256")"
for h in "${HOOKS[@]}"; do
    echo "fetch-turnip-prebuilt: $(stat -c %s "$OUT/jniLibs/arm64-v8a/$h") bytes $h"
done
