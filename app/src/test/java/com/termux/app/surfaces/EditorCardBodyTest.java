package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.surfaces.EditorRows.Kind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.rows.EditorGlyphs;
import com.termux.app.surfaces.rows.GlyphNames;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * The Keyboard, Home, Global and Workspace cards drawn with the redesigned rows (editor-redesign
 * §2) on pong's width, at the default text size and at Large (1.3): every row of the card is
 * there, every control stands inside the 16dp gutter with a 48dp target and no word cut, the
 * sections carry their notes, and the keyboard's opacity link stands between its two strips and
 * reports its touch. Native graphics, so text has real metrics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorCardBodyTest {

    private static final EditorCard[] CARDS = {EditorCard.KEYBOARD, EditorCard.HOME,
        EditorCard.APPEARANCE, EditorCard.WORKSPACE};
    private static final float[] SCALES = {1f, 1.3f};

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test
    public void theseCardsAreDrawnWithTheRedesignedRows() {
        for (EditorCard card : EditorCard.values()) {
            boolean mine = false;
            for (EditorCard c : CARDS)
                mine |= c == card;
            if (mine)
                assertTrue(card.name(), CardBody.redesigned(card));
        }
    }

    @Test
    public void everyRowOfTheCardIsThere() {
        for (EditorCard card : CARDS) {
            Fixture f = new Fixture(card, 1f);
            CardBody body = ReflectionHelpers.getField(f.sheet, "mBody");
            assertNotNull(card + " is drawn by the card body", body);
            for (EditorRows.Spec spec : EditorRows.rows(card, new EditorRows.Offers(true, true)))
                assertTrue(card + " holds " + spec.row, body.holds(spec.row));
        }
    }

    @Test
    public void everyControlStandsInsideTheGutterWithATouchTargetAndNoWordCut() {
        for (float scale : SCALES) {
            for (EditorCard card : CARDS) {
                Fixture f = new Fixture(card, scale);
                f.sheet.setDetent(EditorCardSheet.DETENT_FULL, false);
                f.layOut();
                String at = card + " at " + scale;
                int buttons = 0;
                for (MaterialButton button : views(f.sheet.rows(), MaterialButton.class)) {
                    if (!button.isShown())
                        continue;
                    buttons++;
                    String which = at + ": " + label(button);
                    assertTrue(which + " is a 48dp target",
                        button.getHeight() >= f.dp(48) - 1 && button.getWidth() >= f.dp(48) - 1);
                    assertTrue(which + " starts inside the gutter",
                        f.left(button) >= f.dp(16) - 1);
                    assertTrue(which + " ends inside the gutter",
                        f.left(button) + button.getWidth() <= f.width - f.dp(16) + 1);
                    Layout layout = button.getLayout();
                    if (layout != null && layout.getLineCount() > 0)
                        assertEquals(which + " is cut", 0,
                            layout.getEllipsisCount(layout.getLineCount() - 1));
                }
                assertTrue(at + " has controls", buttons > 0);
                for (LegendSlider slider : views(f.sheet.rows(), LegendSlider.class)) {
                    assertTrue(at + ": " + slider.getContentDescription() + " is laid out",
                        slider.getWidth() > 0 && slider.getHeight() > 0);
                    assertTrue(at + ": " + slider.getContentDescription() + " in the gutter",
                        f.left(slider) >= f.dp(16) - 1
                            && f.left(slider) + slider.getWidth() <= f.width - f.dp(16) + 1);
                }
            }
        }
    }

    @Test
    public void theSectionsCarryTheirNamesAndNotes() {
        Fixture home = new Fixture(EditorCard.HOME, 1f);
        List<String> texts = texts(home.sheet.rows());
        for (int res : new int[] {R.string.editor_section_grid, R.string.editor_section_look,
            R.string.editor_section_glass, R.string.editor_section_labels_folders,
            R.string.editor_section_options, R.string.editor_section_more})
            assertTrue("Home shows " + home.themed.getString(res),
                texts.contains(home.themed.getString(res)));
        assertTrue(texts.contains(home.themed.getString(R.string.editor_note_home_only)));
        Fixture global = new Fixture(EditorCard.APPEARANCE, 1f);
        List<String> globalTexts = texts(global.sheet.rows());
        assertTrue(globalTexts.contains(global.themed.getString(R.string.editor_note_both_screens)));
        assertTrue(globalTexts.contains(global.themed.getString(
            R.string.editor_hint_glass_material)));
    }

    @Test
    public void theOpacityLinkStandsBetweenTheTwoOpacitiesAndReportsItsTouch() {
        Fixture f = new Fixture(EditorCard.KEYBOARD, 1f);
        f.layOut();
        MaterialButton link = null;
        for (MaterialButton button : views(f.sheet.rows(), MaterialButton.class)) {
            if (f.themed.getString(R.string.editor_row_opacity_link).contentEquals(
                String.valueOf(button.getContentDescription())))
                link = button;
        }
        assertNotNull("the link is there", link);
        LegendSlider background = null;
        LegendSlider keys = null;
        for (LegendSlider slider : views(f.sheet.rows(), LegendSlider.class)) {
            String name = String.valueOf(slider.getContentDescription());
            if (name.equals(f.themed.getString(R.string.editor_row_background_opacity)))
                background = slider;
            if (name.equals(f.themed.getString(R.string.appearance_row_key_opacity)))
                keys = slider;
        }
        assertNotNull(background);
        assertNotNull(keys);
        assertTrue("the link after Background opacity", f.left(link) >= f.left(background));
        assertTrue("the link before Key opacity", f.left(link) + link.getWidth()
            <= f.left(keys) + 1);
        assertFalse(link.isChecked());
        link.performClick();
        assertEquals(Integer.valueOf(1), f.model.values.get(Row.OPACITY_LINK));
    }

    @Test
    public void theChoiceRowsShowTheirGlyphsAndNumeralsTheirOwnFont() {
        Fixture f = new Fixture(EditorCard.HOME, 1f);
        List<String> names = new ArrayList<>();
        for (MaterialButton button : views(f.sheet.rows(), MaterialButton.class)) {
            CharSequence name = GlyphNames.of(button);
            if (name != null)
                names.add(name.toString());
            if (f.themed.getString(R.string.appearance_numerals_mono).contentEquals(
                button.getText()))
                assertEquals(Typeface.MONOSPACE, button.getTypeface());
        }
        assertTrue(names.contains(f.themed.getString(R.string.editor_home_icon_default)));
        assertTrue(names.contains(f.themed.getString(R.string.appearance_folders_roomy)));
    }

    // ------------------------------------------------------------------------------- the drive

    @NonNull
    private static <T extends View> List<T> views(@NonNull View view, @NonNull Class<T> type) {
        List<T> out = new ArrayList<>();
        if (type.isInstance(view))
            out.add(type.cast(view));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                out.addAll(views(group.getChildAt(i), type));
        }
        return out;
    }

    @NonNull
    private static List<String> texts(@NonNull View root) {
        List<String> out = new ArrayList<>();
        for (TextView text : views(root, TextView.class))
            out.add(String.valueOf(text.getText()));
        return out;
    }

    @NonNull
    private static String label(@NonNull MaterialButton button) {
        CharSequence text = button.getText();
        return text != null && text.length() > 0 ? text.toString()
            : String.valueOf(button.getContentDescription());
    }

    private static final class Fixture {
        final Context themed;
        final FrameLayout parent;
        final EditorCardSheet sheet;
        final FakeModel model;
        final int width;
        private final int mHeight;

        Fixture(@NonNull EditorCard card, float fontScale) {
            RuntimeEnvironment.setFontScale(fontScale);
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            themed = new ContextThemeWrapper(activity,
                R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            float density = themed.getResources().getDisplayMetrics().density;
            width = Math.round(411 * density);
            mHeight = Math.round(914 * density);
            parent = new FrameLayout(themed);
            activity.setContentView(parent, new ViewGroup.LayoutParams(width, mHeight));
            model = new FakeModel(themed, card);
            sheet = new EditorCardSheet(themed, model, new EditorCardSheet.Listener() {
                @Override public void onClose() {}
                @Override public void onReset() {}
                @Override public void onSheetMoved(int detent, float topPx) {}
            });
            int navPx = Math.round(24 * density);
            int full = Math.round(mHeight * 0.55f);
            sheet.attach(parent);
            sheet.setGeometry(full, full, navPx);
            sheet.show(card, EditorRows.rows(card, new EditorRows.Offers(true, true)), "Reset",
                false);
            layOut();
        }

        int dp(float value) {
            return Math.round(value * themed.getResources().getDisplayMetrics().density);
        }

        int left(@NonNull View view) {
            int[] at = new int[2];
            view.getLocationInWindow(at);
            int[] parentAt = new int[2];
            parent.getLocationInWindow(parentAt);
            return at[0] - parentAt[0];
        }

        void layOut() {
            parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(mHeight, View.MeasureSpec.EXACTLY));
            parent.layout(0, 0, width, mHeight);
        }
    }

    /** Values the cards would show on a fresh phone; touches are recorded by row. */
    private static final class FakeModel implements EditorCardSheet.Model {
        private final Context mContext;
        private final EditorCard mCard;
        final java.util.Map<Row, Integer> values = new java.util.EnumMap<>(Row.class);

        FakeModel(@NonNull Context context, @NonNull EditorCard card) {
            mContext = context;
            mCard = card;
        }

        @Override public int value(@NonNull Row row) {
            Integer held = values.get(row);
            if (held != null)
                return held;
            switch (row.kind) {
                case STRIP: return 50;
                case STEPPER: return 4;
                case SWITCH: return 1;
                default: return row == Row.LOOK ? AppearanceLooks.CUSTOM_STOP : 0;
            }
        }

        @Override public int from(@NonNull Row row) {
            return row.kind == Kind.STEPPER ? 2 : 0;
        }

        @Override public int to(@NonNull Row row) {
            return row.kind == Kind.STEPPER ? 12 : 100;
        }

        @Override public int step(@NonNull Row row) {
            return 1;
        }

        @NonNull @Override public String readout(@NonNull Row row, int value) {
            if (row.kind == Kind.ACTION)
                return mContext.getString(R.string.appearance_match_copy);
            return row.kind == Kind.STEPPER ? String.valueOf(value)
                : mContext.getString(R.string.appearance_readout_percent, value);
        }

        @Override public boolean ownValue(@NonNull Row row) {
            return false;
        }

        @Override public boolean enabled(@NonNull Row row) {
            return true;
        }

        @Nullable @Override public CharSequence summary(@NonNull Row row) {
            switch (row) {
                case SECTION_LOOK: return mContext.getString(mCard == EditorCard.HOME
                    ? R.string.editor_note_home_only : R.string.editor_note_workspace_only);
                case SECTION_GLASS: return mCard == EditorCard.HOME
                    ? mContext.getString(R.string.editor_note_home_glass) : null;
                case SECTION_GLASS_MATERIAL:
                    return mContext.getString(R.string.editor_note_both_screens);
                case GLASS_WIDGETS: return mContext.getString(R.string.appearance_glass_widgets_note);
                default: return null;
            }
        }

        @NonNull @Override public String[] choices(@NonNull Row row) {
            switch (row) {
                case CONTRAST: return strings(R.string.appearance_contrast_softer,
                    R.string.appearance_contrast_default, R.string.appearance_contrast_harder);
                case LABEL_SIZE: return strings(R.string.appearance_size_small,
                    R.string.appearance_size_medium, R.string.appearance_size_large);
                case HOME_ICON_SIZE: return strings(R.string.editor_home_icon_small,
                    R.string.editor_home_icon_default, R.string.editor_home_icon_large);
                case FOLDERS: return strings(R.string.appearance_folders_compact,
                    R.string.appearance_folders_default, R.string.appearance_folders_roomy);
                case NUMERALS: return strings(R.string.appearance_numerals_sans,
                    R.string.appearance_numerals_mono);
                case FORM: return strings(R.string.appearance_form_docked,
                    R.string.appearance_form_floating, R.string.appearance_form_split);
                case THEME: return strings(R.string.appearance_theme_system,
                    R.string.appearance_theme_light, R.string.appearance_theme_dark);
                case TINT_HUE:
                    return mContext.getResources().getStringArray(
                        R.array.appearance_editor_tint_hue_entries);
                case LOOK: return strings(R.string.termux_surface_preset_minimal,
                    R.string.termux_surface_preset_frost, R.string.termux_surface_preset_stock,
                    R.string.termux_surface_preset_solid, R.string.termux_surface_preset_custom);
                default: return new String[0];
            }
        }

        @Nullable @Override public int[] chipIcons(@NonNull Row row) {
            switch (row) {
                case FORM: return new int[] {EditorGlyphs.KB_DOCKED, EditorGlyphs.KB_FLOATING,
                    EditorGlyphs.KB_SPLIT};
                case LABEL_SIZE: return new int[] {EditorGlyphs.LABEL_SMALL,
                    EditorGlyphs.LABEL_MEDIUM, EditorGlyphs.LABEL_LARGE};
                case HOME_ICON_SIZE: return new int[] {EditorGlyphs.ICON_SMALL,
                    EditorGlyphs.ICON_DEFAULT, EditorGlyphs.ICON_LARGE};
                case FOLDERS: return new int[] {EditorGlyphs.FOLDER_COMPACT,
                    EditorGlyphs.FOLDER_DEFAULT, EditorGlyphs.FOLDER_ROOMY};
                case THEME: return new int[] {R.drawable.ic_ed_theme_system,
                    R.drawable.ic_ed_theme_light, R.drawable.ic_ms_dark_mode};
                default: return null;
            }
        }

        @Nullable @Override public int[] swatches(@NonNull Row row) {
            return row == Row.TINT_HUE ? new int[] {0xFF4A5A95, 0xFF5B5D72, 0xFF77536D} : null;
        }

        @Nullable @Override public CharSequence name(@NonNull Row row) {
            return row == Row.OPACITY && mCard == EditorCard.KEYBOARD
                ? mContext.getString(R.string.editor_row_background_opacity) : null;
        }

        @Nullable @Override public Typeface choiceTypeface(@NonNull Row row, int index) {
            return row == Row.NUMERALS && index == 1 ? Typeface.MONOSPACE : null;
        }

        @Nullable @Override public View expanderContent(@NonNull Row row) {
            TextView content = new TextView(mContext);
            content.setText("Packs");
            return content;
        }

        @Override public void onValue(@NonNull Row row, int value, boolean dragging) {
            values.put(row, value);
        }

        @Override public void onReleased(@NonNull Row row) {}

        @Override public void onAction(@NonNull Row row, @NonNull View anchor) {}

        @NonNull
        private String[] strings(int... res) {
            String[] out = new String[res.length];
            for (int i = 0; i < res.length; i++)
                out[i] = mContext.getString(res[i]);
            return out;
        }
    }
}
