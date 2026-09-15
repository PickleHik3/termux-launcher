package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeyColorRole;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.google.android.material.button.MaterialButton;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * How the key row draws what it was told: a key that cannot act here is dead, a key that was given
 * a colour wears it, and while the editor is picking neither of those gets in the way.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ExtraKeysViewKeyStyleTest {

    private static final String ROW =
        "[['ESC', {key: 'TAB', color: 'primary_container'}, {key: 'tool:wall.widgets'}]]";

    private ExtraKeysView view;
    private android.content.Context context;

    @Before
    public void setUp() throws Exception {
        // MaterialButton refuses to build under anything that is not an AppCompat descendant, and
        // the role colours are read off the theme, so the row is built in a Material one.
        context = new android.view.ContextThemeWrapper(RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight);
        view = new ExtraKeysView(context, null);
        view.reload(new ExtraKeysInfo(ROW,
            ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES), 40f);
    }

    @Test
    public void aKeyWithNoColourKeepsTheRowsFlatCap() {
        assertTrue(button(0).getBackground() instanceof ColorDrawable);
    }

    @Test
    public void aColouredKeyWearsItsRoleAndTheMatchingLabel() {
        MaterialButton tab = button(1);
        Drawable background = tab.getBackground();
        assertTrue("a coloured cap is a rounded shape, not a flat fill",
            background instanceof GradientDrawable);
        int expected = ExtraKeyColorRole.PRIMARY_CONTAINER
            .background(context);
        assertEquals(expected, ((GradientDrawable) background).getColor().getDefaultColor());
        assertEquals(ExtraKeyColorRole.PRIMARY_CONTAINER.label(context),
            tab.getCurrentTextColor());
        assertTrue(((GradientDrawable) background).getCornerRadius() > 0f);
    }

    @Test
    public void anUnusableKeyIsDeadAtThirtyEightPercentAndTakesNoTaps() {
        // Everything but the place switch is unusable here.
        view.setKeyUsabilityPolicy(value -> value.startsWith("tool:wall."));

        MaterialButton esc = button(0);
        assertFalse("a key that cannot act must not take a tap", esc.isEnabled());
        assertEquals(97, Color.alpha(esc.getCurrentTextColor()));
        assertTrue("a dead key carries no background tint",
            esc.getBackground() instanceof ColorDrawable);

        // A coloured key that cannot act loses its colour too, rather than reading as live.
        MaterialButton tab = button(1);
        assertFalse(tab.isEnabled());
        assertTrue(tab.getBackground() instanceof ColorDrawable);

        MaterialButton wall = button(2);
        assertTrue(wall.isEnabled());
        assertEquals(255, Color.alpha(wall.getCurrentTextColor()));
    }

    @Test
    public void aDeadKeyComesBackWhenThePlaceMovesUnderIt() {
        view.setKeyUsabilityPolicy(value -> false);
        assertFalse(button(0).isEnabled());
        view.setKeyUsabilityPolicy(value -> true);
        assertTrue(button(0).isEnabled());
        view.setKeyUsabilityPolicy(null);
        assertTrue(button(0).isEnabled());
    }

    @Test
    public void aDeadKeyNeverReachesTheClient() {
        RecordingClient client = new RecordingClient();
        view.setExtraKeysViewClient(client);
        view.setKeyUsabilityPolicy(value -> false);
        // A disabled view swallows the touch without it ever reaching the touch listener, which is
        // what "taps ignored" means here: the key eats the press and does nothing with it.
        button(0).dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN));
        button(0).dispatchTouchEvent(motion(MotionEvent.ACTION_UP));
        assertTrue(client.clicked.isEmpty());
    }

    @Test
    public void pickModeReportsTheKeyInsteadOfFiringIt() {
        RecordingClient client = new RecordingClient();
        view.setExtraKeysViewClient(client);
        // Even a key that cannot act here can be dressed, so pick mode revives every one of them.
        view.setKeyUsabilityPolicy(value -> false);
        final List<Integer> picked = new ArrayList<>();
        view.setKeyPickListener((index, info, button) -> picked.add(index));
        view.setPickMode(true);

        assertTrue("pick mode makes every key live again", button(0).isEnabled());
        button(0).dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN));
        button(0).dispatchTouchEvent(motion(MotionEvent.ACTION_UP));
        assertEquals(1, picked.size());
        assertEquals(Integer.valueOf(0), picked.get(0));
        assertTrue("a picked key must not fire", client.clicked.isEmpty());

        view.setPickMode(false);
        assertFalse("the row goes back to what the place allows", button(0).isEnabled());
    }

    @Test
    public void aPreviewedColourStandsInFrontOfTheStoredOneUntilItIsCleared() {
        MaterialButton esc = button(0);
        view.previewKeyColor(esc, ExtraKeyColorRole.ERROR);
        assertEquals(ExtraKeyColorRole.ERROR.background(context),
            ((GradientDrawable) esc.getBackground()).getColor().getDefaultColor());

        // Previewing "no colour" over a key that has one takes its colour off.
        MaterialButton tab = button(1);
        view.previewKeyColor(tab, null);
        assertTrue(tab.getBackground() instanceof ColorDrawable);

        view.clearPreviewColors();
        assertTrue(esc.getBackground() instanceof ColorDrawable);
        assertTrue("the stored colour comes back",
            tab.getBackground() instanceof GradientDrawable);
    }

    @Test
    public void aThemeRefreshRepaintsWithoutRebuildingTheRow() {
        MaterialButton tab = button(1);
        Drawable before = tab.getBackground();
        view.refreshKeyStyles();
        assertNotNull(tab.getBackground());
        assertTrue(tab.getBackground() instanceof GradientDrawable);
        // Same colour, freshly resolved — the point is that the row was not rebuilt.
        assertEquals(((GradientDrawable) before).getColor().getDefaultColor(),
            ((GradientDrawable) tab.getBackground()).getColor().getDefaultColor());
        assertSame(tab, button(1));
    }

    @Test
    public void reloadingDropsEveryPreviewSoAnEditedRowStartsFromWhatItStores() throws Exception {
        view.previewKeyColor(button(0), ExtraKeyColorRole.ERROR);
        view.reload(new ExtraKeysInfo(ROW,
            ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES), 40f);
        assertTrue(button(0).getBackground() instanceof ColorDrawable);
    }

    @Test
    public void theRowStillAnswersForItsKeysByIndex() {
        assertNotNull(view.definitionForChild(0));
        assertEquals("ESC", view.definitionForChild(0).getKey());
        assertEquals("TAB", view.definitionForChild(1).getKey());
        assertNull(view.definitionForChild(9));
    }

    private MaterialButton button(int index) {
        return (MaterialButton) view.getChildAt(index);
    }

    private static MotionEvent motion(int action) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, action, 1f, 1f, 0);
    }

    private static final class RecordingClient implements ExtraKeysView.IExtraKeysView {
        final List<String> clicked = new ArrayList<>();

        @Override
        public void onExtraKeyButtonClick(android.view.View view, ExtraKeyButton buttonInfo,
                                          MaterialButton button) {
            clicked.add(buttonInfo.getKey());
        }

        @Override
        public boolean performExtraKeyButtonHapticFeedback(android.view.View view,
                                                           ExtraKeyButton buttonInfo,
                                                           MaterialButton button) {
            return true;
        }
    }
}
