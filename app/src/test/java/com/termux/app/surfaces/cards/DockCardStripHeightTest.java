package com.termux.app.surfaces.cards;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.dock.DockStyle;
import com.termux.app.dock.WallStripRowPolicy;
import com.termux.app.place.Element;
import com.termux.app.surfaces.EditorRows.Row;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * The Strip height row (editor-redesign §1: bar Height moves into the cards when Layout mode is
 * retired). It stands under the Dock type tiles, shows with the Strip type alone, and writes the
 * Strip row's height in its own steps and range.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
public class DockCardStripHeightTest {

    private Context mContext;
    private FakeHost mHost;
    private DockCard mCard;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        mContext = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mHost = new FakeHost(mContext);
        mCard = new DockCard(mHost);
    }

    @Test
    public void dockIconPackNamesThePinnedPackAndOpensItsChooser() {
        View row = mCard.view(Row.DOCK_ICON_PACK, 1000);
        assertNotNull(row);
        List<String> words = new java.util.ArrayList<>();
        collectTexts(row, words);
        assertEquals(true, words.contains("Dock icon pack"));
        assertEquals("the current choice", true, words.contains("System"));
        com.google.android.material.button.MaterialButton action = findButton(row);
        assertNotNull(action);
        action.performClick();
        assertEquals(1, mHost.iconPackPicks);
    }

    private static void collectTexts(View view, List<String> out) {
        if (view instanceof TextView) out.add(String.valueOf(((TextView) view).getText()));
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                collectTexts(((ViewGroup) view).getChildAt(i), out);
    }

    @Nullable
    private static com.google.android.material.button.MaterialButton findButton(View view) {
        if (view instanceof com.google.android.material.button.MaterialButton)
            return (com.google.android.material.button.MaterialButton) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                com.google.android.material.button.MaterialButton found =
                    findButton(((ViewGroup) view).getChildAt(i));
                if (found != null) return found;
            }
        return null;
    }

    @Test
    public void appsInTheDockStopsAtTheFitAndSaysSo() {
        mHost.mostFitting = 8;
        View row = mCard.view(Row.DOCK_APP_ICONS, 1000);
        assertNotNull(row);
        com.termux.app.surfaces.rows.StepperRow stepper = null;
        List<String> words = new java.util.ArrayList<>();
        collectTexts(row, words);
        assertEquals(true, words.contains("Apps in the dock"));
        TextView hint = hintOf(row);
        assertEquals("no hint while + still moves", View.GONE, hint.getVisibility());
        List<com.google.android.material.button.MaterialButton> buttons = buttonsOf(row);
        com.google.android.material.button.MaterialButton plus = buttons.get(1);
        plus.performClick();
        assertEquals(8, mHost.appIcons);
        assertEquals("+ is out at the fit", false, plus.isEnabled());
        assertEquals(View.VISIBLE, hint.getVisibility());
        assertEquals("At most 8 fit at size M.", String.valueOf(hint.getText()));
        // A larger icon size: fewer fit, the stored count was re-clamped by the editor.
        mHost.mostFitting = 6;
        mHost.appIcons = DockCard.clampAppIcons(mHost.appIcons, 3, mHost.mostFitting);
        mCard.restate(Row.DOCK_APP_ICONS);
        assertEquals(6, mHost.appIcons);
        assertEquals("At most 6 fit at size M.", String.valueOf(hint.getText()));
        // Back to room for every app: + moves again, the hint goes.
        mHost.mostFitting = 10;
        mCard.restate(Row.DOCK_APP_ICONS);
        assertEquals(true, plus.isEnabled());
        assertEquals(View.GONE, hint.getVisibility());
    }

    @Test
    public void theClampKeepsTheLeastAndTheFit() {
        assertEquals(6, DockCard.clampAppIcons(9, 3, 6));
        assertEquals(5, DockCard.clampAppIcons(5, 3, 6));
        assertEquals("the least wins where not even three fit", 3,
            DockCard.clampAppIcons(5, 3, 2));
        assertEquals(true, DockCard.fitHintShown(6, 6, 10));
        assertEquals(false, DockCard.fitHintShown(5, 6, 10));
        assertEquals("the hard top is no fit", false, DockCard.fitHintShown(10, 10, 10));
    }

    @Nullable
    private static TextView hintOf(View view) {
        if (view instanceof TextView && String.valueOf(((TextView) view).getText()).startsWith("At most"))
            return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            // The hint is the last child of the row's column.
            View last = group.getChildAt(group.getChildCount() - 1);
            if (last instanceof TextView && !(last instanceof com.google.android.material.button.MaterialButton))
                return (TextView) last;
        }
        return null;
    }

    private static List<com.google.android.material.button.MaterialButton> buttonsOf(View view) {
        List<com.google.android.material.button.MaterialButton> out = new java.util.ArrayList<>();
        collectButtons(view, out);
        return out;
    }

    private static void collectButtons(View view,
                                       List<com.google.android.material.button.MaterialButton> out) {
        if (view instanceof com.google.android.material.button.MaterialButton)
            out.add((com.google.android.material.button.MaterialButton) view);
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                collectButtons(((ViewGroup) view).getChildAt(i), out);
    }

    @Test
    public void stripTypeIsTheWallStrip() {
        assertEquals(DockStyle.WALL_STRIP, DockStyle.CHOICES.get(DockCard.STRIP_TYPE));
    }

    @Test
    public void rowHiddenUntilTheStripIsTheType() {
        View type = mCard.view(Row.DOCK_TYPE, 1000);
        assertNotNull(type);
        View strip = stripRow(type);
        assertNotNull("the Dock type section carries the Strip height row", strip);
        assertEquals(View.GONE, strip.getVisibility());

        mHost.type = DockCard.STRIP_TYPE;
        mCard.restate(Row.DOCK_TYPE);
        assertEquals(View.VISIBLE, strip.getVisibility());

        mHost.type = 0;
        mCard.restate(Row.DOCK_TYPE);
        assertEquals(View.GONE, strip.getVisibility());
    }

    @Test
    public void stepsWriteTheStripHeightInItsRange() {
        mHost.type = DockCard.STRIP_TYPE;
        mHost.height = WallStripRowPolicy.DEFAULT_HEIGHT_DP;
        View type = mCard.view(Row.DOCK_TYPE, 1000);
        View strip = stripRow(type);
        assertNotNull(strip);

        View plus = plusOf(strip);
        assertNotNull(plus);
        plus.performClick();
        assertEquals(WallStripRowPolicy.DEFAULT_HEIGHT_DP + WallStripRowPolicy.HEIGHT_STEP_DP,
            mHost.written);

        // An Undo elsewhere restates the row from the store.
        mHost.height = WallStripRowPolicy.MIN_HEIGHT_DP;
        mCard.restate(Row.DOCK_TYPE);
        assertNotNull(findText(strip, mContext.getString(R.string.editor_dock_strip_height_value,
            WallStripRowPolicy.MIN_HEIGHT_DP)));
    }

    // ------------------------------------------------------------------------------ helpers

    /** The stepper row whose title reads Strip height (the title's own line), or null. */
    @Nullable
    private View stripRow(@NonNull View root) {
        TextView title = findText(root, mContext.getString(R.string.editor_dock_strip_height));
        return title != null && title.getParent() instanceof View ? (View) title.getParent() : null;
    }

    @Nullable
    private static TextView findText(@NonNull View view, @NonNull String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText()))
            return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    /** The stepper's plus button: the last button in its line. */
    @Nullable
    private static View plusOf(@NonNull View row) {
        if (!(row instanceof ViewGroup))
            return null;
        View found = null;
        ViewGroup group = (ViewGroup) row;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof com.google.android.material.button.MaterialButton)
                found = group.getChildAt(i);
        }
        return found;
    }

    private static final class FakeHost implements DockCard.Host {
        private final Context mContext;
        int type;
        int height = WallStripRowPolicy.DEFAULT_HEIGHT_DP;
        int written = -1;

        FakeHost(@NonNull Context context) {
            mContext = context;
        }

        @NonNull @Override public Context context() { return mContext; }
        @Override public boolean floating() { return false; }
        @Override public void setFloating(boolean floating) {}
        @NonNull @Override public List<Element> barOrder() { return BarOrder.DEFAULT; }
        @Override public void setBarOrder(@NonNull List<Element> order) {}
        @Override public boolean offers(@NonNull Element bar) { return true; }
        @Override public boolean barShown(@NonNull Element bar) { return true; }
        @Override public void setBarShown(@NonNull Element bar, boolean shown) {}
        @NonNull @Override public BarOrder.Position position(@NonNull Element bar) {
            return BarOrder.positionsFor(bar).get(0);
        }
        @Override public void setPosition(@NonNull Element bar,
                                          @NonNull BarOrder.Position position) {}
        int appIcons = 7;
        int mostFitting = 10;
        @Override public int appIcons() { return appIcons; }
        @Override public int appIconsFrom() { return 3; }
        @Override public int appIconsTo() { return 10; }
        @Override public void setAppIcons(int count) { appIcons = count; }
        @Override public int appIconsMostFitting() { return mostFitting; }
        @NonNull @Override public CharSequence iconSizeName() { return "M"; }
        @Override public boolean mostUsed() { return true; }
        @Override public void setMostUsed(boolean on) {}
        @NonNull @Override public CharSequence iconPack() { return "System"; }
        int iconPackPicks;
        @Override public void pickIconPack() { iconPackPicks++; }
        @Override public boolean azGlass() { return false; }
        @Override public void setAzGlass(boolean on) {}
        @Override public int azShow() { return 0; }
        @Override public void setAzShow(int index) {}
        @Override public void openExtraKeysEditor() {}
        @Override public int dockType() { return type; }
        @Override public void setDockType(int index) { type = index; }
        @Override public boolean option(@NonNull DockCard.Option option) { return false; }
        @Override public void setOption(@NonNull DockCard.Option option, boolean on) {}
        @Override public int stripHeightDp() { return height; }
        @Override public void setStripHeightDp(int dp) {
            written = dp;
            height = dp;
        }
    }
}
