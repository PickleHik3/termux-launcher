package com.termux.app.layouteditor;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.termux.R;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.fragments.settings.PlaceMiniatureView;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

/**
 * The Layout editor: a miniature of the place the user is looking at, parked over the live place,
 * with a Portrait / Landscape toggle above it.
 *
 * <p>The sibling of the surface editor and not a page of it: this one answers where a place's
 * elements sit, that one answers how its surfaces look, and only ever one of the two is open. A bar
 * is dragged on the miniature to an edge or into the tray under it, and the drop writes straight
 * through, so the place behind the card follows every drop made in the orientation the phone is
 * actually in. The other orientation moves on the miniature alone until the phone is turned.
 *
 * <p>✓ keeps the edits, Discard and the revert glyph put every bar back where the editor found it,
 * and Back with something moved asks rather than choosing for the user — the live write-through
 * means leaving would otherwise mean keeping by accident.
 *
 * <p>Every decision here — what is shown, what a drop writes, whether the place follows, whether
 * anything has moved — belongs to {@link LayoutEditorPlan}; this class is the shell that draws its
 * answers. {@link Host} is the seam to the activity, the same shape the surface editor's is.
 */
public final class LayoutEditorController {

    /** What the editor needs from the activity: its views, the places, and the chrome pass. */
    public interface Host {
        @NonNull Context context();

        @Nullable <T extends View> T findView(int viewId);

        /** Where every place keeps its arrangement, or null before the preferences exist. */
        @Nullable PlaceLayoutStore places();

        /** The place the chrome on screen belongs to: what a corner tab opens the editor on. */
        @NonNull PaneWallPage placeOnScreen();

        /** The orientation the phone is in, which is the only one the live place can follow. */
        @NonNull PlaceOrientation placeOrientation();

        /**
         * A bar moved: re-lay every piece of chrome the arrangement decides, for the place on
         * screen, without tearing the editor down.
         */
        void applyPlaceArrangement();

        /**
         * Bring the pane wall to the place the editor is open on and hold its gestures, or hand
         * them back. What the miniature draws is what the user is looking at, so the wall stands
         * still on that place for as long as the editor is up.
         */
        void holdPaneWallOnPlace(@NonNull PaneWallPage place, boolean held);

        int themeColor(int attr, int fallbackRes);

        /** Whether the surface editor holds the screen; the two are never up together. */
        boolean isSurfaceEditorActive();
    }

    @NonNull private final Host mHost;

    /** The editor's fixed views, inflated once per process. */
    private static final class Card {
        final ViewGroup host;
        final LinearLayout root;
        final ImageView revert;
        final TextView discard;
        final ImageView done;
        final MaterialButtonToggleGroup orientation;
        final PlaceMiniatureView miniature;

        Card(ViewGroup host, LinearLayout root) {
            this.host = host;
            this.root = root;
            revert = root.findViewById(R.id.layout_editor_revert);
            discard = root.findViewById(R.id.layout_editor_discard);
            done = root.findViewById(R.id.layout_editor_done);
            orientation = root.findViewById(R.id.layout_editor_orientation);
            miniature = root.findViewById(R.id.layout_editor_miniature);
        }

        boolean complete() {
            return revert != null && discard != null && done != null && orientation != null
                && miniature != null;
        }
    }

    @Nullable private Card mCard;
    @Nullable private LayoutEditorPlan mPlan;
    /** True while the toggle is being restated from the plan, so it writes nothing back. */
    private boolean mRestatingToggle;

    public LayoutEditorController(@NonNull Host host) {
        mHost = host;
    }

    public boolean isActive() {
        return mPlan != null;
    }

    /** The place the editor is open on, or null while it is not. */
    @Nullable
    @VisibleForTesting
    public PaneWallPage editedPlace() {
        return mPlan == null ? null : mPlan.place();
    }

    /** The orientation the miniature is showing, or null while the editor is not open. */
    @Nullable
    @VisibleForTesting
    public PlaceOrientation shownOrientation() {
        return mPlan == null ? null : mPlan.shownOrientation();
    }

