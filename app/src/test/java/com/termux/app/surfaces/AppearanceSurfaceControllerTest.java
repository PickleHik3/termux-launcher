package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.surfaces.AppearanceSurfaceController.PageId;
import com.termux.app.wall.PaneWallPage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Appearance surface's navigation, with a scripted editor and a fake Overview:
 * which page Back goes to, that Look, Layout and the Overview share one session, and that the
 * swap back to the Overview waits for the end of the editor's hide. Animations are off (the animator scale is zero), so every hop the
 * surface itself plays lands at once; the editor's own steps are completed by the test.
 */
@RunWith(RobolectricTestRunner.class)
public class AppearanceSurfaceControllerTest {

    /** The editor, scripted: it records what it is asked and finishes each step when told to. */
    private static final class FakeEditor implements AppearanceSurfaceController.Editor {
        final List<String> calls = new ArrayList<>();
        boolean presented;
        EditorMode mode = EditorMode.LOOK;
        boolean dirty;
        @Nullable Runnable onDone;
        @Nullable Runnable settled;
        @Nullable Runnable hidden;
        @Nullable Runnable pendingLeave;
        /** The one question for a leave with the wallpaper pending: Save and Discard, as the dialog would run them. */
        @Nullable Runnable confirmSave;
        @Nullable Runnable confirmDiscard;

        int count(String call) {
            return Collections.frequency(calls, call);
        }

        @Override public void beginSession() { calls.add("begin"); }

        @Override public void awaitWallpaper(@NonNull Runnable ready) { ready.run(); }

        @Override public void present(@NonNull EditorMode mode, @Nullable PaneWallPage place, @Nullable String section,
                                      float fromScale, float fromTranslationY, @Nullable Runnable onSettled) {
            calls.add("present " + mode.name().toLowerCase(java.util.Locale.ROOT));
            presented = true;
            this.mode = mode;
            settled = onSettled;
        }

        @Override public boolean isPresented() { return presented; }

        @NonNull @Override public EditorMode mode() { return presented ? mode : EditorMode.LOOK; }

        @Override public void setMode(@NonNull EditorMode mode) {
            calls.add("mode " + mode.name().toLowerCase(java.util.Locale.ROOT));
            this.mode = mode;
            // As the real editor does: the bar's pill follows the mode.
            if (pageListener != null) pageListener.onModeChanged(mode);
        }

        @Override public void requestLeave(@NonNull Runnable proceed) {
            calls.add("leave?");
            if (!dirty) {
                proceed.run();
                return;
            }
            pendingLeave = proceed;
        }

        @Override public void confirmLeave(@NonNull Runnable onSave, @NonNull Runnable onDiscard) {
            calls.add("confirm?");
            confirmSave = onSave;
            confirmDiscard = onDiscard;
        }

        @Override public void dismiss(float toScale, float toTranslationY, @Nullable Runnable onHidden) {
            calls.add("dismiss");
            presented = false;
            hidden = onHidden;
        }

        @Override public void restoreLauncher() { calls.add("restore"); }

        @Override public void endSession() {
            calls.add("end");
            presented = false;
        }

        @Override public void setOnDone(@Nullable Runnable onDone) { this.onDone = onDone; }

        @Override public void onStopWhileOpen() { calls.add("stop"); }

        @Nullable AppearanceSurfaceController.PageListener pageListener;

        @Override public void setPageListener(@Nullable AppearanceSurfaceController.PageListener listener) {
            pageListener = listener;
        }

        @Override public void undo() { calls.add("undo"); }

        @Override public void done() { calls.add("done"); }

        /** What the editor says of its page when the surface writes down where the person is. */
        @Nullable String describedTarget;
        boolean describedCustom;
        int describedScroll;
        /** The remembered place the surface handed over for the next presentation. */
        @Nullable AppearanceReturnState restored;

        @Override public void describe(@NonNull AppearanceReturnState.Builder out) {
            if (!presented) return;
            if (mode == EditorMode.LOOK) out.look(describedTarget, describedCustom);
            else if (mode == EditorMode.ICONS) out.iconsScroll(describedScroll);
        }

        @Override public void restoreNext(@Nullable AppearanceReturnState state) { restored = state; }

        /** The frame has settled in the editor. */
        void settle() {
            Runnable r = settled;
            settled = null;
            if (r != null) r.run();
        }

        /** The frame has arrived back at the card. */
        void finishHide() {
            Runnable r = hidden;
            hidden = null;
            if (r != null) r.run();
        }
    }

    private static final class FakePage implements AppearanceSurfaceController.Page {
        final FrameLayout root;
        int shown;
        int hiddenCalls;
        boolean released;

        FakePage(Context context) {
            root = new FrameLayout(context);
        }

        @NonNull @Override public View root() { return root; }
        @NonNull @Override public CharSequence title() { return "Fake"; }
        @Override public void onShown() { shown++; }
        @Override public void onHidden() { hiddenCalls++; }
        @Override public void release() { released = true; }
    }

