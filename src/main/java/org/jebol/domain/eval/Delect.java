package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DELECT: the parser every REBOL dialect is read by.
 *
 * <p>{@code u-dialect.c}. DRAW, EFFECT, TEXT and REBCODE are all read this
 * way, because {@code system/dialects} holds one object per dialect and each
 * object's fields are its commands. Writing a reader for DRAW alone would have
 * been writing the first of four.
 *
 * <p>The idea is not how any other parser works. A command declares the
 * <em>types</em> of its arguments rather than their order, and each argument
 * goes to whichever slot will take it. So {@code cmd 3 a@b} answers
 * {@code [cmd a@b 3]}: neither argument moved to where it was written, both
 * went to where they fit. That is what lets a dialect read as a description
 * rather than as a call.
 *
 * <p>Four things here are the opposite of the obvious guess, and every one was
 * settled against a real 3.22.1 rather than reasoned about. The dialect's
 * <em>first field</em> is its default command whatever it is named, so
 * {@code system/dialects/draw} defaults to {@code type-spec}. A fraction in a
 * whole number's slot is cut down rather than rounded. The answer is padded to
 * whichever is longer, the slot count or the number of arguments written, so
 * writing more than a command takes makes a longer answer rather than an
 * error. And an argument no slot will take stops the command where it stands
 * without raising, leaving the input pointing at it.
 *
 * <p>Specified in {@code spec/dialect.allium}.
 */
final class Delect {

    private Delect() {
    }

