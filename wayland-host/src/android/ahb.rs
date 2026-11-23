//! Feasibility probe (debug only): can a client render into a buffer Android allocated, and can
//! the launcher draw that buffer without a copy? Three experiments, each reached only through
//! `ILinuxWindowsDebug`, logging one fact per line under `TLWaylandAHB`:
//!
//! - E1 `fill`: writes a test pattern into a `HardwareBuffer` the service allocated (the launcher
//!   wraps and draws it);
//! - E2 `layout_probe`: allocates AHardwareBuffers with several usages, pulls their fds out with
//!   `AHardwareBuffer_sendHandleToUnixSocket` and checks whether an mmap of the fd shows a linear
//!   RGBA image, and at what stride;
//! - E3 `serve_client`: hands one such fd to `tlwayland-ahbclient` over a Unix socket, waits for it
//!   to clear the image on its GPU, and reads the colour back.
//!
//! Everything here is API 26 NDK except `AHardwareBuffer_toHardwareBuffer` (API 27, `dlsym`).

use std::ffi::{c_void, CStr, CString};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd, RawFd};
use std::os::raw::c_int;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Instant;

use super::log::{write_tagged, INFO, WARN};

const TAG: &[u8] = b"TLWaylandAHB\0";

macro_rules! alog {
    ($($arg:tt)*) => { write_tagged(TAG, INFO, &format!($($arg)*)) };
}
macro_rules! awarn {
    ($($arg:tt)*) => { write_tagged(TAG, WARN, &format!($($arg)*)) };
}

#[repr(C)]
pub struct AHardwareBuffer {
    _private: [u8; 0],
}

#[repr(C)]
#[derive(Default, Clone, Copy)]
struct Desc {
    width: u32,
    height: u32,
    layers: u32,
    format: u32,
    usage: u64,
    /// In pixels; filled by describe.
    stride: u32,
    rfu0: u32,
    rfu1: u64,
}

#[link(name = "android")]
extern "C" {
    fn AHardwareBuffer_allocate(desc: *const Desc, out: *mut *mut AHardwareBuffer) -> c_int;
    fn AHardwareBuffer_release(buffer: *mut AHardwareBuffer);
    fn AHardwareBuffer_describe(buffer: *const AHardwareBuffer, out: *mut Desc);
    fn AHardwareBuffer_lock(buffer: *mut AHardwareBuffer, usage: u64, fence: i32, rect: *const c_void, out: *mut *mut c_void) -> c_int;
    fn AHardwareBuffer_unlock(buffer: *mut AHardwareBuffer, fence: *mut i32) -> c_int;
    fn AHardwareBuffer_sendHandleToUnixSocket(buffer: *const AHardwareBuffer, socket: c_int) -> c_int;
    fn AHardwareBuffer_fromHardwareBuffer(env: *mut jni::sys::JNIEnv, buffer: jni::sys::jobject) -> *mut AHardwareBuffer;
}

const FORMAT_R8G8B8A8_UNORM: u32 = 1;
const USAGE_CPU_READ_OFTEN: u64 = 3;
const USAGE_CPU_READ_MASK: u64 = 0xf;
const USAGE_CPU_WRITE_OFTEN: u64 = 3 << 4;
const USAGE_CPU_WRITE_MASK: u64 = 0xf << 4;
const USAGE_GPU_SAMPLED_IMAGE: u64 = 1 << 8;
const USAGE_GPU_FRAMEBUFFER: u64 = 1 << 9;
const USAGE_COMPOSER_OVERLAY: u64 = 1 << 11;

/// `DRM_FORMAT_ABGR8888` ("AB24"): bytes R, G, B, A in memory, which is AHB's R8G8B8A8.
const DRM_FORMAT_ABGR8888: u32 = u32::from_le_bytes(*b"AB24");
const DRM_FORMAT_MOD_LINEAR: u64 = 0;
/// `fourcc_mod_code(QCOM, 1)`: Adreno UBWC, which Turnip also knows.
const DRM_FORMAT_MOD_QCOM_COMPRESSED: u64 = (5 << 56) | 1;

/// `_IOW('b', 0, struct dma_buf_sync)`.
const DMA_BUF_IOCTL_SYNC: libc::c_ulong = 0x4008_6200;
const DMA_BUF_SYNC_READ: u64 = 1;
const DMA_BUF_SYNC_START: u64 = 0;
const DMA_BUF_SYNC_END: u64 = 4;

/// E2's and E3's buffer size: pong's screen below the status bar.
const PROBE_WIDTH: u32 = 1080;
const PROBE_HEIGHT: u32 = 2038;

struct Variant {
    name: &'static str,
    usage: u64,
}