    private static final class FakeOverview implements AppearanceSurfaceController.OverviewPage {
        final FakePage page;
        final View strip;
        final View pager;
        float background = 1f;
        boolean pending;
        int commits;
        @Nullable Runnable commitDone;
        @Nullable Runnable commitFailed;

        @Override public boolean hasPendingChanges() { return pending; }

        @Override public void commit(@NonNull Runnable done, @NonNull Runnable failed) {
            commits++;
            commitDone = done;
            commitFailed = failed;
        }

        /** The apply the page started ends: it is set (and nothing is pending), or it failed. */
        void finishCommit(boolean ok) {
            Runnable r = ok ? commitDone : commitFailed;
            if (ok) pending = false;
            commitDone = null;
            commitFailed = null;
            if (r != null) r.run();
        }

        FakeOverview(Context context) {
            page = new FakePage(context);
            strip = new View(context);
            pager = new View(context);
            page.root.addView(strip);
            page.root.addView(pager);
        }

        @NonNull @Override public View root() { return page.root; }
        @NonNull @Override public CharSequence title() { return "Overview"; }
        @Override public void onShown() { page.onShown(); }
        @Override public void onHidden() { page.onHidden(); }
        @Override public void release() { page.release(); }
        @Nullable @Override public View sharedCard() { return null; }
        @NonNull @Override public List<View> leavingViews() { return Collections.singletonList(strip); }
        @NonNull @Override public List<View> fadingViews() { return Collections.singletonList(pager); }
        @Override public void setBackgroundAlpha(float alpha) { background = alpha; }

        @Nullable String centred;

        @Nullable @Override public String centredSlotName() { return centred; }
    }

    private static class FakeHost implements AppearanceSurfaceController.Host {
        final Activity activity;
        FakeOverview overview;
        int closed;
        int closedByUser;

        /** The preferences' copy of where the person left, and the clock it is measured on. */
        @Nullable String stored;
        long now = 1_700_000_000_000L;
        /** The card the last Overview was asked to open on. */
        @Nullable String askedCentred;

        FakeHost(Activity activity) {
            this.activity = activity;
        }

        @Override public void createOverview(@NonNull AppearanceSurfaceController.Navigator navigator,
                                             @Nullable String centredSlot,
                                             @NonNull Consumer<AppearanceSurfaceController.OverviewPage> ready) {
            askedCentred = centredSlot;
            createOverview(navigator, ready);
        }

        @Nullable @Override public String readReturnState() { return stored; }

        @Override public void writeReturnState(@NonNull String state) { stored = state; }

        @Override public long now() { return now; }

        @NonNull @Override public Context context() { return activity; }

        @Nullable @Override public ViewGroup content() { return activity.findViewById(android.R.id.content); }

        @Override public void createOverview(@NonNull AppearanceSurfaceController.Navigator navigator,
                                             @NonNull Consumer<AppearanceSurfaceController.OverviewPage> ready) {
            overview = new FakeOverview(activity);
            ready.accept(overview);
        }

        @Override public void onClosed() { closed++; }
        @Override public void onClosedByUser() { closedByUser++; }
    }

    private Activity mActivity;
    private FakeEditor mEditor;
    private FakeHost mHost;
    private AppearanceSurfaceController mSurface;
    /** The navigator the surface hands pages: its own, reached through the host's factory. */
    private AppearanceSurfaceController.Navigator mNavigator;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        // The editor page inflates the Material page bar.
        mActivity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        // Animations off: every hop the surface plays itself lands at once.
        Settings.Global.putFloat(mActivity.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        mEditor = new FakeEditor();
        mHost = new FakeHost(mActivity) {
            @Override public void createOverview(@NonNull AppearanceSurfaceController.Navigator navigator,
                                                 @NonNull Consumer<AppearanceSurfaceController.OverviewPage> ready) {
                mNavigator = navigator;
                super.createOverview(navigator, ready);
            }
        };
        mSurface = new AppearanceSurfaceController(mHost, mEditor);
    }

    private void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void toLook() {
        mSurface.open(PageId.OVERVIEW);
        mNavigator.openLook();
        mEditor.settle();
    }

    private void toIcons() {
        mSurface.open(PageId.OVERVIEW);
        mNavigator.openIcons();
        mEditor.settle();
    }

    @Test
    public void openingOnTheOverviewAddsOneHostViewAndBeginsOneSession() {
        mSurface.open(PageId.OVERVIEW);
        assertTrue(mSurface.isOpen());
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        ViewGroup content = mActivity.findViewById(android.R.id.content);
        View host = content.findViewById(R.id.appearance_surface_host);
        assertNotNull("one host view in the activity's content", host);
        assertSame("the Overview is its page", mHost.overview.root(), ((ViewGroup) host).getChildAt(0));
        View coverBar = ((ViewGroup) host).getChildAt(((ViewGroup) host).getChildCount() - 1);
        assertEquals("the cover's bar gave way to the page's own", View.INVISIBLE, coverBar.getVisibility());
        assertNull("the cover is lifted", host.getBackground());
        assertEquals(1, mEditor.count("begin"));
        assertEquals(1, mHost.overview.page.shown);
        assertEquals("the editor's Done is the surface's", true, mEditor.onDone != null);
        assertFalse("nothing is presented before a hop", mEditor.presented);
    }

