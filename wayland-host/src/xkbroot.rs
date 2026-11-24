//! Whether a directory holds the xkb data a keymap needs.
//!
//! libxkbcommon's `xkb_context_new` returns NULL when its include path is unusable, and the
//! `xkbcommon` crate wraps that pointer without telling the caller, so smithay's
//! `add_keyboard` then dereferences null and the host dies. The directory is checked first and
//! the seat gets no keyboard when it fails.

use std::path::Path;

/// What the default rules, model and layout read: `rules/evdev` and the four component dirs.
pub fn usable(root: &Path) -> bool {
    root.join("rules").join("evdev").is_file()
        && ["symbols", "keycodes", "types", "compat"].iter().all(|d| root.join(d).is_dir())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn tmp(name: &str) -> std::path::PathBuf {
        let dir = std::env::temp_dir().join(format!("tlxkb-{name}-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn a_missing_directory_is_not_usable() {
        assert!(!usable(Path::new("/nonexistent/share/X11/xkb")));
    }

    #[test]
    fn an_empty_directory_is_not_usable() {
        let dir = tmp("empty");
        assert!(!usable(&dir));
        let _ = fs::remove_dir_all(dir);
    }

    #[test]
    fn rules_and_all_four_directories_make_it_usable() {
        let dir = tmp("full");
        fs::create_dir_all(dir.join("rules")).unwrap();
        fs::write(dir.join("rules/evdev"), "").unwrap();
        for d in ["symbols", "keycodes", "types"] {
            fs::create_dir_all(dir.join(d)).unwrap();
        }
        assert!(!usable(&dir), "compat is still missing");
        fs::create_dir_all(dir.join("compat")).unwrap();
        assert!(usable(&dir));
        let _ = fs::remove_dir_all(dir);
    }
}
