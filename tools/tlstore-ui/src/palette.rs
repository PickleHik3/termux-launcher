//! Material-style colour roles. The launcher exports its wallpaper palette to
//! `~/.termux/material-colors-{dark,light}.properties` (and `material-colors.properties` for the
//! current mode); the store reads the roles it needs from there and falls back to a built-in
//! neutral palette elsewhere.

use std::path::{Path, PathBuf};

use crate::render::{Rgb, Style};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Palette {
    pub dark: bool,
    /// Cursor, selection, key letters, new versions (Material `primary`).
    pub accent: Rgb,
    /// Text on an accent fill (`on_primary`).
    pub on_accent: Rgb,
    /// Soft filled chips and bars (`secondary_container`).
    pub tonal: Rgb,
    /// Text on a tonal fill (`on_secondary_container`).
    pub on_tonal: Rgb,
    /// Page background (`surface`).
    pub surface: Rgb,
    /// Body text (`on_surface`).
    pub ink: Rgb,
    /// Secondary text, leaders, captions (`on_surface_variant`).
    pub dim: Rgb,
    /// Hairlines (`outline_variant`).
    pub rule: Rgb,
}

impl Palette {
    pub const fn builtin_dark() -> Palette {
        Palette {
            dark: true,
            accent: Rgb(0xd0, 0xbc, 0xff),
            on_accent: Rgb(0x38, 0x1e, 0x72),
            tonal: Rgb(0x4a, 0x44, 0x58),
            on_tonal: Rgb(0xe8, 0xde, 0xf8),
            surface: Rgb(0x14, 0x12, 0x18),
            ink: Rgb(0xe6, 0xe0, 0xe9),
            dim: Rgb(0xca, 0xc4, 0xd0),
            rule: Rgb(0x49, 0x45, 0x4f),
        }
    }

    pub const fn builtin_light() -> Palette {
        Palette {
            dark: false,
            accent: Rgb(0x65, 0x55, 0x8f),
            on_accent: Rgb(0xff, 0xff, 0xff),
            tonal: Rgb(0xe8, 0xde, 0xf8),
            on_tonal: Rgb(0x1d, 0x19, 0x2b),
            surface: Rgb(0xfe, 0xf7, 0xff),
            ink: Rgb(0x1d, 0x1b, 0x20),
            dim: Rgb(0x49, 0x45, 0x4f),
            rule: Rgb(0xca, 0xc4, 0xd0),
        }
    }

    pub fn builtin(dark: bool) -> Palette {
        if dark {
            Palette::builtin_dark()
        } else {
            Palette::builtin_light()
        }
    }

    /// Overlays roles found in a `key=#rrggbb` properties text onto the built-in palette.
    pub fn from_properties(text: &str, dark: bool) -> Palette {
        let mut p = Palette::builtin(dark);
        for line in text.lines() {
            let line = line.trim();
            if line.starts_with('#') || line.starts_with('!') {
                continue;
            }
            let Some((k, v)) = line.split_once(['=', ':']) else { continue };
            let Some(c) = Rgb::parse_hex(v.trim().trim_start_matches('\\')) else { continue };
            match k.trim() {
                "primary" => p.accent = c,
                "on_primary" => p.on_accent = c,
                "secondary_container" => p.tonal = c,
                "on_secondary_container" => p.on_tonal = c,
                "surface" => p.surface = c,
                "on_surface" => p.ink = c,
                "on_surface_variant" => p.dim = c,
                "outline_variant" => p.rule = c,
                _ => {}
            }
        }
        p
    }

    /// The launcher's exported palette for this mode, or the built-in one. `home` is usually
    /// `$HOME`.
    pub fn load(home: &Path, dark: bool) -> Palette {
        let dir = home.join(".termux");
        let mode_file: PathBuf = dir.join(if dark {
            "material-colors-dark.properties"
        } else {
            "material-colors-light.properties"
        });
        for f in [mode_file, dir.join("material-colors.properties")] {
            if let Ok(t) = std::fs::read_to_string(&f) {
                return Palette::from_properties(&t, dark);
            }
        }
        Palette::builtin(dark)
    }

    /// The mode the launcher last exported (`mode=dark|light` in material-colors.properties).
    pub fn exported_mode(home: &Path) -> Option<bool> {
        let t = std::fs::read_to_string(home.join(".termux/material-colors.properties")).ok()?;
        t.lines().find_map(|l| match l.trim().split_once('=')? {
            ("mode", "dark") => Some(true),
            ("mode", "light") => Some(false),
            _ => None,
        })
    }

    // Ready-made styles; text sits on the terminal's own background (Color::Default).
    pub fn ink_s(&self) -> Style {
        Style::new().fg(self.ink)
    }
    pub fn dim_s(&self) -> Style {
        Style::new().fg(self.dim)
    }
    pub fn accent_s(&self) -> Style {
        Style::new().fg(self.accent)
    }
    pub fn rule_s(&self) -> Style {
        Style::new().fg(self.rule)
    }
    /// A filled chip or selection bar.
    pub fn tonal_s(&self) -> Style {
        Style::new().fg(self.on_tonal).bg(self.tonal)
    }
    /// A strong filled button.
    pub fn accent_fill_s(&self) -> Style {
        Style::new().fg(self.on_accent).bg(self.accent)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_launcher_roles() {
        let p = Palette::from_properties(
            "# exported\nprimary=#112233\non_surface=#abcdef\noutline_variant=#010203\nmode=dark\nbogus=#zz\n",
            true,
        );
        assert_eq!(p.accent, Rgb(0x11, 0x22, 0x33));
        assert_eq!(p.ink, Rgb(0xab, 0xcd, 0xef));
        assert_eq!(p.rule, Rgb(1, 2, 3));
        assert_eq!(p.surface, Palette::builtin_dark().surface);
    }

    #[test]
    fn falls_back_when_missing() {
        let p = Palette::load(Path::new("/nonexistent-home"), false);
        assert_eq!(p, Palette::builtin_light());
    }
}
