//! libtlwayland: a Wayland compositor that runs in the launcher's `:linuxwindows` process
//! (spike).
//!
//! Each xdg_toplevel is shown in its own Android TextureView: the launcher hands this library
//! one `Surface` per toplevel over binder and the compositor copies the client's wl_shm buffer
//! into it.

pub mod geometry;
pub mod keymap;
pub mod pixels;

#[cfg(target_os = "android")]
mod android;
