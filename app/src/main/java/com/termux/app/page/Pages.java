package com.termux.app.page;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The host's page registry and the recorded front (ADR 0030 sections 1 and 3). Main thread only.
 * A wall commit ({@link #committed}) runs the applied page's off-front at once when the commit
 * leaves it; a wall landing records the front here ({@link #landed}) and asks for the PAGE_FRONT
 * frame step, whose {@link #applyFront} runs the new page's on-front at the structure point after
 * the landing. Readers see the new front from the landing on, before the step has run.
 */
public final class Pages {

    @NonNull private final PageHost mHost;
    @NonNull private final Runnable mRequestFrontStep;
    @NonNull private final Map<Integer, Page> mPages = new HashMap<>();
    @Nullable private BooleanSupplier mHomeDefault;

    private int mFrontId = -1;
    @Nullable private PageKind mFrontKind;
    private int mCommittedId = -1;
    @Nullable private PageKind mCommittedKind;
    private float mProgress;
    /** The page whose on-front last ran; held so a removed page still gets its off-front. */
    @Nullable private Page mApplied;

    public Pages(@NonNull PageHost host, @NonNull Runnable requestFrontStep) {
        mHost = host;
        mRequestFrontStep = requestFrontStep;
    }

    @NonNull
    public PageHost host() {
        return mHost;
    }

    public void put(int pageId, @NonNull Page page) {
        mPages.put(pageId, page);
    }

    public void remove(int pageId) {
        mPages.remove(pageId);
    }

    @Nullable
    public Page page(int pageId) {
        return mPages.get(pageId);
    }

    /**
     * Records the page the wall committed to, when its slide began to settle there. When that page
     * is not the applied one, the applied page's off-front runs now and the landing's front step
     * runs only the new page's on-front.
     */
    public void committed(int pageId, @NonNull PageKind kind) {
        mCommittedId = pageId;
        mCommittedKind = kind;
        Page applied = mApplied;
        if (applied != null && mPages.get(pageId) != applied) {
            mApplied = null;
            applied.offFront();
        }
    }

    public int committedId() {
        return mCommittedId;
    }

    @Nullable
    public PageKind committedKind() {
        return mCommittedKind;
    }

    /**
     * Records the front (and the commit with it); asks for the front step unless the front is
     * unchanged and already applied.
     */
    public void landed(int pageId, @NonNull PageKind kind) {
        mCommittedId = pageId;
        mCommittedKind = kind;
        if (pageId == mFrontId && kind == mFrontKind && mApplied == front()) return;
        mFrontId = pageId;
        mFrontKind = kind;
        mRequestFrontStep.run();
    }

    public int frontId() {
        return mFrontId;
    }

    @Nullable
    public PageKind frontKind() {
        return mFrontKind;
    }

    /** The registered page for the recorded front, or null for a page not behind the contract. */
    @Nullable
    public Page front() {
        return mFrontId < 0 ? null : mPages.get(mFrontId);
    }

    /** How far the wall has moved from the front page, clamped to 0..1; 0 at rest. */
    public void travelled(float progress) {
        mProgress = progress < 0f ? 0f : Math.min(progress, 1f);
    }

    public float progress() {
        return mProgress;
    }

    /**
     * The structure point, run by the PAGE_FRONT step: when the recorded front's page differs from
     * the one last applied, that one's off-front runs once and then the new one's on-front. Does
     * nothing when nothing changed.
     */
    public void applyFront() {
        Page now = front();
        if (now == mApplied) return;
        Page old = mApplied;
        mApplied = now;
        if (old != null) old.offFront();
        if (now != null) now.onFront();
    }

    /** The wall's default Back, Home page 1; returns whether it moved. */
    public void setHomeDefault(@NonNull BooleanSupplier toHomePageOne) {
        mHomeDefault = toHomePageOne;
    }

    /**
     * The Back chain's tail, registered last in the host's overlay registry (everything raised over
     * the page comes first): the front page's Back, then the wall's default. A front that is not a
     * contract page keeps today's Back, so this returns false.
     */
    public boolean onBack() {
        Page front = front();
        if (front == null) return false;
        if (front.onBack()) return true;
        BooleanSupplier home = mHomeDefault;
        return home != null && home.getAsBoolean();
    }
}
