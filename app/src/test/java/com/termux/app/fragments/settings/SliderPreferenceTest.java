package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** The value mapping behind the Slider rows: range, clamping and the Slider's valueTo rule. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SliderPreferenceTest {

    @Test
    public void clampKeepsTheValueInsideTheRange() {
        assertEquals(90, SliderPreference.clamp(50, 90, 120));
        assertEquals(120, SliderPreference.clamp(500, 90, 120));
        assertEquals(100, SliderPreference.clamp(100, 90, 120));
    }

    @Test
    public void degenerateRangeCollapsesOntoMin() {
        assertEquals(5, SliderPreference.clamp(9, 5, 5));
        assertEquals(5, SliderPreference.clamp(9, 5, 3));
        assertEquals(6f, SliderPreference.sliderTo(5, 5), 0f);
        assertEquals(120f, SliderPreference.sliderTo(90, 120), 0f);
    }

    @Test
    public void setValueClampsToTheConfiguredRange() {
        Application app = RuntimeEnvironment.getApplication();
        SliderPreference slider = new SliderPreference(app);
        slider.setMin(35);
        slider.setMax(100);
        slider.setValue(10);
        assertEquals(35, slider.getValue());
        slider.setValue(250);
        assertEquals(100, slider.getValue());
        slider.setValue(72);
        assertEquals(72, slider.getValue());
    }

    @Test
    public void shrinkingTheRangePullsTheValueIn() {
        Application app = RuntimeEnvironment.getApplication();
        SliderPreference slider = new SliderPreference(app);
        slider.setValue(80);
        slider.setMax(60);
        assertEquals(60, slider.getValue());
    }
}
