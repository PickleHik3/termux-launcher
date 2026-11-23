//! Experiment: the bundled Turnip driver, loaded through libadrenotools, and the Vulkan objects
//! of the direct presenter. Only on the compositor thread. Every failure here is a `String` the
//! caller logs before falling back to the copy path; nothing here panics on driver or client
//! input.

use std::collections::HashMap;
use std::ffi::{c_char, c_int, c_void, CStr, CString};
use std::os::fd::AsRawFd;
use std::rc::Rc;
use std::sync::atomic::{AtomicBool, AtomicU8, Ordering};
use std::sync::OnceLock;

use ash::vk;
use smithay::backend::allocator::dmabuf::Dmabuf;
use smithay::backend::allocator::{Buffer, Fourcc};
use smithay::reexports::wayland_server::backend::ObjectId;

use crate::direct::{
    advertised_modifiers, choose_composite_alpha, composite_alpha_names, modifier_usable, swapchain_extent,
    swapchain_image_count, vk_format_for, ModifierCaps,
};
use super::super::log::{linfo, lwarn};
use super::super::source::fourcc_name;
use super::shaders::{OPAQUE_FRAG, OPAQUE_VERT};
use super::{DirectRequest, PresentError};

extern "C" {
    /// libadrenotools (BSD-2-Clause), linked statically by build.rs.
    fn adrenotools_open_libvulkan(
        dlopen_mode: c_int,
        feature_flags: c_int,
        tmp_lib_dir: *const c_char,
        hook_lib_dir: *const c_char,
        custom_driver_dir: *const c_char,
        custom_driver_name: *const c_char,
        file_redirect_dir: *const c_char,
        user_mapping_handle: *mut *mut c_void,
    ) -> *mut c_void;
}

const ADRENOTOOLS_DRIVER_CUSTOM: c_int = 1;
/// The formats direct takes, in the order they are advertised.
const FORMATS: [Fourcc; 4] = [Fourcc::Argb8888, Fourcc::Xrgb8888, Fourcc::Abgr8888, Fourcc::Xbgr8888];
/// How long the host waits for its own blit before giving the window up.
const FENCE_TIMEOUT_NS: u64 = 100_000_000;
/// How long the host waits for a client's implicit fence, when the kernel gives one.
const IMPLICIT_FENCE_TIMEOUT_MS: c_int = 16;

/// The libvulkan handle adrenotools returned: opened once per process and never closed (a
/// driver is not safe to unload), so a later host start reuses it.
static LIBVULKAN: OnceLock<Result<usize, String>> = OnceLock::new();

/// The process's Vulkan instance and device on the bundled driver.
pub struct Ctx {
    instance: ash::Instance,
    surface_fn: ash::khr::surface::Instance,
    android_surface_fn: ash::khr::android_surface::Instance,
    pd: vk::PhysicalDevice,
    device: ash::Device,
    swapchain_fn: ash::khr::swapchain::Device,
    memory_fd_fn: ash::khr::external_memory_fd::Device,
    queue: vk::Queue,
    queue_family: u32,
    /// QUEUE_FAMILY_FOREIGN_EXT when the device has VK_EXT_queue_family_foreign, else EXTERNAL.
    foreign_family: u32,
    /// Per advertised format, the modifiers as the driver reports them.
    pub formats: Vec<(Fourcc, Vec<ModifierCaps>)>,
}

impl Drop for Ctx {
    fn drop(&mut self) {
        // SAFETY: every object made from the device was destroyed before the last Rc went (imports
        // by Direct::drop, presenters by their own Drop), and nothing else uses these handles.
        unsafe {
            let _ = self.device.device_wait_idle();
            self.device.destroy_device(None);
            self.instance.destroy_instance(None);
        }
    }
}

fn yes(b: bool) -> &'static str {
    if b {
        "yes"
    } else {
        "no"
    }
}

fn version(v: u32) -> String {
    format!("{}.{}.{}", vk::api_version_major(v), vk::api_version_minor(v), vk::api_version_patch(v))
}

fn has(exts: &[vk::ExtensionProperties], name: &CStr) -> bool {
    exts.iter().any(|e| e.extension_name_as_c_str() == Ok(name))
}

fn dlerror_text() -> String {
    // SAFETY: dlerror returns null or a thread-local C string valid until the next dl call.
    let e = unsafe { libc::dlerror() };
    if e.is_null() {
        "none".into()
    } else {
        // SAFETY: non-null, NUL-terminated per dlerror's contract.
        unsafe { CStr::from_ptr(e) }.to_string_lossy().into_owned()
    }
}

/// Opens libvulkan through adrenotools with the bundled driver; logs one `direct load` line.
fn open_libvulkan(req: &DirectRequest) -> Result<usize, String> {
    let path = format!("{}{}", req.driver_dir, req.driver_name);
    let size = std::fs::metadata(&path).map(|m| m.len()).map_err(|e| format!("the driver is not at {path}: {e}"))?;
    for hook in ["libhook_impl.so", "libmain_hook.so", "libfile_redirect_hook.so", "libgsl_alloc_hook.so"] {
        let p = std::path::Path::new(&req.hook_dir).join(hook);
        if !p.is_file() {
            return Err(format!("hook {} is missing (native libraries not extracted? legacy packaging)", p.display()));
        }
    }
    let hook = CString::new(req.hook_dir.as_str()).map_err(|_| "hook dir has a NUL")?;
    let dir = CString::new(req.driver_dir.as_str()).map_err(|_| "driver dir has a NUL")?;
    let name = CString::new(req.driver_name.as_str()).map_err(|_| "driver name has a NUL")?;
    // SAFETY: every pointer is a live NUL-terminated string or null as the header allows (tmp dir
    // null on API 29+, no redirect dir, no mapping handle without their flags).
    let lib = unsafe {
        adrenotools_open_libvulkan(
            libc::RTLD_NOW | libc::RTLD_LOCAL,
            ADRENOTOOLS_DRIVER_CUSTOM,
            std::ptr::null(),
            hook.as_ptr(),
            dir.as_ptr(),
            name.as_ptr(),
            std::ptr::null(),
            std::ptr::null_mut(),
        )
    };
    if lib.is_null() {
        return Err(format!(
            "adrenotools_open_libvulkan returned NULL (linker namespace bypass or hook failed; dlerror: {})",
            dlerror_text()
        ));
    }
    linfo!("direct load: ok via adrenotools driver={path} bytes={size} hooks={}", req.hook_dir);
    Ok(lib as usize)
}

