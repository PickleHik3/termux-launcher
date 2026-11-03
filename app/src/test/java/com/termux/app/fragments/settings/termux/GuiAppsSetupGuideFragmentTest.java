package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import org.junit.After;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.termux.R;
import com.termux.app.AppEdition;
import com.termux.app.x11.GuiAppsSetup;
import com.termux.app.x11.GuiAppsSetupGuide;
import com.termux.app.x11.GuiAppsSetupGuide.Step;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;
import java.util.List;

/**
 * Set up Linux apps, the guide: which steps exist for each edition and source, and that the
 * progress line, the body and the footer fit a phone at 1.3x and 2.0x font scale.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w411dp-h914dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GuiAppsSetupGuideFragmentTest {

    private static final int WIDTH_DP = 411;
    private static final int HEIGHT_DP = 914;

    @Before
    public void freshChoices() {
        GuiAppsSetupGuide.preferences(RuntimeEnvironment.getApplication()).edit().clear().commit();
    }

    // ---- which steps exist ---------------------------------------------------------------

    @Test public void aFullLinuxGetsAllFiveSteps() {
        assertEquals(Arrays.asList(Step.SOURCE, Step.DISTRO, Step.STARTERS, Step.COMMAND, Step.GRAPHICS),
            GuiAppsSetupGuide.stepsFor(AppEdition.TERMUX, GuiAppsSetup.Route.DISTRO));
    }

    @Test public void termuxsOwnAppsSkipThePickOfALinux() {
        assertEquals(Arrays.asList(Step.SOURCE, Step.STARTERS, Step.COMMAND, Step.GRAPHICS),
            GuiAppsSetupGuide.stepsFor(AppEdition.TERMUX, GuiAppsSetup.Route.X11_REPO));
        assertEquals(Arrays.asList(Step.SOURCE, Step.STARTERS, Step.COMMAND, Step.GRAPHICS),
            GuiAppsSetupGuide.stepsFor(AppEdition.VAJ, GuiAppsSetup.Route.X11_REPO));
    }

    @Test public void nixHasNoGuideSteps() {
        assertTrue(GuiAppsSetupGuide.stepsFor(AppEdition.NIX, GuiAppsSetup.Route.DISTRO).isEmpty());
        assertTrue(GuiAppsSetupGuide.stepsFor(AppEdition.NIX, null).isEmpty());
    }

    @Test public void vajsOwnAppsOfferNoBrowser() {
        assertEquals(Arrays.asList(GuiAppsSetup.StarterApp.FILE_MANAGER),
            GuiAppsSetupGuide.startersOffered(AppEdition.VAJ, GuiAppsSetup.Route.X11_REPO));
        assertEquals(Arrays.asList(GuiAppsSetup.StarterApp.BROWSER, GuiAppsSetup.StarterApp.FILE_MANAGER),
            GuiAppsSetupGuide.startersOffered(AppEdition.VAJ, GuiAppsSetup.Route.DISTRO));
    }

    @Test public void theProgressLineCountsOnlyTheStepsThatExist() {
        Context context = themed(1f);
        GuiAppsSetupGuideFragment guide = guide(AppEdition.TERMUX, context);
        View root = build(guide, context);
        assertEquals(4, progress(root).getChildCount());
        prefs().edit().putString(GuiAppsSetupGuide.KEY_ROUTE, "distro").commit();
        guide.goTo(Step.SOURCE);
        assertEquals(5, progress(root).getChildCount());
    }

    @After public void restoreMarkerChecks() {
        GuiAppsSetupGuide.markerPresent = GuiAppsSetupGuide.MARKER_ON_DISK;
        GuiAppsSetupGuide.rootfsInstalled = GuiAppsSetupGuide.ROOTFS_ON_DISK;
        GuiAppsSetupGuide.fcitx5Installed = GuiAppsSetupGuide.FCITX5_ON_DISK;
    }

    @Test public void aCopiedCommandWithNoRootfsIsNotMigrated() {
        SharedPreferences prefs = prefs();
        prefs.edit().putBoolean(GuiAppsSetupGuide.KEY_COPIED, true)
            .putString(GuiAppsSetupGuide.KEY_ROUTE, "distro").commit();
        GuiAppsSetupGuide.markerPresent = () -> false;
        GuiAppsSetupGuide.fcitx5Installed = () -> true;
        GuiAppsSetupGuide.rootfsInstalled = d -> false;
        assertFalse(GuiAppsSetupGuide.finished(prefs, AppEdition.TERMUX));
    }

    @Test public void theSummaryNamesWhatWasSetUp() {
        Context context = themed(1f);
        SharedPreferences prefs = prefs();
        GuiAppsSetupGuide.markerPresent = () -> false;
        assertEquals("Not set up yet",
            GuiAppsSetupGuide.rowSummary(context, prefs, AppEdition.TERMUX));
        prefs.edit().putBoolean(GuiAppsSetupGuide.KEY_COPIED, true)
            .putString(GuiAppsSetupGuide.KEY_ROUTE, "distro").commit();
        GuiAppsSetupGuide.markerPresent = () -> false;
        GuiAppsSetupGuide.fcitx5Installed = () -> false;
        try {
            assertEquals("Command copied. Run it in a terminal",
                GuiAppsSetupGuide.rowSummary(context, prefs, AppEdition.TERMUX));
            assertFalse(GuiAppsSetupGuide.isSetUp(context));
            GuiAppsSetupGuide.markerPresent = () -> true;
            GuiAppsSetupGuide.fcitx5Installed = () -> true;
            assertTrue(GuiAppsSetupGuide.isSetUp(context));
            GuiAppsSetupGuide.fcitx5Installed = () -> false;
            assertFalse(GuiAppsSetupGuide.isSetUp(context));
        } finally {
            GuiAppsSetupGuide.fcitx5Installed = GuiAppsSetupGuide.FCITX5_ON_DISK;
        }
        assertEquals("Debian with a browser and a file manager",
            GuiAppsSetupGuide.rowSummary(context, prefs, AppEdition.TERMUX));
        GuiAppsSetupGuide.markerPresent = () -> true;
        assertEquals("Debian with a browser and a file manager",
            GuiAppsSetupGuide.rowSummary(context, prefs, AppEdition.TERMUX));
        prefs.edit().putBoolean("gui_apps_starter_browser", false).commit();
        assertEquals("Debian with a file manager",
            GuiAppsSetupGuide.rowSummary(context, prefs, AppEdition.TERMUX));
    }

    // ---- fit -----------------------------------------------------------------------------

    @Test public void everyStepFitsAtFontScale13() {
        assertEveryStepFits(1.3f);
    }

    @Test public void everyStepFitsAtFontScale2() {
        assertEveryStepFits(2.0f);
    }

    private void assertEveryStepFits(float fontScale) {
        Context context = themed(fontScale);
        prefs().edit().putString(GuiAppsSetupGuide.KEY_ROUTE, "distro").commit();
        GuiAppsSetupGuideFragment guide = guide(AppEdition.TERMUX, context);
        View root = build(guide, context);
        List<Step> steps = guide.steps();
        assertEquals(5, steps.size());
        for (Step step : steps) {
            guide.goTo(step);
            layout(root, context);
            String where = step + " at " + fontScale;
            int width = root.getMeasuredWidth();
            int height = root.getMeasuredHeight();

            LinearLayout progress = progress(root);
            assertTrue(where + ": progress inside", progress.getRight() <= width);
            int previousRight = -1;
            for (int i = 0; i < progress.getChildCount(); i++) {
                View pill = progress.getChildAt(i);
                assertTrue(where + ": pill " + i + " starts after the last", pill.getLeft() >= previousRight);
                assertTrue(where + ": pill " + i + " inside the line",
                    pill.getRight() <= progress.getWidth() - progress.getPaddingRight());
                previousRight = pill.getRight();
                // Only the current pill may give up the end of its label; numbers never clip.
                if (pill.getWidth() > 0 && ((TextView) pill).getText().length() <= 2) {
                    assertEquals(where + ": pill " + i + " text", 0,
                        ((TextView) pill).getLayout().getEllipsisCount(0));
                }
            }

            LinearLayout footer = footer(root);
            assertTrue(where + ": footer inside the screen", footer.getBottom() <= height);
            View back = footer.getChildAt(0);
            View next = footer.getChildAt(2);
            assertTrue(where + ": Back inside", back.getLeft() >= 0 && back.getRight() <= width);
            assertTrue(where + ": Next inside", next.getLeft() >= 0 && next.getRight() <= width);
            assertTrue(where + ": Back and Next apart", back.getRight() <= next.getLeft());
            for (View button : new View[]{back, next}) {
                TextView label = (TextView) button;
                assertEquals(where + ": '" + label.getText() + "' on one line", 1,
                    label.getLayout().getLineCount());
                assertEquals(where + ": '" + label.getText() + "' unclipped", 0,
                    label.getLayout().getEllipsisCount(0));
            }
            assertEquals(where + ": last step says Done",
                step == Step.GRAPHICS ? context.getString(R.string.settings_gui_apps_done)
                    : context.getString(R.string.settings_gui_apps_next),
                ((TextView) next).getText().toString());
            // The body scrolls rather than pushing the footer off the screen.
            View scroll = root instanceof ViewGroup ? ((ViewGroup) root).getChildAt(1) : null;
            assertNotNull(scroll);
            assertTrue(where + ": body above the footer", scroll.getBottom() <= footer.getTop());
        }
    }

    @Test public void theRecommendedBadgeWrapsUnderTheTitleAtLargeFont() {
        Context context = themed(2.0f);
        prefs().edit().putString(GuiAppsSetupGuide.KEY_ROUTE, "distro").commit();
        GuiAppsSetupGuideFragment guide = guide(AppEdition.TERMUX, context);
        View root = build(guide, context);
        guide.goTo(Step.DISTRO);
        layout(root, context);
        GuiAppsSetupGuideFragment.FlowRow row = findFlowRow(root);
        assertNotNull(row);
        assertEquals(2, row.getChildCount());
        View title = row.getChildAt(0);
        View badge = row.getChildAt(1);
        assertTrue("badge under the title", badge.getTop() >= title.getBottom()
            || badge.getLeft() >= title.getRight());
        assertTrue("badge inside its card", badge.getRight() <= row.getWidth());
    }

    // ---- helpers -------------------------------------------------------------------------

    private static SharedPreferences prefs() {
        return GuiAppsSetupGuide.preferences(RuntimeEnvironment.getApplication());
    }

    private static Context themed(float fontScale) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        return new ContextThemeWrapper(app.createConfigurationContext(configuration),
            R.style.Theme_TermuxApp_DayNight_NoActionBar);
    }

    private static GuiAppsSetupGuideFragment guide(AppEdition edition, Context context) {
        GuiAppsSetupGuideFragment guide = new GuiAppsSetupGuideFragment();
        Bundle args = new Bundle();
        args.putString(GuiAppsSetupGuideFragment.ARG_EDITION, edition.name());
        guide.setArguments(args);
        return guide;
    }

    private static View build(GuiAppsSetupGuideFragment guide, Context context) {
        View root = guide.onCreateView(LayoutInflater.from(context), null, null);
        assertNotNull(root);
        layout(root, context);
        return root;
    }

    private static void layout(View root, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int w = Math.round(WIDTH_DP * density);
        int h = Math.round(HEIGHT_DP * density);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private static LinearLayout progress(View root) {
        return (LinearLayout) ((ViewGroup) root).getChildAt(0);
    }

    private static LinearLayout footer(View root) {
        return (LinearLayout) ((ViewGroup) root).getChildAt(2);
    }

    private static GuiAppsSetupGuideFragment.FlowRow findFlowRow(View view) {
        if (view instanceof GuiAppsSetupGuideFragment.FlowRow
            && ((GuiAppsSetupGuideFragment.FlowRow) view).getChildCount() == 2) {
            return (GuiAppsSetupGuideFragment.FlowRow) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                GuiAppsSetupGuideFragment.FlowRow found = findFlowRow(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
