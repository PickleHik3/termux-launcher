package com.termux.app.terminal.rename;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Which rename surface opens for the keyboard the host reports, and that a hardware keyboard's
 * strokes type into and commit the chip when the in-app keyboard stays down for it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalRenameCoordinatorTest {

    @Test
    public void aHardwareKeyboardTypesIntoTheChipWhileTheInAppKeyboardStaysDown() {
        // The host withheld the in-app keyboard for the hardware one, and still reports typing.
        FakeHost host = new FakeHost(true);
        TerminalRenameCoordinator coordinator = new TerminalRenameCoordinator(host, () -> null);

        assertTrue(coordinator.begin(TerminalRenameTarget.WINDOW));
        assertTrue(coordinator.isActive());
        assertEquals(0, host.dialogs);

        coordinator.handleKeyDown(KeyEvent.KEYCODE_MOVE_END, down(KeyEvent.KEYCODE_MOVE_END));
        for (int key : new int[] {KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_V}) {
            assertTrue(coordinator.handleKeyDown(key, down(key)));
        }
        assertTrue(coordinator.handleKeyDown(KeyEvent.KEYCODE_ENTER, down(KeyEvent.KEYCODE_ENTER)));

        assertFalse(coordinator.isActive());
        assertEquals(1, host.applied.size());
        assertEquals("olddev", host.applied.get(0));
        assertEquals(1, host.endedCommitted);
    }

    @Test
    public void noTypingKeyboardFallsBackToTheDialog() {
        FakeHost host = new FakeHost(false);
        TerminalRenameCoordinator coordinator = new TerminalRenameCoordinator(host, () -> null);

        assertTrue(coordinator.begin(TerminalRenameTarget.WINDOW));
        assertFalse(coordinator.isActive());
        assertEquals(1, host.dialogs);
    }

    @NonNull
    private static KeyEvent down(int keyCode) {
        return new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
    }

    private static final class FakeHost implements TerminalRenameCoordinator.Host {
        private final boolean typing;
        private final FrameLayout container =
            new FrameLayout(ApplicationProvider.getApplicationContext());
        final List<String> applied = new ArrayList<>();
        int dialogs;
        int endedCommitted;

        FakeHost(boolean typing) {
            this.typing = typing;
        }

        @Nullable @Override public ViewGroup chipHost() { return container; }
        @Nullable @Override public View anchorFor(@NonNull TerminalRenameTarget target) { return null; }
        @Nullable @Override public String currentName(@NonNull TerminalRenameTarget target) { return "old"; }

        @Override
        public boolean applyName(@NonNull TerminalRenameTarget target, @Nullable String name) {
            applied.add(name);
            return true;
        }

        @NonNull @Override public Drawable chipBackground() { return new ColorDrawable(0); }
        @NonNull @Override public int[] chipColors() { return new int[] {0, 0, 0}; }
        @Override public boolean isReducedMotionEnabled() { return true; }
        @Override public boolean ensureTypingKeyboard() { return typing; }
        @Override public void promptRenameWithDialog(@NonNull TerminalRenameTarget target) { dialogs++; }
        @Override public boolean isPointOnTypingKeyboard(float rawX, float rawY) { return false; }

        @Override
        public void installRenameInterceptor(
                @Nullable TerminalKeyEventHandler.KeyValueInterceptor interceptor) {
        }

        @Override
        public void onRenameEnded(@NonNull TerminalRenameTarget target, boolean committed) {
            if (committed) endedCommitted++;
        }
    }
}