/// Loads the driver (once per process) and makes an instance and device on it. Logs a line per
/// fact; Err is why direct is not available.
pub fn load(req: &DirectRequest) -> Result<Ctx, String> {
    let lib = LIBVULKAN.get_or_init(|| open_libvulkan(req)).clone()?;
    // SAFETY: a handle dlopen gave us; the name is NUL-terminated.
    let gipa = unsafe { libc::dlsym(lib as *mut c_void, c"vkGetInstanceProcAddr".as_ptr()) };
    if gipa.is_null() {
        return Err(format!("no vkGetInstanceProcAddr in the loaded libvulkan (dlerror: {})", dlerror_text()));
    }
    // SAFETY: vkGetInstanceProcAddr has exactly this signature.
    let entry = unsafe {
        ash::Entry::from_static_fn(ash::StaticFn {
            get_instance_proc_addr: std::mem::transmute::<*mut c_void, vk::PFN_vkGetInstanceProcAddr>(gipa),
        })
    };
    // SAFETY (whole block): Vulkan calls on objects created here, as the spec orders them.
    unsafe { make_ctx(entry) }
}

unsafe fn make_ctx(entry: ash::Entry) -> Result<Ctx, String> {
    let loader_version = entry.try_enumerate_instance_version().ok().flatten().unwrap_or(vk::API_VERSION_1_0);
    let api = loader_version.min(vk::API_VERSION_1_3);
    if api < vk::API_VERSION_1_1 {
        return Err(format!("the loader offers Vulkan {} only; 1.1 is needed", version(loader_version)));
    }
    let instance_exts = entry.enumerate_instance_extension_properties(None).map_err(|e| format!("instance extensions: {e:?}"))?;
    let has_android_surface = has(&instance_exts, ash::khr::android_surface::NAME);
    let has_surface = has(&instance_exts, ash::khr::surface::NAME);
    linfo!("direct ext: VK_KHR_android_surface={} (instance, loader api {})", yes(has_android_surface), version(loader_version));
    if !has_android_surface || !has_surface {
        return Err("the loader has no VK_KHR_android_surface".into());
    }
    let enabled = [ash::khr::surface::NAME.as_ptr(), ash::khr::android_surface::NAME.as_ptr()];
    let app = vk::ApplicationInfo::default().application_name(c"tlwayland-direct").api_version(api);
    let info = vk::InstanceCreateInfo::default().application_info(&app).enabled_extension_names(&enabled);
    let instance = entry.create_instance(&info, None).map_err(|e| format!("vkCreateInstance: {e:?}"))?;
    match make_device(&entry, &instance, api) {
        Ok(ctx) => Ok(ctx),
        Err(e) => {
            instance.destroy_instance(None);
            Err(e)
        }
    }
}

unsafe fn make_device(entry: &ash::Entry, instance: &ash::Instance, api: u32) -> Result<Ctx, String> {
    let devices = instance.enumerate_physical_devices().map_err(|e| format!("vkEnumeratePhysicalDevices: {e:?}"))?;
    let Some(&pd) = devices.first() else {
        return Err("no physical devices (the driver does not speak this kernel's KGSL, or the hook loaded nothing)".into());
    };
    let props = instance.get_physical_device_properties(pd);
    let name = props.device_name_as_c_str().map(|c| c.to_string_lossy().into_owned()).unwrap_or_default();
    linfo!("direct device: name=\"{name}\" vendor=0x{:04x} device=0x{:x} api={}", props.vendor_id, props.device_id, version(props.api_version));
    let mut driver = vk::PhysicalDeviceDriverProperties::default();
    {
        let mut props2 = vk::PhysicalDeviceProperties2::default().push_next(&mut driver);
        instance.get_physical_device_properties2(pd, &mut props2);
    }
    let driver_name = driver.driver_name_as_c_str().map(|c| c.to_string_lossy().into_owned()).unwrap_or_default();
    let driver_info = driver.driver_info_as_c_str().map(|c| c.to_string_lossy().into_owned()).unwrap_or_default();
    linfo!(
        "direct driver: version=0x{:08x} ({}) id={:?} name=\"{driver_name}\" info=\"{driver_info}\"{}",
        props.driver_version,
        version(props.driver_version),
        driver.driver_id,
        if driver.driver_id == vk::DriverId::MESA_TURNIP { "" } else { " (not Turnip: the adrenotools hook did not take)" }
    );
    let exts = instance.enumerate_device_extension_properties(pd).map_err(|e| format!("device extensions: {e:?}"))?;
    let dma_buf = has(&exts, ash::ext::external_memory_dma_buf::NAME);
    let modifier = has(&exts, ash::ext::image_drm_format_modifier::NAME);
    let memory_fd = has(&exts, ash::khr::external_memory_fd::NAME);
    let ahb = has(&exts, ash::android::external_memory_android_hardware_buffer::NAME);
    let swapchain = has(&exts, ash::khr::swapchain::NAME);
    let foreign = has(&exts, ash::ext::queue_family_foreign::NAME);
    linfo!("direct ext: VK_EXT_external_memory_dma_buf={}", yes(dma_buf));
    linfo!("direct ext: VK_EXT_image_drm_format_modifier={}", yes(modifier));
    linfo!("direct ext: VK_KHR_external_memory_fd={}", yes(memory_fd));
    linfo!("direct ext: VK_ANDROID_external_memory_android_hardware_buffer={}", yes(ahb));
    linfo!("direct ext: VK_KHR_swapchain={}", yes(swapchain));
    linfo!("direct ext: VK_EXT_queue_family_foreign={}", yes(foreign));
    let missing: Vec<&str> = [
        (dma_buf, "VK_EXT_external_memory_dma_buf"),
        (modifier, "VK_EXT_image_drm_format_modifier"),
        (memory_fd, "VK_KHR_external_memory_fd"),
        (swapchain, "VK_KHR_swapchain (the platform loader adds it only over VK_ANDROID_native_buffer, i.e. a working gralloc)"),
    ]
    .iter()
    .filter(|(ok, _)| !ok)
    .map(|(_, n)| *n)
    .collect();
    if !missing.is_empty() {
        return Err(format!("missing {}", missing.join(", ")));
    }
    let families = instance.get_physical_device_queue_family_properties(pd);
    let Some(queue_family) = families.iter().position(|f| f.queue_flags.contains(vk::QueueFlags::GRAPHICS)) else {
        return Err("no graphics queue family".into());
    };
    let queue_family = queue_family as u32;
    let mut enabled = vec![
        ash::khr::swapchain::NAME.as_ptr(),
        ash::khr::external_memory_fd::NAME.as_ptr(),
        ash::ext::external_memory_dma_buf::NAME.as_ptr(),
        ash::ext::image_drm_format_modifier::NAME.as_ptr(),
    ];
    if foreign {
        enabled.push(ash::ext::queue_family_foreign::NAME.as_ptr());
    }
    // The modifier extension needs VK_KHR_image_format_list, core from 1.2.
    if props.api_version.min(api) < vk::API_VERSION_1_2 {
        if !has(&exts, ash::khr::image_format_list::NAME) {
            return Err("missing VK_KHR_image_format_list".into());
        }
        enabled.push(ash::khr::image_format_list::NAME.as_ptr());
    }
    let priorities = [1.0f32];
    let queues = [vk::DeviceQueueCreateInfo::default().queue_family_index(queue_family).queue_priorities(&priorities)];
    let info = vk::DeviceCreateInfo::default().queue_create_infos(&queues).enabled_extension_names(&enabled);
    let device = instance.create_device(pd, &info, None).map_err(|e| format!("vkCreateDevice: {e:?}"))?;
    let queue = device.get_device_queue(queue_family, 0);
    let formats = FORMATS.iter().map(|&f| (f, modifier_table(instance, pd, f))).collect::<Vec<_>>();
    for (f, caps) in &formats {
        let mut line = String::new();
        for c in caps {
            line.push_str(&format!(
                " 0x{:x}[planes={} blit={} import={}]",
                c.modifier,
                c.plane_count,
                yes(c.blit_src),
                yes(c.importable)
            ));
        }
        let adv: Vec<String> = advertised_modifiers(caps).iter().map(|m| format!("0x{m:x}")).collect();
        linfo!("direct modifiers {}:{} -> advertised {}", fourcc_name(*f as u32), if line.is_empty() { " none".into() } else { line }, adv.join(","));
    }
    Ok(Ctx {
        instance: instance.clone(),
        surface_fn: ash::khr::surface::Instance::new(entry, instance),
        android_surface_fn: ash::khr::android_surface::Instance::new(entry, instance),
        pd,
        swapchain_fn: ash::khr::swapchain::Device::new(instance, &device),
        memory_fd_fn: ash::khr::external_memory_fd::Device::new(instance, &device),
        device,
        queue,
        queue_family,
        foreign_family: if foreign { vk::QUEUE_FAMILY_FOREIGN_EXT } else { vk::QUEUE_FAMILY_EXTERNAL },
        formats,
    })
}

