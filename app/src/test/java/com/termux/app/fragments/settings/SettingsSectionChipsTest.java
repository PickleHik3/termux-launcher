package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.termux.KeyboardHardwarePreferencesFragment;
import com.termux.app.fragments.settings.termux.GeneralPreferencesFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;

/**
 * The pinned section-chip row: shown only on pages with three or more sections that have content,
 * filtering at the adapter level (never by calling {@link Preference#setVisible} on the page's own
 * preferences), and falling back to "All" the moment a selected section stops qualifying.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsSectionChipsTest {

    private <F extends MaterialPreferenceFragment> F launch(Class<F> fragmentClass) {
        Application app = RuntimeEnvironment.getApplication();
        Intent intent = new Intent(app, SettingsActivity.class)
            .putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, fragmentClass.getName());
        ActivityController<SettingsActivity> controller =
            Robolectric.buildActivity(SettingsActivity.class, intent).create().start().resume();
        SettingsActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragmentClass.isInstance(fragment));
        return fragmentClass.cast(fragment);
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static ChipGroup chipGroup(MaterialPreferenceFragment fragment) {
        View row = fragment.getSectionChipsRow();
        assertNotNull("chip row should exist even when hidden", row);
        ChipGroup group = row.findViewById(R.id.settings_section_chips_group);
        assertNotNull(group);
        return group;
    }

    private static Chip findChip(ChipGroup group, CharSequence text) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && text.toString().contentEquals(((Chip) child).getText())) {
                return (Chip) child;
            }
        }
        fail("no chip labelled \"" + text + "\"");
        return null;
    }

    @Test
    public void chipsAppearOnAMultiSectionPage() {
        GeneralPreferencesFragment fragment = launch(GeneralPreferencesFragment.class);
        PreferenceScreen screen = fragment.getPreferenceScreen();
        List<PreferenceCategory> sections = SettingsSectionChips.visibleSections(screen);
        assertTrue("fixture page needs 3+ sections for this test to mean anything",
            sections.size() >= 3);

        View row = fragment.getSectionChipsRow();
        assertNotNull(row);
        assertEquals(View.VISIBLE, row.getVisibility());

        ChipGroup group = chipGroup(fragment);
        // One "All" chip plus one per qualifying section.
        assertEquals(sections.size() + 1, group.getChildCount());
        Chip all = findChip(group, fragment.getString(R.string.settings_section_chip_all));
        assertTrue("All is selected by default", all.isChecked());
        for (PreferenceCategory section : sections) {
            findChip(group, section.getTitle());
        }
    }

    @Test
    public void revealScrollsJustEnoughToShowAChipWithItsGutter() {
        // A 400px row scrolled to 0, chips in content coordinates, a 40px gutter.
        assertEquals("a chip already in view does not move the row",
            0, SettingsSectionChips.revealScrollX(0, 400, 100, 200, 40));
        assertEquals("the last chip lands one gutter in from the end",
            240, SettingsSectionChips.revealScrollX(0, 400, 500, 600, 40));
        assertEquals("a chip off the start comes back one gutter in",
            60, SettingsSectionChips.revealScrollX(300, 400, 100, 200, 40));
        assertEquals(0, SettingsSectionChips.revealScrollX(300, 400, 20, 80, 40));
    }

    @Test
    public void theChipRowEndsOneGutterPastItsLastChip() {
        GeneralPreferencesFragment fragment = launch(GeneralPreferencesFragment.class);
        ChipGroup group = chipGroup(fragment);
        View row = fragment.getSectionChipsRow();
        // Narrow enough that the chips run past the edge and the row scrolls.
        row.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, 200, row.getMeasuredHeight());
        assertTrue("the chips overflow the row", group.getWidth() > 200);
        View last = group.getChildAt(group.getChildCount() - 1);
        int gutter = row.getResources().getDimensionPixelSize(R.dimen.settings_gutter);
        assertEquals("scrolled to the end, the last chip keeps a gutter after it",
            last.getRight() + gutter, group.getWidth());
    }

    @Test
    public void chipsAreAbsentOnAShortSectionPage() {
        KeyboardHardwarePreferencesFragment fragment = launch(KeyboardHardwarePreferencesFragment.class);
        PreferenceScreen screen = fragment.getPreferenceScreen();
        assertTrue(SettingsSectionChips.visibleSections(screen).size() < 3);

        View row = fragment.getSectionChipsRow();
        assertNotNull(row);
        assertEquals(View.GONE, row.getVisibility());
    }

    @Test
    public void tappingASectionChipShowsOnlyThatSection() {
        GeneralPreferencesFragment fragment = launch(GeneralPreferencesFragment.class);
        PreferenceScreen screen = fragment.getPreferenceScreen();
        List<PreferenceCategory> sections = SettingsSectionChips.visibleSections(screen);
        SettingsSectionChips.FilteringAdapter adapter =
            (SettingsSectionChips.FilteringAdapter) fragment.getListView().getAdapter();
        assertNotNull(adapter);
        int fullCount = adapter.getItemCount();

        PreferenceCategory target = sections.get(0);
        ChipGroup group = chipGroup(fragment);
        findChip(group, target.getTitle()).performClick();

        int filteredCount = adapter.getItemCount();
        assertTrue("filtering should narrow the list", filteredCount < fullCount);
        for (int i = 0; i < filteredCount; i++) {
            Preference shown = adapter.getItem(i);
            assertEquals(target, SettingsSectionChips.topCategoryOf(shown, screen));
        }
        // A row from a different section must not leak through the filter.
        PreferenceCategory other = sections.get(1);
        for (int i = 0; i < filteredCount; i++) {
            assertFalse(other.equals(SettingsSectionChips.topCategoryOf(adapter.getItem(i), screen)));
        }
    }

    @Test
    public void allRestoresTheWholePageAndReselectingTheSameChipDoesToo() {
        GeneralPreferencesFragment fragment = launch(GeneralPreferencesFragment.class);
        PreferenceScreen screen = fragment.getPreferenceScreen();
        List<PreferenceCategory> sections = SettingsSectionChips.visibleSections(screen);
        SettingsSectionChips.FilteringAdapter adapter =
            (SettingsSectionChips.FilteringAdapter) fragment.getListView().getAdapter();
        int fullCount = adapter.getItemCount();

        ChipGroup group = chipGroup(fragment);
        PreferenceCategory target = sections.get(sections.size() - 1);
        Chip targetChip = findChip(group, target.getTitle());
        targetChip.performClick();
        assertTrue(adapter.getItemCount() < fullCount);

        // Tapping the already-selected chip again is the other way back to "All".
        targetChip.performClick();
        assertEquals(fullCount, adapter.getItemCount());
        assertTrue(findChip(group, fragment.getString(R.string.settings_section_chip_all)).isChecked());

        // Filter again, then use the explicit "All" chip this time.
        findChip(group, target.getTitle()).performClick();
        assertTrue(adapter.getItemCount() < fullCount);
        findChip(group, fragment.getString(R.string.settings_section_chip_all)).performClick();
        assertEquals(fullCount, adapter.getItemCount());
    }

    @Test
    public void aRowThePageHidStaysHiddenUnderEveryFilterIncludingAll() {
        GeneralPreferencesFragment fragment = launch(GeneralPreferencesFragment.class);
        PreferenceScreen screen = fragment.getPreferenceScreen();
        SettingsSectionChips.FilteringAdapter adapter =
            (SettingsSectionChips.FilteringAdapter) fragment.getListView().getAdapter();

        // Hiding every row of the battery section (as a page's own gating logic would) empties
        // it, so this also exercises "the selected section disappeared -> back to All" in the
        // same test.
        Preference clipboardRow = fragment.findPreference("performance_link");
        assertNotNull(clipboardRow);
        PreferenceCategory clipboardSection = SettingsSectionChips.topCategoryOf(clipboardRow, screen);
        assertNotNull(clipboardSection);

        ChipGroup group = chipGroup(fragment);
        findChip(group, clipboardSection.getTitle()).performClick();
        assertEquals(clipboardSection, SettingsSectionChips.topCategoryOf(adapter.getItem(0), screen));

        clipboardRow.setVisible(false);
        idle(); // let PreferenceGroupAdapter's deferred sync run and notify our wrapper.

        // The section has no visible rows, so it no longer qualifies: the filter must have fallen
        // back to "All" on its own.
        List<PreferenceCategory> sectionsNow = SettingsSectionChips.visibleSections(screen);
        assertFalse(sectionsNow.contains(clipboardSection));
        if (SettingsSectionChips.shouldShowChipRow(sectionsNow)) {
            assertTrue(findChip(group, fragment.getString(R.string.settings_section_chip_all)).isChecked());
        } else {
            // Too few sections are left for a chip row: it is hidden and nothing filters.
            assertEquals(0, group.getChildCount());
        }

        for (int i = 0; i < adapter.getItemCount(); i++) {
            assertFalse("the hidden row must not resurface under All",
                clipboardRow.equals(adapter.getItem(i)));
        }
    }
}
