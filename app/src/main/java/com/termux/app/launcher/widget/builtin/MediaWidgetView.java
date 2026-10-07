package com.termux.app.launcher.widget.builtin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
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
 * 4×2. The body opens the playing app. Without notification access the card asks for it, and a
 * tap opens the switch.
 */
public class MediaWidgetView extends BuiltinWidgetView {
    private static final String GLYPH_PLAY = "\uf04b";
    private static final String GLYPH_PAUSE = "\uf04c";
    private static final String GLYPH_PREVIOUS = "\uf048";
    private static final String GLYPH_NEXT = "\uf051";
    private static final String GLYPH_SHUFFLE = "\uf074";
    private static final String GLYPH_HEART = "\uf004";
    private static final String GLYPH_MUSIC = "\uf001";
    private static final String GLYPH_ACCESS = "\uf0f3";

    private static final int TOUCH_DP = 48;
    /** The touch target of a control whose row is a little short of 48dp. */
    private static final int TOUCH_MID_DP = 40;
    /** The smallest touch target; below this room a control takes what there is. */
    private static final int TOUCH_MIN_DP = 36;
    /** The 2x2 and 4x2 art is not drawn at less than this: below it the art is left out. */
    private static final int ART_MIN_DP = 40;
    /** The least visible space kept between the marks of a spread control row. */
    private static final int SPREAD_GAP_DP = 8;
    /** The previous and next controls, which go from a narrow row after the decorations. */
    private static final int CONTROL_RANK = 30;
    private static final int DECORATION_RANK = 10;
    private static final long TICK_MS = 1000L;

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

    // The views the current layout binds; null where the span has no such view.
    private boolean playerBuilt;
    @Nullable private ImageView art;
    @Nullable private View artPlaceholder;
    @Nullable private Bitmap shownArt;
    @Nullable private TextView title;
    @Nullable private TextView artist;
    @Nullable private TextView sourceLabel;
    @Nullable private TextView elapsedOfTotal;
    @Nullable private TextView elapsed;
    @Nullable private TextView total;
    @Nullable private BuiltinWidgetUi.BarView bar;
    @Nullable private TextView playButton;
    @Nullable private TextView previousButton;
    @Nullable private TextView nextButton;

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
        if (layoutChanged) {
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
        }
        state = next;
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
        if (current == null || current.packageName.isEmpty()) return;
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

    private void onPlayPause() {
        TopPaneMediaState current = state;
        if (current == null) return;
        boolean play = !current.playing;
        TopPaneFeed.togglePlayPause(play);
        // Flips the glyph now; the session's own callback reconciles it moments later.
        TopPaneFeed.applyOptimisticPlayState(play);
    }

