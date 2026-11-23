//! Calls from the compositor thread back into `com.termux.app.wayland.WaylandHost`.
//!
//! The class and method ids are resolved in `nativeStart` on the Java thread: a thread attached
//! from native code finds classes through the system class loader, which cannot see app classes.
//! The compositor thread stays attached for its whole life, so every call runs inside its own
//! local frame; otherwise the local references it creates would never be freed.

use jni::objects::{GlobalRef, JClass, JStaticMethodID};
use jni::signature::{Primitive, ReturnType};
use jni::sys::{jvalue, JNI_FALSE, JNI_TRUE};
use jni::{JNIEnv, JavaVM};

use super::log::lwarn;

pub const HOST_CLASS: &str = "com/termux/app/wayland/WaylandHost";

#[derive(Clone, Copy)]
struct Methods {
    created: JStaticMethodID,
    changed: JStaticMethodID,
    destroyed: JStaticMethodID,
    ended: JStaticMethodID,
    x_display: JStaticMethodID,
}

pub struct JavaCallbacks {
    vm: JavaVM,
    class: GlobalRef,
    methods: Methods,
}

/// The compositor thread's view of Java: what it reports about windows.
pub trait HostEvents {
    fn window_created(&mut self, id: i64, title: &str, app_id: &str, pid: i32, flags: i32);
    fn window_changed(&mut self, id: i64, title: &str, app_id: &str, width: i32, height: i32);
    fn window_destroyed(&mut self, id: i64);
    /// The X display Xwayland got (":0"), or "" when it is gone.
    fn x_display(&mut self, display: &str);
}

impl JavaCallbacks {
    pub fn resolve(env: &mut JNIEnv) -> jni::errors::Result<JavaCallbacks> {
        let local = env.find_class(HOST_CLASS)?;
        let strings = "(JLjava/lang/String;Ljava/lang/String;II)V";
        let methods = Methods {
            created: env.get_static_method_id(&local, "onWindowCreated", strings)?,
            changed: env.get_static_method_id(&local, "onWindowChanged", strings)?,
            destroyed: env.get_static_method_id(&local, "onWindowDestroyed", "(J)V")?,
            ended: env.get_static_method_id(&local, "onHostEnded", "(Z)V")?,
            x_display: env.get_static_method_id(&local, "onXDisplay", "(Ljava/lang/String;)V")?,
        };
        let class = env.new_global_ref(&local)?;
        Ok(JavaCallbacks { vm: env.get_java_vm()?, class, methods })
    }

    /// Binds the callbacks to the calling thread (the compositor thread). The second half
    /// reports the thread's own end and outlives everything the compositor owns.
    pub fn attach(self) -> Option<(AttachedCallbacks, HostEnded)> {
        match self.vm.attach_current_thread_permanently() {
            Ok(env) => {
                let raw = env.get_raw();
                // SAFETY: the env belongs to this thread, which stays attached until it exits;
                // both copies are used only on it.
                let (env, ended_env) = unsafe { (JNIEnv::from_raw(raw).ok()?, JNIEnv::from_raw(raw).ok()?) };
                let ended = HostEnded { env: ended_env, class: self.class.clone(), method: self.methods.ended };
                Some((AttachedCallbacks { env, class: self.class, methods: self.methods }, ended))
            }
            Err(e) => {
                lwarn!("cannot attach the compositor thread to the VM: {e}");
                None
            }
        }
    }
}

fn call(env: &mut JNIEnv<'static>, class: &GlobalRef, method: JStaticMethodID, build: impl FnOnce(&mut JNIEnv) -> jni::errors::Result<Vec<jvalue>>) {
    // A Java exception left pending by an earlier failed call must not poison this one.
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
    let class: &JClass = class.as_obj().into();
    let result = env.with_local_frame(8, |env| -> jni::errors::Result<()> {
        let args = build(env)?;
        // SAFETY: the method id was resolved on this class with this signature.
        unsafe {
            env.call_static_method_unchecked(class, method, ReturnType::Primitive(Primitive::Void), &args)?;
        }
        Ok(())
    });
    if let Err(e) = result {
        lwarn!("callback into Java failed: {e}");
    }
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_describe();
        let _ = env.exception_clear();
    }
}

pub struct AttachedCallbacks {
    env: JNIEnv<'static>,
    class: GlobalRef,
    methods: Methods,
}

fn strings_and_ints(env: &mut JNIEnv, id: i64, a: &str, b: &str, x: i32, y: i32) -> jni::errors::Result<Vec<jvalue>> {
    let a = env.new_string(a)?;
    let b = env.new_string(b)?;
    Ok(vec![jvalue { j: id }, jvalue { l: a.into_raw() }, jvalue { l: b.into_raw() }, jvalue { i: x }, jvalue { i: y }])
}

impl HostEvents for AttachedCallbacks {
    fn window_created(&mut self, id: i64, title: &str, app_id: &str, pid: i32, flags: i32) {
        let m = self.methods.created;
        call(&mut self.env, &self.class, m, |env| strings_and_ints(env, id, title, app_id, pid, flags));
    }

    fn window_changed(&mut self, id: i64, title: &str, app_id: &str, width: i32, height: i32) {
        let m = self.methods.changed;
        call(&mut self.env, &self.class, m, |env| strings_and_ints(env, id, title, app_id, width, height));
    }

    fn window_destroyed(&mut self, id: i64) {
        let m = self.methods.destroyed;
        call(&mut self.env, &self.class, m, |_| Ok(vec![jvalue { j: id }]));
    }

    fn x_display(&mut self, display: &str) {
        let m = self.methods.x_display;
        call(&mut self.env, &self.class, m, |env| Ok(vec![jvalue { l: env.new_string(display)?.into_raw() }]));
    }
}

/// Tells Java the compositor thread ended without being asked.
pub struct HostEnded {
    env: JNIEnv<'static>,
    class: GlobalRef,
    method: JStaticMethodID,
}

impl HostEnded {
    pub fn report(mut self, panicked: bool) {
        let flag = if panicked { JNI_TRUE } else { JNI_FALSE };
        call(&mut self.env, &self.class, self.method, |_| Ok(vec![jvalue { z: flag }]));
    }
}
