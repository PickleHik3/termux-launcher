package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.LookGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The two chips at the top of every card (ADR 0017): Where it applies (Everywhere, the kind of
 * the page in front (ADR 0018 §3), or that page) and What it applies to (All glass or one
 * element). Together they name the grid cell a card's rows read and write. Pure: the caller says
 * which page the editor shows, its kind and which elements it has.
 */
public final class EditorChips {

    /** One option of the Where chip: Everywhere, a kind of page, or one page. */
    public static final class Where {
        public static final Where EVERYWHERE = new Where(LookGrid.NO_PAGE, null);

        public final int page;
        @Nullable public final LookGrid.Kind kind;

        private Where(int page, @Nullable LookGrid.Kind kind) {
            this.page = page;
            this.kind = kind;
        }

        @NonNull
        public static Where page(int pageId) {
            return pageId < 0 ? EVERYWHERE : new Where(pageId, null);
        }

        @NonNull
        public static Where kind(@Nullable LookGrid.Kind kind) {
            return kind == null ? EVERYWHERE : new Where(LookGrid.NO_PAGE, kind);
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Where)) return false;
            Where that = (Where) other;
            return page == that.page && kind == that.kind;
        }

        @Override public int hashCode() {
            return page * 31 + (kind == null ? 0 : kind.hashCode());
        }

        @NonNull @Override public String toString() {
            return kind != null ? kind.key + " pages" : page < 0 ? "everywhere" : "page " + page;
        }
    }

    /** The page the Where chip stands on, or {@link LookGrid#NO_PAGE} for Everywhere or a kind. */
    public final int page;
    /** The kind the Where chip stands on, or null for Everywhere or a page. */
    @Nullable public final LookGrid.Kind kind;
    /** The element the What chip stands on, or null for All glass. */
    @Nullable public final LookGrid.Element element;

    public EditorChips(int page, @Nullable LookGrid.Element element) {
        this(Where.page(page), element);
    }

    public EditorChips(@NonNull Where where, @Nullable LookGrid.Element element) {
        this.page = where.page < 0 ? LookGrid.NO_PAGE : where.page;
        this.kind = where.page >= 0 ? null : where.kind;
        this.element = element;
    }

    /** The element a card tunes, or null for the page cards and the Appearance card. */
    @Nullable
    public static LookGrid.Element elementOf(@Nullable EditorCard card) {
        return card == null || card.slot == null ? null : card.slot.element;
    }

    /** Whether a card is a page card (Home, Terminal page): it opens on its page. */
    public static boolean isPageCard(@Nullable EditorCard card) {
        return card == EditorCard.HOME || card == EditorCard.WORKSPACE;
    }

    /**
     * Where a card opens: an element's card on (Everywhere, its element), a page card on (its
     * page's kind, All glass), the Appearance card on (Everywhere, All glass).
     */
    @NonNull
    public static EditorChips openFor(@Nullable EditorCard card, @Nullable LookGrid.Kind kind) {
        // A page card opens on its page's kind: pages of a kind look the same (ADR 0020 §2).
        if (isPageCard(card)) return new EditorChips(Where.kind(kind), null);
        return new EditorChips(LookGrid.NO_PAGE, elementOf(card));
    }

    /** Whether the card offers the What chip; the Appearance card does not. */
    public static boolean offersWhat(@Nullable EditorCard card) {
        return card != null && card != EditorCard.APPEARANCE;
    }

    /**
     * The Where chip's options: Everywhere, then the kind of the page the editor shows when it
     * has one.
     */
    @NonNull
    public static List<Where> whereOptions(int pageId, @Nullable LookGrid.Kind kind) {
        List<Where> out = new ArrayList<>();
        out.add(Where.EVERYWHERE);
        // No "This page": the page tier is retired (ADR 0020 §2).
        if (kind != null) out.add(Where.kind(kind));
        return out;
    }

    /** The Where option these chips stand on. */
    @NonNull
    public Where where() {
        return kind != null ? Where.kind(kind) : Where.page(page);
    }

    /**
     * The What chip's options: All glass (null), then the card's element; a page card offers
     * every element its page shows ({@code pageElements}); the Appearance card none.
     */
    @NonNull
    public static List<LookGrid.Element> whatOptions(@Nullable EditorCard card,
                                                     @NonNull List<LookGrid.Element> pageElements) {
        if (!offersWhat(card)) return Collections.emptyList();
        List<LookGrid.Element> out = new ArrayList<>();
        out.add(null);
        LookGrid.Element own = elementOf(card);
        if (own != null) out.add(own);
        else
            for (LookGrid.Element element : pageElements)
                if (!out.contains(element)) out.add(element);
        return out;
    }

    @NonNull
    public EditorChips withPage(int pageId) {
        return new EditorChips(pageId, element);
    }

    @NonNull
    public EditorChips withWhere(@NonNull Where where) {
        return new EditorChips(where, element);
    }

    @NonNull
    public EditorChips withElement(@Nullable LookGrid.Element value) {
        return new EditorChips(where(), value);
    }

    /** Whether Where stands on a page rather than Everywhere or a kind. */
    public boolean onPage() {
        return page >= 0;
    }

    /** Whether Where stands on a kind of page. */
    public boolean onKind() {
        return kind != null;
    }

    /**
     * The cell the chips name for {@code property}: a property without an element column (Margin,
     * Vibrancy) drops the element, as the grid stores it, and having no kind row either, reads
     * and writes everywhere under a kind.
     */
    @NonNull
    public LookGrid.Key cell(@NonNull LookGrid.Property property) {
        if (kind != null)
            return property.perElement ? new LookGrid.Key(property, element, kind)
                : new LookGrid.Key(property, null, LookGrid.NO_PAGE);
        return new LookGrid.Key(property, property.perElement ? element : null, page);
    }

    /**
     * Whether the chips grey {@code property}'s row: a property with no element column (and no
     * kind row) while the What chip names an element or the Where chip a kind (the row then
     * writes All glass and says so).
     */
    public boolean elementIgnored(@NonNull LookGrid.Property property) {
        // A kind holds every property now that it stands for the page tier (ADR 0020 §2).
        return element != null && !property.perElement;
    }

    /** Whether these chips stand where {@code card} opens: the editor's plain writes apply. */
    public boolean isOpening(@Nullable EditorCard card) {
        EditorChips open = openFor(card, kind);
        return open.page == page && open.kind == kind && open.element == element;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof EditorChips)) return false;
        EditorChips that = (EditorChips) other;
        return page == that.page && kind == that.kind && element == that.element;
    }

    @Override public int hashCode() {
        return (page * 31 + (kind == null ? 0 : kind.hashCode())) * 31
            + (element == null ? 0 : element.hashCode());
    }

    @NonNull @Override public String toString() {
        return where() + " · " + (element == null ? "all glass" : element.key);
    }
}
