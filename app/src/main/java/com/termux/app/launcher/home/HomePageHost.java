package com.termux.app.launcher.home;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.WidgetPaneController;
import com.termux.app.page.PageKind;
import com.termux.app.wall.WidgetPaneFrame;

/**
 * What a {@link HomePage} asks of the activity: facts about the window and a few narrow requests.
 * The page itself does Home's front, off-front and back work with them. Main thread only.
 */
public interface HomePageHost {

    /** Home's widget controller; null before it is built. */
    @Nullable WidgetPaneController widgets();

    /** The widgets page's frame, which carries the controls; null before it is attached. */
    @Nullable WidgetPaneFrame frame();

    /** Runs {@code work} with the window bar refreshing once, after it. */
    void batchBarRefresh(@NonNull Runnable work);

    /** A focused Home float gives the keyboard back to the window. */
    void releaseHomeFloatFocus();

    /** Whether a Home float has the focus. */
    boolean homeFloatFocused();

    /** Whether Home keeps its keyboard up whatever a float does with it. */
    boolean homeHoldsKeyboardUp();

    /** Hides the in-app keyboard as the user's own event; false when it is not up. */
    boolean lowerKeyboard();

    /** The kind the wall last committed to, or null before any commit. */
    @Nullable PageKind committedKind();

    /** The one view every Home page shows. */
    @NonNull View view();
}
