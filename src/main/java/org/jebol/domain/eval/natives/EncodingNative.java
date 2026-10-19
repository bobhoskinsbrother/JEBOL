package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.SeriesContents;
import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class EncodingNative extends DefaultNative {

    protected final Encodings encodings;

    protected EncodingNative(Encodings encodings) {
        this.encodings = encodings;
    }

    protected Set<Datatype> acceptAnyStringOr(Datatype... alsoAccepted) {
        return TypesetValue.ANY_STRING.membersAnd(alsoAccepted);
    }

    protected Set<Datatype> acceptACountOrPosition() {
        Set<Datatype> accepted = new LinkedHashSet<>(listThePartLimitTypes());
        accepted.remove(PairValue.TYPE);
        return accepted;
    }

    protected String textOf(Value value) {
        return switch (value) {
            case BinaryValue bytes -> new String(bytes.octetsFromHere(), StandardCharsets.UTF_8);
            case AnyStringValue written -> written.text();
            default -> Molder.form(value);
        };
    }

    protected Optional<Long> countHowManyWanted(Value source, List<Value> arguments, Set<String> refinements) {
        return readTheArgumentOf("part", 0, arguments, refinements)
                .filter(count -> !(count instanceof NoneValue))
                .map(count -> countTheUnitsBy(source, count));
    }

    private long countTheUnitsBy(Value source, Value count) {
        return switch (count) {
            case IntegerValue whole when whole.magnitude() > Integer.MAX_VALUE
                    || whole.magnitude() < Integer.MIN_VALUE ->
                    throw Raised.of(EvaluationFailure.OUT_OF_RANGE, count);
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction ->
                    (long) Comparison.asDouble(fraction);
            case RebolSeries upTo -> measureTheDistanceFrom(source, upTo);
            default -> throw Raised.of(EvaluationFailure.INVALID_PART, count);
        };
    }

    private long measureTheDistanceFrom(Value source, RebolSeries upTo) {
        if (!(source instanceof RebolSeries from)
                || from.datatype() != upTo.datatype()
                || !from.sharesStorageWith(upTo)) {
            throw Raised.of(EvaluationFailure.INVALID_PART, upTo);
        }
        return upTo.index() - from.index();
    }

    protected byte[] readTheOctetsWithinAnyPart(Value source, List<Value> arguments, Set<String> refinements) {
        byte[] octets = source.asOctets();
        return countHowManyWanted(source, arguments, refinements)
                .map(count -> count < 0
                        ? readTheOctetsBehind(source, octets, -count)
                        : Arrays.copyOf(octets, (int) Math.min(count, octets.length)))
                .orElse(octets);
    }

    private byte[] readTheOctetsBehind(Value source, byte[] octets, long count) {
        if (!(source instanceof RebolSeries positioned)) {
            return new byte[0];
        }
        int landsOn = (int) Math.max(1, positioned.index() - count);
        byte[] fromThere = positioned.atIndex(landsOn).asOctets();
        return Arrays.copyOf(fromThere, fromThere.length - octets.length);
    }

    protected byte[] readTheUnitsAskedFor(Value value, List<Value> arguments, Set<String> refinements) {
        Optional<Long> asked = countHowManyWanted(value, arguments, refinements);
        if (asked.isPresent() && asked.get() < 0) {
            return readTheUnitsBehind(value, -asked.get());
        }
        int howMany = asked.map(Long::intValue).orElse(SeriesContents.EVERY_ONE);
        return asBytes(SeriesContents.collectTheOctetsContributedBy(value, howMany));
    }

    private byte[] readTheUnitsBehind(Value value, long count) {
        if (!(value instanceof RebolSeries positioned)) {
            return new byte[0];
        }
        int reachedBack = (int) Math.min(count, positioned.index() - 1);
        return asBytes(SeriesContents.collectTheOctetsContributedBy(
                positioned.atIndex(positioned.index() - reachedBack), reachedBack));
    }

    private byte[] asBytes(int[] octets) {
        byte[] bytes = new byte[octets.length];
        for (int at = 0; at < octets.length; at++) {
            bytes[at] = (byte) octets[at];
        }
        return bytes;
    }

    protected String textWithinAnyPart(Value value, List<Value> arguments, Set<String> refinements) {
        String text = textOf(value);
        Optional<Long> asked = countHowManyWanted(value, arguments, refinements);
        if (asked.isPresent() && asked.get() < 0) {
            return readTheTextBehind(value, -asked.get());
        }
        return asked.map(count -> text.substring(0, (int) Math.min(count, text.length())))
                .orElse(text);
    }

    private String readTheTextBehind(Value value, long count) {
        if (!(value instanceof RebolSeries positioned)) {
            return "";
        }
        int reachedBack = (int) Math.min(count, positioned.index() - 1);
        String whole = textOf(positioned.atIndex(positioned.index() - reachedBack));
        return whole.substring(0, Math.min(reachedBack, whole.length()));
    }
}
