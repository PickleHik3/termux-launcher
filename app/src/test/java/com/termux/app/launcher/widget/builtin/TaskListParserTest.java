package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class TaskListParserTest {
    private static final String FILE = "# Today\n"
        + "- [ ] Review dock PR @today\n"
        + "some note the widget ignores\n"
        + "- [x] Update tlstore\n"
        + "  * [X] Indented and starred @Wed\n"
        + "- [ ] Mail bob@example.com\n";

    @Test public void parsesOpenAndDoneTasksAndSkipsOtherLines() {
        List<TaskListParser.Task> tasks = TaskListParser.parse(FILE);
        assertEquals(4, tasks.size());
        assertEquals(1, tasks.get(0).line);
        assertEquals("Review dock PR", tasks.get(0).text);
        assertEquals("today", tasks.get(0).due);
        assertFalse(tasks.get(0).done);
        assertEquals("Update tlstore", tasks.get(1).text);
        assertNull(tasks.get(1).due);
        assertTrue(tasks.get(1).done);
        assertEquals(4, tasks.get(2).line);
        assertTrue(tasks.get(2).done);
        assertEquals("Wed", tasks.get(2).due);
    }

    @Test public void anAtInsideAWordIsNotADueLabel() {
        TaskListParser.Task task = TaskListParser.parse(FILE).get(3);
        assertEquals("Mail bob@example.com", task.text);
        assertNull(task.due);
    }

    @Test public void aTaskThatIsOnlyADueTokenKeepsItAsText() {
        TaskListParser.Task task = TaskListParser.parse("- [ ] @16:00").get(0);
        assertEquals("@16:00", task.text);
        assertNull(task.due);
    }

    @Test public void emptyCheckboxesAndPlainBulletsAreNotTasks() {
        assertTrue(TaskListParser.parse("- [ ] \n- plain\n[ ] no bullet\n-[ ] tight").isEmpty());
        assertTrue(TaskListParser.parse("").isEmpty());
        assertTrue(TaskListParser.parse(null).isEmpty());
    }

    @Test public void toggleFlipsOnlyThatCheckbox() {
        String toggled = TaskListParser.toggle(FILE, 1);
        assertEquals(FILE.replace("- [ ] Review dock PR", "- [x] Review dock PR"), toggled);
        assertEquals(FILE.replace("- [x] Update tlstore", "- [ ] Update tlstore"),
            TaskListParser.toggle(FILE, 3));
        assertEquals(FILE.replace("* [X] Indented", "* [ ] Indented"), TaskListParser.toggle(FILE, 4));
    }

    @Test public void toggleOfANonTaskLineOrOutOfRangeChangesNothing() {
        assertNull(TaskListParser.toggle(FILE, 0));
        assertNull(TaskListParser.toggle(FILE, 2));
        assertNull(TaskListParser.toggle(FILE, 99));
    }

    @Test public void toggleKeepsCarriageReturnsAndTheMissingFinalNewline() {
        String crlf = "- [ ] a\r\n- [ ] b";
        assertEquals("- [ ] a\r\n- [x] b", TaskListParser.toggle(crlf, 1));
        assertEquals("- [x] a\r\n- [ ] b", TaskListParser.toggle(crlf, 0));
    }

    @Test public void toggleFollowsALineThatMovedAndRefusesOneThatIsGone() {
        String moved = "new first line\n" + FILE;
        String raw = "- [ ] Review dock PR @today";
        String toggled = TaskListParser.toggle(moved, 1, raw);
        assertEquals(moved.replace("- [ ] Review dock PR", "- [x] Review dock PR"), toggled);
        assertNull(TaskListParser.toggle("- [ ] something else\n", 1, raw));
    }

    @Test public void appendAddsAnOpenTaskOnItsOwnLine() {
        assertEquals("- [ ] Buy milk\n", TaskListParser.append("", "Buy milk"));
        assertEquals("- [ ] Buy milk\n", TaskListParser.append(null, "  Buy milk  "));
        assertEquals("# t\n- [ ] Buy milk\n", TaskListParser.append("# t", "Buy milk"));
        assertEquals("# t\n- [ ] Buy milk\n", TaskListParser.append("# t\n", "Buy milk"));
        assertEquals("a\r\n- [ ] b c\r\n", TaskListParser.append("a\r\n", "b\nc"));
        assertNull(TaskListParser.append("x", "   "));
        assertNull(TaskListParser.append("x", null));
    }

    @Test public void orderedPutsOpenTasksFirst() {
        List<TaskListParser.Task> ordered = TaskListParser.ordered(TaskListParser.parse(FILE));
        assertFalse(ordered.get(0).done);
        assertFalse(ordered.get(1).done);
        assertEquals("Mail bob@example.com", ordered.get(1).text);
        assertTrue(ordered.get(2).done);
        assertEquals(2, TaskListParser.openCount(ordered));
    }
}
