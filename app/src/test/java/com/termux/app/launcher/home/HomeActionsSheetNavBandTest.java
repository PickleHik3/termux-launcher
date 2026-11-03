package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.View;
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
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Home's long-press sheet under the navigation bar, on pong's 1080x2412 at 420 dpi: the card's
 * rows clear a gesture bar (63 px) and a three-button bar (126 px) by its own bottom padding, the
 * insets reach it although the shell's root before it in the content frame consumes them, and it
 * tells the activity how much of the band under the bar it covers, so the Docked dock's under-pill
 * strip (the decor's topmost child) fades out instead of painting over the card's foot.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w411dp-h918dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomeActionsSheetNavBandTest {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2412;
    private static final int GESTURE_BAR = 63;
    private static final int THREE_BUTTON_BAR = 126;

    private ContextThemeWrapper themed;
    private final List<Float> covers = new ArrayList<>();

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        themed = new ContextThemeWrapper(activity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
    }

    @NonNull
    private HomeActionsSheet sheet() {
        return new HomeActionsSheet(themed, new HomeActionsSheet.Listener() {
            @Override public void onHomeAction(@NonNull HomeActionsSheetPolicy.Item item) { }
            @Override public void onNavBandCovered(float covered) {
                covers.add(covered);
            }
        });
    }

    @NonNull
    private static WindowInsetsCompat navBar(int bottom) {
        return new WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 63, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
            .build();
    }

    @NonNull
    private static View card(@NonNull HomeActionsSheet sheet) {
        return sheet.findViewById(R.id.home_actions_card);
    }

    private static void layout(@NonNull View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
    }

    @Test
    public void coverRunsFromNoneBelowTheEdgeToAllPastTheBand() {
        assertEquals(0f, HomeActionsSheetPolicy.navBandCover(HEIGHT, HEIGHT, GESTURE_BAR), 0f);
        assertEquals(0f, HomeActionsSheetPolicy.navBandCover(HEIGHT + 10, HEIGHT, GESTURE_BAR), 0f);
        assertEquals(1f / 3f,
            HomeActionsSheetPolicy.navBandCover(HEIGHT - 21, HEIGHT, GESTURE_BAR), 1e-6f);
        assertEquals(1f, HomeActionsSheetPolicy.navBandCover(HEIGHT - 63, HEIGHT, GESTURE_BAR), 0f);
        assertEquals(1f, HomeActionsSheetPolicy.navBandCover(1400, HEIGHT, THREE_BUTTON_BAR), 0f);
        // No bar: any card on screen covers the (empty) band; none off screen.
        assertEquals(1f, HomeActionsSheetPolicy.navBandCover(HEIGHT - 1, HEIGHT, 0), 0f);
        assertEquals(0f, HomeActionsSheetPolicy.navBandCover(HEIGHT, HEIGHT, 0), 0f);
    }

    @Test
    public void rowsClearTheGestureBarAndTheThreeButtonBar() {
        HomeActionsSheet sheet = sheet();
        View card = card(sheet);
        int base = card.getPaddingBottom();
        sheet.applyInsets(navBar(GESTURE_BAR));
        assertEquals(base + GESTURE_BAR, card.getPaddingBottom());
        sheet.applyInsets(navBar(THREE_BUTTON_BAR));
        assertEquals(base + THREE_BUTTON_BAR, card.getPaddingBottom());
        sheet.applyInsets(navBar(0));
        assertEquals(base, card.getPaddingBottom());
    }

    /**
     * The cause of the second half of the bug: the shell's root fits the system windows, so the
     * dispatch an overlay after it in the content frame sees carries no navigation bar. The sheet
     * no longer listens for it, so that dispatch leaves the padding the activity handed it alone.
     */
    @Test
    public void aConsumingRootBeforeTheSheetNeitherFeedsNorZeroesItsPadding() {
        FrameLayout content = new FrameLayout(themed);
        View root = new FrameLayout(themed);
        root.setFitsSystemWindows(true);
        content.addView(root, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int[] siblingSaw = {-1};
        View sibling = new View(themed);
        sibling.setOnApplyWindowInsetsListener((v, insets) -> {
            siblingSaw[0] = insets.getSystemWindowInsetBottom();
            return insets;
        });
        content.addView(sibling);
        HomeActionsSheet sheet = sheet();
        content.addView(sheet, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View card = card(sheet);
        int base = card.getPaddingBottom();

        content.dispatchApplyWindowInsets(navBar(GESTURE_BAR).toWindowInsets());
        assertTrue("an overlay after the root saw the bar: " + siblingSaw[0], siblingSaw[0] <= 0);

        sheet.applyInsets(navBar(GESTURE_BAR));
        content.dispatchApplyWindowInsets(navBar(GESTURE_BAR).toWindowInsets());
        assertEquals(base + GESTURE_BAR, card.getPaddingBottom());
    }

    @Test
    public void anOpenCardCoversTheBandAndHandsItBackWhenPutAway() {
        HomeActionsSheet sheet = sheet();
        sheet.applyInsets(navBar(GESTURE_BAR));
        sheet.open(HomeActionsSheetPolicy.itemsFor(true, true, false, 2, false));
        layout(sheet);
        View card = card(sheet);
        // Laid out hidden, at or below the screen's edge: nothing covered yet.
        assertTrue("hidden card top " + card.getTop(), card.getTop() >= HEIGHT);
        assertTrue(covers.isEmpty());
        // Settled up, standing on the screen's bottom edge, where the behaviour's slide leaves it.
        card.offsetTopAndBottom(HEIGHT - card.getHeight() - card.getTop());
        assertEquals(HEIGHT, card.getBottom());
        assertTrue(card.getTop() < HEIGHT - GESTURE_BAR);

        sheet.applyInsets(navBar(GESTURE_BAR));
        assertEquals(1f, covers.get(covers.size() - 1), 0f);

        sheet.closeImmediately();
        assertEquals(0f, covers.get(covers.size() - 1), 0f);
    }
}
