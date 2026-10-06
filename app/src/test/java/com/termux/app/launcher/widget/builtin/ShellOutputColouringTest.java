package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.widget.builtin.ShellOutputColouring.Range;
import com.termux.app.launcher.widget.builtin.ShellOutputColouring.Role;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ShellOutputColouringTest {
    @Test public void branchLineIsDoneWithTheAheadCountWarm() {
        String line = "## dev...origin/dev [ahead 2]";
        List<Range> ranges = ShellOutputColouring.line(line);
        int bracket = line.indexOf('[');
        assertEquals(Arrays.asList(new Range(0, bracket - 1, Role.DONE),
            new Range(bracket, line.length(), Role.WARM)), ranges);
    }

    @Test public void branchLineWithoutCountIsAllDone() {
        assertEquals(Arrays.asList(new Range(0, 6, Role.DONE)), ShellOutputColouring.line("## dev"));
    }

    @Test public void modifiedFilesColourTheirStatusCode() {
        assertEquals(Arrays.asList(new Range(0, 2, Role.ERROR)), ShellOutputColouring.line(" M app/Foo.java"));
        assertEquals(Arrays.asList(new Range(0, 2, Role.ERROR)), ShellOutputColouring.line("M  app/Foo.java"));
        assertEquals(Arrays.asList(new Range(0, 2, Role.ERROR)), ShellOutputColouring.line("MM app/Foo.java"));
    }

    @Test public void untrackedIsPrimary() {
        assertEquals(Arrays.asList(new Range(0, 2, Role.PRIMARY)), ShellOutputColouring.line("?? docs/widgets.md"));
    }

    @Test public void ordinaryOutputStaysPlain() {
        assertTrue(ShellOutputColouring.line("/data   156G  100G").isEmpty());
        assertTrue(ShellOutputColouring.line("up 3 days, 4:12").isEmpty());
        assertTrue(ShellOutputColouring.line("   indented").isEmpty());
        assertTrue(ShellOutputColouring.line("").isEmpty());
        assertTrue(ShellOutputColouring.line("Me and you").isEmpty());
    }
}
