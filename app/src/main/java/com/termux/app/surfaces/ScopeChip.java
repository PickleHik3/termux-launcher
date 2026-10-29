package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.Element;
import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

import java.util.List;

/**
 * The editor's scope chip, {@code Global | <page name>} (ADR 0015 §6, #175): which cards carry
 * it. A card carries it only where its surface shows on two or more pages of the wall, since on
 * one page the two sides would mean the same thing. Pure: the caller says which pages there are
 * and whether a page's dress shows an element (its kind's visibility, or its own page layout's).
 */
public final class ScopeChip {

    private ScopeChip() {}

    /** Whether page {@code page}'s dress shows {@code element}. */
    public interface Shows {
        boolean shows(@NonNull Page page, @NonNull Element element);
    }

    /**
     * The elements a surface stands as, any of which showing puts the surface on the page; null
     * for the terminal's canvas, which only a Terminal page has.
     */
    @Nullable
    static Element[] elementsOf(@NonNull SurfaceSlot slot) {
        switch (slot) {
            case STATUS: return new Element[] {Element.STATUS};
            case DOCK: return new Element[] {Element.APPS, Element.AZ};
            case KEYBOARD: return new Element[] {Element.EXTRA_KEYS};
            case CANVAS:
            default:
                return null;
        }
    }

    /** How many pages of {@code pages} show the surface of {@code slot}. */
    public static int pagesShowing(@NonNull List<Page> pages, @NonNull SurfaceSlot slot,
                                   @NonNull Shows shows) {
        Element[] elements = elementsOf(slot);
        int count = 0;
        for (Page page : pages) {
            if (elements == null) {
                if (page.kind == PageKind.TERMINAL) count++;
                continue;
            }
            for (Element element : elements) {
                if (shows.shows(page, element)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /**
     * Whether a card carries the chip: a surface card when its surface shows on two or more pages;
     * a card with no surface of its own (the Appearance card's Look and All surfaces strip, which
     * reach every surface) when the wall has two or more pages.
     */
    public static boolean offers(@NonNull List<Page> pages, @Nullable SurfaceSlot slot,
                                 @NonNull Shows shows) {
        if (slot == null) return pages.size() >= 2;
        return pagesShowing(pages, slot, shows) >= 2;
    }
}
