package com.termux.app.haptics;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class HapticsTest {

    /** Counts the haptics a view was asked to perform. */
    private static final class CountingView extends View {
        int performed;

        CountingView(Context context) {
            super(context);
        }

        @Override
        public boolean performHapticFeedback(int feedbackConstant) {
            performed++;
            return true;
        }
    }

    @Test
    public void tickIsANoOpWhenTheHapticsSwitchIsOff() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        CountingView view = new CountingView(context);
        SharedPreferences preferences = SharedPreferenceUtils.getPrivateSharedPreferences(context,
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION);

        preferences.edit().putBoolean(TERMUX_APP.KEY_APP_HAPTICS_ENABLED, true).commit();
        Haptics.tick(view, HapticFeedbackConstants.LONG_PRESS);
        assertEquals(1, view.performed);

        preferences.edit().putBoolean(TERMUX_APP.KEY_APP_HAPTICS_ENABLED, false).commit();
        Haptics.tick(view, HapticFeedbackConstants.LONG_PRESS);
        assertEquals(1, view.performed);
    }
}
