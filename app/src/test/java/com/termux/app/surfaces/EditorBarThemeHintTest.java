package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.termux.R;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The one-time bubble over the editor bar's palette glyph. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorBarThemeHintTest {

    private int mPalette;
    private int mSeen;
    private int mDone;

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    private EditorBar bar(float fontScale, boolean wanted, FrameLayout[] parentOut) {
        RuntimeEnvironment.setFontScale(fontScale);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        float density = themed.getResources().getDisplayMetrics().density;
        FrameLayout parent = new FrameLayout(themed);
        activity.setContentView(parent, new ViewGroup.LayoutParams(
            Math.round(411 * density), Math.round(914 * density)));
        EditorBar bar = new EditorBar(themed);
        bar.setListener(new EditorBar.Listener() {
            @Override public void onOrientation() {}
            @Override public void onUndo() {}
            @Override public void onDone() { mDone++; }
            @Override public void onPalette() { mPalette++; }
            @Override public void onThemeHintSeen() { mSeen++; }
        });
        bar.attach(parent);
        bar.setThemeHintWanted(wanted);
        bar.setShown(true);
        bar.setBarShown(true, false);
        parent.measure(View.MeasureSpec.makeMeasureSpec(Math.round(411 * density), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(Math.round(914 * density), View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, Math.round(411 * density), Math.round(914 * density));
        parentOut[0] = parent;
        return bar;
    }

    @Test
    public void theBubbleShowsOnceOwedAndStaysOnOneLineAtLargeText() {
        for (float scale : new float[] {1f, 1.3f}) {
            FrameLayout[] parent = new FrameLayout[1];
            EditorBar bar = bar(scale, true, parent);
            assertTrue(bar.isThemeHintShown());
            TextView hint = (TextView) bar.themeHint();
            assertTrue(hint.getWidth() > 0);
            assertEquals("single line at " + scale, 1, hint.getLayout().getLineCount());
            assertEquals(TextUtils.TruncateAt.END, hint.getEllipsize());
            assertTrue(hint.getWidth() <= parent[0].getWidth());
        }
    }

    @Test
    public void theBubbleStaysAwayWhenAlreadySeen() {
        FrameLayout[] parent = new FrameLayout[1];
        EditorBar bar = bar(1f, false, parent);
        assertFalse(bar.isThemeHintShown());
    }

    @Test
    public void aTapOnThePaletteOrTheBubbleEndsItForGood() {
        FrameLayout[] parent = new FrameLayout[1];
        EditorBar bar = bar(1f, true, parent);
        bar.themeHint().performClick();
        assertFalse(bar.isThemeHintShown());
        assertEquals(1, mSeen);
        assertEquals("the tap still opens the card", 1, mPalette);
        bar.setBarShown(false, false);
        bar.setBarShown(true, false);
        assertFalse("it does not come back with the bar", bar.isThemeHintShown());
        bar.themeHint().performClick();
        assertEquals("recorded once", 1, mSeen);
    }

    @Test
    public void theBubbleFollowsTheBarAndLeavesDoneAlone() {
        FrameLayout[] parent = new FrameLayout[1];
        EditorBar bar = bar(1f, true, parent);
        bar.setBarShown(false, false);
        assertFalse("a raised card hides it with the bar", bar.isThemeHintShown());
        bar.setBarShown(true, false);
        assertTrue(bar.isThemeHintShown());
        assertEquals(0, mSeen);
        assertEquals(0, mDone);
    }
}
