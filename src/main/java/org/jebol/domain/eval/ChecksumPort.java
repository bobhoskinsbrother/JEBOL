package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

final class ChecksumPort {

    static final String HANDLE_TYPE = "checksum";

    private ChecksumPort() {
    }

    static void startEvenOnAnAlreadyOpenPort(PortValue port, String method) {
        port.setField("extra", HandleValue.context(HANDLE_TYPE,
                System.identityHashCode(port.context()),
                JavaObjectValue.of(sumNamed(method))));
        port.setField("data", NoneValue.none());
    }

    private interface RunningSumThatAReadDoesNotEnd {

        void add(byte[] octets, int from, int length);

        byte[] soFar();
    }

    private static RunningSumThatAReadDoesNotEnd sumNamed(String method) {
        String algorithm = Encodings.DIGESTS.get(method);
        if (algorithm == null) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, method);
        }
        if (Encodings.RIPEMD_160.equals(algorithm)
                || Encodings.XXH_3.equals(algorithm)
                || Encodings.XXH_32.equals(algorithm)
                || Encodings.XXH_64.equals(algorithm)
                || Encodings.XXH_128.equals(algorithm)
                || Encodings.MD_4.equals(algorithm)) {
            return keepingTheBytes(method);
        }
        try {
            return aroundTheDigest(MessageDigest.getInstance(algorithm));
        } catch (NoSuchAlgorithmException unavailable) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, method);
        }
    }

    private static RunningSumThatAReadDoesNotEnd aroundTheDigest(
            MessageDigest digest) {
        return new RunningSumThatAReadDoesNotEnd() {

            @Override
            public void add(byte[] octets, int from, int length) {
                digest.update(octets, from, length);
            }

            @Override
            public byte[] soFar() {
                try {
                    return ((MessageDigest) digest.clone()).digest();
                } catch (CloneNotSupportedException cannotBeSplit) {
                    throw Raised.of(EvaluationFailure.INVALID_SPEC,
                            digest.getAlgorithm());
                }
            }
        };
    }

    private static RunningSumThatAReadDoesNotEnd keepingTheBytes(String method) {
        return new RunningSumThatAReadDoesNotEnd() {

            private byte[] kept = new byte[0];

            @Override
            public void add(byte[] octets, int from, int length) {
                byte[] grown = Arrays.copyOf(kept, kept.length + length);
                System.arraycopy(octets, from, grown, kept.length, length);
                kept = grown;
            }

            @Override
            public byte[] soFar() {
                return Encodings.digestOf(kept, method);
            }
        };
    }

    private static RunningSumThatAReadDoesNotEnd inProgress(PortValue port) {
        if (!port.isOpen()
                || !(port.fieldNamed("extra") instanceof HandleValue held)
                || !HANDLE_TYPE.equals(held.typeName())
                || !(held.payload() instanceof JavaObjectValue wrapped)
                || !(wrapped.held().orElse(null) instanceof RunningSumThatAReadDoesNotEnd sum)) {
            return null;
        }
        return sum;
    }

    static void stop(PortValue port) {
        port.setField("extra", NoneValue.none());
        port.setField("data", NoneValue.none());
    }

    static void add(PortValue port, byte[] whole, int startsAt,
            Long seekTo, Long partWanted) {
        RunningSumThatAReadDoesNotEnd sum = inProgress(port);
        if (sum == null) {
            return;
        }
        long from = startsAt;
        if (seekTo != null) {
            from = Math.clamp(from + seekTo, 0, whole.length);
        }
        long length = whole.length - from;
        if (partWanted != null) {
            length = lengthOfTheWindowCountingBackwardsWhenNegative(
                    partWanted, from, length);
            if (partWanted < 0) {
                from = Math.max(0, from + partWanted);
            }
        }
        if (length <= 0) {
            return;
        }
        sum.add(whole, (int) from, (int) Math.min(length, whole.length - from));
    }

    private static long lengthOfTheWindowCountingBackwardsWhenNegative(
            long wanted, long from, long remaining) {
        if (wanted >= 0) {
            return Math.min(wanted, remaining);
        }
        long backwards = -wanted;
        long overshoot = from - backwards;
        return overshoot < 0 ? backwards + overshoot : backwards;
    }

    static Value digestSoFarLeftInTheDataFieldAsWell(PortValue port) {
        RunningSumThatAReadDoesNotEnd sum = inProgress(port);
        if (sum == null) {
            return NoneValue.none();
        }
        BinaryValue answered = BinaryValue.of(bytesAsInts(sum.soFar()));
        port.setField("data", answered);
        return answered;
    }

    private static int[] bytesAsInts(byte[] octets) {
        int[] widened = new int[octets.length];
        for (int at = 0; at < octets.length; at++) {
            widened[at] = octets[at] & 0xFF;
        }
        return widened;
    }

    static String methodOf(PortValue port) {
        if (!(port.fieldNamed("spec") instanceof org.jebol.domain.value.ObjectValue spec)
                || !spec.context().holds("method")
                || !(spec.context().ownSlotFor("method").value()
                        instanceof WordValue method)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, "checksum");
        }
        return method.canonical();
    }
}