/// The usages E2 tries, by index; E3 takes the same index.
const VARIANTS: [Variant; 5] = [
    Variant { name: "a cpu-often+sampled", usage: USAGE_CPU_READ_OFTEN | USAGE_CPU_WRITE_OFTEN | USAGE_GPU_SAMPLED_IMAGE },
    Variant { name: "b sampled+framebuffer", usage: USAGE_GPU_SAMPLED_IMAGE | USAGE_GPU_FRAMEBUFFER },
    Variant { name: "c sampled+framebuffer+overlay", usage: USAGE_GPU_SAMPLED_IMAGE | USAGE_GPU_FRAMEBUFFER | USAGE_COMPOSER_OVERLAY },
    Variant { name: "d cpu-often+sampled+overlay", usage: USAGE_CPU_READ_OFTEN | USAGE_CPU_WRITE_OFTEN | USAGE_GPU_SAMPLED_IMAGE | USAGE_COMPOSER_OVERLAY },
    Variant {
        name: "e cpu-often+sampled+framebuffer",
        usage: USAGE_CPU_READ_OFTEN | USAGE_CPU_WRITE_OFTEN | USAGE_GPU_SAMPLED_IMAGE | USAGE_GPU_FRAMEBUFFER,
    },
];

/// One reference on an AHardwareBuffer, released on drop.
struct Ahb(*mut AHardwareBuffer);

impl Ahb {
    fn allocate(width: u32, height: u32, usage: u64) -> Result<Ahb, c_int> {
        let desc = Desc { width, height, layers: 1, format: FORMAT_R8G8B8A8_UNORM, usage, ..Desc::default() };
        let mut out = std::ptr::null_mut();
        // SAFETY: a filled desc and an out pointer, as the NDK defines the call.
        let rc = unsafe { AHardwareBuffer_allocate(&desc, &mut out) };
        if rc != 0 || out.is_null() {
            Err(rc)
        } else {
            Ok(Ahb(out))
        }
    }

    fn describe(&self) -> Desc {
        let mut d = Desc::default();
        // SAFETY: self holds a live reference.
        unsafe { AHardwareBuffer_describe(self.0, &mut d) };
        d
    }
}

impl Drop for Ahb {
    fn drop(&mut self) {
        // SAFETY: releases the one reference this value owns.
        unsafe { AHardwareBuffer_release(self.0) }
    }
}

/// A CPU lock on a buffer, unlocked (and waited for) on drop.
struct Locked {
    buffer: *mut AHardwareBuffer,
    ptr: *mut u8,
}

impl Locked {
    /// # Safety
    /// `buffer` must stay alive while the lock is held.
    unsafe fn new(buffer: *mut AHardwareBuffer, usage: u64) -> Result<Locked, c_int> {
        let mut ptr = std::ptr::null_mut();
        let rc = AHardwareBuffer_lock(buffer, usage, -1, std::ptr::null(), &mut ptr);
        if rc != 0 || ptr.is_null() {
            Err(rc)
        } else {
            Ok(Locked { buffer, ptr: ptr as *mut u8 })
        }
    }
}

impl Drop for Locked {
    fn drop(&mut self) {
        // SAFETY: the buffer is locked by this value; a null fence makes unlock wait.
        let rc = unsafe { AHardwareBuffer_unlock(self.buffer, std::ptr::null_mut()) };
        if rc != 0 {
            awarn!("unlock rc={rc}");
        }
    }
}

fn errno() -> String {
    std::io::Error::last_os_error().to_string()
}

fn rgba(r: u8, g: u8, b: u8, a: u8) -> u32 {
    u32::from_le_bytes([r, g, b, a])
}

fn hex_rgba(v: u32) -> String {
    let b = v.to_le_bytes();
    format!("{:02x}{:02x}{:02x}{:02x}", b[0], b[1], b[2], b[3])
}

// ---------------------------------------------------------------- E1

/// E1's pattern, premultiplied RGBA: left half transparent, right half opaque orange, a 50 %
/// white band across the middle eighth, and an opaque cyan bar 32 px wide at `frame * 8`.
fn e1_pixel(x: u32, y: u32, w: u32, h: u32, frame: u32) -> u32 {
    let bar = (frame.wrapping_mul(8)) % w.max(1);
    if x >= bar && x < bar + 32 {
        return rgba(0, 200, 255, 255);
    }
    if y >= h * 7 / 16 && y < h * 9 / 16 {
        return rgba(128, 128, 128, 128);
    }
    if x < w / 2 {
        0
    } else {
        rgba(255, 140, 0, 255)
    }
}

