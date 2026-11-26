package com.termux.app.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every glass surface draws the layered Live glass through the registry (ADR 0028 to 0032): the
 * ground, the pages and the local layer, attached to the recorder as a surface by the glass service
 * (or by {@code PanelBackdrop}, which registers its panel with the service first). A panel keeping
 * "one picture" of everything beneath it was the legacy branch; it is deleted, and this keeps it
 * from coming back. It scans {@code app/src/main/java} (comments and literals blanked) for:
 *
 * <ul>
 *   <li>the deleted API: an {@code attach(surface, radius, boolean)} (the panel-radius flag),
 *   {@code recordOne} or {@code new LivePicture(} outside {@code LiveBackdrop}, a {@code panelRadius}
 *   flag in {@code LiveBackdrop} or {@code LivePicture}, and {@code PanelBackdrop.setLayered};</li>
 *   <li>a picture made without the registry: a call to the recorder's {@code attach} outside the
 *   two files that sit behind the glass service ({@code PanelBackdrop}, {@code TermuxActivity}'s
 *   live source), and a {@code new PanelBackdrop(} that is not in a panel's controller or that
 *   names no registry element (its third argument).</li>
 * </ul>
 */
public class LayeredGlassSourceRulesTest {

    static final String LIVE_BACKDROP = "app/chrome/LiveBackdrop.java";
    static final String LIVE_PICTURE = "app/chrome/LivePicture.java";

    /** The files that attach a picture to the recorder: the glass service's source and the panel. */
    static final List<String> RECORDER_ATTACHERS = Arrays.asList(
        "app/chrome/PanelBackdrop.java", "app/TermuxActivity.java");

    /** The panels that wear layered glass; a new panel is added here on purpose. */
    static final List<String> PANEL_OWNERS = Arrays.asList(
        "app/launcher/drawer/AppDrawerController.java",
        "app/terminal/TerminalCommandPaletteController.java",
        "app/terminal/TerminalSheetController.java",
        "app/launcher/popup/AnchoredMenu.java",
        "app/statusbar/MarksZoomBackdrop.java");

    private static final Pattern PANEL_FLAG = Pattern.compile("\\b(mPanelRadius|panelRadius)\\b");
    private static final Pattern RECORD_ONE = Pattern.compile("\\brecordOne\\b");
    private static final Pattern NEW_PICTURE = Pattern.compile("\\bnew\\s+LivePicture\\s*\\(");
    private static final Pattern SET_LAYERED = Pattern.compile("\\bsetLayered\\s*\\(");
    private static final Pattern RECORDER_ATTACH = Pattern.compile(
        "\\b(recorder|mRecorder|mLiveBackdrop|liveBackdrop\\(\\))\\s*\\.attach\\s*\\(");
    private static final Pattern ANY_ATTACH = Pattern.compile("\\.attach\\s*\\(");
    private static final Pattern NEW_PANEL = Pattern.compile(
        "\\bnew\\s+(?:com\\.termux\\.app\\.chrome\\.)?PanelBackdrop\\s*\\(");

    /** The arguments of the call whose opening parenthesis is at {@code open}, split at depth one. */
    static List<String> args(String code, int open) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = open + 1;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    String last = code.substring(start, i).trim();
                    if (!last.isEmpty() || !args.isEmpty()) args.add(last);
                    return args;
                }
            } else if (c == ',' && depth == 1) {
                args.add(code.substring(start, i).trim());
                start = i + 1;
            }
        }
        return args;
    }

    /** Offenders in {@code sources} (path to text), one {@code path: what} line each. */
    static List<String> offenders(Map<String, String> sources) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String path = entry.getKey();
            String code = SourceRules.code(entry.getValue());
            boolean home = path.equals(LIVE_BACKDROP) || path.equals(LIVE_PICTURE);
            if (home && PANEL_FLAG.matcher(code).find()) found.add(path + ": a panel-radius flag");
            if (!path.equals(LIVE_BACKDROP)) {
                if (RECORD_ONE.matcher(code).find()) found.add(path + ": recordOne");
                if (NEW_PICTURE.matcher(code).find()) found.add(path + ": new LivePicture(");
                Matcher any = ANY_ATTACH.matcher(code);
                while (any.find()) {
                    List<String> args = args(code, any.end() - 1);
                    if (args.size() == 3 && args.get(2).matches("true|false")) {
                        found.add(path + ": attach(surface, radius, boolean), the one-picture attach");
                    }
                }
            }
            if (SET_LAYERED.matcher(code).find()) found.add(path + ": setLayered");
            if (!RECORDER_ATTACHERS.contains(path) && RECORDER_ATTACH.matcher(code).find()) {
                found.add(path + ": a picture attached to the recorder outside the glass service");
            }
            Matcher panel = NEW_PANEL.matcher(code);
            while (panel.find()) {
                if (!PANEL_OWNERS.contains(path)) {
                    found.add(path + ": new PanelBackdrop( outside a panel's controller");
                } else if (args(code, panel.end() - 1).size() != 3) {
                    found.add(path + ": new PanelBackdrop( without a registry element");
                }
            }
        }
        return found;
    }

    @Test
    public void everyGlassPanelDrawsLayeredGlassThroughTheRegistry() throws IOException {
        List<String> found = offenders(SourceRules.load(root()));
        assertEquals("one-picture glass is back: " + found, 0, found.size());
    }

    @Test
    public void theRulesSeeTheLegacyPatterns() {
        Map<String, String> bad = new TreeMap<>();
        bad.put("app/x/A.java", "class A { void f() { recorder.attach(v, 3f, true); } }");
        bad.put("app/x/B.java", "class B { void f() { picture.recordOne(a); new LivePicture(v, 1f); } }");
        bad.put("app/chrome/LivePicture.java", "class P { boolean mPanelRadius; }");
        bad.put("app/terminal/TerminalSheetController.java",
            "class S { void f() { new PanelBackdrop(a, b); backdrop.setLayered(1f); } }");
        bad.put("app/y/C.java", "class C { void f() { new com.termux.app.chrome.PanelBackdrop(a, b, \"c\"); } }");
        List<String> found = offenders(bad);
        assertTrue(found.toString(), found.size() >= 8);
        // Comments and literals are not code, and a call through the glass service is fine.
        Map<String, String> good = new TreeMap<>();
        good.put("app/x/A.java", "class A { // recorder.attach(v, 3f, true)\n String s = \"recordOne\"; }");
        good.put("app/chrome/PanelBackdrop.java", "class P { void f() { recorder.attach(v, r, RANK_CHROME); } }");
        good.put("app/terminal/TerminalSheetController.java",
            "class S { void f() { new PanelBackdrop(a(b, c), b, \"sheet\"); } }");
        assertEquals(new ArrayList<String>(), offenders(good));
    }

    private static Path root() {
        for (String candidate : new String[]{"src/main/java/com/termux", "app/src/main/java/com/termux"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) return path;
        }
        throw new AssertionError("app sources not found from " + Paths.get("").toAbsolutePath());
    }
}
