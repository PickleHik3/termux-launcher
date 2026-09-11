package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** The collapsed app list and what the search field does to it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPickerSectionsTest {
    @Test public void listStartsCollapsedAndEachAppOpensAndClosesInPlace() {
        WidgetPickerAdapter adapter = new WidgetPickerAdapter(item -> { });
        List<WidgetAppGroup> catalog = catalog();
        adapter.submit(catalog);
        assertEquals(2, adapter.getItemCount()); // one row per app, no cards
        assertNotNull(adapter.sectionAt(0)); assertNotNull(adapter.sectionAt(1));
        assertNull(adapter.providerAt(0));

        adapter.toggleSection(catalog.get(0)); // Calendar
        assertEquals(4, adapter.getItemCount());
        assertEquals("Agenda", adapter.providerAt(1).label);
        assertEquals("Month", adapter.providerAt(2).label);
        assertNotNull(adapter.sectionAt(3)); // the next app stays where it was

        adapter.toggleSection(catalog.get(1)); // both open at once
        assertEquals(5, adapter.getItemCount());
        adapter.toggleSection(catalog.get(0));
        assertEquals(3, adapter.getItemCount());

        // Closing the picker takes the open state with it.
        adapter.submit(Collections.emptyList());
        adapter.submit(catalog);
        assertEquals(2, adapter.getItemCount());
    }

    @Test public void searchMatchesWidgetAndAppLabelsPastCaseAndAccents() {
        WidgetPickerAdapter adapter = new WidgetPickerAdapter(item -> { });
        adapter.submit(catalog());

        adapter.setQuery("MONTH"); // widget label, wrong case
        assertEquals(2, adapter.getItemCount()); // its app row, opened, plus the card
        assertEquals("Calendar", adapter.sectionAt(0).label);
        assertEquals("Month", adapter.providerAt(1).label);
        assertEquals(1, adapter.sectionAt(0).providerCount); // the row counts what matched

        adapter.setQuery("horloge"); // app label, without its accent
        assertEquals(2, adapter.getItemCount());
        assertEquals("Horlogé", adapter.sectionAt(0).label);
        assertEquals("Uhr", adapter.providerAt(1).label);

        adapter.setQuery("uhr"); // widget label whose app does not match
        assertEquals(2, adapter.getItemCount());
        assertEquals("Horlogé", adapter.sectionAt(0).label);

        adapter.setQuery("nothing here");
        assertEquals(0, adapter.getItemCount());
        assertTrue(adapter.searchFoundNothing());

        adapter.setQuery("");
        assertFalse(adapter.searchFoundNothing());
        assertEquals(2, adapter.getItemCount()); // back to the collapsed list
    }

    @Test public void clearingTheQueryRestoresWhatTheUserHadOpen() {
        WidgetPickerAdapter adapter = new WidgetPickerAdapter(item -> { });
        List<WidgetAppGroup> catalog = catalog();
        adapter.submit(catalog);
        adapter.toggleSection(catalog.get(0));
        assertTrue(adapter.isExpanded(catalog.get(0)));
        adapter.setQuery("uhr");
        assertFalse(adapter.isExpanded(catalog.get(0)));
        adapter.setQuery("");
        assertTrue(adapter.isExpanded(catalog.get(0)));
        assertEquals(4, adapter.getItemCount());
    }

    /** The app rows land before the widgets do, so the count has to come from enumeration. */
    @Test public void appRowsRenderTheirCountBeforeTheirWidgetsArrive() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        WidgetPickerAdapter adapter = new WidgetPickerAdapter(item -> { });
        list.setAdapter(adapter);
        activity.setContentView(list);
        adapter.submit(Collections.singletonList(new WidgetAppGroup(0, "calendar.pkg", "Calendar",
            new ColorDrawable(1), Collections.emptyList(), 2)));
        layout(list);
        RecyclerView.ViewHolder row = list.findViewHolderForAdapterPosition(0);
        assertNotNull(row);
        assertEquals("2 widgets", ((TextView) row.itemView.findViewWithTag("count")).getText()
            .toString());
        assertTrue(row.itemView.isClickable());
        assertEquals("Calendar, 2 widgets, collapsed", row.itemView.getContentDescription());
    }

    /** The whole sheet: what the user types reaches the list, and a dead end says so. */
    @Test public void typingInTheSheetFiltersTheListAndNamesAnEmptyResult() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WidgetPickerSheetView sheet = new WidgetPickerSheetView(activity, item -> { });
        activity.setContentView(sheet);
        sheet.setReducedMotion(true); sheet.open();
        sheet.showLoading();
        sheet.showCatalog(catalog());
        layout(sheet);
        assertEquals(2, sheet.adapter().getItemCount());

        sheet.searchField().setText("agenda");
        assertEquals(2, sheet.adapter().getItemCount()); // the app row it is in, and the card
        assertEquals("Agenda", sheet.adapter().providerAt(1).label);
        assertNull(findDescription(sheet,
            activity.getString(R.string.widget_picker_no_matches)));

        sheet.searchField().setText("zzz");
        assertEquals(0, sheet.adapter().getItemCount());
        assertNotNull(findDescription(sheet,
            activity.getString(R.string.widget_picker_no_matches)));

        sheet.searchField().setText("");
        assertEquals(2, sheet.adapter().getItemCount());
        assertNull(findDescription(sheet,
            activity.getString(R.string.widget_picker_no_matches)));
    }

    private static View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null
            ? "" : view.getContentDescription()) && view.getVisibility() == View.VISIBLE) {
            return view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<WidgetAppGroup> catalog() {
        ArrayList<WidgetAppGroup> groups = new ArrayList<>();
        groups.add(new WidgetAppGroup(0, "calendar.pkg", "Calendar", new ColorDrawable(1),
            Arrays.asList(item("calendar.pkg", "Agenda"), item("calendar.pkg", "Month"))));
        groups.add(new WidgetAppGroup(0, "clock.pkg", "Horlogé", new ColorDrawable(2),
            Collections.singletonList(item("clock.pkg", "Uhr"))));
        return groups;
    }

    private static WidgetProviderItem item(String packageName, String label) {
        AppWidgetProviderInfo info = WidgetTestFixtures.info(false);
        info.provider = new ComponentName(packageName, label);
        return new WidgetProviderItem(0, info, label, 2, 2, 1, 1, true);
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 800, 900);
    }
}