    /**
     * Reads one command, or every command when asked for the whole block.
     *
     * <p>Answers the input standing after what was read, so a caller loops on
     * it, and none at the end so the loop has something to stop on.
     */
    static Value read(
            ObjectValue dialect, BlockValue input, BlockValue output,
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

    /**
     * What one field of a dialect says a command takes.
     *
     * <p>Three kinds and they behave differently enough to be worth naming. A
     * plain slot holds one value of a type. A repeater holds as many of its
     * type as were written in a row. A named slot holds one word and only its
     * own word, and reaching one stops the search, which is how a dialect's
     * option words are told from its ordinary ones.
     */
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
                return value instanceof WordValue named
                        && named.datatype() == Datatype.WORD
                        && named.canonical().equals(word);
            }
        }
    }

    /**
     * The two conversions a slot will make, and it will make no others.
     *
     * <p>They are what make a dialect writable by hand: nobody typing
     * {@code line-width 2} wants to be told it should have been {@code 2.0}.
     */
    private static boolean convertsInto(java.util.Set<Datatype> types, Value value) {
        return types.contains(Datatype.INTEGER) && value instanceof DecimalValue
                || types.contains(Datatype.DECIMAL) && value instanceof IntegerValue;
    }

    private static Value convertedFor(java.util.Set<Datatype> types, Value value) {
        if (types.contains(value.datatype())) {
            return value;
        }
        if (types.contains(Datatype.INTEGER) && value instanceof DecimalValue fraction) {
            return IntegerValue.of((long) fraction.quantity());
        }
        if (types.contains(Datatype.DECIMAL) && value instanceof IntegerValue whole) {
            return DecimalValue.of(whole.magnitude());
        }
        return value;
    }

    /** One reading of one input block, holding where it has got to. */
    private static final class Run {

        private final List<ContextSlot> fields;
        private final BlockValue input;
        private final BlockValue output;
        private final Evaluator evaluator;
        private final Context where;

        private int at;

        Run(ObjectValue dialect, BlockValue input, BlockValue output,
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

        /**
         * Reads one command into the output. Answers whether to carry on.
         *
         * <p>False when an argument stopped it, which is how reading the whole
         * block knows not to loop for ever on a value nothing will take.
         */
        boolean readOneCommand() {
            Value next = input.storage().at(at);
            boolean asALitWord = next.datatype() == Datatype.LIT_WORD;
            int command = next instanceof WordValue word
                            && (word.datatype() == Datatype.WORD || asALitWord)
                    ? indexOfCommand(word)
                    : 0;

            if (command <= 1) {
                return readTheDefaultCommand();
            }
            at++;
            return readNamedCommand(command, asALitWord, howManyArgumentsFollow());
        }

        /**
         * Where the next command starts, which is where this one's arguments
         * stop.
         *
         * <p>A command runs until the next command and not until its slots are
         * full, which is why a dialect needs no punctuation between commands.
         * A keyword does not end one -- its index comes back negative -- so
         * {@code spline 1x1 2x2 closed} finishes with its option word.
         */
        private int howManyArgumentsFollow() {
            int ahead = at;
            while (ahead <= input.storage().length()) {
                Value item = input.storage().at(ahead);
                if (item instanceof WordValue word
                        && (word.datatype() == Datatype.WORD
                                || word.datatype() == Datatype.LIT_WORD)
                        && indexOfCommand(word) > 1) {
                    break;
                }
                ahead++;
            }
            return ahead - at;
        }

        /**
         * Values written before any command reach the dialect's first field.
         *
         * <p>The first field whatever it is named, which is the C reading
         * {@code FRM_WORD_SYM(dialect, 1)} rather than looking for a word.
         * {@code system/dialects/draw} defaults to {@code type-spec} for
         * exactly that reason.
         */
        private boolean readTheDefaultCommand() {
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
            if (!(field.value() instanceof BlockValue declared)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, input);
            }
            List<Slot> slots = slotsDeclaredBy(declared, where);
            output.storage().append(WordValue.of(field.spelling(),
                    asALitWord ? Datatype.LIT_WORD : Datatype.WORD));

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
                padTo(Math.max(placing.countOfPlainSlots(), howMany) + 1);
            }
            return readEverything;
        }

        /**
         * How long the answer is: the command word, then whichever is longer,
         * the slots declared or the arguments written.
         *
         * <p>The second half is the surprising one. Writing more arguments
         * than a command takes makes a longer answer rather than an error,
         * because {@code if (dia->len > size) size = dia->len;} sizes the
         * output by whichever is bigger before any of it is filled in.
         */
        private void padTo(int howLong) {
            while (output.storage().length() < howLong) {
                output.storage().append(NoneValue.none());
            }
        }

        /**
         * What the value at a position is, once it has been looked at.
         *
         * <p>A word the dialect does not know stands for whatever it holds, a
         * paren is evaluated and a path is followed, so a dialect can be
         * written with variables and computed values. A word the dialect
         * <em>does</em> know is left alone, or every option word would have to
         * be a defined variable.
         *
         * <p>Empty when a word names nothing, which stops the command where it
         * stands rather than raising.
         */
        private Optional<Value> theArgumentAt(int position) {
            if (position > input.storage().length()) {
                return Optional.empty();
            }
            Value written = input.storage().at(position);
            return switch (written) {
                case WordValue word when word.datatype() == Datatype.WORD ->
                        indexOfCommand(word) != 0
                                ? Optional.of(word)
                                : whateverTheWordHolds(word);
                case WordValue word when word.datatype() == Datatype.LIT_WORD ->
                        Optional.of(WordValue.of(word.spelling(), Datatype.WORD));
                case BlockValue block when block.datatype() == Datatype.PAREN
                        || block.datatype() == Datatype.PATH ->
                        evaluated(block);
                default -> Optional.of(written);
            };
        }

        private Optional<Value> whateverTheWordHolds(WordValue word) {
            return where.holds(word.canonical())
                    ? Optional.of(where.slotFor(word.canonical()).value())
                    : Optional.empty();
        }

        private Optional<Value> evaluated(BlockValue block) {
            try {
                return Optional.of(evaluator.evaluateOrRaise(
                        Binder.bind(BlockValue.block(List.of(block)), where), where));
            } catch (Raised unreachable) {
                return Optional.empty();
            }
        }

        /**
         * Where a word sits in the dialect: its field number, counting from
         * one, or zero when the dialect has no such field.
         *
         * <p>Negative for a keyword, which is a field holding none rather than
         * a block. A keyword names something without taking anything, and the
         * sign is what stops it ending the command before it.
         */
        private int indexOfCommand(WordValue word) {
            for (int position = 0; position < fields.size(); position++) {
                if (fields.get(position).canonical().equals(word.canonical())) {
                    return fields.get(position).value() instanceof NoneValue
                            ? -(position + 1)
                            : position + 1;
                }
            }
            return 0;
        }
    }

    /** The slots one command declares, read off its block. */
    private static List<Slot> slotsDeclaredBy(BlockValue declared, Context where) {
        List<Slot> slots = new ArrayList<>();
        List<Value> written = declared.remaining();
        for (int at = 0; at < written.size(); at++) {
            Value item = written.get(at);
            boolean repeats = item instanceof WordValue star
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
        return written instanceof WordValue word
                ? Optional.of(new Slot.Named(word.canonical()))
                : Optional.empty();
    }

    /**
     * The datatypes a slot accepts, whether it named one or a whole typeset.
     *
     * <p>A word that is neither is looked up, because that is what a typeset
     * name is: {@code any-string!} is a word bound to a typeset value rather
     * than a spelling anybody parses. The C does the same --
     * {@code Get_Var_No_Trap(fargs)} then {@code IS_TYPESET(temp)} -- and
     * following it means a dialect can declare a slot with a typeset somebody
     * made themselves, not only with the ones the language ships.
     *
     * <p>Rebol's own test needs it: its one command takes {@code any-string!}
     * and expects a string, a tag, a url and an email all to land there.
     */
    private static Optional<java.util.Set<Datatype>> typesNamedBy(
            Value written, Context where) {

        if (written instanceof DatatypeValue named) {
            return Optional.of(java.util.Set.of(named.represents()));
        }
        if (written instanceof TypesetValue named) {
            return Optional.of(named.members());
        }
        if (!(written instanceof WordValue word)) {
            return Optional.empty();
        }
        Optional<Datatype> one = Datatype.named(word.spelling());
        if (one.isPresent()) {
            return Optional.of(java.util.Set.of(one.get()));
        }
        return whateverTheWordNames(word, where);
    }

    private static Optional<java.util.Set<Datatype>> whateverTheWordNames(
            WordValue word, Context where) {

        if (where.holds(word.canonical())
                && where.slotFor(word.canonical()).value()
                        instanceof TypesetValue named) {
            return Optional.of(named.members());
        }
        return Typeset.named(withoutItsMark(word.spelling())).map(Typeset::members);
    }

    /**
     * A typeset's name without its exclamation mark.
     *
     * <p>{@code Datatype.named} takes a name either way and {@code
     * Typeset.named} takes only the bare one, so a slot declared
     * {@code any-string!} matches the first and misses the second. Stripping
     * it here rather than widening the typeset lookup, because that lookup is
     * a name table and this is the one place a REBOL spelling meets it.
     */
    private static String withoutItsMark(String spelling) {
        return spelling.endsWith("!")
                ? spelling.substring(0, spelling.length() - 1)
                : spelling;
    }

    /**
     * Where each argument went, and where to look next.
     *
     * <p>Held apart from the reading because placing is the whole of what
     * makes a dialect a dialect: an argument goes to the first slot that will
     * take it, and the answer comes out in slot order however it was written.
     */
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

        /**
         * A named slot moves the search past itself, so a word that is not its
         * word does not fall through to a later slot. Every other kind moves
         * the search on only when it was the one being looked at, which is what
         * lets an out-of-order argument reach a slot further along without
         * closing the ones before it.
         */
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

        /** How many slots hold at most one value, which is what padding counts. */
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