/// Writes E1's pattern for `frame` into a HardwareBuffer handed from Java. False when it cannot.
pub fn fill(env: *mut jni::sys::JNIEnv, buffer: jni::sys::jobject, frame: i32) -> bool {
    // SAFETY: a live env and a HardwareBuffer reference; the result is borrowed (no acquire)
    // and the Java object outlives this call.
    let ahb = unsafe { AHardwareBuffer_fromHardwareBuffer(env, buffer) };
    if ahb.is_null() {
        awarn!("E1 fill: AHardwareBuffer_fromHardwareBuffer returned null");
        return false;
    }
    let mut d = Desc::default();
    // SAFETY: ahb is live (see above).
    unsafe { AHardwareBuffer_describe(ahb, &mut d) };
    if frame == 0 {
        alog!("E1 fill: buffer {}x{} stride={}px format={} usage=0x{:x}", d.width, d.height, d.stride, d.format, d.usage);
    }
    // SAFETY: ahb is live for the whole lock.
    let lock = match unsafe { Locked::new(ahb, USAGE_CPU_WRITE_OFTEN) } {
        Ok(l) => l,
        Err(rc) => {
            awarn!("E1 fill: lock failed rc={rc}");
            return false;
        }
    };
    let (w, h, stride) = (d.width, d.height, d.stride.max(d.width) as usize);
    // SAFETY: a locked RGBA_8888 buffer holds stride * height pixels at ptr.
    let px = unsafe { std::slice::from_raw_parts_mut(lock.ptr as *mut u32, stride * h as usize) };
    for y in 0..h {
        let row = &mut px[y as usize * stride..y as usize * stride + w as usize];
        for (x, p) in row.iter_mut().enumerate() {
            *p = e1_pixel(x as u32, y, w, h, frame as u32);
        }
    }
    drop(lock);
    true
}

// ---------------------------------------------------------------- E2

/// What arrived on the other end of `AHardwareBuffer_sendHandleToUnixSocket`.
struct Handle {
    fds: Vec<OwnedFd>,
}

/// Sends the buffer's handle over a socketpair and reads the raw message, logging it.
fn extract_handle(label: &str, buffer: &Ahb) -> Option<Handle> {
    let mut sv = [0 as c_int; 2];
    // SAFETY: plain socketpair into a two-int array.
    if unsafe { libc::socketpair(libc::AF_UNIX, libc::SOCK_SEQPACKET | libc::SOCK_CLOEXEC, 0, sv.as_mut_ptr()) } != 0 {
        awarn!("{label} socketpair failed: {}", errno());
        return None;
    }
    // SAFETY: both fds were just created and are owned here.
    let (tx, rx) = unsafe { (OwnedFd::from_raw_fd(sv[0]), OwnedFd::from_raw_fd(sv[1])) };
    // SAFETY: a live buffer and an open socket.
    let rc = unsafe { AHardwareBuffer_sendHandleToUnixSocket(buffer.0, tx.as_raw_fd()) };
    alog!("{label} sendHandleToUnixSocket rc={rc}");
    if rc != 0 {
        return None;
    }
    let mut data = [0u8; 4096];
    let mut cmsg = [0u8; 1024];
    let mut iov = libc::iovec { iov_base: data.as_mut_ptr() as *mut c_void, iov_len: data.len() };
    // SAFETY: zeroed msghdr is a valid empty header; the fields set point at live buffers.
    let mut msg: libc::msghdr = unsafe { std::mem::zeroed() };
    msg.msg_iov = &mut iov;
    msg.msg_iovlen = 1;
    msg.msg_control = cmsg.as_mut_ptr() as *mut c_void;
    msg.msg_controllen = cmsg.len() as _;
    // SAFETY: msg describes live buffers; MSG_DONTWAIT because the message is already queued.
    let n = unsafe { libc::recvmsg(rx.as_raw_fd(), &mut msg, libc::MSG_CMSG_CLOEXEC | libc::MSG_DONTWAIT) };
    if n < 0 {
        awarn!("{label} recvmsg failed: {}", errno());
        return None;
    }
    let n = n as usize;
    let mut fds = Vec::new();
    // SAFETY: walking the control buffer recvmsg filled, with libc's CMSG helpers.
    unsafe {
        let mut c = libc::CMSG_FIRSTHDR(&msg);
        while !c.is_null() {
            if (*c).cmsg_level == libc::SOL_SOCKET && (*c).cmsg_type == libc::SCM_RIGHTS {
                let payload = (*c).cmsg_len as usize - libc::CMSG_LEN(0) as usize;
                let p = libc::CMSG_DATA(c) as *const c_int;
                for k in 0..payload / 4 {
                    fds.push(OwnedFd::from_raw_fd(std::ptr::read_unaligned(p.add(k))));
                }
            }
            c = libc::CMSG_NXTHDR(&msg, c);
        }
    }
    let words: Vec<String> = data[..n].chunks_exact(4).map(|w| format!("{:08x}", u32::from_le_bytes([w[0], w[1], w[2], w[3]]))).collect();
    alog!(
        "{label} handle message: {n} bytes, {} fds, flags=0x{:x} (MSG_CTRUNC={})",
        fds.len(),
        msg.msg_flags,
        msg.msg_flags & libc::MSG_CTRUNC != 0
    );
    // The flattened GraphicBuffer: 'GB01', width, height, stride, format, layers, usage lo,
    // id hi, id lo, generation, numFds, numInts, usage hi, then the native handle's ints.
    // Not a public format: logged raw.
    alog!("{label} handle words: {}", words.join(" "));
    Some(Handle { fds })
}

