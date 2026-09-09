package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The content view's insets listener asks for a chrome apply only when an inset the spec reads
 * has moved: a relayout re-dispatches the same insets, and that re-dispatch used to cost a second
 * full commit in the frame.
 */
@RunWith(RobolectricTestRunner.class)
public class TermuxActivityInsetsSignatureTest {

    private static WindowInsetsCompat insets(int barTop, int barBottom, int imeBottom, boolean imeVisible) {
        return new WindowInsetsCompat.Builder()
            .setInsets(Type.systemBars(), Insets.of(0, barTop, 0, barBottom))
            .setInsets(Type.ime(), Insets.of(0, 0, 0, imeBottom))
            .setVisible(Type.ime(), imeVisible)
            .build();
    }

    @Test
    public void theSameInsetsDispatchedAgainReadAsTheSame() {
        assertEquals(TermuxActivity.insetsSignature(insets(120, 60, 0, false)),
            TermuxActivity.insetsSignature(insets(120, 60, 0, false)));
    }

    @Test
    public void aMovedBarOrKeyboardReadsAsAChange() {
        long rest = TermuxActivity.insetsSignature(insets(120, 60, 0, false));
        assertNotEquals(rest, TermuxActivity.insetsSignature(insets(120, 0, 0, false)));
        assertNotEquals(rest, TermuxActivity.insetsSignature(insets(90, 60, 0, false)));
        assertNotEquals(rest, TermuxActivity.insetsSignature(insets(120, 60, 800, true)));
    }
}
