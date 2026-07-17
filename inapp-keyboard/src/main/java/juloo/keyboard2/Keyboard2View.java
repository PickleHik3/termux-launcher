package juloo.keyboard2;

import android.content.Context;
import android.graphics.Canvas;
import android.media.AudioManager;
import android.view.KeyEvent;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import java.util.Objects;

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
  private Paint _indicatorPaint;
  private float _indicatorShaderWidth = -1f;
  private final Paint _trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final SparseArray<Trail> _trails = new SparseArray<Trail>();

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
    applyTheme();
  }

  private void applyTheme()
  {
    _trailPaint.setColor(_theme.pressedColor);
    _trailPaint.setStrokeWidth(_config.swipeTrailWidthPx);
    _indicatorPaint = null;
    _indicatorShaderWidth = -1f;
    setBackgroundColor(withOpacity(_theme.colorKeyboard, _theme.opacity));
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
    _shift_key = _keyboard.findKeyWithValue(KeyValue.SHIFT);
    _compose_key = _keyboard.findKeyWithValue(KeyValue.COMPOSE);
    resetInputStateInternal(true);
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
    int p;
    switch (event.getActionMasked())
    {
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_POINTER_UP:
        p = event.getActionIndex();
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
        KeyboardData.Key key = getKeyAtPosition(tx, ty);
        if (key != null)
        {
          _pointers.onTouchDown(tx, ty, event.getPointerId(p), key);
          if (_config.swipeTrailEnabled)
            _trails.put(event.getPointerId(p), new Trail(tx, ty));
        }
        break;
      case MotionEvent.ACTION_MOVE:
        for (p = 0; p < event.getPointerCount(); p++)
        {
          _pointers.onTouchMove(event.getX(p), event.getY(p), event.getPointerId(p));
          Trail trail = _trails.get(event.getPointerId(p));
          if (trail != null)
            trail.update(event.getX(p), event.getY(p));
        }
        break;
      case MotionEvent.ACTION_CANCEL:
        _pointers.onTouchCancelCommit();
        _trails.clear();
        requestDisallowIntercept(false);
        break;
      default:
        return (false);
    }
    if (_config.swipeTrailEnabled)
      invalidate();
    return (true);
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
      float cappedHeight = capReferenceHeight
          * Math.min(1f, _config.maxKeyboardHeightFraction * _heightScale);
      rowHeight = Math.min(rowHeight,
          Math.max(0f, cappedHeight - fixedHeight) / _keyboard.keysHeight);
    }
    else if (heightMode == MeasureSpec.EXACTLY)
    {
      rowHeight = Math.min(rowHeight,
          Math.max(0f, heightSize - fixedHeight) / _keyboard.keysHeight);
    }

    _tc = new Theme.Computed(_theme, _config, _keyWidth, _keyboard, rowHeight,
        _keyMarginScale, _keyCornerRadiusOverridePx);
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
    if (_theme.indicatorColors != null)
      drawIndicatorStrip(canvas);
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
        boolean isKeyDown = _pointers.isKeyDown(k);
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
        drawKeyFrame(canvas, x, y, keyW, keyH, tc_key);
        if (k.keys[0] != null)
          drawLabel(canvas, k.keys[0], keyW / 2f + x, y, keyH, isKeyDown, tc_key);
        for (int i = 1; i < 9; i++)
        {
          if (k.keys[i] != null)
            drawSubLabel(canvas, k.keys[i], x, y, keyW, keyH, i, isKeyDown, tc_key);
        }
        drawIndication(canvas, k, x, y, keyW, keyH, _tc);
        x += _keyWidth * k.width;
      }
      y += row.height * _tc.row_height;
    }
    if (_config.swipeTrailEnabled)
      for (int i = 0; i < _trails.size(); i++)
      {
        Trail trail = _trails.valueAt(i);
        canvas.drawLine(trail.startX, trail.startY, trail.endX, trail.endY,
            _trailPaint);
      }
  }

  @Override
  public void onDetachedFromWindow()
  {
    resetInputStateInternal(true);
    requestDisallowIntercept(false);
    super.onDetachedFromWindow();
  }

  /** Draw the theme's underglow as a small centered pill in the keyboard's top
      margin, echoing the launcher dock's page-indicator styling. */
  private void drawIndicatorStrip(Canvas canvas)
  {
    float width = getWidth();
    if (width <= 0f)
      return;
    float density = getResources().getDisplayMetrics().density;
    float height = _config.marginTopPx > 0f ? _config.marginTopPx : 3f * density;
    float pillWidth = Math.min(width * 0.28f, 140f * density);
    float pillLeft = (width - pillWidth) / 2f;
    if (_indicatorPaint == null || _indicatorShaderWidth != width)
    {
      _indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      _indicatorPaint.setShader(new LinearGradient(pillLeft, 0f,
          pillLeft + pillWidth, 0f, _theme.indicatorColors, null,
          Shader.TileMode.CLAMP));
      _indicatorPaint.setAlpha(Theme.multiplyAlpha(230, _theme.opacity));
      _indicatorShaderWidth = width;
    }
    float top = getPaddingTop();
    float radius = height / 2f;
    _tmpRect.set(pillLeft, top, pillLeft + pillWidth, top + height);
    canvas.drawRoundRect(_tmpRect, radius, radius, _indicatorPaint);
  }

  /** Draw borders and background of the key. */
  void drawKeyFrame(Canvas canvas, float x, float y, float keyW, float keyH,
      Theme.Computed.Key tc)
  {
    float r = tc.border_radius;
    float w = tc.border_width;
    float padding = w / 2.f;
    _tmpRect.set(x + padding, y + padding, x + keyW - padding, y + keyH - padding);
    tc.positionGradient(y, keyH);
    canvas.drawRoundRect(_tmpRect, r, r, tc.bg_paint);
    if (w > 0.f)
    {
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

  private void drawLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyH, boolean isKeyDown, Theme.Computed.Key tc)
  {
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    float textSize = scaleTextSize(kv, true);
    Paint p = tc.label_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), labelColor(kv, isKeyDown, false, tc), textSize);
    canvas.drawText(kv.getString(), x, (keyH - p.ascent() - p.descent()) / 2f + y, p);
  }

  private void drawSubLabel(Canvas canvas, KeyValue kv, float x, float y,
      float keyW, float keyH, int sub_index, boolean isKeyDown,
      Theme.Computed.Key tc)
  {
    Paint.Align a = LABEL_POSITION_H[sub_index];
    Vertical v = LABEL_POSITION_V[sub_index];
    kv = modifyKey(kv, _mods);
    if (kv == null)
      return;
    float textSize = scaleTextSize(kv, false);
    Paint p = tc.sublabel_paint(kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT), labelColor(kv, isKeyDown, true, tc), textSize, a);
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
}
