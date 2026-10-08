package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SeriesChange {

    private static final int EVERY_ONE = -1;

    private final Value replacement;
    private final Set<String> refinements;
    private final Optional<Value> part;
    private final Optional<Value> dup;

    public SeriesChange(Value replacement, Set<String> refinements,
            Optional<Value> part, Optional<Value> dup) {
        this.replacement = replacement;
        this.refinements = refinements;
        this.part = part;
        this.dup = dup;
    }

    public Value changed(Value subject) {
        if (subject instanceof StructValue struct) {
            refuseWhatAGobOrAStructDoesNotServe();
            return struct.changedBy(replacement);
        }
        if (subject instanceof ImageValue picture) {
            return picture.changed(replacement, howManyTimesOver(),
                    refinements.contains("only"), part.orElseGet(NoneValue::none));
        }
        if (part.isPresent() && subject instanceof RebolSeries stranded) {
            return replacedAcrossThePart(stranded, part.get());
        }
        if (subject instanceof GobValue gob) {
            refuseWhatAGobOrAStructDoesNotServe();
            GobPath.pokeWhichInsertsRatherThanReplaces(gob, gob.index(), replacement);
            return gob.atIndex(gob.index() + 1);
        }
        Value replacing = dup.<Value>map(replacement::repeatedInABlock).orElse(replacement);
        return switch (subject) {
            case BinaryValue bytes -> overwritten(bytes, replacing);
            case StringValue text -> overwritten((StringValue) text.clampedToTail(), replacing);
            case VectorValue vector -> overwritten((VectorValue) vector.clampedToTail());
            case BlockValue block -> overwritten((BlockValue) block.clampedToTail(), replacing);
            case Value anythingElse -> throw Raised.cannotUseTheAction(anythingElse, "change");
        };
    }

    private Value replacedAcrossThePart(RebolSeries stranded, Value limit) {
        Value copiedReplacement = replacement.copied(replacement instanceof BlockValue);
        long taking = stranded.countUpTo(limit);
        RebolSeries series = stranded.clampedToTail();
        if (taking < 0) {
            long back = Math.min(-taking, series.index() - 1L);
            series = series.atIndex((int) (series.index() - back));
            taking = back;
        }
        SeriesActions arms = (SeriesActions) Actions.of(series).orElseThrow();
        for (long gone = 0; gone < taking && !series.atTail(); gone++) {
            arms.takeOneOutAt(series.index());
        }
        int before = series.storageLength();
        insertInto(series, copiedReplacement);
        return series.atIndex(series.index() + series.storageLength() - before);
    }

    private Value overwritten(BinaryValue bytes, Value replacing) {
        int[] octets = SeriesContents.octetsContributedBy(replacing);
        for (int at = 0; at < octets.length; at++) {
            int where = bytes.index() + at;
            if (where > bytes.storage().length()) {
                bytes.storage().append(octets[at]);
            } else {
                bytes.storage().set(where, octets[at]);
            }
        }
        return bytes.atIndex(bytes.index() + octets.length);
    }

    private Value overwritten(StringValue text, Value replacing) {
        String written = replacing instanceof BlockValue several
                ? several.runTogether()
                : Molder.form(replacing);
        int[] letters = written.codePoints().toArray();
        int overwrittenCount = Math.min(letters.length, text.lengthFromHere());
        for (int gone = 0; gone < overwrittenCount; gone++) {
            text.storage().removeAt(text.index());
        }
        for (int at = 0; at < letters.length; at++) {
            text.storage().insertAt(text.index() + at, letters[at]);
        }
        return text.atIndex(text.index() + letters.length);
    }

    private Value overwritten(VectorValue vector) {
        List<Value> numbers = numbersAdded(vector);
        int asked = refinements.contains("part") ? partCount() : numbers.size();
        int removing = Math.max(0, Math.min(asked, vector.lengthFromHere()));
        for (int gone = 0; gone < removing; gone++) {
            vector.storage().removeAt(vector.index());
        }
        for (int at = numbers.size(); at > 0; at--) {
            vector.storage().insertAt(vector.index(),
                    VectorPath.storedFormOf(vector.kind(), numbers.get(at - 1)));
        }
        return vector.atIndex(vector.index() + numbers.size());
    }

    private Value overwritten(BlockValue block, Value replacing) {
        Optional<BlockValue> spread = !refinements.contains("only")
                && replacing instanceof BlockValue several
                && several.datatype() == Datatype.BLOCK
                ? Optional.of(several)
                : Optional.empty();
        List<Value> replacements = spread.map(BlockValue::remaining).orElse(List.of(replacing));
        for (int at = 0; at < replacements.size(); at++) {
            int where = block.index() + at;
            if (where <= block.storageLength()) {
                block.storage().set(where, replacements.get(at));
            } else {
                block.storage().insertAt(where, replacements.get(at));
            }
            int fromWithinTheSpread = at;
            block.storage().setLineBreakAt(where, spread
                    .map(several -> several.storage()
                            .breaksLineAt(several.index() + fromWithinTheSpread))
                    .orElse(false));
        }
        return block.atIndex(block.index() + replacements.size());
    }

    private void insertInto(RebolSeries stranded, Value value) {
        RebolSeries series = stranded.clampedToTail();
        switch (series) {
            case BlockValue block -> {
                if (value instanceof BlockValue added) {
                    block.storage().spliceInAt(block.index(), added.remaining(),
                            added.storage(), added.index());
                } else {
                    block.storage().spliceInAt(block.index(), List.of(value), null, 1);
                }
            }
            case StringValue text -> {
                int[] added = Molder.form(value).codePoints().toArray();
                for (int at = 0; at < added.length; at++) {
                    text.storage().insertAt(text.index() + at, added[at]);
                }
            }
            case ImageValue image -> pixelsInserted(image, value);
            case GobValue gob -> new GobActions(gob)
                    .givenTheChildrenOf(value, gob.positionWithinThePane());
            case VectorValue vector -> {
                List<Value> numbers = vector.kind().numbersContributedBy(value);
                for (int at = numbers.size(); at > 0; at--) {
                    vector.storage().insertAt(vector.index(),
                            VectorPath.storedFormOf(vector.kind(), numbers.get(at - 1)));
                }
            }
            case BinaryValue bytes -> {
                int[] octets = SeriesContents.octetsContributedBy(value);
                for (int at = octets.length; at > 0; at--) {
                    bytes.storage().insertAt(bytes.index(), octets[at - 1]);
                }
            }
        }
    }

    private void pixelsInserted(ImageValue image, Value value) {
        List<int[]> pixels = new ArrayList<>();
        if (value instanceof ImageValue added) {
            for (int at = 1; at <= added.lengthFromHere(); at++) {
                pixels.add(added.pixelAt(at));
            }
        } else if (value instanceof TupleValue colour) {
            int[] parts = colour.segments();
            pixels.add(new int[] {
                    parts.length > 0 ? parts[0] : 0,
                    parts.length > 1 ? parts[1] : 0,
                    parts.length > 2 ? parts[2] : 0,
                    parts.length > 3 ? parts[3] : 0xFF});
        } else {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "an image takes a pixel or another image, not "
                            + value.datatype().literalSpelling());
        }
        for (int at = 0; at < pixels.size(); at++) {
            int[] channels = pixels.get(at);
            image.storage().insertAt(image.index() + at,
                    channels[0], channels[1], channels[2], channels[3]);
        }
    }

    private List<Value> numbersAdded(VectorValue vector) {
        List<Value> once = vector.kind().numbersContributedBy(replacement);
        long rounds = refinements.contains("dup")
                && dup.orElseGet(NoneValue::none) instanceof IntegerValue(long magnitude)
                ? magnitude
                : 1;
        List<Value> added = new ArrayList<>();
        for (long round = 0; round < rounds; round++) {
            added.addAll(once);
        }
        return added;
    }

    private int partCount() {
        Value limit = part.orElseGet(NoneValue::none);
        if (limit instanceof IntegerValue(long magnitude)) {
            return (int) magnitude;
        }
        if (limit instanceof RebolSeries upTo
                && replacement instanceof RebolSeries from
                && from.sharesStorageWith(upTo)) {
            return Math.abs(upTo.index() - from.index());
        }
        return EVERY_ONE;
    }

    private long howManyTimesOver() {
        return refinements.contains("dup")
                && dup.orElseGet(NoneValue::none) instanceof IntegerValue(long magnitude)
                ? Math.max(0, magnitude)
                : 1;
    }

    private void refuseWhatAGobOrAStructDoesNotServe() {
        for (String unfinished : List.of("part", "only", "dup")) {
            if (refinements.contains(unfinished)) {
                throw Raised.of(EvaluationFailure.FEATURE_NA,
                        "change/" + unfinished + " on a gob is not implemented");
            }
        }
    }
}
