//! Experiment (exp/wayland-turnip): the pure rules of the direct presenter, which shows a client's
//! dmabuf through the bundled Turnip driver (import, GPU blit into a swapchain on the window's
//! Surface) instead of copying it with the CPU. The Vulkan side is `android::direct`; everything
//! here is host-tested.

use crate::geometry::Rect;

pub const DRM_FORMAT_MOD_LINEAR: u64 = 0;
/// "Implicit" modifier: never advertised, never imported.
pub const DRM_FORMAT_MOD_INVALID: u64 = 0x00ff_ffff_ffff_ffff;

/// How a toplevel reaches its Surface.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Mode {
    /// CPU copy with ANativeWindow_lock (the shipped path).
    Copy,
    /// Vulkan swapchain on the Surface; the client's dmabuf is imported and blitted.
    Direct,
}

impl Mode {
    pub fn name(self) -> &'static str {
        match self {
            Mode::Copy => "copy",
            Mode::Direct => "direct",
        }
    }
}

/// The mode a toplevel gets when it is created: direct only while it is asked for and the driver
/// came up with everything direct needs. A window keeps its mode for life (except for a fall back
/// to copy), so switching the toggle affects only new windows.
pub fn mode_for_new_window(requested: bool, available: bool) -> Mode {
    if requested && available {
        Mode::Direct
    } else {
        Mode::Copy
    }
}

/// What a direct window does with one committed buffer.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Route {
    Direct,
    /// The window goes over to copy for good; the reason is logged.
    FallBack(&'static str),
}

/// A wl_shm buffer, or a dmabuf whose format and modifier the driver cannot import, sends the
/// window to the copy path: mixing a CPU lock and a swapchain on one Surface frame by frame is not
/// possible (a window has one producer API connected at a time).
pub fn route(is_dmabuf: bool, format_known: bool, modifier_importable: bool) -> Route {
    if !is_dmabuf {
        Route::FallBack("an shm buffer")
    } else if !format_known {
        Route::FallBack("a format direct does not take")
    } else if !modifier_importable {
        Route::FallBack("a modifier the driver cannot import")
    } else {
        Route::Direct
    }
}

/// One DRM modifier of one format as the driver reports it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ModifierCaps {
    pub modifier: u64,
    /// Memory planes (drmFormatModifierPlaneCount).
    pub plane_count: u32,
    /// The tiling features include BLIT_SRC.
    pub blit_src: bool,
    /// vkGetPhysicalDeviceImageFormatProperties2 with DMA_BUF handle says IMPORTABLE.
    pub importable: bool,
}

impl ModifierCaps {
    /// Direct can take a buffer of this modifier: one plane, importable as a dmabuf, blittable.
    pub fn usable(&self) -> bool {
        self.plane_count == 1 && self.blit_src && self.importable && self.modifier != DRM_FORMAT_MOD_INVALID
    }
}

/// The modifiers advertised for one format while direct is on: LINEAR first and always (the copy
/// path can read it, so a window that falls back still shows), then every modifier the driver can
/// import and blit, in the driver's order, each once. INVALID is never advertised.
pub fn advertised_modifiers(caps: &[ModifierCaps]) -> Vec<u64> {
    let mut out = vec![DRM_FORMAT_MOD_LINEAR];
    for c in caps {
        if c.usable() && !out.contains(&c.modifier) {
            out.push(c.modifier);
        }
    }
    out
}

/// Whether `modifier` is one the driver reported as usable for the format.
pub fn modifier_usable(caps: &[ModifierCaps], modifier: u64) -> bool {
    caps.iter().any(|c| c.modifier == modifier && c.usable())
}

// VkCompositeAlphaFlagBitsKHR.
pub const COMPOSITE_ALPHA_OPAQUE: u32 = 0x1;
pub const COMPOSITE_ALPHA_PRE_MULTIPLIED: u32 = 0x2;
pub const COMPOSITE_ALPHA_POST_MULTIPLIED: u32 = 0x4;
pub const COMPOSITE_ALPHA_INHERIT: u32 = 0x8;

