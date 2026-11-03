package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Up, Down and Enter on the drawer's Android-keyboard field. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerSearchInputViewKeysTest {

    @Test public void upAndDownAreKeptAndEnterRunsTheFirstResultOnce() {
        AppDrawerSearchInputView field =
            new AppDrawerSearchInputView(ApplicationProvider.getApplicationContext());
        int[] actions = {0};
        field.setListener(new AppDrawerSearchInputView.Listener() {
            @Override public void onQueryEdited(@NonNull String text, int caret) { }
            @Override public void onSearchAction() { actions[0]++; }
            @Override public void onDismissAction() { }
        });
        assertTrue(field.onKeyDown(KeyEvent.KEYCODE_DPAD_UP,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)));
        assertTrue(field.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
        assertEquals(0, actions[0]);
        assertTrue(field.onKeyDown(KeyEvent.KEYCODE_ENTER,
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
        assertEquals(1, actions[0]);
    }
}