    // ----- layouts --------------------------------------------------------------------------

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
        View root;
        switch (span) {
            case ONE_BY_ONE: root = buildOneByOne(ui); break;
            case TWO_BY_TWO: root = buildTwoByTwo(ui); break;
            case FOUR_BY_ONE: root = buildFourByOne(ui); break;
            case FOUR_BY_TWO: root = buildFourByTwo(ui); break;
            case TWO_BY_ONE:
            default: root = buildTwoByOne(ui); break;
        }
        frame.addView(root, matchParent());
        playerBuilt = true;
        bind();
    }

    private void clearRefs() {
        playerBuilt = false;
        art = null; artPlaceholder = null; shownArt = null;
        title = null; artist = null; sourceLabel = null;
        elapsedOfTotal = null; elapsed = null; total = null; bar = null;
        playButton = null; previousButton = null; nextButton = null;
    }

    @NonNull private View buildOneByOne(@NonNull BuiltinWidgetUi ui) {
        FrameLayout root = new FrameLayout(getContext());
        root.addView(artFrame(ui, 8, 6, false, 0), matchParent());
        playButton = control(ui, GLYPH_PAUSE, 15, style().onPrimary, 40, this::onPlayPause);
        root.addView(playButton, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        inset(root, 8, 8, 8, 8, ui);
        return root;
    }

    @NonNull private View buildTwoByOne(@NonNull BuiltinWidgetUi ui) {
        ArtFrame artView = artFrame(ui, 10, 6, false, 68);
        title = ui.text("", 12.5f, style().sansBold, style().onSurface);
        artist = ui.text("", 11, style().sansMedium, style().onSurfaceVariant);
        playButton = control(ui, GLYPH_PAUSE, 11, style().onPrimary, 28, this::onPlayPause);
        nextButton = control(ui, GLYPH_NEXT, 12, style().onSurfaceVariant, 0, TopPaneFeed::skipNext);
        FitStack controls = controlRow(ui, false, 10, playButton, 28, nextButton, 12);
        // At 56dp the title and the controls fill the card: the artist goes first. At 109dp
        // wide the card has no room for the art beside two controls, so the art goes too.
        FitStack column = FitStack.column(getContext()).centerAlong()
            .add(title, FitStack.ESSENTIAL, 0)
            .add(artist, 20, 0)
            .addShrink(controls, 30, controlGap(ui, 6, 28), ui.dp(TOUCH_DP), ui.dp(TOUCH_MIN_DP));
        FitStack row = FitStack.row(getContext()).centerAcross()
            .add(artView, 10, 0)
            .addFlex(column, FitStack.ESSENTIAL, ui.dp(10), ui.dp(2 * TOUCH_MIN_DP));
        inset(row, 10, 0, 10, 0, ui);
        return row;
    }

    @NonNull private View buildTwoByTwo(@NonNull BuiltinWidgetUi ui) {
        ArtFrame artView = artFrame(ui, 10, 6, false, 0);
        title = ui.text("", 13, style().sansBold, style().onSurface);
        artist = ui.text("", 11, style().sansMedium, style().onSurfaceVariant);
        bar = ui.bar(0f, style().primary, 4);
        LinearLayout.LayoutParams barParams = (LinearLayout.LayoutParams) bar.getLayoutParams();
        barParams.leftMargin = ui.dp(4);
        barParams.rightMargin = ui.dp(4);
        previousButton = control(ui, GLYPH_PREVIOUS, 13, style().onSurfaceVariant, 0,
            TopPaneFeed::skipPrevious);
        playButton = control(ui, GLYPH_PAUSE, 13, style().onPrimary, 34, this::onPlayPause);
        nextButton = control(ui, GLYPH_NEXT, 13, style().onSurfaceVariant, 0, TopPaneFeed::skipNext);
        FitStack controls = controlRow(ui, true, 0, previousButton, 13, playButton, 34,
            nextButton, 13);
        // At 115dp the card cannot hold the art, the bar, the artist and the transport together:
        // the art goes first, then the bar, then the artist; the title and the controls stay.
        artView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
        FitStack column = FitStack.column(getContext())
            .addFlex(artView, 5, 0, ui.dp(ART_MIN_DP))
            .add(indented(title, ui, 4), FitStack.ESSENTIAL, ui.dp(8))
            .add(indented(artist, ui, 4), 20, 0)
            .add(bar, 10, ui.dp(8))
            .addShrink(controls, 40, controlGap(ui, 8, 34), ui.dp(TOUCH_DP), ui.dp(TOUCH_MIN_DP));
        inset(column, 10, 10, 10, 10, ui);
        return column;
    }

    @NonNull private View buildFourByOne(@NonNull BuiltinWidgetUi ui) {
        ArtFrame artView = artFrame(ui, 10, 6, false, 68);
        title = ui.text("", 13, style().sansBold, style().onSurface);
        elapsedOfTotal = ui.mono("", 10.5f);
        LinearLayout head = ui.row(8, BuiltinWidgetUi.flex(title), elapsedOfTotal);
        bar = ui.bar(0f, style().primary, 4);
        previousButton = control(ui, GLYPH_PREVIOUS, 12, style().onSurface, 0,
            TopPaneFeed::skipPrevious);
        playButton = control(ui, GLYPH_PAUSE, 11, style().onPrimary, 28, this::onPlayPause);
        nextButton = control(ui, GLYPH_NEXT, 12, style().onSurface, 0, TopPaneFeed::skipNext);
        FitStack controls = controlRow(ui, false, 18,
            decoration(ui, GLYPH_SHUFFLE, 12), 12, previousButton, 12, playButton, 28,
            nextButton, 12, decoration(ui, GLYPH_HEART, 12), 12);
        // At 56dp the title line and the controls fill the card: the progress bar goes. At 245dp
        // wide the shuffle and heart marks go from the controls before the buttons do.
        FitStack column = FitStack.column(getContext()).centerAlong()
            .add(BuiltinWidgetUi.size(head, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT), FitStack.ESSENTIAL, 0)
            .add(bar, 10, ui.dp(7))
            .addShrink(controls, 20, controlGap(ui, 7, 28), ui.dp(TOUCH_DP), ui.dp(TOUCH_MIN_DP));
        FitStack row = FitStack.row(getContext()).centerAcross()
            .add(artView, 10, 0)
            .addFlex(column, FitStack.ESSENTIAL, ui.dp(12), ui.dp(3 * TOUCH_MIN_DP));
        inset(row, 12, 0, 12, 0, ui);
        return row;
    }

    @NonNull private View buildFourByTwo(@NonNull BuiltinWidgetUi ui) {
        ArtFrame artView = artFrame(ui, 12, 7, true, 0);
        artView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        sourceLabel = ui.mono("", 10.5f);
        LinearLayout source = ui.row(5, ui.glyph(GLYPH_MUSIC, 10.5f, style().onSurfaceVariant),
            BuiltinWidgetUi.flex(sourceLabel));
        title = ui.text("", 17, style().sansBold, style().onSurface);
        artist = ui.text("", 12, style().sansMedium, style().onSurfaceVariant);

        bar = ui.bar(0f, style().primary, 4);
        elapsed = ui.mono("", 10.5f);
        total = ui.mono("", 10.5f);
        LinearLayout times = wide(ui.row(8, BuiltinWidgetUi.flex(elapsed), total));
        LinearLayout progress = wide(ui.column(5, bar, times));

        previousButton = control(ui, GLYPH_PREVIOUS, 13, style().onSurface, 0,
            TopPaneFeed::skipPrevious);
        playButton = control(ui, GLYPH_PAUSE, 14, style().onPrimary, 38, this::onPlayPause);
        nextButton = control(ui, GLYPH_NEXT, 13, style().onSurface, 0, TopPaneFeed::skipNext);
        FitStack controls = controlRow(ui, true, 0,
            decoration(ui, GLYPH_SHUFFLE, 13), 13, previousButton, 13, playButton, 38,
            nextButton, 13, decoration(ui, GLYPH_HEART, 13), 13);

        // At 115dp the column holds the title, the artist and the controls and no more: the
        // progress goes first, then the source, then the artist. The title stays; the 12dp
        // gap under the source and the progress's own 12dp are the design's.
        FitStack column = FitStack.column(getContext())
            .add(BuiltinWidgetUi.size(source, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT), 20, 0)
            .add(title, FitStack.ESSENTIAL, ui.dp(12))
            .add(artist, 30, ui.dp(6))
            .addElastic(progress, 15, ui.dp(12))
            .addShrink(controls, 25, controlGap(ui, 6, 38), ui.dp(TOUCH_DP), ui.dp(TOUCH_MIN_DP));
        inset(column, 0, 4, 4, 4, ui);
        column.setLayoutParams(new ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));

        FitStack row = FitStack.row(getContext())
            .add(artView, 10, 0)
            .addFlex(column, FitStack.ESSENTIAL, ui.dp(16), ui.dp(3 * TOUCH_MIN_DP));
        inset(row, 12, 12, 12, 12, ui);
        return row;
    }

    @NonNull private View buildAccess(@NonNull BuiltinWidgetSpan span, @NonNull BuiltinWidgetUi ui) {
        boolean small = span == BuiltinWidgetSpan.ONE_BY_ONE;
        TextView message = ui.text(getContext().getString(R.string.bw_feeds_media_allow_access),
            small ? 11 : 12, style().sansBold, style().onSurface);
        message.setSingleLine(false);
        message.setMaxLines(small ? 3 : 2);
        message.setGravity(Gravity.CENTER);
        TextView hint = ui.mono(getContext().getString(R.string.bw_feeds_media_allow_access_hint),
            10.5f);
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

        String titleText = has ? (current.title.isEmpty() ? current.appLabel : current.title)
            : getContext().getString(R.string.bw_feeds_media_nothing_playing);
        String artistText = has && !current.title.isEmpty()
            ? (current.artist.isEmpty() ? current.appLabel : current.artist) : "";
        if (artist != null) {
            artist.setText(artistText);
            artist.setVisibility(artistText.isEmpty() ? GONE : VISIBLE);
        }
        if (title != null) {
            // 4×1 has one line for both: the artist follows the title in the quieter weight.
            title.setText(artist == null && !artistText.isEmpty()
                ? titleWithArtist(titleText, artistText) : titleText);
        }
        if (sourceLabel != null) {
            sourceLabel.setText(has && !current.appLabel.isEmpty() ? current.appLabel
                : getContext().getString(kind.label));
        }

        Bitmap bitmap = has ? current.art : null;
        if (art != null && bitmap != shownArt) {
            // The feed's own bitmap, never a copy: the art costs nothing beyond what the feed holds.
            art.setImageBitmap(bitmap);
            shownArt = bitmap;
        }
        if (art != null) art.setVisibility(bitmap != null ? VISIBLE : GONE);
        if (artPlaceholder != null) artPlaceholder.setVisibility(bitmap != null ? GONE : VISIBLE);

        if (playButton != null) {
            boolean playing = has && current.playing;
            playButton.setText(playing ? GLYPH_PAUSE : GLYPH_PLAY);
            playButton.setContentDescription(getContext().getString(playing
                ? R.string.bw_feeds_media_pause : R.string.bw_feeds_media_play));
            enable(playButton, has);
        }
        if (previousButton != null) enable(previousButton, has);
        if (nextButton != null) enable(nextButton, has);

        updateProgress();
        setContentDescription(describe(current, titleText));
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
        if (elapsedOfTotal != null) {
            setTextIfChanged(elapsedOfTotal,
                known ? MediaWidgetFormats.elapsedOfTotal(position, duration) : "");
        }
        if (elapsed != null) {
            setTextIfChanged(elapsed, known ? MediaWidgetFormats.clock(position) : "");
        }
        if (total != null) setTextIfChanged(total, known ? MediaWidgetFormats.clock(duration) : "");
    }

    @NonNull private String describe(@Nullable TopPaneMediaState current, @NonNull String titleText) {
        if (current == null) return getContext().getString(R.string.bw_feeds_media_cd_nothing);
        boolean byArtist = !current.artist.isEmpty() && !current.title.isEmpty();
        int format = current.playing
            ? (byArtist ? R.string.bw_feeds_media_cd_playing : R.string.bw_feeds_media_cd_playing_title)
            : (byArtist ? R.string.bw_feeds_media_cd_paused : R.string.bw_feeds_media_cd_paused_title);
        return byArtist ? getContext().getString(format, titleText, current.artist)
            : getContext().getString(format, titleText);
    }

    @NonNull private CharSequence titleWithArtist(@NonNull String titleText, @NonNull String artistText) {
        SpannableStringBuilder text = new SpannableStringBuilder(titleText);
        int start = text.length();
        text.append(" · ").append(artistText);
        text.setSpan(new ForegroundColorSpan(style().onSurfaceVariant), start, text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new FaceSpan(style().sansMedium), start, text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    // ----- pieces ---------------------------------------------------------------------------

    /**
     * The art well: the striped stand-in with the session's artwork over it when there is some,
     * rounded to the card's corner less {@code insetDp}. {@code desiredDp} makes it a square of
     * that size that settles for the room it is given; zero fills what the parent gives.
     */
    @NonNull private ArtFrame artFrame(@NonNull BuiltinWidgetUi ui, int insetDp, int bandDp,
                                       boolean square, int desiredDp) {
        ArtFrame frame = new ArtFrame(getContext(), square, ui.dp(desiredDp));
        float radius = ui.innerRadius(insetDp);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
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
        return frame;
    }

    /**
     * A transport control: a glyph, on a {@code discDp} primary disc when that is non-zero, in a
     * touch target of up to 48dp whose extra room is transparent; the target is 40dp, then
     * 36dp, as its room shrinks, and never past its parent.
     */
    @NonNull private TextView control(@NonNull BuiltinWidgetUi ui, @NonNull String glyph, float sp,
                                      @ColorInt int color, int discDp, @NonNull Runnable action) {
        ControlButton view = new ControlButton(ui, glyph, sp, color);
        if (discDp > 0) view.setBackground(new DiscDrawable(style().primary, ui.dp(discDp)));
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

    /** A glyph the design draws in the deck that the session does not offer: shown, not pressed. */
    @NonNull private TextView decoration(@NonNull BuiltinWidgetUi ui, @NonNull String glyph, float sp) {
        TextView view = ui.glyph(glyph, sp, style().onSurfaceVariant);
        view.setClickable(false);
        view.setFocusable(false);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    /**
     * Lays {@code items} (a view, then the dp it visibly occupies, repeated) in a row whose visible
     * gaps are the design's {@code gapDp} — or spread edge to edge, at least
     * {@link #SPREAD_GAP_DP} apart, when {@code spread}. Each control keeps its touch target; the
     * transparent margin of a target is taken out of the gap beside it, so neighbours' targets
     * overlap a little rather than the visible marks drifting apart. When the row is too narrow
     * the decorations go first, then the previous and next controls; the play control stays.
     */
    @NonNull private FitStack controlRow(@NonNull BuiltinWidgetUi ui, boolean spread, int gapDp,
                                         @NonNull Object... items) {
        FitStack row = FitStack.row(getContext()).centerAcross();
        View before = null;
        int beforeVisible = 0;
        for (int i = 0; i < items.length / 2; i++) {
            View view = (View) items[i * 2];
            int visible = (Integer) items[i * 2 + 1];
            int rank = view == playButton ? FitStack.ESSENTIAL
                : view instanceof ControlButton ? CONTROL_RANK : DECORATION_RANK;
            if (before == null) {
                row.add(view, rank, 0);
            } else {
                int gap = ui.dp((spread ? SPREAD_GAP_DP : gapDp)
                    - overhang(before, beforeVisible) - overhang(view, visible));
                if (spread) row.addElastic(view, rank, gap);
                else row.add(view, rank, gap);
            }
            before = view;
            beforeVisible = visible;
        }
        row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** What a control's smallest target adds either side of the {@code visibleDp} it shows. */
    private static float overhang(@NonNull View view, int visibleDp) {
        return view instanceof ControlButton ? Math.max(0f, (TOUCH_MIN_DP - visibleDp) / 2f) : 0f;
    }

    /**
     * The gap above a control row for the design's {@code gapDp} between what is visible: the
     * row's target is taller than the {@code visibleDp} it shows, and the difference sits above it.
     */
    private static int controlGap(@NonNull BuiltinWidgetUi ui, int gapDp, int visibleDp) {
        return Math.max(0, ui.dp(gapDp - (TOUCH_DP - visibleDp) / 2f));
    }

    /** {@code view} across its column, {@code insetDp} in from either side. */
    @NonNull private static <V extends View> V indented(@NonNull V view, @NonNull BuiltinWidgetUi ui,
                                                        int insetDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = ui.dp(insetDp);
        params.rightMargin = ui.dp(insetDp);
        view.setLayoutParams(params);
        return view;
    }

    private static void enable(@NonNull View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.38f);
    }

    private static void setTextIfChanged(@NonNull TextView view, @NonNull String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }

    @NonNull private static <V extends View> V wide(@NonNull V view) {
        ViewGroup.LayoutParams existing = view.getLayoutParams();
        if (existing instanceof LinearLayout.LayoutParams) {
            existing.width = ViewGroup.LayoutParams.MATCH_PARENT;
            view.setLayoutParams(existing);
        } else {
            view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return view;
    }

    @NonNull private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
    }

    /**
     * The art well. With a desired size it is a square of that size, or of the room it is given
     * if that is smaller; at 4×2 it is square to its height, which the card decides.
     */
    private static final class ArtFrame extends FrameLayout {
        private final boolean square;
        private final int desiredPx;

        ArtFrame(@NonNull Context context, boolean square, int desiredPx) {
            super(context);
            this.square = square;
            this.desiredPx = desiredPx;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (desiredPx > 0) {
                int side = Math.min(desiredPx, Math.min(room(widthMeasureSpec),
                    room(heightMeasureSpec)));
                int exact = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
                super.onMeasure(exact, exact);
                return;
            }
            if (square && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                int side = MeasureSpec.getSize(heightMeasureSpec);
                int exact = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
                super.onMeasure(exact, exact);
                return;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /** The size a spec offers; unbounded when it offers none. */
    private static int room(int measureSpec) {
        return View.MeasureSpec.getMode(measureSpec) == View.MeasureSpec.UNSPECIFIED
            ? Integer.MAX_VALUE : View.MeasureSpec.getSize(measureSpec);
    }

    /**
     * A glyph button that is 48dp square where the room allows, 40dp where it does not, and 36dp
     * below that, never larger than the room it is given.
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

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int room = Math.min(room(widthMeasureSpec), room(heightMeasureSpec));
            int side = room >= touchPx ? touchPx : room >= midPx ? midPx : Math.min(room, minPx);
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

    /** A typeface for part of a line, on every API level the launcher runs on. */
    private static final class FaceSpan extends MetricAffectingSpan {
        @NonNull private final Typeface face;
        FaceSpan(@NonNull Typeface face) { this.face = face; }
        @Override public void updateDrawState(@NonNull TextPaint paint) { paint.setTypeface(face); }
        @Override public void updateMeasureState(@NonNull TextPaint paint) { paint.setTypeface(face); }
    }
}
