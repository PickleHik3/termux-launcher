package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Nerd Font glyph a window chip wears for the program running in it. Keyed by the lowercased
 * process basename the foreground resolver reports (or, for a scripting runtime, the name it
 * unwrapped from argv: an npm-installed Claude Code arrives as {@code claude-code}, Codex as
 * {@code codex}). Every code point here is checked against the bundled
 * {@code SymbolsNerdFontMono.ttf} by {@code ProcessGlyphsTest}, so an entry can never render as a
 * tofu box on a phone that has no other symbol face.
 *
 * <p>Unknown programs get {@link #DEFAULT}, the generic terminal, which is also what a chip shows
 * before the resolver has answered.
 */
public final class ProcessGlyphs {

    /** nf-dev-terminal: the generic terminal, for anything not in the table. */
    public static final int DEFAULT_CODE_POINT = 0xE795;
    public static final String DEFAULT = glyph(DEFAULT_CODE_POINT);

    private static final Map<String, Integer> CODE_POINTS = new HashMap<>();

    static {
        // --- coding agents ---
        put(0xEC82, "claude", "claude-code");                  // nf-cod-claude
        put(0xEC81, "codex");                                  // nf-cod-openai
        put(0xE7F0, "gemini");                                 // nf-dev-google
        put(0xEC1E, "copilot", "github-copilot");              // nf-cod-copilot
        put(0xEC20, "opencode", "aider", "cursor-agent", "goose", "amp"); // nf-cod-robot
        put(0xF0CC6, "herdr");                                 // nf-md-sheep: the herder's flock

        // --- shells ---
        put(0xF023A, "fish");                                  // nf-md-fish
        put(0xF1183, "bash", "sh", "zsh", "dash", "ksh", "mksh", "nu", "nushell"); // nf-md-bash
        put(0xEBC7, "pwsh", "powershell");                     // nf-cod-terminal_powershell

        // --- multiplexers and remote shells ---
        put(0xEBC8, "tmux", "screen");                         // nf-cod-terminal_tmux
        put(0xF0BCC, "zellij");                                // nf-md-view_split_vertical
        put(0xF08C0, "ssh", "mosh", "mosh-client", "et");      // nf-md-ssh
        put(0xF1065, "rsync", "scp", "sftp");                  // nf-md-transfer

        // --- editors ---
        put(0xE7C5, "vim", "vi");                              // nf-dev-vim
        put(0xE6AE, "nvim", "neovim");                         // nf-custom-neovim
        put(0xF0F6, "nano", "micro", "ne", "joe", "vis", "ed"); // nf-md-file_document_edit
        put(0xE632, "emacs", "emacsclient");                   // nf-custom-emacs
        put(0xEB0E, "hx", "helix", "kak", "kakoune");          // nf-cod-edit

        // --- version control ---
        put(0xE702, "git", "lazygit", "gitui", "tig", "jj");   // nf-dev-git
        put(0xF02A4, "gh");                                    // nf-md-github

        // --- languages and runtimes ---
        put(0xE73C, "python", "python3", "python2", "ipython", "pip", "pip3", "uv", "uvx"); // nf-dev-python
        put(0xE719, "node", "nodejs", "bun", "deno");          // nf-dev-nodejs
        put(0xE71E, "npm", "npx", "pnpm", "yarn");             // nf-dev-npm
        put(0xE7A8, "cargo", "rustc", "rustup");               // nf-dev-rust
        put(0xE724, "go");                                     // nf-dev-go
        put(0xE738, "java", "javac", "kotlin", "kotlinc");     // nf-dev-java
        put(0xE7F2, "gradle", "gradlew");                      // nf-dev-gradle
        put(0xE739, "ruby", "irb", "gem", "bundle");           // nf-dev-ruby
        put(0xE73D, "php", "composer");                        // nf-dev-php
        put(0xE620, "lua", "luajit");                          // nf-seti-lua
        put(0xE7A3, "gcc", "g++", "clang", "clang++", "cc", "c++"); // nf-dev-cplusplus
        put(0xEB6D, "make", "cmake", "ninja", "meson");        // nf-cod-tools

        // --- containers, systems, packages ---
        put(0xE7B0, "docker", "docker-compose", "podman");     // nf-dev-docker
        put(0xF10FE, "kubectl", "k9s", "helm");                // nf-md-kubernetes
        put(0xF1105, "nix", "nix-shell", "nix-env", "nix-build", "nixos-rebuild"); // nf-md-nix
        put(0xF0BAF, "pacman", "yay", "paru");                 // nf-md-pac_man
        put(0xF03D3, "apt", "apt-get", "dpkg", "pkg", "brew", "dnf", "zypper"); // nf-md-package
        put(0xE70E, "adb", "fastboot");                        // nf-dev-android

        // --- monitors and files ---
        put(0xF04C5, "top", "htop", "btop", "bpytop", "glances", "btm", "bottom", "nvtop"); // nf-md-speedometer
        put(0xF0770, "ranger", "yazi", "lf", "nnn", "mc", "broot", "vifm", "ncdu"); // nf-md-folder_open
        put(0xF05DA, "less", "more", "man", "bat", "glow", "most"); // nf-md-book_open_page_variant
        put(0xF0C7C, "rg", "ripgrep", "fzf", "grep", "fd", "find"); // nf-md-file_search

        // --- network and media ---
        put(0xF01DA, "curl", "wget", "aria2c", "yt-dlp", "youtube-dl"); // nf-md-download
        put(0xF0381, "mpv", "ffmpeg", "ffplay", "vlc");        // nf-md-movie
        put(0xE7C4, "sqlite3", "sqlite");                      // nf-dev-sqlite
        put(0xE76E, "psql");                                   // nf-dev-postgresql
        put(0xE704, "mysql", "mariadb");                       // nf-dev-mysql
        put(0xEC20, "ollama", "llama-cli", "llama-server");    // nf-cod-robot
    }

    private ProcessGlyphs() {}

    /** The glyph for {@code process}, or {@link #DEFAULT} for null, blank or unknown names. */
    @NonNull
    public static String forProcess(@Nullable String process) {
        Integer codePoint = codePointFor(process);
        return codePoint == null ? DEFAULT : glyph(codePoint);
    }

    /** The table entry for {@code process}, or null when the default would be used. */
    @Nullable
    public static Integer codePointFor(@Nullable String process) {
        if (process == null) return null;
        String key = process.trim().toLowerCase(Locale.ROOT);
        return key.isEmpty() ? null : CODE_POINTS.get(key);
    }

    /** True when the table names {@code process} specifically, so a caller can prefer it. */
    public static boolean knows(@Nullable String process) {
        return codePointFor(process) != null;
    }

    /** A copy of the table, for tests that check every entry against the font. */
    @NonNull
    static Map<String, Integer> table() {
        return new HashMap<>(CODE_POINTS);
    }

    private static void put(int codePoint, String... names) {
        for (String name : names) {
            Integer previous = CODE_POINTS.put(name, codePoint);
            if (previous != null) throw new IllegalStateException("duplicate glyph entry " + name);
        }
    }

    @NonNull
    static String glyph(int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
