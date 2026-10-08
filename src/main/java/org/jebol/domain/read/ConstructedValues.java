package org.jebol.domain.read;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorKind;
import org.jebol.domain.value.VectorSpec;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.SetWordValue;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ConstructedValues {

    private static final Set<Datatype> HAVE_NO_MAKER = Typeset.ANY_WORD.membersAnd(
            Datatype.INTEGER, Datatype.MONEY, Datatype.CHAR,
            Datatype.FRAME, Datatype.PORT, Datatype.HANDLE,
            Datatype.LIBRARY, Datatype.UTYPE);

    private static final Set<Datatype> READ_AS_A_BLOCK = Typeset.ANY_BLOCK.members();

    private final Construction construction;

    private final Set<Datatype> readAsTextOrBytes;

    ConstructedValues(Construction construction) {
        this.construction = construction;
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.ANY_STRING.members());
        accepted.add(Datatype.BINARY);
        this.readAsTextOrBytes = Set.copyOf(accepted);
    }

    private static final class CannotConstruct extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CannotConstruct() {
            super("malconstruct", null, false, false);
        }
    }

    Optional<Value> built(List<Value> contents) {
        try {
            return Optional.of(construct(contents));
        } catch (CannotConstruct refused) {
            return Optional.empty();
        }
    }

    private Value construct(List<Value> contents) {
        if (contents.isEmpty() || !(contents.getFirst() instanceof AnyWordValue leading)) {
            throw new CannotConstruct();
        }
        if (contents.size() == 1) {
            Optional<Value> simple = switch (leading.canonical()) {
                case "true" -> Optional.of(LogicValue.yes());
                case "false" -> Optional.of(LogicValue.no());
                case "none" -> Optional.of(NoneValue.none());
                case "unset" -> Optional.of(UnsetValue.unset());
                default -> Optional.empty();
            };
            if (simple.isPresent()) {
                return simple.get();
            }
        }
        if (namesAVector(leading, contents.size())) {
            return VectorSpec.readConstruction(contents).orElseThrow(CannotConstruct::new);
        }
        Value resolved = datatypeNamed(leading);
        if (contents.size() == 1) {
            return resolved;
        }
        if (!(resolved instanceof DatatypeValue(Datatype represents))) {
            throw new CannotConstruct();
        }
        return builtFrom(represents, contents.subList(1, contents.size()));
    }

    private boolean namesAVector(AnyWordValue leading, int howManyParts) {
        if ("vector!".equals(leading.canonical())) {
            return howManyParts > 1;
        }
        return VectorKind.named(leading.spelling()).isPresent();
    }

    private Value datatypeNamed(AnyWordValue word) {
        if (!word.spelling().endsWith("!")) {
            throw new CannotConstruct();
        }
        String name = word.spelling().substring(0, word.spelling().length() - 1);
        for (Datatype candidate : Datatype.values()) {
            if (candidate.spelling().equalsIgnoreCase(name)) {
                return DatatypeValue.of(candidate);
            }
        }
        return Typeset.named(name)
                .map(typeset -> (Value) TypesetValue.of(typeset))
                .orElseThrow(CannotConstruct::new);
    }

    private Value textOrBytesStandingWhereItWasTold(Datatype datatype, List<Value> contents) {
        boolean shapeTheCAccepts = contents.size() == 1
                || (contents.size() == 2 && contents.get(1) instanceof IntegerValue);
        if (!shapeTheCAccepts) {
            throw new CannotConstruct();
        }
        Value whole = builtFrom(datatype, List.of(contents.getFirst()));
        return contents.size() == 1 ? whole : standingWhereItWasTold(whole, contents.get(1));
    }

    private Value standingWhereItWasTold(Value whole, Value position) {
        if (!(whole instanceof RebolSeries series)) {
            throw new CannotConstruct();
        }
        if (!(position instanceof IntegerValue(long magnitude))) {
            return series;
        }
        long tail = series.storageLength() + 1L;
        long counted = magnitude - 1;
        return series.atIndex((int) (counted < 0 || counted > tail - 1 ? tail : counted + 1));
    }

    private Value builtFrom(Datatype datatype, List<Value> contents) {
        if (HAVE_NO_MAKER.contains(datatype)) {
            throw new CannotConstruct();
        }
        if (datatype == Datatype.BITSET && contents.size() == 2
                && contents.getFirst() instanceof AnyWordValue complementing
                && complementing.canonical().equals("not")
                && contents.get(1) instanceof BinaryValue octets) {
            return BitsetValue.of(bytesOf(octets)).complemented();
        }
        if (readAsTextOrBytes.contains(datatype) && contents.size() != 1) {
            return textOrBytesStandingWhereItWasTold(datatype, contents);
        }
        if (READ_AS_A_BLOCK.contains(datatype) && contents.size() > 1) {
            return standingWhereItWasTold(builtFrom(datatype, List.of(contents.getFirst())), contents.get(1));
        }
        if (contents.size() == 2 && contents.get(1) instanceof IntegerValue
                && datatype.isSeries() && !alwaysReadsABlock(datatype)) {
            return standingWhereItWasTold(builtFrom(datatype, List.of(contents.getFirst())), contents.get(1));
        }
        if (datatype == Datatype.BITSET && contents.size() != 1) {
            throw new CannotConstruct();
        }
        if (contents.size() != 1) {
            return madeByTheEvaluator(datatype, contents);
        }
        Value only = contents.getFirst();
        return switch (datatype) {
            case DECIMAL -> only instanceof IntegerValue(long magnitude)
                    ? DecimalValue.of(magnitude)
                    : requireDatatype(only, Datatype.DECIMAL);
            case OBJECT -> objectFrom(only);
            case BITSET -> only instanceof BinaryValue octets
                    ? BitsetValue.of(bytesOf(octets))
                    : requireDatatype(only, Datatype.BITSET);
            case STRING, FILE, URL, EMAIL, TAG, REF -> switch (only) {
                case AnyStringValue text -> text.as(datatype);
                case BinaryValue _ -> madeByTheEvaluator(datatype, contents);
                default -> requireDatatype(only, datatype);
            };
            case BLOCK, PAREN, PATH, SET_PATH, GET_PATH, LIT_PATH, HASH ->
                    only instanceof BlockValue items ? items.as(datatype) : requireDatatype(only, datatype);
            case FUNCTION, CLOSURE -> functionFrom(only);
            default -> madeByTheEvaluator(datatype, contents);
        };
    }

    private Value functionFrom(Value only) {
        if (!(only instanceof BlockValue definition)
                || definition.remaining().size() != 2
                || !(definition.remaining().get(0) instanceof BlockValue spec)
                || !(definition.remaining().get(1) instanceof BlockValue body)
                || spec.datatype() != Datatype.BLOCK
                || body.datatype() != Datatype.BLOCK) {
            throw new CannotConstruct();
        }
        try {
            return construction.functionMadeFrom(spec, body);
        } catch (RuntimeException badSpec) {
            throw new CannotConstruct();
        }
    }

    private Value madeByTheEvaluator(Datatype datatype, List<Value> contents) {
        Value specification = !alwaysReadsABlock(datatype)
                && (contents.size() == 1 || datatype == Datatype.TIME)
                ? contents.getFirst()
                : BlockValue.block(contents);
        Value made;
        try {
            made = construction.madeOf(datatype, specification);
        } catch (RuntimeException refused) {
            throw new CannotConstruct();
        }
        if (made == null) {
            throw new CannotConstruct();
        }
        return made;
    }

    private boolean alwaysReadsABlock(Datatype datatype) {
        return datatype == Datatype.IMAGE;
    }

    private byte[] bytesOf(BinaryValue binary) {
        byte[] octets = new byte[binary.storageLength() - binary.index() + 1];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) binary.storage().at(binary.index() + at);
        }
        return octets;
    }

    private Value objectFrom(Value contents) {
        if (!(contents instanceof BlockValue fields)) {
            throw new CannotConstruct();
        }
        Context built = Context.root();
        List<Value> items = fields.remaining();
        for (int at = 0; at < items.size(); at++) {
            if (!(items.get(at) instanceof SetWordValue name)) {
                throw new CannotConstruct();
            }
            at++;
            built.register(name.spelling(), at < items.size() ? items.get(at) : NoneValue.none());
        }
        return new ObjectValue(built);
    }

    private Value requireDatatype(Value value, Datatype wanted) {
        if (value.datatype() != wanted) {
            throw new CannotConstruct();
        }
        return value;
    }
}
