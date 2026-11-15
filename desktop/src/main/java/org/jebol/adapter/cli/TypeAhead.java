package org.jebol.adapter.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

final class TypeAhead extends InputStream {

    private final InputStream typed;

    private byte[] kept = new byte[0];

    TypeAhead(InputStream typed) {
        this.typed = typed;
    }

    synchronized byte[] takeWhatHasArrived() {
        try {
            int waiting = typed.available();
            if (waiting <= 0) {
                return new byte[0];
            }
            byte[] arrived = new byte[waiting];
            int read = typed.read(arrived, 0, waiting);
            return read <= 0 ? new byte[0] : Arrays.copyOf(arrived, read);
        } catch (IOException gone) {
            return new byte[0];
        }
    }

    synchronized void keep(byte[] more) {
        byte[] joined = Arrays.copyOf(kept, kept.length + more.length);
        System.arraycopy(more, 0, joined, kept.length, more.length);
        kept = joined;
    }

    synchronized void dropKept() {
        kept = new byte[0];
    }

    @Override
    public int read() throws IOException {
        synchronized (this) {
            if (kept.length > 0) {
                int next = kept[0] & 0xFF;
                kept = Arrays.copyOfRange(kept, 1, kept.length);
                return next;
            }
        }
        return typed.read();
    }

    @Override
    public int read(byte[] into, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        synchronized (this) {
            if (kept.length > 0) {
                int taken = Math.min(length, kept.length);
                System.arraycopy(kept, 0, into, offset, taken);
                kept = Arrays.copyOfRange(kept, taken, kept.length);
                return taken;
            }
        }
        return typed.read(into, offset, length);
    }

    @Override
    public int available() throws IOException {
        int waiting;
        synchronized (this) {
            waiting = kept.length;
        }
        return waiting + typed.available();
    }
}
