package com.termux.app.place;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One variant's arrangement (the normal layout, or the one minimal mode stands in), in both
 * orientations, as one immutable value: what the Layout editor took
 * a copy of on entry, what its revert puts back, and — folded to a string — how it knows a bar has
 * been moved since. The three sizes ride along, so a dragged dock, keyboard or chin is exactly as
 * unsaved as a moved bar, and so does the keyboard element's on/off switch.
 *
 * <p>Read and written through {@link PlaceLayoutStore}'s own accessors rather than the raw keys, so
 * a restore writes the value the layout was resolving to whether or not it had a key of its own.
 * That materialises a key the global value was answering for, at the value it was answering with:
 * the same arrangement, spelled out.
 *
 * <p>A snapshot remembers its variant and restores onto it, whatever the store is on by then, so a
 * Discard puts back the layout that was being edited and never the other one.
 *
 * <p>Pure: a store in, a value out, no views, so revert and dirtiness are testable without a window.
 */
public final class PlaceArrangeSnapshot {

    /** One orientation's arrangement, exactly as the store answers for it. */
    private static final class Entry {
        final PlaceOrientation orientation;
        /**
         * Every element's slot — edge, order, hidden, under the keyboard — by
         * {@link Element#ordinal()}, read and written whole through {@link PlaceLayoutStore#slot}
         * and {@link PlaceLayoutStore#setSlot}.
         *
         * <p>It used to be spelled field by field, with the pinned apps and the extra keys read
         * as the old three-way {@code RowPlacement}. That spelling has no top row, so a bar on the
         * top edge was captured as the bottom: Undo and Discard put it back on the wrong edge, and
         * the signature could not tell a move between the two apart. The slot is the whole truth
         * about where an element stands, so it is what is kept.
         */
        final Slot[] slots;
        final PlaceLayout.KeyboardMode keyboardMode;
        final PlaceLayout.KeyboardForm keyboardForm;
        final int widgetColumns;
        final int widgetRows;
        final float dockHeightScale;
        final float keyboardHeightScale;
        final int keyboardChinDp;

        Entry(@NonNull PlaceLayoutStore places, @NonNull PlaceOrientation orientation) {
            this.orientation = orientation;
            slots = new Slot[Element.values().length];
            for (Element element : Element.values())
                slots[element.ordinal()] = places.slot(orientation, element);
            keyboardMode = places.keyboardMode(orientation);
            keyboardForm = places.keyboardForm(orientation);
            widgetColumns = places.widgetColumns(orientation);
            widgetRows = places.widgetRows(orientation);
            dockHeightScale = places.dockHeightScale(orientation);
            keyboardHeightScale = places.keyboardHeightScale(orientation);
            keyboardChinDp = places.keyboardChinDp(orientation);
        }

        void restore(@NonNull PlaceLayoutStore places) {
            for (Element element : Element.values())
                places.setSlot(orientation, element, slots[element.ordinal()]);
            places.setKeyboardMode(orientation, keyboardMode);
            places.setKeyboardForm(orientation, keyboardForm);
            places.setWidgetColumns(orientation, widgetColumns);
            places.setWidgetRows(orientation, widgetRows);
            places.setDockHeightScale(orientation, dockHeightScale);
            places.setKeyboardHeightScale(orientation, keyboardHeightScale);
            places.setKeyboardChinDp(orientation, keyboardChinDp);
        }

        void appendTo(@NonNull StringBuilder out) {
            out.append(orientation.storageValue()).append(':');
            for (Slot slot : slots) out.append(slot).append(',');
            out.append(keyboardMode).append(',')
                .append(keyboardForm).append(',')
                .append(widgetColumns).append('x').append(widgetRows).append(',')
                .append(dockHeightScale).append(',')
                .append(keyboardHeightScale).append(',')
                .append(keyboardChinDp);
            out.append('|');
        }
    }

    @NonNull private final LayoutVariant mVariant;
    @NonNull private final List<Entry> mEntries;
    /** The keyboard element, on or off: one switch for both orientations, captured once. */
    private final boolean mKeyboardShown;

    private PlaceArrangeSnapshot(@NonNull LayoutVariant variant, @NonNull List<Entry> entries,
                                 boolean keyboardShown) {
        mVariant = variant;
        mEntries = Collections.unmodifiableList(entries);
        mKeyboardShown = keyboardShown;
    }

    /** The layout the store is on, in both orientations. */
    @NonNull
    public static PlaceArrangeSnapshot capture(@NonNull PlaceLayoutStore places) {
        return capture(places, places.activeVariant());
    }

    /** Both orientations of one variant: a rotation mid-edit moves which one the editor writes. */
    @NonNull
    public static PlaceArrangeSnapshot capture(@NonNull PlaceLayoutStore places,
                                               @NonNull LayoutVariant variant) {
        PlaceLayoutStore layout = places.forVariant(variant);
        List<Entry> entries = new ArrayList<>();
        for (PlaceOrientation orientation : PlaceOrientation.values())
            entries.add(new Entry(layout, orientation));
        return new PlaceArrangeSnapshot(variant, entries, places.isKeyboardShown());
    }

    /** The variant this snapshot was taken of, and restores onto. */
    @NonNull
    public LayoutVariant variant() {
        return mVariant;
    }

    /** Puts the arrangement back the way {@link #capture} found it. */
    public void restore(@NonNull PlaceLayoutStore places) {
        PlaceLayoutStore layout = places.forVariant(mVariant);
        for (Entry entry : mEntries) entry.restore(layout);
        places.setKeyboardShown(mKeyboardShown);
    }

    /** The whole arrangement as one string, for the editor's unsaved-changes comparison. */
    @NonNull
    public String signature() {
        StringBuilder out = new StringBuilder(128);
        out.append(mVariant).append('/');
        for (Entry entry : mEntries) entry.appendTo(out);
        out.append("keyboard:").append(mKeyboardShown);
        return out.toString();
    }
}