    @Test
    public void theCoverCarriesTheSharedBarWhileTheOverviewLoads() {
        // Waydroid: between Settings and the Overview one frame was bare colorSurface, read as
        // black. The bar is on the cover from its first frame; the page arrives under it.
        java.util.concurrent.atomic.AtomicReference<Consumer<AppearanceSurfaceController.OverviewPage>> pending =
            new java.util.concurrent.atomic.AtomicReference<>();
        FakeHost slow = new FakeHost(mActivity) {
            @Override public void createOverview(@NonNull AppearanceSurfaceController.Navigator navigator,
                                                 @NonNull Consumer<AppearanceSurfaceController.OverviewPage> ready) {
                mNavigator = navigator;
                pending.set(ready);
            }
        };
        AppearanceSurfaceController surface = new AppearanceSurfaceController(slow, mEditor);
        surface.open(PageId.OVERVIEW);
        ViewGroup host = mActivity.findViewById(R.id.appearance_surface_host);
        assertNotNull("covered at once", host.getBackground());
        View bar = host.getChildAt(host.getChildCount() - 1);
        assertEquals(View.VISIBLE, bar.getVisibility());
        com.google.android.material.button.MaterialButton wallpaper =
            bar.findViewById(R.id.appearance_page_mode_wallpaper);
        assertTrue("the pill on Wallpaper", wallpaper.isChecked());

        bar.findViewById(R.id.appearance_page_mode_look).performClick();
        idle();
        assertEquals("Look waits for the Overview", 0, mEditor.count("present look"));
        assertTrue("and the pill stays where the page is", wallpaper.isChecked());

        FakeOverview overview = new FakeOverview(mActivity);
        pending.get().accept(overview);
        assertSame("the page goes under the bar", overview.root(), host.getChildAt(0));
        assertEquals("then the ordinary hop", 1, mEditor.count("present look"));
        mEditor.settle();
        assertEquals(PageId.LOOK, surface.shownPage());
    }

    @Test
    public void lookIsPresentedByTheHopAndTheOverviewIsHiddenNotDestroyed() {
        mSurface.open(PageId.OVERVIEW);
        mNavigator.openLook();
        assertEquals("present asked once", 1, mEditor.count("present look"));
        assertTrue("the hop is playing until the editor settles", mSurface.isTransitioning());
        assertEquals("not shown as Look yet", PageId.OVERVIEW, mSurface.shownPage());
        mEditor.settle();
        assertFalse(mSurface.isTransitioning());
        assertEquals(PageId.LOOK, mSurface.shownPage());
        assertEquals("hidden once", 1, mHost.overview.page.hiddenCalls);
        assertFalse("hidden, not released", mHost.overview.page.released);
        assertEquals(View.INVISIBLE, mHost.overview.root().getVisibility());
        assertEquals("the rows are gone and the background is clear", 0f, mHost.overview.background, 0f);
    }

    @Test
    public void onAnEditorPageTheHostLetsTapsThroughToTheEditorsOverlay() {
        // On pong a tap on a launcher element in the Look editor never reached the editor: the
        // host, clickable for the Overview, sat over the editor's overlay and took it.
        mSurface.open(PageId.OVERVIEW);
        ViewGroup content = mActivity.findViewById(android.R.id.content);
        View host = content.findViewById(R.id.appearance_surface_host);
        assertTrue("the Overview takes the touches it does not use", host.isClickable());
        mNavigator.openLook();
        mEditor.settle();
        assertFalse("Look: taps go through to the launcher", host.isClickable());
        mNavigator.back();
        assertTrue("back on the Overview it takes them again", host.isClickable());
    }

    @Test
    public void anEditorPageHasTheSharedBarAndAScrimStandsUnderTheLauncher() {
        mSurface.open(PageId.OVERVIEW);
        ViewGroup content = mActivity.findViewById(android.R.id.content);
        assertSame("colorSurface scrim at index 0 of the content view", mSurface.scrim(),
            content.getChildAt(0));
        mNavigator.openLook();
        mEditor.settle();
        ViewGroup host = content.findViewById(R.id.appearance_surface_host);
        View bar = host.getChildAt(host.getChildCount() - 1);
        TextView title = bar.findViewById(R.id.appearance_page_title);
        assertEquals("Look", title.getText().toString());
        assertEquals(View.VISIBLE, bar.getVisibility());
        assertNotNull(mEditor.pageListener);
        mEditor.pageListener.onModeChanged(EditorMode.LAYOUT);
        assertEquals("Layout", title.getText().toString());
        assertEquals(View.GONE, bar.findViewById(R.id.appearance_page_undo).getVisibility());
        mEditor.pageListener.onDirtyChanged(true);
        assertEquals(View.VISIBLE, bar.findViewById(R.id.appearance_page_undo).getVisibility());
        bar.findViewById(R.id.appearance_page_undo).performClick();
        bar.findViewById(R.id.appearance_page_done).performClick();
        assertEquals(1, mEditor.count("undo"));
        assertEquals(1, mEditor.count("done"));
        bar.findViewById(R.id.appearance_page_back).performClick();
        assertEquals("the bar's back is Back: it asks the editor to leave", 1, mEditor.count("leave?"));
        mSurface.closeNow();
        assertNull("the scrim goes with the surface", mSurface.scrim());
        assertNull(content.findViewById(R.id.appearance_surface_host));
    }

