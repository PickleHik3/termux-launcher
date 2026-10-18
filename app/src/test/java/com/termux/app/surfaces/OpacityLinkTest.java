package com.termux.app.surfaces;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.surfaces.AppearanceLooks.Control;
import com.termux.app.surfaces.AppearanceLooks.Target;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

/**
 * The keyboard's opacity link: the arithmetic that moves the partner, the leader lines' shape, and
 * the link as the Custom row shows it, at the default text size and at Large.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OpacityLinkTest {

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    // ------------------------------------------------------------------------------ arithmetic

    @Test
    public void theTwoOpacitiesArePartnersAndNothingElseIs() {
        assertEquals(Control.KEY_OPACITY, OpacityLink.partner(Control.OPACITY));
        assertEquals(Control.OPACITY, OpacityLink.partner(Control.KEY_OPACITY));
        for (Control control : Control.values()) {
            if (control != Control.OPACITY && control != Control.KEY_OPACITY)
                assertNull(control.name(), OpacityLink.partner(control));
        }
    }

    @Test
    public void thePartnerMovesByTheSameAmountEitherWay() {
        assertEquals(70, OpacityLink.follow(40, 50, 60, Control.KEY_OPACITY));
        assertEquals(50, OpacityLink.follow(40, 30, 60, Control.KEY_OPACITY));
        assertEquals("Key opacity moving BG opacity", 25,
            OpacityLink.follow(80, 65, 40, Control.OPACITY));
        assertEquals("no move, no change", 60, OpacityLink.follow(40, 40, 60, Control.OPACITY));
    }

    @Test
    public void thePartnerStopsAtItsEndsAndComesBackByTheSharedOffset() {
        assertEquals(Control.KEY_OPACITY.max,
            OpacityLink.follow(40, 90, 80, Control.KEY_OPACITY));
        assertEquals(Control.OPACITY.min, OpacityLink.follow(60, 0, 20, Control.OPACITY));
        // Measured from where the move began: past the end and back, the offset holds.
        assertEquals(85, OpacityLink.follow(40, 45, 80, Control.KEY_OPACITY));
    }

    // -------------------------------------------------------------------------- leader lines

    @Test
    public void aLeaderRunsFromTheButtonDownAlongTheGapToTheTrack() {
        float[] leader = OpacityLink.leader(100f, 48f, 70f, 56f);
        assertArrayEquals(new float[] {100f, 48f, 100f, 52f, 70f, 52f, 70f, 56f}, leader, 0f);
        float[] right = OpacityLink.leader(100f, 48f, 130f, 56f);
        assertEquals("the bar turns toward the track", 130f, right[4], 0f);
        assertEquals("and ends on the track's top", 56f, right[7], 0f);
    }

    @Test
    public void theButtonIsCentredUnlessTheHeadingReachesThatFar() {
        // Tracks at 70 and 126, a 48 wide button: centred at 98 while the name ends by 74.
        assertEquals(98f, OpacityLink.buttonCentreX(70f, 126f, 24f, true, 62f), 0f);
        assertEquals(98f, OpacityLink.buttonCentreX(126f, 70f, 24f, true, 74f), 0f);
        assertEquals("just past a longer name", 104f,
            OpacityLink.buttonCentreX(70f, 126f, 24f, true, 80f), 0f);
        assertEquals("never past the far track", 126f,
            OpacityLink.buttonCentreX(70f, 126f, 24f, true, 140f), 0f);
        assertEquals("no name: centred", 98f,
            OpacityLink.buttonCentreX(70f, 126f, 24f, true, Float.NaN), 0f);
        // Right to left: the name leads from the right and pushes the button left.
        assertEquals(98f, OpacityLink.buttonCentreX(70f, 126f, 24f, false, 140f), 0f);
        assertEquals(92f, OpacityLink.buttonCentreX(70f, 126f, 24f, false, 116f), 0f);
        assertEquals(70f, OpacityLink.buttonCentreX(70f, 126f, 24f, false, 20f), 0f);
    }

    @Test
    public void theNamesRoomEndsWhereTheButtonStandsAtItsFarthest() {
        assertEquals(102f, OpacityLink.nameRoom(70f, 126f, 24f, true, 0f), 0f);
        assertEquals(86f, OpacityLink.nameRoom(70f, 126f, 24f, true, 16f), 0f);
        assertEquals(266f, OpacityLink.nameRoom(70f, 126f, 24f, false, 360f), 0f);
        assertEquals(0f, OpacityLink.nameRoom(70f, 126f, 24f, true, 200f), 0f);
    }

    @Test
    public void aLeaderNeverRisesWhenTheSlidersStandHigherThanTheButton() {
        float[] leader = OpacityLink.leader(100f, 60f, 70f, 56f);
        assertEquals(60f, leader[3], 0f);
        assertEquals(60f, leader[5], 0f);
    }

    // ------------------------------------------------------------------------------ the panel

    @Test
    public void theKeyboardsRowShowsTheLinkBetweenItsTwoOpacities() {
        AppearanceEditorPanel panel = keyboardRow(1f, true);
        assertTrue(panel.isOpacityLinkShown());
        assertTrue(panel.isOpacityLinked());
        assertLinkPlaced(panel);
    }

    @Test
    public void atLargeTextTheLinkStillStandsClearOfTheNameAndTheThemeButton() {
        AppearanceEditorPanel panel = keyboardRow(1.3f, false);
        assertTrue(panel.isOpacityLinkShown());
        assertFalse(panel.isOpacityLinked());
        assertLinkPlaced(panel);
    }

    @Test
    public void otherSelectionsHaveNoLinkAndTheNameGetsItsWidthBack() {
        AppearanceEditorPanel panel = keyboardRow(1.3f, true);
        TextView name = panel.view().findViewById(R.id.appearance_editor_row2_name);
        showRow(panel, Target.DOCK);
        panel.setOpacityLink(false, true);
        layOut(panel);
        assertFalse(panel.isOpacityLinkShown());
        float density = panel.view().getResources().getDisplayMetrics().density;
        assertEquals(Math.round(132 * density), name.getMaxWidth());
    }

    @Test
    public void aTapOnTheLinkReportsItAndARestateDoesNot() {
        AppearanceEditorPanel panel = keyboardRow(1f, false);
        List<Boolean> heard = new ArrayList<>();
        panel.setListener(new RecordingListener(heard));
        MaterialButton link = panel.opacityLinkButton();
        link.performClick();
        assertTrue(panel.isOpacityLinked());
        link.performClick();
        assertFalse("unlinking is the way back out", panel.isOpacityLinked());
        panel.setOpacityLink(true, true);
        assertTrue(panel.isOpacityLinked());
        assertEquals(java.util.Arrays.asList(true, false), heard);
        assertNotNull(link.getContentDescription());
    }

    // ------------------------------------------------------------------------------- helpers

    private static AppearanceEditorPanel keyboardRow(float fontScale, boolean linked) {
        RuntimeEnvironment.setFontScale(fontScale);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        AppearanceEditorPanel panel = AppearanceEditorPanel.inflate(themed,
            new FrameLayout(themed));
        panel.showAppearanceMode();
        showRow(panel, Target.KEYBOARD);
        panel.setOpacityLink(true, linked);
        layOut(panel);
        // The heading's cap is posted from the layout pass; the next pass applies it.
        ShadowLooper.idleMainLooper();
        layOut(panel);
        return panel;
    }

    private static void showRow(AppearanceEditorPanel panel, Target target) {
        panel.showRow2(target == Target.KEYBOARD ? R.string.appearance_editor_target_keyboard
            : R.string.appearance_editor_target_dock);
        List<AppearanceEditorPanel.SliderState> states = new ArrayList<>();
        for (Control control : AppearanceLooks.controls(target)) {
            states.add(new AppearanceEditorPanel.SliderState(control, control.max, true,
                value -> "Opacity · 100%"));
        }
        panel.setSliders(states);
        panel.setDoors(AppearanceLooks.doors(target));
    }

    private static void layOut(AppearanceEditorPanel panel) {
        View root = panel.view();
        int width = Math.round(root.getResources().getConfiguration().screenWidthDp
            * root.getResources().getDisplayMetrics().density);
        int height = panel.measureFor(EditorMode.LOOK, width);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    /**
     * The button is centred between the two opacity tracks in the heading row's height, clear of
     * the name and of Keyboard theme, and the frame drew a leader to each track.
     */
    private static void assertLinkPlaced(AppearanceEditorPanel panel) {
        View root = panel.view();
        SliderLinkFrame frame = root.findViewById(R.id.appearance_editor_row2_body);
        MaterialButton link = panel.opacityLinkButton();
        View head = root.findViewById(R.id.appearance_editor_row2_head);
        TextView name = root.findViewById(R.id.appearance_editor_row2_name);
        View theme = root.findViewById(R.id.appearance_editor_door_keyboard_theme);
        LegendSlider opacity = panel.sliderFor(Control.OPACITY);
        LegendSlider keys = panel.sliderFor(Control.KEY_OPACITY);
        assertNotNull(opacity);
        assertNotNull(keys);
        View sliders = root.findViewById(R.id.appearance_editor_sliders);
        float opacityX = sliders.getLeft() + opacity.getLeft() + opacity.trackCentreX();
        float keysX = sliders.getLeft() + keys.getLeft() + keys.trackCentreX();
        float left = link.getLeft() + link.getTranslationX();
        float top = link.getTop() + link.getTranslationY();
        float centre = left + link.getWidth() / 2f;
        float nameRight = head.getLeft() + name.getRight();
        if (nameRight + link.getWidth() / 2f <= (opacityX + keysX) / 2f)
            assertEquals("centred between the tracks", (opacityX + keysX) / 2f, centre, 1f);
        assertTrue("between the tracks", centre >= Math.min(opacityX, keysX) - 0.5f
            && centre <= Math.max(opacityX, keysX) + 0.5f);
        assertTrue("the name is whole", name.getLayout() != null
            && name.getLayout().getEllipsisCount(0) == 0);
        assertEquals("centred in the heading row", head.getTop() + head.getHeight() / 2f,
            top + link.getHeight() / 2f, 1f);
        assertTrue("the name ends before the link: " + name.getRight() + " > " + left,
            head.getLeft() + name.getRight() <= left + 0.5f);
        assertTrue("Keyboard theme starts after the link",
            head.getLeft() + theme.getLeft() >= left + link.getWidth() - 0.5f);
        assertTrue(frame.hasLeaders());
    }

    private static final class RecordingListener implements AppearanceEditorPanel.Listener {
        private final List<Boolean> mHeard;

        RecordingListener(List<Boolean> heard) {
            mHeard = heard;
        }

        @Override public void onLookStop(int stop, boolean dragging) {}
        @Override public void onStyle(boolean floating) {}
        @Override public void onCorners(int value, boolean dragging) {}
        @Override public void onMargin(int value, boolean dragging) {}
        @Override public void onSlider(Control control, int value, boolean dragging) {}
        @Override public void onSliderReleased() {}
        @Override public void onDoor(AppearanceLooks.Door door, View anchor) {}
        @Override public void onTrailStyle(String id) {}
        @Override public void onRetroEffect(String id) {}
        @Override public void onOpacityLink(boolean linked) { mHeard.add(linked); }
    }
}
