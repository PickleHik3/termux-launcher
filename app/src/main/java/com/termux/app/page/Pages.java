package com.termux.app.page;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    /** The pages of a cell that holds several, one in front at a time: the Workspace's windows. */
    public interface Stops {
        /** The page in front inside the cell, or null when it holds none. */
        @Nullable Page current();

        /**
         * The page that declares the cell's glass: the one in front, or any the cell holds while
         * none is in front for a moment, so a window closing never lets go of the others' glass.
         */
        @Nullable
        default Page glassPage() { return current(); }
    }

    @NonNull private final PageHost mHost;
    @NonNull private final Runnable mRequestFrontStep;
    @NonNull private final Map<Integer, Page> mPages = new HashMap<>();
    @NonNull private final Map<Integer, Stops> mStops = new HashMap<>();
    @Nullable private BooleanSupplier mHomeDefault;
    @Nullable private GlassService mGlass;
    private static final String GLASS_OWNER = "pages";

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
        declareGlass(true);
    }

    /** Puts the pages of cell {@code cellId} behind {@code stops}; {@link #resolve} asks it. */
    public void putStops(int cellId, @NonNull Stops stops) {
        mStops.put(cellId, stops);
    }

    public void remove(int pageId) {
        mPages.remove(pageId);
        mStops.remove(pageId);
        declareGlass(true);
    }

    /** The glass service the pages' entries register with; null lets them all go. */
    public void setGlass(@Nullable GlassService glass) {
        if (mGlass != null && mGlass != glass) mGlass.undeclare(GLASS_OWNER);
        mGlass = glass;
        declareGlass(false);
    }

    /**
     * Declares the glass of every registered page and of each cell's current page: it lives as
     * long as the page is registered, not as long as it is in front. Several Home pages share one
     * view and declare the same entries, so the first of an element wins.
     */
    public void declareGlass() {
        declareGlass(false);
    }

    private void declareGlass(boolean redressNew) {
        GlassService glass = mGlass;
        if (glass == null) return;
        List<GlassEntry> all = new ArrayList<>();
        for (Page page : mPages.values()) page.declareGlass(all);
        for (Stops stops : mStops.values()) {
            Page page = stops.glassPage();
            if (page != null) page.declareGlass(all);
        }
        HashSet<String> seen = new HashSet<>();
        List<GlassEntry> list = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            if (seen.add(all.get(i).element)) list.add(all.get(i));
        }
        glass.declare(GLASS_OWNER, list, redressNew);
    }

    @Nullable
    public Page page(int pageId) {
        return mPages.get(pageId);
    }

    /**
     * A page of {@code kind}: the committed page when it is of that kind, else any registered page
     * or cell's current page of it; null when none is. Pages of a kind share one rest, so any will do.
     */
    @Nullable
    public Page anyOf(@NonNull PageKind kind) {
        Page committed = mCommittedKind == null ? null : resolve(mCommittedId);
        if (committed != null && committed.kind() == kind) return committed;
        for (Page page : mPages.values()) {
            if (page.kind() == kind) return page;
        }
        for (Stops stops : mStops.values()) {
            Page page = stops.current();
            if (page != null && page.kind() == kind) return page;
        }
        return null;
    }

    /** The page put under {@code id}, else the current page of the stops under it, else null. */
    @Nullable
    private Page resolve(int id) {
        Page page = mPages.get(id);
        if (page != null) return page;
        Stops stops = mStops.get(id);
        return stops == null ? null : stops.current();
    }

    /**
     * The page in front inside a cell changed (a window step, a window opened or closed): asks for
     * the front step when that made the front differ from the applied page. Does nothing otherwise.
     */
    public void innerChanged() {
        if (front() != mApplied) mRequestFrontStep.run();
    }

    /**
     * Records the page the wall committed to, when its slide began to settle there. When that page
     * is not the applied one, the applied page's off-front runs now and the landing's front step
     * runs only the new page's on-front.
     */
    public void committed(int pageId, @NonNull PageKind kind) {
        recordCommit(pageId, kind);
        Page applied = mApplied;
        if (applied != null && resolve(pageId) != applied) {
            mApplied = null;
            applied.offFront();
        }
    }

    /**
     * Records the commit alone, so readers see the new kind before the landing's handler runs;
     * {@link #committed} then runs the off-front.
     */
    public void recordCommit(int pageId, @NonNull PageKind kind) {
        mCommittedId = pageId;
        mCommittedKind = kind;
    }

    /** Whether the wall has committed to a page of {@code kind}; mid-slide it follows the commit. */
    public boolean isCommitted(@NonNull PageKind kind) {
        return mCommittedKind == kind;
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
        // Kind-only rows register their pages under negative ids: no kind is what means no front.
        return mFrontKind == null ? null : resolve(mFrontId);
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
