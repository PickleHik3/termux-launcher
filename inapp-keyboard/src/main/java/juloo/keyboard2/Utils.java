package juloo.keyboard2;

import java.util.Locale;

public final class Utils
{
  private Utils() {}

  /** Turn the first Unicode code point of a string uppercase. */
  public static String capitalize_string(String s)
  {
    if (s.length() < 1)
      return s;
    int i = s.offsetByCodePoints(0, 1);
    return s.substring(0, i).toUpperCase(Locale.getDefault()) + s.substring(i);
  }
}