    @Test
    public void aDirectEditorOpenHasABarAndNoCover() {
        mSurface.open(PageId.LOOK);
        assertEquals(PageId.LOOK, mSurface.shownPage());
        ViewGroup content = mActivity.findViewById(android.R.id.content);
        ViewGroup host = content.findViewById(R.id.appearance_surface_host);
        assertNotNull(host);
        assertNotNull(host.findViewById(R.id.appearance_page_back));
        assertFalse("the host lets taps through to the frame", host.isClickable());
        assertSame(mSurface.scrim(), content.getChildAt(0));
    }

    @Test
    public void onlyThePersonsOwnCloseIsReportedAsTheirs() {
        // Settings' Appearance row goes back to Settings on the person's close only: Home and the
        // photo picker close the surface too, and must not.
        mSurface.open(PageId.OVERVIEW);
        mSurface.closeNow();
        assertEquals(1, mHost.closed);
        assertEquals("a forced close is not theirs", 0, mHost.closedByUser);
        mSurface.open(PageId.OVERVIEW);
        mNavigator.back();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        assertEquals(2, mHost.closed);
        assertEquals("Back on the Overview is", 1, mHost.closedByUser);
    }

    @Test
    public void lookLayoutAndTheOverviewShareOneSession() {
        toLook();
        // Layout while the editor is up: the mode changes, no second session and no second hop.
        mSurface.open(PageId.LAYOUT);
        assertEquals(PageId.LAYOUT, mSurface.shownPage());
        assertEquals(1, mEditor.count("present layout"));
        mSurface.open(PageId.LOOK);
        assertEquals(PageId.LOOK, mSurface.shownPage());
        assertEquals(2, mEditor.count("present look"));
        assertEquals("one session through all of it", 1, mEditor.count("begin"));
        assertEquals(0, mEditor.count("end"));

        mSurface.onBack();
        mEditor.finishHide();
        idle();
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        assertEquals("still the same session", 1, mEditor.count("begin"));
        assertEquals(0, mEditor.count("end"));
        assertFalse("and the same Overview, never released", mHost.overview.page.released);
    }

    @Test
    public void backGoesIconsToOverviewToTheLauncher() {
        toIcons();
        assertEquals(1, mEditor.count("present icons"));
        assertEquals(PageId.ICONS, mSurface.shownPage());
        assertEquals(View.INVISIBLE, mHost.overview.root().getVisibility());

        assertTrue(mSurface.onBack());
        assertEquals("the editor leaves through its own rule", 1, mEditor.count("leave?"));
        assertEquals(1, mEditor.count("dismiss"));
        mEditor.finishHide();
        idle();
        assertEquals("Icons goes back to the Overview", PageId.OVERVIEW, mSurface.shownPage());
        assertTrue(mSurface.isOpen());
        assertEquals(View.VISIBLE, mHost.overview.root().getVisibility());

        assertTrue(mSurface.onBack());
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        assertFalse("the Overview's Back closes", mSurface.isOpen());
        assertTrue(mHost.overview.page.released);
        assertEquals(1, mEditor.count("end"));
        assertFalse("nothing is left to take a Back", mSurface.onBack());
    }

    @Test
    public void theOverviewsIconPackHopPresentsTheEditorInIconsMode() {
        mSurface.open(PageId.OVERVIEW);
        mNavigator.openIcons();
        assertEquals("one hop, into the editor", 1, mEditor.count("present icons"));
        assertTrue(mSurface.isTransitioning());
        mEditor.settle();
        assertEquals(PageId.ICONS, mSurface.shownPage());
        assertEquals(1, mHost.overview.page.hiddenCalls);
        ViewGroup content = mActivity.findViewById(android.R.id.content);
        View host = content.findViewById(R.id.appearance_surface_host);
        assertFalse("the launcher is looked at, the host takes no touch", host.isClickable());
        ViewGroup bar = (ViewGroup) ((ViewGroup) host).getChildAt(((ViewGroup) host).getChildCount() - 1);
        assertTrue(((com.google.android.material.button.MaterialButton)
            bar.findViewById(R.id.appearance_page_mode_icon_pack)).isChecked());
    }

