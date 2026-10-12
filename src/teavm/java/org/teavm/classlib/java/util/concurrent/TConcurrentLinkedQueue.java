package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

/**
 * The stand-in for {@code java.util.concurrent.ConcurrentLinkedQueue}, which
 * the classlib does not carry. In the browser there is one JavaScript thread
 * doing everything, so the plain deque behind it is as safe as the lock-free
 * queue it stands in for; on the JVM the real class runs and nothing here is
 * ever loaded.
 */
public class TConcurrentLinkedQueue<E> implements Queue<E> {

    private final ArrayDeque<E> held = new ArrayDeque<>();

    @Override
    public int size() {
        return held.size();
    }

    @Override
    public boolean isEmpty() {
        return held.isEmpty();
    }

    @Override
    public boolean contains(Object sought) {
        return held.contains(sought);
    }

    @Override
    public Iterator<E> iterator() {
        return held.iterator();
    }

    @Override
    public Object[] toArray() {
        return held.toArray();
    }

    @Override
    public <T> T[] toArray(T[] target) {
        return held.toArray(target);
    }

    @Override
    public boolean add(E given) {
        held.addLast(given);
        return true;
    }

    @Override
    public boolean offer(E given) {
        return add(given);
    }

    @Override
    public E remove() {
        return held.pop();
    }

    @Override
    public E poll() {
        return held.pollFirst();
    }

    @Override
    public E element() {
        return held.getFirst();
    }

    @Override
    public E peek() {
        return held.peekFirst();
    }

    @Override
    public boolean remove(Object gone) {
        return held.remove(gone);
    }

    @Override
    public boolean containsAll(Collection<?> wanted) {
        return held.containsAll(wanted);
    }

    @Override
    public boolean addAll(Collection<? extends E> arriving) {
        return held.addAll(arriving);
    }

    @Override
    public boolean removeAll(Collection<?> gone) {
        return held.removeAll(gone);
    }

    @Override
    public boolean retainAll(Collection<?> kept) {
        return held.retainAll(kept);
    }

    @Override
    public void clear() {
        held.clear();
    }
}
