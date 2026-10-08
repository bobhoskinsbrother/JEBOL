package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.value.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

final class ChecksumPort {

    static final String HANDLE_TYPE = "checksum";

    private static final Set<String> DIGESTS_THAT_KEEP_THE_BYTES = Set.of(
            Encodings.RIPEMD_160, Encodings.XXH_3, Encodings.XXH_32,
            Encodings.XXH_64, Encodings.XXH_128, Encodings.MD_4);

    private final Encodings encodings;

    ChecksumPort(Encodings encodings) {
        this.encodings = encodings;
    }

    private interface RunningSumThatAReadDoesNotEnd {

        void add(byte[] octets, int from, int length);

        byte[] soFar();
    }

    void startEvenOnAnAlreadyOpenPort(PortValue port, String method) {
        port.setField("extra", HandleValue.context(HANDLE_TYPE,
                System.identityHashCode(port.context()),
                JavaObjectValue.of(sumNamed(method))));
        port.setField("data", NoneValue.none());
    }

    private RunningSumThatAReadDoesNotEnd sumNamed(String method) {
        String algorithm = Optional.ofNullable(Encodings.DIGESTS.get(method))
                .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_SPEC, method));
        if (DIGESTS_THAT_KEEP_THE_BYTES.contains(algorithm)) {
            return keepingTheBytes(method);
        }
        try {
            return aroundTheDigest(MessageDigest.getInstance(algorithm));
        } catch (NoSuchAlgorithmException unavailable) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, method);
        }
    }

    private RunningSumThatAReadDoesNotEnd aroundTheDigest(MessageDigest digest) {
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
                    throw Raised.of(EvaluationFailure.INVALID_SPEC, digest.getAlgorithm());
                }
            }
        };
    }

    private RunningSumThatAReadDoesNotEnd keepingTheBytes(String method) {
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
                return encodings.digestOf(kept, method);
            }
        };
    }

    private Optional<RunningSumThatAReadDoesNotEnd> inProgress(PortValue port) {
        if (port.isOpen()
                && port.fieldValue("extra") instanceof HandleValue held
                && HANDLE_TYPE.equals(held.typeName())
                && held.payload() instanceof JavaObjectValue wrapped) {
            return wrapped.held()
                    .filter(RunningSumThatAReadDoesNotEnd.class::isInstance)
                    .map(RunningSumThatAReadDoesNotEnd.class::cast);
        }
        return Optional.empty();
    }

    void stop(PortValue port) {
        port.setField("extra", NoneValue.none());
        port.setField("data", NoneValue.none());
    }

    long seekedFrom(byte[] whole, int startsAt, long seekBy) {
        return Math.clamp(startsAt + seekBy, 0L, whole.length);
    }

    void add(PortValue port, byte[] whole, long from) {
        addTheWindow(port, whole, from, whole.length - from);
    }

    void addPart(PortValue port, byte[] whole, long from, long wanted) {
        long length = lengthOfTheWindowCountingBackwardsWhenNegative(
                wanted, from, whole.length - from);
        addTheWindow(port, whole, wanted < 0 ? Math.max(0, from + wanted) : from, length);
    }

    private void addTheWindow(PortValue port, byte[] whole, long from, long length) {
        if (length > 0) {
            inProgress(port).ifPresent(sum -> sum.add(
                    whole, (int) from, (int) Math.min(length, whole.length - from)));
        }
    }

    private long lengthOfTheWindowCountingBackwardsWhenNegative(
            long wanted, long from, long remaining) {
        if (wanted >= 0) {
            return Math.min(wanted, remaining);
        }
        long backwards = -wanted;
        long overshoot = from - backwards;
        return overshoot < 0 ? backwards + overshoot : backwards;
    }

    Value digestSoFarLeftInTheDataFieldAsWell(PortValue port) {
        return inProgress(port).<Value>map(sum -> {
            BinaryValue answered = BinaryValue.ofBytes(sum.soFar());
            port.setField("data", answered);
            return answered;
        }).orElseGet(NoneValue::none);
    }

    String methodOf(PortValue port) {
        if (!(port.fieldValue("spec") instanceof ObjectValue(Context context))
                || !context.holds("method")
                || !(context.ownSlotFor("method").value() instanceof WordValue method)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, "checksum");
        }
        return method.canonical();
    }
}
