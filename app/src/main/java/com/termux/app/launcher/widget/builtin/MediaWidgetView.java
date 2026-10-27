package com.termux.app.launcher.widget.builtin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.app.statusbar.TopPaneFeed;
import com.termux.app.statusbar.TopPaneMediaState;

/**
 * What is playing, with its transport: the media session the notification listener already
 * follows for the status bar, at five spans — a play button over the art at 1×1, the full deck at
 * 4×2. The words and the boxes of the art, the buttons and the progress come from the layout
 * plan ({@link MediaLayoutPlanner}); the art and the buttons are views placed in them. When
 * nothing is playing the card offers to resume the last app that was. The body opens the
 * playing app. Without notification access the card asks for it, and a tap opens the switch.
 */
public class MediaWidgetView extends BuiltinWidgetView {
    private static final String GLYPH_PLAY = "";
    private static final String GLYPH_PAUSE = "";
    private static final String GLYPH_PREVIOUS = "";
    private static final String GLYPH_NEXT = "";
    private static final String GLYPH_ACCESS = "";

    private static final int TOUCH_DP = WidgetTokens.TOUCH_TARGET_DP;
    /** The disc a transport glyph sits on, inside its 48 dp target. */
    private static final int DISC_DP = 40;
    private static final int TOUCH_MID_DP = 40;
    private static final int TOUCH_MIN_DP = 36;
    private static final long TICK_MS = 1000L;

    private static final String PREFS = "builtin_media_widget";
    private static final String KEY_LAST_PACKAGE = "last_package";

