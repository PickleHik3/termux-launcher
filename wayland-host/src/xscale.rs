//! What X11 toolkits under rootless Xwayland are told so they draw at the output scale.
//!
//! X windows are configured in pixels and Xwayland's buffers are shown 1:1, so nothing on the
//! compositor side scales them (that would blur them and cost a pass per frame). Instead the
//! toolkits are told the scale the way an X desktop tells them:
//!
//! - XSETTINGS (read live by GTK 2/3/4 and Chromium/Electron): `Xft/DPI` is 96 x scale;
//!   `Gdk/WindowScalingFactor` is the whole part of the scale, which GTK 3/4 use to draw their
//!   widgets bigger, and `Gdk/UnscaledDPI` the rest, which they use for text, so text comes out
//!   at the full scale even where widgets can only scale by a whole number.
//! - The `Xft.dpi` resource on the root window (`RESOURCE_MANAGER`, what `xrdb` writes): read
//!   once at start by Xft clients (xterm with an Xft face, Qt's xcb platform, Emacs, Tk with
//!   Xft) for their fonts.
//!
//! X settings are in 1024ths of a DPI.

/// The DPI X toolkits take as scale 1.
pub const BASE_DPI: f64 = 96.0;
const XSETTINGS_DPI_UNIT: f64 = 1024.0;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ToolkitScale {
    /// `Gdk/WindowScalingFactor`: the whole part of the scale, at least 1.
    pub window_scale: i32,
    /// `Xft/DPI`, in 1024ths.
    pub xft_dpi_1024: i32,
    /// `Gdk/UnscaledDPI`, in 1024ths: the DPI that, times `window_scale`, is `Xft/DPI`.
    pub unscaled_dpi_1024: i32,
    /// The `Xft.dpi` resource, whole DPI.
    pub xft_dpi: i32,
}

/// The toolkit settings for an output `scale` (already sane: finite and positive; anything
/// else is taken as 1).
pub fn toolkit_scale(scale: f64) -> ToolkitScale {
    let s = if scale.is_finite() && scale > 0.0 { scale } else { 1.0 };
    let window_scale = (s.floor() as i32).max(1);
    let dpi = BASE_DPI * s;
    ToolkitScale {
        window_scale,
        xft_dpi_1024: (dpi * XSETTINGS_DPI_UNIT).round() as i32,
        unscaled_dpi_1024: (dpi / window_scale as f64 * XSETTINGS_DPI_UNIT).round() as i32,
        xft_dpi: dpi.round() as i32,
    }
}

/// The `RESOURCE_MANAGER` text for `t`, as `xrdb` would write it.
pub fn resources(t: &ToolkitScale) -> String {
    format!("Xft.dpi:\t{}\n", t.xft_dpi)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scale_one_is_the_x_default() {
        let t = toolkit_scale(1.0);
        assert_eq!(t, ToolkitScale { window_scale: 1, xft_dpi_1024: 96 * 1024, unscaled_dpi_1024: 96 * 1024, xft_dpi: 96 });
    }

    #[test]
    fn a_fractional_scale_splits_into_widgets_and_text() {
        // 420 dpi phone at its density scale: widgets at 2, text at 2.5.
        let t = toolkit_scale(2.5);
        assert_eq!(t.window_scale, 2);
        assert_eq!(t.xft_dpi, 240);
        assert_eq!(t.xft_dpi_1024, 240 * 1024);
        assert_eq!(t.unscaled_dpi_1024, 120 * 1024);
        // GTK's text DPI is the unscaled one times the window scale: the full scale again.
        assert_eq!(t.unscaled_dpi_1024 * t.window_scale, t.xft_dpi_1024);
    }

    #[test]
    fn below_two_widgets_stay_at_one() {
        let t = toolkit_scale(1.75);
        assert_eq!((t.window_scale, t.xft_dpi), (1, 168));
        assert_eq!(t.unscaled_dpi_1024, t.xft_dpi_1024);
    }

    #[test]
    fn a_scale_below_one_never_gives_a_zero_window_scale() {
        let t = toolkit_scale(0.5);
        assert_eq!((t.window_scale, t.xft_dpi), (1, 48));
    }

    #[test]
    fn a_bad_scale_is_one() {
        assert_eq!(toolkit_scale(f64::NAN), toolkit_scale(1.0));
        assert_eq!(toolkit_scale(0.0), toolkit_scale(1.0));
    }

    #[test]
    fn resources_are_xrdb_lines() {
        assert_eq!(resources(&toolkit_scale(2.5)), "Xft.dpi:\t240\n");
    }
}
