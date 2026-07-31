package juloo.keyboard2;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;

public final class Theme
{
  public final int colorKeyboard;
  public final int colorKey;
  public final int colorKeyActivated;
  public final int colorKeyAction;
  public final int colorKeySpaceBar;

  public final int lockedColor;
  public final int activatedColor;
  public final int pressedColor;
  public final int labelColor;
  public final int subLabelColor;
  public final int secondaryLabelColor;
  public final int greyedLabelColor;
  public final int actionLabelColor;
  public final int actionSubLabelColor;
  public final int actionSecondaryLabelColor;
  public final int actionGreyedLabelColor;
  /** Left-to-right gradient stops for the indicator strip, or null when disabled. */
  public final int[] indicatorColors;
  /** Overlays composited over each key fill as a top-to-bottom gradient; 0 disables. */
  public final int keyGradientTopOverlay;
  public final int keyGradientBottomOverlay;

  public final float keyBorderRadius;
  public final float keyBorderWidth;
  public final float keyBorderWidthActivated;
  public final float keyBorderWidthAction;
  public final float keyBorderWidthSpaceBar;
  public final int keyBorderColorLeft;
  public final int keyBorderColorTop;
  public final int keyBorderColorRight;
  public final int keyBorderColorBottom;
  public final float opacity;

  /** Primary constructor for an app-resolved Material palette. */
  public Theme(Context context, Palette palette)
  {
    getKeyFont(context);
    colorKeyboard = palette.keyboardBackground;
    colorKey = palette.keyBackground;
    colorKeyActivated = palette.activatedKeyBackground;
    colorKeyAction = palette.actionKeyBackground;
    colorKeySpaceBar = palette.spaceBarBackground;
    labelColor = palette.labelColor;
    subLabelColor = palette.subLabelColor;
    secondaryLabelColor = adjustLight(palette.labelColor, palette.secondaryDimming);
    greyedLabelColor = adjustLight(palette.labelColor, palette.greyedDimming);
    actionLabelColor = palette.actionLabelColor;
    actionSubLabelColor = palette.actionSubLabelColor;
    actionSecondaryLabelColor = adjustLight(palette.actionLabelColor, palette.secondaryDimming);
    actionGreyedLabelColor = adjustLight(palette.actionLabelColor, palette.greyedDimming);
    indicatorColors = palette.indicatorColors;
    keyGradientTopOverlay = palette.keyGradientTopOverlay;
    keyGradientBottomOverlay = palette.keyGradientBottomOverlay;
    activatedColor = palette.activatedLabelColor;
    pressedColor = palette.pressedLabelColor;
    lockedColor = palette.lockedModifierColor;
    keyBorderRadius = palette.borderRadius;
    keyBorderWidth = palette.borderEnabled ? palette.borderWidth : 0f;
    keyBorderWidthActivated = keyBorderWidth;
    keyBorderWidthAction = keyBorderWidth;
    keyBorderWidthSpaceBar = keyBorderWidth;
    keyBorderColorLeft = palette.borderColor;
    keyBorderColorTop = palette.borderColor;
    keyBorderColorRight = palette.borderColor;
    keyBorderColorBottom = palette.borderColor;
    opacity = palette.opacity;
  }

  /** Static-style fallback for previews, XML inflation, and tests. */
  public Theme(Context context, AttributeSet attrs)
  {
    this(context, Palette.fromStyle(context, attrs));
  }

  /** Interpolate the value component toward its opposite by alpha. */
  static int adjustLight(int color, float alpha)
  {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    float v = hsv[2];
    hsv[2] = alpha - (2 * alpha - 1) * v;
    return Color.HSVToColor(Color.alpha(color), hsv);
  }

  static int multiplyAlpha(int alpha, float opacity)
  {
    return Math.max(0, Math.min(255, Math.round(alpha * opacity)));
  }

  static Typeface _key_font = null;

