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
import com.termux.app.fragments.settings.termux.StatusBarPreferencesFragment;
import com.termux.app.fragments.settings.termux.TerminalPreferencesFragment;

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
        TerminalPreferencesFragment fragment = launch(TerminalPreferencesFragment.class);
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
    public void chipsAreAbsentOnASingleSectionPage() {
        StatusBarPreferencesFragment fragment = launch(StatusBarPreferencesFragment.class);
        PreferenceScreen screen = fragment.getPreferenceScreen();
        assertTrue(SettingsSectionChips.visibleSections(screen).size() < 3);

        View row = fragment.getSectionChipsRow();
        assertNotNull(row);
        assertEquals(View.GONE, row.getVisibility());
    }

    @Test
    public void tappingASectionChipShowsOnlyThatSection() {
        TerminalPreferencesFragment fragment = launch(TerminalPreferencesFragment.class);
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
        TerminalPreferencesFragment fragment = launch(TerminalPreferencesFragment.class);
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
        TerminalPreferencesFragment fragment = launch(TerminalPreferencesFragment.class);
        PreferenceScreen screen = fragment.getPreferenceScreen();
        SettingsSectionChips.FilteringAdapter adapter =
            (SettingsSectionChips.FilteringAdapter) fragment.getListView().getAdapter();

        // "Lazy mode" carries a single row; hiding it (as a page's own gating logic would) empties
        // its whole section, so this also exercises "the selected section disappeared -> back to
        // All" in the same test.
        Preference lazyMode = fragment.findPreference("lazy_mode");
        assertNotNull(lazyMode);
        PreferenceCategory lazySection = SettingsSectionChips.topCategoryOf(lazyMode, screen);
        assertNotNull(lazySection);

        ChipGroup group = chipGroup(fragment);
        findChip(group, lazySection.getTitle()).performClick();
        assertEquals(lazySection, SettingsSectionChips.topCategoryOf(adapter.getItem(0), screen));

        lazyMode.setVisible(false);
        idle(); // let PreferenceGroupAdapter's deferred sync run and notify our wrapper.

        // The section had only this row, so it no longer qualifies: the filter must have fallen
        // back to "All" on its own.
        List<PreferenceCategory> sectionsNow = SettingsSectionChips.visibleSections(screen);
        assertFalse(sectionsNow.contains(lazySection));
        assertTrue(findChip(group, fragment.getString(R.string.settings_section_chip_all)).isChecked());

        for (int i = 0; i < adapter.getItemCount(); i++) {
            assertFalse("the hidden row must not resurface under All",
                lazyMode.equals(adapter.getItem(i)));
        }
    }
}
