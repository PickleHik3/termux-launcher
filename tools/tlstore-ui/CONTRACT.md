# tlstore-ui core contract (P3, 08e24e2d) — for the screens phase

crate tlstore_ui — use tlstore_ui::{app::*, render::*, layout::*, palette::*, picture::*, term::{Event,Key,Mouse,MouseKind,Size,Caps}};
app::run(first: Box<dyn Screen>, Options::default()) -> io::Result<()>  // opens tty, probes (≤150ms), loops, restores; Ctrl-C/SIGTERM quit
trait Screen { fn draw(&mut self, f:&mut Frame); fn handle(&mut self, ev:&Event, ctx:&mut Ctx) -> Nav;
  fn animating(&self)->bool {false}  // true → tick() every 16.7ms
  fn tick(&mut self, now:Instant, ctx:&mut Ctx)->bool {false}  // return true = redraw (timelines live here)
  fn watch(&self)->Vec<RawFd> {vec![]} }  // readable fd → Event::Readable(fd); screen reads it (child --progress pipe)
enum Nav { Stay, Push(Box<dyn Screen>), Replace(Box<dyn Screen>), Pop /*last pops → quit*/, Quit }
struct Ctx { size: Size{cols,rows,cell_w,cell_h}, caps: Caps, palette: Palette, pics: Pictures, motion: bool /*TLSTORE_MOTION!=0*/, home: PathBuf }
Caps { kitty_graphics, text_sizing, sync_output, in_band_resize, cell_px:Option<(u16,u16)>, background:Option<Rgb>, answered }
enum Event { Key(Key), Mouse(Mouse{kind,button,col,row,..}), Resize(Size), Tap{action:ActionId,col,row}, Readable(RawFd), Reply(_) }
enum Key { Char(c), Alt(c), Ctrl(c), Enter, Esc, Tab, BackTab, Backspace, Delete, Up, Down, Left, Right, Home, End, PageUp, PageDown }
MouseKind { Press, Release, Drag, Move, ScrollUp, ScrollDown }  // Tap = left Press+Release on the same hit region
Frame (fresh blank each draw; all clipped): f.ctx, f.cols()/rows()/area(), f.pal(), f.tier(), f.narrow(), f.regions()->Regions
  f.text(x,y,s,Style)->end_x  f.text_clip(x,y,s,st,max_x)  f.text_right(right_x,y,s,st)->start_x  f.text_centred(rect,y,s,st)
  f.fill(rect,st)  f.hline(x0,x1,y,ch,st)  f.leaders(x0,x1,y,st)
  f.sized(x,y,s,Sizing,st)->Rect  // OSC 66; plain text if !caps.text_sizing or it doesn't fit
  f.picture(Placement)->bool      // false if !caps.kitty_graphics → draw a text stand-in
  f.hit(rect, action:ActionId=u32)  // register after drawing; later = topmost
  Later writes win: text over a sized run removes the run; over half a wide char blanks it.
Sizing { scale:1-7, width:0-7, num,den:0-15, valign,halign }; Sizing::scale(n), Sizing::frac(s,n,d)
Style::new().fg(Rgb).bg(Rgb).bold().italic().dim().reverse().underline(Underline::{Single,Double,Curly,Dotted,Dashed}).ul_color(Rgb); Color::Default
Placement::new(&pic,col,row) /*z -1*/ .at_px(x_px,y_px,cell_w,cell_h) .pid(n) .z(i32) .fit(cols,rows) .crop(Crop{x,y,w,h})
  Same (pic.id,pid) next frame = moved/re-cropped in place; omitted = deleted; upload once per picture.
Picture: Rc clone; .id() .width() .height() .rgba() .cells(cw,ch); Picture::new(w,h,rgba)
ctx.pics: .script_word(text, px_h, Rgb) /*Pinyon, px_h tall*/  .mark(px, Rgb) /*27px×5px*/  .file(&Path, w_px, h_px, Fit::{Contain,Cover})
layout: tier(cols,rows)->Tier::{Full ≥40, Strip 28–39, Compact <28}; narrow(cols)=cols<44; centre_x(rect,w); spaced_caps(s)
Regions { tier, narrow, gutter, masthead, rule, hero, hero_lead, hero_word, body, cover_rows /*Full ≤12, Strip ≤5, Compact 0*/, keys }
  52×45: masthead y1, rule y2, lead y4, word y5–7, body y9–41, keys y43.  52×23: masthead y0, rule y1, hero y2, body y4–20, keys y22.
Palette { dark, accent, on_accent, tonal, on_tonal, surface, ink, dim, rule } + ink_s() dim_s() accent_s() rule_s() tonal_s() accent_fill_s()
  from ~/.termux/material-colors-{dark|light}.properties; dark = OSC 11 background, else exported mode, else dark
Reference screen: src/demo.rs. Binary: tlstore-ui --demo | --probe | --version.

# tlstore-ui screens contract (P4) — for the motion phase