    @Test
    public void backFromAnEditorAsksFirstThenGoesToTheOverviewNotTheLauncher() {
        toLook();
        mEditor.dirty = true;
        assertTrue(mSurface.onBack());
        assertEquals("the unsaved-changes question comes first", 1, mEditor.count("leave?"));
        assertEquals("nothing moves until it is answered", 0, mEditor.count("dismiss"));
        assertEquals(PageId.LOOK, mSurface.shownPage());

        // Keep editing is the question never being answered with proceed.
        // Discard or Save: it proceeds, and the editor goes down to the Overview.
        mEditor.pendingLeave.run();
        assertEquals(1, mEditor.count("dismiss"));
        assertTrue(mSurface.isOpen());
    }

    @Test
    public void theSwapToTheOverviewWaitsForTheEndOfTheEditorsHide() {
        toLook();
        mSurface.onBack();
        assertEquals(1, mEditor.count("dismiss"));
        // The editor is on its way down: the surface has not swapped, nor put the launcher right.
        assertEquals("still the editor's page", PageId.LOOK, mSurface.shownPage());
        assertTrue(mSurface.isTransitioning());
        idle();
        assertEquals("the launcher is not touched under the hop", 0, mEditor.count("restore"));

        mEditor.finishHide();
        assertFalse(mSurface.isTransitioning());
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        assertEquals("put right on the next frame, not in the hide's own end", 0, mEditor.count("restore"));
        idle();
        assertEquals(1, mEditor.count("restore"));
    }

    @Test
    public void backWhileAHopPlaysIsSpentOnIt() {
        mSurface.open(PageId.OVERVIEW);
        mNavigator.openLook();
        assertTrue(mSurface.isTransitioning());
        assertTrue("consumed, so nothing underneath sees it", mSurface.onBack());
        assertEquals(0, mEditor.count("leave?"));
        assertTrue(mSurface.isOpen());
    }

    @Test
    public void doneInAnEditorGoesDownToTheOverviewAndThenClosesTheSurface() {
        toLook();
        assertNotNull(mEditor.onDone);
        mEditor.onDone.run();
        assertEquals("Done goes down without a question", 0, mEditor.count("leave?"));
        assertEquals(1, mEditor.count("dismiss"));
        mEditor.finishHide();
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        assertTrue("the surface closes from the Overview, on the next frame", mSurface.isOpen());
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        assertFalse(mSurface.isOpen());
        assertEquals("the launcher was put right first", 1, mEditor.count("restore"));
        assertEquals("a Done is the person's own close", 1, mHost.closedByUser);
    }

    @Test
    public void theEditorsPillGoesBackToWallpaperThroughTheUnsavedQuestion() {
        toLook();
        ViewGroup host = mActivity.findViewById(R.id.appearance_surface_host);
        View bar = host.getChildAt(host.getChildCount() - 1);
        com.google.android.material.button.MaterialButton wallpaper =
            bar.findViewById(R.id.appearance_page_mode_wallpaper);
        com.google.android.material.button.MaterialButton look =
            bar.findViewById(R.id.appearance_page_mode_look);
        assertEquals("the pill offers Wallpaper behind an Overview", View.VISIBLE, wallpaper.getVisibility());
        mEditor.dirty = true;
        wallpaper.performClick();
        idle();
        assertEquals("asked first", 1, mEditor.count("leave?"));
        assertEquals("kept editing: nothing moved", 0, mEditor.count("dismiss"));
        assertTrue("and the pill snapped back to Look", look.isChecked());
        mEditor.pendingLeave.run();
        assertEquals("answered: the editor goes down", 1, mEditor.count("dismiss"));
        idle();
        assertTrue("the pill reads Wallpaper while it goes", wallpaper.isChecked());
    }

    @Test
    public void theEditorsIconPackSegmentSwitchesTheModeInPlaceFromLookAndLayout() {
        toLook();
        ViewGroup host = mActivity.findViewById(R.id.appearance_surface_host);
        View bar = host.getChildAt(host.getChildCount() - 1);
        bar.findViewById(R.id.appearance_page_mode_icon_pack).performClick();
        idle();
        assertEquals("in place: the editor's mode moves", EditorMode.ICONS, mEditor.mode);
        assertEquals("no question, no hop", 0, mEditor.count("leave?"));
        assertEquals(0, mEditor.count("dismiss"));
        mEditor.pageListener.onModeChanged(EditorMode.ICONS);
        assertEquals(PageId.ICONS, mSurface.shownPage());
        bar.findViewById(R.id.appearance_page_mode_layout).performClick();
        idle();
        assertEquals(EditorMode.LAYOUT, mEditor.mode);
        bar.findViewById(R.id.appearance_page_mode_icon_pack).performClick();
        idle();
        assertEquals(EditorMode.ICONS, mEditor.mode);
        assertEquals("one session, one presentation", 1, mEditor.count("begin"));
        assertEquals(1, mEditor.count("present look"));
    }

