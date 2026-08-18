package com.termux.app.terminal.rename;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class InlineRenameControllerTest {

    @Test
    public void hardwareTypingBuildsTheDraftAndEnterCommitsIt() {
        Host host = new Host();
        InlineRenameController controller = new InlineRenameController();
        controller.begin("ab", 8, host);
        assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_C,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C)));
        assertEquals("abc", controller.model().text());
        assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_ENTER,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
        assertEquals(1, host.endings);
        assertTrue(host.committed);
        assertEquals("abc", host.name);
        assertFalse(controller.isActive());
    }

    @Test
    public void escapeEndsWithoutCommitting() {
        Host host = new Host();
        InlineRenameController controller = new InlineRenameController();
        controller.begin("ab", 8, host);
        assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_ESCAPE,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)));
        assertEquals(1, host.endings);
        assertFalse(host.committed);
        assertNull(host.name);
    }

    @Test
    public void everyExitPathEndsExactlyOnce() {
        for (Exit exit : Exit.values()) {
            Host host = new Host();
            InlineRenameController controller = new InlineRenameController();
            controller.begin("x", 8, host);
            switch (exit) {
                case COMMIT: controller.commit(); break;
                case CANCEL: controller.cancel(); break;
                case RESTART: controller.begin("y", 8, new Host()); break;
            }
            assertEquals(exit.name(), 1, host.endings);
            // A second attempt at the same exit cannot end it again.
            controller.cancel();
            assertEquals(exit.name(), 1, host.endings);
        }
    }

    @Test
    public void clearedDraftCommitsAsNullSoTheNameGoesBackToItsDefault() {
        Host host = new Host();
        InlineRenameController controller = new InlineRenameController();
        controller.begin("work", 8, host);
        for (int i = 0; i < 4; i++) {
            controller.handleKeyDown(KeyEvent.KEYCODE_DEL,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        }
        controller.commit();
        assertTrue(host.committed);
        assertNull(host.name);
    }

    @Test
    public void systemImeChannelClaimsTypingWhileActiveAndNothingWhenNot() {
        Host host = new Host();
        InlineRenameController controller = new InlineRenameController();
        assertFalse(controller.handleCodePoint('a', false));
        controller.begin(null, 8, host);
        assertTrue(controller.handleCodePoint('a', false));
        assertEquals("a", controller.model().text());
        // Enter arrives as text from AOSP-derived keyboards, and has to commit from this channel too.
        assertTrue(controller.handleCodePoint('\n', false));
        assertTrue(host.committed);
        assertFalse(controller.handleCodePoint('b', false));
    }

    @Test
    public void suggestedDraftReplacesWhatWasTyped() {
        Host host = new Host();
        InlineRenameController controller = new InlineRenameController();
        controller.begin("ab", 8, host);
        controller.setDraft("gradle");
        assertEquals("gradle", controller.model().text());
        controller.commit();
        assertEquals("gradle", host.name);
    }

    private enum Exit { COMMIT, CANCEL, RESTART }

    private static final class Host implements InlineRenameController.Host {
        int drafts;
        int endings;
        boolean committed;
        @Nullable String name;

        @Override public void onDraftChanged(@NonNull InlineRenameModel model) { drafts++; }

        @Override public void onRenameEnded(boolean committed, @Nullable String committedName) {
            endings++;
            this.committed = committed;
            this.name = committedName;
        }
    }
}
