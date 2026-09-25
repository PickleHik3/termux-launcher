package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * The spoken words that press a key instead of typing text. Only a segment whose <em>entire</em>
 * normalized transcript is one of these matches: "enter the directory" said in one breath stays
 * text; "enter key" said on its own, after a pause, presses Enter.
 *
 * <p>By default a command word needs a trailing "key" ("enter key", "tab key", "control c key"):
 * plain dictation says "enter" and "key" on their own far more often than a terminal command does,
 * so the bare word stays text. The "Bare command words" keyboard setting restores the original
 * bare-word matching for anyone who finds the suffix awkward to say.
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

    /**
     * The short chip the pill shows for ~1 s after this command's key is sent: an icon (or
     * abbreviation) plus the key's name, so "enter key" reads back as "⏎ Enter" rather than the
     * transcript that triggered it.
     */
    @NonNull
    public String chipLabel() {
        switch (this) {
            case ENTER: return "⏎ Enter";
            case TAB: return "⇥ Tab";
            case ESC: return "Esc";
            case BACKSPACE: return "⌫";
            case SPACE: return "Space";
            case CTRL_C: return "Ctrl+C";
            default: return name();
        }
    }

    /** The trailing word a command needs by default, so plain dictation of the bare word stays text. */
    private static final String KEY_SUFFIX = " key";

    /**
     * The command {@code transcript} is, or {@code null} when it is text. In the default mode
     * ({@code bareWordsAllowed} false) the transcript must end in "key" ("enter key"); with it
     * true, the bare word alone also matches, as the classifier did before this suffix.
     */
    @Nullable
    public static VoiceCommand classify(@Nullable String transcript, boolean bareWordsAllowed) {
        if (transcript == null) return null;
        String text = normalize(transcript);
        if (bareWordsAllowed) {
            VoiceCommand bare = matchWord(text);
            if (bare != null) return bare;
        }
        // Whisper small.en writes "control c key" as "c key" (or "ckey") on most voices in the
        // replay rig: the bias line's "ctrl" splits into odd pieces and "control" is dropped. A
        // lone "c key" is not something anyone dictates, so it is Ctrl+C.
        if ("ckey".equals(text)) text = "c" + KEY_SUFFIX;
        if (!text.endsWith(KEY_SUFFIX)) return null;
        String word = text.substring(0, text.length() - KEY_SUFFIX.length());
        if ("c".equals(word)) return CTRL_C;
        return matchWord(word);
    }

    /** {@code text}, with no "key" suffix considered, against the known command words. */
    @Nullable
    private static VoiceCommand matchWord(@NonNull String text) {
        switch (text) {
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
