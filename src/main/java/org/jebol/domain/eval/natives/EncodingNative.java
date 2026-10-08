package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.SeriesContents;
import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class EncodingNative extends DefaultNative {

    protected final Encodings encodings;

    protected EncodingNative(Encodings encodings) {
        this.encodings = encodings;
    }

    protected Set<Datatype> anyStringOr(Datatype... alsoAccepted) {
        return Typeset.ANY_STRING.membersAnd(alsoAccepted);
    }

    protected Set<Datatype> aCountOrPosition() {
        Set<Datatype> accepted = EnumSet.copyOf(aPartLimit());
        accepted.remove(Datatype.PAIR);
        return Set.copyOf(accepted);
    }

    protected String textOf(Value value) {
        return switch (value) {
            case BinaryValue bytes -> new String(bytes.octetsFromHere(), StandardCharsets.UTF_8);
            case AnyStringValue written -> written.text();
            default -> Molder.form(value);
        };
    }

    protected Optional<Long> howManyWanted(Value source, List<Value> arguments, Set<String> refinements) {
        return argumentOf("part", 0, arguments, refinements)
                .filter(count -> !(count instanceof NoneValue))
                .map(count -> unitsCountedBy(source, count));
    }

    private long unitsCountedBy(Value source, Value count) {
        return switch (count) {
            case IntegerValue(long magnitude) when magnitude > Integer.MAX_VALUE
                    || magnitude < Integer.MIN_VALUE ->
                    throw Raised.of(EvaluationFailure.OUT_OF_RANGE, count);
            case IntegerValue(long magnitude) -> magnitude;
            case DecimalValue fraction when fraction.datatype() != Datatype.PERCENT ->
                    (long) Comparison.asDouble(fraction);
            case RebolSeries upTo -> distanceFrom(source, upTo);
            default -> throw Raised.of(EvaluationFailure.INVALID_PART, count);
        };
    }

    private long distanceFrom(Value source, RebolSeries upTo) {
        if (!(source instanceof RebolSeries from)
                || from.datatype() != upTo.datatype()
                || !from.sharesStorageWith(upTo)) {
            throw Raised.of(EvaluationFailure.INVALID_PART, upTo);
        }
        return upTo.index() - from.index();
    }

    protected byte[] octetsWithinAnyPart(Value source, List<Value> arguments, Set<String> refinements) {
        byte[] octets = source.asOctets();
        return howManyWanted(source, arguments, refinements)
                .map(count -> count < 0
                        ? theOctetsBehind(source, octets, -count)
                        : Arrays.copyOf(octets, (int) Math.min(count, octets.length)))
                .orElse(octets);
    }

    private byte[] theOctetsBehind(Value source, byte[] octets, long count) {
        if (!(source instanceof RebolSeries positioned)) {
            return new byte[0];
        }
        int landsOn = (int) Math.max(1, positioned.index() - count);
        byte[] fromThere = positioned.atIndex(landsOn).asOctets();
        return Arrays.copyOf(fromThere, fromThere.length - octets.length);
    }

    protected byte[] theUnitsAskedFor(Value value, List<Value> arguments, Set<String> refinements) {
        Optional<Long> asked = howManyWanted(value, arguments, refinements);
        if (asked.isPresent() && asked.get() < 0) {
            return theUnitsBehind(value, -asked.get());
        }
        int howMany = asked.map(Long::intValue).orElse(SeriesContents.EVERY_ONE);
        return asBytes(SeriesContents.octetsContributedBy(value, howMany));
    }

    private byte[] theUnitsBehind(Value value, long count) {
        if (!(value instanceof RebolSeries positioned)) {
            return new byte[0];
        }
        int reachedBack = (int) Math.min(count, positioned.index() - 1);
        return asBytes(SeriesContents.octetsContributedBy(
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
        Optional<Long> asked = howManyWanted(value, arguments, refinements);
        if (asked.isPresent() && asked.get() < 0) {
            return theTextBehind(value, -asked.get());
        }
        return asked.map(count -> text.substring(0, (int) Math.min(count, text.length())))
                .orElse(text);
    }

    private String theTextBehind(Value value, long count) {
        if (!(value instanceof RebolSeries positioned)) {
            return "";
        }
        int reachedBack = (int) Math.min(count, positioned.index() - 1);
        String whole = textOf(positioned.atIndex(positioned.index() - reachedBack));
        return whole.substring(0, Math.min(reachedBack, whole.length()));
    }
}
