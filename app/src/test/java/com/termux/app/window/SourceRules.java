package com.termux.app.window;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The scanning half of {@code WindowSourceRulesTest} (ADR 0028 sections 3, 6 and 7). Pure functions
 * over a map of source path to source text, so each rule is tested on synthetic sources. Paths are
 * relative to {@code com/termux}, for example {@code app/chrome/ChromeRenderer.java}; comments and
 * string literals are blanked before any pattern runs.
 */
final class SourceRules {

    /** Rank in the import direction: window, glass, page, then every element. */
    static int rank(String node) {
        switch (node) {
            case "window": return 0;
            case "glass": return 1;
            case "page": return 2;
            default: return 3;
        }
    }

    static final List<String> NEW_PACKAGES = Arrays.asList("window", "glass", "page");

    /** The pipeline's package: the only home of frame hooks. */
    static final String FRAME_HOOK_HOME = "app/window/";

    static final Pattern FRAME_HOOK = Pattern.compile(
        "\\b(addOnPreDrawListener|addOnDrawListener|postFrameCallback(Delayed)?"
            + "|postOnAnimation(Delayed)?)\\s*\\(");
    static final Pattern BRING_TO_FRONT = Pattern.compile("\\bbringToFront\\s*\\(\\s*\\)");
    static final Pattern Z_USE = Pattern.compile(
        "\\b(setElevation|setTranslationZ|setZ|translationZ|elevation)\\s*\\(");
    static final Pattern ADD_VIEW = Pattern.compile("\\b(addView|addContentView)\\s*\\(");
    static final Pattern DECOR_OR_CONTENT = Pattern.compile(
        "\\bgetDecorView\\s*\\(|\\bandroid\\.R\\.id\\.content\\b|\\baddContentView\\s*\\(");
    static final Pattern ACTIVITY = Pattern.compile("\\bcom\\.termux\\.app\\.TermuxActivity\\b");
    private static final Pattern IMPORT = Pattern.compile(
        "(?m)^\\s*import\\s+(?:static\\s+)?(com\\.termux\\.[\\w.]+?)(?:\\.\\*)?\\s*;");
    private static final Pattern HOST = Pattern.compile("\\binterface\\s+(\\w*Host)\\b[^{;]*\\{");
    private static final Pattern ANNOTATION = Pattern.compile("@\\w+(?:\\.\\w+)*(?:\\([^)]*\\))?");

    private SourceRules() {}

    /** Every java file under {@code comTermux}, keyed by path relative to it. */
    static Map<String, String> load(Path comTermux) throws IOException {
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> files = Files.walk(comTermux)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String key = comTermux.relativize(file).toString().replace('\\', '/');
                sources.put(key, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    /** Source with comments and string and char literals blanked (newlines kept). */
    static String code(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0, n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            char d = i + 1 < n ? text.charAt(i + 1) : 0;
            if (c == '/' && d == '/') {
                while (i < n && text.charAt(i) != '\n') i++;
            } else if (c == '/' && d == '*') {
                i += 2;
                while (i < n && !(text.charAt(i) == '*' && i + 1 < n && text.charAt(i + 1) == '/')) {
                    if (text.charAt(i) == '\n') out.append('\n');
                    i++;
                }
                i += 2;
            } else if (c == '"' || c == '\'') {
                out.append(c);
                i++;
                while (i < n && text.charAt(i) != c) {
                    if (text.charAt(i) == '\\') i++;
                    i++;
                }
                out.append(c);
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** The package node a path belongs to: the dir under {@code app}, {@code app} for its root. */
    static String nodeOfPath(String path) {
        String[] seg = path.split("/");
        if (seg.length < 2) return "";
        if (seg[0].equals("app")) return seg.length > 2 ? seg[1] : "app";
        return seg[0];
    }

    /** The node of an imported name: its package segments are those before the first class name. */
    static String nodeOfImport(String name) {
        String[] seg = name.split("\\.");
        StringBuilder path = new StringBuilder();
        for (int i = 2; i < seg.length; i++) {
            if (Character.isUpperCase(seg[i].charAt(0))) break;
            path.append(seg[i]).append('/');
        }
        path.append("X.java");
        return nodeOfPath(path.toString());
    }

    private static boolean isRootOfApp(String path) {
        return path.startsWith("app/") && path.indexOf('/', 4) < 0;
    }

    private static Map<String, Integer> countMatches(Map<String, String> sources, Pattern pattern,
                                                    boolean skipFrameHome) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            if (skipFrameHome && e.getKey().startsWith(FRAME_HOOK_HOME)) continue;
            Matcher m = pattern.matcher(code(e.getValue()));
            int n = 0;
            while (m.find()) n++;
            if (n > 0) counts.put(e.getKey(), n);
        }
        return counts;
    }

    /** Frame hooks outside the pipeline's package: file to count. */
    static Map<String, Integer> frameHooks(Map<String, String> sources) {
        return countMatches(sources, FRAME_HOOK, true);
    }

    static Map<String, Integer> bringToFront(Map<String, String> sources) {
        return countMatches(sources, BRING_TO_FRONT, false);
    }

    /** Elevation or translationZ writes: shadow or order cannot be told apart, so every one is listed. */
    static Map<String, Integer> zWrites(Map<String, String> sources) {
        return countMatches(sources, Z_USE, false);
    }

    /**
     * Sites that reach the window decor or the content view in a file that also adds views: the
     * proxy for "adds a view to the decor or beside the root". File to count of such sites.
     */
    static Map<String, Integer> decorOrContentAdds(Map<String, String> sources) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String code = code(e.getValue());
            if (!ADD_VIEW.matcher(code).find()) continue;
            Matcher m = DECOR_OR_CONTENT.matcher(code);
            int n = 0;
            while (m.find()) n++;
            if (n > 0) counts.put(e.getKey(), n);
        }
        return counts;
    }

