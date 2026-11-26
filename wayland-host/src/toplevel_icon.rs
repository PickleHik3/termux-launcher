//! The icon an app sets on its window at run time (`xdg_toplevel_icon_v1`).
//!
//! The protocol offers an icon as square wl_shm buffers at one or more sizes, or as a theme name.
//! The launcher draws it on a chip and on a page mark at 16 to 32 dp, so the host hands over one
//! square: the buffer nearest `TARGET_PX` (the smallest that is not smaller, else the largest),
//! cut down to `MAX_EDGE_PX` when it is bigger (the same cap as `LinuxAppIcons.MAX_EDGE_PX` on the
//! Java side), as straight-alpha ARGB ints. A name goes across as the name, for Java to resolve
//! the way it resolves a desktop entry's icon. Everything here is pure; `android/host.rs` reads
//! the buffers and calls it.

/// The edge the launcher draws an icon at, in px: 32 dp at the density of a phone.
pub const TARGET_PX: i32 = 96;
/// The most the host hands over on a side.
pub const MAX_EDGE_PX: i32 = 192;
/// A buffer wider than this is not read at all: it would cost megabytes to show 32 dp.
pub const MAX_SOURCE_EDGE_PX: i32 = 1024;

/// The icon as the launcher takes it: a theme name, pixels, or both (pixels win there).
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Icon {
    /// The theme icon name; empty for none.
    pub name: String,
    /// Edge of `argb` in px; 0 when the app gave no usable buffer.
    pub edge: i32,
    /// Straight (not premultiplied) 0xAARRGGBB, `edge * edge` of them.
    pub argb: Vec<i32>,
}

impl Icon {
    /// An icon with neither a name nor pixels is no icon.
    pub fn is_empty(&self) -> bool {
        self.name.is_empty() && self.edge == 0
    }
}

/// Which of the offered buffers (their edges in px) to read; None when none is usable.
pub fn choose(edges: &[i32]) -> Option<usize> {
    let mut best: Option<usize> = None;
    for (i, &e) in edges.iter().enumerate() {
        if !(1..=MAX_SOURCE_EDGE_PX).contains(&e) {
            continue;
        }
        best = match best {
            Some(b) if !nearer(e, edges[b]) => Some(b),
            _ => Some(i),
        };
    }
    best
}

/// Whether edge `e` is a better pick than `than`.
fn nearer(e: i32, than: i32) -> bool {
    match (e >= TARGET_PX, than >= TARGET_PX) {
        (true, true) => e < than,
        (true, false) => true,
        (false, true) => false,
        (false, false) => e > than,
    }
}

/// The edge a buffer of `edge` px is handed over at.
pub fn out_edge(edge: i32) -> i32 {
    edge.min(MAX_EDGE_PX)
}

/// Premultiplied RGBA bytes of a square (`edge` x `edge`) as straight-alpha ARGB ints of
/// `out_edge(edge)` on a side: averaged down when bigger than `MAX_EDGE_PX`, then un-premultiplied
/// (Android's `Bitmap.createBitmap(int[]...)` takes straight colours).
pub fn to_argb(rgba: &[u8], edge: i32) -> Vec<i32> {
    let edge = edge.max(0) as usize;
    let out = out_edge(edge as i32).max(0) as usize;
    if edge == 0 || rgba.len() < edge * edge * 4 {
        return Vec::new();
    }
    let mut argb = Vec::with_capacity(out * out);
    for y in 0..out {
        let top = y * edge / out;
        let bottom = ((y + 1) * edge / out).max(top + 1);
        for x in 0..out {
            let left = x * edge / out;
            let right = ((x + 1) * edge / out).max(left + 1);
            let mut sum = [0u32; 4];
            for sy in top..bottom {
                for sx in left..right {
                    let p = (sy * edge + sx) * 4;
                    for c in 0..4 {
                        sum[c] += rgba[p + c] as u32;
                    }
                }
            }
            let n = ((bottom - top) * (right - left)) as u32;
            let px = [sum[0] / n, sum[1] / n, sum[2] / n, sum[3] / n];
            argb.push(straight(px));
        }
    }
    argb
}

/// One premultiplied pixel (R, G, B, A) as straight 0xAARRGGBB.
fn straight(px: [u32; 4]) -> i32 {
    let a = px[3].min(255);
    if a == 0 {
        return 0;
    }
    let un = |c: u32| ((c.min(a) * 255 + a / 2) / a).min(255);
    ((a << 24) | (un(px[0]) << 16) | (un(px[1]) << 8) | un(px[2])) as i32
}

/// One window's icon and whether the launcher still has to hear it.
#[derive(Default)]
pub struct WindowIcon {
    icon: Option<Icon>,
    unsent: bool,
}

