package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.termux.launcherctl.LauncherToolRegistry;
import com.termux.shared.termux.extrakeys.ExtraKeyIcons;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/**
 * The key picker's action rows: the bar's cap as the leading mark in a fixed slot, then the name;
 * the raw id is gone from the row but still found by the search.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ExtraKeyActionPickerRowsTest {

    private static final String RENDER_DIR =
        "/home/amalvajdan/.cache/window-restructure/rounds/key-picker/";
    private static final int ACTION_TYPE = 3;

    @org.junit.Before
    public void registerTheBarsIcons() {
        // The Application does this at start; the bar's span only draws registered code points.
        ExtraKeyActionLabels.registerIcons();
    }

    private static Context context() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
    }

    private static ExtraKeyActionPicker.Cell cellFor(List<ExtraKeyActionPicker.Cell> cells,
                                                     String choiceId) {
        for (ExtraKeyActionPicker.Cell cell : cells) {
            if (cell.choice != null && choiceId.equals(cell.choice.id)) return cell;
        }
        return null;
    }

    private static List<ExtraKeyActionPicker.Cell> search(String query) {
        ExtraKeyActionPicker picker = new ExtraKeyActionPicker(context());
        picker.query = query;
        return picker.buildCells();
    }

    private static View bind(ExtraKeyActionPicker picker, ExtraKeyActionPicker.Cell cell) {
        ExtraKeyActionPicker.GridAdapter adapter = picker.new GridAdapter();
        adapter.cells = new ArrayList<>();
        adapter.cells.add(cell);
        RecyclerView.ViewHolder holder =
            adapter.onCreateViewHolder(new FrameLayout(context()), ACTION_TYPE);
        adapter.onBindViewHolder(holder, 0);
        return holder.itemView;
    }

    private static void collectText(View view, List<CharSequence> out) {
        if (view instanceof TextView) out.add(((TextView) view).getText());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), out);
        }
    }

    @Test
    public void searchByIdOrAFragmentOfItStillFindsTheRow() {
        String id = LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_SCRATCHPAD;
        assertNotNull(cellFor(search(id), "tool:" + id));
        assertNotNull(cellFor(search("toggle_scratchpad"), "tool:" + id));
        assertNotNull(cellFor(search("TOGGLE_SCRATCH"), "tool:" + id));
        String page = LauncherToolRegistry.TOOL_APP_PAGE_SWITCHER;
        assertNotNull(cellFor(search(page), "tool:" + page));
        assertNotNull(cellFor(search("page_switcher"), "tool:" + page));
    }

    @Test
    public void anActionRowHasAnIconMarkAndNoIdText() {
        ExtraKeyActionPicker picker = new ExtraKeyActionPicker(context());
        String id = LauncherToolRegistry.TOOL_TERMINAL_TOGGLE_SCRATCHPAD;
        ExtraKeyActionPicker.Cell cell = cellFor(search(id), "tool:" + id);
        assertNotNull(cell);
        // The mark is exactly the cap the bar draws for this tool.
        assertEquals(ExtraKeyIcons.string(ExtraKeyIcons.FLOAT_WINDOW), cell.mark);
        assertEquals(ExtraKeyActionLabels.defaultCapGlyph(id), cell.mark);
        View row = bind(picker, cell);
        List<CharSequence> texts = new ArrayList<>();
        collectText(row, texts);
        assertEquals(2, texts.size());
        for (CharSequence text : texts) assertFalse(text.toString().contains(id));
        assertEquals(cell.label, texts.get(1).toString());
        // The mark is a drawable span, not the private-use character as text.
        assertTrue(texts.get(0) instanceof android.text.Spanned);
        assertEquals(cell.label, row.getContentDescription().toString());
    }

    @Test
    public void aTextCapActionShowsItsCapAndEveryMarkHasTheSameSlot() {
        ExtraKeyActionPicker picker = new ExtraKeyActionPicker(context());
        picker.category = ExtraKeyActionPicker.Category.ACTIONS;
        int slot = Math.round(ExtraKeyActionPicker.MARK_SLOT_DP
            * context().getResources().getDisplayMetrics().density);
        int rows = 0;
        boolean sawText = false;
        for (ExtraKeyActionPicker.Cell cell : picker.buildCells()) {
            if (cell.mark == null) continue;
            rows++;
            LinearLayout row = (LinearLayout) bind(picker, cell);
            assertEquals(slot, row.getChildAt(0).getLayoutParams().width);
            String tool = cell.choice.id.replace("tool:", "").split(":")[0];
            if (ExtraKeyActionLabels.defaultCapGlyph(tool) == null
                && !cell.choice.id.startsWith("tool:wall.go")) {
                sawText = true;
                assertEquals(cell.mark, ((TextView) row.getChildAt(0)).getText().toString());
            }
        }
        assertTrue(rows > 10);
        assertTrue("some action has a text cap", sawText);
    }

    @Test
    public void renderTheLists() {
        com.termux.app.testing.ReportRenders.assumeRequested();
        for (String scale : new String[] {"1.0", "1.3"}) {
            for (String night : new String[] {"notnight", "night"}) {
                RuntimeEnvironment.setQualifiers("w411dp-h891dp-" + night + "-xxhdpi");
                RuntimeEnvironment.setFontScale(Float.parseFloat(scale));
                shoot("actions", scale, night, ExtraKeyActionPicker.Category.ACTIONS);
                shoot("common", scale, night, ExtraKeyActionPicker.Category.COMMON);
            }
        }
    }

    private void shoot(String name, String scale, String night,
                       ExtraKeyActionPicker.Category category) {
        Context context = context();
        ExtraKeyActionPicker picker = new ExtraKeyActionPicker(context);
        picker.category = category;
        ExtraKeyActionPicker.GridAdapter adapter = picker.new GridAdapter();
        adapter.rebuild();
        RecyclerView grid = new RecyclerView(context);
        GridLayoutManager manager = new GridLayoutManager(context, 4);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return adapter.cells.get(position).type == 1 ? 1 : 4;
            }
        });
        grid.setLayoutManager(manager);
        grid.setAdapter(adapter);
        grid.setBackgroundColor(night.equals("night") ? 0xFF1B1B1F : 0xFFFEFBFF);
        int w = context.getResources().getDisplayMetrics().widthPixels;
        grid.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, w, 2000);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(w, 2000,
            android.graphics.Bitmap.Config.ARGB_8888);
        grid.draw(new android.graphics.Canvas(bitmap));
        try (java.io.OutputStream out = new java.io.FileOutputStream(
                RENDER_DIR + name + "-" + night + "-font" + scale + ".png")) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
