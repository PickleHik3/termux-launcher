package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.page.PageKind;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.Slot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Home's A–Z under Terl (Terl decisions 2026-11-28; review P1 and P2), through the activity's own
 * population and scrub: at the bottom the letters are the control bar's row, while the arrangement
 * hides the stack's index for it, and they must still be filled and scrub; on a side the grouped
 * column waits for its band's layout with no re-post.
 *
 * <p>Built as {@link AzStandaloneStripEmptyPinnedTest} builds its screen: the real layout, store
 * and edge stacks in a window. The control bar itself needs the tiling desktop; what the letters
 * need of it is the row it lends them ({@code mAzJoinedRowView}), stood here in the window.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class,
    qualifiers = "w400dp-h800dp-port-mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class TerlJoinedAzRowTest {

    private static final int SCREEN_WIDTH = 400;
    private static final int SCREEN_HEIGHT = 800;
    private static final char LETTER = 'B';

    private Context context;
    private TermuxActivity screen;
    private FrameLayout window;
    private ActivityController<Activity> activity;
    private float durationScaleBefore;

    @Before
    public void setUp() {
        durationScaleBefore = ReflectionHelpers.getStaticField(ValueAnimator.class, "sDurationScale");
        ReflectionHelpers.setStaticField(ValueAnimator.class, "sDurationScale", 1f);
        context = RuntimeEnvironment.getApplication().getApplicationContext();
    }

    @After
    public void tearDown() {
        if (activity != null) activity.pause().stop().destroy();
        ReflectionHelpers.setStaticField(ValueAnimator.class, "sDurationScale", durationScaleBefore);
    }

    /** Review P1: the joined row is filled and scrubs although the stack's index is hidden. */
    @Test
    public void theJoinedRowIsFilledAndScrubsOnHome() {
        AzScrubRowView joined = homeWithTheJoinedRow();
        assertFalse("the arrangement hides the stack's index (it is the bar's row)",
            ((com.termux.app.place.ResolvedArrangement)
                ReflectionHelpers.callInstanceMethod(screen, "arrangement")).azIndexShown());

        ReflectionHelpers.callInstanceMethod(screen, "setSuggestionBarView");
        ReflectionHelpers.callInstanceMethod(screen, "syncAzBarHosts");
        ReflectionHelpers.callInstanceMethod(screen, "syncAzScrubLettersAndTint");
        screen.mSuggestionBarView.reload();
        layoutWindow();
        idle();

        assertSame("the scrub serves the joined row", joined,
            ReflectionHelpers.getField(screen, "mAzScrubRowView"));
        assertTrue((Boolean) ReflectionHelpers.callInstanceMethod(screen, "isAzIndexEnabled"));
        List<Character> visible = visibleLetters(joined);
        assertTrue("the activity filled the row's letters: " + visible, visible.contains(LETTER));

        List<Character> letters = visibleLetters(joined);
        float along = (letters.indexOf(LETTER) + 0.5f) / letters.size();
        int[] at = new int[2];
        joined.getLocationOnScreen(at);
        dispatchOn(screen.getWindow().getDecorView(), MotionEvent.ACTION_DOWN,
            at[0] + joined.getWidth() * along, at[1] + joined.getHeight() * 0.5f);
        idle();

        assertEquals("the finger is on the letter", letters.indexOf(LETTER),
            (int) (Integer) ReflectionHelpers.getField(joined, "activeLetterIndex"));
        List<LauncherAppEntry> matches = screen.mSuggestionBarView.azStripVisibleEntries();
        assertFalse("the scrub produced the letter's apps", matches.isEmpty());
        for (LauncherAppEntry entry : matches)
            assertEquals(LETTER, Character.toUpperCase(entry.label.charAt(0)));
    }

    /** Review P2: a side column whose band has no height waits for its layout, posting nothing. */
    @Test
    public void aGroupedColumnWaitsForItsBandsLayoutWithoutReposting() {
        homeWithTheJoinedRow();
        FrameLayout band = new FrameLayout(screen);
        FrameLayout host = new FrameLayout(screen);
        band.addView(host);

        assertNull(ReflectionHelpers.callInstanceMethod(screen, "terlGroupedAzColumn",
            ReflectionHelpers.ClassParameter.from(View.class, host)));
        assertNull(ReflectionHelpers.callInstanceMethod(screen, "terlGroupedAzColumn",
            ReflectionHelpers.ClassParameter.from(View.class, host)));
        assertSame("it listens on the band", band, ReflectionHelpers.getField(screen, "mAzGroupedBand"));
        assertTrue("nothing queued while the band has no height",
            Shadows.shadowOf(Looper.getMainLooper()).isIdle());

        band.measure(View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        band.layout(0, 0, 40, 300);

        assertNull("laid out: the wait is over", ReflectionHelpers.getField(screen, "mAzGroupedBand"));
    }

    /**
     * Home with Terl's A–Z at the bottom: the stack's index hidden, as the bar's arrangement hides
     * it, and the row the bar lends the letters standing in the window.
     */
    private AzScrubRowView homeWithTheJoinedRow() {
        screen = Robolectric.buildActivity(TermuxActivity.class).get();
        screen.setContentView(R.layout.activity_termux);
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(screen, false);
        assertNotNull(preferences);
        preferences.setTerlHomeAzEdge("bottom");
        ReflectionHelpers.setField(screen, "mPreferences", preferences);

        SuggestionBarView bar = new SuggestionBarView(screen, null);
        bar.setDockIconSizePx(24);
        ViewGroup plank = screen.findViewById(R.id.apps_bar_plank_layer);
        plank.addView(bar, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ReflectionHelpers.setField(screen, "mSuggestionBarView", bar);
        bar.setAppDataProvider(loadedCatalogue());
        bar.reloadAllApps();

        PlaceLayout layout = indexHidden();
        PlaceLayoutStore store = ReflectionHelpers.callInstanceMethod(screen, "placeLayoutStore");
        for (PageKind place : PageKind.values())
            for (PlaceOrientation orientation : PlaceOrientation.values())
                for (Element element : Element.values())
                    store.setSlot(orientation, element, layout.slot(element));
        screen.applyEdgeStacks(layout);
        screen.syncPinnedAppsHost(layout);
        screen.applyDockLayout(screen.dockLayoutFor(layout));

        AzScrubRowView joined = new AzScrubRowView(screen);
        ViewGroup content = screen.findViewById(android.R.id.content);
        content.addView(joined, new FrameLayout.LayoutParams(SCREEN_WIDTH - 20, 24,
            android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL));
        ReflectionHelpers.setField(screen, "mAzJoinedRowView", joined);

        activity = Robolectric.buildActivity(Activity.class).setup();
        window = new FrameLayout(activity.get());
        activity.get().setContentView(window);
        window.addView(screen.getWindow().getDecorView(),
            new FrameLayout.LayoutParams(SCREEN_WIDTH, SCREEN_HEIGHT));
        layoutWindow();
        idle();
        return joined;
    }

    /** What the bar's arrangement draws under Terl: v1's bars and the stack's index hidden. */
    private static PlaceLayout indexHidden() {
        Map<Element, Slot> slots = new EnumMap<>(Element.class);
        slots.put(Element.STATUS, Slot.hiddenFrom(PlaceLayout.Edge.TOP, 0));
        slots.put(Element.APPS, Slot.hiddenFrom(PlaceLayout.Edge.BOTTOM, 0));
        slots.put(Element.AZ, Slot.hiddenFrom(PlaceLayout.Edge.BOTTOM, 1));
        slots.put(Element.EXTRA_KEYS, Slot.hiddenFrom(PlaceLayout.Edge.BOTTOM, 2));
        return new PlaceLayout(slots, PlaceLayout.KeyboardMode.RESIZE,
            PlaceLayout.KeyboardForm.DOCKED, 4, 4);
    }

    private static List<Character> visibleLetters(AzScrubRowView letters) {
        char[] shown = ReflectionHelpers.getField(letters, "visibleLetters");
        List<Character> visible = new ArrayList<>(shown.length);
        for (char c : shown) visible.add(c);
        return visible;
    }

    private void layoutWindow() {
        window.measure(View.MeasureSpec.makeMeasureSpec(SCREEN_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SCREEN_HEIGHT, View.MeasureSpec.EXACTLY));
        window.layout(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    }

    private void dispatchOn(View view, int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    private LauncherAppDataProvider loadedCatalogue() {
        ShadowPackageManager packageManager = Shadows.shadowOf(context.getPackageManager());
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (char letter : new char[] {'B', 'C'}) {
            for (int i = 0; i < 3; i++) {
                ResolveInfo resolveInfo = new ResolveInfo();
                resolveInfo.activityInfo = new ActivityInfo();
                resolveInfo.activityInfo.packageName = "com.example." + letter + i;
                resolveInfo.activityInfo.name = "Main" + letter + i;
                resolveInfo.activityInfo.applicationInfo = context.getApplicationInfo();
                resolveInfo.activityInfo.nonLocalizedLabel = letter + "ravo " + i;
                packageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);
            }
        }
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(context);
        provider.invalidate();
        provider.getAllAppsBlocking();
        assertTrue(provider.hasLoadedApps());
        return provider;
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS);
    }
}