    @Test
    public void openingIconsWhileTheEditorIsUpPresentsInPlace() {
        toLook();
        mSurface.open(PageId.ICONS);
        assertEquals(1, mEditor.count("present icons"));
        assertEquals(PageId.ICONS, mSurface.shownPage());
        assertEquals(0, mEditor.count("dismiss"));
    }

    @Test
    public void theEditorsPillSwitchesBetweenLookAndLayoutAndAnOverviewlessOpenHasNoWallpaperSegment() {
        toLook();
        ViewGroup host = mActivity.findViewById(R.id.appearance_surface_host);
        View bar = host.getChildAt(host.getChildCount() - 1);
        bar.findViewById(R.id.appearance_page_mode_layout).performClick();
        assertEquals("the editor's mode is the pill's", EditorMode.LAYOUT, mEditor.mode);
        mSurface.closeNow();
        mSurface.open(PageId.LOOK);
        ViewGroup direct = mActivity.findViewById(R.id.appearance_surface_host);
        View directBar = direct.getChildAt(direct.getChildCount() - 1);
        assertEquals(View.GONE, directBar.findViewById(R.id.appearance_page_mode_wallpaper).getVisibility());
    }

    @Test
    public void oneSessionSpansEveryPageAndClosesOnce() {
        toLook();
        mSurface.onBack();
        mEditor.finishHide();
        idle();
        mNavigator.openLayout();
        mEditor.settle();
        mSurface.onBack();
        mEditor.finishHide();
        idle();
        mNavigator.openIcons();
        mEditor.settle();
        mSurface.onBack();
        mEditor.finishHide();
        idle();
        mSurface.onBack();
        settleClose();
        assertEquals(1, mHost.closed);
    }

    @Test
    public void aSurfaceOpenedIntoAnEditorHasNoOverviewAndDoneCloses() {
        mSurface.open(PageId.LAYOUT, null, PaneWallPage.WIDGETS);
        assertEquals(PageId.LAYOUT, mSurface.shownPage());
        assertNull("no Overview was built", mHost.overview);
        assertEquals(1, mEditor.count("begin"));
        mEditor.onDone.run();
        assertFalse(mSurface.isOpen());
        assertEquals(1, mEditor.count("end"));
    }

    @Test
    public void homeLeavesThroughTheEditorsRuleAndClosesTheSession() {
        toLook();
        mEditor.dirty = true;
        mSurface.requestExit();
        assertTrue("asked first", mSurface.isOpen());
        assertEquals(1, mEditor.count("leave?"));
        mEditor.pendingLeave.run();
        assertFalse(mSurface.isOpen());
        assertEquals(1, mEditor.count("end"));
    }

    private void settleClose() {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
    }

    @Test
    public void doneOnTheOverviewWithNothingPendingJustCloses() {
        mSurface.open(PageId.OVERVIEW);
        mNavigator.done();
        settleClose();
        assertFalse(mSurface.isOpen());
        assertEquals(0, mHost.overview.commits);
        assertEquals(1, mHost.closedByUser);
    }

    @Test
    public void doneOnTheOverviewAppliesWhatIsPendingThenCloses() {
        mSurface.open(PageId.OVERVIEW);
        mHost.overview.pending = true;
        mNavigator.done();
        assertEquals(1, mHost.overview.commits);
        assertTrue("open while it applies", mSurface.isOpen());
        mHost.overview.finishCommit(true);
        settleClose();
        assertFalse(mSurface.isOpen());
        assertEquals(1, mHost.closedByUser);
    }

    @Test
    public void doneBackAndThePillWaitWhileTheCommitRuns() {
        mSurface.open(PageId.OVERVIEW);
        mHost.overview.pending = true;
        mNavigator.done();
        mNavigator.done();
        assertEquals("the second Done does nothing", 1, mHost.overview.commits);
        assertTrue("Back is spent, not acted on", mSurface.onBack());
        mNavigator.openLook();
        mNavigator.openIcons();
        mSurface.requestExit();
        assertTrue(mSurface.isOpen());
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        assertEquals(0, mEditor.count("confirm?"));
        assertEquals(0, mEditor.count("present look"));
    }

    @Test
    public void aFailedCommitStaysOnTheOverviewAndDoneTriesAgain() {
        mSurface.open(PageId.OVERVIEW);
        mHost.overview.pending = true;
        mNavigator.done();
        mHost.overview.finishCommit(false);
        settleClose();
        assertTrue("still open", mSurface.isOpen());
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        mNavigator.done();
        assertEquals(2, mHost.overview.commits);
        mHost.overview.finishCommit(true);
        settleClose();
        assertFalse(mSurface.isOpen());
    }

    @Test
    public void aCommitThatEndsAfterTheSurfaceClosedDoesNothing() {
        mSurface.open(PageId.OVERVIEW);
        mHost.overview.pending = true;
        mNavigator.done();
        FakeOverview old = mHost.overview;
        mSurface.closeNow();
        old.finishCommit(true);
        assertFalse(mSurface.isOpen());
        assertEquals(1, mHost.closed);
    }

