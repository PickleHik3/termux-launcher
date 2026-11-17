package com.termux.app.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Each source-scan rule on synthetic sources: it flags what it must and skips what it must not. */
public class SourceRulesTest {

    private static Map<String, String> sources(String... pathAndText) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pathAndText.length; i += 2) map.put(pathAndText[i], pathAndText[i + 1]);
        return map;
    }

    @Test
    public void commentsAndStringsDoNotCount() {
        Map<String, String> src = sources("app/a/A.java",
            "// v.bringToFront();\n/* v.bringToFront(); */\nString s = \"v.bringToFront()\";\n");
        assertTrue(SourceRules.bringToFront(src).isEmpty());
    }

    @Test
    public void frameHooksAreCountedPerFileAndTheWindowPackageIsExempt() {
        Map<String, String> src = sources(
            "app/a/A.java", "o.addOnPreDrawListener(l); c.postFrameCallback(f); v.postOnAnimation(r);",
            "app/window/Pipeline.java", "o.addOnPreDrawListener(l);",
            "app/a/B.java", "o.addOnGlobalLayoutListener(l); v.post(r);");
        Map<String, Integer> hooks = SourceRules.frameHooks(src);
        assertEquals(Collections.singletonMap("app/a/A.java", 3), hooks);
    }

    @Test
    public void bringToFrontNeedsNoArguments() {
        Map<String, String> src = sources("app/a/A.java", "v.bringToFront(); n.bringToFront(session);");
        assertEquals(Collections.singletonMap("app/a/A.java", 1), SourceRules.bringToFront(src));
    }

    @Test
    public void zWritesCoverElevationAndTranslationZ() {
        Map<String, String> src = sources("app/a/A.java",
            "v.setElevation(1); v.setTranslationZ(2); v.animate().translationZ(0f); float e = getElevation();"
                + " int p = elevationPx(c, 3);");
        assertEquals(Collections.singletonMap("app/a/A.java", 3), SourceRules.zWrites(src));
    }

    @Test
    public void decorAndContentSitesCountOnlyWhereViewsAreAdded() {
        Map<String, String> src = sources(
            "app/a/Adds.java", "ViewGroup c = a.findViewById(android.R.id.content); c.addView(v); w.getDecorView();",
            "app/a/Reads.java", "View c = a.findViewById(android.R.id.content);",
            "app/a/Adds2.java", "a.addContentView(v, lp);");
        Map<String, Integer> found = SourceRules.decorOrContentAdds(src);
        assertEquals(2, found.size());
        assertEquals(Integer.valueOf(2), found.get("app/a/Adds.java"));
        assertEquals(Integer.valueOf(1), found.get("app/a/Adds2.java"));
    }

    @Test
    public void activityUsersSkipTheAppRootPackage() {
        Map<String, String> src = sources(
            "app/TermuxActivity.java", "class TermuxActivity {}",
            "app/DockShelfView.java", "import com.termux.app.TermuxActivity;",
            "app/dock/Plank.java", "import com.termux.app.TermuxActivity;",
            "launcherctl/Route.java", "x = com.termux.app.TermuxActivity.get();",
            "app/dock/Other.java", "import com.termux.app.TermuxActivityHelper;");
        Set<String> users = SourceRules.activityUsers(src);
        assertEquals(Arrays.asList("app/dock/Plank.java", "launcherctl/Route.java"),
            Arrays.asList(users.toArray()));
    }

    @Test
    public void nodesResolveFromPathsAndImports() {
        assertEquals("chrome", SourceRules.nodeOfPath("app/chrome/scene/X.java"));
        assertEquals("app", SourceRules.nodeOfPath("app/X.java"));
        assertEquals("ai", SourceRules.nodeOfPath("ai/X.java"));
        assertEquals("chrome", SourceRules.nodeOfImport("com.termux.app.chrome.scene.Scene.Inner"));
        assertEquals("app", SourceRules.nodeOfImport("com.termux.app.TermuxActivity"));
        assertEquals("terminal", SourceRules.nodeOfImport("com.termux.app.terminal.Host"));
    }

    @Test
    public void importsPointingUpTheDirectionAreFound() {
        Map<String, String> src = sources(
            "app/window/W.java", "import com.termux.app.glass.G;\nimport com.termux.app.window.Other;\n",
            "app/glass/G.java", "import com.termux.app.window.W;\nimport com.termux.app.chrome.C;\n",
            "app/page/P.java", "import com.termux.app.glass.G;\nimport com.termux.app.TermuxActivity;\n",
            "app/chrome/C.java", "import com.termux.app.window.W;\n");
        assertEquals(Arrays.asList(
                "app/glass/G.java imports chrome",
                "app/page/P.java imports app",
                "app/window/W.java imports glass"),
            Arrays.asList(SourceRules.upwardImports(src).toArray()));
    }

    @Test
    public void mutualImportsAreCyclePairsAndLongerCyclesAreResidual() {
        Map<String, String> src = sources(
            "app/a/A.java", "import com.termux.app.b.B;", "app/b/B.java", "import com.termux.app.a.A;",
            "app/c/C.java", "import com.termux.app.d.D;", "app/d/D.java", "import com.termux.app.e.E;",
            "app/e/E.java", "import com.termux.app.c.C;");
        Map<String, Set<String>> graph = SourceRules.packageGraph(src);
        Set<String> pairs = SourceRules.cyclePairs(graph);
        assertEquals(Collections.singletonList("a <-> b"), Arrays.asList(pairs.toArray()));
        assertEquals(Collections.singletonList("c -> d -> e -> c"),
            SourceRules.residualCycles(graph, pairs));
        Map<String, Set<String>> acyclic = SourceRules.packageGraph(sources(
            "app/a/A.java", "import com.termux.app.b.B;", "app/b/B.java", ""));
        assertTrue(SourceRules.residualCycles(acyclic, Collections.<String>emptySet()).isEmpty());
    }

    @Test
    public void hostsOverTheCapAreFoundByMethodCount() {
        StringBuilder big = new StringBuilder("interface Host {\n  int LIMIT = compute(1);\n");
        for (int i = 0; i < 4; i++) big.append("  @Nullable View v").append(i).append("(int a, @IdRes int b);\n");
        big.append("  default void d() { x(); }\n  interface Nested { void n(); }\n}\n");
        Map<String, String> src = sources(
            "app/a/Big.java", "class Big {\n" + big + "}\n",
            "app/a/Small.java", "interface SmallHost { void one(); }\n",
            "app/a/NotAHost.java", "interface Callbacks { void one(); void two(); void three(); }\n");
        assertEquals(Collections.singletonMap("app/a/Big.java#Host", 5), SourceRules.bigHosts(src, 4));
        assertTrue(SourceRules.bigHosts(src, 5).isEmpty());
    }

    @Test
    public void compareNamesNewMoreFewerAndStaleEntries() {
        Map<String, Integer> actual = new LinkedHashMap<>();
        actual.put("new.java", 1);
        actual.put("grew.java", 3);
        actual.put("shrank.java", 1);
        actual.put("same.java", 2);
        List<String> problems = SourceRules.compare("rule", actual,
            Arrays.asList("grew.java:2", "shrank.java:2", "same.java:2", "gone.java:1"));
        assertEquals(4, problems.size());
        assertTrue(problems.get(0).contains("new offender new.java"));
        assertTrue(problems.get(1).contains("more than listed grew.java"));
        assertTrue(problems.get(2).contains("fewer than listed"));
        assertTrue(problems.get(3).contains("no longer offends, delete it: gone.java"));
    }
}
