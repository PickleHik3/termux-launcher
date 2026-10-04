package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** The search row's placeholder is the input's own hint, not a floating wrapper label. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsSearchLayoutTest {

    @Test
    public void hintLivesOnTheInputAndWrapperHintIsDisabled() {
        Application app = RuntimeEnvironment.getApplication();
        ContextThemeWrapper context = new ContextThemeWrapper(app, R.style.Theme_TermuxApp_DayNight_NoActionBar);
        View row = LayoutInflater.from(context).inflate(R.layout.preference_settings_search, null);
        TextInputEditText input = row.findViewById(R.id.settings_search_input);
        assertNotNull(input);
        assertEquals(app.getString(R.string.settings_search_hint), input.getHint().toString());
        TextInputLayout wrapper = (TextInputLayout) input.getParent().getParent();
        assertFalse(wrapper.isHintEnabled());
    }
}
