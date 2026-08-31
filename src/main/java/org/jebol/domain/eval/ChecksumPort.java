package org.jebol.domain.eval;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * A port that hashes what is written to it, {@code checksum://}.
 *
 * <p>{@code Checksum_Actor} in {@code p-checksum.c}. The digest is built up
 * across writes rather than computed in one go, which is the whole point of it:
 * a file too large to hold in memory can be summed a block at a time.
 *
 * <p>The method comes off the spec, which the scheme's own INIT fills from any
 * of three places -- {@code checksum:md5}, {@code checksum://md5} or a spec
 * block -- and defaults to MD5.
 *
 * <p>Reading must not end the sum. The C copies the context onto the stack and
 * finishes the copy, with a comment saying why: "using copy so READ will not
 * destroy intermediate context state by calling *_Finish". A digest can be
 * cloned here, which is the same trick and the reason this works at all --
 * {@code read port} twice running gives the same answer, and writing more
 * afterwards carries on from where the writes had got to rather than from
 * nothing.
 */
final class ChecksumPort {

    static final String HANDLE_TYPE = "checksum";

    private ChecksumPort() {
    }

    /**
     * Starts the sum, which OPEN does on an already-open port as well.
     *
     * <p>{@code Checksum_Open} clears the context whether or not one was
     * there, so opening a port that is already open throws away what has been
     * written to it. Rebol's own suite says so in a comment beside the
     * assertion -- "opening already opened port restarts computation" -- and
     * builds a sum twice from the same port to prove it.
     */
    static void start(PortValue port, String method) {
        port.setField("extra", HandleValue.context(HANDLE_TYPE,
                System.identityHashCode(port.context()),
                JavaObjectValue.of(digestNamed(method))));
        port.setField("data", NoneValue.none());
    }

    private static MessageDigest digestNamed(String method) {
        String named = Encodings.DIGESTS.get(method);
        if (named == null) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, method);
        }
        try {
            return MessageDigest.getInstance(named);
        } catch (NoSuchAlgorithmException unavailable) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, method);
        }
    }

    /**
     * The sum in progress, or nothing where the port was never opened.
     *
     * <p>READ and UPDATE both answer none on a closed port rather than
     * raising: {@code if (!IS_OPEN(req)) return R_NONE}.
     */
    private static MessageDigest inProgress(PortValue port) {
        if (!port.isOpen()
                || !(port.fieldNamed("extra") instanceof HandleValue held)
                || !HANDLE_TYPE.equals(held.typeName())
                || !(held.payload() instanceof JavaObjectValue wrapped)
                || !(wrapped.held().orElse(null) instanceof MessageDigest digest)) {
            return null;
        }
        return digest;
    }

    /** Forgets the sum and the answer, which is what CLOSE does. */
    static void stop(PortValue port) {
        port.setField("extra", NoneValue.none());
        port.setField("data", NoneValue.none());
    }

    /**
     * Adds a run of bytes to the sum and answers the port, so writes chain.
     *
     * <p>The window is worked out the way the C does it, from the value's own
     * index outwards. {@code /seek} moves the start and is clamped to the
     * series; {@code /part} counts from there, and a negative count reaches
     * *backwards*, which is why {@code write/part port tail bin -2} sums the
     * two bytes before the tail rather than nothing at all. A window that ends
     * up empty is not an error -- the C returns the port untouched.
     */
    static void add(PortValue port, byte[] whole, int startsAt,
            Long seekTo, Long partWanted) {
        MessageDigest digest = inProgress(port);
        if (digest == null) {
            return;
        }
        long from = startsAt;
        if (seekTo != null) {
            from = Math.clamp(from + seekTo, 0, whole.length);
        }
        long length = whole.length - from;
        if (partWanted != null) {
            length = lengthOfTheWindow(partWanted, from, length);
            if (partWanted < 0) {
                from = Math.max(0, from + partWanted);
            }
        }
        if (length <= 0) {
            return;
        }
        digest.update(whole, (int) from, (int) Math.min(length, whole.length - from));
    }

    /**
     * How many bytes {@code /part} asks for, counting backwards when it is
     * negative and never reaching past either end.
     */
    private static long lengthOfTheWindow(long wanted, long from, long remaining) {
        if (wanted >= 0) {
            return Math.min(wanted, remaining);
        }
        long backwards = -wanted;
        long overshoot = from - backwards;
        return overshoot < 0 ? backwards + overshoot : backwards;
    }

    /**
     * The sum as it stands, left in {@code port/data} as well as answered.
     *
     * <p>Both READ and UPDATE fill the data field; only READ hands the digest
     * back, which is why {@code update port} answers the port and the sum is
     * then found at {@code port/data}.
     */
    static Value digestSoFar(PortValue port) {
        MessageDigest digest = inProgress(port);
        if (digest == null) {
            return NoneValue.none();
        }
        MessageDigest finishing;
        try {
            finishing = (MessageDigest) digest.clone();
        } catch (CloneNotSupportedException cannotBeSplit) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, digest.getAlgorithm());
        }
        BinaryValue sum = BinaryValue.of(
                bytesAsInts(finishing.digest()));
        port.setField("data", sum);
        return sum;
    }

    private static int[] bytesAsInts(byte[] octets) {
        int[] widened = new int[octets.length];
        for (int at = 0; at < octets.length; at++) {
            widened[at] = octets[at] & 0xFF;
        }
        return widened;
    }

    /** The method the port's spec names, which its INIT has already defaulted. */
    static String methodOf(PortValue port) {
        if (!(port.fieldNamed("spec") instanceof org.jebol.domain.value.ObjectValue spec)
                || !spec.context().holds("method")
                || !(spec.context().ownSlotFor("method").value()
                        instanceof WordValue named)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, "checksum");
        }
        return named.canonical();
    }
}
