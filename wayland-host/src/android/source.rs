//! Where a committed buffer's pixels are read from: a wl_shm pool, or a linear single-plane
//! dmabuf mapped read-only. Both give the copy the same description (size, stride, offset,
//! format), so the paced copy path, crop and size cap are shared.
//!
//! A dmabuf is mapped once per wl_buffer (whole fd, offset 0, so the plane offset is honoured by
//! the copy's own bounds check) and unmapped when the client destroys the buffer. Each read is
//! bracketed with DMA_BUF_IOCTL_SYNC (start/end, read); a driver without it is logged once and
//! read anyway (KGSL attaches no implicit fence, and the client's release follows our tick).

use std::collections::HashMap;
use std::os::fd::AsRawFd;
use std::sync::atomic::{AtomicBool, Ordering};

use smithay::backend::allocator::dmabuf::{Dmabuf, DmabufSyncFlags};
use smithay::backend::allocator::{Buffer, Fourcc, Modifier};
use smithay::reexports::wayland_server::backend::ObjectId;
use smithay::reexports::wayland_server::protocol::wl_buffer::WlBuffer;
use smithay::reexports::wayland_server::protocol::wl_shm;
use smithay::reexports::wayland_server::Resource;
use smithay::wayland::dmabuf::get_dmabuf;
use smithay::wayland::shm::with_buffer_contents;

use crate::pixels::{self, PixelFormat};

use super::log::lwarn;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Kind {
    Shm,
    Dmabuf,
}

impl Kind {
    pub fn name(self) -> &'static str {
        match self {
            Kind::Shm => "shm",
            Kind::Dmabuf => "dmabuf",
        }
    }
}

/// One buffer as the copy sees it. `offset` and `stride` are bytes into the readable range.
#[derive(Clone, Copy, Debug)]
pub struct Desc {
    pub kind: Kind,
    pub width: i32,
    pub height: i32,
    pub stride: i32,
    pub offset: i64,
    /// DRM fourcc (wl_shm's ARGB/XRGB codes 0 and 1 are given as their DRM codes).
    pub fourcc: u32,
    pub modifier: u64,
    pub format: PixelFormat,
}

/// `AR24` and the like, for the log.
pub fn fourcc_name(fourcc: u32) -> String {
    fourcc.to_le_bytes().iter().map(|&b| if b.is_ascii_graphic() { b as char } else { '?' }).collect()
}

/// The dmabuf formats the host advertises, each with the linear modifier only: anything tiled or
/// compressed could not be read by a plain mapping.
pub fn advertised_formats() -> Vec<smithay::backend::allocator::Format> {
    [Fourcc::Argb8888, Fourcc::Xrgb8888, Fourcc::Abgr8888, Fourcc::Xbgr8888]
        .into_iter()
        .map(|code| smithay::backend::allocator::Format { code, modifier: Modifier::Linear })
        .collect()
}

/// Whether an imported dmabuf is one the copy can read; the reason when not.
pub fn check_import(dmabuf: &Dmabuf) -> Result<(), &'static str> {
    let format = dmabuf.format();
    if pixels::drm_format(format.code as u32).is_none() {
        return Err("format");
    }
    if format.modifier != Modifier::Linear {
        return Err("modifier");
    }
    if dmabuf.num_planes() != 1 {
        return Err("planes");
    }
    let size = dmabuf.size();
    let stride = dmabuf.strides().next().unwrap_or(0) as i64;
    if size.w <= 0 || size.h <= 0 || stride < size.w as i64 * 4 {
        return Err("size or stride");
    }
    Ok(())
}

/// A read-only mapping of a whole dmabuf fd, unmapped on drop.
struct Mapping {
    ptr: *mut libc::c_void,
    len: usize,
}

impl Mapping {
    fn new(dmabuf: &Dmabuf) -> Result<Mapping, String> {
        let fd = dmabuf.handles().next().ok_or("no plane")?.as_raw_fd();
        // SAFETY: lseek on a dmabuf fd we hold; SEEK_END gives the buffer's size.
        let end = unsafe { libc::lseek(fd, 0, libc::SEEK_END) };
        // SAFETY: as above; put the offset back for anyone else reading the fd.
        unsafe { libc::lseek(fd, 0, libc::SEEK_SET) };
        let len = usize::try_from(end).ok().filter(|l| *l > 0).ok_or_else(|| format!("size {end}"))?;
        // SAFETY: a fresh shared read-only mapping of `len` bytes of an fd we hold; unmapped in Drop.
        let ptr = unsafe { libc::mmap(std::ptr::null_mut(), len, libc::PROT_READ, libc::MAP_SHARED, fd, 0) };
        if ptr == libc::MAP_FAILED {
            return Err(std::io::Error::last_os_error().to_string());
        }
        Ok(Mapping { ptr, len })
    }
}