use tlstore_ui::store::{Router, scene::*};  Router::new(Env::from_env()) | Router::with_motion(env, Box<dyn Motion>)
Router is the only app::Screen; every navigation goes through Router::navigate (Push/Pop/Replace/Home from views' Go).
trait Motion { fn navigate(&mut self, kind:NavKind, from:&Scene, to:&'static str, now:Instant);  // only when ctx.motion
  fn frame(&mut self, now:Instant, current:&Scene) -> Phase;  // called each frame while active()
  fn active(&self) -> bool }  // true → app ticks 60 fps; Router::animating()/tick() delegate here
enum Phase { Idle, Leaving(Fx) /*router draws the leaving view (kept alive) with Fx*/, Entering(Fx) /*current view*/ }
  Leaving ends (and the old view is dropped) on the first frame that returns Entering or Idle. Input goes to the new view at once.
enum NavKind { Push, Pop, Replace, Home }
Scene { screen:"apps"|"item"|"updates"|"installing"|"nogh", elements:Vec<Element{el,rect,picture:Option<(img_id,pid)>,text}> }
  recorded every frame in drawing order; Router.scene = current view's last frame; `from` = leaving view's last frame.
El: Mark Crumb Context Rule HeroLead HeroWord Cover Caption Header Chips Pager SelBar Row(n on screen) Block(n) Keys Notice
  Block(n): Item 0 line,1 standfirst,2 star rule,3 facts,4 what it does,5 try it,6 demo,7 notes (absent slots shift down);
            Installing 0 number,1 dot bar,2-5 steps,6 "with part",7 note/summary,8 next; NoGh 0 star rule,1 hairline,2 text,3 commands.
Fx::default() = at rest; fx.set(El, Effect{dx,dy /*px*/, shown /*0-1 from top: mask rise, wipe*/, alpha, reveal /*0-1 chars, leaders*/, text /*crumb glyphs*/})
  Pictures move by pixels and crop by `shown`; alpha<0.5 hides a picture. Text moves by whole cells, fades toward surface,
  `reveal` truncates text and draws leaders/hlines out from the left; `text` replaces a text element for the frame.
In-screen motion (install count-up, rows on filter change) uses the same Fx from frame(); job state: router.st.job (pct, step, done).
Motion off: Router passes ctx.motion; with it false navigate() is never called and frame() never runs.

# tlstore-ui motion contract (P5) — for the ship phase and later screens

use tlstore_ui::store::{Router, motion::{Timeline, ease, timing, decode, GLYPHS}};
Router::animated(env) = with_motion(env, Box::new(Timeline::new())); main.rs uses it. Router::new stays motionless.
Router::set_clock(Fn()->Instant)  // tests drive transitions frame by frame; navigate() and draw() read it
Motion gains fn drawn(&mut self, now, current:&Scene)->bool {false}: called after every drawn frame while
  ctx.motion; true → the router clears the Frame (Frame::clear) and draws it again through a fresh frame().
  Used for: first look at an entering view's layout, the first view's own entry, rows changed in place, a
  new install percentage. `current` is empty while a leaving view is drawn (its scene goes to leave_scene).
Effect gains line:f32 (leaders/hlines drawn out, text untouched; they use min(reveal,line)) and
  value:Option<f32> (Installing draws Block(0)'s number and its dot bar from it; the scene keeps the target).
Scene gains cell:(w,h) px. Pictures hidden or moved by an effect are still recorded, at their rest rect.
Row text in the scene = the first text drawn for it (Apps: the item number, drawn before the ✓ mark).
Timeline (ms): leave 160 — all but Mark/Crumb/Context fade (alpha in 1/16 steps); pictures alpha 1-p, lift
  0.4 row. Enter ≈820 after it: Crumb decode 12×32 · Rule reveal 600 out · HeroLead fade 500 @60 ·
  HeroWord picture: spring 600 @100 as dy=h(1−s), shown=min(s,1); text: fade · Cover picture: shown dram
  700 @120; stand-in: fade · Caption @180, Header/Chips @225 fade 300 · Row(i) @260+min(45, 240/(n−1))·i,
  fade 300, leaders 60 behind over 240 · Pager after the last row · Block(b) @180+min(45,300/(n−1))·b
  (Installing Block 0/1: count-up instead) · Keys, SelBar, Notice, Mark, Context at rest.
  Every NavKind plays the same; a navigation mid-transition restarts from the view as last drawn.
In-screen: Apps rows whose numbers change (filter, category, page, resize) re-stagger within 200 ms;
  Installing counts to each new pct from what is shown over 200+6·|Δ| ms (≤700), out easing.
Motion off (ctx.motion false = TLSTORE_MOTION=0): no navigate/frame/drawn calls, no ticks, frames at rest.
  The launcher's animations-off is not visible to shell programs (see the P5 report); nothing reads it yet.
Measured (tests/screens.rs, 52×45 kitty, push to item): max 2.7 KB per frame, mean 0.46 KB, uploads apart;
  cover-only frames ≈81 B with no text.
