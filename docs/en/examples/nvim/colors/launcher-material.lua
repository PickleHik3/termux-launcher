-- launcher-material — Neovim in your wallpaper's colours.
--
--   :colorscheme launcher-material
--
-- A real colors/ file, so any config can select it by name: plain Neovim,
-- lazy.nvim (LazyVim), AstroNvim's `colorscheme = "launcher-material"`, or NvChad.
-- It needs no plugin. All the colour work is in lua/launcher/material_palette.lua,
-- which reads the palette the launcher rewrites on every wallpaper change; this
-- file only paints the highlight groups from it.
--
-- Glass is the default: the editor is a window onto the wallpaper, like the rest
-- of the launcher, and the degree comes from the terminal's own opacity slider.
-- Opt out with `vim.g.material_opaque = true` before the colourscheme loads, or
-- `:MaterialTransparent off`.

--- The palette, or a fixed one ---------------------------------------------

-- Used before the launcher has ever exported wallpaper colours, and on plain
-- Termux, so the colourscheme is never broken — only static.
local FALLBACK = {
  type = "dark",
  base_30 = {
    white = "#ABB2BF",
    darker_black = "#1B1F27",
    black = "#1E222A",
    black2 = "#252931",
    one_bg = "#282C34",
    one_bg2 = "#30343C",
    one_bg3 = "#383C44",
    grey = "#42464E",
    grey_fg = "#5C6370",
    grey_fg2 = "#6C7079",
    light_grey = "#6F737B",
    line = "#31353D",
    statusline_bg = "#22262E",
    lightbg = "#2D3139",
    pmenu_bg = "#61AFEF",
    folder_bg = "#61AFEF",
    red = "#E06C75",
    green = "#98C379",
    vibrant_green = "#7EC7A2",
    yellow = "#E5C07B",
    sun = "#EBCB8B",
    blue = "#61AFEF",
    nord_blue = "#81A1C1",
    cyan = "#56B6C2",
    teal = "#519ABA",
    purple = "#C678DD",
    dark_purple = "#B57EDC",
    pink = "#DE8C92",
    baby_pink = "#DE8C92",
    orange = "#D19A66",
  },
  base_16 = {
    base00 = "#1E222A",
    base01 = "#282C34",
    base02 = "#30343C",
    base03 = "#5C6370",
    base04 = "#6F737B",
    base05 = "#ABB2BF",
    base06 = "#C8CCD4",
    base07 = "#383C44",
    base08 = "#E06C75",
    base09 = "#D19A66",
    base0A = "#E5C07B",
    base0B = "#98C379",
    base0C = "#56B6C2",
    base0D = "#61AFEF",
    base0E = "#C678DD",
    base0F = "#7E4B52",
  },
}

local palette_ok, palette = pcall(require, "launcher.material_palette")
local built = palette_ok and palette.build() or nil
if not built then
  built = FALLBACK
end

local c = built.base_16
local d = built.base_30

--- Glass -------------------------------------------------------------------

-- A terminal cell has no alpha channel, so "glass" means bg NONE: Neovim
-- inherits the terminal's background and the wallpaper shows through it.
-- `material_transparent = false` is still honoured for older configs.
local glass = not (vim.g.material_opaque == true or vim.g.material_transparent == false)

-- Every chrome background goes through this, so opaque stays a real theme
-- rather than a fallback nobody looks at.
local function surface(solid)
  return glass and "NONE" or solid
end

-- With the fills gone, structure has to come from the borders instead, so they
-- step up from outline_variant to outline. What keeps a fill does so because it
-- marks a position rather than a region: the cursor line, the selection, the
-- selected tab.
local border = glass and d.grey_fg2 or d.line
local accent = d.pmenu_bg -- Material PRIMARY

--- Reset -------------------------------------------------------------------

vim.cmd "highlight clear"
if vim.fn.exists "syntax_on" == 1 then
  vim.cmd "syntax reset"
end
vim.o.termguicolors = true
vim.o.background = built.type == "light" and "light" or "dark"

local set = function(group, attrs)
  vim.api.nvim_set_hl(0, group, attrs)
end

--- The buffer --------------------------------------------------------------

