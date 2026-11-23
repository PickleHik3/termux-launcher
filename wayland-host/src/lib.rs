//! libtlwayland: a Wayland compositor that runs inside the launcher process (spike).
//!
//! Each xdg_toplevel is shown in its own Android TextureView: Java hands this library one
//! `Surface` per toplevel and the compositor copies the client's wl_shm buffer into it.

pub mod pixels;

#[cfg(target_os = "android")]
mod android;
