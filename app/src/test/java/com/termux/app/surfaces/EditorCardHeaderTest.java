package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.surfaces.EditorRows.Row;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The card header (appearance-cards overhaul §1): main cards wear the Customization | Home |
 * Workspace pill and the phone glyph; element cards ←, the name and the Show switch where the
 * object can be hidden (Status bar, Dock, Keyboard). Both end in ⋮, whose Reset asks nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorCardHeaderTest {

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test
    public void eachCardWearsItsKindOfHeader() {
        for (float scale : new float[] {1f, 1.3f}) {
            for (EditorCard card : EditorCard.values()) {
                Fixture f = new Fixture(card, scale);
                String at = card + " at " + scale;
                boolean main = EditorCardSheet.isMainCard(card);
                // Main cards: the Customization | Home | Workspace pill and the phone glyph.
                assertEquals(at + ": the pill", main, f.sheet.mainPill().getVisibility() == View.VISIBLE);
                assertEquals(at + ": the phone glyph", main, f.sheet.phoneGlyph().getVisibility() == View.VISIBLE);
                assertFalse(at + ": the glyph is no button", f.sheet.phoneGlyph().isClickable());
                // Element cards: back, name, Show where the object can be hidden.
                assertEquals(at + ": back", !main, f.sheet.backButton().getVisibility() == View.VISIBLE);
                assertEquals(at + ": the name", !main, f.sheet.titleView().getVisibility() == View.VISIBLE);
                assertEquals(at + ": Show", !main && card.hasEye(), f.sheet.eyeButton().getVisibility() == View.VISIBLE);
                assertTrue(at + ": the More menu", f.sheet.resetButton().getVisibility() == View.VISIBLE);
                assertEquals(at, "More options",
                    String.valueOf(f.sheet.resetButton().getContentDescription()));
                View last = f.sheet.resetButton();
                View before = main ? f.sheet.phoneGlyph()
                    : card.hasEye() ? f.sheet.eyeButton() : f.sheet.titleView();
                assertTrue(at + ": nothing runs under the More button",
                    f.left(before) + before.getWidth() <= f.left(last) + 1);
                if (main) {
                    int checked = f.sheet.mainPill().getCheckedButtonId();
                    int index = java.util.Arrays.asList(EditorCardSheet.MAIN_CARDS).indexOf(card);
                    assertEquals(at + ": its own part chosen",
                        f.sheet.mainPill().getChildAt(index).getId(), checked);
                }
            }
        }
    }

    @Test
    public void thePillOpensTheOtherMainCard() {
        Fixture f = new Fixture(EditorCard.HOME, 1f);
        f.sheet.mainPill().getChildAt(2).performClick();
        assertEquals(EditorCard.WORKSPACE, f.opened);
    }

    @Test
    public void backAsksTheControllerOnAnElementCard() {
        Fixture f = new Fixture(EditorCard.DOCK, 1f);
        f.sheet.backButton().performClick();
        assertEquals(1, f.backs);
    }

    @Test
    public void theMoreMenuHoldsResetWhichAsksNothing() {
        Fixture f = new Fixture(EditorCard.DOCK, 1f);
        f.sheet.resetButton().performClick();
        androidx.appcompat.widget.PopupMenu menu = f.sheet.headerMenu();
        assertTrue("the menu opened", menu != null);
        assertEquals("Reset dock", String.valueOf(menu.getMenu().getItem(0).getTitle()));
        menu.getMenu().performIdentifierAction(R.id.editor_header_reset, 0);
        assertEquals(1, f.resets);
    }

    @Test
    public void theEyeShowsAndHidesTheObjectThroughTheModel() {
        Fixture f = new Fixture(EditorCard.KEYBOARD, 1f);
        assertTrue("shown reads checked", f.sheet.eyeButton().isChecked());
        f.sheet.eyeButton().performClick();
        assertEquals(0, f.model.visible);
        assertFalse("hidden reads unchecked", f.sheet.eyeButton().isChecked());
        f.sheet.eyeButton().performClick();
        assertEquals(1, f.model.visible);
        assertTrue(f.sheet.eyeButton().isChecked());
        f.model.visible = 0;
        f.sheet.restate();
        assertFalse("a restatement reads the model again", f.sheet.eyeButton().isChecked());
    }

    @Test
    public void noCardHasAVisibleSwitchRow() {
        for (EditorCard card : EditorCard.values()) {
            Fixture f = new Fixture(card, 1f);
            assertFalse(card + " has no Visible row", hasText(f.sheet.rows(),
                f.themed.getString(R.string.appearance_row_visible)));
        }
    }

    private static boolean hasDescription(@NonNull View view, @NonNull String text) {
        if (text.contentEquals(String.valueOf(view.getContentDescription())))
            return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasDescription(group.getChildAt(i), text))
                    return true;
            }
        }
        return false;
    }

    private static boolean hasText(@NonNull View view, @NonNull String text) {
        if (view instanceof android.widget.TextView
            && text.contentEquals(((android.widget.TextView) view).getText()))
            return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasText(group.getChildAt(i), text))
                    return true;
            }
        }
        return false;
    }

    private static final class Fixture {
        final Context themed;
        final FrameLayout parent;
        final EditorCardSheet sheet;
        final Model model;
        int resets;
        int backs;
        EditorCard opened;

        Fixture(@NonNull EditorCard card, float fontScale) {
            RuntimeEnvironment.setFontScale(fontScale);
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            themed = new ContextThemeWrapper(activity,
                R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            float density = themed.getResources().getDisplayMetrics().density;
            int width = Math.round(411 * density);
            int height = Math.round(914 * density);
            parent = new FrameLayout(themed);
            activity.setContentView(parent, new ViewGroup.LayoutParams(width, height));
            model = new Model(themed);
            sheet = new EditorCardSheet(themed, model, new EditorCardSheet.Listener() {
                @Override public void onReset() {
                    resets++;
                }
                @Override public void onSheetMoved(int detent, float topPx) {}
                @Override public void onOpenCard(@NonNull EditorCard card) {
                    opened = card;
                }
                @Override public void onBack() {
                    backs++;
                }
            });
            sheet.attach(parent);
            int full = Math.round(height * 0.55f);
            sheet.setGeometry(full, full, Math.round(24 * density));
            sheet.show(card, EditorRows.rows(card, new EditorRows.Offers(true, true)),
                card == EditorCard.DOCK ? "Reset dock" : "Reset", false);
            parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            parent.layout(0, 0, width, height);
        }

        @NonNull
        View title() {
            return findTitle(sheet.view(), themed.getString(sheet.card().titleRes));
        }

        int left(@NonNull View view) {
            int[] at = new int[2];
            view.getLocationInWindow(at);
            int[] origin = new int[2];
            parent.getLocationInWindow(origin);
            return at[0] - origin[0];
        }

        @NonNull
        private static View findTitle(@NonNull View view, @NonNull String title) {
            if (view instanceof android.widget.TextView
                && title.contentEquals(((android.widget.TextView) view).getText()))
                return view;
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    try {
                        return findTitle(group.getChildAt(i), title);
                    } catch (AssertionError ignored) {
                        // Not under this child.
                    }
                }
            }
            throw new AssertionError("no title " + title);
        }
    }

    /** A model with mid-range values and a Visible that the eye can turn. */
    private static final class Model implements EditorCardSheet.Model {
        private final Context mContext;
        int visible = 1;

        Model(@NonNull Context context) {
            mContext = context;
        }

        @Override public int value(@NonNull Row row) {
            if (row == Row.VISIBLE) return visible;
            return row.kind == EditorRows.Kind.SLIDER ? 50 : 0;
        }

        @Override public int from(@NonNull Row row) {
            return 0;
        }

        @Override public int to(@NonNull Row row) {
            return row == Row.LOOK ? AppearanceLooks.CUSTOM_STOP : 100;
        }

        @Override public int step(@NonNull Row row) {
            return 1;
        }

        @NonNull @Override public String readout(@NonNull Row row, int value) {
            return mContext.getString(R.string.appearance_readout_percent, value);
        }

        @Override public boolean ownValue(@NonNull Row row) {
            return false;
        }

        @Override public boolean enabled(@NonNull Row row) {
            return true;
        }

        @Nullable @Override public CharSequence summary(@NonNull Row row) {
            return null;
        }

        @NonNull @Override public String[] choices(@NonNull Row row) {
            switch (row) {
                case STYLE: return new String[] {"Docked", "Floating"};
                case DESIGN: return new String[] {"Tray", "Beads", "Strip"};
                case CONTRAST:
                case LABEL_SIZE:
                case FOLDERS:
                case FORM:
                case THEME: return new String[] {"One", "Two", "Three"};
                case NUMERALS: return new String[] {"Sans", "Mono"};
                case LOOK: return new String[] {"Clear", "Mist", "Tint", "Solid", "Custom"};
                default: return new String[0];
            }
        }

        @Nullable @Override public int[] chipIcons(@NonNull Row row) {
            return null;
        }

        @Nullable @Override public View expanderContent(@NonNull Row row) {
            return null;
        }

        @Override public void onValue(@NonNull Row row, int value, boolean dragging) {
            if (row == Row.VISIBLE) visible = value;
        }

        @Override public void onReleased(@NonNull Row row) {}

        @Override public void onAction(@NonNull Row row, @NonNull View anchor) {}
    }
}
