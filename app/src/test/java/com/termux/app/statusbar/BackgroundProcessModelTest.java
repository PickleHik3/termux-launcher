package com.termux.app.statusbar;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BackgroundProcessModelTest {

    @Test
    public void retainsStartOrderUpdatesNativeTitleAndFiltersFocusedPane() {
        BackgroundProcessModel model = new BackgroundProcessModel();
        model.update(Collections.singletonList(snapshot(10, 101, "codex", "Thinking")), 1000);
        model.update(Arrays.asList(snapshot(10, 101, "codex", "requires attention"),
            snapshot(20, 202, "gradle", "Build")), 2000);

        List<BackgroundProcessModel.Entry> all = model.visibleEntries(-1);
        assertEquals(Arrays.asList(10, 20), Arrays.asList(all.get(0).shellPid, all.get(1).shellPid));
        assertEquals(1000, all.get(0).startedAtMs);
        assertEquals("requires attention", all.get(0).displayText());
        assertEquals(Collections.singletonList(20),
            Collections.singletonList(model.visibleEntries(10).get(0).shellPid));
    }

    @Test
    public void foregroundReturnSessionExitAndPidReplacementRemoveOldRows() {
        BackgroundProcessModel model = new BackgroundProcessModel();
        model.update(Collections.singletonList(snapshot(10, 101, "sleep", "sleep")), 1000);
        model.update(Collections.singletonList(snapshot(10, 102, "make", "make")), 2000);
        assertEquals(1, model.visibleEntries(-1).size());
        assertEquals(102, model.visibleEntries(-1).get(0).foregroundPid);
        assertEquals(2000, model.visibleEntries(-1).get(0).startedAtMs);

        model.update(Collections.singletonList(
            new BackgroundProcessModel.Snapshot(10, -1, null, "shell", false)), 3000);
        assertEquals(0, model.visibleEntries(-1).size());

        model.update(Collections.singletonList(snapshot(20, 201, "nvim", "nvim")), 4000);
        model.update(Collections.emptyList(), 5000);
        assertEquals(0, model.visibleEntries(-1).size());
    }

    private static BackgroundProcessModel.Snapshot snapshot(int shell, int foreground,
                                                            String process, String title) {
        return new BackgroundProcessModel.Snapshot(shell, foreground, process, title, true);
    }
}
