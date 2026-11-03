package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Seam 4 of spec #101, for Tasks (#119): at every span and font scale, with no tasks, a few and
 * many: nothing overlaps, every part sits inside the inset, no text is under 11 sp, a checkbox row
 * is a 48 dp target. The header counts what is open, a due tag takes the caution ink, and an
 * empty list invites the first task and names its file.
 */
public class TasksLayoutPlanTest {
    private static final float[] FONT_SCALES = { 1.0f, 1.3f, 2.0f };
    private static final WidgetLayoutPlan.Measure MEASURE = (text, role, numeric, size) -> {
        float em = 0f;
        for (int i = 0; i < text.length(); i++) em += Character.isDigit(text.charAt(i)) ? 0.57f : 0.6f;
        return em * size;
    };
    private static final TasksLayoutPlanner.Words WORDS = new TasksLayoutPlanner.Words() {
        @NonNull @Override public String header(int open) { return "Today · " + open + " open"; }
        @NonNull @Override public String headerShort(int open) { return open + " open"; }
        @NonNull @Override public String openLabel(int open) { return "open"; }
        @NonNull @Override public String addTask() { return "Add a task"; }
        @NonNull @Override public String writesTo(@NonNull String path) { return "Writes to " + path; }
    };

    private static List<TasksLayoutPlanner.Row> rows(int count) {
        List<TasksLayoutPlanner.Row> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            boolean done = i == count - 1 && count > 2;
            rows.add(new TasksLayoutPlanner.Row("Task number " + i + " with a long name",
                i == 1 ? "Tomorrow" : null, done));
        }
        return rows;
    }

    @NonNull private static WidgetLayoutPlan plan(BuiltinWidgetSpan span, float fs, boolean loaded,
                                                  List<TasksLayoutPlanner.Row> rows, int open) {
        return TasksLayoutPlanner.plan(span, span.widthDp, span.heightDp, fs, loaded, rows, open,
            "~/tasks.md", WORDS, MEASURE);
    }

    private static void check(@NonNull WidgetLayoutPlan plan, @NonNull String what) {
        List<Part> parts = plan.parts;
        for (Part part : parts) {
            // A target may run up into the card's inset; the rest stays inside it.
            boolean target = part.id.endsWith(".touch");
            assertTrue(what + ": " + part + " inside " + plan.inset,
                plan.inset.contains(part.box) || target && plan.card.contains(part.box));
            if (part.role != null && part.role != WidgetTokens.Role.HERO) {
                assertTrue(what + ": " + part + " text floor",
                    part.sizeDp >= 11f * plan.fontScale - 0.01f);
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            for (int j = i + 1; j < parts.size(); j++) {
                Part a = parts.get(i), b = parts.get(j);
                // A strike is drawn through its label. A target lies over what it acts on (the
                // empty state's is the whole card; the first row's reaches under the header);
                // only the rows' targets must not share any area.
                if (a.id.endsWith(".strike") || b.id.endsWith(".strike")) continue;
                boolean rowTargets = a.id.matches("row\\.\\d+\\.touch")
                    && b.id.matches("row\\.\\d+\\.touch");
                if ((a.id.endsWith(".touch") || b.id.endsWith(".touch")) && !rowTargets) continue;
                assertFalse(what + ": " + a + " overlaps " + b, a.box.overlaps(b.box));
            }
        }
    }

    @Test public void everySpanFontScaleAndContentFitsItsInset() {
        int[] counts = { 0, 1, 3, 9 };
        for (BuiltinWidgetSpan span : BuiltinWidgetSpan.values()) {
            for (float fs : FONT_SCALES) {
                for (int count : counts) {
                    check(plan(span, fs, true, rows(count), count), span + " " + fs + " x" + count);
                }
            }
        }
    }

    @Test public void headerCountsWhatIsOpen() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 1f, true, rows(4), 3);
        assertEquals("Today · 3 open", plan.part("header").text);
    }

    @Test public void narrowHeaderFallsBackToTheShortCount() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.TWO_BY_TWO, 1.3f, true, rows(4), 3);
        assertEquals("3 open", plan.part("header").text);
    }

    @Test public void rowsAreFortyEightDpTargets() {
        for (float fs : FONT_SCALES) {
            WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.FOUR_BY_TWO, fs, true, rows(9), 8);
            int touches = 0;
            for (Part part : plan.partsStartingWith("row.")) {
                if (!part.id.endsWith(".touch")) continue;
                touches++;
                assertTrue(part.toString(), part.box.height >= 48f - 0.01f);
            }
            assertTrue("rows shown at " + fs, touches >= 2);
        }
    }

    @Test public void dueTagIsCautionAndDoneRowIsStruck() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.FOUR_BY_TWO, 1f, true, rows(3), 2);
        Part due = plan.part("row.1.due");
        assertNotNull(due);
        assertEquals(WidgetLayoutPlan.Ink.CAUTION, due.ink);
        assertEquals("Tomorrow", due.text);
        assertTrue(plan.has("row.2.strike"));
        assertFalse(plan.has("row.0.strike"));
        assertEquals(TasksLayoutPlanner.GLYPH_DONE, plan.part("row.2.box").text);
        assertEquals(TasksLayoutPlanner.GLYPH_OPEN, plan.part("row.0.box").text);
    }

    @Test public void emptyListInvitesTheFirstTaskAndNamesItsFile() {
        for (BuiltinWidgetSpan span : new BuiltinWidgetSpan[] { BuiltinWidgetSpan.TWO_BY_TWO,
            BuiltinWidgetSpan.FOUR_BY_TWO, BuiltinWidgetSpan.TWO_BY_ONE }) {
            WidgetLayoutPlan plan = plan(span, 1f, true, Collections.emptyList(), 0);
            assertEquals("Add a task", plan.part("empty.title").text);
            assertEquals("Writes to ~/tasks.md", plan.part("empty.path").text);
            assertTrue(plan.has("add.touch"));
        }
        WidgetLayoutPlan one = plan(BuiltinWidgetSpan.ONE_BY_ONE, 1f, true, Collections.emptyList(), 0);
        assertEquals("Add a task", one.part("empty.title").text);
        assertTrue(one.has("add.touch"));
    }

    @Test public void notYetLoadedDrawsNothing() {
        assertTrue(plan(BuiltinWidgetSpan.TWO_BY_TWO, 1f, false, Collections.emptyList(), 0)
            .parts.isEmpty());
    }

    @Test public void oneByOneShowsTheOpenCount() {
        WidgetLayoutPlan plan = plan(BuiltinWidgetSpan.ONE_BY_ONE, 1f, true, rows(3), 3);
        assertEquals("3", plan.part("count").text);
    }
}
