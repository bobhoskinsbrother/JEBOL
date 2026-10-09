package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SetNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "set";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("word", TypesetValue.ANY_PATH.membersAnd(
                        WordValue.TYPE, LitWordValue.TYPE, BlockValue.TYPE, ObjectValue.TYPE)),
                Parameter.required("value", TypesetValue.ANY_TYPE.members()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("any", "only", "some");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value target = arguments.getFirst();
            Value supplied = arguments.get(1);
            refuseUnassignable(target, EvaluationFailure.EXPECT_ARG);
            target.refuseToBeWrittenWhenItNamesSelf();
            if (!refinements.contains("any") && supplied instanceof UnsetValue) {
                throw Raised.of(EvaluationFailure.NEED_VALUE, target);
            }
            return switch (target) {
                case AnyWordValue word -> {
                    word.boundSlot().setValue(supplied);
                    yield supplied;
                }
                case AnyPathValue path ->
                        writtenThroughPath(path, supplied);
                case ObjectValue into when supplied instanceof ObjectValue from
                        && !refinements.contains("only") -> {
                    refuseEachUnassignable(wordsOf(into));
                    setFieldsFromObjectMatchedByName(into, from, refinements);
                    yield supplied;
                }
                case ObjectValue into -> spreadOver(wordsOf(into), supplied, refinements);
                case AnyBlockValue words -> spreadOver(words.remaining(), supplied, refinements);
                default -> refuseTheDatatype(target);
            };
        };
    }

    private Value spreadOver(List<Value> words, Value supplied, Set<String> refinements) {
        refuseEachUnassignable(words);
        Optional<List<Value>> values = refinements.contains("only")
                ? Optional.empty()
                : Optional.of(supplied)
                        .filter(AnyBlockValue.class::isInstance)
                        .map(block -> ((AnyBlockValue) block).remaining());
        if (!refinements.contains("any")) {
            values.ifPresent(spread -> refuseUnsetAmong(words, spread));
        }
        boolean onlySome = refinements.contains("some");
        for (int index = 0; index < words.size(); index++) {
            boolean ranOut = values.isPresent() && index >= values.get().size();
            if (ranOut && onlySome) {
                break;
            }
            Value assigned = values.isEmpty()
                    ? supplied
                    : ranOut ? NoneValue.none() : values.get().get(index);
            if (onlySome && assigned instanceof NoneValue) {
                continue;
            }
            ((AnyWordValue) words.get(index)).boundSlot().setValue(assigned);
        }
        return supplied;
    }

    private void refuseUnsetAmong(List<Value> words, List<Value> values) {
        for (int index = 0; index < words.size() && index < values.size(); index++) {
            if (values.get(index) instanceof UnsetValue) {
                throw Raised.of(EvaluationFailure.NEED_VALUE, words.get(index));
            }
        }
    }

    private List<Value> wordsOf(ObjectValue object) {
        return object.context().slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .<Value>map(slot -> WordValue.of(slot.spelling()).boundTo(object.context()))
                .toList();
    }

    private void refuseEachUnassignable(List<Value> words) {
        words.forEach(word -> refuseUnassignable(word, EvaluationFailure.INVALID_ARG));
        words.forEach(Value::refuseToBeWrittenWhenItNamesSelf);
    }

    private void refuseUnassignable(Value word, EvaluationFailure failure) {
        if (word instanceof IssueValue || word instanceof RefinementValue) {
            throw Raised.of(failure,
                    "set cannot assign to a " + word.datatype().literalSpelling());
        }
    }

    private Value writtenThroughPath(AnyBlockValue path, Value supplied) {
        List<Value> segments = path.remaining();
        if (segments.size() < 2 || !(segments.getFirst() instanceof AnyWordValue head)) {
            return refuseTheDatatype(path);
        }
        Value holder = head.boundSlot().value();
        for (int at = 1; at < segments.size() - 1; at++) {
            if (!(segments.get(at) instanceof AnyWordValue field)
                    || !(holder instanceof ObjectValue(Context fields))
                    || !fields.holds(field.canonical())) {
                return refuseTheDatatype(path);
            }
            holder = fields.slotFor(field.canonical()).value();
        }
        if (!(segments.getLast() instanceof AnyWordValue field)
                || !(holder instanceof ObjectValue(Context fields))) {
            return refuseTheDatatype(path);
        }
        if (!fields.holds(field.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
        }
        fields.ownSlotFor(field.canonical()).setValue(supplied);
        return supplied;
    }

    private void setFieldsFromObjectMatchedByName(
            ObjectValue into, ObjectValue from, Set<String> refinements) {
        boolean anyValue = refinements.contains("any");
        boolean onlySome = refinements.contains("some");
        for (ContextSlot slot : fieldsBothDeclare(into, from)) {
            Value supplied = from.context().ownSlotFor(slot.canonical()).value();
            if (!anyValue && supplied instanceof UnsetValue) {
                continue;
            }
            if (onlySome && holdsSomething(slot.value()) && !holdsSomething(supplied)) {
                continue;
            }
            slot.setValue(supplied);
        }
        for (ContextSlot slot : fieldsBothDeclare(into, from)) {
            slot.setValue(Binder.clonedAndRebound(slot.value(),
                    Set.of(from.context()), into.context()));
        }
    }

    private List<ContextSlot> fieldsBothDeclare(ObjectValue into, ObjectValue from) {
        return into.context().slots().stream()
                .filter(slot -> !slot.canonical().equals("self")
                        && from.context().holds(slot.canonical()))
                .toList();
    }

    private boolean holdsSomething(Value held) {
        return held.datatype() != NoneValue.TYPE && held.datatype() != UnsetValue.TYPE;
    }
}