local groups = {
  Normal = { fg = c.base05, bg = surface(c.base00) },
  NormalNC = { fg = c.base05, bg = surface(c.base00) },
  NormalFloat = { fg = c.base05, bg = surface(d.darker_black) },
  FloatBorder = { fg = border, bg = surface(d.darker_black) },
  FloatTitle = { fg = accent, bold = true },
  EndOfBuffer = { fg = glass and border or c.base00, bg = surface(c.base00) },
  SignColumn = { fg = d.grey, bg = surface(c.base00) },
  FoldColumn = { fg = d.grey, bg = surface(c.base00) },
  Folded = { fg = d.grey_fg, bg = surface(d.one_bg) },
  MsgArea = { fg = c.base05, bg = surface(c.base00) },
  NonText = { fg = d.grey },
  SpecialKey = { fg = d.grey },
  Whitespace = { fg = d.grey },
  Conceal = { fg = d.grey },
  Directory = { fg = c.base0D },
  Title = { fg = accent, bold = true },
  ColorColumn = { bg = d.one_bg },
  CursorColumn = { bg = d.one_bg },

  -- Position markers, kept solid on purpose: bare text on glass cannot show
  -- you where the cursor or the selection is.
  CursorLine = { bg = d.one_bg },
  CursorLineNr = { fg = c.base05, bold = true },
  LineNr = { fg = d.grey },
  Visual = { bg = d.one_bg3 },
  VisualNOS = { bg = d.one_bg3 },
  MatchParen = { fg = d.orange, bold = true },
  QuickFixLine = { bg = d.one_bg2 },

  Search = { fg = c.base00, bg = d.yellow },
  IncSearch = { fg = c.base00, bg = d.orange },
  CurSearch = { fg = c.base00, bg = d.orange },

  -- Syntax, from the ANSI slots, which keep their hue separation.
  Comment = { fg = c.base03, italic = true },
  Constant = { fg = c.base09 },
  Number = { fg = c.base09 },
  Float = { fg = c.base09 },
  Boolean = { fg = c.base09 },
  Character = { fg = c.base08 },
  String = { fg = c.base0B },
  Identifier = { fg = c.base08 },
  Function = { fg = c.base0D },
  Statement = { fg = c.base0E },
  Conditional = { fg = c.base0E },
  Repeat = { fg = c.base0E },
  Label = { fg = c.base0A },
  Operator = { fg = c.base05 },
  Keyword = { fg = c.base0E },
  Exception = { fg = c.base08 },
  PreProc = { fg = c.base0A },
  Include = { fg = c.base0D },
  Define = { fg = c.base0E },
  Macro = { fg = c.base08 },
  Type = { fg = c.base0A },
  StorageClass = { fg = c.base0A },
  Structure = { fg = c.base0E },
  Typedef = { fg = c.base0A },
  Special = { fg = c.base0C },
  SpecialChar = { fg = c.base0F },
  SpecialComment = { fg = c.base0C },
  Tag = { fg = c.base0A },
  Delimiter = { fg = c.base05 },
  Debug = { fg = c.base08 },
  Underlined = { fg = c.base0D, underline = true },
  Ignore = { fg = d.grey },
  Error = { fg = d.red, bg = "NONE" },
  ErrorMsg = { fg = d.red },
  WarningMsg = { fg = d.sun },
  ModeMsg = { fg = d.green },
  MoreMsg = { fg = d.green },
  Question = { fg = c.base0D },
  Todo = { fg = c.base00, bg = d.yellow, bold = true },

  -- Chrome, from the Material surface roles, so it layers with the wallpaper.
  StatusLine = { fg = d.white, bg = surface(d.statusline_bg) },
  StatusLineNC = { fg = d.grey_fg, bg = surface(d.statusline_bg) },
  TabLine = { fg = d.grey_fg2, bg = surface(d.darker_black) },
  TabLineFill = { bg = surface(d.darker_black) },
  TabLineSel = { fg = d.white, bg = d.one_bg2, bold = glass },
  WinBar = { fg = d.grey_fg2, bg = surface(c.base00) },
  WinBarNC = { fg = d.grey, bg = surface(c.base00) },
  WinSeparator = { fg = border, bg = surface(c.base00) },
  VertSplit = { fg = border, bg = surface(c.base00) },
  Pmenu = { fg = d.white, bg = surface(d.one_bg) },
  PmenuSel = { fg = c.base00, bg = accent },
  PmenuSbar = { bg = surface(d.one_bg2) },
  PmenuThumb = { bg = d.grey },
  PmenuKind = { fg = c.base0A, bg = surface(d.one_bg) },
  PmenuExtra = { fg = d.grey_fg, bg = surface(d.one_bg) },
  WildMenu = { fg = c.base00, bg = accent },

  -- Diagnostics are never mistaken for ordinary syntax: the palette pushes the
  -- error colour apart from the syntax red when the wallpaper puts them together.
  DiagnosticError = { fg = d.red },
  DiagnosticWarn = { fg = d.sun },
  DiagnosticInfo = { fg = d.blue },
  DiagnosticHint = { fg = d.teal },
  DiagnosticOk = { fg = d.vibrant_green },
  DiagnosticUnderlineError = { undercurl = true, sp = d.red },
  DiagnosticUnderlineWarn = { undercurl = true, sp = d.sun },
  DiagnosticUnderlineInfo = { undercurl = true, sp = d.blue },
  DiagnosticUnderlineHint = { undercurl = true, sp = d.teal },

  -- Git, and the statusline segments plugins derive from it.
  GitSignsAdd = { fg = d.green },
  GitSignsChange = { fg = d.yellow },
  GitSignsDelete = { fg = d.red },
  GitSignsAddNr = { fg = d.green },
  GitSignsChangeNr = { fg = d.yellow },
  GitSignsDeleteNr = { fg = d.red },
  -- Diffs keep their fills in both modes: they mark regions, and a region with
  -- no background is not a region.
  DiffAdd = { fg = d.green, bg = d.one_bg },
  DiffChange = { fg = d.yellow, bg = d.one_bg },
  DiffDelete = { fg = d.red, bg = d.one_bg },
  DiffText = { fg = c.base00, bg = d.yellow },
  Added = { fg = d.green },
  Changed = { fg = d.yellow },
  Removed = { fg = d.red },
}