    /** Files outside the activity's own package that name the activity. */
    static Set<String> activityUsers(Map<String, String> sources) {
        Set<String> files = new TreeSet<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            if (isRootOfApp(e.getKey())) continue;
            if (ACTIVITY.matcher(code(e.getValue())).find()) files.add(e.getKey());
        }
        return files;
    }

    /** Package node to the nodes it imports (known nodes only, never itself). */
    static Map<String, Set<String>> packageGraph(Map<String, String> sources) {
        Set<String> known = new HashSet<>();
        for (String path : sources.keySet()) known.add(nodeOfPath(path));
        Map<String, Set<String>> graph = new TreeMap<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String from = nodeOfPath(e.getKey());
            Matcher m = IMPORT.matcher(e.getValue());
            while (m.find()) {
                String to = nodeOfImport(m.group(1));
                if (to.equals(from) || !known.contains(to)) continue;
                graph.computeIfAbsent(from, k -> new TreeSet<>()).add(to);
            }
        }
        return graph;
    }

    /** Imports that point up the direction (window, glass, page, then elements): "path imports node". */
    static Set<String> upwardImports(Map<String, String> sources) {
        Set<String> found = new TreeSet<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String from = nodeOfPath(e.getKey());
            if (rank(from) >= 3) continue;
            Matcher m = IMPORT.matcher(e.getValue());
            while (m.find()) {
                String to = nodeOfImport(m.group(1));
                if (to.isEmpty() || to.equals(from)) continue;
                if (rank(to) > rank(from)) found.add(e.getKey() + " imports " + to);
            }
        }
        return found;
    }

    /** Mutual import pairs between element packages ("a <-> b", sorted), the root excluded. */
    static Set<String> cyclePairs(Map<String, Set<String>> graph) {
        Set<String> pairs = new TreeSet<>();
        for (Map.Entry<String, Set<String>> e : graph.entrySet()) {
            for (String to : e.getValue()) {
                if (e.getKey().equals("app") || to.equals("app")) continue;
                Set<String> back = graph.get(to);
                if (back != null && back.contains(e.getKey()) && e.getKey().compareTo(to) < 0) {
                    pairs.add(e.getKey() + " <-> " + to);
                }
            }
        }
        return pairs;
    }

    /**
     * Cycles longer than a pair: with every edge between a listed pair removed, any cycle left is
     * returned as "a -> b -> c -> a" (first one found, smallest start), else an empty list.
     */
    static List<String> residualCycles(Map<String, Set<String>> graph, Set<String> listedPairs) {
        Map<String, Set<String>> pruned = new TreeMap<>();
        for (Map.Entry<String, Set<String>> e : graph.entrySet()) {
            if (e.getKey().equals("app")) continue;
            for (String to : e.getValue()) {
                if (to.equals("app")) continue;
                String pair = e.getKey().compareTo(to) < 0 ? e.getKey() + " <-> " + to : to + " <-> " + e.getKey();
                if (listedPairs.contains(pair)) continue;
                pruned.computeIfAbsent(e.getKey(), k -> new TreeSet<>()).add(to);
            }
        }
        List<String> cycles = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (String start : pruned.keySet()) {
            List<String> stack = new ArrayList<>();
            List<String> cycle = findCycle(start, pruned, stack, done);
            if (cycle != null) {
                cycles.add(String.join(" -> ", cycle));
                break;
            }
        }
        return cycles;
    }

    private static List<String> findCycle(String node, Map<String, Set<String>> g, List<String> stack,
                                          Set<String> done) {
        int at = stack.indexOf(node);
        if (at >= 0) {
            List<String> cycle = new ArrayList<>(stack.subList(at, stack.size()));
            cycle.add(node);
            return cycle;
        }
        if (!done.add(node)) return null;
        stack.add(node);
        for (String next : g.getOrDefault(node, Collections.<String>emptySet())) {
            List<String> cycle = findCycle(next, g, stack, done);
            if (cycle != null) return cycle;
        }
        stack.remove(stack.size() - 1);
        return null;
    }

    /** Host interfaces over {@code max} methods: "path#Name" to the method count. */
    static Map<String, Integer> bigHosts(Map<String, String> sources, int max) {
        Map<String, Integer> hosts = new TreeMap<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String code = code(e.getValue());
            Matcher m = HOST.matcher(code);
            while (m.find()) {
                int open = m.end() - 1;
                int n = methodsIn(code, open);
                if (n > max) hosts.put(e.getKey() + "#" + m.group(1), n);
            }
        }
        return hosts;
    }

    /** Members with parentheses at the top level of the braces opening at {@code open}. */
    static int methodsIn(String code, int open) {
        int depth = 0, count = 0;
        StringBuilder header = new StringBuilder();
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                if (depth == 1) {
                    if (isMethod(header)) count++;
                    header.setLength(0);
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return count;
                if (depth == 1) header.setLength(0);
            } else if (depth == 1) {
                if (c == ';') {
                    if (isMethod(header)) count++;
                    header.setLength(0);
                } else {
                    header.append(c);
                }
            }
        }
        return count;
    }

    private static boolean isMethod(CharSequence rawHeader) {
        String h = ANNOTATION.matcher(rawHeader).replaceAll(" ");
        if (h.indexOf('(') < 0 || h.indexOf('=') >= 0) return false;
        return !Pattern.compile("\\b(class|interface|enum)\\b").matcher(h).find();
    }

    /**
     * Compares an actual map with allow-list entries ({@code path:N}, or {@code path} meaning one).
     * Returns the problems: a new offender, or a listed entry that is gone or changed. Empty is green.
     */
    static List<String> compare(String rule, Map<String, Integer> actual, List<String> allowed) {
        Map<String, Integer> listed = new LinkedHashMap<>();
        for (String entry : allowed) {
            int colon = entry.lastIndexOf(':');
            if (colon > 0 && entry.substring(colon + 1).matches("\\d+")) {
                listed.put(entry.substring(0, colon), Integer.parseInt(entry.substring(colon + 1)));
            } else {
                listed.put(entry, 1);
            }
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Integer> e : actual.entrySet()) {
            Integer was = listed.get(e.getKey());
            if (was == null) {
                problems.add(rule + ": new offender " + e.getKey() + ":" + e.getValue());
            } else if (e.getValue() > was) {
                problems.add(rule + ": more than listed " + e.getKey() + ":" + e.getValue()
                    + " (listed " + was + ")");
            } else if (e.getValue() < was) {
                problems.add(rule + ": fewer than listed, lower or delete the entry " + e.getKey()
                    + ":" + e.getValue() + " (listed " + was + ")");
            }
        }
        for (String path : listed.keySet()) {
            if (!actual.containsKey(path)) {
                problems.add(rule + ": listed entry no longer offends, delete it: " + path);
            }
        }
        return problems;
    }

    /** {@link #compare} for rules whose offenders are plain strings, not counts. */
    static List<String> compareSet(String rule, Set<String> actual, List<String> allowed) {
        Map<String, Integer> map = new TreeMap<>();
        for (String a : actual) map.put(a, 1);
        return compare(rule, map, allowed);
    }
}
