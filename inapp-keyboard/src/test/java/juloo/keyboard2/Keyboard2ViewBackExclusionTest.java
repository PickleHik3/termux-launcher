package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The embedded keyboard asks the system gestures to leave its side strips (the back swipe) and its
 * rows between them (the handle's quick switch, which would otherwise steal the space bar's
 * cursor slide). The bottom margin under the last row stays the system's, so the handle's
 * sideways swipe still works where only that margin reaches it.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 29)
public class Keyboard2ViewBackExclusionTest
{
  @Test
  public void theSideStripsAndTheRowsBetweenThemAreExcluded()
  {
    List<Rect> rects = Keyboard2View.backExclusionRects(1080, 600, 63, 63, 580);
    assertEquals(Arrays.asList(new Rect(0, 0, 63, 600), new Rect(1017, 0, 1080, 600),
        new Rect(63, 0, 1017, 580)), rects);
  }

  @Test
  public void aSlideAlongTheSpaceBarIsTheKeyboards()
  {
    // The space bar's row runs to the last row's bottom; a sideways drag landing anywhere on it,
    // even where it reaches the handle's band, is inside an exclusion.
    List<Rect> rects = Keyboard2View.backExclusionRects(1080, 600, 63, 63, 580);
    assertTrue(anyContains(rects, 540, 575));
    assertTrue(anyContains(rects, 200, 579));
  }

  @Test
  public void theBottomMarginUnderTheRowsStaysTheSystems()
  {
    List<Rect> rects = Keyboard2View.backExclusionRects(1080, 600, 63, 63, 580);
    assertFalse(anyContains(rects, 540, 590));
  }

  @Test
  public void aSideWithNoBackSwipeAsksOnlyForTheRows()
  {
    assertEquals(Arrays.asList(new Rect(1017, 0, 1080, 600), new Rect(0, 0, 1017, 600)),
        Keyboard2View.backExclusionRects(1080, 600, 0, 63, 600));
    assertEquals(Arrays.asList(new Rect(0, 0, 1080, 600)),
        Keyboard2View.backExclusionRects(1080, 600, 0, 0, 600));
  }

  @Test
  public void aKeyboardNarrowerThanTheInsetsOrNotLaidOutStaysInsideItself()
  {
    assertEquals(Arrays.asList(new Rect(0, 0, 40, 300), new Rect(0, 0, 40, 300)),
        Keyboard2View.backExclusionRects(40, 300, 63, 63, 300));
    assertTrue(Keyboard2View.backExclusionRects(0, 0, 63, 63, 0).isEmpty());
  }

  private static boolean anyContains(List<Rect> rects, int x, int y)
  {
    for (Rect r : rects)
      if (r.contains(x, y))
        return true;
    return false;
  }
}
