package juloo.keyboard2;

import android.util.Log;

/** Minimal logging facade for the vendored parser and embedded view. */
public final class Logs
{
  static final String TAG = "TermuxInAppKeyboard";

  private Logs() {}

  public static void debug(String message)
  {
    Log.d(TAG, message);
  }

  public static void exn(String message, Exception error)
  {
    Log.e(TAG, message, error);
  }
}
