package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DELECT: the parser every REBOL dialect is read by, DRAW, EFFECT, TEXT and
 * REBCODE alike. A command declares the <em>types</em> of its arguments rather
 * than their order, and each argument goes to whichever slot will take it, so
 * {@code cmd 3 a@b} answers {@code [cmd a@b 3]}.
 *
 * <p>Specified in {@code spec/dialect.allium}.
 */
public final class Delect {

    private Delect() {
    }

    /**
     * Reads one command, or every command when asked for the whole block.
     * Answers the input standing after what was read, so a caller loops on it,
     * and none at the end so the loop has something to stop on.
     */
    public static Value read(
            ObjectValue dialect, AnyBlockValue input, AnyBlockValue output,
            boolean readsWholeBlock, Evaluator evaluator, Context where) {

        if (output.storage().isProtected()) {
            throw new ProtectedFromChange();
        }
        Run run = new Run(dialect, input, output, evaluator, where);
        if (run.nothingIsLeft()) {
            return NoneValue.none();
        }
        if (!readsWholeBlock) {
            run.emptyTheOutput();
            run.readOneCommand();
            return input.atIndex(run.reachedIndex());
        }
        run.emptyTheOutput();
        while (!run.nothingIsLeft() && run.readOneCommand()) {
            continue;
        }
        return input.atIndex(run.reachedIndex());
    }

    private sealed interface Slot {

        boolean accepts(Value value);

        record Plain(java.util.Set<Datatype> types) implements Slot {

            @Override
            public boolean accepts(Value value) {
                return types.contains(value.datatype()) || convertsInto(types, value);
            }
        }

        record Repeating(java.util.Set<Datatype> types) implements Slot {

            @Override
            public boolean accepts(Value value) {
                return types.contains(value.datatype()) || convertsInto(types, value);
            }
        }

        record Named(String word) implements Slot {

            @Override
            public boolean accepts(Value value) {
                return value instanceof WordValue written
                        && written.canonical().equals(word);
            }
        }
    }

    private static boolean convertsInto(java.util.Set<Datatype> types, Value value) {
        return types.contains(IntegerValue.TYPE) && value instanceof AnyDecimalValue
                || types.contains(DecimalValue.TYPE) && value instanceof IntegerValue;
    }

    private static Value convertedFor(java.util.Set<Datatype> types, Value value) {
        if (types.contains(value.datatype())) {
            return value;
        }
        if (types.contains(IntegerValue.TYPE) && value instanceof AnyDecimalValue fraction) {
            return IntegerValue.of(cutDownRatherThanRounded(fraction));
        }
        if (types.contains(DecimalValue.TYPE) && value instanceof IntegerValue(long magnitude)) {
            return DecimalValue.of(magnitude);
        }
        return value;
    }

    private static long cutDownRatherThanRounded(AnyDecimalValue fraction) {
        return (long) fraction.quantity();
    }

    private static final class Run {

        private final List<ContextSlot> fields;
        private final AnyBlockValue input;
        private final AnyBlockValue output;
        private final Evaluator evaluator;
        private final Context where;

        private int at;

        Run(ObjectValue dialect, AnyBlockValue input, AnyBlockValue output,
            Evaluator evaluator, Context where) {

            this.fields = dialect.context().slots().stream()
                    .filter(slot -> !slot.canonical().equals("self"))
                    .toList();
            this.input = input;
            this.output = output;
            this.evaluator = evaluator;
            this.where = where;
            this.at = input.index();
        }

        boolean nothingIsLeft() {
            return at > input.storage().length();
        }

        int reachedIndex() {
            return Math.min(at, input.storage().length() + 1);
        }

        void emptyTheOutput() {
            while (output.storage().length() > 0) {
                output.storage().removeAt(1);
            }
        }

        boolean readOneCommand() {
            Value next = input.storage().at(at);
            boolean asALitWord = next instanceof LitWordValue;
            int command = next instanceof AnyWordValue word
                            && (word instanceof WordValue || asALitWord)
                    ? indexOfCommand(word)
                    : 0;

            if (command <= 1) {
                return readTheDialectsFirstFieldWhateverItIsNamed();
            }
            at++;
            return readNamedCommand(command, asALitWord, howManyArgumentsFollow());
        }

