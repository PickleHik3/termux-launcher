package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 28)
public class Keyboard2ViewMeasurementTest
{
  @Test
  public void atMostParentReportsDesiredFourRowHeightAndUpstreamTextProportions()
      throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), new NoOpHandler());
    builder.rowHeightPx = 50f;
    builder.maxKeyboardHeightFraction = 0.42f;
    builder.horizontalMarginPx = 0f;
    builder.bottomMarginPx = 0f;
    builder.marginTopPx = 0f;
    Config config = builder.build();
    Keyboard2View view = new Keyboard2View(context, config, palette());
    view.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>"
        + "<row><key c='1'/></row><row><key c='2'/></row>"
        + "<row><key c='3'/></row><row><key c='4'/></row></keyboard>"));

    view.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST));

    assertEquals(200, view.getMeasuredHeight());
    assertTrue("four rows must not collapse to the observed ~110px", view.getMeasuredHeight() > 110);
    assertEquals(0.33f, config.labelSizeRatio, 0.0001f);
    assertEquals(0.22f, config.sublabelSizeRatio, 0.0001f);
    float main = ReflectionHelpers.getField(view, "_mainLabelSize");
    float sub = ReflectionHelpers.getField(view, "_subLabelSize");
    assertEquals(config.labelSizeRatio / config.sublabelSizeRatio, main / sub, 0.0001f);
  }

  @Test
  public void atMostParentAppliesHeightFractionOnlyToFullAvailableHeight() throws Exception
  {
    Context context = RuntimeEnvironment.getApplication();
    Config.Builder builder = new Config.Builder(context.getResources(), new NoOpHandler());
    builder.rowHeightPx = 50f;
    builder.maxKeyboardHeightFraction = 0.42f;
    builder.horizontalMarginPx = 0f;
    builder.bottomMarginPx = 0f;
    builder.marginTopPx = 0f;
    Keyboard2View view = new Keyboard2View(context, builder.build(), palette());
    view.setKeyboard(KeyboardData.load_string_exn(
        "<keyboard bottom_row='false'>"
        + "<row><key c='1'/></row><row><key c='2'/></row>"
        + "<row><key c='3'/></row><row><key c='4'/></row></keyboard>"));

    view.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST));

    assertEquals(126, view.getMeasuredHeight());
  }

  @Test
  public void defaultsUsePortraitAndLandscapeDesignBands()
  {
    Context portrait = RuntimeEnvironment.getApplication();
    float density = portrait.getResources().getDisplayMetrics().density;
    Config portraitConfig = new Config(portrait.getResources(), new NoOpHandler());
    assertEquals(52f * density, portraitConfig.rowHeightPx, 0.001f);
    assertEquals(0.42f, portraitConfig.maxKeyboardHeightFraction, 0.0001f);

    Configuration landscapeConfiguration = new Configuration(
        portrait.getResources().getConfiguration());
    landscapeConfiguration.orientation = Configuration.ORIENTATION_LANDSCAPE;
    Context landscape = portrait.createConfigurationContext(landscapeConfiguration);
    Config landscapeConfig = new Config(landscape.getResources(), new NoOpHandler());
    assertEquals(44f * density, landscapeConfig.rowHeightPx, 0.001f);
    assertEquals(0.55f, landscapeConfig.maxKeyboardHeightFraction, 0.0001f);
  }

  private static Theme.Palette palette()
  {
    return new Theme.Palette(
        Color.BLACK, Color.DKGRAY, Color.DKGRAY, Color.DKGRAY,
        Color.GRAY, Color.WHITE, Color.LTGRAY, Color.WHITE, Color.WHITE,
        Color.WHITE, Color.GRAY, false, 0f, 0f, 1f);
  }

  private static final class NoOpHandler implements Config.IKeyEventHandler
  {
    @Override public void key_down(KeyValue value, boolean isSwipe) {}
    @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers) {}
    @Override public void mods_changed(Pointers.Modifiers modifiers) {}
    @Override public void suggestion_entered(String text) {}
  }
}
