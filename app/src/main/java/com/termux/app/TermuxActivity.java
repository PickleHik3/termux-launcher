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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.github.mmin18.widget.AndroidStockBlurImpl;
import com.github.mmin18.widget.RealtimeBlurView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.chrome.ChromePolicy;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.SurfaceDirtyLedger;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.surfaces.SurfaceEditorController;
import com.termux.app.fragments.settings.termux.KeyboardColorSchemeFragment;
import com.termux.app.launcher.animation.LauncherTransitionController;
import com.termux.app.launcher.az.AzScrubGesture;
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
import com.termux.app.terminal.PaneShape;
import com.termux.app.terminal.TerminalFrameMetricsMonitor;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardHost;
import com.termux.app.terminal.inappkeyboard.KeyboardGeometryChoreographer;
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
import com.termux.app.theme.LauncherSchemeTheme;
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
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
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
    /** Opens the surface editor over the live terminal. The value predates the editor's name. */
    public static final String EXTRA_SURFACE_EDITOR =
        "com.termux.app.extra.DOCK_TUNING";
    public static final String EXTRA_SURFACE_EDITOR_SECTION =
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
    /** Resolves per-pane foreground process / open file for the window pill labels. */
    @Nullable private com.termux.app.statusbar.WindowForegroundResolver mWindowForegroundResolver;
    @Nullable private Runnable mSessionBrowserRefreshCallback;
    private final Handler mWindowLabelHandler = new Handler(Looper.getMainLooper());
    private static final long WINDOW_LABEL_POLL_MS = 2000L;
    /** Trailing CPU/RAM/weather widgets, their data controllers, and the shared detail card host. */
    @Nullable private com.termux.app.statusbar.SystemStatsController mStatsController;
    @Nullable private com.termux.app.statusbar.AiIndicatorController mAiIndicatorController;
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
    /** Lazy-mode multipliers for the two sampling cadences; the readings are the same, just rarer. */
    private static final long STATS_LAZY_MULTIPLIER = 3L;
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
    private final java.util.List<com.termux.shared.termux.shell.command.runner.terminal.TermuxSession> mDrawerSessions = new java.util.ArrayList<>();

    /** The one {@link com.termux.app.terminal.TerminalHost} every terminal client is given. */
    @Nullable private ActivityTerminalHost mTerminalHost;

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
    /**
     * The surface editor overlay. All of its UI, gestures and dirty tracking live in the
     * controller; the activity lends it {@link SurfaceEditorHost} and keeps only the entry points.
     */
    private final SurfaceEditorController mSurfaceEditor =
        new SurfaceEditorController(new SurfaceEditorHost());

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
    @Nullable private com.termux.app.terminal.find.TerminalFindCoordinator mFindCoordinator;
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
    /** Height the in-app notice chip is occupying above the background stack; 0 when not showing. */
    private int mAppNoticeOccupancyPx;
    /** True between the onboarding finishing and the last of its permission dialogs closing. */
    private boolean mFirstRunPermissionChainActive;
    /**
     * Nesting depth of {@link #runWithoutNotices}. Creating a window or a pane touches the focused
     * shell, which several unrelated listeners read as a session change worth announcing; the user
     * sees the result of the split on screen already, so the whole operation runs silent.
     */
    private int mNoticeSuppressionDepth;
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
    private final int[] mDockPlankKeySurfaceLocation = new int[2];
    /**
     * The key surfaces that ride on the dock plank. Touches on these never drive the plank
     * physics — see {@link #isOnDockPlankKeySurface(float, float)}.
     */
    private static final int[] DOCK_PLANK_KEY_SURFACE_IDS = {
        R.id.inapp_keyboard_container,
        R.id.terminal_toolbar_view_pager,
    };

    /** True while every terminal pane is a glass slab with its own physics. */
    private boolean mTerminalGlassActive = false;
    /** The blur frame the panes are painting from; kept out of the cache's recycler. */
    @Nullable private Bitmap mPaneGlassFrame;

    private float mTerminalToolbarDefaultHeight;
    private final Handler mAzGestureHandler = new Handler(Looper.getMainLooper());
    /**
     * The A–Z scrub's decision machine. It owns every mode, threshold and timer the gesture is
     * judged by; this activity owns everything those decisions are applied to — the letter row,
     * the suggestion bar, the three FX layers and the {@code Choreographer} loop below.
     */
    private final AzScrubGesture mAzGesture = new AzScrubGesture(SystemClock::uptimeMillis);
    @Nullable private Choreographer.FrameCallback mAzEdgePagingFrameCallback;
    @Nullable private SuggestionBarView.AzDragFocusResult mAzCurrentFocusResult;
    @Nullable private Runnable mAzOverflowRefreshRunnable;
    private boolean mSuggestionBarInteractionActive = false;
    private final RectF mAzRowRawBounds = new RectF();
    private final RectF mAppsRowRawBounds = new RectF();
    private final RectF mExtraKeysRawBounds = new RectF();
    private final RectF mAzFocusLetterRawBounds = new RectF();
    private final int[] mAzViewLocation = new int[2];
    private final AzScrubRowView.LetterVisualMetrics mAzLetterVisualMetrics = new AzScrubRowView.LetterVisualMetrics();

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

    private static final int CONTEXT_MENU_SURFACE_EDITOR_ID = 9;

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
    private static volatile boolean sPendingStyleReloadOnNextResume = false;
    private static volatile boolean sPendingAppDrawerReloadOnNextResume = false;

    private static final int SUGGESTION_BAR_MIN_BUTTON_DP = 56;
    private static final int SUGGESTION_BAR_MAX_INPUT_CHARS = 10;
    private static final long EMPTY_SESSION_RECOVERY_DEBOUNCE_MS = 1500L;
    private static volatile boolean sPendingStyleReloadRecreateActivity = true;

    private boolean mSeamlessStatusBackgroundActive;
    private int mLastStatusBarInsetTop;
    private long mLastEmptySessionRecoveryElapsedMs;
    private boolean mEmptySessionRecoveryInProgress;
    @Nullable private ViewTreeObserver.OnGlobalLayoutListener mAccessoryKeyboardLayoutListener;
    @Nullable private View.OnLayoutChangeListener mAccessoryLayoutChangeListener;
    @Nullable private ActivityResultLauncher<PickVisualMediaRequest> mWallpaperPickerLauncher;
    @Nullable private ActivityResultLauncher<CropImageContractOptions> mWallpaperCropLauncher;
    private final int[] mTmpParentLocation = new int[2];
    private final int[] mTmpViewLocation = new int[2];
    private long mLastAccessoryGeometryApplyUptimeMs;
    private int mAppliedTerminalFlushPaddingPx;
    /** Set when {@link WallpaperManager#getDrawable()} threw for want of the storage permission. */
    private boolean mWallpaperReadPermissionDenied;
    private boolean mWallpaperReadPermissionPromptShowing;
    @Nullable private FrameLayout mDecorNavBarSurfaceOverlay;
    @Nullable private ImageView mDecorNavBarBlurBackdrop;
    @Nullable private View mDecorNavBarTintOverlay;
    @Nullable private Bitmap mInAppKeyboardBackdropBitmap;
    /**
     * Memo of the color scheme's keyboard-background override, keyed on the raw persisted JSON.
     * The value is consulted on every accessory render sync, which must not re-parse JSON.
     */
    @Nullable private String mInAppKeyboardSchemeBackgroundJson;
    @Nullable private Integer mInAppKeyboardSchemeBackgroundColor;
    /**
     * The accessory chrome — glass, blur, frost and backdrops for the dock, the in-app keyboard,
     * the under-pill nav strip, the top pane, the palette and the drawer plane. The Activity keeps
     * only the view slots and the lifecycle forwarding; everything the module needs from here goes
     * through {@link ChromeRenderer.Surfaces} below.
     */
    private final ChromeRenderer mChrome = new ChromeRenderer(new ChromeRenderer.Surfaces() {
        @NonNull @Override public Context context() {
            return TermuxActivity.this;
        }

        @Nullable @Override public View findChromeView(int viewId) {
            return findViewById(viewId);
        }

        @Nullable @Override public TermuxAppSharedPreferences preferences() {
            return mPreferences;
        }

        @Override public int orientation() {
            return getResources().getConfiguration().orientation;
        }

        @Override public float dpToPx(float dp) {
            return TermuxActivity.this.dpToPx(dp);
        }

        @Override public int glassBaseColor() {
            return resolveAccessoryGlassBaseColor();
        }

        @Override public int accentColor() {
            return resolveDockAccentColor();
        }

        @Override public int outlineColor() {
            return resolveAccessoryOutlineColor();
        }

        @Override public boolean roundedDockStyle() {
            return isRoundedDockStyle();
        }

        @Override public float statusBarRimCornerRadiusPx() {
            return resolveStatusBarCapsuleCornerRadiusPx(targetStatusBarHeightPx(true,
                mPreferences != null && mPreferences.isTopPaneClockCollapsed()));
        }

        @NonNull @Override public Rect wallpaperFrameRect() {
            return getManagedWallpaperFrameRect();
        }

        @Override public boolean useManagedWallpaperSource() {
            return shouldUseManagedWallpaperBlurSource();
        }

        @Override public int systemWallpaperId() {
            return getCurrentSystemWallpaperId();
        }

        @NonNull @Override public java.io.File managedWallpaperExactFile() {
            return getManagedWallpaperExactFile();
        }

        @Nullable @Override public Bitmap captureWallpaperFrame(@NonNull Rect frameRect,
                                                                @NonNull View wallpaperFrame) {
            return createWallpaperBackdropBitmapForRect(frameRect, wallpaperFrame);
        }

        @Override public boolean isFrameInUse(@Nullable Bitmap frame) {
            // The keyboard's own backdrop may be the shared frame itself; recycling it under the
            // keyboard crashes its next draw exactly like recycling it under a frost would.
            return frame != null
                && (frame == mInAppKeyboardBackdropBitmap || isSharedWallpaperBlurFrameInUse(frame));
        }

        @Override public void onCacheCleared() {
            mPaneGlassFrame = null;
        }

        @Override public boolean isActivityVisible() {
            return mIsVisible;
        }

        @Override public boolean wallpaperPassthroughEnabled() {
            return shouldUseWallpaperPassthroughMode();
        }

        @Override public boolean fullStatusBarEngaged() {
            return isFullStatusBarEngaged();
        }

        @Override public int effectiveDockBlurRadiusDp() {
            return getEffectiveExtraKeysBlurRadius();
        }

        @Override public int effectiveStatusBarBlurRadiusDp() {
            return getEffectiveStatusBarBlurRadius();
        }

        @NonNull @Override public ChromeSpec buildChromeSpec() {
            return TermuxActivity.this.buildChromeSpec();
        }

        @Override public void applyChromeSpec(@NonNull ChromeSpec spec) {
            TermuxActivity.this.applyChromeSpec(spec);
        }

        @Override public void enforceAccessoryFxInvariants() {
            TermuxActivity.this.enforceAccessoryFxInvariants();
        }

        @Override public void updateTerminalGlassFrost() {
            TermuxActivity.this.updateTerminalGlassFrost();
        }

        @Override public boolean isBlurHealthy(@NonNull ChromeSpec spec) {
            return isAccessoryBlurHealthy(spec);
        }
    });

    /** The accessory chrome module — the one way in to glass, blur, frost and backdrop work. */
    @VisibleForTesting
    @NonNull
    ChromeRenderer getChromeRenderer() {
        return mChrome;
    }

    /**
     * The accessory geometry pass's skip path used to route its reason string through a keyword
     * match to decide whether the blurred backdrops were invalidated too. Kept verbatim so a
     * styling reload still drops them and a layout pass still does not.
     */
    private static int accessorySkipScopes(@NonNull String reason) {
        return ChromeRenderer.SCOPE_ACCESSORY_RENDER
            | (reason.contains("wallpaper") || reason.contains("style") || reason.contains("blur")
                ? ChromeRenderer.SCOPE_BACKDROPS : 0);
    }

    /** The rotation geometry pass waiting for the new layout, or null when none is pending. */
    @Nullable private OneShotPreDrawListener mPendingOrientationGeometryPass;

    /**
     * Set when accessory geometry was suppressed because the app drawer plane owns the stack's
     * transforms. Flushed by {@link #flushPendingAccessoryGeometry()} on drawer close and on every
     * {@link #onStart()} — a suppression that is never flushed freezes the dock until recreate.
     */
    private boolean mAppDrawerGeometryFreezePending;
    @Nullable private WallpaperManager.OnColorsChangedListener mWallpaperColorsChangedListener;
    private final Handler mAccessoryRenderHandler = new Handler(Looper.getMainLooper());

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
            mChrome.requestSync(ChromeRenderer.SCOPE_APPLY_NOW);
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
            handleSurfaceEditorIntent(getIntent());
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
        ensureAppNoticeHost();
        if (savedInstanceState == null) {
            boolean forceOnboarding = getIntent().getBooleanExtra(EXTRA_SHOW_ONBOARDING, false);
            View contentView = findViewById(android.R.id.content);
            contentView.post(() -> FirstLaunchOnboarding.showIfNeeded(this, forceOnboarding,
                this::startFirstRunPermissionChain));
        }
    }

    /**
     * The permissions the launcher wants but cannot function without, asked for once, in order,
     * immediately after the onboarding's last page.
     *
     * <p>Both used to be asked reactively — the wallpaper one only after a read had already failed
     * and the surface had drawn wrong, the location one at the moment the user first opened the
     * weather card. Asking at the end of the introduction instead means the user has just been told
     * what the wallpaper-aware surface and the weather widget are, so the dialogs have a reason
     * attached, and the first real frame is already correct.
     *
     * <p>Strictly sequential: two runtime permission dialogs requested in the same frame means the
     * second one is dropped by the framework, so the location step is started from the wallpaper
     * step's result rather than beside it.
     */
    private void startFirstRunPermissionChain() {
        if (isFinishing() || isDestroyed()) return;
        mFirstRunPermissionChainActive = true;
        if (!requestWallpaperReadPermissionForFirstRun()) {
            requestWeatherLocationPermissionForFirstRun();
        }
    }

    /** @return true when a dialog was raised, so the chain continues from its result instead. */
    private boolean requestWallpaperReadPermissionForFirstRun() {
        if (mPreferences == null) return false;
        boolean granted = androidx.core.content.ContextCompat.checkSelfPermission(this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (granted || mPreferences.isWallpaperReadPermissionPrompted()) return false;
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
            .setNegativeButton(R.string.action_wallpaper_read_permission_dismiss, (dialog, which) -> {
                mPreferences.setWallpaperReadPermissionPrompted(true);
                requestWeatherLocationPermissionForFirstRun();
            })
            .setOnDismissListener(dialog -> mWallpaperReadPermissionPromptShowing = false)
            .show();
        return true;
    }

    /**
     * Second link in the chain: coarse location, which is the only thing the weather widget needs.
     * Skipped when the widget is switched off — a permission for a feature the user is not running
     * is exactly the kind of prompt that gets denied out of hand.
     */
    private void requestWeatherLocationPermissionForFirstRun() {
        if (!mFirstRunPermissionChainActive || isFinishing() || isDestroyed()) return;
        mFirstRunPermissionChainActive = false;
        if (mPreferences == null || !mPreferences.isStatusWidgetWeatherEnabled()) return;
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_weather_location_permission)
            .setMessage(R.string.msg_weather_location_permission)
            .setPositiveButton(R.string.action_weather_location_permission_allow, (dialog, which) ->
                androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[] {android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_CODE_WEATHER_LOCATION))
            .setNegativeButton(R.string.action_weather_location_permission_dismiss, null)
            .show();
    }

    /**
     * Creates the in-app notice chip up front and joins it to the top-trailing column, so the
     * session-switch chip and the background-process stack move down under it while a notice is up
     * rather than being drawn over.
     */
    private void ensureAppNoticeHost() {
        com.termux.app.notice.AppNoticeHostView host = AppNotice.hostFor(this);
        if (host == null) return;
        host.setOccupancyListener(height -> {
            mAppNoticeOccupancyPx = height;
            applyNoticeColumnOffsets();
        });
    }

    /** The top-trailing column: notice chip on top, background stack right under it. The
     *  session-switch indicator lives in the top-leading corner now and no longer shares it. */
    private void applyNoticeColumnOffsets() {
        if (mBackgroundProcessStack != null)
            mBackgroundProcessStack.setNoticeOccupancyPx(mAppNoticeOccupancyPx);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInAppKeyboardHeightAdjustIntent(intent);
        handleSurfaceEditorIntent(intent);
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
            mChrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);
            // HOME while already home never stops the activity, so onStart does not fire for it.
            playWeatherArrivalAnimation();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Both arrivals the launcher has: unlocking the phone, and switching back from another
        // app. Each ends in onStart, because the keyguard stops this activity too.
        View arrivalHost = findViewById(android.R.id.content);
        if (arrivalHost != null) arrivalHost.post(this::playWeatherArrivalAnimation);
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
        applySessionsDrawerLockState();
        syncRecentsVisibilityPolicy();
        configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, false, mPreferences.getSessionsOpacity() / 100f, 0);
        mChrome.requestSync(ChromeRenderer.SCOPE_BLUR_HEALTH);
        registerTermuxActivityBroadcastReceiver();
        registerPackageChangeReceiver();
        registerLauncherAppsCallback();
        registerWallpaperColorsChangedListener();
        refreshCalendarIconsIfDayChanged();
        refreshSuggestionBarIfLauncherCatalogChanged();
        getWindow().getDecorView().post(() -> LauncherCtlApiServer.getInstance().ensureStartedAsync(getApplicationContext()));
        mSurfaceEditor.collapseStatusPaneIfLeftExpanded();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        feedDockPlank(ev);
        feedTerminalPlank(ev);
        mKeybindHintPresenter.onTerminalTouch(ev);
        notifyKeybindHintPanelTouch(ev);
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
                    && y >= mDockPlankTop && y <= mDockPlankTop + mDockPlankHeight
                    && !isOnDockPlankKeySurface(x, y)) {
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

    /**
     * Whether a touch lands on one of the key surfaces the plank carries rather than on the dock.
     *
     * <p>While the in-app keyboard is up the plank target is the whole accessory surface, and that
     * surface holds the keyboard and the extra-keys row alongside the dock. Feeding those touches
     * to the springs tilted and slid the entire plane on every keystroke, so the dock swayed while
     * you typed. The physics belong to pushing the dock, not to pressing a key.</p>
     */
    private boolean isOnDockPlankKeySurface(float rawX, float rawY) {
        for (int viewId : DOCK_PLANK_KEY_SURFACE_IDS) {
            View view = findViewById(viewId);
            if (view == null || view.getVisibility() != View.VISIBLE
                || view.getWidth() <= 0 || view.getHeight() <= 0) {
                continue;
            }
            view.getLocationOnScreen(mDockPlankKeySurfaceLocation);
            float left = mDockPlankKeySurfaceLocation[0];
            float top = mDockPlankKeySurfaceLocation[1];
            if (rawX >= left && rawX <= left + view.getWidth()
                && rawY >= top && rawY <= top + view.getHeight()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.logVerbose(LOG_TAG, "onResume");
        if (mIsInvalidState)
            return;
        // Terminal hierarchy actions from launcherctl/agent/MCP need a foreground
        // Activity; they answer 409 activity_not_running while nothing is attached.
        com.termux.app.terminal.TerminalActionDispatcher.getInstance().attach(terminalHost());
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
        else if (mPaneController != null) {
            mPaneController.refreshPaneSizes();
            // Settings may have flipped the pane behaviour toggles while we were away.
            applyPaneBehaviourPreferences();
        }
        refreshTerminalWindowBar();

        updateWindowBackgroundForCurrentSession();
        syncTerminalWallpaperRenderingMode();
        applySeamlessStatusBackgroundModeIfNeeded();
        applyTerminalSurfaceAppearance();
        syncRecentsVisibilityPolicy();
        applyWallpaperOffsetFixIfNeeded();
        configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, false, mPreferences.getSessionsOpacity() / 100f, 0);
        mChrome.requestSync(ChromeRenderer.SCOPE_BACKDROPS | ChromeRenderer.SCOPE_ACCESSORY_RENDER
            | ChromeRenderer.SCOPE_BLUR_HEALTH);
        refreshPrivilegedBackendIfNeeded();
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
        // Same rule for the find strip: it holds the interceptor and paints over a pane, and both
        // must be handed back before this activity stops being the one on screen.
        if (mFindCoordinator != null) mFindCoordinator.cancel();
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
            int wallpaperDim = resolveWallpaperBackdropDimColor();
            boolean glassPane = isTerminalPaneGlassActive();
            // A rounded Docked terminal is a bounded slab, and a slab's tint cannot be the
            // full-screen dim: painted on the root it fills the very corners the radius is there to
            // cut, and the radius reads as doing nothing. So the root keeps only the wallpaper dim
            // and the tint moves onto the slab itself.
            boolean slab = !glassPane && dockedTerminalCornerRadiusPx() > 0f
                && Color.alpha(terminalSurfaceColor) > 0;
            if (glassPane || slab) {
                // The terminal tint lives on each pane's own glass slab now; the root carries only
                // the wallpaper dim, so the gaps between panes — and the margin around them — show
                // the wallpaper at whatever opacity the Wallpaper control asks for.
                applyUnifiedBackgroundDim(wallpaperDim);
            } else {
                // Unify the background: apply the terminal-opacity dim to the full-screen root so the
                // terminal area, the space under the floating dock, and the gesture-pill strip all read
                // as one continuous surface (the dock then floats on top of it). The bounded
                // terminal_background overlay is retired so the dim isn't applied twice. The wallpaper
                // dim composes underneath it.
                applyUnifiedBackgroundDim(androidx.core.graphics.ColorUtils.compositeColors(
                    terminalSurfaceColor, wallpaperDim));
            }
            terminalSurfaceHost.setBackgroundColor(Color.TRANSPARENT);
            applyTerminalBodySurface(terminalBodySurface,
                slab ? terminalSurfaceColor : Color.TRANSPARENT, slab);
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
        applyTerminalBodySurface(terminalBodySurface, terminalSurfaceColor,
            showSurface && Color.alpha(terminalSurfaceColor) > 0);
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
     * Ceiling on the radius Floating lends the terminal's edge. The dock capsule is a pill; the
     * terminal it floats above is a tall slab, and a pill's radius on a slab reads as a lozenge, so
     * the edge takes the capsule's radius only up to here.
     */
    private static final int TERMINAL_PANE_MAX_CAPSULE_RADIUS_DP = 14;

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

    /**
     * The air the Docked terminal leaves on every side — the surface editor's Margin knob, which is
     * the same number that gaps tiled panes. Floating tucks the frame under the dock's capsule inset
     * instead, so this is not its measure.
     */
    private int dockedTerminalMarginPx() {
        return mPreferences == null ? 0 : Math.round(dpToPx(mPreferences.getTerminalPaneGap()));
    }

    /**
     * The Docked terminal's own corner radius in px, or 0 when it has none to draw with — Floating
     * (where the capsule owns the frame's shape) or the knob's default flush square. Non-zero is
     * what turns the terminal from a full-bleed field into a bounded slab, so every surface that
     * has to agree on that shape reads it from here.
     */
    private float dockedTerminalCornerRadiusPx() {
        if (mPreferences == null || isRoundedDockStyle())
            return 0f;
        return dpToPx(mPreferences.getTerminalCornerRadius());
    }

    /**
     * The radius the terminal's own top corners actually draw with, which is what any surface
     * hanging off them has to match.
     *
     * <p>Docked rounds by the terminal's knob. Floating's slabs round by the dock capsule but
     * capped well under its pill — see {@code paneGlassCornerRadiusPx} — so reading the capsule
     * itself here rounded the hints harder than the window they hang from.
     */
    private float terminalEdgeCornerRadiusPx() {
        if (!isRoundedDockStyle())
            return dockedTerminalCornerRadiusPx();
        return Math.min(dpToPx(TERMINAL_PANE_MAX_CAPSULE_RADIUS_DP),
            resolveDockCapsuleCornerRadiusPx(Integer.MAX_VALUE));
    }

    /**
     * The pane host's clip when it is there to contain, not to shape: a rect inflated by the room a
     * pressed pane travels into.
     *
     * <p>The host clips so a dragged float cannot paint over the dock. Its bounds are the pane area,
     * which is also exactly where a pressed pane's tilt and slide take it — so the clip cut the
     * press off along the margin, taking the slab's lit rim with it, and the border stopped reading
     * as part of the terminal. Slack of a few dp contains the float and lets the press happen. The
     * shaping clips (a Docked radius, the frame's inner radius) are left exact: they are the
     * terminal's own corners, nothing tilts under them, and inflating those would let the corner
     * cells poke past the arc.
     */
    @NonNull
    private ViewOutlineProvider paneHostContainmentOutlineProvider() {
        final int slackPx = Math.round(dpToPx(
            com.termux.app.terminal.TerminalPaneController.PANE_PRESS_SLACK_DP));
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRect(-slackPx, -slackPx,
                    view.getWidth() + slackPx, view.getHeight() + slackPx);
            }
        };
    }

    @NonNull
    private static ViewOutlineProvider roundedOutlineProvider(float radiusPx) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        };
    }

    /**
     * Where the terminal's frame sits inside its host, on one axis.
     *
     * <p>Sideways, Floating tucks under the dock's own capsule inset, which gives it visible air.
     * Vertically it had none, so its edges butted against the status bar and the dock and read as
     * one merged frame; it is held off both by the gap the capsule surfaces leave. Docked spends
     * the user's own Margin knob evenly on all four sides instead.
     *
     * <p>Shared with whatever else has to sit on that edge — the mode hint card hangs off the
     * top-trailing corner and must land on the terminal's edge, not near it.
     *
     * @param framed whether a frame line (the border preference, or the glass rim) is being drawn,
     *     which is what buys the Floating frame its vertical air.
     */
    private int terminalFrameInsetPx(boolean vertical, boolean framed) {
        if (!isRoundedDockStyle())
            return dockedTerminalMarginPx();
        return vertical
            ? (framed ? Math.round(dpToPx(TERMINAL_BORDER_VERTICAL_INSET_DP)) : 0)
            : getDockLayout().horizontalInsetPx;
    }

    /** The same two numbers the frame is laid out with, for a surface that has to meet its edge. */
    private int terminalFrameInsetPx(boolean vertical) {
        boolean framed = mPreferences != null
            && (mPreferences.isTerminalBorderEnabled() || isTerminalPaneGlassActive());
        return terminalFrameInsetPx(vertical, framed);
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
        // With glass on, each pane carries its own lit rim, and a second frame drawn around the
        // whole terminal would box the floating slabs inside a sheet — the exact reading the glass
        // is there to break. So the outer line is the plain-border case only.
        boolean glass = isTerminalPaneGlassActive();
        boolean enabled = preferBorder && singlePane && !glass;
        borderView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        boolean capsule = isRoundedDockStyle();
        int capsuleMarginPx = getDockLayout().horizontalInsetPx;

        // Where a frame line sits, whichever view draws it. Sideways it tucks under the dock's own
        // capsule inset, which gives it visible air. Vertically it had none, so its top edge butted
        // against the status bar's lower edge and its bottom against the dock's upper edge, reading
        // as one merged frame; hold it off both by the gap the capsule surfaces leave.
        //
        // Keyed off the preference rather than off `enabled`, so splitting a window does not shift
        // the terminal: the pane borders land exactly where the terminal border was.
        int borderVerticalInsetPx = terminalFrameInsetPx(true, preferBorder || glass);
        int borderHorizontalInsetPx = terminalFrameInsetPx(false, preferBorder || glass);

        ViewGroup.LayoutParams borderParams = borderView.getLayoutParams();
        if (borderParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) borderParams;
            if (marginParams.leftMargin != borderHorizontalInsetPx || marginParams.rightMargin != borderHorizontalInsetPx
                || marginParams.topMargin != borderVerticalInsetPx
                || marginParams.bottomMargin != borderVerticalInsetPx) {
                marginParams.leftMargin = borderHorizontalInsetPx;
                marginParams.rightMargin = borderHorizontalInsetPx;
                marginParams.topMargin = borderVerticalInsetPx;
                marginParams.bottomMargin = borderVerticalInsetPx;
                borderView.setLayoutParams(marginParams);
            }
        }
        int strokePx = Math.max(1, Math.round(dpToPx(1)));
        // Floating keeps the capsule-derived frame; Docked rounds by the terminal's own knob
        // (default 0 = the flush square frame it always drew).
        float cornerRadiusPx = capsule ? resolveDockCapsuleCornerRadiusPx(Integer.MAX_VALUE)
            : dpToPx(mPreferences.getTerminalCornerRadius());

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
        int paneHorizontalInsetPx = borderHorizontalInsetPx + paneInsetPx;
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
            if (borderView instanceof TerminalGlassFrameView) {
                ((TerminalGlassFrameView) borderView).setRim(false, 0f);
            }
            // The Docked radius is a property of the terminal, not of the frame line: it has to
            // hold with the border off (its default) and with a window split, or the knob only
            // acts in the one configuration that happens to draw a stroke. Glass is the exception
            // — there each pane rounds its own slab, and a second clip around the set of them
            // would box the floating slabs back inside a sheet.
            float hostRadiusPx = glass ? 0f : dockedTerminalCornerRadiusPx();
            applyPaneHostCornerPadding(paneHost, PaneShape.contentInsetPx(hostRadiusPx));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                paneHost.setOutlineProvider(hostRadiusPx > 0f
                    ? roundedOutlineProvider(hostRadiusPx)
                    : paneHostContainmentOutlineProvider());
                paneHost.setClipToOutline(glass || hostRadiusPx > 0f);
            }
            setupTerminalPlankFx(glass);
            updateTerminalGlassFrost();
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
        if (borderView instanceof TerminalGlassFrameView) {
            ((TerminalGlassFrameView) borderView).setRim(false, 0f);
        }

        setupTerminalPlankFx(false);
        updateTerminalGlassFrost();

        float innerRadiusPx = Math.max(0f, cornerRadiusPx - paneInsetPx);
        // Padding the host by the arc's own depth (PaneShape.contentInsetPx) is what keeps the
        // corner glyphs whole, and because it is derived from the radius it holds at 20dp and at
        // 40dp alike — the same trade tmux and zellij make when they spend a whole cell on the
        // frame: the frame owns space the content never enters. Each pane pays the same clearance
        // again for its own corners, against its own radius (PaneContentFrame).
        applyPaneHostCornerPadding(paneHost, PaneShape.contentInsetPx(innerRadiusPx));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            paneHost.setOutlineProvider(innerRadiusPx > 0f
                ? roundedOutlineProvider(innerRadiusPx) : ViewOutlineProvider.BOUNDS);
            paneHost.setClipToOutline(true);
        }
    }

    /**
     * Paints the terminal's own field. Square and full-bleed by default, which is what makes the
     * terminal, the strip under the dock and the gesture-pill area read as one surface; with a
     * Docked corner radius set it becomes a bounded rounded slab inset by the Margin knob, sharing
     * both numbers with the frame line and the pane clip so the three never disagree on an edge.
     */
    private void applyTerminalBodySurface(@NonNull View bodySurface, int color, boolean visible) {
        float radiusPx = dockedTerminalCornerRadiusPx();
        int marginPx = radiusPx > 0f ? dockedTerminalMarginPx() : 0;
        ViewGroup.LayoutParams params = bodySurface.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
            if (marginParams.leftMargin != marginPx || marginParams.topMargin != marginPx
                || marginParams.rightMargin != marginPx || marginParams.bottomMargin != marginPx) {
                marginParams.leftMargin = marginPx;
                marginParams.topMargin = marginPx;
                marginParams.rightMargin = marginPx;
                marginParams.bottomMargin = marginPx;
                bodySurface.setLayoutParams(marginParams);
            }
        }
        if (radiusPx > 0f) {
            GradientDrawable slab = new GradientDrawable();
            slab.setColor(color);
            slab.setCornerRadius(radiusPx);
            bodySurface.setBackground(slab);
        } else {
            bodySurface.setBackground(null);
            bodySurface.setBackgroundColor(color);
        }
        bodySurface.setVisibility(visible ? View.VISIBLE : View.GONE);
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
     * Whether the terminal's glass pane is configured on: border enabled with a blur radius or a
     * grain amount behind it, in wallpaper mode. The pane-count gate lives with the border's own
     * ({@code applyTerminalBorderAppearance} folds both in), since glass and border share a frame.
     */
    private boolean isTerminalGlassConfigured() {
        return mPreferences != null
            && shouldUseWallpaperPassthroughMode()
            && (mPreferences.getTerminalGlassBlurRadius() > 0
                || mPreferences.getTerminalGlassGrain() > 0);
    }

    /**
     * Whether every terminal pane is currently a glass slab of its own.
     *
     * <p>Gated on the border switch because the border is conceptually the pane's edge: with glass
     * on, each pane's lit rim <em>is</em> that border, drawn per pane instead of once around the
     * whole terminal. Deliberately not gated on the pane count — one pane is simply one slab, which
     * is what makes a split read as several floating terminals rather than a sheet with lines on it.
     */
    private boolean isTerminalPaneGlassActive() {
        return isTerminalGlassConfigured() && mPreferences.isTerminalBorderEnabled();
    }

    /**
     * The glass supplier the pane controller paints from. Every value it reads is the same one the
     * dock and status surfaces use, so "Match all surfaces" moves the panes with everything else.
     */
    @NonNull
    private com.termux.app.terminal.TerminalPaneController.PaneSurfaceStyle paneSurfaceStyle() {
        return new com.termux.app.terminal.TerminalPaneController.PaneSurfaceStyle() {
            @Override public boolean isPaneGlassActive() {
                return isTerminalPaneGlassActive();
            }

            @Override @Nullable public Bitmap paneGlassBlurFrame() {
                return obtainTerminalPaneGlassFrame();
            }

            @Override @NonNull public Rect paneGlassBlurFrameRect() {
                return mChrome.blurCache().frameRectRef();
            }

            @Override @Nullable public ColorFilter paneGlassFrostFilter() {
                return com.termux.app.chrome.GlassFilters.frost();
            }

            @Override public int paneGlassTintColor() {
                return shouldShowTerminalOverlaySurface()
                    ? resolveTerminalSurfaceColor() : Color.TRANSPARENT;
            }

            @Override @Nullable public Drawable paneGlassGrainLayer() {
                int grain = mPreferences != null ? mPreferences.getTerminalGlassGrain() : 0;
                return grain > 0 ? mChrome.glass().grainLayer(grain) : null;
            }

            @Override public float paneGlassCornerRadiusPx() {
                if (isRoundedDockStyle())
                    return terminalEdgeCornerRadiusPx();
                // Docked: the glass slabs are the terminal's edge, so they round by the terminal's
                // own knob. Its default 0 keeps the 4dp softening the slabs always had — a glass
                // pane with literally square corners reads as a torn rectangle, not a slab.
                float radiusPx = dockedTerminalCornerRadiusPx();
                return radiusPx > 0f ? radiusPx : dpToPx(4);
            }

            @Override public int paneGapDp() {
                return mPreferences != null ? mPreferences.getTerminalPaneGap()
                    : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_TERMINAL_PANE_GAP;
            }
        };
    }

    /**
     * The shared pre-blurred wallpaper frame the panes draw, remembered so the blur cache never
     * recycles a bitmap a pane is still painting from (the panes hold it in a custom view rather
     * than an ImageView, so the frost-in-use scan cannot find it by drawable).
     */
    @Nullable
    private Bitmap obtainTerminalPaneGlassFrame() {
        int radiusDp = mPreferences != null ? mPreferences.getTerminalGlassBlurRadius() : 0;
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        if (radiusDp <= 0 || wallpaperFrame == null || !isTerminalPaneGlassActive()) {
            mPaneGlassFrame = null;
            return null;
        }
        Bitmap frame = mChrome.blurCache().obtain(radiusDp, wallpaperFrame);
        mPaneGlassFrame = frame != null && !frame.isRecycled() ? frame : null;
        return mPaneGlassFrame;
    }

    /**
     * Dresses the terminal glass backdrop: the shared pre-blurred wallpaper frame shown through the
     * bordered rect (matrix-positioned, so a resize never allocates a terminal-sized bitmap the way
     * a crop would — this surface resizes on every keyboard toggle), with the terminal tint and the
     * film grain layered as its foreground. Localises what used to be the full-screen dim.
     */
    /**
     * Re-dress the panes' glass. Every pane draws the shared pre-blurred wallpaper frame through
     * its own rect, so a wallpaper change, a blur-radius change or a layout move all resolve here
     * rather than in a per-surface bitmap.
     */
    private void updateTerminalGlassFrost() {
        if (mPaneController == null) return;
        mPaneController.setSurfaceStyle(paneSurfaceStyle());
    }

    /**
     * Arms or disarms the panes' glass physics. The rigs themselves live per pane in
     * {@link com.termux.app.terminal.TerminalPaneController}, since with glass on each pane is its
     * own slab and must tip alone.
     */
    private void setupTerminalPlankFx(boolean active) {
        mTerminalGlassActive = active;
    }

    /**
     * Observes (never consumes) touches over the terminal and hands them to the pane under the
     * finger, so scrolling, selecting text or just pressing a pane tips that one physical slab.
     * Overlays that own the screen (drawer, palette, surface editor, FULL status) mute it, so their
     * gestures never tilt the surface underneath.
     */
    private void feedTerminalPlank(MotionEvent ev) {
        if (!mTerminalGlassActive || mPaneController == null) return;
        if (isAppDrawerEngaged() || mSurfaceEditor.isActive() || isCommandPaletteOpen()
            || isFullStatusBarEngaged()) {
            mPaneController.cancelPaneGlassTouch();
            return;
        }
        mPaneController.dispatchPaneGlassTouch(ev, isReducedMotionEnabled());
    }

    /** Black dim over the wallpaper, behind every surface: 0% shows the wallpaper untouched. */
    private int resolveWallpaperBackdropDimColor() {
        int dim = mPreferences != null ? mPreferences.getWallpaperBackdropDim()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_WALLPAPER_BACKDROP_DIM;
        int alpha = Math.round(Math.max(0, Math.min(100, dim)) / 100f * 255f);
        return alpha << 24;   // black at the slider's own alpha
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
                mChrome.blurCache().clear();
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
                mChrome.requestSync(ChromeRenderer.SCOPE_BACKDROPS | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
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

    // -------------------------------------------------------------- "what did that key just do"
    //
    // Tool keys draw a glyph and nothing else, so the row and the keyboard's space-bar swipes are
    // only as discoverable as the guesses people make about them. Every action the dispatcher runs
    // names itself here for a beat, top-right, out of the way of the shell prompt.

    private static final long ACTION_HINT_HOLD_MS = 1100L;
    private static final long ACTION_HINT_FADE_IN_MS = 110L;
    private static final long ACTION_HINT_FADE_OUT_MS = 200L;

    @Nullable private Runnable mActionHintHideRunnable;

    /**
     * Names a dispatched tool in the corner chip; a no-op for tools with no UI title and for
     * self-evident tools — an action whose result is already on screen (a split, a pan, a closed
     * pane) narrates itself, and chipping it too was pure clutter. The chip is for what the screen
     * cannot say: invisible results, refusals, off-screen events.
     */
    void showTerminalActionHint(@NonNull String toolName) {
        TextView chip = findViewById(R.id.terminal_action_hint);
        if (chip == null) return;
        LauncherToolRegistry.ToolMetadata tool = LauncherToolRegistry.getInstance().getTool(toolName);
        if (tool == null || tool.titleRes == 0 || tool.selfEvident) return;
        showTerminalActionHint(chip, getString(tool.titleRes));
    }

    private void showTerminalActionHint(@NonNull TextView chip, @NonNull CharSequence label) {
        if (mActionHintHideRunnable != null)
            chip.removeCallbacks(mActionHintHideRunnable);
        // Both hang from the terminal's top-trailing corner. While a mode legend is up it owns that
        // corner, so the chip stacks underneath it instead of arriving behind it unseen.
        ViewGroup.LayoutParams chipParams = chip.getLayoutParams();
        if (chipParams instanceof ViewGroup.MarginLayoutParams) {
            int cardPx = mModeHintCard != null ? mModeHintCard.occupancyPx() : 0;
            int targetTop = cardPx > 0 ? cardPx + Math.round(dpToPx(6)) : 0;
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) chipParams;
            if (marginParams.topMargin != targetTop) {
                marginParams.topMargin = targetTop;
                chip.setLayoutParams(marginParams);
            }
        }

        chip.setText(label);
        chip.setTextColor(getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface,
            R.color.termux_on_surface));
        GradientDrawable background = new GradientDrawable();
        background.setColor(withAlphaComponent(resolveAccessoryGlassBaseColor(), 235));
        background.setCornerRadius(dpToPx(14));
        background.setStroke(Math.max(1, Math.round(dpToPx(1))),
            withAlphaComponent(resolveAccessoryOutlineColor(), 120));
        chip.setBackground(background);

        chip.animate().cancel();
        chip.setVisibility(View.VISIBLE);
        chip.animate().alpha(1f).setDuration(ACTION_HINT_FADE_IN_MS).start();

        mActionHintHideRunnable = () -> chip.animate().alpha(0f)
            .setDuration(ACTION_HINT_FADE_OUT_MS)
            .withEndAction(() -> chip.setVisibility(View.GONE))
            .start();
        chip.postDelayed(mActionHintHideRunnable, ACTION_HINT_HOLD_MS);
    }

    private int resolveAccessoryGlassBaseColor() {
        // In dark wallpaper mode the glass base deliberately reads the framework's Material You
        // neutral so the dock matches the system exactly; when the chrome belongs to the terminal
        // scheme that bypass would keep every glass surface on the wallpaper palette.
        if (isNightThemeActive() && !LauncherSchemeTheme.isSchemeChromeActive(this)) {
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












    /** Cached light-scatter filter applied to the blurred wallpaper backdrop. */


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
        // The icon row follows the same springs as the glass under it.
        mDockPlankController.setIconLayer(findViewById(R.id.apps_bar_plank_layer));
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
            float radius = isRoundedDockStyle()
                ? resolveDockCapsuleCornerRadiusPx(surfaceHeightPx)
                : resolveDockedDockInnerRadiusPx(surfaceHeightPx);
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
        // Both styles tilt now: the capsule free-floating with a press dip, the edge-to-edge bar
        // hinged at the screen edge and overscanned so its slide cannot open a strip at the sides.
        boolean capsuleDock = isRoundedDockStyle();
        mDockPlankController.setMotionEnabled(true);
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

    private boolean isReducedMotionEnabled() {
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
        if (isNightThemeActive() || LauncherSchemeTheme.isSchemeChromeActive(this)) {
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

    private void applyAccessoryAmbientVeil(@Nullable View accessoryContainer, @NonNull ChromeSpec state) {
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

    /**
     * The legacy left sessions drawer is retired while split panes are on: the sessions panel
     * under the status pill replaces it, and two session managers reachable at once made every
     * rename/close land in the wrong list half the time.
     */
    private void applySessionsDrawerLockState() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer == null)
            return;
        if (isSplitPanesEnabled()) {
            drawer.closeDrawers();
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else if (drawer.getDrawerLockMode(Gravity.LEFT)
            == DrawerLayout.LOCK_MODE_LOCKED_CLOSED) {
            // Copy mode re-asserts its own lock through setDrawerLocked whenever it toggles.
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
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
            int horizontalMargin = getDockLayout().horizontalInsetPx;
            targetLeftMargin = horizontalMargin;
            targetRightMargin = horizontalMargin;
            targetWidth = Math.max(1, targetWidth - (horizontalMargin * 2));
        }
        applyDockSurfaceShape(view, capsuleSurface, targetHeight,
            viewId == R.id.accessory_surface_host);
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
    private void applyAccessorySurfaceBounds(@NonNull ChromeSpec state) {
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
                        capsule, resolveStatusIndicatorCornerRadiusPx(targetHeight, capsule));
                }
            }

            com.termux.app.terminal.TerminalWindowBar windows =
                findViewById(R.id.terminal_window_bar);
            if (windows != null) {
                windows.setSurfaceStyle(capsule,
                    resolveStatusIndicatorCornerRadiusPx(targetHeight, capsule));
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
        // Docked rounds only the edge that faces the terminal; Floating is a card and rounds all
        // four. FULL converges on the capsule radius either way.
        float dockedInner = resolveDockedStatusInnerRadiusPx(
            targetStatusBarHeightPx(false, collapsed));
        mStatusBarSurfaceOutline.setInnerEdgeOnly(!capsule);
        mStatusBarSurfaceOutline.setFrame(capsule ? fullRadius : dockedInner,
            fullRadius, fullProgress);
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
        return DockLayoutPolicy.surfaceHorizontalInsetPx(configuredDp, capsule,
            getResources().getDisplayMetrics().density);
    }

    /** The dock's outer screen margin, shared with the app drawer plane's seed rect. */
    public int getDockHorizontalInsetPx() {
        return getDockLayout().horizontalInsetPx;
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

    /**
     * Internal row inset only; the floating capsule's outer screen margin remains unchanged.
     *
     * <p>The bottom row — sessions chip on the left, status widgets on the right — is bottom-gravity
     * and therefore sits in the surface's bottom corners. Whatever radius rounds those corners eats
     * into the row, so the inset that keeps the content clear of the clip has to follow the radius
     * rather than be a fixed number. Half the radius is the arc's worst-case encroachment over the
     * row's height, which is what both styles grow by.</p>
     *
     * <p>They start from different places because their radii do. Docked is square by default, so
     * its 3dp baseline is the whole story at rest and every bit of radius the user dials in is new
     * encroachment. Floating is a card whose corners are already rounded at rest — 26dp, the auto
     * radius — and its 8dp baseline was measured against exactly that, so only radius beyond the
     * default is encroachment the baseline does not already answer. That keeps a stock Floating
     * surface looking precisely as it did while a raised radius stops clipping the chips.</p>
     */
    private int statusBarContentEdgeInsetPx(boolean capsule) {
        boolean collapsed = mPreferences != null && mPreferences.isTopPaneClockCollapsed();
        float radiusPx = capsule
            ? resolveStatusBarCapsuleCornerRadiusPx(targetStatusBarHeightPx(true, collapsed))
            : resolveDockedStatusInnerRadiusPx(targetStatusBarHeightPx(false, collapsed));
        float baselineRadiusPx = capsule
            ? dpToPx(TermuxPreferenceConstants.TERMUX_APP.STATUS_AUTO_CORNER_RADIUS_MAX_DP)
            : 0f;
        return DockLayoutPolicy.statusBarContentEdgeInsetPx(capsule, radiusPx, baselineRadiusPx,
            getResources().getDisplayMetrics().density);
    }

    /**
     * The shape the status row's two chips wear: the sessions indicator and the window pills.
     *
     * <p>They are content inside the status surface rather than surfaces of their own, so they are
     * not part of the Base cascade and have their own knob. Until it is touched they follow the bar
     * — square while Docked, the capsule's radius while Floating — which is what they always did.
     * Once it is set, that shape holds in both styles, clamped so a chip can never round past half
     * of its own height.
     */
    private float resolveStatusIndicatorCornerRadiusPx(int surfaceHeightPx, boolean capsule) {
        int configured = mPreferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_INDICATOR_CORNER_RADIUS
            : mPreferences.getStatusIndicatorCornerRadius();
        if (configured < 0)
            return capsule ? resolveStatusBarCapsuleCornerRadiusPx(surfaceHeightPx) : 0f;
        // No clamp of our own: both chips draw rounded rects, which already stop at half of the
        // shorter side, and the two chips are not the same height — clamping here would cap the
        // taller one at the shorter one's pill.
        return dpToPx(configured);
    }

    private float resolveStatusBarCapsuleCornerRadiusPx(int surfaceHeightPx) {
        int configuredRadius = mPreferences == null
            ? TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_CORNER_RADIUS
            : mPreferences.getStatusBarCornerRadius();
        if (configuredRadius >= 0) {
            return Math.min(dpToPx(configuredRadius), surfaceHeightPx / 2f);
        }
        return Math.max(
            dpToPx(TermuxPreferenceConstants.TERMUX_APP.STATUS_AUTO_CORNER_RADIUS_MIN_DP),
            Math.min(dpToPx(TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(
                TermuxAppSharedPreferences.SurfaceSlot.STATUS, true)), surfaceHeightPx / 2f));
    }

    private int targetStatusBarHeightPx(boolean capsule, boolean collapsed) {
        return Math.round(dpToPx(collapsed ? capsule ? 30 : 32 : capsule ? 100 : 96));
    }

    /**
     * The top pane's COMPACT height in the current style. Read by the app drawer's top-band
     * choreography, which fakes the expanded→compact collapse with a clip while the plane grows:
     * the real two-state animator writes the pane's height, and the drawer transition freezes
     * exactly that.
     */
    public int getCompactTopStatusBarHeightPx() {
        return getDockLayout().compactStatusBarHeightPx;
    }

    /** Also the command palette's open-state radius, so the two glass surfaces read as one kit. */
    public float resolveDockCapsuleCornerRadiusPx(int surfaceHeightPx) {
        return getDockLayout().capsuleCornerRadiusPx(surfaceHeightPx);
    }

    /**
     * The corner radius a Docked surface puts on its inner edge - the one facing the terminal.
     *
     * <p>Docked is flush with the screen, so the radius stops describing a floating card and starts
     * describing the frame the terminal sits inside. A configured value below zero is the
     * theme-defined sentinel, which resolves to a straight edge here rather than to the 16-26dp the
     * capsule takes: Docked has always been square, and an upgrade must not quietly round it.
     */
    private float resolveDockedInnerRadiusPx(int configuredDp, int surfaceHeightPx) {
        if (isRoundedDockStyle() || configuredDp < 0)
            return 0f;
        return Math.min(dpToPx(configuredDp), Math.max(0, surfaceHeightPx) / 2f);
    }

    private float resolveDockedDockInnerRadiusPx(int surfaceHeightPx) {
        return resolveDockedInnerRadiusPx(mPreferences == null
            ? -1 : mPreferences.getAppLauncherDockCornerRadius(), surfaceHeightPx);
    }

    private float resolveDockedStatusInnerRadiusPx(int surfaceHeightPx) {
        return resolveDockedInnerRadiusPx(mPreferences == null
            ? -1 : mPreferences.getStatusBarCornerRadius(), surfaceHeightPx);
    }

    private final com.termux.app.surfaces.InnerEdgeOutlineProvider mDockInnerEdgeOutline =
        new com.termux.app.surfaces.InnerEdgeOutlineProvider(
            com.termux.app.surfaces.InnerEdgeOutlineProvider.Edge.TOP);

    private void applyDockSurfaceShape(@NonNull View surface, boolean capsule, int surfaceHeightPx,
                                       boolean ownsInnerEdge) {
        if (!capsule) {
            surface.setBackground(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Clip the normal dock to its own bounds so the reactive edge-glow's outward blur
                // can't spill past the dock edges and make it look wider - now with the inner edge
                // rounded when the user has asked for it.
                if (!ownsInnerEdge) {
                    surface.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                    surface.setClipToOutline(true);
                    return;
                }
                boolean changed = mDockInnerEdgeOutline.setRadiusPx(
                    resolveDockedDockInnerRadiusPx(surfaceHeightPx));
                if (surface.getOutlineProvider() != mDockInnerEdgeOutline)
                    surface.setOutlineProvider(mDockInnerEdgeOutline);
                else if (changed)
                    surface.invalidateOutline();
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

    @NonNull
    private ChromeSpec buildChromeSpec() {
        boolean keyboardShown = isInAppKeyboardShown();
        int keyboardHeight = keyboardShown ? mKeyboardGeometry.measureHeightPx() : 0;
        if (mPreferences == null) {
            return new ChromeSpec(false, keyboardShown, keyboardHeight,
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
        return new ChromeSpec(
            dockShown,
            keyboardShown,
            keyboardHeight,
            ChromePolicy.dockBlurEnabled(blurRadiusDp),
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
    private boolean dockBoundsOnScreen(@NonNull Rect out) {
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


    private boolean shouldUseAccessoryRenderEffectBlur(@NonNull ChromeSpec state) {
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
        mChrome.ledger().reset(SurfaceDirtyLedger.Backdrop.ACCESSORY);
    }

    private boolean shouldShowDecorNavBarSurface(@NonNull ChromeSpec state) {
        // Floating capsules leave the gesture-pill inset showing wallpaper; edge-to-edge surfaces
        // (dock glass, or the embedded keyboard's own background) continue under the pill.
        return shouldShowDecorNavBarSurface(state.toolbarShown, state.keyboardShown,
            mNavBarHeight, mKeyboardGeometry.lastImeVisible() || isImeVisible(), isRoundedDockStyle(),
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

    private boolean shouldUseDockDecorNavBarSurface(@NonNull ChromeSpec state) {
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
    private boolean shouldUseUnifiedDefaultKeyboardGlassSurface(@NonNull ChromeSpec state) {
        // A scheme background color or a non-default background opacity must repaint only the
        // keyboard, not the material it would share with the dock, so either drops the keyboard
        // to its own local surface path.
        return ChromePolicy.shouldUseUnifiedDefaultKeyboardGlassSurface(state.toolbarShown,
            state.keyboardShown, isRoundedDockStyle(), isInAppKeyboardGlassSurface())
            && !hasInAppKeyboardBackgroundOverride();
    }


    /** Readiness of the keyboard-local (non-unified) blurred backdrop for the current target. */
    private boolean isInAppKeyboardLocalBackdropReady(@NonNull ChromeSpec state) {
        View surfaceHost = findViewById(R.id.inapp_keyboard_view_host);
        if (surfaceHost == null) return true;
        if (mInAppKeyboardBackdropBitmap == null
            || mChrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD)) return false;
        Rect targetRect = buildInAppKeyboardBackdropTargetRect(state, surfaceHost);
        return targetRect == null || mChrome.ledger().matchesLastRect(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD, targetRect);
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
        mChrome.requestSync(ChromeRenderer.SCOPE_NAV_STRIP_BACKDROP);
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
        applyDockSurfaceShape(overlay, false, targetHeight, false);
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
            mChrome.requestSync(ChromeRenderer.SCOPE_NAV_STRIP_BACKDROP);
        }
    }

    private void applyDecorNavBarSurfaceState(@NonNull ChromeSpec state) {
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
            mDecorNavBarTintOverlay.setBackground(buildDecorNavBarTint(state));
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

    /**
     * The under-pill strip's tint: the bottom slice {@code [f, 1]} of the same light model the
     * surface directly above it renders as {@code [0, f]}, so one foot lands under the pill.
     *
     * <p>Which surface that is depends on the keyboard. The strip used to take the dock's material
     * in both states, which was right only while the keyboard followed Base: a keyboard with its
     * own opacity — or a scheme background colour — painted itself near-solid and left the strip
     * under it at the dock's opacity, so the material visibly stopped at the keyboard's bottom edge
     * and the gesture-nav band read as bare wallpaper. The strip now takes whichever surface is
     * above it, which is what makes the two read as one slab in both states.
     */
    @NonNull
    private Drawable buildDecorNavBarTint(@NonNull ChromeSpec state) {
        float foot = defaultDockGlassFootFraction();
        if (!state.keyboardShown)
            return mChrome.glass().dockSurface(state.barAlpha, foot, 1f, false);
        // Same three values buildInAppKeyboardSurfaceBackground resolves, so the strip cannot
        // disagree with the keyboard about its own material.
        boolean normalized = isInAppKeyboardOpacityLinked();
        Integer schemeBackground = normalized ? null : resolveInAppKeyboardSchemeBackgroundColor();
        int backgroundAlpha = normalized ? 255 : Math.round(
            255f * getInAppKeyboardBackgroundOpacityPercent() / 100f);
        if (schemeBackground != null)
            return new ColorDrawable(withAlphaComponent(schemeBackground, backgroundAlpha));
        Drawable tint = mChrome.glass().dockSurface(state.barAlpha, foot, 1f, false);
        if (backgroundAlpha < 255)
            tint.setAlpha(backgroundAlpha);
        return tint;
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
            return dockHeight + mNavBarHeight + getDockLayout().capsuleBottomGapPx;
        }
        // Default dock renders its body in-content; the decor overlay is the under-pill nav strip
        // only. Size it from the in-content surface's actual bottom edge down to the screen bottom
        // rather than from mNavBarHeight — the content's applied bottom inset can differ from the
        // system-bars height by a few px, which otherwise leaves a thin wallpaper gap (or an overlap)
        // between the dock/keyboard bottom and the strip. This makes them meet exactly.
        int measured = measuredUnderPillStripHeightPx();
        int targetHeight = measured > 0 ? measured : mNavBarHeight;
        if (mKeyboardGeometry.isCloseGeometryPending()) {
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

    /**
     * True when the scheme's background color or the opacity slider repaints the surface.
     *
     * <p>"Match all surfaces" outranks both. An edited keyboard scheme sets a background swatch,
     * which used to drop the keyboard onto its own local surface painted in that colour — a
     * surface no dock/status opacity write reaches, so the keyboard sat visibly lighter than
     * every other surface until the keyboard section was reset. While surfaces are normalized the
     * keyboard renders the shared material and the scheme keeps only its key colours.</p>
     */
    private boolean hasInAppKeyboardBackgroundOverride() {
        return ChromePolicy.hasInAppKeyboardBackgroundOverride(isInAppKeyboardOpacityLinked(),
            resolveInAppKeyboardSchemeBackgroundColor(),
            getInAppKeyboardBackgroundOpacityPercent(),
            mPreferences != null ? mPreferences.getAppBarOpacity()
                : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY);
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

    /** True when the keyboard renders as the floating Rounded surface. */
    private boolean isInAppKeyboardCapsule() {
        // The keyboard's shape always follows the single global surface shape; the old dock-match
        // mode that let it differ is gone.
        return isRoundedDockStyle();
    }

    /**
     * Applies the in-app keyboard's surface treatment: rounded shape (margins + rounded clip +
     * inner padding) when the Rounded surface style is active, and the dock's blurred-wallpaper +
     * tinted-glass stack behind the keys. The glass stack is
     * rendered as the host's background drawable (a pre-blurred wallpaper crop under the same
     * tint used by {@link #buildDockGlassSurface}) so the wrap-content keyboard measurement is
     * never affected by extra sibling views.
     */
    private void applyInAppKeyboardSurfaceState(@NonNull ChromeSpec state) {
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
        int bottomMargin = capsule ? getDockLayout().capsuleBottomGapPx : 0;
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
                mKeyboardGeometry.markHeightDirty();
                mChrome.requestSync(ChromeRenderer.SCOPE_KEYBOARD_BACKDROP);
            }
        }
        if (surfaceHost.getPaddingLeft() != innerPadding
            || surfaceHost.getPaddingTop() != innerPadding
            || surfaceHost.getPaddingRight() != innerPadding
            || surfaceHost.getPaddingBottom() != innerPadding) {
            surfaceHost.setPadding(innerPadding, innerPadding, innerPadding, innerPadding);
            mKeyboardGeometry.markHeightDirty();
            mChrome.requestSync(ChromeRenderer.SCOPE_KEYBOARD_BACKDROP);
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
    private Drawable buildInAppKeyboardSurfaceBackground(@NonNull ChromeSpec state,
                                                         @NonNull View surfaceHost,
                                                         boolean capsule, boolean glassTheme,
                                                         float cornerRadiusPx) {
        java.util.List<Drawable> layers = new java.util.ArrayList<>();
        // While the keyboard's opacity still follows Base it renders the shared material, so its
        // own background colour and opacity are ignored here exactly as in the unified path.
        boolean normalized = isInAppKeyboardOpacityLinked();
        Integer schemeBackground = normalized ? null : resolveInAppKeyboardSchemeBackgroundColor();
        int backgroundAlpha = normalized ? 255 : Math.round(
            255f * getInAppKeyboardBackgroundOpacityPercent() / 100f);
        if (glassTheme) {
            if (state.blurEnabled) {
                Bitmap blurredBackdrop = obtainInAppKeyboardBackdropBitmap(state, surfaceHost);
                if (blurredBackdrop != null) {
                    BitmapDrawable backdrop = new BitmapDrawable(getResources(), blurredBackdrop);
                    // Same content-aware light scatter the dock backdrop uses — one material.
                    backdrop.setColorFilter(com.termux.app.chrome.GlassFilters.frost());
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
                Drawable tint = mChrome.glass().dockSurface(
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
        mChrome.ledger().reset(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD);
        recycleSupersededInAppKeyboardBackdrop(previousBackdrop, null);
    }

    private void recycleSupersededInAppKeyboardBackdrop(@Nullable Bitmap previousBackdrop,
                                                         @Nullable Drawable installedBackground) {
        if (previousBackdrop == null || previousBackdrop == mInAppKeyboardBackdropBitmap
            || mChrome.blurCache().containsFrame(previousBackdrop)
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
    private Bitmap obtainInAppKeyboardBackdropBitmap(@NonNull ChromeSpec state,
                                                     @NonNull View surfaceHost) {
        View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
        if (wallpaperFrame == null) {
            return mChrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD) ? null : mInAppKeyboardBackdropBitmap;
        }

        Rect targetRect = buildInAppKeyboardBackdropTargetRect(state, surfaceHost);
        if (targetRect == null) {
            return mChrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD) ? null : mInAppKeyboardBackdropBitmap;
        }
        boolean usingManagedWallpaperSource = shouldUseManagedWallpaperBlurSource();
        if (!mChrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD) &&
            mChrome.ledger().lastRadiusDp(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD) == state.blurRadiusDp &&
            mChrome.ledger().lastManagedSource(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD) == usingManagedWallpaperSource &&
            mChrome.ledger().matchesLastRect(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD, targetRect) &&
            mInAppKeyboardBackdropBitmap != null) {
            return mInAppKeyboardBackdropBitmap;
        }

        Bitmap blurredBackdrop = mChrome.blurCache().crop(state.blurRadiusDp, targetRect, wallpaperFrame);
        if (blurredBackdrop == null) {
            // A previous-geometry crop is worse than tint-only glass: BitmapDrawable would scale it
            // into the new keyboard height and briefly sample the wrong wallpaper region.
            return mChrome.ledger().matchesLastRect(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD, targetRect)
                ? mInAppKeyboardBackdropBitmap : null;
        }
        mInAppKeyboardBackdropBitmap = blurredBackdrop;
        mChrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD, state.blurRadiusDp,
            usingManagedWallpaperSource, targetRect);
        return mInAppKeyboardBackdropBitmap;
    }

    /**
     * Returns the keyboard crop even during its first pre-layout render. The accessory stack is
     * bottom-anchored, so its already-laid-out bottom remains a stable reference while the new
     * keyboard height is waiting for layout.
     */
    @Nullable
    private Rect buildInAppKeyboardBackdropTargetRect(@NonNull ChromeSpec state,
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
    private boolean isUnifiedAccessoryBackdropReady(@NonNull ChromeSpec state) {
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
        return mChrome.ledger().lastRadiusDp(SurfaceDirtyLedger.Backdrop.ACCESSORY) == state.blurRadiusDp
            && mChrome.ledger().lastManagedSource(SurfaceDirtyLedger.Backdrop.ACCESSORY) == shouldUseManagedWallpaperBlurSource()
            && mChrome.ledger().matchesLastRect(SurfaceDirtyLedger.Backdrop.ACCESSORY, currentTarget);
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
        mChrome.ledger().reset(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR);
    }

    private void updateDecorNavBarBackdrop(@NonNull ChromeSpec state) {
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
        if (!mChrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR) &&
            mChrome.ledger().lastRadiusDp(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR) == state.blurRadiusDp &&
            mChrome.ledger().lastManagedSource(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR) == usingManagedWallpaperSource &&
            mChrome.ledger().matchesLastRect(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR, targetRect) &&
            backdrop.getDrawable() != null) {
            backdrop.setVisibility(View.VISIBLE);
            return;
        }

        Bitmap wallpaperBackdrop = mChrome.blurCache().crop(state.blurRadiusDp, targetRect, wallpaperFrame);
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
        backdrop.setColorFilter(com.termux.app.chrome.GlassFilters.frost());
        backdrop.setVisibility(View.VISIBLE);
        mChrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR, state.blurRadiusDp,
            usingManagedWallpaperSource, targetRect);
    }



    private boolean isAccessoryBlurHealthy(@NonNull ChromeSpec state) {
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
     * display center. ROMs that honor {@code setWallpaperZoomOut(0)} render at 1.0 and need no
     * compensation; Nothing OS applies its own regardless of what the launcher asks for.
     *
     * <p>Re-measured 2026-08-20 and corrected from 1.10 to
     * {@link #NOTHING_OS_WALLPAPER_RENDER_ZOOM}. The method: set the wallpaper through the in-app
     * picker, which keeps a byte-exact copy of the source, then fit the mapping
     * {@code source = ((screen - centre) / zoom + centre) / fillScale} against the sharp wallpaper
     * still visible in a screenshot's margins. 1.03 fit to a mean squared error of 4 with no pan
     * term; 1.00 gave 355 and the old 1.10 gave 564. Over-compensating by those seven percent
     * displaced every frost by ~7% of its distance from the centre — around 75px at the status
     * bar — which is what "the blur is offset" was.
     *
     * <p>If a future ROM changes this again, that measurement is how to re-derive it: the numbers
     * above are the fit quality to beat, not a value to nudge by eye.
     */
    private float systemWallpaperRenderZoom() {
        return "nothing".equalsIgnoreCase(Build.MANUFACTURER)
            ? NOTHING_OS_WALLPAPER_RENDER_ZOOM : 1f;
    }

    /**
     * Extra magnification Nothing OS applies at composite time, on top of whatever scaling is
     * already baked into the stored wallpaper bitmap.
     *
     * <p>Why one constant covers both a wallpaper this app set and one the system cropped: the
     * frost samples {@code WallpaperManager.getDrawable()}, which returns the bitmap <em>as the
     * system stored it</em>, not the file the user picked. The ROM upscales what it is given until
     * the stored bitmap is ~1.10x the display and shows the middle — with a 1250x2500 image applied
     * through the system picker on a 1080x2412 panel, {@code dumpsys wallpaper} reports
     * {@code mCropHint=Rect(0, 0 - 1328, 2654)}, and 2654 / 2412 = 1.1003. That 1.10 is therefore
     * already present in the pixels handed to us, and re-applying it was the bug: it double-counted
     * the store-scale. What remains is the composite zoom, measured at 1.03 (see
     * {@link #systemWallpaperRenderZoom()} for the method and the fit quality).
     *
     * <p>The in-app picker is the well-defined case regardless: it hands the system a bitmap
     * already in the display's aspect and a crop hint covering all of it, so the store-scale is a
     * no-op and only this composite zoom applies.
     */
    private static final float NOTHING_OS_WALLPAPER_RENDER_ZOOM = 1.03f;

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


    private void maybeRequestWallpaperReadPermission() {
        if (!mIsVisible || mPreferences == null || isFinishing() || isDestroyed()) {
            return;
        }
        // On a fresh install the wallpaper read fails while the first-launch tour is still up, so
        // this reactive prompt used to open over the tour. The first-run permission chain asks the
        // same question after the tour is dismissed; if the user skips it there, the next failed
        // read after onboarding re-triggers this path.
        if (com.termux.app.onboarding.FirstLaunchOnboarding.isShowing(this)) {
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
            || !ChromePolicy.shouldPromptForWallpaperRead(mWallpaperReadPermissionDenied,
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
        try {
            return drawWallpaperBackdrop(wallpaper, targetRect);
        } finally {
            // getDrawable() leaves the framework holding the decoded wallpaper in
            // WallpaperManager$Globals for the life of the process — 16.6 MB of this one, at
            // 1400x3100 against a 1080x2412 screen, because the wallpaper is zoomed. It is a pure
            // cache and nothing here reads it again: the crop drawn just now is what gets cached,
            // and this runs only when that cache misses, so the re-read costs nothing anyone waits
            // for. Ours is drawn by the time this runs.
            try {
                wallpaperManager.forgetLoadedWallpaper();
            } catch (Exception ignored) {
                // A vendor implementation that refuses simply keeps its cache.
            }
        }
    }

    private Bitmap drawWallpaperBackdrop(@NonNull Drawable wallpaper, @NonNull Rect targetRect) {
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
        if (frame == mPaneGlassFrame) {
            return true;   // a pane is drawing it right now; recycling it would crash its next draw
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


    private void updateAccessoryRenderEffectBackdrop(@NonNull ChromeSpec state) {
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
        if (!mChrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY) &&
            mChrome.ledger().lastRadiusDp(SurfaceDirtyLedger.Backdrop.ACCESSORY) == state.blurRadiusDp &&
            mChrome.ledger().lastManagedSource(SurfaceDirtyLedger.Backdrop.ACCESSORY) == usingManagedWallpaperSource &&
            mChrome.ledger().matchesLastRect(SurfaceDirtyLedger.Backdrop.ACCESSORY, backdropTargetRect) &&
            isAccessoryBackdropCropHeightCompatible(backdrop, backdropTargetRect.height())) {
            backdrop.setVisibility(View.VISIBLE);
            return;
        }
        Bitmap wallpaperBackdrop = mChrome.blurCache().crop(
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
                mChrome.ledger().markDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY);
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
        backdrop.setColorFilter(com.termux.app.chrome.GlassFilters.frost());
        backdrop.setVisibility(View.VISIBLE);
        mChrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.ACCESSORY, state.blurRadiusDp,
            usingManagedWallpaperSource, backdropTargetRect);
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
    private Rect buildToolbarOnlyAccessoryBounds(@NonNull ChromeSpec state) {
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

    private void applyChromeSpec(@NonNull ChromeSpec state) {
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
            applyRealtimeBlurDownsampleFactor(extraKeysBackgroundBlur, ChromePolicy.ACCESSORY_BLUR_DOWNSAMPLE_FACTOR);
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
            mKeyboardGeometry.completePendingOpenReveal(state);
            mKeyboardGeometry.completePendingCloseGeometry(state);
            configureAccessoryTopEdgeFx(false, state.barAlpha);
            configureExtraKeysDivider(false, 0f);
            resetAzOverflowAffordanceState();
            if (mDockPlankController != null) {
                mDockPlankController.setEnabled(false);
            }
            mChrome.requestSync(ChromeRenderer.SCOPE_TOP_PANE_FROST);
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
            extraKeysBackground.setBackground(mChrome.glass().dockSurface(state.barAlpha,
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
        mChrome.requestSync(ChromeRenderer.SCOPE_TOP_PANE_FROST);
        mKeyboardGeometry.completePendingOpenReveal(state);
        mKeyboardGeometry.completePendingCloseGeometry(state);
        updateAzOverflowAffordance();
    }

    /**
     * The in-app keyboard's geometry and its flash-free reveal choreography: the measurement memo,
     * the pending-reveal/pending-close protocol, the two pre-draw gates and the system-IME inset
     * gate. The Activity keeps the chrome <em>painters</em> — they are welded to the glass factory,
     * the backdrop bitmaps and the dock layout — and hands them to the module below.
     */
    private final KeyboardGeometryChoreographer mKeyboardGeometry =
        new KeyboardGeometryChoreographer(new KeyboardGeometryChoreographer.Surface() {

            @Nullable @Override public View findView(int viewId) {
                return findViewById(viewId);
            }

            @NonNull @Override public DisplayMetrics displayMetrics() {
                return getResources().getDisplayMetrics();
            }

            @Nullable @Override public View attachedKeyboardView() {
                return mAttachedInAppKeyboardView;
            }

            @NonNull @Override public ChromeSpec buildChromeSpec() {
                return TermuxActivity.this.buildChromeSpec();
            }

            @Override public void applyChromeSpec(@NonNull ChromeSpec spec) {
                TermuxActivity.this.applyChromeSpec(spec);
            }

            @Override public void applyKeyboardSurfaceState(@NonNull ChromeSpec spec) {
                applyInAppKeyboardSurfaceState(spec);
            }

            @Override public void requestAccessoryRenderSync() {
                mChrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);
            }

            @Override public void applyAccessoryGeometry(@NonNull String reason) {
                applyAccessoryGeometryIfNeeded(true, reason);
            }

            @Override public boolean keyboardGlassSurface() {
                return isInAppKeyboardGlassSurface();
            }

            @Override public boolean keyboardBackdropReady(@NonNull ChromeSpec spec) {
                // The unified default-dock surface waits on the shared accessory crop; a
                // capsule/local surface waits on its own keyboard backdrop bitmap.
                return shouldUseUnifiedDefaultKeyboardGlassSurface(spec)
                    ? isUnifiedAccessoryBackdropReady(spec)
                    : isInAppKeyboardLocalBackdropReady(spec);
            }

            @Override public boolean dockBackdropSafeForDestination(@NonNull ChromeSpec spec) {
                return isDockBackdropSafeForCurrentDestination(spec);
            }

            @Override public void invalidateTransitionCrops() {
                // Geometry-dependent crops only; the shared full-frame blur is preserved.
                mChrome.ledger().markDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD);
                mChrome.ledger().invalidateRect(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD);
                mChrome.ledger().markDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY);
                mChrome.ledger().invalidateRect(SurfaceDirtyLedger.Backdrop.ACCESSORY);
                mChrome.ledger().markDirty(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR);
            }

            @Override public void invalidateCloseSettledCrops() {
                mChrome.requestSync(ChromeRenderer.SCOPE_KEYBOARD_BACKDROP
                    | ChromeRenderer.SCOPE_NAV_STRIP_BACKDROP);
            }

            @Override public void invalidateAccessoryCrop() {
                mChrome.requestSync(ChromeRenderer.SCOPE_DOCK_BACKDROP);
            }

            @Override public void onKeyboardClosed() {
                mKeybindHintPresenter.hideAfterLinger();
            }

            @Override public void requestApplyInsets() {
                View content = findViewById(android.R.id.content);
                if (content != null)
                    ViewCompat.requestApplyInsets(content);
            }

            @Override public void postDelayed(@NonNull Runnable runnable, long delayMs) {
                mAccessoryRenderHandler.postDelayed(runnable, delayMs);
            }

            @Override public void removeCallbacks(@NonNull Runnable runnable) {
                mAccessoryRenderHandler.removeCallbacks(runnable);
            }

            @Override public boolean isActivityAlive() {
                return !isFinishing() && !isDestroyed();
            }
        });

    private boolean isDockBackdropSafeForCurrentDestination(@NonNull ChromeSpec state) {
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
        int railWidthPx = railActive ? getDockLayout().railWidthPx : 0;
        int leftContentInsetPx = railActive && !railOnRight ? railWidthPx : cutoutInsets.left;
        int rightContentInsetPx = railActive && railOnRight ? railWidthPx : cutoutInsets.right;
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
            mChrome.requestSync(ChromeRenderer.SCOPE_TOP_PANE_FROST);
            return;
        }
        float opacity = mPreferences != null ? mPreferences.getStatusBarOpacity() / 100f : 1f;
        int blurRadiusDp = getEffectiveStatusBarBlurRadius();
        boolean statusBlurEnabled = ChromePolicy.dockBlurEnabled(blurRadiusDp);
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
        applyRealtimeBlurDownsampleFactor(statusBlur, ChromePolicy.ACCESSORY_BLUR_DOWNSAMPLE_FACTOR);
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
            statusSurface.setBackground(mChrome.glass().statusBarSurface(opacity, 0f,
                terminalWindowGlassStatusFraction(host)));
            statusSurface.setVisibility(View.VISIBLE);
        }
        mChrome.requestSync(ChromeRenderer.SCOPE_TOP_PANE_FROST);
    }

    /** Wallpaper frost for the command palette glass; true when the live blur should rest. */
    public boolean applyCommandPaletteWallpaperFrost(@NonNull ImageView frost) {
        return mChrome.frost().applyCommandPalette(frost);
    }

    private float terminalWindowGlassStatusFraction(@NonNull View host) {
        int paneHeight = host.getLayoutParams() != null ? host.getLayoutParams().height : 0;
        if (paneHeight <= 0) paneHeight = Math.round(dpToPx(96));
        return mLastStatusBarInsetTop / (float) (mLastStatusBarInsetTop + paneHeight);
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
        if (!mKeyboardGeometry.acceptsSystemImeInsets() || isSystemImeSuppressedByInAppKeyboard()) {
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
        return mKeyboardGeometry.acceptsSystemImeInsets();
    }

    /** Marks subsequent IME insets as activity-owned rather than inherited from the previous app. */
    public void onSystemImeRequested() {
        mKeyboardGeometry.onSystemImeRequested();
    }

    private void resetInheritedImeLayoutState() {
        mKeyboardGeometry.onSystemImeReleased();
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
        com.termux.app.terminal.TerminalActionDispatcher.getInstance().detach(terminalHost());
        mTerminalFrameMetricsMonitor.stop();
        stopAzEdgePagingLoop();
        cancelAzOverflowRefresh();
        mWindowLabelHandler.removeCallbacksAndMessages(null);
        mBackgroundProcessHandler.removeCallbacks(mBackgroundProcessResync);
        mStatusCardHost.dismiss();
        mKeybindHintPresenter.hideNow(false);
        if (mStatsController != null) mStatsController.stop();
        if (mAiIndicatorController != null) mAiIndicatorController.stop();
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
        mChrome.cancelPendingWork();
        mKeyboardGeometry.onStop();
        applyDockImeOffset(0);
        clearAccessoryRenderEffectBackdrop();
        hideDecorNavBarSurfaceOverlay(true);
        mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
        mAzGestureHandler.removeCallbacks(mLauncherCatalogWarmRunnable);
        getDrawer().closeDrawers();
        mSurfaceEditor.restoreExpandedStatusAfterSurfaceEditor();
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
        if (mIsInvalidState) return;
        // The terminal hears about pressure before the chrome does, and at levels the chrome
        // ignores. RUNNING_LOW and RUNNING_CRITICAL arrive while we are still in front — which for
        // a home app is exactly when a vendor's killer is circling — and the biggest thing a
        // terminal holds by far is decoded animation frames: a single full-rate 512x512 GIF is
        // over a hundred megabytes of them. Giving those up costs the logos their motion and
        // nothing else; being killed costs every open session.
        //
        // RUNNING_MODERATE and UI_HIDDEN are deliberately not enough. The first is a hint, not
        // pressure, and the second only means the UI went away — an animation that is merely
        // hidden already costs nothing, because the visibility gate has stopped it.
        if (level >= TRIM_MEMORY_RUNNING_LOW) dropTerminalAnimationFrames();
        if (level < TRIM_MEMORY_BACKGROUND) {
            return;
        }
        mChrome.onTrimMemory();
        clearInAppKeyboardBackdrop();
        clearAccessoryRenderEffectBackdrop();
    }

    /** Drop every session's kitty animation frames, keeping the still image each rests on. */
    private void dropTerminalAnimationFrames() {
        if (mTermuxService == null) return;
        for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession session
                : mTermuxService.getTermuxSessions()) {
            TerminalSession terminalSession = session.getTerminalSession();
            if (terminalSession == null) continue;
            TerminalEmulator emulator = terminalSession.getEmulator();
            if (emulator != null) emulator.dropKittyAnimationFrames();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logDebug(LOG_TAG, "onDestroy");
        com.termux.app.terminal.TerminalActionDispatcher.getInstance().detach(terminalHost());
        mTerminalFrameMetricsMonitor.stop();
        // The inspector holds this Activity strongly for the life of its overlay, so it has to go
        // with the Activity rather than outlive it.
        com.termux.app.terminal.TerminalKeyInspector.close();
        mChrome.onDestroy();
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
        mChrome.onConfigurationChanged();
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
        mChrome.requestSync(ChromeRenderer.SCOPE_APPLY_NOW);
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
        mChrome.cancelPendingRender();
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

        // The bar lives one level down, inside the plank transform layer, so the dock physics can
        // tilt and shift the icons without fighting the bar's own page-switch transforms or the
        // drawer's lift translation on the pager itself.
        FrameLayout appsBarPlankLayer = findViewById(R.id.apps_bar_plank_layer);
        final ViewGroup appsBarHost = appsBarPlankLayer != null ? appsBarPlankLayer : appsBarContainer;

        if (mSuggestionBarView == null) {
            LayoutInflater inflater = LayoutInflater.from(TermuxActivity.this);
            mSuggestionBarView = (SuggestionBarView) inflater.inflate(R.layout.suggestion_bar, appsBarHost, false);
        } else if (mSuggestionBarView.getParent() instanceof ViewGroup) {
            ((ViewGroup) mSuggestionBarView.getParent()).removeView(mSuggestionBarView);
        }

        if (mSuggestionBarView.getParent() != appsBarHost) {
            appsBarHost.removeAllViews();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            appsBarHost.addView(mSuggestionBarView, params);
        }

        mSuggestionBarView.setAppDataProvider(mLauncherAppDataProvider);
        mSuggestionBarView.setConfigRepository(mLauncherConfigRepository);
        mSuggestionBarView.setAppCatalogChangedListener(() -> {
            syncAzScrubLettersAndTint();
            updateDockRailView();
        });
        mSuggestionBarView.setFolderRenameHost(new SuggestionBarView.FolderRenameHost() {
            @Override public void beginFolderRename(long revision, @NonNull String folderId,
                                                   @NonNull String title,
                                                   @NonNull FolderRenameTitleView titleView) {
                TermuxActivity.this.beginFolderRename(revision, folderId, title, titleView);
            }

            @Override public void cancelFolderRename() {
                TermuxActivity.this.cancelFolderRename();
            }
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
                public boolean isSurfaceEditorActive() {
                    return mSurfaceEditor.isActive();
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
        applyDockLayout(buildDockLayout(0));
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
    private boolean handleTerminalAppSearchKey(int keyCode) {
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
            mChrome.requestSync(accessorySkipScopes(reason));
            return;
        }
        mLastAccessoryGeometryApplyUptimeMs = now;
        updateAppLauncherBarHeight();
        setTerminalToolbarHeight(true);
        mChrome.requestSync(ChromeRenderer.SCOPE_APPLY_NOW);
    }

    static int calculateSuggestionBarMaxButtons(DisplayMetrics displayMetrics) {
        if (displayMetrics == null) {
            return 1;
        }
        float density = Math.max(displayMetrics.density, 0.1f);
        int screenWidthDp = (int) (displayMetrics.widthPixels / density);
        return Math.max(1, screenWidthDp / SUGGESTION_BAR_MIN_BUTTON_DP);
    }

    /** The dock button count the styling pass last saw, so only a real change rebuilds the row. */
    private int mLastStyledDockButtonCount = Integer.MIN_VALUE;

    /**
     * The dock styling a surface-editor tick can change, and nothing else. This is the per-frame
     * body of a slider drag's glass preview: the full {@link #applySuggestionBarPreferences} also
     * reloads the app catalog and re-wires listeners, which is a rebuild of the whole dock row —
     * paying that once per frame was most of the editor's drag jank. The apps-per-page slider is
     * the one control whose preview really needs the row rebuilt, so a changed count still does.
     */
    private void applySuggestionBarSurfaceStyling() {
        if (mSuggestionBarView == null || mPreferences == null) {
            return;
        }
        DockLayout dockLayout = getDockLayout();
        mSuggestionBarView.setIconScale(dockLayout.iconScale);
        mSuggestionBarView.setDockRowHeightHintPx(dockLayout.appsBarHeightHintPx);
        mSuggestionBarView.setAppBarOpacity(mPreferences.getAppBarOpacity());
        int blurRadiusDp = getEffectiveExtraKeysBlurRadius();
        mSuggestionBarView.setBlurConfig(ChromePolicy.dockBlurEnabled(blurRadiusDp), blurRadiusDp);
        mSuggestionBarView.setInheritedTintColor(resolveAccessoryGlassBaseColor());
        int maxButtons = mPreferences.getAppLauncherButtonCount();
        if (maxButtons <= 0) {
            maxButtons = calculateSuggestionBarMaxButtons(getResources().getDisplayMetrics());
        }
        if (mLastStyledDockButtonCount != Integer.MIN_VALUE
            && maxButtons != mLastStyledDockButtonCount) {
            mLastStyledDockButtonCount = maxButtons;
            mSuggestionBarView.setMaxButtonCount(maxButtons);
            mSuggestionBarView.reloadAllApps();
        }
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
        mLastStyledDockButtonCount = maxButtons;
        mSuggestionBarView.setDefaultButtons(new ArrayList<>());
        mSuggestionBarView.setTextSize(10f);
        mSuggestionBarView.setBandW(mPreferences.isAppLauncherBwIconsEnabled());
        applySuggestionBarSurfaceStyling();
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

        populateRawBounds(mAzScrubRowView, mAzRowRawBounds);
        populateRawBounds(mSuggestionBarView, mAppsRowRawBounds);
        populateRawBounds(mAzTerminalToolbarView, mExtraKeysRawBounds);
        AzScrubGesture.Geometry geometry = azGestureGeometry();

        AzScrubGesture.Decision decision;
        switch (phase) {
            case DOWN:
                decision = mAzGesture.onDown(letter, selectionIndex, touchX, touchY, rawX, rawY,
                    eventTimeMs, geometry);
                break;
            case UP:
                decision = mAzGesture.onUp(letter, selectionIndex, touchX, touchY, rawX, rawY,
                    eventTimeMs, geometry);
                break;
            case MOVE:
            default:
                decision = mAzGesture.onMove(letter, selectionIndex, touchX, touchY, rawX, rawY,
                    eventTimeMs, geometry);
                break;
        }

        if (decision.pinnedSymbolReset) {
            mSuggestionBarView.clearAzFocusedEntry();
            mSuggestionBarView.clearAzPreview();
            resetAzGestureState(false, false);
            updateAzOverflowAffordance();
            return;
        }

        cancelAzOverflowRefresh();
        if (decision.track != null) {
            mAzScrubRowView.setInteractionMode(decision.track == AzScrubGesture.Track.WAVE
                ? AzScrubRowView.InteractionMode.WAVE_TRACK
                : AzScrubRowView.InteractionMode.INLINE_EMPHASIS_TRACK);
        }
        if (decision.applyLockedInline) {
            Character inlineLetter =
                decision.lockedInlineLetter == AzScrubGesture.Decision.NO_INLINE_LETTER
                    ? null
                    : Character.valueOf(decision.lockedInlineLetter);
            mAzScrubRowView.setLockedInlineLetter(inlineLetter);
        }
        if (decision.clearFocusedEntry) {
            mSuggestionBarView.clearAzFocusedEntry();
        }
        if (decision.persistPreview) {
            mSuggestionBarView.persistAzPreview(decision.previewLetter, decision.previewSelectionIndex);
        }
        updateAzOverflowAffordance();

        SuggestionBarView.AzDragFocusResult focusResult = decision.requestFocusResolve
            ? mSuggestionBarView.resolveAzDragFocus(rawX, rawY)
            : null;
        mAzCurrentFocusResult = focusResult;
        updateAzOverlayState(focusResult, decision.overlayLetter);
        updateAzEdgePagingLoop(focusResult);

        if (decision.releasing) {
            boolean launched = false;
            if (decision.mode == AzScrubGesture.Mode.ICON_TRACKING_LOCKED
                && focusResult != null && focusResult.hasFocusEntry()) {
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

    /**
     * The layout the scrub is judged against. The row rectangles are the {@code isShown()}-gated
     * ones already populated for the FX layers; the letter row's own position and height are read
     * ungated, because that is what the anchor arithmetic and the row-height thresholds used before
     * the gesture moved out of here.
     */
    @NonNull
    private AzScrubGesture.Geometry azGestureGeometry() {
        float azRowLeftRaw = 0f;
        float azRowTopRaw = 0f;
        float azRowHeightPx = 0f;
        if (mAzScrubRowView != null) {
            mAzScrubRowView.getLocationOnScreen(mAzViewLocation);
            azRowLeftRaw = mAzViewLocation[0];
            azRowTopRaw = mAzViewLocation[1];
            azRowHeightPx = mAzScrubRowView.getHeight();
        }
        float extraKeysHeightPx = (mAzTerminalToolbarView != null && mAzTerminalToolbarView.getHeight() > 0)
            ? mAzTerminalToolbarView.getHeight()
            : 0f;
        return new AzScrubGesture.Geometry(azRowLeftRaw, azRowTopRaw, azRowHeightPx, extraKeysHeightPx,
            toAzBounds(mAzRowRawBounds), toAzBounds(mAppsRowRawBounds), toAzBounds(mExtraKeysRawBounds),
            getResources().getDisplayMetrics().density);
    }

    @NonNull
    private static AzScrubGesture.Bounds toAzBounds(@NonNull RectF bounds) {
        return bounds.isEmpty()
            ? AzScrubGesture.Bounds.EMPTY
            : new AzScrubGesture.Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    @NonNull
    private static AzScrubGesture.Edge toAzEdge(int edge) {
        if (edge == SuggestionBarView.AZ_EDGE_LEFT) return AzScrubGesture.Edge.LEFT;
        if (edge == SuggestionBarView.AZ_EDGE_RIGHT) return AzScrubGesture.Edge.RIGHT;
        return AzScrubGesture.Edge.NONE;
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
            mAzGesture.mode() == AzScrubGesture.Mode.ICON_TRACKING_LOCKED
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
            mAzGesture.isActive(),
            mAzGesture.lastRawX(),
            focusBounds,
            interactionMode
        );
        Drawable focusedIcon = interactionMode == LauncherAzGestureFxView.InteractionMode.ICON_TRACK_LOCKED
            && focusResult != null
            && focusResult.entry != null
            ? com.termux.app.launcher.data.LauncherAppDataProvider.artworkFor(this, focusResult.entry)
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
        } else if (!mAzGesture.isActive() && !mSuggestionBarInteractionActive && mSuggestionBarView.hasPinnedOverflowPages()) {
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
        if (!isAzRowEnabled() || focusResult == null || mSuggestionBarView == null) {
            stopAzEdgePagingLoop();
            return;
        }
        AzScrubGesture.EdgeIntake intake =
            mAzGesture.onEdgeFocus(toAzEdge(focusResult.edge), mAzEdgePagingFrameCallback != null);
        switch (intake.action) {
            case STOP:
                stopAzEdgePagingLoop();
                return;
            case SUPPRESS:
            case CONTINUE:
                applyAzFxEdgeDwellProgress(intake.dwellProgress, mAzGesture.lastRawX(), mAzGesture.lastRawY());
                return;
            case START:
            default:
                break;
        }
        // The machine has already dropped the edge it was dwelling on; the frame callback is the
        // only half of the old loop this activity still owns.
        cancelAzEdgePagingFrameCallback();
        applyAzFxEdgeDwellProgress(intake.dwellProgress, mAzGesture.lastRawX(), mAzGesture.lastRawY());
        mAzEdgePagingFrameCallback = frameTimeNanos -> {
                if (!mAzGesture.isActive() || mSuggestionBarView == null) {
                    stopAzEdgePagingLoop();
                    return;
                }
                SuggestionBarView.AzDragFocusResult fresh =
                    mSuggestionBarView.resolveAzDragFocus(mAzGesture.lastRawX(), mAzGesture.lastRawY());
                AzScrubGesture.EdgeFrame frame = mAzGesture.onEdgeFrame(toAzEdge(fresh.edge));
                if (frame.action == AzScrubGesture.FrameAction.REFOCUS) {
                    mAzCurrentFocusResult = fresh;
                    updateAzOverlayState(fresh, mAzGesture.lockedLetter());
                    updateAzEdgePagingLoop(fresh);
                    return;
                }
                applyAzFxEdgeDwellProgress(frame.dwellProgress, mAzGesture.lastRawX(), mAzGesture.lastRawY());
                if (frame.action == AzScrubGesture.FrameAction.WAIT) {
                    postNextAzEdgePagingFrame();
                    return;
                }
                boolean changed = mSuggestionBarView.requestAzPageDelta(frame.pageDelta, 640f);
                if (changed) {
                    if (mLauncherAzGestureFxLabelOverlayView != null) {
                        mLauncherAzGestureFxLabelOverlayView.playFocusedAppPreviewSettle();
                    }
                    updateAzOverflowAffordance();
                }
                mAzEdgePagingFrameCallback = null;
                applyAzFxEdgeDwellProgress(0f, mAzGesture.lastRawX(), mAzGesture.lastRawY());
                mAzGestureHandler.postDelayed(() -> {
                    if (!mAzGesture.isActive() || mSuggestionBarView == null) return;
                    SuggestionBarView.AzDragFocusResult afterSwitch =
                        mSuggestionBarView.resolveAzDragFocus(mAzGesture.lastRawX(), mAzGesture.lastRawY());
                    mAzCurrentFocusResult = afterSwitch;
                    updateAzOverlayState(afterSwitch, mAzGesture.lockedLetter());
                    updateAzEdgePagingLoop(afterSwitch);
                }, AzScrubGesture.EDGE_PAGE_REPEAT_INTERVAL_MS);
        };
        postNextAzEdgePagingFrame();
    }

    private void postNextAzEdgePagingFrame() {
        if (mAzEdgePagingFrameCallback != null) {
            Choreographer.getInstance().postFrameCallback(mAzEdgePagingFrameCallback);
        }
    }

    private void cancelAzEdgePagingFrameCallback() {
        if (mAzEdgePagingFrameCallback != null) {
            Choreographer.getInstance().removeFrameCallback(mAzEdgePagingFrameCallback);
            mAzEdgePagingFrameCallback = null;
        }
    }

    private void stopAzEdgePagingLoop() {
        cancelAzEdgePagingFrameCallback();
        mAzGesture.stopEdgePaging();
        applyAzFxEdgeDwellProgress(0f, mAzGesture.lastRawX(), mAzGesture.lastRawY());
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
        mAzGestureHandler.postDelayed(mAzOverflowRefreshRunnable, AzScrubGesture.PREVIEW_TIMEOUT_REFRESH_MS);
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
        mAzGesture.reset();
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

    private void refreshPrivilegedBackendIfNeeded() {
        if (mPreferences == null) {
            return;
        }
        String method = mPreferences.getAppLauncherAzLockMethod();
        if (TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_AZ_LOCK_METHOD_SHIZUKU.equals(method)) {
            // A-Z lock needs Shizuku specifically, so it keeps the shizuku-only mode rather than
            // letting the manager settle on su/rish.
            PrivilegedBackendManager.getInstance().initializeShizukuOnly(this)
                .exceptionally(throwable -> {
                    Logger.logWarn(LOG_TAG, "A-Z Shizuku backend refresh failed: " + throwable.getMessage());
                    return false;
                });
            return;
        }
        // Everything else that needs privileges (the CPU card's ticks and process list, the
        // foreground-window resolver) only ever *reads* isPrivilegedAvailable(), so without this
        // the backend stayed UNINITIALIZED on every start unless the A-Z lock happened to be set
        // to Shizuku or the user opened the privileged-access settings screen and pressed Connect.
        // Initializing here also registers the Shizuku binder-received listener, which is what
        // lets a later Shizuku start recover on its own.
        PrivilegedBackendManager.getInstance().initializeIfNeeded(this)
            .exceptionally(throwable -> {
                Logger.logWarn(LOG_TAG, "Privileged backend refresh failed: " + throwable.getMessage());
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
                    AppNotice.show(this, R.string.termux_app_launcher_set_home_unavailable, false);
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
        mTermuxTerminalSessionActivityClient = createTermuxTerminalSessionClient();
        mTermuxTerminalViewClient = createTermuxTerminalViewClient(mTermuxTerminalSessionActivityClient);
        mTermuxTerminalViewClient.setSuggestionBarCallback(this);
        // Split panes: the controller owns the TerminalViews (one per pane leaf) and inflates
        // them into terminal_pane_host. mTerminalView / mActivePane are repointed to the focused
        // pane via PaneHost.onActivePaneChanged, so the single-view call sites act on it.
        android.widget.FrameLayout paneHost = findViewById(R.id.terminal_pane_host);
        mPaneController = new com.termux.app.terminal.TerminalPaneController(
            new PaneHost(), paneHost, getLayoutInflater());
        mPaneController.setSurfaceStyle(paneSurfaceStyle());
        applyPaneBehaviourPreferences();
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
    void showExtraKeysRowEditor() {
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

    private void handleSurfaceEditorIntent(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_SURFACE_EDITOR, false))
            return;
        String initialSection = intent.getStringExtra(EXTRA_SURFACE_EDITOR_SECTION);
        intent.removeExtra(EXTRA_SURFACE_EDITOR);
        intent.removeExtra(EXTRA_SURFACE_EDITOR_SECTION);
        mSurfaceEditor.enter(initialSection);
    }

    /** The activity's half of the surface editor's seam: its views, its prefs, its render pipeline. */
    private final class SurfaceEditorHost implements SurfaceEditorController.Host {
        @NonNull @Override public Context context() {
            return TermuxActivity.this;
        }

        @Nullable @Override public <T extends View> T findView(int viewId) {
            return findViewById(viewId);
        }

        @Nullable @Override public TermuxAppSharedPreferences preferences() {
            return mPreferences;
        }

        @Nullable @Override public com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard inAppKeyboard() {
            return mInAppKeyboard;
        }

        @Nullable @Override public View attachedInAppKeyboardView() {
            return mAttachedInAppKeyboardView;
        }

        @Override public boolean isInAppKeyboardShown() {
            return TermuxActivity.this.isInAppKeyboardShown();
        }

        @Override public boolean isFloatingDock() {
            return TermuxActivity.this.isRoundedDockStyle();
        }

        @Override public boolean isFullStatusBarEngaged() {
            return TermuxActivity.this.isFullStatusBarEngaged();
        }

        @Override public void setTopStatusBarCollapsed(boolean collapsed, boolean animate) {
            TermuxActivity.this.setTopStatusBarCollapsed(collapsed, animate);
        }

        @Override public int statusBarInsetTop() {
            return mLastStatusBarInsetTop;
        }

        @Override public int themeColor(int attr, int fallbackRes) {
            return getTermuxThemeColor(attr, fallbackRes);
        }

        @Override public void refreshPaneLayout() {
            if (mPaneController != null) mPaneController.refreshPaneLayout();
        }

        @Override public void applyTerminalSurfaceAppearance() {
            TermuxActivity.this.applyTerminalSurfaceAppearance();
        }

        @Override public void refreshTerminalWindowBar() {
            TermuxActivity.this.refreshTerminalWindowBar();
        }

        @Override public void applySessionsSurfaceBackground() {
            if (mPreferences == null) return;
            configureBackgroundBlur(R.id.sessions_backgroundblur, R.id.sessions_background, false,
                mPreferences.getSessionsOpacity() / 100f, 0);
        }

        @Override public void applyGeometryPreview(boolean commit) {
            updateAppLauncherBarHeight();
            // Without commit the dock/keyboard visuals still track the drag live; only the
            // terminal resize (a SIGWINCH into the shell per reflow) waits for the release.
            setTerminalToolbarHeight(commit);
            mChrome.requestSync(ChromeRenderer.SCOPE_APPLY_NOW);
        }

        @Override public void applyGlassPreview(boolean blurChanged) {
            // Only a radius control may throw away the shared pre-blurred wallpaper frames; every
            // other slider re-renders on top of them.
            mChrome.requestSync((blurChanged ? ChromeRenderer.SCOPE_WALLPAPER_BLUR_CACHE : 0)
                | ChromeRenderer.SCOPE_BACKDROPS | ChromeRenderer.SCOPE_KEYBOARD_BACKDROP);
            // Styling only: this runs once per frame of a drag, and the full preference apply
            // rebuilds the dock's whole app row.
            applySuggestionBarSurfaceStyling();
            mChrome.requestSync(ChromeRenderer.SCOPE_APPLY_NOW | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        }

        @Override public void openKeyboardColors() {
            startActivity(SettingsActivity.createFragmentIntent(TermuxActivity.this,
                KeyboardColorSchemeFragment.class, R.string.settings_keyboard_colors_title));
        }

        @Override @Nullable public Bitmap wallpaperPreviewThumb(int widthPx, int heightPx) {
            if (mPreferences == null || widthPx <= 0 || heightPx <= 0)
                return null;
            View wallpaperFrame = findViewById(R.id.activity_termux_root_view);
            if (wallpaperFrame == null || wallpaperFrame.getWidth() <= 0)
                return null;
            // The dock's radius is the frame most likely already resident in the LRU; clamp off 0
            // so a blur-less look still gets a soft thumb rather than a full-res wallpaper copy.
            int radiusDp = Math.max(1, mPreferences.getExtraKeysBlurRadius());
            Bitmap frame = mChrome.blurCache().obtain(radiusDp, wallpaperFrame);
            if (frame == null || frame.isRecycled())
                return null;
            Bitmap thumb = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(thumb);
            float scale = Math.max(widthPx / (float) frame.getWidth(),
                heightPx / (float) frame.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate((widthPx - frame.getWidth() * scale) / 2f,
                (heightPx - frame.getHeight() * scale) / 2f);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(frame, matrix, paint);
            return thumb;
        }

        @Override @NonNull public Drawable presetGlassSurface(float barAlpha, int grainPercent,
                                                              float cornerRadiusPx,
                                                              boolean withRim) {
            return mChrome.glass().surface(barAlpha, 0f, 1f, true, grainPercent, cornerRadiusPx,
                withRim);
        }
    }

    /**
     * Whether the keyboard's background opacity still follows Base. That is the question the old
     * all-or-nothing "match all surfaces" switch was standing in for here: a keyboard on the shared
     * value is the dock's material, and only a detached one paints its own.
     */
    private boolean isInAppKeyboardOpacityLinked() {
        return mPreferences != null && mPreferences.isSurfaceInheriting(
            TermuxAppSharedPreferences.SurfaceSlot.KEYBOARD,
            TermuxAppSharedPreferences.SurfaceProperty.OPACITY);
    }

    // ------------------------------------------------------------------ keybind hint popup

    /**
     * The keybind hint surfaces — lit caps on the in-app keyboard, the strip in the A-Z row's slot,
     * the full grouped table behind {@code ?} — all live in
     * {@link com.termux.app.terminal.keybind.KeybindHintPresenter}. The activity only lends it its
     * view tree, its dress, this card host and a place to post delayed work.
     */
    private final com.termux.app.terminal.keybind.KeybindHintPresenter mKeybindHintPresenter =
        new com.termux.app.terminal.keybind.KeybindHintPresenter(
            new KeybindHintSurface(),
            new com.termux.app.terminal.keybind.KeybindHintPresenter.Scheduler() {
                @Override
                public void postDelayed(@NonNull Runnable runnable, long delayMs) {
                    getWindow().getDecorView().postDelayed(runnable, delayMs);
                }

                @Override
                public void remove(@NonNull Runnable runnable) {
                    getWindow().getDecorView().removeCallbacks(runnable);
                }
            },
            com.termux.app.terminal.keybind.KeybindHintPresenter.resolverHints());

    /** The activity's half of the hint surfaces' seam: its view tree, its dress, its card host. */
    private final class KeybindHintSurface
            implements com.termux.app.terminal.keybind.KeybindHintPresenter.Surface {

        @NonNull
        @Override
        public Context context() {
            return TermuxActivity.this;
        }

        @Nullable
        @Override
        public View findView(int viewId) {
            return findViewById(viewId);
        }

        @Override
        public int accessoryGlassBaseColor() {
            return resolveAccessoryGlassBaseColor();
        }

        @Override
        public boolean isReducedMotionEnabled() {
            return TermuxActivity.this.isReducedMotionEnabled();
        }

        @Override
        public boolean isSplitPanesEnabled() {
            return TermuxActivity.this.isSplitPanesEnabled();
        }

        @Override
        public boolean isShowKeyHintsEnabled() {
            return mPreferences == null || mPreferences.isShowKeyHintsEnabled();
        }

        @Override
        public boolean isHintPanelShowing() {
            return mKeybindHintPanel != null && mKeybindHintPanel.isShowing();
        }

        @Override
        public void showHintPanel(@NonNull View content, boolean wide,
                                  @Nullable Runnable onOutsideTap) {
            com.termux.app.terminal.TerminalHintPanelView panel = obtainKeybindHintPanel();
            if (panel == null) return;
            syncTerminalHintPanelFrame(panel);
            mKeybindHintPanelOutsideTap = onOutsideTap;
            panel.show(content, wide);
        }

        @Override
        public void dismissHintPanel(boolean animated) {
            mKeybindHintPanelOutsideTap = null;
            if (mKeybindHintPanel != null) mKeybindHintPanel.hide(animated);
        }

        @Override
        public void setKeyboardHintHighlights(@Nullable Map<String, Integer> litTokens) {
            if (mInAppKeyboard != null)
                mInAppKeyboard.setKeybindHintHighlights(litTokens);
        }

        @Override
        public void setKeyboardHintPulse(@Nullable String token) {
            if (mInAppKeyboard != null)
                mInAppKeyboard.setKeybindHintPulse(token);
        }
    }

    @Nullable private com.termux.app.terminal.TerminalHintPanelView mKeybindHintPanel;
    /** Retires the sticky {@code ?} table on a tap anywhere else; null while the strip is up. */
    @Nullable private Runnable mKeybindHintPanelOutsideTap;

    @Nullable
    private com.termux.app.terminal.TerminalHintPanelView obtainKeybindHintPanel() {
        FrameLayout host = findViewById(R.id.terminal_surface_host);
        if (host == null)
            return null;
        if (mKeybindHintPanel == null)
            mKeybindHintPanel = new com.termux.app.terminal.TerminalHintPanelView(this);
        if (mKeybindHintPanel.getParent() == null) {
            host.addView(mKeybindHintPanel,
                com.termux.app.terminal.TerminalHintPanelView.buildHostLayoutParams());
        }
        mKeybindHintPanel.bringToFront();
        return mKeybindHintPanel;
    }

    /** The hints sit on the terminal's live edge, exactly as the mode legends do. */
    private void syncTerminalHintPanelFrame(
            @NonNull com.termux.app.terminal.TerminalHintPanelView panel) {
        float radiusPx = terminalEdgeCornerRadiusPx();
        panel.setTerminalFrame(terminalFrameInsetPx(false), terminalFrameInsetPx(true),
            terminalFrameInsetPx(true), radiusPx);
    }

    /**
     * A touch that misses the {@code ?} table retires it. The panel is passive — the prefix that
     * raised it is still being held and the terminal underneath must keep working — so the tap is
     * watched here rather than swallowed by a scrim.
     */
    private void notifyKeybindHintPanelTouch(@NonNull MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return;
        Runnable onOutsideTap = mKeybindHintPanelOutsideTap;
        if (onOutsideTap == null || mKeybindHintPanel == null) return;
        if (mKeybindHintPanel.containsScreenPoint(event.getRawX(), event.getRawY())) return;
        mKeybindHintPanelOutsideTap = null;
        onOutsideTap.run();
    }

    /**
     * Names a pressed extra key in the A-Z row's slot, the same surface the leader's hint strip
     * borrows. Called by {@link com.termux.app.terminal.io.TermuxTerminalExtraKeys}.
     */
    public void showExtraKeyPressReadout(@Nullable CharSequence label) {
        mKeybindHintPresenter.showExtraKeyPressReadout(label);
    }

    public boolean isInAppKeyboardEnabled() {
        return mInAppKeyboard != null && mInAppKeyboard.isEnabled();
    }

    /** The in-app keyboard's layout ring, in cycle order; empty while there is no keyboard. */
    @NonNull
    public java.util.List<String> inAppKeyboardLayoutRing() {
        return mInAppKeyboard == null
            ? java.util.Collections.emptyList() : mInAppKeyboard.getLayoutRing();
    }

    /** The layout the ring stands on, even while a modal pad is the one on screen. */
    @NonNull
    public String activeInAppKeyboardLayoutId() {
        return mInAppKeyboard == null
            ? com.termux.app.terminal.inappkeyboard.LauncherKeyboardLayouts.LAYOUT_MAIN
            : mInAppKeyboard.getActiveTextLayoutId();
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
            mKeyboardGeometry.onVisibilityRequested(visible);
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
            mKeyboardGeometry.discardMeasuredHeight();
        }

        @Override
        public void detachKeyboardView() {
            FrameLayout host = findViewById(R.id.inapp_keyboard_view_host);
            if (host != null)
                host.removeAllViews();
            mAttachedInAppKeyboardView = null;
            mKeyboardGeometry.discardMeasuredHeight();
        }

        @Override
        public void onKeyboardModifiersChanged(com.termux.app.terminal.inappkeyboard.TerminalModifiers modifiers) {
            mKeybindHintPresenter.onInAppModifiersChanged(modifiers);
        }

        @Override
        public void requestAccessoryGeometrySync() {
            mKeyboardGeometry.requestGeometrySync();
        }

        @Override
        public void requestAccessoryGeometryPreviewSync() {
            mKeyboardGeometry.requestPreviewGeometrySync();
        }

        @Override
        public void invalidateKeyboardMeasurement() {
            mKeyboardGeometry.invalidateMeasurementAndForceLayout();
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
            controls.setBackground(mChrome.glass().dockSurface(
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
        mChrome.requestSync(ChromeRenderer.SCOPE_APPLY_NOW);
        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);
        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
        mChrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);
    }

    private void updateAppLauncherBarHeight() {
        if (mPreferences == null)
            return;
        applyDockLayout(buildDockLayout(0));
    }

    private boolean isLandscapeOrientation() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

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
                    getDockLayout().railPull,
                    !mSurfaceEditor.isActive(),
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
        DockLayout dockLayout = getDockLayout();
        int railWidthPx = dockLayout.railWidthPx;
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
        int edgeMarginPx = Math.round(dpToPx(DockLayoutPolicy.DOCK_RAIL_EDGE_MARGIN_DP));
        int dockedEdgePadPx = dockLayout.railEdgeInsetPx + edgeMarginPx;
        railScroll.setPadding(isDockRailOnRight() ? edgeMarginPx : dockedEdgePadPx,
            mLastStatusBarInsetTop + verticalPadPx,
            isDockRailOnRight() ? dockedEdgePadPx : edgeMarginPx,
            mLastNavigationBarInsetBottom + verticalPadPx);
        railScroll.setClipToPadding(false);
        railList.removeAllViews();
        int iconSizePx = Math.round(dpToPx(DockLayoutPolicy.DOCK_RAIL_ICON_SIZE_DP));
        int spacingPx = Math.round(dpToPx(DockLayoutPolicy.DOCK_RAIL_ICON_SPACING_DP));
        for (com.termux.app.launcher.model.LauncherAppEntry entry
                : mSuggestionBarView.getDockRailEntries()) {
            Drawable railArtwork =
                com.termux.app.launcher.data.LauncherAppDataProvider.artworkFor(this, entry);
            if (railArtwork == null)
                continue;
            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(railArtwork);
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
        ChromeSpec state = buildChromeSpec();
        int toolbarHeightPx = state.extraKeysRowEnabled ? measuredToolbarHeightPx : 0;
        toolbarLayoutParams.height = toolbarHeightPx;
        terminalToolbarViewPager.setLayoutParams(toolbarLayoutParams);

        DockLayout dockMetrics = buildDockLayout(0);
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
            dockMetrics = buildDockLayout(-(projectedStackPx - maxAccessoryStackPx));
        }
        applyDockLayout(dockMetrics);
        int dockContentHeightPx = state.toolbarShown
            ? dockMetrics.combinedHeight(toolbarHeightPx, state.extraKeysRowEnabled) : 0;
        int accessoryContentHeightPx = computeAccessoryStackHeight(
            dockContentHeightPx, 0, state.keyboardHeight);
        // The embedded keyboard suspends flush absorption: its height is user-scaled and its
        // surface defines its own boundary, so the split remainder halves would surface as
        // wallpaper bands above the gesture-navigation inset instead of hiding in dock glass.
        // Tiled splits couple mTerminalView's window position to the very accessory geometry this
        // padding feeds back into (its pane reflows every time the stack resizes), so the modulo
        // calc below never settles — it just chases its own tail and flickers the bottom pane/dock.
        // Skip it whenever more than one pane is tiled, same as the keyboard-shown/toolbar-hidden
        // cases. A focused FLOATING pane is the same coupling with one hop more: the float's
        // fractional bounds re-project on every host resize, so mTerminalView's top moves with the
        // very stack height the modulo feeds — tap a float and the whole band flickered at layout
        // speed until focus left it.
        boolean activePaneFloating = mPaneController != null && mPaneController.isActivePaneFloating();
        // The system IME stands the absorption down too: the remainder halves only hide when the
        // band rests on the screen edge — above a keyboard they surface as a wallpaper band
        // between the extra-keys glass and the IME, read as a stray gap.
        int terminalFlushPaddingPx = state.keyboardShown || !state.toolbarShown
            || visiblePaneCount() > 1 || activePaneFloating || isImeVisible() ? 0
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
        boolean keyboardShownChanged = mKeyboardGeometry.applyKeyboardShown(state.keyboardShown);
        if (shouldRequestTerminalResize(requestTerminalResize, accessoryHeightChanged,
            accessoryMarginChanged, keyboardShownChanged) && mTerminalView != null) {
            // Bottom-anchored, like the window-bar collapse: the keyboard or dock changing height
            // must not strand a prompt that a shell placed against the bottom edge — growing the
            // pane with a plain resize pads the screen with blank rows below the cursor whenever
            // the transcript cannot fill them (fish's clear wipes it via ESC[3J).
            if (mPaneController != null) mPaneController.beginHostSurfaceResize();
            mTerminalView.post(mTerminalView::updateSize);
            accessoryStackContainer.post(() -> {
                if (mPaneController != null) mPaneController.finishHostSurfaceResizeKeepingBottom();
            });
        }
        mChrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);
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

    void requestTerminalFlushDockGeometryUpdate() {
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

    private int resolveAccessoryStackBottomMarginPx(@NonNull ChromeSpec state) {
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
        return mImeLiftPx + getDockLayout().capsuleBottomGapPx;
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
        DockLayout layout = getDockLayout();
        int surfaceInset = layout.horizontalInsetPx;
        int contentInset = layout.capsule ? layout.capsuleContentInsetPx : surfaceInset;
        int extraKeysInset = layout.capsule ? layout.capsuleExtraKeysInsetPx : surfaceInset;
        int appsTopPadding = layout.appsTopPaddingPx;
        int appsBottomPadding = layout.appsBottomPaddingPx;
        // The apps row reads with more side padding than the A–Z row because its icons are
        // space-between (half a slot of empty space at each edge). Trim the apps-row inset ~18%
        // so the icons sit closer to the edges and line up better with the A–Z row's letter span.
        int appsContentInset = Math.round(contentInset * 0.82f);

        // The find strip is dock furniture: it spans exactly the dock's own width, whichever style
        // is set, so it reads as the bar's top edge rather than as a floating panel.
        updateViewHorizontalMargins(R.id.terminal_find_bar_host, surfaceInset);
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

    private int getDockBaseToolbarHeightPx() {
        if (mTerminalToolbarDefaultHeight > 0) {
            return Math.round(mTerminalToolbarDefaultHeight);
        }
        return Math.round(getResources().getDisplayMetrics().density * 37.5f);
    }

    /**
     * Snapshots every preference/resource value the dock's geometry is a function of, so the sizing
     * itself stays in the pure {@link DockLayoutPolicy}.
     */
    @NonNull
    private DockLayoutPolicy.DockInputs buildDockInputs(int additionalAppsBarHeightPx) {
        boolean preferencesAvailable = mPreferences != null;
        return DockLayoutPolicy.DockInputs.builder()
            .preferencesAvailable(preferencesAvailable)
            .capsule(isRoundedDockStyle())
            .landscape(isLandscapeOrientation())
            .density(getResources().getDisplayMetrics().density)
            .barHeightScale(preferencesAvailable
                ? mPreferences.getAppLauncherBarHeightScale() : DockLayoutPolicy.sizePreset(2))
            .dockHorizontalInsetDp(preferencesAvailable
                ? mPreferences.getDockHorizontalInset()
                : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET)
            .configuredCornerRadiusDp(preferencesAvailable
                ? mPreferences.getAppLauncherDockCornerRadius()
                : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS)
            .appsRowEnabledPref(preferencesAvailable && mPreferences.isAppLauncherAppsRowEnabled())
            .azRowEnabledPref(preferencesAvailable && mPreferences.isAppLauncherAzRowEnabled())
            .baseToolbarHeightPx(getDockBaseToolbarHeightPx())
            .additionalAppsBarHeightPx(additionalAppsBarHeightPx)
            .railOnRight(preferencesAvailable && mPreferences.isAppLauncherDockRailOnRight())
            .displayCutoutInsetLeftPx(mLastDisplayCutoutInsetLeft)
            .displayCutoutInsetRightPx(mLastDisplayCutoutInsetRight)
            .build();
    }

    /**
     * The dock's current geometry. Resolved fresh per call, exactly as the old per-number resolvers
     * were, and read by the app drawer's choreography through this same seam.
     */
    @NonNull
    private DockLayout getDockLayout() {
        return DockLayoutPolicy.compute(buildDockInputs(0));
    }

    @NonNull
    private DockLayout buildDockLayout(int additionalAppsBarHeightPx) {
        return DockLayoutPolicy.compute(buildDockInputs(additionalAppsBarHeightPx));
    }

    private void applyDockLayout(@NonNull DockLayout layout) {
        updateViewHeight(R.id.apps_bar_viewpager, layout.appsBarHeightPx);
        updateViewHeight(R.id.apps_bar_indicator_band, layout.indicatorBandHeightPx);
        updateViewHeight(R.id.apps_bar_az_row, layout.azRowHeightPx);
        updateViewBottomMargin(R.id.apps_bar_viewpager, 0);
        applyDockRowHorizontalInsets();
        if (mSuggestionBarView != null) {
            mSuggestionBarView.setDockRowHeightHintPx(layout.appsBarHeightHintPx);
        }
    }

    public void toggleTerminalToolbar() {
        boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar), true);

        mChrome.requestSync(ChromeRenderer.SCOPE_APPLY_NOW);
        mChrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);

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
                    mChrome.requestSync(ChromeRenderer.SCOPE_BACKDROPS | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
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

        ChromePolicy.applyWallpaperModePreferences(preferences, enabled);
        requestTermuxActivityStylingOnNextResume(context, true);
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
            case CONTEXT_MENU_SURFACE_EDITOR_ID:
                mSurfaceEditor.enter();
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
     * An Intent that opens Termux:Styling, or {@code null} when the companion is not installed.
     *
     * <p>Asking at all needs the {@code <queries>} entry for the styling package: under Android 11
     * package visibility a {@code getPackageInfo} for a package this app does not declare answers
     * "not installed" for a plugin that is sitting right there. The shared user id covers the
     * stock plugins already, and the manifest names the package so a build without one still gets
     * a true answer.
     *
     * <p>The documented activity is tried first and the package's own launcher activity second,
     * because the plugin has renamed that class across releases and a user with an older or newer
     * build than this constant should still get the row rather than a menu that quietly drops it.
     */
    @Nullable
    static Intent resolveTerminalStylingIntent(@NonNull PackageManager packageManager) {
        String stylingPackage = TermuxConstants.TERMUX_STYLING_PACKAGE_NAME;
        String stylingActivity = TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME;
        try {
            packageManager.getActivityInfo(new ComponentName(stylingPackage, stylingActivity), 0);
            Intent stylingIntent = new Intent();
            stylingIntent.setClassName(stylingPackage, stylingActivity);
            return stylingIntent;
        } catch (Exception ignored) {
            // Falls through to the launcher activity below.
        }
        try {
            return packageManager.getLaunchIntentForPackage(stylingPackage);
        } catch (Exception exception) {
            Logger.logVerbose(LOG_TAG, "Termux:Styling is not installed: " + exception.getMessage());
            return null;
        }
    }

    /** Whether the Style row belongs in the terminal long-press menu on this device. */
    public boolean isTerminalStylingAvailable() {
        return resolveTerminalStylingIntent(getPackageManager()) != null;
    }

    /**
     * Termux:Styling if it is there, Appearance settings if it is not.
     *
     * <p>The fallback stays even though the row is now gated on the companion being installed: the
     * package can be uninstalled while this menu is open, and the launch is the only check that
     * cannot go stale between asking and starting.
     */
    private void openTerminalStyling() {
        Intent stylingIntent = resolveTerminalStylingIntent(getPackageManager());
        if (stylingIntent == null) {
            openLookAndFeelSettings();
            return;
        }
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
    boolean showTerminalActionSheet() {
        return showTerminalActionSheet(null);
    }

    /**
     * @param anchor accepted for the callers that pass one, but the dialog centres itself: this is
     *               the pre-sheet menu, restored at the owner's request.
     */
    boolean showTerminalActionSheet(@Nullable android.graphics.PointF anchor) {
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
        items.add(new TerminalActionItem(CONTEXT_MENU_SURFACE_EDITOR_ID, getString(R.string.action_surface_editor)));
        // Only when the companion is installed: a row that opens the Appearance settings under the
        // name of a plugin the device does not have is a broken promise, not a shortcut.
        if (isTerminalStylingAvailable()) {
            items.add(new TerminalActionItem(CONTEXT_MENU_STYLE_ID, getString(R.string.action_style_terminal)));
        }
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
            mTerminalSheet = new com.termux.app.terminal.TerminalSheetController(
                new TerminalSheetHost());
        return mTerminalSheet;
    }

    /** The activity's half of the sheet plane's seam. */
    private final class TerminalSheetHost implements com.termux.app.terminal.TerminalSheetController.Host {
        @NonNull @Override public Context context() {
            return TermuxActivity.this;
        }

        @Nullable @Override public <T extends View> T findView(int viewId) {
            return findViewById(viewId);
        }

        @Override public void yieldCompetingPlanes() {
            closeFullStatusBarImmediate();
            // Guarded on the open check rather than reached through the lazy accessor, so a session
            // that never pulls the drawer down does not build one just because it opened a prompt.
            if (isAppDrawerOpen()) mAppDrawerController.closeImmediate();
        }

        @Override public void ensureInAppTypingKeyboard() {
            TermuxActivity.this.ensureInAppTypingKeyboard();
        }

        @Override public void setSheetInterceptorActive(boolean active) {
            setTerminalSheetInterceptorActive(active);
        }

        @Override public boolean isPointOnInAppKeyboard(float rawX, float rawY) {
            return TermuxActivity.this.isPointOnInAppKeyboard(rawX, rawY);
        }

        @Override public boolean applyWallpaperFrost(@NonNull ImageView frost) {
            return applyCommandPaletteWallpaperFrost(frost);
        }

        @NonNull @Override public Drawable sheetSurface() {
            return buildTerminalSheetSurface();
        }

        @Override public boolean dockBoundsOnScreen(@NonNull Rect out) {
            return TermuxActivity.this.dockBoundsOnScreen(out);
        }

        @Override public boolean isReducedMotionEnabled() {
            return TermuxActivity.this.isReducedMotionEnabled();
        }
    }

    /** Guarded on the field, not the lazy accessor: asking must not build a plane. */
    private boolean isTerminalSheetOpen() {
        return mTerminalSheet != null && mTerminalSheet.isOpen();
    }

    /**
     * Glass for a sheet card. Same builder, tint and rim as the dock and the rename chip, so the
     * plane reads as the same kit rather than as a Material dialog that lost its window.
     */
    @NonNull
    private Drawable buildTerminalSheetSurface() {
        float barAlpha = mPreferences != null ? mPreferences.getAppBarOpacity() / 100f : 0.5f;
        int grain = mPreferences != null
            ? mPreferences.getDockGlassGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN;
        // Floored well above the dock's opacity: a sheet has body text over a live blur, and at the
        // dock's own tint the terminal behind it reads straight through the words.
        return mChrome.glass().surface(Math.max(0.92f, barAlpha), 0f, 1f, false, grain,
            dpToPx(com.termux.app.terminal.TerminalSheetController.cornerRadiusDp()), true);
    }

    /**
     * Makes a keyboard available for typing into a focusless surface, and reports whether one is.
     *
     * <p>False means there is nothing on screen to aim the key channel at — the caller either does
     * without typing or falls back to a focused editor.
     */
    private boolean ensureInAppTypingKeyboard() {
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
    private boolean isPointOnInAppKeyboard(float rawX, float rawY) {
        if (mInAppKeyboard == null) return false;
        Rect keyboard = new Rect();
        return mInAppKeyboard.getKeyboardRectOnScreen(keyboard)
            && keyboard.contains(Math.round(rawX), Math.round(rawY));
    }

    /**
     * The app drawer plane, created on first use. Like the palette it lives in the activity rather
     * than in a window of its own, and binds its views lazily, so an install that never pulls the
     * drawer down never pays for it.
     */
    @NonNull
    public com.termux.app.launcher.drawer.AppDrawerController getAppDrawerController() {
        if (mAppDrawerController == null) {
            mAppDrawerController = new com.termux.app.launcher.drawer.AppDrawerController(
                new AppDrawerHost());
            // Registered here rather than in setSuggestionBarView() so the accessor stays lazy: the
            // only thing that builds a controller is a drag, and a drag comes from the row itself,
            // which therefore already exists by the time this runs.
            mAppDrawerController.setDockChoreographyTarget(mSuggestionBarView);
        }
        return mAppDrawerController;
    }

    /** The activity's half of the drawer plane's seam. */
    private final class AppDrawerHost implements com.termux.app.launcher.drawer.AppDrawerController.Host {
        @NonNull @Override public Context context() {
            return TermuxActivity.this;
        }

        @Nullable @Override public <T extends View> T findView(int viewId) {
            return findViewById(viewId);
        }

        @Nullable @Override public TermuxAppSharedPreferences preferences() {
            return getPreferences();
        }

        @NonNull @Override public DockLayout dockLayout() {
            return getDockLayout();
        }

        @Nullable @Override public SuggestionBarView suggestionBar() {
            return mSuggestionBarView;
        }

        @Override public boolean applyWallpaperFrost(@NonNull ImageView frost) {
            return mChrome.frost().applyAppDrawer(frost);
        }

        @Override public void flushPendingAccessoryGeometry() {
            TermuxActivity.this.flushPendingAccessoryGeometry();
        }

        @Override public void setInterceptorActive(boolean active) {
            setAppDrawerInterceptorActive(active);
        }

        @Override public void hideSystemKeyboard() {
            if (!isImeVisible() || getWindow() == null) return;
            mSystemImeHiddenForDrawer = true;
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .hide(Type.ime());
        }

        @Override public void restoreSystemKeyboard() {
            if (!mSystemImeHiddenForDrawer) return;
            mSystemImeHiddenForDrawer = false;
            if (getWindow() == null || isSystemImeSuppressedByInAppKeyboard()) return;
            // The keyboard was up when the drawer took the screen; the user's context is typing,
            // so the dismissal is undone rather than left as a surprise.
            onSystemImeRequested();
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .show(Type.ime());
        }

        @Override public void requestSearchKeyboard() {
            requestAppDrawerSearchKeyboard();
        }
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
     * <p>{@code AppDrawerController} reaches it from the {@code finally} of its own teardown: the
     * flush must run even if restoring the plane's transforms throws.
     */
    private void flushPendingAccessoryGeometry() {
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
    private void setTerminalSheetInterceptorActive(boolean active) {
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
    @VisibleForTesting
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

    private boolean isFolderRenameActive() { return mFolderRenameController.isActive(); }

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
    @VisibleForTesting
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
    boolean beginTerminalRename(
            @NonNull com.termux.app.terminal.rename.TerminalRenameTarget target) {
        if (target == com.termux.app.terminal.rename.TerminalRenameTarget.WINDOW && !isSplitPanesEnabled())
            return false;
        if (target == com.termux.app.terminal.rename.TerminalRenameTarget.SESSION
            && (mCurrentWSession == null || !isSplitPanesEnabled())) return false;
        if (target == com.termux.app.terminal.rename.TerminalRenameTarget.PANE
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
        return beginTerminalRename(com.termux.app.terminal.rename.TerminalRenameTarget.SESSION);
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
        return beginTerminalRename(com.termux.app.terminal.rename.TerminalRenameTarget.SESSION);
    }

    public boolean isTerminalRenameActive() {
        return mRenameCoordinator != null && mRenameCoordinator.isActive();
    }

    // ------------------------------------------------------------------ scrollback find strip

    @NonNull
    private com.termux.app.terminal.find.TerminalFindCoordinator getFindCoordinator() {
        if (mFindCoordinator == null)
            mFindCoordinator = new com.termux.app.terminal.find.TerminalFindCoordinator(
                new TerminalFindHost());
        return mFindCoordinator;
    }

    /** Opens the find strip on the focused pane, or the compact fallback with no keyboard to type. */
    public boolean beginScrollbackFind() {
        return getFindCoordinator().begin(getTerminalView());
    }

    public boolean isScrollbackFindActive() {
        return mFindCoordinator != null && mFindCoordinator.isActive();
    }

    public void cancelScrollbackFind() {
        if (mFindCoordinator != null) mFindCoordinator.cancel();
    }

    /** Hardware and external-keyboard strokes claimed by an open find session. */
    private boolean handleScrollbackFindKey(int keyCode, @NonNull KeyEvent event) {
        return mFindCoordinator != null && mFindCoordinator.handleKeyDown(keyCode, event);
    }

    /** System-IME committed text claimed by an open find session. */
    private boolean handleScrollbackFindCodePoint(int codePoint, boolean ctrlDown) {
        return mFindCoordinator != null && mFindCoordinator.handleCodePoint(codePoint, ctrlDown);
    }

    /** Activity half of the find strip: where it hangs, what it is drawn in, and its keyboard. */
    private final class TerminalFindHost
            implements com.termux.app.terminal.find.TerminalFindCoordinator.Host {

        @Nullable
        @Override
        public ViewGroup findBarHost() {
            return findViewById(R.id.terminal_find_bar_host);
        }

        @Nullable
        @Override
        public TerminalView terminalView() {
            return getTerminalView();
        }

        @NonNull
        @Override
        public Drawable barBackground() {
            float barAlpha = mPreferences != null ? mPreferences.getAppBarOpacity() / 100f : 0.5f;
            // The keybind hint slab's surface, but a good deal thinner. That slab is read instead of
            // what is behind it; this strip is read alongside the transcript it is searching, and the
            // row of matches directly above it has to stay legible through the glass.
            return mChrome.glass().dockSurface(Math.min(0.45f, barAlpha * 0.6f), 0f, 1f, false);
        }

        @NonNull
        @Override
        public int[] barColors() {
            return new int[] {
                getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurface,
                    R.color.termux_on_surface),
                getTermuxThemeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
                    R.color.termux_on_surface_variant),
                getTermuxThemeColor(com.termux.shared.R.attr.termuxColorPrimary,
                    R.color.termux_primary),
            };
        }

        @Override
        public boolean ensureTypingKeyboard() {
            return ensureInAppTypingKeyboard();
        }

        @Override
        public void installFindInterceptor(
                @Nullable com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler
                    .KeyValueInterceptor interceptor) {
            if (mInAppKeyboard == null) return;
            if (interceptor != null) {
                mInAppKeyboard.setKeyValueInterceptor(interceptor);
            } else {
                setAppDrawerInterceptorActive(isAppDrawerOpen());
            }
        }

        @Override
        public void showFallbackSearch() {
            TermuxTerminalViewClient client = getTermuxTerminalViewClient();
            if (client != null) client.showScrollbackSearchFallback();
        }

        @Override
        public void showModeHint(
                @Nullable com.termux.app.terminal.TerminalModeHintCard.Mode mode) {
            if (mode == null) TermuxActivity.this.hideTerminalModeHint();
            else TermuxActivity.this.showTerminalModeHint(mode);
        }

        @Override
        public void copyToClipboard(@NonNull String text) {
            android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null)
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", text));
        }

        @Override
        public void onYanked(@NonNull String text) {
            showToast(getString(R.string.terminal_find_yanked), true);
        }

        @Override
        public boolean isReducedMotionEnabled() {
            return TermuxActivity.this.isReducedMotionEnabled();
        }
    }

    /** Hardware and external-keyboard strokes claimed by an open rename chip. */
    private boolean handleTerminalRenameKey(int keyCode, @NonNull KeyEvent event) {
        return mRenameCoordinator != null && mRenameCoordinator.handleKeyDown(keyCode, event);
    }

    /** System-IME committed text claimed by an open rename chip. */
    private boolean handleTerminalRenameCodePoint(int codePoint, boolean ctrlDown) {
        return mRenameCoordinator != null
            && mRenameCoordinator.handleCodePoint(codePoint, ctrlDown);
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
        public View anchorFor(@NonNull com.termux.app.terminal.rename.TerminalRenameTarget target) {
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
        public String currentName(@NonNull com.termux.app.terminal.rename.TerminalRenameTarget target) {
            return currentTerminalName(target);
        }

        @Override
        public boolean applyName(@NonNull com.termux.app.terminal.rename.TerminalRenameTarget target,
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
            return mChrome.glass().surface(Math.max(0.88f, barAlpha), 0f, 1f, false, grain,
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
                @NonNull com.termux.app.terminal.rename.TerminalRenameTarget target) {
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
        public void onRenameEnded(@NonNull com.termux.app.terminal.rename.TerminalRenameTarget target,
                                  boolean committed) {
            if (!committed) return;
            refreshTerminalWindowBar();
            refreshSessionsPanel();
            if (getTermuxTerminalSessionClient() != null)
                getTermuxTerminalSessionClient().termuxSessionListNotifyUpdated();
        }
    }

    /** The name {@code target} currently carries, or null when it is unnamed. */
    @Nullable
    private String currentTerminalName(@NonNull com.termux.app.terminal.rename.TerminalRenameTarget target) {
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
    private void requestAppDrawerSearchKeyboard() {
        onSystemImeRequested();
        KeyboardUtils.showSoftKeyboard(this, mTerminalView);
    }

    /** Hardware and external-keyboard strokes claimed by the open palette. */
    private boolean handleCommandPaletteKey(int keyCode, @NonNull KeyEvent event) {
        return mCommandPalette != null && mCommandPalette.handleHardwareKey(keyCode, event);
    }

    /**
     * Text committed by a system IME, claimed by the open palette. Third-party keyboards commit
     * characters through the input connection instead of sending key events, so without this they
     * would type straight into the shell behind the overlay.
     */
    private boolean handleCommandPaletteCodePoint(int codePoint, boolean ctrlDown) {
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
        // The find strip is the next surface in: Back leaves the search before it leaves anything
        // the search was opened over.
        if (isScrollbackFindActive()) {
            cancelScrollbackFind();
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
        } else if (mSurfaceEditor.isActive()) {
            mSurfaceEditor.requestClose();
        } else if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else if (!isSplitPanesEnabled() && !getDrawer().isDrawerOpen(Gravity.LEFT)) {
            // The legacy sessions drawer only exists without the in-app multiplexer: with split
            // panes on, sessions live in the status pill's own panel and this drawer stays shut.
            getDrawer().openDrawer(Gravity.LEFT);
        }
    }

    void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            if (!shouldShowInRecents())
                finishAndRemoveTask();
            else
                finish();
        }
    }

    /**
     * Raise a transient notice in the top-trailing chip.
     *
     * <p>Was a stock toast with {@code setGravity(TOP)}, which Android 11 quietly stopped honouring
     * for text toasts — the notices had been landing bottom-centre over the prompt and the keyboard
     * ever since. {@link AppNotice} owns the placement now, and queues rather than cancelling, so a
     * burst is read in order instead of only its last line surviving.
     */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty() || mNoticeSuppressionDepth > 0)
            return;
        AppNotice.show(this, text, longDuration);
    }

    /**
     * Fork-styled replacement for {@link #showToast(String, boolean)} used for session switch,
     * title-change and session-exit notices: a small glass chip centered near the top of the
     * terminal surface instead of a stock Android toast.
     */
    void showSessionSwitchIndicator(@Nullable String text) {
        if (text == null || text.isEmpty() || isFinishing() || mNoticeSuppressionDepth > 0)
            return;
        com.termux.app.terminal.SessionSwitchIndicatorView indicator = obtainSessionSwitchIndicator();
        if (indicator != null)
            indicator.show(text);
    }

    /**
     * Runs {@code body} with every transient notice swallowed.
     *
     * <p>Window and pane creation are visible in themselves — a new pane appears, the window bar
     * grows a pill — so announcing them was pure noise, and worse, the new shell also tripped the
     * session-change notice, so one keypress raised two chips at once. Only session switches and
     * new sessions are worth a chip, and neither goes through here.
     */
    private void runWithoutNotices(@NonNull Runnable body) {
        mNoticeSuppressionDepth++;
        try {
            body.run();
        } finally {
            mNoticeSuppressionDepth--;
        }
    }

    /** True while a window/pane operation is deliberately running silent. */
    public boolean areNoticesSuppressed() {
        return mNoticeSuppressionDepth > 0;
    }

    // ------------------------------------------------------------- "how do I drive this mode?"
    //
    // Copy mode and scrollback search are entered from a chord and then own every key. The find
    // strip could only afford three words about that at its trailing end, and the view's own text
    // selection said nothing at all. The card is the legend for whichever of them is up.

    @Nullable private com.termux.app.terminal.TerminalModeHintCard mModeHintCard;

    /** Shows (or swaps) the legend for a modal terminal mode. */
    private void showTerminalModeHint(
            @NonNull com.termux.app.terminal.TerminalModeHintCard.Mode mode) {
        com.termux.app.terminal.TerminalModeHintCard card = obtainModeHintCard();
        if (card == null)
            return;
        syncTerminalModeHintFrame(card);
        card.show(mode);
    }

    private void hideTerminalModeHint() {
        if (mModeHintCard != null) mModeHintCard.hide();
    }

    @Nullable
    private com.termux.app.terminal.TerminalModeHintCard obtainModeHintCard() {
        FrameLayout host = findViewById(R.id.terminal_surface_host);
        if (host == null)
            return null;
        if (mModeHintCard == null)
            mModeHintCard = new com.termux.app.terminal.TerminalModeHintCard(this);
        if (mModeHintCard.getParent() == null) {
            host.addView(mModeHintCard,
                com.termux.app.terminal.TerminalModeHintCard.buildHostLayoutParams());
        }
        mModeHintCard.bringToFront();
        return mModeHintCard;
    }

    /**
     * Re-seats the card on the terminal's current edge. Margins, shape and style are all live
     * settings, so this is read at every show rather than once at construction.
     */
    private void syncTerminalModeHintFrame(
            @NonNull com.termux.app.terminal.TerminalModeHintCard card) {
        float radiusPx = terminalEdgeCornerRadiusPx();
        card.setTerminalFrame(terminalFrameInsetPx(false), terminalFrameInsetPx(true), radiusPx);
    }

    @Nullable
    private com.termux.app.terminal.SessionSwitchIndicatorView obtainSessionSwitchIndicator() {
        FrameLayout host = findViewById(R.id.terminal_surface_host);
        if (host == null)
            return null;
        if (mSessionSwitchIndicator == null) {
            // Top-leading corner, flush with the host's top edge — the same row the AppNotice chip
            // hangs from in the top-trailing corner, so two simultaneous notices read as one band.
            mSessionSwitchIndicator = new com.termux.app.terminal.SessionSwitchIndicatorView(this);
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
            // A notice can already be up when the first background command appears.
            mBackgroundProcessStack.setNoticeOccupancyPx(mAppNoticeOccupancyPx);
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
            AppNotice.show(this, R.string.voice_typing_unavailable, false);
            return;
        }
        Intent launch = createVoiceTypingIntent(this, chooser);
        try {
            mVoiceTypingTargetSession = target;
            startActivityForResult(launch, REQUEST_CODE_VOICE_TYPING);
        } catch (android.content.ActivityNotFoundException e) {
            mVoiceTypingTargetSession = null;
            AppNotice.show(this, R.string.voice_typing_unavailable, false);
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
                mChrome.onWallpaperChanged();
            }
            // Granted or denied, the first-run chain moves on: the two permissions are unrelated.
            requestWeatherLocationPermissionForFirstRun();
        }
    }

    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    private TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public View getAccessoryStackContainerView() {
        return findViewById(R.id.accessory_stack_container);
    }

    private ExtraKeysView getExtraKeysView(int page) {
        return page >= 0 && page < mExtraKeysViews.size() ? mExtraKeysViews.get(page) : null;
    }

    /** The first key page — the one the styling and geometry passes speak for. */
    private ExtraKeysView getExtraKeysView() {
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

    void termuxSessionListNotifyUpdated() {
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

    TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        // Returns the focused pane so all single-view client callbacks act on it.
        return mActivePane != null ? mActivePane : mTerminalView;
    }

    /** All terminal pane views currently rendered (leaves of the active tab). */
    java.util.List<TerminalView> getTerminalPaneViews() {
        if (mPaneController != null) return mPaneController.getVisiblePaneViews();
        java.util.List<TerminalView> panes = new java.util.ArrayList<>(1);
        if (mTerminalView != null) panes.add(mTerminalView);
        return panes;
    }

    TerminalFrameMetricsMonitor.Snapshot getTerminalFrameMetricsSnapshot() {
        return mTerminalFrameMetricsMonitor.snapshot();
    }

    /** Reset the window and every currently visible pane to the same benchmark origin. */
    void resetTerminalPerformanceMetrics() {
        mTerminalFrameMetricsMonitor.reset();
        for (TerminalView pane : getTerminalPaneViews()) pane.resetRenderMetrics();
    }

    /** The pane currently displaying {@code session}, or null if none. */
    @Nullable
    TerminalView getTerminalViewForSession(@Nullable TerminalSession session) {
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

    /** 1-based number of the current tmux-style session, or -1 before any session exists. */
    int getCurrentSessionNumber() {
        int index = mCurrentWSession == null ? -1 : mWSessions.indexOf(mCurrentWSession);
        return index < 0 ? -1 : index + 1;
    }

    /** 1-based number of the tmux-style session containing {@code shell}, or -1 when unowned. */
    int getSessionNumberFor(@Nullable TerminalSession shell) {
        if (shell == null || mPaneController == null) return -1;
        WSession ws = wsessionOwning(mPaneController.windowOf(shell));
        int index = ws == null ? -1 : mWSessions.indexOf(ws);
        return index < 0 ? -1 : index + 1;
    }

    /** Ctrl+Alt+Shift+R entry point: rename the current session, not its window or focused pane. */
    boolean promptCurrentSessionRename() {
        return beginTerminalRename(com.termux.app.terminal.rename.TerminalRenameTarget.SESSION);
    }

    /** Ctrl+Alt+R entry point: rename the current window, the tab it occupies in the window bar. */
    boolean promptCurrentWindowRename() {
        return beginTerminalRename(com.termux.app.terminal.rename.TerminalRenameTarget.WINDOW);
    }

    /**
     * Renames the current session without prompting.
     *
     * <p>Seam for registry actions ({@code session.rename}) and for a naming backend: a remote
     * caller supplies the name up front, so the editor cannot be used — it has no way to return a
     * result. Returns false when there is no session to rename.
     */
    boolean renameCurrentSessionTo(@Nullable String name) {
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
    boolean renameCurrentWindowTo(@Nullable String name) {
        if (mPaneController == null || mCurrentWSession == null
            || mCurrentWSession.windows.isEmpty()) return false;
        mPaneController.setWindowName(mCurrentWSession.currentWindow(), name);
        refreshTerminalWindowBar();
        refreshSessionsPanel();
        return true;
    }

    /** The name the current window kept, after the policy capped it. */
    @Nullable
    String getCurrentWindowName() {
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

    boolean renameBrowserSession(int index, @Nullable String name) {
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
    String getBrowserSessionName(int index) {
        if (index < 0 || index >= mWSessions.size()) return null;
        return mWSessions.get(index).name;
    }

    /** Close any browser-selected session, without first activating it. */
    public boolean closeBrowserSession(int index) {
        if (mPaneController == null || index < 0 || index >= mWSessions.size()) return false;
        WSession ws = mWSessions.get(index);
        boolean wasCurrent = ws == mCurrentWSession;
        if (wasCurrent) captureTerminalDeparture();
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
                animateTerminalSessionLifecycleArrival(-1);
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
    boolean selectWindow(int index) {
        if (mPaneController == null || mCurrentWSession == null
            || index < 0 || index >= mCurrentWSession.windows.size()) return false;
        showWindowFromBar(index);
        return true;
    }

    /** Number of windows in the current tmux-style session. */
    int getCurrentWindowCount() {
        return mCurrentWSession == null ? 0 : mCurrentWSession.windows.size();
    }

    /** Index of the visible window within the current session, or -1. */
    int getCurrentWindowIndex() {
        return mCurrentWSession == null ? -1 : mCurrentWSession.current;
    }

    /** Name of the current tmux-style session, or null when unnamed. */
    @Nullable
    String getCurrentSessionName() {
        return mCurrentWSession == null ? null : mCurrentWSession.name;
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
    void showWorkspacePicker() {
        com.termux.app.terminal.TerminalSessionBrowser.showWorkspacePicker(this);
    }

    /** Workspace save-name prompt, the workspace.save_prompt tool's front door. */
    void promptSaveWorkspace() {
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
    public com.termux.app.terminal.TerminalWorkspace.LoadResult loadWorkspace(@NonNull String name, boolean replace, boolean runCommands)
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
                        TerminalSession shell = createCommandShell(
                            runCommands ? pane.command : java.util.Collections.<String>emptyList(),
                            pane.cwd, pane.title);
                        if (shell == null) {
                            throw new com.termux.app.terminal.TerminalWorkspace.WorkspaceException(
                                "session_create_failed", "Could not create every workspace pane");
                        }
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
        return new com.termux.app.terminal.TerminalWorkspace.LoadResult(restored.size(), windowCount, paneCount,
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
    void openWallpaperPicker() {
        launchManagedWallpaperPicker();
    }

    /** Flips wallpaper passthrough mode and reports the value it moved to. */
    boolean toggleWallpaperMode() {
        boolean enabled = !shouldUseWallpaperPassthroughMode();
        setWallpaperModeEnabled(this, enabled);
        return enabled;
    }

    /** Whether wallpaper passthrough is currently on. */
    boolean isWallpaperModeEnabled() {
        return shouldUseWallpaperPassthroughMode();
    }

    /**
     * Flip the cursor trail preference and apply it to every live pane. Returns the value it moved to,
     * which can differ from what the views do while the device is in power save mode.
     */
    boolean toggleCursorTrail() {
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
    boolean isCursorTrailEnabled() {
        return mPreferences != null && mPreferences.isTerminalCursorTrailEnabled();
    }

    void openSurfaceEditor() {
        mSurfaceEditor.enter();
    }

    void openSettings() {
        openSettingsHome();
    }

    void openLookAndFeel() {
        openLookAndFeelSettings();
    }

    void openAppsBar() {
        openAppsBarSettings();
    }

    /** Seam for {@code terminal.reset}: reset the focused shell's emulator state. */
    boolean resetCurrentSession() {
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
    void rebuildDrawerSessions() {
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
                    mChrome.frost().alignFullStatusBar();
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
                        mChrome.frost().releaseFullStatusBar();
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
        applyLazyMode();

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
                    return isCommandPaletteOpen() || isAppDrawerEngaged() || mSurfaceEditor.isActive();
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
                // Docked keeps its terminal-facing corners through the gesture too, at the radius
                // this height resolves to — dropping to 0 mid-drag and snapping back on release
                // read as the corners flickering square.
                float radius = capsule ? resolveStatusBarCapsuleCornerRadiusPx(height)
                    : resolveDockedStatusInnerRadiusPx(height);
                mStatusBarSurfaceOutline.setInnerEdgeOnly(!capsule);
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
        // Re-applied here, not only at setup, so a lazy-mode toggle takes effect on the
        // settings-return refresh instead of waiting for the activity to be recreated.
        applyLazyMode();
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
        boolean windowBarBlurEnabled = ChromePolicy.dockBlurEnabled(blurRadiusDp);
        View blur = findViewById(R.id.terminal_window_bar_blur);
        applyRealtimeBlurRadius(blur, blurRadiusDp);
        applyRealtimeBlurDownsampleFactor(blur, ChromePolicy.ACCESSORY_BLUR_DOWNSAMPLE_FACTOR);
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
            background.setBackground(mChrome.glass().statusBarSurface(opacity,
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

    void syncBackgroundProcessStack() {
        java.util.List<com.termux.app.statusbar.BackgroundProcessModel.Snapshot> snapshots =
            new java.util.ArrayList<>();
        if (mPaneController != null && mWindowForegroundResolver != null) {
            java.util.HashSet<Integer> seenShells = new java.util.HashSet<>();
            for (WSession session : mWSessions) {
                for (com.termux.app.terminal.TerminalPaneController.Window window : session.windows) {
                    for (TerminalSession shell : mPaneController.shellsOf(window)) {
                        int shellPid = shell.getPid();
                        if (shellPid < 1 || !seenShells.add(shellPid)) continue;
                        // Only shells that rang the bell earn a standing row: a long-lived remote
                        // session or watcher is "running" forever, and a permanent corner row for it
                        // is noise. The bell is the same signal the attention notice and the window
                        // pill accent key off, and visiting the window clears it.
                        if (!mAttentionShellPids.contains(shellPid)) continue;
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
    void noteShellAttention(@NonNull TerminalSession session) {
        int pid = session.getPid();
        if (pid < 1 || mPaneController == null) return;
        if (mCurrentWSession != null
            && mPaneController.shellsOf(mCurrentWSession.currentWindow()).contains(session)) return;
        if (!mAttentionShellPids.add(pid)) return;
        refreshTerminalWindowBar();
    }

    void clearShellAttention(int shellPid) {
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
    void noteShellActivity(@Nullable TerminalSession session) {
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
            cpu.setIconGlyph("\uf4bc");   // nf-oct-cpu
            // Seeded from the smoother every pass: onStatsUpdated skips hidden widgets, so a value
            // published while this one was off or covered is otherwise only repainted when the
            // reading next changes \u2014 and before the first sample this is what puts "--" on screen
            // instead of an empty slot.
            if (cpuOn) cpu.setValue(mBarCpuSmoother.text());
            if (cpu.getTag() == null) {
                cpu.setTag("wired");
                cpu.setOnClickListener(v -> toggleStatsCard(v));
            }
        }
        if (ram != null) {
            ram.setVisibility(ramOn ? View.VISIBLE : View.GONE);
            ram.setColorRole(com.termux.app.statusbar.StatusBarWidgetView.ColorRole.SECONDARY);
            ram.setIconGlyph("\uefc5");   // nf-fa-memory
            if (ramOn) ram.setValue(mBarMemorySmoother.text());
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
                weather.setIconAnimation(
                    com.termux.app.statusbar.WeatherController.animationAssetFor(0, true));
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
                statsInterval(cardShowing ? STATS_CARD_INTERVAL_MS : STATS_BAR_INTERVAL_MS),
                cardShowing);
        } else if (mStatsController != null) {
            mStatsController.stop();
        }

        if (weatherOn) {
            ensureWeatherController().refreshIfStale();
        } else if (mWeatherController != null) {
            mWeatherController.stop();
        }

        ensureAiIndicatorController();
    }

    /**
     * The AI glyph has no preference behind it. It is a live state, not a choice: it appears while a
     * model is resident and greys out on its way back off screen, so it is wired once and left to
     * its own visibility rule.
     */
    private void ensureAiIndicatorController() {
        com.termux.app.statusbar.StatusBarWidgetView ai = findViewById(R.id.terminal_status_widget_ai);
        com.termux.app.statusbar.MaterialDotSeparatorView aiDot =
            findViewById(R.id.terminal_status_dot_ai);
        if (ai == null || aiDot == null) return;
        if (mAiIndicatorController == null) {
            mAiIndicatorController = new com.termux.app.statusbar.AiIndicatorController(ai, aiDot);
            ai.setOnClickListener(view -> {
                Intent intent = new Intent(this, com.termux.app.activities.SettingsActivity.class);
                intent.putExtra(com.termux.app.activities.SettingsActivity.EXTRA_OPEN_TAI_SETTINGS, true);
                startActivity(intent);
            });
        }
        mAiIndicatorController.start();
        mAiIndicatorController.refresh();
    }

    /** The sampling cadence, stretched in lazy mode. */
    private long statsInterval(long normalMs) {
        return isLazyModeEnabled() ? normalMs * STATS_LAZY_MULTIPLIER : normalMs;
    }

    private boolean isLazyModeEnabled() {
        return mPreferences != null && mPreferences.isLazyModeEnabled();
    }

    /**
     * Pushes the lazy-mode toggle to every consumer that caches it — the window bar and the clock;
     * the stats sampler reads {@link #isLazyModeEnabled()} on each cadence instead.
     */
    private void applyLazyMode() {
        boolean lazy = isLazyModeEnabled();
        com.termux.app.terminal.TerminalWindowBar bar = findViewById(R.id.terminal_window_bar);
        if (bar != null) bar.setLazyMode(lazy);
        com.termux.app.terminal.TerminalClockWidget clock = findViewById(R.id.terminal_clock_widget);
        if (clock != null) clock.setLazyMode(lazy);
    }

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
            // Keyed on the card, not the tapped widget: CPU and RAM both open this one card.
            boolean same = mStatusCardHost.isShowingContent(mStatsCardView);
            mStatusCardHost.dismiss();
            if (same) return;
        }
        if (mStatsCardView == null) {
            mStatsCardView = new com.termux.app.statusbar.SystemStatsCardView(this);
        }
        detachFromParent(mStatsCardView);
        mStatsCardView.bind(ensureStatsController().latest());
        ensureStatsController().start(statsInterval(STATS_CARD_INTERVAL_MS), true);
        setWidgetAccent(anchor, true);
        mStatusCardHost.setDropEdge(findViewById(R.id.terminal_window_bar_host));
        mStatusCardHost.show(anchor, mStatsCardView, statusCardStyleProvider(), () -> {
            setWidgetAccent(anchor, false);
            if (mStatsController != null
                && mPreferences != null
                && (mPreferences.isStatusWidgetCpuEnabled() || mPreferences.isStatusWidgetRamEnabled())) {
                mStatsController.start(statsInterval(STATS_BAR_INTERVAL_MS), false);
            }
        });
    }

    /**
     * Seam for {@code session.panel} and for the status-row session chip: drop the fork's sessions
     * list beneath the chip, or close it when it is already the open card. Another status card gives
     * way to it, matching how the stats and weather cards trade places.
     */
    void toggleSessionsPanel() {
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
    boolean isSessionsPanelShowing() {
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

    /** How long the status-bar weather icon moves for on arrival. */
    private static final long WEATHER_GREETING_ANIMATION_MS = 3_000L;
    /** Two arrival signals can land together (restart plus a HOME intent); one replay is enough. */
    private static final long WEATHER_GREETING_MIN_GAP_MS = 2_000L;
    private long mLastWeatherGreetingAt;

    /**
     * Runs the weather icon for a few seconds when the user arrives — unlocking the phone, or
     * coming back to the home screen from another app.
     *
     * <p>The icon is otherwise still: looping it costs about 14% of a core for as long as the
     * status bar is up, which is not a price worth paying for something nobody is looking at. The
     * moment the user does look is exactly when they have just arrived, so that is where the
     * movement goes.
     */
    private void playWeatherArrivalAnimation() {
        com.termux.app.statusbar.StatusBarWidgetView widget =
            findViewById(R.id.terminal_status_widget_weather);
        if (widget == null || widget.getVisibility() != View.VISIBLE) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - mLastWeatherGreetingAt < WEATHER_GREETING_MIN_GAP_MS) return;
        mLastWeatherGreetingAt = now;
        widget.replayIconAnimation(WEATHER_GREETING_ANIMATION_MS);
    }

    private void onWeatherUpdated(@NonNull com.termux.app.statusbar.WeatherController.Weather weather) {
        com.termux.app.statusbar.StatusBarWidgetView widget = findViewById(R.id.terminal_status_widget_weather);
        if (widget != null && widget.getVisibility() == View.VISIBLE) {
            if (weather.valid) {
                widget.setIconAnimation(com.termux.app.statusbar.WeatherController.animationAssetFor(
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
            boolean same = mStatusCardHost.isShowingContent(mWeatherCardView);
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
        mStatusCardHost.show(anchor, mWeatherCardView, statusCardStyleProvider(),
            () -> setWidgetAccent(anchor, false));
    }

    /** Switch directly from the app-owned window row and settle the terminal with a short wave. */
    private void showWindowFromBar(int index) {
        if (mPaneController == null || mCurrentWSession == null
            || index < 0 || index >= mCurrentWSession.windows.size()) return;
        int previous = mCurrentWSession.current;
        if (previous == index) return;
        mStatusCardHost.dismiss();
        captureTerminalDeparture();
        mCurrentWSession.current = index;
        mPaneController.showWindow(mCurrentWSession.currentWindow());
        rebuildDrawerSessions();
        animateTerminalWindowArrival(index >= previous ? 1 : -1);
    }

    /**
     * Travel distance for a workspace arrival. Enough to read as a slide, short of the full-width
     * push a compositor can afford — the panes here keep running while they move, and a long throw
     * of live terminals reads as lag rather than as motion.
     */
    private static final int WORKSPACE_SLIDE_DP = 64;

    /**
     * A window arrives sideways, a session arrives vertically. The two axes are the whole point:
     * Hyprland gives workspaces on one axis and groups on another, and once both switches animate
     * the axis is what tells the user which of the two just happened, without reading a label.
     */
    private void animateTerminalWindowArrival(int direction) {
        animateTerminalArrival(direction, true,
            com.termux.app.terminal.TerminalWindowBar.WINDOW_SWITCH_ANIMATION_DURATION_MS);
    }

    /** Session switch: the same arrival on the vertical axis. */
    void animateTerminalSessionArrival(int direction) {
        animateTerminalArrival(direction, false,
            com.termux.app.terminal.TerminalWindowBar.WINDOW_SWITCH_ANIMATION_DURATION_MS);
    }

    /**
     * Creation and close pans are quicker than navigation pans: a switch is travel the user
     * watches, but new/close is a command whose result the user is waiting to type into, and the
     * frame-timing pass measured the wait as the whole perceived latency. Same grammar, less time.
     */
    private static final long TERMINAL_LIFECYCLE_ANIMATION_MS = 380L;

    private void animateTerminalWindowLifecycleArrival(int direction) {
        animateTerminalArrival(direction, true, TERMINAL_LIFECYCLE_ANIMATION_MS);
    }

    void animateTerminalSessionLifecycleArrival(int direction) {
        animateTerminalArrival(direction, false, TERMINAL_LIFECYCLE_ANIMATION_MS);
    }

    private void animateTerminalArrival(int direction, boolean horizontal, long durationMs) {
        ViewGroup surfaceHost = findViewById(R.id.terminal_surface_host);
        View paneHost = findViewById(R.id.terminal_pane_host);
        if (surfaceHost == null || paneHost == null || isReducedMotionEnabled()) {
            dropTerminalDepartureSnapshot();
            return;
        }
        float travel = horizontal ? surfaceHost.getWidth() : surfaceHost.getHeight();
        if (travel <= 0f) {
            dropTerminalDepartureSnapshot();
            return;
        }
        // The switch is a wall pan, not a nudge: the outgoing card and the incoming surface each
        // travel one full viewport on the same clock and curve, edges one viewport apart the whole
        // way, so they read as one continuous sheet sliding past a stationary frame. No fades —
        // fading is what made this read as a step.
        float offset = (direction < 0 ? -1f : 1f) * travel;
        android.view.animation.Interpolator settle = com.termux.app.terminal.Motion.settle();
        // Neutralise any creation animation still running on the host itself.
        surfaceHost.animate().cancel();
        surfaceHost.setAlpha(1f);
        surfaceHost.setScaleX(1f);
        surfaceHost.setScaleY(1f);
        surfaceHost.setTranslationX(0f);
        surfaceHost.setTranslationY(0f);
        // The host normally lets pane chrome overflow; while the wall pans, both sheets must be
        // cut at the viewport edge or they would slide over the status row and the dock.
        // setClipBounds rather than clipChildren: other features flip clipChildren on ancestors
        // at will, and elevated pane chrome escapes it — clipBounds cuts everything this view
        // draws, children included.
        surfaceHost.setClipBounds(new android.graphics.Rect(
            0, 0, surfaceHost.getWidth(), surfaceHost.getHeight()));
        animateTerminalDeparture(surfaceHost, offset, horizontal, settle, durationMs);
        paneHost.animate().cancel();
        paneHost.setTranslationX(horizontal ? offset : 0f);
        paneHost.setTranslationY(horizontal ? 0f : offset);
        paneHost.animate()
            .translationX(0f)
            .translationY(0f)
            .setDuration(durationMs)
            .setInterpolator(settle)
            .withEndAction(() -> surfaceHost.setClipBounds(null))
            .start();
    }

    /** Snapshot of the outgoing terminal surface, captured just before a window/session swap. */
    @Nullable private android.graphics.Bitmap mTerminalDepartureSnapshot;

    /** Whether that snapshot was left see-through (wallpaper passthrough, glass off), so the
     *  ghost that carries it must not put an opaque plate back behind it. */
    private boolean mTerminalDepartureTranslucent;

    /**
     * Captures the terminal surface immediately before a window/session switch tears its pane
     * tree down. The pane glass is translucent, so the raw pixels would double-expose over the
     * incoming session mid-pan; compositing them onto the theme's opaque surface turns the
     * outgoing session into a solid glass card the pan can physically carry away.
     */
    void captureTerminalDeparture() {
        dropTerminalDepartureSnapshot();
        if (isReducedMotionEnabled()) return;
        View terminal = findViewById(R.id.terminal_surface_host);
        if (terminal == null || terminal.getWidth() <= 0 || terminal.getHeight() <= 0) return;
        try {
            // Half resolution: the software draw of the whole hierarchy (glass shaders included)
            // is the expensive part of a switch's silent gap, and it scales with pixels. The card
            // is only ever seen in motion, stretched back up by the ghost — the softness never
            // reads at pan speed. Quarter of the pixels, quarter of the work.
            android.graphics.Bitmap snapshot = android.graphics.Bitmap.createBitmap(
                Math.max(1, terminal.getWidth() / 2), Math.max(1, terminal.getHeight() / 2),
                android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(snapshot);
            canvas.scale(0.5f, 0.5f);
            // The ground behind the panes: the pane-gap margins and rounded rim corners are
            // transparent in the surface's own draw, and whatever is baked there rides along for
            // the whole pan. A flat base colour painted a dark border around every rim (the live
            // layout shows blurred wallpaper through those gaps), so the same shared blur frame
            // the pane glass draws is composited first, and the flat colour is only the fallback
            // for when glass is off (where the terminal ground really is that colour).
            boolean paintedGround = paintWallpaperGlassGround(canvas, terminal);
            // The flat base colour is only right where the live ground really is opaque. In
            // wallpaper passthrough mode with glass off the ground is the wallpaper seen through
            // the unified dim — painting the opaque base there turned the outgoing card into a
            // near-black slab sliding over the wallpaper. Leaving the snapshot translucent is
            // exact instead: the wallpaper behind the pan is static and shared by both sheets,
            // and the card's trailing edge meets the incoming surface's leading edge, so the
            // see-through card never double-exposes over anything the live layout didn't.
            boolean translucentGround = !paintedGround && shouldUseWallpaperPassthroughMode();
            if (!paintedGround && !translucentGround)
                canvas.drawColor(resolveTerminalSurfaceBaseColor());
            terminal.draw(canvas);
            mTerminalDepartureSnapshot = snapshot;
            mTerminalDepartureTranslucent = translucentGround;
        } catch (Throwable t) {
            // OOM or a view that cannot software-draw: the switch just loses its outgoing half.
            mTerminalDepartureSnapshot = null;
        }
    }

    /**
     * Paints the shared pre-blurred wallpaper frame across {@code canvas}, mapped exactly as
     * {@link com.termux.app.terminal.PaneGlassBackdropView} maps it (frame rect in screen
     * coordinates, clamped shader), so a departure snapshot's ground matches what the live layout
     * showed around the panes. False when the glass frame is unavailable (glass off, no blur).
     */
    private boolean paintWallpaperGlassGround(@NonNull android.graphics.Canvas canvas,
                                              @NonNull View terminal) {
        android.graphics.Bitmap frame = obtainTerminalPaneGlassFrame();
        if (frame == null || frame.isRecycled()) return false;
        android.graphics.Rect frameRect = mChrome.blurCache().frameRectRef();
        if (frameRect.isEmpty()) return false;
        int[] location = new int[2];
        terminal.getLocationOnScreen(location);
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(frameRect.width() / (float) Math.max(1, frame.getWidth()),
            frameRect.height() / (float) Math.max(1, frame.getHeight()));
        matrix.postTranslate(frameRect.left - location[0], frameRect.top - location[1]);
        android.graphics.BitmapShader shader = new android.graphics.BitmapShader(
            frame, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        shader.setLocalMatrix(matrix);
        android.graphics.Paint paint =
            new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        paint.setColorFilter(com.termux.app.chrome.GlassFilters.frost());
        // View coordinates, not bitmap coordinates: the caller's canvas may be scaled down for a
        // reduced-resolution snapshot, and the shader matrix above is built in view space.
        canvas.drawRect(0f, 0f, terminal.getWidth(), terminal.getHeight(), paint);
        return true;
    }

    /** The opaque ground the departure card is composited onto. */
    private int resolveTerminalSurfaceBaseColor() {
        return getTermuxThemeColor(
            com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
    }

    private void dropTerminalDepartureSnapshot() {
        if (mTerminalDepartureSnapshot != null) {
            mTerminalDepartureSnapshot.recycle();
            mTerminalDepartureSnapshot = null;
        }
    }

    /**
     * Slides the captured outgoing card one full viewport toward the opposite edge, lifted above
     * the incoming surface with a real shadow — the old session physically picks up and leaves.
     */
    private void animateTerminalDeparture(ViewGroup surfaceHost, float offset, boolean horizontal,
                                          android.view.animation.Interpolator settle,
                                          long durationMs) {
        android.graphics.Bitmap departure = mTerminalDepartureSnapshot;
        boolean translucent = mTerminalDepartureTranslucent;
        mTerminalDepartureSnapshot = null;
        mTerminalDepartureTranslucent = false;
        if (departure == null) return;
        android.widget.ImageView ghost = new android.widget.ImageView(this);
        ghost.setImageBitmap(departure);
        // The snapshot is captured at half resolution (see captureTerminalDeparture); the ghost
        // stretches it back over the full surface. It only ever exists moving, so the softness
        // never reads.
        ghost.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        // A translucent snapshot stays translucent: an opaque plate here is the black flash the
        // capture just avoided. The plate (and the shadow its outline enables) belongs only to
        // the opaque-ground cards.
        if (!translucent) {
            ghost.setBackgroundColor(resolveTerminalSurfaceBaseColor());
            ghost.setElevation(dpToPx(12));
        }
        surfaceHost.addView(ghost, new android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ghost.animate()
            .translationX(horizontal ? -offset : 0f)
            .translationY(horizontal ? 0f : -offset)
            .setDuration(durationMs)
            .setInterpolator(settle)
            .withEndAction(() -> {
                surfaceHost.removeView(ghost);
                ghost.setImageDrawable(null);
                departure.recycle();
            })
            .start();
    }

    // Creation used to settle in place (fade + a whisper of scale); it now rides the same wall
    // pan as navigation — niri's language, where a new surface scrolls the strip to make room —
    // so the dedicated creation animation is gone.

    /** Index of {@code session} within the drawer-visible list, or -1. */
    int getDrawerIndexOfSession(TerminalSession session) {
        for (int i = 0; i < mDrawerSessions.size(); i++)
            if (mDrawerSessions.get(i).getTerminalSession() == session) return i;
        return -1;
    }

    /**
     * Show the window that owns {@code session} and focus that pane. A brand-new shell (not in any
     * window yet) becomes its own new session with a single window.
     * @return true if the focused session changed.
     */
    boolean activateSessionInPanes(TerminalSession session) {
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
    TerminalSession getCurrentTabPrimary() {
        // The current session's drawer representative (its current window's focused shell), so
        // session cycling lines up with the drawer list.
        if (mCurrentWSession != null && mPaneController != null && !mCurrentWSession.windows.isEmpty())
            return mPaneController.windowActiveSession(mCurrentWSession.currentWindow());
        return mCurrentTabPrimary;
    }

    /** Split the focused pane, spawning a new shell in the new pane. orientation = LinearLayout.*. */
    void splitCurrentPane(int orientation) {
        if (!isSplitPanesEnabled() || mPaneController == null) return;
        if (mTermuxService == null || mPaneController.getActiveSession() == null) {
            showSessionSwitchIndicator(getString(R.string.msg_no_session_to_split));
            return;
        }
        // No notice on success: the new pane is the feedback. The refusal above still speaks,
        // because nothing visible happens when there is no shell to split.
        runWithoutNotices(() -> mPaneController.split(orientation));
    }

    /** Push the Sessions-and-panes behaviour toggles into the pane controller. */
    private void applyPaneBehaviourPreferences() {
        if (mPaneController == null || getPreferences() == null) return;
        mPaneController.setDefaultLayoutPolicy(getPreferences().isDwindleDefaultLayoutEnabled()
            ? com.termux.app.terminal.TerminalPaneController.LAYOUT_DWINDLE : null);
        mPaneController.setFocusGrowEnabled(getPreferences().isFocusedPaneGrowsEnabled());
    }

    /** Split the focused pane along its longer side (Ctrl+Alt+Enter): a new terminal, no axis asked. */
    void splitCurrentPaneAuto() {
        if (!isSplitPanesEnabled() || mPaneController == null) return;
        if (mTermuxService == null || mPaneController.getActiveSession() == null) {
            showSessionSwitchIndicator(getString(R.string.msg_no_session_to_split));
            return;
        }
        runWithoutNotices(() -> mPaneController.splitAuto());
    }

    /** Move focus to the pane in the given arrow direction (Ctrl+Alt+arrow). No-op if none. */
    boolean focusPaneDirection(int keyCode) {
        return mPaneController != null && mPaneController.focusDirection(keyCode);
    }

    /** Adjust the split ratio toward the arrow direction (Ctrl+Alt+Shift+arrow). */
    boolean resizeActivePane(int keyCode) {
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
    boolean applyPaneLayout(@NonNull String layout) {
        return isSplitPanesEnabled() && mPaneController != null && mPaneController.applyLayout(layout);
    }

    /** Advance the current window to the next automatic pane layout and retain it. */
    boolean cyclePaneLayout() {
        return isSplitPanesEnabled() && mPaneController != null && mPaneController.nextLayout();
    }

    /** The current window's retained automatic layout, or null when manually managed. */
    @Nullable
    String activePaneLayoutPolicy() {
        return mPaneController == null ? null : mPaneController.activeLayoutPolicy();
    }

    /** Reset every split in the current window to a 1:1 divider ratio. */
    boolean equalizePaneLayout() {
        return isSplitPanesEnabled() && mPaneController != null && mPaneController.equalizeLayout();
    }

    /** Rotate the current pane tree geometrically by ninety degrees. */
    boolean rotatePaneLayout(boolean clockwise) {
        return isSplitPanesEnabled() && mPaneController != null
            && mPaneController.rotateLayout(clockwise);
    }

    /** Move the focused pane to an outer edge of the current window. */
    boolean moveFocusedPaneToEdge(@NonNull String edge) {
        return isSplitPanesEnabled() && mPaneController != null
            && mPaneController.moveActivePaneToEdge(edge);
    }

    /** Kill the focused pane's shell (Alt+Esc). Teardown/promotion happens in onSessionFinished. */
    boolean killFocusedPane() {
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

    /**
     * A shell that first runs {@code command} (argv; empty for a plain shell) and then stays behind
     * as the user's login shell, the way a restored workspace pane does. Null when the service is
     * not ready or the terminal limit is reached. Shared by workspace restore and the pane API.
     */
    @Nullable TerminalSession createCommandShell(@NonNull java.util.List<String> command,
                                                 @Nullable String cwd, @Nullable String title) {
        if (mTermuxService == null) return null;
        if (mTermuxService.getTermuxSessionsSize()
                >= com.termux.app.terminal.TermuxTerminalSessionActivityClient.MAX_SESSIONS) {
            return null;
        }
        String executable = null;
        String[] arguments = null;
        if (!command.isEmpty()) {
            String shell = wrapperShellPath();
            if (shell != null) {
                // The command is looked up on the PATH the user's own config builds, since a
                // captured command frequently lives somewhere only that config knows about, and a
                // shell stays behind once it exits so a pane restoring `make` does not vanish with
                // the build.
                boolean fishStyle = isFishShell(shell);
                String script = shellCommandLine(command, fishStyle)
                    + "; exec " + shellQuote(shell, fishStyle) + " -l";
                String login = loginProgramPath();
                if (login != null) {
                    // Termux's login ends in `exec "$SHELL" -l "$@"`, so this runs the same shell
                    // the same way a normal pane does — and only it sets up LD_PRELOAD for
                    // termux-exec and sources termux-login.sh. Passing arguments also skips its motd.
                    executable = login;
                    arguments = new String[] {"-c", script};
                } else {
                    executable = shell;
                    arguments = new String[] {"-l", "-c", script};
                }
            } else {
                executable = command.get(0);
                arguments = command.subList(1, command.size()).toArray(new String[0]);
            }
        }
        if (cwd == null || cwd.isEmpty()) cwd = getProperties().getDefaultWorkingDirectory();
        com.termux.shared.termux.shell.command.runner.terminal.TermuxSession created =
            mTermuxService.createTermuxSession(executable, arguments, null, cwd, false,
                title == null || title.isEmpty() ? null : title);
        return created == null ? null : created.getTerminalSession();
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
    void createNewWindow() {
        if (!isSplitPanesEnabled() || mPaneController == null) return;
        if (mCurrentWSession == null) { // no session yet -> behave like new session
            getTermuxTerminalSessionClient().addNewSession(false, null);
            return;
        }
        TerminalSession cur = getCurrentSession();
        TerminalSession shell = createShellForCwd(cur != null ? cur.getCwd() : null);
        if (shell == null) return;
        runWithoutNotices(() -> {
            com.termux.app.terminal.TerminalPaneController.Window w = mPaneController.newWindow(shell);
            mCurrentWSession.windows.add(w);
            mCurrentWSession.current = mCurrentWSession.windows.size() - 1;
            // niri's language: a new window on a full screen scrolls the strip sideways to make
            // room, so creation rides the same wall pan as navigation — appended at the end, it
            // enters from the trailing edge while the old window slides out the other side.
            captureTerminalDeparture();
            mPaneController.showWindow(w);
            animateTerminalWindowLifecycleArrival(1);
        });
        rebuildDrawerSessions();
    }

    /** Close the current window (Ctrl+Alt+X): kill its panes; if it was the session's last window,
     *  close the session too. */
    void closeCurrentWindow() {
        if (mPaneController == null || mCurrentWSession == null) return;
        com.termux.app.terminal.TerminalPaneController.Window w = mPaneController.activeWindow();
        if (w == null) return;
        // Creation's pan in reverse: the dying window is carried off and a neighbour slides in —
        // from the left when the strip's tail was closed, from the right when a middle window's
        // right-hand neighbour moves up to fill its slot.
        int oldIndex = mCurrentWSession.current;
        captureTerminalDeparture();
        for (TerminalSession s : mPaneController.removeWindow(w))
            if (mTermuxService != null) mTermuxService.killTermuxSession(s);
        mCurrentWSession.windows.remove(w);
        if (mCurrentWSession.windows.isEmpty()) {
            mWSessions.remove(mCurrentWSession);
            mCurrentWSession = null;
            showNextSessionAfterClose();
        } else {
            mCurrentWSession.current = Math.min(oldIndex, mCurrentWSession.windows.size() - 1);
            mPaneController.showWindow(mCurrentWSession.currentWindow());
            animateTerminalWindowLifecycleArrival(mCurrentWSession.current < oldIndex ? -1 : 1);
        }
        rebuildDrawerSessions();
    }

    /** Switch to the next/previous window within the current session (Ctrl+Alt+] / [). */
    void switchWindow(boolean forward) {
        if (mPaneController == null || mCurrentWSession == null) return;
        int n = mCurrentWSession.windows.size();
        if (n < 2) return;
        int target = ((mCurrentWSession.current + (forward ? 1 : -1)) % n + n) % n;
        showWindowFromBar(target);
        // The pan narrates the switch and the window bar's selected pill names the position, so
        // with the bar on screen a chip would say what is already visible twice over. Only when
        // the bar is hidden does the position have no other voice.
        View windowBar = findViewById(R.id.terminal_window_bar_host);
        if (windowBar == null || !windowBar.isShown()) {
            String direction = getString(forward ? R.string.tool_window_next : R.string.tool_window_previous);
            showSessionSwitchIndicator(getString(R.string.msg_window_switch_position, direction, target + 1, n));
        }
    }

    /** Close the whole current session (Ctrl+Alt+Shift+X): all its windows + panes. */
    void closeCurrentSession() {
        if (mPaneController == null || mCurrentWSession == null) {
            // Fallback: close the current shell's session the classic way.
            TerminalSession cur = getCurrentSession();
            if (cur != null && getTermuxTerminalSessionClient() != null)
                getTermuxTerminalSessionClient().removeFinishedSession(cur);
            return;
        }
        WSession ws = mCurrentWSession;
        captureTerminalDeparture();
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
            // The closed session is carried off along the session axis, the survivor slides in —
            // the same travel its creation played, reversed. Callers capture the departure.
            animateTerminalSessionLifecycleArrival(-1);
        } else if (getTermuxTerminalSessionClient() != null) {
            getTermuxTerminalSessionClient().addNewSession(false, null);
        }
    }

    /** Drop a window from its session after its last pane finished (called from onSessionFinished). */
    void onWindowEmptied(com.termux.app.terminal.TerminalPaneController.Window w) {
        WSession ws = wsessionOwning(w);
        if (ws == null) return;
        int oldIndex = ws.current;
        ws.windows.remove(w);
        if (ws == mCurrentWSession) {
            if (ws.windows.isEmpty()) {
                mWSessions.remove(ws);
                mCurrentWSession = null;
                showNextSessionAfterClose();
            } else {
                ws.current = Math.min(oldIndex, ws.windows.size() - 1);
                mPaneController.showWindow(ws.currentWindow());
                // No departure snapshot here — the window died with its last shell and its panes
                // are already gone — but the neighbour still arrives with the travel language.
                animateTerminalWindowLifecycleArrival(ws.current < oldIndex ? -1 : 1);
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
        @Override public boolean shouldTerminalTakeFocus() {
            return mInAppKeyboard == null || !mInAppKeyboard.isExternalTextInputActive();
        }

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

    /** Bridges the terminal clients back into the activity. */
    private final class ActivityTerminalHost implements com.termux.app.terminal.TerminalHost {

        @Nullable private com.termux.app.terminal.TerminalKeyChordOverlay mKeyChordOverlay;

        @Override @Nullable public TerminalView focusedView() {
            return getTerminalView();
        }

        @Override @Nullable public TerminalSession currentSession() {
            return TermuxActivity.this.getCurrentSession();
        }

        @Override @Nullable public ExtraKeysView extraKeysView() {
            return getExtraKeysView();
        }

        @Override @Nullable public EditText toolbarTextInput() {
            return findViewById(R.id.terminal_toolbar_text_input);
        }

        @Override public boolean hasTerminalToolbar() {
            return getTerminalToolbarViewPager() != null;
        }

        @Override public boolean isTerminalViewSelected() {
            return TermuxActivity.this.isTerminalViewSelected();
        }

        @Override public void setRootViewLoggingEnabled(boolean enabled) {
            getTermuxActivityRootView().setIsRootViewLoggingEnabled(enabled);
        }

        @Override public void showTerminalModeHint(
                @Nullable com.termux.app.terminal.TerminalModeHintCard.Mode mode) {
            // Qualified: this inner class declares the same name, so an unqualified call is a
            // recursion into itself rather than a call to the activity's own method.
            if (mode == null) TermuxActivity.this.hideTerminalModeHint();
            else TermuxActivity.this.showTerminalModeHint(mode);
        }

        @Override public void setDrawerLocked(boolean locked) {
            // Split panes retire the legacy sessions drawer entirely, so leaving copy mode must
            // not unlock it.
            getDrawer().setDrawerLockMode(locked || isSplitPanesEnabled()
                ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                : DrawerLayout.LOCK_MODE_UNLOCKED);
        }

        @Override public void toggleTerminalToolbar() {
            TermuxActivity.this.toggleTerminalToolbar();
        }

        @Override public void requestFlushDockGeometryUpdate() {
            requestTerminalFlushDockGeometryUpdate();
        }

        @Override public TermuxAppSharedPreferences preferences() {
            return getPreferences();
        }

        @Override public TermuxAppSharedProperties properties() {
            return getProperties();
        }

        @Override public boolean isActivityRecreated() {
            return TermuxActivity.this.isActivityRecreated();
        }

        @Override public boolean isOnResumeAfterOnCreate() {
            return TermuxActivity.this.isOnResumeAfterOnCreate();
        }

        @Override public boolean isSplitPanesEnabled() {
            return TermuxActivity.this.isSplitPanesEnabled();
        }

        @Override public void finishActivityIfNotFinishing() {
            TermuxActivity.this.finishActivityIfNotFinishing();
        }

        @Override public void runOnUiThread(@NonNull Runnable runnable) {
            TermuxActivity.this.runOnUiThread(runnable);
        }

        @Override public int activePaneFontSize() {
            return getActivePaneFontSize();
        }

        @Override public boolean setActivePaneFontSize(int size) {
            return TermuxActivity.this.setActivePaneFontSize(size);
        }

        @Override public void onSystemImeRequested() {
            TermuxActivity.this.onSystemImeRequested();
        }

        @Override public boolean shouldDelaySoftKeyboardShowOnResume() {
            return TermuxActivity.this.shouldDelaySoftKeyboardShowOnResume();
        }

        @Override public boolean areSoftKeyboardFlagsDisabled() {
            return KeyboardUtils.areDisableSoftKeyboardFlagsSet(TermuxActivity.this);
        }

        @Override public void disableSoftKeyboard(@Nullable View view) {
            KeyboardUtils.disableSoftKeyboard(TermuxActivity.this, view);
        }

        @Override public void clearDisableSoftKeyboardFlags() {
            KeyboardUtils.clearDisableSoftKeyboardFlags(TermuxActivity.this);
        }

        @Override public void setSoftKeyboardAlwaysHiddenFlags() {
            KeyboardUtils.setSoftKeyboardAlwaysHiddenFlags(TermuxActivity.this);
        }

        @Override public void setSoftInputModeAdjustResize() {
            KeyboardUtils.setSoftInputModeAdjustResize(TermuxActivity.this);
        }

        @Override public void setSoftKeyboardVisibility(@NonNull Runnable showSoftKeyboardRunnable,
                                                        @Nullable View view, boolean visible) {
            KeyboardUtils.setSoftKeyboardVisibility(showSoftKeyboardRunnable, TermuxActivity.this,
                view, visible);
        }

        @Override public boolean isInAppKeyboardEnabled() {
            return mInAppKeyboard != null && mInAppKeyboard.isEnabled();
        }

        @Override public boolean cycleInAppKeyboardLayout(int delta) {
            return mInAppKeyboard != null && mInAppKeyboard.cycleTextLayout(delta);
        }

        @Override public boolean selectInAppKeyboardLayout(@NonNull String layoutId) {
            return mInAppKeyboard != null && mInAppKeyboard.selectTextLayout(layoutId, true);
        }

        @NonNull
        @Override public String activeInAppKeyboardLayout() {
            return mInAppKeyboard == null
                ? com.termux.app.terminal.inappkeyboard.LauncherKeyboardLayouts.LAYOUT_MAIN
                : mInAppKeyboard.getActiveTextLayoutId();
        }

        @Override public boolean isKeybindHintPopupVisible() {
            return mKeybindHintPresenter.isVisible();
        }

        @Override public void onKeybindHintConsumed() {
            mKeybindHintPresenter.onConsumed();
        }

        @Override public void toggleKeybindHintFullPopup() {
            mKeybindHintPresenter.toggleFullPopup();
        }

        @Override public void setHardwareKeybindHintPrefix(@Nullable String prefix, boolean shift) {
            mKeybindHintPresenter.setHardwarePrefix(prefix, shift);
        }

        @Override @NonNull public KeyChordUi keyChordUi() {
            if (mKeyChordOverlay == null)
                mKeyChordOverlay = new com.termux.app.terminal.TerminalKeyChordOverlay(
                    TermuxActivity.this);
            return mKeyChordOverlay;
        }

        @Override public void playKeyChordCancelledSound() {
            getWindow().getDecorView().playSoundEffect(
                android.view.SoundEffectConstants.CLICK);
        }

        @Override public void showToast(String text, boolean longDuration) {
            TermuxActivity.this.showToast(text, longDuration);
        }

        @Override public boolean showTerminalActionSheet(@Nullable android.graphics.PointF anchor) {
            return TermuxActivity.this.showTerminalActionSheet(anchor);
        }

        @Override @NonNull public com.termux.app.terminal.TerminalSheetController sheetController() {
            return getTerminalSheetController();
        }

        @Override public boolean beginScrollbackFind() {
            return TermuxActivity.this.beginScrollbackFind();
        }

        @Override public void showHintsOverlay(@NonNull String transcript) {
            com.termux.app.terminal.TerminalHintsOverlay.show(TermuxActivity.this, transcript);
        }

        @Override public void showScrollbackSearchOverlay(@NonNull TerminalView view) {
            com.termux.app.terminal.TerminalScrollbackSearchOverlay.show(TermuxActivity.this, view);
        }

        @Override public boolean promptCurrentSessionRename() {
            return TermuxActivity.this.promptCurrentSessionRename();
        }

        @Override public boolean overlaysConsumeKeyDown(int keyCode, @NonNull KeyEvent event) {
            // An open rename chip is a modal editor over one surface, so it outranks every other
            // consumer: while it is up, every stroke belongs to the name being typed.
            if (handleTerminalRenameKey(keyCode, event))
                return true;
            // The find strip is the same kind of claim: while it is up every stroke is either the
            // query or a vim command over the transcript, and none of it belongs to the shell.
            if (handleScrollbackFindKey(keyCode, event))
                return true;
            // Back for the widget pane and the FULL status pane. Same order as onBackPressed(), and
            // the same reason the drawer has a claim below: on a device the back key is consumed in
            // this channel and never reaches onBackPressed().
            if (handleOverlayPaneKey(keyCode, event))
                return true;
            // The palette overlay claims typing before the terminal writes it, the same point the
            // in-app keyboard's interceptor sits at. Checked first so nothing else can consume esc.
            if (handleCommandPaletteKey(keyCode, event))
                return true;
            // The sheet plane, after the palette so it can never swallow the escape stroke the
            // palette is checked first for, and before the drawer, which a sheet closes as it opens.
            // On a device back is consumed here and never reaches onBackPressed(), so the plane
            // needs both routes.
            if (handleTerminalSheetKey(keyCode, event))
                return true;
            // After the palette (which can be summoned over the drawer and therefore outranks it)
            // and before the app-search hook, which reads the terminal's own input line — a line
            // nothing typed into the drawer ever reaches.
            if (handleAppDrawerKey(keyCode, event))
                return true;
            return handleTerminalAppSearchKey(keyCode);
        }

        @Override public boolean overlaysConsumeKeyUp(int keyCode) {
            // The release of a stroke the palette consumed on the way down.
            if (isCommandPaletteOpen())
                return true;
            if (isFolderRenameActive())
                return true;
            // The release of a back press a pane consumed on the way down.
            if (consumeOverlayPaneKeyUp(keyCode))
                return true;
            // Same for the sheet plane: the press was claimed on the way down, and a release let
            // through on its own would reach the shell behind a modal surface.
            if (isTerminalSheetOpen())
                return true;
            // Same for the drawer, whose plane is full screen.
            return isAppDrawerOpen();
        }

        @Override public boolean overlaysConsumeCodePoint(int codePoint, boolean ctrlDown) {
            // The rename chip's twin of its key hook, in the same order: it outranks the rest.
            if (handleTerminalRenameCodePoint(codePoint, ctrlDown))
                return true;
            // The find strip's twin of its key hook, in the same order.
            if (handleScrollbackFindCodePoint(codePoint, ctrlDown))
                return true;
            // The twin of the palette hook, and checked first for the same reason.
            if (handleCommandPaletteCodePoint(codePoint, ctrlDown))
                return true;
            // The sheet plane's twin of the same hook, in the same order.
            if (handleTerminalSheetCodePoint(codePoint, ctrlDown))
                return true;
            // The drawer's twin of the same hook: after the palette, before the enter-only
            // app-search hook below.
            if (handleAppDrawerCodePoint(codePoint, ctrlDown))
                return true;
            // The AOSP keyboard and its descendants send ⏎ as text rather than as KEYCODE_ENTER —
            // see TerminalView#sendTextToTerminal — so the key-code-only app-search hook needs this
            // twin too. Only a consumed enter is claimed; an unconsumed one still reaches the shell.
            return (codePoint == '\r' || codePoint == '\n')
                && handleTerminalAppSearchKey(KeyEvent.KEYCODE_ENTER);
        }

        @Override public boolean shouldProcessSuggestionBarKeyEvent(int keyCode) {
            return TermuxActivity.this.shouldProcessSuggestionBarKeyEvent(keyCode);
        }

        @Override public boolean shouldProcessSuggestionBarCodePoint(int codePoint, boolean ctrlDown) {
            return TermuxActivity.this.shouldProcessSuggestionBarCodePoint(codePoint, ctrlDown);
        }

        @Override @NonNull public Context context() {
            return TermuxActivity.this;
        }

        @Override public boolean isHostAlive() {
            return !isFinishing() && !isDestroyed();
        }

        @Override public boolean isVisible() {
            return TermuxActivity.this.isVisible();
        }

        @Override public void showTerminalActionHint(@NonNull String toolName) {
            TermuxActivity.this.showTerminalActionHint(toolName);
        }

        @Override @Nullable public TerminalView viewForSession(@Nullable TerminalSession session) {
            return getTerminalViewForSession(session);
        }

        @Override @NonNull public java.util.List<TerminalView> paneViews() {
            return getTerminalPaneViews();
        }

        @Override @Nullable
        public com.termux.app.terminal.TerminalPaneController paneController() {
            return getPaneController();
        }

        @Override public void splitCurrentPane(int orientation) {
            TermuxActivity.this.splitCurrentPane(orientation);
        }

        @Override public void splitCurrentPaneAuto() {
            TermuxActivity.this.splitCurrentPaneAuto();
        }

        @Override public boolean focusPaneDirection(int keyCode) {
            return TermuxActivity.this.focusPaneDirection(keyCode);
        }

        @Override public boolean resizeActivePane(int keyCode) {
            return TermuxActivity.this.resizeActivePane(keyCode);
        }

        @Override public boolean killFocusedPane() {
            return TermuxActivity.this.killFocusedPane();
        }

        @Override public boolean applyPaneLayout(@NonNull String layout) {
            return TermuxActivity.this.applyPaneLayout(layout);
        }

        @Override public boolean cyclePaneLayout() {
            return TermuxActivity.this.cyclePaneLayout();
        }

        @Override @Nullable public String activePaneLayoutPolicy() {
            return TermuxActivity.this.activePaneLayoutPolicy();
        }

        @Override public boolean equalizePaneLayout() {
            return TermuxActivity.this.equalizePaneLayout();
        }

        @Override public boolean rotatePaneLayout(boolean clockwise) {
            return TermuxActivity.this.rotatePaneLayout(clockwise);
        }

        @Override @Nullable public TerminalSession openCommandPane(
                @NonNull java.util.List<String> command, @Nullable String cwd,
                @Nullable String title, boolean focus) {
            if (!isSplitPanesEnabled() || mPaneController == null
                || mPaneController.getActiveSession() == null) return null;
            TerminalSession shell = createCommandShell(command, cwd, title);
            if (shell == null) return null;
            boolean[] added = {false};
            runWithoutNotices(() -> added[0] = mPaneController.addPane(shell, focus));
            if (!added[0]) {
                mTermuxService.killTermuxSession(shell);
                return null;
            }
            return shell;
        }

        @Override @NonNull public java.util.List<com.termux.app.terminal.TerminalPaneController.Window>
                currentSessionWindows() {
            return mCurrentWSession == null
                ? java.util.Collections.<com.termux.app.terminal.TerminalPaneController.Window>emptyList()
                : new java.util.ArrayList<>(mCurrentWSession.windows);
        }

        @Override @Nullable public TerminalSession findPaneById(@NonNull String id) {
            if (mTermuxService == null) return null;
            for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession
                    : mTermuxService.getTermuxSessions()) {
                TerminalSession session = termuxSession.getTerminalSession();
                if (session != null && id.equals(session.mHandle)) return session;
            }
            return null;
        }

        @Override public boolean moveFocusedPaneToEdge(@NonNull String edge) {
            return TermuxActivity.this.moveFocusedPaneToEdge(edge);
        }

        @Override @Nullable public TermuxService service() {
            return getTermuxService();
        }

        @Override public void noteShellActivity(@Nullable TerminalSession session) {
            TermuxActivity.this.noteShellActivity(session);
        }

        @Override public void noteShellAttention(@NonNull TerminalSession session) {
            TermuxActivity.this.noteShellAttention(session);
        }

        @Override public void clearShellAttention(int shellPid) {
            TermuxActivity.this.clearShellAttention(shellPid);
        }

        @Override public void showSessionSwitchIndicator(@Nullable String text) {
            TermuxActivity.this.showSessionSwitchIndicator(text);
        }

        @Override public void syncBackgroundProcessStack() {
            TermuxActivity.this.syncBackgroundProcessStack();
        }

        @Override @NonNull public Sessions sessions() {
            return mDrawerSessionsSurface;
        }

        @Override public void rebuildDrawerSessions() {
            TermuxActivity.this.rebuildDrawerSessions();
        }

        @Override public void notifySessionListUpdated() {
            termuxSessionListNotifyUpdated();
        }

        @Override public boolean activateSessionInPanes(TerminalSession session) {
            return TermuxActivity.this.activateSessionInPanes(session);
        }

        @Override public void captureTerminalDeparture() {
            TermuxActivity.this.captureTerminalDeparture();
        }

        @Override public void animateSessionArrival(int direction) {
            animateTerminalSessionArrival(direction);
        }

        @Override public void animateSessionLifecycleArrival(int direction) {
            animateTerminalSessionLifecycleArrival(direction);
        }

        @Override public void onWindowEmptied(
                com.termux.app.terminal.TerminalPaneController.Window window) {
            TermuxActivity.this.onWindowEmptied(window);
        }

        @Override public void closeCurrentSession() {
            TermuxActivity.this.closeCurrentSession();
        }

        @Override public boolean cloneCurrentBrowserSession() {
            return TermuxActivity.this.cloneCurrentBrowserSession();
        }

        @Override public boolean resetCurrentSession() {
            return TermuxActivity.this.resetCurrentSession();
        }

        @Override public void showSessionBrowser() {
            com.termux.app.terminal.TerminalSessionBrowser.show(TermuxActivity.this);
        }

        @Override public void toggleSessionsPanel() {
            TermuxActivity.this.toggleSessionsPanel();
        }

        @Override public boolean isSessionsPanelShowing() {
            return TermuxActivity.this.isSessionsPanelShowing();
        }

        @Override public boolean renameCurrentSessionTo(@Nullable String name) {
            return TermuxActivity.this.renameCurrentSessionTo(name);
        }

        @Override @Nullable public String currentSessionName() {
            return getCurrentSessionName();
        }

        @Override public boolean renameBrowserSession(int index, @Nullable String name) {
            return TermuxActivity.this.renameBrowserSession(index, name);
        }

        @Override @Nullable public String browserSessionName(int index) {
            return getBrowserSessionName(index);
        }

        @Override public void createNewWindow() {
            TermuxActivity.this.createNewWindow();
        }

        @Override public void closeCurrentWindow() {
            TermuxActivity.this.closeCurrentWindow();
        }

        @Override public void switchWindow(boolean forward) {
            TermuxActivity.this.switchWindow(forward);
        }

        @Override public boolean selectWindow(int index) {
            return TermuxActivity.this.selectWindow(index);
        }

        @Override public int currentWindowCount() {
            return getCurrentWindowCount();
        }

        @Override public int currentWindowIndex() {
            return getCurrentWindowIndex();
        }

        @Override public boolean promptCurrentWindowRename() {
            return TermuxActivity.this.promptCurrentWindowRename();
        }

        @Override public boolean renameCurrentWindowTo(@Nullable String name) {
            return TermuxActivity.this.renameCurrentWindowTo(name);
        }

        @Override @Nullable public String currentWindowName() {
            return getCurrentWindowName();
        }

        @Override public boolean beginTerminalRename(
                @NonNull com.termux.app.terminal.rename.TerminalRenameTarget target) {
            return TermuxActivity.this.beginTerminalRename(target);
        }

        @Override public void openDrawer() {
            getDrawer().openDrawer(android.view.Gravity.LEFT);
        }

        @Override public void closeDrawers() {
            getDrawer().closeDrawers();
        }

        @Override @NonNull public com.termux.app.terminal.TerminalWorkspace saveWorkspace(
                @NonNull String requestedName, boolean overwrite, boolean captureCommands)
                throws com.termux.app.terminal.TerminalWorkspace.WorkspaceException {
            return TermuxActivity.this.saveWorkspace(requestedName, overwrite, captureCommands);
        }

        @Override @NonNull
        public com.termux.app.terminal.TerminalWorkspace.LoadResult loadWorkspace(
                @NonNull String name, boolean replace, boolean runCommands)
                throws com.termux.app.terminal.TerminalWorkspace.WorkspaceException {
            return TermuxActivity.this.loadWorkspace(name, replace, runCommands);
        }

        @Override @NonNull
        public java.util.List<com.termux.app.terminal.TerminalWorkspaceStore.Entry> listWorkspaces()
                throws com.termux.app.terminal.TerminalWorkspace.WorkspaceException {
            return TermuxActivity.this.listWorkspaces();
        }

        @Override public void deleteWorkspace(@NonNull String name)
                throws com.termux.app.terminal.TerminalWorkspace.WorkspaceException {
            TermuxActivity.this.deleteWorkspace(name);
        }

        @Override public void showWorkspacePicker() {
            TermuxActivity.this.showWorkspacePicker();
        }

        @Override public void promptSaveWorkspace() {
            TermuxActivity.this.promptSaveWorkspace();
        }

        @Override public void openWallpaperPicker() {
            TermuxActivity.this.openWallpaperPicker();
        }

        @Override public boolean toggleWallpaperMode() {
            return TermuxActivity.this.toggleWallpaperMode();
        }

        @Override public boolean isWallpaperModeEnabled() {
            return TermuxActivity.this.isWallpaperModeEnabled();
        }

        @Override public boolean toggleCursorTrail() {
            return TermuxActivity.this.toggleCursorTrail();
        }

        @Override public boolean isCursorTrailEnabled() {
            return TermuxActivity.this.isCursorTrailEnabled();
        }

        @Override public void openSurfaceEditor() {
            TermuxActivity.this.openSurfaceEditor();
        }

        @Override public void updateWindowBackgroundForCurrentSession() {
            TermuxActivity.this.updateWindowBackgroundForCurrentSession();
        }

        @Override public void openSettings() {
            TermuxActivity.this.openSettings();
        }

        @Override public void openLookAndFeel() {
            TermuxActivity.this.openLookAndFeel();
        }

        @Override public void openAppsBar() {
            TermuxActivity.this.openAppsBar();
        }

        @Override public void showCommandPalette() {
            com.termux.app.terminal.TerminalCommandPalette.show(TermuxActivity.this);
        }

        @Override public void showExtraKeysRowEditor() {
            TermuxActivity.this.showExtraKeysRowEditor();
        }

        @Override public boolean toggleKeyInspector() {
            return com.termux.app.terminal.TerminalKeyInspector.toggle(TermuxActivity.this);
        }

        @Override public void showTextInputDialog(int titleRes, @Nullable String initialText,
                                                  int confirmRes,
                                                  @NonNull TextInputDialogUtils.TextSetListener onConfirm) {
            TextInputDialogUtils.textInput(TermuxActivity.this, titleRes, initialText, confirmRes,
                onConfirm, -1, null, -1, null, null);
        }

        @Override public void resetTerminalPerformanceMetrics() {
            TermuxActivity.this.resetTerminalPerformanceMetrics();
        }

        @Override @NonNull
        public com.termux.app.terminal.TerminalFrameMetricsMonitor.Snapshot frameMetricsSnapshot() {
            return getTerminalFrameMetricsSnapshot();
        }

        @Override @Nullable public TermuxTerminalSessionActivityClient sessionClient() {
            return getTermuxTerminalSessionClient();
        }

        @Override @Nullable public TermuxTerminalViewClient viewClient() {
            return getTermuxTerminalViewClient();
        }
    }

    /** The drawer-visible session list, as the terminal clients see it. */
    private final com.termux.app.terminal.TerminalHost.Sessions mDrawerSessionsSurface =
        new com.termux.app.terminal.TerminalHost.Sessions() {

            @Override public int count() {
                return mDrawerSessions.size();
            }

            @Override @Nullable public TerminalSession at(int index) {
                if (index < 0 || index >= mDrawerSessions.size()) return null;
                com.termux.shared.termux.shell.command.runner.terminal.TermuxSession termuxSession =
                    mDrawerSessions.get(index);
                return termuxSession == null ? null : termuxSession.getTerminalSession();
            }

            @Override public int indexOf(@Nullable TerminalSession session) {
                return getDrawerIndexOfSession(session);
            }

            @Override @Nullable public TerminalSession currentTabPrimary() {
                return getCurrentTabPrimary();
            }

            @Override public int currentNumber() {
                return getCurrentSessionNumber();
            }

            @Override public int numberOf(@Nullable TerminalSession shell) {
                return getSessionNumberFor(shell);
            }

            @Override @Nullable public String nameOf(@Nullable TerminalSession shell) {
                return getSessionNameFor(shell);
            }

            @Override @Nullable public android.widget.ListView listView() {
                return findViewById(R.id.terminal_sessions_list);
            }
        };

    /** The single host the terminal clients and the action dispatcher share. */
    @NonNull
    com.termux.app.terminal.TerminalHost terminalHost() {
        if (mTerminalHost == null) mTerminalHost = new ActivityTerminalHost();
        return mTerminalHost;
    }

    /** The view client wired to this activity through {@link ActivityTerminalHost}. */
    @NonNull
    TermuxTerminalViewClient createTermuxTerminalViewClient(
            TermuxTerminalSessionActivityClient sessionClient) {
        return new TermuxTerminalViewClient(this, terminalHost(), sessionClient);
    }

    /** The session client wired to this activity through {@link ActivityTerminalHost}. */
    @NonNull
    TermuxTerminalSessionActivityClient createTermuxTerminalSessionClient() {
        return new TermuxTerminalSessionActivityClient(this, terminalHost());
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

    void updateWindowBackgroundForCurrentSession() {
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
        mChrome.requestSync(ChromeRenderer.SCOPE_BACKDROPS | ChromeRenderer.SCOPE_KEYBOARD_BACKDROP);
        // Returning from another app can restore focus before the terminal host re-measures to full
        // size, leaving panes stuck at a tiny stale grid. Re-measure once layout settles.
        if (mPaneController != null)
            mPaneController.refreshPaneSizes();
        mChrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER | ChromeRenderer.SCOPE_BLUR_HEALTH);
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
        mKeyboardGeometry.resetImeVisibility(isImeVisible());
        mAccessoryKeyboardLayoutListener = () -> {
            boolean imeVisible = isImeVisible();
            if (mKeyboardGeometry.onImeVisibilityProbed(imeVisible)) {
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
                if (height != mKeyboardGeometry.desiredHeightPx()) {
                    v.post(() -> {
                        if (!isFinishing() && !isDestroyed()
                            && v.getHeight() != mKeyboardGeometry.desiredHeightPx()) {
                            mKeyboardGeometry.discardMeasuredHeight();
                            applyAccessoryGeometryIfNeeded(true, "inapp-keyboard:height");
                        }
                    });
                } else {
                    mChrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);
                }
                return;
            }
            mChrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);
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

    /** The drawer dismissed a visible system IME on engage; restored when the drawer goes. */
    private boolean mSystemImeHiddenForDrawer;

    private boolean isImeVisible() {
        View content = findViewById(android.R.id.content);
        if (content == null) {
            return false;
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
        return insets != null && insets.isVisible(Type.ime());
    }

    private void onImeVisibilityChanged(boolean visible) {
        if (!visible && !mAzGesture.isActive()) {
            mSuggestionBarInteractionActive = false;
            if (mSuggestionBarView != null) {
                mSuggestionBarView.clearAzPreview();
            }
        }
        if (mTermuxTerminalSessionActivityClient != null) {
            mTermuxTerminalSessionActivityClient.onImeVisibilityChanged(visible);
        }
        applyAccessoryGeometryIfNeeded(true, visible ? "ime:open" : "ime:close");
        mChrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER | ChromeRenderer.SCOPE_BLUR_HEALTH);
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
        if (!hasOverflow && !mAzGesture.isActive() && !mSuggestionBarInteractionActive) {
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
        // A new scheme on disk invalidates the derived chrome palette whether or not the chrome is
        // currently following it — the cache is keyed on the file, and the user may switch the
        // source over afterwards without touching the file again.
        LauncherSchemeTheme.invalidate();
        if (mPreferences == null || !mPreferences.isTerminalDynamicColorsEnabled())
            return;
        if (!TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE.isFile())
            return;
        Logger.logInfo(LOG_TAG, "Termux:Styling wrote a colour scheme; disabling wallpaper colours");
        mPreferences.setTerminalDynamicColorsEnabled(false);
        // A second reason to drop the cache: that write also hands the chrome to the scheme, and
        // the recreate this broadcast triggers must theme from the new answer, not the old one.
        LauncherSchemeTheme.invalidate();
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
        mChrome.requestSync(ChromeRenderer.SCOPE_BACKDROPS);
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