    @Test
    public void doneOnTheIconPackPageSavesHopsBackAppliesTheWallpaperThenCloses() {
        mSurface.open(PageId.OVERVIEW);
        mHost.overview.pending = true;
        mNavigator.openIcons();
        mEditor.settle();
        assertEquals(PageId.ICONS, mSurface.shownPage());
        mNavigator.done();
        assertEquals("the editor saves its look", 1, mEditor.count("done"));
        mEditor.onDone.run();
        mEditor.finishHide();
        idle();
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        assertEquals(1, mHost.overview.commits);
        mHost.overview.finishCommit(true);
        settleClose();
        assertFalse(mSurface.isOpen());
    }

    @Test
    public void doneOnTheIconPackPageWithNothingPendingCloses() {
        toIcons();
        mNavigator.done();
        mEditor.onDone.run();
        mEditor.finishHide();
        idle();
        settleClose();
        assertFalse(mSurface.isOpen());
        assertEquals(0, mHost.overview.commits);
    }

    @Test
    public void doneInAnEditorSavesThenAppliesTheWallpaperOverTheOverviewThenCloses() {
        toLook();
        mHost.overview.pending = true;
        ViewGroup host = mActivity.findViewById(R.id.appearance_surface_host);
        View bar = host.getChildAt(host.getChildCount() - 1);
        bar.findViewById(R.id.appearance_page_done).performClick();
        assertEquals("the editor saves its look", 1, mEditor.count("done"));
        mEditor.onDone.run();
        mEditor.finishHide();
        idle();
        assertEquals("the Overview shows while it applies", PageId.OVERVIEW, mSurface.shownPage());
        assertEquals(1, mHost.overview.commits);
        mHost.overview.finishCommit(true);
        settleClose();
        assertFalse(mSurface.isOpen());
    }

    @Test
    public void backOnTheOverviewWithAPendingWallpaperAsksOnce() {
        mSurface.open(PageId.OVERVIEW);
        mHost.overview.pending = true;
        assertTrue(mSurface.onBack());
        assertEquals(1, mEditor.count("confirm?"));
        assertTrue("Keep editing: nothing happens", mSurface.isOpen());
        assertEquals(0, mHost.overview.commits);

        mEditor.confirmDiscard.run();
        settleClose();
        assertFalse("Discard closes without applying", mSurface.isOpen());
        assertEquals(0, mHost.overview.commits);
        assertTrue(mHost.overview.page.released);
    }

    @Test
    public void saveOnTheLeaveQuestionAppliesThenCloses() {
        mSurface.open(PageId.OVERVIEW);
        mHost.overview.pending = true;
        mSurface.onBack();
        mEditor.confirmSave.run();
        assertEquals(1, mHost.overview.commits);
        mHost.overview.finishCommit(true);
        settleClose();
        assertFalse(mSurface.isOpen());
    }

    @Test
    public void backWithNothingPendingNeverAsks() {
        mSurface.open(PageId.OVERVIEW);
        mSurface.onBack();
        settleClose();
        assertFalse(mSurface.isOpen());
        assertEquals(0, mEditor.count("confirm?"));
    }

    @Test
    public void homeWithADirtyEditorAndAPendingWallpaperAsksOneQuestion() {
        toLook();
        mEditor.dirty = true;
        mHost.overview.pending = true;
        mSurface.requestExit();
        assertEquals("one dialog covers both", 1, mEditor.count("confirm?"));
        assertEquals("not the editor's own as well", 0, mEditor.count("leave?"));
        assertTrue(mSurface.isOpen());

        mEditor.confirmSave.run();
        assertEquals(1, mHost.overview.commits);
        mHost.overview.finishCommit(true);
        assertFalse(mSurface.isOpen());
    }

    @Test
    public void homeDiscardClosesWithoutApplying() {
        toLook();
        mHost.overview.pending = true;
        mSurface.requestExit();
        mEditor.confirmDiscard.run();
        assertFalse(mSurface.isOpen());
        assertEquals(0, mHost.overview.commits);
    }

    @Test
    public void homeOnTheOverviewWithAPendingWallpaperAsks() {
        mSurface.open(PageId.OVERVIEW);
        mHost.overview.pending = true;
        mSurface.requestExit();
        assertEquals(1, mEditor.count("confirm?"));
        assertTrue(mSurface.isOpen());
    }

    @Test
    public void switchingPagesNeverAsksAboutTheWallpaper() {
        toLook();
        mHost.overview.pending = true;
        ViewGroup host = mActivity.findViewById(R.id.appearance_surface_host);
        View bar = host.getChildAt(host.getChildCount() - 1);
        bar.findViewById(R.id.appearance_page_mode_wallpaper).performClick();
        idle();
        assertEquals(0, mEditor.count("confirm?"));
        assertEquals("the editor's own rule only", 1, mEditor.count("leave?"));
        assertEquals(1, mEditor.count("dismiss"));
        mEditor.finishHide();
        idle();
        assertTrue("the pick is still pending", mHost.overview.pending);
        mNavigator.openIcons();
        mEditor.settle();
        mNavigator.back();
        mEditor.finishHide();
        idle();
        assertEquals(0, mEditor.count("confirm?"));
        assertTrue(mSurface.isOpen());
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
    }

