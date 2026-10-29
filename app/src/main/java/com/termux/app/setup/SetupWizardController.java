package com.termux.app.setup;

import android.content.Context;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;
import com.termux.app.ReducedMotion;
import com.termux.app.tour.TourController;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The setup wizard over the live launcher: the launcher's root is scaled into a miniature above a
 * fixed-height card ({@link SetupSheetView}), the way the Appearance editor does it, so the dock,
 * the status strip and the wall stay visible while the user picks.
 *
 * <p>The pose math is a small copy of {@code surfaces/EditorFramePose} and
 * {@code AppearanceEditorFrame.setPose}/{@code show} (those are package-private to
 * {@code surfaces}): the root's pivot is its top centre, the frame's top sits at the status inset
 * plus 16dp and its bottom 8dp over the card. The wall's gestures are held while the wizard is
 * open, as the editor's session does through {@code holdPaneWall}.
 */
public final class SetupWizardController implements SetupSheetView.Callbacks {

    /** What the wizard needs from the activity. */
    public interface Host {
        @NonNull Context context();

        @Nullable <T extends View> T findView(int id);

        /** Holds or releases the wall's gestures. */
        void holdPaneWall(boolean held);

        int statusBarInsetTop();

        @Nullable TermuxAppSharedPreferences preferences();
    }

    private static final long POSE_MS = 280L;
    private static final int TOP_GAP_DP = 16;
    private static final int GAP_DP = 8;
    private static final float MIN_SCALE = 0.2f;
    private static final float MINIATURE_SHEET_FRACTION = 0.46f;
    private static final float REVIEW_SHEET_FRACTION = 0.78f;

    @NonNull private final Host mHost;
    private boolean mOpen;
    private boolean mUpdater;
    @Nullable private SetupState mState;
    private final List<SetupSection> mSections = new ArrayList<>();
    private int mIndex;
    private int mFurthest;
    @Nullable private FrameLayout mLayer;
    @Nullable private SetupSheetView mSheet;
    @Nullable private View mRoot;
    @Nullable private ViewOutlineProvider mSavedProvider;
    private boolean mSavedClip;
    private boolean mClipped;
    private int mNavInsetPx;
    private int mWindowHeightPx;

    public SetupWizardController(@NonNull Host host) {
        mHost = host;
    }

    public boolean isOpen() {
        return mOpen;
    }

