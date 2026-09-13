-- Builds a base46 theme from the launcher's wallpaper-derived Material palette.
--
-- The launcher writes ~/.termux/material-colors.sh on every wallpaper/theme change.
-- It carries both Material roles (SURFACE*, PRIMARY, ERROR, OUTLINE...) and a full
-- ANSI set (TERMINAL_COLOR0..15, TERMINAL_BACKGROUND/FOREGROUND).
--
-- Division of labour, which is the whole trick: syntax comes from the ANSI colours
-- (already hue-diversified, so code keeps red/green/yellow/blue/magenta/cyan
-- separation) and UI chrome comes from the Material roles (so the editor matches the
-- wallpaper). Mixing that the other way round collapses code into one tint.
--
-- Everything is then clamped: a wallpaper is not designed for code contrast.

local M = {}

local PALETTE = os.getenv("HOME") .. "/.termux/material-colors.sh"

-- Contrast targets, WCAG-style ratios against the background.
local TEXT_CONTRAST = 4.5 -- syntax, foreground text
local DIM_CONTRAST = 3.0 -- comments, borders, gutter

--- Colour helpers ----------------------------------------------------------

local function hex2rgb(hex)
  hex = hex:gsub("#", "")
  return tonumber(hex:sub(1, 2), 16), tonumber(hex:sub(3, 4), 16), tonumber(hex:sub(5, 6), 16)
end

local function clamp(v, lo, hi)
  return math.max(lo, math.min(hi, v))
end

local function rgb2hex(r, g, b)
  return string.format("#%02X%02X%02X", clamp(math.floor(r + 0.5), 0, 255),
    clamp(math.floor(g + 0.5), 0, 255), clamp(math.floor(b + 0.5), 0, 255))
end

local function channel_lin(c)
  c = c / 255
  return c <= 0.03928 and c / 12.92 or ((c + 0.055) / 1.055) ^ 2.4
end

local function luminance(hex)
  local r, g, b = hex2rgb(hex)
  return 0.2126 * channel_lin(r) + 0.7152 * channel_lin(g) + 0.0722 * channel_lin(b)
end

local function contrast(a, b)
  local l1, l2 = luminance(a), luminance(b)
  if l1 < l2 then
    l1, l2 = l2, l1
  end
  return (l1 + 0.05) / (l2 + 0.05)
end

local function mix(a, b, t)
  local ar, ag, ab = hex2rgb(a)
  local br, bg, bb = hex2rgb(b)
  return rgb2hex(ar + (br - ar) * t, ag + (bg - ag) * t, ab + (bb - ab) * t)
end

local function rgb2hsl(hex)
  local r, g, b = hex2rgb(hex)
  r, g, b = r / 255, g / 255, b / 255
  local max, min = math.max(r, g, b), math.min(r, g, b)
  local l = (max + min) / 2
  if max == min then
    return 0, 0, l
  end
  local d = max - min
  local s = l > 0.5 and d / (2 - max - min) or d / (max + min)
  local h
  if max == r then
    h = (g - b) / d + (g < b and 6 or 0)
  elseif max == g then
    h = (b - r) / d + 2
  else
    h = (r - g) / d + 4
  end
  return h * 60, s, l
end

local function hue2rgb(p, q, t)
  if t < 0 then
    t = t + 1
  end
  if t > 1 then
    t = t - 1
  end
  if t < 1 / 6 then
    return p + (q - p) * 6 * t
  end
  if t < 1 / 2 then
    return q
  end
  if t < 2 / 3 then
    return p + (q - p) * (2 / 3 - t) * 6
  end
  return p
end

local function hsl2rgb(h, s, l)
  h = (h % 360) / 360
  if s == 0 then
    local v = l * 255
    return rgb2hex(v, v, v)
  end
  local q = l < 0.5 and l * (1 + s) or l + s - l * s
  local p = 2 * l - q
  return rgb2hex(hue2rgb(p, q, h + 1 / 3) * 255, hue2rgb(p, q, h) * 255, hue2rgb(p, q, h - 1 / 3) * 255)
end

local function saturate(hex, amount)
  local h, s, l = rgb2hsl(hex)
  return hsl2rgb(h, clamp(s + amount, 0, 1), l)
end

local function hue_distance(a, b)
  local ha = rgb2hsl(a)
  local hb = rgb2hsl(b)
  local d = math.abs(ha - hb) % 360
  return d > 180 and 360 - d or d
