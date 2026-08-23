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
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.app.launcher.LauncherAppLauncher;
import com.termux.app.launcher.PinnedAppsEditor;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherConfigSnapshot;
import com.termux.app.launcher.data.LauncherFolderMutator;
import com.termux.app.launcher.folder.FolderRenameModel;
import com.termux.app.launcher.folder.FolderRenameTitleView;
import com.termux.app.launcher.folder.LauncherFolderPopupController;
import com.termux.app.launcher.popup.AnchoredMenu;
import com.termux.app.launcher.popup.AnchoredMenuTheme;
import com.termux.app.launcher.popup.MenuHighlightTracker;
import com.termux.app.launcher.popup.MenuRow;
import com.termux.app.launcher.popup.MenuRowFactory;
import com.termux.app.launcher.popup.MenuRowWidths;
import com.termux.app.launcher.popup.MenuSpec;
import com.termux.app.launcher.icon.DockIconCache;
import com.termux.app.launcher.icon.RenderedIconDrawable;
import com.termux.app.launcher.data.IconPack;
import com.termux.app.launcher.data.IconPackDrawableItem;
import com.termux.app.launcher.data.IconPackRepository;
import com.termux.app.launcher.data.LauncherIconResolver;
import com.termux.app.launcher.notifications.LauncherNotificationBadgeStore;
import com.termux.app.launcher.notifications.NotificationBadgeFrame;
import com.termux.app.launcher.notifications.NotificationCardSurface;
import com.termux.app.launcher.data.LauncherRankingEngine;
import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.drawer.AppDrawerCategory;
import com.termux.app.launcher.drawer.AppDrawerController;
import com.termux.app.launcher.drawer.AppDrawerPickupDelegate;
import com.termux.app.launcher.drawer.AppDrawerGestureArbiter;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry;
import com.termux.app.launcher.model.IconPackInfo;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedIconOverride;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.app.launcher.paging.DockPagingModel;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
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

