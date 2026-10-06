package com.termux.app.launcher.widget.builtin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
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
            } else {
                anchorPositionMs = next.positionMs;
            }
            anchorAtMs = now;
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
        root.addView(artFrame(ui, 8, 6, false), matchParent());
        playButton = control(ui, GLYPH_PAUSE, 15, style().onPrimary, 40, this::onPlayPause);
        root.addView(playButton, new FrameLayout.LayoutParams(ui.dp(TOUCH_DP), ui.dp(TOUCH_DP),
            Gravity.CENTER));
        inset(root, 8, 8, 8, 8, ui);
        return root;
    }

    @NonNull private View buildTwoByOne(@NonNull BuiltinWidgetUi ui) {
        View artView = BuiltinWidgetUi.size(artFrame(ui, 10, 6, false), ui.dp(68), ui.dp(68));
        title = ui.text("", 12.5f, style().sansBold, style().onSurface);
        artist = ui.text("", 11, style().sansMedium, style().onSurfaceVariant);
        playButton = control(ui, GLYPH_PAUSE, 11, style().onPrimary, 28, this::onPlayPause);
        nextButton = control(ui, GLYPH_NEXT, 12, style().onSurfaceVariant, 0, TopPaneFeed::skipNext);
        LinearLayout controls = controlRow(ui, 10, false, playButton, 28, nextButton, 12);
        LinearLayout column = ui.column(6, ui.column(0, title, artist), controls);
        settle(controls, 6, 28, ui);
        LinearLayout row = ui.row(10, artView, BuiltinWidgetUi.flex(column));
        inset(row, 10, 0, 10, 0, ui);
        return row;
    }

    @NonNull private View buildTwoByTwo(@NonNull BuiltinWidgetUi ui) {
        View artView = BuiltinWidgetUi.flexTall(artFrame(ui, 10, 6, false));
        title = ui.text("", 13, style().sansBold, style().onSurface);
        artist = ui.text("", 11, style().sansMedium, style().onSurfaceVariant);
        LinearLayout texts = ui.column(0, title, artist);
        inset(texts, 4, 0, 4, 0, ui);
        bar = ui.bar(0f, style().primary, 4);
        LinearLayout.LayoutParams barParams = (LinearLayout.LayoutParams) bar.getLayoutParams();
        barParams.leftMargin = ui.dp(4);
        barParams.rightMargin = ui.dp(4);
        previousButton = control(ui, GLYPH_PREVIOUS, 13, style().onSurfaceVariant, 0,
            TopPaneFeed::skipPrevious);
        playButton = control(ui, GLYPH_PAUSE, 13, style().onPrimary, 34, this::onPlayPause);
        nextButton = control(ui, GLYPH_NEXT, 13, style().onSurfaceVariant, 0, TopPaneFeed::skipNext);
        LinearLayout controls = controlRow(ui, 0, true, previousButton, 13, playButton, 34,
            nextButton, 13);
        controls.setPadding(ui.dp(10), 0, ui.dp(10), 0);
        LinearLayout column = ui.column(8, artView, wide(texts), bar, wide(controls));
        settle(controls, 8, 34, ui);
        inset(column, 10, 10, 10, 10, ui);
        return column;
    }

    @NonNull private View buildFourByOne(@NonNull BuiltinWidgetUi ui) {
        View artView = BuiltinWidgetUi.size(artFrame(ui, 10, 6, false), ui.dp(68), ui.dp(68));
        title = ui.text("", 13, style().sansBold, style().onSurface);
        elapsedOfTotal = ui.mono("", 10.5f);
        LinearLayout head = ui.row(8, BuiltinWidgetUi.flex(title), elapsedOfTotal);
        bar = ui.bar(0f, style().primary, 4);
        previousButton = control(ui, GLYPH_PREVIOUS, 12, style().onSurface, 0,
            TopPaneFeed::skipPrevious);
        playButton = control(ui, GLYPH_PAUSE, 11, style().onPrimary, 28, this::onPlayPause);
        nextButton = control(ui, GLYPH_NEXT, 12, style().onSurface, 0, TopPaneFeed::skipNext);
        LinearLayout controls = controlRow(ui, 18, false,
            decoration(ui, GLYPH_SHUFFLE, 12), 12, previousButton, 12, playButton, 28,
            nextButton, 12, decoration(ui, GLYPH_HEART, 12), 12);
        LinearLayout column = ui.column(7, wide(head), bar, controls);
        settle(controls, 7, 28, ui);
        LinearLayout row = ui.row(12, artView, BuiltinWidgetUi.flex(column));
        inset(row, 12, 0, 12, 0, ui);
        return row;
    }

    @NonNull private View buildFourByTwo(@NonNull BuiltinWidgetUi ui) {
        ArtFrame artView = artFrame(ui, 12, 7, true);
        artView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
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
        LinearLayout controls = controlRow(ui, 0, true,
            decoration(ui, GLYPH_SHUFFLE, 13), 13, previousButton, 13, playButton, 38,
            nextButton, 13, decoration(ui, GLYPH_HEART, 13), 13);

        LinearLayout column = ui.column(6, wide(source), title, artist,
            BuiltinWidgetUi.flexTall(new View(getContext())), progress, wide(controls));
        ((LinearLayout.LayoutParams) title.getLayoutParams()).topMargin = ui.dp(12);
        settle(controls, 6, 38, ui);
        inset(column, 0, 4, 4, 4, ui);
        column.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout row = ui.row(16, artView, column);
        row.setGravity(Gravity.TOP);
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
     * rounded to the card's corner less {@code insetDp}.
     */
    @NonNull private ArtFrame artFrame(@NonNull BuiltinWidgetUi ui, int insetDp, int bandDp,
                                       boolean square) {
        ArtFrame frame = new ArtFrame(getContext(), square);
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
     * 48dp touch target whose extra room is transparent.
     */
    @NonNull private TextView control(@NonNull BuiltinWidgetUi ui, @NonNull String glyph, float sp,
                                      @ColorInt int color, int discDp, @NonNull Runnable action) {
        TextView view = ui.glyph(glyph, sp, color);
        if (discDp > 0) {
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(style().primary);
            view.setBackground(new InsetDrawable(disc, ui.dp((TOUCH_DP - discDp) / 2f)));
        }
        view.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(TOUCH_DP), ui.dp(TOUCH_DP)));
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
     * gaps are the design's {@code gapDp} — or spread edge to edge when {@code spread} — while each
     * control keeps its full 48dp target. A target's transparent margin is taken back with negative
     * margins, so in the row it occupies only what it shows and its touch area overlaps the gaps.
     * Views without layout params (the decorations) are laid at their own size.
     */
    @NonNull private LinearLayout controlRow(@NonNull BuiltinWidgetUi ui, int gapDp, boolean spread,
                                             @NonNull Object... items) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < items.length / 2; i++) {
            View view = (View) items[i * 2];
            int visible = (Integer) items[i * 2 + 1];
            ViewGroup.LayoutParams existing = view.getLayoutParams();
            boolean target = existing instanceof LinearLayout.LayoutParams;
            LinearLayout.LayoutParams params = target ? (LinearLayout.LayoutParams) existing
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            int pad = target ? ui.dp((TOUCH_DP - visible) / 2f) : 0;
            if (i > 0 && spread) {
                row.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 0, 1f));
            }
            params.leftMargin = (i > 0 && !spread ? ui.dp(gapDp) : 0) - pad;
            params.rightMargin = -pad;
            row.addView(view, params);
        }
        return row;
    }

    /**
     * Seats a control row that follows a {@code gapDp} gap: its 48dp targets are taller than the
     * {@code visibleDp} it shows, so the extra is taken back from the gap above and the padding
     * below, keeping the visible spacing the design's.
     */
    private static void settle(@NonNull View row, int gapDp, int visibleDp, @NonNull BuiltinWidgetUi ui) {
        int overhang = ui.dp((TOUCH_DP - visibleDp) / 2f);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getLayoutParams();
        params.topMargin = ui.dp(gapDp) - overhang;
        params.bottomMargin = -overhang;
        row.setLayoutParams(params);
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

    /** The art well; square to its height at 4×2, where the card's height decides its size. */
    private static final class ArtFrame extends FrameLayout {
        private final boolean square;

        ArtFrame(@NonNull Context context, boolean square) {
            super(context);
            this.square = square;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (square && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                int side = MeasureSpec.getSize(heightMeasureSpec);
                int exact = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY);
                super.onMeasure(exact, exact);
                return;
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /** A typeface for part of a line, on every API level the launcher runs on. */
    private static final class FaceSpan extends MetricAffectingSpan {
        @NonNull private final Typeface face;
        FaceSpan(@NonNull Typeface face) { this.face = face; }
        @Override public void updateDrawState(@NonNull TextPaint paint) { paint.setTypeface(face); }
        @Override public void updateMeasureState(@NonNull TextPaint paint) { paint.setTypeface(face); }
    }
}
