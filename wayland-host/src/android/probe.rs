//! Logs what the stock graphics drivers offer for importing client buffers: one line per fact,
//! under `TLWayland`, prefixed `probe`. A driver that cannot be loaded is a logged result.

use std::ffi::{c_char, c_void, CStr};
use std::sync::atomic::{AtomicBool, Ordering};

use ash::vk;

use super::log::{linfo, lwarn};

const VK_DEVICE_EXTENSIONS: [&CStr; 5] = [
    c"VK_EXT_external_memory_dma_buf",
    c"VK_KHR_external_memory_fd",
    c"VK_EXT_image_drm_format_modifier",
    c"VK_ANDROID_external_memory_android_hardware_buffer",
    c"VK_EXT_queue_family_foreign",
];

const EGL_DISPLAY_EXTENSIONS: [&str; 4] = [
    "EGL_EXT_image_dma_buf_import",
    "EGL_EXT_image_dma_buf_import_modifiers",
    "EGL_ANDROID_get_native_client_buffer",
    "EGL_ANDROID_image_native_buffer",
];

/// One probe at a time.
static RUNNING: AtomicBool = AtomicBool::new(false);

/// Runs both probes on a thread of its own, on request only (the debug interface): a driver
/// that crashes in them is not a panic and takes the process down, and the driver stays mapped
/// afterwards.
pub fn spawn() {
    if RUNNING.swap(true, Ordering::SeqCst) {
        linfo!("probe: already running");
        return;
    }
    let started = std::thread::Builder::new().name("tl-probe".into()).spawn(|| {
        // SAFETY (both): FFI into the system's Vulkan loader and EGL, as their APIs define.
        if std::panic::catch_unwind(|| unsafe { vulkan() }).is_err() {
            lwarn!("probe vulkan: panicked");
        }
        if std::panic::catch_unwind(|| unsafe { egl() }).is_err() {
            lwarn!("probe egl: panicked");
        }
        RUNNING.store(false, Ordering::SeqCst);
    });
    if started.is_err() {
        RUNNING.store(false, Ordering::SeqCst);
    }
}

fn version(v: u32) -> String {
    format!("{}.{}.{}", vk::api_version_major(v), vk::api_version_minor(v), vk::api_version_patch(v))
}

unsafe fn vulkan() {
    let entry = match ash::Entry::load() {
        Ok(e) => e,
        Err(e) => {
            linfo!("probe vulkan: libvulkan.so not loaded: {e}");
            return;
        }
    };
    let loader_version = entry.try_enumerate_instance_version().ok().flatten().unwrap_or(vk::API_VERSION_1_0);
    let api = loader_version.min(vk::API_VERSION_1_3);
    let app = vk::ApplicationInfo::default().api_version(api);
    let info = vk::InstanceCreateInfo::default().application_info(&app);
    let instance = match entry.create_instance(&info, None) {
        Ok(i) => i,
        Err(e) => {
            linfo!("probe vulkan: vkCreateInstance failed: {e:?}");
            return;
        }
    };
    match instance.enumerate_physical_devices() {
        Ok(devices) if !devices.is_empty() => {
            for (n, pd) in devices.into_iter().enumerate() {
                let props = instance.get_physical_device_properties(pd);
                let name = props.device_name_as_c_str().map(|c| c.to_string_lossy().into_owned()).unwrap_or_default();
                let mut driver_name = String::new();
                let mut driver_info = String::new();
                if props.api_version >= vk::API_VERSION_1_2 && api >= vk::API_VERSION_1_2 {
                    let mut driver = vk::PhysicalDeviceDriverProperties::default();
                    let mut props2 = vk::PhysicalDeviceProperties2::default().push_next(&mut driver);
                    instance.get_physical_device_properties2(pd, &mut props2);
                    driver_name = driver.driver_name_as_c_str().map(|c| c.to_string_lossy().into_owned()).unwrap_or_default();
                    driver_info = driver.driver_info_as_c_str().map(|c| c.to_string_lossy().into_owned()).unwrap_or_default();
                }
                linfo!(
                    "probe vulkan device[{n}]: name=\"{name}\" vendor=0x{:04x} device=0x{:x} api={} driver_version=0x{:08x} ({}) driver_name=\"{driver_name}\" driver_info=\"{driver_info}\"",
                    props.vendor_id,
                    props.device_id,
                    version(props.api_version),
                    props.driver_version,
                    version(props.driver_version)
                );
                let exts = instance.enumerate_device_extension_properties(pd).unwrap_or_default();
                for wanted in VK_DEVICE_EXTENSIONS {
                    let has = exts.iter().any(|e| e.extension_name_as_c_str() == Ok(wanted));
                    linfo!("probe vulkan device[{n}]: {}={}", wanted.to_string_lossy(), if has { "yes" } else { "no" });
                }
            }
        }
        Ok(_) => linfo!("probe vulkan: no physical devices"),
        Err(e) => linfo!("probe vulkan: vkEnumeratePhysicalDevices failed: {e:?}"),
    }
    instance.destroy_instance(None);
}

