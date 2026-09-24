//! Cell styles and their SGR encoding.

use std::fmt::Write;

/// A 24-bit colour.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct Rgb(pub u8, pub u8, pub u8);

impl Rgb {
    /// Parses `#rrggbb` (the leading `#` is optional).
    pub fn parse_hex(s: &str) -> Option<Rgb> {
        let s = s.trim().trim_start_matches('#');
        if s.len() != 6 || !s.is_ascii() {
            return None;
        }
        let v = u32::from_str_radix(s, 16).ok()?;
        Some(Rgb((v >> 16) as u8, (v >> 8) as u8, v as u8))
    }

    /// Relative luminance, 0.0 (black) to 1.0 (white), sRGB weights without linearisation.
    pub fn luma(self) -> f32 {
        (0.2126 * self.0 as f32 + 0.7152 * self.1 as f32 + 0.0722 * self.2 as f32) / 255.0
    }

    /// Linear mix toward `other`; `t` = 0 keeps `self`, 1 gives `other`.
    pub fn mix(self, other: Rgb, t: f32) -> Rgb {
        let t = t.clamp(0.0, 1.0);
        let m = |a: u8, b: u8| (a as f32 + (b as f32 - a as f32) * t).round() as u8;
        Rgb(m(self.0, other.0), m(self.1, other.1), m(self.2, other.2))
    }
}

/// A cell colour: the terminal's own default, or a truecolor value.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Default)]
pub enum Color {
    #[default]
    Default,
    Rgb(Rgb),
}

impl From<Rgb> for Color {
    fn from(c: Rgb) -> Self {
        Color::Rgb(c)
    }
}

/// Underline shapes (SGR 4:n).
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Default)]
pub enum Underline {
    #[default]
    None,
    Single,
    Double,
    Curly,
    Dotted,
    Dashed,
}

/// Everything about how a cell is drawn except its text.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Default)]
pub struct Style {
    pub fg: Color,
    pub bg: Color,
    /// Underline colour (SGR 58); `Default` follows the text colour.
    pub ul_color: Color,
    pub underline: Underline,
    pub bold: bool,
    pub italic: bool,
    pub dim: bool,
    pub reverse: bool,
    /// Strikethrough (SGR 9): the old version in the facts strip when an update exists.
    pub strike: bool,
}

impl Style {
    pub const fn new() -> Style {
        Style {
            fg: Color::Default,
            bg: Color::Default,
            ul_color: Color::Default,
            underline: Underline::None,
            bold: false,
            italic: false,
            dim: false,
            reverse: false,
            strike: false,
        }
    }
    pub fn fg(mut self, c: impl Into<Color>) -> Style {
        self.fg = c.into();
        self
    }
    pub fn bg(mut self, c: impl Into<Color>) -> Style {
        self.bg = c.into();
        self
    }
    pub fn bold(mut self) -> Style {
        self.bold = true;
        self
    }
    pub fn italic(mut self) -> Style {
        self.italic = true;
        self
    }
    pub fn dim(mut self) -> Style {
        self.dim = true;
        self
    }
    pub fn reverse(mut self) -> Style {
        self.reverse = true;
        self
    }
    pub fn strike(mut self) -> Style {
        self.strike = true;
        self
    }
    pub fn underline(mut self, u: Underline) -> Style {
        self.underline = u;
        self
    }
    pub fn ul_color(mut self, c: impl Into<Color>) -> Style {
        self.ul_color = c.into();
        self
    }

    /// Appends the SGR sequence that sets exactly this style from any previous state:
    /// always starts with a reset (`0`), then only the attributes that are on.
    pub fn write_sgr(&self, out: &mut String) {
        out.push_str("\x1b[0");
        if self.bold {
            out.push_str(";1");
        }
        if self.dim {
            out.push_str(";2");
        }
        if self.italic {
            out.push_str(";3");
        }
        match self.underline {
            Underline::None => {}
            Underline::Single => out.push_str(";4"),
            Underline::Double => out.push_str(";4:2"),
            Underline::Curly => out.push_str(";4:3"),
            Underline::Dotted => out.push_str(";4:4"),
            Underline::Dashed => out.push_str(";4:5"),
        }
        if self.reverse {
            out.push_str(";7");
        }
        if self.strike {
            out.push_str(";9");
        }
        if let Color::Rgb(Rgb(r, g, b)) = self.fg {
            let _ = write!(out, ";38;2;{r};{g};{b}");
        }
        if let Color::Rgb(Rgb(r, g, b)) = self.bg {
            let _ = write!(out, ";48;2;{r};{g};{b}");
        }
        if let Color::Rgb(Rgb(r, g, b)) = self.ul_color {
            // Colon form is the one kitty documents for SGR 58.
            let _ = write!(out, ";58:2::{r}:{g}:{b}");
        }
        out.push('m');
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sgr_default_is_plain_reset() {
        let mut s = String::new();
        Style::new().write_sgr(&mut s);
        assert_eq!(s, "\x1b[0m");
    }

    #[test]
    fn sgr_full() {
        let mut s = String::new();
        Style::new()
            .fg(Rgb(1, 2, 3))
            .bg(Rgb(4, 5, 6))
            .bold()
            .italic()
            .underline(Underline::Curly)
            .ul_color(Rgb(7, 8, 9))
            .write_sgr(&mut s);
        assert_eq!(s, "\x1b[0;1;3;4:3;38;2;1;2;3;48;2;4;5;6;58:2::7:8:9m");
    }

    #[test]
    fn underline_shapes() {
        for (u, code) in [(Underline::Double, "4:2"), (Underline::Dotted, "4:4"), (Underline::Dashed, "4:5")]
        {
            let mut s = String::new();
            Style::new().underline(u).write_sgr(&mut s);
            assert_eq!(s, format!("\x1b[0;{code}m"));
        }
    }

    #[test]
    fn strikethrough_is_sgr_9() {
        let mut s = String::new();
        Style::new().strike().dim().write_sgr(&mut s);
        assert_eq!(s, "\x1b[0;2;9m");
    }

    #[test]
    fn hex_parse() {
        assert_eq!(Rgb::parse_hex("#0a0B0c"), Some(Rgb(10, 11, 12)));
        assert_eq!(Rgb::parse_hex("zz"), None);
    }
}