  public static synchronized Typeface getKeyFont(Context context)
  {
    if (_key_font == null)
      _key_font = Typeface.createFromAsset(context.getAssets(), "special_font.ttf");
    return _key_font;
  }

  /** Immutable resolved ARGB roles and border geometry. */
  public static final class Palette
  {
    public final int keyboardBackground;
    public final int keyBackground;
    public final int actionKeyBackground;
    public final int spaceBarBackground;
    public final int activatedKeyBackground;
    public final int labelColor;
    public final int subLabelColor;
    public final int activatedLabelColor;
    public final int pressedLabelColor;
    public final int lockedModifierColor;
    public final int borderColor;
    public final boolean borderEnabled;
    public final float borderWidth;
    public final float borderRadius;
    public final float opacity;
    public final float secondaryDimming;
    public final float greyedDimming;
    public final int actionLabelColor;
    public final int actionSubLabelColor;
    /** Left-to-right gradient stops for the indicator strip, or null when disabled. */
    public final int[] indicatorColors;
    /** Overlays composited over each key fill as a top-to-bottom gradient; 0 disables. */
    public final int keyGradientTopOverlay;
    public final int keyGradientBottomOverlay;

    public Palette(int keyboardBackground, int keyBackground,
        int actionKeyBackground, int spaceBarBackground,
        int activatedKeyBackground, int labelColor, int subLabelColor,
        int activatedLabelColor, int pressedLabelColor,
        int lockedModifierColor, int borderColor, boolean borderEnabled,
        float borderWidth, float borderRadius, float opacity)
    {
      this(keyboardBackground, keyBackground, actionKeyBackground,
          spaceBarBackground, activatedKeyBackground, labelColor, subLabelColor,
          activatedLabelColor, pressedLabelColor, lockedModifierColor,
          borderColor, borderEnabled, borderWidth, borderRadius, opacity,
          0.25f, 0.5f);
    }

    public Palette(int keyboardBackground, int keyBackground,
        int actionKeyBackground, int spaceBarBackground,
        int activatedKeyBackground, int labelColor, int subLabelColor,
        int activatedLabelColor, int pressedLabelColor,
        int lockedModifierColor, int borderColor, boolean borderEnabled,
        float borderWidth, float borderRadius, float opacity,
        float secondaryDimming, float greyedDimming)
    {
      this(keyboardBackground, keyBackground, actionKeyBackground,
          spaceBarBackground, activatedKeyBackground, labelColor, subLabelColor,
          activatedLabelColor, pressedLabelColor, lockedModifierColor,
          borderColor, borderEnabled, borderWidth, borderRadius, opacity,
          secondaryDimming, greyedDimming, labelColor, subLabelColor, null);
    }

    public Palette(int keyboardBackground, int keyBackground,
        int actionKeyBackground, int spaceBarBackground,
        int activatedKeyBackground, int labelColor, int subLabelColor,
        int activatedLabelColor, int pressedLabelColor,
        int lockedModifierColor, int borderColor, boolean borderEnabled,
        float borderWidth, float borderRadius, float opacity,
        float secondaryDimming, float greyedDimming,
        int actionLabelColor, int actionSubLabelColor, int[] indicatorColors)
    {
      this(keyboardBackground, keyBackground, actionKeyBackground,
          spaceBarBackground, activatedKeyBackground, labelColor, subLabelColor,
          activatedLabelColor, pressedLabelColor, lockedModifierColor,
          borderColor, borderEnabled, borderWidth, borderRadius, opacity,
          secondaryDimming, greyedDimming, actionLabelColor, actionSubLabelColor,
          indicatorColors, 0, 0);
    }

