//! JNI surface of libtlwayland, loaded only in the `:linuxwindows` process. Every entry point
//! catches panics all the same.
//!
//! Java side: `com.termux.app.wayland.WaylandHost`, driven by `LinuxWindowsService`. The numbers
//! these calls carry are `crate::protocol`'s, mirrored from `ILinuxWindows.aidl`.

pub mod log;
mod clipboard;
mod focus;
mod host;
mod java;
mod probe;
mod signals;
mod source;
mod text_input;
mod window;
mod xwayland;

use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;

use jni::objects::{JClass, JFloatArray, JIntArray, JObject, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use crate::geometry::sane_scale;
use crate::paced::frame_period;
use host::{Command, HostHandle, StartConfig};
use log::{lerror, linfo, lwarn};
use window::NativeWindow;

static HOST: Mutex<Option<HostHandle>> = Mutex::new(None);
/// "compositor thread is gone" is logged once per host, not once per call.
static GONE_LOGGED: AtomicBool = AtomicBool::new(false);

fn guarded<T>(name: &str, fallback: T, f: impl FnOnce() -> T) -> T {
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(v) => v,
        Err(_) => {
            lerror!("panic in {name}");
            fallback
        }
    }
}

fn host_lock() -> std::sync::MutexGuard<'static, Option<HostHandle>> {
    match HOST.lock() {
        Ok(g) => g,
        Err(p) => p.into_inner(),
    }
}

fn send(cmd: Command) {
    let guard = host_lock();
    if let Some(host) = guard.as_ref() {
        if host.sender.send(cmd).is_err() && !GONE_LOGGED.swap(true, Ordering::Relaxed) {
            lwarn!("compositor thread is gone");
        }
    }
}

/// Android event times are uptime milliseconds; the protocol's are 32 bits of the same clock.
fn event_time(ms: jlong) -> u32 {
    ms as u64 as u32
}

/// This process's name, from /proc/self/cmdline (e.g. `com.termux:linuxwindows`).
fn process_name() -> String {
    std::fs::read("/proc/self/cmdline")
        .ok()
        .map(|b| String::from_utf8_lossy(b.split(|c| *c == 0).next().unwrap_or_default()).into_owned())
        .unwrap_or_default()
}

fn java_string(env: &mut JNIEnv, s: &JString) -> Option<String> {
    if s.is_null() {
        return None;
    }
    env.get_string(s).ok().map(|s| s.into())
}

/// Sets an environment variable unless it already has that value. Called only from nativeStart
/// on the Java thread before the compositor thread exists and with no probe thread started, so
/// no thread of ours reads the environment meanwhile; in practice each is set once per process.
fn set_env_once(name: &str, value: &Path) {
    if std::env::var_os(name).as_deref() != Some(value.as_os_str()) {
        std::env::set_var(name, value);
    }
}

/// Starts the compositor. `socketDir` is the prefix's tmp, the clients' runtime directory; the
/// socket is `wayland-0` in it and Xwayland's sockets are in its `.X11-unix`. `xkbConfigRoot` (nullable) becomes XKB_CONFIG_ROOT for the seat's keymap.
/// `xwaylandBinary` (nullable) starts rootless Xwayland when set. `scale` is the display density
/// and `framePeriodNanos` its refresh period (the frame tick). `listenFd`, when 0 or more, is the
/// launcher's socket already listening at that path, taken over (and closed here on failure);
/// below 0 the host binds the path itself. Returns false when the host could
/// not start or is already running.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeStart<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    socket_dir: JString<'l>,
    xkb_config_root: JString<'l>,
    xwayland_binary: JString<'l>,
    output_width: jint,
    output_height: jint,
    scale: jfloat,
    frame_period_nanos: jlong,
    listen_fd: jint,
) -> jboolean {
    guarded("nativeStart", JNI_FALSE, || {
        // Owned from here, so every early return closes it.
        // SAFETY: Java detached this fd from its ParcelFileDescriptor; nothing else closes it.
        let listen_fd = (listen_fd >= 0).then(|| unsafe { <std::os::fd::OwnedFd as std::os::fd::FromRawFd>::from_raw_fd(listen_fd) });
        let mut guard = host_lock();
        if let Some(handle) = guard.take() {
            if !handle.thread.is_finished() {
                *guard = Some(handle);
                lwarn!("nativeStart: already running");
                return JNI_FALSE;
            }
            // The last host ended on its own; its thread is done, so this join is immediate.
            let _ = handle.thread.join();
            linfo!("nativeStart: the previous host had ended");
        }
        let Some(dir) = java_string(&mut env, &socket_dir) else { return JNI_FALSE };
        linfo!("host starting in pid {} ({})", std::process::id(), process_name());
        // Both are read later on the compositor thread (libxkbcommon, smithay's X11 sockets);
        // set here, before it exists.
        if let Some(root) = java_string(&mut env, &xkb_config_root) {
            set_env_once("XKB_CONFIG_ROOT", Path::new(&root));
        }
        let xwayland = java_string(&mut env, &xwayland_binary).map(|binary| xwayland::XwaylandConfig::new(binary, &dir));
        if let Some(tmp) = xwayland.as_ref().and_then(|x| x.tmp_dir()) {
            set_env_once("SMITHAY_X11_TMPDIR", &tmp);
        }
        let callbacks = match java::JavaCallbacks::resolve(&mut env) {
            Ok(c) => c,
            Err(e) => {
                lerror!("nativeStart: cannot resolve Java callbacks: {e}");
                let _ = env.exception_clear();
                return JNI_FALSE;
            }
        };
        let config = StartConfig {
            socket_path: PathBuf::from(&dir).join("wayland-0"),
            output_width,
            output_height,
            scale: sane_scale(scale),
            frame_period: frame_period(frame_period_nanos),
            xwayland,
            listen_fd,
        };
        let events = move || {
            callbacks.attach().map(|(events, ended)| {
                (Box::new(events) as Box<dyn java::HostEvents>, Box::new(move |panicked| ended.report(panicked)) as host::EndReporter)
            })
        };
        match host::spawn(config, events) {
            Ok(handle) => {
                GONE_LOGGED.store(false, Ordering::Relaxed);
                *guard = Some(handle);
                JNI_TRUE
            }
            Err(e) => {
                lerror!("nativeStart: {e}");
                JNI_FALSE
            }
        }
    })
}

