package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Tasks widget's file format: markdown checklist lines, {@code - [ ] text} and
 * {@code - [x] text}, each with an optional trailing {@code @due} token ({@code @today},
 * {@code @16:00}, {@code @Wed}) that is shown as the due label rather than as part of the text.
 * Every other line is kept verbatim and ignored, so the widget can share the file with whatever
 * else the user writes in it. Edits change one character or add one line and leave the rest of
 * the file — line endings included — exactly as it was.
 */
public final class TaskListParser {
    private TaskListParser() { }

    /** {@code - [ ] text}, also with {@code *} or {@code +} bullets and indentation. */
    private static final Pattern TASK = Pattern.compile("^\\s*[-*+]\\s+\\[([ xX])\\]\\s+(.*?)\\s*$");
    /** A trailing {@code @token} separated from the text by whitespace. */
    private static final Pattern DUE = Pattern.compile("^(.*?)\\s+@(\\S+)$");

    /** One checklist line of the file. */
    public static final class Task {
        /** Zero-based line number in the file. */
        public final int line;
        /** The line exactly as the file holds it, so an edit can check it is still there. */
        @NonNull public final String raw;
        @NonNull public final String text;
        /** The due label without its {@code @}, or null. */
        @Nullable public final String due;
        public final boolean done;

        Task(int line, @NonNull String raw, @NonNull String text, @Nullable String due, boolean done) {
            this.line = line; this.raw = raw; this.text = text; this.due = due; this.done = done;
        }
    }

    /** Every task in {@code content}, in file order. */
    @NonNull public static List<Task> parse(@Nullable String content) {
        if (content == null || content.isEmpty()) return Collections.emptyList();
        String[] lines = content.split("\n", -1);
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Task task = parseLine(i, lines[i]);
            if (task != null) tasks.add(task);
        }
        return tasks;
    }

    @Nullable static Task parseLine(int index, @NonNull String raw) {
        Matcher matcher = TASK.matcher(raw);
        if (!matcher.matches()) return null;
        String body = matcher.group(2);
        if (body == null || body.isEmpty()) return null;
        boolean done = !" ".equals(matcher.group(1));
        String due = null;
        Matcher dueMatcher = DUE.matcher(body);
        if (dueMatcher.matches() && !dueMatcher.group(1).trim().isEmpty()) {
            body = dueMatcher.group(1).trim();
            due = dueMatcher.group(2);
        }
        return new Task(index, raw, body, due, done);
    }

    /** The tasks still open first, then the done ones, each group in file order. */
    @NonNull public static List<Task> ordered(@NonNull List<Task> tasks) {
        List<Task> out = new ArrayList<>(tasks.size());
        for (Task task : tasks) if (!task.done) out.add(task);
        for (Task task : tasks) if (task.done) out.add(task);
        return out;
    }

    public static int openCount(@NonNull List<Task> tasks) {
        int open = 0;
        for (Task task : tasks) if (!task.done) open++;
        return open;
    }

    /** {@code content} with the checkbox on {@code line} flipped; null when that line is no task. */
    @Nullable public static String toggle(@NonNull String content, int line) {
        String[] lines = content.split("\n", -1);
        if (line < 0 || line >= lines.length) return null;
        return flip(lines, line);
    }

    /**
     * Flips the task that was read as {@code expectedRaw} on {@code line}. If the file moved under
     * the widget, the first line still reading {@code expectedRaw} is flipped instead; if none
     * does, nothing is changed and null is returned.
     */
    @Nullable public static String toggle(@NonNull String content, int line, @NonNull String expectedRaw) {
        String[] lines = content.split("\n", -1);
        int target = -1;
        if (line >= 0 && line < lines.length && lines[line].equals(expectedRaw)) {
            target = line;
        } else {
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].equals(expectedRaw)) { target = i; break; }
            }
        }
        return target < 0 ? null : flip(lines, target);
    }

    @Nullable private static String flip(@NonNull String[] lines, int index) {
        Matcher matcher = TASK.matcher(lines[index]);
        if (!matcher.matches()) return null;
        int at = matcher.start(1);
        char next = " ".equals(matcher.group(1)) ? 'x' : ' ';
        String line = lines[index];
        lines[index] = line.substring(0, at) + next + line.substring(at + 1);
        return String.join("\n", lines);
    }

    /** {@code content} with {@code - [ ] text} added as the last line; null for blank text. */
    @Nullable public static String append(@Nullable String content, @Nullable String text) {
        if (text == null) return null;
        String clean = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (clean.isEmpty()) return null;
        String current = content == null ? "" : content;
        String newline = current.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder out = new StringBuilder(current.length() + clean.length() + 8).append(current);
        if (current.length() > 0 && !current.endsWith("\n")) out.append(newline);
        return out.append("- [ ] ").append(clean).append(newline).toString();
    }
}
