package org.teavm.classlib.java.lang.ref;

public class TPhantomReference<T> extends TWeakReference<T> {

    public TPhantomReference(T referent, TReferenceQueue<T> queue) {
        super(referent, queue);
    }

    @Override
    public T get() {
        return null;
    }
}
