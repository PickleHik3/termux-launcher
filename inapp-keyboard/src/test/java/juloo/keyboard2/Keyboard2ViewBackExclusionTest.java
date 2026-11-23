package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The embedded keyboard asks the back swipe to leave only its side strips. An exclusion over the
 * rest of it does nothing for Back, and where it reaches the navigation handle the system turns
 * off its sideways swipe to the previous app.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 29)
public class Keyboard2ViewBackExclusionTest
{
  @Test
  public void onlyTheSideStripsTheBackSwipeStartsInAreExcluded()
  {
    List<Rect> rects = Keyboard2View.backExclusionRects(1080, 600, 63, 63);
    assertEquals(Arrays.asList(new Rect(0, 0, 63, 600), new Rect(1017, 0, 1080, 600)), rects);
    // Nothing between the strips, so the bottom middle where the handle sits stays the system's.
    for (Rect r : rects)
      assertTrue(!r.contains(540, 590));
  }

  @Test
  public void aSideWithNoBackSwipeAsksForNothing()
  {
    assertEquals(Arrays.asList(new Rect(1017, 0, 1080, 600)),
        Keyboard2View.backExclusionRects(1080, 600, 0, 63));
    assertTrue(Keyboard2View.backExclusionRects(1080, 600, 0, 0).isEmpty());
  }

  @Test
  public void aKeyboardNarrowerThanTheInsetsOrNotLaidOutStaysInsideItself()
  {
    assertEquals(Arrays.asList(new Rect(0, 0, 40, 300), new Rect(0, 0, 40, 300)),
        Keyboard2View.backExclusionRects(40, 300, 63, 63));
    assertTrue(Keyboard2View.backExclusionRects(0, 0, 63, 63).isEmpty());
  }
}
