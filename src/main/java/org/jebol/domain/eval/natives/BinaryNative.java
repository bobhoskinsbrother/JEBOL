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
                Parameter.required("ctx", Set.of(Datatype.OBJECT, Datatype.BINARY,
                        Datatype.INTEGER, Datatype.NONE)),
                Parameter.belongingTo("init", "spec",
                        Set.of(Datatype.BINARY, Datatype.INTEGER, Datatype.NONE)),
                Parameter.belongingTo("write", "data", Set.of(Datatype.BINARY, Datatype.BLOCK)),
                Parameter.belongingTo("read", "code", Set.of(Datatype.WORD, Datatype.BLOCK,
                        Datatype.INTEGER, Datatype.BINARY)),
                Parameter.belongingTo("into", "out", Set.of(Datatype.BLOCK)),
                Parameter.belongingTo("with", "num", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("init", "write", "read", "into", "with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            UnaryOperator<Value> lookedUp = item -> fetchedIfItAsksToBe(item, evaluator, context);
            ObjectValue held = theDialectContextOf(arguments.getFirst());
            argumentOf("init", 0, arguments, refinements)
                    .ifPresent(replacement -> restartedWith(held, replacement));
            if (refinements.contains("write")) {
                arguments.getFirst().requireChangeable();
                writeThroughTheDialect(held, codesWrittenIn(
                        argumentOf("write", 0, arguments, refinements).orElseThrow()), lookedUp);
                if (arguments.getFirst() instanceof BinaryValue given) {
                    laidBackInto(given, cursorNamed(held, "buffer").head());
                }
            }
            if (refinements.contains("read")) {
                return readThroughTheDialect(held,
                        argumentOf("read", 0, arguments, refinements).orElseThrow(),
                        lookedUp,
                        argumentOf("with", 0, arguments, refinements)
                                .orElseGet(NoneValue::none),
                        argumentOf("into", 0, arguments, refinements)
                                .filter(into -> !(into instanceof NoneValue)));
            }
            return held;
        };
    }

    private Value fetchedIfItAsksToBe(Value item, Evaluator evaluator, Context context) {
        boolean fetches = item instanceof GetWordValue || item instanceof GetPathValue;
        return fetches
                ? evaluator.evaluateOrRaise(BlockValue.block(List.of(item)), context)
                : item;
    }

    private ObjectValue theDialectContextOf(Value given) {
        if (given instanceof ObjectValue existing
                && existing.context().knows("buffer")
                && existing.context().slotFor("buffer").value() instanceof BinaryValue) {
            return existing;
        }
        return theDialectContextFor(bufferOfTheDialectContext(given));
    }

    private BinaryValue bufferOfTheDialectContext(Value given) {
        if (given instanceof BinaryValue bytes) {
            return bytes;
        }
        if (given instanceof ObjectValue(Context context)
                && context.knows("buffer")
                && context.slotFor("buffer").value() instanceof BinaryValue held) {
            return held;
        }
        return BinaryValue.of();
    }

    private ObjectValue theDialectContextFor(BinaryValue buffer) {
        Context made = Context.root();
        made.register("type", WordValue.of("bincode"));
        made.register("buffer", buffer);
        made.register("buffer-write", buffer);
        made.register("r-mask", IntegerValue.of(0));
        made.register("w-mask", IntegerValue.of(0));
        return new ObjectValue(made);
    }

    private void restartedWith(ObjectValue held, Value replacement) {
        BinaryValue fresh = replacement instanceof BinaryValue given
                ? BinaryValue.ofBytes(given.octetsFromHere())
                : BinaryValue.of();
        held.context().register("buffer", fresh);
        held.context().register("buffer-write", fresh);
    }

    private void laidBackInto(BinaryValue given, BinaryValue written) {
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

    private BinaryValue cursorNamed(ObjectValue held, String field) {
        return held.context().knows(field)
                && held.context().slotFor(field).value() instanceof BinaryValue at
                ? at
                : BinaryValue.of();
    }

    private void writeThroughTheDialect(ObjectValue held, List<Value> dialect,
            UnaryOperator<Value> lookedUp) {

        BinaryValue writing = cursorNamed(held, "buffer-write");
        Bincode.Cursor cursor = new Bincode.Cursor(
                octetsOfTheBuffer(writing.head()), writing.index() - 1);
        Bincode.write(cursor, new Bincode.Script(dialect, lookedUp),
                () -> Instant.now().getEpochSecond(), this::nameTheValueRead);
        BinaryValue written = BinaryValue.of(
                cursor.octets().stream().mapToInt(Integer::intValue).toArray());
        held.context().register("buffer", written.atIndex(cursorNamed(held, "buffer").index()));
        held.context().register("buffer-write", written.atIndex(cursor.at() + 1));
    }

    private Value readThroughTheDialect(ObjectValue held, Value asked,
            UnaryOperator<Value> lookedUp, Value count, Optional<Value> into) {

        BinaryValue reading = cursorNamed(held, "buffer");
        Bincode.Cursor cursor = new Bincode.Cursor(
                octetsOfTheBuffer(reading.head()), reading.index() - 1,
                bitsAlreadyTakenIn(held));
        Value theBlockItself = lookedUp.apply(asked);
        if (theBlockItself instanceof IntegerValue howMany) {
            return theseManyBytesRead(held, reading, cursor, howMany, into);
        }
        List<Value> codes = new ArrayList<>(codesWrittenIn(theBlockItself));
        if (!(count instanceof NoneValue)) {
            codes.add(count);
        }
        List<Value> read = Bincode.read(cursor, new Bincode.Script(codes, lookedUp),
                this::nameTheValueRead);
        held.context().register("r-mask", IntegerValue.of(cursor.bitsTaken()));
        if (cursor.cropped() > 0) {
            shortenedFromTheFront(held, cursor);
        } else {
            held.context().register("buffer", reading.atIndex(cursor.at() + 1));
        }
        return into.map(target -> laidInto(target, read))
                .orElseGet(() -> shapedLikeTheAsking(theBlockItself, read));
    }

    private Value theseManyBytesRead(ObjectValue held, BinaryValue reading,
            Bincode.Cursor cursor, IntegerValue howMany, Optional<Value> into) {

        if (into.isPresent()) {
            throw Raised.of(EvaluationFailure.FEATURE_NA,
                    "reading a count of bytes into a block");
        }
        List<Value> read = Bincode.read(cursor,
                new Bincode.Script(List.of(WordValue.of("bytes"), howMany),
                        UnaryOperator.identity()),
                this::nameTheValueRead);
        held.context().register("buffer", reading.atIndex(cursor.at() + 1));
        return read.getFirst();
    }

    private void shortenedFromTheFront(ObjectValue held, Bincode.Cursor cursor) {
        int writingWas = cursorNamed(held, "buffer-write").index();
        BinaryValue shortened = BinaryValue.of(
                cursor.octets().stream().mapToInt(Integer::intValue).toArray());
        held.context().register("buffer", shortened.atIndex(cursor.at() + 1));
        held.context().register("buffer-write",
                shortened.atIndex(Math.max(1, writingWas - cursor.cropped())));
    }

    private int bitsAlreadyTakenIn(ObjectValue held) {
        return held.context().knows("r-mask")
                && held.context().slotFor("r-mask").value() instanceof IntegerValue(long magnitude)
                ? (int) magnitude
                : 0;
    }

    private void nameTheValueRead(AnyWordValue word, Value read) {
        if (!word.isBound() || !word.binding().knows(word.canonical())) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, word.spelling());
        }
        ContextSlot slot = word.binding().slotFor(word.canonical());
        if (slot.isProtected()) {
            throw Raised.of(EvaluationFailure.LOCKED_WORD, word.spelling());
        }
        slot.setValue(read);
    }

    private List<Integer> octetsOfTheBuffer(BinaryValue buffer) {
        List<Integer> octets = new ArrayList<>();
        for (byte octet : buffer.octetsFromHere()) {
            octets.add(octet & 0xFF);
        }
        return octets;
    }

    private List<Value> codesWrittenIn(Value asked) {
        return asked instanceof AnyBlockValue block ? block.remaining() : List.of(asked);
    }

    private Value shapedLikeTheAsking(Value asked, List<Value> values) {
        if (asked instanceof AnyBlockValue) {
            return BlockValue.block(values);
        }
        return values.isEmpty() ? NoneValue.none() : values.getFirst();
    }

    private Value laidInto(Value target, List<Value> read) {
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
