package juloo.keyboard2;

import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

public final class VibratorCompat
{
  private VibratorCompat() {}

  public static void vibrate(View view, Config config)
  {
    if (!config.hapticEnabled || !view.isHapticFeedbackEnabled())
      return;
    if (config.hapticDurationMs <= 0L)
    {
      view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
      return;
    }

    Vibrator vibrator = view.getContext().getSystemService(Vibrator.class);
    if (vibrator == null || !vibrator.hasVibrator())
      return;
    int amplitude = config.hapticAmplitude == -1
        ? VibrationEffect.DEFAULT_AMPLITUDE : config.hapticAmplitude;
    vibrator.vibrate(VibrationEffect.createOneShot(config.hapticDurationMs,
        amplitude));
  }
}
