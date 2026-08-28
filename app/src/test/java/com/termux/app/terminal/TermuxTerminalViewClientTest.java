package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The view client against a {@link FakeTerminalHost}: what it reads out of properties, what it
 * latches from the volume keys, and what it does to the font size, with no activity in sight.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TermuxTerminalViewClientTest {

    @Test
    public void modifiersAreUnlatchedWithoutAnExtraKeysRowOrAHeldVolumeKey() throws IOException {
        FakeTerminalHost host = host("volume-keys=virtual");
        TermuxTerminalViewClient client = client(host);

        assertFalse(client.readControlKey());
        assertFalse(client.readAltKey());
        assertFalse(client.readShiftKey());
        assertFalse(client.readFnKey());
    }

    @Test
    public void volumeKeysLatchCtrlAndFnWhileTheyActAsVirtualKeys() throws IOException {
        FakeTerminalHost host = host("volume-keys=virtual");
        TermuxTerminalViewClient client = client(host);

        assertTrue(client.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN), null));
        assertTrue(client.readControlKey());
        assertFalse(client.readFnKey());

        // Volume up latches the Fn key, which only the code point route reads: fn+k is the
        // writing mode toggle, and it consumes the character rather than writing it.
        assertTrue(client.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP), null));
        assertTrue(client.onCodePoint('k', false, null));
        assertEquals(1, host.toolbarToggles);
        // Alt and shift have no virtual key, so they stay unlatched.
        assertFalse(client.readAltKey());
        assertFalse(client.readShiftKey());

        assertTrue(client.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN,
            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN)));
        assertFalse(client.readControlKey());
    }

    @Test
    public void volumeKeysAreLeftAloneWhenTheyControlVolume() throws IOException {
        FakeTerminalHost host = host("volume-keys=volume");
        TermuxTerminalViewClient client = client(host);

        assertFalse(client.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN), null));
        assertFalse(client.readControlKey());
    }

    @Test
    public void backButtonMappingFollowsTheBackKeyProperty() throws IOException {
        assertFalse(client(host("back-key=back")).shouldBackButtonBeMappedToEscape());
        assertTrue(client(host("back-key=escape")).shouldBackButtonBeMappedToEscape());
    }

    @Test
    public void charBasedInputAndCtrlSpaceWorkaroundFollowTheirProperties() throws IOException {
        TermuxTerminalViewClient off = client(host());
        assertFalse(off.shouldEnforceCharBasedInput());
        assertFalse(off.shouldUseCtrlSpaceWorkaround());

        TermuxTerminalViewClient on = client(host(
            "enforce-char-based-input=true", "ctrl-space-workaround=true"));
        assertTrue(on.shouldEnforceCharBasedInput());
        assertTrue(on.shouldUseCtrlSpaceWorkaround());
    }

    @Test
    public void aModalSurfaceThatClaimsAStrokeEndsTheKeyPass() throws IOException {
        FakeTerminalHost host = host();
        host.overlaysConsume = true;
        TermuxTerminalViewClient client = client(host);

        assertTrue(client.onKeyDown(KeyEvent.KEYCODE_A,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A), null));
        assertTrue(client.onKeyUp(KeyEvent.KEYCODE_A,
            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)));
        assertTrue(client.onCodePoint('a', false, null));
    }

    @Test
    public void copyModeLocksTheDrawer() throws IOException {
        FakeTerminalHost host = host();
        TermuxTerminalViewClient client = client(host);

        client.copyModeChanged(true);
        assertTrue(host.drawerLocked);
        client.copyModeChanged(false);
        assertFalse(host.drawerLocked);
    }

    @Test
    public void terminalIsSelectedWhenThereIsNoToolbarPagerToSelectIn() throws IOException {
        FakeTerminalHost host = host();
        host.hasToolbar = false;
        assertTrue(client(host).isTerminalViewSelected());
    }

    @Test
    public void pinchesInsideTheDeadZoneAreLeftToTheView() throws IOException {
        FakeTerminalHost host = host();
        TermuxTerminalViewClient client = client(host);
        int size = host.preferences.getFontSize();

        assertEquals(1.05f, client.onScale(1.05f), 0.0001f);
        assertEquals(0.95f, client.onScale(0.95f), 0.0001f);
        assertEquals(0, host.paneFontSize);
        assertEquals(size, host.preferences.getFontSize());
    }

    @Test
    public void pinchesOutsideTheDeadZoneStepTheFocusedPaneFontSize() throws IOException {
        FakeTerminalHost host = host();
        TermuxTerminalViewClient client = client(host);
        int expected = host.preferences.stepFontSize(host.preferences.getFontSize(), true);

        assertEquals(1.0f, client.onScale(1.4f), 0.0001f);
        assertEquals(expected, host.paneFontSize);
        assertTrue(host.flushDockRequests > 0);
    }

    @Test
    public void repeatedPinchesClampAtThePreferenceBounds() throws IOException {
        FakeTerminalHost host = host();
        TermuxTerminalViewClient client = client(host);

        for (int i = 0; i < 200; i++) client.onScale(1.4f);
        assertEquals(fixedPoint(host.preferences, true), host.paneFontSize);

        for (int i = 0; i < 400; i++) client.onScale(0.6f);
        assertEquals(fixedPoint(host.preferences, false), host.paneFontSize);
    }

    /** Where {@link TermuxAppSharedPreferences#stepFontSize} stops moving, i.e. the clamp. */
    private static int fixedPoint(@NonNull TermuxAppSharedPreferences preferences, boolean increase) {
        int size = preferences.getFontSize();
        for (int i = 0; i < 500; i++) {
            int next = preferences.stepFontSize(size, increase);
            if (next == size) return size;
            size = next;
        }
        throw new AssertionError("font size never settled");
    }

    private static TermuxTerminalViewClient client(@NonNull FakeTerminalHost host) {
        return new TermuxTerminalViewClient(FakeTerminalHost.testContext(), host, null);
    }

    private static FakeTerminalHost host(String... propertyLines) throws IOException {
        return new FakeTerminalHost(FakeTerminalHost.testContext(), FakeTerminalHost.testProperties(propertyLines));
    }

}
