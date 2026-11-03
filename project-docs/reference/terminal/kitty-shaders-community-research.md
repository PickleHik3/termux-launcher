# kitty custom shaders: the community side

Date: 2026-10-22. Status: research only. Nothing is built, and no decision is recorded here.
Companion to [kitty-custom-shaders-research.md](kitty-custom-shaders-research.md), which covers the
feature itself (config keys, Slang, render pipeline, frame pacing) and its fit for termux-launcher
(AGSL, API levels, battery, licensing). This note does not repeat those. It fills the community gaps
listed in that note's section 6.

Anything marked **(inference)** is my reading and has no source. **(unverified)** means not checked.
Star counts and dates are from the GitHub REST API on 2026-10-22. Reddit scores and comment counts
were read live on old.reddit.com in a real browser on 2026-10-22 (section 6).

## 0. Answer in brief

- **The community is five weeks old and small.** kitty 0.49.0 shipped custom shaders on 2026-09-21
  (existing note, section 1.1). Nearly every kitty-native collection was created after 2026-09-09 and has
  0 to 6 stars. The only large collections are Ghostty ones.
- **Cursor trails dominate; CRT is second; animated backgrounds are rare.** Trails are the one effect
  that exists in every collection. Backgrounds are single repos (sharks, star-nest, clouds).