/// The composite alpha a direct swapchain uses: PRE_MULTIPLIED, then POST_MULTIPLIED, then
/// INHERIT (the Surface's own setting; a TextureView that is not opaque blends), and OPAQUE only
/// when nothing else is offered. The flag says whether the choice keeps per-pixel alpha.
pub fn choose_composite_alpha(supported: u32) -> Option<(u32, bool)> {
    for bit in [COMPOSITE_ALPHA_PRE_MULTIPLIED, COMPOSITE_ALPHA_POST_MULTIPLIED, COMPOSITE_ALPHA_INHERIT] {
        if supported & bit != 0 {
            return Some((bit, true));
        }
    }
    if supported & COMPOSITE_ALPHA_OPAQUE != 0 {
        return Some((COMPOSITE_ALPHA_OPAQUE, false));
    }
    None
}

/// `OPAQUE|INHERIT` and the like, for the log.
pub fn composite_alpha_names(bits: u32) -> String {
    let names = [
        (COMPOSITE_ALPHA_OPAQUE, "OPAQUE"),
        (COMPOSITE_ALPHA_PRE_MULTIPLIED, "PRE_MULTIPLIED"),
        (COMPOSITE_ALPHA_POST_MULTIPLIED, "POST_MULTIPLIED"),
        (COMPOSITE_ALPHA_INHERIT, "INHERIT"),
    ];
    let parts: Vec<&str> = names.iter().filter(|(b, _)| bits & b != 0).map(|(_, n)| *n).collect();
    if parts.is_empty() {
        "none".into()
    } else {
        parts.join("|")
    }
}

// VkFormat values.
pub const VK_FORMAT_R8G8B8A8_UNORM: i32 = 37;
pub const VK_FORMAT_B8G8R8A8_UNORM: i32 = 44;

/// The Vulkan format a client fourcc is imported as, and whether its alpha byte is padding (an X
/// format, made opaque after the blit). DRM fourccs name a little-endian word: ARGB8888 is the
/// bytes B, G, R, A, which is VK_FORMAT_B8G8R8A8_UNORM.
pub fn vk_format_for(fourcc: u32) -> Option<(i32, bool)> {
    match fourcc {
        crate::pixels::DRM_FORMAT_ARGB8888 => Some((VK_FORMAT_B8G8R8A8_UNORM, false)),
        crate::pixels::DRM_FORMAT_XRGB8888 => Some((VK_FORMAT_B8G8R8A8_UNORM, true)),
        crate::pixels::DRM_FORMAT_ABGR8888 => Some((VK_FORMAT_R8G8B8A8_UNORM, false)),
        crate::pixels::DRM_FORMAT_XBGR8888 => Some((VK_FORMAT_R8G8B8A8_UNORM, true)),
        _ => None,
    }
}

/// The blit for a shown rectangle (buffer pixels, as the copy path cuts it): the source corners
/// and the destination size, which is also the swapchain's extent before the surface's limits.
/// None for an empty rectangle.
pub fn blit_region(shown: Rect) -> Option<([i32; 2], [i32; 2], (u32, u32))> {
    let (x, y, w, h) = shown;
    if w <= 0 || h <= 0 || x < 0 || y < 0 {
        return None;
    }
    let x1 = x.checked_add(w)?;
    let y1 = y.checked_add(h)?;
    Some(([x, y], [x1, y1], (w as u32, h as u32)))
}

/// The swapchain extent for `size`, kept within the surface's limits (a 0 maximum means none).
pub fn swapchain_extent(size: (u32, u32), min: (u32, u32), max: (u32, u32)) -> (u32, u32) {
    let clamp = |v: u32, lo: u32, hi: u32| {
        let v = v.max(lo).max(1);
        if hi > 0 {
            v.min(hi)
        } else {
            v
        }
    };
    (clamp(size.0, min.0, max.0), clamp(size.1, min.1, max.1))
}

