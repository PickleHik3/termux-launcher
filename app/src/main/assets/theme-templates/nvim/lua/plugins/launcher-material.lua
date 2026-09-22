-- launcher-material plugin spec — installed by Termux Launcher, do not edit.
--
-- Selects the launcher-material colorscheme (installed by this template's
-- apply.sh into ~/.config/nvim/colors/) through whichever distro this config
-- is, without `require`-ing any of their modules: they may not be on the
-- runtimepath yet the first time this file loads.
--
-- It does not touch `background`: Neovim sets that from the terminal's own
-- light/dark report, and the colourscheme paints whichever mode it names.

local function file_contains(path, needle)
  local fd = io.open(path, "r")
  if not fd then
    return false
  end
  local content = fd:read("*a")
  fd:close()
  return content ~= nil and content:find(needle, 1, true) ~= nil
end

local function config_path(rel)
  return vim.fn.stdpath("config") .. "/" .. rel
end

local function is_lazyvim()
  return file_contains(config_path("lua/config/lazy.lua"), "LazyVim")
end

local function is_astronvim()
  return file_contains(config_path("lua/lazy_setup.lua"), "AstroNvim")
    or file_contains(config_path("lua/community.lua"), "AstroNvim")
end

if is_lazyvim() then
  return {
    { "LazyVim/LazyVim", opts = { colorscheme = "launcher-material" } },
  }
end

if is_astronvim() then
  return {
    { "AstroNvim/astroui", opts = { colorscheme = "launcher-material" } },
  }
end

return {}
