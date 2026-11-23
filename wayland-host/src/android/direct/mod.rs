//! Experiment (exp/wayland-turnip): the direct presenter. When asked (ILinuxWindowsDebug.setDirect
//! or the test screen's Direct toggle), the host loads the Turnip driver bundled with
//! -PwaylandTurnip=true through libadrenotools, and new toplevels show their client's dmabuf with
//! a GPU blit into a Vulkan swapchain on their Surface instead of a CPU copy. Anything that fails
//! is logged and the window, or the whole host, stays on (or goes back to) the copy path.
//!
//! Without the `turnip` feature (every ABI but arm64, and any build without the property) only
//! the bookkeeping below exists: a request is logged as unavailable and every window copies.

#[cfg(all(feature = "turnip", target_arch = "aarch64"))]
mod shaders;
#[cfg(all(feature = "turnip", target_arch = "aarch64"))]
mod vk;

use smithay::backend::allocator::dmabuf::Dmabuf;
use smithay::backend::allocator::Format;
use smithay::reexports::wayland_server::protocol::wl_buffer::WlBuffer;

use crate::direct::{HostTime, Mode};
use crate::geometry::Rect;

use super::log::{linfo, lwarn};
use super::source;
use super::window::NativeWindow;

/// Where the host finds the hooks and the driver (from the service, see TurnipDriver.java).
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DirectRequest {
    /// The app's nativeLibraryDir, holding the four adrenotools hooks.
    pub hook_dir: String,
    /// App-private directory of the driver, ending in '/'.
    pub driver_dir: String,
    pub driver_name: String,
}

/// Why a direct present did not happen.
#[derive(Debug)]
pub enum PresentError {
    /// The window goes over to the copy path for good.
    FallBack(String),
    /// The Surface's consumer is gone: dropped like a failed copy post.
    #[cfg_attr(not(all(feature = "turnip", target_arch = "aarch64")), allow(dead_code))]
    Lost(String),
}

/// A toplevel's direct state. Declared before the toplevel's NativeWindow so the swapchain and
/// Vulkan surface are destroyed before the window reference is released.
pub struct DirectWindow {
    pub mode: Mode,
    /// Host time per frame, for the once-a-second line (copy windows use it too).
    pub host_time: HostTime,
    /// The (fourcc, modifier) last shown directly, so a change is logged once.
    shown_format: Option<(u32, u64)>,
    #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
    presenter: Option<vk::WindowPresenter>,
}

impl DirectWindow {
    pub fn new(mode: Mode) -> DirectWindow {
        DirectWindow {
            mode,
            host_time: HostTime::default(),
            shown_format: None,
            #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
            presenter: None,
        }
    }

    /// The window's Surface is going or being replaced: the swapchain on it goes first.
    pub fn detach(&mut self) {
        #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
        {
            self.presenter = None;
        }
    }

    /// Gives up direct for this window; it copies from now on.
    pub fn fall_back(&mut self) {
        self.detach();
        self.mode = Mode::Copy;
    }

    /// The modifier of the last frame shown directly.
    pub fn shown_modifier(&self) -> Option<u64> {
        self.shown_format.map(|(_, m)| m)
    }
}

/// The host's direct state: the request, and once loaded, the driver context and the imported
/// buffers.
#[derive(Default)]
pub struct Direct {
    requested: Option<DirectRequest>,
    #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
    ctx: Option<std::rc::Rc<vk::Ctx>>,
    /// The load failed; not tried again in this host.
    #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
    load_failed: bool,
    #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
    imports: vk::Imports,
}

impl Direct {
    fn loaded(&self) -> bool {
        #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
        {
            self.ctx.is_some()
        }
        #[cfg(not(all(feature = "turnip", target_arch = "aarch64")))]
        {
            false
        }
    }

    /// Direct is asked for and the driver is up: new windows use it, and the dmabuf global
    /// advertises the driver's modifiers.
    pub fn active(&self) -> bool {
        self.requested.is_some() && self.loaded()
    }

    /// Takes a request (None: off). The first request loads the driver. True when `active`
    /// changed, so the dmabuf formats must be advertised again.
    pub fn set(&mut self, request: Option<DirectRequest>) -> bool {
        let was = self.active();
        if self.requested == request {
            return false;
        }
        self.requested = request;
        if self.requested.is_none() {
            linfo!("direct: off; new windows copy");
            return was != self.active();
        }
        #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
        if let (None, false, Some(req)) = (&self.ctx, self.load_failed, &self.requested) {
            match vk::load(req) {
                Ok(ctx) => self.ctx = Some(std::rc::Rc::new(ctx)),
                Err(e) => {
                    self.load_failed = true;
                    lwarn!("direct load: failed: {e}; every window keeps the copy path");
                }
            }
        } else if self.load_failed {
            lwarn!("direct load: failed earlier in this host; every window keeps the copy path (restart the host to retry)");
        }
        #[cfg(not(all(feature = "turnip", target_arch = "aarch64")))]
        lwarn!("direct load: not in this build (needs -PwaylandTurnip=true, arm64-v8a); every window keeps the copy path");
        if self.active() {
            linfo!("direct: on; new windows use direct");
        }
        was != self.active()
    }

