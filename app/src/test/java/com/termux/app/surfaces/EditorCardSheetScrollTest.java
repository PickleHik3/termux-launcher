package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.surfaces.EditorRows.Row;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.List;


/**
 * A vertical drag on the card's content moves the sheet like a standard bottom sheet: up grows it
 * (peek, half, full) before the content scrolls, down at the content's top shrinks it a stop, and
 * otherwise the content scrolls as it does without the sheet.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class EditorCardSheetScrollTest {

    private int mWidth;
    private int mHeight;
    private FrameLayout mParent;
    private EditorCardSheet mSheet;
    private long mTime = 1000L;

    private void setUp() {
        setUp(EditorCard.DOCK);
    }

    private void setUp(EditorCard card) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Context themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        float density = themed.getResources().getDisplayMetrics().density;
        mWidth = Math.round(411 * density);
        mHeight = Math.round(914 * density);
        int nav = Math.round(24 * density);
        mParent = new FrameLayout(themed);
        activity.setContentView(mParent, new ViewGroup.LayoutParams(mWidth, mHeight));
        mSheet = new EditorCardSheet(themed, new FakeModel(themed),
            new EditorCardSheet.Listener() {
                @Override public void onReset() {}
                @Override public void onSheetMoved(int detent, float topPx) {}
            });
        int full = Math.round(mHeight * 0.8f);
        int half = Math.round(mHeight * 0.4f);
        mSheet.attach(mParent);
        mSheet.setGeometry(half, full, nav);
        mSheet.show(card, EditorRows.rows(card,
            new EditorRows.Offers(true, true)), "Reset", false);
        layOut();
    }

    private void layOut() {
        mParent.measure(View.MeasureSpec.makeMeasureSpec(mWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(mHeight, View.MeasureSpec.EXACTLY));
        mParent.layout(0, 0, mWidth, mHeight);
    }

    /** A quick flick on the content: {@code dy} pixels (negative is up) in 50 ms. */
    private void flick(float dy) {
        View scroll = mSheet.scroll();
        float x = mWidth / 2f;
        float y = mHeight * 0.7f;
        send(scroll, MotionEvent.ACTION_DOWN, x, y);
        for (int i = 1; i <= 5; i++) {
            mTime += 10;
            send(scroll, MotionEvent.ACTION_MOVE, x, y + dy * i / 5f);
        }
        send(scroll, MotionEvent.ACTION_UP, x, y + dy);
    }

    private static String deepest(View view, float x, float y) {
        int[] at = new int[2];
        String best = "";
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View child = group.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                float cx = x + group.getScrollX() - child.getLeft();
                float cy = y + group.getScrollY() - child.getTop() - child.getTranslationY();
                if (cx >= 0 && cy >= 0 && cx < child.getWidth() && cy < child.getHeight())
                    return view.getClass().getSimpleName() + ">" + deepest(child, cx, cy);
            }
        }
        return view.getClass().getSimpleName();
    }

    private void send(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(mTime, mTime, action, x, y, 0);
        view.dispatchTouchEvent(event);
        event.recycle();
    }

    /** The device's path: the drag goes into the sheet's parent, down the real hierarchy. */
    @Test
    public void upOnARowFromTheParentGrowsTheSheetOnEveryCard() {
        for (EditorCard card : EditorCard.values()) {
            for (int step = 0; step < 6; step++) {
                setUp(card);
                float top = mHeight - (mHeight * 0.4f);
                float y = top + 160 + step * (mHeight * 0.4f - 220) / 6f;
                float x = 100f;
                // A glass column's slider is vertical: a vertical drag on it is the slider's.
                if (deepest(mParent, x, y).contains("LegendSlider"))
                    continue;
                // The Dock card's bars reorder by a vertical drag on their handle.
                if (card == EditorCard.DOCK && deepest(mParent, x, y).endsWith("ImageView"))
                    continue;
                send(mParent, MotionEvent.ACTION_DOWN, x, y);
                for (int i = 1; i <= 5; i++) {
                    mTime += 10;
                    send(mParent, MotionEvent.ACTION_MOVE, x, y - 60f * i);
                }
                send(mParent, MotionEvent.ACTION_UP, x, y - 300f);
                assertEquals(card + " step " + step + " at " + deepest(mParent, x, y), EditorCardSheet.DETENT_FULL, mSheet.detent());
            }
        }
    }

    @Test
    public void upOnTheContentGrowsTheSheetBeforeTheContentScrolls() {
        setUp();
        assertTrue("the content is taller than its viewport",
            mSheet.scroll().canScrollVertically(1));
        assertEquals(EditorCardSheet.DETENT_HALF, mSheet.detent());
        flick(-300f);
        assertEquals(EditorCardSheet.DETENT_FULL, mSheet.detent());
        assertEquals(0, mSheet.scroll().getScrollY());
        layOut();
        flick(-300f);
        assertEquals(EditorCardSheet.DETENT_FULL, mSheet.detent());
        assertTrue("a further drag up scrolls the content", mSheet.scroll().getScrollY() > 0);
    }

    @Test
    public void downAtTheContentsTopShrinksTheSheetAStop() {
        setUp();
        mSheet.setDetent(EditorCardSheet.DETENT_FULL, false);
        layOut();
        flick(300f);
        assertEquals(EditorCardSheet.DETENT_HALF, mSheet.detent());
        assertEquals(0, mSheet.scroll().getScrollY());
    }

    @Test
    public void downWithTheContentScrolledScrollsTheContentBackFirst() {
        setUp();
        mSheet.setDetent(EditorCardSheet.DETENT_FULL, false);
        layOut();
        mSheet.scroll().scrollTo(0, 400);
        int before = mSheet.scroll().getScrollY();
        assertTrue(before > 0);
        flick(150f);
        assertEquals(EditorCardSheet.DETENT_FULL, mSheet.detent());
        assertTrue("the content scrolled back", mSheet.scroll().getScrollY() < before);
    }

    /** Values a card would show on a phone in its first days: mid-range, choices in place. */
    private static final class FakeModel implements EditorCardSheet.Model {
        private final Context mContext;
        private final com.termux.app.surfaces.cards.DockCard mDock;

        FakeModel(@NonNull Context context) {
            mContext = context;
            mDock = new com.termux.app.surfaces.cards.DockCard(new FakeDockHost(context));
        }

        @Nullable @Override public View rowView(@NonNull Row row, int widthPx) {
            return mDock.view(row, widthPx);
        }

        @Override public void restateRowView(@NonNull Row row, @NonNull View view) {
            mDock.restate(row);
        }

        @Override public int value(@NonNull Row row) {
            return row.kind == EditorRows.Kind.STRIP ? 50 : 0;
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
            return row == Row.OPACITY;
        }

        @Override public boolean enabled(@NonNull Row row) {
            return true;
        }

        @Nullable @Override public CharSequence summary(@NonNull Row row) {
            return row.kind == EditorRows.Kind.MENU ? "Default" : null;
        }

        @NonNull @Override public String[] choices(@NonNull Row row) {
            switch (row) {
                case STYLE: return strings(R.string.appearance_editor_style_docked,
                    R.string.appearance_editor_style_floating);
                case DESIGN: return strings(R.string.layout_editor_dock_style_glass_tray,
                    R.string.layout_editor_dock_style_lens_beads,
                    R.string.layout_editor_dock_style_wall_strip);
                case CONTRAST: return strings(R.string.appearance_contrast_softer,
                    R.string.appearance_contrast_default, R.string.appearance_contrast_harder);
                case LABEL_SIZE: return strings(R.string.appearance_size_small,
                    R.string.appearance_size_medium, R.string.appearance_size_large);
                case FOLDERS: return strings(R.string.appearance_folders_compact,
                    R.string.appearance_folders_default, R.string.appearance_folders_roomy);
                case NUMERALS: return strings(R.string.appearance_numerals_sans,
                    R.string.appearance_numerals_mono);
                case FORM: return strings(R.string.appearance_form_docked,
                    R.string.appearance_form_floating, R.string.appearance_form_split);
                case THEME: return strings(R.string.appearance_theme_system,
                    R.string.appearance_theme_light, R.string.appearance_theme_dark);
                case LOOK: return strings(R.string.termux_surface_preset_minimal,
                    R.string.termux_surface_preset_frost, R.string.termux_surface_preset_stock,
                    R.string.termux_surface_preset_solid, R.string.termux_surface_preset_custom);
                default: return new String[0];
            }
        }

        @Nullable @Override public int[] chipIcons(@NonNull Row row) {
            return row == Row.DESIGN ? new int[] {R.drawable.ic_symbol_dock_glass_tray,
                R.drawable.ic_symbol_dock_lens_beads, R.drawable.ic_symbol_dock_wall_strip}
                : null;
        }

        @Nullable @Override public View expanderContent(@NonNull Row row) {
            TextView content = new TextView(mContext);
            content.setText("Packs");
            return content;
        }

        @Override public void onValue(@NonNull Row row, int value, boolean dragging) {}

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

    /** The Dock card on a fresh install: Docked, Apps and A–Z shown, Extra keys hidden. */
    private static final class FakeDockHost implements com.termux.app.surfaces.cards.DockCard.Host {
        private final Context mContext;

        FakeDockHost(@NonNull Context context) {
            mContext = context;
        }

        @NonNull @Override public Context context() { return mContext; }
        @Override public boolean floating() { return false; }
        @Override public void setFloating(boolean floating) {}
        @NonNull @Override public List<com.termux.app.place.Element> barOrder() {
            return com.termux.app.surfaces.cards.BarOrder.DEFAULT;
        }
        @Override public void setBarOrder(@NonNull List<com.termux.app.place.Element> order) {}
        @Override public boolean offers(@NonNull com.termux.app.place.Element bar) { return true; }
        @Override public boolean barShown(@NonNull com.termux.app.place.Element bar) {
            return bar != com.termux.app.place.Element.EXTRA_KEYS;
        }
        @Override public void setBarShown(@NonNull com.termux.app.place.Element bar, boolean shown) {}
        @NonNull @Override public com.termux.app.surfaces.cards.BarOrder.Position position(
                @NonNull com.termux.app.place.Element bar) {
            return com.termux.app.surfaces.cards.BarOrder.positionsFor(bar).get(0);
        }
        @Override public void setPosition(@NonNull com.termux.app.place.Element bar,
                @NonNull com.termux.app.surfaces.cards.BarOrder.Position position) {}
        @Override public int appIcons() { return 7; }
        @Override public int appIconsFrom() { return 3; }
        @Override public int appIconsTo() { return 10; }
        @Override public void setAppIcons(int count) {}
        @Override public boolean mostUsed() { return true; }
        @Override public void setMostUsed(boolean on) {}
        @NonNull @Override public CharSequence iconPack() { return "System"; }
        @Override public void pickIconPack() {}
        @Override public boolean azGlass() { return false; }
        @Override public void setAzGlass(boolean on) {}
        @Override public int azShow() { return 0; }
        @Override public void setAzShow(int index) {}
        @Override public void openExtraKeysEditor() {}
        @Override public int dockType() { return 0; }
        @Override public void setDockType(int index) {}
        @Override public boolean option(
                @NonNull com.termux.app.surfaces.cards.DockCard.Option option) {
            return option.ordinal() % 2 == 0;
        }
        @Override public void setOption(
                @NonNull com.termux.app.surfaces.cards.DockCard.Option option, boolean on) {}
    }
}