    public Palette(int keyboardBackground, int keyBackground,
        int actionKeyBackground, int spaceBarBackground,
        int activatedKeyBackground, int labelColor, int subLabelColor,
        int activatedLabelColor, int pressedLabelColor,
        int lockedModifierColor, int borderColor, boolean borderEnabled,
        float borderWidth, float borderRadius, float opacity,
        float secondaryDimming, float greyedDimming,
        int actionLabelColor, int actionSubLabelColor, int[] indicatorColors,
        int keyGradientTopOverlay, int keyGradientBottomOverlay)
    {
      if (borderWidth < 0f || borderRadius < 0f)
        throw new IllegalArgumentException("Border dimensions must not be negative");
      if (opacity < 0f || opacity > 1f)
        throw new IllegalArgumentException("Opacity must be in [0, 1]");
      if (secondaryDimming < 0f || secondaryDimming > 1f
          || greyedDimming < 0f || greyedDimming > 1f)
        throw new IllegalArgumentException("Label dimming must be in [0, 1]");
      if (indicatorColors != null && indicatorColors.length < 2)
        throw new IllegalArgumentException("Indicator needs at least 2 gradient stops");
      this.keyboardBackground = keyboardBackground;
      this.keyBackground = keyBackground;
      this.actionKeyBackground = actionKeyBackground;
      this.spaceBarBackground = spaceBarBackground;
      this.activatedKeyBackground = activatedKeyBackground;
      this.labelColor = labelColor;
      this.subLabelColor = subLabelColor;
      this.activatedLabelColor = activatedLabelColor;
      this.pressedLabelColor = pressedLabelColor;
      this.lockedModifierColor = lockedModifierColor;
      this.borderColor = borderColor;
      this.borderEnabled = borderEnabled;
      this.borderWidth = borderWidth;
      this.borderRadius = borderRadius;
      this.opacity = opacity;
      this.secondaryDimming = secondaryDimming;
      this.greyedDimming = greyedDimming;
      this.actionLabelColor = actionLabelColor;
      this.actionSubLabelColor = actionSubLabelColor;
      this.indicatorColors = indicatorColors == null ? null : indicatorColors.clone();
      this.keyGradientTopOverlay = keyGradientTopOverlay;
      this.keyGradientBottomOverlay = keyGradientBottomOverlay;
    }

    static Palette fromStyle(Context context, AttributeSet attrs)
    {
      TypedArray s = context.getTheme().obtainStyledAttributes(
          attrs, R.styleable.keyboard, 0, 0);
      int keyboard = s.getColor(R.styleable.keyboard_colorKeyboard, 0xFF1B1B1B);
      int key = s.getColor(R.styleable.keyboard_colorKey, 0xFF333333);
      int activatedKey = s.getColor(R.styleable.keyboard_colorKeyActivated, keyboard);
      int actionKey = s.getColor(R.styleable.keyboard_colorKeyAction, key);
      int spaceKey = s.getColor(R.styleable.keyboard_colorKeySpaceBar, key);
      int label = s.getColor(R.styleable.keyboard_colorLabel, Color.WHITE);
      int subLabel = s.getColor(R.styleable.keyboard_colorSubLabel, 0xFFCCCCCC);
      int activatedLabel = s.getColor(
          R.styleable.keyboard_colorLabelActivated, 0xFF3399FF);
      int pressedLabel = s.getColor(R.styleable.keyboard_colorLabelPressed, label);
      int lockedLabel = s.getColor(R.styleable.keyboard_colorLabelLocked, 0xFF33CC33);
      float secondaryDimming = s.getFloat(R.styleable.keyboard_secondaryDimming, 0.25f);
      float greyedDimming = s.getFloat(R.styleable.keyboard_greyedDimming, 0.5f);
      float borderWidth = s.getDimension(R.styleable.keyboard_keyBorderWidth, 0f);
      float borderRadius = s.getDimension(R.styleable.keyboard_keyBorderRadius, 0f);
      int border = s.getColor(R.styleable.keyboard_keyBorderColorBottom, key);
      s.recycle();
      return new Palette(keyboard, key, actionKey, spaceKey, activatedKey,
          label, subLabel, activatedLabel, pressedLabel, lockedLabel, border,
          borderWidth > 0f, borderWidth, borderRadius, 1f,
          secondaryDimming, greyedDimming);
    }
  }

