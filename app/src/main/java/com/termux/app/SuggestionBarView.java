package com.termux.app;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.DragEvent;
import android.util.AttributeSet;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.VelocityTracker;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.github.mmin18.widget.RealtimeBlurView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.launcher.LauncherAppLauncher;
import com.termux.app.launcher.PinnedAppsEditor;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.IconPack;
import com.termux.app.launcher.data.IconPackDrawableItem;
import com.termux.app.launcher.data.IconPackRepository;
import com.termux.app.launcher.data.LauncherIconResolver;
import com.termux.app.launcher.notifications.LauncherNotificationBadgeStore;
import com.termux.app.launcher.data.LauncherRankingEngine;
import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.model.IconPackInfo;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedIconOverride;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.shared.theme.ThemeUtils;
import com.termux.view.TerminalView;
import com.termux.launcherctl.LauncherCtlNotificationListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class SuggestionBarView extends GridLayout {

    public interface LaunchRippleListener {
        void onLaunchRipple(@NonNull String packageName, @Nullable Drawable icon,
                            @Nullable View sourceView);
    }

    private static final String LOG_TAG = "SuggestionBarView";
    private static final char[] AZ_ORDER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray();
    private static final int POPUP_MAX_WIDTH_DP = 320;
    private static final int POPUP_MIN_WIDTH_DP = 188;
    private static final float POPUP_MAX_HEIGHT_FACTOR = 0.45f;
    private static final long APP_LAUNCH_TOUCH_DELAY_MS = 120L;
    private static final long PICKUP_DECISION_WINDOW_MS = 650L;
    private static final float PICKUP_X_AXIS_SLOP_FACTOR = 0.9f;
    private static final float PICKUP_Y_INTENT_SLOP_FACTOR = 1.8f;
    private static final float MENU_SELECTION_ARM_SLOP_FACTOR = 0.8f;
    private static final float PAGE_SWIPE_COMMIT_DISTANCE_DP = 42f;
    private static final float PAGE_SWIPE_COMMIT_WIDTH_RATIO = 0.30f;
    private static final float PAGE_SWIPE_FLING_MIN_DISPLACEMENT_DP = 28f;
    private static final float PAGE_SWIPE_FLING_VELOCITY_PX_PER_SEC = 900f;
    private static final int PINNED_FOLDER_FILL_COLOR = 0x26FFFFFF;
    private static final int PINNED_FOLDER_STROKE_COLOR = 0x33FFFFFF;

    private List<LauncherAppEntry> allApps = new ArrayList<>();
    @Nullable private LaunchRippleListener launchRippleListener;
    private final List<LauncherAppEntry> terminalSearchEntries = new ArrayList<>();
    private final List<View> terminalSearchTargets = new ArrayList<>();
    private int terminalSearchFocusIndex = -1;
    private int maxButtonCount = 7;
    private float textSize = 12f;
    private boolean bandW = false;
    @Nullable private ColorFilter appIconColorFilter;
    private static final int ICON_RENDER_PIPELINE_VERSION = 2;
    private static final int ICON_SHADOW_COLOR = 0x47000000;
    /** Cache of harmonized icon drawables so resting and swipe-preview icons are identical (no size jump) and we don't rebuild bitmaps per frame. */
    private final LruCache<String, Drawable> normalizedIconCache = new LruCache<>(96);
    /** Visible alpha bounds per drawable; avoids rescanning custom/icon-pack artwork on every drag event. */
    private final Map<Drawable, RectF> drawableVisibleBoundsCache = new WeakHashMap<>();
    private final Map<Drawable, FocusOutlineRenderer.Visual> focusOutlineVisualCache = new WeakHashMap<>();
    private final Map<View, ValueAnimator> terminalFocusOutlineAnimators = new WeakHashMap<>();
    private final Map<View, Boolean> terminalFocusOutlineDirections = new WeakHashMap<>();
    private final Map<View, FocusOutlineRenderer.OutlineDrawable> terminalFocusOutlineDrawables = new WeakHashMap<>();
    private int searchTolerance = 70;
    private float iconScale = 1.0f;
    private int appBarOpacity = 80;
    private boolean blurEnabled = false;
    private int blurRadiusDp = 10;
    private int inheritedTintColor = 0;
    private boolean notificationBadgesEnabled = false;
    private boolean rowHapticsEnabled = true;
    @NonNull private Set<String> notificationBadgePackages = Collections.emptySet();
    @Nullable private LauncherNotificationBadgeStore.Listener notificationBadgeListener;
    private int dockRowHeightHintPx = 0;
    private List<String> defaultButtonStrings = new ArrayList<>();
    private final Map<String, WeakReference<View>> launchTargetViews = new HashMap<>();
    private final Map<String, WeakReference<View>> launchTargetViewsByPackage = new HashMap<>();
    private final Map<View, ValueAnimator> launchTouchAnimators = new WeakHashMap<>();
    private final Map<String, LauncherAppEntry> resolvedRefCache = new HashMap<>();
    private final Map<String, List<ShortcutInfo>> shortcutCache = new HashMap<>();
    private final Paint swipePreviewBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint swipePreviewBadgeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint swipePreviewFolderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint swipePreviewFolderStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect swipePreviewIconBounds = new Rect();
    private final int[] azDragRowLocation = new int[2];
    private final int[] azDragViewLocation = new int[2];
    private final RectF azDragIconBoundsScratch = new RectF();
    private final RectF azDragOutlineBoundsScratch = new RectF();

    private LauncherAppDataProvider appDataProvider;
    private LauncherConfigRepository configRepository;
    private LauncherIconResolver iconResolver;
    private IconPackRepository iconPackRepository;
    private List<PinnedItem> pinnedItems = new ArrayList<>();
    private boolean mostUsedPageEnabled = false;
    @Nullable private List<LauncherAppEntry> mostUsedEntriesCache;
    private List<SuggestionBarButton> injectedSuggestionButtons;

    private PopupWindow folderPopupWindow;
    private PopupWindow appContextPopupWindow;
    private PopupWindow shortcutsPopupWindow;
    private PopupWindow notificationPopupWindow;
    @Nullable private PopupWindow notificationInteractionPopup;
    @Nullable private NotificationPopupInteractionListener notificationPopupInteractionListener;
    @Nullable private String notificationPopupPackage;
    @NonNull private Set<String> notificationPopupKeys = Collections.emptySet();
    @Nullable private Dialog iconPickerDialog;

    private String lastInput = "";
    private TerminalView lastTerminalView;

    private Character activeAzLetter;
    private int activeAzSelection = 0;
    private int activeAzPageIndex = 0;
    private List<LauncherAppEntry> activeAzCandidates = new ArrayList<>();
    private final List<Integer> azPageStarts = new ArrayList<>();
    private int pinnedPageIndex = 0;
    private int pinnedItemsPerPage = 1;
    private float swipeDownX = 0f;
    private float swipeDownY = 0f;
    private float swipePagePosition = 0f;
    private boolean swipePageDragging = false;
    private float swipeVisualOffsetX = 0f;
    private float swipeDragProgress = 0f;
    private int swipePreviewDirection = 0;
    private int swipePreviewPageIndex = -1;
    @NonNull private List<LauncherAppEntry> swipePreviewEntries = Collections.emptyList();
    @NonNull private List<PinnedItem> swipePreviewPinnedItems = Collections.emptyList();
    @NonNull private List<List<LauncherAppEntry>> swipePreviewFolderEntries = Collections.emptyList();
    @Nullable private ValueAnimator swipePreviewReboundAnimator;
    private VelocityTracker swipeVelocityTracker;
    private boolean pageSwitchAnimating = false;
    private boolean pendingDeferredRender = false;
    private boolean suppressDrawUntilStableLayout = true;
    private boolean stableLayoutRerenderPosted = false;
    private boolean childLayoutPending = true;
    private long stableLayoutSuppressedSinceUptimeMs = 0L;
    private int lastSurfaceRenderSignature = 0;
    private boolean pendingPinnedMutationFeedback = false;
    private boolean suppressContextLongPressForSwipe = false;
    private int folderDragHoverIndex = -1;
    @Nullable private LongPressPickupState activeLongPressPickupState;
    @Nullable private AppMenuContext activeAppMenuContext;
    @Nullable private List<ShortcutInfo> activeAppMenuShortcuts;
    private final List<MenuActionRow> appContextRows = new ArrayList<>();
    private final List<MenuActionRow> shortcutsRows = new ArrayList<>();
    @Nullable private MenuActionRow activeMenuHighlight;
    private int activeMenuTintBase = 0;
    private static final long STABLE_LAYOUT_MAX_SUPPRESS_MS = 180L;
    @Nullable private TextView shortcutsMainRowView;
    private final Runnable azResetRunnable = this::clearAzPreviewWithFade;
    private static final long AZ_LAUNCH_CLEAR_DELAY_MS = 1000L;
    private final Runnable azPostLaunchClearRunnable = this::clearAzPreview;
    private final Map<Integer, LauncherAppEntry> azRenderedSlotEntries = new HashMap<>();
    private final Map<String, WeakReference<View>> azRenderedEntryTargets = new HashMap<>();
    private int azRenderedSlotCount = 0;
    private boolean azPreviewRendered = false;
    @Nullable private Character azCachedRankLetter;
    @NonNull private List<LauncherAppEntry> azCachedRankedCandidates = new ArrayList<>();
    @Nullable private Character azLastRenderLetter;
    private int azLastRenderPageIndex = -1;
    private int azLastRenderSlots = -1;
    private int azLastRenderSignature = 0;
    private int lastAzResolvedSlot = -1;
    @Nullable private LauncherUsageStatsStore usageStatsStore;
    @Nullable private Runnable appCatalogChangedListener;
    @Nullable private OverflowInteractionListener overflowInteractionListener;
    private final ExecutorService searchExecutor = newIdleFriendlyExecutor();
    private int searchGeneration = 0;
    private boolean hostVisible = true;
    private boolean rowInteractionActive = false;
    @Nullable private String azFocusedEntryKey;
    @Nullable private View azFocusedView;
    @Nullable private Animator azFocusAnimator;
    private long lastAzFocusBounceUptimeMs = 0L;
    private long azFocusLastSeenUptimeMs = 0L;
    private static final long AZ_FOCUS_BOUNCE_COOLDOWN_MS = 320L;
    private static final long AZ_FOCUS_LOSS_GRACE_MS = 180L;
    private static final float AZ_FOCUS_REST_ALPHA = 0.26f;

    public static final int AZ_EDGE_NONE = 0;
    public static final int AZ_EDGE_LEFT = -1;
    public static final int AZ_EDGE_RIGHT = 1;

    public static final class AzDragFocusResult {
        @Nullable public final LauncherAppEntry entry;
        @Nullable public final RectF iconBounds;
        @Nullable public final FocusOutlineRenderer.Visual iconOutlineVisual;
        @Nullable public final RectF iconOutlineBounds;
        @Nullable public final View launchView;
        public final int edge;
        public final boolean canPageLeft;
        public final boolean canPageRight;

        AzDragFocusResult(
            @Nullable LauncherAppEntry entry,
            @Nullable RectF iconBounds,
            @Nullable FocusOutlineRenderer.Visual iconOutlineVisual,
            @Nullable RectF iconOutlineBounds,
            @Nullable View launchView,
            int edge,
            boolean canPageLeft,
            boolean canPageRight
        ) {
            this.entry = entry;
            this.iconBounds = iconBounds;
            this.iconOutlineVisual = iconOutlineVisual;
            this.iconOutlineBounds = iconOutlineBounds;
            this.launchView = launchView;
            this.edge = edge;
            this.canPageLeft = canPageLeft;
            this.canPageRight = canPageRight;
        }

        public boolean hasFocusEntry() {
            return entry != null;
        }
    }

    /** Display bitmap plus the clean, pre-shadow artwork used for focus contour extraction. */
    private static final class RenderedIconDrawable extends BitmapDrawable {
        @NonNull final Bitmap cleanArtwork;

        RenderedIconDrawable(@NonNull Resources resources, @NonNull Bitmap display,
                             @NonNull Bitmap cleanArtwork) {
            super(resources, display);
            this.cleanArtwork = cleanArtwork;
        }
    }

    public interface OverflowInteractionListener {
        void onOverflowInteractionChanged(boolean interacting);
        default void onOverflowPagePositionChanged(float pagePosition) {}
    }

    public interface NotificationPopupInteractionListener {
        void onNotificationPopupShown();
        void onNotificationPopupDismissed();
    }

    private interface IconOverrideApplier {
        void apply(@NonNull PinnedIconOverride override);
    }

    public SuggestionBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initFocusSurface();
        inheritedTintColor = resolveLauncherPanelColor();
        activeMenuTintBase = inheritedTintColor & 0x00FFFFFF;
    }

    @NonNull
    private static ExecutorService newIdleFriendlyExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            0, 1, 15L, TimeUnit.SECONDS, new LinkedBlockingQueue<>()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private void initFocusSurface() {
        setClipChildren(false);
        setClipToPadding(false);
        setRowCount(1);
        setUseDefaultMargins(false);
        setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        swipePreviewBadgePaint.setStyle(Paint.Style.FILL);
        swipePreviewBadgeStrokePaint.setStyle(Paint.Style.STROKE);
        swipePreviewFolderPaint.setStyle(Paint.Style.FILL);
        swipePreviewFolderStrokePaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setClipChildren(false);
        setClipToPadding(false);
        prepareForWindowReentry();
        resetTransientVisualState();
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup parentGroup = (ViewGroup) parent;
            parentGroup.setClipChildren(false);
            parentGroup.setClipToPadding(false);
            ViewParent grandParent = parentGroup.getParent();
            if (grandParent instanceof ViewGroup) {
                ViewGroup grandParentGroup = (ViewGroup) grandParent;
                grandParentGroup.setClipChildren(false);
                grandParentGroup.setClipToPadding(false);
            }
        }
        attachNotificationBadgeListener();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            resetTransientVisualState();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LauncherNotificationBadgeStore.removeListener(notificationBadgeListener);
        notificationBadgeListener = null;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (suppressDrawUntilStableLayout) {
            return;
        }
        if (swipePageDragging && Math.abs(swipeVisualOffsetX) > 0.5f) {
            int currentAlpha = clamp(Math.round(255f * (1f - (0.10f * swipeDragProgress))), 0, 255);
            // Horizontal-only clip: contain the page-swap to this row's own width so the capsule
            // dock's inset interior is respected (incoming/outgoing pages don't slide over the
            // rounded border). Y stays generous so vertical badge / A-Z label overflow still draws
            // (clipChildren is intentionally false). On the edge-to-edge default dock the row spans
            // the screen, so this clip is a no-op.
            int clipSave = canvas.save();
            canvas.clipRect(0f, (float) -getHeight(), (float) getWidth(), (float) (getHeight() * 2));
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), currentAlpha);
            canvas.translate(swipeVisualOffsetX, 0f);
            super.dispatchDraw(canvas);
            canvas.restore();
            drawSwipePreviewPage(canvas);
            canvas.restoreToCount(clipSave);
            return;
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        scheduleStableDrawReleaseIfPossible();
    }

    public void setMaxButtonCount(int maxButtonCount) {
        this.maxButtonCount = maxButtonCount;
    }

    public void setTextSize(float textSize) {
        this.textSize = textSize;
    }

    public void setShowIcons(boolean showIcons) {
        // Retained for test/backward compatibility; icons are always shown.
    }

    public void setBandW(boolean bandW) {
        if (this.bandW == bandW) return;
        this.bandW = bandW;
        updateAppIconColorFilter();
        lastSurfaceRenderSignature = 0;
    }

    private void updateAppIconColorFilter() {
        if (bandW) {
            float[] grayscale = {
                0.33f, 0.33f, 0.33f, 0, 0,
                0.33f, 0.33f, 0.33f, 0, 0,
                0.33f, 0.33f, 0.33f, 0, 0,
                0, 0, 0, 1, 0
            };
            appIconColorFilter = new ColorMatrixColorFilter(grayscale);
        } else {
            appIconColorFilter = null;
        }
    }

    private void applyAppIconColorFilter(@NonNull ImageView imageView) {
        if (appIconColorFilter == null)
            imageView.clearColorFilter();
        else
            imageView.setColorFilter(appIconColorFilter);
    }

    private void invalidateRenderedIconCaches() {
        normalizedIconCache.evictAll();
        drawableVisibleBoundsCache.clear();
        focusOutlineVisualCache.clear();
        for (ValueAnimator animator : new ArrayList<>(terminalFocusOutlineAnimators.values())) {
            if (animator != null) animator.cancel();
        }
        terminalFocusOutlineAnimators.clear();
        terminalFocusOutlineDirections.clear();
        for (Map.Entry<View, FocusOutlineRenderer.OutlineDrawable> entry
                : new ArrayList<>(terminalFocusOutlineDrawables.entrySet())) {
            View target = entry.getKey();
            if (target != null && target.getForeground() == entry.getValue()) target.setForeground(null);
        }
        terminalFocusOutlineDrawables.clear();
        lastSurfaceRenderSignature = 0;
    }

    public void setIconScale(float iconScale) {
        if (Math.abs(this.iconScale - iconScale) < 0.0001f) return;
        this.iconScale = iconScale;
        invalidateRenderedIconCaches();
        childLayoutPending = true;
        requestLayout();
    }

    public void setDockRowHeightHintPx(int dockRowHeightHintPx) {
        int clamped = Math.max(0, dockRowHeightHintPx);
        if (this.dockRowHeightHintPx == clamped) {
            return;
        }
        this.dockRowHeightHintPx = clamped;
        invalidateRenderedIconCaches();
        childLayoutPending = true;
        requestLayout();
        invalidate();
        scheduleStableDrawReleaseIfPossible();
    }

    public void setAppBarOpacity(int appBarOpacity) {
        this.appBarOpacity = appBarOpacity;
    }

    public void setBlurConfig(boolean blurEnabled, int blurRadiusDp) {
        this.blurEnabled = blurEnabled;
        this.blurRadiusDp = Math.max(0, blurRadiusDp);
    }

    public void setNotificationBadgesEnabled(boolean enabled) {
        if (notificationBadgesEnabled == enabled) {
            return;
        }
        notificationBadgesEnabled = enabled;
        notificationBadgePackages = enabled ? LauncherNotificationBadgeStore.getActivePackages() : Collections.emptySet();
        invalidateNotificationBadgeViews();
    }

    public void prepareForWindowReentry() {
        suppressDrawUntilStableLayout = true;
        stableLayoutRerenderPosted = false;
        childLayoutPending = true;
        stableLayoutSuppressedSinceUptimeMs = SystemClock.uptimeMillis();
        invalidate();
        scheduleStableDrawReleaseIfPossible();
    }

    public void setInheritedTintColor(int inheritedTintColor) {
        this.inheritedTintColor = inheritedTintColor;
        this.activeMenuTintBase = inheritedTintColor & 0x00FFFFFF;
        invalidateNotificationBadgeViews();
    }

    private int resolveLauncherTextColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface));
    }

    private static int resolveLauncherTextColor(@NonNull View view) {
        return MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(view.getContext(), R.color.termux_on_surface));
    }

    private int resolveLauncherSubtleTextColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant));
    }

    private int resolveLauncherSelectedTextColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer,
            ContextCompat.getColor(getContext(), R.color.termux_on_accent_container));
    }

    private int resolveLauncherPanelColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh,
            ContextCompat.getColor(getContext(), R.color.termux_surface_panel_high));
    }

    private int resolveLauncherOutlineColor() {
        return ThemeUtils.getSystemAttrColor(getContext(), com.termux.shared.R.attr.termuxColorOutlineVariant,
            ContextCompat.getColor(getContext(), R.color.termux_outline_variant));
    }

    private int resolveNotificationBadgeColor() {
        int tertiary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary,
            ContextCompat.getColor(getContext(), R.color.termux_accent_container));
        return blendColors(tertiary, resolveLauncherTextColor(), 0.10f);
    }

    private int resolveNotificationBadgeStrokeColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh,
            ContextCompat.getColor(getContext(), R.color.termux_surface_panel_high));
    }

    private void attachNotificationBadgeListener() {
        if (notificationBadgeListener != null) {
            return;
        }
        notificationBadgeListener = packages -> post(() -> {
            notificationBadgePackages = notificationBadgesEnabled ? packages : Collections.emptySet();
            if (notificationPopupPackage != null) {
                Set<String> currentKeys = new HashSet<>();
                for (StatusBarNotification sbn :
                    LauncherNotificationBadgeStore.getNotificationsForPackage(notificationPopupPackage)) {
                    currentKeys.add(sbn.getKey() + "@" + sbn.getPostTime());
                }
                if (!currentKeys.equals(notificationPopupKeys)) dismissNotificationPopup();
            }
            invalidateNotificationBadgeViews();
        });
        LauncherNotificationBadgeStore.addListener(notificationBadgeListener);
    }

    private void invalidateNotificationBadgeViews() {
        for (int i = 0; i < getChildCount(); i++) {
            invalidateBadgeViewTree(getChildAt(i));
        }
    }

    private void invalidateBadgeViewTree(@Nullable View view) {
        if (view == null) {
            return;
        }
        if (view instanceof NotificationBadgeFrame) {
            view.invalidate();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                invalidateBadgeViewTree(group.getChildAt(i));
            }
        }
    }

    private static int withAlphaComponent(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public void setConfigRepository(@Nullable LauncherConfigRepository configRepository) {
        this.configRepository = configRepository;
        if (this.configRepository != null) {
            this.pinnedItems = this.configRepository.loadPinnedItems();
        }
    }

    public void setAppDataProvider(@Nullable LauncherAppDataProvider appDataProvider) {
        this.appDataProvider = appDataProvider;
    }

    public void setAppCatalogChangedListener(@Nullable Runnable appCatalogChangedListener) {
        this.appCatalogChangedListener = appCatalogChangedListener;
    }

    public void setOverflowInteractionListener(@Nullable OverflowInteractionListener listener) {
        overflowInteractionListener = listener;
    }

    public void setNotificationPopupInteractionListener(
        @Nullable NotificationPopupInteractionListener listener) {
        notificationPopupInteractionListener = listener;
    }

    public void setHostVisible(boolean visible) {
        hostVisible = visible;
        if (visible) {
            scheduleStableDrawReleaseIfPossible();
            return;
        }
        searchGeneration++;
        pendingDeferredRender = false;
        stableLayoutRerenderPosted = false;
        removeCallbacks(azResetRunnable);
        removeCallbacks(azPostLaunchClearRunnable);
        clearAzFocusedEntry();
        dismissShortcutsPopup();
        dismissAppContextPopup();
        dismissFolderPopup();
        dismissNotificationPopup();
        dismissIconPickerPopup();
    }

    private LauncherUsageStatsStore getUsageStatsStore() {
        if (usageStatsStore == null) {
            usageStatsStore = new LauncherUsageStatsStore(getContext());
        }
        return usageStatsStore;
    }

    public void clearLauncherUsageRanking() {
        getUsageStatsStore().clear();
        invalidateAzRankCache();
        if (activeAzLetter != null) {
            previewAzLetter(activeAzLetter, activeAzSelection, false);
        }
    }

    public boolean isSearchSurfaceActive() {
        return !TextUtils.isEmpty(lastInput.trim());
    }

    public void setDefaultButtons(List<String> defaultButtons) {
        if (defaultButtons == null) {
            this.defaultButtonStrings = new ArrayList<>();
        } else {
            this.defaultButtonStrings = new ArrayList<>(defaultButtons);
        }
    }

    public void clearAppCache() {
        allApps = new ArrayList<>();
        invalidateRenderedIconCaches();
        activeAzLetter = null;
        activeAzCandidates = new ArrayList<>();
        activeAzPageIndex = 0;
        injectedSuggestionButtons = null;
        invalidateAzRankCache();
        invalidateAzRenderState();
        launchTargetViews.clear();
        launchTargetViewsByPackage.clear();
        resolvedRefCache.clear();
        shortcutCache.clear();
        cancelAzResetTimeout();
        if (appDataProvider != null) {
            appDataProvider.invalidate();
        }
        if (iconResolver != null) iconResolver.clearCache();
        if (iconPackRepository != null) iconPackRepository.clearCache();
    }

    /** Reconciles persisted choices after an icon pack is removed or replaces its resources. */
    public void pruneInvalidIconOverrides() {
        if (configRepository == null) return;
        boolean changed = configRepository.pruneInvalidIconOverrides(
            override -> getIconResolver().loadOverride(override) != null);
        if (changed) {
            pinnedItems = configRepository.loadPinnedItems();
            invalidateRenderedIconCaches();
        }
    }

    public void setSuggestionButtons(@Nullable List<? extends SuggestionBarButton> suggestionButtons) {
        if (suggestionButtons == null) {
            this.injectedSuggestionButtons = null;
        } else {
            this.injectedSuggestionButtons = new ArrayList<>(suggestionButtons);
        }
        this.allApps = injectedToEntries(this.injectedSuggestionButtons);
    }

    void reloadAllApps() {
        if (injectedSuggestionButtons != null) {
            allApps = injectedToEntries(injectedSuggestionButtons);
            resolvedRefCache.clear();
            shortcutCache.clear();
            pruneUnavailablePinnedItems();
            if (appCatalogChangedListener != null) {
                appCatalogChangedListener.run();
            }
            return;
        }
        if (appDataProvider == null) {
            appDataProvider = LauncherAppDataProvider.getInstance(getContext());
        }
        if (iconResolver == null) {
            iconResolver = new LauncherIconResolver(getContext());
        }
        if (iconPackRepository == null) {
            iconPackRepository = new IconPackRepository(getContext());
        }
        if (!appDataProvider.hasLoadedApps()) {
            appDataProvider.warmAsync(() -> {
                if (!hostVisible || !isAttachedToWindow()) {
                    return;
                }
                allApps = appDataProvider.getAllApps();
                resolvedRefCache.clear();
                shortcutCache.clear();
                pruneUnavailablePinnedItems();
                invalidateAzRankCache();
                if (appCatalogChangedListener != null) {
                    appCatalogChangedListener.run();
                }
                reloadWithInput(lastInput, lastTerminalView);
            });
            return;
        }
        allApps = appDataProvider.getAllApps();
        resolvedRefCache.clear();
        shortcutCache.clear();
        pruneUnavailablePinnedItems();
        if (appCatalogChangedListener != null) {
            appCatalogChangedListener.run();
        }
        invalidateAzRankCache();
    }

    /**
     * Removes pinned references that no longer resolve to installed launchable apps.
     * This prevents stale "ghost" pinned slots after apps are uninstalled.
     */
    private void pruneUnavailablePinnedItems() {
        if (configRepository == null || pinnedItems == null || pinnedItems.isEmpty() || allApps == null || allApps.isEmpty()) {
            return;
        }

        List<PinnedItem> cleaned = new ArrayList<>();
        boolean changed = false;

        for (PinnedItem item : pinnedItems) {
            if (item instanceof PinnedAppItem) {
                PinnedAppItem appItem = (PinnedAppItem) item;
                AppRef normalizedRef = resolveNormalizedPinnedRef(appItem.appRef);
                if (normalizedRef == null) {
                    changed = true;
                    continue;
                }
                if (!normalizedRef.stableId().equals(appItem.appRef.stableId())) {
                    changed = true;
                }
                cleaned.add(new PinnedAppItem(normalizedRef, appItem.iconOverride));
                continue;
            }

            if (item instanceof PinnedFolderItem) {
                PinnedFolderItem folder = (PinnedFolderItem) clonePinnedItem(item);
                int before = folder.apps.size();
                LinkedHashSet<String> seenStableIds = new LinkedHashSet<>();
                List<AppRef> normalizedApps = new ArrayList<>();
                List<PinnedAppItem> normalizedFolderApps = new ArrayList<>();
                for (int i = folder.apps.size() - 1; i >= 0; i--) {
                    PinnedAppItem folderApp = folder.apps.get(i);
                    AppRef normalizedRef = resolveNormalizedPinnedRef(folderApp.appRef);
                    if (normalizedRef == null) {
                        changed = true;
                        continue;
                    }
                    if (!seenStableIds.add(normalizedRef.stableId())) {
                        changed = true;
                        continue;
                    }
                    normalizedApps.add(0, normalizedRef);
                    normalizedFolderApps.add(0, new PinnedAppItem(normalizedRef, folderApp.iconOverride));
                }
                if (normalizedApps.isEmpty()) {
                    changed = true;
                    continue;
                }
                if (normalizedApps.size() == 1) {
                    changed = true;
                    cleaned.add(normalizedFolderApps.get(0));
                    continue;
                }
                if (normalizedApps.size() != before) {
                    changed = true;
                }
                for (int i = 0; i < normalizedApps.size(); i++) {
                    AppRef oldRef = i < folder.apps.size() ? folder.apps.get(i).appRef : null;
                    AppRef newRef = normalizedApps.get(i);
                    if (oldRef == null || !oldRef.stableId().equals(newRef.stableId())) {
                        changed = true;
                        break;
                    }
                }
                folder.apps.clear();
                folder.apps.addAll(normalizedFolderApps);
                cleaned.add(folder);
                continue;
            }

            cleaned.add(item);
        }

        if (!changed) {
            return;
        }

        pinnedItems = cleaned;
        configRepository.savePinnedItems(pinnedItems);
        pinnedPageIndex = clamp(pinnedPageIndex, 0, Math.max(0, getPinnedPagesCount() - 1));
    }

    @Nullable
    private AppRef resolveNormalizedPinnedRef(@NonNull AppRef ref) {
        LauncherAppEntry resolved = resolveRef(ref);
        if (resolved == null) {
            return null;
        }
        return resolveForSelectionRef(resolved.appRef);
    }

    public void previewAzLetter(char letter, int selectionIndex, boolean commit) {
        cancelAzPostLaunchClear();
        if (appDataProvider == null) {
            appDataProvider = LauncherAppDataProvider.getInstance(getContext());
        }
        if (!appDataProvider.hasLoadedApps()) {
            appDataProvider.warmAsync(() -> {
                if (!hostVisible || !isAttachedToWindow()) {
                    return;
                }
                previewAzLetter(letter, selectionIndex, commit);
            });
            return;
        }
        char normalized = Character.toUpperCase(letter);
        if (activeAzLetter == null || activeAzLetter != normalized) {
            activeAzPageIndex = 0;
        }
        activeAzLetter = normalized;
        activeAzSelection = Math.max(0, selectionIndex);
        cancelAzResetTimeout();
        refreshActiveAzCandidates(activeAzLetter);
        if (activeAzCandidates.isEmpty()) {
            if (commit) {
                clearAzPreview();
            }
            return;
        }

        if (commit) {
            int pageOffset = getAzPageStart(activeAzCandidates, activeAzPageIndex, Math.max(1, maxButtonCount));
            int index = pageOffset + Math.min(activeAzSelection, Math.max(0, maxButtonCount - 1));
            index = Math.min(index, activeAzCandidates.size() - 1);
            launchEntry(activeAzCandidates.get(index), lastTerminalView);
            clearAzPreview();
            return;
        }

        if (shouldSkipAzPreviewRender(activeAzLetter, activeAzPageIndex, Math.max(1, maxButtonCount), activeAzCandidates)) {
            return;
        }
        renderButtons(activeAzCandidates, true);
        captureAzRenderState(activeAzLetter, activeAzPageIndex, Math.max(1, maxButtonCount), activeAzCandidates);
    }

    public void persistAzPreview(char letter, int selectionIndex) {
        previewAzLetter(letter, selectionIndex, false);
        scheduleAzResetTimeout();
    }

    @NonNull
    public Set<Character> getAvailableAzLetters() {
        if (allApps == null || allApps.isEmpty()) {
            reloadAllApps();
        }
        LinkedHashSet<Character> letters = new LinkedHashSet<>();
        if (allApps != null) {
            for (LauncherAppEntry app : allApps) {
                char letter = LauncherAppDataProvider.normalizeLetter(app.label == null ? "" : app.label);
                letters.add(letter);
            }
        }
        if (letters.isEmpty()) {
            letters.add('#');
        }
        return letters;
    }

    public boolean isAzPreviewActive() {
        return activeAzLetter != null && activeAzCandidates != null && !activeAzCandidates.isEmpty();
    }

    public boolean hasAzOverflowPages() {
        return isAzPreviewActive() && getAzPagesCount() > 1;
    }

    public boolean canAzPageLeft() {
        return hasAzOverflowPages();
    }

    public boolean canAzPageRight() {
        return hasAzOverflowPages();
    }

    public int getAzCurrentPageIndex() {
        return Math.max(0, activeAzPageIndex);
    }

    public float getAzVisualPagePosition() {
        return (hasAzOverflowPages() && (swipePageDragging || pageSwitchAnimating))
            ? swipePagePosition
            : getAzCurrentPageIndex();
    }

    public int getAzVisiblePageCount() {
        return getAzPagesCount();
    }

    public boolean hasPinnedOverflowPages() {
        return activeAzLetter == null
            && TextUtils.isEmpty(lastInput.trim())
            && pinnedItems != null
            && !pinnedItems.isEmpty()
            && getPinnedPagesCount() > 1;
    }

    public boolean canPinnedPageLeft() {
        return hasPinnedOverflowPages();
    }

    public boolean canPinnedPageRight() {
        return hasPinnedOverflowPages();
    }

    public int getPinnedCurrentPageIndex() {
        return Math.max(0, pinnedPageIndex);
    }

    public float getPinnedVisualPagePosition() {
        return (hasPinnedOverflowPages() && (swipePageDragging || pageSwitchAnimating))
            ? swipePagePosition
            : getPinnedCurrentPageIndex();
    }

    public int getPinnedVisiblePageCount() {
        return Math.max(1, getPinnedPagesCount());
    }

    public boolean requestAzPageDelta(int pageDelta, float velocityPxPerSec) {
        if (!isAzPreviewActive() || pageDelta == 0 || pageSwitchAnimating) {
            return false;
        }
        int totalPages = getAzPagesCount();
        if (totalPages <= 1) {
            return false;
        }
        swipePagePosition = getAzCurrentPageIndex();
        animateAzPageSwitch(pageDelta, velocityPxPerSec);
        return true;
    }

    public AzDragFocusResult resolveAzDragFocus(float rawX, float rawY) {
        boolean pageLeft = canAzPageLeft();
        boolean pageRight = canAzPageRight();
        if (!isAzPreviewActive() || !azPreviewRendered || azRenderedSlotCount <= 0) {
            lastAzResolvedSlot = -1;
            return new AzDragFocusResult(null, null, null, null, null, AZ_EDGE_NONE, pageLeft, pageRight);
        }

        getLocationOnScreen(azDragRowLocation);
        float localX = rawX - azDragRowLocation[0];
        float localY = rawY - azDragRowLocation[1];
        float width = Math.max(1f, getWidth());
        float height = Math.max(1f, getHeight());

        int edge = AZ_EDGE_NONE;
        float edgeZone = Math.max(dp(18), width * 0.055f);
        if (localX <= edgeZone && pageLeft) {
            edge = AZ_EDGE_LEFT;
        } else if (localX >= (width - edgeZone) && pageRight) {
            edge = AZ_EDGE_RIGHT;
        }

        if (localY < -dp(24) || localY > height + dp(24)) {
            lastAzResolvedSlot = -1;
            return new AzDragFocusResult(null, null, null, null, null, edge, pageLeft, pageRight);
        }

        float clampedX = Math.max(0f, Math.min(width - 1f, localX));
        float slotWidth = width / Math.max(1f, azRenderedSlotCount);
        int candidateSlot = clamp((int) ((clampedX / width) * azRenderedSlotCount), 0, azRenderedSlotCount - 1);
        int slot = candidateSlot;
        if (lastAzResolvedSlot >= 0 && lastAzResolvedSlot < azRenderedSlotCount && candidateSlot != lastAzResolvedSlot) {
            float hysteresis = slotWidth * AzScrubRowView.LETTER_SLOT_HYSTERESIS_RATIO;
            if (candidateSlot > lastAzResolvedSlot) {
                float boundary = (lastAzResolvedSlot + 1) * slotWidth;
                if (clampedX < boundary + hysteresis) {
                    slot = lastAzResolvedSlot;
                }
            } else {
                float boundary = lastAzResolvedSlot * slotWidth;
                if (clampedX > boundary - hysteresis) {
                    slot = lastAzResolvedSlot;
                }
            }
        }
        LauncherAppEntry entry = azRenderedSlotEntries.get(slot);
        if (entry == null) {
            lastAzResolvedSlot = -1;
            return new AzDragFocusResult(null, null, null, null, null, edge, pageLeft, pageRight);
        }
        lastAzResolvedSlot = slot;

        String key = stableEntryKey(entry);
        WeakReference<View> viewRef = azRenderedEntryTargets.get(key);
        View launchView = viewRef == null ? null : viewRef.get();
        boolean hasBounds = false;
        FocusOutlineRenderer.Visual outlineVisual = null;
        boolean hasOutlineBounds = false;
        if (launchView != null && launchView.isAttachedToWindow()) {
            resolveVisibleIconBoundsOnScreen(launchView, azDragIconBoundsScratch);
            hasBounds = !azDragIconBoundsScratch.isEmpty();
            if (launchView instanceof ImageView) {
                FocusOutlineRenderer.Visual visual = resolveFocusOutlineVisual((ImageView) launchView);
                if (visual != null) {
                    launchView.getLocationOnScreen(azDragViewLocation);
                    outlineVisual = visual;
                    azDragOutlineBoundsScratch.set(
                        azDragViewLocation[0] - visual.outerPadding,
                        azDragViewLocation[1] - visual.outerPadding,
                        azDragViewLocation[0] + visual.sourceWidth + visual.outerPadding,
                        azDragViewLocation[1] + visual.sourceHeight + visual.outerPadding
                    );
                    hasOutlineBounds = true;
                }
            }
        }
        if (!hasBounds) {
            approximateAzSlotIconBounds(
                slot, azRenderedSlotCount, azDragRowLocation, width, height, azDragIconBoundsScratch);
        }
        return new AzDragFocusResult(
            entry,
            new RectF(azDragIconBoundsScratch),
            outlineVisual,
            hasOutlineBounds ? new RectF(azDragOutlineBoundsScratch) : null,
            launchView,
            edge,
            pageLeft,
            pageRight
        );
    }

    @Nullable
    private FocusOutlineRenderer.Visual resolveFocusOutlineVisual(@NonNull ImageView imageView) {
        Drawable drawable = imageView.getDrawable();
        int width = imageView.getWidth();
        int height = imageView.getHeight();
        if (drawable == null || width <= 0 || height <= 0 || drawable.getBounds().isEmpty()) {
            return null;
        }
        FocusOutlineRenderer.Visual cached = focusOutlineVisualCache.get(drawable);
        if (cached != null && cached.sourceWidth == width && cached.sourceHeight == height) {
            return cached;
        }

        // Focus geometry must come from clean artwork. The display bitmap deliberately contains a
        // downward contact shadow, which would otherwise skew the alpha contour on every icon.
        Bitmap source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas sourceCanvas = new Canvas(source);
        sourceCanvas.translate(imageView.getPaddingLeft(), imageView.getPaddingTop());
        sourceCanvas.concat(imageView.getImageMatrix());
        if (drawable instanceof RenderedIconDrawable) {
            RenderedIconDrawable rendered = (RenderedIconDrawable) drawable;
            Paint artworkPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            sourceCanvas.drawBitmap(rendered.cleanArtwork, null, drawable.getBounds(), artworkPaint);
        } else {
            drawable.draw(sourceCanvas);
        }
        FocusOutlineRenderer.Visual built = FocusOutlineRenderer.buildVisual(
            source, getResources().getDisplayMetrics().density);
        source.recycle();
        focusOutlineVisualCache.put(drawable, built);
        return built;
    }

    /** Builds a crisp external contour from the icon alpha, including irregular icon-pack shapes. */
    @NonNull
    static Bitmap buildFocusOutlineMask(@NonNull Bitmap source, int gap, int stroke) {
        return FocusOutlineRenderer.buildFocusOutlineMask(source, gap, stroke);
    }

    /**
     * Returns the visible artwork bounds instead of the ImageButton's touch rectangle. Icon packs
     * and legacy/custom Android icons commonly include asymmetric transparent padding; using the
     * whole view makes the focus ring oversized and visibly off-center.
     */
    private void resolveVisibleIconBoundsOnScreen(@NonNull View launchView, @NonNull RectF out) {
        launchView.getLocationOnScreen(azDragViewLocation);
        out.set(
            azDragViewLocation[0],
            azDragViewLocation[1],
            azDragViewLocation[0] + launchView.getWidth(),
            azDragViewLocation[1] + launchView.getHeight()
        );
        if (!(launchView instanceof ImageView)) {
            return;
        }

        ImageView imageView = (ImageView) launchView;
        Drawable drawable = imageView.getDrawable();
        if (drawable == null) {
            return;
        }
        Rect drawableBounds = drawable.getBounds();
        if (drawableBounds.isEmpty()) {
            return;
        }

        RectF normalizedVisibleBounds = drawableVisibleBoundsCache.get(drawable);
        if (normalizedVisibleBounds == null) {
            normalizedVisibleBounds = measureDrawableVisibleBounds(drawable);
            drawableVisibleBoundsCache.put(drawable, normalizedVisibleBounds);
        }
        azDragOutlineBoundsScratch.set(
            drawableBounds.left + (normalizedVisibleBounds.left * drawableBounds.width()),
            drawableBounds.top + (normalizedVisibleBounds.top * drawableBounds.height()),
            drawableBounds.left + (normalizedVisibleBounds.right * drawableBounds.width()),
            drawableBounds.top + (normalizedVisibleBounds.bottom * drawableBounds.height())
        );
        imageView.getImageMatrix().mapRect(azDragOutlineBoundsScratch);
        azDragOutlineBoundsScratch.offset(
            azDragViewLocation[0] + imageView.getPaddingLeft(),
            azDragViewLocation[1] + imageView.getPaddingTop()
        );
        if (azDragOutlineBoundsScratch.width() > 0f && azDragOutlineBoundsScratch.height() > 0f) {
            out.set(azDragOutlineBoundsScratch);
        }
    }

    @NonNull
    private RectF measureDrawableVisibleBounds(@NonNull Drawable drawable) {
        final int scanSize = 128;
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        float aspect = intrinsicWidth > 0 && intrinsicHeight > 0
            ? intrinsicWidth / (float) intrinsicHeight
            : 1f;
        int width = aspect >= 1f ? scanSize : Math.max(1, Math.round(scanSize * aspect));
        int height = aspect >= 1f ? Math.max(1, Math.round(scanSize / aspect)) : scanSize;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        if (drawable instanceof RenderedIconDrawable) {
            canvas.drawBitmap(((RenderedIconDrawable) drawable).cleanArtwork, null,
                new Rect(0, 0, width, height),
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        } else {
            Drawable scanDrawable = drawable;
            Drawable.ConstantState state = drawable.getConstantState();
            if (state != null) {
                scanDrawable = state.newDrawable(getResources()).mutate();
            }
            Rect oldBounds = new Rect(scanDrawable.getBounds());
            scanDrawable.setBounds(0, 0, width, height);
            scanDrawable.draw(canvas);
            scanDrawable.setBounds(oldBounds);
        }

        Rect visible = findVisibleAlphaBounds(bitmap);
        bitmap.recycle();
        if (visible.isEmpty()) {
            return new RectF(0f, 0f, 1f, 1f);
        }
        return new RectF(
            visible.left / (float) width,
            visible.top / (float) height,
            visible.right / (float) width,
            visible.bottom / (float) height
        );
    }

    /** Alpha threshold excludes the dock's soft icon shadow while retaining antialiased artwork. */
    @NonNull
    static Rect findVisibleAlphaBounds(@NonNull Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int maxAlpha = 0;
        for (int pixel : pixels) {
            maxAlpha = Math.max(maxAlpha, pixel >>> 24);
        }
        int threshold = Math.max(8, Math.round(maxAlpha * 0.25f));
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                if ((pixels[row + x] >>> 24) < threshold) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return right >= left && bottom >= top
            ? new Rect(left, top, right + 1, bottom + 1)
            : new Rect();
    }

    private void approximateAzSlotIconBounds(int slot, int slotCount, @NonNull int[] rowLocation,
                                             float width, float height, @NonNull RectF out) {
        float safeSlotCount = Math.max(1, slotCount);
        float slotWidth = width / safeSlotCount;
        float cx = (slotWidth * slot) + (slotWidth * 0.5f);
        float cy = height * 0.5f;
        float size = iconSizePx();
        out.set(
            rowLocation[0] + cx - (size * 0.5f),
            rowLocation[1] + cy - (size * 0.5f),
            rowLocation[0] + cx + (size * 0.5f),
            rowLocation[1] + cy + (size * 0.5f)
        );
    }

    public boolean launchAzFocusedEntry(@Nullable AzDragFocusResult focusResult) {
        if (focusResult == null || focusResult.entry == null) {
            return false;
        }
        launchEntry(focusResult.entry, lastTerminalView, focusResult.launchView);
        removeCallbacks(azResetRunnable);
        removeCallbacks(azPostLaunchClearRunnable);
        postDelayed(azPostLaunchClearRunnable, AZ_LAUNCH_CLEAR_DELAY_MS);
        return true;
    }

    public void updateAzFocusedEntry(@Nullable AzDragFocusResult focusResult) {
        long now = SystemClock.uptimeMillis();
        if (focusResult == null || focusResult.entry == null) {
            if (azFocusedView != null && (now - azFocusLastSeenUptimeMs) <= AZ_FOCUS_LOSS_GRACE_MS) {
                return;
            }
            clearAzFocusedEntry();
            return;
        }
        String key = stableEntryKey(focusResult.entry);
        View target = focusResult.launchView;
        if (target != null) {
            target = resolvePrimaryPressTarget(target);
        }
        if (target == null || !target.isAttachedToWindow()) {
            WeakReference<View> ref = azRenderedEntryTargets.get(key);
            target = ref == null ? null : ref.get();
            if (target != null) {
                target = resolvePrimaryPressTarget(target);
            }
        }
        if (target == null || !target.isAttachedToWindow()) {
            if (azFocusedView != null && (now - azFocusLastSeenUptimeMs) <= AZ_FOCUS_LOSS_GRACE_MS) {
                return;
            }
            clearAzFocusedEntry();
            return;
        }
        azFocusLastSeenUptimeMs = now;
        if (key.equals(azFocusedEntryKey) && target == azFocusedView) {
            return;
        }
        boolean movedBetweenApps = azFocusedEntryKey != null
            && !azFocusedEntryKey.equals(key);
        clearAzFocusedEntry();
        azFocusedEntryKey = key;
        azFocusedView = target;
        if (rowHapticsEnabled && movedBetweenApps) {
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
        if ((now - lastAzFocusBounceUptimeMs) >= AZ_FOCUS_BOUNCE_COOLDOWN_MS) {
            lastAzFocusBounceUptimeMs = now;
            animateAzFocusAlpha(target);
        } else {
            applyAzFocusRestState(target);
        }
    }

    public void clearAzFocusedEntry() {
        if (azFocusAnimator != null) {
            azFocusAnimator.cancel();
            azFocusAnimator = null;
        }
        if (azFocusedView != null) {
            azFocusedView.animate().cancel();
            azFocusedView.setScaleX(1f);
            azFocusedView.setScaleY(1f);
            azFocusedView.setTranslationY(0f);
            azFocusedView.animate()
                .alpha(1f)
                .setDuration(96L)
                .setInterpolator(new DecelerateInterpolator(1.45f))
                .setListener(null)
                .start();
        }
        azFocusedView = null;
        azFocusedEntryKey = null;
        azFocusLastSeenUptimeMs = 0L;
    }

    private void animateAzFocusAlpha(@NonNull View target) {
        target.animate().cancel();
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setTranslationY(0f);
        AnimatorSet bounce = new AnimatorSet();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(target, View.ALPHA, target.getAlpha(), AZ_FOCUS_REST_ALPHA);
        bounce.playTogether(alpha);
        bounce.setDuration(86L);
        bounce.setInterpolator(new DecelerateInterpolator(1.55f));
        bounce.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (target == azFocusedView) {
                    applyAzFocusRestState(target);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (target == azFocusedView) {
                    applyAzFocusRestState(target);
                }
            }
        });
        azFocusAnimator = bounce;
        bounce.start();
    }

    private void applyAzFocusRestState(@NonNull View target) {
        target.setScaleX(1f);
        target.setScaleY(1f);
        target.setTranslationY(0f);
        target.setAlpha(AZ_FOCUS_REST_ALPHA);
    }

    public void clearAzPreview() {
        cancelAzResetTimeout();
        cancelAzPostLaunchClear();
        clearAzFocusedEntry();
        activeAzLetter = null;
        activeAzSelection = 0;
        activeAzPageIndex = 0;
        activeAzCandidates = new ArrayList<>();
        invalidateAzRenderState();
        reloadWithInput(lastInput, lastTerminalView);
    }

    public void clearAzPreviewWithFade() {
        if (activeAzLetter == null) return;
        cancelAzResetTimeout();
        animate()
            .alpha(0.35f)
            .setDuration(120)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setListenerSafe(null);
                    activeAzLetter = null;
                    activeAzSelection = 0;
                    activeAzPageIndex = 0;
                    activeAzCandidates = new ArrayList<>();
                    invalidateAzRenderState();
                    reloadWithInput(lastInput, lastTerminalView);
                    setAlpha(0.35f);
                    animate()
                        .alpha(1f)
                        .setDuration(160)
                        .setInterpolator(new DecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                setListenerSafe(null);
                                setAlpha(1f);
                            }
                        })
                        .start();
                }
            })
            .start();
    }

    public void onTerminalInteraction() {
        if (activeAzLetter != null) {
            clearAzPreviewWithFade();
        }
    }

    private void scheduleAzResetTimeout() {
        cancelAzResetTimeout();
        postDelayed(azResetRunnable, 5000);
    }

    private void cancelAzResetTimeout() {
        removeCallbacks(azResetRunnable);
    }

    private void cancelAzPostLaunchClear() {
        removeCallbacks(azPostLaunchClearRunnable);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event == null) return super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (activeAzLetter != null) {
                scheduleAzResetTimeout();
            }
            // Always normalize row transform at new gesture start to avoid stale offsets.
            animate().cancel();
            cancelSwipePreviewRebound();
            setListenerSafe(null);
            pageSwitchAnimating = false;
            setTranslationX(0f);
            setAlpha(1f);
            suppressContextLongPressForSwipe = false;
            swipePageDragging = false;
            swipePagePosition = resolveCurrentSwipePagePosition();
            clearSwipePagePreview();
            setRowInteractionActive(true);
            if (swipeVelocityTracker != null) swipeVelocityTracker.recycle();
            swipeVelocityTracker = VelocityTracker.obtain();
            swipeVelocityTracker.addMovement(event);
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            swipeDownX = event.getX();
            swipeDownY = event.getY();
        } else if (action == MotionEvent.ACTION_MOVE) {
            setRowInteractionActive(true);
            if (swipeVelocityTracker != null) swipeVelocityTracker.addMovement(event);
            if (activeAzLetter != null) {
                scheduleAzResetTimeout();
            }
            float dx = event.getX() - swipeDownX;
            float dy = event.getY() - swipeDownY;
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            boolean horizontalIntent = Math.abs(dx) >= slop && Math.abs(dx) > (Math.abs(dy) * 1.1f);
            if (horizontalIntent && TextUtils.isEmpty(lastInput.trim())) {
                suppressContextLongPressForSwipe = true;
                cancelPendingContextLongPresses();
                applySwipePageDragFeedback(dx);
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (activeAzLetter != null) {
                scheduleAzResetTimeout();
            }
            if (swipeVelocityTracker != null) {
                swipeVelocityTracker.addMovement(event);
                swipeVelocityTracker.computeCurrentVelocity(1000);
            }
            float dx = event.getX() - swipeDownX;
            float dy = event.getY() - swipeDownY;
            float vx = swipeVelocityTracker == null ? 0f : swipeVelocityTracker.getXVelocity();
            float commitDistance = resolvePageSwipeCommitDistancePx();
            boolean distanceCommit = Math.abs(dx) > commitDistance;
            boolean velocityCommit = Math.abs(dx) >= dp(PAGE_SWIPE_FLING_MIN_DISPLACEMENT_DP)
                && Math.abs(vx) > PAGE_SWIPE_FLING_VELOCITY_PX_PER_SEC
                && Math.signum(dx) == Math.signum(vx);
            boolean swipeQualified = distanceCommit || velocityCommit;
            if (swipeQualified && Math.abs(dx) > Math.abs(dy) * 1.2f &&
                TextUtils.isEmpty(lastInput.trim())) {
                int pageDelta = dx < 0 ? 1 : -1;
                if (activeAzLetter != null) {
                    int totalPages = getAzPagesCount();
                    if (totalPages > 1) {
                        int next = wrapAzPageIndex(activeAzPageIndex + pageDelta, totalPages);
                        if (next != activeAzPageIndex) {
                            animateAzPageSwitch(pageDelta, Math.max(Math.abs(vx), Math.abs(dx) * 8f));
                            if (swipeVelocityTracker != null) {
                                swipeVelocityTracker.recycle();
                                swipeVelocityTracker = null;
                            }
                            suppressContextLongPressForSwipe = false;
                            return true;
                        }
                    }
                } else if (pinnedItemsPerPage > 0) {
                    int totalPages = getPinnedPagesCount();
                    if (totalPages > 1) {
                        int next = wrapPageIndex(pinnedPageIndex + pageDelta, totalPages);
                        if (next != pinnedPageIndex) {
                            animatePageSwitch(pageDelta, Math.max(Math.abs(vx), Math.abs(dx) * 8f));
                            if (swipeVelocityTracker != null) {
                                swipeVelocityTracker.recycle();
                                swipeVelocityTracker = null;
                            }
                            suppressContextLongPressForSwipe = false;
                            return true;
                        }
                    }
                }
            }
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
            if (swipeVelocityTracker != null) {
                swipeVelocityTracker.recycle();
                swipeVelocityTracker = null;
            }
            suppressContextLongPressForSwipe = false;
            if (!pageSwitchAnimating) {
                animateSwipePageDragBack();
            }
            setRowInteractionActive(false);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
            if (swipeVelocityTracker != null) {
                swipeVelocityTracker.recycle();
                swipeVelocityTracker = null;
            }
            suppressContextLongPressForSwipe = false;
            if (!pageSwitchAnimating) {
                animateSwipePageDragBack();
            }
            setRowInteractionActive(false);
        }
        return super.dispatchTouchEvent(event);
    }

    void reloadWithInput(String input, final TerminalView terminalView) {
        if (allApps == null || allApps.isEmpty()) {
            reloadAllApps();
        }
        if (injectedSuggestionButtons == null && appDataProvider != null && appDataProvider.hasLoadedApps()) {
            allApps = appDataProvider.getAllApps();
        }

        this.lastTerminalView = terminalView;
        String nextInput = input == null ? "" : input;
        if (!nextInput.trim().equals(lastInput.trim())) clearTerminalSearchFocus();
        this.lastInput = nextInput;
        final int requestGeneration = ++searchGeneration;
        if (activeAzLetter != null && !this.lastInput.trim().isEmpty()) {
            activeAzLetter = null;
            activeAzSelection = 0;
            activeAzPageIndex = 0;
            activeAzCandidates = new ArrayList<>();
            invalidateAzRenderState();
            cancelAzResetTimeout();
        }

        if (activeAzLetter != null) {
            List<LauncherAppEntry> candidates = appDataProvider.getAppsForLetter(activeAzLetter);
            activeAzCandidates = getUsageStatsStore().rankForAz(candidates);
            azCachedRankLetter = activeAzLetter;
            azCachedRankedCandidates = activeAzCandidates;
            renderButtons(activeAzCandidates, true);
            captureAzRenderState(activeAzLetter, activeAzPageIndex, Math.max(1, maxButtonCount), activeAzCandidates);
            return;
        }

        String trimmed = lastInput.trim();
        if (!trimmed.isEmpty()) {
            final List<LauncherAppEntry> snapshot = new ArrayList<>(allApps);
            searchExecutor.execute(() -> {
                List<LauncherAppEntry> suggestionEntries = LauncherRankingEngine.filterAndRank(snapshot, trimmed, searchTolerance);
                post(() -> {
                    if (!hostVisible || !isAttachedToWindow()) {
                        return;
                    }
                    if (requestGeneration != searchGeneration) {
                        return;
                    }
                    if (!trimmed.equals(lastInput.trim()) || activeAzLetter != null) {
                        return;
                    }
                    renderButtons(suggestionEntries, false);
                });
            });
            return;
        }

        List<LauncherAppEntry> suggestionEntries = buildPinnedOrDefaultSurface();
        renderButtons(suggestionEntries, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    void reload() {
        reloadWithInput("", null);
    }

    private List<LauncherAppEntry> buildPinnedOrDefaultSurface() {
        if (configRepository != null) {
            if (pinnedItems == null || pinnedItems.isEmpty()) {
                pinnedItems = configRepository.loadPinnedItems();
            }
            if (pinnedItems != null && !pinnedItems.isEmpty()) {
                return entriesForPinnedItems(pinnedItems);
            }
        }
        return new ArrayList<>();
    }

    private void renderButtons(@NonNull List<LauncherAppEntry> entries, boolean azPreview) {
        if (!hasStableRenderBounds()) {
            suppressDrawUntilStableLayout = true;
            childLayoutPending = true;
            if (stableLayoutSuppressedSinceUptimeMs == 0L) {
                stableLayoutSuppressedSinceUptimeMs = SystemClock.uptimeMillis();
            }
            if (pendingDeferredRender) {
                return;
            }
            pendingDeferredRender = true;
            final List<LauncherAppEntry> deferredEntries = new ArrayList<>(entries);
            final boolean deferredAzPreview = azPreview;
            post(() -> {
                pendingDeferredRender = false;
                if (!hostVisible || !isAttachedToWindow()) {
                    return;
                }
                renderButtons(deferredEntries, deferredAzPreview);
            });
            return;
        }
        pendingDeferredRender = false;
        int buttonCount = Math.max(1, maxButtonCount);
        int renderStartCol = 0;
        List<PinnedItem> pinnedForSlots = new ArrayList<>();
        int pinnedPageOffset = 0;
        Set<String> azFreshPageEntryKeys = Collections.emptySet();

        if (azPreview) {
            int perPage = Math.max(1, maxButtonCount);
            int totalPages = getAzPagesCount();
            activeAzPageIndex = clamp(activeAzPageIndex, 0, Math.max(0, totalPages - 1));
            int offset = getAzPageStart(entries, activeAzPageIndex, perPage);
            int previousPageEnd = -1;
            if (activeAzPageIndex > 0) {
                int previousOffset = getAzPageStart(entries, activeAzPageIndex - 1, perPage);
                previousPageEnd = Math.min(entries.size(), previousOffset + perPage);
                azFreshPageEntryKeys = new HashSet<>();
            }
            List<LauncherAppEntry> pageEntries = new ArrayList<>();
            for (int i = offset; i < entries.size() && pageEntries.size() < perPage; i++) {
                LauncherAppEntry pageEntry = entries.get(i);
                pageEntries.add(pageEntry);
                if (activeAzPageIndex > 0 && i >= previousPageEnd) {
                    azFreshPageEntryKeys.add(stableEntryKey(pageEntry));
                }
            }
            entries = pageEntries;
            buttonCount = perPage;
            pinnedItemsPerPage = 1;
            pinnedPageIndex = 0;
            renderStartCol = 0;
        }

        boolean pinnedSurface = !azPreview && TextUtils.isEmpty(lastInput.trim()) && pinnedItems != null && !pinnedItems.isEmpty();
        if (pinnedSurface) {
            pinnedItemsPerPage = computePinnedItemsPerPage();
            int totalPages = getPinnedPagesCount();
            pinnedPageIndex = clamp(pinnedPageIndex, 0, Math.max(0, totalPages - 1));
            pinnedPageOffset = pinnedPageIndex * pinnedItemsPerPage;
            buttonCount = Math.max(1, pinnedItemsPerPage);
            if (isMostUsedDynamicPage(pinnedPageIndex)) {
                // Dynamic most-used page: render ranked apps as launch-only buttons. pinnedForSlots
                // stays empty so the render loop binds them with pinnedIndex -1 (no drag/reorder),
                // and they are never written back to pinnedItems / persisted.
                entries = new ArrayList<>(resolveMostUsedPageEntries());
            } else {
                for (int i = pinnedPageOffset; i < pinnedItems.size() && pinnedForSlots.size() < pinnedItemsPerPage; i++) {
                    PinnedItem item = pinnedItems.get(i);
                    if (item != null) pinnedForSlots.add(item);
                }
                entries = entriesForPinnedItems(pinnedForSlots);
            }
        } else {
            pinnedItemsPerPage = 1;
            pinnedPageIndex = 0;
        }

        int surfaceRenderSignature = computeSurfaceRenderSignature(entries, azPreview, pinnedSurface, buttonCount);
        if (surfaceRenderSignature != 0 && surfaceRenderSignature == lastSurfaceRenderSignature && getChildCount() > 0) {
            pendingDeferredRender = false;
            if (suppressDrawUntilStableLayout) {
                scheduleStableDrawReleaseIfPossible();
            } else {
                invalidate();
            }
            return;
        }

        boolean keepCurrentFrameVisible = hasStableDisplayLayout() && surfaceRenderSignature != 0 && surfaceRenderSignature != lastSurfaceRenderSignature;
        if (!keepCurrentFrameVisible) {
            suppressDrawUntilStableLayout = true;
            childLayoutPending = true;
            if (stableLayoutSuppressedSinceUptimeMs == 0L) {
                stableLayoutSuppressedSinceUptimeMs = SystemClock.uptimeMillis();
            }
        } else {
            suppressDrawUntilStableLayout = false;
            childLayoutPending = false;
            stableLayoutSuppressedSinceUptimeMs = 0L;
        }
        resetTransientVisualState();
        folderDragHoverIndex = -1;
        setTranslationX(0f);
        setAlpha(1f);
        removeAllViews();
        clearAzFocusedEntry();
        clearTerminalSearchFocus();
        lastAzResolvedSlot = -1;
        launchTargetViews.clear();
        launchTargetViewsByPackage.clear();
        azRenderedSlotEntries.clear();
        azRenderedEntryTargets.clear();
        azRenderedSlotCount = 0;
        azPreviewRendered = azPreview;
        if (!azPreview) {
            invalidateAzRenderState();
        }

        setColumnCount(buttonCount);
        if (azPreview) {
            azRenderedSlotCount = buttonCount;
        }

        boolean[] usedColumns = new boolean[Math.max(1, buttonCount)];
        int[] azPriorityColumns = null;
        if (azPreview) {
            int preferredCenter = buttonCount / 2;
            if (activeAzLetter != null) {
                preferredCenter = clamp(Math.round(computeAzAnchorPosition(activeAzLetter, buttonCount)), 0, buttonCount - 1);
            }
            azPriorityColumns = buildAzPriorityColumnsAround(preferredCenter, buttonCount);
        }
        for (int col = 0; col < entries.size() && col < buttonCount; col++) {
            LauncherAppEntry entry = entries.get(col);
            View view = createEntryButton(entry);
            int renderCol = azPreview && azPriorityColumns != null
                ? azPriorityColumns[col]
                : (renderStartCol + col);
            LayoutParams param = createSlotParams(renderCol);
            view.setLayoutParams(param);
            if (azPreview && activeAzPageIndex > 0 && !azFreshPageEntryKeys.isEmpty()
                && !azFreshPageEntryKeys.contains(stableEntryKey(entry))) {
                view.setAlpha(0.38f);
            }

            if (!azPreview && col < pinnedForSlots.size()) {
                final int pinnedIndex = pinnedPageOffset + col;
                final PinnedItem pinnedItem = pinnedForSlots.get(col);
                if (pinnedItem instanceof PinnedFolderItem) {
                    view = createFolderPreviewButton((PinnedFolderItem) pinnedItem);
                    view.setLayoutParams(param);
                    View.OnClickListener openFolder = v -> showFolderPopup((PinnedFolderItem) pinnedItem, v);
                    view.setOnClickListener(openFolder);
                    View pressTarget = resolvePrimaryPressTarget(view);
                    pressTarget.setOnClickListener(openFolder);
                    bindFolderContextLongPress(pressTarget, (PinnedFolderItem) pinnedItem, pinnedIndex, true);
                } else {
                    View pressTarget = resolvePrimaryPressTarget(view);
                    bindAppContextLongPress(pressTarget, entry, pinnedIndex, null, null, true);
                }
            } else {
                View pressTarget = resolvePrimaryPressTarget(view);
                bindAppContextLongPress(pressTarget, entry, -1, null, null, false);
            }

            View dragTarget = resolvePrimaryPressTarget(view);
            if (!azPreview && !TextUtils.isEmpty(lastInput.trim())) {
                terminalSearchEntries.add(entry);
                terminalSearchTargets.add(dragTarget);
            }
            if (azPreview && renderCol >= 0 && renderCol < buttonCount) {
                azRenderedSlotEntries.put(renderCol, entry);
                azRenderedEntryTargets.put(stableEntryKey(entry), new WeakReference<>(dragTarget));
            }

            addView(view);
            if (renderCol >= 0 && renderCol < usedColumns.length) {
                usedColumns[renderCol] = true;
            }
        }

        if (pendingPinnedMutationFeedback && !azPreview) {
            pendingPinnedMutationFeedback = false;
            post(this::animatePinnedMutationFeedback);
        }

        boolean showEmptyPinnedHint = !azPreview
            && TextUtils.isEmpty(lastInput.trim())
            && (pinnedItems == null || pinnedItems.isEmpty())
            && entries.isEmpty();

        if (showEmptyPinnedHint) {
            TextView hint = new TextView(getContext());
            hint.setText(R.string.termux_app_launcher_empty_pinned_hint);
            hint.setTextColor(resolvePinnedHintBaseColor());
            hint.setTextSize(11f);
            hint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            hint.setLetterSpacing(0.03f);
            hint.setGravity(Gravity.CENTER);
            hint.setSingleLine(true);
            hint.setPadding(dp(6), 0, dp(6), 0);
            hint.setAlpha(0.92f);
            GridLayout.LayoutParams hintParams = createSlotParams(0);
            hintParams.columnSpec = GridLayout.spec(0, Math.max(1, buttonCount), 1f);
            hintParams.width = 0;
            hint.setLayoutParams(hintParams);
            hint.setOnLongClickListener(v -> {
                openPinEditor();
                return true;
            });
            applyPinnedHintShimmer(hint);
            addView(hint);
            for (int i = 1; i < buttonCount; i++) {
                ImageButton filler = new ImageButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
                filler.setVisibility(INVISIBLE);
                filler.setLayoutParams(createSlotParams(i));
                addView(filler);
            }
        } else {
            for (int i = 0; i < buttonCount; i++) {
                if (usedColumns[i]) continue;
                ImageButton filler = new ImageButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
                filler.setVisibility(VISIBLE);
                filler.setAlpha(0f);
                filler.setLayoutParams(createSlotParams(i));
                if (!azPreview) {
                    final int slotIndex = i;
                    filler.setOnLongClickListener(v -> {
                        openPinEditor();
                        return true;
                    });
                }
                addView(filler);
            }
        }

        if (!azPreview) {
            setOnLongClickListener(v -> {
                openPinEditor();
                return true;
            });
            setOnDragListener(this::handlePinnedBarDragEvent);
        } else {
            setOnLongClickListener(null);
            setOnDragListener(null);
        }
        if (!terminalSearchEntries.isEmpty()) {
            terminalSearchFocusIndex = 0;
            applyTerminalSearchFocusOutline();
        }
        if (overflowInteractionListener != null) {
            overflowInteractionListener.onOverflowInteractionChanged(rowInteractionActive);
        }
        lastSurfaceRenderSignature = surfaceRenderSignature;
        requestLayout();
        if (!keepCurrentFrameVisible) {
            scheduleStableDrawReleaseIfPossible();
        } else {
            invalidate();
        }
    }

    public int getTerminalSearchResultCount() {
        return terminalSearchEntries.size();
    }

    public boolean moveTerminalSearchFocus(int delta) {
        int count = terminalSearchEntries.size();
        if (count == 0) return false;
        int previousFocusIndex = terminalSearchFocusIndex;
        terminalSearchFocusIndex = Math.floorMod(terminalSearchFocusIndex + delta, count);
        if (rowHapticsEnabled && RowHapticTickHelper.isBoundaryCrossing(
            previousFocusIndex, terminalSearchFocusIndex)) {
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
        applyTerminalSearchFocusOutline();
        return true;
    }

    public void setRowHapticsEnabled(boolean enabled) {
        rowHapticsEnabled = enabled;
    }

    public boolean launchFocusedTerminalSearchEntry() {
        int index = terminalSearchFocusIndex;
        if (index < 0 || index >= terminalSearchEntries.size()) return false;
        LauncherAppEntry entry = terminalSearchEntries.get(index);
        View source = index < terminalSearchTargets.size() ? terminalSearchTargets.get(index) : null;
        clearTerminalSearchFocus();
        launchEntry(entry, lastTerminalView, source);
        return true;
    }

    public void clearTerminalSearchFocus() {
        for (ValueAnimator animator : new ArrayList<>(terminalFocusOutlineAnimators.values())) {
            if (animator != null) animator.cancel();
        }
        terminalFocusOutlineAnimators.clear();
        terminalFocusOutlineDirections.clear();
        terminalFocusOutlineDrawables.clear();
        for (View target : terminalSearchTargets) {
            if (target != null) target.setForeground(null);
        }
        terminalSearchEntries.clear();
        terminalSearchTargets.clear();
        terminalSearchFocusIndex = -1;
    }

    private void applyTerminalSearchFocusOutline() {
        int accent = FocusOutlineRenderer.resolveAccent(this);
        for (int i = 0; i < terminalSearchTargets.size(); i++) {
            View target = terminalSearchTargets.get(i);
            if (target == null) continue;
            if (i != terminalSearchFocusIndex) {
                animateTerminalFocusOutline(target, false, null);
                continue;
            }
            if (!(target instanceof ImageView)) continue;
            if (target.getWidth() <= 0 || target.getHeight() <= 0) {
                // Focus is applied while the row is still being built; wait for real image-matrix
                // bounds instead of flashing a geometrically different fallback outline.
                final int focusAtPost = terminalSearchFocusIndex;
                target.post(() -> {
                    if (terminalSearchFocusIndex == focusAtPost) applyTerminalSearchFocusOutline();
                });
                continue;
            }
            FocusOutlineRenderer.Visual visual = resolveFocusOutlineVisual((ImageView) target);
            if (visual == null) continue;
            FocusOutlineRenderer.OutlineDrawable outline = terminalFocusOutlineDrawables.get(target);
            if (outline == null) {
                outline = new FocusOutlineRenderer.OutlineDrawable(visual, accent);
                terminalFocusOutlineDrawables.put(target, outline);
            }
            animateTerminalFocusOutline(target, true, outline);
        }
    }

    private void animateTerminalFocusOutline(@NonNull View target, boolean incoming,
                                             @Nullable FocusOutlineRenderer.OutlineDrawable requested) {
        FocusOutlineRenderer.OutlineDrawable outline = requested != null
            ? requested : terminalFocusOutlineDrawables.get(target);
        if (outline == null) return;

        ValueAnimator running = terminalFocusOutlineAnimators.get(target);
        if (running != null) {
            // Posted layout retries keep the existing transition; a real direction reversal starts
            // from the drawable's current alpha/scale instead of guessing direction from geometry.
            Boolean runningIncoming = terminalFocusOutlineDirections.get(target);
            if (runningIncoming != null && runningIncoming == incoming) return;
            terminalFocusOutlineAnimators.remove(target);
            terminalFocusOutlineDirections.remove(target);
            running.cancel();
        }

        if (incoming && target.getForeground() != outline) {
            outline.setFocusAlpha(0f);
            outline.setFocusScale(1.04f);
            target.setForeground(outline);
            target.setForegroundGravity(Gravity.FILL);
        } else if (!incoming && target.getForeground() != outline) {
            return;
        }

        if (!FocusOutlineRenderer.animationsEnabled(getContext())) {
            terminalFocusOutlineDirections.remove(target);
            outline.setFocusAlpha(incoming ? 1f : 0f);
            outline.setFocusScale(incoming ? 1f : 0.96f);
            if (!incoming && target.getForeground() == outline) target.setForeground(null);
            return;
        }

        final float startAlpha = outline.getFocusAlpha();
        final float startScale = outline.getFocusScale();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        terminalFocusOutlineAnimators.put(target, animator);
        terminalFocusOutlineDirections.put(target, incoming);
        animator.setDuration(160L);
        animator.setInterpolator(new DecelerateInterpolator(1.45f));
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            outline.setFocusAlpha(lerp(startAlpha, incoming ? 1f : 0f, progress));
            outline.setFocusScale(incoming
                ? FocusOutlineRenderer.incomingScale(startScale, progress)
                : lerp(startScale, 0.96f, progress));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (terminalFocusOutlineAnimators.get(target) != animation) return;
                terminalFocusOutlineAnimators.remove(target);
                terminalFocusOutlineDirections.remove(target);
                outline.setFocusAlpha(incoming ? 1f : 0f);
                outline.setFocusScale(incoming ? 1f : 0.96f);
                if (!incoming && target.getForeground() == outline) target.setForeground(null);
            }
        });
        animator.start();
    }

    private void animatePinnedMutationFeedback() {
        animate().cancel();
        setPivotX(getWidth() * 0.5f);
        setPivotY(getHeight() * 0.5f);
        setScaleX(0.986f);
        setScaleY(0.986f);
        setAlpha(0.9f);
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(180L)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private int computeSurfaceRenderSignature(
        @NonNull List<LauncherAppEntry> entries,
        boolean azPreview,
        boolean pinnedSurface,
        int buttonCount
    ) {
        int signature = 17;
        signature = (31 * signature) + (azPreview ? 1 : 0);
        signature = (31 * signature) + (pinnedSurface ? 1 : 0);
        signature = (31 * signature) + (bandW ? 1 : 0);
        signature = (31 * signature) + ICON_RENDER_PIPELINE_VERSION;
        signature = (31 * signature) + Float.floatToIntBits(iconScale);
        signature = (31 * signature) + dockRowHeightHintPx;
        signature = (31 * signature) + Math.max(1, buttonCount);
        signature = (31 * signature) + pinnedPageIndex;
        signature = (31 * signature) + activeAzPageIndex;
        signature = (31 * signature) + lastInput.trim().hashCode();
        int limit = Math.min(entries.size(), Math.max(1, buttonCount));
        for (int i = 0; i < limit; i++) {
            signature = (31 * signature) + stableEntryKey(entries.get(i)).hashCode();
        }
        signature = (31 * signature) + entries.size();
        return signature;
    }

    @NonNull
    private static int[] buildAzPriorityColumnsAround(int center, int count) {
        int safeCount = Math.max(1, count);
        int[] order = new int[safeCount];
        int cursor = 0;
        int anchoredCenter = Math.max(0, Math.min(safeCount - 1, center));
        order[cursor++] = anchoredCenter;
        for (int offset = 1; cursor < safeCount; offset++) {
            int right = anchoredCenter + offset;
            if (right < safeCount) {
                order[cursor++] = right;
                if (cursor >= safeCount) break;
            }
            int left = anchoredCenter - offset;
            if (left >= 0) {
                order[cursor++] = left;
            }
        }
        return order;
    }

    private void applyPinnedHintShimmer(@NonNull TextView hintView) {
        final int baseColor = resolvePinnedHintBaseColor();
        final int shimmerColor = blendColors(baseColor, resolveLauncherTextColor(), 0.24f);
        ValueAnimator shimmer = ValueAnimator.ofObject(new ArgbEvaluator(), baseColor, shimmerColor, baseColor);
        shimmer.setDuration(3200L);
        shimmer.setRepeatCount(ValueAnimator.INFINITE);
        shimmer.setRepeatMode(ValueAnimator.RESTART);
        shimmer.addUpdateListener(animation -> hintView.setTextColor((Integer) animation.getAnimatedValue()));
        hintView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (!shimmer.isStarted()) {
                    shimmer.start();
                }
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                shimmer.cancel();
            }
        });
        shimmer.start();
    }

    private int resolvePinnedHintBaseColor() {
        return blendColors(inheritedTintColor, resolveLauncherTextColor(), 0.58f);
    }

    private static int blendColors(int from, int to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        int fromA = (from >> 24) & 0xFF;
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toA = (to >> 24) & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;
        int outA = Math.round(fromA + ((toA - fromA) * clamped));
        int outR = Math.round(fromR + ((toR - fromR) * clamped));
        int outG = Math.round(fromG + ((toG - fromG) * clamped));
        int outB = Math.round(fromB + ((toB - fromB) * clamped));
        return (outA << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private final class NotificationBadgeFrame extends FrameLayout {
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgeStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @NonNull private Set<String> badgePackages = Collections.emptySet();

        NotificationBadgeFrame(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
            badgePaint.setStyle(Paint.Style.FILL);
            badgeStrokePaint.setStyle(Paint.Style.STROKE);
            badgeStrokePaint.setStrokeWidth(dp(1.6f));
        }

        void setBadgePackages(@Nullable Set<String> packages) {
            badgePackages = packages == null || packages.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(packages);
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!notificationBadgesEnabled || badgePackages.isEmpty() || notificationBadgePackages.isEmpty()) {
                return;
            }
            boolean active = false;
            for (String packageName : badgePackages) {
                if (notificationBadgePackages.contains(packageName)) {
                    active = true;
                    break;
                }
            }
            if (!active || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }

            float radius = Math.max(dp(3.5f), Math.min(getWidth(), getHeight()) * 0.075f);
            float cx = (getWidth() * 0.5f) + (iconSizePx() * 0.30f);
            float cy = (getHeight() * 0.5f) - (iconSizePx() * 0.30f);
            cx = clampFloat(cx, radius + dp(1f), getWidth() - radius - dp(1f));
            cy = clampFloat(cy, radius + dp(1f), getHeight() - radius - dp(1f));
            badgePaint.setColor(resolveNotificationBadgeColor());
            badgeStrokePaint.setColor(resolveNotificationBadgeStrokeColor());
            canvas.drawCircle(cx, cy, radius + dp(1.1f), badgeStrokePaint);
            canvas.drawCircle(cx, cy, radius, badgePaint);
        }
    }

    /** Always-on glass-dock icon treatment, cached across resting and swipe-preview rendering. */
    @Nullable
    private Drawable iconForDisplay(@NonNull LauncherAppEntry entry, int sizePx) {
        Drawable raw = entry.icon != null ? entry.icon : getContext().getPackageManager().getDefaultActivityIcon();
        if (sizePx <= 0) {
            return raw;
        }
        boolean cloneBadge = entry.appRef.clonedProfile;
        String key = "glass" + ICON_RENDER_PIPELINE_VERSION + (cloneBadge ? "c" : "")
            + (entry.iconPackArtwork ? "p" : "")
            + stableEntryKey(entry) + "@" + sizePx;
        Drawable cached = normalizedIconCache.get(key);
        if (cached != null) {
            return cached;
        }
        Drawable built = normalizeIcon(raw, sizePx, !entry.iconPackArtwork, cloneBadge);
        if (built != null) {
            normalizedIconCache.put(key, built);
        }
        return built != null ? built : raw;
    }

    private Drawable normalizeIcon(@Nullable Drawable src, int sizePx, boolean tuneSaturation,
                                   boolean cloneBadge) {
        if (src == null || sizePx <= 0) {
            return src;
        }
        Bitmap cleanArtwork = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas artworkCanvas = new Canvas(cleanArtwork);

        // Keep the prior unification footprint as the normalization baseline for every source,
        // including icon-pack artwork. The native silhouette is never reshaped.
        float inset = sizePx * 0.035f;
        int left = Math.round(inset);
        int top = Math.round(inset);
        int right = Math.round(sizePx - inset);
        int bottom = Math.round(sizePx - inset);
        Rect iconRect = new Rect(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));

        // Render the source at the footprint size so we can derive a silhouette shadow that follows
        // its native shape (adaptive icons draw their own masked bg+fg here, so shape is preserved).
        Bitmap iconBmp = Bitmap.createBitmap(iconRect.width(), iconRect.height(), Bitmap.Config.ARGB_8888);
        Canvas iconCanvas = new Canvas(iconBmp);
        Rect oldBounds = new Rect(src.getBounds());
        src.setBounds(0, 0, iconBmp.getWidth(), iconBmp.getHeight());
        src.draw(iconCanvas);
        src.setBounds(oldBounds);

        // Saturation nudge toward the glass vibrancy (match, not grey), then draw the icon.
        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (tuneSaturation) {
            ColorMatrix saturate = new ColorMatrix();
            saturate.setSaturation(0.92f);
            iconPaint.setColorFilter(new ColorMatrixColorFilter(saturate));
        }
        artworkCanvas.drawBitmap(iconBmp, null, iconRect, iconPaint);
        iconBmp.recycle();
        if (cloneBadge) drawCloneBadge(artworkCanvas, sizePx);

        Bitmap display = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas displayCanvas = new Canvas(display);
        drawIconShadow(displayCanvas, cleanArtwork);
        displayCanvas.drawBitmap(cleanArtwork, 0f, 0f,
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        return new RenderedIconDrawable(getResources(), display, cleanArtwork);
    }

    /** Small, neutral double-tile badge that remains legible over both bright and dark icons. */
    private void drawCloneBadge(@NonNull Canvas canvas, int sizePx) {
        float radius = Math.max(dp(6f), sizePx * 0.19f);
        float cx = sizePx - radius - Math.max(dp(1f), sizePx * 0.025f);
        float cy = sizePx - radius - Math.max(dp(1f), sizePx * 0.025f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xF2FFFFFF);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(dp(1f), sizePx * 0.025f));
        paint.setColor(0x70000000);
        canvas.drawCircle(cx, cy, radius - (paint.getStrokeWidth() * 0.5f), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xD9202020);
        float tile = radius * 0.82f;
        float corner = tile * 0.22f;
        canvas.drawRoundRect(cx - tile * 0.58f, cy - tile * 0.58f,
            cx + tile * 0.22f, cy + tile * 0.22f, corner, corner, paint);
        paint.setColor(0xFF5B6CFF);
        canvas.drawRoundRect(cx - tile * 0.20f, cy - tile * 0.20f,
            cx + tile * 0.60f, cy + tile * 0.60f, corner, corner, paint);
    }

    /** Subtle silhouette contact shadow: 3dp feather, 1dp down, 28% black. */
    private void drawIconShadow(@NonNull Canvas canvas, @NonNull Bitmap cleanArtwork) {
        Bitmap alpha = cleanArtwork.extractAlpha();
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(ICON_SHADOW_COLOR);
        shadowPaint.setMaskFilter(new BlurMaskFilter(Math.max(1f, dp(3f)), BlurMaskFilter.Blur.NORMAL));
        canvas.drawBitmap(alpha, 0f, dp(1f), shadowPaint);
        alpha.recycle();
    }

    private View createEntryButton(@NonNull LauncherAppEntry entry) {
        NotificationBadgeFrame shell = new NotificationBadgeFrame(getContext());
        shell.setBadgePackages(Collections.singleton(entry.appRef.packageName));
        shell.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shell.setClipChildren(false);
        shell.setClipToPadding(false);

        ImageButton imageButton = new ImageButton(getContext());
        int size = iconSizePx();
        Drawable icon = iconForDisplay(entry, size);
        imageButton.setImageDrawable(icon);
        imageButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        imageButton.setAdjustViewBounds(true);
        imageButton.setPadding(0, 0, 0, 0);
        imageButton.setBackgroundColor(0x00000000);
        imageButton.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        imageButton.setMinimumHeight(size);
        imageButton.setMinimumWidth(size);
        applyAppIconColorFilter(imageButton);
        imageButton.setOnClickListener(v -> launchEntryFromTouch(v, entry, lastTerminalView));
        imageButton.setContentDescription(entry.label);
        registerLaunchTarget(entry.appRef, imageButton);
        shell.addView(imageButton);
        return shell;
    }

    private LayoutParams createSlotParams(int col) {
        LayoutParams param = new GridLayout.LayoutParams();
        param.width = 0;
        param.height = ViewGroup.LayoutParams.MATCH_PARENT;
        param.setMargins(0, 0, 0, 0);
        param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1f);
        param.rowSpec = GridLayout.spec(0, GridLayout.FILL, 1f);
        return param;
    }

    private void launchEntry(@NonNull LauncherAppEntry entry, @Nullable TerminalView terminalView) {
        launchEntry(entry, terminalView, null);
    }

    private void launchEntry(@NonNull LauncherAppEntry entry, @Nullable TerminalView terminalView, @Nullable View launchSourceView) {
        launchEntry(entry, terminalView, launchSourceView, true);
    }

    private void launchEntry(@NonNull LauncherAppEntry entry, @Nullable TerminalView terminalView,
                             @Nullable View launchSourceView, boolean playRipple) {
        if (entry.appRef.packageName.startsWith("injected.test")) {
            return;
        }
        if (playRipple) dispatchLaunchRipple(entry, launchSourceView);
        Context context = getContext();
        if (entry.appRef.clonedProfile) {
            LaunchAnimationContext launchAnimationContext = shouldUseTouchLaunchAnimation(launchSourceView)
                ? buildLaunchAnimationContext(launchSourceView)
                : null;
            Bundle options = launchAnimationContext != null ? launchAnimationContext.options : null;
            if (!LauncherAppLauncher.tryStartProfileMainActivity(context, entry, options)) {
                Log.w(LOG_TAG, "Failed to launch cloned/profile package " + entry.appRef.packageName
                    + " activity=" + entry.appRef.activityName + " user=" + entry.appRef.userId);
                return;
            }
            if (activeAzLetter != null) {
                clearAzPreview();
            }
            getUsageStatsStore().recordLaunch(entry.appRef.stableId());
            invalidateMostUsedCache();
            if (terminalView != null) {
                terminalView.clearInputLine();
            }
            dismissFolderPopup();
            dismissAppContextPopup();
            dismissShortcutsPopup();
            return;
        }
        PackageManager packageManager = context.getPackageManager();
        String activityName = entry.appRef.activityName;
        if (!TextUtils.isEmpty(activityName) && activityName.startsWith(".")) {
            activityName = entry.appRef.packageName + activityName;
        }

        Intent explicit = null;
        Intent explicitNoCategory = null;
        if (!TextUtils.isEmpty(activityName)) {
            explicit = new Intent(Intent.ACTION_MAIN);
            explicit.addCategory(Intent.CATEGORY_LAUNCHER);
            explicit.setComponent(new ComponentName(entry.appRef.packageName, activityName));

            explicitNoCategory = new Intent(Intent.ACTION_MAIN);
            explicitNoCategory.setComponent(new ComponentName(entry.appRef.packageName, activityName));
        }

        LaunchAnimationContext launchAnimationContext = shouldUseTouchLaunchAnimation(launchSourceView)
            ? buildLaunchAnimationContext(launchSourceView)
            : null;

        Intent pkgDefault = packageManager.getLaunchIntentForPackage(entry.appRef.packageName);
        ComponentName pkgDefaultComponent = pkgDefault != null ? pkgDefault.getComponent() : null;
        ComponentName explicitComponent = explicit != null ? explicit.getComponent() : null;
        boolean explicitIsPackageDefault = sameComponent(explicitComponent, pkgDefaultComponent);

        boolean launched = false;
        if (explicitIsPackageDefault && tryStartActivity(context, pkgDefault, launchAnimationContext)) {
            launched = true;
        } else if (tryStartActivity(context, explicit, launchAnimationContext)) {
            launched = true;
        } else if (!explicitIsPackageDefault && tryStartActivity(context, pkgDefault, launchAnimationContext)) {
            launched = true;
        }

        Intent resolveFallback = null;
        ComponentName resolved = null;
        if (!launched) {
            resolveFallback = new Intent(Intent.ACTION_MAIN);
            resolveFallback.addCategory(Intent.CATEGORY_LAUNCHER);
            resolveFallback.setPackage(entry.appRef.packageName);
            resolved = resolveFallback.resolveActivity(packageManager);
            if (resolved != null) {
                resolveFallback.setComponent(resolved);
            }
        }
        if (!launched && tryStartActivity(context, explicitNoCategory, launchAnimationContext)) {
            launched = true;
        } else if (!launched && resolved != null && tryStartActivity(context, resolveFallback, launchAnimationContext)) {
            launched = true;
        } else if (!launched && tryStartMainActivity(context, explicit != null ? explicit.getComponent() : null, launchAnimationContext)) {
            launched = true;
        } else if (!launched && tryStartMainActivity(context, pkgDefault != null ? pkgDefault.getComponent() : null, launchAnimationContext)) {
            launched = true;
        } else if (!launched && tryStartMainActivity(context, resolved, launchAnimationContext)) {
            launched = true;
        }
        if (!launched) {
            Intent packageMain = new Intent(Intent.ACTION_MAIN);
            packageMain.addCategory(Intent.CATEGORY_LAUNCHER);
            packageMain.setPackage(entry.appRef.packageName);
            List<android.content.pm.ResolveInfo> matches = packageManager.queryIntentActivities(packageMain, 0);
            for (android.content.pm.ResolveInfo match : matches) {
                if (match == null || match.activityInfo == null) continue;
                String pkg = match.activityInfo.packageName;
                String cls = match.activityInfo.name;
                if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(cls)) continue;
                Intent fallbackExplicit = new Intent(Intent.ACTION_MAIN);
                fallbackExplicit.addCategory(Intent.CATEGORY_LAUNCHER);
                fallbackExplicit.setComponent(new ComponentName(pkg, cls));
                if (tryStartActivity(context, fallbackExplicit, launchAnimationContext)
                    || tryStartMainActivity(context, fallbackExplicit.getComponent(), launchAnimationContext)) {
                    launched = true;
                    break;
                }
            }
        }

        if (!launched) {
            Log.w(LOG_TAG, "Failed to launch package " + entry.appRef.packageName
                + " activity=" + entry.appRef.activityName);
            return;
        }
        if (activeAzLetter != null) {
            clearAzPreview();
        }
        getUsageStatsStore().recordLaunch(entry.appRef.stableId());
        invalidateMostUsedCache();

        if (terminalView != null) {
            terminalView.clearInputLine();
        }
        dismissFolderPopup();
        dismissAppContextPopup();
        dismissShortcutsPopup();
    }

    private void launchEntryFromTouch(@NonNull View sourceView, @NonNull LauncherAppEntry entry, @Nullable TerminalView terminalView) {
        boolean touchAnimation = shouldUseTouchLaunchAnimation(sourceView);
        long launchDelay = touchAnimation ? APP_LAUNCH_TOUCH_DELAY_MS : 0L;
        dispatchLaunchRipple(entry, sourceView);
        Runnable launch = () -> launchEntry(entry, terminalView,
            touchAnimation ? sourceView : null, false);
        if (launchDelay == 0L) {
            launch.run();
        } else {
            postDelayed(launch, launchDelay);
        }
    }

    public void setLaunchRippleListener(@Nullable LaunchRippleListener listener) {
        launchRippleListener = listener;
    }

    private void dispatchLaunchRipple(@NonNull LauncherAppEntry entry, @Nullable View sourceView) {
        if (launchRippleListener == null) return;
        Drawable icon = sourceView instanceof ImageView
            ? ((ImageView) sourceView).getDrawable() : null;
        launchRippleListener.onLaunchRipple(entry.appRef.packageName, icon, sourceView);
    }

    private List<LauncherAppEntry> entriesForPinnedItems(@NonNull List<PinnedItem> source) {
        List<LauncherAppEntry> out = new ArrayList<>();
        for (PinnedItem item : source) {
            if (item instanceof PinnedAppItem) {
                LauncherAppEntry entry = resolvePinnedApp((PinnedAppItem) item);
                if (entry != null) {
                    out.add(entry);
                }
            } else if (item instanceof PinnedFolderItem) {
                LauncherAppEntry synthetic = folderSyntheticEntry((PinnedFolderItem) item);
                out.add(synthetic);
            }
        }
        return out;
    }

    @Nullable
    private LauncherAppEntry resolvePinnedApp(@NonNull PinnedAppItem item) {
        LauncherAppEntry entry = resolveRef(item.appRef);
        if (entry == null) {
            return entry;
        }
        LauncherIconResolver.ResolvedIcon resolvedIcon = getIconResolver().resolvePinnedDetailed(
            entry.appRef, item.iconOverride, entry.icon, entry.iconPackArtwork);
        Drawable pinnedIcon = resolvedIcon.drawable;
        if ((pinnedIcon == null || pinnedIcon == entry.icon)
            && resolvedIcon.iconPackArtwork == entry.iconPackArtwork) {
            return entry;
        }
        return new LauncherAppEntry(entry.appRef, entry.label, pinnedIcon, resolvedIcon.iconPackArtwork);
    }

    private LauncherAppEntry folderSyntheticEntry(@NonNull PinnedFolderItem folder) {
        Drawable icon = null;
        for (PinnedAppItem folderApp : folder.apps) {
            LauncherAppEntry entry = resolvePinnedApp(folderApp);
            if (entry != null && entry.icon != null) {
                icon = entry.icon;
                break;
            }
        }
        String title = TextUtils.isEmpty(folder.title) ? "Folder" : folder.title;
        return new LauncherAppEntry(new AppRef("folder", buildFolderRenderKey(folder)), title, icon);
    }

    @NonNull
    private static String buildFolderRenderKey(@NonNull PinnedFolderItem folder) {
        StringBuilder builder = new StringBuilder(folder.id);
        builder.append('|').append(TextUtils.isEmpty(folder.title) ? "Folder" : folder.title);
        builder.append('|').append(folder.apps.size());
        for (PinnedAppItem folderApp : folder.apps) {
            builder.append('|').append(folderApp.appRef.stableId());
            if (folderApp.iconOverride != null && folderApp.iconOverride.isValid()) {
                builder.append(':').append(folderApp.iconOverride.iconPackPackage)
                    .append('/').append(folderApp.iconOverride.drawableName);
            }
        }
        return builder.toString();
    }

    @Nullable
    private LauncherAppEntry resolveRef(@NonNull AppRef ref) {
        String cacheKey = ref.stableId();
        LauncherAppEntry cached = resolvedRefCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (appDataProvider == null) {
            appDataProvider = LauncherAppDataProvider.getInstance(getContext());
        }
        if (!TextUtils.isEmpty(ref.activityName)) {
            LauncherAppEntry exact = appDataProvider.findByRef(ref);
            if (exact != null) {
                resolvedRefCache.put(cacheKey, exact);
                return exact;
            }
        }
        if (injectedSuggestionButtons == null) {
            LauncherAppEntry resolved = appDataProvider.findDefaultByPackage(ref.packageName);
            if (resolved != null) {
                resolvedRefCache.put(cacheKey, resolved);
                return resolved;
            }
        }
        ComponentName defaultComponent = null;
        Intent pkgDefault = getContext().getPackageManager().getLaunchIntentForPackage(ref.packageName);
        if (pkgDefault != null) {
            defaultComponent = pkgDefault.getComponent();
        }
        if (defaultComponent != null) {
            String defaultClassName = defaultComponent.getClassName();
            for (LauncherAppEntry entry : allApps) {
                if (entry.appRef.packageName.equals(ref.packageName)
                    && defaultClassName.equals(entry.appRef.activityName)) {
                    resolvedRefCache.put(cacheKey, entry);
                    return entry;
                }
            }
        }
        for (LauncherAppEntry entry : allApps) {
            if (entry.appRef.packageName.equals(ref.packageName)) {
                resolvedRefCache.put(cacheKey, entry);
                return entry;
            }
        }
        LauncherAppEntry built = buildEntryFromPackageManager(ref, defaultComponent);
        if (built != null) {
            resolvedRefCache.put(cacheKey, built);
        }
        return built;
    }

    @Nullable
    private LauncherAppEntry buildEntryFromPackageManager(@NonNull AppRef originalRef, @Nullable ComponentName defaultComponent) {
        PackageManager packageManager = getContext().getPackageManager();
        ComponentName component = defaultComponent;
        if (component == null && !TextUtils.isEmpty(originalRef.activityName)) {
            component = new ComponentName(originalRef.packageName, originalRef.activityName);
        }

        AppRef resolvedRef = originalRef;
        String label = originalRef.packageName;
        Drawable icon = null;
        boolean iconPackArtwork = false;

        try {
            if (component != null) {
                resolvedRef = new AppRef(component.getPackageName(), component.getClassName());
                label = String.valueOf(packageManager.getActivityInfo(component, 0).loadLabel(packageManager));
                LauncherIconResolver.ResolvedIcon resolvedIcon = getIconResolver().resolveDetailed(resolvedRef, null, null);
                icon = resolvedIcon.drawable;
                iconPackArtwork = resolvedIcon.iconPackArtwork;
            }
        } catch (Exception ignored) {
        }

        if (icon == null) {
            try {
                label = String.valueOf(packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(originalRef.packageName, 0)
                ));
                LauncherIconResolver.ResolvedIcon resolvedIcon = getIconResolver().resolveDetailed(originalRef, null, null);
                icon = resolvedIcon.drawable;
                iconPackArtwork = resolvedIcon.iconPackArtwork;
            } catch (Exception ignored) {
                return null;
            }
        }

        return new LauncherAppEntry(resolvedRef, label, icon, iconPackArtwork);
    }

    @NonNull
    private LauncherIconResolver getIconResolver() {
        if (iconResolver == null) {
            iconResolver = new LauncherIconResolver(getContext());
        }
        return iconResolver;
    }

    @NonNull
    private IconPackRepository getIconPackRepository() {
        if (iconPackRepository == null) {
            iconPackRepository = new IconPackRepository(getContext());
        }
        return iconPackRepository;
    }

    private boolean tryStartMainActivity(@NonNull Context context, @Nullable ComponentName componentName, @Nullable LaunchAnimationContext animationContext) {
        if (componentName == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        try {
            LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) {
                return false;
            }
            launcherApps.startMainActivity(
                componentName,
                Process.myUserHandle(),
                animationContext != null ? animationContext.sourceBounds : null,
                animationContext != null ? animationContext.options : null
            );
            return true;
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "startMainActivity failed for " + componentName + ": " + throwable.getMessage());
            return false;
        }
    }

    private boolean tryStartActivity(@NonNull Context context, @Nullable Intent intent, @Nullable LaunchAnimationContext animationContext) {
        if (intent == null) return false;
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (animationContext != null && animationContext.sourceBounds != null) {
                intent.setSourceBounds(animationContext.sourceBounds);
            }
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (animationContext != null && animationContext.options != null) {
                    try {
                        activity.startActivity(intent, animationContext.options);
                        return true;
                    } catch (RuntimeException optionError) {
                        Log.d(LOG_TAG, "launch options fallback for " + intent + ": " + optionError.getMessage());
                    }
                }
                activity.startActivity(intent);
            } else {
                context.startActivity(intent);
            }
            return true;
        } catch (Exception e) {
            Log.d(LOG_TAG, "launch failed for intent " + intent + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean sameComponent(@Nullable ComponentName first, @Nullable ComponentName second) {
        return first != null && second != null && first.equals(second);
    }

    @Nullable
    private LaunchAnimationContext buildLaunchAnimationContext(@Nullable View sourceView) {
        if (sourceView == null || !(getContext() instanceof Activity)) {
            return null;
        }
        Rect sourceBounds = getSourceBoundsOnScreen(sourceView);
        if (sourceBounds == null) {
            return null;
        }
        int width = Math.max(1, sourceView.getWidth());
        int height = Math.max(1, sourceView.getHeight());
        ActivityOptions options;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            options = ActivityOptions.makeClipRevealAnimation(sourceView, 0, 0, width, height);
        } else {
            options = ActivityOptions.makeScaleUpAnimation(sourceView, 0, 0, width, height);
        }
        return new LaunchAnimationContext(sourceBounds, options.toBundle());
    }

    @Nullable
    public RectF getLaunchIconBounds(@NonNull ComponentName componentName) {
        WeakReference<View> ref = launchTargetViews.get(componentName.flattenToShortString());
        if (ref == null) {
            ref = launchTargetViews.get(componentName.flattenToString());
        }
        if (ref == null) {
            ref = launchTargetViewsByPackage.get(componentName.getPackageName());
        }
        View target = ref != null ? ref.get() : null;
        if (target == null || !target.isAttachedToWindow()) {
            target = findFirstAttachedLaunchTargetForPackage(componentName.getPackageName());
        }
        if (target == null || !target.isAttachedToWindow()) {
            return null;
        }
        int[] location = new int[2];
        target.getLocationOnScreen(location);
        return new RectF(
            location[0],
            location[1],
            location[0] + target.getWidth(),
            location[1] + target.getHeight()
        );
    }

    /**
     * Opens the modern, reusable pin editor (also used from Settings → Default apps). On save it
     * re-reads pinned items from the repository and re-renders the dock.
     */
    public void openPinEditor() {
        PinnedAppsEditor.show(getContext(), () -> {
            if (configRepository != null) {
                pinnedItems = configRepository.loadPinnedItems();
            }
            invalidateMostUsedCache();
            reloadWithInput("", lastTerminalView);
        });
    }

    private void showFolderContentsEditor(final int folderIndex, @NonNull final PinnedFolderItem folder) {
        if (allApps == null || allApps.isEmpty()) reloadAllApps();

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView title = new TextView(getContext());
        title.setText(TextUtils.isEmpty(folder.title) ? "Folder Apps" : folder.title);
        title.setTextColor(resolveLauncherTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(14f);

        final Set<String> selectedIds = new LinkedHashSet<>();
        for (PinnedAppItem folderApp : folder.apps) {
            selectedIds.add(resolveForSelectionId(folderApp.appRef));
        }

        final List<LauncherAppEntry> source = new ArrayList<>(allApps);
        Collections.sort(source, (a, b) -> {
            boolean aSelected = selectedIds.contains(a.appRef.stableId());
            boolean bSelected = selectedIds.contains(b.appRef.stableId());
            if (aSelected != bSelected) return aSelected ? -1 : 1;
            return String.CASE_INSENSITIVE_ORDER.compare(
                a.label == null ? "" : a.label,
                b.label == null ? "" : b.label
            );
        });
        final List<String> labels = buildDisplayLabels(source);

        EditText searchInput = new EditText(getContext());
        searchInput.setHint("Search apps");
        searchInput.setSingleLine(true);

        final List<LauncherAppEntry> filteredApps = new ArrayList<>(source);
        final List<String> filteredLabels = new ArrayList<>(labels);

        ListView listView = new ListView(getContext());
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_multiple_choice, filteredLabels);
        listView.setAdapter(adapter);
        listView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        syncFolderChecks(listView, filteredApps, selectedIds);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = stringValue(s).trim();
                filteredApps.clear();
                filteredLabels.clear();
                for (int i = 0; i < source.size(); i++) {
                    LauncherAppEntry app = source.get(i);
                    if (matchesLookupQuery(query, buildSearchableAppText(app))) {
                        filteredApps.add(app);
                        filteredLabels.add(labels.get(i));
                    }
                }
                adapter.notifyDataSetChanged();
                syncFolderChecks(listView, filteredApps, selectedIds);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filteredApps.size()) return;
            String stable = filteredApps.get(position).appRef.stableId();
            if (listView.isItemChecked(position)) {
                selectedIds.add(stable);
            } else {
                selectedIds.remove(stable);
            }
        });

        LinearLayout topActions = new LinearLayout(getContext());
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        topActions.setGravity(Gravity.END);

        ImageButton delete = new ImageButton(getContext());
        delete.setImageResource(R.drawable.ic_delete_sweep_24);
        delete.setContentDescription("Delete folder");
        styleIconButton(delete, dp(4));
        delete.setOnClickListener(v -> {
            removePinnedAt(folderIndex);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        topActions.addView(delete, deleteParams);

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        Button cancel = new Button(getContext());
        cancel.setText("Cancel");
        styleGhostButton(cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());

        Button save = new Button(getContext());
        save.setText("Save");
        styleGhostButton(save);
        save.setOnClickListener(v -> {
            List<PinnedAppItem> selectedApps = collectSelectedFolderApps(folder, source, selectedIds);
            dialog.dismiss();
            applyNormalizedFolderSelection(folderIndex, folder, selectedApps);
        });

        buttons.addView(cancel);
        buttons.addView(save);

        root.addView(topActions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(searchInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        root.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.show();
    }

    private View createFolderPreviewButton(@NonNull PinnedFolderItem folder) {
        NotificationBadgeFrame root = new NotificationBadgeFrame(getContext());
        Set<String> folderPackages = new HashSet<>();
        for (PinnedAppItem folderApp : folder.apps) {
            if (folderApp != null && folderApp.appRef != null && !TextUtils.isEmpty(folderApp.appRef.packageName)) {
                folderPackages.add(folderApp.appRef.packageName);
            }
        }
        root.setBadgePackages(folderPackages);
        root.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout iconShell = new FrameLayout(getContext());
        int shellSize = iconSizePx();
        iconShell.setBackground(createPinnedFolderShellBackground());
        iconShell.setLayoutParams(new LinearLayout.LayoutParams(shellSize, shellSize));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            iconShell.setClipToOutline(true);
        }
        iconShell.setPadding(0, 0, 0, 0);

        GridLayout miniGrid = new GridLayout(getContext());
        miniGrid.setColumnCount(2);
        miniGrid.setRowCount(2);
        miniGrid.setUseDefaultMargins(false);
        miniGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        int miniSize = Math.max(dp(9), Math.round(shellSize * 0.42f));
        int placed = 0;
        for (PinnedAppItem folderApp : folder.apps) {
            if (placed >= 4) break;
            LauncherAppEntry e = resolvePinnedApp(folderApp);
            if (e == null || e.icon == null) continue;
            ImageView mini = new ImageView(getContext());
            mini.setImageDrawable(e.icon);
            mini.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = miniSize;
            params.height = miniSize;
            int miniMargin = pinnedFolderMiniIconMarginPx();
            params.setMargins(miniMargin, miniMargin, miniMargin, miniMargin);
            mini.setLayoutParams(params);
            miniGrid.addView(mini);
            placed++;
        }
        iconShell.addView(miniGrid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(iconShell, new FrameLayout.LayoutParams(shellSize, shellSize, Gravity.CENTER));
        return root;
    }

    @NonNull
    private GradientDrawable createPinnedFolderShellBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(PINNED_FOLDER_FILL_COLOR);
        bg.setStroke(1, PINNED_FOLDER_STROKE_COLOR);
        return bg;
    }

    private int pinnedFolderMiniIconMarginPx() {
        return dp(1);
    }

    private void resetPinnedIcon(int index, @NonNull PinnedAppItem item) {
        if (index >= 0 && index < pinnedItems.size()) {
            pinnedItems.set(index, new PinnedAppItem(item.appRef));
            persistPinsAndReload();
        }
    }

    private void changeFolderAppIcon(@NonNull AppMenuContext context) {
        if (context.sourceFolder == null || context.folderEntryRef == null) return;
        PinnedAppItem folderApp = findFolderApp(context.sourceFolder, context.folderEntryRef);
        if (folderApp == null) return;
        showIconPackPicker(folderApp, override -> {
            updateFolderAppIconOverride(context.sourceFolder, folderApp.appRef, override);
            persistPinsAndReload();
        });
    }

    private void resetFolderAppIcon(@NonNull AppMenuContext context) {
        if (context.sourceFolder == null || context.folderEntryRef == null) return;
        if (updateFolderAppIconOverride(context.sourceFolder, context.folderEntryRef, null)) {
            persistPinsAndReload();
        }
    }

    private boolean updateFolderAppIconOverride(
        @NonNull PinnedFolderItem folder,
        @NonNull AppRef ref,
        @Nullable PinnedIconOverride override
    ) {
        AppRef resolved = resolveForSelectionRef(ref);
        String targetStable = resolved.stableId();
        for (int i = 0; i < folder.apps.size(); i++) {
            PinnedAppItem folderApp = folder.apps.get(i);
            if (targetStable.equals(resolveForSelectionRef(folderApp.appRef).stableId())) {
                folder.apps.set(i, new PinnedAppItem(resolveForSelectionRef(folderApp.appRef), override));
                return true;
            }
        }
        return false;
    }

    private void showPinnedIconPackPicker(int index, @NonNull PinnedAppItem item) {
        showIconPackPicker(item, override -> {
            if (index >= 0 && index < pinnedItems.size()) {
                pinnedItems.set(index, new PinnedAppItem(item.appRef, override));
                persistPinsAndReload();
            }
        });
    }

    private void showIconPackPicker(@NonNull PinnedAppItem item, @NonNull IconOverrideApplier applier) {
        dismissIconPickerPopup();
        List<IconPackInfo> packs = getIconPackRepository().discoverIconPacks();
        if (packs.isEmpty()) {
            showIconPickerMessagePopup("Change icon", "No compatible icon packs are installed.");
            return;
        }

        CharSequence[] labels = new CharSequence[packs.size()];
        for (int i = 0; i < packs.size(); i++) {
            labels[i] = packs.get(i).label;
        }
        iconPickerDialog = new MaterialAlertDialogBuilder(getContext())
            .setTitle("Icon pack")
            .setItems(labels, (dialog, which) -> {
                if (which < 0 || which >= packs.size()) return;
                dialog.dismiss();
                iconPickerDialog = null;
                showIconDrawablePicker(item, packs.get(which), applier);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        iconPickerDialog.setOnDismissListener(dismissedDialog -> {
            if (iconPickerDialog != null && !iconPickerDialog.isShowing()) {
                iconPickerDialog = null;
            }
        });
        iconPickerDialog.show();
    }

    private void showIconDrawablePicker(
        @NonNull PinnedAppItem item,
        @NonNull IconPackInfo packInfo,
        @NonNull IconOverrideApplier applier
    ) {
        dismissIconPickerPopup();
        IconPack pack = getIconPackRepository().loadIconPack(packInfo.packageName);
        if (pack == null || pack.drawableItems().isEmpty()) {
            showIconPickerMessagePopup(packInfo.label, "This icon pack does not expose selectable icons.");
            return;
        }

        List<IconPackDrawableItem> source = pack.drawableItems();
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(12));

        TextView title = new TextView(getContext());
        title.setText(packInfo.label);
        title.setTextColor(resolveLauncherTextColor());
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText search = new EditText(getContext());
        search.setSingleLine(true);
        search.setHint("Search icons");
        search.setTextColor(resolveLauncherTextColor());
        search.setHintTextColor(resolveLauncherSubtleTextColor());
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(dp(8));
        searchBg.setColor(withAlphaComponent(resolveLauncherPanelColor(), 0xF2));
        searchBg.setStroke(dp(1), withAlphaComponent(resolveLauncherOutlineColor(), 0x66));
        search.setBackground(searchBg);
        search.setPadding(dp(10), 0, dp(10), 0);
        search.setMinHeight(dp(38));

        GridView iconGrid = new GridView(getContext());
        iconGrid.setNumColumns(GridView.AUTO_FIT);
        iconGrid.setColumnWidth(dp(74));
        iconGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        iconGrid.setVerticalSpacing(dp(8));
        iconGrid.setHorizontalSpacing(dp(8));
        iconGrid.setClipToPadding(false);
        iconGrid.setPadding(0, dp(2), 0, dp(2));
        iconGrid.setBackgroundColor(0x00000000);
        iconGrid.setSelector(new ColorDrawable(0x00000000));
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        gridParams.setMargins(0, dp(10), 0, 0);
        root.addView(iconGrid, gridParams);

        List<IconPackDrawableItem> filtered = new ArrayList<>(source);
        IconDrawableGridAdapter adapter = new IconDrawableGridAdapter(packInfo.packageName, filtered);
        iconGrid.setAdapter(adapter);

        iconGrid.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filtered.size()) return;
            IconPackDrawableItem selected = filtered.get(position);
            applier.apply(new PinnedIconOverride(
                PinnedIconOverride.SOURCE_ICON_PACK,
                packInfo.packageName,
                selected.drawableName,
                selected.label
            ));
            dismissIconPickerPopup();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                filtered.clear();
                for (IconPackDrawableItem candidate : source) {
                    if (query.isEmpty()
                        || candidate.label.toLowerCase(Locale.US).contains(query)
                        || candidate.drawableName.toLowerCase(Locale.US).contains(query)) {
                        filtered.add(candidate);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        Dialog dialog = new Dialog(getContext(), android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        View dialogSurface = buildIconPickerDialogSurface(root);
        dialog.setContentView(dialogSurface);
        iconPickerDialog = dialog;
        dialog.setOnShowListener(shownDialog -> configureIconPickerDialogWindow(dialog, dialogSurface, root));
        iconPickerDialog.setOnDismissListener(dismissedDialog -> {
            if (iconPickerDialog != null && !iconPickerDialog.isShowing()) {
                iconPickerDialog = null;
            }
        });
        iconPickerDialog.show();
    }

    @NonNull
    private View buildIconPickerDialogSurface(@NonNull View content) {
        FrameLayout overlay = new FrameLayout(getContext());
        overlay.setClipToPadding(false);
        overlay.setPadding(0, 0, 0, 0);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        overlay.setMinimumWidth(screenWidth);
        overlay.setMinimumHeight(screenHeight);
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
            screenWidth,
            screenHeight
        ));

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(dp(12));
        panelBg.setColor(withAlphaComponent(resolveLauncherPanelColor(), 0xF4));
        content.setBackground(panelBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            content.setClipToOutline(true);
            content.setElevation(dp(8));
        }

        int sideMargin = dp(18);
        int topMargin = iconPickerTopMargin();
        int bottomMargin = dp(24);
        int cardWidth = screenWidth >= dp(640) ? dp(560) : Math.max(dp(280), screenWidth - (sideMargin * 2));
        int cardHeight = Math.max(dp(360), screenHeight - topMargin - bottomMargin);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            cardWidth,
            cardHeight,
            Gravity.CENTER
        );
        params.setMargins(sideMargin, topMargin, sideMargin, bottomMargin);
        overlay.addView(content, params);
        return overlay;
    }

    private int iconPickerTopMargin() {
        return getStatusBarHeight() + dp(20);
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : dp(24);
    }

    private void configureIconPickerDialogWindow(
        @NonNull Dialog dialog,
        @NonNull View dialogSurface,
        @NonNull View content
    ) {
        android.view.Window window = dialog.getWindow();
        if (window == null) {
            return;
        }

        window.setBackgroundDrawable(new ColorDrawable(0x00000000));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.32f);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING |
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        );
        installKeyboardAwareIconPickerLayout(dialogSurface, content);
    }

    private void installKeyboardAwareIconPickerLayout(@NonNull View dialogSurface, @NonNull View content) {
        ViewTreeObserver observer = dialogSurface.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(() -> {
            Rect visibleFrame = new Rect();
            dialogSurface.getWindowVisibleDisplayFrame(visibleFrame);
            int fullHeight = dialogSurface.getRootView() == null ? dialogSurface.getHeight() : dialogSurface.getRootView().getHeight();
            int keyboardHeight = Math.max(0, fullHeight - visibleFrame.bottom);
            int sideMargin = dp(18);
            int topMargin = iconPickerTopMargin();
            int bottomMargin = dp(24) + keyboardHeight;
            int availableHeight = Math.max(dp(280), fullHeight - topMargin - bottomMargin);
            ViewGroup.LayoutParams rawParams = content.getLayoutParams();
            if (!(rawParams instanceof FrameLayout.LayoutParams)) {
                return;
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
            if (params.leftMargin == sideMargin
                && params.topMargin == topMargin
                && params.rightMargin == sideMargin
                && params.bottomMargin == bottomMargin
                && params.height == availableHeight) {
                return;
            }
            params.setMargins(sideMargin, topMargin, sideMargin, bottomMargin);
            params.height = availableHeight;
            content.setLayoutParams(params);
        });
    }

    private void showIconPickerMessagePopup(@NonNull String title, @NonNull String message) {
        dismissIconPickerPopup();
        iconPickerDialog = new MaterialAlertDialogBuilder(getContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        iconPickerDialog.setOnDismissListener(dialog -> {
            if (iconPickerDialog != null && !iconPickerDialog.isShowing()) {
                iconPickerDialog = null;
            }
        });
        iconPickerDialog.show();
    }

    private final class IconDrawableGridAdapter extends BaseAdapter {
        @NonNull private final String iconPackPackage;
        @NonNull private final List<IconPackDrawableItem> items;

        IconDrawableGridAdapter(@NonNull String iconPackPackage, @NonNull List<IconPackDrawableItem> items) {
            this.iconPackPackage = iconPackPackage;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public IconPackDrawableItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView iconView;
            TextView labelView;
            if (convertView instanceof LinearLayout && ((LinearLayout) convertView).getChildCount() >= 2) {
                cell = (LinearLayout) convertView;
                iconView = (ImageView) cell.getChildAt(0);
                labelView = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(getContext());
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(6), dp(6), dp(6), dp(6));
                iconView = new ImageView(getContext());
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(iconView, new LinearLayout.LayoutParams(dp(48), dp(48)));
                labelView = new TextView(getContext());
                labelView.setGravity(Gravity.CENTER);
                labelView.setSingleLine(true);
                labelView.setEllipsize(TextUtils.TruncateAt.END);
                labelView.setTextSize(10f);
                labelView.setTextColor(resolveLauncherTextColor());
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                labelParams.setMargins(0, dp(4), 0, 0);
                cell.addView(labelView, labelParams);
            }

            GradientDrawable cellBg = new GradientDrawable();
            cellBg.setCornerRadius(dp(8));
            cellBg.setColor(blendColors(withAlphaComponent(resolveLauncherPanelColor(), 0x22), withAlphaComponent(inheritedTintColor, 0x22), 0.35f));
            cellBg.setStroke(dp(1), withAlphaComponent(resolveLauncherOutlineColor(), 0x22));
            cell.setBackground(cellBg);
            labelView.setTextColor(resolveLauncherTextColor());

            IconPackDrawableItem item = getItem(position);
            Drawable icon = getIconResolver().loadDrawableFromPack(iconPackPackage, item.drawableName);
            iconView.setImageDrawable(icon != null ? icon : getContext().getPackageManager().getDefaultActivityIcon());
            labelView.setText(item.label);
            return cell;
        }
    }

    private void showReplacePinnedApp(int index) {
        if (allApps == null || allApps.isEmpty()) reloadAllApps();
        String[] labels = appLabels(allApps);
        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Replace pinned app")
            .setItems(labels, (dialog, which) -> {
                AppRef ref = allApps.get(which).appRef;
                if (index >= 0 && index < pinnedItems.size()) {
                    pinnedItems.set(index, new PinnedAppItem(ref));
                }
                persistPinsAndReload();
            })
            .show();
    }

    private void showMovePinnedAppToFolder(int appIndex, PinnedAppItem item) {
        List<PinnedFolderItem> folders = allFolders();
        if (folders.isEmpty()) {
            showCreateFolderWithSeed(appIndex, item);
            return;
        }
        String[] names = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) names[i] = folders.get(i).title;

        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Move to folder")
            .setItems(names, (dialog, which) -> {
                PinnedFolderItem folder = folders.get(which);
                addPinnedAppToFolderIfMissing(folder, item);
                if (appIndex >= 0 && appIndex < pinnedItems.size()) {
                    pinnedItems.remove(appIndex);
                }
                persistPinsAndReload();
            })
            .show();
    }

    private void showCreateFolderWithSeed(int appIndex, PinnedAppItem item) {
        EditText titleInput = new EditText(getContext());
        titleInput.setHint("Folder name");
        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Create folder")
            .setView(titleInput)
            .setPositiveButton("Create", (dialog, which) -> {
                String title = titleInput.getText() == null ? "Folder" : titleInput.getText().toString().trim();
                if (title.isEmpty()) title = "Folder";
                PinnedFolderItem folder = new PinnedFolderItem(UUID.randomUUID().toString(), title);
                addPinnedAppToFolderIfMissing(folder, item);

                if (appIndex >= 0 && appIndex < pinnedItems.size()) {
                    pinnedItems.set(appIndex, folder);
                } else {
                    pinnedItems.add(folder);
                }
                persistPinsAndReload();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showFolderAppEditor(PinnedFolderItem folder) {
        if (allApps == null || allApps.isEmpty()) reloadAllApps();
        boolean[] checked = new boolean[allApps.size()];
        Set<String> existing = new HashSet<>();
        for (PinnedAppItem folderApp : folder.apps) {
            existing.add(resolveForSelectionId(folderApp.appRef));
        }
        for (int i = 0; i < allApps.size(); i++) {
            checked[i] = existing.contains(allApps.get(i).appRef.stableId());
        }

        String[] labels = appLabels(allApps);
        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Edit folder apps")
            .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                checked[which] = isChecked;
            })
            .setPositiveButton("Save", (dialog, which) -> {
                Map<String, PinnedIconOverride> existingOverrides = folderIconOverridesByStableId(folder);
                folder.apps.clear();
                for (int i = 0; i < checked.length; i++) {
                    if (checked[i]) {
                        AppRef ref = resolveForSelectionRef(allApps.get(i).appRef);
                        folder.apps.add(new PinnedAppItem(ref, existingOverrides.get(ref.stableId())));
                    }
                }
                persistPinsAndReload();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showFolderSettings(PinnedFolderItem folder) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable panel = new GradientDrawable();
        panel.setCornerRadius(dp(14));
        panel.setColor(withAlphaComponent(resolveLauncherPanelColor(), 0xEE));
        panel.setStroke(dp(1), withAlphaComponent(resolveLauncherOutlineColor(), 0x66));
        layout.setBackground(panel);

        TextView title = new TextView(getContext());
        title.setText("Folder settings");
        title.setTextColor(resolveLauncherTextColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(14f);
        title.setPadding(0, 0, 0, dp(8));

        EditText nameInput = new EditText(getContext());
        nameInput.setHint("Folder name");
        nameInput.setText(TextUtils.isEmpty(folder.title) ? "Folder" : folder.title);

        final int[] rowsValue = new int[] {clamp(folder.rows, 1, PinnedFolderItem.MAX_GRID)};
        final int[] colsValue = new int[] {clamp(folder.cols, 1, PinnedFolderItem.MAX_GRID)};
        LinearLayout rowsControl = buildStepperRow("Rows", rowsValue, 1, PinnedFolderItem.MAX_GRID);
        LinearLayout colsControl = buildStepperRow("Columns", colsValue, 1, PinnedFolderItem.MAX_GRID);

        EditText colorInput = new EditText(getContext());
        colorInput.setHint("Tint color");
        colorInput.setText(folder.tintOverrideEnabled ? String.format(Locale.US, "#%08X", folder.tintColor) : "");
        colorInput.setSingleLine(true);
        colorInput.setHint("#AARRGGBB or #RRGGBB");

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        Button cancel = new Button(getContext());
        cancel.setText("Cancel");
        styleGhostButton(cancel);

        Button save = new Button(getContext());
        save.setText("Save");
        styleGhostButton(save);

        buttons.addView(cancel);
        buttons.addView(save);

        layout.addView(title);
        layout.addView(nameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(rowsControl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(colsControl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(colorInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
            .setView(layout)
            .create();

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String newName = stringValue(nameInput.getText()).trim();
            folder.title = newName.isEmpty() ? "Folder" : newName;
            folder.rows = rowsValue[0];
            folder.cols = colsValue[0];
            String color = stringValue(colorInput.getText()).trim();
            if (color.isEmpty()) {
                folder.tintOverrideEnabled = false;
            } else {
                Integer parsed = parseColor(color);
                if (parsed != null) {
                    folder.tintOverrideEnabled = true;
                    folder.tintColor = parsed;
                }
            }
            dialog.dismiss();
            persistPinsAndReload();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            );
        }
    }

    private void showFolderPopup(PinnedFolderItem folder, @Nullable View anchor) {
        dismissFolderPopup();
        dismissAppContextPopup();
        dismissShortcutsPopup();

        List<LauncherAppEntry> folderEntries = new ArrayList<>();
        for (PinnedAppItem folderApp : folder.apps) {
            LauncherAppEntry entry = resolvePinnedApp(folderApp);
            if (entry != null) folderEntries.add(entry);
        }
        if (folderEntries.isEmpty()) {
            return;
        }

        int rows = clamp(folder.rows, 1, PinnedFolderItem.MAX_GRID);
        int cols = clamp(folder.cols, 1, PinnedFolderItem.MAX_GRID);
        int cellCount = rows * cols;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int popupIconSize = computeFolderPopupIconSize(rows, cols, screenW, screenH);

        GridLayout grid = new GridLayout(getContext());
        grid.setColumnCount(cols);
        grid.setRowCount(rows);
        grid.setPadding(dp(3), dp(3), dp(3), dp(3));
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        for (int i = 0; i < folderEntries.size() && i < cellCount; i++) {
            LauncherAppEntry entry = folderEntries.get(i);
            View btn = createPopupEntryButton(entry, popupIconSize, folder);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = popupIconSize;
            params.height = popupIconSize;
            params.columnSpec = GridLayout.spec(i % cols);
            params.rowSpec = GridLayout.spec(i / cols);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            btn.setLayoutParams(params);
            grid.addView(btn);
        }

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(3), dp(3), dp(3), dp(3));

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(2), dp(4), dp(4));

        TextView title = new TextView(getContext());
        title.setText(TextUtils.isEmpty(folder.title) ? "Folder" : folder.title);
        title.setTextColor(resolveLauncherTextColor());
        title.setTextSize(12f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        title.setClickable(true);
        title.setOnClickListener(v -> {
            dismissFolderPopup();
            showRenameFolderDialog(folder);
        });

        ImageButton gear = new ImageButton(getContext());
        gear.setImageResource(R.drawable.ic_settings);
        styleIconButton(gear, dp(3));
        int gearSize = dp(24);
        gear.setOnClickListener(v -> {
            dismissFolderPopup();
            int folderIndex = findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                showFolderContentsEditor(folderIndex, folder);
            }
        });

        header.addView(title);
        header.addView(gear, new LinearLayout.LayoutParams(gearSize, gearSize));
        shell.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int overlayBase = folder.tintOverrideEnabled ? (folder.tintColor & 0x00FFFFFF) : (inheritedTintColor & 0x00FFFFFF);
        folderPopupWindow = buildPopupWindow(shell, overlayBase, true, () -> {
            if (folderPopupWindow != null && !folderPopupWindow.isShowing()) {
                folderPopupWindow = null;
            }
        });
        showPopupAtAnchor(folderPopupWindow, anchor);
    }

    private void removePinnedAt(int index) {
        if (index >= 0 && index < pinnedItems.size()) {
            pinnedItems.remove(index);
            persistPinsAndReload();
        }
    }

    private void persistPinsAndReload() {
        dismissAppContextPopup();
        dismissShortcutsPopup();
        if (configRepository != null) {
            configRepository.savePinnedItems(pinnedItems);
            pinnedItems = configRepository.loadPinnedItems();
        }
        pendingPinnedMutationFeedback = true;
        invalidateRenderedIconCaches();
        reloadWithInput("", lastTerminalView);
    }

    private void dismissFolderPopup() {
        if (folderPopupWindow != null) {
            final PopupWindow popup = folderPopupWindow;
            dismissPopupWindowAnimated(popup, () -> {
                if (folderPopupWindow == popup) folderPopupWindow = null;
            });
        }
    }

    private List<PinnedFolderItem> allFolders() {
        List<PinnedFolderItem> out = new ArrayList<>();
        for (PinnedItem item : pinnedItems) {
            if (item instanceof PinnedFolderItem) {
                out.add((PinnedFolderItem) item);
            }
        }
        return out;
    }

    private void showRenameFolderDialog(@NonNull PinnedFolderItem folder) {
        EditText titleInput = new EditText(getContext());
        titleInput.setHint("Folder name");
        titleInput.setText(TextUtils.isEmpty(folder.title) ? "Folder" : folder.title);
        new MaterialAlertDialogBuilder(getContext())
            .setTitle("Rename folder")
            .setView(titleInput)
            .setPositiveButton("Save", (dialog, which) -> {
                String updated = stringValue(titleInput.getText()).trim();
                folder.title = updated.isEmpty() ? "Folder" : updated;
                persistPinsAndReload();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @NonNull
    private static View resolvePrimaryPressTarget(@NonNull View view) {
        if (view instanceof FrameLayout) {
            FrameLayout frame = (FrameLayout) view;
            if (frame.getChildCount() > 0 && frame.getChildAt(0) != null) {
                return frame.getChildAt(0);
            }
        }
        return view;
    }

    private void bindAppContextLongPress(
        @NonNull View pressTarget,
        @NonNull LauncherAppEntry entry,
        int pinnedIndex,
        @Nullable PinnedFolderItem sourceFolder,
        @Nullable AppRef folderEntryRef,
        boolean allowDragPickup
    ) {
        Runnable notificationSwipeAction = pinnedIndex >= 0
            ? () -> showNotificationPopup(entry, pressTarget)
            : null;
        bindContextLongPressGesture(pressTarget, pinnedIndex, allowDragPickup, () -> {
            dismissShortcutsPopup();
            showAppContextPopup(new AppMenuContext(entry, pressTarget, pinnedIndex, sourceFolder, folderEntryRef));
        }, notificationSwipeAction, entry.appRef.packageName);
    }

    private void bindFolderContextLongPress(
        @NonNull View pressTarget,
        @NonNull PinnedFolderItem folder,
        int pinnedIndex,
        boolean allowDragPickup
    ) {
        bindContextLongPressGesture(pressTarget, pinnedIndex, allowDragPickup, () -> {
            dismissFolderPopup();
            showFolderContextPopup(folder, pinnedIndex, pressTarget);
        }, null, null);
    }

    private void bindContextLongPressGesture(
        @NonNull View pressTarget,
        int pinnedIndex,
        boolean allowDragPickup,
        @NonNull Runnable showContextPopup,
        @Nullable Runnable notificationSwipeAction,
        @Nullable String notificationPackage
    ) {
        pressTarget.setLongClickable(true);
        pressTarget.setOnLongClickListener(v -> {
            if (suppressContextLongPressForSwipe) {
                return true;
            }
            showContextPopup.run();
            LongPressPickupState state = activeLongPressPickupState;
            if (state == null || state.sourceView != pressTarget) {
                state = new LongPressPickupState(pressTarget, pinnedIndex, 0f, 0f);
                activeLongPressPickupState = state;
            }
            state.menuShown = true;
            state.menuShownAtMs = SystemClock.uptimeMillis();
            state.definitiveYMovement = false;
            state.selectionArmed = false;
            return true;
        });
        pressTarget.setOnTouchListener((v, event) -> {
            if (event == null) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                animateLaunchPressDown(pressTarget);
                activeLongPressPickupState = new LongPressPickupState(
                    pressTarget,
                    pinnedIndex,
                    event.getRawX(),
                    event.getRawY()
                );
            } else if (action == MotionEvent.ACTION_MOVE) {
                LongPressPickupState state = activeLongPressPickupState;
                if (state != null && state.sourceView == pressTarget && state.notificationSwipeStarted) {
                    return true;
                }
                if (state != null && state.sourceView == pressTarget && !state.menuShown
                    && notificationSwipeAction != null
                    && LauncherNotificationBadgeStore.hasBadge(notificationPackage)) {
                    float dx = event.getRawX() - state.downRawX;
                    float dy = event.getRawY() - state.downRawY;
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (dy <= -(slop * 1.8f) && Math.abs(dy) > Math.abs(dx) * 1.15f) {
                        state.notificationSwipeStarted = true;
                        suppressContextLongPressForSwipe = true;
                        pressTarget.cancelLongPress();
                        animateLaunchReleaseBounce(pressTarget);
                        notificationSwipeAction.run();
                        return true;
                    }
                }
                if (state != null && state.sourceView == pressTarget && state.menuShown && !state.dragStarted) {
                    float rawX = event.getRawX();
                    float rawY = event.getRawY();
                    float dx = rawX - state.downRawX;
                    float dy = rawY - state.downRawY;
                    float absDx = Math.abs(dx);
                    float absDy = Math.abs(dy);
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    float yIntentThreshold = slop * PICKUP_Y_INTENT_SLOP_FACTOR;
                    float xPickupThreshold = slop * PICKUP_X_AXIS_SLOP_FACTOR;
                    if (absDy >= yIntentThreshold) {
                        state.definitiveYMovement = true;
                    }
                    if (!state.selectionArmed && Math.max(absDx, absDy) >= (slop * MENU_SELECTION_ARM_SLOP_FACTOR)) {
                        state.selectionArmed = true;
                    }

                    boolean withinPickupWindow = (SystemClock.uptimeMillis() - state.menuShownAtMs) <= PICKUP_DECISION_WINDOW_MS;
                    boolean shouldStartPickup = allowDragPickup
                        && withinPickupWindow
                        && !state.definitiveYMovement
                        && absDx >= xPickupThreshold
                        && pinnedIndex >= 0;

                    if (shouldStartPickup) {
                        state.dragStarted = true;
                        clearMenuHighlight();
                        dismissAppContextPopup();
                        dismissFolderPopup();
                        startPinnedDrag(pressTarget, pinnedIndex);
                        activeLongPressPickupState = null;
                        return true;
                    }

                    // Drag-back-to-cancel: once the finger has slid up off the icon onto the menu,
                    // sliding back down onto the originating icon closes the menu without acting.
                    boolean overAnchor = isRawInsideView(pressTarget, rawX, rawY);
                    if (!overAnchor) {
                        state.leftAnchor = true;
                    } else if (state.leftAnchor) {
                        clearMenuHighlight();
                        dismissAppContextPopup();
                        activeLongPressPickupState = null;
                        return true;
                    }

                    boolean highlighted = updateMenuHighlightForRaw(rawX, rawY, true, state.selectionArmed);
                    if (highlighted) {
                        state.selectionArmed = true;
                    }
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                animateLaunchReleaseBounce(pressTarget);
                LongPressPickupState state = activeLongPressPickupState;
                if (state != null && state.sourceView == pressTarget) {
                    if (state.notificationSwipeStarted) {
                        activeLongPressPickupState = null;
                        suppressContextLongPressForSwipe = false;
                        return true;
                    }
                    if (action == MotionEvent.ACTION_UP && state.menuShown && !state.dragStarted) {
                        if (state.selectionArmed) {
                            updateMenuHighlightForRaw(event.getRawX(), event.getRawY(), true, true);
                            executeHighlightedMenuActionOrKeepOpen();
                        }
                        activeLongPressPickupState = null;
                        return true;
                    }
                    if (action == MotionEvent.ACTION_CANCEL) {
                        clearMenuHighlight();
                    }
                    activeLongPressPickupState = null;
                }
            }
            return false;
        });
    }

    private void cancelPendingContextLongPresses() {
        cancelLongPress();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null) continue;
            child.cancelLongPress();
            View pressTarget = resolvePrimaryPressTarget(child);
            if (pressTarget != child) {
                pressTarget.cancelLongPress();
            }
        }
    }

    private void showAppContextPopup(@NonNull AppMenuContext context) {
        dismissAppContextPopup();
        List<ShortcutInfo> shortcuts = queryEntryShortcuts(context.entry);
        boolean hasShortcuts = !shortcuts.isEmpty();
        boolean folderSource = context.sourceFolder != null && context.folderEntryRef != null;
        int topPinnedIndex = context.pinnedIndex >= 0 ? context.pinnedIndex : findPinnedAppIndex(context.entry.appRef);
        boolean topPinned = topPinnedIndex >= 0;

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(3), dp(3), dp(3), dp(3));
        appContextRows.clear();
        shortcutsRows.clear();
        clearMenuHighlight();
        shortcutsMainRowView = null;
        activeAppMenuContext = context;
        activeAppMenuShortcuts = shortcuts;

        TextView header = new TextView(getContext());
        header.setText(context.entry.label);
        header.setTextColor(resolveLauncherTextColor());
        header.setTextSize(12f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(6), dp(8), dp(7));
        Drawable headerIcon = resolveMenuHeaderIcon(context.entry);
        if (headerIcon != null) {
            headerIcon.setBounds(0, 0, dp(22), dp(22));
            header.setCompoundDrawablesRelative(headerIcon, null, null, null);
            header.setCompoundDrawablePadding(dp(10));
        }
        shell.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int tintBase = context.sourceFolder != null && context.sourceFolder.tintOverrideEnabled
            ? (context.sourceFolder.tintColor & 0x00FFFFFF)
            : (inheritedTintColor & 0x00FFFFFF);
        activeMenuTintBase = tintBase;

        TextView uninstallRow = addPopupActionRow(shell, "Uninstall", R.drawable.ic_dock_menu_uninstall, false, tintBase, () -> {
            dismissAppContextPopup();
            requestUninstall(context.entry);
        });
        appContextRows.add(new MenuActionRow(uninstallRow, () -> {
            dismissAppContextPopup();
            requestUninstall(context.entry);
        }, false));

        TextView appInfoRow = addPopupActionRow(shell, "App info", R.drawable.ic_dock_menu_info, false, tintBase, () -> {
            dismissAppContextPopup();
            openAppInfo(context.entry);
        });
        appContextRows.add(new MenuActionRow(appInfoRow, () -> {
            dismissAppContextPopup();
            openAppInfo(context.entry);
        }, false));

        if (folderSource) {
            PinnedAppItem folderApp = findFolderApp(context.sourceFolder, context.folderEntryRef);
            boolean folderHasCustomIcon = folderApp != null
                && getIconResolver().loadOverride(folderApp.iconOverride) != null;
            TextView changeIconRow = addPopupActionRow(shell, "Change icon in folder", R.drawable.ic_dock_menu_change_icon, false, tintBase, () -> {
                dismissAppContextPopup();
                changeFolderAppIcon(context);
            });
            appContextRows.add(new MenuActionRow(changeIconRow, () -> {
                dismissAppContextPopup();
                changeFolderAppIcon(context);
            }, false));

            if (folderHasCustomIcon) {
                TextView resetIconRow = addPopupActionRow(shell, "Reset icon in folder", R.drawable.ic_dock_menu_reset, false, tintBase, () -> {
                    dismissAppContextPopup();
                    resetFolderAppIcon(context);
                });
                appContextRows.add(new MenuActionRow(resetIconRow, () -> {
                    dismissAppContextPopup();
                    resetFolderAppIcon(context);
                }, false));
            }

            addAppWideIconRows(shell, context.entry, tintBase);

            TextView moveToDockRow = addPopupActionRow(shell, "Move to dock", R.drawable.ic_dock_menu_move, false, tintBase, () -> {
                dismissAppContextPopup();
                moveContextEntryToDock(context);
            });
            appContextRows.add(new MenuActionRow(moveToDockRow, () -> {
                dismissAppContextPopup();
                moveContextEntryToDock(context);
            }, false));

            TextView deleteRow = addPopupActionRow(shell, "Delete", R.drawable.ic_dock_menu_uninstall, false, tintBase, () -> {
                dismissAppContextPopup();
                removeFromContextSource(context);
            });
            appContextRows.add(new MenuActionRow(deleteRow, () -> {
                dismissAppContextPopup();
                removeFromContextSource(context);
            }, false));
        } else if (topPinned) {
            final int targetPinnedIndex = topPinnedIndex;
            PinnedAppItem topPinnedApp = pinnedAppAt(targetPinnedIndex);
            boolean pinnedHasCustomIcon = topPinnedApp != null
                && getIconResolver().loadOverride(topPinnedApp.iconOverride) != null;
            if (LauncherNotificationBadgeStore.hasBadge(context.entry.appRef.packageName)) {
                TextView notificationsRow = addPopupActionRow(shell, "Notifications", R.drawable.ic_dock_menu_info, false, tintBase, () -> {
                    dismissAppContextPopup();
                    showNotificationPopup(context.entry, context.anchor);
                });
                appContextRows.add(new MenuActionRow(notificationsRow, () -> {
                    dismissAppContextPopup();
                    showNotificationPopup(context.entry, context.anchor);
                }, false));
            }
            TextView changeIconRow = addPopupActionRow(shell, "Change dock icon", R.drawable.ic_dock_menu_change_icon, false, tintBase, () -> {
                dismissAppContextPopup();
                PinnedAppItem pinnedApp = pinnedAppAt(targetPinnedIndex);
                if (pinnedApp != null) {
                    showPinnedIconPackPicker(targetPinnedIndex, pinnedApp);
                }
            });
            appContextRows.add(new MenuActionRow(changeIconRow, () -> {
                dismissAppContextPopup();
                PinnedAppItem pinnedApp = pinnedAppAt(targetPinnedIndex);
                if (pinnedApp != null) {
                    showPinnedIconPackPicker(targetPinnedIndex, pinnedApp);
                }
            }, false));

            if (pinnedHasCustomIcon) {
                TextView resetIconRow = addPopupActionRow(shell, "Reset dock icon", R.drawable.ic_dock_menu_reset, false, tintBase, () -> {
                    dismissAppContextPopup();
                    PinnedAppItem pinnedApp = pinnedAppAt(targetPinnedIndex);
                    if (pinnedApp != null) {
                        resetPinnedIcon(targetPinnedIndex, pinnedApp);
                    }
                });
                appContextRows.add(new MenuActionRow(resetIconRow, () -> {
                    dismissAppContextPopup();
                    PinnedAppItem pinnedApp = pinnedAppAt(targetPinnedIndex);
                    if (pinnedApp != null) {
                        resetPinnedIcon(targetPinnedIndex, pinnedApp);
                    }
                }, false));
            }

            addAppWideIconRows(shell, context.entry, tintBase);

            TextView unpinRow = addPopupActionRow(shell, "Unpin", R.drawable.ic_dock_menu_pin, false, tintBase, () -> {
                dismissAppContextPopup();
                removePinnedAt(targetPinnedIndex);
            });
            appContextRows.add(new MenuActionRow(unpinRow, () -> {
                dismissAppContextPopup();
                removePinnedAt(targetPinnedIndex);
            }, false));
        } else {
            TextView pinRow = addPopupActionRow(shell, "Pin", R.drawable.ic_dock_menu_pin, false, tintBase, () -> {
                dismissAppContextPopup();
                pinEntryToTopLevel(context.entry);
            });
            appContextRows.add(new MenuActionRow(pinRow, () -> {
                dismissAppContextPopup();
                pinEntryToTopLevel(context.entry);
            }, false));

            TextView changeIconRow = addPopupActionRow(shell, "Change app icon", R.drawable.ic_dock_menu_change_icon, false, tintBase, () -> {
                dismissAppContextPopup();
                changeAppIconForEntry(context.entry);
            });
            appContextRows.add(new MenuActionRow(changeIconRow, () -> {
                dismissAppContextPopup();
                changeAppIconForEntry(context.entry);
            }, false));
            addResetAppIconRowIfNeeded(shell, context.entry, tintBase);
        }

        if (hasShortcuts) {
            addPopupMenuDivider(shell);
            TextView shortcutsRow = addPopupActionRow(shell, "Shortcuts", R.drawable.ic_dock_menu_shortcuts, true, tintBase, () -> {
                if (shortcutsPopupWindow != null && shortcutsPopupWindow.isShowing()) {
                    dismissShortcutsPopup();
                    clearMenuHighlight();
                } else {
                    showShortcutsPopup(context, shortcuts, shortcutsMainRowView);
                }
            });
            shortcutsMainRowView = shortcutsRow;
            appContextRows.add(new MenuActionRow(shortcutsRow, () -> {
                if (shortcutsPopupWindow == null || !shortcutsPopupWindow.isShowing()) {
                    showShortcutsPopup(context, shortcuts, shortcutsMainRowView);
                }
            }, true));
        }

        int rowWidth = normalizePopupRowWidths(appContextRows);
        int contentWidth = constrainPopupHeaderWidth(header, rowWidth);
        constrainPopupRowsWidth(appContextRows, contentWidth);

        appContextPopupWindow = buildPopupWindow(shell, tintBase, true, () -> {
            if (appContextPopupWindow != null && !appContextPopupWindow.isShowing()) {
                appContextPopupWindow = null;
            }
        });
        showPopupAtAnchor(appContextPopupWindow, context.anchor);
    }

    @Nullable
    private PinnedAppItem pinnedAppAt(int index) {
        if (pinnedItems == null || index < 0 || index >= pinnedItems.size()) return null;
        PinnedItem item = pinnedItems.get(index);
        return item instanceof PinnedAppItem ? (PinnedAppItem) item : null;
    }

    private void showFolderContextPopup(@NonNull PinnedFolderItem folder, int pinnedIndex, @NonNull View anchor) {
        dismissAppContextPopup();
        dismissFolderPopup();

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(3), dp(3), dp(3), dp(3));
        appContextRows.clear();
        shortcutsRows.clear();
        clearMenuHighlight();
        shortcutsMainRowView = null;
        activeAppMenuContext = null;
        activeAppMenuShortcuts = null;

        TextView header = new TextView(getContext());
        String title = TextUtils.isEmpty(folder.title) ? "Folder" : folder.title;
        header.setText(title);
        header.setTextColor(resolveLauncherTextColor());
        header.setTextSize(12f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(8), dp(4), dp(8), dp(6));
        shell.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int tintBase = folder.tintOverrideEnabled ? (folder.tintColor & 0x00FFFFFF) : (inheritedTintColor & 0x00FFFFFF);
        activeMenuTintBase = tintBase;

        TextView renameRow = addPopupActionRow(shell, "Rename", tintBase, () -> {
            dismissAppContextPopup();
            showRenameFolderDialog(folder);
        });
        appContextRows.add(new MenuActionRow(renameRow, () -> {
            dismissAppContextPopup();
            showRenameFolderDialog(folder);
        }, false));

        TextView chooseAppsRow = addPopupActionRow(shell, "Choose apps", tintBase, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                showFolderContentsEditor(folderIndex, folder);
            }
        });
        appContextRows.add(new MenuActionRow(chooseAppsRow, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                showFolderContentsEditor(folderIndex, folder);
            }
        }, false));

        TextView deleteRow = addPopupActionRow(shell, "Delete", tintBase, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                removePinnedAt(folderIndex);
            }
        });
        appContextRows.add(new MenuActionRow(deleteRow, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                removePinnedAt(folderIndex);
            }
        }, false));

        int rowWidth = normalizePopupRowWidths(appContextRows);
        int contentWidth = constrainPopupHeaderWidth(header, rowWidth);
        constrainPopupRowsWidth(appContextRows, contentWidth);

        appContextPopupWindow = buildPopupWindow(shell, tintBase, true, () -> {
            if (appContextPopupWindow != null && !appContextPopupWindow.isShowing()) {
                appContextPopupWindow = null;
            }
        });
        showPopupAtAnchor(appContextPopupWindow, anchor);
    }

    private void showShortcutsPopup(@NonNull AppMenuContext context, @NonNull List<ShortcutInfo> shortcuts, @Nullable View shortcutsRowAnchor) {
        dismissShortcutsPopup();
        if (shortcuts.isEmpty()) return;

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(3), dp(3), dp(3), dp(3));
        shortcutsRows.clear();

        for (ShortcutInfo info : shortcuts) {
            String label = info.getShortLabel() != null ? info.getShortLabel().toString() : info.getId();
            final TextView[] shortcutRowHolder = new TextView[1];
            TextView shortcutRow = addPopupActionRow(shell, label, activeMenuTintBase, () -> {
                launchShortcut(info, shortcutRowHolder[0]);
                dismissAppContextPopup();
            });
            shortcutRowHolder[0] = shortcutRow;
            shortcutsRows.add(new MenuActionRow(shortcutRow, () -> {
                launchShortcut(info, shortcutRowHolder[0]);
                dismissAppContextPopup();
            }, false));
        }
        normalizePopupRowWidths(shortcutsRows);

        int tintBase = context.sourceFolder != null && context.sourceFolder.tintOverrideEnabled
            ? (context.sourceFolder.tintColor & 0x00FFFFFF)
            : (inheritedTintColor & 0x00FFFFFF);
        shortcutsPopupWindow = buildPopupWindow(shell, tintBase, true, () -> {
            if (shortcutsPopupWindow != null && !shortcutsPopupWindow.isShowing()) {
                shortcutsPopupWindow = null;
            }
        });
        if (shortcutsRowAnchor != null && appContextPopupWindow != null && appContextPopupWindow.isShowing()) {
            showSidePopupAlignedToRow(shortcutsPopupWindow, shortcutsRowAnchor, appContextPopupWindow);
        } else {
            showPopupAtAnchor(shortcutsPopupWindow, context.anchor);
        }
    }

    @NonNull
    private List<ShortcutInfo> queryEntryShortcuts(@NonNull LauncherAppEntry entry) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return new ArrayList<>();
        String cacheKey = entry.appRef.stableId();
        List<ShortcutInfo> cached = shortcutCache.get(cacheKey);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        try {
            LauncherApps launcherApps = (LauncherApps) getContext().getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) return new ArrayList<>();
            LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
            query.setPackage(entry.appRef.packageName);
            if (!TextUtils.isEmpty(entry.appRef.activityName)) {
                String activityName = entry.appRef.activityName;
                if (activityName.startsWith(".")) {
                    activityName = entry.appRef.packageName + activityName;
                }
                query.setActivity(new ComponentName(entry.appRef.packageName, activityName));
            }
            int flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED;
            query.setQueryFlags(flags);
            UserHandle user = Process.myUserHandle();
            List<ShortcutInfo> shortcuts = launcherApps.getShortcuts(query, user);
            List<ShortcutInfo> result = shortcuts == null ? new ArrayList<>() : new ArrayList<>(shortcuts);
            shortcutCache.put(cacheKey, result);
            return new ArrayList<>(result);
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "shortcut query failed for " + entry.appRef.stableId() + ": " + throwable.getMessage());
            return new ArrayList<>();
        }
    }

    public void invalidateShortcutCache(@Nullable String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            shortcutCache.clear();
            return;
        }
        List<String> keysToRemove = new ArrayList<>();
        for (String key : shortcutCache.keySet()) {
            if (key.startsWith(packageName + "/")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            shortcutCache.remove(key);
        }
    }

    private void launchShortcut(@NonNull ShortcutInfo shortcutInfo) {
        launchShortcut(shortcutInfo, activeAppMenuContext != null ? activeAppMenuContext.anchor : null);
    }

    private void launchShortcut(@NonNull ShortcutInfo shortcutInfo, @Nullable View sourceView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;
        boolean touchAnimation = shouldUseTouchLaunchAnimation(sourceView);
        long launchDelay = 0L;
        if (touchAnimation && sourceView != null) {
            launchDelay = APP_LAUNCH_TOUCH_DELAY_MS;
        }
        Runnable launcherRunnable = () -> doLaunchShortcut(shortcutInfo, touchAnimation ? sourceView : null);
        if (launchDelay > 0L && sourceView != null) {
            sourceView.postDelayed(launcherRunnable, launchDelay);
        } else {
            launcherRunnable.run();
        }
    }

    private void doLaunchShortcut(@NonNull ShortcutInfo shortcutInfo, @Nullable View sourceView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;
        try {
            LauncherApps launcherApps = (LauncherApps) getContext().getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) return;
            LaunchAnimationContext animationContext = shouldUseTouchLaunchAnimation(sourceView)
                ? buildLaunchAnimationContext(sourceView)
                : null;
            launcherApps.startShortcut(
                shortcutInfo.getPackage(),
                shortcutInfo.getId(),
                animationContext != null ? animationContext.sourceBounds : null,
                animationContext != null && animationContext.options != null
                    ? animationContext.options
                    : ActivityOptions.makeBasic().toBundle(),
                Process.myUserHandle()
            );
            dismissFolderPopup();
            if (lastTerminalView != null) {
                lastTerminalView.clearInputLine();
            }
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "shortcut launch failed for " + shortcutInfo.getId() + ": " + throwable.getMessage());
        }
    }

    private void openAppInfo(@NonNull LauncherAppEntry entry) {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + entry.appRef.packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "app info open failed for " + entry.appRef.packageName + ": " + throwable.getMessage());
        }
    }

    private void requestUninstall(@NonNull LauncherAppEntry entry) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + entry.appRef.packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Throwable throwable) {
            Log.d(LOG_TAG, "uninstall intent failed for " + entry.appRef.packageName + ": " + throwable.getMessage());
        }
    }

    private void removeFromContextSource(@NonNull AppMenuContext context) {
        if (context.pinnedIndex >= 0) {
            removePinnedAt(context.pinnedIndex);
            return;
        }
        if (context.sourceFolder != null && context.folderEntryRef != null) {
            dismissFolderPopup();
            removeAppFromFolder(context.sourceFolder, context.folderEntryRef);
            persistPinsAndReload();
        }
    }

    private void moveContextEntryToDock(@NonNull AppMenuContext context) {
        if (context.sourceFolder == null || context.folderEntryRef == null) {
            pinEntryToTopLevel(context.entry);
            return;
        }

        dismissFolderPopup();
        AppRef resolved = resolveForSelectionRef(context.folderEntryRef);
        PinnedAppItem folderApp = findFolderApp(context.sourceFolder, resolved);
        int existingPinnedIndex = findPinnedAppIndex(resolved);
        int sourceFolderIndex = findPinnedFolderIndex(context.sourceFolder);
        removeAppFromFolder(context.sourceFolder, resolved);
        if (existingPinnedIndex >= 0) {
            persistPinsAndReload();
            return;
        }

        int survivingFolderIndex = findPinnedFolderIndex(context.sourceFolder);
        int insertionIndex;
        if (survivingFolderIndex >= 0) {
            insertionIndex = survivingFolderIndex + 1;
        } else if (sourceFolderIndex >= 0) {
            insertionIndex = Math.min(sourceFolderIndex + 1, pinnedItems.size());
        } else {
            insertionIndex = pinnedItems.size();
        }
        PinnedIconOverride override = folderApp == null ? null : folderApp.iconOverride;
        pinnedItems.add(clamp(insertionIndex, 0, pinnedItems.size()), new PinnedAppItem(resolved, override));
        persistPinsAndReload();
    }

    private int findPinnedAppIndex(@NonNull AppRef ref) {
        AppRef resolved = resolveForSelectionRef(ref);
        String targetStable = resolved.stableId();
        for (int i = 0; i < pinnedItems.size(); i++) {
            PinnedItem item = pinnedItems.get(i);
            if (!(item instanceof PinnedAppItem)) continue;
            AppRef pinnedRef = resolveForSelectionRef(((PinnedAppItem) item).appRef);
            if (targetStable.equals(pinnedRef.stableId())) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    private PinnedAppItem findFolderApp(@Nullable PinnedFolderItem folder, @NonNull AppRef ref) {
        if (folder == null) return null;
        AppRef resolved = resolveForSelectionRef(ref);
        String targetStable = resolved.stableId();
        for (PinnedAppItem folderApp : folder.apps) {
            if (targetStable.equals(resolveForSelectionRef(folderApp.appRef).stableId())) {
                return folderApp;
            }
        }
        return null;
    }

    private void pinEntryToTopLevel(@NonNull LauncherAppEntry entry) {
        if (findPinnedAppIndex(entry.appRef) >= 0) return;
        pinnedItems.add(new PinnedAppItem(resolveForSelectionRef(entry.appRef)));
        persistPinsAndReload();
    }

    private void changeAppIconForEntry(@NonNull LauncherAppEntry entry) {
        AppRef ref = resolveForSelectionRef(entry.appRef);
        showIconPackPicker(new PinnedAppItem(ref), override -> {
            if (configRepository == null) return;
            configRepository.saveAppIconOverride(ref, override);
            refreshAfterAppIconOverride();
        });
    }

    private void resetAppIcon(@NonNull LauncherAppEntry entry) {
        if (configRepository == null) return;
        configRepository.saveAppIconOverride(resolveForSelectionRef(entry.appRef), null);
        refreshAfterAppIconOverride();
    }

    private void refreshAfterAppIconOverride() {
        dismissAppContextPopup();
        invalidateRenderedIconCaches();
        resolvedRefCache.clear();
        if (appDataProvider != null) appDataProvider.invalidate();
        allApps = new ArrayList<>();
        reloadAllApps();
    }

    private void addAppWideIconRows(
        @NonNull LinearLayout shell,
        @NonNull LauncherAppEntry entry,
        int tintBase
    ) {
        TextView change = addPopupActionRow(shell, "Change app icon", R.drawable.ic_dock_menu_change_icon, false, tintBase, () -> {
            dismissAppContextPopup();
            changeAppIconForEntry(entry);
        });
        appContextRows.add(new MenuActionRow(change, () -> {
            dismissAppContextPopup();
            changeAppIconForEntry(entry);
        }, false));
        addResetAppIconRowIfNeeded(shell, entry, tintBase);
    }

    private void addResetAppIconRowIfNeeded(
        @NonNull LinearLayout shell,
        @NonNull LauncherAppEntry entry,
        int tintBase
    ) {
        if (configRepository == null
            || configRepository.loadAppIconOverride(resolveForSelectionRef(entry.appRef)) == null) return;
        TextView reset = addPopupActionRow(shell, "Reset app icon", R.drawable.ic_dock_menu_reset, false, tintBase, () -> {
            dismissAppContextPopup();
            resetAppIcon(entry);
        });
        appContextRows.add(new MenuActionRow(reset, () -> {
            dismissAppContextPopup();
            resetAppIcon(entry);
        }, false));
    }

    private TextView addPopupActionRow(@NonNull LinearLayout shell, @NonNull String title, int tintBase, @NonNull Runnable action) {
        return addPopupActionRow(shell, title, 0, false, tintBase, action);
    }

    private TextView addPopupActionRow(@NonNull LinearLayout shell, @NonNull String title, int iconRes, boolean chevron, int tintBase, @NonNull Runnable action) {
        TextView actionRow = new TextView(getContext());
        actionRow.setText(title);
        actionRow.setTextColor(resolveLauncherTextColor());
        actionRow.setTextSize(12f);
        actionRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(8), dp(7), dp(8), dp(7));
        actionRow.setClickable(true);
        if (iconRes != 0 || chevron) {
            Drawable leading = iconRes != 0 ? loadMenuIcon(iconRes, dp(16), resolveLauncherTextColor()) : null;
            Drawable trailing = chevron
                ? loadMenuIcon(R.drawable.ic_dock_menu_chevron, dp(13),
                    (resolveLauncherTextColor() & 0x00FFFFFF) | (0x9E << 24))
                : null;
            actionRow.setCompoundDrawablesRelative(leading, null, trailing, null);
            actionRow.setCompoundDrawablePadding(dp(10));
        }
        stylePopupRow(actionRow, false, tintBase);
        actionRow.setOnClickListener(v -> runPopupActionWithFeedback(actionRow, action));
        shell.addView(actionRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return actionRow;
    }

    /** Loads a menu glyph, tints it to {@code color} (alpha respected) and bounds it to {@code sizePx}. */
    @Nullable
    private Drawable loadMenuIcon(int res, int sizePx, int color) {
        Drawable base = ContextCompat.getDrawable(getContext(), res);
        if (base == null) {
            return null;
        }
        Drawable d = DrawableCompat.wrap(base.mutate());
        DrawableCompat.setTint(d, color);
        d.setBounds(0, 0, sizePx, sizePx);
        return d;
    }

    /** Hairline group separator between the OS-actions group and the app-shortcuts group. */
    private void addPopupMenuDivider(@NonNull LinearLayout shell) {
        View divider = new View(getContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.setMargins(dp(8), dp(5), dp(8), dp(4));
        divider.setLayoutParams(lp);
        divider.setBackgroundColor((resolveLauncherTextColor() & 0x00FFFFFF) | (0x24 << 24));
        shell.addView(divider);
    }

    /** A fresh copy of the app icon for the menu header so we don't disturb the row icon's bounds. */
    @Nullable
    private Drawable resolveMenuHeaderIcon(@NonNull LauncherAppEntry entry) {
        Drawable base = entry.icon;
        if (base == null) {
            return null;
        }
        Drawable.ConstantState state = base.getConstantState();
        return state != null ? state.newDrawable().mutate() : base;
    }

    private void runPopupActionWithFeedback(@NonNull TextView actionRow, @NonNull Runnable action) {
        actionRow.animate().cancel();
        actionRow.setPivotX(actionRow.getWidth() * 0.5f);
        actionRow.setPivotY(actionRow.getHeight() * 0.5f);
        actionRow.animate()
            .alpha(0.68f)
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(70L)
            .setInterpolator(new DecelerateInterpolator())
            .withEndAction(() -> {
                if (actionRow.isAttachedToWindow()) {
                    actionRow.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(110L)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                }
                action.run();
            })
            .start();
    }

    private int normalizePopupRowWidths(@NonNull List<MenuActionRow> rows) {
        if (rows.isEmpty()) return 0;
        int maxWidth = 0;
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (MenuActionRow row : rows) {
            if (row.rowView == null) continue;
            row.rowView.measure(unspecified, unspecified);
            maxWidth = Math.max(maxWidth, row.rowView.getMeasuredWidth());
        }
        if (maxWidth <= 0) return 0;
        for (MenuActionRow row : rows) {
            if (row.rowView == null) continue;
            ViewGroup.LayoutParams params = row.rowView.getLayoutParams();
            if (params == null) {
                params = new LinearLayout.LayoutParams(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                params.width = maxWidth;
            }
            row.rowView.setLayoutParams(params);
        }
        return maxWidth;
    }

    private void constrainPopupRowsWidth(@NonNull List<MenuActionRow> rows, int targetWidth) {
        if (targetWidth <= 0) return;
        for (MenuActionRow row : rows) {
            if (row.rowView == null) continue;
            ViewGroup.LayoutParams params = row.rowView.getLayoutParams();
            if (params == null) {
                params = new LinearLayout.LayoutParams(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                params.width = targetWidth;
            }
            row.rowView.setLayoutParams(params);
        }
    }

    private int constrainPopupHeaderWidth(@NonNull TextView header, int targetWidth) {
        if (targetWidth <= 0) return 0;
        String title = header.getText() == null ? "" : header.getText().toString();
        int horizontalPadding = header.getPaddingLeft() + header.getPaddingRight();
        int titleTextWidth = (int) Math.ceil(header.getPaint().measureText(title));
        int mediumNameLimitWidth = (int) Math.ceil(header.getPaint().measureText("MMMMMMMMMMMM"));
        int desiredSingleLineWidth = titleTextWidth + horizontalPadding;
        int boundedWidth = Math.max(targetWidth, Math.min(desiredSingleLineWidth, mediumNameLimitWidth + horizontalPadding));

        header.setSingleLine(false);
        header.setEllipsize(TextUtils.TruncateAt.END);
        header.setMaxLines(desiredSingleLineWidth <= boundedWidth ? 1 : 2);
        ViewGroup.LayoutParams params = header.getLayoutParams();
        if (params == null) {
            params = new LinearLayout.LayoutParams(boundedWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            params.width = boundedWidth;
        }
        header.setLayoutParams(params);
        return boundedWidth;
    }

    private void stylePopupRow(@NonNull TextView row, boolean highlighted, int tintBase) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        if (highlighted) {
            int fill = blendColors((0x8C << 24) | (tintBase & 0x00FFFFFF), 0x66FFFFFF, 0.35f);
            bg.setColor(fill);
            bg.setStroke(dp(1), blendColors(0x99FFFFFF, (0xFF << 24) | (tintBase & 0x00FFFFFF), 0.5f));
            row.setTextColor(resolveLauncherSelectedTextColor());
        } else {
            bg.setColor(0x00000000);
            bg.setStroke(0, 0x00000000);
            row.setTextColor(resolveLauncherTextColor());
        }
        row.setBackground(bg);
    }

    private void clearMenuHighlight() {
        if (activeMenuHighlight != null && activeMenuHighlight.rowView != null) {
            stylePopupRow(activeMenuHighlight.rowView, false, activeMenuTintBase);
        }
        activeMenuHighlight = null;
    }

    private void setMenuHighlight(@Nullable MenuActionRow target) {
        if (activeMenuHighlight == target) return;
        if (activeMenuHighlight != null && activeMenuHighlight.rowView != null) {
            stylePopupRow(activeMenuHighlight.rowView, false, activeMenuTintBase);
        }
        activeMenuHighlight = target;
        if (activeMenuHighlight != null && activeMenuHighlight.rowView != null) {
            stylePopupRow(activeMenuHighlight.rowView, true, activeMenuTintBase);
        }
    }

    private boolean updateMenuHighlightForRaw(float rawX, float rawY, boolean openShortcutsOnFocus, boolean allowProjectedOutside) {
        MenuActionRow target = resolveMenuRowAtRaw(rawX, rawY, openShortcutsOnFocus, allowProjectedOutside);
        boolean keepShortcutsVisible = isRowInList(target, shortcutsRows) || (target != null && target.opensShortcuts);
        if (!keepShortcutsVisible && shortcutsPopupWindow != null && shortcutsPopupWindow.isShowing()) {
            dismissShortcutsPopup();
        }
        setMenuHighlight(target);
        return target != null;
    }

    @Nullable
    private MenuActionRow resolveMenuRowAtRaw(float rawX, float rawY, boolean openShortcutsOnFocus, boolean allowProjectedOutside) {
        MenuActionRow strictShortcut = resolveStrictInsideRow(shortcutsRows, rawX, rawY);
        if (strictShortcut != null) {
            return strictShortcut;
        }
        MenuActionRow strictMain = resolveStrictInsideRow(appContextRows, rawX, rawY);
        if (strictMain != null) {
            maybeOpenShortcutsForFocusedRow(strictMain, openShortcutsOnFocus);
            return strictMain;
        }
        if (!allowProjectedOutside) {
            return null;
        }
        int lowestBottom = Math.max(lowestRowBottom(appContextRows), lowestRowBottom(shortcutsRows));
        if (lowestBottom > 0 && rawY > (lowestBottom + dp(12))) {
            return null;
        }

        boolean hasMain = !appContextRows.isEmpty() && appContextPopupWindow != null && appContextPopupWindow.isShowing();
        boolean hasShortcuts = !shortcutsRows.isEmpty() && shortcutsPopupWindow != null && shortcutsPopupWindow.isShowing();
        if (!hasMain && !hasShortcuts) {
            return null;
        }

        if (hasMain && hasShortcuts) {
            float mainDistance = popupDistance(appContextPopupWindow, rawX, rawY);
            float shortcutsDistance = popupDistance(shortcutsPopupWindow, rawX, rawY);
            if (shortcutsDistance < mainDistance) {
                return resolveNearestRowByY(shortcutsRows, rawY);
            }
            MenuActionRow row = resolveNearestRowByY(appContextRows, rawY);
            maybeOpenShortcutsForFocusedRow(row, openShortcutsOnFocus);
            return row;
        }
        if (hasShortcuts) {
            return resolveNearestRowByY(shortcutsRows, rawY);
        }
        MenuActionRow row = resolveNearestRowByY(appContextRows, rawY);
        maybeOpenShortcutsForFocusedRow(row, openShortcutsOnFocus);
        return row;
    }

    private static boolean isRowInList(@Nullable MenuActionRow row, @NonNull List<MenuActionRow> rows) {
        if (row == null) return false;
        for (MenuActionRow candidate : rows) {
            if (candidate == row) return true;
        }
        return false;
    }

    private void maybeOpenShortcutsForFocusedRow(@Nullable MenuActionRow row, boolean openShortcutsOnFocus) {
        if (row == null || !openShortcutsOnFocus || !row.opensShortcuts || shortcutsPopupWindow != null) {
            return;
        }
        if (activeAppMenuContext != null && activeAppMenuShortcuts != null && !activeAppMenuShortcuts.isEmpty()) {
            showShortcutsPopup(activeAppMenuContext, activeAppMenuShortcuts, shortcutsMainRowView);
        }
    }

    @Nullable
    private MenuActionRow resolveStrictInsideRow(@NonNull List<MenuActionRow> rows, float rawX, float rawY) {
        for (MenuActionRow row : rows) {
            if (row.rowView != null && isRawInsideView(row.rowView, rawX, rawY)) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private static MenuActionRow resolveNearestRowByY(@NonNull List<MenuActionRow> rows, float rawY) {
        MenuActionRow nearest = null;
        float bestDistance = Float.MAX_VALUE;
        Rect rowBounds = new Rect();
        for (MenuActionRow row : rows) {
            if (!getViewScreenRect(row.rowView, rowBounds)) continue;
            if (rawY >= rowBounds.top && rawY <= rowBounds.bottom) {
                return row;
            }
            float distance = rawY < rowBounds.top ? (rowBounds.top - rawY) : (rawY - rowBounds.bottom);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = row;
            }
        }
        return nearest;
    }

    private static int lowestRowBottom(@NonNull List<MenuActionRow> rows) {
        int bottom = -1;
        Rect rowBounds = new Rect();
        for (MenuActionRow row : rows) {
            if (!getViewScreenRect(row.rowView, rowBounds)) continue;
            if (rowBounds.bottom > bottom) bottom = rowBounds.bottom;
        }
        return bottom;
    }

    private float popupDistance(@Nullable PopupWindow popupWindow, float rawX, float rawY) {
        if (popupWindow == null || !popupWindow.isShowing()) {
            return Float.MAX_VALUE;
        }
        View content = popupWindow.getContentView();
        Rect bounds = new Rect();
        if (!getViewScreenRect(content, bounds)) {
            return Float.MAX_VALUE;
        }
        float dx = 0f;
        if (rawX < bounds.left) {
            dx = bounds.left - rawX;
        } else if (rawX > bounds.right) {
            dx = rawX - bounds.right;
        }
        float dy = 0f;
        if (rawY < bounds.top) {
            dy = bounds.top - rawY;
        } else if (rawY > bounds.bottom) {
            dy = rawY - bounds.bottom;
        }
        return (dx * dx) + (dy * dy);
    }

    private static boolean isRawInsideView(@Nullable View view, float rawX, float rawY) {
        Rect bounds = new Rect();
        if (!getViewScreenRect(view, bounds)) {
            return false;
        }
        return rawX >= bounds.left && rawX <= bounds.right && rawY >= bounds.top && rawY <= bounds.bottom;
    }

    private static boolean getViewScreenRect(@Nullable View view, @NonNull Rect outRect) {
        if (view == null || outRect == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        outRect.set(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
        return true;
    }

    private void executeHighlightedMenuActionOrKeepOpen() {
        if (activeMenuHighlight != null && activeMenuHighlight.action != null) {
            activeMenuHighlight.action.run();
        }
    }

    @NonNull
    private PopupWindow buildPopupWindow(@NonNull View content, int tintBase, boolean tightWrap, @Nullable Runnable onDismiss) {
        return buildPopupWindow(content, tintBase, tightWrap, onDismiss, -1);
    }

    @NonNull
    private PopupWindow buildPopupWindow(
        @NonNull View content,
        int tintBase,
        boolean tightWrap,
        @Nullable Runnable onDismiss,
        int requestedWidth
    ) {
        return buildPopupWindow(content, tintBase, tightWrap, onDismiss, requestedWidth, 0, true);
    }

    @NonNull
    private PopupWindow buildPopupWindow(
        @NonNull View content,
        int tintBase,
        boolean tightWrap,
        @Nullable Runnable onDismiss,
        int requestedWidth,
        int minimumOpacityPercent
    ) {
        return buildPopupWindow(content, tintBase, tightWrap, onDismiss, requestedWidth,
            minimumOpacityPercent, true);
    }

    @NonNull
    private PopupWindow buildPopupWindow(
        @NonNull View content,
        int tintBase,
        boolean tightWrap,
        @Nullable Runnable onDismiss,
        int requestedWidth,
        int minimumOpacityPercent,
        boolean showVerticalScrollbar
    ) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int maxWidth = popupMaxWidth(screenW);
        int minWidth = popupMinWidth(screenW, tightWrap);
        int maxHeight = popupMaxHeight(screenH);
        int fixedWidth = requestedWidth > 0 ? clamp(requestedWidth, minWidth, maxWidth) : -1;

        // Measure the full vertical content before imposing the popup viewport. Measuring the
        // content itself with AT_MOST can clamp a LinearLayout before it enters the ScrollView,
        // leaving trailing action rows laid out beyond its reported bounds and therefore clipped.
        content.measure(
            fixedWidth > 0
                ? View.MeasureSpec.makeMeasureSpec(fixedWidth, View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int desiredWidth = fixedWidth > 0
            ? fixedWidth : clamp(content.getMeasuredWidth(), minWidth, maxWidth);
        int desiredHeight = Math.max(dp(36), Math.min(content.getMeasuredHeight(), maxHeight));

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(showVerticalScrollbar);
        scrollView.setScrollbarFadingEnabled(false);
        scrollView.setFadingEdgeLength(dp(18));
        scrollView.setVerticalFadingEdgeEnabled(true);
        scrollView.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout popupRoot = new FrameLayout(getContext());
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(dp(14));
        int alpha = clamp(Math.max(appBarOpacity, minimumOpacityPercent), 0, 100);
        int overlayColor = (((int) (255f * (alpha / 100f))) << 24) | (tintBase & 0x00FFFFFF);
        panelBg.setColor(overlayColor);
        popupRoot.setBackground(panelBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupRoot.setClipToOutline(true);
        }
        if (blurEnabled && blurRadiusDp > 0) {
            RealtimeBlurView blurView = new RealtimeBlurView(getContext());
            blurView.setBlurRadius(Math.max(0f, (float) dp(blurRadiusDp)));
            blurView.setOverlayColor(overlayColor);
            popupRoot.addView(blurView, new FrameLayout.LayoutParams(desiredWidth, desiredHeight));
        }
        popupRoot.addView(scrollView, new FrameLayout.LayoutParams(desiredWidth, desiredHeight));

        PopupWindow popup = new PopupWindow(popupRoot, desiredWidth, desiredHeight, false);
        popup.setFocusable(false);
        popup.setTouchable(true);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setElevation(8f);
        popup.setOnDismissListener(() -> {
            if (onDismiss != null) onDismiss.run();
        });
        return popup;
    }

    private void showPopupAtAnchor(@NonNull PopupWindow popupWindow, @Nullable View anchor) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        Rect visibleFrame = new Rect(0, 0, screenW, screenH);
        getWindowVisibleDisplayFrame(visibleFrame);
        if (visibleFrame.isEmpty()) {
            visibleFrame.set(0, 0, screenW, screenH);
        }
        int popupWidth = popupWindow.getWidth();
        int popupHeight = popupWindow.getHeight();
        int gap = dp(4);
        if (anchor != null) {
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            int anchorCenterX = location[0] + (anchor.getWidth() / 2);
            // Smart anchoring: align the menu to the icon's position — a left-edge icon left-aligns
            // (menu opens toward the right), a right-edge icon right-aligns (opens toward the left),
            // a mid-dock icon centers over the icon. The menu always opens upward.
            int third = screenW / 3;
            int x;
            if (anchorCenterX <= third) {
                x = location[0];
            } else if (anchorCenterX >= (screenW - third)) {
                x = location[0] + anchor.getWidth() - popupWidth;
            } else {
                x = anchorCenterX - (popupWidth / 2);
            }
            int y = location[1] - popupHeight - gap;
            x = clamp(x, visibleFrame.left, Math.max(visibleFrame.left, visibleFrame.right - popupWidth));
            y = clamp(y, visibleFrame.top, Math.max(visibleFrame.top, visibleFrame.bottom - popupHeight));
            popupWindow.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
        } else {
            popupWindow.showAtLocation(this, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, getHeight() + gap);
        }
        View root = popupWindow.getContentView();
        if (root != null) {
            root.setAlpha(0f);
            root.setTranslationY(dp(8));
            root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(150)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

    private int popupMaxWidth(int screenW) {
        return Math.min(screenW - dp(24), Math.min(dp(POPUP_MAX_WIDTH_DP), (int) (screenW * 0.9f)));
    }

    private int popupMinWidth(int screenW, boolean tightWrap) {
        int target = tightWrap ? 0 : POPUP_MIN_WIDTH_DP;
        return Math.min(popupMaxWidth(screenW), dp(target));
    }

    private int popupMaxHeight(int screenH) {
        return Math.min(screenH - dp(80), (int) (screenH * POPUP_MAX_HEIGHT_FACTOR));
    }

    private void showSidePopupAlignedToRow(@NonNull PopupWindow sidePopup, @NonNull View rowAnchor, @NonNull PopupWindow mainPopup) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int[] mainLoc = new int[2];
        int[] rowLoc = new int[2];
        View mainRoot = mainPopup.getContentView();
        if (mainRoot == null) {
            showPopupAtAnchor(sidePopup, rowAnchor);
            return;
        }
        mainRoot.getLocationOnScreen(mainLoc);
        rowAnchor.getLocationOnScreen(rowLoc);
        int gap = dp(4);
        int popupWidth = sidePopup.getWidth();
        int popupHeight = sidePopup.getHeight();
        int preferredRightX = mainLoc[0] + mainPopup.getWidth() + gap;
        int preferredLeftX = mainLoc[0] - popupWidth - gap;
        int x = preferredRightX;
        if (preferredRightX + popupWidth > screenW && preferredLeftX >= 0) {
            x = preferredLeftX;
        }
        x = clamp(x, 0, Math.max(0, screenW - popupWidth));
        int rowCenterY = rowLoc[1] + (rowAnchor.getHeight() / 2);
        int y = clamp(rowCenterY - (popupHeight / 2), 0, Math.max(0, screenH - popupHeight));
        sidePopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
        View root = sidePopup.getContentView();
        if (root != null) {
            root.setAlpha(0f);
            root.setTranslationX(x >= mainLoc[0] ? dp(6) : -dp(6));
            root.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(140L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

    private void dismissAppContextPopup() {
        dismissShortcutsPopup();
        clearMenuHighlight();
        appContextRows.clear();
        activeAppMenuContext = null;
        activeAppMenuShortcuts = null;
        shortcutsMainRowView = null;
        if (appContextPopupWindow != null) {
            final PopupWindow popup = appContextPopupWindow;
            dismissPopupWindowAnimated(popup, () -> {
                if (appContextPopupWindow == popup) {
                    appContextPopupWindow = null;
                }
            });
        }
    }

    private void dismissShortcutsPopup() {
        clearMenuHighlight();
        shortcutsRows.clear();
        if (shortcutsPopupWindow != null) {
            final PopupWindow popup = shortcutsPopupWindow;
            dismissPopupWindowAnimated(popup, () -> {
                if (shortcutsPopupWindow == popup) {
                    shortcutsPopupWindow = null;
                }
            });
        }
    }

    private void showNotificationPopup(
        @NonNull LauncherAppEntry entry,
        @NonNull View anchor
    ) {
        showNotificationPopup(entry, anchor, false);
    }

    private void showNotificationPopup(
        @NonNull LauncherAppEntry entry,
        @NonNull View anchor,
        boolean foregroundRetried
    ) {
        List<StatusBarNotification> notifications =
            LauncherNotificationBadgeStore.getNotificationsForPackage(entry.appRef.packageName);
        if (notifications.isEmpty()) return;
        dismissNotificationPopup();
        dismissAppContextPopup();
        dismissFolderPopup();

        Activity hostActivity = findHostActivity();
        if (!foregroundRetried && hostActivity != null && !hostActivity.hasWindowFocus()) {
            // A translucent call can leave the launcher visible while another task still owns the
            // focused window. Bring this user-selected reply surface forward before attaching its
            // PopupWindow, otherwise Android cannot route an input connection to it.
            Intent foregroundIntent = new Intent(hostActivity, hostActivity.getClass());
            foregroundIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            hostActivity.startActivity(foregroundIntent);
            postDelayed(() -> {
                if (isAttachedToWindow() && anchor.isAttachedToWindow())
                    showNotificationPopup(entry, anchor, true);
            }, 120L);
            return;
        }

        LinearLayout shell = new LinearLayout(getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView header = new TextView(getContext());
        header.setText(notifications.size() == 1
            ? entry.label : entry.label + " · " + notifications.size());
        header.setTextColor(resolveLauncherTextColor());
        header.setTextSize(13f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Drawable icon = iconForDisplay(entry, dp(24));
        if (icon != null && icon.getConstantState() != null) {
            icon = icon.getConstantState().newDrawable(getResources()).mutate();
        }
        if (icon != null) {
            icon.setBounds(0, 0, dp(24), dp(24));
            header.setCompoundDrawablesRelative(icon, null, null, null);
            header.setCompoundDrawablePadding(dp(9));
        }
        shell.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<NotificationReplyTarget> replyTargets = new ArrayList<>();
        for (int i = 0; i < notifications.size(); i++) {
            if (i > 0) addNotificationDivider(shell);
            NotificationReplyTarget target = addNotificationCard(shell, notifications.get(i));
            if (target != null) replyTargets.add(target);
        }

        // Notification content must remain readable even when the dock itself is configured as
        // nearly transparent. Keep the Material surface hue and let blur provide the glass feel.
        int tintBase = resolveLauncherPanelColor() & 0x00FFFFFF;
        final PopupWindow[] holder = new PopupWindow[1];
        int popupWidth = notificationPopupWidth(notifications);
        notificationPopupWindow = buildPopupWindow(shell, tintBase, false, () -> {
            if (notificationPopupWindow == holder[0]) {
                notificationPopupWindow = null;
                notificationPopupPackage = null;
                notificationPopupKeys = Collections.emptySet();
            }
            if (notificationInteractionPopup == holder[0]) {
                notificationInteractionPopup = null;
                if (notificationPopupInteractionListener != null)
                    notificationPopupInteractionListener.onNotificationPopupDismissed();
            }
        }, popupWidth, 88, false);
        holder[0] = notificationPopupWindow;
        // Focusability is a window creation concern on current Android releases. Retrofitting it
        // with PopupWindow.update() can leave a focused EditText whose window is never registered
        // as the IME target.
        notificationPopupWindow.setFocusable(true);
        notificationPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        notificationPopupWindow.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        notificationInteractionPopup = notificationPopupWindow;
        notificationPopupPackage = entry.appRef.packageName;
        Set<String> shownKeys = new HashSet<>();
        for (StatusBarNotification sbn : notifications) {
            shownKeys.add(sbn.getKey() + "@" + sbn.getPostTime());
        }
        notificationPopupKeys = Collections.unmodifiableSet(shownKeys);
        // Keep this window non-focusable for its first layout. The external-input handoff below
        // removes the embedded keyboard before an available inline reply requests IME focus.
        showPopupAtAnchor(notificationPopupWindow, anchor);
        applyNotificationPopupDim(notificationPopupWindow);
        if (notificationPopupInteractionListener != null)
            notificationPopupInteractionListener.onNotificationPopupShown();
        // With several conversations, choosing the first reply field for the user is surprising.
        // Auto-focus only when there is exactly one possible reply destination.
        NotificationReplyTarget finalAutoReplyTarget = shouldAutoOpenNotificationReply(
            replyTargets.size()) ? replyTargets.get(0) : null;
        if (finalAutoReplyTarget != null) {
            post(() -> {
                if (notificationPopupWindow != holder[0] || !holder[0].isShowing()) return;
                showInlineReply(finalAutoReplyTarget.actionHost, finalAutoReplyTarget.action,
                    finalAutoReplyTarget.remoteInputs, finalAutoReplyTarget.freeform,
                    finalAutoReplyTarget.recipient);
            });
        }
    }

    static boolean shouldAutoOpenNotificationReply(int replyTargetCount) {
        return replyTargetCount == 1;
    }

    @Nullable
    private Activity findHostActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        return context instanceof Activity ? (Activity) context : null;
    }

    private void applyNotificationPopupDim(@NonNull PopupWindow popup) {
        View content = popup.getContentView();
        if (content == null) return;
        content.post(() -> {
            if (!popup.isShowing()) return;
            View popupDecor = content.getRootView();
            ViewGroup.LayoutParams rawParams = popupDecor.getLayoutParams();
            if (!(rawParams instanceof WindowManager.LayoutParams)) return;
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) rawParams;
            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.dimAmount = 0.74f;
            WindowManager windowManager =
                (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                try {
                    windowManager.updateViewLayout(popupDecor, params);
                } catch (IllegalArgumentException ignored) {
                    // The popup may have been dismissed between posting and applying the dim.
                }
            }
        });
    }

    private int notificationPopupWidth(@NonNull List<StatusBarNotification> notifications) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int preferredWidth = notifications.size() > 1
            ? screenWidth - dp(32) : screenWidth / 2;
        int requiredActionWidth = 0;
        boolean hasReplyAction = false;
        Paint actionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        actionTextPaint.setTextSize(11.5f * getResources().getDisplayMetrics().scaledDensity);
        actionTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        for (StatusBarNotification sbn : notifications) {
            Notification notification = sbn.getNotification();
            if (notification == null) continue;
            int rowWidth = dp(28);
            int actionCount = 0;
            if (notification.actions != null) {
                for (Notification.Action action : notification.actions) {
                    if (action == null || action.actionIntent == null) continue;
                    String title = TextUtils.isEmpty(action.title) ? "Action" : action.title.toString();
                    rowWidth += notificationActionMeasuredWidth(actionTextPaint, title);
                    if (firstFreeformRemoteInput(action.getRemoteInputs()) != null)
                        hasReplyAction = true;
                    actionCount++;
                }
            }
            if (sbn.isClearable()) {
                rowWidth += notificationActionMeasuredWidth(actionTextPaint, "Dismiss");
                actionCount++;
            }
            if (actionCount > 1) rowWidth += dp(4) * (actionCount - 1);
            requiredActionWidth = Math.max(requiredActionWidth, rowWidth);
        }
        if (hasReplyAction && notifications.size() == 1)
            preferredWidth = Math.max(preferredWidth, (int) (screenWidth * 0.72f));
        return adaptiveNotificationPopupWidth(
            preferredWidth,
            requiredActionWidth,
            popupMinWidth(screenWidth, false),
            popupMaxWidth(screenWidth)
        );
    }

    static int adaptiveNotificationPopupWidth(
        int preferredWidth,
        int requiredActionWidth,
        int minimumWidth,
        int maximumWidth
    ) {
        return clamp(Math.max(preferredWidth, requiredActionWidth), minimumWidth, maximumWidth);
    }

    private int notificationActionMeasuredWidth(@NonNull Paint paint, @NonNull String title) {
        return Math.max(dp(52), (int) Math.ceil(paint.measureText(title)) + dp(20));
    }

    @Nullable
    private NotificationReplyTarget addNotificationCard(
        @NonNull LinearLayout shell,
        @NonNull StatusBarNotification sbn
    ) {
        Notification notification = sbn.getNotification();
        if (notification == null) return null;
        Bundle extras = notification.extras;
        CharSequence title = firstNotificationText(extras,
            Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG);
        CharSequence text = firstNotificationText(extras,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT);

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(2), dp(7), dp(2), dp(2));

        LinearLayout message = new LinearLayout(getContext());
        message.setOrientation(LinearLayout.VERTICAL);
        message.setPadding(0, 0, 0, dp(2));
        if (!TextUtils.isEmpty(title)) {
            TextView titleView = new TextView(getContext());
            titleView.setText(title);
            titleView.setTextColor(resolveLauncherTextColor());
            titleView.setTextSize(14f);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setMaxLines(2);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            message.addView(titleView);
        }
        if (!TextUtils.isEmpty(text)) {
            TextView textView = new TextView(getContext());
            textView.setText(text);
            textView.setTextColor(resolveLauncherSubtleTextColor());
            textView.setTextSize(12.5f);
            textView.setMaxLines(4);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textLp.topMargin = dp(3);
            message.addView(textView, textLp);
        }
        if (notification.contentIntent != null) {
            message.setClickable(true);
            message.setOnClickListener(v -> {
                sendNotificationIntent(notification.contentIntent, null, null);
                if ((notification.flags & Notification.FLAG_AUTO_CANCEL) != 0) {
                    LauncherCtlNotificationListener.dismissNotification(sbn.getKey());
                }
                dismissNotificationPopup();
            });
        }
        card.addView(message, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout actionHost = new FrameLayout(getContext());
        LinearLayout actionRow = new LinearLayout(getContext());
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        HorizontalScrollView actionScroller = new HorizontalScrollView(getContext());
        actionScroller.setFillViewport(false);
        actionScroller.setHorizontalScrollBarEnabled(false);
        actionScroller.setOverScrollMode(OVER_SCROLL_NEVER);
        actionScroller.addView(actionRow, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionHost.addView(actionScroller, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int actionIndex = 0;
        NotificationReplyTarget replyTarget = null;
        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                if (action == null || action.actionIntent == null) continue;
                String actionTitle = TextUtils.isEmpty(action.title) ? "Action" : action.title.toString();
                Button actionButton = notificationActionButton(actionTitle);
                RemoteInput[] remoteInputs = action.getRemoteInputs();
                RemoteInput freeform = firstFreeformRemoteInput(remoteInputs);
                if (freeform != null) {
                    if (replyTarget == null) {
                        replyTarget = new NotificationReplyTarget(
                            actionHost, action, remoteInputs, freeform, title);
                    }
                    actionButton.setOnClickListener(v ->
                        showInlineReply(actionHost, action, remoteInputs, freeform, title));
                } else {
                    actionButton.setOnClickListener(v ->
                        sendNotificationIntent(action.actionIntent, null, null));
                }
                actionRow.addView(actionButton, notificationActionLayoutParams(actionIndex++ > 0));
            }
        }

        if (sbn.isClearable()) {
            Button dismiss = notificationActionButton("Dismiss");
            dismiss.setOnClickListener(v -> {
                LauncherCtlNotificationListener.dismissNotification(sbn.getKey());
                card.setVisibility(View.GONE);
                postDelayed(() -> {
                    if (LauncherNotificationBadgeStore.getNotificationsForPackage(sbn.getPackageName()).isEmpty()) {
                        dismissNotificationPopup();
                    }
                }, 180L);
            });
            actionRow.addView(dismiss, notificationActionLayoutParams(actionIndex++ > 0));
        }
        if (actionIndex > 0) {
            LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hostLp.topMargin = dp(3);
            card.addView(actionHost, hostLp);
        }
        shell.addView(card, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return replyTarget;
    }

    private static final class NotificationReplyTarget {
        @NonNull final FrameLayout actionHost;
        @NonNull final Notification.Action action;
        @NonNull final RemoteInput[] remoteInputs;
        @NonNull final RemoteInput freeform;
        @Nullable final CharSequence recipient;

        NotificationReplyTarget(@NonNull FrameLayout actionHost,
            @NonNull Notification.Action action, @NonNull RemoteInput[] remoteInputs,
            @NonNull RemoteInput freeform, @Nullable CharSequence recipient) {
            this.actionHost = actionHost;
            this.action = action;
            this.remoteInputs = remoteInputs;
            this.freeform = freeform;
            this.recipient = recipient;
        }
    }

    private void showInlineReply(
        @NonNull FrameLayout actionHost,
        @NonNull Notification.Action action,
        @NonNull RemoteInput[] remoteInputs,
        @NonNull RemoteInput freeform,
        @Nullable CharSequence recipient
    ) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(3), dp(4), dp(3));
        GradientDrawable composerBg = new GradientDrawable();
        composerBg.setCornerRadius(dp(12));
        composerBg.setColor(withAlphaComponent(resolveLauncherPanelColor(), 0xF4));
        composerBg.setStroke(Math.max(1, dp(1)),
            withAlphaComponent(resolveLauncherOutlineColor(), 0x70));
        row.setBackground(composerBg);

        EditText reply = new EditText(getContext());
        reply.setSingleLine(true);
        if (!TextUtils.isEmpty(recipient)) {
            reply.setHint("Reply to " + recipient);
        } else {
            reply.setHint(TextUtils.isEmpty(freeform.getLabel()) ? "Reply" : freeform.getLabel());
        }
        reply.setTextColor(resolveLauncherTextColor());
        reply.setHintTextColor(resolveLauncherSubtleTextColor());
        reply.setTextSize(13f);
        reply.setPadding(dp(10), 0, dp(8), 0);
        reply.setBackgroundColor(Color.TRANSPARENT);
        reply.setImeOptions(EditorInfo.IME_ACTION_SEND);
        reply.setOnTouchListener((view, event) -> {
            if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                enableNotificationReplyInput(reply);
            }
            return false;
        });
        row.addView(reply, new LinearLayout.LayoutParams(0, dp(40), 1f));

        Button send = notificationSendButton();
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
            dp(62), dp(36));
        row.addView(send, sendLp);
        Runnable sendReply = () -> {
            String value = reply.getText().toString().trim();
            if (TextUtils.isEmpty(value)) return;
            if (sendNotificationIntent(action.actionIntent, remoteInputs, value))
                dismissNotificationPopup();
        };
        send.setOnClickListener(v -> sendReply.run());
        reply.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            sendReply.run();
            return true;
        });
        send.setEnabled(false);
        send.setAlpha(0.45f);
        reply.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean enabled = !TextUtils.isEmpty(s == null ? null : s.toString().trim());
                send.setEnabled(enabled);
                send.setAlpha(enabled ? 1f : 0.45f);
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        actionHost.removeAllViews();
        actionHost.addView(row, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        enableNotificationReplyInput(reply);
    }

    @NonNull
    private Button notificationSendButton() {
        Button send = new Button(getContext());
        send.setText("Send");
        send.setAllCaps(false);
        send.setSingleLine(true);
        send.setTextSize(11.5f);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setTextColor(MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            resolveLauncherTextColor()));
        send.setMinWidth(0);
        send.setMinimumWidth(0);
        send.setMinHeight(0);
        send.setMinimumHeight(0);
        send.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(9));
        background.setColor(MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorPrimaryContainer,
            resolveLauncherPanelColor()));
        send.setBackground(background);
        return send;
    }

    private void enableNotificationReplyInput(@NonNull EditText reply) {
        PopupWindow popup = notificationPopupWindow;
        if (popup == null || !popup.isShowing()) return;
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        popup.update();
        applyNotificationPopupDim(popup);
        reply.post(() -> requestNotificationReplyIme(reply, popup, 0));
    }

    private void requestNotificationReplyIme(
        @NonNull EditText reply,
        @NonNull PopupWindow popup,
        int attempt
    ) {
        if (notificationPopupWindow != popup || !popup.isShowing()) return;
        reply.requestFocus();
        InputMethodManager inputMethodManager =
            (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null)
            inputMethodManager.showSoftInput(reply, InputMethodManager.SHOW_IMPLICIT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && reply.getWindowInsetsController() != null) {
            reply.getWindowInsetsController().show(WindowInsets.Type.ime());
        }
        // Bringing the launcher above a translucent call or overlay is asynchronous. Retry only
        // while this reply field has not yet acquired window focus.
        if (!reply.hasWindowFocus() && attempt < 4) {
            reply.postDelayed(() -> requestNotificationReplyIme(reply, popup, attempt + 1), 90L);
        }
    }

    @Nullable
    private static CharSequence firstNotificationText(@Nullable Bundle extras, @NonNull String... keys) {
        if (extras == null) return null;
        for (String key : keys) {
            CharSequence value = extras.getCharSequence(key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return null;
    }

    @Nullable
    private static RemoteInput firstFreeformRemoteInput(@Nullable RemoteInput[] inputs) {
        if (inputs == null) return null;
        for (RemoteInput input : inputs) {
            if (input != null && input.getAllowFreeFormInput()) return input;
        }
        return null;
    }

    private boolean sendNotificationIntent(
        @NonNull PendingIntent pendingIntent,
        @Nullable RemoteInput[] remoteInputs,
        @Nullable CharSequence reply
    ) {
        try {
            Intent fillIn = null;
            if (remoteInputs != null && reply != null) {
                fillIn = new Intent();
                Bundle results = new Bundle();
                for (RemoteInput input : remoteInputs) {
                    if (input != null && input.getAllowFreeFormInput()) {
                        results.putCharSequence(input.getResultKey(), reply);
                    }
                }
                RemoteInput.addResultsToIntent(remoteInputs, fillIn, results);
            }
            pendingIntent.send(getContext(), 0, fillIn);
            return true;
        } catch (PendingIntent.CanceledException exception) {
            Log.d(LOG_TAG, "Notification action is no longer available: " + exception.getMessage());
            return false;
        }
    }

    @NonNull
    private Button notificationActionButton(@NonNull String title) {
        Button button = new Button(getContext());
        button.setText(title);
        button.setTextColor(resolveLauncherTextColor());
        button.setTextSize(11.5f);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        TypedValue selectableBackground = new TypedValue();
        if (getContext().getTheme().resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless, selectableBackground, true)
            && selectableBackground.resourceId != 0) {
            button.setBackgroundResource(selectableBackground.resourceId);
        } else {
            button.setBackgroundColor(0x00000000);
        }
        return button;
    }

    @NonNull
    private LinearLayout.LayoutParams notificationActionLayoutParams(boolean withStartGap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        if (withStartGap) lp.leftMargin = dp(4);
        return lp;
    }

    private void addNotificationDivider(@NonNull LinearLayout shell) {
        View divider = new View(getContext());
        divider.setBackgroundColor(withAlphaComponent(resolveLauncherOutlineColor(), 0x42));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.setMargins(0, dp(5), 0, 0);
        shell.addView(divider, lp);
    }

    private void dismissNotificationPopup() {
        if (notificationPopupWindow == null) return;
        PopupWindow popup = notificationPopupWindow;
        notificationPopupWindow = null;
        notificationPopupPackage = null;
        notificationPopupKeys = Collections.emptySet();
        dismissPopupWindowAnimated(popup, null);
    }

    private void dismissIconPickerPopup() {
        if (iconPickerDialog != null) {
            final Dialog dialog = iconPickerDialog;
            iconPickerDialog = null;
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    private void dismissPopupWindowAnimated(@NonNull PopupWindow popup, @Nullable Runnable onDone) {
        View content = popup.getContentView();
        if (content != null && popup.isShowing()) {
            content.animate()
                .alpha(0f)
                .translationY(dp(6))
                .setDuration(110)
                .withEndAction(() -> {
                    try {
                        popup.dismiss();
                    } catch (Exception ignored) {
                    }
                    if (onDone != null) onDone.run();
                })
                .start();
        } else {
            try {
                popup.dismiss();
            } catch (Exception ignored) {
            }
            if (onDone != null) onDone.run();
        }
    }

    private int findPinnedFolderIndex(@NonNull PinnedFolderItem folder) {
        for (int i = 0; i < pinnedItems.size(); i++) {
            PinnedItem item = pinnedItems.get(i);
            if (!(item instanceof PinnedFolderItem)) continue;
            if (((PinnedFolderItem) item).id.equals(folder.id)) return i;
        }
        return -1;
    }

    private void updateFolderDragInsertionPreview(int targetIndex) {
        if (targetIndex < 0) {
            clearFolderDragInsertionPreview();
            return;
        }
        int maxSlots = Math.max(1, maxButtonCount);
        int clamped = clamp(targetIndex, 0, maxSlots - 1);
        if (folderDragHoverIndex == clamped) return;
        folderDragHoverIndex = clamped;
        applyBarDragTransforms();
    }

    private void clearFolderDragInsertionPreview() {
        if (folderDragHoverIndex < 0) return;
        folderDragHoverIndex = -1;
        applyBarDragTransforms();
    }

    private void applyBarDragTransforms() {
        int maxSlots = Math.max(1, maxButtonCount);
        float insertShift = (folderDragHoverIndex >= 0) ? Math.max(dp(10), getWidth() / (float) Math.max(4, maxSlots) * 0.35f) : 0f;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null) continue;
            float tx = 0f;
            if (folderDragHoverIndex >= 0 && i >= folderDragHoverIndex) {
                tx += insertShift;
            }
            child.animate().translationX(tx).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private String[] appLabels(List<LauncherAppEntry> entries) {
        List<String> displayLabels = buildDisplayLabels(entries);
        return displayLabels.toArray(new String[0]);
    }

    private static final class MenuActionRow {
        @NonNull final TextView rowView;
        @NonNull final Runnable action;
        final boolean opensShortcuts;

        MenuActionRow(@NonNull TextView rowView, @NonNull Runnable action, boolean opensShortcuts) {
            this.rowView = rowView;
            this.action = action;
            this.opensShortcuts = opensShortcuts;
        }
    }

    private static final class AppMenuContext {
        final LauncherAppEntry entry;
        final View anchor;
        final int pinnedIndex;
        @Nullable final PinnedFolderItem sourceFolder;
        @Nullable final AppRef folderEntryRef;

        AppMenuContext(
            @NonNull LauncherAppEntry entry,
            @NonNull View anchor,
            int pinnedIndex,
            @Nullable PinnedFolderItem sourceFolder,
            @Nullable AppRef folderEntryRef
        ) {
            this.entry = entry;
            this.anchor = anchor;
            this.pinnedIndex = pinnedIndex;
            this.sourceFolder = sourceFolder;
            this.folderEntryRef = folderEntryRef;
        }
    }

    private static final class LongPressPickupState {
        final View sourceView;
        final int pinnedIndex;
        final float downRawX;
        final float downRawY;
        boolean menuShown = false;
        boolean dragStarted = false;
        long menuShownAtMs = 0L;
        boolean definitiveYMovement = false;
        boolean selectionArmed = false;
        boolean leftAnchor = false;
        boolean notificationSwipeStarted = false;

        LongPressPickupState(@NonNull View sourceView, int pinnedIndex, float downRawX, float downRawY) {
            this.sourceView = sourceView;
            this.pinnedIndex = pinnedIndex;
            this.downRawX = downRawX;
            this.downRawY = downRawY;
        }
    }

    @NonNull
    private static List<String> buildDisplayLabels(@NonNull List<LauncherAppEntry> apps) {
        Map<String, Integer> counts = new HashMap<>();
        for (LauncherAppEntry app : apps) {
            String key = normalizeLookupValue(app.label);
            counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
        }

        List<String> labels = new ArrayList<>(apps.size());
        for (LauncherAppEntry app : apps) {
            String label = app.label == null ? "" : app.label.trim();
            if (label.isEmpty()) {
                labels.add(app.appRef.packageName);
                continue;
            }
            String key = normalizeLookupValue(label);
            if (counts.get(key) != null && counts.get(key) > 1) {
                labels.add(label + " (" + app.appRef.packageName + ")");
            } else {
                labels.add(label);
            }
        }
        return labels;
    }

    @NonNull
    private static String buildSearchableAppText(@NonNull LauncherAppEntry app) {
        String label = app.label == null ? "" : app.label;
        String packageName = app.appRef.packageName == null ? "" : app.appRef.packageName;
        String activityName = app.appRef.activityName == null ? "" : app.appRef.activityName;
        return label + " " + packageName + " " + activityName;
    }

    private static boolean matchesLookupQuery(@NonNull String query, @NonNull String haystack) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) return true;
        String lowerQuery = trimmed.toLowerCase(Locale.ROOT);
        String lowerHaystack = haystack.toLowerCase(Locale.ROOT);
        if (lowerHaystack.contains(lowerQuery)) {
            return true;
        }
        String normalizedQuery = normalizeLookupValue(trimmed);
        return !normalizedQuery.isEmpty() && normalizeLookupValue(haystack).contains(normalizedQuery);
    }

    @NonNull
    private static String normalizeLookupValue(@Nullable String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder normalized = new StringBuilder(value.length());
        boolean previousWasSpace = true;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
                previousWasSpace = false;
            } else if (!previousWasSpace) {
                normalized.append(' ');
                previousWasSpace = true;
            }
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }

    @NonNull
    private List<PinnedAppItem> collectSelectedFolderApps(
        @NonNull PinnedFolderItem folder,
        @NonNull List<LauncherAppEntry> source,
        @NonNull Set<String> selectedIds
    ) {
        Map<String, PinnedIconOverride> existingOverrides = folderIconOverridesByStableId(folder);
        List<PinnedAppItem> selectedApps = new ArrayList<>();
        for (LauncherAppEntry app : source) {
            if (selectedIds.contains(app.appRef.stableId())) {
                AppRef ref = resolveForSelectionRef(app.appRef);
                selectedApps.add(new PinnedAppItem(ref, existingOverrides.get(ref.stableId())));
            }
        }
        return normalizePinnedAppItems(selectedApps);
    }

    @NonNull
    private List<PinnedAppItem> normalizePinnedAppItems(@NonNull List<PinnedAppItem> apps) {
        List<PinnedAppItem> normalized = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (PinnedAppItem app : apps) {
            AppRef resolved = resolveForSelectionRef(app.appRef);
            if (TextUtils.isEmpty(resolved.packageName)) {
                continue;
            }
            if (seen.add(resolved.stableId())) {
                normalized.add(new PinnedAppItem(resolved, app.iconOverride));
            }
        }
        return normalized;
    }

    @NonNull
    private static Map<String, PinnedIconOverride> folderIconOverridesByStableId(@NonNull PinnedFolderItem folder) {
        Map<String, PinnedIconOverride> overrides = new HashMap<>();
        for (PinnedAppItem folderApp : folder.apps) {
            if (folderApp.iconOverride != null && folderApp.iconOverride.isValid()) {
                overrides.put(folderApp.appRef.stableId(), folderApp.iconOverride);
            }
        }
        return overrides;
    }

    private void applyNormalizedFolderSelection(int folderIndex, @NonNull PinnedFolderItem folder, @NonNull List<PinnedAppItem> selectedApps) {
        int resolvedIndex = folderIndex >= 0 ? folderIndex : findPinnedFolderIndex(folder);
        if (resolvedIndex < 0 || resolvedIndex >= pinnedItems.size()) {
            return;
        }
        if (selectedApps.isEmpty()) {
            pinnedItems.remove(resolvedIndex);
        } else if (selectedApps.size() == 1) {
            pinnedItems.set(resolvedIndex, selectedApps.get(0));
        } else {
            folder.apps.clear();
            folder.apps.addAll(selectedApps);
            pinnedItems.set(resolvedIndex, folder);
        }
        persistPinsAndReload();
    }

    private boolean addPinnedAppToFolderIfMissing(@NonNull PinnedFolderItem folder, @NonNull PinnedAppItem app) {
        AppRef resolved = resolveForSelectionRef(app.appRef);
        for (PinnedAppItem existing : folder.apps) {
            if (resolveForSelectionRef(existing.appRef).stableId().equals(resolved.stableId())) {
                return false;
            }
        }
        folder.apps.add(new PinnedAppItem(resolved, app.iconOverride));
        return true;
    }

    private static void syncFolderChecks(@NonNull ListView listView, @NonNull List<LauncherAppEntry> apps, @NonNull Set<String> selectedIds) {
        for (int i = 0; i < apps.size(); i++) {
            listView.setItemChecked(i, selectedIds.contains(apps.get(i).appRef.stableId()));
        }
    }

    private boolean startPinnedDrag(@NonNull View view, int sourceIndex) {
        ClipData clip = ClipData.newPlainText("pinned-item", Integer.toString(sourceIndex));
        PinnedDragState dragState = new PinnedDragState(sourceIndex);
        View.DragShadowBuilder shadow = createRaisedDragShadow(view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clip, shadow, dragState, 0);
        } else {
            view.startDrag(clip, shadow, dragState, 0);
        }
        return true;
    }

    @NonNull
    private View.DragShadowBuilder createRaisedDragShadow(@NonNull View view) {
        return new View.DragShadowBuilder(view) {
            @Override
            public void onProvideShadowMetrics(@NonNull Point outShadowSize, @NonNull Point outShadowTouchPoint) {
                super.onProvideShadowMetrics(outShadowSize, outShadowTouchPoint);
                if (outShadowSize.y > 1) {
                    outShadowTouchPoint.y = Math.min(outShadowSize.y - 1, Math.round(outShadowSize.y * 0.86f));
                }
            }
        };
    }

    private boolean handlePinnedBarDragEvent(@NonNull View targetView, @NonNull DragEvent event) {
        Object localState = event.getLocalState();
        boolean pinnedDrag = localState instanceof PinnedDragState;
        if (!pinnedDrag) return false;

        int slotCount = Math.max(1, maxButtonCount);
        float width = Math.max(1f, targetView.getWidth());
        float x = Math.max(0f, Math.min(width, event.getX()));
        float slotWidth = width / slotCount;
        float contentX = x;
        int hoveredSlot = clamp((int) (contentX / Math.max(1f, slotWidth)), 0, slotCount - 1);
        float slotStartX = hoveredSlot * slotWidth;
        float dropXRatio = slotWidth <= 0f ? 0.5f : Math.max(0f, Math.min(1f, (contentX - slotStartX) / slotWidth));

        int pageOffset = Math.max(0, pinnedPageIndex) * Math.max(1, pinnedItemsPerPage);
        int targetIndex = clamp(pageOffset + hoveredSlot, 0, pinnedItems == null ? 0 : pinnedItems.size());
        PinnedItem targetItem = null;
        if (pinnedItems != null && targetIndex >= 0 && targetIndex < pinnedItems.size()) {
            targetItem = pinnedItems.get(targetIndex);
        }

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                updateFolderDragInsertionPreview(hoveredSlot);
                return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                updateFolderDragInsertionPreview(hoveredSlot);
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                targetView.setAlpha(0.92f);
                updateFolderDragInsertionPreview(hoveredSlot);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                targetView.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP:
                targetView.setAlpha(1f);
                clearFolderDragInsertionPreview();
                return applyPinnedDrop((PinnedDragState) localState, targetIndex, targetItem, dropXRatio);
            case DragEvent.ACTION_DRAG_ENDED:
                targetView.setAlpha(1f);
                clearFolderDragInsertionPreview();
                return true;
            default:
                return false;
        }
    }

    private boolean applyPinnedDrop(@NonNull PinnedDragState dragState, int targetIndex, @Nullable PinnedItem targetItem, float dropXRatio) {
        if (dragState.sourceIndex < 0 || dragState.sourceIndex >= pinnedItems.size()) return false;
        if (targetIndex < 0 || targetIndex > pinnedItems.size()) return false;

        PinnedItem sourceItem = pinnedItems.get(dragState.sourceIndex);
        boolean sourceIsApp = sourceItem instanceof PinnedAppItem;
        PinnedAppItem sourceApp = sourceIsApp ? (PinnedAppItem) sourceItem : null;
        AppRef sourceRef = sourceApp == null ? null : resolveForSelectionRef(sourceApp.appRef);

        if (sourceIsApp && targetItem instanceof PinnedFolderItem) {
            PinnedFolderItem folder = (PinnedFolderItem) targetItem;
            if (sourceApp != null) addPinnedAppToFolderIfMissing(folder, sourceApp);
            pinnedItems.remove(dragState.sourceIndex);
            persistPinsAndReload();
            return true;
        }

        if (sourceIsApp && targetItem instanceof PinnedAppItem && shouldCreateFolderOnDrop(dropXRatio)) {
            PinnedAppItem targetApp = (PinnedAppItem) targetItem;
            AppRef targetRef = resolveForSelectionRef(targetApp.appRef);
            int source = dragState.sourceIndex;
            int target = targetIndex;
            if (source < target) {
                pinnedItems.remove(source);
                target = target - 1;
            } else {
                pinnedItems.remove(source);
            }
            target = clamp(target, 0, Math.max(0, pinnedItems.size() - 1));
            PinnedFolderItem folder = new PinnedFolderItem(UUID.randomUUID().toString(), "Folder");
            folder.apps.add(new PinnedAppItem(targetRef, targetApp.iconOverride));
            if (sourceApp != null && sourceRef != null && !targetRef.stableId().equals(sourceRef.stableId())) {
                folder.apps.add(new PinnedAppItem(sourceRef, sourceApp.iconOverride));
            }
            pinnedItems.set(target, folder);
            persistPinsAndReload();
            return true;
        }

        int insertionIndex = computeInsertionIndex(targetIndex, targetItem, dropXRatio);
        return movePinnedItem(dragState.sourceIndex, insertionIndex);
    }

    private int computeInsertionIndex(int targetIndex, @Nullable PinnedItem targetItem, float dropXRatio) {
        int insertionIndex = targetIndex;
        if (targetItem != null && dropXRatio >= 0.5f) {
            insertionIndex = targetIndex + 1;
        }
        return clamp(insertionIndex, 0, Math.max(0, pinnedItems.size()));
    }

    private boolean shouldCreateFolderOnDrop(float dropXRatio) {
        return dropXRatio >= 0.28f && dropXRatio <= 0.72f;
    }

    private boolean movePinnedItem(int fromIndex, int insertionIndex) {
        if (fromIndex < 0 || fromIndex >= pinnedItems.size()) return false;
        int boundedInsertion = clamp(insertionIndex, 0, pinnedItems.size());
        PinnedItem moved = pinnedItems.remove(fromIndex);
        if (fromIndex < boundedInsertion) boundedInsertion--;
        boundedInsertion = clamp(boundedInsertion, 0, pinnedItems.size());
        pinnedItems.add(boundedInsertion, moved);
        if (fromIndex == boundedInsertion) {
            return false;
        }
        persistPinsAndReload();
        return true;
    }

    private void removeAppFromFolder(@NonNull PinnedFolderItem folder, @NonNull AppRef appRef) {
        AppRef resolved = resolveForSelectionRef(appRef);
        for (int i = folder.apps.size() - 1; i >= 0; i--) {
            if (resolveForSelectionRef(folder.apps.get(i).appRef).stableId().equals(resolved.stableId())) {
                folder.apps.remove(i);
            }
        }
        if (folder.apps.isEmpty()) {
            for (int i = 0; i < pinnedItems.size(); i++) {
                PinnedItem item = pinnedItems.get(i);
                if (item instanceof PinnedFolderItem) {
                    if (((PinnedFolderItem) item).id.equals(folder.id)) {
                        pinnedItems.remove(i);
                        break;
                    }
                }
            }
        } else if (folder.apps.size() == 1) {
            PinnedAppItem surviving = folder.apps.get(0);
            for (int i = 0; i < pinnedItems.size(); i++) {
                PinnedItem item = pinnedItems.get(i);
                if (item instanceof PinnedFolderItem && ((PinnedFolderItem) item).id.equals(folder.id)) {
                    pinnedItems.set(i, new PinnedAppItem(resolveForSelectionRef(surviving.appRef), surviving.iconOverride));
                    break;
                }
            }
        }
    }

    @NonNull
    private static PinnedItem clonePinnedItem(@NonNull PinnedItem item) {
        if (item instanceof PinnedAppItem) {
            PinnedAppItem appItem = (PinnedAppItem) item;
            AppRef ref = appItem.appRef;
            return new PinnedAppItem(ref.copy(), appItem.iconOverride);
        }
        if (item instanceof PinnedFolderItem) {
            PinnedFolderItem folder = (PinnedFolderItem) item;
            PinnedFolderItem copy = new PinnedFolderItem(folder.id, folder.title);
            copy.rows = folder.rows;
            copy.cols = folder.cols;
            copy.tintOverrideEnabled = folder.tintOverrideEnabled;
            copy.tintColor = folder.tintColor;
            for (PinnedAppItem folderApp : folder.apps) {
                AppRef ref = folderApp.appRef;
                copy.apps.add(new PinnedAppItem(ref.copy(), folderApp.iconOverride));
            }
            return copy;
        }
        return item;
    }

    @Nullable
    private String resolveForSelectionId(@NonNull AppRef ref) {
        AppRef resolved = resolveForSelectionRef(ref);
        return resolved == null ? null : resolved.stableId();
    }

    @NonNull
    private AppRef resolveForSelectionRef(@NonNull AppRef ref) {
        if (!TextUtils.isEmpty(ref.activityName)) return ref;
        LauncherAppEntry resolved = resolveRef(ref);
        return resolved != null ? resolved.appRef : ref;
    }

    private static final class PinnedDragState {
        final int sourceIndex;

        PinnedDragState(int sourceIndex) {
            this.sourceIndex = sourceIndex;
        }
    }

    private LinearLayout buildStepperRow(@NonNull String label, @NonNull int[] valueRef, int min, int max) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView title = new TextView(getContext());
        title.setText(label);
        title.setTextColor(resolveLauncherTextColor());
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton minus = new ImageButton(getContext());
        minus.setImageResource(android.R.drawable.ic_media_previous);
        styleIconButton(minus, dp(2));
        row.addView(minus, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView valueText = new TextView(getContext());
        valueText.setTextColor(resolveLauncherTextColor());
        valueText.setTypeface(Typeface.DEFAULT_BOLD);
        valueText.setText(Integer.toString(valueRef[0]));
        valueText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(32), ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.setMargins(dp(6), 0, dp(6), 0);
        row.addView(valueText, valueParams);

        ImageButton plus = new ImageButton(getContext());
        plus.setImageResource(android.R.drawable.ic_input_add);
        styleIconButton(plus, dp(2));
        row.addView(plus, new LinearLayout.LayoutParams(dp(24), dp(24)));

        minus.setOnClickListener(v -> {
            valueRef[0] = clamp(valueRef[0] - 1, min, max);
            valueText.setText(Integer.toString(valueRef[0]));
        });
        plus.setOnClickListener(v -> {
            valueRef[0] = clamp(valueRef[0] + 1, min, max);
            valueText.setText(Integer.toString(valueRef[0]));
        });
        return row;
    }

    private static int parseInt(CharSequence value, int fallback) {
        try {
            return Integer.parseInt(stringValue(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String stringValue(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    @Nullable
    private static Integer parseColor(String value) {
        try {
            String clean = value.startsWith("#") ? value.substring(1) : value;
            if (clean.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(clean, 16));
            }
            if (clean.length() == 8) {
                return (int) Long.parseLong(clean, 16);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float computeAzAnchorPosition(char letter, int slots) {
        if (slots <= 1) return 0f;
        Set<Character> available = getAvailableAzLetters();
        List<Character> ordered = new ArrayList<>();
        for (char c : AZ_ORDER) {
            if (available.contains(c)) {
                ordered.add(c);
            }
        }
        if (ordered.isEmpty()) return (slots - 1) / 2f;
        char target = Character.toUpperCase(letter);
        int index = ordered.indexOf(target);
        if (index < 0) index = 0;
        if (ordered.size() == 1) return (slots - 1) / 2f;
        float normalized = (float) index / (float) (ordered.size() - 1);
        return Math.max(0f, Math.min(slots - 1, normalized * (slots - 1)));
    }

    private void animatePageSwitch(int pageDelta, float velocityPxPerSec) {
        if (pageSwitchAnimating) return;
        int totalPages = getPinnedPagesCount();
        if (totalPages <= 1) return;
        int targetPage = wrapPageIndex(pinnedPageIndex + pageDelta, totalPages);
        if (targetPage == pinnedPageIndex) return;

        pageSwitchAnimating = true;
        swipePagePosition = targetPage;
        notifyOverflowPagePositionChanged();
        final int direction = pageDelta > 0 ? 1 : -1;
        final long duration = computePinnedPageAnimDuration(velocityPxPerSec);
        Runnable updateContent = () -> {
            pinnedPageIndex = targetPage;
            reloadWithInput("", lastTerminalView);
        };
        if (swipePageDragging && swipePreviewPageIndex == targetPage) {
            runSwipePreviewPageSwitch(direction, duration, updateContent, null);
        } else {
            final float travel = Math.max(dp(24), getWidth() * 0.24f);
            runUnifiedAppsBarPageSwitch(direction, travel, duration, updateContent, null);
        }
    }

    private void animateAzPageSwitch(int pageDelta, float velocityPxPerSec) {
        if (pageSwitchAnimating) return;
        int totalPages = getAzPagesCount();
        if (totalPages <= 1) return;
        int targetPage = wrapAzPageIndex(activeAzPageIndex + pageDelta, totalPages);

        pageSwitchAnimating = true;
        swipePagePosition = targetPage;
        notifyOverflowPagePositionChanged();
        final int direction = pageDelta > 0 ? 1 : -1;
        final long duration = computePinnedPageAnimDuration(velocityPxPerSec);
        Runnable updateContent = () -> {
            activeAzPageIndex = targetPage;
            if (activeAzLetter != null) {
                refreshActiveAzCandidates(activeAzLetter);
            }
            renderButtons(activeAzCandidates, true);
        };
        Runnable completed = () -> {
            if (activeAzLetter != null) {
                captureAzRenderState(activeAzLetter, activeAzPageIndex, Math.max(1, maxButtonCount), activeAzCandidates);
            }
        };
        if (swipePageDragging && swipePreviewPageIndex == targetPage) {
            runSwipePreviewPageSwitch(direction, duration, updateContent, completed);
        } else {
            final float travel = Math.max(dp(24), getWidth() * 0.24f);
            runUnifiedAppsBarPageSwitch(direction, travel, duration, updateContent, completed);
        }
    }

    private float resolveCurrentSwipePagePosition() {
        if (activeAzLetter != null && hasAzOverflowPages()) {
            return getAzCurrentPageIndex();
        }
        if (hasPinnedOverflowPages()) {
            return getPinnedCurrentPageIndex();
        }
        return 0f;
    }

    private void applySwipePageDragFeedback(float dx) {
        if (!hasGesturePageSurface()) {
            return;
        }
        int pageDelta = dx < 0f ? 1 : -1;
        boolean canMove = canMoveGesturePage(pageDelta);
        if (!canMove) {
            // Do not stage a fake neighbouring page at either end of the pinned row. The old edge
            // resistance translated the current page and then snapped it back, which looked like a
            // completed page scroll that mysteriously landed on the same content.
            cancelSwipePreviewRebound();
            swipePageDragging = false;
            swipePagePosition = resolveCurrentSwipePagePosition();
            clearSwipePagePreview();
            invalidate();
            return;
        }
        float width = Math.max(1f, getWidth());
        float commitDistance = resolvePageSwipeCommitDistancePx();
        float rawProgress = clamp01(Math.abs(dx) / commitDistance);
        float easedProgress = (float) Math.sin(rawProgress * (Math.PI * 0.5f));
        float resistance = 1f;
        float visualDx = dx;
        float maxTravel = Math.max(dp(18f), width * 0.38f);
        visualDx = clampFloat(visualDx, -maxTravel, maxTravel);

        swipePageDragging = true;
        swipeVisualOffsetX = visualDx;
        swipeDragProgress = easedProgress * resistance;
        prepareSwipePagePreview(pageDelta);

        float base = activeAzLetter != null ? getAzCurrentPageIndex() : getPinnedCurrentPageIndex();
        float signedProgress = (dx < 0f ? easedProgress : -easedProgress) * resistance;
        int pageCount = activeAzLetter != null ? getAzPagesCount() : getPinnedPagesCount();
        swipePagePosition = clampFloat(base + signedProgress, 0f, Math.max(0, pageCount - 1));
        notifyOverflowPagePositionChanged();
        invalidate();
    }

    private float resolvePageSwipeCommitDistancePx() {
        return Math.max(dp(PAGE_SWIPE_COMMIT_DISTANCE_DP),
            Math.max(1f, getWidth()) * PAGE_SWIPE_COMMIT_WIDTH_RATIO);
    }

    private boolean hasGesturePageSurface() {
        if (!TextUtils.isEmpty(lastInput.trim())) {
            return false;
        }
        return activeAzLetter != null ? hasAzOverflowPages() : hasPinnedOverflowPages();
    }

    private boolean canMoveGesturePage(int pageDelta) {
        if (activeAzLetter != null) {
            return hasAzOverflowPages();
        }
        if (!hasPinnedOverflowPages()) {
            return false;
        }
        return getPinnedPagesCount() > 1;
    }

    private void prepareSwipePagePreview(int pageDelta) {
        int direction = pageDelta > 0 ? 1 : -1;
        int targetPage = resolveSwipePreviewTargetPage(pageDelta);
        if (targetPage < 0) {
            swipePreviewDirection = direction;
            swipePreviewPageIndex = -1;
            swipePreviewEntries = Collections.emptyList();
            swipePreviewPinnedItems = Collections.emptyList();
            swipePreviewFolderEntries = Collections.emptyList();
            return;
        }
        if (swipePreviewDirection == direction && swipePreviewPageIndex == targetPage && !swipePreviewEntries.isEmpty()) {
            return;
        }
        swipePreviewDirection = direction;
        swipePreviewPageIndex = targetPage;
        swipePreviewPinnedItems = activeAzLetter != null
            ? Collections.emptyList()
            : buildSwipePreviewPinnedItems(targetPage);
        swipePreviewEntries = buildSwipePreviewEntries(targetPage);
        swipePreviewFolderEntries = buildSwipePreviewFolderEntries(swipePreviewPinnedItems);
    }

    private int resolveSwipePreviewTargetPage(int pageDelta) {
        if (activeAzLetter != null) {
            int totalPages = getAzPagesCount();
            return totalPages > 1 ? wrapAzPageIndex(activeAzPageIndex + pageDelta, totalPages) : -1;
        }
        if (!hasPinnedOverflowPages()) {
            return -1;
        }
        return wrapPageIndex(pinnedPageIndex + pageDelta, getPinnedPagesCount());
    }

    @NonNull
    private List<LauncherAppEntry> buildSwipePreviewEntries(int pageIndex) {
        if (activeAzLetter != null) {
            int perPage = Math.max(1, maxButtonCount);
            int offset = getAzPageStart(activeAzCandidates, pageIndex, perPage);
            List<LauncherAppEntry> pageEntries = new ArrayList<>();
            for (int i = offset; i < activeAzCandidates.size() && pageEntries.size() < perPage; i++) {
                pageEntries.add(activeAzCandidates.get(i));
            }
            return pageEntries;
        }
        if (isMostUsedDynamicPage(pageIndex)) {
            return new ArrayList<>(resolveMostUsedPageEntries());
        }
        List<PinnedItem> pageItems = swipePreviewPinnedItems.isEmpty()
            ? buildSwipePreviewPinnedItems(pageIndex)
            : swipePreviewPinnedItems;
        return entriesForPinnedItems(pageItems);
    }

    @NonNull
    private List<PinnedItem> buildSwipePreviewPinnedItems(int pageIndex) {
        if (pinnedItems == null || pinnedItems.isEmpty()) {
            return Collections.emptyList();
        }
        int perPage = Math.max(1, computePinnedItemsPerPage());
        int offset = pageIndex * perPage;
        List<PinnedItem> pageItems = new ArrayList<>();
        for (int i = offset; i < pinnedItems.size() && pageItems.size() < perPage; i++) {
            PinnedItem item = pinnedItems.get(i);
            if (item != null) pageItems.add(item);
        }
        return pageItems;
    }

    @NonNull
    private List<List<LauncherAppEntry>> buildSwipePreviewFolderEntries(
        @NonNull List<PinnedItem> previewItems
    ) {
        if (previewItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<LauncherAppEntry>> resolved = new ArrayList<>(previewItems.size());
        for (PinnedItem item : previewItems) {
            if (!(item instanceof PinnedFolderItem)) {
                resolved.add(Collections.emptyList());
                continue;
            }
            List<LauncherAppEntry> folderEntries = new ArrayList<>(4);
            for (PinnedAppItem folderApp : ((PinnedFolderItem) item).apps) {
                if (folderEntries.size() >= 4) break;
                LauncherAppEntry entry = resolvePinnedApp(folderApp);
                if (entry != null && entry.icon != null) {
                    folderEntries.add(entry);
                }
            }
            resolved.add(folderEntries);
        }
        return resolved;
    }

    private void drawSwipePreviewPage(@NonNull Canvas canvas) {
        if (swipePreviewEntries.isEmpty() || swipePreviewDirection == 0 || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        int previewAlpha = clamp(Math.round(255f * (0.72f + (0.24f * swipeDragProgress))), 0, 255);
        int slotCount = activeAzLetter != null ? Math.max(1, maxButtonCount) : Math.max(1, computePinnedItemsPerPage());
        int[] azColumns = null;
        if (activeAzLetter != null) {
            int center = clamp(Math.round(computeAzAnchorPosition(activeAzLetter, slotCount)), 0, slotCount - 1);
            azColumns = buildAzPriorityColumnsAround(center, slotCount);
        }
        float pageOffset = swipeVisualOffsetX + (swipePreviewDirection * getWidth());
        int iconSize = iconSizePx();
        for (int i = 0; i < swipePreviewEntries.size() && i < slotCount; i++) {
            int col = azColumns != null ? azColumns[i] : i;
            float left = pageOffset + ((getWidth() * col) / (float) slotCount);
            float right = pageOffset + ((getWidth() * (col + 1)) / (float) slotCount);
            float cx = (left + right) * 0.5f;
            float cy = getHeight() * 0.5f;
            LauncherAppEntry entry = swipePreviewEntries.get(i);
            PinnedItem pinnedItem = (activeAzLetter == null && i < swipePreviewPinnedItems.size())
                ? swipePreviewPinnedItems.get(i)
                : null;
            if (pinnedItem instanceof PinnedFolderItem) {
                List<LauncherAppEntry> folderEntries = i < swipePreviewFolderEntries.size()
                    ? swipePreviewFolderEntries.get(i) : Collections.emptyList();
                drawSwipePreviewFolder(canvas, (PinnedFolderItem) pinnedItem, folderEntries,
                    cx, cy, iconSize, previewAlpha);
            } else {
                drawSwipePreviewIcon(canvas, entry, cx, cy, iconSize, previewAlpha);
            }
        }
    }

    private void drawSwipePreviewFolder(
        @NonNull Canvas canvas,
        @NonNull PinnedFolderItem folder,
        @NonNull List<LauncherAppEntry> miniEntries,
        float cx,
        float cy,
        int iconSize,
        int alpha
    ) {
        float radius = iconSize * 0.5f;
        swipePreviewFolderPaint.setColor(PINNED_FOLDER_FILL_COLOR);
        swipePreviewFolderStrokePaint.setStrokeWidth(1f);
        swipePreviewFolderStrokePaint.setColor(PINNED_FOLDER_STROKE_COLOR);
        canvas.drawCircle(cx, cy, radius, swipePreviewFolderPaint);
        canvas.drawCircle(cx, cy, radius - dp(0.5f), swipePreviewFolderStrokePaint);

        int miniSize = Math.max(dp(9), Math.round(iconSize * 0.42f));
        float miniGap = pinnedFolderMiniIconMarginPx() * 2f;
        int count = miniEntries.size();
        if (count == 1) {
            drawSwipePreviewIcon(canvas, miniEntries.get(0), cx, cy, miniSize, alpha, false);
        } else if (count == 2) {
            float groupWidth = (miniSize * 2f) + miniGap;
            float left = cx - (groupWidth * 0.5f);
            for (int i = 0; i < count; i++) {
                float miniCx = left + (i * (miniSize + miniGap)) + (miniSize * 0.5f);
                drawSwipePreviewIcon(canvas, miniEntries.get(i), miniCx, cy, miniSize, alpha, false);
            }
        } else if (count > 0) {
            float groupWidth = (miniSize * 2f) + miniGap;
            float groupHeight = (miniSize * 2f) + miniGap;
            float left = cx - (groupWidth * 0.5f);
            float top = cy - (groupHeight * 0.5f);
            for (int i = 0; i < count; i++) {
                int row = i / 2;
                int col = i % 2;
                if (count == 3 && i == 2) {
                    col = 0;
                }
                float miniCx = left + (col * (miniSize + miniGap)) + (miniSize * 0.5f);
                if (count == 3 && i == 2) {
                    miniCx = cx;
                }
                float miniCy = top + (row * (miniSize + miniGap)) + (miniSize * 0.5f);
                drawSwipePreviewIcon(canvas, miniEntries.get(i), miniCx, miniCy, miniSize, alpha, false);
            }
        }

        if (notificationBadgesEnabled && folderHasNotification(folder)) {
            drawSwipePreviewBadge(canvas, cx + (iconSize * 0.30f), cy - (iconSize * 0.30f), iconSize);
        }
    }

    private void drawSwipePreviewIcon(
        @NonNull Canvas canvas,
        @NonNull LauncherAppEntry entry,
        float cx,
        float cy,
        int iconSize,
        int alpha
    ) {
        drawSwipePreviewIcon(canvas, entry, cx, cy, iconSize, alpha, true);
    }

    private void drawSwipePreviewIcon(
        @NonNull Canvas canvas,
        @NonNull LauncherAppEntry entry,
        float cx,
        float cy,
        int iconSize,
        int alpha,
        boolean showBadge
    ) {
        // Same harmonized/cached drawable as the resting buttons → no size jump entering a page.
        Drawable icon = iconForDisplay(entry, iconSize);
        if (icon == null) {
            icon = entry.icon != null ? entry.icon : getContext().getPackageManager().getDefaultActivityIcon();
        }
        int half = Math.max(1, iconSize / 2);
        int saveAlpha = icon.getAlpha();
        ColorFilter oldFilter = icon.getColorFilter();
        swipePreviewIconBounds.set(icon.getBounds());
        icon.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
        icon.setAlpha(alpha);
        icon.setColorFilter(appIconColorFilter);
        icon.draw(canvas);
        icon.setColorFilter(oldFilter);
        icon.setAlpha(saveAlpha);
        icon.setBounds(swipePreviewIconBounds);
        if (showBadge && notificationBadgesEnabled && notificationBadgePackages.contains(entry.appRef.packageName)) {
            drawSwipePreviewBadge(canvas, cx + (iconSize * 0.30f), cy - (iconSize * 0.30f), iconSize);
        }
    }

    private void drawSwipePreviewBadge(@NonNull Canvas canvas, float dotX, float dotY, int iconSize) {
        swipePreviewBadgePaint.setColor(resolveNotificationBadgeColor());
        swipePreviewBadgeStrokePaint.setStrokeWidth(dp(1.4f));
        swipePreviewBadgeStrokePaint.setColor(resolveNotificationBadgeStrokeColor());
        float radius = Math.max(dp(3.5f), iconSize * 0.075f);
        canvas.drawCircle(dotX, dotY, radius + dp(1f), swipePreviewBadgeStrokePaint);
        canvas.drawCircle(dotX, dotY, radius, swipePreviewBadgePaint);
    }

    private boolean folderHasNotification(@NonNull PinnedFolderItem folder) {
        for (PinnedAppItem folderApp : folder.apps) {
            if (folderApp != null && folderApp.appRef != null
                && notificationBadgePackages.contains(folderApp.appRef.packageName)) {
                return true;
            }
        }
        return false;
    }

    private void clearSwipePagePreview() {
        swipeVisualOffsetX = 0f;
        swipeDragProgress = 0f;
        swipePreviewDirection = 0;
        swipePreviewPageIndex = -1;
        swipePreviewEntries = Collections.emptyList();
        swipePreviewPinnedItems = Collections.emptyList();
        swipePreviewFolderEntries = Collections.emptyList();
    }

    private void runSwipePreviewPageSwitch(
        int direction,
        long duration,
        @Nullable Runnable updateContent,
        @Nullable Runnable onCompleted
    ) {
        cancelSwipePreviewRebound();
        animate().cancel();
        setListenerSafe(null);
        setTranslationX(0f);
        setAlpha(1f);

        final float startOffset = swipeVisualOffsetX;
        final float targetOffset = -direction * Math.max(1f, getWidth());
        final float distanceRatio = clamp01(Math.abs(targetOffset - startOffset) / Math.max(1f, getWidth()));
        final long settleDuration = clamp(Math.round(duration * (0.72f + (0.28f * distanceRatio))), 240, 420);
        swipePageDragging = true;
        ValueAnimator settle = ValueAnimator.ofFloat(startOffset, targetOffset);
        swipePreviewReboundAnimator = settle;
        settle.setDuration(settleDuration);
        settle.setInterpolator(pageSettleInterpolator());
        settle.addUpdateListener(animation -> {
            swipeVisualOffsetX = (Float) animation.getAnimatedValue();
            swipeDragProgress = clamp01(Math.abs(swipeVisualOffsetX) / Math.max(1f, getWidth() * 0.42f));
            invalidate();
        });
        settle.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (swipePreviewReboundAnimator != animation) {
                    return;
                }
                swipePreviewReboundAnimator = null;
                if (updateContent != null) updateContent.run();
                pageSwitchAnimating = false;
                swipePageDragging = false;
                swipePagePosition = resolveCurrentSwipePagePosition();
                clearSwipePagePreview();
                setTranslationX(0f);
                setAlpha(1f);
                setRowInteractionActive(false);
                if (onCompleted != null) onCompleted.run();
                invalidate();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (swipePreviewReboundAnimator == animation) {
                    swipePreviewReboundAnimator = null;
                }
            }
        });
        settle.start();
    }

    private void cancelSwipePreviewRebound() {
        if (swipePreviewReboundAnimator != null) {
            ValueAnimator animator = swipePreviewReboundAnimator;
            swipePreviewReboundAnimator = null;
            animator.cancel();
        }
    }

    private void animateSwipePageDragBack() {
        if (!swipePageDragging && Math.abs(swipeVisualOffsetX) < 0.5f && Math.abs(getTranslationX()) < 0.5f) {
            clearSwipePagePreview();
            setTranslationX(0f);
            setAlpha(1f);
            swipePagePosition = resolveCurrentSwipePagePosition();
            notifyOverflowPagePositionChanged();
            return;
        }
        swipePagePosition = resolveCurrentSwipePagePosition();
        notifyOverflowPagePositionChanged();
        animate().cancel();
        setListenerSafe(null);
        final float startOffset = swipeVisualOffsetX;
        cancelSwipePreviewRebound();
        swipePreviewReboundAnimator = ValueAnimator.ofFloat(startOffset, 0f);
        long reboundDuration = clamp(Math.round(150f + (70f * clamp01(Math.abs(startOffset) / Math.max(1f, getWidth() * 0.38f)))), 150, 220);
        swipePreviewReboundAnimator.setDuration(reboundDuration);
        swipePreviewReboundAnimator.setInterpolator(pageSettleInterpolator());
        swipePreviewReboundAnimator.addUpdateListener(animation -> {
            swipeVisualOffsetX = (Float) animation.getAnimatedValue();
            swipeDragProgress = startOffset == 0f ? 0f : Math.abs(swipeVisualOffsetX / startOffset);
            invalidate();
        });
        swipePreviewReboundAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (swipePreviewReboundAnimator != animation) {
                    return;
                }
                swipePreviewReboundAnimator = null;
                swipePageDragging = false;
                clearSwipePagePreview();
                setTranslationX(0f);
                setAlpha(1f);
                invalidate();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (swipePreviewReboundAnimator == animation) {
                    swipePreviewReboundAnimator = null;
                }
            }
        });
        swipePreviewReboundAnimator.start();
    }

    private void notifyOverflowPagePositionChanged() {
        if (overflowInteractionListener != null) {
            overflowInteractionListener.onOverflowPagePositionChanged(swipePagePosition);
        }
    }

    private void setRowInteractionActive(boolean active) {
        if (rowInteractionActive == active) {
            return;
        }
        rowInteractionActive = active;
        if (overflowInteractionListener != null) {
            overflowInteractionListener.onOverflowInteractionChanged(active);
            overflowInteractionListener.onOverflowPagePositionChanged(resolveCurrentSwipePagePosition());
        }
    }

    private long computePinnedPageAnimDuration(float velocityPxPerSec) {
        float v = Math.max(150f, Math.min(5200f, Math.abs(velocityPxPerSec)));
        long ms = (long) (410f - ((v - 150f) / (5200f - 150f)) * 130f);
        return clamp((int) ms, 280, 410);
    }

    @NonNull
    private Interpolator pageSettleInterpolator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new PathInterpolator(0.2f, 0f, 0f, 1f);
        }
        return new DecelerateInterpolator(1.8f);
    }

    private void runUnifiedAppsBarPageSwitch(
        int direction,
        float travel,
        long duration,
        @Nullable Runnable updateContent,
        @Nullable Runnable onCompleted
    ) {
        animate().cancel();
        setListenerSafe(null);
        setRotationY(0f);
        setScaleX(1f);
        setScaleY(1f);

        final Interpolator settleInterpolator = pageSettleInterpolator();
        final long outgoingDuration = Math.max(92L, Math.round(duration * 0.44f));
        final long incomingDuration = Math.max(118L, duration - outgoingDuration);

        animate()
            .translationX(-direction * (travel * 0.78f))
            .alpha(0f)
            .setDuration(outgoingDuration)
            .setInterpolator(settleInterpolator)
            .setListener(new AnimatorListenerAdapter() {
                private boolean completed;

                @Override
                public void onAnimationCancel(Animator animation) {
                    finish(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (completed) {
                        return;
                    }
                    completed = true;
                    if (updateContent != null) updateContent.run();
                    setTranslationX(direction * travel);
                    setAlpha(0f);
                    animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(incomingDuration)
                        .setInterpolator(settleInterpolator)
                        .setListener(new AnimatorListenerAdapter() {
                            private boolean incomingCompleted;

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                finish(false);
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (incomingCompleted) {
                                    return;
                                }
                                incomingCompleted = true;
                                finish(true);
                            }

                            private void finish(boolean callCompleted) {
                                setListenerSafe(null);
                                pageSwitchAnimating = false;
                                swipePageDragging = false;
                                swipePagePosition = resolveCurrentSwipePagePosition();
                                clearSwipePagePreview();
                                setRotationY(0f);
                                setScaleX(1f);
                                setScaleY(1f);
                                setTranslationX(0f);
                                setAlpha(1f);
                                setRowInteractionActive(false);
                                if (callCompleted && onCompleted != null) onCompleted.run();
                            }
                        })
                        .start();
                }

                private void finish(boolean callCompleted) {
                    setListenerSafe(null);
                    pageSwitchAnimating = false;
                    swipePageDragging = false;
                    swipePagePosition = resolveCurrentSwipePagePosition();
                    clearSwipePagePreview();
                    setRotationY(0f);
                    setScaleX(1f);
                    setScaleY(1f);
                    setTranslationX(0f);
                    setAlpha(1f);
                    setRowInteractionActive(false);
                    if (callCompleted && onCompleted != null) onCompleted.run();
                }
            })
            .start();
    }

    private void setListenerSafe(@Nullable AnimatorListenerAdapter adapter) {
        animate().setListener(adapter);
    }

    private View createPopupEntryButton(@NonNull LauncherAppEntry entry, int sizePx, @NonNull PinnedFolderItem sourceFolder) {
        ImageButton button = new ImageButton(getContext());
        Drawable icon = iconForDisplay(entry, sizePx);
        button.setImageDrawable(icon);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setAdjustViewBounds(true);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(0x00000000);
        button.setMinimumWidth(sizePx);
        button.setMinimumHeight(sizePx);
        button.setLayoutParams(new ViewGroup.LayoutParams(sizePx, sizePx));
        applyAppIconColorFilter(button);
        button.setOnClickListener(v -> launchEntryFromTouch(v, entry, lastTerminalView));
        bindAppContextLongPress(button, entry, -1, sourceFolder, resolveForSelectionRef(entry.appRef), false);
        button.setContentDescription(entry.label);
        registerLaunchTarget(entry.appRef, button);
        return button;
    }

    private void registerLaunchTarget(@NonNull AppRef appRef, @NonNull View view) {
        String key = componentKeyFromRef(appRef);
        if (key == null) return;
        launchTargetViews.put(key, new WeakReference<>(view));
        launchTargetViewsByPackage.put(appRef.packageName, new WeakReference<>(view));
        String fullKey = componentFullKeyFromRef(appRef);
        if (fullKey != null) {
            launchTargetViews.put(fullKey, new WeakReference<>(view));
        }
    }

    @Nullable
    private Rect getSourceBoundsOnScreen(@NonNull View sourceView) {
        if (!sourceView.isAttachedToWindow()) {
            return null;
        }
        int[] location = new int[2];
        sourceView.getLocationOnScreen(location);
        int width = Math.max(1, sourceView.getWidth());
        int height = Math.max(1, sourceView.getHeight());
        return new Rect(location[0], location[1], location[0] + width, location[1] + height);
    }

    @Nullable
    private View findFirstAttachedLaunchTargetForPackage(@NonNull String packageName) {
        for (Map.Entry<String, WeakReference<View>> entry : launchTargetViews.entrySet()) {
            if (!entry.getKey().startsWith(packageName + "/")) {
                continue;
            }
            View candidate = entry.getValue().get();
            if (candidate != null && candidate.isAttachedToWindow()) {
                return candidate;
            }
        }
        return null;
    }

    private static final class LaunchAnimationContext {
        @Nullable final Rect sourceBounds;
        @Nullable final Bundle options;

        LaunchAnimationContext(@Nullable Rect sourceBounds, @Nullable Bundle options) {
            this.sourceBounds = sourceBounds;
            this.options = options;
        }
    }

    @Nullable
    private String componentKeyFromRef(@Nullable AppRef appRef) {
        if (appRef == null || TextUtils.isEmpty(appRef.packageName) || TextUtils.isEmpty(appRef.activityName)) {
            return null;
        }
        String activity = appRef.activityName;
        if (activity.startsWith(".")) {
            activity = appRef.packageName + activity;
        }
        ComponentName componentName = new ComponentName(appRef.packageName, activity);
        return componentName.flattenToShortString();
    }

    @Nullable
    private String componentFullKeyFromRef(@Nullable AppRef appRef) {
        if (appRef == null || TextUtils.isEmpty(appRef.packageName) || TextUtils.isEmpty(appRef.activityName)) {
            return null;
        }
        String activity = appRef.activityName;
        if (activity.startsWith(".")) {
            activity = appRef.packageName + activity;
        }
        return new ComponentName(appRef.packageName, activity).flattenToString();
    }

    private boolean shouldUseTouchLaunchAnimation(@Nullable View sourceView) {
        if (sourceView == null) return false;
        try {
            return android.provider.Settings.Global.getFloat(getContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void animateLaunchPressDown(@NonNull View sourceView) {
        if (sourceView.getWidth() <= 0 || sourceView.getHeight() <= 0) {
            return;
        }
        cancelLaunchTouchAnimator(sourceView);
        sourceView.animate().cancel();
        sourceView.setPivotX(sourceView.getWidth() * 0.5f);
        sourceView.setPivotY(sourceView.getHeight());
        float lift = dp(4.2f);
        sourceView.animate()
            .translationY(-lift)
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(120L)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void animateLaunchReleaseBounce(@NonNull View sourceView) {
        if (sourceView.getWidth() <= 0 || sourceView.getHeight() <= 0) {
            return;
        }
        cancelLaunchTouchAnimator(sourceView);
        sourceView.animate().cancel();
        sourceView.setPivotX(sourceView.getWidth() * 0.5f);
        sourceView.setPivotY(sourceView.getHeight());

        final float startTranslationY = sourceView.getTranslationY();
        final float startScaleX = sourceView.getScaleX();
        final float startScaleY = sourceView.getScaleY();
        final float lift = dp(4.2f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(760L);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float decay = (float) Math.exp(-3.9f * t);
            float wave = (float) Math.cos((float) (Math.PI * 4.65f * t));
            float simulatedY = -lift * decay * wave;
            float latch = Math.max(0f, Math.min(1f, t / 0.12f));
            float translationY = startTranslationY + ((simulatedY - startTranslationY) * latch);
            float impact = clamp01(translationY / lift);
            float stretch = clamp01((-translationY) / lift);
            float carry = (1f - Math.max(0f, Math.min(1f, t / 0.2f)));
            float carryScaleX = 1f + ((startScaleX - 1f) * carry);
            float carryScaleY = 1f + ((startScaleY - 1f) * carry);

            float targetScaleX = 1f + (0.085f * impact) - (0.018f * stretch);
            float targetScaleY = 1f - (0.108f * impact) + (0.03f * stretch);
            sourceView.setTranslationY(translationY);
            sourceView.setScaleX(lerp(carryScaleX, targetScaleX, 1f - carry));
            sourceView.setScaleY(lerp(carryScaleY, targetScaleY, 1f - carry));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                launchTouchAnimators.remove(sourceView);
                sourceView.setTranslationY(0f);
                sourceView.setScaleX(1f);
                sourceView.setScaleY(1f);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                launchTouchAnimators.remove(sourceView);
                sourceView.setTranslationY(0f);
                sourceView.setScaleX(1f);
                sourceView.setScaleY(1f);
            }
        });
        launchTouchAnimators.put(sourceView, animator);
        animator.start();
    }

    private void cancelLaunchTouchAnimator(@NonNull View sourceView) {
        ValueAnimator animator = launchTouchAnimators.remove(sourceView);
        if (animator != null) {
            animator.cancel();
        }
    }

    public void resetTransientVisualState() {
        animate().cancel();
        cancelSwipePreviewRebound();
        swipePageDragging = false;
        swipePagePosition = resolveCurrentSwipePagePosition();
        clearSwipePagePreview();
        setTranslationX(0f);
        setTranslationY(0f);
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
        pageSwitchAnimating = false;
        clearAzFocusedEntry();
        List<View> animatedViews = new ArrayList<>(launchTouchAnimators.keySet());
        for (View view : animatedViews) {
            if (view == null) continue;
            cancelLaunchTouchAnimator(view);
            view.animate().cancel();
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.setAlpha(1f);
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null) continue;
            child.animate().cancel();
            child.setTranslationX(0f);
            child.setTranslationY(0f);
            child.setScaleX(1f);
            child.setScaleY(1f);
            child.setAlpha(1f);
            View pressTarget = resolvePrimaryPressTarget(child);
            if (pressTarget != child) {
                pressTarget.animate().cancel();
                pressTarget.setTranslationX(0f);
                pressTarget.setTranslationY(0f);
                pressTarget.setScaleX(1f);
                pressTarget.setScaleY(1f);
                pressTarget.setAlpha(1f);
            }
        }
    }

    private boolean hasStableRenderBounds() {
        int minStableWidth = Math.max(1, dp(120));
        int minStableHeight = Math.max(1, dp(24));
        if (!isLaidOut() || getWidth() < minStableWidth || getHeight() < minStableHeight) {
            return false;
        }
        return dockRowHeightHintPx <= 0 || getHeight() >= Math.max(minStableHeight, dockRowHeightHintPx - dp(4));
    }

    private boolean hasStableChildLayout() {
        if (!childLayoutPending) {
            return true;
        }
        int meaningfulChildren = 0;
        int firstLeft = Integer.MIN_VALUE;
        boolean foundDistinctSlot = false;
        int minChildWidth = Math.max(dp(18), getWidth() / Math.max(2, maxButtonCount * 2));
        int minChildHeight = Math.max(dp(18), Math.min(Math.max(dp(18), dockRowHeightHintPx - dp(8)), getHeight()));

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() != VISIBLE || child.getAlpha() <= 0.01f) {
                continue;
            }
            meaningfulChildren++;
            if (child.getWidth() < minChildWidth || child.getHeight() < minChildHeight) {
                return false;
            }
            if (firstLeft == Integer.MIN_VALUE) {
                firstLeft = child.getLeft();
            } else if (Math.abs(child.getLeft() - firstLeft) >= dp(8)) {
                foundDistinctSlot = true;
            }
        }

        return meaningfulChildren <= 1 || foundDistinctSlot;
    }

    public boolean hasStableDisplayLayout() {
        return isAttachedToWindow()
            && hasStableRenderBounds()
            && hasStableChildLayout()
            && !suppressDrawUntilStableLayout;
    }

    private void scheduleStableDrawReleaseIfPossible() {
        if (!hostVisible || !suppressDrawUntilStableLayout || stableLayoutRerenderPosted) {
            return;
        }
        // The timeout must be able to expire even while render bounds never stabilize (e.g. the
        // bar stuck at a collapsed height after a crash-restart mid-layout) — otherwise draw
        // suppression holds the bar blank indefinitely.
        if (hasStableDrawSuppressionExpired()) {
            releaseStableDrawSuppression();
            return;
        }
        if (!hasStableRenderBounds()) {
            postDelayed(this::scheduleStableDrawReleaseIfPossible, 16L);
            return;
        }
        stableLayoutRerenderPosted = true;
        post(() -> {
            stableLayoutRerenderPosted = false;
            if (hasStableDrawSuppressionExpired()) {
                releaseStableDrawSuppression();
                return;
            }
            if (!hostVisible || !isAttachedToWindow() || !hasStableRenderBounds()) {
                return;
            }
            if (!hasStableChildLayout()) {
                if (suppressDrawUntilStableLayout) {
                    postDelayed(this::scheduleStableDrawReleaseIfPossible, 16L);
                }
                return;
            }
            resetTransientVisualState();
            suppressDrawUntilStableLayout = false;
            childLayoutPending = false;
            stableLayoutSuppressedSinceUptimeMs = 0L;
            invalidate();
        });
    }

    private boolean hasStableDrawSuppressionExpired() {
        if (!suppressDrawUntilStableLayout || stableLayoutSuppressedSinceUptimeMs == 0L)
            return false;
        return SystemClock.uptimeMillis() - stableLayoutSuppressedSinceUptimeMs
            >= STABLE_LAYOUT_MAX_SUPPRESS_MS;
    }

    private void releaseStableDrawSuppression() {
        suppressDrawUntilStableLayout = false;
        childLayoutPending = false;
        stableLayoutSuppressedSinceUptimeMs = 0L;
        invalidate();
    }

    private static float lerp(float start, float end, float t) {
        return start + ((end - start) * t);
    }

    private void refreshActiveAzCandidates(char letter) {
        if (appDataProvider == null) {
            return;
        }
        List<LauncherAppEntry> candidates = appDataProvider.getAppsForLetter(letter);
        activeAzCandidates = getUsageStatsStore().rankForAz(candidates);
        azCachedRankLetter = letter;
        azCachedRankedCandidates = activeAzCandidates;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @NonNull
    private static String stableEntryKey(@NonNull LauncherAppEntry entry) {
        return entry.appRef.stableId();
    }

    private void invalidateAzRankCache() {
        azCachedRankLetter = null;
        azCachedRankedCandidates = new ArrayList<>();
        invalidateMostUsedCache();
    }

    private void invalidateAzRenderState() {
        azLastRenderLetter = null;
        azLastRenderPageIndex = -1;
        azLastRenderSlots = -1;
        azLastRenderSignature = 0;
    }

    private boolean shouldSkipAzPreviewRender(char letter, int pageIndex, int slots, @NonNull List<LauncherAppEntry> rankedCandidates) {
        if (!azPreviewRendered || azLastRenderLetter == null) return false;
        int signature = computeAzPageSignature(rankedCandidates, pageIndex, slots);
        return azLastRenderLetter == letter
            && azLastRenderPageIndex == pageIndex
            && azLastRenderSlots == slots
            && azLastRenderSignature == signature;
    }

    private void captureAzRenderState(char letter, int pageIndex, int slots, @NonNull List<LauncherAppEntry> rankedCandidates) {
        azLastRenderLetter = letter;
        azLastRenderPageIndex = pageIndex;
        azLastRenderSlots = slots;
        azLastRenderSignature = computeAzPageSignature(rankedCandidates, pageIndex, slots);
    }

    private int computeAzPageSignature(@NonNull List<LauncherAppEntry> rankedCandidates, int pageIndex, int slots) {
        int perPage = Math.max(1, slots);
        int start = getAzPageStart(rankedCandidates, pageIndex, perPage);
        int end = Math.min(rankedCandidates.size(), start + perPage);
        int signature = 17;
        for (int i = start; i < end; i++) {
            LauncherAppEntry entry = rankedCandidates.get(i);
            String key = stableEntryKey(entry);
            signature = (31 * signature) + (key == null ? 0 : key.hashCode());
            signature = (31 * signature) + (entry.icon != null ? 1 : 0);
        }
        signature = (31 * signature) + start;
        signature = (31 * signature) + end;
        return signature;
    }

    private int computeFolderPopupIconSize(int rows, int cols, int screenW, int screenH) {
        int maxPopupWidth = Math.min(screenW - dp(24), (int) (screenW * 0.9f));
        int maxPopupHeight = Math.min(screenH - dp(80), (int) (screenH * 0.45f));
        int headerHeight = dp(30);
        int horizontalPadding = dp(20);
        int verticalPadding = dp(20) + headerHeight;
        int cellMargin = dp(4);
        int byWidth = (maxPopupWidth - horizontalPadding - (cellMargin * cols * 2)) / Math.max(cols, 1);
        int byHeight = (maxPopupHeight - verticalPadding - (cellMargin * rows * 2)) / Math.max(rows, 1);
        int candidate = Math.min(iconSizePx(), Math.min(byWidth, byHeight));
        return clamp(candidate, dp(16), iconSizePx());
    }

    private void styleGhostButton(@NonNull Button button) {
        button.setBackgroundColor(0x00000000);
        button.setTextColor(resolveLauncherTextColor());
        button.setAllCaps(false);
    }

    private void styleIconButton(@NonNull ImageButton button, int paddingPx) {
        button.setBackgroundColor(0x00000000);
        button.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setColorFilter(resolveLauncherTextColor());
    }

    private int computePinnedItemsPerPage() {
        return Math.max(1, maxButtonCount);
    }

    /** Pages occupied by the user's persisted pinned items (excludes the dynamic most-used page). */
    private int getRealPinnedPagesCount() {
        int totalPinned = pinnedItems == null ? 0 : pinnedItems.size();
        // Compute fresh instead of reading the pinnedItemsPerPage field: the field is 1 until
        // the first successful pinned render and after az/non-pinned renders, so reading it here
        // would report one page per pinned item (the "dozens of empty page ticks" failure).
        int perPage = Math.max(1, computePinnedItemsPerPage());
        if (totalPinned <= 0) return 1;
        return (totalPinned + perPage - 1) / perPage;
    }

    private int getPinnedPagesCount() {
        return getRealPinnedPagesCount() + (hasMostUsedDynamicPage() ? 1 : 0);
    }

    /**
     * The optional dynamic page is shown only when the toggle is on AND there is at least one
     * most-used candidate to fill it. Must NOT call {@link #getPinnedPagesCount()} (recursion).
     */
    private boolean hasMostUsedDynamicPage() {
        return mostUsedPageEnabled && !resolveMostUsedPageEntries().isEmpty();
    }

    /** The dynamic page is always the trailing page, right after the real pinned pages. */
    private boolean isMostUsedDynamicPage(int pageIndex) {
        return hasMostUsedDynamicPage() && pageIndex == getRealPinnedPagesCount();
    }

    /** Page index of the dynamic most-used page, or -1 when it isn't shown. */
    public int getPinnedDynamicPageIndex() {
        return hasMostUsedDynamicPage() ? getRealPinnedPagesCount() : -1;
    }

    /** Top most-used apps (excluding currently pinned), filling one dock page. Cached until dirty. */
    @NonNull
    private List<LauncherAppEntry> resolveMostUsedPageEntries() {
        if (!mostUsedPageEnabled) return java.util.Collections.emptyList();
        if (mostUsedEntriesCache != null) return mostUsedEntriesCache;
        List<LauncherAppEntry> result = new ArrayList<>();
        if (allApps != null && !allApps.isEmpty()) {
            Set<String> pinnedIds = new HashSet<>();
            if (pinnedItems != null) {
                for (PinnedItem item : pinnedItems) {
                    if (item instanceof PinnedAppItem) {
                        pinnedIds.add(((PinnedAppItem) item).appRef.stableId());
                    } else if (item instanceof PinnedFolderItem) {
                        for (PinnedAppItem folderApp : ((PinnedFolderItem) item).apps) {
                            pinnedIds.add(folderApp.appRef.stableId());
                        }
                    }
                }
            }
            List<LauncherAppEntry> candidates = new ArrayList<>();
            for (LauncherAppEntry entry : allApps) {
                if (!pinnedIds.contains(entry.appRef.stableId())) candidates.add(entry);
            }
            List<LauncherAppEntry> ranked = getUsageStatsStore().rankForAz(candidates);
            int limit = computePinnedItemsPerPage();
            for (int i = 0; i < ranked.size() && result.size() < limit; i++) {
                result.add(ranked.get(i));
            }
        }
        mostUsedEntriesCache = result;
        return result;
    }

    private void invalidateMostUsedCache() {
        mostUsedEntriesCache = null;
    }

    public void setMostUsedPageEnabled(boolean enabled) {
        if (mostUsedPageEnabled == enabled) return;
        mostUsedPageEnabled = enabled;
        invalidateMostUsedCache();
        // Caller (applySuggestionBarPreferences) re-renders afterwards; just keep the page index valid.
        pinnedPageIndex = clamp(pinnedPageIndex, 0, Math.max(0, getPinnedPagesCount() - 1));
    }

    private int getAzPagesCount() {
        rebuildAzPageStarts(activeAzCandidates, Math.max(1, maxButtonCount));
        return Math.max(1, azPageStarts.size());
    }

    private void rebuildAzPageStarts(@Nullable List<LauncherAppEntry> entries, int slots) {
        azPageStarts.clear();
        int total = entries == null ? 0 : entries.size();
        int perPage = Math.max(1, slots);
        if (total <= 0) {
            azPageStarts.add(0);
            return;
        }
        int maxStart = Math.max(0, total - perPage);
        int start = 0;
        azPageStarts.add(0);
        while (start < maxStart) {
            start = Math.min(start + perPage, maxStart);
            if (azPageStarts.get(azPageStarts.size() - 1) != start) {
                azPageStarts.add(start);
            }
        }
    }

    private int getAzPageStart(@Nullable List<LauncherAppEntry> entries, int pageIndex, int slots) {
        rebuildAzPageStarts(entries, slots);
        int safeIndex = clamp(pageIndex, 0, Math.max(0, azPageStarts.size() - 1));
        return azPageStarts.get(safeIndex);
    }

    private int wrapAzPageIndex(int targetPage, int totalPages) {
        return wrapPageIndex(targetPage, totalPages);
    }

    static int wrapPageIndex(int targetPage, int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        int wrapped = targetPage % totalPages;
        if (wrapped < 0) {
            wrapped += totalPages;
        }
        return wrapped;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int iconSizePx() {
        int availableHeight = dockRowHeightHintPx > 0 ? dockRowHeightHintPx : getHeight();
        if (availableHeight <= 0) {
            ViewParent parent = getParent();
            if (parent instanceof View) {
                availableHeight = ((View) parent).getHeight();
            }
        }
        if (availableHeight <= 0) {
            return Math.max(dp(20), Math.round(24f * iconScale * getResources().getDisplayMetrics().density));
        }
        int usableHeight = Math.max(dp(24), availableHeight - dp(2));
        int candidate = Math.round(usableHeight * resolveIconFillRatio());
        return clamp(candidate, dp(20), Math.max(dp(20), usableHeight));
    }

    private float resolveIconFillRatio() {
        return AccessoryStackLayoutPolicy.computeDockIconFillRatio(iconScale);
    }

    @NonNull
    private static List<LauncherAppEntry> injectedToEntries(@Nullable List<? extends SuggestionBarButton> buttons) {
        List<LauncherAppEntry> out = new ArrayList<>();
        if (buttons == null) return out;
        for (int i = 0; i < buttons.size(); i++) {
            SuggestionBarButton button = buttons.get(i);
            if (button == null) continue;
            String label = button.getText() == null ? "" : button.getText();
            AppRef ref = new AppRef("injected.test", "entry" + i);
            out.add(new LauncherAppEntry(ref, label, button.getIcon()));
        }
        return out;
    }

    public void releaseResources() {
        removeCallbacks(azResetRunnable);
        removeCallbacks(azPostLaunchClearRunnable);
        clearAzFocusedEntry();
        dismissShortcutsPopup();
        dismissAppContextPopup();
        dismissFolderPopup();
        if (swipeVelocityTracker != null) {
            swipeVelocityTracker.recycle();
            swipeVelocityTracker = null;
        }
        searchExecutor.shutdownNow();
    }
}