/// The driver's modifiers for one format, with whether each can be imported from a dmabuf and
/// blitted from.
unsafe fn modifier_table(instance: &ash::Instance, pd: vk::PhysicalDevice, fourcc: Fourcc) -> Vec<ModifierCaps> {
    let Some((raw, _)) = vk_format_for(fourcc as u32) else { return Vec::new() };
    let format = vk::Format::from_raw(raw);
    let count = {
        let mut list = vk::DrmFormatModifierPropertiesListEXT::default();
        let mut props = vk::FormatProperties2::default().push_next(&mut list);
        instance.get_physical_device_format_properties2(pd, format, &mut props);
        list.drm_format_modifier_count as usize
    };
    if count == 0 {
        return Vec::new();
    }
    let mut entries = vec![vk::DrmFormatModifierPropertiesEXT::default(); count];
    {
        let mut list = vk::DrmFormatModifierPropertiesListEXT::default().drm_format_modifier_properties(&mut entries);
        let mut props = vk::FormatProperties2::default().push_next(&mut list);
        instance.get_physical_device_format_properties2(pd, format, &mut props);
    }
    entries
        .iter()
        .map(|e| {
            let mut drm = vk::PhysicalDeviceImageDrmFormatModifierInfoEXT::default()
                .drm_format_modifier(e.drm_format_modifier)
                .sharing_mode(vk::SharingMode::EXCLUSIVE);
            let mut external =
                vk::PhysicalDeviceExternalImageFormatInfo::default().handle_type(vk::ExternalMemoryHandleTypeFlags::DMA_BUF_EXT);
            let info = vk::PhysicalDeviceImageFormatInfo2::default()
                .format(format)
                .ty(vk::ImageType::TYPE_2D)
                .tiling(vk::ImageTiling::DRM_FORMAT_MODIFIER_EXT)
                .usage(vk::ImageUsageFlags::TRANSFER_SRC)
                .push_next(&mut external)
                .push_next(&mut drm);
            let mut external_props = vk::ExternalImageFormatProperties::default();
            let ok = {
                let mut props = vk::ImageFormatProperties2::default().push_next(&mut external_props);
                instance.get_physical_device_image_format_properties2(pd, &info, &mut props).is_ok()
            };
            let importable = ok
                && external_props
                    .external_memory_properties
                    .external_memory_features
                    .contains(vk::ExternalMemoryFeatureFlags::IMPORTABLE);
            ModifierCaps {
                modifier: e.drm_format_modifier,
                plane_count: e.drm_format_modifier_plane_count,
                blit_src: e.drm_format_modifier_tiling_features.contains(vk::FormatFeatureFlags::BLIT_SRC),
                importable,
            }
        })
        .collect()
}

impl Ctx {
    pub fn caps_for(&self, fourcc: u32) -> Option<&[ModifierCaps]> {
        self.formats.iter().find(|(f, _)| *f as u32 == fourcc).map(|(_, c)| c.as_slice())
    }

    /// Whether direct can import this dmabuf: an advertised format, one plane, a modifier the
    /// driver reported usable, a positive size.
    pub fn can_import(&self, dmabuf: &Dmabuf) -> bool {
        let format = dmabuf.format();
        let size = dmabuf.size();
        dmabuf.num_planes() == 1
            && size.w > 0
            && size.h > 0
            && self.caps_for(format.code as u32).is_some_and(|caps| modifier_usable(caps, format.modifier.into()))
    }
}

/// One client dmabuf as a VkImage.
pub struct Imported {
    image: vk::Image,
    memory: vk::DeviceMemory,
    /// X format: alpha is padding and is forced to 1 after the blit.
    pub opaque: bool,
}

impl Imported {
    /// # Safety
    /// No submitted work may still use the image.
    pub unsafe fn destroy(&self, device: &ash::Device) {
        device.destroy_image(self.image, None);
        device.free_memory(self.memory, None);
    }
}

