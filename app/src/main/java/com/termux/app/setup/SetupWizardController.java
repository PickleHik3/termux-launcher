package com.termux.app.setup;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.termux.R;
import com.termux.app.HomeAppChooser;
import com.termux.app.ReducedMotion;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.termux.EssentialNotificationRulesFragment;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.ShizukuBackend;
import com.termux.app.firstrun.TaiWelcomeCardHost;
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
public final class SetupWizardController implements SetupSheetView.Callbacks, SetupCardSheet.Callbacks {

    /** What the wizard needs from the activity. */
    public interface Host {
        @NonNull Context context();

        @Nullable <T extends View> T findView(int id);

        /** Holds or releases the wall's gestures. */
        void holdPaneWall(boolean held);

        int statusBarInsetTop();

        @Nullable TermuxAppSharedPreferences preferences();

        /**
         * Opens the in-app wallpaper picker. The host calls {@link #resumeFromPicker} when the
         * person is done with it (including after a photo pick and crop).
         */
        void openWallpaperPicker();

        /** Asks for the storage permission the first-run wallpaper card asks for. */
        void requestWallpaperRead();

        /** Asks for POST_NOTIFICATIONS; the host calls {@link #onNotificationPermissionResult}. */
        void requestNotificationPermission();
    }

    private static final String SHIZUKU_GUIDE_URL =
        "https://picklehik3.github.io/termux-launcher-site/docs/#wiki/permissions";
    private static final long POSE_MS = 280L;
    private static final int TOP_GAP_DP = 16;
    private static final int GAP_DP = 8;
    private static final float MIN_SCALE = 0.2f;
    private static final float MINIATURE_SHEET_FRACTION = 0.53f;
    private static final float REVIEW_SHEET_FRACTION = 0.78f;

    @NonNull private final Host mHost;
    private boolean mOpen;
    /** The wallpaper picker is up: the sheet is hidden and the root unposed, the state kept. */
    private boolean mSuspended;
    private boolean mUpdater;
    @Nullable private SetupState mState;
    private final List<SetupSection> mSections = new ArrayList<>();
    private int mIndex;
    private int mFurthest;
    @Nullable private FrameLayout mLayer;
    @Nullable private SetupSheetView mSheet;
    /** The card sheet after the wizard; set instead of {@link #mSheet} while it is up. */
    @Nullable private SetupCardSheet mCards;
    /** The notification permission was asked and refused: Continue goes to the system page. */
    private boolean mNotificationDenied;
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

    /** Open and showing: false while the wallpaper picker has the screen. */
    public boolean isActive() {
        return mOpen && !mSuspended;
    }

    public boolean isSuspended() {
        return mOpen && mSuspended;
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
        mState = new SetupState(mHost.context(), updater);
        mState.setActions(new SetupState.Actions() {
            @Override public void openWallpaperPicker() {
                suspendForPicker();
            }
            @Override public void requestWallpaperRead() {
                mHost.requestWallpaperRead();
            }
            @Override public void refresh() {
                refreshCustomize();
            }
        });
        mSuspended = false;
        mSections.clear();
        mSections.add(new HomeScreenSection());
        mSections.add(new CustomizeSection());
        mSections.add(new MultiplexerSection());
        mSections.add(new DesktopAppsSection());
        mSections.add(new AiSection());
        mSections.add(new PowerSection());
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
        if (!isActive())
            return false;
        SetupCardSheet cards = mCards;
        if (cards != null) {
            if (cards.isOnPage()) onPageBack();
            else close(true);
            return true;
        }
        if (mIndex > 0)
            showSection(mIndex - 1, true);
        return true;
    }

    /** Fits the miniature and the card to the window again, after a turn. */
    public void relayout() {
        if (isActive() && mCards != null)
            layoutCards();
        else if (isActive())
            layout(false);
    }

    /** Drops the wizard with no ceremony: the root back at rest, the wall released. */
    public void closeImmediately() {
        close(false);
    }

    // ------------------------------------------------------------------------- wallpaper picker

    /**
     * The picker takes the screen: the sheet layer goes, the root goes back to rest at once (the
     * editor poses it itself), and the state, the section and the snapshot stay.
     */
    private void suspendForPicker() {
        if (!mOpen || mSuspended)
            return;
        mSuspended = true;
        FrameLayout layer = mLayer;
        if (layer != null)
            layer.setVisibility(View.GONE);
        View root = mRoot;
        if (root != null) {
            root.animate().cancel();
            root.setScaleX(1f);
            root.setScaleY(1f);
            root.setTranslationX(0f);
            root.setTranslationY(0f);
            unclip(root);
        }
        mHost.holdPaneWall(false);
        mHost.openWallpaperPicker();
    }

    /** The picker is done: the sheet and the miniature come back on the same section. */
    public void resumeFromPicker() {
        if (!mOpen || !mSuspended)
            return;
        mSuspended = false;
        mHost.holdPaneWall(true);
        FrameLayout layer = mLayer;
        if (layer != null)
            layer.setVisibility(View.VISIBLE);
        showSection(mIndex, false);
    }

    /** The storage permission was answered: Customize shows whether the wallpaper reads now. */
    public void onWallpaperPermissionResult() {
        refreshCustomize();
    }

