package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

/** Pure legal-state and one-shot staging bookkeeping for category expansion. */
public final class AppDrawerCategoryExpansionModel {
    public enum State { OVERVIEW, EXPANDING, EXPANDED, COLLAPSE_DRAGGING, COLLAPSING }

    public static final int NONE = 0;
    public static final int RELEASE_OVERVIEW = 1;
    public static final int BIND_DETAIL = 1 << 1;
    public static final int RELEASE_DETAIL = 1 << 2;
    public static final int BIND_OVERVIEW = 1 << 3;

    /**
     * The one staging boundary. Overview previews are released and the detail grid is bound at the
     * same crossing — release first, bind second — so the two icon sets still never hold the shared
     * rendered-icon cache at once, but the detail content exists for almost the whole expansion
     * instead of popping in at the end of it.
     */
    public static final float STAGING_BOUNDARY = 0.15f;

    @NonNull private State state = State.OVERVIEW;
    @Nullable private String selectedId;
    private float progress;
    private boolean overviewReleased;
    private boolean detailBound;

    public boolean expand(@NonNull String categoryId) {
        if (state != State.OVERVIEW || categoryId.isEmpty()) return false;
        selectedId = categoryId;
        state = State.EXPANDING;
        progress = 0f;
        overviewReleased = false;
        detailBound = false;
        return true;
    }

    public boolean collapse() {
        if (state == State.OVERVIEW || selectedId == null) return false;
        state = State.COLLAPSING;
        return true;
    }

    public boolean beginCollapseDrag() {
        if (state != State.EXPANDED) return false;
        state = State.COLLAPSE_DRAGGING;
        return true;
    }

    public void finishCollapseDrag(boolean commit) {
        if (state != State.COLLAPSE_DRAGGING) return;
        state = commit ? State.COLLAPSING : State.EXPANDING;
    }

    /** Returns one-shot release/bind work while crossing the plan's staging boundaries. */
    public int setProgress(float value) {
        float next = AppDrawerTransitionGeometry.clamp01(value);
        int events = NONE;
        if (next >= STAGING_BOUNDARY && !overviewReleased) {
            overviewReleased = true;
            events |= RELEASE_OVERVIEW;
        }
        if (next >= STAGING_BOUNDARY && !detailBound) {
            detailBound = true;
            events |= BIND_DETAIL;
        }
        if (next < STAGING_BOUNDARY && detailBound) {
            detailBound = false;
            events |= RELEASE_DETAIL;
        }
        if (next < STAGING_BOUNDARY && overviewReleased) {
            overviewReleased = false;
            events |= BIND_OVERVIEW;
        }
        progress = next;
        return events;
    }

    public void settle() {
        if (progress >= 1f && state == State.EXPANDING) state = State.EXPANDED;
        if (progress <= 0f && state == State.COLLAPSING) reset();
    }

    public void queryStarted() { reset(); }
    public void teardown() { reset(); }

    public boolean reconcile(@NonNull Collection<String> nonEmptyCategoryIds) {
        if (selectedId == null || nonEmptyCategoryIds.contains(selectedId)) return true;
        reset();
        return false;
    }

    private void reset() {
        state = State.OVERVIEW;
        selectedId = null;
        progress = 0f;
        overviewReleased = false;
        detailBound = false;
    }

    @NonNull public State state() { return state; }
    @Nullable public String selectedId() { return selectedId; }
    public float progress() { return progress; }
    public boolean overviewReleased() { return overviewReleased; }
    public boolean detailBound() { return detailBound; }
}