/// Swapchain images to ask for: one more than the minimum, so the host never waits on the one the
/// consumer holds, within the maximum (0 = none).
pub fn swapchain_image_count(min: u32, max: u32) -> u32 {
    let want = min.max(1) + 1;
    if max > 0 {
        want.min(max)
    } else {
        want
    }
}

/// Microseconds the host spent per frame for one window, for the once-a-second line.
#[derive(Clone, Copy, Debug, Default)]
pub struct HostTime {
    pub frames: u32,
    pub sum_us: u64,
    pub max_us: u64,
}

impl HostTime {
    pub fn add(&mut self, us: u64) {
        self.frames = self.frames.saturating_add(1);
        self.sum_us = self.sum_us.saturating_add(us);
        self.max_us = self.max_us.max(us);
    }

    pub fn avg(&self) -> u64 {
        if self.frames == 0 {
            0
        } else {
            self.sum_us / self.frames as u64
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::pixels::{DRM_FORMAT_ABGR8888, DRM_FORMAT_ARGB8888, DRM_FORMAT_XBGR8888, DRM_FORMAT_XRGB8888};

    const UBWC: u64 = 0x0500_0000_0000_0001;
    const TILED3: u64 = 0x0500_0000_0000_0004;

    fn caps(modifier: u64, plane_count: u32, blit_src: bool, importable: bool) -> ModifierCaps {
        ModifierCaps { modifier, plane_count, blit_src, importable }
    }

    #[test]
    fn a_new_window_is_direct_only_when_asked_and_available() {
        assert_eq!(mode_for_new_window(true, true), Mode::Direct);
        assert_eq!(mode_for_new_window(true, false), Mode::Copy);
        assert_eq!(mode_for_new_window(false, true), Mode::Copy);
        assert_eq!(mode_for_new_window(false, false), Mode::Copy);
    }

    #[test]
    fn shm_and_unknown_buffers_fall_back_to_copy() {
        assert_eq!(route(true, true, true), Route::Direct);
        assert!(matches!(route(false, true, true), Route::FallBack(_)));
        assert!(matches!(route(true, false, true), Route::FallBack(_)));
        assert!(matches!(route(true, true, false), Route::FallBack(_)));
    }

    #[test]
    fn linear_is_always_advertised_first_and_once() {
        let table = [caps(UBWC, 1, true, true), caps(DRM_FORMAT_MOD_LINEAR, 1, true, true), caps(TILED3, 1, true, true)];
        assert_eq!(advertised_modifiers(&table), vec![DRM_FORMAT_MOD_LINEAR, UBWC, TILED3]);
        assert_eq!(advertised_modifiers(&[]), vec![DRM_FORMAT_MOD_LINEAR]);
    }

    #[test]
    fn modifiers_direct_cannot_take_are_not_advertised() {
        let table = [
            caps(UBWC, 2, true, true),
            caps(TILED3, 1, false, true),
            caps(0x0500_0000_0000_0002, 1, true, false),
            caps(DRM_FORMAT_MOD_INVALID, 1, true, true),
        ];
        assert_eq!(advertised_modifiers(&table), vec![DRM_FORMAT_MOD_LINEAR]);
        assert!(!modifier_usable(&table, UBWC));
        assert!(!modifier_usable(&table, DRM_FORMAT_MOD_INVALID));
    }

    #[test]
    fn a_modifier_is_usable_only_as_the_driver_reported_it() {
        let table = [caps(UBWC, 1, true, true)];
        assert!(modifier_usable(&table, UBWC));
        assert!(!modifier_usable(&table, TILED3));
    }

    #[test]
    fn composite_alpha_prefers_modes_that_keep_alpha() {
        let all = COMPOSITE_ALPHA_OPAQUE | COMPOSITE_ALPHA_PRE_MULTIPLIED | COMPOSITE_ALPHA_POST_MULTIPLIED | COMPOSITE_ALPHA_INHERIT;
        assert_eq!(choose_composite_alpha(all), Some((COMPOSITE_ALPHA_PRE_MULTIPLIED, true)));
        assert_eq!(
            choose_composite_alpha(COMPOSITE_ALPHA_OPAQUE | COMPOSITE_ALPHA_POST_MULTIPLIED),
            Some((COMPOSITE_ALPHA_POST_MULTIPLIED, true))
        );
        assert_eq!(choose_composite_alpha(COMPOSITE_ALPHA_OPAQUE | COMPOSITE_ALPHA_INHERIT), Some((COMPOSITE_ALPHA_INHERIT, true)));
        assert_eq!(choose_composite_alpha(COMPOSITE_ALPHA_OPAQUE), Some((COMPOSITE_ALPHA_OPAQUE, false)));
        assert_eq!(choose_composite_alpha(0), None);
        assert_eq!(composite_alpha_names(COMPOSITE_ALPHA_OPAQUE | COMPOSITE_ALPHA_INHERIT), "OPAQUE|INHERIT");
        assert_eq!(composite_alpha_names(0), "none");
    }

    #[test]
    fn fourccs_map_to_vulkan_formats_and_x_formats_are_made_opaque() {
        assert_eq!(vk_format_for(DRM_FORMAT_ARGB8888), Some((VK_FORMAT_B8G8R8A8_UNORM, false)));
        assert_eq!(vk_format_for(DRM_FORMAT_XRGB8888), Some((VK_FORMAT_B8G8R8A8_UNORM, true)));
        assert_eq!(vk_format_for(DRM_FORMAT_ABGR8888), Some((VK_FORMAT_R8G8B8A8_UNORM, false)));
        assert_eq!(vk_format_for(DRM_FORMAT_XBGR8888), Some((VK_FORMAT_R8G8B8A8_UNORM, true)));
        assert_eq!(vk_format_for(0x3231_5258), None);
    }

    #[test]
    fn the_blit_takes_the_shown_rectangle_one_to_one() {
        assert_eq!(blit_region((0, 0, 800, 600)), Some(([0, 0], [800, 600], (800, 600))));
        // A window geometry crop: the shadow margin is skipped.
        assert_eq!(blit_region((24, 16, 640, 480)), Some(([24, 16], [664, 496], (640, 480))));
        assert_eq!(blit_region((0, 0, 0, 10)), None);
        assert_eq!(blit_region((-1, 0, 10, 10)), None);
        assert_eq!(blit_region((i32::MAX, 0, 10, 10)), None);
    }

    #[test]
    fn the_crop_and_size_cap_of_the_copy_path_give_the_blit() {
        use crate::geometry::{clamp_to_limit, post_limit, surface_map};
        // Scale 2 client, 1000x800 buffer with a 10 logical px shadow: geometry (10,10) 480x380.
        let map = surface_map(1000, 800, 2, None, None);
        let full = map.to_buffer(map.logical_content(Some((10, 10, 480, 380))), 1000, 800);
        let shown = clamp_to_limit(full, post_limit(Some((960, 760)), (1080, 2400)));
        assert_eq!(shown, (20, 20, 960, 760));
        assert_eq!(blit_region(shown), Some(([20, 20], [980, 780], (960, 760))));
    }

    #[test]
    fn the_extent_stays_within_the_surface_limits() {
        assert_eq!(swapchain_extent((800, 600), (1, 1), (4096, 4096)), (800, 600));
        assert_eq!(swapchain_extent((8000, 600), (1, 1), (4096, 4096)), (4096, 600));
        assert_eq!(swapchain_extent((0, 0), (1, 1), (0, 0)), (1, 1));
        assert_eq!(swapchain_extent((5000, 5000), (1, 1), (0, 0)), (5000, 5000));
    }

    #[test]
    fn one_image_more_than_the_minimum() {
        assert_eq!(swapchain_image_count(3, 0), 4);
        assert_eq!(swapchain_image_count(3, 3), 3);
        assert_eq!(swapchain_image_count(0, 0), 2);
    }

    #[test]
    fn host_time_averages_per_frame() {
        let mut t = HostTime::default();
        assert_eq!(t.avg(), 0);
        t.add(100);
        t.add(300);
        assert_eq!((t.frames, t.avg(), t.max_us), (2, 200, 300));
    }
}
