package com.termux.app.fragments.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

/**
 * The Layout page's header: which place and which orientation the rows below describe, plus a
 * miniature of the arrangement that results. Kept as one preference — rather than a
 * {@link SegmentedPillPreference} pair — because the selection here is not a stored value; it is
 * the fragment's own state, restored across rotation from saved instance state, the way a tab host
 * would be.
 */
@Keep
public final class LayoutOverviewPreference extends Preference {

    /** Reports a new place/orientation selection, or a tap on one of the miniature's blocks. */
    public interface Listener {
        void onSelectionChanged(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation);
        void onBlockTapped(@NonNull PlaceMiniatureView.Block block);
    }

    private static final long SLIDE_DURATION_MS = 190L;

    @NonNull private PaneWallPage mSelectedPlace = PaneWallPage.TERMINAL;
    @NonNull private PlaceOrientation mSelectedOrientation = PlaceOrientation.PORTRAIT;
    private boolean mDisplayTabVisible = false;
    @Nullable private PlaceLayout mLayout;
    @Nullable private Listener mListener;

    public LayoutOverviewPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_layout_overview);
        setIconSpaceReserved(false);
        setSelectable(false);
    }

    public LayoutOverviewPreference(@NonNull Context context) {
        this(context, null);
    }

    public void setOnSelectionListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** Whether the Display place's tab should be offered at all. */
    public void setDisplayTabVisible(boolean visible) {
        if (mDisplayTabVisible == visible) return;
        mDisplayTabVisible = visible;
        if (!visible && mSelectedPlace == PaneWallPage.DISPLAY) mSelectedPlace = PaneWallPage.TERMINAL;
        notifyChanged();
    }

    /** Sets the selection without notifying the listener — for restoring saved state. */
    public void setSelection(@NonNull PaneWallPage place, @NonNull PlaceOrientation orientation) {
        mSelectedPlace = place;
        mSelectedOrientation = orientation;
        notifyChanged();
    }

    @NonNull
    public PaneWallPage getSelectedPlace() {
        return mSelectedPlace;
    }

    @NonNull
    public PlaceOrientation getSelectedOrientation() {
        return mSelectedOrientation;
    }

    public boolean isDisplayTabVisible() {
        return mDisplayTabVisible;
    }

    /** What the miniature draws; redrawn whenever a row elsewhere on the page changes. */
    public void setLayout(@NonNull PlaceLayout layout) {
        mLayout = layout;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        FrameLayout placesTrack = (FrameLayout) holder.findViewById(R.id.layout_overview_places_track);
        View placesIndicator = holder.findViewById(R.id.layout_overview_places_indicator);
        TextView home = (TextView) holder.findViewById(R.id.layout_overview_tab_home);
        TextView terminal = (TextView) holder.findViewById(R.id.layout_overview_tab_terminal);
        TextView display = (TextView) holder.findViewById(R.id.layout_overview_tab_display);
        if (placesTrack != null && placesIndicator != null
            && home != null && terminal != null && display != null) {
            display.setVisibility(mDisplayTabVisible ? View.VISIBLE : View.GONE);
            PaneWallPage[] places = mDisplayTabVisible
                ? new PaneWallPage[]{PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, PaneWallPage.DISPLAY}
                : new PaneWallPage[]{PaneWallPage.WIDGETS, PaneWallPage.TERMINAL};
            TextView[] tabs = mDisplayTabVisible
                ? new TextView[]{home, terminal, display} : new TextView[]{home, terminal};
            bindTrack(placesTrack, placesIndicator, tabs, indexOf(places, mSelectedPlace), index -> {
                mSelectedPlace = places[index];
                notifySelectionChanged();
            });
        }

        FrameLayout orientationTrack =
            (FrameLayout) holder.findViewById(R.id.layout_overview_orientation_track);
        View orientationIndicator = holder.findViewById(R.id.layout_overview_orientation_indicator);
        TextView portrait = (TextView) holder.findViewById(R.id.layout_overview_orientation_portrait);
        TextView landscape = (TextView) holder.findViewById(R.id.layout_overview_orientation_landscape);
        if (orientationTrack != null && orientationIndicator != null
            && portrait != null && landscape != null) {
            PlaceOrientation[] orientations =
                {PlaceOrientation.PORTRAIT, PlaceOrientation.LANDSCAPE};
            TextView[] segments = {portrait, landscape};
            bindTrack(orientationTrack, orientationIndicator, segments,
                mSelectedOrientation == PlaceOrientation.LANDSCAPE ? 1 : 0, index -> {
                    mSelectedOrientation = orientations[index];
                    notifySelectionChanged();
                });
        }

        PlaceMiniatureView miniature =
            (PlaceMiniatureView) holder.findViewById(R.id.layout_overview_miniature);
        if (miniature != null) {
            if (mLayout != null) miniature.setLayout(mLayout, mSelectedOrientation, mSelectedPlace);
            miniature.setOnBlockTappedListener(block -> {
                if (mListener != null) mListener.onBlockTapped(block);
            });
        }
    }

    private void notifySelectionChanged() {
        if (mListener != null) mListener.onSelectionChanged(mSelectedPlace, mSelectedOrientation);
    }

    private interface IndexSelected {
        void onIndexSelected(int index);
    }

    private void bindTrack(@NonNull FrameLayout track, @NonNull View indicator,
                           @NonNull TextView[] segments, int selectedIndex,
                           @NonNull IndexSelected onSelected) {
        for (int i = 0; i < segments.length; i++) {
            int index = i;
            segments[i].setOnClickListener(view ->
                setTrackSelection(track, indicator, segments, index, onSelected));
        }
        track.post(() -> {
            updateIndicatorWidth(track, indicator, segments.length);
            indicator.setTranslationX(selectedIndex * segmentWidth(track, segments.length));
            updateLabelColors(segments, selectedIndex);
        });
    }

    private void setTrackSelection(@NonNull FrameLayout track, @NonNull View indicator,
                                   @NonNull TextView[] segments, int index,
                                   @NonNull IndexSelected onSelected) {
        updateIndicatorWidth(track, indicator, segments.length);
        float target = index * segmentWidth(track, segments.length);
        indicator.animate().translationX(target).setDuration(SLIDE_DURATION_MS).start();
        updateLabelColors(segments, index);
        onSelected.onIndexSelected(index);
    }

    private void updateIndicatorWidth(@NonNull FrameLayout track, @NonNull View indicator,
                                      int segmentCount) {
        int width = Math.round(segmentWidth(track, segmentCount));
        if (width <= 0 || indicator.getLayoutParams().width == width) return;
        indicator.getLayoutParams().width = width;
        indicator.requestLayout();
    }

    private float segmentWidth(@NonNull FrameLayout track, int segmentCount) {
        return Math.max(0f, (track.getWidth() - track.getPaddingLeft() - track.getPaddingRight())
            / (float) segmentCount);
    }

    private void updateLabelColors(@NonNull TextView[] segments, int selectedIndex) {
        int selected = resolveColor(com.termux.shared.R.attr.termuxColorOnAccentContainer,
            R.color.termux_on_primary);
        int idle = resolveColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant);
        for (int i = 0; i < segments.length; i++) {
            segments[i].setTextColor(i == selectedIndex ? selected : idle);
        }
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return ContextCompat.getColor(getContext(), fallback);
    }

    private static int indexOf(@NonNull PaneWallPage[] places, @NonNull PaneWallPage place) {
        for (int i = 0; i < places.length; i++) {
            if (places[i] == place) return i;
        }
        return 0;
    }
}
