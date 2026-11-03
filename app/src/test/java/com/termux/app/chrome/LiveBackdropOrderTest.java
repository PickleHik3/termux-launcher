package com.termux.app.chrome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Which siblings Live blur records beneath a glass: those that draw before it, in ViewGroup's
 * order (drawing order, then a stable sort by Z), never the glass itself or anything above it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LiveBackdropOrderTest {

    private static int[] beneath(int[] preorder, float[] z, boolean[] drawn, int path) {
        int n = preorder.length;
        int[] out = new int[n];
        int written = LiveBackdrop.beneath(n, preorder, z, drawn, path, new int[n], out);
        return Arrays.copyOf(out, written);
    }

    private static boolean[] all(int n) {
        boolean[] drawn = new boolean[n];
        Arrays.fill(drawn, true);
        return drawn;
    }

    @Test
    public void flatChildrenBeforeTheGlassAreBeneathItBottomFirst() {
        assertArrayEquals(new int[]{0, 1},
            beneath(new int[]{0, 1, 2, 3}, new float[4], all(4), 2));
    }

    @Test
    public void theFirstChildHasNothingBeneathIt() {
        assertArrayEquals(new int[0], beneath(new int[]{0, 1, 2}, new float[3], all(3), 0));
    }

    @Test
    public void aSiblingRaisedByElevationIsAboveTheGlassThoughDeclaredBefore() {
        // Child 0 sits at Z 4: it draws after the glass (child 2, Z 0).
        assertArrayEquals(new int[]{1},
            beneath(new int[]{0, 1, 2, 3}, new float[]{4f, 0f, 0f, 0f}, all(4), 2));
    }

    @Test
    public void aSiblingBelowTheGlassInZIsBeneathItThoughDeclaredAfter() {
        // The glass (child 1) is raised to Z 2; child 3 at Z 1 draws before it.
        assertArrayEquals(new int[]{0, 2, 3},
            beneath(new int[]{0, 1, 2, 3}, new float[]{0f, 2f, 0f, 1f}, all(4), 1));
    }

    @Test
    public void equalZKeepsTheDrawingOrder() {
        assertArrayEquals(new int[]{0, 1},
            beneath(new int[]{0, 1, 2}, new float[]{3f, 3f, 3f}, all(3), 2));
    }

    @Test
    public void aCustomDrawingOrderIsFollowed() {
        // Drawn 3, 0, 2, 1: the glass (1) is last, so 3, 0 and 2 are beneath, in that order.
        assertArrayEquals(new int[]{3, 0, 2},
            beneath(new int[]{3, 0, 2, 1}, new float[4], all(4), 1));
    }

    @Test
    public void childrenThatDoNotDrawAreLeftOut() {
        boolean[] drawn = all(4);
        drawn[1] = false;
        assertArrayEquals(new int[]{0, 2},
            beneath(new int[]{0, 1, 2, 3}, new float[4], drawn, 3));
    }

    @Test
    public void aPathChildThatIsNotThereRecordsNothing() {
        assertArrayEquals(new int[0], beneath(new int[]{0, 1}, new float[2], all(2), 5));
    }

    @Test
    public void realViewsAnswerTheSameWayTheirParentDrawsThem() {
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout parent = new FrameLayout(context);
        View wallpaper = new View(context);
        View raised = new View(context);
        View page = new View(context);
        View hidden = new View(context);
        View glass = new View(context);
        View above = new View(context);
        for (View v : new View[]{wallpaper, raised, page, hidden, glass, above}) parent.addView(v);
        raised.setElevation(6f);
        hidden.setVisibility(View.INVISIBLE);

        LiveBackdrop backdrop = new LiveBackdrop(parent);
        ArrayList<View> out = new ArrayList<>();
        backdrop.collectLevel(parent, glass, out);

        List<View> expected = new ArrayList<>();
        Collections.addAll(expected, wallpaper, page);
        assertEquals(expected, out);
    }
}
