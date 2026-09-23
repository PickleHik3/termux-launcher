use std::path::PathBuf;
use std::process::Command;

/// Bakes a hash of this crate's sources into the binary as `TLSTORE_UI_SRC_HASH`, so
/// `tlstore-ui --version` can prove which sources it was built from. `app/build.gradle`'s
/// `checkTlstoreUiFresh` task recomputes the same hash from the working tree with
/// `scripts/tlstore/ui-src-hash.sh` (the single source of truth for the algorithm — this file
/// only ever shells out to it) and fails the build if a committed `tlstore-ui-<abi>` asset
/// disagrees, which is how a source change without a `build-ui.sh --install` run gets caught
/// before it ships.
fn main() {
    let manifest_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let script = manifest_dir.join("../../scripts/tlstore/ui-src-hash.sh");

    let hash = Command::new("bash")
        .arg(&script)
        .output()
        .ok()
        .filter(|output| output.status.success())
        .map(|output| String::from_utf8_lossy(&output.stdout).trim().to_string())
        .filter(|hash| !hash.is_empty())
        .unwrap_or_else(|| "unknown".to_string());
    println!("cargo:rustc-env=TLSTORE_UI_SRC_HASH={hash}");

    // Any source change must re-run this, so the baked-in hash never goes stale within a build.
    println!("cargo:rerun-if-changed={}", manifest_dir.join("src").display());
    println!("cargo:rerun-if-changed={}", manifest_dir.join("Cargo.toml").display());
    println!("cargo:rerun-if-changed={}", manifest_dir.join("Cargo.lock").display());
    println!("cargo:rerun-if-changed={}", script.display());
}
