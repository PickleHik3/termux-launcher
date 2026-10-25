package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

/**
 * Issue #164: on Page 2, which ships empty and absent from {@code termux.properties}, the reader
 * could not enter a new key. The whole add path is driven here for page index 1: choose the page,
 * open the picker, type a value, take it as text, and save. The file itself is not readable from the JVM sandbox; the page round-trip is
 * covered by ExtraKeysDraftTest and ExtraKeysLayoutModelTest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ExtraKeysEditorFragmentTest {

    private static void idle(SettingsActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static View find(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = find(g.getChildAt(i), text);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static EditText findEdit(View v) {
        if (v instanceof EditText) return (EditText) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText r = findEdit(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    @Test
    public void pageTwoTakesATypedKeyAndSavesIt() {
        Intent intent = SettingsActivity.createFragmentIntent(RuntimeEnvironment.getApplication(),
            ExtraKeysEditorFragment.class, 0);
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class, intent)
            .create().start().resume().visible();
        idle(c.get());
        Fragment f = c.get().getSupportFragmentManager().findFragmentById(R.id.settings);

        find(f.getView(), "Page 2").performClick();
        idle(c.get());
        assertNotNull("the add affordance stays on an empty page", find(f.getView(), "More"));
        find(f.getView(), "More").performClick();
        idle(c.get());

        View sheet = ShadowDialog.getLatestDialog().getWindow().getDecorView();
        EditText search = findEdit(sheet);
        assertNotNull(search);
        search.setText("zz9");
        idle(c.get());
        View send = find(sheet, "Send \"zz9\" as text");
        assertNotNull("typed text is offered as a key", send);
        ((View) send.getParent().getParent()).performClick();
        idle(c.get());

        assertNotNull("the new key is on page 2", find(f.getView(), "zz9"));
        assertTrue(((ExtraKeysEditorFragment) f).hasUnsavedChanges());
        find(f.getView(), "Save").performClick();
        idle(c.get());
        assertFalse("Save committed the draft", ((ExtraKeysEditorFragment) f).hasUnsavedChanges());
    }
}