    private void refreshCustomize() {
        if (isActive() && mSections.get(mIndex) instanceof CustomizeSection)
            showSection(mIndex, false);
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
        if (content == null || sheet == null || root == null || mSuspended)
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
        mSuspended = false;
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
        mCards = null;
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
        // The wizard's AI section and the AI card on the sheet replace the old welcome card.
        TaiWelcomeCardHost.markShown(mHost.context());
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
        // Right after Finish: the cards, when any is still open.
        openCardsIfDue();
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


    // ------------------------------------------------------------------------------ card sheet

    /** The cards are due: the wizard is done and a card is neither done nor dismissed. */
    public boolean isCardSheetDue() {
        TermuxAppSharedPreferences prefs = mHost.preferences();
        if (prefs == null)
            return false;
        return SetupCards.isDue(prefs.getSetupWizardVersion() >= SetupWizardGate.CURRENT,
            SetupCardSystem.cards(mHost.context()));
    }

    /** Opens the card sheet when it is due; true when it is up. */
    public boolean openCardsIfDue() {
        if (mOpen)
            return true;
        if (!isCardSheetDue())
            return false;
        openCards();
        return true;
    }

    /** Opens the card sheet whatever is due (Settings' Setup row). */
    public void openCards() {
        if (mOpen)
            return;
        View root = mHost.findView(R.id.terminal_root_container);
        FrameLayout content = mHost.findView(android.R.id.content);
        if (root == null || content == null)
            return;
        mOpen = true;
        mSuspended = false;
        mRoot = root;
        mHost.holdPaneWall(true);
        mLayer = new FrameLayout(mHost.context());
        mLayer.setClickable(true);
        SetupCardSheet sheet = new SetupCardSheet(mHost.context(), this);
        mCards = sheet;
        mLayer.addView(sheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        content.addView(mLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        sheet.bindList(SetupCardSystem.cards(mHost.context()), false);
        layoutCards();
    }

    /** The sheet is 78% of the window; the launcher root stays at rest beneath it. */
    private void layoutCards() {
        FrameLayout content = mHost.findView(android.R.id.content);
        SetupCardSheet sheet = mCards;
        View root = mRoot;
        if (content == null || sheet == null || root == null)
            return;
        if (content.getHeight() <= 0) {
            content.postOnAnimation(() -> {
                if (mOpen && mCards != null)
                    layoutCards();
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
        sheet.setNavInset(mNavInsetPx);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        int height = Math.round(mWindowHeightPx * REVIEW_SHEET_FRACTION);
        if (params.height != height) {
            params.height = height;
            sheet.setLayoutParams(params);
        }
        moveRoot(1f, 0f, false);
    }

    /** The activity is back in front (a system page was left): the cards read the phone again. */
    public void onActivityResumed() {
        refreshCards();
    }

    /** POST_NOTIFICATIONS was answered. */
    public void onNotificationPermissionResult(boolean granted) {
        mNotificationDenied = !granted;
        refreshCards();
    }

    private void refreshCards() {
        SetupCardSheet sheet = mCards;
        if (mOpen && sheet != null && !sheet.isOnPage())
            sheet.bindList(SetupCardSystem.cards(mHost.context()), true);
    }

    private void startSafely(@NonNull Intent intent) {
        try {
            mHost.context().startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            // The phone has no such page; the card stays open.
        }
    }

    @Override
    public void onContinue(@NonNull String id) {
        Context context = mHost.context();
        switch (id) {
            case SetupCards.SHIZUKU: {
                PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
                manager.initializeIfNeeded(context).thenAccept(ready -> {
                    manager.requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);
                    FrameLayout layer = mLayer;
                    if (layer != null)
                        layer.post(this::refreshCards);
                });
                break;
            }
            case SetupCards.ACCESSIBILITY:
                startSafely(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                break;
            case SetupCards.NOTIFICATIONS:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !mNotificationDenied
                    && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    mHost.requestNotificationPermission();
                } else {
                    startSafely(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName()));
                }
                break;
            case SetupCards.NOTIFICATION_ACCESS:
                onNotificationAccess();
                break;
            case SetupCards.PINNED:
                startSafely(SettingsActivity.createFragmentIntent(context,
                    EssentialNotificationRulesFragment.class,
                    R.string.settings_essential_notifications_title));
                break;
            case SetupCards.HOME:
                HomeAppChooser.open(context);
                break;
            default:
                showPage(id);
                break;
        }
    }

    private void showPage(@NonNull String id) {
        SetupCardSheet sheet = mCards;
        if (sheet == null)
            return;
        for (SetupCards.Card card : SetupCardSystem.cards(mHost.context())) {
            if (card.id.equals(id)) {
                sheet.bindPage(card);
                return;
            }
        }
    }

    @Override
    public void onNotificationAccess() {
        Context context = mHost.context();
        try {
            context.startActivity(LauncherNotificationAccess.detailSettingsIntent(context));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            startSafely(LauncherNotificationAccess.listSettingsIntent());
        }
    }

    @Override
    public void onDismiss(@NonNull String id) {
        setDismissed(id, true);
    }

    @Override
    public void onReopen(@NonNull String id) {
        setDismissed(id, false);
    }

    private void setDismissed(@NonNull String id, boolean dismissed) {
        Context context = mHost.context();
        SetupCards.Stored stored = SetupCardSystem.stored(context);
        stored.setDismissed(id, dismissed);
        SetupCardSystem.save(context, stored);
        refreshCards();
    }

    @Override
    public void onOpenLauncher() {
        close(true);
    }

    @Override
    public void onSettings() {
        startSafely(new Intent(mHost.context(), SettingsActivity.class));
    }

    @Override
    public void onPageBack() {
        SetupCardSheet sheet = mCards;
        if (mOpen && sheet != null)
            sheet.bindList(SetupCardSystem.cards(mHost.context()), false);
    }

    @Override
    public void onShizukuGuide() {
        startSafely(new Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_GUIDE_URL)));
    }

    @Override
    public boolean isShizukuInstalled() {
        return SetupCardSystem.isShizukuInstalled(mHost.context());
    }

    @Override
    public boolean isNotificationAccessOn() {
        return LauncherNotificationAccess.isEnabled(mHost.context());
    }
}
