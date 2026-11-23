//! Logcat under the one tag `TLWayland`.

use std::ffi::CString;
use std::os::raw::{c_char, c_int};

#[link(name = "log")]
extern "C" {
    fn __android_log_write(prio: c_int, tag: *const c_char, text: *const c_char) -> c_int;
}

const TAG: &[u8] = b"TLWayland\0";
pub const INFO: c_int = 4;
pub const WARN: c_int = 5;
pub const ERROR: c_int = 6;

pub fn write(prio: c_int, msg: &str) {
    let text = CString::new(msg.replace('\0', " ")).unwrap_or_default();
    unsafe {
        __android_log_write(prio, TAG.as_ptr() as *const c_char, text.as_ptr());
    }
}

macro_rules! linfo {
    ($($arg:tt)*) => { $crate::android::log::write($crate::android::log::INFO, &format!($($arg)*)) };
}
macro_rules! lwarn {
    ($($arg:tt)*) => { $crate::android::log::write($crate::android::log::WARN, &format!($($arg)*)) };
}
macro_rules! lerror {
    ($($arg:tt)*) => { $crate::android::log::write($crate::android::log::ERROR, &format!($($arg)*)) };
}
pub(crate) use {lerror, linfo, lwarn};