    // ------------------------------------------------------------------------------------ entry

    /**
     * Opens the editor on one place. A door opened while the editor is already up moves it to that
     * place rather than starting over, so what Discard puts back is still the arrangement the
     * session opened on.
     */
    public void enter(@NonNull PaneWallPage place) {
        PlaceLayoutStore places = mHost.places();
        // Only one editor at a time: the surface editor is already holding the screen, and the two
        // would be writing through to the same chrome from two cards.
        if (places == null || mHost.isSurfaceEditorActive())
            return;
        Card card = card();
        if (card == null)
            return;
        if (mPlan == null) mPlan = LayoutEditorPlan.enter(places, place, mHost.placeOrientation());
        else mPlan.showPlace(place);
        card.host.setVisibility(View.VISIBLE);
        card.host.bringToFront();
        mHost.holdPaneWallOnPlace(place, true);
        sync();
    }

    /** Inflates the card into its host, once. */
    @Nullable
    private Card card() {
        if (mCard != null)
            return mCard;
        ViewGroup host = mHost.findView(R.id.layout_editor_host);
        if (host == null)
            return null;
        LinearLayout root = host.findViewById(R.id.layout_editor_card);
        if (root == null) {
            LayoutInflater.from(mHost.context()).inflate(R.layout.layout_editor, host, true);
            root = host.findViewById(R.id.layout_editor_card);
        }
        if (root == null)
            return null;
        Card card = new Card(host, root);
        if (!card.complete())
            return null;
        mCard = card;
        bind(card);
        return card;
    }

