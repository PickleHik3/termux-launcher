//! A tiny wl_shm client for the Wayland host spike.
//!
//! Opens one xdg_toplevel titled from argv[1] and draws, in premultiplied ARGB8888: a fully
//! transparent left half, an opaque gradient on the right half, a 50 % translucent band across
//! the middle and an opaque white bar that moves one step per frame callback. It prints the
//! frame rate once a second so pacing is visible from the shell.

use std::os::fd::{AsFd, OwnedFd};
use std::time::Instant;

use wayland_client::globals::{registry_queue_init, GlobalListContents};
use wayland_client::protocol::{wl_buffer, wl_callback, wl_compositor, wl_registry, wl_shm, wl_shm_pool, wl_surface};
use wayland_client::{delegate_noop, Connection, Dispatch, QueueHandle};
use wayland_protocols::xdg::decoration::zv1::client::{zxdg_decoration_manager_v1, zxdg_toplevel_decoration_v1};
use wayland_protocols::xdg::shell::client::{xdg_surface, xdg_toplevel, xdg_wm_base};

const BUFFERS: usize = 2;
const BAR_WIDTH: i32 = 24;
const BAR_STEP: i32 = 6;

struct Pool {
    _fd: OwnedFd,
    map: *mut u8,
    len: usize,
    pool: wl_shm_pool::WlShmPool,
    buffers: Vec<wl_buffer::WlBuffer>,
    busy: [bool; BUFFERS],
    width: i32,
    height: i32,
}

impl Drop for Pool {
    fn drop(&mut self) {
        for b in &self.buffers {
            b.destroy();
        }
        self.pool.destroy();
        unsafe {
            let _ = rustix::mm::munmap(self.map as *mut _, self.len);
        }
    }
}

struct App {
    compositor: wl_compositor::WlCompositor,
    shm: wl_shm::WlShm,
    surface: wl_surface::WlSurface,
    xdg_surface: xdg_surface::XdgSurface,
    configured: bool,
    pending_size: (i32, i32),
    pool: Option<Pool>,
    frame: u32,
    waiting_frame: bool,
    want_draw: bool,
    running: bool,
    fps_frames: u32,
    fps_since: Instant,
}

fn shm_fd(size: usize) -> std::io::Result<OwnedFd> {
    let fd = match rustix::fs::memfd_create("tlwayland-testclient", rustix::fs::MemfdFlags::CLOEXEC) {
        Ok(fd) => fd,
        Err(_) => {
            // Fall back to an unlinked file in the runtime dir.
            let dir = std::env::var("XDG_RUNTIME_DIR").unwrap_or_else(|_| ".".into());
            let path = format!("{dir}/tlwayland-testclient-{}", std::process::id());
            let file = std::fs::OpenOptions::new().read(true).write(true).create(true).truncate(true).open(&path)?;
            let _ = std::fs::remove_file(&path);
            OwnedFd::from(file)
        }
    };
    rustix::fs::ftruncate(&fd, size as u64)?;
    Ok(fd)
}

impl App {
    fn make_pool(&mut self, qh: &QueueHandle<App>, width: i32, height: i32) {
        self.pool = None;
        let frame_len = (width * height * 4) as usize;
        let len = frame_len * BUFFERS;
        let fd = shm_fd(len).expect("shm file");
        let map = unsafe {
            rustix::mm::mmap(
                std::ptr::null_mut(),
                len,
                rustix::mm::ProtFlags::READ | rustix::mm::ProtFlags::WRITE,
                rustix::mm::MapFlags::SHARED,
                &fd,
                0,
            )
            .expect("mmap")
        } as *mut u8;
        let pool = self.shm.create_pool(fd.as_fd(), len as i32, qh, ());
        let buffers = (0..BUFFERS)
            .map(|i| {
                pool.create_buffer((i * frame_len) as i32, width, height, width * 4, wl_shm::Format::Argb8888, qh, i)
            })
            .collect();
        self.pool = Some(Pool { _fd: fd, map, len, pool, buffers, busy: [false; BUFFERS], width, height });
    }

