package com.termux.app.place;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The shared arrangement, in both orientations, as one immutable value: what the Layout editor took
 * a copy of on entry, what its revert puts back, and — folded to a string — how it knows a bar has
 * been moved since. The three sizes ride along, so a dragged dock, keyboard or chin is exactly as
 * unsaved as a moved bar.
 *
 * <p>Read and written through {@link PlaceLayoutStore}'s own accessors rather than the raw keys, so
 * a restore writes the value the layout was resolving to whether or not it had a key of its own.
 * That materialises a key the global value was answering for, at the value it was answering with:
 * the same arrangement, spelled out.
 *
 * <p>Pure: a store in, a value out, no views, so revert and dirtiness are testable without a window.
 */
public final class PlaceArrangeSnapshot {

    /** One orientation's arrangement, exactly as the store answers for it. */
    private static final class Entry {
        final PlaceOrientation orientation;
        final PlaceLayout.Edge statusBarEdge;
        final PlaceLayout.RowPlacement appsRow;
        final boolean azRowShown;
        final PlaceLayout.Edge azBarEdge;
        final PlaceLayout.RowPlacement extraKeys;
        final PlaceLayout.KeyboardMode keyboardMode;
        final PlaceLayout.KeyboardForm keyboardForm;
        final int widgetColumns;
        final int widgetRows;
        final float dockHeightScale;
        final float keyboardHeightScale;
        final int keyboardChinDp;
        /** Each element's position in its edge's stack, by {@link Element#ordinal()}. */
        final int[] slotOrders;

        Entry(@NonNull PlaceLayoutStore places, @NonNull PlaceOrientation orientation) {
            this.orientation = orientation;
            statusBarEdge = places.statusBarEdge(orientation);
            appsRow = places.appsRow(orientation);
            azRowShown = places.azRowShown(orientation);
            azBarEdge = places.azBarEdge(orientation);
            extraKeys = places.extraKeys(orientation);
            keyboardMode = places.keyboardMode(orientation);
            keyboardForm = places.keyboardForm(orientation);
            widgetColumns = places.widgetColumns(orientation);
            widgetRows = places.widgetRows(orientation);
            dockHeightScale = places.dockHeightScale(orientation);
            keyboardHeightScale = places.keyboardHeightScale(orientation);
            keyboardChinDp = places.keyboardChinDp(orientation);
            slotOrders = new int[Element.values().length];
            for (Element element : Element.values())
                slotOrders[element.ordinal()] = places.slotOrder(orientation, element);
        }

        void restore(@NonNull PlaceLayoutStore places) {
            places.setStatusBarEdge(orientation, statusBarEdge);
            places.setAppsRow(orientation, appsRow);
            places.setAzRowShown(orientation, azRowShown);
            places.setAzBarEdge(orientation, azBarEdge);
            places.setExtraKeys(orientation, extraKeys);
            places.setKeyboardMode(orientation, keyboardMode);
            places.setKeyboardForm(orientation, keyboardForm);
            places.setWidgetColumns(orientation, widgetColumns);
            places.setWidgetRows(orientation, widgetRows);
            places.setDockHeightScale(orientation, dockHeightScale);
            places.setKeyboardHeightScale(orientation, keyboardHeightScale);
            places.setKeyboardChinDp(orientation, keyboardChinDp);
            for (Element element : Element.values())
                places.setSlotOrder(orientation, element, slotOrders[element.ordinal()]);
        }

        void appendTo(@NonNull StringBuilder out) {
            out.append(orientation.storageValue()).append(':')
                .append(statusBarEdge).append(',')
                .append(appsRow).append(',')
                .append(azRowShown).append(',')
                .append(azBarEdge).append(',')
                .append(extraKeys).append(',')
                .append(keyboardMode).append(',')
                .append(keyboardForm).append(',')
                .append(widgetColumns).append('x').append(widgetRows).append(',')
                .append(dockHeightScale).append(',')
                .append(keyboardHeightScale).append(',')
                .append(keyboardChinDp).append(',');
            for (int order : slotOrders) out.append(order).append(';');
            out.append('|');
        }
    }

    @NonNull private final List<Entry> mEntries;

    private PlaceArrangeSnapshot(@NonNull List<Entry> entries) {
        mEntries = Collections.unmodifiableList(entries);
    }

    /** Both orientations: a rotation mid-edit moves which one the editor writes. */
    @NonNull
    public static PlaceArrangeSnapshot capture(@NonNull PlaceLayoutStore places) {
        List<Entry> entries = new ArrayList<>();
        for (PlaceOrientation orientation : PlaceOrientation.values())
            entries.add(new Entry(places, orientation));
        return new PlaceArrangeSnapshot(entries);
    }

    /** Puts the arrangement back the way {@link #capture} found it. */
    public void restore(@NonNull PlaceLayoutStore places) {
        for (Entry entry : mEntries) entry.restore(places);
    }

    /** The whole arrangement as one string, for the editor's unsaved-changes comparison. */
    @NonNull
    public String signature() {
        StringBuilder out = new StringBuilder(128);
        for (Entry entry : mEntries) entry.appendTo(out);
        return out.toString();
    }
}
