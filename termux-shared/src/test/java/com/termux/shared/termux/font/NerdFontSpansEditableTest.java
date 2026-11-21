package com.termux.shared.termux.font;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.SpannableStringBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Typing in a key's label field must keep the icon drawn, not leave an empty box. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class NerdFontSpansEditableTest {

    private static final int ICON = 0x10FFEE;

    private static Context context() {
        return RuntimeEnvironment.getApplication();
    }

    @Test public void applyToSpansARegisteredDrawableSymbolAndRespansAfterAnEdit() {
        NerdFontSpans.registerDrawableSymbol(ICON, android.R.drawable.ic_menu_add);
        Context context = context();
        Editable editable = new SpannableStringBuilder(new String(Character.toChars(ICON)));
        NerdFontSpans.applyTo(context, editable);
        assertEquals(1, editable.getSpans(0, editable.length(),
            NerdFontSpans.TintedDrawableSpan.class).length);

        editable.append("x");
        NerdFontSpans.applyTo(context, editable);
        NerdFontSpans.TintedDrawableSpan[] spans = editable.getSpans(0, editable.length(),
            NerdFontSpans.TintedDrawableSpan.class);
        assertEquals(1, spans.length);
        assertEquals(0, editable.getSpanStart(spans[0]));
        assertEquals(2, editable.getSpanEnd(spans[0]));
    }
}