/// Imports `dmabuf` (already checked with `can_import`) as a transfer-source image.
pub fn import(ctx: &Ctx, dmabuf: &Dmabuf) -> Result<Imported, String> {
    let format = dmabuf.format();
    let fourcc = format.code as u32;
    let modifier: u64 = format.modifier.into();
    let (raw, opaque) = vk_format_for(fourcc).ok_or("format")?;
    let size = dmabuf.size();
    let (Some(offset), Some(stride), Some(fd)) = (dmabuf.offsets().next(), dmabuf.strides().next(), dmabuf.handles().next()) else {
        return Err("no plane".into());
    };
    let layouts = [vk::SubresourceLayout { offset: offset as u64, size: 0, row_pitch: stride as u64, array_pitch: 0, depth_pitch: 0 }];
    let mut explicit = vk::ImageDrmFormatModifierExplicitCreateInfoEXT::default().drm_format_modifier(modifier).plane_layouts(&layouts);
    let mut external = vk::ExternalMemoryImageCreateInfo::default().handle_types(vk::ExternalMemoryHandleTypeFlags::DMA_BUF_EXT);
    let info = vk::ImageCreateInfo::default()
        .image_type(vk::ImageType::TYPE_2D)
        .format(vk::Format::from_raw(raw))
        .extent(vk::Extent3D { width: size.w as u32, height: size.h as u32, depth: 1 })
        .mip_levels(1)
        .array_layers(1)
        .samples(vk::SampleCountFlags::TYPE_1)
        .tiling(vk::ImageTiling::DRM_FORMAT_MODIFIER_EXT)
        .usage(vk::ImageUsageFlags::TRANSFER_SRC)
        .sharing_mode(vk::SharingMode::EXCLUSIVE)
        .initial_layout(vk::ImageLayout::UNDEFINED)
        .push_next(&mut external)
        .push_next(&mut explicit);
    let device = &ctx.device;
    // SAFETY: a valid create info whose chained structs outlive the call.
    let image = unsafe { device.create_image(&info, None) }.map_err(|e| format!("vkCreateImage: {e:?}"))?;
    // SAFETY: the image was just created on this device.
    let reqs = unsafe { device.get_image_memory_requirements(image) };
    // The import takes ownership of the fd on success, so it gets its own copy.
    // SAFETY: duplicating an fd the dmabuf holds open.
    let dup = unsafe { libc::fcntl(fd.as_raw_fd(), libc::F_DUPFD_CLOEXEC, 0) };
    let fail = |why: String, close_fd: bool| {
        // SAFETY: the image is ours and unused; the fd, when still ours, is closed once.
        unsafe {
            device.destroy_image(image, None);
            if close_fd && dup >= 0 {
                libc::close(dup);
            }
        }
        Err(why)
    };
    if dup < 0 {
        return fail(format!("dup: {}", std::io::Error::last_os_error()), false);
    }
    let mut fd_props = vk::MemoryFdPropertiesKHR::default();
    // SAFETY: a valid dma-buf fd and out struct.
    if let Err(e) = unsafe { ctx.memory_fd_fn.get_memory_fd_properties(vk::ExternalMemoryHandleTypeFlags::DMA_BUF_EXT, dup, &mut fd_props) } {
        return fail(format!("vkGetMemoryFdPropertiesKHR: {e:?}"), true);
    }
    let bits = reqs.memory_type_bits & fd_props.memory_type_bits;
    if bits == 0 {
        return fail(
            format!("no memory type fits (image 0x{:x}, fd 0x{:x})", reqs.memory_type_bits, fd_props.memory_type_bits),
            true,
        );
    }
    let mut import = vk::ImportMemoryFdInfoKHR::default().handle_type(vk::ExternalMemoryHandleTypeFlags::DMA_BUF_EXT).fd(dup);
    let mut dedicated = vk::MemoryDedicatedAllocateInfo::default().image(image);
    let alloc = vk::MemoryAllocateInfo::default()
        .allocation_size(reqs.size)
        .memory_type_index(bits.trailing_zeros())
        .push_next(&mut import)
        .push_next(&mut dedicated);
    // SAFETY: a valid allocate info; on success the driver owns `dup`.
    let memory = match unsafe { device.allocate_memory(&alloc, None) } {
        Ok(m) => m,
        Err(e) => return fail(format!("vkAllocateMemory (dma-buf import, {} bytes): {e:?}", reqs.size), true),
    };
    // SAFETY: fresh image and memory of this device.
    if let Err(e) = unsafe { device.bind_image_memory(image, memory, 0) } {
        // SAFETY: the memory is ours and unused.
        unsafe { device.free_memory(memory, None) };
        return fail(format!("vkBindImageMemory: {e:?}"), false);
    }
    Ok(Imported { image, memory, opaque })
}

/// Imports per wl_buffer; `None` marks a failed one, not tried again.
#[derive(Default)]
pub struct Imports {
    map: HashMap<ObjectId, Option<Imported>>,
}

impl Imports {
    pub fn get_or_import(&mut self, ctx: &Ctx, id: ObjectId, dmabuf: &Dmabuf) -> Result<&Imported, String> {
        let slot = self.map.entry(id).or_insert_with(|| match import(ctx, dmabuf) {
            Ok(i) => Some(i),
            Err(e) => {
                lwarn!("direct import {}x{} {} 0x{:x}: {e}", dmabuf.size().w, dmabuf.size().h, fourcc_name(dmabuf.format().code as u32), u64::from(dmabuf.format().modifier));
                None
            }
        });
        slot.as_ref().ok_or_else(|| "this buffer could not be imported".to_string())
    }

    /// # Safety
    /// No submitted work may still use the buffer's image (every present waits for its fence).
    pub unsafe fn forget(&mut self, device: &ash::Device, id: &ObjectId) {
        if let Some(Some(i)) = self.map.remove(id) {
            i.destroy(device);
        }
    }

    /// # Safety
    /// As `forget`, for every buffer.
    pub unsafe fn clear(&mut self, device: &ash::Device) {
        for (_, i) in self.map.drain() {
            if let Some(i) = i {
                i.destroy(device);
            }
        }
    }
}

impl Ctx {
    pub fn device(&self) -> &ash::Device {
        &self.device
    }
}

/// The opaque pass of one swapchain: render pass and pipeline that write alpha = 1, and a
/// framebuffer per swapchain image.
struct OpaquePass {
    render_pass: vk::RenderPass,
    layout: vk::PipelineLayout,
    pipeline: vk::Pipeline,
    views: Vec<vk::ImageView>,
    framebuffers: Vec<vk::Framebuffer>,
}

impl OpaquePass {
    unsafe fn new(device: &ash::Device, format: vk::Format, images: &[vk::Image], extent: vk::Extent2D) -> Result<OpaquePass, String> {
        let mut pass = OpaquePass {
            render_pass: vk::RenderPass::null(),
            layout: vk::PipelineLayout::null(),
            pipeline: vk::Pipeline::null(),
            views: Vec::new(),
            framebuffers: Vec::new(),
        };
        match pass.build(device, format, images, extent) {
            Ok(()) => Ok(pass),
            Err(e) => {
                pass.destroy(device);
                Err(e)
            }
        }
    }

