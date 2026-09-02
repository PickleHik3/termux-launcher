package juloo.keyboard2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class Keyboard2View extends View
  implements View.OnTouchListener, Pointers.IPointerEventHandler
{
  private KeyboardData _keyboard;

  /** Shift key retained for modifier-state bookkeeping. */
  private KeyboardData.Key _shift_key;

  /** Used to add fake pointers. */
  private KeyboardData.Key _compose_key;

  private Pointers _pointers;

  private Pointers.Modifiers _mods;

  private final Config _config;

  /** Host-owned scale applied to row height and the available-height cap during measurement. */
  private float _heightScale = 1f;

  /** Host-owned multiplier for both Config key-margin ratios. */
  private float _keyMarginScale = 1f;

  /** Host-owned radius override in px, or -1 to use Config/palette geometry. */
  private float _keyCornerRadiusOverridePx = -1f;
  /** Absolute key cap background opacity (0..1), or -1 to keep the theme's translucency. */
  private float _keyOpacity = -1f;

  /** Stable host content height used as the fractional keyboard-height cap reference. */
  private int _heightCapReferencePx;

  private float _keyWidth;
  private float _mainLabelSize;
  private float _subLabelSize;
  private float _marginRight;
  private float _marginLeft;
  private float _marginBottom;

  private Theme _theme;
  private Theme.Computed _tc;
  private final Paint _overrideBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint _overrideBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private SparseArray<KeyColorOverride> _keyColorOverrides = new SparseArray<>();
  /** Transient keybind-hint lighting; sits above the color-scheme overrides. */
  private SparseArray<KeyColorOverride> _hintColorOverrides = new SparseArray<>();
  private ValueAnimator _hintBreathAnimator;
  /** 0..1 sine wave driving the hint lighting's slow breathing. */
  private float _hintBreathWave;
  private OnKeyPaintListener _keyPaintListener;
  private String _lastPaintedKeyId;
  private float _launchWaveDensity;
  private final int[] _spaceBarLocation = new int[2];
  private final Paint _trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint _fxFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint _fxStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint _fxHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint _launchWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final SparseArray<Trail> _trails = new SparseArray<Trail>();
  private final SparseArray<TouchFx> _touchFx = new SparseArray<TouchFx>();
  private final ArrayList<TouchFx> _releasedTouchFx = new ArrayList<TouchFx>();

  /**
   * Host hook that may move a press onto a neighbouring key before it is
   * committed, and that is told where every tap landed. Local addition, see
   * UPSTREAM.md. Consulted only for real touches, never for the colour-editor
   * paint path.
   */
  public interface TapResolver
  {
    /**
     * Returns the index in [geometry] of the key the press should resolve to.
     * Returning [rawIndex] or an invalid index leaves the press alone.
     */
    int resolveTap(TapGeometry geometry, int rawIndex, float x, float y);

    /** A press has been released. [swiped] is true when the finger travelled. */
    void observeTap(TapGeometry geometry, int rawIndex, float x, float y,
        boolean swiped);
  }

  private TapResolver _tapResolver;
  private TapGeometry _tapGeometry;

  private static final long PRESS_RAMP_MS = 60L;
  private static final long RELEASE_FADE_MS = 150L;
  private static final long LAUNCH_WAVE_TRAVEL_MS = 250L;
  private static final long LAUNCH_WAVE_TOTAL_MS = 400L;
  private static final long LAUNCH_WAVE_FADE_MS = 80L;
  private ValueAnimator _launchWaveAnimator;
  private float _launchWaveProgress = -1f;
  private float _launchWaveOriginX;
  private float _launchWaveOriginY;
  private float _launchWaveOpacity;
  private int _launchWaveColor;

  private final RectF _tmpRect = new RectF();

  enum Vertical
  {
    TOP,
    CENTER,
    BOTTOM
  }

  public Keyboard2View(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    _theme = new Theme(getContext(), attrs);
    _config = Config.preview(getResources());
    _pointers = new Pointers(this, _config);
    initialize();
    int layout_id = (attrs == null) ? 0 :
      attrs.getAttributeResourceValue(null, "layout", 0);
    if (layout_id == 0)
      setKeyboard(Objects.requireNonNull(
          KeyboardData.load(getResources(), R.xml.latn_qwerty_us)));
    else
      setKeyboard(Objects.requireNonNull(KeyboardData.load(getResources(), layout_id)));
  }

  public Keyboard2View(Context context)
  {
    this(context, (AttributeSet)null);
  }

  public Keyboard2View(Context context, Config config, Theme.Palette palette)
  {
    super(context);
    _config = Objects.requireNonNull(config, "config");
    _theme = new Theme(context, Objects.requireNonNull(palette, "palette"));
    _pointers = new Pointers(this, _config);
    initialize();
    setKeyboard(Objects.requireNonNull(
        KeyboardData.load(getResources(), R.xml.latn_qwerty_us)));
  }

  private void initialize()
  {
    setOnTouchListener(this);
    _trailPaint.setStyle(Paint.Style.STROKE);
    _trailPaint.setStrokeCap(Paint.Cap.ROUND);
    _fxFillPaint.setStyle(Paint.Style.FILL);
    _fxStrokePaint.setStyle(Paint.Style.STROKE);
    _fxStrokePaint.setStrokeCap(Paint.Cap.ROUND);
    _fxHaloPaint.setStyle(Paint.Style.STROKE);
    _fxHaloPaint.setStrokeCap(Paint.Cap.ROUND);
    _launchWaveDensity = getResources().getDisplayMetrics().density;
    applyTheme();
  }

  private void applyTheme()
  {
    _trailPaint.setColor(_theme.pressedColor);
    _trailPaint.setStrokeWidth(_config.swipeTrailWidthPx);
    _fxFillPaint.setColor(_theme.pressedColor);
    _fxStrokePaint.setColor(_theme.pressedColor);
    _fxHaloPaint.setColor(_theme.pressedColor);
    setBackgroundColor(withOpacity(_theme.colorKeyboard, _theme.opacity));
  }

  /** Brief host-triggered wave that subtly modulates each key as the dock front reaches it. */
  public void animateLaunchWave(int color, float originX, float originY)
  {
    requireMainThread();
    cancelLaunchWaveAnimator();
    _launchWaveColor = color;
    // Keep the real dock icon centre, including its normally-negative local Y. All key chips then
    // sample one wavefront from that same source instead of behaving like independent emitters.
    _launchWaveOriginX = originX;
    _launchWaveOriginY = originY;
    _launchWaveOpacity = 1f;
    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    _launchWaveAnimator = animator;
    animator.setDuration(LAUNCH_WAVE_TOTAL_MS);
    animator.setInterpolator(new LinearInterpolator());
    animator.addUpdateListener(animation -> {
      _launchWaveProgress = (Float) animation.getAnimatedValue();
      invalidate();
    });
    animator.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(Animator animation)
      {
        if (_launchWaveAnimator == animation)
          resetLaunchWave();
      }
    });
    animator.start();
  }

  /** Gracefully hands the key-chip modulation to a keyboard/style transition. */
  public void fadeOutLaunchWave()
  {
    requireMainThread();
    if (_launchWaveProgress < 0f || _launchWaveOpacity <= 0f)
      return;
    float startOpacity = _launchWaveOpacity;
    cancelLaunchWaveAnimator();
    ValueAnimator animator = ValueAnimator.ofFloat(startOpacity, 0f);
    _launchWaveAnimator = animator;
    animator.setDuration(LAUNCH_WAVE_FADE_MS);
    animator.setInterpolator(new LinearInterpolator());
    animator.addUpdateListener(animation -> {
      _launchWaveOpacity = (Float) animation.getAnimatedValue();
      invalidate();
    });
    animator.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(Animator animation)
      {
        if (_launchWaveAnimator == animation)
          resetLaunchWave();
      }
    });
    animator.start();
  }

  /** Per-key colors supplied by a host-side color-scheme editor. Null fields inherit the theme. */
  public static final class KeyColorOverride
  {
    public final Integer keyBackground;
    public final Integer primaryLabel;
    public final Integer secondaryLabel;
    public final Integer secondaryBottomLabel;
    public final Integer borderColor;

    public KeyColorOverride(Integer keyBackground, Integer primaryLabel,
        Integer secondaryLabel, Integer secondaryBottomLabel, Integer borderColor)
    {
      this.keyBackground = keyBackground;
      this.primaryLabel = primaryLabel;
      this.secondaryLabel = secondaryLabel;
      this.secondaryBottomLabel = secondaryBottomLabel;
      this.borderColor = borderColor;
    }
  }

  /** Receives stable key ids while the user taps or drags across an editor preview. */
  public interface OnKeyPaintListener
  {
    void onPaintKey(String keyId);
  }

  public void setKeyColorOverrides(Map<String, KeyColorOverride> overrides)
  {
    requireMainThread();
    _keyColorOverrides.clear();
    if (overrides != null) {
      for (Map.Entry<String, KeyColorOverride> e : overrides.entrySet()) {
        int id = parseKeyId(e.getKey());
        if (id >= 0)
          _keyColorOverrides.put(id, e.getValue());
      }
    }
    invalidate();
  }

  /**
   * Transient lighting for the keybind hint popup: while a modifier prefix is latched, the
   * bound caps take these colors on the live keyboard itself. Sits above (and never touches)
   * the user's color-scheme overrides; null or empty clears it wholesale. While set, the lit
   * fills and borders breathe slowly — a gentle brightness swell driven by one animator.
   */
  public void setKeybindHintOverrides(Map<String, KeyColorOverride> overrides)
  {
    requireMainThread();
    boolean clearing = overrides == null || overrides.isEmpty();
    if (clearing && _hintColorOverrides.size() > 0 && _hintFadeAnimator == null
        && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled())
    {
      // Clearing snaps the whole keyboard back at once, which reads as a broad flash after
      // every shortcut. Fade the lighting toward the underlying colors instead, then clear.
      ValueAnimator fade = ValueAnimator.ofFloat(1f, 0f);
      fade.setDuration(HINT_FADE_MS);
      fade.addUpdateListener(a -> {
        _hintFade = (Float) a.getAnimatedValue();
        invalidate();
      });
      fade.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation)
        {
          _hintFadeAnimator = null;
          _hintFade = 1f;
          _hintColorOverrides.clear();
          updateHintBreathAnimator();
          invalidate();
        }
      });
      _hintFadeAnimator = fade;
      fade.start();
      return;
    }
    if (_hintFadeAnimator != null)
      _hintFadeAnimator.cancel(); // end listener clears the faded-out overrides first
    _hintColorOverrides.clear();
    if (overrides != null) {
      for (Map.Entry<String, KeyColorOverride> e : overrides.entrySet()) {
        int id = parseKeyId(e.getKey());
        if (id >= 0)
          _hintColorOverrides.put(id, e.getValue());
      }
    }
    updateHintBreathAnimator();
    invalidate();
  }

  /** Fade-out of the hint lighting after the prefix releases. */
  private static final long HINT_FADE_MS = 260L;
  private ValueAnimator _hintFadeAnimator;
  /** 1 = hint colors at full strength, 0 = fully returned to the underlying colors. */
  private float _hintFade = 1f;

  /** The hint override blended toward what the key would paint without it. */
  private KeyColorOverride fadeHintOverride(KeyColorOverride hint, KeyColorOverride base,
      Theme.Computed.Key tc)
  {
    return new KeyColorOverride(
        fadeHintColor(hint.keyBackground, base == null ? null : base.keyBackground,
            tc.bg_paint.getColor()),
        fadeHintColor(hint.primaryLabel, base == null ? null : base.primaryLabel, tc.labelColor),
        fadeHintColor(hint.secondaryLabel, base == null ? null : base.secondaryLabel,
            tc.subLabelColor),
        fadeHintColor(hint.secondaryBottomLabel,
            base == null ? null : base.secondaryBottomLabel, tc.subLabelColor),
        fadeHintColor(hint.borderColor, base == null ? null : base.borderColor,
            tc.border_left_paint.getColor()));
  }

  private Integer fadeHintColor(Integer hintColor, Integer baseColor, int themeDefault)
  {
    if (hintColor == null)
      return baseColor;
    int target = baseColor != null ? baseColor : themeDefault;
    return lerpColor(target, hintColor, _hintFade);
  }

  private static int lerpColor(int from, int to, float t)
  {
    return Color.argb(
        Color.alpha(from) + Math.round((Color.alpha(to) - Color.alpha(from)) * t),
        Color.red(from) + Math.round((Color.red(to) - Color.red(from)) * t),
        Color.green(from) + Math.round((Color.green(to) - Color.green(from)) * t),
        Color.blue(from) + Math.round((Color.blue(to) - Color.blue(from)) * t));
  }

  /** Period of one full breath of the hint lighting. Deliberately slow and shallow. */
  private static final long HINT_BREATH_PERIOD_MS = 3800L;
  /** How far a lit color sinks toward black at the bottom of a breath. */
  private static final float HINT_BREATH_DEPTH = 0.18f;
  /**
   * The invitation's beacon. One cap — the ? that opens the full keymap — is not a binding among
   * the lit ones but the way to see the rest of them, so it must not read as a louder version of
   * what they are doing. They swell and sink on a slow sine; this snaps bright and eases back, and
   * it lifts toward white rather than dimming, so the two signals differ in kind and not degree.
   */
  private static final float HINT_PULSE_LIFT = 0.62f;
  private static final long HINT_PULSE_PERIOD_MS = 1400L;
  /** Share of the beacon's period spent snapping to full brightness. */
  private static final float HINT_PULSE_ATTACK = 0.12f;
  /** Key the ? invitation sits on while the hints are up, or -1. */
  private int _hintPulseKeyId = -1;
  /** Phase of the faster pulse, on the same animator's clock as the breath. */
  private float _hintPulseWave;

  /**
   * Marks one cap as the invitation rather than as a binding. Null clears it. Costs nothing when
   * no hint lighting is up: the breath animator drives both waves and only runs while lit.
   */
  public void setKeybindHintPulseToken(String token)
  {
    requireMainThread();
    int id = token == null ? -1 : parseKeyId(token);
    if (_hintPulseKeyId == id)
      return;
    _hintPulseKeyId = id;
    invalidate();
  }

  private void updateHintBreathAnimator()
  {
    boolean want = _hintColorOverrides.size() > 0 && isAttachedToWindow()
      && ValueAnimator.areAnimatorsEnabled();
    if (!want)
    {
      if (_hintBreathAnimator != null)
      {
        _hintBreathAnimator.cancel();
        _hintBreathAnimator = null;
      }
      _hintBreathWave = 0f;
      _hintPulseWave = 0f;
      return;
    }
    if (_hintBreathAnimator != null)
      return;
    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(HINT_BREATH_PERIOD_MS);
    animator.setRepeatCount(ValueAnimator.INFINITE);
    animator.setInterpolator(null); // linear phase; the sine below shapes the ease
    animator.addUpdateListener(a -> {
      float phase = (Float) a.getAnimatedValue();
      _hintBreathWave = 0.5f - 0.5f * (float) Math.cos(2.0 * Math.PI * phase);
      // The beacon rides the same clock, so one animator still runs the whole surface. Its
      // envelope is asymmetric on purpose: a fast rise and a long fall is a flash, and a flash is
      // what says "press this" without joining the breath around it.
      double pulsePhase = phase * (double) HINT_BREATH_PERIOD_MS / HINT_PULSE_PERIOD_MS;
      float beat = (float) (pulsePhase - Math.floor(pulsePhase));
      _hintPulseWave = beat < HINT_PULSE_ATTACK
        ? beat / HINT_PULSE_ATTACK
        : (float) Math.pow(1f - (beat - HINT_PULSE_ATTACK) / (1f - HINT_PULSE_ATTACK), 2.2);
      invalidate();
    });
    animator.start();
    _hintBreathAnimator = animator;
  }

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    updateHintBreathAnimator();
  }

  /** A lit hint color at the current point of the breath, or of the beacon for the ? cap. */
  private int hintBreathe(int color, int keyIdValue)
  {
    if (_hintBreathAnimator == null)
      return color;
    if (keyIdValue >= 0 && keyIdValue == _hintPulseKeyId)
      return lerpColor(color, Color.WHITE, HINT_PULSE_LIFT * _hintPulseWave);
    float keep = 1f - HINT_BREATH_DEPTH * _hintBreathWave;
    return Color.argb(Color.alpha(color), Math.round(Color.red(color) * keep),
        Math.round(Color.green(color) * keep), Math.round(Color.blue(color) * keep));
  }

  /** TRACE loop: how long one light takes to travel a latched cap's border. */
  private static final long HINT_TRACE_PERIOD_MS = 2400L;
  private static final long HINT_TRACE_STAGGER_MS = 200L;

  private SweepGradient _hintTraceGradient;
  private int _hintTraceColor;
  private final Matrix _hintTraceMatrix = new Matrix();
  private final Paint _hintTracePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF _hintTraceRect = new RectF();

  /**
   * While the keybind hint lighting is up, a light travels the border of each latched modifier
   * cap — the prefix indicator. One rotating sweep gradient serves every cap; the shared breath
   * animator's frame ticks drive the rotation, so this adds no animator of its own.
   */
  private void drawHintTrace(Canvas canvas, float x, float y, float keyW, float keyH,
      Theme.Computed.Key tc, int traceIndex)
  {
    int light = _theme.activatedColor;
    if (_hintTraceGradient == null || _hintTraceColor != light)
    {
      _hintTraceColor = light;
      _hintTraceGradient = new SweepGradient(0f, 0f,
          new int[] {0, 0, withAlpha(light, 230), 0}, new float[] {0f, 0.72f, 0.9f, 1f});
    }
    float phase = ((SystemClock.uptimeMillis() + HINT_TRACE_PERIOD_MS
        - traceIndex * HINT_TRACE_STAGGER_MS) % HINT_TRACE_PERIOD_MS)
        / (float) HINT_TRACE_PERIOD_MS;
    float cx = x + keyW / 2f;
    float cy = y + keyH / 2f;
    _hintTraceMatrix.setRotate(phase * 360f);
    _hintTraceMatrix.postTranslate(cx, cy);
    _hintTraceGradient.setLocalMatrix(_hintTraceMatrix);
    float strokeWidth = Math.max(tc.border_width * 1.5f, 2f);
    float padding = Math.max(tc.border_width, strokeWidth) / 2f;
    _hintTraceRect.set(x + padding, y + padding, x + keyW - padding, y + keyH - padding);
    _hintTracePaint.setStyle(Paint.Style.STROKE);
    _hintTracePaint.setStrokeWidth(strokeWidth);
    _hintTracePaint.setShader(_hintTraceGradient);
    canvas.drawRoundRect(_hintTraceRect, tc.border_radius, tc.border_radius, _hintTracePaint);
    _hintTracePaint.setShader(null);
  }

  private static int keyId(int rowIndex, int keyIndex)
  {
    return (rowIndex << 16) | (keyIndex & 0xFFFF);
  }

  private static int parseKeyId(String keyId)
  {
    int colon = keyId.indexOf(':');
    if (colon < 0)
      return -1;
    try
    {
      return keyId(Integer.parseInt(keyId.substring(0, colon)),
          Integer.parseInt(keyId.substring(colon + 1)));
    }
    catch (NumberFormatException _e)
    {
      return -1;
    }
  }

  /** Non-null enables paint mode and prevents the preview keyboard from producing key events. */
  public void setOnKeyPaintListener(OnKeyPaintListener listener)
  {
    requireMainThread();
    resetInputStateInternal(false);
    _keyPaintListener = listener;
    _lastPaintedKeyId = null;
  }

  /** Installs or removes (null) the host's tap resolver. */
  public void setTapResolver(TapResolver resolver)
  {
    requireMainThread();
    _tapResolver = resolver;
    _tapGeometry = null;
  }

  /** Opaque/translucent color used by an activity-owned navigation-inset continuation surface. */
  public int getKeyboardBackgroundColor()
  {
    return withOpacity(_theme.colorKeyboard, _theme.opacity);
  }

  /** Label color used by transient host controls drawn against the keyboard palette. */
  public int getKeyboardLabelColor()
  {
    return _theme.labelColor;
  }

  /** Updates keyboard geometry without recreating the renderer or interrupting input state. */
  public void setHeightScale(float heightScale)
  {
    requireMainThread();
    if (Float.isNaN(heightScale) || Float.isInfinite(heightScale) || heightScale <= 0f)
      throw new IllegalArgumentException("heightScale must be finite and positive");
    if (Float.compare(_heightScale, heightScale) == 0)
      return;
    _heightScale = heightScale;
    requestLayout();
    invalidate();
  }

  public float getHeightScale()
  {
    return _heightScale;
  }

  /** Scales both horizontal and vertical gaps without replacing immutable Config. */
  public void setKeyMarginScale(float keyMarginScale)
  {
    requireMainThread();
    if (Float.isNaN(keyMarginScale) || Float.isInfinite(keyMarginScale)
        || keyMarginScale < 0f)
      throw new IllegalArgumentException("keyMarginScale must be finite and non-negative");
    if (Float.compare(_keyMarginScale, keyMarginScale) == 0)
      return;
    _keyMarginScale = keyMarginScale;
    _tc = null;
    requestLayout();
    invalidate();
  }

  public float getKeyMarginScale()
  {
    return _keyMarginScale;
  }

  /** Overrides key corner radius in px; -1 restores Config/palette geometry. */
  public void setKeyCornerRadiusOverride(float radiusPx)
  {
    requireMainThread();
    if (Float.isNaN(radiusPx) || Float.isInfinite(radiusPx)
        || (radiusPx < 0f && Float.compare(radiusPx, -1f) != 0))
      throw new IllegalArgumentException("radiusPx must be finite, non-negative, or -1");
    if (Float.compare(_keyCornerRadiusOverridePx, radiusPx) == 0)
      return;
    _keyCornerRadiusOverridePx = radiusPx;
    _tc = null;
    requestLayout();
    invalidate();
  }

  public float getKeyCornerRadiusOverride()
  {
    return _keyCornerRadiusOverridePx;
  }

  /**
   * Sets the key caps' absolute background opacity (0..1); -1 restores the theme's own
   * translucency. Labels, borders, and the keyboard surface behind the caps are untouched.
   * Recomputes only the theme paints — no geometry or renderer changes.
   */
  public void setKeyOpacity(float opacity)
  {
    requireMainThread();
    if (Float.isNaN(opacity) || Float.isInfinite(opacity) || opacity > 1f
        || (opacity < 0f && Float.compare(opacity, -1f) != 0))
      throw new IllegalArgumentException("opacity must be within 0..1, or -1");
    if (Float.compare(_keyOpacity, opacity) == 0)
      return;
    _keyOpacity = opacity;
    _tc = null;
    requestLayout();
    invalidate();
  }

  public float getKeyOpacity()
  {
    return _keyOpacity;
  }

  /** The normal key role's current effective fill alpha as 0-100, for seeding editors. */
  public int getEffectiveKeyFillOpacityPercent()
  {
    if (_keyOpacity >= 0f)
      return Math.round(_keyOpacity * 100f);
    if (_tc != null)
      return Math.round(_tc.key.fillAlpha() / 255f * 100f);
    return 100;
  }

  public float getEffectiveKeyCornerRadiusPx()
  {
    if (_keyCornerRadiusOverridePx >= 0f)
      return _keyCornerRadiusOverridePx;
    return _config.bordersEnabled ? _config.borderRadiusPx : _theme.keyBorderRadius;
  }

  /**
   * Sets the stable available-height reference used by {@code maxKeyboardHeightFraction}.
   * A value of zero restores the default behavior of using the incoming measure spec.
   */
  public void setHeightCapReferencePx(int heightPx)
  {
    requireMainThread();
    if (heightPx < 0)
      throw new IllegalArgumentException("heightPx must not be negative");
    if (_heightCapReferencePx == heightPx)
      return;
    _heightCapReferencePx = heightPx;
    requestLayout();
  }

  private static int withOpacity(int color, float opacity)
  {
    return Color.argb(Theme.multiplyAlpha(Color.alpha(color), opacity),
        Color.red(color), Color.green(color), Color.blue(color));
  }

  private static void requireMainThread()
  {
    if (Looper.getMainLooper().getThread() != Thread.currentThread())
      throw new IllegalStateException("Keyboard2View mutations must run on the main thread");
  }

  public void setKeyboard(KeyboardData kw)
  {
    requireMainThread();
    _keyboard = Objects.requireNonNull(kw, "keyboard");
    _tapGeometry = null;
    _shift_key = _keyboard.findKeyWithValue(KeyValue.SHIFT);
    _compose_key = _keyboard.findKeyWithValue(KeyValue.COMPOSE);
    resetInputStateInternal(true);
  }

  /**
   * On-screen bounds of the space bar in the rendered layout. The host uses this as the
   * origin rect for surfaces that grow out of the space bar; the geometry mirrors
   * {@link #onDraw} exactly so the seed lands on the drawn cap, not on its cell.
   *
   * @return false when the layout has no space bar or has not been measured yet
   */
  public boolean getSpaceBarRectOnScreen(Rect out)
  {
    if (_keyboard == null || _tc == null)
      return false;
    getLocationOnScreen(_spaceBarLocation);
    float y = getPaddingTop() + _tc.margin_top;
    for (KeyboardData.Row row : _keyboard.rows)
    {
      y += row.shift * _tc.row_height;
      float x = _marginLeft + _tc.margin_left;
      float keyH = row.height * _tc.row_height - _tc.vertical_margin;
      for (KeyboardData.Key k : row.keys)
      {
        x += k.shift * _keyWidth;
        float keyW = _keyWidth * k.width - _tc.horizontal_margin;
        if (isSpaceBar(k))
        {
          out.set(Math.round(_spaceBarLocation[0] + x),
              Math.round(_spaceBarLocation[1] + y),
              Math.round(_spaceBarLocation[0] + x + keyW),
              Math.round(_spaceBarLocation[1] + y + keyH));
          return true;
        }
        x += _keyWidth * k.width;
      }
      y += row.height * _tc.row_height;
    }
    return false;
  }

  /**
   * Space bar identity, matching {@code Pointers.swipeKeyName}: the role attribute, or the
   * center value for user layout files that predate it.
   */
  private static boolean isSpaceBar(KeyboardData.Key key)
  {
    if (key.role == KeyboardData.Key.Role.Space_bar)
      return true;
    KeyValue center = key.keys[0];
    return center != null && center.getKind() == KeyValue.Kind.Editing
      && center.getEditing() == KeyValue.Editing.SPACE_BAR;
  }

  /** @deprecated Use [resetInputState()]. */
  @Deprecated
  public void reset()
  {
    resetInputState();
  }

  public void resetInputState()
  {
    requireMainThread();
    resetInputStateInternal(true);
  }

  private void resetInputStateInternal(boolean notifyHandler)
  {
    _mods = Pointers.Modifiers.EMPTY;
    _pointers.reset();
    _trails.clear();
    _touchFx.clear();
    _releasedTouchFx.clear();
    if (notifyHandler)
      _config.handler.mods_changed(_mods);
    requestLayout();
    invalidate();
  }

  public void setPalette(Theme.Palette palette)
  {
    requireMainThread();
    _theme = new Theme(getContext(), Objects.requireNonNull(palette, "palette"));
    applyTheme();
    requestLayout();
    invalidate();
  }

  public void setShiftLocked(boolean locked)
  {
    requireMainThread();
    set_fake_ptr_latched(_shift_key, KeyValue.SHIFT, locked, true);
  }

  void set_fake_ptr_latched(KeyboardData.Key key, KeyValue kv, boolean latched,
      boolean lock)
  {
    if (_keyboard == null || key == null)
      return;
    _pointers.set_fake_pointer_state(key, kv, latched, lock);
  }

  /** Called by auto-capitalisation. */
  public void set_shift_state(boolean latched, boolean lock)
  {
    requireMainThread();
    set_fake_ptr_latched(_shift_key, KeyValue.SHIFT, latched, lock);
  }

  /** Called when the host enters or leaves compose-pending state. */
  public void set_compose_pending(boolean pending)
  {
    requireMainThread();
    set_fake_ptr_latched(_compose_key, KeyValue.COMPOSE, pending, false);
  }

  public KeyValue modifyKey(KeyValue k, Pointers.Modifiers mods)
  {
    return KeyModifier.modify(k, mods,
        _keyboard == null ? null : _keyboard.modmap);
  }

  public void onPointerDown(KeyValue k, boolean isSwipe)
  {
    updateFlags();
    _config.handler.key_down(k, isSwipe);
    invalidate();
    vibrate();
    playKeySound(k);
  }

  /** System keypress sound effect, honoring the user's touch-sound volume. */
  private void playKeySound(KeyValue k)
  {
    if (!_config.keySoundEnabled)
      return;
    AudioManager audioManager =
      (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
    if (audioManager == null)
      return;
    int effect = AudioManager.FX_KEYPRESS_STANDARD;
    if (k != null)
      switch (k.getKind())
      {
        case Keyevent:
          switch (k.getKeyevent())
          {
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_FORWARD_DEL:
              effect = AudioManager.FX_KEYPRESS_DELETE; break;
            case KeyEvent.KEYCODE_ENTER:
              effect = AudioManager.FX_KEYPRESS_RETURN; break;
            case KeyEvent.KEYCODE_SPACE:
              effect = AudioManager.FX_KEYPRESS_SPACEBAR; break;
          }
          break;
        case Char:
          if (k.getChar() == ' ')
            effect = AudioManager.FX_KEYPRESS_SPACEBAR;
          break;
        default:
          break;
      }
    audioManager.playSoundEffect(effect, -1f);
  }

  public void onPointerUp(KeyValue k, Pointers.Modifiers mods)
  {
    // [key_up] must be called before [updateFlags]. The latter might disable
    // flags.
    _config.handler.key_up(k, mods);
    updateFlags();
    invalidate();
  }

  public void onPointerHold(KeyValue k, Pointers.Modifiers mods)
  {
    _config.handler.key_up(k, mods);
    updateFlags();
  }

  public void onPointerFlagsChanged(boolean shouldVibrate)
  {
    updateFlags();
    invalidate();
    if (shouldVibrate)
      vibrate();
  }

  private void updateFlags()
  {
    _mods = _pointers.getModifiers();
    _config.handler.mods_changed(_mods);
  }

  @Override
  public boolean onTouch(View v, MotionEvent event)
  {
    if (_keyPaintListener != null)
      return onPaintTouch(event);
    int p;
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        p = event.getActionIndex();
        observeTap(_touchFx.get(event.getPointerId(p)));
        finishTouchFx(event.getPointerId(p));
        _pointers.onTouchUp(event.getPointerId(p));
        _trails.remove(event.getPointerId(p));
        if (event.getActionMasked() == MotionEvent.ACTION_UP)
          requestDisallowIntercept(false);
        break;
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_POINTER_DOWN:
        p = event.getActionIndex();
        float tx = event.getX(p);
        float ty = event.getY(p);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
          requestDisallowIntercept(true);
        KeyboardData.Key rawKey = getKeyAtPosition(tx, ty);
        KeyboardData.Key key = resolveTap(rawKey, tx, ty);
        if (key != null)
        {
          startTouchFx(event.getPointerId(p), key, rawKey, tx, ty);
          _pointers.onTouchDown(tx, ty, event.getPointerId(p), key);
          if (_config.swipeTrailEnabled)
            _trails.put(event.getPointerId(p), new Trail(tx, ty));
        }
        break;
      case MotionEvent.ACTION_MOVE:
        for (p = 0; p < event.getPointerCount(); p++)
        {
          _pointers.onTouchMove(event.getX(p), event.getY(p), event.getPointerId(p));
          updateTouchFx(event.getPointerId(p), event.getX(p), event.getY(p));
          Trail trail = _trails.get(event.getPointerId(p));
          if (trail != null)
            trail.update(event.getX(p), event.getY(p));
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        finishAllTouchFx();
        _pointers.onTouchCancelCommit();
        _trails.clear();
        requestDisallowIntercept(false);
        break;
      default:
        return (false);
    }
    postInvalidateOnAnimation();
    return (true);
  }

  private void startTouchFx(int pointerId, KeyboardData.Key key,
      KeyboardData.Key rawKey, float x, float y)
  {
    TouchFx previous = _touchFx.get(pointerId);
    if (previous != null)
      releaseTouchFx(previous, SystemClock.uptimeMillis());
    _touchFx.put(pointerId, new TouchFx(key, rawKey, x, y, SystemClock.uptimeMillis()));
  }

  /** Lazily rebuilds the tap geometry for the current layout and measurement. */
  private TapGeometry tapGeometry()
  {
    if (_tapGeometry == null && _keyboard != null && _tc != null && _keyWidth > 0f)
      _tapGeometry = TapGeometry.of(_keyboard, _tc.row_height, _keyWidth);
    return _tapGeometry;
  }

  private float tapUnitsX(float px) { return (px - _marginLeft) / _keyWidth; }

  private float tapUnitsY(float py)
  {
    return (py - getPaddingTop() - _config.marginTopPx) / _keyWidth;
  }

  /** The key a real press resolves to: [rawKey] unless the resolver moves it. */
  private KeyboardData.Key resolveTap(KeyboardData.Key rawKey, float tx, float ty)
  {
    if (rawKey == null || _tapResolver == null)
      return rawKey;
    TapGeometry geometry = tapGeometry();
    if (geometry == null)
      return rawKey;
    int rawIndex = geometry.indexOf(rawKey);
    if (rawIndex < 0)
      return rawKey;
    int index = _tapResolver.resolveTap(geometry, rawIndex, tapUnitsX(tx), tapUnitsY(ty));
    if (index < 0 || index >= geometry.keyCount)
      return rawKey;
    return geometry.keys[index];
  }

  private void observeTap(TouchFx fx)
  {
    if (fx == null || fx.rawKey == null || _tapResolver == null)
      return;
    TapGeometry geometry = tapGeometry();
    if (geometry == null)
      return;
    int rawIndex = geometry.indexOf(fx.rawKey);
    if (rawIndex < 0)
      return;
    _tapResolver.observeTap(geometry, rawIndex, tapUnitsX(fx.downX),
        tapUnitsY(fx.downY), fx.swiped);
  }

  private void updateTouchFx(int pointerId, float x, float y)
  {
    TouchFx fx = _touchFx.get(pointerId);
    if (fx != null)
      fx.update(x, y, _config.swipeDistancePx);
  }

  private void finishTouchFx(int pointerId)
  {
    TouchFx fx = _touchFx.get(pointerId);
    if (fx == null)
      return;
    _touchFx.remove(pointerId);
    releaseTouchFx(fx, SystemClock.uptimeMillis());
  }

  private void finishAllTouchFx()
  {
    long now = SystemClock.uptimeMillis();
    for (int i = 0; i < _touchFx.size(); i++)
      releaseTouchFx(_touchFx.valueAt(i), now);
    _touchFx.clear();
  }

  private void releaseTouchFx(TouchFx fx, long now)
  {
    fx.releasedAt = now;
    _releasedTouchFx.add(fx);
  }

  private boolean onPaintTouch(MotionEvent event)
  {
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN:
        requestDisallowIntercept(true);
        _lastPaintedKeyId = null;
        paintKeyAt(event.getX(), event.getY());
        return true;
      case MotionEvent.ACTION_MOVE:
        paintKeyAt(event.getX(), event.getY());
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        _lastPaintedKeyId = null;
        requestDisallowIntercept(false);
        return true;
      default:
        return true;
    }
  }

  private void paintKeyAt(float x, float y)
  {
    KeyboardData.Key key = getKeyAtPosition(x, y);
    String keyId = keyId(key);
    if (keyId != null && !keyId.equals(_lastPaintedKeyId))
    {
      _lastPaintedKeyId = keyId;
      _keyPaintListener.onPaintKey(keyId);
    }
  }

  private String keyId(KeyboardData.Key target)
  {
    if (target == null || _keyboard == null)
      return null;
    for (int rowIndex = 0; rowIndex < _keyboard.rows.size(); rowIndex++)
    {
      KeyboardData.Row row = _keyboard.rows.get(rowIndex);
      for (int keyIndex = 0; keyIndex < row.keys.size(); keyIndex++)
        if (row.keys.get(keyIndex) == target)
          return rowIndex + ":" + keyIndex;
    }
    return null;
  }

  private void requestDisallowIntercept(boolean disallow)
  {
    if (getParent() != null)
      getParent().requestDisallowInterceptTouchEvent(disallow);
  }

  private KeyboardData.Row getRowAtPosition(float ty)
  {
    if (_keyboard == null || _tc == null)
      return null;
    float y = getPaddingTop() + _config.marginTopPx;
    if (ty < y)
      return null;
    for (KeyboardData.Row row : _keyboard.rows)
    {
      y += (row.shift + row.height) * _tc.row_height;
      if (ty < y)
        return row;
    }
    return null;
  }

  private KeyboardData.Key getKeyAtPosition(float tx, float ty)
  {
    KeyboardData.Row row = getRowAtPosition(ty);
    float x = _marginLeft;
    if (row == null || tx < x)
      return null;
    for (KeyboardData.Key key : row.keys)
    {
      float xLeft = x + key.shift * _keyWidth;
      float xRight = xLeft + key.width * _keyWidth;
      if (tx < xLeft)
        return null;
      if (tx < xRight)
        return key;
      x = xRight;
    }
    return null;
  }

  private void vibrate()
  {
    VibratorCompat.vibrate(this, _config);
  }

  private final Rect _exclusion_rect = new Rect();
  private final java.util.List<Rect> _exclusion_rects =
      java.util.Collections.singletonList(_exclusion_rect);

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom)
  {
    super.onLayout(changed, left, top, right, bottom);
    if (Build.VERSION.SDK_INT < 29 || !changed)
      return;
    // Unlike an IME window, an activity-embedded keyboard gets no automatic
    // system-gesture exclusion, so edge-column swipes would be recognized as
    // Back gestures and cancelled. Upstream had an equivalent SDK-29 block; it
    // used parent-relative coordinates, which only worked because the IME view
    // sat at (0,0) — exclusion rects are in view-local coordinates.
    _exclusion_rect.set(0, 0, right - left, bottom - top);
    setSystemGestureExclusionRects(_exclusion_rects);
  }

  @Override
  public void onMeasure(int wSpec, int hSpec)
  {
    if (_keyboard == null)
    {
      setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), wSpec),
          resolveSize(0, hSpec));
      return;
    }

    float configuredRowHeight = _config.rowHeightPx * _heightScale;
    int desiredWidth = (int)Math.ceil(
        _keyboard.keysWidth * configuredRowHeight * 2f / 3f
        + getPaddingLeft() + getPaddingRight()
        + 2f * _config.horizontalMarginPx);
    int width = resolveSize(Math.max(desiredWidth, getSuggestedMinimumWidth()), wSpec);
    _marginLeft = getPaddingLeft() + _config.horizontalMarginPx;
    _marginRight = getPaddingRight() + _config.horizontalMarginPx;
    _marginBottom = getPaddingBottom() + _config.bottomMarginPx;
    float contentWidth = Math.max(0f, width - _marginLeft - _marginRight);
    _keyWidth = contentWidth / _keyboard.keysWidth;

    float fixedHeight = getPaddingTop() + _config.marginTopPx + _marginBottom;
    float rowHeight = configuredRowHeight;
    int heightMode = MeasureSpec.getMode(hSpec);
    int heightSize = MeasureSpec.getSize(hSpec);
    if (heightMode == MeasureSpec.AT_MOST)
    {
      // An embedded host may first measure against its full content root, then lay this view out
      // inside a shorter exact accessory stack. Keep the fraction cap tied to the stable host
      // reference so those two AT_MOST contexts resolve to the same desired keyboard height.
      int capReferenceHeight = _heightCapReferencePx > 0
          ? _heightCapReferencePx : heightSize;
      // The fraction is a hard ceiling on screen share; the user's height scale sizes the rows
      // under it but must not be able to raise it, or a tall scale starves the terminal.
      float cappedHeight = capReferenceHeight
          * Math.min(1f, _config.maxKeyboardHeightFraction);
      rowHeight = Math.min(rowHeight,
          Math.max(0f, cappedHeight - fixedHeight) / _keyboard.keysHeight);
    }
    else if (heightMode == MeasureSpec.EXACTLY)
    {
      rowHeight = Math.min(rowHeight,
          Math.max(0f, heightSize - fixedHeight) / _keyboard.keysHeight);
    }

    _tc = new Theme.Computed(_theme, _config, _keyWidth, _keyboard, rowHeight,
        _keyMarginScale, _keyCornerRadiusOverridePx, _keyOpacity);
    _tapGeometry = null;
    // Compute the size of labels based on the width or the height of keys. The
    // margin around keys is taken into account. Keys normal aspect ratio is
    // assumed to be 3/2 for a 10 columns layout. It's generally more, the
    // width computation is useful when the keyboard is unusually high.
    float labelBaseSize = Math.min(
        _tc.row_height - _tc.vertical_margin,
        (width / 10f - _tc.horizontal_margin) * 3f / 2f)
        * _config.characterSize;
    _mainLabelSize = labelBaseSize * _config.labelSizeRatio;
    _subLabelSize = labelBaseSize * _config.sublabelSizeRatio;
    int desiredHeight = (int)Math.ceil(
        _tc.row_height * _keyboard.keysHeight + fixedHeight);
    setMeasuredDimension(width, resolveSize(desiredHeight, hSpec));
  }

  /** Horizontal and vertical position of the 9 indexes. */
  static final Paint.Align[] LABEL_POSITION_H = new Paint.Align[]{
    Paint.Align.CENTER, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.LEFT,
    Paint.Align.RIGHT, Paint.Align.LEFT, Paint.Align.RIGHT,
    Paint.Align.CENTER, Paint.Align.CENTER
  };

  static final Vertical[] LABEL_POSITION_V = new Vertical[]{
    Vertical.CENTER, Vertical.TOP, Vertical.TOP, Vertical.BOTTOM,
    Vertical.BOTTOM, Vertical.CENTER, Vertical.CENTER, Vertical.TOP,
    Vertical.BOTTOM
  };

  @Override
  protected void onDraw(Canvas canvas)
  {
    if (_keyboard == null || _tc == null)
      return;
    long now = SystemClock.uptimeMillis();
    pruneReleasedTouchFx(now);
    _launchWaveDensity = getResources().getDisplayMetrics().density;
    boolean animateNextFrame = false;
    int hintTraceIndex = 0;
    float y = getPaddingTop() + _tc.margin_top;
    for (int rowIndex = 0; rowIndex < _keyboard.rows.size(); rowIndex++)
    {
      KeyboardData.Row row = _keyboard.rows.get(rowIndex);
      y += row.shift * _tc.row_height;
      float x = _marginLeft + _tc.margin_left;
      float keyH = row.height * _tc.row_height - _tc.vertical_margin;
      for (int keyIndex = 0; keyIndex < row.keys.size(); keyIndex++)
      {
        KeyboardData.Key k = row.keys.get(keyIndex);
        int keyIdValue = keyId(rowIndex, keyIndex);
        KeyColorOverride hintOverride = _hintColorOverrides.get(keyIdValue);
        KeyColorOverride schemeOverride = _keyColorOverrides.get(keyIdValue);
        KeyColorOverride colorOverride =
            hintOverride != null ? hintOverride : schemeOverride;
        x += k.shift * _keyWidth;
        float keyW = _keyWidth * k.width - _tc.horizontal_margin;
        boolean isKeyDown = _pointers.isKeyDown(k);
        TouchFx touchFx = findTouchFx(k);
        float fxStrength = touchFx == null ? 0f : touchFx.strength(now);
        float launchStrength = launchWaveStrength(x + keyW / 2f, y + keyH / 2f);
        int keySave = canvas.save();
        if (touchFx != null && !touchFx.swiped && fxStrength > 0f)
        {
          float scale = 1f - 0.035f * fxStrength;
          canvas.scale(scale, scale, x + keyW / 2f, y + keyH / 2f);
        }
        Theme.Computed.Key tc_key;
        if (isKeyDown)
          tc_key = _tc.key_activated;
        else
          switch (k.role)
          {
            case Action: tc_key = _tc.key_action; break;
            case Space_bar: tc_key = _tc.key_space_bar; break;
            case Suggestion: tc_key = _tc.key_suggestion; break;
            default:
            case Normal: tc_key = _tc.key; break;
          }
        if (hintOverride != null && _hintFadeAnimator != null)
        {
          hintOverride = fadeHintOverride(hintOverride, schemeOverride, tc_key);
          colorOverride = hintOverride;
        }
        Integer frameBackground =
            isKeyDown || colorOverride == null ? null : colorOverride.keyBackground;
        Integer frameBorder =
            isKeyDown || colorOverride == null ? null : colorOverride.borderColor;
        if (hintOverride != null)
        {
          // Only the hint lighting breathes; color-scheme overrides stay steady.
          if (frameBackground != null)
            frameBackground = hintBreathe(frameBackground, keyIdValue);
          if (frameBorder != null)
            frameBorder = hintBreathe(frameBorder, keyIdValue);
        }
        drawKeyFrame(canvas, x, y, keyW, keyH, tc_key, frameBackground, frameBorder);
        // The latched Ctrl/Alt/Shift caps are the hint popup's prefix indicator; trace them
        // while the hint lighting is up. Latched modifiers render as key-down.
        if (_hintColorOverrides.size() > 0 && _hintBreathAnimator != null && isKeyDown
            && k.keys[0] != null && k.keys[0].getKind() == KeyValue.Kind.Modifier)
          drawHintTrace(canvas, x, y, keyW, keyH, tc_key, hintTraceIndex++);
        if (launchStrength > 0f)
        {
          _launchWavePaint.setColor(withAlpha(_launchWaveColor,
              Math.round(30f * launchStrength)));
          float chipInset = tc_key.border_width * 0.5f;
          _tmpRect.set(x + chipInset, y + chipInset,
              x + keyW - chipInset, y + keyH - chipInset);
          float chipRadius = Math.max(0f, tc_key.border_radius - chipInset);
          canvas.drawRoundRect(_tmpRect, chipRadius, chipRadius, _launchWavePaint);
        }
        if (touchFx != null && fxStrength > 0f)
          drawTouchFx(canvas, touchFx, x, y, keyW, keyH, tc_key,
              fxStrength);
        if (k.keys[0] != null)
          drawLabel(canvas, k.keys[0], keyW / 2f + x, y, keyH, isKeyDown, tc_key,
              isKeyDown || colorOverride == null ? null : colorOverride.primaryLabel);
        for (int i = 1; i < 9; i++)
        {
          if (k.keys[i] != null)
          {
            Integer labelOverride = null;
            if (!isKeyDown && colorOverride != null)
              labelOverride = (i == 3 || i == 4) && colorOverride.secondaryBottomLabel != null
                  ? colorOverride.secondaryBottomLabel : colorOverride.secondaryLabel;
            drawSubLabel(canvas, k.keys[i], x, y, keyW, keyH, i, isKeyDown, tc_key,
                labelOverride);
          }
        }
        drawIndication(canvas, k, x, y, keyW, keyH, _tc);
        canvas.restoreToCount(keySave);
        if (touchFx != null && touchFx.needsFrame(now))
          animateNextFrame = true;
        x += _keyWidth * k.width;
      }
      y += row.height * _tc.row_height;
    }
    if (_config.swipeTrailEnabled)
      for (int i = 0; i < _trails.size(); i++)
      {
        Trail trail = _trails.valueAt(i);
        _fxHaloPaint.setStrokeWidth(Math.max(_config.swipeTrailWidthPx * 3.2f, 4f));
        _fxHaloPaint.setColor(withAlpha(_theme.pressedColor, 42));
        canvas.drawLine(trail.startX, trail.startY, trail.endX, trail.endY,
            _fxHaloPaint);
        canvas.drawLine(trail.startX, trail.startY, trail.endX, trail.endY,
            _trailPaint);
      }
    if (animateNextFrame || !_releasedTouchFx.isEmpty())
      postInvalidateOnAnimation();
  }

  private float launchWaveStrength(float keyX, float keyY)
  {
    if (_launchWaveProgress < 0f || getWidth() <= 0 || getHeight() <= 0)
      return 0f;
    float farX = Math.max(Math.abs(_launchWaveOriginX),
        Math.abs(getWidth() - _launchWaveOriginX));
    float farY = Math.max(Math.abs(_launchWaveOriginY),
        Math.abs(getHeight() - _launchWaveOriginY));
    float maxRadius = Math.max(getWidth(), (float)Math.hypot(farX, farY));
    float travel = Math.min(1f,
        _launchWaveProgress * LAUNCH_WAVE_TOTAL_MS / LAUNCH_WAVE_TRAVEL_MS);
    float easedTravel = travel * travel * (3f - 2f * travel);
    float waveRadius = maxRadius * easedTravel;
    float distance = (float)Math.hypot(keyX - _launchWaveOriginX,
        keyY - _launchWaveOriginY);
    float halfBand = _launchWaveDensity * 11f;
    float delta = Math.abs(distance - waveRadius);
    if (delta >= halfBand)
      return 0f;
    // A cosine shoulder keeps this a subtle brightness/tint pass over the existing chip geometry.
    float strength = 0.5f + 0.5f
        * (float)Math.cos(Math.PI * delta / Math.max(1f, halfBand));
    return strength * _launchWaveOpacity;
  }

  private void cancelLaunchWaveAnimator()
  {
    ValueAnimator animator = _launchWaveAnimator;
    _launchWaveAnimator = null;
    if (animator != null)
      animator.cancel();
  }

  private void resetLaunchWave()
  {
    _launchWaveAnimator = null;
    _launchWaveProgress = -1f;
    _launchWaveOpacity = 0f;
    invalidate();
  }

  private void pruneReleasedTouchFx(long now)
  {
    for (int i = _releasedTouchFx.size() - 1; i >= 0; i--)
      if (now - _releasedTouchFx.get(i).releasedAt >= RELEASE_FADE_MS)
        _releasedTouchFx.remove(i);
  }

  private TouchFx findTouchFx(KeyboardData.Key key)
  {
    for (int i = _touchFx.size() - 1; i >= 0; i--)
      if (_touchFx.valueAt(i).key == key)
        return _touchFx.valueAt(i);
    for (int i = _releasedTouchFx.size() - 1; i >= 0; i--)
      if (_releasedTouchFx.get(i).key == key)
        return _releasedTouchFx.get(i);
    return null;
  }

  private void drawTouchFx(Canvas canvas, TouchFx fx, float x, float y,
      float keyW, float keyH, Theme.Computed.Key keyTheme, float strength)
  {
    float radius = keyTheme.border_radius;
    float inset = Math.max(1f, Math.min(keyW, keyH) * 0.055f);
    _tmpRect.set(x + inset, y + inset, x + keyW - inset, y + keyH - inset);
    if (!fx.swiped)
    {
      // Fill the existing chip interior instead of drawing a second inset outline. The activated
      // frame supplies the accent tint; these low-alpha overlays lift that fill and its gradient
      // while the chip's one normal border remains the only stroke.
      float chipInset = keyTheme.border_width * 0.5f;
      _tmpRect.set(x + chipInset, y + chipInset,
          x + keyW - chipInset, y + keyH - chipInset);
      _fxFillPaint.setColor(withAlpha(_theme.pressedColor,
          Math.round(28f * strength)));
      float chipRadius = Math.max(0f, radius - chipInset);
      canvas.drawRoundRect(_tmpRect, chipRadius, chipRadius, _fxFillPaint);
      _fxFillPaint.setColor(withAlpha(Color.WHITE, Math.round(12f * strength)));
      canvas.drawRoundRect(_tmpRect, chipRadius, chipRadius, _fxFillPaint);
      return;
    }

    float cx = x + keyW / 2f;
    float cy = y + keyH / 2f;
    if (fx.isRotating())
    {
      float ringRadius = Math.min(keyW, keyH) * 0.31f;
      _tmpRect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius);
      float sweep = (float)Math.toDegrees(fx.angularTravel);
      sweep = Math.max(-330f, Math.min(330f, sweep));
      _fxHaloPaint.setStrokeWidth(Math.max(5f, ringRadius * 0.24f));
      _fxHaloPaint.setColor(withAlpha(_theme.pressedColor,
          Math.round(38f * strength)));
      canvas.drawArc(_tmpRect, (float)Math.toDegrees(fx.firstAngle), sweep,
          false, _fxHaloPaint);
      _fxStrokePaint.setStrokeWidth(Math.max(1.8f, ringRadius * 0.075f));
      _fxStrokePaint.setColor(withAlpha(_theme.pressedColor,
          Math.round(205f * strength)));
      canvas.drawArc(_tmpRect, (float)Math.toDegrees(fx.firstAngle), sweep,
          false, _fxStrokePaint);
      return;
    }

    float dx = fx.x - fx.downX;
    float dy = fx.y - fx.downY;
    float scale = Math.max(Math.abs(dx) / Math.max(1f, keyW * 0.40f),
        Math.abs(dy) / Math.max(1f, keyH * 0.38f));
    if (scale < 1f) scale = 1f;
    float ex = cx + dx / scale;
    float ey = cy + dy / scale;
    _fxHaloPaint.setStrokeWidth(Math.max(5f, Math.min(keyW, keyH) * 0.17f));
    _fxHaloPaint.setColor(withAlpha(_theme.pressedColor,
        Math.round(44f * strength)));
    canvas.drawLine(cx, cy, ex, ey, _fxHaloPaint);
    _fxStrokePaint.setStrokeWidth(Math.max(1.8f, Math.min(keyW, keyH) * 0.045f));
    _fxStrokePaint.setColor(withAlpha(_theme.pressedColor,
        Math.round(220f * strength)));
    canvas.drawLine(cx, cy, ex, ey, _fxStrokePaint);
    _fxFillPaint.setColor(withAlpha(_theme.pressedColor,
        Math.round(190f * strength)));
    canvas.drawCircle(ex, ey, Math.max(2.2f, Math.min(keyW, keyH) * 0.055f),
        _fxFillPaint);
  }

  private static int withAlpha(int color, int alpha)
  {
    return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color),
        Color.green(color), Color.blue(color));
  }

  @Override
  public void onDetachedFromWindow()
  {
    cancelLaunchWaveAnimator();
    _launchWaveProgress = -1f;
    _launchWaveOpacity = 0f;
    if (_hintBreathAnimator != null)
    {
      _hintBreathAnimator.cancel();
      _hintBreathAnimator = null;
      _hintBreathWave = 0f;
      _hintPulseWave = 0f;
    }
    resetInputStateInternal(true);
    requestDisallowIntercept(false);
    super.onDetachedFromWindow();
  }

  /** Draw borders and background of the key. */
  void drawKeyFrame(Canvas canvas, float x, float y, float keyW, float keyH,
      Theme.Computed.Key tc, Integer backgroundOverride, Integer borderOverride)
  {
    float r = tc.border_radius;
    float w = tc.border_width;
    float padding = w / 2.f;
    _tmpRect.set(x + padding, y + padding, x + keyW - padding, y + keyH - padding);
    tc.positionGradient(y, keyH);
    if (backgroundOverride == null)
      canvas.drawRoundRect(_tmpRect, r, r, tc.bg_paint);
    else
    {
      // A host override recolors the base but keeps this role's translucency and
      // keycap gradient, so a custom color reads as a tint of the glass chip, not
      // an opaque slab that hides the blurred wallpaper behind it.
      _overrideBackgroundPaint.setStyle(Paint.Style.FILL);
      tc.applyOverrideFill(_overrideBackgroundPaint, backgroundOverride, y, keyH);
      canvas.drawRoundRect(_tmpRect, r, r, _overrideBackgroundPaint);
      _overrideBackgroundPaint.setShader(null);
    }
    if (w > 0.f)
    {
      if (borderOverride != null)
      {
        // One uniform stroke in the host-chosen color; overrides the theme's four
        // side colors regardless of whether they were uniform.
        _overrideBorderPaint.setStyle(Paint.Style.STROKE);
        _overrideBorderPaint.setStrokeWidth(w);
        _overrideBorderPaint.setColor(borderOverride);
        canvas.drawRoundRect(_tmpRect, r, r, _overrideBorderPaint);
        return;
      }
      if (tc.border_uniform)
      {
        // One anti-aliased stroke pass; the four-way clip below leaves seams
        // and aliased joins once the corner radius grows past a few dp.
        canvas.drawRoundRect(_tmpRect, r, r, tc.border_left_paint);
        return;
      }
      float overlap = r - r * 0.85f + w; // sin(45°)
      drawBorder(canvas, x, y, x + overlap, y + keyH, tc.border_left_paint, tc);
      drawBorder(canvas, x + keyW - overlap, y, x + keyW, y + keyH, tc.border_right_paint, tc);
      drawBorder(canvas, x, y, x + keyW, y + overlap, tc.border_top_paint, tc);
      drawBorder(canvas, x, y + keyH - overlap, x + keyW, y + keyH, tc.border_bottom_paint, tc);
    }
  }

  /** Clip to draw a border at a time. This allows to call [drawRoundRect]
      several time with the same parameters but a different Paint. */
  void drawBorder(Canvas canvas, float clipl, float clipt, float clipr,
      float clipb, Paint paint, Theme.Computed.Key tc)
  {
    float r = tc.border_radius;
    canvas.save();
    canvas.clipRect(clipl, clipt, clipr, clipb);
    canvas.drawRoundRect(_tmpRect, r, r, paint);
    canvas.restore();
  }

  private int labelColor(KeyValue k, boolean isKeyDown, boolean sublabel,
      Theme.Computed.Key tc)
  {
    if (isKeyDown)
    {
      int flags = _pointers.getKeyFlags(k);
      if (flags != -1)
      {
        if ((flags & Pointers.FLAG_P_LOCKED) != 0)
          return _theme.lockedColor;
        return _theme.activatedColor;
      }
      return _theme.pressedColor;
    }
    if (k.hasFlagsAny(KeyValue.FLAG_SECONDARY | KeyValue.FLAG_GREYED))
    {
      if (k.hasFlagsAny(KeyValue.FLAG_GREYED))
        return tc.greyedLabelColor;
      return tc.secondaryLabelColor;
    }
    return sublabel ? tc.subLabelColor : tc.labelColor;
  }

  /**
   * Keeps a latched Shift visible on caps that Ctrl or Alt already turned into key events.
   *
   * <p>Modifiers apply in ordinal order, so Ctrl and Alt run before Shift and
   * [KeyModifier.turn_into_keyevent] has replaced the Char value by the time Shift is applied;
   * Shift only upper-cases Char and String values, so Ctrl+Alt+Shift used to draw the same
   * lower-case caps as Ctrl+Alt. Only the drawn symbol changes here: the key event the cap sends
   * is untouched, because the shifted stroke rides in the event's meta state.
   */
  private KeyValue shiftedKeyeventLabel(KeyValue kv)
  {
    if (kv.getKind() != KeyValue.Kind.Keyevent
        || _mods == null || !_mods.has(KeyValue.Modifier.SHIFT))
      return kv;
    String symbol = kv.getString();
    if (symbol.length() != 1)
      return kv;
    char c = symbol.charAt(0);
    char upper = Character.toUpperCase(c);
    return (upper == c) ? kv : kv.withSymbol(String.valueOf(upper));
  }

  private void drawLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyH, boolean isKeyDown, Theme.Computed.Key tc, Integer colorOverride)
  {
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    kv = shiftedKeyeventLabel(kv);
    float textSize = scaleTextSize(kv, true);
    int color = colorOverride == null ? labelColor(kv, isKeyDown, false, tc) : colorOverride;
    Paint p = tc.label_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), color, textSize);
    canvas.drawText(kv.getString(), x, (keyH - p.ascent() - p.descent()) / 2f + y, p);
  }

  private void drawSubLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyW, float keyH, int sub_index, boolean isKeyDown,
      Theme.Computed.Key tc, Integer colorOverride)
  {
    Paint.Align a = LABEL_POSITION_H[sub_index];
    Vertical v = LABEL_POSITION_V[sub_index];
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    kv = shiftedKeyeventLabel(kv);
    float textSize = scaleTextSize(kv, false);
    int color = colorOverride == null ? labelColor(kv, isKeyDown, true, tc) : colorOverride;
    Paint p = tc.sublabel_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), color, textSize, a);
    float subPadding = _config.keyPaddingPx;
    // Corner-anchored sublabels sit where a large corner radius cuts the key
    // fill away; pull them inward with the radius so they stay on the cap.
    if (a != Paint.Align.CENTER && v != Vertical.CENTER)
      subPadding += tc.border_radius * 0.3f;
    if (v == Vertical.CENTER)
      y += (keyH - p.ascent() - p.descent()) / 2f;
    else
      y += (v == Vertical.TOP) ? subPadding - p.ascent() : keyH - subPadding - p.descent();
    if (a == Paint.Align.CENTER)
      x += keyW / 2f;
    else
      x += (a == Paint.Align.LEFT) ? subPadding : keyW - subPadding;
    String label = kv.getString();
    int label_len = label.length();
    // Limit the label of string keys to 3 characters
    if (label_len > 3 && kv.getKind() == KeyValue.Kind.String)
      label_len = 3;
    canvas.drawText(label, 0, label_len, x, y, p);
  }

  private void drawIndication(Canvas canvas, KeyboardData.Key k, float x,
      float y, float keyW, float keyH, Theme.Computed tc)
  {
    if (k.indication == null || k.indication.equals(""))
      return;
    Paint p = tc.indication_paint;
    p.setTextSize(_subLabelSize);
    canvas.drawText(k.indication, 0, k.indication.length(),
        x + keyW / 2f, (keyH - p.ascent() - p.descent()) * 4/5 + y, p);
  }

  private float scaleTextSize(KeyValue k, boolean main_label)
  {
    float smaller_font = k.hasFlagsAny(KeyValue.FLAG_SMALLER_FONT) ? 0.75f : 1.f;
    float label_size = main_label ? _mainLabelSize : _subLabelSize;
    return label_size * smaller_font;
  }

  private static final class Trail
  {
    final float startX;
    final float startY;
    float endX;
    float endY;

    Trail(float x, float y)
    {
      startX = endX = x;
      startY = endY = y;
    }

    void update(float x, float y)
    {
      endX = x;
      endY = y;
    }
  }

  private static final class TouchFx
  {
    final KeyboardData.Key key;
    /** The key the static grid resolved, before any tap correction. */
    final KeyboardData.Key rawKey;
    final float downX;
    final float downY;
    final long downAt;
    float x;
    float y;
    boolean swiped;
    double firstAngle = Double.NaN;
    double lastAngle = Double.NaN;
    double angularTravel;
    long releasedAt = -1L;

    TouchFx(KeyboardData.Key key, KeyboardData.Key rawKey, float x, float y, long downAt)
    {
      this.key = key;
      this.rawKey = rawKey;
      downX = x;
      downY = y;
      this.x = x;
      this.y = y;
      this.downAt = downAt;
    }

    void update(float x, float y, float swipeDistance)
    {
      this.x = x;
      this.y = y;
      float dx = x - downX;
      float dy = y - downY;
      float distance = (float)Math.hypot(dx, dy);
      if (distance < swipeDistance * 0.72f)
        return;
      swiped = true;
      double angle = Math.atan2(dy, dx);
      if (Double.isNaN(firstAngle))
        firstAngle = angle;
      if (!Double.isNaN(lastAngle))
      {
        double delta = angle - lastAngle;
        while (delta > Math.PI) delta -= Math.PI * 2.0;
        while (delta < -Math.PI) delta += Math.PI * 2.0;
        // Ignore a single across-center jump; a genuine circle produces a series of small deltas.
        if (Math.abs(delta) < 1.25)
          angularTravel += delta;
      }
      lastAngle = angle;
    }

    boolean isRotating()
    {
      return Math.abs(angularTravel) > 1.05;
    }

    float strength(long now)
    {
      float ramp = Math.min(1f, Math.max(0f, (now - downAt) / (float)PRESS_RAMP_MS));
      if (releasedAt < 0L)
        return ramp;
      float fade = 1f - Math.min(1f,
          Math.max(0f, (now - releasedAt) / (float)RELEASE_FADE_MS));
      return ramp * fade;
    }

    boolean needsFrame(long now)
    {
      return (releasedAt < 0L && now - downAt < PRESS_RAMP_MS)
          || (releasedAt >= 0L && now - releasedAt < RELEASE_FADE_MS);
    }
  }
}
