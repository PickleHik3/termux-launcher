package com.termux.app.dock;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The reflection fades with Home's share across a crossing through its view's own alpha, so a
 * fade records nothing again, and a view stood up later wears the fade already set.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PeekGlassReflectionFadeTest {

    private static FrameLayout host() {
        return new FrameLayout(ApplicationProvider.getApplicationContext());
    }

    @Test public void theFadeIsTheViewsAlpha() {
        PeekGlassReflection reflection = new PeekGlassReflection(2.625f);
        View view = reflection.attachHost(host(), null);
        assertEquals(1f, view.getAlpha(), 0f);
        reflection.setFade(0.4f);
        assertEquals(0.4f, view.getAlpha(), 1e-6f);
        assertEquals(0.4f, reflection.fade(), 1e-6f);
    }

    @Test public void theFadeClamps() {
        PeekGlassReflection reflection = new PeekGlassReflection(2.625f);
        View view = reflection.attachHost(host(), null);
        reflection.setFade(-1f);
        assertEquals(0f, view.getAlpha(), 0f);
        reflection.setFade(3f);
        assertEquals(1f, view.getAlpha(), 0f);
    }

    @Test public void aViewAttachedLaterWearsTheFade() {
        PeekGlassReflection reflection = new PeekGlassReflection(2.625f);
        reflection.setFade(0.25f);
        View view = reflection.attachHost(host(), null);
        assertEquals(0.25f, view.getAlpha(), 1e-6f);
    }
}
