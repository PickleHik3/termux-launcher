package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Matrix;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

/**
 * Live blur plans a picture once per motion ({@link LiveBackdrop.MotionPlan}): a moving frame
 * reads the kept path and views beneath and works out only the surface's matrix. Parity: what a
 * kept plan answers on a later frame of the motion is what a whole walk answers then.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LiveBackdropMotionPlanTest {

    private LiveBackdropFrameLayout mRoot;
    private View mBottom;
    private LiveBackdropFrameLayout mPage;
    private View mUnder;
    private View mSurface;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        mRoot = new LiveBackdropFrameLayout(context);
        mBottom = new View(context);
        mPage = new LiveBackdropFrameLayout(context);
        mUnder = new View(context);
        mSurface = new View(context);
        mRoot.addView(mBottom, new ViewGroup.LayoutParams(400, 800));
        mRoot.addView(mPage, new ViewGroup.LayoutParams(400, 800));
        mPage.addView(mUnder, new ViewGroup.LayoutParams(300, 300));
        mPage.addView(mSurface, new ViewGroup.LayoutParams(200, 100));
        layout();
    }

    private void layout() {
        mRoot.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        mRoot.layout(0, 0, 400, 800);
    }

    @Test
    public void aKeptPlanAnswersWhatAWholeWalkDoesAsThePageSlides() {
        LiveBackdrop recorder = new LiveBackdrop(mRoot);
        LiveBackdrop.MotionPlan plan = new LiveBackdrop.MotionPlan();
        assertTrue(recorder.collectPlanned(plan, mSurface, true));
        int stamp = plan.stamp;
        for (float slide : new float[] {-37.5f, -120f, -260.25f}) {
            mPage.setTranslationX(slide);
            assertTrue(recorder.collectPlanned(plan, mSurface, true));
            assertEquals("the plan was kept", stamp, plan.stamp);
            ArrayList<View> kept = new ArrayList<>(recorder.collectedBeneath());
            Matrix keptMatrix = new Matrix(recorder.collectedMatrix());

            LiveBackdrop fresh = new LiveBackdrop(mRoot);
            assertTrue(fresh.collectPlanned(new LiveBackdrop.MotionPlan(), mSurface, false));
            assertEquals(fresh.collectedBeneath(), kept);
            assertEquals(fresh.collectedMatrix(), keptMatrix);
        }
        float[] origin = {0f, 0f};
        recorder.collectedMatrix().mapPoints(origin);
        assertEquals(-260.25f, origin[0], 1e-3f);
    }

    @Test
    public void aViewBeneathThatStopsDrawingPlansWhole() {
        LiveBackdrop recorder = new LiveBackdrop(mRoot);
        LiveBackdrop.MotionPlan plan = new LiveBackdrop.MotionPlan();
        assertTrue(recorder.collectPlanned(plan, mSurface, true));
        assertTrue(recorder.collectedBeneath().contains(mBottom));
        mBottom.setVisibility(View.INVISIBLE);
        assertTrue(recorder.collectPlanned(plan, mSurface, true));
        assertFalse(recorder.collectedBeneath().contains(mBottom));
        assertTrue(recorder.collectedBeneath().contains(mUnder));
    }

    @Test
    public void aMovedStampPlansWholeAndRestPlansEveryFrame() {
        LiveBackdrop recorder = new LiveBackdrop(mRoot);
        LiveBackdrop.MotionPlan plan = new LiveBackdrop.MotionPlan();
        assertTrue(recorder.collectPlanned(plan, mSurface, true));
        View added = new View(RuntimeEnvironment.getApplication());
        mPage.addView(added, 0, new ViewGroup.LayoutParams(10, 10));
        // Kept: the plan is the motion's until something moves the stamp (a layout pass does).
        assertTrue(recorder.collectPlanned(plan, mSurface, true));
        assertFalse(recorder.collectedBeneath().contains(added));
        recorder.forgetPlansForTest();
        assertTrue(recorder.collectPlanned(plan, mSurface, true));
        assertTrue(recorder.collectedBeneath().contains(added));
        // At rest nothing is kept: no view is held, and every frame walks.
        assertTrue(recorder.collectPlanned(plan, mSurface, false));
        assertEquals(LiveBackdrop.MotionPlan.NONE, plan.stamp);
        assertTrue(plan.beneath.isEmpty());
    }

    @Test
    public void aPlanHoldsOnlyWhileMovingAtItsOwnStamp() {
        assertTrue(LiveBackdrop.planHolds(4, 4, true));
        assertFalse(LiveBackdrop.planHolds(4, 4, false));
        assertFalse(LiveBackdrop.planHolds(4, 5, true));
        assertFalse(LiveBackdrop.planHolds(LiveBackdrop.MotionPlan.NONE,
            LiveBackdrop.MotionPlan.NONE, true));
    }
}
