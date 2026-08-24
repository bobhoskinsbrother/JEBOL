package org.jebol.domain.value;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * How many bytes the series buffers are holding, which is what STATS reports.
 *
 * <p>Rebol's STATS answers {@code PG_Mem_Usage}, the total its own allocator
 * has handed out for series. JEBOL answered the JVM's heap instead, which is a
 * different quantity and moves for reasons that have nothing to do with the
 * script: it counts every object the interpreter and the JVM itself are
 * holding, and it falls when a collection runs whether or not the script let
 * anything go. Rebol's own test measures the difference. It makes a five
 * million character string, asks whether the number went up by at least that,
 * drops the string, recycles, and asks whether the number came back. Against
 * the heap the first question passed by luck and the second failed.
 *
 * <p>So each buffer says what it is holding, and says again when it grows. A
 * phantom reference gives the bytes back when the collector takes the buffer,
 * and the queue is drained where the answer is read rather than on a thread of
 * its own, so no reading can land between a collection and its bookkeeping.
 *
 * <p>Held once per process rather than once per interpreter, which is what
 * {@code PG_} means in the C as well. The test runner forks a JVM per worker
 * and runs sequentially inside one, so two interpreters never write to this at
 * the same moment.
 *
 * <p>The number cannot equal Rebol's and is not meant to. A Rebol block holds
 * its values inline and a Java one holds references to values counted where
 * they live, so the two count a tree of series differently. What is the same
 * is the shape: a large buffer arriving is visible, and letting go of it gives
 * the bytes back.
 */
public final class SeriesMemory {

    private static final ReferenceQueue<Object> COLLECTED = new ReferenceQueue<>();
    private static final Set<Reservation> OUTSTANDING = ConcurrentHashMap.newKeySet();
    private static final AtomicLong HELD = new AtomicLong();

    private SeriesMemory() {
    }

    /** What one buffer is holding, from its first byte to its collection. */
    public static final class Reservation extends PhantomReference<Object> {

        private long bytes;

        private Reservation(Object buffer, long bytes) {
            super(buffer, COLLECTED);
            this.bytes = bytes;
        }

        /** Says the buffer has grown or shrunk, and by how much. */
        public void nowHolds(long grownTo) {
            HELD.addAndGet(grownTo - bytes);
            bytes = grownTo;
        }
    }

    /**
     * Starts counting a buffer.
     *
     * <p>The reservation is what the buffer keeps and what the collector
     * hands back. It never points at the buffer -- a phantom reference is the
     * whole reason this can be counted without keeping it alive.
     *
     * <p>It gives back what the collector has already taken first, and that
     * line is not tidiness. A reservation has to be held somewhere until its
     * buffer dies, or nothing is left to be enqueued; draining only where the
     * answer is read meant the outstanding set kept one object per buffer ever
     * allocated, for as long as nothing called STATS. The whole test run ran
     * out of heap.
     */
    public static Reservation reserve(Object buffer, long bytes) {
        giveBackWhatTheCollectorTook();
        Reservation started = new Reservation(buffer, bytes);
        OUTSTANDING.add(started);
        HELD.addAndGet(bytes);
        return started;
    }

    /** The bytes every live series buffer is holding between them. */
    public static long bytesHeld() {
        giveBackWhatTheCollectorTook();
        return HELD.get();
    }

    private static void giveBackWhatTheCollectorTook() {
        Reservation gone;
        while ((gone = (Reservation) COLLECTED.poll()) != null) {
            OUTSTANDING.remove(gone);
            HELD.addAndGet(-gone.bytes);
        }
    }

    /**
     * Collects, and waits until it has happened.
     *
     * <p>What RECYCLE needs and what {@code System.gc()} alone does not give:
     * the call is a request the JVM may take its time over, so a STATS read
     * straight afterwards can see the memory still held and report that
     * nothing was freed. Waiting until a reference known to be unreachable is
     * enqueued is the proof that a cycle finished, and the bytes are given
     * back before this returns.
     *
     * <p>Bounded, because a JVM run with explicit collection disabled would
     * otherwise wait for something that is never going to happen.
     */
    public static long collectNow() {
        long before = bytesHeld();
        long giveUpAt = System.nanoTime() + SECONDS_TO_WAIT_FOR_A_COLLECTION * 1_000_000_000L;
        while (System.nanoTime() < giveUpAt && !aCollectionHasFinished()) {
            giveBackWhatTheCollectorTook();
        }
        giveBackWhatTheCollectorTook();
        return Math.max(0, before - HELD.get());
    }

    private static final long SECONDS_TO_WAIT_FOR_A_COLLECTION = 2;

    private static boolean aCollectionHasFinished() {
        ReferenceQueue<Object> proof = new ReferenceQueue<>();
        PhantomReference<Object> unreachable =
                new PhantomReference<>(new Object(), proof);
        System.gc();
        try {
            return proof.remove(50) != null;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return true;
        } finally {
            unreachable.clear();
        }
    }
}
