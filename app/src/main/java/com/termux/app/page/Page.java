package com.termux.app.page;

import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.glass.GlassEntry;

import java.util.List;

/**
 * A stop on the wall (ADR 0030 section 1): each Home page and each window. Pose is not a member;
 * the wall's writer places every page from the scene. Main thread only.
 */
public interface Page {

    @NonNull PageKind kind();

    /** The side the page belongs to. */
    @NonNull
    default Side side() {
        return kind().side();
    }

    /** The page's view, laid out once in its wall cell. */
    @NonNull View view();

    /** What the page asks of the chrome at rest, from the host's plain facts. */
    @NonNull Rest rest(@NonNull Rest.Inputs in);

    /** One-time work when the page becomes the front (focus, the system IME, resuming a session). */
    void onFront();

    /** One-time work when the page stops being the front. */
    void offFront();

    /** Back, after everything raised over the page; true when the press was spent here. */
    boolean onBack();

    /** Adds the page's own glass entries (panes, widgets, floats); none by default. */
    default void declareGlass(@NonNull List<GlassEntry> out) { }
}
