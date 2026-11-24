package com.termux.app.surfaces;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.layouteditor.BarDragGeometry;
import com.termux.app.layouteditor.BarLandingPolicy;
import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.FrameState;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;
import com.termux.app.layouteditor.BarPlaceOffers;
import com.termux.app.layouteditor.HintText;
import com.termux.app.layouteditor.BarPlaceOffers.Label;
import com.termux.app.layouteditor.BarPlaceOffers.Offer;
import com.termux.app.layouteditor.LayoutEditorController;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.layout.LayoutKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A selected bar on the editor's frame (#341, #342): dashed places where it can go, each a
 * labelled button ("Move Extra keys here", "Put it inside the pane"), and a Hide button on the
 * bar. A tap on a place writes the draft through {@link BarDragController#commit}, so a tap and a
 * drag are one write and Undo and Discard treat them alike. A selected bar that is put away shows
 * the one place that brings it back. Everything offered comes from {@link BarPlaceOffers}, which
 * asks the landing policy, so no place is shown where a drop would be refused.
 *
 * <p>The buttons are views of the tap overlay, pooled: a refresh only sets text, translation and
 * scale (the buttons keep their on-screen size at any frame scale, as the chips do). They are
 * plain views with a content description and a button role, so a screen reader reads and clicks
 * them like any button. {@link #refresh} runs where the targets are laid over the bars, never in
 * measure, layout or draw.
 *
 * <p>For the hint (#343): {@link #selected} is the "selected" state; its reach is
 * {@code BarLandingPolicy.places(state, bar).scope} (move) and {@code hideScope} (hide, bring back).
 */
final class BarPlacesController {

    private static final int BUTTON_HEIGHT_DP = 36;
    private static final int MARGIN_DP = 6;
    private static final int DASH_DP = 5;

    @NonNull private final BarDragController.Host mHost;
    @NonNull private final BarDragController mDrag;
    @NonNull private final List<PlaceButton> mButtons = new ArrayList<>(6);
    @NonNull private final float[] mRect = new float[4];
    @NonNull private final float[] mLine = new float[4];
    @NonNull private List<Offer> mOffers = Collections.emptyList();
    @Nullable private Bar mSelected;

    BarPlacesController(@NonNull BarDragController.Host host, @NonNull BarDragController drag) {
        mHost = host;
        mDrag = drag;
    }

    /** The selected bar, or null. */
    @Nullable
    Bar selected() {
        return mSelected;
    }

    /** Selects {@code bar} (a tap on it, or on its chip in the Hidden column). */
    void select(@Nullable Bar bar) {
        if (bar == mSelected)
            return;
        mSelected = bar;
        refresh();
    }

    void clear() {
        select(null);
    }

    /** Takes the buttons off the overlay with the session. */
    void release() {
        mSelected = null;
        mOffers = Collections.emptyList();
        ViewGroup overlay = mHost.overlay();
        for (View button : mButtons)
            if (overlay != null)
                overlay.removeView(button);
        mButtons.clear();
    }

    /** Lays the selected bar's places over the frame, then tells the host (the hint reads it). */
    void refresh() {
        layPlaces();
        mHost.onPlacesRefreshed();
    }

    /**
     * The hint for the state at rest: the selected bar with its reach from the policy, or the
     * general line (#343).
     */
    @NonNull
    HintText hint() {
        LayoutEditorController layout = mHost.layout();
        Bar bar = mSelected;
        boolean anyHidden = layout != null && !layout.trayItems().isEmpty();
        PlaceLayout arranged = layout == null ? null : layout.arrangedLayout();
        PlaceOrientation orientation = layout == null ? null : layout.shownOrientation();
        LayoutKind kind = layout == null ? null : layout.layoutKind();
        if (bar == null || arranged == null || orientation == null || kind == null)
            return HintText.atRest(anyHidden);
        BarLandingPolicy.Places places = BarLandingPolicy.places(
            mHost.frameState(arranged, orientation, kind), bar);
        return HintText.selected(bar, !shownOn(layout, bar), places.scope, places.hideScope);
    }

    /** Lays the selected bar's places over the frame; with no selection every button goes. */
    private void layPlaces() {
        ViewGroup overlay = mHost.overlay();
        LayoutEditorController layout = mHost.layout();
        Bar bar = mSelected;
        PlaceLayout arranged = layout == null ? null : layout.arrangedLayout();
        PlaceOrientation orientation = layout == null ? null : layout.shownOrientation();
        LayoutKind kind = layout == null ? null : layout.layoutKind();
        if (bar == null || overlay == null || !mHost.open() || arranged == null
            || orientation == null || kind == null) {
            hideFrom(0);
            mOffers = Collections.emptyList();
            return;
        }
        FrameState state = mHost.frameState(arranged, orientation, kind);
        mOffers = BarPlaceOffers.of(state, bar, shownOn(layout, bar));
        BarDragGeometry.Frame frame = mDrag.buildFrame(overlay, arranged, kind);
        float scale = Math.max(0.01f, mHost.scale());
        float inverse = 1f / scale;
        int shown = 0;
        for (int i = 0; i < mOffers.size(); i++) {
            Offer offer = mOffers.get(i);
            if (!anchor(offer, frame, mRect))
                continue;
            PlaceButton button = buttonAt(shown, overlay);
            shown++;
            String words = wordsOf(offer);
            if (!words.contentEquals(button.getText())) {
                button.setText(words);
                button.setContentDescription(descriptionOf(offer));
                button.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(Math.round(dpToPx(BUTTON_HEIGHT_DP)),
                        View.MeasureSpec.EXACTLY));
            }
            button.offer = offer;
            styleOf(button, offer);
            float width = button.getMeasuredWidth() * inverse;
            float height = button.getMeasuredHeight() * inverse;
            float margin = dpToPx(MARGIN_DP) * inverse;
            float left;
            float top;
            if (offer.onBar()) {
                left = mRect[2] - width - margin;
                top = mRect[1] + margin;
            } else {
                // mRect holds the slot line (x1, y1, x2, y2): the button stands on its inner side.
                float centreX = (mRect[0] + mRect[2]) / 2f;
                float centreY = (mRect[1] + mRect[3]) / 2f;
                left = centreX - width / 2f;
                top = centreY - height / 2f;
                if (offer.landing.zone == Zone.PANE) {
                    top = mRect[1] + margin;
                } else if (offer.landing.edge != null) {
                    switch (offer.landing.edge) {
                        case TOP: top = mRect[1] + margin; break;
                        case BOTTOM: top = mRect[1] - height - margin; break;
                        case LEFT: left = mRect[0] + margin; break;
                        default: left = mRect[0] - width - margin; break;
                    }
                }
            }
            left = Math.max(0f, Math.min(left, overlay.getWidth() - width));
            top = Math.max(0f, Math.min(top, overlay.getHeight() - height));
            if (button.getScaleX() != inverse) {
                button.setScaleX(inverse);
                button.setScaleY(inverse);
            }
            button.setPivotX(0f);
            button.setPivotY(0f);
            button.setTranslationX(left);
            button.setTranslationY(top);
            EditorViews.setVisible(button, true);
        }
        hideFrom(shown);
    }

    /** Where {@code offer} is drawn: the bar's rect for Hide, the slot line for a place. */
    private boolean anchor(@NonNull Offer offer, @NonNull BarDragGeometry.Frame frame,
                           @NonNull float[] out) {
        if (offer.onBar())
            return mHost.rectOf(offer.bar, out);
        return BarDragGeometry.slotLine(frame, offer.bar, offer.landing, out);
    }

    private void onTap(@NonNull PlaceButton button) {
        LayoutEditorController layout = mHost.layout();
        Offer offer = button.offer;
        if (offer == null || layout == null)
            return;
        boolean wrote;
        if (offer.label == Label.BRING_BACK) {
            if (offer.bar == Bar.PAGE_BAR)
                mHost.setPageBarShown(true);
            else if (offer.bar.element() != null)
                layout.setShown(offer.bar.element(), true);
            else
                layout.setKeyboardShown(true);
            wrote = true;
        } else {
            wrote = mDrag.commit(offer.bar, offer.landing);
        }
        if (wrote)
            mHost.onWritten();
    }

    private boolean shownOn(@NonNull LayoutEditorController layout, @NonNull Bar bar) {
        if (bar == Bar.PAGE_BAR)
            return mHost.pageBarShown();
        if (bar == Bar.KEYBOARD)
            return layout.isKeyboardShown();
        return bar.element() == null || layout.isShown(bar.element());
    }

    private void hideFrom(int from) {
        for (int i = from; i < mButtons.size(); i++)
            EditorViews.setVisible(mButtons.get(i), false);
    }

    @NonNull
    private PlaceButton buttonAt(int index, @NonNull ViewGroup overlay) {
        if (index < mButtons.size())
            return mButtons.get(index);
        Context context = mHost.context();
        PlaceButton button = new PlaceButton(context);
        BarChipStyle.styleLabel(button, mHost.onSurfaceColor());
        button.setGravity(Gravity.CENTER);
        int pad = Math.round(dpToPx(12));
        button.setPadding(pad, 0, pad, 0);
        button.setClickable(true);
        button.setFocusable(true);
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        button.setOnClickListener(view -> onTap((PlaceButton) view));
        overlay.addView(button, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            Math.round(dpToPx(BUTTON_HEIGHT_DP)), Gravity.TOP | Gravity.START));
        mButtons.add(button);
        return button;
    }

    /** Dashed for a place, solid for Hide; set only when the look changes. */
    private void styleOf(@NonNull PlaceButton button, @NonNull Offer offer) {
        boolean dashed = !offer.onBar();
        if (button.getBackground() != null && button.dashed == dashed)
            return;
        GradientDrawable back = new GradientDrawable();
        int primary = mHost.primaryColor();
        int surface = mHost.surfaceColor();
        back.setCornerRadius(dpToPx(BUTTON_HEIGHT_DP / 2f));
        if (dashed) {
            back.setColor((0x33 << 24) | (surface & 0x00FFFFFF));
            back.setStroke(Math.round(dpToPx(2)), primary, dpToPx(DASH_DP), dpToPx(DASH_DP));
        } else {
            back.setColor((0xE6 << 24) | (surface & 0x00FFFFFF));
            back.setStroke(Math.round(dpToPx(1)), primary);
        }
        button.setBackground(back);
        button.dashed = dashed;
    }

    @NonNull
    private String wordsOf(@NonNull Offer offer) {
        Context context = mHost.context();
        switch (offer.label) {
            case PUT_IN_PANE: return context.getString(R.string.editor_place_pane);
            case HIDE: return context.getString(R.string.editor_place_hide);
            case BRING_BACK:
                return context.getString(R.string.editor_place_bring_back,
                    context.getString(nameOf(offer.bar)));
            default:
                return context.getString(R.string.editor_place_move_here,
                    context.getString(nameOf(offer.bar)));
        }
    }

    /** What a screen reader says: Hide names its bar, the rest already do. */
    @NonNull
    private String descriptionOf(@NonNull Offer offer) {
        Context context = mHost.context();
        return offer.label == Label.HIDE
            ? context.getString(R.string.editor_place_hide_bar, context.getString(nameOf(offer.bar)))
            : wordsOf(offer);
    }

    private static int nameOf(@NonNull Bar bar) {
        switch (bar) {
            case STATUS_BAR: return R.string.editor_bar_status;
            case APPS_ROW: return R.string.editor_bar_apps;
            case AZ_INDEX: return R.string.editor_bar_az;
            case EXTRA_KEYS: return R.string.editor_bar_extra_keys;
            case PAGE_BAR: return R.string.editor_bar_page_bar;
            default: return R.string.editor_bar_keyboard;
        }
    }

    /** One pooled button: the offer a tap carries out, and the look it wears. */
    private static final class PlaceButton extends TextView {
        @Nullable Offer offer;
        boolean dashed;

        PlaceButton(@NonNull Context context) {
            super(context);
        }
    }

    private float dpToPx(float dp) {
        return dp * mHost.context().getResources().getDisplayMetrics().density;
    }
}
