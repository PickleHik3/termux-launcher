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
 * The Layout page's header: which place the rows below describe, and that place drawn twice —
 * portrait beside landscape, each miniature live for its own orientation. Kept as one preference
 * — rather than a {@link SegmentedPillPreference} — because the place here is not a stored value;
 * it is the fragment's own state, restored across rotation from saved instance state, the way a
 * tab host would be. The miniatures carry no legend: every element is named in its own row below.
 */
@Keep
public final class LayoutOverviewPreference extends Preference {

    /** Reports a new place, or a tap on one of the miniatures' bands. */
    public interface Listener {
        void onPlaceChanged(@NonNull PaneWallPage place);

        void onBlockTapped(@NonNull PlaceMiniatureView.Block block);
    }

    private static final long SLIDE_DURATION_MS = 190L;

    @NonNull private PaneWallPage mSelectedPlace = PaneWallPage.TERMINAL;
    private boolean mDisplayTabVisible = false;
    @Nullable private PlaceLayout mPortrait;
    @Nullable private PlaceLayout mLandscape;
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

    /** Sets the place without notifying the listener — for restoring saved state. */
    public void setSelection(@NonNull PaneWallPage place) {
        mSelectedPlace = place;
        notifyChanged();
    }

    @NonNull
    public PaneWallPage getSelectedPlace() {
        return mSelectedPlace;
    }

    public boolean isDisplayTabVisible() {
        return mDisplayTabVisible;
    }

    /** What the two miniatures draw; redrawn whenever a chooser writes. */
    public void setLayouts(@NonNull PlaceLayout portrait, @NonNull PlaceLayout landscape) {
        mPortrait = portrait;
        mLandscape = landscape;
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
                if (mListener != null) mListener.onPlaceChanged(mSelectedPlace);
            });
        }

        bindMiniature(holder, R.id.layout_overview_miniature_portrait, PlaceOrientation.PORTRAIT,
            mPortrait);
        bindMiniature(holder, R.id.layout_overview_miniature_landscape, PlaceOrientation.LANDSCAPE,
            mLandscape);
    }

    private void bindMiniature(@NonNull PreferenceViewHolder holder, int viewId,
                               @NonNull PlaceOrientation orientation,
                               @Nullable PlaceLayout layout) {
        PlaceMiniatureView miniature = (PlaceMiniatureView) holder.findViewById(viewId);
        if (miniature == null) return;
        miniature.setLegendVisible(false);
        if (layout != null) miniature.setLayout(layout, orientation, mSelectedPlace);
        miniature.setOnBlockTappedListener(block -> {
            if (mListener != null) mListener.onBlockTapped(block);
        });
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
        int selected = resolveColor(com.termux.shared.R.attr.termuxColorOnPrimary,
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