- **Ghostty shaders are ported by hand, not run.** The ports exist for trails
  ([sahaj-b](https://github.com/sahaj-b/cursor-shaders),
  [cursor-blaze](https://github.com/MarphDE-82/cursor-blaze)). The big Ghostty libraries
  ([hackr-sh](https://github.com/hackrdotsh/ghostty-shaders), 1547 stars) have no kitty port that I found.
- **The reported problems are small and mostly not performance.** The recurring ones are version
  floor, `slangc` as a dependency, `cursor_trail` having to be on, black-background and opacity
  assumptions, and one real power measurement: a live 60 fps CRT costs about +16 W of GPU on one
  laptop, while a still shader costs nothing. On Reddit, users report GPU use as anecdotes: an iGPU
  spiking to 25%+ (about 2.2 W) with a CRT and sparks, `northern-lights` pinning a GPU at 100%, and
  "on battery ... you are fucked". Two users report kitty freezing after the 0.49 update.
- **Reddit is lively but recent.** r/KittyTerminal has about 20 shader-related threads. Since 0.49
  there have been showcase posts scoring 83 to 217 points (top: "I can't stop playing around with
  different shaders", 217). Before that, the most popular were the built-in cursor trail (253) and
  the LD_PRELOAD CRTty (127). One commenter asks for a separate KittyShader subreddit.

## 1. Collections

All kitty-native entries need kitty 0.49.0 or newer and are written in Slang. Licences are as reported
by the GitHub API unless I read the file.

| Repo | Stars, pushed | Licence | Effects | Kind |
|---|---|---|---|---|
| [sahaj-b/cursor-shaders](https://github.com/sahaj-b/cursor-shaders) | 1170, 2026-10-03 | MIT | Cursor trails (warp, sweep, tail); ripple and boom pulses are Ghostty-only | Ghostty originals, plus a kitty trails-only port in `kitty/` |
| [hackrdotsh/ghostty-shaders](https://github.com/hackrdotsh/ghostty-shaders) | 1547, 2026-07-13 | None declared (no licence file, GitHub reports none) | About 40 `.glsl`: `crt`, `bettercrt`, `bloom`, `cursor_blaze`, `inside-the-matrix`, `fireworks`, `galaxy`, `starfield`, `tft`, `glitchy` | Ghostty only. Install is `cp x.glsl ~/.config/ghostty/shaders/` ([Readme.md](https://github.com/hackrdotsh/ghostty-shaders/blob/HEAD/Readme.md)) |
| [kosa12/CRTty](https://github.com/kosa12/CRTty) | 63, 2026-09-06 | MIT | CRT (scanlines, curvature, phosphor glow, chromatic aberration), greyscale, invert, custom `.glsl` | Not a kitty feature: an `LD_PRELOAD` library that hooks buffer swaps. Works on kitty before 0.49 |
| [AxDSan/kitty-crt](https://github.com/AxDSan/kitty-crt) | 1, 2026-09-29 | MIT | Ten-pass CRT: bloom, halation, afterglow, bezel, five monitor colours | kitty-native Slang |
| [noomz/kitty-shaders](https://github.com/noomz/kitty-shaders) | 0, 2026-10-18 | GPL-3.0 | 18: trails (comet, nyan, sparks, warp), bell-glitch, bell-shake, click-sonar, focus-mode, vhs, phosphor, snow, idle-starfield, tab-slide | kitty-native. README marks each as "Original design", "Inspired by sahaj-b ... (MIT)" or "Ideas from Microsoft Terminal ... (MIT)" |
| [zxzxn3/kitty-shaders](https://github.com/zxzxn3/kitty-shaders) | 0, 2026-10-15 | GPL-3.0 plus CC-BY-NC-SA-3.0 and an MIT Star Nest text in `LICENSES/` | 13 pipelines: ink-stroke trail, star-nest, star-hash, cloud-bg, crt-warp, focus-pulse | kitty-native. Has a `THIRD-PARTY-NOTICES.md`, which is rare here |
| [MarphDE-82/cursor-blaze](https://github.com/MarphDE-82/cursor-blaze) | 0, 2026-09-10 | NOASSERTION | Four trail variants | The same effects as GLSL and Slang side by side, "1:1" |
| [Jaleel-VS/kitty-ascii-sharks](https://github.com/Jaleel-VS/kitty-ascii-sharks) | 1, 2026-10-20 | MIT | ASCII sharks swimming behind text | kitty-native, procedural, tested on macOS |
| [lessucettes/ume-kaze](https://github.com/lessucettes/ume-kaze) | 0, 2026-10-19 | GPL-3.0 | Petals revealed by the terminal bell | kitty-native |
| [atomicstack/kitty-shaders](https://github.com/atomicstack/kitty-shaders) | 0, 2026-10-04 | None declared | Confetti on cursor move, "shatter" cursor burst | kitty-native port of Vercel's `hyperpower` "wow mode" |
| [oleg-koval/kitty-rainbow-trail](https://github.com/oleg-koval/kitty-rainbow-trail) | 6, 2026-10-21 | MIT | Rainbow cursor trail | Not a shader: a Python CLI that cycles `cursor_trail_color` through `kitty @ load-config` |

Other Ghostty collections seen in a GitHub search (no kitty port checked):
[KroneCorylus/ghostty-shader-playground](https://github.com/KroneCorylus/ghostty-shader-playground) (504, MIT),
[luiscarlospando CRT shader](https://github.com/luiscarlospando/crt-shader-with-chromatic-aberration-glow-scanlines-dot-matrix) (81, MIT),
[lex-ghostty-shaders](https://github.com/lexrus/lex-ghostty-shaders) (71, no licence). Not an awesome-kitty
list: the only curated list found is
[Awesome-Ghostty](https://github.com/fearlessgeekmedia/Awesome-Ghostty) (278).

Licence note, relevant to the existing note's section 4.3: the biggest library (hackr-sh) declares no
licence, so its files are "all rights reserved" by default **(inference, not legal advice)**. Its
`inside-the-matrix` and `fireworks` share names with kitty's shipped shaders, which that note already
flags as Shadertoy ports with unclear provenance.

### 1.1 Ghostty to kitty compatibility

- The maintainer says Ghostty/Shadertoy GLSL won't run: "adding GLSL would not make shaders from X
  other product compatible since the uniforms and pipeline architecture will remain different"
  ([issue 10344 comment](https://github.com/kovidgoyal/kitty/issues/10344#issuecomment-5656761745),
  quoted in the existing note, section 2.1).
- The cursor-blaze README says the same from the porter's side: Ghostty uses `mainImage()` with
  `iCurrentCursor`, `iPreviousCursor`, `iTime`, `iFocus`; kitty uses `fragment_main(color, textures, data)`
  and reads cursor state from `KittyCustomShaderData`
  ([README](https://github.com/MarphDE-82/cursor-blaze/blob/HEAD/README.md)).
- Behaviour differences the ports run into, all from READMEs:
  - kitty only feeds cursor data to shaders while `cursor_trail` is on, and subscribing to trail events
    hides kitty's own trail ([zxzxn3 README](https://github.com/zxzxn3/kitty-shaders/blob/HEAD/README.md),
    [atomicstack README](https://github.com/atomicstack/kitty-shaders/blob/HEAD/README.md)).
  - `cursor_trail_start_threshold` defaults to 2 cells, so typing doesn't trigger a trail. The ports
    set it to 0 to match Ghostty ([cursor-blaze](https://github.com/MarphDE-82/cursor-blaze)).
  - Cursor shape-change effects (Ghostty "boom" and "ripple") can't be ported: with the trail
    inactive, kitty 0.49.2 exposes no cursor to the shader
    ([sahaj-b README](https://github.com/sahaj-b/cursor-shaders/blob/HEAD/README.md)).
- The Ghostty to kitty direction is trails only so far. Backgrounds and CRTs for kitty are written
  fresh or ported from Shadertoy, not from the Ghostty libraries **(inference from the table above)**.

## 2. Notable recipes

Config lines below are copied from the repos' READMEs and pipeline files.

**Cursor trail, ported from Ghostty** ([sahaj-b](https://github.com/sahaj-b/cursor-shaders/blob/HEAD/README.md)):

```conf
cursor_trail 1
custom_shaders cursor-warp   # or cursor-sweep, cursor-tail
```

**Trail on every keystroke** ([cursor-blaze](https://github.com/MarphDE-82/cursor-blaze/blob/HEAD/README.md)):

```conf
cursor_trail 1
cursor_trail_start_threshold 0
custom_shaders perfection
```

**Built-in CRT, one line.** A comment in an r/KittyTerminal showcase thread: "just need to have the latest
kitty edition and put this at the end of your kitty.conf = `custom_shaders crt`"
([thread](https://www.reddit.com/r/KittyTerminal/comments/1x14osz/the_crt_shader_is_really_nice/),
2026-10-08, 120 points).

**Stacking built-ins.** `custom_shaders` takes a space-separated list. In two threads, users had
assumed it took only one shader
([showcase](https://www.reddit.com/r/KittyTerminal/comments/1wrw08z/showcase_with_new_custom_shaders_in_kitty_0_49/),
[late to the party](https://www.reddit.com/r/KittyTerminal/comments/1wse3pa/it_seems_im_late_to_the_party_kitty_049_custom/)):

```conf
custom_shaders northern-lights cursor-trail-blaze
```

The same reply warns that `northern-lights` "is the heaviest shader, it wont work well unless you have a
beefy machine". The poster said it drove GPU use to 100% and froze their livestream. For mixing
shaders with parameters, users point to pipeline files
([docs](https://sw.kovidgoyal.net/kitty/custom-shaders/), and a
[how-to video post](https://www.reddit.com/r/KittyTerminal/comments/1wuf2lx/create_your_own_shaders_in_kitty_0_49/)
covering pipelines, Slang and a Mandelbrot shader). The built-ins demoed in the 18-shader showcase
include bloom, crt, crt-blue, cursor-trail-blaze, cursor-trail-lightning, dim-inactive-windows,
fireworks, fireworks-rockets and focus-highlight. The post body is cut off after that. The CRT's curve is a built-in option. The maintainer's answer to a request for a flat
version was "The shader already has the option to do just that. Use it."
([kitty#10502](https://github.com/kovidgoyal/kitty/issues/10502)).

**CRT with a power switch** ([kitty-crt](https://github.com/AxDSan/kitty-crt/blob/HEAD/README.md)):
a 10-pass monitor with a "live" mode (60 fps afterglow, snow, flicker) and a "still" mode that redraws
only on change. It binds `ctrl+shift+f12` to swap them and recommends still on battery.

**Animated background, focus-gated** ([ascii-sharks](https://github.com/Jaleel-VS/kitty-ascii-sharks/blob/HEAD/README.md)):

```
startgroup
    animation_start os-window-focus-in
    animation_stop os-window-focus-out
    animation_step 33
    var int NUM_SHARKS = 6
    shaders ascii-sharks
endgroup
```

It claims to cost "nothing while the window is unfocused". This is the same pattern as kitty's own
backgrounds (existing note, section 1.4).

**Event-driven, no idle cost** ([ume-kaze](https://github.com/lessucettes/ume-kaze/blob/HEAD/README.md)):
petals appear on the terminal bell, and "no animation frames are requested by this shader while it is
inactive".

**Everyday-use set.** A video-only post by the author of five "boring" shaders (find-cursor, focus-line,
a calmer `cursor-trail-blaze`, a long-line marker, a rectangular spotlight), built on kitty's shipped
trail and spotlight shaders
([thread](https://www.reddit.com/r/KittyTerminal/comments/1x4d5xl/old_manboring_shaders_for_daily_programming/),
2026-10-12, 56 points; sources in
[FractalCodeRicardo/dev-config](https://github.com/FractalCodeRicardo/dev-config/tree/master/kitty/shaders)).
The post's framing is that most shaders "look amazing" but "are not really useful for daily
programming".

**More sources linked from threads** (not read): [cursor-smith](https://github.com/Sadsnake1/cursor-smith),
a "Cursor Engine" that commenters want ported to kitty, and
[Ghostty-Nyan-Shader](https://github.com/guysoft/Ghostty-Nyan-Shader), whose author added kitty
support. There is also a full dotfiles setup with a CRT and a hand-written typing-sparks shader
([ryu-mirror/kitty](https://github.com/RyuZinOh/ryu-mirror/tree/main/kitty)), from the 177-point
[Kitty shader :3](https://www.reddit.com/r/KittyTerminal/comments/1wthm72/kitty_shader_3/) thread.

**Particle-style effects** are the most elaborate: atomicstack's confetti uses a ring buffer of spray
events kept in the `persist` texture and tile caching, all in shader code
([README](https://github.com/atomicstack/kitty-shaders/blob/HEAD/README.md)).

## 3. r/KittyTerminal themes

Read live on old.reddit.com (section 6). Format: date, points, comments.

| Theme | Threads | Strength of evidence |
|---|---|---|
| Announcement and curiosity | ["Custom shaders for kitty"](https://www.reddit.com/r/KittyTerminal/comments/1vnv8zr/custom_shaders_for_kitty/) (2026-08-14, 25, 4) points at RFC #10344 and says the events suit "mouse clicks or window/tab transitions" for screencasts. Replies: "fractals, cellular automata?" ("yes, check shadertoy") | One thread |
| Showcases after 0.49 (the bulk) | [I can't stop playing around with different shaders](https://www.reddit.com/r/KittyTerminal/comments/1x5yepi/i_cant_stop_playing_around_with_different_shaders/) (10-14, 217, 14), [Kitty shader :3](https://www.reddit.com/r/KittyTerminal/comments/1wthm72/kitty_shader_3/) (09-29, 177, 37), [crt shader is really nice](https://www.reddit.com/r/KittyTerminal/comments/1x14osz/the_crt_shader_is_really_nice/) (10-08, 120, 2), [18-shader showcase video](https://www.reddit.com/r/KittyTerminal/comments/1wrw08z/showcase_with_new_custom_shaders_in_kitty_0_49/) (09-27, 83, 11), [lightning trail + particles](https://www.reddit.com/r/KittyTerminal/comments/1x7ktz7/im_trying_to_tweak_the_cursortraillighting_shader/) (10-16, 42, 5), [late to the party](https://www.reddit.com/r/KittyTerminal/comments/1wse3pa/it_seems_im_late_to_the_party_kitty_049_custom/) (09-28, 28, 4), [I'm the last in the shaders thing](https://www.reddit.com/r/KittyTerminal/comments/1xc0tnw/im_the_last_in_the_shaders_thing_for_kitty/) (10-21, 9, 0). Replies are mostly "how do I do this?" (answered with the docs link or "`custom_shaders crt`") and requests for dotfiles. One reply suggests a dedicated KittyShader subreddit. One poster: "it can get pretty distracting after a while" | Recurring. The strongest signal in the sub: a ricing wave, with "golden age of ricing" said twice |
| Practical / calm shaders | [Old man/boring shaders](https://www.reddit.com/r/KittyTerminal/comments/1x4d5xl/old_manboring_shaders_for_daily_programming/) (10-12, 56, 1): find-cursor, focus-line, a long-line marker | One post, a counter-current to the showcases |
| CRT / retro look | [CRTty](https://www.reddit.com/r/KittyTerminal/comments/1r32ljs/crtty_dropin_crt_shader_for_kitty/) (2026-02-12, 127, 16; "the curve seems a bit exaggerated", since ported to Alacritty), plus the built-in `crt` showcases above | Recurring: the most-requested look, and the only one with a pre-0.49 community answer |
| Cursor trail as usability vs distraction | [Neovide-like cursor animation](https://www.reddit.com/r/KittyTerminal/comments/1g7vkwt/neovide_like_cursor_animation_in_kitty_terminal/) (2024-10-20, 253, 45; the built-in trail's merge announcement). Top comment: "quite distracting. How do you manage to write code with this on?" Replies say it helps find the cursor after jumps, splits and mode changes; one says "in minutes ... my eyes started hurting". [Kitty Cursor Trails](https://www.reddit.com/r/KittyTerminal/comments/1typh32/kitty_cursor_trails/) (2026-06-06, 42) argues the same orientation benefit. In a Kitty-vs-Ghostty thread one user says the trail is why they picked kitty | Recurring, and the most-discussed taste question |
| Asking for bloom or CRT before 0.49 | [bloom around my text, and maybe a crt effect?](https://www.reddit.com/r/KittyTerminal/comments/1qqq7sh/is_there_any_way_to_add_bloom_around_my_text_and/) (2026-01-30, 3). Replies: "No, there isnt ... kitty is all about modernising terminals not ageing them" and "check out the source code and alter the shaders" | Two comments. It is the baseline: before 0.49 the answer was "no" |
| Cursor trail | Built-in trail questions ([not working](https://www.reddit.com/r/KittyTerminal/comments/1k25r5r/cursor_trail_not_working/) 2025-04-18; [where is the trailing cursor](https://www.reddit.com/r/KittyTerminal/comments/1wceauz/where_is_the_trailing_cursor/) 2026-09-10; [customize the trailing](https://www.reddit.com/r/KittyTerminal/comments/1nhyxpg/is_it_possible_to_customize_the_cursor_trailing/) 2025-09-16), a [rainbow-cycling trail](https://www.reddit.com/r/KittyTerminal/comments/1umgy85/made_kittys_cursor_trail_rainbowcycling_instead/) (2026-07-03, 7) | Recurring. The answers are "add `cursor_trail 1`" and "put 5 for best experience" |
| Animated backgrounds | `northern-lights` and `fireworks` appear in showcases. The only thread about a background in its own right is a "too heavy" report (section 4). [cbirds](https://www.reddit.com/r/KittyTerminal/comments/1x8f0fq/pipe_any_text_into_cbirds_and_the_letters_fly_as_a_flock/) (10-17, 91) animates sprites over kitty's graphics protocol and is not a shader | Rare as a topic |

Reading the threads together **(inference)**: the people who post about shaders want a look (CRT,
trail), not a framework. The few real "how do I" questions are about enabling a built-in.

## 4. Reported problems

| Problem | Evidence | Strength |
|---|---|---|
| Power cost of continuous redraw | kitty-crt measured "live" at +4 to +21 W GPU (median +16 W), 7-11% of one core, against 0% and no change for "still". Frame interval 16, 33 or 50 ms made no difference, so it is a power-state change, not a per-frame cost ([performance.md](https://github.com/AxDSan/kitty-crt/blob/HEAD/docs/performance.md)). One laptop (RTX 2070 Max-Q, Wayland, 144 Hz) | Single measurement, by the author, with the method shown. Complements the maintainer's numbers in the existing note, section 1.4 |
| Idle behaviour | Everyone who mentions it says the same: stop animation on focus-out or idle (sharks, ume-kaze, kitty's own). The community has not complained about idle drain | Recurring design pattern; no complaints found |
| Version floor | Every kitty-native README states "0.49.0 or newer"; ume-kaze says 0.49.2. cursor-blaze notes a `kitty-git` build "doesn't bump its version string" and checks the option schema instead. 0.49.1 and 0.49.2 matter (existing note, section 1.1) | Recurring |
| `slangc` as a dependency | FreeBSD port failed: "shader slang compiler not found". The maintainer: slang is "a hard build time (and soft runtime) dependency", and advised copying prebuilt shaders from a Linux build ([kitty#10529](https://github.com/kovidgoyal/kitty/issues/10529), 2026-09-24) | One report; consistent with the existing note, section 1.2 |
| First-start build time | kitty-crt: cold shader cache takes "5-10 s (kitty builds 11 modules with `slangc`, one after another)"; a pipeline change froze the window about 6 s without its helper | Single repo, measured |
| Opacity and background assumptions | kitty-crt: opaque windows only ("a shader that moves pixels cannot work with `background_opacity` below 1"), and kitty's background must be `#000000` since the monitor choice is carried in it. kitty's `dim-inactive-windows` does nothing on an already black background ([kitty#10511](https://github.com/kovidgoyal/kitty/issues/10511), 2026-09-22) | Two sources |
| Compositor and platform | macOS black borders with shaders ([kitty#10524](https://github.com/kovidgoyal/kitty/issues/10524)), and 0.49 regression on old macOS AMD GPUs (existing note, section 1.4). I found no Wayland compositor transparency complaint specific to shaders **(unverified)**. kitty-crt tested only on Fedora/KDE/Wayland/NVIDIA | Few reports |
| Mouse mapping under distortion | With CRT curvature 0.06 clicks are off by about 29 px at the corners, because kitty maps the mouse before the shader bends the picture ([README](https://github.com/AxDSan/kitty-crt/blob/HEAD/README.md)). A kitty PR to remap pointer events for such shaders is #10639 (title only read) | One repo |
| Time precision | `timestamp` lost precision after hours of uptime, so animations got jerky. Fixed on master 2026-10-04 ([kitty#10595](https://github.com/kovidgoyal/kitty/issues/10595)) | One bug, fixed |
| Misreads of built-ins | `tab-change` "makes the tab black": it is a wipe and needed content to show ([kitty#10504](https://github.com/kovidgoyal/kitty/issues/10504)). `dim-inactive-windows` "does nothing" ([kitty#10503](https://github.com/kovidgoyal/kitty/issues/10503)) | Recurring in the first week: discoverability, not bugs |
| Config discovery | `~` not expanded in `custom_shaders` paths ([kitty#10694](https://github.com/kovidgoyal/kitty/issues/10694), [#10467](https://github.com/kovidgoyal/kitty/issues/10467)) | Two issues |
| CRTty install pain (pre-0.49 route) | In the [thread](https://www.reddit.com/r/KittyTerminal/comments/1r32ljs/crtty_dropin_crt_shader_for_kitty/): a CachyOS user sees static lines but no animation; a macOS build fails with "Undefined symbol for architecture arm64"; another user says it "makes my kitty unusable and it opens another kitty"; the author's answer is "very dependant on setup". (Pullpush's copy also had a Fedora `cargo` error, which the live thread no longer shows) | Single comments |
| Heavy built-ins and GPU use (Reddit) | `northern-lights` + `cursor-trail-blaze`: "GPU usage goes up to 100%, livestream froze". The reply: northern-lights "is the heaviest shader ... see the discussion ... in the custom shaders rfc issue" ([thread](https://www.reddit.com/r/KittyTerminal/comments/1wse3pa/it_seems_im_late_to_the_party_kitty_049_custom/)). In [Kitty shader :3](https://www.reddit.com/r/KittyTerminal/comments/1wthm72/kitty_shader_3/), the poster sees an iGPU "spikes to like 25%+ ... with 2.2w" in btop, and plans to use shaders "only ... while showcasing", otherwise "cursor-trail only". Others: "on the battery ... you are fucked", "uses a bit more gpu than usual", and "is it worth the GPU cycles?" | Several anecdotes in two threads, no measurements. They agree with kitty-crt's number |
| Freezes after updating to 0.49 | "my terminal isn't working after the update. it's freezed. I rolled back"; a second user: "I had a big freeze with one of the shaders" ([thread](https://www.reddit.com/r/KittyTerminal/comments/1wrw08z/showcase_with_new_custom_shaders_in_kitty_0_49/)) | Two comments, no issue linked |
| Distro packaging | "for some Arch users: you need to install the shader-slang package in order for the shaders to work" (same thread). Older trail threads: Ubuntu's packaged kitty was too old for `cursor_trail` ([thread](https://www.reddit.com/r/KittyTerminal/comments/1k25r5r/cursor_trail_not_working/)) | Recurring: the version and `slangc` floor reaching users |
| Render cost of kitty itself | A 5-7% speedup from skipping glyph samples on blank cells was benchmarked by the maintainer in a PR comment ([kitty#10652](https://github.com/kovidgoyal/kitty/pull/10652)). Not a shader complaint, but shows the render path being tuned after release | One PR |

No thread is a dedicated battery complaint. GPU and battery cost comes up inside showcase threads.
Users treat heavy shaders as something for showing off, and keep a trail for daily use
**(inference from two threads)**.

## 5. What this suggests for termux-launcher

Short, all **(inference)**. The full analysis is in the [existing note](kitty-custom-shaders-research.md).

- **Taste signal:** the demand is for cursor trails and CRT. Backgrounds are a minority, which agrees
  with the existing note's option A (small original AGSL built-ins) and with the app's existing
  Canvas-drawn trail (section 3.1 there). A CRT would be a post-process, so option D, out of scope.
- **Power is the one measured community cost.** The kitty-crt result (continuous redraw costs a
  power state, not a per-frame cost, so slowing to 20 fps does not help) is a warning for the
  existing note's section 4.1: lowering the frame rate may not save much on a phone either.
  A still variant that draws only on events cost nothing. Test on a real device first
  (**unverified** for Android). The Reddit anecdotes point the same way: an iGPU laptop at 25%+ GPU,
  and a heavy background at 100%. A phone GPU is weaker than both.
- **Trails split users.** The built-in trail's top thread is half "distracting / my eyes hurt", half
  "helps me find the cursor". A trail should be opt-in, with a subtle default.
- **Event-driven beats ambient.** ume-kaze, the sharks and kitty's own backgrounds all gate on focus
  or events. That fits the pause rules in the spec (existing note, section 4.1).
- **Licences:** the most popular collections are MIT (sahaj-b, CRTty, kitty-crt) or GPL-3.0 (noomz,
  ume-kaze), which a GPLv3-only app can take with notices. hackr-sh has no licence, and zxzxn3 mixes
  in CC-BY-NC-SA files. Same conclusion as section 4.3 there: write originals.
- **Ghostty ports are cursor-only and hand-made,** so there is no ready library of mobile-friendly
  GLSL to borrow from the kitty side.

## 6. Sources not reached

- **Reddit, how it was read.** Headless fetches fail: old.reddit.com JSON redirects to a login page,
  www.reddit.com returns 403, and WebFetch refuses reddit.com. A first pass used the Pullpush archive,
  which was incomplete and had stale scores. A second pass opened old.reddit.com in a real browser
  (terminal-browser) and searched the subreddit, sorted by top over all time, for `shader`,
  `custom_shaders`, `cursor trail`, `glsl`, `crt`, `bloom` and `ghostty`: 45 unique threads. I read the
  post body and up to 40 comments of the 22 shader- or trail-related threads. Threads that only
  mention Ghostty in passing were skimmed by title. The search may miss threads that use other words.
- **Video content.** Several threads are v.redd.it or YouTube posts. I read only titles and bodies.
- **The full built-in shader list** from the 18-shader showcase post was cut off. The kitty docs page
  is the authoritative list (existing note).
- **Hyprland and other subreddits.** A reply links an r/hyprland comment about shaders. Not read.
- **Repos linked from threads** (cursor-smith, Ghostty-Nyan-Shader, FractalCodeRicardo's shaders,
  ryu-mirror) were not opened.
- **Kitty Discord, Matrix and kitty GitHub Discussions.** Not searched. Issues were searched
  through the GitHub API (query "shader" in kovidgoyal/kitty, top 50 by date); bodies and comments
  were read for ten of them.
- **Shader source in the collections.** I read the READMEs, licence metadata and directory listings,
  not the shader code. I did not run any shader or compile any pipeline.
- **Licence files.** I took licences from the GitHub API and README text. I did not read the
  licence files of the collections beyond the names in zxzxn3's `LICENSES/`.
- **Stars of kitty-native repos** are tiny and change daily. Treat the table as a snapshot.
- **Unknown pieces:** whether hackr-sh shaders have been ported anywhere under a different name, and
  whether a kitty-native Shadertoy converter exists. A GitHub repository search for "kitty shader",
  "kitty custom_shaders", "ghostty shaders", "kitty slang shader" and "kitty crt shader" was the only
  discovery method; a repo that uses other words would be missed.
