//! Calls from the compositor thread back into `com.termux.app.wayland.WaylandHost`.
//!
//! The class and method ids are resolved in `nativeStart` on the Java thread: a thread attached
//! from native code finds classes through the system class loader, which cannot see app classes.
//! The compositor thread stays attached for its whole life, so every call runs inside its own
//! local frame; otherwise the local references it creates would never be freed.

use jni::objects::{GlobalRef, JClass, JStaticMethodID};
use jni::signature::{Primitive, ReturnType};
use jni::sys::jvalue;
use jni::{JNIEnv, JavaVM};

use super::log::lwarn;

pub const HOST_CLASS: &str = "com/termux/app/wayland/WaylandHost";

pub struct JavaCallbacks {
    vm: JavaVM,
    class: GlobalRef,
    created: JStaticMethodID,
    title: JStaticMethodID,
    buffer_size: JStaticMethodID,
    destroyed: JStaticMethodID,
}

/// The compositor thread's view of Java: what it reports about toplevels.
pub trait HostEvents {
    fn toplevel_created(&mut self, id: i64, title: &str, app_id: &str);
    fn toplevel_title(&mut self, id: i64, title: &str, app_id: &str);
    fn toplevel_buffer_size(&mut self, id: i64, width: i32, height: i32);
    fn toplevel_destroyed(&mut self, id: i64);
}

impl JavaCallbacks {
    pub fn resolve(env: &mut JNIEnv) -> jni::errors::Result<JavaCallbacks> {
        let local = env.find_class(HOST_CLASS)?;
        let created = env.get_static_method_id(&local, "onToplevelCreated", "(JLjava/lang/String;Ljava/lang/String;)V")?;
        let title = env.get_static_method_id(&local, "onToplevelTitle", "(JLjava/lang/String;Ljava/lang/String;)V")?;
        let buffer_size = env.get_static_method_id(&local, "onToplevelBufferSize", "(JII)V")?;
        let destroyed = env.get_static_method_id(&local, "onToplevelDestroyed", "(J)V")?;
        let class = env.new_global_ref(&local)?;
        Ok(JavaCallbacks { vm: env.get_java_vm()?, class, created, title, buffer_size, destroyed })
    }

    /// Binds the callbacks to the calling thread (the compositor thread).
    pub fn attach(self) -> Option<AttachedCallbacks> {
        match self.vm.attach_current_thread_permanently() {
            Ok(env) => {
                // SAFETY: the env belongs to this thread, which stays attached until it exits.
                let env = unsafe { JNIEnv::from_raw(env.get_raw()).ok()? };
                Some(AttachedCallbacks { env, callbacks: self })
            }
            Err(e) => {
                lwarn!("cannot attach the compositor thread to the VM: {e}");
                None
            }
        }
    }
}

pub struct AttachedCallbacks {
    env: JNIEnv<'static>,
    callbacks: JavaCallbacks,
}

impl AttachedCallbacks {
    fn call(&mut self, method: JStaticMethodID, build: impl FnOnce(&mut JNIEnv) -> jni::errors::Result<Vec<jvalue>>) {
        let class: &JClass = self.callbacks.class.as_obj().into();
        let class = unsafe { JClass::from_raw(class.as_raw()) };
        let result = self.env.with_local_frame(8, |env| -> jni::errors::Result<()> {
            let args = build(env)?;
            unsafe {
                env.call_static_method_unchecked(&class, method, ReturnType::Primitive(Primitive::Void), &args)?;
            }
            Ok(())
        });
        if let Err(e) = result {
            lwarn!("callback into Java failed: {e}");
        }
        if self.env.exception_check().unwrap_or(false) {
            let _ = self.env.exception_describe();
            let _ = self.env.exception_clear();
        }
    }

    fn strings(env: &mut JNIEnv, id: i64, a: &str, b: &str) -> jni::errors::Result<Vec<jvalue>> {
        let a = env.new_string(a)?;
        let b = env.new_string(b)?;
        Ok(vec![jvalue { j: id }, jvalue { l: a.into_raw() }, jvalue { l: b.into_raw() }])
    }
}

impl HostEvents for AttachedCallbacks {
    fn toplevel_created(&mut self, id: i64, title: &str, app_id: &str) {
        let m = self.callbacks.created;
        self.call(m, |env| Self::strings(env, id, title, app_id));
    }

    fn toplevel_title(&mut self, id: i64, title: &str, app_id: &str) {
        let m = self.callbacks.title;
        self.call(m, |env| Self::strings(env, id, title, app_id));
    }

    fn toplevel_buffer_size(&mut self, id: i64, width: i32, height: i32) {
        let m = self.callbacks.buffer_size;
        self.call(m, |_| Ok(vec![jvalue { j: id }, jvalue { i: width }, jvalue { i: height }]));
    }

    fn toplevel_destroyed(&mut self, id: i64) {
        let m = self.callbacks.destroyed;
        self.call(m, |_| Ok(vec![jvalue { j: id }]));
    }
}
