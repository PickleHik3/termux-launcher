package com.termux.shared.termux.settings.preferences;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ScratchpadFontSizePreferencesTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences store = context.getSharedPreferences("scratchpad-font-test", 0);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    @Test
    public void firstReadCopiesMainSizeThenBothSizesRemainIndependent() {
        preferences.setFontSize(40);
        assertEquals(40, preferences.getScratchpadFontSize());

        preferences.setFontSize(46);
        assertEquals(40, preferences.getScratchpadFontSize());
        preferences.setScratchpadFontSize(52);
        assertEquals(46, preferences.getFontSize());
        assertEquals(52, preferences.getScratchpadFontSize());
    }
}
