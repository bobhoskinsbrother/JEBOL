package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class PutAction extends DefaultNative implements ActionValue {

    private static final int NOT_FOUND = -1;

    @Override
    public String nativeName() {
        return "put";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("target"),
                Parameter.required("key", Typeset.ANY_TYPE.members()),
                Parameter.required("value", Typeset.ANY_TYPE.members()),
                Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("case", "skip");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .theRebolActorsAnswer("put", arguments, refinements)
                .orElseGet(() -> put(arguments, refinements));
    }

    private Value put(List<Value> arguments, Set<String> refinements) {
        Value key = arguments.get(1);
        Value written = arguments.get(2);
        switch (arguments.getFirst()) {
            case MapValue map -> map.put(key, written, refinements.contains("case"));
            case ObjectValue object when key instanceof WordValue field ->
                    putIntoTheObject(object, field, written);
            case ObjectValue ignored -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    Molder.mold(key) + " is not a word an object can hold a field under");
            case BlockValue block -> putIntoTheBlock(block, key, written,
                    recordWidth(arguments, refinements), refinements.contains("case"));
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        }
        return written;
    }

    private void putIntoTheObject(ObjectValue object, WordValue field, Value written) {
        object.refuseHiddenFieldsIn(field);
        if (object.context().isClosedToNewNames()) {
            throw Raised.of(EvaluationFailure.PROTECTED);
        }
        object.context().register(field.spelling(), written);
    }

    private void putIntoTheBlock(
            BlockValue block, Value key, Value written, int stride, boolean mindingCase) {
        List<Value> items = block.remaining();
        int found = positionOfTheKey(items, key, stride, mindingCase);
        if (found == NOT_FOUND) {
            block.storage().append(key);
            block.storage().append(written);
        } else if (found + 1 >= items.size()) {
            block.storage().append(written);
        } else {
            block.storage().set(block.index() + found + 1, written);
        }
    }

    private int positionOfTheKey(
            List<Value> items, Value key, int stride, boolean mindingCase) {
        for (int at = 0; at < items.size(); at += stride) {
            boolean matches = mindingCase
                    ? Comparison.identicallyEqual(items.get(at), key)
                    : Comparison.looselyEqual(items.get(at), key);
            if (matches) {
                return at;
            }
        }
        return NOT_FOUND;
    }

    private int recordWidth(List<Value> arguments, Set<String> refinements) {
        return argumentOf("skip", 0, arguments, refinements)
                .map(size -> Math.max(1, (int) ((IntegerValue) size).magnitude()))
                .orElse(1);
    }
}