/// A read-only mapping of an fd, unmapped on drop.
struct Mapping {
    ptr: *mut c_void,
    len: usize,
}

impl Mapping {
    fn words(&self) -> &[u32] {
        // SAFETY: a live PROT_READ mapping of len bytes, page-aligned.
        unsafe { std::slice::from_raw_parts(self.ptr as *const u32, self.len / 4) }
    }
}

impl Drop for Mapping {
    fn drop(&mut self) {
        // SAFETY: unmaps what mmap returned.
        unsafe { libc::munmap(self.ptr, self.len) };
    }
}

fn map_read(fd: RawFd, len: usize) -> Result<Mapping, String> {
    if len == 0 {
        return Err("zero length".into());
    }
    // SAFETY: a fresh read-only shared mapping; failure is checked.
    let p = unsafe { libc::mmap(std::ptr::null_mut(), len, libc::PROT_READ, libc::MAP_SHARED, fd, 0) };
    if p == libc::MAP_FAILED {
        Err(errno())
    } else {
        Ok(Mapping { ptr: p, len })
    }
}

fn dma_buf_sync(fd: RawFd, flags: u64) -> String {
    let arg: u64 = flags;
    // SAFETY: DMA_BUF_IOCTL_SYNC takes a pointer to one u64.
    let rc = unsafe { libc::ioctl(fd, DMA_BUF_IOCTL_SYNC as _, &arg) };
    if rc == 0 {
        "ok".into()
    } else {
        format!("failed ({})", errno())
    }
}

fn fd_size(fd: RawFd) -> i64 {
    // SAFETY: lseek on an open fd; the position is put back.
    unsafe {
        let end = libc::lseek(fd, 0, libc::SEEK_END);
        libc::lseek(fd, 0, libc::SEEK_SET);
        end as i64
    }
}

/// Logs what each fd is and returns a mapping of the first fd that maps at `expected` bytes or
/// more (the image, if any fd is).
fn fd_facts(label: &str, fds: &[OwnedFd], expected: usize) -> Option<(usize, Mapping)> {
    let mut first = None;
    for (k, fd) in fds.iter().enumerate() {
        let raw = fd.as_raw_fd();
        // SAFETY: zeroed stat is a valid out buffer for fstat.
        let mut st: libc::stat = unsafe { std::mem::zeroed() };
        // SAFETY: fstat on an open fd.
        let fst = unsafe { libc::fstat(raw, &mut st) };
        let link = std::fs::read_link(format!("/proc/self/fd/{raw}")).map(|p| p.display().to_string()).unwrap_or_else(|e| format!("? ({e})"));
        if fst == 0 {
            alog!(
                "{label} fd[{k}]={raw} fstat size={} mode=0{:o} ino={} dev={:x} rdev={:x} link={link}",
                st.st_size,
                st.st_mode,
                st.st_ino,
                st.st_dev,
                st.st_rdev
            );
        } else {
            alog!("{label} fd[{k}]={raw} fstat failed ({}) link={link}", errno());
        }
        let seek = fd_size(raw);
        alog!("{label} fd[{k}] lseek end={seek}");
        let expected_map = map_read(raw, expected);
        alog!("{label} fd[{k}] mmap expected {expected} bytes: {}", expected_map.as_ref().map(|_| "ok".to_string()).unwrap_or_else(|e| format!("failed ({e})")));
        let st_size = if fst == 0 { st.st_size.max(0) as usize } else { 0 };
        let seek_size = seek.max(0) as usize;
        for (what, size) in [("fstat", st_size), ("lseek", seek_size)] {
            if size > 0 && size != expected {
                let m = map_read(raw, size);
                alog!("{label} fd[{k}] mmap {what} size {size} bytes: {}", m.as_ref().map(|_| "ok".to_string()).unwrap_or_else(|e| format!("failed ({e})")));
            }
        }
        alog!(
            "{label} fd[{k}] DMA_BUF_IOCTL_SYNC start|read: {}; end|read: {}",
            dma_buf_sync(raw, DMA_BUF_SYNC_START | DMA_BUF_SYNC_READ),
            dma_buf_sync(raw, DMA_BUF_SYNC_END | DMA_BUF_SYNC_READ)
        );
        // Reading a mapping past the end of a smaller file is SIGBUS: keep only one the fd's
        // own size covers (dma-buf mmap refuses oversize maps anyway).
        if first.is_none() && seek_size.max(st_size) >= expected {
            if let Ok(m) = expected_map {
                first = Some((k, m));
            }
        }
    }
    first
}

/// E2's pattern word: row and column in the low 24 bits, so a word found in memory says where
/// it was written.
fn pattern_word(x: u32, y: u32) -> u32 {
    0xA500_0000 | ((y & 0xfff) << 12) | (x & 0xfff)
}