    unsafe fn build(&mut self, device: &ash::Device, format: vk::Format, images: &[vk::Image], extent: vk::Extent2D) -> Result<(), String> {
        let attachment = [vk::AttachmentDescription::default()
            .format(format)
            .samples(vk::SampleCountFlags::TYPE_1)
            .load_op(vk::AttachmentLoadOp::LOAD)
            .store_op(vk::AttachmentStoreOp::STORE)
            .stencil_load_op(vk::AttachmentLoadOp::DONT_CARE)
            .stencil_store_op(vk::AttachmentStoreOp::DONT_CARE)
            .initial_layout(vk::ImageLayout::TRANSFER_DST_OPTIMAL)
            .final_layout(vk::ImageLayout::PRESENT_SRC_KHR)];
        let color = [vk::AttachmentReference { attachment: 0, layout: vk::ImageLayout::COLOR_ATTACHMENT_OPTIMAL }];
        let subpass = [vk::SubpassDescription::default().pipeline_bind_point(vk::PipelineBindPoint::GRAPHICS).color_attachments(&color)];
        let deps = [
            vk::SubpassDependency {
                src_subpass: vk::SUBPASS_EXTERNAL,
                dst_subpass: 0,
                src_stage_mask: vk::PipelineStageFlags::TRANSFER,
                dst_stage_mask: vk::PipelineStageFlags::COLOR_ATTACHMENT_OUTPUT,
                src_access_mask: vk::AccessFlags::TRANSFER_WRITE,
                dst_access_mask: vk::AccessFlags::COLOR_ATTACHMENT_READ | vk::AccessFlags::COLOR_ATTACHMENT_WRITE,
                dependency_flags: vk::DependencyFlags::empty(),
            },
            vk::SubpassDependency {
                src_subpass: 0,
                dst_subpass: vk::SUBPASS_EXTERNAL,
                src_stage_mask: vk::PipelineStageFlags::COLOR_ATTACHMENT_OUTPUT,
                dst_stage_mask: vk::PipelineStageFlags::BOTTOM_OF_PIPE,
                src_access_mask: vk::AccessFlags::COLOR_ATTACHMENT_WRITE,
                dst_access_mask: vk::AccessFlags::empty(),
                dependency_flags: vk::DependencyFlags::empty(),
            },
        ];
        let info = vk::RenderPassCreateInfo::default().attachments(&attachment).subpasses(&subpass).dependencies(&deps);
        self.render_pass = device.create_render_pass(&info, None).map_err(|e| format!("vkCreateRenderPass: {e:?}"))?;
        self.layout = device
            .create_pipeline_layout(&vk::PipelineLayoutCreateInfo::default(), None)
            .map_err(|e| format!("vkCreatePipelineLayout: {e:?}"))?;
        let vert = device
            .create_shader_module(&vk::ShaderModuleCreateInfo::default().code(&OPAQUE_VERT), None)
            .map_err(|e| format!("vertex shader: {e:?}"))?;
        let frag = match device.create_shader_module(&vk::ShaderModuleCreateInfo::default().code(&OPAQUE_FRAG), None) {
            Ok(f) => f,
            Err(e) => {
                device.destroy_shader_module(vert, None);
                return Err(format!("fragment shader: {e:?}"));
            }
        };
        let stages = [
            vk::PipelineShaderStageCreateInfo::default().stage(vk::ShaderStageFlags::VERTEX).module(vert).name(c"main"),
            vk::PipelineShaderStageCreateInfo::default().stage(vk::ShaderStageFlags::FRAGMENT).module(frag).name(c"main"),
        ];
        let vertex_input = vk::PipelineVertexInputStateCreateInfo::default();
        let assembly = vk::PipelineInputAssemblyStateCreateInfo::default().topology(vk::PrimitiveTopology::TRIANGLE_LIST);
        let viewport = vk::PipelineViewportStateCreateInfo::default().viewport_count(1).scissor_count(1);
        let raster = vk::PipelineRasterizationStateCreateInfo::default()
            .polygon_mode(vk::PolygonMode::FILL)
            .cull_mode(vk::CullModeFlags::NONE)
            .front_face(vk::FrontFace::COUNTER_CLOCKWISE)
            .line_width(1.0);
        let multisample = vk::PipelineMultisampleStateCreateInfo::default().rasterization_samples(vk::SampleCountFlags::TYPE_1);
        let blend_attachment = [vk::PipelineColorBlendAttachmentState::default().color_write_mask(vk::ColorComponentFlags::A)];
        let blend = vk::PipelineColorBlendStateCreateInfo::default().attachments(&blend_attachment);
        let dynamic_states = [vk::DynamicState::VIEWPORT, vk::DynamicState::SCISSOR];
        let dynamic = vk::PipelineDynamicStateCreateInfo::default().dynamic_states(&dynamic_states);
        let pipeline_info = [vk::GraphicsPipelineCreateInfo::default()
            .stages(&stages)
            .vertex_input_state(&vertex_input)
            .input_assembly_state(&assembly)
            .viewport_state(&viewport)
            .rasterization_state(&raster)
            .multisample_state(&multisample)
            .color_blend_state(&blend)
            .dynamic_state(&dynamic)
            .layout(self.layout)
            .render_pass(self.render_pass)
            .subpass(0)];
        let pipelines = device.create_graphics_pipelines(vk::PipelineCache::null(), &pipeline_info, None);
        device.destroy_shader_module(vert, None);
        device.destroy_shader_module(frag, None);
        self.pipeline = match pipelines {
            Ok(p) => p.first().copied().ok_or("no pipeline")?,
            Err((_, e)) => return Err(format!("vkCreateGraphicsPipelines: {e:?}")),
        };
        for &image in images {
            let view_info = vk::ImageViewCreateInfo::default()
                .image(image)
                .view_type(vk::ImageViewType::TYPE_2D)
                .format(format)
                .subresource_range(color_range());
            let view = device.create_image_view(&view_info, None).map_err(|e| format!("vkCreateImageView: {e:?}"))?;
            self.views.push(view);
            let attachments = [view];
            let fb_info = vk::FramebufferCreateInfo::default()
                .render_pass(self.render_pass)
                .attachments(&attachments)
                .width(extent.width)
                .height(extent.height)
                .layers(1);
            let fb = device.create_framebuffer(&fb_info, None).map_err(|e| format!("vkCreateFramebuffer: {e:?}"))?;
            self.framebuffers.push(fb);
        }
        Ok(())
    }

    unsafe fn destroy(&mut self, device: &ash::Device) {
        for fb in self.framebuffers.drain(..) {
            device.destroy_framebuffer(fb, None);
        }
        for v in self.views.drain(..) {
            device.destroy_image_view(v, None);
        }
        if self.pipeline != vk::Pipeline::null() {
            device.destroy_pipeline(self.pipeline, None);
        }
        if self.layout != vk::PipelineLayout::null() {
            device.destroy_pipeline_layout(self.layout, None);
        }
        if self.render_pass != vk::RenderPass::null() {
            device.destroy_render_pass(self.render_pass, None);
        }
    }
}

fn color_range() -> vk::ImageSubresourceRange {
    vk::ImageSubresourceRange { aspect_mask: vk::ImageAspectFlags::COLOR, base_mip_level: 0, level_count: 1, base_array_layer: 0, layer_count: 1 }
}

/// One toplevel's swapchain on its Surface, with the objects one frame needs. Every present waits
/// for its own fence, so nothing is in flight between ticks.
pub struct WindowPresenter {
    ctx: Rc<Ctx>,
    surface: vk::SurfaceKHR,
    swapchain: vk::SwapchainKHR,
    images: Vec<vk::Image>,
    format: vk::Format,
    extent: vk::Extent2D,
    /// The size asked for (before the surface's limits), to tell when to recreate.
    wanted: (u32, u32),
    pool: vk::CommandPool,
    cmd: vk::CommandBuffer,
    fence: vk::Fence,
    acquire: vk::Semaphore,
    /// One per swapchain image: a present may still wait on the previous one's.
    done: Vec<vk::Semaphore>,
    opaque: Option<OpaquePass>,
    /// The last present said the swapchain no longer matches; recreate before the next frame.
    stale: bool,
}