    /**
     * Opens the wizard, with the current settings pre-selected.
     *
     * @param updater whether this person has used the app before: they get "Keep my current setup"
     */
    public void open(boolean updater) {
        if (mOpen)
            return;
        View root = mHost.findView(R.id.terminal_root_container);
        FrameLayout content = mHost.findView(android.R.id.content);
        if (root == null || content == null)
            return;
        mOpen = true;
        mUpdater = updater;
        mRoot = root;
        mHost.holdPaneWall(true);
        mState = new SetupState(mHost.context());
        mSections.clear();
        mSections.add(new HomeScreenSection());
        mSections.add(new ReviewSection());
        mIndex = 0;
        mFurthest = 0;

        mLayer = new FrameLayout(mHost.context());
        // The layer takes every touch, so the miniature underneath is looked at, not used.
        mLayer.setClickable(true);
        mSheet = new SetupSheetView(mHost.context(), updater, this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM);
        mLayer.addView(mSheet, params);
        content.addView(mLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        showSection(mIndex, false);
    }

    /** Back: the previous section; on the first one nothing (Skip is the way out). */
    public boolean handleBack() {
        if (!mOpen)
            return false;
        if (mIndex > 0)
            showSection(mIndex - 1, true);
        return true;
    }

    /** Fits the miniature and the card to the window again, after a turn. */
    public void relayout() {
        if (mOpen)
            layout(false);
    }

    /** Drops the wizard with no ceremony: the root back at rest, the wall released. */
    public void closeImmediately() {
        close(false);
    }

    // ------------------------------------------------------------------------------- sections

    private void showSection(int index, boolean animate) {
        SetupSection section = mSections.get(index);
        SetupState state = mState;
        SetupSheetView sheet = mSheet;
        if (state == null || sheet == null)
            return;
        mIndex = index;
        mFurthest = Math.max(mFurthest, index);
        View body = section.createView(mHost.context(), state);
        sheet.bind(index, mSections.size(), mFurthest,
            mHost.context().getString(section.nameRes()), body, index == mSections.size() - 1,
            index > 0);
        layout(animate);
        section.onShown();
    }

    private void layout(boolean animate) {
        FrameLayout content = mHost.findView(android.R.id.content);
        SetupSheetView sheet = mSheet;
        View root = mRoot;
        if (content == null || sheet == null || root == null)
            return;
        if (content.getHeight() <= 0 || root.getHeight() <= 0) {
            // Not laid out yet: try again on the next frame.
            content.postOnAnimation(() -> {
                if (mOpen)
                    layout(false);
            });
            return;
        }
        mWindowHeightPx = content.getHeight();
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
        Insets bars = insets == null ? Insets.NONE
            : insets.getInsets(WindowInsetsCompat.Type.systemBars());
        int[] contentInWindow = new int[2];
        content.getLocationInWindow(contentInWindow);
        mNavInsetPx = Math.max(0, contentInWindow[1] + mWindowHeightPx
            - (content.getRootView().getHeight() - bars.bottom));
        boolean miniature = mSections.get(mIndex).showsMiniature();
        int sheetHeight = Math.round(mWindowHeightPx
            * (miniature ? MINIATURE_SHEET_FRACTION : REVIEW_SHEET_FRACTION));
        sheet.setNavInset(mNavInsetPx);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        if (params.height != sheetHeight) {
            params.height = sheetHeight;
            sheet.setLayoutParams(params);
        }
        if (!miniature) {
            moveRoot(1f, 0f, animate);
            return;
        }
        float density = mHost.context().getResources().getDisplayMetrics().density;
        float top = mHost.statusBarInsetTop() + TOP_GAP_DP * density;
        float bottom = mWindowHeightPx - sheetHeight - GAP_DP * density;
        float scale = Math.max(MIN_SCALE, Math.min(1f, (bottom - top) / root.getHeight()));
        moveRoot(scale, top - rootTopInContent(root, content), animate);
    }

    private static float rootTopInContent(@NonNull View root, @NonNull View content) {
        float top = 0f;
        View view = root;
        while (view != null && view != content) {
            top += view.getTop();
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return top;
    }

    // ------------------------------------------------------------------------------------ pose

    private void moveRoot(float scale, float translationY, boolean animate) {
        View root = mRoot;
        if (root == null)
            return;
        root.setPivotX(root.getWidth() / 2f);
        root.setPivotY(0f);
        if (scale < 1f)
            clip(root, scale);
        root.animate().cancel();
        Runnable done = () -> {
            if (scale >= 1f)
                unclip(root);
        };
        if (!animate || ReducedMotion.isEnabled(mHost.context())) {
            root.setScaleX(scale);
            root.setScaleY(scale);
            root.setTranslationX(0f);
            root.setTranslationY(translationY);
            done.run();
            return;
        }
        root.animate().scaleX(scale).scaleY(scale).translationX(0f).translationY(translationY)
            .setDuration(POSE_MS).setInterpolator(new DecelerateInterpolator(2f))
            .withEndAction(done).start();
    }

    /** Rounds the miniature's corners; the radius is in the root's own px, so it grows with 1/scale. */
    private void clip(@NonNull View root, float scale) {
        if (!mClipped) {
            mSavedProvider = root.getOutlineProvider();
            mSavedClip = root.getClipToOutline();
            mClipped = true;
        }
        float radius = SetupUi.dp(root.getContext(), 28) / Math.max(scale, MIN_SCALE);
        root.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        root.setClipToOutline(true);
    }

    private void unclip(@NonNull View root) {
        if (!mClipped)
            return;
        root.setClipToOutline(mSavedClip);
        root.setOutlineProvider(mSavedProvider);
        mClipped = false;
    }

    // ---------------------------------------------------------------------------------- finish

    private void close(boolean animate) {
        if (!mOpen)
            return;
        mOpen = false;
        View root = mRoot;
        if (root != null) {
            root.animate().cancel();
            root.setScaleX(1f);
            root.setScaleY(1f);
            root.setTranslationX(0f);
            root.setTranslationY(0f);
            unclip(root);
        }
        FrameLayout layer = mLayer;
        if (layer != null && layer.getParent() instanceof ViewGroup)
            ((ViewGroup) layer.getParent()).removeView(layer);
        mLayer = null;
        mSheet = null;
        mRoot = null;
        mHost.holdPaneWall(false);
    }

    /** Records that this wizard version ran, and that the old tour never needs to start. */
    private void markDone() {
        TermuxAppSharedPreferences prefs = mHost.preferences();
        if (prefs == null)
            return;
        prefs.setSetupWizardVersion(SetupWizardGate.CURRENT);
        if (prefs.getFirstBootTourCompletedVersion() < TourController.RUN_VERSION)
            prefs.setFirstBootTourCompletedVersion(TourController.RUN_VERSION);
    }

    @Override
    public void onBack() {
        if (mIndex > 0)
            showSection(mIndex - 1, true);
    }

    @Override
    public void onSkip() {
        markDone();
        close(true);
    }

    @Override
    public void onContinue() {
        if (mIndex < mSections.size() - 1) {
            showSection(mIndex + 1, true);
            return;
        }
        SetupState state = mState;
        if (state != null)
            state.apply();
        markDone();
        close(true);
    }

    @Override
    public void onKeepCurrent() {
        SetupState state = mState;
        if (state != null)
            state.restore(state.snapshot());
        markDone();
        close(true);
    }

    @Override
    public void onStepTapped(int index) {
        if (SetupStepPolicy.canJump(mIndex, mFurthest, mSections.size(), index))
            showSection(index, true);
    }
}