/// Stops the compositor and waits for its thread. Clients are disconnected and the socket is
/// removed. Also clears a host that ended on its own.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeStop<'l>(_env: JNIEnv<'l>, _class: JClass<'l>) {
    guarded("nativeStop", (), || {
        let handle = host_lock().take();
        if let Some(handle) = handle {
            handle.stopping.store(true, std::sync::atomic::Ordering::SeqCst);
            handle.signal.stop();
            handle.signal.wakeup();
            if handle.thread.join().is_err() {
                lerror!("nativeStop: compositor thread panicked");
            }
            linfo!("host stopped");
        }
    })
}

/// Hands the window `id` the Surface it is drawn into, or takes it away when `surface` is null.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeSetSurface<'l>(
    env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    surface: JObject<'l>,
) {
    guarded("nativeSetSurface", (), || {
        let window = if surface.is_null() {
            None
        } else {
            // SAFETY: a live env on this thread and a Surface reference from Java.
            let w = unsafe { NativeWindow::from_surface(env.get_raw(), surface.as_raw()) };
            if w.is_none() {
                lwarn!("nativeSetSurface: no native window for window {id}");
                return;
            }
            w
        };
        send(Command::SetWindow { id, window });
    })
}

/// The size, in pixels, of the view that shows window `id`, and the density it is shown at.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeConfigure<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    width: jint,
    height: jint,
    scale: jfloat,
) {
    guarded("nativeConfigure", (), || send(Command::Configure { id, width, height, scale: sane_scale(scale) }))
}

/// The launcher started `pid` as a Wayland desktop (`on`), or it ended: its windows are configured
/// in real pixels from the first configure.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeSetPixelClient<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    pid: jint,
    on: jboolean,
) {
    guarded("nativeSetPixelClient", (), || send(Command::PixelClient { pid, on: on != 0 }))
}

/// The size, in pixels, of the page slot a new full-page window gets, and the scale it would
/// use; told whenever it changes.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeSetPageSize<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    width: jint,
    height: jint,
    scale: jfloat,
) {
    guarded("nativeSetPageSize", (), || send(Command::PageSize { width, height, scale: sane_scale(scale) }))
}

/// Whether window `id`'s view is on screen.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeSetVisible<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    visible: jboolean,
) {
    guarded("nativeSetVisible", (), || send(Command::SetVisible { id, visible: visible != JNI_FALSE }))
}

/// Shows window `id` full screen, or not; its next configure carries the size.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeSetFullscreen<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    on: jboolean,
) {
    guarded("nativeSetFullscreen", (), || send(Command::SetFullscreen { id, on: on != JNI_FALSE }))
}

/// Gives window `id` keyboard focus; 0 takes it from every window.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeSetFocus<'l>(_env: JNIEnv<'l>, _class: JClass<'l>, id: jlong) {
    guarded("nativeSetFocus", (), || send(Command::SetFocus { id }))
}

