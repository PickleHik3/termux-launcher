//! tlstore-ui: the full-screen tlstore, drawn by the terminal itself (text, OSC 66 text
//! sizing, kitty pictures, SGR mouse). The `tlstore` shell script stays the engine; this
//! program only draws and calls it.
//!
//! Layers: [`term`] (raw mode, input, probe, signals) → [`render`] (cells, sized runs,
//! pictures, tap regions, diff) → [`app`] (screen stack and loop). [`picture`], [`layout`] and
//! [`palette`] are the shared pieces screens draw with.

pub mod app;
pub mod demo;
pub mod launch;
pub mod layout;
pub mod palette;
pub mod picture;
pub mod render;
pub mod store;
pub mod term;