public final class SuggestionBarView extends GridLayout
    implements AppDrawerController.AppDrawerDockChoreographyTarget {

    public interface LaunchRippleListener {
        void onLaunchRipple(@NonNull String packageName, @Nullable Drawable icon,
                            @Nullable View sourceView);
    }

    /**
     * The dock's half of the app-drawer pull-down gesture.
     *
     * <p>The row arbitrates the touch stream — it is the only view that sees the whole gesture from
     * {@code ACTION_DOWN} — but it knows nothing about the plane. Everything the claim depends on
     * that lives outside the row (the preference, dock tuning, the palette, the drawer's own state)
     * is asked for through this listener, and the claimed drag is handed straight back to
     * {@code AppDrawerController} through it.
     *
     * <p>The four state queries are read once each, at {@code ACTION_DOWN}, into an
     * {@link AppDrawerGestureArbiter.Eligibility} snapshot: half of them flip <em>because of</em>
     * the gesture in flight, and re-reading them mid-drag would revoke the claim under a finger
     * that is already dragging the plane.
     */
    public interface AppDrawerGestureListener {

        /** The {@code app_launcher_drawer_enabled} preference. */
        boolean isAppDrawerEnabled();

        /** Dock tuning owns drags on the dock itself while it is up. */
        boolean isDockTuningActive();

        /** The command palette is a full-screen overlay of its own; it must not stack with one. */
        boolean isCommandPaletteOpen();

        /** True while the plane is open, dragging or still settling. */
        boolean isAppDrawerEngaged();

        /** FULL status geometry is modal relative to the drawer. */
        default boolean isFullStatusPaneClosed() { return true; }

        /** @param downRawY the gesture's {@code ACTION_DOWN} raw screen Y */
        void onDrawerDragBegin(float downRawY);

        /** @param rawY the current raw screen Y; the plane tracks this 1:1 */
        void onDrawerDrag(float rawY);

        /** @param velocityPxPerSec release velocity, positive downwards */
        void onDrawerDragEnd(float velocityPxPerSec);

        void onDrawerDragCancel();
    }

    private static final String LOG_TAG = "SuggestionBarView";
    private static final char[] AZ_ORDER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray();
    private static final long APP_LAUNCH_TOUCH_DELAY_MS = 120L;
    private static final long PICKUP_DECISION_WINDOW_MS = 650L;
    private static final float PICKUP_X_AXIS_SLOP_FACTOR = 0.9f;
    private static final float PICKUP_Y_INTENT_SLOP_FACTOR = 1.8f;
    private static final float MENU_SELECTION_ARM_SLOP_FACTOR = 0.8f;
    private static final int PINNED_FOLDER_FILL_COLOR = 0x26FFFFFF;
    private static final int PINNED_FOLDER_STROKE_COLOR = 0x33FFFFFF;

    /** Nothing owns the stream yet; the claim tests are still live. */
    private static final int GESTURE_CLAIM_PENDING = 0;
    /** The horizontal page swipe owns it. */
    private static final int GESTURE_CLAIM_PAGE_SWIPE = 1;
    /** The app drawer's pull-down owns it; every other branch stands down. */
    private static final int GESTURE_CLAIM_DRAWER_DRAG = 2;
    /** A child took it first — a shown context menu, a notification swipe, a pickup drag. */
    private static final int GESTURE_CLAIM_CHILD_OWNED = 3;

    /** Pinned icons leave in a wave; past this many the stagger stops growing. */
    private static final int DRAWER_ICON_STAGGER_CAP = 8;
    private static final float DRAWER_ICON_STAGGER_STEP = 0.012f;
    private static final float DRAWER_ICON_FADE_START = 0.02f;
    private static final float DRAWER_ICON_FADE_END = 0.30f;
    private static final float DRAWER_ICON_EXIT_SCALE = 0.92f;
    private static final float DRAWER_ICON_EXIT_LIFT_DP = 6f;

    private List<LauncherAppEntry> allApps = new ArrayList<>();
    @Nullable private LaunchRippleListener launchRippleListener;
    private final List<LauncherAppEntry> terminalSearchEntries = new ArrayList<>();
    private final List<View> terminalSearchTargets = new ArrayList<>();
    private int terminalSearchFocusIndex = -1;
    private int maxButtonCount = 7;
    private float textSize = 12f;
    private boolean bandW = false;
    @Nullable private ColorFilter appIconColorFilter;
    /**
     * The rendered-icon subsystem: harmonized artwork shared with the drawer, budgeted in bytes.
     * See {@link DockIconCache}.
     */
    private final DockIconCache iconCache = new DockIconCache(
        getResources(),
        DockIconCache.memoryClassMb(getContext()),
        () -> getContext().getPackageManager().getDefaultActivityIcon());
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
    private final LauncherFolderPopupController sharedFolderPopup =
        new LauncherFolderPopupController();
    @Nullable private View activePinnedDragSourceView;
    @Nullable private PinnedDragState activePinnedDragState;
    @Nullable private FolderEntryDragState activeFolderEntryDragState;
    /** Identifies a folder-member drag in windows the drag's local state does not reach. */
    static final String FOLDER_ENTRY_CLIP_LABEL = "folder-entry";
    /** The launcher's look-and-feel, read live by the popup module. */
    private final AnchoredMenuTheme menuTheme = new AnchoredMenuTheme() {
        @Override public int textColor() { return resolveLauncherTextColor(); }
        @Override public int selectedTextColor() { return resolveLauncherSelectedTextColor(); }
        @Override public int opacityPercent() { return appBarOpacity; }
        @Override public boolean blurEnabled() { return blurEnabled; }
        @Override public int blurRadiusDp() { return blurRadiusDp; }
    };
    private final MenuRowFactory menuRows = new MenuRowFactory(getContext(), menuTheme);
    /** The app/folder context menu, and the shortcuts menu that opens beside it. */
    private final AnchoredMenu appContextMenu = new AnchoredMenu(this, menuTheme);
    private final AnchoredMenu shortcutsMenu = new AnchoredMenu(this, menuTheme);
    private final AnchoredMenu categoryPickerMenu = new AnchoredMenu(this, menuTheme);
    /**
     * Surface used to build the two popups whose windows are owned elsewhere: the folder grid (the
     * shared folder controller runs its own spring and dim) and the notification stack (focusable
     * from creation, with its own dim and IME handoff).
     */
    private final AnchoredMenu detachedMenuSurface = new AnchoredMenu(this, menuTheme);
    /** How the dock's badge dots read the launcher's live badge state and materials. */
    private final NotificationBadgeFrame.Style notificationBadgeStyle =
        new NotificationBadgeFrame.Style() {
            @Override public boolean badgesEnabled() { return notificationBadgesEnabled; }
            @NonNull @Override public Set<String> activeBadgePackages() {
                return notificationBadgePackages;
            }
            @Override public int badgeFillColor() { return resolveNotificationBadgeColor(); }
            @Override public int badgeStrokeColor() { return resolveNotificationBadgeStrokeColor(); }
            @Override public int iconSizePx() { return SuggestionBarView.this.iconSizePx(); }
            @Override public float density() { return screenDensity(); }
        };
    /**
     * Card building, swipe-to-dismiss and the inline reply composer for the mirrored notification
     * stack. The window it lives in stays here (see {@link #showNotificationPopup}); the surface only
     * builds content and reports back through its listener.
     */
    private final NotificationCardSurface notificationCards = new NotificationCardSurface(
        new NotificationCardSurface.Host() {
            @NonNull @Override public Context context() { return getContext(); }
            @Override public int dp(int value) { return SuggestionBarView.this.dp(value); }
            @Override public float density() { return screenDensity(); }
            @Override public int textColor() { return resolveLauncherTextColor(); }
            @Override public int subtleTextColor() { return resolveLauncherSubtleTextColor(); }
            @Override public int panelColor() { return resolveLauncherPanelColor(); }
            @Override public int outlineColor() { return resolveLauncherOutlineColor(); }
            @Override public int highlightAccentColor() {
                return MaterialColors.getColor(SuggestionBarView.this,
                    com.google.android.material.R.attr.colorPrimary, resolveLauncherOutlineColor());
            }
            @Override public int sendButtonTextColor() {
                return MaterialColors.getColor(SuggestionBarView.this,
                    com.google.android.material.R.attr.colorOnPrimaryContainer,
                    resolveLauncherTextColor());
            }
            @Override public int sendButtonBackgroundColor() {
                return MaterialColors.getColor(SuggestionBarView.this,
                    com.google.android.material.R.attr.colorPrimaryContainer,
                    resolveLauncherPanelColor());
            }
            @Override public void post(@NonNull Runnable action) {
                SuggestionBarView.this.post(action);
            }
            @Override public void postDelayed(@NonNull Runnable action, long delayMs) {
                SuggestionBarView.this.postDelayed(action, delayMs);
            }
            @Override public void cancelNotification(@NonNull String key) {
                LauncherCtlNotificationListener.dismissNotification(key);
            }
            @Override public boolean hasActiveNotifications(@NonNull String packageName) {
                return !LauncherNotificationBadgeStore
                    .getNotificationsForPackage(packageName).isEmpty();
            }
        },
        new NotificationCardSurface.Listener() {
            @Override public void onContentIntentSent(@NonNull StatusBarNotification sbn,
                                                      boolean sent) {
            }
            @Override public void onActionInvoked(@NonNull StatusBarNotification sbn,
                                                 @NonNull Notification.Action action, boolean sent) {
            }
            @Override public void onCardDismissed(@NonNull StatusBarNotification sbn) {
            }
            @Override public void onReplyComposerOpened(@NonNull EditText editor) {
                notificationReplyEditor = editor;
                enableNotificationReplyInput(editor);
            }
            @Override public void onReplyImeRequested(@NonNull EditText editor) {
                enableNotificationReplyInput(editor);
            }
            @Override public void onReplySent(@NonNull StatusBarNotification sbn,
                                              @NonNull CharSequence text) {
            }
            @Override public void onPopupDismissRequested() {
                dismissNotificationPopup();
            }
        });
    private final MenuHighlightTracker menuHighlight =
        new MenuHighlightTracker(this, menuRows, appContextMenu, shortcutsMenu);
    private PopupWindow notificationPopupWindow;
    @Nullable private PopupWindow notificationInteractionPopup;
    @Nullable private FolderRenameHost folderRenameHost;
    @Nullable private NotificationPopupInteractionListener notificationPopupInteractionListener;
    @Nullable private String notificationPopupPackage;
    @NonNull private Set<String> notificationPopupKeys = Collections.emptySet();
    /** The reply field currently on screen, so an incoming notification cannot discard a draft. */
    @Nullable private EditText notificationReplyEditor;
    @Nullable private Dialog iconPickerDialog;

    private String lastInput = "";
    private TerminalView lastTerminalView;

    private Character activeAzLetter;
    private int activeAzSelection = 0;
    private int activeAzPageIndex = 0;
    private List<LauncherAppEntry> activeAzCandidates = new ArrayList<>();
    private int pinnedPageIndex = 0;
    private int pinnedItemsPerPage = 1;
    private float swipeDownX = 0f;
    private float swipeDownY = 0f;
    /**
     * The gesture's down point in screen coordinates. The drawer runs on raw values rather than the
     * local ones the page swipe uses because the row's own parent is translated while the plane is
     * dragging: local Y would then be measured against a moving origin.
     */
    private float swipeDownRawX = 0f;
    private float swipeDownRawY = 0f;
    private final AppDrawerGestureArbiter gestureArbiter = new AppDrawerGestureArbiter();
    /**
     * The latched owner of the current stream, mirrored from {@link #gestureArbiter} at the two
     * points it is consulted. Replaces the {@code horizontalIntent} boolean the move handler used to
     * recompute from scratch on every event — recomputation is what let one drag hand ownership
     * back and forth, and fire both the page swipe and the drawer.
     */
    private int gestureClaim = GESTURE_CLAIM_PENDING;
    @Nullable private AppDrawerGestureListener appDrawerGestureListener;
    /** Last progress handed down by {@code AppDrawerController}; 0 means no transition on screen. */
    private float drawerTransitionProgress = 0f;
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
    private static final int MAX_DEFERRED_RENDER_ATTEMPTS = 8;
    private int deferredRenderAttempts;
    private int lastSurfaceRenderSignature = 0;
    private boolean pendingPinnedMutationFeedback = false;
    private boolean suppressContextLongPressForSwipe = false;
    private int folderDragHoverIndex = -1;
    @Nullable private LongPressPickupState activeLongPressPickupState;
    @Nullable private AppMenuContext activeAppMenuContext;
    @Nullable private List<ShortcutInfo> activeAppMenuShortcuts;
    /** Rows being composed for the menu currently under construction, before it is shown. */
    private final List<MenuRow> pendingMenuRows = new ArrayList<>();
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
    @Nullable private Runnable drawerConfigChangedListener;
    /** A config change that landed while the folder popup was up; replayed when it closes. */
    private boolean pendingDrawerConfigRefresh;
    /** A catalogue swap that landed while the host was hidden; rows re-render on return. */
    private boolean pendingCatalogRefreshRender;
    private final LauncherConfigRepository.Listener configListener = snapshot -> post(() -> {
        pinnedItems = new ArrayList<>(snapshot.dockItems);
        invalidateRenderedIconCaches();
        reloadWithInput("", lastTerminalView);
        if (appCatalogChangedListener != null) appCatalogChangedListener.run();
        // Recomposing the drawer dismisses its popups, so a change made from inside the folder
        // popup (a rename, a removal) must not tear the popup down under the finger.
        if (sharedFolderPopup.isShowing()) pendingDrawerConfigRefresh = true;
        else notifyDrawerConfigChanged();
    });
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

    public interface OverflowInteractionListener {
        void onOverflowInteractionChanged(boolean interacting);
        default void onOverflowPagePositionChanged(float pagePosition) {}
    }

    /**
     * The activity-side folder rename flow. The folder popup's title is the rename field's anchor,
     * so the popup has to reach the controller that owns the rename — through this, rather than by
     * casting its own context to the activity.
     */
    public interface FolderRenameHost {
        void beginFolderRename(long revision, @NonNull String folderId, @NonNull String title,
                               @NonNull FolderRenameTitleView titleView);
        void cancelFolderRename();
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
        menuHighlight.setTintBase(inheritedTintColor & 0x00FFFFFF);
        // Deciding what the shortcuts menu holds is this view's business, not the tracker's.
        menuHighlight.setSubmenuOpener(this::openShortcutsForFocusedRow);
        menuHighlight.setSubmenuDismisser(this::dismissShortcutsPopup);
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
        if (configRepository != null) configRepository.addListener(configListener);
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
        if (configRepository != null) configRepository.removeListener(configListener);
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
        // A genuine layout pass is new information: the bounded defer in renderButtons may try
        // its full budget again.
        if (changed) deferredRenderAttempts = 0;
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
        if (appIconColorFilter != null) {
            imageView.setColorFilter(appIconColorFilter);
            return;
        }
        imageView.clearColorFilter();
        Drawable iconDrawable = imageView.getDrawable();
        if (iconDrawable != null) iconDrawable.clearColorFilter();
        imageView.invalidate();
    }

    /**
     * Applies the dock's current icon tint treatment (monochrome mode included) to a view the dock
     * does not own, so drawer cells never drift from the row above them.
     */
    public void applyIconColorFilter(@NonNull ImageView imageView) {
        applyAppIconColorFilter(imageView);
    }

    private void invalidateRenderedIconCaches() {
        launcherTextColorCache = null;
        iconCache.invalidateAll();
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
        menuHighlight.setTintBase(inheritedTintColor & 0x00FFFFFF);
        invalidateNotificationBadgeViews();
    }

    private int resolveLauncherTextColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface));
    }

    /**
     * Cached theme resolve: the drawer asks for this on every cell bind, and each resolve walks
     * the theme's attribute table. Invalidated with the rendered-icon caches, which the styling
     * reload paths already evict whenever the theme can have changed.
     */
    @Nullable private Integer launcherTextColorCache;

    /** The launcher's on-surface text colour, for surfaces rendered outside this view. */
    public int getLauncherTextColor() {
        Integer cached = launcherTextColorCache;
        if (cached != null) return cached;
        int resolved = resolveLauncherTextColor();
        launcherTextColorCache = resolved;
        return resolved;
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
                boolean keysChanged = !currentKeys.equals(notificationPopupKeys);
                if (shouldDismissNotificationPopupOnKeyChange(keysChanged, isComposingReply())) {
                    dismissNotificationPopup();
                } else if (keysChanged) {
                    // Re-snapshot even when the popup stays: otherwise the very next notification
                    // change compares against stale keys and dismisses immediately.
                    notificationPopupKeys = Collections.unmodifiableSet(currentKeys);
                }
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
        if (this.configRepository != null) this.configRepository.removeListener(configListener);
        this.configRepository = configRepository;
        if (this.configRepository != null) {
            this.configRepository.addListener(configListener);
            this.pinnedItems = this.configRepository.loadPinnedItems();
        }
    }

    @NonNull
    public LauncherConfigSnapshot getLauncherConfigSnapshot() {
        if (configRepository != null) return configRepository.loadSnapshot();
        // Tests may construct the row without persistence; use an empty in-memory store.
        return new LauncherConfigRepository(new LauncherConfigRepository.PreferencesStore() {
            private String value = "{\"schemaVersion\":5,\"items\":[],\"folders\":[]}";
            @Override public String getPinnedItemsV2() { return value; }
            @Override public int getPinnedItemsSchemaVersion() { return 5; }
            @Override public boolean commitPinnedItems(String next, int version) {
                value = next;
                return true;
            }
            @Override public String getLegacyDefaultButtons() { return ""; }
        }).loadSnapshot();
    }

    public void openFolderFromDrawer(@NonNull String folderId, @Nullable View anchor) {
        if (configRepository == null) return;
        PinnedFolderItem folder = configRepository.loadSnapshot().folder(folderId);
        if (folder != null) showFolderPopup(folder, anchor);
    }

    @Nullable
    public LauncherAppEntry resolveFolderMemberForDrawer(@NonNull PinnedAppItem member) {
        return resolvePinnedApp(member);
    }

    @NonNull
    public LauncherConfigRepository.MutationResult createDrawerFolder(long revision,
        @Nullable LauncherAppEntry target, @NonNull String sourceStableId) {
        if (configRepository == null || target == null)
            return LauncherConfigRepository.MutationResult.MISSING;
        LauncherAppEntry source = findDrawerEntry(sourceStableId);
        if (source == null) return LauncherConfigRepository.MutationResult.MISSING;
        return configRepository.createFolder(revision, UUID.randomUUID().toString(),
            new PinnedAppItem(target.appRef), new PinnedAppItem(source.appRef));
    }

    @NonNull
    public LauncherConfigRepository.MutationResult addDrawerAppToFolder(long revision,
        @NonNull String folderId, @NonNull String sourceStableId) {
        if (configRepository == null) return LauncherConfigRepository.MutationResult.MISSING;
        LauncherAppEntry source = findDrawerEntry(sourceStableId);
        if (source == null) return LauncherConfigRepository.MutationResult.MISSING;
        return configRepository.addAppToFolder(revision, folderId,
            new PinnedAppItem(source.appRef));
    }

    /**
     * Parks a drawer folder in front of a given app (or at the end of the list), which is what a
     * folder tile dragged around the drawer persists.
     */
    @NonNull
    public LauncherConfigRepository.MutationResult moveDrawerFolder(long revision,
        @NonNull String folderId, @Nullable String anchorStableId) {
        if (configRepository == null) return LauncherConfigRepository.MutationResult.MISSING;
        return configRepository.setFolderDrawerAnchor(revision, folderId, anchorStableId);
    }

    @Nullable
    private LauncherAppEntry findDrawerEntry(@NonNull String stableId) {
        if (allApps == null) return null;
        for (LauncherAppEntry entry : allApps)
            if (stableId.equals(entry.appRef.stableId())) return entry;
        return null;
    }

    public void setAppDataProvider(@Nullable LauncherAppDataProvider appDataProvider) {
        this.appDataProvider = appDataProvider;
    }

    public void setAppCatalogChangedListener(@Nullable Runnable appCatalogChangedListener) {
        this.appCatalogChangedListener = appCatalogChangedListener;
    }

    /** The drawer's recompose hook for pin/folder mutations, which never touch the app catalog. */
    public void setDrawerConfigChangedListener(@Nullable Runnable drawerConfigChangedListener) {
        this.drawerConfigChangedListener = drawerConfigChangedListener;
    }

    private void notifyDrawerConfigChanged() {
        pendingDrawerConfigRefresh = false;
        if (drawerConfigChangedListener != null) drawerConfigChangedListener.run();
    }

    public void setOverflowInteractionListener(@Nullable OverflowInteractionListener listener) {
        overflowInteractionListener = listener;
    }

    public void setFolderRenameHost(@Nullable FolderRenameHost host) {
        this.folderRenameHost = host;
    }

    /** No-op when nothing hosts the rename flow (the popup is then just a folder grid). */
    private void cancelFolderRename() {
        if (folderRenameHost != null) folderRenameHost.cancelFolderRename();
    }

    public void setNotificationPopupInteractionListener(
        @Nullable NotificationPopupInteractionListener listener) {
        notificationPopupInteractionListener = listener;
    }

    public void setHostVisible(boolean visible) {
        hostVisible = visible;
        if (visible) {
            if (pendingCatalogRefreshRender) {
                pendingCatalogRefreshRender = false;
                post(() -> reloadWithInput(lastInput, lastTerminalView));
            }
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
            usageStatsStore = LauncherUsageStatsStore.getInstance(getContext());
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
     * Refreshes the catalogue in place after a package change: the current apps keep rendering
     * while the provider rebuilds in the background, then everything swaps at once. This replaces
     * the old clearAppCache()+reloadAllApps() flow on this path, whose synchronous wipe blanked
     * the dock and drawer for the whole rebuild (seconds, every icon re-resolved).
     *
     * <p>{@code changedPackages} scopes the rebuild — entries of untouched packages are reused
     * wholesale. Null forces a full rebuild. An update to the active icon pack itself widens to a
     * full rebuild here, because reused entries would keep the pack's stale artwork.
     */
    void refreshAllApps(@Nullable Set<String> changedPackages) {
        if (injectedSuggestionButtons != null
            || appDataProvider == null || !appDataProvider.hasLoadedApps()) {
            reloadAllApps();
            return;
        }
        if (changedPackages != null && !changedPackages.isEmpty()) {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(getContext(), false);
            if (preferences != null) {
                String globalPack = preferences.getAppLauncherIconPackPackage();
                String pinnedPack = preferences.getAppLauncherPinnedIconPackPackage();
                if ((globalPack != null && changedPackages.contains(globalPack))
                    || (pinnedPack != null && changedPackages.contains(pinnedPack))) {
                    changedPackages = null;
                }
            }
        }
        if (changedPackages == null) {
            // Full rebuild resolves every icon again — drop the parsed pack caches so it picks up
            // the new pack resources instead of the 10s-TTL stale ones.
            if (iconResolver != null) iconResolver.clearCache();
            if (iconPackRepository != null) iconPackRepository.clearCache();
        }
        appDataProvider.refreshAsync(changedPackages, () -> {
            allApps = appDataProvider.getAllApps();
            resolvedRefCache.clear();
            shortcutCache.clear();
            invalidateRenderedIconCaches();
            pruneUnavailablePinnedItems();
            invalidateAzRankCache();
            invalidateAzRenderState();
            if (appCatalogChangedListener != null) {
                appCatalogChangedListener.run();
            }
            if (hostVisible && isAttachedToWindow()) {
                reloadWithInput(lastInput, lastTerminalView);
            } else {
                // The swap landed while the host was away; re-render the rows on return, or the
                // dock would keep showing the pre-change catalogue with no later signal to fix it.
                pendingCatalogRefreshRender = true;
            }
        });
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
        pinnedPageIndex = DockPagingModel.clampPage(pinnedPageIndex, getPinnedPagesCount());
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
        return isAzPreviewActive() && DockPagingModel.hasOverflowPages(getAzPagesCount());
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
        return DockPagingModel.visualPagePosition(hasAzOverflowPages(), swipePageDragging,
            pageSwitchAnimating, swipePagePosition, activeAzPageIndex);
    }

    public int getAzVisiblePageCount() {
        return getAzPagesCount();
    }

    public boolean hasPinnedOverflowPages() {
        return activeAzLetter == null
            && TextUtils.isEmpty(lastInput.trim())
            && pinnedItems != null
            && !pinnedItems.isEmpty()
            && DockPagingModel.hasOverflowPages(getPinnedPagesCount());
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
        return DockPagingModel.visualPagePosition(hasPinnedOverflowPages(), swipePageDragging,
            pageSwitchAnimating, swipePagePosition, pinnedPageIndex);
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
            swipeDownRawX = event.getRawX();
            swipeDownRawY = event.getRawY();
            gestureClaim = GESTURE_CLAIM_PENDING;
            gestureArbiter.begin(swipeDownRawX, swipeDownRawY, captureDrawerEligibility());
        } else if (action == MotionEvent.ACTION_MOVE) {
            setRowInteractionActive(true);
            if (swipeVelocityTracker != null) swipeVelocityTracker.addMovement(event);
            if (activeAzLetter != null) {
                scheduleAzResetTimeout();
            }
            if (gestureClaim == GESTURE_CLAIM_DRAWER_DRAG) {
                if (appDrawerGestureListener != null)
                    appDrawerGestureListener.onDrawerDrag(event.getRawY());
                return true;
            }
            // The drawer test is inside evaluate() and runs before the page test; a child that has
            // already taken the stream latches first so neither can steal it back.
            if (isGestureOwnedByChild()) {
                gestureClaim = toGestureClaim(gestureArbiter.claimChild());
            } else {
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                gestureClaim = toGestureClaim(
                    gestureArbiter.evaluate(event.getRawX(), event.getRawY(), slop));
            }
            if (gestureClaim == GESTURE_CLAIM_DRAWER_DRAG) {
                beginDrawerDrag(event);
                return true;
            }
            float dx = event.getX() - swipeDownX;
            if (gestureClaim == GESTURE_CLAIM_PAGE_SWIPE && TextUtils.isEmpty(lastInput.trim())) {
                suppressContextLongPressForSwipe = true;
                cancelPendingContextLongPresses();
                applySwipePageDragFeedback(dx);
            }
        } else if (action == MotionEvent.ACTION_UP) {
            int claim = gestureClaim;
            resetGestureClaim();
            if (claim == GESTURE_CLAIM_DRAWER_DRAG) {
                finishDrawerDrag(event, false);
                return true;
            }
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
            int committedPageDelta = DockPagingModel.commitPageDelta(dx, dy, vx,
                resolvePageSwipeCommitDistancePx(), density());
            if (claim == GESTURE_CLAIM_PAGE_SWIPE && committedPageDelta != 0
                && TextUtils.isEmpty(lastInput.trim())) {
                int pageDelta = committedPageDelta;
                if (activeAzLetter != null) {
                    int totalPages = getAzPagesCount();
                    if (totalPages > 1) {
                        int next = DockPagingModel.wrap(activeAzPageIndex + pageDelta, totalPages);
                        if (next != activeAzPageIndex) {
                            animateAzPageSwitch(pageDelta, DockPagingModel.settleVelocityHint(dx, vx));
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
                        int next = DockPagingModel.wrap(pinnedPageIndex + pageDelta, totalPages);
                        if (next != pinnedPageIndex) {
                            animatePageSwitch(pageDelta, DockPagingModel.settleVelocityHint(dx, vx));
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
            int claim = gestureClaim;
            resetGestureClaim();
            if (claim == GESTURE_CLAIM_DRAWER_DRAG) {
                finishDrawerDrag(event, true);
                return true;
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
        } else if (gestureClaim == GESTURE_CLAIM_DRAWER_DRAG) {
            // Extra pointers during a claimed drag: swallowed rather than forwarded, so a second
            // finger cannot start a second interaction on children this stream has already
            // cancelled.
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    public void setAppDrawerGestureListener(@Nullable AppDrawerGestureListener listener) {
        appDrawerGestureListener = listener;
    }

    /**
     * The dock row's bounds in screen coordinates — the rectangle the drawer's pull-down starts
     * from, and the one the plane's seed rect has to agree with.
     */
    public void getAppsRowScreenRect(@NonNull Rect out) {
        int[] location = new int[2];
        getLocationOnScreen(location);
        out.set(location[0], location[1], location[0] + getWidth(), location[1] + getHeight());
    }

    /**
     * The eight vetoes, read once at {@code ACTION_DOWN}. Four are the row's own state and four come
     * from the host through {@link AppDrawerGestureListener}; with no listener wired the drawer can
     * never claim, which is what keeps every test and every non-launcher host on the old behaviour.
     */
    @NonNull
    private AppDrawerGestureArbiter.Eligibility captureDrawerEligibility() {
        AppDrawerGestureListener listener = appDrawerGestureListener;
        boolean portrait = getResources().getConfiguration().orientation
            != Configuration.ORIENTATION_LANDSCAPE;
        // A pickup state or a folder-drag hover left over from the previous gesture means the row is
        // still mid-interaction; the drawer stays out of it.
        boolean noActivePickup = activeLongPressPickupState == null && folderDragHoverIndex < 0;
        return new AppDrawerGestureArbiter.Eligibility(
            listener != null && listener.isAppDrawerEnabled(),
            // The filtered apps row used to veto the pull-down, forcing a search clear before the
            // drawer could open. A vertical pull is unambiguous even over filtered results — the
            // page swipe stays horizontal — so the veto slot is permanently clear.
            true,
            activeAzLetter == null,
            portrait,
            listener != null && !listener.isDockTuningActive(),
            listener != null && !listener.isCommandPaletteOpen(),
            noActivePickup,
            listener != null && !listener.isAppDrawerEngaged(),
            listener != null && listener.isFullStatusPaneClosed());
    }

    /** The child-owned cases, read live: all three begin <em>during</em> the stream, not before it. */
    private boolean isGestureOwnedByChild() {
        LongPressPickupState state = activeLongPressPickupState;
        return state != null
            && (state.menuShown || state.notificationSwipeStarted || state.dragStarted);
    }

    private static int toGestureClaim(@NonNull AppDrawerGestureArbiter.Claim claim) {
        switch (claim) {
            case PAGE_SWIPE: return GESTURE_CLAIM_PAGE_SWIPE;
            case DRAWER_DRAG: return GESTURE_CLAIM_DRAWER_DRAG;
            case CHILD_OWNED: return GESTURE_CLAIM_CHILD_OWNED;
            case PENDING:
            default: return GESTURE_CLAIM_PENDING;
        }
    }

    private void resetGestureClaim() {
        gestureClaim = GESTURE_CLAIM_PENDING;
        gestureArbiter.reset();
    }

    /**
     * Hands the stream to the drawer. The order here is not cosmetic:
     *
     * <ol>
     *   <li>the long-press suppression flag goes up and the pending long-presses are cancelled, so
     *       the context menu cannot open under the plane;
     *   <li>any popup already on screen is dismissed — it is a window of its own and would outlive
     *       the row it was anchored to;
     *   <li><b>exactly one</b> synthetic {@code ACTION_CANCEL} goes down to the children. Every
     *       later event of this stream is consumed here without reaching {@code super}, so without
     *       it the pressed icon never sees an UP: {@code animateLaunchReleaseBounce} never runs and
     *       {@code activeLongPressPickupState} is left pointing at a permanently pressed view. The
     *       one-way latch is what guarantees "exactly one" — this method runs on the transition into
     *       {@link #GESTURE_CLAIM_DRAWER_DRAG}, and the claim can never re-enter it;
     *   <li>only then does the drag begin, from the <em>down</em> point rather than the current one,
     *       so the plane's progress starts at zero.
     * </ol>
     */
    private void beginDrawerDrag(@NonNull MotionEvent event) {
        suppressContextLongPressForSwipe = true;
        cancelPendingContextLongPresses();
        dismissAppContextPopup();
        dismissFolderPopup();
        dismissShortcutsPopup();
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        try {
            super.dispatchTouchEvent(cancel);
        } finally {
            cancel.recycle();
        }
        if (appDrawerGestureListener != null) {
            appDrawerGestureListener.onDrawerDragBegin(swipeDownRawY);
            // The move that crossed the claim threshold is already drawer travel. This matters on
            // the cold path: building the selected drawer content can occupy the rest of the input
            // frame, leaving UP as the next delivered event. Dropping this move then releases at
            // progress zero and makes that first otherwise-valid pull look swallowed.
            appDrawerGestureListener.onDrawerDrag(event.getRawY());
        }
    }

    /**
     * Ends a drawer drag and — the part that is easy to lose — clears
     * {@code suppressContextLongPressForSwipe}. The two places that used to clear it live further
     * down the UP and CANCEL branches, which this path returns before reaching, and a flag left up
     * makes every later long-press on the dock silently do nothing.
     */
    private void finishDrawerDrag(@NonNull MotionEvent event, boolean cancelled) {
        float velocityPxPerSec = 0f;
        if (swipeVelocityTracker != null) {
            if (!cancelled) {
                swipeVelocityTracker.addMovement(event);
                swipeVelocityTracker.computeCurrentVelocity(1000);
                velocityPxPerSec = swipeVelocityTracker.getYVelocity();
            }
            swipeVelocityTracker.recycle();
            swipeVelocityTracker = null;
        }
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
        suppressContextLongPressForSwipe = false;
        setRowInteractionActive(false);
        AppDrawerGestureListener listener = appDrawerGestureListener;
        if (listener == null) return;
        if (cancelled) listener.onDrawerDragCancel();
        else listener.onDrawerDragEnd(velocityPxPerSec);
    }

    /**
     * The pinned icons' exit, staggered by slot so the row empties as a wave rather than a block.
     * Each icon's ramp is offset by its index, capped at {@link #DRAWER_ICON_STAGGER_CAP} so a long
     * dock does not push the last icon's fade past the end of the transition.
     *
     * <p>This is the only thing the drawer's controller asks of the row, and the row is the only
     * writer of these properties while a transition is on screen — see
     * {@link #resetTransientVisualState()}.
     */
    @Override
    public void setDrawerTransitionProgress(float progress) {
        float p = clamp01(progress);
        drawerTransitionProgress = p;
        float lift = dp(DRAWER_ICON_EXIT_LIFT_DP);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null) continue;
            float stagger = DRAWER_ICON_STAGGER_STEP * Math.min(i, DRAWER_ICON_STAGGER_CAP);
            float r = AppDrawerTransitionGeometry.ramp(p,
                DRAWER_ICON_FADE_START + stagger, DRAWER_ICON_FADE_END + stagger);
            float scale = 1f + ((DRAWER_ICON_EXIT_SCALE - 1f) * r);
            child.setAlpha(1f - r);
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setTranslationY(lift * r);
        }
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
    public void reload() {
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
            // Bounded, not clock-bounded: a dock that can never stabilize (hidden, or measured
            // 1x1 in a test fixture) must stop reposting, or the main queue never drains and
            // Robolectric's idle() livelocks. The next real reload or size change retries.
            if (deferredRenderAttempts >= MAX_DEFERRED_RENDER_ATTEMPTS) {
                return;
            }
            deferredRenderAttempts++;
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
        deferredRenderAttempts = 0;
        int buttonCount = Math.max(1, maxButtonCount);
        int renderStartCol = 0;
        List<PinnedItem> pinnedForSlots = new ArrayList<>();
        int pinnedPageOffset = 0;
        Set<String> azFreshPageEntryKeys = Collections.emptySet();

        if (azPreview) {
            int perPage = Math.max(1, maxButtonCount);
            int totalPages = getAzPagesCount();
            activeAzPageIndex = DockPagingModel.clampPage(activeAzPageIndex, totalPages);
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
            pinnedPageIndex = DockPagingModel.clampPage(pinnedPageIndex, totalPages);
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

    /**
     * Whether row haptics are on, so a letter tick raised by a surface the dock does not own — the
     * drawer's A-Z column — honours the same preference as the dock's own A-Z row instead of keeping
     * a second copy of it.
     */
    public boolean isRowHapticsEnabled() {
        return rowHapticsEnabled;
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
        signature = (31 * signature) + DockIconCache.RENDER_PIPELINE_VERSION;
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

    /** Always-on glass-dock icon treatment, cached across resting and swipe-preview rendering. */
    @Nullable
    private Drawable iconForDisplay(@NonNull LauncherAppEntry entry, int sizePx) {
        return iconCache.icon(entry, sizePx);
    }

    /**
     * The same rendered, cached icon the dock draws, at an arbitrary size. Sharing the cache is the
     * point: keys already carry the pixel size, so a drawer cell and a dock icon of the same size
     * are literally the same drawable instance.
     */
    @Nullable
    public Drawable getRenderedIcon(@NonNull LauncherAppEntry entry, int sizePx) {
        return iconForDisplay(entry, sizePx);
    }

    /** Read-only budget shared by dock and drawer rendered icons. */
    public int getRenderedIconCacheBudgetBytes() {
        return iconCache.budgetBytes();
    }

    private View createEntryButton(@NonNull LauncherAppEntry entry) {
        NotificationBadgeFrame shell = new NotificationBadgeFrame(getContext(), notificationBadgeStyle);
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

    /**
     * Launches an entry on behalf of a surface the dock does not own (the app drawer grid).
     *
     * <p>Deliberately routed through {@link #launchEntryFromTouch} so the drawer inherits the whole
     * ladder — clone/work-profile branch, activity fallbacks, launch transition, ripple, usage
     * recording, popup dismissal — rather than a second, drifting copy of it.
     *
     * <p>It must <em>not</em> call {@code registerLaunchTarget}: those entries index the dock's
     * launch-animation targets by component, and a drawer cell registering itself would redirect the
     * dock's own return animation to a view that no longer exists once the drawer closes.
     *
     * @return true when the request was dispatched (the launch itself may still be deferred by the
     *     touch-launch animation delay).
     */
    public boolean launchEntryFromDrawer(@Nullable View sourceView, @Nullable LauncherAppEntry entry) {
        if (entry == null) return false;
        launchEntryFromTouch(sourceView != null ? sourceView : this, entry, lastTerminalView);
        return true;
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

    /**
     * Resolved pinned entries for an external dock surface (the landscape rail). Folders are
     * excluded — the rail has no popup surface to open them into.
     */
    @NonNull
    public List<LauncherAppEntry> getDockRailEntries() {
        List<PinnedItem> source = configRepository != null
            ? configRepository.loadPinnedItems() : pinnedItems;
        List<LauncherAppEntry> out = new ArrayList<>();
        for (LauncherAppEntry entry : entriesForPinnedItems(source)) {
            if (!"folder".equals(entry.appRef.packageName)) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Launches an entry on behalf of an external dock surface (the landscape rail). */
    public void launchEntryFromRail(@NonNull LauncherAppEntry entry, @Nullable View sourceView) {
        launchEntry(entry, null, sourceView, true);
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
            if (folderIndex >= 0) {
                removePinnedAt(folderIndex);
            } else {
                dissolveDrawerFolder(folder.id);
            }
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
        NotificationBadgeFrame root = new NotificationBadgeFrame(getContext(), notificationBadgeStyle);
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
            mini.setImageDrawable(getRenderedIcon(e, miniSize));
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
        if (folder.apps.size() > 4) {
            TextView overflow = new TextView(getContext());
            overflow.setText("+" + (folder.apps.size() - 3));
            overflow.setTextColor(Color.WHITE);
            overflow.setTextSize(8f);
            overflow.setGravity(Gravity.CENTER);
            overflow.setBackgroundColor(0xB8000000);
            FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(miniSize, miniSize,
                Gravity.END | Gravity.BOTTOM);
            badge.setMargins(0, 0, pinnedFolderMiniIconMarginPx(), pinnedFolderMiniIconMarginPx());
            iconShell.addView(overflow, badge);
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
        refreshPinnedItemsFromRepository();
        if (context.sourceFolderId == null || context.folderEntryRef == null) return;
        PinnedFolderItem folder = resolveLatestFolder(context.sourceFolderId);
        if (folder == null) return;
        PinnedAppItem folderApp = findFolderApp(folder, context.folderEntryRef);
        if (folderApp == null) return;
        showIconPackPicker(folderApp, override -> {
            refreshPinnedItemsFromRepository();
            PinnedFolderItem latest = resolveLatestFolder(context.sourceFolderId);
            if (latest == null) return;
            updateFolderAppIconOverride(latest, folderApp.appRef, override);
            persistPinsAndReload();
        });
    }

    private void resetFolderAppIcon(@NonNull AppMenuContext context) {
        refreshPinnedItemsFromRepository();
        if (context.sourceFolderId == null || context.folderEntryRef == null) return;
        PinnedFolderItem folder = resolveLatestFolder(context.sourceFolderId);
        if (folder != null && updateFolderAppIconOverride(folder, context.folderEntryRef, null)) {
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

    private void showFolderPopup(PinnedFolderItem folder, @Nullable View anchor) {
        showFolderPopup(folder, anchor, false);
    }

    private void showFolderPopup(PinnedFolderItem folder, @Nullable View anchor,
                                 boolean beginRename) {
        dismissFolderPopup();
        dismissAppContextPopup();
        dismissShortcutsPopup();

        LauncherConfigSnapshot snapshot = configRepository == null ? null
            : configRepository.loadSnapshot();
        PinnedFolderItem resolvedFolder = snapshot == null ? folder : snapshot.folder(folder.id);
        if (resolvedFolder == null) return;
        folder = resolvedFolder;
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
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int popupIconSize = computeFolderPopupIconSize(rows, cols, screenW, screenH);

        RecyclerView grid = new RecyclerView(getContext());
        grid.setLayoutManager(new GridLayoutManager(getContext(), cols));
        grid.setOverScrollMode(OVER_SCROLL_NEVER);
        grid.setItemAnimator(null);
        final String popupFolderId = folder.id;
        grid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
                FrameLayout holder = new FrameLayout(parent.getContext());
                holder.setLayoutParams(new RecyclerView.LayoutParams(popupIconSize + dp(8),
                    popupIconSize + dp(8)));
                return new RecyclerView.ViewHolder(holder) {};
            }

            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,
                                                   int position) {
                ViewGroup root = (ViewGroup) holder.itemView;
                root.removeAllViews();
                View button = createPopupEntryButton(folderEntries.get(position), popupIconSize,
                    popupFolderId);
                root.addView(button, new FrameLayout.LayoutParams(popupIconSize, popupIconSize,
                    Gravity.CENTER));
            }

            @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
                ((ViewGroup) holder.itemView).removeAllViews();
            }

            @Override public int getItemCount() { return folderEntries.size(); }
        });

        LinearLayout shell = menuRows.newShell();

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(2), dp(4), dp(4));

        FolderRenameTitleView title = new FolderRenameTitleView(getContext());
        title.setTextColor(getLauncherTextColor());
        title.bind(new FolderRenameModel(TextUtils.isEmpty(folder.title) ? "Folder" : folder.title),
            false);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        title.setClickable(true);
        title.setOnClickListener(v -> {
            LauncherConfigSnapshot latest = configRepository == null ? null
                : configRepository.loadSnapshot();
            PinnedFolderItem latestFolder = latest == null ? null : latest.folder(popupFolderId);
            if (folderRenameHost != null && latestFolder != null)
                folderRenameHost.beginFolderRename(latest.revision, popupFolderId,
                    latestFolder.title, title);
        });

        ImageButton gear = new ImageButton(getContext());
        gear.setImageResource(R.drawable.ic_settings);
        styleIconButton(gear, dp(3));
        int gearSize = dp(24);
        gear.setOnClickListener(v -> {
            dismissFolderPopup();
            refreshPinnedItemsFromRepository();
            PinnedFolderItem latestFolder = resolveLatestFolder(popupFolderId);
            if (latestFolder == null) return;
            // Drawer-only folders have no dock index (-1); the editor persists those through the
            // shared folder entity instead of a dock slot, so the cog must not require a pin.
            showFolderContentsEditor(findPinnedFolderIndex(latestFolder), latestFolder);
        });

        header.addView(title);
        header.addView(gear, new LinearLayout.LayoutParams(gearSize, gearSize));
        shell.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int visibleRows = Math.max(1, Math.min(rows,
            (int) Math.ceil(folderEntries.size() / (float) cols)));
        shell.addView(grid, new LinearLayout.LayoutParams(
            Math.min(screenW, cols * (popupIconSize + dp(8))),
            visibleRows * (popupIconSize + dp(8))));

        int overlayBase = folder.tintOverrideEnabled ? (folder.tintColor & 0x00FFFFFF) : (inheritedTintColor & 0x00FFFFFF);
        // The shared controller owns this window's lifecycle (spring transition, dim, dismissal),
        // so the module builds it detached and only lends its placement policy.
        folderPopupWindow = detachedMenuSurface.buildDetached(
            MenuSpec.of(shell, overlayBase).build());
        final PopupWindow shownPopup = folderPopupWindow;
        sharedFolderPopup.show(shownPopup, folder.id,
            () -> detachedMenuSurface.placeAtAnchor(shownPopup, anchor, false), () -> {
            cancelFolderRename();
            if (folderPopupWindow != null && !folderPopupWindow.isShowing()) {
                folderPopupWindow = null;
            }
            if (pendingDrawerConfigRefresh) notifyDrawerConfigChanged();
        });
        if (beginRename) title.post(title::performClick);
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
        cancelFolderRename();
        if (folderPopupWindow != null) {
            sharedFolderPopup.dismiss();
        }
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
        // A folder member has no dock slot to drag, so its pickup starts a folder-entry drag that
        // carries the member out of the folder instead of reordering a pinned index.
        Runnable folderEntryPickup = sourceFolder != null && folderEntryRef != null
            ? () -> startFolderEntryDrag(pressTarget, entry, sourceFolder.id)
            : null;
        bindContextLongPressGesture(pressTarget, pinnedIndex, allowDragPickup, () -> {
            dismissShortcutsPopup();
            showAppContextPopup(new AppMenuContext(entry, pressTarget, pinnedIndex,
                sourceFolder == null ? null : sourceFolder.id,
                folderEntryRef));
        }, notificationSwipeAction, entry.appRef.packageName, null, null, folderEntryPickup);
    }

    /**
     * Binds the dock's app context menu onto a view the dock does not own (an app drawer cell).
     *
     * <p>This delegates to the one gesture implementation rather than reproducing any part of it:
     * press-down animation, pickup state, slide-to-select, drag-back-to-cancel and the release
     * bounce all come from {@link #bindContextLongPressGesture}, which is already parameterised for
     * an unpinned, non-draggable target ({@code pinnedIndex = -1}, {@code allowDragPickup = false},
     * no notification-swipe action) — exactly the configuration the A-Z preview and folder-popup
     * icons use. {@code showAppContextPopup} recomputes the pinned index itself, so a drawer cell
     * for an already-pinned app still gets the Unpin shape.
     */
    public void bindDrawerAppContextLongPress(@NonNull View pressTarget, @NonNull LauncherAppEntry entry) {
        bindDrawerAppContextLongPress(pressTarget, entry, null);
    }

    public void bindDrawerAppContextLongPress(@NonNull View pressTarget,
                                              @NonNull LauncherAppEntry entry,
                                              @Nullable AppDrawerPickupDelegate pickupDelegate) {
        bindDrawerAppContextLongPress(pressTarget, entry, pickupDelegate, null);
    }

    /**
     * Same as the two-arg overload, but with a {@code categoryAction}: when non-null, the popup
     * this opens drops its Pin/Unpin row and gains a "Category" row that runs it instead — the
     * app-drawer categories grid's reassignment entry point, reusing this Material popup rather
     * than jumping straight to a category picker.
     */
    public void bindDrawerAppContextLongPress(@NonNull View pressTarget,
                                              @NonNull LauncherAppEntry entry,
                                              @Nullable AppDrawerPickupDelegate pickupDelegate,
                                              @Nullable Runnable categoryAction) {
        bindContextLongPressGesture(pressTarget, -1, false,
            () -> showDrawerAppContextPopup(pressTarget, entry, categoryAction),
            null, null, pickupDelegate, entry, null);
    }

    /** Shows the Material app-context popup anchored to a drawer view, outside the long-press gesture. */
    public void showDrawerAppContextPopup(@NonNull View anchor, @NonNull LauncherAppEntry entry,
                                          @Nullable Runnable categoryAction) {
        dismissShortcutsPopup();
        showAppContextPopup(new AppMenuContext(entry, anchor, -1, null, null, categoryAction));
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
        bindContextLongPressGesture(pressTarget, pinnedIndex, allowDragPickup, showContextPopup,
            notificationSwipeAction, notificationPackage, null, null, null);
    }

    private void bindContextLongPressGesture(
        @NonNull View pressTarget,
        int pinnedIndex,
        boolean allowDragPickup,
        @NonNull Runnable showContextPopup,
        @Nullable Runnable notificationSwipeAction,
        @Nullable String notificationPackage,
        @Nullable AppDrawerPickupDelegate drawerPickup,
        @Nullable LauncherAppEntry drawerPickupEntry,
        @Nullable Runnable folderEntryPickup
    ) {
        pressTarget.setLongClickable(true);
        pressTarget.setOnLongClickListener(v -> {
            if (suppressContextLongPressForSwipe) {
                return true;
            }
            if (drawerPickup != null && drawerPickupEntry != null
                && !drawerPickup.claimContext(pressTarget, drawerPickupEntry)) return true;
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
                        && (pinnedIndex >= 0 || folderEntryPickup != null);
                    if (drawerPickup != null) shouldStartPickup = withinPickupWindow
                        && !state.definitiveYMovement && absDx >= xPickupThreshold;

                    if (shouldStartPickup) {
                        state.dragStarted = true;
                        menuHighlight.clear();
                        dismissAppContextPopup();
                        if (drawerPickup != null && drawerPickupEntry != null) {
                            dismissFolderPopup();
                            drawerPickup.startPickup(pressTarget, drawerPickupEntry);
                        } else if (folderEntryPickup != null) {
                            // The folder popup owns this drag's source window: start the drag
                            // first, then hide (not dismiss) the popup so the window survives
                            // until the drag ends (see startFolderEntryDrag).
                            folderEntryPickup.run();
                        } else {
                            dismissFolderPopup();
                            startPinnedDrag(pressTarget, pinnedIndex);
                        }
                        activeLongPressPickupState = null;
                        return true;
                    }

                    // Drag-back-to-cancel: once the finger has slid up off the icon onto the menu,
                    // sliding back down onto the originating icon closes the menu without acting.
                    boolean overAnchor = AnchoredMenu.isRawInsideView(pressTarget, rawX, rawY);
                    if (!overAnchor) {
                        state.leftAnchor = true;
                    } else if (state.leftAnchor) {
                        menuHighlight.clear();
                        dismissAppContextPopup();
                        activeLongPressPickupState = null;
                        return true;
                    }

                    boolean highlighted = menuHighlight.updateForRaw(rawX, rawY, true, state.selectionArmed);
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
                            menuHighlight.updateForRaw(event.getRawX(), event.getRawY(), true, true);
                            menuHighlight.commitHighlighted();
                        }
                        activeLongPressPickupState = null;
                        return true;
                    }
                    if (action == MotionEvent.ACTION_CANCEL) {
                        menuHighlight.clear();
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
        PinnedFolderItem sourceFolder = resolveLatestFolder(context.sourceFolderId);
        boolean folderSource = sourceFolder != null && context.folderEntryRef != null;
        int topPinnedIndex = context.pinnedIndex >= 0 ? context.pinnedIndex : findPinnedAppIndex(context.entry.appRef);
        boolean topPinned = topPinnedIndex >= 0;
        // A category-context popup swaps its Pin/Unpin row for a Category row instead — see below.
        boolean suppressPinRow = context.categoryAction != null;

        LinearLayout shell = menuRows.newShell();
        pendingMenuRows.clear();
        menuHighlight.clear();
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

        int tintBase = sourceFolder != null && sourceFolder.tintOverrideEnabled
            ? (sourceFolder.tintColor & 0x00FFFFFF)
            : (inheritedTintColor & 0x00FFFFFF);
        menuHighlight.setTintBase(tintBase);

        TextView uninstallRow = menuRows.addActionRow(shell, "Uninstall", R.drawable.ic_dock_menu_uninstall, false, tintBase, () -> {
            dismissAppContextPopup();
            requestUninstall(context.entry);
        });
        pendingMenuRows.add(new MenuRow(uninstallRow, () -> {
            dismissAppContextPopup();
            requestUninstall(context.entry);
        }, false));

        TextView appInfoRow = menuRows.addActionRow(shell, "App info", R.drawable.ic_dock_menu_info, false, tintBase, () -> {
            dismissAppContextPopup();
            openAppInfo(context.entry);
        });
        pendingMenuRows.add(new MenuRow(appInfoRow, () -> {
            dismissAppContextPopup();
            openAppInfo(context.entry);
        }, false));

        if (folderSource) {
            PinnedAppItem folderApp = findFolderApp(sourceFolder, context.folderEntryRef);
            boolean folderHasCustomIcon = folderApp != null
                && getIconResolver().loadOverride(folderApp.iconOverride) != null;
            TextView changeIconRow = menuRows.addActionRow(shell, "Change icon in folder", R.drawable.ic_dock_menu_change_icon, false, tintBase, () -> {
                dismissAppContextPopup();
                changeFolderAppIcon(context);
            });
            pendingMenuRows.add(new MenuRow(changeIconRow, () -> {
                dismissAppContextPopup();
                changeFolderAppIcon(context);
            }, false));

            if (folderHasCustomIcon) {
                TextView resetIconRow = menuRows.addActionRow(shell, "Reset icon in folder", R.drawable.ic_dock_menu_reset, false, tintBase, () -> {
                    dismissAppContextPopup();
                    resetFolderAppIcon(context);
                });
                pendingMenuRows.add(new MenuRow(resetIconRow, () -> {
                    dismissAppContextPopup();
                    resetFolderAppIcon(context);
                }, false));
            }

            addAppWideIconRows(shell, context.entry, tintBase);

            TextView moveToDockRow = menuRows.addActionRow(shell, "Move to dock", R.drawable.ic_dock_menu_move, false, tintBase, () -> {
                dismissAppContextPopup();
                moveContextEntryToDock(context);
            });
            pendingMenuRows.add(new MenuRow(moveToDockRow, () -> {
                dismissAppContextPopup();
                moveContextEntryToDock(context);
            }, false));

            TextView deleteRow = menuRows.addActionRow(shell, "Delete", R.drawable.ic_dock_menu_uninstall, false, tintBase, () -> {
                dismissAppContextPopup();
                removeFromContextSource(context);
            });
            pendingMenuRows.add(new MenuRow(deleteRow, () -> {
                dismissAppContextPopup();
                removeFromContextSource(context);
            }, false));
        } else if (topPinned) {
            final int targetPinnedIndex = topPinnedIndex;
            PinnedAppItem topPinnedApp = pinnedAppAt(targetPinnedIndex);
            boolean pinnedHasCustomIcon = topPinnedApp != null
                && getIconResolver().loadOverride(topPinnedApp.iconOverride) != null;
            if (LauncherNotificationBadgeStore.hasBadge(context.entry.appRef.packageName)) {
                TextView notificationsRow = menuRows.addActionRow(shell, "Notifications", R.drawable.ic_dock_menu_info, false, tintBase, () -> {
                    dismissAppContextPopup();
                    showNotificationPopup(context.entry, context.anchor);
                });
                pendingMenuRows.add(new MenuRow(notificationsRow, () -> {
                    dismissAppContextPopup();
                    showNotificationPopup(context.entry, context.anchor);
                }, false));
            }
            TextView changeIconRow = menuRows.addActionRow(shell, "Change dock icon", R.drawable.ic_dock_menu_change_icon, false, tintBase, () -> {
                dismissAppContextPopup();
                PinnedAppItem pinnedApp = pinnedAppAt(targetPinnedIndex);
                if (pinnedApp != null) {
                    showPinnedIconPackPicker(targetPinnedIndex, pinnedApp);
                }
            });
            pendingMenuRows.add(new MenuRow(changeIconRow, () -> {
                dismissAppContextPopup();
                PinnedAppItem pinnedApp = pinnedAppAt(targetPinnedIndex);
                if (pinnedApp != null) {
                    showPinnedIconPackPicker(targetPinnedIndex, pinnedApp);
                }
            }, false));

            if (pinnedHasCustomIcon) {
                TextView resetIconRow = menuRows.addActionRow(shell, "Reset dock icon", R.drawable.ic_dock_menu_reset, false, tintBase, () -> {
                    dismissAppContextPopup();
                    PinnedAppItem pinnedApp = pinnedAppAt(targetPinnedIndex);
                    if (pinnedApp != null) {
                        resetPinnedIcon(targetPinnedIndex, pinnedApp);
                    }
                });
                pendingMenuRows.add(new MenuRow(resetIconRow, () -> {
                    dismissAppContextPopup();
                    PinnedAppItem pinnedApp = pinnedAppAt(targetPinnedIndex);
                    if (pinnedApp != null) {
                        resetPinnedIcon(targetPinnedIndex, pinnedApp);
                    }
                }, false));
            }

            addAppWideIconRows(shell, context.entry, tintBase);

            if (!suppressPinRow) {
                TextView unpinRow = menuRows.addActionRow(shell, "Unpin", R.drawable.ic_dock_menu_pin, false, tintBase, () -> {
                    dismissAppContextPopup();
                    removePinnedAt(targetPinnedIndex);
                });
                pendingMenuRows.add(new MenuRow(unpinRow, () -> {
                    dismissAppContextPopup();
                    removePinnedAt(targetPinnedIndex);
                }, false));
            }
        } else {
            if (!suppressPinRow) {
                TextView pinRow = menuRows.addActionRow(shell, "Pin", R.drawable.ic_dock_menu_pin, false, tintBase, () -> {
                    dismissAppContextPopup();
                    pinEntryToTopLevel(context.entry);
                });
                pendingMenuRows.add(new MenuRow(pinRow, () -> {
                    dismissAppContextPopup();
                    pinEntryToTopLevel(context.entry);
                }, false));
            }

            TextView changeIconRow = menuRows.addActionRow(shell, "Change app icon", R.drawable.ic_dock_menu_change_icon, false, tintBase, () -> {
                dismissAppContextPopup();
                changeAppIconForEntry(context.entry);
            });
            pendingMenuRows.add(new MenuRow(changeIconRow, () -> {
                dismissAppContextPopup();
                changeAppIconForEntry(context.entry);
            }, false));
            addResetAppIconRowIfNeeded(shell, context.entry, tintBase);
        }

        if (context.categoryAction != null) {
            Runnable categoryAction = context.categoryAction;
            TextView categoryRow = menuRows.addActionRow(shell,
                getResources().getString(R.string.app_drawer_category_menu_entry),
                R.drawable.ic_dock_menu_category, false, tintBase, () -> {
                    dismissAppContextPopup();
                    categoryAction.run();
                });
            pendingMenuRows.add(new MenuRow(categoryRow, () -> {
                dismissAppContextPopup();
                categoryAction.run();
            }, false));
        }

        if (hasShortcuts) {
            menuRows.addDivider(shell);
            TextView shortcutsRow = menuRows.addActionRow(shell, "Shortcuts", R.drawable.ic_dock_menu_shortcuts, true, tintBase, () -> {
                if (shortcutsMenu.isShowing()) {
                    dismissShortcutsPopup();
                    menuHighlight.clear();
                } else {
                    showShortcutsPopup(context, shortcuts, shortcutsMainRowView);
                }
            });
            shortcutsMainRowView = shortcutsRow;
            pendingMenuRows.add(new MenuRow(shortcutsRow, () -> {
                if (!shortcutsMenu.isShowing()) {
                    showShortcutsPopup(context, shortcuts, shortcutsMainRowView);
                }
            }, true));
        }

        int rowWidth = MenuRowWidths.normalize(pendingMenuRows);
        int contentWidth = MenuRowWidths.constrainHeader(header, rowWidth);
        MenuRowWidths.constrainRows(pendingMenuRows, contentWidth);

        appContextMenu.show(MenuSpec.of(shell, tintBase).rows(pendingMenuRows).build(),
            context.anchor);
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

        LinearLayout shell = menuRows.newShell();
        pendingMenuRows.clear();
        menuHighlight.clear();
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
        menuHighlight.setTintBase(tintBase);

        TextView renameRow = menuRows.addActionRow(shell, "Rename", tintBase, () -> {
            dismissAppContextPopup();
            showFolderPopup(folder, anchor, true);
        });
        pendingMenuRows.add(new MenuRow(renameRow, () -> {
            dismissAppContextPopup();
            showFolderPopup(folder, anchor, true);
        }, false));

        TextView chooseAppsRow = menuRows.addActionRow(shell, "Choose apps", tintBase, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                showFolderContentsEditor(folderIndex, folder);
            }
        });
        pendingMenuRows.add(new MenuRow(chooseAppsRow, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                showFolderContentsEditor(folderIndex, folder);
            }
        }, false));

        TextView deleteRow = menuRows.addActionRow(shell, "Delete", tintBase, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                removePinnedAt(folderIndex);
            }
        });
        pendingMenuRows.add(new MenuRow(deleteRow, () -> {
            dismissAppContextPopup();
            int folderIndex = pinnedIndex >= 0 ? pinnedIndex : findPinnedFolderIndex(folder);
            if (folderIndex >= 0) {
                removePinnedAt(folderIndex);
            }
        }, false));

        int rowWidth = MenuRowWidths.normalize(pendingMenuRows);
        int contentWidth = MenuRowWidths.constrainHeader(header, rowWidth);
        MenuRowWidths.constrainRows(pendingMenuRows, contentWidth);

        appContextMenu.show(MenuSpec.of(shell, tintBase).rows(pendingMenuRows).build(), anchor);
    }

    private void showShortcutsPopup(@NonNull AppMenuContext context, @NonNull List<ShortcutInfo> shortcuts, @Nullable View shortcutsRowAnchor) {
        dismissShortcutsPopup();
        if (shortcuts.isEmpty()) return;

        LinearLayout shell = menuRows.newShell();
        List<MenuRow> shortcutRows = new ArrayList<>();

        for (ShortcutInfo info : shortcuts) {
            String label = info.getShortLabel() != null ? info.getShortLabel().toString() : info.getId();
            final TextView[] shortcutRowHolder = new TextView[1];
            TextView shortcutRow = menuRows.addActionRow(shell, label, menuHighlight.tintBase(), () -> {
                launchShortcut(info, shortcutRowHolder[0]);
                dismissAppContextPopup();
            });
            shortcutRowHolder[0] = shortcutRow;
            shortcutRows.add(new MenuRow(shortcutRow, () -> {
                launchShortcut(info, shortcutRowHolder[0]);
                dismissAppContextPopup();
            }, false));
        }
        MenuRowWidths.normalize(shortcutRows);

        PinnedFolderItem sourceFolder = resolveLatestFolder(context.sourceFolderId);
        int tintBase = sourceFolder != null && sourceFolder.tintOverrideEnabled
            ? (sourceFolder.tintColor & 0x00FFFFFF)
            : (inheritedTintColor & 0x00FFFFFF);
        MenuSpec spec = MenuSpec.of(shell, tintBase).rows(shortcutRows).build();
        if (shortcutsRowAnchor != null && appContextMenu.isShowing()) {
            shortcutsMenu.showAlignedToRow(spec, shortcutsRowAnchor, appContextMenu);
        } else {
            shortcutsMenu.show(spec, context.anchor);
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
        if (context.sourceFolderId != null && context.folderEntryRef != null) {
            dismissFolderPopup();
            refreshPinnedItemsFromRepository();
            PinnedFolderItem folder = resolveLatestFolder(context.sourceFolderId);
            if (folder != null) {
                removeAppFromFolder(folder, context.folderEntryRef);
                persistPinsAndReload();
            }
        }
    }

    private void moveContextEntryToDock(@NonNull AppMenuContext context) {
        if (context.sourceFolderId == null || context.folderEntryRef == null) {
            pinEntryToTopLevel(context.entry);
            return;
        }

        dismissFolderPopup();
        refreshPinnedItemsFromRepository();
        PinnedFolderItem sourceFolder = resolveLatestFolder(context.sourceFolderId);
        if (sourceFolder == null) return;
        AppRef resolved = resolveForSelectionRef(context.folderEntryRef);
        PinnedAppItem folderApp = findFolderApp(sourceFolder, resolved);
        int existingPinnedIndex = findPinnedAppIndex(resolved);
        int sourceFolderIndex = findPinnedFolderIndex(sourceFolder);
        removeAppFromFolder(sourceFolder, resolved);
        if (existingPinnedIndex >= 0) {
            persistPinsAndReload();
            return;
        }

        int survivingFolderIndex = findPinnedFolderIndex(sourceFolder);
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

    @Nullable
    private PinnedFolderItem resolveLatestFolder(@Nullable String folderId) {
        if (folderId == null) return null;
        if (configRepository != null) {
            PinnedFolderItem folder = configRepository.loadSnapshot().folder(folderId);
            if (folder != null) return folder;
        }
        for (PinnedItem item : pinnedItems) {
            if (item instanceof PinnedFolderItem
                && folderId.equals(((PinnedFolderItem) item).id)) return (PinnedFolderItem) item;
        }
        return null;
    }

    private void refreshPinnedItemsFromRepository() {
        if (configRepository != null) pinnedItems = configRepository.loadPinnedItems();
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
        TextView change = menuRows.addActionRow(shell, "Change app icon", R.drawable.ic_dock_menu_change_icon, false, tintBase, () -> {
            dismissAppContextPopup();
            changeAppIconForEntry(entry);
        });
        pendingMenuRows.add(new MenuRow(change, () -> {
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
        TextView reset = menuRows.addActionRow(shell, "Reset app icon", R.drawable.ic_dock_menu_reset, false, tintBase, () -> {
            dismissAppContextPopup();
            resetAppIcon(entry);
        });
        pendingMenuRows.add(new MenuRow(reset, () -> {
            dismissAppContextPopup();
            resetAppIcon(entry);
        }, false));
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

    /**
     * Opens the shortcuts menu because the finger came to rest on the row that owns it. The tracker
     * knows the row wants a submenu; only this view knows what goes in it.
     */
    private void openShortcutsForFocusedRow() {
        if (activeAppMenuContext != null && activeAppMenuShortcuts != null
            && !activeAppMenuShortcuts.isEmpty()) {
            showShortcutsPopup(activeAppMenuContext, activeAppMenuShortcuts, shortcutsMainRowView);
        }
    }

    private void dismissAppContextPopup() {
        dismissShortcutsPopup();
        menuHighlight.clear();
        pendingMenuRows.clear();
        activeAppMenuContext = null;
        activeAppMenuShortcuts = null;
        shortcutsMainRowView = null;
        appContextMenu.dismiss();
    }

    private void dismissShortcutsPopup() {
        menuHighlight.clear();
        shortcutsMenu.dismiss();
    }

    /**
     * Closes every context surface anchored to a launcher icon. Surfaces outside this view (the app
     * drawer) call this when their own anchors go away — a recycled or scrolled-away cell would
     * otherwise leave a menu floating over nothing.
     */
    public void dismissContextPopups() {
        // The drawer calls this from onScrolled, i.e. on every frame of every scroll. With no
        // surface showing there is nothing to dismiss, and the individual dismissers below do
        // unconditional state-clearing work even then.
        if (appContextMenu.window() == null && folderPopupWindow == null
            && shortcutsMenu.window() == null && categoryPickerMenu.window() == null) {
            return;
        }
        dismissAppContextPopup();
        dismissFolderPopup();
        dismissShortcutsPopup();
        dismissCategoryPickerPopup();
    }

    /**
     * The Material popup listing every non-synthetic {@link AppDrawerCategory} plus "Automatic",
     * opened from the "Category" row of the drawer app-context popup. Replaces the old
     * {@code AlertDialog}-based picker with the same glass/blur shell every other launcher popup uses.
     */
    public void showCategoryPickerPopup(
        @NonNull LauncherAppEntry entry,
        @NonNull View anchor,
        @NonNull List<AppDrawerCategory> categories,
        @Nullable AppDrawerCategory current,
        @NonNull java.util.function.Consumer<AppDrawerCategory> onPick
    ) {
        dismissContextPopups();
        LinearLayout shell = menuRows.newShell();

        TextView header = new TextView(getContext());
        header.setText(entry.label);
        header.setTextColor(resolveLauncherTextColor());
        header.setTextSize(12f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(6), dp(8), dp(7));
        shell.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int tintBase = inheritedTintColor & 0x00FFFFFF;
        List<MenuRow> rows = new ArrayList<>();
        rows.add(new MenuRow(addCategoryPickRow(shell,
            getResources().getString(R.string.app_drawer_category_automatic), current == null, tintBase, () -> {
                dismissCategoryPickerPopup();
                onPick.accept(null);
            }), () -> {}));
        for (AppDrawerCategory category : categories) {
            rows.add(new MenuRow(addCategoryPickRow(shell,
                getResources().getString(category.labelRes), category == current, tintBase, () -> {
                    dismissCategoryPickerPopup();
                    onPick.accept(category);
                }), () -> {}));
        }

        int rowWidth = MenuRowWidths.normalize(rows);
        MenuRowWidths.constrainHeader(header, rowWidth);

        categoryPickerMenu.show(MenuSpec.of(shell, tintBase).rows(rows).build(), anchor);
    }

    @NonNull
    private TextView addCategoryPickRow(@NonNull LinearLayout shell, @NonNull String title,
                                        boolean checked, int tintBase, @NonNull Runnable action) {
        return menuRows.addCheckableRow(shell, title, R.drawable.ic_symbol_check_circle, checked,
            tintBase, action);
    }

    private void dismissCategoryPickerPopup() {
        categoryPickerMenu.dismiss();
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

        NotificationCardSurface.Content content = notificationCards.buildContent(
            entry.label, iconForDisplay(entry, dp(24)), notifications);

        // Notification content must remain readable even when the dock itself is configured as
        // nearly transparent. Keep the Material surface hue and let blur provide the glass feel.
        int tintBase = resolveLauncherPanelColor() & 0x00FFFFFF;
        final PopupWindow[] holder = new PopupWindow[1];
        // Detached: this window is focusable from creation, carries its own dim, and its dismiss
        // bookkeeping spans two fields, so the view keeps the reference and the module only builds,
        // places and animates it away.
        notificationPopupWindow = detachedMenuSurface.buildDetached(
            MenuSpec.of(content.shell, tintBase)
                .tightWrap(false)
                .width(notificationCards.preferredWidth(notifications))
                .minimumOpacityPercent(88)
                .verticalScrollbar(false)
                .onDismiss(() -> {
                    if (notificationPopupWindow == holder[0]) {
                        notificationPopupWindow = null;
                        notificationPopupPackage = null;
                        notificationPopupKeys = Collections.emptySet();
                        notificationReplyEditor = null;
                    }
                    if (notificationInteractionPopup == holder[0]) {
                        notificationInteractionPopup = null;
                        if (notificationPopupInteractionListener != null)
                            notificationPopupInteractionListener.onNotificationPopupDismissed();
                    }
                })
                .build());
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
        // The window is focusable from creation (see setFocusable above — retrofitting focus with
        // PopupWindow.update() can leave a focused EditText whose window is never registered as the
        // IME target). The handoff that follows removes the embedded keyboard before an inline reply
        // asks for IME focus.
        detachedMenuSurface.placeAtAnchor(notificationPopupWindow, anchor, true);
        applyNotificationPopupDim(notificationPopupWindow);
        if (notificationPopupInteractionListener != null)
            notificationPopupInteractionListener.onNotificationPopupShown();
        // Swiping a pinned icon means "reply to this app", so open a composer whenever there is
        // one to open. getNotificationsForPackage sorts newest-first and each card keeps only its
        // first free-form action, so target 0 is the latest conversation — which is what the gesture
        // means. The card is highlighted and scrolled to, so which one it picked is never a mystery.
        NotificationCardSurface.ReplyTarget finalAutoReplyTarget = shouldAutoOpenNotificationReply(
            content.replyTargets.size()) ? content.replyTargets.get(0) : null;
        if (finalAutoReplyTarget != null) notificationCards.highlightReplyCard(finalAutoReplyTarget);
        if (finalAutoReplyTarget != null) {
            post(() -> {
                if (notificationPopupWindow != holder[0] || !holder[0].isShowing()) return;
                notificationCards.beginReply(finalAutoReplyTarget);
            });
        }
    }

    /** @see NotificationCardSurface#shouldAutoOpenReply(int) */
    static boolean shouldAutoOpenNotificationReply(int replyTargetCount) {
        return NotificationCardSurface.shouldAutoOpenReply(replyTargetCount);
    }

    /** @see NotificationCardSurface#shouldDismissOnKeyChange(boolean, boolean) */
    static boolean shouldDismissNotificationPopupOnKeyChange(boolean keysChanged,
                                                            boolean composing) {
        return NotificationCardSurface.shouldDismissOnKeyChange(keysChanged, composing);
    }

    /** @see NotificationCardSurface#adaptiveWidth(int, int, int, int) */
    static int adaptiveNotificationPopupWidth(
        int preferredWidth,
        int requiredActionWidth,
        int minimumWidth,
        int maximumWidth
    ) {
        return NotificationCardSurface.adaptiveWidth(
            preferredWidth, requiredActionWidth, minimumWidth, maximumWidth);
    }

    /** Composing means the reply field has focus or already holds text. */
    private boolean isComposingReply() {
        EditText editor = notificationReplyEditor;
        if (editor == null) return false;
        return editor.isFocused() || !TextUtils.isEmpty(editor.getText());
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

    /**
     * The IME side of an inline reply: only this window's owner can flip its soft-input mode and
     * chase window focus, so the card surface asks for it through its listener.
     */
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

    private void dismissNotificationPopup() {
        if (notificationPopupWindow == null) return;
        PopupWindow popup = notificationPopupWindow;
        notificationPopupWindow = null;
        notificationPopupPackage = null;
        notificationPopupKeys = Collections.emptySet();
        notificationReplyEditor = null;
        detachedMenuSurface.dismissAnimated(popup, null);
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

    private static final class AppMenuContext {
        final LauncherAppEntry entry;
        final View anchor;
        final int pinnedIndex;
        @Nullable final String sourceFolderId;
        @Nullable final AppRef folderEntryRef;
        @Nullable final Runnable categoryAction;

        AppMenuContext(
            @NonNull LauncherAppEntry entry,
            @NonNull View anchor,
            int pinnedIndex,
            @Nullable String sourceFolderId,
            @Nullable AppRef folderEntryRef
        ) {
            this(entry, anchor, pinnedIndex, sourceFolderId, folderEntryRef, null);
        }

        AppMenuContext(
            @NonNull LauncherAppEntry entry,
            @NonNull View anchor,
            int pinnedIndex,
            @Nullable String sourceFolderId,
            @Nullable AppRef folderEntryRef,
            @Nullable Runnable categoryAction
        ) {
            this.entry = entry;
            this.anchor = anchor;
            this.pinnedIndex = pinnedIndex;
            this.sourceFolderId = sourceFolderId;
            this.folderEntryRef = folderEntryRef;
            this.categoryAction = categoryAction;
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
            applyDrawerFolderSelection(folder, selectedApps);
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

    /**
     * Persists an edit to a drawer-only folder (one with no dock slot) by mutating the shared
     * snapshot entity: {@code savePinnedItems} retains drawer entities and its normalize pass
     * collapses folders left with fewer than two members.
     */
    private void applyDrawerFolderSelection(@NonNull PinnedFolderItem folder, @NonNull List<PinnedAppItem> selectedApps) {
        if (configRepository == null) return;
        PinnedFolderItem entity = resolveLatestFolder(folder.id);
        if (entity == null) return;
        entity.apps.clear();
        entity.apps.addAll(selectedApps);
        persistPinsAndReload();
    }

    /** Deletes a drawer-only folder entity; the repository publish recomposes dock and drawer. */
    private void dissolveDrawerFolder(@NonNull String folderId) {
        if (configRepository == null) return;
        configRepository.dissolveFolder(configRepository.loadSnapshot().revision, folderId);
    }


    private void showFolderAppendRejected(@NonNull LauncherFolderMutator.AppendResult result) {
        int message = result == LauncherFolderMutator.AppendResult.CAPACITY
            ? R.string.folder_capacity_reached : R.string.folder_already_contains_app;
        AppNotice.show(getContext(), message, false);
    }

    private static void syncFolderChecks(@NonNull ListView listView, @NonNull List<LauncherAppEntry> apps, @NonNull Set<String> selectedIds) {
        for (int i = 0; i < apps.size(); i++) {
            listView.setItemChecked(i, selectedIds.contains(apps.get(i).appRef.stableId()));
        }
    }

    private boolean startPinnedDrag(@NonNull View view, int sourceIndex) {
        ClipData clip = ClipData.newPlainText("pinned-item", Integer.toString(sourceIndex));
        PinnedDragState dragState = new PinnedDragState(sourceIndex);
        Drawable ghost = renderedDragGhost(sourceIndex, Math.max(1, iconSizePx()));
        if (ghost == null) return false;
        View.DragShadowBuilder shadow = createRaisedDragShadow(ghost, Math.max(1, iconSizePx()));
        activePinnedDragSourceView = view;
        activePinnedDragState = dragState;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.startDragAndDrop(clip, shadow, dragState, 0);
        } else {
            view.startDrag(clip, shadow, dragState, 0);
        }
        return true;
    }

    /**
     * Window-level drag out of the folder popup, mirroring {@link #startPinnedDrag}: the drag
     * carries the member's folder id + ref instead of a dock index. The popup is hidden — not
     * dismissed — so the system drag keeps its source window; the dock's drag listener finishes
     * the dismissal on ACTION_DRAG_ENDED.
     */
    private boolean startFolderEntryDrag(@NonNull View view, @NonNull LauncherAppEntry entry,
                                         @NonNull String sourceFolderId) {
        int sizePx = Math.max(1, iconSizePx());
        Drawable ghost = getRenderedIcon(entry, sizePx);
        if (ghost == null) return false;
        FolderEntryDragState dragState = new FolderEntryDragState(sourceFolderId,
            resolveForSelectionRef(entry.appRef));
        ClipData clip = ClipData.newPlainText(FOLDER_ENTRY_CLIP_LABEL, dragState.appRef.stableId());
        View.DragShadowBuilder shadow = createRaisedDragShadow(ghost, sizePx);
        activePinnedDragSourceView = view;
        activeFolderEntryDragState = dragState;
        boolean started;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            started = view.startDragAndDrop(clip, shadow, dragState, crossWindowDragFlags());
        } else {
            started = view.startDrag(clip, shadow, dragState, 0);
        }
        if (!started) {
            activePinnedDragSourceView = null;
            activeFolderEntryDragState = null;
            return false;
        }
        sharedFolderPopup.hideForDrag();
        return true;
    }

    @NonNull
    private Drawable renderedDragGhost(int sourceIndex, int sizePx) {
        if (sourceIndex < 0 || sourceIndex >= pinnedItems.size()) return null;
        PinnedItem item = pinnedItems.get(sourceIndex);
        PinnedAppItem app = item instanceof PinnedAppItem ? (PinnedAppItem) item
            : item instanceof PinnedFolderItem && !((PinnedFolderItem) item).apps.isEmpty()
                ? ((PinnedFolderItem) item).apps.get(0) : null;
        LauncherAppEntry entry = app == null ? null : resolvePinnedApp(app);
        return entry == null ? null : getRenderedIcon(entry, sizePx);
    }

    @NonNull
    private View.DragShadowBuilder createRaisedDragShadow(@NonNull Drawable drawable, int sizePx) {
        return new View.DragShadowBuilder() {
            @Override
            public void onProvideShadowMetrics(@NonNull Point outShadowSize, @NonNull Point outShadowTouchPoint) {
                outShadowSize.set(sizePx, sizePx);
                outShadowTouchPoint.set(sizePx / 2, Math.min(sizePx - 1, Math.round(sizePx * 0.86f)));
            }

            @Override public void onDrawShadow(@NonNull Canvas canvas) {
                Rect previous = new Rect(drawable.getBounds());
                drawable.setBounds(0, 0, sizePx, sizePx);
                drawable.draw(canvas);
                drawable.setBounds(previous);
            }
        };
    }

    /** Invalidates pickup/local state before the dock row is hidden. */
    public void cancelActiveDockDrag() {
        PinnedDragState state = activePinnedDragState;
        if (state != null) state.cancelled = true;
        FolderEntryDragState folderState = activeFolderEntryDragState;
        if (folderState != null) {
            folderState.cancelled = true;
            sharedFolderPopup.dismissImmediate();
        }
        View source = activePinnedDragSourceView;
        if (source != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) source.cancelDragAndDrop();
        activePinnedDragSourceView = null;
        activePinnedDragState = null;
        activeFolderEntryDragState = null;
        activeLongPressPickupState = null;
        clearFolderDragInsertionPreview();
    }

    private boolean handlePinnedBarDragEvent(@NonNull View targetView, @NonNull DragEvent event) {
        Object localState = event.getLocalState();
        boolean pinnedDrag = localState instanceof PinnedDragState;
        FolderEntryDragState folderEntry = resolveFolderEntryDrag(event);
        boolean folderEntryDrag = folderEntry != null;
        if (!pinnedDrag && !folderEntryDrag) return false;

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
                if (folderEntryDrag) {
                    return applyFolderEntryDrop(folderEntry, targetIndex, targetItem, dropXRatio);
                }
                return applyPinnedDrop((PinnedDragState) localState, targetIndex, targetItem, dropXRatio);
            case DragEvent.ACTION_DRAG_ENDED:
                targetView.setAlpha(1f);
                clearFolderDragInsertionPreview();
                // A folder-entry drag left its source popup hidden but alive; finish it now.
                if (folderEntryDrag) sharedFolderPopup.dismissImmediate();
                activePinnedDragSourceView = null;
                activePinnedDragState = null;
                activeFolderEntryDragState = null;
                return true;
            default:
                return false;
        }
    }

    private boolean applyPinnedDrop(@NonNull PinnedDragState dragState, int targetIndex, @Nullable PinnedItem targetItem, float dropXRatio) {
        if (dragState.cancelled) return false;
        if (dragState.sourceIndex < 0 || dragState.sourceIndex >= pinnedItems.size()) return false;
        if (targetIndex < 0 || targetIndex > pinnedItems.size()) return false;

        PinnedItem sourceItem = pinnedItems.get(dragState.sourceIndex);
        boolean sourceIsApp = sourceItem instanceof PinnedAppItem;
        PinnedAppItem sourceApp = sourceIsApp ? (PinnedAppItem) sourceItem : null;
        AppRef sourceRef = sourceApp == null ? null : resolveForSelectionRef(sourceApp.appRef);

        if (sourceIsApp && targetItem instanceof PinnedFolderItem) {
            PinnedFolderItem folder = (PinnedFolderItem) targetItem;
            LauncherFolderMutator.AppendResult result = sourceApp == null
                ? LauncherFolderMutator.AppendResult.MISSING
                : LauncherFolderMutator.moveTopLevelAppIntoFolder(pinnedItems,
                    dragState.sourceIndex, folder, sourceApp);
            if (result != LauncherFolderMutator.AppendResult.APPLIED) {
                showFolderAppendRejected(result);
                return false;
            }
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

    /**
     * Applies a folder-member drop onto the dock row: the member leaves its folder and lands on
     * the hovered slot (top-level insert, or append when the slot holds another folder). Dropping
     * back onto the source folder's own tile cancels. Folder collapse (0/1 members left) rides
     * the repository's normalize pass during {@link #persistPinsAndReload}.
     */
    private boolean applyFolderEntryDrop(@NonNull FolderEntryDragState dragState, int targetIndex,
                                         @Nullable PinnedItem targetItem, float dropXRatio) {
        if (dragState.cancelled) return false;
        PinnedFolderItem sourceFolder = resolveLatestFolder(dragState.folderId);
        PinnedAppItem member = findFolderApp(sourceFolder, dragState.appRef);
        if (sourceFolder == null || member == null) return false;
        if (targetItem instanceof PinnedFolderItem
            && sourceFolder.id.equals(((PinnedFolderItem) targetItem).id)) {
            return false;
        }
        AppRef resolved = resolveForSelectionRef(member.appRef);
        if (targetItem instanceof PinnedFolderItem) {
            PinnedFolderItem destination = (PinnedFolderItem) targetItem;
            if (destination.containsApp(resolved)) {
                showFolderAppendRejected(LauncherFolderMutator.AppendResult.DUPLICATE);
                return false;
            }
            if (destination.apps.size() >= PinnedFolderItem.MAX_APPS) {
                showFolderAppendRejected(LauncherFolderMutator.AppendResult.CAPACITY);
                return false;
            }
            destination.apps.add(new PinnedAppItem(resolved, member.iconOverride));
            removeAppFromFolder(sourceFolder, resolved);
            persistPinsAndReload();
            return true;
        }
        if (findPinnedAppIndex(resolved) >= 0) {
            // Already docked top-level: leaving the folder must not create a duplicate slot.
            removeAppFromFolder(sourceFolder, resolved);
            persistPinsAndReload();
            return true;
        }
        int insertionIndex = computeInsertionIndex(targetIndex, targetItem, dropXRatio);
        LauncherFolderMutator.AppendResult result = LauncherFolderMutator.moveFolderAppToTopLevel(
            pinnedItems, sourceFolder, member.appRef.stableId(), insertionIndex);
        if (result != LauncherFolderMutator.AppendResult.APPLIED) return false;
        persistPinsAndReload();
        return true;
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
        boolean cancelled;

        PinnedDragState(int sourceIndex) {
            this.sourceIndex = sourceIndex;
        }
    }

    /**
     * The folder member leaves a {@link android.widget.PopupWindow}, and a plain (window-local)
     * system drag is delivered only to the window it started in — so the dock row and the app
     * drawer, which live in the activity window, never saw the drop. These flags let the drag cross
     * our own windows; below API 34, where the same-application flag does not exist, the global
     * flag is the only way to leave the popup window at all.
     */
    private static int crossWindowDragFlags() {
        if (Build.VERSION.SDK_INT >= 34) return View.DRAG_FLAG_GLOBAL_SAME_APPLICATION;
        return View.DRAG_FLAG_GLOBAL;
    }

    /**
     * A cross-window drag carries its local state only inside the source window, so the dock and
     * drawer identify a folder-member drag by clip label plus the live drag this view owns.
     *
     * @return the in-flight folder-member drag this event belongs to, or null when the event is
     *     some other drag.
     */
    @Nullable
    private FolderEntryDragState resolveFolderEntryDrag(@NonNull DragEvent event) {
        Object localState = event.getLocalState();
        if (localState instanceof FolderEntryDragState) return (FolderEntryDragState) localState;
        FolderEntryDragState active = activeFolderEntryDragState;
        if (active == null || active.cancelled) return null;
        ClipDescription description = event.getClipDescription();
        CharSequence label = description == null ? null : description.getLabel();
        return label != null && FOLDER_ENTRY_CLIP_LABEL.contentEquals(label) ? active : null;
    }

    /** True while this event belongs to a member being dragged out of the shared folder popup. */
    public boolean isFolderEntryDrag(@NonNull DragEvent event) {
        return resolveFolderEntryDrag(event) != null;
    }

    /**
     * Drops a folder member onto the app drawer: it leaves the folder and reappears in the drawer's
     * plain list, since the drawer composer suppresses exactly the apps a folder holds. A folder
     * left with fewer than two members collapses in the repository's normalize pass.
     *
     * @return true when the member was removed, so the drawer can consume the drop.
     */
    public boolean dropFolderEntryOnDrawer(@NonNull DragEvent event) {
        FolderEntryDragState dragState = resolveFolderEntryDrag(event);
        if (dragState == null || configRepository == null) return false;
        LauncherConfigSnapshot snapshot = configRepository.loadSnapshot();
        PinnedFolderItem folder = snapshot.folder(dragState.folderId);
        PinnedAppItem member = findFolderApp(folder, dragState.appRef);
        if (folder == null || member == null) return false;
        LauncherConfigRepository.MutationResult result = configRepository.removeAppFromFolder(
            snapshot.revision, folder.id, member.appRef.stableId());
        if (result != LauncherConfigRepository.MutationResult.APPLIED) return false;
        dragState.cancelled = true;
        activeFolderEntryDragState = null;
        activePinnedDragSourceView = null;
        // The popup is only hidden while a drag is in flight, and the dock's own drag listener —
        // the usual place that finishes it — may be hidden behind the open drawer.
        sharedFolderPopup.dismissImmediate();
        refreshPinnedItemsFromRepository();
        return true;
    }

    /** Local drag state for a member dragged out of a folder popup. */
    private static final class FolderEntryDragState {
        @NonNull final String folderId;
        @NonNull final AppRef appRef;
        boolean cancelled;

        FolderEntryDragState(@NonNull String folderId, @NonNull AppRef appRef) {
            this.folderId = folderId;
            this.appRef = appRef;
        }
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
        int targetPage = DockPagingModel.wrap(pinnedPageIndex + pageDelta, totalPages);
        if (targetPage == pinnedPageIndex) return;

        performPinnedPageTransitionHaptic(targetPage);
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

    private void performPinnedPageTransitionHaptic(int targetPage) {
        if (!rowHapticsEnabled)
            return;
        performHapticFeedback(pinnedPageTransitionHaptic(
            isMostUsedDynamicPage(targetPage), Build.VERSION.SDK_INT));
    }

    static int pinnedPageTransitionHaptic(boolean mostUsedPage, int sdkInt) {
        if (mostUsedPage) {
            return sdkInt >= Build.VERSION_CODES.R
                ? android.view.HapticFeedbackConstants.GESTURE_END
                : android.view.HapticFeedbackConstants.CONTEXT_CLICK;
        }
        return sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ? android.view.HapticFeedbackConstants.SEGMENT_TICK
            : android.view.HapticFeedbackConstants.CLOCK_TICK;
    }

    private void animateAzPageSwitch(int pageDelta, float velocityPxPerSec) {
        if (pageSwitchAnimating) return;
        int totalPages = getAzPagesCount();
        if (totalPages <= 1) return;
        int targetPage = DockPagingModel.wrap(activeAzPageIndex + pageDelta, totalPages);

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
        int pageDelta = DockPagingModel.dragPageDelta(dx);
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
        float easedProgress = DockPagingModel.dragEasedProgress(dx, resolvePageSwipeCommitDistancePx());

        swipePageDragging = true;
        swipeVisualOffsetX = DockPagingModel.dragVisualOffsetPx(dx, getWidth(), density());
        swipeDragProgress = easedProgress;
        prepareSwipePagePreview(pageDelta);

        float base = activeAzLetter != null ? getAzCurrentPageIndex() : getPinnedCurrentPageIndex();
        int pageCount = activeAzLetter != null ? getAzPagesCount() : getPinnedPagesCount();
        swipePagePosition = DockPagingModel.dragPagePosition(base, dx, easedProgress, pageCount);
        notifyOverflowPagePositionChanged();
        invalidate();
    }

    private float resolvePageSwipeCommitDistancePx() {
        return DockPagingModel.commitDistancePx(getWidth(), density());
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
        return DockPagingModel.hasOverflowPages(getPinnedPagesCount());
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
            return DockPagingModel.hasOverflowPages(totalPages)
                ? DockPagingModel.wrap(activeAzPageIndex + pageDelta, totalPages) : -1;
        }
        if (!hasPinnedOverflowPages()) {
            return -1;
        }
        return DockPagingModel.wrap(pinnedPageIndex + pageDelta, getPinnedPagesCount());
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

    /**
     * The page commit, wrapped so it runs exactly once from whichever path the switch animation
     * ends on — its own end, or a cancel.
     *
     * <p>Both switch animations used to commit the new page only from their end callback, and both
     * drop that callback when cancelled: {@code ACTION_DOWN} on the row, a stable-draw release and
     * {@link #resetTransientVisualState()} all cancel a switch that is still settling. The gesture
     * had already qualified and the row had already played the whole slide, so the swipe looked
     * committed and then silently landed back on the page it came from — the ghost swipe. A
     * qualified swipe is a decision; the animation is only how it is shown.
     */
    @NonNull
    private static Runnable pageCommitOnce(@Nullable Runnable commit) {
        final boolean[] done = {false};
        return () -> {
            if (done[0]) return;
            done[0] = true;
            if (commit != null) commit.run();
        };
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
        final Runnable commit = pageCommitOnce(updateContent);
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
                commit.run();
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
                // Whoever cancelled owns the visual state that follows (a new gesture, a reset);
                // the page the swipe asked for is committed here either way.
                commit.run();
                swipePagePosition = resolveCurrentSwipePagePosition();
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
        return DockPagingModel.settleDurationMs(velocityPxPerSec);
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
        final Runnable commit = pageCommitOnce(updateContent);

        animate()
            .translationX(-direction * (travel * 0.78f))
            .alpha(0f)
            .setDuration(outgoingDuration)
            .setInterpolator(settleInterpolator)
            .setListener(new AnimatorListenerAdapter() {
                private boolean completed;
                private boolean cancelled;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                    // Commit before the reset: the page is the swipe's decision, and the incoming
                    // half that would otherwise have carried it is not going to run.
                    commit.run();
                    finish(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (completed || cancelled) {
                        return;
                    }
                    completed = true;
                    commit.run();
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

    private View createPopupEntryButton(@NonNull LauncherAppEntry entry, int sizePx,
                                        @NonNull String sourceFolderId) {
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
        PinnedFolderItem sourceFolder = resolveLatestFolder(sourceFolderId);
        if (sourceFolder != null) bindAppContextLongPress(button, entry, -1, sourceFolder,
            resolveForSelectionRef(entry.appRef), true);
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

    /**
     * Puts every transient transform on the row and its children back to rest.
     *
     * <p>Refuses to run while the app drawer's transition is on screen, and re-applies the current
     * progress instead. Three callers reach here for reasons that have nothing to do with the drawer
     * — {@code onAttachedToWindow}, a window turning visible, and the HOME intent — and each of them
     * would otherwise reset the pinned icons to alpha 1 in the middle of a fade the controller is
     * still driving, or, worse, land after the last frame and leave them at the alpha the
     * <em>transition</em> wanted forever. The controller's own close path calls back with progress 0,
     * which is what actually restores them.
     */
    public void resetTransientVisualState() {
        if (drawerTransitionProgress > 0f) {
            setDrawerTransitionProgress(drawerTransitionProgress);
            return;
        }
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
        return DockPagingModel.azPageSignature(entryDigest(rankedCandidates), pageIndex, slots);
    }

    /** Adapts the ranked A–Z candidates to what the pure paging model needs to fingerprint a page. */
    @NonNull
    private static DockPagingModel.EntryDigest entryDigest(@NonNull List<LauncherAppEntry> entries) {
        return new DockPagingModel.EntryDigest() {
            @Override
            public int size() {
                return entries.size();
            }

            @Override
            public String keyAt(int index) {
                return stableEntryKey(entries.get(index));
            }

            @Override
            public boolean hasIconAt(int index) {
                return entries.get(index).icon != null;
            }
        };
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
        return DockPagingModel.pinnedItemsPerPage(maxButtonCount);
    }

    /** Pages occupied by the user's persisted pinned items (excludes the dynamic most-used page). */
    private int getRealPinnedPagesCount() {
        // Pass maxButtonCount rather than the pinnedItemsPerPage field: the field is 1 until the
        // first successful pinned render and after az/non-pinned renders, so feeding it here would
        // report one page per pinned item (the "dozens of empty page ticks" failure).
        return DockPagingModel.realPinnedPageCount(pinnedItemCount(), maxButtonCount);
    }

    private int pinnedItemCount() {
        return pinnedItems == null ? 0 : pinnedItems.size();
    }

    private int getPinnedPagesCount() {
        return DockPagingModel.pinnedPageCount(pinnedItemCount(), maxButtonCount,
            hasMostUsedDynamicPage());
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
        return DockPagingModel.isMostUsedDynamicPage(pageIndex, pinnedItemCount(), maxButtonCount,
            hasMostUsedDynamicPage());
    }

    /** Page index of the dynamic most-used page, or -1 when it isn't shown. */
    public int getPinnedDynamicPageIndex() {
        return DockPagingModel.dynamicPageIndex(pinnedItemCount(), maxButtonCount,
            hasMostUsedDynamicPage());
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
        pinnedPageIndex = DockPagingModel.clampPage(pinnedPageIndex, getPinnedPagesCount());
    }

    private int getAzPagesCount() {
        return DockPagingModel.azPageCount(
            activeAzCandidates == null ? 0 : activeAzCandidates.size(), maxButtonCount);
    }

    private int getAzPageStart(@Nullable List<LauncherAppEntry> entries, int pageIndex, int slots) {
        return DockPagingModel.azPageStart(entries == null ? 0 : entries.size(), pageIndex, slots);
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float screenDensity() {
        return getResources().getDisplayMetrics().density;
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
