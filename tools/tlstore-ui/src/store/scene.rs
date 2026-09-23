//! Motion hooks. Every screen records the elements it drew (a [`Scene`]) and draws each one
//! through an [`Effect`] looked up in the frame's [`Fx`]. The router asks one [`Motion`] per
//! frame which view to draw and with which effects, and tells it about every navigation.
//! P3/P4 ship [`NoMotion`]: everything at rest, navigation instant.

use std::collections::HashMap;
use std::time::Instant;

use crate::render::Rect;

/// A named part of a screen that motion can move, crop, fade or reveal.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum El {
    /// Masthead: the pixel mark, the breadcrumb text, the right-hand context item.
    Mark,
    Crumb,
    Context,
    /// The hairline rule under the masthead.
    Rule,
    /// Hero: the spaced lead line and the script word picture.
    HeroLead,
    HeroWord,
    /// A screen's cover picture (Apps: the featured item; Item: the item's own).
    Cover,
    /// The caption over/under the cover.
    Caption,
    /// Apps: the APPS header, the category chips (or the search line), the pager, the
    /// selection bar.
    Header,
    Chips,
    Pager,
    SelBar,
    /// The n-th list row on screen (Apps rows, Updates entries), 0-based from the top.
    Row(u16),
    /// The n-th block of a text page (Item: line, standfirst, star rule, facts, …; Installing:
    /// number, bar, steps, notes), 0-based in drawing order.
    Block(u16),
    /// The key-hint row and the one-line notice above it.
    Keys,
    Notice,
}

/// One drawn element: where it landed and, for pictures, which kitty placement it is.
#[derive(Clone, Debug, PartialEq)]
pub struct Element {
    pub el: El,
    pub rect: Rect,
    /// `(picture id, placement id)` when the element is a picture on screen.
    pub picture: Option<(u32, u32)>,
    /// The element's text at rest (breadcrumb, lead, row name, …), when it has one.
    pub text: Option<String>,
}

/// What a view drew in its last frame, in drawing order.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Scene {
    /// "apps", "item", "updates", "installing", "nogh".
    pub screen: &'static str,
    pub elements: Vec<Element>,
}

impl Scene {
    pub fn new(screen: &'static str) -> Scene {
        Scene { screen, elements: Vec::new() }
    }
    pub fn push(&mut self, el: El, rect: Rect, picture: Option<(u32, u32)>, text: Option<&str>) {
        self.elements.push(Element { el, rect, picture, text: text.map(str::to_string) });
    }
    pub fn get(&self, el: El) -> Option<&Element> {
        self.elements.iter().find(|e| e.el == el)
    }
}

/// How one element is drawn this frame. The default is "at rest".
#[derive(Clone, Debug, PartialEq)]
pub struct Effect {
    /// Offset in pixels (pictures move by pixels; text moves by whole cells, rounded).
    pub dx: i32,
    pub dy: i32,
    /// Fraction of the element shown from its top edge, 0.0–1.0 (a mask rise or a wipe down:
    /// pictures are cropped in pixels, text by whole rows).
    pub shown: f32,
    /// 0.0 invisible – 1.0 opaque. Text colours mix toward the page; pictures are hidden
    /// below 0.5 (kitty has no per-placement alpha).
    pub alpha: f32,
    /// Fraction of the text's characters drawn, 0.0–1.0 (dot leaders drawing out, typing).
    pub reveal: f32,
    /// Replacement text for this frame (the breadcrumb decoding from ░▒▓ glyphs). Same width
    /// as the text at rest.
    pub text: Option<String>,
}

impl Default for Effect {
    fn default() -> Effect {
        Effect { dx: 0, dy: 0, shown: 1.0, alpha: 1.0, reveal: 1.0, text: None }
    }
}

impl Effect {
    pub fn at_rest(&self) -> bool {
        *self == Effect::default()
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
    /// Deeper: Apps → Item, Item → Installing, …
    Push,
    /// Back one level.
    Pop,
    /// Swapped in place.
    Replace,
    /// Straight back to Apps (the mark).
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

/// The timeline P5 plugs into the router.
pub trait Motion {
    /// A navigation just happened. `from` is the leaving view's last drawn scene; `to` names
    /// the entering screen. Only called when motion is on (`Ctx::motion`).
    fn navigate(&mut self, kind: NavKind, from: &Scene, to: &'static str, now: Instant);
    /// Called before every frame while [`Motion::active`]; `current` is the current view's
    /// last drawn scene (empty on its very first frame).
    fn frame(&mut self, now: Instant, current: &Scene) -> Phase;
    /// True while anything moves; the app then ticks at 60 fps.
    fn active(&self) -> bool;
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