impl Drop for Mapping {
    fn drop(&mut self) {
        // SAFETY: ptr and len are exactly what mmap returned.
        unsafe { libc::munmap(self.ptr, self.len) };
    }
}

/// Mappings per dmabuf wl_buffer. `None` marks a buffer whose mapping failed: it is dropped
/// (logged once) and not tried again.
#[derive(Default)]
pub struct DmabufMaps {
    maps: HashMap<ObjectId, Option<Mapping>>,
}

impl DmabufMaps {
    /// The client destroyed `buffer`.
    pub fn forget(&mut self, buffer: &WlBuffer) {
        self.maps.remove(&buffer.id());
    }

    pub fn clear(&mut self) {
        self.maps.clear();
    }
}

static SYNC_FAILED_LOGGED: AtomicBool = AtomicBool::new(false);

fn sync(dmabuf: &Dmabuf, flags: DmabufSyncFlags) {
    if let Err(e) = dmabuf.sync_plane(0, flags | DmabufSyncFlags::READ) {
        if !SYNC_FAILED_LOGGED.swap(true, Ordering::Relaxed) {
            lwarn!("DMA_BUF_IOCTL_SYNC failed ({e}); dmabufs are read without it");
        }
    }
}

/// Calls `f` with the buffer's readable bytes (pointer and length) and its description. Ok(None)
/// when the buffer is of a format the copy does not take, or its dmabuf could not be mapped (the
/// window keeps its previous frame); Err for a buffer that is neither shm nor dmabuf.
pub fn with_pixels<R>(
    buffer: &WlBuffer,
    maps: &mut DmabufMaps,
    f: impl FnOnce(*const u8, usize, &Desc) -> Option<R>,
) -> Result<Option<R>, String> {
    if let Ok(dmabuf) = get_dmabuf(buffer) {
        let Some(format) = pixels::drm_format(dmabuf.format().code as u32) else { return Ok(None) };
        let (Some(offset), Some(stride)) = (dmabuf.offsets().next(), dmabuf.strides().next()) else {
            return Ok(None);
        };
        let size = dmabuf.size();
        let desc = Desc {
            kind: Kind::Dmabuf,
            width: size.w,
            height: size.h,
            stride: i32::try_from(stride).unwrap_or(-1),
            offset: offset as i64,
            fourcc: dmabuf.format().code as u32,
            modifier: u64::from(dmabuf.format().modifier),
            format,
        };
        let id = buffer.id();
        let mapping = maps.maps.entry(id).or_insert_with(|| match Mapping::new(dmabuf) {
            Ok(m) => Some(m),
            Err(e) => {
                lwarn!("dmabuf {}x{}: cannot map ({e}); this buffer is not shown", size.w, size.h);
                None
            }
        });
        let Some(mapping) = mapping.as_ref() else { return Ok(None) };
        sync(dmabuf, DmabufSyncFlags::START);
        let result = f(mapping.ptr as *const u8, mapping.len, &desc);
        sync(dmabuf, DmabufSyncFlags::END);
        return Ok(result);
    }
    with_buffer_contents(buffer, |ptr, len, data| {
        let (format, fourcc) = match data.format {
            wl_shm::Format::Argb8888 => (pixels::ARGB, pixels::DRM_FORMAT_ARGB8888),
            wl_shm::Format::Xrgb8888 => (pixels::XRGB, pixels::DRM_FORMAT_XRGB8888),
            wl_shm::Format::Abgr8888 => (pixels::ABGR, pixels::DRM_FORMAT_ABGR8888),
            wl_shm::Format::Xbgr8888 => (pixels::XBGR, pixels::DRM_FORMAT_XBGR8888),
            _ => return None,
        };
        let desc = Desc {
            kind: Kind::Shm,
            width: data.width,
            height: data.height,
            stride: data.stride,
            offset: data.offset as i64,
            fourcc,
            modifier: 0,
            format,
        };
        f(ptr, len, &desc)
    })
    .map_err(|e| format!("{e:?}"))
}
