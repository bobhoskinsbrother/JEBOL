package org.jebol.domain.eval;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyFunctionValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.AskedNative;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockStorage;
import org.jebol.domain.value.ClosureValue;
import org.jebol.domain.value.CommandValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.DefinedFunctionValue;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.ErrorWording;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RespecifiedNative;
import org.jebol.domain.value.StringStorage;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.TaskValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorStorage;
import org.jebol.domain.value.VectorValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class Crossing {

    private static final String WORD_OBJECT_ANSWERS_TO_FOR_ITSELF = "self";

    private final Value sent;

    private final Set<Context> contextsThatCross;

    private Crossing(Value sent, Set<Context> contextsThatCross) {
        this.sent = sent;
        this.contextsThatCross = contextsThatCross;
    }

    public static Crossing prepare(Value sent) {
        Reach reach = new Reach();
        reach.walk(sent);
        return new Crossing(sent, reach.contexts);
    }

    public CrossedCopy copyInto(Context receiverUser, Context receiverLib) {
        Copier copier = new Copier(receiverUser, receiverLib);
        Value copied = copier.copy(sent);
        return new CrossedCopy(copied, List.copyOf(copier.lineBreaks));
    }

    public record StorageLines(BlockStorage storage, List<Boolean> breaks) {
    }

    public record CrossedCopy(Value value, List<StorageLines> lineBreaks) {

        public Value markLinesOnThisThread() {
            lineBreaks.forEach(lines -> lines.storage().setLineBreaksFromHead(lines.breaks()));
            return value;
        }
    }

    private static final class Reach {

        private final Set<Context> contexts = Collections.newSetFromMap(new IdentityHashMap<>());

        private final Set<Object> walked = Collections.newSetFromMap(new IdentityHashMap<>());

        private void walk(Value value) {
            switch (value) {
                case PortValue refused -> throw refuse(refused);
                case HandleValue refused -> throw refuse(refused);
                case EventValue refused -> throw refuse(refused);
                case GobValue refused -> throw refuse(refused);
                case JavaObjectValue refused -> throw refuse(refused);
                case CommandValue refused -> throw refuse(refused);
                case AnyBlockValue block -> walkEachOnce(block.storage(), block.storage().snapshot());
                case MapValue map -> walkMap(map);
                case ObjectValue(Context fields) -> walkContext(fields);
                case ModuleValue(Context fields, ObjectValue header) -> {
                    walkContext(fields);
                    walk(header);
                }
                case TaskValue(Context fields, AnyBlockValue body) -> {
                    walkContext(fields);
                    walk(body);
                }
                case ErrorValue error -> listArguments(error).forEach(this::walk);
                case DefinedFunctionValue function -> walkEachOnce(function, List.of(function.spec(), function.body()));
                case RespecifiedNative respecified -> walk(respecified.spec());
                case OperatorValue operator -> walk(operator.underlying());
                default -> {
                }
            }
        }

        private void walkEachOnce(Object holder, List<Value> held) {
            if (walked.add(holder)) {
                held.forEach(this::walk);
            }
        }

        private void walkMap(MapValue map) {
            if (walked.add(map)) {
                for (Value key : map.keys()) {
                    walk(key);
                    walk(map.select(key));
                }
            }
        }

        private void walkContext(Context fields) {
            if (contexts.add(fields)) {
                fields.listEverySlot().forEach(slot -> walk(slot.value()));
            }
        }

        private static Raised refuse(Value refused) {
            return Raised.of(EvaluationFailure.INVALID_TYPE, refused.datatype());
        }
    }

    private static List<Value> listArguments(ErrorValue error) {
        List<Value> arguments = new ArrayList<>();
        error.subject().ifPresent(arguments::add);
        error.secondArgument().ifPresent(arguments::add);
        error.thirdArgument().ifPresent(arguments::add);
        error.near().ifPresent(arguments::add);
        error.whereChain().ifPresent(arguments::add);
        arguments.addAll(error.writtenFields().values());
        return arguments;
    }

    private final class Copier {

        private final Context receiverUser;

        private final Context receiverLib;

        private final Map<Object, Object> copies = new IdentityHashMap<>();

        private final List<StorageLines> lineBreaks = new ArrayList<>();

        private Copier(Context receiverUser, Context receiverLib) {
            this.receiverUser = receiverUser;
            this.receiverLib = receiverLib;
        }

        private Value copy(Value value) {
            return switch (value) {
                case AnyWordValue word -> rebind(word);
                case AnyBlockValue block -> copyBlock(block);
                case AnyStringValue text -> copyString(text);
                case BinaryValue bytes -> copyBinary(bytes);
                case ImageValue image -> new ImageValue(
                        remember(image.storage(), () -> image.storage().copy()), image.index());
                case VectorValue vector -> new VectorValue(copyVectorStorage(vector), vector.index());
                case MapValue map -> copyMap(map);
                case BitsetValue bits -> remember(bits, () -> (BitsetValue) bits.copy(true));
                case StructValue struct -> remember(struct, () -> (StructValue) struct.copy(true));
                case ObjectValue(Context fields) -> new ObjectValue(copyContext(fields));
                case ModuleValue(Context fields, ObjectValue header) ->
                        new ModuleValue(copyContext(fields), (ObjectValue) copy(header));
                case TaskValue(Context fields, AnyBlockValue body) ->
                        new TaskValue(copyContext(fields), (AnyBlockValue) copy(body));
                case ErrorValue error -> copyError(error);
                case DefinedFunctionValue function -> copyFunction(function);
                case OperatorValue operator -> copyOperator(operator);
                case NativeValue nativeValue -> findReceiversOwn(nativeValue);
                default -> value;
            };
        }

        @SuppressWarnings("unchecked")
        private <Kind> Kind remember(Object original, Supplier<Kind> making) {
            Object made = copies.get(original);
            if (made == null) {
                made = making.get();
                copies.put(original, made);
            }
            return (Kind) made;
        }

        private AnyWordValue rebind(AnyWordValue word) {
            Context bound = word.binding();
            if (contextsThatCross.contains(bound)) {
                return word.bindTo(copyContext(bound));
            }
            String name = word.canonical();
            if (!receiverUser.answersAlreadyTo(name)) {
                receiverUser.register(word.spelling(), receiverUser.contains(name)
                        ? receiverUser.findSlotFor(name).value()
                        : UnsetValue.unset());
            }
            return word.bindTo(receiverUser.contextHolding(name));
        }

        private AnyBlockValue copyBlock(AnyBlockValue block) {
            BlockStorage original = block.storage();
            BlockStorage made = (BlockStorage) copies.get(original);
            if (made == null) {
                List<Value> items = original.snapshot();
                made = new BlockStorage(Collections.nCopies(items.size(), UnsetValue.unset()));
                copies.put(original, made);
                for (int at = 1; at <= items.size(); at++) {
                    made.set(at, copy(items.get(at - 1)));
                }
                made.takeLineBreaksFrom(original, 1);
                lineBreaks.add(new StorageLines(made, original.lineBreaksFromHead()));
            }
            return ((AnyBlockValue.AnyBlockDatatype) block.datatype())
                    .wrap(made, Math.min(block.index(), made.length() + 1));
        }

        private AnyStringValue copyString(AnyStringValue text) {
            StringStorage made = remember(text.storage(),
                    () -> ((AnyStringValue) text.atIndex(1).copy(false)).storage());
            return ((AnyStringValue.AnyStringDatatype) text.datatype())
                    .wrap(made, Math.min(text.index(), made.length() + 1));
        }

        private BinaryValue copyBinary(BinaryValue bytes) {
            BinaryValue head = remember(bytes.storage(), () -> (BinaryValue) bytes.atIndex(1).copy(false));
            return head.atIndex(Math.min(bytes.index(), head.storageLength() + 1));
        }

        private VectorStorage copyVectorStorage(VectorValue vector) {
            return remember(vector.storage(), () -> new VectorValue(vector.storage(), 1)
                    .copyFirst(vector.storage().length()).storage());
        }

        private MapValue copyMap(MapValue map) {
            MapValue made = (MapValue) copies.get(map);
            if (made == null) {
                made = MapValue.of(List.of());
                copies.put(map, made);
                for (Value key : map.keys()) {
                    made.put(copy(key), copy(map.select(key)));
                }
            }
            return made;
        }

        private Context copyContext(Context fields) {
            Context made = (Context) copies.get(fields);
            if (made != null) {
                return made;
            }
            made = Context.makeChildOf(receiverUser);
            copies.put(fields, made);
            for (ContextSlot slot : fields.listEverySlot()) {
                made.register(slot.spelling(), copy(slot.value())).hide(slot.isHidden());
            }
            if (fields.hasItsOwnSelf()) {
                made.pointItsOwnSelfAt(copy(fields.findSlotFor(WORD_OBJECT_ANSWERS_TO_FOR_ITSELF).value()));
            }
            made.closeToNewNames(fields.isClosedToNewNames());
            return made;
        }

        private ErrorValue copyError(ErrorValue error) {
            Map<String, Value> fields = new LinkedHashMap<>();
            error.writtenFields().forEach((name, held) -> fields.put(name, copy(held)));
            return new ErrorValue(error.category(), error.errorId(), error.message(),
                    error.subject().map(this::copy), error.secondArgument().map(this::copy),
                    error.thirdArgument().map(this::copy), error.near().map(this::copy),
                    error.whereChain().map(this::copy), fields,
                    error.wording().system().isUnbound() ? error.wording() : new ErrorWording(receiverLib));
        }

        private DefinedFunctionValue copyFunction(DefinedFunctionValue function) {
            Object done = copies.get(function);
            if (done != null) {
                return (DefinedFunctionValue) done;
            }
            AnyBlockValue spec = (AnyBlockValue) copy(function.spec());
            AnyBlockValue body = (AnyBlockValue) copy(function.body());
            Context closedOver = contextsThatCross.contains(function.closedOver())
                    ? copyContext(function.closedOver())
                    : receiverUser;
            FunctionValue made = Binder.withItsBodyBound(new FunctionValue(
                    spec, body, function.parameters(), function.localNames(), closedOver));
            DefinedFunctionValue kept = function instanceof ClosureValue ? made.asClosure() : made;
            copies.put(function, kept);
            return kept;
        }

        private AnyFunctionValue copyOperator(OperatorValue operator) {
            if (operator.underlying() instanceof NativeValue
                    && findInLib(operator.operatorName()) instanceof OperatorValue own
                    && own.operatorName().equals(operator.operatorName())) {
                return own;
            }
            return new OperatorValue(operator.operatorName(), (AnyFunctionValue) copy(operator.underlying()));
        }

        private NativeValue findReceiversOwn(NativeValue sentNative) {
            return switch (sentNative) {
                case AskedNative asked -> findReceiversOwn(asked.asked()).askFor(asked.askedRefinements());
                case RespecifiedNative respecified -> findReceiversOwn(respecified.original())
                        .deriveWith((AnyBlockValue) copy(respecified.spec()), respecified.parameters());
                default -> findNativeNamed(sentNative);
            };
        }

        private NativeValue findNativeNamed(NativeValue sentNative) {
            if (findInLib(sentNative.nativeName()) instanceof NativeValue own
                    && own.nativeName().equals(sentNative.nativeName())) {
                return own.withoutAskedRefinements();
            }
            throw Raised.of(EvaluationFailure.INVALID_TYPE, sentNative.datatype());
        }

        private Value findInLib(String name) {
            String canonical = Context.canonicalise(name);
            return receiverLib.contains(canonical)
                    ? receiverLib.findSlotFor(canonical).value()
                    : UnsetValue.unset();
        }
    }
}