/// Fills a CPU-writable buffer with `pattern_word` at the lock's stride.
fn write_pattern(label: &str, buffer: &Ahb, d: &Desc) -> bool {
    // SAFETY: buffer outlives the lock.
    let lock = match unsafe { Locked::new(buffer.0, USAGE_CPU_WRITE_OFTEN) } {
        Ok(l) => l,
        Err(rc) => {
            alog!("{label} lock for CPU write failed rc={rc} (expected without CPU usage)");
            return false;
        }
    };
    let stride = d.stride.max(d.width) as usize;
    // SAFETY: a locked RGBA_8888 buffer holds stride * height pixels.
    let px = unsafe { std::slice::from_raw_parts_mut(lock.ptr as *mut u32, stride * d.height as usize) };
    for y in 0..d.height {
        for x in 0..d.width {
            px[y as usize * stride + x as usize] = pattern_word(x, y);
        }
    }
    drop(lock);
    alog!("{label} lock+write+unlock ok (pattern at describe stride {} px)", d.stride);
    true
}

/// Looks for the pattern through the fd's mapping and logs the layout it implies.
fn find_pattern(label: &str, fd: RawFd, map: &Mapping, d: &Desc) {
    let sync = dma_buf_sync(fd, DMA_BUF_SYNC_START | DMA_BUF_SYNC_READ);
    let words = map.words();
    let window = words.len().min(1 << 20);
    let first: Vec<String> = words.iter().take(8).map(|w| format!("{w:08x}")).collect();
    alog!("{label} mmap first words: {} (sync start: {sync})", first.join(" "));
    let origin = words[..window].iter().position(|w| *w == pattern_word(0, 0));
    let Some(origin) = origin else {
        let nonzero = words[..window.min(16384)].iter().filter(|w| **w != 0).count();
        alog!("{label} pattern NOT found in the first {} bytes; {nonzero} of the first 64 KiB words are nonzero -> not linear (or not this fd)", window * 4);
        dma_buf_sync(fd, DMA_BUF_SYNC_END | DMA_BUF_SYNC_READ);
        return;
    };
    let row1 = words[origin + 1..window].iter().position(|w| *w == pattern_word(0, 1)).map(|i| i + 1);
    match row1 {
        Some(r) => {
            let stride_bytes = r * 4;
            let check = |x: u32, y: u32| -> bool {
                let i = origin + y as usize * r + x as usize;
                words.get(i) == Some(&pattern_word(x, y))
            };
            let mid = check(500, 100);
            let last = check(d.width - 1, d.height - 1);
            let rows_ok = (0..d.height).all(|y| check(0, y) && check(d.width - 1, y));
            alog!(
                "{label} pattern found: offset={} bytes stride={stride_bytes} bytes ({} px; describe says {} px) (500,100)={} last pixel={} every row's ends={} -> linear={}",
                origin * 4,
                r,
                d.stride,
                mid,
                last,
                rows_ok,
                mid && last && rows_ok
            );
        }
        None => alog!("{label} pattern origin at offset {} bytes but row 1 not found -> not a plain linear layout", origin * 4),
    }
    dma_buf_sync(fd, DMA_BUF_SYNC_END | DMA_BUF_SYNC_READ);
}

fn probe_variant(index: usize) {
    let v = &VARIANTS[index];
    let label = format!("E2[{}]", v.name);
    let started = Instant::now();
    let buffer = match Ahb::allocate(PROBE_WIDTH, PROBE_HEIGHT, v.usage) {
        Ok(b) => b,
        Err(rc) => {
            alog!("{label} allocate {PROBE_WIDTH}x{PROBE_HEIGHT} usage=0x{:x} failed rc={rc}", v.usage);
            return;
        }
    };
    let d = buffer.describe();
    alog!(
        "{label} allocated usage=0x{:x}: describe {}x{} layers={} format={} stride={} px usage=0x{:x}",
        v.usage,
        d.width,
        d.height,
        d.layers,
        d.format,
        d.stride,
        d.usage
    );
    let Some(handle) = extract_handle(&label, &buffer) else { return };
    let expected = d.stride.max(d.width) as usize * 4 * d.height as usize;
    let mapped = fd_facts(&label, &handle.fds, expected);
    let wrote = write_pattern(&label, &buffer, &d);
    match mapped {
        Some((k, map)) => {
            if wrote {
                find_pattern(&format!("{label} fd[{k}]"), handle.fds[k].as_raw_fd(), &map, &d);
            } else {
                let first: Vec<String> = map.words().iter().take(8).map(|w| format!("{w:08x}")).collect();
                alog!("{label} fd[{k}] no CPU pattern; mmap first words: {}", first.join(" "));
            }
        }
        None => alog!("{label} no fd maps at the expected size -> a client cannot be given (fd, stride) for this usage"),
    }
    alog!("{label} done in {} ms", started.elapsed().as_millis());
}

static E2_RUNNING: AtomicBool = AtomicBool::new(false);

