package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.termux.R;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

/**
 * The text input field takes focus only while the text input page is in front: a terminal view
 * going away on a window switch must not hand focus (and with it the system keyboard) to it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ExtraKeysBarFocusTest {

    private Activity activity;
    private FrameLayout root;
    private View terminal;
    private ExtraKeysBarView bar;
    private EditText field;
    private int released;

    private static ExtraKeysInfo keys() throws Exception {
        return new ExtraKeysInfo("[['A','B']]",
            ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES);
    }

    @Before
    public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).create().get();
        root = new FrameLayout(activity);
        terminal = new View(activity);
        terminal.setFocusable(true);
        terminal.setFocusableInTouchMode(true);
        root.addView(terminal, new FrameLayout.LayoutParams(500, 300));

        bar = new ExtraKeysBarView(activity, null);
        bar.setRowMetrics(100, 1f);
        bar.setKeySet(new ExtraKeysInfo[] {keys()});
        LinearLayout page = new LinearLayout(activity);
        field = new EditText(activity);
        field.setId(R.id.terminal_toolbar_text_input);
        page.addView(field, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bar.setTextInputPage(page);
        bar.setListener(new ExtraKeysBarView.Listener() {
            @Override public void onPageSelected(int p) { }
            @Override public void onCommittedHeightChanged() { }
            @Override public void onTextInputFocusReleased() { released++; }
        });
        root.addView(bar, new FrameLayout.LayoutParams(500, 100));
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 500, 400);
        assertTrue(terminal.requestFocus());
    }

    @Test
    public void theFieldCannotTakeFocusWhileAKeyPageIsInFront() {
        // A window switch: the focused terminal view goes away and Android looks for the next one.
        root.removeView(terminal);
        assertFalse(field.hasFocus());
        root.requestFocus();
        assertFalse(field.hasFocus());
        assertFalse(field.requestFocus());
        assertNotSame(field, root.focusSearch(View.FOCUS_DOWN));
        assertNotSame(field, root.focusSearch(View.FOCUS_FORWARD));
    }

    @Test
    public void theFieldIsNotFocusableWhileTheTextInputPageSlidesIn() {
        bar.setCurrentItem(1, true);
        assertFalse(field.isFocusable());
        assertFalse(field.requestFocus());
    }

    @Test
    public void theFieldTakesFocusOnceTheTextInputPageHasLandedAndLosesItAfter() {
        bar.setCurrentItem(1, false);
        assertTrue(field.requestFocus());
        assertTrue(field.hasFocus());
        bar.setCurrentItem(0, false);
        assertFalse(field.hasFocus());
        assertFalse(field.requestFocus());
        assertFalse(field.isFocusable());
        root.removeView(terminal);
        root.requestFocus();
        assertFalse(field.hasFocus());
    }

    @Test
    public void aFieldHoldingFocusOutOfTurnLosesItWhenAKeySetArrives() throws Exception {
        // A restored state or a race: the field has focus while a key page is in front.
        ((ViewGroup) field.getParent())
            .setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        field.setFocusable(true);
        field.setFocusableInTouchMode(true);
        assertTrue(field.requestFocus());
        assertTrue(field.hasFocus());
        bar.setKeySet(new ExtraKeysInfo[] {keys()});
        assertFalse(field.hasFocus());
        assertFalse(field.isFocusable());
        assertEquals(1, released);
    }
}
