package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The editor bar keeps the lift {@code place} gave it when it comes back in over a card. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi", application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorBarPlaceTest {

    private EditorBar bar() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        FrameLayout parent = new FrameLayout(themed);
        activity.setContentView(parent);
        EditorBar bar = new EditorBar(themed);
        bar.attach(parent);
        bar.setShown(true);
        return bar;
    }

    @Test
    public void theBarComingBackAtPeekStandsOnItsLiftNotAtTheFoot() {
        EditorBar bar = bar();
        // Hidden above peek (the card at half), as the drag from half to peek finds it.
        bar.setBarShown(false, false);
        bar.place(300f, 400f);
        // The drag settles at peek: the bar comes back, non-animated and animated alike.
        bar.setBarShown(true, false);
        assertEquals(-300f, bar.barTranslationY(), 0.01f);
        bar.setBarShown(false, false);
        bar.setBarShown(true, true);
        bar.place(300f, 400f);
        // Mid-drop it stands above its lift, never down at the foot.
        assertTrue(bar.barTranslationY() <= -300f);
    }

    @Test
    public void thePlacedLiftWinsWhateverTheBarWasDoingBefore() {
        EditorBar bar = bar();
        bar.place(120f, 200f);
        bar.setBarShown(false, false);
        bar.setBarShown(true, false);
        assertEquals(-120f, bar.barTranslationY(), 0.01f);
    }
}
