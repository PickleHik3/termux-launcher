package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.DockStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One variant's arrangement (the normal layout, or the one minimal mode stands in), in both
 * orientations, as one immutable value: what the Layout editor took
 * a copy of on entry, what its revert puts back, and — folded to a string — how it knows a bar has
 * been moved since. The three sizes ride along, so a dragged dock, keyboard or chin is exactly as
 * unsaved as a moved bar (on the Home layout they are the normal layout's, linked, so its
 * snapshot captures and restores the values both halves share, #84), and so does the keyboard
 * element's on/off switch, with Home's own
 * (always on), which a Home page edits on whichever layout it wears. Each layout's Dock design
 * rides along too, with its Search field and Drawer tile toggles, so a design picked or a toggle
 * flipped in the editor is undone and asked about like a move.
 *
 * <p>Read and written through {@link PlaceLayoutStore}'s own accessors rather than the raw keys, so
 * a restore writes the value the layout was resolving to whether or not it had a key of its own.
 * That materialises a key the global value was answering for, at the value it was answering with:
 * the same arrangement, spelled out. The keyboard mode is the exception: it is put back as stored
 * or as absent, so a default that later changes is not kept as a choice. The signature still
 * compares the mode it resolves to, like every other field.
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
        /**
         * Whether the keyboard mode had a key of its own. The one field a restore leaves absent
         * where it was absent: the mode's default has moved (portrait was Resize, now Overlay),
         * and a revert that spelled it out kept the old default as though it were a choice.
         */
        final boolean keyboardModeStored;
        final PlaceLayout.KeyboardForm keyboardForm;
        final int widgetColumns;
        final int widgetRows;
        final float dockHeightScale;
        final float keyboardHeightScale;
        final int keyboardChinDp;
        /** The Wall strip's first row, in dp: a size like the dock's, so its handle undoes too. */
        final int wallStripHeightDp;

        Entry(@NonNull PlaceLayoutStore places, @NonNull PlaceOrientation orientation) {
            this.orientation = orientation;
            slots = new Slot[Element.values().length];
            for (Element element : Element.values())
                slots[element.ordinal()] = places.slot(orientation, element);
            keyboardMode = places.keyboardMode(orientation);
            keyboardModeStored = places.hasStoredKeyboardMode(orientation);
            keyboardForm = places.keyboardForm(orientation);
            widgetColumns = places.widgetColumns(orientation);
            widgetRows = places.widgetRows(orientation);
            dockHeightScale = places.dockHeightScale(orientation);
            keyboardHeightScale = places.keyboardHeightScale(orientation);
            keyboardChinDp = places.keyboardChinDp(orientation);
            wallStripHeightDp = places.wallStripHeightDp(orientation);
        }

        /** {@code base} with the four sizes {@code sizes} holds. */
        Entry(@NonNull Entry base, @NonNull Entry sizes) {
            orientation = base.orientation;
            slots = base.slots.clone();
            keyboardMode = base.keyboardMode;
            keyboardModeStored = base.keyboardModeStored;
            keyboardForm = base.keyboardForm;
            widgetColumns = base.widgetColumns;
            widgetRows = base.widgetRows;
            dockHeightScale = sizes.dockHeightScale;
            keyboardHeightScale = sizes.keyboardHeightScale;
            keyboardChinDp = sizes.keyboardChinDp;
            wallStripHeightDp = sizes.wallStripHeightDp;
        }

        void restore(@NonNull PlaceLayoutStore places) {
            for (Element element : Element.values())
                places.setSlot(orientation, element, slots[element.ordinal()]);
            if (keyboardModeStored) places.setKeyboardMode(orientation, keyboardMode);
            else places.clearKeyboardMode(orientation);
            places.setKeyboardForm(orientation, keyboardForm);
            places.setWidgetColumns(orientation, widgetColumns);
            places.setWidgetRows(orientation, widgetRows);
            places.setDockHeightScale(orientation, dockHeightScale);
            places.setKeyboardHeightScale(orientation, keyboardHeightScale);
            places.setKeyboardChinDp(orientation, keyboardChinDp);
            places.setWallStripHeightDp(orientation, wallStripHeightDp);
        }

        void appendTo(@NonNull StringBuilder out) {
            out.append(orientation.storageValue()).append(':');
            for (Slot slot : slots) out.append(slot).append(',');
            out.append(keyboardMode).append(',')
                .append(keyboardForm).append(',')
                .append(widgetColumns).append('x').append(widgetRows).append(',')
                .append(dockHeightScale).append(',')
                .append(keyboardHeightScale).append(',')
                .append(keyboardChinDp).append(',')
                .append(wallStripHeightDp);
            out.append('|');
        }
    }

    @NonNull private final LayoutVariant mVariant;
    @NonNull private final List<Entry> mEntries;
    /** The keyboard element, on or off: one switch for both orientations, captured once. */
    private final boolean mKeyboardShown;
    /**
     * Home's keyboard element (always on), which a Home page edits on whichever layout it wears,
     * so every variant's snapshot keeps it.
     */
    private final boolean mHomeAlwaysOn;
    /** The layout's Dock design as picked, both orientations. */
    @NonNull private final DockStyle mDockStyle;
    /** Whether the layout turns the Search field on beside its design, both orientations. */
    private final boolean mSearchField;
    /** Whether the layout turns the Drawer tile on beside its design, both orientations. */
    private final boolean mDrawerTile;

    private PlaceArrangeSnapshot(@NonNull LayoutVariant variant, @NonNull List<Entry> entries,
                                 boolean keyboardShown, boolean homeAlwaysOn,
                                 @NonNull DockStyle dockStyle, boolean searchField,
                                 boolean drawerTile) {
        mVariant = variant;
        mEntries = Collections.unmodifiableList(entries);
        mKeyboardShown = keyboardShown;
        mHomeAlwaysOn = homeAlwaysOn;
        mDockStyle = dockStyle;
        mSearchField = searchField;
        mDrawerTile = drawerTile;
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
        // The variant's own keyboard element: the Home layout's is always on, not the switch.
        return new PlaceArrangeSnapshot(variant, entries, layout.isKeyboardShown(),
            layout.isHomeKeyboardAlwaysOn(), layout.dockStyle(), layout.searchField(),
            layout.drawerTile());
    }

    /**
     * This snapshot with the dock's, the keyboard's, the chin's and the Strip row's sizes {@code
     * other} holds, orientation by orientation: for a snapshot of a layout whose sizes are linked
     * to {@code other}'s ({@link PlaceLayoutStore#sizesLinked}), taken after they may have been
     * edited through it, so both put back the sizes as they were first found.
     */
    @NonNull
    public PlaceArrangeSnapshot withSizesOf(@NonNull PlaceArrangeSnapshot other) {
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : mEntries) {
            Entry sizes = entry;
            for (Entry candidate : other.mEntries) {
                if (candidate.orientation == entry.orientation) sizes = candidate;
            }
            entries.add(new Entry(entry, sizes));
        }
        return new PlaceArrangeSnapshot(mVariant, entries, mKeyboardShown, mHomeAlwaysOn,
            mDockStyle, mSearchField, mDrawerTile);
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
        layout.setHomeKeyboardAlwaysOn(mHomeAlwaysOn);
        layout.setKeyboardShown(mKeyboardShown);
        layout.setDockStyle(mDockStyle);
        layout.setSearchField(mSearchField);
        layout.setDrawerTile(mDrawerTile);
    }

    /** The whole arrangement as one string, for the editor's unsaved-changes comparison. */
    @NonNull
    public String signature() {
        StringBuilder out = new StringBuilder(128);
        out.append(mVariant).append('/');
        for (Entry entry : mEntries) entry.appendTo(out);
        out.append("keyboard:").append(mKeyboardShown)
            .append("|always:").append(mHomeAlwaysOn)
            .append("|dock:").append(mDockStyle.storageValue())
            .append("|search:").append(mSearchField)
            .append("|drawer:").append(mDrawerTile);
        return out.toString();
    }
}
