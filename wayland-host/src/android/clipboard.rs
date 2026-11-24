//! Android's clipboard and the Linux clipboard, as plain text.
//!
//! Android to Linux: the launcher sends its text (`Command::SetClipboardText`); the compositor
//! owns that selection toward Wayland clients and, through the X11 window manager, toward X11
//! clients. Linux to Android: when a client (Wayland or X11) sets the selection, its text is read
//! on a short-lived thread, handed back to the compositor thread through a channel and reported
//! to Java. Pipes are never waited on from the compositor thread, so a client that never reads
//! or never writes cannot hang the host. `crate::clipboard` has the pure parts.

use std::sync::Arc;

use smithay::input::Seat;
use smithay::reexports::calloop::channel::Sender;
use smithay::reexports::wayland_server::Resource;
use smithay::wayland::seat::WaylandFocus;
use smithay::wayland::selection::data_device::{
    clear_data_device_selection, current_data_device_selection_userdata, request_data_device_client_selection,
    set_data_device_focus, set_data_device_selection,
};
use smithay::wayland::selection::SelectionTarget;

use crate::clipboard::{pick_text_mime, pipe, read_text, write_text, Echo, MAX_TEXT_BYTES, OFFERED_MIMES, TRANSFER_TIMEOUT};

use super::focus::KeyboardFocus;
use super::host::State;
use super::log::{linfo, lwarn};

/// Who owns the selection the compositor set; the user data of smithay's server-side selection.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ClipSource {
    /// Text the launcher sent from Android's clipboard.
    Android,
    /// A selection an X11 client set, offered to Wayland clients.
    X11,
}

/// Where a Linux selection that was just set is read from.
#[derive(Clone, Copy)]
pub enum Pull {
    Wayland,
    X11,
}

pub struct ClipState {
    /// What Android last sent; served while the selection is `ClipSource::Android`.
    android_text: Option<Arc<str>>,
    echo: Echo,
    /// Texts the reader threads read, back to the compositor thread.
    pub texts: Sender<String>,
}

impl ClipState {
    pub fn new(texts: Sender<String>) -> ClipState {
        ClipState { android_text: None, echo: Echo::default(), texts }
    }
}

fn spawn(name: &str, f: impl FnOnce() + Send + 'static) {
    if let Err(e) = std::thread::Builder::new().name(name.into()).spawn(f) {
        lwarn!("clipboard: cannot start {name}: {e}");
    }
}

impl State {
    /// Android's clipboard changed (or the screen came to the front): offer its text to Linux.
    pub fn set_android_text(&mut self, text: String) {
        if text.is_empty() || text.len() > MAX_TEXT_BYTES {
            return;
        }
        if !self.clip.echo.crossing(&text) {
            return;
        }
        self.clip.android_text = Some(text.into());
        let mimes: Vec<String> = OFFERED_MIMES.iter().map(|m| m.to_string()).collect();
        if let Some(wm) = self.xwayland.as_mut().and_then(|x| x.wm.as_mut()) {
            if let Err(e) = wm.new_selection(SelectionTarget::Clipboard, Some(mimes.clone())) {
                lwarn!("clipboard: X11 selection: {e:?}");
            }
        }
        set_data_device_selection(&self.display_handle, &self.seat, mimes, ClipSource::Android);
    }

    /// A text read from a Linux client, on the compositor thread: report it unless it is the
    /// one that crossed last.
    pub fn linux_text(&mut self, text: String) {
        if text.is_empty() || !self.clip.echo.crossing(&text) {
            return;
        }
        self.events.clipboard_text(&text);
    }

    /// A client set the clipboard selection offering `mimes`: read its text once it is stored.
    pub fn pull_linux_text(&mut self, from: Pull, mimes: Vec<String>) {
        self.loop_handle.insert_idle(move |state| state.pull_now(from, &mimes));
    }

