package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.wall.WallPages.PageKind;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Which of the launcher's saved arrangements a layout belongs to. The shared layout has two, the
 * normal one and the one minimal mode stands in (CONTEXT.md), worn by every page that holds no
 * layout of its own (ADR 0009). A page may hold a <b>page layout</b> of its own, keyed by its page
 * id ({@link #page}, ADR 0015 §3): a whole per-orientation layout, kept beside {@link
 * PlaceOrientation} in {@link PlaceLayoutStore}'s keys under {@code page<id>.}.
 *
 * <p>{@link #HOME} is the old opt-in Home layout's scope. After the upgrade to page layouts
 * (version 16) no page wears its arrangement; it stays the scope of what is Home's alone whichever
 * layout asks (the widget grid, always on) and the upgrade's source.
 *
 * <p>A class with interned values rather than an enum, so a page's layout is a variant like the
 * fixed three and {@code ==} still tells two apart: {@link #page} hands out one instance per page
 * id and kind.
 */
public final class LayoutVariant {

    public static final LayoutVariant NORMAL = new LayoutVariant("NORMAL", "", -1, null);
    public static final LayoutVariant MINIMAL = new LayoutVariant("MINIMAL", "minimal.", -1, null);
    public static final LayoutVariant HOME = new LayoutVariant("HOME", "home.", -1, PageKind.HOME);

    /** The fixed variants, in their old enum order: what the migrations walk. */
    private static final LayoutVariant[] FIXED = {NORMAL, MINIMAL, HOME};

    private static final ConcurrentHashMap<String, LayoutVariant> PAGES = new ConcurrentHashMap<>();

    @NonNull private final String mName;
    @NonNull private final String mKeyScope;
    private final int mPageId;
    /** The kind of page this layout dresses; null for the shared layouts (the Terminal's kind). */
    private final PageKind mKind;

    private LayoutVariant(@NonNull String name, @NonNull String keyScope, int pageId,
                          PageKind kind) {
        mName = name;
        mKeyScope = keyScope;
        mPageId = pageId;
        mKind = kind;
    }

    /** The fixed variants (normal, minimal, Home), never a page's. */
    @NonNull
    public static LayoutVariant[] values() {
        return FIXED.clone();
    }

    /** The Workspace variant the launcher stands in while minimal mode is {@code minimal}. */
    @NonNull
    public static LayoutVariant of(boolean minimal) {
        return minimal ? MINIMAL : NORMAL;
    }

    /**
     * The page layout of page {@code pageId}, a page of {@code kind}: one instance per id and
     * kind, so {@code ==} holds. A Display page dresses as a Terminal page does.
     */
    @NonNull
    public static LayoutVariant page(int pageId, @NonNull PageKind kind) {
        PageKind dressed = kind == PageKind.HOME ? PageKind.HOME : PageKind.TERMINAL;
        String key = dressed.name() + pageId;
        LayoutVariant variant = PAGES.get(key);
        if (variant != null) return variant;
        LayoutVariant made = new LayoutVariant("PAGE_" + pageId, "page" + pageId + ".", pageId,
            dressed);
        LayoutVariant raced = PAGES.putIfAbsent(key, made);
        return raced != null ? raced : made;
    }

    /** Whether this is a page's own layout ({@link #page}). */
    public boolean isPage() {
        return mPageId >= 0;
    }

    /** The page this layout belongs to, or -1 for the shared layouts and Home's. */
    public int pageId() {
        return mPageId;
    }

    /**
     * Whether this layout keeps which elements show in its own slots rather than reading its
     * kind's: the minimal layout, and every page layout (ADR 0015 §3).
     */
    public boolean holdsOwnVisibility() {
        return this == MINIMAL || isPage();
    }

    /** Whether this layout dresses Home pages: the old Home layout, or a Home page's own. */
    public boolean isHomeKind() {
        return mKind == PageKind.HOME;
    }

    /** The kind of page this layout dresses: Home's, else the Terminal's. */
    @NonNull
    public PageKind kind() {
        return mKind == PageKind.HOME ? PageKind.HOME : PageKind.TERMINAL;
    }

    /**
     * Whether this layout reads its sizes from the normal layout's keys ({@link
     * PlaceLayoutStore#sizesLinked}): Home's and every page's. Sizes stay linked everywhere.
     */
    boolean linksSizes() {
        return this == HOME || isPage();
    }

    /**
     * Whether the Layout editor offers this element on this variant, and whether it may ever be
     * shown there: whether a page on it wears the element, as the dress answers ({@link
     * com.termux.app.chrome.DressResolver#wornOn}). The Home layout keeps the status bar and the
     * dock's rows (the pinned apps and the A–Z index); the extra keys are the Workspace's alone.
     * The keyboard is not an element with a slot and is offered on every variant.
     */
    public boolean offers(@NonNull Element element) {
        return com.termux.app.chrome.DressResolver.wornOn(this, element);
    }

    /**
     * The key segment that scopes this variant's keys: none for the normal layout, whose keys are
     * the ones the launcher has always written, {@code minimal.} and {@code home.} for the others,
     * {@code page<id>.} for a page's own.
     */
    @NonNull
    String keyScope() {
        return mKeyScope;
    }

    @NonNull
    public String name() {
        return mName;
    }

    @NonNull
    @Override
    public String toString() {
        return mName;
    }
}
