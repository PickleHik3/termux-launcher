//! Links libadrenotools (static, C++) into the arm64 Android library when the `turnip` feature is
//! on. The archives come from TL_ADRENOTOOLS_DIR (scripts/fetch-turnip-prebuilt.sh); nothing is
//! linked for any other target or without the feature.

fn main() {
    println!("cargo:rerun-if-env-changed=TL_ADRENOTOOLS_DIR");
    let turnip = std::env::var_os("CARGO_FEATURE_TURNIP").is_some();
    let os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    let arch = std::env::var("CARGO_CFG_TARGET_ARCH").unwrap_or_default();
    if !turnip || os != "android" || arch != "aarch64" {
        return;
    }
    let Some(dir) = std::env::var_os("TL_ADRENOTOOLS_DIR") else {
        panic!("the turnip feature needs TL_ADRENOTOOLS_DIR (run scripts/fetch-turnip-prebuilt.sh)");
    };
    let dir = std::path::PathBuf::from(dir);
    for lib in ["libadrenotools.a", "liblinkernsbypass.a"] {
        let path = dir.join(lib);
        println!("cargo:rerun-if-changed={}", path.display());
        if !path.is_file() {
            panic!("{} is missing (run scripts/fetch-turnip-prebuilt.sh)", path.display());
        }
    }
    println!("cargo:rustc-link-search=native={}", dir.display());
    println!("cargo:rustc-link-lib=static=adrenotools");
    println!("cargo:rustc-link-lib=static=linkernsbypass");
    // Both archives are C++ built against the NDK's static libc++.
    println!("cargo:rustc-link-lib=c++_static");
    println!("cargo:rustc-link-lib=c++abi");
    println!("cargo:rustc-link-lib=dl");
    println!("cargo:rustc-link-lib=log");
}
