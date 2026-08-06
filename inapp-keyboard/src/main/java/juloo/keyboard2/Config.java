package juloo.keyboard2;

import android.content.res.Resources;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.util.Objects;

/** Immutable configuration for one embedded keyboard view. */
public final class Config
{
  public final IKeyEventHandler handler;

  public final float rowHeightPx;
  public final float maxKeyboardHeightFraction;
  public final float horizontalMarginPx;
  public final float bottomMarginPx;
  public final float marginTopPx;
  public final float keyPaddingPx;

  public final float labelSizeRatio;
  public final float sublabelSizeRatio;
  public final float characterSize;
  public final float horizontalKeyMarginRatio;
  public final float verticalKeyMarginRatio;

  /** Optional config-level border override. Palette borders are used when false. */
  public final boolean bordersEnabled;
  public final float borderRadiusPx;
  public final float borderWidthPx;
  public final int keyOpacity;
  public final int activatedKeyOpacity;
  public final int labelBrightness;

  public final boolean swipeTrailEnabled;
  public final float swipeTrailWidthPx;
  public final float swipeDistancePx;
  public final float sliderStepPx;
  public final int circleSensitivity;

  public final long longPressTimeoutMs;
  public final long repeatIntervalMs;
  public final boolean keyRepeatEnabled;
  public final boolean doubleTapShiftLock;

  public final boolean hapticEnabled;
  public final long hapticDurationMs;
  public final int hapticAmplitude;

  /** Plays the system keypress sound effect on pointer down. */
  public final boolean keySoundEnabled;
  /** Typeface for text labels, or null for the system default. Key glyphs
      always use the bundled special font. */
  public final Typeface labelFont;

  public final boolean addBottomRow;
  public final boolean addNumberRow;
  public final boolean numberRowSymbols;

  public Config(Builder builder)
  {
    handler = Objects.requireNonNull(builder.handler, "handler");
    rowHeightPx = positive(builder.rowHeightPx, "rowHeightPx");
    if (builder.maxKeyboardHeightFraction <= 0f || builder.maxKeyboardHeightFraction > 1f)
      throw new IllegalArgumentException("maxKeyboardHeightFraction must be in (0, 1]");
    maxKeyboardHeightFraction = builder.maxKeyboardHeightFraction;
    horizontalMarginPx = nonNegative(builder.horizontalMarginPx, "horizontalMarginPx");
    bottomMarginPx = nonNegative(builder.bottomMarginPx, "bottomMarginPx");
    marginTopPx = nonNegative(builder.marginTopPx, "marginTopPx");
    keyPaddingPx = nonNegative(builder.keyPaddingPx, "keyPaddingPx");
    labelSizeRatio = positive(builder.labelSizeRatio, "labelSizeRatio");
    sublabelSizeRatio = positive(builder.sublabelSizeRatio, "sublabelSizeRatio");
    characterSize = positive(builder.characterSize, "characterSize");
    horizontalKeyMarginRatio = nonNegative(builder.horizontalKeyMarginRatio, "horizontalKeyMarginRatio");
    verticalKeyMarginRatio = nonNegative(builder.verticalKeyMarginRatio, "verticalKeyMarginRatio");
    bordersEnabled = builder.bordersEnabled;
    borderRadiusPx = nonNegative(builder.borderRadiusPx, "borderRadiusPx");
    borderWidthPx = nonNegative(builder.borderWidthPx, "borderWidthPx");
    keyOpacity = alpha(builder.keyOpacity, "keyOpacity");
    activatedKeyOpacity = alpha(builder.activatedKeyOpacity, "activatedKeyOpacity");
    labelBrightness = alpha(builder.labelBrightness, "labelBrightness");
    swipeTrailEnabled = builder.swipeTrailEnabled;
    swipeTrailWidthPx = nonNegative(builder.swipeTrailWidthPx, "swipeTrailWidthPx");
    swipeDistancePx = positive(builder.swipeDistancePx, "swipeDistancePx");
    sliderStepPx = positive(builder.sliderStepPx, "sliderStepPx");
    circleSensitivity = Math.max(1, builder.circleSensitivity);
    longPressTimeoutMs = nonNegative(builder.longPressTimeoutMs, "longPressTimeoutMs");
    repeatIntervalMs = Math.max(1L, builder.repeatIntervalMs);
    keyRepeatEnabled = builder.keyRepeatEnabled;
    doubleTapShiftLock = builder.doubleTapShiftLock;
    hapticEnabled = builder.hapticEnabled;
    hapticDurationMs = nonNegative(builder.hapticDurationMs, "hapticDurationMs");
    hapticAmplitude = builder.hapticAmplitude;
    keySoundEnabled = builder.keySoundEnabled;
    labelFont = builder.labelFont;
    if (hapticAmplitude < -1 || hapticAmplitude == 0 || hapticAmplitude > 255)
      throw new IllegalArgumentException("hapticAmplitude must be -1 or in [1, 255]");
    addBottomRow = builder.addBottomRow;
    addNumberRow = builder.addNumberRow;
    numberRowSymbols = builder.numberRowSymbols;
  }

