package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.Scope;
import com.termux.app.layouteditor.HintText;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The hint is plain sentences: the line, then one whole sentence for how far it reaches. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, application = Application.class)
public class HintStringsTest {

    private final Context mContext = RuntimeEnvironment.getApplication();

    @Test
    public void everyReachIsItsOwnSentence() {
        assertEquals("Drag it or tap a place to move it. Changes apply on the Home pages.",
            HintStrings.of(mContext, HintText.selected(Bar.APPS_ROW, false, Scope.HOME_PAGES,
                Scope.HOME_PAGES)));
        assertEquals("Drop it here to hide. Changes apply on every page.",
            HintStrings.of(mContext, HintText.selected(Bar.PAGE_BAR, false, null, null)));
        for (Scope scope : Scope.values()) {
            String text = HintStrings.of(mContext,
                HintText.selected(Bar.EXTRA_KEYS, true, scope, scope));
            assertTrue(text, text.endsWith("."));
            assertTrue(text, text.contains("Changes apply on "));
            assertFalse(text, text.contains("%"));
        }
    }

    @Test
    public void aRefusedDropIsOneShortSentence() {
        assertEquals("It can't go here.",
            HintStrings.of(mContext, HintText.dragging(Bar.APPS_ROW, null)));
    }
}