        private int howManyArgumentsFollow() {
            int ahead = at;
            while (ahead <= input.storage().length()) {
                Value item = input.storage().at(ahead);
                if (item instanceof AnyWordValue word
                        && (word instanceof WordValue || word instanceof LitWordValue)
                        && startsTheNextCommandRatherThanBeingAKeyword(word)) {
                    break;
                }
                ahead++;
            }
            return ahead - at;
        }

        private boolean startsTheNextCommandRatherThanBeingAKeyword(AnyWordValue word) {
            return indexOfCommand(word) > 1;
        }

        private boolean readTheDialectsFirstFieldWhateverItIsNamed() {
            if (fields.isEmpty()) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, input);
            }
            int lengthBefore = output.storage().length();
            boolean tookIt = readNamedCommand(1, false, 1);
            if (!tookIt || output.storage().length() == lengthBefore + 1) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, input);
            }
            return true;
        }

        private boolean readNamedCommand(int command, boolean asALitWord, int howMany) {
            ContextSlot field = fields.get(command - 1);
            if (!(field.value() instanceof AnyBlockValue declared)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, input);
            }
            List<Slot> slots = slotsDeclaredBy(declared, where);
            output.storage().append(asALitWord
                    ? LitWordValue.of(field.spelling())
                    : WordValue.of(field.spelling()));

            Placing placing = new Placing(slots);
            boolean readEverything = true;
            for (int taken = 0; taken < howMany; taken++) {
                Optional<Value> argument = theArgumentAt(at);
                if (argument.isEmpty()) {
                    readEverything = false;
                    break;
                }
                at++;
                if (argument.get() instanceof NoneValue) {
                    continue;
                }
                if (!placing.place(argument.get())) {
                    at--;
                    readEverything = false;
                    break;
                }
            }
            placing.writeInto(output.storage());
            if (readEverything) {
                padTo(whicheverIsLonger(placing.countOfPlainSlots(), howMany) + 1);
            }
            return readEverything;
        }

        private void padTo(int howLong) {
            while (output.storage().length() < howLong) {
                output.storage().append(NoneValue.none());
            }
        }

        private static int whicheverIsLonger(int slotsDeclared, int argumentsWritten) {
            return Math.max(slotsDeclared, argumentsWritten);
        }

        private Optional<Value> theArgumentAt(int position) {
            if (position > input.storage().length()) {
                return Optional.empty();
            }
            Value written = input.storage().at(position);
            return switch (written) {
                case WordValue word ->
                        aWordTheDialectKnowsIsLeftAlone(word)
                                ? Optional.of(word)
                                : whateverTheWordHoldsByItsOwnBindingFirst(word);
                case LitWordValue quoted -> Optional.of(WordValue.of(quoted.spelling()));
                case ParenValue paren -> evaluated(paren);
                case PathValue path -> evaluated(path);
                default -> Optional.of(written);
            };
        }

        private boolean aWordTheDialectKnowsIsLeftAlone(AnyWordValue word) {
            return indexOfCommand(word) != 0;
        }

        private Optional<Value> whateverTheWordHoldsByItsOwnBindingFirst(AnyWordValue word) {
            if (!word.binding().isUnbound() && word.binding().holds(word.canonical())) {
                return Optional.of(word.binding().slotFor(word.canonical()).value());
            }
            return where.holds(word.canonical())
                    ? Optional.of(where.slotFor(word.canonical()).value())
                    : Optional.empty();
        }

        private Optional<Value> evaluated(AnyBlockValue block) {
            if (thereIsNoEvaluatorBecauseAGobIsBeingFlattened()) {
                return Optional.empty();
            }
            try {
                return Optional.of(evaluator.evaluateOrRaise(
                        Binder.bind(BlockValue.block(List.of(block)), where), where));
            } catch (Raised unreachable) {
                return Optional.empty();
            }
        }

        private boolean thereIsNoEvaluatorBecauseAGobIsBeingFlattened() {
            return evaluator == null;
        }

        private int indexOfCommand(AnyWordValue word) {
            for (int position = 0; position < fields.size(); position++) {
                if (fields.get(position).canonical().equals(word.canonical())) {
                    return fields.get(position).value() instanceof NoneValue
                            ? negativeForAKeywordThatTakesNothing(position)
                            : position + 1;
                }
            }
            return 0;
        }

        private static int negativeForAKeywordThatTakesNothing(int position) {
            return -(position + 1);
        }
    }

    private static List<Slot> slotsDeclaredBy(AnyBlockValue declared, Context where) {
        List<Slot> slots = new ArrayList<>();
        List<Value> written = declared.remaining();
        for (int at = 0; at < written.size(); at++) {
            Value item = written.get(at);
            boolean repeats = item instanceof AnyWordValue star
                    && star.canonical().equals("*");
            if (repeats && at + 1 < written.size()) {
                at++;
                item = written.get(at);
            }
            slotFor(item, repeats, where).ifPresent(slots::add);
        }
        return List.copyOf(slots);
    }

    private static Optional<Slot> slotFor(
            Value written, boolean repeats, Context where) {

        Optional<java.util.Set<Datatype>> types = typesNamedBy(written, where);
        if (types.isPresent()) {
            return Optional.of(repeats
                    ? new Slot.Repeating(types.get())
                    : new Slot.Plain(types.get()));
        }
        return written instanceof AnyWordValue word
                ? Optional.of(new Slot.Named(word.canonical()))
                : Optional.empty();
    }

    private static Optional<java.util.Set<Datatype>> typesNamedBy(
            Value written, Context where) {

        if (written instanceof Datatype represents) {
            return Optional.of(java.util.Set.of(represents));
        }
        if (written instanceof TypesetValue family) {
            return Optional.of(family.members());
        }
        if (!(written instanceof AnyWordValue word)) {
            return Optional.empty();
        }
        Optional<Datatype> one = Catalogue.DATATYPES.named(word.spelling());
        if (one.isPresent()) {
            return Optional.of(java.util.Set.of(one.get()));
        }
        return whateverTheWordNames(word, where);
    }

    private static Optional<java.util.Set<Datatype>> whateverTheWordNames(
            AnyWordValue word, Context where) {

        if (where.holds(word.canonical())
                && where.slotFor(word.canonical()).value()
                        instanceof TypesetValue family) {
            return Optional.of(family.members());
        }
        return Catalogue.DATATYPES.typesetNamed(word.spelling()).map(TypesetValue::members);
    }

    private static final class Placing {

        private final List<Slot> slots;
        private final List<List<Value>> placed = new ArrayList<>();
        private int searchFrom;

        Placing(List<Slot> slots) {
            this.slots = slots;
            slots.forEach(ignored -> placed.add(new ArrayList<>()));
        }

        boolean place(Value argument) {
            for (int which = searchFrom; which < slots.size(); which++) {
                Slot slot = slots.get(which);
                if (isFull(which) || !slot.accepts(argument)) {
                    continue;
                }
                placed.get(which).add(valueFor(slot, argument));
                advancePast(which, slot);
                return true;
            }
            return false;
        }

        private static Value valueFor(Slot slot, Value argument) {
            return switch (slot) {
                case Slot.Plain plain -> convertedFor(plain.types(), argument);
                case Slot.Repeating repeating -> convertedFor(repeating.types(), argument);
                case Slot.Named ignored -> argument;
            };
        }

        private void advancePast(int which, Slot slot) {
            if (slot instanceof Slot.Named) {
                searchFrom = which + 1;
                return;
            }
            if (which == searchFrom && !(slot instanceof Slot.Repeating)) {
                searchFrom = which + 1;
            }
        }

        private boolean isFull(int which) {
            return !(slots.get(which) instanceof Slot.Repeating)
                    && !placed.get(which).isEmpty();
        }

        int countOfPlainSlots() {
            return (int) slots.stream()
                    .filter(slot -> !(slot instanceof Slot.Repeating))
                    .count();
        }

        void writeInto(BlockStorage answer) {
            for (int which = 0; which < slots.size(); which++) {
                if (slots.get(which) instanceof Slot.Repeating) {
                    placed.get(which).forEach(answer::append);
                    continue;
                }
                answer.append(placed.get(which).isEmpty()
                        ? NoneValue.none()
                        : placed.get(which).getFirst());
            }
        }
    }
}