end

--- Lift a colour until it is legible on bg, without changing its hue.
--- Mixing toward white/black rather than working in OKLCH: no external deps, and
--- the hue drift over the few steps this needs is not visible.
local function ensure_contrast(fg, bg, target)
  local towards = luminance(bg) > 0.18 and "#000000" or "#FFFFFF"
  local out = fg
  for _ = 1, 24 do
    if contrast(out, bg) >= target then
      break
    end
    out = mix(out, towards, 0.06)
  end
  return out
end

--- Palette file -----------------------------------------------------------

--- Parses `export TERMUX_MATERIAL_KEY='#RRGGBB'` lines. Returns nil when the file
--- is missing, which is the plain-Termux / pre-first-wallpaper case.
function M.read(path)
  local fd = io.open(path or PALETTE, "r")
  if not fd then
    return nil
  end
  local out = {}
  for line in fd:lines() do
    local key, value = line:match("^%s*export%s+TERMUX_MATERIAL_([%w_]+)%s*=%s*'?([^']*)'?%s*$")
    if key and value ~= "" then
      out[key] = value
    end
  end
  fd:close()
  return next(out) and out or nil
end

--- A fixed, known-good syntax set. Used when the wallpaper cannot supply one.
local FALLBACK_SYNTAX = {
  red = "#E06C75",
  orange = "#D19A66",
  yellow = "#E5C07B",
  green = "#98C379",
  cyan = "#56B6C2",
  blue = "#61AFEF",
  magenta = "#C678DD",
}

--- Chroma floor: a greyscale wallpaper yields six near-identical hues, and
--- faithfully reproducing that makes code unreadable. Degrade instead.
local function syntax_from(p, bg)
  local syn = {
    red = p.TERMINAL_COLOR1,
    green = p.TERMINAL_COLOR2,
    yellow = p.TERMINAL_COLOR3,
    blue = p.TERMINAL_COLOR4,
    magenta = p.TERMINAL_COLOR5,
    cyan = p.TERMINAL_COLOR6,
    orange = p.TERTIARY or p.TERMINAL_COLOR3,
  }

  local total, count = 0, 0
  for _, hex in pairs(syn) do
    if hex then
      local _, s = rgb2hsl(hex)
      total, count = total + s, count + 1
    end
  end
  local average_saturation = count > 0 and total / count or 0

  local degraded = count < 7 or average_saturation < 0.15
  if degraded then
    syn = vim.deepcopy(FALLBACK_SYNTAX)
  end

  for name, hex in pairs(syn) do
    syn[name] = ensure_contrast(hex, bg, TEXT_CONTRAST)
  end

  return syn, degraded, average_saturation
end

--- Build ------------------------------------------------------------------