for group, attrs in pairs(groups) do
  set(group, attrs)
end

--- Treesitter --------------------------------------------------------------

-- Captures Neovim's own queries emit, pointed at the groups above so a config
-- that turns treesitter on gets the same colours as one that does not.
local captures = {
  ["@comment"] = "Comment",
  ["@comment.error"] = "DiagnosticError",
  ["@comment.warning"] = "DiagnosticWarn",
  ["@comment.note"] = "DiagnosticInfo",
  ["@comment.todo"] = "Todo",
  ["@constant"] = "Constant",
  ["@constant.builtin"] = "Constant",
  ["@constant.macro"] = "Macro",
  ["@number"] = "Number",
  ["@boolean"] = "Boolean",
  ["@float"] = "Float",
  ["@string"] = "String",
  ["@string.regexp"] = "Special",
  ["@string.escape"] = "SpecialChar",
  ["@string.special"] = "Special",
  ["@character"] = "Character",
  ["@variable"] = "Identifier",
  ["@variable.builtin"] = "Special",
  ["@variable.parameter"] = "Identifier",
  ["@variable.member"] = "Identifier",
  ["@property"] = "Identifier",
  ["@field"] = "Identifier",
  ["@function"] = "Function",
  ["@function.builtin"] = "Function",
  ["@function.call"] = "Function",
  ["@function.macro"] = "Macro",
  ["@method"] = "Function",
  ["@method.call"] = "Function",
  ["@constructor"] = "Type",
  ["@keyword"] = "Keyword",
  ["@keyword.function"] = "Keyword",
  ["@keyword.operator"] = "Operator",
  ["@keyword.return"] = "Keyword",
  ["@keyword.conditional"] = "Conditional",
  ["@keyword.repeat"] = "Repeat",
  ["@keyword.import"] = "Include",
  ["@keyword.exception"] = "Exception",
  ["@type"] = "Type",
  ["@type.builtin"] = "Type",
  ["@type.definition"] = "Typedef",
  ["@attribute"] = "PreProc",
  ["@operator"] = "Operator",
  ["@punctuation.delimiter"] = "Delimiter",
  ["@punctuation.bracket"] = "Delimiter",
  ["@punctuation.special"] = "Special",
  ["@tag"] = "Tag",
  ["@tag.attribute"] = "Identifier",
  ["@tag.delimiter"] = "Delimiter",
  ["@module"] = "Type",
  ["@label"] = "Label",
  ["@markup.heading"] = "Title",
  ["@markup.link"] = "Underlined",
  ["@markup.raw"] = "String",
  ["@diff.plus"] = "Added",
  ["@diff.minus"] = "Removed",
  ["@diff.delta"] = "Changed",
  ["@lsp.type.class"] = "Type",
  ["@lsp.type.function"] = "Function",
  ["@lsp.type.keyword"] = "Keyword",
  ["@lsp.type.method"] = "Function",
  ["@lsp.type.property"] = "Identifier",
  ["@lsp.type.variable"] = "Identifier",
}

for capture, group in pairs(captures) do
  set(capture, { link = group })
end

--- :terminal on the same sixteen colours the shell uses --------------------

local ansi = {
  c.base00,
  c.base08,
  c.base0B,
  c.base0A,
  c.base0D,
  c.base0E,
  c.base0C,
  c.base05,
  d.grey_fg2,
  d.baby_pink,
  d.vibrant_green,
  d.sun,
  d.nord_blue,
  d.dark_purple,
  d.teal,
  c.base06,
}
for i, colour in ipairs(ansi) do
  vim.g["terminal_color_" .. (i - 1)] = colour
end

-- Nothing that floats is blended: everything is already bg NONE in glass mode,
-- and blending a transparent window washes the text out against the wallpaper
-- twice.
vim.o.winblend = 0
vim.o.pumblend = 0

--- Follow the wallpaper ----------------------------------------------------

if palette_ok then
  -- Re-apply this colourscheme when the launcher rewrites the palette, rather
  -- than the base46 reload the palette module does for NvChad.
  palette.reload = function()
    pcall(vim.cmd.colorscheme, "launcher-material")
  end
  pcall(palette.watch)
end

vim.api.nvim_create_user_command("MaterialTransparent", function(cmd)
  local on
  if cmd.args == "on" then
    on = true
  elseif cmd.args == "off" then
    on = false
  else
    on = not glass
  end
  vim.g.material_opaque = not on
  vim.g.material_transparent = on
  vim.cmd.colorscheme "launcher-material"
  print("launcher-material: glass " .. (on and "on" or "off"))
end, {
  nargs = "?",
  complete = function()
    return { "on", "off" }
  end,
  desc = "Toggle the glass background (the opacity comes from the terminal)",
})

vim.g.colors_name = "launcher-material"
