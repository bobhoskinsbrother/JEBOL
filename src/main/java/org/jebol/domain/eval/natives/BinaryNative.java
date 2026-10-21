package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Bincode;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public class BinaryNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "binary";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("ctx", Set.of(ObjectValue.TYPE, BinaryValue.TYPE,
                        IntegerValue.TYPE, NoneValue.TYPE)),
                Parameter.belongTo("init", "spec",
                        Set.of(BinaryValue.TYPE, IntegerValue.TYPE, NoneValue.TYPE)),
                Parameter.belongTo("write", "data", Set.of(BinaryValue.TYPE, BlockValue.TYPE)),
                Parameter.belongTo("read", "code", Set.of(WordValue.TYPE, BlockValue.TYPE,
                        IntegerValue.TYPE, BinaryValue.TYPE)),
                Parameter.belongTo("into", "out", Set.of(BlockValue.TYPE)),
                Parameter.belongTo("with", "num", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("init", "write", "read", "into", "with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            UnaryOperator<Value> lookedUp = item -> fetchIfItAsksToBe(item, evaluator, context);
            ObjectValue held = findTheDialectContextOf(arguments.getFirst());
            readTheArgumentOf("init", 0, arguments, refinements)
                    .ifPresent(replacement -> restartWith(held, replacement));
            if (refinements.contains("write")) {
                arguments.getFirst().requireChangeable();
                writeThroughTheDialect(held, readTheCodesWrittenIn(
                        readTheArgumentOf("write", 0, arguments, refinements).orElseThrow()), lookedUp);
                if (arguments.getFirst() instanceof BinaryValue given) {
                    layBackInto(given, findCursorNamed(held, "buffer").head());
                }
            }
            if (refinements.contains("read")) {
                return readThroughTheDialect(held,
                        readTheArgumentOf("read", 0, arguments, refinements).orElseThrow(),
                        lookedUp,
                        readTheArgumentOf("with", 0, arguments, refinements)
                                .orElseGet(NoneValue::none),
                        readTheArgumentOf("into", 0, arguments, refinements)
                                .filter(into -> !(into instanceof NoneValue)));
            }
            return held;
        };
    }

    private Value fetchIfItAsksToBe(Value item, Evaluator evaluator, Context context) {
        boolean fetches = item instanceof GetWordValue || item instanceof GetPathValue;
        return fetches
                ? evaluator.evaluateOrRaise(BlockValue.of(List.of(item)), context)
                : item;
    }

    private ObjectValue findTheDialectContextOf(Value given) {
        if (given instanceof ObjectValue existing
                && existing.context().contains("buffer")
                && existing.context().findSlotFor("buffer").value() instanceof BinaryValue) {
            return existing;
        }
        return findDialectContextFor(bufferOfTheDialectContext(given));
    }

    private BinaryValue bufferOfTheDialectContext(Value given) {
        if (given instanceof BinaryValue bytes) {
            return bytes;
        }
        if (given instanceof ObjectValue(Context context)
                && context.contains("buffer")
                && context.findSlotFor("buffer").value() instanceof BinaryValue held) {
            return held;
        }
        return BinaryValue.of();
    }

    private ObjectValue findDialectContextFor(BinaryValue buffer) {
        Context made = Context.root();
        made.register("type", WordValue.of("bincode"));
        made.register("buffer", buffer);
        made.register("buffer-write", buffer);
        made.register("r-mask", IntegerValue.of(0));
        made.register("w-mask", IntegerValue.of(0));
        return new ObjectValue(made);
    }

    private void restartWith(ObjectValue held, Value replacement) {
        BinaryValue fresh = replacement instanceof BinaryValue given
                ? BinaryValue.ofBytes(given.octetsFromHere())
                : BinaryValue.of();
        held.context().register("buffer", fresh);
        held.context().register("buffer-write", fresh);
    }

    private void layBackInto(BinaryValue given, BinaryValue written) {
        BinaryStorage storage = given.storage();
        byte[] octets = written.octetsFromHere();
        while (storage.length() > octets.length) {
            storage.removeAt(storage.length());
        }
        for (int at = 1; at <= octets.length; at++) {
            if (at <= storage.length()) {
                storage.set(at, octets[at - 1] & 0xFF);
            } else {
                storage.append(octets[at - 1] & 0xFF);
            }
        }
    }

    private BinaryValue findCursorNamed(ObjectValue held, String field) {
        return held.context().contains(field)
                && held.context().findSlotFor(field).value() instanceof BinaryValue at
                ? at
                : BinaryValue.of();
    }

    private void writeThroughTheDialect(ObjectValue held, List<Value> dialect,
            UnaryOperator<Value> lookedUp) {

        BinaryValue writing = findCursorNamed(held, "buffer-write");
        Bincode.Cursor cursor = new Bincode.Cursor(
                listTheOctetsOfTheBuffer(writing.head()), writing.index() - 1);
        Bincode.write(cursor, new Bincode.Script(dialect, lookedUp),
                () -> Instant.now().getEpochSecond(), this::nameValueRead);
        BinaryValue written = BinaryValue.of(
                cursor.octets().stream().mapToInt(Integer::intValue).toArray());
        held.context().register("buffer", written.atIndex(findCursorNamed(held, "buffer").index()));
        held.context().register("buffer-write", written.atIndex(cursor.at() + 1));
    }

    private Value readThroughTheDialect(ObjectValue held, Value asked,
            UnaryOperator<Value> lookedUp, Value count, Optional<Value> into) {

        BinaryValue reading = findCursorNamed(held, "buffer");
        Bincode.Cursor cursor = new Bincode.Cursor(
                listTheOctetsOfTheBuffer(reading.head()), reading.index() - 1,
                countBitsAlreadyTakenIn(held));
        Value theBlockItself = lookedUp.apply(asked);
        if (theBlockItself instanceof IntegerValue howMany) {
            return readTheseManyBytes(held, reading, cursor, howMany, into);
        }
        List<Value> codes = new ArrayList<>(readTheCodesWrittenIn(theBlockItself));
        if (!(count instanceof NoneValue)) {
            codes.add(count);
        }
        List<Value> read = Bincode.read(cursor, new Bincode.Script(codes, lookedUp),
                this::nameValueRead);
        held.context().register("r-mask", IntegerValue.of(cursor.bitsTaken()));
        if (cursor.cropped() > 0) {
            shortenFromFront(held, cursor);
        } else {
            held.context().register("buffer", reading.atIndex(cursor.at() + 1));
        }
        return into.map(target -> layInto(target, read))
                .orElseGet(() -> shapeLikeAsking(theBlockItself, read));
    }

    private Value readTheseManyBytes(ObjectValue held, BinaryValue reading,
                                     Bincode.Cursor cursor, IntegerValue howMany, Optional<Value> into) {

        if (into.isPresent()) {
            throw Raised.of(EvaluationFailure.FEATURE_NA,
                    "reading a count of bytes into a block");
        }
        List<Value> read = Bincode.read(cursor,
                new Bincode.Script(List.of(WordValue.of("bytes"), howMany),
                        UnaryOperator.identity()),
                this::nameValueRead);
        held.context().register("buffer", reading.atIndex(cursor.at() + 1));
        return read.getFirst();
    }

    private void shortenFromFront(ObjectValue held, Bincode.Cursor cursor) {
        int writingWas = findCursorNamed(held, "buffer-write").index();
        BinaryValue shortened = BinaryValue.of(
                cursor.octets().stream().mapToInt(Integer::intValue).toArray());
        held.context().register("buffer", shortened.atIndex(cursor.at() + 1));
        held.context().register("buffer-write",
                shortened.atIndex(Math.max(1, writingWas - cursor.cropped())));
    }

    private int countBitsAlreadyTakenIn(ObjectValue held) {
        return held.context().contains("r-mask")
                && held.context().findSlotFor("r-mask").value() instanceof IntegerValue(long magnitude)
                ? (int) magnitude
                : 0;
    }

    private void nameValueRead(AnyWordValue word, Value read) {
        if (!word.isBound() || !word.binding().contains(word.canonical())) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, word.spelling());
        }
        ContextSlot slot = word.binding().findSlotFor(word.canonical());
        if (slot.isProtected()) {
            throw Raised.of(EvaluationFailure.LOCKED_WORD, word.spelling());
        }
        slot.setValue(read);
    }

    private List<Integer> listTheOctetsOfTheBuffer(BinaryValue buffer) {
        List<Integer> octets = new ArrayList<>();
        for (byte octet : buffer.octetsFromHere()) {
            octets.add(octet & 0xFF);
        }
        return octets;
    }

    private List<Value> readTheCodesWrittenIn(Value asked) {
        return asked instanceof AnyBlockValue block ? block.remaining() : List.of(asked);
    }

    private Value shapeLikeAsking(Value asked, List<Value> values) {
        if (asked instanceof AnyBlockValue) {
            return BlockValue.of(values);
        }
        return values.isEmpty() ? NoneValue.none() : values.getFirst();
    }

    private Value layInto(Value target, List<Value> read) {
        if (!(target instanceof AnyBlockValue into)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(target));
        }
        int at = into.index();
        for (Value value : read) {
            into.storage().insertAt(at, value);
            at++;
        }
        return into.atIndex(at);
    }
}