    private void bind(@NonNull Card card) {
        card.root.setBackground(cardBackground());
        setIcon(card.revert, R.drawable.ic_symbol_restart, false);
        setIcon(card.done, R.drawable.ic_symbol_check, true);

        card.revert.setOnClickListener(view -> revertToEntryState());
        card.discard.setOnClickListener(view -> {
            revertToEntryState();
            exit();
        });
        card.done.setOnClickListener(view -> exit());

        card.miniature.setLegendVisible(false);
        card.miniature.setOnBarDroppedListener(this::onBarDropped);
        card.orientation.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || mRestatingToggle || mPlan == null)
                return;
            PlaceOrientation picked = checkedId == R.id.layout_editor_orientation_landscape
                ? PlaceOrientation.LANDSCAPE : PlaceOrientation.PORTRAIT;
            if (picked == mPlan.shownOrientation())
                return;
            mPlan.showOrientation(picked);
            sync();
        });
    }

    // ------------------------------------------------------------------------------- the canvas

    /**
     * A bar dropped on an edge, or in the tray when {@code edge} is null. The write lands on the
     * orientation the miniature is showing; the live place is re-laid only when that is the
     * orientation the phone is in.
     */
    @VisibleForTesting
    void onBarDropped(@NonNull PlaceMiniatureView.Block block, @Nullable PlaceLayout.Edge edge) {
        MiniatureDragPolicy.Bar bar = PlaceMiniatureView.barOf(block);
        if (mPlan == null || bar == null)
            return;
        if (mPlan.drop(bar, edge) == LayoutEditorPlan.Drop.LIVE)
            mHost.applyPlaceArrangement();
        sync();
    }

    /** Re-reads the miniature, the toggle and the two unsaved glyphs from the plan. */
    private void sync() {
        Card card = mCard;
        LayoutEditorPlan plan = mPlan;
        if (card == null || plan == null)
            return;
        mRestatingToggle = true;
        card.orientation.check(plan.shownOrientation() == PlaceOrientation.LANDSCAPE
            ? R.id.layout_editor_orientation_landscape : R.id.layout_editor_orientation_portrait);
        mRestatingToggle = false;
        applyCanvasHeight(card, plan);
        card.miniature.setLayout(plan.shownLayout(), plan.shownOrientation(), plan.place());
        int dirty = plan.isDirty() ? View.VISIBLE : View.GONE;
        card.revert.setVisibility(dirty);
        card.discard.setVisibility(dirty);
    }

    /** Sizes the canvas to the frame the shown orientation asks for. */
    private void applyCanvasHeight(@NonNull Card card, @NonNull LayoutEditorPlan plan) {
        DisplayMetrics metrics = mHost.context().getResources().getDisplayMetrics();
        int height = LayoutEditorPlan.miniatureHeightPx(plan.shownOrientation(),
            metrics.widthPixels, metrics.heightPixels,
            PlaceMiniatureView.frameAspect(plan.shownOrientation()),
            Math.round(card.miniature.reservedHeightPx()));
        ViewGroup.LayoutParams params = card.miniature.getLayoutParams();
        if (params == null || params.height == height)
            return;
        params.height = height;
        card.miniature.setLayoutParams(params);
    }

    // ------------------------------------------------------------------------- unsaved and exit

    private void revertToEntryState() {
        if (mPlan == null)
            return;
        mPlan.revert();
        // The bars have to be back on their edges before the chrome is re-read.
        mHost.applyPlaceArrangement();
        sync();
    }

    /**
     * The back press. ✓ keeps the edits, and when there is something to lose this asks rather than
     * silently choosing for the user: every drop is already written through, so leaving would
     * otherwise mean keeping by accident.
     */
    public void requestClose() {
        if (mPlan == null)
            return;
        if (!mPlan.isDirty()) {
            exit();
            return;
        }
        new MaterialAlertDialogBuilder(mHost.context())
            .setTitle(R.string.termux_surface_tuning_unsaved_title)
            .setMessage(R.string.termux_layout_editor_unsaved_message)
            .setNeutralButton(R.string.termux_surface_tuning_unsaved_keep_editing, null)
            .setNegativeButton(R.string.termux_surface_tuning_unsaved_discard,
                (dialog, which) -> {
                    revertToEntryState();
                    exit();
                })
            .setPositiveButton(R.string.termux_surface_tuning_unsaved_save,
                (dialog, which) -> exit())
            .show();
    }

    /** Leaving from outside a Back press — a HOME press — takes the same route, dirty or not. */
    public void requestExit() {
        requestClose();
    }

    /** The phone turned: the miniature goes with it, and so does what the next drop writes. */
    public void onPlaceOrientationChanged() {
        if (mPlan == null)
            return;
        mPlan.onDeviceOrientationChanged(mHost.placeOrientation());
        sync();
    }

    @VisibleForTesting
    void exit() {
        PaneWallPage place = mPlan == null ? null : mPlan.place();
        mPlan = null;
        if (mCard != null) {
            mCard.revert.setVisibility(View.GONE);
            mCard.discard.setVisibility(View.GONE);
            mCard.host.setVisibility(View.GONE);
        }
        if (place != null) mHost.holdPaneWallOnPlace(place, false);
    }

    // -------------------------------------------------------------------------------- the chrome

    @NonNull
    private Drawable cardBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(mHost.themeColor(
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            R.color.termux_surface_panel_high));
        background.setCornerRadius(dpToPx(24));
        background.setStroke(Math.max(1, Math.round(dpToPx(1))), mHost.themeColor(
            com.termux.shared.R.attr.termuxColorOutlineVariant,
            R.color.termux_outline_variant));
        return background;
    }

    private void setIcon(@NonNull ImageView view, @DrawableRes int drawableRes, boolean onAccent) {
        Drawable icon = androidx.core.content.ContextCompat.getDrawable(
            mHost.context(), drawableRes);
        if (icon == null)
            return;
        icon = icon.mutate();
        icon.setTint(onAccent
            ? mHost.themeColor(com.termux.shared.R.attr.termuxColorOnAccentContainer,
                R.color.termux_on_accent_container)
            : mHost.themeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary));
        view.setImageDrawable(icon);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
            mHost.context().getResources().getDisplayMetrics());
    }
}