/// E2: probes every usage on a thread of its own, one run at a time.
pub fn spawn_layout_probe() {
    if E2_RUNNING.swap(true, Ordering::SeqCst) {
        alog!("E2 already running");
        return;
    }
    let started = std::thread::Builder::new().name("tl-ahb-e2".into()).spawn(|| {
        alog!("E2 start: {} variants at {PROBE_WIDTH}x{PROBE_HEIGHT} RGBA_8888, pid {}", VARIANTS.len(), std::process::id());
        for i in 0..VARIANTS.len() {
            if std::panic::catch_unwind(|| probe_variant(i)).is_err() {
                awarn!("E2[{}] panicked", VARIANTS[i].name);
            }
        }
        alog!("E2 end");
        E2_RUNNING.store(false, Ordering::SeqCst);
    });
    if started.is_err() {
        E2_RUNNING.store(false, Ordering::SeqCst);
    }
}

// ---------------------------------------------------------------- E3

/// The header E3 sends with the fd (little-endian, 48 bytes); `tlwayland-ahbclient` reads the
/// same layout.
fn e3_header(width: u32, height: u32, stride_bytes: u32, modifier: u64, size: u64) -> [u8; 48] {
    let mut h = [0u8; 48];
    h[0..8].copy_from_slice(b"TLAHB001");
    h[8..12].copy_from_slice(&width.to_le_bytes());
    h[12..16].copy_from_slice(&height.to_le_bytes());
    h[16..20].copy_from_slice(&stride_bytes.to_le_bytes());
    h[20..24].copy_from_slice(&0u32.to_le_bytes());
    h[24..28].copy_from_slice(&DRM_FORMAT_ABGR8888.to_le_bytes());
    h[32..40].copy_from_slice(&modifier.to_le_bytes());
    h[40..48].copy_from_slice(&size.to_le_bytes());
    h
}

fn poll_in(fd: RawFd, timeout_ms: i32) -> bool {
    let mut p = libc::pollfd { fd, events: libc::POLLIN, revents: 0 };
    // SAFETY: one pollfd.
    unsafe { libc::poll(&mut p, 1, timeout_ms) > 0 }
}

fn send_with_fd(sock: RawFd, data: &[u8], fd: RawFd) -> bool {
    let mut iov = libc::iovec { iov_base: data.as_ptr() as *mut c_void, iov_len: data.len() };
    // SAFETY: CMSG_SPACE of one int.
    let space = unsafe { libc::CMSG_SPACE(4) } as usize;
    let mut cbuf = vec![0u8; space];
    // SAFETY: zeroed msghdr; every pointer set below refers to a live buffer.
    let mut msg: libc::msghdr = unsafe { std::mem::zeroed() };
    msg.msg_iov = &mut iov;
    msg.msg_iovlen = 1;
    msg.msg_control = cbuf.as_mut_ptr() as *mut c_void;
    msg.msg_controllen = space as _;
    // SAFETY: the control buffer has room for one SCM_RIGHTS header with one fd.
    unsafe {
        let c = libc::CMSG_FIRSTHDR(&msg);
        (*c).cmsg_level = libc::SOL_SOCKET;
        (*c).cmsg_type = libc::SCM_RIGHTS;
        (*c).cmsg_len = libc::CMSG_LEN(4) as _;
        std::ptr::write_unaligned(libc::CMSG_DATA(c) as *mut c_int, fd);
    }
    // SAFETY: msg is fully set up.
    let n = unsafe { libc::sendmsg(sock, &msg, libc::MSG_NOSIGNAL) };
    n == data.len() as isize
}

/// Reads one reply line from the client, at most 60 s.
fn read_line(sock: RawFd) -> Option<String> {
    let mut out = Vec::new();
    let deadline = Instant::now() + std::time::Duration::from_secs(60);
    let mut buf = [0u8; 256];
    while Instant::now() < deadline && out.len() < 4096 {
        let left = deadline.saturating_duration_since(Instant::now()).as_millis() as i32;
        if !poll_in(sock, left.max(1)) {
            break;
        }
        // SAFETY: reading into a live buffer.
        let n = unsafe { libc::read(sock, buf.as_mut_ptr() as *mut c_void, buf.len()) };
        if n <= 0 {
            break;
        }
        out.extend_from_slice(&buf[..n as usize]);
        if out.contains(&b'\n') {
            break;
        }
    }
    if out.is_empty() {
        None
    } else {
        let s = String::from_utf8_lossy(&out).into_owned();
        Some(s.lines().next().unwrap_or_default().to_string())
    }
}

/// The colour the client says it cleared to ("ok RRGGBBAA").
fn claimed_colour(reply: &str) -> Option<u32> {
    let mut it = reply.split_whitespace();
    if it.next()? != "ok" {
        return None;
    }
    let hex = it.next()?;
    let v = u32::from_str_radix(hex, 16).ok()?;
    // RRGGBBAA as written -> memory order R, G, B, A.
    Some(u32::from_le_bytes(v.to_be_bytes()))
}

