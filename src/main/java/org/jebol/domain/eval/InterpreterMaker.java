package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Conversion;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.ErrorCatalogue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.Maker;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ParameterKind;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class InterpreterMaker implements Maker {

    private static final String AN_OPERATOR_NOBODY_HAS_NAMED = "?";

    private final Evaluator evaluator;
    private final Context context;
    private final MakingAndConverting makingAndConverting;

    public InterpreterMaker(Evaluator evaluator, Context context,
            MakingAndConverting makingAndConverting) {
        this.evaluator = evaluator;
        this.context = context;
        this.makingAndConverting = makingAndConverting;
    }

    @Override
    public Value convertedTo(DatatypeValue wanted, Value value) {
        return switch (wanted.represents()) {
            case EVENT -> EventPath.made(wanted, value,
                    piece -> evaluator.simpleValueOf(piece, context));
            case ERROR, FUNCTION, CLOSURE, STRUCT -> wanted.make(value, this);
            case OBJECT -> anObjectConvertedFrom(value);
            case MODULE -> aModuleConvertedFrom(value);
            default -> withItsWordsInterned(makingAndConverting.converted(Conversion.TO, wanted, value));
        };
    }

    private Value anObjectConvertedFrom(Value value) {
        if (!(value instanceof ErrorValue raised)) {
            throw Raised.badMakeArg(value, "object!");
        }
        if (raised.field("code").orElseGet(NoneValue::none) instanceof IntegerValue(long magnitude)
                && magnitude < ErrorCatalogue.LOWEST_CODE_AN_ENTRY_HAS) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, value);
        }
        Context fields = Context.childOf(Context.root());
        for (String field : ErrorValue.FIELDS) {
            fields.register(field, raised.field(field).orElseGet(NoneValue::none));
        }
        return new ObjectValue(fields);
    }

    private Value aModuleConvertedFrom(Value value) {
        if (!(value instanceof BlockValue parts)
                || parts.datatype() != Datatype.BLOCK
                || parts.remaining().isEmpty()) {
            throw Raised.badMakeArg(value, "module!");
        }
        List<Value> given = parts.remaining();
        if (!(given.getFirst() instanceof ObjectValue header)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, given.getFirst());
        }
        if (given.size() < 2 || !(given.get(1) instanceof ObjectValue(Context fields))) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    given.size() < 2 ? given.getFirst() : given.get(1));
        }
        return new ModuleValue(fields, header);
    }

    @Override
    public Value make(Datatype kind, Value spec) {
        return switch (kind) {
            case OBJECT -> makeObjectFrom(spec);
            case FUNCTION -> makeFunctionFrom(spec);
            case CLOSURE -> makeFunctionFrom(spec).asClosure();
            case OP -> makeOperatorFrom(spec);
            case ERROR -> makeErrorFrom(spec);
            case MODULE -> makeModuleFrom(spec);
            default -> makeAnotherFrom(kind, spec);
        };
    }

    @Override
    public Value makeAnotherFrom(Datatype kind, Value spec) {
        return withItsWordsInterned(makingAndConverting.made(DatatypeValue.of(kind), spec, evaluator, context,
                value -> evaluator.simpleValueOf(value, context)));
    }

    private Value withItsWordsInterned(Value made) {
        evaluator.symbols().internWhatWasRead(List.of(made));
        return made;
    }

    private Value makeObjectFrom(Value spec) {
        if (spec instanceof NoneValue) {
            throw Raised.badMakeArg(spec, "object!");
        }
        return evaluator.evaluatedInto(evaluator.freshObjectWithin(context),
                spec instanceof BlockValue body ? body : BlockValue.block(List.of()));
    }

    @Override
    public Value makeObjectFrom(ObjectValue prototype, Value spec) {
        return spec instanceof ObjectValue other
                ? mergedObject(prototype, other)
                : evaluator.evaluatedInto(aCopyOf(prototype), (BlockValue) spec);
    }

    private ObjectValue aCopyOf(ObjectValue prototype) {
        ObjectValue built = evaluator.freshObjectWithin(context);
        Context fields = built.context();
        fieldsOtherThanSelfIn(prototype.context()).forEach(slot -> fields.register(
                slot.spelling(), Binder.clonedAndRebound(
                        slot.value(), Set.of(prototype.context()), fields)));
        return built;
    }

    private Value mergedObject(ObjectValue prototype, ObjectValue other) {
        Context fields = Context.childOf(context);
        fieldsOtherThanSelfIn(prototype.context())
                .forEach(slot -> fields.register(slot.spelling(), slot.value()));
        fieldsOtherThanSelfIn(other.context())
                .forEach(slot -> fields.register(slot.spelling(), slot.value()));

        ObjectValue merged = new ObjectValue(fields);
        fields.register("self", merged);
        Set<Context> sources = Set.of(prototype.context(), other.context());
        fieldsOtherThanSelfIn(fields).forEach(slot -> fields.register(slot.spelling(),
                Binder.clonedAndRebound(slot.value(), sources, fields)));
        return merged;
    }

    private List<ContextSlot> fieldsOtherThanSelfIn(Context fields) {
        return fields.slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .toList();
    }


    private FunctionValue makeFunctionFrom(Value spec) {
        if (!(spec instanceof BlockValue parts)) {
            throw Raised.badMakeArg(spec, "function!");
        }
        List<Value> items = parts.remaining();
        if (items.size() < 2
                || !(items.get(0) instanceof BlockValue functionSpec)
                || !(items.get(1) instanceof BlockValue body)) {
            throw Raised.badMakeArg(spec, "function!");
        }
        return Binder.functionWithItsBodyBound(functionSpec, body, context);
    }

    @Override
    public Value makeFunctionFrom(Value prototype, BlockValue spec) {
        List<Value> parts = spec.remaining();
        if (parts.isEmpty()) {
            return prototype;
        }
        Value first = parts.getFirst();
        boolean keepingTheSpecification = isTheStarThatMeansKeepIt(first);
        if (!keepingTheSpecification && !(first instanceof BlockValue)) {
            throw Raised.cannotUse(spec, "make on a function");
        }
        Value replacementBody = parts.size() > 1 ? parts.get(1) : NoneValue.none();
        if (prototype instanceof NativeValue && replacementBody instanceof BlockValue) {
            throw Raised.cannotUseTheAction(spec, "make");
        }
        if (!(prototype instanceof FunctionValue written)) {
            return prototype instanceof NativeValue built && !keepingTheSpecification
                    ? built.derivedWith((BlockValue) first,
                            FunctionSpec.parametersIn((BlockValue) first))
                    : prototype;
        }
        BlockValue functionSpec = keepingTheSpecification
                ? asABlock(written.spec())
                : (BlockValue) first;
        BlockValue body = replacementBody instanceof BlockValue replacement
                ? replacement
                : asABlock(written.body());
        return Binder.functionWithItsBodyBound(functionSpec, body, written.closedOver());
    }

    private BlockValue asABlock(Value half) {
        return half instanceof BlockValue block
                ? block
                : BlockValue.block(List.of());
    }

    private boolean isTheStarThatMeansKeepIt(Value first) {
        return first instanceof WordValue star && star.canonical().equals("*");
    }

    private Value makeOperatorFrom(Value spec) {
        Value dispatching = spec instanceof BlockValue
                ? makeFunctionFrom(spec)
                : spec;
        if (!dispatching.datatype().isAnyFunction()
                || howManyArgumentsBeforeAnyRefinement(dispatching) != 2) {
            throw Raised.badMakeArg(spec, "op!");
        }
        return new OperatorValue(AN_OPERATOR_NOBODY_HAS_NAMED, dispatching);
    }

    private int howManyArgumentsBeforeAnyRefinement(Value dispatching) {
        List<Parameter> declared = switch (dispatching) {
            case FunctionValue function -> function.parameters();
            case NativeValue built -> built.parameters();
            case OperatorValue operator ->
                    List.of(Parameter.required("a"), Parameter.required("b"));
            default -> List.of();
        };
        int counted = 0;
        for (Parameter parameter : declared) {
            if (parameter.kind() == ParameterKind.REFINEMENT) {
                return counted;
            }
            if (parameter.kind() != ParameterKind.RETURN_TYPE) {
                counted++;
            }
        }
        return counted;
    }

    @Override
    public Value makeErrorFrom(Value spec) {
        Value theSpecAsWritten = spec;
        boolean fromAnObject = spec instanceof ObjectValue;
        if (spec instanceof ObjectValue(Context fields)) {
            spec = BlockValue.block(fields.setWordsAndValues());
        } else if (spec instanceof BlockValue body && body.datatype() == Datatype.BLOCK) {
            spec = BlockValue.block(
                    evaluator.evaluatedInto(evaluator.freshObjectWithin(context), body).context().setWordsAndValues());
        }
        if (!(spec instanceof BlockValue fields)) {
            if (!(spec instanceof StringValue written)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, spec);
            }
            return evaluator.spokenHere(new ErrorValue(ErrorCategory.USER, "message",
                    written.text(), Optional.of(spec),
                    Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(),
                    new LinkedHashMap<>()));
        }
        List<Value> items = fields.remaining();
        ErrorCategory category = ErrorCategory.USER;
        String errorId = "user-error";
        String typeWordAsSpelled = "";
        boolean namedAType = false;
        boolean namedAnId = false;
        Value unknownId = NoneValue.none();
        Optional<Value> subject = Optional.empty();
        Optional<Value> second = Optional.empty();
        Optional<Value> third = Optional.empty();
        for (int at = 0; at + 1 < items.size(); at += 2) {
            if (!(items.get(at) instanceof WordValue name)
                    || name.datatype() != Datatype.SET_WORD) {
                continue;
            }
            String said = items.get(at + 1) instanceof WordValue spelled
                    ? spelled.canonical()
                    : Molder.form(items.get(at + 1));
            Value asWritten = items.get(at + 1);
            switch (name.canonical()) {
                case "type" -> {
                    namedAType = true;
                    typeWordAsSpelled = asWritten instanceof WordValue spelled
                            ? spelled.spelling()
                            : said;
                    category = ErrorCategory.named(said).orElseThrow(() ->
                            Raised.of(EvaluationFailure.INVALID_ARG, asWritten));
                }
                case "id" -> {
                    namedAnId = true;
                    errorId = said;
                    unknownId = asWritten;
                }
                case "arg1" -> subject = Optional.of(items.get(at + 1));
                case "arg2" -> second = Optional.of(items.get(at + 1));
                case "arg3" -> third = Optional.of(items.get(at + 1));
                default -> { }
            }
        }
        if (!namedAType || !namedAnId) {
            throw new Raised(ErrorValue.of(ErrorCategory.INTERNAL,
                    "invalid-error", "an error spec names a type and an id"));
        }
        refuseAnErrorTheCatalogueHasNot(
                category, errorId, unknownId, theSpecAsWritten, fromAnObject);
        ErrorValue built = new ErrorValue(category, errorId, errorId, subject,
                second, third, Optional.empty(), Optional.empty(),
                new LinkedHashMap<>());
        built.write("type", WordValue.of(typeWordAsSpelled));
        return evaluator.spokenHere(built);
    }

    private void refuseAnErrorTheCatalogueHasNot(
            ErrorCategory category, String errorId, Value asWritten, Value spec,
            boolean fromAnObject) {

        if (!ErrorCatalogue.idsIn(category.spelling()).contains(errorId)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asWritten);
        }
        if (!fromAnObject && ErrorCatalogue.codeFor(category.spelling(), errorId)
                < ErrorCatalogue.LOWEST_CODE_AN_ENTRY_HAS) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, spec);
        }
    }

    private Value makeModuleFrom(Value spec) {
        if (!(spec instanceof BlockValue given)) {
            throw Raised.badMakeArg(spec, "module!");
        }
        Value built = evaluator.applyFunction(
                context.systemFunctionNamed("make-module*"), List.of(given));
        if (!(built instanceof ModuleValue module)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, "module!");
        }
        return module;
    }

    @Override
    public Value makeStructFrom(StructValue prototype, Value spec) {
        StructValue made = prototype.separateCopy();
        if (spec instanceof BinaryValue octets) {
            byte[] bytes = octets.bytesFromHere();
            if (bytes.length < made.size()) {
                throw Raised.badMakeArg(spec, "struct!");
            }
            made.changeFrom(bytes);
            return made;
        }
        if (!(spec instanceof BlockValue written)) {
            throw Raised.badMakeArg(spec, "struct!");
        }
        made.startedWith(BlockValue.block(evaluator.reducedLeavingSetWords(written)));
        return made;
    }

    @Override
    public Value makeEventFrom(EventValue prototype, Value spec) {
        return EventPath.made(prototype, spec, value -> evaluator.simpleValueOf(value, context));
    }
}
