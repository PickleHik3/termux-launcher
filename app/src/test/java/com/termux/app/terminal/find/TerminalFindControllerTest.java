package com.termux.app.terminal.find;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalFindControllerTest {

    @Test public void nothingIsClaimedBeforeASessionStarts() {
        TerminalFindController controller = new TerminalFindController();
        assertFalse(controller.isActive());
        assertFalse(controller.handleCodePoint('a', false));
        assertFalse(controller.handleKeyDown(KeyEvent.KEYCODE_A, down(KeyEvent.KEYCODE_A)));
    }

    @Test public void everyStrokeIsSwallowedWhileTheStripIsUp() {
        Recorder host = new Recorder();
        TerminalFindController controller = begin(host);

        // Even a key this session does nothing with never reaches the shell behind the strip.
        assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_F5, down(KeyEvent.KEYCODE_F5)));
        assertTrue(controller.handleCodePoint('q', false));
    }

    @Test public void imeTextThenEnterThenNWalksMatches() {
        Recorder host = new Recorder();
        TerminalFindController controller = begin(host);

        controller.handleCodePoint('h', false);
        controller.handleCodePoint('i', false);
        TerminalFindModel model = controller.model();
        assertNotNull(model);
        assertEquals("hi", model.query());
        assertEquals(2, model.matches().size());

        controller.handleCodePoint('\n', false);
        assertEquals(TerminalFindModel.Mode.NAVIGATE, model.mode());
        int before = model.currentIndex();
        controller.handleCodePoint('n', false);
        assertTrue(model.currentIndex() != before);
    }

    @Test public void arrowsStepMatchesWhileTypingAndMoveTheCursorOnceCommitted() {
        Recorder host = new Recorder();
        TerminalFindController controller = begin(host);
        controller.handleCodePoint('h', false);
        TerminalFindModel model = controller.model();
        assertNotNull(model);

        controller.handleKeyDown(KeyEvent.KEYCODE_DPAD_UP, down(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals(0, model.currentIndex());

        controller.handleKeyDown(KeyEvent.KEYCODE_ENTER, down(KeyEvent.KEYCODE_ENTER));
        int row = model.cursorRow();
        controller.handleKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, down(KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals(row + 1, model.cursorRow());
    }

    @Test public void ctrlVWhileTypingCommitsTheQueryAndOpensABlockSelection() {
        Recorder host = new Recorder();
        TerminalFindController controller = begin(host);
        controller.handleCodePoint('h', false);
        controller.handleCodePoint('i', false);

        controller.handleCodePoint('v', true);
        TerminalFindModel model = controller.model();
        assertNotNull(model);
        assertEquals(TerminalFindModel.Mode.SELECT, model.mode());
        assertEquals(TerminalFindModel.Selection.BLOCK, model.selection());
    }

    @Test public void escapeUnwindsToTheSessionEndingExactlyOnce() {
        Recorder host = new Recorder();
        TerminalFindController controller = begin(host);
        controller.handleCodePoint('h', false);
        controller.handleCodePoint('\n', false);
        controller.handleCodePoint('v', false);

        controller.handleKeyDown(KeyEvent.KEYCODE_ESCAPE, down(KeyEvent.KEYCODE_ESCAPE));
        assertTrue(controller.isActive());
        controller.handleKeyDown(KeyEvent.KEYCODE_ESCAPE, down(KeyEvent.KEYCODE_ESCAPE));
        assertFalse(controller.isActive());
        assertEquals(1, host.ended);
        assertNull(host.yanked);
        // A stroke after the end belongs to the shell again.
        assertFalse(controller.handleCodePoint('x', false));
    }

    @Test public void yankEndsTheSessionAndHandsTheTextOver() {
        Recorder host = new Recorder();
        TerminalFindController controller = begin(host);
        controller.handleCodePoint('h', false);
        controller.handleCodePoint('\n', false);
        controller.handleCodePoint('V', false);
        controller.handleCodePoint('y', false);

        assertFalse(controller.isActive());
        assertEquals(1, host.ended);
        // The session opened on the newest hit, so that is the row the linewise yank took.
        assertEquals("hit two", host.yanked);
    }

    private static TerminalFindController begin(@NonNull Recorder host) {
        List<TerminalFindModel.Line> lines = new ArrayList<>();
        lines.add(new TerminalFindModel.Line(0, "hit one"));
        lines.add(new TerminalFindModel.Line(1, "hit two"));
        TerminalFindController controller = new TerminalFindController();
        controller.begin(lines, host);
        return controller;
    }

    private static KeyEvent down(int keyCode) {
        return new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
    }

    private static final class Recorder implements TerminalFindController.Host {
        int changed;
        int ended;
        @Nullable String yanked;

        @Override public void onFindChanged(@NonNull TerminalFindModel model) { changed++; }

        @Override public void onFindEnded(@Nullable String yankedText) {
            ended++;
            yanked = yankedText;
        }
    }
}
