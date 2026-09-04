package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

/**
 * An open file, which is a series with a position rather than a whole.
 *
 * <p>{@code p-file.c}. A file port is the one port that behaves like a series:
 * it has an index, SKIP and AT move it, HEAD and TAIL go to the ends, and
 * {@code length?} counts what is left rather than what there is. READ takes
 * from the position and leaves the position past what it took, so reading
 * twice gives the whole file and then nothing.
 *
 * <p>The position lives on the port and the bytes live on the filesystem. That
 * is the whole design: nothing is buffered here, so two ports open on one file
 * see each other's writes, as they do in the C.
 */
final class SeekableFilePort {

    private SeekableFilePort() {
    }

    /** Where the position is kept, in the port's own STATE field. */
    private static final String THE_POSITION = "state";

    static long positionOf(PortValue port) {
        return port.fieldNamed(THE_POSITION) instanceof IntegerValue at
                ? at.magnitude()
                : 0;
    }

    static void moveTo(PortValue port, long position) {
        port.setField(THE_POSITION, IntegerValue.of(Math.max(0, position)));
    }

    /** The path a file port was opened on. */
    static String pathOf(PortValue port) {
        Value spec = port.fieldNamed("spec");
        if (spec instanceof org.jebol.domain.value.ObjectValue fields
                && fields.context().holds("ref")
                && fields.context().ownSlotFor("ref").value()
                        instanceof StringValue named) {
            return named.text();
        }
        return "";
    }

    /**
     * How many bytes the file holds, or nothing when it is not there.
     *
     * <p>Asked of the filesystem every time rather than remembered, because
     * the file is the truth: a write through another port, or by anything else
     * on the machine, changes the answer and the port has no way of hearing
     * about it.
     */
    static long sizeOf(FilePort files, String path) {
        return files.informationAbout(path)
                .flatMap(FileInformation::size)
                .orElse(0L);
    }

    /**
     * READ from the position, taking everything left or a stated count.
     *
     * <p>The position ends where the reading stopped, which is what makes a
     * second READ answer nothing: {@code read port} twice is the file and then
     * the empty binary, and the suite asserts exactly that.
     */
    static Value readFrom(FilePort files, PortValue port, Long howMany) {
        String path = pathOf(port);
        long size = sizeOf(files, path);
        long at = Math.min(positionOf(port), size);
        long wanted = howMany == null ? size - at : howMany;
        if (wanted < 0) {
            wanted = Math.max(0, Math.min(-wanted, at));
            at -= wanted;
        }
        long taken = Math.max(0, Math.min(wanted, size - at));
        byte[] whole = files.readBytes(path);
        int[] part = new int[(int) taken];
        for (int step = 0; step < taken; step++) {
            part[step] = whole[(int) at + step] & 0xFF;
        }
        moveTo(port, at + taken);
        return new BinaryValue(BinaryStorage.of(part), 1);
    }

    /**
     * WRITE at the position, which pushes the position past what was written.
     *
     * <p>Past the end is allowed and the gap is zero bytes, because
     * {@code writeAt} on a file that stops short simply lengthens it. The
     * suite writes fifteen bytes at offset twelve of a twelve-byte file and
     * expects the two to join up.
     */
    static void writeAt(FilePort files, PortValue port, byte[] contents) {
        String path = pathOf(port);
        long at = positionOf(port);
        files.writeAt(path, at, contents);
        moveTo(port, at + contents.length);
    }

    /**
     * What LENGTH? answers: how much is left, never how much there is.
     *
     * <p>A directory has no bytes to be part way through, so what is left of
     * one is how many names it holds. That is what makes {@code empty?} on a
     * directory port ask whether the directory is empty, which is the only
     * reading of the word that means anything there.
     */
    static Value lengthLeft(FilePort files, PortValue port) {
        String path = pathOf(port);
        if (port.schemeName().equals("dir")) {
            return IntegerValue.of(files.namesIn(path).size());
        }
        return IntegerValue.of(Math.max(0, sizeOf(files, path) - positionOf(port)));
    }

    /** What SIZE? answers: the whole file, wherever the position stands. */
    static Value wholeSize(FilePort files, PortValue port) {
        return IntegerValue.of(sizeOf(files, pathOf(port)));
    }

    /** Whether the position has reached the end, or the directory is empty. */
    static boolean atTail(FilePort files, PortValue port) {
        String path = pathOf(port);
        if (port.schemeName().equals("dir")) {
            return files.namesIn(path).isEmpty();
        }
        return positionOf(port) >= sizeOf(files, path);
    }

    /**
     * A directory port's contents: the names in it, as files.
     *
     * <p>A name that is itself a directory keeps its trailing slash, which is
     * how {@code dir?} on a read entry answers without asking the filesystem
     * again.
     */
    static Value namesIn(FilePort files, String path) {
        java.util.List<Value> names = new java.util.ArrayList<>();
        for (String name : files.namesIn(path)) {
            names.add(StringValue.of(name, Datatype.FILE));
        }
        return org.jebol.domain.value.BlockValue.block(names);
    }

    /** The port's own answer to QUERY, or none where there is no file. */
    static Value informationAbout(FilePort files, String path) {
        return files.informationAbout(path).isPresent()
                ? IntegerValue.of(sizeOf(files, path))
                : NoneValue.none();
    }
}
