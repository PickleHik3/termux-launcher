package com.termux.app.chrome;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class OverlayRegistryTest {

    /** A Back-only overlay that is open until Back closes it. */
    private static class Pane implements OverlayRegistry.Overlay {
        final String name;
        final List<String> log;
        boolean open;

        Pane(String name, boolean open, List<String> log) {
            this.name = name;
            this.open = open;
            this.log = log;
        }

        @Override public boolean onBack() {
            if (!open) return false;
            open = false;
            log.add(name + ":back");
            return true;
        }

        @Override public void closeImmediately(@NonNull OverlayRegistry.CloseReason reason) {
            open = false;
            log.add(name + ":" + reason);
        }
    }

    /** A typed overlay: owns every key while open and handles Back itself through that route. */
    private static final class Editor extends Pane implements OverlayRegistry.TypedOverlay {
        Editor(String name, boolean open, List<String> log) {
            super(name, open, log);
        }

        @Override public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
            if (!open) return false;
            log.add(name + ":key" + keyCode);
            if (keyCode == KeyEvent.KEYCODE_BACK) open = false;
            return true;
        }

        @Override public boolean onCodePoint(int codePoint, boolean ctrlDown) {
            if (!open) return false;
            log.add(name + ":cp" + codePoint);
            return true;
        }

        @Override public boolean swallowsKeyUp() {
            return open;
        }
    }

    private static KeyEvent down(int keyCode) {
        return new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
    }

    @Test
    public void backGoesToTheInnermostOpenOverlayOnePressAtATime() {
        List<String> log = new ArrayList<>();
        OverlayRegistry registry = new OverlayRegistry();
        Pane inner = new Pane("inner", false, log);
        Pane middle = new Pane("middle", true, log);
        Pane outer = new Pane("outer", true, log);
        registry.register(inner);
        registry.register(middle);
        registry.register(outer);

        assertTrue(registry.onBackPressed());
        assertTrue(registry.onBackPressed());
        assertFalse("nothing left to close", registry.onBackPressed());
        assertEquals(List.of("middle:back", "outer:back"), log);
    }

    /** The route Back actually travels on a device, with the release swallowed once. */
    @Test
    public void theKeyChannelClaimsBackForPanesAndSwallowsTheRelease() {
        List<String> log = new ArrayList<>();
        OverlayRegistry registry = new OverlayRegistry();
        Pane pane = new Pane("pane", true, log);
        registry.register(pane);

        assertFalse("only a Back press is a pane's to claim",
            registry.consumeKeyDown(KeyEvent.KEYCODE_ESCAPE, down(KeyEvent.KEYCODE_ESCAPE)));
        assertTrue(registry.consumeKeyDown(KeyEvent.KEYCODE_BACK, down(KeyEvent.KEYCODE_BACK)));
        assertFalse(pane.open);
        assertTrue(registry.consumeKeyUp(KeyEvent.KEYCODE_BACK));
        assertFalse("the swallow is one-shot", registry.consumeKeyUp(KeyEvent.KEYCODE_BACK));
    }

    @Test
    public void aTypedOverlayOwnsKeysAndTextAheadOfEverythingBehindIt() {
        List<String> log = new ArrayList<>();
        OverlayRegistry registry = new OverlayRegistry();
        Editor editor = new Editor("editor", true, log);
        Pane pane = new Pane("pane", true, log);
        registry.register(editor);
        registry.register(pane);

        assertTrue(registry.consumeKeyDown(KeyEvent.KEYCODE_A, down(KeyEvent.KEYCODE_A)));
        assertTrue(registry.consumeCodePoint('x', false));
        assertTrue("a typed overlay swallows releases while up",
            registry.consumeKeyUp(KeyEvent.KEYCODE_A));
        assertTrue(registry.consumeKeyDown(KeyEvent.KEYCODE_BACK, down(KeyEvent.KEYCODE_BACK)));
        assertTrue("the pane behind it was never asked", pane.open);
        assertFalse("a typed overlay's Back leaves no pane claim behind",
            registry.consumeKeyUp(KeyEvent.KEYCODE_BACK));
        assertEquals(List.of("editor:key" + KeyEvent.KEYCODE_A, "editor:cp" + (int) 'x',
            "editor:key" + KeyEvent.KEYCODE_BACK), log);
    }

    @Test
    public void closeAllReachesEveryOverlayInOrderWithTheReason() {
        List<String> log = new ArrayList<>();
        OverlayRegistry registry = new OverlayRegistry();
        registry.register(new Pane("a", true, log));
        registry.register(new Editor("b", false, log));
        registry.register(new Pane("c", true, log));

        registry.closeAll(OverlayRegistry.CloseReason.HOME);

        assertEquals(List.of("a:HOME", "b:HOME", "c:HOME"), log);
        assertFalse(registry.onBackPressed());
    }
}
