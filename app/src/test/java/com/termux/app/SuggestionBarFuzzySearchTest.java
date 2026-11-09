package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class SuggestionBarFuzzySearchTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
    }

    @Test
    public void testPrefixMode_keepsStableOrder() throws Exception {
        SuggestionBarView suggestionBarView = new SuggestionBarView(context, null);
        // The dock hands the row its icon; a row handed no size draws none (#135).
        suggestionBarView.setDockIconSizePx(Math.round(24f * suggestionBarView.getResources().getDisplayMetrics().density));
        suggestionBarView.setShowIcons(false);

        List<SuggestionBarButton> buttons = Arrays.asList(
                new TestButton("alpine"),
                new TestButton("alpha"),
                new TestButton("beta")
        );
        suggestionBarView.setSuggestionButtons(new ArrayList<>(buttons));
        suggestionBarView.setMaxButtonCount(2);

        suggestionBarView.reloadWithInput("al", null);
        awaitChildCount(suggestionBarView, 2);

        assertEquals(2, suggestionBarView.getChildCount());
        String first = findContentDescription(suggestionBarView.getChildAt(0));
        String second = findContentDescription(suggestionBarView.getChildAt(1));
        List<String> texts = Arrays.asList(first, second);
        assertTrue(texts.contains("alpine"));
        assertTrue(texts.contains("alpha"));
    }

    @Test
    public void testFuzzyMode_ranksByRatioAndRespectsTolerance() throws Exception {
        SuggestionBarView suggestionBarView = new SuggestionBarView(context, null);
        // The dock hands the row its icon; a row handed no size draws none (#135).
        suggestionBarView.setDockIconSizePx(Math.round(24f * suggestionBarView.getResources().getDisplayMetrics().density));
        suggestionBarView.setShowIcons(false);
        suggestionBarView.setSearchTolerance(70);

        List<TestButton> buttons = Arrays.asList(
                new TestButton("terminal"),
                new TestButton("termux"),
                new TestButton("remote"),
                new TestButton("zzzz")
        );

        suggestionBarView.setSuggestionButtons(new ArrayList<>(buttons));
        suggestionBarView.setMaxButtonCount(buttons.size());

        suggestionBarView.reloadWithInput("termx", null);
        awaitChildCount(suggestionBarView, 1);

        assertEquals(1, suggestionBarView.getChildCount());
        assertEquals("termux", findContentDescription(suggestionBarView.getChildAt(0)));
    }

    private static void awaitChildCount(SuggestionBarView suggestionBarView, int expectedCount) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (suggestionBarView.getChildCount() != expectedCount && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10L);
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static String findContentDescription(View view) {
        if (view == null) return null;
        CharSequence direct = view.getContentDescription();
        if (direct != null && direct.length() > 0) {
            return direct.toString();
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            String nested = findContentDescription(group.getChildAt(i));
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static final class TestButton implements SuggestionBarButton {
        private final String text;
        private int ratio;

        private TestButton(String text) {
            this.text = text;
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public Boolean hasIcon() {
            return false;
        }

        @Override
        public android.graphics.drawable.Drawable getIcon() {
            return null;
        }

        @Override
        public void click() {
        }

        @Override
        public int getRatio() {
            return ratio;
        }

        @Override
        public void setRatio(int ratio) {
            this.ratio = ratio;
        }
    }
}
