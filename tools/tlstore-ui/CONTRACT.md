# tlstore-ui contract (Revision 6) — header, Front, Item, Installing

crate tlstore_ui — use tlstore_ui::{app::*, render::*, layout::*, palette::*, picture::*, term::{Event,Key,Mouse,MouseKind,Size,Caps}};
app::run(first: Box<dyn Screen>, Options::default()) -> io::Result<()>  // opens tty, probes (≤150ms), loops, restores; Ctrl-C/SIGTERM quit
trait Screen { draw(&mut self, f:&mut Frame); handle(&mut self, ev:&Event, ctx:&mut Ctx)->Nav; animating()->bool; tick(now,ctx)->bool; watch()->Vec<RawFd> }
Ctx { size: Size{cols,rows,cell_w,cell_h}, caps: Caps, palette: Palette, pics: Pictures, motion: bool /*TLSTORE_MOTION!=0*/, home, cell_known }
Event { Key(Key), Mouse(Mouse{kind,button,col,row,..}), Resize(Size), Tap{action:ActionId,col,row}, Readable(RawFd), Reply(_) }
Frame (fresh blank each draw; all clipped): f.text/text_clip/text_right/text_centred, f.fill, f.hline, f.sized(x,y,s,Sizing,st)->Rect (OSC 66,
  plain when unsupported), f.picture(Placement)->bool (false = no kitty), f.link(rect,url) (OSC 8), f.hit(rect,action), f.header(body_need,pic_rows)
Sizing::scale(n) | Sizing::frac(scale,num,den) {valign 0 top/1 bottom/2 centre}  Style::new().fg().bg().bold().italic().dim().strike()
  .underline(Underline::{Single,Double,Curly,Dotted,Dashed}).ul_color(Rgb)  // strike = SGR 9
Placement::new(&pic,col,row) /*z -1*/ .at_px(x,y,cw,ch) .pid(n) .z(i) .crop(Crop)  Same (pic.id,pid) next frame = moved in place; omitted = deleted.
  An animated picture whose placements all go is deleted outright (`a=d,d=I`, frames freed) and uploaded again if it returns.
Picture::new(w,h,rgba) .id() .width() .height() .rgba() .cells(cw,ch); Picture::animated(w,h,rgba,first_gap_ms,Vec<Cel{gap_ms,rgba}>)
  .is_animated() .gap_ms() .cel_count() /*frames after the first, arrived so far*/ .with_cel(i,|c|..) .complete()
ctx.pics: .script_word(text,px_h,Rgb) /*Pinyon*/ .mark(px,Rgb) .file(path,w,h,Fit) .header_picture(path,w,h,animate) /*Fit::Width (fills the width, crops what is too tall) + bottom 38% fade;
  animate + APNG = a clip: frame 1 now, the rest fitted+faded by a worker thread (picture::apng), thinned to ≤32 MB / ≤48 frames by dropping every
  other frame (gaps folded) until it fits; one clip cached at a time*/ .shape(key, ||(w,h,rgba)) /*cached drawn shape*/;
  picture::shapes::{fade_bottom, pill(w,h,r,Rgb,alpha), progress_line(w,h,line_h,pct,track,fill)}
picture::apng: Apng::open(&bytes)->Option<Apng> (acTL) .size() .frames() .next_frame()->Option<Cel> (full canvas; dispose NONE/BACKGROUND/PREVIOUS,
  blend SOURCE/OVER); stride(frames,frame_bytes); thin(cels,stride); stream(bytes,w,h,fit,fade,stride,&tx) /*Msg::StillGap then Msg::Frame*/
Renderer: render(buf,places,out) diffs; pending() while a clip on screen has frames to send; stream(out) sends one `a=f` frame (i, f=32, s, v, X=1,
  z=gap ms, o=z, chunked m=) per call, then `a=a,i,r=1,z=<frame 1 gap>,s=3,v=1` to loop; app loop calls it between events at STREAM_PACE (one frame
  per 60 fps tick). The launcher's KittyGraphicsProtocol accepts a=f (i,s,v,x,y,z,c,r,X,Y,o,m,q) and a=a (r+z, c, s, v); frame quota RAM/48
  clamped 64–256 MB, folded rather than refused; frames die with the session.