/// One MotionEvent for window `id`: the masked action, the action index, the event time and
/// every pointer (id, x, y in view pixels); one wl_touch.frame. Only the first `count` entries
/// (at most MAX_TOUCH_POINTERS) are read.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeTouch<'l>(
    env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    action: jint,
    action_index: jint,
    event_time_ms: jlong,
    count: jint,
    pointer_ids: JIntArray<'l>,
    xs: JFloatArray<'l>,
    ys: JFloatArray<'l>,
) {
    guarded("nativeTouch", (), || {
        let n = (count.max(0) as usize).min(host::MAX_TOUCH_POINTS);
        let mut s = [0i32; host::MAX_TOUCH_POINTS];
        let mut x = [0f32; host::MAX_TOUCH_POINTS];
        let mut y = [0f32; host::MAX_TOUCH_POINTS];
        if n > 0
            && (env.get_int_array_region(&pointer_ids, 0, &mut s[..n]).is_err()
                || env.get_float_array_region(&xs, 0, &mut x[..n]).is_err()
                || env.get_float_array_region(&ys, 0, &mut y[..n]).is_err())
        {
            let _ = env.exception_clear();
            return;
        }
        let mut points = host::TouchPoints { count: n, points: [(0, 0.0, 0.0); host::MAX_TOUCH_POINTS] };
        for k in 0..n {
            points.points[k] = (s[k], x[k], y[k]);
        }
        send(Command::Touch { id, action, action_index, time: event_time(event_time_ms), points });
    })
}

/// A hardware key for window `id`: an Android key code, mapped to evdev here. Only the focused
/// window takes keys.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeKey<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    android_keycode: jint,
    down: jboolean,
    event_time_ms: jlong,
) {
    guarded("nativeKey", (), || {
        send(Command::Key { id, android_keycode, down: down != JNI_FALSE, time: event_time(event_time_ms) })
    })
}

/// Asks window `id` to close (xdg_toplevel.close, or WM_DELETE_WINDOW for X11).
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeClose<'l>(_env: JNIEnv<'l>, _class: JClass<'l>, id: jlong) {
    guarded("nativeClose", (), || send(Command::Close { id }))
}

/// A pointer event for window `id`; `action` is a POINTER_* of the protocol.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativePointer<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    action: jint,
    button: jint,
    x: jfloat,
    y: jfloat,
    event_time_ms: jlong,
) {
    guarded("nativePointer", (), || {
        send(Command::Pointer { id, action, button, x, y, time: event_time(event_time_ms) })
    })
}

/// Android's clipboard text, offered to Linux clients. Null and oversize texts are dropped.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeSetClipboardText<'l>(mut env: JNIEnv<'l>, _class: JClass<'l>, text: JString<'l>) {
    guarded("nativeSetClipboardText", (), || {
        if let Some(text) = java_string(&mut env, &text).filter(|t| t.len() <= crate::clipboard::MAX_TEXT_BYTES) {
            send(Command::SetClipboardText { text });
        }
    })
}

/// Typed text for window `id`'s focused text field; dropped when it has none enabled.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeCommitText<'l>(mut env: JNIEnv<'l>, _class: JClass<'l>, id: jlong, text: JString<'l>) {
    guarded("nativeCommitText", (), || {
        if let Some(text) = java_string(&mut env, &text).filter(|t| !t.is_empty() && t.len() <= crate::text_field::MAX_TEXT_BYTES) {
            send(Command::CommitText { id, text });
        }
    })
}

/// Composing text for window `id`'s focused text field; empty clears it. `cursor`: a UTF-16
/// index into the text, -1 for none.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeSetPreeditText<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    text: JString<'l>,
    cursor: jint,
) {
    guarded("nativeSetPreeditText", (), || {
        let text = java_string(&mut env, &text).unwrap_or_default();
        if text.len() <= crate::text_field::MAX_TEXT_BYTES {
            send(Command::SetPreedit { id, text, cursor });
        }
    })
}

/// Deletes UTF-16 code units before and after the cursor of window `id`'s focused text field.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeDeleteSurroundingText<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    before: jint,
    after: jint,
) {
    guarded("nativeDeleteSurroundingText", (), || send(Command::DeleteSurrounding { id, before, after }))
}

/// Debug: kills this process with abort(), to check that the launcher survives it.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeAbort<'l>(_env: JNIEnv<'l>, _class: JClass<'l>) {
    lerror!("aborting on request (Crash compositor)");
    std::process::abort();
}

/// Debug: logs what the GPU drivers offer, on a thread of its own. Never runs unless asked: a
/// driver that crashes in it takes this process down.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeProbe<'l>(_env: JNIEnv<'l>, _class: JClass<'l>) {
    guarded("nativeProbe", (), probe::spawn)
}

/// Debug: turns the once-a-second copy-time line per window on or off.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeSetCopyLog<'l>(_env: JNIEnv<'l>, _class: JClass<'l>, on: jboolean) {
    host::COPY_LOG.store(on != JNI_FALSE, Ordering::Relaxed);
}

/// Debug: writes what window `id` shows as a PNG to `path`. The service has checked that the
/// path is inside the app's data directory.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeDumpWindow<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    path: JString<'l>,
) {
    guarded("nativeDumpWindow", (), || {
        if let Some(path) = java_string(&mut env, &path) {
            send(Command::Dump { id, path });
        }
    })
}
