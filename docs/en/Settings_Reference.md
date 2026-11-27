# Settings reference

<!-- Generated from the launcher's settings; do not edit by hand. To update it, run
REGENERATE_SETTINGS_REFERENCE=1 ./gradlew :app:testDebugUnitTest --tests 'com.termux.app.settings.SettingsReferenceTest' -->

Every setting [`launcherctl config`](LauncherCtl.md#config) can read and change, with the values it accepts and what a fresh install uses. A value outside the range is refused and nothing changes.

## Colours and text

| Setting | Id | Values | Default | What it does |
|---|---|---|---|---|
| Theme | `theme.mode` | system, light, dark | system | Whether the launcher follows the system's light or dark setting, or stays light or dark. |
| Terminal contrast | `terminal.contrast` | softer, default, harder | default | How hard the terminal's colours are, from soft pastels to the deepest background and brightest text. |
| Text size | `text.size` | 80 to 150 | 100 | How large text is across the launcher's own screens, in percent. |
| Terl desktop | `terl.desktop` | false, true | false | Opens on Home and Workspaces of windows, in place of the dock and status bar. |
| Extra keys row | `terl.extra_keys` | false, true | false | Shows a row of extra keys above the keyboard, for typing with your phone's own keyboard. |
| Drawer item | `terl.drawer_tool` | false, true | true | Shows the drawer's button in Home's bottom row. |
| Terminal font size | `terminal.font_size` | From a third of the default size up to 256 px | About 12 dp of text, whatever the screen | How large the terminal's text is, in pixels. |
| Compact font size | `terminal.compact_font_size` | From a third of the default size up to 256 px | About 12 dp of text, whatever the screen | How large text is in Compact windows; until you set one it follows the terminal's. |
| Scratchpad font size | `terminal.scratchpad_font_size` | From a third of the default size up to 256 px | About 12 dp of text, whatever the screen | How large text is in the scratchpad; until you set one it follows the terminal's. |

## Motion and battery

| Setting | Id | Values | Default | What it does |
|---|---|---|---|---|
| Animation speed | `motion.animation_speed` | slower, normal, faster, off | normal | How fast the launcher's own animations run, or whether they play at all. |
| Page transition | `motion.page_transition` | slide, depth, fade, carousel, stack | slide | How a page turns when you swipe between Home pages, the Terminal and windows. |
| Idle animations | `motion.idle_animations` | false, true | false | Lets the retro screen effect play small glitches while the terminal sits idle. |
| Idle glitch rate | `motion.idle_tear_rate` | rare, sometimes, often | rare | How often the idle animations glitch the terminal. |
| Cursor trail | `motion.cursor_trail` | false, true | true | Draws a trail behind the terminal's cursor as it moves. |
| Wallpaper parallax | `motion.wallpaper_parallax` | false, true | true | Moves the wallpaper a little as pages and windows slide. |
| Live windows | `motion.live_windows` | false, true | true | Keeps windows updating while you swipe between them. |
| Reduce idle activity | `battery.reduce_idle` | false, true | false | Lets the launcher do less in the background while you are not using it. |
| Glass refraction | `glass.refraction` | false, true | true | Makes glass surfaces bend and shine the way real glass does. |
| Live blur | `glass.live_blur` | false, true | false | Blurs whatever moves behind glass surfaces, not just the wallpaper. |
| Terminal effect | `terminal.effect` | none, crt, crt_green, crt_amber, crt_material, tft | none | Gives the terminal an old monitor look, or none. |
| Screen events | `terminal.screen_events` | false, true | true | Lets the terminal's bell and other events flicker the screen effect. |
| Effect blending | `terminal.effect_composite` | false, true | false | Blends the retro effect into the terminal's colours. |
| Low memory preset | `battery.low_memory_preset` | false, true | false | Switches off the costliest effects and lightens the look so the launcher runs on less. |

## Status bar, dock and Home

| Setting | Id | Values | Default | What it does |
|---|---|---|---|---|
| Battery in the status bar | `status.battery` | false, true | false | Shows the battery level in the status bar. |
| Media in the status bar | `status.media` | false, true | true | Shows what is playing in the status bar. |
| Weather in the status bar | `status.weather` | false, true | true | Shows the weather in the status bar. |
| AM and PM on the clock | `status.clock_am_pm` | false, true | true | Shows AM or PM beside the status bar clock. |
| Dock style | `dock.style` | docked, floating | docked | Whether the dock and bars sit on the screen's edge or float as cards. |
| Notification dots | `notifications.dots` | false, true | false | Marks app icons that have notifications. |
| Notification badge | `notifications.badge_style` | dot, count | dot | Whether an app icon shows a dot or the number of notifications. |
| Quick reply swipe | `home.quick_reply_swipe` | false, true | true | Swipe up on an app icon that has a notification to reply from the dock. |
| Keyboard when the drawer opens | `drawer.search_on_open` | false, true | false | Opens the keyboard as soon as the app drawer opens. |
| Most-used apps page | `drawer.most_used_page` | false, true | false | Adds a drawer page ranked by how often you open each app. |
| Use Terl as | `home.use_as` | home, terminal | home | Whether Terl is your home screen with Home pages, or a terminal that opens on its windows. |

## Keys

| Setting | Id | Values | Default | What it does |
|---|---|---|---|---|
| Leader | `keys.leader` | terl, ctrl+alt, custom | terl | What starts every shortcut: the Terl key (Ctrl+Alt on a hardware keyboard), Ctrl+Alt, or keys you choose. |
| Custom leader | `keys.leader_chord` | two or more keys with Ctrl or Alt | none | The keys of a custom leader: ctrl+shift is held with the key, ctrl+b is pressed before it. |

## Terminals, keyboard and Linux apps

| Setting | Id | Values | Default | What it does |
|---|---|---|---|---|
| Terminal core | `terminal.core` | termux, libghostty | termux | Which engine new terminals run on. |
| Scrollback | `terminal.scrollback` | 100 to 50000 | 2000 | How many lines a terminal keeps for scrolling back. |
| Bell | `terminal.bell` | vibrate, beep, ignore | vibrate | What a terminal does when a program rings its bell. |
| Display server | `linux.display_server` | x11, wayland | x11 | Whether Linux apps open on X11 or on Wayland, each app in its own window. |
| Camera cutout | `cutout.mode` | automatic, custom | automatic | Keeps clear of the camera where the phone reports it, or where you place it. |
| Room around the camera | `cutout.inset` | 0 to 24 | 4 | How much space is kept clear around the camera cutout, in dp. |
| Key sound | `keyboard.key_sound` | false, true | false | Plays a click as you type on the launcher keyboard. |
| Key preview | `keyboard.key_popup` | false, true | true | Shows the letter above your finger as you press a key. |
| Vibrate on keypress | `keyboard.haptics` | false, true | true | Gives a short vibration for each key you press. |
| Keyboard layout | `keyboard.layout` | qwerty, qwertz, azerty, dvorak, colemak, custom | qwerty | The letters the launcher keyboard types: QWERTY, QWERTZ, AZERTY, Dvorak, Colemak, or your own layout. |

## Look

| Setting | Id | Values | Default | What it does |
|---|---|---|---|---|
| Global blur | `look.blur` | 0 to 7 | 2 | How frosted the glass is, from clear to heavily frosted. |
| Global grain | `look.grain` | 0 to 100 | 3 | How much film grain sits on the glass, in percent. |
| Global opacity | `look.opacity` | 0 to 100 | 34 | How solid the glass is, in percent. |
| Global tint | `look.tint` | 0 to 100 | 0 | How strongly the theme colour tints the glass, in percent. |
| Global tint colour | `look.colour` | primary, secondary, tertiary | primary | Which theme colour tints the glass. |
| Global depth | `look.depth` | 0 to 45 | 9 | How strongly the glass bends the wallpaper at its edge, in dp. |
| Global corners | `look.corners` | -1 to 40 | 24 | How round the corners are, in dp; the lowest value follows each surface's style. |
| Global shine | `look.shine` | 0 to 100 | 50 | How much light catches the glass's rim and lens. |
| Global vibrancy | `look.vibrancy` | 70 to 150 | 100 | How much the glass lifts the colours behind it. |
| Global edge gap | `look.margin` | 0 to 48 | 12 | The gap between the glass and the screen edge, in dp. |
| Status bar opacity | `look.status.opacity` | 0 to 100 | 34 | The status bar's own opacity; until you set one it follows the Global look. |
| Status bar blur | `look.status.blur` | 0 to 7 | 2 | The status bar's own blur; until you set one it follows the Global look. |
| Dock opacity | `look.dock.opacity` | 0 to 100 | 34 | The dock's own opacity; until you set one it follows the Global look. |
| Dock blur | `look.dock.blur` | 0 to 7 | 2 | The dock's own blur; until you set one it follows the Global look. |
| Keyboard opacity | `look.keyboard.opacity` | 0 to 100 | 34 | The keyboard's own opacity; until you set one it follows the Global look. |
| Keyboard blur | `look.keyboard.blur` | 0 to 7 | 2 | The keyboard's own blur; until you set one it follows the Global look. |
| Terminal background opacity | `look.background.opacity` | 0 to 100 | 40 | How solid the terminal panes' background is, in percent. |
| Terminal background blur | `look.background.blur` | 0 to 48 | 8 | How blurred the wallpaper behind the terminal panes is, in dp. |
| Terminal background wallpaper dim | `look.background.dim` | 0 to 100 | 0 | How much the wallpaper behind the terminal is darkened, in percent. |

## Layout sizes

| Setting | Id | Values | Default | What it does |
|---|---|---|---|---|
| Icon size | `layout.sizes.icon_size` | s, m, l or xl | m | How large app icons are on Home. |
| Dock height in portrait | `layout.sizes.portrait.dock_height` | 0.4 to 3.0 | 2.18 | How tall the dock is, as a multiple of the standard height. |
| Keyboard height in portrait | `layout.sizes.portrait.keyboard_height` | 0.5 to 1.6 | The standard height | How tall the in-app keyboard is, as a multiple of the standard height. |
| Keyboard bottom space in portrait | `layout.sizes.portrait.keyboard_chin` | 0 to 48 | 0 | The space under the in-app keyboard, in dp. |
| Wall strip height in portrait | `layout.sizes.portrait.wall_strip_height` | 56 to 128 | 84 | How tall the strip of wall controls is, in dp. |
| Dock height in landscape | `layout.sizes.landscape.dock_height` | 0.4 to 3.0 | 2.18 | How tall the dock is, as a multiple of the standard height. |
| Keyboard height in landscape | `layout.sizes.landscape.keyboard_height` | 0.5 to 1.6 | The standard height | How tall the in-app keyboard is, as a multiple of the standard height. |
| Keyboard bottom space in landscape | `layout.sizes.landscape.keyboard_chin` | 0 to 48 | 0 | The space under the in-app keyboard, in dp. |
| Wall strip height in landscape | `layout.sizes.landscape.wall_strip_height` | 56 to 128 | 84 | How tall the strip of wall controls is, in dp. |

The other layout fields, such as where a bar sits on a page, are listed by `launcherctl config list` and described under [layout](LauncherCtl.md#layout).
