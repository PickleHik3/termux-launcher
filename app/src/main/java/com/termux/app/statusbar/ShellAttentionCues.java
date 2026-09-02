package com.termux.app.statusbar;

import java.util.regex.Pattern;

/**
 * Whether a screen looks like a program waiting for an answer: the approval and choice prompts of
 * the agent CLIs, and the yes/no questions of ordinary tools. Read only when a command has just gone
 * quiet, to tell "asking you" from "finished". Deliberately a short list of strong cues — a false
 * bell is worse than a tick, since a tick is what the quiet spell means anyway.
 */
public final class ShellAttentionCues {

    /** How many of the screen's last lines are read. A prompt sits at the bottom. */
    static final int TAIL_LINES = 14;

    /** A highlighted numbered choice: "❯ 1. Yes", "› 2. No, tell it what to do". */
    private static final Pattern SELECTED_CHOICE =
        Pattern.compile("(?m)^\\s*[❯›>▸▶●◉]\\s*\\d+[.)]\\s+\\S");
    /** A yes/no question at the end of a line: "(y/n)", "[Y/n]:", "(yes/no)?". */
    private static final Pattern YES_NO =
        Pattern.compile("(?mi)[\\[(](?:y/n|yes/no|y/N|Y/n)[\\])]\\s*[:?]?\\s*$");
    /** A question the agent CLIs ask in words. */
    private static final Pattern ASKING =
        Pattern.compile("(?mi)^\\s*(?:do you want to|would you like to|allow|approve|proceed)\\b.*\\?\\s*$"
            + "|(?mi)^\\s*press enter to continue");

    private ShellAttentionCues() {}

    public static boolean looksLikeQuestion(String screen) {
        if (screen == null || screen.isEmpty()) return false;
        String tail = tail(screen, TAIL_LINES);
        return SELECTED_CHOICE.matcher(tail).find() || YES_NO.matcher(tail).find()
            || ASKING.matcher(tail).find();
    }

    /** The last {@code lines} non-blank lines of {@code text}. */
    static String tail(String text, int lines) {
        String[] all = text.split("\n");
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (int i = all.length - 1; i >= 0 && kept < lines; i--) {
            if (all[i].trim().isEmpty()) continue;
            out.insert(0, all[i] + "\n");
            kept++;
        }
        return out.toString();
    }
}