    @Test
    public void aStopKeepsTheSurfaceAndTellsTheEditor() {
        mSurface.open(PageId.OVERVIEW);
        mSurface.onStop();
        assertTrue(mSurface.isOpen());
        assertEquals(1, mEditor.count("stop"));
    }

    // ------------------------------------------------------------ where the person left it

    @Test
    public void doneOnLookIsRememberedAndTheNextOpenComesBackThroughTheOverview() {
        toLook();
        mEditor.describedTarget = "TERMINAL";
        mEditor.describedCustom = true;
        mEditor.onDone.run();
        AppearanceReturnState saved = AppearanceReturnState.parse(mHost.stored);
        assertNotNull(saved);
        assertEquals(PageId.LOOK, saved.page);
        assertEquals("TERMINAL", saved.target);
        assertTrue(saved.custom);
        mEditor.finishHide();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        assertFalse(mSurface.isOpen());
        assertEquals("the close from the Overview keeps the editor page", PageId.LOOK,
            AppearanceReturnState.parse(mHost.stored).page);
        assertNull("nothing is left for a later presentation", mEditor.restored);

        mHost.now += 60_000L;
        mSurface.open(PageId.OVERVIEW);
        idle();
        assertNotNull("the Overview opens first", mHost.overview);
        assertEquals("then the normal hop into the remembered page", 2, mEditor.count("present look"));
        assertNotNull(mEditor.restored);
        assertEquals("TERMINAL", mEditor.restored.target);
        mEditor.settle();
        assertEquals(PageId.LOOK, mSurface.shownPage());
        ViewGroup host = mActivity.findViewById(R.id.appearance_surface_host);
        View bar = host.getChildAt(host.getChildCount() - 1);
        assertEquals("not a direct open: Wallpaper is offered", View.VISIBLE,
            bar.findViewById(R.id.appearance_page_mode_wallpaper).getVisibility());
        mSurface.onBack();
        mEditor.finishHide();
        idle();
        assertTrue("Back goes to the Overview, not out", mSurface.isOpen());
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
    }

    @Test
    public void homeOnIconPackRemembersTheRowsScroll() {
        toIcons();
        mEditor.describedScroll = 240;
        mSurface.requestExit();
        AppearanceReturnState saved = AppearanceReturnState.parse(mHost.stored);
        assertNotNull(saved);
        assertEquals(PageId.ICONS, saved.page);
        assertEquals(240, saved.iconsScrollPx);
    }

    @Test
    public void backOutOfTheOverviewRemembersItsCentredCard() {
        mSurface.open(PageId.OVERVIEW);
        mHost.overview.centred = "LOCK";
        mNavigator.back();
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        assertFalse(mSurface.isOpen());
        AppearanceReturnState saved = AppearanceReturnState.parse(mHost.stored);
        assertNotNull(saved);
        assertEquals(PageId.OVERVIEW, saved.page);
        assertEquals("LOCK", saved.wallpaperSlot);

        mSurface.open(PageId.OVERVIEW);
        assertEquals("the next Overview opens on that card", "LOCK", mHost.askedCentred);
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        assertEquals("and stays there", 0, mEditor.count("present look"));
    }

    @Test
    public void afterTheWindowTheSurfaceOpensOnTheOverviewAsBefore() {
        mHost.stored = new AppearanceReturnState.Builder(PageId.LAYOUT).place("WIDGETS")
            .wallpaperSlot("LOCK").build(mHost.now).serialize();
        mHost.now += com.termux.app.activities.SettingsBackStackState.RETAIN_WINDOW_MS + 1L;
        mSurface.open(PageId.OVERVIEW);
        idle();
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        assertEquals(0, mEditor.count("present layout"));
        assertNull(mEditor.restored);
        assertNull("the default card", mHost.askedCentred);
    }

    @Test
    public void anUnreadableStateOpensTheOverview() {
        mHost.stored = "{\"page\": \"SOMEWHERE\"";
        mSurface.open(PageId.OVERVIEW);
        idle();
        assertEquals(PageId.OVERVIEW, mSurface.shownPage());
        assertEquals(0, mEditor.count("present look"));
        assertNull(mEditor.restored);
    }

    @Test
    public void aDoorNamingAPageOpensItDirectAndIgnoresTheMemory() {
        mHost.stored = new AppearanceReturnState.Builder(PageId.ICONS).iconsScroll(80)
            .build(mHost.now).serialize();
        mSurface.open(PageId.LAYOUT);
        assertEquals(PageId.LAYOUT, mSurface.shownPage());
        assertEquals(0, mEditor.count("present icons"));
        assertNull(mEditor.restored);
    }
}