function M.build()
  local p = M.read()
  if not p then
    return nil
  end

  local bg = p.TERMINAL_BACKGROUND or p.SURFACE or "#1A1111"
  local fg = p.TERMINAL_FOREGROUND or p.ON_SURFACE or "#F1DEDD"
  local is_light = luminance(bg) > 0.18

  local syn, degraded, saturation = syntax_from(p, bg)

  -- Diagnostics must never be mistaken for ordinary syntax. Material's ERROR role
  -- is authoritative, but on a red-tinted wallpaper it lands right on top of the
  -- syntax red, so separate them by chroma — and move the syntax colour never the
  -- diagnostic one, so an error always reads as an error.
  local error_colour = p.ERROR or syn.red
  if hue_distance(error_colour, syn.red) < 20 then
    local _, error_saturation = rgb2hsl(error_colour)
    local _, red_saturation = rgb2hsl(syn.red)
    if math.abs(error_saturation - red_saturation) < 0.15 then
      error_colour = saturate(error_colour, 0.25)
    end
  end
  error_colour = ensure_contrast(error_colour, bg, TEXT_CONTRAST)

  local surface1 = p.SURFACE_CONTAINER or mix(bg, fg, 0.06)
  local surface2 = p.SURFACE_CONTAINER_HIGH or mix(bg, fg, 0.12)
  local surface3 = p.SURFACE_CONTAINER_HIGHEST or mix(bg, fg, 0.18)
  local outline = p.OUTLINE or mix(bg, fg, 0.45)
  local outline_variant = p.OUTLINE_VARIANT or mix(bg, fg, 0.25)
  local comment = ensure_contrast(p.TERMINAL_COLOR8 or outline, bg, DIM_CONTRAST)
  local accent = p.PRIMARY or syn.blue

  local theme = {
    type = is_light and "light" or "dark",

    -- Chrome: Material roles, so the editor matches the wallpaper.
    base_30 = {
      white = fg,
      darker_black = mix(bg, is_light and "#FFFFFF" or "#000000", 0.25),
      black = bg,
      black2 = surface1,
      one_bg = surface1,
      one_bg2 = surface2,
      one_bg3 = surface3,
      grey = outline_variant,
      grey_fg = comment,
      grey_fg2 = outline,
      light_grey = ensure_contrast(p.ON_SURFACE_VARIANT or outline, bg, DIM_CONTRAST),
      line = outline_variant,
      statusline_bg = surface1,
      lightbg = surface2,
      pmenu_bg = accent,
      folder_bg = accent,

      -- Semantics: pinned, not free-running.
      red = error_colour,
      green = syn.green,
      vibrant_green = ensure_contrast(p.TERMINAL_COLOR10 or syn.green, bg, TEXT_CONTRAST),
      yellow = syn.yellow,
      sun = ensure_contrast(p.TERMINAL_COLOR11 or syn.yellow, bg, TEXT_CONTRAST),
      blue = syn.blue,
      nord_blue = ensure_contrast(p.TERMINAL_COLOR12 or syn.blue, bg, TEXT_CONTRAST),
      cyan = syn.cyan,
      teal = ensure_contrast(p.TERMINAL_COLOR14 or syn.cyan, bg, TEXT_CONTRAST),
      purple = syn.magenta,
      dark_purple = ensure_contrast(p.TERMINAL_COLOR13 or syn.magenta, bg, TEXT_CONTRAST),
      pink = ensure_contrast(p.TERMINAL_COLOR13 or syn.magenta, bg, TEXT_CONTRAST),
      baby_pink = ensure_contrast(p.TERMINAL_COLOR9 or syn.red, bg, TEXT_CONTRAST),
      orange = syn.orange,
    },

    -- Syntax: ANSI slots, which keep their hue separation.
    base_16 = {
      base00 = bg,
      base01 = surface1,
      base02 = surface2,
      base03 = comment, -- comments
      base04 = ensure_contrast(p.ON_SURFACE_VARIANT or outline, bg, DIM_CONTRAST),
      base05 = fg, -- default text
      base06 = ensure_contrast(p.TERMINAL_COLOR15 or fg, bg, TEXT_CONTRAST),
      base07 = surface3,
      base08 = syn.red, -- variables, tags
      base09 = syn.orange, -- numbers, constants
      base0A = syn.yellow, -- types
      base0B = syn.green, -- strings
      base0C = syn.cyan, -- escapes, regex
      base0D = syn.blue, -- functions
      base0E = syn.magenta, -- keywords
      base0F = mix(syn.red, bg, 0.35), -- deprecated
    },
  }

  -- Diagnostics/state are inspectable rather than guessed at: :lua= these.
  vim.g.material_theme_info = {
    degraded = degraded,
    average_saturation = saturation,
    contrast_level = p.TERMINAL_CONTRAST_LEVEL,
    comment_contrast = contrast(comment, bg),
    fg_contrast = contrast(fg, bg),
    type = theme.type,
  }

  return theme
end

--- Live reload -----------------------------------------------------------

local watcher, pending

--- Rebuild highlights when the launcher rewrites the palette, so changing the
--- wallpaper retints an open editor. Debounced: the file is rewritten in a few
--- syscalls and fs_event reports each one.
function M.watch()
  if watcher then
    return
  end
  watcher = vim.uv.new_fs_event()
  if not watcher then
    return
  end
  watcher:start(PALETTE, {}, function()
    if pending then
      return
    end
    pending = true
    vim.defer_fn(function()
      pending = false
      M.reload()
    end, 250)
  end)
end

function M.reload()
  package.loaded["launcher.material_palette"] = nil
  package.loaded["themes.material"] = nil
  local ok, err = pcall(function()
    require("base46").load_all_highlights()
  end)
  if not ok then
    vim.notify("material theme reload failed: " .. tostring(err), vim.log.levels.WARN)
  end
end

return M
