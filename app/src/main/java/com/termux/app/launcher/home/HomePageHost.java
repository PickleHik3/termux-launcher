package com.termux.app.launcher.home;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * What a {@link HomePage} asks of the activity: Home's leave and shown work, which used to run at
 * the wall's commit and settle, and the one view every Home page shares. The page calls
 * {@link #dismissControls()} on every off-front and {@link #leaving()} only when the wall
 * committed to a page that is not Home. Main thread only.
 */
public interface HomePageHost {

    /** Closes Home's open controls. */
    void dismissControls();

    /** Home's leave work: its widgets go to rest and the floating Home focus is released. */
    void leaving();

    /** Home is the front: its widgets come alive. */
    void shown();

    /** The kind the wall last committed to, or null before any commit. */
    @Nullable PageKind committedKind();

    /** The one view every Home page shows. */
    @NonNull View view();
}
