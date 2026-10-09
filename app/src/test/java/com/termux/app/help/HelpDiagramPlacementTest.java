package com.termux.app.help;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.termux.app.wall.PaneWallPage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * Where the drawn diagram sits on a topic page, and when it stops: under the instruction, kept
 * at the drawable's own shape, still when the phone plays no animations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, qualifiers = "w400dp-h800dp")
public class HelpDiagramPlacementTest {

    private Activity activity;
    private HelpPanelView panel;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        panel = new HelpPanelView(activity);
        root.addView(panel, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void openTopic(String topicId) {
        HelpNavigation navigation = new HelpNavigation();
        navigation.openTopic(PaneWallPage.TERMINAL, topicId);
        panel.show();
        panel.render(navigation, false);
    }

    private HelpDiagramView diagramOnPage() {
        return find(panel);
    }

    private static HelpDiagramView find(View view) {
        if (view instanceof HelpDiagramView) return (HelpDiagramView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            HelpDiagramView found = find(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @Test public void theTopicsDiagramIsOnItsPage() {
        openTopic("places");
        HelpDiagramView card = diagramOnPage();
        assertNotNull("the page should carry a diagram card", card);
        assertEquals("places", card.topicId());
        assertSame(card, panel.diagram());
        assertTrue(card.hasDiagram());
        assertFalse(card.isAnimated());
    }

    @Test public void theCardSitsUnderTheInstruction() {
        openTopic("places");
        ViewGroup body = (ViewGroup) diagramOnPage().getParent();
        // Summary, then the instruction, then the drawing that shows it.
        assertEquals(2, body.indexOfChild(diagramOnPage()));
    }

    @Test public void movingToAnotherPageDropsTheDiagram() {
        openTopic("places");
        HelpNavigation navigation = new HelpNavigation();
        navigation.open(PaneWallPage.TERMINAL);
        panel.render(navigation, false);
        assertNull(panel.diagram());
        assertNull(diagramOnPage());
    }

    @Test public void theCardIsAsTallAsTheDrawingsShape() {
        openTopic("places");
        HelpDiagramView card = diagramOnPage();
        card.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertEquals(720, card.getMeasuredWidth());
        // places.xml is 360dp by 290dp.
        assertEquals(Math.round(720f * 290 / 360), card.getMeasuredHeight());
    }

    @Test public void mouseModeIsTheOnlyAnimatedDiagram() {
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            assertEquals(entry.id, "mouse_mode".equals(entry.id), HelpDiagrams.isAnimated(entry.id));
        }
        openTopic("mouse_mode");
        assertTrue(diagramOnPage().isAnimated());
    }

    @Test public void aPhoneWithAnimationsOffNeverStartsTheLoop() {
        android.provider.Settings.Global.putFloat(activity.getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        openTopic("mouse_mode");
        assertFalse(diagramOnPage().isPlaying());
    }

    @Test public void hidingThePanelStopsTheLoop() {
        openTopic("mouse_mode");
        HelpDiagramView card = diagramOnPage();
        panel.hide();
        assertFalse(card.isPlaying());
        assertSame(card, panel.diagram());
    }
}
