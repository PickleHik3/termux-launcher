//! libtlwayland: a Wayland compositor that runs in the launcher's `:linuxwindows` process.
//!
//! Each toplevel is shown in its own Android view: the launcher hands this library one
//! `Surface` per toplevel over binder and the compositor copies the client's wl_shm buffer into
//! it, at most once per frame tick.

pub mod clipboard;
pub mod compose;
pub mod geometry;
pub mod held;
pub mod keymap;
pub mod paced;
pub mod pixels;
pub mod png;
pub mod protocol;
pub mod teardown;
pub mod xkbroot;
pub mod xlock;
pub mod xscale;

#[cfg(target_os = "android")]
mod android;