const EGL_VENDOR: i32 = 0x3053;
const EGL_VERSION: i32 = 0x3054;
const EGL_EXTENSIONS: i32 = 0x3055;

type EglGetDisplay = unsafe extern "C" fn(*mut c_void) -> *mut c_void;
type EglInitialize = unsafe extern "C" fn(*mut c_void, *mut i32, *mut i32) -> u32;
type EglQueryString = unsafe extern "C" fn(*mut c_void, i32) -> *const c_char;
type EglTerminate = unsafe extern "C" fn(*mut c_void) -> u32;

unsafe fn sym<T: Copy>(lib: *mut c_void, name: &CStr) -> Option<T> {
    let p = libc::dlsym(lib, name.as_ptr());
    if p.is_null() {
        None
    } else {
        Some(std::mem::transmute_copy::<*mut c_void, T>(&p))
    }
}

unsafe fn query(query_string: EglQueryString, display: *mut c_void, what: i32) -> String {
    let s = query_string(display, what);
    if s.is_null() {
        String::new()
    } else {
        CStr::from_ptr(s).to_string_lossy().into_owned()
    }
}

unsafe fn egl() {
    let lib = libc::dlopen(c"libEGL.so".as_ptr(), libc::RTLD_NOW | libc::RTLD_LOCAL);
    if lib.is_null() {
        linfo!("probe egl: libEGL.so not loaded");
        return;
    }
    let (Some(get_display), Some(initialize), Some(query_string), Some(terminate)) = (
        sym::<EglGetDisplay>(lib, c"eglGetDisplay"),
        sym::<EglInitialize>(lib, c"eglInitialize"),
        sym::<EglQueryString>(lib, c"eglQueryString"),
        sym::<EglTerminate>(lib, c"eglTerminate"),
    ) else {
        linfo!("probe egl: libEGL.so lacks the core entry points");
        return;
    };
    let display = get_display(std::ptr::null_mut());
    if display.is_null() {
        linfo!("probe egl: no default display");
        return;
    }
    let (mut major, mut minor) = (0, 0);
    if initialize(display, &mut major, &mut minor) == 0 {
        linfo!("probe egl: eglInitialize failed");
        return;
    }
    linfo!(
        "probe egl: version={major}.{minor} vendor=\"{}\" version_string=\"{}\"",
        query(query_string, display, EGL_VENDOR),
        query(query_string, display, EGL_VERSION)
    );
    let extensions = query(query_string, display, EGL_EXTENSIONS);
    for wanted in EGL_DISPLAY_EXTENSIONS {
        let has = extensions.split_ascii_whitespace().any(|e| e == wanted);
        linfo!("probe egl: {wanted}={}", if has { "yes" } else { "no" });
    }
    terminate(display);
}