  public static final class Computed
  {
    public final float vertical_margin;
    public final float horizontal_margin;
    public final float margin_top;
    public final float margin_left;
    public final float row_height;
    public final Paint indication_paint;

    public final Key key;
    public final Key key_activated;
    public final Key key_action;
    public final Key key_space_bar;
    public final Key key_suggestion;

    public Computed(Theme theme, Config config, float keyWidth,
        KeyboardData layout, float rowHeight)
    {
      this(theme, config, keyWidth, layout, rowHeight, 1f, -1f, -1f);
    }

    public Computed(Theme theme, Config config, float keyWidth,
        KeyboardData layout, float rowHeight, float keyMarginScale,
        float keyCornerRadiusOverridePx, float keyOpacityOverride)
    {
      row_height = rowHeight;
      vertical_margin = config.verticalKeyMarginRatio * keyMarginScale * row_height;
      horizontal_margin = config.horizontalKeyMarginRatio * keyMarginScale * keyWidth;
      margin_top = config.marginTopPx + vertical_margin / 2f;
      margin_left = horizontal_margin / 2f;
      key = new Key(theme, config, false, KeyboardData.Key.Role.Normal,
          keyCornerRadiusOverridePx, keyOpacityOverride);
      key_action = new Key(theme, config, false, KeyboardData.Key.Role.Action,
          keyCornerRadiusOverridePx, keyOpacityOverride);
      key_space_bar = new Key(theme, config, false, KeyboardData.Key.Role.Space_bar,
          keyCornerRadiusOverridePx, keyOpacityOverride);
      key_activated = new Key(theme, config, true, KeyboardData.Key.Role.Normal,
          keyCornerRadiusOverridePx, keyOpacityOverride);
      key_suggestion = new Key(theme, config, false, KeyboardData.Key.Role.Suggestion,
          keyCornerRadiusOverridePx, keyOpacityOverride);
      indication_paint = init_label_paint(config.labelFont);
      indication_paint.setColor(theme.subLabelColor);
    }

    public static final class Key
    {
      public final Paint bg_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      /** All four border sides share one color, so the frame can be stroked in
          a single anti-aliased pass without clip seams. */
      public final boolean border_uniform;
      /** Unit-height vertical keycap shading gradient, or null when disabled. */
      final LinearGradient _bg_gradient;
      private final Matrix _bg_gradient_matrix;
      public final Paint border_left_paint;
      public final Paint border_top_paint;
      public final Paint border_right_paint;
      public final Paint border_bottom_paint;
      public final float border_width;
      public final float border_radius;
      public final int labelColor;
      public final int subLabelColor;
      public final int secondaryLabelColor;
      public final int greyedLabelColor;
      final Paint _label_paint;
      final Paint _special_label_paint;
      final Paint _sublabel_paint;
      final Paint _special_sublabel_paint;
      final int _label_alpha_bits;
      /** This role's resolved fill (with translucency) and keycap gradient overlays,
          retained so a host color override can tint the glass instead of replacing it. */
      private final int _fill;
      private final int _grad_top;
      private final int _grad_bottom;

      /** Alpha of this role's resolved fill, 0-255; hosts read it to seed opacity editors. */
      public int fillAlpha() { return Color.alpha(_fill); }
      private final Matrix _override_gradient_matrix = new Matrix();
      private int _override_gradient_color;
      private LinearGradient _override_gradient;

