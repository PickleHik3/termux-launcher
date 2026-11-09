package com.termux.app.fragments.settings.termux;

import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.terminal.io.ExtraKeysDraft;

import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Opens the extra-keys editor in Settings and finds its views, for the editor's tests. */
final class ExtraKeysEditorHarness {

    final ActivityController<SettingsActivity> controller;
    final ExtraKeysEditorFragment fragment;

    ExtraKeysEditorHarness() {
        Intent intent = SettingsActivity.createFragmentIntent(
            RuntimeEnvironment.getApplication(), ExtraKeysEditorFragment.class, 0);
        controller = Robolectric.buildActivity(SettingsActivity.class, intent)
            .create().start().resume().visible();
        idle();
        Fragment f = controller.get().getSupportFragmentManager().findFragmentById(R.id.settings);
        fragment = (ExtraKeysEditorFragment) f;
    }

    void idle() {
        controller.get().getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
    }

    View page() {
        return fragment.getView();
    }

    /** The decor of the newest dialog: the sheet that just opened. */
    View sheet() {
        return ShadowDialog.getLatestDialog().getWindow().getDecorView();
    }

    void click(View view) {
        view.performClick();
        idle();
    }

    /** Edits the draft directly, as a shortcut to a state the UI takes many taps to reach. */
    void editDraft(Consumer<ExtraKeysDraft> edit) throws Exception {
        Field field = ExtraKeysEditorFragment.class.getDeclaredField("draft");
        field.setAccessible(true);
        edit.accept((ExtraKeysDraft) field.get(fragment));
        Method refresh = ExtraKeysEditorFragment.class.getDeclaredMethod("refreshAll");
        refresh.setAccessible(true);
        refresh.invoke(fragment);
        Method edited = ExtraKeysEditorFragment.class.getDeclaredMethod("edited");
        edited.setAccessible(true);
        edited.invoke(fragment);
        idle();
    }

    static View findText(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findText(g.getChildAt(i), text);
                if (r != null) return r;
            }
        }
        return null;
    }

    static View findDescription(View v, String description) {
        if (v.getContentDescription() != null
            && description.contentEquals(v.getContentDescription())) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findDescription(g.getChildAt(i), description);
                if (r != null) return r;
            }
        }
        return null;
    }

    static EditText findEdit(View v) {
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

    static String string(int res) {
        return ((Application) RuntimeEnvironment.getApplication()).getString(res);
    }
}
