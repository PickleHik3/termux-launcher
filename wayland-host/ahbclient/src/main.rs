//! Feasibility probe E3: can a Termux program's Vulkan driver (Mesa Turnip on KGSL) import a
//! buffer the launcher allocated (an AHardwareBuffer's dma-buf fd) and render into it?
//!
//! `tlwayland-ahbclient <socket> [libvulkan.so path]`: connects to the launcher's socket, receives
//! one fd and its layout (width, height, stride, DRM fourcc and modifier), imports the fd as the
//! memory of a VkImage with `VK_EXT_external_memory_dma_buf` and an explicit layout
//! (`VK_EXT_image_drm_format_modifier`), clears it to a known premultiplied colour, waits for the
//! queue, then tells the launcher `ok RRGGBBAA` or `fail <call> <error>` on the same socket.
//!
//! The Vulkan loader is the prefix's (`$PREFIX/lib/libvulkan.so`), loaded with dlopen: a bare
//! `libvulkan.so` would resolve to the system's stock driver. The loader itself honours
//! `VK_DRIVER_FILES` / `VK_ICD_FILENAMES`.

use std::ffi::{c_void, CStr};
use std::io::Write;
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd, RawFd};
use std::os::unix::net::UnixStream;

use ash::vk;

/// The colour cleared to, premultiplied RGBA bytes: blue at 50 % alpha with a little green.
const CLEAR: [u8; 4] = [0x00, 0x40, 0x80, 0x80];

const DRM_FORMAT_ABGR8888: u32 = u32::from_le_bytes(*b"AB24");

struct Layout {
    width: u32,
    height: u32,
    stride: u32,
    offset: u32,
    fourcc: u32,
    modifier: u64,
    size: u64,
}

/// A failure: the call that failed and why.
struct Fail(String, String);

fn fail<E: std::fmt::Debug>(call: &str) -> impl FnOnce(E) -> Fail + '_ {
    move |e| Fail(call.to_string(), format!("{e:?}"))
}

fn say(msg: &str) {
    println!("ahbclient: {msg}");
}

/// Receives the 48-byte layout header and one fd (SCM_RIGHTS).
fn receive(sock: RawFd) -> Result<(Layout, OwnedFd), Fail> {
    let mut data = [0u8; 48];
    let mut cmsg = [0u8; 64];
    let mut iov = libc::iovec { iov_base: data.as_mut_ptr() as *mut c_void, iov_len: data.len() };
    // SAFETY: a zeroed msghdr is valid; every pointer set refers to a live buffer.
    let mut msg: libc::msghdr = unsafe { std::mem::zeroed() };
    msg.msg_iov = &mut iov;
    msg.msg_iovlen = 1;
    msg.msg_control = cmsg.as_mut_ptr() as *mut c_void;
    msg.msg_controllen = cmsg.len() as _;
    // SAFETY: msg describes live buffers.
    let n = unsafe { libc::recvmsg(sock, &mut msg, libc::MSG_CMSG_CLOEXEC | libc::MSG_WAITALL) };
    if n != data.len() as isize {
        return Err(Fail("recvmsg".into(), format!("got {n} bytes: {}", std::io::Error::last_os_error())));
    }
    let mut fd = None;
    // SAFETY: walking the control buffer recvmsg filled, with libc's CMSG helpers.
    unsafe {
        let c = libc::CMSG_FIRSTHDR(&msg);
        if !c.is_null() && (*c).cmsg_level == libc::SOL_SOCKET && (*c).cmsg_type == libc::SCM_RIGHTS {
            fd = Some(OwnedFd::from_raw_fd(std::ptr::read_unaligned(libc::CMSG_DATA(c) as *const i32)));
        }
    }
    if &data[0..8] != b"TLAHB001" {
        return Err(Fail("header".into(), "bad magic".into()));
    }
    let u32_at = |o: usize| u32::from_le_bytes(data[o..o + 4].try_into().unwrap_or_default());
    let u64_at = |o: usize| u64::from_le_bytes(data[o..o + 8].try_into().unwrap_or_default());
    let layout = Layout {
        width: u32_at(8),
        height: u32_at(12),
        stride: u32_at(16),
        offset: u32_at(20),
        fourcc: u32_at(24),
        modifier: u64_at(32),
        size: u64_at(40),
    };
    let fd = fd.ok_or_else(|| Fail("recvmsg".into(), "no fd arrived".into()))?;
    Ok((layout, fd))
}

fn has(exts: &[vk::ExtensionProperties], name: &CStr) -> bool {
    exts.iter().any(|e| e.extension_name_as_c_str().map(|n| n == name).unwrap_or(false))
}

