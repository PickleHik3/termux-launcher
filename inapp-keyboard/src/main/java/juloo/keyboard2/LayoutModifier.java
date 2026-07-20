package juloo.keyboard2;

import android.content.res.Resources;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Pure composition of embedded-only optional rows and host-enabled extra keys. */
public final class LayoutModifier
{
  private LayoutModifier() {}

  /**
   * Composes the final keyboard, mirroring the ordering of upstream's
   * {@code modify_layout}:
   * <ol>
   * <li>the bottom row is inserted first so its {@code loc} slots take part in
   *     the extra-keys pass;</li>
   * <li>{@code loc}-flagged slots survive only when their key is enabled in
   *     {@link LayoutOptions#extraKeys};</li>
   * <li>enabled keys absent from the layout are added at their preferred
   *     position;</li>
   * <li>the optional number row is inserted last so extra keys never land on
   *     it.</li>
   * </ol>
   */
  public static KeyboardData modify(KeyboardData keyboard,
      LayoutOptions options, Resources resources)
  {
    Objects.requireNonNull(keyboard, "keyboard");
    Objects.requireNonNull(options, "options");
    Objects.requireNonNull(resources, "resources");

    // Deterministic placement order; KeyValue is Comparable. Keys already on
    // the layout are removed before the placement pass.
    final TreeMap<KeyValue, KeyboardData.PreferredPos> extraKeys =
        new TreeMap<KeyValue, KeyboardData.PreferredPos>(options.extraKeys);
    KeyboardData result = keyboard;
    try
    {
      // Add the bottom row before computing the layout's key set so its 'loc'
      // slots are treated like the main layout's.
      if (options.addBottomRow && result.bottom_row)
      {
        KeyboardData.Row bottom = KeyboardData.load_row(resources, R.xml.bottom_row);
        result = result.insert_row(bottom, result.rows.size());
      }
    }
    catch (Exception error)
    {
      throw new IllegalStateException("Bundled keyboard row could not be parsed", error);
    }

    // Keys present on the layout (including 'loc' slots) before extra keys.
    Set<KeyValue> layoutKeys = new HashSet<KeyValue>(result.getKeys().keySet());

    result = result.mapKeys(new KeyboardData.MapKeyValues()
    {
      @Override
      public KeyValue apply(KeyValue key, boolean localized)
      {
        if (localized && !extraKeys.containsKey(key))
          return null;
        if (key.getKind() == KeyValue.Kind.Event
            && key.getEvent() == KeyValue.Event.ACTION)
          return KeyValue.ENTER;
        return key;
      }
    });

    // Add enabled keys that are not already on the layout ('loc' slots kept
    // above count as present).
    extraKeys.keySet().removeAll(layoutKeys);
    if (!extraKeys.isEmpty())
      result = result.addExtraKeys(extraKeys.entrySet().iterator());

    try
    {
      // Inserted after the extra keys so they never land on the number row.
      if (options.addNumberRow && !result.embedded_number_row)
      {
        int rowId = options.numberRowSymbols
            ? R.xml.number_row : R.xml.number_row_no_symbols;
        KeyboardData.Row numbers = KeyboardData.load_row(resources, rowId);
        numbers = modify_number_row(numbers, result);
        result = result.insert_row(numbers, 0);
      }
    }
    catch (Exception error)
    {
      throw new IllegalStateException("Bundled keyboard row could not be parsed", error);
    }

    return result;
  }

  static KeyboardData.Row modify_number_row(KeyboardData.Row row,
      KeyboardData keyboard)
  {
    KeyboardData.MapKeyValues map = numpad_script_map(keyboard.numpad_script);
    return map == null ? row : row.mapKeys(map);
  }

  static KeyboardData.MapKeyValues numpad_script_map(String numpadScript)
  {
    final int mapDigit = KeyModifier.modify_numpad_script(numpadScript);
    if (mapDigit == -1)
      return null;
    return new KeyboardData.MapKeyValues()
    {
      @Override
      public KeyValue apply(KeyValue key, boolean localized)
      {
        KeyValue modified = ComposeKey.apply(mapDigit, key);
        return modified == null ? key : modified;
      }
    };
  }

  public static final class LayoutOptions
  {
    public final boolean addBottomRow;
    public final boolean addNumberRow;
    public final boolean numberRowSymbols;
    /** Extra keys merged into the layout; never null. Enables matching
        {@code loc} slots and places the remainder at their preferred
        position. */
    public final Map<KeyValue, KeyboardData.PreferredPos> extraKeys;

    public LayoutOptions(boolean addBottomRow, boolean addNumberRow,
        boolean numberRowSymbols)
    {
      this(addBottomRow, addNumberRow, numberRowSymbols,
          Collections.<KeyValue, KeyboardData.PreferredPos>emptyMap());
    }

    public LayoutOptions(boolean addBottomRow, boolean addNumberRow,
        boolean numberRowSymbols,
        Map<KeyValue, KeyboardData.PreferredPos> extraKeys)
    {
      this.addBottomRow = addBottomRow;
      this.addNumberRow = addNumberRow;
      this.numberRowSymbols = numberRowSymbols;
      this.extraKeys = Collections.unmodifiableMap(
          Objects.requireNonNull(extraKeys, "extraKeys"));
    }

    public static LayoutOptions fromConfig(Config config)
    {
      return new LayoutOptions(config.addBottomRow, config.addNumberRow,
          config.numberRowSymbols);
    }
  }
}
