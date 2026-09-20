package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class StatusBarWidgetViewTest {

    @Test
    public void iconAndValue_areVerticallyCenteredWithSymmetricPadding() {
        StatusBarWidgetView widget = new StatusBarWidgetView(
            ApplicationProvider.getApplicationContext(), null);
        widget.setValue("88%");
        widget.setIconGlyph("\uf4bc");   // nf-oct-cpu, the CPU widget's icon
        widget.measure(exact(100), exact(24));
        widget.layout(0, 0, 100, 24);

        assertEquals(widget.getPaddingTop(), widget.getPaddingBottom());
        for (int i = 0; i < widget.getChildCount(); i++) {
            View child = widget.getChildAt(i);
            // The icon comes in two mutually exclusive forms — a vector and a Nerd Font glyph —
            // so whichever one is unused sits GONE at the origin with nothing to centre.
            if (child.getVisibility() == View.GONE) continue;
            assertEquals(widget.getHeight() / 2f,
                (child.getTop() + child.getBottom()) / 2f, .51f);
            assertEquals(0f, child.getTranslationY(), .01f);
        }
    }

    /** The mouse mark is a glyph alone; it must not carry the stats' shared floor width. */
    @Test
    public void glyphWithoutValue_takesOnlyTheGlyphAndPadding() {
        StatusBarWidgetView widget = new StatusBarWidgetView(
            ApplicationProvider.getApplicationContext(), null);
        widget.setIconGlyph("\uf245");
        widget.setValue("");
        widget.measure(atMost(400), exact(24));
        int glyphWidth = 0;
        for (int i = 0; i < widget.getChildCount(); i++) {
            View child = widget.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            glyphWidth += child.getMeasuredWidth();
        }
        assertEquals(widget.getPaddingLeft() + glyphWidth + widget.getPaddingRight(),
            widget.getMeasuredWidth());

        widget.setValue("88%");
        widget.measure(atMost(400), exact(24));
        assertTrue(widget.getMeasuredWidth() >= widget.getMinimumWidth());
        assertTrue(widget.getMinimumWidth() > 0);
    }

    /**
     * The three stats draw at one weight and one size — the temperature's — and none of them is
     * dimmer than another; the hierarchy is hue and order. The widget applies the shared answer
     * itself, so every tier lands on the same face at the same size.
     */
    @Test
    public void colorRole_drawsEveryTierAtTheTemperaturesWeightAndSize() {
        StatusBarWidgetView cpu = widget(StatusBarWidgetView.ColorRole.PRIMARY);
        StatusBarWidgetView ram = widget(StatusBarWidgetView.ColorRole.SECONDARY);
        StatusBarWidgetView weather = widget(StatusBarWidgetView.ColorRole.TERTIARY);

        assertEquals("the CPU figure is the temperature's size",
            value(weather).getTextSize(), value(cpu).getTextSize(), 0.01f);
        assertEquals("and so is the RAM figure",
            value(weather).getTextSize(), value(ram).getTextSize(), 0.01f);
        assertEquals("nothing is bold", android.graphics.Typeface.NORMAL,
            value(cpu).getTypeface().getStyle());
        assertEquals("the RAM figure is on the same face as the temperature",
            value(weather).getTypeface(), value(ram).getTypeface());
        assertEquals(StatusBarWidgetView.ColorRole.TERTIARY, weather.colorRole());
    }

    /**
     * Measured ink is drawn exactly as it was resolved. The tier used to lay its own alpha over the
     * role colour, which is the contrast the resolution had just gone looking for.
     */
    @Test
    public void setInk_drawsWhatTheChromeResolvedWithNoAlphaOfItsOwn() {
        StatusBarWidgetView widget = widget(StatusBarWidgetView.ColorRole.SECONDARY);
        widget.setIconGlyph("\uf4bc");
        widget.setInk(0xFF102030, 0xFF405060, 0xFF708090);

        assertEquals(0xFF102030, value(widget).getCurrentTextColor());

        widget.setMuted(true);
        assertEquals(0xFF708090, value(widget).getCurrentTextColor());
        widget.setMuted(false);
        assertEquals(0xFF102030, value(widget).getCurrentTextColor());
    }

    private static StatusBarWidgetView widget(StatusBarWidgetView.ColorRole role) {
        StatusBarWidgetView widget = new StatusBarWidgetView(
            ApplicationProvider.getApplicationContext(), null);
        widget.setColorRole(role);
        widget.setValue("88%");
        return widget;
    }

    /** The value is the widget's last child; the icon forms come before it. */
    private static android.widget.TextView value(StatusBarWidgetView widget) {
        return (android.widget.TextView) widget.getChildAt(widget.getChildCount() - 1);
    }

    private static int atMost(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST);
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