    /// The mode a new toplevel gets.
    pub fn new_window_mode(&self) -> Mode {
        crate::direct::mode_for_new_window(self.requested.is_some(), self.loaded())
    }

    /// The (format, modifier) pairs the dmabuf global advertises: linear only unless active.
    pub fn dmabuf_formats(&self) -> Vec<Format> {
        #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
        if let (true, Some(ctx)) = (self.active(), &self.ctx) {
            use smithay::backend::allocator::Modifier;
            let mut out = Vec::new();
            for (code, caps) in &ctx.formats {
                for m in crate::direct::advertised_modifiers(caps) {
                    out.push(Format { code: *code, modifier: Modifier::from(m) });
                }
            }
            return out;
        }
        source::advertised_formats()
    }

    /// Whether a dmabuf the copy path refuses (a tiled or compressed modifier) can still be taken
    /// because direct can import it.
    pub fn can_import(&self, dmabuf: &Dmabuf) -> bool {
        #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
        {
            self.ctx.as_ref().is_some_and(|c| c.can_import(dmabuf))
        }
        #[cfg(not(all(feature = "turnip", target_arch = "aarch64")))]
        {
            let _ = dmabuf;
            false
        }
    }

    /// The client destroyed `buffer`: its imported image goes.
    pub fn forget(&mut self, buffer: &WlBuffer) {
        #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
        if let Some(ctx) = &self.ctx {
            use smithay::reexports::wayland_server::Resource;
            // SAFETY: every present waits for its fence, so no GPU work uses the image.
            unsafe { self.imports.forget(ctx.device(), &buffer.id()) };
        }
        #[cfg(not(all(feature = "turnip", target_arch = "aarch64")))]
        let _ = buffer;
    }

    /// Shows `buffer`'s `shown` rectangle (buffer pixels, cut like a copy) in `win`'s Surface.
    pub fn present(&mut self, win: &mut DirectWindow, native: &NativeWindow, buffer: &WlBuffer, shown: Rect, id: i64) -> Result<(), PresentError> {
        #[cfg(all(feature = "turnip", target_arch = "aarch64"))]
        {
            use crate::direct::{blit_region, modifier_usable, route, vk_format_for, Route};
            use smithay::backend::allocator::Buffer;
            use smithay::reexports::wayland_server::Resource;
            let Some(ctx) = self.ctx.clone() else { return Err(PresentError::FallBack("the driver is not loaded".into())) };
            let dmabuf = smithay::wayland::dmabuf::get_dmabuf(buffer).ok();
            let (fourcc, modifier) = dmabuf.map(|d| (d.format().code as u32, u64::from(d.format().modifier))).unwrap_or((0, 0));
            let usable = ctx.caps_for(fourcc).is_some_and(|caps| modifier_usable(caps, modifier));
            if let Route::FallBack(why) = route(dmabuf.is_some(), vk_format_for(fourcc).is_some(), usable) {
                return Err(PresentError::FallBack(format!("{why} (format {} modifier 0x{modifier:x})", source::fourcc_name(fourcc))));
            }
            let Some(dmabuf) = dmabuf else { return Err(PresentError::FallBack("not a dmabuf".into())) };
            let Some((from, to, size)) = blit_region(shown) else {
                return Err(PresentError::FallBack(format!("nothing to show ({shown:?})")));
            };
            if win.shown_format != Some((fourcc, modifier)) {
                win.shown_format = Some((fourcc, modifier));
                linfo!(
                    "toplevel {id}: mode=direct format={} modifier=0x{modifier:x} buffer={}x{}",
                    source::fourcc_name(fourcc),
                    dmabuf.size().w,
                    dmabuf.size().h
                );
            }
            vk::wait_implicit_fence(dmabuf);
            let imported = self.imports.get_or_import(&ctx, buffer.id(), dmabuf).map_err(PresentError::FallBack)?;
            if win.presenter.is_none() {
                // SAFETY: the NativeWindow outlives the presenter (DirectWindow is dropped or
                // detached before the toplevel's window), and a direct window never locks it.
                let p = unsafe { vk::WindowPresenter::new(&ctx, native.as_ptr().cast(), size, id) };
                win.presenter = Some(p.map_err(PresentError::FallBack)?);
            }
            let Some(presenter) = win.presenter.as_mut() else { return Err(PresentError::FallBack("no presenter".into())) };
            // SAFETY: the image belongs to this device and nothing else is using it.
            unsafe { presenter.present(imported, from, to, size, id) }
        }
        #[cfg(not(all(feature = "turnip", target_arch = "aarch64")))]
        {
            let _ = (win, native, buffer, shown, id);
            Err(PresentError::FallBack("not in this build".into()))
        }
    }
}

#[cfg(all(feature = "turnip", target_arch = "aarch64"))]
impl Drop for Direct {
    fn drop(&mut self) {
        if let Some(ctx) = &self.ctx {
            // SAFETY: every present waited for its fence; nothing uses the imported images.
            unsafe { self.imports.clear(ctx.device()) };
        }
    }
}
