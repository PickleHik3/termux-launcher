package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** The card's bottom padding is its own plus the band under the pill, from one setter. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w411dp-h918dp-420dpi", application = Application.class)
public class HomeActionsSheetBandTest {
    private HomeActionsSheet sheet;
    private ViewGroup card;
    private int ownPadBottom;
    private int ownPadLeft;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        FrameLayout content = new FrameLayout(themed);
        activity.setContentView(content, new ViewGroup.LayoutParams(1080, 2412));
        sheet = new HomeActionsSheet(themed, new HomeActionsSheet.Listener() {
            @Override public void onHomeAction(@NonNull HomeActionsSheetPolicy.Item item) { }
        });
        content.addView(sheet, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card = ReflectionHelpers.getField(sheet, "card");
        ownPadBottom = card.getPaddingBottom();
        ownPadLeft = card.getPaddingLeft();
    }

    @Test public void theBottomPaddingIsItsOwnPlusTheBandAndFollowsIt() {
        sheet.setBottomBandPx(110);
        assertEquals(ownPadBottom + 110, card.getPaddingBottom());
        sheet.setBottomBandPx(0);
        assertEquals(ownPadBottom, card.getPaddingBottom());
        sheet.setBottomBandPx(48);
        assertEquals(ownPadBottom + 48, card.getPaddingBottom());
    }

    @Test public void theInsetsBottomDoesNotMoveItButTheSidesStillDo() {
        sheet.setBottomBandPx(48);
        sheet.applyInsets(new WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(12, 0, 16, 200))
            .build());
        assertEquals(ownPadBottom + 48, card.getPaddingBottom());
        assertEquals(ownPadLeft + 12, card.getPaddingLeft());
    }
}
