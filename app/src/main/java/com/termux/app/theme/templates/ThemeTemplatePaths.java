package com.termux.app.theme.templates;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Expands the {@code output} path of a manifest.
 *
 * <p>Templates are ported from a desktop shell and name their outputs the way a shell would —
 * {@code ~/.config/starship.toml}, {@code $XDG_CONFIG_HOME/btop/themes/…}. No shell runs here, so
 * this does the small part of the expansion that is worth having: {@code ~}, {@code $VAR} and
 * {@code ${VAR}}, with the two XDG directories defaulting the way the specifications say when the
 * environment leaves them unset. An unknown variable expands to nothing, exactly as a shell does.
 */
public final class ThemeTemplatePaths {

    private final String mHome;

    private final Map<String, String> mEnvironment;

    public ThemeTemplatePaths(String home, Map<String, String> environment) {
        mHome = home;
        mEnvironment = new HashMap<>(environment);
        mEnvironment.put("HOME", home);
        if (isBlank(mEnvironment.get("XDG_CONFIG_HOME")))
            mEnvironment.put("XDG_CONFIG_HOME", home + "/.config");
        if (isBlank(mEnvironment.get("XDG_CACHE_HOME")))
            mEnvironment.put("XDG_CACHE_HOME", home + "/.cache");
    }

    public String home() {
        return mHome;
    }

    /** The raw manifest value as an absolute path. */
    public String expand(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return value;
        if (value.equals("~")) {
            value = mHome;
        } else if (value.startsWith("~/")) {
            value = mHome + value.substring(1);
        }
        return expandVariables(value);
    }

    private String expandVariables(String value) {
        StringBuilder out = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (current != '$') {
                out.append(current);
                index++;
                continue;
            }
            int nameStart = index + 1;
            int nameEnd;
            boolean braced = nameStart < value.length() && value.charAt(nameStart) == '{';
            if (braced) {
                nameStart++;
                nameEnd = value.indexOf('}', nameStart);
                if (nameEnd < 0) {
                    // An unclosed ${ is not a variable at all; keep the text as written.
                    out.append(value.substring(index));
                    break;
                }
            } else {
                nameEnd = nameStart;
                while (nameEnd < value.length() && isNameCharacter(value.charAt(nameEnd))) nameEnd++;
                if (nameEnd == nameStart) {
                    out.append(current);
                    index++;
                    continue;
                }
            }
            String name = value.substring(nameStart, nameEnd);
            String replacement = mEnvironment.get(name);
            out.append(replacement == null ? "" : replacement);
            index = braced ? nameEnd + 1 : nameEnd;
        }
        return out.toString();
    }

    private static boolean isNameCharacter(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Whether {@code file} already holds exactly these bytes.
     *
     * <p>Every file this feature writes — a rendered config, an unpacked template, the applied
     * ledger — is written only when the answer is no. Tools watch their config files by modification
     * time, so rewriting identical content is not free: it reloads them for nothing.
     */
    public static boolean sameOnDisk(File file, byte[] wanted) {
        if (file == null || !file.isFile() || file.length() != wanted.length) return false;
        try {
            return Arrays.equals(Files.readAllBytes(file.toPath()), wanted);
        } catch (IOException e) {
            return false;
        }
    }
}