    fn pull_now(&mut self, from: Pull, mimes: &[String]) {
        let Some(mime) = pick_text_mime(mimes).map(str::to_owned) else { return };
        let Some((read, write)) = pipe() else { return };
        let asked = match from {
            Pull::Wayland => request_data_device_client_selection(&self.seat, mime.clone(), write).map_err(|e| format!("{e}")),
            Pull::X11 => {
                let handle = self.loop_handle.clone();
                match self.xwayland.as_mut().and_then(|x| x.wm.as_mut()) {
                    Some(wm) => wm.send_selection(SelectionTarget::Clipboard, mime.clone(), write, handle).map_err(|e| format!("{e:?}")),
                    None => Err("no X11 window manager".into()),
                }
            }
        };
        if let Err(e) = asked {
            linfo!("clipboard: cannot read the selection as {mime}: {e}");
            return;
        }
        let tx = self.clip.texts.clone();
        spawn("tl-clip-read", move || {
            if let Some(text) = read_text(read, MAX_TEXT_BYTES, TRANSFER_TIMEOUT) {
                let _ = tx.send(text);
            }
        });
    }

    /// A client asks for the selection the compositor owns, in `mime`, to be written to `fd`.
    pub fn serve_selection(&mut self, source: ClipSource, mime: String, fd: std::os::fd::OwnedFd) {
        match source {
            ClipSource::Android => self.write_android_text(fd),
            ClipSource::X11 => {
                let handle = self.loop_handle.clone();
                if let Some(wm) = self.xwayland.as_mut().and_then(|x| x.wm.as_mut()) {
                    if let Err(e) = wm.send_selection(SelectionTarget::Clipboard, mime, fd, handle) {
                        lwarn!("clipboard: X11 selection read: {e:?}");
                    }
                }
            }
        }
    }

    /// An X11 client reads the selection the compositor mirrored: Android's text, or a Wayland
    /// client's selection.
    pub fn serve_x11_read(&mut self, mime: String, fd: std::os::fd::OwnedFd) {
        let owner = current_data_device_selection_userdata(&self.seat).map(|u| *u);
        match owner {
            Some(ClipSource::Android) => self.write_android_text(fd),
            Some(ClipSource::X11) => {}
            None => {
                if let Err(e) = request_data_device_client_selection(&self.seat, mime, fd) {
                    linfo!("clipboard: X11 read of a Wayland selection: {e}");
                }
            }
        }
    }

    fn write_android_text(&self, fd: std::os::fd::OwnedFd) {
        let Some(text) = self.clip.android_text.clone() else { return };
        spawn("tl-clip-write", move || {
            if !write_text(fd, text.as_bytes(), TRANSFER_TIMEOUT) {
                linfo!("clipboard: a client did not take the text in time");
            }
        });
    }

    /// An X11 client cleared its selection.
    pub fn x11_selection_cleared(&mut self) {
        if current_data_device_selection_userdata(&self.seat).map(|u| *u) == Some(ClipSource::X11) {
            clear_data_device_selection(&self.display_handle, &self.seat);
        }
    }

    /// An X11 client set the selection: offer it to Wayland clients and read its text.
    pub fn x11_selection_set(&mut self, mimes: Vec<String>) {
        set_data_device_selection(&self.display_handle, &self.seat, mimes.clone(), ClipSource::X11);
        self.pull_linux_text(Pull::X11, mimes);
    }

    /// A Wayland client set the selection: mirror it to X11 clients and read its text.
    pub fn wayland_selection_set(&mut self, mimes: Option<Vec<String>>) {
        if let Some(wm) = self.xwayland.as_mut().and_then(|x| x.wm.as_mut()) {
            if let Err(e) = wm.new_selection(SelectionTarget::Clipboard, mimes.clone()) {
                lwarn!("clipboard: X11 selection: {e:?}");
            }
        }
        if let Some(mimes) = mimes {
            self.pull_linux_text(Pull::Wayland, mimes);
        }
    }

    /// The seat's keyboard focus moved: that client may now set and see the selection.
    pub fn focus_data_device(&mut self, seat: &Seat<State>, focused: Option<&KeyboardFocus>) {
        let client = focused.and_then(|f| f.wl_surface()).and_then(|s| s.client());
        set_data_device_focus(&self.display_handle, seat, client);
    }
}
