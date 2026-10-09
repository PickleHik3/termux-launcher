package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Dictated shell commands, written as commands (device check B, 2026-09-27): "ls dash la" is
 * {@code ls -la}, "L S dash La" is {@code ls -la}, "cd slash home slash user" is
 * {@code cd /home/user}. Deterministic and table-driven, no model: it runs on every dictation that
 * starts with a command name, whatever its length and whether cleanup is on, and the cleanup model
 * is never sent such text ({@link VoicePolishRules#skipReason} says {@code command}), so nothing
 * can undo it.
 *
 * <p>What it does, and nothing more:
 * <ul>
 *   <li>the command is found case-insensitively ({@code Git}, {@code LS}), spelled single letters
 *       at the start join ("L S" is {@code ls}, "C D" is {@code cd}), and a path ({@code ./gradlew},
 *       {@code /usr/bin/env}, spoken "dot slash gradlew") counts as a command too;</li>
 *   <li>the spoken symbols of {@link #SYMBOLS} become characters: "dash dash X" is {@code --X} and
 *       "dash X" is {@code -X}, glued to the next word (spelled letters after a dash join, "dash L A"
 *       is {@code -la}); dot, slash, equals and underscore glue to their neighbours
 *       ({@code file.txt}, {@code /home/user}, {@code --color=auto}); pipe stands alone;</li>
 *   <li>the command name and the flags are lower-cased, the recognizer's trailing {@code . ? !}
 *       and its commas go, and hesitations ("um", "uh") are dropped;</li>
 *   <li>everything else is kept as heard.</li>
 * </ul>
 *
 * <p>Text that does not start with a command name is prose and is left alone. A few command names
 * are everyday words that open sentences ("Make sure…", "Find the file…", "Claude, can you…"):
 * those count as a command only in a dictation of at most two words or one that says a symbol or
 * a flag ({@link #AMBIGUOUS}).
 */
public final class VoiceCommandFormatter {

    /**
     * Command names that are also words a sentence opens with. They make a command only when the
     * dictation is at most {@link #AMBIGUOUS_MAX_WORDS} words or has a spoken symbol, a flag or a
     * path in it.
     */
    static final Set<String> AMBIGUOUS = new HashSet<>(Arrays.asList(
        "cat", "claude", "clear", "diff", "echo", "exit", "export", "find", "fish", "go", "head",
        "java", "kill", "less", "make", "man", "more", "node", "python", "source", "tail", "top",
        "touch", "which", "yarn"));
    static final int AMBIGUOUS_MAX_WORDS = 2;

    /** Spoken symbols and what they become. "dash" is handled on its own (flags). */
    static final Map<String, String> SYMBOLS;

    static {
        Map<String, String> symbols = new HashMap<>();
        symbols.put("dot", ".");
        symbols.put("slash", "/");
        symbols.put("tilde", "~");
        symbols.put("pipe", "|");
        symbols.put("star", "*");
        symbols.put("asterisk", "*");
        symbols.put("equals", "=");
        symbols.put("underscore", "_");
        SYMBOLS = Collections.unmodifiableMap(symbols);
    }

    private static final String DASH = "dash";
    /** Hesitations the recognizer writes out; never part of a command. */
    private static final Set<String> HESITATIONS = new HashSet<>(Arrays.asList(
        "um", "umm", "uh", "uhh", "uhm", "erm", "hmm"));
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[,.;:?!]+|[,.;:?!]+$");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private static final Pattern TRAILING_COMMA = Pattern.compile("[,;]+$");
    private static final Pattern TRAILING_SENTENCE_END = Pattern.compile("[,;.?!]+$");
    /** A dot between two word characters: a file name or a host, typed out rather than spoken. */
    private static final Pattern INNER_DOT = Pattern.compile(".*[\\p{L}\\p{Nd}]\\.[\\p{L}\\p{Nd}].*");

    private VoiceCommandFormatter() {
    }

    /** Whether {@code text} starts with a command name and so is written as a command. */
    public static boolean isCommand(@NonNull String text) {
        return format(text) != null;
    }

    /**
     * {@code text} written as a shell command, or {@code null} when it does not start with a
     * command name (prose, left as it is). Running it again on its own output changes nothing.
     */
    @Nullable
    public static String format(@NonNull String text) {
        List<String> tokens = new ArrayList<>();
        for (String token : WHITESPACE.split(text.trim())) {
            if (token.isEmpty() || HESITATIONS.contains(key(token))) continue;
            tokens.add(token);
        }
        if (tokens.isEmpty()) return null;

        List<Piece> pieces = new ArrayList<>();
        int next = head(tokens, pieces);
        if (next < 0) return null;
        if (!pieces.isEmpty() && AMBIGUOUS.contains(pieces.get(0).text)
            && !looksLikeCommand(tokens.subList(next, tokens.size()))) return null;

        int count = tokens.size();
        int i = next;
        while (i < count) {
            String word = key(tokens.get(i));
            boolean last = i == count - 1;
            Piece previous = pieces.isEmpty() ? null : pieces.get(pieces.size() - 1);
            if (DASH.equals(word)) {
                int dashes = 1;
                if (i + 1 < count && DASH.equals(key(tokens.get(i + 1)))) dashes = 2;
                int after = i + dashes;
                if (after >= count || isSpoken(tokens.get(after))) {
                    // A dash on its own: "cd dash" is cd -.
                    pieces.add(new Piece(dashes == 2 ? "--" : "-", Kind.SYMBOL, false, after < count));
                    i = after;
                    continue;
                }
                // The flag's name: the next word, or spelled letters joined ("dash L A" is -la).
                StringBuilder name = new StringBuilder();
                int end = after;
                if (isSingleLetter(tokens.get(end))) {
                    while (end < count && isSingleLetter(tokens.get(end))) name.append(letterOf(tokens.get(end++)));
                } else {
                    name.append(plain(tokens.get(end), end == count - 1));
                    end++;
                }
                String flag = name.toString().toLowerCase(Locale.ROOT);
                if (dashes == 1 && previous != null && previous.kind == Kind.FLAG && previous.text.startsWith("--")) {
                    // "dash dash dry dash run" is --dry-run, not --dry -run.
                    previous.text = previous.text + "-" + flag;
                } else {
                    pieces.add(new Piece((dashes == 2 ? "--" : "-") + flag, Kind.FLAG, false, false));
                }
                i = end;
                continue;
            }
            String symbol = SYMBOLS.get(word);
            if (symbol != null) {
                pieces.add(symbolPiece(word, symbol, previous, i + 1 < count ? key(tokens.get(i + 1)) : null));
                i++;
                continue;
            }
            String token = plain(tokens.get(i), last);
            if (token.startsWith("-") && token.length() > 1) {
                pieces.add(new Piece(token.toLowerCase(Locale.ROOT), Kind.FLAG, false, false));
            } else if (!token.isEmpty()) {
                pieces.add(new Piece(token, Kind.WORD, false, false));
            }
            i++;
        }
        return render(pieces);
    }

    // ------------------------------------------------------------------ the command name

    /**
     * Puts the command name (or the path) into {@code pieces} and returns the index of the first
     * token after it, or -1 when {@code tokens} does not start with one.
     */
    private static int head(@NonNull List<String> tokens, @NonNull List<Piece> pieces) {
        String first = tokens.get(0);
        String bare = TRAILING_SENTENCE_END.matcher(first).replaceAll("");
        if (bare.startsWith("./") || bare.startsWith("/") || bare.startsWith("~/")) {
            pieces.add(new Piece(bare, Kind.HEAD, false, false));
            return 1;
        }
        // Spoken "dot slash gradlew": the symbols make the path, no command name to find.
        if (tokens.size() > 2 && "dot".equals(key(first)) && "slash".equals(key(tokens.get(1)))) return 0;
        // Spelled letters: the longest run of two or more that names a command.
        int letters = 0;
        while (letters < tokens.size() && isSingleLetter(tokens.get(letters))) letters++;
        for (int run = letters; run >= 2; run--) {
            StringBuilder joined = new StringBuilder();
            for (int k = 0; k < run; k++) joined.append(letterOf(tokens.get(k)));
            String name = joined.toString().toLowerCase(Locale.ROOT);
            if (VoicePolishRules.COMMAND_NAMES.contains(name)) {
                pieces.add(new Piece(name, Kind.HEAD, false, false));
                return run;
            }
        }
        String name = NOT_ALPHANUMERIC.matcher(first.toLowerCase(Locale.ROOT)).replaceAll("");
        if (!VoicePolishRules.COMMAND_NAMES.contains(name)) return -1;
        pieces.add(new Piece(name, Kind.HEAD, false, false));
        return 1;
    }

    /** For an {@link #AMBIGUOUS} command name: what follows it is short, or says a symbol, a flag or a path. */
    private static boolean looksLikeCommand(@NonNull List<String> rest) {
        if (rest.size() + 1 <= AMBIGUOUS_MAX_WORDS) return true;
        for (String token : rest) {
            String word = key(token);
            if (DASH.equals(word) || SYMBOLS.containsKey(word)) return true;
            if (token.startsWith("-") || token.contains("/") || token.contains("|")
                || token.contains("=") || token.contains("~") || token.contains("*")) return true;
            if (INNER_DOT.matcher(token).matches()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ symbols

    /**
     * One spoken symbol, with how it joins its neighbours. Dot, slash, equals and underscore join
     * the word before them (never the command name: "cd dot dot" is cd .., "cd slash" is cd /) and
     * the word after them (never a flag); a dot that ends the command stands alone ("git add dot"
     * is git add .).
     * Tilde joins a slash after it; pipe and star join nothing themselves.
     */
    @NonNull
    private static Piece symbolPiece(@NonNull String word, @NonNull String symbol, @Nullable Piece previous,
                                     @Nullable String following) {
        // Never onto a flag: "find dot dash name" is find . -name.
        boolean hasNext = following != null && !DASH.equals(following);
        boolean afterWord = previous != null && previous.kind != Kind.HEAD;
        switch (word) {
            case "dot":
                return new Piece(symbol, Kind.SYMBOL, afterWord && previous.kind != Kind.FLAG && hasNext, hasNext);
            case "slash":
                return new Piece(symbol, Kind.SYMBOL, afterWord && previous.kind != Kind.FLAG, hasNext);
            case "equals":
            case "underscore":
                return new Piece(symbol, Kind.SYMBOL, afterWord, hasNext);
            case "tilde":
                return new Piece(symbol, Kind.SYMBOL, false, "slash".equals(following));
            default:
                return new Piece(symbol, Kind.SYMBOL, false, false);
        }
    }

    private static boolean isSpoken(@NonNull String token) {
        String word = key(token);
        return DASH.equals(word) || SYMBOLS.containsKey(word);
    }

    // ------------------------------------------------------------------ tokens

    /** A token compared as a word: lower-case, the recognizer's punctuation off its edges. */
    @NonNull
    private static String key(@NonNull String token) {
        return EDGE_PUNCTUATION.matcher(token.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /** "L", "l.", "S," — one letter, as spelled. */
    private static boolean isSingleLetter(@NonNull String token) {
        String letters = NOT_ALPHANUMERIC.matcher(token).replaceAll("");
        return letters.length() == 1 && Character.isLetter(letters.charAt(0))
            && token.length() <= 2;
    }

    @NonNull
    private static String letterOf(@NonNull String token) {
        return NOT_ALPHANUMERIC.matcher(token).replaceAll("");
    }

    /**
     * An ordinary word as typed: a trailing comma off it, and on the last one the sentence's end
     * too — unless that is all there is, as in {@code git add .}.
     */
    @NonNull
    private static String plain(@NonNull String token, boolean last) {
        String stripped = (last ? TRAILING_SENTENCE_END : TRAILING_COMMA).matcher(token).replaceAll("");
        return stripped.isEmpty() ? token : stripped;
    }

    @NonNull
    private static String render(@NonNull List<Piece> pieces) {
        StringBuilder out = new StringBuilder();
        Piece previous = null;
        for (Piece piece : pieces) {
            if (previous != null && !previous.glueRight && !piece.glueLeft) out.append(' ');
            out.append(piece.text);
            previous = piece;
        }
        return out.toString();
    }

    private enum Kind { HEAD, FLAG, SYMBOL, WORD }

    private static final class Piece {
        @NonNull String text;
        final Kind kind;
        final boolean glueLeft;
        final boolean glueRight;

        Piece(@NonNull String text, @NonNull Kind kind, boolean glueLeft, boolean glueRight) {
            this.text = text;
            this.kind = kind;
            this.glueLeft = glueLeft;
            this.glueRight = glueRight;
        }
    }
}
