package juloo.keyboard2;

import com.termux.terminal.DebugSwitch;

/**
 * How a key press highlight is animated, chosen at run time without UI so the options can be
 * measured on one build: {@code adb shell setprop debug.tl.press_fx legacy|overlay|nofade}; unset
 * means overlay. The
 * property is re-read at most once per {@link DebugSwitch#REREAD_MS}; between reads
 * {@link #current} is a field read.
 */
final class PressFxMode
{
  enum Mode
  {
    /** The highlight is drawn by the keyboard's own onDraw, one full re-record per frame. */
    LEGACY,
    /** The pressed caps are drawn by a RenderNode that animates without re-recording the view. */
    OVERLAY,
    /** As legacy, but the release fade is skipped: the highlight clears at release. */
    NOFADE
  }

  static final String PROPERTY = "debug.tl.press_fx";

  private final DebugSwitch.Reader _reader;
  private Mode _mode = Mode.OVERLAY;
  private long _readAtMs = Long.MIN_VALUE;

  PressFxMode(DebugSwitch.Reader reader)
  {
    _reader = reader;
  }

  /** {@code legacy} and {@code nofade} are names; anything else, unset included, is {@link Mode#OVERLAY}. */
  static Mode parse(String value)
  {
    if ("legacy".equals(value))
      return Mode.LEGACY;
    if ("nofade".equals(value))
      return Mode.NOFADE;
    return Mode.OVERLAY;
  }

  Mode current(long nowMs)
  {
    if (_readAtMs == Long.MIN_VALUE || nowMs - _readAtMs >= DebugSwitch.REREAD_MS
        || nowMs < _readAtMs)
    {
      _readAtMs = nowMs;
      _mode = parse(_reader.read(PROPERTY));
    }
    return _mode;
  }
}
