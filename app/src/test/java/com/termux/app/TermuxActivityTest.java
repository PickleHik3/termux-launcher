package com.termux.app;

import android.graphics.drawable.GradientDrawable;

import com.termux.shared.termux.data.TermuxUrlUtils;
import org.junit.Assert;
import org.junit.Test;
import java.util.Collections;
import java.util.LinkedHashSet;

public class TermuxActivityTest {

    private void assertUrlsAre(String text, String... urls) {
        LinkedHashSet<String> expected = new LinkedHashSet<>();
        Collections.addAll(expected, urls);
        Assert.assertEquals(expected, TermuxUrlUtils.extractUrls(text));
    }

    @Test
    public void testExtractUrls() {
        assertUrlsAre("hello http://example.com world", "http://example.com");
        assertUrlsAre("http://example.com\nhttp://another.com", "http://example.com", "http://another.com");
        assertUrlsAre("hello http://example.com world and http://more.example.com with secure https://more.example.com", "http://example.com", "http://more.example.com", "https://more.example.com");
        assertUrlsAre("hello https://example.com/#bar https://example.com/foo#bar", "https://example.com/#bar", "https://example.com/foo#bar");
    }

    @Test
    public void testRecentAppsVisibilityPolicy() {
        Assert.assertTrue(TermuxActivity.shouldShowInRecents(true, false));
        Assert.assertFalse(TermuxActivity.shouldShowInRecents(true, true));
        Assert.assertFalse(TermuxActivity.shouldShowInRecents(false, false));
        Assert.assertFalse(TermuxActivity.shouldShowInRecents(false, true));
    }

    @Test
    public void keyboardMaterialBackgroundDoesNotContributeToLayoutSize() {
        GradientDrawable capturedBackdrop = new GradientDrawable();
        TermuxActivity.LayoutNeutralDrawable background =
            new TermuxActivity.LayoutNeutralDrawable(capturedBackdrop);

        Assert.assertEquals(0, background.getIntrinsicWidth());
        Assert.assertEquals(0, background.getIntrinsicHeight());
        Assert.assertEquals(0, background.getMinimumWidth());
        Assert.assertEquals(0, background.getMinimumHeight());
    }

}