    fn draw(&mut self, qh: &QueueHandle<App>) {
        let Some(pool) = self.pool.as_mut() else { return };
        let Some(index) = pool.busy.iter().position(|b| !b) else {
            // Both buffers are with the compositor: draw again on the next release.
            self.want_draw = true;
            return;
        };
        self.want_draw = false;
        let (w, h) = (pool.width, pool.height);
        let frame_len = (w * h) as usize;
        let pixels = unsafe { std::slice::from_raw_parts_mut((pool.map as *mut u32).add(index * frame_len), frame_len) };
        let bar_x = (self.frame as i32 * BAR_STEP) % w.max(1);
        let (band_top, band_bottom) = (h * 2 / 5, h * 3 / 5);
        for y in 0..h {
            let row = &mut pixels[(y * w) as usize..((y + 1) * w) as usize];
            let in_band = y >= band_top && y < band_bottom;
            for (x, px) in row.iter_mut().enumerate() {
                let x = x as i32;
                let r = (255 * x / w.max(1)) as u32;
                let g = (255 * y / h.max(1)) as u32;
                let b = 160u32;
                *px = if x >= bar_x && x < bar_x + BAR_WIDTH {
                    0xFFFF_FFFF
                } else if in_band {
                    // 50 % alpha, premultiplied.
                    (128 << 24) | ((r * 128 / 255) << 16) | ((g * 128 / 255) << 8) | (b * 128 / 255)
                } else if x >= w / 2 {
                    (255 << 24) | (r << 16) | (g << 8) | b
                } else {
                    0
                };
            }
        }
        pool.busy[index] = true;
        self.surface.attach(Some(&pool.buffers[index]), 0, 0);
        self.surface.damage_buffer(0, 0, w, h);
        self.surface.frame(qh, ());
        self.surface.commit();
        self.waiting_frame = true;
        self.frame = self.frame.wrapping_add(1);

        self.fps_frames += 1;
        let elapsed = self.fps_since.elapsed();
        if elapsed.as_secs_f32() >= 1.0 {
            println!("{:.1} frames/s at {}x{}", self.fps_frames as f32 / elapsed.as_secs_f32(), w, h);
            self.fps_frames = 0;
            self.fps_since = Instant::now();
        }
    }
}

impl Dispatch<wl_registry::WlRegistry, GlobalListContents> for App {
    fn event(_: &mut Self, _: &wl_registry::WlRegistry, _: wl_registry::Event, _: &GlobalListContents, _: &Connection, _: &QueueHandle<Self>) {}
}

impl Dispatch<wl_buffer::WlBuffer, usize> for App {
    fn event(app: &mut Self, _: &wl_buffer::WlBuffer, event: wl_buffer::Event, index: &usize, _: &Connection, qh: &QueueHandle<Self>) {
        if let wl_buffer::Event::Release = event {
            if let Some(pool) = app.pool.as_mut() {
                pool.busy[*index] = false;
            }
            if app.want_draw {
                app.draw(qh);
            }
        }
    }
}

impl Dispatch<wl_callback::WlCallback, ()> for App {
    fn event(app: &mut Self, _: &wl_callback::WlCallback, event: wl_callback::Event, _: &(), _: &Connection, qh: &QueueHandle<Self>) {
        if let wl_callback::Event::Done { .. } = event {
            app.waiting_frame = false;
            app.draw(qh);
        }
    }
}

impl Dispatch<xdg_wm_base::XdgWmBase, ()> for App {
    fn event(_: &mut Self, base: &xdg_wm_base::XdgWmBase, event: xdg_wm_base::Event, _: &(), _: &Connection, _: &QueueHandle<Self>) {
        if let xdg_wm_base::Event::Ping { serial } = event {
            base.pong(serial);
        }
    }
}

