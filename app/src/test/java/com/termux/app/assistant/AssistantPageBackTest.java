package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.Page;
import com.termux.app.page.PageHost;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** The Assistant page's Back: Chats first, then the field's focus and the keyboard, then the chain. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AssistantPageBackTest {

    private int lowered;

    private AssistantPageView page() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        PageHost host = new PageHost() {
            @Override public void fieldFocusChanged(@NonNull Page page, boolean focused) { }
            @Override public boolean lowerKeyboard() { lowered++; return true; }
        };
        AssistantPageView view = new AssistantPageView(activity, host);
        activity.setContentView(view, new ViewGroup.LayoutParams(1080, 1800));
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1080, 1800);
        return view;
    }

    @Nullable
    private static EditText firstField(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = firstField(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @Test public void backWithTheFieldFocusedDropsItAndLowersTheKeyboardThenFallsThrough() {
        AssistantPageView view = page();
        EditText field = firstField(view);
        assertNotNull(field);
        field.setFocusableInTouchMode(true);
        assertTrue(field.requestFocus());
        assertTrue(view.isTyping());

        assertTrue("the first Back is spent on the field", view.onBack());

        assertFalse("the field lost focus", field.hasFocus());
        assertEquals("the keyboard was asked to go down", 1, lowered);
        assertFalse("nothing left: the chain goes Home", view.onBack());
        assertEquals(1, lowered);
    }

    @Test public void backWithNothingRaisedIsNotHandled() {
        AssistantPageView view = page();
        assertFalse(view.onBack());
        assertEquals(0, lowered);
    }
}
