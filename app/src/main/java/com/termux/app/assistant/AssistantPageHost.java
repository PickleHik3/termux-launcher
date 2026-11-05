package com.termux.app.assistant;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * What the wall asks for to show the Assistant page (ADR 0022): the one view the page stands on.
 * The wall builds it when the wall holds an Assistant page and drops it when the page goes; the
 * chat's own host replaces {@link #DEFAULT} without {@code TermuxActivity} growing.
 */
public interface AssistantPageHost {

    /** The empty page: a container with the page's ground. */
    @NonNull AssistantPageHost DEFAULT = AssistantPageView::new;

    /** The view for the Assistant page, built once for the page's life on the wall. */
    @NonNull View createPageView(@NonNull Context context);
}