      public Key(Theme theme, Config config, boolean activated,
          KeyboardData.Key.Role role, float keyCornerRadiusOverridePx,
          float keyOpacityOverride)
      {
        border_radius = keyCornerRadiusOverridePx >= 0f
            ? keyCornerRadiusOverridePx
            : (config.bordersEnabled ? config.borderRadiusPx : theme.keyBorderRadius);
        int bg_color;
        int alpha;
        if (activated)
        {
          bg_color = theme.colorKeyActivated;
          border_width = config.bordersEnabled
              ? config.borderWidthPx : theme.keyBorderWidthActivated;
          alpha = config.activatedKeyOpacity;
        }
        else
        {
          switch (role)
          {
            case Action:
              bg_color = theme.colorKeyAction;
              border_width = config.bordersEnabled
                  ? config.borderWidthPx : theme.keyBorderWidthAction;
              break;
            case Space_bar:
              bg_color = theme.colorKeySpaceBar;
              border_width = config.bordersEnabled
                  ? config.borderWidthPx : theme.keyBorderWidthSpaceBar;
              break;
            case Suggestion:
              bg_color = Color.TRANSPARENT;
              border_width = 0f;
              break;
            default:
              bg_color = theme.colorKey;
              border_width = config.bordersEnabled
                  ? config.borderWidthPx : theme.keyBorderWidth;
              break;
          }
          alpha = config.keyOpacity;
        }
        boolean actionRole = role == KeyboardData.Key.Role.Action
            || role == KeyboardData.Key.Role.Space_bar;
        labelColor = actionRole ? theme.actionLabelColor : theme.labelColor;
        subLabelColor = actionRole ? theme.actionSubLabelColor : theme.subLabelColor;
        secondaryLabelColor = actionRole
            ? theme.actionSecondaryLabelColor : theme.secondaryLabelColor;
        greyedLabelColor = actionRole
            ? theme.actionGreyedLabelColor : theme.greyedLabelColor;
        // A host-set absolute opacity replaces the theme/config translucency stack entirely,
        // so 100% really is opaque even when the theme keys are glass. Pressed (activated)
        // caps keep their theme look so press feedback stays distinct, and roles the theme
        // leaves fully transparent (suggestions) stay invisible.
        int fill;
        if (keyOpacityOverride >= 0f && !activated && Color.alpha(bg_color) != 0)
          fill = Color.argb(Math.round(255f * Math.min(1f, keyOpacityOverride)),
              Color.red(bg_color), Color.green(bg_color), Color.blue(bg_color));
        else
          fill = withAlpha(bg_color, alpha, theme.opacity);
        _fill = fill;
        _grad_top = theme.keyGradientTopOverlay;
        _grad_bottom = theme.keyGradientBottomOverlay;
        bg_paint.setColor(fill);
        if ((theme.keyGradientTopOverlay != 0 || theme.keyGradientBottomOverlay != 0)
            && Color.alpha(fill) > 0)
        {
          _bg_gradient = new LinearGradient(0f, 0f, 0f, 1f,
              compositeOver(theme.keyGradientTopOverlay, fill),
              compositeOver(theme.keyGradientBottomOverlay, fill),
              Shader.TileMode.CLAMP);
          _bg_gradient_matrix = new Matrix();
          bg_paint.setShader(_bg_gradient);
        }
        else
        {
          _bg_gradient = null;
          _bg_gradient_matrix = null;
        }
        border_uniform = theme.keyBorderColorLeft == theme.keyBorderColorTop
            && theme.keyBorderColorLeft == theme.keyBorderColorRight
            && theme.keyBorderColorLeft == theme.keyBorderColorBottom;
        border_left_paint = init_border_paint(theme, config.keyOpacity, border_width,
            theme.keyBorderColorLeft);
        border_top_paint = init_border_paint(theme, config.keyOpacity, border_width,
            theme.keyBorderColorTop);
        border_right_paint = init_border_paint(theme, config.keyOpacity, border_width,
            theme.keyBorderColorRight);
        border_bottom_paint = init_border_paint(theme, config.keyOpacity, border_width,
            theme.keyBorderColorBottom);
        _label_paint = init_label_paint(config.labelFont);
        _special_label_paint = init_label_paint(_key_font);
        _sublabel_paint = init_label_paint(config.labelFont);
        _special_sublabel_paint = init_label_paint(_key_font);
        _label_alpha_bits = (config.labelBrightness & 0xFF) << 24;
      }

