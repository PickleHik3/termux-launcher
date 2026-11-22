package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import com.termux.R;
import com.termux.app.TermuxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * Hardware keys reach the drawer's Android-keyboard field and land in its text. The activity's
 * dispatch leg (focus on a non-terminal view routes PASS) is not covered: a Robolectric activity
 * without a window reports no current focus.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerHardwareKeysTest {

    @Test public void physicalKeysTypeIntoTheFocusedSearchField() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        AppDrawerController controller = activity.getAppDrawerController();
        assertTrue((Boolean) ReflectionHelpers.callInstanceMethod(controller, "bindViews"));
        AppDrawerContentView content = ReflectionHelpers.getField(controller, "mContent");
        content.setTextFieldSearch(true);
        AppDrawerSearchInputView field = content.searchInput();
        assertTrue(field.requestFocus());
        for (int code : new int[]{KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_A}) {
            field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        }
        assertEquals("ca", field.getText().toString());
    }
}
