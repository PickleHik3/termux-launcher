package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.SettingsRowGroups.Kind;
import com.termux.app.fragments.settings.SettingsRowGroups.Shape;
import com.termux.app.fragments.settings.termux.GeneralPreferencesFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

/**
 * The Material 3 settings rows: each section's rows share one rounded tonal card (grouping), the
 * rows carry a leading icon, and segmented controls are filled.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsMaterialRowsTest {

    // ---- Grouping policy ----------------------------------------------------------------------

    private static final class FakeRows implements SettingsRowGroups.Rows {
        private final Kind[] kinds;
        private final Object[] groups;

        FakeRows(Kind[] kinds, Object[] groups) {
            this.kinds = kinds;
            this.groups = groups;
        }

        @Override public int count() { return kinds.length; }
        @NonNull @Override public Kind kindAt(int position) { return kinds[position]; }
        @Nullable @Override public Object groupAt(int position) { return groups[position]; }
    }

    @Test public void rowsOfOneSectionShareACardThatHeadersAndCardsEnd() {
        Object a = "a", b = "b", c = "c", page = "page";
        FakeRows rows = new FakeRows(
            new Kind[]{Kind.ROW, Kind.HEADER, Kind.ROW, Kind.ROW, Kind.ROW, Kind.HEADER, Kind.ROW,
                Kind.STANDALONE, Kind.ROW, Kind.ROW, Kind.ROW},
            new Object[]{page, page, a, a, a, page, b, b, b, page, c});
        Shape[] expected = {Shape.SOLO, Shape.NONE, Shape.FIRST, Shape.MIDDLE, Shape.LAST, Shape.NONE,
            Shape.SOLO, Shape.NONE, Shape.SOLO, Shape.SOLO, Shape.SOLO};
        for (int i = 0; i < expected.length; i++) {
            assertEquals("position " + i, expected[i], SettingsRowGroups.shapeAt(rows, i));
        }
        assertEquals(Shape.NONE, SettingsRowGroups.shapeAt(rows, -1));
        assertEquals(Shape.NONE, SettingsRowGroups.shapeAt(rows, expected.length));
    }

    @Test public void onlyAGroupWithNoHeaderAboveItGetsLeadingSpace() {
        FakeRows rows = new FakeRows(
            new Kind[]{Kind.ROW, Kind.HEADER, Kind.ROW, Kind.STANDALONE, Kind.ROW},
            new Object[]{"page", "page", "a", "page", "page"});
        assertTrue(SettingsRowGroups.needsLeadingSpace(rows, 0));
        assertFalse(SettingsRowGroups.needsLeadingSpace(rows, 2));
        assertTrue(SettingsRowGroups.needsLeadingSpace(rows, 4));
    }

    @Test public void shapesKnowWhichCornersAreRounded() {
        assertTrue(Shape.SOLO.opens() && Shape.SOLO.closes());
        assertTrue(Shape.FIRST.opens() && !Shape.FIRST.closes());
        assertTrue(!Shape.LAST.opens() && Shape.LAST.closes());
        assertTrue(!Shape.MIDDLE.opens() && !Shape.MIDDLE.closes());
        assertFalse(Shape.NONE.grouped());
    }

    // ---- Grouping on a real page --------------------------------------------------------------

    private static <F extends MaterialPreferenceFragment> F launch(Class<F> fragmentClass) {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, fragmentClass.getName());
        SettingsActivity activity =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume().get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragmentClass.isInstance(fragment));
        return fragmentClass.cast(fragment);
    }

    private static RecyclerView laidOutList(MaterialPreferenceFragment fragment) {
        RecyclerView list = fragment.getListView();
        assertNotNull(list);
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(6000, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 6000);
        return list;
    }

    @Test public void aPageInsetsItsRowsIntoCardsAndClipsTheOuterCorners() {
        GeneralPreferencesFragment fragment = launch(GeneralPreferencesFragment.class);
        RecyclerView list = laidOutList(fragment);
        RecyclerView.LayoutManager layout = list.getLayoutManager();
        assertNotNull(layout);
        int gutter = list.getResources().getDimensionPixelSize(R.dimen.settings_gutter);
        int hairline = Math.max(1, Math.round(SettingsGroupDecoration.HAIRLINE_DP
            * list.getResources().getDisplayMetrics().density));

        boolean sawDecoration = false;
        for (int i = 0; i < list.getItemDecorationCount(); i++) {
            sawDecoration |= list.getItemDecorationAt(i) instanceof SettingsGroupDecoration;
        }
        assertTrue("the page's list carries the group decoration", sawDecoration);

        RecyclerView.Adapter<?> adapter = list.getAdapter();
        int grouped = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            int position = list.getChildAdapterPosition(child);
            Preference preference = SettingsGroupDecoration.itemAt(adapter, position);
            assertNotNull(preference);
            Kind kind = SettingsRowGroups.kindOf(preference);
            if (kind != Kind.ROW) {
                assertEquals(preference.getKey() + " sits outside any card", 0, layout.getLeftDecorationWidth(child));
                assertFalse(child.getClipToOutline());
                continue;
            }
            grouped++;
            assertEquals(preference.getKey() + " is inset into its card", gutter, layout.getLeftDecorationWidth(child));
            assertEquals(gutter, layout.getRightDecorationWidth(child));
            Preference before = SettingsGroupDecoration.itemAt(adapter, position - 1);
            boolean opens = before == null || SettingsRowGroups.kindOf(before) != Kind.ROW
                || before.getParent() != preference.getParent();
            if (opens) {
                assertTrue(preference.getKey() + " rounds its top corners", child.getClipToOutline());
                assertTrue(child.getOutlineProvider() instanceof SettingsGroupDecoration.GroupOutline);
            } else {
                assertEquals(preference.getKey() + " leaves room for the hairline", hairline,
                    layout.getTopDecorationHeight(child));
            }
        }
        assertTrue("General has grouped rows", grouped > 3);
    }

    @Test public void aRowJustUnderAHeaderOpensACardWithNoExtraSpace() {
        GeneralPreferencesFragment fragment = launch(GeneralPreferencesFragment.class);
        RecyclerView list = laidOutList(fragment);
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        assertTrue(adapter instanceof PreferenceGroup.PreferencePositionCallback);
        int position = ((PreferenceGroup.PreferencePositionCallback) adapter)
            .getPreferenceAdapterPosition("animation_speed");
        assertTrue(position > 0);
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        assertNotNull(holder);
        assertEquals(0, list.getLayoutManager().getTopDecorationHeight(holder.itemView));
        assertTrue(holder.itemView.getClipToOutline());
    }

    // ---- Leading icons ------------------------------------------------------------------------

    private static void assertEveryRowHasAnIcon(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            if (preference instanceof PreferenceGroup) {
                assertEveryRowHasAnIcon((PreferenceGroup) preference);
            } else {
                assertNotNull(preference.getKey() + " leads with an icon", preference.getIcon());
            }
        }
    }

    @Test public void everyRowOnAPageLeadsWithAnIcon() {
        GeneralPreferencesFragment fragment = launch(GeneralPreferencesFragment.class);
        assertEveryRowHasAnIcon(fragment.getPreferenceScreen());
    }

    @Test public void aRowKeepsTheIconItDeclares() {
        Preference row = new Preference(RuntimeEnvironment.getApplication());
        row.setKey("app_launcher_set_home");
        ColorDrawable own = new ColorDrawable(Color.RED);
        row.setIcon(own);
        SettingsLayoutUtils.applyRowLayout(row);
        assertSame(own, row.getIcon());

        Preference plain = new Preference(RuntimeEnvironment.getApplication());
        plain.setKey("app_launcher_set_home");
        SettingsLayoutUtils.applyRowLayout(plain);
        assertNotNull(plain.getIcon());
        assertEquals(R.drawable.ic_ms_home, SettingsRowIcons.forPreference(plain));
    }

    @Test public void aRowWithNoKeyFallsBackToThePageItOpens() {
        Preference row = new Preference(RuntimeEnvironment.getApplication());
        row.setFragment("com.termux.app.fragments.settings.termux_api.DebuggingPreferencesFragment");
        assertEquals(R.drawable.ic_ms_bug_report, SettingsRowIcons.forPreference(row));
        assertEquals(0, SettingsRowIcons.forPreference(new Preference(RuntimeEnvironment.getApplication())));
    }

    // ---- Filled segments ----------------------------------------------------------------------

    private static final int[] CHECKED = {android.R.attr.state_enabled, android.R.attr.state_checked};
    private static final int[] UNCHECKED = {android.R.attr.state_enabled};

    private static Context themed(float fontScale) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        return new ContextThemeWrapper(app.createConfigurationContext(configuration),
            R.style.Theme_TermuxApp_DayNight_NoActionBar);
    }

    private static int color(Context context, int attr) {
        return MaterialColors.getColor(context, attr, "settings colour");
    }

    @Test public void segmentsAreFilledWithNoOutline() {
        Context context = themed(1f);
        View row = LayoutInflater.from(context).inflate(R.layout.preference_segmented_pill,
            new FrameLayout(context), false);
        MaterialButtonToggleGroup group = row.findViewById(R.id.segmented_pill_track);
        assertTrue("neighbouring tonal segments keep a gap", group.getSpacing() > 0);
        MaterialButton segment = row.findViewById(R.id.segmented_pill_default);
        assertEquals(0, segment.getStrokeWidth());
        assertEquals(0, segment.getMinWidth());
        ColorStateList container = segment.getBackgroundTintList();
        assertNotNull(container);
        assertEquals(color(context, com.google.android.material.R.attr.colorPrimaryContainer),
            container.getColorForState(CHECKED, 0));
        assertEquals(color(context, com.google.android.material.R.attr.colorSurfaceContainerHighest),
            container.getColorForState(UNCHECKED, 0));
        assertEquals(color(context, com.google.android.material.R.attr.colorOnPrimaryContainer),
            segment.getTextColors().getColorForState(CHECKED, 0));
    }

    @Test public void theSectionFilterChipsAreFilledSegments() {
        GeneralPreferencesFragment fragment = launch(GeneralPreferencesFragment.class);
        View chipsRow = fragment.getSectionChipsRow();
        assertNotNull(chipsRow);
        ChipGroup chips = chipsRow.findViewById(R.id.settings_section_chips_group);
        assertTrue(chips.getChildCount() > 0);
        Chip chip = (Chip) chips.getChildAt(0);
        assertEquals(0f, chip.getChipStrokeWidth(), 0f);
        assertFalse(chip.isCheckedIconVisible());
        ColorStateList background = chip.getChipBackgroundColor();
        assertNotNull(background);
        assertEquals(color(chip.getContext(), com.google.android.material.R.attr.colorPrimaryContainer),
            background.getColorForState(CHECKED, 0));
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void anIconRowHoldsItsSlotAndGrowsWithLargeText() {
        Context context = themed(1.3f);
        float density = context.getResources().getDisplayMetrics().density;
        ViewGroup row = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.preference_settings_row,
            new FrameLayout(context), false);
        ImageView icon = row.findViewById(android.R.id.icon);
        icon.setImageResource(R.drawable.ic_ms_history);
        icon.setVisibility(View.VISIBLE);
        TextView title = row.findViewById(android.R.id.title);
        TextView summary = row.findViewById(android.R.id.summary);
        title.setText("Show in Recents when not the default launcher");
        summary.setText("Keep this app in the Recents screen while another launcher is home");
        // A 380dp phone, less the two card gutters.
        int width = Math.round((380 - 2 * 16) * density);
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, width, row.getMeasuredHeight());

        assertEquals(Math.round(24 * density), icon.getWidth());
        View text = (View) title.getParent();
        assertTrue("the title starts after the icon slot", text.getLeft() >= icon.getRight());
        int textHeight = title.getHeight() + summary.getHeight()
            + row.getPaddingTop() + row.getPaddingBottom();
        assertTrue("the row grows with its text instead of clipping it",
            row.getMeasuredHeight() >= textHeight);
        assertTrue(row.getMeasuredHeight() >= Math.round(56 * density));
    }
}