  /** Density-scaled defaults suitable for a terminal keyboard. */
  public Config(Resources resources, IKeyEventHandler handler)
  {
    this(new Builder(resources, handler));
  }

  static Config preview(Resources resources)
  {
    return new Config(resources, NO_OP_HANDLER);
  }

  private static float positive(float value, String name)
  {
    if (value <= 0f)
      throw new IllegalArgumentException(name + " must be positive");
    return value;
  }

  private static float nonNegative(float value, String name)
  {
    if (value < 0f)
      throw new IllegalArgumentException(name + " must not be negative");
    return value;
  }

  private static long nonNegative(long value, String name)
  {
    if (value < 0L)
      throw new IllegalArgumentException(name + " must not be negative");
    return value;
  }

  private static int alpha(int value, String name)
  {
    if (value < 0 || value > 255)
      throw new IllegalArgumentException(name + " must be in [0, 255]");
    return value;
  }

  private static float dp(Resources resources, float value)
  {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
        resources.getDisplayMetrics());
  }

  public static final class Builder
  {
    public IKeyEventHandler handler;
    public float rowHeightPx;
    public float maxKeyboardHeightFraction = 0.42f;
    public float horizontalMarginPx;
    public float bottomMarginPx;
    public float marginTopPx;
    public float keyPaddingPx;
    public float labelSizeRatio = 0.33f;
    public float sublabelSizeRatio = 0.22f;
    public float characterSize = 1.15f;
    public float horizontalKeyMarginRatio = 0.02f;
    public float verticalKeyMarginRatio = 0.015f;
    public boolean bordersEnabled = false;
    public float borderRadiusPx = 0f;
    public float borderWidthPx = 0f;
    public int keyOpacity = 255;
    public int activatedKeyOpacity = 255;
    public int labelBrightness = 255;
    public boolean swipeTrailEnabled = false;
    public float swipeTrailWidthPx;
    public float swipeDistancePx;
    public float sliderStepPx;
    public int circleSensitivity = 2;
    public long longPressTimeoutMs = 600L;
    public long repeatIntervalMs = 65L;
    public boolean keyRepeatEnabled = true;
    public boolean doubleTapShiftLock = false;
    public boolean hapticEnabled = true;
    public long hapticDurationMs = 0L;
    public int hapticAmplitude = -1;
    public boolean keySoundEnabled = false;
    public Typeface labelFont = null;
    public boolean addBottomRow = true;
    public boolean addNumberRow = false;
    public boolean numberRowSymbols = true;

    public Builder(Resources resources, IKeyEventHandler handler)
    {
      this.handler = Objects.requireNonNull(handler, "handler");
      boolean landscape = resources.getConfiguration().orientation
          == Configuration.ORIENTATION_LANDSCAPE;
      // Design defaults: 48-56dp portrait / 40-48dp landscape, capped against the full
      // terminal-root height by Keyboard2View's AT_MOST measurement.
      rowHeightPx = dp(resources, landscape ? 44f : 52f);
      // Landscape screens are short: anything past ~40% starves the terminal to a line or two
      // once the status bar, window bar and extra-keys row take their share.
      maxKeyboardHeightFraction = landscape ? 0.40f : 0.42f;
      horizontalMarginPx = dp(resources, 3f);
      bottomMarginPx = dp(resources, 7f);
      marginTopPx = resources.getDimension(R.dimen.margin_top);
      keyPaddingPx = resources.getDimension(R.dimen.key_padding);
      swipeTrailWidthPx = dp(resources, 3f);
      DisplayMetrics dm = resources.getDisplayMetrics();
      float dpiRatio = Math.max(dm.xdpi, dm.ydpi) / Math.min(dm.xdpi, dm.ydpi);
      float swipeScaling = Math.min(dm.widthPixels, dm.heightPixels) / 10f * dpiRatio;
      // Upstream preference defaults: swipe_dist=15, slider_sensitivity=30.
      swipeDistancePx = 15f / 25f * swipeScaling;
      sliderStepPx = 30f / 100f * swipeScaling;
    }

    public Config build()
    {
      return new Config(this);
    }
  }

  public static interface IKeyEventHandler
  {
    public void key_down(KeyValue value, boolean is_swipe);
    public void key_up(KeyValue value, Pointers.Modifiers mods);
    public void mods_changed(Pointers.Modifiers mods);
    public void suggestion_entered(String text);
  }

  private static final IKeyEventHandler NO_OP_HANDLER = new IKeyEventHandler()
  {
    @Override public void key_down(KeyValue value, boolean is_swipe) {}
    @Override public void key_up(KeyValue value, Pointers.Modifiers mods) {}
    @Override public void mods_changed(Pointers.Modifiers mods) {}
    @Override public void suggestion_entered(String text) {}
  };
}
