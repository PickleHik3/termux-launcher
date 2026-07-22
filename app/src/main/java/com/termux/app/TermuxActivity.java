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
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
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
import com.termux.app.launcher.animation.LauncherTransitionController;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.LauncherLockAccessibilityAccess;
import com.termux.app.launcher.LockAccessibilityService;
import com.termux.app.launcher.TerminalAppSearchKeyDecision;
import com.termux.launcherctl.LauncherCtlApiServer;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.ShizukuBackend;
import com.termux.app.terminal.AccessoryStackLayoutPolicy;
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
import com.termux.app.activities.OnboardingActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.theme.TermuxThemeManager;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
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
import java.util.Set;
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

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     * Split-pane (Phase 1 spike): the second {@link TerminalView}. Null-safe everywhere;
     * remains hidden until a second session is attached via {@link #ensureSecondPaneSession()}.
     */
    TerminalView mTerminalView2;

    /** The pane that currently has focus. Backs {@link #getTerminalView()}. */
    TerminalView mActivePane;

    // ---- Split-pane model ----
    // A "tab" = one primary session, shown in the drawer. A tab may own one secondary
    // pane session (2-pane split). Secondary sessions are hidden from the drawer.
    /** primary session -> its secondary pane session. */
    private final java.util.Map<TerminalSession, TerminalSession> mTabSecondary = new java.util.HashMap<>();
    /** primary session -> pane container orientation (LinearLayout.VERTICAL/HORIZONTAL). */
    private final java.util.Map<TerminalSession, Integer> mTabOrientation = new java.util.HashMap<>();
    /** Sessions that are a secondary pane; excluded from the drawer list. */
    private final java.util.Set<TerminalSession> mSecondaryPaneSessions = new java.util.HashSet<>();
    /** Primary session of the currently displayed tab. */
    @Nullable private TerminalSession mCurrentTabPrimary;
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
    private boolean mDockTuningMode;
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
    ExtraKeysView mExtraKeysView;
    ExtraKeysView mExtraKeysView2;

    SuggestionBarView mSuggestionBarView;
    private boolean mSuggestionBarExplicitSearchActive;
    AzScrubRowView mAzScrubRowView;
    @Nullable private View mAzTerminalToolbarView;
    LauncherAzGestureFxView mLauncherAzGestureFxUnderlayView;
    LauncherAzGestureFxView mLauncherAzGestureFxOverlayView;
    LauncherAzGestureFxView mLauncherAzGestureFxLabelOverlayView;

    private LauncherAppDataProvider mLauncherAppDataProvider;
    private LauncherConfigRepository mLauncherConfigRepository;
    private LauncherTransitionController mLauncherTransitionController;
    private int mLastLauncherIconPreferencesSignature = Integer.MIN_VALUE;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys2;

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
    @Nullable private LauncherApps mLauncherApps;
    @Nullable private LauncherApps.Callback mLauncherAppsCallback;
    private boolean mLauncherAppsCallbackRegistered = false;
    private static final long PACKAGE_REFRESH_DEBOUNCE_MS = 120L;
    private static final long LAUNCHER_CATALOG_WARM_DELAY_MS = 450L;
    private boolean mPackageRefreshForceCatalogReload = false;
    private int mLastLauncherCatalogSignature = Integer.MIN_VALUE;
    private int mLastLauncherIconDayKey = Integer.MIN_VALUE;
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
    @Nullable private AlertDialog mTerminalActionDialog;

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
    private boolean mShouldLaunchOnboardingAfterBootstrap = false;

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

    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;

    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;

    private static final int CONTEXT_MENU_SET_WALLPAPER_ID = 2;

    private static final int CONTEXT_MENU_REMOVE_WALLPAPER_ID = 3;

    private static final int CONTEXT_MENU_LOOK_AND_FEEL_ID = 4;

    private static final int CONTEXT_MENU_APPS_BAR_ID = 5;

    private static final int CONTEXT_MENU_SETTINGS_ID = 6;

    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 7;

    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 8;

    private static final int CONTEXT_MENU_GLASS_LAB_ID = 9;

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

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";

    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";
    private static final String ARG_SHOULD_LAUNCH_ONBOARDING = "should_launch_onboarding";

    private static final String LOG_TAG = "TermuxActivity";
    private static final int IN_APP_KEYBOARD_MARGIN_SLIDER_STEPS_PER_UNIT = 100;
    private static final int IN_APP_KEYBOARD_RADIUS_SLIDER_STEPS_PER_DP = 10;
    private static final int ACCESSORY_BLUR_DOWNSAMPLE_FACTOR = 4;
    private static final long ACCESSORY_BLUR_BACKSTOP_MS = 300_000L;
    private static volatile boolean sPendingStyleReloadOnNextResume = false;

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
    /** Keeps a unified glass keyboard hidden until the expanded dock+keyboard crop is installed. */
    private boolean mPendingInAppKeyboardOpenReveal;
    /** Keeps the under-pill glass covering stale close geometry until dock-only layout settles. */
    private boolean mPendingInAppKeyboardCloseGeometry;
    private boolean mAccessoryBackdropDirty = true;
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
    /** One wallpaper-frame blur shared by dock, keyboard, and gesture-nav crops. */
    @Nullable private Bitmap mCachedAccessoryWallpaperBlurBitmap;
    @NonNull private final Rect mCachedAccessoryWallpaperBlurFrameRect = new Rect();
    private int mCachedAccessoryWallpaperBlurRadiusDp = -1;
    private boolean mCachedAccessoryWallpaperBlurManagedSource;
    private int mCachedAccessoryWallpaperBlurSystemId = -1;
    private long mCachedAccessoryWallpaperBlurManagedLastModified = -1L;
    private long mCachedAccessoryWallpaperBlurManagedLength = -1L;
    @Nullable private Drawable mManagedWallpaperWindowBackground;
    private long mManagedWallpaperWindowBackgroundLastModified = -1L;
    private long mManagedWallpaperWindowBackgroundLength = -1L;
    @Nullable private Drawable mSystemWallpaperWindowBackground;
    private int mSystemWallpaperWindowBackgroundId = -1;
    @Nullable private WallpaperManager.OnColorsChangedListener mWallpaperColorsChangedListener;
    private final Handler mAccessoryRenderHandler = new Handler(Looper.getMainLooper());
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
        if (savedInstanceState == null) {
            mShouldLaunchOnboardingAfterBootstrap = OnboardingActivity.prepareAutomaticLaunch(this);
        } else {
            mShouldLaunchOnboardingAfterBootstrap = savedInstanceState.containsKey(
                ARG_SHOULD_LAUNCH_ONBOARDING)
                ? savedInstanceState.getBoolean(ARG_SHOULD_LAUNCH_ONBOARDING)
                : OnboardingActivity.prepareAutomaticLaunch(this);
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);
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
        setTerminalToolbarView(savedInstanceState);
        initializeInAppKeyboard(savedInstanceState);
        // Only a fresh launch may enter adjust mode: after process death the system re-delivers
        // the original launch intent with the extra still set, which must not re-enter it.
        if (savedInstanceState == null) {
            handleInAppKeyboardHeightAdjustIntent(getIntent());
            handleDockTuningIntent(getIntent());
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
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInAppKeyboardHeightAdjustIntent(intent);
        handleDockTuningIntent(intent);
        if (isLauncherHomeIntent(intent)) {
            mLastLaunchWasLauncherEntry = true;
        }
        if (mLauncherTransitionController != null) {
            mLauncherTransitionController.maybeHandleGestureContract(intent, mSuggestionBarView);
        }
        if (isLauncherHomeIntent(intent)) {
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
        scheduleOnboardingIfReady();
    
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

        syncTerminalWallpaperRenderingMode();
        applySeamlessStatusBackgroundModeIfNeeded();
        applyTerminalSurfaceAppearance();
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
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.refreshMaterialTerminalColors(true);
        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();
        refreshLauncherIconsIfPreferencesChanged();
        maybeRecoverFromEmptySession("onResume");
        // If compatibility mode was just enabled, drop any active split back to a single pane.
        if (!isSplitPanesEnabled())
            collapseAllSplits();

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
                    mTermuxTerminalSessionActivityClient.refreshMaterialTerminalColors(true);
                }
                applyTerminalSurfaceAppearance();
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
            return resolveMonetDarkBackgroundColor();
        }
        return getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh, R.color.termux_surface_panel_high);
    }

    private int resolveMonetDarkBackgroundColor() {
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

    /** Wallpaper-derived accent (Monet primary) used across the dock's reactive glass treatment. */
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
        int grain = mPreferences != null
            ? mPreferences.getDockGlassGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN;
        if (grain > 0) {
            layers.add(buildDockGrainLayer(grain));
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

    /** Cached static monochrome noise tile for the dock-glass grain. */
    @Nullable private Bitmap mDockGrainBitmap;

    @NonNull
    private Bitmap getDockGrainBitmap() {
        if (mDockGrainBitmap == null) {
            int size = 110;
            int[] px = new int[size * size];
            // Fixed seed -> stable grain across rebuilds (no shimmer when the dock repositions).
            java.util.Random rnd = new java.util.Random(0x6A11E);
            for (int i = 0; i < px.length; i++) {
                int v = rnd.nextInt(256);        // monochrome speck luminance
                int a = rnd.nextInt(256);        // sparse alpha -> film grain, not a flat gray wash
                px[i] = (a << 24) | (v << 16) | (v << 8) | v;
            }
            mDockGrainBitmap = Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        }
        return mDockGrainBitmap;
    }

    /** A tiled grain layer whose strength is controlled only by the grain preference. */
    @NonNull
    private Drawable buildDockGrainLayer(int grainPercent) {
        BitmapDrawable grain = new BitmapDrawable(getResources(), getDockGrainBitmap());
        grain.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        grain.setDither(true);
        // Cap the strength so even at 100% it stays a texture, not visual static.
        grain.setAlpha(dockGlassGrainAlpha(grainPercent));
        return grain;
    }

    static int dockGlassGrainAlpha(int grainPercent) {
        return Math.round(Math.max(0, Math.min(100, grainPercent)) / 100f * 60f);
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
            float radius = isValarieDockStyle() ? resolveDockCapsuleCornerRadiusPx(surfaceHeightPx) : 0f;
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
        boolean capsuleDock = isValarieDockStyle();
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

        boolean capsule = isValarieDockStyle();
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
            () -> capsule == isValarieDockStyle()
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
        if (isNightThemeActive()) {
            return getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        }
        return Color.parseColor("#1C1B1F");
    }

    private int resolveTerminalSurfaceColor() {
        int baseColor = shouldUseWallpaperPassthroughMode()
            ? resolveTerminalOverlayBaseColor()
            : getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base);
        int alpha = Math.round(resolveOpacityAlpha(
            mPreferences != null ? mPreferences.getTerminalBackgroundOpacity() : 100
        ) * 255f);
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
    private Drawable buildValarieAmbientVeil(float surfaceAlpha, boolean decorLayer) {
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
        if (!state.toolbarShown || !isValarieDockStyle()) {
            accessoryContainer.setBackground(null);
            return;
        }
        accessoryContainer.setBackground(buildValarieAmbientVeil(state.barAlpha, false));
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
        boolean capsuleSurface = viewId == R.id.accessory_surface_host && isValarieDockStyle();
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
        if (capsuleSurface && targetWidth > 0) {
            int horizontalMargin = resolveDockCapsuleHorizontalMarginPx();
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

    private boolean isValarieDockStyle() {
        return mPreferences != null
            && TermuxPreferenceConstants.TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_VALARIE_CAPSULE.equals(
                mPreferences.getAppLauncherDockStyle()
            );
    }

    private int resolveDockCapsuleHorizontalMarginPx() {
        // Floating capsule floats 10dp off the screen edges (design redline · Outer margin 10).
        return Math.round(dpToPx(10));
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

    private float resolveDockCapsuleCornerRadiusPx(int surfaceHeightPx) {
        // Capsule radius 26 (design redline · Card radius 26).
        float maxRadius = dpToPx(26);
        return Math.max(dpToPx(16), Math.min(maxRadius, surfaceHeightPx / 2f));
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
        final float barAlpha;
        final int blurRadiusDp;

        AccessoryRenderState(boolean toolbarShown, boolean keyboardShown, int keyboardHeight,
                             boolean blurEnabled, boolean appsRowEnabled, boolean azRowEnabled,
                             float barAlpha, int blurRadiusDp) {
            this.toolbarShown = toolbarShown;
            this.keyboardShown = keyboardShown;
            this.keyboardHeight = Math.max(0, keyboardHeight);
            this.blurEnabled = blurEnabled;
            this.appsRowEnabled = appsRowEnabled;
            this.azRowEnabled = azRowEnabled;
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

        int combinedHeight(int toolbarHeightPx) {
            return AccessoryStackLayoutPolicy.computeCombinedHeight(
                toolbarHeightPx,
                appsBarHeightPx,
                azRowHeightPx,
                indicatorBandHeightPx
            );
        }
    }

    @NonNull
    private AccessoryRenderState buildAccessoryRenderState() {
        boolean keyboardShown = isInAppKeyboardShown();
        int keyboardHeight = keyboardShown ? measureInAppKeyboardHeight() : 0;
        if (mPreferences == null) {
            return new AccessoryRenderState(false, keyboardShown, keyboardHeight,
                false, false, false, 1.0f, 0);
        }
        boolean appsRowEnabled = mPreferences.isAppLauncherAppsRowEnabled();
        int blurRadiusDp = getEffectiveExtraKeysBlurRadius();
        float barAlpha = mPreferences.getAppBarOpacity() / 100f;
        return new AccessoryRenderState(
            mPreferences.shouldShowTerminalToolbar(),
            keyboardShown,
            keyboardHeight,
            dockBlurEnabled(blurRadiusDp),
            appsRowEnabled,
            appsRowEnabled && mPreferences.isAppLauncherAzRowEnabled(),
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

    private boolean isLiveWallpaperActive() {
        try {
            WallpaperInfo wallpaperInfo = WallpaperManager.getInstance(this).getWallpaperInfo();
            return wallpaperInfo != null;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to detect live wallpaper state", e);
            return false;
        }
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
            mNavBarHeight, mLastImeVisible || isImeVisible(), isValarieDockStyle(),
            isInAppKeyboardCapsule());
    }

    static boolean shouldShowDecorNavBarSurface(boolean toolbarShown, boolean keyboardShown,
                                                int navBarHeight, boolean imeVisible,
                                                boolean valarieDockStyle,
                                                boolean keyboardCapsule) {
        if (navBarHeight <= 0 || imeVisible)
            return false;
        if (keyboardShown)
            return !keyboardCapsule;
        return toolbarShown && !valarieDockStyle;
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
        return shouldUseUnifiedDefaultKeyboardGlassSurface(state.toolbarShown,
            state.keyboardShown, isValarieDockStyle(), isInAppKeyboardGlassSurface());
    }

    static boolean shouldUseUnifiedDefaultKeyboardGlassSurface(boolean toolbarShown,
                                                                boolean keyboardShown,
                                                                boolean valarieDockStyle,
                                                                boolean keyboardGlassSurface) {
        return toolbarShown && keyboardShown && !valarieDockStyle && keyboardGlassSurface;
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
        if (isValarieDockStyle()) {
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

    /** True when the dock-match mode renders the keyboard on the dock's glass surface. */
    private boolean isInAppKeyboardGlassSurface() {
        if (mPreferences == null)
            return false;
        String mode = mPreferences.getInAppKeyboardDockMatch();
        return "glass".equals(mode) || "both".equals(mode);
    }

    /**
     * Fraction of the combined content+under-pill glass height occupied by the in-content surface
     * (the keyboard host when the keyboard is shown, otherwise the dock stack). The content surface
     * and the under-pill nav strip render adjacent slices ({@code [0, f]} and {@code [f, 1]}) of one
     * shared light model so a single dark foot lands under the pill in both keyboard states. Returns
     * 1 (no slicing) for the floating capsule or when there is no gesture-nav strip below.
     */
    private float defaultDockGlassFootFraction() {
        if (isValarieDockStyle()) {
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

    /** True when the dock-match mode makes the keyboard shape follow the dock style. */
    private boolean isInAppKeyboardShapeMatch() {
        if (mPreferences == null)
            return false;
        String mode = mPreferences.getInAppKeyboardDockMatch();
        return "shape".equals(mode) || "both".equals(mode);
    }

    /** True when the keyboard renders as a floating capsule (shape match under Valarie). */
    private boolean isInAppKeyboardCapsule() {
        return isValarieDockStyle() && isInAppKeyboardShapeMatch();
    }

    /**
     * Applies the in-app keyboard's surface treatment: capsule shape (margins + rounded clip +
     * inner padding) when the Valarie dock style is active, and the dock's blurred-wallpaper +
     * tinted-glass stack behind the keys when the dock-match mode enables glass. The glass stack is
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
        int horizontalMargin = capsule ? resolveDockCapsuleHorizontalMarginPx() : 0;
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
            // Render only the keyboard's slice of the shared light model; the under-pill nav strip
            // renders the remainder so the single foot lands under the pill (see the slice overload).
            layers.add(buildDockGlassSurface(state.barAlpha, 0f, defaultDockGlassFootFraction(), false));
        } else if (capsule) {
            // Opaque themes fill the capsule with the keyboard's own background color so the
            // inner padding ring stays seamless with the keys.
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(resolveInAppKeyboardBackgroundColor());
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
            || previousBackdrop == mCachedAccessoryWallpaperBlurBitmap
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

        Bitmap blurredBackdrop = createCachedAccessoryWallpaperBlurCrop(state, targetRect, wallpaperFrame);
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
        int seamOverscanPx = !isValarieDockStyle() && shouldShowDecorNavBarSurface(state)
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
        int topOverscanPx = !isValarieDockStyle()
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

        Bitmap wallpaperBackdrop = createCachedAccessoryWallpaperBlurCrop(state, targetRect, wallpaperFrame);
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
                return isValarieDockStyle() ? accessoryHealthy && decorHealthy : decorHealthy;
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
            return null;
        }
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

        float downsampleFactor = ACCESSORY_BLUR_DOWNSAMPLE_FACTOR;
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
    private Bitmap obtainCachedAccessoryWallpaperBlur(@NonNull AccessoryRenderState state,
                                                       @NonNull View wallpaperFrame) {
        Rect frameRect = getManagedWallpaperFrameRect();
        boolean managedSource = shouldUseManagedWallpaperBlurSource();
        int systemWallpaperId = getCurrentSystemWallpaperId();
        File managedFile = managedSource ? getManagedWallpaperExactFile() : null;
        long managedLastModified = managedFile != null ? managedFile.lastModified() : -1L;
        long managedLength = managedFile != null ? managedFile.length() : -1L;
        if (mCachedAccessoryWallpaperBlurBitmap != null
            && !mCachedAccessoryWallpaperBlurBitmap.isRecycled()
            && mCachedAccessoryWallpaperBlurRadiusDp == state.blurRadiusDp
            && mCachedAccessoryWallpaperBlurManagedSource == managedSource
            && mCachedAccessoryWallpaperBlurSystemId == systemWallpaperId
            && mCachedAccessoryWallpaperBlurManagedLastModified == managedLastModified
            && mCachedAccessoryWallpaperBlurManagedLength == managedLength
            && mCachedAccessoryWallpaperBlurFrameRect.equals(frameRect)) {
            return mCachedAccessoryWallpaperBlurBitmap;
        }

        Bitmap wallpaperBitmap = createWallpaperBackdropBitmapForRect(frameRect, wallpaperFrame);
        if (wallpaperBitmap == null) {
            return null;
        }
        Bitmap blurredBitmap = createPreBlurredWallpaperBackdropBitmap(wallpaperBitmap, state.blurRadiusDp);
        if (blurredBitmap == null) {
            wallpaperBitmap.recycle();
            return null;
        }
        if (blurredBitmap != wallpaperBitmap) {
            wallpaperBitmap.recycle();
        }
        clearCachedAccessoryWallpaperBlur();
        mCachedAccessoryWallpaperBlurBitmap = blurredBitmap;
        mCachedAccessoryWallpaperBlurFrameRect.set(frameRect);
        mCachedAccessoryWallpaperBlurRadiusDp = state.blurRadiusDp;
        mCachedAccessoryWallpaperBlurManagedSource = managedSource;
        mCachedAccessoryWallpaperBlurSystemId = systemWallpaperId;
        mCachedAccessoryWallpaperBlurManagedLastModified = managedLastModified;
        mCachedAccessoryWallpaperBlurManagedLength = managedLength;
        return blurredBitmap;
    }

    /** Crops the shared full-frame blur in screen coordinates, clamping any overscan at its edges. */
    @Nullable
    private Bitmap createCachedAccessoryWallpaperBlurCrop(@NonNull AccessoryRenderState state,
                                                           @NonNull Rect targetRect,
                                                           @NonNull View wallpaperFrame) {
        Bitmap fullBlur = obtainCachedAccessoryWallpaperBlur(state, wallpaperFrame);
        if (fullBlur == null) {
            return null;
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

    private void clearCachedAccessoryWallpaperBlur() {
        if (mCachedAccessoryWallpaperBlurBitmap != null
            && !mCachedAccessoryWallpaperBlurBitmap.isRecycled()) {
            mCachedAccessoryWallpaperBlurBitmap.recycle();
        }
        mCachedAccessoryWallpaperBlurBitmap = null;
        mCachedAccessoryWallpaperBlurFrameRect.setEmpty();
        mCachedAccessoryWallpaperBlurRadiusDp = -1;
        mCachedAccessoryWallpaperBlurManagedSource = false;
        mCachedAccessoryWallpaperBlurSystemId = -1;
        mCachedAccessoryWallpaperBlurManagedLastModified = -1L;
        mCachedAccessoryWallpaperBlurManagedLength = -1L;
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
        if (shouldUseDockDecorNavBarSurface(state) && !isValarieDockStyle()) {
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
        int seamOverscanPx = !isValarieDockStyle() && shouldShowDecorNavBarSurface(state)
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
            state, backdropTargetRect, wallpaperFrame);
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
            float radiusPx = isValarieDockStyle()
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
            terminalToolbarViewPager.setVisibility(View.VISIBLE);
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
            extraKeysBackground.setVisibility(useDecorSurface && !isValarieDockStyle() ? View.GONE : View.VISIBLE);
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
        configureExtraKeysDivider(state.appsRowEnabled, state.barAlpha);
        applyDecorNavBarSurfaceState(state);
        applyInAppKeyboardSurfaceState(state);
        updateAccessoryRenderEffectBackdrop(state);
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
        int seamOverscanPx = !isValarieDockStyle() && shouldShowDecorNavBarSurface(state)
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

        applyTerminalSurfaceAppearance();
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
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(
            getWindow(), getWindow().getDecorView());
        if (mProperties.isUsingFullScreen()) {
            insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            insetsController.hide(Type.systemBars());
        } else {
            insetsController.show(Type.systemBars());
        }
    }

    private void applySeamlessStatusBackgroundModeIfNeeded() {
        boolean enable = shouldEnableSeamlessStatusBackground();
        if (mSeamlessStatusBackgroundActive == enable) {
            return;
        }
        mSeamlessStatusBackgroundActive = enable;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), !enable);

        if (mTermuxActivityRootView != null) {
            mTermuxActivityRootView.setClipToPadding(!enable);
            mTermuxActivityRootView.setClipChildren(!enable);
        }
        View terminalRootContainer = findViewById(R.id.terminal_root_container);
        if (terminalRootContainer instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) terminalRootContainer;
            container.setClipToPadding(!enable);
            container.setClipChildren(!enable);
        }

        resetRootBottomMarginAfterEdgeModeToggle();
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
        stopAzEdgePagingLoop();
        cancelAzOverflowRefresh();
        if (mIsInvalidState)
            return;
        mIsVisible = false;
        if (mDockPlankController != null) {
            mDockPlankController.reset();
        }
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
        mAccessoryRenderHandler.removeCallbacks(mAccessoryRenderSyncRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurHeartbeatRunnable);
        mAccessoryRenderHandler.removeCallbacks(mAccessoryBlurRecoveryRunnable);
        mAccessoryRenderSyncPending = false;
        mPendingInAppKeyboardCloseGeometry = false;
        removeInAppKeyboardOpenPreDrawGate();
        removeInAppKeyboardClosePreDrawCorrection();
        applyDockImeOffset(0);
        clearAccessoryRenderEffectBackdrop();
        hideDecorNavBarSurfaceOverlay(true);
        mAzGestureHandler.removeCallbacks(mPackageRefreshRunnable);
        mAzGestureHandler.removeCallbacks(mLauncherCatalogWarmRunnable);
        getDrawer().closeDrawers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logDebug(LOG_TAG, "onDestroy");
        clearCachedAccessoryWallpaperBlur();
        if (mIsInvalidState)
            return;
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
        savedInstanceState.putBoolean(ARG_SHOULD_LAUNCH_ONBOARDING,
            mShouldLaunchOnboardingAfterBootstrap);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Logger.logVerbose(LOG_TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.refreshMaterialTerminalColors(true);
        if (mInAppKeyboard != null) {
            mInAppKeyboardShiftLocked = false;
            mInAppKeyboard.onConfigurationChanged(newConfig);
        }
        updateWindowBackgroundForCurrentSession();
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
        scheduleOnboardingIfReady();
    }

    private void startBootstrapAndSession(@Nullable Intent intent) {
        TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
            // Bootstrap setup may complete after app startup; re-attempt launcher CLI script install.
            LauncherCtlApiServer.getInstance().ensureCliScriptsInstalled();

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
                scheduleOnboardingIfReady();
            } catch (WindowManager.BadTokenException e) {
                // Activity finished - ignore.
                mEmptySessionRecoveryInProgress = false;
            }
        });
    }

    private void scheduleOnboardingIfReady() {
        if (!mShouldLaunchOnboardingAfterBootstrap || !mIsVisible || mTermuxService == null
            || mTermuxService.isTermuxSessionsEmpty() || isFinishing()) {
            return;
        }
        mShouldLaunchOnboardingAfterBootstrap = false;
        View content = findViewById(android.R.id.content);
        if (content != null) {
            content.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    ActivityUtils.startActivity(this, OnboardingActivity.createIntent(this));
                }
            }, 350L);
        }
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
                mLauncherConfigRepository = new LauncherConfigRepository(mPreferences);
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
        mSuggestionBarView.setAppCatalogChangedListener(this::syncAzScrubLettersAndTint);
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
        applySuggestionBarPreferences();
        applyDockLayoutMetrics(buildDockLayoutMetrics(0));
        if (isSuggestionBarEnabled()) {
            mSuggestionBarView.reload();
        }
        mSuggestionBarView.post(() -> {
            if (mSuggestionBarView == null || !mIsVisible || !isSuggestionBarEnabled()) {
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
            && shouldShowInRecents(mPreferences.isRemoveTaskOnActivityFinishEnabled(), isDefaultHomeApp());
    }

    private void syncRecentsVisibilityPolicy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        boolean excludeFromRecents = !shouldShowInRecents();
        try {
            if (getTaskId() != -1) {
                for (android.app.ActivityManager.AppTask appTask : getSystemService(android.app.ActivityManager.class).getAppTasks()) {
                    if (appTask == null || appTask.getTaskInfo() == null || appTask.getTaskInfo().taskId != getTaskId()) {
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
        return isSuggestionBarEnabled() && mPreferences.isAppLauncherAzRowEnabled();
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
            mLauncherConfigRepository = new LauncherConfigRepository(mPreferences);
        }
        int iconPreferencesSignature = computeLauncherIconPreferencesSignature();
        if (mLastLauncherIconPreferencesSignature != Integer.MIN_VALUE
            && mLastLauncherIconPreferencesSignature != iconPreferencesSignature) {
            mLauncherAppDataProvider.invalidate();
            mSuggestionBarView.clearAppCache();
        }
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
        mSuggestionBarView.setAppCatalogChangedListener(this::syncAzScrubLettersAndTint);
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
        if (!isSuggestionBarEnabled()) {
            mSuggestionBarExplicitSearchActive = false;
            resetAzGestureState(false, true);
            resetAzOverflowAffordanceState();
            return;
        }
        mSuggestionBarView.reloadAllApps();
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
        int muted = mutedMonetShade(base);
        if (mAzScrubRowView.getCurrentTextColor() != muted) {
            mAzScrubRowView.setTextColor(muted);
        }
        mAzScrubRowView.setInteractionAccentColor(base);
        mAzScrubRowView.setInteractionMode(AzScrubRowView.InteractionMode.WAVE_TRACK);
        mAzScrubRowView.setLockedInlineLetter(null);
        int orbColor = brightMonetShade(base);
        int edgeColor = edgeMonetVariant(base);
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
        if (!isSuggestionBarEnabled()) {
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
        if (!isSuggestionBarEnabled()) {
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

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private int resolveAzGestureAccentColor() {
        return MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(this, R.color.termux_primary));
    }

    private int mutedMonetShade(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * 0.92f));
        hsv[2] = Math.max(0.78f, Math.min(1f, hsv[2] * 0.86f));
        return Color.HSVToColor(0xF4, hsv);
    }

    private int brightMonetShade(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * 1.28f));
        hsv[2] = Math.max(0f, Math.min(1f, Math.max(hsv[2], 0.90f)));
        return Color.HSVToColor(0xF6, hsv);
    }

    private int edgeMonetVariant(int color) {
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
        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);
        // Split-pane spike: the second view shares the same clients. All client callbacks
        // route through getTerminalView() (active pane) or per-session lookup, so a single
        // client pair drives both panes correctly.
        mTerminalView2 = findViewById(R.id.terminal_view_2);
        if (mTerminalView2 != null)
            mTerminalView2.setTerminalViewClient(mTermuxTerminalViewClient);
        mActivePane = mTerminalView;
        setupPaneFocusRouting();
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

    private void handleDockTuningIntent(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_DOCK_TUNING, false))
            return;
        intent.removeExtra(EXTRA_DOCK_TUNING);
        enterDockTuningMode();
    }

    private void enterDockTuningMode() {
        if (mPreferences == null)
            return;
        mDockTuningMode = true;
        View controls = findViewById(R.id.dock_tuning_controls);
        SeekBar blur = findViewById(R.id.dock_tuning_blur_slider);
        SeekBar opacity = findViewById(R.id.dock_tuning_opacity_slider);
        SeekBar grain = findViewById(R.id.dock_tuning_grain_slider);
        SeekBar terminal = findViewById(R.id.dock_tuning_terminal_slider);
        SeekBar sessions = findViewById(R.id.dock_tuning_sessions_slider);
        SeekBar size = findViewById(R.id.dock_tuning_size_slider);
        SeekBar icons = findViewById(R.id.dock_tuning_icons_slider);
        TextView blurValue = findViewById(R.id.dock_tuning_blur_value);
        TextView opacityValue = findViewById(R.id.dock_tuning_opacity_value);
        TextView grainValue = findViewById(R.id.dock_tuning_grain_value);
        TextView terminalValue = findViewById(R.id.dock_tuning_terminal_value);
        TextView sessionsValue = findViewById(R.id.dock_tuning_sessions_value);
        TextView sizeValue = findViewById(R.id.dock_tuning_size_value);
        TextView iconsValue = findViewById(R.id.dock_tuning_icons_value);
        MaterialButtonToggleGroup styleGroup = findViewById(R.id.dock_tuning_style_group);
        View confirm = findViewById(R.id.dock_tuning_confirm);
        View dismiss = findViewById(R.id.dock_tuning_dismiss);
        if (controls == null || blur == null || opacity == null || grain == null
            || terminal == null || sessions == null || size == null || icons == null
            || blurValue == null || opacityValue == null || grainValue == null
            || terminalValue == null || sessionsValue == null || sizeValue == null
            || iconsValue == null || styleGroup == null || confirm == null) {
            mDockTuningMode = false;
            return;
        }
        controls.setVisibility(View.VISIBLE);
        final int initialBlur = mPreferences.getExtraKeysBlurRadius();
        final int initialOpacity = mPreferences.getAppBarOpacity();
        final int initialGrain = mPreferences.getDockGlassGrain();
        final int initialTerminal = mPreferences.getTerminalBackgroundOpacity();
        final int initialSessions = mPreferences.getSessionsOpacity();
        final float initialBarHeight = mPreferences.getAppLauncherBarHeightScale();
        final int initialSizeIndex = nearestDockSizePresetIndex(initialBarHeight);
        final int initialButtonCount = mPreferences.getAppLauncherButtonCount();
        final String initialStyle = mPreferences.getAppLauncherDockStyle();

        blur.setProgress(initialBlur);
        opacity.setProgress(initialOpacity);
        grain.setProgress(initialGrain);
        terminal.setProgress(initialTerminal);
        sessions.setProgress(initialSessions);
        size.setProgress(initialSizeIndex);
        icons.setProgress(Math.max(1, Math.min(20, initialButtonCount)));
        blurValue.setText(getString(R.string.termux_dock_tuning_value_dp, initialBlur));
        opacityValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialOpacity));
        grainValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialGrain));
        terminalValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialTerminal));
        sessionsValue.setText(getString(R.string.termux_dock_tuning_value_percent, initialSessions));
        sizeValue.setText(dockSizePresetLabel(initialSizeIndex));
        iconsValue.setText(Integer.toString(Math.max(1, initialButtonCount)));
        styleGroup.check(SegmentedPillPreference.VALUE_CAPSULE.equals(initialStyle)
            ? R.id.dock_tuning_style_capsule : R.id.dock_tuning_style_default);

        blur.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                blurValue.setText(getString(R.string.termux_dock_tuning_value_dp, progress));
                if (fromUser) {
                    mPreferences.setExtraKeysBlurRadius(progress);
                    applyDockTuningPreview(true);
                }
            }
        });
        opacity.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacityValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    mPreferences.setAppBarOpacity(progress);
                    applyDockTuningPreview(false);
                }
            }
        });
        grain.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                grainValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    mPreferences.setDockGlassGrain(progress);
                    applyDockTuningPreview(false);
                }
            }
        });
        terminal.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                terminalValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    mPreferences.setTerminalBackgroundOpacity(progress);
                    applyDockTuningStructuralPreview();
                }
            }
        });
        sessions.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sessionsValue.setText(getString(R.string.termux_dock_tuning_value_percent, progress));
                if (fromUser) {
                    mPreferences.setSessionsOpacity(progress);
                    applyDockTuningStructuralPreview();
                }
            }
        });
        size.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int index = Math.max(0, Math.min(DOCK_TUNING_SIZE_PRESETS.length - 1, progress));
                sizeValue.setText(dockSizePresetLabel(index));
                if (fromUser) {
                    mPreferences.setAppLauncherBarHeightScale(DOCK_TUNING_SIZE_PRESETS[index]);
                    applyDockTuningStructuralPreview();
                }
            }
        });
        icons.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int count = Math.max(1, progress);
                iconsValue.setText(Integer.toString(count));
                if (fromUser) {
                    mPreferences.setAppLauncherButtonCount(count);
                    applyDockTuningStructuralPreview();
                }
            }
        });
        styleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked)
                return;
            String style = checkedId == R.id.dock_tuning_style_capsule
                ? SegmentedPillPreference.VALUE_CAPSULE : SegmentedPillPreference.VALUE_DEFAULT;
            if (!style.equals(mPreferences.getAppLauncherDockStyle())) {
                mPreferences.setAppLauncherDockStyle(style);
                applyDockTuningStructuralPreview();
            }
        });
        confirm.setOnClickListener(view -> exitDockTuningMode());
        if (dismiss != null) {
            dismiss.setOnClickListener(view -> {
                // Dismiss reverts to the values captured when tuning began.
                mPreferences.setExtraKeysBlurRadius(initialBlur);
                mPreferences.setAppBarOpacity(initialOpacity);
                mPreferences.setDockGlassGrain(initialGrain);
                mPreferences.setTerminalBackgroundOpacity(initialTerminal);
                mPreferences.setSessionsOpacity(initialSessions);
                mPreferences.setAppLauncherBarHeightScale(initialBarHeight);
                mPreferences.setAppLauncherButtonCount(initialButtonCount);
                mPreferences.setAppLauncherDockStyle(initialStyle);
                applyDockTuningStructuralPreview();
                exitDockTuningMode();
            });
        }
        controls.bringToFront();
        registerDockTuningLayoutListener(controls);
        controls.post(this::adjustDockTuningCardHeight);
    }

    private void registerDockTuningLayoutListener(@NonNull View controls) {
        if (mDockTuningLayoutListener != null)
            return;
        mDockTuningLayoutListener = this::adjustDockTuningCardHeight;
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
        View done = findViewById(R.id.dock_tuning_confirm);
        View stack = findViewById(R.id.accessory_stack_container);
        if (controls == null || scroll == null || headerRow == null || done == null || stack == null)
            return;
        if (controls.getVisibility() != View.VISIBLE)
            return;
        View scrollChild = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        if (scrollChild == null)
            return;

        int topLimit = Math.max(mLastStatusBarInsetTop, Math.round(dpToPx(24))) + Math.round(dpToPx(8));
        int cardMarginBottom = Math.round(dpToPx(10));
        int availableCard = (stack.getTop() - cardMarginBottom) - topLimit;
        // Chrome outside the scroll region: card top/bottom padding (10 + 12), Done top margin (6),
        // plus the measured header and Done heights.
        int chrome = Math.round(dpToPx(10 + 12 + 6)) + headerRow.getHeight() + done.getHeight();
        int maxScroll = availableCard - chrome;
        int minScroll = Math.round(dpToPx(96));
        if (maxScroll < minScroll)
            maxScroll = minScroll;

        int natural = scrollChild.getMeasuredHeight();
        if (natural <= 0)
            natural = scrollChild.getHeight();
        ViewGroup.LayoutParams lp = scroll.getLayoutParams();
        int target = (natural > maxScroll) ? maxScroll : ViewGroup.LayoutParams.WRAP_CONTENT;
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

    /** Broader live re-apply for controls that change dock geometry, terminal, or sessions surfaces. */
    private void applyDockTuningStructuralPreview() {
        updateAppLauncherBarHeight();
        applyTerminalSurfaceAppearance();
        applyDockTuningPreview(true);
        applyAccessoryGeometryIfNeeded(true, "dock-tuning:structural");
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
        public void requestAccessoryGeometrySync() {
            requestInAppKeyboardGeometrySync();
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
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView, mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient, 0);
        mTermuxTerminalExtraKeys2 = null;
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

    public void setTerminalToolbarHeight() {
        setTerminalToolbarHeight(true);
    }

    private void setTerminalToolbarHeight(boolean requestTerminalResize) {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        View accessoryStackContainer = findViewById(R.id.accessory_stack_container);
        if (terminalToolbarViewPager == null || accessoryStackContainer == null)
            return;
        ViewGroup.LayoutParams toolbarLayoutParams = terminalToolbarViewPager.getLayoutParams();

        int matrix = 0;
        if (mTermuxTerminalExtraKeys != null && mTermuxTerminalExtraKeys.getExtraKeysInfo() != null) {
            matrix = mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length;
        }

        int toolbarHeightPx = AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(
            Math.round(mTerminalToolbarDefaultHeight),
            matrix,
            mProperties.getTerminalToolbarHeightScaleFactor()
        );
        toolbarLayoutParams.height = toolbarHeightPx;
        terminalToolbarViewPager.setLayoutParams(toolbarLayoutParams);

        AccessoryRenderState state = buildAccessoryRenderState();
        DockLayoutMetrics dockMetrics = buildDockLayoutMetrics(0);
        applyDockLayoutMetrics(dockMetrics);
        int accessoryBottomMarginPx = resolveAccessoryStackBottomMarginPx(state);
        // Preserve the legacy (GONE) toolbar-only layout params byte-for-byte when no embedded
        // keyboard is present. Once the keyboard is shown, hidden toolbar rows contribute zero.
        int dockContentHeightPx = state.keyboardShown && !state.toolbarShown
            ? 0 : dockMetrics.combinedHeight(toolbarHeightPx);
        int accessoryContentHeightPx = computeAccessoryStackHeight(
            dockContentHeightPx, 0, state.keyboardHeight);
        // The embedded keyboard suspends flush absorption: its height is user-scaled and its
        // surface defines its own boundary, so the split remainder halves would surface as
        // wallpaper bands above the gesture-navigation inset instead of hiding in dock glass.
        int terminalFlushPaddingPx = state.keyboardShown ? 0
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
        // The embedded keyboard is an ordinary bottom child. Root/decor inset policy already keeps
        // it above navigation bars, so a floating-dock gap must not be inserted beneath it.
        if (state.keyboardShown)
            return mImeLiftPx;
        // The capsule floats, so it keeps its bottom gap even when the keyboard is up — otherwise it
        // sits flush against the keyboard. Non-capsule styles stay flush.
        if (!isValarieDockStyle()) {
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
        int contentInset = isValarieDockStyle() ? resolveDockCapsuleContentInsetPx() : 0;
        int extraKeysInset = isValarieDockStyle() ? resolveDockCapsuleExtraKeysInsetPx() : 0;
        int surfaceInset = isValarieDockStyle() ? resolveDockCapsuleHorizontalMarginPx() : 0;
        int appsTopPadding = isValarieDockStyle() ? resolveDockCapsuleAppsTopPaddingPx() : resolveDefaultDockAppsTopPaddingPx();
        int appsBottomPadding = isValarieDockStyle() ? resolveDockCapsuleAppsBottomPaddingPx() : resolveDefaultDockAppsBottomPaddingPx();
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
        int appsTopPadding = isValarieDockStyle() ? resolveDockCapsuleAppsTopPaddingPx() : resolveDefaultDockAppsTopPaddingPx();
        int appsBottomPadding = isValarieDockStyle() ? resolveDockCapsuleAppsBottomPaddingPx() : resolveDefaultDockAppsBottomPaddingPx();
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
        boolean appsRowEnabled = mPreferences.isAppLauncherAppsRowEnabled();
        int appsBarHeightPx = appsRowEnabled
            ? resolveDockAppsBarHeightPx(normalizedScale, defaultDockProgress,
                Math.max(0, additionalAppsBarHeightPx))
            : 0;

        boolean azEnabled = appsRowEnabled && mPreferences.isAppLauncherAzRowEnabled();
        int azRowHeightPx = AccessoryStackLayoutPolicy.computeAzRowHeightPx(azEnabled, density);
        int indicatorBandHeightPx = AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(azEnabled, density);

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
        boolean capsule = isValarieDockStyle();
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
        if (isValarieDockStyle()) {
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
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null, R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text), R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text), -1, null, null);
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
                clearWallpaperWindowBackgroundCaches();
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

    private boolean handleTerminalAction(int itemId) {
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
            case CONTEXT_MENU_LOOK_AND_FEEL_ID:
                openLookAndFeelSettings();
                return true;
            case CONTEXT_MENU_APPS_BAR_ID:
                openAppsBarSettings();
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
            default:
                return false;
        }
    }

    public boolean showTerminalActionSheet() {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) {
            return false;
        }
        if (mTerminalActionDialog != null && mTerminalActionDialog.isShowing()) {
            return true;
        }
        List<TerminalActionItem> items = new ArrayList<>();
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

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (mDockTuningMode) {
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

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null)
            return;
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.ok, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.cancel, null);
        b.show();
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
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: " + resultCode + ", data: " + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: " + Arrays.toString(permissions) + ", grantResults: " + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
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

    public ExtraKeysView getExtraKeysView(int i) {
        return mExtraKeysView;
    }
    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys(int i) {
        return mTermuxTerminalExtraKeys;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView, int i) {
        mExtraKeysView = extraKeysView;
        applyExtraKeysFeedbackAccent(extraKeysView);
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
        applyExtraKeysFeedbackAccent(extraKeysView);
    }

    /** Tints the extra-keys press feedback with the dock accent so it matches the dock's rim glow. */
    private void applyExtraKeysFeedbackAccent(@Nullable ExtraKeysView extraKeysView) {
        if (extraKeysView != null) {
            extraKeysView.setKeyPressFeedbackColor(resolveDockAccentColor());
            // Soft feathered wash when the dock blur is doing the work; a more present fill otherwise.
            boolean blurActive = mPreferences != null && mPreferences.getExtraKeysBlurRadius() > 0;
            extraKeysView.setKeyPressFeedbackBlurAvailable(blurActive);
            // Floating capsule dock -> vertical liquid popup pill; edge-to-edge dock -> rounded-rect.
            extraKeysView.setPopupCapsuleStyle(isValarieDockStyle());
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

    /** All terminal panes that currently exist (order = pane 1, pane 2). */
    public java.util.List<TerminalView> getTerminalPaneViews() {
        java.util.List<TerminalView> panes = new java.util.ArrayList<>(2);
        if (mTerminalView != null) panes.add(mTerminalView);
        if (mTerminalView2 != null) panes.add(mTerminalView2);
        return panes;
    }

    /** The pane currently displaying {@code session}, or null if none. */
    @Nullable
    public TerminalView getTerminalViewForSession(@Nullable TerminalSession session) {
        if (session == null) return null;
        for (TerminalView v : getTerminalPaneViews()) {
            if (v.getCurrentSession() == session) return v;
        }
        return null;
    }

    private void setupPaneFocusRouting() {
        // Touch-down (not focus-change) so every tap reliably re-targets the active pane,
        // even after focus has bounced around between panes / the in-app keyboard.
        android.view.View.OnTouchListener t = (view, ev) -> {
            if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                setActivePane((TerminalView) view);
            return false; // observe only; let the view handle the touch
        };
        for (TerminalView v : getTerminalPaneViews())
            v.setOnTouchListener(t);
        updatePaneActiveIndicators();
    }

    /** Mark {@code view} as the focused pane and update the active-pane border. */
    public void setActivePane(TerminalView view) {
        if (view == null) return;
        mActivePane = view;
        if (!view.isFocused())
            view.requestFocus();
        updatePaneActiveIndicators();
    }

    private void updatePaneActiveIndicators() {
        View frame1 = findViewById(R.id.terminal_pane_frame_1);
        View frame2 = findViewById(R.id.terminal_pane_frame_2);
        // Only show the active-pane border while a second pane is visible (split mode).
        boolean split = mTerminalView2 != null && frame2 != null
            && frame2.getVisibility() == View.VISIBLE;
        if (frame1 != null)
            frame1.setForeground(split && mActivePane == mTerminalView
                ? androidx.core.content.ContextCompat.getDrawable(this, R.drawable.pane_active_border) : null);
        if (frame2 != null)
            frame2.setForeground(split && mActivePane == mTerminalView2
                ? androidx.core.content.ContextCompat.getDrawable(this, R.drawable.pane_active_border) : null);
    }

    public boolean isSecondaryPaneSession(@Nullable TerminalSession s) {
        return s != null && mSecondaryPaneSessions.contains(s);
    }

    @Nullable
    public TerminalSession getTabSecondary(@Nullable TerminalSession primary) {
        return primary == null ? null : mTabSecondary.get(primary);
    }

    @Nullable
    private TerminalSession findPrimaryForSecondary(TerminalSession secondary) {
        for (java.util.Map.Entry<TerminalSession, TerminalSession> e : mTabSecondary.entrySet())
            if (e.getValue() == secondary) return e.getKey();
        return null;
    }

    /** Rebuild the drawer-visible session list (excludes secondary panes) and refresh the adapter. */
    public void rebuildDrawerSessions() {
        mDrawerSessions.clear();
        if (mTermuxService != null) {
            for (com.termux.shared.termux.shell.command.runner.terminal.TermuxSession ts : mTermuxService.getTermuxSessions()) {
                if (!isSecondaryPaneSession(ts.getTerminalSession()))
                    mDrawerSessions.add(ts);
            }
        }
        if (mTermuxSessionListViewController != null)
            mTermuxSessionListViewController.notifyDataSetChanged();
    }

    /** Index of {@code session} within the drawer-visible list, or -1. */
    public int getDrawerIndexOfSession(TerminalSession session) {
        for (int i = 0; i < mDrawerSessions.size(); i++)
            if (mDrawerSessions.get(i).getTerminalSession() == session) return i;
        return -1;
    }

    private int paneDp(int dp) {
        return Math.round(getResources().getDisplayMetrics().density * dp);
    }

    /** Lay the two pane frames + divider out for the given orientation. */
    private void applyPaneContainerOrientation(int orientation) {
        android.widget.LinearLayout panes = findViewById(R.id.terminal_panes);
        View frame1 = findViewById(R.id.terminal_pane_frame_1);
        View divider = findViewById(R.id.terminal_pane_divider);
        View frame2 = findViewById(R.id.terminal_pane_frame_2);
        if (panes == null || frame1 == null || divider == null || frame2 == null) return;
        panes.setOrientation(orientation);
        boolean vertical = orientation == android.widget.LinearLayout.VERTICAL;
        int match = android.widget.LinearLayout.LayoutParams.MATCH_PARENT;
        // Weighted frames: the weighted axis is 0dp, the other is match_parent.
        frame1.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            vertical ? match : 0, vertical ? 0 : match, 1f));
        frame2.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            vertical ? match : 0, vertical ? 0 : match, 1f));
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            vertical ? match : paneDp(1), vertical ? paneDp(1) : match));
    }

    /**
     * Show a session's tab across the panes: primary in pane 1, its secondary (if any) in
     * pane 2. Passing a secondary session activates its owning tab and focuses pane 2.
     * @return true if the focused session changed.
     */
    public boolean activateSessionInPanes(TerminalSession session) {
        if (session == null || mTerminalView == null) return false;
        TerminalSession previousFocused = getCurrentSession();
        TerminalSession primary = isSecondaryPaneSession(session) ? findPrimaryForSecondary(session) : session;
        if (primary == null) primary = session;
        mCurrentTabPrimary = primary;
        TerminalSession secondary = mTabSecondary.get(primary);

        if (getPreferences() != null && !mTerminalView.isFontInitialized())
            mTerminalView.setTextSize(getPreferences().getFontSize());
        mTerminalView.attachSession(primary);

        View frame2 = findViewById(R.id.terminal_pane_frame_2);
        View divider = findViewById(R.id.terminal_pane_divider);
        if (secondary != null && mTerminalView2 != null) {
            Integer o = mTabOrientation.get(primary);
            applyPaneContainerOrientation(o == null ? android.widget.LinearLayout.VERTICAL : o);
            if (getPreferences() != null && !mTerminalView2.isFontInitialized())
                mTerminalView2.setTextSize(getPreferences().getFontSize());
            if (frame2 != null) frame2.setVisibility(View.VISIBLE);
            if (divider != null) divider.setVisibility(View.VISIBLE);
            mTerminalView2.attachSession(secondary);
        } else {
            if (frame2 != null) frame2.setVisibility(View.GONE);
            if (divider != null) divider.setVisibility(View.GONE);
        }
        // Apply font/colours (nerd-font typeface) to every populated pane.
        if (getTermuxTerminalSessionClient() != null)
            getTermuxTerminalSessionClient().checkForFontAndColors();

        mActivePane = (secondary != null && session == secondary) ? mTerminalView2 : mTerminalView;
        if (mActivePane != null) mActivePane.requestFocus();
        updatePaneActiveIndicators();
        for (TerminalView v : getTerminalPaneViews())
            if (v.getCurrentSession() != null) v.onScreenUpdated();
        return previousFocused != session;
    }

    /** Whether custom window/pane splitting is active (disabled by compatibility mode). */
    public boolean isSplitPanesEnabled() {
        return getPreferences() == null || !getPreferences().isCompatibilityModeEnabled();
    }

    @Nullable
    public TerminalSession getCurrentTabPrimary() {
        return mCurrentTabPrimary;
    }

    /** Split the current tab's pane into two, spawning a new shell in the second pane. */
    public void splitCurrentPane(int orientation) {
        if (!isSplitPanesEnabled()) return;
        if (mTermuxService == null || mCurrentTabPrimary == null) {
            showToast("No session to split", true);
            return;
        }
        if (mTabSecondary.containsKey(mCurrentTabPrimary)) {
            showToast("Pane already split", true);
            return;
        }
        if (mTerminalView2 == null) return;
        String cwd = mCurrentTabPrimary.getCwd();
        if (cwd == null) cwd = getProperties().getDefaultWorkingDirectory();
        com.termux.shared.termux.shell.command.runner.terminal.TermuxSession created =
            mTermuxService.createTermuxSession(null, null, null, cwd, false, null);
        if (created == null) return;
        TerminalSession sec = created.getTerminalSession();
        mSecondaryPaneSessions.add(sec);
        mTabSecondary.put(mCurrentTabPrimary, sec);
        mTabOrientation.put(mCurrentTabPrimary, orientation);
        applyPaneContainerOrientation(orientation);
        if (getPreferences() != null) {
            mTerminalView2.setTextSize(getPreferences().getFontSize());
            mTerminalView2.setKeepScreenOn(getPreferences().shouldKeepScreenOn());
        }
        View frame2 = findViewById(R.id.terminal_pane_frame_2);
        View divider = findViewById(R.id.terminal_pane_divider);
        if (frame2 != null) frame2.setVisibility(View.VISIBLE);
        if (divider != null) divider.setVisibility(View.VISIBLE);
        mTerminalView2.attachSession(sec);
        // Apply font to the NEW pane only. Re-applying to pane 1 would recreate its renderer
        // and trigger a second resize/repaint (fish then reprints its prompt -> duplicate).
        if (getTermuxTerminalSessionClient() != null)
            getTermuxTerminalSessionClient().applyFontToView(mTerminalView2);
        mActivePane = mTerminalView2;
        mTerminalView2.requestFocus();
        updatePaneActiveIndicators();
        mTerminalView2.onScreenUpdated();
        // A side-by-side split changes pane 1's column count; the running shell reflows and
        // leaves a stale prompt line. Once the resize settles, nudge it (Ctrl+L) to redraw
        // cleanly. Stacked splits keep the same width, so no nudge is needed there.
        if (orientation == android.widget.LinearLayout.HORIZONTAL) {
            final TerminalSession primary = mCurrentTabPrimary;
            mTerminalView.postDelayed(() -> {
                if (primary != null && primary.isRunning())
                    primary.write("\u000c");
            }, 250);
        }
        rebuildDrawerSessions();
    }

    /** Move focus to the pane in the given arrow direction (Ctrl+Alt+arrow). No-op if none. */
    public boolean focusPaneDirection(int keyCode) {
        View frame2 = findViewById(R.id.terminal_pane_frame_2);
        boolean split = mTerminalView2 != null && frame2 != null && frame2.getVisibility() == View.VISIBLE;
        if (!split) return true; // consume; nothing to move to in single-pane mode
        android.widget.LinearLayout panes = findViewById(R.id.terminal_panes);
        boolean stacked = panes == null || panes.getOrientation() == android.widget.LinearLayout.VERTICAL;
        TerminalView target = null;
        if (stacked) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) target = mTerminalView;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) target = mTerminalView2;
        } else {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) target = mTerminalView;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) target = mTerminalView2;
        }
        if (target != null) setActivePane(target);
        return true;
    }

    /** Adjust the split ratio toward the arrow direction (Ctrl+Alt+Shift+arrow). */
    public boolean resizeActivePane(int keyCode) {
        View frame1 = findViewById(R.id.terminal_pane_frame_1);
        View frame2 = findViewById(R.id.terminal_pane_frame_2);
        if (frame1 == null || frame2 == null || frame2.getVisibility() != View.VISIBLE) return true;
        android.widget.LinearLayout panes = findViewById(R.id.terminal_panes);
        boolean stacked = panes == null || panes.getOrientation() == android.widget.LinearLayout.VERTICAL;
        android.widget.LinearLayout.LayoutParams p1 = (android.widget.LinearLayout.LayoutParams) frame1.getLayoutParams();
        android.widget.LinearLayout.LayoutParams p2 = (android.widget.LinearLayout.LayoutParams) frame2.getLayoutParams();
        float step = 0.12f;
        float w1 = p1.weight, w2 = p2.weight;
        if (stacked) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { w1 += step; w2 -= step; }
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { w1 -= step; w2 += step; }
            else return true;
        } else {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { w1 += step; w2 -= step; }
            else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { w1 -= step; w2 += step; }
            else return true;
        }
        // Clamp so neither pane collapses (total weight stays 2).
        w1 = Math.max(0.3f, Math.min(1.7f, w1));
        w2 = 2f - w1;
        p1.weight = w1; p2.weight = w2;
        frame1.setLayoutParams(p1);
        frame2.setLayoutParams(p2);
        return true;
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

    /** Close the current tab (its primary session plus any secondary pane) and switch away. */
    public void closeCurrentSession() {
        TerminalSession primary = mCurrentTabPrimary != null ? mCurrentTabPrimary : getCurrentSession();
        if (primary == null) return;
        TerminalSession sec = mTabSecondary.get(primary);
        if (sec != null) {
            mTabSecondary.remove(primary);
            mTabOrientation.remove(primary);
            mSecondaryPaneSessions.remove(sec);
            if (mTermuxService != null) mTermuxService.removeTermuxSession(sec);
        }
        if (getTermuxTerminalSessionClient() != null)
            getTermuxTerminalSessionClient().removeFinishedSession(primary);
    }

    /** Collapse any active split back to a single pane (used when entering compatibility mode). */
    public void collapseAllSplits() {
        if (mTerminalView2 == null) return;
        View frame2 = findViewById(R.id.terminal_pane_frame_2);
        View divider = findViewById(R.id.terminal_pane_divider);
        if (frame2 != null) frame2.setVisibility(View.GONE);
        if (divider != null) divider.setVisibility(View.GONE);
        mTabSecondary.clear();
        mTabOrientation.clear();
        mSecondaryPaneSessions.clear();
        mActivePane = mTerminalView;
        updatePaneActiveIndicators();
        rebuildDrawerSessions();
    }

    /** Tear down the second pane after its (secondary) session has finished. */
    public void closeSecondaryPane(TerminalSession sec) {
        TerminalSession primary = findPrimaryForSecondary(sec);
        if (primary != null) {
            mTabSecondary.remove(primary);
            mTabOrientation.remove(primary);
        }
        mSecondaryPaneSessions.remove(sec);
        View frame2 = findViewById(R.id.terminal_pane_frame_2);
        View divider = findViewById(R.id.terminal_pane_divider);
        if (frame2 != null) frame2.setVisibility(View.GONE);
        if (divider != null) divider.setVisibility(View.GONE);
        mActivePane = mTerminalView;
        if (mTerminalView != null) mTerminalView.requestFocus();
        updatePaneActiveIndicators();
        rebuildDrawerSessions();
    }

    /** Promote a tab's secondary pane to a standalone session (used when the primary exits). */
    public void promoteSecondaryToPrimary(TerminalSession primary) {
        TerminalSession sec = mTabSecondary.remove(primary);
        mTabOrientation.remove(primary);
        if (sec != null) mSecondaryPaneSessions.remove(sec);
        rebuildDrawerSessions();
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
        if (shouldUseWallpaperPassthroughMode()) {
            Drawable managedWallpaperBackground = getManagedWallpaperWindowBackground();
            if (managedWallpaperBackground != null) {
                getWindow().getDecorView().setBackground(managedWallpaperBackground);
            } else if (!isLiveWallpaperActive()) {
                Drawable systemWallpaperBackground = getSystemWallpaperWindowBackground();
                if (systemWallpaperBackground != null) {
                    getWindow().getDecorView().setBackground(systemWallpaperBackground);
                } else {
                    getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);
                }
            } else {
                getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);
            }
            return;
        }
        clearWallpaperWindowBackgroundCaches();
        getWindow().getDecorView().setBackgroundColor(
            getTermuxThemeColor(com.termux.shared.R.attr.termuxColorSurfaceBase, R.color.termux_surface_base)
        );
    }

    @Nullable
    private Drawable getManagedWallpaperWindowBackground() {
        if (!shouldUseManagedWallpaperBlurSource()) {
            return null;
        }
        File sourceFile = getManagedWallpaperExactFile();
        long lastModified = sourceFile.lastModified();
        long length = sourceFile.length();
        if (mManagedWallpaperWindowBackground != null &&
            mManagedWallpaperWindowBackgroundLastModified == lastModified &&
            mManagedWallpaperWindowBackgroundLength == length) {
            return mManagedWallpaperWindowBackground;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
        if (bitmap == null) {
            clearManagedWallpaperWindowBackgroundCache();
            return null;
        }
        mManagedWallpaperWindowBackground = new CenterCropBitmapDrawable(bitmap);
        mManagedWallpaperWindowBackgroundLastModified = lastModified;
        mManagedWallpaperWindowBackgroundLength = length;
        return mManagedWallpaperWindowBackground;
    }

    private void clearManagedWallpaperWindowBackgroundCache() {
        mManagedWallpaperWindowBackground = null;
        mManagedWallpaperWindowBackgroundLastModified = -1L;
        mManagedWallpaperWindowBackgroundLength = -1L;
    }

    @Nullable
    private Drawable getSystemWallpaperWindowBackground() {
        int currentWallpaperId = getCurrentSystemWallpaperId();
        if (currentWallpaperId > 0 &&
            mSystemWallpaperWindowBackground != null &&
            mSystemWallpaperWindowBackgroundId == currentWallpaperId) {
            return mSystemWallpaperWindowBackground;
        }

        try {
            Drawable wallpaper = WallpaperManager.getInstance(this).getDrawable();
            if (wallpaper == null) {
                clearSystemWallpaperWindowBackgroundCache();
                return null;
            }
            Drawable source = wallpaper.getConstantState() != null
                ? wallpaper.getConstantState().newDrawable().mutate()
                : wallpaper.mutate();
            mSystemWallpaperWindowBackground = new CenterCropDrawable(source);
            mSystemWallpaperWindowBackgroundId = currentWallpaperId;
            return mSystemWallpaperWindowBackground;
        } catch (Exception e) {
            clearSystemWallpaperWindowBackgroundCache();
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to create system wallpaper window background", e);
            return null;
        }
    }

    private void clearSystemWallpaperWindowBackgroundCache() {
        mSystemWallpaperWindowBackground = null;
        mSystemWallpaperWindowBackgroundId = -1;
    }

    private void clearWallpaperWindowBackgroundCaches() {
        clearManagedWallpaperWindowBackgroundCache();
        clearSystemWallpaperWindowBackgroundCache();
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
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackageAdded(String packageName, UserHandle user) {
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackageChanged(String packageName, UserHandle user) {
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                        scheduleSuggestionBarPackageRefresh(false, true);
                    }

                    @Override
                    public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
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

    private void refreshSuggestionBarFromPackageState(boolean forceCatalogRefresh) {
        if (!isSuggestionBarEnabled() || mSuggestionBarView == null) {
            return;
        }
        if (forceCatalogRefresh) {
            LauncherCtlApiServer.getInstance().invalidatePackageCaches();
            mSuggestionBarView.clearAppCache();
            mSuggestionBarView.pruneInvalidIconOverrides();
            mSuggestionBarView.reloadAllApps();
            mLastLauncherCatalogSignature = computeLauncherCatalogSignature();
            syncAzScrubLettersAndTint();
        } else if (mSuggestionBarView.hasPinnedOverflowPages()) {
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
            if (v == mTerminalView) {
                // Never mutate accessory layout params from inside this layout pass.
                v.post(() -> {
                    if (!isFinishing() && !isDestroyed())
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
            R.id.terminal_view,
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
            R.id.terminal_view,
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
        if (!isSuggestionBarEnabled()) {
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
        if (!isSuggestionBarEnabled() || mSuggestionBarView == null) {
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
        if (mIsVisible && isSuggestionBarEnabled() && mSuggestionBarView != null) {
            mAzGestureHandler.postDelayed(mLauncherCatalogWarmRunnable, LAUNCHER_CATALOG_WARM_DELAY_MS);
        }
    }

    private void runLauncherCatalogWarmup() {
        if (!mIsVisible || !isSuggestionBarEnabled() || mSuggestionBarView == null) {
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
                scheduleSuggestionBarPackageRefresh(false, true);
            }
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null)
                return;
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
        applySuggestionBarInputChar();
        mAccessoryBackdropDirty = true;
        mDecorNavBarBackdropDirty = true;
        applyAccessoryGeometryIfNeeded(true, "reloadActivityStyling");
        applySeamlessStatusBackgroundModeIfNeeded();
        applyTerminalSurfaceAppearance();
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

    private static final class CenterCropBitmapDrawable extends Drawable {
        @NonNull private final Bitmap mBitmap;
        @NonNull private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        @NonNull private final RectF mDestinationRect = new RectF();

        CenterCropBitmapDrawable(@NonNull Bitmap bitmap) {
            mBitmap = bitmap;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            int boundsWidth = Math.max(1, bounds.width());
            int boundsHeight = Math.max(1, bounds.height());
            int bitmapWidth = Math.max(1, mBitmap.getWidth());
            int bitmapHeight = Math.max(1, mBitmap.getHeight());
            float scale = Math.max((float) boundsWidth / bitmapWidth, (float) boundsHeight / bitmapHeight);
            float drawWidth = bitmapWidth * scale;
            float drawHeight = bitmapHeight * scale;
            float left = bounds.left + ((boundsWidth - drawWidth) / 2f);
            float top = bounds.top + ((boundsHeight - drawHeight) / 2f);
            mDestinationRect.set(left, top, left + drawWidth, top + drawHeight);
            canvas.drawBitmap(mBitmap, null, mDestinationRect, mPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private static final class CenterCropDrawable extends Drawable {
        @NonNull private final Drawable mSource;

        CenterCropDrawable(@NonNull Drawable source) {
            mSource = source;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            int boundsWidth = Math.max(1, bounds.width());
            int boundsHeight = Math.max(1, bounds.height());
            int sourceWidth = mSource.getIntrinsicWidth() > 0 ? mSource.getIntrinsicWidth() : boundsWidth;
            int sourceHeight = mSource.getIntrinsicHeight() > 0 ? mSource.getIntrinsicHeight() : boundsHeight;
            float scale = Math.max((float) boundsWidth / sourceWidth, (float) boundsHeight / sourceHeight);
            int drawWidth = Math.max(boundsWidth, Math.round(sourceWidth * scale));
            int drawHeight = Math.max(boundsHeight, Math.round(sourceHeight * scale));
            int left = bounds.left + Math.round((boundsWidth - drawWidth) / 2f);
            int top = bounds.top + Math.round((boundsHeight - drawHeight) / 2f);
            mSource.setBounds(left, top, left + drawWidth, top + drawHeight);
            mSource.draw(canvas);
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

        @Override
        public int getOpacity() {
            return mSource.getOpacity();
        }
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