impl WindowPresenter {
    /// A Vulkan surface on `window` (an ANativeWindow) and a swapchain of `size`.
    ///
    /// # Safety
    /// `window` must be a live ANativeWindow that outlives the presenter, with no other producer
    /// connected.
    pub unsafe fn new(ctx: &Rc<Ctx>, window: *mut c_void, size: (u32, u32), id: i64) -> Result<WindowPresenter, String> {
        let info = vk::AndroidSurfaceCreateInfoKHR::default().window(window);
        let surface = ctx
            .android_surface_fn
            .create_android_surface(&info, None)
            .map_err(|e| format!("vkCreateAndroidSurfaceKHR: {e:?} (another producer connected to the Surface?)"))?;
        let mut p = WindowPresenter {
            ctx: ctx.clone(),
            surface,
            swapchain: vk::SwapchainKHR::null(),
            images: Vec::new(),
            format: vk::Format::UNDEFINED,
            extent: vk::Extent2D::default(),
            wanted: (0, 0),
            pool: vk::CommandPool::null(),
            cmd: vk::CommandBuffer::null(),
            fence: vk::Fence::null(),
            acquire: vk::Semaphore::null(),
            done: Vec::new(),
            opaque: None,
            stale: false,
        };
        let device = &ctx.device;
        let pool_info = vk::CommandPoolCreateInfo::default()
            .queue_family_index(ctx.queue_family)
            .flags(vk::CommandPoolCreateFlags::RESET_COMMAND_BUFFER);
        p.pool = device.create_command_pool(&pool_info, None).map_err(|e| format!("vkCreateCommandPool: {e:?}"))?;
        let alloc = vk::CommandBufferAllocateInfo::default().command_pool(p.pool).level(vk::CommandBufferLevel::PRIMARY).command_buffer_count(1);
        p.cmd = device
            .allocate_command_buffers(&alloc)
            .map_err(|e| format!("vkAllocateCommandBuffers: {e:?}"))?
            .first()
            .copied()
            .ok_or("no command buffer")?;
        p.fence = device.create_fence(&vk::FenceCreateInfo::default(), None).map_err(|e| format!("vkCreateFence: {e:?}"))?;
        p.acquire = device.create_semaphore(&vk::SemaphoreCreateInfo::default(), None).map_err(|e| format!("vkCreateSemaphore: {e:?}"))?;
        let supported = ctx.surface_fn.get_physical_device_surface_support(ctx.pd, ctx.queue_family, surface).unwrap_or(false);
        if !supported {
            return Err("the queue cannot present to this surface".into());
        }
        p.create_swapchain(size, id)?;
        Ok(p)
    }

    unsafe fn create_swapchain(&mut self, size: (u32, u32), id: i64) -> Result<(), String> {
        let ctx = self.ctx.clone();
        let ctx = &*ctx;
        let caps = ctx
            .surface_fn
            .get_physical_device_surface_capabilities(ctx.pd, self.surface)
            .map_err(|e| format!("surface capabilities: {e:?}"))?;
        let formats = ctx
            .surface_fn
            .get_physical_device_surface_formats(ctx.pd, self.surface)
            .map_err(|e| format!("surface formats: {e:?}"))?;
        let Some(chosen) = [vk::Format::R8G8B8A8_UNORM, vk::Format::B8G8R8A8_UNORM]
            .into_iter()
            .find_map(|want| formats.iter().find(|f| f.format == want).copied())
        else {
            let offered: Vec<String> = formats.iter().map(|f| format!("{:?}", f.format)).collect();
            return Err(format!("no 8-bit RGBA surface format; offered {}", offered.join(",")));
        };
        let supported_alpha = caps.supported_composite_alpha.as_raw();
        let Some((alpha, keeps_alpha)) = choose_composite_alpha(supported_alpha) else {
            return Err("the surface offers no composite alpha mode".into());
        };
        if !caps.supported_usage_flags.contains(vk::ImageUsageFlags::TRANSFER_DST) {
            return Err(format!("swapchain images cannot be blit targets (usage {:?})", caps.supported_usage_flags));
        }
        let features = ctx.instance.get_physical_device_format_properties(ctx.pd, chosen.format).optimal_tiling_features;
        if !features.contains(vk::FormatFeatureFlags::BLIT_DST) {
            return Err(format!("{:?} cannot be a blit target", chosen.format));
        }
        let (w, h) = swapchain_extent(
            size,
            (caps.min_image_extent.width, caps.min_image_extent.height),
            (caps.max_image_extent.width, caps.max_image_extent.height),
        );
        let count = swapchain_image_count(caps.min_image_count, caps.max_image_count);
        let transform = if caps.supported_transforms.contains(vk::SurfaceTransformFlagsKHR::IDENTITY) {
            vk::SurfaceTransformFlagsKHR::IDENTITY
        } else {
            caps.current_transform
        };
        let old = self.swapchain;
        let info = vk::SwapchainCreateInfoKHR::default()
            .surface(self.surface)
            .min_image_count(count)
            .image_format(chosen.format)
            .image_color_space(chosen.color_space)
            .image_extent(vk::Extent2D { width: w, height: h })
            .image_array_layers(1)
            .image_usage(vk::ImageUsageFlags::TRANSFER_DST | vk::ImageUsageFlags::COLOR_ATTACHMENT)
            .image_sharing_mode(vk::SharingMode::EXCLUSIVE)
            .pre_transform(transform)
            .composite_alpha(vk::CompositeAlphaFlagsKHR::from_raw(alpha))
            .present_mode(vk::PresentModeKHR::FIFO)
            .clipped(true)
            .old_swapchain(old);
        let swapchain = ctx.swapchain_fn.create_swapchain(&info, None);
        // The old one is retired either way; nothing of it is in flight.
        self.drop_swapchain_parts();
        if old != vk::SwapchainKHR::null() {
            ctx.swapchain_fn.destroy_swapchain(old, None);
        }
        self.swapchain = vk::SwapchainKHR::null();
        let swapchain = swapchain.map_err(|e| format!("vkCreateSwapchainKHR {w}x{h}: {e:?}"))?;
        self.swapchain = swapchain;
        self.images = ctx.swapchain_fn.get_swapchain_images(swapchain).map_err(|e| format!("swapchain images: {e:?}"))?;
        for _ in 0..self.images.len() {
            let s = ctx.device.create_semaphore(&vk::SemaphoreCreateInfo::default(), None).map_err(|e| format!("vkCreateSemaphore: {e:?}"))?;
            self.done.push(s);
        }
        self.format = chosen.format;
        self.extent = vk::Extent2D { width: w, height: h };
        self.wanted = size;
        self.stale = false;
        linfo!(
            "toplevel {id}: direct swapchain {w}x{h} format={:?} images={} present=FIFO transform={:?} composite_alpha supported={} chose={}",
            chosen.format,
            self.images.len(),
            transform,
            composite_alpha_names(supported_alpha),
            composite_alpha_names(alpha)
        );
        if !keeps_alpha {
            lwarn!("toplevel {id}: direct: the surface offers only OPAQUE composite alpha; per-pixel alpha is not promised by the WSI");
        }
        Ok(())
    }

