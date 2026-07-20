package juloo.keyboard2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GestureTest
{
  @Test
  public void recognizesSwipeAndRoundtrip()
  {
    Gesture swipe = new Gesture(0, 2);
    assertEquals(Gesture.Name.Swipe, swipe.get_gesture());
    swipe.pointer_up();
    assertFalse(swipe.is_in_progress());
    assertEquals(Gesture.Name.Swipe, swipe.get_gesture());

    Gesture roundtrip = new Gesture(4, 2);
    assertTrue(roundtrip.moved_to_center());
    assertEquals(Gesture.Name.Roundtrip, roundtrip.get_gesture());
  }

  @Test
  public void recognizesBothCircleDirectionsAndCancellation()
  {
    Gesture clockwise = new Gesture(0, 2);
    assertTrue(clockwise.changed_direction(3));
    assertEquals(Gesture.Name.Circle, clockwise.get_gesture());
    clockwise.pointer_up();
    assertEquals(Gesture.Name.Circle, clockwise.get_gesture());

    Gesture anticlockwise = new Gesture(0, 2);
    assertTrue(anticlockwise.changed_direction(13));
    assertEquals(Gesture.Name.Anticircle, anticlockwise.get_gesture());
    assertTrue(anticlockwise.changed_direction(2));
    assertEquals(Gesture.Name.None, anticlockwise.get_gesture());
  }
}
