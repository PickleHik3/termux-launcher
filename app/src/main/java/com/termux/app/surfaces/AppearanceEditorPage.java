package com.termux.app.surfaces;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButtonToggleGroup;

import androidx.annotation.NonNull;

import com.termux.R;

/**
 * Look and Layout as a page of the Appearance surface, shaped like the Icon pack page: the shared
 * {@code appearance_page_frame} bar (back arrow, the Look | Layout pill in the title's place, then Undo and Done as the bar's end
 * actions) over a transparent content region. The launcher's scaled frame is not in this view: it
 * is the root container, scaled underneath, standing in the region below the bar
 * ({@link #BAR_DP} plus the status inset is where {@link SurfaceEditorController} puts the frame's
 * top). The page takes no touch outside the bar's own buttons, so a tap inside the frame reaches
 * the editor's targets as it always did.
 *
 * <p>The pill follows the editor's mode (Look or Layout) and switches it; Undo is visible while there is
 * something to undo; Done is always there. Back is the surface's Back, which asks the
 * unsaved-changes question.</p>
 */
final class AppearanceEditorPage {

    /** The bar's height, dp: the frame stands below it. */
    static final int BAR_DP = 64;

    /** What the bar's buttons ask of the surface. */
    interface Callbacks {
        void onBack();

        void onUndo();

        void onDone();

        /** The bar's Look | Layout pill moved: true for Layout. */
        void onMode(boolean layout);
    }

    @NonNull private final View mRoot;
    @NonNull private final TextView mTitle;
    @NonNull private final View mModeSlot;
    @NonNull private final MaterialButtonToggleGroup mMode;
    private boolean mRestating;
    @NonNull private final View mUndo;
    @NonNull private final View mDone;

    AppearanceEditorPage(@NonNull Context context, @NonNull Callbacks callbacks) {
        mRoot = LayoutInflater.from(context).inflate(R.layout.appearance_page_frame, null, false);
        // Transparent: the scaled launcher shows through the content region, and colorSurface (the
        // surface's scrim, in the content view) stands behind the bar and around the frame.
        mRoot.setBackground(null);
        mTitle = mRoot.findViewById(R.id.appearance_page_title);
        mModeSlot = mRoot.findViewById(R.id.appearance_page_mode_slot);
        mMode = mRoot.findViewById(R.id.appearance_page_mode);
        mUndo = mRoot.findViewById(R.id.appearance_page_undo);
        mDone = mRoot.findViewById(R.id.appearance_page_done);
        mRoot.findViewById(R.id.appearance_page_back).setOnClickListener(v -> callbacks.onBack());
        mUndo.setOnClickListener(v -> callbacks.onUndo());
        mDone.setOnClickListener(v -> callbacks.onDone());
        // The editor page's title is the pill; the title text stays for the shared frame.
        mTitle.setVisibility(View.GONE);
        mModeSlot.setVisibility(View.VISIBLE);
        mMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (mRestating || !isChecked)
                return;
            callbacks.onMode(checkedId == R.id.appearance_page_mode_layout);
        });
        mUndo.setVisibility(View.GONE);
        mDone.setVisibility(View.VISIBLE);
        ViewGroup content = mRoot.findViewById(R.id.appearance_page_content);
        content.setClickable(false);
        content.setFocusable(false);
        setLayoutMode(false);
    }

    @NonNull
    View root() {
        return mRoot;
    }

    @NonNull
    CharSequence title() {
        return mTitle.getText();
    }

    void setLayoutMode(boolean layout) {
        mTitle.setText(layout ? R.string.appearance_page_layout : R.string.appearance_page_look);
        int id = layout ? R.id.appearance_page_mode_layout : R.id.appearance_page_mode_look;
        if (mMode.getCheckedButtonId() != id) {
            mRestating = true;
            mMode.check(id);
            mRestating = false;
        }
    }

    boolean isModeShown() {
        return mModeSlot.getVisibility() == View.VISIBLE;
    }

    boolean isTitleShown() {
        return mTitle.getVisibility() == View.VISIBLE;
    }

    void setDirty(boolean dirty) {
        int visibility = dirty ? View.VISIBLE : View.GONE;
        if (mUndo.getVisibility() != visibility)
            mUndo.setVisibility(visibility);
    }

    boolean isUndoShown() {
        return mUndo.getVisibility() == View.VISIBLE;
    }

    boolean isDoneShown() {
        return mDone.getVisibility() == View.VISIBLE;
    }
}
