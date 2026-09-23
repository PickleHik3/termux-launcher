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