fn sample_points(d: &Desc) -> [(&'static str, u32, u32); 3] {
    [("centre", d.width / 2, d.height / 2), ("corner(0,0)", 0, 0), ("corner(last)", d.width - 1, d.height - 1)]
}

/// Runs E3's host side and returns the buffer for the launcher to show, or None.
fn serve(variant: usize, modifier_choice: i32, path: &str) -> Option<Ahb> {
    let Some(v) = VARIANTS.get(variant) else {
        alog!("E3 no variant {variant} (0..{})", VARIANTS.len() - 1);
        return None;
    };
    let label = format!("E3[{}]", v.name);
    let modifier = if modifier_choice == 1 { DRM_FORMAT_MOD_QCOM_COMPRESSED } else { DRM_FORMAT_MOD_LINEAR };
    let buffer = match Ahb::allocate(PROBE_WIDTH, PROBE_HEIGHT, v.usage) {
        Ok(b) => b,
        Err(rc) => {
            alog!("{label} allocate failed rc={rc}");
            return None;
        }
    };
    let d = buffer.describe();
    let stride_bytes = d.stride.max(d.width) * 4;
    let handle = extract_handle(&label, &buffer)?;
    let Some(fd) = handle.fds.first() else {
        alog!("{label} the handle carried no fd");
        return None;
    };
    let size = fd_size(fd.as_raw_fd()).max(0) as u64;
    // Magenta, so a client that wrote nothing is told apart from one that wrote.
    let sentinel = rgba(255, 0, 255, 255);
    if v.usage & USAGE_CPU_WRITE_MASK != 0 {
        // SAFETY: buffer outlives the lock.
        match unsafe { Locked::new(buffer.0, USAGE_CPU_WRITE_OFTEN) } {
            Ok(lock) => {
                let stride = d.stride.max(d.width) as usize;
                // SAFETY: a locked RGBA_8888 buffer holds stride * height pixels.
                let px = unsafe { std::slice::from_raw_parts_mut(lock.ptr as *mut u32, stride * d.height as usize) };
                px.fill(sentinel);
                drop(lock);
                alog!("{label} prefilled with sentinel {}", hex_rgba(sentinel));
            }
            Err(rc) => alog!("{label} prefill lock failed rc={rc}"),
        }
    }
    alog!(
        "{label} buffer {}x{} stride={stride_bytes} bytes size={size} fourcc=AB24 modifier=0x{modifier:x} fd={}",
        d.width,
        d.height,
        fd.as_raw_fd()
    );

    let _ = std::fs::remove_file(path);
    let Ok(cpath) = CString::new(path) else { return None };
    // SAFETY: a new socket; owned at once.
    let listener = unsafe { libc::socket(libc::AF_UNIX, libc::SOCK_STREAM | libc::SOCK_CLOEXEC, 0) };
    if listener < 0 {
        alog!("{label} socket failed: {}", errno());
        return None;
    }
    // SAFETY: just created.
    let listener = unsafe { OwnedFd::from_raw_fd(listener) };
    // SAFETY: a zeroed sockaddr_un is valid; the path is copied below if it fits.
    let mut addr: libc::sockaddr_un = unsafe { std::mem::zeroed() };
    addr.sun_family = libc::AF_UNIX as _;
    let bytes = cpath.as_bytes_with_nul();
    if bytes.len() > addr.sun_path.len() {
        alog!("{label} socket path too long: {path}");
        return None;
    }
    for (dst, src) in addr.sun_path.iter_mut().zip(bytes) {
        *dst = *src as _;
    }
    // SAFETY: bind and listen on our socket with a filled address.
    let ok = unsafe {
        libc::bind(listener.as_raw_fd(), &addr as *const _ as *const libc::sockaddr, std::mem::size_of::<libc::sockaddr_un>() as _) == 0
            && libc::listen(listener.as_raw_fd(), 1) == 0
    };
    if !ok {
        alog!("{label} bind/listen {path} failed: {}", errno());
        return None;
    }
    alog!("{label} listening on {path} for 120 s: run tlwayland-ahbclient {path}");
    if !poll_in(listener.as_raw_fd(), 120_000) {
        alog!("{label} no client connected in 120 s");
        let _ = std::fs::remove_file(path);
        return None;
    }
    // SAFETY: accept on a listening socket that polled readable.
    let conn = unsafe { libc::accept4(listener.as_raw_fd(), std::ptr::null_mut(), std::ptr::null_mut(), libc::SOCK_CLOEXEC) };
    let _ = std::fs::remove_file(path);
    if conn < 0 {
        alog!("{label} accept failed: {}", errno());
        return None;
    }
    // SAFETY: just accepted.
    let conn = unsafe { OwnedFd::from_raw_fd(conn) };
    let header = e3_header(d.width, d.height, stride_bytes, modifier, size);
    if !send_with_fd(conn.as_raw_fd(), &header, fd.as_raw_fd()) {
        alog!("{label} sending the fd failed: {}", errno());
        return None;
    }
    alog!("{label} fd and layout sent; waiting 60 s for the client");
    let reply = read_line(conn.as_raw_fd());
    alog!("{label} client reply: {}", reply.as_deref().unwrap_or("(none)"));
    let claimed = reply.as_deref().and_then(claimed_colour);

    let points = sample_points(&d);
    let stride_px = d.stride.max(d.width) as usize;
    let mut seen_centre = None;
    if v.usage & USAGE_CPU_READ_MASK != 0 {
        // SAFETY: buffer outlives the lock.
        match unsafe { Locked::new(buffer.0, USAGE_CPU_READ_OFTEN) } {
            Ok(lock) => {
                // SAFETY: a locked RGBA_8888 buffer holds stride * height pixels.
                let px = unsafe { std::slice::from_raw_parts(lock.ptr as *const u32, stride_px * d.height as usize) };
                for (name, x, y) in points {
                    let p = px[y as usize * stride_px + x as usize];
                    if name == "centre" {
                        seen_centre = Some(p);
                    }
                    alog!("{label} readback lock {name} ({x},{y}) = {}", hex_rgba(p));
                }
            }
            Err(rc) => alog!("{label} readback lock failed rc={rc}"),
        }
    } else {
        alog!("{label} readback lock skipped: no CPU read usage");
    }
    let linear_len = stride_bytes as usize * d.height as usize;
    let mapped = if (size as usize) < linear_len {
        Err(format!("the fd holds {size} bytes, fewer than {linear_len}"))
    } else {
        map_read(fd.as_raw_fd(), linear_len)
    };
    match mapped {
        Ok(map) => {
            dma_buf_sync(fd.as_raw_fd(), DMA_BUF_SYNC_START | DMA_BUF_SYNC_READ);
            let words = map.words();
            for (name, x, y) in points {
                let p = words[y as usize * stride_px + x as usize];
                if seen_centre.is_none() && name == "centre" {
                    seen_centre = Some(p);
                }
                alog!("{label} readback mmap (as linear) {name} ({x},{y}) = {}", hex_rgba(p));
            }
            dma_buf_sync(fd.as_raw_fd(), DMA_BUF_SYNC_END | DMA_BUF_SYNC_READ);
        }
        Err(e) => alog!("{label} readback mmap failed ({e})"),
    }
    match (claimed, seen_centre) {
        (Some(c), Some(s)) => alog!(
            "{label} RESULT client cleared {} host reads {} -> {}",
            hex_rgba(c),
            hex_rgba(s),
            if c == s { "MATCH" } else if s == sentinel { "UNCHANGED (sentinel)" } else { "DIFFERENT" }
        ),
        _ => alog!("{label} RESULT no comparison (client reply or readback missing)"),
    }
    Some(buffer)
}

type ToHardwareBuffer = unsafe extern "C" fn(*mut jni::sys::JNIEnv, *mut AHardwareBuffer) -> jni::sys::jobject;

/// `AHardwareBuffer_toHardwareBuffer` (API 27), looked up at run time.
fn to_hardware_buffer_fn() -> Option<ToHardwareBuffer> {
    const LIB: &CStr = c"libandroid.so";
    const SYM: &CStr = c"AHardwareBuffer_toHardwareBuffer";
    // SAFETY: dlopen of a system library already loaded in this process, then dlsym; the
    // symbol's signature is the NDK's.
    unsafe {
        let lib = libc::dlopen(LIB.as_ptr(), libc::RTLD_NOW);
        if lib.is_null() {
            return None;
        }
        let sym = libc::dlsym(lib, SYM.as_ptr());
        if sym.is_null() {
            None
        } else {
            Some(std::mem::transmute::<*mut c_void, ToHardwareBuffer>(sym))
        }
    }
}

/// E3: runs the host side (blocking, up to about three minutes) and returns the buffer as a
/// Java HardwareBuffer for the launcher to show, or null.
pub fn serve_client(env: *mut jni::sys::JNIEnv, variant: i32, modifier_choice: i32, path: &str) -> jni::sys::jobject {
    let started = Instant::now();
    let buffer = serve(variant.max(0) as usize, modifier_choice, path);
    alog!("E3 host side done in {} ms", started.elapsed().as_millis());
    let Some(buffer) = buffer else { return std::ptr::null_mut() };
    match to_hardware_buffer_fn() {
        // SAFETY: a live env on this thread and a live buffer; the HardwareBuffer takes its own
        // reference, ours is released when `buffer` drops.
        Some(f) => unsafe { f(env, buffer.0) },
        None => {
            alog!("E3 AHardwareBuffer_toHardwareBuffer missing (API 27+): the launcher cannot show it");
            std::ptr::null_mut()
        }
    }
}