    /// The objects tied to the current swapchain's images.
    unsafe fn drop_swapchain_parts(&mut self) {
        let device = &self.ctx.device;
        if let Some(mut pass) = self.opaque.take() {
            pass.destroy(device);
        }
        for s in self.done.drain(..) {
            device.destroy_semaphore(s, None);
        }
        self.images.clear();
    }

    /// Blits `src` (the shown rectangle `[x0,y0]-[x1,y1]`) into the next swapchain image and
    /// presents it, then waits for the GPU so the client's buffer can be released at once.
    ///
    /// # Safety
    /// `src` must be an image of this presenter's device that no other work uses.
    pub unsafe fn present(&mut self, src: &Imported, from: [i32; 2], to: [i32; 2], size: (u32, u32), id: i64) -> Result<(), PresentError> {
        if self.stale || size != self.wanted {
            self.create_swapchain(size, id).map_err(PresentError::FallBack)?;
        }
        let ctx = self.ctx.clone();
        let device = &ctx.device;
        let index = match ctx.swapchain_fn.acquire_next_image(self.swapchain, u64::MAX, self.acquire, vk::Fence::null()) {
            Ok((i, _suboptimal)) => i,
            Err(vk::Result::ERROR_OUT_OF_DATE_KHR) => {
                self.create_swapchain(size, id).map_err(PresentError::FallBack)?;
                match ctx.swapchain_fn.acquire_next_image(self.swapchain, u64::MAX, self.acquire, vk::Fence::null()) {
                    Ok((i, _)) => i,
                    Err(e) => return Err(PresentError::FallBack(format!("vkAcquireNextImageKHR after recreate: {e:?}"))),
                }
            }
            Err(vk::Result::ERROR_SURFACE_LOST_KHR) => return Err(PresentError::Lost("surface lost on acquire".into())),
            Err(e) => return Err(PresentError::FallBack(format!("vkAcquireNextImageKHR: {e:?}"))),
        };
        let i = index as usize;
        let (Some(&dst), Some(&done)) = (self.images.get(i), self.done.get(i)) else {
            return Err(PresentError::FallBack(format!("acquire gave image {index} of {}", self.images.len())));
        };
        if src.opaque && self.opaque.is_none() {
            self.opaque = Some(OpaquePass::new(device, self.format, &self.images, self.extent).map_err(PresentError::FallBack)?);
        }
        let record = || -> Result<(), vk::Result> {
            device.reset_command_buffer(self.cmd, vk::CommandBufferResetFlags::empty())?;
            device.begin_command_buffer(self.cmd, &vk::CommandBufferBeginInfo::default().flags(vk::CommandBufferUsageFlags::ONE_TIME_SUBMIT))?;
            let take = [
                // The client's image: acquired from the foreign queue that rendered it, content kept.
                vk::ImageMemoryBarrier::default()
                    .src_access_mask(vk::AccessFlags::empty())
                    .dst_access_mask(vk::AccessFlags::TRANSFER_READ)
                    .old_layout(vk::ImageLayout::GENERAL)
                    .new_layout(vk::ImageLayout::TRANSFER_SRC_OPTIMAL)
                    .src_queue_family_index(ctx.foreign_family)
                    .dst_queue_family_index(ctx.queue_family)
                    .image(src.image)
                    .subresource_range(color_range()),
                vk::ImageMemoryBarrier::default()
                    .src_access_mask(vk::AccessFlags::empty())
                    .dst_access_mask(vk::AccessFlags::TRANSFER_WRITE)
                    .old_layout(vk::ImageLayout::UNDEFINED)
                    .new_layout(vk::ImageLayout::TRANSFER_DST_OPTIMAL)
                    .src_queue_family_index(vk::QUEUE_FAMILY_IGNORED)
                    .dst_queue_family_index(vk::QUEUE_FAMILY_IGNORED)
                    .image(dst)
                    .subresource_range(color_range()),
            ];
            device.cmd_pipeline_barrier(
                self.cmd,
                vk::PipelineStageFlags::TRANSFER,
                vk::PipelineStageFlags::TRANSFER,
                vk::DependencyFlags::empty(),
                &[],
                &[],
                &take,
            );
            let layers = vk::ImageSubresourceLayers { aspect_mask: vk::ImageAspectFlags::COLOR, mip_level: 0, base_array_layer: 0, layer_count: 1 };
            let region = [vk::ImageBlit {
                src_subresource: layers,
                src_offsets: [vk::Offset3D { x: from[0], y: from[1], z: 0 }, vk::Offset3D { x: to[0], y: to[1], z: 1 }],
                dst_subresource: layers,
                dst_offsets: [
                    vk::Offset3D { x: 0, y: 0, z: 0 },
                    vk::Offset3D { x: self.extent.width as i32, y: self.extent.height as i32, z: 1 },
                ],
            }];
            device.cmd_blit_image(
                self.cmd,
                src.image,
                vk::ImageLayout::TRANSFER_SRC_OPTIMAL,
                dst,
                vk::ImageLayout::TRANSFER_DST_OPTIMAL,
                &region,
                vk::Filter::NEAREST,
            );
            match (&self.opaque, src.opaque) {
                (Some(pass), true) => {
                    let begin = vk::RenderPassBeginInfo::default()
                        .render_pass(pass.render_pass)
                        .framebuffer(pass.framebuffers[i])
                        .render_area(vk::Rect2D { offset: vk::Offset2D::default(), extent: self.extent });
                    device.cmd_begin_render_pass(self.cmd, &begin, vk::SubpassContents::INLINE);
                    device.cmd_bind_pipeline(self.cmd, vk::PipelineBindPoint::GRAPHICS, pass.pipeline);
                    let viewport = [vk::Viewport {
                        x: 0.0,
                        y: 0.0,
                        width: self.extent.width as f32,
                        height: self.extent.height as f32,
                        min_depth: 0.0,
                        max_depth: 1.0,
                    }];
                    device.cmd_set_viewport(self.cmd, 0, &viewport);
                    device.cmd_set_scissor(self.cmd, 0, &[vk::Rect2D { offset: vk::Offset2D::default(), extent: self.extent }]);
                    device.cmd_draw(self.cmd, 3, 1, 0, 0);
                    device.cmd_end_render_pass(self.cmd);
                }
                _ => {
                    let to_present = [vk::ImageMemoryBarrier::default()
                        .src_access_mask(vk::AccessFlags::TRANSFER_WRITE)
                        .dst_access_mask(vk::AccessFlags::empty())
                        .old_layout(vk::ImageLayout::TRANSFER_DST_OPTIMAL)
                        .new_layout(vk::ImageLayout::PRESENT_SRC_KHR)
                        .src_queue_family_index(vk::QUEUE_FAMILY_IGNORED)
                        .dst_queue_family_index(vk::QUEUE_FAMILY_IGNORED)
                        .image(dst)
                        .subresource_range(color_range())];
                    device.cmd_pipeline_barrier(
                        self.cmd,
                        vk::PipelineStageFlags::TRANSFER,
                        vk::PipelineStageFlags::BOTTOM_OF_PIPE,
                        vk::DependencyFlags::empty(),
                        &[],
                        &[],
                        &to_present,
                    );
                }
            }
            // Hand the client's image back to its queue as it was.
            let give_back = [vk::ImageMemoryBarrier::default()
                .src_access_mask(vk::AccessFlags::TRANSFER_READ)
                .dst_access_mask(vk::AccessFlags::empty())
                .old_layout(vk::ImageLayout::TRANSFER_SRC_OPTIMAL)
                .new_layout(vk::ImageLayout::GENERAL)
                .src_queue_family_index(ctx.queue_family)
                .dst_queue_family_index(ctx.foreign_family)
                .image(src.image)
                .subresource_range(color_range())];
            device.cmd_pipeline_barrier(
                self.cmd,
                vk::PipelineStageFlags::TRANSFER,
                vk::PipelineStageFlags::BOTTOM_OF_PIPE,
                vk::DependencyFlags::empty(),
                &[],
                &[],
                &give_back,
            );
            device.end_command_buffer(self.cmd)
        };
        if let Err(e) = record() {
            // The acquire semaphore is signalled and never waited: this presenter is done.
            return Err(PresentError::FallBack(format!("recording: {e:?}")));
        }
        let waits = [self.acquire];
        let stages = [vk::PipelineStageFlags::TRANSFER];
        let cmds = [self.cmd];
        let signals = [done];
        let submit = [vk::SubmitInfo::default().wait_semaphores(&waits).wait_dst_stage_mask(&stages).command_buffers(&cmds).signal_semaphores(&signals)];
        if let Err(e) = device.queue_submit(ctx.queue, &submit, self.fence) {
            return Err(PresentError::FallBack(format!("vkQueueSubmit: {e:?}")));
        }
        let swapchains = [self.swapchain];
        let indices = [index];
        let present = vk::PresentInfoKHR::default().wait_semaphores(&signals).swapchains(&swapchains).image_indices(&indices);
        let presented = ctx.swapchain_fn.queue_present(ctx.queue, &present);
        // The blit has to be done before the client gets its buffer back.
        let waited = device.wait_for_fences(&[self.fence], true, FENCE_TIMEOUT_NS);
        let _ = device.reset_fences(&[self.fence]);
        if let Err(e) = waited {
            return Err(PresentError::FallBack(format!("the GPU did not finish the blit in {} ms: {e:?}", FENCE_TIMEOUT_NS / 1_000_000)));
        }
        match presented {
            Ok(suboptimal) => {
                self.stale |= suboptimal;
                Ok(())
            }
            Err(vk::Result::ERROR_OUT_OF_DATE_KHR) => {
                self.stale = true;
                Ok(())
            }
            Err(vk::Result::ERROR_SURFACE_LOST_KHR) => Err(PresentError::Lost("surface lost on present".into())),
            Err(e) => Err(PresentError::FallBack(format!("vkQueuePresentKHR: {e:?}"))),
        }
    }
}