impl WindowIcon {
    /// The app set an icon (None: it set none, or took its icon away: the default again). An icon
    /// with nothing in it counts as none. Clearing a window that has none tells nobody.
    pub fn set(&mut self, icon: Option<Icon>) {
        let icon = icon.filter(|i| !i.is_empty());
        if icon != self.icon {
            self.unsent = true;
        }
        self.icon = icon;
    }

    /// The window is (re)announced: the launcher has heard nothing of it yet.
    pub fn announced(&mut self) {
        self.unsent = self.icon.is_some();
    }

    /// What the launcher has yet to hear: None when nothing changed, Some(None) for "back to the
    /// default", Some(Some(icon)) for a new icon. Asking marks it heard.
    pub fn pending(&mut self) -> Option<Option<&Icon>> {
        if !std::mem::take(&mut self.unsent) {
            return None;
        }
        Some(self.icon.as_ref())
    }

    pub fn current(&self) -> Option<&Icon> {
        self.icon.as_ref()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn icon(name: &str, edge: i32) -> Icon {
        Icon { name: name.into(), edge, argb: vec![0; (edge * edge) as usize] }
    }

    #[test]
    fn the_smallest_buffer_that_is_big_enough_wins() {
        assert_eq!(choose(&[32, 128, 256, 64]), Some(1));
        assert_eq!(choose(&[256, 128]), Some(1));
        assert_eq!(choose(&[96, 128]), Some(0));
    }

    #[test]
    fn when_all_are_small_the_largest_wins() {
        assert_eq!(choose(&[16, 48, 32]), Some(1));
    }

    #[test]
    fn a_buffer_nobody_can_afford_is_never_chosen() {
        assert_eq!(choose(&[]), None);
        assert_eq!(choose(&[0, -4, MAX_SOURCE_EDGE_PX + 1]), None);
        assert_eq!(choose(&[4096, 48]), Some(1));
    }

    #[test]
    fn a_big_choice_is_handed_over_at_the_cap() {
        assert_eq!(out_edge(512), MAX_EDGE_PX);
        assert_eq!(out_edge(128), 128);
    }

    #[test]
    fn pixels_come_out_straight() {
        // One premultiplied pixel: half-transparent red (R 128, G 0, B 0, A 128).
        let argb = to_argb(&[128, 0, 0, 128], 1);
        assert_eq!(argb, vec![0x80FF0000u32 as i32]);
        // A fully transparent pixel is all zero whatever its colour bytes say.
        assert_eq!(to_argb(&[9, 9, 9, 0], 1), vec![0]);
        // Opaque stays as it is.
        assert_eq!(to_argb(&[1, 2, 3, 255], 1), vec![0xFF010203u32 as i32]);
    }

    #[test]
    fn a_bigger_square_is_averaged_down_to_the_cap() {
        let edge = 384;
        let rgba = vec![255u8; (edge * edge * 4) as usize];
        let argb = to_argb(&rgba, edge);
        assert_eq!(argb.len(), (MAX_EDGE_PX * MAX_EDGE_PX) as usize);
        assert!(argb.iter().all(|&p| p == 0xFFFFFFFFu32 as i32));
    }

    #[test]
    fn a_short_buffer_gives_nothing() {
        assert!(to_argb(&[0; 8], 2).is_empty());
        assert!(to_argb(&[], 0).is_empty());
    }

    #[test]
    fn an_icon_is_heard_once() {
        let mut w = WindowIcon::default();
        assert!(w.pending().is_none());
        w.set(Some(icon("", 32)));
        assert!(matches!(w.pending(), Some(Some(i)) if i.edge == 32));
        assert!(w.pending().is_none());
    }

    #[test]
    fn taking_the_icon_away_is_heard_and_clearing_nothing_is_not() {
        let mut w = WindowIcon::default();
        w.set(None);
        assert!(w.pending().is_none());
        w.set(Some(icon("app", 0)));
        assert!(w.pending().is_some());
        w.set(None);
        assert!(matches!(w.pending(), Some(None)));
        assert!(w.current().is_none());
    }

    #[test]
    fn the_same_icon_again_is_not_news_and_an_empty_one_is_none() {
        let mut w = WindowIcon::default();
        w.set(Some(icon("app", 0)));
        w.pending();
        w.set(Some(icon("app", 0)));
        assert!(w.pending().is_none());
        w.set(Some(icon("", 0)));
        assert!(matches!(w.pending(), Some(None)));
    }

    #[test]
    fn a_window_announced_again_tells_its_icon_again() {
        let mut w = WindowIcon::default();
        w.set(Some(icon("app", 0)));
        w.pending();
        w.announced();
        assert!(matches!(w.pending(), Some(Some(_))));
        let mut none = WindowIcon::default();
        none.announced();
        assert!(none.pending().is_none());
    }

    #[test]
    fn an_icon_set_before_the_window_is_announced_waits_for_it() {
        let mut w = WindowIcon::default();
        w.set(Some(icon("app", 0)));
        w.announced();
        assert!(matches!(w.pending(), Some(Some(i)) if i.name == "app"));
    }
}
