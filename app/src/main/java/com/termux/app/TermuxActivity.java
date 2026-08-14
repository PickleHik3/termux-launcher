package com.termux.app;

import android.annotation.SuppressLint;
import android.app.WallpaperColors;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.github.mmin18.widget.AndroidStockBlurImpl;
import com.github.mmin18.widget.RealtimeBlurView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.fragments.settings.termux.KeyboardColorSchemeFragment;
import com.termux.app.launcher.animation.LauncherTransitionController;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.drawer.AppDrawerGestureArbiter;
import com.termux.app.launcher.drawer.DockRailScrollView;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.folder.FolderRenameController;
import com.termux.app.launcher.folder.FolderRenameModel;
import com.termux.app.launcher.folder.FolderRenameTitleView;
import com.termux.app.launcher.LauncherLockAccessibilityAccess;
import com.termux.app.launcher.LockAccessibilityService;
import com.termux.app.launcher.TerminalAppSearchKeyDecision;
import com.termux.app.onboarding.FirstLaunchOnboarding;
import com.termux.launcherctl.LauncherCtlApiServer;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.ShizukuBackend;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
import com.termux.app.terminal.TerminalFrameMetricsMonitor;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardHost;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.theme.TermuxThemeManager;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.TerminalNamePolicy;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.theme.ThemeUtils;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsCompat.Type;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.termux.launcherctl.LauncherToolRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import juloo.keyboard2.Keyboard2View;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection, SuggestionBarCallback {

    public static final String EXTRA_IN_APP_KEYBOARD_HEIGHT_ADJUST =
        "com.termux.app.extra.IN_APP_KEYBOARD_HEIGHT_ADJUST";
    public static final String EXTRA_DOCK_TUNING =
        "com.termux.app.extra.DOCK_TUNING";
    public static final String EXTRA_DOCK_TUNING_SECTION =
        "com.termux.app.extra.DOCK_TUNING_SECTION";
    /** Opens the extra-keys row editor over the live terminal, from Settings. */
    public static final String EXTRA_EDIT_EXTRA_KEYS =
        "com.termux.app.extra.EDIT_EXTRA_KEYS";
    /** Forces the first-launch experience for screenshots, product demos, and UI verification. */
    public static final String EXTRA_SHOW_ONBOARDING =
        "com.termux.app.extra.SHOW_ONBOARDING";

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} of the currently focused pane. Kept as a live alias to the
     * active pane so the many single-view call sites act on whichever pane has focus. Owned
     * and repointed by {@link #mPaneController}; null until the first tab is shown.
     */
    TerminalView mTerminalView;

    /** The pane that currently has focus. Backs {@link #getTerminalView()} (== {@link #mTerminalView}). */
    TerminalView mActivePane;

    // ---- Split-pane model ----
    // A "tab" = one primary session, shown in the drawer. Each tab owns a recursive binary
    // pane tree (tmux-style, unlimited splits) managed by mPaneController. Non-primary pane
    // sessions are hidden from the drawer.
    /** Recursive pane-tree engine; source of truth for panes/windows. */
    private com.termux.app.terminal.TerminalPaneController mPaneController;
    @Nullable private Bundle mPendingPaneLayoutState;
    /** Focused shell of the current window (mirrors the controller's active pane session). */
    @Nullable private TerminalSession mCurrentTabPrimary;

    /** A tmux-style session: an ordered list of windows (each a pane tree) + which is current. */
    static final class WSession {
        private static final java.util.concurrent.atomic.AtomicLong NEXT_ID =
            new java.util.concurrent.atomic.AtomicLong(1L);
        final long id = NEXT_ID.getAndIncrement();
        final java.util.List<com.termux.app.terminal.TerminalPaneController.Window> windows = new java.util.ArrayList<>();
        int current;
        @Nullable String name;
        com.termux.app.terminal.TerminalPaneController.Window currentWindow() { return windows.get(current); }
    }
    /** All sessions shown in the drawer. Each owns one or more windows. */
    private final java.util.List<WSession> mWSessions = new java.util.ArrayList<>();
    @Nullable private WSession mCurrentWSession;
    /** Session the switch indicator last fired for; compared by identity, never dereferenced. */
    @Nullable private WSession mLastIndicatedWSession;
    /** Resolves per-pane foreground process / open file for the window pill labels. */
    @Nullable private com.termux.app.statusbar.WindowForegroundResolver mWindowForegroundResolver;
    @Nullable private Runnable mSessionBrowserRefreshCallback;
    private final Handler mWindowLabelHandler = new Handler(Looper.getMainLooper());
    private static final long WINDOW_LABEL_POLL_MS = 2000L;
    /** Trailing CPU/RAM/weather widgets, their data controllers, and the shared detail card host. */
    @Nullable private com.termux.app.statusbar.SystemStatsController mStatsController;
    @Nullable private com.termux.app.statusbar.SystemStatsCardView mStatsCardView;
    /**
     * Presentation smoothing for the two bar readings only. One controller feeds both surfaces, and
     * the card wants every raw sample; the bar does not — see {@link
     * com.termux.app.statusbar.StatusBarStatSmoother}.
     */
    private final com.termux.app.statusbar.StatusBarStatSmoother mBarCpuSmoother =
        com.termux.app.statusbar.StatusBarStatSmoother.forCpuPercent();
    private final com.termux.app.statusbar.StatusBarStatSmoother mBarMemorySmoother =
        com.termux.app.statusbar.StatusBarStatSmoother.forMemoryPercent();
    /**
     * Sampling cadence while the mini-btop card is open. A live monitor is the point of the card, so
     * this is the one number here that must not grow.
     */
    private static final long STATS_CARD_INTERVAL_MS = 1500L;
    /**
     * And with the card closed, when the only consumers are the two bar readings. Those go through
     * {@link com.termux.app.statusbar.StatusBarStatSmoother} and repaint no faster than every 3 s
     * anyway, so sampling twice inside one of their publish windows was buying nothing and paying for
     * it with a privileged {@code /proc} read. Six seconds also widens the {@code /proc/stat} delta
     * window, which makes the raw CPU reading less spiky before any smoothing is applied.
     */
    private static final long STATS_BAR_INTERVAL_MS = 6000L;
    @Nullable private com.termux.app.statusbar.WeatherController mWeatherController;
    @Nullable private com.termux.app.statusbar.WeatherCardView mWeatherCardView;
    /** Fork-native sessions list dropped beneath the status-row session chip. */
    @Nullable private com.termux.app.statusbar.SessionsPanelView mSessionsPanelView;
    private final TerminalFrameMetricsMonitor mTerminalFrameMetricsMonitor =
        new TerminalFrameMetricsMonitor();
    private final com.termux.app.statusbar.StatusCardHost mStatusCardHost =
        new com.termux.app.statusbar.StatusCardHost();
    @Nullable private android.animation.ValueAnimator mStatusBarCollapseAnimator;
    private int mStatusBarTerminalResizeGeneration;
    @Nullable private com.termux.app.statusbar.FullStatusBarController mFullStatusBarController;
    private final com.termux.app.statusbar.StatusBarSurfaceOutlineProvider
        mStatusBarSurfaceOutline =
            new com.termux.app.statusbar.StatusBarSurfaceOutlineProvider();
    private int mFullStatusBarResizeGeneration;
    private boolean mRestoreFullStatusBar;
    @NonNull private com.termux.app.statusbar.TopStatusBarState mRestoredFullPrior =
        com.termux.app.statusbar.TopStatusBarState.EXPANDED;
    @Nullable private com.termux.app.launcher.widget.LauncherWidgetHostController mWidgetHostController;
    @Nullable private com.termux.app.launcher.widget.WidgetPaneController mWidgetPaneController;
    private static final int REQUEST_CODE_WEATHER_LOCATION = 4711;
    private static final int REQUEST_CODE_VOICE_TYPING = 4712;
    private static final int REQUEST_CODE_WALLPAPER_READ_PERMISSION = 4713;
    private static final int REQUEST_CODE_WIDGET_BIND = 4714;
    private static final int REQUEST_CODE_WIDGET_CONFIGURE = 4715;
    @Nullable private TerminalSession mVoiceTypingTargetSession;
    /** Drawer-visible sessions = service sessions minus secondary panes. Backs the list adapter. */
    public final java.util.List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> mDrawerSessions = new java.util.ArrayList<>();

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /** Activity-scoped embedded-keyboard controller and its currently attached renderer. */
    @Nullable private TermuxInAppKeyboard mInAppKeyboard;
    @Nullable private View mAttachedInAppKeyboardView;
    private boolean mInAppKeyboardShiftLocked;
    private float mInAppKeyboardHeightDragStartY;
    private float mInAppKeyboardHeightDragStartScale;
    private float mInAppKeyboardUnscaledDragHeight;
    private float mSurfaceTuningInsetDragStartX;
    private int mSurfaceTuningInsetDragStartDp;
    private float mSurfaceTuningDockHeightDragStartY;
    private float mSurfaceTuningDockHeightDragStartScale;
    private boolean mDockTuningMode;
    private boolean mDockTuningRestoreExpandedStatus;
    /** The status section expanded a collapsed pane for its preview, so closing gives it back. */
    private boolean mSurfaceEditorExpandedStatusPane;
    private ViewTreeObserver.OnGlobalLayoutListener mDockTuningLayoutListener;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    /** One entry per key page of the terminal toolbar pager, in page order. */
    final java.util.List<ExtraKeysView> mExtraKeysViews = new java.util.ArrayList<>();
    ExtraKeysView mExtraKeysView;

    SuggestionBarView mSuggestionBarView;
    private boolean mSuggestionBarExplicitSearchActive;
    AzScrubRowView mAzScrubRowView;
    @Nullable private View mAzTerminalToolbarView;
    LauncherAzGestureFxView mLauncherAzGestureFxUnderlayView;
    LauncherAzGestureFxView mLauncherAzGestureFxOverlayView;
    LauncherAzGestureFxView mLauncherAzGestureFxLabelOverlayView;

    private LauncherAppDataProvider mLauncherAppDataProvider;
    private LauncherConfigRepository mLauncherConfigRepository;
    private final FolderRenameController mFolderRenameController = new FolderRenameController();
    /** Anchored glass editor for session/window/pane renames; built on first rename. */
    @Nullable
    private com.termux.app.terminal.rename.TerminalRenameCoordinator mRenameCoordinator;
    private LauncherTransitionController mLauncherTransitionController;
    private int mLastLauncherIconPreferencesSignature = Integer.MIN_VALUE;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;
    /** Key-page clients, index-aligned with {@link #mExtraKeysViews}. */
    final java.util.List<TermuxTerminalExtraKeys> mTermuxTerminalExtraKeysPages =
        new java.util.ArrayList<>();

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();
    private final BroadcastReceiver mPackageChangeReceiver = new PackageChangeReceiver();
    private boolean mPackageChangeReceiverRegistered = false;
    /**
     * Fired by PackageManagerService whenever the preferred (default) home activity changes, so the
     * recents-visibility policy can react while the activity sits stopped in the background — the
     * onStart/onResume re-checks alone leave a task stuck excluded from recents until relaunch.
     * Hidden action string; on ROMs that never deliver it the lifecycle re-checks still apply.
     */
    private static final String ACTION_PREFERRED_ACTIVITY_CHANGED = "android.intent.action.ACTION_PREFERRED_ACTIVITY_CHANGED";
    @Nullable private BroadcastReceiver mPreferredHomeChangeReceiver;
    @Nullable private LauncherApps mLauncherApps;
    @Nullable private LauncherApps.Callback mLauncherAppsCallback;
    private boolean mLauncherAppsCallbackRegistered = false;
    private static final long PACKAGE_REFRESH_DEBOUNCE_MS = 120L;
    private static final long LAUNCHER_CATALOG_WARM_DELAY_MS = 450L;
    private boolean mPackageRefreshForceCatalogReload = false;
    private int mLastLauncherCatalogSignature = Integer.MIN_VALUE;
    private int mLastLauncherIconDayKey = Integer.MIN_VALUE;
    /**
     * Packages the pending (debounced) catalogue refresh knows were touched; scopes the provider's
     * entry reuse. Guarded by its own monitor — broadcasts and the debounce runnable share it.
     * When a refresh is requested without knowing what changed (calendar day flip, malformed
     * broadcast), {@link #mPendingChangedPackagesUnknown} widens the next refresh to a full rebuild.
     */
    private final java.util.LinkedHashSet<String> mPendingChangedPackages = new java.util.LinkedHashSet<>();
    private boolean mPendingChangedPackagesUnknown = false;
    private final Runnable mPackageRefreshRunnable = () -> {
        boolean forceCatalogRefresh = mPackageRefreshForceCatalogReload;
        mPackageRefreshForceCatalogReload = false;
        refreshSuggestionBarFromPackageState(forceCatalogRefresh);
    };
    private final Runnable mLauncherCatalogWarmRunnable = this::runLauncherCatalogWarmup;

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /** Which shells have produced output recently; drives the "working" indication. */
    private final com.termux.app.statusbar.ShellActivityTracker mShellActivityTracker =
        new com.termux.app.statusbar.ShellActivityTracker();
    /**
     * Deliberately its own handler rather than mWindowLabelHandler: scheduleWindowLabelPoll calls
     * removeCallbacksAndMessages(null) on that one, which would drop a pending activity refresh and
     * leave the coalescing flag stuck true.
     */
    private final android.os.Handler mShellActivityHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mShellActivityRefresh = this::refreshShellActivityIndication;
    private final Runnable mShellActivityDecay = this::refreshShellActivityIndication;
    private boolean mShellActivityRefreshPending;
    /** Output arrives far faster than a status row can usefully redraw. */
    private static final long SHELL_ACTIVITY_REFRESH_MS = 150L;
    /**
     * How long after something was written to a pane it stays exempt from the working indication.
     * Covers the echo of a keystroke and the render it triggers, so typing never lights the pill.
     */
    private static final long SHELL_INPUT_GRACE_MS = 700L;

    @Nullable private com.termux.app.terminal.SessionSwitchIndicatorView mSessionSwitchIndicator;
    private final com.termux.app.statusbar.BackgroundProcessModel mBackgroundProcessModel =
        new com.termux.app.statusbar.BackgroundProcessModel();
    @Nullable private com.termux.app.statusbar.BackgroundProcessStackView mBackgroundProcessStack;
    private final Handler mBackgroundProcessHandler = new Handler(Looper.getMainLooper());
    /** Height the transient notice chip is occupying above the stack; 0 when it is not showing. */
    private int mNoticeOccupancyPx;
    /**
     * Shells that rang while the user was elsewhere, so their window pill can say so. Kept by pid
     * rather than by session object: the flag has to survive the pane tree being rearranged under it.
     */
    private final java.util.Set<Integer> mAttentionShellPids = new java.util.HashSet<>();
    private final Runnable mBackgroundProcessResync = this::syncBackgroundProcessStack;
    @Nullable private com.termux.app.terminal.TerminalCommandPaletteController mCommandPalette;
    @Nullable private com.termux.app.terminal.TerminalSheetController mTerminalSheet;
    @Nullable private com.termux.app.launcher.drawer.AppDrawerController mAppDrawerController;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If service connected before activity became visible and bootstrap/session start should be retried onStart().
     */
    private boolean mPendingBootstrapOnStart = false;

    /**
     * Launch intent captured when bootstrap/session start is deferred to onStart().
     */
    @Nullable
    private Intent mPendingLaunchIntent;
    private boolean mLastLaunchWasLauncherEntry;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;
    
    public boolean isToolbarHidden = false;

    private int mNavBarHeight;
    private int mImeLiftPx;
    /** Set only after this activity explicitly requests a system IME in its current visible run. */
    private boolean mAcceptSystemImeInsets;
    private final LruCache<String, Integer> mLaunchIconColorCache = new LruCache<>(64);

    /** Reactive glass-plank physics for the dock (tilt, specular, accent rim glow). */
    @Nullable private DockPlankController mDockPlankController;
    @Nullable private View mDockPlankTarget;
    private boolean mDockPlankTouchInside = false;
    private float mDockPlankLeft = 0f;
    private float mDockPlankTop = 0f;
    private float mDockPlankWidth = 0f;
    private float mDockPlankHeight = 0f;
    private final int[] mDockPlankLocation = new int[2];

    private float mTerminalToolbarDefaultHeight;
    private final Handler mAzGestureHandler = new Handler(Looper.getMainLooper());
    private enum AzGestureMode {
        IDLE,
        AZ_TRACKING,
        UPWARD_LOCKED,
        ICON_TRACKING_LOCKED
    }
    @NonNull private AzGestureMode mAzGestureMode = AzGestureMode.IDLE;
    @Nullable private Choreographer.FrameCallback mAzEdgePagingFrameCallback;
    @Nullable private SuggestionBarView.AzDragFocusResult mAzCurrentFocusResult;
    @Nullable private Runnable mAzOverflowRefreshRunnable;
    private int mAzEdgePagingEdge = SuggestionBarView.AZ_EDGE_NONE;
    private long mAzEdgeDwellStartUptimeMs = 0L;
    private long mAzEdgePageCooldownUntilUptimeMs = 0L;
    private boolean mAzEdgeRequiresReentry = false;
    private boolean mAzGestureActive = false;
    private boolean mSuggestionBarInteractionActive = false;
    private char mAzLockedLetter = '#';
    private int mAzLockedSelectionIndex = 0;
    private boolean mAzHasLockedSelection = false;
    private boolean mAzHasPreviewAnchor = false;
    private char mAzPreviewAnchorLetter = '#';
    private int mAzPreviewAnchorSelectionIndex = 0;
    private float mAzRecentMotionDx = 0f;
    private float mAzRecentMotionDy = 0f;
    private long mAzLastMotionEventTimeMs = 0L;
    private float mAzUpwardTravelRefY = 0f;
    private float mAzLastScrubTouchX = 0f;
    private float mAzLastScrubTouchY = 0f;
    private float mAzLastRawX = 0f;
    private float mAzLastRawY = 0f;
    private float mAzLastAnchorRawX = 0f;
    private float mAzLastAnchorRawY = 0f;
    private float mAzLockedAnchorRawX = 0f;
    private float mAzLockedAnchorRawY = 0f;
    private final RectF mAzRowRawBounds = new RectF();
    private final RectF mAppsRowRawBounds = new RectF();
    private final RectF mExtraKeysRawBounds = new RectF();
    private final RectF mAzFocusLetterRawBounds = new RectF();
    private final int[] mAzViewLocation = new int[2];
    private final AzScrubRowView.LetterVisualMetrics mAzLetterVisualMetrics = new AzScrubRowView.LetterVisualMetrics();
    private static final long AZ_EDGE_PAGE_INITIAL_DELAY_MS = 560L;
    private static final long AZ_EDGE_PAGE_REPEAT_INTERVAL_MS = 420L;
    private static final long AZ_EDGE_PAGE_COOLDOWN_MS = 520L;
    private static final long AZ_PREVIEW_TIMEOUT_REFRESH_MS = 5200L;
    private static final float AZ_UPWARD_LOCK_TOUCH_Y_RATIO = 0.60f;
    private static final float AZ_RETURN_TOUCH_Y_RATIO = 0.55f;
    // Direction ratios compare against the smoothed RECENT motion vector, not displacement from
    // touch-down: after a long horizontal letter scrub the old cumulative test demanded a
    // near-vertical climb before the upward lock could engage.
    private static final float AZ_UPWARD_DIRECTION_RATIO = 0.45f;
    private static final float AZ_RETURN_DIRECTION_RATIO = 0.5f;
    /** Time constant for recent pointer velocity; independent of touch sampling rate. */
    private static final float AZ_RECENT_MOTION_TAU_MS = 50f;

    /**
     * The two long-press menu rows with no registry tool behind them. Everything else the menu
     * offers goes through {@code TerminalActionDispatcher} instead, so the menu, the palette, a
     * keybind and a remote caller share one implementation; these two have nowhere to share with.
     * Kill process is the confirmation dialog rather than {@code session.close_current}'s silent
     * close, and Style hands off to the Termux:Styling plugin.
     */
    public static final int CONTEXT_MENU_KILL_PROCESS_ID = 8;

    public static final int CONTEXT_MENU_STYLE_ID = 11;

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;

    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;

    private static final int CONTEXT_MENU_SET_WALLPAPER_ID = 2;

    private static final int CONTEXT_MENU_REMOVE_WALLPAPER_ID = 3;

    private static final int CONTEXT_MENU_SETTINGS_ID = 6;

    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 7;

    private static final int CONTEXT_MENU_GLASS_LAB_ID = 9;

    private static final int CONTEXT_MENU_COMMAND_PALETTE_ID = 10;

    /** One row of the long-press action dialog. */
    private static final class TerminalActionItem {
        final int id;
        final CharSequence title;

        TerminalActionItem(int id, CharSequence title) {
            this.id = id;
            this.title = title;
        }

        @NonNull
        @Override
        public String toString() {
            return title.toString();
        }
    }

    @Nullable private AlertDialog mTerminalActionDialog;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";

    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";
    private static final String ARG_PANE_LAYOUT = "pane_layout";
    private static final String ARG_FULL_STATUS_BAR = "full_status_bar";
    private static final String ARG_FULL_STATUS_BAR_PRIOR = "full_status_bar_prior";
    private static final String PANE_STATE_SESSIONS = "sessions";
    private static final String PANE_STATE_WINDOWS = "windows";
    private static final String PANE_STATE_CURRENT_WINDOW = "current_window";
    private static final String PANE_STATE_CURRENT_SESSION = "current_session";
    private static final String PANE_STATE_SESSION_NAME = "session_name";

    private static final String LOG_TAG = "TermuxActivity";
    private static final int IN_APP_KEYBOARD_MARGIN_SLIDER_STEPS_PER_UNIT = 100;
    private static final int IN_APP_KEYBOARD_RADIUS_SLIDER_STEPS_PER_DP = 10;
    private static final int ACCESSORY_BLUR_DOWNSAMPLE_FACTOR = 4;
    private static final long ACCESSORY_BLUR_BACKSTOP_MS = 300_000L;
    private static volatile boolean sPendingStyleReloadOnNextResume = false;
    private static volatile boolean sPendingAppDrawerReloadOnNextResume = false;

    private static final int SUGGESTION_BAR_MIN_BUTTON_DP = 56;
    private static final int SUGGESTION_BAR_MAX_INPUT_CHARS = 10;
    private static final long EMPTY_SESSION_RECOVERY_DEBOUNCE_MS = 1500L;
    private static final long ACCESSORY_BLUR_RECOVERY_RETRY_MS = 120L;
    private static final float DEFAULT_DOCK_SIZE_PRESET_SHIFT = 0.27f;
    private static final float DEFAULT_DOCK_SIZE_MAX_PROGRESS = 1.18f;
    private static final float[] DEFAULT_DOCK_ICON_PROGRESS_POINTS = {0.54f, 0.77f, 1.00f, 1.18f};
    private static final float[] DEFAULT_DOCK_ICON_SCALE_POINTS = {
        1.3068f, 1.487604f, 1.68f, 1.89072f
    };
    private static final float[] CAPSULE_DOCK_ICON_PROGRESS_POINTS = {0.27f, 0.50f, 0.73f, 1.00f};
    private static final float[] CAPSULE_DOCK_ICON_SCALE_POINTS = {
        1.7252f, 1.9633334f, 2.21312f, 2.508f
    };
    private static volatile boolean sPendingStyleReloadRecreateActivity = true;

    private boolean mSeamlessStatusBackgroundActive;
    private int mLastStatusBarInsetTop;
    private long mLastEmptySessionRecoveryElapsedMs;
    private boolean mEmptySessionRecoveryInProgress;
    private boolean mAccessoryRenderSyncPending;
    private boolean mLastImeVisible;
    @Nullable private ViewTreeObserver.OnGlobalLayoutListener mAccessoryKeyboardLayoutListener;
    @Nullable private View.OnLayoutChangeListener mAccessoryLayoutChangeListener;
    @Nullable private ViewTreeObserver.OnPreDrawListener mInAppKeyboardOpenPreDrawListener;
    @Nullable private View mInAppKeyboardOpenPreDrawView;
    @Nullable private ViewTreeObserver.OnPreDrawListener mInAppKeyboardClosePreDrawListener;
    @Nullable private View mInAppKeyboardClosePreDrawView;
    private int mInAppKeyboardOpenRevealBlockedFrames;
    private final Runnable mInAppKeyboardOpenRevealBackstopRunnable =
        this::revealInAppKeyboardIfStillPending;
    @Nullable private ActivityResultLauncher<PickVisualMediaRequest> mWallpaperPickerLauncher;
    @Nullable private ActivityResultLauncher<CropImageContractOptions> mWallpaperCropLauncher;
    private final int[] mTmpParentLocation = new int[2];
    private final int[] mTmpViewLocation = new int[2];
    private long mLastAccessoryGeometryApplyUptimeMs;
    private int mAppliedTerminalFlushPaddingPx;
    private boolean mAppliedInAppKeyboardShown;
    private int mDesiredInAppKeyboardHeightPx;
    private int mInAppKeyboardMeasureWidthPx;
    private int mInAppKeyboardAvailableHeightPx;
    private boolean mInAppKeyboardHeightDirty = true;
    private boolean mInAppKeyboardPreviewGeometrySyncPosted;
    /** Keeps a unified glass keyboard hidden until the expanded dock+keyboard crop is installed. */
    private boolean mPendingInAppKeyboardOpenReveal;
    /** Keeps the under-pill glass covering stale close geometry until dock-only layout settles. */
    private boolean mPendingInAppKeyboardCloseGeometry;
    private boolean mAccessoryBackdropDirty = true;
    /** Set when {@link WallpaperManager#getDrawable()} threw for want of the storage permission. */
    private boolean mWallpaperReadPermissionDenied;
    private boolean mWallpaperReadPermissionPromptShowing;
    private int mLastAccessoryBackdropBlurRadiusDp = -1;
    private boolean mLastAccessoryBackdropManagedSource;
    @NonNull private final Rect mLastAccessoryBackdropTargetRect = new Rect();
    @Nullable private FrameLayout mDecorNavBarSurfaceOverlay;
    @Nullable private ImageView mDecorNavBarBlurBackdrop;
    @Nullable private View mDecorNavBarTintOverlay;
    private boolean mDecorNavBarBackdropDirty = true;
    private int mLastDecorNavBarBackdropBlurRadiusDp = -1;
    private boolean mLastDecorNavBarBackdropManagedSource;
    @NonNull private final Rect mLastDecorNavBarBackdropTargetRect = new Rect();
    private boolean mInAppKeyboardBackdropDirty = true;
    private int mLastInAppKeyboardBackdropBlurRadiusDp = -1;
    private boolean mLastInAppKeyboardBackdropManagedSource;
    @NonNull private final Rect mLastInAppKeyboardBackdropTargetRect = new Rect();
    @Nullable private Bitmap mInAppKeyboardBackdropBitmap;
    /**
     * Memo of the color scheme's keyboard-background override, keyed on the raw persisted JSON.
     * The value is consulted on every accessory render sync, which must not re-parse JSON.
     */
    @Nullable private String mInAppKeyboardSchemeBackgroundJson;
    @Nullable private Integer mInAppKeyboardSchemeBackgroundColor;
    /**
     * Pre-blurred wallpaper frames shared by dock, keyboard, gesture-nav, and top-pane frost
     * crops — one frame per requested blur radius, LRU-capped. Surfaces are tuned independently
     * (dock and status frost carry their own radius sliders); the previous single-slot cache
     * was invalidated by every radius alternation, re-decoding and re-blurring the wallpaper on
     * the main thread two or three times on every return home (1-3s of dropped frames).
     */
    private static final int MAX_CACHED_WALLPAPER_BLUR_RADII = 3;
    @NonNull private final java.util.LinkedHashMap<Integer, Bitmap>
        mCachedAccessoryWallpaperBlurByRadius = new java.util.LinkedHashMap<>(4, 0.75f, true);
    @NonNull private final Rect mCachedAccessoryWallpaperBlurFrameRect = new Rect();
    private boolean mCachedAccessoryWallpaperBlurManagedSource;
    private int mCachedAccessoryWallpaperBlurSystemId = -1;
    private long mCachedAccessoryWallpaperBlurManagedLastModified = -1L;
    private long mCachedAccessoryWallpaperBlurManagedLength = -1L;
    /**
     * The orientation the cached frames were captured in. The frame rect alone was supposed to
     * carry this, but a rotation delivers {@code onConfigurationChanged} <em>before</em> the window
     * is re-laid out, so a crop taken during that pass records the outgoing orientation's rect and
     * then matches itself forever after. That is what landscape showed: a brighter, mismatched
     * wallpaper region with a hard seam at the pane's left edge, while portrait was correct.
     */
    private int mCachedAccessoryWallpaperBlurOrientation = Configuration.ORIENTATION_UNDEFINED;

    /** The rotation geometry pass waiting for the new layout, or null when none is pending. */
    @Nullable private OneShotPreDrawListener mPendingOrientationGeometryPass;

    /** Wallpaper-frost crop state for the top pane (status inset band + window-bar pane). */
    private boolean mTopPaneFrostDirty = true;
    private int mLastTopPaneFrostRadiusDp = -1;
    private final Rect mLastStatusFrostRect = new Rect();
    private final Rect mLastWindowBarFrostRect = new Rect();
    private final Matrix mFullStatusFrostMatrix = new Matrix();
    private final Rect mLastCommandPaletteFrostRect = new Rect();
    private int mLastCommandPaletteFrostRadiusDp = -1;
    private final Rect mLastAppDrawerFrostRect = new Rect();
    private int mLastAppDrawerFrostRadiusDp = -1;
    /**
     * Set when accessory geometry was suppressed because the app drawer plane owns the stack's
     * transforms. Flushed by {@link #flushPendingAccessoryGeometry()} on drawer close and on every
     * {@link #onStart()} — a suppression that is never flushed freezes the dock until recreate.
     */
    private boolean mAppDrawerGeometryFreezePending;
    @Nullable private WallpaperManager.OnColorsChangedListener mWallpaperColorsChangedListener;
    private final Handler mAccessoryRenderHandler = new Handler(Looper.getMainLooper());
    private final Runnable mInAppKeyboardPreviewGeometrySyncRunnable = () -> {
        mInAppKeyboardPreviewGeometrySyncPosted = false;
        if (!isFinishing() && !isDestroyed())
            requestInAppKeyboardGeometrySync();
    };
    private final Runnable mAccessoryBlurHeartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsVisible) {
                return;
            }
            AccessoryRenderState state = buildAccessoryRenderState();
            if (!state.toolbarShown || !state.blurEnabled) {
                return;
            }
            if (!isAccessoryBlurHealthy(state)) {
                mAccessoryBackdropDirty = true;
                mDecorNavBarBackdropDirty = true;
                mInAppKeyboardBackdropDirty = true;
                scheduleAccessoryRenderSync("blur:backstop");
            }
            mAccessoryRenderHandler.postDelayed(this, ACCESSORY_BLUR_BACKSTOP_MS);
        }
    };
    private final Runnable mAccessoryBlurRecoveryRunnable = () -> {
        if (!mIsVisible) {
            return;
        }
        AccessoryRenderState state = buildAccessoryRenderState();
        if (!state.toolbarShown || !state.blurEnabled) {
            return;
        }
        if (!isAccessoryBlurHealthy(state)) {
            mAccessoryBackdropDirty = true;
            mDecorNavBarBackdropDirty = true;
            mInAppKeyboardBackdropDirty = true;
        }
        scheduleAccessoryRenderSync("blur:recovery");
    };
    private final Runnable mAccessoryRenderSyncRunnable = () -> {
        mAccessoryRenderSyncPending = false;
        configureExtraKeysBackground();
        enforceAccessoryFxInvariants();
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;
        if (savedInstanceState != null) {
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);
            mPendingPaneLayoutState = savedInstanceState.getBundle(ARG_PANE_LAYOUT);
            mRestoreFullStatusBar = savedInstanceState.getBoolean(ARG_FULL_STATUS_BAR, false);
            try {
                mRestoredFullPrior = com.termux.app.statusbar.TopStatusBarState.valueOf(
                    savedInstanceState.getString(ARG_FULL_STATUS_BAR_PRIOR,
                        com.termux.app.statusbar.TopStatusBarState.EXPANDED.name()));
            } catch (IllegalArgumentException ignored) {
                mRestoredFullPrior = com.termux.app.statusbar.TopStatusBarState.EXPANDED;
            }
        }
        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);
        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        // Load preferences BEFORE setting theme (needed to check wallpaper preference)
        mPreferences = TermuxAppSharedPreferences.build(this, false);

        // Apply wallpaper or normal theme based on preference
        setActivityThemeAndWindow();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termux);
        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        if (mPreferences == null) {
            mPreferences = TermuxAppSharedPreferences.build(this, true);
        }
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }
        // Built after the preferences so a terminal-only install never registers an app widget
        // host at all — no binding, no listening, no widget providers reconciled.
        if (mPreferences.isAppLauncherWidgetPaneEnabled()) {
            mWidgetHostController = new com.termux.app.launcher.widget.LauncherWidgetHostController(this);
        }
        mPreferences.migrateTerminalMarginAdjustmentDefaultIfNeeded();
        mLauncherTransitionController = new LauncherTransitionController(this, mPreferences);
        setMargins();
        setSuggestionBarView();
        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());
        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            WindowInsetsCompat insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, v);
            mNavBarHeight = insetsCompat.getInsets(Type.systemBars()).bottom;
            mImeLiftPx = computeDockImeLiftPx(insetsCompat);
            applyDockImeOffset(0);
            applyTerminalOverlayInsets(insetsCompat);
            configureExtraKeysBackground();
            return insetsCompat.toWindowInsets();
        });
        applySeamlessStatusBackgroundModeIfNeeded();
        ViewCompat.requestApplyInsets(content);
        applyFullscreenMode();
        // Must be done every time activity is created in order to registerForActivityResult,
        // Even if the logic of launching is based on user input.
        registerWallpaperActivityResultLaunchers();
        mLastLaunchWasLauncherEntry = isLauncherHomeIntent(getIntent());
        setTermuxTerminalViewAndClients();
        createFullStatusBarController();
        createWidgetPaneController();
        setTerminalWindowBar();
        setTerminalToolbarView(savedInstanceState);
        updateDockRailView();
        initializeInAppKeyboard(savedInstanceState);
        // Only a fresh launch may enter adjust mode: after process death the system re-delivers
        // the original launch intent with the extra still set, which must not re-enter it.
        if (savedInstanceState == null) {
            handleInAppKeyboardHeightAdjustIntent(getIntent());
            handleDockTuningIntent(getIntent());
            handleEditExtraKeysIntent(getIntent());
        }
        if (mRestoreFullStatusBar) {
            View fullHost = findViewById(R.id.terminal_window_bar_host);
            if (fullHost != null) fullHost.post(() -> {
                if (mFullStatusBarController != null && mRestoreFullStatusBar) {
                    mRestoreFullStatusBar = false;
                    mFullStatusBarController.restoreFullImmediate(mRestoredFullPrior);
                }
            });
        }
        setSettingsButtonView();
        setNewSessionButtonView();
        setToggleKeyboardView();
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);
        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            };

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this, getString(e.getMessage() != null && e.getMessage().contains("app is in background") ? R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general), true);
            mIsInvalidState = true;
            return;
        }
        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
        registerPreferredHomeChangeReceiver();
        if (savedInstanceState == null) {
            boolean forceOnboarding = getIntent().getBooleanExtra(EXTRA_SHOW_ONBOARDING, false);
            View contentView = findViewById(android.R.id.content);
            contentView.post(() -> FirstLaunchOnboarding.showIfNeeded(this, forceOnboarding));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInAppKeyboardHeightAdjustIntent(intent);
        handleDockTuningIntent(intent);
        handleEditExtraKeysIntent(intent);
        if (isLauncherHomeIntent(intent)) {
            mLastLaunchWasLauncherEntry = true;
        }
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.maybeHandleGestureContract(intent, mSuggestionBarView);
        }
        if (isLauncherHomeIntent(intent)) {
            // Before resetTransientVisualState(): that call stomps every dock child's alpha, scale
            // and translation, so a drawer left open across HOME would strand faded pinned icons.
            closeAppDrawerImmediate();
            if (mSuggestionBarView != null) {
                mSuggestionBarView.resetTransientVisualState();
            }
            applyAccessoryGeometryIfNeeded(false, "onNewIntent:home");
            scheduleAccessoryRenderSync("onNewIntent:home");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Logger.logDebug(LOG_TAG, "onStart");
    
        if (mIsInvalidState) return;

        if (mWidgetHostController != null) mWidgetHostController.onStart();
        if (mWidgetPaneController != null) mWidgetPaneController.onStart();

        mTerminalFrameMetricsMonitor.start(getWindow());

        mIsVisible = true;
        resetInheritedImeLayoutState();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.setHostVisible(true);
            scheduleLauncherCatalogWarmup();
        }
        if (mPendingBootstrapOnStart && mTermuxService != null && mTermuxService.isTermuxSessionsEmpty()) {
            mPendingBootstrapOnStart = false;
            Intent pendingIntent = mPendingLaunchIntent;
            mPendingLaunchIntent = null;
            startBootstrapAndSession(pendingIntent);
        }
        maybeRecoverFromEmptySession("onStart");

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();
        if (mInAppKeyboard != null) {
            mInAppKeyboard.onStart();
        }
        updateWindowBackgroundForCurrentSession();
    
        if (mPreferences.isTerminalMarginAdjustmentEnabled()) {
            addTermuxActivityRootViewGlobalLayoutListener();
        }
        addAccessoryKeyboardLayoutListener();
        addAccessoryLayoutChangeListeners();
        // Unconditional: if a close threw, or the process was stopped mid-drag, the accessory
        // geometry is still frozen and the dock would stay deaf to style changes until recreate.
        flushPendingAccessoryGeometry();

        syncTerminalWallpaperRenderingMode();
        applySeamlessStatusBackgroundModeIfNeeded();
        applyTerminalSurfaceAppearance();
        // onStop() stops the stats sampler, and the only thing that starts it is
        // updateStatusWidgets(), reached solely from refreshTerminalWindowBar()'s tail — which
        // neither onStart nor onResume calls. Without this the CPU and memory readings never
        // resumed after leaving the app and coming back.
        updateStatusWidgets();
        syncRecentsVisibilityPolicy();
        configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, false, mPreferences.getSessionsOpacity() / 100f, 0);
        restartAccessoryBlurHeartbeat();
        scheduleAccessoryBlurRecovery();
        registerTermuxActivityBroadcastReceiver();
        registerPackageChangeReceiver();
        registerLauncherAppsCallback();
        registerWallpaperColorsChangedListener();
        refreshCalendarIconsIfDayChanged();
        refreshSuggestionBarIfLauncherCatalogChanged();
        getWindow().getDecorView().post(() -> LauncherCtlApiServer.getInstance().ensureStartedAsync(getApplicationContext()));
        if (mDockTuningMode && mDockTuningRestoreExpandedStatus
            && !isSurfaceTuningStatusSectionActive()
            && !mPreferences.isTopPaneClockCollapsed()) {
            setTopStatusBarCollapsed(true, false);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        feedDockPlank(ev);
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Observes (never consumes) touches over the dock to drive the reactive glass-plank physics:
     * the plank tilts toward the finger, dips on press, and the specular/rim glow track contact.
     * Bounds are captured once on ACTION_DOWN so the per-frame tilt transform can't feed back into
     * the hit-test, and reused for the rest of the gesture.
     */
    private void feedDockPlank(MotionEvent ev) {
        DockPlankController controller = mDockPlankController;
        if (controller == null) {
            return;
        }
        // The drawer's open drag starts on the dock; letting the plank keep tilting under it would
        // put two owners on the same views' transforms.
        if (isAppDrawerEngaged()) {
            return;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                View plank = mDockPlankTarget;
                mDockPlankTouchInside = false;
                if (plank == null || plank.getVisibility() != View.VISIBLE
                    || plank.getWidth() <= 0 || plank.getHeight() <= 0) {
                    break;
                }
                plank.getLocationOnScreen(mDockPlankLocation);
                mDockPlankLeft = mDockPlankLocation[0];
                mDockPlankTop = mDockPlankLocation[1];
                mDockPlankWidth = plank.getWidth();
                mDockPlankHeight = plank.getHeight();
                float x = ev.getRawX();
                float y = ev.getRawY();
                if (x >= mDockPlankLeft && x <= mDockPlankLeft + mDockPlankWidth
                    && y >= mDockPlankTop && y <= mDockPlankTop + mDockPlankHeight) {
                    mDockPlankTouchInside = true;
                    controller.onPointerDown(
                        (x - mDockPlankLeft) / mDockPlankWidth,
                        (y - mDockPlankTop) / mDockPlankHeight);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (mDockPlankTouchInside && mDockPlankWidth > 0f && mDockPlankHeight > 0f) {
                    controller.onPointerMove(
                        (ev.getRawX() - mDockPlankLeft) / mDockPlankWidth,
                        (ev.getRawY() - mDockPlankTop) / mDockPlankHeight);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mDockPlankTouchInside) {
                    controller.onPointerUp();
                    mDockPlankTouchInside = false;
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.logVerbose(LOG_TAG, "onResume");
        if (mIsInvalidState)
            return;
        // Terminal hierarchy actions from launcherctl/agent/MCP need a foreground
        // Activity; they answer 409 activity_not_running while nothing is attached.
        com.termux.app.terminal.TerminalActionDispatcher.getInstance().attach(this);
        // The last insets snapshot can be from mid-transition out of the previous app (IME still
        // up, nav bars reported hidden) and there is no later dispatch to correct it — a stale
        // lift here renders the dock and keyboard in the top third of the screen. Drop it and
        // ask for a fresh pass.
        resetInheritedImeLayoutState();
        View contentView = findViewById(android.R.id.content);
        if (contentView != null)
            androidx.core.view.ViewCompat.requestApplyInsets(contentView);
        // Preferences may have changed while the settings activity covered this one.
        initializeInAppKeyboard(null);
        if (mInAppKeyboard != null) {
            mInAppKeyboard.onPreferencesReloaded();
            mInAppKeyboard.onResume();
            if (mInAppKeyboard.isExternalTextInputActive())
                onSystemImeRequested();
        }
        if (sPendingStyleReloadOnNextResume) {
            boolean recreateActivity = consumePendingStyleReloadRecreateActivity();
            reloadActivityStyling(recreateActivity);
            return;
        }
        if (sPendingAppDrawerReloadOnNextResume) {
            sPendingAppDrawerReloadOnNextResume = false;
            if (mAppDrawerController != null) mAppDrawerController.onPreferencesReloaded();
        }
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.refreshMaterialTerminalColorsIfNeeded();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();
        refreshLauncherIconsIfPreferencesChanged();
        maybeRecoverFromEmptySession("onResume");
        // If compatibility mode was just enabled, drop any active split back to a single pane.
        if (!isSplitPanesEnabled())
            collapseAllSplits();
        else if (mPaneController != null)
            mPaneController.refreshPaneSizes();
        refreshTerminalWindowBar();

        updateWindowBackgroundForCurrentSession();
        syncTerminalWallpaperRenderingMode();
        applySeamlessStatusBackgroundModeIfNeeded();
        applyTerminalSurfaceAppearance();
        syncRecentsVisibilityPolicy();
        applyWallpaperOffsetFixIfNeeded();
        configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, false, mPreferences.getSessionsOpacity() / 100f, 0);
        scheduleAccessoryRenderSync("wallpaper:resume");
        restartAccessoryBlurHeartbeat();
        scheduleAccessoryBlurRecovery();
        refreshShizukuLockBackendIfNeeded();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.post(this::updateAzOverflowAffordance);
        }

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);
        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onPause() {
        // Rename owns the in-app-keyboard interceptor only while this activity is visible.
        mFolderRenameController.onActivityPaused();
        if (mRenameCoordinator != null) mRenameCoordinator.onActivityPaused();
        super.onPause();
    }

    private void applyTerminalSurfaceAppearance() {
        if (mPreferences == null) {
            return;
        }
        View terminalSurfaceHost = findViewById(R.id.terminal_surface_host);
        View terminalBodySurface = findViewById(R.id.terminal_background);
        View terminalStatusSurface = findViewById(R.id.terminal_status_bar_background);
        View terminalView = findViewById(R.id.terminal_view);
        if (terminalSurfaceHost == null || terminalBodySurface == null || terminalStatusSurface == null) {
            return;
        }
        applyTerminalBorderAppearance();
        boolean wallpaperMode = shouldUseWallpaperPassthroughMode();
        int accessoryBaseColor = resolveAccessoryGlassBaseColor();
        int sessionsBaseColor = resolveAccessoryGlassBaseColor();
        applyGlassSurfaceColor(R.id.extrakeys_background, accessoryBaseColor);
        applyGlassSurfaceColor(R.id.activity_termux_bottom_space_background, accessoryBaseColor);
        applyGlassSurfaceColor(R.id.sessions_background, sessionsBaseColor);

        if (wallpaperMode) {
            boolean showSurface = shouldShowTerminalOverlaySurface();
            int terminalSurfaceColor = showSurface ? resolveTerminalSurfaceColor() : Color.TRANSPARENT;
            // Unify the background: apply the terminal-opacity dim to the full-screen root so the
            // terminal area, the space under the floating dock, and the gesture-pill strip all read
            // as one continuous surface (the dock then floats on top of it). The bounded
            // terminal_background overlay is retired so the dim isn't applied twice.
            applyUnifiedBackgroundDim(terminalSurfaceColor);
            terminalSurfaceHost.setBackgroundColor(Color.TRANSPARENT);
            terminalBodySurface.setBackgroundColor(Color.TRANSPARENT);
            terminalBodySurface.setVisibility(View.GONE);
            terminalStatusSurface.setBackgroundColor(Color.TRANSPARENT);
            terminalStatusSurface.setVisibility(View.GONE);
            if (terminalView != null) {
                terminalView.setBackgroundColor(Color.TRANSPARENT);
                if (terminalView instanceof TerminalView) {
                    ((TerminalView) terminalView).setTransparentFrameOverlayColor(Color.TRANSPARENT);
                }
            }
            applyTerminalStatusBarSurfaceColor(showSurface, terminalSurfaceColor);
            applyTerminalWindowBarBackdropInsets();
            return;
        }

        // Opaque (non-wallpaper) mode keeps the bounded terminal surface; no full-screen dim needed.
        applyUnifiedBackgroundDim(Color.TRANSPARENT);
        boolean showSurface = true;
        int terminalSurfaceColor = resolveTerminalSurfaceColor();
        terminalSurfaceHost.setBackgroundColor(Color.TRANSPARENT);
        terminalBodySurface.setBackgroundColor(terminalSurfaceColor);
        terminalBodySurface.setVisibility(showSurface && Color.alpha(terminalSurfaceColor) > 0 ? View.VISIBLE : View.GONE);
        terminalStatusSurface.setBackgroundColor(terminalSurfaceColor);
        terminalStatusSurface.setVisibility(shouldShowTerminalStatusBarSurface(showSurface, terminalSurfaceColor) ? View.VISIBLE : View.GONE);
        if (terminalView != null) {
            terminalView.setBackgroundColor(Color.TRANSPARENT);
            if (terminalView instanceof TerminalView) {
                ((TerminalView) terminalView).setTransparentFrameOverlayColor(Color.TRANSPARENT);
            }
        }
        applyTerminalStatusBarSurfaceColor(showSurface, terminalSurfaceColor);
        applyTerminalWindowBarBackdropInsets();
    }

    /**
     * Optional thin outline framing the terminal area. Rounded to the same radius as other capsule
     * surfaces and inset from the dock/keyboard edges by the same 10dp when the Rounded surface
     * style is active; square and edge-to-edge otherwise. The pane host is inset by the stroke
     * width plus a small gap so terminal content never renders under the line, and is clipped to
     * the same rounded outline so its corners don't poke past a rounded border.
     */
    /** Gap the terminal border keeps from the status bar above it and the dock below it, in dp. */
    private static final int TERMINAL_BORDER_VERTICAL_INSET_DP = 5;

    /**
     * Tiled panes in the active window. A maximized pane counts as one, which is the point:
     * temporarily maximizing is visually a lone pane and should get the terminal border back.
     *
     * <p>Floats are deliberately excluded. This count decides whether the terminal border or the
     * per-pane borders own the frame line, and that flips paneInsetPx — which resizes the tiled
     * TerminalView and so reflows its PTY and resets its scroll position. A float is drawn over the
     * terminal and owns its own border; it must not make the pane underneath it reflow.
     */
    private int visiblePaneCount() {
        return mPaneController == null ? 1 : mPaneController.tiledPaneCount();
    }

    private void applyTerminalBorderAppearance() {
        if (mPreferences == null) {
            return;
        }
        View borderView = findViewById(R.id.terminal_border_overlay);
        View paneHost = findViewById(R.id.terminal_pane_host);
        if (borderView == null || paneHost == null) {
            return;
        }
        // One frame line, one owner. A lone pane gets the terminal border; the moment a window
        // splits, the pane borders are the frame and the terminal border stands down — two
        // concentric strokes only ever cost the terminal a row and clipped the prompt's own glyph
        // against the outer one.
        boolean preferBorder = mPreferences.isTerminalBorderEnabled();
        boolean singlePane = visiblePaneCount() <= 1;
        boolean enabled = preferBorder && singlePane;
        borderView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        boolean capsule = isRoundedDockStyle();
        int capsuleMarginPx = resolveDockHorizontalInsetPx();

        // Where a frame line sits, whichever view draws it. Sideways it tucks under the dock's own
        // capsule inset, which gives it visible air. Vertically it had none, so its top edge butted
        // against the status bar's lower edge and its bottom against the dock's upper edge, reading
        // as one merged frame; hold it off both by the gap the capsule surfaces leave.
        //
        // Keyed off the preference rather than off `enabled`, so splitting a window does not shift
        // the terminal: the pane borders land exactly where the terminal border was.
        int borderVerticalInsetPx = preferBorder
            ? Math.round(dpToPx(TERMINAL_BORDER_VERTICAL_INSET_DP)) : 0;

        ViewGroup.LayoutParams borderParams = borderView.getLayoutParams();
        if (borderParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) borderParams;
            if (marginParams.leftMargin != capsuleMarginPx || marginParams.rightMargin != capsuleMarginPx
                || marginParams.topMargin != borderVerticalInsetPx
                || marginParams.bottomMargin != borderVerticalInsetPx) {
                marginParams.leftMargin = capsuleMarginPx;
                marginParams.rightMargin = capsuleMarginPx;
                marginParams.topMargin = borderVerticalInsetPx;
                marginParams.bottomMargin = borderVerticalInsetPx;
                borderView.setLayoutParams(marginParams);
            }
        }

        int strokePx = Math.max(1, Math.round(dpToPx(1)));
        float cornerRadiusPx = capsule ? resolveDockCapsuleCornerRadiusPx(Integer.MAX_VALUE) : 0f;

        // Clearance inside the frame line, so a glyph never touches the stroke. Only the terminal
        // border needs it: pane borders draw their own stroke on the frame line itself, and adding
        // this on top of them is what pushed the split panes inside the outer border and clipped
        // their top corners.
        //
        // Arc clearance is not part of this margin. A margin moves the clipped box; it does not
        // move the box's own corners away from the arc it is clipped to, so at any radius the
        // corner cells were still being nibbled — worse the larger the radius. That clearance is
        // padding inside the clip instead (see cornerArcPaddingPx below).
        int paneInsetPx = enabled ? strokePx + Math.round(dpToPx(2)) : 0;
        int paneHorizontalInsetPx = capsuleMarginPx + paneInsetPx;
        int paneVerticalInsetPx = borderVerticalInsetPx + paneInsetPx;
        ViewGroup.LayoutParams paneParams = paneHost.getLayoutParams();
        if (paneParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) paneParams;
            if (marginParams.leftMargin != paneHorizontalInsetPx || marginParams.rightMargin != paneHorizontalInsetPx
                || marginParams.topMargin != paneVerticalInsetPx
                || marginParams.bottomMargin != paneVerticalInsetPx) {
                marginParams.leftMargin = paneHorizontalInsetPx;
                marginParams.rightMargin = paneHorizontalInsetPx;
                marginParams.topMargin = paneVerticalInsetPx;
                marginParams.bottomMargin = paneVerticalInsetPx;
                paneHost.setLayoutParams(marginParams);
            }
        }

        if (!enabled) {
            borderView.setBackground(null);
            applyPaneHostCornerPadding(paneHost, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                paneHost.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                paneHost.setClipToOutline(false);
            }
            return;
        }

        // Harmonize with the split-pane focus border (pane_active_border.xml uses colorPrimary to
        // mark the active pane): the ambient outer border must read as a quiet structural outline
        // rather than a second focus indicator, so it uses termuxColorOutlineVariant (the same role
        // already used for the dock/keyboard capsule's containing stroke) at a moderate alpha.
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setCornerRadius(cornerRadiusPx);
        border.setStroke(strokePx, withAlphaComponent(resolveAccessoryOutlineColor(), 150));
        borderView.setBackground(border);

        float innerRadiusPx = capsule ? Math.max(0f, cornerRadiusPx - paneInsetPx) : 0f;
        // A rounded rect of radius r reaches r·(1 - 1/√2) ≈ 0.293r past its own corner along the
        // diagonal, so content that starts at the corner of a clip with radius r loses that much of
        // its first cell. Padding the host by the arc's depth is what keeps the corner glyphs whole,
        // and because it is derived from the radius it holds at 20dp and at 40dp alike — the same
        // trade tmux and zellij make when they spend a whole cell on the frame: the frame owns
        // space the content never enters.
        applyPaneHostCornerPadding(paneHost, Math.round(innerRadiusPx * 0.30f));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (capsule) {
                paneHost.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), innerRadiusPx);
                    }
                });
                paneHost.setClipToOutline(true);
            } else {
                paneHost.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                paneHost.setClipToOutline(true);
            }
        }
    }

    /**
     * Arc clearance the pane tree lays out inside, so no glyph sits under the rounded clip. Applied
     * as padding rather than as a margin on purpose: the clip follows the host's bounds, so only
     * padding puts distance between the content's corners and the arc.
     */
    private static void applyPaneHostCornerPadding(@NonNull View paneHost, int paddingPx) {
        if (paneHost.getPaddingLeft() == paddingPx && paneHost.getPaddingTop() == paddingPx
            && paneHost.getPaddingRight() == paddingPx
            && paneHost.getPaddingBottom() == paddingPx)
            return;
        paneHost.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
    }

    /**
     * Paints the full-screen root with the terminal-opacity dim so the whole window background is a
     * single uniform surface (terminal, the gap under the floating dock, and the gesture-pill strip
     * all match), with the dock floating on top. Pass {@link Color#TRANSPARENT} to clear it.
     */
    private void applyUnifiedBackgroundDim(int color) {
        View root = mTermuxActivityRootView != null
            ? mTermuxActivityRootView
            : findViewById(R.id.activity_termux_root_view);
        if (root != null) {
            root.setBackgroundColor(color);
        }
    }

    private void applyTerminalStatusBarSurfaceColor(boolean showSurface, int terminalSurfaceColor) {
        int targetColor;
        if (shouldUseWallpaperPassthroughMode()) {
            // The full-screen root dim already covers the status-bar region uniformly. A seamless
            // status scrim here would dim it a second time, making the notification area read darker
            // than the terminal — so keep the system status bar transparent in wallpaper mode.
            targetColor = Color.TRANSPARENT;
        } else if (shouldEnableSeamlessStatusBackground()) {
            targetColor = terminalSurfaceColor;
        } else {
            targetColor = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        }
        if (getWindow() != null) {
            getWindow().setStatusBarColor(targetColor);
            if (shouldUseWallpaperPassthroughMode()) {
                // Keep the gesture-pill strip showing only the unified root dim. Re-assert a fully
                // transparent navigation bar with contrast enforcement off so the OS doesn't paint a
                // darker contrast scrim behind the pill that would make the nav area read darker than
                // the terminal. (This removes a scrim; it never adds a layer over the content.)
                getWindow().setNavigationBarColor(Color.TRANSPARENT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getWindow().setNavigationBarContrastEnforced(false);
                }
            }
        }
    }

    private void applyWallpaperOffsetFixIfNeeded() {
        if (!shouldUseWallpaperPassthroughMode()) {
            return;
        }
        if (getWindow() == null || getWindow().getDecorView() == null) {
            return;
        }
        IBinder windowToken = getWindow().getDecorView().getWindowToken();
        if (windowToken == null) {
            return;
        }
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
            Rect frameRect = getSystemWallpaperFrameRect();
            // suggestDesiredDimensions needs SET_WALLPAPER_HINTS; guard it on its own so a missing
            // permission (or OEM restriction) can't skip the actual offset-centering below it.
            try {
                wallpaperManager.suggestDesiredDimensions(frameRect.width(), frameRect.height());
            } catch (Exception ignored) {
            }
            wallpaperManager.setWallpaperOffsetSteps(1f, 1f);
            wallpaperManager.setWallpaperOffsets(windowToken, 0.5f, 0.5f);
            // Ask the system to render the wallpaper at true size. Home apps are expected to
            // drive this; left alone, some OEMs keep the launcher-state "zoom out" at maximum
            // (~1.10x about the screen center — measured 1.105x on Nothing OS with a grid
            // wallpaper), and every pre-blurred glass crop then shows content displaced by
            // ~10% of its distance from the center: a few dp at the dock, worst under the
            // keyboard. The frost math models an unzoomed wallpaper, so request exactly that.
            // setWallpaperZoomOut is hidden API; launchers reach it by reflection, and when a
            // ROM blocks the call nothing changes from today's behavior.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    WallpaperManager.class
                        .getMethod("setWallpaperZoomOut", IBinder.class, float.class)
                        .invoke(wallpaperManager, windowToken, 0f);
                } catch (Throwable t) {
                    Logger.logVerbose(LOG_TAG, "setWallpaperZoomOut unavailable: " + t);
                }
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply wallpaper offset fix", e);
        }
    }

    private void registerWallpaperColorsChangedListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 || mWallpaperColorsChangedListener != null) {
            return;
        }
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
            mWallpaperColorsChangedListener = (WallpaperColors colors, int which) -> {
                clearCachedAccessoryWallpaperBlur();
                if (!mIsVisible) {
                    return;
                }
                if (mTermuxTerminalSessionActivityClient != null) {
                    mTermuxTerminalSessionActivityClient.refreshMaterialTerminalColorsIfNeeded();
                }
                applyTerminalSurfaceAppearance();
                // Dynamic keyboard swatches follow the Material roles; pinned or imported ones
                // are left exactly as stored. Gated internally on the palette signature moving.
                if (mInAppKeyboard != null) {
                    mInAppKeyboard.refreshMaterialPalette();
                }
                scheduleAccessoryRenderSync("wallpaper:colors");
            };
            wallpaperManager.addOnColorsChangedListener(mWallpaperColorsChangedListener, mAccessoryRenderHandler);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to register wallpaper color listener", e);
            mWallpaperColorsChangedListener = null;
        }
    }

    private void unregisterWallpaperColorsChangedListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 || mWallpaperColorsChangedListener == null) {
            return;
        }
        try {
            WallpaperManager.getInstance(this).removeOnColorsChangedListener(mWallpaperColorsChangedListener);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to unregister wallpaper color listener", e);
        } finally {
            mWallpaperColorsChangedListener = null;
        }
    }

    private int resolveAccessoryGlassBaseColor() {
        if (isNightThemeActive()) {
            return resolveMaterialDarkBackgroundColor();
        }
        return getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh, R.color.termux_surface_panel_high);
    }

    private int resolveMaterialDarkBackgroundColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int colorResId = getResources().getIdentifier("system_neutral1_900", "color", "android");
            if (colorResId != 0) {
                return ContextCompat.getColor(this, colorResId);
            }
        }
        return Color.parseColor("#1C1B1F");
    }

    private int resolveAccessoryOutlineColor() {
        return getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOutlineVariant, R.color.termux_outline_variant);
    }

    /** Wallpaper-derived accent (Material You primary) used across the dock's reactive glass treatment. */
    private int resolveDockAccentColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(this, R.color.termux_primary));
    }

    /**
     * Builds the Material-tinted glass surface: an opaque neutral base with a faint top-down sheen
     * layered on top. The earlier corner-to-corner (TL->BR) accent wash read as a "digital"
     * left-light / right-dark gradient; a real glass pane catches ambient light from above, so this
     * uses a gentle, low-contrast top sheen (cool accent at the very top easing to clear well before
     * the bottom) with extra stops for a smooth, bandless falloff. Kept subtle so the blurred
     * wallpaper behind it carries the glass read rather than a synthetic gradient. The host clips
     * this to the dock's rounded outline; the view's own alpha carries the configured opacity.
     */
    @NonNull
    private Drawable buildDockGlassSurface(float barAlpha) {
        return buildDockGlassSurface(barAlpha, 0f, 1f);
    }

    /**
     * Builds the dock/keyboard glass tint. The vertical light model — thin cool sheen at the top, a
     * faint accent edge, a clear see-through middle, then a soft dark "foot" at the bottom that
     * suggests the slab's thickness — normally spans the full surface height ({@code sliceStart=0},
     * {@code sliceEnd=1}).
     *
     * <p>When the keyboard is shown, the glass is split across two stacked surfaces (the keyboard
     * host, then the shorter under-pill nav strip below it). Rendering the full model on each would
     * put a dark foot at the keyboard's own bottom AND another at the strip's bottom — a dark band
     * mid-slab and an over-tinted strip. Instead both surfaces render adjacent slices of ONE model
     * spanning keyboard+strip: the keyboard uses {@code [0, f]} and the strip {@code [f, 1]}, so the
     * single foot lands under the pill exactly as it does for the keyboard-off dock (which draws one
     * gradient over dock+nav). This keeps the two states looking identical.</p>
     */
    @NonNull
    private Drawable buildDockGlassSurface(float barAlpha, float sliceStart, float sliceEnd) {
        return buildDockGlassSurface(barAlpha, sliceStart, sliceEnd, true);
    }

    /**
     * @param withFoot when false the dark bottom "foot" of the light model is dropped. The default
     *     dock stack (in-content dock/keyboard + under-pill nav strip) sets this false so the strip
     *     is not darker than the dock body — the foot would otherwise land under the pill and read as
     *     a darker nav band. The floating capsule veil / controls bar keep the foot for slab depth.
     */
    @NonNull
    private Drawable buildDockGlassSurface(float barAlpha, float sliceStart, float sliceEnd,
                                           boolean withFoot) {
        int grain = mPreferences != null
            ? mPreferences.getDockGlassGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN;
        return buildGlassSurface(barAlpha, sliceStart, sliceEnd, withFoot, grain);
    }

    @NonNull
    private Drawable buildStatusBarGlassSurface(float barAlpha, float sliceStart, float sliceEnd) {
        return buildStatusBarGlassSurface(barAlpha, sliceStart, sliceEnd, false);
    }

    /**
     * The status bar's glass, built from the same model as the dock and the keyboard and differing
     * only in the values its own controls supply.
     *
     * <p>It used to pass {@code withFoot=false}, which dropped the dark bottom foot the other
     * surfaces have, and no caller gave it the containing stroke and corner radius that
     * {@code configureAccessoryCapsuleOutline} gives the dock — so at identical opacity, blur and
     * grain it still read as a flat slab rather than glass. Both now come from the shared builder.
     *
     * @param rim whether this view is the visible slab (as opposed to the behind-status extension
     *            that merges into it, where a stroke would draw a line through the seam)
     */
    private Drawable buildStatusBarGlassSurface(float barAlpha, float sliceStart, float sliceEnd,
                                                boolean rim) {
        int grain = mPreferences != null
            ? mPreferences.getStatusBarGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_GRAIN;
        // Height-clamped like the outline clip (min(configured, height/2)): the compact pill's
        // baked stroke must curve exactly with the clip, or the corners double up.
        float cornerRadiusPx = rim && isRoundedDockStyle()
            ? resolveStatusBarCapsuleCornerRadiusPx(targetStatusBarHeightPx(true,
                mPreferences != null && mPreferences.isTopPaneClockCollapsed()))
            : 0f;
        return buildGlassSurface(barAlpha, sliceStart, sliceEnd, true, grain, cornerRadiusPx, rim);
    }

    private float resolveStatusBarCornerRadiusPx() {
        if (mPreferences == null)
            return 0f;
        int configured = mPreferences.getStatusBarCornerRadius();
        // -1 means "follow the style", which used to reach the drawable as a negative radius and
        // silently squared the status surface off while the dock beside it was rounded.
        return dpToPx(configured >= 0 ? configured
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP);
    }

    @NonNull
    private Drawable buildGlassSurface(float barAlpha, float sliceStart, float sliceEnd,
                                      boolean withFoot, int grain) {
        return buildGlassSurface(barAlpha, sliceStart, sliceEnd, withFoot, grain, 0f, false);
    }

    /**
     * The one glass surface builder every surface goes through: tint, vertical light model, grain,
     * and optionally the rounded containing stroke. Callers differ only in the values their own
     * controls supply, which is what keeps the dock, the keyboard and the status bar the same
     * material while still being tunable apart.
     */
    @NonNull
    private Drawable buildGlassSurface(float barAlpha, float sliceStart, float sliceEnd,
                                      boolean withFoot, int grain, float cornerRadiusPx,
                                      boolean withRim) {
        int base = resolveAccessoryGlassBaseColor();
        int accent = resolveDockAccentColor();
        float clamped = barAlpha < 0f ? 0f : (barAlpha > 1f ? 1f : barAlpha);
        // Opacity controls the colored material wash and its lighting. The wallpaper blur and
        // grain are independent physical layers: reducing tint should reveal more frost/texture,
        // not cross-fade back to sharp wallpaper.
        int baseAlpha = dockGlassBaseAlpha(clamped);
        int topSheenAlpha = Math.round(16f * clamped);
        int midSheenAlpha = Math.round(8f * clamped);
        int bottomFootAlpha = withFoot ? Math.round(20f * clamped) : 0;
        GradientDrawable baseLayer = new GradientDrawable();
        baseLayer.setColor(withAlphaComponent(base, baseAlpha));
        baseLayer.setDither(true);

        int[] sliceColors = dockGlassLightModelSlice(accent, topSheenAlpha, midSheenAlpha,
            bottomFootAlpha, sliceStart, sliceEnd);
        GradientDrawable lightLayer = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, sliceColors);
        lightLayer.setDither(true);

        java.util.List<Drawable> layers = new java.util.ArrayList<>();
        layers.add(baseLayer);
        layers.add(lightLayer);
        // Optional film grain over the frosted glass — reads as real glass texture instead of a flat
        // blur. Amount is user-controlled (Appearance > Glass grain); 0 omits the layer entirely.
        if (grain > 0) {
            layers.add(buildDockGrainLayer(grain));
        }
        if (withRim) {
            // Same barely-there containing stroke the dock's capsule pass draws. Anything heavier
            // reads as a drawn border over the glass rather than the edge of the material.
            GradientDrawable rim = new GradientDrawable();
            rim.setColor(Color.TRANSPARENT);
            rim.setCornerRadius(cornerRadiusPx);
            rim.setStroke(Math.max(1, Math.round(dpToPx(1))),
                withAlphaComponent(resolveAccessoryOutlineColor(), 18));
            layers.add(rim);
        }
        if (cornerRadiusPx > 0f) {
            baseLayer.setCornerRadius(cornerRadiusPx);
            lightLayer.setCornerRadius(cornerRadiusPx);
        }
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    /** Model stop positions for the vertical glass light model, matched to {@link #dockGlassLightModelColorAt}. */
    private static final float[] DOCK_GLASS_MODEL_STOPS = {0f, 0.33f, 0.67f, 1f};

    /**
     * Samples the vertical glass light model over {@code [sliceStart, sliceEnd]} (fractions of the
     * full model height) and returns the colors for a top-to-bottom gradient across that slice. The
     * slice's own model stops are included so the sheen/foot shape is preserved rather than reduced
     * to a straight two-color ramp.
     */
    @NonNull
    private int[] dockGlassLightModelSlice(int accent, int topSheenAlpha, int midSheenAlpha,
                                           int bottomFootAlpha, float sliceStart, float sliceEnd) {
        float start = Math.max(0f, Math.min(1f, sliceStart));
        float end = Math.max(start, Math.min(1f, sliceEnd));
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        colors.add(dockGlassLightModelColorAt(start, accent, topSheenAlpha, midSheenAlpha, bottomFootAlpha));
        for (float stop : DOCK_GLASS_MODEL_STOPS) {
            if (stop > start && stop < end) {
                colors.add(dockGlassLightModelColorAt(stop, accent, topSheenAlpha, midSheenAlpha, bottomFootAlpha));
            }
        }
        colors.add(dockGlassLightModelColorAt(end, accent, topSheenAlpha, midSheenAlpha, bottomFootAlpha));
        int[] result = new int[colors.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = colors.get(i);
        }
        return result;
    }

    /**
     * Color of the vertical glass light model at {@code pos} in [0,1]: accent sheen at the top
     * ([0,0.33]), fading to a clear see-through middle ([0.33,0.67]), then to a dark foot at the
     * bottom ([0.67,1]). No broad white wash — a near-white sheen reads as frosted plastic.
     */
    private int dockGlassLightModelColorAt(float pos, int accent, int topSheenAlpha,
                                           int midSheenAlpha, int bottomFootAlpha) {
        int sheenTop = withAlphaComponent(accent, topSheenAlpha);
        int sheenMid = withAlphaComponent(accent, midSheenAlpha);
        int clear = Color.TRANSPARENT;
        int foot = withAlphaComponent(Color.BLACK, bottomFootAlpha);
        if (pos <= 0.33f) {
            return lerpArgb(sheenTop, sheenMid, pos / 0.33f);
        }
        if (pos <= 0.67f) {
            return lerpArgb(sheenMid, clear, (pos - 0.33f) / 0.34f);
        }
        return lerpArgb(clear, foot, (pos - 0.67f) / 0.33f);
    }

    /** Straight ARGB interpolation (alpha included) between two colors. */
    private static int lerpArgb(int a, int b, float t) {
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        int aa = Color.alpha(a), ab = Color.alpha(b);
        int ra = Color.red(a), rb = Color.red(b);
        int ga = Color.green(a), gb = Color.green(b);
        int ba = Color.blue(a), bb = Color.blue(b);
        return Color.argb(
            Math.round(aa + (ab - aa) * t),
            Math.round(ra + (rb - ra) * t),
            Math.round(ga + (gb - ga) * t),
            Math.round(ba + (bb - ba) * t));
    }

    /** A tiled grain layer whose strength is controlled only by the grain preference. */
    @NonNull
    private Drawable buildDockGrainLayer(int grainPercent) {
        return DockGlassRendering.createGrainLayer(getResources(), grainPercent);
    }

    static int dockGlassGrainAlpha(int grainPercent) {
        return DockGlassRendering.grainAlpha(grainPercent);
    }

    static boolean dockBlurEnabled(int blurRadiusDp) {
        return blurRadiusDp > 0;
    }

    /** Literal opacity endpoint: 100% is an opaque material and 0% is fully transparent. */
    static final int DOCK_GLASS_BASE_MAX_ALPHA = 255;
    static int dockGlassBaseAlpha(float opacity) {
        float clampedOpacity = Math.max(0f, Math.min(1f, opacity));
        return Math.round(clampedOpacity * DOCK_GLASS_BASE_MAX_ALPHA);
    }

    /** Cached light-scatter filter applied to the blurred wallpaper backdrop. */
    @Nullable private ColorMatrixColorFilter mGlassFrostFilter;

    /**
     * "Liquid glass" vibrancy applied to the blurred backdrop (cheap GPU colour filter). Apple-style
     * glass does NOT desaturate and lift the backdrop toward grey — that reads as milky plastic.
     * Instead it keeps the content vivid: boost saturation and DEEPEN contrast so darks stay dark and
     * colours pop through the blur, so the dock reads as a vivid see-through pane, not a flat slab.
     */
    @NonNull
    private ColorMatrixColorFilter glassFrostFilter() {
        if (mGlassFrostFilter == null) {
            ColorMatrix frost = new ColorMatrix();
            frost.setSaturation(1.30f);   // vibrancy boost (was desaturating -> milk)
            float c = 1.06f;   // slight contrast boost (>1); opposite of the milky compression
            float t = -6f;     // no brightness lift; tiny deepen so darks don't haze to grey
            ColorMatrix vibrancy = new ColorMatrix(new float[] {
                c, 0, 0, 0, t,
                0, c, 0, 0, t,
                0, 0, c, 0, t,
                0, 0, 0, 1, 0
            });
            frost.postConcat(vibrancy);
            mGlassFrostFilter = new ColorMatrixColorFilter(frost);
        }
        return mGlassFrostFilter;
    }

    /** Cached AGSL glass-refraction shader (API 33+). */
    @Nullable private RuntimeShader mGlassShader;

    // --- Active extra-key lens state (drives the per-key refraction in the backdrop shader). ---
    private boolean mKeyLensActive = false;
    private float mKeyLensIntensity = 0f;            // 0..1 fade
    private float mKeyLensCx, mKeyLensCy;            // centre in backdrop fragCoord space
    private float mKeyLensHx, mKeyLensHy;            // half extents
    private float mKeyLensRadius;                    // corner radius
    // Last dock-glass params, so the lens can rebuild the backdrop effect without recapturing.
    private boolean mGlassParamsValid = false;
    private float mGlassBlurPx, mGlassCapLeft, mGlassCapTop, mGlassCapRight, mGlassCapBottom, mGlassRadiusPx;

    /**
     * AGSL glass: what separates real glass from frosted polymer is that glass <em>bends</em> light
     * at its edges (refraction) and catches a crisp edge highlight, instead of just diffusing it.
     * This shader samples the blurred backdrop and, within a band along the rounded-capsule edge,
     * displaces the sample inward along the edge normal — so the wallpaper compresses/lenses at the
     * rim like the bevel of a thick glass slab — then lays a thin sharp rim highlight and a faint
     * inner shadow for thickness. Runs on the GPU as a one-shot RenderEffect; no per-frame re-blur.
     */
    private static final String GLASS_AGSL =
        "uniform shader content;\n" +
        "uniform float2 uRectMin;\n" +
        "uniform float2 uRectMax;\n" +
        "uniform float uRadius;\n" +
        "uniform float uBand;\n" +
        "uniform float uStrength;\n" +
        "uniform float uRim;\n" +
        "uniform float uDensity;\n" +
        // Active extra-key "lens": a rounded-rect that MAGNIFIES (bends) the backdrop strongest from
        // the middle and eases to nothing at its rim, so a pressed key reads as a thick glass pill
        // refracting the wallpaper that shows through the transparent key cell. uLensActive carries
        // the 0..1 fade intensity.
        "uniform float uLensActive;\n" +
        "uniform float2 uLensCenter;\n" +
        "uniform float2 uLensHalf;\n" +
        "uniform float uLensRadius;\n" +
        "uniform float uLensStrength;\n" +
        "uniform float uLensFeather;\n" +
        "float sdRoundRect(float2 p, float2 b, float r) {\n" +
        "    float2 q = abs(p) - b + float2(r, r);\n" +
        "    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;\n" +
        "}\n" +
        "half4 main(float2 fragCoord) {\n" +
        "    float2 center = (uRectMin + uRectMax) * 0.5;\n" +
        "    float2 b = (uRectMax - uRectMin) * 0.5;\n" +
        "    float2 p = fragCoord - center;\n" +
        "    float inside = -sdRoundRect(p, b, uRadius);\n" +
        "    float2 n = normalize(float2(p.x / max(b.x, 1.0), p.y / max(b.y, 1.0)) + float2(1e-4, 1e-4));\n" +
        "    float e = clamp(1.0 - inside / uBand, 0.0, 1.0);\n" +
        "    e = e * e;\n" +
        "    float2 sampleCoord = fragCoord - n * (e * uStrength);\n" +
        // Per-key lens: magnify the backdrop toward the key centre, fading out to the pill rim.
        "    float lensGlow = 0.0;\n" +
        "    if (uLensActive > 0.001) {\n" +
        "        float2 lp = fragCoord - uLensCenter;\n" +
        "        float ld = -sdRoundRect(lp, uLensHalf, uLensRadius);\n" +
        "        if (ld > 0.0) {\n" +
        "            float2 ln = float2(lp.x / max(uLensHalf.x, 1.0), lp.y / max(uLensHalf.y, 1.0));\n" +
        "            float rr = clamp(length(ln), 0.0, 1.0);\n" +
        "            float kFull = mix(1.0 - uLensStrength, 1.0, smoothstep(0.5, 1.0, rr));\n" +
        "            float k = mix(1.0, kFull, uLensActive);\n" +
        "            float fade = smoothstep(0.0, max(uLensFeather, 1.0), ld) * uLensActive;\n" +
        "            float2 lensCoord = uLensCenter + lp * k;\n" +
        "            sampleCoord = mix(sampleCoord, lensCoord - n * (e * uStrength), fade);\n" +
        "            lensGlow = fade * (1.0 - rr);\n" +
        "        }\n" +
        "    }\n" +
        "    half4 col = content.eval(sampleCoord);\n" +
        // One clean, sharp hairline rim where the light catches the glass edge. No dark contour, no
        // wide bevel band, no inner shadow — minimal/zen: a crisp pane with slight edge refraction.
        "    float rim = 1.0 - smoothstep(0.0, 2.0 * uDensity, inside);\n" +
        "    col.rgb = col.rgb + half3(rim * uRim);\n" +
        "    col.rgb = col.rgb + half3(lensGlow * uRim * 0.6);\n" +
        "    return col;\n" +
        "}\n";

    /**
     * Build the glass RenderEffect: refraction shader fed by a blur of the backdrop. Returns null on
     * pre-33 devices or if the shader fails to compile, so the caller falls back to a plain blur.
     */
    @Nullable
    private RenderEffect buildGlassRefractionEffect(float blurPx, float capLeft, float capTop,
                                                    float capRight, float capBottom, float radiusPx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return null;
        }
        try {
            if (mGlassShader == null) {
                mGlassShader = new RuntimeShader(GLASS_AGSL);
            }
            float density = getResources().getDisplayMetrics().density;
            mGlassShader.setFloatUniform("uRectMin", capLeft, capTop);
            mGlassShader.setFloatUniform("uRectMax", capRight, capBottom);
            mGlassShader.setFloatUniform("uRadius", radiusPx);
            mGlassShader.setFloatUniform("uBand", density * 20f);
            mGlassShader.setFloatUniform("uStrength", density * 9f);
            mGlassShader.setFloatUniform("uRim", 0.16f);
            mGlassShader.setFloatUniform("uDensity", density);
            // Per-key lens state (0 intensity == no lens, dock refraction unchanged).
            mGlassShader.setFloatUniform("uLensActive", mKeyLensActive ? mKeyLensIntensity : 0f);
            mGlassShader.setFloatUniform("uLensCenter", mKeyLensCx, mKeyLensCy);
            mGlassShader.setFloatUniform("uLensHalf", Math.max(1f, mKeyLensHx), Math.max(1f, mKeyLensHy));
            mGlassShader.setFloatUniform("uLensRadius", mKeyLensRadius);
            mGlassShader.setFloatUniform("uLensStrength", 0.20f);
            mGlassShader.setFloatUniform("uLensFeather", density * 10f);
            RenderEffect shaderEffect = RenderEffect.createRuntimeShaderEffect(mGlassShader, "content");
            if (blurPx > 0f) {
                RenderEffect blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP);
                return RenderEffect.createChainEffect(shaderEffect, blur);
            }
            return shaderEffect;
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Glass refraction shader failed; falling back to blur", t);
            return null;
        }
    }

    /**
     * Drive the per-key refraction lens from a pressed extra key (screen-space rect). Maps the rect
     * into the backdrop shader's coordinate space and fades the lens in. No-op unless the backdrop
     * glass shader is currently active (API 33+, static wallpaper, blur on) — otherwise the key just
     * shows its thin-border bubble. Called on the UI thread from the ExtraKeysView lens listener.
     */
    private void setExtraKeyLens(float screenLeft, float screenTop, float screenRight, float screenBottom) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !mGlassParamsValid) {
            return;
        }
        View surfaceHost = findViewById(R.id.accessory_surface_host);
        if (surfaceHost == null || surfaceHost.getWidth() <= 0 || surfaceHost.getHeight() <= 0) {
            return;
        }
        int[] hostLoc = new int[2];
        surfaceHost.getLocationOnScreen(hostLoc);
        // surfaceHost's left maps to fragCoord x == capLeft (the overscan); top maps to capTop.
        float fl = (screenLeft - hostLoc[0]) + mGlassCapLeft;
        float fr = (screenRight - hostLoc[0]) + mGlassCapLeft;
        float ft = (screenTop - hostLoc[1]) + mGlassCapTop;
        float fb = (screenBottom - hostLoc[1]) + mGlassCapTop;
        mKeyLensCx = (fl + fr) * 0.5f;
        mKeyLensCy = (ft + fb) * 0.5f;
        mKeyLensHx = Math.max(1f, (fr - fl) * 0.5f);
        mKeyLensHy = Math.max(1f, (fb - ft) * 0.5f);
        mKeyLensRadius = Math.min(mKeyLensHx, mKeyLensHy); // stadium pill, matches the bubble
        mKeyLensActive = true;
        // Snap on (single RenderEffect rebuild) rather than fade — a per-frame rebuild during a fade
        // would be continuous GPU work while typing, which we explicitly avoid. The bubble border
        // carries the motion; the lens is a static refraction held for the duration of the press.
        mKeyLensIntensity = 1f;
        rebuildBackdropGlassEffectForLens();
    }

    /** Snap the active extra-key lens off (single rebuild). */
    private void clearExtraKeyLens() {
        if (!mKeyLensActive && mKeyLensIntensity == 0f) {
            return;
        }
        mKeyLensActive = false;
        mKeyLensIntensity = 0f;
        rebuildBackdropGlassEffectForLens();
    }

    /** Rebuild + re-apply the backdrop glass RenderEffect with the current lens state (no recapture). */
    private void rebuildBackdropGlassEffectForLens() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !mGlassParamsValid) {
            return;
        }
        View backdrop = findViewById(R.id.accessory_blur_backdrop);
        if (backdrop == null || backdrop.getVisibility() != View.VISIBLE) {
            return;
        }
        RenderEffect glass = buildGlassRefractionEffect(
            mGlassBlurPx, mGlassCapLeft, mGlassCapTop, mGlassCapRight, mGlassCapBottom, mGlassRadiusPx);
        if (glass != null) {
            backdrop.setRenderEffect(glass);
        }
    }

    /** Lazily wires the reactive glass-plank controller to the inflated dock views. */
    private void setupDockPlankFx() {
        View plank = findViewById(isInAppKeyboardShown()
            ? R.id.accessory_surface_host : R.id.accessory_stack_container);
        if (plank == null) {
            return;
        }
        if (mDockPlankController == null || mDockPlankTarget != plank) {
            if (mDockPlankController != null)
                mDockPlankController.reset();
            View specular = findViewById(R.id.accessory_specular_fx);
            View glow = findViewById(R.id.accessory_edge_glow_fx);
            mDockPlankController = new DockPlankController(plank, specular, glow);
            mDockPlankTarget = plank;
        }
        mDockPlankController.setReducedMotion(isReducedMotionEnabled());
    }

    /** Refreshes the plank FX drawables (accent/shape may change) and enables it for a shown dock. */
    private void refreshDockPlankFx(float materialAlpha) {
        setupDockPlankFx();
        if (mDockPlankController == null) {
            return;
        }
        int accent = resolveDockAccentColor();
        View specular = findViewById(R.id.accessory_specular_fx);
        if (specular != null) {
            specular.setBackground(buildDockSpecularDrawable(accent));
            specular.setAlpha(materialAlpha);
        }
        View glow = findViewById(R.id.accessory_edge_glow_fx);
        if (glow instanceof DockEdgeGlowView) {
            View surfaceHost = findViewById(R.id.accessory_surface_host);
            int surfaceHeightPx = surfaceHost != null ? surfaceHost.getHeight() : 0;
            float radius = isRoundedDockStyle() ? resolveDockCapsuleCornerRadiusPx(surfaceHeightPx) : 0f;
            DockEdgeGlowView glowView = (DockEdgeGlowView) glow;
            glowView.setAlpha(materialAlpha);
            glowView.setAccentColor(accent);
            glowView.setCornerRadiusPx(radius);
            // Feather the rim into a soft glow instead of a hard outline (API 31+). The view draws
            // its rim inset from the edge so this blur stays inside the rounded clip at the corners.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                float blur = getResources().getDisplayMetrics().density * 7f;
                glow.setRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
            }
        }
        // Capsule keeps the free-floating tilt + dip. The edge-to-edge default dock keeps the
        // touch-tracked specular/glow only; rotating a full-width slab exposes clipped side gaps.
        boolean capsuleDock = isRoundedDockStyle();
        mDockPlankController.setMotionEnabled(capsuleDock);
        mDockPlankController.setHingeMode(!capsuleDock);
        mDockPlankController.setReducedMotion(isReducedMotionEnabled());
        mDockPlankController.setEnabled(true);
    }

    /** Soft accent-tinted radial specular that rides the touch point across the glass. */
    @NonNull
    private Drawable buildDockSpecularDrawable(int accent) {
        GradientDrawable specular = new GradientDrawable();
        specular.setShape(GradientDrawable.RECTANGLE);
        specular.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        specular.setGradientCenter(0.5f, 0.5f);
        specular.setGradientRadius(dpToPx(120));
        // White-leaning, wide and soft so it reads as caught light gliding over glass rather than a
        // painted accent disc under the finger.
        specular.setColors(new int[] {
            withAlphaComponent(Color.WHITE, 70),
            withAlphaComponent(accent, 22),
            withAlphaComponent(accent, 0)
        });
        specular.setDither(true);
        return specular;
    }

    public boolean isReducedMotionEnabled() {
        try {
            float scale = Settings.Global.getFloat(
                getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale == 0f;
        } catch (Throwable t) {
            return false;
        }
    }

    private void playAppLaunchRipple(@NonNull String packageName, @Nullable Drawable icon,
                                     @Nullable View sourceView) {
        if (isReducedMotionEnabled()) return;
        View accessorySurface = findViewById(R.id.accessory_surface_host);
        DockLaunchRippleView ripple = accessorySurface == null ? null
            : (DockLaunchRippleView) accessorySurface.findViewWithTag("dock_launch_ripple");
        if (ripple == null || ripple.getWidth() <= 0 || ripple.getHeight() <= 0) return;

        int color = boostLaunchRippleColor(resolveLaunchIconDominantColor(packageName, icon),
            resolveDockAccentColor());
        int[] rippleLocation = new int[2];
        ripple.getLocationOnScreen(rippleLocation);
        float originX;
        float originY;
        if (sourceView != null && sourceView.isAttachedToWindow()) {
            int[] sourceLocation = new int[2];
            sourceView.getLocationOnScreen(sourceLocation);
            originX = sourceLocation[0] + sourceView.getWidth() * 0.5f - rippleLocation[0];
            originY = sourceLocation[1] + sourceView.getHeight() * 0.5f - rippleLocation[1];
        } else {
            originX = ripple.getWidth() * 0.5f;
            originY = ripple.getHeight() * 0.5f;
        }

        boolean capsule = isRoundedDockStyle();
        RectF bounds = new RectF(0f, 0f, ripple.getWidth(), ripple.getHeight());
        float cornerRadius = capsule ? resolveDockCapsuleCornerRadiusPx(ripple.getHeight()) : 0f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The glass shader is stateful and belongs to the backdrop. Reusing it here mutates the
            // same rounded-surface uniforms while a radial source is being drawn, which produced the
            // audit's repeated oversized masks. Keep the launch wave a single Canvas composition.
            ripple.setRenderEffect(null);
        }
        DockEdgeGlowView edgeGlow = findViewById(R.id.accessory_edge_glow_fx);
        if (capsule && edgeGlow != null) edgeGlow.setVisibility(View.VISIBLE);
        final boolean keyboardShownAtLaunch = isInAppKeyboardShown();
        ripple.startRipple(color, originX, originY, capsule, bounds, cornerRadius,
            edgeGlow == null ? null : (collisionColor, level) ->
                edgeGlow.setLaunchCollisionState(collisionColor, level),
            () -> capsule == isRoundedDockStyle()
                && keyboardShownAtLaunch == isInAppKeyboardShown(),
            () -> {
                if (mInAppKeyboard != null) mInAppKeyboard.fadeOutLaunchWave();
            });

        if (!capsule && keyboardShownAtLaunch && mInAppKeyboard != null) {
            mInAppKeyboard.animateLaunchWave(color, originX + rippleLocation[0],
                originY + rippleLocation[1]);
        }
    }

    /** Keeps an icon's hue legible after the low-alpha wave is composited through tinted glass. */
    private static int boostLaunchRippleColor(int color, int fallbackAccent) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] < 0.08f) {
            Color.colorToHSV(fallbackAccent, hsv);
        }
        hsv[1] = Math.max(0.72f, hsv[1]);
        hsv[2] = Math.max(0.78f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    private int resolveLaunchIconDominantColor(@NonNull String packageName,
                                               @Nullable Drawable sourceIcon) {
        Integer cached = mLaunchIconColorCache.get(packageName);
        if (cached != null) return cached;
        Drawable drawable = sourceIcon;
        if (drawable == null) {
            try {
                drawable = getPackageManager().getApplicationIcon(packageName);
            } catch (Throwable ignored) {
            }
        }
        int fallback = resolveDockAccentColor();
        int color = extractDominantIconColor(drawable, fallback);
        mLaunchIconColorCache.put(packageName, color);
        return color;
    }

    static int extractDominantIconColor(@Nullable Drawable drawable, int fallback) {
        if (drawable == null) return fallback;
        final int size = 40;
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Rect oldBounds = drawable.copyBounds();
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            drawable.setBounds(oldBounds);
        } catch (Throwable throwable) {
            return fallback;
        }
        int[] buckets = new int[4096];
        int[] pixels = new int[size * size];
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
        bitmap.recycle();
        // Only chromatic pixels vote: adaptive icons are mostly white/neutral background, and letting
        // that mass win pushed every launch color to the theme-accent fallback. Neutral-only icons
        // still fall back below.
        int bestBucket = -1;
        int bestWeight = 0;
        int opaqueCount = 0;
        int chromaticCount = 0;
        float[] hsv = new float[3];
        for (int pixel : pixels) {
            int alpha = Color.alpha(pixel);
            if (alpha < 64) continue;
            opaqueCount++;
            Color.colorToHSV(pixel, hsv);
            if (hsv[1] < 0.18f || hsv[2] < 0.18f) continue;
            chromaticCount++;
            int r = Color.red(pixel) >> 4;
            int g = Color.green(pixel) >> 4;
            int b = Color.blue(pixel) >> 4;
            int bucket = (r << 8) | (g << 4) | b;
            int weight = Math.round(alpha * hsv[1]);
            buckets[bucket] += weight;
            if (buckets[bucket] > bestWeight) {
                bestWeight = buckets[bucket];
                bestBucket = bucket;
            }
        }
        if (bestBucket < 0 || opaqueCount == 0 || chromaticCount * 25 < opaqueCount)
            return fallback;
        int r = ((bestBucket >> 8) & 0xF) * 17;
        int g = ((bestBucket >> 4) & 0xF) * 17;
        int b = (bestBucket & 0xF) * 17;
        return Color.rgb(r, g, b);
    }

    private void applyGlassSurfaceColor(int viewId, int surfaceColor) {
        View surface = findViewById(viewId);
        if (surface != null) {
            surface.setBackgroundColor(surfaceColor);
        }
    }

    private float resolveOpacityAlpha(int opacityPercent) {
        int clamped = Math.max(0, Math.min(100, opacityPercent));
        return clamped / 100f;
    }

    private int resolveTerminalOverlayBaseColor() {
        if (mPreferences != null && mPreferences.isTerminalDynamicColorsEnabled()) {
            return com.termux.app.terminal.MaterialTerminalColorScheme.backgroundColor(
                this, mPreferences.getTerminalContrastLevel());
        }
        if (isNightThemeActive()) {
            return getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        }
        return Color.parseColor("#1C1B1F");
    }

    private int resolveTerminalSurfaceColor() {
        int baseColor = shouldUseWallpaperPassthroughMode()
            ? resolveTerminalOverlayBaseColor()
            : getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        // The opacity slider is the user's contract: in wallpaper mode this colour is painted on the
        // full-screen root, so raising it for glyph contrast hides the wallpaper everywhere at once.
        // Generated palettes always contain mid-tone greys (color0/color8), which cannot meet a
        // contrast target over both a dark and a light wallpaper at any alpha below 255 — so any
        // "raise until every glyph is legible over any wallpaper" floor is pinned at fully opaque.
        int opacity = mPreferences != null ? mPreferences.getTerminalBackgroundOpacity() : 100;
        int alpha = Math.round(resolveOpacityAlpha(opacity) * 255f);
        return (alpha << 24) | (baseColor & 0x00FFFFFF);
    }

    private int resolveAccessorySurfaceColor(float surfaceAlpha) {
        int baseColor = shouldUseWallpaperPassthroughMode()
            ? resolveAccessoryGlassBaseColor()
            : getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        int alpha = Math.round(Math.max(0f, Math.min(1f, surfaceAlpha)) * 255f);
        return (alpha << 24) | (baseColor & 0x00FFFFFF);
    }

    @NonNull
    private Drawable buildRoundedAmbientVeil(float surfaceAlpha, boolean decorLayer) {
        int surfaceColor = resolveAccessorySurfaceColor(surfaceAlpha);
        int baseAlpha = Color.alpha(surfaceColor);
        int midAlpha = Math.round(baseAlpha * (decorLayer ? 0.22f : 0.14f));
        int highAlpha = Math.round(baseAlpha * (decorLayer ? 0.40f : 0.24f));
        // Soft veil that fades back to transparent at the BOTTOM as well as the top, so the floating
        // dock blends smoothly into the same terminal/background dim above and below — no hard
        // darker-band seam at the dock's bottom edge against the under-dock gap and gesture strip.
        GradientDrawable veil = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {
                Color.TRANSPARENT,
                withAlphaComponent(surfaceColor, midAlpha),
                withAlphaComponent(surfaceColor, highAlpha),
                Color.TRANSPARENT
            }
        );
        veil.setDither(true);
        return veil;
    }

    private void applyAccessoryAmbientVeil(@Nullable View accessoryContainer, @NonNull AccessoryRenderState state) {
        if (accessoryContainer == null) {
            return;
        }
        if (!state.toolbarShown || !isRoundedDockStyle()) {
            accessoryContainer.setBackground(null);
            return;
        }
        accessoryContainer.setBackground(buildRoundedAmbientVeil(state.barAlpha, false));
    }

    private int getTermuxThemeColor(int attr, int fallbackRes) {
        return ThemeUtils.getSystemAttrColor(this, attr, ContextCompat.getColor(this, fallbackRes));
    }

    private boolean isNightThemeActive() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int withAlphaComponent(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private boolean shouldShowTerminalOverlaySurface() {
        if (mProperties == null || mProperties.isUsingFullScreen()) {
            return false;
        }
        if (mPreferences == null) {
            return false;
        }
        if (!shouldUseWallpaperPassthroughMode()) {
            return true;
        }
        return mPreferences.getTerminalBackgroundOpacity() > 0;
    }

    private void applyAccessoryLayerBounds(int viewId, @Nullable Rect bounds) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof RelativeLayout.LayoutParams)) {
            return;
        }
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layoutParams;
        int targetTop = 0;
        int targetWidth = ViewGroup.LayoutParams.MATCH_PARENT;
        int targetHeight = ViewGroup.LayoutParams.MATCH_PARENT;
        int targetLeftMargin = 0;
        int targetRightMargin = 0;
        boolean capsuleSurface = viewId == R.id.accessory_surface_host && isRoundedDockStyle();
        ViewParent parent = view.getParent();
        if (parent instanceof View) {
            View parentView = (View) parent;
            if (parentView.getWidth() > 0) {
                targetWidth = parentView.getWidth();
            }
            if (parentView.getHeight() > 0) {
                targetHeight = parentView.getHeight();
            }
        }
        if (bounds != null) {
            targetTop = Math.max(0, bounds.top);
            targetHeight = Math.max(0, bounds.height());
        }
        if (viewId == R.id.accessory_surface_host && targetWidth > 0) {
            int horizontalMargin = resolveDockHorizontalInsetPx();
            targetLeftMargin = horizontalMargin;
            targetRightMargin = horizontalMargin;
            targetWidth = Math.max(1, targetWidth - (horizontalMargin * 2));
        }
        applyDockSurfaceShape(view, capsuleSurface, targetHeight);
        if (params.leftMargin != targetLeftMargin || params.topMargin != targetTop ||
            params.rightMargin != targetRightMargin || params.bottomMargin != 0 ||
            params.width != targetWidth || params.height != targetHeight) {
            params.leftMargin = targetLeftMargin;
            params.topMargin = targetTop;
            params.rightMargin = targetRightMargin;
            params.bottomMargin = 0;
            params.width = targetWidth;
            params.height = targetHeight;
            view.setLayoutParams(params);
        }
    }

    /** Positions the dock glass behind either the dock rows alone or the unified default stack. */
    private void applyAccessorySurfaceBounds(@NonNull AccessoryRenderState state) {
        View surface = findViewById(R.id.accessory_surface_host);
        if (surface == null)
            return;
        ViewGroup.LayoutParams layoutParams = surface.getLayoutParams();
        if (layoutParams instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) layoutParams;
            boolean unified = shouldUseUnifiedDefaultKeyboardGlassSurface(state);
            boolean rulesChanged = false;
            if (unified) {
                rulesChanged = params.getRule(RelativeLayout.ABOVE) != 0
                    || params.getRule(RelativeLayout.ALIGN_PARENT_TOP) != RelativeLayout.TRUE;
                params.removeRule(RelativeLayout.ABOVE);
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            } else {
                rulesChanged = params.getRule(RelativeLayout.ALIGN_PARENT_TOP) != 0
                    || params.getRule(RelativeLayout.ABOVE) != R.id.inapp_keyboard_container;
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                params.addRule(RelativeLayout.ABOVE, R.id.inapp_keyboard_container);
                params.alignWithParent = true;
            }
            if (rulesChanged)
                surface.setLayoutParams(params);
        }
        Rect bounds = state.keyboardShown && !shouldUseUnifiedDefaultKeyboardGlassSurface(state)
            ? buildToolbarOnlyAccessoryBounds(state) : null;
        applyAccessoryLayerBounds(R.id.accessory_surface_host, bounds);
    }

    private void applyAccessoryLayerVerticalBounds(int viewId, @Nullable Rect bounds) {
        View view = findViewById(viewId);
        if (view == null || !(view.getLayoutParams() instanceof RelativeLayout.LayoutParams))
            return;
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();
        int targetTop = bounds == null ? 0 : Math.max(0, bounds.top);
        int targetHeight = bounds == null
            ? ViewGroup.LayoutParams.MATCH_PARENT : Math.max(0, bounds.height());
        if (params.topMargin != targetTop || params.height != targetHeight) {
            params.topMargin = targetTop;
            params.height = targetHeight;
            view.setLayoutParams(params);
        }
    }

    public boolean isRoundedDockStyle() {
        return mPreferences != null
            && TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED.equals(
                mPreferences.getAppLauncherDockStyle()
            );
    }

    /**
     * Apply the selected status-bar style to the top window-bar host. Default is the edge-to-edge
     * glass pane (no margins, square corners, glass extended behind the system status bar). Rounded
     * capsule floats the pane: horizontal margins, a gap below the status bar, and a rounded-outline
     * clip so the blur + glass read as the same capsule geometry as the Rounded dock.
     */
    private void applyStatusBarStyle(@NonNull View host) {
        boolean capsule = isRoundedDockStyle();
        boolean collapsed = mPreferences != null && mPreferences.isTopPaneClockCollapsed();
        ViewGroup.LayoutParams lp = host.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            int hMargin = resolveStatusBarHorizontalInsetPx();
            // Extend Rounded upward without moving its lower edge or the terminal content.
            int topMargin = capsule ? Math.round(dpToPx(2)) : 0;
            int targetHeight = targetStatusBarHeightPx(capsule, collapsed);
            if (mlp.leftMargin != hMargin || mlp.rightMargin != hMargin
                || mlp.topMargin != topMargin
                || (mStatusBarCollapseAnimator == null && !isFullStatusBarEngaged()
                    && mlp.height != targetHeight)) {
                mlp.leftMargin = hMargin;
                mlp.rightMargin = hMargin;
                mlp.topMargin = topMargin;
                if (mStatusBarCollapseAnimator == null && !isFullStatusBarEngaged()) {
                    mlp.height = targetHeight;
                }
                host.setLayoutParams(mlp);
            }
            View topWidgets = findViewById(R.id.terminal_top_widget_area);
            if (topWidgets != null && topWidgets.getLayoutParams() != null) {
                int targetWidgetHeight = Math.round(dpToPx(capsule ? 72 : 68));
                ViewGroup.LayoutParams widgetParams = topWidgets.getLayoutParams();
                if (widgetParams.height != targetWidgetHeight) {
                    widgetParams.height = targetWidgetHeight;
                    topWidgets.setLayoutParams(widgetParams);
                }
                if (mStatusBarCollapseAnimator == null && !isFullStatusBarEngaged()) {
                    topWidgets.setAlpha(1f);
                    topWidgets.setTranslationY(0f);
                    topWidgets.setVisibility(collapsed ? View.GONE : View.VISIBLE);
                }
            }

            // While a spring or animator drives the pane, applyFrame's
            // applyInteractiveStatusRowGeometry() is the sole writer of status-row geometry —
            // the same ownership rule the host-height and top-slot writes above already follow.
            // Without this, any refreshTerminalWindowBar() while FULL is settled re-anchors the
            // row to its normal-state rule (CENTER_VERTICAL while the clock is collapsed),
            // parking it mid-pane instead of on the pane's bottom inset.
            boolean interactiveGeometryOwnsRow = mStatusBarCollapseAnimator != null
                || isFullStatusBarEngaged();

            // Keep the bottom chip corners inside the capsule's 26dp outline. At the former 4dp
            // inset, the rounded host clip intersected the session and weather chip backgrounds.
            View statusRow = findViewById(R.id.terminal_status_row);
            if (statusRow != null && !interactiveGeometryOwnsRow
                && statusRow.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams rowParams =
                    (ViewGroup.MarginLayoutParams) statusRow.getLayoutParams();
                // Keep only enough inset for the capsule clip and move the side content inward
                // below where the curve becomes tight.
                int targetBottomMargin = Math.round(dpToPx(collapsed ? 0 : capsule ? 3 : 2));
                int targetRowHeight = Math.round(dpToPx(collapsed && capsule ? 22 : 24));
                int targetGravity = collapsed ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM;
                boolean rowChanged = rowParams.bottomMargin != targetBottomMargin
                    || rowParams.topMargin != 0 || rowParams.height != targetRowHeight;
                if (rowParams instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) rowParams;
                    rowChanged |= frameParams.gravity != targetGravity;
                    frameParams.gravity = targetGravity;
                }
                if (rowChanged) {
                    rowParams.topMargin = 0;
                    rowParams.bottomMargin = targetBottomMargin;
                    rowParams.height = targetRowHeight;
                    statusRow.setLayoutParams(rowParams);
                }
            }

            View sessions = findViewById(R.id.terminal_sessions_indicator);
            if (sessions != null
                && sessions.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams sessionParams =
                    (ViewGroup.MarginLayoutParams) sessions.getLayoutParams();
                int targetStartMargin = statusBarContentEdgeInsetPx(capsule);
                int targetSessionHeight = Math.round(dpToPx(collapsed
                    ? capsule ? 18 : 20 : 20));
                int targetSessionWidth = sessions instanceof
                    com.termux.app.statusbar.SessionsIndicatorView
                    && ((com.termux.app.statusbar.SessionsIndicatorView) sessions).isShowingSessionNumber()
                    ? targetSessionHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
                boolean sessionLayoutChanged = sessionParams.getMarginStart() != targetStartMargin
                    || sessionParams.height != targetSessionHeight
                    || sessionParams.width != targetSessionWidth;
                // The chip's height is row geometry too; the interactive writer owns it as well.
                if (sessionLayoutChanged && !interactiveGeometryOwnsRow) {
                    sessionParams.setMarginStart(targetStartMargin);
                    sessionParams.height = targetSessionHeight;
                    sessionParams.width = targetSessionWidth;
                    if (sessionParams instanceof android.widget.LinearLayout.LayoutParams) {
                        ((android.widget.LinearLayout.LayoutParams) sessionParams).gravity =
                            Gravity.CENTER_VERTICAL;
                    }
                    sessions.setLayoutParams(sessionParams);
                }
                if (sessions instanceof com.termux.app.statusbar.SessionsIndicatorView) {
                    ((com.termux.app.statusbar.SessionsIndicatorView) sessions).setSurfaceStyle(
                        capsule, resolveStatusBarCapsuleCornerRadiusPx(targetHeight));
                }
            }

            com.termux.app.terminal.TerminalWindowBar windows =
                findViewById(R.id.terminal_window_bar);
            if (windows != null) {
                windows.setSurfaceStyle(capsule,
                    resolveStatusBarCapsuleCornerRadiusPx(targetHeight));
            }

            View statusWidgets = findViewById(R.id.terminal_status_widgets);
            if (statusWidgets != null) {
                int targetEndPadding = statusBarContentEdgeInsetPx(capsule);
                if (statusWidgets.getPaddingEnd() != targetEndPadding) {
                    statusWidgets.setPaddingRelative(statusWidgets.getPaddingStart(),
                        statusWidgets.getPaddingTop(), targetEndPadding,
                        statusWidgets.getPaddingBottom());
                }
            }
            if (host instanceof com.termux.app.statusbar.StatusBarSwipeLayout) {
                ((com.termux.app.statusbar.StatusBarSwipeLayout) host).setCollapsed(collapsed);
            }
        }
        applyFullStatusBarOutline(host, isFullStatusBarEngaged()
            ? mStatusBarSurfaceOutline.fullProgress() : 0f);
    }

    /**
     * One outline clips every status-pane layer, including live blur and wallpaper frost. Normal
     * Rounded keeps its capsule radius; normal Default remains square. FULL converges on the same
     * status radius used by the existing top-pane/dock language instead of introducing a second
     * radius, and its bottom edge therefore rounds continuously with the height spring.
     */
    private void applyFullStatusBarOutline(@NonNull View host, float fullProgress) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        boolean capsule = isRoundedDockStyle();
        boolean collapsed = mPreferences != null && mPreferences.isTopPaneClockCollapsed();
        float fullRadius = resolveStatusBarCapsuleCornerRadiusPx(
            targetStatusBarHeightPx(true, collapsed));
        mStatusBarSurfaceOutline.setFrame(capsule ? fullRadius : 0f, fullRadius, fullProgress);
        if (host.getOutlineProvider() != mStatusBarSurfaceOutline)
            host.setOutlineProvider(mStatusBarSurfaceOutline);
        host.setClipToOutline(mStatusBarSurfaceOutline.clipsCorners());
        host.invalidateOutline();
        if (host instanceof com.termux.app.statusbar.StatusBarSwipeLayout) {
            ((com.termux.app.statusbar.StatusBarSwipeLayout) host)
                .setGlassRim(mStatusBarSurfaceOutline.radiusPx(), fullProgress);
        }
    }

    /**
     * Symmetric outer screen inset for one editable surface. The floating capsule sits at the
     * configured dp (10dp by default · design redline · Outer margin 10); the edge-to-edge default
     * shape stays flush until the user pushes the inset past that baseline.
     */
    private int resolveSurfaceHorizontalInsetPx(int configuredDp, boolean capsule) {
        int insetDp = TermuxAppSharedPreferences.clampSurfaceHorizontalInset(configuredDp);
        if (!capsule)
            insetDp = Math.max(0, insetDp
                - TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET);
        return Math.round(dpToPx(insetDp));
    }

    private int resolveDockCapsuleHorizontalMarginPx() {
        return resolveSurfaceHorizontalInsetPx(mPreferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET
            : mPreferences.getDockHorizontalInset(), true);
    }

    private int resolveDockHorizontalInsetPx() {
        return resolveSurfaceHorizontalInsetPx(mPreferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET
            : mPreferences.getDockHorizontalInset(), isRoundedDockStyle());
    }

    /** The dock's outer screen margin, shared with the app drawer plane's seed rect. */
    public int getDockHorizontalInsetPx() {
        return resolveDockHorizontalInsetPx();
    }

    private int resolveInAppKeyboardHorizontalInsetPx() {
        return resolveSurfaceHorizontalInsetPx(mPreferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HORIZONTAL_INSET
            : mPreferences.getInAppKeyboardHorizontalInset(), isInAppKeyboardCapsule());
    }

    private int resolveStatusBarHorizontalInsetPx() {
        return resolveSurfaceHorizontalInsetPx(mPreferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET
            : mPreferences.getStatusBarHorizontalInset(), isRoundedDockStyle());
    }

    /** Internal row inset only; the floating capsule's outer screen margin remains unchanged. */
    private int statusBarContentEdgeInsetPx(boolean capsule) {
        return Math.round(dpToPx(capsule ? 8 : 3));
    }

    private float resolveStatusBarCapsuleCornerRadiusPx(int surfaceHeightPx) {
        int configuredRadius = mPreferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_CORNER_RADIUS
            : mPreferences.getStatusBarCornerRadius();
        if (configuredRadius >= 0) {
            return Math.min(dpToPx(configuredRadius), surfaceHeightPx / 2f);
        }
        return Math.max(dpToPx(16), Math.min(dpToPx(26), surfaceHeightPx / 2f));
    }

    private int targetStatusBarHeightPx(boolean capsule, boolean collapsed) {
        return Math.round(dpToPx(collapsed ? capsule ? 30 : 32 : capsule ? 100 : 96));
    }

    private int resolveDockCapsuleContentInsetPx() {
        // Inner padding between the capsule border and the row content. Trimmed slightly from the
        // 16dp redline so the rows (and the 2-row extra keys) sit a touch closer to the edges.
        return resolveDockCapsuleHorizontalMarginPx() + Math.round(dpToPx(14));
    }

    private int resolveDockCapsuleExtraKeysInsetPx() {
        return resolveDockCapsuleContentInsetPx() + Math.round(dpToPx(2));
    }

    private int resolveDockCapsuleAppsTopPaddingPx() {
        // Top space equals bottom padding plus the 3dp icon/A-Z indicator band. Together with the
        // paired bottom formula this preserves the old total inset while centering the icon row.
        int totalPadding = resolveDockCapsuleAppsTotalPaddingPx();
        int indicatorBand = Math.round(dpToPx(3));
        return Math.min(totalPadding, Math.max(0, (totalPadding + indicatorBand + 1) / 2));
    }

    private int resolveDockCapsuleAppsBottomPaddingPx() {
        return Math.max(0, resolveDockCapsuleAppsTotalPaddingPx() - resolveDockCapsuleAppsTopPaddingPx());
    }

    private int resolveDockCapsuleAppsTotalPaddingPx() {
        float progress = mPreferences != null
            ? resolveDockSizeProgress(mPreferences.getAppLauncherBarHeightScale())
            : 1f;
        float density = getResources().getDisplayMetrics().density;
        // Exactly preserve the previous top (6dp + 7dp*progress) plus 1dp bottom budget.
        return Math.round((6f + progress * 7f) * density) + Math.round(density);
    }

    private int resolveDefaultDockAppsTopPaddingPx() {
        // 6dp above equals 3dp below plus the fixed 3dp icon/A-Z band.
        return Math.round(dpToPx(6));
    }

    private int resolveDefaultDockAppsBottomPaddingPx() {
        return Math.round(dpToPx(3));
    }

    private int resolveDockCapsuleBottomGapPx() {
        return Math.round(dpToPx(6));
    }

    /** Also the command palette's open-state radius, so the two glass surfaces read as one kit. */
    public float resolveDockCapsuleCornerRadiusPx(int surfaceHeightPx) {
        int configuredRadius = mPreferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS
            : mPreferences.getAppLauncherDockCornerRadius();
        if (configuredRadius >= 0) {
            return Math.min(dpToPx(configuredRadius), surfaceHeightPx / 2f);
        }
        // Follow-the-style radius, shared with the status surface and the terminal border. The
        // design redline's 26 read as a lozenge on a short dock and cost the terminal a wide corner
        // arc; 20 is the same family, quieter, and still capsule-like at dock height.
        return Math.min(dpToPx(
            TermuxPreferenceConstants.TERMUX_APP.DEFAULT_ROUNDED_SURFACE_CORNER_RADIUS_DP),
            surfaceHeightPx / 2f);
    }

    private void applyDockSurfaceShape(@NonNull View surface, boolean capsule, int surfaceHeightPx) {
        if (!capsule) {
            surface.setBackground(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Clip the normal dock to its own rectangular bounds so the reactive edge-glow's
                // outward blur can't spill past the dock edges and make it look wider.
                surface.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                surface.setClipToOutline(true);
            }
            return;
        }

        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setCornerRadius(resolveDockCapsuleCornerRadiusPx(surfaceHeightPx));
        // Barely-there containing stroke; the AGSL shader draws the dark glass contour + bevel at the
        // edge, so a visible outline here would read as a drawn border ("inside rim") over the glass.
        outline.setStroke(Math.max(1, Math.round(dpToPx(1))), withAlphaComponent(resolveAccessoryOutlineColor(), 18));
        surface.setBackground(outline);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            surface.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            surface.setClipToOutline(true);
        }
    }

    private void configureAccessoryTopEdgeFx(boolean visible, float barAlpha) {
        View edgeFx = findViewById(R.id.accessory_top_edge_fx);
        if (edgeFx == null) {
            return;
        }
        if (!visible) {
            edgeFx.setVisibility(View.GONE);
            edgeFx.setBackground(null);
            return;
        }

        // No white top highlight band — it read as a plastic sheen. The crisp glass top edge is now
        // produced by the AGSL refraction shader on the backdrop. Keep only a faint shadow seam.
        int highlight = Color.TRANSPARENT;
        int shadow = withAlphaComponent(resolveAccessoryOutlineColor(), Math.round(18f * Math.max(0f, barAlpha)));
        GradientDrawable edge = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { highlight, shadow, Color.TRANSPARENT }
        );
        edgeFx.setBackground(edge);
        edgeFx.setVisibility(View.VISIBLE);
    }

    /** The subtle hairline between the A–Z row and the extra-keys (3rd) row, per the design. */
    private void configureExtraKeysDivider(boolean visible, float materialAlpha) {
        View divider = findViewById(R.id.extrakeys_divider);
        if (divider == null) {
            return;
        }
        if (!visible || materialAlpha <= 0f) {
            divider.setVisibility(View.GONE);
            return;
        }
        divider.setBackgroundColor(withAlphaComponent(resolveAccessoryOutlineColor(),
            Math.round(70f * Math.max(0f, Math.min(1f, materialAlpha)))));
        divider.setVisibility(View.VISIBLE);
    }

    private void configureBackgroundBlur(int blurViewId, int backgroundViewId, boolean isBlurEnabled, float surfaceAlpha, int blurRadiusDp) {
        View blurView = findViewById(blurViewId);
        View backgroundView = findViewById(backgroundViewId);
        applyRealtimeBlurRadius(blurView, blurRadiusDp);
        blurView.setVisibility(isBlurEnabled ? View.VISIBLE : View.GONE);
        backgroundView.setAlpha(surfaceAlpha);
    }

    private static final class AccessoryRenderState {
        final boolean toolbarShown;
        final boolean keyboardShown;
        final int keyboardHeight;
        final boolean blurEnabled;
        final boolean appsRowEnabled;
        final boolean azRowEnabled;
        final boolean extraKeysRowEnabled;
        final float barAlpha;
        final int blurRadiusDp;

        AccessoryRenderState(boolean toolbarShown, boolean keyboardShown, int keyboardHeight,
                             boolean blurEnabled, boolean appsRowEnabled, boolean azRowEnabled,
                             boolean extraKeysRowEnabled, float barAlpha, int blurRadiusDp) {
            this.toolbarShown = toolbarShown;
            this.keyboardShown = keyboardShown;
            this.keyboardHeight = Math.max(0, keyboardHeight);
            this.blurEnabled = blurEnabled;
            this.appsRowEnabled = appsRowEnabled;
            this.azRowEnabled = azRowEnabled;
            this.extraKeysRowEnabled = extraKeysRowEnabled;
            this.barAlpha = barAlpha;
            this.blurRadiusDp = blurRadiusDp;
        }
    }

    private static final class DockLayoutMetrics {
        final int appsBarHeightPx;
        final int indicatorBandHeightPx;
        final int azRowHeightPx;
        final int interRowGapPx;

        DockLayoutMetrics(int appsBarHeightPx, int indicatorBandHeightPx, int azRowHeightPx, int interRowGapPx) {
            this.appsBarHeightPx = Math.max(0, appsBarHeightPx);
            this.indicatorBandHeightPx = Math.max(0, indicatorBandHeightPx);
            this.azRowHeightPx = Math.max(0, azRowHeightPx);
            this.interRowGapPx = Math.max(0, interRowGapPx);
        }

        int combinedHeight(int toolbarHeightPx, boolean extraKeysRowEnabled) {
            return AccessoryStackLayoutPolicy.computeCombinedHeight(
                appsBarHeightPx > 0,
                azRowHeightPx > 0,
                extraKeysRowEnabled,
                appsBarHeightPx,
                azRowHeightPx,
                toolbarHeightPx,
                indicatorBandHeightPx);
        }
    }

    @NonNull
    private AccessoryRenderState buildAccessoryRenderState() {
        boolean keyboardShown = isInAppKeyboardShown();
        int keyboardHeight = keyboardShown ? measureInAppKeyboardHeight() : 0;
        if (mPreferences == null) {
            return new AccessoryRenderState(false, keyboardShown, keyboardHeight,
                false, false, false, false, 1.0f, 0);
        }
        // Mirror the metrics-side collapse: zero-height dock rows still paint at full size through
        // the stack's clipChildren=false chain, so the render state must hide them outright. The
        // landscape launcher surface is the left dock rail instead.
        boolean appsRowEnabled = mPreferences.isAppLauncherAppsRowEnabled()
            && !isLandscapeOrientation();
        boolean azRowEnabled = mPreferences.isAppLauncherAzRowEnabled()
            && !isLandscapeOrientation();
        boolean extraKeysRowEnabled = mPreferences.isAppLauncherExtraKeysRowEnabled()
            && mPreferences.shouldShowTerminalToolbar();
        boolean dockShown = appsRowEnabled || azRowEnabled || extraKeysRowEnabled;
        int blurRadiusDp = getEffectiveExtraKeysBlurRadius();
        float barAlpha = mPreferences.getAppBarOpacity() / 100f;
        return new AccessoryRenderState(
            dockShown,
            keyboardShown,
            keyboardHeight,
            dockBlurEnabled(blurRadiusDp),
            appsRowEnabled,
            azRowEnabled,
            extraKeysRowEnabled,
            barAlpha,
            blurRadiusDp
        );
    }

    private boolean isInAppKeyboardShown() {
        View keyboardContainer = findViewById(R.id.inapp_keyboard_container);
        if (mInAppKeyboard != null) {
            return mInAppKeyboard.isVisible();
        }
        return keyboardContainer != null && keyboardContainer.getVisibility() != View.GONE;
    }

    private int measureInAppKeyboardHeight() {
        View keyboardContainer = findViewById(R.id.inapp_keyboard_container);
        if (keyboardContainer == null)
            return 0;
        View availableRoot = findViewById(R.id.activity_termux_root_relative_layout);
        int width = availableRoot != null ? availableRoot.getWidth() : 0;
        int availableHeight = availableRoot != null ? availableRoot.getHeight() : 0;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (width <= 0)
            width = metrics.widthPixels;
        if (availableHeight <= 0)
            availableHeight = metrics.heightPixels;
        if (mAttachedInAppKeyboardView instanceof Keyboard2View) {
            // The keyboard is measured here against the full content root, but RelativeLayout later
            // measures it inside the shorter exact accessory stack. Keep its fractional height cap
            // tied to this stable root height so both AT_MOST passes resolve identically.
            ((Keyboard2View) mAttachedInAppKeyboardView)
                .setHeightCapReferencePx(Math.max(0, availableHeight));
        }
        if (!mInAppKeyboardHeightDirty && mDesiredInAppKeyboardHeightPx > 0
            && mInAppKeyboardMeasureWidthPx == width
            && mInAppKeyboardAvailableHeightPx == availableHeight) {
            return mDesiredInAppKeyboardHeightPx;
        }
        // Measure the wrap-content keyboard independently of accessory_stack_container. The stack's
        // current exact height may have been computed from an older keyboard measurement, so using
        // its normal parent-provided spec here creates a shrinking feedback loop. This AT_MOST spec
        // is always based on the full content root and lets Keyboard2View apply its orientation cap.
        keyboardContainer.measure(
            View.MeasureSpec.makeMeasureSpec(Math.max(0, width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(Math.max(0, availableHeight), View.MeasureSpec.AT_MOST));
        mDesiredInAppKeyboardHeightPx = Math.max(0, keyboardContainer.getMeasuredHeight());
        mInAppKeyboardMeasureWidthPx = width;
        mInAppKeyboardAvailableHeightPx = availableHeight;
        mInAppKeyboardHeightDirty = false;
        return mDesiredInAppKeyboardHeightPx;
    }

    static boolean shouldShowAccessoryStack(boolean toolbarShown, boolean keyboardShown) {
        return toolbarShown || keyboardShown;
    }

    static int computeAccessoryStackHeight(int dockContentHeight, int terminalFlushPadding,
                                           int keyboardHeight) {
        return Math.max(0, dockContentHeight)
            + Math.max(0, terminalFlushPadding)
            + Math.max(0, keyboardHeight);
    }

    private int getEffectiveExtraKeysBlurRadius() {
        if (mPreferences == null) {
            return 0;
        }
        int blurRadiusDp = mPreferences.getExtraKeysBlurRadius();
        if (blurRadiusDp <= 0 || isLiveWallpaperActive()) {
            return 0;
        }
        return blurRadiusDp;
    }

    private int getEffectiveStatusBarBlurRadius() {
        if (mPreferences == null) return 0;
        int blurRadiusDp = mPreferences.getStatusBarBlurRadius();
        return blurRadiusDp <= 0 || isLiveWallpaperActive() ? 0 : blurRadiusDp;
    }

    private boolean isLiveWallpaperActive() {
        try {
            WallpaperInfo wallpaperInfo = WallpaperManager.getInstance(this).getWallpaperInfo();
            return wallpaperInfo != null;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to detect live wallpaper state", e);
            return false;
        }
    }

    /**
     * The dock's rectangle on screen, for a surface that wants to sit exactly where the dock is.
     *
     * @return false when there is no dock laid out — a terminal-only install, or before first layout.
     */
    public boolean dockBoundsOnScreen(@NonNull Rect out) {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null || accessoryContainer.getVisibility() != View.VISIBLE
            || accessoryContainer.getWidth() <= 0 || accessoryContainer.getHeight() <= 0) {
            return false;
        }
        int[] location = new int[2];
        accessoryContainer.getLocationOnScreen(location);
        out.set(location[0], location[1], location[0] + accessoryContainer.getWidth(),
            location[1] + accessoryContainer.getHeight());
        return true;
    }

    private void configureExtraKeysBackground() {
        applyAccessoryRenderState(buildAccessoryRenderState());
    }

    private boolean shouldUseAccessoryRenderEffectBlur(@NonNull AccessoryRenderState state) {
        return state.toolbarShown
            && state.blurEnabled;
    }

    private void clearAccessoryRenderEffectBackdrop() {
        ImageView backdrop = findViewById(R.id.accessory_blur_backdrop);
        if (backdrop == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdrop.setRenderEffect(null);
        }
        backdrop.setImageDrawable(null);
        backdrop.setVisibility(View.GONE);
        mGlassParamsValid = false; // no live backdrop shader -> key lens falls back to the bubble border
        mAccessoryBackdropDirty = true;
        mLastAccessoryBackdropBlurRadiusDp = -1;
        mLastAccessoryBackdropManagedSource = false;
        mLastAccessoryBackdropTargetRect.setEmpty();
    }

    private boolean shouldShowDecorNavBarSurface(@NonNull AccessoryRenderState state) {
        // Floating capsules leave the gesture-pill inset showing wallpaper; edge-to-edge surfaces
        // (dock glass, or the embedded keyboard's own background) continue under the pill. The
        // keyboard's shape is decoupled from the dock style via the dock-match mode, so it owns
        // its own capsule decision.
        return shouldShowDecorNavBarSurface(state.toolbarShown, state.keyboardShown,
            mNavBarHeight, mLastImeVisible || isImeVisible(), isRoundedDockStyle(),
            isInAppKeyboardCapsule());
    }

    static boolean shouldShowDecorNavBarSurface(boolean toolbarShown, boolean keyboardShown,
                                                int navBarHeight, boolean imeVisible,
                                                boolean roundedDockStyle,
                                                boolean keyboardCapsule) {
        if (navBarHeight <= 0 || imeVisible)
            return false;
        if (keyboardShown)
            return !keyboardCapsule;
        return toolbarShown && !roundedDockStyle;
    }

    private boolean shouldUseDockDecorNavBarSurface(@NonNull AccessoryRenderState state) {
        // The dock body always renders in-content (accessory_surface_host + refraction), in front of
        // the terminal dim, in BOTH keyboard states — so keyboard-off no longer routes the dock
        // through the behind-content decor overlay (which the terminal dim darkened, making it read
        // darker than the keyboard-on dock). The decor overlay is now the under-pill nav strip only.
        return false;
    }

    /**
     * The default edge-to-edge dock and a glass-matched keyboard are one rectangular material.
     * Render one backdrop through both instead of placing two independently cropped glass layers
     * next to each other. Floating/capsule styling deliberately remains on its separate path.
     */
    private boolean shouldUseUnifiedDefaultKeyboardGlassSurface(@NonNull AccessoryRenderState state) {
        // A scheme background color or a non-default background opacity must repaint only the
        // keyboard, not the material it would share with the dock, so either drops the keyboard
        // to its own local surface path.
        return shouldUseUnifiedDefaultKeyboardGlassSurface(state.toolbarShown,
            state.keyboardShown, isRoundedDockStyle(), isInAppKeyboardGlassSurface())
            && !hasInAppKeyboardBackgroundOverride();
    }

    static boolean shouldUseUnifiedDefaultKeyboardGlassSurface(boolean toolbarShown,
                                                                boolean keyboardShown,
                                                                boolean roundedDockStyle,
                                                                boolean keyboardGlassSurface) {
        return toolbarShown && keyboardShown && !roundedDockStyle && keyboardGlassSurface;
    }

    /**
     * Any blurred glass keyboard needs a destination-backdrop gate on a fresh open — the unified
     * default-dock surface waits on the shared accessory crop, the capsule/local surface waits on
     * its own keyboard backdrop bitmap. Without the gate the first frame draws base-color glass.
     */
    static boolean shouldDeferInAppKeyboardReveal(boolean openingFromGone,
                                                   boolean glassSurface,
                                                   boolean blurEnabled,
                                                   boolean backdropReady) {
        return openingFromGone && glassSurface && blurEnabled && !backdropReady;
    }

    /** Readiness of the keyboard-local (non-unified) blurred backdrop for the current target. */
    private boolean isInAppKeyboardLocalBackdropReady(@NonNull AccessoryRenderState state) {
        View surfaceHost = findViewById(R.id.inapp_keyboard_view_host);
        if (surfaceHost == null) return true;
        if (mInAppKeyboardBackdropBitmap == null || mInAppKeyboardBackdropDirty) return false;
        Rect targetRect = buildInAppKeyboardBackdropTargetRect(state, surfaceHost);
        return targetRect == null || mLastInAppKeyboardBackdropTargetRect.equals(targetRect);
    }

    private void ensureDecorNavBarSurfaceOverlay() {
        if (mDecorNavBarSurfaceOverlay != null) {
            return;
        }
        if (getWindow() == null || !(getWindow().getDecorView() instanceof FrameLayout)) {
            return;
        }

        FrameLayout decorRoot = (FrameLayout) getWindow().getDecorView();
        FrameLayout surfaceOverlay = new FrameLayout(this);
        surfaceOverlay.setVisibility(View.GONE);
        surfaceOverlay.setClickable(false);
        surfaceOverlay.setFocusable(false);
        surfaceOverlay.setClipChildren(true);
        surfaceOverlay.setClipToPadding(true);
        surfaceOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        ImageView blurBackdrop = new ImageView(this);
        blurBackdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        blurBackdrop.setVisibility(View.GONE);
        surfaceOverlay.addView(blurBackdrop, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        View tintOverlay = new View(this);
        surfaceOverlay.addView(tintOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            Gravity.BOTTOM
        );
        // Add as the TOPMOST decor child (not index 0). At index 0 the overlay sat behind the
        // activity content, so the terminal/root dim (activity_termux_root_view's background, set by
        // applyUnifiedBackgroundDim) composited OVER the finished nav glass — while the dock, being a
        // descendant of that root, draws after the dim and stays bright. That fixed, opacity- and
        // wallpaper-independent darkening of the under-pill strip is removed by drawing the strip
        // after the dim, matching the dock. The strip is non-clickable/non-focusable and confined to
        // the gesture-nav inset, and the system gesture pill renders above the app window regardless.
        decorRoot.addView(surfaceOverlay, overlayParams);

        mDecorNavBarSurfaceOverlay = surfaceOverlay;
        mDecorNavBarBlurBackdrop = blurBackdrop;
        mDecorNavBarTintOverlay = tintOverlay;
        mDecorNavBarBackdropDirty = true;
    }

    private void removeDecorNavBarSurfaceOverlay() {
        if (mDecorNavBarSurfaceOverlay == null) {
            return;
        }
        clearDecorNavBarBackdrop();
        ViewParent parent = mDecorNavBarSurfaceOverlay.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(mDecorNavBarSurfaceOverlay);
        }
        mDecorNavBarSurfaceOverlay = null;
        mDecorNavBarBlurBackdrop = null;
        mDecorNavBarTintOverlay = null;
    }

    private void hideDecorNavBarSurfaceOverlay(boolean clearBackdrop) {
        if (mDecorNavBarSurfaceOverlay != null) {
            mDecorNavBarSurfaceOverlay.setVisibility(View.GONE);
        }
        if (clearBackdrop) {
            clearDecorNavBarBackdrop();
        }
    }

    private void applyDecorNavBarSurfaceBounds(@NonNull FrameLayout overlay, boolean visible) {
        ViewGroup.LayoutParams layoutParams = overlay.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layoutParams;
        int targetHeight = visible ? resolveDecorNavBarSurfaceHeightPx() : 0;
        int targetHorizontalMargin = 0;
        int targetBottomMargin = 0;
        applyDockSurfaceShape(overlay, false, targetHeight);
        if (params.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            params.height != targetHeight ||
            params.gravity != Gravity.BOTTOM ||
            params.leftMargin != targetHorizontalMargin ||
            params.rightMargin != targetHorizontalMargin ||
            params.bottomMargin != targetBottomMargin) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = targetHeight;
            params.gravity = Gravity.BOTTOM;
            params.leftMargin = targetHorizontalMargin;
            params.rightMargin = targetHorizontalMargin;
            params.bottomMargin = targetBottomMargin;
            overlay.setLayoutParams(params);
            mDecorNavBarBackdropDirty = true;
        }
    }

    private void applyDecorNavBarSurfaceState(@NonNull AccessoryRenderState state) {
        boolean visible = shouldShowDecorNavBarSurface(state);
        if (!visible) {
            hideDecorNavBarSurfaceOverlay(true);
            return;
        }

        ensureDecorNavBarSurfaceOverlay();
        FrameLayout overlay = mDecorNavBarSurfaceOverlay;
        if (overlay == null) {
            return;
        }

        applyDecorNavBarSurfaceBounds(overlay, true);

        if (mDecorNavBarTintOverlay != null) {
            if (state.keyboardShown && !isInAppKeyboardGlassSurface()) {
                // The activity content ends at fitSystemWindows' bottom boundary. Continue the
                // opaque keyboard surface through the decor-owned gesture-navigation inset.
                mDecorNavBarTintOverlay.setBackgroundColor(resolveInAppKeyboardBackgroundColor());
            } else {
                // The under-pill strip is the bottom slice [f, 1] of the shared light model; the
                // in-content surface above it (dock stack when keyboard-off, keyboard host when
                // keyboard-on) renders [0, f]. Both states stack a content-level surface + this
                // nav-only strip, so a single foot lands under the pill identically either way.
                mDecorNavBarTintOverlay.setBackground(
                    buildDockGlassSurface(state.barAlpha, defaultDockGlassFootFraction(), 1f, false));
            }
            mDecorNavBarTintOverlay.setAlpha(1f);
            mDecorNavBarTintOverlay.setVisibility(View.VISIBLE);
        }

        if (state.blurEnabled && (!state.keyboardShown || isInAppKeyboardGlassSurface())) {
            updateDecorNavBarBackdrop(state);
        } else {
            clearDecorNavBarBackdrop();
        }

        overlay.setVisibility(View.VISIBLE);
    }

    @Nullable
    private Rect buildDecorNavBarBackdropTargetRect(int topOverscanPx) {
        if (getWindow() == null || getWindow().getDecorView() == null || resolveDecorNavBarSurfaceHeightPx() <= 0) {
            return null;
        }
        View decorView = getWindow().getDecorView();
        int decorWidth = decorView.getWidth();
        int decorHeight = decorView.getHeight();
        if (decorWidth <= 0 || decorHeight <= 0) {
            return null;
        }

        decorView.getLocationOnScreen(mTmpParentLocation);
        int horizontalMargin = 0;
        int bottomMargin = 0;
        int surfaceHeight = Math.min(Math.max(1, resolveDecorNavBarSurfaceHeightPx()), decorHeight);
        int bottom = mTmpParentLocation[1] + decorHeight - bottomMargin;
        // Overscan the crop UPWARD past the strip top (the seam with the dock/keyboard) so the
        // refraction edge band lands above the visible strip; the dock overscans down by the same
        // amount, so their blurred wallpaper meets seamlessly at the seam.
        int top = Math.max(mTmpParentLocation[1], bottom - surfaceHeight - Math.max(0, topOverscanPx));
        return new Rect(
            mTmpParentLocation[0] + horizontalMargin,
            top,
            mTmpParentLocation[0] + decorWidth - horizontalMargin,
            bottom
        );
    }

    private int resolveDecorNavBarSurfaceHeightPx() {
        if (mNavBarHeight <= 0) {
            return 0;
        }
        if (isRoundedDockStyle()) {
            View accessoryContainer = findViewById(R.id.accessory_stack_container);
            int dockHeight = accessoryContainer != null ? Math.max(0, accessoryContainer.getHeight()) : 0;
            return dockHeight + mNavBarHeight + resolveDockCapsuleBottomGapPx();
        }
        // Default dock renders its body in-content; the decor overlay is the under-pill nav strip
        // only. Size it from the in-content surface's actual bottom edge down to the screen bottom
        // rather than from mNavBarHeight — the content's applied bottom inset can differ from the
        // system-bars height by a few px, which otherwise leaves a thin wallpaper gap (or an overlap)
        // between the dock/keyboard bottom and the strip. This makes them meet exactly.
        int measured = measuredUnderPillStripHeightPx();
        int targetHeight = measured > 0 ? measured : mNavBarHeight;
        if (mPendingInAppKeyboardCloseGeometry) {
            // Closing makes the keyboard GONE before RelativeLayout has produced dock-only bounds.
            // During that pass measuredUnderPillStripHeightPx() samples the old full-height host and
            // misses the newly restored flush padding, exposing a sharp wallpaper seam. Oversizing
            // the decor glass by that known inset is safe (it sits behind the dock) and keeps the
            // seam covered until the destination layout is observed.
            View accessoryContainer = findViewById(R.id.accessory_stack_container);
            int pendingFlushInset = accessoryContainer != null
                ? Math.max(0, accessoryContainer.getPaddingBottom()) : 0;
            targetHeight = Math.max(targetHeight, mNavBarHeight + pendingFlushInset);
        }
        return targetHeight;
    }

    /**
     * Distance from the in-content surface's bottom edge (keyboard host when shown, else the dock
     * stack) to the screen bottom, so the under-pill decor strip fills exactly that span. Returns 0
     * when geometry is not yet laid out (callers fall back to {@code mNavBarHeight}).
     */
    private int measuredUnderPillStripHeightPx() {
        if (getWindow() == null || getWindow().getDecorView() == null) {
            return 0;
        }
        // Use the glass host, not the stack container: the container has a bottom padding (flush
        // centering) so its glass surface ends a few px above the container bottom. Sizing from the
        // container left that padding band uncovered — the ~3-4dp wallpaper gap the user saw.
        View content = findViewById(isInAppKeyboardShown()
            ? R.id.inapp_keyboard_view_host : R.id.accessory_surface_host);
        if (content == null || content.getHeight() <= 0 || content.getWidth() <= 0) {
            return 0;
        }
        View decorView = getWindow().getDecorView();
        if (decorView.getHeight() <= 0) {
            return 0;
        }
        int[] decorLoc = new int[2];
        int[] contentLoc = new int[2];
        decorView.getLocationOnScreen(decorLoc);
        content.getLocationOnScreen(contentLoc);
        int screenBottom = decorLoc[1] + decorView.getHeight();
        int contentBottom = contentLoc[1] + content.getHeight();
        int height = screenBottom - contentBottom;
        // Guard against transient layouts that would collapse or overshoot the strip.
        if (height <= 0 || height > mNavBarHeight * 3) {
            return 0;
        }
        return height;
    }

    private int resolveInAppKeyboardBackgroundColor() {
        if (mAttachedInAppKeyboardView instanceof Keyboard2View)
            return ((Keyboard2View) mAttachedInAppKeyboardView).getKeyboardBackgroundColor();
        return MaterialColors.getColor(this,
            com.google.android.material.R.attr.colorSurface,
            ContextCompat.getColor(this, R.color.termux_surface_base));
    }

    /**
     * Keyboard-background color assigned in the color scheme editor, or null for the theme's own
     * surface. Memoized on the raw pref JSON — dynamic slots re-resolve against Material roles,
     * which only move with a configuration change that recreates this activity anyway.
     */
    @Nullable
    private Integer resolveInAppKeyboardSchemeBackgroundColor() {
        if (mPreferences == null)
            return null;
        String json = mPreferences.getInAppKeyboardColorScheme();
        if (!json.equals(mInAppKeyboardSchemeBackgroundJson)) {
            mInAppKeyboardSchemeBackgroundJson = json;
            mInAppKeyboardSchemeBackgroundColor =
                com.termux.app.terminal.inappkeyboard.InAppKeyboardColorScheme
                    .fromJson(this, json).resolvedKeyboardBackground();
        }
        return mInAppKeyboardSchemeBackgroundColor;
    }

    private int getInAppKeyboardBackgroundOpacityPercent() {
        return mPreferences != null
            ? mPreferences.getInAppKeyboardBackgroundOpacity()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BACKGROUND_OPACITY;
    }

    /** True when the scheme's background color or the opacity slider repaints the surface. */
    private boolean hasInAppKeyboardBackgroundOverride() {
        return resolveInAppKeyboardSchemeBackgroundColor() != null
            || getInAppKeyboardBackgroundOpacityPercent()
                != TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BACKGROUND_OPACITY;
    }

    /** The keyboard always renders on the same glass material path as the dock. */
    private boolean isInAppKeyboardGlassSurface() {
        return true;
    }

    /**
     * Fraction of the combined content+under-pill glass height occupied by the in-content surface
     * (the keyboard host when the keyboard is shown, otherwise the dock stack). The content surface
     * and the under-pill nav strip render adjacent slices ({@code [0, f]} and {@code [f, 1]}) of one
     * shared light model so a single dark foot lands under the pill in both keyboard states. Returns
     * 1 (no slicing) for the floating capsule or when there is no gesture-nav strip below.
     */
    private float defaultDockGlassFootFraction() {
        if (isRoundedDockStyle()) {
            return 1f;
        }
        int navHeight = mNavBarHeight;
        if (navHeight <= 0) {
            return 1f;
        }
        View contentHost = findViewById(isInAppKeyboardShown()
            ? R.id.inapp_keyboard_view_host : R.id.accessory_stack_container);
        int contentHeight = contentHost != null ? contentHost.getHeight() : 0;
        if (contentHeight <= 0) {
            return 1f;
        }
        return contentHeight / (float) (contentHeight + navHeight);
    }

    /** Keyboard shape always follows the single global surface shape. */
    private boolean isInAppKeyboardShapeMatch() {
        return true;
    }

    /** True when the keyboard renders as the floating Rounded surface. */
    private boolean isInAppKeyboardCapsule() {
        return isRoundedDockStyle() && isInAppKeyboardShapeMatch();
    }

    /**
     * Applies the in-app keyboard's surface treatment: rounded shape (margins + rounded clip +
     * inner padding) when the Rounded surface style is active, and the dock's blurred-wallpaper +
     * tinted-glass stack behind the keys. The glass stack is
     * rendered as the host's background drawable (a pre-blurred wallpaper crop under the same
     * tint used by {@link #buildDockGlassSurface}) so the wrap-content keyboard measurement is
     * never affected by extra sibling views.
     */
    private void applyInAppKeyboardSurfaceState(@NonNull AccessoryRenderState state) {
        View surfaceHost = findViewById(R.id.inapp_keyboard_view_host);
        if (surfaceHost == null) {
            return;
        }
        if (!state.keyboardShown) {
            surfaceHost.setBackground(null);
            clearInAppKeyboardBackdrop();
            return;
        }

        boolean capsule = isInAppKeyboardCapsule();
        boolean glassTheme = isInAppKeyboardGlassSurface();
        int horizontalMargin = resolveInAppKeyboardHorizontalInsetPx();
        int bottomMargin = capsule ? resolveDockCapsuleBottomGapPx() : 0;
        int topMargin = capsule ? Math.round(dpToPx(4)) : 0;
        int innerPadding = capsule ? Math.round(dpToPx(6)) : 0;
        ViewGroup.LayoutParams layoutParams = surfaceHost.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) layoutParams;
            if (params.leftMargin != horizontalMargin || params.rightMargin != horizontalMargin
                || params.topMargin != topMargin || params.bottomMargin != bottomMargin) {
                params.leftMargin = horizontalMargin;
                params.rightMargin = horizontalMargin;
                params.topMargin = topMargin;
                params.bottomMargin = bottomMargin;
                surfaceHost.setLayoutParams(params);
                mInAppKeyboardHeightDirty = true;
                mInAppKeyboardBackdropDirty = true;
            }
        }
        if (surfaceHost.getPaddingLeft() != innerPadding
            || surfaceHost.getPaddingTop() != innerPadding
            || surfaceHost.getPaddingRight() != innerPadding
            || surfaceHost.getPaddingBottom() != innerPadding) {
            surfaceHost.setPadding(innerPadding, innerPadding, innerPadding, innerPadding);
            mInAppKeyboardHeightDirty = true;
            mInAppKeyboardBackdropDirty = true;
        }

        float cornerRadiusPx = capsule ? resolveDockCapsuleCornerRadiusPx(Integer.MAX_VALUE) : 0f;
        applyInAppKeyboardSurfaceClip(surfaceHost, capsule, cornerRadiusPx);
        if (shouldUseUnifiedDefaultKeyboardGlassSurface(state)) {
            // Once accessory_surface_host has actually laid out at the expanded height and its
            // matching crop is installed, the transparent keyboard exposes that one unified
            // material. Until then, keep a keyboard-local glass background in place: changing the
            // RelativeLayout rules above only requests layout, so clearing this background here
            // would expose sharp wallpaper for a frame (or the whole IME transition).
            if (isUnifiedAccessoryBackdropReady(state)) {
                surfaceHost.setBackground(null);
                clearInAppKeyboardBackdrop();
            } else {
                Bitmap previousBackdrop = mInAppKeyboardBackdropBitmap;
                Drawable background = buildInAppKeyboardSurfaceBackground(
                    state, surfaceHost, false, glassTheme, 0f);
                surfaceHost.setBackground(background);
                recycleSupersededInAppKeyboardBackdrop(previousBackdrop, surfaceHost.getBackground());
            }
            return;
        }
        Bitmap previousBackdrop = mInAppKeyboardBackdropBitmap;
        Drawable background = buildInAppKeyboardSurfaceBackground(
            state, surfaceHost, capsule, glassTheme, cornerRadiusPx);
        surfaceHost.setBackground(background);
        recycleSupersededInAppKeyboardBackdrop(previousBackdrop, surfaceHost.getBackground());
    }

    /** Rounded clip for the capsule keyboard; rectangular bounds clip for the default style. */
    private void applyInAppKeyboardSurfaceClip(@NonNull View surfaceHost, boolean capsule,
                                               float cornerRadiusPx) {
        if (!capsule) {
            surfaceHost.setOutlineProvider(ViewOutlineProvider.BOUNDS);
            surfaceHost.setClipToOutline(true);
            return;
        }
        surfaceHost.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
            }
        });
        surfaceHost.setClipToOutline(true);
    }

    @Nullable
    private Drawable buildInAppKeyboardSurfaceBackground(@NonNull AccessoryRenderState state,
                                                         @NonNull View surfaceHost,
                                                         boolean capsule, boolean glassTheme,
                                                         float cornerRadiusPx) {
        java.util.List<Drawable> layers = new java.util.ArrayList<>();
        Integer schemeBackground = resolveInAppKeyboardSchemeBackgroundColor();
        int backgroundAlpha = Math.round(
            255f * getInAppKeyboardBackgroundOpacityPercent() / 100f);
        if (glassTheme) {
            if (state.blurEnabled) {
                Bitmap blurredBackdrop = obtainInAppKeyboardBackdropBitmap(state, surfaceHost);
                if (blurredBackdrop != null) {
                    BitmapDrawable backdrop = new BitmapDrawable(getResources(), blurredBackdrop);
                    // Same content-aware light scatter the dock backdrop uses — one material.
                    backdrop.setColorFilter(glassFrostFilter());
                    backdrop.setAlpha(255);
                    layers.add(backdrop);
                }
            }
            if (schemeBackground != null) {
                // The scheme's background color replaces the glass tint over the blurred
                // wallpaper; the opacity slider decides how much of the blur shows through.
                layers.add(new ColorDrawable(
                    withAlphaComponent(schemeBackground, backgroundAlpha)));
            } else {
                // Render only the keyboard's slice of the shared light model; the under-pill nav
                // strip renders the remainder so the single foot lands under the pill (see the
                // slice overload).
                Drawable tint = buildDockGlassSurface(
                    state.barAlpha, 0f, defaultDockGlassFootFraction(), false);
                if (backgroundAlpha < 255)
                    tint.setAlpha(backgroundAlpha);
                layers.add(tint);
            }
        } else if (capsule) {
            // Opaque themes fill the capsule with the keyboard's own background color so the
            // inner padding ring stays seamless with the keys.
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(withAlphaComponent(schemeBackground != null
                ? schemeBackground : resolveInAppKeyboardBackgroundColor(), backgroundAlpha));
            fill.setCornerRadius(cornerRadiusPx);
            layers.add(fill);
        }
        if (capsule) {
            GradientDrawable ring = new GradientDrawable();
            ring.setColor(Color.TRANSPARENT);
            ring.setCornerRadius(cornerRadiusPx);
            ring.setStroke(Math.max(1, Math.round(dpToPx(1))),
                withAlphaComponent(resolveAccessoryOutlineColor(), 18));
            layers.add(ring);
        }
        if (layers.isEmpty()) {
            return null;
        }
        Drawable material = layers.size() == 1
            ? layers.get(0) : new LayerDrawable(layers.toArray(new Drawable[0]));
        // A BitmapDrawable reports its captured bitmap dimensions as its minimum size. Since this
        // drawable is installed on a wrap-content host, exposing that intrinsic size feeds the old
        // backdrop height back into layout and creates a blank band below a subsequently shorter
        // keyboard layout. Decoration must follow content geometry, never define it.
        return new LayoutNeutralDrawable(material);
    }

    private void clearInAppKeyboardBackdrop() {
        Bitmap previousBackdrop = mInAppKeyboardBackdropBitmap;
        mInAppKeyboardBackdropBitmap = null;
        mInAppKeyboardBackdropDirty = true;
        mLastInAppKeyboardBackdropBlurRadiusDp = -1;
        mLastInAppKeyboardBackdropManagedSource = false;
        mLastInAppKeyboardBackdropTargetRect.setEmpty();
        recycleSupersededInAppKeyboardBackdrop(previousBackdrop, null);
    }

    private void recycleSupersededInAppKeyboardBackdrop(@Nullable Bitmap previousBackdrop,
                                                         @Nullable Drawable installedBackground) {
        if (previousBackdrop == null || previousBackdrop == mInAppKeyboardBackdropBitmap
            || mCachedAccessoryWallpaperBlurByRadius.containsValue(previousBackdrop)
            || previousBackdrop.isRecycled()
            || drawableReferencesBitmap(installedBackground, previousBackdrop)) {
            return;
        }
        previousBackdrop.recycle();
    }

    private boolean drawableReferencesBitmap(@Nullable Drawable drawable, @NonNull Bitmap bitmap) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap() == bitmap;
        }
        if (drawable instanceof LayoutNeutralDrawable) {
            return drawableReferencesBitmap(((LayoutNeutralDrawable) drawable).mSource, bitmap);
        }
        if (drawable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) drawable;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                if (drawableReferencesBitmap(layers.getDrawable(i), bitmap)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Blurred wallpaper crop behind the keyboard, cached until its geometry, blur radius, or
     * wallpaper source changes. Returns the last good bitmap while a recapture is unavailable.
     */
    @Nullable
    private Bitmap obtainInAppKeyboardBackdropBitmap(@NonNull AccessoryRenderState state,
                                                     @NonNull View surfaceHost) {
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        if (wallpaperFrame == null) {
            return mInAppKeyboardBackdropDirty ? null : mInAppKeyboardBackdropBitmap;
        }

        Rect targetRect = buildInAppKeyboardBackdropTargetRect(state, surfaceHost);
        if (targetRect == null) {
            return mInAppKeyboardBackdropDirty ? null : mInAppKeyboardBackdropBitmap;
        }
        boolean usingManagedWallpaperSource = shouldUseManagedWallpaperBlurSource();
        if (!mInAppKeyboardBackdropDirty &&
            mLastInAppKeyboardBackdropBlurRadiusDp == state.blurRadiusDp &&
            mLastInAppKeyboardBackdropManagedSource == usingManagedWallpaperSource &&
            mLastInAppKeyboardBackdropTargetRect.equals(targetRect) &&
            mInAppKeyboardBackdropBitmap != null) {
            return mInAppKeyboardBackdropBitmap;
        }

        Bitmap blurredBackdrop = createCachedAccessoryWallpaperBlurCrop(state.blurRadiusDp, targetRect, wallpaperFrame);
        if (blurredBackdrop == null) {
            // A previous-geometry crop is worse than tint-only glass: BitmapDrawable would scale it
            // into the new keyboard height and briefly sample the wrong wallpaper region.
            return mLastInAppKeyboardBackdropTargetRect.equals(targetRect)
                ? mInAppKeyboardBackdropBitmap : null;
        }
        mInAppKeyboardBackdropBitmap = blurredBackdrop;
        mInAppKeyboardBackdropDirty = false;
        mLastInAppKeyboardBackdropBlurRadiusDp = state.blurRadiusDp;
        mLastInAppKeyboardBackdropManagedSource = usingManagedWallpaperSource;
        mLastInAppKeyboardBackdropTargetRect.set(targetRect);
        return mInAppKeyboardBackdropBitmap;
    }

    /**
     * Returns the keyboard crop even during its first pre-layout render. The accessory stack is
     * bottom-anchored, so its already-laid-out bottom remains a stable reference while the new
     * keyboard height is waiting for layout.
     */
    @Nullable
    private Rect buildInAppKeyboardBackdropTargetRect(@NonNull AccessoryRenderState state,
                                                       @NonNull View surfaceHost) {
        if (surfaceHost.getWidth() > 0 && surfaceHost.getHeight() > 0) {
            surfaceHost.getLocationOnScreen(mTmpViewLocation);
            return new Rect(
                mTmpViewLocation[0],
                mTmpViewLocation[1],
                mTmpViewLocation[0] + surfaceHost.getWidth(),
                mTmpViewLocation[1] + surfaceHost.getHeight()
            );
        }

        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null || state.keyboardHeight <= 0) {
            return null;
        }
        accessoryContainer.getLocationOnScreen(mTmpViewLocation);
        int width = accessoryContainer.getWidth();
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        int bottom = mTmpViewLocation[1] + accessoryContainer.getHeight();
        if (bottom <= 0) {
            Rect frameRect = getManagedWallpaperFrameRect();
            bottom = frameRect.bottom;
        }
        return new Rect(
            mTmpViewLocation[0],
            Math.max(0, bottom - state.keyboardHeight),
            mTmpViewLocation[0] + Math.max(1, width),
            Math.max(1, bottom)
        );
    }

    /** True only after both expanded layout geometry and its matching unified crop are installed. */
    private boolean isUnifiedAccessoryBackdropReady(@NonNull AccessoryRenderState state) {
        if (!shouldUseUnifiedDefaultKeyboardGlassSurface(state)) {
            return false;
        }
        ImageView backdrop = findViewById(R.id.accessory_blur_backdrop);
        View surfaceHost = findViewById(R.id.accessory_surface_host);
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        View keyboardContainer = findViewById(R.id.inapp_keyboard_container);
        if (backdrop == null || surfaceHost == null || accessoryContainer == null
            || keyboardContainer == null || keyboardContainer.getVisibility() == View.GONE
            || backdrop.getDrawable() == null || backdrop.getVisibility() != View.VISIBLE) {
            return false;
        }
        ViewGroup.LayoutParams containerParams = accessoryContainer.getLayoutParams();
        int expectedHeight = containerParams != null && containerParams.height > 0
            ? containerParams.height : accessoryContainer.getHeight();
        if (expectedHeight <= 0 || surfaceHost.getHeight() < expectedHeight) {
            return false;
        }
        // Before expanded layout, both values above can still describe the old dock-only state and
        // therefore appear consistent. A unified dock+keyboard host must be taller than the
        // keyboard portion by itself.
        if (surfaceHost.getHeight() <= state.keyboardHeight) {
            return false;
        }
        int horizontalOverscanPx = computeAccessoryBackdropHorizontalOverscanPx(state.blurRadiusDp);
        int seamOverscanPx = !isRoundedDockStyle() && shouldShowDecorNavBarSurface(state)
            ? horizontalOverscanPx : 0;
        Rect currentTarget = buildAccessoryBackdropTargetRect(
            surfaceHost, horizontalOverscanPx, seamOverscanPx);
        // setLayoutParams() during pre-draw does not resize the ImageView until another layout.
        // Treat that pass as unready so it is skipped instead of drawing the expanded surface with
        // a dock-height blur child for one frame.
        if (backdrop.getWidth() < currentTarget.width()
            || backdrop.getHeight() < currentTarget.height()) {
            return false;
        }
        return mLastAccessoryBackdropBlurRadiusDp == state.blurRadiusDp
            && mLastAccessoryBackdropManagedSource == shouldUseManagedWallpaperBlurSource()
            && mLastAccessoryBackdropTargetRect.equals(currentTarget);
    }

    private void clearDecorNavBarBackdrop() {
        ImageView backdrop = mDecorNavBarBlurBackdrop;
        if (backdrop != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                backdrop.setRenderEffect(null);
            }
            backdrop.setImageDrawable(null);
            backdrop.setVisibility(View.GONE);
        }
        mDecorNavBarBackdropDirty = true;
        mLastDecorNavBarBackdropBlurRadiusDp = -1;
        mLastDecorNavBarBackdropManagedSource = false;
        mLastDecorNavBarBackdropTargetRect.setEmpty();
    }

    private void updateDecorNavBarBackdrop(@NonNull AccessoryRenderState state) {
        ImageView backdrop = mDecorNavBarBlurBackdrop;
        FrameLayout overlay = mDecorNavBarSurfaceOverlay;
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        if (backdrop == null || overlay == null || wallpaperFrame == null) {
            return;
        }
        backdrop.setAlpha(1f);
        // Overscan the strip's blur bitmap upward past the seam with the dock/keyboard (default dock
        // only) so the refraction edge band clears the visible seam and meets the dock's downward
        // overscan. The bitmap is taller than the overlay; a negative top margin pushes the overscan
        // above the overlay where clipChildren hides it.
        int topOverscanPx = !isRoundedDockStyle()
            ? computeAccessoryBackdropHorizontalOverscanPx(state.blurRadiusDp) : 0;
        Rect targetRect = buildDecorNavBarBackdropTargetRect(topOverscanPx);
        if (targetRect == null) {
            if (backdrop.getDrawable() != null) {
                backdrop.setVisibility(View.VISIBLE);
            } else {
                clearDecorNavBarBackdrop();
            }
            return;
        }
        if (backdrop.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams bp = (FrameLayout.LayoutParams) backdrop.getLayoutParams();
            int surfaceHeight = Math.max(1, resolveDecorNavBarSurfaceHeightPx());
            int targetBackdropHeight = surfaceHeight + Math.max(0, topOverscanPx);
            if (bp.height != targetBackdropHeight || bp.topMargin != -topOverscanPx) {
                bp.height = targetBackdropHeight;
                bp.width = FrameLayout.LayoutParams.MATCH_PARENT;
                bp.topMargin = -topOverscanPx;
                bp.gravity = Gravity.TOP | Gravity.START;
                backdrop.setLayoutParams(bp);
            }
        }

        boolean usingManagedWallpaperSource = shouldUseManagedWallpaperBlurSource();
        if (!mDecorNavBarBackdropDirty &&
            mLastDecorNavBarBackdropBlurRadiusDp == state.blurRadiusDp &&
            mLastDecorNavBarBackdropManagedSource == usingManagedWallpaperSource &&
            mLastDecorNavBarBackdropTargetRect.equals(targetRect) &&
            backdrop.getDrawable() != null) {
            backdrop.setVisibility(View.VISIBLE);
            return;
        }

        Bitmap wallpaperBackdrop = createCachedAccessoryWallpaperBlurCrop(state.blurRadiusDp, targetRect, wallpaperFrame);
        if (wallpaperBackdrop == null) {
            if (backdrop.getDrawable() != null) {
                backdrop.setVisibility(View.VISIBLE);
            } else {
                clearDecorNavBarBackdrop();
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdrop.setImageBitmap(wallpaperBackdrop);
            // Use the SAME AGSL glass refraction the content dock/keyboard backdrop uses (API 33+),
            // not a plain blur, so this decor surface reads as the same material in both states: the
            // keyboard-off dock+nav overlay and the keyboard-on under-pill strip now match the
            // refraction-lit content dock instead of being a flatter, darker plain blur. Falls back
            // to plain blur when the shader is unavailable (< API 33 or shader failure).
            RenderEffect glass = buildGlassRefractionEffect(0f, 0f, 0f,
                Math.max(1, targetRect.width()), Math.max(1, targetRect.height()), 0f);
            backdrop.setRenderEffect(glass);
        } else {
            backdrop.setImageBitmap(wallpaperBackdrop);
        }

        // Same content-aware light scatter the dock and keyboard backdrops apply, so the
        // under-pill strip reads as the same glass material rather than a plain blur.
        backdrop.setColorFilter(glassFrostFilter());
        backdrop.setVisibility(View.VISIBLE);
        mDecorNavBarBackdropDirty = false;
        mLastDecorNavBarBackdropBlurRadiusDp = state.blurRadiusDp;
        mLastDecorNavBarBackdropManagedSource = usingManagedWallpaperSource;
        mLastDecorNavBarBackdropTargetRect.set(targetRect);
    }

    private void restartAccessoryBlurHeartbeat() {
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurHeartbeatRunnable);
        AccessoryRenderState state = buildAccessoryRenderState();
        if (mIsVisible && state.toolbarShown && state.blurEnabled) {
            mAccessoryRenderHandler.postDelayed(mAccessoryBlurHeartbeatRunnable, ACCESSORY_BLUR_BACKSTOP_MS);
        }
    }

    private void scheduleAccessoryBlurRecovery() {
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurRecoveryRunnable);
        AccessoryRenderState state = buildAccessoryRenderState();
        if (mIsVisible && state.toolbarShown && state.blurEnabled) {
            mAccessoryRenderHandler.postDelayed(mAccessoryBlurRecoveryRunnable, ACCESSORY_BLUR_RECOVERY_RETRY_MS);
        }
    }

    private boolean isAccessoryBlurHealthy(@NonNull AccessoryRenderState state) {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null || accessoryContainer.getVisibility() != View.VISIBLE) {
            return !state.toolbarShown;
        }
        boolean useRenderEffectBlur = shouldUseAccessoryRenderEffectBlur(state);
        if (useRenderEffectBlur) {
            ImageView backdrop = findViewById(R.id.accessory_blur_backdrop);
            boolean accessoryHealthy = backdrop != null
                && backdrop.getVisibility() == View.VISIBLE
                && backdrop.getDrawable() != null;
            if (shouldUseDockDecorNavBarSurface(state)) {
                boolean decorHealthy = mDecorNavBarBlurBackdrop != null
                    && mDecorNavBarBlurBackdrop.getVisibility() == View.VISIBLE
                    && mDecorNavBarBlurBackdrop.getDrawable() != null;
                return isRoundedDockStyle() ? accessoryHealthy && decorHealthy : decorHealthy;
            }
            return accessoryHealthy;
        }
        View realtimeBlur = findViewById(R.id.extrakeys_backgroundblur);
        return realtimeBlur != null
            && realtimeBlur.getVisibility() == View.VISIBLE
            && realtimeBlur instanceof RealtimeBlurView;
    }

    private boolean shouldUseManagedWallpaperBlurSource() {
        if (mPreferences == null) {
            return false;
        }
        int storedWallpaperId = mPreferences.getManagedWallpaperSystemId();
        if (storedWallpaperId <= 0 || storedWallpaperId != getCurrentSystemWallpaperId()) {
            return false;
        }
        return getManagedWallpaperExactFile().isFile();
    }

    private int computeAccessoryBackdropHorizontalOverscanPx(int blurRadiusDp) {
        float blurRadiusPx = ViewUtils.dpToPx(this, Math.max(0, blurRadiusDp));
        float density = getResources().getDisplayMetrics().density;
        return Math.max(0, Math.round((blurRadiusPx * 2f) + (density * 2f)));
    }

    private void applyAccessoryBackdropOverscan(@NonNull ImageView backdrop, @NonNull View surfaceHost,
                                                int horizontalOverscanPx, int bottomOverscanPx) {
        ViewGroup.LayoutParams layoutParams = backdrop.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layoutParams;
        int targetWidth = Math.max(1, surfaceHost.getWidth() + (horizontalOverscanPx * 2));
        // Grow the bitmap DOWN past the surface (clipped by the host) so the refraction's bottom edge
        // band falls below the visible seam with the under-pill strip, not on it.
        int targetHeight = Math.max(1, surfaceHost.getHeight() + bottomOverscanPx);
        int targetLeftMargin = -horizontalOverscanPx;
        int targetTopMargin = 0;
        if (params.width != targetWidth || params.height != targetHeight ||
            params.leftMargin != targetLeftMargin || params.topMargin != targetTopMargin) {
            params.width = targetWidth;
            params.height = targetHeight;
            params.leftMargin = targetLeftMargin;
            params.topMargin = targetTopMargin;
            params.rightMargin = 0;
            params.bottomMargin = 0;
            params.gravity = Gravity.TOP | Gravity.START;
            backdrop.setLayoutParams(params);
        }
    }

    @NonNull
    private Rect buildAccessoryBackdropTargetRect(@NonNull View surfaceHost, int horizontalOverscanPx,
                                                  int bottomOverscanPx) {
        surfaceHost.getLocationOnScreen(mTmpViewLocation);
        return new Rect(
            mTmpViewLocation[0] - horizontalOverscanPx,
            mTmpViewLocation[1],
            mTmpViewLocation[0] + Math.max(1, surfaceHost.getWidth()) + horizontalOverscanPx,
            mTmpViewLocation[1] + Math.max(1, surfaceHost.getHeight()) + bottomOverscanPx
        );
    }

    @Nullable
    private Bitmap createManagedWallpaperBackdropBitmapForRect(@NonNull Rect targetRect, @NonNull View wallpaperFrame) {
        File sourceFile = getManagedWallpaperExactFile();
        Bitmap sourceBitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
        if (sourceBitmap == null) {
            return null;
        }

        try {
            Rect frameRect = getManagedWallpaperFrameRect();
            int targetWidth = Math.max(1, targetRect.width());
            int targetHeight = Math.max(1, targetRect.height());

            Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            int frameWidth = Math.max(1, frameRect.width());
            int frameHeight = Math.max(1, frameRect.height());
            int sourceWidth = Math.max(1, sourceBitmap.getWidth());
            int sourceHeight = Math.max(1, sourceBitmap.getHeight());
            float scale = Math.max((float) frameWidth / sourceWidth, (float) frameHeight / sourceHeight);
            float drawWidth = sourceWidth * scale;
            float drawHeight = sourceHeight * scale;
            float translateX = frameRect.left + ((frameWidth - drawWidth) / 2f) - targetRect.left;
            float translateY = frameRect.top + ((frameHeight - drawHeight) / 2f) - targetRect.top;

            Matrix shaderMatrix = new Matrix();
            shaderMatrix.setScale(scale, scale);
            shaderMatrix.postTranslate(translateX, translateY);
            float zoom = systemWallpaperRenderZoom();
            if (zoom != 1f) {
                // Same render-zoom compensation as the system-drawable path, in this bitmap's
                // local coordinates (the target rect's origin is already subtracted above).
                shaderMatrix.postScale(zoom, zoom,
                    frameRect.exactCenterX() - targetRect.left,
                    frameRect.exactCenterY() - targetRect.top);
            }

            BitmapShader shader = new BitmapShader(sourceBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shader.setLocalMatrix(shaderMatrix);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            paint.setShader(shader);
            canvas.drawRect(0f, 0f, targetWidth, targetHeight, paint);
            return bitmap;
        } finally {
            sourceBitmap.recycle();
        }
    }

    @Nullable
    /**
     * Extra magnification the system applies when it renders the static wallpaper, about the
     * display center. Measured with a coordinate-grid wallpaper: Nothing OS draws at a fixed
     * 1.10x regardless of the zoom-out the launcher requests, which displaced every glass crop
     * by ~10% of its distance from the center (a few dp at the dock, most under the keyboard).
     * ROMs that honor {@code setWallpaperZoomOut(0)} render at 1.0 and need no compensation.
     */
    private float systemWallpaperRenderZoom() {
        return "nothing".equalsIgnoreCase(Build.MANUFACTURER) ? 1.10f : 1f;
    }

    /**
     * The glass bands blur a crop of the system wallpaper read through {@link WallpaperManager}.
     * Where the platform still gates that read behind the legacy storage permission, an install
     * that never ran {@code termux-setup-storage} and never set its wallpaper through the in-app
     * picker (which keeps a managed copy and needs no permission) gets a {@link SecurityException}
     * instead, and every band renders flat with nothing but a log line to say why. Ask at the
     * point the read actually fails rather than up front, so devices that can read the wallpaper
     * are never prompted at all.
     */
    private void onSystemWallpaperReadDenied() {
        if (mWallpaperReadPermissionDenied) {
            return;
        }
        mWallpaperReadPermissionDenied = true;
        // Called from inside a render pass; do not put up a dialog mid-draw.
        View root = findViewById(R.id.activity_termux_root_view);
        if (root != null) {
            root.post(this::maybeRequestWallpaperReadPermission);
        }
    }

    /**
     * The prompt is worth showing only when the wallpaper read has already failed, the bands are
     * actually sourcing the wallpaper, the permission is the thing standing in the way, and the
     * user has not been asked before.
     */
    static boolean shouldPromptForWallpaperRead(boolean readDenied, boolean wallpaperPassthrough,
                                                boolean permissionGranted, boolean alreadyPrompted) {
        return readDenied && wallpaperPassthrough && !permissionGranted && !alreadyPrompted;
    }

    private void maybeRequestWallpaperReadPermission() {
        if (!mIsVisible || mPreferences == null || isFinishing() || isDestroyed()) {
            return;
        }
        boolean permissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (permissionGranted) {
            // Already granted and the read still failed: the refusal is not about this permission,
            // so a prompt would only nag.
            mWallpaperReadPermissionDenied = false;
            return;
        }
        if (mWallpaperReadPermissionPromptShowing
            || !shouldPromptForWallpaperRead(mWallpaperReadPermissionDenied,
                shouldUseWallpaperPassthroughMode(), false,
                mPreferences.isWallpaperReadPermissionPrompted())) {
            return;
        }
        mWallpaperReadPermissionPromptShowing = true;
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_wallpaper_read_permission)
            .setMessage(R.string.msg_wallpaper_read_permission)
            .setPositiveButton(R.string.action_wallpaper_read_permission_allow, (dialog, which) -> {
                mPreferences.setWallpaperReadPermissionPrompted(true);
                androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_WALLPAPER_READ_PERMISSION);
            })
            .setNegativeButton(R.string.action_wallpaper_read_permission_dismiss, (dialog, which) ->
                mPreferences.setWallpaperReadPermissionPrompted(true))
            .setOnDismissListener(dialog -> mWallpaperReadPermissionPromptShowing = false)
            .show();
    }

    @Nullable
    private Bitmap createWallpaperBackdropBitmapForRect(@NonNull Rect targetRect, @NonNull View wallpaperFrame) {
        if (shouldUseManagedWallpaperBlurSource()) {
            Bitmap managedBackdrop = createManagedWallpaperBackdropBitmapForRect(targetRect, wallpaperFrame);
            if (managedBackdrop != null) {
                return managedBackdrop;
            }
        }

        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        Drawable wallpaper;
        try {
            wallpaper = wallpaperManager.getDrawable();
        } catch (SecurityException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Cannot read system wallpaper for accessory backdrop", e);
            onSystemWallpaperReadDenied();
            return null;
        }
        mWallpaperReadPermissionDenied = false;
        if (wallpaper == null) {
            return null;
        }

        int targetWidth = Math.max(1, targetRect.width());
        int targetHeight = Math.max(1, targetRect.height());
        Rect frameRect = getManagedWallpaperFrameRect();
        int frameWidth = Math.max(1, frameRect.width());
        int frameHeight = Math.max(1, frameRect.height());

        int intrinsicWidth = wallpaper.getIntrinsicWidth() > 0 ? wallpaper.getIntrinsicWidth() : frameWidth;
        int intrinsicHeight = wallpaper.getIntrinsicHeight() > 0 ? wallpaper.getIntrinsicHeight() : frameHeight;
        float scale = Math.max((float) frameWidth / intrinsicWidth, (float) frameHeight / intrinsicHeight);
        int drawWidth = Math.max(frameWidth, Math.round(intrinsicWidth * scale));
        int drawHeight = Math.max(frameHeight, Math.round(intrinsicHeight * scale));

        int frameScreenX = frameRect.left;
        int frameScreenY = frameRect.top;
        int offsetLeft = frameScreenX + Math.round((frameWidth - drawWidth) / 2f);
        int offsetTop = frameScreenY + Math.round((frameHeight - drawHeight) / 2f);

        float zoom = systemWallpaperRenderZoom();
        if (zoom != 1f) {
            float centerX = frameRect.exactCenterX();
            float centerY = frameRect.exactCenterY();
            offsetLeft = Math.round(zoom * (offsetLeft - centerX) + centerX);
            offsetTop = Math.round(zoom * (offsetTop - centerY) + centerY);
            drawWidth = Math.round(drawWidth * zoom);
            drawHeight = Math.round(drawHeight * zoom);
        }

        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable drawable = wallpaper.getConstantState() != null
            ? wallpaper.getConstantState().newDrawable().mutate()
            : wallpaper.mutate();
        drawable.setBounds(
            offsetLeft - targetRect.left,
            offsetTop - targetRect.top,
            offsetLeft - targetRect.left + drawWidth,
            offsetTop - targetRect.top + drawHeight
        );
        drawable.draw(canvas);
        return bitmap;
    }

    @Nullable
    private Bitmap createPreBlurredWallpaperBackdropBitmap(@NonNull Bitmap sourceBitmap, int blurRadiusDp) {
        float blurRadiusPx = ViewUtils.dpToPx(this, Math.max(0, blurRadiusDp));
        if (blurRadiusPx <= 0f) {
            return sourceBitmap;
        }

        // Low radii must keep the source crisp: a fixed 4x down/up resample softened the frame far
        // beyond the requested blur and shifted content by a few pixels, so at 1-5dp the glass read
        // as showing a different wallpaper than the one right next to it. Use the smallest factor
        // that keeps the script radius inside RenderScript's 25px cap instead.
        float downsampleFactor = Math.max(1f, Math.min(ACCESSORY_BLUR_DOWNSAMPLE_FACTOR,
            (float) Math.ceil(blurRadiusPx / 25f)));
        float scriptRadius = blurRadiusPx / downsampleFactor;
        if (scriptRadius > 25f) {
            downsampleFactor = (float) Math.ceil(blurRadiusPx / 25f);
            scriptRadius = blurRadiusPx / downsampleFactor;
        }
        scriptRadius = Math.max(0.1f, Math.min(25f, scriptRadius));

        int scaledWidth = Math.max(1, Math.round(sourceBitmap.getWidth() / downsampleFactor));
        int scaledHeight = Math.max(1, Math.round(sourceBitmap.getHeight() / downsampleFactor));
        Bitmap blurInput = null;
        Bitmap blurOutput = null;
        AndroidStockBlurImpl blurImpl = new AndroidStockBlurImpl();
        try {
            blurInput = Bitmap.createScaledBitmap(sourceBitmap, scaledWidth, scaledHeight, true);
            blurOutput = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
            if (!blurImpl.prepare(this, blurInput, scriptRadius)) {
                return null;
            }
            blurImpl.blur(blurInput, blurOutput);
            return Bitmap.createScaledBitmap(blurOutput, sourceBitmap.getWidth(), sourceBitmap.getHeight(), true);
        } catch (Throwable e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to create cached accessory wallpaper blur", e);
            return null;
        } finally {
            blurImpl.release();
            if (blurInput != null && blurInput != sourceBitmap) {
                blurInput.recycle();
            }
            if (blurOutput != null) {
                blurOutput.recycle();
            }
        }
    }

    /**
     * Returns the one pre-blurred wallpaper frame used by every accessory glass surface. Geometry
     * changes only crop this bitmap; they never capture and blur a second, visually different copy.
     */
    @Nullable
    private Bitmap obtainCachedAccessoryWallpaperBlur(int blurRadiusDp,
                                                       @NonNull View wallpaperFrame) {
        Rect frameRect = getManagedWallpaperFrameRect();
        boolean managedSource = shouldUseManagedWallpaperBlurSource();
        int systemWallpaperId = getCurrentSystemWallpaperId();
        File managedFile = managedSource ? getManagedWallpaperExactFile() : null;
        long managedLastModified = managedFile != null ? managedFile.lastModified() : -1L;
        long managedLength = managedFile != null ? managedFile.length() : -1L;
        boolean sourceValid = mCachedAccessoryWallpaperBlurManagedSource == managedSource
            && mCachedAccessoryWallpaperBlurSystemId == systemWallpaperId
            && mCachedAccessoryWallpaperBlurManagedLastModified == managedLastModified
            && mCachedAccessoryWallpaperBlurManagedLength == managedLength
            && mCachedAccessoryWallpaperBlurOrientation
                == getResources().getConfiguration().orientation
            && mCachedAccessoryWallpaperBlurFrameRect.equals(frameRect);
        if (sourceValid) {
            Bitmap cached = mCachedAccessoryWallpaperBlurByRadius.get(blurRadiusDp);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        } else {
            // The wallpaper itself changed; every per-radius frame is stale.
            clearCachedAccessoryWallpaperBlur();
        }

        Bitmap wallpaperBitmap = createWallpaperBackdropBitmapForRect(frameRect, wallpaperFrame);
        if (wallpaperBitmap == null) {
            return null;
        }
        Bitmap blurredBitmap = createPreBlurredWallpaperBackdropBitmap(wallpaperBitmap, blurRadiusDp);
        if (blurredBitmap == null) {
            wallpaperBitmap.recycle();
            return null;
        }
        if (blurredBitmap != wallpaperBitmap) {
            wallpaperBitmap.recycle();
        }
        mCachedAccessoryWallpaperBlurByRadius.put(blurRadiusDp, blurredBitmap);
        while (mCachedAccessoryWallpaperBlurByRadius.size() > MAX_CACHED_WALLPAPER_BLUR_RADII) {
            java.util.Iterator<Bitmap> eldest =
                mCachedAccessoryWallpaperBlurByRadius.values().iterator();
            Bitmap evicted = eldest.next();
            eldest.remove();
            if (evicted != null && !evicted.isRecycled()
                && evicted != mInAppKeyboardBackdropBitmap
                && !isSharedWallpaperBlurFrameInUse(evicted)) {
                evicted.recycle();
            }
        }
        mCachedAccessoryWallpaperBlurFrameRect.set(frameRect);
        mCachedAccessoryWallpaperBlurOrientation = getResources().getConfiguration().orientation;
        mCachedAccessoryWallpaperBlurManagedSource = managedSource;
        mCachedAccessoryWallpaperBlurSystemId = systemWallpaperId;
        mCachedAccessoryWallpaperBlurManagedLastModified = managedLastModified;
        mCachedAccessoryWallpaperBlurManagedLength = managedLength;
        return blurredBitmap;
    }

    /**
     * Crops the shared full-frame blur in screen coordinates, clamping any overscan at its edges.
     *
     * <p>A full-screen surface (the command palette glass, the app drawer plane) asks for exactly
     * the cached frame's rect, and copying it would allocate a second full-screen ARGB_8888 bitmap
     * — ~10MB on a 1080x2400 panel, on the first frame of the open gesture. That request is
     * answered with the cached frame itself; the returned bitmap is then shared, so
     * {@link #clearCachedAccessoryWallpaperBlur()} detaches it from the glass frosts before
     * recycling.</p>
     */
    @Nullable
    private Bitmap createCachedAccessoryWallpaperBlurCrop(int blurRadiusDp,
                                                           @NonNull Rect targetRect,
                                                           @NonNull View wallpaperFrame) {
        Bitmap fullBlur = obtainCachedAccessoryWallpaperBlur(blurRadiusDp, wallpaperFrame);
        if (fullBlur == null) {
            return null;
        }
        if (targetRect.equals(mCachedAccessoryWallpaperBlurFrameRect)) {
            return fullBlur;
        }
        int width = Math.max(1, targetRect.width());
        int height = Math.max(1, targetRect.height());
        Bitmap crop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(crop);
        BitmapShader shader = new BitmapShader(fullBlur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix matrix = new Matrix();
        matrix.setTranslate(
            mCachedAccessoryWallpaperBlurFrameRect.left - targetRect.left,
            mCachedAccessoryWallpaperBlurFrameRect.top - targetRect.top);
        shader.setLocalMatrix(matrix);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        canvas.drawRect(0f, 0f, width, height, paint);
        return crop;
    }

    /**
     * True while a full-screen glass frost is displaying this exact frame through the identity fast
     * path in {@link #createCachedAccessoryWallpaperBlurCrop}. Recycling a bitmap an ImageView still
     * holds crashes on its next draw, so such a frame is dropped from the cache without recycling
     * and left to the collector.
     */
    private boolean isSharedWallpaperBlurFrameInUse(@Nullable Bitmap frame) {
        if (frame == null) {
            return false;
        }
        int[] frostIds = {R.id.command_palette_wallpaper_backdrop,
            R.id.app_drawer_wallpaper_backdrop, R.id.terminal_window_bar_wallpaper_backdrop};
        for (int frostId : frostIds) {
            ImageView frost = findViewById(frostId);
            Drawable drawable = frost != null ? frost.getDrawable() : null;
            if (drawable instanceof BitmapDrawable
                && ((BitmapDrawable) drawable).getBitmap() == frame) {
                return true;
            }
        }
        return false;
    }

    private void clearCachedAccessoryWallpaperBlur() {
        for (Bitmap cached : mCachedAccessoryWallpaperBlurByRadius.values()) {
            if (cached != null && !cached.isRecycled()
                && cached != mInAppKeyboardBackdropBitmap
                && !isSharedWallpaperBlurFrameInUse(cached)) {
                cached.recycle();
            }
        }
        mCachedAccessoryWallpaperBlurByRadius.clear();
        mCachedAccessoryWallpaperBlurFrameRect.setEmpty();
        mCachedAccessoryWallpaperBlurOrientation = Configuration.ORIENTATION_UNDEFINED;
        mCachedAccessoryWallpaperBlurManagedSource = false;
        mCachedAccessoryWallpaperBlurSystemId = -1;
        mCachedAccessoryWallpaperBlurManagedLastModified = -1L;
        mCachedAccessoryWallpaperBlurManagedLength = -1L;
        mTopPaneFrostDirty = true;
    }

    private void updateAccessoryRenderEffectBackdrop(@NonNull AccessoryRenderState state) {
        ImageView backdrop = findViewById(R.id.accessory_blur_backdrop);
        View surfaceHost = findViewById(R.id.accessory_surface_host);
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        boolean usingManagedWallpaperSource = shouldUseManagedWallpaperBlurSource();
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        if (backdrop != null) {
            backdrop.setAlpha(1f);
        }
        applyAccessorySurfaceBounds(state);
        if (shouldUseDockDecorNavBarSurface(state) && !isRoundedDockStyle()) {
            clearAccessoryRenderEffectBackdrop();
            return;
        }
        if (backdrop == null || surfaceHost == null || accessoryContainer == null || wallpaperFrame == null ||
            accessoryContainer.getWidth() <= 0 || accessoryContainer.getHeight() <= 0) {
            if (backdrop != null && shouldUseAccessoryRenderEffectBlur(state)
                && isAccessoryBackdropCropHeightCompatible(backdrop, backdrop.getHeight())) {
                backdrop.setVisibility(View.VISIBLE);
            } else {
                clearAccessoryRenderEffectBackdrop();
            }
            return;
        }
        if (!shouldUseAccessoryRenderEffectBlur(state)) {
            clearAccessoryRenderEffectBackdrop();
            return;
        }
        int horizontalOverscanPx = computeAccessoryBackdropHorizontalOverscanPx(state.blurRadiusDp);
        // When the under-pill decor strip abuts the dock/keyboard bottom (default dock only), overscan
        // the bitmap downward past that seam so the refraction edge band lands below it — the strip
        // overscans upward by the same amount, so the two surfaces' blurred wallpaper meets seamlessly.
        int seamOverscanPx = !isRoundedDockStyle() && shouldShowDecorNavBarSurface(state)
            ? horizontalOverscanPx : 0;
        applyAccessoryBackdropOverscan(backdrop, surfaceHost, horizontalOverscanPx, seamOverscanPx);
        Rect backdropTargetRect = buildAccessoryBackdropTargetRect(surfaceHost, horizontalOverscanPx,
            seamOverscanPx);
        if (!mAccessoryBackdropDirty &&
            mLastAccessoryBackdropBlurRadiusDp == state.blurRadiusDp &&
            mLastAccessoryBackdropManagedSource == usingManagedWallpaperSource &&
            mLastAccessoryBackdropTargetRect.equals(backdropTargetRect) &&
            isAccessoryBackdropCropHeightCompatible(backdrop, backdropTargetRect.height())) {
            backdrop.setVisibility(View.VISIBLE);
            return;
        }
        Bitmap wallpaperBackdrop = createCachedAccessoryWallpaperBlurCrop(
            state.blurRadiusDp, backdropTargetRect, wallpaperFrame);
        if (wallpaperBackdrop == null) {
            if (isAccessoryBackdropCropHeightCompatible(backdrop, backdropTargetRect.height())) {
                backdrop.setVisibility(View.VISIBLE);
            } else {
                // Never scale the keyboard-era crop into dock-only bounds on close. A subsequent
                // recovery pass can restore blur; this frame keeps the tint layer without stale
                // wallpaper brightness.
                backdrop.setImageDrawable(null);
                backdrop.setVisibility(View.GONE);
                mAccessoryBackdropDirty = true;
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backdrop.setImageBitmap(wallpaperBackdrop);
            // The capsule sits inside the (horizontally overscanned) backdrop bitmap: left/right are
            // inset by the overscan, top/bottom span the full height. Hand that rect to the shader so
            // refraction happens at the real dock edge.
            float capLeft = horizontalOverscanPx;
            float capRight = horizontalOverscanPx + Math.max(1, surfaceHost.getWidth());
            // Put the shader's bottom edge at the overscanned bitmap bottom (below the visible seam)
            // so the visible dock bottom reads as interior glass, not a refracting edge.
            float capBottom = Math.max(1, surfaceHost.getHeight()) + seamOverscanPx;
            float radiusPx = isRoundedDockStyle()
                ? resolveDockCapsuleCornerRadiusPx(surfaceHost.getHeight())
                : 0f;
            RenderEffect glass = buildGlassRefractionEffect(0f, capLeft, 0f, capRight, capBottom, radiusPx);
            // Remember the dock params so a key-press lens can rebuild this effect cheaply (no recapture).
            mGlassBlurPx = 0f;
            mGlassCapLeft = capLeft;
            mGlassCapTop = 0f;
            mGlassCapRight = capRight;
            mGlassCapBottom = capBottom;
            mGlassRadiusPx = radiusPx;
            mGlassParamsValid = (glass != null);
            backdrop.setRenderEffect(glass);
        } else {
            backdrop.setImageBitmap(wallpaperBackdrop);
        }
        // Content-aware light scatter — the frost that makes the blur read as glass, not plastic.
        backdrop.setColorFilter(glassFrostFilter());
        backdrop.setVisibility(View.VISIBLE);
        mAccessoryBackdropDirty = false;
        mLastAccessoryBackdropBlurRadiusDp = state.blurRadiusDp;
        mLastAccessoryBackdropManagedSource = usingManagedWallpaperSource;
        mLastAccessoryBackdropTargetRect.set(backdropTargetRect);
        // The keyboard-local cover is deliberately retained until this expanded crop is ready.
        // Remove it now so dock and keyboard switch atomically to the unified material.
        if (isUnifiedAccessoryBackdropReady(state)) {
            View keyboardSurfaceHost = findViewById(R.id.inapp_keyboard_view_host);
            if (keyboardSurfaceHost != null) {
                keyboardSurfaceHost.setBackground(null);
            }
            clearInAppKeyboardBackdrop();
        }
    }

    private boolean isAccessoryBackdropCropHeightCompatible(@NonNull ImageView backdrop,
                                                             int destinationHeight) {
        Drawable drawable = backdrop.getDrawable();
        if (!(drawable instanceof BitmapDrawable) || destinationHeight <= 0)
            return false;
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        return bitmap != null && !bitmap.isRecycled()
            && bitmap.getHeight() == destinationHeight
            && backdrop.getHeight() == destinationHeight;
    }

    @Nullable
    private Rect buildToolbarOnlyAccessoryBounds(@NonNull AccessoryRenderState state) {
        if (!state.toolbarShown)
            return null;
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null)
            return null;
        ViewGroup.LayoutParams params = accessoryContainer.getLayoutParams();
        int totalHeight = params != null && params.height > 0
            ? params.height : accessoryContainer.getHeight();
        int toolbarHeight = Math.max(0, totalHeight - state.keyboardHeight);
        if (toolbarHeight <= 0)
            return null;
        int width = accessoryContainer.getWidth();
        if (width <= 0)
            width = getResources().getDisplayMetrics().widthPixels;
        return new Rect(0, 0, Math.max(0, width), toolbarHeight);
    }

    private void applyAccessoryRenderState(@NonNull AccessoryRenderState state) {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        View accessorySurfaceHost = findViewById(R.id.accessory_surface_host);
        View terminalToolbarViewPager = findViewById(R.id.terminal_toolbar_view_pager);
        View appsBarViewPager = findViewById(R.id.apps_bar_viewpager);
        View indicatorBand = findViewById(R.id.apps_bar_indicator_band);
        View extraKeysBackground = findViewById(R.id.extrakeys_background);
        View extraKeysBackgroundBlur = findViewById(R.id.extrakeys_backgroundblur);
        View azRow = findViewById(R.id.apps_bar_az_row);
        View azFxUnderlay = findViewById(R.id.apps_bar_az_fx_underlay);
        View azFxOverlay = findViewById(R.id.apps_bar_az_fx_overlay);
        View azLabelOverlay = findViewById(R.id.apps_bar_az_label_overlay);
        Rect toolbarOnlyBounds = state.keyboardShown ? buildToolbarOnlyAccessoryBounds(state) : null;
        applyAccessorySurfaceBounds(state);
        applyAccessoryLayerVerticalBounds(R.id.apps_bar_az_fx_underlay, toolbarOnlyBounds);
        applyAccessoryLayerVerticalBounds(R.id.apps_bar_az_fx_overlay, toolbarOnlyBounds);
        boolean useRenderEffectBlur = shouldUseAccessoryRenderEffectBlur(state);
        boolean useDecorSurface = shouldUseDockDecorNavBarSurface(state);
        applyAccessoryAmbientVeil(accessoryContainer, state);

        if (extraKeysBackgroundBlur != null && !useRenderEffectBlur && !useDecorSurface) {
            applyRealtimeBlurRadius(extraKeysBackgroundBlur, state.blurRadiusDp);
            applyRealtimeBlurDownsampleFactor(extraKeysBackgroundBlur, ACCESSORY_BLUR_DOWNSAMPLE_FACTOR);
            applyRealtimeBlurOverlayColor(
                extraKeysBackgroundBlur,
                state.blurEnabled ? resolveAccessorySurfaceColor(state.barAlpha) : Color.TRANSPARENT
            );
        }
        // Invalidate platform/local drag state while its source view is still attached and visible.
        // Alphabet and extra-key row state is deliberately not part of this cancellation decision.
        if (!state.appsRowEnabled && mSuggestionBarView != null)
            mSuggestionBarView.cancelActiveDockDrag();
        if (!state.toolbarShown) {
            if (accessoryContainer != null) {
                accessoryContainer.setVisibility(
                    shouldShowAccessoryStack(false, state.keyboardShown) ? View.VISIBLE : View.GONE);
            }
            if (extraKeysBackgroundBlur != null) {
                extraKeysBackgroundBlur.setVisibility(View.GONE);
            }
            if (extraKeysBackground != null) {
                extraKeysBackground.setVisibility(View.GONE);
            }
            if (accessorySurfaceHost != null) {
                accessorySurfaceHost.setVisibility(View.GONE);
            }
            if (appsBarViewPager != null) {
                appsBarViewPager.setVisibility(View.GONE);
            }
            if (indicatorBand != null) {
                indicatorBand.setVisibility(View.GONE);
            }
            if (terminalToolbarViewPager != null) {
                terminalToolbarViewPager.setVisibility(View.GONE);
            }
            if (azRow != null) {
                azRow.setVisibility(View.GONE);
            }
            if (azFxOverlay != null) {
                azFxOverlay.setVisibility(View.GONE);
            }
            if (azFxUnderlay != null) {
                azFxUnderlay.setVisibility(View.GONE);
            }
            if (azLabelOverlay != null) {
                azLabelOverlay.setVisibility(View.GONE);
            }
            clearAccessoryRenderEffectBackdrop();
            applyDecorNavBarSurfaceState(state);
            applyInAppKeyboardSurfaceState(state);
            completePendingInAppKeyboardOpenReveal(state);
            completePendingInAppKeyboardCloseGeometry(state);
            configureAccessoryTopEdgeFx(false, state.barAlpha);
            configureExtraKeysDivider(false, 0f);
            resetAzOverflowAffordanceState();
            if (mDockPlankController != null) {
                mDockPlankController.setEnabled(false);
            }
            updateTopPaneWallpaperFrost();
            return;
        }

        if (accessoryContainer != null)
            accessoryContainer.setVisibility(
                shouldShowAccessoryStack(true, state.keyboardShown) ? View.VISIBLE : View.GONE);
        if (accessorySurfaceHost != null) {
            accessorySurfaceHost.setVisibility(View.VISIBLE);
        }
        if (appsBarViewPager != null) {
            appsBarViewPager.setVisibility(state.appsRowEnabled ? View.VISIBLE : View.GONE);
        }
        if (!state.appsRowEnabled) {
            mSuggestionBarExplicitSearchActive = false;
            resetAzGestureState(false, true);
        }
        if (indicatorBand != null) {
            indicatorBand.setVisibility(state.azRowEnabled ? View.VISIBLE : View.GONE);
        }
        if (terminalToolbarViewPager != null) {
            terminalToolbarViewPager.setVisibility(
                state.extraKeysRowEnabled ? View.VISIBLE : View.GONE);
        }
        if (azRow != null) {
            azRow.setVisibility(state.azRowEnabled ? View.VISIBLE : View.GONE);
        }
        if (azFxUnderlay != null) {
            azFxUnderlay.setVisibility(View.GONE);
        }
        if (azFxOverlay != null) {
            azFxOverlay.setVisibility(View.GONE);
        }
        if (azLabelOverlay != null) {
            azLabelOverlay.setVisibility(View.GONE);
        }

        if (extraKeysBackground != null) {
            extraKeysBackground.setVisibility(useDecorSurface && !isRoundedDockStyle() ? View.GONE : View.VISIBLE);
            // Keyboard-off, the dock continues into the under-pill nav strip, so it renders the top
            // slice [0, f] of the shared model (strip renders [f, 1]) — one foot under the pill.
            // Keyboard-on, the dock is a distinct plank above the keyboard, so it keeps the full model.
            extraKeysBackground.setBackground(buildDockGlassSurface(state.barAlpha,
                0f, state.keyboardShown ? 1f : defaultDockGlassFootFraction(), false));
            // Opacity is baked into the drawable (translucent base) so the glass light model survives.
            extraKeysBackground.setAlpha(1f);
        }
        refreshDockPlankFx(state.barAlpha);

        if (extraKeysBackgroundBlur != null) {
            extraKeysBackgroundBlur.setAlpha(1f);
            extraKeysBackgroundBlur.setVisibility(
                state.blurEnabled && !useRenderEffectBlur && !useDecorSurface ? View.VISIBLE : View.GONE
            );
        }
        configureAccessoryTopEdgeFx(true, state.barAlpha);
        // Thin material hairline at the seam between the A–Z row and the extra-keys row.
        configureExtraKeysDivider(
            state.extraKeysRowEnabled && (state.appsRowEnabled || state.azRowEnabled),
            state.barAlpha);
        applyDecorNavBarSurfaceState(state);
        applyInAppKeyboardSurfaceState(state);
        // Wallpaper passthrough feeds every glass surface from the shared pre-blurred wallpaper, so
        // the live blur views have nothing left to contribute — and each one that keeps capturing
        // redraws the whole window, terminal text included, on the UI thread.
        updateAccessoryRenderEffectBackdrop(state);
        updateTopPaneWallpaperFrost();
        completePendingInAppKeyboardOpenReveal(state);
        completePendingInAppKeyboardCloseGeometry(state);
        updateAzOverflowAffordance();
    }

    /** Reveals keys in the same UI transaction that installs the destination unified backdrop. */
    private void completePendingInAppKeyboardOpenReveal(@NonNull AccessoryRenderState state) {
        if (!mPendingInAppKeyboardOpenReveal) {
            return;
        }
        View keyboardContainer = findViewById(R.id.inapp_keyboard_container);
        if (keyboardContainer == null || !state.keyboardShown) {
            mPendingInAppKeyboardOpenReveal = false;
            removeInAppKeyboardOpenPreDrawGate();
            return;
        }
        boolean unifiedGlassSurface = shouldUseUnifiedDefaultKeyboardGlassSurface(state);
        boolean backdropReady = unifiedGlassSurface
            ? isUnifiedAccessoryBackdropReady(state)
            : isInAppKeyboardLocalBackdropReady(state);
        if (isInAppKeyboardGlassSurface() && state.blurEnabled && !backdropReady) {
            return;
        }
        mPendingInAppKeyboardOpenReveal = false;
        keyboardContainer.setVisibility(View.VISIBLE);
        removeInAppKeyboardOpenPreDrawGate();
    }

    private void revealInAppKeyboardIfStillPending() {
        if (mPendingInAppKeyboardOpenReveal) forceRevealInAppKeyboardNow();
    }

    /** Immediately reveals the keyboard regardless of backdrop readiness (fail-safe path). */
    private void forceRevealInAppKeyboardNow() {
        mPendingInAppKeyboardOpenReveal = false;
        View keyboardContainer = findViewById(R.id.inapp_keyboard_container);
        if (keyboardContainer != null && keyboardContainer.getVisibility() == View.INVISIBLE)
            keyboardContainer.setVisibility(View.VISIBLE);
        removeInAppKeyboardOpenPreDrawGate();
    }

    /** Runs after destination layout but before its first draw, closing the one-frame stale-crop gap. */
    private void installInAppKeyboardOpenPreDrawGate() {
        if (mInAppKeyboardOpenPreDrawListener != null) {
            return;
        }
        View gateView = findViewById(R.id.activity_termux_root_view);
        if (gateView == null) {
            // No view to gate on — reveal now rather than leaving the keyboard invisible.
            forceRevealInAppKeyboardNow();
            return;
        }
        mInAppKeyboardOpenPreDrawView = gateView;
        mInAppKeyboardOpenRevealBlockedFrames = 0;
        mInAppKeyboardOpenPreDrawListener = () -> {
            if (!mPendingInAppKeyboardOpenReveal) {
                removeInAppKeyboardOpenPreDrawGate();
                return true;
            }
            // Posted render syncs run after traversal and would permit one draw with the old,
            // dock-only crop. Refresh synchronously now that destination geometry is measurable.
            applyAccessoryRenderState(buildAccessoryRenderState());
            boolean readyToDraw = !mPendingInAppKeyboardOpenReveal;
            if (!readyToDraw) {
                // Fail-safe: the gate must never wedge the whole window if the backdrop cannot
                // become ready (wallpaper unavailable, blur crop failing). Worst case after three
                // blocked frames is the old one-frame mismatch, never a frozen UI.
                if (++mInAppKeyboardOpenRevealBlockedFrames >= 3) {
                    forceRevealInAppKeyboardNow();
                    return true;
                }
                scheduleAccessoryRenderSync("inapp-keyboard:open-waiting-for-backdrop");
            }
            return readyToDraw;
        };
        gateView.getViewTreeObserver().addOnPreDrawListener(mInAppKeyboardOpenPreDrawListener);
        // Backstop for windows that stop drawing entirely (or test environments with no draw
        // pass): reveal shortly after install even if no pre-draw callback ever fires.
        mAccessoryRenderHandler.removeCallbacks(mInAppKeyboardOpenRevealBackstopRunnable);
        mAccessoryRenderHandler.postDelayed(mInAppKeyboardOpenRevealBackstopRunnable, 160L);
    }

    private void removeInAppKeyboardOpenPreDrawGate() {
        mAccessoryRenderHandler.removeCallbacks(mInAppKeyboardOpenRevealBackstopRunnable);
        View gateView = mInAppKeyboardOpenPreDrawView;
        ViewTreeObserver.OnPreDrawListener listener = mInAppKeyboardOpenPreDrawListener;
        mInAppKeyboardOpenPreDrawView = null;
        mInAppKeyboardOpenPreDrawListener = null;
        if (gateView == null || listener == null) {
            return;
        }
        ViewTreeObserver observer = gateView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(listener);
        }
    }

    /** Stops conservative close-seam coverage after dock-only destination layout is observed. */
    private void completePendingInAppKeyboardCloseGeometry(@NonNull AccessoryRenderState state) {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        ViewGroup.LayoutParams accessoryParams = accessoryContainer != null
            ? accessoryContainer.getLayoutParams() : null;
        int expectedAccessoryHeight = accessoryParams != null && accessoryParams.height > 0
            ? accessoryParams.height : 0;
        boolean destinationLayoutReady = accessoryContainer != null
            && expectedAccessoryHeight > 0
            && accessoryContainer.getHeight() == expectedAccessoryHeight;
        if (!mPendingInAppKeyboardCloseGeometry || state.keyboardShown || accessoryContainer == null) {
            return;
        }
        if (destinationLayoutReady) {
            mPendingInAppKeyboardCloseGeometry = false;
            mInAppKeyboardBackdropDirty = true;
            // Re-evaluate the strip once without the conservative close overscan. At this point the
            // measured dock bottom is stable, so the exact seam crop can replace the safe cover.
            mDecorNavBarBackdropDirty = true;
            scheduleAccessoryRenderSync("inapp-keyboard:close-ready");
        }
    }

    /** Rebuilds dock-only blur after close layout and before that geometry is allowed to draw. */
    private void installInAppKeyboardClosePreDrawCorrection() {
        if (mInAppKeyboardClosePreDrawListener != null)
            return;
        View gateView = findViewById(R.id.activity_termux_root_view);
        if (gateView == null)
            return;
        mInAppKeyboardClosePreDrawView = gateView;
        mInAppKeyboardClosePreDrawListener = () -> {
            AccessoryRenderState state = buildAccessoryRenderState();
            if (!state.keyboardShown) {
                mAccessoryBackdropDirty = true;
                applyAccessoryRenderState(state);
            }
            if (!isDockBackdropSafeForCurrentDestination(state))
                return false;
            removeInAppKeyboardClosePreDrawCorrection();
            return true;
        };
        gateView.getViewTreeObserver().addOnPreDrawListener(mInAppKeyboardClosePreDrawListener);
    }

    private boolean isDockBackdropSafeForCurrentDestination(@NonNull AccessoryRenderState state) {
        if (!shouldUseAccessoryRenderEffectBlur(state))
            return true;
        ImageView backdrop = findViewById(R.id.accessory_blur_backdrop);
        View surfaceHost = findViewById(R.id.accessory_surface_host);
        if (backdrop == null || surfaceHost == null || backdrop.getDrawable() == null
            || backdrop.getVisibility() != View.VISIBLE) {
            return true;
        }
        int horizontalOverscanPx = computeAccessoryBackdropHorizontalOverscanPx(state.blurRadiusDp);
        int seamOverscanPx = !isRoundedDockStyle() && shouldShowDecorNavBarSurface(state)
            ? horizontalOverscanPx : 0;
        Rect target = buildAccessoryBackdropTargetRect(
            surfaceHost, horizontalOverscanPx, seamOverscanPx);
        return isAccessoryBackdropCropHeightCompatible(backdrop, target.height());
    }

    private void removeInAppKeyboardClosePreDrawCorrection() {
        View gateView = mInAppKeyboardClosePreDrawView;
        ViewTreeObserver.OnPreDrawListener listener = mInAppKeyboardClosePreDrawListener;
        mInAppKeyboardClosePreDrawView = null;
        mInAppKeyboardClosePreDrawListener = null;
        if (gateView == null || listener == null)
            return;
        ViewTreeObserver observer = gateView.getViewTreeObserver();
        if (observer.isAlive())
            observer.removeOnPreDrawListener(listener);
    }

    /** Invalidates geometry-dependent crops while preserving the shared full-frame blur. */
    private void invalidateInAppKeyboardTransitionBackdropCrops() {
        mInAppKeyboardBackdropDirty = true;
        mLastInAppKeyboardBackdropTargetRect.setEmpty();
        mAccessoryBackdropDirty = true;
        mLastAccessoryBackdropTargetRect.setEmpty();
        mDecorNavBarBackdropDirty = true;
    }

    private void applyRealtimeBlurRadius(View blurView, int blurRadiusDp) {
        if (!(blurView instanceof RealtimeBlurView)) {
            return;
        }
        float radiusPx = ViewUtils.dpToPx(this, Math.max(0, blurRadiusDp));
        ((RealtimeBlurView) blurView).setBlurRadius(radiusPx);
    }

    private void applyRealtimeBlurDownsampleFactor(View blurView, int downsampleFactor) {
        if (!(blurView instanceof RealtimeBlurView)) {
            return;
        }
        ((RealtimeBlurView) blurView).setDownsampleFactor(Math.max(1, downsampleFactor));
    }

    private void applyRealtimeBlurOverlayColor(View blurView, int overlayColor) {
        if (!(blurView instanceof RealtimeBlurView)) {
            return;
        }
        ((RealtimeBlurView) blurView).setOverlayColor(overlayColor);
    }

    private boolean shouldUseWallpaperPassthroughMode() {
        return mPreferences != null
            && mPreferences.isUseSystemWallpaperEnabled();
    }

    private void syncTerminalWallpaperRenderingMode() {
        if (mTerminalView == null) {
            return;
        }
        mTerminalView.setUseTransparentFrameClear(false);
    }

    private boolean shouldEnableSeamlessStatusBackground() {
        return mProperties != null
            && !mProperties.isUsingFullScreen();
    }

    private boolean shouldShowTerminalStatusBarSurface(boolean showSurface, int terminalSurfaceColor) {
        return shouldEnableSeamlessStatusBackground()
            && showSurface
            && Color.alpha(terminalSurfaceColor) > 0
            && mLastStatusBarInsetTop > 0;
    }

    private void applyTerminalOverlayInsets(@NonNull WindowInsetsCompat insetsCompat) {
        int statusBarInsetTop = insetsCompat.getInsets(Type.statusBars()).top;
        mLastStatusBarInsetTop = statusBarInsetTop;

        View statusBarSurface = findViewById(R.id.terminal_status_bar_background);
        if (statusBarSurface != null) {
            ViewGroup.LayoutParams layoutParams = statusBarSurface.getLayoutParams();
            if (layoutParams != null && layoutParams.height != statusBarInsetTop) {
                layoutParams.height = statusBarInsetTop;
                statusBarSurface.setLayoutParams(layoutParams);
            }
        }

        // Keep content out of the camera hole now that the window draws under the cutout. Only the
        // horizontal insets matter: the top cutout is already covered by the status-bar inset.
        // In landscape the dock rail claims one edge column, so the content inset on that side
        // grows to the rail's width (which itself never shrinks below the cutout inset).
        androidx.core.graphics.Insets cutoutInsets = insetsCompat.getInsets(Type.displayCutout());
        mLastDisplayCutoutInsetLeft = cutoutInsets.left;
        mLastDisplayCutoutInsetRight = cutoutInsets.right;
        mLastNavigationBarInsetBottom = insetsCompat.getInsets(Type.navigationBars()).bottom;
        boolean railActive = isDockRailActive();
        boolean railOnRight = isDockRailOnRight();
        int leftContentInsetPx = railActive && !railOnRight
            ? resolveDockRailWidthPx() : cutoutInsets.left;
        int rightContentInsetPx = railActive && railOnRight
            ? resolveDockRailWidthPx() : cutoutInsets.right;
        View rootRelativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        if (rootRelativeLayout != null
            && (rootRelativeLayout.getPaddingLeft() != leftContentInsetPx
                || rootRelativeLayout.getPaddingRight() != rightContentInsetPx)) {
            rootRelativeLayout.setPadding(leftContentInsetPx, rootRelativeLayout.getPaddingTop(),
                rightContentInsetPx, rootRelativeLayout.getPaddingBottom());
        }

        applyTerminalSurfaceAppearance();
    }

    /**
     * Continue the top pane's glass through the system status-bar inset. This surface is a sibling
     * of the drawer, so Android cannot clip it at the drawer's top bound. The compact window row
     * remains bottom-aligned inside its 96dp pane and the terminal still starts below that pane.
     */
    private void applyTerminalWindowBarBackdropInsets() {
        View host = findViewById(R.id.terminal_window_bar_host);
        View statusGlass = findViewById(R.id.terminal_status_bar_background);
        if (host == null || statusGlass == null) return;
        boolean show = host.getVisibility() == View.VISIBLE
            && isSplitPanesEnabled()
            && shouldEnableSeamlessStatusBackground()
            && !isRoundedDockStyle()
            && mLastStatusBarInsetTop > 0;
        View statusBlur = findViewById(R.id.terminal_status_bar_glass_blur);
        View statusSurface = findViewById(R.id.terminal_status_bar_glass_surface);
        if (!show) {
            statusGlass.setTranslationY(0f);
            if (statusBlur != null) statusBlur.setVisibility(View.GONE);
            if (statusSurface != null) statusSurface.setVisibility(View.GONE);
            updateTopPaneWallpaperFrost();
            return;
        }
        float opacity = mPreferences != null ? mPreferences.getStatusBarOpacity() / 100f : 1f;
        int blurRadiusDp = getEffectiveStatusBarBlurRadius();
        boolean statusBlurEnabled = dockBlurEnabled(blurRadiusDp);
        int glassInset = resolveStatusBarHorizontalInsetPx();
        ViewGroup.LayoutParams glassParams = statusGlass.getLayoutParams();
        if (glassParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams glassMargins = (ViewGroup.MarginLayoutParams) glassParams;
            if (glassMargins.leftMargin != glassInset || glassMargins.rightMargin != glassInset) {
                glassMargins.leftMargin = glassInset;
                glassMargins.rightMargin = glassInset;
                statusGlass.setLayoutParams(glassMargins);
            }
        }
        statusGlass.setTranslationY(-mLastStatusBarInsetTop);
        statusGlass.setBackgroundColor(Color.TRANSPARENT);
        statusGlass.setVisibility(View.VISIBLE);
        applyRealtimeBlurRadius(statusBlur, blurRadiusDp);
        applyRealtimeBlurDownsampleFactor(statusBlur, ACCESSORY_BLUR_DOWNSAMPLE_FACTOR);
        // Same tinted-overlay treatment as the dock's extraKeysBackgroundBlur: colored when blur
        // is actually contributing, transparent otherwise.
        // Blur only — no tint. The glass drawable built below already paints the material wash at
        // this opacity, and the dock reaches the same place by a different route: its overlay call
        // sits behind `!useRenderEffectBlur`, so on any device with RenderEffect the dock's blur is
        // untinted and the shader owns the colour. The status bar had no such guard, so it stacked
        // a full-strength surface colour (alpha 127 at 50%%) under its own tint and went opaque
        // while the dock stayed glass at identical settings.
        applyRealtimeBlurOverlayColor(statusBlur, Color.TRANSPARENT);
        if (statusBlur != null) {
            statusBlur.setVisibility(statusBlurEnabled ? View.VISIBLE : View.GONE);
        }
        if (statusSurface != null) {
            statusSurface.setBackground(buildStatusBarGlassSurface(opacity, 0f,
                terminalWindowGlassStatusFraction(host)));
            statusSurface.setVisibility(View.VISIBLE);
        }
        updateTopPaneWallpaperFrost();
    }

    private float terminalWindowGlassStatusFraction(@NonNull View host) {
        int paneHeight = host.getLayoutParams() != null ? host.getLayoutParams().height : 0;
        if (paneHeight <= 0) paneHeight = Math.round(dpToPx(96));
        return mLastStatusBarInsetTop / (float) (mLastStatusBarInsetTop + paneHeight);
    }

    /** Radius for wallpaper frost on top glass surfaces: follow the dock so the materials match. */
    private int resolveTopGlassFrostRadiusDp() {
        int radiusDp = getEffectiveExtraKeysBlurRadius();
        return radiusDp > 0 ? radiusDp : getEffectiveStatusBarBlurRadius();
    }

    /**
     * In wallpaper passthrough mode the top pane's RealtimeBlurViews can only sample the window's
     * own (transparent) content, so the pane reads as a flat tint while the dock shows frosted
     * wallpaper. Give the status inset band and the window-bar pane crops of the same shared
     * pre-blurred wallpaper frame the dock uses, and rest the useless live-blur views. Runs after
     * the blur views' own visibility passes so its GONE wins while frost is active.
     */
    private void updateTopPaneWallpaperFrost() {
        ImageView statusFrost = findViewById(R.id.terminal_status_bar_wallpaper_backdrop);
        ImageView paneFrost = findViewById(R.id.terminal_window_bar_wallpaper_backdrop);
        if (statusFrost == null || paneFrost == null) return;
        // The status surface's own radius, not the dock's: the editor tunes them apart, and the
        // status slider has to visibly change this pane.
        int blurRadiusDp = getEffectiveStatusBarBlurRadius();
        if (!shouldUseWallpaperPassthroughMode() || blurRadiusDp <= 0) {
            clearTopPaneWallpaperFrost();
            return;
        }
        if (isFullStatusBarEngaged()) {
            alignFullStatusBarWallpaperFrost();
            return;
        }
        // Rounded style: the pane is a floating capsule already clipped to its outline, so it takes
        // frost like any surface; the inset band above it shows raw wallpaper by design. This used
        // to bail out for the whole style, which left the capsule with no blur at all — its live
        // blur view is as blind to the wallpaper as every other RealtimeBlurView here.
        boolean capsule = isRoundedDockStyle();
        boolean statusApplied = !capsule && applyWallpaperFrostCrop(statusFrost,
            findViewById(R.id.terminal_status_bar_background), blurRadiusDp, mLastStatusFrostRect);
        if (capsule) {
            statusFrost.setImageDrawable(null);
            statusFrost.setVisibility(View.GONE);
            mLastStatusFrostRect.setEmpty();
        }
        boolean paneApplied = applyWallpaperFrostCrop(paneFrost,
            findViewById(R.id.terminal_window_bar_host), blurRadiusDp, mLastWindowBarFrostRect);
        View statusBlur = findViewById(R.id.terminal_status_bar_glass_blur);
        View paneBlur = findViewById(R.id.terminal_window_bar_blur);
        if (statusApplied && statusBlur != null) statusBlur.setVisibility(View.GONE);
        // While FULL is engaged the pane's live blur deliberately stays on over the frost
        // (alignFullStatusBarWallpaperFrost) so the terminal behind shows through the glass.
        if (paneApplied && paneBlur != null && !isFullStatusBarEngaged()) {
            paneBlur.setVisibility(View.GONE);
        }
        if (statusApplied || paneApplied) {
            mTopPaneFrostDirty = false;
            mLastTopPaneFrostRadiusDp = blurRadiusDp;
        }
    }

    private void clearTopPaneWallpaperFrost() {
        ImageView statusFrost = findViewById(R.id.terminal_status_bar_wallpaper_backdrop);
        ImageView paneFrost = findViewById(R.id.terminal_window_bar_wallpaper_backdrop);
        if (statusFrost != null) {
            statusFrost.setImageDrawable(null);
            statusFrost.setVisibility(View.GONE);
        }
        if (paneFrost != null) {
            paneFrost.setImageDrawable(null);
            paneFrost.setVisibility(View.GONE);
        }
        mLastStatusFrostRect.setEmpty();
        mLastWindowBarFrostRect.setEmpty();
        mLastCommandPaletteFrostRect.setEmpty();
        mLastAppDrawerFrostRect.setEmpty();
        mLastTopPaneFrostRadiusDp = -1;
    }

    /**
     * FULL displays the already cached screen-sized status-radius frame through the existing pane
     * backdrop. Only its matrix changes as layout moves; no target-sized bitmap is allocated per
     * spring frame and no new blur-radius cache key exists.
     */
    private void alignFullStatusBarWallpaperFrost() {
        if (!isFullStatusBarEngaged() || !shouldUseWallpaperPassthroughMode()) return;
        int radiusDp = getEffectiveStatusBarBlurRadius();
        if (radiusDp <= 0) return;
        ImageView frost = findViewById(R.id.terminal_window_bar_wallpaper_backdrop);
        View host = findViewById(R.id.terminal_window_bar_host);
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        if (frost == null || host == null || wallpaperFrame == null) return;
        Bitmap full = obtainCachedAccessoryWallpaperBlur(radiusDp, wallpaperFrame);
        if (full == null || full.isRecycled()) return;
        host.getLocationOnScreen(mTmpViewLocation);
        float scaleX = mCachedAccessoryWallpaperBlurFrameRect.width()
            / (float) Math.max(1, full.getWidth());
        float scaleY = mCachedAccessoryWallpaperBlurFrameRect.height()
            / (float) Math.max(1, full.getHeight());
        mFullStatusFrostMatrix.reset();
        mFullStatusFrostMatrix.setScale(scaleX, scaleY);
        mFullStatusFrostMatrix.postTranslate(
            mCachedAccessoryWallpaperBlurFrameRect.left - mTmpViewLocation[0],
            mCachedAccessoryWallpaperBlurFrameRect.top - mTmpViewLocation[1]);
        if (!(frost.getDrawable() instanceof BitmapDrawable)
            || ((BitmapDrawable) frost.getDrawable()).getBitmap() != full) {
            frost.setImageBitmap(full);
        }
        frost.setScaleType(ImageView.ScaleType.MATRIX);
        frost.setImageMatrix(mFullStatusFrostMatrix);
        frost.setColorFilter(glassFrostFilter());
        frost.setVisibility(View.VISIBLE);
        // Live blur stays ON above the frost while FULL is engaged: it can see the frozen,
        // still-running terminal behind the pane, so the terminal shows through the glass even
        // in wallpaper mode (the frost keeps covering the wallpaper the blur cannot see).
        View liveBlur = findViewById(R.id.terminal_window_bar_blur);
        if (liveBlur != null) liveBlur.setVisibility(View.VISIBLE);
        mLastWindowBarFrostRect.set(mCachedAccessoryWallpaperBlurFrameRect);
        mLastTopPaneFrostRadiusDp = radiusDp;
        mTopPaneFrostDirty = false;
    }

    private void releaseFullStatusBarWallpaperFrost() {
        ImageView frost = findViewById(R.id.terminal_window_bar_wallpaper_backdrop);
        if (frost != null) {
            frost.setImageDrawable(null);
            frost.setScaleType(ImageView.ScaleType.FIT_XY);
        }
        mLastWindowBarFrostRect.setEmpty();
        mTopPaneFrostDirty = true;
        updateTopPaneWallpaperFrost();
    }

    /** Installs one frost crop matching {@code boundsView}'s screen rect; false hides the frost. */
    private boolean applyWallpaperFrostCrop(@NonNull ImageView frost, @Nullable View boundsView,
                                            int blurRadiusDp, @NonNull Rect lastRect) {
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        if (boundsView == null || wallpaperFrame == null
            || boundsView.getVisibility() != View.VISIBLE
            || boundsView.getWidth() <= 0 || boundsView.getHeight() <= 0) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            lastRect.setEmpty();
            return false;
        }
        boundsView.getLocationOnScreen(mTmpViewLocation);
        Rect targetRect = new Rect(mTmpViewLocation[0], mTmpViewLocation[1],
            mTmpViewLocation[0] + boundsView.getWidth(),
            mTmpViewLocation[1] + boundsView.getHeight());
        if (!mTopPaneFrostDirty && targetRect.equals(lastRect)
            && mLastTopPaneFrostRadiusDp == blurRadiusDp && frost.getDrawable() != null) {
            frost.setVisibility(View.VISIBLE);
            return true;
        }
        Bitmap crop = createCachedAccessoryWallpaperBlurCrop(blurRadiusDp, targetRect, wallpaperFrame);
        if (crop == null) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            lastRect.setEmpty();
            return false;
        }
        frost.setImageBitmap(crop);
        frost.setColorFilter(glassFrostFilter());
        frost.setVisibility(View.VISIBLE);
        lastRect.set(targetRect);
        return true;
    }

    /**
     * Wallpaper frost for the command palette glass. The palette's RealtimeBlurView has the same
     * blind spot as the top pane's: over the home wallpaper it can only blur the window's dim
     * scrim, which renders the glass as grey mud. Returns true when a frost crop was installed
     * and the live blur should rest; the crop spans the full glass pane and the pane's animated
     * outline clips it.
     */
    public boolean applyCommandPaletteWallpaperFrost(@NonNull ImageView frost) {
        int blurRadiusDp = resolveTopGlassFrostRadiusDp();
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        View glass = frost.getParent() instanceof View ? (View) frost.getParent() : null;
        if (!shouldUseWallpaperPassthroughMode() || blurRadiusDp <= 0 || wallpaperFrame == null
            || glass == null || glass.getWidth() <= 0 || glass.getHeight() <= 0) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            return false;
        }
        glass.getLocationOnScreen(mTmpViewLocation);
        Rect targetRect = new Rect(mTmpViewLocation[0], mTmpViewLocation[1],
            mTmpViewLocation[0] + glass.getWidth(), mTmpViewLocation[1] + glass.getHeight());
        // The palette re-applies its frost on every open and on every animated resize. Without the
        // same guard the top-pane path uses, each of those calls re-cut a full-pane crop.
        if (!mTopPaneFrostDirty && targetRect.equals(mLastCommandPaletteFrostRect)
            && mLastCommandPaletteFrostRadiusDp == blurRadiusDp && frost.getDrawable() != null) {
            frost.setVisibility(View.VISIBLE);
            return true;
        }
        Bitmap crop = createCachedAccessoryWallpaperBlurCrop(blurRadiusDp, targetRect, wallpaperFrame);
        if (crop == null) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            mLastCommandPaletteFrostRect.setEmpty();
            return false;
        }
        mLastCommandPaletteFrostRect.set(targetRect);
        mLastCommandPaletteFrostRadiusDp = blurRadiusDp;
        frost.setImageBitmap(crop);
        frost.setColorFilter(glassFrostFilter());
        frost.setVisibility(View.VISIBLE);
        return true;
    }

    /**
     * Wallpaper frost for the app drawer plane's glass, the same blind-spot fix the palette needs:
     * over the home wallpaper the plane's RealtimeBlurView can only blur the window's own dim
     * scrim. Unlike the palette this follows {@link #getEffectiveExtraKeysBlurRadius()} directly
     * rather than {@link #resolveTopGlassFrostRadiusDp()} — the plane grows out of the dock, so it
     * has to be cut from the dock's radius or the two would read as different materials mid-handoff
     * (and a fourth radius would evict the dock's own entry from the pre-blur LRU). Returns true
     * when a frost crop was installed and the live blur should rest; the crop spans the full glass
     * pane and the plane's animated outline clips it.
     */
    public boolean applyAppDrawerWallpaperFrost(@NonNull ImageView frost) {
        int blurRadiusDp = getEffectiveExtraKeysBlurRadius();
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        View glass = frost.getParent() instanceof View ? (View) frost.getParent() : null;
        if (!shouldUseWallpaperPassthroughMode() || blurRadiusDp <= 0 || wallpaperFrame == null
            || glass == null || glass.getWidth() <= 0 || glass.getHeight() <= 0) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            return false;
        }
        glass.getLocationOnScreen(mTmpViewLocation);
        Rect targetRect = new Rect(mTmpViewLocation[0], mTmpViewLocation[1],
            mTmpViewLocation[0] + glass.getWidth(), mTmpViewLocation[1] + glass.getHeight());
        // The plane re-applies its frost on every open; without this guard each open re-cut a
        // full-screen crop.
        if (!mTopPaneFrostDirty && targetRect.equals(mLastAppDrawerFrostRect)
            && mLastAppDrawerFrostRadiusDp == blurRadiusDp && frost.getDrawable() != null) {
            frost.setVisibility(View.VISIBLE);
            return true;
        }
        Bitmap crop = createCachedAccessoryWallpaperBlurCrop(blurRadiusDp, targetRect, wallpaperFrame);
        if (crop == null) {
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
            mLastAppDrawerFrostRect.setEmpty();
            return false;
        }
        mLastAppDrawerFrostRect.set(targetRect);
        mLastAppDrawerFrostRadiusDp = blurRadiusDp;
        frost.setImageBitmap(crop);
        frost.setColorFilter(glassFrostFilter());
        frost.setVisibility(View.VISIBLE);
        return true;
    }

    private void applyDockImeOffset(int imeLiftPx) {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null) {
            return;
        }
        float translationY = -Math.max(0, imeLiftPx);
        if (accessoryContainer.getTranslationY() != translationY) {
            accessoryContainer.setTranslationY(translationY);
        }
    }

    /**
     * IME lift for the dock, owned by insets only while system bars are hidden (fullscreen property
     * or externally forced immersive). With bars visible, {@link TermuxActivityRootView}'s
     * visible-frame probe already lifts the whole root above the keyboard, so applying an inset
     * lift too would double it.
     */
    private int computeDockImeLiftPx(@NonNull WindowInsetsCompat insets) {
        // Insets can retain the previous app's mid-transition IME snapshot across a home resume.
        // Accept them only after an input flow in this activity explicitly requested the IME.
        if (!mAcceptSystemImeInsets || isSystemImeSuppressedByInAppKeyboard()) {
            return 0;
        }
        if (!insets.isVisible(Type.ime())) {
            return 0;
        }
        if (insets.isVisible(Type.navigationBars())) {
            return 0;
        }
        return Math.max(0,
            insets.getInsets(Type.ime()).bottom - insets.getInsets(Type.navigationBars()).bottom);
    }

    /** True while the dock is being lifted above the IME via insets instead of the root-view probe. */
    public boolean isDockImeLiftActive() {
        return mImeLiftPx > 0;
    }

    /**
     * True while the embedded keyboard holds activity-wide system-IME suppression — the system
     * keyboard cannot legitimately be visible, so IME-driven layout adjustments must not run.
     */
    public boolean isSystemImeSuppressedByInAppKeyboard() {
        return mInAppKeyboard != null && mInAppKeyboard.isSystemImeSuppressed();
    }

    public boolean shouldAcceptSystemImeLayout() {
        return mAcceptSystemImeInsets;
    }

    /** Marks subsequent IME insets as activity-owned rather than inherited from the previous app. */
    public void onSystemImeRequested() {
        mAcceptSystemImeInsets = true;
        View content = findViewById(android.R.id.content);
        if (content != null)
            ViewCompat.requestApplyInsets(content);
    }

    private void resetInheritedImeLayoutState() {
        mAcceptSystemImeInsets = false;
        mImeLiftPx = 0;
        applyDockImeOffset(0);
    }

    private void applyFullscreenMode() {
        if (mProperties == null || getWindow() == null) {
            return;
        }
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(
            getWindow(), decorView);
        boolean fullscreen = mProperties.isUsingFullScreen();
        if (fullscreen) {
            insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            insetsController.hide(Type.systemBars());
        } else {
            insetsController.show(Type.systemBars());
        }
    }

    private void applySeamlessStatusBackgroundModeIfNeeded() {
        boolean enable = shouldEnableSeamlessStatusBackground();
        boolean modeChanged = mSeamlessStatusBackgroundActive != enable;
        mSeamlessStatusBackgroundActive = enable;
        // Both normal and fullscreen modes are edge-to-edge. Fullscreen previously restored
        // decor-fitting immediately before hiding the bars, leaving the wallpaper/content frame
        // cropped below the old status-bar inset (most visible during transient-bar gestures).
        boolean edgeToEdge = enable || (mProperties != null && mProperties.isUsingFullScreen());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), !edgeToEdge);

        if (mTermuxActivityRootView != null) {
            mTermuxActivityRootView.setClipToPadding(!edgeToEdge);
            mTermuxActivityRootView.setClipChildren(!edgeToEdge);
        }
        View terminalRootContainer = findViewById(R.id.terminal_root_container);
        if (terminalRootContainer instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) terminalRootContainer;
            container.setClipToPadding(!edgeToEdge);
            container.setClipChildren(!edgeToEdge);
        }

        if (modeChanged) resetRootBottomMarginAfterEdgeModeToggle();
        View content = findViewById(android.R.id.content);
        if (content != null) {
            ViewCompat.requestApplyInsets(content);
        }
    }

    private void resetRootBottomMarginAfterEdgeModeToggle() {
        if (mTermuxActivityRootView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = mTermuxActivityRootView.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
            if (marginLayoutParams.bottomMargin != 0) {
                marginLayoutParams.bottomMargin = 0;
                mTermuxActivityRootView.setLayoutParams(marginLayoutParams);
            }
        }
        mTermuxActivityRootView.marginBottom = 0;
        mTermuxActivityRootView.lastMarginBottom = null;
        mTermuxActivityRootView.lastMarginBottomTime = 0L;
        mTermuxActivityRootView.lastMarginBottomExtraTime = 0L;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.logDebug(LOG_TAG, "onStop");
        com.termux.app.terminal.TerminalActionDispatcher.getInstance().detach(this);
        mTerminalFrameMetricsMonitor.stop();
        stopAzEdgePagingLoop();
        cancelAzOverflowRefresh();
        mWindowLabelHandler.removeCallbacksAndMessages(null);
        mBackgroundProcessHandler.removeCallbacks(mBackgroundProcessResync);
        mStatusCardHost.dismiss();
        if (mStatsController != null) mStatsController.stop();
        // Sampling stops here, so the smoothed history stops meaning anything. Dropped now rather
        // than aged out on resume: the first reading the user sees again has to be the true one.
        mBarCpuSmoother.reset();
        mBarMemorySmoother.reset();
        if (mIsInvalidState)
            return;
        if (mWidgetPaneController != null) mWidgetPaneController.onStop();
        if (mWidgetHostController != null) mWidgetHostController.onStop();
        mIsVisible = false;
        if (mFullStatusBarController != null) mFullStatusBarController.closeImmediateToPrior();
        if (mDockPlankController != null) {
            mDockPlankController.reset();
        }
        if (mCommandPalette != null)
            mCommandPalette.dismissImmediately();
        if (mTerminalSheet != null)
            mTerminalSheet.dismissImmediately();
        closeAppDrawerImmediate();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();
        if (mInAppKeyboard != null)
            mInAppKeyboard.onStop();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.setHostVisible(false);
        }
        removeTermuxActivityRootViewGlobalLayoutListener();
        removeAccessoryKeyboardLayoutListener();
        removeAccessoryLayoutChangeListeners();
        unregisterTermuxActivityBroadcastReceiver();
        unregisterPackageChangeReceiver();
        unregisterLauncherAppsCallback();
        unregisterWallpaperColorsChangedListener();
        mAccessoryRenderHandler.removeCallbacks(mInAppKeyboardPreviewGeometrySyncRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryRenderSyncRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurHeartbeatRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurRecoveryRunnable);
        mAccessoryRenderSyncPending = false;
        mInAppKeyboardPreviewGeometrySyncPosted = false;
        mPendingInAppKeyboardCloseGeometry = false;
        removeInAppKeyboardOpenPreDrawGate();
        removeInAppKeyboardClosePreDrawCorrection();
        applyDockImeOffset(0);
        clearAccessoryRenderEffectBackdrop();
        hideDecorNavBarSurfaceOverlay(true);
        mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
        mAzGestureHandler.removeCallbacks(mLauncherCatalogWarmRunnable);
        getDrawer().closeDrawers();
        restoreExpandedStatusAfterSurfaceEditor();
    }

    /**
     * A backgrounded home app that keeps several full-screen blur bitmaps alive is exactly what
     * aggressive vendor memory killers reap first — and a reaped default launcher reads to the
     * user as "the app crashed" the moment they press home. Everything released here is rebuilt
     * on demand through the existing dirty flags, so the only cost of a trim is one blur redraw
     * on the way back in.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_BACKGROUND || mIsInvalidState) {
            return;
        }
        clearCachedAccessoryWallpaperBlur();
        clearInAppKeyboardBackdrop();
        clearAccessoryRenderEffectBackdrop();
        mAccessoryBackdropDirty = true;
        mDecorNavBarBackdropDirty = true;
        mInAppKeyboardBackdropDirty = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logDebug(LOG_TAG, "onDestroy");
        com.termux.app.terminal.TerminalActionDispatcher.getInstance().detach(this);
        mTerminalFrameMetricsMonitor.stop();
        // The inspector holds this Activity strongly for the life of its overlay, so it has to go
        // with the Activity rather than outlive it.
        com.termux.app.terminal.TerminalKeyInspector.close();
        clearCachedAccessoryWallpaperBlur();
        unregisterPreferredHomeChangeReceiver();
        if (mIsInvalidState)
            return;
        if (mCommandPalette != null) {
            mCommandPalette.dismissImmediately();
            mCommandPalette = null;
        }
        if (mTerminalSheet != null) {
            mTerminalSheet.dismissImmediately();
            mTerminalSheet = null;
        }
        if (mInAppKeyboard != null) {
            mTermuxTerminalViewClient.setInAppKeyboardController(null);
            mInAppKeyboard.onDestroy();
            mInAppKeyboard = null;
        }
        clearAccessoryRenderEffectBackdrop();
        removeDecorNavBarSurfaceOverlay();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.releaseResources();
        }
        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
            mTermuxService = null;
        }
        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        if (mInAppKeyboard != null)
            mInAppKeyboard.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
        if (mFullStatusBarController != null && mFullStatusBarController.isEngaged()) {
            savedInstanceState.putBoolean(ARG_FULL_STATUS_BAR, true);
            savedInstanceState.putString(ARG_FULL_STATUS_BAR_PRIOR,
                mFullStatusBarController.priorState().name());
        }
        Bundle paneLayout = savePaneLayoutState();
        if (paneLayout != null) savedInstanceState.putBundle(ARG_PANE_LAYOUT, paneLayout);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Logger.logVerbose(LOG_TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.refreshMaterialTerminalColorsIfNeeded();
        if (mInAppKeyboard != null) {
            mInAppKeyboardShiftLocked = false;
            mInAppKeyboard.onConfigurationChanged(newConfig);
        }
        if (mCommandPalette != null) {
            mCommandPalette.dismissImmediately();
            mCommandPalette.refreshAppearance();
        }
        // No refresh pass to match the palette's: a sheet's glass is built per show(), so the next
        // one already picks up the new configuration.
        if (mTerminalSheet != null)
            mTerminalSheet.dismissImmediately();
        // Every pre-blurred wallpaper frame describes the orientation being left; a rotation makes
        // all of them wrong at once.
        clearCachedAccessoryWallpaperBlur();
        scheduleOrientationGeometryPass();
    }

    /**
     * Runs the orientation-dependent geometry pass once the new layout exists.
     *
     * <p>A rotation delivers {@code onConfigurationChanged} <em>before</em> the window is re-laid
     * out: the decor view, the display metrics and every child still report the orientation being
     * left. Running the pass inline therefore sized the accessory stack from stale geometry, posted
     * a terminal resize, and then did it all again after the real layout — the two SIGWINCHes
     * ({@code 11 110} then {@code 12 110}) one rotation into landscape produced, which made TUIs
     * redraw twice. The same stale pass is what filled the glass with a crop of the outgoing frame.
     *
     * <p>{@link OneShotPreDrawListener} rather than a {@code post()}: it fires after measure and
     * layout of the new configuration and before the first draw, so nothing is ever painted from
     * the intermediate state.
     */
    private void scheduleOrientationGeometryPass() {
        View decorView = getWindow() != null ? getWindow().getDecorView() : null;
        if (decorView == null) {
            runOrientationGeometryPass();
            return;
        }
        if (mPendingOrientationGeometryPass != null) {
            // Two rotations before a single layout: the later one describes where we end up.
            mPendingOrientationGeometryPass.removeListener();
        }
        mPendingOrientationGeometryPass = OneShotPreDrawListener.add(decorView, () -> {
            mPendingOrientationGeometryPass = null;
            runOrientationGeometryPass();
        });
    }

    /**
     * Dock metrics are orientation-dependent: landscape collapses the horizontal rows in favor of
     * the rail, portrait restores them. The full toolbar-geometry pass reruns because the accessory
     * stack container is explicitly sized from these metrics.
     */
    @VisibleForTesting
    void runOrientationGeometryPass() {
        // The listener is attached to the decor view and can outlive the state the pass reads —
        // a rotation delivered while the activity is tearing down still draws once.
        if (mProperties == null) return;
        setTerminalToolbarHeight();
        updateDockRailView();
        // The render state hides the apps and A-Z rows in landscape and shows them in portrait, and
        // it is derived, not stored — so it has to be rebuilt here too. Without this the rows a
        // landscape session collapsed stayed collapsed after rotating back, with their preferences
        // still enabled, until something else happened to sync the accessory stack.
        configureExtraKeysBackground();
        updateWindowBackgroundForCurrentSession();
    }

    /** True while a rotation's geometry pass is waiting for the new layout. */
    @VisibleForTesting
    boolean hasPendingOrientationGeometryPass() {
        return mPendingOrientationGeometryPass != null;
    }

    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");
        mTermuxService = ((TermuxService.LocalBinder) service).service;
        restorePaneLayoutState();
        ensureWindowsForServiceSessions();
        setTermuxSessionsListView();
        final Intent intent = getIntent();
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.maybeHandleGestureContract(intent, mSuggestionBarView);
        }
        setIntent(null);
        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                if (!recoverEmptyVisibleSessionInPlace(intent, "service-connected")) {
                    startBootstrapAndSession(intent);
                }
            } else {
                // Service can connect before onStart() on some devices. Defer bootstrap/session creation.
                if (mIsOnResumeAfterOnCreate) {
                    mPendingBootstrapOnStart = true;
                    mPendingLaunchIntent = intent;
                } else {
                    // Service connected while activity is actually in background - bail out.
                    finishActivityIfNotFinishing();
                }
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }
        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
        rebuildDrawerSessions();
    }

    private void startBootstrapAndSession(@Nullable Intent intent) {
        TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
            // Bootstrap setup may complete after app startup; re-attempt launcher CLI script install.
            LauncherCtlApiServer.getInstance().ensureCliScriptsInstalled();
            TermuxShellIntegrationInstaller.ensureInstalled(this);
            TermuxLauncherConfigInstaller.ensureInstalled(this);

            // Activity might have been destroyed.
            if (mTermuxService == null) {
                mEmptySessionRecoveryInProgress = false;
                return;
            }
            try {
                boolean launchFailsafe = false;
                if (intent != null && intent.getExtras() != null) {
                    launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                }
                mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                mEmptySessionRecoveryInProgress = false;
            } catch (WindowManager.BadTokenException e) {
                // Activity finished - ignore.
                mEmptySessionRecoveryInProgress = false;
            }
        });
    }

    private void maybeRecoverFromEmptySession(@NonNull String source) {
        if (!mIsVisible || mTermuxService == null || mTermuxTerminalSessionActivityClient == null) {
            return;
        }
        if (!mTermuxService.isTermuxSessionsEmpty()) {
            return;
        }
        recoverEmptyVisibleSessionInPlace(null, source);
    }

    private boolean shouldRecoverEmptySessionInPlace() {
        return mIsVisible && (mLastLaunchWasLauncherEntry || isTaskRoot() || isDefaultHomeApp());
    }

    private void resetUiForInPlaceSessionRecovery(@NonNull String reason) {
        Logger.logWarn(LOG_TAG, "Resetting launcher UI before in-place empty-session recovery from " + reason);
        if (mTerminalActionDialog != null) {
            mTerminalActionDialog.dismiss();
            mTerminalActionDialog = null;
        }
        if (mTerminalSheet != null) mTerminalSheet.dismissImmediately();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.resetTransientVisualState();
            mSuggestionBarView.clearAzPreview();
        }
        stopAzEdgePagingLoop();
        cancelAzOverflowRefresh();
        mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryRenderSyncRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurHeartbeatRunnable);
        mAccessoryRenderSyncPending = false;
        if (mTerminalView != null) {
            mTerminalView.onContextMenuClosed(null);
        }
        getDrawer().closeDrawers();
    }

    public boolean recoverEmptyVisibleSessionInPlace(@Nullable Intent intent, @NonNull String reason) {
        if (!shouldRecoverEmptySessionInPlace() || mTermuxService == null || mTermuxTerminalSessionActivityClient == null) {
            return false;
        }
        if (!mTermuxService.isTermuxSessionsEmpty()) {
            mEmptySessionRecoveryInProgress = false;
            return true;
        }
        if (mEmptySessionRecoveryInProgress) {
            Logger.logWarn(LOG_TAG, "Ignoring duplicate empty-session recovery while one is already running: " + reason);
            return true;
        }
        long now = SystemClock.elapsedRealtime();
        if (mLastEmptySessionRecoveryElapsedMs > 0
            && (now - mLastEmptySessionRecoveryElapsedMs) < EMPTY_SESSION_RECOVERY_DEBOUNCE_MS) {
            Logger.logWarn(LOG_TAG, "Ignoring empty-session recovery during debounce window: " + reason);
            return true;
        }
        mLastEmptySessionRecoveryElapsedMs = now;
        mEmptySessionRecoveryInProgress = true;
        Logger.logWarn(LOG_TAG, "No active terminal session while visible Home launcher; recovering in-place from " + reason);
        resetUiForInPlaceSessionRecovery(reason);
        startBootstrapAndSession(intent);
        return true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");
        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }

    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }

    private void setActivityThemeAndWindow() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        boolean useWallpaperTheme = shouldUseWallpaperPassthroughMode();
        setTheme(useWallpaperTheme ? R.style.Theme_TermuxActivity_Wallpaper : R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        TermuxThemeManager.applyThemeOverlays(this);
        Logger.logDebug(LOG_TAG, "Applied " + (useWallpaperTheme ? "wallpaper" : "normal") + " theme");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        // The wallpaper theme sets windowTranslucentNavigation=true, which makes the system draw a
        // dark translucent scrim behind the gesture pill that ignores setNavigationBarColor — this
        // was the persistent dark band under the pill. Clear it so our transparent nav bar (and the
        // unified background dim / dock surface beneath it) is what actually shows.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Let the window extend into the display-cutout ear in landscape; otherwise the system
            // letterboxes the window off that edge and the raw wallpaper shows as an unscrimmed strip.
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = shouldUseWallpaperPassthroughMode() ? 0 : mProperties.getTerminalMarginHorizontal();
        int marginVertical = shouldUseWallpaperPassthroughMode() ? 0 : mProperties.getTerminalMarginVertical();
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }

    private void setSuggestionBarView() {
        final FrameLayout appsBarContainer = findViewById(R.id.apps_bar_viewpager);
        mAzScrubRowView = findViewById(R.id.apps_bar_az_row);
        mAzTerminalToolbarView = findViewById(R.id.terminal_toolbar_view_pager);
        mLauncherAzGestureFxUnderlayView = findViewById(R.id.apps_bar_az_fx_underlay);
        mLauncherAzGestureFxOverlayView = findViewById(R.id.apps_bar_az_fx_overlay);
        mLauncherAzGestureFxLabelOverlayView = findViewById(R.id.apps_bar_az_label_overlay);
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setRenderLayer(LauncherAzGestureFxView.RenderLayer.UNDERLAY);
            mLauncherAzGestureFxUnderlayView.setFocusedIconRingEnabled(false);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setRenderLayer(LauncherAzGestureFxView.RenderLayer.OVERLAY);
            mLauncherAzGestureFxOverlayView.setFocusedIconRingEnabled(true);
        }
        if (mLauncherAzGestureFxLabelOverlayView != null) {
            mLauncherAzGestureFxLabelOverlayView.setRenderLayer(LauncherAzGestureFxView.RenderLayer.OVERLAY);
            mLauncherAzGestureFxLabelOverlayView.setFocusedIconRingEnabled(false);
        }
        setupDockPlankFx();
        if (appsBarContainer == null) {
            return;
        }
        appsBarContainer.setClipChildren(false);
        appsBarContainer.setClipToPadding(false);
        ViewParent vpParent = appsBarContainer.getParent();
        if (vpParent instanceof ViewGroup) {
            ViewGroup parentGroup = (ViewGroup) vpParent;
            parentGroup.setClipChildren(false);
            parentGroup.setClipToPadding(false);
        }

        if (mPreferences != null) {
            if (mLauncherAppDataProvider == null) {
                mLauncherAppDataProvider = LauncherAppDataProvider.getInstance(this);
            }
            if (mLauncherConfigRepository == null) {
                mLauncherConfigRepository = LauncherConfigRepository.getInstance(this);
            }
        }

        if (mSuggestionBarView == null) {
            LayoutInflater inflater = LayoutInflater.from(TermuxActivity.this);
            mSuggestionBarView = (SuggestionBarView) inflater.inflate(R.layout.suggestion_bar, appsBarContainer, false);
        } else if (mSuggestionBarView.getParent() instanceof ViewGroup) {
            ((ViewGroup) mSuggestionBarView.getParent()).removeView(mSuggestionBarView);
        }

        if (mSuggestionBarView.getParent() != appsBarContainer) {
            appsBarContainer.removeAllViews();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            appsBarContainer.addView(mSuggestionBarView, params);
        }

        mSuggestionBarView.setAppDataProvider(mLauncherAppDataProvider);
        mSuggestionBarView.setConfigRepository(mLauncherConfigRepository);
        mSuggestionBarView.setAppCatalogChangedListener(() -> {
            syncAzScrubLettersAndTint();
            updateDockRailView();
        });
        mSuggestionBarView.setNotificationPopupInteractionListener(
            new SuggestionBarView.NotificationPopupInteractionListener() {
                @Override
                public void onNotificationPopupShown() {
                    if (mInAppKeyboard != null)
                        mInAppKeyboard.beginExternalTextInput();
                }

                @Override
                public void onNotificationPopupDismissed() {
                    if (mInAppKeyboard != null)
                        mInAppKeyboard.endExternalTextInput();
                }
            });
        mSuggestionBarView.setLaunchRippleListener(this::playAppLaunchRipple);
        mSuggestionBarView.setAppDrawerGestureListener(
            new SuggestionBarView.AppDrawerGestureListener() {
                @Override
                public boolean isAppDrawerEnabled() {
                    return mPreferences != null && mPreferences.isAppLauncherDrawerEnabled();
                }

                @Override
                public boolean isDockTuningActive() {
                    return mDockTuningMode;
                }

                @Override
                public boolean isCommandPaletteOpen() {
                    return TermuxActivity.this.isCommandPaletteOpen();
                }

                @Override
                public boolean isAppDrawerEngaged() {
                    return TermuxActivity.this.isAppDrawerEngaged();
                }

                @Override
                public boolean isFullStatusPaneClosed() {
                    return !TermuxActivity.this.isFullStatusBarEngaged();
                }

                @Override
                public void onDrawerDragBegin(float downRawY) {
                    getAppDrawerController().beginDrag(downRawY);
                }

                @Override
                public void onDrawerDrag(float rawY) {
                    getAppDrawerController().updateDrag(rawY);
                }

                @Override
                public void onDrawerDragEnd(float velocityPxPerSec) {
                    getAppDrawerController().endDrag(velocityPxPerSec);
                }

                @Override
                public void onDrawerDragCancel() {
                    getAppDrawerController().cancelDrag();
                }
            });
        // Only when the controller already exists: the row is also registered by the lazy accessor
        // itself, so an install that never pulls the drawer down still never builds one.
        if (mAppDrawerController != null)
            mAppDrawerController.setDockChoreographyTarget(mSuggestionBarView);
        applySuggestionBarPreferences();
        applyDockLayoutMetrics(buildDockLayoutMetrics(0));
        if (isLauncherCatalogEnabled()) {
            mSuggestionBarView.reload();
        }
        mSuggestionBarView.post(() -> {
            if (mSuggestionBarView == null || !mIsVisible || !isLauncherCatalogEnabled()) {
                return;
            }
            scheduleLauncherCatalogWarmup();
        });
        if (mTermuxTerminalViewClient != null) {
            mTermuxTerminalViewClient.setSuggestionBarCallback(this);
        }

        if (mAzScrubRowView != null) {
            mAzScrubRowView.setScrubCallback(new AzScrubRowView.ScrubCallback() {
                @Override
                public void onScrub(char letter, int selectionIndex, float touchX, float touchY,
                                    float rawX, float rawY, long eventTimeMs,
                                    @NonNull AzScrubRowView.GesturePhase phase) {
                    handleAzGestureScrub(letter, selectionIndex, touchX, touchY, rawX, rawY,
                        eventTimeMs, phase);
                }

                @Override
                public void onCancel() {
                    resetAzGestureState(false, true);
                }

                @Override
                public void onDoubleTap() {
                    lockScreenFromAzDoubleTap();
                }
            });
        }
    }

    private boolean isLauncherHomeIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())) {
            return false;
        }
        Set<String> categories = intent.getCategories();
        if (categories == null || categories.isEmpty()) {
            return false;
        }
        return categories.contains(Intent.CATEGORY_HOME) || categories.contains(Intent.CATEGORY_LAUNCHER);
    }

    static boolean shouldShowInRecents(boolean showWhenNotHomeEnabled, boolean isDefaultHome) {
        return showWhenNotHomeEnabled && !isDefaultHome;
    }

    private boolean isDefaultHomeApp() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        PackageManager packageManager = getPackageManager();
        ResolveInfo resolveInfo = packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return false;
        }
        String homePackage = resolveInfo.activityInfo.packageName;
        return !TextUtils.isEmpty(homePackage) && getPackageName().equals(homePackage);
    }

    private boolean shouldShowInRecents() {
        return mPreferences != null
            && shouldShowInRecents(mPreferences.isShowInRecentsWhenNotDefaultEnabled(), isDefaultHomeApp());
    }

    private void syncRecentsVisibilityPolicy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        boolean excludeFromRecents = !shouldShowInRecents();
        try {
            if (getTaskId() != -1) {
                for (android.app.ActivityManager.AppTask appTask : getSystemService(android.app.ActivityManager.class).getAppTasks()) {
                    if (appTask == null || appTask.getTaskInfo() == null
                        || taskIdOf(appTask.getTaskInfo()) != getTaskId()) {
                        continue;
                    }
                    appTask.setExcludeFromRecents(excludeFromRecents);
                    break;
                }
            }
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Failed to sync recents visibility: " + throwable.getMessage());
        }
    }

    /**
     * {@code TaskInfo.taskId} only exists from Q. Reading it below that throws NoSuchFieldError,
     * which the caller's catch swallows — so the recents policy silently stopped applying on
     * Android 8 and 9 instead of failing loudly. The pre-Q field carries the same task id.
     */
    @SuppressWarnings("deprecation")
    private static int taskIdOf(@NonNull android.app.ActivityManager.RecentTaskInfo taskInfo) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? taskInfo.taskId : taskInfo.id;
    }

    private char getSuggestionBarSplitChar() {
        if (mPreferences == null) {
            return ' ';
        }
        String inputChar = mPreferences.getAppLauncherInputChar();
        if (inputChar == null) {
            return ' ';
        }
        String trimmed = inputChar.trim();
        return trimmed.isEmpty() ? ' ' : trimmed.charAt(0);
    }

    private boolean isSuggestionBarEnabled() {
        return mPreferences != null && mPreferences.isAppLauncherAppsRowEnabled();
    }

    private boolean isAzRowEnabled() {
        return mPreferences != null && mPreferences.isAppLauncherAzRowEnabled();
    }

    private boolean isLauncherCatalogEnabled() {
        return isSuggestionBarEnabled() || isAzRowEnabled();
    }

    public boolean shouldProcessSuggestionBarKeyEvent(int keyCode) {
        if (!isSuggestionBarEnabled() || mSuggestionBarView == null) {
            return false;
        }
        if (keyCode == android.view.KeyEvent.KEYCODE_DEL || keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
            return mSuggestionBarExplicitSearchActive || mSuggestionBarView.isSearchSurfaceActive();
        }
        return false;
    }

    /** Consumes navigation only while a literal app-search command is visible in the normal buffer. */
    public boolean handleTerminalAppSearchKey(int keyCode) {
        if (mTerminalView == null || mSuggestionBarView == null) return false;
        boolean literalMode = mSuggestionBarExplicitSearchActive
            && mTerminalView.isCurrentInputAppSearchMode();
        TerminalAppSearchKeyDecision.Action action = TerminalAppSearchKeyDecision.decide(
            literalMode, mTerminalView.isAlternateBufferActive(),
            mSuggestionBarView.getTerminalSearchResultCount(), keyCode);
        switch (action) {
            case PREVIOUS:
                return mSuggestionBarView.moveTerminalSearchFocus(-1);
            case NEXT:
                return mSuggestionBarView.moveTerminalSearchFocus(1);
            case LAUNCH:
                mSuggestionBarExplicitSearchActive = false;
                return mSuggestionBarView.launchFocusedTerminalSearchEntry();
            case EXIT:
                mSuggestionBarExplicitSearchActive = false;
                mSuggestionBarView.clearTerminalSearchFocus();
                mSuggestionBarView.reloadWithInput("", mTerminalView);
                return false;
            default:
                if (mSuggestionBarExplicitSearchActive && !literalMode) {
                    mSuggestionBarExplicitSearchActive = false;
                    mSuggestionBarView.clearTerminalSearchFocus();
                }
                return false;
        }
    }

    public boolean shouldProcessSuggestionBarCodePoint(int codePoint, boolean ctrlDown) {
        if (ctrlDown || !isSuggestionBarEnabled() || mSuggestionBarView == null) {
            return false;
        }
        if (mSuggestionBarExplicitSearchActive || mSuggestionBarView.isSearchSurfaceActive()) {
            return true;
        }
        char[] chars = Character.toChars(codePoint);
        return chars.length == 1 && chars[0] == getSuggestionBarSplitChar();
    }

    public boolean shouldDelaySoftKeyboardShowOnResume() {
        return isDefaultHomeApp() && isLauncherHomeIntent(getIntent());
    }

    private void applyAccessoryGeometryIfNeeded(boolean force, @NonNull String reason) {
        // Same freeze as setTerminalToolbarHeight: while the drawer plane owns the stack every
        // band moves by translation/clip only, and a relayout here would fight it. Replayed on close.
        if (isAppDrawerEngaged()) {
            mAppDrawerGeometryFreezePending = true;
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!force && (now - mLastAccessoryGeometryApplyUptimeMs) < 120L) {
            scheduleAccessoryRenderSync(reason + ":skip");
            return;
        }
        mLastAccessoryGeometryApplyUptimeMs = now;
        updateAppLauncherBarHeight();
        setTerminalToolbarHeight(true);
        configureExtraKeysBackground();
    }

    static int calculateSuggestionBarMaxButtons(DisplayMetrics displayMetrics) {
        if (displayMetrics == null) {
            return 1;
        }
        float density = Math.max(displayMetrics.density, 0.1f);
        int screenWidthDp = (int) (displayMetrics.widthPixels / density);
        return Math.max(1, screenWidthDp / SUGGESTION_BAR_MIN_BUTTON_DP);
    }

    private void applySuggestionBarPreferences() {
        if (mSuggestionBarView == null || mPreferences == null) {
            return;
        }
        if (mLauncherAppDataProvider == null) {
            mLauncherAppDataProvider = LauncherAppDataProvider.getInstance(this);
        }
        if (mLauncherConfigRepository == null) {
            mLauncherConfigRepository = LauncherConfigRepository.getInstance(this);
        }
        int iconPreferencesSignature = computeLauncherIconPreferencesSignature();
        // Icon-pack/BW changes rebuild every entry, but in the background: the old synchronous
        // invalidate()+clearAppCache() here emptied the dock and drawer for the whole re-resolve,
        // and a scoped (pinned-only) pack apply could render mid-wipe with holes in the pages.
        boolean iconPreferencesChanged = mLastLauncherIconPreferencesSignature != Integer.MIN_VALUE
            && mLastLauncherIconPreferencesSignature != iconPreferencesSignature;
        mLastLauncherIconPreferencesSignature = iconPreferencesSignature;
        int maxButtons = mPreferences.getAppLauncherButtonCount();
        if (maxButtons <= 0) {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            maxButtons = calculateSuggestionBarMaxButtons(displayMetrics);
        }
        mSuggestionBarView.setMaxButtonCount(maxButtons);
        mSuggestionBarView.setDefaultButtons(new ArrayList<>());
        mSuggestionBarView.setTextSize(10f);
        mSuggestionBarView.setBandW(mPreferences.isAppLauncherBwIconsEnabled());
        mSuggestionBarView.setIconScale(resolveDerivedDockIconScale());
        mSuggestionBarView.setDockRowHeightHintPx(resolveDockAppsBarHeightHintPx(buildDockLayoutMetrics(0).appsBarHeightPx));
        mSuggestionBarView.setAppBarOpacity(mPreferences.getAppBarOpacity());
        int blurRadiusDp = getEffectiveExtraKeysBlurRadius();
        mSuggestionBarView.setBlurConfig(dockBlurEnabled(blurRadiusDp), blurRadiusDp);
        mSuggestionBarView.setInheritedTintColor(resolveAccessoryGlassBaseColor());
        mSuggestionBarView.setNotificationBadgesEnabled(mPreferences.isAppLauncherNotificationDotsEnabled());
        boolean rowHapticsEnabled = mPreferences.isAppLauncherRowHapticsEnabled();
        mSuggestionBarView.setRowHapticsEnabled(rowHapticsEnabled);
        if (mAzScrubRowView != null)
            mAzScrubRowView.setRowHapticsEnabled(rowHapticsEnabled);
        mSuggestionBarView.setMostUsedPageEnabled(mPreferences.isAppLauncherMostUsedPageEnabled());
        mSuggestionBarView.setAppDataProvider(mLauncherAppDataProvider);
        mSuggestionBarView.setConfigRepository(mLauncherConfigRepository);
        mSuggestionBarView.setAppCatalogChangedListener(() -> {
            syncAzScrubLettersAndTint();
            updateDockRailView();
        });
        mSuggestionBarView.setOverflowInteractionListener(new SuggestionBarView.OverflowInteractionListener() {
            @Override
            public void onOverflowInteractionChanged(boolean interacting) {
                onSuggestionBarOverflowInteractionChanged(interacting);
            }

            @Override
            public void onOverflowPagePositionChanged(float pagePosition) {
                updateAzOverflowAffordance();
            }
        });
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.onAnimationPreferenceUpdated();
        }
        if (!isLauncherCatalogEnabled()) {
            mSuggestionBarExplicitSearchActive = false;
            resetAzGestureState(false, true);
            resetAzOverflowAffordanceState();
            return;
        }
        if (iconPreferencesChanged) {
            mSuggestionBarView.refreshAllApps(null);
        } else {
            mSuggestionBarView.reloadAllApps();
        }
        String input = "";
        if (mTerminalView != null && mSuggestionBarExplicitSearchActive) {
            input = normalizeSuggestionBarInput(mTerminalView.getCurrentInput());
        }
        mSuggestionBarView.reloadWithInput(input, mTerminalView);
        syncAzScrubLettersAndTint();
    }

    private int computeLauncherIconPreferencesSignature() {
        if (mPreferences == null) return 0;
        int signature = 17;
        signature = (31 * signature) + stringSignature(mPreferences.getAppLauncherIconPackPackage());
        signature = (31 * signature) + stringSignature(mPreferences.getAppLauncherPinnedIconPackPackage());
        signature = (31 * signature) + (mPreferences.isAppLauncherBwIconsEnabled() ? 1 : 0);
        return signature;
    }

    /**
     * Reconciles icon-pack changes even when the styling broadcast was sent while this activity
     * was stopped. The settings activity writes preferences directly, so the persisted signature
     * is the durable source of truth; the broadcast is only a fast path.
     */
    private void refreshLauncherIconsIfPreferencesChanged() {
        if (mSuggestionBarView == null || mPreferences == null) {
            return;
        }
        int iconPreferencesSignature = computeLauncherIconPreferencesSignature();
        if (mLastLauncherIconPreferencesSignature == iconPreferencesSignature) {
            return;
        }
        applySuggestionBarPreferences();
    }

    private static int stringSignature(@Nullable String value) {
        return value == null ? 0 : value.hashCode();
    }

    private void onSuggestionBarOverflowInteractionChanged(boolean interacting) {
        mSuggestionBarInteractionActive = interacting;
        updateAzOverflowAffordance();
    }

    private void syncAzScrubLettersAndTint() {
        if (!isAzRowEnabled() || mAzScrubRowView == null || mSuggestionBarView == null) return;
        Set<Character> letters = new LinkedHashSet<>(mSuggestionBarView.getAvailableAzLetters());
        mAzScrubRowView.setVisibleLetters(letters);
        int base = resolveAzGestureAccentColor();
        int muted = mutedMaterialShade(base);
        if (mAzScrubRowView.getCurrentTextColor() != muted) {
            mAzScrubRowView.setTextColor(muted);
        }
        mAzScrubRowView.setInteractionAccentColor(base);
        mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
        mAzScrubRowView.setLockedInlineLetter(null);
        int orbColor = brightMaterialShade(base);
        int edgeColor = edgeMaterialVariant(base);
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setColors(orbColor, edgeColor);
            mLauncherAzGestureFxUnderlayView.setDarkThemeActive(isNightThemeActive());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mLauncherAzGestureFxUnderlayView.setElevation(0f);
                mLauncherAzGestureFxUnderlayView.setTranslationZ(-dpToPx(8));
            }
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setColors(orbColor, edgeColor);
            mLauncherAzGestureFxOverlayView.setDarkThemeActive(isNightThemeActive());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mLauncherAzGestureFxOverlayView.setElevation(dpToPx(30));
                mLauncherAzGestureFxOverlayView.setTranslationZ(dpToPx(30));
            }
        }
        if (mLauncherAzGestureFxLabelOverlayView != null) {
            mLauncherAzGestureFxLabelOverlayView.setColors(orbColor, edgeColor);
            mLauncherAzGestureFxLabelOverlayView.setDarkThemeActive(isNightThemeActive());
            mLauncherAzGestureFxLabelOverlayView.setFocusedAppPreviewLabelEnabled(
                mPreferences != null && mPreferences.isAppLauncherDisplayAppNamesEnabled()
            );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mLauncherAzGestureFxLabelOverlayView.setElevation(dpToPx(40));
                mLauncherAzGestureFxLabelOverlayView.setTranslationZ(dpToPx(40));
            }
        }
        updateAzOverflowAffordance();
        mAzScrubRowView.setBackgroundColor(Color.TRANSPARENT);
        mAzScrubRowView.bringToFront();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAzScrubRowView.setElevation(dpToPx(24));
            mAzScrubRowView.setTranslationZ(dpToPx(24));
        }
        if (mAzScrubRowView.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) mAzScrubRowView.getParent();
            parent.setClipChildren(false);
            parent.setClipToPadding(false);
            if (parent.getParent() instanceof ViewGroup) {
                ViewGroup grandParent = (ViewGroup) parent.getParent();
                grandParent.setClipChildren(false);
                grandParent.setClipToPadding(false);
            }
        }
    }

    private void handleAzGestureScrub(
        char letter,
        int selectionIndex,
        float touchX,
        float touchY,
        float rawX,
        float rawY,
        long eventTimeMs,
        @NonNull AzScrubRowView.GesturePhase phase
    ) {
        if (!isAzRowEnabled() || mSuggestionBarView == null || mAzScrubRowView == null) {
            return;
        }

        if (phase == AzScrubRowView.GesturePhase.DOWN) {
            mAzGestureMode = AzGestureMode.AZ_TRACKING;
            mAzHasLockedSelection = false;
            mAzHasPreviewAnchor = false;
            mAzRecentMotionDx = 0f;
            mAzRecentMotionDy = 0f;
            mAzLastMotionEventTimeMs = eventTimeMs;
            mAzUpwardTravelRefY = touchY;
            mAzLastScrubTouchX = touchX;
            mAzLastScrubTouchY = touchY;
            mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
            mAzScrubRowView.setLockedInlineLetter(null);
        } else {
            // Smooth pointer velocity by elapsed event time: intent classification below reads its
            // direction, so behavior stays consistent across touch-controller sampling rates.
            long dtMs = Math.max(1L, eventTimeMs - mAzLastMotionEventTimeMs);
            float eventVelocityX = (touchX - mAzLastScrubTouchX) / dtMs;
            float eventVelocityY = (touchY - mAzLastScrubTouchY) / dtMs;
            float alpha = (float) (1d - Math.exp(-dtMs / AZ_RECENT_MOTION_TAU_MS));
            mAzRecentMotionDx += (eventVelocityX - mAzRecentMotionDx) * alpha;
            mAzRecentMotionDy += (eventVelocityY - mAzRecentMotionDy) * alpha;
            mAzLastMotionEventTimeMs = eventTimeMs;
            mAzLastScrubTouchX = touchX;
            mAzLastScrubTouchY = touchY;
            // While still letter-scrubbing horizontally, keep re-anchoring the upward-travel
            // reference so the climb is measured from where the finger actually turned upward.
            if (mAzGestureMode == AzGestureMode.AZ_TRACKING
                && Math.abs(mAzRecentMotionDx) > Math.abs(mAzRecentMotionDy) * 1.3f) {
                mAzUpwardTravelRefY = touchY;
            }
        }

        mAzLastRawX = rawX;
        mAzLastRawY = rawY;
        mAzScrubRowView.getLocationOnScreen(mAzViewLocation);
        mAzLastAnchorRawX = mAzViewLocation[0] + touchX;
        mAzLastAnchorRawY = mAzViewLocation[1] + (mAzScrubRowView.getHeight() * 0.5f);
        populateRawBounds(mAzScrubRowView, mAzRowRawBounds);
        populateRawBounds(mSuggestionBarView, mAppsRowRawBounds);
        populateRawBounds(mAzTerminalToolbarView, mExtraKeysRawBounds);

        if (letter == AzScrubRowView.PINNED_APPS_SYMBOL) {
            mSuggestionBarView.clearAzFocusedEntry();
            mSuggestionBarView.clearAzPreview();
            resetAzGestureState(false, false);
            updateAzOverflowAffordance();
            return;
        }

        mAzGestureActive = true;
        cancelAzOverflowRefresh();
        float rowHeight = Math.max(1f, mAzScrubRowView.getHeight());
        View extraKeysRow = mAzTerminalToolbarView;
        float extraKeysHeight = (extraKeysRow != null && extraKeysRow.getHeight() > 0)
            ? extraKeysRow.getHeight()
            : (rowHeight * 1.2f);
        float filterUpperBound = -(rowHeight * 0.10f);
        float filterLowerBound = rowHeight + extraKeysHeight + (rowHeight * 0.25f);
        float unlockThreshold = rowHeight * AZ_RETURN_TOUCH_Y_RATIO;
        float unlockMaxBound = filterLowerBound + (rowHeight * 0.18f);
        float minUpwardTravel = Math.max(getResources().getDisplayMetrics().density * 10f, rowHeight * 0.22f);
        // Intent from the smoothed recent motion vector, travel from the rolling upward reference:
        // a diagonal thumb arc out of a horizontal scrub locks upward without a vertical climb.
        boolean recentUpwardDominant = -mAzRecentMotionDy
            >= Math.abs(mAzRecentMotionDx) * AZ_UPWARD_DIRECTION_RATIO;
        boolean upwardIntent = touchY <= (rowHeight * AZ_UPWARD_LOCK_TOUCH_Y_RATIO)
            && (mAzUpwardTravelRefY - touchY) >= minUpwardTravel
            && recentUpwardDominant;
        // Once the drag starts on the AZ row, keep horizontal letter filtering captured below it.
        // This matches the visual wave tracking and avoids requiring exact vertical placement.
        boolean withinAzFilterBand = touchY >= filterUpperBound;
        boolean enteringUpwardLock = upwardIntent;
        boolean enteringIconTrack = isInAppsRowCorridor(rawY) || isInAzCaptureWedge(rawX, rawY);
        // Locked states are sticky: releasing them needs deliberate downward motion, not mere
        // position drift near the row boundary while the thumb wanders sideways.
        boolean recentDownwardDominant = mAzRecentMotionDy > 0f
            && mAzRecentMotionDy >= Math.abs(mAzRecentMotionDx) * AZ_RETURN_DIRECTION_RATIO;
        boolean returningToUpwardTrack = recentDownwardDominant
            && touchY >= unlockThreshold && touchY <= unlockMaxBound;
        boolean returningToIconTrack = recentDownwardDominant
            && !isInAppsRowCorridor(rawY) && !isInAzCaptureWedge(rawX, rawY) && isInAzReturnBand(rawY);

        if (mAzGestureMode == AzGestureMode.AZ_TRACKING) {
            if (enteringIconTrack && mAzHasPreviewAnchor && phase != AzScrubRowView.GesturePhase.UP) {
                lockAzGestureAnchor(letter, selectionIndex, AzGestureMode.ICON_TRACKING_LOCKED);
            } else if (enteringUpwardLock) {
                lockAzGestureAnchor(letter, selectionIndex, AzGestureMode.UPWARD_LOCKED);
            } else if (withinAzFilterBand || phase == AzScrubRowView.GesturePhase.DOWN) {
                persistAzPreviewAnchor(letter, selectionIndex);
            }
        } else if (mAzGestureMode == AzGestureMode.UPWARD_LOCKED && mAzHasLockedSelection) {
            if (returningToUpwardTrack && phase != AzScrubRowView.GesturePhase.UP) {
                mAzGestureMode = AzGestureMode.AZ_TRACKING;
                mAzHasLockedSelection = false;
                mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
                mAzScrubRowView.setLockedInlineLetter(null);
                mSuggestionBarView.clearAzFocusedEntry();
                if (withinAzFilterBand) {
                    persistAzPreviewAnchor(letter, selectionIndex);
                } else {
                    persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
                }
            } else {
                if (mAzGestureMode == AzGestureMode.UPWARD_LOCKED && enteringIconTrack) {
                    mAzGestureMode = AzGestureMode.ICON_TRACKING_LOCKED;
                }
                persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
                mAzScrubRowView.setLockedInlineLetter(Character.toUpperCase(mAzLockedLetter));
            }
        } else if (mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED && mAzHasLockedSelection) {
            if (returningToIconTrack && phase != AzScrubRowView.GesturePhase.UP) {
                mAzGestureMode = AzGestureMode.AZ_TRACKING;
                mAzHasLockedSelection = false;
                mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
                mAzScrubRowView.setLockedInlineLetter(null);
                mSuggestionBarView.clearAzFocusedEntry();
                persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
            } else {
                persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
                mAzScrubRowView.setLockedInlineLetter(Character.toUpperCase(mAzLockedLetter));
            }
        }
        updateAzOverflowAffordance();

        SuggestionBarView.AzDragFocusResult focusResult = null;
        if (mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED) {
            focusResult = mSuggestionBarView.resolveAzDragFocus(rawX, rawY);
            mAzCurrentFocusResult = focusResult;
        } else {
            mAzCurrentFocusResult = null;
        }

        char overlayLetter = (mAzGestureMode == AzGestureMode.UPWARD_LOCKED || mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED) && mAzHasLockedSelection
            ? mAzLockedLetter
            : letter;
        updateAzOverlayState(focusResult, overlayLetter);
        updateAzEdgePagingLoop(focusResult);

        if (phase == AzScrubRowView.GesturePhase.UP) {
            boolean launched = false;
            if (mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED && focusResult != null && focusResult.hasFocusEntry()) {
                if (mLauncherAzGestureFxLabelOverlayView != null) {
                    mLauncherAzGestureFxLabelOverlayView.dismissFocusedAppPreviewForLaunch();
                }
                launched = mSuggestionBarView.launchAzFocusedEntry(focusResult);
            }
            resetAzGestureState(!launched, false);
            updateAzOverflowAffordance();
            if (!launched) {
                scheduleAzOverflowRefresh();
            }
        }
    }

    private void persistAzPreviewAnchor(char letter, int selectionIndex) {
        if (mSuggestionBarView == null) return;
        mSuggestionBarView.persistAzPreview(letter, selectionIndex);
        mAzPreviewAnchorLetter = letter;
        mAzPreviewAnchorSelectionIndex = selectionIndex;
        mAzHasPreviewAnchor = true;
    }

    private void lockAzGestureAnchor(char fallbackLetter, int fallbackSelectionIndex, @NonNull AzGestureMode targetMode) {
        if (mAzHasPreviewAnchor) {
            mAzLockedLetter = mAzPreviewAnchorLetter;
            mAzLockedSelectionIndex = mAzPreviewAnchorSelectionIndex;
        } else {
            mAzLockedLetter = fallbackLetter;
            mAzLockedSelectionIndex = fallbackSelectionIndex;
        }
        persistAzPreviewAnchor(mAzLockedLetter, mAzLockedSelectionIndex);
        mAzGestureMode = targetMode;
        mAzHasLockedSelection = true;
        mAzLockedAnchorRawX = mAzLastAnchorRawX;
        mAzLockedAnchorRawY = mAzLastAnchorRawY;
        mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.INLINE_EMPHASIS_TRACK);
        mAzScrubRowView.setLockedInlineLetter(Character.toUpperCase(mAzLockedLetter));
    }

    private boolean isInAppsRowCorridor(float rawY) {
        if (mSuggestionBarView == null) {
            return false;
        }
        if (mAppsRowRawBounds.isEmpty()) {
            return false;
        }
        float topTolerance = dpToPx(2);
        float bottomTolerance = dpToPx(4);
        return rawY >= (mAppsRowRawBounds.top - topTolerance) && rawY <= (mAppsRowRawBounds.bottom + bottomTolerance);
    }

    private boolean isInAzCaptureWedge(float rawX, float rawY) {
        if (!mAzHasLockedSelection || mAppsRowRawBounds.isEmpty()) {
            return false;
        }
        float startY = mAzLockedAnchorRawY - dpToPx(4);
        float topLimit = mAppsRowRawBounds.top - dpToPx(2);
        float bottomLimit = mAppsRowRawBounds.bottom + dpToPx(4);
        if (rawY > startY || rawY < topLimit || rawY > bottomLimit) {
            return false;
        }
        float wedgeTravel = Math.max(dpToPx(24), startY - topLimit);
        float progress = Math.max(0f, Math.min(1f, (startY - rawY) / wedgeTravel));
        // Wide enough at the base for a natural thumb arc (~±45° cone) instead of demanding a
        // straight vertical rise out of the locked letter.
        float targetHalfWidth = Math.max(dpToPx(40), mAppsRowRawBounds.width() * 0.18f);
        float halfWidth = dpToPx(22) + (targetHalfWidth * progress);
        return Math.abs(rawX - mAzLockedAnchorRawX) <= halfWidth;
    }

    private boolean isInAzReturnBand(float rawY) {
        if (mAzRowRawBounds.isEmpty()) {
            return false;
        }
        float top = mAzRowRawBounds.top - dpToPx(10);
        float bottom = mAzRowRawBounds.bottom + dpToPx(12);
        if (!mExtraKeysRawBounds.isEmpty()) {
            bottom = Math.max(bottom, mExtraKeysRawBounds.bottom + dpToPx(10));
        }
        return rawY >= top && rawY <= bottom;
    }

    private void updateAzOverlayState(@Nullable SuggestionBarView.AzDragFocusResult focusResult, char activeLetter) {
        if (!isAzRowEnabled()) {
            return;
        }
        if (mLauncherAzGestureFxUnderlayView == null && mLauncherAzGestureFxOverlayView == null) {
            return;
        }
        populateRawBounds(mAzScrubRowView, mAzRowRawBounds);
        populateRawBounds(mSuggestionBarView, mAppsRowRawBounds);
        populateRawBounds(mAzTerminalToolbarView, mExtraKeysRawBounds);
        applyAzFxRowBounds();
        LauncherAzGestureFxView.InteractionMode interactionMode =
            mAzGestureMode == AzGestureMode.ICON_TRACKING_LOCKED
                ? LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED
                : LauncherAzGestureFxView.InteractionMode.LETTER_TRACK;
        if (mSuggestionBarView != null) {
            if (interactionMode == LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED) {
                mSuggestionBarView.updateAzFocusedEntry(focusResult);
            } else {
                mSuggestionBarView.clearAzFocusedEntry();
            }
        }
        RectF focusBounds;
        if (interactionMode == LauncherAzGestureFxView.InteractionMode.LETTER_TRACK && mAzScrubRowView != null) {
            mAzLetterVisualMetrics.clear();
            boolean hasMetrics = mAzScrubRowView.getLetterVisualMetricsOnScreen(Character.toUpperCase(activeLetter), mAzLetterVisualMetrics);
            if (hasMetrics) {
                mAzFocusLetterRawBounds.set(mAzLetterVisualMetrics.glassBoundsRaw);
                focusBounds = mAzFocusLetterRawBounds;
            } else {
                mAzFocusLetterRawBounds.setEmpty();
                focusBounds = null;
            }
        } else {
            focusBounds = focusResult == null ? null : focusResult.iconBounds;
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setFocusedIconOutline(
                focusResult == null ? null : focusResult.iconOutlineVisual,
                focusResult == null ? null : focusResult.iconOutlineBounds
            );
        }
        boolean overflowActive = mSuggestionBarView != null && mSuggestionBarView.hasAzOverflowPages();
        boolean canLeft = mSuggestionBarView != null && mSuggestionBarView.canAzPageLeft();
        boolean canRight = mSuggestionBarView != null && mSuggestionBarView.canAzPageRight();
        float currentPagePosition = mSuggestionBarView != null ? mSuggestionBarView.getAzVisualPagePosition() : 0f;
        int pageCount = mSuggestionBarView != null ? mSuggestionBarView.getAzVisiblePageCount() : 1;
        applyAzFxInteractionOverflowState(overflowActive, canLeft, canRight, currentPagePosition, pageCount, overflowActive, true, -1);

        applyAzFxDrag(
            mAzGestureActive,
            mAzLastRawX,
            focusBounds,
            interactionMode
        );
        Drawable focusedIcon = interactionMode == LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED
            && focusResult != null
            && focusResult.entry != null
            ? focusResult.entry.icon
            : null;
        if (focusedIcon == null && interactionMode == LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED
            && focusResult != null && focusResult.entry != null) {
            focusedIcon = getPackageManager().getDefaultActivityIcon();
        }
        String focusedLabel = interactionMode == LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED
            && focusResult != null
            && focusResult.entry != null
            ? focusResult.entry.label
            : null;
        applyAzFxFocusedAppIcon(focusedIcon, focusedLabel);
    }

    private void populateRawBounds(@Nullable View view, @NonNull RectF out) {
        if (view == null || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) {
            out.setEmpty();
            return;
        }
        view.getLocationOnScreen(mAzViewLocation);
        out.set(mAzViewLocation[0], mAzViewLocation[1],
            mAzViewLocation[0] + view.getWidth(), mAzViewLocation[1] + view.getHeight());
    }

    private void updateAzOverflowAffordance() {
        if (!isLauncherCatalogEnabled()) {
            resetAzOverflowAffordanceState();
            return;
        }
        if ((mLauncherAzGestureFxUnderlayView == null && mLauncherAzGestureFxOverlayView == null) || mSuggestionBarView == null) {
            return;
        }
        populateRawBounds(mAzScrubRowView, mAzRowRawBounds);
        populateRawBounds(mSuggestionBarView, mAppsRowRawBounds);
        populateRawBounds(mAzTerminalToolbarView, mExtraKeysRawBounds);
        applyAzFxRowBounds();
        boolean azOverflowActive = mSuggestionBarView.hasAzOverflowPages();
        boolean interactionActive = mSuggestionBarInteractionActive;
        boolean canLeft = false;
        boolean canRight = false;
        float currentPagePosition = 0f;
        int pageCount = 1;
        boolean showPageIndicators = false;
        boolean subtlePageIndicators = false;
        int dynamicPageIndex = -1;
        if (azOverflowActive) {
            canLeft = mSuggestionBarView.canAzPageLeft();
            canRight = mSuggestionBarView.canAzPageRight();
            currentPagePosition = mSuggestionBarView.getAzVisualPagePosition();
            pageCount = mSuggestionBarView.getAzVisiblePageCount();
            showPageIndicators = true;
            interactionActive = true;
            subtlePageIndicators = true;
        } else if (mSuggestionBarInteractionActive && mSuggestionBarView.hasPinnedOverflowPages()) {
            canLeft = mSuggestionBarView.canPinnedPageLeft();
            canRight = mSuggestionBarView.canPinnedPageRight();
            currentPagePosition = mSuggestionBarView.getPinnedVisualPagePosition();
            pageCount = mSuggestionBarView.getPinnedVisiblePageCount();
            showPageIndicators = true;
            subtlePageIndicators = true;
            dynamicPageIndex = mSuggestionBarView.getPinnedDynamicPageIndex();
        } else if (!mAzGestureActive && !mSuggestionBarInteractionActive && mSuggestionBarView.hasPinnedOverflowPages()) {
            canLeft = mSuggestionBarView.canPinnedPageLeft();
            canRight = mSuggestionBarView.canPinnedPageRight();
            currentPagePosition = mSuggestionBarView.getPinnedVisualPagePosition();
            pageCount = mSuggestionBarView.getPinnedVisiblePageCount();
            showPageIndicators = true;
            interactionActive = true;
            subtlePageIndicators = true;
            dynamicPageIndex = mSuggestionBarView.getPinnedDynamicPageIndex();
        }
        applyAzFxInteractionOverflowState(
            interactionActive,
            canLeft,
            canRight,
            currentPagePosition,
            pageCount,
            showPageIndicators,
            subtlePageIndicators,
            dynamicPageIndex
        );
    }

    private void applyAzFxRowBounds() {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setRowBounds(mAppsRowRawBounds);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setRowBounds(mAppsRowRawBounds);
        }
        if (mLauncherAzGestureFxLabelOverlayView != null) {
            mLauncherAzGestureFxLabelOverlayView.setRowBounds(mAppsRowRawBounds);
        }
    }

    private void applyAzFxInteractionOverflowState(
        boolean active,
        boolean canLeft,
        boolean canRight,
        float currentPagePosition,
        int pageCount,
        boolean showPageIndicators,
        boolean subtlePinnedIndicators,
        int dynamicPageIndex
    ) {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setInteractionOverflowState(
                active, canLeft, canRight, currentPagePosition, pageCount, showPageIndicators, subtlePinnedIndicators, dynamicPageIndex
            );
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setInteractionOverflowState(
                active, canLeft, canRight, currentPagePosition, pageCount, showPageIndicators, subtlePinnedIndicators, dynamicPageIndex
            );
        }
    }

    private void resetAzOverflowAffordanceState() {
        mSuggestionBarInteractionActive = false;
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.clearDrag(false);
            mLauncherAzGestureFxUnderlayView.setVisibility(View.GONE);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.clearDrag(false);
            mLauncherAzGestureFxOverlayView.setVisibility(View.GONE);
        }
        if (mLauncherAzGestureFxLabelOverlayView != null) {
            mLauncherAzGestureFxLabelOverlayView.clearDrag(false);
            mLauncherAzGestureFxLabelOverlayView.setVisibility(View.GONE);
        }
    }

    private void applyAzFxDrag(boolean active, float rawX, @Nullable RectF focusedBoundsRaw,
                               @NonNull LauncherAzGestureFxView.InteractionMode mode) {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.updateDrag(active, rawX, focusedBoundsRaw, mode);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.updateDrag(active, rawX, focusedBoundsRaw, mode);
        }
        if (mLauncherAzGestureFxLabelOverlayView != null) {
            mLauncherAzGestureFxLabelOverlayView.updateDrag(active, rawX, focusedBoundsRaw, mode);
        }
    }

    private void applyAzFxFocusedAppIcon(@Nullable Drawable icon, @Nullable String label) {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setFocusedAppPreviewIcon(null);
            mLauncherAzGestureFxUnderlayView.setFocusedAppPreviewLabel(null);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setFocusedAppPreviewIcon(null);
            mLauncherAzGestureFxOverlayView.setFocusedAppPreviewLabel(null);
        }
        if (mLauncherAzGestureFxLabelOverlayView != null) {
            mLauncherAzGestureFxLabelOverlayView.setFocusedAppPreviewLabel(label);
            mLauncherAzGestureFxLabelOverlayView.setFocusedAppPreviewIcon(icon);
        }
    }

    private void updateAzEdgePagingLoop(@Nullable SuggestionBarView.AzDragFocusResult focusResult) {
        if (!isAzRowEnabled() || !mAzGestureActive || mAzGestureMode != AzGestureMode.ICON_TRACKING_LOCKED || focusResult == null) {
            stopAzEdgePagingLoop();
            return;
        }
        if (focusResult.edge != SuggestionBarView.AZ_EDGE_LEFT && focusResult.edge != SuggestionBarView.AZ_EDGE_RIGHT) {
            mAzEdgeRequiresReentry = false;
            stopAzEdgePagingLoop();
            return;
        }
        if (mSuggestionBarView == null) {
            stopAzEdgePagingLoop();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (mAzEdgeRequiresReentry || now < mAzEdgePageCooldownUntilUptimeMs) {
            applyAzFxEdgeDwellProgress(0f, mAzLastRawX, mAzLastRawY);
            return;
        }
        if (mAzEdgePagingFrameCallback != null && mAzEdgePagingEdge == focusResult.edge) {
            updateAzEdgeDwellProgress(now);
            return;
        }
        stopAzEdgePagingLoop();
        mAzEdgePagingEdge = focusResult.edge;
        mAzEdgeDwellStartUptimeMs = now;
        updateAzEdgeDwellProgress(now);
        mAzEdgePagingFrameCallback = frameTimeNanos -> {
                if (!mAzGestureActive || mSuggestionBarView == null) {
                    stopAzEdgePagingLoop();
                    return;
                }
                SuggestionBarView.AzDragFocusResult fresh = mSuggestionBarView.resolveAzDragFocus(mAzLastRawX, mAzLastRawY);
                if (fresh.edge != mAzEdgePagingEdge) {
                    mAzCurrentFocusResult = fresh;
                    updateAzOverlayState(fresh, mAzLockedLetter);
                    updateAzEdgePagingLoop(fresh);
                    return;
                }
                long frameNow = SystemClock.uptimeMillis();
                if (frameNow < mAzEdgePageCooldownUntilUptimeMs || mAzEdgeRequiresReentry) {
                    applyAzFxEdgeDwellProgress(0f, mAzLastRawX, mAzLastRawY);
                    postNextAzEdgePagingFrame();
                    return;
                }
                long dwellMs = frameNow - mAzEdgeDwellStartUptimeMs;
                updateAzEdgeDwellProgress(frameNow);
                if (dwellMs < AZ_EDGE_PAGE_INITIAL_DELAY_MS) {
                    postNextAzEdgePagingFrame();
                    return;
                }
                int pageDelta = mAzEdgePagingEdge == SuggestionBarView.AZ_EDGE_LEFT ? -1 : 1;
                boolean changed = mSuggestionBarView.requestAzPageDelta(pageDelta, 640f);
                if (changed) {
                    if (mLauncherAzGestureFxLabelOverlayView != null) {
                        mLauncherAzGestureFxLabelOverlayView.playFocusedAppPreviewSettle();
                    }
                    updateAzOverflowAffordance();
                }
                mAzEdgePageCooldownUntilUptimeMs = frameNow + AZ_EDGE_PAGE_COOLDOWN_MS;
                mAzEdgeRequiresReentry = true;
                mAzEdgePagingFrameCallback = null;
                applyAzFxEdgeDwellProgress(0f, mAzLastRawX, mAzLastRawY);
                mAzGestureHandler.postDelayed(() -> {
                    if (!mAzGestureActive || mSuggestionBarView == null) return;
                    SuggestionBarView.AzDragFocusResult afterSwitch = mSuggestionBarView.resolveAzDragFocus(mAzLastRawX, mAzLastRawY);
                    mAzCurrentFocusResult = afterSwitch;
                    updateAzOverlayState(afterSwitch, mAzLockedLetter);
                    updateAzEdgePagingLoop(afterSwitch);
                }, AZ_EDGE_PAGE_REPEAT_INTERVAL_MS);
        };
        postNextAzEdgePagingFrame();
    }

    private void postNextAzEdgePagingFrame() {
        if (mAzEdgePagingFrameCallback != null) {
            Choreographer.getInstance().postFrameCallback(mAzEdgePagingFrameCallback);
        }
    }

    private void stopAzEdgePagingLoop() {
        if (mAzEdgePagingFrameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(mAzEdgePagingFrameCallback);
            mAzEdgePagingFrameCallback = null;
        }
        mAzEdgePagingEdge = SuggestionBarView.AZ_EDGE_NONE;
        mAzEdgeDwellStartUptimeMs = 0L;
        mAzEdgeRequiresReentry = false;
        applyAzFxEdgeDwellProgress(0f, mAzLastRawX, mAzLastRawY);
    }

    private void updateAzEdgeDwellProgress(long now) {
        if (mAzEdgeDwellStartUptimeMs <= 0L) {
            applyAzFxEdgeDwellProgress(0f, mAzLastRawX, mAzLastRawY);
            return;
        }
        float progress = Math.min(1f, (now - mAzEdgeDwellStartUptimeMs) / (float) AZ_EDGE_PAGE_INITIAL_DELAY_MS);
        applyAzFxEdgeDwellProgress(progress, mAzLastRawX, mAzLastRawY);
    }

    private void applyAzFxEdgeDwellProgress(float progress, float rawX, float rawY) {
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.setEdgeDwellProgress(0f, rawX, rawY);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.setEdgeDwellProgress(progress, rawX, rawY);
        }
    }

    private void scheduleAzOverflowRefresh() {
        if (!isAzRowEnabled()) {
            return;
        }
        cancelAzOverflowRefresh();
        mAzOverflowRefreshRunnable = this::updateAzOverflowAffordance;
        mAzGestureHandler.postDelayed(mAzOverflowRefreshRunnable, AZ_PREVIEW_TIMEOUT_REFRESH_MS);
    }

    private void cancelAzOverflowRefresh() {
        if (mAzOverflowRefreshRunnable != null) {
            mAzGestureHandler.removeCallbacks(mAzOverflowRefreshRunnable);
            mAzOverflowRefreshRunnable = null;
        }
    }

    private void resetAzGestureState(boolean keepOverflowAffordance, boolean clearPreview) {
        stopAzEdgePagingLoop();
        cancelAzOverflowRefresh();
        mAzGestureActive = false;
        mAzGestureMode = AzGestureMode.IDLE;
        mAzLockedLetter = '#';
        mAzLockedSelectionIndex = 0;
        mAzHasLockedSelection = false;
        mAzHasPreviewAnchor = false;
        mAzCurrentFocusResult = null;
        if (mAzScrubRowView != null) {
            mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
            mAzScrubRowView.setLockedInlineLetter(null);
        }
        if (mSuggestionBarView != null) {
            mSuggestionBarView.clearAzFocusedEntry();
        }
        if (mLauncherAzGestureFxUnderlayView != null) {
            mLauncherAzGestureFxUnderlayView.clearDrag(keepOverflowAffordance);
        }
        if (mLauncherAzGestureFxOverlayView != null) {
            mLauncherAzGestureFxOverlayView.clearDrag(keepOverflowAffordance);
        }
        if (mLauncherAzGestureFxLabelOverlayView != null) {
            mLauncherAzGestureFxLabelOverlayView.clearDrag(false);
        }
        if (clearPreview && mSuggestionBarView != null) {
            mSuggestionBarView.clearAzPreview();
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float pxToDp(float px) {
        return px / getResources().getDisplayMetrics().density;
    }

    private int resolveAzGestureAccentColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(this, R.color.termux_primary));
    }

    private int mutedMaterialShade(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * 0.92f));
        hsv[2] = Math.max(0.78f, Math.min(1f, hsv[2] * 0.86f));
        return Color.HSVToColor(0xF4, hsv);
    }

    private int brightMaterialShade(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * 1.28f));
        hsv[2] = Math.max(0f, Math.min(1f, Math.max(hsv[2], 0.90f)));
        return Color.HSVToColor(0xF6, hsv);
    }

    private int edgeMaterialVariant(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + 24f) % 360f;
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * 1.1f));
        hsv[2] = Math.max(0f, Math.min(1f, Math.max(hsv[2], 0.92f)));
        return Color.HSVToColor(0xE0, hsv);
    }

    private void lockScreenFromAzDoubleTap() {
        if (mPreferences == null || !mPreferences.isAppLauncherAzRowEnabled()) {
            return;
        }
        String method = mPreferences.getAppLauncherAzLockMethod();
        if (TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU.equals(method)) {
            lockScreenWithShizuku();
        } else if (TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_ACCESSIBILITY.equals(method)) {
            lockScreenWithAccessibility();
        }
    }

    private void refreshShizukuLockBackendIfNeeded() {
        if (mPreferences == null) {
            return;
        }
        String method = mPreferences.getAppLauncherAzLockMethod();
        if (!TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU.equals(method)) {
            return;
        }
        PrivilegedBackendManager.getInstance().initializeShizukuOnly(this)
            .exceptionally(throwable -> {
                Logger.logWarn(LOG_TAG, "A-Z Shizuku backend refresh failed: " + throwable.getMessage());
                return false;
            });
    }

    private void lockScreenWithShizuku() {
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        manager.initializeShizukuOnly(this)
            .thenAccept(available -> {
                if (!available || !manager.isPrivilegedAvailable()) {
                    manager.requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);
                    return;
                }
                executeShizukuLockCommand(manager);
            })
            .exceptionally(throwable -> {
                Logger.logWarn(LOG_TAG, "A-Z Shizuku lock initialization failed: " + throwable.getMessage());
                return null;
            });
    }

    private void executeShizukuLockCommand(@NonNull PrivilegedBackendManager manager) {
        manager.executeCommand("input keyevent 223")
            .thenAccept(output -> {
                if (isSuccessfulPrivilegedCommandOutput(output)) {
                    return;
                }
                manager.executeCommand("input keyevent 26")
                    .thenAccept(fallback -> {
                        if (!isSuccessfulPrivilegedCommandOutput(fallback)) {
                            Logger.logWarn(LOG_TAG, "A-Z double tap lock failed: " + fallback);
                        }
                    })
                    .exceptionally(throwable -> {
                        Logger.logWarn(LOG_TAG, "A-Z double tap lock fallback failed: " + throwable.getMessage());
                        return null;
                    });
            })
            .exceptionally(throwable -> {
                Logger.logWarn(LOG_TAG, "A-Z double tap lock command failed: " + throwable.getMessage());
                return null;
            });
    }

    private void lockScreenWithAccessibility() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            showEnableAccessibilityLockDialog();
            return;
        }
        if (LauncherLockAccessibilityAccess.isEnabled(this) && LockAccessibilityService.lockScreen()) {
            return;
        }
        showEnableAccessibilityLockDialog();
    }

    private void showEnableAccessibilityLockDialog() {
        runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.termux_app_launcher_accessibility_lock_prompt_title)
            .setMessage(R.string.termux_app_launcher_accessibility_lock_prompt_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.termux_app_launcher_accessibility_lock_prompt_enable, (dialog, which) -> {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.termux_app_launcher_set_home_unavailable, Toast.LENGTH_SHORT).show();
                }
            })
            .show());
    }

    private boolean isSuccessfulPrivilegedCommandOutput(String output) {
        if (output == null) return false;
        String trimmed = output.trim();
        if (trimmed.isEmpty()) return true;
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("error")) return false;
        if (lower.contains("permission required")) return false;
        if (lower.contains("no privileged backend")) return false;
        return true;
    }

    private void applySuggestionBarInputChar() {
        if (mTerminalView == null || mPreferences == null)
            return;
        mTerminalView.setSplitChar(getSuggestionBarSplitChar());
    }

    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);
        mTermuxTerminalViewClient.setSuggestionBarCallback(this);
        // Split panes: the controller owns the TerminalViews (one per pane leaf) and inflates
        // them into terminal_pane_host. mTerminalView / mActivePane are repointed to the focused
        // pane via PaneHost.onActivePaneChanged, so the single-view call sites act on it.
        android.widget.FrameLayout paneHost = findViewById(R.id.terminal_pane_host);
        mPaneController = new com.termux.app.terminal.TerminalPaneController(
            new PaneHost(), paneHost, getLayoutInflater());
        // Bootstrap a sessionless pane so the many single-view call sites (in-app keyboard,
        // font setup) have a non-null active view before the first session/tab is shown.
        mTerminalView = mPaneController.createBootstrapView();
        mActivePane = mTerminalView;
        syncTerminalWallpaperRenderingMode();
        applySuggestionBarInputChar();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void initializeInAppKeyboard(@Nullable Bundle savedInstanceState) {
        if (mInAppKeyboard != null || mPreferences == null
            || !mPreferences.isInAppKeyboardEnabled())
            return;
        mInAppKeyboard = new TermuxInAppKeyboard(new InAppKeyboardActivityHost(), mPreferences);
        mTermuxTerminalViewClient.setInAppKeyboardController(mInAppKeyboard);
        mInAppKeyboard.onCreate(savedInstanceState);
    }

    private void handleInAppKeyboardHeightAdjustIntent(@Nullable Intent intent) {
        if (intent == null
            || !intent.getBooleanExtra(EXTRA_IN_APP_KEYBOARD_HEIGHT_ADJUST, false))
            return;
        intent.removeExtra(EXTRA_IN_APP_KEYBOARD_HEIGHT_ADJUST);
        if (mPreferences != null && !mPreferences.isInAppKeyboardEnabled())
            mPreferences.setInAppKeyboardEnabled(true);
        initializeInAppKeyboard(null);
        if (mInAppKeyboard != null)
            mInAppKeyboard.beginHeightAdjustment();
    }

    /**
     * Opens the extra-keys row editor over the live terminal, so the row being edited is the row
     * on screen. Saving rewrites the properties and reloads the toolbar in place.
     */
    public void showExtraKeysRowEditor() {
        com.termux.app.terminal.io.ExtraKeysRowEditor.show(this,
            this::reloadExtraKeysFromProperties);
    }

    private void handleEditExtraKeysIntent(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_EDIT_EXTRA_KEYS, false)) return;
        intent.removeExtra(EXTRA_EDIT_EXTRA_KEYS);
        View root = findViewById(R.id.activity_termux_root_view);
        if (root != null) root.post(this::showExtraKeysRowEditor);
        else showExtraKeysRowEditor();
    }

    private void handleDockTuningIntent(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_DOCK_TUNING, false))
            return;
        String initialSection = intent.getStringExtra(EXTRA_DOCK_TUNING_SECTION);
        intent.removeExtra(EXTRA_DOCK_TUNING);
        intent.removeExtra(EXTRA_DOCK_TUNING_SECTION);
        enterDockTuningMode(initialSection);
    }

    private void enterDockTuningMode() {
        // No section asked for: reopen where the user last was.
        enterDockTuningMode(mPreferences == null
            ? null : mPreferences.getSurfaceTuningLastSection());
    }

    private void enterDockTuningMode(@Nullable String initialSection) {
        if (isFullStatusBarEngaged()) return;
        if (mPreferences == null)
            return;
        View controls = findViewById(R.id.dock_tuning_controls);
        MaterialButtonToggleGroup sectionGroup = findViewById(R.id.surface_tuning_section_group);
        View keyboardColors = findViewById(R.id.surface_tuning_keyboard_colors);
        SeekBar blur = findViewById(R.id.dock_tuning_blur_slider);
        SeekBar opacity = findViewById(R.id.dock_tuning_opacity_slider);
        SeekBar grain = findViewById(R.id.dock_tuning_grain_slider);
        SeekBar dockRadius = findViewById(R.id.dock_tuning_radius_slider);
        SeekBar terminal = findViewById(R.id.dock_tuning_terminal_slider);
        com.google.android.material.materialswitch.MaterialSwitch terminalBorder =
            findViewById(R.id.dock_tuning_terminal_border_switch);
        MaterialButtonToggleGroup terminalContrast =
            findViewById(R.id.dock_tuning_terminal_contrast_group);
        TextView terminalContrastHint = findViewById(R.id.dock_tuning_terminal_contrast_hint);
        SeekBar sessions = findViewById(R.id.dock_tuning_sessions_slider);
        SeekBar size = findViewById(R.id.dock_tuning_size_slider);
        SeekBar icons = findViewById(R.id.dock_tuning_icons_slider);
        SeekBar keyboardHeight = findViewById(R.id.surface_tuning_keyboard_height_slider);
        SeekBar keyboardSpacing = findViewById(R.id.surface_tuning_keyboard_spacing_slider);
        SeekBar keyboardRadius = findViewById(R.id.surface_tuning_keyboard_radius_slider);
        SeekBar keyboardKeyOpacity = findViewById(R.id.surface_tuning_keyboard_key_opacity_slider);
        SeekBar keyboardBgOpacity = findViewById(R.id.surface_tuning_keyboard_bg_opacity_slider);
        SeekBar statusBlur = findViewById(R.id.surface_tuning_status_blur_slider);
        SeekBar statusOpacity = findViewById(R.id.surface_tuning_status_opacity_slider);
        SeekBar statusGrain = findViewById(R.id.surface_tuning_status_grain_slider);
        SeekBar statusRadius = findViewById(R.id.surface_tuning_status_radius_slider);
        TextView blurValue = findViewById(R.id.dock_tuning_blur_value);
        TextView opacityValue = findViewById(R.id.dock_tuning_opacity_value);
        TextView grainValue = findViewById(R.id.dock_tuning_grain_value);
        TextView dockRadiusValue = findViewById(R.id.dock_tuning_radius_value);
        TextView terminalValue = findViewById(R.id.dock_tuning_terminal_value);
        TextView sessionsValue = findViewById(R.id.dock_tuning_sessions_value);
        TextView sizeValue = findViewById(R.id.dock_tuning_size_value);
        TextView iconsValue = findViewById(R.id.dock_tuning_icons_value);
        TextView keyboardHeightValue = findViewById(R.id.surface_tuning_keyboard_height_value);
        TextView keyboardSpacingValue = findViewById(R.id.surface_tuning_keyboard_spacing_value);
        TextView keyboardRadiusValue = findViewById(R.id.surface_tuning_keyboard_radius_value);
        TextView keyboardKeyOpacityValue = findViewById(R.id.surface_tuning_keyboard_key_opacity_value);
        TextView keyboardBgOpacityValue = findViewById(R.id.surface_tuning_keyboard_bg_opacity_value);
        TextView statusBlurValue = findViewById(R.id.surface_tuning_status_blur_value);
        TextView statusOpacityValue = findViewById(R.id.surface_tuning_status_opacity_value);
        TextView statusGrainValue = findViewById(R.id.surface_tuning_status_grain_value);
        TextView statusRadiusValue = findViewById(R.id.surface_tuning_status_radius_value);
        MaterialButtonToggleGroup styleGroup = findViewById(R.id.dock_tuning_style_group);
        View confirm = findViewById(R.id.dock_tuning_confirm);
        View reset = findViewById(R.id.surface_tuning_reset);
        View dismiss = findViewById(R.id.dock_tuning_dismiss);
        if (controls == null || sectionGroup == null || keyboardColors == null
            || blur == null || opacity == null || grain == null || dockRadius == null
            || terminal == null || sessions == null || size == null || icons == null
            || keyboardHeight == null || keyboardSpacing == null || keyboardRadius == null
            || keyboardKeyOpacity == null || keyboardKeyOpacityValue == null
            || keyboardBgOpacity == null || keyboardBgOpacityValue == null
            || statusBlur == null || statusOpacity == null || statusGrain == null
            || statusRadius == null
            || blurValue == null || opacityValue == null || grainValue == null
            || dockRadiusValue == null
            || terminalValue == null || sessionsValue == null || sizeValue == null
            || iconsValue == null || keyboardHeightValue == null || keyboardSpacingValue == null
            || keyboardRadiusValue == null || statusBlurValue == null
            || statusOpacityValue == null || statusGrainValue == null
            || statusRadiusValue == null || styleGroup == null || confirm == null
            || reset == null) {
            mDockTuningMode = false;
            return;
        }
        if (!mDockTuningMode) {
            mDockTuningRestoreExpandedStatus = !mPreferences.isTopPaneClockCollapsed();
            if (mDockTuningRestoreExpandedStatus) setTopStatusBarCollapsed(true, false);
        }
        mDockTuningMode = true;
        controls.setVisibility(View.VISIBLE);
        final int initialBlur = mPreferences.getExtraKeysBlurRadius();
        final int initialOpacity = mPreferences.getAppBarOpacity();
        final int initialGrain = mPreferences.getDockGlassGrain();
        final int initialDockRadius = mPreferences.getAppLauncherDockCornerRadius();
        final int initialTerminal = mPreferences.getTerminalBackgroundOpacity();
        final boolean initialTerminalBorder = mPreferences.isTerminalBorderEnabled();
        final String initialTerminalContrast = mPreferences.getTerminalContrastLevel().value;
        final int initialSessions = mPreferences.getSessionsOpacity();
        final float initialBarHeight = mPreferences.getAppLauncherBarHeightScale();
        final int initialSizeIndex = nearestDockSizePresetIndex(initialBarHeight);
        final int initialButtonCount = mPreferences.getAppLauncherButtonCount();
        final String initialStyle = mPreferences.getAppLauncherDockStyle();
        final float initialKeyboardHeight = mPreferences.getInAppKeyboardHeightScale();
        final float initialKeyboardSpacing = mPreferences.getInAppKeyboardKeyMarginScale();
        final float initialKeyboardRadius = mPreferences.getInAppKeyboardKeyCornerRadiusDp();
        // The stored value may be the -1 "theme-defined" sentinel; the slider always shows the
        // effective percent, while dismiss restores the raw stored value.
        final int initialKeyboardKeyOpacity = mPreferences.getInAppKeyboardKeyOpacity();
        final int initialKeyboardKeyOpacityEffective = mInAppKeyboard != null
            ? mInAppKeyboard.getEffectiveKeyOpacityPercent()
            : Math.max(0, initialKeyboardKeyOpacity);
        final int initialKeyboardBgOpacity = mPreferences.getInAppKeyboardBackgroundOpacity();
        final int initialStatusBlur = mPreferences.getStatusBarBlurRadius();
        final int initialStatusOpacity = mPreferences.getStatusBarOpacity();
        final int initialStatusGrain = mPreferences.getStatusBarGrain();
        final int initialStatusRadius = mPreferences.getStatusBarCornerRadius();
        final int initialDockInset = mPreferences.getDockHorizontalInset();
        final int initialKeyboardInset = mPreferences.getInAppKeyboardHorizontalInset();
        final int initialStatusInset = mPreferences.getStatusBarHorizontalInset();

        blur.setProgress(initialBlur);
        opacity.setProgress(initialOpacity);
        grain.setProgress(initialGrain);
        dockRadius.setProgress(editorRadius(initialDockRadius));
        terminal.setProgress(initialTerminal);
        if (terminalBorder != null) {
            terminalBorder.setOnCheckedChangeListener(null);
            terminalBorder.setChecked(mPreferences.isTerminalBorderEnabled());
        }
        syncTerminalContrastGroup(terminalContrast, terminalContrastHint);
        sessions.setProgress(initialSessions);
        size.setProgress(initialSizeIndex);
        icons.setProgress(Math.max(1, Math.min(20, initialButtonCount)));
        keyboardHeight.setProgress(keyboardEditorProgress(initialKeyboardHeight,
            TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE));
        keyboardSpacing.setProgress(keyboardEditorProgress(initialKeyboardSpacing,
            TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
        keyboardRadius.setProgress(Math.round(initialKeyboardRadius * 10f));
        keyboardKeyOpacity.setProgress(initialKeyboardKeyOpacityEffective);
        keyboardBgOpacity.setProgress(initialKeyboardBgOpacity);
        statusBlur.setProgress(initialStatusBlur);
        statusOpacity.setProgress(initialStatusOpacity);
        statusGrain.setProgress(initialStatusGrain);
        statusRadius.setProgress(editorRadius(initialStatusRadius));
        blurValue.setText(getString(R.string.termux_dock_tuning_value_dp, initialBlur));
        opacityValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialOpacity));
        grainValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialGrain));
        dockRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp,
            editorRadius(initialDockRadius)));
        terminalValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialTerminal));
        sessionsValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialSessions));
        sizeValue.setText(dockSizePresetLabel(initialSizeIndex));
        iconsValue.setText(Integer.toString(Math.max(1, initialButtonCount)));
        keyboardHeightValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            keyboardHeight.getProgress()));
        keyboardSpacingValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            keyboardSpacing.getProgress()));
        keyboardRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp,
            Math.round(initialKeyboardRadius)));
        keyboardKeyOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            initialKeyboardKeyOpacityEffective));
        keyboardBgOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            initialKeyboardBgOpacity));
        statusBlurValue.setText(getString(R.string.termux_dock_tuning_value_dp, initialStatusBlur));
        statusOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            initialStatusOpacity));
        statusGrainValue.setText(getString(R.string.termux_dock_tuning_value_percent,
            initialStatusGrain));
        statusRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp,
            editorRadius(initialStatusRadius)));
        sectionGroup.clearOnButtonCheckedListeners();
        styleGroup.clearOnButtonCheckedListeners();
        styleGroup.check(SegmentedPillPreference.VALUE_ROUNDED.equals(initialStyle)
            ? R.id.dock_tuning_style_capsule : R.id.dock_tuning_style_default);
        int initialSectionId = surfaceTuningSectionId(initialSection);
        sectionGroup.check(initialSectionId);
        showSurfaceTuningPanel(initialSectionId);
        mPreferences.setSurfaceTuningLastSection(surfaceTuningSectionKey(initialSectionId));

        sectionGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showSurfaceTuningPanel(checkedId);
                mPreferences.setSurfaceTuningLastSection(surfaceTuningSectionKey(checkedId));
            }
        });

        blur.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                blurValue.setText(getString(R.string.termux_dock_tuning_value_dp, progress));
                if (fromUser) {
                    writeSurfaceBlur(SURFACE_TUNING_TARGET_DOCK, progress);
                    requestDockTuningPreview(TUNING_PREVIEW_BLUR);
                }
            }
        });
        opacity.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacityValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    writeSurfaceOpacity(SURFACE_TUNING_TARGET_DOCK, progress);
                    requestDockTuningPreview(TUNING_PREVIEW_GLASS);
                }
            }
        });
        grain.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                grainValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    writeSurfaceGrain(SURFACE_TUNING_TARGET_DOCK, progress);
                    requestDockTuningPreview(TUNING_PREVIEW_GLASS);
                }
            }
        });
        dockRadius.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                dockRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp, progress));
                if (fromUser) {
                    writeSurfaceCornerRadius(SURFACE_TUNING_TARGET_DOCK, progress);
                    requestDockTuningPreview(TUNING_PREVIEW_GEOMETRY | TUNING_PREVIEW_SURFACES);
                }
            }
        });
        terminal.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                terminalValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    mPreferences.setTerminalBackgroundOpacity(progress);
                    requestDockTuningPreview(TUNING_PREVIEW_SURFACES);
                }
            }
        });
        if (terminalBorder != null) {
            terminalBorder.setOnCheckedChangeListener((button, isChecked) -> {
                mPreferences.setTerminalBorderEnabled(isChecked);
                applyDockTuningStructuralPreview();
            });
        }
        if (terminalContrast != null) {
            terminalContrast.clearOnButtonCheckedListeners();
            terminalContrast.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                String level = terminalContrastLevelForButton(checkedId);
                if (level.equals(mPreferences.getTerminalContrastLevel().value)) return;
                mPreferences.setTerminalContrastLevel(level);
                applyTerminalContrastChange();
            });
        }
        sessions.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sessionsValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    mPreferences.setSessionsOpacity(progress);
                    requestDockTuningPreview(TUNING_PREVIEW_SURFACES);
                }
            }
        });
        size.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int index = Math.max(0, Math.min(DOCK_TUNING_SIZE_PRESETS.length - 1, progress));
                sizeValue.setText(dockSizePresetLabel(index));
                if (fromUser) {
                    mPreferences.setAppLauncherBarHeightScale(DOCK_TUNING_SIZE_PRESETS[index]);
                    requestDockTuningPreview(TUNING_PREVIEW_GEOMETRY);
                }
            }
        });
        icons.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int count = Math.max(1, progress);
                iconsValue.setText(Integer.toString(count));
                if (fromUser) {
                    mPreferences.setAppLauncherButtonCount(count);
                    requestDockTuningPreview(TUNING_PREVIEW_GEOMETRY);
                }
            }
        });
        keyboardColors.setOnClickListener(view -> startActivity(SettingsActivity.createFragmentIntent(
            this, KeyboardColorSchemeFragment.class, R.string.settings_keyboard_colors_title)));
        keyboardHeight.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardHeightValue.setText(getString(R.string.termux_dock_tuning_value_percent,
                    progress));
                if (fromUser && mInAppKeyboard != null)
                    mInAppKeyboard.previewSurfaceEditorHeightScale(keyboardEditorValue(progress,
                        TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                        TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE));
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                mPreferences.setInAppKeyboardHeightScale(keyboardEditorValue(seekBar.getProgress(),
                    TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                    TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE));
            }
        });
        keyboardSpacing.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardSpacingValue.setText(getString(R.string.termux_dock_tuning_value_percent,
                    progress));
                if (fromUser && mInAppKeyboard != null)
                    mInAppKeyboard.previewSurfaceEditorKeyMarginScale(keyboardEditorValue(progress,
                        TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                        TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                mPreferences.setInAppKeyboardKeyMarginScale(keyboardEditorValue(seekBar.getProgress(),
                    TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                    TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
            }
        });
        keyboardRadius.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardRadiusValue.setText(getString(R.string.termux_dock_tuning_value_dp,
                    Math.round(progress / 10f)));
                if (fromUser && mInAppKeyboard != null)
                    mInAppKeyboard.previewSurfaceEditorKeyCornerRadiusDp(progress / 10f);
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                mPreferences.setInAppKeyboardKeyCornerRadiusDp(seekBar.getProgress() / 10f);
            }
        });
        keyboardKeyOpacity.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardKeyOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
                    progress));
                // Scoped preview: repaints only the keyboard view, never the glass pipeline.
                if (fromUser && mInAppKeyboard != null)
                    mInAppKeyboard.previewSurfaceEditorKeyOpacity(progress);
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                mPreferences.setInAppKeyboardKeyOpacity(seekBar.getProgress());
            }
        });
        keyboardBgOpacity.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                keyboardBgOpacityValue.setText(getString(R.string.termux_dock_tuning_value_percent,
                    progress));
                // The render path reads this pref, so write-then-re-render is the live preview.
                // Leaving 100 also flips the keyboard off the unified dock material, which the
                // coalesced glass re-render (backdrop dirty + accessory sync) already handles.
                if (fromUser) {
                    mPreferences.setInAppKeyboardBackgroundOpacity(progress);
                    requestDockTuningPreview(TUNING_PREVIEW_GLASS);
                }
            }
        });
        styleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked)
                return;
            String style = checkedId == R.id.dock_tuning_style_capsule
                ? SegmentedPillPreference.VALUE_ROUNDED : SegmentedPillPreference.VALUE_DEFAULT;
            if (!style.equals(mPreferences.getAppLauncherDockStyle())) {
                mPreferences.setAppLauncherDockStyle(style);
                applyDockTuningStructuralPreview();
            }
        });
        bindStatusSeekBar(statusBlur, statusBlurValue, true,
            TUNING_PREVIEW_BLUR | TUNING_PREVIEW_SURFACES,
            value -> writeSurfaceBlur(SURFACE_TUNING_TARGET_STATUS, value));
        bindStatusSeekBar(statusOpacity, statusOpacityValue, false,
            TUNING_PREVIEW_SURFACES,
            value -> writeSurfaceOpacity(SURFACE_TUNING_TARGET_STATUS, value));
        bindStatusSeekBar(statusGrain, statusGrainValue, false,
            TUNING_PREVIEW_SURFACES,
            value -> writeSurfaceGrain(SURFACE_TUNING_TARGET_STATUS, value));
        // Radius also reshapes the dock capsule when "match all surfaces" is on.
        bindStatusSeekBar(statusRadius, statusRadiusValue, true,
            TUNING_PREVIEW_SURFACES | TUNING_PREVIEW_GEOMETRY,
            value -> writeSurfaceCornerRadius(SURFACE_TUNING_TARGET_STATUS, value));
        bindSurfaceTuningClockPicker();
        bindSurfaceTuningNormalizeSwitch();
        bindSurfaceTuningGestures();
        reset.setOnClickListener(view -> {
            int section = sectionGroup.getCheckedButtonId();
            if (section == R.id.surface_tuning_section_dock) {
                mPreferences.setExtraKeysBlurRadius(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS);
                mPreferences.setAppBarOpacity(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY);
                mPreferences.setDockGlassGrain(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN);
                mPreferences.setAppLauncherDockCornerRadius(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS);
                mPreferences.setAppLauncherBarHeightScale(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT);
                mPreferences.setAppLauncherButtonCount(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_BUTTON_COUNT);
                mPreferences.setAppLauncherDockStyle(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_STYLE);
                mPreferences.setDockHorizontalInset(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET);
            } else if (section == R.id.surface_tuning_section_keyboard) {
                mPreferences.setInAppKeyboardHeightScale(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE);
                mPreferences.setInAppKeyboardKeyMarginScale(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_MARGIN_SCALE);
                mPreferences.setInAppKeyboardKeyCornerRadiusDp(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP);
                mPreferences.setInAppKeyboardKeyOpacity(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY);
                mPreferences.setInAppKeyboardBackgroundOpacity(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BACKGROUND_OPACITY);
                mPreferences.setInAppKeyboardHorizontalInset(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HORIZONTAL_INSET);
                if (mInAppKeyboard != null) {
                    mInAppKeyboard.previewSurfaceEditorHeightScale(
                        TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE);
                    mInAppKeyboard.previewSurfaceEditorKeyOpacity(
                        TermuxPreferenceConstants.TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY);
                }
            } else if (section == R.id.surface_tuning_section_status) {
                mPreferences.setStatusBarBlurRadius(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_BLUR_RADIUS);
                mPreferences.setStatusBarOpacity(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_OPACITY);
                mPreferences.setStatusBarGrain(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_GRAIN);
                mPreferences.setStatusBarCornerRadius(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_CORNER_RADIUS);
                mPreferences.setStatusBarHorizontalInset(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET);
            } else {
                mPreferences.setTerminalBackgroundOpacity(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_TERMINAL_BACKGROUND_OPACITY);
                mPreferences.setSessionsOpacity(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_SESSIONS_OPACITY);
                mPreferences.setTerminalBorderEnabled(
                    TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_TERMINAL_BORDER_ENABLED);
                mPreferences.setTerminalContrastLevel(
                    com.termux.shared.termux.settings.preferences.TerminalContrastLevel
                        .DEFAULT.value);
                applyTerminalContrastChange();
            }
            blur.setProgress(mPreferences.getExtraKeysBlurRadius());
            opacity.setProgress(mPreferences.getAppBarOpacity());
            grain.setProgress(mPreferences.getDockGlassGrain());
            dockRadius.setProgress(editorRadius(mPreferences.getAppLauncherDockCornerRadius()));
            size.setProgress(nearestDockSizePresetIndex(mPreferences.getAppLauncherBarHeightScale()));
            icons.setProgress(mPreferences.getAppLauncherButtonCount());
            styleGroup.check(SegmentedPillPreference.VALUE_ROUNDED.equals(
                mPreferences.getAppLauncherDockStyle())
                ? R.id.dock_tuning_style_capsule : R.id.dock_tuning_style_default);
            keyboardHeight.setProgress(keyboardEditorProgress(
                mPreferences.getInAppKeyboardHeightScale(),
                TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
                TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE));
            keyboardSpacing.setProgress(keyboardEditorProgress(
                mPreferences.getInAppKeyboardKeyMarginScale(),
                TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
                TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE));
            keyboardRadius.setProgress(Math.round(mPreferences.getInAppKeyboardKeyCornerRadiusDp() * 10f));
            keyboardKeyOpacity.setProgress(mInAppKeyboard != null
                ? mInAppKeyboard.getEffectiveKeyOpacityPercent()
                : Math.max(0, mPreferences.getInAppKeyboardKeyOpacity()));
            keyboardBgOpacity.setProgress(mPreferences.getInAppKeyboardBackgroundOpacity());
            statusBlur.setProgress(mPreferences.getStatusBarBlurRadius());
            statusOpacity.setProgress(mPreferences.getStatusBarOpacity());
            statusGrain.setProgress(mPreferences.getStatusBarGrain());
            statusRadius.setProgress(editorRadius(mPreferences.getStatusBarCornerRadius()));
            terminal.setProgress(mPreferences.getTerminalBackgroundOpacity());
            if (terminalBorder != null)
                terminalBorder.setChecked(mPreferences.isTerminalBorderEnabled());
            syncTerminalContrastGroup(terminalContrast, terminalContrastHint);
            sessions.setProgress(mPreferences.getSessionsOpacity());
            syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_DOCK);
            syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_KEYBOARD);
            syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_STATUS);
            applyDockTuningStructuralPreview();
        });
        confirm.setOnClickListener(view -> exitDockTuningMode());
        if (dismiss != null) {
            dismiss.setOnClickListener(view -> {
                // Dismiss reverts to the values captured when tuning began.
                mPreferences.setExtraKeysBlurRadius(initialBlur);
                mPreferences.setAppBarOpacity(initialOpacity);
                mPreferences.setDockGlassGrain(initialGrain);
                mPreferences.setAppLauncherDockCornerRadius(initialDockRadius);
                mPreferences.setTerminalBackgroundOpacity(initialTerminal);
                mPreferences.setTerminalBorderEnabled(initialTerminalBorder);
                if (!initialTerminalContrast.equals(
                        mPreferences.getTerminalContrastLevel().value)) {
                    mPreferences.setTerminalContrastLevel(initialTerminalContrast);
                    applyTerminalContrastChange();
                }
                mPreferences.setSessionsOpacity(initialSessions);
                mPreferences.setAppLauncherBarHeightScale(initialBarHeight);
                mPreferences.setAppLauncherButtonCount(initialButtonCount);
                mPreferences.setAppLauncherDockStyle(initialStyle);
                mPreferences.setInAppKeyboardHeightScale(initialKeyboardHeight);
                mPreferences.setInAppKeyboardKeyMarginScale(initialKeyboardSpacing);
                mPreferences.setInAppKeyboardKeyCornerRadiusDp(initialKeyboardRadius);
                mPreferences.setInAppKeyboardKeyOpacity(initialKeyboardKeyOpacity);
                mPreferences.setInAppKeyboardBackgroundOpacity(initialKeyboardBgOpacity);
                mPreferences.setStatusBarBlurRadius(initialStatusBlur);
                mPreferences.setStatusBarOpacity(initialStatusOpacity);
                mPreferences.setStatusBarGrain(initialStatusGrain);
                mPreferences.setStatusBarCornerRadius(initialStatusRadius);
                mPreferences.setDockHorizontalInset(initialDockInset);
                mPreferences.setInAppKeyboardHorizontalInset(initialKeyboardInset);
                mPreferences.setStatusBarHorizontalInset(initialStatusInset);
                if (mInAppKeyboard != null) {
                    mInAppKeyboard.previewSurfaceEditorHeightScale(initialKeyboardHeight);
                    mInAppKeyboard.previewSurfaceEditorKeyOpacity(initialKeyboardKeyOpacity);
                }
                applyDockTuningStructuralPreview();
                exitDockTuningMode();
            });
        }
        controls.bringToFront();
        setSurfaceTuningGestureOverlayVisible(true);
        registerDockTuningLayoutListener(controls);
        controls.post(this::adjustDockTuningCardHeight);
    }

    private int surfaceTuningSectionId(@Nullable String section) {
        if ("keyboard".equals(section)) return R.id.surface_tuning_section_keyboard;
        if ("status".equals(section)) return R.id.surface_tuning_section_status;
        if ("other".equals(section) || "terminal".equals(section))
            return R.id.surface_tuning_section_other;
        return R.id.surface_tuning_section_dock;
    }

    private String surfaceTuningSectionKey(int sectionId) {
        if (sectionId == R.id.surface_tuning_section_keyboard) return "keyboard";
        if (sectionId == R.id.surface_tuning_section_status) return "status";
        if (sectionId == R.id.surface_tuning_section_other) return "other";
        return "dock";
    }

    private int editorRadius(int value) {
        return value < 0 ? 26 : Math.min(40, value);
    }

    static int keyboardEditorProgress(float value, float minValue, float maxValue) {
        if (Float.isNaN(value) || Float.isInfinite(value) || maxValue <= minValue)
            return 0;
        float normalized = (value - minValue) / (maxValue - minValue);
        return Math.max(0, Math.min(100, Math.round(normalized * 100f)));
    }

    static float keyboardEditorValue(int progress, float minValue, float maxValue) {
        int normalizedProgress = Math.max(0, Math.min(100, progress));
        return minValue + ((maxValue - minValue) * normalizedProgress / 100f);
    }

    private void showSurfaceTuningPanel(int checkedId) {
        // The status section tunes the expanded top pane: show the clock face while it is open so
        // the sliders preview against it, and give the space back when another section takes over.
        if (mDockTuningMode) {
            boolean collapse = checkedId != R.id.surface_tuning_section_status;
            mSurfaceEditorExpandedStatusPane = !collapse && mPreferences != null
                && mPreferences.isTopPaneClockCollapsed();
            setTopStatusBarCollapsed(collapse, true);
        }
        View dock = findViewById(R.id.surface_tuning_dock_panel);
        View dockContinuation = findViewById(R.id.surface_tuning_dock_continuation_panel);
        View keyboard = findViewById(R.id.surface_tuning_keyboard_panel);
        View status = findViewById(R.id.surface_tuning_status_panel);
        View other = findViewById(R.id.surface_tuning_other_panel);
        boolean showDock = checkedId == R.id.surface_tuning_section_dock;
        if (dock != null) dock.setVisibility(showDock ? View.VISIBLE : View.GONE);
        if (dockContinuation != null)
            dockContinuation.setVisibility(showDock ? View.VISIBLE : View.GONE);
        if (keyboard != null) keyboard.setVisibility(
            checkedId == R.id.surface_tuning_section_keyboard ? View.VISIBLE : View.GONE);
        if (status != null) status.setVisibility(
            checkedId == R.id.surface_tuning_section_status ? View.VISIBLE : View.GONE);
        if (other != null) other.setVisibility(
            checkedId == R.id.surface_tuning_section_other ? View.VISIBLE : View.GONE);
        ScrollView scroll = findViewById(R.id.dock_tuning_scroll);
        if (scroll != null) {
            scroll.scrollTo(0, 0);
            scroll.post(this::adjustDockTuningCardHeight);
        }
    }

    private interface StatusValueSetter {
        void set(int value);
    }

    private void bindStatusSeekBar(SeekBar seekBar, TextView valueView, boolean dp,
                                   int previewScopes, StatusValueSetter setter) {
        seekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueView.setText(getString(dp ? R.string.termux_dock_tuning_value_dp
                    : R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    setter.set(progress);
                    requestDockTuningPreview(previewScopes);
                }
            }
        });
    }

    private static final int SURFACE_TUNING_TARGET_DOCK = 0;
    private static final int SURFACE_TUNING_TARGET_KEYBOARD = 1;
    private static final int SURFACE_TUNING_TARGET_STATUS = 2;

    // ---------------------------------------------------- surface glass, one writer per control
    //
    // The dock and the status bar keep separate preferences for the same five glass properties,
    // which is what lets them be tuned apart. Every editor control funnels through the writers
    // below so "match all surfaces" is a single branch per property instead of a rule duplicated
    // at each slider. The in-app keyboard has no blur/opacity/grain/radius of its own — it renders
    // on the dock's glass — so only padding fans out to all three.

    private boolean isSurfaceTuningNormalized() {
        return mPreferences != null && mPreferences.isSurfaceTuningNormalized();
    }

    /**
     * Pushes the stored values back onto both sections' sliders. Only one section is on screen at a
     * time, so without this a normalized write would leave the hidden section showing the number it
     * had before until the editor was reopened. Programmatic {@code setProgress} arrives with
     * {@code fromUser == false}, which every binder here already ignores.
     */
    private void syncSurfaceGlassSliders() {
        if (mPreferences == null)
            return;
        setSeekBarProgress(R.id.dock_tuning_blur_slider, mPreferences.getExtraKeysBlurRadius());
        setSeekBarProgress(R.id.dock_tuning_opacity_slider, mPreferences.getAppBarOpacity());
        setSeekBarProgress(R.id.dock_tuning_grain_slider, mPreferences.getDockGlassGrain());
        setSeekBarProgress(R.id.dock_tuning_radius_slider,
            mPreferences.getAppLauncherDockCornerRadius());
        setSeekBarProgress(R.id.surface_tuning_status_blur_slider,
            mPreferences.getStatusBarBlurRadius());
        setSeekBarProgress(R.id.surface_tuning_status_opacity_slider,
            mPreferences.getStatusBarOpacity());
        setSeekBarProgress(R.id.surface_tuning_status_grain_slider,
            mPreferences.getStatusBarGrain());
        setSeekBarProgress(R.id.surface_tuning_status_radius_slider,
            mPreferences.getStatusBarCornerRadius());
    }

    private void setSeekBarProgress(int sliderId, int progress) {
        SeekBar slider = findViewById(sliderId);
        if (slider != null && slider.getProgress() != progress)
            slider.setProgress(progress);
    }

    private void writeSurfaceBlur(int target, int value) {
        if (mPreferences == null) return;
        if (isSurfaceTuningNormalized() || target == SURFACE_TUNING_TARGET_DOCK)
            mPreferences.setExtraKeysBlurRadius(value);
        if (isSurfaceTuningNormalized() || target == SURFACE_TUNING_TARGET_STATUS)
            mPreferences.setStatusBarBlurRadius(value);
        if (isSurfaceTuningNormalized()) syncSurfaceGlassSliders();
    }

    private void writeSurfaceOpacity(int target, int value) {
        if (mPreferences == null) return;
        if (isSurfaceTuningNormalized() || target == SURFACE_TUNING_TARGET_DOCK)
            mPreferences.setAppBarOpacity(value);
        if (isSurfaceTuningNormalized() || target == SURFACE_TUNING_TARGET_STATUS)
            mPreferences.setStatusBarOpacity(value);
        if (isSurfaceTuningNormalized()) syncSurfaceGlassSliders();
    }

    private void writeSurfaceGrain(int target, int value) {
        if (mPreferences == null) return;
        if (isSurfaceTuningNormalized() || target == SURFACE_TUNING_TARGET_DOCK)
            mPreferences.setDockGlassGrain(value);
        if (isSurfaceTuningNormalized() || target == SURFACE_TUNING_TARGET_STATUS)
            mPreferences.setStatusBarGrain(value);
        if (isSurfaceTuningNormalized()) syncSurfaceGlassSliders();
    }

    private void writeSurfaceCornerRadius(int target, int value) {
        if (mPreferences == null) return;
        if (isSurfaceTuningNormalized() || target == SURFACE_TUNING_TARGET_DOCK)
            mPreferences.setAppLauncherDockCornerRadius(value);
        if (isSurfaceTuningNormalized() || target == SURFACE_TUNING_TARGET_STATUS)
            mPreferences.setStatusBarCornerRadius(value);
        if (isSurfaceTuningNormalized()) syncSurfaceGlassSliders();
    }

    /** Clock style picker in the editor's Status section. */
    private void bindSurfaceTuningClockPicker() {
        com.google.android.material.button.MaterialButtonToggleGroup group =
            findViewById(R.id.surface_tuning_status_clock_group);
        if (group == null || mPreferences == null)
            return;
        group.clearOnButtonCheckedListeners();
        group.check(surfaceTuningClockButtonId(mPreferences.getTopPaneClockStyle()));
        group.addOnButtonCheckedListener((buttons, checkedId, isChecked) -> {
            if (!isChecked || mPreferences == null)
                return;
            String style = surfaceTuningClockStyle(checkedId);
            if (style.equals(mPreferences.getTopPaneClockStyle()))
                return;
            mPreferences.setTopPaneClockStyle(style);
            com.termux.app.terminal.TerminalClockWidget clock =
                findViewById(R.id.terminal_clock_widget);
            if (clock != null) {
                clock.setStyle(style);
                clock.setAlignment(mPreferences.getTopPaneClockAlignment());
            }
        });
    }

    private int surfaceTuningClockButtonId(String style) {
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD.equals(style))
            return R.id.surface_tuning_status_clock_lcd;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(style))
            return R.id.surface_tuning_status_clock_minimal;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED.equals(style))
            return R.id.surface_tuning_status_clock_led;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE.equals(style))
            return R.id.surface_tuning_status_clock_tape;
        if (TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB.equals(style))
            return R.id.surface_tuning_status_clock_slab;
        return R.id.surface_tuning_status_clock_flip;
    }

    private String surfaceTuningClockStyle(int buttonId) {
        if (buttonId == R.id.surface_tuning_status_clock_lcd)
            return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD;
        if (buttonId == R.id.surface_tuning_status_clock_minimal)
            return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL;
        if (buttonId == R.id.surface_tuning_status_clock_led)
            return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED;
        if (buttonId == R.id.surface_tuning_status_clock_tape)
            return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE;
        if (buttonId == R.id.surface_tuning_status_clock_slab)
            return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB;
        return TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP;
    }

    /**
     * "Match all surfaces". Turning it on immediately levels the other surfaces onto the status
     * bar's current values, so the switch shows its effect rather than waiting for the next slider
     * nudge to reveal it.
     */
    private void bindSurfaceTuningNormalizeSwitch() {
        com.google.android.material.materialswitch.MaterialSwitch normalize =
            findViewById(R.id.surface_tuning_status_normalize_switch);
        if (normalize == null || mPreferences == null)
            return;
        View normalizeLabel = findViewById(R.id.surface_tuning_status_normalize_label);
        if (normalizeLabel != null) normalizeLabel.setOnClickListener(v -> normalize.toggle());
        normalize.setOnCheckedChangeListener(null);
        normalize.setChecked(mPreferences.isSurfaceTuningNormalized());
        normalize.setOnCheckedChangeListener((button, isChecked) -> {
            if (mPreferences == null)
                return;
            mPreferences.setSurfaceTuningNormalized(isChecked);
            if (isChecked) {
                writeSurfaceBlur(SURFACE_TUNING_TARGET_STATUS, mPreferences.getStatusBarBlurRadius());
                writeSurfaceOpacity(SURFACE_TUNING_TARGET_STATUS, mPreferences.getStatusBarOpacity());
                writeSurfaceGrain(SURFACE_TUNING_TARGET_STATUS, mPreferences.getStatusBarGrain());
                writeSurfaceCornerRadius(SURFACE_TUNING_TARGET_STATUS,
                    mPreferences.getStatusBarCornerRadius());
                setSurfaceTuningInsetDp(SURFACE_TUNING_TARGET_STATUS,
                    mPreferences.getStatusBarHorizontalInset());
                syncSurfaceGlassSliders();
            }
            applyDockTuningStructuralPreview();
        });
    }

    /** A finger travel of 1dp moves a surface edge half a dp, so the 0..48dp span needs ~96dp. */
    private static final float SURFACE_TUNING_INSET_DRAG_GAIN = 0.5f;
    /** Finger travel that walks the dock across its whole preset height range. */
    private static final float SURFACE_TUNING_DOCK_HEIGHT_DRAG_SPAN_DP = 40f;
    /** How far the capture groups reach above their surface so the border handle is inside. */
    private static final int SURFACE_TUNING_HANDLE_OVERHANG_DP = 14;
    private static final long SURFACE_TUNING_FADE_DURATION_MS = 200;

    private int surfaceTuningInsetDp(int target) {
        if (mPreferences == null)
            return TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET;
        switch (target) {
            case SURFACE_TUNING_TARGET_KEYBOARD:
                return mPreferences.getInAppKeyboardHorizontalInset();
            case SURFACE_TUNING_TARGET_STATUS:
                return mPreferences.getStatusBarHorizontalInset();
            default:
                return mPreferences.getDockHorizontalInset();
        }
    }

    private void setSurfaceTuningInsetDp(int target, int insetDp) {
        if (mPreferences == null)
            return;
        if (isSurfaceTuningNormalized()) {
            mPreferences.setInAppKeyboardHorizontalInset(insetDp);
            mPreferences.setStatusBarHorizontalInset(insetDp);
            mPreferences.setDockHorizontalInset(insetDp);
            syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_DOCK);
            syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_KEYBOARD);
            syncSurfaceTuningInsetSlider(SURFACE_TUNING_TARGET_STATUS);
            applyDockTuningStructuralPreview();
            return;
        }
        switch (target) {
            case SURFACE_TUNING_TARGET_KEYBOARD:
                mPreferences.setInAppKeyboardHorizontalInset(insetDp);
                break;
            case SURFACE_TUNING_TARGET_STATUS:
                mPreferences.setStatusBarHorizontalInset(insetDp);
                break;
            default:
                mPreferences.setDockHorizontalInset(insetDp);
                break;
        }
        syncSurfaceTuningInsetSlider(target);
        applyDockTuningStructuralPreview();
    }

    private int surfaceTuningInsetSliderId(int target) {
        switch (target) {
            case SURFACE_TUNING_TARGET_KEYBOARD:
                return R.id.surface_tuning_keyboard_inset_slider;
            case SURFACE_TUNING_TARGET_STATUS:
                return R.id.surface_tuning_status_inset_slider;
            default:
                return R.id.surface_tuning_dock_inset_slider;
        }
    }

    private int surfaceTuningInsetValueId(int target) {
        switch (target) {
            case SURFACE_TUNING_TARGET_KEYBOARD:
                return R.id.surface_tuning_keyboard_inset_value;
            case SURFACE_TUNING_TARGET_STATUS:
                return R.id.surface_tuning_status_inset_value;
            default:
                return R.id.surface_tuning_dock_inset_value;
        }
    }

    private void syncSurfaceTuningInsetSlider(int target) {
        int insetDp = surfaceTuningInsetDp(target);
        SeekBar slider = findViewById(surfaceTuningInsetSliderId(target));
        TextView value = findViewById(surfaceTuningInsetValueId(target));
        if (slider != null && slider.getProgress() != insetDp)
            slider.setProgress(insetDp);
        if (value != null)
            value.setText(getString(R.string.termux_dock_tuning_value_dp, insetDp));
    }

    private void bindSurfaceTuningInsetSeekBar(int target) {
        SeekBar slider = findViewById(surfaceTuningInsetSliderId(target));
        if (slider == null)
            return;
        slider.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                TextView value = findViewById(surfaceTuningInsetValueId(target));
                if (value != null)
                    value.setText(getString(R.string.termux_dock_tuning_value_dp, progress));
                if (fromUser && mPreferences != null && progress != surfaceTuningInsetDp(target))
                    setSurfaceTuningInsetDp(target, progress);
            }
        });
        syncSurfaceTuningInsetSlider(target);
    }

    /**
     * Horizontal drag anywhere over a surface walks its symmetric screen-edge inset: right widens
     * both edges, left narrows them. Previews land in preferences immediately like the card's own
     * sliders, so Done keeps them and Close restores the values captured on entry.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void bindSurfaceTuningInsetGesture(int groupId, int target) {
        View group = findViewById(groupId);
        if (group == null)
            return;
        group.setOnTouchListener((view, event) -> {
            if (!mDockTuningMode || mPreferences == null)
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mSurfaceTuningInsetDragStartX = event.getRawX();
                    mSurfaceTuningInsetDragStartDp = surfaceTuningInsetDp(target);
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float travelDp = pxToDp(event.getRawX() - mSurfaceTuningInsetDragStartX);
                    int insetDp = TermuxAppSharedPreferences.clampSurfaceHorizontalInset(
                        Math.round(mSurfaceTuningInsetDragStartDp
                            + (travelDp * SURFACE_TUNING_INSET_DRAG_GAIN)));
                    if (insetDp != surfaceTuningInsetDp(target))
                        setSurfaceTuningInsetDp(target, insetDp);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    /** Vertical drag on the dock's top-border pill walks the preset height range continuously. */
    @SuppressLint("ClickableViewAccessibility")
    private void bindSurfaceTuningDockHeightGesture() {
        View handle = findViewById(R.id.surface_tuning_dock_height_handle);
        if (handle == null)
            return;
        handle.setOnTouchListener((view, event) -> {
            if (!mDockTuningMode || mPreferences == null)
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mSurfaceTuningDockHeightDragStartY = event.getRawY();
                    mSurfaceTuningDockHeightDragStartScale =
                        mPreferences.getAppLauncherBarHeightScale();
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float minScale = DOCK_TUNING_SIZE_PRESETS[0];
                    float maxScale = DOCK_TUNING_SIZE_PRESETS[DOCK_TUNING_SIZE_PRESETS.length - 1];
                    float travelDp = pxToDp(mSurfaceTuningDockHeightDragStartY - event.getRawY());
                    float scale = mSurfaceTuningDockHeightDragStartScale
                        + ((travelDp / SURFACE_TUNING_DOCK_HEIGHT_DRAG_SPAN_DP)
                            * (maxScale - minScale));
                    scale = Math.max(minScale, Math.min(maxScale, scale));
                    if (Float.compare(scale, mPreferences.getAppLauncherBarHeightScale()) != 0) {
                        mPreferences.setAppLauncherBarHeightScale(scale);
                        syncSurfaceTuningDockHeightSlider();
                        applyDockTuningStructuralPreview();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    /** Vertical drag on the keyboard's top-border pill, on the same 1:1 mapping as the old handle. */
    @SuppressLint("ClickableViewAccessibility")
    private void bindSurfaceTuningKeyboardHeightGesture() {
        View handle = findViewById(R.id.surface_tuning_keyboard_height_handle);
        if (handle == null)
            return;
        handle.setOnTouchListener((view, event) -> {
            if (!mDockTuningMode || mPreferences == null || mInAppKeyboard == null)
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mInAppKeyboardHeightDragStartY = event.getRawY();
                    mInAppKeyboardHeightDragStartScale = mInAppKeyboard.getHeightScale();
                    int renderedHeight = mAttachedInAppKeyboardView == null
                        ? 0 : mAttachedInAppKeyboardView.getMeasuredHeight();
                    mInAppKeyboardUnscaledDragHeight = Math.max(1f,
                        renderedHeight / Math.max(0.01f, mInAppKeyboardHeightDragStartScale));
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float scale = TermuxInAppKeyboard.calculateHeightScaleForDrag(
                        mInAppKeyboardHeightDragStartScale,
                        event.getRawY() - mInAppKeyboardHeightDragStartY,
                        mInAppKeyboardUnscaledDragHeight);
                    mInAppKeyboard.previewSurfaceEditorHeightScale(scale);
                    syncSurfaceTuningKeyboardHeightSlider(mInAppKeyboard.getHeightScale());
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mPreferences.setInAppKeyboardHeightScale(mInAppKeyboard.getHeightScale());
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void syncSurfaceTuningDockHeightSlider() {
        if (mPreferences == null)
            return;
        int index = nearestDockSizePresetIndex(mPreferences.getAppLauncherBarHeightScale());
        SeekBar slider = findViewById(R.id.dock_tuning_size_slider);
        TextView value = findViewById(R.id.dock_tuning_size_value);
        if (slider != null && slider.getProgress() != index)
            slider.setProgress(index);
        if (value != null)
            value.setText(dockSizePresetLabel(index));
    }

    private void syncSurfaceTuningKeyboardHeightSlider(float heightScale) {
        int progress = keyboardEditorProgress(heightScale,
            TermuxPreferenceConstants.TERMUX_APP.MIN_IN_APP_KEYBOARD_HEIGHT_SCALE,
            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE);
        SeekBar slider = findViewById(R.id.surface_tuning_keyboard_height_slider);
        TextView value = findViewById(R.id.surface_tuning_keyboard_height_value);
        if (slider != null && slider.getProgress() != progress)
            slider.setProgress(progress);
        if (value != null)
            value.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
    }

    private void bindSurfaceTuningGestures() {
        bindSurfaceTuningInsetSeekBar(SURFACE_TUNING_TARGET_DOCK);
        bindSurfaceTuningInsetSeekBar(SURFACE_TUNING_TARGET_KEYBOARD);
        bindSurfaceTuningInsetSeekBar(SURFACE_TUNING_TARGET_STATUS);
        bindSurfaceTuningInsetGesture(R.id.surface_tuning_dock_gesture_group,
            SURFACE_TUNING_TARGET_DOCK);
        bindSurfaceTuningInsetGesture(R.id.surface_tuning_keyboard_gesture_group,
            SURFACE_TUNING_TARGET_KEYBOARD);
        bindSurfaceTuningInsetGesture(R.id.surface_tuning_status_gesture_group,
            SURFACE_TUNING_TARGET_STATUS);
        bindSurfaceTuningDockHeightGesture();
        bindSurfaceTuningKeyboardHeightGesture();
    }

    private void setSurfaceTuningGestureOverlayVisible(boolean visible) {
        View overlay = findViewById(R.id.surface_tuning_gesture_overlay);
        if (overlay == null)
            return;
        overlay.animate().cancel();
        if (visible) {
            positionSurfaceTuningGestureTargets();
            overlay.setAlpha(0f);
            overlay.setVisibility(View.VISIBLE);
            overlay.animate().alpha(1f).setDuration(SURFACE_TUNING_FADE_DURATION_MS)
                .setInterpolator(surfaceTuningFadeInterpolator()).start();
            return;
        }
        overlay.animate().alpha(0f).setDuration(SURFACE_TUNING_FADE_DURATION_MS)
            .setInterpolator(surfaceTuningFadeInterpolator())
            .withEndAction(() -> {
                overlay.setVisibility(View.GONE);
                overlay.setAlpha(1f);
            }).start();
    }

    private android.view.animation.Interpolator surfaceTuningFadeInterpolator() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? new android.view.animation.PathInterpolator(0.16f, 1f, 0.3f, 1f)
            : new android.view.animation.DecelerateInterpolator(1.8f);
    }

    private void positionSurfaceTuningGestureTargets() {
        View overlay = findViewById(R.id.surface_tuning_gesture_overlay);
        if (overlay == null || !mDockTuningMode || overlay.getWidth() <= 0)
            return;
        View statusSurface = findViewById(R.id.terminal_window_bar_host);
        positionSurfaceTuningGestureGroup(R.id.surface_tuning_status_gesture_group, overlay,
            statusSurface);
        resizeStatusTuningPills(statusSurface);
        positionSurfaceTuningGestureGroup(R.id.surface_tuning_dock_gesture_group, overlay,
            findViewById(R.id.accessory_surface_host));
        positionSurfaceTuningGestureGroup(R.id.surface_tuning_keyboard_gesture_group, overlay,
            isInAppKeyboardShown() ? findViewById(R.id.inapp_keyboard_view_host) : null);
    }

    /**
     * Tracks one surface's measured rect with its capture group, reaching
     * {@link #SURFACE_TUNING_HANDLE_OVERHANG_DP} further up so the pill centred on the top border
     * still falls inside the group's hit area.
     */
    private void positionSurfaceTuningGestureGroup(int groupId, @NonNull View overlay,
                                                   @Nullable View surface) {
        View group = findViewById(groupId);
        if (group == null)
            return;
        if (surface == null || surface.getVisibility() != View.VISIBLE
            || surface.getWidth() <= 0 || surface.getHeight() <= 0) {
            group.setVisibility(View.GONE);
            return;
        }
        int[] overlayLocation = new int[2];
        int[] surfaceLocation = new int[2];
        overlay.getLocationInWindow(overlayLocation);
        surface.getLocationInWindow(surfaceLocation);
        int surfaceTop = surfaceLocation[1] - overlayLocation[1];
        int top = Math.max(0, surfaceTop - Math.round(dpToPx(SURFACE_TUNING_HANDLE_OVERHANG_DP)));
        int left = Math.max(0, surfaceLocation[0] - overlayLocation[0]);
        // Pin both margins against a match_parent width so the group never depends on how the
        // overlay resolves an absent horizontal gravity.
        int right = Math.max(0, overlay.getWidth() - (left + surface.getWidth()));
        int height = Math.max(1, (surfaceTop + surface.getHeight()) - top);
        ViewGroup.LayoutParams layoutParams = group.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) layoutParams;
            if (params.leftMargin != left || params.rightMargin != right
                || params.topMargin != top || params.height != height) {
                params.leftMargin = left;
                params.rightMargin = right;
                params.topMargin = top;
                params.height = height;
                group.setLayoutParams(params);
            }
        }
        group.setVisibility(View.VISIBLE);
    }

    /**
     * The status pane can collapse to a ~32dp compact bar, where the fixed 28dp side pills read
     * as oversized bars instead of edge handles. Scale them to a bit over half the pane height,
     * capped at the shared 28dp; the capsule drawable keeps proper arc ends at any height.
     */
    private void resizeStatusTuningPills(@Nullable View statusSurface) {
        if (statusSurface == null || statusSurface.getHeight() <= 0)
            return;
        int target = Math.round(Math.min(dpToPx(28),
            Math.max(dpToPx(12), statusSurface.getHeight() * 0.55f)));
        int[] pillIds = {R.id.surface_tuning_status_pill_left, R.id.surface_tuning_status_pill_right};
        for (int pillId : pillIds) {
            View pill = findViewById(pillId);
            if (pill == null)
                continue;
            ViewGroup.LayoutParams params = pill.getLayoutParams();
            if (params != null && params.height != target) {
                params.height = target;
                pill.setLayoutParams(params);
            }
        }
    }

    // ------------------------------------------------------------------ keybind hint popup

    /**
     * While Ctrl+Alt (optionally +Shift) is latched on the in-app keyboard, the bound caps
     * light up in their legend group's colour on the live keyboard itself, and a glass slab
     * flush against the accessory stack shows a grouped legend of what each lit key does. Any
     * other modifier state removes both, so they track latch, lock and release for free via
     * onKeyboardModifiersChanged.
     *
     * <p>A prefix change while latched (Shift joining or leaving) never remounts the slab: the
     * legend re-runs its entry animation, the keyboard re-lights and the letters flip case.
     */
    private void updateKeybindHintPopup(
            @Nullable com.termux.app.terminal.inappkeyboard.TerminalModifiers modifiers) {
        android.widget.LinearLayout popup = findViewById(R.id.keybind_hint_popup);
        if (popup == null) return;
        boolean show = modifiers != null && modifiers.isCtrl() && modifiers.isAlt()
            && isInAppKeyboardShown() && isSplitPanesEnabled();
        Map<String, String> hints = null;
        boolean shift = show && modifiers.isShift();
        String prefix = shift ? "ctrl+alt+shift+" : "ctrl+alt+";
        if (show) {
            hints = com.termux.app.terminal.TerminalKeyBindingResolver.getInstance()
                .hintsForPrefix(prefix,
                    com.termux.app.terminal.TerminalActionDispatcher.getInstance().actionContext());
            show = !hints.isEmpty();
        }
        if (!show) {
            hideKeybindHintPopup();
            return;
        }
        boolean visible = popup.getVisibility() == View.VISIBLE;
        // Modifier callbacks repeat for the same latch state; only content changes repopulate,
        // so the lighting and entry animations are not restarted every callback.
        String signature = prefix + '|' + hints;
        if (visible && signature.equals(popup.getTag())) return;
        popup.setTag(signature);
        Map<String, Integer> litTokens = populateKeybindHintPopup(popup, hints, shift);
        if (mInAppKeyboard != null)
            mInAppKeyboard.setKeybindHintHighlights(litTokens);
        float barAlpha = mPreferences != null ? mPreferences.getAppBarOpacity() / 100f : 0.5f;
        popup.setBackground(buildDockGlassSurface(Math.max(0.85f, barAlpha), 0f, 1f, false));
        if (!visible) {
            popup.setVisibility(View.VISIBLE);
            if (isReducedMotionEnabled()) {
                popup.setAlpha(1f);
                popup.setTranslationY(0f);
            } else {
                popup.setAlpha(0f);
                popup.setTranslationY(dpToPx(14));
                popup.animate().alpha(1f).translationY(0f).setDuration(220L)
                    .setInterpolator(new android.view.animation.PathInterpolator(
                        0.2f, 0.8f, 0.2f, 1f))
                    .start();
            }
        } else {
            // A repopulate can land while the hide fade is still running; keep the slab up.
            popup.animate().cancel();
            popup.setAlpha(1f);
            popup.setTranslationY(0f);
        }
    }

    private void hideKeybindHintPopup() {
        if (mInAppKeyboard != null)
            mInAppKeyboard.setKeybindHintHighlights(null);
        View popup = findViewById(R.id.keybind_hint_popup);
        if (popup == null || popup.getVisibility() != View.VISIBLE) return;
        popup.setTag(null);
        popup.animate().alpha(0f).setDuration(100L)
            .withEndAction(() -> popup.setVisibility(View.GONE)).start();
    }

    /**
     * Cap on legend rows, not on lit keys. Every bound key under the latched prefix lights up
     * whatever this is: the 18-row cap used to end the loop that also built the lighting map, so
     * the strokes registered last — Ctrl+Alt+R among them, behind the nine session-index digits —
     * were neither listed nor lit. Runs collapse to one row each, which is what makes the cap
     * comfortable rather than tight.
     */
    private static final int KEYBIND_HINT_MAX = 24;
    private static final int KEYBIND_HINT_COLUMNS = 2;
    private static final long KEYBIND_HINT_LEGEND_BASE_DELAY_MS = 60L;
    private static final long KEYBIND_HINT_LEGEND_STAGGER_MS = 26L;

    /** One legend line: the keycap text shown, the keyboard tokens it lights, and its label. */
    private static final class KeybindHintEntry {
        String cap;
        final java.util.List<String> tokens = new java.util.ArrayList<>(4);
        final String label;

        KeybindHintEntry(String cap, String token, String label) {
            this.cap = cap;
            this.tokens.add(token);
            this.label = label;
        }
    }

    /** Builds the legend and returns the binding token -> group colour map for the keyboard. */
    @NonNull
    private Map<String, Integer> populateKeybindHintPopup(
            @NonNull android.widget.LinearLayout popup,
            @NonNull Map<String, String> hints, boolean shift) {
        popup.removeAllViews();
        LauncherToolRegistry registry = LauncherToolRegistry.getInstance();
        boolean animate = !isReducedMotionEnabled();
        int onSurface = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface,
            R.color.termux_on_surface);
        int glassBase = resolveAccessoryGlassBaseColor();
        int primary = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorPrimary,
            R.color.termux_primary);
        java.util.EnumMap<com.termux.app.terminal.KeybindGroupPalette.Group, Integer> groupColors =
            new java.util.EnumMap<>(com.termux.app.terminal.KeybindGroupPalette.Group.class);

        // Legend groups in KeybindGroupPalette order, so the same action always lands in the same
        // section with the same colour. Keys of one tool that form a run — the arrows, the session
        // digits — collapse into one entry ("←↓↑→ Move pane focus", "1-9 Switch to session") so a
        // whole row of keys costs one legend row.
        java.util.EnumMap<com.termux.app.terminal.KeybindGroupPalette.Group,
            java.util.List<KeybindHintEntry>> groups =
            new java.util.EnumMap<>(com.termux.app.terminal.KeybindGroupPalette.Group.class);
        java.util.Map<String, KeybindHintEntry> runEntryByTool = new java.util.HashMap<>();
        java.util.List<KeybindHintEntry> runEntries = new java.util.ArrayList<>();
        java.util.Map<String, Integer> litTokens = new java.util.LinkedHashMap<>();
        int added = 0;
        for (Map.Entry<String, String> hint : hints.entrySet()) {
            String token = hint.getKey();
            String toolName = hint.getValue();
            com.termux.app.terminal.KeybindGroupPalette.Group group =
                com.termux.app.terminal.KeybindGroupPalette.groupFor(toolName);
            Integer groupColor = groupColors.get(group);
            if (groupColor == null) {
                groupColor = com.termux.app.terminal.KeybindGroupPalette
                    .colorFor(group, primary, glassBase);
                groupColors.put(group, groupColor);
            }
            // Lighting is never truncated: a bound cap that lights but has no legend row still
            // tells the truth, a legend row for a dark cap would not.
            litTokens.put(token, groupColor);
            boolean run = keybindHintRunToken(token);
            if (run) {
                KeybindHintEntry merged = runEntryByTool.get(toolName);
                if (merged != null) {
                    merged.tokens.add(token);
                    continue;
                }
            }
            if (added >= KEYBIND_HINT_MAX) continue;
            added++;
            KeybindHintEntry entry = new KeybindHintEntry(
                keybindHintCapText(token, shift), token, keybindHintLabel(registry, toolName));
            if (run) {
                runEntryByTool.put(toolName, entry);
                runEntries.add(entry);
            }
            java.util.List<KeybindHintEntry> groupEntries = groups.get(group);
            if (groupEntries == null) {
                groupEntries = new java.util.ArrayList<>();
                groups.put(group, groupEntries);
            }
            groupEntries.add(entry);
        }
        // A merged entry shows every key it absorbed: arrows as glyphs in ←↓↑→ order, digits as
        // the range they span.
        for (KeybindHintEntry entry : runEntries) {
            if (entry.tokens.size() > 1) entry.cap = keybindHintRunCap(entry.tokens);
        }

        int groupIndex = 0;
        for (Map.Entry<com.termux.app.terminal.KeybindGroupPalette.Group,
                java.util.List<KeybindHintEntry>> group : groups.entrySet()) {
            int groupColor = groupColors.get(group.getKey());
            View groupView = buildKeybindHintGroup(group.getKey().title(), group.getValue(),
                groupColor, onSurface);
            android.widget.LinearLayout.LayoutParams groupParams =
                new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (groupIndex > 0) groupParams.topMargin = Math.round(dpToPx(6));
            popup.addView(groupView, groupParams);
            if (animate) {
                groupView.setAlpha(0f);
                groupView.setTranslationY(dpToPx(8));
                groupView.animate().alpha(1f).translationY(0f).setDuration(280L)
                    .setStartDelay(KEYBIND_HINT_LEGEND_BASE_DELAY_MS
                        + groupIndex * KEYBIND_HINT_LEGEND_STAGGER_MS)
                    .setInterpolator(new android.view.animation.PathInterpolator(
                        0.2f, 0.8f, 0.2f, 1f))
                    .start();
            }
            groupIndex++;
        }
        return litTokens;
    }

    /**
     * Whether this key is part of a run one tool claims across several keys — the four arrows, the
     * nine session digits. Such keys share a legend row instead of each taking one.
     */
    private static boolean keybindHintRunToken(@NonNull String token) {
        if (keybindHintArrowGlyph(token) != null) return true;
        return token.length() == 1 && token.charAt(0) >= '0' && token.charAt(0) <= '9';
    }

    /** Keycap text for a merged run: {@code ←↓↑→} for arrows, {@code 1-9} for a digit span. */
    @NonNull
    private static String keybindHintRunCap(@NonNull java.util.List<String> tokens) {
        StringBuilder cap = new StringBuilder();
        for (String token : new String[] {"left", "down", "up", "right"}) {
            if (tokens.contains(token)) cap.append(keybindHintArrowGlyph(token));
        }
        java.util.List<String> digits = new java.util.ArrayList<>(tokens.size());
        for (String token : tokens) {
            if (keybindHintArrowGlyph(token) == null) digits.add(token);
        }
        java.util.Collections.sort(digits);
        if (digits.size() >= 3) {
            // Contiguity is not checked: a gap in the middle of nine index binds is not worth
            // spelling out on a cap this small, and the labels name the action either way.
            cap.append(digits.get(0)).append('-').append(digits.get(digits.size() - 1));
        } else {
            for (int i = 0; i < digits.size(); i++) {
                if (i > 0) cap.append(' ');
                cap.append(digits.get(i));
            }
        }
        return cap.toString();
    }

    @Nullable
    private static String keybindHintArrowGlyph(@NonNull String token) {
        switch (token) {
            case "left": return "←";
            case "down": return "↓";
            case "up": return "↑";
            case "right": return "→";
            default: return null;
        }
    }

    /** Legend keycap text: spelled-out tokens back to their glyph, letters follow the prefix case. */
    @NonNull
    private static String keybindHintCapText(@NonNull String token, boolean shift) {
        String arrow = keybindHintArrowGlyph(token);
        if (arrow != null) return arrow;
        switch (token) {
            case "minus": return "-";
            case "equals": return "=";
            case "plus": return "+";
            // Named keys as their glyph: a legend cap column is 22dp wide, which "backspace"
            // spelled out overruns before the label it belongs to has started.
            case "space": return "␣";
            case "tab": return "⇥";
            case "enter": return "⏎";
            case "backspace": return "⌫";
            case "delete": return "⌦";
            case "escape": return "esc";
            case "pageup": return "⇞";
            case "pagedown": return "⇟";
            default:
                return shift ? token.toUpperCase(java.util.Locale.ROOT)
                    : token.toLowerCase(java.util.Locale.ROOT);
        }
    }

    @NonNull
    private View buildKeybindHintGroup(@NonNull String title,
                                       @NonNull java.util.List<KeybindHintEntry> entries,
                                       int groupColor, int onSurface) {
        android.widget.LinearLayout group = new android.widget.LinearLayout(this);
        group.setOrientation(android.widget.LinearLayout.VERTICAL);

        android.widget.LinearLayout header = new android.widget.LinearLayout(this);
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View swatch = new View(this);
        GradientDrawable swatchShape = new GradientDrawable();
        swatchShape.setColor(groupColor);
        swatchShape.setCornerRadius(dpToPx(1));
        swatch.setBackground(swatchShape);
        int swatchSize = Math.round(dpToPx(3.5f));
        android.widget.LinearLayout.LayoutParams swatchParams =
            new android.widget.LinearLayout.LayoutParams(swatchSize, swatchSize);
        swatchParams.rightMargin = Math.round(dpToPx(4.5f));
        header.addView(swatch, swatchParams);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTypeface(android.graphics.Typeface.create(
            android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD));
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 6.5f);
        titleView.setLetterSpacing(0.2f);
        titleView.setTextColor(groupColor);
        header.addView(titleView, new android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View rule = new View(this);
        rule.setBackgroundColor(withAlphaComponent(groupColor, 51));
        android.widget.LinearLayout.LayoutParams ruleParams =
            new android.widget.LinearLayout.LayoutParams(0,
                Math.max(1, Math.round(dpToPx(0.5f))), 1f);
        ruleParams.leftMargin = Math.round(dpToPx(6));
        header.addView(rule, ruleParams);
        group.addView(header, new android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int labelColor = withAlphaComponent(onSurface, 199);
        android.widget.LinearLayout row = null;
        for (int i = 0; i < entries.size(); i++) {
            if (row == null || i % KEYBIND_HINT_COLUMNS == 0) {
                row = new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                android.widget.LinearLayout.LayoutParams rowParams =
                    new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = Math.round(dpToPx(i == 0 ? 6 : 1));
                group.addView(row, rowParams);
            }
            KeybindHintEntry entry = entries.get(i);
            android.widget.LinearLayout cell = new android.widget.LinearLayout(this);
            cell.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);

            TextView key = new TextView(this);
            key.setText(entry.cap);
            key.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            key.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 8.5f);
            key.setTextColor(groupColor);
            key.setMinWidth(Math.round(dpToPx(22)));
            cell.addView(key, new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = new TextView(this);
            label.setText(entry.label);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10.5f);
            label.setTextColor(labelColor);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            android.widget.LinearLayout.LayoutParams labelParams =
                new android.widget.LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.leftMargin = Math.round(dpToPx(4.5f));
            labelParams.rightMargin = i % KEYBIND_HINT_COLUMNS == 0 ? Math.round(dpToPx(8)) : 0;
            cell.addView(label, labelParams);

            row.addView(cell, new android.widget.LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        // An odd trailing cell still gets its half of the row, keeping columns aligned.
        if (row != null && row.getChildCount() == 1 && KEYBIND_HINT_COLUMNS == 2) {
            View filler = new View(this);
            row.addView(filler, new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        }
        return group;
    }

    @NonNull
    private String keybindHintLabel(@NonNull LauncherToolRegistry registry,
                                    @NonNull String toolName) {
        LauncherToolRegistry.ToolMetadata tool = registry.getTool(toolName);
        if (tool != null && tool.titleRes != 0) return getString(tool.titleRes);
        return toolName;
    }

    private void registerDockTuningLayoutListener(@NonNull View controls) {
        if (mDockTuningLayoutListener != null)
            return;
        mDockTuningLayoutListener = () -> {
            adjustDockTuningCardHeight();
            positionSurfaceTuningGestureTargets();
        };
        controls.getViewTreeObserver().addOnGlobalLayoutListener(mDockTuningLayoutListener);
    }

    private void unregisterDockTuningLayoutListener() {
        if (mDockTuningLayoutListener == null)
            return;
        View controls = findViewById(R.id.dock_tuning_controls);
        if (controls != null)
            controls.getViewTreeObserver().removeOnGlobalLayoutListener(mDockTuningLayoutListener);
        mDockTuningLayoutListener = null;
    }

    /**
     * Caps the scrollable slider region so the header and Done button always stay on screen above
     * the accessory stack (dock, and the in-app keyboard when shown), regardless of screen size or
     * density. When the content fits it stays wrap-content; when it would overflow it scrolls.
     */
    private void adjustDockTuningCardHeight() {
        if (!mDockTuningMode)
            return;
        View controls = findViewById(R.id.dock_tuning_controls);
        ScrollView scroll = findViewById(R.id.dock_tuning_scroll);
        View headerRow = findViewById(R.id.dock_tuning_header_row);
        View navigation = findViewById(R.id.surface_tuning_navigation);
        View actions = findViewById(R.id.surface_tuning_actions);
        View stack = findViewById(R.id.accessory_stack_container);
        if (controls == null || scroll == null || headerRow == null || navigation == null
            || actions == null || stack == null)
            return;
        if (controls.getVisibility() != View.VISIBLE)
            return;
        View scrollChild = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        if (scrollChild == null)
            return;

        // The card may grow until just under the launcher's own status bar — a 2dp seam, not a
        // band of empty terminal. Positions are compared in the card parent's coordinate space,
        // so the window bar (a child of the terminal container) is converted through the window.
        int statusBottom = Math.max(mLastStatusBarInsetTop, Math.round(dpToPx(24)));
        View windowBar = findViewById(R.id.terminal_window_bar_host);
        if (windowBar != null && windowBar.getVisibility() == View.VISIBLE
            && windowBar.getHeight() > 0 && controls.getParent() instanceof View) {
            int[] location = new int[2];
            windowBar.getLocationInWindow(location);
            int barBottomInWindow = location[1] + windowBar.getHeight();
            ((View) controls.getParent()).getLocationInWindow(location);
            statusBottom = Math.max(statusBottom, barBottomInWindow - location[1]);
        }
        int topLimit = statusBottom + Math.round(dpToPx(2));
        int cardMarginBottom = Math.round(dpToPx(10));
        int availableCard = (stack.getTop() - cardMarginBottom) - topLimit;
        // Chrome outside the scroll region: card top/bottom padding (10 + 12), Done top margin (6),
        // plus the measured header and Done heights.
        int chrome = Math.round(dpToPx(10 + 12 + 6)) + headerRow.getHeight()
            + navigation.getHeight() + actions.getHeight();
        int maxScroll = availableCard - chrome;
        int minScroll = Math.round(dpToPx(96));
        if (maxScroll < minScroll)
            maxScroll = minScroll;

        ViewGroup.LayoutParams lp = scroll.getLayoutParams();
        // Keep the card and tab row stationary as sections change. A wrap-content scroll area made
        // shorter panels pull the whole bottom-anchored card downward under the user's finger.
        int target = maxScroll;
        if (lp.height != target) {
            lp.height = target;
            scroll.setLayoutParams(lp);
        }
    }

    private void applyDockTuningPreview(boolean blurChanged) {
        if (blurChanged)
            clearCachedAccessoryWallpaperBlur();
        mAccessoryBackdropDirty = true;
        mDecorNavBarBackdropDirty = true;
        mInAppKeyboardBackdropDirty = true;
        applySuggestionBarPreferences();
        applyAccessoryRenderState(buildAccessoryRenderState());
        scheduleAccessoryRenderSync("dock-tuning:preview");
    }

    // Live-preview scopes for the surface editor. Sliders fire onProgressChanged far faster than
    // a full re-apply fits in a frame, so requests carry only the scopes their control touches and
    // are coalesced to a single apply per animation frame. GLASS (the accessory re-render in
    // applyDockTuningPreview) runs on every apply; BLUR additionally throws away the shared
    // pre-blurred wallpaper bitmap, which is the single most expensive thing a slider can cause —
    // only radius controls may request it.
    private static final int TUNING_PREVIEW_GLASS = 1;
    private static final int TUNING_PREVIEW_BLUR = 1 << 1;
    private static final int TUNING_PREVIEW_GEOMETRY = 1 << 2;
    private static final int TUNING_PREVIEW_SURFACES = 1 << 3;
    private static final int TUNING_PREVIEW_KEYBOARD = 1 << 4;
    private static final int TUNING_PREVIEW_ALL = TUNING_PREVIEW_GLASS | TUNING_PREVIEW_BLUR
        | TUNING_PREVIEW_GEOMETRY | TUNING_PREVIEW_SURFACES | TUNING_PREVIEW_KEYBOARD;

    private int mPendingTuningPreviewScopes;
    private boolean mTuningPreviewScheduled;
    private final Runnable mTuningPreviewRunnable = this::runPendingTuningPreview;

    private void requestDockTuningPreview(int scopes) {
        mPendingTuningPreviewScopes |= scopes | TUNING_PREVIEW_GLASS;
        if (mTuningPreviewScheduled)
            return;
        View root = findViewById(R.id.activity_termux_root_view);
        if (root == null) {
            runPendingTuningPreview();
            return;
        }
        mTuningPreviewScheduled = true;
        root.postOnAnimation(mTuningPreviewRunnable);
    }

    private void runPendingTuningPreview() {
        mTuningPreviewScheduled = false;
        int scopes = mPendingTuningPreviewScopes;
        mPendingTuningPreviewScopes = 0;
        if (scopes == 0 || mPreferences == null)
            return;
        if ((scopes & TUNING_PREVIEW_GEOMETRY) != 0) {
            updateAppLauncherBarHeight();
            setTerminalToolbarHeight(true);
            configureExtraKeysBackground();
        }
        if ((scopes & TUNING_PREVIEW_SURFACES) != 0) {
            applyTerminalSurfaceAppearance();
            refreshTerminalWindowBar();
            configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, false,
                mPreferences.getSessionsOpacity() / 100f, 0);
        }
        if ((scopes & TUNING_PREVIEW_KEYBOARD) != 0 && mInAppKeyboard != null)
            mInAppKeyboard.onPreferencesReloaded();
        applyDockTuningPreview((scopes & TUNING_PREVIEW_BLUR) != 0);
    }

    /** Broader live re-apply for controls that change dock geometry, terminal, or sessions surfaces. */
    private void applyDockTuningStructuralPreview() {
        requestDockTuningPreview(TUNING_PREVIEW_ALL);
    }

    @NonNull
    private static String terminalContrastLevelForButton(int checkedId) {
        com.termux.shared.termux.settings.preferences.TerminalContrastLevel level;
        if (checkedId == R.id.dock_tuning_terminal_contrast_softer) {
            level = com.termux.shared.termux.settings.preferences.TerminalContrastLevel.SOFTER;
        } else if (checkedId == R.id.dock_tuning_terminal_contrast_harder) {
            level = com.termux.shared.termux.settings.preferences.TerminalContrastLevel.HARDER;
        } else {
            level = com.termux.shared.termux.settings.preferences.TerminalContrastLevel.DEFAULT;
        }
        return level.value;
    }

    private static int terminalContrastButtonForLevel(
            @NonNull com.termux.shared.termux.settings.preferences.TerminalContrastLevel level) {
        switch (level) {
            case SOFTER: return R.id.dock_tuning_terminal_contrast_softer;
            case HARDER: return R.id.dock_tuning_terminal_contrast_harder;
            default: return R.id.dock_tuning_terminal_contrast_default;
        }
    }

    /**
     * Selects the stored level without firing the listener, and disables the row when the palette it
     * grades is not in use: contrast targets the generated wallpaper palette, so with wallpaper colours
     * off there is nothing for it to act on. The hint says so rather than leaving a dead control.
     */
    private void syncTerminalContrastGroup(@Nullable MaterialButtonToggleGroup group,
                                           @Nullable TextView hint) {
        if (mPreferences == null || group == null) return;
        boolean available = mPreferences.isTerminalDynamicColorsEnabled();
        // No listener juggling: the reset button calls this too, and clearing here would leave the row
        // dead afterwards. The listener is a no-op when the level it reads back is already stored.
        group.check(terminalContrastButtonForLevel(mPreferences.getTerminalContrastLevel()));
        for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setEnabled(available);
        if (hint != null) hint.setVisibility(available ? View.GONE : View.VISIBLE);
    }

    /**
     * Regenerate the terminal palette and restyle the surfaces that read it. Both halves are needed:
     * the sessions take their colours from the generated palette, while the wallpaper-mode overlay
     * takes only its background tone, and a contrast change moves both.
     */
    private void applyTerminalContrastChange() {
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.refreshMaterialTerminalColorsIfNeeded();
        applyTerminalSurfaceAppearance();
    }

    private static final float[] DOCK_TUNING_SIZE_PRESETS = {1.72f, 1.95f, 2.18f, 2.45f};

    private int nearestDockSizePresetIndex(float scale) {
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < DOCK_TUNING_SIZE_PRESETS.length; i++) {
            float distance = Math.abs(scale - DOCK_TUNING_SIZE_PRESETS[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    @NonNull
    private String dockSizePresetLabel(int index) {
        switch (Math.max(0, Math.min(DOCK_TUNING_SIZE_PRESETS.length - 1, index))) {
            case 0:
                return getString(R.string.termux_dock_preset_smallest);
            case 1:
                return getString(R.string.termux_dock_preset_small);
            case 2:
                return getString(R.string.termux_dock_preset_default);
            default:
                return getString(R.string.termux_dock_preset_large);
        }
    }

    private void exitDockTuningMode() {
        mDockTuningMode = false;
        setSurfaceTuningGestureOverlayVisible(false);
        unregisterDockTuningLayoutListener();
        ScrollView scroll = findViewById(R.id.dock_tuning_scroll);
        if (scroll != null) {
            ViewGroup.LayoutParams lp = scroll.getLayoutParams();
            if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                scroll.setLayoutParams(lp);
            }
        }
        View controls = findViewById(R.id.dock_tuning_controls);
        if (controls != null)
            controls.setVisibility(View.GONE);
        restoreExpandedStatusAfterSurfaceEditor();
        mDockTuningRestoreExpandedStatus = false;
        mSurfaceEditorExpandedStatusPane = false;
    }

    private void restoreExpandedStatusAfterSurfaceEditor() {
        if (mPreferences == null)
            return;
        // Only the editor's own temporary change is undone here. onStop() also calls this, and
        // without the guard an expanded pane was collapsed — and the collapse persisted — every
        // time the user left the app, so the clock never came back.
        if (!mDockTuningRestoreExpandedStatus && !mSurfaceEditorExpandedStatusPane)
            return;
        if (mDockTuningRestoreExpandedStatus && mPreferences.isTopPaneClockCollapsed()) {
            setTopStatusBarCollapsed(false, false);
        } else if (!mDockTuningRestoreExpandedStatus && !mPreferences.isTopPaneClockCollapsed()) {
            // Editor closed from the status section: the pane was expanded only for its preview.
            setTopStatusBarCollapsed(true, false);
        }
    }

    private boolean isSurfaceTuningStatusSectionActive() {
        MaterialButtonToggleGroup sectionGroup = findViewById(R.id.surface_tuning_section_group);
        return sectionGroup != null
            && sectionGroup.getCheckedButtonId() == R.id.surface_tuning_section_status;
    }

    private abstract static class SimpleSeekBarChangeListener
        implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    public boolean isInAppKeyboardEnabled() {
        return mInAppKeyboard != null && mInAppKeyboard.isEnabled();
    }

    public void suppressSystemImeForInAppKeyboard() {
        if (isInAppKeyboardEnabled())
            mInAppKeyboard.suppressSystemIme();
    }

    /** Temporarily gives the toolbar EditText ownership of the system IME. */
    public void beginTerminalToolbarExternalTextInput(@NonNull EditText editText) {
        if (mInAppKeyboard != null && mInAppKeyboard.isEnabled()) {
            mInAppKeyboard.beginExternalTextInput();
        }
        onSystemImeRequested();
        editText.post(() -> {
            if (!editText.hasFocus())
                editText.requestFocus();
            KeyboardUtils.showSoftKeyboard(TermuxActivity.this, editText);
        });
    }

    /** Restores the embedded keyboard's prior visibility and system-IME suppression. */
    public void endTerminalToolbarExternalTextInput() {
        if (mInAppKeyboard != null)
            mInAppKeyboard.endExternalTextInput();
    }

    private final class InAppKeyboardActivityHost implements InAppKeyboardHost {

        @Override
        public View getKeyboardContainer() {
            return findViewById(R.id.inapp_keyboard_container);
        }

        @Override
        public void setKeyboardContainerVisible(boolean visible) {
            View keyboardContainer = getKeyboardContainer();
            if (keyboardContainer == null) {
                return;
            }
            if (visible) {
                boolean openingFromGone = keyboardContainer.getVisibility() == View.GONE;
                removeInAppKeyboardClosePreDrawCorrection();
                if (openingFromGone)
                    invalidateInAppKeyboardTransitionBackdropCrops();
                mPendingInAppKeyboardCloseGeometry = false;
                AccessoryRenderState state = buildAccessoryRenderState();
                boolean unifiedGlassSurface = shouldUseUnifiedDefaultKeyboardGlassSurface(state);
                boolean backdropReady = unifiedGlassSurface
                    ? isUnifiedAccessoryBackdropReady(state)
                    : isInAppKeyboardLocalBackdropReady(state);
                boolean deferReveal = mPendingInAppKeyboardOpenReveal
                    || shouldDeferInAppKeyboardReveal(openingFromGone,
                        isInAppKeyboardGlassSurface(), state.blurEnabled, backdropReady);
                mPendingInAppKeyboardOpenReveal = deferReveal;
                // INVISIBLE participates in destination layout without allowing a draw. The render
                // pass can therefore install the expanded crop before keys and their glass backing
                // become visible; non-unified surfaces keep the immediate path.
                keyboardContainer.setVisibility(deferReveal ? View.INVISIBLE : View.VISIBLE);
                applyInAppKeyboardSurfaceState(state);
                if (deferReveal) {
                    installInAppKeyboardOpenPreDrawGate();
                }
            } else {
                boolean closingToGone = keyboardContainer.getVisibility() != View.GONE;
                if (closingToGone)
                    invalidateInAppKeyboardTransitionBackdropCrops();
                mPendingInAppKeyboardOpenReveal = false;
                removeInAppKeyboardOpenPreDrawGate();
                mPendingInAppKeyboardCloseGeometry = closingToGone;
                keyboardContainer.setVisibility(View.GONE);
                hideKeybindHintPopup();
                if (closingToGone)
                    installInAppKeyboardClosePreDrawCorrection();
            }
        }

        @Override
        public void attachKeyboardView(View keyboardView) {
            FrameLayout host = findViewById(R.id.inapp_keyboard_view_host);
            if (host == null)
                return;
            ViewParent parent = keyboardView.getParent();
            if (parent instanceof ViewGroup)
                ((ViewGroup) parent).removeView(keyboardView);
            host.removeAllViews();
            host.addView(keyboardView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            mAttachedInAppKeyboardView = keyboardView;
            mDesiredInAppKeyboardHeightPx = 0;
            mInAppKeyboardHeightDirty = true;
        }

        @Override
        public void detachKeyboardView() {
            FrameLayout host = findViewById(R.id.inapp_keyboard_view_host);
            if (host != null)
                host.removeAllViews();
            mAttachedInAppKeyboardView = null;
            mDesiredInAppKeyboardHeightPx = 0;
            mInAppKeyboardHeightDirty = true;
        }

        @Override
        public void onKeyboardModifiersChanged(com.termux.app.terminal.inappkeyboard.TerminalModifiers modifiers) {
            updateKeybindHintPopup(modifiers);
        }

        @Override
        public void requestAccessoryGeometrySync() {
            requestInAppKeyboardGeometrySync();
        }

        @Override
        public void requestAccessoryGeometryPreviewSync() {
            // Slider events can arrive faster than display frames. Keep the latest renderer values,
            // but collapse measurement/layout/backdrop work into one update per frame.
            mDesiredInAppKeyboardHeightPx = 0;
            mInAppKeyboardMeasureWidthPx = 0;
            mInAppKeyboardAvailableHeightPx = 0;
            mInAppKeyboardHeightDirty = true;
            if (mInAppKeyboardPreviewGeometrySyncPosted)
                return;
            mInAppKeyboardPreviewGeometrySyncPosted = true;
            mAccessoryRenderHandler.postDelayed(
                mInAppKeyboardPreviewGeometrySyncRunnable, 16L);
        }

        @Override
        public void invalidateKeyboardMeasurement() {
            // The measurement cache is keyed by the available bounds, which do not change while
            // the user previews keyboard geometry. Explicitly invalidate both our cached result
            // and Android's same-spec measurement cache before the following geometry sync.
            mDesiredInAppKeyboardHeightPx = 0;
            mInAppKeyboardMeasureWidthPx = 0;
            mInAppKeyboardAvailableHeightPx = 0;
            mInAppKeyboardHeightDirty = true;
            if (mAttachedInAppKeyboardView != null)
                mAttachedInAppKeyboardView.forceLayout();
            View keyboardContainer = getKeyboardContainer();
            if (keyboardContainer != null)
                keyboardContainer.forceLayout();
        }

        @Override
        public void setKeyboardHeightAdjustmentVisible(boolean visible) {
            setInAppKeyboardHeightAdjustmentVisible(visible);
        }

        @Override
        public TerminalView getTerminalView() {
            // Route in-app keyboard input to the focused pane.
            return TermuxActivity.this.getTerminalView();
        }

        @Override
        public TerminalSession getCurrentSession() {
            return TermuxActivity.this.getCurrentSession();
        }

        @Override
        public void restoreLegacySoftKeyboardState() {
            if (mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.setSoftKeyboardState(false, true);
        }

        @Override
        public void onExternalTextInputStarted() {
            onSystemImeRequested();
        }

        @Override
        public void onExternalTextInputEnded() {
            resetInheritedImeLayoutState();
        }

        @Override
        public void runOnMain(Runnable runnable) {
            TermuxActivity.this.runOnUiThread(runnable);
        }

        @Override
        public void paste() {
            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(getCurrentSession());
        }

        @Override
        public void copySelection() {
            if (mTerminalView == null)
                return;
            String selectedText = mTerminalView.getSelectedText();
            if (DataUtils.isNullOrEmpty(selectedText))
                selectedText = mTerminalView.getStoredSelectedText();
            if (!DataUtils.isNullOrEmpty(selectedText))
                ShareUtils.copyTextToClipboard(TermuxActivity.this, selectedText);
        }

        @Override
        public void selectAll() {
            if (mTerminalView == null)
                return;
            mTerminalView.selectAllText();
        }

        @Override
        public boolean prepareCut() {
            if (mTerminalView == null)
                return false;
            String selectedText = mTerminalView.getSelectedText();
            if (DataUtils.isNullOrEmpty(selectedText))
                selectedText = mTerminalView.getStoredSelectedText();
            if (!DataUtils.isNullOrEmpty(selectedText)) {
                ShareUtils.copyTextToClipboard(TermuxActivity.this, selectedText);
                mTerminalView.stopTextSelectionMode();
                return false;
            }
            String currentInput = mTerminalView.getCurrentInput();
            if (!DataUtils.isNullOrEmpty(currentInput))
                ShareUtils.copyTextToClipboard(TermuxActivity.this, currentInput);
            // Ctrl+U is the terminal-native cut for the current prompt line. Sending it even when
            // the heuristic input reader returns empty also clears shells with custom prompts.
            return true;
        }

        @Override
        public void requestTextLayout() {
            if (mInAppKeyboard != null)
                mInAppKeyboard.requestTextLayout();
        }

        @Override
        public void requestNumericLayout() {
            if (mInAppKeyboard != null)
                mInAppKeyboard.requestNumericLayout();
        }

        @Override
        public void requestGreekMathLayout() {
            if (mInAppKeyboard != null)
                mInAppKeyboard.requestGreekMathLayout();
        }

        @Override
        public void requestForwardLayout() {
            if (mInAppKeyboard != null)
                mInAppKeyboard.requestForwardLayout();
        }

        @Override
        public void requestBackwardLayout() {
            if (mInAppKeyboard != null)
                mInAppKeyboard.requestBackwardLayout();
        }

        @Override
        public void openKeyboardSettings() {
            ActivityUtils.startActivity(TermuxActivity.this,
                SettingsActivity.createFragmentIntent(TermuxActivity.this,
                    com.termux.app.fragments.settings.termux.KeyboardPreferencesFragment.class,
                    R.string.termux_keyboard_preferences_title));
        }

        @Override
        public void hideKeyboard() {
            if (mInAppKeyboard != null)
                mInAppKeyboard.hide(TermuxInAppKeyboard.HideReason.USER_EVENT);
        }

        @Override
        public void requestVoiceTyping(boolean chooser) {
            launchVoiceTyping(chooser);
        }

        @Override
        public void setComposePending(boolean pending) {
            if (mAttachedInAppKeyboardView instanceof Keyboard2View)
                ((Keyboard2View) mAttachedInAppKeyboardView).set_compose_pending(pending);
        }

        @Override
        public void toggleCapsLock() {
            mInAppKeyboardShiftLocked = !mInAppKeyboardShiftLocked;
            if (mAttachedInAppKeyboardView instanceof Keyboard2View)
                ((Keyboard2View) mAttachedInAppKeyboardView).setShiftLocked(mInAppKeyboardShiftLocked);
        }

        @Override
        public void runLauncherTool(String toolId) {
            if (mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.runLauncherTool(toolId);
        }

        @Override
        public void debugLog(String message) {
            Logger.logDebug(LOG_TAG, message);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setInAppKeyboardHeightAdjustmentVisible(boolean visible) {
        View controls = findViewById(R.id.inapp_keyboard_height_adjust_controls);
        View handle = findViewById(R.id.inapp_keyboard_height_adjust_handle);
        View handleIndicator = findViewById(
            R.id.inapp_keyboard_height_adjust_handle_indicator);
        View confirm = findViewById(R.id.inapp_keyboard_height_adjust_confirm);
        View cancel = findViewById(R.id.inapp_keyboard_height_adjust_cancel);
        TextView spacingLabel = findViewById(R.id.inapp_keyboard_key_spacing_label);
        SeekBar spacingSlider = findViewById(R.id.inapp_keyboard_key_spacing_slider);
        TextView radiusLabel = findViewById(R.id.inapp_keyboard_key_corner_radius_label);
        SeekBar radiusSlider = findViewById(
            R.id.inapp_keyboard_key_corner_radius_slider);
        if (controls == null || handle == null || handleIndicator == null
            || confirm == null || cancel == null || spacingLabel == null
            || spacingSlider == null || radiusLabel == null || radiusSlider == null)
            return;
        controls.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            handle.setOnTouchListener(null);
            confirm.setOnClickListener(null);
            cancel.setOnClickListener(null);
            spacingSlider.setOnSeekBarChangeListener(null);
            radiusSlider.setOnSeekBarChangeListener(null);
            return;
        }

        if (isInAppKeyboardGlassSurface()) {
            // The glass keyboard is transparent; give the controls the same tint so
            // they stay readable over the terminal instead of inheriting a transparent background.
            controls.setBackground(buildDockGlassSurface(
                mPreferences != null ? mPreferences.getAppBarOpacity() / 100f : 1f));
        } else {
            controls.setBackgroundColor(resolveInAppKeyboardBackgroundColor());
        }
        if (mAttachedInAppKeyboardView instanceof Keyboard2View) {
            int controlColor = ((Keyboard2View) mAttachedInAppKeyboardView)
                .getKeyboardLabelColor();
            ColorStateList controlTint = ColorStateList.valueOf(controlColor);
            handleIndicator.setBackgroundColor(controlColor);
            ((TextView) confirm).setTextColor(controlColor);
            ((TextView) cancel).setTextColor(controlColor);
            spacingLabel.setTextColor(controlColor);
            radiusLabel.setTextColor(controlColor);
            spacingSlider.setProgressTintList(controlTint);
            spacingSlider.setThumbTintList(controlTint);
            radiusSlider.setProgressTintList(controlTint);
            radiusSlider.setThumbTintList(controlTint);
        }
        spacingSlider.setMax(Math.round(
            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE
                * IN_APP_KEYBOARD_MARGIN_SLIDER_STEPS_PER_UNIT));
        spacingSlider.setProgress(Math.round(mInAppKeyboard.getKeyMarginScale()
            * IN_APP_KEYBOARD_MARGIN_SLIDER_STEPS_PER_UNIT));
        radiusSlider.setMax(Math.round(
            TermuxPreferenceConstants.TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP
                * IN_APP_KEYBOARD_RADIUS_SLIDER_STEPS_PER_DP));
        radiusSlider.setProgress(Math.round(mInAppKeyboard.getEffectiveKeyCornerRadiusDp()
            * IN_APP_KEYBOARD_RADIUS_SLIDER_STEPS_PER_DP));
        spacingSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mInAppKeyboard != null)
                    mInAppKeyboard.previewKeyMarginScale(
                        progress / (float) IN_APP_KEYBOARD_MARGIN_SLIDER_STEPS_PER_UNIT);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        radiusSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mInAppKeyboard != null)
                    mInAppKeyboard.previewKeyCornerRadiusDp(
                        progress / (float) IN_APP_KEYBOARD_RADIUS_SLIDER_STEPS_PER_DP);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        confirm.setOnClickListener(view -> {
            if (mInAppKeyboard != null) {
                mInAppKeyboard.confirmHeightAdjustment();
                // Match termux-reload-settings exactly. That command omits the recreate extra,
                // whose default is true; the full activity rebuild is what clears the stale
                // exact accessory height left behind by a downward keyboard resize.
                reloadActivityStyling(true);
            }
        });
        cancel.setOnClickListener(view -> {
            if (mInAppKeyboard != null)
                mInAppKeyboard.cancelHeightAdjustment();
        });
        handle.setOnTouchListener((view, event) -> {
            if (mInAppKeyboard == null || !mInAppKeyboard.isHeightAdjusting())
                return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mInAppKeyboardHeightDragStartY = event.getRawY();
                    mInAppKeyboardHeightDragStartScale = mInAppKeyboard.getHeightScale();
                    int renderedHeight = mAttachedInAppKeyboardView == null
                        ? 0 : mAttachedInAppKeyboardView.getMeasuredHeight();
                    mInAppKeyboardUnscaledDragHeight = Math.max(1f,
                        renderedHeight / mInAppKeyboardHeightDragStartScale);
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float scale = TermuxInAppKeyboard.calculateHeightScaleForDrag(
                        mInAppKeyboardHeightDragStartScale,
                        event.getRawY() - mInAppKeyboardHeightDragStartY,
                        mInAppKeyboardUnscaledDragHeight);
                    mInAppKeyboard.previewHeightScale(scale);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void requestInAppKeyboardGeometrySync() {
        View keyboardContainer = findViewById(R.id.inapp_keyboard_container);
        mDesiredInAppKeyboardHeightPx = 0;
        mInAppKeyboardHeightDirty = true;
        if (keyboardContainer != null)
            keyboardContainer.requestLayout();
        applyAccessoryGeometryIfNeeded(true, "inapp-keyboard");
        if (keyboardContainer != null) {
            keyboardContainer.post(() -> {
                if (!isFinishing() && !isDestroyed())
                    applyAccessoryGeometryIfNeeded(true, "inapp-keyboard:layout");
            });
        }
    }

    private void setTermuxSessionsListView() {
        ListView termuxSessionsListView = findViewById(R.id.terminal_sessions_list);
        // Backed by the filtered drawer list (excludes secondary panes) rather than the raw
        // service session list, so panes don't show up as their own sessions.
        rebuildDrawerSessions();
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mDrawerSessions);
        termuxSessionsListView.setAdapter(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        termuxSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);
    }

    private void setTerminalToolbarView(Bundle savedInstanceState) {
        rebuildExtraKeysPageClients();
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;
        updateAppLauncherBarHeight();
        setTerminalToolbarHeight();
        configureExtraKeysBackground();
        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);
        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
        scheduleAccessoryRenderSync("setTerminalToolbarView");
    }

    private void updateAppLauncherBarHeight() {
        if (mPreferences == null)
            return;
        applyDockLayoutMetrics(buildDockLayoutMetrics(0));
    }

    private boolean isLandscapeOrientation() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private static final float DOCK_RAIL_MIN_WIDTH_DP = 52f;
    /**
     * Breathing room between a rail icon and the display edge it is docked to, on top of whatever
     * cutout inset that edge already carries. Without it the icons sat against the glass — and on
     * the rounded corners, partly under it.
     */
    private static final float DOCK_RAIL_EDGE_MARGIN_DP = 10f;
    private static final float DOCK_RAIL_ICON_SIZE_DP = 38f;
    private static final float DOCK_RAIL_ICON_SPACING_DP = 10f;

    /** Horizontal display-cutout insets from the last insets pass; the rail never draws narrower. */
    private int mLastDisplayCutoutInsetLeft;
    private int mLastDisplayCutoutInsetRight;
    /** Navigation-bar inset, so the rail's scroll range can clear it at the bottom. */
    private int mLastNavigationBarInsetBottom;

    private boolean isDockRailActive() {
        return isLandscapeOrientation()
            && mPreferences != null
            && mPreferences.isAppLauncherAppsRowEnabled();
    }

    private boolean isDockRailOnRight() {
        return mPreferences != null && mPreferences.isAppLauncherDockRailOnRight();
    }

    /**
     * Which way the app drawer is pulled off the rail: away from the edge it is docked to. Portrait
     * has no rail, and the drawer is pulled down off the apps row there instead.
     */
    @NonNull
    private AppDrawerGestureArbiter.Pull resolveDockRailPull() {
        if (!isDockRailActive())
            return AppDrawerGestureArbiter.Pull.NONE;
        return isDockRailOnRight()
            ? AppDrawerGestureArbiter.Pull.LEFT : AppDrawerGestureArbiter.Pull.RIGHT;
    }

    /**
     * The rail's column: the docked edge's cutout inset <em>plus</em> a column wide enough for an
     * icon and its two margins. It used to be the larger of the two, which on a device whose cutout
     * inset is small left the icons hard against the display edge, and left the content column
     * inset by no more than the icons themselves — so on the right-hand rail the terminal's own text
     * ran underneath them.
     */
    private int resolveDockRailWidthPx() {
        return resolveDockRailEdgeInsetPx() + Math.max(Math.round(dpToPx(DOCK_RAIL_MIN_WIDTH_DP)),
            Math.round(dpToPx(DOCK_RAIL_ICON_SIZE_DP + 2 * DOCK_RAIL_EDGE_MARGIN_DP)));
    }

    /** The cutout inset on the edge the rail is docked to; zero on a device without one there. */
    private int resolveDockRailEdgeInsetPx() {
        return isDockRailOnRight() ? mLastDisplayCutoutInsetRight : mLastDisplayCutoutInsetLeft;
    }

    /**
     * The rail's half of the app-drawer pull. Only four of the nine vetoes have a rail equivalent:
     * the rail carries no search field, no A-Z scrub and no long-press pickup, so those slots are
     * permanently clear, and the pull direction is the one veto that is not a boolean.
     */
    private final DockRailScrollView.DrawerPullListener mDockRailDrawerPullListener =
        new DockRailScrollView.DrawerPullListener() {
            @NonNull
            @Override
            public AppDrawerGestureArbiter.Eligibility captureDrawerEligibility() {
                return new AppDrawerGestureArbiter.Eligibility(
                    mPreferences != null && mPreferences.isAppLauncherDrawerEnabled(),
                    true,
                    true,
                    resolveDockRailPull(),
                    !mDockTuningMode,
                    !isCommandPaletteOpen(),
                    true,
                    !isAppDrawerEngaged(),
                    !isFullStatusBarEngaged());
            }

            @Override
            public void onDrawerDragBegin(float downPull) {
                getAppDrawerController().beginDrag(downPull);
            }

            @Override
            public void onDrawerDrag(float pull) {
                getAppDrawerController().updateDrag(pull);
            }

            @Override
            public void onDrawerDragEnd(float velocityPxPerSec) {
                getAppDrawerController().endDrag(velocityPxPerSec);
            }

            @Override
            public void onDrawerDragCancel() {
                getAppDrawerController().cancelDrag();
            }
        };

    /**
     * Rebuilds the landscape dock rail: the pinned dock apps as a vertical icon column that owns
     * one screen edge (the horizontal dock rows stay collapsed in landscape). The rail sits beside
     * the padded content root, so it lives in the same column the terminal is inset from.
     */
    private void updateDockRailView() {
        DockRailScrollView railScroll = findViewById(R.id.dock_rail_scroll);
        LinearLayout railList = findViewById(R.id.dock_rail_list);
        if (railScroll == null || railList == null)
            return;
        if (!isDockRailActive() || mSuggestionBarView == null) {
            railScroll.setDrawerPullListener(null);
            railScroll.setVisibility(View.GONE);
            railList.removeAllViews();
            return;
        }
        int railWidthPx = resolveDockRailWidthPx();
        ViewGroup.LayoutParams scrollParams = railScroll.getLayoutParams();
        if (scrollParams != null && scrollParams.width != railWidthPx) {
            scrollParams.width = railWidthPx;
            railScroll.setLayoutParams(scrollParams);
        }
        if (scrollParams instanceof FrameLayout.LayoutParams) {
            int gravity = (isDockRailOnRight() ? Gravity.END : Gravity.START) | Gravity.TOP;
            FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) scrollParams;
            if (frameParams.gravity != gravity) {
                frameParams.gravity = gravity;
                railScroll.setLayoutParams(frameParams);
            }
        }
        railScroll.setDrawerPullListener(mDockRailDrawerPullListener);
        // Padded on all four sides rather than only vertically: the docked edge carries its cutout
        // inset plus a margin, and the scroll range clears the status and navigation bars, so the
        // first and last icons cannot end up under a system bar when the rail is scrolled.
        int verticalPadPx = Math.round(dpToPx(10));
        int edgeMarginPx = Math.round(dpToPx(DOCK_RAIL_EDGE_MARGIN_DP));
        int dockedEdgePadPx = resolveDockRailEdgeInsetPx() + edgeMarginPx;
        railScroll.setPadding(isDockRailOnRight() ? edgeMarginPx : dockedEdgePadPx,
            mLastStatusBarInsetTop + verticalPadPx,
            isDockRailOnRight() ? dockedEdgePadPx : edgeMarginPx,
            mLastNavigationBarInsetBottom + verticalPadPx);
        railScroll.setClipToPadding(false);
        railList.removeAllViews();
        int iconSizePx = Math.round(dpToPx(DOCK_RAIL_ICON_SIZE_DP));
        int spacingPx = Math.round(dpToPx(DOCK_RAIL_ICON_SPACING_DP));
        for (com.termux.app.launcher.model.LauncherAppEntry entry
                : mSuggestionBarView.getDockRailEntries()) {
            if (entry.icon == null)
                continue;
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(entry.icon);
            iconView.setContentDescription(entry.label);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
            iconParams.topMargin = spacingPx;
            iconParams.bottomMargin = spacingPx;
            railList.addView(iconView, iconParams);
            iconView.setOnClickListener(v -> mSuggestionBarView.launchEntryFromRail(entry, v));
        }
        railScroll.setVisibility(railList.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * Ceiling for the accessory stack: whatever the window holds minus the status inset, the
     * window bar, and a minimum usable terminal slice. Returns MAX_VALUE before first layout.
     */
    private int computeMaxAccessoryStackHeightPx(int accessoryBottomMarginPx) {
        // The root relative layout already sits below the status-bar inset, so only the window bar
        // and the reserved terminal slice come out of its height.
        View root = findViewById(R.id.activity_termux_root_relative_layout);
        int rootHeightPx = root != null ? root.getHeight() : 0;
        if (rootHeightPx <= 0)
            return Integer.MAX_VALUE;
        View windowBarHost = findViewById(R.id.terminal_window_bar_host);
        int windowBarPx = windowBarHost != null && windowBarHost.getVisibility() == View.VISIBLE
            ? windowBarHost.getHeight() : 0;
        int minTerminalPx = Math.round(dpToPx(72));
        return Math.max(0, rootHeightPx - windowBarPx - minTerminalPx
            - Math.max(0, accessoryBottomMarginPx));
    }

    public void setTerminalToolbarHeight() {
        setTerminalToolbarHeight(true);
    }

    private void setTerminalToolbarHeight(boolean requestTerminalResize) {
        // Frozen while the app drawer plane is engaged: this path resizes the toolbar and the
        // terminal, and driving it per animation frame is a SIGWINCH storm. Replayed on close.
        if (isAppDrawerEngaged()) {
            mAppDrawerGeometryFreezePending = true;
            return;
        }
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        View accessoryStackContainer = findViewById(R.id.accessory_stack_container);
        if (terminalToolbarViewPager == null || accessoryStackContainer == null)
            return;
        ViewGroup.LayoutParams toolbarLayoutParams = terminalToolbarViewPager.getLayoutParams();

        int matrix = 0;
        if (mTermuxTerminalExtraKeys != null && mTermuxTerminalExtraKeys.getExtraKeysInfo() != null) {
            matrix = mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;
        }

        int measuredToolbarHeightPx = AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(
            Math.round(mTerminalToolbarDefaultHeight),
            matrix,
            mProperties.getTerminalToolbarHeightScaleFactor()
        );
        AccessoryRenderState state = buildAccessoryRenderState();
        int toolbarHeightPx = state.extraKeysRowEnabled ? measuredToolbarHeightPx : 0;
        toolbarLayoutParams.height = toolbarHeightPx;
        terminalToolbarViewPager.setLayoutParams(toolbarLayoutParams);

        DockLayoutMetrics dockMetrics = buildDockLayoutMetrics(0);
        int accessoryBottomMarginPx = resolveAccessoryStackBottomMarginPx(state);
        // The stack has no natural ceiling: dock rows + keyboard can otherwise consume the whole
        // window and crush the terminal and its window bar to zero height. Shed the overflow from
        // the apps row (its icons rescale to the row-height hint); the keyboard's own screen-share
        // cap bounds the rest.
        int maxAccessoryStackPx = computeMaxAccessoryStackHeightPx(accessoryBottomMarginPx);
        int projectedStackPx = computeAccessoryStackHeight(
            dockMetrics.combinedHeight(toolbarHeightPx, state.extraKeysRowEnabled),
            0, state.keyboardHeight);
        if (projectedStackPx > maxAccessoryStackPx) {
            dockMetrics = buildDockLayoutMetrics(-(projectedStackPx - maxAccessoryStackPx));
        }
        applyDockLayoutMetrics(dockMetrics);
        int dockContentHeightPx = state.toolbarShown
            ? dockMetrics.combinedHeight(toolbarHeightPx, state.extraKeysRowEnabled) : 0;
        int accessoryContentHeightPx = computeAccessoryStackHeight(
            dockContentHeightPx, 0, state.keyboardHeight);
        // The embedded keyboard suspends flush absorption: its height is user-scaled and its
        // surface defines its own boundary, so the split remainder halves would surface as
        // wallpaper bands above the gesture-navigation inset instead of hiding in dock glass.
        int terminalFlushPaddingPx = state.keyboardShown || !state.toolbarShown ? 0
            : resolveTerminalFlushDockPaddingPx(accessoryContentHeightPx, accessoryBottomMarginPx);
        mAppliedTerminalFlushPaddingPx = terminalFlushPaddingPx;
        int combinedHeight = computeAccessoryStackHeight(
            dockContentHeightPx, terminalFlushPaddingPx, state.keyboardHeight);
        // Split the absorbed remainder around the dock rows so they stay visually centered in the
        // taller glass instead of hugging its bottom edge.
        int flushBottomInsetPx = terminalFlushPaddingPx / 2;
        if (accessoryStackContainer.getPaddingBottom() != flushBottomInsetPx) {
            accessoryStackContainer.setPadding(
                accessoryStackContainer.getPaddingLeft(),
                accessoryStackContainer.getPaddingTop(),
                accessoryStackContainer.getPaddingRight(),
                flushBottomInsetPx);
        }
        boolean accessoryHeightChanged = updateAccessoryStackContainerHeight(accessoryStackContainer, combinedHeight);
        boolean accessoryMarginChanged = updateAccessoryStackContainerBottomMargin(
            accessoryStackContainer,
            accessoryBottomMarginPx
        );
        boolean keyboardShownChanged = state.keyboardShown != mAppliedInAppKeyboardShown;
        mAppliedInAppKeyboardShown = state.keyboardShown;
        if (shouldRequestTerminalResize(requestTerminalResize, accessoryHeightChanged,
            accessoryMarginChanged, keyboardShownChanged) && mTerminalView != null) {
            mTerminalView.post(mTerminalView::updateSize);
        }
        scheduleAccessoryRenderSync("setTerminalToolbarHeight");
    }

    static boolean shouldRequestTerminalResize(boolean requested, boolean heightChanged,
                                               boolean marginChanged, boolean keyboardShownChanged) {
        return requested && (heightChanged || marginChanged || keyboardShownChanged);
    }

    private int resolveTerminalFlushDockPaddingPx(int accessoryContentHeightPx,
                                                  int accessoryBottomMarginPx) {
        // Always-on since the flush-dock toggle was removed from Screen settings: the stored
        // preference (possibly still false from the short-lived toggle) must not gate this,
        // or the sub-line remainder band above the dock returns with no UI to fix it.
        if (mTerminalView == null || mTerminalView.mRenderer == null
            || mTerminalView.getWidth() <= 0 || mTerminalView.getHeight() <= 0)
            return mAppliedTerminalFlushPaddingPx;
        int fontLineSpacingPx = mTerminalView.mRenderer.getFontLineSpacing();
        if (fontLineSpacingPx <= 0)
            return mAppliedTerminalFlushPaddingPx;
        View rootRelativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        if (rootRelativeLayout == null || rootRelativeLayout.getHeight() <= 0)
            return mAppliedTerminalFlushPaddingPx;
        // Structural base: the height the terminal will settle at once all accessory content
        // (without any flush padding) is laid out. Deliberately NOT derived from the terminal's current
        // height — mid-relayout passes (e.g. multi-row extra keys toggling with the IME) would
        // feed the previously applied padding back in and make the result oscillate.
        rootRelativeLayout.getLocationInWindow(mTmpViewLocation);
        int accessoryBottomWindowY = mTmpViewLocation[1] + rootRelativeLayout.getHeight() - accessoryBottomMarginPx;
        mTerminalView.getLocationInWindow(mTmpViewLocation);
        int terminalTopWindowY = mTmpViewLocation[1];
        int baseTerminalHeightPx = accessoryBottomWindowY - accessoryContentHeightPx - terminalTopWindowY;
        if (baseTerminalHeightPx <= 0)
            return mAppliedTerminalFlushPaddingPx;
        int availableTerminalHeightPx = Math.max(0,
            baseTerminalHeightPx - mTerminalView.mRenderer.getFontLineSpacingAndAscent());
        return availableTerminalHeightPx % fontLineSpacingPx;
    }

    public void requestTerminalFlushDockGeometryUpdate() {
        if (mTerminalView == null)
            return;
        mTerminalView.post(() -> {
            if (!isFinishing() && !isDestroyed())
                applyAccessoryGeometryIfNeeded(true, "terminal:metrics");
        });
    }

    private boolean updateAccessoryStackContainerHeight(View view, int height) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams == null)
            return false;
        if (layoutParams.height == height)
            return false;
        layoutParams.height = height;
        view.setLayoutParams(layoutParams);
        return true;
    }

    private boolean updateAccessoryStackContainerBottomMargin(View view, int marginBottom) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams))
            return false;
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginParams.bottomMargin == marginBottom)
            return false;
        marginParams.bottomMargin = marginBottom;
        view.setLayoutParams(marginParams);
        return true;
    }

    private int resolveAccessoryStackBottomMarginPx(@NonNull AccessoryRenderState state) {
        if (!state.toolbarShown && !state.keyboardShown)
            return 0;
        // The embedded keyboard is an ordinary bottom child. Root/decor inset policy already keeps
        // it above navigation bars, so a floating-dock gap must not be inserted beneath it.
        if (state.keyboardShown)
            return mImeLiftPx;
        // The capsule floats, so it keeps its bottom gap even when the keyboard is up — otherwise it
        // sits flush against the keyboard. Non-capsule styles stay flush.
        if (!isRoundedDockStyle()) {
            return mImeLiftPx;
        }
        return mImeLiftPx + resolveDockCapsuleBottomGapPx();
    }

    // Kept for test compatibility and to preserve existing RelativeLayout params in-place.
    private void updateExtraKeysBackgroundHeight(View view, int height) {
        updateAccessoryStackContainerHeight(view, height);
    }

    private void updateViewHeight(int viewId, int height) {
        View view = findViewById(viewId);
        if (view == null)
            return;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams == null)
            return;
        layoutParams.height = height;
        view.setLayoutParams(layoutParams);
    }

    private void updateViewBottomMargin(int viewId, int marginBottom) {
        View view = findViewById(viewId);
        if (view == null) return;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginParams.bottomMargin == marginBottom) return;
        marginParams.bottomMargin = marginBottom;
        view.setLayoutParams(marginParams);
    }

    private void updateViewHorizontalMargins(int viewId, int marginHorizontal) {
        View view = findViewById(viewId);
        if (view == null) return;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginParams.leftMargin == marginHorizontal && marginParams.rightMargin == marginHorizontal) return;
        marginParams.leftMargin = marginHorizontal;
        marginParams.rightMargin = marginHorizontal;
        view.setLayoutParams(marginParams);
    }

    private void updateViewPadding(int viewId, int left, int top, int right, int bottom) {
        View view = findViewById(viewId);
        if (view == null) return;
        if (view.getPaddingLeft() == left && view.getPaddingTop() == top &&
            view.getPaddingRight() == right && view.getPaddingBottom() == bottom) {
            return;
        }
        view.setPadding(left, top, right, bottom);
    }

    private void applyDockRowHorizontalInsets() {
        int surfaceInset = resolveDockHorizontalInsetPx();
        int contentInset = isRoundedDockStyle() ? resolveDockCapsuleContentInsetPx() : surfaceInset;
        int extraKeysInset = isRoundedDockStyle()
            ? resolveDockCapsuleExtraKeysInsetPx() : surfaceInset;
        int appsTopPadding = isRoundedDockStyle() ? resolveDockCapsuleAppsTopPaddingPx() : resolveDefaultDockAppsTopPaddingPx();
        int appsBottomPadding = isRoundedDockStyle() ? resolveDockCapsuleAppsBottomPaddingPx() : resolveDefaultDockAppsBottomPaddingPx();
        // The apps row reads with more side padding than the A–Z row because its icons are
        // space-between (half a slot of empty space at each edge). Trim the apps-row inset ~18%
        // so the icons sit closer to the edges and line up better with the A–Z row's letter span.
        int appsContentInset = Math.round(contentInset * 0.82f);

        updateViewHorizontalMargins(R.id.apps_bar_viewpager, appsContentInset);
        updateViewHorizontalMargins(R.id.apps_bar_indicator_band, contentInset);
        updateViewHorizontalMargins(R.id.apps_bar_az_row, contentInset);
        updateViewHorizontalMargins(R.id.terminal_toolbar_view_pager, extraKeysInset);
        updateViewHorizontalMargins(R.id.extrakeys_divider, extraKeysInset);
        updateViewPadding(R.id.apps_bar_viewpager, 0, appsTopPadding, 0, appsBottomPadding);
        updateViewHorizontalMargins(R.id.apps_bar_az_fx_underlay, surfaceInset);
        updateViewHorizontalMargins(R.id.apps_bar_az_fx_overlay, surfaceInset);

        // Keep the extra-keys ViewPager from leaking its adjacent (text-input) page. In capsule mode
        // the pager is inset from the dock edges, so the off-screen page's edge (the "❮" button)
        // peeked into the visible page. clipChildren alone doesn't clip a ViewPager's off-screen
        // pages, so hard-clip the pager to its own rectangular bounds via clipToOutline.
        View toolbarPager = findViewById(R.id.terminal_toolbar_view_pager);
        if (toolbarPager instanceof ViewGroup) {
            ((ViewGroup) toolbarPager).setClipChildren(true);
            ((ViewGroup) toolbarPager).setClipToPadding(true);
        }
        if (toolbarPager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            toolbarPager.setOutlineProvider(ViewOutlineProvider.BOUNDS);
            toolbarPager.setClipToOutline(true);
        }
    }

    private int resolveDockAppsBarHeightHintPx(int appsBarHeightPx) {
        int appsTopPadding = isRoundedDockStyle() ? resolveDockCapsuleAppsTopPaddingPx() : resolveDefaultDockAppsTopPaddingPx();
        int appsBottomPadding = isRoundedDockStyle() ? resolveDockCapsuleAppsBottomPaddingPx() : resolveDefaultDockAppsBottomPaddingPx();
        return Math.max(0, appsBarHeightPx - appsTopPadding - appsBottomPadding);
    }

    private int getDockBaseToolbarHeightPx() {
        if (mTerminalToolbarDefaultHeight > 0) {
            return Math.round(mTerminalToolbarDefaultHeight);
        }
        return Math.round(getResources().getDisplayMetrics().density * 37.5f);
    }

    @NonNull
    private DockLayoutMetrics buildDockLayoutMetrics(int additionalAppsBarHeightPx) {
        if (mPreferences == null) {
            return new DockLayoutMetrics(0, 0, 0, 0);
        }

        float density = getResources().getDisplayMetrics().density;
        float barHeightScale = mPreferences.getAppLauncherBarHeightScale();
        float normalizedScale = resolveDockSizeProgress(barHeightScale);
        float defaultDockProgress = resolveDefaultDockSizeProgress(barHeightScale);
        boolean appsRowEnabled = mPreferences.isAppLauncherAppsRowEnabled()
            && !isLandscapeOrientation();
        int appsBarHeightPx = appsRowEnabled
            ? resolveDockAppsBarHeightPx(normalizedScale, defaultDockProgress,
                Math.max(0, additionalAppsBarHeightPx))
            : 0;

        boolean azEnabled = mPreferences.isAppLauncherAzRowEnabled()
            && !isLandscapeOrientation();
        int azRowHeightPx = AccessoryStackLayoutPolicy.computeAzRowHeightPx(azEnabled, density);
        int indicatorBandHeightPx = AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(
            appsRowEnabled && azEnabled, density);

        int interRowGapPx = indicatorBandHeightPx;

        return new DockLayoutMetrics(appsBarHeightPx, indicatorBandHeightPx, azRowHeightPx, interRowGapPx);
    }

    /**
     * Keeps the old row/icon result as each preset's baseline, then allocates enough extra row
     * height for the new icon curve. This makes the requested icon-size bump real in pixels while
     * preserving the smallest preset and the fixed A-Z/extra-keys heights.
     */
    private int resolveDockAppsBarHeightPx(float normalizedScale, float defaultDockProgress,
                                           int additionalAppsBarHeightPx) {
        boolean capsule = isRoundedDockStyle();
        float baselineHeightFactor = capsule
            ? (1.12f + (normalizedScale * 0.60f))
            : (1.00f + (defaultDockProgress * 0.52f));
        int baselineRowHeightPx = Math.round(getDockBaseToolbarHeightPx() * baselineHeightFactor);
        int verticalPaddingPx = capsule
            ? resolveDockCapsuleAppsTopPaddingPx() + resolveDockCapsuleAppsBottomPaddingPx()
            : resolveDefaultDockAppsTopPaddingPx() + resolveDefaultDockAppsBottomPaddingPx();
        int twoDpPx = Math.round(dpToPx(2));
        int minUsablePx = Math.round(dpToPx(24));
        int baselineHintPx = Math.max(0, baselineRowHeightPx - verticalPaddingPx);
        int baselineUsablePx = Math.max(minUsablePx, baselineHintPx - twoDpPx);

        float baselineIconScale = capsule
            ? (1.52f + (normalizedScale * 0.76f))
            : (1.08f + (defaultDockProgress * 0.42f));
        float targetIconScale = capsule
            ? resolveCapsuleDockIconScaleForProgress(normalizedScale)
            : resolveDefaultDockIconScaleForProgress(defaultDockProgress);
        float requestedIconGrowth = targetIconScale / Math.max(0.0001f, baselineIconScale);
        if (Math.abs(requestedIconGrowth - 1f) < 0.0001f) {
            return Math.max(0, baselineRowHeightPx + additionalAppsBarHeightPx);
        }

        int baselineIconPx = Math.round(baselineUsablePx
            * AccessoryStackLayoutPolicy.computeDockIconFillRatio(baselineIconScale));
        int targetIconPx = Math.max(1, Math.round(baselineIconPx * requestedIconGrowth));
        float targetFill = AccessoryStackLayoutPolicy.computeDockIconFillRatio(targetIconScale);
        int targetUsablePx = Math.max(minUsablePx, Math.round(targetIconPx / targetFill));
        return Math.max(0, targetUsablePx + twoDpPx + verticalPaddingPx + additionalAppsBarHeightPx);
    }

    private float resolveDockSizeProgress(float barHeightScale) {
        return Math.max(0f, Math.min(1f, (barHeightScale - 1.45f) / (2.45f - 1.45f)));
    }

    private float resolveDefaultDockSizeProgress(float barHeightScale) {
        float progress = resolveDockSizeProgress(barHeightScale) + DEFAULT_DOCK_SIZE_PRESET_SHIFT;
        return Math.max(0f, Math.min(DEFAULT_DOCK_SIZE_MAX_PROGRESS, progress));
    }

    private float resolveDerivedDockIconScale() {
        if (mPreferences == null) {
            return 1.36f;
        }
        float barHeightScale = mPreferences.getAppLauncherBarHeightScale();
        float normalized = resolveDockSizeProgress(barHeightScale);
        if (isRoundedDockStyle()) {
            return resolveCapsuleDockIconScaleForProgress(normalized);
        }
        float defaultDockProgress = resolveDefaultDockSizeProgress(barHeightScale);
        return resolveDefaultDockIconScaleForProgress(defaultDockProgress);
    }

    static float resolveDefaultDockIconScaleForProgress(float defaultDockProgress) {
        return AccessoryStackLayoutPolicy.interpolatePresetCurve(defaultDockProgress,
            DEFAULT_DOCK_ICON_PROGRESS_POINTS, DEFAULT_DOCK_ICON_SCALE_POINTS);
    }

    static float resolveCapsuleDockIconScaleForProgress(float normalizedProgress) {
        return AccessoryStackLayoutPolicy.interpolatePresetCurve(normalizedProgress,
            CAPSULE_DOCK_ICON_PROGRESS_POINTS, CAPSULE_DOCK_ICON_SCALE_POINTS);
    }

    private void applyDockLayoutMetrics(@NonNull DockLayoutMetrics metrics) {
        updateViewHeight(R.id.apps_bar_viewpager, metrics.appsBarHeightPx);
        updateViewHeight(R.id.apps_bar_indicator_band, metrics.indicatorBandHeightPx);
        updateViewHeight(R.id.apps_bar_az_row, metrics.azRowHeightPx);
        updateViewBottomMargin(R.id.apps_bar_viewpager, 0);
        applyDockRowHorizontalInsets();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.setDockRowHeightHintPx(resolveDockAppsBarHeightHintPx(metrics.appsBarHeightPx));
        }
    }

    public void toggleTerminalToolbar() {
        boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar), true);

        configureExtraKeysBackground();
        scheduleAccessoryRenderSync("toggleTerminalToolbar");

        isToolbarHidden = !showNow;
    
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }
    
    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null)
            return;
        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty())
                savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }

    private void setSettingsButtonView() {
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        View newSessionButton = findViewById(R.id.new_session_button);
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            promptNewSession();
            return true;
        });
    }

    private void setToggleKeyboardView() {
        findViewById(R.id.toggle_keyboard_button).setOnClickListener(v -> {
            mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
            getDrawer().closeDrawers();
        });
        findViewById(R.id.toggle_keyboard_button).setOnLongClickListener(v -> {
            toggleTerminalToolbar();
            return true;
        });
    }

    private void registerWallpaperActivityResultLaunchers() {
        mWallpaperPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) {
                    launchWallpaperCrop(uri);
                }
            }
        );
        mWallpaperCropLauncher = registerForActivityResult(
            new CropImageContract(),
            result -> {
                if (result instanceof CropImage.CancelledResult) {
                    return;
                }
                handleWallpaperCropResult(result);
            }
        );
    }

    private void launchManagedWallpaperPicker() {
        if (mWallpaperPickerLauncher == null) {
            showToast(getString(R.string.error_wallpaper_set_failed), true);
            return;
        }
        mWallpaperPickerLauncher.launch(
            new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()
        );
    }

    private void launchWallpaperCrop(@NonNull Uri sourceUri) {
        if (mWallpaperCropLauncher == null) {
            showToast(getString(R.string.error_wallpaper_set_failed), true);
            return;
        }

        Rect wallpaperFrameRect = getSystemWallpaperFrameRect();
        CropImageOptions cropOptions = new CropImageOptions();
        cropOptions.fixAspectRatio = true;
        cropOptions.aspectRatioX = Math.max(1, wallpaperFrameRect.width());
        cropOptions.aspectRatioY = Math.max(1, wallpaperFrameRect.height());
        cropOptions.outputRequestWidth = Math.max(1, wallpaperFrameRect.width());
        cropOptions.outputRequestHeight = Math.max(1, wallpaperFrameRect.height());
        File tempCropFile = getManagedWallpaperTempFile();
        if (tempCropFile.exists()) {
            tempCropFile.delete();
        }
        cropOptions.customOutputUri = getManagedWallpaperTempFileUri(tempCropFile);
        cropOptions.outputCompressFormat = Bitmap.CompressFormat.PNG;
        cropOptions.outputCompressQuality = 100;
        cropOptions.activityTitle = "";
        cropOptions.cropMenuCropButtonTitle = getString(R.string.action_apply);
        int surfaceBase = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        int surfacePanelHigh = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh, R.color.termux_surface_panel_high);
        int primary = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorPrimary, R.color.termux_primary);
        int onSurface = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface, R.color.termux_on_surface);
        int onSurfaceVariant = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant, R.color.termux_on_surface_variant);
        int accentContainer = getTermuxThemeColor(com.termux.shared.R.attr.termuxColorAccentContainer, R.color.termux_accent_container);
        cropOptions.activityBackgroundColor = surfaceBase;
        cropOptions.toolbarColor = surfacePanelHigh;
        cropOptions.toolbarTitleColor = onSurface;
        cropOptions.toolbarBackButtonColor = onSurface;
        cropOptions.toolbarTintColor = primary;
        cropOptions.activityMenuIconColor = primary;
        cropOptions.activityMenuTextColor = primary;
        cropOptions.backgroundColor = withAlphaComponent(Color.BLACK, 190);
        cropOptions.guidelinesColor = withAlphaComponent(onSurfaceVariant, 210);
        cropOptions.borderLineColor = withAlphaComponent(onSurface, 210);
        cropOptions.borderCornerColor = onSurface;
        cropOptions.circleCornerFillColorHexValue = onSurface;
        cropOptions.progressBarColor = accentContainer;

        mWallpaperCropLauncher.launch(new CropImageContractOptions(sourceUri, cropOptions));
    }

    private void handleWallpaperCropResult(@NonNull CropImageView.CropResult result) {
        if (!result.isSuccessful()) {
            Logger.logError(LOG_TAG, "Wallpaper crop failed");
            if (result.getError() != null) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Wallpaper crop failed", result.getError());
            }
            showToast(getString(R.string.error_wallpaper_set_failed), true);
            return;
        }

        Uri croppedUri = result.getUriContent();
        if (croppedUri == null) {
            showToast(getString(R.string.error_wallpaper_set_failed), true);
            return;
        }

        showWallpaperTargetPrompt(croppedUri);
    }

    private void showWallpaperTargetPrompt(@NonNull Uri croppedUri) {
        String[] targets = new String[] {
            getString(R.string.wallpaper_target_home_screen),
            getString(R.string.wallpaper_target_lock_screen),
            getString(R.string.wallpaper_target_home_and_lock_screen)
        };
        int[] flags = new int[] {
            WallpaperManager.FLAG_SYSTEM,
            WallpaperManager.FLAG_LOCK,
            WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, targets);
        new MaterialAlertDialogBuilder(this)
            .setAdapter(adapter, (dialogInterface, which) -> {
                int selectedFlags = flags[which];
                if (!applyManagedWallpaper(croppedUri, selectedFlags)) {
                    showToast(getString(R.string.error_wallpaper_set_failed), true);
                    return;
                }

                if ((selectedFlags & WallpaperManager.FLAG_SYSTEM) != 0) {
                    setWallpaperModeEnabled(this, true);
                    updateWindowBackgroundForCurrentSession();
                    View rootView = findViewById(R.id.activity_termux_root_view);
                    if (rootView != null) {
                        rootView.post(this::applyWallpaperOffsetFixIfNeeded);
                    }
                    scheduleAccessoryRenderSync("wallpaper-crop-applied");
                }
            })
            .show();
    }

    private boolean applyManagedWallpaper(@NonNull Uri croppedUri, int wallpaperFlags) {
        Rect visibleCropHint = getWallpaperFullImageCropHint(croppedUri);
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
            if ((wallpaperFlags & WallpaperManager.FLAG_SYSTEM) != 0) {
                suggestManagedWallpaperDimensions(wallpaperManager);
            }
            if (!setManagedWallpaperStream(wallpaperManager, croppedUri, visibleCropHint, wallpaperFlags)) {
                return false;
            }
            exportWallpaperCopyToTermuxBackgroundDirectory(croppedUri);
            if ((wallpaperFlags & WallpaperManager.FLAG_SYSTEM) != 0) {
                promoteManagedWallpaperTempFile();
                int wallpaperId = getCurrentSystemWallpaperId();
                if (mPreferences != null) {
                    mPreferences.setManagedWallpaperSystemId(wallpaperId);
                }
            }
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply managed wallpaper", e);
            return false;
        }
    }

    private boolean setManagedWallpaperStream(@NonNull WallpaperManager wallpaperManager, @NonNull Uri croppedUri,
                                              @Nullable Rect visibleCropHint, int wallpaperFlags) {
        if (visibleCropHint != null) {
            try (InputStream inputStream = openWallpaperInputStream(croppedUri)) {
                if (inputStream == null) {
                    return false;
                }
                wallpaperManager.setStream(inputStream, visibleCropHint, true, wallpaperFlags);
                return true;
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply managed wallpaper with crop hint; retrying without hint", e);
            }
        }

        try (InputStream inputStream = openWallpaperInputStream(croppedUri)) {
            if (inputStream == null) {
                return false;
            }
            wallpaperManager.setStream(inputStream, null, true, wallpaperFlags);
            return true;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to apply managed wallpaper without crop hint", e);
            return false;
        }
    }

    @Nullable
    private Rect getWallpaperFullImageCropHint(@NonNull Uri uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream inputStream = openWallpaperInputStream(uri)) {
            if (inputStream == null) {
                return null;
            }
            BitmapFactory.decodeStream(inputStream, null, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null;
            }
            return new Rect(0, 0, options.outWidth, options.outHeight);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read wallpaper crop bounds", e);
            return null;
        }
    }

    private void exportWallpaperCopyToTermuxBackgroundDirectory(@NonNull Uri wallpaperUri) {
        File backgroundDir = TermuxConstants.TERMUX_BACKGROUND_DIR;
        if (!backgroundDir.exists() && !backgroundDir.mkdirs()) {
            Logger.logError(LOG_TAG, "Failed to create termux background directory at: " + backgroundDir.getAbsolutePath());
            return;
        }

        File destination = TermuxConstants.TERMUX_BACKGROUND_IMAGE_FILE;
        try (InputStream inputStream = openWallpaperInputStream(wallpaperUri);
             FileOutputStream outputStream = new FileOutputStream(destination, false)) {
            if (inputStream == null) {
                Logger.logError(LOG_TAG, "Failed to export wallpaper copy: could not open source stream");
                return;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to export wallpaper copy to " + destination.getAbsolutePath(), e);
        }
    }

    @Nullable
    private InputStream openWallpaperInputStream(@NonNull Uri uri) {
        try {
            if ("file".equals(uri.getScheme())) {
                return new FileInputStream(new File(uri.getPath()));
            }
            return getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to open wallpaper stream", e);
            return null;
        }
    }

    private int getCurrentSystemWallpaperId() {
        try {
            return WallpaperManager.getInstance(this).getWallpaperId(WallpaperManager.FLAG_SYSTEM);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to resolve current system wallpaper id", e);
            return -1;
        }
    }

    @NonNull
    private File getManagedWallpaperExactFile() {
        File directory = new File(getFilesDir(), "managed-wallpaper");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return new File(directory, "system-wallpaper-exact.png");
    }

    @NonNull
    private File getManagedWallpaperTempFile() {
        File directory = new File(getFilesDir(), "managed-wallpaper");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return new File(directory, "system-wallpaper-pending.png");
    }

    @NonNull
    private Uri getManagedWallpaperTempFileUri(@NonNull File file) {
        return FileProvider.getUriForFile(
            this,
            getPackageName() + ".cropper.fileprovider",
            file
        );
    }

    private void promoteManagedWallpaperTempFile() {
        File tempFile = getManagedWallpaperTempFile();
        if (!tempFile.isFile()) {
            return;
        }
        File exactFile = getManagedWallpaperExactFile();
        if (exactFile.exists()) {
            exactFile.delete();
        }
        if (!tempFile.renameTo(exactFile)) {
            Logger.logError(LOG_TAG, "Failed to promote managed wallpaper temp file");
        }
    }

    private void suggestManagedWallpaperDimensions(@NonNull WallpaperManager wallpaperManager) {
        try {
            Rect frameRect = getSystemWallpaperFrameRect();
            wallpaperManager.suggestDesiredDimensions(frameRect.width(), frameRect.height());
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to suggest wallpaper dimensions; continuing with wallpaper apply", e);
        }
    }

    @NonNull
    private Rect getManagedWallpaperFrameRect() {
        if (getWindow() != null && getWindow().getDecorView() != null) {
            View decorView = getWindow().getDecorView();
            if (decorView.getWidth() > 0 && decorView.getHeight() > 0) {
                decorView.getLocationOnScreen(mTmpParentLocation);
                int left = mTmpParentLocation[0];
                int top = mTmpParentLocation[1];
                return new Rect(left, top, left + decorView.getWidth(), top + decorView.getHeight());
            }
        }
        return getSystemWallpaperFrameRect();
    }

    @NonNull
    private Rect getSystemWallpaperFrameRect() {
        DisplayMetrics realMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(realMetrics);
        return new Rect(0, 0, Math.max(1, realMetrics.widthPixels), Math.max(1, realMetrics.heightPixels));
    }

    public static void setWallpaperModeEnabled(@NonNull Context context, boolean enabled) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        if (preferences == null) {
            return;
        }

        applyWallpaperModePreferences(preferences, enabled);
        requestTermuxActivityStylingOnNextResume(context, true);
    }

    static void applyWallpaperModePreferences(@NonNull TermuxAppSharedPreferences preferences, boolean enabled) {
        if (enabled) {
            preferences.setUseSystemWallpaperEnabled(true);
            preferences.setTerminalBackgroundOpacity(preferences.getWallpaperEnabledTerminalBackgroundOpacity());
            preferences.setAppBarOpacity(preferences.getWallpaperEnabledAppBarOpacity());
            preferences.setExtraKeysBlurRadius(preferences.getWallpaperEnabledExtraKeysBlurRadius());
        } else {
            preferences.setWallpaperEnabledTerminalBackgroundOpacity(preferences.getTerminalBackgroundOpacity());
            preferences.setWallpaperEnabledAppBarOpacity(preferences.getAppBarOpacity());
            preferences.setWallpaperEnabledExtraKeysBlurRadius(preferences.getExtraKeysBlurRadius());
            preferences.setUseSystemWallpaperEnabled(false);
            preferences.setTerminalBackgroundOpacity(100);
            preferences.setAppBarOpacity(100);
            preferences.setExtraKeysBlurRadius(0);
        }
    }

    private void openLookAndFeelSettings() {
        ActivityUtils.startActivity(this,
            SettingsActivity.createFragmentIntent(this,
                com.termux.app.fragments.settings.termux.TermuxStylePreferencesFragment.class,
                R.string.termux_style_preferences_title));
    }

    private void openAppsBarSettings() {
        ActivityUtils.startActivity(this,
            SettingsActivity.createFragmentIntent(this,
                com.termux.app.fragments.settings.termux.LauncherPreferencesFragment.class,
                R.string.termux_launcher_preferences_title));
    }

    private void openSettingsHome() {
        ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
    }

    /** Clock apps the platform intents do not reach, tried in order once those have failed. */
    private static final String[] CLOCK_PACKAGES = {
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
        "com.nothing.clock",
    };

    /**
     * Destination of a tap on the top-pane clock: the device's own clock app.
     *
     * <p>{@code ACTION_SHOW_ALARMS} first because it is the documented way to ask for whatever the
     * user's clock app is, without this fork having to know its package. Only if nothing handles it —
     * some OEM clocks declare neither alarm action — does the package list get a turn.
     */
    private void openSystemClockApp() {
        if (startClockIntent(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return;
        for (String packageName : CLOCK_PACKAGES) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch != null && startClockIntent(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
                return;
        }
        Logger.logWarn(LOG_TAG, "No clock app available for the top-pane clock tap");
    }

    /**
     * Launch attempt reported by outcome rather than by a prior {@code resolveActivity}: under
     * Android 11 package visibility that query is filtered for an action this app does not declare in
     * {@code <queries>}, so it answers "nothing handles this" even when the clock app does.
     */
    private boolean startClockIntent(@NonNull Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (Exception exception) {
            Logger.logWarn(LOG_TAG, "Cannot open clock app: " + exception.getMessage());
            return false;
        }
    }

    /** The long-press action dialog's rows, and the two legacy menu items that share their ids. */
    public boolean handleTerminalAction(int itemId) {
        TerminalSession session = getCurrentSession();
        switch(itemId) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SET_WALLPAPER_ID:
                launchManagedWallpaperPicker();
                return true;
            case CONTEXT_MENU_REMOVE_WALLPAPER_ID:
                setWallpaperModeEnabled(this, !shouldUseWallpaperPassthroughMode());
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                openSettingsHome();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_GLASS_LAB_ID:
                enterDockTuningMode();
                return true;
            case CONTEXT_MENU_COMMAND_PALETTE_ID:
                com.termux.app.terminal.TerminalCommandPalette.show(this);
                return true;
            case CONTEXT_MENU_STYLE_ID:
                openTerminalStyling();
                return true;
            default:
                return false;
        }
    }

    /**
     * Termux:Styling if it is there, Appearance settings if it is not.
     *
     * <p>Decided by the launch outcome rather than by a {@code resolveActivity} or a
     * {@code getPackageInfo}: under Android 11 package visibility both are filtered for a package this
     * app does not declare in {@code <queries>}, so they answer "not installed" for a plugin that is
     * sitting right there.
     */
    private void openTerminalStyling() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (Exception exception) {
            Logger.logWarn(LOG_TAG, "Cannot open Termux:Styling, falling back to Appearance settings: "
                + exception.getMessage());
            openLookAndFeelSettings();
        }
    }

    /**
     * The terminal long-press menu. Keeps its {@code boolean} contract — the long-press path and
     * {@code terminal.action_sheet} both read it as "was the gesture spent here".
     */
    public boolean showTerminalActionSheet() {
        return showTerminalActionSheet(null);
    }

    /**
     * @param anchor accepted for the callers that pass one, but the dialog centres itself: this is
     *               the pre-sheet menu, restored at the owner's request.
     */
    public boolean showTerminalActionSheet(@Nullable android.graphics.PointF anchor) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) {
            return false;
        }
        if (mTerminalActionDialog != null && mTerminalActionDialog.isShowing()) {
            return true;
        }
        List<TerminalActionItem> items = new ArrayList<>();
        items.add(new TerminalActionItem(CONTEXT_MENU_COMMAND_PALETTE_ID, getString(R.string.action_command_palette)));
        items.add(new TerminalActionItem(CONTEXT_MENU_SELECT_URL_ID, getString(R.string.action_select_url)));
        items.add(new TerminalActionItem(CONTEXT_MENU_SHARE_TRANSCRIPT_ID, getString(R.string.action_share_transcript)));
        items.add(new TerminalActionItem(CONTEXT_MENU_SET_WALLPAPER_ID, getString(R.string.action_set_background_image)));
        items.add(new TerminalActionItem(
            CONTEXT_MENU_REMOVE_WALLPAPER_ID,
            getString(shouldUseWallpaperPassthroughMode()
                ? R.string.action_disable_background_image
                : R.string.action_enable_background_image)
        ));
        items.add(new TerminalActionItem(CONTEXT_MENU_GLASS_LAB_ID, getString(R.string.action_glass_lab)));
        // Appearance and Apps & Access are reachable from the Settings page; keep this sheet lean.
        items.add(new TerminalActionItem(CONTEXT_MENU_SETTINGS_ID, getString(R.string.action_open_settings)));
        items.add(new TerminalActionItem(CONTEXT_MENU_RESET_TERMINAL_ID, getString(R.string.action_reset_terminal)));
        items.add(new TerminalActionItem(CONTEXT_MENU_KILL_PROCESS_ID,
            getString(R.string.action_kill_process, currentSession.getPid())));

        ArrayAdapter<TerminalActionItem> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_1, items);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setAdapter(adapter, (dialogInterface, which) -> handleTerminalAction(items.get(which).id))
            .setOnDismissListener(dialogInterface -> {
                if (mTerminalView != null) {
                    mTerminalView.onContextMenuClosed(null);
                }
                if (mTerminalActionDialog == dialogInterface) {
                    mTerminalActionDialog = null;
                }
            })
            .create();
        mTerminalActionDialog = dialog;
        dialog.show();
        return true;
    }

    /**
     * The command palette overlay, created on first use. It lives in the activity rather than in
     * a dialog window so typing reaches it from the in-app keyboard without the system IME.
     */
    @NonNull
    public com.termux.app.terminal.TerminalCommandPaletteController getCommandPaletteController() {
        if (mCommandPalette == null)
            mCommandPalette = new com.termux.app.terminal.TerminalCommandPaletteController(this);
        return mCommandPalette;
    }

    public boolean isCommandPaletteOpen() {
        return mCommandPalette != null && mCommandPalette.isOpen();
    }

    /**
     * The terminal sheet plane, created on first use. Like the palette it lives in the activity
     * rather than in a dialog window, so a prompt costs no focus change and no system IME, and it
     * binds its views lazily so a session that never opens a prompt never pays for them.
     */
    @NonNull
    public com.termux.app.terminal.TerminalSheetController getTerminalSheetController() {
        if (mTerminalSheet == null)
            mTerminalSheet = new com.termux.app.terminal.TerminalSheetController(this);
        return mTerminalSheet;
    }

    /** Guarded on the field, not the lazy accessor: asking must not build a plane. */
    public boolean isTerminalSheetOpen() {
        return mTerminalSheet != null && mTerminalSheet.isOpen();
    }

    /**
     * Glass for a sheet card. Same builder, tint and rim as the dock and the rename chip, so the
     * plane reads as the same kit rather than as a Material dialog that lost its window.
     */
    @NonNull
    public Drawable buildTerminalSheetSurface() {
        float barAlpha = mPreferences != null ? mPreferences.getAppBarOpacity() / 100f : 0.5f;
        int grain = mPreferences != null
            ? mPreferences.getDockGlassGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN;
        // Floored well above the dock's opacity: a sheet has body text over a live blur, and at the
        // dock's own tint the terminal behind it reads straight through the words.
        return buildGlassSurface(Math.max(0.92f, barAlpha), 0f, 1f, false, grain,
            dpToPx(com.termux.app.terminal.TerminalSheetController.cornerRadiusDp()), true);
    }

    /**
     * Makes a keyboard available for typing into a focusless surface, and reports whether one is.
     *
     * <p>False means there is nothing on screen to aim the key channel at — the caller either does
     * without typing or falls back to a focused editor.
     */
    public boolean ensureInAppTypingKeyboard() {
        if (!isInAppKeyboardEnabled() || mInAppKeyboard == null) return false;
        if (!mInAppKeyboard.isVisible()) {
            mInAppKeyboard.show(com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard
                .ShowReason.KEYBOARD_ACTION);
        }
        return true;
    }

    /**
     * Whether a raw screen point lands on the in-app keyboard. Every full-screen focusless overlay
     * has to let those touches through: they are the keys it is typed with, and swallowing them
     * would end the interaction on its first keystroke.
     */
    public boolean isPointOnInAppKeyboard(float rawX, float rawY) {
        if (mInAppKeyboard == null) return false;
        Rect keyboard = new Rect();
        return mInAppKeyboard.getKeyboardRectOnScreen(keyboard)
            && keyboard.contains(Math.round(rawX), Math.round(rawY));
    }

    /**
     * The launcher row, or null before it is built. Exposed for the drawer's grid, whose cells
     * borrow their icons, tint, launch ladder and context menu from it rather than owning a second
     * copy of any of them.
     */
    @Nullable
    public SuggestionBarView getSuggestionBarView() {
        return mSuggestionBarView;
    }

    /**
     * The app drawer plane, created on first use. Like the palette it lives in the activity rather
     * than in a window of its own, and binds its views lazily, so an install that never pulls the
     * drawer down never pays for it.
     */
    @NonNull
    public com.termux.app.launcher.drawer.AppDrawerController getAppDrawerController() {
        if (mAppDrawerController == null) {
            mAppDrawerController = new com.termux.app.launcher.drawer.AppDrawerController(this);
            // Registered here rather than in setSuggestionBarView() so the accessor stays lazy: the
            // only thing that builds a controller is a drag, and a drag comes from the row itself,
            // which therefore already exists by the time this runs.
            mAppDrawerController.setDockChoreographyTarget(mSuggestionBarView);
        }
        return mAppDrawerController;
    }

    /**
     * True while the app drawer plane owns the accessory stack's transforms — open, or mid open/
     * close drag. Every accessory-geometry seam gates on this: while the plane is engaged the stack
     * must not relayout, because that runs the flush-padding solver and a terminal resize (SIGWINCH)
     * per frame.
     */
    private boolean isAppDrawerEngaged() {
        return mAppDrawerController != null && mAppDrawerController.isEngaged();
    }

    /**
     * Closes the app drawer plane with no animation. Called from the lifecycle and HOME paths, where
     * a settling spring would otherwise be resumed against stale geometry.
     *
     * <p>Deliberately does not go through {@link #getAppDrawerController()}: a drawer that was never
     * opened has nothing to close, and building the controller here would defeat the lazy accessor.
     */
    private void closeAppDrawerImmediate() {
        try {
            if (mAppDrawerController != null) mAppDrawerController.closeImmediate();
        } finally {
            flushPendingAccessoryGeometry();
        }
    }

    /**
     * Re-applies the accessory geometry that {@link #isAppDrawerEngaged()} suppressed. Called on
     * drawer close and unconditionally from {@link #onStart()}: a suppression that is never flushed
     * leaves the dock deaf to style and height changes until the activity is recreated.
     *
     * <p>Public because {@code AppDrawerController} calls it from the {@code finally} of its own
     * teardown: the flush must run even if restoring the plane's transforms throws.
     */
    public void flushPendingAccessoryGeometry() {
        if (!mAppDrawerGeometryFreezePending || isAppDrawerEngaged()) {
            return;
        }
        mAppDrawerGeometryFreezePending = false;
        applyAccessoryGeometryIfNeeded(true, "appDrawer:flush");
    }

    /** Row-level haptic ticks, shared by the dock rows and the palette's focus movement. */
    public boolean isRowHapticsEnabled() {
        return mPreferences != null && mPreferences.isAppLauncherRowHapticsEnabled();
    }

    /**
     * Routes in-app keyboard values to the palette while it is open. The palette owns the
     * interceptor slot on the keyboard's own handler, so nothing forks the key pipeline.
     *
     * <p>This is only one of three ways typing reaches a focusless overlay, and any new one must
     * wire all three or it will look dead on somebody's keyboard:
     *
     * <ul>
     *   <li>hardware and external keyboards, as key events through
     *       {@link #handleCommandPaletteKey};
     *   <li>the in-app keyboard, as resolved key values through this interceptor;
     *   <li>system IMEs, as committed text through {@link #handleCommandPaletteCodePoint} — those
     *       send no key events at all for ordinary characters.
     * </ul>
     */
    public void setCommandPaletteInterceptorActive(boolean active) {
        if (mInAppKeyboard == null)
            return;
        mInAppKeyboard.setKeyValueInterceptor(active ? getCommandPaletteController() : null);
    }

    /**
     * Routes in-app keyboard values to the terminal sheet plane while a sheet is up, the same slot
     * and the same three channels the palette documents above.
     *
     * <p>Releasing means "whoever owns the slot now", not "nobody": the drawer's search may be open
     * behind a sheet the drawer itself never closed.
     */
    public void setTerminalSheetInterceptorActive(boolean active) {
        if (mInAppKeyboard == null)
            return;
        if (active) {
            mInAppKeyboard.setKeyValueInterceptor(getTerminalSheetController());
            return;
        }
        setAppDrawerInterceptorActive(isAppDrawerOpen());
    }

    /** Seed rect for surfaces growing out of the space bar; false when the keyboard is hidden. */
    public boolean getInAppKeyboardSpaceBarRect(@NonNull Rect out) {
        return mInAppKeyboard != null && mInAppKeyboard.getSpaceBarRectOnScreen(out);
    }

    /**
     * Routes in-app keyboard values to the app drawer's search while it is open.
     *
     * <p>Shares the single interceptor slot with the palette, which is safe in exactly one
     * direction: {@code TerminalCommandPaletteController.show()} closes the drawer before it
     * installs its own interceptor, and the drawer's close releases this one on the way. The drawer
     * cannot open over the palette — the dock's arbiter vetoes a drawer drag while it is up — so the
     * two are never both claiming.
     *
     * <p>Guarded on the field rather than the lazy accessor: deactivating a drawer that was never
     * opened must not build one.
     */
    public void setAppDrawerInterceptorActive(boolean active) {
        if (mInAppKeyboard == null)
            return;
        // A close/mode reset can be re-entered from terminal dispatch while rename is active. The
        // rename interceptor is a stronger, independently owned latch and cannot be cleared by the
        // drawer search lifecycle; its own single finish path restores the previous owner.
        if (mFolderRenameController.isActive()) {
            mInAppKeyboard.setKeyValueInterceptor(mFolderRenameController);
            return;
        }
        // Same latch for a terminal rename chip: beginTerminalRename closes the drawer, and the
        // closing spring's onClosed lands here hundreds of ms after the chip installed its
        // interceptor. Clearing the slot then would leave the chip up but dead, typing into the
        // shell behind it. The chip's own finish path restores the drawer's claim.
        if (isTerminalRenameActive())
            return;
        mInAppKeyboard.setKeyValueInterceptor(
            active && mAppDrawerController != null
                ? mAppDrawerController.getSearchController() : null);
    }

    /** Installs focusless rename ahead of drawer search while TerminalView retains InputConnection. */
    public void beginFolderRename(long revision, @NonNull String folderId, @NonNull String title,
                                  @NonNull FolderRenameTitleView titleView) {
        mFolderRenameController.begin(revision, folderId, title,
            new FolderRenameController.Host() {
                @NonNull
                @Override public LauncherConfigRepository.MutationResult commit(long expected,
                    @NonNull String id, @NonNull String value) {
                    if (mLauncherConfigRepository == null)
                        return LauncherConfigRepository.MutationResult.MISSING;
                    return mLauncherConfigRepository.renameFolder(expected, id, value);
                }

                @Override public void onDraftChanged(@NonNull FolderRenameModel model) {
                    titleView.bind(model, true);
                }

                @Override public void onRenameEnded(boolean committed) {
                    com.termux.app.launcher.model.PinnedFolderItem latest =
                        mLauncherConfigRepository == null ? null
                            : mLauncherConfigRepository.loadSnapshot().folder(folderId);
                    titleView.bind(new FolderRenameModel(latest == null ? title : latest.title), false);
                    if (mInAppKeyboard != null) mInAppKeyboard.setKeyValueInterceptor(
                        isAppDrawerOpen() && mAppDrawerController != null
                            ? mAppDrawerController.getSearchController() : null);
                }
            });
        if (mInAppKeyboard != null)
            mInAppKeyboard.setKeyValueInterceptor(mFolderRenameController);
        onSystemImeRequested();
        KeyboardUtils.showSoftKeyboard(this, mTerminalView);
    }

    public void cancelFolderRename() { mFolderRenameController.cancel(); }

    public boolean isFolderRenameActive() { return mFolderRenameController.isActive(); }

    /** Hardware and external-keyboard strokes claimed by the open app drawer's search. */
    public boolean handleAppDrawerKey(int keyCode, @NonNull KeyEvent event) {
        if (mFolderRenameController.handleKeyDown(keyCode, event)) return true;
        return mAppDrawerController != null
            && mAppDrawerController.handleSearchKey(keyCode, event);
    }

    /**
     * Text committed by a system IME, claimed by the open app drawer's search. The drawer has no
     * focused text field by design, so this is the only route by which a third-party keyboard's
     * ordinary characters reach it rather than the shell behind the plane.
     */
    public boolean handleAppDrawerCodePoint(int codePoint, boolean ctrlDown) {
        if (mFolderRenameController.handleCodePoint(codePoint, ctrlDown)) return true;
        return mAppDrawerController != null
            && mAppDrawerController.handleSearchCodePoint(codePoint, ctrlDown);
    }

    /** Set when {@link #handleOverlayPaneKey} closed a pane, so the matching release is swallowed. */
    private boolean mOverlayPaneClaimedBackDown;

    /**
     * Back aimed at the widget pane or the FULL status pane, claimed in the key channel.
     *
     * <p>{@link #onBackPressed()} already closes both, but on a device the back key travels the key
     * channel and is consumed before {@code onBackPressed()} ever runs — which is why two presses
     * left the pane open and only the pull-up gesture closed it. The drawer has had a claim here for
     * exactly this reason; these two had none. The order matches {@link #onBackPressed()}: panes
     * before the palette and the drawer.
     *
     * <p>Restricted to {@code ACTION_DOWN} on the back key, so nothing here can swallow the escape
     * stroke the palette is checked first for.
     */
    public boolean handleOverlayPaneKey(int keyCode, @NonNull KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_DOWN)
            return false;
        boolean consumed = (mWidgetPaneController != null && mWidgetPaneController.onBackPressed())
            || (mFullStatusBarController != null && mFullStatusBarController.onBackPressed());
        if (consumed) mOverlayPaneClaimedBackDown = true;
        return consumed;
    }

    /**
     * Whether the release of a back press this activity's panes already consumed should be
     * swallowed. Tracked as a flag rather than re-asked as "is a pane open", because by the time the
     * release arrives the pane is closing and would answer no.
     */
    public boolean consumeOverlayPaneKeyUp(int keyCode) {
        if (keyCode != KeyEvent.KEYCODE_BACK || !mOverlayPaneClaimedBackDown)
            return false;
        mOverlayPaneClaimedBackDown = false;
        return true;
    }

    /** True while the drawer plane is up — what the key-release swallow asks. */
    public boolean isAppDrawerOpen() {
        return mAppDrawerController != null && mAppDrawerController.isOpen();
    }

    // ------------------------------------------------------------------ inline rename

    /**
     * The anchored glass editor every terminal rename goes through. Built lazily: a user who never
     * renames anything never pays for the chip or its host lookup.
     */
    @NonNull
    public com.termux.app.terminal.rename.TerminalRenameCoordinator getRenameCoordinator() {
        if (mRenameCoordinator == null)
            mRenameCoordinator = new com.termux.app.terminal.rename.TerminalRenameCoordinator(
                new TerminalRenameHost());
        return mRenameCoordinator;
    }

    /** Opens the rename editor for {@code target}. The single entry point every caller uses. */
    public boolean beginTerminalRename(
            @NonNull com.termux.app.terminal.TerminalRenameTarget target) {
        if (target == com.termux.app.terminal.TerminalRenameTarget.WINDOW && !isSplitPanesEnabled())
            return false;
        if (target == com.termux.app.terminal.TerminalRenameTarget.SESSION
            && (mCurrentWSession == null || !isSplitPanesEnabled())) return false;
        if (target == com.termux.app.terminal.TerminalRenameTarget.PANE
            && getCurrentSession() == null) return false;
        // The palette, the sheet plane and the drawer all own the same interceptor slot the chip
        // needs, so a rename starts from a clean surface rather than fighting one of them for
        // typing. The sheets also have to go for a second reason: the chip anchors to the window
        // bar or the session indicator, both of which sit behind a modal sheet, so a rename started
        // from the browser would otherwise open an editor nobody can see.
        if (isCommandPaletteOpen()) mCommandPalette.collapse();
        if (mTerminalSheet != null) mTerminalSheet.dismissAll();
        if (mAppDrawerController != null && mAppDrawerController.isOpen())
            mAppDrawerController.close(true);
        return getRenameCoordinator().begin(target);
    }

    /**
     * Opens the rename editor for the session at {@code index}, bringing it forward first.
     *
     * <p>The editor names "the current session", so a panel or browser row that points at another
     * one activates it rather than editing a session the user cannot see change.
     */
    public boolean beginSessionRenameAtIndex(int index) {
        if (index < 0 || index >= mWSessions.size()) return false;
        WSession target = mWSessions.get(index);
        if (target != mCurrentWSession && !activateBrowserSession(index)) return false;
        return beginTerminalRename(com.termux.app.terminal.TerminalRenameTarget.SESSION);
    }

    /** Renames a session identified by its drawer row, used by the drawer's long-press. */
    public boolean beginSessionRenameFor(@Nullable TerminalSession shell) {
        if (shell == null || mPaneController == null) return false;
        WSession owner = wsessionOwning(mPaneController.windowOf(shell));
        if (owner == null) return false;
        if (owner != mCurrentWSession && getTermuxTerminalSessionClient() != null) {
            // The editor names "the current session", so bring the row's session forward first
            // rather than editing something the user cannot see.
            getTermuxTerminalSessionClient().setCurrentSession(shell);
        }
        return beginTerminalRename(com.termux.app.terminal.TerminalRenameTarget.SESSION);
    }

    public boolean isTerminalRenameActive() {
        return mRenameCoordinator != null && mRenameCoordinator.isActive();
    }

    /** Hardware and external-keyboard strokes claimed by an open rename chip. */
    public boolean handleTerminalRenameKey(int keyCode, @NonNull KeyEvent event) {
        return mRenameCoordinator != null && mRenameCoordinator.handleKeyDown(keyCode, event);
    }

    /** System-IME committed text claimed by an open rename chip. */
    public boolean handleTerminalRenameCodePoint(int codePoint, boolean ctrlDown) {
        return mRenameCoordinator != null
            && mRenameCoordinator.handleCodePoint(codePoint, ctrlDown);
    }

    /**
     * Installs the backend that proposes names for windows and sessions. Nothing installs one yet;
     * an on-device model backend is the intended first caller, and it reaches the same apply path a
     * keybind does.
     */
    public void setRenameSuggestionProvider(
            @Nullable com.termux.app.terminal.rename.TerminalRenameSuggestionProvider provider) {
        getRenameCoordinator().setSuggestionProvider(provider);
    }

    /** Activity half of the rename editor: anchors, names, glass and the keyboard it types with. */
    private final class TerminalRenameHost
            implements com.termux.app.terminal.rename.TerminalRenameCoordinator.Host {

        @Nullable
        @Override
        public ViewGroup chipHost() {
            return findViewById(R.id.terminal_rename_chip_host);
        }

        @Nullable
        @Override
        public View anchorFor(@NonNull com.termux.app.terminal.TerminalRenameTarget target) {
            switch (target) {
                case WINDOW: {
                    com.termux.app.terminal.TerminalWindowBar bar =
                        findViewById(R.id.terminal_window_bar);
                    View tab = bar == null ? null : bar.selectedTabView();
                    return tab != null ? tab : bar;
                }
                case SESSION: {
                    View indicator = findViewById(R.id.terminal_sessions_indicator);
                    return indicator != null && indicator.isShown() ? indicator : null;
                }
                case PANE:
                default:
                    return getTerminalView();
            }
        }

        @Nullable
        @Override
        public String currentName(@NonNull com.termux.app.terminal.TerminalRenameTarget target) {
            return currentTerminalName(target);
        }

        @Override
        public boolean applyName(@NonNull com.termux.app.terminal.TerminalRenameTarget target,
                                 @Nullable String name) {
            switch (target) {
                case WINDOW: return renameCurrentWindowTo(name);
                case SESSION: return renameCurrentSessionTo(name);
                case PANE:
                default: {
                    TermuxTerminalSessionActivityClient client = getTermuxTerminalSessionClient();
                    return client != null && client.renameCurrentPaneTo(name == null ? "" : name);
                }
            }
        }

        @NonNull
        @Override
        public Drawable chipBackground() {
            float barAlpha = mPreferences != null ? mPreferences.getAppBarOpacity() / 100f : 0.5f;
            int grain = mPreferences != null
                ? mPreferences.getDockGlassGrain()
                : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN;
            // Half the chip's 40dp height: a full pill, with the same containing rim the other
            // floating glass surfaces carry.
            return buildGlassSurface(Math.max(0.88f, barAlpha), 0f, 1f, false, grain,
                dpToPx(20f), true);
        }

        @NonNull
        @Override
        public int[] chipColors() {
            return new int[] {
                getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                    R.color.termux_on_surface_variant),
                getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface,
                    R.color.termux_on_surface),
                getTermuxThemeColor(com.termux.shared.R.attr.termuxColorPrimary,
                    R.color.termux_primary),
            };
        }

        @Override
        public boolean isReducedMotionEnabled() {
            return TermuxActivity.this.isReducedMotionEnabled();
        }

        @Override
        public boolean ensureTypingKeyboard() {
            return ensureInAppTypingKeyboard();
        }

        @Override
        public void promptRenameWithDialog(
                @NonNull com.termux.app.terminal.TerminalRenameTarget target) {
            switch (target) {
                case WINDOW: promptWindowRenameDialog(); break;
                case SESSION: promptSessionRename(mCurrentWSession); break;
                case PANE:
                default: {
                    TermuxTerminalSessionActivityClient client = getTermuxTerminalSessionClient();
                    if (client != null) client.promptCurrentPaneRenameDialog();
                    break;
                }
            }
        }

        @Override
        public boolean isPointOnTypingKeyboard(float rawX, float rawY) {
            return isPointOnInAppKeyboard(rawX, rawY);
        }

        @Override
        public void installRenameInterceptor(
                @Nullable com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler
                    .KeyValueInterceptor interceptor) {
            if (mInAppKeyboard == null) return;
            // Restoring means "whoever owns the slot now", not "nobody": the drawer's search may be
            // open behind a rename started from a drawer row.
            if (interceptor != null) {
                mInAppKeyboard.setKeyValueInterceptor(interceptor);
            } else {
                setAppDrawerInterceptorActive(isAppDrawerOpen());
            }
        }

        @Override
        public void onRenameEnded(@NonNull com.termux.app.terminal.TerminalRenameTarget target,
                                  boolean committed) {
            if (!committed) return;
            refreshTerminalWindowBar();
            refreshSessionsPanel();
            if (getTermuxTerminalSessionClient() != null)
                getTermuxTerminalSessionClient().termuxSessionListNotifyUpdated();
        }

        @Nullable
        @Override
        public com.termux.app.terminal.rename.TerminalRenameContext renameContext(
                @NonNull com.termux.app.terminal.TerminalRenameTarget target) {
            return buildRenameContext(target);
        }
    }

    /**
     * Facts a naming backend needs about {@code target}, gathered from the same sources the window
     * pills read: the focused pane's directory, its resolved foreground process and open file, and
     * the titles of the panes the target contains.
     */
    @Nullable
    private com.termux.app.terminal.rename.TerminalRenameContext buildRenameContext(
            @NonNull com.termux.app.terminal.TerminalRenameTarget target) {
        TerminalSession focused = getCurrentSession();
        java.util.List<String> paneTitles = new java.util.ArrayList<>();
        if (mPaneController != null && mCurrentWSession != null) {
            if (target == com.termux.app.terminal.TerminalRenameTarget.SESSION) {
                for (com.termux.app.terminal.TerminalPaneController.Window window :
                        mCurrentWSession.windows) {
                    for (TerminalSession shell : mPaneController.shellsOf(window))
                        paneTitles.add(shell.getTitle());
                }
            } else if (target == com.termux.app.terminal.TerminalRenameTarget.WINDOW
                && !mCurrentWSession.windows.isEmpty()) {
                for (TerminalSession shell :
                        mPaneController.shellsOf(mCurrentWSession.currentWindow()))
                    paneTitles.add(shell.getTitle());
            }
        }
        if (paneTitles.isEmpty() && focused != null) paneTitles.add(focused.getTitle());
        com.termux.app.statusbar.WindowForegroundResolver.ForegroundInfo info =
            focused == null || mWindowForegroundResolver == null ? null
                : mWindowForegroundResolver.get(focused.getPid());
        return new com.termux.app.terminal.rename.TerminalRenameContext(target,
            currentTerminalName(target),
            com.termux.app.terminal.TerminalNamePolicy.maxCodePointsFor(target),
            focused == null ? null : focused.getCwd(),
            info == null || info.idle ? null : info.processName,
            info == null || info.idle ? null : info.openFile,
            paneTitles);
    }

    /** The name {@code target} currently carries, or null when it is unnamed. */
    @Nullable
    private String currentTerminalName(@NonNull com.termux.app.terminal.TerminalRenameTarget target) {
        switch (target) {
            case WINDOW: return getCurrentWindowName();
            case SESSION: return mCurrentWSession == null ? null : mCurrentWSession.name;
            case PANE:
            default: {
                TerminalSession session = getCurrentSession();
                return session == null ? null : session.mSessionName;
            }
        }
    }

    /** Legacy dialog for the window name, used only when there is no in-app keyboard to type with. */
    private void promptWindowRenameDialog() {
        if (mPaneController == null || mCurrentWSession == null
            || mCurrentWSession.windows.isEmpty()) return;
        com.termux.app.terminal.TerminalPaneController.Window window =
            mCurrentWSession.currentWindow();
        TextInputDialogUtils.textInput(this, R.string.title_rename_window,
            mPaneController.windowName(window), R.string.action_rename_session_confirm, text -> {
                if (mPaneController == null || mCurrentWSession == null
                    || !mCurrentWSession.windows.contains(window)) return;
                mPaneController.setWindowName(window, text);
                refreshTerminalWindowBar();
                refreshSessionsPanel();
            }, -1, null, -1, null, null);
    }

    /**
     * Summons the system IME for the drawer's search, with <b>no focus change</b>.
     *
     * <p>The obvious call here would be {@code beginExternalTextInput()}, and it is exactly wrong:
     * it runs {@code requestAccessoryGeometrySync()}, and the accessory stack's geometry is frozen
     * for the life of the drawer transition. Thawing it mid-reveal relayouts the stack under an open
     * plane and the dock visibly jumps on close. Focus stays on the terminal view, which keeps
     * owning the input connection; the committed text reaches the drawer through
     * {@link #handleAppDrawerCodePoint}.
     */
    public void requestAppDrawerSearchKeyboard() {
        onSystemImeRequested();
        KeyboardUtils.showSoftKeyboard(this, mTerminalView);
    }

    /** Hardware and external-keyboard strokes claimed by the open palette. */
    public boolean handleCommandPaletteKey(int keyCode, @NonNull KeyEvent event) {
        return mCommandPalette != null && mCommandPalette.handleHardwareKey(keyCode, event);
    }

    /**
     * Text committed by a system IME, claimed by the open palette. Third-party keyboards commit
     * characters through the input connection instead of sending key events, so without this they
     * would type straight into the shell behind the overlay.
     */
    public boolean handleCommandPaletteCodePoint(int codePoint, boolean ctrlDown) {
        return mCommandPalette != null
            && mCommandPalette.handleSoftKeyboardCodePoint(codePoint, ctrlDown);
    }

    /** Hardware and external-keyboard strokes claimed by the open sheet plane. */
    public boolean handleTerminalSheetKey(int keyCode, @NonNull KeyEvent event) {
        return mTerminalSheet != null && mTerminalSheet.handleHardwareKey(keyCode, event);
    }

    /** The sheet plane's twin of {@link #handleCommandPaletteCodePoint}, for the same reason. */
    public boolean handleTerminalSheetCodePoint(int codePoint, boolean ctrlDown) {
        return mTerminalSheet != null
            && mTerminalSheet.handleSoftKeyboardCodePoint(codePoint, ctrlDown);
    }

    /**
     * Back consumers, in order.
     *
     * <p>FULL stays first, including while its exit spring is settling, so repeated Back cannot
     * fall through into another surface. The palette follows, then the sheet plane, then the drawer
     * because it is a full-screen plane, and both of the consumers
     * below it — dock tuning and the navigation drawer — are conceptually behind it; a back press
     * that skipped past it would dismiss something the user cannot even see.
     *
     * <p>The sheet plane sits after the palette so it can never swallow the escape stroke the
     * palette is checked first for, and before the drawer, which a sheet closes as it opens and
     * therefore always outranks.
     */
    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        // The rename chip is the innermost surface on screen whenever it is up, so it is the first
        // thing Back closes — and closing it discards the draft, unlike a tap outside.
        if (isTerminalRenameActive()) {
            mRenameCoordinator.cancel();
            return;
        }
        if (mWidgetPaneController != null && mWidgetPaneController.onBackPressed()) {
            return;
        } else if (mFullStatusBarController != null && mFullStatusBarController.onBackPressed()) {
            return;
        } else if (isCommandPaletteOpen()) {
            mCommandPalette.collapse();
        } else if (mTerminalSheet != null && mTerminalSheet.onBackPressed()) {
            // One card per press, not the whole stack: a confirmation opened over the workspace
            // picker has to give the picker back rather than drop the user on the terminal.
            return;
        } else if (mAppDrawerController != null && mAppDrawerController.isOpen()) {
            // A back press with something typed is spent on the query and the drawer stays up. The
            // branch still consumes it either way: falling through to dock tuning because the query
            // absorbed it would dismiss something behind a full-screen plane.
            if (!mAppDrawerController.onBackPressedInDrawer())
                mAppDrawerController.close(true);
        } else if (mDockTuningMode) {
            exitDockTuningMode();
        } else if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else if (!getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().openDrawer(Gravity.LEFT);
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            if (!shouldShowInRecents())
                finishAndRemoveTask();
            else
                finish();
        }
    }

    /**
     * Show a toast and dismiss the last one if still visible.
     */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty())
            return;
        if (mLastToast != null)
            mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }

    /**
     * Fork-styled replacement for {@link #showToast(String, boolean)} used for session switch,
     * title-change and session-exit notices: a small glass chip centered near the top of the
     * terminal surface instead of a stock Android toast.
     */
    public void showSessionSwitchIndicator(@Nullable String text) {
        if (text == null || text.isEmpty() || isFinishing())
            return;
        com.termux.app.terminal.SessionSwitchIndicatorView indicator = obtainSessionSwitchIndicator();
        if (indicator != null)
            indicator.show(text);
    }

    @Nullable
    private com.termux.app.terminal.SessionSwitchIndicatorView obtainSessionSwitchIndicator() {
        FrameLayout host = findViewById(R.id.terminal_surface_host);
        if (host == null)
            return null;
        if (mSessionSwitchIndicator == null) {
            mSessionSwitchIndicator = new com.termux.app.terminal.SessionSwitchIndicatorView(this);
            // Both chips share the corner, so the notice owns the top slot and the background stack
            // follows it down and back up — one column, never a reserved gap.
            mSessionSwitchIndicator.setOccupancyListener(height -> {
                mNoticeOccupancyPx = height;
                if (mBackgroundProcessStack != null)
                    mBackgroundProcessStack.setNoticeOccupancyPx(height);
            });
        }
        if (mSessionSwitchIndicator.getParent() == null) {
            host.addView(mSessionSwitchIndicator,
                com.termux.app.terminal.SessionSwitchIndicatorView.buildHostLayoutParams(this));
        }
        return mSessionSwitchIndicator;
    }

    @Nullable
    private com.termux.app.statusbar.BackgroundProcessStackView obtainBackgroundProcessStack() {
        FrameLayout host = findViewById(R.id.terminal_surface_host);
        if (host == null) return null;
        if (mBackgroundProcessStack == null)
            mBackgroundProcessStack = new com.termux.app.statusbar.BackgroundProcessStackView(this);
        if (mBackgroundProcessStack.getParent() == null) {
            host.addView(mBackgroundProcessStack,
                com.termux.app.statusbar.BackgroundProcessStackView.buildHostLayoutParams(this));
            // The notice can already be up when the first background command appears.
            mBackgroundProcessStack.setNoticeOccupancyPx(mNoticeOccupancyPx);
        }
        return mBackgroundProcessStack;
    }

    /**
     * Hook system menu to show the terminal action sheet instead.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        showTerminalActionSheet();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (handleTerminalAction(item.getItemId())) {
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Confirms killing a session on the terminal sheet plane rather than in a dialog window, so the
     * prompt costs no focus change over the terminal it is about. The pid rides in the title: it is
     * the only thing distinguishing this prompt from the same one about another session.
     */
    private void showKillSessionDialog(TerminalSession session) {
        if (session == null)
            return;
        com.termux.app.terminal.TerminalSheetController sheet = getTerminalSheetController();
        LinearLayout body = com.termux.app.terminal.TerminalSheetViews.body(this);
        com.termux.app.terminal.TerminalSheetViews.addMessage(body,
            getString(R.string.title_confirm_kill_process));
        LinearLayout actions = com.termux.app.terminal.TerminalSheetViews.addActionRow(body);
        com.termux.app.terminal.TerminalSheetViews.addAction(actions,
            getString(android.R.string.cancel), sheet::dismiss);
        com.termux.app.terminal.TerminalSheetViews.addAction(actions,
            getString(android.R.string.ok), () -> {
                sheet.dismiss();
                session.finishIfRunning();
            });
        sheet.show(getString(R.string.action_kill_process, session.getPid()), body);
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);
            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {

            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;
                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, true, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG, getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));
                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG, getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + IntentUtils.getIntentString(data));
        if ((requestCode == REQUEST_CODE_WIDGET_BIND || requestCode == REQUEST_CODE_WIDGET_CONFIGURE)
            && mWidgetHostController != null
            && mWidgetHostController.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        } else if (requestCode == REQUEST_CODE_VOICE_TYPING) {
            TerminalSession target = mVoiceTypingTargetSession;
            mVoiceTypingTargetSession = null;
            if (resultCode != RESULT_OK || data == null || target == null) return;
            java.util.ArrayList<String> results = data.getStringArrayListExtra(
                android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (results == null) return;
            for (String result : results) {
                if (result != null && !result.trim().isEmpty()) {
                    target.write(result);
                    break;
                }
            }
        }
    }

    private void launchVoiceTyping(boolean chooser) {
        TerminalSession target = getCurrentSession();
        if (target == null) return;
        Intent recognition = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognition.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        if (getPackageManager().resolveActivity(recognition,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) == null) {
            Toast.makeText(this, R.string.voice_typing_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent launch = createVoiceTypingIntent(this, chooser);
        try {
            mVoiceTypingTargetSession = target;
            startActivityForResult(launch, REQUEST_CODE_VOICE_TYPING);
        } catch (android.content.ActivityNotFoundException e) {
            mVoiceTypingTargetSession = null;
            Toast.makeText(this, R.string.voice_typing_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    static Intent createVoiceTypingIntent(@NonNull Context context, boolean chooser) {
        Intent recognition = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognition.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        return chooser ? Intent.createChooser(recognition,
            context.getString(R.string.voice_typing_chooser_title)) : recognition;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: " + Arrays.toString(permissions) + ", grantResults: " + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        } else if (requestCode == REQUEST_CODE_WEATHER_LOCATION) {
            if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ensureWeatherController().forceRefresh();
            }
        } else if (requestCode == REQUEST_CODE_WALLPAPER_READ_PERMISSION) {
            if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                mWallpaperReadPermissionDenied = false;
                clearCachedAccessoryWallpaperBlur();
                scheduleAccessoryRenderSync("wallpaper:permission");
            }
        }
    }

    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public View getAccessoryStackContainerView() {
        return findViewById(R.id.accessory_stack_container);
    }

    public ExtraKeysView getExtraKeysView(int page) {
        return page >= 0 && page < mExtraKeysViews.size() ? mExtraKeysViews.get(page) : null;
    }

    /** The first key page — the one the styling and geometry passes speak for. */
    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys(int page) {
        if (mTermuxTerminalExtraKeysPages.isEmpty()) rebuildExtraKeysPageClients();
        if (page < 0 || page >= mTermuxTerminalExtraKeysPages.size()) page = 0;
        return mTermuxTerminalExtraKeysPages.get(page);
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return getTermuxTerminalExtraKeys(0);
    }

    /**
     * How many key pages the toolbar pager shows. Page 1 always exists; further pages exist only
     * while their property holds keys, so emptying {@code extra-keys2} removes the page rather
     * than leaving a blank one to swipe into.
     */
    public int getExtraKeysPageCount() {
        if (mTermuxTerminalExtraKeysPages.isEmpty()) rebuildExtraKeysPageClients();
        return Math.max(1, mTermuxTerminalExtraKeysPages.size());
    }

    /** Rebuilds one client per configured key page. Cheap: it only re-parses the properties. */
    private void rebuildExtraKeysPageClients() {
        mTermuxTerminalExtraKeysPages.clear();
        for (int page = 0; page < TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length; page++) {
            TermuxTerminalExtraKeys client = new TermuxTerminalExtraKeys(this, mTerminalView,
                mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient, page);
            // Page 1 stays even when empty: the row is the surface the pager is built around.
            if (page > 0 && client.isEmpty()) break;
            mTermuxTerminalExtraKeysPages.add(client);
        }
        if (mTermuxTerminalExtraKeysPages.isEmpty()) {
            mTermuxTerminalExtraKeysPages.add(new TermuxTerminalExtraKeys(this, mTerminalView,
                mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient, 0));
        }
        mTermuxTerminalExtraKeys = mTermuxTerminalExtraKeysPages.get(0);
    }

    /**
     * Re-reads the extra-keys properties and rebuilds every toolbar page. Called after the row
     * editor writes, so an edit lands without a restart — the pager's pages are recreated because
     * a page may have appeared or gone.
     */
    public void reloadExtraKeysFromProperties() {
        if (mProperties == null) return;
        mProperties.loadTermuxPropertiesFromDisk();
        rebuildExtraKeysPageClients();
        ViewPager pager = getTerminalToolbarViewPager();
        if (pager != null && pager.getAdapter() != null) {
            int page = Math.min(pager.getCurrentItem(), getExtraKeysPageCount());
            mExtraKeysViews.clear();
            mExtraKeysView = null;
            pager.getAdapter().notifyDataSetChanged();
            pager.setCurrentItem(page, false);
        }
        setTerminalToolbarHeight();
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView, int page) {
        while (mExtraKeysViews.size() <= page) mExtraKeysViews.add(null);
        mExtraKeysViews.set(page, extraKeysView);
        if (page == 0) mExtraKeysView = extraKeysView;
        applyExtraKeysFeedbackAccent(extraKeysView);
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        setExtraKeysView(extraKeysView, 0);
    }

    /** Tints the extra-keys press feedback with the dock accent so it matches the dock's rim glow. */
    private void applyExtraKeysFeedbackAccent(@Nullable ExtraKeysView extraKeysView) {
        if (extraKeysView != null) {
            extraKeysView.setKeyPressFeedbackColor(resolveDockAccentColor());
            // Soft feathered wash when the dock blur is doing the work; a more present fill otherwise.
            boolean blurActive = mPreferences != null && mPreferences.getExtraKeysBlurRadius() > 0;
            extraKeysView.setKeyPressFeedbackBlurAvailable(blurActive);
            // Floating capsule dock -> vertical liquid popup pill; edge-to-edge dock -> rounded-rect.
            extraKeysView.setPopupCapsuleStyle(isRoundedDockStyle());
            // Drive the per-key glass refraction lens from a pressed key (API 33+ / blur on only).
            extraKeysView.setKeyLensListener(new ExtraKeysView.KeyLensListener() {
                @Override
                public void onKeyLensShow(float l, float t, float r, float b) {
                    setExtraKeyLens(l, t, r, b);
                }
                @Override
                public void onKeyLensHide() {
                    clearExtraKeyLens();
                }
            });
        }
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }

    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }

    public void termuxSessionListNotifyUpdated() {
        // Rebuild the filtered drawer list (also calls notifyDataSetChanged).
        rebuildDrawerSessions();
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isImeVisibleForLayout() {
        return isImeVisible();
    }

    public boolean isWallpaperPassthroughEnabled() {
        return shouldUseWallpaperPassthroughMode();
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }

    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        // Returns the focused pane so all single-view client callbacks act on it.
        return mActivePane != null ? mActivePane : mTerminalView;
    }

    /** All terminal pane views currently rendered (leaves of the active tab). */
    public java.util.List<TerminalView> getTerminalPaneViews() {
        if (mPaneController != null) return mPaneController.getVisiblePaneViews();
        java.util.List<TerminalView> panes = new java.util.ArrayList<>(1);
        if (mTerminalView != null) panes.add(mTerminalView);
        return panes;
    }

    public TerminalFrameMetricsMonitor.Snapshot getTerminalFrameMetricsSnapshot() {
        return mTerminalFrameMetricsMonitor.snapshot();
    }

    /** Reset the window and every currently visible pane to the same benchmark origin. */
    public void resetTerminalPerformanceMetrics() {
        mTerminalFrameMetricsMonitor.reset();
        for (TerminalView pane : getTerminalPaneViews()) pane.resetRenderMetrics();
    }

    /** The pane currently displaying {@code session}, or null if none. */
    @Nullable
    public TerminalView getTerminalViewForSession(@Nullable TerminalSession session) {
        return mPaneController == null ? null : mPaneController.getViewForSession(session);
    }

    /** The pane-tree engine (source of truth for panes/tabs). */
    public com.termux.app.terminal.TerminalPaneController getPaneController() {
        return mPaneController;
    }

    /** Mark {@code view} as the focused pane (touch / programmatic). */
    public void setActivePane(TerminalView view) {
        if (view == null || mPaneController == null) return;
        TerminalSession s = view.getCurrentSession();
        if (s != null) mPaneController.focusSession(s);
    }

    public boolean isSecondaryPaneSession(@Nullable TerminalSession s) {
        // A shell is "secondary" (hidden from the drawer) unless it is the representative of some
        // session = the focused pane of that session's current window.
        if (s == null) return true;
        for (WSession ws : mWSessions)
            if (mPaneController != null
                && mPaneController.windowActiveSession(ws.currentWindow()) == s) return false;
        return true;
    }

    // ---- Window / session helpers ----

    @Nullable private WSession wsessionOwning(com.termux.app.terminal.TerminalPaneController.Window w) {
        for (WSession ws : mWSessions)
            if (ws.windows.contains(w)) return ws;
        return null;
    }

    /** Stable name of the tmux-style session containing {@code shell}. */
    @Nullable
    public String getSessionNameFor(@Nullable TerminalSession shell) {
        if (shell == null || mPaneController == null) return null;
        WSession ws = wsessionOwning(mPaneController.windowOf(shell));
        return ws == null ? TerminalNamePolicy.normalizeSession(shell.mSessionName) : ws.name;
    }

    /** 1-based number of the tmux-style session containing {@code shell}, or -1 when unowned. */
    public int getSessionNumberFor(@Nullable TerminalSession shell) {
        if (shell == null || mPaneController == null) return -1;
        WSession ws = wsessionOwning(mPaneController.windowOf(shell));
        int index = ws == null ? -1 : mWSessions.indexOf(ws);
        return index < 0 ? -1 : index + 1;
    }

    /**
     * Records that the session-switch indicator is about to fire for {@code shell} and answers
     * whether it should: false while the shell still belongs to the session indicated last time,
     * so pane and window churn inside one session never reads as a session switch.
     */
    public boolean noteSessionSwitchIndicated(@Nullable TerminalSession shell) {
        if (shell == null || mPaneController == null) return true;
        WSession ws = wsessionOwning(mPaneController.windowOf(shell));
        if (ws == null) return true;
        if (ws == mLastIndicatedWSession) return false;
        mLastIndicatedWSession = ws;
        return true;
    }

    /** Ctrl+Alt+Shift+R entry point: rename the current session, not its window or focused pane. */
    public boolean promptCurrentSessionRename() {
        return beginTerminalRename(com.termux.app.terminal.TerminalRenameTarget.SESSION);
    }

    /** Ctrl+Alt+R entry point: rename the current window, the tab it occupies in the window bar. */
    public boolean promptCurrentWindowRename() {
        return beginTerminalRename(com.termux.app.terminal.TerminalRenameTarget.WINDOW);
    }

    /**
     * Renames the current session without prompting.
     *
     * <p>Seam for registry actions ({@code session.rename}) and for a naming backend: a remote
     * caller supplies the name up front, so the editor cannot be used — it has no way to return a
     * result. Returns false when there is no session to rename.
     */
    public boolean renameCurrentSessionTo(@Nullable String name) {
        if (mCurrentWSession == null || !mWSessions.contains(mCurrentWSession)) return false;
        mCurrentWSession.name = TerminalNamePolicy.normalizeSession(name);
        rebuildDrawerSessions();
        return true;
    }

    /**
     * Renames the current window without prompting. Seam for {@code window.rename}.
     *
     * <p>An empty name clears the label, which puts the tab back on its derived process/directory
     * text rather than leaving it blank. Returns false when there is no window to rename.
     */
    public boolean renameCurrentWindowTo(@Nullable String name) {
        if (mPaneController == null || mCurrentWSession == null
            || mCurrentWSession.windows.isEmpty()) return false;
        mPaneController.setWindowName(mCurrentWSession.currentWindow(), name);
        refreshTerminalWindowBar();
        refreshSessionsPanel();
        return true;
    }

    /** The name the current window kept, after the policy capped it. */
    @Nullable
    public String getCurrentWindowName() {
        if (mPaneController == null || mCurrentWSession == null
            || mCurrentWSession.windows.isEmpty()) return null;
        return mPaneController.windowName(mCurrentWSession.currentWindow());
    }

    /** Immutable projection consumed by the searchable session browser. */
    @NonNull
    public java.util.List<com.termux.app.terminal.SessionBrowserModel.Session> getSessionBrowserSessions() {
        java.util.List<com.termux.app.terminal.SessionBrowserModel.Session> sessions =
            new java.util.ArrayList<>();
        if (mPaneController == null) return sessions;
        for (int sessionIndex = 0; sessionIndex < mWSessions.size(); sessionIndex++) {
            WSession ws = mWSessions.get(sessionIndex);
            java.util.List<com.termux.app.terminal.SessionBrowserModel.Window> windows =
                new java.util.ArrayList<>();
            for (int windowIndex = 0; windowIndex < ws.windows.size(); windowIndex++) {
                com.termux.app.terminal.TerminalPaneController.Window window = ws.windows.get(windowIndex);
                java.util.List<com.termux.app.terminal.SessionBrowserModel.Pane> panes =
                    new java.util.ArrayList<>();
                java.util.List<TerminalSession> shells = mPaneController.shellsOf(window);
                TerminalSession activeShell = mPaneController.windowActiveSession(window);
                int activePane = activeShell == null ? 0 : shells.indexOf(activeShell);
                if (activePane < 0) activePane = 0;
                for (TerminalSession shell : shells) {
                    String foreground = null;
                    com.termux.app.statusbar.WindowForegroundResolver.ForegroundInfo info =
                        mWindowForegroundResolver == null ? null
                            : mWindowForegroundResolver.get(shell.getPid());
                    if (info != null && !info.idle) {
                        foreground = info.processName;
                        if (info.openFile != null) {
                            foreground = foreground == null ? info.openFile
                                : foreground + " · " + info.openFile;
                        }
                    }
                    if (foreground == null) foreground = shell.getTitle();
                    panes.add(new com.termux.app.terminal.SessionBrowserModel.Pane(
                        shell.getCwd(), foreground));
                }
                String named = mPaneController.windowName(window);
                String windowLabel = named != null ? named
                    : com.termux.app.terminal.TerminalWindowBar
                        .itemFor(activeShell, windowIndex).spokenLabel;
                windows.add(new com.termux.app.terminal.SessionBrowserModel.Window(window.id,
                    windowIndex, windowIndex == ws.current, activePane, panes, windowLabel));
            }
            sessions.add(new com.termux.app.terminal.SessionBrowserModel.Session(ws.id, sessionIndex,
                ws == mCurrentWSession, ws.name, windows));
        }
        return sessions;
    }

    /** Select a browser row's current window and focused pane. */
    public boolean activateBrowserSession(int index) {
        if (mPaneController == null || index < 0 || index >= mWSessions.size()) return false;
        WSession ws = mWSessions.get(index);
        if (ws.windows.isEmpty()) return false;
        TerminalSession shell = mPaneController.windowActiveSession(ws.currentWindow());
        if (shell == null) return false;
        if (getTermuxTerminalSessionClient() != null) {
            getTermuxTerminalSessionClient().setCurrentSession(shell);
        } else {
            activateSessionInPanes(shell);
        }
        return true;
    }

    /** Activate exactly the stable session/window pair selected by the sessions tree. */
    public boolean activateBrowserWindow(long sessionId, long windowId) {
        if (mPaneController == null) return false;
        WSession targetSession = null;
        com.termux.app.terminal.TerminalPaneController.Window targetWindow = null;
        for (WSession session : mWSessions) {
            if (session.id != sessionId) continue;
            targetSession = session;
            for (com.termux.app.terminal.TerminalPaneController.Window window : session.windows) {
                if (window.id == windowId) {
                    targetWindow = window;
                    break;
                }
            }
            break;
        }
        if (targetSession == null || targetWindow == null) return false;
        targetSession.current = targetSession.windows.indexOf(targetWindow);
        TerminalSession shell = mPaneController.windowActiveSession(targetWindow);
        if (shell == null) return false;
        if (getTermuxTerminalSessionClient() != null) {
            getTermuxTerminalSessionClient().setCurrentSession(shell);
        } else {
            activateSessionInPanes(shell);
        }
        return true;
    }

    private int browserSessionIndex(long sessionId) {
        for (int i = 0; i < mWSessions.size(); i++) {
            if (mWSessions.get(i).id == sessionId) return i;
        }
        return -1;
    }

    /** Create a fresh top-level session from the currently focused pane's CWD. */
    public boolean createBrowserSession() {
        com.termux.app.terminal.TermuxTerminalSessionActivityClient client =
            getTermuxTerminalSessionClient();
        TerminalSession current = getCurrentSession();
        return client != null && client.addNewSessionAtWorkingDirectory(
            current == null ? null : current.getCwd(), false, null);
    }

    /** Clone a selected session as a fresh one-window shell at its focused pane's CWD. */
    public boolean cloneBrowserSession(int index) {
        if (mPaneController == null || index < 0 || index >= mWSessions.size()) return false;
        WSession ws = mWSessions.get(index);
        if (ws.windows.isEmpty()) return false;
        TerminalSession source = mPaneController.windowActiveSession(ws.currentWindow());
        com.termux.app.terminal.TermuxTerminalSessionActivityClient client =
            getTermuxTerminalSessionClient();
        return source != null && client != null
            && client.addNewSessionAtWorkingDirectory(source.getCwd(), false, null);
    }

    public boolean cloneCurrentBrowserSession() {
        int index = mCurrentWSession == null ? -1 : mWSessions.indexOf(mCurrentWSession);
        return cloneBrowserSession(index);
    }

    public boolean renameBrowserSession(int index, @Nullable String name) {
        if (index < 0 || index >= mWSessions.size()) return false;
        mWSessions.get(index).name = TerminalNamePolicy.normalizeSession(name);
        rebuildDrawerSessions();
        refreshSessionsPanel();
        return true;
    }

    /**
     * The label a browser-indexed session actually kept. TerminalNamePolicy caps it, so what was
     * asked for and what was stored can differ; callers that report back read this.
     */
    @Nullable
    public String getBrowserSessionName(int index) {
        if (index < 0 || index >= mWSessions.size()) return null;
        return mWSessions.get(index).name;
    }

    /** Close any browser-selected session, without first activating it. */
    public boolean closeBrowserSession(int index) {
        if (mPaneController == null || index < 0 || index >= mWSessions.size()) return false;
        WSession ws = mWSessions.get(index);
        boolean wasCurrent = ws == mCurrentWSession;
        for (com.termux.app.terminal.TerminalPaneController.Window window :
                new java.util.ArrayList<>(ws.windows)) {
            for (TerminalSession shell : mPaneController.removeWindow(window)) {
                if (mTermuxService != null) mTermuxService.killTermuxSession(shell);
            }
        }
        mWSessions.remove(ws);
        if (wasCurrent) {
            mCurrentWSession = null;
            if (!mWSessions.isEmpty()) {
                mCurrentWSession = mWSessions.get(Math.min(index, mWSessions.size() - 1));
                mPaneController.showWindow(mCurrentWSession.currentWindow());
            } else if (getTermuxTerminalSessionClient() != null) {
                getTermuxTerminalSessionClient().addNewSession(false, null);
            }
        }
        rebuildDrawerSessions();
        return true;
    }

    public void setSessionBrowserRefreshCallback(@Nullable Runnable callback) {
        mSessionBrowserRefreshCallback = callback;
    }

    /** Resolve foreground labels for all panes, including inactive sessions and windows. */
    public void requestSessionBrowserForegroundRefresh() {
        if (mPaneController == null) return;
        if (mWindowForegroundResolver == null) {
            mWindowForegroundResolver = new com.termux.app.statusbar.WindowForegroundResolver(
                this::onWindowForegroundResolved);
        }
        java.util.List<Integer> pids = new java.util.ArrayList<>();
        for (WSession ws : mWSessions) {
            for (com.termux.app.terminal.TerminalPaneController.Window window : ws.windows) {
                for (TerminalSession shell : mPaneController.shellsOf(window)) {
                    if (shell.getPid() > 0) pids.add(shell.getPid());
                }
            }
        }
        mWindowForegroundResolver.refresh(pids, android.os.SystemClock.uptimeMillis());
    }

    private void onWindowForegroundResolved() {
        refreshTerminalWindowBar();
        refreshSessionsPanel();
        syncBackgroundProcessStack();
        if (mSessionBrowserRefreshCallback != null) mSessionBrowserRefreshCallback.run();
    }

    /** Seam for {@code window.select}: switch windows by index. False when out of range. */
    public boolean selectWindow(int index) {
        if (mPaneController == null || mCurrentWSession == null
            || index < 0 || index >= mCurrentWSession.windows.size()) return false;
        showWindowFromBar(index);
        return true;
    }

    /** Number of windows in the current tmux-style session. */
    public int getCurrentWindowCount() {
        return mCurrentWSession == null ? 0 : mCurrentWSession.windows.size();
    }

    /** Index of the visible window within the current session, or -1. */
    public int getCurrentWindowIndex() {
        return mCurrentWSession == null ? -1 : mCurrentWSession.current;
    }

    /** Name of the current tmux-style session, or null when unnamed. */
    @Nullable
    public String getCurrentSessionName() {
        return mCurrentWSession == null ? null : mCurrentWSession.name;
    }

    /** Result summary returned by the durable workspace loader. */
    public static final class WorkspaceLoadResult {
        public final int sessions;
        public final int windows;
        public final int panes;
        public final int commandsRun;
        public final int commandsSkipped;
        public final boolean replaced;

        WorkspaceLoadResult(int sessions, int windows, int panes, int commandsRun,
                            int commandsSkipped, boolean replaced) {
            this.sessions = sessions;
            this.windows = windows;
            this.panes = panes;
            this.commandsRun = commandsRun;
            this.commandsSkipped = commandsSkipped;
            this.replaced = replaced;
        }
    }

    /** Capture the complete live session/window/pane topology into a durable JSON file. */
    @NonNull
    public com.termux.app.terminal.TerminalWorkspace saveWorkspace(
            @NonNull String requestedName, boolean overwrite, boolean captureCommands)
            throws com.termux.app.terminal.TerminalWorkspace.WorkspaceException {
        if (mPaneController == null || mWSessions.isEmpty()) {
            throw new com.termux.app.terminal.TerminalWorkspace.WorkspaceException(
                "no_session", "There are no terminal sessions to save");
        }
        String name = com.termux.app.terminal.TerminalWorkspaceStore.validateName(requestedName);
        java.util.List<com.termux.app.terminal.TerminalWorkspace.Session> sessions =
            new java.util.ArrayList<>();
        for (WSession ws : mWSessions) {
            if (ws.windows.isEmpty()) continue;
            java.util.List<com.termux.app.terminal.TerminalWorkspace.Window> windows =
                new java.util.ArrayList<>();
            for (com.termux.app.terminal.TerminalPaneController.Window window : ws.windows) {
                windows.add(mPaneController.snapshotWorkspaceWindow(window, shell -> {
                    String cwd = shell.getCwd();
                    if (cwd == null) cwd = getProperties().getDefaultWorkingDirectory();
                    java.util.List<String> command = java.util.Collections.emptyList();
                    if (captureCommands && mWindowForegroundResolver != null) {
                        com.termux.app.statusbar.WindowForegroundResolver.ForegroundInfo info =
                            mWindowForegroundResolver.get(shell.getPid());
                        if (info != null && !info.idle) command = info.command;
                    }
                    return new com.termux.app.terminal.TerminalWorkspace.Pane(
                        cwd, shell.mSessionName, command);
                }));
            }
            sessions.add(new com.termux.app.terminal.TerminalWorkspace.Session(ws.name,
                Math.max(0, Math.min(ws.current, windows.size() - 1)), windows));
        }
        int current = Math.max(0, Math.min(
            mCurrentWSession == null ? 0 : mWSessions.indexOf(mCurrentWSession), sessions.size() - 1));
        com.termux.app.terminal.TerminalWorkspace workspace =
            new com.termux.app.terminal.TerminalWorkspace(name, System.currentTimeMillis(), current, sessions);
        workspace.validate();
        new com.termux.app.terminal.TerminalWorkspaceStore().save(name, workspace, overwrite);
        return workspace;
    }

    @NonNull
    public java.util.List<com.termux.app.terminal.TerminalWorkspaceStore.Entry> listWorkspaces()
            throws com.termux.app.terminal.TerminalWorkspace.WorkspaceException {
        return new com.termux.app.terminal.TerminalWorkspaceStore().list();
    }

    /** Workspace picker dialog (list + load), the workspace.picker tool's front door. */
    public void showWorkspacePicker() {
        com.termux.app.terminal.TerminalSessionBrowser.showWorkspacePicker(this);
    }

    /** Workspace save-name prompt, the workspace.save_prompt tool's front door. */
    public void promptSaveWorkspace() {
        com.termux.app.terminal.TerminalSessionBrowser.promptSaveWorkspace(this);
    }

    public void deleteWorkspace(@NonNull String name)
            throws com.termux.app.terminal.TerminalWorkspace.WorkspaceException {
        new com.termux.app.terminal.TerminalWorkspaceStore().delete(name);
    }

    /**
     * The shell a restored pane runs its command through and then hands back to.
     *
     * <p>It has to be the user's own shell, because the command was captured from an environment
     * that shell built: {@code ~/.termux/shell} is the symlink {@code chsh} maintains and Termux's
     * own {@code login} follows, so it is the same choice a new session gets. Falling back to a
     * fixed preference order instead would hand a fish user bash, and a command living on a PATH
     * their config sets up would not be found.
     *
     * <p>Deliberately not {@link
     * com.termux.shared.shell.command.environment.UnixShellEnvironment#LOGIN_SHELL_BINARIES}, whose
     * first entry is the {@code login} wrapper script rather than a shell: right for starting a
     * session, but it has no defined {@code -c} behaviour to wrap a command with.
     */
    @Nullable
    private static String wrapperShellPath() {
        java.io.File configured = new java.io.File(
            com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH, ".termux/shell");
        if (configured.canExecute()) {
            try {
                return configured.getCanonicalPath();
            } catch (java.io.IOException ignored) {
                return configured.getAbsolutePath();
            }
        }
        // Same fallback order as login's, so the shell picked here is the shell it would pick.
        String binPath = new com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment()
            .getDefaultBinPath();
        if (binPath != null && !binPath.isEmpty()) {
            for (String shellBinary : new String[] {"bash", "sh"}) {
                java.io.File shellFile = new java.io.File(binPath, shellBinary);
                if (shellFile.canExecute()) return shellFile.getAbsolutePath();
            }
        }
        java.io.File systemShell = new java.io.File("/system/bin/sh");
        return systemShell.canExecute() ? systemShell.getAbsolutePath() : null;
    }

    /** Termux's {@code login}, which sets a session's environment up before exec'ing the shell. */
    @Nullable
    private static String loginProgramPath() {
        String binPath = new com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment()
            .getDefaultBinPath();
        if (binPath == null || binPath.isEmpty()) return null;
        java.io.File login = new java.io.File(binPath, "login");
        return login.canExecute() ? login.getAbsolutePath() : null;
    }

    /** True when the shell parses single-quoted strings fish's way rather than POSIX's. */
    @androidx.annotation.VisibleForTesting
    static boolean isFishShell(@Nullable String shellPath) {
        if (shellPath == null) return false;
        return "fish".equals(new java.io.File(shellPath).getName());
    }

    /**
     * Single-quote an argument so a shell reads it back as the one literal word it was.
     *
     * <p>The two families disagree inside single quotes. POSIX shells take every character
     * literally, so a quote has to end the string, be escaped outside it, and start a new one.
     * Fish instead honours {@code \'} and {@code \\} within the quotes, which means a backslash
     * must be doubled for fish and must not be for anyone else — and an argument ending in a
     * backslash would otherwise escape fish's own closing quote and swallow the rest of the line.
     */
    @NonNull
    @androidx.annotation.VisibleForTesting
    static String shellQuote(@NonNull String argument, boolean fishStyle) {
        if (fishStyle)
            return "'" + argument.replace("\\", "\\\\").replace("'", "\\'") + "'";
        return "'" + argument.replace("'", "'\\''") + "'";
    }

    /** Rebuild a shell command line from captured argv, quoting every word. */
    @NonNull
    @androidx.annotation.VisibleForTesting
    static String shellCommandLine(@NonNull java.util.List<String> command, boolean fishStyle) {
        StringBuilder line = new StringBuilder();
        for (String argument : command) {
            if (line.length() > 0) line.append(' ');
            line.append(shellQuote(argument, fishStyle));
        }
        return line.toString();
    }

    /**
     * Recreate a saved workspace. Shell+CWD restore is the default; foreground argv execution is a
     * separate explicit opt-in because workspace files are user-editable executable input.
     */
    @NonNull
    public WorkspaceLoadResult loadWorkspace(@NonNull String name, boolean replace, boolean runCommands)
            throws com.termux.app.terminal.TerminalWorkspace.WorkspaceException {
        if (!isSplitPanesEnabled()) {
            throw new com.termux.app.terminal.TerminalWorkspace.WorkspaceException(
                "splits_disabled", "Split panes are disabled while compatibility mode is on");
        }
        if (mPaneController == null || mTermuxService == null) {
            throw new com.termux.app.terminal.TerminalWorkspace.WorkspaceException(
                "unavailable", "The terminal service and pane controller must be ready");
        }
        com.termux.app.terminal.TerminalWorkspace workspace =
            new com.termux.app.terminal.TerminalWorkspaceStore().load(name);
        int paneCount = workspace.paneCount();
        int eventualCount = replace ? paneCount : mTermuxService.getTermuxSessionsSize() + paneCount;
        if (eventualCount > com.termux.app.terminal.TermuxTerminalSessionActivityClient.MAX_SESSIONS) {
            throw new com.termux.app.terminal.TerminalWorkspace.WorkspaceException(
                "too_many_panes", "Restoring this workspace would create " + eventualCount
                    + " terminals; the limit is "
                    + com.termux.app.terminal.TermuxTerminalSessionActivityClient.MAX_SESSIONS);
        }

        java.util.List<TerminalSession> createdShells = new java.util.ArrayList<>();
        java.util.List<java.util.List<TerminalSession>> windowShells = new java.util.ArrayList<>();
        int windowCount = 0;
        try {
            for (com.termux.app.terminal.TerminalWorkspace.Session savedSession : workspace.sessions) {
                for (com.termux.app.terminal.TerminalWorkspace.Window savedWindow : savedSession.windows) {
                    java.util.List<com.termux.app.terminal.TerminalWorkspace.Pane> panes =
                        new java.util.ArrayList<>();
                    collectWorkspacePanes(savedWindow.root, panes);
                    // Floating panes come after the tree, matching newWorkspaceWindow's order.
                    for (com.termux.app.terminal.TerminalWorkspace.FloatingPane floating : savedWindow.floats)
                        panes.add(floating.pane);
                    java.util.List<TerminalSession> shells = new java.util.ArrayList<>();
                    for (com.termux.app.terminal.TerminalWorkspace.Pane pane : panes) {
                        String executable = null;
                        String[] arguments = null;
                        if (runCommands && !pane.command.isEmpty()) {
                            String shell = wrapperShellPath();
                            if (shell != null) {
                                // The command is looked up on the PATH the user's own config
                                // builds, since a captured command frequently lives somewhere only
                                // that config knows about, and a shell stays behind once it exits
                                // so a pane restoring `make` does not vanish with the build.
                                boolean fishStyle = isFishShell(shell);
                                String script = shellCommandLine(pane.command, fishStyle)
                                    + "; exec " + shellQuote(shell, fishStyle) + " -l";
                                String login = loginProgramPath();
                                if (login != null) {
                                    // Termux's login ends in `exec "$SHELL" -l "$@"`, so this runs
                                    // the same shell the same way a normal pane does — and only it
                                    // sets up LD_PRELOAD for termux-exec and sources
                                    // termux-login.sh. Passing arguments also skips its motd.
                                    executable = login;
                                    arguments = new String[] {"-c", script};
                                } else {
                                    executable = shell;
                                    arguments = new String[] {"-l", "-c", script};
                                }
                            } else {
                                executable = pane.command.get(0);
                                arguments = pane.command.subList(1, pane.command.size()).toArray(new String[0]);
                            }
                        }
                        String cwd = pane.cwd;
                        if (cwd == null || cwd.isEmpty()) cwd = getProperties().getDefaultWorkingDirectory();
                        com.termux.shared.termux.shell.command.runner.terminal.TermuxSession created =
                            mTermuxService.createTermuxSession(executable, arguments, null, cwd,
                                false, pane.title);
                        if (created == null || created.getTerminalSession() == null) {
                            throw new com.termux.app.terminal.TerminalWorkspace.WorkspaceException(
                                "session_create_failed", "Could not create every workspace pane");
                        }
                        TerminalSession shell = created.getTerminalSession();
                        shells.add(shell);
                        createdShells.add(shell);
                    }
                    windowShells.add(shells);
                    windowCount++;
                }
            }
        } catch (com.termux.app.terminal.TerminalWorkspace.WorkspaceException e) {
            for (TerminalSession shell : createdShells) mTermuxService.killTermuxSession(shell);
            throw e;
        } catch (RuntimeException e) {
            for (TerminalSession shell : createdShells) mTermuxService.killTermuxSession(shell);
            throw new com.termux.app.terminal.TerminalWorkspace.WorkspaceException(
                "session_create_failed", "Could not create workspace panes: " + e.getMessage(), e);
        }

        java.util.List<WSession> restored = new java.util.ArrayList<>();
        java.util.List<com.termux.app.terminal.TerminalPaneController.Window> restoredWindows =
            new java.util.ArrayList<>();
        int shellWindowIndex = 0;
        try {
            for (com.termux.app.terminal.TerminalWorkspace.Session savedSession : workspace.sessions) {
                WSession ws = new WSession();
                ws.name = TerminalNamePolicy.normalizeSession(savedSession.name);
                for (com.termux.app.terminal.TerminalWorkspace.Window savedWindow : savedSession.windows) {
                    com.termux.app.terminal.TerminalPaneController.Window window =
                        mPaneController.newWorkspaceWindow(savedWindow, windowShells.get(shellWindowIndex++));
                    ws.windows.add(window);
                    restoredWindows.add(window);
                }
                ws.current = savedSession.currentWindow;
                restored.add(ws);
            }
        } catch (RuntimeException e) {
            for (com.termux.app.terminal.TerminalPaneController.Window window : restoredWindows)
                mPaneController.removeWindow(window);
            for (TerminalSession shell : createdShells) mTermuxService.killTermuxSession(shell);
            throw new com.termux.app.terminal.TerminalWorkspace.WorkspaceException(
                "invalid_workspace", "Could not rebuild workspace topology: " + e.getMessage(), e);
        }

        int firstRestoredIndex = mWSessions.size();
        if (replace) {
            java.util.List<WSession> oldSessions = new java.util.ArrayList<>(mWSessions);
            mWSessions.clear();
            mCurrentWSession = null;
            for (WSession old : oldSessions) {
                for (com.termux.app.terminal.TerminalPaneController.Window window :
                        new java.util.ArrayList<>(old.windows)) {
                    for (TerminalSession shell : mPaneController.removeWindow(window))
                        mTermuxService.killTermuxSession(shell);
                }
            }
            firstRestoredIndex = 0;
        }
        mWSessions.addAll(restored);
        int selected = firstRestoredIndex + workspace.currentSession;
        mCurrentWSession = mWSessions.get(selected);
        mPaneController.showWindow(mCurrentWSession.currentWindow());
        if (getTermuxTerminalSessionClient() != null)
            getTermuxTerminalSessionClient().checkForFontAndColors();
        rebuildDrawerSessions();
        return new WorkspaceLoadResult(restored.size(), windowCount, paneCount,
            runCommands ? workspace.commandCount() : 0,
            runCommands ? 0 : workspace.commandCount(), replace);
    }

    private static void collectWorkspacePanes(
            @NonNull com.termux.app.terminal.TerminalWorkspace.Node node,
            @NonNull java.util.List<com.termux.app.terminal.TerminalWorkspace.Pane> panes) {
        if (node instanceof com.termux.app.terminal.TerminalWorkspace.Pane) {
            panes.add((com.termux.app.terminal.TerminalWorkspace.Pane) node);
            return;
        }
        com.termux.app.terminal.TerminalWorkspace.Split split =
            (com.termux.app.terminal.TerminalWorkspace.Split) node;
        collectWorkspacePanes(split.a, panes);
        collectWorkspacePanes(split.b, panes);
    }

    /**
     * Seams for the {@code appearance.*} and {@code app.*} registry actions. Each
     * wraps a private handler that the terminal action sheet already invokes, so
     * the palette, a keybind, and a remote caller reach the same code.
     */
    public void openWallpaperPicker() {
        launchManagedWallpaperPicker();
    }

    /** Flips wallpaper passthrough mode and reports the value it moved to. */
    public boolean toggleWallpaperMode() {
        boolean enabled = !shouldUseWallpaperPassthroughMode();
        setWallpaperModeEnabled(this, enabled);
        return enabled;
    }

    /** Whether wallpaper passthrough is currently on. */
    public boolean isWallpaperModeEnabled() {
        return shouldUseWallpaperPassthroughMode();
    }

    /**
     * Flip the cursor trail preference and apply it to every live pane. Returns the value it moved to,
     * which can differ from what the views do while the device is in power save mode.
     */
    public boolean toggleCursorTrail() {
        boolean enabled = !mPreferences.isTerminalCursorTrailEnabled();
        mPreferences.setTerminalCursorTrailEnabled(enabled);
        if (mTermuxTerminalViewClient != null) {
            if (mPaneController != null) {
                for (TerminalView view : mPaneController.getVisiblePaneViews())
                    mTermuxTerminalViewClient.applyCursorTrailPolicy(view);
            }
            mTermuxTerminalViewClient.applyCursorTrailPolicy(getTerminalView());
        }
        return enabled;
    }

    /** Whether the cursor trail preference is on, regardless of power save state. */
    public boolean isCursorTrailEnabled() {
        return mPreferences != null && mPreferences.isTerminalCursorTrailEnabled();
    }

    public void openGlassLab() {
        enterDockTuningMode();
    }

    public void openSettings() {
        openSettingsHome();
    }

    public void openLookAndFeel() {
        openLookAndFeelSettings();
    }

    public void openAppsBar() {
        openAppsBarSettings();
    }

    /** Seam for {@code terminal.reset}: reset the focused shell's emulator state. */
    public boolean resetCurrentSession() {
        TerminalSession session = getCurrentSession();
        if (session == null) return false;
        onResetTerminalSession(session);
        return true;
    }

    /** Drawer entry point for renaming the tmux-style session that contains {@code shell}. */
    public void promptSessionRenameFor(@Nullable TerminalSession shell) {
        if (shell == null || mPaneController == null) return;
        promptSessionRename(wsessionOwning(mPaneController.windowOf(shell)));
    }

    private void promptSessionRename(@Nullable WSession session) {
        if (session == null) return;
        TextInputDialogUtils.textInput(this, R.string.title_rename_session, session.name,
            R.string.action_rename_session_confirm, text -> {
                if (!mWSessions.contains(session)) return;
                session.name = TerminalNamePolicy.normalizeSession(text);
                rebuildDrawerSessions();
            }, -1, null, -1, null, null);
    }

    @Nullable
    private com.termux.shared.termux.shell.command.runner.terminal.TermuxSession findTermuxSession(TerminalSession shell) {
        if (mTermuxService == null || shell == null) return null;
        for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession ts : mTermuxService.getTermuxSessions())
            if (ts.getTerminalSession() == shell) return ts;
        return null;
    }

    /** Ensure every genuinely new service shell that isn't in the restored pane tree gets a window. */
    public void ensureWindowsForServiceSessions() {
        if (mTermuxService == null || mPaneController == null) return;
        for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession ts : mTermuxService.getTermuxSessions()) {
            TerminalSession shell = ts.getTerminalSession();
            if (!com.termux.app.terminal.TerminalPaneController
                .shouldAdoptAsWindowSession(shell == null ? null : shell.mSessionName)) continue;
            if (mPaneController.windowOf(shell) == null) {
                com.termux.app.terminal.TerminalPaneController.Window w = mPaneController.newWindow(shell);
                WSession ws = new WSession();
                ws.windows.add(w);
                ws.current = 0;
                ws.name = TerminalNamePolicy.normalizeSession(shell.mSessionName);
                mWSessions.add(ws);
            }
        }
    }

    @Nullable
    private Bundle savePaneLayoutState() {
        if (mPaneController == null || mWSessions.isEmpty()) return null;
        Bundle root = new Bundle();
        java.util.ArrayList<Bundle> sessionStates = new java.util.ArrayList<>();
        for (WSession ws : mWSessions) {
            if (ws.windows.isEmpty()) continue;
            Bundle sessionState = new Bundle();
            java.util.ArrayList<Bundle> windowStates = new java.util.ArrayList<>();
            for (com.termux.app.terminal.TerminalPaneController.Window window : ws.windows)
                windowStates.add(mPaneController.saveWindow(window));
            sessionState.putParcelableArrayList(PANE_STATE_WINDOWS, windowStates);
            sessionState.putInt(PANE_STATE_CURRENT_WINDOW,
                Math.max(0, Math.min(ws.current, windowStates.size() - 1)));
            if (ws.name != null) sessionState.putString(PANE_STATE_SESSION_NAME, ws.name);
            sessionStates.add(sessionState);
        }
        root.putParcelableArrayList(PANE_STATE_SESSIONS, sessionStates);
        root.putInt(PANE_STATE_CURRENT_SESSION,
            Math.max(0, mCurrentWSession == null ? 0 : mWSessions.indexOf(mCurrentWSession)));
        mPaneController.saveScratchpadState(root);
        return root;
    }

    private void restorePaneLayoutState() {
        Bundle root = mPendingPaneLayoutState;
        mPendingPaneLayoutState = null;
        if (root == null || mTermuxService == null || mPaneController == null) return;
        mPaneController.restoreScratchpadState(root);
        java.util.Map<String, TerminalSession> sessionsByHandle = new java.util.HashMap<>();
        for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession
                : mTermuxService.getTermuxSessions()) {
            TerminalSession terminal = termuxSession.getTerminalSession();
            if (terminal != null) sessionsByHandle.put(terminal.mHandle, terminal);
        }
        java.util.ArrayList<Bundle> sessionStates =
            root.getParcelableArrayList(PANE_STATE_SESSIONS);
        if (sessionStates == null) return;
        mWSessions.clear();
        for (Bundle sessionState : sessionStates) {
            if (sessionState == null) continue;
            java.util.ArrayList<Bundle> windowStates =
                sessionState.getParcelableArrayList(PANE_STATE_WINDOWS);
            if (windowStates == null) continue;
            WSession ws = new WSession();
            for (Bundle windowState : windowStates) {
                com.termux.app.terminal.TerminalPaneController.Window window =
                    mPaneController.restoreWindow(windowState, sessionsByHandle);
                if (window != null) ws.windows.add(window);
            }
            if (ws.windows.isEmpty()) continue;
            ws.current = Math.max(0, Math.min(
                sessionState.getInt(PANE_STATE_CURRENT_WINDOW, 0), ws.windows.size() - 1));
            ws.name = TerminalNamePolicy.normalizeSession(sessionState.getString(PANE_STATE_SESSION_NAME));
            mWSessions.add(ws);
        }
        if (!mWSessions.isEmpty()) {
            int current = Math.max(0, Math.min(root.getInt(PANE_STATE_CURRENT_SESSION, 0),
                mWSessions.size() - 1));
            mCurrentWSession = mWSessions.get(current);
            mPaneController.showWindow(mCurrentWSession.currentWindow());
        }
    }

    /** Rebuild the drawer list: one row per session (its current window's focused shell). */
    public void rebuildDrawerSessions() {
        mDrawerSessions.clear();
        if (mTermuxService != null && mPaneController != null) {
            for (WSession ws : mWSessions) {
                if (ws.windows.isEmpty()) continue;
                TerminalSession rep = mPaneController.windowActiveSession(ws.currentWindow());
                com.termux.shared.termux.shell.command.runner.terminal.TermuxSession ts = findTermuxSession(rep);
                if (ts != null) mDrawerSessions.add(ts);
            }
        }
        if (mTermuxSessionListViewController != null)
            mTermuxSessionListViewController.notifyDataSetChanged();
        if (mTermuxService != null)
            mTermuxService.setVisibleSessionCount(mDrawerSessions.size());
        refreshTerminalWindowBar();
        refreshSessionsPanel();
    }

    /** Wire the app-owned window strip. Window chips are indicators and direct switch targets. */
    private void createFullStatusBarController() {
        mFullStatusBarController = new com.termux.app.statusbar.FullStatusBarController(
            new com.termux.app.statusbar.FullStatusBarController.Host() {
                private View paneHost() { return findViewById(R.id.terminal_window_bar_host); }
                private ViewGroup contentColumn() {
                    View view = findViewById(R.id.terminal_content_column);
                    return view instanceof ViewGroup ? (ViewGroup) view : null;
                }

                @Override public int currentHeight() {
                    View host = paneHost();
                    return host == null ? 0 : currentTopStatusBarHeight(host);
                }
                @Override public int normalHeight(@NonNull com.termux.app.statusbar.TopStatusBarState state) {
                    return targetStatusBarHeightPx(isRoundedDockStyle(), state.toCollapsedPreference());
                }
                @Override public int parentMeasuredHeight() {
                    ViewGroup column = contentColumn();
                    return column == null ? 0 : column.getMeasuredHeight();
                }
                @Override public int parentPaddingTop() {
                    ViewGroup column = contentColumn();
                    return column == null ? 0 : column.getPaddingTop();
                }
                @Override public int parentPaddingBottom() {
                    ViewGroup column = contentColumn();
                    int padding = column == null ? 0 : column.getPaddingBottom();
                    // FULL never touches the dock: when the accessory stack is visible, the pane's
                    // resolved height keeps an 8dp breathing gap above its top edge. Reported as
                    // parent padding so every frame of the spring uses the same shrunken travel —
                    // the accessory bands themselves are never resized or clipped by this.
                    View accessory = findViewById(R.id.accessory_stack_container);
                    if (accessory != null && accessory.getVisibility() == View.VISIBLE) {
                        padding += Math.round(dpToPx(8));
                    }
                    return padding;
                }
                @Override public int hostTopMargin() {
                    View host = paneHost();
                    ViewGroup.LayoutParams params = host == null ? null : host.getLayoutParams();
                    return params instanceof ViewGroup.MarginLayoutParams
                        ? ((ViewGroup.MarginLayoutParams) params).topMargin : 0;
                }
                @Override public boolean reducedMotion() { return isReducedMotionEnabled(); }
                @Override public void cancelNormalAnimatorKeepingCurrent() {
                    if (mStatusBarCollapseAnimator != null) mStatusBarCollapseAnimator.cancel();
                }
                @Override public void beginTerminalResize() {
                    // FULL overlays a live terminal: the terminal never resizes for it, so the
                    // PTY-pause machinery stays untouched. Normal COMPACT/EXPANDED writes keep it.
                    if (mFullOverlayTerminalFrozen) return;
                    mFullStatusBarResizeGeneration = beginStatusBarTerminalResize();
                }
                @Override public void applyFrame(int height, float fullProgress) {
                    View host = paneHost();
                    if (host == null) return;
                    applyTopStatusBarInteractiveHeight(host,
                        findViewById(R.id.terminal_top_widget_area), height, isRoundedDockStyle());
                    applyFullOverlayTerminalShift(height);
                    applyFullOverlayTerminalTint(fullProgress);
                    // See-through pane, but only in flight: the surface tint thins out mid-drag
                    // so the frozen terminal reads through the glass, then recovers by FULL. The
                    // old linear fade rested at 40% tint, which made the settled widget surface
                    // read noticeably brighter than every other glass surface.
                    View surfaceTint = findViewById(R.id.terminal_window_bar_background);
                    if (surfaceTint != null) {
                        float p = Math.max(0f, Math.min(1f, fullProgress));
                        surfaceTint.setAlpha(1f - 0.6f * (4f * p * (1f - p)));
                    }
                    if (host instanceof com.termux.app.statusbar.StatusBarSwipeLayout) {
                        ((com.termux.app.statusbar.StatusBarSwipeLayout) host)
                            .setFullStatusRowBottomInset(Math.round(dpToPx(
                                isRoundedDockStyle() ? 3 : 2)));
                    }
                    applyFullStatusBarOutline(host, fullProgress);
                    View top = findViewById(R.id.terminal_top_widget_area);
                    if (top instanceof com.termux.app.statusbar.TopPaneWidgetSlot) {
                        ((com.termux.app.statusbar.TopPaneWidgetSlot) top)
                            .setFullExpansionProgress(fullProgress);
                    }
                    alignFullStatusBarWallpaperFrost();
                    if (mWidgetPaneController != null) {
                        mWidgetPaneController.onFullFrame(fullProgress);
                    }
                }
                @Override public void finishTerminalResizeAfterLayout() {
                    if (mFullOverlayTerminalFrozen) return;
                    View host = paneHost();
                    if (host != null) finishStatusBarTerminalResizeAfterLayout(host,
                        mFullStatusBarResizeGeneration);
                }
                @Override public void applyNormalState(
                        @NonNull com.termux.app.statusbar.TopStatusBarState state) {
                    applyFullStatusBarPriorState(state);
                }
                @Override public void onEngagementChanged(boolean engaged,
                        @NonNull com.termux.app.statusbar.TopStatusBarState normalTarget) {
                    if (engaged) freezeTerminalForFullOverlay();
                    View view = paneHost();
                    if (view instanceof com.termux.app.statusbar.StatusBarSwipeLayout) {
                        ((com.termux.app.statusbar.StatusBarSwipeLayout) view).setStatusState(
                            engaged ? com.termux.app.statusbar.TopStatusBarState.FULL : normalTarget,
                            normalTarget);
                    }
                    if (!engaged) {
                        unfreezeTerminalAfterFullOverlay();
                        releaseFullStatusBarWallpaperFrost();
                    }
                }
                @Override public void onFullSettled(boolean settled) {
                    if (mWidgetPaneController != null) {
                        mWidgetPaneController.onFullSettled(settled);
                    }
                }
            });
        View contentColumn = findViewById(R.id.terminal_content_column);
        if (contentColumn != null) contentColumn.addOnLayoutChangeListener(
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (mFullStatusBarController != null && (right - left != oldRight - oldLeft
                    || bottom - top != oldBottom - oldTop)) {
                    mFullStatusBarController.onParentLayoutChanged();
                }
            });
    }

    private void createWidgetPaneController() {
        View view = findViewById(R.id.widget_pane);
        // Terminal-only installs keep the FULL pull-down (clock, status) but not the widget grid.
        if (view != null && mPreferences != null && !mPreferences.isAppLauncherWidgetPaneEnabled()) {
            view.setVisibility(View.GONE);
            return;
        }
        if (!(view instanceof com.termux.app.launcher.widget.WidgetPaneView)
            || mWidgetHostController == null || mFullStatusBarController == null) return;
        mWidgetPaneController = new com.termux.app.launcher.widget.WidgetPaneController(
            (com.termux.app.launcher.widget.WidgetPaneView) view, mWidgetHostController,
            new com.termux.app.launcher.widget.WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return isReducedMotionEnabled(); }
                @Override public boolean isFullEngaged() {
                    return mFullStatusBarController != null && mFullStatusBarController.isEngaged();
                }
                @NonNull @Override public com.termux.app.statusbar.TopStatusBarState fullPriorState() {
                    return mFullStatusBarController == null
                        ? com.termux.app.statusbar.TopStatusBarState.EXPANDED
                        : mFullStatusBarController.priorState();
                }
                @Override public void restoreFull(
                        @NonNull com.termux.app.statusbar.TopStatusBarState prior) {
                    View pane = findViewById(R.id.widget_pane);
                    if (pane != null) pane.post(() -> {
                        if (mFullStatusBarController != null
                            && !mFullStatusBarController.isEngaged()) {
                            mFullStatusBarController.restoreFullImmediate(prior);
                        }
                    });
                }
                // A provider text input inside a widget follows the toolbar text-input seam: the
                // in-app keyboard yields (clearing its disable-IME window flags), then the editor
                // takes focus and the system IME. Closing restores the prior arrangement.
                @Override public void onWidgetEditorFocused(@NonNull View editor) {
                    if (mInAppKeyboard != null && mInAppKeyboard.isEnabled()) {
                        mInAppKeyboard.beginExternalTextInput();
                    }
                    onSystemImeRequested();
                    editor.post(() -> {
                        if (!editor.hasFocus()) editor.requestFocus();
                        KeyboardUtils.showSoftKeyboard(TermuxActivity.this, editor);
                    });
                }
                @Override public void onWidgetEditorClosed() {
                    if (mInAppKeyboard != null) mInAppKeyboard.endExternalTextInput();
                }
            });
    }

    /** True while FULL overlays a frozen-geometry, still-running terminal. */
    private boolean mFullOverlayTerminalFrozen;
    private int mFullOverlayHostBaseHeight;
    private float mFullOverlayRestoreWeight;
    private int mFullOverlayRestoreHeight;

    /**
     * FULL never resizes the terminal. The pane host still grows inside the shared column (its
     * bounds must cover the pane for touch and glass), so the terminal surface is frozen at its
     * current pixel height and counter-translated by exactly the host's growth: it keeps its
     * screen position, keeps rendering, and shows through the pane's translucent glass. The host
     * is Z-lifted for the duration so the pane draws over its later-in-column sibling.
     */
    private void freezeTerminalForFullOverlay() {
        if (mFullOverlayTerminalFrozen) return;
        View surface = findViewById(R.id.terminal_surface_host);
        View host = findViewById(R.id.terminal_window_bar_host);
        if (surface == null || host == null || surface.getHeight() <= 0) return;
        ViewGroup.LayoutParams params = surface.getLayoutParams();
        if (!(params instanceof android.widget.LinearLayout.LayoutParams)) return;
        android.widget.LinearLayout.LayoutParams linear =
            (android.widget.LinearLayout.LayoutParams) params;
        mFullOverlayTerminalFrozen = true;
        mFullOverlayHostBaseHeight = currentTopStatusBarHeight(host);
        mFullOverlayRestoreWeight = linear.weight;
        mFullOverlayRestoreHeight = linear.height;
        linear.weight = 0f;
        linear.height = surface.getHeight();
        surface.setLayoutParams(linear);
        host.setTranslationZ(dpToPx(6));
    }

    private void applyFullOverlayTerminalShift(int hostHeight) {
        if (!mFullOverlayTerminalFrozen) return;
        View surface = findViewById(R.id.terminal_surface_host);
        if (surface != null) {
            surface.setTranslationY(-Math.max(0, hostHeight - mFullOverlayHostBaseHeight));
        }
    }

    /**
     * Inverted standardized dim: the running terminal BEHIND the pane darkens with the pane's
     * spring while the glass keeps its own opacity, so the surface reads elevated because the
     * scene under it recedes.
     */
    private void applyFullOverlayTerminalTint(float fullProgress) {
        if (!mFullOverlayTerminalFrozen && fullProgress > 0f) return;
        View surface = findViewById(R.id.terminal_surface_host);
        if (surface == null) return;
        if (mFullOverlayTerminalTint == null) {
            mFullOverlayTerminalTint = new android.graphics.drawable.ColorDrawable();
            surface.setForeground(mFullOverlayTerminalTint);
        }
        int tint = com.termux.app.GlassBackdropTint.colorFor(fullProgress);
        mFullOverlayTerminalTint.setColor(tint);
        // The same progress-driven dim rides the accessory stack as its foreground, so dock and
        // keyboard recede with the terminal instead of ending the scrim hard at the dock's top.
        // A foreground (not an overlay view) draws after every child, whatever z-lift the dock's
        // glass tuning gives them, and never re-orders siblings. The container has no other
        // foreground owner.
        View accessory = findViewById(R.id.accessory_stack_container);
        if (accessory != null) {
            if (mFullOverlayAccessoryTint == null) {
                mFullOverlayAccessoryTint = new android.graphics.drawable.ColorDrawable();
                accessory.setForeground(mFullOverlayAccessoryTint);
            }
            mFullOverlayAccessoryTint.setColor(tint);
        }
    }

    private android.graphics.drawable.ColorDrawable mFullOverlayTerminalTint;
    private android.graphics.drawable.ColorDrawable mFullOverlayAccessoryTint;

    private void unfreezeTerminalAfterFullOverlay() {
        if (!mFullOverlayTerminalFrozen) return;
        mFullOverlayTerminalFrozen = false;
        View surface = findViewById(R.id.terminal_surface_host);
        View host = findViewById(R.id.terminal_window_bar_host);
        if (surface != null) {
            ViewGroup.LayoutParams params = surface.getLayoutParams();
            if (params instanceof android.widget.LinearLayout.LayoutParams) {
                android.widget.LinearLayout.LayoutParams linear =
                    (android.widget.LinearLayout.LayoutParams) params;
                linear.weight = mFullOverlayRestoreWeight;
                linear.height = mFullOverlayRestoreHeight;
                surface.setLayoutParams(linear);
            }
            surface.setTranslationY(0f);
            if (mFullOverlayTerminalTint != null) mFullOverlayTerminalTint.setColor(0);
        }
        if (mFullOverlayAccessoryTint != null) mFullOverlayAccessoryTint.setColor(0);
        if (host != null) host.setTranslationZ(0f);
    }

    private void openFullStatusBar(@NonNull com.termux.app.statusbar.TopStatusBarState prior) {
        if (mFullStatusBarController == null) return;
        if (mAppDrawerController != null) mAppDrawerController.closeImmediate();
        if (mSuggestionBarView != null) mSuggestionBarView.dismissContextPopups();
        mFullStatusBarController.open(prior);
    }

    public boolean isFullStatusBarEngaged() {
        return mFullStatusBarController != null && mFullStatusBarController.isEngaged();
    }

    /** Palette takeover is immediate so two modal glass surfaces never stack. */
    public void closeFullStatusBarImmediate() {
        if (mFullStatusBarController != null) mFullStatusBarController.closeImmediateToPrior();
    }

    private void applyFullStatusBarPriorState(
            @NonNull com.termux.app.statusbar.TopStatusBarState state) {
        boolean collapsed = state.toCollapsedPreference();
        if (mPreferences != null) mPreferences.setTopPaneClockCollapsed(collapsed);
        View topWidgets = findViewById(R.id.terminal_top_widget_area);
        if (topWidgets != null) {
            topWidgets.setClipBounds(null);
            topWidgets.setAlpha(1f);
            topWidgets.setTranslationY(0f);
            topWidgets.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        View host = findViewById(R.id.terminal_window_bar_host);
        if (host instanceof com.termux.app.statusbar.StatusBarSwipeLayout) {
            ((com.termux.app.statusbar.StatusBarSwipeLayout) host).setCollapsed(collapsed);
        }
        com.termux.app.terminal.TerminalWindowBar bar = findViewById(R.id.terminal_window_bar);
        if (bar != null) bar.setStatusBarCollapsed(collapsed);
        refreshTerminalWindowBar();
    }

    private void setTerminalWindowBar() {
        com.termux.app.terminal.TerminalWindowBar bar = findViewById(R.id.terminal_window_bar);
        if (bar == null) return;
        bar.setOnWindowSelectedListener(index -> {
            if (!isSplitPanesEnabled() || mPaneController == null || mCurrentWSession == null
                || index < 0 || index >= mCurrentWSession.windows.size()) return;
            showWindowFromBar(index);
        });
        bar.setOnCreateWindowListener(this::createNewWindow);
        bar.setOnEdgeOverswipeListener(collapsed -> setTopStatusBarCollapsed(collapsed, true));
        com.termux.app.statusbar.SessionsIndicatorView sessionsIndicator =
            findViewById(R.id.terminal_sessions_indicator);
        if (sessionsIndicator != null) {
            // The chip owns the fork's sessions panel. The native drawer stays reachable by its
            // edge swipe and by the app.open_drawer action, but no longer by this tap.
            sessionsIndicator.setOnClickListener(v -> toggleSessionsPanel());
        }
        View statusBarHost = findViewById(R.id.terminal_window_bar_host);
        if (statusBarHost instanceof com.termux.app.statusbar.StatusBarSwipeLayout) {
            com.termux.app.statusbar.StatusBarSwipeLayout swipeHost =
                (com.termux.app.statusbar.StatusBarSwipeLayout) statusBarHost;
            swipeHost.setCollapsed(mPreferences != null
                && mPreferences.isTopPaneClockCollapsed());
            // The FULL pane is a home surface: a terminal-only install must not be able to pull
            // a notification panel down over its terminal, by drag or by long press.
            swipeHost.setFullPaneAvailable(mPreferences == null
                || !mPreferences.isTerminalOnlyUseCase());
            swipeHost.setListener(new com.termux.app.statusbar.StatusBarSwipeLayout.Listener() {
                @Override public void onCollapsedStateRequested(boolean collapsed) {
                    setTopStatusBarCollapsed(collapsed, true);
                }
                @Override public void onFullStateRequested(
                        @NonNull com.termux.app.statusbar.TopStatusBarState priorState) {
                    openFullStatusBar(priorState);
                }
                @Override public boolean isStatusGestureBlocked() {
                    // A normal COMPACT/EXPANDED animator is deliberately eligible for takeover;
                    // FullStatusBarController freezes its current height before cancelling it.
                    return isCommandPaletteOpen() || isAppDrawerEngaged() || mDockTuningMode;
                }
                @Override public boolean onFullDragBegin(
                        @NonNull com.termux.app.statusbar.TopStatusBarState priorState) {
                    if (mFullStatusBarController == null) return false;
                    if (mAppDrawerController != null) mAppDrawerController.closeImmediate();
                    if (mSuggestionBarView != null) mSuggestionBarView.dismissContextPopups();
                    return mFullStatusBarController.dragBegin(priorState);
                }
                @Override public boolean onFullCloseDragBegin() {
                    return mFullStatusBarController != null
                        && mFullStatusBarController.dragBeginClose();
                }
                @Override public void onFullDrag(float dragPx) {
                    if (mFullStatusBarController != null) {
                        mFullStatusBarController.dragUpdate(dragPx);
                    }
                }
                @Override public void onFullDragEnd(float velocityPxPerSec) {
                    if (mFullStatusBarController != null) {
                        mFullStatusBarController.dragEnd(velocityPxPerSec);
                    }
                }
                @Override public void onFullDragCancel() {
                    if (mFullStatusBarController != null) {
                        mFullStatusBarController.dragCancel();
                    }
                }
            });
        }
        bar.setStatusBarCollapsed(mPreferences != null
            && mPreferences.isTopPaneClockCollapsed());
        refreshTerminalWindowBar();
    }

    private void applyTopStatusBarInteractiveHeight(View host, @Nullable View topWidgets,
                                                     int height, boolean capsule) {
        ViewGroup.LayoutParams params = host.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            host.setLayoutParams(params);
        }
        int collapsedHeight = targetStatusBarHeightPx(capsule, true);
        int expandedHeight = targetStatusBarHeightPx(capsule, false);
        float expansion = expandedHeight == collapsedHeight ? 0f
            : Math.max(0f, Math.min(1f,
                (height - collapsedHeight) / (float) (expandedHeight - collapsedHeight)));
        com.termux.app.statusbar.StatusBarResizeGeometry.Row rowGeometry =
            applyInteractiveStatusRowGeometry(height, capsule, collapsedHeight, expandedHeight);
        if (topWidgets != null) {
            topWidgets.setVisibility(View.VISIBLE);
            topWidgets.setAlpha(expansion);
            topWidgets.setTranslationY(-dpToPx(8) * (1f - expansion));
            int clipRight = Math.max(1, Math.max(host.getWidth(), topWidgets.getWidth()));
            int widgetHeight = topWidgets.getHeight() > 0
                ? topWidgets.getHeight() : rowGeometry.clockClipBottom;
            int clipBottom = Math.max(0,
                Math.min(widgetHeight, rowGeometry.clockClipBottom));
            topWidgets.setClipBounds(new Rect(0, 0, clipRight, clipBottom));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // The clip radius must follow the CURRENT interactive height, not the endpoint it is
            // heading to: min(configured, height/2) keeps the compact pill a pill and rounds the
            // growing bar continuously, instead of dragging a stale endpoint radius through every
            // intermediate height (visible as mismatched corners mid-gesture). FULL frames are
            // excluded — applyFullStatusBarOutline re-resolves their radius right after this.
            if (!isFullStatusBarEngaged()) {
                float radius = capsule ? resolveStatusBarCapsuleCornerRadiusPx(height) : 0f;
                mStatusBarSurfaceOutline.setFrame(radius, radius, 0f);
                if (host.getOutlineProvider() != mStatusBarSurfaceOutline)
                    host.setOutlineProvider(mStatusBarSurfaceOutline);
                host.setClipToOutline(mStatusBarSurfaceOutline.clipsCorners());
            }
            host.invalidateOutline();
        }
    }

    private com.termux.app.statusbar.StatusBarResizeGeometry.Row
            applyInteractiveStatusRowGeometry(int surfaceHeight, boolean capsule,
                                              int collapsedHeight, int expandedHeight) {
        int collapsedRowHeight = Math.round(dpToPx(capsule ? 22 : 24));
        int expandedRowHeight = Math.round(dpToPx(24));
        int expandedBottomMargin = Math.round(dpToPx(capsule ? 3 : 2));
        com.termux.app.statusbar.StatusBarResizeGeometry.Row geometry =
            isFullStatusBarEngaged()
                ? com.termux.app.statusbar.StatusBarResizeGeometry.calculateFull(surfaceHeight,
                    expandedHeight, Math.max(expandedHeight,
                        com.termux.app.statusbar.FullStatusBarGeometry.resolveFullHeight(
                            findViewById(R.id.terminal_content_column) == null ? 0
                                : findViewById(R.id.terminal_content_column).getMeasuredHeight(),
                            findViewById(R.id.terminal_content_column) == null ? 0
                                : findViewById(R.id.terminal_content_column).getPaddingTop(),
                            findViewById(R.id.terminal_content_column) == null ? 0
                                : findViewById(R.id.terminal_content_column).getPaddingBottom(),
                            0)), expandedRowHeight, expandedBottomMargin)
                : com.termux.app.statusbar.StatusBarResizeGeometry.calculate(surfaceHeight,
                    collapsedHeight, expandedHeight, collapsedRowHeight, expandedRowHeight,
                    expandedBottomMargin);

        View statusRow = findViewById(R.id.terminal_status_row);
        if (statusRow != null && statusRow.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) statusRow.getLayoutParams();
            params.gravity = Gravity.TOP;
            params.topMargin = geometry.top;
            params.bottomMargin = 0;
            params.height = geometry.height;
            statusRow.setLayoutParams(params);
        }

        View sessions = findViewById(R.id.terminal_sessions_indicator);
        if (sessions != null && sessions.getLayoutParams() != null) {
            int collapsedSessionHeight = Math.round(dpToPx(capsule ? 18 : 20));
            int expandedSessionHeight = Math.round(dpToPx(20));
            int sessionHeight = Math.round(collapsedSessionHeight
                + (expandedSessionHeight - collapsedSessionHeight) * geometry.expansion);
            ViewGroup.LayoutParams params = sessions.getLayoutParams();
            params.height = sessionHeight;
            if (sessions instanceof com.termux.app.statusbar.SessionsIndicatorView
                && ((com.termux.app.statusbar.SessionsIndicatorView) sessions).isShowingSessionNumber()) {
                params.width = sessionHeight;
            }
            sessions.setLayoutParams(params);
        }
        return geometry;
    }

    private int currentTopStatusBarHeight(View host) {
        ViewGroup.LayoutParams params = host.getLayoutParams();
        return params != null && params.height > 0 ? params.height : host.getHeight();
    }

    private int beginStatusBarTerminalResize() {
        int generation = ++mStatusBarTerminalResizeGeneration;
        if (mPaneController != null) mPaneController.beginHostSurfaceResize();
        return generation;
    }

    private void finishStatusBarTerminalResizeAfterLayout(View host, int generation) {
        // The posted resume runs after the requested terminal-surface layout. Each pane then sends
        // exactly one final row/column update instead of a SIGWINCH for every animation frame.
        host.post(() -> {
            if (mPaneController != null) mPaneController.finishHostSurfaceResizeKeepingBottom();
        });
    }

    private void setTopStatusBarCollapsed(boolean collapsed, boolean animate) {
        if (mPreferences == null) return;
        if (isFullStatusBarEngaged()) {
            // FULL's captured prior remains authoritative; ordinary two-state requests cannot
            // write geometry or start a competing animator until FULL has settled closed.
            return;
        }
        View host = findViewById(R.id.terminal_window_bar_host);
        View topWidgets = findViewById(R.id.terminal_top_widget_area);
        com.termux.app.statusbar.StatusBarSwipeLayout swipeHost = host instanceof
            com.termux.app.statusbar.StatusBarSwipeLayout
            ? (com.termux.app.statusbar.StatusBarSwipeLayout) host : null;
        boolean capsule = isRoundedDockStyle();
        int targetHeight = targetStatusBarHeightPx(capsule, collapsed);
        boolean preferenceChanged = mPreferences.isTopPaneClockCollapsed() != collapsed;
        boolean geometryChanged = host != null && currentTopStatusBarHeight(host) != targetHeight;
        if (swipeHost != null) swipeHost.setCollapsed(collapsed);
        com.termux.app.terminal.TerminalWindowBar windowBar =
            findViewById(R.id.terminal_window_bar);
        if (windowBar != null) windowBar.setStatusBarCollapsed(collapsed);
        if (!preferenceChanged && !geometryChanged) {
            if (topWidgets != null) {
                topWidgets.setClipBounds(null);
                topWidgets.setAlpha(1f);
                topWidgets.setTranslationY(0f);
                topWidgets.setVisibility(collapsed ? View.GONE : View.VISIBLE);
            }
            refreshTerminalWindowBar();
            if (host != null) finishStatusBarTerminalResizeAfterLayout(host,
                mStatusBarTerminalResizeGeneration);
            return;
        }
        if (preferenceChanged) mPreferences.setTopPaneClockCollapsed(collapsed);
        if (host == null) {
            refreshTerminalWindowBar();
            return;
        }
        if (!animate) {
            int resizeGeneration = beginStatusBarTerminalResize();
            refreshTerminalWindowBar();
            finishStatusBarTerminalResizeAfterLayout(host, resizeGeneration);
            return;
        }

        if (mStatusBarCollapseAnimator != null) mStatusBarCollapseAnimator.cancel();
        final int resizeGeneration = beginStatusBarTerminalResize();
        int startHeight = currentTopStatusBarHeight(host);
        if (startHeight <= 0) startHeight = targetStatusBarHeightPx(capsule, !collapsed);
        applyTopStatusBarInteractiveHeight(host, topWidgets, startHeight, capsule);
        mStatusBarCollapseAnimator = android.animation.ValueAnimator.ofInt(startHeight, targetHeight);
        int fullDistance = Math.max(1, targetStatusBarHeightPx(capsule, false)
            - targetStatusBarHeightPx(capsule, true));
        long settleDuration = Math.max(90L,
            Math.round(260f * Math.abs(targetHeight - startHeight) / fullDistance));
        mStatusBarCollapseAnimator.setDuration(settleDuration);
        mStatusBarCollapseAnimator.setInterpolator(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? new android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f)
            : new android.view.animation.DecelerateInterpolator(1.8f));
        mStatusBarCollapseAnimator.addUpdateListener(animation -> {
            int height = (Integer) animation.getAnimatedValue();
            applyTopStatusBarInteractiveHeight(host, topWidgets, height, capsule);
        });
        mStatusBarCollapseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (topWidgets != null) {
                    topWidgets.setClipBounds(null);
                    topWidgets.setAlpha(1f);
                    topWidgets.setTranslationY(0f);
                    topWidgets.setVisibility(collapsed ? View.GONE : View.VISIBLE);
                }
                mStatusBarCollapseAnimator = null;
                refreshTerminalWindowBar();
                finishStatusBarTerminalResizeAfterLayout(host, resizeGeneration);
            }
        });
        mStatusBarCollapseAnimator.start();
    }

    /** Refresh visibility, labels, selection and the shared dock/keyboard glass treatment. */
    public void refreshTerminalWindowBar() {
        View host = findViewById(R.id.terminal_window_bar_host);
        com.termux.app.terminal.TerminalWindowBar bar = findViewById(R.id.terminal_window_bar);
        if (host == null || bar == null) return;
        boolean visible = isSplitPanesEnabled();
        host.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            applyTerminalSurfaceAppearance();
            // Idempotence: the widgets live inside the GONE host here, so nothing is shown either
            // way — but updateStatusWidgets also owns starting and stopping the sampler, and
            // skipping it would make that depend on which mode the user happens to be in.
            updateStatusWidgets();
            return;
        }

        com.termux.app.terminal.TerminalClockWidget clock = findViewById(R.id.terminal_clock_widget);
        if (clock != null) {
            if (mPreferences != null) {
                clock.setStyle(mPreferences.getTopPaneClockStyle());
                clock.setAlignment(mPreferences.getTopPaneClockAlignment());
                clock.setUseAmPm(mPreferences.isTopPaneClockAmPmEnabled());
            }
            // Only reachable while the panel is expanded: the widget slot the clock lives in is GONE
            // in the collapsed bar, so this needs no state check of its own.
            if (clock.getTag() == null) {
                clock.setTag("wired");
                clock.setOnClickListener(v -> openSystemClockApp());
            }
        }

        float opacity = mPreferences != null ? mPreferences.getStatusBarOpacity() / 100f : 1f;
        int blurRadiusDp = getEffectiveStatusBarBlurRadius();
        boolean windowBarBlurEnabled = dockBlurEnabled(blurRadiusDp);
        View blur = findViewById(R.id.terminal_window_bar_blur);
        applyRealtimeBlurRadius(blur, blurRadiusDp);
        applyRealtimeBlurDownsampleFactor(blur, ACCESSORY_BLUR_DOWNSAMPLE_FACTOR);
        // Same tinted-overlay treatment as the dock's extraKeysBackgroundBlur: colored when blur
        // is actually contributing, transparent otherwise.
        // Blur only; the glass drawable below owns the tint. See the status-glass caller above.
        applyRealtimeBlurOverlayColor(blur, Color.TRANSPARENT);
        if (blur != null) blur.setVisibility(windowBarBlurEnabled ? View.VISIBLE : View.GONE);
        boolean capsuleStatusBar = isRoundedDockStyle();
        View background = findViewById(R.id.terminal_window_bar_background);
        if (background != null) {
            // The capsule floats below the status bar as its own slab, so its glass spans the full
            // pane height. The default pane merges with the behind-status glass, so it renders only
            // the lower slice and the extension draws the rest.
            background.setBackground(buildStatusBarGlassSurface(opacity,
                capsuleStatusBar ? 0f : terminalWindowGlassStatusFraction(host), 1f, true));
        }
        applyStatusBarStyle(host);
        applyTerminalWindowBarBackdropInsets();

        com.termux.app.statusbar.SessionsIndicatorView sessionsIndicator =
            findViewById(R.id.terminal_sessions_indicator);
        if (sessionsIndicator != null) {
            int sessionIndex = mCurrentWSession == null ? -1 : mWSessions.indexOf(mCurrentWSession);
            sessionsIndicator.setSession(currentStatusSessionName(),
                mWSessions.size(), sessionIndex);
        }

        java.util.List<Integer> foregroundPids = syncWindowBarItems(bar);
        scheduleWindowLabelPoll(foregroundPids);
        updateStatusWidgets();
        // The tab a chip hangs from can move when the row re-lays out (a window opened, a label
        // widened), so a live chip follows it instead of drifting off its anchor.
        if (mRenameCoordinator != null && mRenameCoordinator.isActive())
            mRenameCoordinator.reposition();
    }

    /**
     * Push the current window list — labels, selection and working state — into {@code bar}, and
     * return the pane pids for the foreground resolver. Split out of
     * {@link #refreshTerminalWindowBar()} so the shell-activity refresh can update the pills without
     * redoing the bar's glass, blur and palette work several times a second.
     */
    @NonNull
    private java.util.List<Integer> syncWindowBarItems(
            @NonNull com.termux.app.terminal.TerminalWindowBar bar) {
        java.util.List<com.termux.app.terminal.TerminalWindowBar.WindowItem> items =
            new java.util.ArrayList<>();
        java.util.List<Integer> foregroundPids = new java.util.ArrayList<>();
        int selected = -1;
        if (mCurrentWSession != null && mPaneController != null) {
            long now = android.os.SystemClock.uptimeMillis();
            selected = Math.max(0, Math.min(mCurrentWSession.current,
                mCurrentWSession.windows.size() - 1));
            for (int i = 0; i < mCurrentWSession.windows.size(); i++) {
                com.termux.app.terminal.TerminalPaneController.Window window =
                    mCurrentWSession.windows.get(i);
                TerminalSession session = mPaneController.windowActiveSession(window);
                // Looking at the window is the acknowledgement: whatever it wanted, the user is here.
                if (i == selected) clearWindowAttention(window);
                items.add(buildWindowItem(session, i, foregroundPids,
                    mPaneController.windowName(window))
                    .withBusy(isWindowWorking(window, now))
                    .withAttention(isWindowAwaitingUser(window)));
            }
        }
        bar.setWindows(items, selected);
        syncBackgroundProcessStack();
        return collectAllPanePids();
    }

    @NonNull
    private java.util.List<Integer> collectAllPanePids() {
        java.util.LinkedHashSet<Integer> unique = new java.util.LinkedHashSet<>();
        if (mPaneController != null) {
            for (WSession session : mWSessions) {
                for (com.termux.app.terminal.TerminalPaneController.Window window : session.windows) {
                    for (TerminalSession shell : mPaneController.shellsOf(window)) {
                        if (shell.getPid() > 0) unique.add(shell.getPid());
                    }
                }
            }
        }
        return new java.util.ArrayList<>(unique);
    }

    public void syncBackgroundProcessStack() {
        java.util.List<com.termux.app.statusbar.BackgroundProcessModel.Snapshot> snapshots =
            new java.util.ArrayList<>();
        if (mPaneController != null && mWindowForegroundResolver != null) {
            java.util.HashSet<Integer> seenShells = new java.util.HashSet<>();
            for (WSession session : mWSessions) {
                for (com.termux.app.terminal.TerminalPaneController.Window window : session.windows) {
                    for (TerminalSession shell : mPaneController.shellsOf(window)) {
                        int shellPid = shell.getPid();
                        if (shellPid < 1 || !seenShells.add(shellPid)) continue;
                        com.termux.app.statusbar.WindowForegroundResolver.ForegroundInfo info =
                            mWindowForegroundResolver.get(shellPid);
                        if (info == null || info.idle || info.foregroundPid < 1) continue;
                        com.termux.terminal.TerminalEmulator emulator = shell.getEmulator();
                        boolean running = emulator == null || !emulator.hasShellIntegration()
                            || emulator.isShellIntegrationCommandRunning();
                        snapshots.add(new com.termux.app.statusbar.BackgroundProcessModel.Snapshot(
                            session.id, shellPid, info.foregroundPid, info.processName,
                            shell.getTitle(), running));
                    }
                }
            }
        }
        long now = android.os.SystemClock.elapsedRealtime();
        mBackgroundProcessModel.update(snapshots, now);
        // Session, not pane: moving between the windows of one session leaves their pills on screen,
        // and the pills already carry the working and waiting states. Only leaving the session hides
        // them, which is the case this corner covers.
        long focusedSessionId = mCurrentWSession == null ? -1L : mCurrentWSession.id;
        java.util.List<com.termux.app.statusbar.BackgroundProcessModel.Entry> visible =
            mBackgroundProcessModel.visibleEntries(focusedSessionId, now);
        long untilNext = mBackgroundProcessModel.msUntilNextVisible(focusedSessionId, now);
        mBackgroundProcessHandler.removeCallbacks(mBackgroundProcessResync);
        if (untilNext >= 0L)
            mBackgroundProcessHandler.postDelayed(mBackgroundProcessResync, untilNext);
        if (mBackgroundProcessStack == null && visible.isEmpty()) return;
        com.termux.app.statusbar.BackgroundProcessStackView stack = obtainBackgroundProcessStack();
        if (stack != null) stack.bind(visible);
    }

    /**
     * Record that {@code session} asked for the user. The terminal bell is the signal: it is what
     * shells, editors, and agent CLIs already ring when a command finishes or a prompt needs an
     * answer, so no cooperation from the program is required.
     *
     * <p>A bell from the window the user is already in is not news, and is dropped.
     */
    public void noteShellAttention(@NonNull TerminalSession session) {
        int pid = session.getPid();
        if (pid < 1 || mPaneController == null) return;
        if (mCurrentWSession != null
            && mPaneController.shellsOf(mCurrentWSession.currentWindow()).contains(session)) return;
        if (!mAttentionShellPids.add(pid)) return;
        refreshTerminalWindowBar();
    }

    public void clearShellAttention(int shellPid) {
        if (mAttentionShellPids.remove(shellPid)) refreshTerminalWindowBar();
    }

    private void clearWindowAttention(
            @NonNull com.termux.app.terminal.TerminalPaneController.Window window) {
        if (mPaneController == null || mAttentionShellPids.isEmpty()) return;
        for (TerminalSession shell : mPaneController.shellsOf(window)) {
            mAttentionShellPids.remove(shell.getPid());
        }
    }

    private boolean isWindowAwaitingUser(
            @NonNull com.termux.app.terminal.TerminalPaneController.Window window) {
        if (mPaneController == null || mAttentionShellPids.isEmpty()) return false;
        for (TerminalSession shell : mPaneController.shellsOf(window)) {
            if (mAttentionShellPids.contains(shell.getPid())) return true;
        }
        return false;
    }

    /**
     * Whether any shell in {@code window} has a command actively working in it — an agent thinking, a
     * build compiling — as opposed to merely having something on screen.
     *
     * <p>The signal is the foreground process group's CPU use between the resolver's polls. That is
     * what separates the three states that all look identical to an output-only signal: a shell
     * echoing what is being typed at it, a TUI sitting there repainting its clock once a second, and
     * an agent actually working. Only the last of the three burns CPU.
     *
     * <p>Input silences the indication outright: while the user is typing, the pane is being
     * interacted with, not working in the background, whatever its process spends on rendering the
     * keystrokes.
     *
     * <p>Where no CPU reading is available — no privileged backend, an unreadable procfs, or simply
     * the first poll of a newly started command — the pane falls back to sustained output activity,
     * excluding full-screen (alternate buffer) applications, since a repainting TUI is exactly what
     * that fallback cannot tell apart from work. A reading that exists and is below the threshold is
     * final, though: that is the answer, not a gap to be filled in.
     */
    private boolean isWindowWorking(
            @NonNull com.termux.app.terminal.TerminalPaneController.Window window, long nowMs) {
        if (mPaneController == null) return false;
        for (TerminalSession shell : mPaneController.shellsOf(window)) {
            int pid = shell.getPid();
            if (pid <= 0) continue;
            long lastWrite = shell.getLastWriteUptimeMs();
            if (lastWrite > 0L && nowMs - lastWrite < SHELL_INPUT_GRACE_MS) continue;
            com.termux.app.statusbar.WindowForegroundResolver.ForegroundInfo info =
                mWindowForegroundResolver == null ? null : mWindowForegroundResolver.get(pid);
            if (info != null && info.idle) continue;          // the shell itself has the terminal
            if (info != null) {
                if (info.working) return true;
                if (info.cpuFraction >= 0d) continue;         // measured, and it is not working
            }
            if (isFullScreenApplication(shell)) continue;
            if (mShellActivityTracker.isWorking(pid, nowMs)) return true;
        }
        return false;
    }

    /** Whether {@code shell} has a full-screen application on the alternate screen buffer. */
    private static boolean isFullScreenApplication(@NonNull TerminalSession shell) {
        com.termux.terminal.TerminalEmulator emulator = shell.getEmulator();
        return emulator != null && emulator.isAlternateBufferActive();
    }

    /**
     * Record output from {@code session} and schedule one coalesced refresh of the working
     * indication.
     *
     * <p>This is tmux's monitor-activity: {@code onTextChanged} already fires on every screen
     * update, so no privileged backend and no procfs polling is involved.
     *
     * <p>Deliberately not on mWindowLabelHandler despite being the same kind of work:
     * scheduleWindowLabelPoll calls removeCallbacksAndMessages(null) on that handler, which would
     * drop a pending refresh and leave the coalescing flag stuck.
     */
    public void noteShellActivity(@Nullable TerminalSession session) {
        if (session == null || !isSplitPanesEnabled()) return;
        int pid = session.getPid();
        if (pid <= 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        mShellActivityTracker.noteActivity(pid, now);
        mShellActivityTracker.pruneBefore(now - 4 * com.termux.app.statusbar.ShellActivityTracker.DECAY_MS);
        if (mShellActivityRefreshPending) return;
        mShellActivityRefreshPending = true;
        mShellActivityHandler.postDelayed(mShellActivityRefresh, SHELL_ACTIVITY_REFRESH_MS);
    }

    private void refreshShellActivityIndication() {
        mShellActivityRefreshPending = false;
        com.termux.app.terminal.TerminalWindowBar bar = findViewById(R.id.terminal_window_bar);
        if (bar != null && isSplitPanesEnabled()) syncWindowBarItems(bar);
        scheduleShellActivityDecay();
    }

    /**
     * One refresh at the moment the soonest still-active shell stops counting as working, instead of
     * polling for the whole time nothing is happening.
     */
    private void scheduleShellActivityDecay() {
        mShellActivityHandler.removeCallbacks(mShellActivityDecay);
        long now = android.os.SystemClock.uptimeMillis();
        long expiry = mShellActivityTracker.nextExpiryMs(now);
        if (expiry < 0L) return;
        mShellActivityHandler.postDelayed(mShellActivityDecay, Math.max(50L, expiry - now) + 20L);
    }

    @Nullable
    private CharSequence currentStatusSessionName() {
        return mCurrentWSession == null ? null : mCurrentWSession.name;
    }

    /**
     * Build a window pill label following the priority: open file basename (editors) &gt; foreground
     * process name &gt; directory basename (idle). Falls back to the title/cwd label when foreground
     * detection has no data yet. Collects the pane pid into {@code foregroundPids} for the next
     * resolver poll.
     */
    @NonNull
    private com.termux.app.terminal.TerminalWindowBar.WindowItem buildWindowItem(
            @Nullable TerminalSession session, int index, @NonNull java.util.List<Integer> foregroundPids,
            @Nullable String windowName) {
        if (session == null) {
            return windowName == null
                ? com.termux.app.terminal.TerminalWindowBar.itemFor(null, index)
                : com.termux.app.terminal.TerminalWindowBar.itemForNamed(windowName, null);
        }
        int pid = session.getPid();
        if (pid > 0) foregroundPids.add(pid);
        com.termux.app.statusbar.WindowForegroundResolver.ForegroundInfo info =
            mWindowForegroundResolver == null ? null : mWindowForegroundResolver.get(pid);
        // A user-given name outranks every derived label: the whole point of naming a window is that
        // its tab stops changing under you as the foreground process comes and goes.
        if (windowName != null) {
            String process = info != null && !info.idle ? info.processName
                : com.termux.app.terminal.TerminalWindowBar.processName(session.getTitle());
            return com.termux.app.terminal.TerminalWindowBar.itemForNamed(windowName, process);
        }
        if (info != null && !info.idle && info.processName != null) {
            if (info.openFile != null) {
                return com.termux.app.terminal.TerminalWindowBar.itemForResolved(info.processName,
                    com.termux.app.terminal.TerminalWindowBar.truncateFile(info.openFile),
                    info.processName + " editing " + info.openFile);
            }
            return com.termux.app.terminal.TerminalWindowBar.itemForResolved(info.processName,
                com.termux.app.terminal.TerminalWindowBar.truncateProcess(info.processName),
                info.processName);
        }
        // Idle or not yet resolved: directory basename via the existing title/cwd derivation.
        return com.termux.app.terminal.TerminalWindowBar.itemFor(session, index);
    }

    /** Kick the throttled foreground resolver and keep it polling while the window bar is shown. */
    private void scheduleWindowLabelPoll(@NonNull java.util.List<Integer> pids) {
        mWindowLabelHandler.removeCallbacksAndMessages(null);
        if (pids.isEmpty()) return;
        if (mWindowForegroundResolver == null) {
            mWindowForegroundResolver = new com.termux.app.statusbar.WindowForegroundResolver(
                this::onWindowForegroundResolved);
        }
        mWindowForegroundResolver.refresh(pids, android.os.SystemClock.uptimeMillis());
        mWindowLabelHandler.postDelayed(() -> {
            if (mWindowForegroundResolver != null && isSplitPanesEnabled()) {
                mWindowForegroundResolver.refresh(pids, android.os.SystemClock.uptimeMillis());
                scheduleWindowLabelPoll(pids);
            }
        }, WINDOW_LABEL_POLL_MS);
    }

    // ---- Trailing status widgets (CPU / RAM / weather) + their anchored detail cards ----

    /** Apply widget visibility from preferences, wire taps once, and drive the data controllers. */
    private void updateStatusWidgets() {
        com.termux.app.statusbar.StatusBarWidgetView cpu = findViewById(R.id.terminal_status_widget_cpu);
        com.termux.app.statusbar.StatusBarWidgetView ram = findViewById(R.id.terminal_status_widget_ram);
        com.termux.app.statusbar.StatusBarWidgetView weather = findViewById(R.id.terminal_status_widget_weather);
        com.termux.app.statusbar.MaterialDotSeparatorView cpuRamDot =
            findViewById(R.id.terminal_status_dot_cpu_ram);
        com.termux.app.statusbar.MaterialDotSeparatorView ramWeatherDot =
            findViewById(R.id.terminal_status_dot_ram_weather);
        boolean cpuOn = mPreferences != null && mPreferences.isStatusWidgetCpuEnabled();
        boolean ramOn = mPreferences != null && mPreferences.isStatusWidgetRamEnabled();
        boolean weatherOn = mPreferences != null && mPreferences.isStatusWidgetWeatherEnabled();

        if (cpu != null) {
            cpu.setVisibility(cpuOn ? View.VISIBLE : View.GONE);
            cpu.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.PRIMARY);
            cpu.setIconResource(R.drawable.ic_stat_cpu);
            if (cpu.getTag() == null) {
                cpu.setTag("wired");
                cpu.setOnClickListener(v -> toggleStatsCard(v));
            }
        }
        if (ram != null) {
            ram.setVisibility(ramOn ? View.VISIBLE : View.GONE);
            ram.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
            ram.setIconResource(R.drawable.ic_stat_ram);
            if (ram.getTag() == null) {
                ram.setTag("wired");
                ram.setOnClickListener(v -> toggleStatsCard(v));
            }
        }
        if (weather != null) {
            weather.setVisibility(weatherOn ? View.VISIBLE : View.GONE);
            weather.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.TERTIARY);
            if (weather.getTag() == null) {
                weather.setTag("wired");
                weather.setIconResource(R.drawable.ic_weather_clear_day);
                weather.setValue("--");
                weather.setOnClickListener(v -> toggleWeatherCard(v));
            }
        }

        if (cpuRamDot != null) {
            boolean show = cpuOn && (ramOn || weatherOn);
            cpuRamDot.setVisibility(show ? View.VISIBLE : View.GONE);
            cpuRamDot.setColorRole(ramOn
                ? com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY
                : com.termux.app.statusbar.StatusBarWidgetView.ColorRole.TERTIARY);
        }
        if (ramWeatherDot != null) {
            ramWeatherDot.setVisibility(ramOn && weatherOn ? View.VISIBLE : View.GONE);
            ramWeatherDot.setColorRole(
                com.termux.app.statusbar.StatusBarWidgetView.ColorRole.TERTIARY);
        }

        if (cpuOn || ramOn) {
            boolean cardShowing = mStatusCardHost.isShowing() && mStatsCardView != null;
            ensureStatsController().start(
                cardShowing ? STATS_CARD_INTERVAL_MS : STATS_BAR_INTERVAL_MS, cardShowing);
        } else if (mStatsController != null) {
            mStatsController.stop();
        }

        if (weatherOn) {
            ensureWeatherController().refreshIfStale();
        } else if (mWeatherController != null) {
            mWeatherController.stop();
        }
    }

    @NonNull
    private com.termux.app.statusbar.SystemStatsController ensureStatsController() {
        if (mStatsController == null) {
            mStatsController = new com.termux.app.statusbar.SystemStatsController(this, this::onStatsUpdated);
        }
        return mStatsController;
    }

    /**
     * The one callback behind two surfaces with opposite needs. The card is a monitor and gets the raw
     * snapshot at whatever cadence it asked for; the bar is a glance and goes through the smoothers,
     * which repaint it on their own calm rhythm and, in the common case, say "nothing changed" so no
     * view is touched at all.
     */
    private void onStatsUpdated(@NonNull com.termux.app.statusbar.SystemStatsController.Stats stats) {
        long nowMs = android.os.SystemClock.uptimeMillis();
        com.termux.app.statusbar.StatusBarWidgetView cpu = findViewById(R.id.terminal_status_widget_cpu);
        com.termux.app.statusbar.StatusBarWidgetView ram = findViewById(R.id.terminal_status_widget_ram);
        if (cpu != null && cpu.getVisibility() == View.VISIBLE
            && mBarCpuSmoother.offer(stats.cpuPercent, nowMs)) {
            cpu.setValue(mBarCpuSmoother.text());
        }
        if (ram != null && ram.getVisibility() == View.VISIBLE) {
            int memPct = stats.memTotalKb > 0
                ? (int) Math.round(100.0 * stats.memUsedKb / stats.memTotalKb) : -1;
            if (mBarMemorySmoother.offer(memPct, nowMs)) ram.setValue(mBarMemorySmoother.text());
        }
        if (mStatsCardView != null && mStatusCardHost.isShowing()) {
            mStatsCardView.bind(stats);
        }
    }

    private void toggleStatsCard(@NonNull View anchor) {
        if (mStatusCardHost.isShowing()) {
            boolean same = mStatusCardHost.isShowingFor(anchor);
            mStatusCardHost.dismiss();
            if (same) return;
        }
        if (mStatsCardView == null) {
            mStatsCardView = new com.termux.app.statusbar.SystemStatsCardView(this);
        }
        detachFromParent(mStatsCardView);
        mStatsCardView.bind(ensureStatsController().latest());
        ensureStatsController().start(STATS_CARD_INTERVAL_MS, true);
        setWidgetAccent(anchor, true);
        mStatusCardHost.setDropEdge(findViewById(R.id.terminal_window_bar_host));
        mStatusCardHost.show(anchor, mStatsCardView, statusCardStyleProvider(), () -> {
            setWidgetAccent(anchor, false);
            if (mStatsController != null
                && mPreferences != null
                && (mPreferences.isStatusWidgetCpuEnabled() || mPreferences.isStatusWidgetRamEnabled())) {
                mStatsController.start(STATS_BAR_INTERVAL_MS, false);
            }
        });
    }

    /**
     * Seam for {@code session.panel} and for the status-row session chip: drop the fork's sessions
     * list beneath the chip, or close it when it is already the open card. Another status card gives
     * way to it, matching how the stats and weather cards trade places.
     */
    public void toggleSessionsPanel() {
        View anchor = findViewById(R.id.terminal_sessions_indicator);
        if (anchor == null) return;
        if (mStatusCardHost.isShowing()) {
            boolean same = mStatusCardHost.isShowingFor(anchor);
            mStatusCardHost.dismissAnimated();
            if (same) return;
        }
        if (mSessionsPanelView == null) {
            mSessionsPanelView = new com.termux.app.statusbar.SessionsPanelView(this);
            mSessionsPanelView.setListener(sessionsPanelListener());
        }
        detachFromParent(mSessionsPanelView);
        mSessionsPanelView.setSurfaceStyle(isRoundedDockStyle(),
            resolveStatusBarCapsuleCornerRadiusPx(Math.round(dpToPx(44))));
        mSessionsPanelView.bind(getSessionBrowserSessions());
        mStatusCardHost.setDropEdge(findViewById(R.id.terminal_window_bar_host));
        mStatusCardHost.showPanel(anchor, mSessionsPanelView, statusCardStyleProvider(),
            mSessionsPanelView.desiredWidthDp(), null);
        requestSessionBrowserForegroundRefresh();
    }

    /** Whether the sessions panel is the card currently on screen. */
    public boolean isSessionsPanelShowing() {
        View anchor = findViewById(R.id.terminal_sessions_indicator);
        return anchor != null && mStatusCardHost.isShowingFor(anchor);
    }

    /** Re-bind the open panel after a session was created, closed, renamed, or switched. */
    private void refreshSessionsPanel() {
        if (mSessionsPanelView == null || !isSessionsPanelShowing()) return;
        mSessionsPanelView.bind(getSessionBrowserSessions());
    }

    @NonNull
    private com.termux.app.statusbar.SessionsPanelView.Listener sessionsPanelListener() {
        return new com.termux.app.statusbar.SessionsPanelView.Listener() {
            @Override
            public void onWindowSelected(long sessionId, long windowId) {
                if (activateBrowserWindow(sessionId, windowId)) mStatusCardHost.dismissAnimated();
            }

            @Override
            public void onSessionClosed(long sessionId) {
                int index = browserSessionIndex(sessionId);
                if (!sessionHasForegroundJob(index)) {
                    // Nothing is running, so the panel stays open and just loses the row.
                    closeBrowserSession(index);
                    return;
                }
                mStatusCardHost.dismissAnimated();
                confirmCloseBrowserSession(index);
            }

            @Override
            public void onSessionRenameRequested(long sessionId) {
                int index = browserSessionIndex(sessionId);
                mStatusCardHost.dismissAnimated();
                if (index < 0 || index >= mWSessions.size()) return;
                beginSessionRenameAtIndex(index);
            }

            @Override
            public void onNewSession() {
                if (createBrowserSession()) mStatusCardHost.dismissAnimated();
            }

            @Override
            public void onNewSessionPrompt() {
                mStatusCardHost.dismissAnimated();
                promptNewSession();
            }
        };
    }

    /** True when any pane of the session at {@code index} has a non-idle foreground process. */
    private boolean sessionHasForegroundJob(int index) {
        if (mPaneController == null || mWindowForegroundResolver == null
            || index < 0 || index >= mWSessions.size()) return false;
        for (com.termux.app.terminal.TerminalPaneController.Window window :
                mWSessions.get(index).windows) {
            for (TerminalSession shell : mPaneController.shellsOf(window)) {
                com.termux.app.statusbar.WindowForegroundResolver.ForegroundInfo info =
                    mWindowForegroundResolver.get(shell.getPid());
                if (info != null && !info.idle) return true;
            }
        }
        return false;
    }

    /** Shared close confirmation for the sessions panel; mirrors the browser's wording. */
    private void confirmCloseBrowserSession(int index) {
        if (index < 0 || index >= mWSessions.size() || mPaneController == null) return;
        WSession session = mWSessions.get(index);
        int paneCount = 0;
        for (com.termux.app.terminal.TerminalPaneController.Window window : session.windows)
            paneCount += mPaneController.shellsOf(window).size();
        String title = TerminalNamePolicy.normalizeSession(session.name) == null
            ? getString(R.string.session_browser_unnamed, index + 1)
            : getString(R.string.session_browser_named, index + 1, session.name);
        final int panes = paneCount;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.session_browser_close_title, title))
            .setMessage(getResources().getQuantityString(
                R.plurals.session_browser_close_message, panes, panes))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.session_browser_close,
                (dialog, which) -> closeBrowserSession(index))
            .show();
    }

    /** Named-session prompt with the fail-safe alternative, shared by the drawer and the panel. */
    public void promptNewSession() {
        if (mTermuxTerminalSessionActivityClient == null) return;
        TextInputDialogUtils.textInput(this, R.string.title_create_named_session, null,
            R.string.action_create_named_session_confirm,
            text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
            R.string.action_new_session_failsafe,
            text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text), -1, null, null);
    }

    @NonNull
    private com.termux.app.statusbar.StatusCardHost.StyleProvider statusCardStyleProvider() {
        return new com.termux.app.statusbar.StatusCardHost.StyleProvider() {
            @Override
            public Drawable cardBackground(boolean panel) {
                int surface = getTermuxThemeColor(
                    com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                    R.color.termux_surface_panel_high);
                int outline = getTermuxThemeColor(
                    com.termux.shared.R.attr.termuxColorOutlineVariant,
                    R.color.termux_outline_variant);
                GradientDrawable materialSurface = new GradientDrawable();
                materialSurface.setColor(withAlphaComponent(surface, 248));
                materialSurface.setCornerRadius(panel && isRoundedDockStyle()
                    ? resolveStatusBarCapsuleCornerRadiusPx(Integer.MAX_VALUE) : dpToPx(16));
                materialSurface.setStroke(Math.max(1, Math.round(dpToPx(1))),
                    withAlphaComponent(outline, 118));
                return materialSurface;
            }

            @Override
            public float cornerRadiusPx(boolean panel) {
                return panel && isRoundedDockStyle()
                    ? resolveStatusBarCapsuleCornerRadiusPx(Integer.MAX_VALUE) : dpToPx(16);
            }

            @Override
            public float contentInsetPx(boolean panel) {
                return dpToPx(panel ? 8 : 12);
            }
        };
    }

    private void setWidgetAccent(@NonNull View anchor, boolean accent) {
        if (anchor instanceof com.termux.app.statusbar.StatusBarWidgetView) {
            ((com.termux.app.statusbar.StatusBarWidgetView) anchor).setAccent(accent);
        }
    }

    private static void detachFromParent(@NonNull View view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    @NonNull
    private com.termux.app.statusbar.WeatherController ensureWeatherController() {
        if (mWeatherController == null) {
            mWeatherController = new com.termux.app.statusbar.WeatherController(this, this::onWeatherUpdated);
        }
        return mWeatherController;
    }

    private void onWeatherUpdated(@NonNull com.termux.app.statusbar.WeatherController.Weather weather) {
        com.termux.app.statusbar.StatusBarWidgetView widget = findViewById(R.id.terminal_status_widget_weather);
        if (widget != null && widget.getVisibility() == View.VISIBLE) {
            if (weather.valid) {
                widget.setIconResource(com.termux.app.statusbar.WeatherController.iconFor(
                    weather.currentCode, weather.currentIsDay));
                widget.setValue(com.termux.app.statusbar.WeatherController.formatTemp(weather.currentC,
                    mPreferences != null && mPreferences.isStatusWidgetWeatherFahrenheit()));
            } else {
                widget.setValue("--°");
            }
        }
        if (mWeatherCardView != null && mStatusCardHost.isShowing()) {
            mWeatherCardView.bind(weather);
        }
    }

    private void toggleWeatherCard(@NonNull View anchor) {
        if (mStatusCardHost.isShowing()) {
            boolean same = mStatusCardHost.isShowingFor(anchor);
            mStatusCardHost.dismiss();
            if (same) return;
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                new String[] {android.Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_CODE_WEATHER_LOCATION);
        }
        if (mWeatherCardView == null) {
            mWeatherCardView = new com.termux.app.statusbar.WeatherCardView(this);
        }
        detachFromParent(mWeatherCardView);
        mWeatherCardView.bind(ensureWeatherController().cache());
        ensureWeatherController().refreshIfStale();
        setWidgetAccent(anchor, true);
        mStatusCardHost.setDropEdge(findViewById(R.id.terminal_window_bar_host));
        mStatusCardHost.show(anchor, mWeatherCardView, statusCardStyleProvider(), 360,
            () -> setWidgetAccent(anchor, false));
    }

    /** Switch directly from the app-owned window row and settle the terminal with a short wave. */
    private void showWindowFromBar(int index) {
        if (mPaneController == null || mCurrentWSession == null
            || index < 0 || index >= mCurrentWSession.windows.size()) return;
        int previous = mCurrentWSession.current;
        if (previous == index) return;
        mStatusCardHost.dismiss();
        mCurrentWSession.current = index;
        mPaneController.showWindow(mCurrentWSession.currentWindow());
        rebuildDrawerSessions();
        animateTerminalWindowArrival(index >= previous ? 1 : -1);
    }

    private void animateTerminalWindowArrival(int direction) {
        View terminal = findViewById(R.id.terminal_surface_host);
        if (terminal == null) return;
        terminal.animate().cancel();
        terminal.setAlpha(0.78f);
        terminal.setTranslationX((direction < 0 ? -1f : 1f) * dpToPx(34));
        terminal.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(com.termux.app.terminal.TerminalWindowBar
                .WINDOW_SWITCH_ANIMATION_DURATION_MS)
            .setInterpolator(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ? new android.view.animation.PathInterpolator(0.16f, 1f, 0.3f, 1f)
                : new android.view.animation.DecelerateInterpolator(1.8f))
            .start();
    }

    /** Index of {@code session} within the drawer-visible list, or -1. */
    public int getDrawerIndexOfSession(TerminalSession session) {
        for (int i = 0; i < mDrawerSessions.size(); i++)
            if (mDrawerSessions.get(i).getTerminalSession() == session) return i;
        return -1;
    }

    /**
     * Show the window that owns {@code session} and focus that pane. A brand-new shell (not in any
     * window yet) becomes its own new session with a single window.
     * @return true if the focused session changed.
     */
    public boolean activateSessionInPanes(TerminalSession session) {
        if (session == null || mPaneController == null) return false;
        TerminalSession previousFocused = getCurrentSession();
        com.termux.app.terminal.TerminalPaneController.Window w = mPaneController.windowOf(session);
        if (w == null) {
            // A scratchpad shell that isn't in any tree is hidden, not new: it belongs to
            // toggleScratchpad as a float, so minting a window session for it here would add a
            // bogus row to the sessions panel.
            if (!com.termux.app.terminal.TerminalPaneController
                .shouldAdoptAsWindowSession(session.mSessionName)) return false;
            // New shell -> its own session with one window.
            w = mPaneController.newWindow(session);
            WSession ws = new WSession();
            ws.windows.add(w);
            ws.current = 0;
            ws.name = TerminalNamePolicy.normalizeSession(session.mSessionName);
            mWSessions.add(ws);
            mCurrentWSession = ws;
        } else {
            WSession ws = wsessionOwning(w);
            if (ws != null) { mCurrentWSession = ws; ws.current = ws.windows.indexOf(w); }
        }
        mPaneController.showWindow(w);
        mPaneController.focusSession(session);
        // Apply font/colours (nerd-font typeface) to every populated pane.
        if (getTermuxTerminalSessionClient() != null)
            getTermuxTerminalSessionClient().checkForFontAndColors();
        for (TerminalView v : getTerminalPaneViews())
            if (v.getCurrentSession() != null) v.onScreenUpdated();
        rebuildDrawerSessions();
        return previousFocused != session;
    }

    /** Whether custom window/pane splitting is active (disabled by compatibility mode). */
    public boolean isSplitPanesEnabled() {
        return getPreferences() == null || !getPreferences().isCompatibilityModeEnabled();
    }

    @Nullable
    public TerminalSession getCurrentTabPrimary() {
        // The current session's drawer representative (its current window's focused shell), so
        // session cycling lines up with the drawer list.
        if (mCurrentWSession != null && mPaneController != null && !mCurrentWSession.windows.isEmpty())
            return mPaneController.windowActiveSession(mCurrentWSession.currentWindow());
        return mCurrentTabPrimary;
    }

    /** Split the focused pane, spawning a new shell in the new pane. orientation = LinearLayout.*. */
    public void splitCurrentPane(int orientation) {
        if (!isSplitPanesEnabled() || mPaneController == null) return;
        if (mTermuxService == null || mPaneController.getActiveSession() == null) {
            showSessionSwitchIndicator(getString(R.string.msg_no_session_to_split));
            return;
        }
        // Successful pane creation says so too: with the notice chip carrying the refusals, silence
        // on success would be the only unlabelled outcome.
        if (mPaneController.split(orientation)) {
            int panes = mPaneController.getVisiblePaneViews().size();
            showSessionSwitchIndicator(getResources().getQuantityString(
                R.plurals.msg_pane_count, panes, panes));
        }
    }

    /** Move focus to the pane in the given arrow direction (Ctrl+Alt+arrow). No-op if none. */
    public boolean focusPaneDirection(int keyCode) {
        return mPaneController == null || mPaneController.focusDirection(keyCode);
    }

    /** Adjust the split ratio toward the arrow direction (Ctrl+Alt+Shift+arrow). */
    public boolean resizeActivePane(int keyCode) {
        return mPaneController == null || mPaneController.resizeActive(keyCode);
    }

    /** The focused pane's pinned font size, or 0 while it follows the app-wide default. */
    public int getActivePaneFontSize() {
        return mPaneController == null ? 0 : mPaneController.getActivePaneFontSize();
    }

    /** Pin the focused pane's font size; false when no pane controller is active. */
    public boolean setActivePaneFontSize(int size) {
        return mPaneController != null && mPaneController.setActivePaneFontSize(size);
    }

    /** Apply an automatic pane layout to the current window. */
    public boolean applyPaneLayout(@NonNull String layout) {
        return isSplitPanesEnabled() && mPaneController != null && mPaneController.applyLayout(layout);
    }

    /** Advance the current window to the next automatic pane layout and retain it. */
    public boolean cyclePaneLayout() {
        return isSplitPanesEnabled() && mPaneController != null && mPaneController.nextLayout();
    }

    /** The current window's retained automatic layout, or null when manually managed. */
    @Nullable
    public String activePaneLayoutPolicy() {
        return mPaneController == null ? null : mPaneController.activeLayoutPolicy();
    }

    /** Reset every split in the current window to a 1:1 divider ratio. */
    public boolean equalizePaneLayout() {
        return isSplitPanesEnabled() && mPaneController != null && mPaneController.equalizeLayout();
    }

    /** Rotate the current pane tree geometrically by ninety degrees. */
    public boolean rotatePaneLayout(boolean clockwise) {
        return isSplitPanesEnabled() && mPaneController != null
            && mPaneController.rotateLayout(clockwise);
    }

    /** Move the focused pane to an outer edge of the current window. */
    public boolean moveFocusedPaneToEdge(@NonNull String edge) {
        return isSplitPanesEnabled() && mPaneController != null
            && mPaneController.moveActivePaneToEdge(edge);
    }

    /** Kill the focused pane's shell (Alt+Esc). Teardown/promotion happens in onSessionFinished. */
    public boolean killFocusedPane() {
        TerminalView active = getTerminalView();
        if (active == null) return false;
        TerminalSession s = active.getCurrentSession();
        if (s == null) return false;
        s.finishIfRunning();
        return true;
    }

    /** Create a shell in {@code cwd} (or the default) via the service; null on failure. */
    @Nullable TerminalSession createShellForCwd(@Nullable String cwd) {
        return createShellForCwd(cwd, null);
    }

    @Nullable TerminalSession createShellForCwd(@Nullable String cwd, @Nullable String sessionName) {
        if (mTermuxService == null) return null;
        if (mTermuxService.getTermuxSessionsSize()
                >= com.termux.app.terminal.TermuxTerminalSessionActivityClient.MAX_SESSIONS) {
            showSessionSwitchIndicator(getString(R.string.title_max_terminals_reached) + " — "
                + getString(R.string.msg_max_terminals_reached));
            return null;
        }
        if (cwd == null) cwd = getProperties().getDefaultWorkingDirectory();
        com.termux.shared.termux.shell.command.runner.terminal.TermuxSession created =
            mTermuxService.createTermuxSession(null, null, null, cwd, false, sessionName);
        return created == null ? null : created.getTerminalSession();
    }

    /** Whether any window of any session currently displays this shell as a pane. */
    private boolean isShellDisplayedInAnyWindow(@NonNull TerminalSession session) {
        if (mPaneController == null) return false;
        for (WSession ws : mWSessions) {
            for (com.termux.app.terminal.TerminalPaneController.Window window : ws.windows) {
                if (mPaneController.shellsOf(window).contains(session)) return true;
            }
        }
        return false;
    }

    /** New window in the current session (Ctrl+Alt+C): a fresh shell as a new pane tree. */
    public void createNewWindow() {
        if (!isSplitPanesEnabled() || mPaneController == null) return;
        if (mCurrentWSession == null) { // no session yet -> behave like new session
            getTermuxTerminalSessionClient().addNewSession(false, null);
            return;
        }
        TerminalSession cur = getCurrentSession();
        TerminalSession shell = createShellForCwd(cur != null ? cur.getCwd() : null);
        if (shell == null) return;
        com.termux.app.terminal.TerminalPaneController.Window w = mPaneController.newWindow(shell);
        mCurrentWSession.windows.add(w);
        mCurrentWSession.current = mCurrentWSession.windows.size() - 1;
        mPaneController.showWindow(w);
        animateTerminalWindowArrival(1);
        showSessionSwitchIndicator(getString(R.string.msg_window_position,
            mCurrentWSession.current + 1, mCurrentWSession.windows.size()));
        rebuildDrawerSessions();
    }

    /** Close the current window (Ctrl+Alt+X): kill its panes; if it was the session's last window,
     *  close the session too. */
    public void closeCurrentWindow() {
        if (mPaneController == null || mCurrentWSession == null) return;
        com.termux.app.terminal.TerminalPaneController.Window w = mPaneController.activeWindow();
        if (w == null) return;
        for (TerminalSession s : mPaneController.removeWindow(w))
            if (mTermuxService != null) mTermuxService.killTermuxSession(s);
        mCurrentWSession.windows.remove(w);
        if (mCurrentWSession.windows.isEmpty()) {
            mWSessions.remove(mCurrentWSession);
            mCurrentWSession = null;
            showNextSessionAfterClose();
        } else {
            mCurrentWSession.current = Math.min(mCurrentWSession.current, mCurrentWSession.windows.size() - 1);
            mPaneController.showWindow(mCurrentWSession.currentWindow());
        }
        rebuildDrawerSessions();
    }

    /** Switch to the next/previous window within the current session (Ctrl+Alt+] / [). */
    public void switchWindow(boolean forward) {
        if (mPaneController == null || mCurrentWSession == null) return;
        int n = mCurrentWSession.windows.size();
        if (n < 2) return;
        int target = ((mCurrentWSession.current + (forward ? 1 : -1)) % n + n) % n;
        showWindowFromBar(target);
        showSessionSwitchIndicator(getString(R.string.msg_window_position, target + 1, n));
    }

    /** Close the whole current session (Ctrl+Alt+Shift+X): all its windows + panes. */
    public void closeCurrentSession() {
        if (mPaneController == null || mCurrentWSession == null) {
            // Fallback: close the current shell's session the classic way.
            TerminalSession cur = getCurrentSession();
            if (cur != null && getTermuxTerminalSessionClient() != null)
                getTermuxTerminalSessionClient().removeFinishedSession(cur);
            return;
        }
        WSession ws = mCurrentWSession;
        for (com.termux.app.terminal.TerminalPaneController.Window w : new java.util.ArrayList<>(ws.windows))
            for (TerminalSession s : mPaneController.removeWindow(w))
                if (mTermuxService != null) mTermuxService.killTermuxSession(s);
        mWSessions.remove(ws);
        mCurrentWSession = null;
        showNextSessionAfterClose();
        rebuildDrawerSessions();
    }

    /** After a session closes, show another session, or spawn a fresh one if none remain. */
    private void showNextSessionAfterClose() {
        if (!mWSessions.isEmpty()) {
            mCurrentWSession = mWSessions.get(0);
            mPaneController.showWindow(mCurrentWSession.currentWindow());
        } else if (getTermuxTerminalSessionClient() != null) {
            getTermuxTerminalSessionClient().addNewSession(false, null);
        }
    }

    /** Drop a window from its session after its last pane finished (called from onSessionFinished). */
    public void onWindowEmptied(com.termux.app.terminal.TerminalPaneController.Window w) {
        WSession ws = wsessionOwning(w);
        if (ws == null) return;
        ws.windows.remove(w);
        if (ws == mCurrentWSession) {
            if (ws.windows.isEmpty()) {
                mWSessions.remove(ws);
                mCurrentWSession = null;
                showNextSessionAfterClose();
            } else {
                ws.current = Math.min(ws.current, ws.windows.size() - 1);
                mPaneController.showWindow(ws.currentWindow());
            }
        } else if (ws.windows.isEmpty()) {
            mWSessions.remove(ws);
        }
        rebuildDrawerSessions();
    }

    /** Collapse every window back to single panes (used when entering compatibility mode). */
    public void collapseAllSplits() {
        if (mPaneController == null) return;
        for (TerminalSession sec : mPaneController.collapseAll())
            if (mTermuxService != null) mTermuxService.killTermuxSession(sec);
        rebuildDrawerSessions();
    }

    /** Bridges {@link com.termux.app.terminal.TerminalPaneController} back into the activity. */
    private final class PaneHost implements com.termux.app.terminal.TerminalPaneController.Host {
        @Override @Nullable public TerminalSession createShell(@Nullable String cwd) {
            return createShellForCwd(cwd);
        }

        @Override @Nullable public TerminalSession createNamedShell(@NonNull String name,
                                                                    @Nullable String cwd) {
            return createShellForCwd(cwd, name);
        }

        /**
         * A live shell carrying this session name that no window currently displays. Lets the
         * scratchpad re-adopt its shell after a hide or an activity restart instead of piling
         * up fresh ones.
         */
        @Override @Nullable public TerminalSession findIdleShellByName(@NonNull String name) {
            if (mTermuxService == null || mPaneController == null) return null;
            for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession
                    : mTermuxService.getTermuxSessions()) {
                if (!name.equals(termuxSession.getExecutionCommand().shellName)) continue;
                TerminalSession session = termuxSession.getTerminalSession();
                if (session != null && !isShellDisplayedInAnyWindow(session)) return session;
            }
            return null;
        }

        @Override public void configurePaneView(TerminalView view) {
            view.setTerminalViewClient(mTermuxTerminalViewClient);
            if (getPreferences() != null) {
                view.setTextSize(getPreferences().getFontSize());
                view.setKeepScreenOn(getPreferences().shouldKeepScreenOn());
            }
            if (mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.applyCursorTrailPolicy(view);
            // A pane created while the key inspector is open must report through it too.
            com.termux.app.terminal.TerminalKeyInspector.attachTo(view);
            view.setUseTransparentFrameClear(false);
            view.setBackgroundColor(Color.TRANSPARENT);
            view.setTransparentFrameOverlayColor(Color.TRANSPARENT);
            view.setSplitChar(getSuggestionBarSplitChar());
            if (getTermuxTerminalSessionClient() != null)
                getTermuxTerminalSessionClient().applyFontToView(view);
        }

        @Override public void configureAttachedPaneView(TerminalView view, TerminalSession session) {
            if (getPreferences() == null || view == null) return;
            view.setTextSize(com.termux.app.terminal.TerminalPaneController
                .isScratchpadShellName(session == null ? null : session.mSessionName)
                ? getPreferences().getScratchpadFontSize()
                : getPreferences().getFontSize());
        }

        @Override public void removeShell(TerminalSession session) {
            if (mTermuxService != null) mTermuxService.killTermuxSession(session);
        }

        @Override public void onActivePaneChanged() {
            TerminalView v = mPaneController.getActivePaneView();
            if (v != null) {
                mActivePane = v;
                mTerminalView = v;
                if (mTermuxTerminalExtraKeys != null)
                    mTermuxTerminalExtraKeys.setTerminalView(v);
            }
            mCurrentTabPrimary = mPaneController.getActiveSession();
            refreshTerminalWindowBar();
        }

        @Override public void onTreesChanged() {
            rebuildDrawerSessions();
        }

        @Override public void onPanesRendered() {
            // Who owns the frame line depends on how many panes are up, so re-decide it here.
            applyTerminalBorderAppearance();
        }

        @Override public String defaultCwd() {
            return getProperties().getDefaultWorkingDirectory();
        }
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        TerminalView active = getTerminalView();
        if (active != null)
            return active.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }

    public void updateWindowBackgroundForCurrentSession() {
        if (getWindow() == null) {
            return;
        }
        View decorView = getWindow().getDecorView();
        View contentView = findViewById(android.R.id.content);
        View backgroundHost = contentView != null ? contentView : decorView;
        if (shouldUseWallpaperPassthroughMode()) {
            // Theme.TermuxActivity.Wallpaper already asks WindowManager to show the system
            // wallpaper. Do not install a second WallpaperManager drawable on an app view: the
            // app content frame begins below the status-bar inset on Android 16, so that drawable
            // gets center-cropped to a shorter frame while the native wallpaper remains visible in
            // the inset. Two different crops of the same image produce the hard y=inset seam.
            decorView.setBackgroundColor(Color.TRANSPARENT);
            if (backgroundHost != decorView) {
                backgroundHost.setBackgroundColor(Color.TRANSPARENT);
            }
            return;
        }
        int surfaceColor = getTermuxThemeColor(
            com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        decorView.setBackgroundColor(surfaceColor);
        if (backgroundHost != decorView) {
            backgroundHost.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyFullscreenMode();
        }
        if (!hasFocus || mIsInvalidState || !mIsVisible) {
            return;
        }
        mAccessoryBackdropDirty = true;
        mDecorNavBarBackdropDirty = true;
        mInAppKeyboardBackdropDirty = true;
        // Returning from another app can restore focus before the terminal host re-measures to full
        // size, leaving panes stuck at a tiny stale grid. Re-measure once layout settles.
        if (mPaneController != null)
            mPaneController.refreshPaneSizes();
        scheduleAccessoryRenderSync("window:focus");
        restartAccessoryBlurHeartbeat();
        scheduleAccessoryBlurRecovery();
    }

    @Override
    public void reloadSuggestionBar(char inputChar) {
        if (!isSuggestionBarEnabled() || mSuggestionBarView == null || mTerminalView == null) {
            return;
        }
        resetAzGestureState(false, true);
        mSuggestionBarView.onTerminalInteraction();
        if (inputChar == getSuggestionBarSplitChar()) {
            mSuggestionBarExplicitSearchActive = true;
        }
        if (!mSuggestionBarExplicitSearchActive) {
            return;
        }
        String input = normalizeSuggestionBarInput(mTerminalView.getCurrentInput(inputChar));
        if (!input.isEmpty()) {
            mSuggestionBarView.reloadWithInput(input, mTerminalView);
            return;
        }
        if (mTerminalView.getCurrentInput() != null && !mTerminalView.getCurrentInput().trim().isEmpty()) {
            mSuggestionBarExplicitSearchActive = false;
        }
        if (mSuggestionBarView.isSearchSurfaceActive()) {
            mSuggestionBarView.reloadWithInput("", mTerminalView);
        }
    }

    @Override
    public void reloadSuggestionBar(boolean delete, boolean enter) {
        if (!isSuggestionBarEnabled() || mSuggestionBarView == null || mTerminalView == null) {
            return;
        }
        resetAzGestureState(false, true);
        mSuggestionBarView.onTerminalInteraction();
        if (enter) {
            mSuggestionBarExplicitSearchActive = false;
            if (mSuggestionBarView.isSearchSurfaceActive()) {
                mSuggestionBarView.reloadWithInput("", mTerminalView);
            }
            return;
        }
        if (!mSuggestionBarExplicitSearchActive && !mSuggestionBarView.isSearchSurfaceActive()) {
            return;
        }
        String input = normalizeSuggestionBarInput(mTerminalView.getCurrentInput());
        if (!input.isEmpty()) {
            mSuggestionBarView.reloadWithInput(input, mTerminalView);
            return;
        }
        mSuggestionBarExplicitSearchActive = false;
        if (mSuggestionBarView.isSearchSurfaceActive()) {
            mSuggestionBarView.reloadWithInput("", mTerminalView);
        }
    }

    private String normalizeSuggestionBarInput(String rawInput) {
        if (rawInput == null) {
            return "";
        }
        String trimmedRaw = rawInput.trim();
        if (trimmedRaw.isEmpty()) {
            return "";
        }
        if (trimmedRaw.indexOf(' ') >= 0) {
            return "";
        }
        if (containsAppSearchSeparator(trimmedRaw)) {
            return "";
        }
        if (trimmedRaw.length() > SUGGESTION_BAR_MAX_INPUT_CHARS) {
            return "";
        }
        return trimmedRaw;
    }

    private boolean containsAppSearchSeparator(String value) {
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '/':
                case '.':
                case '-':
                case '_':
                case ':':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    public static void requestTermuxActivityStylingOnNextResume(Context context, boolean recreateActivity) {
        sPendingStyleReloadRecreateActivity = recreateActivity;
        sPendingStyleReloadOnNextResume = true;
        updateTermuxActivityStyling(context, recreateActivity);
    }

    public static void requestAppDrawerReloadOnNextResume(Context context) {
        sPendingAppDrawerReloadOnNextResume = true;
        context.sendBroadcast(new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_APP_DRAWER));
    }

    private static boolean consumePendingStyleReloadRecreateActivity() {
        if (!sPendingStyleReloadOnNextResume) {
            return true;
        }
        boolean recreateActivity = sPendingStyleReloadRecreateActivity;
        sPendingStyleReloadOnNextResume = false;
        sPendingStyleReloadRecreateActivity = true;
        return recreateActivity;
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_APP_DRAWER);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        intentFilter.addAction(Intent.ACTION_DATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

        if (Build.VERSION.SDK_INT >= 28 ) {
            registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
        }
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void registerPackageChangeReceiver() {
        if (mPackageChangeReceiverRegistered)
            return;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        intentFilter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 28) {
            registerReceiver(mPackageChangeReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mPackageChangeReceiver, intentFilter);
        }
        mPackageChangeReceiverRegistered = true;
    }

    private void registerPreferredHomeChangeReceiver() {
        if (mPreferredHomeChangeReceiver != null)
            return;
        mPreferredHomeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !ACTION_PREFERRED_ACTIVITY_CHANGED.equals(intent.getAction()))
                    return;
                syncRecentsVisibilityPolicy();
            }
        };
        IntentFilter intentFilter = new IntentFilter(ACTION_PREFERRED_ACTIVITY_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                registerReceiver(mPreferredHomeChangeReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(mPreferredHomeChangeReceiver, intentFilter);
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Failed to register preferred home change receiver: " + e.getMessage());
            mPreferredHomeChangeReceiver = null;
        }
    }

    private void unregisterPreferredHomeChangeReceiver() {
        if (mPreferredHomeChangeReceiver == null)
            return;
        try {
            unregisterReceiver(mPreferredHomeChangeReceiver);
        } catch (IllegalArgumentException ignored) {
            // Ignore if already unregistered.
        }
        mPreferredHomeChangeReceiver = null;
    }

    private void registerLauncherAppsCallback() {
        if (mLauncherAppsCallbackRegistered) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        try {
            if (mLauncherApps == null) {
                mLauncherApps = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            }
            if (mLauncherApps == null) {
                return;
            }
            if (mLauncherAppsCallback == null) {
                mLauncherAppsCallback = new LauncherApps.Callback() {
                    @Override
                    public void onPackageRemoved(String packageName, UserHandle user) {
                        notePendingChangedPackage(packageName);
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackageAdded(String packageName, UserHandle user) {
                        notePendingChangedPackage(packageName);
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackageChanged(String packageName, UserHandle user) {
                        notePendingChangedPackage(packageName);
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                        notePendingChangedPackages(packageNames);
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
                        notePendingChangedPackages(packageNames);
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onShortcutsChanged(String packageName, List<ShortcutInfo> shortcuts, UserHandle user) {
                        if (mSuggestionBarView != null) {
                            mSuggestionBarView.invalidateShortcutCache(packageName);
                        }
                    }
                };
            }
            mLauncherApps.registerCallback(mLauncherAppsCallback, mAzGestureHandler);
            mLauncherAppsCallbackRegistered = true;
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "LauncherApps callback registration failed: " + throwable.getMessage());
        }
    }

    private void unregisterLauncherAppsCallback() {
        if (!mLauncherAppsCallbackRegistered || mLauncherApps == null || mLauncherAppsCallback == null) {
            return;
        }
        try {
            mLauncherApps.unregisterCallback(mLauncherAppsCallback);
        } catch (Throwable ignored) {
        }
        mLauncherAppsCallbackRegistered = false;
    }

    /** Records a package a broadcast reported as touched, for the next debounced refresh. */
    private void notePendingChangedPackage(@Nullable String packageName) {
        synchronized (mPendingChangedPackages) {
            if (packageName == null || packageName.isEmpty()) {
                mPendingChangedPackagesUnknown = true;
            } else {
                mPendingChangedPackages.add(packageName);
            }
        }
    }

    private void notePendingChangedPackages(@Nullable String[] packageNames) {
        if (packageNames == null || packageNames.length == 0) {
            notePendingChangedPackage(null);
            return;
        }
        for (String packageName : packageNames) {
            notePendingChangedPackage(packageName);
        }
    }

    /** Marks the next catalogue refresh as a full rebuild (no entry reuse). */
    private void requestFullCatalogRebuild() {
        synchronized (mPendingChangedPackages) {
            mPendingChangedPackagesUnknown = true;
        }
    }

    /**
     * Consumes the accumulated change scope: null = unknown, rebuild everything; a set (possibly
     * empty) = only these packages changed, reuse the rest.
     */
    @Nullable
    private java.util.Set<String> drainPendingChangedPackages() {
        synchronized (mPendingChangedPackages) {
            if (mPendingChangedPackagesUnknown) {
                mPendingChangedPackagesUnknown = false;
                mPendingChangedPackages.clear();
                return null;
            }
            java.util.Set<String> changed = new java.util.LinkedHashSet<>(mPendingChangedPackages);
            mPendingChangedPackages.clear();
            return changed;
        }
    }

    private void refreshSuggestionBarFromPackageState(boolean forceCatalogRefresh) {
        boolean catalogEnabled = isLauncherCatalogEnabled() && mSuggestionBarView != null;
        if (catalogEnabled && forceCatalogRefresh) {
            // Kick the provider's background refresh BEFORE notifying the drawer below: while the
            // refresh is in flight the provider parks warmAsync callbacks, so the drawer's
            // re-registration fires once against the fresh snapshot instead of echoing the stale
            // one. The old flow (clearAppCache + reloadAllApps) wiped the provider synchronously
            // and blanked both surfaces for the whole rebuild.
            mSuggestionBarView.pruneInvalidIconOverrides();
            mSuggestionBarView.refreshAllApps(drainPendingChangedPackages());
            mLastLauncherCatalogSignature = computeLauncherCatalogSignature();
            syncAzScrubLettersAndTint();
        }
        // The drawer's catalogue is not the dock's: it must refresh even with the suggestion bar
        // disabled or its view not yet built. Guarded on the field so a session that never opened
        // the drawer still never builds one.
        if (mWidgetHostController != null) {
            mWidgetHostController.reconcileProviders();
        }
        if (mWidgetPaneController != null) {
            mWidgetPaneController.onPackageOrProfileChanged();
        }
        if (mAppDrawerController != null) {
            mAppDrawerController.onAppCatalogChanged();
        }
        if (!catalogEnabled) {
            return;
        }
        if (!forceCatalogRefresh && mSuggestionBarView.hasPinnedOverflowPages()) {
            // Keep affordance state fresh without forcing a catalog rebuild.
            updateAzOverflowAffordance();
        }
        String input = "";
        if (mTerminalView != null && mSuggestionBarExplicitSearchActive) {
            input = normalizeSuggestionBarInput(mTerminalView.getCurrentInput());
        }
        mSuggestionBarView.reloadWithInput(input, mTerminalView);
        updateAzOverflowAffordance();
    }

    private void addAccessoryKeyboardLayoutListener() {
        View content = findViewById(android.R.id.content);
        if (content == null || mAccessoryKeyboardLayoutListener != null) {
            return;
        }
        mLastImeVisible = isImeVisible();
        mAccessoryKeyboardLayoutListener = () -> {
            boolean imeVisible = isImeVisible();
            if (imeVisible != mLastImeVisible) {
                mLastImeVisible = imeVisible;
                onImeVisibilityChanged(imeVisible);
            }
        };
        content.getViewTreeObserver().addOnGlobalLayoutListener(mAccessoryKeyboardLayoutListener);
    }

    private void removeAccessoryKeyboardLayoutListener() {
        View content = findViewById(android.R.id.content);
        if (content == null || mAccessoryKeyboardLayoutListener == null) {
            return;
        }
        content.getViewTreeObserver().removeOnGlobalLayoutListener(mAccessoryKeyboardLayoutListener);
        mAccessoryKeyboardLayoutListener = null;
    }

    private void addAccessoryLayoutChangeListeners() {
        if (mAccessoryLayoutChangeListener != null) {
            return;
        }
        mAccessoryLayoutChangeListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int oldWidth = oldRight - oldLeft;
            int height = bottom - top;
            int oldHeight = oldBottom - oldTop;
            if (width == oldWidth && height == oldHeight) {
                return;
            }
            if (v.getId() == R.id.terminal_pane_host) {
                if (isFullStatusBarEngaged()) return;
                // Never mutate accessory layout params from inside this layout pass.
                v.post(() -> {
                    if (!isFinishing() && !isDestroyed() && !isFullStatusBarEngaged())
                        applyAccessoryGeometryIfNeeded(true, "terminal:layout");
                });
                return;
            }
            if (v.getId() == R.id.inapp_keyboard_container) {
                // The desired height was measured independently before the stack height was set.
                // Only retry if the laid-out child disagrees; equality is the stable terminal state
                // and prevents layout -> requestLayout -> layout cycles.
                if (height != mDesiredInAppKeyboardHeightPx) {
                    v.post(() -> {
                        if (!isFinishing() && !isDestroyed()
                            && v.getHeight() != mDesiredInAppKeyboardHeightPx) {
                            mDesiredInAppKeyboardHeightPx = 0;
                            mInAppKeyboardHeightDirty = true;
                            applyAccessoryGeometryIfNeeded(true, "inapp-keyboard:height");
                        }
                    });
                } else {
                    scheduleAccessoryRenderSync("inapp-keyboard:layout");
                }
                return;
            }
            scheduleAccessoryRenderSync("accessory:layout");
        };
        int[] watchIds = {
            R.id.terminal_pane_host,
            R.id.accessory_stack_container,
            R.id.apps_bar_viewpager,
            R.id.apps_bar_indicator_band,
            R.id.apps_bar_az_row,
            R.id.terminal_toolbar_view_pager,
            R.id.inapp_keyboard_container
        };
        for (int watchId : watchIds) {
            View watchView = findViewById(watchId);
            if (watchView != null) {
                watchView.addOnLayoutChangeListener(mAccessoryLayoutChangeListener);
            }
        }
    }

    private void removeAccessoryLayoutChangeListeners() {
        if (mAccessoryLayoutChangeListener == null) {
            return;
        }
        int[] watchIds = {
            R.id.terminal_pane_host,
            R.id.accessory_stack_container,
            R.id.apps_bar_viewpager,
            R.id.apps_bar_indicator_band,
            R.id.apps_bar_az_row,
            R.id.terminal_toolbar_view_pager,
            R.id.inapp_keyboard_container
        };
        for (int watchId : watchIds) {
            View watchView = findViewById(watchId);
            if (watchView != null) {
                watchView.removeOnLayoutChangeListener(mAccessoryLayoutChangeListener);
            }
        }
        mAccessoryLayoutChangeListener = null;
    }

    private boolean isImeVisible() {
        View content = findViewById(android.R.id.content);
        if (content == null) {
            return false;
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
        return insets != null && insets.isVisible(Type.ime());
    }

    private void onImeVisibilityChanged(boolean visible) {
        if (!visible && !mAzGestureActive) {
            mSuggestionBarInteractionActive = false;
            if (mSuggestionBarView != null) {
                mSuggestionBarView.clearAzPreview();
            }
        }
        if (mTermuxTerminalSessionActivityClient != null) {
            mTermuxTerminalSessionActivityClient.onImeVisibilityChanged(visible);
        }
        applyAccessoryGeometryIfNeeded(true, visible ? "ime:open" : "ime:close");
        scheduleAccessoryRenderSync(visible ? "ime:open" : "ime:close");
        restartAccessoryBlurHeartbeat();
        scheduleAccessoryBlurRecovery();
    }

    private void scheduleAccessoryRenderSync(@NonNull String reason) {
        if (reason.contains("wallpaper") || reason.contains("style") || reason.contains("blur")) {
            mAccessoryBackdropDirty = true;
            mDecorNavBarBackdropDirty = true;
        }
        if (mAccessoryRenderSyncPending) {
            return;
        }
        mAccessoryRenderSyncPending = true;
        mAccessoryRenderHandler.post(mAccessoryRenderSyncRunnable);
    }

    private void enforceAccessoryFxInvariants() {
        View accessoryContainer = findViewById(R.id.accessory_stack_container);
        if (accessoryContainer == null || accessoryContainer.getVisibility() != View.VISIBLE) {
            resetAzOverflowAffordanceState();
            return;
        }
        if (mSuggestionBarView == null) {
            resetAzOverflowAffordanceState();
            return;
        }
        boolean hasOverflow = mSuggestionBarView.hasAzOverflowPages() || mSuggestionBarView.hasPinnedOverflowPages();
        if (!hasOverflow && !mAzGestureActive && !mSuggestionBarInteractionActive) {
            resetAzOverflowAffordanceState();
            return;
        }
        updateAzOverflowAffordance();
    }

    private void scheduleSuggestionBarPackageRefresh(boolean immediate, boolean forceCatalogRefresh) {
        if (!isLauncherCatalogEnabled()) {
            mPackageRefreshForceCatalogReload = false;
            mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
            return;
        }
        mPackageRefreshForceCatalogReload = mPackageRefreshForceCatalogReload || forceCatalogRefresh;
        mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
        if (immediate) {
            boolean forceNow = mPackageRefreshForceCatalogReload;
            mPackageRefreshForceCatalogReload = false;
            refreshSuggestionBarFromPackageState(forceNow);
            return;
        }
        mAzGestureHandler.postDelayed(mPackageRefreshRunnable, PACKAGE_REFRESH_DEBOUNCE_MS);
    }

    private void refreshSuggestionBarIfLauncherCatalogChanged() {
        if (!isLauncherCatalogEnabled() || mSuggestionBarView == null) {
            return;
        }
        int signature = computeLauncherCatalogSignature();
        if (mLastLauncherCatalogSignature == Integer.MIN_VALUE) {
            mLastLauncherCatalogSignature = signature;
            return;
        }
        if (mLastLauncherCatalogSignature == signature) {
            return;
        }
        mLastLauncherCatalogSignature = signature;
        scheduleSuggestionBarPackageRefresh(false, true);
    }

    private void refreshCalendarIconsIfDayChanged() {
        Calendar now = Calendar.getInstance();
        int dayKey = (now.get(Calendar.YEAR) * 400) + now.get(Calendar.DAY_OF_YEAR);
        if (mLastLauncherIconDayKey == Integer.MIN_VALUE) {
            mLastLauncherIconDayKey = dayKey;
            return;
        }
        if (mLastLauncherIconDayKey == dayKey) return;
        mLastLauncherIconDayKey = dayKey;
        // Dynamic calendar icons change without any package event; entry reuse would keep
        // yesterday's rendering, so the refresh must rebuild every entry.
        requestFullCatalogRebuild();
        scheduleSuggestionBarPackageRefresh(true, true);
    }

    private int computeLauncherCatalogSignature() {
        PackageManager packageManager = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables = packageManager.queryIntentActivities(main, 0);
        List<String> ids = new ArrayList<>(launchables.size());
        for (ResolveInfo resolveInfo : launchables) {
            if (resolveInfo == null || resolveInfo.activityInfo == null
                || resolveInfo.activityInfo.packageName == null || resolveInfo.activityInfo.name == null) {
                continue;
            }
            ids.add(resolveInfo.activityInfo.packageName + "/" + resolveInfo.activityInfo.name);
        }
        Collections.sort(ids);
        int signature = 17;
        signature = (31 * signature) + ids.size();
        for (String id : ids) {
            signature = (31 * signature) + id.hashCode();
        }
        return signature;
    }

    private void scheduleLauncherCatalogWarmup() {
        mAzGestureHandler.removeCallbacks(mLauncherCatalogWarmRunnable);
        if (mIsVisible && isLauncherCatalogEnabled() && mSuggestionBarView != null) {
            mAzGestureHandler.postDelayed(mLauncherCatalogWarmRunnable, LAUNCHER_CATALOG_WARM_DELAY_MS);
        }
    }

    private void runLauncherCatalogWarmup() {
        if (!mIsVisible || !isLauncherCatalogEnabled() || mSuggestionBarView == null) {
            return;
        }
        mSuggestionBarView.reloadAllApps();
        mSuggestionBarView.reload();
    }

    private void unregisterPackageChangeReceiver() {
        if (!mPackageChangeReceiverRegistered)
            return;
        try {
            unregisterReceiver(mPackageChangeReceiver);
        } catch (IllegalArgumentException ignored) {
            // Ignore if already unregistered.
        }
        mPackageChangeReceiverRegistered = false;
    }

    /**
     * Turn "Use wallpaper colors" off when Termux:Styling has just written a colour scheme.
     *
     * <p>Dynamic colours build the palette from the wallpaper and never read
     * {@code ~/.termux/colors.properties}, so picking a scheme in Termux:Styling used to do nothing
     * visible and say nothing about why. Only the styling app sends
     * {@code EXTRA_RELOAD_STYLE = "colors"} — the in-app reload carries {@code EXTRA_RECREATE_ACTIVITY}
     * alone — so this cannot fire on our own restyles. Re-enabling the switch hands the palette back
     * to the wallpaper, which is what the hint under it says.
     */
    @VisibleForTesting
    void yieldDynamicColorsToStylingScheme(@Nullable Intent intent) {
        if (intent == null || !TERMUX_ACTIVITY.ACTION_RELOAD_STYLE.equals(intent.getAction()))
            return;
        if (!"colors".equals(intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE)))
            return;
        if (mPreferences == null || !mPreferences.isTerminalDynamicColorsEnabled())
            return;
        if (!TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE.isFile())
            return;
        Logger.logInfo(LOG_TAG, "Termux:Styling wrote a colour scheme; disabling wallpaper colours");
        mPreferences.setTerminalDynamicColorsEnabled(false);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null)
            return;
        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class PackageChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || mSuggestionBarView == null)
                return;
            String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                Intent.ACTION_PACKAGE_REMOVED.equals(action) ||
                Intent.ACTION_PACKAGE_CHANGED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                notePendingChangedPackage(intent.getData() != null
                    ? intent.getData().getSchemeSpecificPart() : null);
                scheduleSuggestionBarPackageRefresh(false, true);
            }
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;
            // Before the visibility gate: Termux:Styling normally broadcasts while this activity is in
            // the background, and the user's choice of scheme must not be dropped for that.
            yieldDynamicColorsToStylingScheme(intent);
            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);
                switch(intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        sPendingStyleReloadOnNextResume = false;
                        sPendingStyleReloadRecreateActivity = true;
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_APP_DRAWER:
                        sPendingAppDrawerReloadOnNextResume = false;
                        if (mAppDrawerController != null)
                            mAppDrawerController.onPreferencesReloaded();
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    case Intent.ACTION_DATE_CHANGED:
                    case Intent.ACTION_TIME_CHANGED:
                    case Intent.ACTION_TIMEZONE_CHANGED:
                        refreshCalendarIconsIfDayChanged();
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();
            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                applyExtraKeysFeedbackAccent(mExtraKeysView);
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }
            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }
        setMargins();
        updateAppLauncherBarHeight();
        applySuggestionBarPreferences();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.resetTransientVisualState();
        }
        if (mAppDrawerController != null)
            mAppDrawerController.onPreferencesReloaded();
        applySuggestionBarInputChar();
        mAccessoryBackdropDirty = true;
        mDecorNavBarBackdropDirty = true;
        applySeamlessStatusBackgroundModeIfNeeded();
        applyTerminalSurfaceAppearance();
        // After appearance: applyTerminalSurfaceAppearance() flat-colors the dock surfaces, so the
        // accessory pass must rebuild the dock glass last (mirrors the onCreate order).
        applyAccessoryGeometryIfNeeded(true, "reloadActivityStyling");
        syncTerminalWallpaperRenderingMode();
        updateWindowBackgroundForCurrentSession();
        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);
        initializeInAppKeyboard(null);
        if (mInAppKeyboard != null)
            mInAppKeyboard.onPreferencesReloaded();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();
        // Re-render the status widgets (weather unit, visibility) from possibly-changed prefs;
        // updateStatusWidgets replays the weather cache through onWeatherUpdated.
        refreshTerminalWindowBar();
        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
        applyFullscreenMode();
    }

    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /** Drawable delegate whose pixels participate in rendering but never in wrap-content sizing. */
    static final class LayoutNeutralDrawable extends Drawable {
        @NonNull private final Drawable mSource;

        LayoutNeutralDrawable(@NonNull Drawable source) {
            mSource = source;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            mSource.setBounds(getBounds());
            mSource.draw(canvas);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            mSource.setBounds(bounds);
        }

        @Override
        public void setAlpha(int alpha) {
            mSource.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mSource.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override public int getIntrinsicWidth() { return 0; }
        @Override public int getIntrinsicHeight() { return 0; }
        @Override public int getMinimumWidth() { return 0; }
        @Override public int getMinimumHeight() { return 0; }

        @Override
        public int getOpacity() {
            return mSource.getOpacity();
        }
    }

}