      /** Maps the unit-height keycap gradient onto one key's frame. */
      public void positionGradient(float y, float keyH)
      {
        if (_bg_gradient == null)
          return;
        _bg_gradient_matrix.setScale(1f, Math.max(1f, keyH));
        _bg_gradient_matrix.postTranslate(0f, y);
        _bg_gradient.setLocalMatrix(_bg_gradient_matrix);
      }

      /**
       * Configures [paint] to fill a key with a host override color while keeping this
       * role's translucency and keycap light gradient. The override supplies only hue:
       * its alpha is discarded in favor of the theme fill's alpha, so on a glass theme
       * the color tints the translucent chip rather than covering the wallpaper behind it.
       */
      public void applyOverrideFill(Paint paint, int overrideColor, float y, float keyH)
      {
        int fill = Color.argb(Color.alpha(_fill), Color.red(overrideColor),
            Color.green(overrideColor), Color.blue(overrideColor));
        paint.setColor(fill);
        if ((_grad_top != 0 || _grad_bottom != 0) && Color.alpha(fill) > 0)
        {
          if (_override_gradient == null || _override_gradient_color != overrideColor)
          {
            _override_gradient_color = overrideColor;
            _override_gradient = new LinearGradient(0f, 0f, 0f, 1f,
                compositeOver(_grad_top, fill), compositeOver(_grad_bottom, fill),
                Shader.TileMode.CLAMP);
          }
          _override_gradient_matrix.setScale(1f, Math.max(1f, keyH));
          _override_gradient_matrix.postTranslate(0f, y);
          _override_gradient.setLocalMatrix(_override_gradient_matrix);
          paint.setShader(_override_gradient);
        }
        else
          paint.setShader(null);
      }

      public Paint label_paint(boolean special_font, int color, float text_size)
      {
        Paint p = special_font ? _special_label_paint : _label_paint;
        p.setColor((color & 0x00FFFFFF) | _label_alpha_bits);
        p.setTextSize(text_size);
        return p;
      }

      public Paint sublabel_paint(boolean special_font, int color,
          float text_size, Paint.Align align)
      {
        Paint p = special_font ? _special_sublabel_paint : _sublabel_paint;
        p.setColor((color & 0x00FFFFFF) | _label_alpha_bits);
        p.setTextSize(text_size);
        p.setTextAlign(align);
        return p;
      }
    }

    static Paint init_border_paint(Theme theme, int alpha, float width,
        int color)
    {
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(width);
      p.setColor(withAlpha(color, alpha, theme.opacity));
      return p;
    }

    static int withAlpha(int color, int alpha, float opacity)
    {
      int resultAlpha = multiplyAlpha(Color.alpha(color), alpha / 255f * opacity);
      return Color.argb(resultAlpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Source-over composite of [overlay] on [base], keeping [base]'s translucency. */
    static int compositeOver(int overlay, int base)
    {
      if (overlay == 0)
        return base;
      float oa = Color.alpha(overlay) / 255f;
      float ba = Color.alpha(base) / 255f;
      float outA = oa + ba * (1f - oa);
      if (outA <= 0f)
        return Color.TRANSPARENT;
      int r = Math.round((Color.red(overlay) * oa + Color.red(base) * ba * (1f - oa)) / outA);
      int g = Math.round((Color.green(overlay) * oa + Color.green(base) * ba * (1f - oa)) / outA);
      int b = Math.round((Color.blue(overlay) * oa + Color.blue(base) * ba * (1f - oa)) / outA);
      return Color.argb(Math.round(outA * 255f), r, g, b);
    }

    static Paint init_label_paint(Typeface font)
    {
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
      p.setTextAlign(Paint.Align.CENTER);
      if (font != null)
        p.setTypeface(font);
      return p;
    }
  }
}