impl Drop for WindowPresenter {
    fn drop(&mut self) {
        let ctx = self.ctx.clone();
        let device = &ctx.device;
        // SAFETY: every present waited for its fence; a failed one may leave work queued, so wait
        // for the queue before destroying what it may use. The surface goes last: destroying it
        // disconnects the Surface, so the copy path can connect to it afterwards.
        unsafe {
            let _ = device.queue_wait_idle(ctx.queue);
            self.drop_swapchain_parts();
            if self.swapchain != vk::SwapchainKHR::null() {
                ctx.swapchain_fn.destroy_swapchain(self.swapchain, None);
            }
            if self.acquire != vk::Semaphore::null() {
                device.destroy_semaphore(self.acquire, None);
            }
            if self.fence != vk::Fence::null() {
                device.destroy_fence(self.fence, None);
            }
            if self.pool != vk::CommandPool::null() {
                device.destroy_command_pool(self.pool, None);
            }
            ctx.surface_fn.destroy_surface(self.surface, None);
        }
    }
}

/// The state of DMA_BUF_IOCTL_EXPORT_SYNC_FILE on this kernel: 0 untried, 1 gives fences,
/// 2 unsupported.
static EXPORT_SYNC: AtomicU8 = AtomicU8::new(0);
static FENCE_TIMEOUT_LOGGED: AtomicBool = AtomicBool::new(false);

#[repr(C)]
struct DmaBufExportSyncFile {
    flags: u32,
    fd: i32,
}

/// _IOWR('b', 2, struct dma_buf_export_sync_file), Linux 6.0.
const DMA_BUF_IOCTL_EXPORT_SYNC_FILE: u32 = 0xC008_6202;
const DMA_BUF_SYNC_READ: u32 = 1;

/// Waits (at most IMPLICIT_FENCE_TIMEOUT_MS) for the writes the kernel knows of on the client's
/// dmabuf, when the kernel can export them as a sync file. KGSL attaches no fence to a dmabuf,
/// so on pong this is expected to be either unsupported or an already-signalled fence; the
/// first outcome is logged once.
pub fn wait_implicit_fence(dmabuf: &Dmabuf) {
    if EXPORT_SYNC.load(Ordering::Relaxed) == 2 {
        return;
    }
    let Some(fd) = dmabuf.handles().next() else { return };
    let mut arg = DmaBufExportSyncFile { flags: DMA_BUF_SYNC_READ, fd: -1 };
    // SAFETY: the ioctl writes only into `arg`, which lives on this stack.
    let rc = unsafe { libc::ioctl(fd.as_raw_fd(), DMA_BUF_IOCTL_EXPORT_SYNC_FILE as _, &mut arg) };
    if rc != 0 || arg.fd < 0 {
        let err = std::io::Error::last_os_error();
        if EXPORT_SYNC.swap(2, Ordering::Relaxed) != 2 {
            lwarn!("direct sync: DMA_BUF_IOCTL_EXPORT_SYNC_FILE unsupported ({err}); no implicit fence, the blit may read a frame the client is still drawing (tearing possible)");
        }
        return;
    }
    let mut pfd = libc::pollfd { fd: arg.fd, events: libc::POLLIN, revents: 0 };
    // SAFETY: one valid pollfd; the sync file fd is ours and closed right after.
    let ready = unsafe { libc::poll(&mut pfd, 1, IMPLICIT_FENCE_TIMEOUT_MS) };
    // SAFETY: closing the sync file the ioctl gave us.
    unsafe { libc::close(arg.fd) };
    if EXPORT_SYNC.swap(1, Ordering::Relaxed) == 0 {
        linfo!(
            "direct sync: DMA_BUF_IOCTL_EXPORT_SYNC_FILE gives a fence (first one {}); KGSL attaches none of its own, so a signalled fence proves nothing",
            if ready > 0 { "signalled" } else { "not signalled in time" }
        );
    } else if ready == 0 && !FENCE_TIMEOUT_LOGGED.swap(true, Ordering::Relaxed) {
        lwarn!("direct sync: a client fence did not signal within {IMPLICIT_FENCE_TIMEOUT_MS} ms; blitting anyway (logged once)");
    }
}