fn version(v: u32) -> String {
    format!("{}.{}.{}", vk::api_version_major(v), vk::api_version_minor(v), vk::api_version_patch(v))
}

/// Imports the fd and clears the image. Every Vulkan object is left to process exit: this is a
/// one-shot probe.
fn run(layout: &Layout, fd: &OwnedFd, vulkan_path: &str) -> Result<(), Fail> {
    if layout.fourcc != DRM_FORMAT_ABGR8888 {
        return Err(Fail("format".into(), format!("fourcc 0x{:08x} is not AB24", layout.fourcc)));
    }
    // SAFETY: loads a Vulkan loader library; its entry points are called as Vulkan defines.
    let entry = unsafe { ash::Entry::load_from(vulkan_path) }.map_err(fail("dlopen libvulkan"))?;
    say(&format!("loaded {vulkan_path}"));
    // SAFETY: entry point of a loaded loader.
    let loader = unsafe { entry.try_enumerate_instance_version() }.ok().flatten().unwrap_or(vk::API_VERSION_1_0);
    say(&format!("loader version {}", version(loader)));
    if loader < vk::API_VERSION_1_1 {
        return Err(Fail("vkEnumerateInstanceVersion".into(), "loader below Vulkan 1.1".into()));
    }
    let app = vk::ApplicationInfo::default().api_version(loader.min(vk::API_VERSION_1_3));
    // SAFETY: a valid create info.
    let instance = unsafe { entry.create_instance(&vk::InstanceCreateInfo::default().application_info(&app), None) }
        .map_err(fail("vkCreateInstance"))?;
    // SAFETY: a live instance.
    let devices = unsafe { instance.enumerate_physical_devices() }.map_err(fail("vkEnumeratePhysicalDevices"))?;
    let pd = *devices.first().ok_or_else(|| Fail("vkEnumeratePhysicalDevices".into(), "no device".into()))?;
    // SAFETY: a physical device of this instance.
    let props = unsafe { instance.get_physical_device_properties(pd) };
    let name = props.device_name_as_c_str().map(|c| c.to_string_lossy().into_owned()).unwrap_or_default();
    say(&format!(
        "device 0: {name} (api {}, driver 0x{:x}, vendor 0x{:x}, {} device(s) in all)",
        version(props.api_version),
        props.driver_version,
        props.vendor_id,
        devices.len()
    ));
    if props.api_version < vk::API_VERSION_1_1 {
        return Err(Fail("device".into(), "below Vulkan 1.1".into()));
    }
    // SAFETY: a physical device of this instance.
    let exts = unsafe { instance.enumerate_device_extension_properties(pd) }.map_err(fail("vkEnumerateDeviceExtensionProperties"))?;
    let needed = [ash::khr::external_memory_fd::NAME, ash::ext::external_memory_dma_buf::NAME, ash::ext::image_drm_format_modifier::NAME];
    let optional = [ash::ext::queue_family_foreign::NAME, ash::khr::image_format_list::NAME];
    for n in needed.iter().chain(optional.iter()) {
        say(&format!("extension {}: {}", n.to_string_lossy(), if has(&exts, n) { "yes" } else { "MISSING" }));
    }
    if !has(&exts, needed[0]) || !has(&exts, needed[1]) {
        return Err(Fail("extensions".into(), "no dma-buf import".into()));
    }
    let use_modifier = has(&exts, needed[2]);
    let foreign = has(&exts, optional[0]);
    let mut enabled: Vec<*const std::ffi::c_char> =vec![needed[0].as_ptr(), needed[1].as_ptr()];
    if use_modifier {
        enabled.push(needed[2].as_ptr());
        if props.api_version < vk::API_VERSION_1_2 && has(&exts, optional[1]) {
            enabled.push(optional[1].as_ptr());
        }
    } else {
        say("no VK_EXT_image_drm_format_modifier: falling back to VK_IMAGE_TILING_LINEAR and checking its pitch");
    }
    if foreign {
        enabled.push(optional[0].as_ptr());
    }

    let format = vk::Format::R8G8B8A8_UNORM;
    let handle_type = vk::ExternalMemoryHandleTypeFlags::DMA_BUF_EXT;
    let tiling = if use_modifier { vk::ImageTiling::DRM_FORMAT_MODIFIER_EXT } else { vk::ImageTiling::LINEAR };

    if use_modifier {
        let mut list = vk::DrmFormatModifierPropertiesListEXT::default();
        let mut fp = vk::FormatProperties2::default().push_next(&mut list);
        // SAFETY: a valid out struct chain.
        unsafe { instance.get_physical_device_format_properties2(pd, format, &mut fp) };
        let count = list.drm_format_modifier_count as usize;
        let mut mods = vec![vk::DrmFormatModifierPropertiesEXT::default(); count];
        let mut list = vk::DrmFormatModifierPropertiesListEXT::default().drm_format_modifier_properties(&mut mods);
        let mut fp = vk::FormatProperties2::default().push_next(&mut list);
        // SAFETY: the list points at `count` entries.
        unsafe { instance.get_physical_device_format_properties2(pd, format, &mut fp) };
        for m in &mods {
            say(&format!(
                "R8G8B8A8_UNORM modifier 0x{:x}: {} plane(s), features {:?}",
                m.drm_format_modifier, m.drm_format_modifier_plane_count, m.drm_format_modifier_tiling_features
            ));
        }
    }

    let mut usage = vk::ImageUsageFlags::TRANSFER_DST | vk::ImageUsageFlags::COLOR_ATTACHMENT;
    for attempt in 0..2 {
        let mut ext_info = vk::PhysicalDeviceExternalImageFormatInfo::default().handle_type(handle_type);
        let mut mod_info = vk::PhysicalDeviceImageDrmFormatModifierInfoEXT::default()
            .drm_format_modifier(layout.modifier)
            .sharing_mode(vk::SharingMode::EXCLUSIVE);
        let mut info = vk::PhysicalDeviceImageFormatInfo2::default()
            .format(format)
            .ty(vk::ImageType::TYPE_2D)
            .tiling(tiling)
            .usage(usage)
            .push_next(&mut ext_info);
        if use_modifier {
            info = info.push_next(&mut mod_info);
        }
        let mut ext_props = vk::ExternalImageFormatProperties::default();
        let mut out = vk::ImageFormatProperties2::default().push_next(&mut ext_props);
        // SAFETY: valid in and out chains.
        let r = unsafe { instance.get_physical_device_image_format_properties2(pd, &info, &mut out) };
        let ep = ext_props.external_memory_properties;
        say(&format!(
            "image format query (usage {usage:?}): {r:?}; external features {:?}, importable from {:?}",
            ep.external_memory_features, ep.compatible_handle_types
        ));
        if r.is_ok() || attempt == 1 {
            break;
        }
        usage = vk::ImageUsageFlags::TRANSFER_DST;
    }

    // SAFETY: a physical device of this instance.
    let families = unsafe { instance.get_physical_device_queue_family_properties(pd) };
    let family = families
        .iter()
        .position(|f| f.queue_flags.contains(vk::QueueFlags::GRAPHICS))
        .ok_or_else(|| Fail("queue".into(), "no graphics queue".into()))? as u32;
    let priorities = [1.0f32];
    let queue_info = [vk::DeviceQueueCreateInfo::default().queue_family_index(family).queue_priorities(&priorities)];
    let device_info = vk::DeviceCreateInfo::default().queue_create_infos(&queue_info).enabled_extension_names(&enabled);
    // SAFETY: valid create info naming only extensions the device lists.
    let device = unsafe { instance.create_device(pd, &device_info, None) }.map_err(fail("vkCreateDevice"))?;
    say("device created");

    let plane = [vk::SubresourceLayout { offset: layout.offset as u64, size: 0, row_pitch: layout.stride as u64, array_pitch: 0, depth_pitch: 0 }];
    let mut external = vk::ExternalMemoryImageCreateInfo::default().handle_types(handle_type);
    let mut explicit = vk::ImageDrmFormatModifierExplicitCreateInfoEXT::default().drm_format_modifier(layout.modifier).plane_layouts(&plane);
    let mut image_info = vk::ImageCreateInfo::default()
        .image_type(vk::ImageType::TYPE_2D)
        .format(format)
        .extent(vk::Extent3D { width: layout.width, height: layout.height, depth: 1 })
        .mip_levels(1)
        .array_layers(1)
        .samples(vk::SampleCountFlags::TYPE_1)
        .tiling(tiling)
        .usage(usage)
        .sharing_mode(vk::SharingMode::EXCLUSIVE)
        .initial_layout(vk::ImageLayout::UNDEFINED)
        .push_next(&mut external);
    if use_modifier {
        image_info = image_info.push_next(&mut explicit);
    }
    // SAFETY: a valid create info on a live device.
    let image = unsafe { device.create_image(&image_info, None) }.map_err(fail("vkCreateImage"))?;
    say(&format!("image created: {}x{} tiling {tiling:?} modifier 0x{:x} rowPitch {}", layout.width, layout.height, layout.modifier, layout.stride));

    let aspect = if use_modifier { vk::ImageAspectFlags::MEMORY_PLANE_0_EXT } else { vk::ImageAspectFlags::COLOR };
    // SAFETY: a linear or modifier image of this device.
    let sub = unsafe { device.get_image_subresource_layout(image, vk::ImageSubresource { aspect_mask: aspect, mip_level: 0, array_layer: 0 }) };
    say(&format!("driver layout: offset {} size {} rowPitch {} (host stride {})", sub.offset, sub.size, sub.row_pitch, layout.stride));
    if sub.row_pitch != layout.stride as u64 {
        say("WARNING: the driver's row pitch differs from the buffer's stride: the picture will be sheared");
    }

    let fd_api = ash::khr::external_memory_fd::Device::new(&instance, &device);
    let mut fd_props = vk::MemoryFdPropertiesKHR::default();
    // SAFETY: an open dma-buf fd; the call does not take it.
    unsafe { fd_api.get_memory_fd_properties(handle_type, fd.as_raw_fd(), &mut fd_props) }.map_err(fail("vkGetMemoryFdPropertiesKHR"))?;
    // SAFETY: an image of this device.
    let reqs = unsafe { device.get_image_memory_requirements(image) };
    say(&format!(
        "memory: image needs {} bytes (align {}) types 0x{:x}; fd allows types 0x{:x}; buffer is {} bytes",
        reqs.size, reqs.alignment, reqs.memory_type_bits, fd_props.memory_type_bits, layout.size
    ));
    if layout.size != 0 && reqs.size > layout.size {
        say("WARNING: the image needs more bytes than the buffer holds");
    }
    let bits = reqs.memory_type_bits & fd_props.memory_type_bits;
    let bits = if bits == 0 { reqs.memory_type_bits } else { bits };
    if bits == 0 {
        return Err(Fail("memory type".into(), "no type fits".into()));
    }
    let type_index = bits.trailing_zeros();
    // The import takes ownership of the fd it is given, so it gets a duplicate.
    // SAFETY: dup of an open fd.
    let dup = unsafe { libc::fcntl(fd.as_raw_fd(), libc::F_DUPFD_CLOEXEC, 0) };
    if dup < 0 {
        return Err(Fail("dup".into(), std::io::Error::last_os_error().to_string()));
    }
    let mut import = vk::ImportMemoryFdInfoKHR::default().handle_type(handle_type).fd(dup);
    let mut dedicated = vk::MemoryDedicatedAllocateInfo::default().image(image);
    let alloc = vk::MemoryAllocateInfo::default()
        .allocation_size(reqs.size)
        .memory_type_index(type_index)
        .push_next(&mut import)
        .push_next(&mut dedicated);
    // SAFETY: a valid import chain; on success the driver owns `dup`.
    let memory = match unsafe { device.allocate_memory(&alloc, None) } {
        Ok(m) => m,
        Err(e) => {
            // SAFETY: the failed import left `dup` with us.
            unsafe { libc::close(dup) };
            return Err(Fail("vkAllocateMemory (import)".into(), format!("{e:?}")));
        }
    };
    say(&format!("memory imported (type {type_index})"));
    // SAFETY: memory and image of this device, offset 0.
    unsafe { device.bind_image_memory(image, memory, 0) }.map_err(fail("vkBindImageMemory"))?;
    say("memory bound");

    // SAFETY: plain command recording and submission on objects of this device.
    unsafe {
        let pool = device
            .create_command_pool(&vk::CommandPoolCreateInfo::default().queue_family_index(family), None)
            .map_err(fail("vkCreateCommandPool"))?;
        let cmd = device
            .allocate_command_buffers(
                &vk::CommandBufferAllocateInfo::default().command_pool(pool).level(vk::CommandBufferLevel::PRIMARY).command_buffer_count(1),
            )
            .map_err(fail("vkAllocateCommandBuffers"))?[0];
        device
            .begin_command_buffer(cmd, &vk::CommandBufferBeginInfo::default().flags(vk::CommandBufferUsageFlags::ONE_TIME_SUBMIT))
            .map_err(fail("vkBeginCommandBuffer"))?;
        let range = vk::ImageSubresourceRange { aspect_mask: vk::ImageAspectFlags::COLOR, base_mip_level: 0, level_count: 1, base_array_layer: 0, layer_count: 1 };
        let to_dst = vk::ImageMemoryBarrier::default()
            .src_access_mask(vk::AccessFlags::empty())
            .dst_access_mask(vk::AccessFlags::TRANSFER_WRITE)
            .old_layout(vk::ImageLayout::UNDEFINED)
            .new_layout(vk::ImageLayout::TRANSFER_DST_OPTIMAL)
            .src_queue_family_index(vk::QUEUE_FAMILY_IGNORED)
            .dst_queue_family_index(vk::QUEUE_FAMILY_IGNORED)
            .image(image)
            .subresource_range(range);
        device.cmd_pipeline_barrier(
            cmd,
            vk::PipelineStageFlags::TOP_OF_PIPE,
            vk::PipelineStageFlags::TRANSFER,
            vk::DependencyFlags::empty(),
            &[],
            &[],
            &[to_dst],
        );
        let colour = vk::ClearColorValue { float32: CLEAR.map(|c| c as f32 / 255.0) };
        device.cmd_clear_color_image(cmd, image, vk::ImageLayout::TRANSFER_DST_OPTIMAL, &colour, &[range]);
        // Release to whoever reads the buffer next (the launcher).
        let release = vk::ImageMemoryBarrier::default()
            .src_access_mask(vk::AccessFlags::TRANSFER_WRITE)
            .dst_access_mask(vk::AccessFlags::empty())
            .old_layout(vk::ImageLayout::TRANSFER_DST_OPTIMAL)
            .new_layout(vk::ImageLayout::GENERAL)
            .src_queue_family_index(family)
            .dst_queue_family_index(if foreign { vk::QUEUE_FAMILY_FOREIGN_EXT } else { vk::QUEUE_FAMILY_EXTERNAL })
            .image(image)
            .subresource_range(range);
        device.cmd_pipeline_barrier(
            cmd,
            vk::PipelineStageFlags::TRANSFER,
            vk::PipelineStageFlags::BOTTOM_OF_PIPE,
            vk::DependencyFlags::empty(),
            &[],
            &[],
            &[release],
        );
        device.end_command_buffer(cmd).map_err(fail("vkEndCommandBuffer"))?;
        let queue = device.get_device_queue(family, 0);
        let cmds = [cmd];
        device
            .queue_submit(queue, &[vk::SubmitInfo::default().command_buffers(&cmds)], vk::Fence::null())
            .map_err(fail("vkQueueSubmit"))?;
        device.queue_wait_idle(queue).map_err(fail("vkQueueWaitIdle"))?;
    }
    say(&format!("cleared to {:02x}{:02x}{:02x}{:02x} (RGBA, premultiplied) and the queue is idle", CLEAR[0], CLEAR[1], CLEAR[2], CLEAR[3]));
    Ok(())
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let Some(path) = args.get(1) else {
        eprintln!("usage: tlwayland-ahbclient <socket> [path to libvulkan.so]");
        std::process::exit(2);
    };
    let vulkan_path = args.get(2).cloned().unwrap_or_else(|| match std::env::var("PREFIX") {
        Ok(prefix) => format!("{prefix}/lib/libvulkan.so"),
        Err(_) => "libvulkan.so".to_string(),
    });
    for var in ["VK_DRIVER_FILES", "VK_ICD_FILENAMES", "PREFIX"] {
        say(&format!("{var}={}", std::env::var(var).unwrap_or_else(|_| "(unset)".into())));
    }
    let mut stream = match UnixStream::connect(path) {
        Ok(s) => s,
        Err(e) => {
            say(&format!("connect {path} failed: {e}"));
            std::process::exit(1);
        }
    };
    say(&format!("connected to {path}"));
    let result = receive(stream.as_raw_fd()).and_then(|(layout, fd)| {
        say(&format!(
            "received fd {} {}x{} stride {} offset {} fourcc 0x{:08x} modifier 0x{:x} size {}",
            fd.as_raw_fd(),
            layout.width,
            layout.height,
            layout.stride,
            layout.offset,
            layout.fourcc,
            layout.modifier,
            layout.size
        ));
        run(&layout, &fd, &vulkan_path)
    });
    let reply = match &result {
        Ok(()) => {
            say("import ok");
            format!("ok {:02x}{:02x}{:02x}{:02x}\n", CLEAR[0], CLEAR[1], CLEAR[2], CLEAR[3])
        }
        Err(Fail(call, e)) => {
            say(&format!("import FAILED at {call}: {e}"));
            format!("fail {call} {e}\n")
        }
    };
    let _ = stream.write_all(reply.as_bytes());
    let _ = stream.flush();
    if result.is_err() {
        std::process::exit(1);
    }
}
