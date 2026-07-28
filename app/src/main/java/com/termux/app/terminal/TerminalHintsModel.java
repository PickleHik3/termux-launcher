package com.termux.app.terminal;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds keyboard-addressable URLs, paths, hashes, and source line references. */
final class TerminalHintsModel {

    static final int MAX_HINTS = 36;
    private static final String LABELS = "asdfghjklqwertyuiopzxcvbnm1234567890";

    enum Type { URL, LINE, PATH, HASH }

    static final class Hint {
        final char label;
        final Type type;
        final String value;
        final int offset;

        Hint(char label, Type type, String value, int offset) {
            this.label = label;
            this.type = type;
            this.value = value;
            this.offset = offset;
        }
    }

    private static final Pattern URL = Pattern.compile(
        "(?i)(?:\\b(?:https?|ftps?|mailto|tel|sms|geo):[^\\s<>\\\"']+)");
    private static final Pattern LINE = Pattern.compile(
        "(?<![\\w])(?:(?:~?/|\\.{1,2}/)?[\\w.@%+=~-]+(?:/[\\w.@%+=~-]+)*|"
            + "[\\w.@%+=~-]+\\.[A-Za-z0-9]{1,12}):\\d+(?::\\d+)?");
    private static final Pattern PATH = Pattern.compile(
        "(?<![\\w])(?:(?:~?/|\\.{1,2}/)[\\w.@%+,=:~-]+(?:/[\\w.@%+,=:~-]+)*|"
            + "[\\w.@%+=~-]+(?:/[\\w.@%+,=:~-]+)+|"
            + "[\\w.@%+=~-]+\\.(?:java|kt|kts|py|js|ts|tsx|jsx|go|rs|c|cc|cpp|h|hpp|"
            + "sh|bash|zsh|fish|md|txt|json|ya?ml|toml|xml|gradle|properties))",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern HASH = Pattern.compile(
        "(?<![A-Fa-f0-9])[A-Fa-f0-9]{7,64}(?![A-Fa-f0-9])");

    private TerminalHintsModel() {}

    @NonNull
    static List<Hint> extract(@NonNull String transcript) {
        List<Candidate> candidates = new ArrayList<>();
        collect(transcript, LINE, Type.LINE, candidates);
        collect(transcript, URL, Type.URL, candidates);
        collect(transcript, PATH, Type.PATH, candidates);
        collect(transcript, HASH, Type.HASH, candidates);

        // Higher-priority candidates were inserted first. Remove nested matches
        // (for example /example inside https://example) and duplicate values,
        // then label the most recent occurrences first.
        List<Candidate> accepted = new ArrayList<>();
        Set<String> seenValues = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (overlapsAny(candidate, accepted)) continue;
            String identity = candidate.type + "\u0000" + candidate.value;
            if (!seenValues.add(identity)) continue;
            accepted.add(candidate);
        }
        Collections.sort(accepted, Comparator.comparingInt((Candidate c) -> c.start).reversed());

        List<Hint> result = new ArrayList<>(Math.min(MAX_HINTS, accepted.size()));
        for (int i = 0; i < accepted.size() && i < MAX_HINTS; i++) {
            Candidate candidate = accepted.get(i);
            result.add(new Hint(LABELS.charAt(i), candidate.type, candidate.value, candidate.start));
        }
        return result;
    }

    private static void collect(String text, Pattern pattern, Type type, List<Candidate> output) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = trimTrailingPunctuation(matcher.group());
            if (!value.isEmpty()) {
                output.add(new Candidate(type, value, matcher.start(), matcher.start() + value.length()));
            }
        }
    }

    private static String trimTrailingPunctuation(String value) {
        while (!value.isEmpty()) {
            char last = value.charAt(value.length() - 1);
            if (last == '.' || last == ',' || last == ';' || last == ')' || last == ']')
                value = value.substring(0, value.length() - 1);
            else break;
        }
        return value;
    }

    private static boolean overlapsAny(Candidate candidate, List<Candidate> accepted) {
        for (Candidate other : accepted) {
            if (candidate.start < other.end && other.start < candidate.end) return true;
        }
        return false;
    }

    private static final class Candidate {
        final Type type;
        final String value;
        final int start;
        final int end;

        Candidate(Type type, String value, int start, int end) {
            this.type = type;
            this.value = value;
            this.start = start;
            this.end = end;
        }
    }
}
