package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Layout;
import android.view.View;
import android.widget.LinearLayout;

import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class,
    qualifiers = "w360dp-h700dp-xxhdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppDrawerHighlightBeadTest {

    private static final File OUT = new File(
        "/home/amalvajdan/.cache/window-restructure/rounds/drawer-highlight");
    private static final String SHORT = "Maps";
    private static final String LONG = "Extraordinarily Long Application Name";

    @After public void resetChrome() {
        ChromeShade.clear();
    }

    private static AppDrawerAppCellView laidOutCell(int columns, String name, boolean highlight) {
        float density = RuntimeEnvironment.getApplication().getResources()
            .getDisplayMetrics().density;
        AppDrawerGridMetrics metrics = AppDrawerGridMetrics.resolve(1080f, density,
            14f * density, columns);
        int w = Math.round(metrics.cellWidthPx);
        int h = Math.round(metrics.rowHeightPx);
        AppDrawerAppCellView cell = new AppDrawerAppCellView(RuntimeEnvironment.getApplication());
        cell.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        LauncherAppEntry entry = new LauncherAppEntry(new AppRef("pkg." + name, "Main"), name,
            new ColorDrawable(Color.rgb(60, 140, 220)));
        cell.bind(null, entry, metrics, AppDrawerAppCellView.ALLOW_CLICKS);
        cell.setKeyboardHighlight(highlight);
        layout(cell, w, h);
        return cell;
    }

    private static void layout(View view, int w, int h) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
    }

    @Test public void highlightHugsContentAndMatchesThePureAnswer() {
        ChromeShade.note(ChromeInk.Polarity.PALE_INK, 0xFF101418);
        for (int columns : new int[] {4, 5}) {
            for (String name : new String[] {SHORT, LONG}) {
                AppDrawerAppCellView cell = laidOutCell(columns, name, true);
                AppDrawerGlassBeadDrawable bead = (AppDrawerGlassBeadDrawable) cell.getBackground();
                int[] expected = expected(cell);
                assertEquals(expected[0], bead.innerLeft());
                assertEquals(expected[1], bead.innerTop());
                assertEquals(expected[2], bead.innerRight());
                assertEquals(expected[3], bead.innerBottom());
                assertFalse(bead.innerLeft() == 0 && bead.innerRight() == cell.getWidth());
                assertNotEquals(cell.getHeight(), bead.innerBottom() - bead.innerTop());
                int centre = cell.icon.getLeft() + cell.icon.getWidth() / 2;
                assertEquals(centre - bead.innerLeft(), bead.innerRight() - centre);
            }
        }
    }

    @Test public void shortNamesGetASnugRingAndLongOnesTheFullLabel() {
        AppDrawerAppCellView shortCell = laidOutCell(4, SHORT, true);
        AppDrawerAppCellView longCell = laidOutCell(4, LONG, true);
        AppDrawerGlassBeadDrawable s = (AppDrawerGlassBeadDrawable) shortCell.getBackground();
        AppDrawerGlassBeadDrawable l = (AppDrawerGlassBeadDrawable) longCell.getBackground();
        assertTrue(s.innerRight() - s.innerLeft() < l.innerRight() - l.innerLeft());
    }

    @Test public void clearingTheHighlightRemovesTheBeadAndSettingItBringsItBack() {
        AppDrawerAppCellView cell = laidOutCell(4, SHORT, true);
        cell.setKeyboardHighlight(false);
        assertNull(cell.getBackground());
        cell.setKeyboardHighlight(true);
        assertTrue(cell.getBackground() instanceof AppDrawerGlassBeadDrawable);
    }

    @Test public void renderComparisonPngs() throws Exception {
        org.junit.Assume.assumeTrue(OUT.isDirectory() || OUT.mkdirs());
        for (boolean dark : new boolean[] {true, false}) {
            if (dark) ChromeShade.note(ChromeInk.Polarity.PALE_INK, 0xFF101418);
            else ChromeShade.note(ChromeInk.Polarity.DARK_INK, 0xFFF4F6FA);
            for (int columns : new int[] {4, 5}) {
                int rowHeight = 0;
                LinearLayout stack = new LinearLayout(RuntimeEnvironment.getApplication());
                stack.setOrientation(LinearLayout.VERTICAL);
                stack.setBackgroundColor(dark ? 0xFF1B222B : 0xFFE9ECF1);
                for (boolean old : new boolean[] {true, false}) {
                    LinearLayout row = new LinearLayout(RuntimeEnvironment.getApplication());
                    for (int i = 0; i < columns; i++) {
                        String name = i == 0 ? SHORT : i == 1 ? LONG : "App" + i;
                        AppDrawerAppCellView cell = laidOutCell(columns, name, i < 2);
                        cell.label.setTextColor(dark ? Color.WHITE : Color.BLACK);
                        if (old && i < 2) cell.setBackground(oldRing());
                        rowHeight = cell.getHeight();
                        row.addView(cell);
                    }
                    stack.addView(row);
                }
                layout(stack, 1080, rowHeight * 2);
                Bitmap bitmap = Bitmap.createBitmap(1080, rowHeight * 2, Bitmap.Config.ARGB_8888);
                stack.draw(new Canvas(bitmap));
                File png = new File(OUT, "bead-" + columns + "col-" + (dark ? "dark" : "light")
                    + ".png");
                try (FileOutputStream out = new FileOutputStream(png)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            }
        }
    }

    private static GradientDrawable oldRing() {
        float density = RuntimeEnvironment.getApplication().getResources()
            .getDisplayMetrics().density;
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(16 * density);
        ring.setColor(ChromeShade.stateFill(0x26FFFFFF, 0x08FFFFFF));
        ring.setStroke(Math.round(2 * density), ChromeShade.rim(0xE6FFFFFF));
        return ring;
    }

    private static int[] expected(AppDrawerAppCellView cell) {
        float density = cell.getResources().getDisplayMetrics().density;
        int padding = Math.round(AppDrawerHighlightShape.PADDING_DP * density);
        int spare = Math.max(1, Math.round(AppDrawerHighlightShape.SPARE_DP * density));
        int[] box = {cell.icon.getLeft(), cell.icon.getTop(), cell.icon.getRight(),
            cell.icon.getBottom()};
        Layout layout = cell.label.getLayout();
        int areaLeft = cell.label.getLeft() + cell.label.getCompoundPaddingLeft();
        int areaRight = cell.label.getRight() - cell.label.getCompoundPaddingRight();
        int lineWidth = Math.min(areaRight - areaLeft, (int) Math.ceil(layout.getLineWidth(0)));
        int textLeft = areaLeft + (areaRight - areaLeft - lineWidth) / 2;
        int textRight = textLeft + lineWidth;
        box[0] = Math.min(box[0], textLeft);
        box[2] = Math.max(box[2], textRight);
        box[1] = Math.min(box[1], cell.label.getTop() + cell.label.getCompoundPaddingTop());
        box[3] = Math.max(box[3], cell.label.getBottom() - cell.label.getCompoundPaddingBottom());
        int[] out = new int[4];
        AppDrawerHighlightShape.resolve(cell.getWidth(), cell.getHeight(), box,
            cell.icon.getLeft() + cell.icon.getWidth() / 2, true, padding, spare, out);
        return out;
    }
}
