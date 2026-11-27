package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.termux.app.leader.Leader;
import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A moved action keeps only its new key, built-in defaults included. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class TerminalBindingMoveTest {

    @Before @After
    public void reset() {
        TerminalKeyBindingResolver.resetForTesting();
    }

    private static TerminalKeyBindingResolver load(List<String> lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) text.append(line).append('\n');
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(text.toString(),
            LauncherToolRegistry.getInstance(), true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        TerminalKeyBindingResolver.installConfigForTesting(config);
        return TerminalKeyBindingResolver.getInstance();
    }

    @Test
    public void aDefaultActionMovesToTheNewKey() {
        TerminalKeyBindingResolver before = load(Collections.<String>emptyList());
        assertEquals("pane.split_vertical",
            before.holdersByStoredStroke().get("ctrl+alt+v").toolName);

        TerminalBindingConfigWriter.Edit edit = TerminalBindingConfigWriter.move(
            Collections.<String>emptyList(), "ctrl+alt+q", "pane.split_vertical",
            Collections.singletonList("ctrl+alt+v"));
        assertTrue(edit.error, edit.ok());
        TerminalKeyBindingResolver after = load(edit.lines);
        assertEquals("pane.split_vertical",
            after.holdersByStoredStroke().get("ctrl+alt+q").toolName);
        assertNull(after.holdersByStoredStroke().get("ctrl+alt+v"));
    }

    @Test
    public void aTypedClashIsTakenOffSoTheNewKeyFires() {
        // A plain binding of ctrl+shift+x, which a Ctrl+Shift leader types for leader+X.
        List<String> file = Arrays.asList("map ctrl+shift+x pane.split_vertical");
        TerminalBindingConfigWriter.Edit edit = TerminalBindingConfigWriter.move(file,
            "ctrl+alt+x", "pane.split_horizontal", Collections.singletonList("ctrl+shift+x"));
        assertTrue(edit.error, edit.ok());
        assertFalse(edit.lines.contains("map ctrl+shift+x pane.split_vertical"));
        load(edit.lines);
        TerminalKeyBindingResolver.setLeader(
            Leader.of("custom", "ctrl+shift"), true);
        TerminalKeyBindingResolver resolver = TerminalKeyBindingResolver.getInstance();
        assertEquals("pane.split_horizontal", resolver.holdersByTypedStroke().get("ctrl+shift+x").toolName);
    }

    @Test
    public void unbindingADefaultWritesOneUnmapAndNoDuplicate() {
        TerminalBindingConfigWriter.Edit first = TerminalBindingConfigWriter.unbind(
            Collections.<String>emptyList(), "ctrl+alt+v");
        assertTrue(first.lines.contains("unmap ctrl+alt+v"));
        TerminalBindingConfigWriter.Edit again = TerminalBindingConfigWriter.unbind(first.lines, "ctrl+alt+v");
        assertEquals(first.lines, again.lines);
    }
}
