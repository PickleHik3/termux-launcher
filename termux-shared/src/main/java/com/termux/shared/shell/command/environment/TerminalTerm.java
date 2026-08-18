package com.termux.shared.shell.command.environment;

/** Pure validation and defaulting for the TERM value exported to child processes. */
public final class TerminalTerm {

    public static final String DEFAULT_VALUE = "xterm-256color";

    private TerminalTerm() {}

    /** Return a safe configured terminal name, preserving the historical default when unset. */
    public static String resolve(String configured) {
        if (configured == null) return DEFAULT_VALUE;
        String value = configured.trim();
        if (value.isEmpty() || value.length() > 128) return DEFAULT_VALUE;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return DEFAULT_VALUE;
        }
        return value;
    }
}
