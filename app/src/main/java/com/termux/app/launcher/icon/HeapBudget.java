package com.termux.app.launcher.icon;

/**
 * The share of the per-app heap a pixel store may hold: one fraction of the memory class, clamped
 * into a byte range so a starved device still gets something usable and a generous one does not
 * hand a cache more than it could ever fill.
 */
public final class HeapBudget {

    private HeapBudget() {
    }

    /**
     * @param memoryClassMb the per-app heap ceiling in MB; anything below zero reads as unknown
     * @param heapDivisor the fraction of that heap the store may hold (16 for one sixteenth)
     * @param minBytes the smallest budget handed out, even on a memory-starved device
     * @param maxBytes the ceiling regardless of how generous the heap is
     */
    public static int of(int memoryClassMb, int heapDivisor, int minBytes, int maxBytes) {
        long heapBytes = (long) Math.max(0, memoryClassMb) * 1024L * 1024L;
        long budget = heapBytes / heapDivisor;
        return (int) Math.max(minBytes, Math.min(maxBytes, budget));
    }
}