impl Dispatch<xdg_surface::XdgSurface, ()> for App {
    fn event(app: &mut Self, s: &xdg_surface::XdgSurface, event: xdg_surface::Event, _: &(), _: &Connection, qh: &QueueHandle<Self>) {
        if let xdg_surface::Event::Configure { serial } = event {
            s.ack_configure(serial);
            app.configured = true;
            let (w, h) = app.pending_size;
            let (w, h) = (if w > 0 { w } else { 480 }, if h > 0 { h } else { 320 });
            let resize = app.pool.as_ref().map(|p| (p.width, p.height)) != Some((w, h));
            if resize {
                app.make_pool(qh, w, h);
            }
            if resize || !app.waiting_frame {
                app.draw(qh);
            }
        }
    }
}

impl Dispatch<xdg_toplevel::XdgToplevel, ()> for App {
    fn event(app: &mut Self, _: &xdg_toplevel::XdgToplevel, event: xdg_toplevel::Event, _: &(), _: &Connection, _: &QueueHandle<Self>) {
        match event {
            xdg_toplevel::Event::Configure { width, height, .. } => app.pending_size = (width, height),
            xdg_toplevel::Event::Close => app.running = false,
            _ => {}
        }
    }
}

delegate_noop!(App: ignore wl_compositor::WlCompositor);
delegate_noop!(App: ignore wl_surface::WlSurface);
delegate_noop!(App: ignore wl_shm::WlShm);
delegate_noop!(App: ignore wl_shm_pool::WlShmPool);
delegate_noop!(App: ignore zxdg_decoration_manager_v1::ZxdgDecorationManagerV1);
delegate_noop!(App: ignore zxdg_toplevel_decoration_v1::ZxdgToplevelDecorationV1);

fn main() {
    let title = std::env::args().nth(1).unwrap_or_else(|| "tlwayland test".into());
    let conn = Connection::connect_to_env().expect("connect: set XDG_RUNTIME_DIR and WAYLAND_DISPLAY");
    let (globals, mut queue) = registry_queue_init::<App>(&conn).expect("registry");
    let qh = queue.handle();

    let compositor: wl_compositor::WlCompositor = globals.bind(&qh, 1..=6, ()).expect("wl_compositor");
    let shm: wl_shm::WlShm = globals.bind(&qh, 1..=1, ()).expect("wl_shm");
    let wm_base: xdg_wm_base::XdgWmBase = globals.bind(&qh, 1..=6, ()).expect("xdg_wm_base");
    let decorations: Option<zxdg_decoration_manager_v1::ZxdgDecorationManagerV1> = globals.bind(&qh, 1..=1, ()).ok();

    let surface = compositor.create_surface(&qh, ());
    let xdg_surface = wm_base.get_xdg_surface(&surface, &qh, ());
    let toplevel = xdg_surface.get_toplevel(&qh, ());
    toplevel.set_title(title.clone());
    toplevel.set_app_id("tlwayland-testclient".into());
    if let Some(d) = decorations.as_ref() {
        let deco = d.get_toplevel_decoration(&toplevel, &qh, ());
        deco.set_mode(zxdg_toplevel_decoration_v1::Mode::ServerSide);
    }
    surface.commit();

    let mut app = App {
        compositor,
        shm,
        surface,
        xdg_surface,
        configured: false,
        pending_size: (0, 0),
        pool: None,
        frame: 0,
        waiting_frame: false,
        want_draw: false,
        running: true,
        fps_frames: 0,
        fps_since: Instant::now(),
    };
    println!("{title}: connected");
    while app.running {
        if let Err(e) = queue.blocking_dispatch(&mut app) {
            eprintln!("dispatch: {e}");
            break;
        }
    }
    let _ = (&app.compositor, &app.xdg_surface, app.configured);
    toplevel.destroy();
}
