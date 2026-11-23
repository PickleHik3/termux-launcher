//! The few ANativeWindow calls the presenter needs (libandroid, API 26).

use std::os::raw::{c_int, c_void};
use std::time::Instant;

use crate::pixels;

#[repr(C)]
pub struct ANativeWindow {
    _private: [u8; 0],
}

#[repr(C)]
struct ANativeWindowBuffer {
    width: i32,
    height: i32,
    /// In pixels.
    stride: i32,
    format: i32,
    bits: *mut c_void,
    reserved: [u32; 6],
}

#[repr(C)]
struct ARect {
    left: i32,
    top: i32,
    right: i32,
    bottom: i32,
}

pub const WINDOW_FORMAT_RGBA_8888: i32 = 1;

#[link(name = "android")]
extern "C" {
    fn ANativeWindow_fromSurface(env: *mut jni::sys::JNIEnv, surface: jni::sys::jobject) -> *mut ANativeWindow;
    fn ANativeWindow_release(window: *mut ANativeWindow);
    fn ANativeWindow_setBuffersGeometry(window: *mut ANativeWindow, width: i32, height: i32, format: i32) -> c_int;
    fn ANativeWindow_lock(window: *mut ANativeWindow, out: *mut ANativeWindowBuffer, dirty: *mut ARect) -> c_int;
    fn ANativeWindow_unlockAndPost(window: *mut ANativeWindow) -> c_int;
}

/// One reference on an ANativeWindow, released on drop. Used only on the compositor thread
/// after it is handed over.
pub struct NativeWindow {
    ptr: *mut ANativeWindow,
    geometry: (i32, i32),
}

// The window is created on the JNI thread and then only touched by the compositor thread.
unsafe impl Send for NativeWindow {}

/// Timings of one post, for the once-a-second log line.
pub struct PostTiming {
    /// Pixel conversion only.
    pub copy_us: u64,
    /// lock + conversion + unlockAndPost.
    pub total_us: u64,
}

impl NativeWindow {
    /// # Safety
    /// `env` and `surface` must be a live JNI env and an `android.view.Surface` reference.
    pub unsafe fn from_surface(env: *mut jni::sys::JNIEnv, surface: jni::sys::jobject) -> Option<NativeWindow> {
        let ptr = ANativeWindow_fromSurface(env, surface);
        if ptr.is_null() {
            None
        } else {
            Some(NativeWindow { ptr, geometry: (0, 0) })
        }
    }

    /// Copies a `width` x `height` wl_shm image (`stride` bytes per row) into the window and
    /// posts it. The window's buffers take the client's size; TextureView scales to the view.
    pub fn post(&mut self, src: &[u8], stride: usize, width: i32, height: i32, opaque: bool) -> Result<PostTiming, i32> {
        let start = Instant::now();
        unsafe {
            if self.geometry != (width, height) {
                let rc = ANativeWindow_setBuffersGeometry(self.ptr, width, height, WINDOW_FORMAT_RGBA_8888);
                if rc != 0 {
                    return Err(rc);
                }
                self.geometry = (width, height);
            }
            let mut buf = ANativeWindowBuffer {
                width: 0,
                height: 0,
                stride: 0,
                format: 0,
                bits: std::ptr::null_mut(),
                reserved: [0; 6],
            };
            let rc = ANativeWindow_lock(self.ptr, &mut buf, std::ptr::null_mut());
            if rc != 0 {
                return Err(rc);
            }
            let copy_start = Instant::now();
            if !buf.bits.is_null() && buf.format == WINDOW_FORMAT_RGBA_8888 {
                let w = width.min(buf.width).max(0) as usize;
                let h = height.min(buf.height).max(0) as usize;
                let dst_stride = buf.stride as usize * 4;
                let dst = std::slice::from_raw_parts_mut(buf.bits as *mut u8, dst_stride * buf.height as usize);
                pixels::convert(src, stride, dst, dst_stride, w, h, opaque);
            }
            let copy_us = copy_start.elapsed().as_micros() as u64;
            let rc = ANativeWindow_unlockAndPost(self.ptr);
            if rc != 0 {
                return Err(rc);
            }
            Ok(PostTiming { copy_us, total_us: start.elapsed().as_micros() as u64 })
        }
    }
}

impl Drop for NativeWindow {
    fn drop(&mut self) {
        unsafe { ANativeWindow_release(self.ptr) }
    }
}