layout: BASE 53×26. tier(cols,rows)->Tier::{Tall ≥40, Base 26–39, Compact <26}; narrow(cols)=cols<44; gutter 2 (1 narrow); item_rows(tier)=3|1
header(cols,rows,body_need,pic_rows:Option<u16>) -> Header { tier, narrow, gutter, cols, rows, content, masthead:0, picture:Option<Rect>,
  name:Rect(3 rows, 2 Compact), standfirst, facts, body:Rect, notice:rows-2, keys:rows-1 }
  Rows: masthead, blank, [picture P, blank], name, standfirst, facts, blank, body, notice, keys. P = rows − 11 − body_need, ≤12, ≤pic_rows
  (the picture's own rows; u16::MAX = reserve), 0 under 4 or when pic_rows is None; Compact never. 53×26 Front: P 8 over 7 rows; 53×40: P 9 over 20.
key_slots(cols) = [2,12,24,34,45] (≥53) | [1,8,16,24,32] (<44) | spread between; key_rooms(cols) = columns per slot.
wrap(s,w,max_lines)/fit_line(s,w): whole words, `…` when cut. centre_x(rect,w).

use tlstore_ui::store::{Router, Store, Go, View, Verb, Job, Gh, Got, Readme, GH_NOTICE, header_for, paint::*, scene::*, readme, motion::Timeline};
Router::new(env) (no motion) | Router::animated(env) (Timeline) | Router::with_motion(env, Box<dyn Motion>); Router::set_clock(Fn()->Instant)
Router is the only app::Screen; views: Front → Item → Installing, stacked; Router::top()/depth()/header_item(); router.st is the Store.
trait View { name()->"front"|"item"|"installing"; draw(&mut self,p:&mut Paint,st:&mut Store) /*header + body*/; keys(&self,st)->Slots;
  handle(&mut self,ev,st)->Go::{Stay,Pass,Push,Replace,Back,Home,Quit}; refresh(&mut self,st) }  Router draws notice + key row after draw().
Store: cat (Catalog: items, updates, info(env,name)), job: Option<Job{verb,names,current,pct,step,done,exit,cancelled}>, gh, stars, notice,
  now (router clock), start_job/cancel_job/job_running, star(name)->bool (GH_NOTICE on the notice row when gh is not ready), open_url/open_repo,
  facts(name,state)->Facts, fetch(Fetch)->Got::{Pending,Ready(path),Failed(code)} (spawns `tlstore picture|readme|readme-asset` once, off the draw path),
  picture_path(name), readme(name)->Readme::{Loading,Doc(Rc<Doc>),NoUpstream /*exit 2*/,Unavailable /*exit 1*/}, asset_path(name,src),
  header_shown(name)/header_rested(name,motion) /*150 ms rest, wake_at ticks the router*/, tasks_pending(), leaving.
header_for(p, st, name, body_need, readme_first, reserve, animate) -> (Header, Option<Picture>)  // fitted+faded picture, placed only once rested;
  animate && ctx.motion: an APNG plays (Front after the 150 ms rest, Item); Installing passes false. TLSTORE_MOTION=0 or --shot: frame 1 only.
paint: Paint{f,fx,scene,clip,scroll}: text/text_clip/right/centred/fill/hline/sized/picture(el,pic,col,row,(off_x,off_y),pid)/hit/link; alpha fades
  toward the surface, pictures hide under 0.5. draw_header(p,&Header,&HeaderContent{masthead: Masthead::{Front{updates,filter},Page{repo,setup}},
  picture, name, standfirst, facts: Facts{state,version,new,more}}); draw_keys(p,&Header,&Slots) /*[Option<Slot{label,words,key,on}>;5]*/;
  draw_notice(p,&Header,text). Taps: A_HOME 1, A_BACK 2, A_CONTEXT 3 (updates / repo), A_ALL 4, A_HEADER 5, A_KEY0+i 10..14; screens use 100+.
readme: parse(md, skip:&[String]) -> Doc{first_image, blocks: Vec<Block>} (drop rules of REVISION-6.md; SKIP_SECTIONS + skip, case-insensitive);
  fit(&Doc, width, repo_url) -> Vec<Row::{Blank, Text{indent,spans,quote}, H2, H3, Code, Image{src,alt}, Hairline, Link{text,url}}>;
  MAX_ROWS 400, CODE_LINES 12, IMAGE_ROWS 6; image_kind(src)->{Inline,Skip,Gif}; wrap_spans(spans,width). Item paints rows lazily (assets on view).
scene: El::{Mark, Context, Picture, Name, Standfirst, Facts, Pill, Row(n), Block(n), Pager, Keys, Notice}; El::is_body() = Pill|Row|Block|Pager.
  Scene{screen, elements: Vec<Element{el,rect,picture:Option<(img,pid)>,text}>, cell}; Scene::body() in drawing order. Effect{alpha, value}.
  trait Motion { navigate(kind:NavKind,from:&Scene,to,now); frame(now,&Scene)->Phase::{Idle,Leaving(Fx),Entering(Fx)}; active(); drawn(now,&Scene)->bool }
motion (D7): leave 120 ms body fade (pictures hidden at once); enter: body element i fades 0→1 over 200 ms from min(30·i,100) ms, rest by 300;
  header/notice/keys never move; Installing count-up 200+6·|Δ| ms (≤700) on Block(0).value. Input goes to the new view at once, during the leave too.
  TLSTORE_MOTION=0: no navigate/frame/drawn, frames at rest, header pictures placed at once.
Measured (tests/screens.rs, 53×26 kitty, push to item): frames ≤ 8 KB, nothing written at rest. Release binary (host x86_64, stripped): 1.41 MB.
Binary: tlstore-ui | --probe | --version. Fixture store: tests/fixtures/store (stub tlstore speaks list/info/update/picture/readme/readme-asset/jobs;
  `picture` answers pics/<name>.png before .jpg, so a test can drop in an APNG).

# preview renderer (cargo feature `shot`; dev only, build-ui.sh never enables it)
tlstore-ui --shot <cols>x<rows> --screen <spec> --out <file.png> [--store <dir>]  |  --shot-all --out <dir> [--store <dir>]
  <spec>: front[:cursor] · front:selected=<a,b> · front:updates · item:<name>[:scroll] · installing:<name>:<pct>; --store defaults to
  tests/fixtures/store (copied to a temp dir, pictures from scripts/tlstore/pictures, gh signed in). --shot-all: front, item:dawn,
  installing:dawn:64 at 53×26, 53×40, 40×24 as <slug>-<cols>x<rows>.png.
Drives the real Router (Caps::all(), motion off, 12×26 px cells), settles every task (refresh, gh, picture, readme, assets) like
  tests/screens.rs, and paints the resting frame: surface, cell backgrounds, placements z<0, glyphs from bundled JetBrains Mono
  (assets/fonts/jetbrainsmono, OFL) with bold/italic/dim/reverse, underline styles + colour, strike, OSC 66 runs at scale×num/den
  with their alignment, then placements z≥0 with their alpha. ★/☆ drawn by hand; other missing glyphs are boxes.
shot::{Spec::parse, render(cols,rows,&Spec,store)->Image{width,height,rgba,pixel(),png()}, shoot(..,&Path), shoot_all(dir,store), cli(args)}
  installing:<name>:<pct> uses Store::fake_job(verb,name,pct) + Router::show_installing() (both cfg(feature="shot"); no script runs).
Test: tests/shot.rs (`cargo test --features shot`): front 53×26 is a PNG with ink on the name rows.
