package com.termux.app.help;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.termux.app.wall.PaneWallPage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Where the recorded gesture sits on a topic page, and when it stops: under the instruction, gone
 * when the panel is hidden or the reader moves to another page, and absent altogether on a topic
 * the recording run could not capture.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w400dp-h800dp")
public class HelpClipPlacementTest {

    private Activity activity;
    private HelpPanelView panel;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        panel = new HelpPanelView(activity);
        root.addView(panel, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @After public void tearDown() {
        HelpClips.forget();
    }

    private void openTopic(String topicId) {
        HelpNavigation navigation = new HelpNavigation();
        navigation.openTopic(PaneWallPage.TERMINAL, topicId);
        panel.show();
        panel.render(navigation, null, false);
    }

    private HelpClipView clipOnPage() {
        return find(panel);
    }

    private static HelpClipView find(View view) {
        if (view instanceof HelpClipView) return (HelpClipView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            HelpClipView found = find(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @Test public void theTopicsClipPlaysOnItsPage() {
        openTopic("places");
        HelpClipView card = clipOnPage();
        assertNotNull("the page should carry a clip card", card);
        assertEquals("places", card.clipId());
        assertSame(card, panel.clip());
        assertEquals(HelpClips.of(activity).forTopic("places").alt, card.getContentDescription());
    }

    @Test public void theCardSitsUnderTheInstruction() {
        openTopic("places");
        ViewGroup body = (ViewGroup) clipOnPage().getParent();
        int card = body.indexOfChild(clipOnPage());
        // Summary, then the instruction, then the gesture that performs it.
        assertEquals(2, card);
    }

    @Test public void aTopicWithNoRecordingHasNoCard() {
        openTopic("setup");
        assertNull(clipOnPage());
        assertNull(panel.clip());
    }

    @Test public void hidingThePanelLetsGoOfTheClip() {
        openTopic("places");
        HelpClipView card = clipOnPage();
        panel.hide();
        // The card stays on the page it belongs to; nothing is decoding behind it.
        assertFalse(card.isPlayerOpen());
        assertSame(card, panel.clip());
    }

    @Test public void movingToAnotherPageLetsGoOfTheClip() {
        openTopic("places");
        HelpClipView first = clipOnPage();
        HelpNavigation navigation = new HelpNavigation();
        navigation.open(PaneWallPage.TERMINAL);
        panel.render(navigation, null, false);
        assertNull(panel.clip());
        assertFalse(first.isPlayerOpen());
        assertNull(clipOnPage());
    }

    @Test public void aPhoneWithAnimationsOffGetsAStillToTap() {
        android.provider.Settings.Global.putFloat(activity.getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        openTopic("places");
        HelpClipView card = clipOnPage();
        assertNotNull(card);
        assertTrue("animations off: hold the first frame", card.isStill());
        assertTrue("and let a tap run the gesture once", card.isClickable());
    }

    @Test public void aPhoneWithAnimationsOnPlaysWithoutBeingAsked() {
        openTopic("places");
        assertFalse(clipOnPage().isStill());
        assertFalse(clipOnPage().isClickable());
    }

    @Test public void theCardIsAsTallAsTheRecordingsShape() {
        openTopic("places");
        HelpClipView card = clipOnPage();
        HelpClips.Clip recorded = HelpClips.of(activity).forTopic("places");
        card.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertEquals(600, card.getMeasuredWidth());
        assertEquals(Math.round(600f * recorded.height / recorded.width), card.getMeasuredHeight());
    }
}
