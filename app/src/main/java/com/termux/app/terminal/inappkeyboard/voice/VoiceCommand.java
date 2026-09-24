package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The spoken words that press a key instead of typing text. Only a segment whose <em>entire</em>
 * normalized transcript is one of these matches: "enter the directory" said in one breath stays
 * text; "enter" said on its own, after a pause, presses Enter.
 */
public enum VoiceCommand {
    ENTER("enter", false),
    TAB("tab", false),
    ESC("esc", false),
    BACKSPACE("backspace", false),
    SPACE("space", false),
    /** Ctrl+C: the character key "c" under the Ctrl modifier, the way the keyboard sends it. */
    CTRL_C("c", true);

    /** The key's name as a layout file names it, for {@code KeyValue.getKeyByName}. */
    public final String keyName;
    /** Whether the key is sent under the Ctrl modifier. */
    public final boolean ctrl;

    VoiceCommand(String keyName, boolean ctrl) {
        this.keyName = keyName;
        this.ctrl = ctrl;
    }

    /** The command {@code transcript} is, or {@code null} when it is text. */
    @Nullable
    public static VoiceCommand classify(@Nullable String transcript) {
        if (transcript == null) return null;
        switch (normalize(transcript)) {
            case "enter":
            case "return":
            case "send":
            case "submit":
                return ENTER;
            case "tab":
                return TAB;
            case "escape":
                return ESC;
            case "backspace":
            case "delete":
                return BACKSPACE;
            case "space":
                return SPACE;
            case "ctrl c":
            case "cancel":
                return CTRL_C;
            default:
                return null;
        }
    }

    /**
     * Lowercased, punctuation turned into spaces, whitespace collapsed, "control" said as "ctrl":
     * "Control-C." and "ctrl c" are the same command.
     */
    @NonNull
    static String normalize(@NonNull String transcript) {
        String text = transcript.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
        return text.replaceAll("\\bcontrol\\b", "ctrl");
    }
}
