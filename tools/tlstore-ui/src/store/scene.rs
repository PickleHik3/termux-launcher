//! Motion hooks. Every screen records the elements it drew (a [`Scene`]) and draws each one
//! through an [`Effect`] looked up in the frame's [`Fx`]. The router asks one [`Motion`] per
//! frame which view to draw and with which effects, and tells it about every navigation.
//! [`NoMotion`] keeps everything at rest; [`super::motion::Timeline`] is the store's motion.

use std::collections::HashMap;
use std::time::Instant;

use crate::render::Rect;

/// A named part of a screen that motion can fade.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum El {
    /// Masthead: the pixel mark (or `‹ apps`), and the right-hand item (updates, repo link).
    Mark,
    Context,
    /// The header: picture, name, standfirst, facts strip. Never moved by motion.
    Picture,
    Name,
    Standfirst,
    Facts,
    /// Front: the cursor pill under the current row.
    Pill,
    /// The n-th list row on screen (Front), 0-based from the top.
    Row(u16),
    /// The n-th block of a text page on screen (Item rows, Installing parts), 0-based in
    /// drawing order.
    Block(u16),
    /// Front's pager.
    Pager,
    /// The key row and the one-line notice above it (a notice or the selection bar).
    Keys,
    Notice,
}

impl El {
    /// Body elements leave and enter; everything else stays put.
    pub fn is_body(self) -> bool {
        matches!(self, El::Pill | El::Row(_) | El::Block(_) | El::Pager)
    }
}

/// One drawn element: where it landed and, for pictures, which kitty placement it is.
#[derive(Clone, Debug, PartialEq)]
pub struct Element {
    pub el: El,
    pub rect: Rect,
    /// `(picture id, placement id)` when the element is a picture on screen.
    pub picture: Option<(u32, u32)>,
    /// The element's text at rest (row name, the install number's target, …), when it has one.
    pub text: Option<String>,
}

/// What a view drew in its last frame, in drawing order.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Scene {
    /// "front", "item", "installing".
    pub screen: &'static str,
    pub elements: Vec<Element>,
    /// Cell size in pixels when the frame was drawn.
    pub cell: (u16, u16),
}

impl Scene {
    pub fn new(screen: &'static str) -> Scene {
        Scene { screen, elements: Vec::new(), cell: (0, 0) }
    }
    pub fn push(&mut self, el: El, rect: Rect, picture: Option<(u32, u32)>, text: Option<&str>) {
        self.elements.push(Element { el, rect, picture, text: text.map(str::to_string) });
    }
    pub fn get(&self, el: El) -> Option<&Element> {
        self.elements.iter().find(|e| e.el == el)
    }
    /// The body elements in drawing order (what leaves and enters).
    pub fn body(&self) -> impl Iterator<Item = &Element> {
        self.elements.iter().filter(|e| e.el.is_body())
    }
}

/// How one element is drawn this frame. The default is "at rest".
#[derive(Clone, Debug, PartialEq)]
pub struct Effect {
    /// 0.0 invisible – 1.0 opaque. Text colours mix toward the page; pictures are hidden
    /// below 0.5 (kitty has no per-placement alpha).
    pub alpha: f32,
    /// An animated number the view shows instead of its own (the install count-up).
    pub value: Option<f32>,
}

impl Default for Effect {
    fn default() -> Effect {
        Effect { alpha: 1.0, value: None }
    }
}

impl Effect {
    pub fn at_rest(&self) -> bool {
        *self == Effect::default()
    }
    pub fn alpha(a: f32) -> Effect {
        Effect { alpha: a, value: None }
    }
}

/// The effects for one frame; an element with no entry is at rest.
#[derive(Clone, Debug, Default)]
pub struct Fx {
    map: HashMap<El, Effect>,
}

impl Fx {
    pub fn set(&mut self, el: El, e: Effect) {
        self.map.insert(el, e);
    }
    pub fn get(&self, el: El) -> Effect {
        self.map.get(&el).cloned().unwrap_or_default()
    }
    pub fn is_empty(&self) -> bool {
        self.map.is_empty()
    }
}

/// How the router moved between views.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum NavKind {
    /// Deeper: Front → Item, Item → Installing, …
    Push,
    /// Back one level.
    Pop,
    /// Swapped in place.
    Replace,
    /// Straight back to Front (the mark).
    Home,
}

/// Which view the router draws this frame, and how.
pub enum Phase {
    /// Nothing moving: draw the current view at rest.
    Idle,
    /// Draw the view being left (kept alive by the router until this phase ends) with these
    /// effects; input already goes to the new view.
    Leaving(Fx),
    /// Draw the current view with these effects.
    Entering(Fx),
}

/// The timeline the router drives.
pub trait Motion {
    /// A navigation just happened. `from` is the leaving view's last drawn scene; `to` names
    /// the entering screen. Only called when motion is on (`Ctx::motion`).
    fn navigate(&mut self, kind: NavKind, from: &Scene, to: &'static str, now: Instant);
    /// Called before every frame while [`Motion::active`]; `current` is the current view's
    /// last drawn scene (empty on its very first frame).
    fn frame(&mut self, now: Instant, current: &Scene) -> Phase;
    /// True while anything moves; the app then ticks at 60 fps.
    fn active(&self) -> bool;
    /// Called after every drawn frame while motion is on, with the current view's scene as
    /// just drawn (empty while a leaving view was drawn). Returning true makes the router
    /// draw the frame again at once, through a fresh [`Motion::frame`]: for a change that
    /// must animate from its very first frame (a new install percentage, the first look at an
    /// entering view's layout).
    fn drawn(&mut self, _now: Instant, _current: &Scene) -> bool {
        false
    }
}

/// No motion: every navigation is instant.
#[derive(Default)]
pub struct NoMotion;

impl Motion for NoMotion {
    fn navigate(&mut self, _: NavKind, _: &Scene, _: &'static str, _: Instant) {}
    fn frame(&mut self, _: Instant, _: &Scene) -> Phase {
        Phase::Idle
    }
    fn active(&self) -> bool {
        false
    }
}