    private final TopPaneFeed.Observer feedObserver = () -> syncFeed(false);
    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            TopPaneMediaState current = state;
            if (!isStarted() || current == null || !current.playing) return;
            updateProgress();
            scheduleTick();
        }
    };

    /** The session on screen; dropped on stop so its artwork is not held off screen. */
    @Nullable private TopPaneMediaState state;
    private boolean access;

    // The last report, and the position carried forward from it (see MediaWidgetFormats).
    private boolean anchored;
    @NonNull private String anchorPackage = "";
    @NonNull private String anchorTitle = "";
    private long reportedPositionMs;
    private long anchorPositionMs;
    private long anchorAtMs;
    private boolean anchorPlaying;
    private long anchorDurationMs;

    /** The app that last played, for the resume offer; null until read from the preferences. */
    @Nullable private String lastPackage;

    // The views the current layout binds; null where the span has no such view.
    private boolean playerBuilt;
    /** What the words on the card were planned from; a change of it is a new plan. */
    @NonNull private String builtSignature = "";
    @NonNull private MediaLayoutPlanner.Mode mode = MediaLayoutPlanner.Mode.IDLE;
    @Nullable private ResumeTarget resume;
    @Nullable private ImageView art;
    @Nullable private View artPlaceholder;
    @Nullable private Bitmap shownArt;
    @Nullable private Drawable shownIcon;
    @Nullable private TextView elapsed;
    @Nullable private TextView total;
    @Nullable private BuiltinWidgetUi.BarView bar;
    @Nullable private TextView playButton;
    @Nullable private TextView previousButton;
    @Nullable private TextView nextButton;

    /** The app the resume offer opens. */
    private static final class ResumeTarget {
        @NonNull final String packageName;
        @NonNull final String label;
        @NonNull final Intent launch;

        ResumeTarget(@NonNull String packageName, @NonNull String label, @NonNull Intent launch) {
            this.packageName = packageName; this.label = label; this.launch = launch;
        }
    }

    public MediaWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                           @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.MEDIA, services, style);
    }

    // ----- lifecycle ------------------------------------------------------------------------

    @Override protected void onStart() {
        TopPaneFeed.addObserver(feedObserver);
        // Whatever changed while the card was off screen; onStop let go of the session.
        syncFeed(true);
        scheduleTick();
    }

    @Override protected void onStop() {
        TopPaneFeed.removeObserver(feedObserver);
        services.main().removeCallbacks(progressTick);
        if (art != null) art.setImageDrawable(null);
        shownArt = null;
        shownIcon = null;
        state = null;
    }

    /** Brings the card level with the feed; {@code force} rebinds even when nothing moved. */
    private void syncFeed(boolean force) {
        if (!isStarted()) return;
        boolean nextAccess = hasAccess();
        TopPaneMediaState next = TopPaneFeed.getMedia();
        // The feed also carries pinned notifications; their changes are not ours.
        if (!force && nextAccess == access && next == state && playerBuilt == access) return;
        boolean layoutChanged = nextAccess != access || playerBuilt != nextAccess;
        access = nextAccess;
        setState(next);
        if (layoutChanged || (access && !signature().equals(builtSignature))) {
            FrameLayout frame = content();
            frame.removeAllViews();
            layout(frame, span(), ui());
        } else {
            bind();
        }
    }

    private boolean hasAccess() {
        // A connected listener has access by definition; only a disconnected one needs the
        // settings read, and an enabled-but-reconnecting listener is "nothing playing", not a
        // request for access the user already gave.
        return TopPaneFeed.isListenerConnected()
            || LauncherNotificationAccess.isEnabled(getContext());
    }

    private void setState(@Nullable TopPaneMediaState next) {
        if (next != null) {
            long now = SystemClock.elapsedRealtime();
            if (anchored && MediaWidgetFormats.carriesPosition(anchorPackage, anchorTitle,
                reportedPositionMs, next.packageName, next.title, next.positionMs)) {
                anchorPositionMs = MediaWidgetFormats.positionAt(anchorPositionMs, anchorAtMs, now,
                    anchorPlaying, anchorDurationMs);
                anchorAtMs = now;
            } else {
                // The session says when its position was true; count from there, not from
                // when this widget first heard of it.
                anchorPositionMs = next.positionMs;
                anchorAtMs = next.positionUpdatedAtElapsedMs > 0
                    && next.positionUpdatedAtElapsedMs <= now ? next.positionUpdatedAtElapsedMs : now;
            }
            anchored = true;
            anchorPackage = next.packageName;
            anchorTitle = next.title;
            reportedPositionMs = next.positionMs;
            anchorPlaying = next.playing;
            anchorDurationMs = next.durationMs;
            remember(next.packageName);
        }
        state = next;
    }

    // ----- the last app ---------------------------------------------------------------------

    /** Keeps {@code packageName} as the app to offer to resume, once per change. */
    private void remember(@NonNull String packageName) {
        if (isPreview() || packageName.isEmpty() || packageName.equals(lastPackage())) return;
        lastPackage = packageName;
        prefs().edit().putString(KEY_LAST_PACKAGE, packageName).apply();
    }

    @NonNull private String lastPackage() {
        if (lastPackage == null) lastPackage = prefs().getString(KEY_LAST_PACKAGE, "");
        return lastPackage == null ? "" : lastPackage;
    }

    @NonNull private SharedPreferences prefs() {
        return getContext().getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The last app when it is still installed and can be opened, else null. */
    @Nullable private ResumeTarget findResume() {
        if (isPreview()) return null;
        String packageName = lastPackage();
        if (packageName.isEmpty()) return null;
        PackageManager pm = getContext().getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(packageName);
        if (launch == null) return null;
        try {
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0));
            return new ResumeTarget(packageName, label.toString(), launch);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private long currentPositionMs() {
        TopPaneMediaState current = state;
        if (current == null) return 0L;
        if (isPreview()) return current.positionMs;
        return MediaWidgetFormats.positionAt(anchorPositionMs, anchorAtMs,
            SystemClock.elapsedRealtime(), current.playing, current.durationMs);
    }

    private void scheduleTick() {
        services.main().removeCallbacks(progressTick);
        TopPaneMediaState current = state;
        if (!isStarted() || isPreview() || current == null || !current.playing
            || current.durationMs <= 0L) {
            return;
        }
        // Land on the next whole second of the track, so the clock turns over when it should.
        long delay = TICK_MS - (currentPositionMs() % TICK_MS);
        services.main().postDelayed(progressTick, Math.max(16L, delay));
    }

    // ----- taps -----------------------------------------------------------------------------

    @Override protected void onTap() {
        if (isPreview()) return;
        if (!access) {
            if (!start(LauncherNotificationAccess.detailSettingsIntent(getContext()))) {
                start(LauncherNotificationAccess.listSettingsIntent());
            }
            return;
        }
        TopPaneMediaState current = state;
        if (current == null) {
            resumeLastApp();
            return;
        }
        if (current.packageName.isEmpty()) return;
        Intent launch = getContext().getPackageManager()
            .getLaunchIntentForPackage(current.packageName);
        if (launch != null) start(launch);
    }

    private boolean start(@NonNull Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private void resumeLastApp() {
        ResumeTarget target = resume;
        if (target != null) start(new Intent(target.launch));
    }

    private void onPlayPause() {
        TopPaneMediaState current = state;
        if (current == null) {
            resumeLastApp();
            return;
        }
        boolean play = !current.playing;
        TopPaneFeed.togglePlayPause(play);
        // Flips the glyph now; the session's own callback reconciles it moments later.
        TopPaneFeed.applyOptimisticPlayState(play);
    }

    // ----- layouts --------------------------------------------------------------------------

    /** Every layout takes a tall card's height in its art, which a zoom would only narrow. */
    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return false; }

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        if (isPreview()) {
            access = true;
            state = new TopPaneMediaState("", "Low Tide", "Halcyon Tapes", "", null,
                102_000L, 238_000L, true);
        } else {
            access = hasAccess();
            setState(TopPaneFeed.getMedia());
        }
        layout(frame, span, ui);
    }

    private void layout(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                        @NonNull BuiltinWidgetUi ui) {
        clearRefs();
        if (!access) {
            frame.addView(buildAccess(span, ui), matchParent());
            setContentDescription(getContext().getString(R.string.bw_feeds_media_cd_access));
            return;
        }
        resume = state == null ? findResume() : null;
        mode = state != null ? MediaLayoutPlanner.Mode.PLAYING
            : resume != null ? MediaLayoutPlanner.Mode.RESUME : MediaLayoutPlanner.Mode.IDLE;
        builtSignature = signature();
        frame.addView(buildPlayer(span, ui), matchParent());
        playerBuilt = true;
        bind();
    }

    private void clearRefs() {
        playerBuilt = false;
        art = null; artPlaceholder = null; shownArt = null; shownIcon = null;
        elapsed = null; total = null; bar = null;
        playButton = null; previousButton = null; nextButton = null;
    }

    /** What decides the plan's words and parts: a change of any of it is a new layout. */
    @NonNull private String signature() {
        TopPaneMediaState current = state;
        if (current != null) {
            return "p|" + current.title + '|' + current.artist + '|' + current.appLabel + '|'
                + (current.durationMs > 0L);
        }
        ResumeTarget target = findResume();
        return target != null ? "r|" + target.packageName : "i";
    }

    @NonNull private MediaLayoutPlanner.Content planContent() {
        TopPaneMediaState current = state;
        Context context = getContext();
        if (current != null) {
            String title = current.title.isEmpty() ? current.appLabel : current.title;
            String artist = current.title.isEmpty() ? ""
                : current.artist.isEmpty() ? current.appLabel : current.artist;
            return new MediaLayoutPlanner.Content(MediaLayoutPlanner.Mode.PLAYING, title, artist,
                current.appLabel, current.durationMs > 0L);
        }
        ResumeTarget target = resume;
        if (target != null) {
            return new MediaLayoutPlanner.Content(MediaLayoutPlanner.Mode.RESUME,
                context.getString(R.string.bw_feeds_media_resume, target.label), "", target.label,
                false);
        }
        return new MediaLayoutPlanner.Content(MediaLayoutPlanner.Mode.IDLE,
            context.getString(R.string.bw_feeds_media_nothing_playing), "", "", false);
    }

    @NonNull private View buildPlayer(@NonNull BuiltinWidgetSpan span, @NonNull BuiltinWidgetUi ui) {
        MediaLayoutPlanner.Content content = planContent();
        float fontScale = WidgetTokens.typeScale(getResources());
        return new WidgetPlanHost(getContext(), style(), (widthDp, heightDp, measure) ->
            MediaLayoutPlanner.plan(span, widthDp, heightDp, fontScale, content, measure),
            new WidgetPlanHost.ViewSource() {
                @Nullable @Override public View view(@NonNull WidgetLayoutPlan.Part part) {
                    return partView(part, span, ui, fontScale);
                }
                @Override public void onPlanned(@NonNull WidgetLayoutPlan plan) { bind(); }
            });
    }

    /** The view for a TOUCH part of the plan. */
    @Nullable private View partView(@NonNull WidgetLayoutPlan.Part part,
                                    @NonNull BuiltinWidgetSpan span, @NonNull BuiltinWidgetUi ui,
                                    float fontScale) {
        switch (part.id) {
            case "art":
                return artFrame(ui, WidgetTokens.insetDp(span), 6);
            case "bar":
                bar = ui.bar(0f, style().primary, 4);
                bar.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
                return bar;
            case "elapsed":
                elapsed = ui.mono("", WidgetTokens.Role.LABEL.sp * fontScale);
                return elapsed;
            case "total":
                total = ui.mono("", WidgetTokens.Role.LABEL.sp * fontScale);
                total.setGravity(Gravity.END);
                return total;
            case "prev":
                previousButton = control(ui, GLYPH_PREVIOUS, 14, style().onSurface, true,
                    TopPaneFeed::skipPrevious);
                return previousButton;
            case "next":
                nextButton = control(ui, GLYPH_NEXT, 14, style().onSurface, true,
                    TopPaneFeed::skipNext);
                return nextButton;
            case "play":
                playButton = control(ui, GLYPH_PAUSE, 15, style().onPrimary, false,
                    this::onPlayPause);
                playButton.setBackground(new DiscDrawable(style().primary, ui.dp(DISC_DP)));
                return playButton;
            default:
                return null;
        }
    }

    @NonNull private View buildAccess(@NonNull BuiltinWidgetSpan span, @NonNull BuiltinWidgetUi ui) {
        boolean small = span == BuiltinWidgetSpan.ONE_BY_ONE;
        TextView message = ui.text(getContext().getString(R.string.bw_feeds_media_allow_access),
            small ? 11 : 12, style().sansBold, style().onSurface);
        message.setSingleLine(false);
        message.setMaxLines(small ? 3 : 2);
        message.setGravity(Gravity.CENTER);
        TextView hint = ui.mono(getContext().getString(R.string.bw_feeds_media_allow_access_hint),
            11f);
        hint.setVisibility(small ? GONE : VISIBLE);
        LinearLayout column = ui.column(6,
            ui.glyph(GLYPH_ACCESS, small ? 20 : 24, style().onSurfaceVariant), message, hint);
        column.setGravity(Gravity.CENTER);
        inset(column, 10, 8, 10, 8, ui);
        return column;
    }

    // ----- binding --------------------------------------------------------------------------

    private void bind() {
        if (!playerBuilt) return;
        TopPaneMediaState current = state;
        boolean has = current != null;
        boolean resuming = !has && mode == MediaLayoutPlanner.Mode.RESUME && resume != null;

        Bitmap bitmap = has ? current.art : null;
        Drawable icon = null;
        if (resuming) {
            try {
                icon = getContext().getPackageManager().getApplicationIcon(resume.packageName);
            } catch (PackageManager.NameNotFoundException e) {
                icon = null;
            }
        }
        if (art != null) {
            if (resuming) {
                if (icon != shownIcon) {
                    art.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    art.setImageDrawable(icon);
                    shownIcon = icon;
                    shownArt = null;
                }
            } else if (bitmap != shownArt || shownIcon != null) {
                // The feed's own bitmap, never a copy: the art costs nothing beyond what the feed holds.
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setImageBitmap(bitmap);
                shownArt = bitmap;
                shownIcon = null;
            }
            boolean shows = resuming ? icon != null : bitmap != null;
            art.setVisibility(shows ? VISIBLE : GONE);
            if (artPlaceholder != null) artPlaceholder.setVisibility(shows ? GONE : VISIBLE);
        }

        if (playButton != null) {
            boolean playing = has && current.playing;
            playButton.setText(playing ? GLYPH_PAUSE : GLYPH_PLAY);
            playButton.setContentDescription(resuming
                ? getContext().getString(R.string.bw_feeds_media_resume, resume.label)
                : getContext().getString(playing
                    ? R.string.bw_feeds_media_pause : R.string.bw_feeds_media_play));
            enable(playButton, has || resuming);
        }
        if (previousButton != null) enable(previousButton, has);
        if (nextButton != null) enable(nextButton, has);

        updateProgress();
        setContentDescription(describe(current, resuming));
        scheduleTick();
    }

    /** The bar and the clock labels only: the once-a-second work while a track plays. */
    private void updateProgress() {
        TopPaneMediaState current = state;
        long duration = current == null ? 0L : current.durationMs;
        long position = currentPositionMs();
        boolean known = current != null && duration > 0L;
        if (bar != null) {
            bar.set(known ? MediaWidgetFormats.fraction(position, duration) : 0f, style().primary);
        }
        if (elapsed != null) {
            setTextIfChanged(elapsed, known ? MediaWidgetFormats.clock(position) : "");
        }
        if (total != null) setTextIfChanged(total, known ? MediaWidgetFormats.clock(duration) : "");
    }

    @NonNull private String describe(@Nullable TopPaneMediaState current, boolean resuming) {
        if (current == null) {
            return resuming && resume != null
                ? getContext().getString(R.string.bw_feeds_media_resume, resume.label)
                : getContext().getString(R.string.bw_feeds_media_cd_nothing);
        }
        String titleText = current.title.isEmpty() ? current.appLabel : current.title;
        boolean byArtist = !current.artist.isEmpty() && !current.title.isEmpty();
        int format = current.playing
            ? (byArtist ? R.string.bw_feeds_media_cd_playing : R.string.bw_feeds_media_cd_playing_title)
            : (byArtist ? R.string.bw_feeds_media_cd_paused : R.string.bw_feeds_media_cd_paused_title);
        return byArtist ? getContext().getString(format, titleText, current.artist)
            : getContext().getString(format, titleText);
    }

    // ----- pieces ---------------------------------------------------------------------------

    /**
     * The art well: the striped stand-in with the session's artwork over it when there is some,
     * cut at the inner radius ({@link WidgetTokens#innerRadius}): the card's corner less
     * {@code insetDp}. It fills the box the plan gives it.
     */
    @NonNull private ArtFrame artFrame(@NonNull BuiltinWidgetUi ui, int insetDp, int bandDp) {
        ArtFrame frame = ui.followCorner(new ArtFrame(getContext()));
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                    ui.innerRadius(insetDp));
            }
        });
        frame.setClipToOutline(true);
        MediaArtPlaceholderView placeholder = new MediaArtPlaceholderView(getContext(),
            style().containerHigh, style().container, ui.dp(bandDp));
        frame.addView(placeholder, matchParent());
        ImageView image = new ImageView(getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        image.setVisibility(GONE);
        frame.addView(image, matchParent());
        artPlaceholder = placeholder;
        art = image;
        shownArt = null;
        shownIcon = null;
        return frame;
    }

    /**
     * A transport control: a glyph in a 48 dp target, on a {@link #DISC_DP} container disc when
     * {@code container} (the play button gets its primary disc from the caller).
     */
    @NonNull private TextView control(@NonNull BuiltinWidgetUi ui, @NonNull String glyph, float sp,
                                      @ColorInt int color, boolean container, @NonNull Runnable action) {
        ControlButton view = new ControlButton(ui, glyph, sp, color);
        if (container) view.setBackground(new DiscDrawable(style().containerHigh, ui.dp(DISC_DP)));
        int description = GLYPH_PREVIOUS.equals(glyph) ? R.string.bw_feeds_media_previous
            : GLYPH_NEXT.equals(glyph) ? R.string.bw_feeds_media_next
            : R.string.bw_feeds_media_pause;
        view.setContentDescription(getContext().getString(description));
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        view.setFocusable(true);
        view.setClickable(!isPreview());
        if (!isPreview()) view.setOnClickListener(v -> action.run());
        return view;
    }

    private static void enable(@NonNull View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.38f);
    }

    private static void setTextIfChanged(@NonNull TextView view, @NonNull String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }

    @NonNull private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
    }

    /** The art well: a frame the accessibility tree passes over. */
    private static final class ArtFrame extends FrameLayout {
        ArtFrame(@NonNull Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    /** The size a spec offers; unbounded when it offers none. */
    private static int room(int measureSpec) {
        return View.MeasureSpec.getMode(measureSpec) == View.MeasureSpec.UNSPECIFIED
            ? Integer.MAX_VALUE : View.MeasureSpec.getSize(measureSpec);
    }

    /**
     * A glyph button that is 48dp square where the room allows, 40dp where it does not, and 36dp
     * below that, never larger than the room it is given. The plan gives it 48.
     */
    private static final class ControlButton extends TextView {
        private final int touchPx, midPx, minPx;

        ControlButton(@NonNull BuiltinWidgetUi ui, @NonNull String glyph, float sp,
                      @ColorInt int color) {
            super(ui.context);
            touchPx = ui.dp(TOUCH_DP);
            midPx = ui.dp(TOUCH_MID_DP);
            minPx = ui.dp(TOUCH_MIN_DP);
            setText(glyph);
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            if (ui.style.nerd != null) setTypeface(ui.style.nerd);
            setTextColor(color);
            setGravity(Gravity.CENTER);
            setIncludeFontPadding(false);
        }

        /** 48dp where {@code room} allows it, 40dp, then 36dp, never more than the room. */
        private int target(int room) {
            return room >= touchPx ? touchPx : room >= midPx ? midPx : Math.min(room, minPx);
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                    MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                        ? MeasureSpec.getSize(heightMeasureSpec) : target(room(heightMeasureSpec)));
                return;
            }
            int side = target(Math.min(room(widthMeasureSpec), room(heightMeasureSpec)));
            setMeasuredDimension(side, side);
        }
    }

    /** A round fill of at most {@code discPx} across, centred in whatever bounds it is given. */
    private static final class DiscDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int discPx;

        DiscDrawable(@ColorInt int color, int discPx) {
            paint.setColor(color);
            this.discPx = discPx;
        }

        @Override public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float radius = Math.min(discPx, Math.min(bounds.width(), bounds.height())) / 2f;
            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(@Nullable ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
