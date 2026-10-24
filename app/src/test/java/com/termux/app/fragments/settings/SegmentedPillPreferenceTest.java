package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Looper;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;

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
 * The two ways a page can hand a pill its segments without a per-row dialog: straight from Java
 * (an existing {@code ListPreference}-style entries array), and straight from XML
 * ({@code app:entries}/{@code app:entryValues}, the same attributes a {@code ListPreference}
 * would use) — the path that lets most of the Settings pages convert without any fragment code.
 * And the fit rule: a row stacks only when a label cannot fit its segment on one line.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SegmentedPillPreferenceTest {

    @Test
    public void setSegmentsWithCharSequenceLabelsConfiguresTheSameAsResourceIds() {
        Application app = RuntimeEnvironment.getApplication();
        SegmentedPillPreference pill = new SegmentedPillPreference(app);
        pill.setSegments(
            new String[]{"never", "cursor", "always"},
            new CharSequence[]{"Never", "Cursor", "Always"});
        assertEquals(3, pill.segmentCount());
        // No key set, so nothing is persisted yet: the first value is the fallback.
        assertEquals("never", pill.getValue());
        pill.setValue("cursor");
        assertEquals("cursor", pill.getValue());
    }

    @Test
    public void xmlEntriesAndEntryValuesConfigureAPillWithoutAnyFragmentCode() {
        // termux_style_preferences.xml's theme_mode row declares app:entries/app:entryValues and
        // nothing else — the fragment never calls setSegments for it.
        Application app = RuntimeEnvironment.getApplication();
        PreferenceManager manager = new PreferenceManager(app);
        PreferenceScreen screen = manager.inflateFromResource(
            app, R.xml.termux_style_preferences, null);
        androidx.preference.Preference found = screen.findPreference("theme_mode");
        assertNotNull(found);
        assertTrue(found instanceof SegmentedPillPreference);
        SegmentedPillPreference pill = (SegmentedPillPreference) found;
        assertEquals(3, pill.segmentCount());
    }

    @Test
    public void selectedSegmentFollowsTheValueAndNoneLightsNothing() {
        Application app = RuntimeEnvironment.getApplication();
        SegmentedPillPreference pill = new SegmentedPillPreference(app);
        pill.setSegments(
            new String[]{"a", "b", "c", "d"},
            new CharSequence[]{"A", "B", "C", "D"});
        assertEquals(4, pill.segmentCount());
        assertEquals(0, pill.selectedSegment());
        pill.setValue("c");
        assertEquals(2, pill.selectedSegment());
        pill.setValue(SegmentedPillPreference.VALUE_NONE);
        assertEquals(-1, pill.selectedSegment());
        // An unknown value falls back to the first segment.
        pill.setValue("zzz");
        assertEquals(0, pill.selectedSegment());
    }

    // ---- Fit: one row while every label fits its segment ---------------------------------------
    // The rows are bound, attached to a window (so the check mark resolves to a real compound
    // drawable, as on a phone) and laid out at a 411dp phone's width less the card insets: the
    // emulator where Drawer layout stacked at font scale 1.0.

    private static final String PHONE = "w411dp-h914dp-420dpi";
    private static final String[] DRAWER = {"Vertical", "Pages", "Categories"};
    private static final String[] TOUCH = {"Trackpad", "Touch", "Direct"};

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    public void drawerLayoutStaysOneRowAtNormalText() {
        assertOneRow(bind(1f, DRAWER));
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    public void drawerLayoutStaysOneRowAtLargeText() {
        assertOneRow(bind(1.3f, DRAWER));
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    public void touchModeStaysOneRowAtNormalText() {
        assertOneRow(bind(1f, TOUCH));
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    public void touchModeStaysOneRowAtLargeText() {
        assertOneRow(bind(1.3f, TOUCH));
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    public void aLabelTooLongForItsSegmentStacks() {
        MaterialButtonToggleGroup group =
            bind(1f, "Off", "Follow the system theme every evening", "On");
        assertEquals(LinearLayout.VERTICAL, group.getOrientation());
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(qualifiers = PHONE)
    public void onlyTheSelectedSegmentCarriesTheCheck() {
        MaterialButtonToggleGroup group = bind(1f, DRAWER);
        assertNotNull("the selected segment shows its check", button(group, 0).getIcon());
        assertNull(button(group, 1).getIcon());
        assertNull(button(group, 2).getIcon());
    }

    private static void assertOneRow(MaterialButtonToggleGroup group) {
        assertEquals(LinearLayout.HORIZONTAL, group.getOrientation());
        for (int i = 0; i < 3; i++) {
            Layout layout = button(group, i).getLayout();
            assertNotNull(layout);
            assertEquals("segment " + i + " label on one line", 1, layout.getLineCount());
            assertEquals(0, layout.getEllipsisCount(0));
        }
    }

    private static MaterialButton button(MaterialButtonToggleGroup group, int index) {
        return (MaterialButton) group.getChildAt(index);
    }

    private static Context themed(float fontScale) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        return new ContextThemeWrapper(app.createConfigurationContext(configuration),
            R.style.Theme_TermuxApp_DayNight_NoActionBar);
    }

    private static MaterialButtonToggleGroup bind(float fontScale, String... labels) {
        Context context = themed(fontScale);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout window = new FrameLayout(activity);
        activity.setContentView(window);

        SegmentedPillPreference pill = new SegmentedPillPreference(context);
        pill.setTitle("Grid");
        String[] values = new String[labels.length];
        for (int i = 0; i < labels.length; i++) values[i] = "v" + i;
        pill.setSegments(values, labels);

        View row = LayoutInflater.from(context).inflate(R.layout.preference_segmented_pill, window, false);
        float density = context.getResources().getDisplayMetrics().density;
        // The card sits one gutter in from each screen edge.
        int width = Math.round((411 - 2 * 16) * density);
        window.addView(row, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        pill.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));
        // Layout, then the posted fit check, then whatever it changed, twice over.
        for (int pass = 0; pass < 3; pass++) {
            layout(row, width);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        layout(row, width);
        return row.findViewById(R.id.segmented_pill_track);
    }

    private static void layout(View row, int width) {
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, width, row.getMeasuredHeight());
    }
}
