package juloo.keyboard2;

/** Terminal-oriented editor capabilities retained for source compatibility. */
public final class EditorConfig
{
  public final boolean numeric_layout;
  public final boolean should_show_candidates_view;
  public final KeyValue action_key_replacement;
  public final KeyValue enter_key_replacement;

  private EditorConfig()
  {
    numeric_layout = false;
    should_show_candidates_view = false;
    action_key_replacement = KeyValue.ENTER;
    enter_key_replacement = null;
  }

  public static EditorConfig forTerminal()
  {
    return new EditorConfig();
  }
}
