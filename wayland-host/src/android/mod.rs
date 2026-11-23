//! JNI surface of libtlwayland, loaded only in the `:linuxwindows` process. Every entry point
//! catches panics all the same.
//!
//! Java side: `com.termux.app.wayland.WaylandHost`, driven by `LinuxWindowsService`.

pub mod log;
mod focus;
mod host;
mod java;
mod probe;
mod window;
mod xwayland;

use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::PathBuf;
use std::sync::Mutex;

use jni::objects::{JClass, JFloatArray, JIntArray, JObject, JString};
use jni::sys::{jboolean, jfloat, jint, jlong, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use host::{Command, HostHandle, StartConfig};
use log::{lerror, linfo, lwarn};
use window::NativeWindow;

static HOST: Mutex<Option<HostHandle>> = Mutex::new(None);

fn guarded<T>(name: &str, fallback: T, f: impl FnOnce() -> T) -> T {
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(v) => v,
        Err(_) => {
            lerror!("panic in {name}");
            fallback
        }
    }
}

fn send(cmd: Command) {
    let guard = match HOST.lock() {
        Ok(g) => g,
        Err(p) => p.into_inner(),
    };
    if let Some(host) = guard.as_ref() {
        if host.sender.send(cmd).is_err() {
            lwarn!("compositor thread is gone");
        }
    }
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

/// Starts the compositor. `socketDir` is the clients' XDG_RUNTIME_DIR; the socket is
/// `wayland-0` in it. `xkbConfigRoot` (nullable) becomes XKB_CONFIG_ROOT for the seat's keymap.
/// `xwaylandBinary` (nullable) starts rootless Xwayland when set. Returns false when the host
/// could not start or is already running.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeStart<'l>(
    mut env: JNIEnv<'l>,
    _class: JClass<'l>,
    socket_dir: JString<'l>,
    xkb_config_root: JString<'l>,
    xwayland_binary: JString<'l>,
    output_width: jint,
    output_height: jint,
) -> jboolean {
    guarded("nativeStart", JNI_FALSE, || {
        let mut guard = match HOST.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        if guard.is_some() {
            lwarn!("nativeStart: already running");
            return JNI_FALSE;
        }
        let Some(dir) = java_string(&mut env, &socket_dir) else { return JNI_FALSE };
        linfo!("host starting in pid {} ({})", std::process::id(), process_name());
        probe::spawn();
        if let Some(root) = java_string(&mut env, &xkb_config_root) {
            // Read by libxkbcommon when the seat's keyboard builds its keymap, on the compositor
            // thread, which does not exist yet.
            std::env::set_var("XKB_CONFIG_ROOT", root);
        }
        let xwayland = java_string(&mut env, &xwayland_binary).map(|binary| xwayland::XwaylandConfig::new(binary, &dir));
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
            xwayland,
        };
        let events = move || callbacks.attach().map(|a| Box::new(a) as Box<dyn java::HostEvents>);
        match host::spawn(config, events) {
            Ok(handle) => {
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

/// Stops the compositor and waits for its thread. Clients are disconnected and the socket
/// is removed.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeStop<'l>(_env: JNIEnv<'l>, _class: JClass<'l>) {
    guarded("nativeStop", (), || {
        let handle = match HOST.lock() {
            Ok(mut g) => g.take(),
            Err(p) => p.into_inner().take(),
        };
        if let Some(handle) = handle {
            handle.signal.stop();
            handle.signal.wakeup();
            if handle.thread.join().is_err() {
                lerror!("nativeStop: compositor thread panicked");
            }
            linfo!("host stopped");
        }
    })
}

/// Hands the toplevel `id` the Surface it is shown in, or takes it away when `surface` is null.
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
                lwarn!("nativeSetSurface: no native window for toplevel {id}");
                return;
            }
            w
        };
        send(Command::SetWindow { id, window });
    })
}

/// Tells the toplevel `id` the size, in pixels, of the view that shows it.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeConfigure<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    width: jint,
    height: jint,
) {
    guarded("nativeConfigure", (), || send(Command::Configure { id, width, height }))
}

/// One MotionEvent's touch points for the toplevel `id`, in view pixels; they share `action`
/// (0 down, 1 up, 2 motion, 3 cancel) and are followed by one wl_touch.frame. `slots` are
/// Android pointer ids; only the first `count` entries (at most 10) are read.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeTouch<'l>(
    env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    action: jint,
    count: jint,
    slots: JIntArray<'l>,
    xs: JFloatArray<'l>,
    ys: JFloatArray<'l>,
) {
    guarded("nativeTouch", (), || {
        let n = (count.max(0) as usize).min(host::MAX_TOUCH_POINTS);
        let mut s = [0i32; host::MAX_TOUCH_POINTS];
        let mut x = [0f32; host::MAX_TOUCH_POINTS];
        let mut y = [0f32; host::MAX_TOUCH_POINTS];
        if n > 0
            && (env.get_int_array_region(&slots, 0, &mut s[..n]).is_err()
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
        send(Command::Touch { id, action, points });
    })
}

/// A hardware key for the toplevel `id`: an Android key code, mapped to evdev here. The
/// toplevel takes keyboard focus first.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeKey<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    android_keycode: jint,
    down: jboolean,
) {
    guarded("nativeKey", (), || send(Command::Key { id, android_keycode, down: down != JNI_FALSE }))
}

/// Asks the toplevel `id` to close (xdg_toplevel.close, or WM_DELETE_WINDOW for X11).
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeClose<'l>(_env: JNIEnv<'l>, _class: JClass<'l>, id: jlong) {
    guarded("nativeClose", (), || send(Command::Close { id }))
}

/// Debug: kills this process with abort(), to check that the launcher survives it.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativeAbort<'l>(_env: JNIEnv<'l>, _class: JClass<'l>) {
    lerror!("aborting on request (Crash compositor)");
    std::process::abort();
}

/// A pointer event for the toplevel `id`. `action`: 0 motion (x, y in view pixels),
/// 1 button down, 2 button up (`button` is a Linux BTN_* code), 3 axis (x, y are scroll
/// distances), 4 leave.
#[no_mangle]
pub extern "system" fn Java_com_termux_app_wayland_WaylandHost_nativePointer<'l>(
    _env: JNIEnv<'l>,
    _class: JClass<'l>,
    id: jlong,
    action: jint,
    button: jint,
    x: jfloat,
    y: jfloat,
) {
    guarded("nativePointer", (), || send(Command::Pointer { id, action, button, x, y }))
}
