package com.termux.app.assistant;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.page.Page;
import com.termux.app.page.PageHost;

/**
 * What the wall asks for to show the Assistant page (ADR 0022): the page, whose one view it stands on.
 * The wall builds it when the wall holds an Assistant page and drops it when the page goes; the
 * chat's own host replaces {@link #DEFAULT} without {@code TermuxActivity} growing.
 */
public interface AssistantPageHost {

    /** The empty page: a container with the page's ground. */
    @NonNull AssistantPageHost DEFAULT = AssistantPageView::new;

    /** The Assistant page, built once for its life on the wall; it asks its host through {@code host}. */
    @NonNull Page createPage(@NonNull Context context, @NonNull PageHost host);
}
