package com.termux.privileged;

/**
 * The command line behind the "Root shell" pane. Pure so the line can be tested.
 *
 * <p>The outer {@code sh} expands Termux's own variables before {@code su} resets the environment,
 * so root gets the same PATH, PREFIX, TMPDIR and preload as the user's shell and keeps the Termux
 * tools. HOME moves to {@code ~/.suroot}, as Termux's tsu does, so root-owned dotfiles never land
 * in the user's home.
 */
public final class RootShellCommand {

    /** Where root's HOME lives, under the user's own HOME. */
    public static final String ROOT_HOME_DIR = ".suroot";

    private static final String LINE =
        "mkdir -p \"$HOME/" + ROOT_HOME_DIR + "\" && exec su -c \""
            + "export PATH='$PATH:/system/bin' PREFIX='$PREFIX' TMPDIR='$TMPDIR'"
            + " LD_PRELOAD='$LD_PRELOAD' LANG='$LANG' TERM='$TERM' COLORTERM='$COLORTERM'"
            + " HOME='$HOME/" + ROOT_HOME_DIR + "'; cd '$PWD' 2>/dev/null;"
            + " exec \\\"\\${SHELL:-$PREFIX/bin/sh}\\\" -l\"";

    private RootShellCommand() {}

    /** The line to hand to {@code sh -c}. */
    public static String line() {
        return LINE;
    }
}
